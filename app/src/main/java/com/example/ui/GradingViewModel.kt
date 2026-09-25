package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.GeminiClient
import com.example.network.LocalServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface GradingUiState {
    object Idle : GradingUiState
    object Loading : GradingUiState
    data class Success(val result: GradingResult) : GradingUiState
    data class Error(val message: String) : GradingUiState
}

class GradingViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = GradingRepository(
        database.gradingResultDao(),
        database.examConfigDao()
    )

    // --- State: Active Configuration ---
    private val _configState = MutableStateFlow(
        ExamConfig(
            testType = TestType.ANSWER_TABLE,
            totalQuestions = 10,
            pointsPerCorrect = 1.0,
            masterKeys = mapOf(
                1 to "A", 2 to "B", 3 to "C", 4 to "D", 5 to "A",
                6 to "B", 7 to "C", 8 to "D", 9 to "A", 10 to "B"
            ),
            selectedQuestions = emptySet()
        )
    )
    val configState: StateFlow<ExamConfig> = _configState.asStateFlow()

    // --- State: List of Exam Configs ---
    val examConfigs: StateFlow<List<ExamConfig>> = repository.allConfigs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- State: Historical Results ---
    val historicalResults: StateFlow<List<GradingResult>> = repository.allResults
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- State: Current Grading UI State ---
    private val _gradingUiState = MutableStateFlow<GradingUiState>(GradingUiState.Idle)
    val gradingUiState: StateFlow<GradingUiState> = _gradingUiState.asStateFlow()

    // --- State: Auto Transition Mode ---
    private val _autoTransitionMode = MutableStateFlow(false)
    val autoTransitionMode: StateFlow<Boolean> = _autoTransitionMode.asStateFlow()

    fun setAutoTransitionMode(enabled: Boolean) {
        _autoTransitionMode.value = enabled
    }

    // --- State: Require Student Name Toggle ---
    private val _requireStudentName = MutableStateFlow(true)
    val requireStudentName: StateFlow<Boolean> = _requireStudentName.asStateFlow()

    fun setRequireStudentName(required: Boolean) {
        _requireStudentName.value = required
    }

    // --- State: Auto Scanning Mode (No click required) ---
    private val _isAutoScanning = MutableStateFlow(false)
    val isAutoScanning: StateFlow<Boolean> = _isAutoScanning.asStateFlow()

    private val _autoCaptureTrigger = MutableStateFlow(0)
    val autoCaptureTrigger: StateFlow<Int> = _autoCaptureTrigger.asStateFlow()

    private val _autoCaptureWarning = MutableStateFlow<String?>(null)
    val autoCaptureWarning: StateFlow<String?> = _autoCaptureWarning.asStateFlow()

    private var lastAnalysisTime = 0L
    private var isAnalyzing = false


    fun setAutoScanning(enabled: Boolean) {
        _isAutoScanning.value = enabled
    }

    // --- State: Remote Actions Flow ---
    private val _remoteActionFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 10)
    val remoteActionFlow = _remoteActionFlow.asSharedFlow()

    fun handleRemoteAction(action: String) {
        viewModelScope.launch {
            when (action) {
                "toggle_auto" -> {
                    _isAutoScanning.value = !_isAutoScanning.value
                }
                "next" -> {
                    _gradingUiState.value = GradingUiState.Idle
                }
                else -> {
                    _remoteActionFlow.emit(action)
                }
            }
        }
    }

    // --- State: Local HTTP Server Status ---
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverIpAddress = MutableStateFlow<String?>(null)
    val serverIpAddress: StateFlow<String?> = _serverIpAddress.asStateFlow()

    private var localServer: LocalServer? = null

    init {
        // Automatically insert a default configuration if empty
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.allConfigs.first().let { configs ->
                    if (configs.isEmpty()) {
                        val defaultConfigs = listOf(
                            ExamConfig(
                                examName = "Kiểm Tra 15 Phút",
                                className = "12A1",
                                testType = TestType.ANSWER_TABLE,
                                totalQuestions = 10,
                                pointsPerCorrect = 1.0,
                                masterKeys = mapOf(
                                    1 to "A", 2 to "B", 3 to "C", 4 to "D", 5 to "A",
                                    6 to "B", 7 to "C", 8 to "D", 9 to "A", 10 to "B"
                                )
                            ),
                            ExamConfig(
                                examName = "Thi Giữa Kỳ I",
                                className = "12A2",
                                testType = TestType.ANSWER_TABLE,
                                totalQuestions = 10,
                                pointsPerCorrect = 1.0,
                                masterKeys = mapOf(
                                    1 to "A", 2 to "B", 3 to "C", 4 to "D", 5 to "A",
                                    6 to "B", 7 to "C", 8 to "D", 9 to "A", 10 to "B"
                                )
                            )
                        )
                        defaultConfigs.forEach { repository.insertConfig(it) }
                        _configState.value = defaultConfigs[0]
                    } else {
                        _configState.value = configs[0]
                    }
                }
            } catch (e: Exception) {
                Log.e("GradingViewModel", "Error loading default configs", e)
            }
        }
        // Automatically start local server safely
        try {
            startServer()
        } catch (e: Exception) {
            Log.e("GradingViewModel", "Failed to start local server on launch", e)
        }
    }

    // --- Server Actions ---

    fun startServer() {
        if (localServer != null) return
        localServer = LocalServer(
            context = getApplication(),
            repository = repository,
            onConfigChanged = { newConfig ->
                _configState.value = newConfig
                saveConfigToDatabase(newConfig)
            },
            onNewResultGraded = { newResult ->
                _gradingUiState.value = GradingUiState.Success(newResult)
            },
            getActiveConfig = { _configState.value },
            onServerStarted = { ipAddress ->
                _isServerRunning.value = true
                _serverIpAddress.value = ipAddress
            },
            onServerStopped = {
                _isServerRunning.value = false
                _serverIpAddress.value = null
            },
            onRemoteAction = { action ->
                handleRemoteAction(action)
            }
        )
        localServer?.start()
    }

    fun stopServer() {
        localServer?.stop()
        localServer = null
    }

    fun getLocalServerIp(): String? {
        return localServer?.getLocalIpAddress()
    }

    // --- Configuration Actions ---

    fun selectActiveConfig(config: ExamConfig) {
        _configState.value = config
    }

    fun createNewConfig(examName: String, className: String, testType: TestType, total: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val defaultKeys = mutableMapOf<Int, String>()
            for (i in 1..total) {
                defaultKeys[i] = "A"
            }
            val newConfig = ExamConfig(
                examName = examName,
                className = className,
                testType = testType,
                totalQuestions = total,
                pointsPerCorrect = 1.0,
                masterKeys = defaultKeys
            )
            val savedId = repository.insertConfig(newConfig)
            val finalConfig = newConfig.copy(id = savedId.toInt())
            _configState.value = finalConfig
        }
    }

    fun saveConfigToDatabase(config: ExamConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            val savedId = repository.insertConfig(config)
            val finalConfig = config.copy(id = savedId.toInt())
            if (config.id == _configState.value.id || _configState.value.id == 0) {
                _configState.value = finalConfig
            }
        }
    }

    fun updateExamMeta(examName: String, className: String) {
        val updated = _configState.value.copy(examName = examName, className = className)
        _configState.value = updated
        saveConfigToDatabase(updated)
    }

    fun deleteConfig(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteConfig(id)
            // Switch to another config if active one was deleted
            val remaining = repository.allConfigs.first()
            if (remaining.isNotEmpty()) {
                val nextActive = remaining.firstOrNull { it.id != id } ?: remaining[0]
                _configState.value = nextActive
            }
        }
    }

    fun selectConfig(config: ExamConfig) {
        _configState.value = config
    }

    fun updateConfig(newConfig: ExamConfig) {
        _configState.value = newConfig
        saveConfigToDatabase(newConfig)
    }

    fun setTestType(type: TestType) {
        val updated = _configState.value.copy(testType = type)
        _configState.value = updated
        saveConfigToDatabase(updated)
    }

    fun setTotalQuestions(total: Int) {
        val currentKeys = _configState.value.masterKeys.toMutableMap()
        // Trim or pad keys
        if (total < _configState.value.totalQuestions) {
            val keysToRemove = currentKeys.keys.filter { it > total }
            keysToRemove.forEach { currentKeys.remove(it) }
        } else {
            for (i in (_configState.value.totalQuestions + 1)..total) {
                if (!currentKeys.containsKey(i)) {
                    currentKeys[i] = "A" // Default dummy option
                }
            }
        }
        val updated = _configState.value.copy(
            totalQuestions = total,
            masterKeys = currentKeys
        )
        _configState.value = updated
        saveConfigToDatabase(updated)
    }

    fun setPointsPerCorrect(points: Double) {
        val updated = _configState.value.copy(pointsPerCorrect = points)
        _configState.value = updated
        saveConfigToDatabase(updated)
    }

    fun updateMasterKey(questionNumber: Int, option: String) {
        val currentKeys = _configState.value.masterKeys.toMutableMap()
        currentKeys[questionNumber] = option.uppercase().trim()
        val updated = _configState.value.copy(masterKeys = currentKeys)
        _configState.value = updated
        saveConfigToDatabase(updated)
    }

    fun importConfigFromCsv(csvText: String) {
        val parsedKeys = AnswerKeyParser.parse(csvText)
        if (parsedKeys.isNotEmpty()) {
            val maxQuestion = parsedKeys.keys.maxOrNull() ?: 10
            _configState.value = _configState.value.copy(
                totalQuestions = maxQuestion,
                masterKeys = parsedKeys
            )
        }
    }

    fun toggleQuestionSelection(questionNumber: Int) {
        val currentSelected = _configState.value.selectedQuestions.toMutableSet()
        if (currentSelected.contains(questionNumber)) {
            currentSelected.remove(questionNumber)
        } else {
            currentSelected.add(questionNumber)
        }
        _configState.value = _configState.value.copy(selectedQuestions = currentSelected)
    }

    fun clearQuestionSelection() {
        _configState.value = _configState.value.copy(selectedQuestions = emptySet())
    }

    fun selectAllQuestions() {
        val all = (1.._configState.value.totalQuestions).toSet()
        _configState.value = _configState.value.copy(selectedQuestions = all)
    }

    fun setTotalPages(pages: Int) {
        val updated = _configState.value.copy(totalPages = pages.coerceIn(1, 5))
        _configState.value = updated
        saveConfigToDatabase(updated)
    }

    fun broadcastCameraFrame(bitmap: Bitmap) {
        localServer?.updateLatestCameraFrame(bitmap)

        if (_isAutoScanning.value && _gradingUiState.value is GradingUiState.Idle && !isAnalyzing) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastAnalysisTime > 1500L) {
                lastAnalysisTime = currentTime
                isAnalyzing = true
                viewModelScope.launch(Dispatchers.IO) {
                    val analysisResult = GeminiClient.analyzeExamFrame(bitmap)
                    launch(Dispatchers.Main) {
                        isAnalyzing = false
                        if (analysisResult.isSuccess) {
                            val result = analysisResult.getOrThrow()
                            if (result.isExam && result.isFullyInFrame && result.isClear) {
                                _autoCaptureWarning.value = "Tuyệt vời, đang chụp..."
                                _autoCaptureTrigger.value += 1
                            } else {
                                _autoCaptureWarning.value = result.message
                            }
                        } else {
                            _autoCaptureWarning.value = "Lỗi phân tích AI"
                        }
                    }
                }
            }
        } else if (!_isAutoScanning.value) {
            _autoCaptureWarning.value = null
        }
    }

    fun clearCameraFrame() {
        localServer?.latestCameraFrameBase64 = null
    }

    // --- Grading Actions ---

    fun gradePhoto(bitmap: Bitmap) {
        gradePhotos(listOf(bitmap))
    }

    fun gradePhotos(bitmaps: List<Bitmap>) {
        _gradingUiState.value = GradingUiState.Loading
        val activeConfig = _configState.value
        viewModelScope.launch(Dispatchers.IO) {
            val result = GeminiClient.gradeExam(getApplication(), bitmaps, activeConfig)
            launch(Dispatchers.Main) {
                if (result.isSuccess) {
                    val rawResult = result.getOrThrow()
                    val gradingResult = rawResult.copy(
                        className = activeConfig.className,
                        examName = activeConfig.examName
                    )
                    // Save to database
                    viewModelScope.launch(Dispatchers.IO) {
                        val savedId = repository.insert(gradingResult)
                        val finalResult = gradingResult.copy(id = savedId.toInt())
                        _gradingUiState.value = GradingUiState.Success(finalResult)
                    }
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Có lỗi xảy ra trong quá trình chấm thi AI."
                    _gradingUiState.value = GradingUiState.Error(errorMsg)
                }
            }
        }
    }

    fun updateResultStudentName(id: Int, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateStudentName(id, newName)
            val currentState = _gradingUiState.value
            if (currentState is GradingUiState.Success && currentState.result.id == id) {
                val updatedResult = currentState.result.copy(studentName = newName)
                launch(Dispatchers.Main) {
                    _gradingUiState.value = GradingUiState.Success(updatedResult)
                }
            }
        }
    }

    fun resetGradingUiState() {
        _gradingUiState.value = GradingUiState.Idle
    }

    // --- Historical Database Actions ---

    fun deleteResult(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopServer()
    }
}

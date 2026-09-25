import re

with open('./app/src/main/java/com/example/ui/GradingViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

state_flows = """    private val _isAutoScanning = MutableStateFlow(false)
    val isAutoScanning: StateFlow<Boolean> = _isAutoScanning.asStateFlow()

    private val _autoCaptureTrigger = MutableStateFlow(0)
    val autoCaptureTrigger: StateFlow<Int> = _autoCaptureTrigger.asStateFlow()

    private val _autoCaptureWarning = MutableStateFlow<String?>(null)
    val autoCaptureWarning: StateFlow<String?> = _autoCaptureWarning.asStateFlow()

    private var lastAnalysisTime = 0L
    private var isAnalyzing = false
"""

content = content.replace("    private val _isAutoScanning = MutableStateFlow(false)\n    val isAutoScanning: StateFlow<Boolean> = _isAutoScanning.asStateFlow()", state_flows)

broadcast_func = """    fun broadcastCameraFrame(bitmap: Bitmap) {
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
    }"""

content = re.sub(
    r"    fun broadcastCameraFrame\(bitmap: Bitmap\) \{.*?\n    \}",
    broadcast_func,
    content,
    flags=re.DOTALL
)

with open('./app/src/main/java/com/example/ui/GradingViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)

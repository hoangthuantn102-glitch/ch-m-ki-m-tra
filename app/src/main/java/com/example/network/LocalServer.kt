package com.example.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.data.AnswerKeyParser
import com.example.data.ExamConfig
import com.example.data.GradingRepository
import com.example.data.GradingResult
import com.example.data.TestType
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalServer(
    private val context: Context,
    private val repository: GradingRepository,
    private val onConfigChanged: (ExamConfig) -> Unit,
    private val onNewResultGraded: (GradingResult) -> Unit,
    private val getActiveConfig: () -> ExamConfig,
    private val onServerStarted: (String) -> Unit, // Callback when server starts with IP:Port
    private val onServerStopped: () -> Unit,
    private val onRemoteAction: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Volatile
    var latestCameraFrameBase64: String? = null

    fun updateLatestCameraFrame(bitmap: Bitmap) {
        try {
            val maxDim = 480
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val scale = maxDim.toFloat() / Math.max(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }
            val outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            val bytes = outputStream.toByteArray()
            latestCameraFrameBase64 = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("LocalServer", "Error updating camera frame", e)
        }
    }

    val port = 8080

    fun start() {
        if (isRunning) return
        isRunning = true
        serverScope.launch {
            try {
                serverSocket = ServerSocket(port)
                val ip = getLocalIpAddress() ?: "localhost"
                withContext(Dispatchers.Main) {
                    onServerStarted("http://$ip:$port")
                }
                Log.d("LocalServer", "Server started at http://$ip:$port")

                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    launch {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e("LocalServer", "Error in server loop", e)
                stop()
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("LocalServer", "Error closing socket", e)
        }
        serverSocket = null
        try {
            onServerStopped()
        } catch (e: Exception) {
            Log.e("LocalServer", "Error in onServerStopped callback", e)
        }
        try {
            serverScope.coroutineContext.cancelChildren()
        } catch (e: Exception) {
            Log.e("LocalServer", "Error cancelling coroutine children", e)
        }
        Log.d("LocalServer", "Server stopped")
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val output = BufferedOutputStream(s.getOutputStream())

                val requestLine = reader.readLine() ?: return
                Log.d("LocalServer", "Request: $requestLine")

                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1]

                // Parse headers
                var contentLength = 0
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.isEmpty()) break
                    if (line!!.lowercase().startsWith("content-length:")) {
                        contentLength = line!!.substring(15).trim().toIntOrNull() ?: 0
                    }
                }

                when {
                    method == "GET" && path == "/" -> {
                        sendHtmlResponse(output, getHtmlDashboard())
                    }
                    method == "GET" && path == "/api/status" -> {
                        val config = getActiveConfig()
                        val json = serializeConfig(config)
                        sendJsonResponse(output, json)
                    }
                    method == "GET" && path == "/api/results" -> {
                        val results = repository.allResults.first()
                        val json = serializeResults(results)
                        sendJsonResponse(output, json)
                    }
                    method == "GET" && path == "/api/configs" -> {
                        val configs = repository.allConfigs.first()
                        val listType = Types.newParameterizedType(List::class.java, ExamConfig::class.java)
                        val json = moshi.adapter<List<ExamConfig>>(listType).toJson(configs)
                        sendJsonResponse(output, json)
                    }
                    method == "GET" && path == "/api/camera_frame" -> {
                        val frame = latestCameraFrameBase64 ?: ""
                        sendJsonResponse(output, "{\"frame\":\"$frame\"}")
                    }
                    method == "GET" && (path == "/download-standalone-zip" || path == "/api/download_web_zip") -> {
                        try {
                            val assetStream = context.assets.open("ChamTracNghiem_PC_WebApp.zip")
                            val bytes = assetStream.readBytes()
                            assetStream.close()
                            output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                            output.write("Content-Type: application/zip\r\n".toByteArray())
                            output.write("Content-Disposition: attachment; filename=\"ChamTracNghiem_PC_WebApp.zip\"\r\n".toByteArray())
                            output.write("Content-Length: ${bytes.size}\r\n".toByteArray())
                            output.write("Connection: close\r\n\r\n".toByteArray())
                            output.write(bytes)
                            output.flush()
                        } catch (e: Exception) {
                            Log.e("LocalServer", "Error sending standalone zip", e)
                            sendJsonResponse(output, "{\"error\":\"File not found\"}", 404)
                        }
                    }
                    method == "GET" && path.startsWith("/api/image") -> {
                        val query = path.substringAfter("?path=")
                        val decodedPath = java.net.URLDecoder.decode(query, "UTF-8")
                        val file = File(decodedPath)
                        if (file.exists()) {
                            val mime = "image/jpeg"
                            output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                            output.write("Content-Type: $mime\r\n".toByteArray())
                            output.write("Content-Length: ${file.length()}\r\n".toByteArray())
                            output.write("Connection: close\r\n\r\n".toByteArray())
                            file.inputStream().use { it.copyTo(output) }
                        } else {
                            output.write("HTTP/1.1 404 Not Found\r\n\r\n".toByteArray())
                        }
                    }
                    method == "POST" && path == "/api/select_config" -> {
                        val body = readBody(reader, contentLength)
                        val map = moshi.adapter(Map::class.java).fromJson(body) ?: emptyMap<Any, Any>()
                        val configId = (map["id"] as? Double)?.toInt() ?: map["id"]?.toString()?.toIntOrNull()
                        if (configId != null) {
                            val configs = repository.allConfigs.first()
                            val target = configs.firstOrNull { it.id == configId }
                            if (target != null) {
                                withContext(Dispatchers.Main) {
                                    onConfigChanged(target)
                                }
                                sendJsonResponse(output, "{\"status\":\"success\"}")
                            } else {
                                sendJsonResponse(output, "{\"status\":\"error\",\"message\":\"Config not found\"}", 404)
                            }
                        } else {
                            sendJsonResponse(output, "{\"status\":\"error\",\"message\":\"Missing config ID\"}", 400)
                        }
                    }
                    method == "POST" && path == "/api/action" -> {
                        val body = readBody(reader, contentLength)
                        val map = moshi.adapter(Map::class.java).fromJson(body) ?: emptyMap<Any, Any>()
                        val action = map["action"] as? String ?: ""
                        if (action.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                onRemoteAction(action)
                            }
                            sendJsonResponse(output, "{\"status\":\"success\"}")
                        } else {
                            sendJsonResponse(output, "{\"status\":\"error\",\"message\":\"Missing action\"}", 400)
                        }
                    }
                    method == "POST" && path == "/api/settings" -> {
                        val body = readBody(reader, contentLength)
                        val success = handleUpdateSettings(body)
                        val responseJson = if (success) "{\"status\":\"success\"}" else "{\"status\":\"error\",\"message\":\"Sai định dạng dữ liệu\"}"
                        sendJsonResponse(output, responseJson)
                    }
                    method == "POST" && path == "/api/grade" -> {
                        val body = readBody(reader, contentLength)
                        val result = handleRemoteGrade(body)
                        if (result != null) {
                            val json = moshi.adapter(GradingResult::class.java).toJson(result)
                            sendJsonResponse(output, json)
                        } else {
                            sendJsonResponse(output, "{\"status\":\"error\",\"message\":\"Chấm bài từ xa thất bại. Kiểm tra kết nối mạng hoặc ảnh tải lên.\"}", 400)
                        }
                    }
                    method == "POST" && path == "/api/clear" -> {
                        repository.clearAll()
                        sendJsonResponse(output, "{\"status\":\"success\"}")
                    }
                    else -> {
                        sendJsonResponse(output, "{\"error\":\"Not Found\"}", 404)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LocalServer", "Error handling client", e)
        }
    }

    private fun readBody(reader: BufferedReader, length: Int): String {
        val charArray = CharArray(length)
        var readTotal = 0
        while (readTotal < length) {
            val read = reader.read(charArray, readTotal, length - readTotal)
            if (read == -1) break
            readTotal += read
        }
        return String(charArray)
    }

    private fun sendHtmlResponse(out: BufferedOutputStream, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun sendJsonResponse(out: BufferedOutputStream, json: String, statusCode: Int = 200) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val statusText = when (statusCode) {
            200 -> "200 OK"
            400 -> "400 Bad Request"
            404 -> "404 Not Found"
            else -> "500 Internal Server Error"
        }
        val header = "HTTP/1.1 $statusText\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" + // CORS support for easy development
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.write(bytes)
        out.flush()
    }

    // --- API Handlers ---

    private fun handleUpdateSettings(body: String): Boolean {
        return try {
            val map = moshi.adapter(Map::class.java).fromJson(body) ?: return false
            val current = getActiveConfig()
            val totalQuestions = (map["totalQuestions"] as? Double)?.toInt() ?: current.totalQuestions
            val totalPages = (map["totalPages"] as? Double)?.toInt() ?: map["totalPages"]?.toString()?.toIntOrNull() ?: current.totalPages
            val pointsPerCorrect = (map["pointsPerCorrect"] as? Double) ?: current.pointsPerCorrect
            val testTypeStr = (map["testType"] as? String) ?: current.testType.name
            val testType = TestType.valueOf(testTypeStr)

            // Parse masterKeys
            val masterKeysRaw = map["masterKeys"] as? Map<*, *>
            val masterKeys = mutableMapOf<Int, String>()
            masterKeysRaw?.forEach { (k, v) ->
                val qNum = k.toString().toDoubleOrNull()?.toInt() ?: k.toString().toIntOrNull()
                val ans = v.toString()
                if (qNum != null) {
                    masterKeys[qNum] = ans
                }
            }

            // Parse selectedQuestions
            val selectedQuestionsRaw = map["selectedQuestions"] as? List<*>
            val selectedQuestions = mutableSetOf<Int>()
            selectedQuestionsRaw?.forEach {
                val qNum = it.toString().toDoubleOrNull()?.toInt() ?: it.toString().toIntOrNull()
                if (qNum != null) {
                    selectedQuestions.add(qNum)
                }
            }

            val newConfig = current.copy(
                testType = testType,
                totalQuestions = totalQuestions,
                totalPages = totalPages,
                pointsPerCorrect = pointsPerCorrect,
                masterKeys = masterKeys,
                selectedQuestions = selectedQuestions
            )

            // Dispatch update to ViewModel/UI
            onConfigChanged(newConfig)
            true
        } catch (e: Exception) {
            Log.e("LocalServer", "Error parsing updated settings", e)
            false
        }
    }

    private suspend fun handleRemoteGrade(body: String): GradingResult? {
        return try {
            val map = moshi.adapter(Map::class.java).fromJson(body) ?: return null
            val base64Image = map["image"] as? String ?: return null
            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null

            // Use active configurations
            val config = getActiveConfig()
            val result = GeminiClient.gradeExam(context, listOf(bitmap), config)

            if (result.isSuccess) {
                val gradingResult = result.getOrThrow()
                // Save to db
                val savedId = repository.insert(gradingResult)
                val finalResult = gradingResult.copy(id = savedId.toInt())

                // Callback for UI updates
                withContext(Dispatchers.Main) {
                    onNewResultGraded(finalResult)
                }
                finalResult
            } else {
                Log.e("LocalServer", "AI grading error: ${result.exceptionOrNull()?.message}")
                null
            }
        } catch (e: Exception) {
            Log.e("LocalServer", "Error in remote grading", e)
            null
        }
    }

    // --- JSON Serializers ---

    private fun serializeConfig(config: ExamConfig): String {
        val keysMap = config.masterKeys.mapKeys { it.key.toString() }
        val map = mapOf(
            "totalQuestions" to config.totalQuestions,
            "totalPages" to config.totalPages,
            "pointsPerCorrect" to config.pointsPerCorrect,
            "testType" to config.testType.name,
            "masterKeys" to keysMap,
            "selectedQuestions" to config.selectedQuestions.toList()
        )
        return moshi.adapter(Map::class.java).toJson(map)
    }

    private fun serializeResults(results: List<GradingResult>): String {
        val listType = Types.newParameterizedType(List::class.java, GradingResult::class.java)
        return moshi.adapter<List<GradingResult>>(listType).toJson(results)
    }

    // --- Utilities ---

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("LocalServer", "Error getting IP Address", ex)
        }
        return null
    }

    // --- Embedded HTML Dashboard Source ---

    private fun getHtmlDashboard(): String {
        val d = "$"
        return """
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chấm Thi Trắc Nghiệm AI - PC Remote Control</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <style>
        body {
            font-family: 'Plus Jakarta Sans', sans-serif;
            background-color: #f8fafc;
        }
    </style>
</head>
<body class="text-slate-800">
    <!-- Navigation Bar -->
    <nav class="bg-indigo-600 text-white shadow-md">
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div class="flex items-center justify-between h-16">
                <div class="flex items-center space-x-3">
                    <span class="text-2xl">📝</span>
                    <span class="font-bold text-lg tracking-tight">Chấm Thi Trắc Nghiệm AI</span>
                    <span class="bg-emerald-500 text-xs px-2 py-0.5 rounded-full font-medium animate-pulse">Kết Nối Ổn Định</span>
                </div>
                <div class="text-sm font-mono bg-indigo-700 px-3 py-1.5 rounded-lg border border-indigo-500/30">
                    Địa chỉ thiết bị: <span id="device-ip-span"></span>
                </div>
            </div>
        </div>
    </nav>

    <!-- Main Content Grid -->
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <!-- Standalone PC Web App Download Banner -->
        <div class="bg-gradient-to-r from-emerald-600 via-teal-600 to-indigo-700 text-white p-5 rounded-2xl shadow-lg flex flex-wrap items-center justify-between gap-4 mb-8 border border-white/10">
            <div class="flex items-center space-x-3.5">
                <span class="text-3xl bg-white/20 p-2.5 rounded-xl backdrop-blur-md">💻</span>
                <div>
                    <h3 class="font-extrabold text-base text-white flex items-center gap-2">
                        Bản Web App Độc Lập Cho Máy Tính (Chạy Trực Tiếp Không Cần Điện Thoại)
                        <span class="bg-white/25 text-[11px] font-semibold uppercase px-2 py-0.5 rounded">Mới</span>
                    </h3>
                    <p class="text-xs text-emerald-100 mt-0.5">Dùng trực tiếp Webcam USB / Camera laptop trên máy tính để soi bài thi và chấm điểm bằng Gemini AI.</p>
                </div>
            </div>
            <a href="/download-standalone-zip" download="ChamTracNghiem_PC_WebApp.zip" class="bg-white hover:bg-emerald-50 text-emerald-800 font-extrabold px-5 py-3 rounded-xl shadow-md transition flex items-center gap-2 text-xs uppercase tracking-wider">
                <span class="text-base">📥</span>
                <span>Tải Trọn Gói (.ZIP) Về PC</span>
            </a>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-12 gap-8">
            
            <!-- Left Panel: Remote Controls & Camera & Configs (Occupies most space) -->
            <div class="lg:col-span-8 xl:col-span-9 flex flex-col space-y-6">
                
                <!-- PC Remote Controls & Camera Merged -->
                <div class="bg-indigo-50 border border-indigo-100 p-6 rounded-2xl shadow-sm flex flex-col">
                    <h2 class="text-xl font-bold mb-3 flex items-center space-x-2 text-indigo-900">
                        <span>🎮</span>
                        <span>Điều Khiển Điện Thoại Từ PC</span>
                    </h2>
                    <p class="text-sm text-indigo-700/80 mb-4">Theo dõi camera và điều khiển hoạt động chấm thi trực tiếp.</p>
                    
                    <!-- Camera View -->
                    <div class="relative bg-slate-900 rounded-2xl p-2 border border-slate-800 flex flex-col items-center justify-center min-h-[450px] lg:min-h-[550px] mb-6 overflow-hidden">
                        <img id="pc-live-camera-feed" src="" class="hidden max-h-[700px] w-full object-contain rounded-xl" alt="Live Camera Feed" />
                        <div id="pc-live-camera-placeholder" class="text-center p-6 text-slate-400">
                            <span class="text-5xl block mb-3">📷</span>
                            <span id="pc-camera-status" class="text-base">Đang chờ tín hiệu camera từ điện thoại...</span>
                        </div>
                        <div class="absolute top-4 right-4 bg-slate-800/80 backdrop-blur-md px-3 py-1.5 rounded-full text-xs text-emerald-400 font-semibold flex items-center gap-1.5 border border-slate-700">
                            <span class="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse"></span> Live Sync
                        </div>
                    </div>

                    <!-- Remote Actions -->
                    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        <button type="button" onclick="triggerRemoteAction('capture')" class="bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-3.5 px-4 rounded-xl shadow-md transition-all flex items-center justify-center space-x-2">
                            <span class="text-xl">📸</span>
                            <span class="text-sm font-semibold">Chụp Bài Ngay</span>
                        </button>
                        <button type="button" onclick="triggerRemoteAction('next')" class="bg-emerald-600 hover:bg-emerald-700 text-white font-bold py-3.5 px-4 rounded-xl shadow-md transition-all flex items-center justify-center space-x-2">
                            <span class="text-xl">➡️</span>
                            <span class="text-sm font-semibold">Chấm Bài Tiếp</span>
                        </button>
                        <button type="button" onclick="triggerRemoteAction('toggle_auto')" id="remote-auto-btn" class="sm:col-span-2 lg:col-span-1 bg-white hover:bg-slate-50 text-indigo-700 border border-indigo-200 font-bold py-3.5 px-4 rounded-xl shadow-sm transition-all text-sm flex justify-center items-center space-x-2">
                            <span class="text-xl">🤖</span>
                            <span>Bật/Tắt Tự Động Quét</span>
                        </button>
                    </div>

                    <div id="remote-grading-loader" class="hidden flex flex-col items-center justify-center p-6 space-y-3 mt-4">
                        <div class="w-10 h-10 border-4 border-indigo-600 border-t-transparent rounded-full animate-spin"></div>
                        <span class="text-sm text-indigo-700 font-medium">Đang truyền ảnh sang máy & AI đang chấm...</span>
                        <span class="text-xs text-indigo-500">(Quá trình này có thể mất 10-15 giây)</span>
                    </div>
                </div>

                <!-- Configs Row -->
                <div class="grid grid-cols-1 xl:grid-cols-2 gap-6">
                    <!-- Selected Class & Exam Config -->
                    <div class="bg-white p-6 rounded-2xl shadow-sm border border-slate-100 flex flex-col">
                        <h2 class="text-lg font-bold mb-3 flex items-center space-x-2 text-slate-900">
                            <span>🏫</span>
                            <span>Lớp & Bài Kiểm Tra Đang Chấm</span>
                        </h2>
                        <p class="text-xs text-slate-500 mb-4">Chọn đề thi và lớp học tương ứng được đồng bộ từ điện thoại để đối chiếu đáp án chính xác.</p>
                        
                        <div class="space-y-3">
                            <div>
                                <select id="config-selector" onchange="selectSavedConfig(this.value)" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2.5 text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500">
                                    <option value="">-- Đang tải danh sách đề thi --</option>
                                </select>
                            </div>
                        </div>

                        <h2 class="text-sm font-bold mt-8 mb-2 flex items-center space-x-2 text-slate-900">
                            <span>📤</span>
                            <span>Hoặc Tải File Ảnh Từ Máy Tính</span>
                        </h2>
                        
                        <div id="remote-upload-area" class="border-2 border-dashed border-slate-200 rounded-2xl p-4 text-center cursor-pointer hover:border-indigo-500 hover:bg-slate-50/50 transition-all flex flex-col items-center justify-center space-y-1 flex-1">
                            <span class="text-2xl">📤</span>
                            <span class="text-xs font-medium text-slate-600">Kéo thả hoặc Click để chọn ảnh bài thi</span>
                            <span class="text-[10px] text-slate-400">Định dạng JPEG, PNG</span>
                            <input type="file" id="remote-image-input" accept="image/*" class="hidden" onchange="handleRemoteImageUpload(this)">
                        </div>
                    </div>

                    <!-- Cấu Hình Chi Tiết -->
                    <div class="bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
                        <h2 class="text-lg font-bold mb-4 flex items-center space-x-2 text-slate-900">
                            <span>⚙️</span>
                            <span>Cấu Hình Chi Tiết</span>
                        </h2>
                        
                        <form id="settings-form" class="space-y-4">
                            <div>
                                <label class="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Loại Hình Bài Thi</label>
                                <select id="setting-test-type" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2.5 text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500">
                                    <option value="ANSWER_TABLE">Học sinh điền đáp án vào BẢNG/Ô ĐÁP ÁN</option>
                                    <option value="CIRCLED_ON_SHEET">Học sinh khoanh tròn TRÊN ĐỀ THI</option>
                                </select>
                            </div>
                            
                            <div class="grid grid-cols-3 gap-3">
                                <div>
                                    <label class="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Số Trang Bài Thi</label>
                                    <select id="setting-total-pages" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500">
                                        <option value="1">1 Trang</option>
                                        <option value="2">2 Trang</option>
                                        <option value="3">3 Trang</option>
                                        <option value="4">4 Trang</option>
                                        <option value="5">5 Trang</option>
                                    </select>
                                </div>
                                <div>
                                    <label class="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Tổng Số Câu</label>
                                    <input type="number" id="setting-total-questions" min="1" max="100" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500">
                                </div>
                                <div>
                                    <label class="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Điểm / Câu</label>
                                    <input type="number" id="setting-points" step="0.05" min="0.01" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500">
                                </div>
                            </div>

                            <div>
                                <label class="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Lựa Chọn Chấm Thi</label>
                                <div class="flex flex-wrap gap-1.5 p-3 bg-slate-50 border border-slate-200 rounded-xl max-h-24 overflow-y-auto" id="selected-questions-container">
                                </div>
                            </div>

                            <div>
                                <div class="flex justify-between items-center mb-1">
                                    <label class="block text-xs font-semibold text-slate-500 uppercase tracking-wider">Khóa Đáp Án (Master Key)</label>
                                    <button type="button" onclick="triggerCsvUpload()" class="text-xs font-medium text-indigo-600 hover:text-indigo-800">📥 File CSV</button>
                                    <input type="file" id="csv-file-input" accept=".csv, .txt" class="hidden" onchange="handleCsvFile(this)">
                                </div>
                                <textarea id="setting-master-keys" rows="4" class="w-full bg-slate-50 border border-slate-200 font-mono text-sm rounded-xl p-3 text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500 placeholder-slate-300" placeholder="1,A&#10;2,B&#10;3,C&#10;..."></textarea>
                            </div>

                            <button type="submit" class="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-semibold py-2.5 px-4 rounded-xl shadow-sm transition-all text-sm flex justify-center items-center space-x-2">
                                <span>💾</span>
                                <span>Cập Nhật Tới ĐT</span>
                            </button>
                        </form>
                    </div>
                </div>
            </div>

            <!-- Right Panel: Historical Results Table -->
            <div class="lg:col-span-4 xl:col-span-3 flex flex-col space-y-6">
                <div class="bg-white p-6 rounded-2xl shadow-sm border border-slate-100 flex flex-col h-[850px]">
                    <div class="flex justify-between items-start mb-6">
                        <div>
                            <h2 class="text-xl font-bold text-slate-900 flex items-center space-x-2">
                                <span>📊</span>
                                <span>Kết Quả Chấm</span>
                            </h2>
                            <p class="text-xs text-slate-500 mt-1">Lịch sử chấm thi.</p>
                        </div>
                        <button onclick="clearAllResults()" class="text-xs font-semibold text-rose-600 hover:text-white hover:bg-rose-600 border border-rose-200 px-3 py-2 rounded-xl transition-all" title="Xóa Toàn Bộ Lịch Sử">
                            🗑️ Xóa
                        </button>
                    </div>

                    <!-- Search / Quick Filter -->
                    <div class="mb-4">
                        <input type="text" id="search-input" oninput="filterResults()" placeholder="Tìm kiếm tên..." class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-2 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500">
                    </div>

                    <!-- Table (Vertical/Compact layout) -->
                    <div class="overflow-y-auto flex-1 rounded-xl bg-slate-50 p-2 space-y-3" id="results-table-body">
                    </div>
                </div>
            </div>
        </div>
    </main>

    <!-- Detail Modal -->
    <div id="detail-modal" class="hidden fixed inset-0 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4 z-50">
        <div class="bg-white rounded-2xl shadow-xl w-full max-w-5xl overflow-hidden flex flex-col max-h-[90vh]">
            <!-- Header -->
            <div class="bg-indigo-600 text-white px-6 py-4 flex justify-between items-center">
                <div>
                    <h3 id="modal-student-name" class="text-lg font-bold">Tên học sinh</h3>
                    <p id="modal-test-meta" class="text-xs text-indigo-100 mt-0.5">Thông tin bài thi</p>
                </div>
                <button onclick="closeModal()" class="text-white/80 hover:text-white text-2xl font-bold">&times;</button>
            </div>
            
            <!-- Body -->
            <div class="p-6 overflow-y-auto flex-1">
                <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
                    <!-- Left Column: Original Student Paper Image with Toolbar -->
                    <div id="modal-image-container" class="lg:col-span-5 bg-slate-900 rounded-2xl p-3 border border-slate-800 flex flex-col items-center justify-center">
                        <div class="w-full flex justify-between items-center mb-2 px-1">
                            <span class="text-[11px] font-bold text-slate-300 flex items-center gap-1 uppercase tracking-wide">
                                📷 Bài Làm Gốc
                            </span>
                            <div class="flex items-center gap-1">
                                <button type="button" onclick="rotateStudentImg(-90)" title="Xoay trái" class="bg-slate-800 hover:bg-slate-700 text-slate-200 p-1 rounded text-xs font-semibold border border-slate-700 transition-all">↺</button>
                                <button type="button" onclick="rotateStudentImg(90)" title="Xoay phải" class="bg-slate-800 hover:bg-slate-700 text-slate-200 p-1 rounded text-xs font-semibold border border-slate-700 transition-all">↻</button>
                                <button type="button" onclick="zoomStudentImg(0.2)" title="Phóng to" class="bg-slate-800 hover:bg-slate-700 text-slate-200 p-1 rounded text-xs font-semibold border border-slate-700 transition-all">🔍+</button>
                                <button type="button" onclick="zoomStudentImg(-0.2)" title="Thu nhỏ" class="bg-slate-800 hover:bg-slate-700 text-slate-200 p-1 rounded text-xs font-semibold border border-slate-700 transition-all">🔍-</button>
                                <button type="button" onclick="resetStudentImg()" title="Đặt lại" class="bg-slate-800 hover:bg-slate-700 text-slate-200 p-1 rounded text-xs font-semibold border border-slate-700 transition-all">🔄</button>
                                <button type="button" onclick="openFullscreenImg()" title="Toàn màn hình" class="bg-indigo-600 hover:bg-indigo-500 text-white p-1 rounded text-xs font-semibold border border-indigo-500 transition-all">⛶</button>
                            </div>
                        </div>
                        <div class="w-full overflow-hidden rounded-xl bg-black flex items-center justify-center p-1 min-h-[250px] cursor-pointer" onclick="openFullscreenImg()">
                            <img id="modal-student-image" src="" class="max-h-[380px] w-full object-contain rounded-lg shadow-md transition-transform duration-200" alt="Bài làm học sinh" />
                        </div>
                    </div>

                    <!-- Right Column: Result Overview & Answer Grid -->
                    <div id="modal-details-container" class="lg:col-span-7 flex flex-col gap-4">
                        <div class="grid grid-cols-3 gap-3">
                            <div class="bg-slate-50 p-3.5 rounded-xl text-center border border-slate-100">
                                <span class="block text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-1">Tổng Điểm</span>
                                <span id="modal-score" class="text-xl font-bold text-indigo-600">0.0 / 10</span>
                            </div>
                            <div class="bg-slate-50 p-3.5 rounded-xl text-center border border-slate-100">
                                <span class="block text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-1">Số Câu Đúng</span>
                                <span id="modal-correct-ratio" class="text-xl font-bold text-emerald-600">0 / 0</span>
                            </div>
                            <div class="bg-slate-50 p-3.5 rounded-xl text-center border border-slate-100">
                                <span class="block text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-1">Ngày Chấm</span>
                                <span id="modal-date" class="text-xs font-semibold text-slate-600 flex h-full items-center justify-center">--/--</span>
                            </div>
                        </div>

                        <!-- Answer Sheet Detail -->
                        <div>
                            <h4 class="text-xs font-bold text-slate-700 uppercase tracking-wider mb-2.5">Chi tiết đáp án từng câu</h4>
                            <div class="grid grid-cols-2 sm:grid-cols-4 gap-2.5 max-h-[300px] overflow-y-auto pr-1" id="modal-answers-grid">
                                <!-- Dynamic Grid items -->
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            
            <!-- Footer -->
            <div class="bg-slate-50 border-t border-slate-100 px-6 py-3.5 flex justify-end">
                <button onclick="closeModal()" class="bg-slate-200 hover:bg-slate-300 text-slate-700 font-semibold py-2 px-5 rounded-xl text-sm transition-all">Đóng</button>
            </div>
        </div>
    </div>

    <!-- Fullscreen Lightbox Modal -->
    <div id="fullscreen-image-modal" class="hidden fixed inset-0 bg-black/95 backdrop-blur-md flex flex-col z-[100] p-4">
        <div class="flex justify-between items-center text-white px-4 py-2 border-b border-slate-800">
            <div class="flex items-center gap-3">
                <span class="text-sm font-bold text-indigo-400">📷 Xem Chi Tiết Bài Làm Gốc (Toàn Màn Hình)</span>
                <span id="fullscreen-student-label" class="text-xs text-slate-400"></span>
            </div>
            <div class="flex items-center gap-2">
                <button type="button" onclick="rotateStudentImgFs(-90)" class="bg-slate-800 hover:bg-slate-700 text-white px-3 py-1.5 rounded-xl text-xs font-semibold border border-slate-700">↺ Xoay Trái</button>
                <button type="button" onclick="rotateStudentImgFs(90)" class="bg-slate-800 hover:bg-slate-700 text-white px-3 py-1.5 rounded-xl text-xs font-semibold border border-slate-700">↻ Xoay Phải</button>
                <button type="button" onclick="zoomStudentImgFs(0.25)" class="bg-slate-800 hover:bg-slate-700 text-white px-3 py-1.5 rounded-xl text-xs font-semibold border border-slate-700">🔍 Phóng To</button>
                <button type="button" onclick="zoomStudentImgFs(-0.25)" class="bg-slate-800 hover:bg-slate-700 text-white px-3 py-1.5 rounded-xl text-xs font-semibold border border-slate-700">🔍 Thu Nhỏ</button>
                <button type="button" onclick="resetStudentImgFs()" class="bg-slate-800 hover:bg-slate-700 text-white px-3 py-1.5 rounded-xl text-xs font-semibold border border-slate-700">🔄 Đặt Lại</button>
                <button type="button" onclick="closeFullscreenImg()" class="bg-rose-600 hover:bg-rose-500 text-white px-4 py-1.5 rounded-xl text-xs font-bold shadow-md">✕ Đóng</button>
            </div>
        </div>
        <div class="flex-1 overflow-auto flex items-center justify-center p-4">
            <img id="fullscreen-student-image" src="" class="max-h-[90vh] max-w-[90vw] object-contain transition-transform duration-150" alt="Full image" />
        </div>
    </div>

    <!-- Toast Notification -->
    <div id="toast" class="fixed bottom-6 right-6 transform translate-y-20 opacity-0 bg-slate-900 text-white px-4 py-3 rounded-xl shadow-lg flex items-center space-x-2 transition-all duration-300 z-50 text-sm">
        <span id="toast-icon">✨</span>
        <span id="toast-msg">Thông báo thành công</span>
    </div>

    <script>
        // Set device IP text
        document.getElementById('device-ip-span').innerText = window.location.host;

        let activeConfig = null;
        let gradingHistory = [];

        // Image Manipulation State
        let imgAngle = 0;
        let imgZoom = 1;
        let fsImgAngle = 0;
        let fsImgZoom = 1;

        function updateImgTransform() {
            const img = document.getElementById('modal-student-image');
            if (img) {
                img.style.transform = "rotate(" + imgAngle + "deg) scale(" + imgZoom + ")";
            }
        }

        function rotateStudentImg(deg) {
            imgAngle = (imgAngle + deg) % 360;
            updateImgTransform();
        }

        function zoomStudentImg(delta) {
            imgZoom = Math.max(0.3, Math.min(4.0, imgZoom + delta));
            updateImgTransform();
        }

        function resetStudentImg() {
            imgAngle = 0;
            imgZoom = 1;
            updateImgTransform();
        }

        function updateFsImgTransform() {
            const img = document.getElementById('fullscreen-student-image');
            if (img) {
                img.style.transform = "rotate(" + fsImgAngle + "deg) scale(" + fsImgZoom + ")";
            }
        }

        function openFullscreenImg() {
            const srcImg = document.getElementById('modal-student-image');
            const studentName = document.getElementById('modal-student-name').innerText;
            if (!srcImg || !srcImg.src || srcImg.src.length < 20) return;

            document.getElementById('fullscreen-student-image').src = srcImg.src;
            document.getElementById('fullscreen-student-label').innerText = studentName;
            fsImgAngle = imgAngle;
            fsImgZoom = imgZoom > 1 ? imgZoom : 1.2;
            updateFsImgTransform();
            document.getElementById('fullscreen-image-modal').classList.remove('hidden');
        }

        function closeFullscreenImg() {
            document.getElementById('fullscreen-image-modal').classList.add('hidden');
        }

        function rotateStudentImgFs(deg) {
            fsImgAngle = (fsImgAngle + deg) % 360;
            updateFsImgTransform();
        }

        function zoomStudentImgFs(delta) {
            fsImgZoom = Math.max(0.3, Math.min(5.0, fsImgZoom + delta));
            updateFsImgTransform();
        }

        function resetStudentImgFs() {
            fsImgAngle = 0;
            fsImgZoom = 1;
            updateFsImgTransform();
        }

        // Live Camera Frame Polling
                async function fetchLiveCameraFrame() {
            try {
                const response = await fetch('/api/camera_frame');
                if (response.ok) {
                    const data = await response.json();
                    const feedImg = document.getElementById('pc-live-camera-feed');
                    const placeholder = document.getElementById('pc-live-camera-placeholder');
                    const statusTxt = document.getElementById('pc-camera-status');
                    if (data.frame && data.frame.length > 20) {
                        feedImg.src = data.frame;
                        feedImg.classList.remove('hidden');
                        placeholder.classList.add('hidden');
                    } else {
                        feedImg.classList.add('hidden');
                        placeholder.classList.remove('hidden');
                        if (statusTxt) statusTxt.innerText = "Đang chờ tín hiệu camera từ điện thoại...";
                    }
                }
            } catch (e) {
                // Ignore poll errors
            }
            setTimeout(fetchLiveCameraFrame, 50); // Recursive call for low latency
        }

        // Start live camera stream polling every 1.2s
        // Fetch handled recursively in fetchLiveCameraFrame

        // Fetch everything on load
        window.addEventListener('load', () => {
            fetchConfig();
            fetchResults();
            setupRemoteUpload();
            fetchLiveCameraFrame();
        });

        function showToast(msg, icon = '✨') {
            const toast = document.getElementById('toast');
            document.getElementById('toast-msg').innerText = msg;
            document.getElementById('toast-icon').innerText = icon;
            
            toast.classList.remove('translate-y-20', 'opacity-0');
            toast.classList.add('translate-y-0', 'opacity-100');
            
            setTimeout(() => {
                toast.classList.add('translate-y-20', 'opacity-0');
                toast.classList.remove('translate-y-0', 'opacity-100');
            }, 3000);
        }

        // --- Fetch API Data ---

        async function fetchConfig() {
            try {
                const response = await fetch('/api/status');
                activeConfig = await response.json();
                populateConfigUI();
                fetchSavedConfigs();
            } catch (err) {
                console.error('Lỗi tải cấu hình', err);
            }
        }

        async function fetchSavedConfigs() {
            try {
                const response = await fetch('/api/configs');
                const configs = await response.json();
                const selector = document.getElementById('config-selector');
                if (!selector) return;
                selector.innerHTML = '';
                
                if (configs.length === 0) {
                    selector.innerHTML = '<option value="">Chưa có đề thi nào trong CSDL</option>';
                    return;
                }
                
                configs.forEach(cfg => {
                    const option = document.createElement('option');
                    option.value = cfg.id;
                    option.text = `${d}{cfg.examName} - Lớp: ${d}{cfg.className} (${d}{cfg.totalQuestions} câu)`;
                    if (activeConfig && activeConfig.id === cfg.id) {
                        option.selected = true;
                    }
                    selector.appendChild(option);
                });
            } catch (err) {
                console.error("Lỗi tải đề thi", err);
            }
        }

        async function selectSavedConfig(id) {
            if (!id) return;
            try {
                const response = await fetch('/api/select_config', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ id: parseInt(id) })
                });
                const res = await response.json();
                if (res.status === 'success') {
                    showToast('Đã chuyển đổi đề thi thành công', '🏫');
                    fetchConfig();
                } else {
                    showToast('Lỗi khi đổi đề thi: ' + res.message, '❌');
                }
            } catch (err) {
                showToast('Lỗi kết nối đến máy', '❌');
            }
        }

        async function triggerRemoteAction(action) {
            try {
                const response = await fetch('/api/action', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: action })
                });
                const res = await response.json();
                if (res.status === 'success') {
                    if (action === 'capture') {
                        showToast('Đã gửi lệnh chụp ảnh tới điện thoại', '📸');
                    } else if (action === 'next') {
                        showToast('Đã sẵn sàng chấm bài mới', '➡️');
                    } else if (action === 'toggle_auto') {
                        showToast('Đã gửi lệnh bật/tắt Tự động quét', '🤖');
                    }
                } else {
                    showToast('Gửi lệnh thất bại', '❌');
                }
            } catch (err) {
                showToast('Không thể kết nối đến điện thoại', '❌');
            }
        }

        async function fetchResults() {
            try {
                const response = await fetch('/api/results');
                gradingHistory = await response.json();
                populateResultsUI(gradingHistory);
            } catch (err) {
                console.error('Lỗi tải lịch sử chấm', err);
            }
        }

        // --- Populate UI ---

        function populateConfigUI() {
            if (!activeConfig) return;
            
            document.getElementById('setting-test-type').value = activeConfig.testType;
            document.getElementById('setting-total-pages').value = activeConfig.totalPages || 1;
            document.getElementById('setting-total-questions').value = activeConfig.totalQuestions;
            document.getElementById('setting-points').value = activeConfig.pointsPerCorrect;

            // Populate Master keys text area
            const keysArray = [];
            for (let i = 1; i <= activeConfig.totalQuestions; i++) {
                const ans = activeConfig.masterKeys[i] || '';
                keysArray.push(i + "," + ans);
            }
            document.getElementById('setting-master-keys').value = keysArray.join('\n');

            // Generate selection of graded questions checkmarks
            const container = document.getElementById('selected-questions-container');
            container.innerHTML = '';
            
            for (let i = 1; i <= activeConfig.totalQuestions; i++) {
                const isSelected = activeConfig.selectedQuestions.length === 0 || activeConfig.selectedQuestions.includes(i);
                const btn = document.createElement('button');
                btn.type = 'button';
                btn.id = 'q-select-' + i;
                btn.className = "px-2.5 py-1 text-xs font-semibold rounded-lg border transition-all " + 
                    (isSelected ? "bg-indigo-50 text-indigo-600 border-indigo-200" : "bg-white text-slate-400 border-slate-200");
                btn.innerText = "Câu " + i;
                btn.dataset.selected = isSelected ? "true" : "false";
                btn.dataset.num = i;
                
                btn.addEventListener('click', () => {
                    const sel = btn.dataset.selected === "true";
                    if (sel) {
                        btn.dataset.selected = "false";
                        btn.className = "px-2.5 py-1 text-xs font-semibold rounded-lg border border-slate-200 bg-white text-slate-400 transition-all";
                    } else {
                        btn.dataset.selected = "true";
                        btn.className = "px-2.5 py-1 text-xs font-semibold rounded-lg border border-indigo-200 bg-indigo-50 text-indigo-600 transition-all";
                    }
                });
                
                container.appendChild(btn);
            }
        }

        function populateResultsUI(historyList) {
            const body = document.getElementById('results-table-body');
            body.innerHTML = '';

            if (historyList.length === 0) {
                body.innerHTML = `
                    <div class="p-8 text-center text-sm text-slate-400 border border-dashed border-slate-300 rounded-xl">Chưa có bài thi nào được chấm</div>
                `;
                return;
            }

            historyList.forEach(item => {
                const dateText = formatDate(item.timestamp);
                const typeText = item.testType === "ANSWER_TABLE" ? "Bảng Đáp Án" : "Khoanh Trên Đề";

                const card = document.createElement('div');
                card.className = "bg-white p-4 rounded-xl shadow-sm border border-slate-100 hover:border-indigo-300 transition-all cursor-pointer";
                card.onclick = () => openResultDetail(item.id);
                
                card.innerHTML = `
                    <div class="flex justify-between items-start mb-2">
                        <div class="font-bold text-slate-900">${d}{escapeHtml(item.studentName)}</div>
                        <div class="text-indigo-600 font-bold font-mono bg-indigo-50 px-2 py-0.5 rounded-md text-sm">${d}{item.score.toFixed(2)} đ</div>
                    </div>
                    <div class="flex justify-between items-center text-xs text-slate-500">
                        <div>
                            <span class="bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded mr-1">${d}{typeText}</span>
                        </div>
                        <div class="font-mono">${d}{item.correctCount}/${d}{item.gradedCount} câu</div>
                    </div>
                    <div class="mt-2 text-right text-[10px] text-slate-400">${d}{dateText}</div>
                `;
                body.appendChild(card);
            });
        }

        // --- Config Submit ---

        document.getElementById('settings-form').addEventListener('submit', async (e) => {
            e.preventDefault();
            
            const testType = document.getElementById('setting-test-type').value;
            const totalPages = parseInt(document.getElementById('setting-total-pages').value) || 1;
            const totalQuestions = parseInt(document.getElementById('setting-total-questions').value) || 20;
            const pointsPerCorrect = parseFloat(document.getElementById('setting-points').value) || 0.5;

            // Gather selected questions
            const selectedQuestions = [];
            const selectBtns = document.getElementById('selected-questions-container').children;
            let allSelected = true;
            for (let btn of selectBtns) {
                if (btn.dataset.selected === "true") {
                    selectedQuestions.push(parseInt(btn.dataset.num));
                } else {
                    allSelected = false;
                }
            }

            // Parse master keys textarea
            const keysText = document.getElementById('setting-master-keys').value;
            const masterKeys = {};
            const lines = keysText.split('\n');
            lines.forEach(line => {
                const parts = line.split(',');
                if (parts.size < 2) return;
                const qNum = parseInt(parts[0]);
                const ans = parts[1]?.trim()?.toUpperCase() || '';
                if (!isNaN(qNum) && ans.length > 0) {
                    masterKeys[qNum] = ans;
                }
            });

            // If all checkboxes are checked, send empty list to mean "grade all"
            const finalSelection = allSelected ? [] : selectedQuestions;

            const payload = {
                testType,
                totalPages,
                totalQuestions,
                pointsPerCorrect,
                selectedQuestions: finalSelection,
                masterKeys
            };

            try {
                const response = await fetch('/api/settings', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const res = await response.json();
                if (res.status === 'success') {
                    showToast('Cập nhật cấu hình lên điện thoại thành công!', '💾');
                    fetchConfig();
                } else {
                    showToast('Có lỗi xảy ra: ' + res.message, '❌');
                }
            } catch (err) {
                showToast('Không thể kết nối đến điện thoại', '❌');
            }
        });

        // --- CSV Import ---

        function triggerCsvUpload() {
            document.getElementById('csv-file-input').click();
        }

        function handleCsvFile(input) {
            const file = input.files[0];
            if (!file) return;

            const reader = new FileReader();
            reader.onload = (e) => {
                const content = e.target.result;
                document.getElementById('setting-master-keys').value = content;
                showToast('Đã trích xuất nội dung đáp án từ file', '📥');
                
                // Try to parse the file line count to auto-update totalQuestions
                const lines = content.split('\n').filter(l => l.trim().length > 0);
                if (lines.length > 0) {
                    document.getElementById('setting-total-questions').value = lines.length;
                    // Trigger config UI recreate to match new total questions count
                    activeConfig.totalQuestions = lines.length;
                    populateConfigUI();
                }
            };
            reader.readAsText(file);
        }

        // --- Remote Image Scoring ---

        function setupRemoteUpload() {
            const area = document.getElementById('remote-upload-area');
            area.addEventListener('click', () => {
                document.getElementById('remote-image-input').click();
            });
            area.addEventListener('dragover', (e) => {
                e.preventDefault();
                area.classList.add('bg-indigo-50/50', 'border-indigo-500');
            });
            area.addEventListener('dragleave', () => {
                area.classList.remove('bg-indigo-50/50', 'border-indigo-500');
            });
            area.addEventListener('drop', (e) => {
                e.preventDefault();
                area.classList.remove('bg-indigo-50/50', 'border-indigo-500');
                if (e.dataTransfer.files.length > 0) {
                    document.getElementById('remote-image-input').files = e.dataTransfer.files;
                    handleRemoteImageUpload(document.getElementById('remote-image-input'));
                }
            });
        }

        function handleRemoteImageUpload(input) {
            const file = input.files[0];
            if (!file) return;

            const reader = new FileReader();
            reader.onload = async (e) => {
                const base64Data = e.target.result.split(',')[1];
                
                // Hide upload area, show progress loader
                document.getElementById('remote-upload-area').classList.add('hidden');
                document.getElementById('remote-grading-loader').classList.remove('hidden');

                try {
                    const response = await fetch('/api/grade', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ image: base64Data })
                    });
                    const res = await response.json();
                    
                    if (response.ok && res.studentName) {
                        showToast(`Chấm thành công bài thi: ${d}{res.studentName}! Điểm: ${d}{res.score.toFixed(2)}`, '🎉');
                        fetchResults();
                        openResultDetail(res.id || gradingHistory[0]?.id || 1, res);
                    } else {
                        showToast(res.message || 'Lỗi chấm bài từ xa', '❌');
                    }
                } catch (err) {
                    showToast('Truyền dữ liệu chấm bài thất bại', '❌');
                } finally {
                    document.getElementById('remote-upload-area').classList.remove('hidden');
                    document.getElementById('remote-grading-loader').classList.add('hidden');
                }
            };
            reader.readAsDataURL(file);
        }

        // --- Detail Modal ---

        function openResultDetail(id, cachedData = null) {
            const item = cachedData || gradingHistory.find(r => r.id === id);
            if (!item) return;

            document.getElementById('modal-student-name').innerText = item.studentName;
            
            const typeText = item.testType === "ANSWER_TABLE" ? "Bảng Đáp Án" : "Khoanh Trên Đề";
            document.getElementById('modal-test-meta').innerText = `Loại bài: ${d}{typeText} | Tổng số câu: ${d}{item.totalQuestions}`;
            document.getElementById('modal-score').innerText = `${d}{item.score.toFixed(2)} / ${d}{item.maxScore.toFixed(2)}`;
            document.getElementById('modal-correct-ratio').innerText = `${d}{item.correctCount} / ${d}{item.gradedCount}`;
            document.getElementById('modal-date').innerText = formatDate(item.timestamp);

            const imgContainer = document.getElementById('modal-image-container');
            const imgElem = document.getElementById('modal-student-image');
                        if (item.imagePath && item.imagePath.length > 5) {
                // If it's base64, use directly. Else, fetch from /api/image
                if (item.imagePath.startsWith('data:')) {
                    imgElem.src = item.imagePath;
                } else {
                    imgElem.src = '/api/image?path=' + encodeURIComponent(item.imagePath);
                }
                imgContainer.style.display = 'flex';
            } else {
                imgElem.src = '';
                imgContainer.style.display = 'none';
            }

            const grid = document.getElementById('modal-answers-grid');
            grid.innerHTML = '';

            item.answers.forEach(ans => {
                const div = document.createElement('div');
                div.className = "p-3 rounded-xl border flex flex-col text-center justify-between shadow-[0_1px_2px_rgba(0,0,0,0.02)] " + 
                    (!ans.isGraded ? "bg-slate-50 border-slate-100 opacity-60" : 
                     ans.isCorrect ? "bg-emerald-50/50 border-emerald-200 text-emerald-800" : "bg-rose-50/50 border-rose-200 text-rose-800");
                
                let answerText = ans.studentAnswer;
                if (!ans.isGraded) answerText = "Bỏ";
                else if (!answerText || answerText === "Trống" || answerText === "EMPTY" || answerText === "NONE" || answerText === "-") answerText = "-";
                else if (answerText === "X" || answerText === "Nhiều đáp án" || (typeof answerText === 'string' && answerText.includes("NHIỀU"))) answerText = "X";

                div.innerHTML = `
                    <span class="text-xs font-bold text-slate-500 mb-1">Câu ${d}{ans.questionNumber}</span>
                    <div class="flex items-center justify-center space-x-1.5 my-1 font-mono text-base font-bold">
                        <span>${d}{answerText}</span>
                        ${d}{ans.isGraded && !ans.isCorrect ? `<span class="text-xs font-medium text-slate-400">(${d}{ans.correctAnswer})</span>` : ''}
                    </div>
                    <span class="text-[10px] font-semibold text-slate-400">
                        ${d}{!ans.isGraded ? 'Không chấm' : ans.isCorrect ? 'Đúng' : 'Sai'}
                    </span>
                `;
                grid.appendChild(div);
            });

            document.getElementById('detail-modal').classList.remove('hidden');
        }

        function closeModal() {
            document.getElementById('detail-modal').classList.add('hidden');
        }

        // --- Clear All Results ---

        async function clearAllResults() {
            if (!confirm('Bạn có chắc chắn muốn xóa TOÀN BỘ lịch sử chấm thi không? Hành động này không thể hoàn tác.')) return;
            try {
                const response = await fetch('/api/clear', { method: 'POST' });
                if (response.ok) {
                    showToast('Đã xóa sạch lịch sử chấm thi', '🗑️');
                    fetchResults();
                }
            } catch (err) {
                showToast('Lỗi xóa lịch sử', '❌');
            }
        }

        // --- Search Filtering ---

        function filterResults() {
            const query = document.getElementById('search-input').value.toLowerCase().trim();
            const filtered = gradingHistory.filter(item => item.studentName.toLowerCase().includes(query));
            populateResultsUI(filtered);
        }

        // --- Formatting Helpers ---

        function formatDate(timestamp) {
            const date = new Date(timestamp);
            return date.toLocaleDateString('vi-VN', { 
                day: '2-digit', 
                month: '2-digit', 
                year: 'numeric' 
            }) + " " + date.toLocaleTimeString('vi-VN', { 
                hour: '2-digit', 
                minute: '2-digit' 
            });
        }

        function escapeHtml(str) {
            return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}

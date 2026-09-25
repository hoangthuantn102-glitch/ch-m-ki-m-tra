import re

with open('./app/src/main/java/com/example/network/GeminiService.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add FrameAnalysisResult data class
if "data class FrameAnalysisResult" not in content:
    models = """data class FrameAnalysisResult(
    val isExam: Boolean,
    val isFullyInFrame: Boolean,
    val isClear: Boolean,
    val message: String
)

data class GeminiGradingOutput"""
    content = content.replace("data class GeminiGradingOutput", models)

# Add analyzeExamFrame function
if "suspend fun analyzeExamFrame" not in content:
    analyze_func = """
    suspend fun analyzeExamFrame(bitmap: Bitmap): Result<FrameAnalysisResult> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return Result.failure(Exception("Vui lòng cấu hình GEMINI_API_KEY"))
        }

        try {
            val maxDim = 512
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val scale = maxDim.toFloat() / Math.max(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }
            val outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val bytes = outputStream.toByteArray()
            val base64Image = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

            val prompt = \"\"\"
                Bạn là hệ thống kiểm tra chất lượng ảnh chụp bài kiểm tra.
                Hãy phân tích ảnh này và trả về kết quả dưới định dạng JSON với các trường sau:
                {
                  "isExam": true/false, // Ảnh có phải là bài kiểm tra/phiếu trả lời trắc nghiệm không?
                  "isFullyInFrame": true/false, // Toàn bộ phần nội dung bài làm có nằm gọn trong khung hình không? (bị cắt viền là false)
                  "isClear": true/false, // Ảnh có đủ rõ nét để đọc chữ không? (bị mờ nhoè là false)
                  "message": "Thông báo ngắn gọn (tiếng Việt)" // Ví dụ: "Vui lòng giữ yên điện thoại", "Đưa toàn bộ bài thi vào khung hình", "Chưa phát hiện bài thi", "Ảnh quá mờ", hoặc "Tuyệt vời, đang chụp..." nếu tất cả đều true.
                }
                Chỉ trả về JSON hợp lệ, không có markdown block.
            \"\"\".trimIndent()

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = prompt),
                            GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image))
                        )
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.1
                )
            )

            val rawResponse = service.generateContent(apiKey, request)
            val rawText = rawResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return Result.failure(Exception("Không có kết quả"))

            var cleanedJson = rawText.trim()
            if (cleanedJson.startsWith("```json")) {
                cleanedJson = cleanedJson.removePrefix("```json").removeSuffix("```").trim()
            } else if (cleanedJson.startsWith("```")) {
                cleanedJson = cleanedJson.removePrefix("```").removeSuffix("```").trim()
            }

            val parsedOutput = moshi.adapter(FrameAnalysisResult::class.java).fromJson(cleanedJson)
                ?: return Result.failure(Exception("Lỗi phân tích JSON"))

            return Result.success(parsedOutput)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Core grading function
"""
    content = content.replace("    /**\n     * Core grading function", analyze_func)

with open('./app/src/main/java/com/example/network/GeminiService.kt', 'w', encoding='utf-8') as f:
    f.write(content)

package com.example.network

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.ExamConfig
import com.example.data.GradingResult
import com.example.data.StudentAnswerDetail
import com.example.data.TestType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// --- Gemini REST API Request Models ---

data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Double? = null
)

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

// --- Gemini REST API Response Models ---

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?
)

// --- Retrofit Service Interface ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    // JSON response model returned by Gemini
    data class FrameAnalysisResult(
    val isExam: Boolean,
    val isFullyInFrame: Boolean,
    val isClear: Boolean,
    val message: String
)

data class GeminiGradingOutput(
        val studentName: String? = null,
        val score: Double? = null,
        val maxScore: Double? = null,
        val answers: List<GeminiAnswerOutput> = emptyList()
    )

    data class GeminiAnswerOutput(
        val questionNumber: Int,
        val studentAnswer: String
    )

    /**
     * Helper to compress bitmap and convert to Base64 String for Gemini API
     */
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        // Compress with high quality (95) to preserve fine pen/pencil circle details
        compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Helper to create a compact base64 data URL for persisting student paper image(s)
     */
    
    private fun List<Bitmap>.saveToLocalFile(context: android.content.Context): String {
        if (isEmpty()) return ""
        val combinedBitmap = if (size == 1) {
            this[0]
        } else {
            val targetWidth = this.maxOf { it.width }
            val totalHeight = this.sumOf { (it.height.toFloat() * (targetWidth.toFloat() / it.width)).toInt() }
            val result = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            var currentY = 0f
            for (bmp in this) {
                val scale = targetWidth.toFloat() / bmp.width
                val scaledH = bmp.height * scale
                val matrix = Matrix()
                matrix.postScale(scale, scale)
                matrix.postTranslate(0f, currentY)
                canvas.drawBitmap(bmp, matrix, null)
                currentY += scaledH
            }
            result
        }
        val file = java.io.File(context.cacheDir, "exam_" + System.currentTimeMillis() + ".jpg")
        try {
            val out = java.io.FileOutputStream(file)
            combinedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.flush()
            out.close()
            return file.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("GeminiClient", "Failed to save image", e)
            return ""
        }
    }



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

            val prompt = """
                Bạn là hệ thống kiểm tra chất lượng ảnh chụp bài kiểm tra.
                Hãy phân tích ảnh này và trả về kết quả dưới định dạng JSON với các trường sau:
                {
                  "isExam": true/false, // Ảnh có phải là bài kiểm tra/phiếu trả lời trắc nghiệm không?
                  "isFullyInFrame": true/false, // Toàn bộ phần nội dung bài làm có nằm gọn trong khung hình không? (bị cắt viền là false)
                  "isClear": true/false, // Ảnh có đủ rõ nét để đọc chữ không? (bị mờ nhoè là false)
                  "message": "Thông báo ngắn gọn (tiếng Việt)" // Ví dụ: "Vui lòng giữ yên điện thoại", "Đưa toàn bộ bài thi vào khung hình", "Chưa phát hiện bài thi", "Ảnh quá mờ", hoặc "Tuyệt vời, đang chụp..." nếu tất cả đều true.
                }
                Chỉ trả về JSON hợp lệ, không có markdown block.
            """.trimIndent()

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

            var rawResponse: GeminiResponse? = null
            var retries = 0
            var delayMs = 1000L
            while (retries < 3) {
                try {
                    rawResponse = service.generateContent(apiKey, request)
                    break // success
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 429) {
                        retries++
                        if (retries >= 3) throw Exception("Hệ thống AI đang quá tải (Lỗi 429). Vui lòng thử lại sau.")
                        kotlinx.coroutines.delay(delayMs)
                        delayMs *= 2
                    } else {
                        throw e
                    }
                }
            }
            val rawText = rawResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
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
 that calls Gemini for single or multi-page exams
     */
    suspend fun gradeExam(
        context: android.content.Context,
        bitmaps: List<Bitmap>,
        config: ExamConfig
    ): Result<GradingResult> {
        if (bitmaps.isEmpty()) {
            return Result.failure(Exception("Không có hình ảnh bài thi nào được cung cấp."))
        }
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return Result.failure(Exception("Vui lòng cấu hình GEMINI_API_KEY trong bảng điều khiển Secrets của AI Studio."))
        }

        try {
            val testTypeInstruction = when (config.testType) {
                TestType.CIRCLED_ON_SHEET -> """
                    Học sinh KHOANH TRÒN hoặc ĐÁNH DẤU đáp án trực tiếp trên tờ đề thi (các chữ cái A, B, C, D).
                    Hãy quan sát thật kỹ từng câu hỏi từ 1 đến ${config.totalQuestions}:
                    - CHỈ nhận diện là A, B, C hoặc D nếu chữ cái đó được khoanh tròn hoặc đánh dấu gạch chéo RÕ RÀNG VÀ DUY NHẤT.
                    - NẾU KHÔNG CÓ chữ cái nào được khoanh/đánh dấu ở câu đó (như câu 4 học sinh không làm): BẮT BUỘC trả về studentAnswer = "-".
                    - NẾU CÓ TỪ 2 CHỮ CÁI TRỞ LÊN cùng được khoanh tròn/đánh dấu: BẮT BUỘC trả về studentAnswer = "X".
                """.trimIndent()
                TestType.ANSWER_TABLE -> """
                    Học sinh ĐIỀN ĐÁP ÁN vào một bảng ô đáp án (viết chữ A, B, C, D vào từng ô tương ứng với số câu).
                    Hãy quan sát từng ô đáp án từ câu 1 đến câu ${config.totalQuestions}:
                    - NẾU Ô HOÀN TOÀN TRỐNG (học sinh không ghi chữ nào): BẮT BUỘC trả về studentAnswer = "-".
                    - NẾU Ô CÓ TỪ 2 CHỮ CÁI TRỞ LÊN (ví dụ ghi cả A và B): BẮT BUỘC trả về studentAnswer = "X".
                    - NẾU Ô GHI RÕ RÀNG 1 CHỮ CÁI: Trả về chữ cái đó (ví dụ "A", "B", "C", "D").
                """.trimIndent()
            }

            val questionSelectionText = if (config.selectedQuestions.isEmpty()) {
                "Nhận diện tất cả các câu từ 1 đến ${config.totalQuestions}."
            } else {
                "Nhận diện các câu sau: ${config.selectedQuestions.sorted().joinToString(", ")}."
            }

            val systemPrompt = """
                Bạn là một hệ thống máy quét OCR nhận diện nét bút khoanh bài thi trắc nghiệm.
                CẢNH BÁO TỐI CAO:
                1. BẠN LÀ MÁY SCAN THỦ CÔNG, TUYỆT ĐỐI KHÔNG DÙNG KIẾN THỨC MÔN HỌC (VẬT LÝ, HÓA HỌC, TOÁN) ĐỂ TỰ ĐOÁN ĐÁP ÁN ĐÚNG CỦA CÂU HỎI.
                2. NẾU Ở MỘT CÂU HỎI KHÔNG CÓ CHỮ CÁI NÀO ĐƯỢC KHOANH TRÒN HOẶC ĐÁNH DẤU -> BẮT BUỘC TRẢ VỀ "-". KHÔNG ĐƯỢC ĐIỀN ĐÁP ÁN LÝ THUYẾT.
                3. NẾU HỌC SINH KHOANH CHỮ CÁI NÀO (DÙ ĐÚNG HAY SAI LÝ THUYẾT MÔN HỌC), BẠN PHẢI TRẢ VỀ ĐÚNG CHỮ CÁI ĐÓ MẮT THẤY KHOANH. Ví dụ: Nếu học sinh khoanh A ở câu 6 -> BẮT BUỘC TRẢ VỀ studentAnswer = "A".
                4. NẾU KHOANH/ĐIỀN TỪ 2 ĐÁP ÁN TRỞ LÊN -> TRẢ VỀ "X".
                5. TÊN HỌC SINH (studentName): Đọc ĐÚNG CHÍNH XÁC từng chữ viết tay hoặc đánh máy ở mục "Họ và tên" / "Tên học sinh" trên bài thi. TUYỆT ĐỐI KHÔNG TỰ BỊA TÊN. NẾU CÓ TÊN THÌ BẮT BUỘC PHẢI GHI RÕ TÊN HỌC SINH ĐÓ (ví dụ: 'Nguyễn Văn A'). CHỈ GHI 'Học sinh ẩn danh' KHI THỰC SỰ KHÔNG CÓ CHỮ TÊN NÀO TRÊN BÀI., KHÔNG TỰ BỔ SUNG HOẶC ĐỔI TÊN. Nếu không thấy mục ghi tên hoặc mục này bỏ trống không có chữ -> BẮT BUỘC trả về "Học sinh ẩn danh".
            """.trimIndent()

            val pageNote = if (bitmaps.size > 1) "Bài thi này bao gồm ${bitmaps.size} trang ảnh." else "Bài thi bao gồm 1 trang ảnh."

            val prompt = """
                Hãy quan sát thật cẩn thận $pageNote đính kèm và trích xuất BẢN CHẤT MẮT THẤY CÁC VẾT KHOANH BÚT CỦA HỌC SINH.

                CẤU HÌNH BÀI THI:
                - Tổng số câu hỏi: ${config.totalQuestions}
                - Hình thức làm bài: $testTypeInstruction
                - Phạm vi câu hỏi: $questionSelectionText

                HƯỚNG DẪN KIỂM TRA TỪNG CÂU TỪ 1 ĐẾN ${config.totalQuestions}:
                - Đọc tên/lớp ĐÚNG CHÍNH XÁC như viết trên bài thi (nếu không có chữ tên -> studentName = "Học sinh ẩn danh").
                - Soi từng vị trí A, B, C, D của từng câu:
                  + Nếu chữ D có nét khoanh tròn -> studentAnswer = "D".
                  + Nếu câu đó hoàn toàn không có nét khoanh ở A, B, C, D -> studentAnswer = "-".
                  + Nếu khoanh 2 đáp án trở lên -> studentAnswer = "X".

                Trả về JSON duy nhất không chứa markdown:
                {
                  "studentName": "Nguyễn Văn A",
                  "answers": [
                    {"questionNumber": 1, "studentAnswer": "A"},
                    {"questionNumber": 2, "studentAnswer": "C"},
                    {"questionNumber": 3, "studentAnswer": "D"},
                    {"questionNumber": 4, "studentAnswer": "-"}
                  ]
                }
            """.trimIndent()

            Log.d("GeminiService", "Sending OCR request to Gemini with ${bitmaps.size} page(s)...")

            val contentParts = mutableListOf<GeminiPart>()
            contentParts.add(GeminiPart(text = prompt))
            for (bmp in bitmaps) {
                contentParts.add(GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = bmp.toBase64())))
            }

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(parts = contentParts)
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = systemPrompt))
                ),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.0
                )
            )

            var rawResponse: GeminiResponse? = null
            var retries = 0
            var delayMs = 1000L
            while (retries < 3) {
                try {
                    rawResponse = service.generateContent(apiKey, request)
                    break // success
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 429) {
                        retries++
                        if (retries >= 3) throw Exception("Hệ thống AI đang quá tải (Lỗi 429). Vui lòng thử lại sau.")
                        kotlinx.coroutines.delay(delayMs)
                        delayMs *= 2
                    } else {
                        throw e
                    }
                }
            }
            val rawText = rawResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return Result.failure(Exception("Gemini không trả về kết quả đọc ảnh."))

            var cleanedJson = rawText.trim()
            if (cleanedJson.startsWith("```json")) {
                cleanedJson = cleanedJson.removePrefix("```json").removeSuffix("```").trim()
            } else if (cleanedJson.startsWith("```")) {
                cleanedJson = cleanedJson.removePrefix("```").removeSuffix("```").trim()
            }

            Log.d("GeminiService", "Received JSON: $cleanedJson")

            // Parse the JSON output using Moshi
            val parsedOutput = moshi.adapter(GeminiGradingOutput::class.java).fromJson(cleanedJson)
                ?: return Result.failure(Exception("Không thể phân tích định dạng kết quả từ Gemini."))

            // Map parsed output back to domain GradingResult
            val answersMap = parsedOutput.answers.associate { it.questionNumber to it.studentAnswer.trim().uppercase() }
            
            var totalCorrect = 0
            var gradedCount = 0
            
            val finalAnswers = (1..config.totalQuestions).map { qNum ->
                val correctAns = config.masterKeys[qNum]?.trim()?.uppercase() ?: ""
                val rawStudentAns = answersMap[qNum]?.trim()?.uppercase() ?: ""
                val isGraded = config.isQuestionGraded(qNum)
                
                val cleanStudentAns = when {
                    rawStudentAns.isEmpty() || rawStudentAns == "TRỐNG" || rawStudentAns == "EMPTY" || rawStudentAns == "NONE" || rawStudentAns == "-" -> "-"
                    rawStudentAns == "X" || rawStudentAns.contains("NHIỀU") || rawStudentAns.contains("MULTIPLE") || rawStudentAns.contains(",") || rawStudentAns.contains(";") -> "X"
                    else -> {
                        val letterMatch = Regex("[A-D]").find(rawStudentAns)?.value
                        if (rawStudentAns.length == 1 && rawStudentAns[0] in 'A'..'D') {
                            rawStudentAns
                        } else if (letterMatch != null && rawStudentAns.length <= 2) {
                            letterMatch
                        } else if (rawStudentAns.length > 1) {
                            "X"
                        } else {
                            "-"
                        }
                    }
                }

                // Strictly evaluate correctness:
                // Must be selected for grading AND student answer must be a single option in A, B, C, D AND match master key
                val isCorrect = isGraded &&
                        correctAns.isNotEmpty() &&
                        cleanStudentAns in listOf("A", "B", "C", "D") &&
                        cleanStudentAns == correctAns

                if (isGraded) {
                    gradedCount++
                    if (isCorrect) totalCorrect++
                }

                StudentAnswerDetail(
                    questionNumber = qNum,
                    studentAnswer = cleanStudentAns,
                    correctAnswer = correctAns,
                    isCorrect = isCorrect,
                    points = if (isCorrect) config.pointsPerCorrect else 0.0,
                    isGraded = isGraded
                )
            }

            val finalScore = totalCorrect * config.pointsPerCorrect
            val maxPossibleScore = gradedCount * config.pointsPerCorrect

            val gradingResult = GradingResult(
                studentName = parsedOutput.studentName?.trim()?.ifEmpty { "Học sinh ẩn danh" } ?: "Học sinh ẩn danh",
                score = finalScore,
                maxScore = maxPossibleScore,
                totalQuestions = config.totalQuestions,
                gradedCount = gradedCount,
                correctCount = totalCorrect,
                testType = config.testType,
                answers = finalAnswers,
                imagePath = bitmaps.saveToLocalFile(context)
            )

            return Result.success(gradingResult)

        } catch (e: Exception) {
            Log.e("GeminiService", "Grading failed", e)
            return Result.failure(e)
        }
    }
}

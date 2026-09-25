import re

with open('./app/src/main/java/com/example/network/GeminiService.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace `toStorageDataUrl` with `saveToLocalFile`
save_to_local = """
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
"""

content = re.sub(
    r"private fun List<Bitmap>\.toStorageDataUrl\(\): String \{.*?(?=suspend fun gradeExam)",
    save_to_local,
    content,
    flags=re.DOTALL
)

content = content.replace("bitmaps.toStorageDataUrl()", "bitmaps.saveToLocalFile(context)")
content = content.replace("suspend fun gradeExam(\n        bitmaps: List<Bitmap>,\n        config: ExamConfig\n    )", "suspend fun gradeExam(\n        context: android.content.Context,\n        bitmaps: List<Bitmap>,\n        config: ExamConfig\n    )")
content = content.replace("suspend fun gradeExam(\n        bitmap: Bitmap,\n        config: ExamConfig\n    ): Result<GradingResult> = gradeExam(listOf(bitmap), config)", "")

# Add stronger constraint on StudentName in the prompt
content = content.replace("TUYỆT ĐỐI KHÔNG TỰ BỊA TÊN", "TUYỆT ĐỐI KHÔNG TỰ BỊA TÊN. NẾU CÓ TÊN THÌ BẮT BUỘC PHẢI GHI RÕ TÊN HỌC SINH ĐÓ (ví dụ: 'Nguyễn Văn A'). CHỈ GHI 'Học sinh ẩn danh' KHI THỰC SỰ KHÔNG CÓ CHỮ TÊN NÀO TRÊN BÀI.")


with open('./app/src/main/java/com/example/network/GeminiService.kt', 'w', encoding='utf-8') as f:
    f.write(content)

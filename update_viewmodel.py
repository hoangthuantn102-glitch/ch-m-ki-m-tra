import re
with open('./app/src/main/java/com/example/ui/GradingViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    "val result = GeminiClient.gradeExam(bitmaps, activeConfig)",
    "val result = GeminiClient.gradeExam(getApplication(), bitmaps, activeConfig)"
)

content = content.replace(
    "val result = GeminiClient.gradeExam(bitmap, activeConfig)",
    "val result = GeminiClient.gradeExam(getApplication(), listOf(bitmap), activeConfig)"
)

with open('./app/src/main/java/com/example/ui/GradingViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)

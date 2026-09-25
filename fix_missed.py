with open('./app/src/main/java/com/example/ui/MainLayout.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'processingBitmap?.let { viewModel.gradePhoto(it) }',
    'if (capturedBitmaps.isNotEmpty()) viewModel.gradePhotos(capturedBitmaps.toList())'
)

with open('./app/src/main/java/com/example/ui/MainLayout.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("Done")

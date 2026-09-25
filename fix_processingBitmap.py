import re

with open('./app/src/main/java/com/example/ui/MainLayout.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Replace declaration
content = content.replace(
    'var processingBitmap by remember { mutableStateOf<Bitmap?>(null) }',
    'val capturedBitmaps = remember { androidx.compose.runtime.mutableStateListOf<Bitmap>() }'
)

# 2. Replace null assignments
content = content.replace('processingBitmap = null', 'capturedBitmaps.clear()')

# 3. Replace single image capture in gallery
content = content.replace(
'''            if (bitmap != null) {
                processingBitmap = bitmap
                showLiveCamera = false
                viewModel.gradePhoto(bitmap)
            }''',
'''            if (bitmap != null) {
                capturedBitmaps.add(bitmap)
                if (capturedBitmaps.size >= config.totalPages) {
                    showLiveCamera = false
                    viewModel.gradePhotos(capturedBitmaps.toList())
                }
            }'''
)

# 4. Replace single image capture in small mode (line ~645)
# And replace `isIdle = (gradingUiState is GradingUiState.Idle && processingBitmap == null)`
content = content.replace(
    'isIdle = (gradingUiState is GradingUiState.Idle && processingBitmap == null)',
    'isIdle = (gradingUiState is GradingUiState.Idle && capturedBitmaps.isEmpty())'
)

content = content.replace(
'''                        onImageCaptured = { bitmap ->
                            processingBitmap = bitmap
                            showLiveCamera = false
                            viewModel.clearCameraFrame()
                            viewModel.gradePhoto(bitmap)
                        },''',
'''                        onImageCaptured = { bitmap ->
                            capturedBitmaps.add(bitmap)
                            if (capturedBitmaps.size >= config.totalPages) {
                                showLiveCamera = false
                                viewModel.clearCameraFrame()
                                viewModel.gradePhotos(capturedBitmaps.toList())
                            }
                        },'''
)

# 5. Replace single image capture in full screen mode (line ~1000)
content = content.replace(
'''                    onImageCaptured = { bitmap ->
                        processingBitmap = bitmap
                        showLiveCamera = false
                        viewModel.clearCameraFrame()
                        viewModel.gradePhoto(bitmap)
                    },''',
'''                    onImageCaptured = { bitmap ->
                        capturedBitmaps.add(bitmap)
                        if (capturedBitmaps.size >= config.totalPages) {
                            showLiveCamera = false
                            viewModel.clearCameraFrame()
                            viewModel.gradePhotos(capturedBitmaps.toList())
                        }
                    },'''
)

# 6. View conditions
content = content.replace(
    'if (processingBitmap != null) {',
    'if (!showLiveCamera && capturedBitmaps.isNotEmpty()) {'
)
content = content.replace(
    'model = processingBitmap,',
    'model = capturedBitmaps.lastOrNull(),'
)
content = content.replace(
    'if (!showLiveCamera && processingBitmap == null) {',
    'if (!showLiveCamera && capturedBitmaps.isEmpty()) {'
)
content = content.replace(
    '} else if (processingBitmap != null) {',
    '} else if (!showLiveCamera && capturedBitmaps.isNotEmpty()) {'
)

# 7. Error retry button
content = content.replace(
    'onClick = { processingBitmap?.let { viewModel.gradePhoto(it) } },',
    'onClick = { if (capturedBitmaps.isNotEmpty()) viewModel.gradePhotos(capturedBitmaps.toList()) },'
)

with open('./app/src/main/java/com/example/ui/MainLayout.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Done refactoring MainLayout.kt")

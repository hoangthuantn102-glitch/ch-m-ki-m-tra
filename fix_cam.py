import re

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
'''    showBottomCaptureButton: Boolean = false,
    onCameraFrame: ((Bitmap) -> Unit)? = null,''',
'''    showBottomCaptureButton: Boolean = false,
    capturedCount: Int = 0,
    totalPages: Int = 1,
    onCameraFrame: ((Bitmap) -> Unit)? = null,'''
)

content = content.replace(
    'Text("Chụp Để Chấm Bài", fontWeight = FontWeight.Bold, fontSize = 12.sp)',
    'Text(if (totalPages > 1) "Chụp Trang ${capturedCount + 1}/$totalPages" else "Chụp Để Chấm Bài", fontWeight = FontWeight.Bold, fontSize = 12.sp)'
)

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.write(content)

with open('./app/src/main/java/com/example/ui/MainLayout.kt', 'r', encoding='utf-8') as f:
    main_content = f.read()

main_content = main_content.replace(
'''                        isAutoScanning = isAutoScanning,
                        isIdle = (gradingUiState is GradingUiState.Idle && capturedBitmaps.isEmpty()),
                        captureTrigger = localCaptureTrigger,
                        onCameraFrame = { viewModel.broadcastCameraFrame(it) },''',
'''                        isAutoScanning = isAutoScanning,
                        isIdle = (gradingUiState is GradingUiState.Idle && capturedBitmaps.isEmpty()),
                        captureTrigger = localCaptureTrigger,
                        capturedCount = capturedBitmaps.size,
                        totalPages = config.totalPages,
                        onCameraFrame = { viewModel.broadcastCameraFrame(it) },'''
)

main_content = main_content.replace(
'''                    isAutoScanning = isAutoScanning,
                    isIdle = (gradingUiState is GradingUiState.Idle && capturedBitmaps.isEmpty()),
                    captureTrigger = localCaptureTrigger,
                    onCameraFrame = { viewModel.broadcastCameraFrame(it) },''',
'''                    isAutoScanning = isAutoScanning,
                    isIdle = (gradingUiState is GradingUiState.Idle && capturedBitmaps.isEmpty()),
                    captureTrigger = localCaptureTrigger,
                    capturedCount = capturedBitmaps.size,
                    totalPages = config.totalPages,
                    onCameraFrame = { viewModel.broadcastCameraFrame(it) },'''
)

# And in full screen idle, the bottom button says "Chụp Bài"
main_content = main_content.replace(
    'Text("Chụp Bài", fontSize = 12.sp, fontWeight = FontWeight.Bold)',
    'Text(if (config.totalPages > 1) "Chụp Trang ${capturedBitmaps.size + 1}/${config.totalPages}" else "Chụp Bài", fontSize = 12.sp, fontWeight = FontWeight.Bold)'
)

with open('./app/src/main/java/com/example/ui/MainLayout.kt', 'w', encoding='utf-8') as f:
    f.write(main_content)

print("Done updating CameraPreview.kt and MainLayout.kt")

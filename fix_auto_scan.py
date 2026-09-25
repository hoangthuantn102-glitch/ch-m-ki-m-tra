import re

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'LaunchedEffect(isAutoScanning, isIdle, imageCaptureUseCase) {',
    'LaunchedEffect(isAutoScanning, isIdle, imageCaptureUseCase, capturedCount) {'
)

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.write(content)

with open('./app/src/main/java/com/example/ui/MainLayout.kt', 'r', encoding='utf-8') as f:
    main_content = f.read()

main_content = main_content.replace(
    'isIdle = (gradingUiState is GradingUiState.Idle && capturedBitmaps.isEmpty())',
    'isIdle = (gradingUiState is GradingUiState.Idle && capturedBitmaps.size < config.totalPages)'
)

with open('./app/src/main/java/com/example/ui/MainLayout.kt', 'w', encoding='utf-8') as f:
    f.write(main_content)
print("Done fixing auto scan")

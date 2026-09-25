with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
'''            showBottomCaptureButton = showBottomCaptureButton,
            onCameraFrame = onCameraFrame,''',
'''            showBottomCaptureButton = showBottomCaptureButton,
            capturedCount = capturedCount,
            totalPages = totalPages,
            onCameraFrame = onCameraFrame,'''
)

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("Done fixing params")

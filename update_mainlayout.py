import re

with open('./app/src/main/java/com/example/ui/MainLayout.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# In CameraOrSnapshotSection
content = content.replace("val isAutoScanning by viewModel.isAutoScanning.collectAsStateWithLifecycle()", "val isAutoScanning by viewModel.isAutoScanning.collectAsStateWithLifecycle()\n        val autoCaptureTrigger by viewModel.autoCaptureTrigger.collectAsStateWithLifecycle()\n        val autoCaptureWarning by viewModel.autoCaptureWarning.collectAsStateWithLifecycle()")

# Inside DirectCameraView calls
content = content.replace(
    "captureTrigger = localCaptureTrigger,",
    "captureTrigger = localCaptureTrigger,\n                        autoCaptureTrigger = autoCaptureTrigger,\n                        autoCaptureWarning = autoCaptureWarning,"
)

# Wait, in the full screen mode inside GradingTabScreen:
content = content.replace(
    "val isAutoScanning by viewModel.isAutoScanning.collectAsStateWithLifecycle()",
    "val isAutoScanning by viewModel.isAutoScanning.collectAsStateWithLifecycle()\n    val autoCaptureTrigger by viewModel.autoCaptureTrigger.collectAsStateWithLifecycle()\n    val autoCaptureWarning by viewModel.autoCaptureWarning.collectAsStateWithLifecycle()"
)


with open('./app/src/main/java/com/example/ui/MainLayout.kt', 'w', encoding='utf-8') as f:
    f.write(content)

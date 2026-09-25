import re

with open('./app/src/main/java/com/example/ui/MainLayout.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace("val autoCaptureTrigger by viewModel.autoCaptureTrigger.collectAsStateWithLifecycle()\n        val autoCaptureTrigger by viewModel.autoCaptureTrigger.collectAsStateWithLifecycle()", "val autoCaptureTrigger by viewModel.autoCaptureTrigger.collectAsStateWithLifecycle()")
content = content.replace("val autoCaptureWarning by viewModel.autoCaptureWarning.collectAsStateWithLifecycle()\n        val autoCaptureWarning by viewModel.autoCaptureWarning.collectAsStateWithLifecycle()", "val autoCaptureWarning by viewModel.autoCaptureWarning.collectAsStateWithLifecycle()")

# If it's structured differently:
content = re.sub(r"(val autoCaptureTrigger.*?\n.*?)val autoCaptureTrigger.*?\n", r"\1", content)
content = re.sub(r"(val autoCaptureWarning.*?\n.*?)val autoCaptureWarning.*?\n", r"\1", content)

with open('./app/src/main/java/com/example/ui/MainLayout.kt', 'w', encoding='utf-8') as f:
    f.write(content)

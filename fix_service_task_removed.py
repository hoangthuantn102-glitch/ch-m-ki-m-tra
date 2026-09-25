import re

with open('./app/src/main/java/com/example/CameraForegroundService.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'return START_NOT_STICKY',
    'return START_NOT_STICKY\n    }\n\n    override fun onTaskRemoved(rootIntent: Intent?) {\n        super.onTaskRemoved(rootIntent)\n        stopSelf()\n    }'
)

with open('./app/src/main/java/com/example/CameraForegroundService.kt', 'w', encoding='utf-8') as f:
    f.write(content)

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    content = f.read()

import re

target = "    } else {"
replacement = """    } else {
        DisposableEffect(Unit) {
            val serviceIntent = android.content.Intent(context, com.example.CameraForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            onDispose {
                context.stopService(serviceIntent)
            }
        }"""

content = content.replace(target, replacement)

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.write(content)

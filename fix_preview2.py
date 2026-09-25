import re

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    content = f.read()

bad_replacement = """        DisposableEffect(Unit) {
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

# Fix 1: around bitmap
target1 = """                                        } else {
""" + bad_replacement + """
                                            bitmap
                                        }"""
content = content.replace(target1, """                                        } else {
                                            bitmap
                                        }""")

# Fix 2: around bindToLifecycle
target2 = """                } else {
""" + bad_replacement + """
                    cameraProvider.bindToLifecycle("""
content = content.replace(target2, """                } else {
                    cameraProvider.bindToLifecycle(""")


with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.write(content)

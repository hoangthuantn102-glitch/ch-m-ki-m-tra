with open('./app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

import re

# Remove the block that starts CameraForegroundService
pattern = r"val serviceIntent = Intent\(this, CameraForegroundService::class\.java\)\n\s*if \(Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.O\) \{\n\s*startForegroundService\(serviceIntent\)\n\s*\} else \{\n\s*startService\(serviceIntent\)\n\s*\}"
content = re.sub(pattern, "", content)

# Remove the block that stops the service in onDestroy
pattern2 = r"override fun onDestroy\(\) \{\n\s*super\.onDestroy\(\)\n\s*val serviceIntent = Intent\(this, CameraForegroundService::class\.java\)\n\s*stopService\(serviceIntent\)\n\s*\}"
content = re.sub(pattern2, "", content)

with open('./app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(content)

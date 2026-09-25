import re

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# I will just clean up the duplicated and mangled lines
content = content.replace("import android.graphics.Bitmap\nimport android.media.ExifInterfaceimport android.graphics.Bitmap\nimport android.media.ExifInterfaceFactory", "import android.graphics.Bitmap\nimport android.graphics.BitmapFactory\nimport android.media.ExifInterface")

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.write(content)

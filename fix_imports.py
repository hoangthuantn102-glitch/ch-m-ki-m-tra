import re

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = re.sub(r"import android\.graphics\.Bitmap\nimport android\.media\.ExifInterfaceimport android\.graphics\.Bitmap\nimport android\.media\.ExifInterfaceFactory", "import android.graphics.Bitmap\nimport android.graphics.BitmapFactory\nimport android.media.ExifInterface", content)

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.write(content)

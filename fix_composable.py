import re

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix the duplicate imports
content = content.replace("import android.graphics.Bitmap\nimport android.media.ExifInterfaceimport android.graphics.Bitmap\nimport android.graphics.BitmapFactory\n", "import android.graphics.Bitmap\nimport android.media.ExifInterface\nimport android.graphics.BitmapFactory\n")

# Fix the placement of annotations
target = """@OptIn(ExperimentalAnimationApi::class)
@Composable

fun decodeAndRotateBitmap(path: String): Bitmap? {"""

replacement = """fun decodeAndRotateBitmap(path: String): Bitmap? {"""

content = content.replace(target, replacement)

target2 = """}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DirectCameraView("""

content = content.replace("}\n@OptIn(ExperimentalAnimationApi::class)\n@Composable\nfun DirectCameraView(", target2)

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.write(content)

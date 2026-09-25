import re

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Remove the previously injected code
content = re.sub(
    r"import androidx\.exifinterface\.media\.ExifInterface\n\nfun decodeAndRotateBitmap.*?return bitmap\n}\n\n",
    "",
    content,
    flags=re.DOTALL
)

# Insert android.media.ExifInterface import at the top
content = content.replace("import android.graphics.Bitmap", "import android.graphics.Bitmap\nimport android.media.ExifInterface")

# Insert decodeAndRotateBitmap right after imports
if "fun decodeAndRotateBitmap" not in content:
    func = """
fun decodeAndRotateBitmap(path: String): Bitmap? {
    val bitmap = android.graphics.BitmapFactory.decodeFile(path) ?: return null
    try {
        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        val matrix = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postRotate(180f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
        }
        if (!matrix.isIdentity) {
            return android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    } catch (e: Exception) {
        android.util.Log.e("CameraPreview", "Exif rotation failed", e)
    }
    return bitmap
}
"""
    content = content.replace("fun DirectCameraView(", func + "\n@OptIn(ExperimentalAnimationApi::class)\n@Composable\nfun DirectCameraView(")

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.write(content)


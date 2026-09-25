import re

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add a helper function to rotate bitmap based on Exif
exif_helper = """
import androidx.exifinterface.media.ExifInterface

fun decodeAndRotateBitmap(path: String): android.graphics.Bitmap? {
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

if "fun decodeAndRotateBitmap" not in content:
    content = content.replace("package com.example.ui\n", "package com.example.ui\n" + exif_helper)

# Replace BitmapFactory.decodeFile with decodeAndRotateBitmap
content = content.replace("BitmapFactory.decodeFile(photoFile.absolutePath)", "decodeAndRotateBitmap(photoFile.absolutePath)")

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.write(content)


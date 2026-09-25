with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if line.startswith("import android.media.ExifInterfaceimport"):
        new_lines.append("import android.media.ExifInterface\n")
    elif line.startswith("import android.media.ExifInterfaceFactory"):
        new_lines.append("import android.graphics.BitmapFactory\n")
    else:
        new_lines.append(line)

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)

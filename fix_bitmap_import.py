with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
bitmap_found = False
for line in lines:
    if line.strip() == "import android.graphics.Bitmap":
        if bitmap_found:
            continue
        bitmap_found = True
    new_lines.append(line)

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)

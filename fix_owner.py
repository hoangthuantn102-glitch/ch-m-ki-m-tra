with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'val lifecycleOwner = LocalLifecycleOwner.current',
    'val lifecycleOwner = com.example.AlwaysActiveLifecycleOwner'
)

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.write(content)

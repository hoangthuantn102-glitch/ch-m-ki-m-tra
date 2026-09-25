import re

# 1. Update CameraPreview.kt
with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'if (currentTime - lastBroadcastTime >= 1500L) {',
    'if (currentTime - lastBroadcastTime >= 150L) {'
)

with open('./app/src/main/java/com/example/ui/CameraPreview.kt', 'w', encoding='utf-8') as f:
    f.write(content)

# 2. Update LocalServer.kt JS interval
with open('./app/src/main/java/com/example/network/LocalServer.kt', 'r', encoding='utf-8') as f:
    server_content = f.read()

# Replace the setInterval with recursive setTimeout to avoid queue buildup
server_content = server_content.replace(
    "setInterval(fetchLiveCameraFrame, 1200);",
    "// Fetch handled recursively in fetchLiveCameraFrame"
)

# Also need to append setTimeout to fetchLiveCameraFrame
# Let's find the end of fetchLiveCameraFrame block
fetch_func = """        async function fetchLiveCameraFrame() {
            try {
                const response = await fetch('/api/camera_frame');
                if (response.ok) {
                    const data = await response.json();
                    const feedImg = document.getElementById('pc-live-camera-feed');
                    const placeholder = document.getElementById('pc-live-camera-placeholder');
                    const statusTxt = document.getElementById('pc-camera-status');
                    if (data.frame && data.frame.length > 20) {
                        feedImg.src = data.frame;
                        feedImg.classList.remove('hidden');
                        placeholder.classList.add('hidden');
                    } else {
                        feedImg.classList.add('hidden');
                        placeholder.classList.remove('hidden');
                        if (statusTxt) statusTxt.innerText = "Đang chờ tín hiệu camera từ điện thoại...";
                    }
                }
            } catch (e) {
                // Ignore poll errors
            }
            setTimeout(fetchLiveCameraFrame, 150); // Recursive call for low latency
        }"""

server_content = re.sub(
    r"async function fetchLiveCameraFrame\(\) \{.*?\n        \}",
    fetch_func,
    server_content,
    flags=re.DOTALL
)

# Also let's increase max dimensions or JPEG quality a bit? Or keep it same to maintain speed.
# JPEG 70 is fine.
with open('./app/src/main/java/com/example/network/LocalServer.kt', 'w', encoding='utf-8') as f:
    f.write(server_content)

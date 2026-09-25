import re

with open('./app/src/main/java/com/example/network/LocalServer.kt', 'r', encoding='utf-8') as f:
    content = f.read()

endpoint_code = """                    method == "GET" && path.startsWith("/api/image") -> {
                        val query = path.substringAfter("?path=")
                        val decodedPath = java.net.URLDecoder.decode(query, "UTF-8")
                        val file = File(decodedPath)
                        if (file.exists()) {
                            val mime = "image/jpeg"
                            output.write("HTTP/1.1 200 OK\\r\\n".toByteArray())
                            output.write("Content-Type: $mime\\r\\n".toByteArray())
                            output.write("Content-Length: ${file.length()}\\r\\n".toByteArray())
                            output.write("Connection: close\\r\\n\\r\\n".toByteArray())
                            file.inputStream().use { it.copyTo(output) }
                        } else {
                            output.write("HTTP/1.1 404 Not Found\\r\\n\\r\\n".toByteArray())
                        }
                    }
                    method == "POST" && path == "/api/select_config" -> {"""

content = content.replace('                    method == "POST" && path == "/api/select_config" -> {', endpoint_code)

js_code = """            if (item.imagePath && item.imagePath.length > 5) {
                // If it's base64, use directly. Else, fetch from /api/image
                if (item.imagePath.startsWith('data:')) {
                    imgElem.src = item.imagePath;
                } else {
                    imgElem.src = '/api/image?path=' + encodeURIComponent(item.imagePath);
                }
                imgContainer.style.display = 'flex';"""

content = re.sub(r"if \(item\.imagePath && item\.imagePath\.length > 20\) \{\s*imgElem\.src = item\.imagePath;\s*imgContainer\.style\.display = 'flex';", js_code, content)

with open('./app/src/main/java/com/example/network/LocalServer.kt', 'w', encoding='utf-8') as f:
    f.write(content)

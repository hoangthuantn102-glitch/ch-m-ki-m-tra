import re

with open('./app/src/main/java/com/example/network/GeminiService.kt', 'r', encoding='utf-8') as f:
    content = f.read()

retry_logic = """
            var rawResponse: GeminiResponse? = null
            var retries = 0
            var delayMs = 1000L
            while (retries < 3) {
                try {
                    rawResponse = service.generateContent(apiKey, request)
                    break // success
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 429) {
                        retries++
                        if (retries >= 3) throw Exception("Hệ thống AI đang quá tải (Lỗi 429). Vui lòng thử lại sau.")
                        kotlinx.coroutines.delay(delayMs)
                        delayMs *= 2
                    } else {
                        throw e
                    }
                }
            }
"""

content = content.replace("val rawResponse = service.generateContent(apiKey, request)", retry_logic.strip())

with open('./app/src/main/java/com/example/network/GeminiService.kt', 'w', encoding='utf-8') as f:
    f.write(content)


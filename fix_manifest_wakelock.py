import re

with open('./app/src/main/AndroidManifest.xml', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    '<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />',
    '<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n    <uses-permission android:name="android.permission.WAKE_LOCK" />'
)

with open('./app/src/main/AndroidManifest.xml', 'w', encoding='utf-8') as f:
    f.write(content)

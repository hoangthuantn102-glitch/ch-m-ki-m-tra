import re

with open('./app/src/main/AndroidManifest.xml', 'r', encoding='utf-8') as f:
    content = f.read()

permissions = """    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />"""

content = re.sub(
    r'<uses-permission android:name="android.permission.INTERNET" />\s*<uses-permission android:name="android.permission.CAMERA" />\s*<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />\s*<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />',
    permissions,
    content
)

service_decl = """        <service
            android:name=".CameraForegroundService"
            android:foregroundServiceType="camera"
            android:exported="false" />

        <activity"""

content = content.replace('<activity', service_decl)

with open('./app/src/main/AndroidManifest.xml', 'w', encoding='utf-8') as f:
    f.write(content)

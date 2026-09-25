#!/usr/bin/env python3
import http.server
import socketserver
import webbrowser
import os
import sys

PORT = 8000
DIRECTORY = os.path.dirname(os.path.abspath(__file__))

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

def main():
    os.chdir(DIRECTORY)
    with socketserver.TCPServer(("", PORT), Handler) as httpd:
        url = f"http://localhost:{PORT}/index.html"
        print("=" * 60)
        print(" CHẤM THI TRẮC NGHIỆM AI - BẢN WEB APP ĐỘC LẬP TRÊN PC")
        print("=" * 60)
        print(f" Máy chủ đang chạy tại: {url}")
        print(" Đang tự động mở trình duyệt web...")
        print(" Nhấn Ctrl + C để dừng máy chủ bất cứ lúc nào.")
        print("=" * 60)
        try:
            webbrowser.open(url)
        except Exception as e:
            print(f" Không thể mở tự động trình duyệt: {e}")
            print(f" Vui lòng mở thủ công trình duyệt và truy cập: {url}")
        
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\n Đã dừng máy chủ. Tạm biệt!")
            sys.exit(0)

if __name__ == '__main__':
    main()

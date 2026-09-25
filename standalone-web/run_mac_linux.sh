#!/usr/bin/env bash
echo "========================================================"
echo "  KHỞI ĐỘNG PHẦN MỀM CHẤM THI TRẮC NGHIỆM AI TRÊN PC"
echo "========================================================"

if command -v python3 &>/dev/null; then
    echo "[OK] Khởi động với python3..."
    python3 server.py
elif command -v python &>/dev/null; then
    echo "[OK] Khởi động với python..."
    python server.py
else
    echo "[!] Không tìm thấy Python. Đang mở trực tiếp index.html..."
    if command -v xdg-open &>/dev/null; then
        xdg-open index.html
    elif command -v open &>/dev/null; then
        open index.html
    fi
fi

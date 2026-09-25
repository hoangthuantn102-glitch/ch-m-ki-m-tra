@echo off
chcp 65001 > nul
title Chấm Thi Trắc Nghiệm AI - Web App Độc Lập
echo ========================================================
echo   KHỞI ĐỘNG PHẦN MỀM CHẤM THI TRẮC NGHIỆM AI TRÊN PC
echo ========================================================
echo.

where python >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [OK] Đang khởi chạy máy chủ bằng Python...
    python "%~dp0server.py"
    goto end
)

where py >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [OK] Đang khởi chạy máy chủ bằng Python launcher...
    py "%~dp0server.py"
    goto end
)

echo [*] Đang khởi chạy máy chủ bằng PowerShell...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server.ps1"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [!] Khởi chạy máy chủ gặp lỗi. Đang mở trực tiếp index.html...
    start "" "%~dp0index.html"
    echo.
    echo Nhấn phím bất kỳ để đóng...
    pause >nul
)

:end

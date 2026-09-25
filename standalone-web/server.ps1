$port = 8000
$started = $false
$listener = $null

foreach ($p in @(8000, 8080, 8888, 5000, 3000)) {
    try {
        $listener = New-Object System.Net.HttpListener
        $listener.Prefixes.Add("http://localhost:$p/")
        $listener.Start()
        $port = $p
        $started = $true
        break
    } catch {
        try {
            $listener = New-Object System.Net.HttpListener
            $listener.Prefixes.Add("http://127.0.0.1:$p/")
            $listener.Start()
            $port = $p
            $started = $true
            break
        } catch {
            $listener = $null
        }
    }
}

if (-not $started) {
    Write-Host "Không thể khởi tạo cổng mạng cục bộ. Đang mở index.html trực tiếp..." -ForegroundColor Red
    Start-Process (Join-Path $PSScriptRoot "index.html")
    exit 1
}

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host " CHẤM THI TRẮC NGHIỆM AI - BẢN WEB APP ĐỘC LẬP TRÊN PC" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host " Máy chủ đang chạy tại: http://localhost:$port/index.html" -ForegroundColor Yellow
Write-Host " Đang tự động mở trình duyệt..." -ForegroundColor White
Write-Host " Nhấn Ctrl + C để dừng máy chủ bất cứ lúc nào." -ForegroundColor Gray
Write-Host "========================================================" -ForegroundColor Cyan

Start-Process "http://localhost:$port/index.html"

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        $request = $context.Request
        $response = $context.Response
        $rawPath = $request.Url.LocalPath.TrimStart('/')
        if ([string]::IsNullOrEmpty($rawPath)) { $rawPath = "index.html" }

        $filePath = Join-Path $PSScriptRoot $rawPath
        if (Test-Path $filePath -PathType Leaf) {
            $bytes = [System.IO.File]::ReadAllBytes($filePath)
            if ($rawPath.EndsWith(".html")) { $response.ContentType = "text/html; charset=utf-8" }
            elseif ($rawPath.EndsWith(".js")) { $response.ContentType = "application/javascript; charset=utf-8" }
            elseif ($rawPath.EndsWith(".css")) { $response.ContentType = "text/css; charset=utf-8" }
            elseif ($rawPath.EndsWith(".zip")) { $response.ContentType = "application/zip" }
            elseif ($rawPath.EndsWith(".json")) { $response.ContentType = "application/json" }
            
            $response.ContentLength64 = $bytes.Length
            $response.OutputStream.Write($bytes, 0, $bytes.Length)
        } else {
            $response.StatusCode = 404
        }
        $response.Close()
    }
} finally {
    $listener.Stop()
}

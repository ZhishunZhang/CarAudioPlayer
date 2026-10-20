# ============================================================
#  CarAudioPlayer 一键连接脚本
#  架构：中转服务跑在 3201，平板通过 adb reverse 把 3200 映射到电脑 3201
#  （3200 被一个无法杀掉的旧进程占用，故新服务用 3201，App 代码零改动）
#  用法：右键 "使用 PowerShell 运行"，或在此目录终端执行  .\connect.ps1
# ============================================================
$ErrorActionPreference = 'Stop'

$PORT       = 3201          # 中转服务实际监听端口
$DEVICE_PORT = 3200         # App 访问的端口(BASE_URL=127.0.0.1:3200)
$API_DIR    = 'C:\Users\examp\qq-music-api'
$CHECK_PATH = '/getSongListCategories'

Write-Host "==================== CarAudioPlayer 一键连接 ====================" -ForegroundColor Cyan

# ---------- 1. 确保 ADB 设备在线 ----------
Write-Host ""
Write-Host "[1/4] 检查 ADB 设备..." -ForegroundColor Yellow
$lines = & adb devices
$found = $false
foreach ($line in $lines) {
    if ($line -match 'device$') { $found = $true; break }
}
if (-not $found) {
    Write-Host "[失败] 未检测到已授权设备，请确认 USB 连接并允许调试。" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] 设备已在线" -ForegroundColor Green

# ---------- 2. 确保中转服务在 3201 监听 ----------
Write-Host ""
Write-Host "[2/4] 检查中转服务端口 $PORT ..." -ForegroundColor Yellow
$listening = Get-NetTCPConnection -LocalPort $PORT -State Listen -ErrorAction SilentlyContinue
if (-not $listening) {
    Write-Host "[信息] 端口 $PORT 未监听，正在启动中转服务..." -ForegroundColor Yellow
    Push-Location $API_DIR
    $env:PORT = "$PORT"
    Start-Process -FilePath "npm.cmd" -ArgumentList "run","dev" -WindowStyle Hidden -RedirectStandardOutput "$API_DIR\server.log" -RedirectStandardError "$API_DIR\server.err.log"
    Pop-Location
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 500
        if (Get-NetTCPConnection -LocalPort $PORT -State Listen -ErrorAction SilentlyContinue) { break }
    }
}
if (Get-NetTCPConnection -LocalPort $PORT -State Listen -ErrorAction SilentlyContinue) {
    Write-Host "[OK] 中转服务已在端口 $PORT 监听" -ForegroundColor Green
} else {
    Write-Host "[失败] 中转服务启动异常，请查看 $API_DIR\server.err.log" -ForegroundColor Red
    exit 1
}

# ---------- 3. 建立 reverse 隧道(设备3200 -> 电脑3201) ----------
Write-Host ""
Write-Host "[3/4] 建立 adb reverse 隧道 (设备:$DEVICE_PORT -> 电脑:$PORT)..." -ForegroundColor Yellow
& adb reverse --remove "tcp:$DEVICE_PORT" 2>$null
& adb reverse "tcp:$DEVICE_PORT" "tcp:$PORT" | Out-Null
$rules = & adb reverse --list
if ($rules -match "tcp:$DEVICE_PORT") {
    Write-Host "[OK] 隧道已生效: 设备 $DEVICE_PORT -> 电脑 $PORT" -ForegroundColor Green
} else {
    Write-Host "[失败] 隧道建立失败，请检查 adb 连接。" -ForegroundColor Red
    exit 1
}

# ---------- 4. 双侧验证 ----------
Write-Host ""
Write-Host "[4/4] 验证连通性..." -ForegroundColor Yellow

$localOk = $false
try {
    Invoke-WebRequest -Uri "http://127.0.0.1:$PORT$CHECK_PATH" -UseBasicParsing -TimeoutSec 8 | Out-Null
    $localOk = $true
} catch {
    $localOk = $false
}
if ($localOk) {
    Write-Host "[OK] 电脑本机访问正常" -ForegroundColor Green
} else {
    Write-Host "[失败] 电脑本机访问异常" -ForegroundColor Red
}

$tab = & adb shell "curl -s -m 8 http://127.0.0.1:$DEVICE_PORT$CHECK_PATH"
if ($tab -match '"code"') {
    Write-Host "[OK] 平板经隧道访问正常，返回数据成功" -ForegroundColor Green
} else {
    Write-Host "[失败] 平板侧请求异常" -ForegroundColor Red
}

Write-Host ""
Write-Host "======================== 连接完成，可打开 App ========================" -ForegroundColor Cyan
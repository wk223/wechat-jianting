# 微信@消息监控 APK 编译脚本
# 右键 -> 用 PowerShell 运行

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  微信@消息监控 APK 编译工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ── 路径配置 ──────────────────────────────────────────
$ANDROID_SDK  = "D:\SDK"
$GRADLE_HOME  = "D:\浏览下载\gradle-9.4.1-all\gradle-9.4.1"
$GRADLE_BIN   = "$GRADLE_HOME\bin\gradle.bat"
# ──────────────────────────────────────────────────────

# 检查 Gradle
if (-not (Test-Path $GRADLE_BIN)) {
    Write-Host "[错误] 找不到 Gradle: $GRADLE_BIN" -ForegroundColor Red
    Write-Host "请确认路径是否正确" -ForegroundColor Yellow
    Read-Host "按回车退出"
    exit 1
}
Write-Host "[OK] Gradle: $GRADLE_HOME" -ForegroundColor Green

# 检查 Android SDK
if (-not (Test-Path $ANDROID_SDK)) {
    Write-Host "[错误] 找不到 Android SDK: $ANDROID_SDK" -ForegroundColor Red
    Read-Host "按回车退出"
    exit 1
}
Write-Host "[OK] Android SDK: $ANDROID_SDK" -ForegroundColor Green

# 检查 Java
try {
    $null = & java -version 2>&1
    Write-Host "[OK] Java 已就绪" -ForegroundColor Green
} catch {
    Write-Host "[错误] 找不到 Java，请安装 JDK 17+" -ForegroundColor Red
    Read-Host "按回车退出"
    exit 1
}

Write-Host ""
Write-Host "开始编译，请稍候..." -ForegroundColor Cyan
Write-Host ""

# 切换到脚本所在目录
Set-Location $PSScriptRoot

# 写入 local.properties
"sdk.dir=$($ANDROID_SDK -replace '\\','\\')" | Out-File -FilePath "local.properties" -Encoding utf8
Write-Host "[OK] local.properties 已写入" -ForegroundColor Green

# 执行编译
& cmd /c "`"$GRADLE_BIN`" assembleDebug --no-daemon --stacktrace"

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "[失败] 编译出错，请查看上方日志" -ForegroundColor Red
    Write-Host "常见原因：D:\SDK 里缺少 build-tools 或 platforms" -ForegroundColor Yellow
    Read-Host "按回车退出"
    exit 1
}

# 输出结果
$APK = "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $APK) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  编译成功！" -ForegroundColor Green
    Write-Host "  APK: $PSScriptRoot\$APK" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Start-Process "explorer.exe" "app\build\outputs\apk\debug"
} else {
    Write-Host "[警告] 未找到 APK 文件" -ForegroundColor Yellow
}

Read-Host "按回车退出"

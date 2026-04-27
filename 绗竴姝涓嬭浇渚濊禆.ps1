# 下载 gradle-wrapper.jar（只需运行一次）
$url = "https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar"
$dest = "$PSScriptRoot\gradle\wrapper\gradle-wrapper.jar"

Write-Host "正在下载 gradle-wrapper.jar..." -ForegroundColor Cyan

try {
    Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing
    Write-Host "下载完成！现在可以双击「编译APK.bat」开始编译。" -ForegroundColor Green
} catch {
    Write-Host "下载失败，请手动下载：" -ForegroundColor Red
    Write-Host $url
    Write-Host "保存到：$dest"
}
pause

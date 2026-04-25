@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command "& {Set-Location (Split-Path -Parent $MyInvocation.MyCommand.Path); [System.IO.File]::WriteAllText((Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'local.properties'), 'sdk.dir=D:\\SDK'); $env:JAVA_HOME=$null; & 'D:\gradle\bin\gradle.bat' assembleDebug --no-daemon; if ($LASTEXITCODE -eq 0) { Write-Host 'BUILD SUCCESS' -ForegroundColor Green; Start-Process 'explorer' 'app\build\outputs\apk\debug' } else { Write-Host 'BUILD FAILED' -ForegroundColor Red }; Read-Host 'Press Enter to exit'}"

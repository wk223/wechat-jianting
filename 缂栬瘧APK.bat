@echo off
chcp 936 >nul
cmd /k "cd /d "%~dp0" && D:\gradle\bin\gradle.bat assembleDebug --no-daemon & echo sdk.dir=D:\\SDK > local.properties"

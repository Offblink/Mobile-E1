@echo off
rem Re-wire device -> host port forwarding. Run after rebooting the emulator
rem or reconnecting a phone (adb reverse is not persistent).
rem ASCII-only comments.
setlocal
set "ADB=C:\Android\AndroidSDK\platform-tools\adb.exe"
if not exist "%ADB%" (
    echo adb not found at %ADB%
    pause
    exit /b 1
)
"%ADB%" devices
"%ADB%" reverse tcp:8081 tcp:8081
echo.
echo done: device 127.0.0.1:8081 -^> host 8081
pause

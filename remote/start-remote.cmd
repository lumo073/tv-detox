@echo off
rem Double-click (or use the "TV Remote" desktop shortcut) to start the phone remote. It opens on
rem this laptop too, and prints the link for your phone. Keep this window open while you use it.
rem Change the IP if your TV gets a new one.
title TV Remote
cd /d "%~dp0"
set "PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools;%PATH%"
adb connect 192.168.1.69:5555 >nul 2>&1
python tv_remote.py 192.168.1.69 --open
pause

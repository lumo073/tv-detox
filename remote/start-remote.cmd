@echo off
rem Double-click to start the phone remote. Change the IP if your TV gets a new one.
cd /d "%~dp0"
set "PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools;%PATH%"
python tv_remote.py 192.168.1.69
pause

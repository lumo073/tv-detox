@echo off
rem Connects to the TV, checks every change in my-tv.txt is still in place, puts back anything an
rem update undid, and clears caches. Same as the 8:30 PM daily check, on demand.
title TV Check and Fix
cd /d "%~dp0.."
set "PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools;%PATH%"
set "MSYS2_ARG_CONV_EXCL=/proc;/sdcard;/data;/system"
set "BASH=%ProgramFiles%\Git\bin\bash.exe"
if not exist "%BASH%" (
  echo Git Bash isn't installed at %BASH%.
  pause
  exit /b 1
)
"%BASH%" -c "sh check.sh 192.168.1.69 --fix my-tv.txt; adb -s 192.168.1.69:5555 shell pm trim-caches 99G && echo Caches cleared. && adb -s 192.168.1.69:5555 shell grep MemAvailable /proc/meminfo"
echo.
pause

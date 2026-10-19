@echo off
rem Shows the TV's screen in a window on this laptop, and lets you control it with the mouse and
rem keyboard. Uses scrcpy (https://github.com/Genymobile/scrcpy), unzipped into
rem %LOCALAPPDATA%\Programs\scrcpy-win64-v5.0.1. Change the IP if your TV gets a new one.
rem
rem In the window: click = OK on that spot, right-click = Back, middle-click = Home,
rem arrow keys move, Enter = OK, Esc = Back, and you can type into search boxes.
rem Settings are light on purpose (720p, 30 fps), so the TV stays quick while you watch.
set "SCRCPY=%LOCALAPPDATA%\Programs\scrcpy-win64-v5.0.1\scrcpy.exe"
rem Use the same adb as everything else, so the phone remote isn't disconnected.
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB="
if not exist "%SCRCPY%" (
  echo scrcpy isn't at %SCRCPY%
  echo Download scrcpy-win64 from https://github.com/Genymobile/scrcpy/releases and unzip it there.
  pause
  exit /b 1
)
if defined ADB ("%ADB%" connect 192.168.1.69:5555 >nul) else ("%SCRCPY:scrcpy.exe=adb.exe%" connect 192.168.1.69:5555 >nul)
"%SCRCPY%" -s 192.168.1.69:5555 --max-size=1280 --video-bit-rate=4M --max-fps=30 --no-audio --window-title="Mi TV"
if errorlevel 1 pause

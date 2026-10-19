@echo off
rem Shows the TV's screen in a window on this laptop, and lets you control it with the mouse and
rem keyboard. Uses scrcpy (https://github.com/Genymobile/scrcpy), unzipped into
rem %LOCALAPPDATA%\Programs\scrcpy-win64-v5.0.1. Change the IP if your TV gets a new one.
rem
rem The TV sees a real mouse and keyboard (HID), because TV apps ignore touch:
rem   - Click in the window and the mouse moves a pointer on the TV. Press Alt to get your mouse
rem     back for the laptop; click the window again to control the TV.
rem   - Arrow keys move, Enter = OK, and you can type straight into search boxes.
rem   - Alt+B = Back, Alt+H = Home, Alt+M = Menu (scrcpy's own shortcuts; Esc does nothing on this TV).
rem     Right-click usually works as Back too.
rem Video in YouTube, Netflix and other players shows black here (subtitles still show): the TV
rem plays video on its own hardware layer, and Netflix blocks capture. Menus show fine.
rem Settings are light on purpose (720p, 30 fps), so the TV stays quick.
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
"%SCRCPY%" -s 192.168.1.69:5555 --max-size=1280 --video-bit-rate=4M --max-fps=30 --no-audio ^
  --keyboard=uhid --mouse=uhid --window-title="Mi TV  -  Alt+B Back  -  Alt+H Home  -  Alt frees the mouse"
if errorlevel 1 pause

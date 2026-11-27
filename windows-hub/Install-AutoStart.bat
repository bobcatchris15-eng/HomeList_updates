@echo off
setlocal
set "HOMELIST_HUB_EXE=%~dp0HomeListHub.exe"
set "HOMELIST_HUB_DIR=%~dp0"
set "HOMELIST_HUB_LINK=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\HomeList Home Hub.lnk"

if exist "%HOMELIST_HUB_LINK%" (
    del "%HOMELIST_HUB_LINK%"
    echo Removed HomeList Home Hub from Windows Startup. Run this again to install the current shortcut.
    pause
    exit /b 0
)
if not exist "%HOMELIST_HUB_EXE%" (
    echo HomeListHub.exe is missing. Extract the complete Windows Hub release ZIP first.
    pause
    exit /b 1
)

powershell.exe -NoProfile -Command ^
  "$ErrorActionPreference = 'Stop'; $ws = New-Object -ComObject WScript.Shell;" ^
  "$s = $ws.CreateShortcut($env:HOMELIST_HUB_LINK);" ^
  "$s.TargetPath = $env:HOMELIST_HUB_EXE;" ^
  "$s.WorkingDirectory = $env:HOMELIST_HUB_DIR;" ^
  "$s.WindowStyle = 7; $s.Save()"
if errorlevel 1 (
    pause
    exit /b 1
)

echo Added HomeList Home Hub to Windows Startup. It starts minimized on login.
echo Run this file again to remove it from Startup.
pause

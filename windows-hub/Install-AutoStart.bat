@echo off
setlocal
set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "SHORTCUT=%STARTUP%\HomeList Home Hub.lnk"

if exist "%SHORTCUT%" (
    del "%SHORTCUT%"
    echo Removed HomeList Home Hub from Windows Startup.
    pause
    exit /b 0
)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ws = New-Object -ComObject WScript.Shell; " ^
  "$s = $ws.CreateShortcut('%SHORTCUT%'); " ^
  "$s.TargetPath = 'powershell.exe'; " ^
  "$s.Arguments = '-NoProfile -ExecutionPolicy Bypass -File ""%~dp0HomeListHub.ps1""'; " ^
  "$s.WorkingDirectory = '%~dp0'; " ^
  "$s.WindowStyle = 7; " ^
  "$s.Save()"

echo Added HomeList Home Hub to Windows Startup (starts minimized on login).
echo Run this file again anytime to remove it from Startup.
pause

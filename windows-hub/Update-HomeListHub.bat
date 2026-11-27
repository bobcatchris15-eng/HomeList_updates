@echo off
title Update HomeList Windows Home Hub
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Update-HomeListHub.ps1"
set "HOMELIST_RESULT=%ERRORLEVEL%"
pause
exit /b %HOMELIST_RESULT%

@echo off
title HomeList Windows Home Hub
if not exist "%~dp0HomeListHub.exe" (
    echo HomeListHub.exe is missing. Extract the complete Windows Hub release ZIP or run Update-HomeListHub.bat.
    pause
    exit /b 1
)
"%~dp0HomeListHub.exe" %*
set "HOMELIST_RESULT=%ERRORLEVEL%"
if not "%HOMELIST_RESULT%"=="0" pause
exit /b %HOMELIST_RESULT%

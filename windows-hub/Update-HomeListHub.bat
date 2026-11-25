@echo off
title Update HomeList Windows Home Hub
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0HomeListHub.ps1" -UpdateNow
pause

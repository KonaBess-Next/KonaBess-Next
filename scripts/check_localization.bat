@echo off
:: KonaBess Localization Checker
:: This batch file runs the PowerShell localization checker script
:: 
:: Usage: Just double-click this file or run from command prompt

cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0check_localization.ps1"
pause

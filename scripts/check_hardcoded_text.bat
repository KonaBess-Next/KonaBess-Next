@echo off
:: KonaBess Hardcoded Text Checker

cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0check_hardcoded_text.ps1" %*
echo.
echo Press any key to exit...
pause >nul

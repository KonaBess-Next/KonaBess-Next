@echo off
:: KonaBess Localization Checker
:: Auto-detects locales and missing strings

cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0check_localization.ps1"
echo.
echo Press any key to exit...
pause >nul

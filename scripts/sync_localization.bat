@echo off
:: KonaBess Localization Sync Tool
:: Sync existing locales from English base values/strings*.xml

cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sync_localization.ps1"
echo.
echo Press any key to exit...
pause >nul

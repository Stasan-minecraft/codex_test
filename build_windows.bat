@echo off
setlocal

where pyinstaller >nul 2>nul
if %ERRORLEVEL% neq 0 (
  echo [ERROR] PyInstaller is not installed.
  echo Install with: py -m pip install pyinstaller
  exit /b 1
)

pyinstaller --noconfirm --onefile --windowed --name OpenAI-Codex-Windows app.py

echo.
echo Build completed. Executable is in dist\OpenAI-Codex-Windows.exe
endlocal

@echo off
setlocal

if "%OPENAI_API_KEY%"=="" (
    echo [INFO] OPENAI_API_KEY is not set. You can still type it in the app.
)

python app.py
endlocal

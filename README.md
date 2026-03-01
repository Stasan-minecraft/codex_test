# OpenAI Codex (for Windows)

Сучасний desktop-клієнт OpenAI Codex для Windows на `tkinter/ttk`.

## Що покращено

- ✅ **One-click OAuth auto mode**: тиснеш `Login with OpenAI` — браузер відкривається автоматично, токен повертається в app.
- ✅ **Без обов'язкового `OPENAI_OAUTH_CLIENT_ID`**: працює auto-mode, `client_id` додається тільки якщо заданий.
- ✅ **Live model sync**: `Refresh models` підтягує актуальний список з `/v1/models`.
- ✅ **Codex-first сортування**: моделі з `codex` показуються першими.
- ✅ **Краща стабільність**: сумісні type hints для PyInstaller, без крашу `unsupported operand type(s) for |`.
- ✅ **Кращий UX**: темна тема, статус-бар, блокування кнопок під час запитів, `Ctrl+Enter` для запуску.

## Швидкий старт (Windows)

1. Встанови Python 3.10+.
2. Запусти додаток:
   - `run_windows.bat`
3. Натисни `Login with OpenAI` і залогінься в браузері.
4. Натисни `Refresh models` (або дочекайся авто-оновлення після OAuth).
5. Введи prompt і натисни `Run` (або `Ctrl+Enter`).

## Налаштування через env (опціонально)

- `OPENAI_API_KEY`
- `OPENAI_OAUTH_TOKEN`
- `OPENAI_MODEL`
- `OPENAI_OAUTH_CLIENT_ID` (опціонально)
- `OPENAI_OAUTH_AUTH_URL` (default: `https://auth.openai.com/oauth/authorize`)
- `OPENAI_OAUTH_TOKEN_URL` (default: `https://auth.openai.com/oauth/token`)
- `OPENAI_OAUTH_SCOPES` (default: `openid profile email`)
- `OPENAI_OAUTH_REDIRECT_URI` (default: `http://127.0.0.1:8765/callback`)

## Збірка EXE

1. Встанови PyInstaller:
   - `py -m pip install pyinstaller`
2. Запусти:
   - `build_windows.bat`
3. Готовий файл:
   - `dist\OpenAI-Codex-Windows.exe`

## Файли

- `app.py` — GUI застосунок.
- `run_windows.bat` — запуск локально.
- `build_windows.bat` — збірка `.exe`.

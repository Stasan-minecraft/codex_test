# OpenAI Codex (for Windows)

Сучасний desktop-клієнт OpenAI Codex для Windows на `tkinter/ttk`.

## Головні покращення

- ✅ **Норм дизайн + вкладки**: `Assistant` і `Settings`.
- ✅ **Реальний OAuth “1 кнопка і готово”**: натискаєш `Login with OpenAI (1 click)` — браузер відкривається, токен повертається в app.
- ✅ **Auto fallback OAuth**: якщо endpoint вимагає `client_id`, app автоматично попросить його один раз і повторить логін.
- ✅ **Live models**: `Refresh models` тягне актуальні моделі з `/v1/models`, Codex-першими.
- ✅ **Кращий UX**: progress bar (анімація завантаження), блокування кнопок під час запитів, `Ctrl+Enter`.

## Швидкий старт (Windows)

1. Встанови Python 3.10+.
2. Запусти:
   - `run_windows.bat`
3. У вкладці `Assistant` натисни `Login with OpenAI (1 click)`.
4. Якщо OAuth-сервер попросить `client_id`, app сам попросить ввести його і повторить процес.
5. Натисни `Refresh models`, введи prompt, натисни `Run`.

## Вкладки

- **Assistant**: логін, токени, вибір моделі, prompt/response.
- **Settings**: OAuth client id, auth/token URL, scopes, redirect URI.

## Налаштування через env (опціонально)

- `OPENAI_API_KEY`
- `OPENAI_OAUTH_TOKEN`
- `OPENAI_MODEL`
- `OPENAI_OAUTH_CLIENT_ID`
- `OPENAI_OAUTH_AUTH_URL` (default: `https://auth.openai.com/oauth/authorize`)
- `OPENAI_OAUTH_TOKEN_URL` (default: `https://auth.openai.com/oauth/token`)
- `OPENAI_OAUTH_SCOPES` (default: `openid profile email`)
- `OPENAI_OAUTH_REDIRECT_URI` (default: `http://127.0.0.1:8765/callback`)

## Збірка EXE

1. Встанови PyInstaller:
   - `py -m pip install pyinstaller`
2. Запусти:
   - `build_windows.bat`
3. EXE:
   - `dist\OpenAI-Codex-Windows.exe`

## Файли

- `app.py` — GUI застосунок.
- `run_windows.bat` — запуск локально.
- `build_windows.bat` — збірка `.exe`.

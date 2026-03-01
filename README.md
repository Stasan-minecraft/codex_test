# OpenAI Codex (for Windows)

Сучасний desktop-клієнт OpenAI Codex для Windows на `tkinter/ttk`.

## Що зроблено під твій запит

- ✅ **One-click OAuth**: натискаєш `Login with OpenAI` → браузер відкривається автоматично → токен повертається в app автоматично.
- ✅ **Авто-список моделей**: кнопка `Refresh models` тягне **актуальний список з OpenAI `/v1/models`**, тому бачиш реально існуючі моделі, включно з Codex.
- ✅ **Codex first**: у списку після refresh моделі з `codex` піднімаються на початок.
- ✅ **Кращий UI/UX**: темна тема, статус-бар, clear output/tokens, блокування кнопок під час запитів.
- ✅ **Fallback на API key** якщо OAuth не налаштований.

## Швидкий старт (Windows)

1. Встанови Python 3.10+.
2. Запусти додаток:
   - `run_windows.bat`
3. Натисни `Login with OpenAI`, залогінься в браузері.
4. Натисни `Refresh models` (або після OAuth список оновиться автоматично).
5. Пиши prompt і тисни `Run`.

> `OPENAI_OAUTH_CLIENT_ID` тепер **не обов'язковий**: додаток працює в auto-mode і передає `client_id` лише якщо він заданий.

## Моделі

- При старті є fallback-перелік моделей для зручності.
- Після авторизації тисни `Refresh models`, і додаток завантажить **всі доступні моделі саме для твого акаунта/токена**.
- Можна вписувати будь-яку модель вручну в поле `Model`.

## Налаштування через env (опціонально)

- `OPENAI_API_KEY`
- `OPENAI_OAUTH_TOKEN`
- `OPENAI_MODEL`
- `OPENAI_OAUTH_CLIENT_ID` (опціонально; додається автоматично, якщо задано)
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

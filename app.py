import base64
import hashlib
import json
import os
import secrets
import threading
import time
import tkinter as tk
import webbrowser
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, HTTPServer
from tkinter import messagebox, scrolledtext, simpledialog, ttk
from typing import Any, Dict, List, Optional, Tuple
from urllib import error, parse, request

API_BASE = "https://api.openai.com/v1"
RESPONSES_URL = f"{API_BASE}/responses"
MODELS_URL = f"{API_BASE}/models"

DEFAULT_MODEL = "gpt-5-codex"
MODEL_FALLBACK = [
    "gpt-5-codex",
    "gpt-5",
    "gpt-5-mini",
    "gpt-5-nano",
    "gpt-4.1",
    "gpt-4.1-mini",
    "gpt-4o",
    "gpt-4o-mini",
    "o4-mini",
]


@dataclass
class AppConfig:
    oauth_client_id: str
    oauth_auth_url: str
    oauth_token_url: str
    oauth_scopes: str
    oauth_redirect_uri: str


@dataclass
class OAuthResult:
    access_token: str
    state: str


def load_config() -> AppConfig:
    return AppConfig(
        oauth_client_id=os.getenv("OPENAI_OAUTH_CLIENT_ID", "").strip(),
        oauth_auth_url=os.getenv("OPENAI_OAUTH_AUTH_URL", "https://auth.openai.com/oauth/authorize").strip(),
        oauth_token_url=os.getenv("OPENAI_OAUTH_TOKEN_URL", "https://auth.openai.com/oauth/token").strip(),
        oauth_scopes=os.getenv("OPENAI_OAUTH_SCOPES", "openid profile email").strip(),
        oauth_redirect_uri=os.getenv("OPENAI_OAUTH_REDIRECT_URI", "http://127.0.0.1:8765/callback").strip(),
    )


class CodexWindowsApp:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self.cfg = load_config()

        self.root.title("OpenAI Codex for Windows")
        self.root.geometry("1140x780")
        self.root.minsize(900, 640)
        self.root.configure(bg="#0f172a")

        self.api_key = tk.StringVar(value=os.getenv("OPENAI_API_KEY", ""))
        self.oauth_token = tk.StringVar(value=os.getenv("OPENAI_OAUTH_TOKEN", ""))
        self.model = tk.StringVar(value=os.getenv("OPENAI_MODEL", DEFAULT_MODEL))
        self.status = tk.StringVar(value="Ready")
        self.oauth_client_id = tk.StringVar(value=self.cfg.oauth_client_id)

        self._configure_theme()
        self._build_ui()
        self._bind_shortcuts()
        self._set_model_options(MODEL_FALLBACK)

    def _configure_theme(self) -> None:
        style = ttk.Style(self.root)
        if "clam" in style.theme_names():
            style.theme_use("clam")

        style.configure("App.TFrame", background="#111827")
        style.configure("Header.TLabel", background="#111827", foreground="#e5e7eb", font=("Segoe UI", 13, "bold"))
        style.configure("Sub.TLabel", background="#111827", foreground="#9ca3af", font=("Segoe UI", 10))
        style.configure("Accent.TButton", font=("Segoe UI", 10, "bold"))

    def _build_ui(self) -> None:
        outer = ttk.Frame(self.root, style="App.TFrame", padding=14)
        outer.pack(fill=tk.BOTH, expand=True)

        header = ttk.Frame(outer, style="App.TFrame")
        header.pack(fill=tk.X, pady=(0, 10))
        ttk.Label(header, text="OpenAI Codex Desktop", style="Header.TLabel").pack(side=tk.LEFT)
        ttk.Label(header, text="Windows • OAuth auto-login • Live model sync", style="Sub.TLabel").pack(side=tk.RIGHT)

        cred = ttk.LabelFrame(outer, text="Authorization", padding=10)
        cred.pack(fill=tk.X, pady=(0, 10))

        ttk.Label(cred, text="OAuth access token", style="Sub.TLabel").grid(row=0, column=0, sticky="w")
        ttk.Entry(cred, textvariable=self.oauth_token, show="*", width=70).grid(row=0, column=1, sticky="ew", padx=8)
        self.oauth_btn = ttk.Button(cred, text="Login with OpenAI", style="Accent.TButton", command=self.start_oauth)
        self.oauth_btn.grid(row=0, column=2, sticky="w")

        ttk.Label(cred, text="OAuth client id", style="Sub.TLabel").grid(row=1, column=0, sticky="w", pady=(8, 0))
        ttk.Entry(cred, textvariable=self.oauth_client_id, width=70).grid(row=1, column=1, sticky="ew", padx=8, pady=(8, 0))
        ttk.Label(cred, text="(required by many OAuth setups)", style="Sub.TLabel").grid(row=1, column=2, sticky="w", pady=(8, 0))

        ttk.Label(cred, text="API key (fallback)", style="Sub.TLabel").grid(row=2, column=0, sticky="w", pady=(8, 0))
        ttk.Entry(cred, textvariable=self.api_key, show="*", width=70).grid(row=2, column=1, sticky="ew", padx=8, pady=(8, 0))
        ttk.Button(cred, text="Clear tokens", command=self.clear_tokens).grid(row=2, column=2, sticky="w", pady=(8, 0))
        cred.columnconfigure(1, weight=1)

        main = ttk.LabelFrame(outer, text="Model + Prompt", padding=10)
        main.pack(fill=tk.BOTH, expand=True)

        controls = ttk.Frame(main)
        controls.pack(fill=tk.X, pady=(0, 8))

        ttk.Label(controls, text="Model", style="Sub.TLabel").pack(side=tk.LEFT)
        self.model_box = ttk.Combobox(controls, textvariable=self.model, width=30)
        self.model_box.pack(side=tk.LEFT, padx=(8, 10))
        self.refresh_models_btn = ttk.Button(controls, text="Refresh models", command=self.refresh_models)
        self.refresh_models_btn.pack(side=tk.LEFT)

        self.run_btn = ttk.Button(controls, text="Run", style="Accent.TButton", command=self.ask_model)
        self.run_btn.pack(side=tk.RIGHT)
        ttk.Button(controls, text="Clear Output", command=self.clear_output).pack(side=tk.RIGHT, padx=6)

        ttk.Label(main, text="Prompt (Ctrl+Enter to run)", style="Sub.TLabel").pack(anchor="w")
        self.prompt = scrolledtext.ScrolledText(
            main,
            height=10,
            wrap=tk.WORD,
            bg="#0b1220",
            fg="#d1d5db",
            insertbackground="#f8fafc",
            relief=tk.FLAT,
            padx=10,
            pady=10,
        )
        self.prompt.pack(fill=tk.BOTH, expand=False)
        self.prompt.insert(
            "1.0",
            "Напиши короткий план дій та приклад коду для задачі, яку я опишу нижче...",
        )

        ttk.Label(main, text="Response / Logs", style="Sub.TLabel").pack(anchor="w", pady=(8, 0))
        self.output = scrolledtext.ScrolledText(
            main,
            wrap=tk.WORD,
            state=tk.DISABLED,
            bg="#020617",
            fg="#dbeafe",
            insertbackground="#f8fafc",
            relief=tk.FLAT,
            padx=10,
            pady=10,
        )
        self.output.pack(fill=tk.BOTH, expand=True)

        ttk.Label(
            outer,
            text="Tip: після OAuth натисни Refresh models, щоб отримати повний актуальний список /v1/models.",
            style="Sub.TLabel",
        ).pack(fill=tk.X, pady=(8, 0))

        self.progress = ttk.Progressbar(outer, mode="indeterminate")
        self.progress.pack(fill=tk.X, pady=(4, 0))
        ttk.Label(outer, textvariable=self.status, style="Sub.TLabel").pack(fill=tk.X, pady=(4, 0))

    def _bind_shortcuts(self) -> None:
        self.root.bind("<Control-Return>", lambda _e: self.ask_model())

    def _set_status(self, text: str) -> None:
        self.root.after(0, lambda: self.status.set(text))

    def _log(self, text: str) -> None:
        def update() -> None:
            self.output.config(state=tk.NORMAL)
            self.output.insert(tk.END, f"{text}\n")
            self.output.see(tk.END)
            self.output.config(state=tk.DISABLED)

        self.root.after(0, update)

    def _set_busy(self, busy: bool) -> None:
        state = tk.DISABLED if busy else tk.NORMAL
        self.root.after(0, lambda: self.run_btn.config(state=state))
        self.root.after(0, lambda: self.oauth_btn.config(state=state))
        self.root.after(0, lambda: self.refresh_models_btn.config(state=state))
        if busy:
            self.root.after(0, lambda: self.progress.start(10))
        else:
            self.root.after(0, self.progress.stop)

    def clear_tokens(self) -> None:
        self.oauth_token.set("")
        self.api_key.set("")
        self._set_status("Tokens cleared")

    def clear_output(self) -> None:
        self.output.config(state=tk.NORMAL)
        self.output.delete("1.0", tk.END)
        self.output.config(state=tk.DISABLED)
        self._set_status("Output cleared")

    def _resolve_bearer(self) -> str:
        token = self.oauth_token.get().strip()
        key = self.api_key.get().strip()
        if token:
            return token
        if key:
            return key
        raise RuntimeError("Provide OPENAI_API_KEY or login via OpenAI OAuth.")

    def _set_model_options(self, models: List[str]) -> None:
        cleaned = sorted({m.strip() for m in models if m and m.strip()})
        if not cleaned:
            cleaned = MODEL_FALLBACK
        self.model_box["values"] = cleaned
        if not self.model.get().strip():
            self.model.set(cleaned[0])

    def refresh_models(self) -> None:
        try:
            bearer = self._resolve_bearer()
        except Exception as exc:
            messagebox.showerror("Missing credentials", str(exc))
            return

        self._set_status("Refreshing models...")
        self._set_busy(True)
        threading.Thread(target=self._refresh_models_worker, args=(bearer,), daemon=True).start()

    def _refresh_models_worker(self, bearer: str) -> None:
        try:
            models = _fetch_models(bearer)
            ordered = _codex_first(models)
            self.root.after(0, lambda: self._set_model_options(ordered))
            if ordered and not self.model.get().strip():
                self.root.after(0, lambda: self.model.set(ordered[0]))
            self._log(f"[Models] Loaded {len(ordered)} models from OpenAI.")
            self._set_status("Models refreshed")
        except Exception as exc:
            self._log(f"[Models][ERROR] {exc}")
            self._set_status("Model refresh failed")
        finally:
            self._set_busy(False)

    def ask_model(self) -> None:
        prompt = self.prompt.get("1.0", tk.END).strip()
        model = self.model.get().strip()
        if not prompt:
            messagebox.showerror("Missing prompt", "Please enter a prompt.")
            return
        if not model:
            messagebox.showerror("Missing model", "Please select or enter model.")
            return

        try:
            bearer = self._resolve_bearer()
        except Exception as exc:
            messagebox.showerror("Missing credentials", str(exc))
            return

        self._set_status("Request in progress...")
        self._set_busy(True)
        threading.Thread(target=self._worker, args=(bearer, model, prompt), daemon=True).start()

    def _worker(self, bearer: str, model: str, prompt: str) -> None:
        try:
            answer = _call_openai(bearer, model, prompt)
            self._log(f"\n>>> {model}\n{answer}\n")
            self._set_status("Request completed")
        except Exception as exc:
            self._log(f"\n[ERROR] {exc}\n")
            self._set_status("Request failed")
        finally:
            self._set_busy(False)

    def start_oauth(self) -> None:
        client_id = self.oauth_client_id.get().strip()
        if not client_id:
            client_id = simpledialog.askstring(
                "OAuth Client ID",
                "Введи OPENAI OAuth Client ID (потрібно для авторизації):",
                parent=self.root,
            ) or ""
            client_id = client_id.strip()
            if not client_id:
                messagebox.showerror("Missing OAuth client id", "OAuth client id required by auth server.")
                return
            self.oauth_client_id.set(client_id)

        self._set_busy(True)
        self.root.after(0, lambda: self.oauth_btn.config(text="Waiting OAuth..."))
        self._set_status("OAuth login started...")
        threading.Thread(target=self._oauth_worker, daemon=True).start()

    def _oauth_worker(self) -> None:
        try:
            verifier = _create_code_verifier()
            challenge = _create_code_challenge(verifier)
            state = secrets.token_urlsafe(24)

            auth_params = {
                "response_type": "code",
                "redirect_uri": self.cfg.oauth_redirect_uri,
                "scope": self.cfg.oauth_scopes,
                "state": state,
                "code_challenge": challenge,
                "code_challenge_method": "S256",
            }
            client_id = self.oauth_client_id.get().strip()
            if client_id:
                auth_params["client_id"] = client_id

            launch_url = f"{self.cfg.oauth_auth_url}?{parse.urlencode(auth_params)}"
            self._log("[OAuth] Opening browser for OpenAI sign-in...")
            opened = webbrowser.open(launch_url)
            if not opened:
                self._log("[OAuth] Could not open browser automatically. Open this URL manually:")
                self._log(launch_url)

            oauth = _finish_oauth_flow(
                token_url=self.cfg.oauth_token_url,
                client_id=client_id or None,
                redirect_uri=self.cfg.oauth_redirect_uri,
                expected_state=state,
                code_verifier=verifier,
                timeout_s=240,
            )
            self.root.after(0, lambda: self.oauth_token.set(oauth.access_token))
            self._log("[OAuth] Login done. Access token inserted automatically.")

            models = _fetch_models(oauth.access_token)
            ordered = _codex_first(models)
            self.root.after(0, lambda: self._set_model_options(ordered))
            if ordered:
                self.root.after(0, lambda: self.model.set(ordered[0]))
            self._set_status("OAuth login successful")
        except Exception as exc:
            msg = str(exc)
            self._log(f"[OAuth][ERROR] {msg}")
            if "missing_required_parameter" in msg:
                self._log("[OAuth] Схоже, потрібен client_id. Вкажи його у полі OAuth client id і спробуй ще раз.")
            self._set_status("OAuth login failed")
        finally:
            self.root.after(0, lambda: self.oauth_btn.config(text="Login with OpenAI"))
            self._set_busy(False)


def _codex_first(models: List[str]) -> List[str]:
    codex = sorted([m for m in models if "codex" in m.lower()])
    others = sorted([m for m in models if "codex" not in m.lower()])
    return codex + others


def _safe_json_loads(raw: str) -> Dict[str, Any]:
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return {"raw": raw}
    if isinstance(data, dict):
        return data
    return {"raw": data}


def _http_json(method: str, url: str, bearer: str, body: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {bearer}")
    req.add_header("Content-Type", "application/json")

    try:
        with request.urlopen(req, timeout=120) as response:
            raw = response.read().decode("utf-8")
    except error.HTTPError as http_err:
        detail = http_err.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {http_err.code}: {detail}") from http_err
    except error.URLError as url_err:
        raise RuntimeError(f"Network error: {url_err}") from url_err

    return _safe_json_loads(raw)


def _fetch_models(bearer: str) -> List[str]:
    parsed = _http_json("GET", MODELS_URL, bearer)
    data = parsed.get("data", [])
    if not isinstance(data, list):
        return []
    result = []
    for item in data:
        if isinstance(item, dict):
            model_id = str(item.get("id", "")).strip()
            if model_id:
                result.append(model_id)
    return result


def _call_openai(bearer: str, model: str, prompt: str) -> str:
    parsed = _http_json("POST", RESPONSES_URL, bearer, body={"model": model, "input": prompt})
    if parsed.get("output_text"):
        return str(parsed["output_text"])

    chunks: List[str] = []
    for item in parsed.get("output", []):
        if not isinstance(item, dict):
            continue
        for content in item.get("content", []):
            if isinstance(content, dict) and content.get("type") == "output_text":
                chunks.append(str(content.get("text", "")))
    if chunks:
        return "".join(chunks).strip()

    return json.dumps(parsed, indent=2, ensure_ascii=False)


def _create_code_verifier() -> str:
    return base64.urlsafe_b64encode(secrets.token_bytes(64)).decode("utf-8").rstrip("=")


def _create_code_challenge(verifier: str) -> str:
    digest = hashlib.sha256(verifier.encode("utf-8")).digest()
    return base64.urlsafe_b64encode(digest).decode("utf-8").rstrip("=")


def _finish_oauth_flow(
    token_url: str,
    client_id: Optional[str],
    redirect_uri: str,
    expected_state: str,
    code_verifier: str,
    timeout_s: int,
) -> OAuthResult:
    code, returned_state = _wait_for_callback_code(redirect_uri, timeout_s)
    if returned_state and returned_state != expected_state:
        raise RuntimeError("OAuth state mismatch. Please retry.")

    token = _exchange_oauth_token(
        token_url=token_url,
        client_id=client_id,
        code=code,
        redirect_uri=redirect_uri,
        code_verifier=code_verifier,
    )
    return OAuthResult(access_token=token, state=returned_state)


def _wait_for_callback_code(redirect_uri: str, timeout_s: int) -> Tuple[str, str]:
    parsed_redirect = parse.urlparse(redirect_uri)
    result: Dict[str, Optional[str]] = {"code": None, "state": None}

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802
            q = parse.urlparse(self.path)
            params = parse.parse_qs(q.query)
            result["code"] = params.get("code", [None])[0]
            result["state"] = params.get("state", [None])[0]
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(b"<html><body><h3>Login success. You can close this tab.</h3></body></html>")

        def log_message(self, fmt: str, *args: Any) -> None:  # noqa: A003
            return

    host = parsed_redirect.hostname or "127.0.0.1"
    port = parsed_redirect.port or 80
    try:
        server = HTTPServer((host, port), Handler)
    except OSError as exc:
        raise RuntimeError(f"OAuth callback server failed on {host}:{port}. Port busy? {exc}") from exc

    server.timeout = 0.5
    start = time.time()

    while time.time() - start < timeout_s:
        server.handle_request()
        if result["code"]:
            break

    server.server_close()
    if not result["code"]:
        raise RuntimeError(f"OAuth callback timeout for {redirect_uri}")

    return str(result["code"]), str(result["state"] or "")


def _exchange_oauth_token(token_url: str, client_id: Optional[str], code: str, redirect_uri: str, code_verifier: str) -> str:
    payload: Dict[str, str] = {
        "grant_type": "authorization_code",
        "code": code,
        "redirect_uri": redirect_uri,
        "code_verifier": code_verifier,
    }
    if client_id:
        payload["client_id"] = client_id

    req = request.Request(token_url, data=parse.urlencode(payload).encode("utf-8"), method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")

    try:
        with request.urlopen(req, timeout=60) as response:
            raw = response.read().decode("utf-8")
    except error.HTTPError as http_err:
        detail = http_err.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"OAuth token exchange failed ({http_err.code}): {detail}") from http_err
    except error.URLError as url_err:
        raise RuntimeError(f"Network error during OAuth token exchange: {url_err}") from url_err

    parsed = _safe_json_loads(raw)
    token = parsed.get("access_token")
    if not token:
        raise RuntimeError(f"OAuth token response has no access_token: {parsed}")
    return str(token)


if __name__ == "__main__":
    root = tk.Tk()
    app = CodexWindowsApp(root)
    root.mainloop()

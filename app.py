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
from tkinter import messagebox, scrolledtext, ttk
from urllib import error, parse, request
from typing import Any, Dict, List, Optional, Tuple

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

# OAuth defaults. Client ID optional (auto mode).
OAUTH_CLIENT_ID = os.getenv("OPENAI_OAUTH_CLIENT_ID", "").strip()
OAUTH_AUTH_URL = os.getenv("OPENAI_OAUTH_AUTH_URL", "https://auth.openai.com/oauth/authorize").strip()
OAUTH_TOKEN_URL = os.getenv("OPENAI_OAUTH_TOKEN_URL", "https://auth.openai.com/oauth/token").strip()
OAUTH_SCOPES = os.getenv("OPENAI_OAUTH_SCOPES", "openid profile email").strip()
OAUTH_REDIRECT_URI = os.getenv("OPENAI_OAUTH_REDIRECT_URI", "http://127.0.0.1:8765/callback").strip()


@dataclass
class OAuthResult:
    access_token: str
    state: str


class CodexWindowsApp:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self.root.title("OpenAI Codex for Windows")
        self.root.geometry("1120x780")
        self.root.minsize(860, 620)
        self.root.configure(bg="#0f172a")

        self.api_key = tk.StringVar(value=os.getenv("OPENAI_API_KEY", ""))
        self.oauth_token = tk.StringVar(value=os.getenv("OPENAI_OAUTH_TOKEN", ""))
        self.model = tk.StringVar(value=os.getenv("OPENAI_MODEL", DEFAULT_MODEL))
        self.status = tk.StringVar(value="Ready")

        self._configure_theme()
        self._build_ui()
        self._set_model_options(MODEL_FALLBACK)

    def _configure_theme(self) -> None:
        style = ttk.Style(self.root)
        if "clam" in style.theme_names():
            style.theme_use("clam")

        style.configure("Card.TFrame", background="#111827")
        style.configure("Title.TLabel", background="#111827", foreground="#e5e7eb", font=("Segoe UI", 12, "bold"))
        style.configure("Label.TLabel", background="#111827", foreground="#cbd5e1", font=("Segoe UI", 10))
        style.configure("Accent.TButton", font=("Segoe UI", 10, "bold"))

    def _build_ui(self) -> None:
        outer = ttk.Frame(self.root, style="Card.TFrame", padding=14)
        outer.pack(fill=tk.BOTH, expand=True)

        header = ttk.Frame(outer, style="Card.TFrame")
        header.pack(fill=tk.X, pady=(0, 10))
        ttk.Label(header, text="OpenAI Codex Desktop", style="Title.TLabel").pack(side=tk.LEFT)
        ttk.Label(header, text="Windows • One-click OAuth • Auto-model sync", style="Label.TLabel").pack(side=tk.RIGHT)

        cred = ttk.LabelFrame(outer, text="Authorization", padding=10)
        cred.pack(fill=tk.X, pady=(0, 10))

        ttk.Label(cred, text="OAuth Access Token", style="Label.TLabel").grid(row=0, column=0, sticky="w")
        ttk.Entry(cred, textvariable=self.oauth_token, show="*", width=68).grid(row=0, column=1, sticky="ew", padx=8)
        self.oauth_btn = ttk.Button(cred, text="Login with OpenAI", style="Accent.TButton", command=self.start_oauth)
        self.oauth_btn.grid(row=0, column=2, sticky="w")

        ttk.Label(cred, text="API Key (fallback)", style="Label.TLabel").grid(row=1, column=0, sticky="w", pady=(8, 0))
        ttk.Entry(cred, textvariable=self.api_key, show="*", width=68).grid(row=1, column=1, sticky="ew", padx=8, pady=(8, 0))
        ttk.Button(cred, text="Clear tokens", command=self.clear_tokens).grid(row=1, column=2, sticky="w", pady=(8, 0))
        cred.columnconfigure(1, weight=1)

        config = ttk.LabelFrame(outer, text="Model + Prompt", padding=10)
        config.pack(fill=tk.BOTH, expand=True)

        top_row = ttk.Frame(config)
        top_row.pack(fill=tk.X, pady=(0, 8))

        ttk.Label(top_row, text="Model", style="Label.TLabel").pack(side=tk.LEFT)
        self.model_box = ttk.Combobox(top_row, textvariable=self.model, width=28)
        self.model_box.pack(side=tk.LEFT, padx=(8, 10))
        self.refresh_models_btn = ttk.Button(top_row, text="Refresh models", command=self.refresh_models)
        self.refresh_models_btn.pack(side=tk.LEFT)

        self.run_btn = ttk.Button(top_row, text="Run", style="Accent.TButton", command=self.ask_model)
        self.run_btn.pack(side=tk.RIGHT)
        ttk.Button(top_row, text="Clear Output", command=self.clear_output).pack(side=tk.RIGHT, padx=6)

        ttk.Label(config, text="Prompt", style="Label.TLabel").pack(anchor="w")
        self.prompt = scrolledtext.ScrolledText(
            config,
            height=10,
            wrap=tk.WORD,
            bg="#0b1220",
            fg="#d1d5db",
            insertbackground="#f8fafc",
            relief=tk.FLAT,
            padx=8,
            pady=8,
        )
        self.prompt.pack(fill=tk.BOTH, expand=False)

        ttk.Label(config, text="Response / Logs", style="Label.TLabel").pack(anchor="w", pady=(8, 0))
        self.output = scrolledtext.ScrolledText(
            config,
            wrap=tk.WORD,
            state=tk.DISABLED,
            bg="#020617",
            fg="#dbeafe",
            insertbackground="#f8fafc",
            relief=tk.FLAT,
            padx=8,
            pady=8,
        )
        self.output.pack(fill=tk.BOTH, expand=True)

        ttk.Label(
            outer,
            text=(
                "Tip: натисни Refresh models після OAuth — список підтягнеться автоматично з /v1/models "
                "і покаже актуальні Codex-моделі."
            ),
            style="Label.TLabel",
        ).pack(fill=tk.X, pady=(8, 0))

        footer = ttk.Label(outer, textvariable=self.status, style="Label.TLabel")
        footer.pack(fill=tk.X, pady=(6, 0))

    def clear_tokens(self) -> None:
        self.oauth_token.set("")
        self.api_key.set("")
        self._set_status("Tokens cleared")

    def clear_output(self) -> None:
        self.output.config(state=tk.NORMAL)
        self.output.delete("1.0", tk.END)
        self.output.config(state=tk.DISABLED)
        self._set_status("Output cleared")

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
        self.root.after(0, lambda: self.oauth_btn.config(state=state if not busy else tk.DISABLED))
        self.root.after(0, lambda: self.refresh_models_btn.config(state=state))

    def _resolve_bearer(self) -> str:
        token = self.oauth_token.get().strip()
        key = self.api_key.get().strip()
        if token:
            return token
        if key:
            return key
        raise RuntimeError("Provide OPENAI_API_KEY or login via OpenAI OAuth.")

    def _set_model_options(self, models: list[str]) -> None:
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

        self._set_status("Refreshing models from OpenAI...")
        self._set_busy(True)
        threading.Thread(target=self._refresh_models_worker, args=(bearer,), daemon=True).start()

    def _refresh_models_worker(self, bearer: str) -> None:
        try:
            models = _fetch_models(bearer)
            codex_first = sorted([m for m in models if "codex" in m.lower()])
            others = sorted([m for m in models if "codex" not in m.lower()])
            combined = codex_first + others
            self.root.after(0, lambda: self._set_model_options(combined))
            if codex_first and self.model.get() not in codex_first:
                self.root.after(0, lambda: self.model.set(codex_first[0]))
            self._set_status(f"Models refreshed: {len(combined)} loaded")
            self._log(f"[Models] Loaded {len(combined)} models from OpenAI.")
        except Exception as exc:
            self._set_status("Model refresh failed")
            self._log(f"[Models][ERROR] {exc}")
        finally:
            self._set_busy(False)

    def ask_model(self) -> None:
        prompt = self.prompt.get("1.0", tk.END).strip()
        model = self.model.get().strip()
        if not prompt:
            messagebox.showerror("Missing prompt", "Please enter a prompt.")
            return
        if not model:
            messagebox.showerror("Missing model", "Please enter a model name.")
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
                "redirect_uri": OAUTH_REDIRECT_URI,
                "scope": OAUTH_SCOPES,
                "state": state,
                "code_challenge": challenge,
                "code_challenge_method": "S256",
            }
            if OAUTH_CLIENT_ID:
                auth_params["client_id"] = OAUTH_CLIENT_ID
            launch_url = f"{OAUTH_AUTH_URL}?{parse.urlencode(auth_params)}"
            self._log("[OAuth] Opening browser for OpenAI sign-in...")
            webbrowser.open(launch_url)

            oauth = _finish_oauth_flow(
                token_url=OAUTH_TOKEN_URL,
                client_id=OAUTH_CLIENT_ID or None,
                redirect_uri=OAUTH_REDIRECT_URI,
                expected_state=state,
                code_verifier=verifier,
                timeout_s=240,
            )
            self.root.after(0, lambda: self.oauth_token.set(oauth.access_token))
            self._log("[OAuth] Login done. Access token inserted automatically.")
            self._set_status("OAuth login successful. Refreshing models...")
            models = _fetch_models(oauth.access_token)
            codex_first = sorted([m for m in models if "codex" in m.lower()])
            others = sorted([m for m in models if "codex" not in m.lower()])
            self.root.after(0, lambda: self._set_model_options(codex_first + others))
            if codex_first:
                self.root.after(0, lambda: self.model.set(codex_first[0]))
            self._set_status("OAuth login successful")
        except Exception as exc:
            self._log(f"[OAuth][ERROR] {exc}")
            self._set_status("OAuth login failed")
        finally:
            self.root.after(0, lambda: self.oauth_btn.config(text="Login with OpenAI"))
            self._set_busy(False)


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

    return json.loads(raw)


def _fetch_models(bearer: str) -> List[str]:
    parsed = _http_json("GET", MODELS_URL, bearer)
    data = parsed.get("data", [])
    model_ids = [item.get("id", "") for item in data if isinstance(item, dict)]
    return [m for m in model_ids if m]


def _call_openai(bearer: str, model: str, prompt: str) -> str:
    parsed = _http_json("POST", RESPONSES_URL, bearer, body={"model": model, "input": prompt})
    if parsed.get("output_text"):
        return parsed["output_text"]

    chunks = []
    for item in parsed.get("output", []):
        for content in item.get("content", []):
            if content.get("type") == "output_text":
                chunks.append(content.get("text", ""))
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
    result = {"code": None, "state": None}

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

        def log_message(self, fmt: str, *args) -> None:  # noqa: A003
            return

    host = parsed_redirect.hostname or "127.0.0.1"
    port = parsed_redirect.port or 80
    server = HTTPServer((host, port), Handler)
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
    payload = {
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

    parsed = json.loads(raw)
    token = parsed.get("access_token")
    if not token:
        raise RuntimeError(f"OAuth token response has no access_token: {parsed}")
    return token


if __name__ == "__main__":
    root = tk.Tk()
    app = CodexWindowsApp(root)
    root.mainloop()

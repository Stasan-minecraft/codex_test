"""Core domain model for the binary-only social network."""

from __future__ import annotations

from dataclasses import dataclass, field, asdict
from datetime import datetime
from pathlib import Path
from typing import Dict, Iterable, List
import json


ALLOWED_USER_TYPES = {"pc", "ai"}


def _ensure_binary_text(text: str) -> str:
    """Validate that *text* contains only binary digits and return it.

    Args:
        text: Message text that should consist solely of ``0`` and ``1``.

    Raises:
        ValueError: If the text is empty or contains characters other than
            ``0`` or ``1``.
    """

    if not text:
        raise ValueError("Binary messages must contain at least one character.")

    invalid = {ch for ch in text if ch not in {"0", "1"}}
    if invalid:
        raise ValueError(
            "Binary messages may only contain 0 and 1; invalid characters: "
            + ", ".join(sorted(invalid))
        )
    return text


@dataclass(frozen=True)
class User:
    """Represents a registered account."""

    username: str
    kind: str

    def __post_init__(self) -> None:  # pragma: no cover - handled in constructor
        normalized = self.kind.lower()
        if normalized not in ALLOWED_USER_TYPES:
            raise ValueError(
                f"Unknown user kind '{self.kind}'. Expected one of: {sorted(ALLOWED_USER_TYPES)}"
            )
        object.__setattr__(self, "kind", normalized)


@dataclass(frozen=True)
class BinaryMessage:
    """A message sent between users that only contains binary digits."""

    sender: str
    recipient: str
    content: str = field(metadata={"description": "Message content composed of 0 and 1."})
    timestamp: datetime = field(default_factory=datetime.utcnow)

    def __post_init__(self) -> None:
        _ensure_binary_text(self.content)


class BinarySocialNetwork:
    """Manage binary-only conversations between PC and AI participants."""

    def __init__(self, storage_path: Path | str | None = None) -> None:
        self._storage_path = Path(storage_path) if storage_path else None
        self._users: Dict[str, User] = {}
        self._messages: List[BinaryMessage] = []
        if self._storage_path:
            self._load()

    # ------------------------------------------------------------------
    # Persistence helpers
    def _load(self) -> None:
        if not self._storage_path or not self._storage_path.exists():
            return
        with self._storage_path.open("r", encoding="utf-8") as fh:
            payload = json.load(fh)
        self._users = {
            data["username"]: User(**data)
            for data in payload.get("users", [])
        }
        self._messages = [
            BinaryMessage(
                sender=data["sender"],
                recipient=data["recipient"],
                content=data["content"],
                timestamp=datetime.fromisoformat(data["timestamp"]),
            )
            for data in payload.get("messages", [])
        ]

    def _persist(self) -> None:
        if not self._storage_path:
            return
        payload = {
            "users": [asdict(user) for user in self._users.values()],
            "messages": [
                {
                    **asdict(message),
                    "timestamp": message.timestamp.isoformat(),
                }
                for message in self._messages
            ],
        }
        self._storage_path.parent.mkdir(parents=True, exist_ok=True)
        with self._storage_path.open("w", encoding="utf-8") as fh:
            json.dump(payload, fh, indent=2)

    # ------------------------------------------------------------------
    # Public API
    def add_user(self, username: str, kind: str) -> User:
        """Register a new user in the social network."""
        if username in self._users:
            raise ValueError(f"User '{username}' already exists")
        user = User(username=username, kind=kind)
        self._users[username] = user
        self._persist()
        return user

    def list_users(self) -> List[User]:
        """Return a list of all registered users."""
        return list(self._users.values())

    def send_message(self, sender: str, recipient: str, content: str) -> BinaryMessage:
        """Send a message from *sender* to *recipient* containing binary text."""
        if sender not in self._users:
            raise KeyError(f"Unknown sender '{sender}'")
        if recipient not in self._users:
            raise KeyError(f"Unknown recipient '{recipient}'")
        message = BinaryMessage(sender=sender, recipient=recipient, content=content)
        self._messages.append(message)
        self._persist()
        return message

    def get_messages_for_user(self, username: str) -> List[BinaryMessage]:
        """Return every message that involves *username* either as sender or recipient."""
        if username not in self._users:
            raise KeyError(f"Unknown user '{username}'")
        return [
            message
            for message in self._messages
            if message.sender == username or message.recipient == username
        ]

    def get_conversation(self, user_a: str, user_b: str) -> List[BinaryMessage]:
        """Return messages exchanged directly between *user_a* and *user_b*."""
        if user_a not in self._users:
            raise KeyError(f"Unknown user '{user_a}'")
        if user_b not in self._users:
            raise KeyError(f"Unknown user '{user_b}'")
        return [
            message
            for message in self._messages
            if {message.sender, message.recipient} == {user_a, user_b}
        ]

    # ------------------------------------------------------------------
    # Utility helpers
    def export_messages(self) -> Iterable[BinaryMessage]:
        """Iterate over messages in chronological order."""
        return iter(sorted(self._messages, key=lambda msg: msg.timestamp))

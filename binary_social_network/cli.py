"""Command line interface for the binary-only social network."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Iterable

from .network import BinarySocialNetwork, BinaryMessage


def _format_message(message: BinaryMessage) -> str:
    timestamp = message.timestamp.isoformat(timespec="seconds")
    return f"[{timestamp}] {message.sender} -> {message.recipient}: {message.content}"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="binary-social",
        description="Interact with a binary-only social network",
    )
    parser.add_argument(
        "--storage",
        type=Path,
        default=Path("network_state.json"),
        help="Path to the JSON file that stores network data",
    )

    subparsers = parser.add_subparsers(dest="command", required=True)

    register = subparsers.add_parser("register", help="Register a new user")
    register.add_argument("username", help="Unique username for the new account")
    register.add_argument(
        "kind",
        choices=("pc", "ai"),
        help="Whether the account represents a PC or AI participant",
    )

    message = subparsers.add_parser("message", help="Send a binary message")
    message.add_argument("sender", help="Username of the sender")
    message.add_argument("recipient", help="Username of the recipient")
    message.add_argument(
        "content",
        help="Message body containing only 0 and 1",
    )

    inbox = subparsers.add_parser(
        "inbox",
        help="Show every message that involves a user",
    )
    inbox.add_argument("username", help="User whose messages should be displayed")

    conversation = subparsers.add_parser(
        "conversation",
        help="Show the conversation between two users",
    )
    conversation.add_argument("user_a")
    conversation.add_argument("user_b")

    subparsers.add_parser("users", help="List all registered users")

    return parser


def _print_messages(messages: Iterable[BinaryMessage]) -> None:
    for message in messages:
        print(_format_message(message))


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    network = BinarySocialNetwork(storage_path=args.storage)

    try:
        if args.command == "register":
            network.add_user(args.username, args.kind)
            print(f"User '{args.username}' registered as {args.kind}.")
        elif args.command == "message":
            message = network.send_message(args.sender, args.recipient, args.content)
            print("Message sent:")
            print(_format_message(message))
        elif args.command == "inbox":
            _print_messages(network.get_messages_for_user(args.username))
        elif args.command == "conversation":
            _print_messages(network.get_conversation(args.user_a, args.user_b))
        elif args.command == "users":
            for user in network.list_users():
                print(f"{user.username} ({user.kind})")
        else:  # pragma: no cover - argparse enforces valid commands
            parser.error(f"Unknown command: {args.command}")
    except (ValueError, KeyError) as exc:
        parser.error(str(exc))
    return 0


if __name__ == "__main__":  # pragma: no cover - CLI entry point
    sys.exit(main())

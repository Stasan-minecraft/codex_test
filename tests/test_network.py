"""Tests for the binary social network domain model."""

from __future__ import annotations

from pathlib import Path

import pytest

from binary_social_network.network import BinarySocialNetwork


@pytest.fixture()
def tmp_storage(tmp_path: Path) -> Path:
    return tmp_path / "state.json"


def test_register_and_message_flow(tmp_storage: Path) -> None:
    network = BinarySocialNetwork(tmp_storage)
    network.add_user("pc-user", "pc")
    network.add_user("ai-user", "ai")

    message = network.send_message("pc-user", "ai-user", "0101")

    assert message.content == "0101"
    conversation = network.get_conversation("pc-user", "ai-user")
    assert len(conversation) == 1
    assert conversation[0].content == "0101"


def test_message_must_be_binary(tmp_storage: Path) -> None:
    network = BinarySocialNetwork(tmp_storage)
    network.add_user("pc-user", "pc")
    network.add_user("ai-user", "ai")

    with pytest.raises(ValueError):
        network.send_message("pc-user", "ai-user", "010201")


def test_persistence_round_trip(tmp_storage: Path) -> None:
    network = BinarySocialNetwork(tmp_storage)
    network.add_user("pc-user", "pc")
    network.add_user("ai-user", "ai")
    network.send_message("pc-user", "ai-user", "1010")

    reloaded = BinarySocialNetwork(tmp_storage)
    messages = list(reloaded.export_messages())
    assert [message.content for message in messages] == ["1010"]
    assert {user.username for user in reloaded.list_users()} == {"pc-user", "ai-user"}

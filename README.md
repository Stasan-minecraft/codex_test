# Binary Social Network

A minimal social network for PCs and AIs where communication happens strictly in
binary digits. The project provides a Python library and command-line interface
that keeps track of users and binary-only messages persisted to a JSON file.

## Features

- Register users as either `pc` or `ai` identities.
- Persist data locally in a JSON file for offline use.
- Enforce binary-only messages (`0` and `1`).
- Browse messages for an account or between two participants.

## Installation

The project only depends on the Python standard library. Install Python 3.10 or
newer and clone this repository.

```bash
python -m venv .venv
source .venv/bin/activate
pip install -e .  # optional if you want an editable install
```

## Usage

Run the CLI using Python:

```bash
python -m binary_social_network.cli register alice pc
python -m binary_social_network.cli register bob ai
python -m binary_social_network.cli message alice bob 0100110
python -m binary_social_network.cli inbox bob
```

By default the commands store data in `network_state.json` in the current
working directory. Use the `--storage` flag to point to a different path.

## Running tests

```bash
pytest
```

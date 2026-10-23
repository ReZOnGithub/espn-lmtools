"""Runtime configuration for the dashboard server."""

from __future__ import annotations

from pathlib import Path

DEFAULT_LEAGUE = ""
DEFAULT_GAME = "fba"
DEFAULT_SEASON = "2027"

CONFIG = {
    "league": DEFAULT_LEAGUE,
    "game": DEFAULT_GAME,
    "season": DEFAULT_SEASON,
    "account": None,
    "cookies_dir": Path("cookies"),
}


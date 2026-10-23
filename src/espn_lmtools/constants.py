"""Shared constants for ESPN Fantasy endpoints and display metadata."""

from __future__ import annotations

SPORT = {"fba": "basketball", "ffl": "football", "flb": "baseball", "fhl": "hockey"}

SITE_SPORT = {
    "fba": "basketball/nba",
    "ffl": "football/nfl",
    "flb": "baseball/mlb",
    "fhl": "hockey/nhl",
}

HEADSHOT_SPORT = {"fba": "nba", "ffl": "nfl", "flb": "mlb", "fhl": "nhl"}

POSITIONS = {
    "fba": {1: "PG", 2: "SG", 3: "SF", 4: "PF", 5: "C"},
    "ffl": {1: "QB", 2: "RB", 3: "WR", 4: "TE", 5: "K", 16: "D/ST"},
    "flb": {
        1: "SP",
        2: "RP",
        3: "C",
        4: "1B",
        5: "2B",
        6: "3B",
        7: "SS",
        8: "OF",
        9: "DH",
        10: "UTIL",
        11: "P",
    },
    "fhl": {1: "C", 2: "LW", 3: "RW", 4: "D", 5: "G", 6: "F", 7: "UTIL"},
}

LINEUP_SLOTS = {
    "fba": {
        0: "PG",
        1: "SG",
        2: "SF",
        3: "PF",
        4: "C",
        5: "G",
        6: "F",
        7: "G/F",
        8: "F/C",
        9: "UTIL",
        10: "UTIL",
        11: "BE",
        12: "BN",
        13: "IR",
        14: "IR+",
    },
    "ffl": {
        0: "QB",
        1: "RB",
        2: "RB/WR",
        3: "WR",
        4: "WR/TE",
        5: "TE",
        6: "FLEX",
        7: "D/ST",
        8: "K",
        9: "P",
        10: "BE",
        11: "BE",
        12: "IR",
        13: "IR",
        14: "IR",
    },
    "flb": {
        0: "C",
        1: "1B",
        2: "2B",
        3: "3B",
        4: "SS",
        5: "OF",
        6: "OF",
        7: "OF",
        8: "UTIL",
        9: "UTIL",
        10: "P",
        11: "P",
        12: "P",
        13: "BE",
        14: "BE",
        15: "BE",
        16: "BE",
        17: "BE",
        18: "IR",
        19: "IR",
    },
    "fhl": {
        0: "C",
        1: "LW",
        2: "RW",
        3: "D",
        4: "D",
        5: "G",
        6: "F",
        7: "F",
        8: "UTIL",
        9: "UTIL",
        10: "BE",
        11: "BE",
        12: "BE",
        13: "IR",
        14: "IR",
        15: "IR+",
    },
}

BASKETBALL_STATS = [
    ("PTS", 0, 1.0, False),
    ("REB", 6, 1.0, False),
    ("AST", 3, 1.0, False),
    ("STL", 2, 1.0, False),
    ("BLK", 1, 1.0, False),
    ("3PM", 17, 1.0, False),
    ("FG%", 19, 100.0, True),
    ("FT%", 20, 100.0, True),
    ("TO", 11, 1.0, False),
    ("MIN", 28, 1.0, False),
]

ACCENTS = [
    {"a": "#7c5cff", "b": "#22d3ee"},
    {"a": "#f59e0b", "b": "#f43f5e"},
    {"a": "#10b981", "b": "#84cc16"},
    {"a": "#0ea5e9", "b": "#a855f7"},
    {"a": "#f43f5e", "b": "#fb923c"},
    {"a": "#14b8a6", "b": "#6366f1"},
]

ZERO_SWID = "0" * 32

ACTIVE_INJURIES = {"ACTIVE", "NORMAL", None, ""}

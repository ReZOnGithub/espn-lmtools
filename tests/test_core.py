from __future__ import annotations

import json

import pytest

from espn_lmtools.cli.trade_probe import (
    build_body,
    coerce,
    parse_raw_request,
    recursive_swap,
)
from espn_lmtools.cookies import load_cookies, parse_netscape, write_netscape
from espn_lmtools.dashboard.state import norm_swid, season_label
from espn_lmtools.transactions import build_transaction

COOKIE_FILE = """# Netscape HTTP Cookie File
#HttpOnly_.espn.com\tTRUE\t/\tTRUE\t0\tSWID\t{AAAA}
.espn.com\tTRUE\t/\tTRUE\t0\tespn_s2\tsecret
"""


def test_parse_and_load_cookies(tmp_path):
    path = tmp_path / "account.txt"
    path.write_text(COOKIE_FILE, encoding="utf-8")
    jar = parse_netscape(path)
    assert jar == {"SWID": "{AAAA}", "espn_s2": "secret"}
    assert load_cookies(path) == {"SWID": "{AAAA}", "espn_s2": "secret"}


def test_load_cookies_requires_both(tmp_path):
    path = tmp_path / "account.txt"
    path.write_text(COOKIE_FILE.replace("espn_s2", "other"), encoding="utf-8")
    with pytest.raises(ValueError):
        load_cookies(path)


def test_write_netscape_round_trip(tmp_path):
    path = tmp_path / "out.txt"
    write_netscape(
        [
            {
                "domain": ".espn.com",
                "httpOnly": True,
                "path": "/",
                "secure": True,
                "expires": 0,
                "name": "SWID",
                "value": "{AAAA}",
            }
        ],
        path,
    )
    assert parse_netscape(path)["SWID"] == "{AAAA}"


def test_parse_raw_request():
    raw = (
        b"POST /apis/v3/games/fba/seasons/2027/segments/0/leagues/1/transactions/ HTTP/1.1\r\n"
        b"Host: lm-api-writes.fantasy.espn.com\r\n"
        b"Content-Type: application/json\r\n"
        b"\r\n"
        b'{"type":"TRADE_ACCEPT"}'
    )
    method, target, headers, body = parse_raw_request(raw)
    assert method == "POST"
    assert target.endswith("/transactions/")
    assert dict(headers)["Host"] == "lm-api-writes.fantasy.espn.com"
    assert json.loads(body) == {"type": "TRADE_ACCEPT"}


def test_build_body_set_and_swap():
    raw = json.dumps({"teamId": 3, "nested": {"teamId": 3}}).encode()
    body, changed = build_body(raw, "application/json", ["json.teamId=7"], ["teamId:3:7"])
    payload = json.loads(body)
    assert changed == 2
    assert payload["teamId"] == 7
    assert payload["nested"]["teamId"] == 7


def test_recursive_swap_and_coerce():
    payload = {"a": [{"teamId": 3}, {"teamId": 4}]}
    assert recursive_swap(payload, "teamId", 3, 7) == 1
    assert payload["a"][0]["teamId"] == 7
    assert coerce("true") is True
    assert coerce("null") is None
    assert coerce("12") == 12


def test_build_transaction_defaults():
    body = build_transaction("TRADE_ACCEPT", 2, "{SWID}", 5, related="trade")
    assert body["type"] == "TRADE_ACCEPT"
    assert body["memberId"] == "{SWID}"
    assert body["scoringPeriodId"] == 5
    assert body["relatedTransactionId"] == "trade"
    assert body["isLeagueManager"] is False
    assert "items" not in body


def test_norm_swid_and_season_label():
    assert norm_swid("{aa-bb}") == "AABB"
    assert season_label("2027") == "2026-27"
    assert season_label("bad") == "bad"

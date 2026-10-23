"""Accept one pending trade offer, matched by player name or trade id."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import requests

from espn_lmtools.client import League
from espn_lmtools.constants import SITE_SPORT
from espn_lmtools.transactions import build_transaction, post_transaction

_name_cache: dict[int, str] = {}
_roster_names: dict[int, str] = {}


def prime_names(league):
    for team in league.get("teams", []):
        for entry in (team.get("roster") or {}).get("entries", []):
            player = (entry.get("playerPoolEntry") or {}).get("player") or {}
            if player.get("id"):
                _roster_names[player["id"]] = player.get("fullName")


def player_name(game: str, player_id: int) -> str:
    if player_id in _roster_names:
        return _roster_names[player_id]
    if player_id in _name_cache:
        return _name_cache[player_id]
    name = f"player {player_id}"
    try:
        url = (
            "https://site.api.espn.com/apis/site/v2/sports/"
            f"{SITE_SPORT.get(game, 'basketball/nba')}/athletes/{player_id}"
        )
        response = requests.get(url, headers={"User-Agent": "Mozilla/5.0"}, timeout=15)
        if response.status_code == 200:
            data = response.json()
            athlete = data.get("athlete") or data
            name = athlete.get("displayName") or athlete.get("fullName") or name
    except requests.RequestException:
        pass
    _name_cache[player_id] = name
    return name


def collect_transactions(league):
    out = []
    for key in ("pendingTransactions", "transactions", "tradeProposals"):
        value = league.get(key)
        if isinstance(value, list):
            out.extend(item for item in value if isinstance(item, dict))
    return out


def main(argv=None):
    parser = argparse.ArgumentParser(description="Accept one specific pending trade offer.")
    parser.add_argument("--league", required=True)
    parser.add_argument("--team", type=int, required=True, help="your team id in that league")
    parser.add_argument("--account", required=True, help="cookie file name in --cookies-dir")
    parser.add_argument("--match", help="substring to match against player names")
    parser.add_argument("--game", default="fba")
    parser.add_argument("--season", default="2027")
    parser.add_argument("--cookies-dir", type=Path, default=Path("cookies"))
    parser.add_argument("--trade-id", help="accept exactly this trade id, skipping the match")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)

    if not args.trade_id and not args.match:
        parser.error("pass --match or --trade-id")

    league_client = League(
        args.league, args.account, args.game, args.season, args.cookies_dir
    )
    response = requests.get(
        league_client.read + "?view=mTeam&view=mTransactions2&view=mRoster",
        cookies=league_client.cookies,
        headers=league_client.h,
        timeout=30,
    )
    if response.status_code != 200:
        print(f"cannot read league: {response.status_code} {response.text[:200]}")
        return 1
    league = response.json()
    prime_names(league)

    team = next(
        (item for item in league.get("teams", []) if item.get("id") == args.team), None
    )
    if not team:
        print(f"team {args.team} not in league {args.league}")
        return 1

    transactions = [
        item for item in collect_transactions(league) if str(item.get("type", "")).startswith("TRADE")
    ]
    incoming = []
    for transaction in transactions:
        items = transaction.get("items") or []
        if not items:
            continue
        if any(item.get("toTeamId") == args.team for item in items):
            direction = "outgoing" if transaction.get("teamId") == args.team else "incoming"
            incoming.append((transaction, direction))

    if not incoming:
        print(f"no pending trade offers addressed to team {args.team}")
        print(f"  (scanned {len(transactions)} trade-ish transactions in the league payload)")
        return 1

    scored = []
    for transaction, direction in incoming:
        involved, give, see = [], [], []
        for item in transaction.get("items") or []:
            player_id = item.get("playerId")
            if not player_id or player_id < 0:
                continue
            name = player_name(args.game, player_id)
            involved.append(name)
            (give if item.get("fromTeamId") == args.team else see).append(name)
        haystack = " | ".join(involved).lower()
        scored.append((transaction, give, see, haystack, direction))

    print(f"pending offers for team {args.team}: {len(scored)}")
    for transaction, give, see, _, direction in scored:
        print(f"  {transaction.get('id')}  [{direction}]")
        print(f"    you send : {', '.join(give) or '-'}")
        print(f"    you get  : {', '.join(see) or '-'}")
        print(f"    expires  : {transaction.get('expirationDate')}")

    if args.trade_id:
        chosen = next((item for item in scored if item[0].get("id") == args.trade_id), None)
    else:
        needle = args.match.lower()
        pool = [item for item in scored if item[4] == "incoming"]
        hits = [item for item in pool if needle in item[3]]
        chosen = hits[0] if hits else None
        if len(hits) > 1:
            print(f"\n{len(hits)} offers match {args.match!r}; refusing to guess. Pass --trade-id.")
            return 1

    if not chosen:
        print(f"\nno offer matched {args.match!r}; nothing accepted")
        return 1

    transaction, give, see, _, direction = chosen
    if direction != "incoming":
        print("that offer is outgoing (you proposed it); the other team accepts it, not you")
        return 1
    print(f"\nselected {transaction.get('id')}: send {', '.join(give) or '-'} / get {', '.join(see) or '-'}")

    if args.dry_run:
        print("dry run: not accepted")
        return 0

    body = build_transaction(
        "TRADE_ACCEPT",
        args.team,
        league_client.swid,
        league.get("status", {}).get("latestScoringPeriod") or 1,
        related=transaction.get("id"),
        comment="",
        lm=False,
    )
    response = post_transaction(league_client, body)
    print(f"accept -> {response.status_code}")
    print(response.text[:400])
    return 0 if response.status_code < 300 else 1


if __name__ == "__main__":
    sys.exit(main())

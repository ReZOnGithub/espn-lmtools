"""Consolidated ESPN Fantasy league toolkit."""

from __future__ import annotations

import argparse
import datetime
import sys
from pathlib import Path

import requests

from espn_lmtools.client import League
from espn_lmtools.transactions import build_transaction, post_transaction

from .common import need_yes, with_lm


def ts(ms):
    if not ms:
        return "-"
    return datetime.datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:%M:%S")


def main(argv=None):
    parser = argparse.ArgumentParser(description="ESPN fantasy league toolkit")
    parser.add_argument("--league", required=True)
    parser.add_argument("--account", required=True)
    parser.add_argument("--game", default="fba")
    parser.add_argument("--season", default="2027")
    parser.add_argument("--cookies-dir", type=Path, default=Path("cookies"))
    parser.add_argument("--yes", action="store_true")
    sub = parser.add_subparsers(dest="cmd", required=True)

    sub.add_parser("info")
    sub.add_parser("members")
    sub.add_parser("teams")
    activity = sub.add_parser("activity")
    activity.add_argument("--limit", type=int, default=15)

    sub.add_parser("escalate")
    sub.add_parser("deescalate")

    rename = sub.add_parser("rename")
    rename.add_argument("--name", required=True)

    accept = sub.add_parser("accept-trade")
    accept.add_argument("--trade-id", required=True)
    accept.add_argument("--team", type=int)
    accept.add_argument("--member", help="member SWID acting for --team; defaults to my own")
    accept.add_argument("--period", type=int, help="scoring period; defaults to the league's latest")

    drop = sub.add_parser("drop")
    drop.add_argument("--team", type=int, required=True)
    drop.add_argument("--player", type=int, required=True)

    notify = sub.add_parser("notify")
    notify.add_argument("--member", required=True, help="member SWID")
    notify.add_argument("--type", default="TEAM_TRADE_OFFER")
    notify.add_argument("--on", action="store_true")
    notify.add_argument("--off", action="store_true")

    args = parser.parse_args(argv)
    league = League(args.league, args.account, args.game, args.season, args.cookies_dir)

    if args.cmd == "info":
        data = league.get()
        settings = data.get("settings", {})
        print(f"league   {settings.get('name')}  ({args.league})")
        print(f"season   {args.game} {args.season}   teams {settings.get('size')}")
        acquisition = settings.get("acquisitionSettings", {})
        print(
            f"waivers  {acquisition.get('acquisitionType')}  process "
            f"{acquisition.get('waiverProcessDays')} {acquisition.get('waiverProcessHour')}:00"
        )
        print(f"account  {league.swid}  isLeagueManager={league.is_lm()}")
        order = sorted((team.get("waiverRank"), team.get("id")) for team in data.get("teams", []))
        print(f"waiver order {order}")
        return 0

    if args.cmd == "members":
        for member in league.members():
            flag = "LM" if member.get("isLeagueManager") else "  "
            real = " ".join(
                value for value in (member.get("firstName"), member.get("lastName")) if value
            )
            print(f"{flag} {member.get('id')}  {member.get('displayName')!r:<28} {real}")
        return 0

    if args.cmd == "teams":
        for team_id, info in sorted(league.rosters().items()):
            team = info["team"]
            print(f"team {team_id:<3} {len(info['ids']):>2} players  owners={team.get('owners')}")
        return 0

    if args.cmd == "activity":
        response = requests.get(
            f"{league.read}/communication/?view=kona_league_communication",
            cookies=league.cookies,
            headers=league.h,
            timeout=30,
        )
        messages = [
            message
            for topic in response.json().get("topics", [])
            for message in (topic.get("messages") or [])
        ]
        messages.sort(key=lambda message: message.get("date") or 0)
        for message in messages[-args.limit :]:
            who = message.get("to") or message.get("author")
            print(f"{ts(message.get('date'))}  type={message.get('messageTypeId')}  {who}")
        transactions = requests.get(
            f"{league.read}?view=mTransactions2",
            cookies=league.cookies,
            headers=league.h,
            timeout=30,
        ).json()
        for transaction in (transactions.get("transactions") or [])[-args.limit :]:
            items = [
                (item.get("playerId"), item.get("type")) for item in (transaction.get("items") or [])
            ]
            print(
                f"{ts(transaction.get('proposedDate'))}  {transaction.get('type')}  "
                f"team {transaction.get('teamId')}  {items}"
            )
        return 0

    if args.cmd == "escalate":
        if not need_yes(args, "set my isLeagueManager=true"):
            return 2
        response, _ = league.set_lm(True)
        print(f"PUT -> {response.status_code} | stored isLeagueManager={league.is_lm()}")
        return 0 if league.is_lm() else 1

    if args.cmd == "deescalate":
        if not need_yes(args, "set my isLeagueManager=false"):
            return 2
        base = league.me()
        base["isLeagueManager"] = False
        response, _ = league.set_lm(False, base)
        print(f"PUT -> {response.status_code} | stored isLeagueManager={league.is_lm()}")
        return 0 if not league.is_lm() else 1

    if args.cmd == "rename":
        if not need_yes(args, f"set my displayName to {args.name!r}"):
            return 2
        response, _ = league.put_member(league.me(), "displayName", args.name)
        print(f"PUT -> {response.status_code} | stored={league.me().get('displayName')!r}")
        return 0

    if args.cmd == "accept-trade":
        if args.team is None:
            sys.exit("--team is required (the teamId to reference in the accept)")
        if not need_yes(args, f"accept trade {args.trade_id} as team {args.team}"):
            return 2

        period = args.period or (
            league.get(("mSettings",)).get("status", {}).get("latestScoringPeriod") or 1
        )
        team = next(
            (item for item in league.get(("mTeam",)).get("teams", []) if item.get("id") == args.team),
            None,
        )
        if not team:
            sys.exit(f"team {args.team} not in league {args.league}")
        member = args.member or (team.get("owners") or [league.swid])[0]
        print(f"  scoringPeriodId={period}  teamId={args.team}  memberId={member}")

        def go():
            body = build_transaction(
                "TRADE_ACCEPT",
                args.team,
                member,
                period,
                related=args.trade_id,
                comment="",
                lm=False,
            )
            response = post_transaction(league, body)
            print(f"  TRADE_ACCEPT -> {response.status_code} {response.text[:200]}")
            return 0 if response.status_code < 300 else 1

        return with_lm(league, True, go)

    if args.cmd == "drop":
        player_id = args.player
        if not need_yes(args, f"drop player {player_id} from team {args.team}"):
            return 2

        def go():
            items = [
                {"playerId": player_id, "fromTeamId": args.team, "toTeamId": 0, "type": "DROP"}
            ]
            body = build_transaction(
                "ROSTER", args.team, league.swid, 1, items=items, lm=True, comment="espn_lmtools"
            )
            response = post_transaction(league, body)
            print(f"  ROSTER/DROP -> {response.status_code} {response.text[:200]}")
            return 0 if response.status_code < 300 else 1

        return with_lm(league, True, go)

    if args.cmd == "notify":
        enable = bool(args.on)
        if not args.on and not args.off:
            sys.exit("pass --on or --off")
        target = next((member for member in league.members() if member.get("id") == args.member), None)
        if not target:
            sys.exit("member not found")
        setting = next(
            (
                item
                for item in target.get("notificationSettings") or []
                if item.get("type") == args.type
            ),
            None,
        )
        if not setting:
            sys.exit(f"{args.type} setting not found")
        if not need_yes(args, f"set {args.type}={enable} for {target.get('displayName')}"):
            return 2

        def go():
            response, _ = league.set_notification(args.member, setting["id"], enable)
            print(f"  POST notificationSettings -> {response.status_code}")
            return 0 if response.status_code < 300 else 1

        return with_lm(league, True, go)

    return 1


if __name__ == "__main__":
    sys.exit(main())



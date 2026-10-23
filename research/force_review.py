"""Hold revisionHours=0 for an hour and poll whether the trade processor moves players."""

from __future__ import annotations

import argparse
import copy
import datetime
import json
import time
from pathlib import Path

import requests

from espn_lmtools.client import League

TRADE_ID = None
HOLD_MINUTES = 60
POLL_SECONDS = 20
LOG = Path("force_review.log")
EVIDENCE = Path("force_review_evidence.json")
BRUNSON, PJ, DEROZAN, RANDLE = 3934672, 4278078, 3978, 3064514


def log(message):
    line = f"{datetime.datetime.now().isoformat(timespec='seconds')}  {message}"
    print(line, flush=True)
    with LOG.open("a", encoding="utf-8") as handle:
        handle.write(line + "\n")


def settings(league):
    return league.get(("mSettings",)).get("settings", {}).get("tradeSettings", {})


def set_revision_hours(league, hours, label):
    original = copy.deepcopy(league.me())
    start = bool(original.get("isLeagueManager"))
    raised = False
    try:
        if not start:
            response, _ = league.set_lm(True, original)
            log(f"{label}: escalate {response.status_code}")
            if response.status_code >= 300 or not league.is_lm():
                raise RuntimeError("escalation failed")
            raised = True
        body = dict(settings(league))
        body["revisionHours"] = hours
        response = requests.post(
            f"{league.write}/settings",
            cookies=league.cookies,
            headers=league.h,
            data=json.dumps({"tradeSettings": body}),
            timeout=30,
        )
        stored = settings(league).get("revisionHours")
        log(f"{label}: POST revisionHours={hours} {response.status_code} stored={stored}")
        if response.status_code >= 300 or stored != hours:
            raise RuntimeError(response.text[:160])
        return dict(body)
    finally:
        if raised:
            response, _ = league.set_lm(start, original)
            log(f"{label}: de-escalate {response.status_code} isLM={league.is_lm()}")


def state(league):
    data = league.get(("mTransactions2", "mRoster", "mTeam", "mSettings"))
    transaction = next(
        (item for item in data.get("transactions", []) if item.get("id") == TRADE_ID), None
    )
    rosters = {
        team["id"]: [
            entry.get("playerId")
            for entry in (team.get("roster") or {}).get("entries", [])
            if entry.get("playerId", -1) > 0
        ]
        for team in data.get("teams", [])
    }
    moved = (
        BRUNSON in rosters.get(4, [])
        and PJ in rosters.get(4, [])
        and DEROZAN in rosters.get(6, [])
        and RANDLE in rosters.get(6, [])
    )
    return {
        "proposal_status": None if transaction is None else transaction.get("status"),
        "is_pending": None if transaction is None else transaction.get("isPending"),
        "moved": moved,
        "revision_hours": data.get("settings", {}).get("tradeSettings", {}).get("revisionHours"),
    }


def main(argv=None):
    global TRADE_ID
    parser = argparse.ArgumentParser(description="Force the trade review window open for an hour.")
    parser.add_argument("--league", required=True)
    parser.add_argument("--account", required=True)
    parser.add_argument("--trade-id", required=True)
    parser.add_argument("--hold-minutes", type=int, default=HOLD_MINUTES)
    parser.add_argument("--poll-seconds", type=int, default=POLL_SECONDS)
    args = parser.parse_args(argv)
    TRADE_ID = args.trade_id

    league = League(args.league, args.account)
    log(f"start name={(league.me() or {}).get('displayName')!r} isLM={league.is_lm()}")
    baseline = settings(league)
    log(f"baseline {json.dumps(baseline)}")
    result = {
        "started": datetime.datetime.now().isoformat(),
        "baseline": baseline,
        "polls": [],
        "executed": False,
        "restored": False,
        "error": None,
    }
    try:
        set_revision_hours(league, 0, "hold")
        deadline = datetime.datetime.now() + datetime.timedelta(minutes=args.hold_minutes)
        while datetime.datetime.now() < deadline:
            current = state(league)
            result["polls"].append(
                {"at": datetime.datetime.now().isoformat(timespec="seconds"), **current}
            )
            log(f"poll status={current['proposal_status']} moved={current['moved']}")
            if current["moved"]:
                result["executed"] = True
                break
            time.sleep(args.poll_seconds)
    except Exception as exc:
        result["error"] = f"{type(exc).__name__}: {exc}"
        log(f"ERROR {result['error']}")
    finally:
        try:
            set_revision_hours(league, baseline.get("revisionHours", 24), "restore")
            result["restored"] = settings(league).get("revisionHours") == baseline.get(
                "revisionHours", 24
            )
            log(
                f"restored={result['restored']} "
                f"revisionHours={settings(league).get('revisionHours')}"
            )
        except Exception as exc:
            result["restored"] = False
            result["error"] = (result["error"] or "") + f" restore: {exc}"
            log(f"RESTORE FAILED {exc}")
    final = state(league)
    final["display_name"] = (league.me() or {}).get("displayName")
    final["is_lm"] = league.is_lm()
    result["final"] = final
    EVIDENCE.write_text(json.dumps(result, indent=2), encoding="utf-8")
    log(f"final {json.dumps(final)}")
    return 0 if result["executed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())



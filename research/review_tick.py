"""Drop revisionHours to 0 around the hourly trade processor, then restore it."""

from __future__ import annotations

import argparse
import copy
import datetime
import json
import sys
import time
from pathlib import Path

import requests

from espn_lmtools.client import League

TRADE_ID = None
DROP_AT = datetime.time(21, 58)
RESTORE_AT = datetime.time(22, 2)
POLL_SECONDS = 10
LOG = Path("review_tick.log")
EVIDENCE = Path("review_tick_evidence.json")


def log(message):
    line = f"{datetime.datetime.now().isoformat(timespec='seconds')}  {message}"
    print(line, flush=True)
    with LOG.open("a", encoding="utf-8") as handle:
        handle.write(line + "\n")


def sleep_until(target):
    while True:
        now = datetime.datetime.now()
        delta = (target - now).total_seconds()
        if delta <= 0:
            return
        time.sleep(min(delta, 30))
        log(f"waiting for {target.strftime('%H:%M:%S')} ({int(delta)}s left)")


def read_settings(league):
    return league.get(("mSettings",)).get("settings", {}).get("tradeSettings", {})


def get_trade(league):
    data = league.get(("mTransactions2", "mTeam"))
    transaction = next(
        (item for item in data.get("transactions", []) if item.get("id") == TRADE_ID), None
    )
    return transaction, data


def set_revision_hours(league, hours, label):
    original = copy.deepcopy(league.me())
    start = bool(original.get("isLeagueManager"))
    raised = False
    try:
        if not start:
            response, _ = league.set_lm(True, original)
            log(f"{label}: escalate -> {response.status_code}")
            if response.status_code >= 300 or not league.is_lm():
                raise RuntimeError("escalation failed")
            raised = True
        current = read_settings(league)
        wanted = dict(current)
        wanted["revisionHours"] = hours
        response = requests.post(
            f"{league.write}/settings",
            cookies=league.cookies,
            headers=league.h,
            data=json.dumps({"tradeSettings": wanted}),
            timeout=30,
        )
        stored = read_settings(league).get("revisionHours")
        log(f"{label}: POST revisionHours={hours} -> {response.status_code}, stored={stored}")
        if response.status_code >= 300 or stored != hours:
            raise RuntimeError(f"settings write failed: {response.text[:160]}")
        return current
    finally:
        if raised:
            response, _ = league.set_lm(start, original)
            log(f"{label}: de-escalate -> {response.status_code}, isLM={league.is_lm()}")


def main(argv=None):
    global TRADE_ID
    parser = argparse.ArgumentParser(description="Toggle revisionHours around the trade processor.")
    parser.add_argument("--league", required=True)
    parser.add_argument("--account", required=True)
    parser.add_argument("--trade-id", required=True)
    parser.add_argument("--drop-at", default=DROP_AT.strftime("%H:%M"))
    parser.add_argument("--restore-at", default=RESTORE_AT.strftime("%H:%M"))
    parser.add_argument("--poll-seconds", type=int, default=POLL_SECONDS)
    args = parser.parse_args(argv)
    TRADE_ID = args.trade_id

    drop_time = datetime.datetime.strptime(args.drop_at, "%H:%M").time()
    restore_time = datetime.datetime.strptime(args.restore_at, "%H:%M").time()

    now = datetime.datetime.now()
    drop_at = now.replace(
        hour=drop_time.hour, minute=drop_time.minute, second=0, microsecond=0
    )
    restore_at = now.replace(
        hour=restore_time.hour, minute=restore_time.minute, second=0, microsecond=0
    )
    if now >= restore_at:
        log(f"refusing to run: {now:%H:%M} is past restore window")
        return 2

    league = League(args.league, args.account)
    log(
        f"start: account={args.account} name={(league.me() or {}).get('displayName')!r} "
        f"isLM={league.is_lm()}"
    )
    transaction, _ = get_trade(league)
    log(f"trade {args.trade_id}: status={transaction.get('status')} pending={transaction.get('isPending')}")

    baseline = read_settings(league)
    log(f"baseline tradeSettings={json.dumps(baseline)}")

    result = {
        "started": now.isoformat(),
        "baseline": baseline,
        "polls": [],
        "executed": False,
        "restored": False,
        "error": None,
    }

    try:
        sleep_until(drop_at)
        set_revision_hours(league, 0, "drop")

        transaction, _ = get_trade(league)
        log(f"after drop: status={transaction.get('status')} pending={transaction.get('isPending')}")

        while datetime.datetime.now() < restore_at:
            transaction, _ = get_trade(league)
            status = transaction.get("status")
            result["polls"].append(
                {
                    "at": datetime.datetime.now().isoformat(timespec="seconds"),
                    "status": status,
                    "pending": transaction.get("isPending"),
                }
            )
            rosters = league.rosters()
            moved = (
                3934672 in rosters[4]["ids"]
                and 4278078 in rosters[4]["ids"]
                and 3978 in rosters[6]["ids"]
                and 3064514 in rosters[6]["ids"]
            )
            log(
                f"poll: status={status} pending={transaction.get('isPending')} "
                f"rosters_moved={moved}"
            )
            if moved:
                result["executed"] = True
                break
            time.sleep(args.poll_seconds)
    except Exception as exc:
        result["error"] = f"{type(exc).__name__}: {exc}"
        log(f"ERROR: {result['error']}")
    finally:
        try:
            set_revision_hours(league, baseline.get("revisionHours", 24), "restore")
            result["restored"] = read_settings(league).get("revisionHours") == baseline.get(
                "revisionHours", 24
            )
            log(
                f"settings restored={result['restored']} "
                f"revisionHours={read_settings(league).get('revisionHours')}"
            )
        except Exception as exc:
            result["restored"] = False
            result["error"] = (result["error"] or "") + f" restore failed: {exc}"
            log(f"RESTORE FAILED: {exc}")

    transaction, _ = get_trade(league)
    rosters = league.rosters()
    result["final"] = {
        "proposal_status": transaction.get("status"),
        "is_pending": transaction.get("isPending"),
        "team4_has_brunson": 3934672 in rosters[4]["ids"],
        "team4_has_pj": 4278078 in rosters[4]["ids"],
        "team6_has_derozan": 3978 in rosters[6]["ids"],
        "team6_has_randle": 3064514 in rosters[6]["ids"],
        "display_name": (league.me() or {}).get("displayName"),
        "is_lm": league.is_lm(),
        "revision_hours": read_settings(league).get("revisionHours"),
    }
    EVIDENCE.write_text(json.dumps(result, indent=2), encoding="utf-8")
    log(f"final: {json.dumps(result['final'])}")
    log(f"evidence: {EVIDENCE.resolve()}")
    return 0 if result["executed"] else 1


if __name__ == "__main__":
    sys.exit(main())



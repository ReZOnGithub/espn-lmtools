"""Shared CLI helpers."""

from __future__ import annotations

import copy


def need_yes(args, what: str) -> bool:
    if args.yes:
        return True
    print(f"about to: {what}")
    print("re-run with --yes to execute")
    return False


def with_lm(league, enabled: bool, action):
    original = copy.deepcopy(league.me())
    start = bool(original.get("isLeagueManager"))
    raised = False
    try:
        if enabled and not start:
            response, _ = league.set_lm(True, original)
            print(f"  escalate -> {response.status_code}", end="")
            if response.status_code >= 300:
                print(f" {response.text[:120]}")
                return 1
            if not league.is_lm():
                print(" (flag did not stick)")
                return 1
            raised = True
            print(" verified")
        return action()
    finally:
        if raised:
            response, _ = league.set_lm(start, original)
            restored = league.is_lm() == start
            print(
                f"  de-escalate -> {response.status_code} "
                f"{'restored' if restored else 'RESTORE FAILED'}"
            )
            if not restored:
                print(f"  fix manually: PUT members/{league.swid} isLeagueManager={start}")

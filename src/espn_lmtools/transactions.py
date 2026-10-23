"""Transaction payload builders for the ESPN write API."""

from __future__ import annotations

import json

import requests


def build_transaction(
    kind,
    team_id,
    member_id,
    period,
    *,
    items=None,
    related=None,
    execution=None,
    comment="",
    lm=False,
    acting_as_owner=True,
    **extra,
):
    body = {
        "type": kind,
        "teamId": team_id,
        "memberId": member_id,
        "scoringPeriodId": period,
        "isActingAsTeamOwner": acting_as_owner,
        "isLeagueManager": bool(lm),
        "skipTransactionCounters": False,
        "bidAmount": 0,
        "comment": comment or "",
        "executionType": execution,
        "relatedTransactionId": related,
    }
    if items is not None:
        body["items"] = items
    body.update(extra)
    return body


def post_transaction(league, body, timeout: int = 30):
    return requests.post(
        f"{league.write}/transactions/",
        cookies=league.cookies,
        headers=league.h,
        data=json.dumps(body),
        timeout=timeout,
    )

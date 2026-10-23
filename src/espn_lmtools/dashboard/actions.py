"""Dashboard write actions."""

from __future__ import annotations

import copy

from espn_lmtools.client import League
from espn_lmtools.constants import ZERO_SWID
from espn_lmtools.transactions import build_transaction, post_transaction

from .config import CONFIG
from .state import _trade_families, invalidate_cache, norm_swid


class ActionError(Exception):
    def __init__(self, message, status=400, detail=None):
        super().__init__(message)
        self.status = status
        self.detail = detail


def _league(account, league_id, game, season):
    try:
        return League(league_id, account, game, season, CONFIG["cookies_dir"])
    except FileNotFoundError:
        raise ActionError(
            f"No cookie file for account '{account}'. Run espn-cookie-harvest first.",
            status=400,
        ) from None
    except ValueError as exc:
        raise ActionError(str(exc), status=400) from exc


def _rosters(data):
    out = {}
    for team in data.get("teams") or []:
        out[team["id"]] = {
            entry.get("playerId")
            for entry in (team.get("roster") or {}).get("entries", [])
            if entry.get("playerId")
        }
    return out


def _team(data, team_id):
    return next((team for team in data.get("teams") or [] if team.get("id") == team_id), None)


def _entry(data, team_id, player_id):
    for team in data.get("teams") or []:
        if team.get("id") != team_id:
            continue
        for entry in (team.get("roster") or {}).get("entries", []):
            if entry.get("playerId") == player_id:
                return entry
    return None


def _period(data):
    return (data.get("status") or {}).get("latestScoringPeriod") or 1


def _owner_of(team, fallback_swid):
    owners = [owner for owner in (team or {}).get("owners") or [] if norm_swid(owner) != ZERO_SWID]
    return owners[0] if owners else fallback_swid


def _post_transaction(league, body):
    response = post_transaction(league, body)
    try:
        payload = response.json()
    except ValueError:
        payload = None
    if response.status_code >= 300:
        raise ActionError(
            f"ESPN rejected the transaction ({response.status_code}): {response.text[:400]}",
            status=502,
            detail=payload,
        )
    return payload if payload is not None else {"raw": response.text}


def with_lm(league, enabled, action):
    original = copy.deepcopy(league.me())
    if not original:
        raise ActionError("Could not read your member record.", status=502)
    start = bool(original.get("isLeagueManager"))
    raised = False
    try:
        if enabled and not start:
            response, _ = league.set_lm(True, original)
            if response.status_code >= 300:
                raise ActionError(
                    f"LM escalation refused ({response.status_code}): {response.text[:240]}",
                    status=502,
                )
            if not league.is_lm():
                raise ActionError("LM escalation did not persist; action aborted.", status=502)
            raised = True
        return action()
    finally:
        if raised:
            restore_response = None
            try:
                restore_response, _ = league.set_lm(start, original)
                restored = league.is_lm() == start
            except Exception:
                restored = False
            if not restored:
                CONFIG["_restore_warning"] = (
                    "LM flag restore FAILED. Fix manually: PUT members/"
                    f"{league.swid} with isLeagueManager={start}. "
                    f"HTTP {getattr(restore_response, 'status_code', '?')}"
                )
            else:
                CONFIG.pop("_restore_warning", None)


def _require_team(data, team_id):
    team = _team(data, team_id)
    if not team:
        raise ActionError(f"Team {team_id} is not in this league.")
    return team


def action_propose(payload):
    account = payload.get("account")
    league_id = payload.get("league")
    game = payload.get("game") or CONFIG["game"]
    season = payload.get("season") or CONFIG["season"]
    team_id = int(payload.get("teamId") or 0)
    partner_id = int(payload.get("toTeamId") or 0)
    give = [int(player_id) for player_id in payload.get("give") or []]
    receive = [int(player_id) for player_id in payload.get("receive") or []]
    comment = (payload.get("comment") or "").strip()[:200]
    lm = bool(payload.get("lm"))

    if not account:
        raise ActionError("Select an account first.")
    if not give and not receive:
        raise ActionError("Select at least one player for either side of the trade.")
    if not team_id or not partner_id or team_id == partner_id:
        raise ActionError("Pick a valid trade partner.")

    league = _league(account, league_id, game, season)
    data = league.get(("mTeam", "mRoster", "mSettings"))
    my_team = _require_team(data, team_id)
    _require_team(data, partner_id)

    my_swid = norm_swid(league.swid)
    owns_team = any(norm_swid(owner) == my_swid for owner in my_team.get("owners") or [])
    if not owns_team and not lm:
        raise ActionError("You do not own that team. Enable LM mode to act for it.")

    rosters = _rosters(data)
    if any(player_id not in rosters.get(team_id, set()) for player_id in give):
        raise ActionError("One of the players you are sending is not on that roster.")
    if any(player_id not in rosters.get(partner_id, set()) for player_id in receive):
        raise ActionError("One of the players you are requesting is not on that roster.")

    items = []
    for player_id in dict.fromkeys(give):
        items.append(
            {
                "playerId": player_id,
                "fromTeamId": team_id,
                "toTeamId": partner_id,
                "type": "TRADE",
            }
        )
    for player_id in dict.fromkeys(receive):
        items.append(
            {
                "playerId": player_id,
                "fromTeamId": partner_id,
                "toTeamId": team_id,
                "type": "TRADE",
            }
        )
    if not items:
        raise ActionError("The trade is empty.")

    member_id = _owner_of(my_team, league.swid)
    period = _period(data)

    def go():
        body = build_transaction(
            "TRADE_PROPOSAL",
            team_id,
            member_id,
            period,
            items=items,
            comment=comment or "Proposed from League Desk",
            lm=lm,
        )
        return _post_transaction(league, body)

    result = with_lm(league, lm, go)
    invalidate_cache()
    return {
        "tradeId": (result or {}).get("id"),
        "message": "Trade proposal sent.",
        "result": result,
        "restoreWarning": CONFIG.get("_restore_warning"),
    }


def action_accept(payload):
    account = payload.get("account")
    league_id = payload.get("league")
    game = payload.get("game") or CONFIG["game"]
    season = payload.get("season") or CONFIG["season"]
    trade_id = payload.get("tradeId")
    team_id = int(payload.get("teamId") or 0)
    lm = bool(payload.get("lm"))

    if not account:
        raise ActionError("Select an account first.")
    if not trade_id:
        raise ActionError("Missing trade id.")

    league = _league(account, league_id, game, season)
    data = league.get(("mTeam", "mRoster", "mSettings"))
    family = _trade_families(data).get(trade_id)
    if not family:
        raise ActionError("That trade is no longer pending.")
    if not family["pending"]:
        raise ActionError("That trade is not pending.")

    accepting_team = _require_team(data, team_id)
    items = [item for item in family["items"] if str(item.get("type")) == "TRADE"]
    involved = {int(item.get("fromTeamId") or 0) for item in items} | {
        int(item.get("toTeamId") or 0) for item in items
    }
    involved.discard(0)
    if team_id not in involved:
        raise ActionError("That team is not part of this trade.")

    my_swid = norm_swid(league.swid)
    owns_team = any(
        norm_swid(owner) == my_swid for owner in accepting_team.get("owners") or []
    )
    if not owns_team and not lm:
        raise ActionError(
            f"You do not own {accepting_team.get('name') or 'that team'}. "
            "Enable LM mode to accept on its behalf."
        )

    member_id = _owner_of(accepting_team, league.swid)
    period = _period(data)

    def go():
        body = build_transaction(
            "TRADE_ACCEPT",
            team_id,
            member_id,
            period,
            related=trade_id,
            comment="",
            lm=lm,
        )
        return _post_transaction(league, body)

    result = with_lm(league, lm, go)
    invalidate_cache()
    completed = bool(result) and result.get("isPending") is False
    return {
        "tradeId": trade_id,
        "message": (
            "Trade accepted."
            if completed
            else "Trade accepted — awaiting league review/processing."
        ),
        "result": result,
        "restoreWarning": CONFIG.get("_restore_warning"),
    }


def action_cancel(payload):
    account = payload.get("account")
    league_id = payload.get("league")
    game = payload.get("game") or CONFIG["game"]
    season = payload.get("season") or CONFIG["season"]
    trade_id = payload.get("tradeId")
    lm = bool(payload.get("lm"))

    if not account:
        raise ActionError("Select an account first.")
    if not trade_id:
        raise ActionError("Missing trade id.")

    league = _league(account, league_id, game, season)
    data = league.get(("mTeam", "mRoster", "mSettings"))
    family = _trade_families(data).get(trade_id)
    if not family:
        raise ActionError("That trade is no longer pending.")
    proposer_id = family.get("proposer")
    if not proposer_id:
        raise ActionError("Could not identify the proposing team for that trade.")

    my_team = _team(data, proposer_id)
    my_swid = norm_swid(league.swid)
    owns_team = my_team and any(
        norm_swid(owner) == my_swid for owner in my_team.get("owners") or []
    )
    if not owns_team and not lm:
        raise ActionError("Only the proposing team can cancel that trade.")

    member_id = _owner_of(my_team, league.swid)
    period = _period(data)

    def go():
        body = build_transaction(
            "TRADE_PROPOSAL",
            proposer_id,
            member_id,
            period,
            related=trade_id,
            execution="CANCEL",
            comment="Cancelled from League Desk",
            lm=lm,
        )
        return _post_transaction(league, body)

    result = with_lm(league, lm, go)
    invalidate_cache()
    return {
        "tradeId": trade_id,
        "message": "Trade cancelled.",
        "result": result,
        "restoreWarning": CONFIG.get("_restore_warning"),
    }


def action_drop(payload):
    account = payload.get("account")
    league_id = payload.get("league")
    game = payload.get("game") or CONFIG["game"]
    season = payload.get("season") or CONFIG["season"]
    team_id = int(payload.get("teamId") or 0)
    player_id = int(payload.get("playerId") or 0)
    lm = bool(payload.get("lm"))

    if not account:
        raise ActionError("Select an account first.")
    if not team_id or not player_id:
        raise ActionError("Missing team or player.")

    league = _league(account, league_id, game, season)
    data = league.get(("mTeam", "mRoster", "mSettings"))
    team = _require_team(data, team_id)
    entry = _entry(data, team_id, player_id)
    if not entry:
        raise ActionError("That player is not on that roster.")

    player = (entry.get("playerPoolEntry") or {}).get("player") or {}
    if player.get("droppable") is False:
        raise ActionError("ESPN marks that player as undroppable.", status=400)

    my_swid = norm_swid(league.swid)
    owns_team = any(norm_swid(owner) == my_swid for owner in team.get("owners") or [])
    if not owns_team and not lm:
        raise ActionError(
            f"You do not own {team.get('name') or 'that team'}. "
            "Enable LM mode to drop from another roster."
        )

    member_id = _owner_of(team, league.swid)
    items = [{"playerId": player_id, "fromTeamId": team_id, "toTeamId": 0, "type": "DROP"}]

    def go():
        body = build_transaction(
            "ROSTER",
            team_id,
            member_id,
            _period(data),
            items=items,
            comment="Dropped from League Desk",
            lm=lm,
        )
        return _post_transaction(league, body)

    result = with_lm(league, lm, go)
    invalidate_cache()
    return {
        "message": f"{player.get('fullName') or 'Player'} dropped.",
        "result": result,
        "restoreWarning": CONFIG.get("_restore_warning"),
    }


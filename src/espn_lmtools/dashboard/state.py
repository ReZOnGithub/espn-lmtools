"""League payload to dashboard model conversion and caching."""

from __future__ import annotations

import contextlib
import threading
import time

import requests

from espn_lmtools.client import League
from espn_lmtools.constants import (
    ACCENTS,
    ACTIVE_INJURIES,
    BASKETBALL_STATS,
    HEADSHOT_SPORT,
    LINEUP_SLOTS,
    POSITIONS,
    SITE_SPORT,
    SPORT,
    ZERO_SWID,
)

from .config import CONFIG

CACHE_TTL = 20.0

_CACHE: dict[tuple, tuple[float, dict]] = {}
_CACHE_LOCK = threading.Lock()
_BUILD_LOCK = threading.Lock()
_PRO_TEAMS: dict[str, tuple[float, dict]] = {}
_PRO_LOCK = threading.Lock()
_NAME_CACHE: dict[tuple, str] = {}
_NAME_LOCK = threading.Lock()


def norm_swid(value) -> str:
    return (value or "").strip("{}").replace("-", "").upper()


def now_ms() -> int:
    return int(time.time() * 1000)


def season_label(season) -> str:
    try:
        number = int(season)
        return f"{number - 1}-{str(number)[-2:]}"
    except (TypeError, ValueError):
        return str(season)


def headshot_url(game: str, player_id) -> str:
    sport = HEADSHOT_SPORT.get(game, "nba")
    return (
        "https://a.espncdn.com/combiner/i?img=/i/headshots/"
        f"{sport}/players/full/{player_id}.png&w=350&h=254&scale=crop&cquality=40"
    )


def initials(name: str) -> str:
    parts = [part for part in (name or "").replace(".", " ").split() if part]
    return "".join(part[0] for part in parts[:2]).upper() or "?"


def _round(value, digits=2):
    try:
        if value is None:
            return None
        return round(float(value), digits)
    except (TypeError, ValueError):
        return None


def pro_teams(game: str) -> dict:
    with _PRO_LOCK:
        hit = _PRO_TEAMS.get(game)
        if hit and time.time() - hit[0] < 12 * 3600:
            return hit[1]

    path = SITE_SPORT.get(game, "basketball/nba")
    mapping: dict[str, dict] = {}
    try:
        response = requests.get(
            f"https://site.api.espn.com/apis/site/v2/sports/{path}/teams",
            headers={"User-Agent": "Mozilla/5.0"},
            timeout=15,
        )
        if response.status_code == 200:
            payload = response.json()
            groups = (payload.get("sports") or [{}])[0].get("leagues") or [{}]
            for wrapper in groups[0].get("teams") or []:
                team = wrapper.get("team") or {}
                if not team.get("id"):
                    continue
                mapping[str(team["id"])] = {
                    "abbrev": team.get("abbreviation") or "",
                    "name": team.get("displayName") or team.get("name") or "",
                    "color": f"#{team['color']}" if team.get("color") else None,
                    "logo": ((team.get("logos") or [{}])[0]).get("href"),
                }
    except requests.RequestException:
        pass

    with _PRO_LOCK:
        _PRO_TEAMS[game] = (time.time(), mapping)
    return mapping


def resolve_player_name(game: str, player_id) -> str:
    key = (game, str(player_id))
    with _NAME_LOCK:
        if key in _NAME_CACHE:
            return _NAME_CACHE[key]

    name = None
    path = SITE_SPORT.get(game, "basketball/nba")
    try:
        response = requests.get(
            f"https://site.web.api.espn.com/apis/common/v3/sports/{path}/athletes/{player_id}",
            headers={"User-Agent": "Mozilla/5.0"},
            timeout=8,
        )
        if response.status_code == 200:
            athlete = (response.json() or {}).get("athlete") or {}
            name = athlete.get("displayName") or athlete.get("fullName")
    except requests.RequestException:
        pass

    name = name or f"Player {player_id}"
    with _NAME_LOCK:
        _NAME_CACHE[key] = name
    return name


def _stat_entry(stats, season, source):
    for item in stats:
        if (
            item.get("seasonId") == season
            and item.get("statSourceId") == source
            and item.get("statSplitTypeId") == 0
            and (item.get("scoringPeriodId") or 0) == 0
        ):
            return item
    return None


def _stat_summary(player, season, game):
    with contextlib.suppress(TypeError, ValueError):
        season = int(season)
    stats = player.get("stats") or []
    current_actual = _stat_entry(stats, season, 0)
    previous_actual = _stat_entry(stats, season - 1, 0)
    current_proj = _stat_entry(stats, season, 1)
    previous_proj = _stat_entry(stats, season - 1, 1)

    def avg(entry):
        if not entry:
            return None
        value = entry.get("appliedAverage")
        if value is None or value <= 0:
            return None
        return float(value)

    fppg = avg(current_actual) or avg(previous_actual)
    last_fppg = avg(previous_actual)
    proj_fppg = avg(current_proj) or avg(previous_proj)

    actual = None
    actual_season = None
    if current_actual and avg(current_actual) is not None:
        actual, actual_season = current_actual, season
    elif previous_actual and avg(previous_actual) is not None:
        actual, actual_season = previous_actual, season - 1

    games = None
    if actual:
        total = actual.get("appliedTotal")
        average = actual.get("appliedAverage")
        if total and average:
            games = int(round(total / average))

    stat_line = []
    if game == "fba" and actual:
        averages = actual.get("averageStats") or {}
        for label, stat_id, scale, is_pct in BASKETBALL_STATS:
            value = averages.get(str(stat_id))
            if value is None:
                continue
            text = f"{value * scale:.1f}%" if is_pct else f"{value * scale:.1f}"
            stat_line.append({"label": label, "value": text})

    return {
        "fppg": _round(fppg, 1),
        "lastFppg": _round(last_fppg, 1),
        "projFppg": _round(proj_fppg, 1),
        "gamesPlayed": games,
        "statsSeason": season_label(actual_season) if actual_season else None,
        "statLine": stat_line,
    }


def _player_brief(player_id, players_by_id, game):
    known = players_by_id.get(player_id)
    if known:
        return {
            "id": player_id,
            "name": known["name"],
            "position": known["position"],
            "proTeam": known.get("proTeam"),
            "headshot": known.get("headshot"),
            "fppg": known.get("fppg"),
            "teamId": known.get("teamId"),
        }
    return {
        "id": player_id,
        "name": resolve_player_name(game, player_id),
        "position": "",
        "proTeam": "",
        "headshot": headshot_url(game, player_id),
        "fppg": None,
        "teamId": None,
    }


def build_player(entry, team_id, season, game, pro_map):
    pool = entry.get("playerPoolEntry") or {}
    player = pool.get("player") or {}
    player_id = player.get("id") or entry.get("playerId")
    name = player.get("fullName") or resolve_player_name(game, player_id)

    pro = pro_map.get(str(player.get("proTeamId"))) or {}
    slot_id = entry.get("lineupSlotId")
    slot_name = LINEUP_SLOTS.get(game, {}).get(slot_id)
    if slot_name is None:
        slot_name = f"Slot {slot_id}" if slot_id is not None else "—"

    injury = (player.get("injuryStatus") or entry.get("injuryStatus") or "").upper()
    ownership = player.get("ownership") or {}
    summary = _stat_summary(player, season, game)

    return {
        "id": player_id,
        "name": name,
        "initials": initials(name),
        "teamId": team_id,
        "position": POSITIONS.get(game, {}).get(player.get("defaultPositionId"), "—"),
        "positionId": player.get("defaultPositionId"),
        "proTeamId": player.get("proTeamId"),
        "proTeam": pro.get("abbrev") or "",
        "proTeamName": pro.get("name") or "",
        "proTeamColor": pro.get("color"),
        "slotId": slot_id,
        "slot": slot_name,
        "isStarter": slot_id is not None and slot_id < 11,
        "injuryStatus": injury,
        "injured": bool(player.get("injured")) or injury not in ACTIVE_INJURIES,
        "headshot": headshot_url(game, player_id),
        "ownership": {
            "owned": _round(ownership.get("percentOwned"), 1),
            "started": _round(ownership.get("percentStarted"), 1),
            "change": _round(ownership.get("percentChange"), 2),
            "adp": _round(ownership.get("averageDraftPosition"), 1),
            "auction": _round(ownership.get("auctionValueAverage"), 1),
        },
        **summary,
        "outlook": player.get("seasonOutlook") or "",
        "droppable": player.get("droppable", True),
        "tradeLocked": bool(pool.get("tradeLocked")),
        "lineupLocked": bool(pool.get("lineupLocked")),
        "acquisitionType": entry.get("acquisitionType"),
        "acquisitionDate": entry.get("acquisitionDate"),
        "pendingTransactions": entry.get("pendingTransactionIds") or [],
    }


def build_team(team, index, season, game, pro_map, owner_names, my_team_id):
    roster = []
    for entry in (team.get("roster") or {}).get("entries", []):
        if entry.get("playerId") and entry.get("playerId") > 0:
            roster.append(build_player(entry, team["id"], season, game, pro_map))

    roster.sort(
        key=lambda player: (
            0 if player["isStarter"] else 1,
            player["slotId"] if player["slotId"] is not None else 99,
            -(player.get("fppg") or 0),
        )
    )

    record = (team.get("record") or {}).get("overall") or {}
    accent = ACCENTS[index % len(ACCENTS)]
    total_fppg = sum((player.get("fppg") or 0) for player in roster)

    return {
        "id": team["id"],
        "name": team.get("name") or f"Team {team['id']}",
        "abbrev": team.get("abbrev") or f"T{team['id']}",
        "logo": team.get("logo"),
        "owners": owner_names,
        "isMine": team["id"] == my_team_id,
        "accent": accent,
        "record": {
            "wins": record.get("wins", 0),
            "losses": record.get("losses", 0),
            "ties": record.get("ties", 0),
            "pct": _round(record.get("percentage"), 3),
            "pointsFor": _round(record.get("pointsFor"), 1),
            "pointsAgainst": _round(record.get("pointsAgainst"), 1),
            "streak": record.get("streakType"),
            "streakLength": record.get("streakLength", 0),
        },
        "rank": team.get("playoffSeed") or team.get("currentProjectedRank") or index + 1,
        "waiverRank": team.get("waiverRank"),
        "roster": roster,
        "totalFppg": _round(total_fppg, 1),
        "totalPlayers": len(roster),
    }


def _team_meta(teams):
    return {
        team["id"]: {
            "name": team["name"],
            "logo": team["logo"],
            "accent": team["accent"],
            "abbrev": team["abbrev"],
        }
        for team in teams
    }


def _gather_trade_records(data):
    records = {}
    for transaction in data.get("transactions") or []:
        if transaction.get("id"):
            records[transaction["id"]] = transaction
    for team in data.get("teams") or []:
        for transaction in team.get("pendingTransactions") or []:
            if transaction.get("id"):
                records[transaction["id"]] = transaction
    return list(records.values())


def _trade_families(data):
    families = {}
    for transaction in _gather_trade_records(data):
        kind = str(transaction.get("type") or "")
        if not kind.startswith("TRADE"):
            continue
        related = transaction.get("relatedTransactionId")
        family_id = related if kind == "TRADE_ACCEPT" and related else transaction.get("id")
        family = families.setdefault(
            family_id,
            {
                "id": family_id,
                "records": [],
                "items": [],
                "proposer": None,
                "accepted": set(),
                "dates": [],
                "expirations": [],
                "pending": False,
                "executed": False,
            },
        )
        family["records"].append(transaction)
        items = transaction.get("items") or []
        if items and (kind == "TRADE_PROPOSAL" or not family["items"]):
            family["items"] = items
        if kind == "TRADE_PROPOSAL":
            family["proposer"] = transaction.get("teamId")
        for team_id, action in (transaction.get("teamActions") or {}).items():
            if str(action).upper() == "ACCEPTED":
                with contextlib.suppress(TypeError, ValueError):
                    family["accepted"].add(int(team_id))
        for key in ("proposedDate", "acceptedDate"):
            if transaction.get(key):
                family["dates"].append(transaction[key])
        if transaction.get("expirationDate"):
            family["expirations"].append(transaction["expirationDate"])
        family["pending"] = (
            family["pending"]
            or bool(transaction.get("isPending"))
            or str(transaction.get("status") or "").upper() == "PENDING"
        )
        family["executed"] = (
            family["executed"]
            or str(transaction.get("status") or "").upper() == "EXECUTED"
            or str(transaction.get("executionType") or "").upper() == "EXECUTE"
            and not transaction.get("isPending")
        )
    return families


def build_trades(data, players_by_id, teams, my_team_id, game):
    meta = _team_meta(teams)
    trades = []

    for family_id, family in _trade_families(data).items():
        items = [item for item in family["items"] if str(item.get("type") or "") == "TRADE"]
        if not items:
            continue

        sides: dict[int, list] = {}
        for item in items:
            try:
                from_team = int(item.get("fromTeamId") or 0)
                to_team = int(item.get("toTeamId") or 0)
            except (TypeError, ValueError):
                continue
            player_id = item.get("playerId")
            if player_id and player_id > 0:
                brief = _player_brief(player_id, players_by_id, game)
                brief["fromTeamId"] = from_team
                brief["toTeamId"] = to_team
                sides.setdefault(from_team, []).append(brief)
            elif item.get("overallPickNumber"):
                sides.setdefault(from_team, []).append(
                    {
                        "id": None,
                        "name": f"Round {item.get('roundId') or '?'} pick",
                        "position": "PICK",
                        "isPick": True,
                        "fromTeamId": from_team,
                        "toTeamId": to_team,
                    }
                )

        involved = sorted(team for team in sides if team)
        if len(involved) < 2:
            continue

        proposer = family["proposer"] or next(
            (
                int(record.get("teamId"))
                for record in family["records"]
                if record.get("teamId")
            ),
            involved[0],
        )
        accepted = set(family["accepted"]) | {proposer}
        needs = [team_id for team_id in involved if team_id not in accepted]

        pending = bool(needs) and family["pending"] and not family["executed"]
        processing = bool(needs) and family["pending"] and family["executed"]
        if not family["pending"] and not needs:
            status = "completed"
        elif pending or processing:
            status = "pending" if pending else "processing"
        elif not needs:
            status = "processing" if family["pending"] else "completed"
        else:
            status = "pending"

        side_payload = []
        for team_id in involved:
            players = sides[team_id]
            side_payload.append(
                {
                    "teamId": team_id,
                    "teamName": meta.get(team_id, {}).get("name") or f"Team {team_id}",
                    "teamLogo": meta.get(team_id, {}).get("logo"),
                    "accent": meta.get(team_id, {}).get("accent")
                    or ACCENTS[team_id % len(ACCENTS)],
                    "players": players,
                    "value": _round(sum((player.get("fppg") or 0) for player in players), 1),
                    "accepted": team_id in accepted,
                    "isMine": team_id == my_team_id,
                    "isProposer": team_id == proposer,
                }
            )

        accept_options = [
            {
                "teamId": team_id,
                "teamName": meta.get(team_id, {}).get("name") or f"Team {team_id}",
                "requiresLM": team_id != my_team_id,
            }
            for team_id in needs
        ]

        if my_team_id == proposer:
            direction = "outgoing"
        elif my_team_id in involved:
            direction = "incoming"
        else:
            direction = "neutral"

        trades.append(
            {
                "id": family_id,
                "proposerTeamId": proposer,
                "proposerTeam": meta.get(proposer, {}).get("name") or f"Team {proposer}",
                "direction": direction,
                "status": status,
                "isPending": pending or processing,
                "sides": side_payload,
                "acceptOptions": accept_options,
                "canCancel": direction == "outgoing" and pending,
                "proposedDate": min(family["dates"]) if family["dates"] else None,
                "lastEventDate": max(family["dates"]) if family["dates"] else None,
                "expirationDate": max(family["expirations"]) if family["expirations"] else None,
                "needsAcceptFrom": needs,
            }
        )

    trades.sort(key=lambda trade: (trade["status"] != "pending", -(trade.get("lastEventDate") or 0)))
    return trades


def build_activity(data, players_by_id, teams, game):
    meta = _team_meta(teams)
    activity = []
    family_items = {}

    for transaction in data.get("transactions") or []:
        if transaction.get("type") == "TRADE_PROPOSAL" and transaction.get("id"):
            family_items[transaction["id"]] = transaction.get("items") or []
        if transaction.get("type") == "TRADE_ACCEPT" and transaction.get("relatedTransactionId"):
            family_items.setdefault(
                transaction["relatedTransactionId"], transaction.get("items") or []
            )

    families = _trade_families(data)
    for family_id, family in families.items():
        if family["pending"] and not family["executed"]:
            continue
        completed = any(
            str(record.get("type") or "") == "TRADE_ACCEPT"
            and (
                str(record.get("status") or "").upper() == "EXECUTED"
                or (
                    str(record.get("executionType") or "").upper() == "EXECUTE"
                    and not record.get("isPending")
                )
            )
            for record in family["records"]
        )
        if not completed:
            continue
        items = [item for item in family["items"] if str(item.get("type") or "") == "TRADE"]
        if not items:
            continue

        targets = {}
        for item in items:
            player_id = item.get("playerId")
            if not player_id or player_id < 0:
                continue
            brief = _player_brief(player_id, players_by_id, game)
            brief["fromTeamId"] = item.get("fromTeamId")
            brief["toTeamId"] = item.get("toTeamId")
            targets.setdefault(item.get("fromTeamId"), []).append(brief)

        sides = []
        for team_id, players in targets.items():
            sides.append(
                {
                    "teamId": team_id,
                    "teamName": meta.get(team_id, {}).get("name") or f"Team {team_id}",
                    "teamLogo": meta.get(team_id, {}).get("logo"),
                    "players": players,
                }
            )
        activity.append(
            {
                "id": family_id,
                "kind": "trade",
                "title": "Trade completed",
                "subtitle": (
                    f"{len(sides)} teams · "
                    f"{sum(len(side['players']) for side in sides)} players"
                ),
                "date": max(family["dates"]) if family["dates"] else None,
                "status": "completed",
                "sides": sides,
            }
        )

    for transaction in data.get("transactions") or []:
        kind = str(transaction.get("type") or "")
        if kind.startswith("TRADE"):
            continue
        items = [
            item for item in (transaction.get("items") or []) if item.get("playerId", -1) > 0
        ]
        players = []
        for item in items:
            brief = _player_brief(item["playerId"], players_by_id, game)
            brief["fromTeamId"] = item.get("fromTeamId")
            brief["toTeamId"] = item.get("toTeamId")
            players.append(brief)

        team_id = transaction.get("teamId")
        title = "Roster move"
        if any(str(item.get("type")) == "DROP" for item in items):
            title = "Player dropped"
        elif any(str(item.get("type")) in ("ADD", "FREEAGENT", "WAIVER") for item in items):
            title = "Player added"

        activity.append(
            {
                "id": transaction.get("id"),
                "kind": "roster",
                "title": title,
                "subtitle": meta.get(team_id, {}).get("name") or f"Team {team_id}",
                "date": transaction.get("proposedDate") or transaction.get("acceptedDate"),
                "status": "completed",
                "sides": [
                    {
                        "teamId": team_id,
                        "teamName": meta.get(team_id, {}).get("name") or f"Team {team_id}",
                        "teamLogo": meta.get(team_id, {}).get("logo"),
                        "players": players,
                    }
                ],
            }
        )

    activity.sort(key=lambda item: -(item.get("date") or 0))
    return activity[:40]


def build_state(account, league_id, game, season):
    league = League(league_id, account, game, season, CONFIG["cookies_dir"])
    data = league.get(("mRoster", "mTeam", "mSettings", "mMembers", "mTransactions2"))

    settings = data.get("settings") or {}
    status = data.get("status") or {}
    members = data.get("members") or []
    pro_map = pro_teams(game)

    members_by_swid = {
        norm_swid(member.get("id")): member
        for member in members
        if member.get("id") and norm_swid(member.get("id")) != ZERO_SWID
    }

    def owner_label(member_id):
        member = members_by_swid.get(norm_swid(member_id))
        if not member:
            return "Unclaimed"
        return (
            member.get("displayName")
            or " ".join(
                value for value in (member.get("firstName"), member.get("lastName")) if value
            )
            or "Member"
        )

    me = members_by_swid.get(norm_swid(league.swid))
    my_team_id = None
    for team in data.get("teams") or []:
        if any(norm_swid(owner) == norm_swid(league.swid) for owner in (team.get("owners") or [])):
            my_team_id = team["id"]
            break

    teams = []
    for index, team in enumerate(
        sorted(data.get("teams") or [], key=lambda item: item.get("id", 0))
    ):
        owners = [owner_label(owner) for owner in (team.get("owners") or [])]
        if not owners:
            owners = ["Unclaimed"]
        teams.append(build_team(team, index, season, game, pro_map, owners, my_team_id))

    players_by_id = {}
    for team in teams:
        for player in team["roster"]:
            players_by_id[player["id"]] = player

    pending_trades = [
        trade
        for trade in build_trades(data, players_by_id, teams, my_team_id, game)
        if trade["status"] in ("pending", "processing")
    ]
    activity = build_activity(data, players_by_id, teams, game)

    my_team = next((team for team in teams if team["id"] == my_team_id), None)
    account_name = (
        (me or {}).get("displayName")
        or " ".join(
            value
            for value in ((me or {}).get("firstName"), (me or {}).get("lastName"))
            if value
        )
        or account
    )

    trade_settings = settings.get("tradeSettings") or {}
    acquisition = settings.get("acquisitionSettings") or {}

    return {
        "league": {
            "id": str(league_id),
            "name": settings.get("name") or f"League {league_id}",
            "size": settings.get("size") or len(teams),
            "game": game,
            "sport": SPORT.get(game, "sport"),
            "season": str(season),
            "seasonLabel": season_label(season),
            "week": status.get("latestScoringPeriod") or 1,
            "matchupPeriod": status.get("currentMatchupPeriod"),
            "tradeDeadline": trade_settings.get("deadlineDate"),
            "revisionHours": trade_settings.get("revisionHours"),
            "vetoVotes": trade_settings.get("vetoVotesRequired"),
            "waiverType": acquisition.get("acquisitionType"),
            "waiverDays": acquisition.get("waiverProcessDays"),
            "lineupSlots": LINEUP_SLOTS.get(game, {}),
            "positions": POSITIONS.get(game, {}),
        },
        "account": {
            "name": account,
            "swid": league.swid,
            "label": account_name,
            "isLM": bool((me or {}).get("isLeagueManager")),
            "teamId": my_team_id,
            "teamName": (my_team or {}).get("name"),
        },
        "teams": teams,
        "myTeamId": my_team_id,
        "pendingTrades": pending_trades,
        "activity": activity,
        "fetchedAt": now_ms(),
    }


def get_state(account, league_id, game, season, refresh=False):
    key = (account, str(league_id), game, str(season))
    if not refresh:
        with _CACHE_LOCK:
            entry = _CACHE.get(key)
            if entry and time.time() - entry[0] < CACHE_TTL:
                return entry[1]

    with _BUILD_LOCK:
        if not refresh:
            with _CACHE_LOCK:
                entry = _CACHE.get(key)
                if entry and time.time() - entry[0] < CACHE_TTL:
                    return entry[1]
        state = build_state(account, league_id, game, season)
        with _CACHE_LOCK:
            _CACHE[key] = (time.time(), state)
        return state


def invalidate_cache():
    with _CACHE_LOCK:
        _CACHE.clear()


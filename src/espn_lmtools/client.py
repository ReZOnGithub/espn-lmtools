"""Authenticated ESPN Fantasy league client."""

from __future__ import annotations

import copy
import json
from pathlib import Path

import requests

from .constants import SPORT
from .cookies import load_cookies

READ_HOST = "https://lm-api-reads.fantasy.espn.com"
WRITE_HOST = "https://lm-api-writes.fantasy.espn.com"


class League:
    def __init__(
        self,
        league_id,
        account,
        game: str = "fba",
        season: str = "2027",
        cookies_dir: Path = Path("cookies"),
    ):
        self.league_id = league_id
        self.account = account
        self.game = game
        self.season = season
        self.cookies = load_cookies(cookies_dir / f"{account}.txt")
        self.swid = self.cookies["SWID"]
        sport = SPORT.get(game, "basketball")
        root = f"/apis/v3/games/{game}/seasons/{season}/segments/0/leagues/{league_id}"
        self.read = READ_HOST + root
        self.write = WRITE_HOST + root
        self.h = {
            "Accept": "application/json",
            "Content-Type": "application/json",
            "Origin": "https://fantasy.espn.com",
            "Referer": f"https://fantasy.espn.com/{sport}/league?leagueId={league_id}&seasonId={season}",
            "User-Agent": "Mozilla/5.0",
            "X-Fantasy-Source": "fantasy",
            "X-Fantasy-Platform": "kona-PROD-1",
        }

    def get(self, views=("mTeam", "mMembers", "mSettings")):
        query = "&".join(f"view={view}" for view in views)
        response = requests.get(
            f"{self.read}?{query}", cookies=self.cookies, headers=self.h, timeout=30
        )
        response.raise_for_status()
        return response.json()

    def members(self):
        return self.get(("mMembers", "mTeam")).get("members", [])

    def me(self):
        for member in self.members():
            if member.get("id", "").strip("{}").upper() == self.swid.strip("{}").upper():
                return member
        return None

    def is_lm(self) -> bool:
        member = self.me()
        return bool(member and member.get("isLeagueManager"))

    def rosters(self):
        data = self.get(("mRoster", "mTeam"))
        out = {}
        for team in data.get("teams", []):
            ids, names = [], {}
            for entry in (team.get("roster") or {}).get("entries", []):
                player = (entry.get("playerPoolEntry") or {}).get("player") or {}
                ids.append(entry.get("playerId"))
                names[entry.get("playerId")] = player.get("fullName") or str(entry.get("playerId"))
            out[team["id"]] = {"ids": ids, "names": names, "team": team}
        return out

    def set_lm(self, value, base=None):
        body = copy.deepcopy(base if base is not None else self.me())
        body["isLeagueManager"] = value
        response = requests.put(
            f"{self.write}/members/{self.swid}",
            cookies=self.cookies,
            headers=self.h,
            data=json.dumps(body),
            timeout=30,
        )
        return response, body

    def put_member(self, member, field, value):
        body = copy.deepcopy(member)
        body[field] = value
        response = requests.put(
            f"{self.write}/members/{member['id']}",
            cookies=self.cookies,
            headers=self.h,
            data=json.dumps(body),
            timeout=30,
        )
        return response, body

    def set_notification(self, member_swid, setting_id, enabled):
        body = {"id": setting_id, "enabled": enabled}
        response = requests.post(
            f"{self.write}/members/{member_swid}/notificationSettings/{setting_id}",
            cookies=self.cookies,
            headers=self.h,
            data=json.dumps(body),
            timeout=30,
        )
        return response, body

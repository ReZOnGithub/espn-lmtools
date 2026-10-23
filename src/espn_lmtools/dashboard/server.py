"""Local HTTP server for the League Desk dashboard."""

from __future__ import annotations

import argparse
import json
import mimetypes
import sys
import threading
import traceback
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import requests

from espn_lmtools.cookies import load_cookies

from .actions import ActionError, action_accept, action_cancel, action_drop, action_propose
from .config import CONFIG, DEFAULT_GAME, DEFAULT_LEAGUE, DEFAULT_SEASON
from .state import get_state

WEB_DIR = Path(__file__).resolve().parent.parent / "web"
MAX_BODY = 1_000_000


def available_accounts():
    accounts = []
    for path in sorted(Path(CONFIG["cookies_dir"]).glob("*.txt")):
        try:
            load_cookies(path)
            accounts.append(path.stem)
        except (OSError, ValueError):
            continue
    return accounts


def config_payload():
    accounts = available_accounts()
    default_account = CONFIG.get("account")
    if default_account not in accounts:
        default_account = accounts[0] if accounts else None
    return {
        "accounts": accounts,
        "defaultAccount": default_account,
        "league": CONFIG["league"],
        "game": CONFIG["game"],
        "season": CONFIG["season"],
        "games": [
            {"id": "fba", "label": "Basketball"},
            {"id": "ffl", "label": "Football"},
            {"id": "flb", "label": "Baseball"},
            {"id": "fhl", "label": "Hockey"},
        ],
        "cookiesDir": str(CONFIG["cookies_dir"]),
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "LeagueDesk/1.0"

    def log_message(self, fmt, *args):
        print(f"[web] {fmt % args}")

    def send_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def send_error_json(self, status, message, detail=None):
        self.send_json(status, {"ok": False, "error": message, "detail": detail})

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/api/config":
            self.send_json(200, {"ok": True, **config_payload()})
            return
        if parsed.path == "/api/state":
            query = parse_qs(parsed.query)
            account = (query.get("account") or [None])[0]
            league_id = (query.get("league") or [CONFIG["league"]])[0]
            game = (query.get("game") or [CONFIG["game"]])[0]
            season = (query.get("season") or [CONFIG["season"]])[0]
            refresh = (query.get("refresh") or ["0"])[0] in ("1", "true", "yes")
            if not account:
                self.send_error_json(400, "No account selected.")
                return
            try:
                state = get_state(account, league_id, game, season, refresh=refresh)
                self.send_json(200, {"ok": True, "state": state})
            except requests.HTTPError as exc:
                code = getattr(exc.response, "status_code", 502)
                hint = (
                    " That usually means the cookies expired or the account "
                    "cannot see this league."
                    if code in (401, 403)
                    else ""
                )
                self.send_error_json(
                    502,
                    f"ESPN read failed ({code}).{hint}",
                    getattr(exc.response, "text", "")[:300],
                )
            except FileNotFoundError:
                self.send_error_json(400, f"No cookie file for account '{account}'.")
            except ValueError as exc:
                self.send_error_json(400, str(exc))
            except Exception as exc:
                traceback.print_exc()
                self.send_error_json(502, f"Could not load league: {exc}")
            return
        if parsed.path.startswith("/api/"):
            self.send_error_json(404, "Unknown API endpoint.")
            return
        self.serve_static(parsed.path)

    def do_POST(self):
        parsed = urlparse(self.path)
        routes = {
            "/api/trade/propose": action_propose,
            "/api/trade/accept": action_accept,
            "/api/trade/cancel": action_cancel,
            "/api/drop": action_drop,
        }
        action = routes.get(parsed.path)
        if not action:
            self.send_error_json(404, "Unknown API endpoint.")
            return
        try:
            length = int(self.headers.get("Content-Length") or 0)
            if length > MAX_BODY:
                self.send_error_json(413, "Request body too large.")
                return
            raw = self.rfile.read(length) if length else b"{}"
            payload = json.loads(raw.decode("utf-8") or "{}")
        except (ValueError, UnicodeDecodeError):
            self.send_error_json(400, "Invalid JSON body.")
            return

        try:
            result = action(payload)
            result["ok"] = True
            self.send_json(200, result)
        except ActionError as exc:
            self.send_error_json(exc.status, str(exc), exc.detail)
        except requests.HTTPError as exc:
            self.send_error_json(
                502,
                f"ESPN request failed ({getattr(exc.response, 'status_code', '?')}).",
                getattr(exc.response, "text", "")[:300],
            )
        except Exception as exc:
            traceback.print_exc()
            self.send_error_json(502, f"Action failed: {exc}")

    def serve_static(self, path):
        relative = path.lstrip("/") or "index.html"
        if relative.endswith("/"):
            relative += "index.html"
        candidate = (WEB_DIR / relative).resolve()
        try:
            candidate.relative_to(WEB_DIR.resolve())
        except ValueError:
            self.send_error_json(403, "Forbidden.")
            return
        if not candidate.is_file():
            if "." not in Path(relative).name:
                candidate = WEB_DIR / "index.html"
            if not candidate.is_file():
                self.send_error_json(404, "Not found.")
                return
        body = candidate.read_bytes()
        content_type = mimetypes.guess_type(candidate.name)[0] or "application/octet-stream"
        if content_type.startswith("text/") or content_type in (
            "application/javascript",
            "application/json",
        ):
            content_type += "; charset=utf-8"
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Local dashboard for the ESPN LM toolkit.")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8777)
    parser.add_argument("--league", default=DEFAULT_LEAGUE)
    parser.add_argument("--game", default=DEFAULT_GAME, choices=("fba", "ffl", "flb", "fhl"))
    parser.add_argument("--season", default=DEFAULT_SEASON)
    parser.add_argument("--account", default=None)
    parser.add_argument("--cookies-dir", type=Path, default=Path("cookies"))
    parser.add_argument("--no-open", action="store_true", help="do not open a browser")
    args = parser.parse_args(argv)
    if not args.league:
        parser.error("--league is required")

    CONFIG.update(
        {
            "league": args.league,
            "game": args.game,
            "season": args.season,
            "account": args.account,
            "cookies_dir": args.cookies_dir,
        }
    )

    if not WEB_DIR.is_dir():
        sys.exit(f"missing UI directory: {WEB_DIR}")

    url = f"http://{args.host}:{args.port}/"
    try:
        server = ThreadingHTTPServer((args.host, args.port), Handler)
    except OSError as exc:
        sys.exit(f"cannot bind {args.host}:{args.port}: {exc}")

    print(f"  League Desk  {url}")
    print(f"  league {args.league}  game {args.game}  season {args.season}")
    print(f"  cookies {args.cookies_dir}")
    print("  Ctrl+C to stop\n")

    if not args.no_open:
        threading.Timer(0.7, lambda: webbrowser.open(url)).start()

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n  stopped")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())


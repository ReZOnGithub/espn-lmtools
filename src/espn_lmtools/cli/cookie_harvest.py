"""Log into ESPN in a real browser and export espn_s2/SWID cookies."""

from __future__ import annotations

import argparse
import contextlib
import json
import re
import sys
import time
from pathlib import Path

try:
    from playwright.sync_api import TimeoutError as PWTimeout
    from playwright.sync_api import sync_playwright
except ImportError:
    sys.exit(
        "playwright is required: python -m pip install playwright && "
        "python -m playwright install chromium"
    )

try:
    import requests
except ImportError:
    requests = None

from espn_lmtools.cookies import write_netscape

EMAIL_SELECTORS = [
    'input[type="email"]',
    'input[name="email"]',
    'input[id*="email" i]',
    'input[name*="email" i]',
    'input[autocomplete="username"]',
    'input[inputmode="email"]',
]

PASS_SELECTORS = [
    'input[type="password"]',
    'input[name="password"]',
    'input[id*="password" i]',
    'input[autocomplete="current-password"]',
]

SUBMIT_SELECTORS = [
    'button[type="submit"]',
    'input[type="submit"]',
    'button:has-text("Log In")',
    'button:has-text("Login")',
    'button:has-text("Sign In")',
    'button:has-text("Continue")',
    'button:has-text("Next")',
]

NEEDED = ("espn_s2", "SWID")

LOGIN_FAIL_MARKERS = (
    "password you entered is incorrect",
    "incorrect password",
    "too many attempts",
    "we can't find",
    "unable to log you in",
    "couldn't find your account",
)

OTP_SELECTORS = [
    'input[autocomplete="one-time-code"]',
    'input[inputmode="numeric"]',
    'input[name*="code" i]',
    'input[id*="code" i]',
    'input[placeholder*="code" i]',
]

RATE_LIMIT_MARKERS = (
    "too many attempts",
    "too many requests",
    "too many tries",
    "try again later",
    "temporarily locked",
    "temporarily blocked",
    "suspicious activity",
    "unusual activity",
    "we've temporarily",
)

JOIN_LABEL = re.compile(r"^(join league|join team|join|accept invite|accept|claim team)$", re.I)
JOINED_MARKERS = ("you're in", "you are in", "joined", "welcome to", "team name", "you have joined")


def slug(text: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "_", text).strip("_")
    return cleaned or "account"


def find_visible(page, selectors):
    for frame in page.frames:
        for selector in selectors:
            try:
                locator = frame.locator(selector)
                if locator.count() and locator.first.is_visible():
                    return locator.first
            except Exception:
                continue
    return None


def click_submit(page):
    for frame in page.frames:
        for selector in SUBMIT_SELECTORS:
            try:
                locator = frame.locator(selector)
                if locator.count() and locator.first.is_visible() and locator.first.is_enabled():
                    locator.first.click(timeout=2000)
                    return True
            except Exception:
                continue
    return False


def espn_cookies(context):
    try:
        jar = context.cookies(
            ["https://www.espn.com", "https://fantasy.espn.com", "https://espn.com"]
        )
    except Exception:
        jar = context.cookies()
    return [cookie for cookie in jar if "espn.com" in (cookie.get("domain") or "")]


def have_auth(jar):
    names = {cookie["name"] for cookie in jar}
    return all(name in names for name in NEEDED)


def verify(jar, league_id: str):
    if requests is None:
        return "requests not installed, skipped verification"
    cookies = {cookie["name"]: cookie["value"] for cookie in jar}
    url = (
        "https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl/seasons/2026"
        f"/segments/0/leagues/{league_id}?view=mSettings"
    )
    try:
        response = requests.get(
            url,
            cookies=cookies,
            headers={
                "Accept": "application/json",
                "Referer": "https://fantasy.espn.com/",
                "X-Fantasy-Source": "fantasy",
            },
            timeout=20,
        )
    except Exception as exc:
        return f"verification request failed: {exc}"
    if response.status_code == 200:
        return "league read OK (200) - cookies are live"
    return f"league read returned {response.status_code}: {response.text[:160]}"


def find_otp_input(page):
    for frame in page.frames:
        if "registerdisney" not in frame.url:
            continue
        for selector in OTP_SELECTORS:
            try:
                locator = frame.locator(selector)
                if locator.count() and locator.first.is_visible():
                    return locator.first
            except Exception:
                continue
    return None


def login_error(page):
    for frame in page.frames:
        if "registerdisney" not in frame.url:
            continue
        try:
            text = frame.inner_text("body")[:4000].lower()
        except Exception:
            continue
        for marker in LOGIN_FAIL_MARKERS:
            if marker in text:
                return marker
    return None


def rate_limited(page):
    for frame in page.frames:
        if "registerdisney" not in frame.url and "espn.com" not in frame.url:
            continue
        try:
            text = frame.inner_text("body")[:4000].lower()
        except Exception:
            continue
        for marker in RATE_LIMIT_MARKERS:
            if marker in text:
                return marker
    return None


def attempts_file(profile_dir: Path) -> Path:
    return profile_dir / "attempts.json"


def last_attempt(profile_dir: Path, name: str) -> float:
    path = attempts_file(profile_dir)
    if not path.exists():
        return 0.0
    try:
        return float(json.loads(path.read_text(encoding="utf-8")).get(name, 0))
    except Exception:
        return time.time()


def record_attempt(profile_dir: Path, name: str) -> None:
    path = attempts_file(profile_dir)
    try:
        data = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    except Exception:
        data = {}
    data[name] = time.time()
    path.write_text(json.dumps(data, indent=2), encoding="utf-8")


def join_league(page, join_url, manual_wait=60):
    page.goto(join_url, wait_until="domcontentloaded", timeout=45000)
    page.wait_for_timeout(4000)
    print(f"   join page: {page.url}")

    for _ in range(4):
        clicked = False
        for frame in page.frames:
            for selector in ("button", 'a[role="button"]', 'input[type="submit"]'):
                try:
                    locator = frame.locator(selector)
                    for index in range(min(locator.count(), 30)):
                        element = locator.nth(index)
                        try:
                            if not element.is_visible():
                                continue
                            text = (element.inner_text() or "").strip()
                        except Exception:
                            continue
                        if text and JOIN_LABEL.match(text):
                            print(f"   clicking '{text}'")
                            element.click(timeout=3000)
                            page.wait_for_timeout(4000)
                            clicked = True
                            break
                except Exception:
                    continue
                if clicked:
                    break
            if clicked:
                break
        if not clicked:
            break

    text = ""
    with contextlib.suppress(Exception):
        text = page.inner_text("body").lower()
    for marker in JOINED_MARKERS:
        if marker in text:
            print(f"   join looks complete ({marker})")
            return True

    print("   could not confirm the join automatically. Finish it in the window:")
    print(f"   {page.url}")
    print(f"   waiting {manual_wait}s for you...")
    with contextlib.suppress(Exception):
        page.wait_for_timeout(manual_wait * 1000)
    try:
        text = page.inner_text("body").lower()
    except Exception:
        text = ""
    return any(marker in text for marker in JOINED_MARKERS)


def discover_league_ids(page):
    ids = set()
    try:
        html = page.content()
    except Exception:
        html = ""
    for pattern in (r"leagueId=(\d{4,})", r'"leagueId"\s*:\s*(\d{4,})', r"/leagues/(\d{5,})"):
        ids.update(re.findall(pattern, html))
    try:
        text = page.inner_text("body")
    except Exception:
        text = ""
    for pattern in (r"leagueId=(\d{4,})",):
        ids.update(re.findall(pattern, text))
    return sorted(ids)


def ensure_fantasy_session(page):
    try:
        page.goto(
            "https://fantasy.espn.com/football/",
            wait_until="domcontentloaded",
            timeout=45000,
        )
        page.wait_for_timeout(5000)
    except Exception:
        pass
    return espn_cookies(page.context)


def harvest(page, email, password, timeout, otp_code=None, name="account"):
    filled_email = filled_pass = False
    otp_attempts = 0
    idle = 0
    deadline = time.monotonic() + timeout
    last_note = 0.0
    last_otp = 0.0

    while time.monotonic() < deadline:
        jar = espn_cookies(page.context)
        if have_auth(jar):
            return jar, None

        blocked = rate_limited(page)
        if blocked:
            return jar, f"ESPN is rate limiting this IP/account ({blocked}); stop and wait"

        otp_field = find_otp_input(page)
        if otp_field is not None and time.monotonic() - last_otp > 6:
            if otp_attempts >= 3:
                return espn_cookies(page.context), "email code rejected 3 times"
            otp_attempts += 1
            last_otp = time.monotonic()
            if otp_code:
                code = otp_code
            else:
                print(f"[{name}] ESPN emailed a verification code to {email}")
                try:
                    code = input(
                        f"[{name}] enter the code from the inbox "
                        f"(attempt {otp_attempts}/3): "
                    ).strip()
                except EOFError:
                    return espn_cookies(page.context), "no code provided and stdin is not interactive"
            if not code:
                continue
            try:
                otp_field.fill(code)
                click_submit(page) or otp_field.press("Enter")
                print(f"[{name}] submitted code, waiting for session...")
                page.wait_for_timeout(4000)
                continue
            except Exception as exc:
                print(f"[{name}] code entry failed: {exc}")

        field = find_visible(page, EMAIL_SELECTORS)
        if field is not None and not filled_email:
            try:
                field.fill(email)
                filled_email = True
                click_submit(page) or field.press("Enter")
                page.wait_for_timeout(1500)
                continue
            except Exception:
                pass

        field = find_visible(page, PASS_SELECTORS)
        if field is not None and not filled_pass:
            try:
                field.fill(password)
                filled_pass = True
                click_submit(page) or field.press("Enter")
                page.wait_for_timeout(2000)
                continue
            except Exception:
                pass

        if filled_email and filled_pass:
            failure = login_error(page)
            if failure:
                return espn_cookies(page.context), f"login rejected ({failure})"

        form_field = (
            find_visible(page, EMAIL_SELECTORS)
            or find_visible(page, PASS_SELECTORS)
            or find_otp_input(page)
        )
        has_onesite = any(cookie["name"] == "ESPN-ONESITE.WEB-PROD.api" for cookie in jar)
        idle = idle + 1 if form_field is None and has_onesite else 0

        if idle >= 3:
            print(
                f"[{name}] no login form and an ONESITE session exists; "
                "opening fantasy to mint espn_s2"
            )
            jar = ensure_fantasy_session(page)
            if have_auth(jar):
                return jar, None
            return jar, "login completed but ESPN did not issue espn_s2"

        if time.monotonic() - last_note > 15:
            last_note = time.monotonic()
            names = {cookie["name"] for cookie in jar}
            print(
                f"      waiting... url={page.url} "
                f"cookies[SWID={'yes' if 'SWID' in names else 'no'} "
                f"espn_s2={'yes' if 'espn_s2' in names else 'no'} "
                f"onesite={'yes' if 'ESPN-ONESITE.WEB-PROD.api' in names else 'no'}]"
            )

        page.wait_for_timeout(1000)

    return espn_cookies(page.context), "timed out waiting for espn_s2 + SWID"


def main(argv=None):
    parser = argparse.ArgumentParser(description="Log into ESPN and export espn_s2/SWID cookies.")
    parser.add_argument(
        "--creds", type=Path, default=Path("login.creds.json"), help="JSON list of name/email/password"
    )
    parser.add_argument("--out-dir", type=Path, default=Path("cookies"), help="where to write <name>.txt")
    parser.add_argument(
        "--profile-dir", type=Path, default=Path(".espnprofiles"), help="browser profiles"
    )
    parser.add_argument("--only", help="harvest a single account by name")
    parser.add_argument("--timeout", type=float, default=300.0, help="seconds per account")
    parser.add_argument("--league-id", help="optional: verify cookies against a league")
    parser.add_argument("--join-url", help="open an ESPN invite link after login and accept it")
    parser.add_argument("--headless", action="store_true", help="no window")
    parser.add_argument(
        "--cooldown",
        type=float,
        default=30.0,
        help="minutes to refuse a repeat login attempt (default 30)",
    )
    parser.add_argument("--force", action="store_true", help="ignore the cooldown")
    parser.add_argument("--otp-code", help="email verification code for a single account")
    parser.add_argument("--email", help="single-account mode: email")
    parser.add_argument("--password", help="single-account mode: password")
    parser.add_argument("--name", help="single-account mode: account name")
    args = parser.parse_args(argv)

    if args.email and args.password:
        accounts = [
            {
                "name": args.name or slug(args.email.split("@")[0]),
                "email": args.email,
                "password": args.password,
            }
        ]
    else:
        if not args.creds.exists():
            sys.exit(f"{args.creds} not found. Create it as a JSON list of name/email/password entries.")
        accounts = json.loads(args.creds.read_text(encoding="utf-8"))
        if isinstance(accounts, dict):
            accounts = accounts.get("accounts", [accounts])
        if not isinstance(accounts, list) or not accounts:
            sys.exit("creds file must contain a non-empty list")

    if args.only:
        accounts = [account for account in accounts if account.get("name") == args.only]
        if not accounts:
            sys.exit(f"no account named {args.only!r}")

    args.out_dir.mkdir(parents=True, exist_ok=True)
    args.profile_dir.mkdir(parents=True, exist_ok=True)

    ok, bad, skipped = [], [], []
    cooldown_s = max(args.cooldown, 0) * 60
    halt = False

    with sync_playwright() as playwright:
        for account in accounts:
            if halt:
                break
            name = slug(account.get("name") or account["email"].split("@")[0])
            email, password = account["email"], account["password"]
            profile = args.profile_dir / name
            profile.mkdir(parents=True, exist_ok=True)

            last = last_attempt(args.profile_dir, name)
            wait_left = cooldown_s - (time.time() - last) if last else 0
            if wait_left > 0 and not args.force:
                minutes = int(wait_left // 60) + 1
                print(
                    f"[{name}] SKIPPED: last login attempt was "
                    f"{int((time.time() - last) // 60)} min ago, {minutes} min of cooldown left. "
                    "Wait it out, or --force if you know the account is fine."
                )
                skipped.append(name)
                continue

            print(f"[{name}] launching ({profile})")
            context = playwright.chromium.launch_persistent_context(
                user_data_dir=str(profile),
                headless=args.headless,
                viewport={"width": 1100, "height": 800},
                args=["--disable-blink-features=AutomationControlled"],
            )
            page = context.pages[0] if context.pages else context.new_page()

            try:
                jar = espn_cookies(context)
                if have_auth(jar):
                    print(f"[{name}] existing profile already logged in")
                else:
                    record_attempt(args.profile_dir, name)
                    page.goto(
                        "https://www.espn.com/login",
                        wait_until="domcontentloaded",
                        timeout=60000,
                    )
                    page.wait_for_timeout(2500)
                    print(f"[{name}] login page: {page.url}")
                    jar, error = harvest(page, email, password, args.timeout, args.otp_code, name)
                    if error:
                        print(f"[{name}] FAILED: {error}")
                        if not have_auth(jar):
                            if jar:
                                partial = args.out_dir / f"{name}.partial.txt"
                                write_netscape(jar, partial, "espn_lmtools")
                                names = ", ".join(sorted(cookie["name"] for cookie in jar)[:10])
                                print(f"[{name}] partial cookies saved to {partial} ({names})")
                            bad.append(name)
                            if "rate limiting" in error:
                                print(
                                    "stopping: further attempts from this IP will only extend the block"
                                )
                                halt = True
                            continue

                target = args.out_dir / f"{name}.txt"
                write_netscape(jar, target, "espn_lmtools")
                names = sorted(cookie["name"] for cookie in jar)
                print(f"[{name}] wrote {target} ({len(jar)} cookies; auth={have_auth(jar)})")
                print(
                    f"[{name}] cookie names: {', '.join(names[:12])}"
                    f"{' ...' if len(names) > 12 else ''}"
                )

                if args.join_url:
                    joined = join_league(page, args.join_url)
                    print(f"[{name}] joined={joined}")

                if args.league_id:
                    print(f"[{name}] {verify(jar, args.league_id)}")
                else:
                    try:
                        page.goto(
                            "https://fantasy.espn.com/football/",
                            wait_until="domcontentloaded",
                            timeout=30000,
                        )
                        page.wait_for_timeout(3500)
                        ids = discover_league_ids(page)
                        if ids:
                            print(f"[{name}] league ids on your fantasy homepage: {', '.join(ids)}")
                        else:
                            print(f"[{name}] no league ids visible; this account may not be in a league yet")
                    except Exception as exc:
                        print(f"[{name}] league discovery skipped: {exc}")
                ok.append(name)
            except PWTimeout as exc:
                print(f"[{name}] playwright timeout: {exc}")
                bad.append(name)
            except Exception as exc:
                print(f"[{name}] error: {exc}")
                bad.append(name)
            finally:
                try:
                    if not args.headless:
                        page.wait_for_timeout(500)
                except Exception:
                    pass
                context.close()

    print()
    print(f"done: {len(ok)} ok, {len(bad)} failed")
    if ok:
        print(
            f"next: espn-trade-probe -r accept.txt --cookies-dir {args.out_dir} -b {ok[0]}"
        )
    if bad:
        print(f"failed: {', '.join(bad)} - re-run with --only <name> to retry just that one")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())


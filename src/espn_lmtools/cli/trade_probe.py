"""Replay an ESPN fantasy write request under multiple sessions (IDOR probe)."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import sys
import time
from pathlib import Path

import requests

from espn_lmtools.cookies import parse_netscape

HOP_BY_HOP = {
    "content-length",
    "connection",
    "transfer-encoding",
    "keep-alive",
    "proxy-connection",
    "te",
    "trailer",
    "upgrade",
}

AUTH_ERR_MARKERS = (
    "not authorized",
    "unauthorized",
    "forbidden",
    "auth_",
    "not permitted",
    "does not have permission",
    "access denied",
)

ACCOUNTS_TEMPLATE = {
    "accounts": [
        {
            "name": "ownerA",
            "swid": "{00000000-0000-0000-0000-000000000000}",
            "espn_s2": "PASTE_ESPN_S2",
        },
        {
            "name": "ownerB",
            "swid": "{11111111-1111-1111-1111-111111111111}",
            "espn_s2": "PASTE_ESPN_S2",
        },
        {
            "name": "outsider",
            "swid": "{22222222-2222-2222-2222-222222222222}",
            "espn_s2": "PASTE_ESPN_S2",
        },
    ]
}


def parse_raw_request(raw: bytes):
    sep = b"\r\n\r\n"
    index = raw.find(sep)
    if index == -1:
        sep = b"\n\n"
        index = raw.find(sep)
    if index == -1:
        raise ValueError("no blank line separating headers from body; is this a raw request?")

    head, body = raw[:index], raw[index + len(sep) :]
    lines = head.replace(b"\r\n", b"\n").decode("latin-1").split("\n")

    first = lines[0].strip()
    parts = first.split(" ")
    if len(parts) < 2:
        raise ValueError(f"malformed request line: {first!r}")
    method, target = parts[0].upper(), parts[1]

    headers = []
    for line in lines[1:]:
        if not line.strip():
            continue
        key, separator, value = line.partition(":")
        if not separator:
            continue
        headers.append((key.strip(), value.strip()))

    return method, target, headers, body


def header_get(headers, name):
    for key, value in headers:
        if key.lower() == name.lower():
            return value
    return None


def header_set(headers, name, value):
    out, placed = [], False
    for key, current in headers:
        if key.lower() == name.lower():
            if not placed:
                out.append((key, value))
                placed = True
        else:
            out.append((key, current))
    if not placed:
        out.append((name, value))
    return out


def load_accounts(path: Path):
    data = json.loads(path.read_text(encoding="utf-8"))
    accounts = data.get("accounts", data) if isinstance(data, dict) else data
    if not isinstance(accounts, list) or not accounts:
        raise ValueError("accounts.json must contain a non-empty list under 'accounts'")
    for account in accounts:
        if "password" in account or "email" in account:
            raise ValueError(
                "this looks like a login.creds.json (email/password). It is for "
                "espn-cookie-harvest. Run that first, then pass --cookies-dir here."
            )
        for field in ("name", "swid", "espn_s2"):
            if not account.get(field):
                raise ValueError(f"account {account.get('name', '?')!r} is missing '{field}'")
    return accounts


def load_cookie_accounts(directory: Path):
    candidates = sorted(
        path
        for path in directory.iterdir()
        if path.is_file() and path.suffix.lower() in (".txt", ".cookies")
    )
    accounts = []
    for path in candidates:
        jar = parse_netscape(path)
        if "espn_s2" not in jar or "SWID" not in jar:
            print(f"skip {path.name}: no espn_s2/SWID cookie")
            continue
        accounts.append(
            {
                "name": path.stem,
                "swid": jar["SWID"],
                "espn_s2": jar["espn_s2"],
                "source": path.name,
            }
        )
    if not accounts:
        raise ValueError(f"no usable *.txt cookie exports with espn_s2 + SWID in {directory}")
    return accounts


def fingerprint(secret: str) -> str:
    return hashlib.sha256(secret.encode()).hexdigest()[:10]


def jpath_set(obj, path: str, value):
    parts = [part for part in path.split(".") if part]
    if parts and parts[0] == "json":
        parts = parts[1:]
    if not parts:
        raise ValueError(f"empty path in --set {path!r}")
    current = obj
    for part in parts[:-1]:
        if isinstance(current, list):
            current = current[int(part)]
        elif isinstance(current, dict):
            current = current.setdefault(part, {})
        else:
            raise ValueError(f"cannot descend into {type(current).__name__} at {part!r}")
    last = parts[-1]
    if isinstance(current, list):
        current[int(last)] = value
    elif isinstance(current, dict):
        current[last] = value
    else:
        raise ValueError(f"cannot set {last!r} on {type(current).__name__}")


def recursive_swap(node, key: str, old, new):
    hits = 0
    if isinstance(node, dict):
        for item_key, value in node.items():
            if item_key == key and value == old:
                node[item_key] = new
                hits += 1
            else:
                hits += recursive_swap(value, key, old, new)
    elif isinstance(node, list):
        for item in node:
            hits += recursive_swap(item, key, old, new)
    return hits


def coerce(value: str):
    lowered = value.strip().lower()
    if lowered in ("true", "false"):
        return lowered == "true"
    if lowered in ("null", "none"):
        return None
    try:
        return int(value)
    except ValueError:
        pass
    try:
        return float(value)
    except ValueError:
        pass
    return value


def build_body(raw_body: bytes, content_type: str, sets, swaps):
    if not raw_body:
        return raw_body, 0
    is_json = "json" in (content_type or "").lower() or raw_body.lstrip()[:1] in (b"{", b"[")
    if not is_json:
        if sets or swaps:
            sys.exit("--set/--swap require a JSON body")
        return raw_body, 0

    try:
        payload = json.loads(raw_body.decode("utf-8"))
    except Exception as exc:
        sys.exit(f"body looks like JSON but does not parse: {exc}")

    changed = 0
    for item in sets or []:
        path, separator, raw_value = item.partition("=")
        if not separator:
            sys.exit(f"--set expects path=value, got {item!r}")
        jpath_set(payload, path, coerce(raw_value))
        changed += 1
    for item in swaps or []:
        bits = item.split(":", 2)
        if len(bits) != 3:
            sys.exit(f"--swap expects key:old:new, got {item!r}")
        key, old, new = bits
        changed += recursive_swap(payload, key, coerce(old), coerce(new))

    return json.dumps(payload, separators=(",", ":")).encode("utf-8"), changed


def looks_like_auth_error(text: str) -> bool:
    low = text.lower()
    return any(marker in low for marker in AUTH_ERR_MARKERS)


def preview(text: str, limit: int = 400) -> str:
    flat = " ".join(text.split())
    return flat if len(flat) <= limit else flat[:limit] + " ..."


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Replay an ESPN fantasy write request under multiple sessions (IDOR probe).",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("-r", "--request", required=False, type=Path, help="raw request file")
    parser.add_argument("-a", "--accounts", type=Path, help="accounts.json with name/swid/espn_s2")
    parser.add_argument(
        "--cookies-dir", type=Path, help="directory of Netscape cookies.txt exports, one per account"
    )
    parser.add_argument(
        "-b", "--baseline", help="account name that is legitimately allowed to run this action"
    )
    parser.add_argument(
        "--set", dest="sets", action="append", metavar="PATH=VALUE", help="set a JSON body value"
    )
    parser.add_argument(
        "--swap",
        dest="swaps",
        action="append",
        metavar="KEY:OLD:NEW",
        help="recursive value swap",
    )
    parser.add_argument("--url", help="override target URL (default: derived from request line + Host)")
    parser.add_argument("--header", action="append", metavar="K:V", help="extra/override header")
    parser.add_argument("--delay", type=float, default=2.0, help="seconds between requests")
    parser.add_argument("--timeout", type=float, default=30.0, help="per-request timeout")
    parser.add_argument("--out", type=Path, help="write full results JSON here")
    parser.add_argument(
        "--stop-on-bug", action="store_true", help="halt at the first 2xx from a non-baseline account"
    )
    parser.add_argument("--dry-run", action="store_true", help="print what would be sent")
    parser.add_argument("--yes", action="store_true", help="skip the interactive confirmation")
    parser.add_argument("--init-accounts", action="store_true", help="write accounts.example.json")
    args = parser.parse_args(argv)

    if args.init_accounts:
        target = Path("accounts.example.json")
        target.write_text(json.dumps(ACCOUNTS_TEMPLATE, indent=2) + "\n", encoding="utf-8")
        print(f"wrote {target.resolve()}")
        return 0

    if not args.request:
        parser.error("-r/--request is required")
    if not args.baseline:
        parser.error("-b/--baseline is required")
    if not args.accounts and not args.cookies_dir:
        parser.error("supply either -a/--accounts or --cookies-dir")
    if args.accounts and args.cookies_dir:
        parser.error("-a/--accounts and --cookies-dir are mutually exclusive")

    method, target, headers, raw_body = parse_raw_request(args.request.read_bytes())
    try:
        accounts = (
            load_accounts(args.accounts)
            if args.accounts
            else load_cookie_accounts(args.cookies_dir)
        )
    except (ValueError, OSError) as exc:
        sys.exit(f"account error: {exc}")
    by_name = {account["name"]: account for account in accounts}
    if args.baseline not in by_name:
        sys.exit(f"baseline {args.baseline!r} not in accounts ({', '.join(by_name)})")

    content_type = header_get(headers, "content-type") or ""
    body, mutated = build_body(raw_body, content_type, args.sets, args.swaps)

    host = header_get(headers, "host")
    if args.url:
        url = args.url
    elif target.startswith("http"):
        url = target
    elif host:
        url = f"https://{host}{target}"
    else:
        sys.exit("no Host header and no --url given")

    base_headers = [
        (key, value) for key, value in headers if key.lower() not in HOP_BY_HOP and key.lower() != "cookie"
    ]
    for item in args.header or []:
        key, _, value = item.partition(":")
        base_headers = header_set(base_headers, key.strip(), value.strip())

    print(f"{method} {url}")
    print(f"body: {len(body)} bytes, {mutated} mutation(s) applied")
    print(f"accounts: {', '.join(by_name)}  (baseline: {args.baseline})")
    print()

    if args.dry_run:
        for account in accounts:
            origin = f", from {account['source']}" if account.get("source") else ""
            print(
                f"--- {account['name']} (espn_s2 {fingerprint(account['espn_s2'])}{origin})"
            )
            for key, value in base_headers:
                print(f"{key}: {value}")
            print(f"Cookie: SWID={account['swid']}; espn_s2=<{len(account['espn_s2'])} chars>")
            print()
            if body:
                print(body.decode("utf-8", "replace"))
                print()
        return 0

    if not args.yes:
        print(f"About to send {len(accounts)} state-changing request(s) to {url}.")
        print("Only continue if the league in that URL is a test league with accounts you own.")
        answer = input("Type the league id from the URL to continue: ").strip()
        league_id = ""
        for part in url.split("leagues/")[-1:]:
            league_id = part.split("/")[0].split("?")[0]
        if answer != league_id:
            print("league id did not match; aborting")
            return 2
        print()

    results = []
    bug_found = False

    for index, account in enumerate(accounts):
        cookie = f"SWID={account['swid']}; espn_s2={account['espn_s2']}"
        send_headers = header_set(copy.deepcopy(base_headers), "Cookie", cookie)

        record = {
            "account": account["name"],
            "espn_s2_sha256_prefix": fingerprint(account["espn_s2"]),
            "role": "baseline" if account["name"] == args.baseline else "probe",
        }
        try:
            started = time.monotonic()
            response = requests.request(
                method,
                url,
                headers=dict(send_headers),
                data=body or None,
                timeout=args.timeout,
                allow_redirects=False,
            )
            record["elapsed_ms"] = int((time.monotonic() - started) * 1000)
            record["status"] = response.status_code
            text = response.text or ""
            record["body_bytes"] = len(text)
            record["body_preview"] = preview(text)
            record["authz_error_in_body"] = looks_like_auth_error(text)
            record["location"] = response.headers.get("Location")

            verdict = "unknown"
            if response.status_code >= 500:
                verdict = "server-error"
            elif 200 <= response.status_code < 300:
                verdict = "EXECUTED" if account["name"] == args.baseline else "BUG-CANDIDATE"
            elif response.status_code in (401, 403, 404):
                verdict = "denied" if account["name"] != args.baseline else "baseline-denied?!"
            elif 300 <= response.status_code < 400:
                verdict = "redirect"
            record["verdict"] = verdict

        except requests.RequestException as exc:
            record["status"] = None
            record["verdict"] = "transport-error"
            record["error"] = str(exc)

        results.append(record)

        name = record["account"]
        status = record["status"] if record["status"] is not None else "ERR"
        marker = ""
        if record["verdict"] == "BUG-CANDIDATE":
            marker = "  <<< 2xx from an unauthorized session"
            bug_found = True
        print(f"{name:<12} {str(status):<5} {record['verdict']:<18}{marker}")
        if record.get("body_preview"):
            print(f"{'':<12} {record['body_preview']}")
        if record.get("error"):
            print(f"{'':<12} {record['error']}")

        if bug_found and args.stop_on_bug:
            print("\nstopping early (--stop-on-bug)")
            break
        if index != len(accounts) - 1:
            time.sleep(max(args.delay, 0))

    print()
    baseline_record = next((item for item in results if item["role"] == "baseline"), None)
    if baseline_record and baseline_record.get("status") and not (
        200 <= baseline_record["status"] < 300
    ):
        print("note: the baseline account did not get a 2xx. The captured request may be stale")
        print("      (expired cookies, closed trade window, or the trade already resolved).")
        print("      Fix the baseline before trusting any probe result.")

    if bug_found:
        print("RESULT: at least one non-owning session got a success response.")
        print("        Re-run in a fresh test league to confirm it is deterministic, capture")
        print("        before/after trade state via the read API, then report with those artifacts.")
    else:
        print("RESULT: no authorization bypass observed with this request/body variant.")

    if args.out:
        args.out.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
        print(f"results written to {args.out.resolve()}")

    return 0


if __name__ == "__main__":
    sys.exit(main())

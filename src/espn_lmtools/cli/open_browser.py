"""Open a saved ESPN browser profile in a visible Chromium window."""

from __future__ import annotations

import argparse
import contextlib
import json
import os
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright


def main(argv=None):
    parser = argparse.ArgumentParser(description="Open a persisted ESPN browser profile.")
    parser.add_argument("account", help="profile directory name")
    parser.add_argument("url", nargs="?", default="https://fantasy.espn.com/")
    parser.add_argument("--profile-dir", type=Path, default=Path(".espnprofiles"))
    parser.add_argument(
        "--status", type=Path, default=Path("browser-status.json"), help="status output file"
    )
    parser.add_argument("--screenshot", type=Path, default=Path("browser-ready.png"))
    args = parser.parse_args(argv)

    with sync_playwright() as playwright:
        context = playwright.chromium.launch_persistent_context(
            str(args.profile_dir / args.account),
            headless=False,
            no_viewport=True,
            args=["--start-maximized", "--disable-blink-features=AutomationControlled"],
        )
        page = context.pages[0] if context.pages else context.new_page()
        page.goto(args.url, wait_until="domcontentloaded", timeout=60000)
        page.wait_for_timeout(5000)
        args.status.write_text(
            json.dumps({"url": page.url, "title": page.title(), "pid": os.getpid()}, indent=2),
            encoding="utf-8",
        )
        with contextlib.suppress(Exception):
            page.screenshot(path=str(args.screenshot))
        with contextlib.suppress(Exception):
            page.wait_for_event("close", timeout=0)
        with contextlib.suppress(Exception):
            context.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())


# League Desk (Android)

Native Kotlin + Jetpack Compose client for ESPN Fantasy. No React Native, no web wrapper,
no middle server. One WebView is used only for ESPN sign-in; everything after that is native
and talks directly to ESPN with the imported session cookies.

## Build

Requirements: JDK 21 (Android Studio JBR works), Android SDK 36.

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
Install:

```powershell
adb install -r LeagueDesk.apk
```

`LeagueDesk.apk` at the project root is the ready-to-install debug build.

## Sign in

- Default league: `642667711`, season `2027`, basketball.
- Tap **Sign in with ESPN**. The app opens ESPN's own login page, then imports `SWID` and
  `espn_s2` after the redirect to Fantasy, clears the WebView cookies, and stores the
  session encrypted with an Android Keystore key.
- Use the ESPN account that owns the team in the league. If the account is not a member,
  the app shows a "Switch account" banner instead of hiding the problem.

## What it does

- Teams, rosters, owners, records, waiver ranks, team FPPG, player details, injuries,
  ownership, projections, per-game stats, and season outlook.
- Click a player to inspect; select players to build a trade.
- Propose trades, cancel your offers, accept incoming offers.
- Accept on behalf of another team or drop from another roster with automatic LM
  escalation and restore.
- Pending trade board with both sides, player values, acceptance state, expiration, and
  LM options.
- Completed trade and roster-move activity.
- Player search, position filters, and depth / PPG / alphabetical sorting.

## Parity with `espn_lm_tools.py`

Covered:

- `info`, `teams`, `activity` (transaction side), and the read path of `accept-trade`/`drop`.
- `escalate` / `deescalate` behavior is automatic around actions that need LM.
- `accept-trade`, `drop`, plus `TRADE_PROPOSAL` and cancel from `espn_trade_poc.py`.

Not exposed in the Android UI:

- `members` directory, `rename`, `notify`, and league message topics (the activity screen
  shows transactions/trades, not the communication feed).
- There is no persistent "become LM" toggle; escalation is scoped to a single action and
  restored in a `finally` path, matching the safety behavior of the Python tools.

## Verification

- `RepositorySmokeTest` loads the live league with the brunson cookies: 14 teams, 19 pending
  trades, 37 activity events, real rosters and stats.
- Native APK built and installed on a physical device; ESPN sign-in, session import, team
  detection, roster rendering, and the trade desk were exercised on-device.
- Write transactions were not sent to ESPN during verification to avoid changing the live
  league. The request bodies and LM restore flow are the same ones used by the Python tools.

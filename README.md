# espn-lmtools

espn-lmtools is a project me and AI worked on over the course of a few days. It allows full LM
(League Manager escalation) on ESPN's fantasy platform. Tested mainly on basketball, but the
structure should be the same across sports. Comes with a web app: just supply your ESPN cookies
(`espn_s2`, `SWID`) and it gives you options to force trades, drop other players, and possibly
destructive features like deleting the league (not included in the GUI). I am not responsible for
what you choose to do with this tool, whether it's testing in a league you own or using it in a
friend's league to give yourself players. Have fun!

## What it does

Tooling and evidence from testing ESPN Fantasy league write-authorization.

The toolkit drives the same private endpoints the ESPN web client uses. It reads league
state, proposes and accepts trades, drops players, and can raise or restore the
`isLeagueManager` flag on an account. Everything is exercised against leagues and
accounts the operator controls.

## Install

```bash
python -m pip install -e ".[harvest]"
python -m playwright install chromium
```

Python 3.10+.

## Commands

| command | purpose |
|---|---|
| `espn-lmtools` (alias `espn-lm`) | league toolkit: `info`, `members`, `teams`, `activity`, `escalate`, `deescalate`, `rename`, `accept-trade`, `drop`, `notify` |
| `espn-trade-poc` | end-to-end chain: self-escalate â†’ propose â†’ accept â†’ restore |
| `espn-accept-trade` | list pending offers for one team and accept exactly one, matched by player name or trade id |
| `espn-trade-probe` | replay a captured raw request under several sessions and diff status (generic IDOR probe) |
| `espn-cookie-harvest` | browser login â†’ export `espn_s2` + `SWID` per account, optional `--join-url` |
| `espn-open-browser` | open a persisted profile in a visible Chromium window |
| `espn-dashboard` | local League Desk web UI on `127.0.0.1` |

Common flags: `--league`, `--account` (file stem in the cookies directory),
`--game fba|ffl|flb|fhl`, `--season`, `--cookies-dir`, `--yes` for writes.

```bash
espn-lmtools --league 123456789 --account ownerA members
espn-lmtools --league 123456789 --account ownerA activity --limit 25
espn-lmtools --league 123456789 --account ownerA accept-trade --trade-id abc123 --team 2 --yes
espn-trade-poc --league 123456789 --proposer ownerA --recipient-team 1 --dry-run
espn-trade-probe -r accept.txt --cookies-dir cookies -b ownerA --dry-run
```

## Cookies

ESPN has no scripted login path that survives its bot protections, so sessions are
exported from a real browser as Netscape `cookies.txt` files, one per account:

```
cookies/<account>.txt     # must contain SWID and espn_s2
```

`espn-cookie-harvest` drives Chromium with Playwright, autofills the Disney login form,
and waits for a manual CAPTCHA/2FA step if ESPN asks for one. Each account gets its own
persistent profile, so later runs usually need no login.

`accounts.example.json` shows the alternate JSON format used by `espn-trade-probe -a`.

Cookies, browser profiles, and credential files are gitignored. They are not part of the
published tree.

## Dashboard

```bash
espn-dashboard --league 123456789 --account ownerA
espn-dashboard --port 8787 --no-open
```

The server reads `cookies/<account>.txt` from `--cookies-dir` (default `./cookies`),
serves the UI from `espn_lmtools/web`, and keeps all ESPN traffic server-side. The UI shows
every roster, pending trades, and league activity, and supports proposals, accepts,
cancels, and LM drops with the same automatic escalate/restore behavior as the CLI.
It binds to localhost only.

## Layout

```
src/espn_lmtools/
  client.py        authenticated League client
  cookies.py       Netscape jar parsing/writing
  constants.py     sport, position, lineup, and display tables
  transactions.py  write payload builder
  cli/             command-line entry points
  dashboard/       local web UI, model builders, write actions
  web/             static dashboard assets
research/          archived one-off evidence collection scripts
evidence/          sanitized run artifacts and the vulnerability report
tests/             unit tests for pure helpers
```

## Findings

| # | finding | status |
|---|---|---|
| 1 | `PUT /leagues/{id}/members/{ownSwid}` accepts `isLeagueManager: true` from the client â†’ any member becomes League Manager | **confirmed**, reportable |
| 2 | Escalated LM can accept trades referencing teams it does not own | confirmed |
| 3 | Escalated LM can drop players from other teams (`ROSTER`/`DROP`) | confirmed |
| 4 | Escalated LM can silence another member's notification settings | confirmed |
| 5 | `displayName` is self-editable and not unique â†’ audit attribution spoofable | confirmed |
| 6 | `POST /invites/{inviteId}?memberId={swid}` seats an arbitrary SWID (create-only) | observed, low impact |
| 7 | "Proposer can accept its own trade" as a standalone bug | **retracted** â€” LM-only; non-LM gets 401 |
| 8 | "Privilege change is invisible to the league" | **retracted** â€” appears in Recent Activity as `Assigned/Revoked LM powers to {displayName}` |
| 9 | LM review queues an accepted trade by writing `TRADE_ACCEPT` with `executionType: PROCESS`, keyed to the original `acceptedDate`; `TRADE_UPHOLD` alone only records approval | **confirmed** |

Finding 9 detail:

- accepted 20:58:58, remained `PENDING` after `TRADE_UPHOLD` from the escalated account
- LM uphold from team 1 at 22:32:43 produced:
  - `TRADE_ACCEPT`, `executionType: PROCESS`, `isPending: true`, `processDate`, `acceptedDate`,
    `teamActions: {6: ACCEPTED, 2: APPROVED, 3: VETOED, 4: VETOED}`
  - `TRADE_UPHOLD`, `isLeagueManager: true`, `teamId: 1`
- `TRADE_UPHOLD` with `isLeagueManager: true` from a transiently escalated account does not
  create the PROCESS record
- `isLeagueManager` in `mMembers` read `false` for the account that successfully upheld with
  `isLeagueManager: true`; do not trust that view for privilege checks

## Correctly enforced (negative results)

- Non-LM editing another member â†’ `401 You are not authorized to edit this member`
- Non-LM `isLeagueCreator` / `hasLimitedPrivileges` â†’ `401 System Administrator`
- Non-LM `skipTransactionCounters` â†’ `409` even with the escalated flag
- Non-LM editing another team (`updateTeam`, drop, waiver add) â†’ `401`
- Non-LM `DELETE /invites/{id}`, `POST /draftDetail` â†’ `401`
- Member `id` is not writable (body ignored); PUT to a non-member path â†’ `409`
- Creator removal: UI hides the control, force-click issues no request, `POST/PUT /teams/{id}`
  with empty owners â†’ `400`

## Evidence

- `evidence/hackerone-report.md` â€” full writeup
- `evidence/poc-evidence.json` â€” escalation + trade chain
- `evidence/lm-escalation-evidence.json` â€” privilege-flag control matrix
- `evidence/review_tick_evidence.json`, `evidence/force_review_evidence.json` â€” review-window runs

Identifiers, display names, and session values have been redacted. Raw runs stay local
and are not committed.

## Tests

```bash
python -m pip install -e ".[dev]"
python -m pytest
```

## Notes

These tools target a private API that can change without notice. Use them only against
leagues and accounts you own or are authorized to test.


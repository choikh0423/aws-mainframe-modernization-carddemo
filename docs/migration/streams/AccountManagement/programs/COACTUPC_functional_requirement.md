# COACTUPC (CAUP) — Functional Requirements

**Program:** `app/cbl/COACTUPC.cbl` (4236 lines) · **Transaction:** `CAUP` · **Mapset/Map:** `COACTUP `/`CACTUPA` (`app/bms/COACTUP.bms`)
**Files:** `CXACAIX`, `ACCTDAT` (read + read-for-update + rewrite), `CUSTDAT` (read + read-for-update + rewrite)
**Migrated surface:** `com.carddemo.account.controller.AccountUpdateController`
* `GET  /api/accounts/{accountId}/update` — fetch details (legacy `9000-READ-ACCT`)
* `POST /api/accounts/{accountId}/update/validate` — ENTER on a changed screen (`1200-EDIT-MAP-INPUTS` + `2000-DECIDE-ACTION`)
* `POST /api/accounts/{accountId}/update/save` — F5 (`9600-WRITE-PROCESSING`)
React `pages/account/AccountUpdatePage.js` at `/accounts/update`.

## 1. Entry, AID handling and state
| ID | Requirement | Cite |
|----|-------------|------|
| U-01 | With no commarea, or on arrival from `COMEN01C` without `CDEMO-PGM-REENTER`, both the shared commarea and the program-private commarea are initialised and the state is `ACUP-DETAILS-NOT-FETCHED`. | COACTUPC:880-893 |
| U-02 | Valid AIDs: ENTER always; PF3 always; PF5 **only** in `ACUP-CHANGES-OK-NOT-CONFIRMED`; PF12 **only** when details have been fetched. Anything else is forced to ENTER. | COACTUPC:901-916 |
| U-03 | PF3 → caller tran/program, else `CM00`/`COMEN01C`. | COACTUPC:921-959 |
| U-04 | State transitions are exactly those of `2000-DECIDE-ACTION` (see the analysis §3.3 table). | COACTUPC:2562-2648 |
| U-05 | Info line per state: not-fetched → `Enter or update id of account to update`; show-details / changes-not-ok → `Update account details presented above.`; changes-ok-not-confirmed → `Changes validated.Press F5 to save`; okayed-and-done → `Changes committed to database`; lock-error / failed → `Changes unsuccessful. Please try again`. | COACTUPC:2955-2982 |
| U-06 | Field protection per state: only the account id is enterable before a fetch; all data fields are enterable in show-details / changes-not-ok; everything is protected in changes-ok-not-confirmed and after a successful save. | COACTUPC:2986-3006 |

## 2. Receive-map normalisation
| ID | Requirement | Cite |
|----|-------------|------|
| U-07 | A field containing `'*'` or only spaces is converted to `LOW-VALUES`, i.e. treated as **not supplied**. | COACTUPC:1039-1428 |
| U-08 | Signed amount fields keep their raw text; the numeric value is only taken when `FUNCTION TEST-NUMVAL-C` accepts the text. | COACTUPC:1064-1200, 2201-2215 |

## 3. Search key edit
| ID | Condition | Message | Cite |
|----|-----------|---------|------|
| U-09 | Blank account id | `No input received` | COACTUPC:1441-1443, 1787-1797 |
| U-10 | Non-numeric or zero account id | `Account Number if supplied must be a 11 digit Non-Zero Number` | COACTUPC:1799-1813 |
| U-11 | Fetch order is xref (`CXACAIX`) → `ACCTDAT` → `CUSTDAT`; the not-found texts are identical to CAVW's. | COACTUPC:3608-3800 |
| U-12 | After a successful fetch the values read are stored as the snapshot `ACUP-OLD-DETAILS` used later for change detection and for the stale check. | COACTUPC:3801-3884 |

## 4. Change detection (`1205-COMPARE-OLD-NEW`)
| ID | Requirement | Cite |
|----|-------------|------|
| U-13 | If every field equals its snapshot value, no edits run and the screen shows `No change detected with respect to values fetched.` | COACTUPC:1460-1468, 1681-1782 |
| U-14 | Comparison is case-insensitive for the account status; `TRIM`+case-insensitive for group id, names, address lines, state, country, zip, government id and primary-holder indicator; exact for ids, amounts, dates, phone parts, SSN, EFT id and FICO. | COACTUPC:1684-1782 |

## 5. Field edits
Order and messages: see stream FR-AU-13…FR-AU-28. The generic routines are `1215-EDIT-MANDATORY`, `1220-EDIT-YESNO`, `1225-EDIT-ALPHA-REQD`, `1235-EDIT-ALPHA-OPT`, `1245-EDIT-NUM-REQD`, `1250-EDIT-SIGNED-9V2`, `1260-EDIT-US-PHONE-NUM`, `1265-EDIT-US-SSN`, `1270-EDIT-US-STATE-CD`, `1275-EDIT-FICO-SCORE`, `1280-EDIT-US-STATE-ZIP-CD` (COACTUPC:1824-2560) plus the date copybook `CSUTLDPY`.

| ID | Requirement | Cite |
|----|-------------|------|
| U-15 | Every edit writes its message only when `WS-RETURN-MSG` is still blank ⇒ **first error wins**, but all edits still run (so the field-level "in error" flags are complete). | COACTUPC:1472-1669 |
| U-16 | Date edits accept only centuries 19 and 20, reject 31-day days in Apr/Jun/Sep/Nov, 30 days in February, and 29 February outside leap years. | CSUTLDPY:98-300 |
| U-17 | The date-of-birth edit additionally rejects a date that is not strictly in the past. | CSUTLDPY:329-367 |
| U-18 | Phone numbers are optional as a whole but, once any part is supplied, all three parts are edited; the area code must be a North America general-purpose code from `CSLKPCDY`. | COACTUPC:2225-2426 |
| U-19 | The state/zip cross-edit only runs when both the state and the zip passed their own edits. | COACTUPC:1664-1669 |
| U-20 | When no edit failed, the state becomes `ACUP-CHANGES-OK-NOT-CONFIRMED`. | COACTUPC:1671-1675 |

## 6. Save (`9600-WRITE-PROCESSING`)
| ID | Step | Failure behaviour | Cite |
|----|------|-------------------|------|
| U-21 | READ `ACCTDAT` UPDATE | `Could not lock account record for update`, state `ACUP-CHANGES-OKAYED-LOCK-ERROR` | COACTUPC:3892-3916 |
| U-22 | READ `CUSTDAT` UPDATE | `Could not lock customer record for update`, same lock-error state | COACTUPC:3918-3944 |
| U-23 | `9700-CHECK-CHANGE-IN-REC` | `Record changed by some one else. Please review`, back to show-details with the freshly read values, nothing written | COACTUPC:3945-3953, 4109-4202 |
| U-24 | Build `ACCT-UPDATE-RECORD` — dates re-assembled `YYYY-MM-DD`, amounts into `S9(10)V99` | — | COACTUPC:3955-4000 |
| U-25 | Build `CUST-UPDATE-RECORD` — phones re-assembled `(AAA)PPP-LLLL`, SSN as `9(09)`, DOB `YYYY-MM-DD` | — | COACTUPC:4001-4062 |
| U-26 | REWRITE `ACCTDAT` then REWRITE `CUSTDAT` | on either failure: `Update of record failed` + `EXEC CICS SYNCPOINT ROLLBACK`, so neither row changes | COACTUPC:4065-4103 |
| U-27 | Both rewrites succeed | state `ACUP-CHANGES-OKAYED-AND-DONE`, info line `Changes committed to database` | COACTUPC:2596-2606 |

## 7. Screen contract (`COACTUP.bms`)
Title `Update Account`; same field order as CAVW but enterable; dates split into year/month/day; SSN into 3/2/4; phones into area/prefix/line; footer over three literals: `ENTER=Process F3=Exit`, `F5=Save`, `F12=Cancel`. Lengths: account id 11, status 1, amounts 15, customer id 9, FICO 3, names 25, government id 20, EFT id 10, primary holder 1.

## 8. Migration mapping
| Legacy | Migrated |
|--------|----------|
| `WS-THIS-PROGCOMMAREA` (`ACUP-OLD-DETAILS` / `ACUP-NEW-DETAILS`) | `AccountUpdateRequest.original` + `AccountUpdateRequest.updated` — the snapshot travels in the request instead of a server-side commarea |
| `2000-DECIDE-ACTION` | `AccountUpdateState` enum returned in every response; the client drives ENTER/F5/F12 by calling `validate`, `save`, `GET .../update` |
| `1200-EDIT-MAP-INPUTS` | `AccountUpdateValidator.validate` (same order, first message wins) |
| `9700-CHECK-CHANGE-IN-REC` | `AccountUpdateService.assertUnchanged` against the locked rows |
| READ … UPDATE | `EntityManager.find(..., LockModeType.PESSIMISTIC_WRITE)` inside the `@Transactional` save |
| `SYNCPOINT ROLLBACK` | Transaction rollback via the thrown `AccountUpdateFailedException` |

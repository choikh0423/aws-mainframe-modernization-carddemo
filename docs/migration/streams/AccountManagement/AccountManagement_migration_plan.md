# Account Management (S-02) — Migration Plan

**Stream:** S-02 AccountManagement — `CAVW` (COACTVWC) and `CAUP` (COACTUPC)
**Target:** the consolidated app in `migration/carddemo` (Spring Boot 3.2.5 / Java 17 backend, React frontend).
**Companion docs:** [`AccountManagement_analysis.md`](AccountManagement_analysis.md) (source analysis),
[`AccountManagement_functional_requirement.md`](AccountManagement_functional_requirement.md) (the sign-off oracle),
[`programs/COACTVWC_functional_requirement.md`](programs/COACTVWC_functional_requirement.md),
[`programs/COACTUPC_functional_requirement.md`](programs/COACTUPC_functional_requirement.md).

---

## 1. What was built

### 1.1 Backend — `com.carddemo.account`

| Layer | Type | Legacy counterpart |
|-------|------|--------------------|
| controller | `AccountViewController` — `GET /api/accounts/{accountId}` | CAVW ENTER (COACTVWC:441-520) |
| controller | `AccountUpdateController` — `GET /api/accounts/{accountId}/update`, `POST …/update/validate`, `POST …/update/save` | CAUP ENTER (fetch), ENTER (edit), F5 |
| service | `AccountViewService` | COACTVWC main flow |
| service | `AccountLookupService` | 9200-GETCARDXREF-BYACCT → 9300-GETACCTDATA-BYACCT → 9400-GETCUSTDATA-BYCUST |
| service | `AccountUpdateService` | 1000-SEND / 2000-DECIDE-ACTION / 9600-WRITE-PROCESSING / 9700-CHECK-CHANGE-IN-REC |
| validator | `AccountFilterValidator` | 1210-EDIT-ACCOUNT (both programs, with their two different messages) |
| validator | `AccountUpdateValidator` | 1200-EDIT-MAP-INPUTS, in source order, first message wins |
| validator | `AccountFieldEdits` | the generic edits 1900-…-2200 (mandatory, Y/N, alphabetic, numeric, signed amount) |
| validator | `AccountDateValidator` | `CSUTLDPY` / `CSUTLDWY` date edits including the future-DOB test |
| validator | `UsLookupTables` | `CSLKPCDY` — 410 area codes, 56 state codes, 240 state+zip-prefix combinations, generated from the copybook |
| validator | `AccountChangeDetector` | 1205-COMPARE-OLD-NEW |
| dto | `AccountViewResponse`, `AccountFields`, `AccountUpdateRequest`, `AccountUpdateResponse`, `AccountUpdateState` | map `COACTVW` / `COACTUP` symbolic maps and `ACUP-OLD-DETAILS` / `ACUP-NEW-DETAILS` |
| util | `AccountFormat`, `AccountMessages` | the `PICOUT` edits and every COBOL message literal |
| exception | `AccountFilterException`, `AccountValidationException`, `AccountNotFoundException`, `RecordLockException`, `StaleRecordException`, `AccountUpdateFailedException` + `AccountExceptionHandler` | the `WS-RETURN-MSG` paths, mapped to 400 / 404 / 409 / 500 with the exact text in `ErrorResponse.message` |

No shared entity, repository or Flyway migration was added or changed: `AccountRecord`, `CustomerRecord` and
`CardXrefRecord` in `com.carddemo.common.domain` already cover ACCTDAT, CUSTDAT and CCXREF/CXACAIX, including
the alternate-index read (`CardXrefRepository.findByAcctId`). The stream therefore uses **none** of its reserved
migration number band.

### 1.2 Frontend

- `src/pages/account/AccountViewPage.js` — CAVW.
- `src/pages/account/AccountUpdatePage.js` — CAUP.
- `src/routes/registry.js` — the two S-02 entries' `element` (plus their two imports); nothing else.

Both screens keep the BMS labels, the field order of the maps and the PF lines verbatim
(`  F3=Exit ` for CAVW, `ENTER=Process F3=Exit` / `F5=Save` / `F12=Cancel` for CAUP), and render the
backend's message strings without rewording. CAUP protects its fields once the changes are validated,
enables F5 only in the validated state and F12 only once details are on the screen.

## 2. Boundary decisions

1. **The CAUP conversation is stateless.** COACTUPC keeps `ACUP-OLD-DETAILS` (the fetched snapshot) and
   `ACUP-NEW-DETAILS` (the typed screen) in its own commarea across pseudo-conversational turns. The
   migrated API carries both explicitly: every `POST` body is `{original, updated}` and every response
   carries the state (`SHOW_DETAILS`, `CHANGES_NOT_OK`, `CHANGES_OK_NOT_CONFIRMED`,
   `CHANGES_OKAYED_AND_DONE`). No server-side session state was introduced.
2. **AID keys are client-side.** ENTER/F3/F5/F12 are React handlers; the server enforces what the AID
   handling enforced: F5 cannot commit an unchanged or invalid screen, and each turn re-edits the account
   filter. `CHANGES_FAILED` from the legacy state set is not modelled as a state: a failed save is an error
   response (409/500) carrying the same text, and the next ENTER re-fetches, which is what COACTUPC does.
3. **Locking.** `READ … UPDATE` became `EntityManager.find(…, PESSIMISTIC_WRITE)` inside the `save`
   transaction, account row first, then the customer row — the same order and the same two failure
   messages. On H2 (test profile) row locks are weaker than VSAM's; the stale-record comparison
   (9700-CHECK-CHANGE-IN-REC, re-implemented as `assertUnchanged`) is what actually guarantees that a
   concurrent change is never overwritten, so correctness does not depend on the lock mode.
4. **Rollback.** `SYNCPOINT ROLLBACK` is the `@Transactional` boundary on `AccountUpdateService.save`: any
   failure after the first rewrite throws, so neither row is committed.
5. **Message fidelity over "correctness".** Where the source contradicts itself (see the quirk table
   FR-AQ-01…09) the migrated code reproduces the runtime behaviour, not the declared intent. The dead
   88-level texts are *not* carried over as unused constants; `AccountQuirkTest` pins the behaviour instead.
6. **Diagnostic texts.** `Resp:`/`Reas:` values are formatted exactly as the COBOL `STRING` built them and
   clipped to `WS-RETURN-MSG PIC X(75)`, so `… Reas:0000` is genuinely what the screen showed.
7. **Card screens are out of scope.** COACTUPC's navigation into the card screens (S-03) is not wired; the
   two screens return to the caller/menu on F3 only.

## 3. Parity — legacy vs migrated

Fixtures: `app/data/ASCII/acctdata.txt`, `custdata.txt`, `cardxref.txt` (the same rows the consolidated app
seeds into H2). Account `00000000001` / customer `000000001` is the worked example.

| Path | Legacy (COBOL/BMS) | Migrated | Evidence |
|------|--------------------|----------|----------|
| CAVW valid id | account + customer detail, info `Displaying details of given Account` | identical field set and text | `AccountManagementIntegrationTest#viewShowsTheEditedAccountAndCustomerDetails` |
| CAVW amount edit | `PICOUT='+ZZZ,ZZZ,ZZZ.99'` → `+      2,020.00`, `-        197.13` | same 15-character strings | `AccountFormatTest`, `AccountViewServiceTest` |
| CAVW SSN / ids | `020-97-3888`, ids zero-padded to 11 / 9 | same | same tests |
| CAVW blank / bad / missing id | `No input received` / `Account Filter must  be a non-zero 11 digit number` / `Account:… not found in Cross ref file.  Resp:000000013  Reas:0000` | same text, 400/400/404 | `AccountManagementIntegrationTest`, `AccountViewServiceTest` |
| CAUP fetch | screen fields split as the map splits them (`CCYY MM DD`, SSN 3-2-4, phone 3-3-4) | same split in `AccountFields` | `AccountManagementIntegrationTest#updateFetchReturnsTheSplitScreenFields` |
| CAUP ENTER, nothing changed | `No change detected with respect to values fetched.` | same, before any edit runs | `AccountUpdateServiceTest#validateReportsNoChangeBeforeRunningTheEdits` |
| CAUP ENTER, bad field | only the first failing edit's text | same order, same text | `AccountUpdateValidatorTest`, `AccountManagementIntegrationTest` |
| CAUP ENTER, good change | `Changes validated.Press F5 to save`, fields protected | same, F5 enabled only here | `AccountUpdateServiceTest`, `AccountUpdatePage` |
| CAUP F5 | both rows rewritten, `Changes committed to database` | same; dates `YYYY-MM-DD`, phones `(AAA)PPP-LLLL`, SSN 9 digits | `AccountManagementIntegrationTest#saveRewritesBothRecords` |
| CAUP F5, row moved on | `Record changed by some one else. Please review`, nothing written | same, 409 | `#saveRefusesAStaleSnapshot`, `AccountUpdateServiceTest` |
| CAUP F5, lock refused | `Could not lock account record for update` / `… customer record …` | same, 409 | `AccountUpdateServiceTest` |
| CAUP F12 | re-read and re-present the stored record | client re-issues the fetch | `AccountUpdatePage` |

**Seeded-data note.** The fixture rows do not all satisfy COACTUPC's own edits — account 1's customer has a
FICO score of 274, a zip that does not belong to its state and a second phone whose area code is not in
`CSLKPCDY`. The legacy program behaves the same way: such a record can be *viewed* but cannot be *saved*
until those fields are corrected. The integration test corrects them explicitly rather than relaxing an edit.

## 4. Verification

```
cd migration/carddemo/backend && mvn -B test        # 210 tests, green (77 in com.carddemo.account)
cd migration/carddemo/frontend && npm ci && CI=true npm run build   # green, no warnings
```

## 5. Deliberate deferrals and contradictions

- **Deferred:** navigation from CAUP into the card screens (S-03, out of scope); the account's
  `CDEMO-ACCT-ID` hand-off from other streams' screens is supported as `?accountId=` but only S-02's own
  screens produce it today.
- **Contradiction with the route registry:** the registry lists both S-02 screens with `tranId: 'CM00'`,
  but both programs move their own `LIT-THISTRANID` into `WS-TRANID` and into the map's `TRNNAME` field
  (COACTVWC:145-146, 438; COACTUPC:535-536, 872), so the real screens show `CAVW` and `CAUP`. The page
  components use the source values; the registry lines were left untouched because they belong to the
  shared file and only the `element` field is this stream's to change.
- **Contradiction with the inventory:** none found for S-02; the transactions, programs, maps and files
  listed in `docs/migration/CardDemo_inventory.md` match the source.

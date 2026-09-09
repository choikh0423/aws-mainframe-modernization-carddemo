# COTRTUPC (CTTU) — Functional Requirements

**Source:** `app/app-transaction-type-db2/cbl/COTRTUPC.cbl` (1702 lines), map `CTRTUPA` in `bms/COTRTUP.bms`.
**Target:** `com.carddemo.trantype` — `TransactionTypeMaintenanceController` / `TransactionTypeMaintenanceService`, React page `TransactionTypeUpdatePage`.
Stream-level IDs (FR-U*) are in `../TransactionTypeManagement_functional_requirement.md`.

## 1. Screen contract (verbatim from `bms/COTRTUP.bms`)

| Row/col | Field | Len | Content |
|---|---|---|---|
| 1,1 / 1,7 | label / `TRNNAME` | 5 / 4 | `Tran:` / `CTTU` |
| 1,21 | `TITLE01` | 40 | title line 1 |
| 1,65 / 1,71 | label / `CURDATE` | 5 / 8 | `Date:` / `mm/dd/yy` |
| 2,1 / 2,7 | label / `PGMNAME` | 5 / 8 | `Prog:` / `COTRTUPC` |
| 2,21 | `TITLE02` | 40 | title line 2 |
| 2,65 / 2,71 | label / `CURTIME` | 5 / 8 | `Time:` / `hh:mm:ss` |
| 7,28 | literal | 25 | `Maintain Transaction Type` |
| 12,4 / 12,26 | literal / `TRTYPCD` | 19 / 2 | `Transaction Type  :` / input |
| 14,4 / 14,26 | literal / `TRTYDSC` | 19 / 50 | `Description       :` / input |
| 22,23 | `INFOMSG` | 45 | information line (centred) |
| 23,1 | `ERRMSG` | 78 | error line (centred) |
| 24,1 / 24,23 / 24,33 / 24,43 / 24,69 | PF line | | `ENTER=Process F3=Exit`, `F4=Delete`, `F5=Save`, `F6=Add`, `F12=Cancel` |

`F4`, `F5`, `F6` and `F12` are `DRK` (hidden) in the map and are brightened per state by `3391-SETUP-PFKEY-ATTRS` (`:1397-1423`). **`F6=Add` is never enabled by the program** — dead UI carried over verbatim.

## 2. State machine (`TTUP-DETAILS-FETCH-STATUS`, `:298-327`)

| State | Meaning | Enabled keys |
|---|---|---|
| not fetched (low-values) | ask for a key | ENTER, F3 |
| invalid search keys (`K`) | key failed validation | ENTER, F3 |
| details not found (`X`) | key valid, no row | ENTER, F3, F5, F12 |
| show details (`S`) | row displayed | ENTER, F3, F4, F12 |
| create new record (`R`) | add mode | ENTER, F3, F12 |
| changes not ok (`E`) | edits failed | ENTER, F3 |
| changes ok not confirmed (`N`) | awaiting save | ENTER, F3, F5, F12 |
| changes okayed and done (`C`) | committed | ENTER, F3 |
| changes backed out (`B`) | update cancelled | ENTER, F3 |
| lock error (`L`) / write failed (`F`) | update failed | ENTER, F3 |
| confirm delete (`9`) | awaiting F4 | F3, F4, F12 (ENTER is **not** valid) |
| delete in progress (`8`) / done (`7`) / failed (`6`) | | ENTER, F3 |

Any key outside the enabled set → `Invalid key pressed` (`:577-608`). An unreachable state in `2000-DECIDE-ACTION` abends with culprit `COTRTUPC`, code `0001`, message `UNEXPECTED DATA SCENARIO` (`:1073-1080`).

## 3. Entry conditions

| # | Rule | Source |
|---|---|---|
| E1 | `HANDLE ABEND LABEL(ABEND-ROUTINE)` is established before anything else | :348-350 |
| E2 | `EIBCALEN = 0`, or fresh arrival from `COADM01C` or `COTRTLIC`: state reset to "not fetched" and the empty screen is sent | :366-374, 465-478 |
| E3 | Otherwise the COMMAREA is split into the shared and program-private halves | :375-381 |
| E4 | Terminal states (committed / failed / delete done / delete failed) are collapsed into a fresh-entry simulation on the next keystroke | :405-419 |

## 4. Validation rules

| # | Field | Rule | Message | Source |
|---|---|---|---|---|
| V1 | any input | `*` or all-spaces is treated as empty; both fields are trimmed | — | :652-685 |
| V2 | Transaction Type | required | `Tran Type code must be supplied.` | :907-974 |
| V3 | Transaction Type | numeric only | `Tran Type code must be numeric.` | :907-974 |
| V4 | Transaction Type | must not be zero | `Tran Type code must not be zero.` | :907-974 |
| V5 | Transaction Type | a valid value is normalised via `NUMVAL` and zero-padded to 2 characters (`7` → `07`) | — | :834-842 |
| V6 | Transaction Type | blank on a lookup | `No input received` | :720-726 |
| V7 | Transaction Type | not re-edited while in add mode or awaiting save | — | :712-714 |
| V8 | Description | required | `Transaction Desc must be supplied.` | :849-903 |
| V9 | Description | letters, digits and spaces only, max 50 | `Transaction Desc can have numbers or alphabets only.` | :849-903 |
| V10 | key + description | identical (upper-cased, trimmed, same trimmed length) to the fetched values | `No change detected with respect to values fetched.` | :783-816 |
| V11 | messages | only the first error of a keystroke survives | — | :849-903 |

## 5. Business rules

| # | Rule | Source |
|---|---|---|
| B1 | Update and delete both require an explicit confirmation keystroke (ENTER validates → F5 saves; F4 arms → F4 deletes) | :482-520, 1023-1030 |
| B2 | Add is reached only from a failed lookup via F5 | :503-508, 1053-1055 |
| B3 | The **save path is an UPDATE first**; `SQLCODE +100` falls through to INSERT, which is how new records are created and also silently re-creates a row deleted by someone else | :1531-1592, 1596-1621 |
| B4 | Delete always uses the **fetched** key (`TTUP-OLD-TTYP-TYPE`), not the key currently on screen | :1624-1664 |
| B5 | Every successful write ends with `EXEC CICS SYNCPOINT` | :1555-1560, 1608-1610, 1637-1639 |
| B6 | F12 restores the fetched values (update) or the shown record (delete confirmation) | :999-1008, 1041-1042 |
| B7 | The info line is centred in a 40-character field | :1249-1262 |

## 6. Messages (exact)

**Information:** `Enter transaction type to be maintained`, `Selected transaction type shown above`, `Press F05 to add. F12 to cancel`, `Update transaction type details shown.`, `Enter new transaction type details.`, `Changes validated.Press F5 to save`, `Changes committed to database`, `Changes unsuccessful`, `Delete this record ? Press F4 to confirm`, `Delete successful.`

**Error:** `PF03 pressed.Exiting`, `Invalid key pressed`, `No record found for this key in database`, `No input received`, `No change detected with respect to values fetched.`, `Could not lock record for update`, `Record changed by some one else. Please review`, `Update was cancelled`, `Update of record failed`, `Delete of record failed`, `Delete was cancelled`, `Looks Good.... so far`, `Name can only contain alphabets and spaces` (defined at `:171-172`/`:154-155` but not reachable — the description edit uses the generic alphanumeric text; documented, not "fixed").

**SQL-derived:** `Error accessing: TRANSACTION_TYPE table. SQLCODE:<code>:<sqlerrm>`, `Error updating: TRANSACTION_TYPE Table. SQLCODE:<code>:<sqlerrm>`, `Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:<code>:<sqlerrm>`, `Please delete associated child records first:SQLCODE :<code>:<sqlerrm><sqlerrm>` (SQLERRM is concatenated twice in the source, `:1645-1646`), `Delete failed with message:SQLCODE :<code>:<sqlerrm>`.

## 7. Control flow

| AID | Target | Literal traced | Source |
|---|---|---|---|
| F3 | `XCTL CDEMO-TO-PROGRAM` | caller if known, else `LIT-ADMINPGM` = `COADM01C` / `CA00` | :429-460 |
| all others | `RETURN TRANSID('CTTU')` | `CCARD-NEXT-PROG` = `COTRTUPC` | :560-571 |

## 8. Data access

`SELECT` by key (`:1469-1512`), `UPDATE … SET TR_DESCRIPTION` (`:1531-1592`), `INSERT` (`:1596-1621`), `DELETE` (`:1624-1664`) — all against `CARDDEMO.TRANSACTION_TYPE`, keyed on `TR_TYPE`.

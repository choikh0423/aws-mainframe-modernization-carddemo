# Account Management (S-02) — Source Analysis

**Stream:** S-02 AccountManagement (ONLINE)
**Transactions:** `CAVW` (view account), `CAUP` (update account)
**Programs:** `app/cbl/COACTVWC.cbl` (1194 lines), `app/cbl/COACTUPC.cbl` (4236 lines — the largest program in the estate)
**Maps:** `app/bms/COACTVW.bms` (mapset `COACTVW `, map `CACTVWA`), `app/bms/COACTUP.bms` (mapset `COACTUP `, map `CACTUPA`)
**Copybooks:** `CVACT01Y` (ACCTDAT), `CVCUS01Y` (CUSTDAT), `CVACT03Y` (CCXREF), `COCOM01Y` (commarea), `CSUTLDPY`/`CSUTLDWY` (date edits), `CSLKPCDY` (phone/state/zip lookup tables), `CSMSG01Y`, `COTTL01Y`, `CSDAT01Y`
**Out of scope (S-03):** `COCRDUPC`/`CCUP`, `COCRDLIC`/`CCLI`, `COCRDSLC`/`CCDL` — reachable as navigation targets from both programs but not migrated here.

All line cites below are `FILE:line` against the sources in `app/` at the commit this stream was branched from.

---

## 1. Data layout

### 1.1 ACCTDAT — `CVACT01Y` (300-byte record, key `ACCT-ID` PIC 9(11))
| Field | PIC | Note |
|---|---|---|
| `ACCT-ID` | 9(11) | key |
| `ACCT-ACTIVE-STATUS` | X(01) | Y/N |
| `ACCT-CURR-BAL` | S9(10)V99 | signed |
| `ACCT-CREDIT-LIMIT` | S9(10)V99 | signed |
| `ACCT-CASH-CREDIT-LIMIT` | S9(10)V99 | signed |
| `ACCT-OPEN-DATE` | X(10) | `YYYY-MM-DD` |
| `ACCT-EXPIRAION-DATE` | X(10) | source spelling kept |
| `ACCT-REISSUE-DATE` | X(10) | |
| `ACCT-CURR-CYC-CREDIT` | S9(10)V99 | |
| `ACCT-CURR-CYC-DEBIT` | S9(10)V99 | |
| `ACCT-ADDR-ZIP` | X(10) | not shown on either map |
| `ACCT-GROUP-ID` | X(10) | |

### 1.2 CUSTDAT — `CVCUS01Y` (500-byte record, key `CUST-ID` PIC 9(09))
`CUST-FIRST/MIDDLE/LAST-NAME` X(25), `CUST-ADDR-LINE-1/2/3` X(50), `CUST-ADDR-STATE-CD` X(02), `CUST-ADDR-COUNTRY-CD` X(03), `CUST-ADDR-ZIP` X(10), `CUST-PHONE-NUM-1/2` X(15) stored as `(999)999-9999`, `CUST-SSN` 9(09), `CUST-GOVT-ISSUED-ID` X(20), `CUST-DOB-YYYY-MM-DD` X(10), `CUST-EFT-ACCOUNT-ID` X(10), `CUST-PRI-CARD-HOLDER-IND` X(01), `CUST-FICO-CREDIT-SCORE` 9(03).

### 1.3 CCXREF — `CVACT03Y`
`XREF-CARD-NUM` X(16) (primary key), `XREF-CUST-ID` 9(09), `XREF-ACCT-ID` 9(11). Both programs read it through the **alternate index path `CXACAIX`** keyed on `XREF-ACCT-ID` (COACTVWC:190, COACTUPC:826).

Target tables `accounts`, `customers`, `card_xref` (V1 baseline) already carry all of these columns, including the `expiraion_date` misspelling; **no Flyway migration was needed for this stream**.

---

## 2. CAVW — `COACTVWC.cbl` (view account)

### 2.1 Entry conditions (COACTVWC:416-470)
* `EIBCALEN = 0` → initialise the commarea, `CDEMO-PGM-ENTER`, clear the account filter, send the map with `WS-PROMPT-FOR-INPUT`.
* Re-entry from `COMEN01C` without `CDEMO-PGM-REENTER` → same reset (COACTVWC:431-437).
* Otherwise the commarea plus `WS-THIS-PROGCOMMAREA` are restored and the program re-enters at the receive-map step.

### 2.2 AID handling (COACTVWC:441-470)
Valid AIDs are **ENTER** and **PF3** only; any other AID is silently forced to ENTER (`SET CCARD-AID-ENTER TO TRUE`). PF3 transfers to `CDEMO-FROM-TRANID`/`CDEMO-FROM-PROGRAM` when present, else to `CM00`/`COMEN01C` (COACTVWC:472-520). Before every `XCTL`/`RETURN` the program moves its own literals into the navigation fields: `CAVW`, `COACTVWC`, `COACTVW `, `CACTVWA` (COACTVWC:142-149, 577-587).

### 2.3 Navigation literals (COACTVWC:142-193)
`COACTVWC`/`CAVW`/`COACTVW `/`CACTVWA` (self), `COCRDLIC`/`CCLI`, `COCRDUPC`/`CCUP`, `COCRDSLC`/`CCDL` (card screens — S-03), `COMEN01C`/`CM00` (menu). Files: `ACCTDAT `, `CARDDAT `, `CUSTDAT `, `CARDAIX `, `CXACAIX `.

### 2.4 Account filter validation (COACTVWC:622-680)
1. `LOW-VALUES`/spaces → `FLG-ACCTFILTER-BLANK`, message `Account number not provided` (only if no message set yet).
2. Non-numeric **or** zero → `FLG-ACCTFILTER-NOT-OK`, message `Account Filter must  be a non-zero 11 digit number` (two spaces after "must" — verbatim).
3. Otherwise valid.

CAUP's own account edit (`1210-EDIT-ACCOUNT`, COACTUPC:1783-1818) uses a **different** literal for the same condition: `Account Number if supplied must be a 11 digit Non-Zero Number`. Both are reproduced as-is.

**Quirk (documented, preserved):** the caller (COACTVWC:600-620) overwrites the blank message with `SET NO-SEARCH-CRITERIA-RECEIVED TO TRUE`, so the operator actually sees **`No input received`** for a blank filter; the literal `Account number not provided` is never displayed. The declared 88-levels `SEARCHED-ACCT-ZEROES`/`SEARCHED-ACCT-NOT-NUMERIC` (`Account number must be a non zero 11 digit number`, COACTVWC:120-124) are likewise dead — the active code moves the `Account Filter must  be …` literal instead.

### 2.5 File access order (COACTVWC:687-870)
`9200-GETCARDXREF-BYACCT` (READ `CXACAIX` by account) → `9300-GETACCTDATA-BYACCT` (READ `ACCTDAT`) → `9400-GETCUSTDATA-BYCUST` (READ `CUSTDAT` with `XREF-CUST-ID`). Each read stops the flow on failure.

Not-found / error text is **built at runtime**, not taken from the declared 88s:
* xref `NOTFND` → `Account:<11-digit id> not found in Cross ref file` (COACTVWC:740-748)
* xref other → `Unable to lookup Account:<id> in Cross ref file` (COACTVWC:751-762)
* ACCTDAT `NOTFND` → `Account:<id> not found in Acct Master file` (COACTVWC:791-799)
* ACCTDAT other → `Unable to lookup Account:<id> in Acct Master file`
* CUSTDAT `NOTFND` → `CustId:<9-digit id> not found in Cust Master file` (COACTVWC:842-850)
* CUSTDAT other → `Unable to lookup CustId:<id> in Cust Master file`

The not-found text is assembled as `'Account:' <11-digit id> ' not found in' ' Cross ref file.  Resp:' <resp> ' Reas:' <reas>` (COACTVWC:735-755) — note the two spaces before `Resp:`. Each also appends `Resp: <resp> Reas: <reas>` in the diagnostic tail (COACTVWC:744-747). **Source/inventory contradiction:** the friendly literals `Did not find this account in account card xref file`, `Did not find this account in account master file`, `Did not find associated customer in master file`, `Error reading account card xref File` (COACTVWC:126-138) are declared but never set by the active code.

### 2.6 Screen (COACTVW.bms) and success message
Title `View Account`; header `Tran:`/`Date:`/`Prog:`/`Time:`; `Account Number :` (length 11, `PICIN='99999999999'`); account block `Active Y/N:`, `Opened:`, `Credit Limit        :`, `Expiry:`, `Cash credit Limit   :`, `Reissue:`, `Current Balance     :`, `Current Cycle Credit:`, `Account Group:`, `Current Cycle Debit :`; customer block `Customer id  :`, `SSN:`, `Date of birth:`, `FICO Score:`, `First Name`, `Middle Name: `, `Last Name : `, `Address:`, `State `, `Zip`, `City `, `Country`, `Phone 1:`, `Government Issued Id Ref    : `, `Phone 2:`, `EFT Account Id: `, `Primary Card Holder Y/N:`; footer `  F3=Exit`.
All five amount fields are `PICOUT='+ZZZ,ZZZ,ZZZ.99'` (COACTVW.bms:120,141,162,174,195). SSN is displayed as `999-99-9999` (COACTVWC:1010-1017). Info line on a successful read: `Displaying details of given Account` (COACTVWC:112-113); the empty screen prompts `Enter or update id of account to display`.

---

## 3. CAUP — `COACTUPC.cbl` (update account)

### 3.1 Entry and pseudo-conversational state (COACTUPC:880-893)
`EIBCALEN = 0`, or arrival from `COMEN01C` without `CDEMO-PGM-REENTER`, initialises both the commarea and `WS-THIS-PROGCOMMAREA` and sets `ACUP-DETAILS-NOT-FETCHED`. Otherwise both areas are restored from `DFHCOMMAREA`. The **program-private commarea** carries `ACUP-OLD-DETAILS` (the snapshot of what was read) and `ACUP-NEW-DETAILS` (what the operator typed) across turns — this is the state the REST design has to make explicit.

### 3.2 AID handling (COACTUPC:901-916)
Valid: ENTER, PF3, PF5 **only** in state `ACUP-CHANGES-OK-NOT-CONFIRMED`, PF12 **only** once details have been fetched. Everything else is forced to ENTER. PF3 → caller or `CM00`/`COMEN01C` (COACTUPC:921-959).

### 3.3 State machine (`2000-DECIDE-ACTION`, COACTUPC:2562-2648)
| From | Trigger | To |
|---|---|---|
| `ACUP-DETAILS-NOT-FETCHED` (or PF12 from any fetched state) | valid account filter, reads succeed | `ACUP-SHOW-DETAILS` |
| `ACUP-SHOW-DETAILS` | input error **or** no changes | stays (`ACUP-CHANGES-NOT-OK` set by `1200` on error) |
| `ACUP-SHOW-DETAILS` | changed and valid | `ACUP-CHANGES-OK-NOT-CONFIRMED` |
| `ACUP-CHANGES-OK-NOT-CONFIRMED` + PF5 | `9600-WRITE-PROCESSING` ok | `ACUP-CHANGES-OKAYED-AND-DONE` |
| … | lock failed | `ACUP-CHANGES-OKAYED-LOCK-ERROR` |
| … | rewrite failed | `ACUP-CHANGES-OKAYED-BUT-FAILED` |
| … | record changed underneath | back to `ACUP-SHOW-DETAILS` with `Record changed by some one else. Please review` |
| `ACUP-CHANGES-OKAYED-AND-DONE` | next ENTER | `ACUP-SHOW-DETAILS` (re-reads) |
| lock/failed states | next ENTER | `ACUP-DETAILS-NOT-FETCHED` (fresh search) |

Info line per state (`3250-SETUP-INFOMSG`, COACTUPC:2955-2982):
`ACUP-DETAILS-NOT-FETCHED`/first entry → `Enter or update id of account to update`; `ACUP-SHOW-DETAILS` and `ACUP-CHANGES-NOT-OK` → `Update account details presented above.`; `ACUP-CHANGES-OK-NOT-CONFIRMED` → `Changes validated.Press F5 to save`; `ACUP-CHANGES-OKAYED-AND-DONE` → `Changes committed to database`; lock/failed → `Changes unsuccessful. Please try again`.

### 3.4 Receive-map normalisation (COACTUPC:1039-1428)
Every input field is normalised before validation: a value of `'*'` or spaces becomes `LOW-VALUES` (i.e. "not supplied"), e.g. the account filter at COACTUPC:1051-1058. Signed amounts are only parsed when `FUNCTION TEST-NUMVAL-C` returns zero, otherwise the raw text is kept for the "is not valid" edit.

### 3.5 Field validation order (`1200-EDIT-MAP-INPUTS`, COACTUPC:1429-1680)
Account filter first (`1210-EDIT-ACCOUNT`); when details are already fetched the old/new comparison (`1205-COMPARE-OLD-NEW`, COACTUPC:1681-1782) runs and, if nothing changed, sets `No change detected with respect to values fetched.` and skips all field edits. Otherwise the fields are edited in **exactly** this order, and only the **first** message survives because every edit is guarded by `IF WS-RETURN-MSG-OFF`:

1 Account Status (Y/N) · 2 Open Date · 3 Credit Limit · 4 Expiry Date · 5 Cash Credit Limit · 6 Reissue Date · 7 Current Balance · 8 Current Cycle Credit · 9 Current Cycle Debit · 10 SSN · 11 Date of Birth · 12 FICO · 13 First Name · 14 Middle Name · 15 Last Name · 16 Address Line 1 · 17 State · 18 Zip · 19 City · 20 Country · 21 Phone 1 · 22 Phone 2 · 23 EFT Account Id · 24 Primary Card Holder · 25 State/ZIP cross-check.

### 3.6 Generic edit routines
| Routine | Cite | Messages |
|---|---|---|
| `1210-EDIT-ACCOUNT` | 1783 | blank → `Account number not provided`; non-numeric or zero → `Account Number if supplied must be a 11 digit Non-Zero Number` |
| `1215-EDIT-MANDATORY` | 1824 | `<field> must be supplied.` |
| `1220-EDIT-YESNO` | 1856 | `<field> must be supplied.` / `<field> must be Y or N.` |
| `1225-EDIT-ALPHA-REQD` | 1898 | `<field> must be supplied.` / `<field> can have alphabets only.` (letters and spaces pass) |
| `1230-EDIT-ALPHANUM-REQD` | 1955 | `<field> must be supplied.` / `<field> can have numbers or alphabets only.` |
| `1235-EDIT-ALPHA-OPT` | 2012 | blank is accepted; otherwise as `1225` |
| `1245-EDIT-NUM-REQD` | 2109 | `<field> must be supplied.` / `<field> must be all numeric.` / `<field> must not be zero.` |
| `1250-EDIT-SIGNED-9V2` | 2180 | `<field> must be supplied.` / `<field> is not valid` (`FUNCTION TEST-NUMVAL-C`) |
| `1260-EDIT-US-PHONE-NUM` | 2225 | area/prefix/line-number edits, see below |
| `1265-EDIT-US-SSN` | 2431 | SSN part edits, see below |
| `1270-EDIT-US-STATE-CD` | 2493 | `<field> : is not a valid state code` |
| `1275-EDIT-FICO-SCORE` | 2514 | `<field> : should be between 300 and 850` |
| `1280-EDIT-US-STATE-ZIP-CD` | 2536 | `Invalid zip code for state` |

The `STRING FUNCTION TRIM(name) ': …'` edits produce a space before the colon (e.g. `State : is not a valid state code`), the `' must be supplied.'` family does not — both forms are reproduced byte-for-byte.

Phone edits (COACTUPC:2225-2426, field names `Phone Number 1` / `Phone Number 2`): all three parts blank → the number is not supplied and no edit fires. Otherwise each part is edited in turn (area → prefix → line number), the first message winning:
`<field>: Area code must be supplied.`, `<field>: Area code must be A 3 digit number.`, `<field>: Area code cannot be zero`, `<field>: Not valid North America general purpose area code` (`VALID-GENERAL-PURP-CODE` in `CSLKPCDY`, 410 codes),
`<field>: Prefix code must be supplied.`, `<field>: Prefix code must be A 3 digit number.`, `<field>: Prefix code cannot be zero`,
`<field>: Line number code must be supplied.`, `<field>: Line number code must be A 4 digit number.`, `<field>: Line number code cannot be zero`.

SSN edits (COACTUPC:2431-2492) run `1245-EDIT-NUM-REQD` per part with its own field name, then a range check on part 1:
`SSN: First 3 chars must be supplied.` / `… must be all numeric.` / `… must not be zero.` / `SSN: First 3 chars: should not be 000, 666, or between 900 and 999`;
`SSN 4th & 5th chars …`; `SSN Last 4 chars …`.

FICO (COACTUPC:1545-1556, 2514-2535): numeric, non-zero, then `FICO Score : should be between 300 and 850`.
State must be alphabetic and one of the 56 `VALID-US-STATE-CODE` values; ZIP is edited as a required 5-digit number; when both are individually valid, `state || zip(1:2)` must be one of the 240 `VALID-US-STATE-ZIP-CD2-COMBO` values, else `Invalid zip code for state` (COACTUPC:1664-1669, 2536-2556).

### 3.7 Date edits — `CSUTLDPY.cpy`
`EDIT-DATE-CCYYMMDD` runs year → month → day → combination → `CSUTLDTC` LE check, and for the DOB also `EDIT-DATE-OF-BIRTH`. Messages (`CSUTLDPY:18-367`), with `<field>` = `Account Open Date`, `Account Expiry Date`, `Account Reissue Date`, `Date of Birth`:
`<field> : Year must be supplied.`, `<field> must be 4 digit number.`, `<field> : Century is not valid.` (only 19 and 20 accepted), `<field> : Month must be supplied.`, `<field>: Month must be a number between 1 and 12.`, `<field> : Day must be supplied.`, `<field>:day must be a number between 1 and 31.`, `<field>:Cannot have 31 days in this month.`, `<field>:Cannot have 30 days in this month.`, `<field>:Not a leap year.Cannot have 29 days in this month.`, `<field>:cannot be in the future`.
Leap rule (CSUTLDPY:243-268): when the two-digit year is `00` the century year is divided by 400, otherwise the year is divided by 4 — arithmetically the standard Gregorian rule.

### 3.8 Persisted representation (`9600-WRITE-PROCESSING`, COACTUPC:3888-4107)
Dates are re-assembled as `YYYY-MM-DD` by `STRING`; phones as `(AAA)PPP-LLLL`; SSN as the 9-digit number; amounts move into the packed `S9(10)V99` fields.

Write order: READ `ACCTDAT` UPDATE (lock) → `Could not lock account record for update` on failure; READ `CUSTDAT` UPDATE → `Could not lock customer record for update`; `9700-CHECK-CHANGE-IN-REC` → `Record changed by some one else. Please review`; REWRITE `ACCTDAT` → on failure `Update of record failed` + `SYNCPOINT ROLLBACK`; REWRITE `CUSTDAT` → same failure handling with rollback (COACTUPC:4065-4103).

### 3.9 Stale-record check (`9700-CHECK-CHANGE-IN-REC`, COACTUPC:4109-4202)
Compares the just-locked records with `ACUP-OLD-*`. Account: status, all five amounts, the three dates and `FUNCTION LOWER-CASE(ACCT-GROUP-ID)`. Customer: names/address/country/government id compared case-insensitively after `TRIM`; zip, both phones, SSN, EFT id, primary-holder indicator and FICO compared exactly.

**Quirk (documented, preserved):** the DOB comparison slices the 10-char stored date at `(1:4)`, `(6:2)`, `(9:2)` but the 8-char snapshot at `(1:4)`, `(5:2)`, `(7:2)` (COACTUPC:4174-4179) — i.e. the snapshot is `CCYYMMDD` while the record is `CCYY-MM-DD`. The comparison is therefore correct despite looking asymmetric, and the migrated code compares the same three components.

### 3.10 Screen (COACTUP.bms)
Title `Update Account`; same field order as CAVW but editable, with dates split into year/month/day, SSN into 3/2/4 and phones into area/prefix/line. Footer, verbatim over three fields: `ENTER=Process F3=Exit`, `F5=Save`, `F12=Cancel`. Field lengths: account id 11, status 1, amounts 15 (`+ZZZ,ZZZ,ZZZ.99`), customer id 9, FICO 3, names 25, government id 20, EFT id 10, primary-holder 1.

---

## 4. Control-flow summary
| From | AID | Target program / tran | Cite |
|---|---|---|---|
| CAVW | PF3 | `CDEMO-FROM-*` else `COMEN01C`/`CM00` | COACTVWC:472-520 |
| CAVW | ENTER | self (`COACTVWC`/`CAVW`, map `CACTVWA`) | COACTVWC:577-587 |
| CAUP | PF3 | `CDEMO-FROM-*` else `COMEN01C`/`CM00` | COACTUPC:921-959 |
| CAUP | ENTER/PF5/PF12 | self (`COACTUPC`/`CAUP`, map `CACTUPA`) | COACTUPC:964-1004 |
| CAUP | (declared, unused in this stream) | `COCRDUPC`/`CCUP`, `COCRDLIC`/`CCLI`, `COCRDSLC`/`CCDL` | COACTUPC:770-800 — S-03 |

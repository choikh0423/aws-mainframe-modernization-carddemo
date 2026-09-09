# S-05 UserManagement — source analysis

Scope: the four admin-only CICS screens that maintain the USRSEC security file.

| Tran | Program | Map | Purpose | CSD |
|---|---|---|---|---|
| CU00 | `app/cbl/COUSR00C.cbl` | `app/bms/COUSR00.bms` (`COUSR0A`) | List users, paged, with U/D row select | `app/csd/CARDDEMO.CSD:157,278,450` |
| CU01 | `app/cbl/COUSR01C.cbl` | `app/bms/COUSR01.bms` (`COUSR1A`) | Add a user | `app/csd/CARDDEMO.CSD:161,285,460` |
| CU02 | `app/cbl/COUSR02C.cbl` | `app/bms/COUSR02.bms` (`COUSR2A`) | Update a user | `app/csd/CARDDEMO.CSD:165,292,470` |
| CU03 | `app/cbl/COUSR03C.cbl` | `app/bms/COUSR03.bms` (`COUSR3A`) | Delete a user | `app/csd/CARDDEMO.CSD:169,299,480` |

All four are reached only from the admin menu COADM01C, options 1-4 of `app/cpy/COADM02Y.cpy:26-44`
(`'User List (Security)'`, `'User Add (Security)'`, `'User Update (Security)'`,
`'User Delete (Security)'`). No other program XCTLs into them. The stream owns every write path against
USRSEC; S-01 (`COSGN00C`) only reads it to authenticate.

---

## 1. Data — USRSEC

`app/cpy/CSUSR01Y.cpy:17-23`:

```cobol
01 SEC-USER-DATA.
  05 SEC-USR-ID                 PIC X(08).
  05 SEC-USR-FNAME              PIC X(20).
  05 SEC-USR-LNAME              PIC X(20).
  05 SEC-USR-PWD                PIC X(08).
  05 SEC-USR-TYPE               PIC X(01).
  05 SEC-USR-FILLER             PIC X(23).
```

* 80-byte fixed record, VSAM KSDS keyed on `SEC-USR-ID` (`app/jcl/DUSRSECJ.jcl:62-73`,
  `KEYS(8,0)`, `RECORDSIZE(80,80)`), file name `'USRSEC  '` in every program (e.g. `COUSR00C.cbl:39`).
* `SEC-USR-PWD` is clear text; `COSGN00C` compares it literally.
* `SEC-USR-TYPE` is a single character; `'A'` = administrator, `'U'` = ordinary user
  (`app/cpy/COCOM01Y.cpy:27-29` 88-levels). **No program in this stream validates it** — see the quirks
  section.
* `SEC-USR-FILLER` carries nothing; it is never referenced.
* The 10 seed records are the in-stream `SYSUT1` data of `app/jcl/DUSRSECJ.jcl:34-43`
  (`ADMIN001`…`ADMIN005` type `A`, `USER0001`…`USER0005` type `U`, every password `PASSWORD`).
  There is **no `usrsec` file under `app/data/ASCII/`** — that directory holds the nine business files
  only; the security file's fixture lives in the JCL. The consolidated app already seeds exactly those
  10 rows into `sec_users`
  (`migration/carddemo/backend/src/main/resources/db/seed/R__seed_carddemo_data.sql:697-708`).

Access pattern per program:

| Program | CICS calls against USRSEC |
|---|---|
| COUSR00C | `STARTBR` (`:588`), `READNEXT` (`:621`), `READPREV` (`:655`), `ENDBR` (`:689`) |
| COUSR01C | `WRITE` (`:240`) |
| COUSR02C | `READ … UPDATE` (`:322`), `REWRITE` (`:360`) |
| COUSR03C | `READ … UPDATE` (`:269`), `DELETE` (`:307`) |

`RESP`/`RESP2` are checked after every call and mapped to a screen message; there is no ABEND path.

---

## 2. Common shape of the four programs

Every program is pseudo-conversational and has the same skeleton (`COUSR00C.cbl:98-144` and the
equivalents):

1. `SET ERR-FLG-OFF`, blank `WS-MESSAGE` and `ERRMSGO`.
2. `IF EIBCALEN = 0` → `MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM`, `RETURN-TO-PREV-SCREEN` (XCTL). Entering a
   screen with no COMMAREA therefore bounces to sign-on.
3. Else copy `DFHCOMMAREA` into `CARDDEMO-COMMAREA` (`app/cpy/COCOM01Y.cpy`) and branch on
   `CDEMO-PGM-REENTER`:
   * first pass — `MOVE LOW-VALUES` to the output map, set the cursor, send the screen;
   * re-entry — `RECEIVE` the map, then `EVALUATE EIBAID`.
4. `EXEC CICS RETURN TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA)`.

`RETURN-TO-PREV-SCREEN` defaults `CDEMO-TO-PROGRAM` to `'COSGN00C'` when it is blank, records
`WS-TRANID`/`WS-PGMNAME` in `CDEMO-FROM-*`, and `XCTL`s (`COUSR00C.cbl:506-517`). Every XCTL target in
this stream is a **literal** — `'COSGN00C'`, `'COADM01C'`, `'COUSR02C'`, `'COUSR03C'` — or
`CDEMO-FROM-PROGRAM` echoed back (CU02/CU03 PF3). There are no `LINK`s and no dynamic program names.

`WS-TRANID`/`WS-PGMNAME` per program: `CU00`/`COUSR00C`, `CU01`/`COUSR01C`, `CU02`/`COUSR02C`,
`CU03`/`COUSR03C` (each program's `WS-VARIABLES`, e.g. `COUSR00C.cbl:36-37`).

Screen framing is identical in all four maps: row 1 `Tran:`/title/`Date:`, row 2 `Prog:`/title/`Time:`,
the screen title in row 4, `ERRMSG` (78 chars, red) at row 23, the PF-key line (yellow) at row 24
(`app/bms/COUSR00.bms:26-89`, `:449-458`).

---

## 3. CU00 — COUSR00C, List Users

### Screen (`app/bms/COUSR00.bms`)

* Title `List Users` (`:79`), `Page:` + `PAGENUM` (`:84-89`), `Search User ID:` + 8-char unprotected
  `USRIDIN` (`:94-99`).
* Column headings `Sel`, `User ID `, `     First Name     `, `     Last Name      `, `Type`
  (`:107-127`) over dashed rules `---`, `--------`, `--------------------`,
  `--------------------`, `----` (`:132-152`).
* Ten row groups `SELnnnn` (1 char, unprotected), `USRIDnn` (8), `FNAMEnn` (20), `LNAMEnn` (20),
  `UTYPEnn` (**1** char) at rows 10-19 (`:153-442`).
* Row 21 `Type 'U' to Update or 'D' to Delete a User from the list` (`:443-448`).
* Row 24 `ENTER=Continue  F3=Back  F7=Backward  F8=Forward` (`:453-458`).

### Paging state

Carried in the COMMAREA extension `CDEMO-CU00-INFO` (`COUSR00C.cbl:67-76`):
`CDEMO-CU00-USRID-FIRST`, `CDEMO-CU00-USRID-LAST`, `CDEMO-CU00-PAGE-NUM`, `CDEMO-CU00-NEXT-PAGE-FLG`,
`CDEMO-CU00-USR-SEL-FLG`, `CDEMO-CU00-USR-SELECTED`. `USRID-FIRST` is set from row 1 and `USRID-LAST`
from row 10 while the page is populated (`:387-388`, `:433-435`).

### Key handling (`:122-137`)

| Key | Action |
|---|---|
| ENTER | `PROCESS-ENTER-KEY` |
| PF3 | `MOVE 'COADM01C' TO CDEMO-TO-PROGRAM`, XCTL |
| PF7 | `PROCESS-PF7-KEY` |
| PF8 | `PROCESS-PF8-KEY` |
| other | `CCDA-MSG-INVALID-KEY` = `Invalid key pressed. Please see below...` (`app/cpy/CSMSG01Y.cpy:21-22`) |

### `PROCESS-ENTER-KEY` (`:149-232`)

1. An `EVALUATE TRUE` walks `SEL0001I`…`SEL0010I` top-down and takes the **first** non-blank one into
   `CDEMO-CU00-USR-SEL-FLG` / `CDEMO-CU00-USR-SELECTED`; lower rows are ignored (`:151-185`).
2. If a flag and an id were captured (`:187-215`):
   * `'U'`/`'u'` → `CDEMO-TO-PROGRAM = 'COUSR02C'`, `CDEMO-PGM-CONTEXT = 0`, XCTL with the COMMAREA;
   * `'D'`/`'d'` → same to `'COUSR03C'`;
   * anything else → `WS-MESSAGE = 'Invalid selection. Valid values are U and D'` and execution falls
     through to the re-list below.
3. `SEC-USR-ID` is set from `USRIDINI` (the search field), or `LOW-VALUES` when it is blank (`:217-222`).
4. `CDEMO-CU00-PAGE-NUM = 0`, then `PROCESS-PAGE-FORWARD` (`:227-228`) — so ENTER always restarts the
   browse at page 1, including after an invalid selection.

### `PROCESS-PAGE-FORWARD` (`:282-331`)

`STARTBR` on `SEC-USR-ID` (GTEQ — the `GTEQ` operand is commented out at `:592`, and GTEQ is the CICS
default, so the browse positions at the first key **>=** the search value). Then:

* `IF EIBAID NOT = DFHENTER AND DFHPF7 AND DFHPF3` → one throwaway `READNEXT` (`:286-288`). Only PF8
  satisfies this, so PF8 skips the record it was positioned on (`CDEMO-CU00-USRID-LAST`) and the next
  page starts strictly **after** the last id shown; ENTER keeps the record, so its page starts **at**
  the search value.
* Blank the 10 rows, then `READNEXT`+`POPULATE-USER-DATA` until 10 rows or EOF.
* One extra `READNEXT` decides `CDEMO-CU00-NEXT-PAGE-FLG` (`:305-312`); `CDEMO-CU00-PAGE-NUM` is
  incremented when the page held anything.
* `ENDBR`, display the page number, send the screen.

### `PROCESS-PAGE-BACKWARD` (`:336-379`)

`STARTBR` on `CDEMO-CU00-USRID-FIRST`, one throwaway `READPREV` (PF7 is the only key that reaches here),
then `READPREV` filling rows 10 down to 1 — i.e. the 10 rows immediately **before** the first id shown,
redisplayed in ascending order. A further `READPREV` decides whether `PAGE-NUM` is decremented or reset
to 1 (`:361-370`).

### Boundary and error messages

| Condition | Message | Cite |
|---|---|---|
| PF7 while `PAGE-NUM <= 1` | `You are already at the top of the page...` | `:249-254` |
| PF8 while `NEXT-PAGE-NO` | `You are already at the bottom of the page...` | `:271-276` |
| `STARTBR` NOTFND | `You are at the top of the page...` | `:600-607` |
| `READNEXT` ENDFILE | `You have reached the bottom of the page...` | `:634-641` |
| `READPREV` ENDFILE | `You have reached the top of the page...` | `:668-675` |
| any other RESP on `STARTBR`/`READNEXT`/`READPREV` | `Unable to lookup User...` | `:608-613`, `:642-647`, `:676-681` |
| non-U/D selection | `Invalid selection. Valid values are U and D` | `:214-218` |

---

## 4. CU01 — COUSR01C, Add User

### Screen (`app/bms/COUSR01.bms`)

Title `Add User` (`:79`); fields in screen order — `First Name:` + `FNAME` (20, unprotected, `IC`
cursor) (`:80-88`), `Last Name:` + `LNAME` (20) (`:92-101`), `User ID:` + `USERID` (8) + `(8 Char)`
(`:106-120`), `Password:` + `PASSWD` (8, `DRK` = non-display) + `(8 Char)` (`:121-135`),
`User Type: ` + `USRTYPE` (1) + `(A=Admin, U=User)` (`:136-150`). Row 24:
`ENTER=Add User  F3=Back  F4=Clear  F12=Exit` (`:155-159`).

### Key handling (`:90-103`)

ENTER → `PROCESS-ENTER-KEY`; PF3 → `'COADM01C'`; PF4 → `CLEAR-CURRENT-SCREEN`; anything else →
`CCDA-MSG-INVALID-KEY`. **PF12 is not handled** even though the map's PF line advertises `F12=Exit`, so
PF12 produces the invalid-key message.

### Validation (`:115-152`) — an `EVALUATE TRUE`, first failure wins

| # | Test | Message |
|---|---|---|
| 1 | `FNAMEI = SPACES OR LOW-VALUES` | `First Name can NOT be empty...` |
| 2 | `LNAMEI` blank | `Last Name can NOT be empty...` |
| 3 | `USERIDI` blank | `User ID can NOT be empty...` |
| 4 | `PASSWDI` blank | `Password can NOT be empty...` |
| 5 | `USRTYPEI` blank | `User Type can NOT be empty...` |

That is the **whole** edit set: no length, character-set, domain or duplicate-name rule.

### Write (`:154-160`, `:238-274`)

The five map fields are MOVEd into `SEC-USER-DATA` and `EXEC CICS WRITE`n. Outcomes:

* `NORMAL` → `INITIALIZE-ALL-FIELDS` (all inputs blanked, cursor back on First Name), `ERRMSGC = DFHGREEN`
  and `STRING 'User ' / SEC-USR-ID DELIMITED BY SPACE / ' has been added ...'` → e.g.
  `User NEWUSER1 has been added ...` (`:250-259`).
* `DUPKEY`/`DUPREC` → `User ID already exist...` (`:260-266`).
* other → `Unable to Add User...` (`:267-272`).

---

## 5. CU02 — COUSR02C, Update User

### Screen (`app/bms/COUSR02.bms`)

Title `Update User` (`:79`), `Enter User ID:` + `USRIDIN` (8, `IC`) (`:80-89`), a 70-char yellow
`*` separator rule (`:93-97`), then `First Name:` (20), `Last Name:` (20), `Password:` (8, `DRK`) +
`(8 Char)`, `User Type: ` (1) + `(A=Admin, U=User)` (`:98-154`). All five inputs are unprotected. Row 24:
`ENTER=Fetch  F3=Save&Exit  F4=Clear  F5=Save  F12=Cancel` (`:159-164`; the map source writes `&&`,
which BMS renders as a single `&`).

### Entry from CU00 (`:96-104`)

On the first pass, if `CDEMO-CU02-USR-SELECTED` is non-blank it is moved into `USRIDINI` and
`PROCESS-ENTER-KEY` runs immediately, so selecting `U` on the list lands on a populated screen.

### Key handling (`:108-131`)

| Key | Action |
|---|---|
| ENTER | `PROCESS-ENTER-KEY` — fetch the record |
| PF3 | `UPDATE-USER-INFO` **then** XCTL to `CDEMO-FROM-PROGRAM` (or `'COADM01C'` when blank) |
| PF4 | `CLEAR-CURRENT-SCREEN` |
| PF5 | `UPDATE-USER-INFO`, stay on the screen |
| PF12 | XCTL `'COADM01C'` without saving |
| other | `CCDA-MSG-INVALID-KEY` |

### `PROCESS-ENTER-KEY` (`:143-172`)

Blank user id → `User ID can NOT be empty...`. Otherwise the four data fields are blanked, the record is
`READ … UPDATE`n and, on success, First/Last/Password/Type are painted from the record and the screen
shows `Press PF5 key to save your updates ...` (`:334-339`). `NOTFND` → `User ID NOT found...`
(`:340-346`); any other RESP → `Unable to lookup User...` (`:347-352`).

### `UPDATE-USER-INFO` (`:177-245`)

Edits, first failure wins: user id, first name, last name, password, user type — each
`… can NOT be empty...`. Then the record is re-read and each of the four data fields is compared with the
record; a difference is copied in and sets `USR-MODIFIED-YES`. If nothing differs the screen shows
`Please modify to update ...` in red and **no** write happens (`:238-243`). Otherwise `REWRITE`:

* `NORMAL` → green `User <id> has been updated ...` (`:368-376`);
* `NOTFND` → `User ID NOT found...` (`:377-383`);
* other → `Unable to Update User...` (`:384-389`).

---

## 6. CU03 — COUSR03C, Delete User

### Screen (`app/bms/COUSR03.bms`)

Title `Delete User` (`:79`), `Enter User ID:` + `USRIDIN` (8, `IC`, the **only** unprotected field)
(`:80-89`), the `*` rule, then `First Name:` (20, `ASKIP`), `Last Name:` (20, `ASKIP`), `User Type: ` (1,
`ASKIP`) + `(A=Admin, U=User)` (`:93-139`). There is **no password field**. Row 24:
`ENTER=Fetch  F3=Back  F4=Clear  F5=Delete` (`:144-148`).

### Entry from CU00 (`:96-105`)

Same as CU02, driven by `CDEMO-CU03-USR-SELECTED`.

### Key handling (`:108-130`)

ENTER → fetch; PF3 → `CDEMO-FROM-PROGRAM` or `'COADM01C'` (no delete); PF4 → clear; PF5 →
`DELETE-USER-INFO`; PF12 → `'COADM01C'`; other → `CCDA-MSG-INVALID-KEY`.

### Fetch (`:142-169`, `:267-300`)

Blank id → `User ID can NOT be empty...`. On a good `READ … UPDATE` the three display fields are painted
and the screen shows `Press PF5 key to delete this user ...` (`:281-286`); `NOTFND` →
`User ID NOT found...`; other → `Unable to lookup User...`.

### Delete (`:174-192`, `:305-336`)

Blank id → `User ID can NOT be empty...`; otherwise `READ … UPDATE` followed unconditionally by
`DELETE`. Outcomes: `NORMAL` → fields cleared + green `User <id> has been deleted ...`; `NOTFND` →
`User ID NOT found...`; other → `Unable to Update User...`.

---

## 7. Navigation graph

```
COADM01C ──1──> COUSR00C ──'U'──> COUSR02C ──PF3──> COUSR00C (CDEMO-FROM-PROGRAM)
   │  │  │  └───'D'──> COUSR03C ──PF3──> COUSR00C
   │  │  └──2──> COUSR01C ──PF3──> COADM01C
   │  └─────3──> COUSR02C ──PF3/PF12──> COADM01C (no CDEMO-FROM-PROGRAM)
   └────────4──> COUSR03C ──PF3/PF12──> COADM01C
any screen with EIBCALEN = 0 ──> COSGN00C
```

---

## 8. Quirks (preserved, not fixed)

| Q | Behaviour | Cite |
|---|---|---|
| Q1 | The user **type is never validated**. Any single non-blank character is written and rewritten, so `X`, `9` or lowercase `a` all become valid types even though the caption says `(A=Admin, U=User)`. | `COUSR01C.cbl:142-146`, `COUSR02C.cbl:204-208` |
| Q2 | The user id is not checked for length or character set, and the password has no format or strength rule. Passwords are stored in clear text. | `COUSR01C.cbl:115-160`, `CSUSR01Y.cpy:21` |
| Q3 | CU03's generic delete failure reports `Unable to Update User...` — the delete screen borrows the update program's literal. | `COUSR03C.cbl:330-334` |
| Q4 | CU02 PF3 is `Save&Exit`: it runs the full update and then XCTLs regardless of the outcome, so a validation failure or `User ID NOT found...` is discarded unseen. | `COUSR02C.cbl:111-119` |
| Q5 | CU02 `UPDATE-USER-INFO` does not test the error flag between the `READ` and the comparison, so a PF5 against a missing id still runs the compare and the `REWRITE`; the `NOTFND` from the rewrite reproduces the same message the read produced. | `COUSR02C.cbl:215-245` |
| Q6 | CU03 `DELETE-USER-INFO` likewise performs `READ` then `DELETE` unconditionally. | `COUSR03C.cbl:188-192` |
| Q7 | CU03 deletes with no confirmation prompt, no protection for the signed-on administrator and no "last admin" guard. | `COUSR03C.cbl:121-122` |
| Q8 | CU00 honours only the **first** non-blank `SEL` field, top-down; flags on lower rows are silently ignored. | `COUSR00C.cbl:151-185` |
| Q9 | CU00 falls through to a fresh page-1 browse after an invalid selection, so the message is shown over a re-listed first page rather than the page the user was on. | `COUSR00C.cbl:214-228` |
| Q10 | CU01's PF-key line advertises `F12=Exit` but the program has no PF12 branch, so PF12 yields `Invalid key pressed. Please see below...`. | `COUSR01C.cbl:90-103` |
| Q11 | `WS-USER-DATA` declares a `USER-TYPE PIC X(08)` row field, but the map's `UTYPEnn` is 1 character and the record is written straight from `SEC-USR-TYPE`; the working-storage table is dead code. | `COUSR00C.cbl:56-63`, `COUSR00.bms:177-181` |
| Q12 | The user id shown in the success messages is `DELIMITED BY SPACE`, so an id containing an embedded blank would be truncated at that blank. | `COUSR01C.cbl:255-258` |
| Q13 | A backward page can populate fewer than 10 rows, which would leave the **top** rows blank because the loop fills row 10 downwards. It is unreachable in practice: PF7 is only honoured when `PAGE-NUM > 1`, which implies 10 preceding records. | `COUSR00C.cbl:349-360` |

---

## 9. Where the source contradicts the inventory

* `docs/migration/CardDemo_inventory.md:127` lists the stream's data as USRSEC only, which the source
  confirms — but the fixture directory `app/data/ASCII/` contains **no** USRSEC extract. The only user
  fixture in the repo is the in-stream data of `app/jcl/DUSRSECJ.jcl:34-43`, which is what the
  consolidated app already seeds. Tests in this stream use those 10 rows.
* Nothing else in the inventory conflicts with the source for S-05.

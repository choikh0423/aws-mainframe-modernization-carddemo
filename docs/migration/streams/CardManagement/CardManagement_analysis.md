# S-03 CardManagement — Source analysis

**Stream:** S-03 CardManagement (ONLINE)
**Transactions / programs / maps:**

| Tran | Program | Mapset | Map | Screen |
|------|---------|--------|-----|--------|
| CCLI | `app/cbl/COCRDLIC.cbl` | `COCRDLI` | `CCRDLIA` | List Credit Cards |
| CCDL | `app/cbl/COCRDSLC.cbl` | `COCRDSL` | `CCRDSLA` | View Credit Card Detail |
| CCUP | `app/cbl/COCRDUPC.cbl` | `COCRDUP` | `CCRDUPA` | Update Credit Card Details |

**Files:** `CARDDAT` (VSAM KSDS, key = 16-char card number, layout `CVACT02Y`), `CARDAIX`
(alternate index path over `CARDDAT` keyed by 11-digit account id). `CCXREF` / `CVACT03Y` is
declared in the estate inventory for this stream but **no program in the stream reads it** (see
§7 quirks).

**Deferred:** transaction `CDV1` / program `COCRDSEC`. The CSD defines the transaction, but the
program source does not exist in the repository — inventory risk **R-1**, boundary register
**B-11**. It is not migrated and nothing is invented in its place.

---

## 1. Record layout — `CVACT02Y` CARD-RECORD (RECLN 150)

| Field | Picture | Notes |
|-------|---------|-------|
| `CARD-NUM` | `X(16)` | primary key of `CARDDAT` |
| `CARD-ACCT-ID` | `9(11)` | alternate key (`CARDAIX`) |
| `CARD-CVV-CD` | `9(03)` | never displayed on any S-03 screen; carried through the update |
| `CARD-EMBOSSED-NAME` | `X(50)` | |
| `CARD-EXPIRAION-DATE` | `X(10)` | `YYYY-MM-DD` (spelling of the copybook preserved) |
| `CARD-ACTIVE-STATUS` | `X(01)` | `Y` / `N` |
| `FILLER` | `X(59)` | |

The update program slices the expiry date positionally: `(1:4)` year, `(6:2)` month, `(9:2)` day
(`COCRDUPC.cbl:1361-1366`, `1505-1507`).

## 2. CCLI — `COCRDLIC` (List Credit Cards)

### 2.1 Entry conditions
* `EIBCALEN = 0` → fresh start: COMMAREA initialised, `CA-FIRST-PAGE`, `CA-LAST-PAGE-NOT-SHOWN`
  (`COCRDLIC.cbl:315-325`).
* Re-entry with `CDEMO-PGM-ENTER` and `CDEMO-FROM-PROGRAM ≠ COCRDLIC` (i.e. arriving from the
  menu) → the private commarea is re-initialised and the browse restarts at page 1
  (`:336-343`).
* Re-entry with `CDEMO-FROM-PROGRAM = COCRDLIC` → the map is received and edited (`:357-362`).

### 2.2 Valid AIDs
ENTER, PF3, PF7, PF8 (`:370-380`). Any other AID is silently remapped to ENTER — pressing e.g.
PF5 behaves exactly like ENTER.

### 2.3 Screen layout — `app/bms/COCRDLI.bms`
Title `List Credit Cards`; search fields `Account Number    :` (`ACCTSID`, 11) and
`Credit Card Number:` (`CARDSID`, 16); column headings `Select    `, `Account Number`,
` Card Number `, `Active `; **7** detail rows (`CRDSELn` 1, `ACCTNOn` 11, `CRDNUMn` 16,
`CRDSTSn` 1); `INFOMSG` 45; `ERRMSG` 78; PF caption `  F3=Exit F7=Backward  F8=Forward`.
`WS-MAX-SCREEN-LINES = 7` (`COCRDLIC.cbl:177-178`).

### 2.4 Field-by-field validation (`2200-EDIT-INPUTS`, `:985-1115`)
* **Account filter** (`2210-EDIT-ACCOUNT`, `:1003-1029`): LOW-VALUES / spaces / all-zeroes → the
  filter is *blank* (no error, no filtering). Otherwise, if not numeric →
  `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`, `INPUT-ERROR`, and the row select
  fields are protected. Otherwise the filter is valid.
* **Card filter** (`2220-EDIT-CARD`, `:1036-1066`): same shape;
  `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` (only if the error line is still empty —
  the account message wins).
* **Selection flags** (`2250-EDIT-ARRAY`, `:1073-1115`): skipped entirely when a filter error was
  already raised. `INSPECT … TALLYING FOR ALL 'S' ALL 'U'`; more than one → `INPUT-ERROR` and
  `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE`. Then, per row: `S`/`U` (88 `SELECT-OK`,
  `:77-79`) records the row index into `I-SELECTED`; space/LOW-VALUES is ignored; anything else →
  `INPUT-ERROR` and `INVALID ACTION CODE` (only if the error line is still empty).
  `I-SELECTED` keeps the **last** valid flag row, not the first (`:1099-1105`).

### 2.5 Browse / paging (`9000-READ-FORWARD` `:1123-1260`, `9100-READ-BACKWARDS` `:1264-1372`)
`STARTBR CARDDAT GTEQ` from `WS-CARD-RID-CARDNUM`, then `READNEXT` until 7 rows pass
`9500-FILTER-RECORDS` (`:1382-1407`: account filter equality, then card filter equality —
client-side filtering of the browse, not a keyed read). After the 7th row a look-ahead
`READNEXT` decides `CA-NEXT-PAGE-EXISTS`; `ENDFILE` on the look-ahead or in the loop sets
`CA-NEXT-PAGE-NOT-EXISTS` and, if the error line is empty, `NO MORE RECORDS TO SHOW`
(`:1215-1221`, `:1233-1240`). Page 1 with zero rows sets `NO RECORDS FOUND FOR THIS SEARCH
CONDITION.` in `WS-INFO-MSG` (`:1241-1245`), but `1100-SCREEN-INIT` re-initialises that field
(`SET WS-NO-INFO-MESSAGE TO TRUE`, `:668-670`) before `1400-SETUP-MESSAGE` looks at it, so the
literal is dead — an empty result shows `NO MORE RECORDS TO SHOW` plus the normal info line
(quirk Q-11). Backward paging re-browses from the current first key and fills the
7 slots bottom-up with `READPREV` (`:1294-1371`).

PF8 advances `WS-CA-SCREEN-NUM` only when `CA-NEXT-PAGE-EXISTS` (`:486-497`); PF7 decrements only
when not on page 1 (`:501-513`); PF7 on page 1 re-reads the same page (`:444-454`). Any AID other
than PF7/PF8 resets `CA-LAST-PAGE-NOT-SHOWN` (`:410-414`).

### 2.6 Message selection (`1400-SETUP-MESSAGE`, `:895-932`)
Precedence, top to bottom:
1. account/card filter error → keep that error, no info line;
2. PF7 on the first page → `NO PREVIOUS PAGES TO DISPLAY`;
3. PF8, no next page, last page already shown → `NO MORE PAGES TO DISPLAY`;
4. PF8, no next page (first time) → info `TYPE S FOR DETAIL, U TO UPDATE ANY RECORD` and mark the
   last page as shown;
5. otherwise → info `TYPE S FOR DETAIL, U TO UPDATE ANY RECORD`.
The info line is suppressed when no records were found (`:926-930`).

### 2.7 Control flow
| Trigger | Target | Cite |
|---------|--------|------|
| PF3 (from itself) | `XCTL COMEN01C` (`LIT-MENUPGM`), `CDEMO-TO-PROGRAM = COMEN01C`, message `PF03 PRESSED.EXITING` | `:384-406` |
| ENTER + `S` on row *n* | `CCARD-NEXT-PROG = COCRDSLC` (`LIT-CARDDTLPGM`), mapset `COCRDSL`, map `CCRDSLA`, `CDEMO-ACCT-ID`/`CDEMO-CARD-NUM` = that row → `XCTL` | `:517-541` |
| ENTER + `U` on row *n* | `CCARD-NEXT-PROG = COCRDUPC` (`LIT-CARDUPDPGM`), mapset `COCRDUP`, map `CCRDUPA`, same row values → `XCTL` | `:545-569` |
| anything else | re-browse from the current first key and resend the map | `:572-582` |

## 3. CCDL — `COCRDSLC` (View Credit Card Detail)

### 3.1 Entry conditions (`COCRDSLC.cbl:268-279`, `:304-381`)
* `EIBCALEN = 0`, or arrival from `COMEN01C` without re-entry → empty commarea, prompt screen.
* `CDEMO-PGM-ENTER` **and** `CDEMO-FROM-PROGRAM = COCRDLIC` → keys already validated: account and
  card are taken from the commarea, the card is read and displayed (`:339-348`).
* `CDEMO-PGM-ENTER` from any other context → prompt screen (`:349-356`).
* `CDEMO-PGM-REENTER` → receive + edit + read (`:357-371`).

### 3.2 Valid AIDs
ENTER and PF3 only; everything else is remapped to ENTER (`:291-299`).

### 3.3 Screen layout — `app/bms/COCRDSL.bms`
Title `View Credit Card Detail`; `Account Number    :` (11), `Card Number       :` (16),
`Name on card      :` (50), `Card Active Y/N   : ` (1), `Expiry Date       : ` (month 2, year 4,
day 2 protected/dark); PF caption `ENTER=Search Cards  F3=Exit`.

### 3.4 Input normalisation and validation
`*` or spaces in either key field → LOW-VALUES (`:615-627`). Then:
* account (`2210-EDIT-ACCOUNT`, `:647-679`): blank/zero → `Account number not provided`;
  non-numeric → `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`;
* card (`2220-EDIT-CARD`, `:685-720`): blank/zero → `Card number not provided`; non-numeric →
  `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER`;
* both blank → `No input received` (cross-field edit, `:637-640`), which overwrites the two
  "not provided" prompts because the flags are re-evaluated after both edits.
Only the first message set wins (`IF WS-RETURN-MSG-OFF` guards).

### 3.5 File access (`9100-GETCARD-BYACCTCARD`, `:736-777`)
`READ CARDDAT` keyed on the **card number only**. `NORMAL` → `FOUND-CARDS-FOR-ACCOUNT` and the
info line `   Displaying requested details` (three leading spaces are in the literal,
`:129-130`). `NOTFND` → `Did not find cards for this search condition`. Any other RESP →
`File Error: READ     on CARDDAT   returned RESP <resp>,RESP2 <resp2>` built from
`WS-FILE-ERROR-MESSAGE` (`:102-121`).

`9150-GETCARD-BYACCT` (`:779-…`) reads through `CARDAIX` and owns the message
`Did not find this account in cards database`, but **no paragraph performs it** — dead code
(quirk Q-3).

### 3.6 Control flow
PF3 → `CDEMO-TO-PROGRAM = CDEMO-FROM-PROGRAM` when set, else `COMEN01C`; `CDEMO-TO-TRANID =
CDEMO-FROM-TRANID` when set, else `CM00`; then `XCTL CDEMO-TO-PROGRAM` (`:305-334`). Arriving
from CCLI therefore returns to CCLI; arriving from the menu returns to the menu.

## 4. CCUP — `COCRDUPC` (Update Credit Card Details)

### 4.1 Screen layout — `app/bms/COCRDUP.bms`
Same labels and order as CCDL, title `Update Credit Card Details`; name (50), status (1), expiry
month (2) and year (4) are enterable, expiry day is protected/dark; PF captions
`ENTER=Process F3=Exit` and `F5=Save F12=Cancel`.

### 4.2 State machine (`CCUP-CHANGE-ACTION`, `2000-DECIDE-ACTION` `:948-1031`)
`CCUP-DETAILS-NOT-FETCHED` → read the card → `CCUP-SHOW-DETAILS` → (edits pass)
`CCUP-CHANGES-OK-NOT-CONFIRMED` → PF5 → `9200-WRITE-PROCESSING` →
`CCUP-CHANGES-OKAYED-AND-DONE` / `…-LOCK-ERROR` / `…-BUT-FAILED`, or back to
`CCUP-SHOW-DETAILS` when the record changed underneath. PF12 re-reads the card and returns to
`CCUP-SHOW-DETAILS` (cancel).

### 4.3 Input handling (`1100-RECEIVE-MAP` `:585-636`, `1200-EDIT-MAP-INPUTS` `:641-719`)
`*`/spaces → LOW-VALUES for account, card, name, status, expiry month, expiry year; the expiry
**day** is taken from the screen as-is (`:621`) and is never edited by the user (protected
field), so the stored day is preserved.

While the details have not been fetched, only the search keys are edited (same rules and messages
as CCDL, `:721-799`, plus `No input received` when both are blank). Once details are shown, the
new values are compared with the fetched values, upper-cased on both sides (`:680-683`); equal →
`No change detected with respect to values fetched.` and no field edits run. Otherwise:

| Field | Rule | Message | Cite |
|-------|------|---------|------|
| Name | not blank | `Card name not provided` | `:811-819` |
| Name | letters and spaces only (`INSPECT … CONVERTING` A–Z a–z → spaces, remainder must be empty) | `Card name can only contain alphabets and spaces` | `:822-837` |
| Status | not blank, and `Y` or `N` (88 `FLG-YES-NO-VALID`, `:91`) | `Card Active Status must be Y or N` | `:845-872` |
| Expiry month | not blank, numeric 1–12 (88 `VALID-MONTH`, `:92-95`) | `Card expiry month must be between 1 and 12` | `:877-907` |
| Expiry year | not blank, numeric 1950–2099 (88 `VALID-YEAR`, `:96-99`) | `Invalid card expiry year` | `:913-943` |

All edits pass → `Changes validated.Press F5 to save`.

### 4.4 Write path (`9200-WRITE-PROCESSING` `:1420-1493`, `9300-CHECK-CHANGE-IN-REC` `:1498-1520`)
`READ CARDDAT UPDATE` on the card number; non-`NORMAL` → `Could not lock record for update`.
Then the record on file is compared with the values fetched for this screen (CVV, embossed name
upper-cased, expiry year/month/day, active status); any difference →
`Record changed by some one else. Please review`, the screen is refreshed with the file values and
nothing is written. Otherwise the record is rebuilt — card number, account id, CVV, new name,
`YYYY-MM-DD` from new year + new month + **old day**, new status — and `REWRITE`n; a failing
rewrite → `Update of record failed`, success → `Changes committed to database`.

### 4.5 Control flow
`LIT-MENUPGM = COMEN01C`, `LIT-CCLISTPGM = COCRDLIC`, `LIT-CARDDTLPGM = COCRDSLC`. PF3 returns to
`CDEMO-FROM-PROGRAM` when set (CCLI when arriving from the list), otherwise `COMEN01C`, exactly
as CCDL does.

## 5. Informational / error message catalogue (verbatim)

**CCLI:** `PF03 PRESSED.EXITING`, `NO RECORDS FOUND FOR THIS SEARCH CONDITION.`,
`PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE`, `INVALID ACTION CODE`,
`NO MORE PAGES TO DISPLAY`, `NO MORE RECORDS TO SHOW`, `NO PREVIOUS PAGES TO DISPLAY`,
`TYPE S FOR DETAIL, U TO UPDATE ANY RECORD`,
`ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`,
`CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER`, `File Error: …`.

**CCDL:** `   Displaying requested details`, `Please enter Account and Card Number`,
`PF03 pressed.Exiting              `, `Account number not provided`, `Card number not provided`,
`No input received`, `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`,
`CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER`,
`Did not find cards for this search condition`, `Did not find this account in cards database`
(dead code), `Error reading Card Data File`, `File Error: …`.

**CCUP:** `Details of selected card shown above`, `Please enter Account and Card Number`,
`Update card details presented above.`, `Changes validated.Press F5 to save`,
`Changes committed to database`, `Changes unsuccessful. Please try again`,
`No change detected with respect to values fetched.`, `Card name not provided`,
`Card name can only contain alphabets and spaces`, `Card Active Status must be Y or N`,
`Card expiry month must be between 1 and 12`, `Invalid card expiry year`,
`Could not lock record for update`, `Record changed by some one else. Please review`,
`Update of record failed`, plus the CCDL search-key messages.

## 6. Data / fixtures
`app/data/ASCII/carddata.txt` — 50 CARDDAT records; `app/data/ASCII/cardxref.txt` — 50 CCXREF
records. Both are already loaded into the shared schema by
`migration/carddemo/backend/src/main/resources/db/seed/R__seed_carddemo_data.sql`
(`cards`, `card_xref`), so the migrated stream is exercised against exactly the legacy fixtures.

## 7. Quirks preserved (do not "fix")
* **Q-1** CCLI: an invalid PF key is treated as ENTER; there is no "invalid key" message.
* **Q-2** CCLI: with two or more `S`/`U` flags the program still remembers the **last** flagged
  row, but refuses the transfer with `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE`.
* **Q-3** CCDL/CCUP: the account number is validated but **not** used to read the card — the read
  is by card number only, so an account number that does not own the card still displays the
  card. The alternate-index paragraph that would enforce it is dead code.
* **Q-4** CCLI: `NO MORE RECORDS TO SHOW` is produced while filling a page that hits end-of-file,
  even though the page itself is full of rows.
* **Q-5** CCLI: filter validation short-circuits the selection edits, so a bad filter hides an
  invalid selection flag on the same submit.
* **Q-6** CCDL/CCUP: a lone `*` in a key field means "blank", producing the "not provided"
  message rather than a wildcard search.
* **Q-7** CCUP: the "no change" comparison is case-insensitive, so changing only the letter case
  of the embossed name is treated as no change at all.
* **Q-8** CCUP: expiry **day** is never editable and is carried over verbatim into the rewritten
  `YYYY-MM-DD` value.
* **Q-9** CCUP: card status accepts only upper-case `Y`/`N`; the name check accepts both cases.
* **Q-10** CCDL/CCUP: both key fields are mandatory even though the underlying read needs only
  the card number.
* **Q-11** CCLI: `NO RECORDS FOUND FOR THIS SEARCH CONDITION.` is unreachable. The read sets it in
  `WS-INFO-MSG` (`:1241-1245`), but `1100-SCREEN-INIT` re-initialises that field (`:668-670`)
  before `1400-SETUP-MESSAGE` runs, so an empty result shows `NO MORE RECORDS TO SHOW` on the
  error line and the ordinary `TYPE S FOR DETAIL, U TO UPDATE ANY RECORD` info line instead.

## 8. Out of scope
`CDV1` / `COCRDSEC` (R-1 / B-11) — no source in the repository, deferred, not invented.

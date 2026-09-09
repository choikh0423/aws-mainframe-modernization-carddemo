# S-06 BillPayment — source analysis

Stream: **S-06 BillPayment** (ONLINE). Transaction **CB00**, program `app/cbl/COBIL00C.cbl`,
map `app/bms/COBIL00.bms` (mapset `COBIL00`, map `COBIL0A`).
Files: **ACCTDAT** (read for update + rewrite), **TRANSACT** (browse for the highest key + write),
**CXACAIX** (the CCXREF alternate index, read by account id).

Every line cite below is `app/cbl/COBIL00C.cbl` unless another file is named.

---

## 1. Estate context

`COBIL00C` is reached from the main menu only: `app/cpy/COMEN02Y.cpy:80-84` defines option **10**,
label `Bill Payment`, program `COBIL00C`, user type `U` (every signed-on user; not admin-only).
The program is installed in group `CARDDEMO` (`app/csd/CARDDEMO.CSD:196`, `app/jcl/CBADMCDJ.jcl:127`)
and transaction `CB00` is defined against it (`app/csd/CARDDEMO.CSD:338`). No other program XCTLs or
LINKs to it, and `COBIL00C` itself LINKs to nothing.

## 2. Entry conditions and control flow (MAIN-PARA, lines 99-149)

| Condition | Behaviour | Cite |
|---|---|---|
| `EIBCALEN = 0` (no commarea — the transaction typed at a blank screen) | `MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM`, `RETURN-TO-PREV-SCREEN` → `XCTL COSGN00C` | 107-109, 273-284 |
| commarea present, `NOT CDEMO-PGM-REENTER` (first entry from the menu) | set re-enter, clear the map (`MOVE LOW-VALUES TO COBIL0AO`), cursor to `ACTIDIN`; if `CDEMO-CB00-TRN-SELECTED` is non-blank move it into `ACTIDINI` and `PERFORM PROCESS-ENTER-KEY`; then send the screen | 112-122 |
| re-entry, `EIBAID = DFHENTER` | `PROCESS-ENTER-KEY` | 126-127 |
| re-entry, `EIBAID = DFHPF3` | `CDEMO-TO-PROGRAM` = `CDEMO-FROM-PROGRAM`, or `COMEN01C` when the caller is blank; `XCTL` | 128-135 |
| re-entry, `EIBAID = DFHPF4` | `CLEAR-CURRENT-SCREEN` | 136-137 |
| re-entry, any other AID (PF1/PF2/PF5…PF12, PA keys, CLEAR) | error flag on, message `CCDA-MSG-INVALID-KEY` = `Invalid key pressed. Please see below...` (`app/cpy/CSMSG01Y.cpy`), send screen | 138-141 |

Every path ends in `EXEC CICS RETURN TRANSID('CB00') COMMAREA(CARDDEMO-COMMAREA)` (146-149) —
pseudo-conversational, so each turn is one screen send.

`RETURN-TO-PREV-SCREEN` (273-284) defaults an empty `CDEMO-TO-PROGRAM` to `COSGN00C`, stamps
`CDEMO-FROM-TRANID = 'CB00'`, `CDEMO-FROM-PROGRAM = 'COBIL00C'`, zeroes `CDEMO-PGM-CONTEXT`, then
`XCTL PROGRAM(CDEMO-TO-PROGRAM)`. **All XCTL targets are literal** (`COSGN00C`, `COMEN01C`) or the
caller's own name carried in the commarea; there is no symbolic dispatch table in this program.

### `CDEMO-CB00-TRN-SELECTED` — a dead input

`CDEMO-CB00-INFO` is declared *inside this program* as an extension of the copied commarea
(63-72). Grepping the whole estate shows the only references to `CDEMO-CB00-*` are these
declarations and the read at 116-119 — **no program ever sets it**, and the field overlays commarea
bytes past `COCOM01Y`. The auto-submit path at 116-121 is therefore unreachable in the estate as
shipped.

## 3. Screen layout (`app/bms/COBIL00.bms`)

Map `COBIL0A`, 24×80, `CTRL=(ALARM,FREEKB)`. Fields in map order:

| Row/Col | Field | Len | Attr | Literal / source |
|---|---|---|---|---|
| 1,1 | — | 5 | ASKIP blue | `Tran:` |
| 1,7 | `TRNNAME` | 4 | ASKIP blue | `WS-TRANID` = `CB00` (325) |
| 1,21 | `TITLE01` | 40 | ASKIP yellow | `CCDA-TITLE01` = `      AWS Mainframe Modernization       ` (`app/cpy/COTTL01Y.cpy`) |
| 1,65 | — | 5 | ASKIP blue | `Date:` |
| 1,71 | `CURDATE` | 8 | ASKIP blue | `mm/dd/yy` from `FUNCTION CURRENT-DATE` (321-332) |
| 2,1 | — | 5 | ASKIP blue | `Prog:` |
| 2,7 | `PGMNAME` | 8 | ASKIP blue | `COBIL00C` (326) |
| 2,21 | `TITLE02` | 40 | ASKIP yellow | `CCDA-TITLE02` = `              CardDemo                  ` |
| 2,65 | — | 5 | ASKIP blue | `Time:` |
| 2,71 | `CURTIME` | 8 | ASKIP blue | `hh:mm:ss` (334-338) |
| 4,35 | — | 12 | ASKIP bright neutral | `Bill Payment` |
| 6,6 | — | 14 | ASKIP green | `Enter Acct ID:` |
| 6,21 | `ACTIDIN` | 11 | **UNPROT**, IC, FSET, underlined green | operator input |
| 8,6 | — | 70 | yellow | `----------------------------------------------------------------------` |
| 11,6 | — | 25 | ASKIP turquoise | `Your current balance is: ` |
| 11,32 | `CURBAL` | 14 | ASKIP blue | `WS-CURR-BAL PIC +9999999999.99` (56, 193-194) |
| 15,6 | — | 53 | ASKIP turquoise | `Do you want to pay your balance now. Please confirm: ` |
| 15,60 | `CONFIRM` | 1 | **UNPROT**, FSET, underlined green | operator input |
| 15,63 | — | 5 | ASKIP neutral | `(Y/N)` |
| 23,1 | `ERRMSG` | 78 | ASKIP bright **red** (`bms:127-130`) | `WS-MESSAGE` (293); recoloured `DFHGREEN` on a successful payment (526) |
| 24,1 | — | 33 | ASKIP yellow | `ENTER=Continue  F3=Back  F4=Clear` |

The two input fields are `ACTIDIN` (11) and `CONFIRM` (1). There is no PF7/PF8 line and no
`(Y/N)`-typed field: the confirm edit is done in the program.

## 4. `PROCESS-ENTER-KEY` — field-by-field rules (154-244)

Executed for ENTER (and for the dead auto-submit path). `WS-CONF-PAY-FLG` starts `N` (156).

1. **Account id empty** (159-164): `ACTIDINI = SPACES OR LOW-VALUES` → error flag `Y`, message
   `Acct ID can NOT be empty...`, cursor to `ACTIDIN`, send screen. No file is touched.
2. **Key move** (170-171): `MOVE ACTIDINI TO ACCT-ID, XREF-ACCT-ID`. Both targets are `PIC 9(11)`
   (`app/cpy/CVACT01Y.cpy`, `app/cpy/CVACT03Y.cpy`). **There is no numeric edit anywhere in this
   program** — unlike `COTRN02C`, which rejects a non-numeric account id with
   `Account ID must be Numeric...`. A non-numeric or short entry simply fails the VSAM read and is
   reported as `Account ID NOT found...`.
3. **Confirm edit** (173-191), on the raw `CONFIRMI` character:
   - `Y` / `y` → `CONF-PAY-YES`, then `READ-ACCTDAT-FILE`;
   - `N` / `n` → `CLEAR-CURRENT-SCREEN` (blank all fields + send), then error flag `Y` so the rest of
     the paragraph is skipped. **No message is shown** and no file is read;
   - `SPACES` / `LOW-VALUES` → `READ-ACCTDAT-FILE` (balance inquiry, no payment);
   - anything else → error flag `Y`, message `Invalid value. Valid values are (Y/N)...`, cursor to
     `CONFIRM`, send screen.
4. **Balance to the screen** (193-194): `MOVE ACCT-CURR-BAL TO WS-CURR-BAL` then to `CURBALI`.
   This sits *inside* the `IF NOT ERR-FLG-ON` block that started at 169, so it also runs after the
   `N` branch and after the invalid-confirm branch, where `ACCOUNT-RECORD` was never read
   (see quirk Q-2).
5. **Nothing to pay** (197-206): if `ACCT-CURR-BAL <= ZEROS` *and* the account id is non-blank →
   error flag `Y`, message `You have nothing to pay...`, cursor to `ACTIDIN`, send screen. The
   balance was already moved to the map at step 4, so the screen shows the balance *and* the error.
   The `ACTIDINI NOT = SPACES` half of the condition is always true here (step 1 already rejected
   blanks) — dead sub-condition.
6. **Payment** (208-244), only when no error flag is set:
   - `CONF-PAY-YES` → the write sequence in §5;
   - otherwise → message `Confirm to make a bill payment...`, cursor to `CONFIRM` (the balance
     inquiry turn);
   - either way, `SEND-BILLPAY-SCREEN` (242).

## 5. The payment write sequence (210-235)

| Step | Detail | Cite |
|---|---|---|
| Read the xref | `READ CXACAIX RIDFLD(XREF-ACCT-ID)` → `XREF-CARD-NUM` | 211, 408-436 |
| Highest key | `MOVE HIGH-VALUES TO TRAN-ID`, `STARTBR TRANSACT`, `READPREV`, `ENDBR`; `ENDFILE` → `MOVE ZEROS TO TRAN-ID` | 212-215, 441-505 |
| New id | `MOVE TRAN-ID TO WS-TRAN-ID-NUM PIC 9(16)`, `ADD 1` | 216-217 |
| Build the record | `INITIALIZE TRAN-RECORD` then the fixed literals below | 218-232 |
| Write | `WRITE TRANSACT FROM(TRAN-RECORD) RIDFLD(TRAN-ID)` | 233, 510-547 |
| Balance | `COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT` | 234 |
| Rewrite | `REWRITE ACCTDAT FROM(ACCOUNT-RECORD)` (the record is held from the `READ ... UPDATE` at 345-354) | 235, 377-403 |

Record contents (`app/cpy/CVTRA05Y.cpy` layout):

| Field | PIC | Value | Cite |
|---|---|---|---|
| `TRAN-ID` | X(16) | highest existing key + 1, zero-padded | 216-219 |
| `TRAN-TYPE-CD` | X(02) | `02` | 220 |
| `TRAN-CAT-CD` | 9(04) | `2` (stored as `0002`) | 221 |
| `TRAN-SOURCE` | X(10) | `POS TERM` | 222 |
| `TRAN-DESC` | X(100) | `BILL PAYMENT - ONLINE` | 223 |
| `TRAN-AMT` | S9(09)V99 | `ACCT-CURR-BAL` (S9(10)V99 — **truncating**, quirk Q-1) | 224 |
| `TRAN-CARD-NUM` | X(16) | `XREF-CARD-NUM` from CXACAIX | 225 |
| `TRAN-MERCHANT-ID` | 9(09) | `999999999` | 226 |
| `TRAN-MERCHANT-NAME` | X(50) | `BILL PAYMENT` | 227 |
| `TRAN-MERCHANT-CITY` | X(50) | `N/A` | 228 |
| `TRAN-MERCHANT-ZIP` | X(10) | `N/A` | 229 |
| `TRAN-ORIG-TS`, `TRAN-PROC-TS` | X(26) | `WS-TIMESTAMP`, both identical | 230-232 |

`GET-CURRENT-TIMESTAMP` (249-267): `ASKTIME` + `FORMATTIME YYYYMMDD(...) DATESEP('-')
TIME(...) TIMESEP(':')`, then `WS-TIMESTAMP(01:10)` = date, `(12:08)` = time,
`WS-TIMESTAMP-TM-MS6 = ZEROS`. `WS-TIMESTAMP` is `app/cpy/CSDAT01Y.cpy`'s
`YYYY-MM-DD HH:MM:SS.mmmmmm` (the byte at position 11 is the copybook's `FILLER VALUE ' '`, which
`INITIALIZE` leaves alone). So the stored timestamp is
**`YYYY-MM-DD HH:MM:SS.000000`** — a blank date/time separator and always-zero microseconds.

## 6. File access and every error path

| Paragraph | CICS call | RESP | Message | Cite |
|---|---|---|---|---|
| `READ-ACCTDAT-FILE` | `READ ACCTDAT ... UPDATE RIDFLD(ACCT-ID)` | NORMAL | — | 345-358 |
| | | NOTFND | `Account ID NOT found...` | 359-364 |
| | | other | `Unable to lookup Account...` | 365-371 |
| `UPDATE-ACCTDAT-FILE` | `REWRITE ACCTDAT` | NORMAL | — | 379-389 |
| | | NOTFND | `Account ID NOT found...` | 390-395 |
| | | other | `Unable to Update Account...` | 396-402 |
| `READ-CXACAIX-FILE` | `READ CXACAIX RIDFLD(XREF-ACCT-ID)` | NORMAL | — | 410-422 |
| | | NOTFND | `Account ID NOT found...` (same text as the ACCTDAT miss) | 423-428 |
| | | other | `Unable to lookup XREF AIX file...` | 429-435 |
| `STARTBR-TRANSACT-FILE` | `STARTBR TRANSACT RIDFLD(TRAN-ID)` | NORMAL | — | 443-453 |
| | | NOTFND | `Transaction ID NOT found...` | 454-459 |
| | | other | `Unable to lookup Transaction...` | 460-466 |
| `READPREV-TRANSACT-FILE` | `READPREV TRANSACT` | NORMAL | — | 474-486 |
| | | ENDFILE | `MOVE ZEROS TO TRAN-ID` (empty file → first id is 1) | 487-488 |
| | | other | `Unable to lookup Transaction...` | 489-495 |
| `ENDBR-TRANSACT-FILE` | `ENDBR TRANSACT` | not checked | — | 501-505 |
| `WRITE-TRANSACT-FILE` | `WRITE TRANSACT` | NORMAL | clear fields, `ERRMSGC = DFHGREEN`, success text | 522-532 |
| | | DUPKEY / DUPREC | `Tran ID already exist...` | 533-539 |
| | | other | `Unable to Add Bill pay Transaction...` | 540-546 |

Success text (527-531):

```
STRING 'Payment successful. '     DELIMITED BY SIZE
       ' Your Transaction ID is ' DELIMITED BY SIZE
       TRAN-ID                    DELIMITED BY SPACE
       '.'                        DELIMITED BY SIZE
  INTO WS-MESSAGE
```

The first literal ends with a blank and the second starts with one, so the rendered text has **two
spaces** after the first period:
`Payment successful.  Your Transaction ID is 0000000000000042.`
`TRAN-ID` is zero-padded and contains no blank, so `DELIMITED BY SPACE` contributes all 16 digits.

`CLEAR-CURRENT-SCREEN` (552-555) = `INITIALIZE-ALL-FIELDS` + send.
`INITIALIZE-ALL-FIELDS` (560-566): cursor to `ACTIDIN`; `ACTIDINI`, `CURBALI`, `CONFIRMI` and
`WS-MESSAGE` to spaces.

## 7. Quirks (preserved in the migration — see the FR)

- **Q-1 — the paid amount can be smaller than the balance.** `MOVE ACCT-CURR-BAL TO TRAN-AMT`
  (224) moves `S9(10)V99` into `S9(09)V99`: the high-order digit is **truncated**. For a balance of
  `1234567890.12` the transaction is written for `234567890.12` and
  `COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT` (234) leaves `1000000000.00` behind, so the
  "pay in full" screen does not zero the account. Under 10⁹ the balance always lands exactly on
  `0.00`; both operands are scale-2, so no rounding ever occurs.
- **Q-2 — `CURBAL` is refreshed from an unread record.** Steps 3 and 4 above: after `N` and after
  an invalid confirm character, `MOVE ACCT-CURR-BAL TO CURBALI` runs even though `ACCOUNT-RECORD`
  was never read on that turn, so the field shows whatever the freshly initialised working-storage
  copy holds.
- **Q-3 — a missing CXACAIX entry is reported as `Account ID NOT found...`** (423-427), the same
  text as a missing ACCTDAT record, even though the account does exist.
- **Q-4 — the account id is never edited for numerics**, so `ABCDEFGHIJK` produces
  `Account ID NOT found...`, not a numeric-format message (Q-4 is the direct consequence of §4.2).
- **Q-5 — the screen is sent twice on a successful payment.** `WRITE-TRANSACT-FILE` sends it (532)
  and `PROCESS-ENTER-KEY` sends it again at 242 after the balance rewrite. The operator sees only
  the second send; the content is identical.
- **Q-6 — `STARTBR` NOTFND says `Transaction ID NOT found...`** even though the browse is positioned
  on `HIGH-VALUES` and the real meaning is "the TRANSACT file is empty/unavailable"; an empty file
  normally surfaces as the `READPREV` `ENDFILE` branch instead.
- **Q-7 — the `N` (decline) turn shows no message at all**: the screen is simply blanked.
- **Q-8 — `CDEMO-CB00-TRN-SELECTED` auto-submit is dead code** (§2).

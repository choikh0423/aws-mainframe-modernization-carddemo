# S-07 Reporting — source analysis

Stream S-07 (`docs/migration/CardDemo_inventory.md:129`) is a single ONLINE transaction:

| Transaction | Program | Map / mapset | Copybook |
|---|---|---|---|
| `CR00` | `app/cbl/CORPT00C.cbl` | `CORPT0A` / `CORPT00` (`app/bms/CORPT00.bms`) | `app/cpy-bms/CORPT00.CPY` |

Its whole purpose is to collect a report type and a date range on a 3270 screen and to submit the
`TRANREPT` batch job (`app/proc/TRANREPT.prc`, program `CBTRN03C`) through the CICS internal reader.
It reads no business data itself.

---

## 1. Entry conditions and control flow

`MAIN-PARA` (CORPT00C.cbl:163-202):

| Condition | Behaviour | Cite |
|---|---|---|
| `EIBCALEN = 0` (transaction typed with no COMMAREA) | `MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM`, `RETURN-TO-PREV-SCREEN` → `XCTL COSGN00C` | cbl:172-174, 540-551 |
| COMMAREA present, `NOT CDEMO-PGM-REENTER` (first display) | set re-enter flag, `MOVE LOW-VALUES TO CORPT0AO`, cursor to `MONTHLY`, `SEND-TRNRPT-SCREEN` with `ERASE` | cbl:177-181 |
| Re-entry, `EIBAID = DFHENTER` | `PROCESS-ENTER-KEY` | cbl:185-186 |
| Re-entry, `EIBAID = DFHPF3` | `MOVE 'COMEN01C' TO CDEMO-TO-PROGRAM` → `XCTL COMEN01C` | cbl:187-189 |
| Re-entry, any other AID | error `CCDA-MSG-INVALID-KEY`, cursor to `MONTHLY`, resend | cbl:190-194 |

**Symbolic routing (risk R-2).** The two `XCTL` targets are resolved literals moved into
`CDEMO-TO-PROGRAM`: `'COSGN00C'` (cbl:173, 543) and `'COMEN01C'` (cbl:188). `RETURN-TO-PREV-SCREEN`
defaults `CDEMO-TO-PROGRAM` to `'COSGN00C'` when it is spaces/low-values (cbl:542-544) and sets
`CDEMO-FROM-TRANID = 'CR00'`, `CDEMO-FROM-PROGRAM = 'CORPT00C'`, `CDEMO-PGM-CONTEXT = ZEROS`
(cbl:545-547). There is no other `XCTL`/`LINK` in the program.

**Pseudo-conversational quirk that shapes everything below.** `SEND-TRNRPT-SCREEN` ends with
`GO TO RETURN-TO-CICS` (cbl:580), and `RETURN-TO-CICS` issues `EXEC CICS RETURN` (cbl:587-591). Every
`PERFORM SEND-TRNRPT-SCREEN` therefore **terminates the task**: validation is strictly first-error-wins,
and no statement after a failing check is ever executed in that task. All the `IF NOT ERR-FLG-ON` guards
that follow a send (cbl:434, 445, 476) are consequently always true when reached.

## 2. Screen layout (`app/bms/CORPT00.bms`)

| Field | Type | Len | Pos | Literal / notes | Cite |
|---|---|---|---|---|---|
| — | label | 5 | 1,1 | `Tran:` | bms:29-33 |
| `TRNNAME` | out | 4 | 1,7 | `CR00` | bms:34-37 |
| `TITLE01` | out | 40 | 1,21 | `CCDA-TITLE01` | bms:38-41 |
| — | label | 5 | 1,65 | `Date:` | bms:42-46 |
| `CURDATE` | out | 8 | 1,71 | `mm/dd/yy` | bms:47-51 |
| — | label | 5 | 2,1 | `Prog:` | bms:52-56 |
| `PGMNAME` | out | 8 | 2,7 | `CORPT00C` | bms:57-60 |
| `TITLE02` | out | 40 | 2,21 | `CCDA-TITLE02` | bms:61-64 |
| — | label | 5 | 2,65 | `Time:` | bms:65-69 |
| `CURTIME` | out | 8 | 2,71 | `hh:mm:ss` | bms:70-74 |
| — | heading | 19 | 4,30 | `Transaction Reports` | bms:75-79 |
| `MONTHLY` | unprot | 1 | 7,10 | initial cursor (`IC`) | bms:80-85 |
| — | label | 23 | 7,15 | `Monthly (Current Month)` | bms:89-93 |
| `YEARLY` | unprot | 1 | 9,10 | | bms:94-99 |
| — | label | 23 | 9,15 | `Yearly (Current Year)` | bms:103-107 |
| `CUSTOM` | unprot | 1 | 11,10 | | bms:108-113 |
| — | label | 23 | 11,15 | `Custom (Date Range)` | bms:117-121 |
| — | label | 12 | 13,15 | `Start Date :` | bms:122-126 |
| `SDTMM` | unprot NUM | 2 | 13,29 | | bms:127-132 |
| `SDTDD` | unprot NUM | 2 | 13,34 | separator `/` at 13,32 and 13,37 | bms:138-143 |
| `SDTYYYY` | unprot NUM | 4 | 13,39 | | bms:149-154 |
| — | label | 12 | 13,46 | `(MM/DD/YYYY)` | bms:157-160 |
| — | label | 12 | 14,15 | `  End Date :` (two leading blanks) | bms:161-165 |
| `EDTMM` | unprot NUM | 2 | 14,29 | | bms:166-171 |
| `EDTDD` | unprot NUM | 2 | 14,34 | | bms:177-182 |
| `EDTYYYY` | unprot NUM | 4 | 14,39 | | bms:188-193 |
| — | label | 12 | 14,46 | `(MM/DD/YYYY)` | bms:196-199 |
| — | label | 59 | 19,6 | `The Report will be submitted for printing. Please confirm: ` | bms:200-205 |
| `CONFIRM` | unprot | 1 | 19,66 | | bms:206-210 |
| — | label | 5 | 19,69 | `(Y/N)` | bms:213-217 |
| `ERRMSG` | out | 78 | 23,1 | red (`COLOR=RED`), recoloured green on success | bms:218-221, cbl:448 |
| — | label | 23 | 24,1 | `ENTER=Continue  F3=Back` | bms:222-226 |

`POPULATE-HEADER-INFO` (cbl:609-628) fills `TITLE01`/`TITLE02` from `COTTL01Y`, `TRNNAME = 'CR00'`,
`PGMNAME = 'CORPT00C'`, `CURDATE` as `MM/DD/YY` and `CURTIME` as `HH:MM:SS` from `FUNCTION CURRENT-DATE`
on **every** send.

## 3. Report-type selection (`PROCESS-ENTER-KEY`, cbl:208-456)

The `EVALUATE TRUE` tests the three selection fields **in order** — Monthly, then Yearly, then Custom —
against `NOT = SPACES AND LOW-VALUES` (cbl:213, 239, 256). Any non-blank character selects; if more than
one is marked, the first in that order wins and the others are ignored.

### Monthly (cbl:213-238)
`WS-REPORT-NAME = 'Monthly'`. Start date = current year, current month, day `01`. End date = the day
before the first of the following month, i.e. the last day of the current month, computed with
`FUNCTION DATE-OF-INTEGER(FUNCTION INTEGER-OF-DATE(...) - 1)` after rolling the month (and the year when
the month passes 12).

### Yearly (cbl:239-255)
`WS-REPORT-NAME = 'Yearly'`. Start = `YYYY-01-01`, end = `YYYY-12-31` of the current year.

### Custom (cbl:256-436)
`WS-REPORT-NAME = 'Custom'`. Uses the six date fields; see §4.

### Nothing selected (cbl:437-442)
Message `Select a report type to print report...`, cursor to `MONTHLY`.

## 4. Custom date validation, field by field

Order of the checks, each of which ends the task on failure (§1):

1. **Empty tests** (cbl:258-303), in field order, comparing `= SPACES OR LOW-VALUES`:

   | Field | Message | Cursor |
   |---|---|---|
   | `SDTMM` | `Start Date - Month can NOT be empty...` | `SDTMM` |
   | `SDTDD` | `Start Date - Day can NOT be empty...` | `SDTDD` |
   | `SDTYYYY` | `Start Date - Year can NOT be empty...` | `SDTYYYY` |
   | `EDTMM` | `End Date - Month can NOT be empty...` | `EDTMM` |
   | `EDTDD` | `End Date - Day can NOT be empty...` | `EDTDD` |
   | `EDTYYYY` | `End Date - Year can NOT be empty...` | `EDTYYYY` |

2. **`NUMVAL-C` normalisation** (cbl:305-327). Each field is passed through
   `FUNCTION NUMVAL-C` into `WS-NUM-99` (`PIC 99`) or `WS-NUM-9999` (`PIC 9999`) and moved back into the
   screen field, so the value is truncated to the picture and **zero-padded**: `` `1 ` `` → `01`,
   `` ` 7` `` → `07`, `20` (year) → `0020`.

3. **Range tests** (cbl:329-379):

   | Test | Message | Cursor |
   |---|---|---|
   | `SDTMM NOT NUMERIC OR > '12'` | `Start Date - Not a valid Month...` | `SDTMM` |
   | `SDTDD NOT NUMERIC OR > '31'` | `Start Date - Not a valid Day...` | `SDTDD` |
   | `SDTYYYY NOT NUMERIC` | `Start Date - Not a valid Year...` | `SDTYYYY` |
   | `EDTMM NOT NUMERIC OR > '12'` | `End Date - Not a valid Month...` | `EDTMM` |
   | `EDTDD NOT NUMERIC OR > '31'` | `End Date - Not a valid Day...` | `EDTDD` |
   | `EDTYYYY NOT NUMERIC` | `End Date - Not a valid Year...` | `EDTYYYY` |

4. **`CSUTLDTC` calls** (cbl:388-426) on `WS-START-DATE` and then `WS-END-DATE`, both formatted
   `YYYY-MM-DD` (the group items at cbl:60-71 carry hard-coded `-` separators, and `WS-DATE-FORMAT` is
   `'YYYY-MM-DD'`, cbl:72). A call is a failure only when
   `CSUTLDTC-RESULT-SEV-CD NOT = '0000'` **and** `CSUTLDTC-RESULT-MSG-NUM NOT = '2513'`:

   | Failing call | Message | Cursor |
   |---|---|---|
   | start date | `Start Date - Not a valid date...` | `SDTMM` |
   | end date | `End Date - Not a valid date...` | `EDTMM` |

### Quirks in this block (preserved, not fixed)

* **Q-1 — the `NOT NUMERIC` tests can never fire.** Step 2 moves a `PIC 9(n)` item into the field, so by
  step 3 the field always holds digits. Non-numeric input is silently converted (see Q-2) rather than
  rejected by the `Not a valid Month/Day/Year` messages; the *Year* checks (cbl:347-353, 373-379) are
  therefore dead code and their messages are unreachable.
* **Q-2 — junk becomes zero.** `FUNCTION NUMVAL-C` on an argument with no numeric characters yields 0, so
  `ab` → `00`. A month or day of `00` passes the `> '12'` / `> '31'` tests and is caught only later by
  `CSUTLDTC`, surfacing as `... - Not a valid date...` rather than `... - Not a valid Month...`.
* **Q-3 — message 2513 is accepted.** `2513` is the `CEE2513` feedback of `CEEDAYS`
  (`FC-UNSUPP-RANGE`, `app/cbl/CSUTLDTC.cbl:66`) — a syntactically correct date outside the Lillian range
  (before 1582-10-15). CORPT00C explicitly lets it through (cbl:399, 419), so e.g. `01/01/1000` is
  accepted as a report range.
* **Q-4 — no ordering check.** The program never compares start with end, so an end date before the start
  date is submitted happily (the `TRANREPT` SORT then simply includes no rows).
* **Q-5 — no day-of-month check beyond `> '31'` before `CSUTLDTC`;** `02/31/2023` reaches `CSUTLDTC`,
  which rejects it with `Datevalue error` (severity ≠ 0, msg 2508) → `Start Date - Not a valid date...`.

## 5. Confirmation and submission (`SUBMIT-JOB-TO-INTRDR`, cbl:462-510)

Reached for **all three** report types.

| `CONFIRM` | Behaviour | Cite |
|---|---|---|
| spaces / low-values | `Please confirm to print the <name> report...`, cursor `CONFIRM`, no submit | cbl:464-474 |
| `Y` or `y` | continue to the internal-reader write | cbl:478-479 |
| `N` or `n` | `INITIALIZE-ALL-FIELDS`, error flag on, screen resent **with an empty message** | cbl:480-483 |
| anything else | `"<c>" is not a valid value to confirm...`, cursor `CONFIRM` | cbl:484-493 |

The confirmation prompt is built with `STRING 'Please confirm to print the ' WS-REPORT-NAME DELIMITED BY
SPACE ' report...'`, e.g. `Please confirm to print the Monthly report...`. The invalid-value message is
`STRING '"' CONFIRMI DELIMITED BY SPACE '" is not a valid value to confirm...'`.

**The JCL deck** is a fixed 17-line table `JOB-DATA-1` (cbl:81-125), redefined as
`JOB-LINES OCCURS 1000 TIMES PIC X(80)` (cbl:126-127):

```
//TRNRPT00 JOB 'TRAN REPORT',CLASS=A,MSGCLASS=0,
// NOTIFY=&SYSUID
//*
//JOBLIB JCLLIB ORDER=('AWS.M2.CARDDEMO.PROC')
//*
//STEP10 EXEC PROC=TRANREPT
//*
//STEP05R.SYMNAMES DD *
TRAN-CARD-NUM,263,16,ZD
TRAN-PROC-DT,305,10,CH
PARM-START-DATE,C'<start>'
PARM-END-DATE,C'<end>'
/*
//STEP10R.DATEPARM DD *
<start> <end>
/*
/*EOF
```

`<start>`/`<end>` are the `YYYY-MM-DD` strings moved into `PARM-START-DATE-1/2` and `PARM-END-DATE-1/2`
(cbl:106, 111, 118, 120). They override the `SYMNAMES` `PARM-START-DATE`/`PARM-END-DATE` symbols of
`app/proc/TRANREPT.prc` (the SORT `INCLUDE COND` on `TRAN-PROC-DT`) and the `DATEPARM` DD read by
`CBTRN03C` at `STEP10R`.

The loop (cbl:498-508) walks `JOB-LINES` from 1 to at most 1000, writing each line to the extra-partition
TDQ `JOBS` with `EXEC CICS WRITEQ TD` (cbl:517-523):

* **Q-6 — the terminator is written too.** `END-LOOP-YES` is set *before* `PERFORM WIRTE-JOBSUB-TDQ`, so
  all 17 lines including the trailing `/*EOF` reach the queue, and only then does the loop stop.
  Each line is exactly 80 characters: `PARM-START-DATE,C'<start>'` is padded to 80 by the `PIC X(52)`
  filler (cbl:107), `PARM-END-DATE,C'<end>'` by the `PIC X(54)` filler (cbl:112), and the `DATEPARM`
  line is `<start> <end>` followed by 59 blanks (cbl:117-121).
* Any `RESP` other than `NORMAL` gives `Unable to Write TDQ (JOBS)...` with the cursor on `MONTHLY`
  (cbl:525-535).

**Success** (cbl:445-456): `INITIALIZE-ALL-FIELDS` clears the three selection fields, the six date fields,
the confirm field and `WS-MESSAGE`; `ERRMSGC` is set to `DFHGREEN`; and the message
`STRING WS-REPORT-NAME DELIMITED BY SPACE ' report submitted for printing ...'` is shown, e.g.
`Monthly report submitted for printing ...`. Cursor returns to `MONTHLY`.

## 6. File and data access

**None.** `WS-TRANSACT-FILE`, `WS-TRANSACT-EOF`, `WS-REC-COUNT`, `WS-TRAN-AMT`, `WS-TRAN-DATE` and the
`COPY CVTRA05Y` transaction record (cbl:40-46, 56, 77-78, 146) are declared but never referenced by the
procedure division — dead working storage left over from a copy of a transaction browse program. The only
external resources CR00 touches are the TDQ `JOBS`, the `CSUTLDTC` subroutine and the COMMAREA.

## 7. Boundaries

| ID | Boundary | Direction | Handling |
|---|---|---|---|
| B-02 | `CALL 'CSUTLDTC'` (cbl:392, 412) | outbound | reuse `com.carddemo.common.service.DateValidationService`; **do not re-port** (inventory:192) |
| — | `WRITEQ TD QUEUE('JOBS')` → internal reader → `TRANREPT`/`CBTRN03C` | outbound, intra-estate | the S-07 → S-14 coupling flagged in `docs/migration/CardDemo_inventory.md:221-223`; decided in `Reporting_migration_plan.md` §3 |

`app/jcl/INTRDRJ1.JCL` / `INTRDRJ2.JCL` are the repo's *examples* of internal-reader submission (an
`IEBGENER` step writing a member to `SYSOUT=(A,INTRDR)`); they do not name `TRANREPT` and are not read by
CORPT00C. The runtime path is the TDQ `JOBS`, which is defined as an extra-partition destination routed to
the internal reader.

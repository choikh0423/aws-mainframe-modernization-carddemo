# S-12 InterestCalculation — source analysis

Scope: `app/jcl/INTCALC.jcl`, `app/jcl/COMBTRAN.jcl`, `app/cbl/CBACT04C.cbl`.
Copybooks: `CVTRA01Y` (TCATBALF), `CVTRA02Y` (DISCGRP), `CVACT01Y` (ACCTDAT), `CVACT03Y` (CCXREF),
`CVTRA05Y` (TRANSACT).
Every statement below cites the source line it came from. This stream is **batch only** — it has no BMS
map, no CICS transaction and no screen, so there are no screen layouts, no field-level screen validation
and no `XCTL`/`LINK` control flow to trace: `CBACT04C` is a standalone batch program whose only exits are
`GOBACK` (`CBACT04C.cbl:232`) and `CALL 'CEE3ABD'` (`CBACT04C.cbl:632`).

---

## 1. Job chain and entry conditions

Control-M chain `MONTHLY-InterestCalculation` (`docs/migration/CardDemo_inventory.md:81`):

```
CLOSEFIL → INTCALC → COMBTRAN → WAITSTEP → OPENFIL
```

CLOSEFIL/OPENFIL (CICS file close/open) and WAITSTEP belong to S-17 OperationsChain; INTCALC and COMBTRAN
are S-12.

### 1.1 INTCALC (`app/jcl/INTCALC.jcl`)

One step: `//STEP15 EXEC PGM=CBACT04C,PARM='2022071800'`.

| DD | Dataset | Use |
|---|---|---|
| `TCATBALF` | `...TCATBALF.VSAM.KSDS` | input, browsed sequentially |
| `XREFFILE` | `...CARDXREF.VSAM.KSDS` | random read by the `CXACAIX` alternate key |
| `XREFFIL1` | `...CARDXREF.VSAM.AIX.PATH` | the alternate-index path allocated for `XREFFILE` |
| `ACCTFILE` | `...ACCTDATA.VSAM.KSDS` | random read + rewrite |
| `DISCGRP` | `...DISCGRP.VSAM.KSDS` | random read |
| `TRANSACT` | `...SYSTRAN(+1)`, `RECFM=F,LRECL=350`, `DISP=(NEW,CATLG,DELETE)` | **sequential output**, a new GDG generation |

Entry condition: the PARM is a 10-byte character run date (`PARM-DATE PIC X(10)`, `CBACT04C.cbl:176-178`).
The value `'2022071800'` is `YYYYMMDD` plus two trailing characters; the program never parses it, never
validates it and never compares it to a date — it only concatenates it into the generated transaction id
(`CBACT04C.cbl:476-480`). A shorter PARM leaves the remaining bytes of `PARM-DATE` as whatever the LE
parameter area contains; a missing PARM is undefined behaviour and is not handled.

**Important:** the `TRANSACT` DD of INTCALC is *not* the transaction master. It is the sequential
`SYSTRAN` generation, which only COMBTRAN later merges into the master KSDS.

### 1.2 COMBTRAN (`app/jcl/COMBTRAN.jcl`)

| Step | Program | Behaviour |
|---|---|---|
| `STEP05R` | `SORT` | `SORTIN` is the concatenation of `TRANSACT.BKUP(0)` (backup of the transaction master) and `SYSTRAN(0)` (the generation INTCALC just wrote); `SYMNAMES` defines `TRAN-ID,1,16,CH`; `SORT FIELDS=(TRAN-ID,A)`; `SORTOUT` is a new `TRANSACT.COMBINED(+1)` generation with `DCB=(*.SORTIN)`, i.e. F/350 |
| `STEP10` | `IDCAMS` | `REPRO INFILE(TRANSACT) OUTFILE(TRANVSAM)` — loads `TRANSACT.COMBINED(+1)` into `TRANSACT.VSAM.KSDS`, the transaction master |

The sort is on the character key at position 1 length 16, ascending — the same collating order as the
KSDS key, which is why REPRO into the KSDS succeeds without a preceding sort inside IDCAMS.

---

## 2. Record layouts

### TCATBALF — `CVTRA01Y` `TRAN-CAT-BAL-RECORD` (RECLN 50)

| Field | PIC | Offset (0-based) |
|---|---|---|
| `TRANCAT-ACCT-ID` | `9(11)` | 0 |
| `TRANCAT-TYPE-CD` | `X(02)` | 11 |
| `TRANCAT-CD` | `9(04)` | 13 |
| `TRAN-CAT-BAL` | `S9(09)V99` | 17 |
| FILLER | `X(22)` | 28 |

The first three fields are the KSDS key (`FD-TRAN-CAT-KEY`, `CBACT04C.cbl:63-66`), so a sequential browse
returns records in `(acct id, type cd, cat cd)` order.

### DISCGRP — `CVTRA02Y` `DIS-GROUP-RECORD` (RECLN 50)

| Field | PIC | Offset |
|---|---|---|
| `DIS-ACCT-GROUP-ID` | `X(10)` | 0 |
| `DIS-TRAN-TYPE-CD` | `X(02)` | 10 |
| `DIS-TRAN-CAT-CD` | `9(04)` | 12 |
| `DIS-INT-RATE` | `S9(04)V99` | 16 |

Key = the first three fields (`CBACT04C.cbl:78-81`). The rate is an annual percentage, e.g. `00150{` =
`+1.50`.

### ACCTDAT — `CVACT01Y` `ACCOUNT-RECORD` (RECLN 300)

The fields this program touches: `ACCT-ID PIC 9(11)`, `ACCT-CURR-BAL PIC S9(10)V99`,
`ACCT-CURR-CYC-CREDIT PIC S9(10)V99`, `ACCT-CURR-CYC-DEBIT PIC S9(10)V99`, `ACCT-GROUP-ID PIC X(10)`.

### CCXREF — `CVACT03Y` `CARD-XREF-RECORD` (RECLN 50)

`XREF-CARD-NUM PIC X(16)` (primary key), `XREF-CUST-ID PIC 9(09)`, `XREF-ACCT-ID PIC 9(11)` (alternate
key `CXACAIX`). Only `XREF-CARD-NUM` is used, as the card number of the generated transaction.

### TRANSACT — `CVTRA05Y` `TRAN-RECORD` (RECLN 350)

`TRAN-ID X(16)`, `TRAN-TYPE-CD X(02)`, `TRAN-CAT-CD 9(04)`, `TRAN-SOURCE X(10)`, `TRAN-DESC X(100)`,
`TRAN-AMT S9(09)V99`, `TRAN-MERCHANT-ID 9(09)`, `TRAN-MERCHANT-NAME X(50)`, `TRAN-MERCHANT-CITY X(50)`,
`TRAN-MERCHANT-ZIP X(10)`, `TRAN-CARD-NUM X(16)`, `TRAN-ORIG-TS X(26)`, `TRAN-PROC-TS X(26)`,
FILLER `X(20)`.

---

## 3. File access pattern (`CBACT04C.cbl:28-56`)

| File | Organization | Access | Key |
|---|---|---|---|
| TCATBALF | indexed | sequential | `FD-TRAN-CAT-KEY` |
| XREFFILE | indexed | random | `FD-XREF-CARD-NUM`, alternate `FD-XREF-ACCT-ID` |
| ACCTFILE | indexed | random | `FD-ACCT-ID` |
| DISCGRP | indexed | random | `FD-DISCGRP-KEY` |
| TRANSACT | sequential | sequential | — (output only) |

All five are opened up front (`CBACT04C.cbl:182-186`) and closed at the end (`:224-228`); ACCTFILE is
opened `I-O` and rewritten in place (`:356`), TRANSACT is opened `OUTPUT` and only ever written (`:500`).

---

## 4. Control flow (`CBACT04C.cbl:180-232`)

```
DISPLAY 'START OF EXECUTION OF PROGRAM CBACT04C'          :181
open TCATBALF, XREFFILE, DISCGRP, ACCTFILE, TRANSACT      :182-186
PERFORM UNTIL END-OF-FILE = 'Y'                           :188
  IF END-OF-FILE = 'N'
    1000-TCATBALF-GET-NEXT                                :190
    IF END-OF-FILE = 'N'
      ADD 1 TO WS-RECORD-COUNT                            :192
      DISPLAY TRAN-CAT-BAL-RECORD                         :193
      IF TRANCAT-ACCT-ID NOT = WS-LAST-ACCT-NUM           :194
        IF WS-FIRST-TIME NOT = 'Y' 1050-UPDATE-ACCOUNT    :195-196
        ELSE MOVE 'N' TO WS-FIRST-TIME                    :197-198
        MOVE 0 TO WS-TOTAL-INT                            :200
        MOVE TRANCAT-ACCT-ID TO WS-LAST-ACCT-NUM          :201
        1100-GET-ACCT-DATA   (key = TRANCAT-ACCT-ID)      :202-203
        1110-GET-XREF-DATA   (alt key = TRANCAT-ACCT-ID)  :204-205
      END-IF
      key DISCGRP = ACCT-GROUP-ID + TRANCAT-TYPE-CD + TRANCAT-CD   :210-212
      1200-GET-INTEREST-RATE                              :213
      IF DIS-INT-RATE NOT = 0                             :214
        1300-COMPUTE-INTEREST                             :215
        1400-COMPUTE-FEES                                 :216
  ELSE
    1050-UPDATE-ACCOUNT                                   :220   <-- unreachable, see §7.1
END-PERFORM
close all five files                                      :224-228
DISPLAY 'END OF EXECUTION OF PROGRAM CBACT04C'            :230
GOBACK                                                    :232
```

There is no `CALL` to a business subroutine, no `XCTL`, no `LINK`, no COMMAREA and no
`CDEMO-TO-PROGRAM`/`CCARD-NEXT-PROG` navigation in this program: the only `CALL` is `'CEE3ABD'` in the
abend paragraph (`:632`).

---

## 5. Business rules

### 5.1 Account grouping (`:194-206`)

TCATBALF is browsed in key order, so all rows of one account arrive together. On the first row of a new
account the program: updates the **previous** account (unless this is the first account of the run),
resets `WS-TOTAL-INT` to zero, remembers the account id, reads ACCTDAT by that id, and reads CCXREF by
that id through the alternate index. The account record read here supplies `ACCT-GROUP-ID` for every
disclosure-group lookup of that account, and the xref supplies `XREF-CARD-NUM` for every generated
transaction of that account.

### 5.2 Disclosure-group lookup and DEFAULT fallback (`:210-212`, `1200-GET-INTEREST-RATE :415-440`, `1200-A-GET-DEFAULT-INT-RATE :443-460`)

1. Read DISCGRP with key `ACCT-GROUP-ID (X(10)) + TRANCAT-TYPE-CD + TRANCAT-CD`.
2. Status `00` → use `DIS-INT-RATE` from that record.
3. Status `23` (record not found) → `DISPLAY 'DISCLOSURE GROUP RECORD MISSING'`,
   `DISPLAY 'TRY WITH DEFAULT GROUP CODE'`, then move the literal `'DEFAULT'` into the group id of the key
   (padded to `X(10)`, i.e. `'DEFAULT   '`) and read again, **keeping the same transaction type and
   category**.
4. Any other status → `'ERROR READING DISCLOSURE GROUP FILE'` + abend.
5. On the retry, anything other than status `00` (including a missing DEFAULT row) →
   `'ERROR READING DEFAULT DISCLOSURE GROUP'` + abend.

The fallback is one level deep and only ever substitutes the *group id*.

### 5.3 Interest arithmetic (`1300-COMPUTE-INTEREST :462-470`)

```cobol
COMPUTE WS-MONTHLY-INT = ( TRAN-CAT-BAL * DIS-INT-RATE) / 1200
ADD WS-MONTHLY-INT TO WS-TOTAL-INT
```

* Only executed when `DIS-INT-RATE NOT = 0` (`:214`) — a zero rate produces no interest **and no
  transaction**, even though the category balance is non-zero.
* `WS-MONTHLY-INT PIC S9(09)V99` (`:168`) and the `COMPUTE` has **no `ROUNDED` phrase**, so the result is
  *truncated* to two decimals (toward zero), not rounded half-up. `-0.4999` stores as `-0.49`;
  `1.999` stores as `1.99`.
* `1200` = 12 months × 100 (the rate is a percentage).
* `WS-TOTAL-INT` accumulates the **already truncated** per-category amounts, so the account posting is the
  sum of the transaction amounts and never differs from them by a rounding remainder.

### 5.4 Generated transaction (`1300-B-WRITE-TX :473-515`)

| Field | Value | Line |
|---|---|---|
| `TRAN-ID` | `PARM-DATE` (10 chars) followed by `WS-TRANID-SUFFIX PIC 9(06)`, incremented by 1 *before* each write, starting at 0 → first id ends `000001` | `:474-480` |
| `TRAN-TYPE-CD` | `'01'` | `:482` |
| `TRAN-CAT-CD` | `'05'` moved to `PIC 9(04)` → `0005` | `:483` |
| `TRAN-SOURCE` | `'System'` (padded to `X(10)`) | `:484` |
| `TRAN-DESC` | `'Int. for a/c '` + `ACCT-ID` (`9(11)`, zero padded) → `Int. for a/c 00000000011`, padded to `X(100)` | `:485-489` |
| `TRAN-AMT` | `WS-MONTHLY-INT` | `:490` |
| `TRAN-MERCHANT-ID` | `0` | `:491` |
| `TRAN-MERCHANT-NAME/CITY/ZIP` | spaces | `:492-494` |
| `TRAN-CARD-NUM` | `XREF-CARD-NUM` of the account | `:495` |
| `TRAN-ORIG-TS`, `TRAN-PROC-TS` | current timestamp in DB2 format (§5.6), identical in both fields | `:496-498` |

The suffix is a **run-wide** counter, not per account, so ids are unique within a run and dense.

### 5.5 Account update (`1050-UPDATE-ACCOUNT :350-370`)

```cobol
ADD WS-TOTAL-INT TO ACCT-CURR-BAL
MOVE 0 TO ACCT-CURR-CYC-CREDIT
MOVE 0 TO ACCT-CURR-CYC-DEBIT
REWRITE FD-ACCTFILE-REC FROM ACCOUNT-RECORD
```

The cycle credit and debit totals are reset unconditionally as part of the same rewrite, so an account
that accrued zero interest is still rewritten with zeroed cycle buckets.

### 5.6 Timestamp (`Z-GET-DB2-FORMAT-TIMESTAMP :613-626`)

`FUNCTION CURRENT-DATE` reformatted as `YYYY-MM-DD-HH.MM.SS.hh0000` (26 characters), where `hh` is the
hundredths of a second from `COB-MIL` and the last four characters are the literal `'0000'`. This is a
clock read, not the PARM date.

### 5.7 Fees (`1400-COMPUTE-FEES :518-520`)

```cobol
1400-COMPUTE-FEES.
* To be implemented
    EXIT.
```

Empty in the source. The job name and the JCL comment ("compute interest and fees") advertise fees, but no
fee rule exists anywhere in the program.

---

## 6. Error paths and exact message text

Every message is a `DISPLAY` to SYSOUT. Where the table says "abend", the program performs
`9999-ABEND-PROGRAM` (`:628-632`), which displays `ABENDING PROGRAM` and calls `CEE3ABD` with ABCODE 999
and TIMING 0. Before every abend, `9910-DISPLAY-IO-STATUS` (`:635-648`) displays
`FILE STATUS IS: NNNN` followed by the four-character status: for a numeric status not starting with `9`
it is `'00'` + the two status characters (e.g. `0023`); otherwise the first character followed by the
binary value of the second character in three digits.

| Message (verbatim) | Condition | Line | Outcome |
|---|---|---|---|
| `START OF EXECUTION OF PROGRAM CBACT04C` | program entry | 181 | — |
| `ERROR OPENING TRANSACTION CATEGORY BALANCE` | TCATBALF open status ≠ `00` | 245 | abend |
| `ERROR OPENING CROSS REF FILE` (followed by the status value on the same `DISPLAY`) | XREFFILE open status ≠ `00` | 263 | abend |
| `ERROR OPENING DALY REJECTS FILE` | DISCGRP open status ≠ `00` (message names the wrong file — see §7.2) | 281 | abend |
| `ERROR OPENING ACCOUNT MASTER FILE` | ACCTFILE open status ≠ `00` | 300 | abend |
| `ERROR OPENING TRANSACTION FILE` | TRANSACT open status ≠ `00` | 318 | abend |
| `ERROR READING TRANSACTION CATEGORY FILE` | TCATBALF read status ∉ {`00`,`10`} | 342 | abend |
| `ERROR RE-WRITING ACCOUNT FILE` | ACCTFILE rewrite status ≠ `00` | 365 | abend |
| `ACCOUNT NOT FOUND: ` + key | ACCTFILE read INVALID KEY | 375 | falls through to the next check → abend |
| `ERROR READING ACCOUNT FILE` | ACCTFILE read status ≠ `00` | 386 | abend |
| `ACCOUNT NOT FOUND: ` + key | XREFFILE read INVALID KEY | 397 | falls through → abend |
| `ERROR READING XREF FILE` | XREFFILE read status ≠ `00` | 408 | abend |
| `DISCLOSURE GROUP RECORD MISSING` | DISCGRP read INVALID KEY | 418 | continue with DEFAULT |
| `TRY WITH DEFAULT GROUP CODE` | as above | 419 | continue with DEFAULT |
| `ERROR READING DISCLOSURE GROUP FILE` | DISCGRP read status ∉ {`00`,`23`} | 431 | abend |
| `ERROR READING DEFAULT DISCLOSURE GROUP` | DEFAULT retry status ≠ `00` | 455 | abend |
| `ERROR WRITING TRANSACTION RECORD` | TRANSACT write status ≠ `00` | 510 | abend |
| `ERROR CLOSING TRANSACTION BALANCE FILE` | TCATBALF close status ≠ `00` | 533 | abend |
| `ERROR CLOSING CROSS REF FILE` | XREFFILE close status ≠ `00` | 552 | abend |
| `ERROR CLOSING DISCLOSURE GROUP FILE` | DISCGRP close status ≠ `00` | 570 | abend |
| `ERROR CLOSING ACCOUNT FILE` | ACCTFILE close status ≠ `00` | 588 | abend |
| `ERROR CLOSING TRANSACTION FILE` | TRANSACT close status ≠ `00` | 606 | abend |
| `ABENDING PROGRAM` | any abend | 629 | `CEE3ABD` ABCODE 999 |
| `FILE STATUS IS: NNNN` + status | before every abend | 642, 646 | — |
| `END OF EXECUTION OF PROGRAM CBACT04C` | normal end | 230 | — |
| the TCATBALF record image | every record read | 193 | — |

Note the two `ACCOUNT NOT FOUND: ` messages are the *same literal* for two different files: the XREF path
displays `ACCOUNT NOT FOUND: ` with the xref alternate key (`:397`), not an "XREF NOT FOUND" message.

---

## 7. Quirks preserved (do not "fix")

### 7.1 The last account of the run is never updated

The `ELSE PERFORM 1050-UPDATE-ACCOUNT` at `:219-220` is **dead code**: the `PERFORM UNTIL END-OF-FILE = 'Y'`
at `:188` only enters the body while `END-OF-FILE = 'N'`, so the guarding `IF END-OF-FILE = 'N'` at `:189`
is always true on entry and the `ELSE` branch can never run. The iteration that hits end-of-file sets
`END-OF-FILE = 'Y'` inside `1000-TCATBALF-GET-NEXT` and then falls through the inner `IF` at `:191`, and
the loop exits.

Consequence: for the **highest account id present in TCATBALF**, the accrued interest is written as
transactions but is **never added to `ACCT-CURR-BAL`**, and its `ACCT-CURR-CYC-CREDIT` /
`ACCT-CURR-CYC-DEBIT` are **not reset**. Every other account is updated. This is preserved verbatim.

### 7.2 The DISCGRP open error names the rejects file

`0200-DISCGRP-OPEN` displays `ERROR OPENING DALY REJECTS FILE` (`:281`) — copy-paste from `CBTRN02C`.
The text is reproduced verbatim.

### 7.3 Truncation, not rounding

See §5.3. A "half-up" implementation would differ by one cent on roughly half of all non-terminating
divisions.

### 7.4 A zero rate suppresses the transaction entirely

`IF DIS-INT-RATE NOT = 0` (`:214`) wraps both the compute and the write, so a category with a zero rate
produces no zero-amount transaction. A category whose *balance* is zero but whose rate is non-zero does
produce a transaction, with amount `0.00`.

### 7.5 `WS-LAST-ACCT-NUM` is `X(11)`, compared against a `9(11)` field

`:167`, `:194`. Zoned decimal digits compare byte-wise as characters, so this behaves as an equality test
on the 11 digit characters. The initial value is `SPACES`, which is why `WS-FIRST-TIME` exists.

### 7.6 The run date is never validated

`PARM-DATE` is copied straight into the transaction id (§5.4). Any 10 characters are accepted, including
non-numeric ones, and the id inherits them.

### 7.7 A missing account or xref abends the whole job

There is no reject file in this program: one orphan TCATBALF row terminates the run (`:386`, `:408`).

---

## 8. Fixture reality (`app/data/ASCII/**`)

| File | Rows | Relevant content |
|---|---|---|
| `tcatbal.txt` | 50 | one row per account 1–50, all key `type 01 / cat 0001`, **all balances `+0.00`** |
| `acctdata.txt` | 50 | accounts 1–50; `ACCT-GROUP-ID` (offset 112) is **blank for every account** — the value `A000000000` sits in `ACCT-ADDR-ZIP` (offset 102) |
| `cardxref.txt` | 50 | one card per account 1–50 |
| `discgrp.txt` | 51 | groups `A000000000` (rates 1.50/2.50/…) and `DEFAULT` (`01/0001` = 1.50, `01/0002` = 2.50, `02/*` and `03/*` = 0.00) |

Because every account's group id is blank and no `'          '` group exists in DISCGRP, **every** lookup
in a fixture run takes the status-23 DEFAULT fallback (§5.2), and because every balance is zero, every
generated transaction has amount `0.00`. The fixtures therefore exercise the fallback path and the
zero-balance path end to end, but not the arithmetic; the parity section of the migration plan adds
hand-computed cases for that.

---

## 9. Source vs. inventory

* `docs/migration/CardDemo_inventory.md:139` lists S-12 as one program (`CBACT04C`) and two jobs
  (`INTCALC`, `COMBTRAN`) — this matches the source exactly.
* The inventory (and the INTCALC JCL comment) describe the job as computing "interest **and fees**"; the
  fee paragraph is empty in the source (§5.7). No fee rule exists to migrate.
* `migration/carddemo/README.md:32` reserves the package `com.carddemo.batch.interest` for S-12 while the
  stream brief for this session mandates `com.carddemo.batch.intcalc`. Both names are inside S-12's lane
  and no other stream can claim either; the code follows the session brief (`intcalc`) and the migration
  plan records the deviation.

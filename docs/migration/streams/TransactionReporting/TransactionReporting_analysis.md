# S-14 TransactionReporting — source analysis

Scope (from `docs/migration/CardDemo_inventory.md`): the batch reporting stream.

| Artefact | Role |
| --- | --- |
| `app/jcl/TRANREPT.jcl` | Daily transaction report job: unload → filter/sort → `CBTRN03C` |
| `app/proc/TRANREPT.prc` | The same three steps packaged as a procedure (`STEP01R`/`STEP05R`/`STEP10R`) |
| `app/proc/REPROC.prc` | `EXEC PGM=IDCAMS` wrapper used by both jobs' unload steps |
| `app/cbl/CBTRN03C.cbl` | The report writer |
| `app/jcl/PRTCATBL.jcl` | Print the transaction category balance file (`IEFBR14` + `IDCAMS` + `SORT`) |
| `app/cbl/CORPT00C.cbl` | **Caller only** — the CR00 online screen that submits `TRANREPT`; owned by S-07 |
| `app/cpy/CVTRA07Y.cpy` | Report record layouts |
| `app/cpy/CVTRA05Y.cpy`, `CVTRA03Y.cpy`, `CVTRA04Y.cpy`, `CVTRA01Y.cpy`, `CVACT03Y.cpy` | TRANSACT, tran type, tran category, TCATBALF, CARDXREF records |
| `app/data/ASCII/*.txt` | Fixtures used for the parity run |

No BMS map belongs to this stream: S-14 is batch only. The only screen involved is CR00
(`COTRN..`/`CORPT0A`), which belongs to S-07 and is not touched here.

---

## 1. `TRANREPT.jcl` — control flow

Three `EXEC` statements, in order (TRANREPT.jcl:23, :37, :59):

1. **`STEP05R  EXEC PROC=REPROC`** (TRANREPT.jcl:23-33). `REPROC` is an IDCAMS wrapper
   (`app/proc/REPROC.prc`: `PRC001 EXEC PGM=IDCAMS`, `SYSIN DD DSN=&CNTLLIB(REPROCT)`), i.e. a
   `REPRO` of `FILEIN` into `FILEOUT`. Here it unloads `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` into a
   sequential `LRECL=350` generation `AWS.M2.CARDDEMO.TRANSACT.BKUP(+1)`. A KSDS `REPRO` is written
   in key order, i.e. ascending `TRAN-ID`.
2. **`STEP05R  EXEC PGM=SORT`** (TRANREPT.jcl:37-55) — note the JCL reuses the step name `STEP05R`
   for the second step, which z/OS allows and which matters below. `SYMNAMES` (TRANREPT.jcl:41-44):

   ```text
   TRAN-CARD-NUM,263,16,ZD
   TRAN-PROC-DT,305,10,CH
   PARM-START-DATE,C'2022-01-01'
   PARM-END-DATE,C'2022-07-06'
   ```

   ```text
   SORT FIELDS=(TRAN-CARD-NUM,A)
   INCLUDE COND=(TRAN-PROC-DT,GE,PARM-START-DATE,AND,
           TRAN-PROC-DT,LE,PARM-END-DATE)
   ```

   So: keep records whose `TRAN-PROC-TS` **date part** (columns 305-314 of the 350-byte record) is
   between the two parameter dates **inclusive**, and order what is left by card number ascending.
   The `PARM-*` literals are the two values CR00 substitutes into the JCL before submitting it
   (`CORPT00C.cbl:449-470`, see §4). Output: `AWS.M2.CARDDEMO.TRANSACT.DALY(+1)`, same DCB as
   `SORTIN` (`DCB=(*.SORTIN)`, TRANREPT.jcl:53), i.e. 350-byte records.
3. **`STEP10R  EXEC PGM=CBTRN03C`** (TRANREPT.jcl:59-80). DDs:
   `TRANFILE` = the sorted extract, `CARDXREF`, `TRANTYPE`, `TRANCATG` = the VSAM reference KSDSs,
   `DATEPARM` = `AWS.M2.CARDDEMO.DATEPARM`, and the output `TRANREPT` = `LRECL=133,RECFM=FB`
   generation `AWS.M2.CARDDEMO.TRANREPT(+1)`.

`app/proc/TRANREPT.prc` repeats exactly these three steps (its own first step is named `STEP01R`);
it carries no additional logic, so the job and the procedure are one specification.

## 2. `CBTRN03C` — control flow, layouts, rules

### 2.1 Files (CBTRN03C.cbl:29-56)

| SELECT | DD | Organization | Key |
| --- | --- | --- | --- |
| `TRANSACT-FILE` | `TRANFILE` | sequential | — |
| `XREF-FILE` | `CARDXREF` | indexed | `FD-XREF-CARD-NUM` (16) |
| `TRANTYPE-FILE` | `TRANTYPE` | indexed | `FD-TRAN-TYPE` (2) |
| `TRANCATG-FILE` | `TRANCATG` | indexed | `FD-TRAN-CAT-KEY` = type (2) + category (4) |
| `REPORT-FILE` | `TRANREPT` | sequential | — (133-byte records) |
| `DATE-PARMS-FILE` | `DATEPARM` | sequential | — |

### 2.2 Main line (CBTRN03C.cbl:159-215)

```text
DISPLAY 'START OF EXECUTION OF PROGRAM CBTRN03C'
open TRANFILE, TRANREPT, CARDXREF, TRANTYPE, TRANCATG, DATEPARM   (0000-…-0500-)
0550-DATEPARM-READ                                                (start/end date)
PERFORM UNTIL END-OF-FILE = 'Y'
   1000-TRANFILE-GET-NEXT
   IF TRAN-PROC-TS(1:10) >= WS-START-DATE AND <= WS-END-DATE  CONTINUE
   ELSE NEXT SENTENCE                                             (skip the record)
   IF END-OF-FILE = 'N'
      DISPLAY TRAN-RECORD
      IF WS-CURR-CARD-NUM NOT= TRAN-CARD-NUM
         IF WS-FIRST-TIME = 'N' → 1120-WRITE-ACCOUNT-TOTALS
         MOVE TRAN-CARD-NUM TO WS-CURR-CARD-NUM, FD-XREF-CARD-NUM
         1500-A-LOOKUP-XREF
      1500-B-LOOKUP-TRANTYPE                                      (key = TRAN-TYPE-CD)
      1500-C-LOOKUP-TRANCATG                                      (key = type + category)
      1100-WRITE-TRANSACTION-REPORT
   ELSE                                                           (end of file)
      DISPLAY 'TRAN-AMT ' TRAN-AMT / 'WS-PAGE-TOTAL' WS-PAGE-TOTAL
      ADD TRAN-AMT TO WS-PAGE-TOTAL, WS-ACCOUNT-TOTAL
      1110-WRITE-PAGE-TOTALS
      1110-WRITE-GRAND-TOTALS
close the six files                                               (9000-…-9500-)
DISPLAY 'END OF EXECUTION OF PROGRAM CBTRN03C'
```

Three consequences of that shape, all reproduced as-is (see the quirks in the FR):

- **Q-1 (end of file adds the last record again).** At EOF `TRAN-AMT` still holds the last record
  read, and it is added to the page and account totals a second time (CBTRN03C.cbl:198-201). The
  page total, and through it the grand total, are therefore larger than the sum of the detail lines
  by the amount of the last in-range transaction.
- **Q-2 (the last account never gets an account total).** `1120-WRITE-ACCOUNT-TOTALS` only runs on a
  *change* of card number (CBTRN03C.cbl:181-184), so the final card's account total line is never
  written; its amounts are carried into the page/grand totals only.
- **Q-3 (the date filter is applied twice).** `STEP05R` already filtered on the same dates, and
  CBTRN03C filters again on `TRAN-PROC-TS(1:10)` (CBTRN03C.cbl:173-178) using the dates from
  `DATEPARM`. The two are independent inputs and nothing checks that they agree.
- **Q-4 (`NEXT SENTENCE` skips to the end of the sentence).** An out-of-range record falls through
  to the closing `END-PERFORM`, so it is skipped without any counter being touched — including at
  EOF, where an out-of-range last record means the `ELSE` branch above never runs and the report
  ends without page/grand totals.

### 2.3 Working storage that drives the layout (CBTRN03C.cbl:125-140)

```cobol
05 WS-LINE-COUNTER   PIC 9(09) COMP-3 VALUE 0.
05 WS-PAGE-SIZE      PIC 9(03) COMP-3 VALUE 20.
05 WS-PAGE-TOTAL     PIC S9(09)V99 VALUE 0.
05 WS-ACCOUNT-TOTAL  PIC S9(09)V99 VALUE 0.
05 WS-GRAND-TOTAL    PIC S9(09)V99 VALUE 0.
05 WS-CURR-CARD-NUM  PIC X(16) VALUE SPACES.
```

`1100-WRITE-TRANSACTION-REPORT` (CBTRN03C.cbl:274-290):

- on the very first detail, moves the two dates into the header and writes the headers;
- when `FUNCTION MOD(WS-LINE-COUNTER, WS-PAGE-SIZE) = 0`, writes the page totals and a new header
  block — the counter counts *every* written line, headers and totals included, so a "page" is 20
  written lines, not 20 detail lines;
- adds `TRAN-AMT` to the page and account totals, then writes the detail line.

`1110-WRITE-PAGE-TOTALS` (:293-304) writes `REPORT-PAGE-TOTALS`, adds the page total to the grand
total, resets the page total, then writes a `TRANSACTION-HEADER-2` rule line (both counted).
`1120-WRITE-ACCOUNT-TOTALS` (:306-317) writes `REPORT-ACCOUNT-TOTALS`, resets the account total,
then a rule line. `1110-WRITE-GRAND-TOTALS` (:318-322) writes `REPORT-GRAND-TOTALS` and does **not**
count the line. `1120-WRITE-HEADERS` (:324-341) writes name header, blank line, `TRANSACTION-HEADER-1`
and `TRANSACTION-HEADER-2`.

### 2.4 Report layout (`app/cpy/CVTRA07Y.cpy`)

All records are 133 bytes (`LRECL=133`, TRANREPT.jcl:78).

```text
REPORT-NAME-HEADER   : 'DALYREPT'(38) 'Daily Transaction Report'(41) 'Date Range: '(12)
                       start(10) ' to '(4) end(10)
TRANSACTION-HEADER-1 : 'Transaction ID'(17) 'Account ID'(12) 'Transaction Type'(19)
                       'Tran Category'(35) 'Tran Source'(14) ' '(1) '        Amount'(16)
TRANSACTION-HEADER-2 : 133 × '-'
TRANSACTION-DETAIL   : id X(16) ' ' acct X(11) ' ' type X(02) '-' typedesc X(15) ' '
                       cat 9(04) '-' catdesc X(29) ' ' source X(10) '    '
                       amount -ZZZ,ZZZ,ZZZ.ZZ '  '
REPORT-PAGE-TOTALS   : 'Page Total'(11)    + 86 × '.' + +ZZZ,ZZZ,ZZZ.ZZ
REPORT-ACCOUNT-TOTALS: 'Account Total'(13) + 84 × '.' + +ZZZ,ZZZ,ZZZ.ZZ
REPORT-GRAND-TOTALS  : 'Grand Total'(12)   + 85 × '.' + +ZZZ,ZZZ,ZZZ.ZZ
```

Detail amounts use `-ZZZ,ZZZ,ZZZ.ZZ` (15 characters, blank sign when positive); the three totals use
`+ZZZ,ZZZ,ZZZ.ZZ` (mandatory `+` when not negative). Both suppress leading zeros to blanks and both
truncate rather than round, because the source fields are `S9(09)V99`.

### 2.5 Error paths — exact text

| Situation | Source | Text displayed |
| --- | --- | --- |
| Start | :160 | `START OF EXECUTION OF PROGRAM CBTRN03C` |
| Date parms read | :232-233 | `Reporting from ` start `to ` end |
| `DATEPARM` read error | :238 | `ERROR READING DATEPARM FILE` |
| `TRANFILE` read error | :266 | `ERROR READING TRANSACTION FILE` |
| Report write error | :354 | `ERROR WRITING REPTFILE` |
| Open errors | :387, :405, :423, :441, :459, :477 | `ERROR OPENING TRANFILE` / `ERROR OPENING REPTFILE` / `ERROR OPENING CROSS REF FILE` / `ERROR OPENING TRANSACTION TYPE FILE` / `ERROR OPENING TRANSACTION CATG FILE` / `ERROR OPENING DATE PARM FILE` |
| Close errors | :525, :543, :562, :580, :598, :616 | `ERROR CLOSING POSTED TRANSACTION FILE` / `ERROR CLOSING REPORT FILE` / `ERROR CLOSING CROSS REF FILE` / `ERROR CLOSING TRANSACTION TYPE FILE` / `ERROR CLOSING TRANSACTION CATG FILE` / `ERROR CLOSING DATE PARM FILE` |
| XREF miss | :487 | `INVALID CARD NUMBER : ` + card number, `IO-STATUS` 23 |
| Tran type miss | :497 | `INVALID TRANSACTION TYPE : ` + type code, `IO-STATUS` 23 |
| Tran category miss | :507 | `INVALID TRAN CATG KEY : ` + type+category, `IO-STATUS` 23 |
| Any of the above | :627-630 | `ABENDING PROGRAM`, then `CALL 'CEE3ABD' USING ABCODE(999), TIMING` |
| I/O status display | :633-644 | `FILE STATUS IS: NNNN` with the status in the last four characters |

Every failure is fatal: the paragraph displays its message, displays the I/O status and calls
`9999-ABEND-PROGRAM`. There is no skip-and-continue path anywhere in the program.

### 2.6 Field-by-field validation

There is none in the classic sense: CBTRN03C validates nothing about the content of a transaction.
The only conditions it evaluates are

1. the inclusive date window on `TRAN-PROC-TS(1:10)` (:173-177) — a record outside it is skipped
   silently, and a malformed date simply compares as a string;
2. the existence of the three reference records (:484-511) — a miss is an abend, not a message.

## 3. `PRTCATBL.jcl` — control flow

1. **`DELDEF EXEC PGM=IEFBR14`** with `THEFILE DD DISP=(MOD,DELETE)` on
   `AWS.M2.CARDDEMO.TCATBALF.REPT` (PRTCATBL.jcl:21-25): allocate-if-absent then delete, i.e. the
   previous report is always removed before the job writes a new one.
2. **`STEP05R EXEC PROC=REPROC`** (PRTCATBL.jcl:29-39): `REPRO` of `TCATBALF.VSAM.KSDS` into a
   `LRECL=50` generation `TCATBALF.BKUP(+1)`, in key order.
3. **`STEP10R EXEC PGM=SORT`** (PRTCATBL.jcl:43-63):

   ```text
   TRANCAT-ACCT-ID,1,11,ZD     TRANCAT-TYPE-CD,12,2,CH
   TRANCAT-CD,14,4,ZD          TRAN-CAT-BAL,18,11,ZD
   SORT FIELDS=(TRANCAT-ACCT-ID,A,TRANCAT-TYPE-CD,A,TRANCAT-CD,A)
   OUTREC FIELDS=(TRANCAT-ACCT-ID,X, TRANCAT-TYPE-CD,X, TRANCAT-CD,X,
       TRAN-CAT-BAL,EDIT=(TTTTTTTTT.TT),9X)
   ```

   Every position of the `EDIT` pattern is a `T`, so all nine integer digits print with no zero
   suppression and the pattern has no sign position; with DFSORT's default `SIGNS`, a negative
   balance prints exactly like a positive one.

   **Contradiction in the shipped JCL (C-1).** The `OUTREC` record is 11+1+2+1+4+1+12+9 = **41**
   bytes, while `SORTOUT` declares `DCB=(LRECL=40,...)` (PRTCATBL.jcl:61). On z/OS this pairing is
   an error (DFSORT would truncate or fail depending on the installation defaults); the shipped JCL
   cannot have both. `OUTREC` describes what the report is meant to contain, so the migration
   follows `OUTREC` and writes 41 characters. This is reported rather than silently reconciled.

There is no COBOL program in this job: the report *is* the SORT output.

## 4. Launch boundary — how CR00 starts `TRANREPT` (S-07 owns this side)

`CORPT00C` (CR00, "Transaction Reports") derives a report name and a date range and submits the job
through the CICS internal reader:

- `Monthly` — start = first of the current month, end = the current date
  (`MOVE 'Monthly' TO WS-REPORT-NAME`, CORPT00C.cbl:214);
- `Yearly` — start = 1 January, end = the current date (CORPT00C.cbl:240);
- `Custom` — the two dates typed on the screen, each validated by `CSUTLDTC`
  (CORPT00C.cbl:388-427); `MOVE 'Custom' TO WS-REPORT-NAME` (CORPT00C.cbl:433).
- the confirmed submission builds the JCL, substituting the dates into the two SYMNAMES lines,
  `"PARM-START-DATE,C'"` and `"PARM-END-DATE,C'"` (CORPT00C.cbl:105-112), and writes the records to
  the internal reader (CORPT00C.cbl:517-521):

  ```cobol
  EXEC CICS WRITEQ TD QUEUE ('JOBS') FROM (JCL-RECORD)
            LENGTH (LENGTH OF JCL-RECORD)
  ```

- the user is then told `<report name> report submitted for printing ...` (CORPT00C.cbl:449-452).

So the contract the online side needs is exactly **(report type, start date, end date)**, and the
report type is presentational: nothing in `TRANREPT.jcl` or `CBTRN03C` reads it. The dates reach the
batch side twice — as the SORT symbols and as the `DATEPARM` file — which is Q-3 above.

## 5. Data and fixtures

`app/data/ASCII/` provides `cardxref.txt` (50), `trantype.txt` (7), `trancatg.txt` (18),
`tcatbal.txt` (50) and `dailytran.txt` (300 × 350 bytes). There is **no TRANSACT unload** in the
fixtures, so the parity run derives one from `dailytran.txt` (CVTRA06Y and CVTRA05Y share the same
350-byte layout) by filling `TRAN-PROC-TS`; see the parity section of the migration plan.

Numeric fields in those fixtures are zoned decimal with the sign overpunched on the last digit
(`0000001838H` = +183.88). This matters for the parity harness: GnuCOBOL's default ASCII sign mode
reads such a byte as an invalid sign and zeroes the digit, so the parity run must be compiled with
`-fsign=EBCDIC`, which is what the mainframe data actually is.

## 6. Inventory cross-check

`docs/migration/CardDemo_inventory.md` lists S-14 as `TRANREPT`/`CBTRN03C` plus `PRTCATBL`, launched
from CR00 (S-07) — the source agrees. Two things the inventory does not record:

- `TRANREPT.jcl` names its first two steps identically (`STEP05R` twice, TRANREPT.jcl:23 and :37);
- the `LRECL=40` vs 41-byte `OUTREC` contradiction in `PRTCATBL.jcl` (C-1 above).

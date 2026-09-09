# S-15 FileReadUtilities — source analysis

Scope: the four sequential "read and print a master file" batch jobs of the CardDemo estate
(`docs/migration/CardDemo_inventory.md:142`).

| JCL | Program | Input | Outputs |
|---|---|---|---|
| `app/jcl/READACCT.jcl` | `app/cbl/CBACT01C.cbl` | ACCTDAT KSDS (`ACCTFILE`) | `SYSOUT` + three sequential datasets (`OUTFILE`, `ARRYFILE`, `VBRCFILE`) |
| `app/jcl/READCARD.jcl` | `app/cbl/CBACT02C.cbl` | CARDDAT KSDS (`CARDFILE`) | `SYSOUT` |
| `app/jcl/READXREF.jcl` | `app/cbl/CBACT03C.cbl` | CCXREF KSDS (`XREFFILE`) | `SYSOUT` |
| `app/jcl/READCUST.jcl` | `app/cbl/CBCUS01C.cbl` | CUSTDAT KSDS (`CUSTFILE`) | `SYSOUT` |

There is no screen, no COMMAREA, no `XCTL`/`LINK` and no symbolic transfer anywhere in this stream: all four
programs are `PROCEDURE DIVISION` … `GOBACK` batch main programs. The only outbound call other than the
abend service is `CALL 'COBDATFT'` in CBACT01C (boundary B-03).

---

## 1. Entry conditions

Each job is a single `EXEC PGM=` step run under a job card with no `PARM=`, no `SYSIN` and no control
cards; the programs read no run date and take no parameters.

* `READACCT.jcl:22-28` — extra `PREDEL EXEC PGM=IEFBR14` step that allocates
  `AWS.M2.CARDDEMO.ACCTDATA.PSCOMP`, `.ARRYPS` and `.VBPS` with `DISP=(MOD,DELETE,DELETE)`, i.e. deletes
  them if they exist so `STEP05` can create them `NEW,CATLG`.
* `READACCT.jcl:31-48` — `STEP05 EXEC PGM=CBACT01C` with DDs `ACCTFILE` (input KSDS), `OUTFILE`
  (`LRECL=107,RECFM=FB`), `ARRYFILE` (`LRECL=110,RECFM=FB`), `VBRCFILE` (`LRECL=84,RECFM=VB`), `SYSOUT`,
  `SYSPRINT`.
* `READCARD.jcl:22-28`, `READXREF.jcl:22-28`, `READCUST.jcl:21-27` — one `STEP05` each with the input DD
  (`CARDFILE` / `XREFFILE` / `CUSTFILE`) plus `SYSOUT`/`SYSPRINT`.

## 2. Record layouts

| Copybook | Record | Length | Used by |
|---|---|---:|---|
| `app/cpy/CVACT01Y.cpy` | `ACCOUNT-RECORD` | 300 | CBACT01C |
| `app/cpy/CVACT02Y.cpy` | `CARD-RECORD` | 150 | CBACT02C |
| `app/cpy/CVACT03Y.cpy` | `CARD-XREF-RECORD` | 50 | CBACT03C |
| `app/cpy/CVCUS01Y.cpy` | `CUSTOMER-RECORD` | 500 | CBCUS01C |
| `app/cpy/CODATECN.cpy` | `CODATECN-REC` | 80 | CBACT01C ↔ COBDATFT |

The `FD` record in each program is a two-field envelope (key + remainder, e.g.
`CBACT01C.cbl:52-55` `FD-ACCT-ID PIC 9(11)` + `FD-ACCT-DATA PIC X(289)`); the real layout comes from the
copybook the record is read `INTO` (`READ … INTO ACCOUNT-RECORD`, `CBACT01C.cbl:166`).

CBACT01C's three output layouts (`CBACT01C.cbl:56-85`):

* `OUT-ACCT-REC` — 107 bytes: `9(11)`, `X(01)`, three `S9(10)V99`, three `X(10)` dates, `S9(10)V99`,
  `S9(10)V99 COMP-3` (7 bytes packed), `X(10)`.
* `ARR-ARRAY-REC` — 110 bytes: `9(11)` + `OCCURS 5` of (`S9(10)V99` display, `S9(10)V99 COMP-3`) + `X(04)`
  filler.
* `VBR-REC` — `RECORDING MODE V`, `RECORD IS VARYING FROM 10 TO 80 DEPENDING ON WS-RECD-LEN`, so each
  record is written with the length currently in `WS-RECD-LEN` (12 or 39, see §4).

## 3. Control flow

All four share one shape (`CBACT01C.cbl:140-160`, `CBACT02C.cbl:70-87`, `CBACT03C.cbl:70-87`,
`CBCUS01C.cbl:70-87`):

```
DISPLAY 'START OF EXECUTION OF PROGRAM <pgm>'
open input (CBACT01C also opens the three outputs)
PERFORM UNTIL END-OF-FILE = 'Y'
    IF END-OF-FILE = 'N'
        PERFORM 1000-<file>-GET-NEXT
        IF END-OF-FILE = 'N'
            DISPLAY <record>            *> whole-record image
        END-IF
    END-IF
END-PERFORM
close input
DISPLAY 'END OF EXECUTION OF PROGRAM <pgm>'
GOBACK
```

`1000-…-GET-NEXT` (`CBACT01C.cbl:165-198` and the parallel paragraphs at `CBACT02C.cbl:92-116`,
`CBACT03C.cbl:92-116`, `CBCUS01C.cbl:92-116`) maps the file status to `APPL-RESULT`:

| Status | `APPL-RESULT` | Behaviour |
|---|---|---|
| `00` | 0 (`APPL-AOK`) | record processed |
| `10` | 16 (`APPL-EOF`) | `MOVE 'Y' TO END-OF-FILE`, loop ends, normal termination |
| anything else | 12 | `DISPLAY 'ERROR READING <file>'`, `9910-DISPLAY-IO-STATUS`, abend |

Opens (`CBACT01C.cbl:317-386` and `…:118-134` in the other three) abend on any status other than `00`, with
one message per DD; closes (`CBACT01C.cbl:388-404`, `…:136-152`) do the same.

### Per-record DISPLAY differences (quirks — preserved as-is)

| Program | Displays per record |
|---|---|
| CBACT01C | the 11 labelled field lines + `'---…---'` separator (`1100-DISPLAY-ACCT-RECORD`, `CBACT01C.cbl:200-213`), then `VBRC-REC1:`/`VBRC-REC2:` (`CBACT01C.cbl:283-284`), then the raw 300-byte `ACCOUNT-RECORD` image from the main loop (`CBACT01C.cbl:151`) |
| CBACT02C | **once** — the in-paragraph `DISPLAY CARD-RECORD` is commented out (`CBACT02C.cbl:96`), only the main loop displays (`CBACT02C.cbl:78`) |
| CBACT03C | **twice** — `CBACT03C.cbl:96` inside `1000-XREFFILE-GET-NEXT` *and* `CBACT03C.cbl:78` in the main loop |
| CBCUS01C | **twice** — `CBCUS01C.cbl:96` and `CBCUS01C.cbl:78` |

## 4. CBACT01C output construction

`1300-POPUL-ACCT-RECORD` (`CBACT01C.cbl:215-240`):

1. Copies id, status, balance, both limits, open and expiry date straight across.
2. `MOVE ACCT-REISSUE-DATE TO CODATECN-INP-DATE WS-REISSUE-DATE`, `MOVE '2' TO CODATECN-TYPE` and
   `CODATECN-OUTTYPE`, then `CALL 'COBDATFT' USING CODATECN-REC` (boundary B-03) and
   `MOVE CODATECN-0UT-DATE TO OUT-ACCT-REISSUE-DATE`. With in-type `2` (`YYYY-MM-DD`) and out-type `2`
   (`YYYYMMDD`) the Assembler (`app/asm/COBDATFT.asm:46-54`) builds `YYYY` + input+5(2) + input+8(2) and
   leaves the rest of the 20-byte output field untouched, so the `X(10)` target receives `YYYYMMDD` plus
   two trailing blanks.
3. `MOVE ACCT-CURR-CYC-CREDIT TO OUT-ACCT-CURR-CYC-CREDIT`.
4. **Quirk** (`CBACT01C.cbl:236-238`): `OUT-ACCT-CURR-CYC-DEBIT` is written **only** when
   `ACCT-CURR-CYC-DEBIT` is zero, and then with the literal `2525.00` — the account's own debit value is
   never copied. Because the FD record area is not re-initialised per record, a non-zero-debit account
   inherits whatever the previous record left in that field.
5. `MOVE ACCT-GROUP-ID TO OUT-ACCT-GROUP-ID`.

`1400-POPUL-ARRAY-RECORD` (`CBACT01C.cbl:253-261`), after `INITIALIZE ARR-ARRAY-REC`
(`CBACT01C.cbl:169`, so all five occurrences start at zero and the filler at spaces), fills **three** of
the five occurrences with a mix of the account balance and literals:

| Occurrence | `ARR-ACCT-CURR-BAL` | `ARR-ACCT-CURR-CYC-DEBIT` |
|---|---|---|
| 1 | `ACCT-CURR-BAL` | `1005.00` |
| 2 | `ACCT-CURR-BAL` | `1525.00` |
| 3 | `-1025.00` | `-2500.00` |
| 4, 5 | 0 | 0 |

`1500-POPUL-VBRC-RECORD` (`CBACT01C.cbl:276-285`) builds two variable-length records for the same account
and displays both: `VBRC-REC1` = id + active status (written with `WS-RECD-LEN = 12`,
`CBACT01C.cbl:288-290`), `VBRC-REC2` = id + balance + credit limit + the **year only** of the reissue date
taken from `WS-ACCT-REISSUE-YYYY`, i.e. the first four characters of the raw `ACCT-REISSUE-DATE`
(`CBACT01C.cbl:131-137`, written with `WS-RECD-LEN = 39`, `CBACT01C.cbl:303-305`).

Every write checks its file status and accepts `00` **or** `10`; anything else displays
`'ACCOUNT FILE WRITE STATUS IS:'` — the same literal for all three files, including the array and VB files
(`CBACT01C.cbl:246, 268, 294, 309`) — then the I/O status and an abend.

## 5. Error paths and exact message text

`9910-DISPLAY-IO-STATUS` (`CBACT01C.cbl:413-426`, identical logic as `Z-DISPLAY-IO-STATUS` in
`CBCUS01C.cbl:161-174`) prints a 4-character rendering of the 2-byte file status:

* status not numeric, or first byte `'9'`: first byte kept as-is, second byte taken as a **binary** byte
  value and printed in three digits (`'9'` + `%03d` of the byte) — e.g. status `9`/`0x04` → `9004`.
* otherwise: `'00'` + the status — e.g. `35` → `0035`.

The literal is `'FILE STATUS IS: NNNN'` followed by the 4 digits, so the emitted line is
`FILE STATUS IS: NNNN0035` (no space before the digits).

`9999-ABEND-PROGRAM` / `Z-ABEND-PROGRAM` (`CBACT01C.cbl:406-410`, `CBCUS01C.cbl:154-158`) displays
`'ABENDING PROGRAM'` and calls `CEE3ABD` with `ABCODE = 999`, `TIMING = 0` (boundary B-01).

Complete message inventory for the stream:

| Message | Source |
|---|---|
| `START OF EXECUTION OF PROGRAM CBACT01C` / `…CBACT02C` / `…CBACT03C` / `…CBCUS01C` | `CBACT01C.cbl:141`, `CBACT02C.cbl:71`, `CBACT03C.cbl:71`, `CBCUS01C.cbl:71` |
| `END OF EXECUTION OF PROGRAM …` | `CBACT01C.cbl:158`, `CBACT02C.cbl:85`, `CBACT03C.cbl:85`, `CBCUS01C.cbl:85` |
| `ERROR OPENING ACCTFILE` | `CBACT01C.cbl:328` |
| `ERROR OPENING OUTFILE` + status | `CBACT01C.cbl:345` |
| `ERROR OPENING ARRAYFILE` + status | `CBACT01C.cbl:363` |
| `ERROR OPENING VBRC FILE` + status | `CBACT01C.cbl:381` |
| `ERROR READING ACCOUNT FILE` | `CBACT01C.cbl:192` |
| `ACCOUNT FILE WRITE STATUS IS:` + status | `CBACT01C.cbl:246, 268, 294, 309` |
| `ERROR CLOSING ACCOUNT FILE` | `CBACT01C.cbl:399` |
| `ERROR OPENING CARDFILE` / `ERROR READING CARDFILE` / `ERROR CLOSING CARDFILE` | `CBACT02C.cbl:129, 110, 147` |
| `ERROR OPENING XREFFILE` / `ERROR READING XREFFILE` / `ERROR CLOSING XREFFILE` | `CBACT03C.cbl:129, 110, 147` |
| `ERROR OPENING CUSTFILE` / `ERROR READING CUSTOMER FILE` / `ERROR CLOSING CUSTOMER FILE` | `CBCUS01C.cbl:129, 110, 147` |
| `ABENDING PROGRAM` | all four |
| `FILE STATUS IS: NNNN` + 4 digits | all four |
| `ACCT-ID                 :` … `ACCT-GROUP-ID           :` (11 labels) and the 49-dash separator | `CBACT01C.cbl:201-212` |
| `VBRC-REC1:` / `VBRC-REC2:` | `CBACT01C.cbl:283-284` |
| `INVALID INPUT` (COBDATFT error field, never triggered by CBACT01C's `2`/`2` call) | `app/asm/COBDATFT.asm:56` |

## 6. File access pattern

`ORGANIZATION IS INDEXED`, `ACCESS MODE IS SEQUENTIAL` on the primary key for all four inputs
(`CBACT01C.cbl:29-33`, `CBACT02C.cbl:29-33`, `CBACT03C.cbl:29-33`, `CBCUS01C.cbl:29-33`): a plain
key-ordered browse of the whole file, no `START`, no random `READ`, no update. Keys are `ACCT-ID` (11),
`CARD-NUM` (16), `XREF-CARD-NUM` (16), `CUST-ID` (9). CBACT01C's three outputs are `ORGANIZATION
SEQUENTIAL`, opened `OUTPUT` (create/overwrite) and written record by record.

## 7. Validation and business rules

None. Nothing in this stream validates a field, rejects a record or updates data; the value of the stream is
the print formatting, the derived output records of CBACT01C, and the file-status/abend behaviour. The only
computation is the date reformat of §4.2 and the literal-filled derived records of §4.

## 8. Numeric display representation

The fixtures in `app/data/ASCII/**` (the ASCII unloads these VSAM files were built from, e.g.
`acctdata.txt:1`) carry signed `PIC S9(n)V99` DISPLAY fields as zoned decimal with the sign as an overpunch
on the last digit — `{ABCDEFGHI` for positive, `}JKLMNOPQR` for negative, which is what
`com.carddemo.common.batch.FixedWidthRecord.signed` decodes. A `DISPLAY` of such a field, or of a record
containing one, emits those bytes unchanged: `194.00` prints as `00000001940{`.

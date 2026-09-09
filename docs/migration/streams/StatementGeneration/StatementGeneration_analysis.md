# S-13 StatementGeneration — Stream Analysis

**Stream:** S-13 StatementGeneration (BATCH)
**SOURCE:** `choikh0423/aws-mainframe-modernization-carddemo` — `app/jcl/CREASTMT.JCL`, `app/cbl/CBSTM03A.CBL`,
`app/cbl/CBSTM03B.CBL`, `app/cpy/COSTM01.CPY`, boundary job `app/jcl/TXT2PDF1.JCL`.
**Target:** `com.carddemo.batch.statement` (Spring Batch 5, D-3), one `Job` per JCL job, one `Step` per `EXEC PGM=`.

All line cites below are `FILE:line` against the files as committed in this repository.

---

## 1. Scope and estate position

| Artifact | Role | Source |
| --- | --- | --- |
| `CREASTMT.JCL` | The whole stream: build a card-keyed copy of TRANSACT, delete last run's reports, run CBSTM03A | `CREASTMT.JCL:1-97` |
| `CBSTM03A.CBL` | Print one account statement per CARDXREF record, in plain text **and** HTML | `CBSTM03A.CBL:1-924` |
| `CBSTM03B.CBL` | I/O subroutine: OPEN/READ/READ-KEY/CLOSE for TRNXFILE, XREFFILE, CUSTFILE, ACCTFILE | `CBSTM03B.CBL:1-230` |
| `COSTM01.CPY` | The "transaction altered layout" — TRANSACT re-ordered with card number first | `COSTM01.CPY:20-36` |
| `TXT2PDF1.JCL` | **Boundary B-13** — separate job, renders the text statement to PDF with a TSO utility | `TXT2PDF1.JCL:24-40` |

CBSTM03A is a batch program: no BMS map, no CICS, no `XCTL`/`LINK`, no COMMAREA. The only inter-program
control transfer is a static `CALL 'CBSTM03B' USING WS-M03B-AREA` at nine sites (`CBSTM03A.CBL:351, 377,
401, 734, 746, 769, 787, 805, 835, 860, 877, 893, 909`) plus `CALL 'CEE3ABD'` on the abend path
(`CBSTM03A.CBL:923`). There is nothing symbolic to trace: `CDEMO-TO-PROGRAM` / `CCARD-NEXT-PROG` do not
appear anywhere in this stream.

## 2. JCL — `CREASTMT.JCL`

| Step | PGM | What it does | Cite |
| --- | --- | --- | --- |
| `DELDEF01` | IDCAMS | `DELETE` `TRXFL.SEQ` and `TRXFL.VSAM.KSDS`, `SET MAXCC=0`, then `DEFINE CLUSTER` for the KSDS: `KEYS(32 0)`, `RECORDSIZE(350 350)`, `INDEXED` | `CREASTMT.JCL:22-40` |
| `STEP010` | SORT | Reads `TRANSACT.VSAM.KSDS`; `SORT FIELDS=(263,16,CH,A,1,16,CH,A)` (card number, then tran id); `OUTREC FIELDS=(1:263,16,17:1,262,279:279,50)` writes the 350-byte `TRXFL.SEQ` | `CREASTMT.JCL:44-55` |
| `STEP020` | IDCAMS | `REPRO INFILE(TRXFL.SEQ) OUTFILE(TRXFL.VSAM.KSDS)`; `COND=(0,NE)` | `CREASTMT.JCL:56-62` |
| `STEP030` | IEFBR14 | `DISP=(MOD,DELETE,DELETE)` on `STATEMNT.HTML` and `STATEMNT.PS` — deletes the previous run's reports | `CREASTMT.JCL:66-75` |
| `STEP040` | CBSTM03A | DDs `TRNXFILE`(KSDS), `XREFFILE`, `ACCTFILE`, `CUSTFILE` in; `STMTFILE` (LRECL 80 FB) and `HTMLFILE` (LRECL 100 FB) out | `CREASTMT.JCL:79-96` |

**OUTREC field mapping.** TRANSACT is `CVTRA05Y` (350 bytes): TRAN-ID 1-16, TYPE-CD 17-18, CAT-CD 19-22,
SOURCE 23-32, DESC 33-132, AMT 133-143, MERCHANT-ID 144-152, MERCHANT-NAME 153-202, MERCHANT-CITY 203-252,
MERCHANT-ZIP 253-262, CARD-NUM 263-278, ORIG-TS 279-304, PROC-TS 305-330, FILLER 331-350
(`CVTRA05Y.cpy:5-18`). `OUTREC` produces: card number at 1-16, the first 262 bytes of the input (tran id
through merchant zip) at 17-278, and input 279-328 at 279-328. That is exactly `COSTM01` (`COSTM01.CPY:20-36`).

> **Quirk Q-1 (source, carried forward).** The third `OUTREC` field copies **50** bytes from 279, but
> ORIG-TS + PROC-TS are 52 bytes. `TRNX-PROC-TS` in the work file therefore holds only the first 24
> characters of the original PROC-TS, and output bytes 329-350 are blank. CBSTM03A never reads
> `TRNX-ORIG-TS`/`TRNX-PROC-TS`, so no statement is affected — but the work record is not a lossless copy.

**DCB quirk Q-2.** `STEP030` declares `HTMLFILE` with `LRECL=80` (`CREASTMT.JCL:69`) while `STEP040`
creates it with `LRECL=100` (`CREASTMT.JCL:94`). IEFBR14 only deletes, so the mismatch is inert.

## 3. `CBSTM03B` — the I/O subroutine

Single parameter `LK-M03B-AREA` (`CBSTM03B.CBL:100-112`), identical in layout to the caller's
`WS-M03B-AREA` (`CBSTM03A.CBL:71-83`):

| Field | PIC | Meaning |
| --- | --- | --- |
| `LK-M03B-DD` | X(8) | DD name selecting the file: `TRNXFILE`/`XREFFILE`/`CUSTFILE`/`ACCTFILE` |
| `LK-M03B-OPER` | X(1) | `O` open, `C` close, `R` sequential read, `K` keyed read, `W` write, `Z` rewrite |
| `LK-M03B-RC` | X(2) | the COBOL FILE STATUS of the operation |
| `LK-M03B-KEY` | X(25) | key value for `K` |
| `LK-M03B-KEY-LN` | S9(4) | key length used as `LK-M03B-KEY (1:LK-M03B-KEY-LN)` |
| `LK-M03B-FLDT` | X(1000) | the record read |

Dispatch is a single `EVALUATE LK-M03B-DD` (`CBSTM03B.CBL:118-128`); an unknown DD name falls to
`9999-GOBACK` (`CBSTM03B.CBL:127-131`) **leaving `LK-M03B-RC` untouched**.

| DD | File | ORGANIZATION / ACCESS | Key | Ops implemented | Cite |
| --- | --- | --- | --- | --- | --- |
| `TRNXFILE` | TRXFL KSDS | INDEXED / SEQUENTIAL | `FD-TRNXS-ID` = card(16) + tran id(16) | O, R, C | `CBSTM03B.CBL:31-35, 133-155` |
| `XREFFILE` | CARDXREF KSDS | INDEXED / SEQUENTIAL | `FD-XREF-CARD-NUM` X(16) | O, R, C | `CBSTM03B.CBL:37-41, 157-179` |
| `CUSTFILE` | CUSTDATA KSDS | INDEXED / RANDOM | `FD-CUST-ID` X(9) | O, K, C | `CBSTM03B.CBL:43-47, 181-204` |
| `ACCTFILE` | ACCTDATA KSDS | INDEXED / RANDOM | `FD-ACCT-ID` 9(11) | O, K, C | `CBSTM03B.CBL:49-53, 206-229` |

Every path funnels through `n900-EXIT`, which moves the file's FILE STATUS into `LK-M03B-RC`
(`CBSTM03B.CBL:152, 176, 201, 226`). Relevant statuses: `00` ok, `10` end of file (sequential read past the
last record), `23` record not found (keyed read), `35` file not found on OPEN, `39` DCB conflict.

> **Quirk Q-3.** `W` (write) and `Z` (rewrite) are declared as 88-levels (`CBSTM03B.CBL:107-108`) but no
> branch implements them: the paragraph falls through to `n900-EXIT` and returns the *previous* file status.
> CBSTM03A never sets them.
>
> **Quirk Q-4.** Sequential reads on TRNXFILE/XREFFILE are `READ ... INTO LK-M03B-FLDT` on an INDEXED file
> opened `ACCESS SEQUENTIAL`, i.e. ascending **key** order, not arrival order.
>
> **Quirk Q-5.** `CUSTFILE`'s key is `PIC X(09)` while the customer id is `PIC 9(09)` (`CUSTREC.cpy:5`); the
> caller moves the numeric id into an X(25) field and passes length 9, so the compare is character-wise on
> the zero-padded digits.

## 4. `CBSTM03A` — control flow

### 4.1 Entry conditions

No parameters, no COMMAREA. Before any file work the program walks the z/OS control blocks
(`CBSTM03A.CBL:266-291`): PSA → TCB → TIOT, displays `Running JCL : <job> Step <step>`, then displays every
DD name in the TIOT with `-- valid UCB` / `--  null UCB`. This is pure diagnostics (the program's stated
purpose is to exercise modernization tooling, `CBSTM03A.CBL:26-35`) and produces no file output.

It then `OPEN OUTPUT STMT-FILE HTML-FILE` and `INITIALIZE WS-TRNX-TABLE WS-TRN-TBL-CNTR`
(`CBSTM03A.CBL:293-294`).

### 4.2 The ALTER/GO TO open sequence

`0000-START` (`CBSTM03A.CBL:296-314`) is a dispatcher over `WS-FL-DD`, which starts as `'TRNXFILE'`
(`CBSTM03A.CBL:67`). Each branch `ALTER`s the `GO TO` inside `8100-FILE-OPEN` (`CBSTM03A.CBL:726-728`) and
jumps to it, and each open paragraph sets the *next* DD name and goes back to `0000-START`:

```
0000-START (WS-FL-DD='TRNXFILE') -> 8100-TRNXFILE-OPEN  (open + first read; WS-FL-DD := 'READTRNX')
0000-START (WS-FL-DD='READTRNX') -> 8500-READTRNX-READ  (load table; WS-FL-DD := 'XREFFILE')
0000-START (WS-FL-DD='XREFFILE') -> 8200-XREFFILE-OPEN  (open;        WS-FL-DD := 'CUSTFILE')
0000-START (WS-FL-DD='CUSTFILE') -> 8300-CUSTFILE-OPEN  (open;        WS-FL-DD := 'ACCTFILE')
0000-START (WS-FL-DD='ACCTFILE') -> 8400-ACCTFILE-OPEN  (open) -> GO TO 1000-MAINLINE
```

Cites: `8100-TRNXFILE-OPEN` 730-762, `8200-XREFFILE-OPEN` 765-781, `8300-CUSTFILE-OPEN` 783-799,
`8400-ACCTFILE-OPEN` 801-816, `8500-READTRNX-READ` 818-853. The `ALTER` is decorative: every branch of the
`EVALUATE` that reaches `8100-FILE-OPEN` proceeds to the paragraph the DD name already selected, and the
`WHEN OTHER` arm (`CBSTM03A.CBL:313-314`) can never fire because `WS-FL-DD` only ever holds the five values
above. The net effect is a fixed sequence: open TRNXFILE → load the transaction table → open XREFFILE →
open CUSTFILE → open ACCTFILE → mainline.

Every OPEN accepts `'00' OR '04'` and abends otherwise, after two DISPLAYs
(`CBSTM03A.CBL:736-742, 748-754, 771-777, 789-795, 807-813`).

### 4.3 Loading the transaction table (`8500-READTRNX-READ`)

`WS-TRNX-TABLE` (`CBSTM03A.CBL:225-233`) is `WS-CARD-TBL OCCURS 51`, each holding a 16-byte card number and
`WS-TRAN-TBL OCCURS 10` of (tran id X(16), rest X(318)); `WS-TRN-TBL-CTR OCCURS 51` holds the per-card
transaction count.

`8100-TRNXFILE-OPEN` reads the first record, saves its card number in `WS-SAVE-CARD`, sets `CR-CNT = 1`,
`TR-CNT = 0` (`CBSTM03A.CBL:756-759`) and does **not** store it. `8500-READTRNX-READ` then loops
(`CBSTM03A.CBL:818-853`), starting on that same first record — which now compares equal to `WS-SAVE-CARD`,
so it is stored at occurrence (1, 1):

* same card as `WS-SAVE-CARD` → `ADD 1 TO TR-CNT`;
* different card → `MOVE TR-CNT TO WS-TRCT (CR-CNT)`, `ADD 1 TO CR-CNT`, `MOVE 1 TO TR-CNT`;
* store card number, tran id and the 318-byte rest at `(CR-CNT, TR-CNT)`, save the card, read the next
  record; RC `00` loops, RC `10` exits, anything else abends;
* on exit `MOVE TR-CNT TO WS-TRCT (CR-CNT)` (`CBSTM03A.CBL:850`).

> **Quirk Q-6 (an empty work file abends).** The priming read in `8100-TRNXFILE-OPEN` accepts only `00` or
> `04` (`CBSTM03A.CBL:748-754`). If TRNXFILE holds no records the read returns `10`, which falls into the
> ELSE arm: the program displays `ERROR READING TRNXFILE` / `RETURN CODE: 10` and abends instead of
> producing empty statements.
>
> **Quirk Q-7 (silent capacity limits).** Nothing checks `CR-CNT <= 51` or `TR-CNT <= 10`. A 51st card or an
> 11th transaction on one card writes past the occurrence and corrupts adjacent storage (undefined
> behaviour, no diagnostic). Only the first 10 transactions of a card can be reported even in the benign
> case.

### 4.4 Mainline (`1000-MAINLINE`, `CBSTM03A.CBL:316-342`)

```
PERFORM UNTIL END-OF-FILE = 'Y'
    1000-XREFFILE-GET-NEXT      (sequential read of CARDXREF, in card-number order)
    IF still not EOF
        2000-CUSTFILE-GET       (keyed read CUSTFILE  by XREF-CUST-ID)
        3000-ACCTFILE-GET       (keyed read ACCTFILE  by XREF-ACCT-ID)
        5000-CREATE-STATEMENT   (header: text lines 0..13 + HTML header/name/address/basics)
        MOVE 1 TO CR-JMP / MOVE ZERO TO WS-TOTAL-AMT
        4000-TRNXFILE-GET       (transaction lines + totals + statement footer)
END-PERFORM
close TRNXFILE, XREFFILE, CUSTFILE, ACCTFILE, then STMT-FILE and HTML-FILE
```

* `1000-XREFFILE-GET-NEXT` (345-366): RC `00` continue, RC `10` sets `END-OF-FILE = 'Y'`, anything else
  DISPLAYs `ERROR READING XREFFILE` + `RETURN CODE: nn` and abends. The record is moved into
  `CARD-XREF-RECORD` (`CVACT03Y.cpy:4-8`) **unconditionally**, including after the `10` (end of file), but
  the mainline's `IF END-OF-FILE = 'N'` prevents it from being used.
* `2000-CUSTFILE-GET` (368-390) and `3000-ACCTFILE-GET` (392-414): keyed reads; **only `00` is accepted** —
  a missing customer or account (`23`) DISPLAYs `ERROR READING CUSTFILE` / `ERROR READING ACCTFILE`, then
  `RETURN CODE: 23`, then abends. There is no "skip this card" path.
* Closes: `9100`/`9200`/`9300`/`9400` (856-919) each accept `00`/`04` and abend otherwise.
* `9999-ABEND-PROGRAM` (921-923): `DISPLAY 'ABENDING PROGRAM'` then `CALL 'CEE3ABD'`.

### 4.5 Statement header (`5000-CREATE-STATEMENT`, `CBSTM03A.CBL:458-504`)

1. `INITIALIZE STATEMENT-LINES` — resets the named fields only (FILLERs keep their `VALUE`s).
2. Write `ST-LINE0` to STMTFILE.
3. `5100-WRITE-HTML-HEADER` (506-555).
4. Build `ST-NAME` with `STRING CUST-FIRST-NAME DELIMITED BY ' ' , ' ', CUST-MIDDLE-NAME DELIMITED BY ' ',
   ' ', CUST-LAST-NAME DELIMITED BY ' ', ' '` into X(75) (462-469).
5. `MOVE CUST-ADDR-LINE-1 TO ST-ADD1`, `CUST-ADDR-LINE-2 TO ST-ADD2` (470-471); `ST-ADD3` is
   `STRING`ed from address line 3, state code, country code and zip, each `DELIMITED BY ' '` and separated
   by one space (472-481).
6. `MOVE ACCT-ID TO ST-ACCT-ID` (X(20) — 11 digits left-justified, 9 trailing spaces),
   `MOVE ACCT-CURR-BAL TO ST-CURR-BAL` (`PIC 9(9).99-`), `MOVE CUST-FICO-CREDIT-SCORE TO ST-FICO-SCORE`
   (X(20) — 3 digits left-justified) (483-485).
7. `5200-WRITE-HTML-NMADBS` (558-672).
8. Write text lines 1, 2, 3, 4, 5, 6, 5, 7, 8, 9, 10, 11, 12, 13, 12 (488-502).

### 4.6 Transactions and footer (`4000-TRNXFILE-GET`, `CBSTM03A.CBL:416-456`)

```
PERFORM VARYING CR-JMP FROM 1 BY 1
  UNTIL CR-JMP > CR-CNT OR (WS-CARD-NUM (CR-JMP) > XREF-CARD-NUM)
    IF XREF-CARD-NUM = WS-CARD-NUM (CR-JMP)
        PERFORM VARYING TR-JMP FROM 1 BY 1 UNTIL TR-JMP > WS-TRCT (CR-JMP)
            move slot into TRNX-RECORD; 6000-WRITE-TRANS; ADD TRNX-AMT TO WS-TOTAL-AMT
```

The scan relies on both the table and CARDXREF being in ascending card-number order and stops early at the
first table entry greater than the xref card. `WS-TOTAL-AMT` (`S9(9)V99` COMP-3) was zeroed by the mainline
before the call. Then `ST-TOTAL-TRAMT` is filled and text lines 12, 14A, 15 are written (433-437), followed
by the HTML footer `<tr>`, L10, `<h3>End of Statement</h3>`, `</td>`, `</tr>`, `</table>`, `</body>`,
`</html>` (439-454).

> **Quirk Q-8.** A card in CARDXREF with no transactions still produces a full statement whose only amount
> line is `Total EXP:` with a blank-suppressed zero. A card that has transactions but no CARDXREF record is
> never reported.
>
> **Quirk Q-9.** `WS-TOTAL-AMT` is `S9(9)V99`; totalling more than 999,999,999.99 truncates silently.

### 4.7 Record layouts written

**STMTFILE, `FD-STMTFILE-REC PIC X(80)`** (`CBSTM03A.CBL:44-45`), lines defined at 85-146:

| Line | Content |
| --- | --- |
| `ST-LINE0` | 31 × `*`, `START OF STATEMENT`, 31 × `*` |
| `ST-LINE1` | `ST-NAME` X(75) + 5 spaces |
| `ST-LINE2` / `ST-LINE3` | `ST-ADD1` / `ST-ADD2` X(50) + 30 spaces |
| `ST-LINE4` | `ST-ADD3` X(80) |
| `ST-LINE5` / `10` / `12` | 80 × `-` |
| `ST-LINE6` | 33 spaces + `Basic Details ` (X(14)) + 33 spaces |
| `ST-LINE7` | `Account ID         :` + `ST-ACCT-ID` X(20) + 40 spaces |
| `ST-LINE8` | `Current Balance    :` + `ST-CURR-BAL` `PIC 9(9).99-` + 7 + 40 spaces |
| `ST-LINE9` | `FICO Score         :` + `ST-FICO-SCORE` X(20) + 40 spaces |
| `ST-LINE11` | 30 spaces + `TRANSACTION SUMMARY ` + 30 spaces |
| `ST-LINE13` | `Tran ID         ` + `Tran Details    ` in X(51) + `  Tran Amount` |
| `ST-LINE14` | `ST-TRANID` X(16) + space + `ST-TRANDT` X(49) + `$` + `ST-TRANAMT` `PIC Z(9).99-` |
| `ST-LINE14A` | `Total EXP:` + 56 spaces + `$` + `ST-TOTAL-TRAMT` `PIC Z(9).99-` |
| `ST-LINE15` | 32 × `*`, `END OF STATEMENT`, 32 × `*` |

`ST-TRANDT` is X(49) but `TRNX-DESC` is X(100) (`COSTM01.CPY:28`), so descriptions are truncated to 49
characters (**Quirk Q-10**, `CBSTM03A.CBL:677`). `ST-CURR-BAL` is `9(9).99-` while `ACCT-CURR-BAL` is
`S9(10)V99` (`CVACT01Y.cpy:7`), so a balance of 1,000,000,000.00 or more loses its high-order digit
(**Quirk Q-11**).

**HTMLFILE, `FD-HTMLFILE-REC PIC X(100)`** (`CBSTM03A.CBL:46-47`): the fixed lines are 88-level values on
`HTML-FIXED-LN` X(100) (`CBSTM03A.CBL:148-211`); the variable lines are built with `STRING`
(`CBSTM03A.CBL:212-223, 560-633, 686-716`). Two `STRING` idioms appear:

* `DELIMITED BY '*'` on a literal or fixed field — the delimiter never occurs, so the **whole** field
  (including its trailing spaces) is copied. `<p>` + `ST-TRANID` X(16) + `</p>` is therefore
  `<p>0000000000000001</p>`, but `<p>` + `ST-TRANDT` X(49) + `</p>` keeps all 49 columns before `</p>`.
* `DELIMITED BY '  '` (two spaces) on the name/address fields — copies up to the first double space, so
  trailing padding is stripped (`CBSTM03A.CBL:563, 571, 579, 587`). An address containing an internal
  double space is cut there (**Quirk Q-12**).

`L23-NAME` is X(50) fed from `ST-NAME` X(75), so the HTML name line drops anything past column 50
(**Quirk Q-13**, `CBSTM03A.CBL:220, 560`).

The HTML emission order is: header (5100) → name/address/basics/table headings (5200) → one `<tr>` block of
three cells per transaction (6000, 675-723) → footer (4000, 439-454).

## 5. Data the target already has

| Legacy file | Target table / entity | Note |
| --- | --- | --- |
| `CARDXREF.VSAM.KSDS` (`CVACT03Y`) | `card_xref` / `CardXrefRecord` | read in `card_num` order |
| `CUSTDATA.VSAM.KSDS` (`CUSTREC`) | `customers` / `CustomerRecord` | keyed by `cust_id` |
| `ACCTDATA.VSAM.KSDS` (`CVACT01Y`) | `accounts` / `AccountRecord` | keyed by `acct_id` |
| `TRANSACT.VSAM.KSDS` (`CVTRA05Y`) | `transactions` / `TransactionRecord` | STEP010 input |
| `TRXFL.SEQ` / `TRXFL.VSAM.KSDS` (`COSTM01`) | **new**, stream-private `statement_work_transactions` | STEP010/020 output, STEP040 input |
| `STATEMNT.PS` / `STATEMNT.HTML` | files written by STEP040 | 80 / 100 byte fixed records |

## 6. Contradictions with the inventory

* `docs/migration/CardDemo_inventory.md` lists CBSTM03B as called "at three sites". The literal
  `CALL 'CBSTM03B'` appears at **thirteen** sites (`CBSTM03A.CBL:351, 377, 401, 734, 746, 769, 787, 805,
  835, 860, 877, 893, 909`) — three *read* sites (XREF sequential, CUST keyed, ACCT keyed) plus the
  TRNXFILE open/read pair, four opens and four closes. The inventory's count is of the read call sites, not
  of the CALL statements.
* The inventory names `app/cpy/COSTM01.cpy`; the file on disk is `app/cpy/COSTM01.CPY` (upper case), as are
  `CREASTMT.JCL` and `CBSTM03A.CBL`. Cosmetic only.

## 7. Boundary B-13 (PDF)

`TXT2PDF1.JCL` is a **separate job** that runs `IKJEFT1B` with `%TXT2PDF BROWSE Y IN DD:INDD OUT
'AWS.M2.CARDDEMO.STATEMNT.PS.PDF'` (`TXT2PDF1.JCL:24-40`). The renderer is a licensed TSO/REXX load library
(`AWS.M2.LBD.TXT2PDF.LOAD`) that is not part of this repository, and the consolidated backend has no PDF
dependency in `migration/carddemo/backend/pom.xml`. See the migration plan for the deferral decision.

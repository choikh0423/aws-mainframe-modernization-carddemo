# 05 — Bill Payment (CB00) and Transaction Reports (CR00): Functional and Technical Specification

**Scope:** Online Bill Payment (`CB00` / `COBIL00C`) and online Transaction Reports (`CR00` / `CORPT00C`). This includes the batch job that `CR00` submits (`TRNRPT00` → `TRANREPT` proc), up to the point where the report program `CBTRN03C` is invoked.
**SOURCE:** `choikh0423/aws-mainframe-modernization-carddemo` @ `7fd2344fe22ea20b31dc8ae0d662d9379ec7d284`
**Source files:** `app/cbl/COBIL00C.cbl`, `app/cbl/CORPT00C.cbl`, `app/bms/COBIL00.bms`, `app/bms/CORPT00.bms`, `app/cpy/CVACT01Y.cpy`, `app/cpy/CVACT03Y.cpy`, `app/cpy/CVTRA05Y.cpy`, `app/jcl/TRANREPT.jcl`, `app/proc/TRANREPT.prc`. Supporting files that were read to resolve references: `COCOM01Y`, `CSDAT01Y`, `CSMSG01Y`, `COTTL01Y`, `COMEN02Y`, `COMEN01C`, `CSUTLDTC`, `REPROC.prc`, `CARDDEMO.CSD`, `XREFFILE.jcl`, `CBTRN03C` (interface only), and the generated symbolic maps `app/cpy-bms/COBIL00.CPY` and `CORPT00.CPY`.
**Entry points:** CICS transaction `CB00` → program `COBIL00C` (CARDDEMO.CSD:337-338). CICS transaction `CR00` → program `CORPT00C` (CARDDEMO.CSD:409-410). Both are reached from main-menu options 10 and 9 (COMEN02Y:74-84). The batch job `TRNRPT00` is generated in memory by `CORPT00C` and runs `PROC=TRANREPT`. `app/jcl/TRANREPT.jcl` is the standalone, hard-coded-date version of the same job.
**Purpose:** Bill Payment lets a user pay off an account's full current balance. It writes one payment transaction to `TRANSACT` and reduces `ACCT-CURR-BAL` by the amount paid. Transaction Reports lets a user choose a Monthly (current month), Yearly (current year) or Custom date range. It then submits a batch job through the CICS extrapartition TD queue `JOBS` (the JES internal reader). That job unloads `TRANSACT`, filters and sorts it by processed date and card number, and prints the report with `CBTRN03C`.

**Related specs (out of scope, linked rather than repeated):** Transaction List/View/Add (CT00/CT01/CT02), which also owns `TRANSACT` and the "max-key + 1" Tran ID pattern: [`TransactionManagement_functional_requirement.md`](../../migration/transaction-management/docs/TransactionManagement_functional_requirement.md). Stream inventory: [`CardDemo-Stream-Map.md`](../../migration/transaction-management/docs/CardDemo-Stream-Map.md) (streams #6 and #7).

**Conventions:** A cite `PROG:a-b` means lines a–b of the file at the commit above. COBIL00C = `app/cbl/COBIL00C.cbl`, CORPT00C = `app/cbl/CORPT00C.cbl`, TRANREPT.prc = `app/proc/TRANREPT.prc`, TRANREPT.jcl = `app/jcl/TRANREPT.jcl`, CSD = `app/csd/CARDDEMO.CSD`.

---

## 2. Functional requirements

### 2.1 CB00 — Bill Payment (COBIL00C)
| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-BP-01 | Select option 10 "Bill Payment" on the main menu | Bill Payment screen opens empty, with the header (Tran `CB00`, Prog `COBIL00C`, titles, date `mm/dd/yy`, time `hh:mm:ss`) and the cursor on "Enter Acct ID" | COMEN02Y:80-84; COBIL00C:112-122, 289-301, 319-338 |
| FR-BP-02 | Start `CB00` with no COMMAREA (e.g. type it on a blank screen) | Control passes to the sign-on screen `COSGN00C`. The Bill Payment screen is not shown | COBIL00C:107-109, 273-284 |
| FR-BP-03 | Press ENTER with Acct ID blank | See "Acct ID can NOT be empty..." with the cursor on Acct ID | COBIL00C:158-164 |
| FR-BP-04 | Enter an Acct ID, leave Confirm blank, press ENTER | The account is read. If its balance is > 0, "Your current balance is:" shows the balance (`+9999999999.99`) and the prompt "Confirm to make a bill payment..." appears with the cursor on Confirm | COBIL00C:182-184, 193-194, 236-242 |
| FR-BP-05 | Enter an Acct ID that does not exist in ACCTDAT | See "Account ID NOT found..." with the cursor on Acct ID | COBIL00C:359-364 |
| FR-BP-06 | Enter an Acct ID whose current balance is ≤ 0 (Confirm blank or Y) | The balance is shown and the message "You have nothing to pay..." appears. No transaction is written | COBIL00C:193-205 |
| FR-BP-07 | Enter an Acct ID, set Confirm = `Y`/`y`, press ENTER | A payment transaction for the full current balance is written to TRANSACT and the account balance is reduced by that amount. The screen is cleared and the green message "Payment successful.  Your Transaction ID is <id>." appears | COBIL00C:173-177, 208-235, 510-532 |
| FR-BP-08 | Set Confirm = `N`/`n`, press ENTER | The screen is cleared (Acct ID, balance, Confirm and message blanked). Nothing is written | COBIL00C:178-181, 552-566 |
| FR-BP-09 | Enter any other Confirm value | See "Invalid value. Valid values are (Y/N)..." with the cursor on Confirm | COBIL00C:185-190 |
| FR-BP-10 | Press PF3 | Return to the calling program (`CDEMO-FROM-PROGRAM`), or to `COMEN01C` if none is recorded | COBIL00C:128-135, 273-284 |
| FR-BP-11 | Press PF4 | Clear all input/output fields and redisplay the screen | COBIL00C:136-137, 552-566 |
| FR-BP-12 | Press any other AID key (PF1, PF5…, PA, CLEAR) | See "Invalid key pressed. Please see below..." | COBIL00C:138-141; CSMSG01Y:20-21 |
| FR-BP-13 | Arrive with `CDEMO-CB00-TRN-SELECTED` populated in the COMMAREA | Acct ID is pre-filled from that field and processed as if ENTER had been pressed (no producer found — see §9 Q-BP-09) | COBIL00C:116-121 |
| FR-BP-14 | Pay when TRANSACT already holds a record with the generated ID | See "Tran ID already exist..." (the account is still updated — see §9 Q-BP-01) | COBIL00C:533-539 |
| FR-BP-15 | Pay when the account has no card cross-reference via CXACAIX | See "Account ID NOT found..." (processing continues — see §9 Q-BP-02) | COBIL00C:423-428 |
| FR-BP-16 | Pay when an unexpected VSAM/CICS error occurs | See one of "Unable to lookup Account...", "Unable to lookup XREF AIX file...", "Unable to lookup Transaction...", "Unable to Add Bill pay Transaction...", "Unable to Update Account...". `RESP:`/`REAS:` is DISPLAYed to the job log | COBIL00C:365-371, 396-402, 429-435, 460-466, 489-495, 540-546 |

### 2.2 CR00 — Transaction Reports (CORPT00C)
| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-RP-01 | Select option 9 "Transaction Reports" on the main menu | Transaction Reports screen opens empty, with the header (Tran `CR00`, Prog `CORPT00C`) and the cursor on "Monthly" | COMEN02Y:74-78; CORPT00C:177-181, 556-580, 609-628 |
| FR-RP-02 | Start `CR00` with no COMMAREA | Control passes to the sign-on screen `COSGN00C` | CORPT00C:172-174, 540-551 |
| FR-RP-03 | Mark Monthly (any non-blank char), set Confirm = `Y`, press ENTER | A job is submitted for the 1st through the last day of the current month, and the green message "Monthly report submitted for printing ..." appears. All fields are cleared | CORPT00C:213-238, 445-454, 462-508 |
| FR-RP-04 | Mark Yearly, set Confirm = `Y`, press ENTER | A job is submitted for `YYYY-01-01` .. `YYYY-12-31` of the current year, and "Yearly report submitted for printing ..." appears | CORPT00C:239-255, 445-454 |
| FR-RP-05 | Mark Custom, enter Start/End MM/DD/YYYY, set Confirm = `Y`, press ENTER | The dates are validated and a job is submitted for the entered range. "Custom report submitted for printing ..." appears | CORPT00C:256-436, 445-454 |
| FR-RP-06 | Mark Custom and leave a date part empty | See the first applicable message, checked in this order: "Start Date - Month can NOT be empty...", "Start Date - Day can NOT be empty...", "Start Date - Year can NOT be empty...", "End Date - Month can NOT be empty...", "End Date - Day can NOT be empty...", "End Date - Year can NOT be empty...". The cursor goes to that field | CORPT00C:258-303 |
| FR-RP-07 | Mark Custom and enter a month > 12 or a non-numeric month/day/year | See "Start Date - Not a valid Month...", "Start Date - Not a valid Day...", "Start Date - Not a valid Year..." (and the `End Date -` equivalents) | CORPT00C:329-379 |
| FR-RP-08 | Mark Custom and enter a calendar-invalid date (e.g. 02/30/2023) | See "Start Date - Not a valid date..." or "End Date - Not a valid date..." | CORPT00C:388-426 |
| FR-RP-09 | Choose a report, leave Confirm blank, press ENTER | See "Please confirm to print the <Monthly/Yearly/Custom> report..." with the cursor on Confirm | CORPT00C:464-473 |
| FR-RP-10 | Choose a report, set Confirm = `N`/`n` | The screen is cleared with no message. No job is submitted | CORPT00C:480-483, 633-646 |
| FR-RP-11 | Set Confirm to any other value | See `"<value>" is not a valid value to confirm...` with the cursor on Confirm | CORPT00C:484-493 |
| FR-RP-12 | Press ENTER without marking Monthly, Yearly or Custom | See "Select a report type to print report..." with the cursor on Monthly | CORPT00C:437-442 |
| FR-RP-13 | Press PF3 | Return to the main menu `COMEN01C` (always, regardless of the caller) | CORPT00C:187-189, 540-551 |
| FR-RP-14 | Press any other AID key | See "Invalid key pressed. Please see below..." with the cursor on Monthly | CORPT00C:190-194; CSMSG01Y:20-21 |
| FR-RP-15 | Submit when the `JOBS` TDQ write fails | See "Unable to Write TDQ (JOBS)...". `RESP:`/`REAS:` is DISPLAYed | CORPT00C:525-534 |

### 2.3 Batch — report job TRNRPT00 / TRANREPT proc
| ID | Given input… the job… | Expected output | Source cite |
|----|-----------------------|-----------------|-------------|
| FR-RJ-01 | Given a confirmed CR00 request, the CICS program writes the 80-byte JCL records of job `TRNRPT00` to TDQ `JOBS`, one record per line, up to and including `/*EOF` | JES receives job `TRNRPT00` (CLASS=A, MSGCLASS=0, NOTIFY=&SYSUID). It uses `JCLLIB ORDER=('AWS.M2.CARDDEMO.PROC')` and `STEP10 EXEC PROC=TRANREPT` | CORPT00C:81-127, 496-523; CSD:499-504 |
| FR-RJ-02 | Given the submitted job, step `STEP01R` (nested `PROC=REPROC`) runs IDCAMS REPRO against TRANSACT | A new generation `AWS.M2.CARDDEMO.TRANSACT.BKUP(+1)` (LRECL 350, FB) is created as a sequential copy of the TRANSACT KSDS. The IDCAMS control statements come from `&CNTLLIB(REPROCT)` | TRANREPT.prc:21-31; REPROC.prc:21-29 |
| FR-RJ-03 | Given the backup, step `STEP05R` SORT keeps records with `TRAN-PROC-DT` (pos 305, len 10) between PARM-START-DATE and PARM-END-DATE inclusive, and sorts them ascending on `TRAN-CARD-NUM` (pos 263, len 16) | A new generation `AWS.M2.CARDDEMO.TRANSACT.DALY(+1)` is created with the same DCB as SORTIN. The online JCL overrides `SYMNAMES` with the user's dates | TRANREPT.prc:35-53; CORPT00C:97-114 |
| FR-RJ-04 | Given the filtered file, step `STEP10R` runs `CBTRN03C` with TRANFILE, CARDXREF, TRANTYPE, TRANCATG and DATEPARM | A new generation `AWS.M2.CARDDEMO.TRANREPT(+1)` (LRECL 133, FB) holds the formatted report. The online JCL overrides `DATEPARM` with an in-stream record `<start> <end>` | TRANREPT.prc:57-78; CORPT00C:115-123; CBTRN03C:220-233 |
| FR-RJ-05 | Given the standalone `app/jcl/TRANREPT.jcl` is run directly | The same three steps run inline (no PROC), with the hard-coded range `2022-01-01`..`2022-07-06`. DATEPARM is read from dataset `AWS.M2.CARDDEMO.DATEPARM` | TRANREPT.jcl:1-80 |

**Totals:** 16 FR-BP + 15 FR-RP + 5 FR-RJ = **36 FRs**.

---

## 3. Screen / interface specification

### 3.1 COBIL00 / map COBIL0A (24×80) — Bill Payment
Header fields (TRNNAME 4, TITLE01 40, CURDATE 8, PGMNAME 8, TITLE02 40, CURTIME 8) are ASKIP/FSET output fields filled by POPULATE-HEADER-INFO (COBIL00C:319-338). TITLE01 = `'      AWS Mainframe Modernization       '` and TITLE02 = `'              CardDemo                  '` (COTTL01Y:18-22). Static title: `Bill Payment` at (4,35).

| Label (screen text) | BMS field | Pos | Len | I/O | Attributes | Edit rules / program use | Cite |
|---|---|---|---|---|---|---|---|
| `Enter Acct ID:` | ACTIDIN | (6,21) | 11 | Input | FSET, IC, NORM, UNPROT; GREEN; UNDERLINE | Required (not spaces/low-values). Moved unchanged into `ACCT-ID` / `XREF-ACCT-ID` PIC 9(11). There is **no numeric or justification edit** (see §9 Q-BP-05) | COBIL00.bms:85-89; COBIL00C:158-171 |
| `Your current balance is: ` | CURBAL | (11,32) | 14 | Output | ASKIP, FSET, NORM; BLUE | `ACCT-CURR-BAL` edited through `WS-CURR-BAL PIC +9999999999.99` | COBIL00.bms:103-106; COBIL00C:56, 193-194 |
| `Do you want to pay your balance now. Please confirm: ` | CONFIRM | (15,60) | 1 | Input | FSET, NORM, UNPROT; GREEN; UNDERLINE | `Y`/`y` = pay; `N`/`n` = clear; blank = look up only; anything else is an error | COBIL00.bms:115-119; COBIL00C:173-191 |
| (message line) | ERRMSG | (23,1) | 78 | Output | ASKIP, BRT, FSET; RED by default | `WS-MESSAGE` X(80) is moved into the 78-byte field. Set to GREEN (`ERRMSGC = DFHGREEN`) on successful payment | COBIL00.bms:127-130; COBIL00C:293, 526 |
| (key legend) | — | (24,1) | 33 | Literal | ASKIP; YELLOW | `ENTER=Continue  F3=Back  F4=Clear` | COBIL00.bms:131-135 |

**PF-key map (COBIL00C:125-142):**
| Key | Action |
|---|---|
| ENTER | PROCESS-ENTER-KEY (validate, look up, pay) |
| PF3 | XCTL to `CDEMO-FROM-PROGRAM`, or `COMEN01C` if it is blank |
| PF4 | Clear screen (CLEAR-CURRENT-SCREEN) |
| other | "Invalid key pressed. Please see below..." |

### 3.2 CORPT00 / map CORPT0A (24×80) — Transaction Reports
The header fields match §3.1 (CORPT00C:609-628). Static title: `Transaction Reports` at (4,30).

| Label | BMS field | Pos | Len | I/O | Attributes | Edit rules / program use | Cite |
|---|---|---|---|---|---|---|---|
| `Monthly (Current Month)` | MONTHLY | (7,10) | 1 | Input | FSET, IC, NORM, UNPROT; GREEN; UNDERLINE | Any non-blank value selects it. Checked first | CORPT00.bms:80-93; CORPT00C:213 |
| `Yearly (Current Year)` | YEARLY | (9,10) | 1 | Input | FSET, NORM, UNPROT; GREEN; UNDERLINE | Any non-blank value. Checked second | CORPT00.bms:94-107; CORPT00C:239 |
| `Custom (Date Range)` | CUSTOM | (11,10) | 1 | Input | FSET, NORM, UNPROT; GREEN; UNDERLINE | Any non-blank value. Checked third | CORPT00.bms:108-121; CORPT00C:256 |
| `Start Date :` MM | SDTMM | (13,29) | 2 | Input | FSET, NORM, **NUM**, UNPROT | Required for Custom. NUMVAL-C → `99`. Must be numeric and ≤ `'12'` | CORPT00.bms:127-132; CORPT00C:259-265, 305-307, 329-336 |
| `Start Date :` DD | SDTDD | (13,34) | 2 | Input | FSET, NORM, NUM, UNPROT | Required. NUMVAL-C → `99`. Must be numeric and ≤ `'31'` | CORPT00.bms:138-143; CORPT00C:266-272, 309-311, 338-345 |
| `Start Date :` YYYY | SDTYYYY | (13,39) | 4 | Input | FSET, NORM, NUM, UNPROT | Required. NUMVAL-C → `9999`. Must be numeric | CORPT00.bms:149-154; CORPT00C:273-279, 313-315, 347-353 |
| `End Date :` MM / DD / YYYY | EDTMM / EDTDD / EDTYYYY | (14,29) / (14,34) / (14,39) | 2 / 2 / 4 | Input | FSET, NORM, NUM, UNPROT | Same rules as Start | CORPT00.bms:166-193; CORPT00C:280-300, 317-327, 355-379 |
| (format hint) | — | (13,46), (14,46) | 12 | Literal | BLUE | `(MM/DD/YYYY)` | CORPT00.bms:157-160, 196-199 |
| `The Report will be submitted for printing. Please confirm: ` | CONFIRM | (19,66) | 1 | Input | FSET, NORM, UNPROT; GREEN; UNDERLINE | `Y`/`y` submit; `N`/`n` clear; blank → prompt; other → error | CORPT00.bms:200-210; CORPT00C:464-494 |
| (message line) | ERRMSG | (23,1) | 78 | Output | ASKIP, BRT, FSET; RED | Set to GREEN on successful submit | CORPT00.bms:218-221; CORPT00C:448, 560 |
| (key legend) | — | (24,1) | 23 | Literal | YELLOW | `ENTER=Continue  F3=Back` | CORPT00.bms:222-226 |

**PF-key map (CORPT00C:184-195):**
| Key | Action |
|---|---|
| ENTER | PROCESS-ENTER-KEY |
| PF3 | XCTL to `COMEN01C` |
| other | "Invalid key pressed. Please see below..." |

### 3.3 Job submission interface (CR00 → JES)
| Item | Value | Cite |
|---|---|---|
| TD queue | `JOBS`, TYPE(EXTRA), DDNAME(INREADER), TYPEFILE(OUTPUT), RECORDSIZE(80), RECORDFORMAT(FIXED), BLOCKFORMAT(UNBLOCKED), DISPOSITION(MOD), ERROROPTION(IGNORE) — description "SUBMIT JOBS FROM CICS" | CSD:499-504 |
| Write command | `EXEC CICS WRITEQ TD QUEUE('JOBS') FROM(JCL-RECORD) LENGTH(80)` once per JCL line | CORPT00C:515-523 |
| Generated JCL (one 80-byte record per line, in order) | `//TRNRPT00 JOB 'TRAN REPORT',CLASS=A,MSGCLASS=0,` · `// NOTIFY=&SYSUID` · `//*` · `//JOBLIB JCLLIB ORDER=('AWS.M2.CARDDEMO.PROC')` · `//*` · `//STEP10 EXEC PROC=TRANREPT` · `//*` · `//STEP05R.SYMNAMES DD *` · `TRAN-CARD-NUM,263,16,ZD` · `TRAN-PROC-DT,305,10,CH` · `PARM-START-DATE,C'<yyyy-mm-dd>'` · `PARM-END-DATE,C'<yyyy-mm-dd>'` · `/*` · `//STEP10R.DATEPARM DD *` · `<yyyy-mm-dd> <yyyy-mm-dd>` · `/*` · `/*EOF` | CORPT00C:81-125 |
| Date substitution | `PARM-START-DATE-1/-2` ← `WS-START-DATE`; `PARM-END-DATE-1/-2` ← `WS-END-DATE` (layout `YYYY-MM-DD`) | CORPT00C:60-71, 106, 111, 118-120, 220-221, 235-236, 247-253, 429-432 |

### 3.4 Batch DD / file I/O — TRANREPT proc (as invoked by TRNRPT00)
| Step | Program | DD | Dataset | DISP | Dir | DCB / notes | Cite |
|---|---|---|---|---|---|---|---|
| STEP01R (→ REPROC.PRC001) | IDCAMS | FILEIN | `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` | SHR | In | TRANSACT KSDS | TRANREPT.prc:21-25; REPROC.prc:21-26 |
| | | FILEOUT | `AWS.M2.CARDDEMO.TRANSACT.BKUP(+1)` | NEW,CATLG,DELETE | Out | LRECL=350, RECFM=FB; SPACE CYL(1,1) | TRANREPT.prc:27-31 |
| | | SYSIN | `&CNTLLIB(REPROCT)` with `CNTLLIB=AWS.M2.CARDDEMO.CNTL` | SHR | In | REPRO control cards (not in repo) | TRANREPT.prc:21-22; REPROC.prc:27-28 |
| | | SYSPRINT | SYSOUT=* | | Out | | REPROC.prc:22 |
| STEP05R | SORT | SORTIN | `AWS.M2.CARDDEMO.TRANSACT.BKUP(+1)` | SHR | In | | TRANREPT.prc:35-37 |
| | | SYMNAMES | in-stream; **overridden** by TRNRPT00 | | In | `TRAN-CARD-NUM,263,16,ZD`, `TRAN-PROC-DT,305,10,CH`, `PARM-START-DATE`, `PARM-END-DATE` | TRANREPT.prc:38-42; CORPT00C:97-114 |
| | | SYSIN | in-stream | | In | `SORT FIELDS=(TRAN-CARD-NUM,A)` and `INCLUDE COND=(TRAN-PROC-DT,GE,PARM-START-DATE,AND,TRAN-PROC-DT,LE,PARM-END-DATE)` | TRANREPT.prc:43-47 |
| | | SORTOUT | `AWS.M2.CARDDEMO.TRANSACT.DALY(+1)` | NEW,CATLG,DELETE | Out | DCB=*.SORTIN | TRANREPT.prc:49-53 |
| STEP10R | CBTRN03C | STEPLIB | `AWS.M2.CARDDEMO.LOADLIB` | SHR | | | TRANREPT.prc:57-59 |
| | | TRANFILE | `AWS.M2.CARDDEMO.TRANSACT.DALY(+1)` | SHR | In | filtered and sorted transactions | TRANREPT.prc:63-64 |
| | | CARDXREF | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` | SHR | In | | TRANREPT.prc:65-66 |
| | | TRANTYPE | `AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS` | SHR | In | | TRANREPT.prc:67-68 |
| | | TRANCATG | `AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS` | SHR | In | | TRANREPT.prc:69-70 |
| | | DATEPARM | `AWS.M2.CARDDEMO.DATEPARM` in the proc; **overridden** to in-stream `<start> <end>` by TRNRPT00 | SHR | In | 80-byte record: start X(10), space, end X(10) | TRANREPT.prc:71-72; CORPT00C:115-121; CBTRN03C:122-125 |
| | | TRANREPT | `AWS.M2.CARDDEMO.TRANREPT(+1)` | NEW,CATLG,DELETE | Out | LRECL=133, RECFM=FB | TRANREPT.prc:74-78 |
| | | SYSOUT/SYSPRINT | SYSOUT=* | | Out | | TRANREPT.prc:60-61 |

GDG bases `TRANSACT.BKUP`, `TRANSACT.DALY` and `TRANREPT` are defined in `app/jcl/DEFGDGB.jcl:25,31,37`.

---

## 4. Business rules and calculations

| ID | Rule | Cite |
|----|------|------|
| BR-01 | **Pay-in-full only.** The payment amount is always the account's entire `ACCT-CURR-BAL`. There is no partial-amount input | COBIL00C:224; COBIL00.bms:85-126 |
| BR-02 | **Balance check.** If `ACCT-CURR-BAL <= 0`, no payment is allowed ("You have nothing to pay..."). The check runs on both the look-up (Confirm blank) path and the Confirm=Y path, before any write | COBIL00C:197-206 |
| BR-03 | **Two-step confirmation.** ENTER with Confirm blank only displays the balance and prompts. A payment is made only when Confirm is `Y` or `y` (`CONF-PAY-YES`). `N`/`n` clears the screen | COBIL00C:156, 173-191, 210, 236-240 |
| BR-04 | **Transaction ID generation (max-key + 1).** `TRAN-ID` is set to HIGH-VALUES, then STARTBR, READPREV and ENDBR are issued on TRANSACT. The last key read is moved to `WS-TRAN-ID-NUM PIC 9(16)` and incremented by 1. On ENDFILE (empty file) the key is set to zeros, so the first ID is `0000000000000001` | COBIL00C:212-217, 219, 441-505 |
| BR-05 | **Payment transaction record (CVTRA05Y)**: `TRAN-ID` = generated ID; `TRAN-TYPE-CD` = `'02'`; `TRAN-CAT-CD` = `2` (→ `0002`); `TRAN-SOURCE` = `'POS TERM'`; `TRAN-DESC` = `'BILL PAYMENT - ONLINE'`; `TRAN-AMT` = `ACCT-CURR-BAL`; `TRAN-CARD-NUM` = `XREF-CARD-NUM` (from CXACAIX by account); `TRAN-MERCHANT-ID` = `999999999`; `TRAN-MERCHANT-NAME` = `'BILL PAYMENT'`; `TRAN-MERCHANT-CITY` = `'N/A'`; `TRAN-MERCHANT-ZIP` = `'N/A'`; `TRAN-ORIG-TS` = `TRAN-PROC-TS` = current timestamp. All other bytes come from `INITIALIZE TRAN-RECORD` | COBIL00C:218-232 |
| BR-06 | **Reference-data meaning of the codes.** In the sample data, type `02` = "Payment" and type/category `02`/`0002` = "Electronic payment". The program hard-codes these values and does not read TRANTYPE/TRANCATG | app/data/ASCII/trantype.txt:2; app/data/ASCII/trancatg.txt:7; COBIL00C:220-221 |
| BR-07 | **Timestamp format.** `EXEC CICS ASKTIME` + `FORMATTIME YYYYMMDD DATESEP('-') TIME TIMESEP(':')` produces `YYYY-MM-DD HH:MM:SS.000000` (26 chars). Microseconds are always zeros | COBIL00C:249-267; CSDAT01Y:42-55 |
| BR-08 | **Account balance update.** After the transaction write, `ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT`. With BR-01 this sets the balance to zero, unless truncation occurs (Q-BP-04). The record is then REWRITTEN under the lock from `READ … UPDATE`. No other account fields change (e.g. `ACCT-CURR-CYC-CREDIT` is untouched) | COBIL00C:234-235, 343-354, 377-385; CVACT01Y:7, 13 |
| BR-09 | **Card resolution.** The card number for the payment comes from `CXACAIX`: the CARDXREF alternate index keyed on `XREF-ACCT-ID` (AIX KEYS(11,25), NONUNIQUEKEY) | COBIL00C:171, 211, 408-418; XREFFILE.jcl:72-75; CSD:63-65 |
| BR-10 | **Order of operations (payment):** (1) READ ACCTDAT UPDATE; (2) balance check; (3) READ CXACAIX; (4) generate ID; (5) WRITE TRANSACT; (6) compute new balance; (7) REWRITE ACCTDAT. Error flags set in steps 3–5 do **not** stop the later steps (see Q-BP-01/02) | COBIL00C:177, 197-235 |
| BR-11 | **Report-type precedence.** Monthly is checked first, then Yearly, then Custom. The first field that is not spaces or low-values wins, and its value is not otherwise validated | CORPT00C:212-213, 239, 256, 437 |
| BR-12 | **Monthly range.** Start = current `YYYY-MM-01`. End = (first day of next month) − 1 day, computed with `FUNCTION INTEGER-OF-DATE` / `DATE-OF-INTEGER`. December rolls over to January of the next year | CORPT00C:213-236 |
| BR-13 | **Yearly range.** Start = current `YYYY-01-01`. End = current `YYYY-12-31` | CORPT00C:239-253 |
| BR-14 | **Custom date normalisation.** Each part is converted with `FUNCTION NUMVAL-C` into `PIC 99` / `PIC 9999` and written back to the screen field (so `1` becomes `01`). The range test happens afterwards | CORPT00C:305-327 |
| BR-15 | **Custom date part validation.** Month must be numeric and ≤ `'12'`. Day must be numeric and ≤ `'31'`. Year must be numeric. The comparisons are alphanumeric. There is no lower bound, so `00` passes this step | CORPT00C:329-379 |
| BR-16 | **Custom calendar validation.** `YYYY-MM-DD` is passed to `CSUTLDTC` (a wrapper around LE `CEEDAYS`) with format `'YYYY-MM-DD'`. The date is accepted if severity = `'0000'` **or** the message number = `'2513'`. 2513 = `X'09D1'`, which CSUTLDTC classifies as `FC-UNSUPP-RANGE` ("Unsupp. Range") | CORPT00C:388-426; CSUTLDTC:62-70, 116-124, 137-138 |
| BR-17 | **No start ≤ end check** and no future-date check is coded for Custom ranges | CORPT00C:256-436 |
| BR-18 | **Report confirmation.** Before submission, Confirm must be `Y`/`y`. Blank → prompt; `N`/`n` → clear; other → error | CORPT00C:462-494 |
| BR-19 | **JCL stream emission.** `JOB-LINES(1..1000)` are written in order. The loop stops after writing the first line that equals `'/*EOF'`, spaces or low-values (the terminating line itself is written), or on a TDQ error. `/*EOF` is the JES internal-reader end-of-job delimiter | CORPT00C:496-508, 515-523 |
| BR-20 | **Batch filter semantics.** SORT INCLUDE keeps `TRAN-PROC-DT` (first 10 bytes of `TRAN-PROC-TS`, offset 305) with `GE start AND LE end`, i.e. an inclusive range on processed date, not original date. CBTRN03C re-tests the same inclusive range on `TRAN-PROC-TS(1:10)` | TRANREPT.prc:39-46; CVTRA05Y:16-17; CBTRN03C:173-174 |
| BR-21 | **Batch sort order.** Output is ordered ascending on `TRAN-CARD-NUM` (offset 263, length 16, type ZD) so CBTRN03C can control-break per card | TRANREPT.prc:39, 44; CVTRA05Y:15 |
| BR-22 | **Offsets in SYMNAMES match CVTRA05Y.** TRAN-ID 1-16, TYPE 17-18, CAT 19-22, SOURCE 23-32, DESC 33-132, AMT 133-143, MERCHANT-ID 144-152, NAME 153-202, CITY 203-252, ZIP 253-262, **CARD-NUM 263-278**, ORIG-TS 279-304, **PROC-TS 305-330**, FILLER 331-350 | CVTRA05Y:4-18; CORPT00C:100-102 |

**Totals: 22 BRs.**

---

## 5. Data access

| Program | File (CICS name / DSN) | Operation | Key | Record copybook | Cite |
|---|---|---|---|---|---|
| COBIL00C | `ACCTDAT` / `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | READ **UPDATE** (on both the look-up and pay paths) | `ACCT-ID` 9(11) ← ACTIDIN | CVACT01Y `ACCOUNT-RECORD` (300) | COBIL00C:343-354 |
| COBIL00C | `ACCTDAT` | REWRITE | (from the prior READ UPDATE) | CVACT01Y | COBIL00C:377-385 |
| COBIL00C | `CXACAIX` / `AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH` | READ | `XREF-ACCT-ID` 9(11) (AIX, non-unique) | CVACT03Y `CARD-XREF-RECORD` (50) | COBIL00C:408-418; CSD:63-65 |
| COBIL00C | `TRANSACT` / `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` | BROWSE (STARTBR HIGH-VALUES, READPREV ×1, ENDBR) | `TRAN-ID` X(16) | CVTRA05Y `TRAN-RECORD` (350) | COBIL00C:441-505 |
| COBIL00C | `TRANSACT` | WRITE | `TRAN-ID` (generated) | CVTRA05Y | COBIL00C:510-520 |
| CORPT00C | TDQ `JOBS` (extrapartition → INTRDR) | WRITEQ TD | — | 80-byte `JCL-RECORD` | CORPT00C:515-523; CSD:499-504 |
| CORPT00C | — | CALL `CSUTLDTC` (date validation) | — | `CSUTLDTC-PARM` | CORPT00C:129-136, 392-394, 412-414 |
| TRNRPT00/TRANREPT | TRANSACT KSDS → BKUP(+1) → DALY(+1); CARDXREF, TRANTYPE, TRANCATG KSDS; DATEPARM; TRANREPT(+1) | batch READ / WRITE (see §3.4) | — | CVTRA05Y (350) for TRANSACT/BKUP/DALY | TRANREPT.prc:21-78 |

CORPT00C has `COPY CVTRA05Y` and declares `WS-TRANSACT-FILE 'TRANSACT'`, but issues **no** TRANSACT I/O itself (CORPT00C:40, 146).

---

## 6. Message catalogue

All texts are verbatim from source. Messages go to `ERRMSG` (78 bytes, red unless noted).

### 6.1 COBIL00C
| # | Exact text | Condition | Cite |
|---|---|---|---|
| M-BP-01 | `Acct ID can NOT be empty...` | ENTER with ACTIDIN blank or low-values | COBIL00C:159-162 |
| M-BP-02 | `Invalid value. Valid values are (Y/N)...` | Confirm not in {Y,y,N,n,blank} | COBIL00C:185-188 |
| M-BP-03 | `You have nothing to pay...` | `ACCT-CURR-BAL <= 0` | COBIL00C:198-202 |
| M-BP-04 | `Confirm to make a bill payment...` | Valid account with balance > 0 and Confirm blank | COBIL00C:236-239 |
| M-BP-05 | `Account ID NOT found...` | NOTFND on ACCTDAT READ, ACCTDAT REWRITE, or CXACAIX READ | COBIL00C:359-362, 390-393, 423-426 |
| M-BP-06 | `Unable to lookup Account...` | Other RESP on ACCTDAT READ | COBIL00C:365-369 |
| M-BP-07 | `Unable to Update Account...` | Other RESP on ACCTDAT REWRITE | COBIL00C:396-400 |
| M-BP-08 | `Unable to lookup XREF AIX file...` | Other RESP on CXACAIX READ | COBIL00C:429-433 |
| M-BP-09 | `Transaction ID NOT found...` | NOTFND on TRANSACT STARTBR | COBIL00C:454-457 |
| M-BP-10 | `Unable to lookup Transaction...` | Other RESP on STARTBR or READPREV | COBIL00C:460-464, 489-493 |
| M-BP-11 | `Payment successful. ` + ` Your Transaction ID is ` + `<TRAN-ID>` + `.` → renders as `Payment successful.  Your Transaction ID is <16-digit id>.` (two spaces after the first period) — **GREEN** | NORMAL on TRANSACT WRITE | COBIL00C:522-532 |
| M-BP-12 | `Tran ID already exist...` | DUPKEY/DUPREC on TRANSACT WRITE | COBIL00C:533-537 |
| M-BP-13 | `Unable to Add Bill pay Transaction...` | Other RESP on TRANSACT WRITE | COBIL00C:540-544 |
| M-BP-14 | `Invalid key pressed. Please see below...         ` (CCDA-MSG-INVALID-KEY, X(50) with trailing spaces) | AID other than ENTER/PF3/PF4 | COBIL00C:138-141; CSMSG01Y:20-21 |
| L-BP-01 | `RESP:` `<WS-RESP-CD>` `REAS:` `<WS-REAS-CD>` (DISPLAY to the CICS job log) | Every WHEN OTHER error branch | COBIL00C:366, 397, 430, 461, 490, 541 |

### 6.2 CORPT00C
| # | Exact text | Condition | Cite |
|---|---|---|---|
| M-RP-01 | `Start Date - Month can NOT be empty...` | Custom, SDTMM blank | CORPT00C:259-262 |
| M-RP-02 | `Start Date - Day can NOT be empty...` | Custom, SDTDD blank | CORPT00C:266-269 |
| M-RP-03 | `Start Date - Year can NOT be empty...` | Custom, SDTYYYY blank | CORPT00C:273-276 |
| M-RP-04 | `End Date - Month can NOT be empty...` | Custom, EDTMM blank | CORPT00C:280-283 |
| M-RP-05 | `End Date - Day can NOT be empty...` | Custom, EDTDD blank | CORPT00C:287-290 |
| M-RP-06 | `End Date - Year can NOT be empty...` | Custom, EDTYYYY blank | CORPT00C:294-297 |
| M-RP-07 | `Start Date - Not a valid Month...` | SDTMM not numeric or > '12' | CORPT00C:329-332 |
| M-RP-08 | `Start Date - Not a valid Day...` | SDTDD not numeric or > '31' | CORPT00C:338-341 |
| M-RP-09 | `Start Date - Not a valid Year...` | SDTYYYY not numeric | CORPT00C:347-349 |
| M-RP-10 | `End Date - Not a valid Month...` | EDTMM not numeric or > '12' | CORPT00C:355-358 |
| M-RP-11 | `End Date - Not a valid Day...` | EDTDD not numeric or > '31' | CORPT00C:364-367 |
| M-RP-12 | `End Date - Not a valid Year...` | EDTYYYY not numeric | CORPT00C:373-375 |
| M-RP-13 | `Start Date - Not a valid date...` | CSUTLDTC severity ≠ '0000' and msg ≠ '2513' | CORPT00C:396-401 |
| M-RP-14 | `End Date - Not a valid date...` | Same as M-RP-13, for the end date | CORPT00C:416-421 |
| M-RP-15 | `Select a report type to print report...` | None of Monthly/Yearly/Custom marked | CORPT00C:437-439 |
| M-RP-16 | `<Monthly\|Yearly\|Custom>` + ` report submitted for printing ...` — **GREEN** | Job lines written without error | CORPT00C:445-452 |
| M-RP-17 | `Please confirm to print the ` + `<name>` + ` report...` | Confirm blank | CORPT00C:464-470 |
| M-RP-18 | `"` + `<CONFIRM value>` + `" is not a valid value to confirm...` | Confirm not Y/y/N/n/blank | CORPT00C:484-490 |
| M-RP-19 | `Unable to Write TDQ (JOBS)...` | WRITEQ TD RESP ≠ NORMAL | CORPT00C:528-532 |
| M-RP-20 | `Invalid key pressed. Please see below...         ` | AID other than ENTER/PF3 | CORPT00C:190-194; CSMSG01Y:20-21 |
| L-RP-01 | `PROCESS ENTER KEY` (DISPLAY) | Every ENTER | CORPT00C:210 |
| L-RP-02 | `RESP:` `<WS-RESP-CD>` `REAS:` `<WS-REAS-CD>` (DISPLAY) | TDQ write failure | CORPT00C:529 |

### 6.3 Batch (CBTRN03C, logged; owned by the batch-reports scope)
| # | Exact text | Condition | Cite |
|---|---|---|---|
| L-RJ-01 | `Reporting from ` `<start>` ` to ` `<end>` | DATEPARM read OK | CBTRN03C:231-233 |
| L-RJ-02 | `ERROR READING DATEPARM FILE` (then abend) | DATEPARM status not 00/10 | CBTRN03C:237-241 |

---

## 7. Navigation / integration

| Direction | From → To | Mechanism | COMMAREA fields | Cite |
|---|---|---|---|---|
| Inbound | COMEN01C (option 10) → COBIL00C | `XCTL` with CARDDEMO-COMMAREA; FROM-TRANID/FROM-PROGRAM = menu; PGM-CONTEXT = 0 | `CDEMO-FROM-TRANID`, `CDEMO-FROM-PROGRAM`, `CDEMO-PGM-CONTEXT` | COMEN01C:177-187; COMEN02Y:80-84 |
| Inbound | COMEN01C (option 9) → CORPT00C | same | same | COMEN01C:177-187; COMEN02Y:74-78 |
| Inbound | (none found) → COBIL00C with `CDEMO-CB00-TRN-SELECTED` | the pre-fill path exists, but no program in `app/` sets this field | `CDEMO-CB00-INFO` (TRNID-FIRST/LAST, PAGE-NUM, NEXT-PAGE-FLG, TRN-SEL-FLG, TRN-SELECTED) | COBIL00C:64-72, 116-121 |
| Pseudo-conversational | COBIL00C ↔ terminal | `EXEC CICS RETURN TRANSID('CB00') COMMAREA(CARDDEMO-COMMAREA)`; `CDEMO-PGM-REENTER` (=1) marks a re-entry | `CDEMO-PGM-CONTEXT` | COBIL00C:112-113, 146-149 |
| Pseudo-conversational | CORPT00C ↔ terminal | `RETURN TRANSID('CR00') COMMAREA(...)` after every SEND (`GO TO RETURN-TO-CICS`) | `CDEMO-PGM-CONTEXT` | CORPT00C:177-178, 199-202, 580-591 |
| Outbound | COBIL00C → `CDEMO-FROM-PROGRAM` or COMEN01C (PF3); → COSGN00C (no COMMAREA) | `XCTL` with COMMAREA; sets FROM-TRANID=`CB00`, FROM-PROGRAM=`COBIL00C`, PGM-CONTEXT=0; TO-PROGRAM defaults to COSGN00C if blank | `CDEMO-TO-PROGRAM`, `CDEMO-FROM-*` | COBIL00C:128-135, 273-284 |
| Outbound | CORPT00C → COMEN01C (PF3); → COSGN00C (no COMMAREA) | `XCTL`; FROM-TRANID=`CR00`, FROM-PROGRAM=`CORPT00C` | same | CORPT00C:187-189, 540-551 |
| Outbound | CORPT00C → CSUTLDTC | static `CALL 'CSUTLDTC' USING date, format, result` | — | CORPT00C:392-394, 412-414 |
| Outbound | CORPT00C → JES (job TRNRPT00) | `WRITEQ TD QUEUE('JOBS')` → INTRDR | — | CORPT00C:515-523; CSD:499-504 |
| Batch chain | TRNRPT00 → PROC TRANREPT → (STEP01R → PROC REPROC / IDCAMS) → STEP05R SORT → STEP10R CBTRN03C | JCL | DATEPARM record | TRANREPT.prc:21-78 |

`CDEMO-USER-ID`, `CDEMO-USER-TYPE`, `CDEMO-ACCT-ID` and `CDEMO-CARD-NUM` are **not** used by either program. COBIL00C takes the account only from the screen (COBIL00C:170-171).

---

## 8. Edge cases, error handling, abend/return codes

| # | Case | Behaviour | Cite |
|---|---|---|---|
| E-01 | COBIL00C SEND does not end the task | `SEND-BILLPAY-SCREEN` returns to its caller, so processing continues after an error SEND. Later steps are skipped only where guarded by `IF NOT ERR-FLG-ON` (lines 169, 197, 208). The payment block (210-235) has **no** guard between its steps | COBIL00C:289-301, 169, 197, 208-235 |
| E-02 | CORPT00C SEND always ends the task | `SEND-TRNRPT-SCREEN` ends with `GO TO RETURN-TO-CICS`, so the first error message ends the transaction and no later checks run | CORPT00C:556-591 |
| E-03 | Double SEND on successful payment | WRITE success sends the success screen (532). Then REWRITE runs (235), and SEND runs again at 242 with the same message. If the REWRITE fails, its error SEND replaces the screen and is sent again at 242 — still with `ERRMSGC` = GREEN from line 526 | COBIL00C:526-532, 234-242, 396-402 |
| E-04 | Empty TRANSACT file | `READPREV` ENDFILE → TRAN-ID = zeros → ID 1. STARTBR on an empty file may instead return NOTFND (M-BP-09), and the flow then continues (E-01) | COBIL00C:454-459, 487-488 |
| E-05 | Account record locked | `READ … UPDATE` is issued even on the look-up-only path and is never released explicitly (no UNLOCK). It is released at task end (`RETURN`) | COBIL00C:182-184, 343-354, 146-149 |
| E-06 | Confirm `N` (CB00 and CR00) | Fields are cleared and the error flag is set to stop further processing. No message is shown | COBIL00C:178-181; CORPT00C:480-483 |
| E-07 | TDQ write fails mid-stream | The loop stops at the first failure (`ERR-FLG-ON`). Records already written stay on the INTRDR queue, so a partial job deck can reach JES. The TDQ is defined `ERROROPTION(IGNORE)` | CORPT00C:498-499, 525-534; CSD:501 |
| E-08 | Any non-blank char in Monthly/Yearly/Custom | Treated as selected. If several are marked, Monthly takes precedence over Yearly, and Yearly over Custom | CORPT00C:213, 239, 256 |
| E-09 | CB00/CR00 started without COMMAREA | XCTL to COSGN00C | COBIL00C:107-109; CORPT00C:172-174 |
| E-10 | CBTRN03C return codes/abend | DATEPARM status `10` → treated as EOF (`APPL-RESULT` 16). Any other non-00 → `ERROR READING DATEPARM FILE` and `9999-ABEND-PROGRAM` | CBTRN03C:220-243 |
| E-11 | Batch step failure | All new GDG generations use `DISP=(NEW,CATLG,DELETE)`, so they are deleted on abnormal step termination. No `COND=` / `IF` is coded, so the default JES rules apply | TRANREPT.prc:27-31, 49-53, 74-78 |
| E-12 | CSUTLDTC return code | CSUTLDTC sets `RETURN-CODE` to the CEEDAYS severity. CORPT00C ignores RETURN-CODE and tests only the result string | CSUTLDTC:97-98; CORPT00C:396-399 |
| E-13 | No explicit abends / HANDLE ABEND | Neither online program issues `EXEC CICS ABEND` or `HANDLE`. All CICS errors are handled with RESP | COBIL00C (whole); CORPT00C (whole) |

---

## 9. Open questions, ambiguities, apparent legacy bugs

These are reported as found and have **not** been fixed. "Inference" marks conclusions drawn from CICS/JES semantics that were not observed at runtime.

| ID | Finding | Cite |
|----|---------|------|
| Q-BP-01 | **Balance is reduced even if the TRANSACT write fails.** After WRITE-TRANSACT-FILE (DUPREC/other error), lines 234-235 still subtract the amount and REWRITE ACCTDAT. Money can "disappear" with no payment transaction recorded | COBIL00C:233-235, 533-546 |
| Q-BP-02 | **Payment continues after a CXACAIX failure.** On NOTFND or another error, the ID is still generated and the transaction is written with whatever is in `XREF-CARD-NUM` (uninitialised or stale), and the balance is updated | COBIL00C:211-235, 420-436 |
| Q-BP-03 | **Accounts with more than one card (inference).** CXACAIX is a NONUNIQUEKEY AIX. A CICS READ through the path returns the first record with RESP=DUPKEY when duplicates exist. That falls into WHEN OTHER → "Unable to lookup XREF AIX file...", and the flow continues per Q-BP-02. It is unclear whether the first card is the intended card | COBIL00C:420-436; XREFFILE.jcl:72-75 |
| Q-BP-04 | **Amount truncation.** `ACCT-CURR-BAL` is S9(10)V99 but `TRAN-AMT` is S9(09)V99. A balance ≥ 1,000,000,000.00 is truncated on the move, so the transaction amount is wrong and the leftover balance stays non-zero | COBIL00C:224, 234; CVACT01Y:7; CVTRA05Y:10 |
| Q-BP-05 | **Acct ID input is not edited.** ACTIDIN X(11) is moved directly into PIC 9(11) with no NUMVAL or numeric test. Short or left-justified input (e.g. `123` + spaces) or non-numeric input gives a non-matching key (NOTFND) rather than a validation message. It is unclear whether users must type all 11 zero-padded digits | COBIL00C:170-171; COBIL00.bms:85-89 |
| Q-BP-06 | **Race on ID generation.** max-key + 1 runs with no enqueue. Two concurrent payments can compute the same ID, and the second gets "Tran ID already exist..." while its balance is still updated (Q-BP-01). This is the same pattern as CT02 | COBIL00C:212-219 |
| Q-BP-07 | **Source code `'POS TERM'`** is used for an online bill payment. It is unclear whether this is intended or a copy-paste from POS transactions | COBIL00C:222 |
| Q-BP-08 | **Payment does not update cycle fields.** `ACCT-CURR-CYC-CREDIT`/`-DEBIT` are not touched. It is unclear whether interest/statement batch logic relies on POSTTRAN to reflect online payments. The payment is written straight to TRANSACT and is not routed through the daily-tran posting | COBIL00C:234-235; CVACT01Y:13-14 |
| Q-BP-09 | **Dead pre-fill path.** `CDEMO-CB00-INFO` (copied from the COTRN00C paging layout) and the `CDEMO-CB00-TRN-SELECTED` → ACTIDIN pre-fill are never set by any program in `app/`. On first entry the field holds uninitialised working storage. Also, its name says "TRN" but it is used as an account ID | COBIL00C:64-72, 116-121 |
| Q-BP-10 | **Success message has a double space** (`Payment successful.  Your Transaction ID is …`) because both literals carry a space. This should be kept verbatim for parity unless it is waived | COBIL00C:527-528 |
| Q-BP-11 | **Look-up path takes an update lock** (READ UPDATE with Confirm blank) that is never used | COBIL00C:182-184, 351 |
| Q-BP-12 | **Error after a green success keeps the green colour** (E-03) | COBIL00C:526, 396-402 |
| Q-BP-13 | **Stale `CURBAL` after errors.** Lines 193-194 move `ACCT-CURR-BAL` into CURBAL even after a failed READ or invalid Confirm (the SEND has already happened, so it is not visible in that task). It is unclear what is displayed on the redisplayed screen | COBIL00C:185-194 |
| Q-BP-14 | Unused working storage: `WS-TRAN-AMT`, `WS-TRAN-DATE`, `WS-USR-MODIFIED`. The header comment typo "tractionsaction" | COBIL00C:6, 48-50, 55, 58 |
| Q-RP-01 | **CEEDAYS 2513 ("Unsupp. Range") is accepted as valid.** Dates outside the LE-supported range (e.g. year < 1582) pass validation and are put into the JCL | CORPT00C:399, 419; CSUTLDTC:66, 137-138 |
| Q-RP-02 | **No start ≤ end check.** An inverted range submits a job that selects zero rows | CORPT00C:256-436 |
| Q-RP-03 | **NUMVAL-C on non-numeric input (inference).** For invalid arguments the result of NUMVAL-C is implementation-dependent (often 0). The subsequent "NOT NUMERIC" tests then never fire, and `00` passes the ≤'12'/≤'31' checks. The error the user sees then depends on CSUTLDTC (or Q-RP-01) | CORPT00C:305-379 |
| Q-RP-04 | **Proc statement name mismatch.** Member `TRANREPT.prc` starts with `//REPROC PROC`, but it is invoked as `PROC=TRANREPT` and itself calls `PROC=REPROC`. The name on the PROC statement of a cataloged procedure is not what is resolved, but it is confusing and could clash if the member were copied in-stream | TRANREPT.prc:1, 21; CORPT00C:94 |
| Q-RP-05 | **`REPROCT` control member is not in the repo.** STEP01R depends on `AWS.M2.CARDDEMO.CNTL(REPROCT)`, so the REPRO parameters cannot be verified | REPROC.prc:27-28 |
| Q-RP-06 | **Standalone `TRANREPT.jcl` defines two steps named `STEP05R`** (the REPROC unload and the SORT), and has hard-coded dates. A `STEP05R.*` override would be ambiguous | TRANREPT.jcl:23, 37, 43-44 |
| Q-RP-07 | **`TRAN-CARD-NUM` sorted as ZD** although it is X(16) in CVTRA05Y. This is fine for all-digit card numbers but would misorder non-digit content | CORPT00C:100; CVTRA05Y:15 |
| Q-RP-08 | **Hard-coded environment values** in the generated JCL (`CLASS=A`, `MSGCLASS=0`, `AWS.M2.CARDDEMO.PROC`, job name `TRNRPT00`, `NOTIFY=&SYSUID`). `&SYSUID` resolves to the CICS region's user, not the terminal user (inference) | CORPT00C:84-90 |
| Q-RP-09 | **No job-completion feedback.** CR00 reports "submitted" as soon as the TDQ writes succeed. The job number and outcome are not returned to the user | CORPT00C:445-454 |
| Q-RP-10 | **PF3 ignores the caller.** CR00 always returns to COMEN01C, unlike CB00, which honours `CDEMO-FROM-PROGRAM` | CORPT00C:187-189 vs COBIL00C:128-134 |
| Q-RP-11 | **Stream-Map inaccuracy.** `CardDemo-Stream-Map.md` lists CR00's primary data as "TRANSACT, TCATBALF". CORPT00C performs no file I/O except the TDQ, and the job reads TRANSACT/CARDXREF/TRANTYPE/TRANCATG, not TCATBALF | CORPT00C:40, 146, 515-523; TRANREPT.prc:63-72 |
| Q-RP-12 | Unused declarations: `COPY CVTRA05Y`, `WS-TRANSACT-FILE`, `WS-TRANSACT-EOF`, `WS-REC-COUNT`, `WS-SEND-ERASE-FLG` (never set to NO), and the paragraph-name typo `WIRTE-JOBSUB-TDQ` | CORPT00C:40-49, 56, 146, 507, 515 |
| Q-RP-13 | **CBTRN03C loop control (out of scope, noted for the batch owner).** An out-of-range record triggers `NEXT SENTENCE`, which jumps past the end of the `PERFORM … END-PERFORM.` sentence. That would stop the report loop, though the SORT pre-filter normally prevents it. The EOF branch also adds the last `TRAN-AMT` to the totals again | CBTRN03C:170-206 |
| Q-RP-14 | **TRANREPT is not scheduled.** It is not in the Control-M/CA7 definitions; it runs only on demand via CR00 or by manual submission | app/scheduler (no TRANREPT reference) |
| Q-RP-15 | **CSD batch-load job `CBADMCDJ.jcl` defines `COBIL00C`** (and mapset `COBIL00S`) but **not** `CORPT00C`, `CORPT00`, or the CB00/CR00 transactions. `CARDDEMO.CSD` does define them all. It is unclear which CSD source is authoritative | app/jcl/CBADMCDJ.jcl:83, 127; CSD:114, 137, 196, 242, 337, 409 |

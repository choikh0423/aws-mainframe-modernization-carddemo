# 07 — Batch: Statements, Customer Import/Export, File-Read Utilities, Date/Wait Utilities, Job Scheduling

## 1. Header

| Item | Value |
|------|-------|
| **Scope** | Account statement generation (text + HTML), branch-migration export/import (multi-record-type file), VSAM read/print utilities, date and wait utilities, VSAM/GDG/CICS file-definition jobs, Control-M and CA-7 job schedules |
| **Source** | `choikh0423/aws-mainframe-modernization-carddemo` @ `7fd2344fe22ea20b31dc8ae0d662d9379ec7d284` |
| **Programs** | `app/cbl/CBSTM03A.CBL`, `CBSTM03B.CBL`, `CBEXPORT.cbl`, `CBIMPORT.cbl`, `CBACT01C.cbl`, `CBACT02C.cbl`, `CBACT03C.cbl`, `CBCUS01C.cbl`, `CSUTLDTC.cbl`, `COBSWAIT.cbl`; `app/asm/COBDATFT.asm`, `app/asm/MVSWAIT.asm` (+ macros `app/maclib/COCDATFT.mac`, `app/maclib/ASMWAIT.mac`) |
| **Copybooks** | `app/cpy/COSTM01.CPY`, `CVEXPORT.cpy`, `CUSTREC.cpy`; also referenced: `CVACT01Y`, `CVACT02Y`, `CVACT03Y`, `CVCUS01Y`, `CVTRA05Y`, `CODATECN` |
| **JCL** | `app/jcl/CREASTMT.JCL`, `CBEXPORT.jcl`, `CBIMPORT.jcl`, `READACCT.jcl`, `READCARD.jcl`, `READCUST.jcl`, `READXREF.jcl`, `WAITSTEP.jcl`, `TXT2PDF1.JCL`, `INTRDRJ1.JCL`, `INTRDRJ2.JCL`, `FTPJCL.JCL`, `ACCTFILE.jcl`, `CARDFILE.jcl`, `CUSTFILE.jcl`, `XREFFILE.jcl`, `TRANFILE.jcl`, `TRANIDX.jcl`, `TCATBALF.jcl`, `TRANCATG.jcl`, `TRANTYPE.jcl`, `DISCGRP.jcl`, `DEFCUST.jcl`, `DEFGDGB.jcl`, `DEFGDGD.jcl`, `ESDSRRDS.jcl`, `OPENFIL.jcl`, `CLOSEFIL.jcl`, `CBADMCDJ.jcl`, `REPTFILE.jcl`, `PRTCATBL.jcl` |
| **Schedules** | `app/scheduler/CardDemo.controlm`, `app/scheduler/CardDemo.ca7` |
| **Entry points** | Batch jobs only (no CICS transaction). Primary jobs: `CREASTMT`, `CBEXPORT`, `CBIMPORT`, `READACCT`/`READCARD`/`READCUST`/`READXREF`, `WAITSTEP`. Callable leaf utilities: `CSUTLDTC` (CALLed online by COTRN02C / CORPT00C), `COBDATFT`, `MVSWAIT`, `CBSTM03B`. |
| **Out of scope** | CT00/CT01/CT02 online transaction screens — see the signed-off spec [`TransactionManagement_functional_requirement.md`](../../migration/transaction-management/docs/TransactionManagement_functional_requirement.md) and the stream inventory [`CardDemo-Stream-Map.md`](../../migration/transaction-management/docs/CardDemo-Stream-Map.md). Posting/interest/reporting batch programs (CBTRN02C, CBACT04C, CBTRN03C) are listed in the run-book only. |

**Purpose.** `CREASTMT` builds one account statement per card in the card cross-reference, in both an 80-column text file and a 100-column HTML file, by sorting the transaction master by card and calling `CBSTM03A`, which does its file I/O through the subroutine `CBSTM03B`. `CBEXPORT` and `CBIMPORT` move a branch's customers, accounts, cross-references, transactions and cards into one 500-byte multi-record-type file and back out again. The `READ*` jobs, the date/wait utilities and the file-definition JCL are the operational toolkit around these jobs, and the two scheduler definitions put them in order.

---

## 2. Functional requirements

### 2.1 Statements — CREASTMT / CBSTM03A / CBSTM03B / TXT2PDF1
| ID | Given input… the job… | Expected visible result / output | Source cite |
|----|----------------------|-----------------------------------|-------------|
| FR-ST1 | Given any prior state, CREASTMT step DELDEF01… | deletes `TRXFL.SEQ` and `TRXFL.VSAM.KSDS`, resets MAXCC to 0, redefines the work KSDS (KEYS(32 0), RECORDSIZE 350) | CREASTMT:22-40 |
| FR-ST2 | Given `TRANSACT.VSAM.KSDS`, step STEP010… | sorts on card number (pos 263, len 16) then tran id (pos 1, len 16) and reformats the record so card number comes first, writing `TRXFL.SEQ` (FB 350) | CREASTMT:44-55 |
| FR-ST3 | Given STEP010 RC=0, STEP020… | REPROs `TRXFL.SEQ` into `TRXFL.VSAM.KSDS` | CREASTMT:56-62 |
| FR-ST4 | Given STEP020 RC=0, STEP030 (IEFBR14)… | deletes any existing `STATEMNT.HTML` and `STATEMNT.PS` | CREASTMT:66-75 |
| FR-ST5 | Given STEP030 RC=0, STEP040… | runs CBSTM03A with TRNXFILE/XREFFILE/ACCTFILE/CUSTFILE and catalogs new `STATEMNT.PS` (FB 80) and `STATEMNT.HTML` (FB 100) | CREASTMT:79-96 |
| FR-ST6 | Given CBSTM03A starts… | logs the job and step names and every DD name in the TIOT, each marked valid or null UCB | CBSTM03A:266-291 |
| FR-ST7 | Given each XREF record (read sequentially by card)… | reads the customer (by XREF-CUST-ID) and the account (by XREF-ACCT-ID), then writes the text statement header: start banner, name, 3 address lines, "Basic Details" (Account ID, Current Balance, FICO Score), "TRANSACTION SUMMARY" and column headings | CBSTM03A:317-329, 345-414, 458-504 |
| FR-ST8 | Given that card has buffered transactions… | writes one detail line per transaction (Tran ID, description, `$` amount) to text and one `<tr>` row to HTML | CBSTM03A:416-432, 675-723 |
| FR-ST9 | After the card's transactions… | writes a dashed rule, `Total EXP:` with the summed amount, and the `END OF STATEMENT` banner; HTML gets an "End of Statement" row and closes `</table></body></html>` | CBSTM03A:433-454 |
| FR-ST10 | Given each XREF record… | writes a full HTML document: header with account number, fixed bank name/address block, customer name/address, Basic Details, Transaction Summary table header | CBSTM03A:461, 486, 506-672 |
| FR-ST11 | Given a card with no transactions in TRNXFILE… | still writes a statement, with no detail lines and a total of 0 (no match in the 4000 scan; WS-TOTAL-AMT reset to 0) | CBSTM03A:324-326, 416-437 |
| FR-ST12 | Given a customer or account key that is missing… | displays the read error and return code, then abends | CBSTM03A:379-386, 403-410, 921-923 |
| FR-ST13 | Given a CALL from CBSTM03A with DD/op/key… | CBSTM03B opens, reads, reads by key or closes the named file and returns its file status in RC (protocol §3.2) | CBSTM03B:114-229 |
| FR-ST14 | Given `STATEMNT.PS` exists, TXT2PDF1… | runs the TXT2PDF REXX under IKJEFT1B and writes `STATEMNT.PS.PDF` | TXT2PDF1.JCL:24-39 |

### 2.2 Export — CBEXPORT
| ID | Given input… the job… | Expected visible result / output | Source cite |
|----|----------------------|-----------------------------------|-------------|
| FR-EX1 | Given any prior state, CBEXPORT STEP01… | deletes (PURGE) and redefines `EXPORT.DATA` as a KSDS (RECORDSIZE 500, KEYS(4 28)) | CBEXPORT.jcl:24-39 |
| FR-EX2 | Given STEP02 starts… | logs the start message, opens 5 input KSDSs and the export file, and logs the export date and time | CBEXPORT:161-240 |
| FR-EX3 | Given CUSTDATA… | writes one type `C` record per customer, then logs the count | CBEXPORT:243-310 |
| FR-EX4 | Given ACCTDATA… | writes one type `A` record per account, then logs the count | CBEXPORT:312-373 |
| FR-EX5 | Given CARDXREF… | writes one type `X` record per cross-reference, then logs the count | CBEXPORT:376-428 |
| FR-EX6 | Given TRANSACT… | writes one type `T` record per transaction, then logs the count | CBEXPORT:431-493 |
| FR-EX7 | Given CARDDATA… | writes one type `D` record per card, then logs the count | CBEXPORT:496-551 |
| FR-EX8 | After all types… | closes all files and logs per-type and total export counts | CBEXPORT:554-573 |
| FR-EX9 | Given any open, read or write status other than OK (or EOF on reads)… | logs `ERROR: …, Status: nn` and abends | CBEXPORT:198-240, 258-266, 301-307, 576-579 |

### 2.3 Import — CBIMPORT
| ID | Given input… the job… | Expected visible result / output | Source cite |
|----|----------------------|-----------------------------------|-------------|
| FR-IM1 | Given `EXPORT.DATA`… | logs the start message and import date/time, opens 1 input and 6 outputs, reads every record sequentially and counts them | CBIMPORT:165-193, 196-245, 248-267 |
| FR-IM2 | Given a type `C` record… | writes a 500-byte CUSTOMER-RECORD to CUSTOUT | CBIMPORT:288-320 |
| FR-IM3 | Given a type `A` record… | writes a 300-byte ACCOUNT-RECORD to ACCTOUT | CBIMPORT:323-349 |
| FR-IM4 | Given a type `X` record… | writes a 50-byte CARD-XREF-RECORD to XREFOUT | CBIMPORT:352-369 |
| FR-IM5 | Given a type `T` record… | writes a 350-byte TRAN-RECORD to TRNXOUT | CBIMPORT:372-399 |
| FR-IM6 | Given a type `D` record… | writes a 150-byte CARD-RECORD to CARDOUT | CBIMPORT:402-422 |
| FR-IM7 | Given any other record type… | writes a 132-byte pipe-delimited error row to ERROUT ("Unknown record type encountered") and continues | CBIMPORT:283-284, 425-446 |
| FR-IM8 | At end of file… | logs the fixed validation messages, closes all files and logs read, per-type, error and unknown counts | CBIMPORT:449-478 |
| FR-IM9 | Given an open, read or data-file write failure… | logs `ERROR: …, Status: nn` and abends | CBIMPORT:196-245, 259-267, 314-318, 481-484 |

### 2.4 File-read utilities — READACCT / READCARD / READXREF / READCUST
| ID | Given input… the job… | Expected visible result / output | Source cite |
|----|----------------------|-----------------------------------|-------------|
| FR-RA1 | Given READACCT starts, PREDEL… | deletes `ACCTDATA.PSCOMP`, `.ARRYPS` and `.VBPS` | READACCT.jcl:22-28 |
| FR-RA2 | Given ACCTDATA KSDS, CBACT01C… | displays each account field-by-field with labels, then displays the whole record | CBACT01C:141-160, 165-213 |
| FR-RA3 | For each account… | writes a 107-byte OUTFILE record: reissue date reformatted to YYYYMMDD via COBDATFT; cycle debit defaulted to 2525.00 when zero | CBACT01C:215-251 |
| FR-RA4 | For each account… | writes a 110-byte ARRYFILE record with a 5-slot balance array (3 slots populated) | CBACT01C:253-274 |
| FR-RA5 | For each account… | writes two variable-length VBRCFILE records (12 and 39 bytes) and displays both | CBACT01C:276-315 |
| FR-RC1 | Given CARDDATA KSDS, READCARD/CBACT02C… | displays each card record once, between START/END banners | READCARD.jcl:22-26; CBACT02C:70-116 |
| FR-RX1 | Given CARDXREF KSDS, READXREF/CBACT03C… | displays each xref record **twice** (in GET-NEXT and in the main loop) | READXREF.jcl:22-26; CBACT03C:70-116 |
| FR-RU1 | Given CUSTDATA KSDS, READCUST/CBCUS01C… | displays each customer record **twice** (in GET-NEXT and in the main loop) | READCUST.jcl:21-25; CBCUS01C:70-116 |

### 2.5 Date / wait utilities
| ID | Given input… the program… | Expected visible result / output | Source cite |
|----|--------------------------|-----------------------------------|-------------|
| FR-UD1 | Given a 10-char date and a 10-char mask, CSUTLDTC… | calls LE `CEEDAYS`, returns an 80-char result message (severity, message no, result text, tested date, mask) and sets RETURN-CODE to the severity | CSUTLDTC:88-154 |
| FR-UD2 | Given CODATECN-REC (in type, in date, out type), COBDATFT… | converts YYYYMMDD↔YYYY-MM-DD into the out-date, or sets the error msg `INVALID INPUT` | COBDATFT.asm:17-62 |
| FR-UW1 | Given SYSIN `00003600`, WAITSTEP/COBSWAIT… | waits 3600 centiseconds (36 s) via MVSWAIT→STIMER, then ends | WAITSTEP.jcl:22-27; COBSWAIT:34-40; MVSWAIT.asm:17-27; ASMWAIT.mac:17-20 |

### 2.6 File-definition / environment jobs
| ID | Given input… the job… | Expected visible result / output | Source cite |
|----|----------------------|-----------------------------------|-------------|
| FR-JB1 | Given `ACCTDATA.PS`, ACCTFILE… | deletes and redefines the ACCTDATA KSDS (key 11@0, rec 300) and REPROs the flat file into it | ACCTFILE.jcl:22-61 |
| FR-JB2 | Given `CARDDATA.PS`, CARDFILE… | closes CICS CARDDAT/CARDAIX; deletes, redefines (key 16@0, rec 150) and loads the KSDS; defines AIX on acct id (KEYS 11 16) + PATH; BLDINDEX; reopens the CICS files | CARDFILE.jcl:22-123 |
| FR-JB3 | Given `CUSTDATA.PS`, CUSTFILE… | closes CICS CUSTDAT; deletes, redefines (key 9@0, rec 500) and loads; reopens CUSTDAT | CUSTFILE.jcl:22-80 |
| FR-JB4 | Given `CARDXREF.PS`, XREFFILE… | deletes, redefines (key 16@0, rec 50) and loads; defines AIX on acct id (KEYS 11,25) + PATH; BLDINDEX | XREFFILE.jcl:22-102 |
| FR-JB5 | Given `DALYTRAN.PS.INIT`, TRANFILE… | closes CICS TRANSACT/CXACAIX; deletes, redefines (key 16@0, rec 350) and loads; defines AIX on proc timestamp (KEYS 26 304) + PATH; BLDINDEX; reopens | TRANFILE.jcl:22-121 |
| FR-JB6 | Given TRANSACT KSDS, TRANIDX… | defines the TRANSACT AIX, PATH and BLDINDEX only | TRANIDX.jcl:22-54 |
| FR-JB7 | Given the `.PS` flat file, TCATBALF / TRANCATG / TRANTYPE / DISCGRP… | deletes, redefines and REPROs the KSDS: TCATBALF key 17@0 rec 50; TRANCATG key 6@0 rec 60; TRANTYPE key 2@0 rec 60; DISCGRP key 16@0 rec 50 | TCATBALF.jcl:22-61; TRANCATG.jcl:22-61; TRANTYPE.jcl:22-61; DISCGRP.jcl:22-61 |
| FR-JB8 | DEFCUST… | deletes `AWS.CCDA.CUSTDATA.CLUSTER`; defines `AWS.CUSTDATA.CLUSTER` (key 10@0, rec 500) | DEFCUST.jcl:22-46 |
| FR-JB9 | DEFGDGB… | defines GDG bases TRANSACT.BKUP, TRANSACT.DALY, TRANREPT, TCATBALF.BKUP, SYSTRAN, TRANSACT.COMBINED (LIMIT 5, SCRATCH), ignoring "already exists" (LASTCC 12) | DEFGDGB.jcl:21-59 |
| FR-JB10 | DEFGDGD… | defines GDGs TRANTYPE.BKUP, TRANCATG.PS.BKUP, DISCGRP.BKUP and seeds generation +1 of each from the `.PS` file | DEFGDGD.jcl:24-89 |
| FR-JB11 | ESDSRRDS… | writes 10 in-stream user-security rows to `ESDSRRDS.PS`; defines USRSEC ESDS and RRDS (rec 80) and REPROs the rows into both | ESDSRRDS.jcl:24-120 |
| FR-JB12 | OPENFIL / CLOSEFIL… | issues SDSF `/F CICSAWSA,'CEMT SET FIL(x) OPE|CLO'` for TRANSACT, CCXREF, ACCTDAT, CXACAIX, USRSEC | OPENFIL.jcl:22-30; CLOSEFIL.jcl:22-30 |
| FR-JB13 | CBADMCDJ… | runs DFHCSDUP to define CICS group CARDDEMO (LIBRARY, MAPSETs, PROGRAMs, TRANSACTIONs) and lists the group | CBADMCDJ.jcl:25-159 |
| FR-JB14 | REPTFILE… | defines GDG base TRANREPT LIMIT(10) | REPTFILE.jcl:22-28 |
| FR-JB15 | Given TCATBALF KSDS, PRTCATBL… | deletes the old report; backs up the KSDS to TCATBALF.BKUP(+1) via REPROC; sorts by acct/type/cat and writes the edited 40-byte report `TCATBALF.REPT` | PRTCATBL.jcl:19-63 |
| FR-JB16 | INTRDRJ1… | REPROs `FTP.TEST` to `.BKUP`, then submits member INTRDRJ2 via the internal reader; INTRDRJ2 REPROs `.BKUP` to `.BKUP.INTRDR` | INTRDRJ1.JCL:6-18; INTRDRJ2.JCL:7-13 |
| FR-JB17 | FTPJCL… | FTPs `AWS.M2.CARDEMO.FTP.TEST` in ASCII to a remote host as `/ftpfolder/welcome.txt` | FTPJCL.JCL:30-41 |

---

## 3. Interface spec (batch — no online screens in scope)

### 3.1 DD / file I/O by program
| Program (job/step) | DD | Dataset | Org / access | Rec len | I/O | Record layout |
|--------------------|----|---------|--------------|---------|-----|---------------|
| CBSTM03B (CREASTMT/STEP040) | TRNXFILE | `AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS` | KSDS seq, key 32 (card+tranid) | 350 | IN | COSTM01 `TRNX-RECORD` |
| CBSTM03B | XREFFILE | `CARDXREF.VSAM.KSDS` | KSDS seq, key 16 | 50 | IN | CVACT03Y |
| CBSTM03B | CUSTFILE | `CUSTDATA.VSAM.KSDS` | KSDS random, key 9 | 500 | IN | CUSTREC |
| CBSTM03B | ACCTFILE | `ACCTDATA.VSAM.KSDS` | KSDS random, key 11 | 300 | IN | CVACT01Y |
| CBSTM03A | STMTFILE | `STATEMNT.PS` | PS FB | 80 | OUT | ST-LINE0..15 |
| CBSTM03A | HTMLFILE | `STATEMNT.HTML` | PS FB | 100 | OUT | HTML-LINES |
| CBEXPORT (CBEXPORT/STEP02) | CUSTFILE / ACCTFILE / XREFFILE / TRANSACT / CARDFILE | `CUSTDATA` / `ACCTDATA` / `CARDXREF` / `TRANSACT` / `CARDDATA` `.VSAM.KSDS` | KSDS seq | 500/300/50/350/150 | IN | CVCUS01Y / CVACT01Y / CVACT03Y / CVTRA05Y / CVACT02Y |
| CBEXPORT | EXPFILE | `EXPORT.DATA` | KSDS seq (key EXPORT-SEQUENCE-NUM) | 500 | OUT | CVEXPORT |
| CBIMPORT (CBIMPORT/STEP01) | EXPFILE | `EXPORT.DATA` | KSDS seq | 500 | IN | CVEXPORT |
| CBIMPORT | CUSTOUT / ACCTOUT / XREFOUT / TRNXOUT | `CUSTDATA.IMPORT` / `ACCTDATA.IMPORT` / `CARDXREF.IMPORT` / `TRANSACT.IMPORT` | PS FB, NEW | 500/300/50/350 | OUT | CVCUS01Y / CVACT01Y / CVACT03Y / CVTRA05Y |
| CBIMPORT | CARDOUT | **no DD in CBIMPORT.jcl** | PS | 150 | OUT | CVACT02Y |
| CBIMPORT | ERROUT | `IMPORT.ERRORS` | PS FB, NEW | 132 | OUT | WS-ERROR-RECORD |
| CBACT01C (READACCT/STEP05) | ACCTFILE | `ACCTDATA.VSAM.KSDS` | KSDS seq | 300 | IN | CVACT01Y |
| CBACT01C | OUTFILE | `ACCTDATA.PSCOMP` | PS FB | 107 | OUT | OUT-ACCT-REC |
| CBACT01C | ARRYFILE | `ACCTDATA.ARRYPS` | PS FB | 110 | OUT | ARR-ARRAY-REC |
| CBACT01C | VBRCFILE | `ACCTDATA.VBPS` | PS VB (LRECL 84) | 10–80 | OUT | VBRC-REC1 / VBRC-REC2 |
| CBACT02C (READCARD) | CARDFILE | `CARDDATA.VSAM.KSDS` | KSDS seq | 150 | IN | CVACT02Y |
| CBACT03C (READXREF) | XREFFILE | `CARDXREF.VSAM.KSDS` | KSDS seq | 50 | IN | CVACT03Y |
| CBCUS01C (READCUST) | CUSTFILE | `CUSTDATA.VSAM.KSDS` | KSDS seq | 500 | IN | CVCUS01Y |
| COBSWAIT (WAITSTEP) | SYSIN | in-stream | — | 80 | IN | `PARM-VALUE` X(8) |
| TXT2PDF (TXT2PDF1) | INDD | `STATEMNT.PS` | PS | 80 | IN | text statement |

All dataset names are prefixed `AWS.M2.CARDDEMO.` unless shown in full. Cites: CREASTMT:79-96; CBSTM03A:39-47; CBSTM03B:31-78; CBEXPORT.jcl:43-68; CBEXPORT:35-92; CBIMPORT.jcl:22-65; CBIMPORT:37-109; READACCT.jcl:32-50; CBACT01C:29-85; WAITSTEP.jcl:22-27; TXT2PDF1.JCL:24-39.

### 3.2 CBSTM03B subroutine call protocol (`CALL 'CBSTM03B' USING WS-M03B-AREA`)
| Field | PIC | Direction | Meaning | Cite |
|-------|-----|-----------|---------|------|
| `WS-M03B-DD` | X(08) | in | Target DD: `TRNXFILE`, `XREFFILE`, `CUSTFILE`, `ACCTFILE`; anything else → immediate GOBACK, no RC set | CBSTM03A:72; CBSTM03B:118-128 |
| `WS-M03B-OPER` | X(01) | in | `O` open, `C` close, `R` sequential read, `K` keyed read, `W` write, `Z` rewrite (W/Z declared but not implemented) | CBSTM03A:73-79; CBSTM03B:102-108 |
| `WS-M03B-RC` | X(02) | out | VSAM file status of the DD after the operation | CBSTM03B:151-152, 175-176, 200-201, 225-226 |
| `WS-M03B-KEY` | X(25) | in | Key for `K`; only `(1:KEY-LN)` is used | CBSTM03B:189, 214 |
| `WS-M03B-KEY-LN` | S9(4) | in | Key length (9 for CUST, 11 for ACCT) | CBSTM03A:373-374, 397-398 |
| `WS-M03B-FLDT` | X(1000) | out | Record image (READ … INTO, space-padded) | CBSTM03B:141, 165, 190, 215 |

| DD | Supported ops | Open mode | Access | Cite |
|----|---------------|-----------|--------|------|
| TRNXFILE | O, R, C | INPUT | sequential, key FD-TRNXS-ID (32) | CBSTM03B:31-35, 133-155 |
| XREFFILE | O, R, C | INPUT | sequential, key card (16) | CBSTM03B:37-41, 157-179 |
| CUSTFILE | O, K, C | INPUT | random, key CUST-ID X(9) | CBSTM03B:43-47, 181-204 |
| ACCTFILE | O, K, C | INPUT | random, key ACCT-ID 9(11) | CBSTM03B:49-53, 206-229 |

Call sequence used by CBSTM03A: O TRNXFILE → R TRNXFILE (first record) → R TRNXFILE until RC `10` (buffering) → O XREFFILE → O CUSTFILE → O ACCTFILE → loop {R XREFFILE; K CUSTFILE; K ACCTFILE} until XREF RC `10` → C TRNXFILE, XREFFILE, CUSTFILE, ACCTFILE (CBSTM03A:296-341, 345-414, 726-919).

### 3.3 Text statement layout (STMTFILE, 80 cols) — per card, in write order
| # | Line | Content | Cite |
|---|------|---------|------|
| 1 | ST-LINE0 | 31×`*` + `START OF STATEMENT` + 31×`*` | CBSTM03A:86-89, 460 |
| 2 | ST-LINE1 | Name X(75) = first + ' ' + middle + ' ' + last | 90-92, 462-469, 488 |
| 3-4 | ST-LINE2/3 | Address line 1 / line 2, X(50) | 93-98, 470-471, 489-490 |
| 5 | ST-LINE4 | X(80) = addr line 3 + state + country + zip, space-separated | 99-100, 472-481, 491 |
| 6 | ST-LINE5 | 80×`-` | 101-102, 492 |
| 7 | ST-LINE6 | `Basic Details` at col 34 | 103-106, 493 |
| 8 | ST-LINE5 | 80×`-` | 494 |
| 9 | ST-LINE7 | `Account ID         :` + ACCT-ID X(20) | 107-110, 483, 495 |
| 10 | ST-LINE8 | `Current Balance    :` + `9(9).99-` | 111-115, 484, 496 |
| 11 | ST-LINE9 | `FICO Score         :` + X(20) | 116-119, 485, 497 |
| 12 | ST-LINE10 | 80×`-` | 120-121, 498 |
| 13 | ST-LINE11 | `TRANSACTION SUMMARY ` at col 31 | 122-125, 499 |
| 14 | ST-LINE12 | 80×`-` | 126-127, 500 |
| 15 | ST-LINE13 | `Tran ID         ` + `Tran Details    `(51) + `  Tran Amount` | 128-131, 501 |
| 16 | ST-LINE12 | 80×`-` | 502 |
| 17..n | ST-LINE14 | Tran ID X(16), ' ', description X(49), `$`, amount `Z(9).99-` | 132-137, 676-679 |
| n+1 | ST-LINE12 | 80×`-` | 435 |
| n+2 | ST-LINE14A | `Total EXP:` + 56 spaces + `$` + `Z(9).99-` | 138-142, 434, 436 |
| n+3 | ST-LINE15 | 32×`*` + `END OF STATEMENT` + 32×`*` | 143-146, 437 |

### 3.4 HTML statement layout (HTMLFILE, 100 cols) — per card
| Block | Lines written (88-level / field) | Content | Cite |
|-------|-------------------------------|---------|------|
| Doc head | L01–L08 | `<!DOCTYPE html>`, `<html lang="en">`, `<head>`, `<meta charset="utf-8">`, `<title>HTML Table Layout</title>`, `</head>`, `<body style="margin:0px;">`, `<table  align="center" frame="box" style="width:70%; font:12px Segoe UI,sans-serif;">` | CBSTM03A:150-158, 508-523 |
| Title row | LTRS, L10, HTML-L11, LTDE, LTRE | `<td colspan="3" … background-color:#1d1d96b3;">` + `<h3>Statement for Account Number: {ACCT-ID}</h3>` | 159-164, 212-216, 524-534 |
| Bank row | LTRS, L15, L16, L17, L18, LTDE, LTRE | bg `#FFAF33`; `<p style="font-size:16px">Bank of XYZ</p>`, `<p>410 Terry Ave N</p>`, `<p>Seattle WA 99999</p>` | 165-172, 535-548 |
| Customer row | LTRS, L22-35, name, addr×3, LTDE, LTRE | bg `#f2f2f2`; `<p style="font-size:16px">{name}</p>`, `<p>{addr1}</p>`, `<p>{addr2}</p>`, `<p>{addr3}</p>` | 173-175, 217-221, 549-597 |
| Basic Details heading | LTRS, L30-42, L31, LTDE, LTRE | bg `#33FFD1` centered; `<p style="font-size:16px">Basic Details</p>` | 176-180, 598-607 |
| Basic Details body | LTRS, L22-35, 3×BSIC, LTDE, LTRE | `<p>Account ID         : …</p>`, `<p>Current Balance    : …</p>`, `<p>FICO Score         : …</p>` | 608-637 |
| Summary heading | LTRS, L30-42, L43, LTDE, LTRE | `<p style="font-size:16px">Transaction Summary</p>` | 181-182, 638-647 |
| Column heads | LTRS, L47/L48, L50/L51, L53/L54, LTRE | green `#33FF5E` cells 25%/55%/20%: `Tran ID`, `Tran Details`, `Amount` | 183-197, 648-669 |
| Detail rows | LTRS, L58 `<p>id</p>`, L61 `<p>desc</p>`, L64 `<p>amt</p>`, LTRE | grey `#f2f2f2` cells 25%/55%/20% (amount right-aligned) | 198-206, 681-721 |
| Trailer | LTRS, L10, L75, LTDE, LTRE, L78, L79, L80 | `<h3>End of Statement</h3>`, `</table>`, `</body>`, `</html>` | 207-211, 439-454 |

### 3.5 Export multi-record-type layout (CVEXPORT, 500 bytes)
**Common header (bytes 1-40)**
| Field | PIC | Offset (0-based) / len | Value set by CBEXPORT | Cite |
|-------|-----|----------------------|-----------------------|------|
| EXPORT-REC-TYPE | X(1) | 0 / 1 | `C` `A` `X` `T` `D` | CVEXPORT:10; CBEXPORT:274, 343, 407, 462, 527 |
| EXPORT-TIMESTAMP | X(26) | 1 / 26 | `YYYY-MM-DD HH:MM:SS.00` (space-padded) | CVEXPORT:11-15; CBEXPORT:191-194 |
| EXPORT-SEQUENCE-NUM | 9(9) COMP | 27 / 4 | running counter starting at 1 | CVEXPORT:16; CBEXPORT:276-277 |
| EXPORT-BRANCH-ID | X(4) | 31 / 4 | `0001` | CVEXPORT:17; CBEXPORT:278 |
| EXPORT-REGION-CODE | X(5) | 35 / 5 | `NORTH` | CVEXPORT:18; CBEXPORT:279 |
| EXPORT-RECORD-DATA | X(460) | 40 / 460 | REDEFINED per type below | CVEXPORT:19 |

**Type-specific payload (REDEFINES EXPORT-RECORD-DATA)**
| Type | Fields (in order, PIC) | Encoding differences vs source copybook | Cite |
|------|-----------------------|----------------------------------------|------|
| `C` Customer | CUST-ID 9(9) **COMP**; first/middle/last X(25)×3; ADDR-LINE X(50) OCCURS 3; STATE X(2); COUNTRY X(3); ZIP X(10); PHONE X(15) OCCURS 2; SSN 9(9); GOVT-ID X(20); DOB X(10); EFT X(10); PRI-HOLDER X(1); FICO 9(3) **COMP-3**; FILLER X(134) | ID binary, FICO packed; address/phone become arrays | CVEXPORT:24-42; CBEXPORT:282-299 |
| `A` Account | ACCT-ID 9(11); STATUS X(1); CURR-BAL S9(10)V99 **COMP-3**; CREDIT-LIMIT S9(10)V99 (display); CASH-LIMIT S9(10)V99 **COMP-3**; OPEN/EXPIRY/REISSUE X(10)×3; CYC-CREDIT S9(10)V99 (display); CYC-DEBIT S9(10)V99 **COMP**; ZIP X(10); GROUP X(10); FILLER X(352) | mixed packed/binary/display | CVEXPORT:47-60; CBEXPORT:351-362 |
| `T` Transaction | TRAN-ID X(16); TYPE X(2); CAT 9(4); SOURCE X(10); DESC X(100); AMT S9(9)V99 **COMP-3**; MERCHANT-ID 9(9) **COMP**; MERCH NAME/CITY X(50)×2; ZIP X(10); CARD-NUM X(16); ORIG-TS X(26); PROC-TS X(26); FILLER X(140) | amount packed, merchant id binary | CVEXPORT:65-79; CBEXPORT:470-482 |
| `X` Card xref | CARD-NUM X(16); CUST-ID 9(9); ACCT-ID 9(11) **COMP**; FILLER X(427) | acct id binary | CVEXPORT:84-88; CBEXPORT:415-417 |
| `D` Card | CARD-NUM X(16); ACCT-ID 9(11) **COMP**; CVV 9(3) **COMP**; EMBOSSED-NAME X(50); EXPIRY X(10); STATUS X(1); FILLER X(373) | acct id and CVV binary | CVEXPORT:93-100; CBEXPORT:535-540 |

Record order in the file is always all `C`, then all `A`, `X`, `T`, `D`; each group is in its source file's key order (CBEXPORT:151-157).

### 3.6 CBIMPORT error record (ERROUT, 132)
`ERR-TIMESTAMP X(26) | ERR-RECORD-TYPE X(1) | ERR-SEQUENCE 9(7) | ERR-MESSAGE X(50)` + 43 spaces, `|` delimiters (CBIMPORT:152-160).

### 3.7 CBACT01C output layouts
| File | Layout | Cite |
|------|--------|------|
| OUTFILE (107) | ACCT-ID 9(11), STATUS X(1), CURR-BAL S9(10)V99, CREDIT-LIMIT S9(10)V99, CASH-LIMIT S9(10)V99, OPEN X(10), EXPIRY X(10), REISSUE X(10) (YYYYMMDD + 2 spaces), CYC-CREDIT S9(10)V99, CYC-DEBIT S9(10)V99 COMP-3, GROUP X(10) | CBACT01C:56-69 |
| ARRYFILE (110) | ACCT-ID 9(11), 5×{CURR-BAL S9(10)V99, CYC-DEBIT S9(10)V99 COMP-3}, FILLER X(4) | CBACT01C:71-78 |
| VBRCFILE rec 1 (12) | ACCT-ID 9(11), STATUS X(1) | CBACT01C:123-125, 287-290 |
| VBRCFILE rec 2 (39) | ACCT-ID 9(11), CURR-BAL S9(10)V99, CREDIT-LIMIT S9(10)V99, REISSUE-YYYY X(4) | CBACT01C:126-130, 302-305 |

### 3.8 Utility call interfaces
| Utility | Parameters | Returns | Cite |
|---------|-----------|---------|------|
| CSUTLDTC | `LS-DATE X(10)`, `LS-DATE-FORMAT X(10)`, `LS-RESULT X(80)` | LS-RESULT = WS-MESSAGE; RETURN-CODE = severity | CSUTLDTC:83-102 |
| COBDATFT | CODATECN-REC: TYPE X(1) (`1`=YYYYMMDD in, `2`=YYYY-MM-DD in), INP-DATE X(20), OUTTYPE X(1) (`1`=YYYY-MM-DD out, `2`=YYYYMMDD out), OUT-DATE X(20), ERROR-MSG X(38) | OUT-DATE or ERROR-MSG; R15=0 always | CODATECN.cpy:17-52; COCDATFT.mac; COBDATFT.asm:27-62 |
| MVSWAIT | fullword binary interval (1/100 s) | R15=0 | MVSWAIT.asm:19-27; ASMWAIT.mac:17-20 |

---

## 4. Business rules & calculations

| ID | Rule | Cite |
|----|------|------|
| BR-01 | Statements are produced **one per card-xref record**, in card-number order. An account with several cards gets several statements (JCL comment: "create statement for each CARD present in the XREF file"). | CBSTM03A:317-329, 345-366; CREASTMT:19-20 |
| BR-02 | For each xref, the customer is read by `XREF-CUST-ID` (key length 9) and the account by `XREF-ACCT-ID` (key length 11). Any RC other than `00` abends. | CBSTM03A:368-414 |
| BR-03 | The transaction work file is sorted ascending by card number then transaction id, and reformatted to `card(16) + TRAN-ID..MERCHANT-ZIP(262) + 50 bytes from pos 279`, keyed on the first 32 bytes. | CREASTMT:29-30, 53-54; COSTM01:20-36 |
| BR-04 | All transactions are loaded into a 2-D table before any statement is written: up to 51 cards × 10 transactions, each stored as card #, tran id and 318-byte rest. The per-card count goes to `WS-TRCT`. A card change bumps CR-CNT and resets TR-CNT to 1. | CBSTM03A:225-233, 756-759, 818-853 |
| BR-05 | Matching: for each xref card the table is scanned from slot 1 until `CR-JMP > CR-CNT` or the table card is greater than the xref card. On equality every buffered transaction for that card is printed. This relies on both files being in ascending card order. | CBSTM03A:416-432 |
| BR-06 | Statement total = Σ `TRNX-AMT` over the card's printed transactions (`WS-TOTAL-AMT` COMP-3 S9(9)V99), reset to 0 before each card. | CBSTM03A:65, 325, 429, 433-434 |
| BR-07 | Name line = FIRST, MIDDLE and LAST, each `DELIMITED BY ' '` (truncated at the first space), joined by single spaces. | CBSTM03A:462-469 |
| BR-08 | Address line 3 = ADDR-LINE-3, STATE-CD, COUNTRY-CD and ZIP, each `DELIMITED BY ' '`, joined by spaces. Lines 1 and 2 are copied verbatim (X(50)). | CBSTM03A:470-481 |
| BR-09 | Formats: balance `9(9).99-` (unsigned magnitude, trailing minus), transaction amount and total `Z(9).99-` after a literal `$`. The account id is moved to X(20) and the FICO score 9(3) to X(20). | CBSTM03A:109-118, 136-142, 483-485 |
| BR-10 | The "Tran Details" column holds `TRNX-DESC` (100 chars) truncated to 49 chars in text. HTML strips trailing space via `DELIMITED BY '*'`. | CBSTM03A:135, 677, 699-703 |
| BR-11 | In HTML the name, addresses and labels are cut at the first double space (`DELIMITED BY '  '`). The name source is `L23-NAME` X(50), which receives the 75-char `ST-NAME`. | CBSTM03A:217-220, 560-591 |
| BR-12 | The HTML bank identity is a fixed literal: "Bank of XYZ", "410 Terry Ave N", "Seattle WA 99999". | CBSTM03A:167-172 |
| BR-13 | File-open order is driven by `WS-FL-DD` with `ALTER … TO PROCEED TO` / `GO TO`: TRNXFILE (open + read all) → XREFFILE → CUSTFILE → ACCTFILE → main loop. | CBSTM03A:67, 296-314, 726-816 |
| BR-14 | Open/close and the first TRNXFILE read accept RC `00` or `04`. Subsequent sequential reads treat `00` as data, `10` as EOF, and anything else as an abend. Keyed reads accept only `00`. | CBSTM03A:736-754, 353-362, 379-386, 837-847 |
| BR-15 | CBSTM03B always returns the DD's 2-byte file status in `LK-M03B-RC` after O/R/K/C. For an op not handled for that DD it still returns the current status without doing any I/O. | CBSTM03B:133-229 |
| BR-16 | Export order is fixed: Customers → Accounts → Xrefs → Transactions → Cards. | CBEXPORT:149-158 |
| BR-17 | `EXPORT-SEQUENCE-NUM` is one counter across all record types: it starts at 0 and is incremented before each write, so the first record is 1. It is the KSDS key. | CBEXPORT:66-68, 123, 276-277, 345-346, 409-410, 464-465, 529-530 |
| BR-18 | One timestamp per run, `YYYY-MM-DD HH:MM:SS.00`, is stamped on every export record (hundredths hard-coded `.00`). | CBEXPORT:172-195, 275 |
| BR-19 | Branch `0001` and region `NORTH` are hard-coded on every export record. | CBEXPORT:278-279, 347-348, 411-412, 466-467, 531-532 |
| BR-20 | Export changes numeric encoding (binary/packed, §3.5) through COBOL MOVE; import reverses it with symmetric MOVEs into the source copybooks. | CVEXPORT:24-100; CBIMPORT:293-310, 328-339, 357-359, 377-389, 407-412 |
| BR-21 | Each record is routed by `EXPORT-REC-TYPE` (C/A/X/T/D) to its output file. An unknown type is counted, logged to ERROUT and skipped; it does not abend. | CBIMPORT:272-285, 425-434 |
| BR-22 | A failed error-file write is displayed but not fatal, and the error counter is still incremented. | CBIMPORT:439-446 |
| BR-23 | `3000-VALIDATE-IMPORT` performs no checks. It always displays success. | CBIMPORT:449-452 |
| BR-24 | CBACT01C converts the reissue date from `YYYY-MM-DD` to `YYYYMMDD` by calling COBDATFT with type `2` / outtype `2`. | CBACT01C:223-233 |
| BR-25 | CBACT01C writes `2525.00` to OUT cycle-debit only when `ACCT-CURR-CYC-DEBIT = 0`. There is no MOVE otherwise. | CBACT01C:236-238 |
| BR-26 | CBACT01C array record: slot 1 = (CURR-BAL, 1005.00), slot 2 = (CURR-BAL, 1525.00), slot 3 = (-1025.00, -2500.00). Slots 4-5 are left as INITIALIZEd. | CBACT01C:169, 253-261 |
| BR-27 | CBACT01C variable records: rec 1 length 12 (id + status), rec 2 length 39 (id, balance, credit limit, reissue year). | CBACT01C:276-315 |
| BR-28 | File-status display (`9910-DISPLAY-IO-STATUS` / `Z-DISPLAY-IO-STATUS`): if the status is non-numeric or stat1=`9`, print stat1 + binary of stat2 as 3 digits; else `00` + status. Output is `FILE STATUS IS: NNNN` + 4 chars. | CBACT01C:413-426; CBACT02C:161-174; CBACT03C:161-174; CBCUS01C:161-174 |
| BR-29 | READ* utilities map status `00`→APPL-RESULT 0, `10`→16 (EOF) and other→12 (error, then abend). Open/close non-`00` → abend with ABCODE 999, TIMING 0. | CBACT01C:165-198, 317-333, 388-410; CBACT02C:92-158; CBACT03C:92-158; CBCUS01C:92-158 |
| BR-30 | COBDATFT: type `1` requires the 5th input char ≠ `-` and outtype ≠ `2`, and emits `YYYY-MM-DD`. Type `2` requires outtype ≠ `1` and emits `YYYYMMDD` (its separator check is commented out). Any other type, or a disallowed combination, gives `INVALID INPUT`. | COBDATFT.asm:30-56 |
| BR-31 | CSUTLDTC: the date and mask are passed to CEEDAYS as 10-char varying strings. The 8-byte feedback token is mapped to a 15-char result: all-zero → `Date is valid`; named conditions → specific texts; anything else → `Date is invalid`. Severity goes to RETURN-CODE. | CSUTLDTC:59-80, 97-98, 103-149 |
| BR-32 | COBSWAIT reads 8 chars from SYSIN, moves them to a `9(8) COMP` and passes that to MVSWAIT, which issues `STIMER WAIT,BINTVL=` (hundredths of a second). WAITSTEP passes `00003600` = 36 s. | COBSWAIT:30-38; MVSWAIT.asm:20-23; ASMWAIT.mac:18-19; WAITSTEP.jcl:25-26 |
| BR-33 | CREASTMT steps STEP020–STEP040 run only if all prior steps ended RC 0 (`COND=(0,NE)`). DELDEF01 forces MAXCC=0 after its deletes. | CREASTMT:28, 56, 66, 79 |
| BR-34 | Every CBEXPORT run starts from an empty export KSDS (DELETE … PURGE, MAXCC 0, DEFINE). | CBEXPORT.jcl:27-38 |
| BR-35 | Batch that reloads a CICS-owned file runs between CLOSEFIL and OPENFIL, or the job closes and reopens the file itself. | CardDemo.controlm:4-24; CardDemo.ca7:18-43, 124; CARDFILE.jcl:22-27, 118-123; CUSTFILE.jcl:22-26, 76-80; TRANFILE.jcl:22-27, 116-121 |
| BR-36 | GDG bases keep 5 generations with SCRATCH (DEFGDGB/DEFGDGD), except REPTFILE's TRANREPT (LIMIT 10, no SCRATCH). | DEFGDGB.jcl:24-59; DEFGDGD.jcl:27-31, 50-54, 73-77; REPTFILE.jcl:25-28 |
| BR-37 | PRTCATBL report line: ACCT-ID(11) ␠ TYPE(2) ␠ CAT(4) ␠ BAL edited `TTTTTTTTT.TT` + 9 spaces = 40 bytes, sorted by acct/type/cat. | PRTCATBL.jcl:46-63 |

---

## 5. Data access

| File / dataset | Program / job | Operation | Key used | Record copybook | Cite |
|----------------|---------------|-----------|----------|-----------------|------|
| TRANSACT.VSAM.KSDS | CREASTMT STEP010 (SORT) | READ (all) | — | CVTRA05Y | CREASTMT:44-55 |
| TRXFL.SEQ / TRXFL.VSAM.KSDS | CREASTMT DELDEF01/STEP010/STEP020 | DELETE/DEFINE, WRITE, REPRO | card+tranid (32@0) | COSTM01 | CREASTMT:22-62 |
| TRXFL.VSAM.KSDS (TRNXFILE) | CBSTM03B for CBSTM03A | BROWSE (sequential READ) | FD-TRNXS-ID | COSTM01 | CBSTM03B:31-35, 140-144 |
| CARDXREF.VSAM.KSDS (XREFFILE) | CBSTM03B | BROWSE | XREF-CARD-NUM | CVACT03Y | CBSTM03B:37-41, 164-168 |
| CUSTDATA.VSAM.KSDS (CUSTFILE) | CBSTM03B | READ (random) | XREF-CUST-ID → FD-CUST-ID | CUSTREC | CBSTM03B:43-47, 188-193 |
| ACCTDATA.VSAM.KSDS (ACCTFILE) | CBSTM03B | READ (random) | XREF-ACCT-ID → FD-ACCT-ID | CVACT01Y | CBSTM03B:49-53, 213-218 |
| STATEMNT.PS / STATEMNT.HTML | CBSTM03A | WRITE | — | ST-LINE*/HTML-* | CBSTM03A:293, 339 |
| CUSTDATA/ACCTDATA/CARDXREF/TRANSACT/CARDDATA KSDS | CBEXPORT | BROWSE | primary key | CVCUS01Y/CVACT01Y/CVACT03Y/CVTRA05Y/CVACT02Y | CBEXPORT:35-63 |
| EXPORT.DATA | CBEXPORT | WRITE (sequential, ascending key) | EXPORT-SEQUENCE-NUM | CVEXPORT | CBEXPORT:65-69 |
| EXPORT.DATA | CBIMPORT | BROWSE | EXPORT-SEQUENCE-NUM | CVEXPORT | CBIMPORT:37-41, 259-267 |
| *.IMPORT, IMPORT.ERRORS | CBIMPORT | WRITE | — | CVCUS01Y/CVACT01Y/CVACT03Y/CVTRA05Y/CVACT02Y, WS-ERROR-RECORD | CBIMPORT:43-71 |
| ACCTDATA.VSAM.KSDS | CBACT01C | BROWSE | FD-ACCT-ID | CVACT01Y | CBACT01C:29-33 |
| ACCTDATA.PSCOMP / ARRYPS / VBPS | CBACT01C | WRITE | — | §3.7 | CBACT01C:35-48 |
| CARDDATA.VSAM.KSDS | CBACT02C | BROWSE | FD-CARD-NUM | CVACT02Y | CBACT02C:29-33 |
| CARDXREF.VSAM.KSDS | CBACT03C | BROWSE | FD-XREF-CARD-NUM | CVACT03Y | CBACT03C:29-33 |
| CUSTDATA.VSAM.KSDS | CBCUS01C | BROWSE | FD-CUST-ID | CVCUS01Y | CBCUS01C:29-33 |
| ACCTDATA/CARDDATA/CUSTDATA/CARDXREF/TRANSACT/TCATBALF/TRANCATG/TRANTYPE/DISCGRP KSDS | *FILE.jcl, TCATBALF/TRANCATG/TRANTYPE/DISCGRP.jcl | DELETE, DEFINE, REPRO load | 11@0 / 16@0 / 9@0 / 16@0 / 16@0 / 17@0 / 6@0 / 2@0 / 16@0 | (flat `.PS` source) | see FR-JB1..JB7 |
| CARDDATA AIX / CARDXREF AIX / TRANSACT AIX | CARDFILE / XREFFILE / TRANFILE, TRANIDX | DEFINE AIX + PATH, BLDINDEX | acct id 11@16 / acct id 11@25 / proc-ts 26@304 (non-unique) | — | CARDFILE.jcl:83-112; XREFFILE.jcl:72-102; TRANFILE.jcl:82-111; TRANIDX.jcl:25-54 |
| USRSEC.VSAM.ESDS / .RRDS | ESDSRRDS | DELETE, DEFINE, REPRO | — (ESDS/RRDS) | 80-byte user row | ESDSRRDS.jcl:59-120 |
| TCATBALF.VSAM.KSDS → TCATBALF.BKUP(+1) → TCATBALF.REPT | PRTCATBL | READ (REPRO), SORT, WRITE | — | SYMNAMES layout | PRTCATBL.jcl:29-63 |

---

## 6. Message catalogue (exact text)

### CBSTM03A
| Text | Condition | Cite |
|------|-----------|------|
| `'Running JCL : ' TIOTNJOB ' Step ' TIOTJSTP` | Always, at start | CBSTM03A:270 |
| `'DD Names from TIOT: '` | Always | CBSTM03A:275 |
| `': ' TIOCDDNM ' -- valid UCB'` / `': ' TIOCDDNM ' --  null UCB'` | Each TIOT entry in loop | CBSTM03A:279, 281 |
| `': ' TIOCDDNM ' -- valid UCB'` / `': ' TIOCDDNM ' -- null  UCB'` | Final entry after loop | CBSTM03A:288, 290 |
| `'ERROR OPENING TRNXFILE'` / `'ERROR OPENING XREFFILE'` / `'ERROR OPENING CUSTFILE'` / `'ERROR OPENING ACCTFILE'` | Open RC not 00/04 | CBSTM03A:739, 774, 792, 810 |
| `'ERROR READING TRNXFILE'` | First read RC not 00/04, or later read RC not 00/10 | CBSTM03A:751, 844 |
| `'ERROR READING XREFFILE'` | XREF read RC not 00/10 | CBSTM03A:359 |
| `'ERROR READING CUSTFILE'` / `'ERROR READING ACCTFILE'` | Keyed read RC not 00 | CBSTM03A:383, 407 |
| `'ERROR CLOSING TRNXFILE'` / `'ERROR CLOSING XREFFILE'` / `'ERROR CLOSING CUSTFILE'` / `'ERROR CLOSING ACCTFILE'` | Close RC not 00/04 | CBSTM03A:865, 882, 898, 914 |
| `'RETURN CODE: ' WS-M03B-RC` | After each error above | CBSTM03A:360, 384, 408, 740, 752, 775, 793, 811, 845, 866, 883, 899, 915 |
| `'ABENDING PROGRAM'` | Before CEE3ABD | CBSTM03A:922 |

### CBEXPORT
| Text | Condition | Cite |
|------|-----------|------|
| `'CBEXPORT: Starting Customer Data Export'` | Start | CBEXPORT:163 |
| `'CBEXPORT: Export Date: '` / `'CBEXPORT: Export Time: '` | After open | CBEXPORT:168-169 |
| `'ERROR: Cannot open CUSTOMER-INPUT, Status: '` (also `ACCOUNT-INPUT`, `XREF-INPUT`, `TRANSACTION-INPUT`, `CARD-INPUT`, `EXPORT-OUTPUT`) | Open status ≠ 00 | CBEXPORT:202, 209, 216, 223, 230, 237 |
| `'CBEXPORT: Processing customer records'` / `'…account records'` / `'…cross-reference records'` / `'…transaction records'` / `'…card records'` | Start of each phase | CBEXPORT:245, 314, 378, 433, 498 |
| `'CBEXPORT: Customers exported: '` / `'CBEXPORT: Accounts exported: '` / `'CBEXPORT: Cross-references exported: '` / `'CBEXPORT: Transactions exported: '` / `'CBEXPORT: Cards exported: '` | End of each phase | CBEXPORT:254, 323, 387, 442, 507 |
| `'ERROR: Reading CUSTOMER-INPUT, Status: '` (also `ACCOUNT-INPUT`, `XREF-INPUT`, `TRANSACTION-INPUT`, `CARD-INPUT`) | Read status not 00/10 | CBEXPORT:263, 332, 396, 451, 516 |
| `'ERROR: Writing export record, Status: '` | Write status ≠ 00 | CBEXPORT:304, 367, 422, 487, 545 |
| `'CBEXPORT: Export completed'`, `'CBEXPORT: Customers Exported: '`, `'CBEXPORT: Accounts Exported: '`, `'CBEXPORT: XRefs Exported: '`, `'CBEXPORT: Transactions Exported: '`, `'CBEXPORT: Cards Exported: '`, `'CBEXPORT: Total Records Exported: '` | Finalize | CBEXPORT:563-573 |
| `'CBEXPORT: ABENDING PROGRAM'` | Before CEE3ABD | CBEXPORT:578 |

### CBIMPORT
| Text | Condition | Cite |
|------|-----------|------|
| `'CBIMPORT: Starting Customer Data Import'` | Start | CBIMPORT:176 |
| `'CBIMPORT: Import Date: '` / `'CBIMPORT: Import Time: '` | After open | CBIMPORT:192-193 |
| `'ERROR: Cannot open EXPORT-INPUT, Status: '` (also `CUSTOMER-OUTPUT`, `ACCOUNT-OUTPUT`, `XREF-OUTPUT`, `TRANSACTION-OUTPUT`, `CARD-OUTPUT`, `ERROR-OUTPUT`) | Open status ≠ 00 | CBIMPORT:200, 207, 214, 221, 228, 235, 242 |
| `'ERROR: Reading EXPORT-INPUT, Status: '` | Read status not 00/10 | CBIMPORT:264 |
| `'ERROR: Writing customer record, Status: '` / `'…account record…'` / `'…xref record…'` / `'…transaction record…'` / `'…card record…'` | Write status ≠ 00 | CBIMPORT:315, 344, 364, 394, 417 |
| `'Unknown record type encountered'` (ERR-MESSAGE written to ERROUT) | Rec type not C/A/X/T/D | CBIMPORT:432 |
| `'ERROR: Writing error record, Status: '` | ERROUT write ≠ 00 (non-fatal) | CBIMPORT:442 |
| `'CBIMPORT: Import validation completed'`, `'CBIMPORT: No validation errors detected'` | Always | CBIMPORT:451-452 |
| `'CBIMPORT: Import completed'`, `'CBIMPORT: Total Records Read: '`, `'CBIMPORT: Customers Imported: '`, `'CBIMPORT: Accounts Imported: '`, `'CBIMPORT: XRefs Imported: '`, `'CBIMPORT: Transactions Imported: '`, `'CBIMPORT: Cards Imported: '`, `'CBIMPORT: Errors Written: '`, `'CBIMPORT: Unknown Record Types: '` | Finalize | CBIMPORT:465-478 |
| `'CBIMPORT: ABENDING PROGRAM'` | Before CEE3ABD | CBIMPORT:483 |

### CBACT01C / CBACT02C / CBACT03C / CBCUS01C
| Text | Condition | Cite |
|------|-----------|------|
| `'START OF EXECUTION OF PROGRAM CBACT01C'` / `'END OF EXECUTION OF PROGRAM CBACT01C'` (same pattern for CBACT02C, CBACT03C, CBCUS01C) | Start / end | CBACT01C:141, 158; CBACT02C:71, 85; CBACT03C:71, 85; CBCUS01C:71, 85 |
| `'ACCT-ID                 :'` … `'ACCT-GROUP-ID           :'` (11 labels) and `'-------------------------------------------------'` | Each account | CBACT01C:201-212 |
| `'VBRC-REC1:'` / `'VBRC-REC2:'` | Each account | CBACT01C:283-284 |
| `'ERROR READING ACCOUNT FILE'` | Read status not 00/10 | CBACT01C:192 |
| `'ACCOUNT FILE WRITE STATUS IS:'` | OUT/ARRY/VBRC write status not 00/10 | CBACT01C:246, 268, 294, 309 |
| `'ERROR OPENING ACCTFILE'`, `'ERROR OPENING OUTFILE'`, `'ERROR OPENING ARRAYFILE'`, `'ERROR OPENING VBRC FILE'` | Open ≠ 00 | CBACT01C:328, 345, 363, 381 |
| `'ERROR CLOSING ACCOUNT FILE'` | Close ≠ 00 | CBACT01C:399 |
| `'ERROR READING CARDFILE'` / `'ERROR OPENING CARDFILE'` / `'ERROR CLOSING CARDFILE'` | CBACT02C I/O errors | CBACT02C:110, 129, 147 |
| `'ERROR READING XREFFILE'` / `'ERROR OPENING XREFFILE'` / `'ERROR CLOSING XREFFILE'` | CBACT03C I/O errors | CBACT03C:110, 129, 147 |
| `'ERROR READING CUSTOMER FILE'` / `'ERROR OPENING CUSTFILE'` / `'ERROR CLOSING CUSTOMER FILE'` | CBCUS01C I/O errors | CBCUS01C:110, 129, 147 |
| `'FILE STATUS IS: NNNN'` + IO-STATUS-04 | After each I/O error | CBACT01C:420, 424; CBACT02C:168, 172; CBACT03C:168, 172; CBCUS01C:168, 172 |
| `'ABENDING PROGRAM'` | Before CEE3ABD (999) | CBACT01C:407; CBACT02C:155; CBACT03C:155; CBCUS01C:155 |

### Utilities
| Text | Condition | Cite |
|------|-----------|------|
| `'Date is valid'`, `'Insufficient'`, `'Datevalue error'`, `'Invalid Era    '`, `'Unsupp. Range  '`, `'Invalid month  '`, `'Bad Pic String '`, `'Nonnumeric data'`, `'YearInEra is 0 '`, `'Date is invalid'` | CEEDAYS feedback (returned in LS-RESULT, not displayed) | CSUTLDTC:128-149 |
| `'Mesg Code:'`, `'TstDate:'`, `'Mask used:'` | Literal labels in LS-RESULT | CSUTLDTC:45, 51, 54 |
| `INVALID INPUT` | COBDATFT bad type / combination (to CODATECN-ERROR-MSG) | COBDATFT.asm:56 |

---

## 7. Navigation / integration

| Direction | From → To | Mechanism | Data passed | Cite |
|-----------|-----------|-----------|-------------|------|
| Inbound | CREASTMT STEP040 → CBSTM03A | `EXEC PGM=` | DDs | CREASTMT:79 |
| Outbound | CBSTM03A → CBSTM03B | static `CALL 'CBSTM03B' USING WS-M03B-AREA` (13 call sites) | §3.2 | CBSTM03A:351, 377, 401, 734, 746, 769, 787, 805, 835, 860, 877, 893, 909 |
| Outbound | CBSTM03A, CBEXPORT, CBIMPORT, CBACT0xC, CBCUS01C → LE | `CALL 'CEE3ABD'` | none (CBSTM03A/CBEXPORT/CBIMPORT); `ABCODE`=999, `TIMING`=0 (READ* utilities) | CBSTM03A:923; CBEXPORT:579; CBIMPORT:484; CBACT01C:408-410; CBACT02C:156-158; CBACT03C:156-158; CBCUS01C:156-158 |
| Inbound | COTRN02C (CT02, out of scope) → CSUTLDTC | `CALL 'CSUTLDTC' USING CSUTLDTC-DATE, -DATE-FORMAT, -RESULT` | date, mask, 80-byte result | COTRN02C:393, 413 |
| Inbound | CORPT00C (CR00, Reporting stream) → CSUTLDTC | same | same | CORPT00C:392, 412 |
| Outbound | CSUTLDTC → LE `CEEDAYS` | CALL | varying date, varying mask, Lillian, feedback | CSUTLDTC:116-120 |
| Outbound | CBACT01C → COBDATFT (assembler) | `CALL 'COBDATFT' USING CODATECN-REC` | §3.8 | CBACT01C:231 |
| Outbound | COBSWAIT → MVSWAIT (assembler) → `STIMER` | CALL | `MVSWAIT-TIME` 9(8) COMP | COBSWAIT:38; MVSWAIT.asm:20-23 |
| Job submission | INTRDRJ1 → INTRDRJ2 | IEBGENER to `SYSOUT=(A,INTRDR)` from `AWS.M2.CARDDEMO.JCL(INTRDRJ2)` | JCL text | INTRDRJ1.JCL:14-18 |
| Job → CICS | OPENFIL / CLOSEFIL / CARDFILE / CUSTFILE / TRANFILE → region `CICSAWSA` | SDSF `/F … CEMT SET FIL(..) OPE/CLO` | file names | OPENFIL.jcl:22-30; CLOSEFIL.jcl:22-30 |
| Job → CICS CSD | CBADMCDJ → DFHCSDUP | batch CSD update | group CARDDEMO | CBADMCDJ.jcl:27-33 |
| Job → external | TXT2PDF1 → REXX `%TXT2PDF` (lib `AWS.M2.LBD.TXT2PDF.*`); FTPJCL → FTP server | TSO batch / FTP client | dataset | TXT2PDF1.JCL:24-39; FTPJCL.JCL:30-41 |

COMMAREA: not applicable. Everything in scope is batch or a CALLed subroutine. The only shared parameter blocks are `WS-M03B-AREA`, `CODATECN-REC` and the CSUTLDTC 3-parameter list.

### 7.1 End-to-end batch run-book (scheduler order)

**Control-M (`CardDemo.controlm`)** — condition-driven chains (`INCOND`/`OUTCOND`):
| Folder / cadence | # | Job | Steps → programs | Key datasets (in → out) | Cite |
|------------------|---|-----|------------------|-------------------------|------|
| DAILY-TransactionBackup (DAYS=ALL) | 1 | CLOSEFIL | CLCIFIL → SDSF (CEMT CLO) | CICS files TRANSACT, CCXREF, ACCTDAT, CXACAIX, USRSEC | controlm:4-7; CLOSEFIL.jcl:22-30 |
| | 2 | TRANBKP | STEP05R → PROC REPROC; STEP05 → IDCAMS delete; STEP10 → IDCAMS define | TRANSACT.VSAM.KSDS → TRANSACT.BKUP(+1); redefine TRANSACT KSDS | controlm:8-13; TRANBKP.jcl:23-66 |
| | 3 | WAITSTEP | WAIT → COBSWAIT → MVSWAIT | SYSIN 00003600 | controlm:14-19; WAITSTEP.jcl:22-27 |
| | 4 | OPENFIL | OPCIFIL → SDSF (CEMT OPE) | same CICS files | controlm:20-24; OPENFIL.jcl:22-30 |
| WEEKLY-TransactionTypesDBRefresh | 1 | MNTTRDB2 | (optional DB2 module, `app/app-transaction-type-db2/jcl/MNTTRDB2.jcl`) | — | controlm:26-31 |
| SMART DisclosureGroupsRefresh (DAYS=SA, after MNTTRDB2) | 1 | CLOSEFIL | as above | | controlm:32-37 |
| | 2 | DISCGRP | STEP05/10/15 → IDCAMS | DISCGRP.PS → DISCGRP.VSAM.KSDS | controlm:38-43; DISCGRP.jcl:22-61 |
| | 3 | WAITSTEP | COBSWAIT | | controlm:44-49 |
| | 4 | OPENFIL | SDSF | | controlm:50-54 |
| SMART TransactionTypesDBRefresh (DAYS=SA, after MNTTRDB2) | 1 | TRANEXTR | (optional DB2 module, `app/app-transaction-type-db2/jcl/TRANEXTR.jcl`) | — | controlm:57-63 |
| MONTHLY-InterestCalculation | 1 | CLOSEFIL | SDSF | | controlm:64-68 |
| | 2 | INTCALC | STEP15 → CBACT04C PARM='2022071800' | TCATBALF, CARDXREF(+AIX path), ACCTDATA, DISCGRP → SYSTRAN(+1) | controlm:69-74; INTCALC.jcl:22-41 |
| | 3 | COMBTRAN | STEP05R → SORT; STEP10 → IDCAMS REPRO | TRANSACT.BKUP(0) + SYSTRAN(0) → TRANSACT.COMBINED(+1) → TRANSACT.VSAM.KSDS | controlm:75-80; COMBTRAN.jcl:22-46 |
| | 4 | WAITSTEP | COBSWAIT | | controlm:81-86 |
| | 5 | OPENFIL | SDSF | | controlm:87-91 |

**CA-7 (`CardDemo.ca7`)** — trigger chains (job completion triggers the next):
| Chain (SCHID) | # | Job | Steps → programs | Key datasets (in → out) | Cite |
|---------------|---|-----|------------------|-------------------------|------|
| A (030) | 1 | CLOSEFIL | SDSF | | ca7:18-43 |
| | 2 | CBPAUP0J | (optional IMS module `app/app-authorization-ims-db2-mq/jcl/CBPAUP0J.jcl`) | — | ca7:43-70 |
| | 3 | POSTTRAN | STEP15 → CBTRN02C | DALYTRAN.PS, CARDXREF, ACCTDATA, TCATBALF, TRANSACT → TRANSACT, DALYREJS(+1) | ca7:70-97; POSTTRAN.jcl:23-42 |
| | 4 | WAITSTEP | COBSWAIT | | ca7:97-124 |
| | 5 | OPENFIL | SDSF | | ca7:124-141 |
| B (030→031/032→030) | 1 | CLOSEFIL | SDSF | | ca7:159-162 |
| | 2 | TRANTYPE | STEP05/10/15 → IDCAMS | TRANTYPE.PS → TRANTYPE.VSAM.KSDS | ca7:162-189; TRANTYPE.jcl:22-61 |
| | 3 | WAITSTEP | COBSWAIT; triggers CLOSEFIL1 (031) and CLOSEFIL2 (032) | | ca7:189-217 |
| | 4a | CLOSEFIL1 → TRANCATG → WAITSTEP (031) | **CLOSEFIL1 has no JCL member**; TRANCATG → IDCAMS | TRANCATG.PS → TRANCATG.VSAM.KSDS | ca7:219-303; TRANCATG.jcl:22-61 |
| | 4b | CLOSEFIL2 → TCATBALF → WAITSTEP (032) | **CLOSEFIL2 has no JCL member**; TCATBALF → IDCAMS | TCATBALF.PS → TCATBALF.VSAM.KSDS | ca7:246-335; TCATBALF.jcl:22-61 |
| | 5 | CLOSEFIL | SDSF | | ca7:303, 335-340 |
| | 6 | READACCT | PREDEL → IEFBR14; STEP05 → CBACT01C (→ COBDATFT) | ACCTDATA KSDS → ACCTDATA.PSCOMP/ARRYPS/VBPS | ca7:340-367; READACCT.jcl:22-50 |
| | 7 | READCARD | STEP05 → CBACT02C | CARDDATA KSDS → SYSOUT | ca7:367-394; READCARD.jcl:22-28 |
| | 8 | READCUST | STEP05 → CBCUS01C | CUSTDATA KSDS → SYSOUT | ca7:394-421; READCUST.jcl:21-27 |
| | 9 | READXREF | STEP05 → CBACT03C | CARDXREF KSDS → SYSOUT | ca7:421-448; READXREF.jcl:22-28 |
| | 10 | WAITSTEP | COBSWAIT | | ca7:448-453 |
| | 11 | OPENFIL | SDSF | | ca7:453-455 |
| C (030→031) | 1 | CLOSEFIL | SDSF | | ca7:465-468 |
| | 2 | CREASTMT | DELDEF01 → IDCAMS; STEP010 → SORT; STEP020 → IDCAMS REPRO; STEP030 → IEFBR14; STEP040 → CBSTM03A (+CBSTM03B) | TRANSACT KSDS → TRXFL.SEQ → TRXFL KSDS; TRXFL+CARDXREF+CUSTDATA+ACCTDATA → STATEMNT.PS, STATEMNT.HTML | ca7:468-495; CREASTMT:22-96 |
| | 3 | TXT2PDF1 | TXT2PDF → IKJEFT1B (%TXT2PDF) | STATEMNT.PS → STATEMNT.PS.PDF | ca7:495-522; TXT2PDF1.JCL:24-39 |
| | 4 | WAITSTEP | COBSWAIT | | ca7:522-527 |
| | 5 | OPENFIL | SDSF | | ca7:527-532 |
| | 6 | CLOSEFIL (031) | SDSF | | ca7:532-537 |
| | 7 | PRTCATBL | DELDEF → IEFBR14; STEP05R → PROC REPROC; STEP10R → SORT | TCATBALF KSDS → TCATBALF.BKUP(+1) → TCATBALF.REPT | ca7:537-564; PRTCATBL.jcl:21-63 |
| | 8 | WAITSTEP (031) | COBSWAIT | | ca7:564-569 |
| | 9 | OPENFIL (031) | SDSF | | ca7:569 |

**Jobs present in `app/jcl` but absent from both schedulers** (run on demand / one-time setup):
| Job | Steps → programs | Datasets | Cite |
|-----|------------------|----------|------|
| CBEXPORT | STEP01 → IDCAMS; STEP02 → CBEXPORT | 5 KSDS → EXPORT.DATA | CBEXPORT.jcl:24-68 |
| CBIMPORT | STEP01 → CBIMPORT | EXPORT.DATA → *.IMPORT, IMPORT.ERRORS | CBIMPORT.jcl:22-65 |
| ACCTFILE, CARDFILE, CUSTFILE, XREFFILE, TRANFILE, TRANIDX | IDCAMS (+SDSF for CARD/CUST/TRAN) | `.PS` → KSDS (+AIX) | FR-JB1..JB6 |
| DEFCUST, DEFGDGB, DEFGDGD, REPTFILE | IDCAMS (+IEBGENER in DEFGDGD) | cluster / GDG bases | FR-JB8..JB10, FR-JB14 |
| ESDSRRDS | IEFBR14, IEBGENER, IDCAMS ×4 | USRSEC ESDS/RRDS | ESDSRRDS.jcl:24-120 |
| CBADMCDJ | DFHCSDUP | CICS CSD | CBADMCDJ.jcl:27-159 |
| INTRDRJ1 / INTRDRJ2 | IDCAMS, IEBGENER→INTRDR / IDCAMS | `AWS.M2.CARDEMO.FTP.TEST*` | INTRDRJ1.JCL:6-18; INTRDRJ2.JCL:7-13 |
| FTPJCL | FTP | `AWS.M2.CARDEMO.FTP.TEST` → remote | FTPJCL.JCL:30-41 |
| TRANREPT, DALYREJS, DUSRSECJ (out of scope; listed for completeness) | REPROC+SORT+CBTRN03C / IDCAMS GDG / IEBGENER+IDCAMS | — | TRANREPT.jcl:23-80; DALYREJS.jcl:21-25; DUSRSECJ.jcl:23-83 |

Scheduler coverage differences: CREASTMT, TXT2PDF1, READ*, PRTCATBL, TRANTYPE, TRANCATG, TCATBALF and POSTTRAN appear **only in CA-7**. TRANBKP, DISCGRP, INTCALC, COMBTRAN, MNTTRDB2 and TRANEXTR appear **only in Control-M**.

---

## 8. Edge cases, error handling, abend / return codes

| Case | Behaviour | Cite |
|------|-----------|------|
| TRNXFILE is empty | The first READ after OPEN returns `10`, which is not in 00/04, so the job displays `ERROR READING TRNXFILE` and abends | CBSTM03A:744-754 |
| More than 51 distinct cards or more than 10 transactions per card in TRNXFILE | No bounds check: subscript overflow into adjacent storage (behaviour undefined) | CBSTM03A:225-233, 818-829 |
| Transaction whose card is not in XREF | Buffered but never printed | CBSTM03A:416-432 |
| XREF card has no customer or account | Abend after `ERROR READING CUSTFILE` / `ACCTFILE` | CBSTM03A:379-386, 403-410 |
| XREF EOF | `END-OF-FILE='Y'`. The last CARD-XREF-RECORD is still overwritten with FLDT (spaces) but not processed | CBSTM03A:356-357, 364 |
| CBSTM03B called with unknown DD | GOBACK without touching RC | CBSTM03B:127-131 |
| CBSTM03B op `W`/`Z`, or `K` on a sequential DD | No I/O; RC = existing file status | CBSTM03B:133-229 |
| CREASTMT step fails | Later steps are skipped (`COND=(0,NE)`); STEP010 has no COND | CREASTMT:44, 56, 66, 79 |
| CBEXPORT STEP01 IDCAMS fails | STEP02 has no COND and still runs | CBEXPORT.jcl:43 |
| CBIMPORT unknown record type | Error row written, processing continues, RC unaffected | CBIMPORT:425-446 |
| CBIMPORT rerun | Output DDs are `DISP=(NEW,CATLG,DELETE)` with no pre-delete step, so a rerun fails at allocation if the datasets exist | CBIMPORT.jcl:33-60 |
| CBIMPORT CARDOUT not allocated | `OPEN OUTPUT CARD-OUTPUT` status ≠ 00 → `ERROR: Cannot open CARD-OUTPUT, Status: ` → abend (inferred from JCL; not executed) | CBIMPORT:233-238; CBIMPORT.jcl:22-65 |
| Abend codes | CBSTM03A/CBEXPORT/CBIMPORT: `CALL 'CEE3ABD'` with **no arguments**. READ* utilities: `CEE3ABD USING ABCODE(999), TIMING(0)` → U999 | CBSTM03A:921-923; CBEXPORT:576-579; CBIMPORT:481-484; CBACT01C:406-410 |
| CSUTLDTC return code | RETURN-CODE = CEEDAYS severity (0 when valid). The caller must also inspect LS-RESULT | CSUTLDTC:97-98 |
| COBDATFT error | Only CODATECN-ERROR-MSG is set; R15 is always 0; the output date is left unchanged | COBDATFT.asm:55-62 |
| COBSWAIT non-numeric SYSIN | Alphanumeric→COMP MOVE with no validation; the wait length is undefined | COBSWAIT:36-37 |
| IDCAMS delete of a missing dataset | Tolerated via `IF MAXCC LE 08 THEN SET MAXCC = 0` / `SET MAXCC = 0` / `IF LASTCC=12 THEN SET MAXCC=0` | ACCTFILE.jcl:27; TCATBALF.jcl:27; DEFGDGB.jcl:29 |

---

## 9. Open questions, ambiguities and apparent legacy bugs (not fixed)

| # | Finding | Cite |
|---|---------|------|
| Q1 | **Table overflow**: the statement buffer is 51 cards × 10 transactions with no bounds check, which is not production-safe for real volumes. Should the target stream instead of buffering? | CBSTM03A:225-233, 818-829 |
| Q2 | **Truncated names/addresses**: `DELIMITED BY ' '` cuts multi-word values at the first space (e.g. city "NEW YORK" → "NEW", two-word first names). | CBSTM03A:462-481 |
| Q3 | **HTML name truncation**: HTML uses `DELIMITED BY '  '`. A blank middle name produces a double space after the first name, so the HTML shows the first name only. `L23-NAME` (50) also truncates the 75-char `ST-NAME`. | CBSTM03A:217-220, 560-567 |
| Q4 | **HTMLFILE is not one valid document**: a full `<!DOCTYPE html>…</html>` is emitted per card and all are concatenated into a single dataset. | CBSTM03A:439-454, 506-523 |
| Q5 | HTML statement **omits the `Total EXP:` line** that the text version prints. Intended? | CBSTM03A:433-454 |
| Q6 | An empty transaction file abends the statement job instead of producing zero-activity statements. | CBSTM03A:744-754 |
| Q7 | `ST-CURR-BAL PIC 9(9).99-` drops the high-order digit of `ACCT-CURR-BAL S9(10)V99`. The "Tran Details" column shows the description truncated to 49 chars, not a date (the field is named `ST-TRANDT`). | CBSTM03A:113, 135, 484, 677 |
| Q8 | The CREASTMT `OUTREC … 279:279,50` copies only 50 of the 52 timestamp bytes, so the last 2 bytes of PROC-TS are lost (unused by CBSTM03A). | CREASTMT:54; COSTM01:34-35 |
| Q9 | CBSTM03B declares `W` (write) and `Z` (rewrite) operations that are not implemented. Unsupported ops silently return a stale status. | CBSTM03B:107-108, 133-229 |
| Q10 | `CALL 'CEE3ABD'` without ABCODE/TIMING in CBSTM03A, CBEXPORT and CBIMPORT leaves the abend code undefined. Which code should the target use? | CBSTM03A:923; CBEXPORT:579; CBIMPORT:484 |
| Q11 | **Export KSDS key offset off-by-one**: `KEYS(4 28)` but `EXPORT-SEQUENCE-NUM` sits at offset 27 (after 1+26 bytes). The VSAM key is really seq bytes 2-4 plus the first branch byte. | CBEXPORT.jcl:32; CVEXPORT:10-17 |
| Q12 | CBEXPORT/CBIMPORT declare `RECORD KEY IS EXPORT-SEQUENCE-NUM`, a WORKING-STORAGE field (CVEXPORT), while the FD record is a flat `PIC X(500)`. This is non-standard and compiler-dependent. | CBEXPORT:65-69, 89-96; CBIMPORT:37-41, 76-79, 113 |
| Q13 | **CBIMPORT.jcl has no `CARDOUT` DD**, so the program opens CARD-OUTPUT and will abend at open. Card records cannot be imported as delivered. | CBIMPORT:63-66, 233-238; CBIMPORT.jcl:22-65 |
| Q14 | The CBIMPORT header promises "Validate data integrity using checksums", but `3000-VALIDATE-IMPORT` is a stub that always prints "No validation errors detected". No checksum exists in CVEXPORT. | CBIMPORT:31, 449-452 |
| Q15 | CBIMPORT error row: `ERR-SEQUENCE 9(7)` truncates the 9-digit sequence, and `ERR-TIMESTAMP` receives the 21-char `FUNCTION CURRENT-DATE` (not a formatted timestamp). | CBIMPORT:153-157, 429-431 |
| Q16 | CBIMPORT writes flat PS `*.IMPORT` files only. Nothing in scope loads them back into the VSAM masters. Is a REPRO step expected? | CBIMPORT.jcl:33-52 |
| Q17 | Branch `0001` / region `NORTH` are hard-coded. What is the intended parameterisation for multi-branch migration? | CBEXPORT:278-279 |
| Q18 | CBEXPORT and CBIMPORT are in neither scheduler. Is the migration export an on-demand run only? | CardDemo.controlm; CardDemo.ca7 |
| Q19 | **CBACT01C cycle-debit bug**: `OUT-ACCT-CURR-CYC-DEBIT` is set only when the source is zero (to 2525.00). A non-zero value is never moved, so the output carries the previous record's value or uninitialised storage. | CBACT01C:236-238 |
| Q20 | CBACT01C array/VB outputs use hard-coded demo values (1005.00, 1525.00, -1025.00, -2500.00) and fill 3 of 5 slots. They look like test fixtures, not business data. | CBACT01C:253-261 |
| Q21 | CBACT01C ignores `CODATECN-ERROR-MSG` returned by COBDATFT. | CBACT01C:231-233 |
| Q22 | CBACT03C and CBCUS01C DISPLAY every record twice; CBACT02C has the inner DISPLAY commented out. | CBACT03C:78, 96; CBCUS01C:78, 96; CBACT02C:78, 96 |
| Q23 | JCL typos: `NOTIFY=&SYUID` in READCUST; `UNIT=SYSAD` (not SYSDA) in READACCT/ESDSRRDS. | READCUST.jcl:2; READACCT.jcl:40, 44, 48; ESDSRRDS.jcl:50 |
| Q24 | DEFCUST deletes `AWS.CCDA.CUSTDATA.CLUSTER` but defines `AWS.CUSTDATA.CLUSTER` with `KEYS(10 0)` (CUST-ID is 9 bytes), uses two steps both named STEP05, and conflicts with CUSTFILE.jcl (`KEYS(9 0)`). Obsolete? | DEFCUST.jcl:22-46; CUSTFILE.jcl:46-51 |
| Q25 | GDG `TRANREPT` is defined twice with different attributes: REPTFILE LIMIT(10) no SCRATCH vs DEFGDGB LIMIT(5) SCRATCH. | REPTFILE.jcl:25-28; DEFGDGB.jcl:36-40 |
| Q26 | **Security**: FTPJCL hard-codes a host IP, user id and password in SYSIN. ESDSRRDS in-stream data holds plaintext user passwords. Must not be carried forward. | FTPJCL.JCL:32-35; ESDSRRDS.jcl:35-45 |
| Q27 | INTRDRJ1/2 and FTPJCL use HLQ `AWS.M2.CARDEMO` (missing "D"), unlike the rest of the estate (`AWS.M2.CARDDEMO`). Typo or intentional? | INTRDRJ1.JCL:8-9; INTRDRJ2.JCL:9-10; FTPJCL.JCL:40 |
| Q28 | CA-7 triggers `CLOSEFIL1` and `CLOSEFIL2`, which have no JCL members in the repo. CA-7 also triggers CBPAUP0J (optional IMS module) before POSTTRAN. | CardDemo.ca7:216-217, 43 |
| Q29 | The Control-M `MONTHLY-InterestCalculation` jobs carry no DAYS/MONTHDAYS rule, so the monthly cadence is not encoded. WAITSTEP and OPENFIL share `JOBISN="4"`. | CardDemo.controlm:64-91 |
| Q30 | The Control-M and CA-7 definitions disagree (daily TRANBKP vs POSTTRAN chain; statements, READ* and PRTCATBL exist only in CA-7). Which scheduler is authoritative for the target run-book? | CardDemo.controlm; CardDemo.ca7 |
| Q31 | CREASTMT STEP040 `STMTFILE` SPACE line has garbage in its comment field (`00,RECFM=FB), ATA.VSAM.KSDS`). STEP030 declares HTMLFILE with LRECL=80 while STEP040 creates it with LRECL=100. | CREASTMT:67-71, 90, 92-96 |
| Q32 | MVSWAIT writes `X'FF'` to `12(13)` in the caller's save area before return. The author's own comment questions its purpose. | MVSWAIT.asm:25 |
| Q33 | In CSUTLDTC the 88-level `FC-INVALID-DATE` (all-zero token) actually means the date is **valid** (mapped to 'Date is valid'). The name is misleading. | CSUTLDTC:62, 129-130 |
| Q34 | CBADMCDJ defines MAPSET COSGN00M twice and COACT00S/COACTVWS/COACTUPS/COACTDES twice (account vs card descriptions). It references programs not present in `app/cbl` (e.g. COACT00C, COADM00C, COTSTP1C-4C). TRANSACTION `CCDM` → COADM00C while the PROGRAM says TRANSID(CCAD). | CBADMCDJ.jcl:50-157 |
| Q35 | CBSTM03A uses `ALTER`/`GO TO` dispatch and reads MVS control blocks (PSA→TCB→TIOT) only to log DD names. Neither is portable; assume the target drops both. | CBSTM03A:30-31, 266-315 |
| Q36 | COBDATFT type-1 path rejects input only when the 5th char is `-`, and the type-2 separator check is commented out, so malformed dates are not detected. | COBDATFT.asm:36-37, 47-48 |

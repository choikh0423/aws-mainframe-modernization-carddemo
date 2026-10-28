# 06 — Batch: Daily Transaction Validation/Posting, Interest & Fees, Transaction Report

**Scope:** Batch programs that validate and post the daily transaction file, calculate monthly interest (fees stub), produce the daily transaction report, and the JCL that backs up / reloads the transaction master.
**SOURCE:** `choikh0423/aws-mainframe-modernization-carddemo` @ `7fd2344fe22ea20b31dc8ae0d662d9379ec7d284`
**Source files:** `app/cbl/CBTRN01C.cbl`, `CBTRN02C.cbl`, `CBTRN03C.cbl`, `CBACT04C.cbl`; `app/jcl/POSTTRAN.jcl`, `INTCALC.jcl`, `TRANREPT.jcl`, `COMBTRAN.jcl`, `TRANBKP.jcl`, `DALYREJS.jcl`; `app/proc/REPROC.prc`, `TRANREPT.prc`; `app/cpy/CVTRA01Y`–`CVTRA07Y`, `CVACT01Y`–`CVACT03Y`.
**Entry jobs:** `POSTTRAN` (CBTRN02C), `INTCALC` (CBACT04C), `TRANREPT` (REPROC → SORT → CBTRN03C; also submitted online as `TRNRPT00` via PROC `TRANREPT` from CORPT00C), `COMBTRAN`, `TRANBKP`, `DALYREJS` (one-time GDG define). `CBTRN01C` has no JCL in the repo.
**Purpose:** Turn the day's card transactions (DALYTRAN) into posted transactions: each is validated against the card cross-reference and account (credit limit, expiry); valid ones update the account balances and the per-account/type/category balance file (TCATBAL) and are written to the transaction master, invalid ones go to a rejects GDG with a reason code. Monthly, interest is computed per TCATBAL category from the disclosure-group rate and posted as system-generated transactions that are merged back into the master. A date-ranged, card-grouped transaction report with page/account/grand totals is produced on demand.

Related, out of scope: online CT00/CT01/CT02 (signed-off spec: [`TransactionManagement_functional_requirement.md`](../../migration/transaction-management/docs/TransactionManagement_functional_requirement.md)); online report request CR00/CORPT00C (Reporting stream, see [`CardDemo-Stream-Map.md`](../../migration/transaction-management/docs/CardDemo-Stream-Map.md) #7); statement generation (CREASTMT/CBSTM03A/B).

Cite format: `PROGRAM:start-end` = line numbers in the file at the commit above. JCL/PROC/copybook cites use the member name.

---

## 1. Functional requirements

### 1.1 CBTRN01C — Daily transaction verification (prototype, not scheduled)
| ID | Trigger | Expected visible result / output | Source cite |
|----|---------|----------------------------------|-------------|
| BP-FR-01 | Given a DALYTRAN record, the job reads it sequentially | Record is DISPLAYed to SYSOUT; loop ends on file status `10` | CBTRN01C:164-169, 202-225 |
| BP-FR-02 | Given a record whose card number is in XREFFILE | Displays `SUCCESSFUL READ OF XREF` + card/account/customer IDs, then reads ACCTFILE by `XREF-ACCT-ID`; on success displays `SUCCESSFUL READ OF ACCOUNT FILE` | CBTRN01C:170-176, 227-250 |
| BP-FR-03 | Given a record whose account is not on ACCTFILE | Displays `INVALID ACCOUNT NUMBER FOUND` then `ACCOUNT <acct-id> NOT FOUND` | CBTRN01C:177-179, 245-247 |
| BP-FR-04 | Given a record whose card is not on XREFFILE | Displays `INVALID CARD NUMBER FOR XREF` then `CARD NUMBER <card> COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-<id>` | CBTRN01C:180-184, 231-233 |
| BP-FR-05 | Given any run | No file is written or updated (CUSTFILE, CARDFILE, TRANFILE are opened INPUT and never read) | CBTRN01C:157-162, 252-359 |

### 1.2 CBTRN02C — Validate & post daily transactions (job POSTTRAN)
| ID | Trigger | Expected visible result / output | Source cite |
|----|---------|----------------------------------|-------------|
| BP-FR-10 | Given DALYTRAN records, the job processes each in file order | Each record counted in `WS-TRANSACTION-COUNT`, validated, then posted or rejected | CBTRN02C:202-219 |
| BP-FR-11 | Given a card number not on XREFFILE | Reject record written to DALYREJS with reason `0100` / `INVALID CARD NUMBER FOUND`; account checks skipped | CBTRN02C:370-392, 446-465 |
| BP-FR-12 | Given an xref account not on ACCTFILE | Reject with `0101` / `ACCOUNT RECORD NOT FOUND` | CBTRN02C:393-399 |
| BP-FR-13 | Given cycle credit − cycle debit + amount exceeding the credit limit | Reject with `0102` / `OVERLIMIT TRANSACTION` | CBTRN02C:403-413 |
| BP-FR-14 | Given transaction orig date (first 10 chars of `DALYTRAN-ORIG-TS`) later than account expiry date | Reject with `0103` / `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` (overwrites 0102 if both apply) | CBTRN02C:414-420 |
| BP-FR-15 | Given a record passing all validations | TCATBAL balance created/updated, account balances updated, record written to TRANSACT KSDS with `TRAN-PROC-TS` = current timestamp | CBTRN02C:424-444 |
| BP-FR-16 | Given no TCATBAL record for (account, type, category) | Displays `TCATBAL record not found for key : <key>.. Creating.` and writes a new TCATBAL record with balance = amount | CBTRN02C:467-479, 495-524 |
| BP-FR-17 | Given end of DALYTRAN | Displays `TRANSACTIONS PROCESSED :<n>` and `TRANSACTIONS REJECTED  :<n>`; RC=4 if any reject, else RC=0 | CBTRN02C:221-234 |
| BP-FR-18 | Given any OPEN/READ/WRITE/REWRITE/CLOSE I/O error (other than handled not-found) | Displays specific error + `FILE STATUS IS: NNNN<status>`, `ABENDING PROGRAM`, abends via CEE3ABD code 999 | CBTRN02C:236-343, 345-369, 446-542, 562-690, 707-727 |

### 1.3 CBACT04C — Interest calculation (job INTCALC)
| ID | Trigger | Expected visible result / output | Source cite |
|----|---------|----------------------------------|-------------|
| BP-FR-20 | Given TCATBAL read sequentially (key order) and PARM date, the job | Each record counted and DISPLAYed; on change of account ID reads ACCTFILE (by account) and XREFFILE (by AIX account ID) | CBACT04C:188-206, 325-348, 372-413 |
| BP-FR-21 | Given a TCATBAL record, the job looks up the disclosure-group rate by (ACCT-GROUP-ID, type, category) | Rate found → used; not found (status 23) → displays `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and re-reads with group `DEFAULT` | CBACT04C:210-213, 415-460 |
| BP-FR-22 | Given a non-zero rate | Monthly interest computed and a system interest transaction written to TRANSACT (SYSTRAN GDG) | CBACT04C:214-217, 462-515 |
| BP-FR-23 | Given a zero rate | No interest transaction for that category | CBACT04C:214 |
| BP-FR-24 | Given an account break (next account starts) | Previous account: `ACCT-CURR-BAL += total interest`, cycle credit/debit reset to 0, REWRITE | CBACT04C:194-199, 350-370 |
| BP-FR-25 | Given fee computation | No-op (`* To be implemented`) | CBACT04C:518-520 |
| BP-FR-26 | Given account or xref not found, default disclosure group missing, or any I/O error | Displays message + file status and abends (code 999) | CBACT04C:372-413, 443-460, 628-648 |

### 1.4 CBTRN03C — Daily transaction report (job TRANREPT / TRNRPT00)
| ID | Trigger | Expected visible result / output | Source cite |
|----|---------|----------------------------------|-------------|
| BP-FR-30 | Given a DATEPARM record `start end` | Displays `Reporting from <start> to <end>`; dates used for filter and header | CBTRN03C:168, 220-243 |
| BP-FR-31 | Given the (card-sorted) transaction file, the job for each in-range record | Resolves account via CARDXREF (on card change), type desc via TRANTYPE, category desc via TRANCATG, writes a detail line | CBTRN03C:170-196, 361-374 |
| BP-FR-32 | Given the first detail line | Writes header block: report name/date-range line, blank line, column header, dashes | CBTRN03C:274-280, 324-341 |
| BP-FR-33 | Given line counter is a multiple of 20 before a detail | Writes `Page Total` line + dashes, then header block again | CBTRN03C:282-285, 293-304 |
| BP-FR-34 | Given a change of card number (not first record) | Writes `Account Total` line + dashes, resets account total | CBTRN03C:181-184, 306-316 |
| BP-FR-35 | Given end of file | Writes final `Page Total` and `Grand Total` lines | CBTRN03C:197-204, 318-322 |
| BP-FR-36 | Given card/type/category not found in lookup files | Displays `INVALID CARD NUMBER : …` / `INVALID TRANSACTION TYPE : …` / `INVALID TRAN CATG KEY : …`, status 0023, abend 999 | CBTRN03C:484-512 |
| BP-FR-37 | Given a record whose proc date is outside [start,end] | `NEXT SENTENCE` — leaves the PERFORM loop (see Q-05) | CBTRN03C:173-178 |

### 1.5 JCL jobs
| ID | Trigger | Expected output | Source cite |
|----|---------|-----------------|-------------|
| BP-FR-40 | Given POSTTRAN submitted | STEP15 runs CBTRN02C; creates `DALYREJS(+1)` | POSTTRAN:23-42 |
| BP-FR-41 | Given INTCALC submitted with `PARM='2022071800'` | STEP15 runs CBACT04C; creates `SYSTRAN(+1)` (LRECL 350) | INTCALC:22-41 |
| BP-FR-42 | Given TRANREPT submitted | Unload KSDS → `TRANSACT.BKUP(+1)`; SORT filter 2022-01-01..2022-07-06 by card → `TRANSACT.DALY(+1)`; CBTRN03C → `TRANREPT(+1)` (LRECL 133) | TRANREPT.jcl:19-80 |
| BP-FR-43 | Given COMBTRAN submitted | SORT `TRANSACT.BKUP(0)` + `SYSTRAN(0)` by TRAN-ID → `TRANSACT.COMBINED(+1)`; IDCAMS REPRO into TRANSACT KSDS | COMBTRAN:22-49 |
| BP-FR-44 | Given TRANBKP submitted | REPRO KSDS → `TRANSACT.BKUP(+1)`; DELETE KSDS + AIX; DEFINE empty KSDS (KEYS(16 0), RECORDSIZE(350 350)) | TRANBKP:19-68 |
| BP-FR-45 | Given DALYREJS submitted (setup) | Defines GDG base `AWS.M2.CARDDEMO.DALYREJS` LIMIT(5) SCRATCH | DALYREJS:21-29 |

---

## 2. Interface spec (batch DD / file I/O)

### 2.1 Per program
| Program | DD | Org / access | Open mode | Record (len) | Copybook / FD | Cite |
|---------|----|--------------|-----------|--------------|---------------|------|
| CBTRN01C | DALYTRAN | PS seq | INPUT | 350 | CVTRA06Y | CBTRN01C:29-32, 66-69, 254 |
| CBTRN01C | CUSTFILE | KSDS random, key FD-CUST-ID 9(9) | INPUT (unused) | 500 | CVCUS01Y | CBTRN01C:34-38, 71-74, 273 |
| CBTRN01C | XREFFILE | KSDS random, key card 16 | INPUT | 50 | CVACT03Y | CBTRN01C:40-44, 291 |
| CBTRN01C | CARDFILE | KSDS random, key card 16 | INPUT (unused) | 150 | CVACT02Y | CBTRN01C:46-50, 309 |
| CBTRN01C | ACCTFILE | KSDS random, key 9(11) | INPUT | 300 | CVACT01Y | CBTRN01C:52-56, 327 |
| CBTRN01C | TRANFILE | KSDS random, key 16 | INPUT (unused) | 350 | CVTRA05Y | CBTRN01C:58-62, 345 |
| CBTRN02C | DALYTRAN | PS seq | INPUT | 350 | CVTRA06Y | CBTRN02C:29-32, 238 |
| CBTRN02C | TRANFILE | KSDS random, key TRAN-ID 16 | OUTPUT | 350 | CVTRA05Y | CBTRN02C:34-38, 256 |
| CBTRN02C | XREFFILE | KSDS random, key card 16 | INPUT | 50 | CVACT03Y | CBTRN02C:40-44, 275 |
| CBTRN02C | DALYREJS | PS seq | OUTPUT | 430 = 350 tran + 80 trailer | FD-REJS-RECORD | CBTRN02C:46-49, 81-84, 293 |
| CBTRN02C | ACCTFILE | KSDS random, key 9(11) | I-O | 300 | CVACT01Y | CBTRN02C:51-55, 311 |
| CBTRN02C | TCATBALF | KSDS random, key acct 9(11)+type X(2)+cat 9(4) | I-O | 50 | CVTRA01Y | CBTRN02C:57-61, 91-97, 329 |
| CBACT04C | TCATBALF | KSDS **sequential** | INPUT | 50 | CVTRA01Y | CBACT04C:28-32, 236 |
| CBACT04C | XREFFILE (+ XREFFIL1 AIX path) | KSDS random, alt key FD-XREF-ACCT-ID | INPUT | 50 | CVACT03Y | CBACT04C:34-39, 69-74, 254; INTCALC:29-32 |
| CBACT04C | ACCTFILE | KSDS random | I-O | 300 | CVACT01Y | CBACT04C:41-45, 291 |
| CBACT04C | DISCGRP | KSDS random, key group X(10)+type X(2)+cat 9(4) | INPUT | 50 | CVTRA02Y | CBACT04C:47-51, 76-82, 272 |
| CBACT04C | TRANSACT | PS seq | OUTPUT | 350 | CVTRA05Y | CBACT04C:53-56, 309 |
| CBTRN03C | TRANFILE | PS seq | INPUT | 350 | CVTRA05Y | CBTRN03C:29-31, 61-65, 378 |
| CBTRN03C | CARDXREF | KSDS random, key card 16 | INPUT | 50 | CVACT03Y | CBTRN03C:33-37, 414 |
| CBTRN03C | TRANTYPE | KSDS random, key type X(2) | INPUT | 60 | CVTRA03Y | CBTRN03C:39-43, 432 |
| CBTRN03C | TRANCATG | KSDS random, key type X(2)+cat 9(4) | INPUT | 60 | CVTRA04Y | CBTRN03C:45-49, 450 |
| CBTRN03C | DATEPARM | PS seq | INPUT | 80 (`start(10) sp end(10)`) | WS-DATEPARM-RECORD | CBTRN03C:55-57, 87-88, 122-125, 468 |
| CBTRN03C | TRANREPT | PS seq | OUTPUT | 133 | CVTRA07Y | CBTRN03C:51-53, 84-85, 396 |

### 2.2 Job step order and datasets
| Job | Step | Program | Inputs | Outputs | Cite |
|-----|------|---------|--------|---------|------|
| POSTTRAN | STEP15 | CBTRN02C | `TRANSACT.VSAM.KSDS`, `DALYTRAN.PS`, `CARDXREF.VSAM.KSDS`, `ACCTDATA.VSAM.KSDS`, `TCATBALF.VSAM.KSDS` | `DALYREJS(+1)` RECFM=F LRECL=430; updates TRANSACT/ACCTDATA/TCATBALF | POSTTRAN:23-42 |
| INTCALC | STEP15 (PARM='2022071800') | CBACT04C | `TCATBALF.VSAM.KSDS`, `CARDXREF.VSAM.KSDS`, `CARDXREF.VSAM.AIX.PATH` (XREFFIL1), `ACCTDATA.VSAM.KSDS`, `DISCGRP.VSAM.KSDS` | `SYSTRAN(+1)` RECFM=F LRECL=350; updates ACCTDATA | INTCALC:22-41 |
| TRANREPT | STEP05R | PROC REPROC (IDCAMS, SYSIN `CNTL(REPROCT)`) | `TRANSACT.VSAM.KSDS` | `TRANSACT.BKUP(+1)` FB 350 | TRANREPT.jcl:23-33; REPROC:21-29 |
| TRANREPT | STEP05R (dup name) | SORT, INCLUDE `TRAN-PROC-DT`(305,10) GE `2022-01-01` AND LE `2022-07-06`, SORT `TRAN-CARD-NUM`(263,16) A | `TRANSACT.BKUP(+1)` | `TRANSACT.DALY(+1)` | TRANREPT.jcl:37-55 |
| TRANREPT | STEP10R | CBTRN03C | `TRANSACT.DALY(+1)`, `CARDXREF`, `TRANTYPE`, `TRANCATG` KSDS, `DATEPARM` dataset | `TRANREPT(+1)` FB 133 | TRANREPT.jcl:59-80 |
| PROC TRANREPT | STEP01R / STEP05R / STEP10R | same three steps as above (used by CORPT00C's `TRNRPT00`, which overrides `STEP05R.SYMNAMES` and `STEP10R.DATEPARM`) | as above | as above | TRANREPT.prc:21-79; CORPT00C:81-125 |
| COMBTRAN | STEP05R | SORT by TRAN-ID(1,16) A | `TRANSACT.BKUP(0)` + `SYSTRAN(0)` | `TRANSACT.COMBINED(+1)` | COMBTRAN:22-37 |
| COMBTRAN | STEP10 | IDCAMS `REPRO INFILE(TRANSACT) OUTFILE(TRANVSAM)` | `TRANSACT.COMBINED(+1)` | `TRANSACT.VSAM.KSDS` | COMBTRAN:41-49 |
| TRANBKP | STEP05R | PROC REPROC | `TRANSACT.VSAM.KSDS` | `TRANSACT.BKUP(+1)` | TRANBKP:19-33 |
| TRANBKP | STEP05 | IDCAMS DELETE cluster + AIX; `IF MAXCC LE 08 THEN SET MAXCC = 0` | — | — | TRANBKP:37-46 |
| TRANBKP | STEP10 COND=(4,LT) | IDCAMS DEFINE CLUSTER KEYS(16 0) RECORDSIZE(350 350) INDEXED | — | empty `TRANSACT.VSAM.KSDS` | TRANBKP:51-68 |
| DALYREJS | STEP05 | IDCAMS DEFINE GDG LIMIT(5) SCRATCH | — | GDG base | DALYREJS:21-29 |

Scheduler context (outside source scope, for orientation): CA-7 chain `CLOSEFIL → CBPAUP0J → POSTTRAN → WAITSTEP → OPENFIL` (CardDemo.ca7:18-127); Control-M `DAILY-TransactionBackup`: `CLOSEFIL → TRANBKP → WAITSTEP → OPENFIL` (CardDemo.controlm:3-22); `MONTHLY-InterestCalculation`: `CLOSEFIL → INTCALC → COMBTRAN → WAITSTEP → OPENFIL` (CardDemo.controlm:64-89). TRANREPT is not scheduled; it is run standalone or submitted from CR00.

### 2.3 Record layouts used
| Copybook | Record (len) | Fields | Cite |
|----------|--------------|--------|------|
| CVTRA01Y | TRAN-CAT-BAL-RECORD (50) | key: TRANCAT-ACCT-ID 9(11), TRANCAT-TYPE-CD X(2), TRANCAT-CD 9(4); TRAN-CAT-BAL S9(9)V99; filler X(22) | CVTRA01Y:4-10 |
| CVTRA02Y | DIS-GROUP-RECORD (50) | key: DIS-ACCT-GROUP-ID X(10), DIS-TRAN-TYPE-CD X(2), DIS-TRAN-CAT-CD 9(4); DIS-INT-RATE S9(4)V99; filler X(28) | CVTRA02Y:4-10 |
| CVTRA03Y | TRAN-TYPE-RECORD (60) | TRAN-TYPE X(2), TRAN-TYPE-DESC X(50) | CVTRA03Y:4-7 |
| CVTRA04Y | TRAN-CAT-RECORD (60) | key TRAN-TYPE-CD X(2)+TRAN-CAT-CD 9(4); TRAN-CAT-TYPE-DESC X(50) | CVTRA04Y:4-9 |
| CVTRA05Y / CVTRA06Y | TRAN-RECORD / DALYTRAN-RECORD (350) | ID X(16), TYPE-CD X(2), CAT-CD 9(4), SOURCE X(10), DESC X(100), AMT S9(9)V99, MERCHANT-ID 9(9), MERCHANT-NAME X(50), CITY X(50), ZIP X(10), CARD-NUM X(16) (pos 263), ORIG-TS X(26) (pos 279), PROC-TS X(26) (pos 305), filler X(20) | CVTRA05Y:4-18; CVTRA06Y:4-18 |
| CVTRA07Y | report lines | see §3.6 | CVTRA07Y:4-66 |
| CVACT01Y | ACCOUNT-RECORD (300) | ACCT-ID 9(11), ACTIVE-STATUS X, CURR-BAL, CREDIT-LIMIT, CASH-CREDIT-LIMIT S9(10)V99, OPEN/EXPIRAION/REISSUE-DATE X(10), CURR-CYC-CREDIT, CURR-CYC-DEBIT S9(10)V99, ADDR-ZIP X(10), GROUP-ID X(10) | CVACT01Y:4-17 |
| CVACT02Y | CARD-RECORD (150) | CARD-NUM X(16), CARD-ACCT-ID 9(11), CVV, embossed name, expiry, status | CVACT02Y:4-11 |
| CVACT03Y | CARD-XREF-RECORD (50) | XREF-CARD-NUM X(16), XREF-CUST-ID 9(9), XREF-ACCT-ID 9(11) | CVACT03Y:4-8 |
| (CBTRN02C WS) | REJECT-RECORD (430) | REJECT-TRAN-DATA X(350) + trailer: WS-VALIDATION-FAIL-REASON 9(4), WS-VALIDATION-FAIL-REASON-DESC X(76) | CBTRN02C:176-182 |

---

## 3. Business rules & calculations

### 3.1 Validation (CBTRN02C)
| ID | Rule | Cite |
|----|------|------|
| BP-BR-01 | Validation order: XREF lookup by `DALYTRAN-CARD-NUM`; only if it passes (reason 0) is the account looked up by `XREF-ACCT-ID`. No other validations ("ADD MORE VALIDATIONS HERE"). | CBTRN02C:370-378 |
| BP-BR-02 | Reason codes: `100` card not in XREF; `101` account not found; `102` over limit; `103` received after account expiry. Reason is reset to 0/spaces per record. | CBTRN02C:208-209, 384-420 |
| BP-BR-03 | Over-limit test: `WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT − ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT`; pass iff `ACCT-CREDIT-LIMIT >= WS-TEMP-BAL`. | CBTRN02C:403-413 |
| BP-BR-04 | Expiry test: pass iff `ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS(1:10)` (alphanumeric compare of `YYYY-MM-DD`). | CBTRN02C:414-420 |
| BP-BR-05 | 102 and 103 are both evaluated; the last failing one (103) wins in the trailer. | CBTRN02C:407-420 |
| BP-BR-06 | Rejected record = original 350-byte DALYTRAN record + 80-byte trailer (4-digit reason + 76-char description); reject count incremented. | CBTRN02C:213-215, 446-451 |

### 3.2 Posting (CBTRN02C)
| ID | Rule | Cite |
|----|------|------|
| BP-BR-07 | Posted TRAN-RECORD copies every DALYTRAN field 1:1 (ID, type, category, source, desc, amount, merchant id/name/city/zip, card, orig TS); `TRAN-PROC-TS` = run timestamp. | CBTRN02C:425-438 |
| BP-BR-08 | Timestamp format: `YYYY-MM-DD-HH.MM.SS.hh0000` (26 chars) built from `FUNCTION CURRENT-DATE`; the 7th pair is hundredths (named MIL). | CBTRN02C:150-174, 692-705 |
| BP-BR-09 | Posting sequence per valid record: (1) TCATBAL update, (2) account update, (3) TRANSACT write. | CBTRN02C:440-442 |
| BP-BR-10 | Category-balance accumulation: key = (`XREF-ACCT-ID`, `DALYTRAN-TYPE-CD`, `DALYTRAN-CAT-CD`); if absent (status 23) create record initialised to zero then `+ DALYTRAN-AMT` and WRITE; else `TRAN-CAT-BAL += DALYTRAN-AMT` and REWRITE. Signed amount, no rounding. | CBTRN02C:467-542 |
| BP-BR-11 | Account update: `ACCT-CURR-BAL += amt`; if `amt >= 0` then `ACCT-CURR-CYC-CREDIT += amt` else `ACCT-CURR-CYC-DEBIT += amt` (debit accumulates a negative number). | CBTRN02C:545-553 |
| BP-BR-12 | Account REWRITE INVALID KEY sets reason 109 `ACCOUNT RECORD NOT FOUND` but neither rejects nor abends; the transaction is still written. | CBTRN02C:554-560 |
| BP-BR-13 | Control totals: records read, records rejected; `RETURN-CODE = 4` iff reject count > 0. | CBTRN02C:184-186, 206, 214, 227-231 |

### 3.3 Interest (CBACT04C)
| ID | Rule | Cite |
|----|------|------|
| BP-BR-14 | Processing is driven by TCATBAL in key order (account, type, category); account break on `TRANCAT-ACCT-ID NOT= WS-LAST-ACCT-NUM`. | CBACT04C:188-206 |
| BP-BR-15 | Disclosure-group key = (`ACCT-GROUP-ID` of the account, `TRANCAT-TYPE-CD`, `TRANCAT-CD`). | CBACT04C:210-213 |
| BP-BR-16 | DEFAULT fallback: if the read returns status `23`, group ID is replaced with `DEFAULT` (type/category kept) and re-read; any non-`00` status on the default read abends. Status other than `00`/`23` on the first read abends. | CBACT04C:415-460 |
| BP-BR-17 | Interest formula: `WS-MONTHLY-INT = (TRAN-CAT-BAL × DIS-INT-RATE) / 1200` — annual percentage rate ÷ 12 months ÷ 100; result S9(9)V99, truncated (no ROUNDED). Example: balance 1000.00 at 18.00 → 15.00. | CBACT04C:462-466 |
| BP-BR-18 | Interest is calculated only when `DIS-INT-RATE NOT = 0`; per-account `WS-TOTAL-INT` accumulates monthly interest across categories and is reset to 0 at each account break. | CBACT04C:200, 214-217, 467 |
| BP-BR-19 | Interest transaction: `TRAN-ID` = PARM-DATE (10 chars) ‖ 6-digit run sequence (starts 000001 each run); type `01`; category `05` (→ 0005); source `System`; desc `Int. for a/c ` ‖ ACCT-ID; amount = monthly interest; merchant id 0, name/city/zip spaces; card = XREF-CARD-NUM from AIX read; orig TS = proc TS = run timestamp. One transaction per TCATBAL record with non-zero rate. | CBACT04C:173, 473-499 |
| BP-BR-20 | Account update on break: `ACCT-CURR-BAL += WS-TOTAL-INT`; `ACCT-CURR-CYC-CREDIT = 0`; `ACCT-CURR-CYC-DEBIT = 0`; REWRITE (error abends). | CBACT04C:350-370 |
| BP-BR-21 | Fees: `1400-COMPUTE-FEES` is an empty stub; no fee is computed or posted. | CBACT04C:518-520 |
| BP-BR-22 | PARM is received via LINKAGE (`PARM-LENGTH` S9(4) COMP, `PARM-DATE` X(10)); not validated. | CBACT04C:175-180 |

### 3.4 Report (CBTRN03C)
| ID | Rule | Cite |
|----|------|------|
| BP-BR-23 | Date filter: include iff `TRAN-PROC-TS(1:10)` between `WS-START-DATE` and `WS-END-DATE` inclusive (string compare). | CBTRN03C:173-178 |
| BP-BR-24 | Grouping key is **card number** (`WS-CURR-CARD-NUM`), relying on SORT by TRAN-CARD-NUM; account ID on detail comes from CARDXREF, looked up only on card change. | CBTRN03C:181-188; TRANREPT.prc:35-46 |
| BP-BR-25 | Page size 20 lines (`WS-PAGE-SIZE`); page break tested before each detail as `MOD(WS-LINE-COUNTER, 20) = 0`; counter counts every written line except the grand total, and is never reset. | CBTRN03C:129-132, 282-285, 299-339, 373 |
| BP-BR-26 | Each detail adds `TRAN-AMT` to page and account totals; page total is added to grand total when the page-total line is written, then reset to 0; account total resets after its line. | CBTRN03C:287-288, 293-298, 306-310 |
| BP-BR-27 | Header block = REPORT-NAME-HEADER (with start/end date), blank line, TRANSACTION-HEADER-1, TRANSACTION-HEADER-2 (133 × `-`). Written once on first detail and after every page total. | CBTRN03C:274-280, 324-341 |
| BP-BR-28 | Detail type/category descriptions are truncated to 15 and 29 chars; amount edited `-ZZZ,ZZZ,ZZZ.ZZ`; totals edited `+ZZZ,ZZZ,ZZZ.ZZ`. | CVTRA07Y:15-31, 50-66 |
| BP-BR-29 | End of file: last record's `TRAN-AMT` is added again to page and account totals, then final page total and grand total are written; no final account total. | CBTRN03C:197-204 |

### 3.5 Job-level rules
| ID | Rule | Cite |
|----|------|------|
| BP-BR-30 | Rejects GDG keeps 5 generations (LIMIT(5) SCRATCH); each POSTTRAN run creates a new generation. | DALYREJS:24-28; POSTTRAN:34-38 |
| BP-BR-31 | Interest transactions do not enter the master directly: INTCALC writes `SYSTRAN(+1)`; COMBTRAN merges `TRANSACT.BKUP(0)` + `SYSTRAN(0)` sorted by TRAN-ID and REPROs into the KSDS. | INTCALC:37-41; COMBTRAN:22-49 |
| BP-BR-32 | TRANBKP backs up then deletes and redefines an empty TRANSACT KSDS (AIX deleted, not redefined); DEFINE is skipped if prior steps RC > 4. | TRANBKP:23-68 |
| BP-BR-33 | Report SORT positions: TRAN-CARD-NUM 263 len 16 (ZD), TRAN-PROC-DT 305 len 10 (CH), matching CVTRA05Y offsets. | TRANREPT.jcl:40-48; CVTRA05Y:5-17 |

### 3.6 Report line layout (133-byte records)
| Line | Content (in order) | Cite |
|------|--------------------|------|
| Name header | `DALYREPT` (X38) · `Daily Transaction Report` (X41) · `Date Range: ` (X12) · start X(10) · ` to ` · end X(10) | CVTRA07Y:4-13 |
| Header-1 | `Transaction ID` (17) · `Account ID` (12) · `Transaction Type` (19) · `Tran Category` (35) · `Tran Source` (14) · sp · `        Amount` (16) | CVTRA07Y:33-46 |
| Header-2 | 133 × `-` | CVTRA07Y:48 |
| Detail | TRAN-ID X16 · sp · ACCT X11 · sp · TYPE X2 · `-` · TYPE-DESC X15 · sp · CAT 9(4) · `-` · CAT-DESC X29 · sp · SOURCE X10 · 4 sp · AMT `-ZZZ,ZZZ,ZZZ.ZZ` · 2 sp | CVTRA07Y:15-31 |
| Page total | `Page Total` (X11) · 86 × `.` · `+ZZZ,ZZZ,ZZZ.ZZ` | CVTRA07Y:50-54 |
| Account total | `Account Total` (X13) · 84 × `.` · `+ZZZ,ZZZ,ZZZ.ZZ` | CVTRA07Y:56-60 |
| Grand total | `Grand Total` (X11) · 86 × `.` · `+ZZZ,ZZZ,ZZZ.ZZ` | CVTRA07Y:62-66 |

---

## 4. Data access
| Program | File | Operation | Key | Copybook | Cite |
|---------|------|-----------|-----|----------|------|
| CBTRN01C | DALYTRAN | READ seq | — | CVTRA06Y | CBTRN01C:203 |
| CBTRN01C | XREFFILE | READ | card num | CVACT03Y | CBTRN01C:228-239 |
| CBTRN01C | ACCTFILE | READ | acct id | CVACT01Y | CBTRN01C:242-250 |
| CBTRN02C | DALYTRAN | READ seq | — | CVTRA06Y | CBTRN02C:346 |
| CBTRN02C | XREFFILE | READ | DALYTRAN-CARD-NUM | CVACT03Y | CBTRN02C:382-391 |
| CBTRN02C | ACCTFILE | READ, REWRITE | XREF-ACCT-ID | CVACT01Y | CBTRN02C:394-421, 554-559 |
| CBTRN02C | TCATBALF | READ, WRITE (new), REWRITE | acct+type+cat | CVTRA01Y | CBTRN02C:469-542 |
| CBTRN02C | TRANFILE | WRITE | TRAN-ID | CVTRA05Y | CBTRN02C:562-579 |
| CBTRN02C | DALYREJS | WRITE seq | — | REJECT-RECORD | CBTRN02C:446-465 |
| CBACT04C | TCATBALF | READ seq (BROWSE) | — | CVTRA01Y | CBACT04C:325-348 |
| CBACT04C | ACCTFILE | READ, REWRITE | TRANCAT-ACCT-ID | CVACT01Y | CBACT04C:350-391 |
| CBACT04C | XREFFILE | READ via alternate key | FD-XREF-ACCT-ID | CVACT03Y | CBACT04C:393-413 |
| CBACT04C | DISCGRP | READ (×2 with DEFAULT) | group+type+cat | CVTRA02Y | CBACT04C:415-460 |
| CBACT04C | TRANSACT (SYSTRAN) | WRITE seq | — | CVTRA05Y | CBACT04C:500-514 |
| CBTRN03C | TRANFILE (DALY GDG) | READ seq | — | CVTRA05Y | CBTRN03C:248-272 |
| CBTRN03C | CARDXREF / TRANTYPE / TRANCATG | READ | card / type / type+cat | CVACT03Y / CVTRA03Y / CVTRA04Y | CBTRN03C:484-512 |
| CBTRN03C | DATEPARM | READ seq (1 record) | — | WS-DATEPARM-RECORD | CBTRN03C:220-243 |
| CBTRN03C | TRANREPT | WRITE seq | — | CVTRA07Y | CBTRN03C:343-359 |
| COMBTRAN | TRANSACT KSDS | REPRO (load) | TRAN-ID | — | COMBTRAN:41-49 |
| TRANBKP / TRANREPT | TRANSACT KSDS | REPRO (unload), DELETE/DEFINE (TRANBKP) | — | — | TRANBKP:23-68; TRANREPT.jcl:23-33 |

---

## 5. Message catalogue (SYSOUT DISPLAY text, verbatim)
All programs: `START OF EXECUTION OF PROGRAM <pgm>` / `END OF EXECUTION OF PROGRAM <pgm>`; on fatal error `ABENDING PROGRAM` and `FILE STATUS IS: NNNN` followed by the 4-digit status (CBTRN01C:156, 195, 469-489; CBTRN02C:194, 232, 707-727; CBTRN03C:160, 215, 626-646; CBACT04C:181, 230, 628-648).

| Program | Exact text | Condition | Cite |
|---------|------------|-----------|------|
| CBTRN01C | `'ACCOUNT ' ACCT-ID ' NOT FOUND'` | account read invalid key | CBTRN01C:178 |
| CBTRN01C | `'CARD NUMBER ' DALYTRAN-CARD-NUM ' COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-' DALYTRAN-ID` | xref invalid key | CBTRN01C:181-183 |
| CBTRN01C | `ERROR READING DAILY TRANSACTION FILE` | read status not 00/10 | CBTRN01C:219 |
| CBTRN01C | `INVALID CARD NUMBER FOR XREF` | xref invalid key | CBTRN01C:232 |
| CBTRN01C | `SUCCESSFUL READ OF XREF`, `'CARD NUMBER: '`, `'ACCOUNT ID : '`, `'CUSTOMER ID: '` | xref found | CBTRN01C:235-238 |
| CBTRN01C | `INVALID ACCOUNT NUMBER FOUND` / `SUCCESSFUL READ OF ACCOUNT FILE` | account read | CBTRN01C:246, 249 |
| CBTRN01C | `ERROR OPENING DAILY TRANSACTION FILE`, `ERROR OPENING CUSTOMER FILE`, `ERROR OPENING CROSS REF FILE`, `ERROR OPENING CARD FILE`, `ERROR OPENING ACCOUNT FILE`, `ERROR OPENING TRANSACTION FILE` | open ≠ 00 | CBTRN01C:263, 282, 300, 318, 336, 354 |
| CBTRN01C | `ERROR CLOSING CUSTOMER FILE` (×2: DALYTRAN and CUSTFILE close), `ERROR CLOSING CROSS REF FILE`, `ERROR CLOSING CARD FILE`, `ERROR CLOSING ACCOUNT FILE`, `ERROR CLOSING TRANSACTION FILE` | close ≠ 00 | CBTRN01C:372, 390, 408, 426, 444, 462 |
| CBTRN02C | `TRANSACTIONS PROCESSED :` + count / `TRANSACTIONS REJECTED  :` + count | end of run | CBTRN02C:227-228 |
| CBTRN02C | `ERROR OPENING DALYTRAN`, `ERROR OPENING TRANSACTION FILE`, `ERROR OPENING CROSS REF FILE`, `ERROR OPENING DALY REJECTS FILE`, `ERROR OPENING ACCOUNT MASTER FILE`, `ERROR OPENING TRANSACTION BALANCE FILE` | open ≠ 00 | CBTRN02C:247, 265, 284, 302, 320, 338 |
| CBTRN02C | `ERROR READING DALYTRAN FILE` | read status not 00/10 | CBTRN02C:363 |
| CBTRN02C | Reject descs: `INVALID CARD NUMBER FOUND`, `ACCOUNT RECORD NOT FOUND`, `OVERLIMIT TRANSACTION`, `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` (written to DALYREJS trailer, not displayed) | reasons 100–103 | CBTRN02C:386, 398, 411, 418 |
| CBTRN02C | `ERROR WRITING TO REJECTS FILE` | reject write ≠ 00 | CBTRN02C:460 |
| CBTRN02C | `'TCATBAL record not found for key : ' FD-TRAN-CAT-KEY '.. Creating.'` | TCATBAL status 23 | CBTRN02C:476-477 |
| CBTRN02C | `ERROR READING TRANSACTION BALANCE FILE`, `ERROR WRITING TRANSACTION BALANCE FILE`, `ERROR REWRITING TRANSACTION BALANCE FILE` | TCATBAL I/O error | CBTRN02C:489, 520, 538 |
| CBTRN02C | `ERROR WRITING TO TRANSACTION FILE` | TRANSACT write ≠ 00 (incl. duplicate key) | CBTRN02C:574 |
| CBTRN02C | `ERROR CLOSING DALYTRAN FILE`, `ERROR CLOSING TRANSACTION FILE`, `ERROR CLOSING CROSS REF FILE`, `ERROR CLOSING DAILY REJECTS FILE`, `ERROR CLOSING ACCOUNT FILE`, `ERROR CLOSING TRANSACTION BALANCE FILE` | close ≠ 00 | CBTRN02C:593, 611, 630, 648, 666, 685 |
| CBACT04C | `ERROR OPENING TRANSACTION CATEGORY BALANCE`, `'ERROR OPENING CROSS REF FILE' XREFFILE-STATUS`, `ERROR OPENING DALY REJECTS FILE` (on DISCGRP open), `ERROR OPENING ACCOUNT MASTER FILE`, `ERROR OPENING TRANSACTION FILE` | open ≠ 00 | CBACT04C:245, 263, 281, 300, 318 |
| CBACT04C | `ERROR READING TRANSACTION CATEGORY FILE` | TCATBAL read not 00/10 | CBACT04C:342 |
| CBACT04C | `ERROR RE-WRITING ACCOUNT FILE` | account rewrite ≠ 00 | CBACT04C:365 |
| CBACT04C | `'ACCOUNT NOT FOUND: ' FD-ACCT-ID` then `ERROR READING ACCOUNT FILE` | account invalid key | CBACT04C:375, 386 |
| CBACT04C | `'ACCOUNT NOT FOUND: ' FD-XREF-ACCT-ID` then `ERROR READING XREF FILE` | xref AIX invalid key | CBACT04C:397, 408 |
| CBACT04C | `DISCLOSURE GROUP RECORD MISSING` + `TRY WITH DEFAULT GROUP CODE` | DISCGRP status 23 | CBACT04C:418-419 |
| CBACT04C | `ERROR READING DISCLOSURE GROUP FILE` / `ERROR READING DEFAULT DISCLOSURE GROUP` | DISCGRP read error / default missing | CBACT04C:431, 455 |
| CBACT04C | `ERROR WRITING TRANSACTION RECORD` | SYSTRAN write ≠ 00 | CBACT04C:510 |
| CBACT04C | `ERROR CLOSING TRANSACTION BALANCE FILE`, `ERROR CLOSING CROSS REF FILE`, `ERROR CLOSING DISCLOSURE GROUP FILE`, `ERROR CLOSING ACCOUNT FILE`, `ERROR CLOSING TRANSACTION FILE` | close ≠ 00 | CBACT04C:533, 552, 570, 588, 606 |
| CBTRN03C | `'Reporting from ' WS-START-DATE ' to ' WS-END-DATE` | DATEPARM read OK | CBTRN03C:232-233 |
| CBTRN03C | `'TRAN-AMT ' TRAN-AMT` / `'WS-PAGE-TOTAL' WS-PAGE-TOTAL` | end of file branch | CBTRN03C:198-199 |
| CBTRN03C | `ERROR READING DATEPARM FILE` / `ERROR READING TRANSACTION FILE` | read status not 00/10 | CBTRN03C:238, 266 |
| CBTRN03C | `ERROR WRITING REPTFILE` | report write ≠ 00 | CBTRN03C:354 |
| CBTRN03C | `ERROR OPENING TRANFILE`, `ERROR OPENING REPTFILE`, `ERROR OPENING CROSS REF FILE`, `ERROR OPENING TRANSACTION TYPE FILE`, `ERROR OPENING TRANSACTION CATG FILE`, `ERROR OPENING DATE PARM FILE` | open ≠ 00 | CBTRN03C:387, 405, 423, 441, 459, 477 |
| CBTRN03C | `'INVALID CARD NUMBER : ' FD-XREF-CARD-NUM`, `'INVALID TRANSACTION TYPE : ' FD-TRAN-TYPE`, `'INVALID TRAN CATG KEY : ' FD-TRAN-CAT-KEY` | lookup invalid key (→ abend) | CBTRN03C:487, 497, 507 |
| CBTRN03C | `ERROR CLOSING POSTED TRANSACTION FILE`, `ERROR CLOSING REPORT FILE`, `ERROR CLOSING CROSS REF FILE`, `ERROR CLOSING TRANSACTION TYPE FILE`, `ERROR CLOSING TRANSACTION CATG FILE`, `ERROR CLOSING DATE PARM FILE` | close ≠ 00 | CBTRN03C:525, 543, 562, 580, 598, 616 |

Record dumps: CBTRN01C, CBTRN03C and CBACT04C also DISPLAY each input record (`DISPLAY DALYTRAN-RECORD` / `TRAN-RECORD` / `TRAN-CAT-BAL-RECORD`) (CBTRN01C:168; CBTRN03C:180; CBACT04C:193).

---

## 6. Navigation / integration
| Direction | Item | Detail | Cite |
|-----------|------|--------|------|
| Inbound | Online CR00 (CORPT00C) | Submits job `TRNRPT00` → `EXEC PROC=TRANREPT`, overriding `STEP05R.SYMNAMES` (PARM-START/END-DATE) and `STEP10R.DATEPARM` (instream `start end`) | CORPT00C:81-125 |
| Inbound | Scheduler | POSTTRAN via CA-7 after CBPAUP0J; INTCALC→COMBTRAN monthly and TRANBKP daily via Control-M | CardDemo.ca7:18-127; CardDemo.controlm:3-89 |
| Inbound | DALYTRAN.PS | Produced outside this scope (no producer in these sources) | POSTTRAN:30-31 |
| Outbound | `CEE3ABD` | CALLed with ABCODE 999, TIMING 0 on fatal error (all four programs) | CBTRN01C:469-473; CBTRN02C:707-711; CBTRN03C:626-630; CBACT04C:628-632 |
| Outbound | Datasets for downstream | TRANSACT KSDS (posted), ACCTDATA (balances), TCATBALF (category balances), DALYREJS(+1), SYSTRAN(+1), TRANSACT.BKUP/DALY/COMBINED, TRANREPT(+1) | §2.2 |
| Linkage | CBACT04C PARM | `EXTERNAL-PARMS`: PARM-LENGTH, PARM-DATE X(10) | CBACT04C:175-180 |
| COMMAREA | — | Batch only; no CICS COMMAREA used | — |
| No XCTL/LINK/job submission | — | None of the four programs CALLs another application program or submits jobs | — |

---

## 7. Edge cases, error handling, return codes
| Case | Behaviour | Cite |
|------|-----------|------|
| Empty DALYTRAN | CBTRN02C: counts 0/0, RC 0 | CBTRN02C:202-231 |
| Any reject | CBTRN02C RC = 4 (job continues) | CBTRN02C:229-231 |
| File status `10` on sequential read | Treated as EOF (APPL-RESULT 16) in all programs | CBTRN01C:207-217; CBTRN02C:351-361; CBTRN03C:254-264; CBACT04C:330-340 |
| Any other non-`00` I/O status | Message + `FILE STATUS IS: NNNN` + abend 999 (status ≥ `9x` or non-numeric shown as binary-decoded) | CBTRN02C:714-727 (same in all) |
| TCATBAL key missing at POSTTRAN | Created (status 23 accepted) | CBTRN02C:474-485 |
| Duplicate TRAN-ID in DALYTRAN vs KSDS | WRITE fails → abend after TCATBAL/account already updated | CBTRN02C:440-442, 562-578 |
| Disclosure group missing | DEFAULT fallback; DEFAULT missing → abend | CBACT04C:436-459 |
| Account / xref missing in INTCALC | Abend (status 23 ≠ 00) | CBACT04C:372-413 |
| Card/type/category missing in report | Abend with status 0023 | CBTRN03C:484-512 |
| Empty DATEPARM | END-OF-FILE set before main loop → no report lines | CBTRN03C:235-236 |
| TRANBKP DELETE of non-existent cluster/AIX | `MAXCC ≤ 8` reset to 0 | TRANBKP:40-45 |
| TRANBKP STEP10 | Skipped if any prior RC > 4 (`COND=(4,LT)`) | TRANBKP:51 |

---

## 8. Open questions / ambiguities / apparent legacy bugs (not fixed)
| ID | Finding | Cite |
|----|---------|------|
| Q-01 | **CBACT04C never updates the last account.** The `ELSE PERFORM 1050-UPDATE-ACCOUNT` belongs to `IF END-OF-FILE = 'N'` inside `PERFORM UNTIL END-OF-FILE = 'Y'`, so it can never execute; the final account's interest is written as a transaction but not added to `ACCT-CURR-BAL`, and its cycle totals are not reset. | CBACT04C:188-222 |
| Q-02 | CBACT04C does not reset or roll TCATBAL balances after charging interest; re-running INTCALC charges again on the same balances. Intended cycle-close behaviour is unknown. | CBACT04C:236, 462-468 |
| Q-03 | INTCALC TRAN-ID = PARM-DATE ‖ sequence restarting at 000001 each run; the PARM is hard-coded `2022071800`, so reruns/other months produce duplicate keys that COMBTRAN's REPRO into the KSDS would reject. PARM length is not validated. | CBACT04C:173, 474-480; INTCALC:22 |
| Q-04 | Interest is computed on any sign of `TRAN-CAT-BAL` (credit balances give negative interest) and truncated, not rounded. Confirm business intent. | CBACT04C:464-465 |
| Q-05 | CBTRN03C uses `NEXT SENTENCE` for out-of-range dates; the next period is the `END-PERFORM.`, so the first out-of-range record ends the loop (no totals written) rather than skipping that record. Normally hidden because SORT pre-filters, but standalone TRANREPT.jcl hard-codes SORT dates (2022-01-01..2022-07-06) independently of the DATEPARM dataset. | CBTRN03C:173-178, 206; TRANREPT.jcl:43-44, 73-74 |
| Q-06 | CBTRN03C at EOF adds the last record's `TRAN-AMT` a second time to page/account totals (READ…INTO leaves the last record in TRAN-RECORD), so final Page Total and Grand Total are overstated by that amount; on an empty file TRAN-AMT is uninitialised. | CBTRN03C:197-203, 248-249 |
| Q-07 | CBTRN03C never prints the Account Total for the last card group. | CBTRN03C:181-184, 197-204 |
| Q-08 | "Account Total" breaks on **card number**, not account; an account with several cards gets several "Account Total" lines. | CBTRN03C:181-187 |
| Q-09 | Page break uses `MOD(line-counter, 20) = 0` with a never-reset counter that also counts total/dash lines (+2), so a multiple of 20 can be skipped and a page runs long; no ASA/page-eject control is written. | CBTRN03C:282, 299-302, 311-314 |
| Q-10 | CBTRN02C opens TRANSACT KSDS `OUTPUT` with RANDOM access while the KSDS is populated; on z/OS this normally requires an empty (or REUSE) cluster. Expected open mode (I-O/EXTEND) and whether POSTTRAN must follow TRANBKP need confirmation. | CBTRN02C:34-38, 254-270 |
| Q-11 | CBTRN02C posting is not atomic: TCATBAL and ACCTDAT are updated before the TRANSACT WRITE; a write failure abends after the balance updates. | CBTRN02C:440-442 |
| Q-12 | CBTRN02C account REWRITE failure sets reason 109 but the transaction is still posted and not rejected or counted. | CBTRN02C:554-559 |
| Q-13 | Only the last of reasons 102/103 is recorded when both apply. | CBTRN02C:407-420 |
| Q-14 | Over-limit formula `CYC-CREDIT − CYC-DEBIT + amt`: since negative amounts accumulate into CYC-DEBIT (BR-11), subtracting it adds them back; the sign convention (purchases positive?) and use of cycle totals rather than `ACCT-CURR-BAL` should be confirmed. Cash credit limit and `ACCT-ACTIVE-STATUS` / card status are not checked. | CBTRN02C:403-413, 548-552 |
| Q-15 | Wrong messages/status in error paths: CBTRN01C DALYTRAN close shows `ERROR CLOSING CUSTOMER FILE` with CUSTFILE-STATUS; CBTRN02C DALYREJS close shows XREFFILE-STATUS; CBACT04C DISCGRP open shows `ERROR OPENING DALY REJECTS FILE`. | CBTRN01C:372-373; CBTRN02C:648-649; CBACT04C:281 |
| Q-16 | CBTRN01C looks like a prototype: no JCL runs it, it posts nothing, and after EOF it still performs one more XREF/account lookup with the last record (lookup is outside the inner `IF … = 'N'`). | CBTRN01C:164-186 |
| Q-17 | TRANREPT.jcl has two steps named `STEP05R`; TRANREPT.prc's PROC statement is named `REPROC` (same as REPROC.prc); the REPROC SYSIN member `AWS.M2.CARDDEMO.CNTL(REPROCT)` is not in the repo. | TRANREPT.jcl:23, 37; TRANREPT.prc:1; REPROC.prc:27-28 |
| Q-18 | COMBTRAN reads `TRANSACT.BKUP(0)`, which may be produced by either TRANBKP or TRANREPT (both create BKUP(+1)); its REPRO assumes TRANBKP emptied the KSDS first, but the monthly folder does not include TRANBKP. TRANBKP also deletes the TRANSACT AIX without redefining it. | COMBTRAN:23-26, 41-48; TRANBKP:43-44; TRANREPT.jcl:29-33 |
| Q-19 | CBACT04C picks the card for an interest transaction via the XREF alternate key (account); with several cards per account which card is returned is not defined in these sources. | CBACT04C:204-205, 393-395, 495 |
| Q-20 | Category code `'05'` moved to PIC 9(4) TRAN-CAT-CD and type `'01'` are hard-coded for interest; their TRANTYPE/TRANCATG descriptions are data-dependent (needed by CBTRN03C, which abends on a missing key). | CBACT04C:482-483; CBTRN03C:494-512 |

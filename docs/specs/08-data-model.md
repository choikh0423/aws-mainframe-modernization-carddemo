# 08 — Data Model: VSAM files, record layouts, keys, alternate indexes, reference data

## 1. Header

**Scope:** The CardDemo persistent data model. This covers every VSAM cluster, alternate index (AIX), PATH, GDG base and flat file that the base application defines, and the copybook record layouts for them. It also covers the CICS FILE resources that expose them, the jobs and utilities that define, load, back up, export and dump them, and the decoded reference data (transaction types, categories, disclosure groups).
**SOURCE:** `choikh0423/aws-mainframe-modernization-carddemo` @ `7fd2344fe22ea20b31dc8ae0d662d9379ec7d284`
- Copybooks: `app/cpy/CVACT01Y.cpy`, `CVACT02Y.cpy`, `CVACT03Y.cpy`, `CVCUS01Y.cpy`, `CUSTREC.cpy`, `CVTRA01Y.cpy`–`CVTRA07Y.cpy`, `CSUSR01Y.cpy`, `CVEXPORT.cpy`, `COSTM01.CPY`, `UNUSED1Y.cpy` (plus `COCOM01Y.cpy` for the user-type codes)
- File-definition JCL: `app/jcl/ACCTFILE.jcl`, `CARDFILE.jcl`, `CUSTFILE.jcl`, `XREFFILE.jcl`, `TRANFILE.jcl`, `TRANIDX.jcl`, `DISCGRP.jcl`, `TCATBALF.jcl`, `TRANCATG.jcl`, `TRANTYPE.jcl`, `DUSRSECJ.jcl`, `ESDSRRDS.jcl`, `DEFCUST.jcl`, `DEFGDGB.jcl`, `DEFGDGD.jcl`, `DALYREJS.jcl`, `REPTFILE.jcl`, `TRANBKP.jcl`, `COMBTRAN.jcl`, `PRTCATBL.jcl`, `OPENFIL.jcl`, `CLOSEFIL.jcl`, `READACCT.jcl`, `READCARD.jcl`, `READXREF.jcl`, `READCUST.jcl`, `CBEXPORT.jcl`, `CBIMPORT.jcl`, and the data-prep steps of `CREASTMT.JCL`
- Catalog: `app/catlg/LISTCAT.txt` (IDCAMS `LISTCAT LEVEL(AWS.M2.CARDDEMO) ALL`, dated 09/01/22, LISTCAT.txt:1-4)
- Sample data: `app/data/ASCII/*.txt`. CICS resources: `app/csd/CARDDEMO.CSD`
- Data utilities: `app/cbl/CBACT01C.cbl`, `CBACT02C.cbl`, `CBACT03C.cbl`, `CBCUS01C.cbl`, `CBEXPORT.cbl`, `CBIMPORT.cbl`. File-access cross-reference: all of `app/cbl/*`

**Entry jobs:** ACCTFILE, CARDFILE, CUSTFILE, XREFFILE, TRANFILE, TRANIDX, DISCGRP, TCATBALF, TRANCATG, TRANTYPE, DUSRSECJ, ESDSRRDS, DEFCUST, DEFGDGB, DEFGDGD, DALYREJS, REPTFILE, TRANBKP, COMBTRAN, PRTCATBL, OPENFIL, CLOSEFIL, READACCT, READCARD, READXREF, READCUST, CBEXPORT, CBIMPORT. **Online:** none of its own. The CSD FILE resources ACCTDAT, CARDDAT, CARDAIX, CCXREF, CXACAIX, CUSTDAT, TRANSACT and USRSEC are used by the online streams.

**Purpose:** This document is the reference oracle for rebuilding CardDemo's storage layer, for example as relational tables or JPA entities. It gives byte-exact record layouts, keys and access paths, the reference-code tables, and which program touches which file with which operation. The data logic of individual screens is in each stream's own spec. Transaction Management (CT00/CT01/CT02) is already signed off in [`TransactionManagement_functional_requirement.md`](../../migration/transaction-management/docs/TransactionManagement_functional_requirement.md) and is only linked here, not repeated. Stream inventory: [`CardDemo-Stream-Map.md`](../../migration/transaction-management/docs/CardDemo-Stream-Map.md).

**Conventions:** Offsets are **0-based** byte offsets within the record. IDCAMS `KEYS(len off)` uses the same 0-based offset. DFSORT positions in JCL are 1-based. Cites are `FILE:start-end` against the commit above. JCL and data files are cited with their extension; programs are cited without it.

---

## 2. Functional requirements

### 2.1 Define-and-load jobs (one per master/reference file)
| ID | Given input… the job… | Expected output | Source cite |
|----|-----------------------|-----------------|-------------|
| FR-DM-01 | Given flat file `AWS.M2.CARDDEMO.ACCTDATA.PS`, ACCTFILE deletes, defines and loads the account master | KSDS `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS`, KEYS(11 0), RECORDSIZE(300 300), loaded by REPRO | ACCTFILE.jcl:22-61 |
| FR-DM-02 | Given `CARDDATA.PS`, CARDFILE closes CICS files CARDDAT/CARDAIX, deletes the KSDS and AIX, defines and loads the card master, builds the AIX on account id, then reopens the CICS files | KSDS `CARDDATA.VSAM.KSDS` KEYS(16 0) RECORDSIZE(150 150); AIX `CARDDATA.VSAM.AIX` KEYS(11 16) NONUNIQUEKEY UPGRADE; PATH `CARDDATA.VSAM.AIX.PATH` | CARDFILE.jcl:22-123 |
| FR-DM-03 | Given `CUSTDATA.PS`, CUSTFILE closes CUSTDAT, then defines and loads the customer master and reopens CUSTDAT | KSDS `CUSTDATA.VSAM.KSDS` KEYS(9 0) RECORDSIZE(500 500) | CUSTFILE.jcl:22-80 |
| FR-DM-04 | Given `CARDXREF.PS`, XREFFILE defines and loads the card cross-reference and builds the AIX on account id | KSDS `CARDXREF.VSAM.KSDS` KEYS(16 0) RECORDSIZE(50 50); AIX `CARDXREF.VSAM.AIX` KEYS(11,25) NONUNIQUEKEY UPGRADE; PATH `CARDXREF.VSAM.AIX.PATH` | XREFFILE.jcl:22-102 |
| FR-DM-05 | Given `DALYTRAN.PS.INIT`, TRANFILE closes TRANSACT/CXACAIX, then defines and loads the transaction master, builds the AIX on processed timestamp and reopens the files | KSDS `TRANSACT.VSAM.KSDS` KEYS(16 0) RECORDSIZE(350 350); AIX `TRANSACT.VSAM.AIX` KEYS(26 304) NONUNIQUEKEY UPGRADE; PATH `TRANSACT.VSAM.AIX.PATH` | TRANFILE.jcl:22-121 |
| FR-DM-06 | Given an existing TRANSACT KSDS, TRANIDX (re)defines only the processed-timestamp AIX and PATH and rebuilds it | AIX/PATH as in FR-DM-05 | TRANIDX.jcl:22-54 |
| FR-DM-07 | Given `DISCGRP.PS`, DISCGRP defines and loads the disclosure-group file | KSDS `DISCGRP.VSAM.KSDS` KEYS(16 0) RECORDSIZE(50 50) | DISCGRP.jcl:22-61 |
| FR-DM-08 | Given `TCATBALF.PS`, TCATBALF defines and loads the category-balance file | KSDS `TCATBALF.VSAM.KSDS` KEYS(17 0) RECORDSIZE(50 50) | TCATBALF.jcl:22-61 |
| FR-DM-09 | Given `TRANCATG.PS`, TRANCATG defines and loads the transaction-category reference file | KSDS `TRANCATG.VSAM.KSDS` KEYS(6 0) RECORDSIZE(60 60) | TRANCATG.jcl:22-61 |
| FR-DM-10 | Given `TRANTYPE.PS`, TRANTYPE defines and loads the transaction-type reference file | KSDS `TRANTYPE.VSAM.KSDS` KEYS(2 0) RECORDSIZE(60 60) SHAREOPTIONS(1 4) | TRANTYPE.jcl:22-61 |
| FR-DM-11 | Given 10 in-stream user rows, DUSRSECJ builds `USRSEC.PS` (LRECL 80), then defines and loads the user-security file | KSDS `USRSEC.VSAM.KSDS` KEYS(8,0) RECORDSIZE(80,80) REUSE; 5 admin + 5 regular users (§3.4.5) | DUSRSECJ.jcl:23-87 |
| FR-DM-12 | Given the same 10 in-stream users, ESDSRRDS builds ESDS and RRDS variants of the security file | ESDS `USRSEC.VSAM.ESDS` (NONINDEXED) and RRDS `USRSEC.VSAM.RRDS` (NUMBERED), both RECORDSIZE(80,80) REUSE | ESDSRRDS.jcl:24-121 |
| FR-DM-13 | DEFGDGB defines the GDG bases for transaction and balance history | GDGs `TRANSACT.BKUP`, `TRANSACT.DALY`, `TRANREPT`, `TCATBALF.BKUP`, `SYSTRAN`, `TRANSACT.COMBINED`, each LIMIT(5) SCRATCH; LASTCC=12 (already exists) is reset to 0 | DEFGDGB.jcl:21-60 |
| FR-DM-14 | Given the reference PS files, DEFGDGD defines reference-data backup GDGs and writes generation +1 of each | `TRANTYPE.BKUP` (LRECL 60), `TRANCATG.PS.BKUP` (LRECL 60), `DISCGRP.BKUP` (LRECL 50), each LIMIT(5) SCRATCH | DEFGDGD.jcl:24-89 |
| FR-DM-15 | DALYREJS defines the daily-reject GDG base | GDG `DALYREJS` LIMIT(5) SCRATCH | DALYREJS.jcl:21-28 |
| FR-DM-16 | REPTFILE defines the transaction-report GDG base | GDG `TRANREPT` LIMIT(10), no SCRATCH (conflicts with FR-DM-13, see Q-08) | REPTFILE.jcl:22-28 |
| FR-DM-17 | DEFCUST defines an alternative customer cluster | Deletes `AWS.CCDA.CUSTDATA.CLUSTER`, defines `AWS.CUSTDATA.CLUSTER` KEYS(10 0) RECORDSIZE(500 500) SHAREOPTIONS(1 4) (legacy, see Q-07) | DEFCUST.jcl:22-45 |

### 2.2 Housekeeping and data-movement jobs
| ID | Given input… the job… | Expected output | Source cite |
|----|-----------------------|-----------------|-------------|
| FR-DM-18 | Given the TRANSACT KSDS, TRANBKP copies it to a new backup generation, then deletes and redefines an **empty** KSDS | `TRANSACT.BKUP(+1)`; empty `TRANSACT.VSAM.KSDS` KEYS(16 0) RECORDSIZE(350 350). The AIX is deleted but **not** rebuilt (Q-09) | TRANBKP.jcl:23-67 |
| FR-DM-19 | Given `TRANSACT.BKUP(0)` and `SYSTRAN(0)`, COMBTRAN merges and sorts them by TRAN-ID and reloads the transaction master | `TRANSACT.COMBINED(+1)` sorted by `TRAN-ID,1,16,CH` ascending; REPRO into `TRANSACT.VSAM.KSDS` | COMBTRAN.jcl:22-49 |
| FR-DM-20 | Given TCATBALF, PRTCATBL backs it up and prints a sorted, edited balance listing | `TCATBALF.BKUP(+1)`; `TCATBALF.REPT` LRECL 40, sorted by acct/type/cat, with balance edited `TTTTTTTTT.TT` | PRTCATBL.jcl:21-64 |
| FR-DM-21 | OPENFIL / CLOSEFIL issue CEMT SET FIL … OPE/CLO against region CICSAWSA | Files TRANSACT, CCXREF, ACCTDAT, CXACAIX and USRSEC opened or closed (CARDDAT, CARDAIX and CUSTDAT are not included) | OPENFIL.jcl:22-30; CLOSEFIL.jcl:22-30 |
| FR-DM-22 | Given TRANSACT KSDS, CREASTMT (data-prep steps) re-keys transactions by card + tran id into a statement work KSDS | `TRXFL.VSAM.KSDS` KEYS(32 0) RECORDSIZE(350 350), loaded from `TRXFL.SEQ` sorted `(263,16,CH,A,1,16,CH,A)`, layout COSTM01 | CREASTMT.JCL:22-62 |

### 2.3 Data utilities (dump, export, import)
| ID | Given input… the job… | Expected output | Source cite |
|----|-----------------------|-----------------|-------------|
| FR-DM-23 | Given ACCTDATA KSDS, READACCT/CBACT01C reads every account in key order and prints each field. It also writes three derived files | SYSOUT listing `ACCT-ID :` … `ACCT-GROUP-ID :`; `ACCTDATA.PSCOMP` (fixed, CYC-DEBIT as COMP-3), `ACCTDATA.ARRYPS` (5-occurrence array), `ACCTDATA.VBPS` (variable 12- and 39-byte records) | CBACT01C:140-160, 165-310; READACCT.jcl:22-46 |
| FR-DM-24 | Given CARDDATA KSDS, READCARD/CBACT02C prints each card record | One SYSOUT line per CARD-RECORD | CBACT02C:70-116; READCARD.jcl:22-26 |
| FR-DM-25 | Given CARDXREF KSDS, READXREF/CBACT03C prints each xref record | Each CARD-XREF-RECORD is displayed **twice** (Q-12) | CBACT03C:70-116; READXREF.jcl:22-26 |
| FR-DM-26 | Given CUSTDATA KSDS, READCUST/CBCUS01C prints each customer record | Each CUSTOMER-RECORD is displayed **twice** (Q-12) | CBCUS01C:70-116; READCUST.jcl:21-25 |
| FR-DM-27 | Given the five master KSDSs, CBEXPORT writes one export file containing every customer, account, xref, transaction and card | `AWS.M2.CARDDEMO.EXPORT.DATA` (KSDS, 500-byte CVEXPORT records) typed `C`/`A`/`X`/`T`/`D`, with sequence numbers 1…n; counts are displayed at the end | CBEXPORT:146-575; CBEXPORT.jcl:24-63 |
| FR-DM-28 | Given the export file, CBIMPORT splits it back into one sequential file per entity and writes unknown record types to an error file | `CUSTDATA.IMPORT` (500), `ACCTDATA.IMPORT` (300), `CARDXREF.IMPORT` (50), `TRANSACT.IMPORT` (350), card output DD `CARDOUT`, `IMPORT.ERRORS` (132); counts are displayed | CBIMPORT:162-480; CBIMPORT.jcl:22-57 |

### 2.4 Online access paths (CSD FILE resources)
| ID | As a user I can… | Expected visible result | Source cite |
|----|------------------|-------------------------|-------------|
| FR-DM-29 | Use any CardDemo screen whose program addresses file ACCTDAT, CARDDAT, CCXREF, CUSTDAT, TRANSACT or USRSEC by its CICS name | The file maps to the base KSDS listed in §3.3. All eight FILE resources allow ADD/BROWSE/DELETE/READ/UPDATE | CARDDEMO.CSD:1-99 |
| FR-DM-30 | Look up cards or xref rows by **account** from a screen (card list/detail, account view/update, bill pay, transaction add) | The lookup is served by PATH resources CARDAIX (→ `CARDDATA.VSAM.AIX.PATH`) and CXACAIX (→ `CARDXREF.VSAM.AIX.PATH`) | CARDDEMO.CSD:13-24, 63-75; COCRDSLC:783; COACTVWC:727; COBIL00C:410; COTRN02C:578 |

---

## 3. Screen / interface spec — datasets, record layouts, reference data
This scope has no online screens, so the BMS field table and PF-key map do not apply. The batch DD/file I/O table is §3.5.

### 3.1 Dataset catalogue
| Dataset (all `AWS.M2.CARDDEMO.` unless shown) | Org | RECLN | Primary key (off,len) | Alternate index / path | Copybook (01) | Defined by | LISTCAT (entry / REC-TOTAL) |
|---|---|---|---|---|---|---|---|
| `ACCTDATA.VSAM.KSDS` | KSDS | 300 F | ACCT-ID (0,11) | — | CVACT01Y `ACCOUNT-RECORD` | ACCTFILE.jcl:36-49 | LISTCAT.txt:22, 59-64 / 50 |
| `CARDDATA.VSAM.KSDS` | KSDS | 150 F | CARD-NUM (0,16) | `CARDDATA.VSAM.AIX` on CARD-ACCT-ID (16,11) non-unique, UPGRADE; path `…AIX.PATH` | CVACT02Y `CARD-RECORD` | CARDFILE.jcl:50-112 | LISTCAT.txt:150-287 (AXRKP 16, LISTCAT.txt:283) / 50 |
| `CARDXREF.VSAM.KSDS` | KSDS | 50 F | XREF-CARD-NUM (0,16) | `CARDXREF.VSAM.AIX` on XREF-ACCT-ID (25,11) non-unique, UPGRADE; path `…AIX.PATH` | CVACT03Y `CARD-XREF-RECORD` | XREFFILE.jcl:39-102 | LISTCAT.txt:351-506 (AXRKP 25) / 50 |
| `CUSTDATA.VSAM.KSDS` | KSDS | 500 F | CUST-ID (0,9) | — | CVCUS01Y (CBSTM03A uses CUSTREC) `CUSTOMER-RECORD` | CUSTFILE.jcl:46-58 | LISTCAT.txt:595, 632-637 / 50 |
| `TRANSACT.VSAM.KSDS` | KSDS | 350 F | TRAN-ID (0,16) | `TRANSACT.VSAM.AIX` on TRAN-PROC-TS (304,26) non-unique, UPGRADE; path `…AIX.PATH` | CVTRA05Y `TRAN-RECORD` | TRANFILE.jcl:49-111; TRANIDX.jcl:25-54 | LISTCAT.txt:3541-3680 (AXRKP 304) / 311 (AIX 39) |
| `TCATBALF.VSAM.KSDS` | KSDS | 50 F | TRAN-CAT-KEY (0,17) = acct(11)+type(2)+cat(4) | — | CVTRA01Y `TRAN-CAT-BAL-RECORD` | TCATBALF.jcl:36-48 | LISTCAT.txt:1334, 1371-1376 / 100 |
| `DISCGRP.VSAM.KSDS` | KSDS | 50 F | DIS-GROUP-KEY (0,16) = group(10)+type(2)+cat(4) | — | CVTRA02Y `DIS-GROUP-RECORD` | DISCGRP.jcl:36-48 | LISTCAT.txt:859, 896-901 / 51 |
| `TRANTYPE.VSAM.KSDS` | KSDS | 60 F | TRAN-TYPE (0,2) | — | CVTRA03Y `TRAN-TYPE-RECORD` | TRANTYPE.jcl:36-48 | LISTCAT.txt:3742, 3779-3784 / 7 |
| `TRANCATG.VSAM.KSDS` | KSDS | 60 F | TRAN-CAT-KEY (0,6) = type(2)+cat(4) | — | CVTRA04Y `TRAN-CAT-RECORD` | TRANCATG.jcl:36-48 | LISTCAT.txt:1440, 1475-1482 / 18 |
| `USRSEC.VSAM.KSDS` | KSDS | 80 F | SEC-USR-ID (0,8) | — | CSUSR01Y `SEC-USER-DATA` | DUSRSECJ.jcl:64-73 | LISTCAT.txt:3846, 3883-3888 / 10 |
| `USRSEC.VSAM.ESDS` | ESDS | 80 | (RBA) | — | CSUSR01Y (no program reads it) | ESDSRRDS.jcl:65-72 | not in LISTCAT |
| `USRSEC.VSAM.RRDS` | RRDS | 80 | (RRN) | — | CSUSR01Y (no program reads it) | ESDSRRDS.jcl:99-106 | not in LISTCAT |
| `EXPORT.DATA` | KSDS | 500 F | IDCAMS KEYS(4 28); program key EXPORT-SEQUENCE-NUM is at offset 27 (Q-01) | — | CVEXPORT `EXPORT-RECORD` | CBEXPORT.jcl:30-38 | not in LISTCAT |
| `TRXFL.VSAM.KSDS` | KSDS | 350 F | TRNX-KEY (0,32) = card(16)+tran id(16) | — | COSTM01 `TRNX-RECORD` | CREASTMT.JCL:29-37 | not in LISTCAT |
| `AWS.CUSTDATA.CLUSTER` | KSDS | 500 | (0,10) | — | (none) | DEFCUST.jcl:35-45 | not in LISTCAT |
| `TRANSACT.BKUP` | GDG LIMIT 5 SCRATCH | 350 | — | — | CVTRA05Y | DEFGDGB.jcl:24-28 | LISTCAT.txt:1631 |
| `TRANSACT.DALY` | GDG LIMIT 5 SCRATCH | 350 | sorted by card num | — | CVTRA05Y | DEFGDGB.jcl:30-34 | LISTCAT.txt:3021 |
| `TRANSACT.COMBINED` | GDG LIMIT 5 SCRATCH | 350 | sorted by TRAN-ID | — | CVTRA05Y | DEFGDGB.jcl:54-58 | LISTCAT.txt:2919 |
| `SYSTRAN` | GDG LIMIT 5 SCRATCH | 350 | — | — | CVTRA05Y (interest transactions) | DEFGDGB.jcl:48-52 | LISTCAT.txt:1098 |
| `TRANREPT` | GDG LIMIT 5 SCRATCH (REPTFILE: LIMIT 10) | report | — | — | CVTRA07Y (print lines) | DEFGDGB.jcl:36-40; REPTFILE.jcl:25-28 | LISTCAT.txt:1527, 1535 (LIMIT 5) |
| `TCATBALF.BKUP` | GDG LIMIT 5 SCRATCH | 50 | — | — | CVTRA01Y | DEFGDGB.jcl:42-46 | LISTCAT.txt:1202 |
| `DALYREJS` | GDG LIMIT 5 SCRATCH | (reject rec) | — | — | CBTRN02C FD (posting scope) | DALYREJS.jcl:24-28 | LISTCAT.txt:684-690 |
| `TRANTYPE.BKUP` / `TRANCATG.PS.BKUP` / `DISCGRP.BKUP` | GDG LIMIT 5 SCRATCH | 60 / 60 / 50 | — | — | CVTRA03Y / CVTRA04Y / CVTRA02Y | DEFGDGD.jcl:27-31, 50-54, 73-77 | not in LISTCAT |
| `ACCTDATA.PS`, `CARDDATA.PS`, `CUSTDATA.PS`, `CARDXREF.PS`, `DISCGRP.PS`, `TCATBALF.PS`, `TRANCATG.PS`, `TRANTYPE.PS`, `DALYTRAN.PS`, `DALYTRAN.PS.INIT`, `USRSEC.PS` | PS (load / input) | as the KSDS | — | — | as the KSDS | (pre-existing; ASCII copies in `app/data/ASCII`) | LISTCAT.txt:7, 133, 334, 580, 786, 801, 844, 1304, 1423, 3727, 3831 |
| `DATEPARM` | PS | — | — | — | CBTRN03C FD (reports scope) | — | LISTCAT.txt:818 |

Storage attributes common to every KSDS defined in the base JCL: `SHAREOPTIONS(2 3)`, `ERASE`, `INDEXED`, `CYLINDERS(1 5)`, `VOLUMES(AWSHJ1)`. The exceptions are TRANTYPE `SHAREOPTIONS(1 4)` (TRANTYPE.jcl:42) and USRSEC (no ERASE; `REUSE`, `TRACKS(45,15)`, `CISZ(8192)`, DUSRSECJ.jcl:64-73). LISTCAT confirms CISIZE 18432 for all data components except USRSEC, which is 8192 (LISTCAT.txt:59, 3883).

### 3.2 Record layouts (copybooks)
Every 01-level record length below was recomputed from the PIC clauses and matches the `RECLN` comment in the copybook and the IDCAMS RECORDSIZE. Offsets are 0-based. "Line" is the copybook line.

#### 3.2.1 `CVACT01Y.cpy` — ACCTDATA account master

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| ACCOUNT-RECORD | 01 | group | 0 | 300 | Account master record (RECLN 300) | CVACT01Y:4 |
| &nbsp;&nbsp;ACCT-ID | 05 | 9(11) | 0 | 11 | Account number; **primary key** of ACCTDATA. 11 digits, zero-filled (sample `00000000001`–`00000000050`) | CVACT01Y:5 |
| &nbsp;&nbsp;ACCT-ACTIVE-STATUS | 05 | X(01) | 11 | 1 | `Y` active / `N` inactive (edit rule COACTUPC:193). Sample: all `Y` | CVACT01Y:6 |
| &nbsp;&nbsp;ACCT-CURR-BAL | 05 | S9(10)V99 | 12 | 12 | Current balance, signed zoned decimal, 2 dp (e.g. `00000001940{` = +194.00) | CVACT01Y:7 |
| &nbsp;&nbsp;ACCT-CREDIT-LIMIT | 05 | S9(10)V99 | 24 | 12 | Credit limit, signed zoned, 2 dp | CVACT01Y:8 |
| &nbsp;&nbsp;ACCT-CASH-CREDIT-LIMIT | 05 | S9(10)V99 | 36 | 12 | Cash-advance limit, signed zoned, 2 dp | CVACT01Y:9 |
| &nbsp;&nbsp;ACCT-OPEN-DATE | 05 | X(10) | 48 | 10 | Open date, text `YYYY-MM-DD` | CVACT01Y:10 |
| &nbsp;&nbsp;ACCT-EXPIRAION-DATE | 05 | X(10) | 58 | 10 | Expiry date `YYYY-MM-DD` (name misspelt in source) | CVACT01Y:11 |
| &nbsp;&nbsp;ACCT-REISSUE-DATE | 05 | X(10) | 68 | 10 | Reissue date `YYYY-MM-DD` | CVACT01Y:12 |
| &nbsp;&nbsp;ACCT-CURR-CYC-CREDIT | 05 | S9(10)V99 | 78 | 12 | Cycle-to-date credits; +amt when posting amt ≥ 0 (CBTRN02C:548-549); reset to 0 by interest run (CBACT04C:353) | CVACT01Y:13 |
| &nbsp;&nbsp;ACCT-CURR-CYC-DEBIT | 05 | S9(10)V99 | 90 | 12 | Cycle-to-date debits; +amt when posting amt < 0 (CBTRN02C:550-551); reset to 0 by interest run (CBACT04C:354) | CVACT01Y:14 |
| &nbsp;&nbsp;ACCT-ADDR-ZIP | 05 | X(10) | 102 | 10 | Account ZIP. Sample data holds `A000000000` here for all 50 rows (see Q-03) | CVACT01Y:15 |
| &nbsp;&nbsp;ACCT-GROUP-ID | 05 | X(10) | 112 | 10 | Disclosure group id → DISCGRP key part 1 (CBACT04C:210). Blank in all sample rows (see Q-03) | CVACT01Y:16 |
| &nbsp;&nbsp;FILLER | 05 | X(178) | 122 | 178 | Unused / padding | CVACT01Y:17 |

#### 3.2.2 `CVACT02Y.cpy` — CARDDATA card master

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| CARD-RECORD | 01 | group | 0 | 150 | Card master record (RECLN 150) | CVACT02Y:4 |
| &nbsp;&nbsp;CARD-NUM | 05 | X(16) | 0 | 16 | Card number; **primary key** of CARDDATA (16 chars, numeric in sample) | CVACT02Y:5 |
| &nbsp;&nbsp;CARD-ACCT-ID | 05 | 9(11) | 16 | 11 | Owning account → ACCTDATA. **AIX key** (CARDDATA.VSAM.AIX, non-unique) | CVACT02Y:6 |
| &nbsp;&nbsp;CARD-CVV-CD | 05 | 9(03) | 27 | 3 | Card verification value, 3 digits, stored in clear | CVACT02Y:7 |
| &nbsp;&nbsp;CARD-EMBOSSED-NAME | 05 | X(50) | 30 | 50 | Name embossed on card | CVACT02Y:8 |
| &nbsp;&nbsp;CARD-EXPIRAION-DATE | 05 | X(10) | 80 | 10 | Card expiry `YYYY-MM-DD` (name misspelt in source) | CVACT02Y:9 |
| &nbsp;&nbsp;CARD-ACTIVE-STATUS | 05 | X(01) | 90 | 1 | `Y` / `N` (edit rule COCRDUPC:195-196). Sample: all `Y` | CVACT02Y:10 |
| &nbsp;&nbsp;FILLER | 05 | X(59) | 91 | 59 | Unused / padding | CVACT02Y:11 |

#### 3.2.3 `CVACT03Y.cpy` — CARDXREF card cross-reference

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| CARD-XREF-RECORD | 01 | group | 0 | 50 | Card ↔ customer ↔ account cross-reference (RECLN 50) | CVACT03Y:4 |
| &nbsp;&nbsp;XREF-CARD-NUM | 05 | X(16) | 0 | 16 | Card number; **primary key** of CARDXREF → CARDDATA | CVACT03Y:5 |
| &nbsp;&nbsp;XREF-CUST-ID | 05 | 9(09) | 16 | 9 | Customer id → CUSTDATA | CVACT03Y:6 |
| &nbsp;&nbsp;XREF-ACCT-ID | 05 | 9(11) | 25 | 11 | Account id → ACCTDATA. **AIX key** (CARDXREF.VSAM.AIX, non-unique; CICS file CXACAIX) | CVACT03Y:7 |
| &nbsp;&nbsp;FILLER | 05 | X(14) | 36 | 14 | Unused / padding | CVACT03Y:8 |

#### 3.2.4 `CVCUS01Y.cpy` — CUSTDATA customer master

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| CUSTOMER-RECORD | 01 | group | 0 | 500 | Customer master record (RECLN 500) | CVCUS01Y:4 |
| &nbsp;&nbsp;CUST-ID | 05 | 9(09) | 0 | 9 | Customer id; **primary key** of CUSTDATA (9 digits, sample 1–50) | CVCUS01Y:5 |
| &nbsp;&nbsp;CUST-FIRST-NAME | 05 | X(25) | 9 | 25 | First name | CVCUS01Y:6 |
| &nbsp;&nbsp;CUST-MIDDLE-NAME | 05 | X(25) | 34 | 25 | Middle name | CVCUS01Y:7 |
| &nbsp;&nbsp;CUST-LAST-NAME | 05 | X(25) | 59 | 25 | Last name | CVCUS01Y:8 |
| &nbsp;&nbsp;CUST-ADDR-LINE-1 | 05 | X(50) | 84 | 50 | Address line 1 | CVCUS01Y:9 |
| &nbsp;&nbsp;CUST-ADDR-LINE-2 | 05 | X(50) | 134 | 50 | Address line 2 | CVCUS01Y:10 |
| &nbsp;&nbsp;CUST-ADDR-LINE-3 | 05 | X(50) | 184 | 50 | Address line 3 (city in sample) | CVCUS01Y:11 |
| &nbsp;&nbsp;CUST-ADDR-STATE-CD | 05 | X(02) | 234 | 2 | 2-char state code (US states/territories in sample) | CVCUS01Y:12 |
| &nbsp;&nbsp;CUST-ADDR-COUNTRY-CD | 05 | X(03) | 236 | 3 | 3-char country code (`USA` for all sample rows) | CVCUS01Y:13 |
| &nbsp;&nbsp;CUST-ADDR-ZIP | 05 | X(10) | 239 | 10 | ZIP / ZIP+4 text | CVCUS01Y:14 |
| &nbsp;&nbsp;CUST-PHONE-NUM-1 | 05 | X(15) | 249 | 15 | Phone 1, formatted text e.g. `(908)119-8310` | CVCUS01Y:15 |
| &nbsp;&nbsp;CUST-PHONE-NUM-2 | 05 | X(15) | 264 | 15 | Phone 2 | CVCUS01Y:16 |
| &nbsp;&nbsp;CUST-SSN | 05 | 9(09) | 279 | 9 | Social Security number, 9 digits, stored in clear | CVCUS01Y:17 |
| &nbsp;&nbsp;CUST-GOVT-ISSUED-ID | 05 | X(20) | 288 | 20 | Government-issued id (20 chars, digits in sample) | CVCUS01Y:18 |
| &nbsp;&nbsp;CUST-DOB-YYYY-MM-DD | 05 | X(10) | 308 | 10 | Date of birth `YYYY-MM-DD` | CVCUS01Y:19 |
| &nbsp;&nbsp;CUST-EFT-ACCOUNT-ID | 05 | X(10) | 318 | 10 | EFT (bank) account id | CVCUS01Y:20 |
| &nbsp;&nbsp;CUST-PRI-CARD-HOLDER-IND | 05 | X(01) | 328 | 1 | Primary card-holder indicator (`Y` in all sample rows) | CVCUS01Y:21 |
| &nbsp;&nbsp;CUST-FICO-CREDIT-SCORE | 05 | 9(03) | 329 | 3 | FICO score, 3 digits (sample 001–793) | CVCUS01Y:22 |
| &nbsp;&nbsp;FILLER | 05 | X(168) | 332 | 168 | Unused / padding | CVCUS01Y:23 |

#### 3.2.5 `CUSTREC.cpy` — Customer layout used by CBSTM03A (byte-identical to CVCUS01Y)

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| CUSTOMER-RECORD | 01 | group | 0 | 500 | Customer master record (RECLN 500) | CUSTREC:4 |
| &nbsp;&nbsp;CUST-ID | 05 | 9(09) | 0 | 9 | Customer id; **primary key** of CUSTDATA (9 digits, sample 1–50) | CUSTREC:5 |
| &nbsp;&nbsp;CUST-FIRST-NAME | 05 | X(25) | 9 | 25 | First name | CUSTREC:6 |
| &nbsp;&nbsp;CUST-MIDDLE-NAME | 05 | X(25) | 34 | 25 | Middle name | CUSTREC:7 |
| &nbsp;&nbsp;CUST-LAST-NAME | 05 | X(25) | 59 | 25 | Last name | CUSTREC:8 |
| &nbsp;&nbsp;CUST-ADDR-LINE-1 | 05 | X(50) | 84 | 50 | Address line 1 | CUSTREC:9 |
| &nbsp;&nbsp;CUST-ADDR-LINE-2 | 05 | X(50) | 134 | 50 | Address line 2 | CUSTREC:10 |
| &nbsp;&nbsp;CUST-ADDR-LINE-3 | 05 | X(50) | 184 | 50 | Address line 3 (city in sample) | CUSTREC:11 |
| &nbsp;&nbsp;CUST-ADDR-STATE-CD | 05 | X(02) | 234 | 2 | 2-char state code (US states/territories in sample) | CUSTREC:12 |
| &nbsp;&nbsp;CUST-ADDR-COUNTRY-CD | 05 | X(03) | 236 | 3 | 3-char country code (`USA` for all sample rows) | CUSTREC:13 |
| &nbsp;&nbsp;CUST-ADDR-ZIP | 05 | X(10) | 239 | 10 | ZIP / ZIP+4 text | CUSTREC:14 |
| &nbsp;&nbsp;CUST-PHONE-NUM-1 | 05 | X(15) | 249 | 15 | Phone 1, formatted text e.g. `(908)119-8310` | CUSTREC:15 |
| &nbsp;&nbsp;CUST-PHONE-NUM-2 | 05 | X(15) | 264 | 15 | Phone 2 | CUSTREC:16 |
| &nbsp;&nbsp;CUST-SSN | 05 | 9(09) | 279 | 9 | Social Security number, 9 digits, stored in clear | CUSTREC:17 |
| &nbsp;&nbsp;CUST-GOVT-ISSUED-ID | 05 | X(20) | 288 | 20 | Government-issued id (20 chars, digits in sample) | CUSTREC:18 |
| &nbsp;&nbsp;CUST-DOB-YYYYMMDD | 05 | X(10) | 308 | 10 | Date of birth; same bytes as CVCUS01Y `CUST-DOB-YYYY-MM-DD` (name differs only) | CUSTREC:19 |
| &nbsp;&nbsp;CUST-EFT-ACCOUNT-ID | 05 | X(10) | 318 | 10 | EFT (bank) account id | CUSTREC:20 |
| &nbsp;&nbsp;CUST-PRI-CARD-HOLDER-IND | 05 | X(01) | 328 | 1 | Primary card-holder indicator (`Y` in all sample rows) | CUSTREC:21 |
| &nbsp;&nbsp;CUST-FICO-CREDIT-SCORE | 05 | 9(03) | 329 | 3 | FICO score, 3 digits (sample 001–793) | CUSTREC:22 |
| &nbsp;&nbsp;FILLER | 05 | X(168) | 332 | 168 | Unused / padding | CUSTREC:23 |

#### 3.2.6 `CVTRA01Y.cpy` — TCATBALF transaction-category balance

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| TRAN-CAT-BAL-RECORD | 01 | group | 0 | 50 | Transaction-category balance per account (RECLN 50) | CVTRA01Y:4 |
| &nbsp;&nbsp;TRAN-CAT-KEY | 05 | group | 0 | 17 | **Primary key** (17 bytes for TCATBALF; 6 bytes for TRANCATG) | CVTRA01Y:5 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRANCAT-ACCT-ID | 10 | 9(11) | 0 | 11 | Account id → ACCTDATA | CVTRA01Y:6 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRANCAT-TYPE-CD | 10 | X(02) | 11 | 2 | Transaction type → TRANTYPE | CVTRA01Y:7 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRANCAT-CD | 10 | 9(04) | 13 | 4 | Category code → TRANCATG (with type) | CVTRA01Y:8 |
| &nbsp;&nbsp;TRAN-CAT-BAL | 05 | S9(09)V99 | 17 | 11 | Running balance for account/type/category, signed zoned 2 dp | CVTRA01Y:9 |
| &nbsp;&nbsp;FILLER | 05 | X(22) | 28 | 22 | Unused / padding | CVTRA01Y:10 |

#### 3.2.7 `CVTRA02Y.cpy` — DISCGRP disclosure group

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| DIS-GROUP-RECORD | 01 | group | 0 | 50 | Disclosure group (interest-rate) record (RECLN 50) | CVTRA02Y:4 |
| &nbsp;&nbsp;DIS-GROUP-KEY | 05 | group | 0 | 16 | **Primary key** (16 bytes) | CVTRA02Y:5 |
| &nbsp;&nbsp;&nbsp;&nbsp;DIS-ACCT-GROUP-ID | 10 | X(10) | 0 | 10 | Group id: `A000000000`, `DEFAULT`, `ZEROAPR` in sample | CVTRA02Y:6 |
| &nbsp;&nbsp;&nbsp;&nbsp;DIS-TRAN-TYPE-CD | 10 | X(02) | 10 | 2 | Transaction type → TRANTYPE | CVTRA02Y:7 |
| &nbsp;&nbsp;&nbsp;&nbsp;DIS-TRAN-CAT-CD | 10 | 9(04) | 12 | 4 | Category → TRANCATG | CVTRA02Y:8 |
| &nbsp;&nbsp;DIS-INT-RATE | 05 | S9(04)V99 | 16 | 6 | Annual interest rate in percent, 2 dp (e.g. `00150{` = 15.00). Used ÷1200 for monthly interest | CVTRA02Y:9 |
| &nbsp;&nbsp;FILLER | 05 | X(28) | 22 | 28 | Unused / padding | CVTRA02Y:10 |

#### 3.2.8 `CVTRA03Y.cpy` — TRANTYPE transaction type

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| TRAN-TYPE-RECORD | 01 | group | 0 | 60 | Transaction type reference record (RECLN 60) | CVTRA03Y:4 |
| &nbsp;&nbsp;TRAN-TYPE | 05 | X(02) | 0 | 2 | Type code; **primary key** of TRANTYPE (`01`–`07`, see §3.4) | CVTRA03Y:5 |
| &nbsp;&nbsp;TRAN-TYPE-DESC | 05 | X(50) | 2 | 50 | Type description | CVTRA03Y:6 |
| &nbsp;&nbsp;FILLER | 05 | X(08) | 52 | 8 | Unused / padding | CVTRA03Y:7 |

#### 3.2.9 `CVTRA04Y.cpy` — TRANCATG transaction category

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| TRAN-CAT-RECORD | 01 | group | 0 | 60 | Transaction category reference record (RECLN 60) | CVTRA04Y:4 |
| &nbsp;&nbsp;TRAN-CAT-KEY | 05 | group | 0 | 6 | **Primary key** (17 bytes for TCATBALF; 6 bytes for TRANCATG) | CVTRA04Y:5 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRAN-TYPE-CD | 10 | X(02) | 0 | 2 | Type code → TRANTYPE | CVTRA04Y:6 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRAN-CAT-CD | 10 | 9(04) | 2 | 4 | Category code → TRANCATG (key with type) | CVTRA04Y:7 |
| &nbsp;&nbsp;TRAN-CAT-TYPE-DESC | 05 | X(50) | 6 | 50 | Category description | CVTRA04Y:8 |
| &nbsp;&nbsp;FILLER | 05 | X(04) | 56 | 4 | Unused / padding | CVTRA04Y:9 |

#### 3.2.10 `CVTRA05Y.cpy` — TRANSACT transaction master (also SYSTRAN/BKUP/DALY/COMBINED GDGs)

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| TRAN-RECORD | 01 | group | 0 | 350 | Transaction record (RECLN 350); TRANSACT KSDS and SYSTRAN / BKUP / DALY / COMBINED GDGs | CVTRA05Y:4 |
| &nbsp;&nbsp;TRAN-ID | 05 | X(16) | 0 | 16 | Transaction id; **primary key** of TRANSACT (16-char numeric string) | CVTRA05Y:5 |
| &nbsp;&nbsp;TRAN-TYPE-CD | 05 | X(02) | 16 | 2 | Type code → TRANTYPE | CVTRA05Y:6 |
| &nbsp;&nbsp;TRAN-CAT-CD | 05 | 9(04) | 18 | 4 | Category code → TRANCATG (key with type) | CVTRA05Y:7 |
| &nbsp;&nbsp;TRAN-SOURCE | 05 | X(10) | 22 | 10 | Origin channel: `POS TERM`, `OPERATOR` (sample), `System` (interest, CBACT04C:484) | CVTRA05Y:8 |
| &nbsp;&nbsp;TRAN-DESC | 05 | X(100) | 32 | 100 | Free-text description | CVTRA05Y:9 |
| &nbsp;&nbsp;TRAN-AMT | 05 | S9(09)V99 | 132 | 11 | Amount, signed zoned 2 dp; negative = credit/return in sample (type 03) | CVTRA05Y:10 |
| &nbsp;&nbsp;TRAN-MERCHANT-ID | 05 | 9(09) | 143 | 9 | Merchant id (9 digits; `800000000` in all sample rows) | CVTRA05Y:11 |
| &nbsp;&nbsp;TRAN-MERCHANT-NAME | 05 | X(50) | 152 | 50 | Merchant name | CVTRA05Y:12 |
| &nbsp;&nbsp;TRAN-MERCHANT-CITY | 05 | X(50) | 202 | 50 | Merchant city | CVTRA05Y:13 |
| &nbsp;&nbsp;TRAN-MERCHANT-ZIP | 05 | X(10) | 252 | 10 | Merchant ZIP | CVTRA05Y:14 |
| &nbsp;&nbsp;TRAN-CARD-NUM | 05 | X(16) | 262 | 16 | Card number → CARDXREF / CARDDATA | CVTRA05Y:15 |
| &nbsp;&nbsp;TRAN-ORIG-TS | 05 | X(26) | 278 | 26 | Origination timestamp `YYYY-MM-DD HH:MM:SS.ffffff` | CVTRA05Y:16 |
| &nbsp;&nbsp;TRAN-PROC-TS | 05 | X(26) | 304 | 26 | Processing timestamp, same format; set at posting (CBTRN02C:438). **AIX key** of TRANSACT (offset 304, len 26) | CVTRA05Y:17 |
| &nbsp;&nbsp;FILLER | 05 | X(20) | 330 | 20 | Unused / padding | CVTRA05Y:18 |

#### 3.2.11 `CVTRA06Y.cpy` — DALYTRAN daily transaction input

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| DALYTRAN-RECORD | 01 | group | 0 | 350 | Daily transaction input record (RECLN 350) — same layout as TRAN-RECORD | CVTRA06Y:4 |
| &nbsp;&nbsp;DALYTRAN-ID | 05 | X(16) | 0 | 16 | Same as `TRAN-ID` | CVTRA06Y:5 |
| &nbsp;&nbsp;DALYTRAN-TYPE-CD | 05 | X(02) | 16 | 2 | Same as `TRAN-TYPE-CD` | CVTRA06Y:6 |
| &nbsp;&nbsp;DALYTRAN-CAT-CD | 05 | 9(04) | 18 | 4 | Same as `TRAN-CAT-CD` | CVTRA06Y:7 |
| &nbsp;&nbsp;DALYTRAN-SOURCE | 05 | X(10) | 22 | 10 | Same as `TRAN-SOURCE` | CVTRA06Y:8 |
| &nbsp;&nbsp;DALYTRAN-DESC | 05 | X(100) | 32 | 100 | Same as `TRAN-DESC` | CVTRA06Y:9 |
| &nbsp;&nbsp;DALYTRAN-AMT | 05 | S9(09)V99 | 132 | 11 | Same as `TRAN-AMT` | CVTRA06Y:10 |
| &nbsp;&nbsp;DALYTRAN-MERCHANT-ID | 05 | 9(09) | 143 | 9 | Same as `TRAN-MERCHANT-ID` | CVTRA06Y:11 |
| &nbsp;&nbsp;DALYTRAN-MERCHANT-NAME | 05 | X(50) | 152 | 50 | Same as `TRAN-MERCHANT-NAME` | CVTRA06Y:12 |
| &nbsp;&nbsp;DALYTRAN-MERCHANT-CITY | 05 | X(50) | 202 | 50 | Same as `TRAN-MERCHANT-CITY` | CVTRA06Y:13 |
| &nbsp;&nbsp;DALYTRAN-MERCHANT-ZIP | 05 | X(10) | 252 | 10 | Same as `TRAN-MERCHANT-ZIP` | CVTRA06Y:14 |
| &nbsp;&nbsp;DALYTRAN-CARD-NUM | 05 | X(16) | 262 | 16 | Same as `TRAN-CARD-NUM` | CVTRA06Y:15 |
| &nbsp;&nbsp;DALYTRAN-ORIG-TS | 05 | X(26) | 278 | 26 | Same as `TRAN-ORIG-TS` | CVTRA06Y:16 |
| &nbsp;&nbsp;DALYTRAN-PROC-TS | 05 | X(26) | 304 | 26 | Processing timestamp; blank in all 300 sample input rows | CVTRA06Y:17 |
| &nbsp;&nbsp;FILLER | 05 | X(20) | 330 | 20 | Unused / padding | CVTRA06Y:18 |

#### 3.2.12 `CSUSR01Y.cpy` — USRSEC user security

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| SEC-USER-DATA | 01 | group | 0 | 80 | User-security record (RECLN 80) | CSUSR01Y:17 |
| &nbsp;&nbsp;SEC-USR-ID | 05 | X(08) | 0 | 8 | User id; **primary key** of USRSEC (8 chars, e.g. `ADMIN001`, `USER0001`) | CSUSR01Y:18 |
| &nbsp;&nbsp;SEC-USR-FNAME | 05 | X(20) | 8 | 20 | First name | CSUSR01Y:19 |
| &nbsp;&nbsp;SEC-USR-LNAME | 05 | X(20) | 28 | 20 | Last name | CSUSR01Y:20 |
| &nbsp;&nbsp;SEC-USR-PWD | 05 | X(08) | 48 | 8 | Password, 8 chars, **plain text** (sample `PASSWORD`) | CSUSR01Y:21 |
| &nbsp;&nbsp;SEC-USR-TYPE | 05 | X(01) | 56 | 1 | `A` admin / `U` regular user (88-levels on CDEMO-USER-TYPE, COCOM01Y:27-28) | CSUSR01Y:22 |
| &nbsp;&nbsp;SEC-USR-FILLER | 05 | X(23) | 57 | 23 | Unused | CSUSR01Y:23 |

#### 3.2.13 `CVEXPORT.cpy` — EXPORT.DATA multi-record export

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| EXPORT-RECORD | 01 | group | 0 | 500 | Multi-record export record (500 bytes) written by CBEXPORT, read by CBIMPORT | CVEXPORT:9 |
| &nbsp;&nbsp;EXPORT-REC-TYPE | 05 | X(1) | 0 | 1 | `C` customer, `A` account, `X` xref, `T` transaction, `D` card (CBEXPORT:274/343/407/462/527) | CVEXPORT:10 |
| &nbsp;&nbsp;EXPORT-TIMESTAMP | 05 | X(26) | 1 | 26 | Export run timestamp (same value on every record) | CVEXPORT:11 |
| &nbsp;&nbsp;EXPORT-TIMESTAMP-R | 05 | group REDEFINES EXPORT-TIMESTAMP | 1 | 26 | Redefinition of the timestamp | CVEXPORT:12 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXPORT-DATE | 10 | X(10) | 1 | 10 | Date part | CVEXPORT:13 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXPORT-DATE-TIME-SEP | 10 | X(1) | 11 | 1 | Separator | CVEXPORT:14 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXPORT-TIME | 10 | X(15) | 12 | 15 | Time part | CVEXPORT:15 |
| &nbsp;&nbsp;EXPORT-SEQUENCE-NUM | 05 | 9(9) COMP | 27 | 4 | Running record counter from 1 (binary). Declared RECORD KEY of EXPFILE (CBEXPORT:68) | CVEXPORT:16 |
| &nbsp;&nbsp;EXPORT-BRANCH-ID | 05 | X(4) | 31 | 4 | Constant `0001` (CBEXPORT:278) | CVEXPORT:17 |
| &nbsp;&nbsp;EXPORT-REGION-CODE | 05 | X(5) | 35 | 5 | Constant `NORTH` (CBEXPORT:279) | CVEXPORT:18 |
| &nbsp;&nbsp;EXPORT-RECORD-DATA | 05 | X(460) | 40 | 460 | 460-byte payload, redefined per record type | CVEXPORT:19 |
| &nbsp;&nbsp;EXPORT-CUSTOMER-DATA | 05 | group REDEFINES EXPORT-RECORD-DATA | 40 | 460 | Payload for `C` | CVEXPORT:24 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-ID | 10 | 9(09) COMP | 40 | 4 | From `CUST-ID` | CVEXPORT:25 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-FIRST-NAME | 10 | X(25) | 44 | 25 | From `CUST-FIRST-NAME` | CVEXPORT:26 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-MIDDLE-NAME | 10 | X(25) | 69 | 25 | From `CUST-MIDDLE-NAME` | CVEXPORT:27 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-LAST-NAME | 10 | X(25) | 94 | 25 | From `CUST-LAST-NAME` | CVEXPORT:28 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-ADDR-LINES | 10 | group OCCURS 3 | 119 | 150 (50×3) | CUST-ADDR-LINE-1..3 | CVEXPORT:29 |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-ADDR-LINE | 15 | X(50) | 119 | 50 | Address line (×3) | CVEXPORT:30 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-ADDR-STATE-CD | 10 | X(02) | 269 | 2 | From `CUST-ADDR-STATE-CD` | CVEXPORT:31 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-ADDR-COUNTRY-CD | 10 | X(03) | 271 | 3 | From `CUST-ADDR-COUNTRY-CD` | CVEXPORT:32 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-ADDR-ZIP | 10 | X(10) | 274 | 10 | From `CUST-ADDR-ZIP` | CVEXPORT:33 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-PHONE-NUMS | 10 | group OCCURS 2 | 284 | 30 (15×2) | CUST-PHONE-NUM-1..2 | CVEXPORT:34 |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-PHONE-NUM | 15 | X(15) | 284 | 15 | Phone (×2) | CVEXPORT:35 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-SSN | 10 | 9(09) | 314 | 9 | From `CUST-SSN` | CVEXPORT:36 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-GOVT-ISSUED-ID | 10 | X(20) | 323 | 20 | From `CUST-GOVT-ISSUED-ID` | CVEXPORT:37 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-DOB-YYYY-MM-DD | 10 | X(10) | 343 | 10 | From `CUST-DOB-YYYY-MM-DD` | CVEXPORT:38 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-EFT-ACCOUNT-ID | 10 | X(10) | 353 | 10 | From `CUST-EFT-ACCOUNT-ID` | CVEXPORT:39 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-PRI-CARD-HOLDER-IND | 10 | X(01) | 363 | 1 | From `CUST-PRI-CARD-HOLDER-IND` | CVEXPORT:40 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CUST-FICO-CREDIT-SCORE | 10 | 9(03) COMP-3 | 364 | 2 | From `CUST-FICO-CREDIT-SCORE` | CVEXPORT:41 |
| &nbsp;&nbsp;&nbsp;&nbsp;FILLER | 10 | X(134) | 366 | 134 | Unused / padding | CVEXPORT:42 |
| &nbsp;&nbsp;EXPORT-ACCOUNT-DATA | 05 | group REDEFINES EXPORT-RECORD-DATA | 40 | 460 | Payload for `A` | CVEXPORT:47 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-ACCT-ID | 10 | 9(11) | 40 | 11 | From `ACCT-ID` | CVEXPORT:48 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-ACCT-ACTIVE-STATUS | 10 | X(01) | 51 | 1 | From `ACCT-ACTIVE-STATUS` | CVEXPORT:49 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-ACCT-CURR-BAL | 10 | S9(10)V99 COMP-3 | 52 | 7 | From `ACCT-CURR-BAL` | CVEXPORT:50 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-ACCT-CREDIT-LIMIT | 10 | S9(10)V99 | 59 | 12 | From `ACCT-CREDIT-LIMIT` | CVEXPORT:51 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-ACCT-CASH-CREDIT-LIMIT | 10 | S9(10)V99 COMP-3 | 71 | 7 | From `ACCT-CASH-CREDIT-LIMIT` | CVEXPORT:52 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-ACCT-OPEN-DATE | 10 | X(10) | 78 | 10 | From `ACCT-OPEN-DATE` | CVEXPORT:53 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-ACCT-EXPIRAION-DATE | 10 | X(10) | 88 | 10 | From `ACCT-EXPIRAION-DATE` | CVEXPORT:54 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-ACCT-REISSUE-DATE | 10 | X(10) | 98 | 10 | From `ACCT-REISSUE-DATE` | CVEXPORT:55 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-ACCT-CURR-CYC-CREDIT | 10 | S9(10)V99 | 108 | 12 | From `ACCT-CURR-CYC-CREDIT` | CVEXPORT:56 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-ACCT-CURR-CYC-DEBIT | 10 | S9(10)V99 COMP | 120 | 8 | From `ACCT-CURR-CYC-DEBIT` | CVEXPORT:57 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-ACCT-ADDR-ZIP | 10 | X(10) | 128 | 10 | From `ACCT-ADDR-ZIP` | CVEXPORT:58 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-ACCT-GROUP-ID | 10 | X(10) | 138 | 10 | From `ACCT-GROUP-ID` | CVEXPORT:59 |
| &nbsp;&nbsp;&nbsp;&nbsp;FILLER | 10 | X(352) | 148 | 352 | Unused / padding | CVEXPORT:60 |
| &nbsp;&nbsp;EXPORT-TRANSACTION-DATA | 05 | group REDEFINES EXPORT-RECORD-DATA | 40 | 460 | Payload for `T` | CVEXPORT:65 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-TRAN-ID | 10 | X(16) | 40 | 16 | From `TRAN-ID` | CVEXPORT:66 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-TRAN-TYPE-CD | 10 | X(02) | 56 | 2 | From `TRAN-TYPE-CD` | CVEXPORT:67 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-TRAN-CAT-CD | 10 | 9(04) | 58 | 4 | From `TRAN-CAT-CD` | CVEXPORT:68 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-TRAN-SOURCE | 10 | X(10) | 62 | 10 | From `TRAN-SOURCE` | CVEXPORT:69 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-TRAN-DESC | 10 | X(100) | 72 | 100 | From `TRAN-DESC` | CVEXPORT:70 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-TRAN-AMT | 10 | S9(09)V99 COMP-3 | 172 | 6 | From `TRAN-AMT` | CVEXPORT:71 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-TRAN-MERCHANT-ID | 10 | 9(09) COMP | 178 | 4 | From `TRAN-MERCHANT-ID` | CVEXPORT:72 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-TRAN-MERCHANT-NAME | 10 | X(50) | 182 | 50 | From `TRAN-MERCHANT-NAME` | CVEXPORT:73 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-TRAN-MERCHANT-CITY | 10 | X(50) | 232 | 50 | From `TRAN-MERCHANT-CITY` | CVEXPORT:74 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-TRAN-MERCHANT-ZIP | 10 | X(10) | 282 | 10 | From `TRAN-MERCHANT-ZIP` | CVEXPORT:75 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-TRAN-CARD-NUM | 10 | X(16) | 292 | 16 | From `TRAN-CARD-NUM` | CVEXPORT:76 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-TRAN-ORIG-TS | 10 | X(26) | 308 | 26 | From `TRAN-ORIG-TS` | CVEXPORT:77 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-TRAN-PROC-TS | 10 | X(26) | 334 | 26 | From `TRAN-PROC-TS` | CVEXPORT:78 |
| &nbsp;&nbsp;&nbsp;&nbsp;FILLER | 10 | X(140) | 360 | 140 | Unused / padding | CVEXPORT:79 |
| &nbsp;&nbsp;EXPORT-CARD-XREF-DATA | 05 | group REDEFINES EXPORT-RECORD-DATA | 40 | 460 | Payload for `X` | CVEXPORT:84 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-XREF-CARD-NUM | 10 | X(16) | 40 | 16 | From `XREF-CARD-NUM` | CVEXPORT:85 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-XREF-CUST-ID | 10 | 9(09) | 56 | 9 | From `XREF-CUST-ID` | CVEXPORT:86 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-XREF-ACCT-ID | 10 | 9(11) COMP | 65 | 8 | From `XREF-ACCT-ID` | CVEXPORT:87 |
| &nbsp;&nbsp;&nbsp;&nbsp;FILLER | 10 | X(427) | 73 | 427 | Unused / padding | CVEXPORT:88 |
| &nbsp;&nbsp;EXPORT-CARD-DATA | 05 | group REDEFINES EXPORT-RECORD-DATA | 40 | 460 | Payload for `D` | CVEXPORT:93 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CARD-NUM | 10 | X(16) | 40 | 16 | From `CARD-NUM` | CVEXPORT:94 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CARD-ACCT-ID | 10 | 9(11) COMP | 56 | 8 | From `CARD-ACCT-ID` | CVEXPORT:95 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CARD-CVV-CD | 10 | 9(03) COMP | 64 | 2 | From `CARD-CVV-CD` | CVEXPORT:96 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CARD-EMBOSSED-NAME | 10 | X(50) | 66 | 50 | From `CARD-EMBOSSED-NAME` | CVEXPORT:97 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CARD-EXPIRAION-DATE | 10 | X(10) | 116 | 10 | From `CARD-EXPIRAION-DATE` | CVEXPORT:98 |
| &nbsp;&nbsp;&nbsp;&nbsp;EXP-CARD-ACTIVE-STATUS | 10 | X(01) | 126 | 1 | From `CARD-ACTIVE-STATUS` | CVEXPORT:99 |
| &nbsp;&nbsp;&nbsp;&nbsp;FILLER | 10 | X(373) | 127 | 373 | Unused / padding | CVEXPORT:100 |

#### 3.2.14 `COSTM01.CPY` — TRXFL statement work file

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| TRNX-RECORD | 01 | group | 0 | 350 | Statement work record (RECLN 350) in TRXFL KSDS built by CREASTMT | COSTM01:20 |
| &nbsp;&nbsp;TRNX-KEY | 05 | group | 0 | 32 | **Primary key** of TRXFL (32 bytes = card + tran id) | COSTM01:21 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRNX-CARD-NUM | 10 | X(16) | 0 | 16 | Same as `TRAN-CARD-NUM` | COSTM01:22 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRNX-ID | 10 | X(16) | 16 | 16 | Same as `TRAN-ID` | COSTM01:23 |
| &nbsp;&nbsp;TRNX-REST | 05 | group | 32 | 318 | Remaining TRAN-RECORD fields | COSTM01:24 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRNX-TYPE-CD | 10 | X(02) | 32 | 2 | Same as `TRAN-TYPE-CD` | COSTM01:25 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRNX-CAT-CD | 10 | 9(04) | 34 | 4 | Same as `TRAN-CAT-CD` | COSTM01:26 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRNX-SOURCE | 10 | X(10) | 38 | 10 | Same as `TRAN-SOURCE` | COSTM01:27 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRNX-DESC | 10 | X(100) | 48 | 100 | Same as `TRAN-DESC` | COSTM01:28 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRNX-AMT | 10 | S9(09)V99 | 148 | 11 | Same as `TRAN-AMT` | COSTM01:29 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRNX-MERCHANT-ID | 10 | 9(09) | 159 | 9 | Same as `TRAN-MERCHANT-ID` | COSTM01:30 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRNX-MERCHANT-NAME | 10 | X(50) | 168 | 50 | Same as `TRAN-MERCHANT-NAME` | COSTM01:31 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRNX-MERCHANT-CITY | 10 | X(50) | 218 | 50 | Same as `TRAN-MERCHANT-CITY` | COSTM01:32 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRNX-MERCHANT-ZIP | 10 | X(10) | 268 | 10 | Same as `TRAN-MERCHANT-ZIP` | COSTM01:33 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRNX-ORIG-TS | 10 | X(26) | 278 | 26 | Same as `TRAN-ORIG-TS` | COSTM01:34 |
| &nbsp;&nbsp;&nbsp;&nbsp;TRNX-PROC-TS | 10 | X(26) | 304 | 26 | Same as `TRAN-PROC-TS` | COSTM01:35 |
| &nbsp;&nbsp;&nbsp;&nbsp;FILLER | 10 | X(20) | 330 | 20 | Unused / padding | COSTM01:36 |

#### 3.2.15 `UNUSED1Y.cpy` — Unused

| Field | Lvl | PIC / usage | Offset | Len | Meaning / allowed values | Line |
|---|---|---|---|---|---|---|
| UNUSED-DATA | 01 | group | 0 | 80 | Unused copybook; same 80-byte shape as SEC-USER-DATA. No program copies it | UNUSED1Y:1 |
| &nbsp;&nbsp;UNUSED-ID | 05 | X(08) | 0 | 8 | Same as `SEC-USR-ID` | UNUSED1Y:2 |
| &nbsp;&nbsp;UNUSED-FNAME | 05 | X(20) | 8 | 20 | Same as `SEC-USR-FNAME` | UNUSED1Y:3 |
| &nbsp;&nbsp;UNUSED-LNAME | 05 | X(20) | 28 | 20 | Same as `SEC-USR-LNAME` | UNUSED1Y:4 |
| &nbsp;&nbsp;UNUSED-PWD | 05 | X(08) | 48 | 8 | Same as `SEC-USR-PWD` | UNUSED1Y:5 |
| &nbsp;&nbsp;UNUSED-TYPE | 05 | X(01) | 56 | 1 | Same as `SEC-USR-TYPE` | UNUSED1Y:6 |
| &nbsp;&nbsp;UNUSED-FILLER | 05 | X(23) | 57 | 23 | Same as `SEC-USR-FILLER` | UNUSED1Y:7 |

#### 3.2.16 `CVTRA07Y.cpy` — TRANREPT report print lines (not a stored record)
Print-line structures used by CBTRN03C: `REPORT-NAME-HEADER` (`'DALYREPT'`, `'Daily Transaction Report'`, `'Date Range: '` + start + `' to '` + end), `TRANSACTION-DETAIL-REPORT` (tran id, account id, type code-desc, category code-desc, source, amount `-ZZZ,ZZZ,ZZZ.ZZ`), `TRANSACTION-HEADER-1/2`, and page, account and grand totals (`+ZZZ,ZZZ,ZZZ.ZZ`). CVTRA07Y:4-66. The detail is specified by the reporting scope.

### 3.3 CICS FILE resources (CARDDEMO.CSD, group CARDDEMO)
| CICS FILE | DSNAME | Points at | Ops allowed | Cite |
|---|---|---|---|---|
| ACCTDAT | `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | base KSDS | ADD, BROWSE, DELETE, READ, UPDATE | CARDDEMO.CSD:1-12 |
| CARDAIX | `AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX.PATH` | card AIX path (by account) | same | CARDDEMO.CSD:13-24 |
| CARDDAT | `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` | base KSDS | same | CARDDEMO.CSD:25-36 |
| CCXREF | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` | base KSDS ("CARD TO ACCOUNT XREF") | same | CARDDEMO.CSD:37-49 |
| CUSTDAT | `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` | base KSDS | same | CARDDEMO.CSD:50-62 |
| CXACAIX | `AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH` | xref AIX path ("ALTERNATE INDEX TO CCXREF VIA ACCOUNT KEY") | same | CARDDEMO.CSD:63-75 |
| TRANSACT | `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` | base KSDS | same | CARDDEMO.CSD:76-87 |
| USRSEC | `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS` | base KSDS | same | CARDDEMO.CSD:88-99 |

Common attributes: `RLSACCESS(NO) LSRPOOLNUM(1) READINTEG(UNCOMMITTED) STRINGS(1) OPENTIME(FIRSTREF) DISPOSITION(SHARE) RECORDFORMAT(V) RECOVERY(NONE) JOURNAL(NO)` (CARDDEMO.CSD:2-9). TRANTYPE, TRANCATG, DISCGRP, TCATBALF, the TRANSACT AIX path and the USRSEC ESDS/RRDS have **no** CICS FILE; they are batch-only.

### 3.4 Reference data (decoded from `app/data/ASCII`)

#### 3.4.1 Transaction types — TRANTYPE (key TRAN-TYPE)
| Code | Description | FILLER | Line |
|---|---|---|---|
| `01` | Purchase | `00000000` | trantype.txt:1 |
| `02` | Payment | `00000000` | trantype.txt:2 |
| `03` | Credit | `00000000` | trantype.txt:3 |
| `04` | Authorization | `00000000` | trantype.txt:4 |
| `05` | Refund | `00000000` | trantype.txt:5 |
| `06` | Reversal | `00000000` | trantype.txt:6 |
| `07` | Adjustment | `00000000` | trantype.txt:7 |

#### 3.4.2 Transaction categories — TRANCATG (key type+category)
| Type | Cat | Type desc | Category description | Line |
|---|---|---|---|---|
| `01` | `0001` | Purchase | Regular Sales Draft | trancatg.txt:1 |
| `01` | `0002` | Purchase | Regular Cash Advance | trancatg.txt:2 |
| `01` | `0003` | Purchase | Convenience Check Debit | trancatg.txt:3 |
| `01` | `0004` | Purchase | ATM Cash Advance | trancatg.txt:4 |
| `01` | `0005` | Purchase | Interest Amount | trancatg.txt:5 |
| `02` | `0001` | Payment | Cash payment | trancatg.txt:6 |
| `02` | `0002` | Payment | Electronic payment | trancatg.txt:7 |
| `02` | `0003` | Payment | Check payment | trancatg.txt:8 |
| `03` | `0001` | Credit | Credit to Account | trancatg.txt:9 |
| `03` | `0002` | Credit | Credit to Purchase balance | trancatg.txt:10 |
| `03` | `0003` | Credit | Credit to Cash balance | trancatg.txt:11 |
| `04` | `0001` | Authorization | Zero dollar authorization | trancatg.txt:12 |
| `04` | `0002` | Authorization | Online purchase authorization | trancatg.txt:13 |
| `04` | `0003` | Authorization | Travel booking authorization | trancatg.txt:14 |
| `05` | `0001` | Refund | Refund credit | trancatg.txt:15 |
| `06` | `0001` | Reversal | Fraud reversal | trancatg.txt:16 |
| `06` | `0002` | Reversal | Non-fraud reversal | trancatg.txt:17 |
| `07` | `0001` | Adjustment | Sales draft credit adjustment | trancatg.txt:18 |

#### 3.4.3 Disclosure groups — DISCGRP (annual interest rate %, DIS-INT-RATE S9(04)V99)
| Type | Cat | Category | `A000000000` | `DEFAULT` | `ZEROAPR` | Lines |
|---|---|---|---|---|---|---|
| `01` | `0001` | Regular Sales Draft | 15.00 | 15.00 | 0.00 | discgrp.txt:1,18,35 |
| `01` | `0002` | Regular Cash Advance | 25.00 | 25.00 | 0.00 | discgrp.txt:2,19,36 |
| `01` | `0003` | Convenience Check Debit | 25.00 | 25.00 | 0.00 | discgrp.txt:3,20,37 |
| `01` | `0004` | ATM Cash Advance | 25.00 | 25.00 | 0.00 | discgrp.txt:4,21,38 |
| `02` | `0001` | Cash payment | 0.00 | 0.00 | 0.00 | discgrp.txt:5,22,39 |
| `02` | `0002` | Electronic payment | 0.00 | 0.00 | 0.00 | discgrp.txt:6,23,40 |
| `02` | `0003` | Check payment | 0.00 | 0.00 | 0.00 | discgrp.txt:7,24,41 |
| `03` | `0001` | Credit to Account | 0.00 | 0.00 | 0.00 | discgrp.txt:8,25,42 |
| `03` | `0002` | Credit to Purchase balance | 0.00 | 0.00 | 0.00 | discgrp.txt:9,26,43 |
| `03` | `0003` | Credit to Cash balance | 0.00 | 0.00 | 0.00 | discgrp.txt:10,27,44 |
| `04` | `0001` | Zero dollar authorization | 15.00 | 15.00 | 0.00 | discgrp.txt:11,28,45 |
| `04` | `0002` | Online purchase authorization | 15.00 | 15.00 | 0.00 | discgrp.txt:12,29,46 |
| `04` | `0003` | Travel booking authorization | 15.00 | 15.00 | 0.00 | discgrp.txt:13,30,47 |
| `05` | `0001` | Refund credit | 15.00 | 15.00 | 0.00 | discgrp.txt:14,31,48 |
| `06` | `0001` | Fraud reversal | 15.00 | 15.00 | 0.00 | discgrp.txt:15,32,49 |
| `06` | `0002` | Non-fraud reversal | 15.00 | 15.00 | 0.00 | discgrp.txt:16,33,50 |
| `07` | `0001` | Sales draft credit adjustment | 15.00 | 0.00 | 0.00 | discgrp.txt:17,34,51 |

Categories with **no** DISCGRP row: `01`/`0005` (Interest Amount) (Q-04). Group `A000000000` = lines 1-17, `DEFAULT` = 18-34, `ZEROAPR` = 35-51. FILLER is 28 × `0` on every row.

#### 3.4.4 Transaction-category balance seed — TCATBALF
50 rows (tcatbal.txt:1-50), one per account `00000000001`–`00000000050`, all type `01` / category `0001`, balance `0000000000{` = 0.00.

#### 3.4.5 User security seed — USRSEC (DUSRSECJ.jcl:35-44, identical rows in ESDSRRDS.jcl:36-45)
| SEC-USR-ID | First | Last | Password | Type |
|---|---|---|---|---|
| ADMIN001 | MARGARET | GOLD | PASSWORD | A |
| ADMIN002 | RUSSELL | RUSSELL | PASSWORD | A |
| ADMIN003 | RAYMOND | WHITMORE | PASSWORD | A |
| ADMIN004 | EMMANUEL | CASGRAIN | PASSWORD | A |
| ADMIN005 | GRANVILLE | LACHAPELLE | PASSWORD | A |
| USER0001 | LAWRENCE | THOMAS | PASSWORD | U |
| USER0002 | AJITH | KUMAR | PASSWORD | U |
| USER0003 | LAURITZ | ALME | PASSWORD | U |
| USER0004 | AVERARDO | MAZZI | PASSWORD | U |
| USER0005 | LEE | TING | PASSWORD | U |

The in-stream rows are 57 bytes (id 8 + first 20 + last 20 + password 8 + type 1); IEBGENER pads them to LRECL 80.

#### 3.4.6 Master sample-data profile
| File | Rows / length | Profile | Lines |
|---|---|---|---|
| acctdata.txt | 50 × 300 | ACCT-ID 1–50; status all `Y`; CYC credit/debit 0; ADDR-ZIP = `A000000000`, GROUP-ID blank on every row (Q-03); expiry 2023-01-06 … 2025-12-28 | acctdata.txt:1-50 |
| carddata.txt | 50 × 150 | one card per account (50 distinct CARD-ACCT-ID); status all `Y` | carddata.txt:1-50 |
| cardxref.txt | 50 × 36 (FILLER trimmed) | 1:1:1 card/customer/account; every xref card exists in carddata with the same account | cardxref.txt:1-50 |
| custdata.txt | 50 × 500 | CUST-ID 1–50; country `USA`; primary-holder `Y`; FICO 001–793 | custdata.txt:1-50 |
| dailytran.txt | 300 × 350 | 250 × type `01`/cat `0001`, source `POS TERM`; 50 × type `03`/cat `0001`, source `OPERATOR`, negative amounts; amounts −998.33 … 999.77; merchant id `800000000`; ORIG-TS all 2022-06-10; PROC-TS blank; every card is in cardxref; 300 distinct TRAN-IDs | dailytran.txt:1-300 |
| discgrp.txt / tcatbal.txt / trancatg.txt / trantype.txt | 51×50 / 50×50 / 18×60 / 7×60 | see §3.4.1–3.4.4 | — |

### 3.5 Batch DD / file I/O (jobs in this scope)
| Job (step / PGM) | DD | Dataset | Disp / direction | Cite |
|---|---|---|---|---|
| ACCTFILE STEP15 IDCAMS REPRO | ACCTDATA → ACCTVSAM | `ACCTDATA.PS` → `ACCTDATA.VSAM.KSDS` | in → out | ACCTFILE.jcl:54-61 |
| CARDFILE STEP15 | CARDDATA → CARDVSAM | `CARDDATA.PS` → `CARDDATA.VSAM.KSDS` | in → out | CARDFILE.jcl:68-75 |
| CUSTFILE STEP15 | CUSTDATA → CUSTVSAM | `CUSTDATA.PS` → `CUSTDATA.VSAM.KSDS` | in → out | CUSTFILE.jcl:64-71 |
| XREFFILE STEP15 | XREFDATA → XREFVSAM | `CARDXREF.PS` → `CARDXREF.VSAM.KSDS` | in → out | XREFFILE.jcl:57-64 |
| TRANFILE STEP15 | TRANSACT → TRANVSAM | `DALYTRAN.PS.INIT` → `TRANSACT.VSAM.KSDS` | in → out | TRANFILE.jcl:67-74 |
| DISCGRP / TCATBALF / TRANCATG / TRANTYPE STEP15 | DISCGRP→DISCVSAM / TCATBAL→TCATBALV / TRANCATG→TCATVSAM / TRANTYPE→TTYPVSAM | `*.PS` → `*.VSAM.KSDS` | in → out | DISCGRP.jcl:54-61; TCATBALF.jcl:54-61; TRANCATG.jcl:54-61; TRANTYPE.jcl:54-61 |
| DUSRSECJ STEP01 IEBGENER / STEP03 IDCAMS | SYSUT1 (in-stream) → SYSUT2; IN → OUT | `USRSEC.PS` (LRECL 80 FB) → `USRSEC.VSAM.KSDS` | new; in → out | DUSRSECJ.jcl:32-48, 80-87 |
| READACCT STEP05 CBACT01C | ACCTFILE; OUTFILE; ARRYFILE; VBRCFILE | `ACCTDATA.VSAM.KSDS`; `ACCTDATA.PSCOMP`; `ACCTDATA.ARRYPS`; `ACCTDATA.VBPS` | in; new ×3 (pre-deleted by IEFBR14) | READACCT.jcl:22-46 |
| READCARD / READXREF / READCUST STEP05 | CARDFILE / XREFFILE / CUSTFILE | the matching KSDS | in | READCARD.jcl:22-26; READXREF.jcl:22-26; READCUST.jcl:21-25 |
| CBEXPORT STEP01 IDCAMS + STEP02 CBEXPORT | CUSTFILE, ACCTFILE, XREFFILE, TRANSACT, CARDFILE; EXPFILE | five base KSDSs; `EXPORT.DATA` | in ×5; out | CBEXPORT.jcl:24-63 |
| CBIMPORT STEP01 | EXPFILE; CUSTOUT; ACCTOUT; XREFOUT; TRNXOUT; ERROUT (no CARDOUT, Q-02) | `EXPORT.DATA`; `CUSTDATA.IMPORT` (500); `ACCTDATA.IMPORT` (300); `CARDXREF.IMPORT` (50); `TRANSACT.IMPORT` (350); `IMPORT.ERRORS` (132) | in; new ×5 | CBIMPORT.jcl:22-57 |
| TRANBKP STEP05R REPROC / STEP05 / STEP10 | FILEIN → FILEOUT | `TRANSACT.VSAM.KSDS` → `TRANSACT.BKUP(+1)`; delete + redefine KSDS | in → new | TRANBKP.jcl:23-67 |
| COMBTRAN STEP05R SORT / STEP10 IDCAMS | SORTIN (2) → SORTOUT; TRANSACT → TRANVSAM | `TRANSACT.BKUP(0)` + `SYSTRAN(0)` → `TRANSACT.COMBINED(+1)` → `TRANSACT.VSAM.KSDS` | in → new → out | COMBTRAN.jcl:22-49 |
| PRTCATBL STEP05R REPROC / STEP10R SORT | FILEIN → FILEOUT; SORTIN → SORTOUT | `TCATBALF.VSAM.KSDS` → `TCATBALF.BKUP(+1)` → `TCATBALF.REPT` (LRECL 40) | in → new → new | PRTCATBL.jcl:21-64 |
| CREASTMT DELDEF01 / STEP010 SORT / STEP020 REPRO | SORTIN → SORTOUT; INFILE → OUTFILE | `TRANSACT.VSAM.KSDS` → `TRXFL.SEQ` (LRECL 350) → `TRXFL.VSAM.KSDS` | in → new → out | CREASTMT.JCL:22-62 |

---

## 4. Business rules & calculations
| ID | Rule | Source cite |
|----|------|-------------|
| BR-DM-01 | **Account identity.** An account is identified by ACCT-ID, 11 numeric digits at offset 0. It is the unique KSDS key, and every other entity references an account through this value (CARD-ACCT-ID, XREF-ACCT-ID, TRANCAT-ACCT-ID). | CVACT01Y:5; ACCTFILE.jcl:40; CVACT02Y:6; CVACT03Y:7; CVTRA01Y:6 |
| BR-DM-02 | **Card identity and ownership.** A card is identified by CARD-NUM X(16), the unique key. CARD-ACCT-ID is a **non-unique** alternate key, so one account may own many cards. The AIX is defined UPGRADE, so VSAM maintains it on every base write. | CVACT02Y:5-6; CARDFILE.jcl:54, 83-88 |
| BR-DM-03 | **Card → customer → account resolution goes through CARDXREF.** XREF-CARD-NUM is the unique key. XREF-ACCT-ID (offset 25) is a non-unique, UPGRADE alternate key. Online account-to-card lookups READ the path CXACAIX; card-to-account lookups READ CCXREF. Batch reads the alternate key with `KEY IS FD-XREF-ACCT-ID`, through DD `XREFFIL1` bound to the AIX PATH. | CVACT03Y:5-7; XREFFILE.jcl:43, 72-77; COTRN02C:578, 611; COBIL00C:410; COACTVWC:727; CBACT04C:38, 393-397; INTCALC.jcl:30-32 |
| BR-DM-04 | **Customer identity.** CUST-ID 9(09) at offset 0 is the unique key. A customer is reached from a card or account only through XREF-CUST-ID. CUSTDATA has no alternate index. | CVCUS01Y:5; CUSTFILE.jcl:50; CVACT03Y:6 |
| BR-DM-05 | **Transaction identity.** TRAN-ID X(16) is the unique key of TRANSACT. TRAN-PROC-TS (offset 304, len 26) is a non-unique, UPGRADE alternate key. No CSD FILE resource points at the TRANSACT AIX PATH, so online programs cannot use it. | CVTRA05Y:5, 17; TRANFILE.jcl:53, 82-87; CARDDEMO.CSD:76-87 |
| BR-DM-06 | **New online Tran IDs are max + 1.** COBIL00C and COTRN02C browse TRANSACT from HIGH-VALUES with READPREV to find the highest TRAN-ID, then add 1 as a 16-digit number. The CT02 half of this rule is specified in the signed-off Transaction Management FR. | COBIL00C:212-219; COTRN02C:444, 644-704 |
| BR-DM-07 | **Interest-transaction IDs** are `PARM-DATE` concatenated with a running suffix, not max + 1. These rows go to GDG `SYSTRAN(+1)`, not directly to TRANSACT. COMBTRAN later merges them into the master, sorted by TRAN-ID. | CBACT04C:473-480; INTCALC.jcl:41; COMBTRAN.jcl:22-49 |
| BR-DM-08 | **Category-balance key** is the 17-byte composite TRANCAT-ACCT-ID(11) + TRANCAT-TYPE-CD(2) + TRANCAT-CD(4). There is one row per account per type/category. | CVTRA01Y:5-8; TCATBALF.jcl:40 |
| BR-DM-09 | **Category-balance maintenance (posting).** Posting builds the key from XREF-ACCT-ID and the daily type and category. If the key is not found (status 23), a new row is created with the amount. Otherwise the amount is added and the row is rewritten. Any status other than 00/23 abends. | CBTRN02C:467-542 |
| BR-DM-10 | **Account balance maintenance (posting).** ACCT-CURR-BAL += amount. If amount ≥ 0, ACCT-CURR-CYC-CREDIT += amount; otherwise ACCT-CURR-CYC-DEBIT += amount. If the REWRITE gets INVALID KEY, the record is flagged with reason 109 `ACCOUNT RECORD NOT FOUND`. | CBTRN02C:545-559 |
| BR-DM-11 | **Processing timestamp** is assigned at posting (`DB2-FORMAT-TS` → TRAN-PROC-TS). Daily input rows carry a blank PROC-TS (all 300 sample rows). | CBTRN02C:438; dailytran.txt:1-300 |
| BR-DM-12 | **Disclosure-group key** is DIS-ACCT-GROUP-ID(10) + DIS-TRAN-TYPE-CD(2) + DIS-TRAN-CAT-CD(4) = 16 bytes. The interest job fills it from ACCT-GROUP-ID and the TCATBAL type/category. | CVTRA02Y:5-8; DISCGRP.jcl:40; CBACT04C:210-213 |
| BR-DM-13 | **Default disclosure group.** If the group/type/category key is missing (status 23), the job displays two messages, sets the group to `DEFAULT` and re-reads. If the DEFAULT row is missing too, the job abends. | CBACT04C:415-460 |
| BR-DM-14 | **Monthly interest** = `TRAN-CAT-BAL × DIS-INT-RATE / 1200`. It is computed and written only when DIS-INT-RATE ≠ 0. DIS-INT-RATE is an annual percentage with 2 decimals (e.g. `00150{` = 15.00). Fees are a stub (`To be implemented`). | CBACT04C:214-217, 462-468, 518-520; CVTRA02Y:9 |
| BR-DM-15 | **Interest transaction content:** TRAN-TYPE-CD `01`, TRAN-CAT-CD `05` (→ 0005 "Interest Amount"), TRAN-SOURCE `System`, TRAN-DESC `Int. for a/c ` + ACCT-ID, merchant id 0, merchant name/city/zip spaces, TRAN-CARD-NUM = the XREF card for the account, ORIG-TS = PROC-TS = current timestamp. | CBACT04C:482-498 |
| BR-DM-16 | **Account rewrite after interest.** At each account break and at end of file, ACCT-CURR-BAL += total interest, and ACCT-CURR-CYC-CREDIT and ACCT-CURR-CYC-DEBIT are set to 0. | CBACT04C:194-196, 220, 350-356 |
| BR-DM-17 | **Report lookups must resolve.** When CBTRN03C cannot find a card in CARDXREF, a type in TRANTYPE or a type+category in TRANCATG, it abends (IO-STATUS forced to 23). Reference tables must therefore cover every code present in TRANSACT. | CBTRN03C:484-512 |
| BR-DM-18 | **Reference code domains.** Transaction types are `01`–`07` and categories are the 18 type+category pairs in §3.4.2. Disclosure groups exist for exactly 17 of those 18 pairs; `01`/`0005` Interest Amount has no DISCGRP row. | trantype.txt:1-7; trancatg.txt:1-18; discgrp.txt:1-51 |
| BR-DM-19 | **User type codes.** `A` = admin and `U` = regular user. At sign-on the user id and password are UPPER-CASEd, the record is READ by user id, and the password is compared in plain text. On a match SEC-USR-TYPE is copied into the COMMAREA. Admin goes to XCTL COADM01C; any other type goes to COMEN01C. | COCOM01Y:26-28; COSGN00C:132-136, 209-257 |
| BR-DM-20 | **User add only checks that User Type is non-blank.** Any non-blank character is stored, and any value other than `A` is routed as a regular user (BR-DM-19). | COUSR01C:142-147, 153-158 |
| BR-DM-21 | **Status codes.** ACCT-ACTIVE-STATUS and CARD-ACTIVE-STATUS take `Y` / `N`; the update screens reject other values. | COACTUPC:192-195, 503-504; COCRDUPC:195-196 |
| BR-DM-22 | **Numeric encoding in master files** is display (zoned), with the sign overpunched in the last byte: `{`=+0, `A`–`I`=+1…+9, `}`=−0, `J`–`R`=−1…−9. Examples: acctdata.txt:1 `00000001940{` = +194.00; dailytran.txt:2 `0000009190}` = −919.00. No COMP or COMP-3 field appears in the master copybooks; CVEXPORT is the only packed/binary layout. | CVACT01Y:7-14; CVTRA05Y:10; CVEXPORT:16-96 |
| BR-DM-23 | **Dates and timestamps are text.** Dates are X(10) `YYYY-MM-DD`; timestamps are X(26) `YYYY-MM-DD HH:MM:SS.ffffff`. Nothing at the file layer validates them. | CVACT01Y:10-12; CVTRA05Y:16-17; dailytran.txt:1 |
| BR-DM-24 | **Export framing.** Each record gets a type code (`C`, `A`, `X`, `T`, `D`), the run timestamp, a sequence number from a counter that starts at 1 and increments per record, branch `0001` and region `NORTH`. Records are written per entity in the order customers → accounts → xrefs → transactions → cards. | CBEXPORT:149-158, 274-279, 343-348, 407-412, 462-467, 527-532 |
| BR-DM-25 | **Export numeric re-encoding.** CVEXPORT payloads switch some numerics to COMP / COMP-3. The choice is per field and inconsistent: in the account payload CURR-BAL and CASH-CREDIT-LIMIT are COMP-3, CREDIT-LIMIT and CYC-CREDIT are display, and CYC-DEBIT is COMP. Import moves the values back into the display layouts. | CVEXPORT:47-60; CBEXPORT:338-362; CBIMPORT:323-351 |
| BR-DM-26 | **Import routing.** `C`/`A`/`X`/`T`/`D` are written to the matching sequential output. Any other type increments an "unknown" counter and writes an error record `Unknown record type encountered`. "Validation" only displays fixed text and checks nothing. | CBIMPORT:270-284, 425-442, 449-452 |
| BR-DM-27 | **Statement work-file re-keying.** SORT orders TRANSACT by card (cols 263-278) then TRAN-ID (1-16). OUTREC moves the card number in front: `1:263,16,17:1,262,279:279,50`. The result is loaded into the TRXFL KSDS with key (32,0), and COSTM01 describes the record. | CREASTMT.JCL:29-37, 44-61; COSTM01.CPY:20-36 |
| BR-DM-28 | **GDG retention.** History/backup GDGs keep 5 generations with SCRATCH (TRANSACT.BKUP, TRANSACT.DALY, TRANREPT, TCATBALF.BKUP, SYSTRAN, TRANSACT.COMBINED, DALYREJS, TRANTYPE.BKUP, TRANCATG.PS.BKUP, DISCGRP.BKUP). | DEFGDGB.jcl:24-58; DALYREJS.jcl:24-28; DEFGDGD.jcl:27-31, 50-54, 73-77 |
| BR-DM-29 | **Account dump derivations (CBACT01C).** ACCT-REISSUE-DATE is reformatted by assembler routine COBDATFT (in/out type `2`). If ACCT-CURR-CYC-DEBIT is zero, the output carries **2525.00**. The array record is filled with hard-coded test amounts (1005.00, 1525.00, −1025.00, −2500.00). VB record 1 is 12 bytes (id + status); VB record 2 is 39 bytes (id, balance, limit, reissue year). | CBACT01C:215-237, 253-261, 276-310 |
| BR-DM-30 | **Transaction master rebuild cycle.** TRANBKP copies TRANSACT to BKUP(+1) and recreates the KSDS empty. COMBTRAN sorts BKUP(0) + SYSTRAN(0) by TRAN-ID into COMBINED(+1) and REPROs that into TRANSACT. TRANFILE's initial load comes from `DALYTRAN.PS.INIT`. | TRANBKP.jcl:23-67; COMBTRAN.jcl:22-49; TRANFILE.jcl:67-74 |

## 5. Data access
Operation abbreviations: R = READ, RU = READ UPDATE, RW = REWRITE, W = WRITE, D = DELETE, BR = STARTBR/READNEXT/READPREV/ENDBR (browse), SEQ = sequential batch read. Batch programs use the DD name in the SELECT; online programs use the CICS FILE name.

| File (CICS name / batch DD) | Program | Operation | Key used | Copybook | Cite |
|---|---|---|---|---|---|
| ACCTDATA (ACCTDAT / ACCTFILE) | COACTVWC | R | ACCT-ID from xref | CVACT01Y | COACTVWC:776 |
| | COACTUPC | R; RU; RW | ACCT-ID | CVACT01Y | COACTUPC:3703, 3894, 4065 |
| | COBIL00C | RU; RW | ACCT-ID | CVACT01Y | COBIL00C:345, 379 |
| | COTRN02C | declared only (no EXEC CICS on it) | — | CVACT01Y | COTRN02C:40 |
| | CBACT01C | SEQ | key order | CVACT01Y | CBACT01C:166, 319 |
| | CBACT04C | random R; RW (I-O) | FD-ACCT-ID = TRANCAT-ACCT-ID | CVACT01Y | CBACT04C:291, 356, 373 |
| | CBTRN01C | random R (no JCL in repo) | XREF-ACCT-ID | CVACT01Y | CBTRN01C:243, 327 |
| | CBTRN02C | random R; RW (I-O) | XREF-ACCT-ID | CVACT01Y | CBTRN02C:311, 395, 554 |
| | CBEXPORT | SEQ | — | CVACT01Y | CBEXPORT:207, 329 |
| | CBSTM03B | random R | ACCT-ID | (FD) | CBSTM03B:49-53, 215 |
| | COACCT01, COPAUA0C, COPAUS0C (optional modules) | declared | — | CVACT01Y | app-vsam-mq/cbl/COACCT01:116; app-authorization-ims-db2-mq/cbl/COPAUA0C:35; COPAUS0C:38 |
| CARDDATA (CARDDAT; CARDAIX path / CARDFILE) | COCRDLIC | BR (STARTBR/READNEXT/READPREV/ENDBR) | CARD-NUM | CVACT02Y | COCRDLIC:1129-1375 |
| | COCRDSLC | R by card; R via CARDAIX by account | CARD-NUM; CARD-ACCT-ID | CVACT02Y | COCRDSLC:742, 783 |
| | COCRDUPC | R; RU; RW | CARD-NUM | CVACT02Y | COCRDUPC:1382, 1427, 1477 |
| | COACTVWC, COACTUPC | names declared, no CARDDAT/CARDAIX I/O found | — | CVACT02Y | COACTVWC:187-191; COACTUPC:578-580 |
| | CBACT02C | SEQ | key order | CVACT02Y | CBACT02C:93, 120 |
| | CBTRN01C | OPEN INPUT only | — | CVACT02Y | CBTRN01C:309 |
| | CBEXPORT | SEQ | — | CVACT02Y | CBEXPORT:228, 513 |
| CARDXREF (CCXREF; CXACAIX path / XREFFILE, XREFFIL1, CARDXREF) | COTRN02C | R via CXACAIX; R via CCXREF | XREF-ACCT-ID; XREF-CARD-NUM | CVACT03Y | COTRN02C:578, 611 |
| | COBIL00C | R via CXACAIX | XREF-ACCT-ID | CVACT03Y | COBIL00C:410 |
| | COACTVWC / COACTUPC | R via CXACAIX | account id | CVACT03Y | COACTVWC:727; COACTUPC:3654 |
| | CBACT03C | SEQ | key order | CVACT03Y | CBACT03C:93, 120 |
| | CBACT04C | random R by **alternate** key | FD-XREF-ACCT-ID | CVACT03Y | CBACT04C:34-39, 394-397 |
| | CBTRN01C / CBTRN02C | random R | XREF-CARD-NUM = daily card | CVACT03Y | CBTRN01C:229; CBTRN02C:383 |
| | CBTRN03C | random R (DD CARDXREF) | TRAN-CARD-NUM | CVACT03Y | CBTRN03C:33-37, 485 |
| | CBSTM03B | SEQ | key order | (FD) | CBSTM03B:37-41, 165 |
| | CBEXPORT | SEQ | — | CVACT03Y | CBEXPORT:214, 393 |
| CUSTDATA (CUSTDAT / CUSTFILE) | COACTVWC | R | XREF-CUST-ID | CVCUS01Y | COACTVWC:826 |
| | COACTUPC | R; RU; RW | CUST-ID | CVCUS01Y | COACTUPC:3753, 3921, 4085 |
| | CBCUS01C | SEQ | key order | CVCUS01Y | CBCUS01C:93, 120 |
| | CBTRN01C | OPEN INPUT only | — | CVCUS01Y | CBTRN01C:273 |
| | CBSTM03B | random R | CUST-ID | CUSTREC (via CBSTM03A) | CBSTM03B:43-47, 190 |
| | CBEXPORT | SEQ | — | CVCUS01Y | CBEXPORT:200, 260 |
| TRANSACT (TRANSACT / TRANFILE, TRANSACT) | COTRN00C | BR | TRAN-ID | CVTRA05Y | COTRN00C:593-694 |
| | COTRN01C | **RU** (READ UPDATE for a view, see Q-11) | TRAN-ID | CVTRA05Y | COTRN01C:269 |
| | COTRN02C | BR from HIGH-VALUES; W | TRAN-ID | CVTRA05Y | COTRN02C:644-713 |
| | COBIL00C | BR from HIGH-VALUES; W | TRAN-ID | CVTRA05Y | COBIL00C:443-512 |
| | CORPT00C | declared; submits report JCL via TDQ `JOBS` | — | CVTRA05Y | CORPT00C:40, 517-518 |
| | CBTRN02C | W (OPEN OUTPUT on the KSDS, see Q-10) | TRAN-ID | CVTRA05Y | CBTRN02C:34-38, 256, 564 |
| | CBTRN01C | OPEN INPUT only | — | CVTRA05Y | CBTRN01C:345 |
| | CBEXPORT | SEQ | — | CVTRA05Y | CBEXPORT:221, 448 |
| | CBACT04C | W sequential → `SYSTRAN(+1)` | — | CVTRA05Y | CBACT04C:53-56, 309, 500; INTCALC.jcl:41 |
| | CBTRN03C | SEQ of `TRANSACT.DALY(+1)` | — | CVTRA05Y | CBTRN03C:29-31, 249; TRANREPT.jcl:66 |
| TCATBALF (— / TCATBALF) | CBACT04C | SEQ | key order (account break) | CVTRA01Y | CBACT04C:28-32, 326 |
| | CBTRN02C | random R; W; RW | TRAN-CAT-KEY | CVTRA01Y | CBTRN02C:57-61, 474, 510, 528 |
| DISCGRP (— / DISCGRP) | CBACT04C | random R (twice on fallback) | DIS-GROUP-KEY | CVTRA02Y | CBACT04C:47-51, 416, 444 |
| TRANTYPE (— / TRANTYPE) | CBTRN03C | random R | TRAN-TYPE | CVTRA03Y | CBTRN03C:39-43, 495 |
| TRANCATG (— / TRANCATG) | CBTRN03C | random R | TRAN-CAT-KEY | CVTRA04Y | CBTRN03C:45-49, 505 |
| USRSEC (USRSEC) | COSGN00C | R | SEC-USR-ID | CSUSR01Y | COSGN00C:209-217 |
| | COUSR00C | BR | SEC-USR-ID | CSUSR01Y | COUSR00C:588-689 |
| | COUSR01C | W | SEC-USR-ID | CSUSR01Y | COUSR01C:240 |
| | COUSR02C | RU; RW | SEC-USR-ID | CSUSR01Y | COUSR02C:322, 360 |
| | COUSR03C | RU; D | SEC-USR-ID | CSUSR01Y | COUSR03C:269, 307 |
| | COADM01C, COMEN01C | declared only | — | CSUSR01Y | COADM01C:39; COMEN01C:39 |
| DALYTRAN.PS (— / DALYTRAN) | CBTRN01C, CBTRN02C | SEQ | — | CVTRA06Y | CBTRN01C:203; CBTRN02C:346 |
| EXPORT.DATA (— / EXPFILE) | CBEXPORT | W | EXPORT-SEQUENCE-NUM | CVEXPORT | CBEXPORT:65-69, 235, 301 |
| | CBIMPORT | SEQ | — | CVEXPORT | CBIMPORT:37-41, 198, 261 |
| TRXFL.VSAM.KSDS (— / TRNXFILE) | CBSTM03B | SEQ | TRNX-KEY | COSTM01 (via CBSTM03A) | CBSTM03B:31-35, 141 |

**Copybook usage (COPY statements):** CVACT01Y: 16 programs; CVACT02Y: 10; CVACT03Y: 16; CVCUS01Y: 10; CUSTREC and COSTM01: CBSTM03A only; CVTRA01Y: CBACT04C, CBTRN02C; CVTRA02Y: CBACT04C; CVTRA03Y, CVTRA04Y, CVTRA07Y: CBTRN03C; CVTRA05Y: 11 programs; CVTRA06Y: CBTRN01C, CBTRN02C; CSUSR01Y: 14 programs (including the optional Db2 module); CVEXPORT: CBEXPORT, CBIMPORT; **UNUSED1Y: none** (determined by `grep -rl "COPY <name>" app/cbl app/app-*`).

## 6. Message catalogue
All messages are batch SYSOUT DISPLAY lines unless the line says "screen". Text is verbatim; `<x>` marks a value appended at runtime.

| Message (exact) | Condition | Cite |
|---|---|---|
| "START OF EXECUTION OF PROGRAM CBACT01C" / "END OF EXECUTION OF PROGRAM CBACT01C" | Start / end of the account dump (CBACT02C, CBACT03C, CBCUS01C print the same with their own name) | CBACT01C:141, 158; CBACT02C:71, 85; CBACT03C:71, 85; CBCUS01C:71, 85 |
| "ACCT-ID                 :" … "ACCT-GROUP-ID           :" and "-------------------------------------------------" | One block per account | CBACT01C:200-212 |
| "ERROR READING ACCOUNT FILE" | Read status not 00/10 | CBACT01C:192 |
| "ACCOUNT FILE WRITE STATUS IS:" `<status>` | Write to OUTFILE/ARRYFILE/VBRCFILE not 00/10 | CBACT01C:246, 268, 294, 309 |
| "ERROR OPENING ACCTFILE" / "ERROR OPENING OUTFILE" / "ERROR OPENING ARRAYFILE" / "ERROR OPENING VBRC FILE" | Open failure | CBACT01C:328, 345, 363, 381 |
| "ERROR CLOSING ACCOUNT FILE" | Close failure | CBACT01C:399 |
| "ERROR READING CARDFILE" / "ERROR OPENING CARDFILE" / "ERROR CLOSING CARDFILE" | Card dump I/O failure | CBACT02C:110, 129, 147 |
| "ERROR READING XREFFILE" / "ERROR OPENING XREFFILE" / "ERROR CLOSING XREFFILE" | Xref dump I/O failure | CBACT03C:110, 129, 147 |
| "ERROR READING CUSTOMER FILE" / "ERROR OPENING CUSTFILE" / "ERROR CLOSING CUSTOMER FILE" | Customer dump I/O failure | CBCUS01C:110, 129, 147 |
| "ABENDING PROGRAM" | Before CEE3ABD (code 999) | CBACT01C:407-410; CBACT02C:155-158 |
| "FILE STATUS IS: NNNN" `<status>` | Every I/O error path (9910-DISPLAY-IO-STATUS) | CBACT01C:420, 424; CBACT04C:642, 646; CBTRN02C:721, 725 |
| "DISCLOSURE GROUP RECORD MISSING" then "TRY WITH DEFAULT GROUP CODE" | DISCGRP key not found (23) | CBACT04C:418-419 |
| "ERROR READING DISCLOSURE GROUP FILE" | DISCGRP status not 00/23 | CBACT04C:431 |
| "ERROR READING DEFAULT DISCLOSURE GROUP" | DEFAULT row missing or error | CBACT04C:455 |
| "ACCOUNT NOT FOUND: " `<id>` | ACCTDATA or XREF (alt key) INVALID KEY in interest run | CBACT04C:375, 397 |
| "ERROR RE-WRITING ACCOUNT FILE" | Account REWRITE status ≠ 00 | CBACT04C:365 |
| "ERROR WRITING TRANSACTION RECORD" | SYSTRAN write ≠ 00 | CBACT04C:510 |
| "TCATBAL record not found for key : " `<key>` ".. Creating." | TCATBAL INVALID KEY during posting | CBTRN02C:476-477 |
| "ERROR READING TRANSACTION BALANCE FILE" / "ERROR WRITING TRANSACTION BALANCE FILE" / "ERROR REWRITING TRANSACTION BALANCE FILE" | TCATBAL I/O failure | CBTRN02C:489, 520, 538 |
| "ACCOUNT RECORD NOT FOUND" (reject reason 109) | Account REWRITE INVALID KEY | CBTRN02C:557 |
| "INVALID CARD NUMBER : " / "INVALID TRANSACTION TYPE : " / "INVALID TRAN CATG KEY : " `<key>` | Reference lookup miss in the report (then abend) | CBTRN03C:487, 497, 507 |
| "CBEXPORT: Starting Customer Data Export", "CBEXPORT: Export completed", "CBEXPORT: <Entity> exported: " … "CBEXPORT: Total Records Exported: " | Progress and counts | CBEXPORT:163-169, 245-254, 563-572 |
| "ERROR: Cannot open <FILE>, Status: " (CUSTOMER-INPUT, ACCOUNT-INPUT, XREF-INPUT, TRANSACTION-INPUT, CARD-INPUT, EXPORT-OUTPUT) | Open failure → abend | CBEXPORT:202-237 |
| "ERROR: Reading <FILE>, Status: " / "ERROR: Writing export record, Status: " | I/O failure | CBEXPORT:263, 304, 332, 367, 396, 422, 451, 487, 516, 545 |
| "CBEXPORT: ABENDING PROGRAM" | Before CEE3ABD | CBEXPORT:578-579 |
| "CBIMPORT: Starting Customer Data Import", "CBIMPORT: Import completed", "CBIMPORT: Total Records Read: " … "CBIMPORT: Unknown Record Types: " | Progress and counts | CBIMPORT:176-193, 465-477 |
| "ERROR: Cannot open <FILE>, Status: " (EXPORT-INPUT, CUSTOMER-OUTPUT, ACCOUNT-OUTPUT, XREF-OUTPUT, TRANSACTION-OUTPUT, CARD-OUTPUT, ERROR-OUTPUT) | Open failure | CBIMPORT:200-242 |
| "ERROR: Reading EXPORT-INPUT, Status: " / "ERROR: Writing <customer/account/xref/transaction/card/error> record, Status: " | I/O failure | CBIMPORT:264, 315, 344, 364, 394, 417, 442 |
| "Unknown record type encountered" (error-file record, not SYSOUT) | EXPORT-REC-TYPE not C/A/X/T/D | CBIMPORT:425-433 |
| "CBIMPORT: Import validation completed" / "CBIMPORT: No validation errors detected" | Always (no real validation) | CBIMPORT:451-452 |
| "CBIMPORT: ABENDING PROGRAM" | Before CEE3ABD | CBIMPORT:483-484 |
| screen: "Wrong Password. Try again ..." / "User not found. Try again ..." / "Unable to verify the User ..." | USRSEC READ: password mismatch / NOTFND (13) / other RESP (owned by the sign-on spec) | COSGN00C:242, 249, 254 |
| screen: "User Type can NOT be empty..." / "User ID already exist..." / "Unable to Add User..." / "User <id> has been added ..." | USRSEC WRITE edits and results (owned by the user-mgmt spec) | COUSR01C:144, 257, 263, 270 |
| screen: "Account Active Status must be Y or N" / "Card Active Status must be Y or N" | Status-code domain edits | COACTUPC:503-504; COCRDUPC:195-196 |

## 7. Navigation / integration
| Item | Detail | Cite |
|---|---|---|
| Inbound | Nothing calls the data-definition jobs; they are run manually or from the scheduler. CORPT00C (CR00) submits report JCL by writing to extrapartition TDQ `JOBS`. | CORPT00C:517-518 |
| CICS file lifecycle | Define jobs bracket IDCAMS with SDSF `/F CICSAWSA,'CEMT SET FIL(..) CLO'` … `OPE`. OPENFIL and CLOSEFIL do the same for 5 files. | CARDFILE.jcl:22-27, 118-123; CUSTFILE.jcl:22-26, 76-80; TRANFILE.jcl:22-27, 116-121; OPENFIL.jcl:26-30; CLOSEFIL.jcl:26-30 |
| Batch DD ↔ dataset | Posting POSTTRAN: TRANFILE/DALYTRAN/XREFFILE/DALYREJS(+1)/ACCTFILE/TCATBALF. Interest INTCALC: TCATBALF/XREFFILE/XREFFIL1 (AIX path)/ACCTFILE/DISCGRP/TRANSACT=SYSTRAN(+1). Report TRANREPT: BKUP(+1) → DALY(+1) → CBTRN03C with CARDXREF/TRANTYPE/TRANCATG/DATEPARM/TRANREPT(+1). Statement CREASTMT: TRXFL/XREFFILE/ACCTFILE/CUSTFILE. | POSTTRAN.jcl:23-42; INTCALC.jcl:22-41; TRANREPT.jcl:23-80; CREASTMT.JCL:79-96 |
| Suggested load order (from key dependencies) | ACCTDATA, CUSTDATA → CARDDATA (+AIX) → CARDXREF (+AIX) → TRANTYPE, TRANCATG, DISCGRP → TCATBALF → TRANSACT (+AIX) → USRSEC; GDG bases (DEFGDGB, DALYREJS, DEFGDGD) before any batch run. VSAM defines no referential integrity, so this order is inferred. | — |
| COMMAREA keys carried between screens | `CDEMO-USER-ID` X(8) = SEC-USR-ID; `CDEMO-USER-TYPE` X(1) `A`/`U`; `CDEMO-CUST-ID` 9(9) = CUST-ID; `CDEMO-ACCT-ID` 9(11) = ACCT-ID; `CDEMO-ACCT-STATUS` X(1); `CDEMO-CARD-NUM` **9(16)**, while CARD-NUM / XREF-CARD-NUM are X(16) | COCOM01Y:25-28, 33, 38-41 |
| Optional modules | `app-vsam-mq` (COACCT01 / CODATE01) and `app-authorization-ims-db2-mq` (COPAUA0C / COPAUS0C) reuse ACCTDAT/CUSTDAT/CARDDAT/CCXREF/CXACAIX and copybooks CVACT01Y/02Y/03Y/CVCUS01Y. `app-transaction-type-db2` (COTRTLIC / COTRTUPC) holds transaction types in Db2 rather than the TRANTYPE KSDS. | app-vsam-mq/cbl/COACCT01:116; app-authorization-ims-db2-mq/cbl/COPAUA0C:35-39 |

## 8. Edge cases, error handling, abend / return codes
| Case | Behaviour | Cite |
|---|---|---|
| Batch file status convention | `00` → APPL-RESULT 0; `10` (EOF) → 16 (APPL-EOF); anything else → 12, then display "FILE STATUS IS: NNNN", then abend | CBACT02C:92-116, 161-172 |
| Abend code | `MOVE 999 TO ABCODE` + `CALL 'CEE3ABD'` → U0999. CBEXPORT and CBIMPORT call CEE3ABD with no arguments, so the code is unspecified | CBACT01C:406-410; CBACT04C:631-632; CBTRN02C:710-711; CBTRN03C:629-630; CBEXPORT:579; CBIMPORT:484 |
| Posting return code | CBTRN02C sets RETURN-CODE 4 when rejects exist (posting scope) | CBTRN02C:230 |
| Not-found tolerated | DISCGRP status 23 → DEFAULT fallback; TCATBAL status 23 → create row | CBACT04C:422-439; CBTRN02C:474-499 |
| IDCAMS delete of a missing cluster | `IF MAXCC LE 08 THEN SET MAXCC = 0` (master files) vs. an unconditional `SET MAXCC = 0` (DISCGRP, TCATBALF, TRANCATG, TRANTYPE). The unconditional form also hides real DELETE errors | ACCTFILE.jcl:27; CARDFILE.jcl:38, 41; DISCGRP.jcl:27; TCATBALF.jcl:27; TRANCATG.jcl:27; TRANTYPE.jcl:27 |
| GDG already defined | DEFGDGB tolerates `LASTCC=12`. DEFGDGD has no such reset, so a rerun fails STEP10 and `COND=(0,NE)` then skips STEP20/30/40/60. STEP50 has no COND and runs anyway | DEFGDGB.jcl:29; DEFGDGD.jcl:24-89 |
| TRANBKP | Redefine runs only if the delete step RC ≤ 4 (`COND=(4,LT)`) | TRANBKP.jcl:51 |
| CREASTMT data prep | Steps 020/030/040 run only if all earlier steps are RC 0 | CREASTMT.JCL:56, 66, 79 |
| Record-length edge | cardxref.txt lines are 36 bytes; the trailing 14-byte FILLER is trimmed in the ASCII copy, while VSAM RECORDSIZE is 50. trantype.txt and tcatbal.txt mix CRLF and LF line endings | cardxref.txt:1-50; XREFFILE.jcl:44 |

## 9. Open questions / ambiguities / apparent legacy bugs
| ID | Finding | Evidence |
|---|---|---|
| Q-01 | **Export key offset mismatch.** EXPFILE is defined `KEYS(4 28)`, but `EXPORT-SEQUENCE-NUM` (the declared RECORD KEY) sits at offset **27** (1 + 26). The program's key and the cluster key disagree, which normally fails OPEN with a key-definition mismatch. | CBEXPORT.jcl:30-32; CVEXPORT:10-16; CBEXPORT:65-69; CBIMPORT:37-41 |
| Q-02 | **CBIMPORT card output has no DD.** `CARD-OUTPUT ASSIGN TO CARDOUT` is opened OUTPUT, but CBIMPORT.jcl defines no `CARDOUT` DD (only CUSTOUT, ACCTOUT, XREFOUT, TRNXOUT, ERROUT). The open fails → "ERROR: Cannot open CARD-OUTPUT, Status: " → abend. | CBIMPORT:63-66, 233-237; CBIMPORT.jcl:22-57 |
| Q-03 | **Account sample data is shifted.** All 50 acctdata rows hold `A000000000` in ACCT-ADDR-ZIP (offset 102) and blanks in ACCT-GROUP-ID (offset 112). The interest job therefore never finds group `A000000000` and always falls back to `DEFAULT` (BR-DM-13). The group id looks misplaced in the zip slot. | acctdata.txt:1-50; CVACT01Y:15-16; CBACT04C:210, 436-438 |
| Q-04 | **No DISCGRP row for Interest Amount (01/0005).** The type/category exists in TRANCATG but has no disclosure row. Any TCATBAL row with 01/0005 would hit the DEFAULT fallback and then abend ("ERROR READING DEFAULT DISCLOSURE GROUP"). | trancatg.txt:5; discgrp.txt:1-51; CBACT04C:443-459 |
| Q-05 | **Inconsistent rate for 07/0001 (Adjustment).** `A000000000` = 15.00 %, `DEFAULT` = 0.00 %. Unclear whether this is intended. | discgrp.txt:17, 34 |
| Q-06 | **TRANFILE/TRANIDX close and reopen CICS file `CXACAIX`** (the CARDXREF AIX path) around the TRANSACT AIX rebuild. No CICS FILE exists for the TRANSACT AIX path, so this looks like a copy-paste slip. It also leaves CXACAIX briefly unavailable to online users. | TRANFILE.jcl:26-27, 120-121; CARDDEMO.CSD:63-87 |
| Q-07 | **DEFCUST is inconsistent.** It deletes `AWS.CCDA.CUSTDATA.CLUSTER` but defines `AWS.CUSTDATA.CLUSTER` with KEYS(10 0), while CUST-ID is 9 bytes and CUSTFILE uses KEYS(9 0). It also has two steps named STEP05. No program or CSD entry references this dataset. | DEFCUST.jcl:22-45; CUSTFILE.jcl:50 |
| Q-08 | **TRANREPT GDG is defined twice with different limits:** LIMIT(5) SCRATCH (DEFGDGB) and LIMIT(10) without SCRATCH (REPTFILE). LISTCAT shows 5. | DEFGDGB.jcl:36-40; REPTFILE.jcl:25-28; LISTCAT.txt:1535 |
| Q-09 | **TRANBKP deletes the TRANSACT AIX but never redefines or rebuilds it**, so after TRANBKP + COMBTRAN the AIX/PATH is missing until TRANIDX runs. | TRANBKP.jcl:40-67; TRANIDX.jcl:22-54 |
| Q-10 | **POSTTRAN opens the TRANSACT KSDS with OPEN OUTPUT** to add posted transactions. For a non-empty, non-REUSE KSDS this is normally rejected; it only works if the KSDS is empty (e.g. right after TRANBKP). Run-order dependency to confirm with the batch/posting scope. | CBTRN02C:34-38, 254-256; POSTTRAN.jcl:29 |
| Q-11 | **COTRN01C (view) issues READ … UPDATE on TRANSACT** with no REWRITE, so it holds a record lock for the task. This belongs to the signed-off CT01 scope; noted here only as a data-access observation. | COTRN01C:267-275 |
| Q-12 | **Double display.** CBACT03C and CBCUS01C DISPLAY each record both in the read paragraph and in the main loop (CBACT02C has the inner one commented out). | CBACT03C:78, 96; CBCUS01C:78, 96; CBACT02C:96 |
| Q-13 | **CBACT01C writes fabricated values:** CYC-DEBIT = 2525.00 when zero, and hard-coded array amounts. These look like test scaffolding; the derived files must not be treated as business data. | CBACT01C:236-237, 253-261 |
| Q-14 | **Statement work file truncates TRNX-PROC-TS.** OUTREC `279:279,50` copies only 50 bytes = ORIG-TS (26) + the first 24 bytes of PROC-TS; the last 2 microsecond digits are lost. | CREASTMT.JCL:54; COSTM01.CPY:34-35 |
| Q-15 | **COMMAREA card number is numeric** (`CDEMO-CARD-NUM PIC 9(16)`) while the file keys are `X(16)`. A non-numeric card number would be invalid there. | COCOM01Y:41; CVACT02Y:5 |
| Q-16 | **Security / PII stored in clear:** passwords (SEC-USR-PWD, every sample user = `PASSWORD`), CVV, SSN, government id. CSD defines `RLSACCESS(NO)` and no encryption; LISTCAT shows `DATA SET ENCRYPTION-----(NO)`. | CSUSR01Y:21; CVACT02Y:7; CVCUS01Y:17-18; DUSRSECJ.jcl:35-44; LISTCAT.txt:17 |
| Q-17 | **CUSTREC vs CVCUS01Y drift.** They are byte-identical, but the DOB field name differs (`CUST-DOB-YYYYMMDD` vs `CUST-DOB-YYYY-MM-DD`) and CUSTREC contains TAB characters. Decide which copybook is canonical. | CUSTREC.cpy:19; CVCUS01Y:19 |
| Q-18 | **Unused artifacts:** `UNUSED1Y` (copied nowhere); USRSEC ESDS/RRDS (no CSD FILE, no program); CICS files CARDDAT, CARDAIX and CUSTDAT are not covered by OPENFIL/CLOSEFIL. Confirm whether these should be migrated. | UNUSED1Y.cpy:1-7; ESDSRRDS.jcl:65-106; OPENFIL.jcl:26-30 |
| Q-19 | **Misspelt field names** `ACCT-EXPIRAION-DATE` and `CARD-EXPIRAION-DATE` are propagated into CVEXPORT and CBACT01C. Target schema naming should be decided once. | CVACT01Y:11; CVACT02Y:9; CVEXPORT:54, 98 |
| Q-20 | **Catalog vs sample-data drift.** LISTCAT REC-TOTAL is TCATBALF 100 (sample 50) and TRANSACT 311 (sample dailytran 300). LISTCAT is a later snapshot; the source of `DALYTRAN.PS.INIT` (TRANSACT initial load) is not in `app/data/ASCII`. | LISTCAT.txt:1376, 3598; TRANFILE.jcl:70 |
| Q-21 | **FICO score 001** appears in the sample customers (range 001–793). There is no domain validation at the file layer. | custdata.txt:1-50; CVCUS01Y:22 |
| Q-22 | **CSD RECORDFORMAT(V)** is declared for all files while every cluster is fixed-length (RECORDSIZE min = max). It is harmless in CICS but should be noted for re-platforming tools. | CARDDEMO.CSD:6; ACCTFILE.jcl:41 |

## 10. Dependencies on other scopes
- **Transaction Management (CT00/01/02)**, already signed off: TRANSACT browse/read/write and the max+1 Tran ID rule (BR-DM-06). See the [signed-off FR](../../migration/transaction-management/docs/TransactionManagement_functional_requirement.md).
- **Sign-on / menu / user management (CC00, CA00, CU00–CU03):** USRSEC record semantics and user-type routing (BR-DM-19/20).
- **Account management (CAVW/CAUP) and card management (CCLI/CCDL/CCUP):** ACCTDAT/CUSTDAT/CARDDAT read/update flows and the Y/N status edits (BR-DM-21).
- **Bill payment (CB00):** ACCTDAT update and TRANSACT write (COBIL00C).
- **Batch posting (POSTTRAN / CBTRN02C, CBTRN01C):** DALYTRAN validation and reject reasons, the TCATBAL/account update rules (BR-DM-09/10/11), DALYREJS layout, and the OPEN OUTPUT run-order issue (Q-10).
- **Interest calculation (INTCALC / CBACT04C):** DISCGRP lookup/fallback and the interest formula (BR-DM-12–16), Q-03/Q-04.
- **Reporting (CR00 / TRANREPT / CBTRN03C, PRTCATBL):** TRANTYPE/TRANCATG lookups (BR-DM-17), the DATEPARM layout, and the hard-coded SORT date window (TRANREPT.jcl:43-48).
- **Statements (CREASTMT / CBSTM03A/B):** TRXFL / COSTM01 / CUSTREC (BR-DM-27, Q-14).
- **Scheduler / job orchestration** (`app/scheduler`): order of TRANBKP → POSTTRAN → INTCALC → COMBTRAN → TRANIDX (Q-09, Q-10).
- **Optional modules** (Db2 transaction types, IMS/Db2/MQ authorizations, VSAM-MQ): shared files and copybooks (§7).

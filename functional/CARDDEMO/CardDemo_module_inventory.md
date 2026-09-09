# CardDemo — Module Inventory Analysis

Produced by `!mf_module_inventory_analysis`. Status: **DRAFT — awaiting STOP B (user picks the stream(s))**.

- **Module**: CARDDEMO — CICS online + JCL batch
- **SOURCE**: `choikh0423/aws-mainframe-modernization-carddemo` @ `main`
- Cites are `path:line` into that repo.

---

## 1. Entry points

### Online (CICS)
Every transaction id is defined in `app/csd/CARDDEMO.CSD`. Sign-on `CC00` (`COSGN00C`) is the only user-facing entry; everything else is reached through the two menus.

| Tran | Program | Reached from |
|---|---|---|
| CC00 | COSGN00C | 3270 sign-on (module entry) |
| CM00 | COMEN01C | COSGN00C after a non-admin sign-on (`COSGN00C.cbl`) |
| CA00 | COADM01C | COSGN00C after an admin sign-on |
| CAVW / CAUP | COACTVWC / COACTUPC | main menu options 1, 2 (`app/cpy/COMEN02Y.cpy`) |
| CCLI / CCDL / CCUP | COCRDLIC / COCRDSLC / COCRDUPC | main menu options 3, 4, 5 |
| CT00 / CT01 / CT02 | COTRN00C / COTRN01C / COTRN02C | main menu options 6, 7, 8 |
| CR00 | CORPT00C | main menu option 9 |
| CB00 | COBIL00C | main menu option 10 |
| CU00–CU03 | COUSR00C–COUSR03C | admin menu options 1–4 (`app/cpy/COADM02Y.cpy`) |
| CDV1 | (no program in `app/cbl`) | CSD-only definition; see §5 |

### Batch (JCL)
`app/jcl` holds 38 members; `app/scheduler/CardDemo.controlm` and `CardDemo.ca7` hold the production dependencies. Jobs that execute an application program:

| Job | Step → program | Function |
|---|---|---|
| `POSTTRAN.jcl` | `STEP15 EXEC PGM=CBTRN02C` | post the daily transaction file, write rejects |
| `INTCALC.jcl` | `STEP15 EXEC PGM=CBACT04C,PARM='2022071800'` | interest calculation |
| `TRANREPT.jcl` (+ `proc/TRANREPT.prc`, `proc/REPROC.prc`) | `STEP10R EXEC PGM=CBTRN03C` | transaction detail report |
| `CREASTMT.JCL` | `STEP040 EXEC PGM=CBSTM03A` | account statements (text + HTML) |
| `READACCT.jcl` / `READCARD.jcl` / `READXREF.jcl` / `READCUST.jcl` | `CBACT01C` / `CBACT02C` / `CBACT03C` / `CBCUS01C` | sequential print/extract of each master file |
| `CBEXPORT.jcl` / `CBIMPORT.jcl` | `CBEXPORT` / `CBIMPORT` | branch-migration export/import |
| `WAITSTEP.jcl` | `WAIT EXEC PGM=COBSWAIT` | scheduler wait step (calls assembler `MVSWAIT`) |
| `CBADMCDJ.jcl` | `DFHCSDUP` | CICS CSD definition load |

The remaining jobs (`ACCTFILE`, `CARDFILE`, `CUSTFILE`, `XREFFILE`, `TRANFILE`, `TRANIDX`, `TRANCATG`, `TRANTYPE`, `TCATBALF`, `DISCGRP`, `DALYREJS`, `REPTFILE`, `DEFCUST`, `DEFGDGB`, `DEFGDGD`, `ESDSRRDS`, `DUSRSECJ`, `COMBTRAN`, `TRANBKP`, `PRTCATBL`, `OPENFIL`, `CLOSEFIL`, `INTRDRJ1/2`, `FTPJCL`, `TXT2PDF1`) run only IBM utilities (`IDCAMS`, `SORT`, `IEBGENER`, `IEFBR14`, `SDSF`, `FTP`, `IKJEFT1B`) — dataset lifecycle, not application logic.

### Scheduler chains (`app/scheduler/CardDemo.controlm`)
- **DAILY-TransactionBackup**: `CLOSEFIL → TRANBKP → WAITSTEP → OPENFIL`
- **WEEKLY-DisclosureGroupsRefresh**: `CLOSEFIL → DISCGRP → WAITSTEP → OPENFIL` (gated on `MNTTRDB2`)
- **WEEKLY-TransactionTypesDBRefresh**: `MNTTRDB2 → TRANEXTR` (DB2 optional module)

`CLOSEFIL`/`OPENFIL` close and re-open the CICS FCT entries around batch — i.e. **the online and batch surfaces share the same VSAM files and must not run concurrently**. That is the single most important structural fact for this migration.

---

## 2. Stream catalog

### Online streams

| # | Stream | Trans | Programs | Primary data | Status |
|---|---|---|---|---|---|
| O1 | Transaction Management | CT00, CT01, CT02 | COTRN00C, COTRN01C, COTRN02C | TRANSACT; reads CCXREF, ACCTDAT | **already migrated** (`migration/transaction-management`, PRs #32–#36) |
| O2 | Auth / Sign-on + Menus | CC00, CM00, CA00 | COSGN00C, COMEN01C, COADM01C | USRSEC + the menu option tables | not started — **shell, everything routes through it** |
| O3 | Account Management | CAVW, CAUP | COACTVWC, COACTUPC | ACCTDAT, CUSTDAT, CCXREF | not started |
| O4 | Card Management | CCLI, CCDL, CCUP | COCRDLIC, COCRDSLC, COCRDUPC | CARDDAT, CCXREF | not started |
| O5 | User Management (security) | CU00–CU03 | COUSR00C, COUSR01C, COUSR02C, COUSR03C | USRSEC | not started |
| O6 | Bill Payment | CB00 | COBIL00C | ACCTDAT (rewrite), TRANSACT (write), CXACAIX | not started |
| O7 | Reporting (submits batch) | CR00 | CORPT00C | TRANSACT; writes the `JOBS` extra-partition TDQ → internal reader | not started |

### Batch streams

| # | Stream | Job(s) | Programs | Primary data | Status |
|---|---|---|---|---|---|
| B1 | Daily transaction posting | POSTTRAN | CBTRN02C (+ CBTRN01C, the unused validation-only twin) | DALYTRAN → TRANSACT, DALYREJS, ACCTDAT, TCATBALF, CCXREF | not started — **the core batch chain** |
| B2 | Interest calculation | INTCALC | CBACT04C | TCATBALF, DISCGRP, ACCTDAT, CCXREF → TRANSACT | not started |
| B3 | Transaction detail report | TRANREPT (+ REPROC/TRANREPT procs) | CBTRN03C | TRANSACT, CARDXREF, TRANTYPE, TRANCATG, DATEPARM → TRANREPT | not started |
| B4 | Account statements | CREASTMT | CBSTM03A → CBSTM03B | TRNXFILE, XREFFILE, CUSTFILE, ACCTFILE → STMTFILE + HTMLFILE | not started |
| B5 | Master-file readers/extracts | READACCT, READCARD, READXREF, READCUST | CBACT01C, CBACT02C, CBACT03C, CBCUS01C | ACCTDAT / CARDDAT / CCXREF / CUSTDAT → print + VB/array extract files | not started |
| B6 | Branch export / import | CBEXPORT, CBIMPORT | CBEXPORT, CBIMPORT | all five masters → EXPFILE → re-split + error file | not started |
| B7 | File lifecycle & scheduler plumbing | CLOSEFIL, OPENFIL, WAITSTEP, TRANBKP, DISCGRP, *FILE, DEFGDG*, … | COBSWAIT only (rest are IDCAMS/SORT/IEBGENER) | all VSAM clusters, GDG bases | not started — mostly **not portable program-for-program** |

### Optional (separately packaged) streams — out of scope unless STOP B says otherwise

| # | Stream | Programs | Blocker |
|---|---|---|---|
| X1 | Transaction Type maintenance (DB2) | COTRTLIC, COTRTUPC, COBTUPDT | needs DB2; reachable from admin menu options 5–6 |
| X2 | Pending Authorizations (IMS + DB2 + MQ) | COPAUS0C, COPAUS1C, COPAUS2C, COPAUA0C, CBPAUP0C, PAUDBLOD, PAUDBUNL, DBUNLDGS | needs IMS DB and MQ; `COMEN01C.cbl` references `COPAUS0C` |
| X3 | MQ inquiry | COACCT01, CODATE01 | needs MQ |

---

## 3. Coverage proof (program-level arithmetic)

**Core module, `app/cbl` = 31 source members.**

| Bucket | Count | Members |
|---|---|---|
| Assigned to an online stream | 17 | O1: COTRN00C, COTRN01C, COTRN02C · O2: COSGN00C, COMEN01C, COADM01C · O3: COACTVWC, COACTUPC · O4: COCRDLIC, COCRDSLC, COCRDUPC · O5: COUSR00C–COUSR03C · O6: COBIL00C · O7: CORPT00C |
| Assigned to a batch stream | 12 | B1: CBTRN02C, CBTRN01C · B2: CBACT04C · B3: CBTRN03C · B4: CBSTM03A, CBSTM03B · B5: CBACT01C, CBACT02C, CBACT03C, CBCUS01C · B6: CBEXPORT, CBIMPORT |
| Batch plumbing | 1 | COBSWAIT (`WAITSTEP.jcl`) |
| Shared (used by ≥2 streams) | 1 | CSUTLDTC — CALLed by `COTRN02C.cbl` (already ported as `DateValidationService`) and by `CORPT00C.cbl` |
| **Total** | **31** | = 17 + 12 + 1 + 1 ✅ |

**Assembler, `app/asm` = 2**: `MVSWAIT` (CALLed by COBSWAIT), `COBDATFT` (CALLed by `CBACT01C.cbl`). Both assigned to B7/B5 respectively as utilities to reimplement in Java.

**Optional modules = 15 programs** (X1: 3, X2: 8, X3: 2, plus `DBUNLDGS`/`PAUDBLOD`/`PAUDBUNL` counted in X2), all in the excluded set with a reason.

**Unreachable / no source**: `COCRDSEC` and transaction `CDV1` are defined in `app/csd/CARDDEMO.CSD` but have no member in `app/cbl` — dead CSD definitions, excluded.

**Route-level closure**: main menu = 11 options (`COMEN02Y.cpy:21`, `CDEMO-MENU-OPT-COUNT VALUE 11`) → options 1–10 map to O3, O4, O1, O7, O6 above; option 11 exits to `COSGN00C`. Admin menu = 6 options (`COADM02Y.cpy:22`, `CDEMO-ADMIN-OPT-COUNT VALUE 6`) → 1–4 map to O5, 5–6 to X1 (DB2). Every `MOVE '…' TO CDEMO-TO-PROGRAM` target in `app/cbl/CO*.cbl` resolves to a program in the table above.

---

## 4. Shared-program map (port once, first stream owns it)

| Asset | Used by | Owner proposal |
|---|---|---|
| `CSUTLDTC` (date validation) | COTRN02C (O1, ported), CORPT00C (O7) | already ported in O1 — O7 imports it |
| `COCOM01Y` COMMAREA | every online program | not ported; replaced by stateless requests (CORE) |
| `CVACT01Y` ACCTDAT record | O1, O3, O6, B1, B2, B4, B5, B6 | first migrating stream owns the `accounts` table |
| `CVACT03Y` CCXREF record | O1 (ported), O3, O4, O6, B1, B2, B3, B4, B6 | already owned by O1 (`card_xref`) |
| `CVACT02Y` CARDDAT record | O4, B5, B6 | O4 |
| `CVCUS01Y`/`CUSTREC` CUSTDAT | O3, B4, B5, B6 | O3 |
| `CVTRA05Y` TRANSACT record | O1 (ported), O6, O7, B1, B2, B3, B4, B6 | already owned by O1 (`transactions`) |
| `CVTRA01Y`/`CVTRA02Y`/`CVTRA03Y`/`CVTRA04Y` (tran type, category, disclosure group, category balance) | B1, B2, B3, X1 | B1 |
| `CSUSR01Y` USRSEC | O2, O5 | O2 |
| `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y/02Y`, `CSSETATY`, `CSSTRPFY`, `DFHAID`, `DFHBMSCA` | all online | presentation-layer constants; not ported |
| `MVSWAIT`, `COBDATFT` (assembler) | B7, B5 | reimplement as Java utilities |

---

## 5. First-pass boundary register (feeds `!mf_boundary_resolution`)

| ID | Class | Crossing | Notes |
|---|---|---|---|
| BR-01 | B2 | `CSUTLDTC` shared by O1 and O7 | already ported; O7 imports rather than re-ports |
| BR-02 | B5 | Sign-on/menu `XCTL` into every functional program | O2 owns the routing; each stream exposes an entry route |
| BR-03 | B8 | `CORPT00C` writes the `JOBS` extra-partition TDQ → internal reader → submits `TRANREPT` (`CORPT00C.cbl:507,517`) | online action triggering a batch job — needs a decided launch seam |
| BR-04 | B7 | `POSTTRAN` consumes DALYTRAN and produces DALYREJS for downstream handling | dataset hand-off |
| BR-05 | B7 | `CREASTMT` chains `SORT → IDCAMS REPRO → CBSTM03A` on `AWS.M2.CARDDEMO.TRXFL.*` | intermediate datasets owned by the job, not the program |
| BR-06 | B9 | Control-M/CA7 chains incl. `CLOSEFIL`/`OPENFIL` bracketing (`app/scheduler/`) | online must be quiesced around batch — disappears once both sides share one DB, but the decision must be recorded |
| BR-07 | B10 | ACCTDAT/TRANSACT written by both online (O6 bill pay) and batch (B1, B2) | data contract shared across surfaces; ownership must be fixed before either side migrates |
| BR-08 | B3 | Admin menu options 5–6 → `COTRTLIC`/`COTRTUPC` (DB2 module) | foreign module; deferral or exposure decision |
| BR-09 | B11 | `COMEN01C` references `COPAUS0C` (IMS/MQ authorizations) | external system, infra not provisioned |
| BR-10 | B4 | VSAM KSDS + alternate indexes (`CXACAIX`, `CARDAIX`, TRANSACT AIX) | physical data layer → tables + indexes |
| BR-11 | B6 | `TXT2PDF1`, `FTPJCL`, internal-reader jobs | outbound file/format hand-offs, likely documented deferrals |

---

## 6. Recommended order (recommendation only — the user chooses at STOP B)

1. **O2 Auth/Sign-on + Menus** — nothing else has a home until the shell exists; owns `USRSEC` and the routing table.
2. **O3 Account Management** — owns ACCTDAT + CUSTDAT, the tables most other streams read.
3. **O4 Card Management** — owns CARDDAT; completes the master data set.
4. **B1 Daily posting** — the core batch chain; needs ACCTDAT, TRANSACT, CCXREF, TCATBALF to exist.
5. **B2 Interest calculation** — depends on the same tables plus DISCGRP.
6. **O6 Bill Payment** — writes ACCTDAT + TRANSACT; safest once both are owned.
7. **B3 Transaction report** and **B4 Statements** — read-only reporting on top of everything above.
8. **O5 User Management** — independent of the data masters, can slot in anywhere after O2.
9. **O7 Reporting** — last, because BR-03 (online → batch submit) needs B3 to exist.
10. **B5 extracts** and **B6 export/import** — thin readers over already-migrated tables.
11. **B7 file lifecycle** — resolved as documentation plus whatever remains after the DB replaces VSAM.

Excluded until explicitly requested: X1 (DB2), X2 (IMS/MQ), X3 (MQ), and the dead `COCRDSEC`/`CDV1` definitions.

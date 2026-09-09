# CardDemo — Independent Migration Audit

**Auditor:** independent reviewer; performed none of the migration work and changed no
application, COBOL, schema, test or frontend code. The only artefact added by this audit is
this document.

**Under audit:** branch `devin/carddemo-integration` at commit `d8b64a7` (foundation PR #40
plus stream PRs #41–#56).

**Oracle:** the legacy estate still present in `app/**` — COBOL, JCL, PROC, BMS, CSD, copybooks,
DDL, DCLGEN, IMS DBD/PSB and the scheduler definitions.

**Contract documents audited against:** `docs/migration/CardDemo_target_state.md`,
`docs/migration/CardDemo_inventory.md`, `.migration/04_boundary_register.md`,
`.migration/05_progress.md`, `.migration/06_decisions.md`,
`docs/migration/streams/<Stream>/**` and `migration/carddemo/README.md`.

---

## 1. Overall recommendation

**`devin/carddemo-integration` is NOT yet fit to merge into `main`.**

The migration is, on the whole, of unusually high fidelity. Money arithmetic in the three
streams that carry the most financial risk — S-11 daily posting, S-12 interest calculation and
S-06 bill payment — reproduces the COBOL faithfully, including its rounding mode, its signed
cycle-credit/debit split, its max-key+1 id generation and even its bugs. Screen and error text
is preserved character-for-character, double spaces included. Lane discipline is clean.

Merge is blocked on **two defects in the S-09 `AUTHFRDS` schema** that silently lose data and
that no document acknowledges. Both are narrow and localised; neither requires redesign. Once
they are corrected and the boundary register is brought in line with reality, the branch should
merge.

| | Count |
|---|---:|
| BLOCKER | 2 |
| MAJOR | 5 |
| MINOR | 3 |
| **Total findings** | **10** |

---

## 2. Verified build and test numbers

These numbers were obtained by the auditor on this machine, not taken from `.migration/05_progress.md`.

### Backend

```
$ cd migration/carddemo/backend
$ /home/ubuntu/apache-maven-3.9.9/bin/mvn -B test

Tests run: 916, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Maven was not preinstalled; Apache Maven 3.9.9 was installed under `/home/ubuntu` and the GCS
mirror `https://maven-central.storage-download.googleapis.com/maven2` was configured in
`~/.m2/settings.xml`. No repository file was modified to make the build run.

**The ledger's claim of 916 passing tests is confirmed.** 153 test classes; zero skips, zero
disabled tests, zero ignored assertions.

### Frontend

```
$ source ~/.nvm/nvm.sh && nvm use 20        # Node v20.20.2, npm 10.8.2
$ cd migration/carddemo/frontend
$ npm ci
$ CI=true npm run build

Compiled successfully.
exit 0
```

`npm ci` printed an advisory audit notice; the build itself is clean and warning-free under
`CI=true` (which promotes warnings to errors).

**Caveat, recorded as finding A-07:** neither the local run nor `.github/workflows/carddemo-ci.yml`
executes any frontend test. The CI workflow runs `npm ci --no-audit --no-fund` and
`CI=true npm run build` only. The 916 figure is a backend-only number.

---

## 3. Verdict per stream

| Stream | Verdict | Findings |
|---|---|---|
| S-01 AuthAndShell | PASS | — |
| S-02 AccountManagement | PASS | — |
| S-03 CardManagement | PASS | — (B-11 / `COCRDSEC` correctly documented as absent source) |
| S-04 TransactionManagement | PASS WITH FINDINGS | A-05 |
| S-05 UserManagement | PASS | — |
| S-06 BillPayment | PASS | — |
| S-07 Reporting | PASS | — |
| S-08 TransactionTypeManagement | PASS | — |
| S-09 PendingAuthorizations | **FAIL** | A-01, A-02, A-03, A-08 |
| S-10 MQInquiry | PASS | — |
| S-11 DailyTransactionPosting | PASS | — |
| S-12 InterestCalculation | PASS | — |
| S-13 StatementGeneration | PASS WITH FINDINGS | A-06 |
| S-14 TransactionReporting | PASS WITH FINDINGS | A-10 |
| S-15 FileReadUtilities | PASS | — |
| S-16 DataExportImport | PASS WITH FINDINGS | A-09 |
| S-17 OperationsChain | PASS WITH FINDINGS | A-04 |
| S-18 DataLoadAndSetup | PASS | — |
| S-19 TransactionTypeDB2Refresh | PASS | — |
| S-20 PendingAuthPurgeAndIMSLoad | PASS | — |

---

## 4. Findings

Severity definitions used here: **BLOCKER** — silently wrong or lost production data, or a
correctness defect that a user cannot detect; must be fixed before merge. **MAJOR** — a real
contract deviation or a traceability gap that prevents a reviewer from confirming coverage.
**MINOR** — cosmetic, operational, or documentation-only.

### Findings table

| ID | Sev | Stream | COBOL / legacy oracle | Java / migrated | What is wrong | What correct looks like |
|---|---|---|---|---|---|---|
| A-01 | BLOCKER | S-09 | `app/app-authorization-ims-db2-mq/ddl/AUTHFRDS.ddl:12-13` — `TRANSACTION_AMT DECIMAL(12,2)`, `APPROVED_AMT DECIMAL(12,2)`; DCLGEN `app/app-authorization-ims-db2-mq/dcl/AUTHFRDS.dcl:66` — `PIC S9(10)V9(2) COMP-3` | `migration/carddemo/backend/src/main/resources/db/migration/V1__baseline_carddemo_estate.sql:195-196` — `transaction_amt NUMERIC(11,2)`, `approved_amt NUMERIC(11,2)`; written at `.../pendingauth/service/AuthFraudService.java:98-99` | The fraud table loses one integer digit against its own DDL. `AuthFraudService` copies straight from `pending_auth_detail.pa_transaction_amt NUMERIC(12,2)` (correctly derived from `CIPAUDTY.cpy` `PIC S9(10)V99 COMP-3`) into an 11-digit column. Any authorization of 1,000,000,000.00 or more that the source segment can legally hold overflows on insert. The narrowing is silent — no document, decision or stream plan mentions it. | `transaction_amt NUMERIC(12,2)` and `approved_amt NUMERIC(12,2)`, matching `DECIMAL(12,2)` in the DDL and the 12-digit `S9(10)V9(2)` host variables, so the fraud table can hold every value the pending-auth detail segment can. |
| A-02 | BLOCKER | S-09 | `app/app-authorization-ims-db2-mq/cbl/COPAUS2C.cbl:194` (INSERT) and `:225` (UPDATE) — the column is set by the server to `CURRENT DATE`; `AUTHFRDS.ddl:25` — `FRAUD_RPT_DATE DATE`; DCLGEN `AUTHFRDS.dcl:84` — `PIC X(10)` | `V1__baseline_carddemo_estate.sql:208` — `fraud_rpt_date CHAR(8)`; `AuthFraudService.java:33,76,83,111` — `DateTimeFormatter.ofPattern("MM/dd/yy")`, `LocalDate.now(clock).format(RPT_DATE)` | The DB2 `DATE` column has been migrated as `CHAR(8)` holding `MM/dd/yy`. This is a two-digit year: the century is destroyed at write time and cannot be recovered. The column can no longer be range-queried, sorted or compared as a date, and an `ORDER BY fraud_rpt_date` now sorts by month. The migration has conflated two distinct legacy fields: the DB2 column `AUTHFRDS.FRAUD_RPT_DATE` (a real `DATE`, 10-char host variable) and the *IMS detail segment* field `PA-FRAUD-RPT-DATE PIC X(08)` (`app/app-authorization-ims-db2-mq/cpy/CIPAUDTY.cpy:53`), which is a separate `MMDDYY` screen field set at `COPAUS2C.cbl:101`. The latter is already modelled correctly and separately as `pa_fraud_rpt_date VARCHAR(8)` at `V1__baseline_carddemo_estate.sql:275`. The stream's own analysis records the correct contract — `docs/migration/streams/PendingAuthorizations/PendingAuthorizations_analysis.md:214` and `programs/COPAUS2C_functional_requirement.md:33` both state `FRAUD_RPT_DATE ← CURRENT DATE` — so the implementation contradicts its own FR. | `fraud_rpt_date DATE`, and `AuthFraudRecord.fraudRptDate` typed `LocalDate`, assigned `LocalDate.now(clock)` (the server date, as `CURRENT DATE` is server-evaluated). The `MM/dd/yy` string, if it is needed for the COPAUS2C screen, belongs only on the IMS-derived `pa_fraud_rpt_date` field, which already exists. |
| A-03 | MAJOR | S-09 | `AUTHFRDS.ddl:16` — `POS_ENTRY_MODE SMALLINT`; DCLGEN `AUTHFRDS.dcl:71` — `PIC S9(4) COMP` | `V1__baseline_carddemo_estate.sql:199` — `pos_entry_mode CHAR(2)`; `AuthFraudService.java:102` and its `posEntryMode(...)` helper — `String.format("%02d", ...)` | A numeric DB2 column has become a character column, changing comparison and ordering semantics and narrowing the domain from the `SMALLINT` range to two characters. Mitigating: the only writer today sources the value from `pa_pos_entry_mode NUMERIC(2)` (`CIPAUDTY.cpy:38`, `PIC 9(02)`), so no value currently overflows — but any pre-existing DB2 row, or any future writer honouring the DDL, cannot be represented, and `String.format("%02d", …)` would silently widen past the column for a three-digit value. | `pos_entry_mode SMALLINT` with an `Integer`-typed field, matching the DDL and the `S9(4) COMP` host variable. Formatting to two digits, if the screen needs it, belongs in the presentation layer. |
| A-04 | MAJOR | S-17 / cross-cutting | `app/jcl`, `app/proc`, `app/app-transaction-type-db2/jcl`, `app/app-authorization-ims-db2-mq/jcl` — 119 raw `EXEC PGM=` occurrences (98 / 3 / 11 / 7) | 21 Spring Batch jobs and 29 `new StepBuilder(...)` definitions under `migration/carddemo/backend/src/main/java/com/carddemo/batch` | `docs/migration/CardDemo_inventory.md:25` publishes "`EXEC PGM=` steps \| 101" as a bare figure with no derivation anywhere in the repository, while `migration/carddemo/README.md:155` states the binding rule "one `@Bean` `Step` per `EXEC PGM=`". Neither 119 nor 101 can be reconciled against the 29 steps that exist. The gap is almost certainly legitimate — 107 of the 119 invoke MVS utilities (IDCAMS ×62, IEFBR14 ×9, SDSF ×8, IEBGENER ×8, IKJEFT01 ×7, SORT ×5, DFSRRC00 ×5, FTP, DFHCSDUP, IKJEFT1B), most of which correctly vanish against a shared database — but *no document says so*, so a reviewer cannot distinguish a deliberate elision from an omission. This is the single largest hole in the coverage evidence. | A published step-reconciliation table in the inventory: 119 raw occurrences → *n* utility steps that disappear in the target (by utility, with the reason) → *m* logical business steps → the 29 implemented Spring Batch steps, with every difference named. Either the arithmetic closes or the residual is itemised as not-migrated with a reason. |
| A-05 | MAJOR | S-04 | `app/cbl/COTRN00C.cbl`, `app/cbl/COTRN01C.cbl`, `app/cbl/COTRN02C.cbl` | `migration/carddemo/backend/src/main/java/com/carddemo/transaction/**` (24 classes, 11 test classes) | S-04 TransactionManagement is a named stream in `docs/migration/CardDemo_inventory.md`, but `docs/migration/streams/` contains only 16 directories and **TransactionManagement is not one of them**. There is no analysis, no stream FR, no migration plan and no `programs/COTRN0*C_functional_requirement.md` for any of its three programs — the only three migrated COBOL programs in the estate with no functional-requirement document at all. The code is present and tested, so this is a traceability defect rather than a coverage defect: there is nothing to audit the implementation against, and the "46 program FR docs" figure conceals it (46 documents exist, but 7 of them describe JCL jobs — `CLOSEFIL`, `COMBTRAN`, `CREADB21`, `OPENFIL`, `PRTCATBL`, `TRANEXTR`, `WAITSTEP` — rather than COBOL programs). | Either a `docs/migration/streams/TransactionManagement/` folder with the same four artefacts every other stream has, or an explicit written statement in the inventory that S-04 was delivered as the frozen v1 reference app whose requirements live elsewhere, naming where. |
| A-06 | MAJOR | governance / S-13 | `.migration/04_boundary_register.md:12-26` — B-01 through B-15 | `.migration/05_progress.md` | Every one of the 15 boundary rows still reads status `REGISTERED` with an empty `Decided on` and an empty `Decision` column, against a register whose own header defines the lifecycle `REGISTERED → DECIDED → IMPLEMENTED \| DEFERRED`. `.migration/05_progress.md` asserts that all 15 boundaries are decided and implemented-or-deferred. One of these two documents is false. On the evidence the *code* is right — B-03 is implemented with an explicit contract in `com.carddemo.batch.filereads.LegacyDateFormatter`, B-05/B-06 in `com.carddemo.mqinquiry.jms`, B-08/B-09 as JPA entities, B-13 deferred with a written rationale in the StatementGeneration plan — so this is a documentation failure, not an unhandled boundary. But as it stands the register, which is the artefact a reviewer is told to check, records that nothing was ever decided. | Each B-01…B-15 row appended with `IMPLEMENTED` or `DEFERRED`, a date, and a one-line decision citing the implementing class or the deferral rationale — in particular B-13 (TXT2PDF/IKJEFT1B rendering, deferred: the TSO/REXX utility is not in the repo and `TXT2PDF1` remains in the scheduler chain) which is currently invisible in the register. |
| A-07 | MAJOR | frontend / cross-cutting | 17 core + 4 add-on BMS maps; 21 screens registered in `migration/carddemo/frontend/src/routes/registry.js` | `migration/carddemo/frontend/src/pages/user/selection.test.js` — the only test file in the frontend | One test file covers 21 migrated screens, and `.github/workflows/carddemo-ci.yml` never invokes `npm test`, so even that one test does not run in CI. Every screen-level behaviour the BMS maps define — field-level validation messages, PF-key navigation, pagination and the selection-flag semantics on the list screens — is unguarded by any automated check. The headline "916 tests" is a backend-only figure and is presented in the ledger without that qualification. | A `npm test -- --watchAll=false` step in the CI workflow, and at minimum one test per list screen covering pagination plus selection-flag handling and one per maintenance screen covering the validation-message set, so the exact-text fidelity the backend guards is guarded on the screen too. |
| A-08 | MINOR | S-09 | `AUTHFRDS.ddl:18` — `MERCHANT_NAME VARCHAR(22)`; DCLGEN `AUTHFRDS.dcl` — a length-prefixed `49 MERCHANT-NAME-LEN` / `49 MERCHANT-NAME-TEXT` group | `V1__baseline_carddemo_estate.sql:201` — `merchant_name CHAR(22)`; written at `AuthFraudService.java:104` | A varying-length DB2 column has become fixed-width `CHAR(22)`, so values now read back space-padded to 22 characters where DB2 returned the exact stored length. This changes equality comparison and any rendering of the value. Note the same logical column is `VARCHAR(50)` in the VSAM-derived `transactions` / `daily_transactions` tables (lines 88, 107), so the estate is internally inconsistent here too. | `merchant_name VARCHAR(22)`, matching the DDL. |
| A-09 | MINOR | S-16 | `app/cbl/CBEXPORT.cbl`, `app/cbl/CBIMPORT.cbl` — operator `DISPLAY` text including `'CBEXPORT: Processing customer records'`, `'CBEXPORT: Customers exported: '`, `'ERROR: Cannot open CUSTOMER-INPUT, Status: '`, `'ERROR: Reading CUSTOMER-INPUT, Status: '`, `'ERROR: Cannot open CUSTOMER-OUTPUT, Status: '`, `'ERROR: Writing customer record, Status: '` | `migration/carddemo/backend/src/main/java/com/carddemo/batch/**` — none of these strings appears anywhere in the Java source | The operator-facing SYSOUT contract of the export/import jobs is not reproduced and is not documented as dropped. Unlike S-14, where the stream plan explicitly records that `CBTRN03C`'s per-record `DISPLAY` is replaced by job/step logging, the DataExportImport plan makes no equivalent statement. Anyone running these jobs and grepping SYSOUT for the legacy strings — including the record counts, which are a real operational control — finds nothing. | Either emit the equivalent counts and file-status messages through the job logger at the same points, or state in the DataExportImport migration plan which legacy SYSOUT lines were deliberately dropped and what replaces the record-count control. |
| A-10 | MINOR | S-14 | `app/jcl/PRTCATBL.jcl` — a `SORT OUTREC` whose reformatted record does not agree with the declared `LRECL=40` | `migration/carddemo/backend/src/main/java/com/carddemo/batch/tranreport/**` | The TransactionReporting migration plan notes the `OUTREC`/`LRECL=40` contradiction in the legacy JCL but resolves it silently in the implementation and never raises it as a data-fidelity risk in the boundary register or the decisions log. A latent legacy defect that the migration has to pick a side on is exactly the kind of thing that belongs in `.migration/06_decisions.md`. | A decision entry (D-*n*) recording which interpretation of the `PRTCATBL` record layout the migration adopted and why, so the choice is reviewable rather than buried in a plan paragraph. |

---

## 5. Coverage reconciliation

### 5.1 Programs — arithmetic shown

Direct count of COBOL source on the branch:

```
app/cbl                                31
app/app-transaction-type-db2/cbl        3
app/app-authorization-ims-db2-mq/cbl    8
app/app-vsam-mq/cbl                     2
                                       --
TOTAL                                  44
```

This confirms the inventory's headline figure of 44 exactly. The inventory's own derivation

```
3+2+3+3+4+1+1+3+8+2+1+1+2+1+4+2+1 = 42
42 + 1 shared CSUTLDTC + 1 unreachable CBTRN01C = 44
```

also closes at 44 and is consistent with the file count.

**Program-to-FR reconciliation.** 46 functional-requirement documents exist under
`docs/migration/streams/*/programs/`. They do not map one-to-one onto the 44 programs:

- **5 programs have no FR document:** `CBTRN01C` (accepted gap — unreachable), `CSUTLDTC`
  (shared utility, correctly covered instead as boundary B-02 and documented inside the
  Reporting stream FR, analysis and plan), and `COTRN00C` / `COTRN01C` / `COTRN02C` — the S-04
  programs, reported as **A-05**.
- **7 FR documents describe JCL jobs or PROC steps rather than COBOL programs:** `CLOSEFIL`,
  `COMBTRAN`, `CREADB21`, `OPENFIL`, `PRTCATBL`, `TRANEXTR`, `WAITSTEP`. This is legitimate —
  those are units of work in the estate — but it means "46 FR docs for 44 programs" reads as
  over-coverage when it in fact conceals the three-program gap above.

So: `44 programs − 2 accepted gaps − 1 correctly-covered-elsewhere (CSUTLDTC) = 41 expected FR
docs; 39 present; 3 missing (S-04)`.

**Accepted gaps — both correctly documented.** Confirmed, not counted as findings:

- **`COCRDSEC`** — named by `DEFINE TRANSACTION CDV1` in `app/csd/CARDDEMO.CSD:388`, no source
  anywhere in the repository. Recorded as boundary B-11 and as inventory risk R-1, and the
  inventory footnote explains the resulting 24-vs-25 transaction discrepancy explicitly. This
  is documented honestly rather than papered over.
- **`CBTRN01C`** — unreachable; no JCL, PROC, scheduler entry or COBOL `CALL` references it.
  Confirmed independently: it appears in no `EXEC PGM=` and in no `CALL` in the estate. Recorded
  in the inventory arithmetic as the 44th program.

**Nothing else is silently missing at program level.** Every other one of the 44 programs is
either implemented in `com.carddemo.*` / `com.carddemo.batch.*` or documented as not-migrated
with a stated reason.

### 5.2 JCL steps

```
app/jcl                                 98
app/proc                                 3
app/app-transaction-type-db2/jcl        11
app/app-authorization-ims-db2-mq/jcl     7
                                       ---
raw EXEC PGM= occurrences              119

inventory claim                        101   (no derivation published)
implemented Spring Batch steps          29
implemented Spring Batch jobs           21   (against 46 JCL jobs)
```

By invoked program, the 119 break down as: IDCAMS 62, IEFBR14 9, SDSF 8, IEBGENER 8, IKJEFT01 7,
SORT 5, DFSRRC00 5, CBTRN03C 2, and one each of IKJEFT1B, FTP, DFHCSDUP, COBSWAIT, CBTRN02C,
CBSTM03A, CBIMPORT, CBEXPORT, CBCUS01C, CBACT04C, CBACT03C, CBACT02C, CBACT01C. Only 12 distinct
COBOL programs are named directly on an `EXEC PGM=`; the DB2 and IMS programs are invoked through
`IKJEFT01` and `DFSRRC00` respectively.

Most of the 107 utility invocations legitimately disappear against a shared relational target —
IDCAMS VSAM define/delete, IEFBR14 allocation, SDSF-driven CICS file quiescing (boundary B-15,
explicitly "disappears in the target"). The 26 JCL files with no standalone Spring Batch job
(`ACCTFILE`, `CARDFILE`, `CBADMCDJ`, `CLOSEFIL`, `CUSTFILE`, `DALYREJS`, `DEFCUST`, `DEFGDGB`,
`DEFGDGD`, `DISCGRP`, `DUSRSECJ`, `ESDSRRDS`, `FTPJCL`, `INTRDRJ1`, `INTRDRJ2`, `OPENFIL`,
`REPTFILE`, `TCATBALF`, `TRANBKP`, `TRANCATG`, `TRANFILE`, `TRANIDX`, `TRANTYPE`, `TXT2PDF1`,
`XREFFILE`, `DBPAUTP0`) are all file-management, GDG, scheduler-handoff, boundary or
dataset-definition jobs of exactly this kind.

**But the arithmetic is not published anywhere and therefore cannot be checked.** That is
finding **A-04**. It is the one place where the audit cannot positively confirm coverage; it can
only observe that the residual is plausible.

### 5.3 BMS maps, CSD transactions, copybooks

- **BMS maps** — 17 core + 4 add-on. All 21 have a corresponding entry in
  `migration/carddemo/frontend/src/routes/registry.js`, each with a concrete `element`; the
  `PlaceholderPage` infrastructure exists but is not referenced by any current registry entry.
  No map is unaccounted for. (Their *test* coverage is finding A-07.)
- **CSD transactions** — 24 in the inventory against 25 `DEFINE TRANSACTION` statements; the
  single difference is `CDV1`/`COCRDSEC`, footnoted in the inventory and registered as B-11.
  Reconciles.
- **Copybooks** — 30 data/interface + 17 BMS symbolic + 9 add-on. Field-level reconciliation
  against the Flyway baseline is in §7. Four field-level deviations were found, all in the
  `AUTHFRDS` table: A-01, A-02, A-03, A-08. No dropped field was found in any VSAM-derived or
  IMS-derived table.

### 5.4 Boundaries B-01…B-15

| ID | Boundary | Handled in code? | Register status |
|---|---|---|---|
| B-01 | `CEE3ABD` abend service | Yes — `AbendService`, non-zero exit + logged reason | `REGISTERED` |
| B-02 | `CSUTLDTC` date validation | Yes — `common/service/DateValidationService` | `REGISTERED` |
| B-03 | `COBDATFT` assembler date format | Yes — `batch/filereads/LegacyDateFormatter` | `REGISTERED` |
| B-04 | `MVSWAIT` timer | Yes — `WAITSTEP` job, `WAIT` step | `REGISTERED` |
| B-05 | MQ inquiry request/response | Yes — `mqinquiry/jms/*` (disabled unless enabled) | `REGISTERED` |
| B-06 | MQ authorization request/response | Yes — same JMS seam | `REGISTERED` |
| B-07 | IMS DL/I calls | Yes — flattened to relational, status codes typed | `REGISTERED` |
| B-08 | DB2 transaction type/category | Yes — `db2_transaction_type*` tables + JPA | `REGISTERED` |
| B-09 | DB2 `AUTHFRDS` | Yes, but **defectively** — A-01, A-02, A-03, A-08 | `REGISTERED` |
| B-10 | Dynamic CICS LINK to fraud program | Yes — `AuthFraudService` | `REGISTERED` |
| B-11 | Missing `COCRDSEC` / CDV1 | Accepted gap, documented | `REGISTERED` |
| B-12 | `CBEXPORT`/`CBIMPORT` + FTP hand-off | Yes, with A-09 on the SYSOUT contract | `REGISTERED` |
| B-13 | TXT2PDF / IKJEFT1B rendering | **Deferred** with written rationale in the S-13 plan | `REGISTERED` |
| B-14 | Scheduler dependency graph | Deferred — exit codes only, graph documented in `OperationsChainSchedule` | `REGISTERED` |
| B-15 | OPENFIL/CLOSEFIL quiescing | Removed by design (shared database) | `REGISTERED` |

**No boundary is silently unhandled.** All 15 are addressed in code or deferred with a rationale
somewhere in the stream plans. However all 15 register rows are still `REGISTERED` with empty
decision fields, contradicting `.migration/05_progress.md` — finding **A-06**.

---

## 6. Fidelity — what was checked and what it showed

Every claim below was checked by reading the COBOL and the Java side by side. The findings above
are what did *not* reconcile; this section records what did, because an audit that reports only
defects is not evidence of scope.

### S-11 Daily Transaction Posting — PASS

`app/cbl/CBTRN02C.cbl` opens six files in a fixed order (`0000-DALYTRAN`, `0100-TRANFILE`,
`0200-XREFFILE`, `0300-DALYREJS`, `0400-ACCTFILE`, `0500-TCATBALF`) and the migrated job
reproduces it. The four rejection reasons and their exact description text — `100 INVALID CARD
NUMBER FOUND`, `101 ACCOUNT RECORD NOT FOUND`, `102 OVERLIMIT TRANSACTION`, `103 TRANSACTION
RECEIVED AFTER ACCT EXPIRATION` — are reproduced verbatim in `TransactionValidationService`.

Two things I specifically tried to break and could not:

- The COBOL overlimit test is `COMPUTE WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT − ACCT-CURR-CYC-DEBIT
  + DALYTRAN-AMT`, and the expiration check that follows *overwrites* reason 102 with 103 when
  both fail. The Java deliberately preserves that overwrite rather than short-circuiting on the
  first failure. This is a faithful port of the COBOL control flow, and it is commented as such.
- The signed split `IF DALYTRAN-AMT >= 0 ADD … TO ACCT-CURR-CYC-CREDIT ELSE ADD … TO
  ACCT-CURR-CYC-DEBIT` — including the fact that a *negative* amount is added (not subtracted)
  to the debit accumulator, which is what makes the overlimit expression above work — is
  reproduced exactly.

Posting order (`2700-UPDATE-TCATBAL` → `2800-UPDATE-ACCOUNT-REC` → `2900-WRITE-TRANSACTION-FILE`)
is preserved, duplicate transaction ids route to `AbendService`, and `TransactionMasterInitializer`
correctly models `OPEN OUTPUT TRANSACT-FILE` by clearing the table once per job instance rather
than once per chunk.

### S-12 Interest Calculation — PASS

Interest is `COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200` with no `ROUNDED`
clause, i.e. COBOL truncation. `InterestCalculator` uses
`balance.multiply(rate).divide(MONTHS_TIMES_PERCENT, 2, RoundingMode.DOWN)`. `RoundingMode.DOWN`
is truncation toward zero, which is exactly what an unrounded COBOL `COMPUTE` does for both signs
— including negative balances, which I checked specifically because `FLOOR` and `DOWN` diverge
there. Correct.

The transaction id is `PARM-DATE` concatenated with a run-scoped incrementing suffix; the Java
`TranIdSequence` persists the suffix through the Spring Batch `ExecutionContext` so it survives
chunk boundaries and restarts. Correct.

**The one thing that looked like a bug and is not.** `InterestCalculator.java:154` returns `null`
from `updatedAccount(...)` when `group.lastGroupInRun()` — i.e. it deliberately does *not* write
back the accumulated interest for the final account of the run. The comment at lines 145-150
claims the COBOL `ELSE PERFORM 1050-UPDATE-ACCOUNT` is unreachable. I checked the claim rather
than accepting it, because the COBOL plainly contains that `ELSE`:

```cobol
PERFORM UNTIL END-OF-FILE = 'Y'          *> CBACT04C.cbl:188
    IF  END-OF-FILE = 'N'                *> :189   outer IF
        PERFORM 1000-TCATBALF-GET-NEXT
        IF  END-OF-FILE = 'N'            *> :191   inner IF
            ... accumulate ...
        END-IF                           *> :218
    ELSE                                 *> :219   ELSE of the OUTER IF
        PERFORM 1050-UPDATE-ACCOUNT      *> :220
    END-IF
END-PERFORM
```

The `ELSE` at :219 binds to the outer `IF` at :189, and the loop only enters its body while
`END-OF-FILE` is not `'Y'` — so on entry the outer `IF` is always true and the `ELSE` can never
execute. When `1000-TCATBALF-GET-NEXT` reaches end of file it sets the flag, the inner `IF`
fails, and the loop exits without ever performing `1050-UPDATE-ACCOUNT`. **The last account of
every run genuinely never has its interest posted to `ACCT-CURR-BAL`, and its cycle credit/debit
are never reset.** That is a real defect in the legacy program, and the migration reproduces it
faithfully and documents it as FR-I16. This is the correct call for a like-for-like migration.
No finding.

Also confirmed faithful: `1400-COMPUTE-FEES` is an empty `EXIT` paragraph in the COBOL
("To be implemented") and is not invented in the Java.

### S-06 Bill Payment — PASS

`app/cbl/COBIL00C.cbl` message text is preserved character-for-character in
`BillPaymentMessages`, including the ellipses: `Acct ID can NOT be empty...`, `Invalid value.
Valid values are (Y/N)...`, `Account ID NOT found...`, `You have nothing to pay...`, `Confirm to
make a bill payment...`, `Tran ID already exist...`.

The success message is built by a COBOL `STRING` of `'Payment successful. '` followed by
`' Your Transaction ID is '` — two adjacent literals that each carry a space, producing a **double
space**. `BillPaymentFormat` reproduces `"Payment successful.  Your Transaction ID is "` with both
spaces. This is the level of care the whole migration shows on message text, and it is the
strongest single signal of quality in the branch.

Confirmation handling matches the COBOL `EVALUATE`, including the non-obvious case that `SPACES`
and `LOW-VALUES` fall through to `READ-ACCTDAT-FILE` rather than erroring, and that lowercase
`y`/`n` are accepted. Max-key+1 id generation (`MOVE HIGH-VALUES` → `STARTBR` → `READPREV` →
`ADD 1`) is modelled as `findTopByOrderByIdDesc()` + 1 with a 16-digit zero pad. The posted
transaction's fixed fields (`02`, cat `2`, `POS TERM`, `BILL PAYMENT - ONLINE`, merchant
`999999999` / `BILL PAYMENT` / `N/A` / `N/A`) all match, as does
`ACCT-CURR-BAL = ACCT-CURR-BAL − TRAN-AMT`.

### S-08 Transaction Type Management — PASS

Message text in `TranTypeMessages` matches `COTRTUPC.cbl` for the substantive set (`No record
found for this key in database`, `No input received`, `Update of record failed`, `Record changed
by some one else. Please review`, `Could not lock record for update`, `No change detected with
respect to values fetched.`, `Name can only contain alphabets and spaces`). Optimistic-locking
semantics — re-read and compare before update, distinct messages for "changed by someone else"
versus "could not lock" — are preserved.

I examined the differences in trailing space and casing (`'PF03 pressed.Exiting              '`
and `'Invalid Key pressed. '` in the COBOL versus `"PF03 pressed.Exiting"` and `"Invalid key
pressed"` in the Java) and am **not** raising them as findings: the COBOL padding is an artefact
of `MOVE` into a fixed-width `PIC X(n)` BMS field and is not observable through a JSON API, and
the target state explicitly replaces fixed-width map fields with variable-length strings. The
casing difference on "Key"/"key" is a genuine but purely cosmetic divergence in a single message,
below the MINOR threshold given the stream's otherwise exact reproduction.

### S-09 Pending Authorizations — FAIL

The IMS side is good: the hierarchy is flattened to `pending_auth_summary` / `pending_auth_detail`
with the correct composite key `(pa_acct_id, pa_auth_date_9c, pa_auth_time_9c)` and a
child-to-parent foreign key, DL/I status codes become typed results, and every packed-decimal
field from `CIPAUSMY.cpy` and `CIPAUDTY.cpy` maps at the right precision
(`PIC S9(09)V99 COMP-3` → `NUMERIC(11,2)`, `PIC S9(10)V99 COMP-3` → `NUMERIC(12,2)`,
`PIC S9(05) COMP-3` → `NUMERIC(5)`, `PIC S9(09) COMP-3` → `NUMERIC(9)`). Stateless paging, the
serialized 14-digit authorization key and the fixed-width unload/load formats are all documented
decisions (BD-1…BD-8) and implemented as documented.

The **DB2 side is where it fails**: the `AUTHFRDS` table is the only place in the entire Flyway
baseline where the migrated column definitions do not match their oracle, and all four deviations
(A-01, A-02, A-03, A-08) are in it. The pattern suggests this one table was transcribed by eye
rather than derived from the DDL the way the rest of the schema was.

### S-10 MQ Inquiry — PASS

`COACCT01.cbl` and `CODATE01.cbl` map to `AccountInquiryListener` / `DateInquiryListener` with
`MqInquiryLayout` carrying the fixed-width request/response contract and `MqErrorReport` the
error path. The seam is disabled unless explicitly enabled, per the target state. B-03's
`LegacyDateFormatter` is a model of how a boundary should be migrated: it reproduces the
assembler's contract including the fact that it validates nothing — "the Assembler copies bytes,
and so does this class" — and cites `COBDATFT.asm:30-56` for the `INVALID INPUT` cases.

### S-13 Statement Generation — PASS WITH FINDINGS

`CBSTM03A.CBL` emits literal HTML; `StatementRenderer` reproduces the fixed lines exactly,
including `<html lang="en">`, `<meta charset="utf-8">`, `<body style="margin:0px;">` and the
`<p style="font-size:16px">` wrappers for `Bank of XYZ`, `Basic Details`, `Transaction Summary`,
`Tran ID`, `Tran Details` and `Amount`. The COBOL's `DELIMITED BY '  '` name truncation is
preserved. PDF rendering is deferred as B-13 with a stated rationale (the TSO/REXX `TXT2PDF`
utility is not in the repository) — the deferral is correct and honest; it is only the *register*
that fails to record it (A-06).

### S-07 Reporting and B-02 — PASS

`CORPT00C.cbl` tolerates `CSUTLDTC` message number `2513` (unsupported range) as a non-error for
both start and end dates while rejecting every other non-`0000` severity. This is an easy detail
to miss, and `CsutldtcFeedbackService` implements it explicitly and separately from the general
`DateValidationService`, which is the right factoring: the tolerance is reporting-specific, not
a property of date validation.

### S-02 Account Management — PASS

`AccountMessages` preserves the `COACTVWC.cbl` set including `Looks Good.... so far` (four dots)
and the double space in `Account Filter must  be a non-zero 11 digit number` — another
character-level match on a string that is easy to normalise by accident. Fixed-width response-code
formatting (`String.format("%09d ", value)`) is reproduced where the COBOL builds a `PIC 9(9)`
response field.

---

## 7. Schema and data

`V1__baseline_carddemo_estate.sql` states its own conversion rule at line 4 — `PIC 9(n) →
NUMERIC(n)`, `PIC S9(a)V9(b) → NUMERIC(a+b,b)` — and applies it correctly throughout the
VSAM- and IMS-derived tables. Spot-checks that reconcile:

| Legacy | PIC / DDL | Flyway | OK |
|---|---|---|---|
| `CVTRA05Y.cpy` `TRAN-AMT` | `S9(09)V99` | `transactions.amount NUMERIC(11,2)` | yes |
| `CVACT01Y.cpy` `ACCT-CURR-BAL` | `S9(10)V99` | `accounts.curr_bal NUMERIC(12,2)` | yes |
| `CVACT01Y.cpy` cycle credit/debit | `S9(10)V99` | `NUMERIC(12,2)` | yes |
| `CVTRA01Y.cpy` `TRAN-CAT-BAL` | `S9(09)V99` | `tran_cat_bal.bal NUMERIC(11,2)` | yes |
| `CVTRA02Y.cpy` `DIS-INT-RATE` | `S9(04)V99` | `disclosure_group.int_rate NUMERIC(6,2)` | yes |
| `CIPAUSMY.cpy` limits/balances | `S9(09)V99 COMP-3` | `pending_auth_summary.* NUMERIC(11,2)` | yes |
| `CIPAUDTY.cpy` `PA-TRANSACTION-AMT` | `S9(10)V99 COMP-3` | `pending_auth_detail.pa_transaction_amt NUMERIC(12,2)` | yes |
| `CIPAUDTY.cpy` `PA-AUTH-DATE-9C` / `-TIME-9C` | `S9(05)` / `S9(09)` COMP-3 | `NUMERIC(5)` / `NUMERIC(9)`, both in the PK | yes |
| DB2 `TRANTYPE`/`TRANCATG` DCLGEN | — | `db2_transaction_type`, `db2_transaction_type_category` with FK | yes |

**No dropped field and no lost precision was found in any VSAM-derived or IMS-derived table.**

The `AUTHFRDS` table is the sole exception, and it is the reason S-09 fails:

| Column | `AUTHFRDS.ddl` | DCLGEN host var | Flyway | Finding |
|---|---|---|---|---|
| `TRANSACTION_AMT` | `DECIMAL(12,2)` | `S9(10)V9(2) COMP-3` | `NUMERIC(11,2)` | **A-01** |
| `APPROVED_AMT` | `DECIMAL(12,2)` | `S9(10)V9(2) COMP-3` | `NUMERIC(11,2)` | **A-01** |
| `FRAUD_RPT_DATE` | `DATE` | `PIC X(10)` | `CHAR(8)`, written `MM/dd/yy` | **A-02** |
| `POS_ENTRY_MODE` | `SMALLINT` | `S9(4) COMP` | `CHAR(2)` | **A-03** |
| `MERCHANT_NAME` | `VARCHAR(22)` | length-prefixed group | `CHAR(22)` | **A-08** |

The other 21 columns of `AUTHFRDS` reconcile, and the primary key `(CARD_NUM, AUTH_TS)` is
preserved.

---

## 8. Stubs, shortcuts and test quality

Searched the production sources for `TODO`, `FIXME`, `XXX`, `HACK`, placeholder returns, empty
`catch` blocks, `@Disabled`, `@Ignore` and `assumeTrue`.

- **No `TODO`/`FIXME` in production code.**
- **No disabled or skipped tests** — the Maven run reports `Skipped: 0` across all 916.
- **No empty catch blocks** in the batch or service layers; failure paths route to `AbendService`
  or to a typed exception with a preserved message.
- **No hard-coded value standing in for logic** was found. The two places that look like
  hard-coding on first reading are both correct ports: the empty `1400-COMPUTE-FEES` in S-12
  (the COBOL paragraph is genuinely empty) and the `null` return in `updatedAccount` (analysed in
  §6 — the COBOL branch really is unreachable).
- **Tests do exercise the FRs.** The interest, posting and bill-payment tests assert against the
  COBOL-derived expected values — truncated interest amounts, the 102-overwritten-by-103
  rejection reason, the double-spaced success string — rather than re-asserting whatever the
  implementation happens to produce. `TranIdSequence` is tested across a simulated restart, which
  is the behaviour that actually matters and the one a trivial test would miss.
- **The gap is the frontend**, not the backend: one test file for 21 screens, not run in CI
  (**A-07**).

---

## 9. Lane discipline — PASS

```
$ git diff --stat origin/main...origin/devin/carddemo-integration -- app/ migration/transaction-management/
(no output)
```

- **No change under `app/**`.** The legacy estate is untouched, so it remains a valid oracle.
- **No change under `migration/transaction-management/**`.** The frozen v1 reference app is
  byte-identical to `main`.
- **No cross-package editing.** Each stream's code lives under its own `com.carddemo.<stream>`
  package; shared types live in `com.carddemo.common` and are consumed read-only by the streams,
  consistent with the rule stated in `migration/carddemo/README.md`.
- **No shared-entity change that breaks another stream.** The single Flyway baseline is additive;
  the 916-test suite exercises all streams against it and passes.
- **This audit PR** adds exactly one file, `docs/migration/CardDemo_independent_audit.md`, and
  changes nothing else.

---

## 10. What must happen before merge

1. **A-01** — widen `auth_fraud.transaction_amt` and `auth_fraud.approved_amt` to `NUMERIC(12,2)`.
2. **A-02** — change `auth_fraud.fraud_rpt_date` to `DATE` and write `LocalDate.now(clock)`,
   leaving the `MM/dd/yy` string on the IMS-derived `pa_fraud_rpt_date` where it belongs.

Both are corrections to `V1__baseline_carddemo_estate.sql` (or a follow-on `V2`) plus the
corresponding field types in `AuthFraudRecord` and `AuthFraudService`. Neither affects another
stream.

Strongly recommended in the same change, though not merge-blocking:

3. **A-06** — close out all 15 boundary register rows so the register stops contradicting the
   progress ledger.
4. **A-04** — publish the `EXEC PGM=` step reconciliation so coverage becomes verifiable rather
   than plausible.
5. **A-05** — give S-04 the stream documentation every other stream has.
6. **A-07** — add `npm test` to CI.

Findings A-03, A-08, A-09 and A-10 can be scheduled as follow-up work.

---

## 11. Auditor's note

This branch is better than most modernizations of this size. The migration team read the COBOL
rather than paraphrasing it: they preserved a double space in a success message, they preserved
truncation-toward-zero on negative interest, they preserved a reason code being overwritten by a
later validation, and — most tellingly — they identified that CBACT04C never posts interest for
the last account of a run, proved the branch unreachable, and reproduced the defect instead of
silently fixing it. That is the correct instinct for a like-for-like migration and it is
consistently applied.

The failures are concentrated, not systemic. One DB2 table was transcribed by eye instead of
being derived from its DDL, and the governance artefacts — the boundary register, the step count,
the S-04 stream folder — were not kept current with the code. Fix the two blockers and the branch
should merge.

---
---

# Re-audit — 2026-09-09

**Scope:** verification that the ten findings above are genuinely closed, and that the
remediation introduced no new defect.

**Under re-audit:** `devin/carddemo-integration` at commit `986e8eb` (remediation PRs #58, #59,
#60 plus the orchestrator's documentation commits), re-checked against the same oracles: the
COBOL, DDL, DCLGEN and copybooks in `app/**`. PR descriptions were not taken as evidence; every
claim below was re-derived from the branch and the legacy source.

## R.1 Verdict

**`devin/carddemo-integration` is now fit to merge into `main`.**

Both blockers are genuinely closed, and closed correctly rather than cosmetically: the amount
domain, the century and the DB2/IMS field separation were each verified against the DDL, the
DCLGEN and the CICS `FORMATTIME` call that produces the IMS value. All ten findings are closed.

Four new findings are raised — 0 BLOCKER, 1 MAJOR, 3 MINOR. None of them corrupts data or money,
and none is a regression: the MAJOR is a pre-existing CT00 parity gap that the A-07 remediation
had the opportunity to catch and did not. They are follow-up work, not merge conditions.

| | Round 1 | Round 2 |
|---|---:|---:|
| BLOCKER | 2 | 0 |
| MAJOR | 5 | 1 |
| MINOR | 3 | 3 |

## R.2 Status of the ten original findings

| ID | Severity | Closed? | Evidence re-derived by the auditor |
|---|---|---|---|
| A-01 | BLOCKER | **CLOSED** | `V900__auth_fraud_column_types.sql:20-21` sets `transaction_amt`/`approved_amt` to `NUMERIC(12,2)`, matching `AUTHFRDS.ddl` `DECIMAL(12,2)` and `AUTHFRDS.dcl` `PIC S9(10)V9(2) COMP-3`. `AuthFraudColumnFidelityTest.anAmountAtTheTopOfTheDecimal12Domain` round-trips `9999999999.99` — the exact top of the domain — so the test is not tautological. |
| A-02 | BLOCKER | **CLOSED** | `V900:27-28` rebuilds `fraud_rpt_date` as `DATE`; `AuthFraudRecord:87` is `LocalDate`; `AuthFraudService:71` stamps `LocalDate.now(clock)`, matching `COPAUS2C.cbl:194,225` `,CURRENT DATE`. The century survives: `theCenturyOfTheReportDateSurvivesTheRoundTrip` and `reportDatesOrderChronologicallyAcrossTheCentury` assert `1999-12-31 < 2001-01-01`, which the old `CHAR(8)` `MM/dd/yy` could not express. |
| A-03 | MAJOR | **CLOSED** | `V900:24-25` makes `pos_entry_mode` `SMALLINT`; `AuthFraudRecord:60` is `Integer`, matching `PIC S9(4) COMP`. `thePosEntryModeIsComparedNumerically` proves ordering is numeric, not lexical. |
| A-08 | MINOR | **CLOSED** | `V900:22` makes `merchant_name` `VARCHAR(22)`; `theMerchantNameReadsBackWithoutPadding` asserts no trailing pad. |
| A-04 | MAJOR | **CLOSED** | Inventory §9 publishes the arithmetic. Re-derived independently: the documented grep returns exactly 98 + 3 + 11 + 7 = **119**, and the per-program histogram (`IDCAMS` 62, `IEFBR14` 9, `SDSF` 8, `IEBGENER` 8, `IKJEFT01` 7, `SORT` 5, `DFSRRC00` 5, `CBTRN03C` 2, thirteen singletons) reproduces byte for byte. 96 + 23 = 119 closes. See A-13 for the one figure in §9.5 that does not reproduce. |
| A-05 | MAJOR | **CLOSED** | Inventory §10 states that S-04's requirements live in the frozen `migration/transaction-management/**` module under D-6, and enumerates every program with no FR document. The absence is now declared rather than implicit. |
| A-06 | MAJOR | **CLOSED** | `.migration/04_boundary_register.md` now carries a dated decision table for B-01…B-15: eleven IMPLEMENTED, four DEFERRED, each with a rationale and a named artefact. B-13's deferral rationale (the TXT2PDF load library is not in the repository) is explicit, as required. |
| A-10 | MINOR | **CLOSED** | D-9 records that the migration follows `PRTCATBL`'s `OUTREC` field layout and not the declared `LRECL=40`, and says why. |
| A-09 | MINOR | **CLOSED** | `ExportImportSysout` reproduces the `ERROR:` and `<program>: ABENDING PROGRAM` lines; `ExportStatistics`/`ImportStatistics` reproduce every counter line. Checked literal by literal against all 29 `DISPLAY` statements in `CBEXPORT.cbl` and all 28 in `CBIMPORT.cbl`, including the `9(09)` zero padding and the casing change between the per-group `exported:` (cbl:254) and the finalize `Exported:` (cbl:564). The error-file write path is right too: `CBIMPORT.cbl:2750-WRITE-ERROR` displays but does **not** abend and still increments the counter, and `ImportOutputItemWriter:76-80` special-cases `ImportTarget.ERROR` to do exactly that. |
| A-07 | MAJOR | **CLOSED** | 14 suites / 212 tests, run by CI (`carddemo-ci.yml:46`, `npm test -- --watchAll=false`). Verified locally: `Test Suites: 14 passed, Tests: 212 passed`. Every registered screen now has a suite. The parity defect the suite found (D-1) is genuinely fixed — see R.3. |

## R.3 The one production behaviour change (D-1), verified

`TransactionListPage.js` previously used one literal for two distinct COTRN00C events. The fix
separates them, and the separation is correct:

| Event | COBOL | Literal | Java |
|---|---|---|---|
| PF8 pressed while `NEXT-PAGE-NO` | `COTRN00C.cbl:267-274` | `You are already at the bottom of the page...` | `TransactionListPage.js:23,163-166` |
| `READNEXT` hits `ENDFILE` filling a page | `COTRN00C.cbl:639-645` | `You have reached the bottom of the page...` | `TransactionListPage.js:24,121-123` |
| PF7 pressed while `PAGE-NUM = 1` | `COTRN00C.cbl:245-252` | `You are already at the top of the page...` | `TransactionListPage.js:25,172-175` |

The guard semantics also match: COBOL's `NEXT-PAGE` flag is set by a lookahead `READNEXT` during
the previous render (`cbl:308-315`), which is what `hasNextPage` is computed from
(`TransactionListService:89-92`). Both leave the page in place and only change the message.

**The `PROCESS-PAGE-BACKWARD` path, which the request asked to be checked specifically, is not
fully migrated** — see A-11.

## R.4 New findings

| ID | Severity | Stream | COBOL | Java | What is wrong | What correct looks like |
|---|---|---|---|---|---|---|
| A-11 | MAJOR | S-04 | `app/cbl/COTRN00C.cbl:605-610`, `:673-678` | `migration/carddemo/frontend/src/pages/transaction/TransactionListPage.js:23-25,110-127` | CT00 implements three of COTRN00C's five `WS-MESSAGE` boundary literals. Missing: `You are at the top of the page...` (`STARTBR` returns `NOTFND` — an empty `TRANSACT` file, or a filter Tran ID higher than every key) and `You have reached the top of the page...` (`READPREV` hits `ENDFILE` while `PROCESS-PAGE-BACKWARD` fills a page — reachable by filtering into the middle of the file and then pressing PF7, which returns fewer than ten rows). In both cases the screen renders silently. `load()` hard-codes `''` for every `dir === 'prev'` response, so the second literal can never appear. The same program pair got this right for CU00: `UserListPage.js:114-121` handles all five. | Mirror `UserListPage.js:114-121`: `count === 0` → `You are at the top of the page...`; a short page → `You have reached the top of the page...` when `dir === 'prev'`, `You have reached the bottom of the page...` otherwise. Add the two cases to `TransactionListPage.test.js`, which currently declares only three of the five literals (`:15-17`). |
| A-12 | MINOR | S-04, S-05 | `app/cbl/COTRN00C.cbl:308-315` with `:639-645`; `app/cbl/COUSR00C.cbl:308-316` | `TransactionListPage.js:121-123`; `UserListPage.js:117-119` | A last page that happens to be exactly ten rows renders no message. In COBOL the fill loop exits on `WS-IDX >= 11` without EOF, and the **lookahead** `READNEXT` then hits `ENDFILE`, which sets `WS-MESSAGE` to `You have reached the bottom of the page...`; `SEND-TRNLST-SCREEN:531` never clears `WS-MESSAGE`, so the operator does see it. Both screens instead test `count < PAGE_SIZE`, which is false for a full last page. | Test `!data.hasNextPage` rather than `data.count < PAGE_SIZE`. `count < PAGE_SIZE` implies `!hasNextPage`, so the new condition is a strict superset and no other case changes. |
| A-13 | MINOR | governance | — | `docs/migration/CardDemo_inventory.md:317-319` | §9.5 — the section added to close A-04, whose whole purpose is reproducible arithmetic — states "**34** `StepBuilder` step definitions". `grep -rho "new StepBuilder(" src/main/java \| wc -l` returns **40** (and understates the runtime total, because `filereads/PrintSteps.java` is a shared factory called by four jobs). The 21 `Job` beans in the same sentence do reproduce. | Correct the figure to 40, or state the counting rule that yields 34. The argument §9.5 makes — that D-3 is a floor, not an equality — is unaffected either way. |
| A-14 | MINOR | S-09 | `app/app-authorization-ims-db2-mq/ddl/AUTHFRDS.ddl:12,15` | `V900__auth_fraud_column_types.sql:24-28` | `pos_entry_mode` and `fraud_rpt_date` are migrated by `DROP COLUMN` + `ADD COLUMN`, which discards any existing values and moves both columns to the end of the table. For `fraud_rpt_date` the file explains this (the `CHAR(8)` `MM/dd/yy` century is unrecoverable, so a cast would invent data) and the choice is right. For `pos_entry_mode` it is not: `CHAR(2)` → `SMALLINT` is a lossless cast. Immaterial in this repository — no seed inserts into `auth_fraud`, and `R__seed_carddemo_data.sql:12` empties it — but applied to a populated environment it is a silent data-loss migration. | `ALTER TABLE auth_fraud ALTER COLUMN pos_entry_mode TYPE SMALLINT USING pos_entry_mode::smallint;`. Note in the migration header that `fraud_rpt_date` is deliberately rebuilt and why, so an operator does not read the two `DROP`s as the same decision. |

## R.5 Verified build and test numbers

Re-run by the auditor on commit `986e8eb`.

Backend — `mvn -B test` in `migration/carddemo/backend`:

```
Tests run: 926, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS   (347 main sources, 160 test sources, 01:12 min)
```

926, up from the 916 verified in round 1: +5 `AuthFraudColumnFidelityTest`, plus the new
`ExportImportSysoutTest` and `PendingAuthDetailServiceTest` cases.

Frontend — Node v20.20.2, npm 10.8.2, in `migration/carddemo/frontend`:

```
npm ci                                    exit 0
npm test -- --watchAll=false              Test Suites: 14 passed, 14 total
                                          Tests:       212 passed, 212 total
CI=true npm run build                     Compiled successfully.   exit 0
```

The 212/14 claim is accurate. Zero skipped tests in either suite.

## R.6 Lane discipline and blast radius

`git diff --name-only d8b64a7..986e8eb` returns 40 files. None is under `app/**` or
`migration/transaction-management/**`, and `V1__baseline_carddemo_estate.sql` is untouched — the
S-09 correction is an additive migration in the stream's own `V900-V999` band, as claimed.
Exactly one production frontend file changed (`TransactionListPage.js`); the other 16 frontend
files are tests, the test harness, `setupTests.js` and the lockfile. Backend changes are confined
to `com.carddemo.batch.exportimport`, `com.carddemo.pendingauth` and `AuthFraudRecord`.

The Flyway H2 profile does execute `V900` during `mvn test` (`application.yml:73-76`,
`ddl-auto: none`), so the migration is exercised rather than bypassed by schema generation.

## R.7 Stream verdicts, revised

| Stream | Round 1 | Round 2 |
|---|---|---|
| S-09 PendingAuthorizations | **FAIL** | **PASS** |
| S-16 DataExportImport | PASS WITH FINDINGS | **PASS** |
| S-14 TransactionReporting | PASS WITH FINDINGS | **PASS** |
| S-17 OperationsChain | PASS WITH FINDINGS | **PASS** |
| S-13 StatementGeneration | PASS WITH FINDINGS | **PASS** |
| S-04 TransactionManagement | PASS WITH FINDINGS | **PASS WITH FINDINGS** (A-11, A-12) |
| S-05 UserManagement | PASS | **PASS WITH FINDINGS** (A-12) |
| all others | PASS | **PASS** |

## R.8 Auditor's note

The remediation is honest. The S-09 fix did not merely change column types: it recognised that
the old `CHAR(8)` value could not be cast back into a `DATE` without inventing a century, rebuilt
the column instead, and moved the `MM/dd/yy` rendering to `PendingAuthFormat.reportDate` for the
IMS field that genuinely is eight characters wide. That field is `PA-FRAUD-RPT-DATE PIC X(08)`
(`CIPAUDTY.cpy:53`), and `COPAUS2C.cbl:95-101` fills it with `EXEC CICS FORMATTIME MMDDYY(...)
DATESEP` — which, with `DATESEP` defaulting to `/`, is exactly `MM/DD/YY`, eight characters. The
two fields are now correctly separate and both are correctly represented. The conflation the
first audit found has not reappeared.

The A-07 work is the round's most valuable outcome, because it was tests finding a defect rather
than tests recording behaviour: the D-1 literal split came out of writing the suite. That it
stopped three literals short of the five COTRN00C actually uses — and that CU00's screen, written
by the same effort, has all five — is the finding worth acting on.

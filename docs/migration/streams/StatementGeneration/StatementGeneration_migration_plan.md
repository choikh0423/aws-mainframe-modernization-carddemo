# S-13 StatementGeneration — Migration Plan

**Stream:** S-13 StatementGeneration (BATCH), job `CREASTMT`.
**Target package:** `com.carddemo.batch.statement` (Spring Batch 5, per D-3).
**Flyway band:** `V1300`–`V1399`.
**Companion documents:** `StatementGeneration_analysis.md` (source analysis),
`StatementGeneration_functional_requirement.md` (FR-01…FR-42), `programs/CBSTM03A_functional_requirement.md`
(FR-A1…FR-A18), `programs/CBSTM03B_functional_requirement.md` (FR-B1…FR-B11).

---

## 1. What was built

| Legacy artifact | Target artifact |
| --- | --- |
| `CREASTMT.JCL` (the job) | `CreateStatementJobConfiguration.createStatementJob`, job name `CREASTMT` |
| `DELDEF01` (IDCAMS delete/define) | Step `DELDEF01`: tasklet clearing `statement_work_transactions`, deleting the work file and creating the output directory |
| `STEP010` (SORT) | Step `STEP010`: reads `transactions` ordered by card number then transaction id, writes 350-byte `COSTM01` records to the work file via `TrnxLayout.fromTransact` |
| `STEP020` (IDCAMS REPRO) | Step `STEP020`: loads the work file into the keyed work table via `TrnxLayout.toWorkTransaction` |
| `STEP030` (IEFBR14 delete) | Step `STEP030`: tasklet deleting `STATEMNT.PS` and `STATEMNT.HTML` |
| `STEP040 EXEC PGM=CBSTM03A` | Step `STEP040`: `StatementItemReader` + `CompositeItemWriter` (text and HTML) |
| `CBSTM03A` mainline | `StatementItemReader` (xref-driven loop, keyed customer/account reads, abends) |
| `CBSTM03A` 0000-START / transaction table | `TransactionIndex` (priming read, 51 × 10 grouping) |
| `CBSTM03A` 5000/5100/5200/6000 report paragraphs | `StatementRenderer` (X(80) text records, X(100) HTML records) |
| `CBSTM03B` | `StatementFileAccess` + `StatementFileArea` (DD-name dispatch, `O`/`C`/`R`/`K`, FILE STATUS return codes) |
| COBOL record layouts | `TrnxLayout`, `XrefLayout`, `CustomerLayout`, `AccountLayout`, `StatementFormat` |
| `CEE3ABD` | `StatementAbend` on top of the shared `AbendService` |

Files are datasets in name only: the work file `AWS.M2.CARDDEMO.TRXFL.SEQ` and the two reports
`AWS.M2.CARDDEMO.STATEMNT.PS` / `AWS.M2.CARDDEMO.STATEMNT.HTML` are written under
`carddemo.batch.statement.output-dir` (default `target/statements`). The keyed work store
`TRXFL.VSAM.KSDS` is the table `statement_work_transactions`, created by
`V1300__statement_work_transactions.sql` with the legacy `KEYS(32 0)` composite key
(card number + transaction id).

Shared entities (`AccountRecord`, `CustomerRecord`, `CardXrefRecord`, `TransactionRecord`) and their
repositories are used read-only; nothing in `com.carddemo.common` was changed and no column was added.
Nothing outside `com.carddemo.batch.statement` was touched except one two-line wiring fix in
`DataLoadJobTest` (see §6).

Run it the way the README documents:

```bash
cd migration/carddemo/backend
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.batch.job.name=CREASTMT
```

## 2. Design decisions

- **One step per `EXEC PGM=`, plus the IDCAMS/IEFBR14 housekeeping steps**, so a failed run is diagnosable
  step by step exactly as the JCL is. `COND=(0,NE)` (FR-09) is Spring Batch's default step sequencing: a
  failed step stops the job.
- **`CBSTM03B` is migrated as a subroutine, not inlined.** `StatementFileAccess` keeps the DD name /
  operation / return-code contract of the linkage area, including the two unimplemented operations and the
  silent unknown-DD path (FR-B2, FR-B8, FR-B11). That keeps CBSTM03A's error handling — which branches on
  the two-character FILE STATUS — a faithful translation instead of an interpretation.
- **The 51 × 10 transaction table is modelled explicitly** (`TransactionIndex`) rather than replaced by a
  per-card query, because its capacity is observable behaviour: an 11th transaction never reaches a
  statement (FR-14).
- **Statement text is assembled from the copybook's edited pictures**, not from `String.format`:
  `StatementFormat.amountPic9` / `amountPicZ` implement `9(9).99-` and `Z(9).99-` including the trailing
  blank for positive amounts and the high-order truncation above nine integer digits (FR-23, FR-24, FR-27,
  FR-15). `CobolFormat.amountEdited` in `com.carddemo.common` covers a different picture, so the stream
  keeps its own formatter rather than changing a shared class.
- **Restart.** The job runs on the shared `JobRepository`. `STEP040` has no restart cursor: `DELDEF01` and
  `STEP030` rebuild the work store and delete the reports at the start of every run, so a restarted job
  regenerates both reports from the beginning — which is what re-running the JCL does.

## 3. Legacy vs migrated parity

Parity is measured on the seeded estate (`db/seed/R__seed_carddemo_data.sql`, generated from
`app/data/ASCII/**`) plus the transaction rows described in §4, exercised by `CreateStatementJobTest`.

| Path | Legacy behaviour | Migrated behaviour |
| --- | --- | --- |
| Job shape | 5 steps, `DELDEF01`→`STEP010`→`STEP020`→`STEP030`→`STEP040` | Same 5 step names in the same order (`CreateStatementJobTest.runsTheFiveCreastmtStepsAndWritesBothReports`) |
| Work record | 350-byte `COSTM01`, card number first, PROC-TS truncated to 24 characters | Identical, `TrnxLayoutTest` |
| Statement set | One statement per CARDXREF record, ascending card number | 50 statements for the 50 seeded xref rows, ascending card number |
| Text report | X(80) fixed records, `START OF STATEMENT` banner … `END OF STATEMENT` | Byte-identical line shapes, every record exactly 80 characters |
| HTML report | X(100) fixed records, `<!DOCTYPE html>` … `</html>` per statement | Same document, every record exactly 100 characters |
| Amounts | `$       13.75 ` / `$       17.50-` (trailing sign, blank when positive) | Identical |
| Card with no transactions | Statement with a zero total `$         .00 ` | Identical |
| Missing customer for an xref record | `ERROR READING CUSTFILE`, `RETURN CODE: 23`, `ABENDING PROGRAM`, abend | Same three log lines, `AbendException`, job `FAILED` |
| Empty transaction work store | Priming read returns `10` → `ERROR READING TRNXFILE` and abend | Identical (`TransactionIndexTest.abendsWhenTheWorkStoreIsEmpty`) |

A sample of the generated text report (seeded account 50, two transactions):

```
*******************************START OF STATEMENT*******************************
Aniya Alba Von
1588 Nienow Cape
Suite 187
New OR USA 04257
--------------------------------------------------------------------------------
                                 Basic Details
--------------------------------------------------------------------------------
Account ID         :00000000050
Current Balance    :000000492.00
FICO Score         :623
--------------------------------------------------------------------------------
                              TRANSACTION SUMMARY
--------------------------------------------------------------------------------
Tran ID         Tran Details                                         Tran Amount
--------------------------------------------------------------------------------
0000000000000042 GROCERY PURCHASE                                 $       13.75
0000000000000043 FUEL PURCHASE                                    $       17.50-
--------------------------------------------------------------------------------
Total EXP:                                                        $        3.75-
********************************END OF STATEMENT********************************
```

(trailing blanks stripped for this document; the records themselves are 80 characters.)

## 4. Source vs inventory contradictions

1. **No TRANSACT unload exists.** `CREASTMT.JCL:47` reads `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS`, but
   `app/data/ASCII/**` contains no TRANSACT unload — only `dailytran.txt` (DALYTRAN, a different file).
   The consolidated `transactions` table is therefore populated only by
   `db/seed/R__seed_transaction_demo_data.sql`, the Transaction Management demo dataset. `STEP010` reads
   that table, which is the correct target of the migration, but no full-estate parity run against real
   TRANSACT data is possible in this repository. Recorded here rather than worked around.
2. **The transaction demo seed contradicts the master files.** `R__seed_transaction_demo_data.sql` adds
   five `card_xref` rows (`4111111111111111`…`4555555555555555`) whose `cust_id`/`acct_id`
   (`111000001`, `10000000001`, …) exist in neither `customers` nor `accounts`. Running `CREASTMT` over the
   seeded database as shipped therefore abends with `ERROR READING CUSTFILE` — which is the *faithful*
   CBSTM03A behaviour for an orphan xref record (FR-40), not a migration defect. `CreateStatementJobTest`
   asserts both halves: the abend on the orphan row, and a clean 50-statement run once the orphan rows are
   excluded. The seed is another stream's fixture, so it was left untouched.
3. **`HTMLFILE` LRECL disagrees between steps** — 80 in `STEP030`, 100 in `STEP040`
   (`CREASTMT.JCL:69, 94`). `STEP030` only deletes the file, so the mismatch is inert; the target writes
   100-character records (FR-08).

## 5. Boundary decisions and deferrals

- **B-13 — PDF rendering is deferred.** `TXT2PDF1.JCL` renders the text statement to PDF with
  `IKJEFT1B` driving the TSO/REXX `TXT2PDF` utility (`TXT2PDF1.JCL:24-40`). It is a separate job, not a
  step of `CREASTMT`, and the backend has no PDF library on its classpath. Adding one (OpenPDF, PDFBox) is
  a new dependency and a new rendering contract, neither of which is pinned by the target state, so this
  stream produces the text and HTML statements only and B-13 stays open. The text report is a fixed-pitch
  80-column document, so a later PDF step can consume it unchanged.
- **FR-A2 — the TIOT diagnostic is not migrated.** CBSTM03A walks PSA → TCB → TIOT to display the job name,
  step name and the DD names allocated to the step (`CBSTM03A.CBL:238-311`). These are z/OS control blocks
  with no target equivalent; Spring Batch's own job/step execution logging carries the same diagnostic
  intent. No behaviour that a statement reader can observe depends on it.
- **FR-14 — the one deliberate behavioural deviation.** The legacy table has no bound check: an 11th
  transaction for a card, or a 51st distinct card, writes past `WS-TRNX-TABLE` and corrupts adjacent
  storage (`CBSTM03A.CBL:818-830`). The migrated `TransactionIndex` keeps the same capacities — so the same
  records reach a statement — but discards the excess and logs a count instead of corrupting memory.
  Reproducing a storage overlay is neither possible nor desirable in the target; everything observable on a
  statement is unchanged.
- **No frontend work.** S-13 has no BMS map, no CICS transaction and no online entry point, so no React
  component and no route-registry line were added.

## 6. Lane notes

- Everything new is in `com.carddemo.batch.statement`, plus `V1300__statement_work_transactions.sql` in the
  stream's reserved Flyway band.
- One shared test needed a two-line fix: `DataLoadJobTest` relied on `JobLauncherTestUtils` autowiring the
  single `Job` bean in the context. `CREASTMT` is the second job bean, so the test now selects
  `dataLoadJob` explicitly. No production code outside the stream package was changed. Every stream that
  adds a job will hit this once; the fix is the standard `jobLauncherTestUtils.setJob(...)` call.

## 7. Verification

```bash
cd migration/carddemo/backend && mvn -B test      # 166+ tests, green
cd migration/carddemo/frontend && npm ci && npm run build
```

The stream's own tests are `CreateStatementJobTest`, `StatementItemReaderTest`, `StatementRendererTest`,
`StatementFileAccessTest`, `TransactionIndexTest`, `TrnxLayoutTest` and `StatementFormatTest`; the
FR-to-test matrices are in the FR documents.

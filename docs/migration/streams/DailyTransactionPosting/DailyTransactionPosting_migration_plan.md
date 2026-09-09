# S-11 DailyTransactionPosting — migration plan

How `POSTTRAN` / `CBTRN02C` was implemented in the consolidated application, the boundary decisions
taken, and the legacy-versus-migrated parity evidence.

Sources: `app/jcl/POSTTRAN.jcl`, `app/cbl/CBTRN02C.cbl`, `app/jcl/DALYREJS.jcl`, `app/jcl/TRANBKP.jcl`,
`app/jcl/TRANIDX.jcl`. Requirements: `DailyTransactionPosting_functional_requirement.md` (FR-S11-nnn),
`programs/CBTRN02C_functional_requirement.md` (FR-CBTRN02C-nn).

## 1. Shape of the implementation

Everything lives in `com.carddemo.batch.posttran` (backend, no frontend surface — S-11 is a batch stream).

| Legacy | Migrated |
| --- | --- |
| Job `POSTTRAN` | `PostTranJobConfiguration.postTranJob()` — Spring Batch `Job` named `POSTTRAN` |
| `STEP15 EXEC PGM=CBTRN02C` | `Step` named `STEP15`, chunk oriented (100), restartable |
| DD `DALYTRAN` (QSAM, 350) | `JpaPagingItemReader` over `DailyTransactionRecord` ordered by transaction id |
| DD `TRANSACT` (VSAM KSDS) | `TransactionRepository` |
| DD `XREFFILE` (VSAM KSDS) | `CardXrefRepository` |
| DD `ACCTFILE` (VSAM KSDS) | `AccountRepository` |
| DD `TCATBALF` (VSAM KSDS) | `TransactionCategoryBalanceRepository` |
| DD `DALYREJS` (QSAM, 430) | `FlatFileItemWriter<String>` over the generation `RejectFileGenerations` allocated |
| `CALL 'CEE3ABD'` | `com.carddemo.common.batch.AbendService` (boundary B-01) |
| Condition code 0 / 4 | `PostTranJobListener implements ExitCodeGenerator` |

Program logic is split the way the COBOL paragraphs are:

- `TransactionValidationService` — 1500-A/1500-B lookups plus the two limit checks, in COBOL order,
  returning the reason code and the message text of the first (and, for 103, the last) failing check.
- `TransactionPostingService` — 2000-POST-TRANSACTION: field-by-field copy, processing timestamp,
  TCATBALF create-or-add, ACCTDAT update, TRANSACT write.
- `RejectRecord` / `DailyTransactionImage` — the 430-byte reject record: the original 350-byte DALYTRAN
  image re-rendered from the record (zoned decimal with overpunched sign included) plus the 80-byte trailer.
- `Db2TimestampFormatter` — the `YYYY-MM-DD-HH.MM.SS.hh0000` processing timestamp, clock injected.
- `DailyTransactionPostingProcessor` — the read loop body: post, or emit a reject line for the writer.
- `TransactionMasterInitializer` — `OPEN OUTPUT TRANFILE`.
- `PostTranJobListener` — the execution markers, the two counters, the condition code, the reject generation.

## 2. Boundary decisions

**B-01 abend.** `AbendService` is used for every unrecoverable path CBTRN02C hands to `CEE3ABD`
(FR-CBTRN02C-30). The migrated job does not kill the JVM: the step fails, the JobRepository records it,
`BatchJobLauncher` exits 12. A restart therefore resumes from the last committed chunk instead of
rerunning the whole file, which is strictly better than the legacy rerun and does not change any posted
result (FR-S11-028).

**`OPEN OUTPUT TRANFILE` (D-1 sequential-file mapping).** The COBOL opens TRANSACT for output, which on
z/OS empties the dataset — POSTTRAN always rewrites the transaction master from scratch, and `TRANBKP.jcl`
is what preserves the previous content. `TransactionMasterInitializer` reproduces this by deleting all
`transactions` rows once per job execution, guarded by a marker in the execution context so a restart does
not wipe the chunks already committed (FR-S11-003, FR-S11-028).

**`TRANBKP.jcl`, `TRANIDX.jcl`, `DALYREJS.jcl` (IDCAMS, effect only).** No IDCAMS is ported.
- `TRANBKP` — backup-then-redefine of the transaction master. Its effect (POSTTRAN starts from an empty
  master, the previous generation survives elsewhere) is the delete above; taking the backup itself is a
  database-operations concern (`pg_dump`/snapshot), not application code. Recorded as deferred in §5.
- `TRANIDX` — a non-unique alternate index over the 26-byte processing timestamp at offset 304. Migrated as
  the Flyway migration `V1100__posttran_transaction_proc_ts_index.sql`, `idx_transactions_proc_ts` on
  `transactions(proc_ts)`, inside S-11's reserved band (FR-S11-024).
- `DALYREJS` — a GDG with `LIMIT(5) SCRATCH`. `RejectFileGenerations` writes
  `DALYREJS.G<nnnn>V00` files under `carddemo.batch.posttran.reject-dir`, allocates the next generation per
  job instance and scratches everything older than the newest five (FR-S11-023). The reject file stays a
  file rather than becoming a table: it is a fixed 430-byte report consumed downstream as a dataset, and
  nothing in the estate reads it relationally.

**DALYTRAN source.** The reader reads the `daily_transactions` table rather than parsing
`app/data/ASCII/dailytran.txt` directly, because the consolidated application already loads that unload
into the table with the DATALOAD job (`com.carddemo.batch.dataload`) and every other stream consumes the
table. The 350-byte image the reject record needs is re-rendered from the row by `DailyTransactionImage`,
which is asserted byte-for-byte against all 300 fixture lines (`DailyTransactionImageTest`), so no fidelity
is lost. Reading order (transaction id) is the physical order of the unload.

**Counters and markers.** The COBOL `DISPLAY`s become `INFO` logs with the literal text and the same
`ZZZZZZZ9`-style zero padding (FR-S11-020); they are asserted in `PostTranJobIntegrationTest`.

**Nothing shared was modified.** No entity, no repository, no existing migration, no CI workflow, no
`app/**`, no other stream's package.

## 3. Quirks preserved deliberately

| Quirk | Source | Where |
| --- | --- | --- |
| Reason 103 overwrites an already-set 102: the expiration check is not `ELSE`-guarded, so an overlimit *and* expired transaction is rejected as expired | CBTRN02C.cbl:409-422 | FR-S11-009, `TransactionValidationServiceTest.expirationOverwritesOverlimit` |
| A transaction exactly at the credit limit, and one whose expiration date equals the transaction date, are both accepted | CBTRN02C.cbl:404, 415 | FR-S11-007, FR-S11-008 |
| Debit cycle totals are accumulated as negative numbers, so the "debit" column is negative | CBTRN02C.cbl:521-525 | FR-S11-015 |
| Reason 109 (`TRANSACTION ID NOT FOUND`) can never be reached: the write path abends first | CBTRN02C.cbl:552-560 | FR-S11-026, `PostTranJobIntegrationTest.neverWritesReason109` |
| No validation of amount, merchant, type or category — anything that passes the four checks posts | CBTRN02C.cbl:389-422 | FR-S11-025 |
| The reject trailer is written even for reasons the record cannot carry data for | CBTRN02C.cbl:590-607 | FR-S11-018 |

None of these were "fixed".

## 4. Parity: legacy behaviour vs migrated behaviour

The expected numbers are not read off the migrated code. `migration/carddemo/tools/posttran_parity_oracle.py`
applies the CBTRN02C rules directly to the ASCII unloads in `app/data/ASCII/**`
(`dailytran.txt`, `cardxref.txt`, `acctdata.txt`, `tcatbal.txt`) and is the oracle; the integration test
asserts the migrated job against those numbers.

| Path | Legacy (oracle over `app/data/ASCII/**`) | Migrated (`PostTranJobIntegrationTest`) |
| --- | --- | --- |
| Records read from DALYTRAN | 300 | 300 (`readCount`) |
| Posted to TRANSACT | 262 | 262 rows |
| Rejected to DALYREJS | 38 | 38 lines, each 430 chars |
| Reject reasons | all `0102 OVERLIMIT TRANSACTION` | identical trailer on every line |
| TCATBALF rows after the run | 100 (50 seeded, 50 created) | 100, each equal to what was posted into it |
| Account 1 `curr_bal` / cycle credit / cycle debit | 1288.10 / 1164.87 / -70.77 | identical |
| Condition code | 4 | 4 (`PostTranJobListener.getExitCode()`) |
| Nothing rejected (credit limit raised) | 300 posted, CC 0 | 300 posted, CC 0, empty reject file |
| Everything rejected (limit 0, amounts positive) | 0 posted, accounts and TCATBALF untouched | identical |

The 38 rejects are order-dependent — each one is decided against the cycle totals the *earlier* records of
the same run left on the account — so this number also demonstrates that the migrated job accumulates in
input order (FR-S11-016).

Restart parity has no legacy equivalent (z/OS reran the step from the top); the migrated behaviour is
asserted instead: a failure mid-file, then a restart with the same job parameters, ends with exactly the
262 postings and 38 rejects of a clean run and no double posting
(`PostTranJobIntegrationTest.restartsFromTheLastCommittedChunk`).

## 5. Deferred, with reasons

- **Taking the TRANBKP backup copy.** POSTTRAN's dependency on it (an empty master at start) is
  implemented; producing and retaining the backup generation itself is an operational database task and
  has no COBOL logic to migrate.
- **Scheduling.** The chain `TRANBKP → POSTTRAN → TRANIDX` was a JCL/scheduler sequence. The job is
  launchable per the README (`--spring.batch.job.name=POSTTRAN`); wiring a scheduler is out of scope.
- **`CBTRN02C`'s file-status displays for I/O errors** are implemented as abends with the same text, but
  the numeric VSAM status codes have no equivalent in JPA; the message renders the COBOL literal with the
  status field left as the fixed `NNNN` shape the COBOL prints when it has none.

## 6. Contradiction with the inventory

`migration/carddemo/README.md` reserves package `com.carddemo.batch.posting` for S-11, while this stream
was assigned `com.carddemo.batch.posttran`. The assignment was followed; the README line is stale.

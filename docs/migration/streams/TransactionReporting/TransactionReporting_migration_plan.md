# S-14 TransactionReporting — migration plan

How the stream was implemented, the boundary decisions taken, the parity evidence, and what was
deliberately left out. Analysis: [`TransactionReporting_analysis.md`](TransactionReporting_analysis.md).
Requirements: [`TransactionReporting_functional_requirement.md`](TransactionReporting_functional_requirement.md).

## 1. Shape of the implementation

Everything lives in `com.carddemo.batch.tranreport`
(`migration/carddemo/backend/src/main/java/com/carddemo/batch/tranreport`), per the README's lane
rules. Two Spring Batch 5 jobs (D-3), one step per legacy `EXEC PGM=`:

| Legacy step | Migrated step | Class |
| --- | --- | --- |
| `TRANREPT STEP05R` (`REPRO` TRANSACT) | `STEP05R` | `TranReportJobConfiguration` — `JpaPagingItemReader<TransactionRecord>` → `TransactionRecordImage` → flat file |
| `TRANREPT STEP05R` (`SORT`) | `STEP05RSORT` | `TransactionExtractSortTasklet` |
| `TRANREPT STEP10R` (`CBTRN03C`) | `STEP10R` | `TransactionReportWriter` |
| `PRTCATBL DELDEF` (`IEFBR14`) | `DELDEF` | `PrintCategoryBalanceJobConfiguration` |
| `PRTCATBL STEP05R` (`REPRO` TCATBALF) | `STEP05R` | `CategoryBalanceRecordImage` |
| `PRTCATBL STEP10R` (`SORT`) | `STEP10R` | `CategoryBalanceSortTasklet` |

Supporting classes: `TranReportPictures` (COBOL amount editing), `PostedTransaction` (the parsed
extract record), `TranReportFiles` (dataset names), `TransactionReportLauncher` /
`TransactionReportSubmission` (the launch port) and `TransactionReportJobLauncherAdapter` (the
binding to S-07's seam).

The jobs are named `TRANREPT` and `PRTCATBL` and are started the way the README prescribes, by
`com.carddemo.batch.BatchJobLauncher` with `spring.batch.job.name`:

```bash
java -jar carddemo.jar --spring.main.web-application-type=none \
     --spring.batch.job.name=TRANREPT \
     --reportType=Monthly --startDate=2022-07-01 --endDate=2022-07-06
```

Restartability is the shared `JobRepository`'s: each step is a normal chunk/tasklet step, so a
failed run resumes at the step that failed.

### Why the steps stay file-based

The legacy job is three steps because it moves data through two flat files. Reading TRANSACT
straight out of the database and writing the report in one step would have been shorter, but the
133-byte report is produced from the *sorted, date-filtered extract* and the totals depend on the
order of that extract; keeping the extract as a real file keeps the step boundaries, the record
images, and therefore the parity oracle. The 350-byte unload image is also the only place where the
column positions the SORT symbols address (263-278, 305-314) can be reproduced honestly.

## 2. Boundary decisions

- **B-1 — the launch port (S-07 boundary).** CR00 built JCL and wrote it to the CICS internal
  reader (`WRITEQ TD QUEUE('JOBS')`, CORPT00C.cbl:517-521). Migrated, S-14 exposes
  `TransactionReportLauncher.submit(reportType, startDate, endDate)`, which launches the `TRANREPT`
  job and returns `TransactionReportSubmission(jobExecutionId, status, reportFile)`. S-07 calls it
  from its CR00 replacement. S-07 landed its side first as
  `com.carddemo.reporting.port.TransactionReportJobLauncher`, with a recording no-op bound
  `@ConditionalOnMissingBean` until this stream arrived, so
  `TransactionReportJobLauncherAdapter` implements that interface and delegates to the launcher;
  registering it drops the no-op out. **No S-07 code is touched by this stream.** The contract is the three
  values CR00 already derived (`Monthly` / `Yearly` / `Custom` plus two `YYYY-MM-DD` dates,
  inclusive). A unique `submittedAt` parameter is added on every call so a repeated request starts a
  new run, as the internal reader did (FR-1405).
- **B-2 — `reportType` is carried, not used.** Nothing in the legacy batch reads it (FR-1403); it is
  kept as a job parameter so runs are identifiable and so the port matches what CR00 knows.
- **B-3 — `DATEPARM` becomes job parameters.** On the mainframe CR00 wrote the same two dates into
  both the SORT symbols and the `DATEPARM` dataset. One pair of job parameters feeds both migrated
  steps. The double filtering itself is preserved (FR-1430): the report step re-applies the window,
  so a record that reached it out of range is still skipped, and Q-4 still shows.
- **B-4 — GDG generations become per-execution file names.** `(+1)` becomes the job execution id:
  `TRANSACT.BKUP.G0007`, `TRANSACT.DALY.G0007`, `TRANREPT.G0007` under
  `carddemo.batch.output-dir` (default `${java.io.tmpdir}/carddemo-batch`). Every run gets its own
  set and older runs stay readable, which is what a GDG base gave. `PRTCATBL` keeps a single fixed
  output name (`TCATBALF.REPT`), because its `DELDEF` step exists precisely to delete the previous
  one (FR-1451).
- **B-5 — abends go through the shared `AbendService`** (boundary register B-01) instead of
  `CEE3ABD`: code `0999`, culprit `CBTRN03C`, reason `FILE STATUS IS: NNNN0023`. The three legacy
  messages are logged verbatim before it (FR-1443), and the abend fails the step and the job.
- **B-6 — DFSORT becomes a tasklet, not a database `ORDER BY`.** Both sort steps read the unload
  file, apply the `INCLUDE`/`SORT`/`OUTREC` statements to the record images and write the result.
  That keeps the sort keys at their JCL column positions and keeps the sort stable, which matters
  for FR-1413.
- **B-7 — the duplicate step name.** `TRANREPT.jcl` names its first two steps `STEP05R`
  (TRANREPT.jcl:23 and :37). Spring Batch step names must be unique inside a job, so the sort step
  is `STEP05RSORT`; the order and the boundaries are unchanged.
- **B-8 — `PRTCATBL` writes 41 characters (C-1).** `OUTREC` and `LRECL=40` contradict each other in
  the shipped JCL; `OUTREC` is treated as the specification (FR-1454). Flagged, not reconciled.
- **B-9 — the per-record `DISPLAY` is not reproduced.** `CBTRN03C` displays every selected
  transaction record on SYSOUT (CBTRN03C.cbl:180). That is an operator diagnostic, not business
  output, and one log line per transaction is not something the consolidated application should
  emit; the job/step boundaries are logged instead. The start/end and `Reporting from` messages are
  logged. This is the only CBTRN03C output not reproduced verbatim.
- **B-10 — no schema change, so no Flyway migration.** The stream reads `TransactionRecord`,
  `CardXrefRecord`, `TransactionTypeRecord`, `TransactionCategoryRecord` and
  `TransactionCategoryBalanceRecord` from `com.carddemo.common.domain` and adds nothing. The S-14
  band `V1400`–`V1499` is left unused.
- **B-11 — no frontend.** S-14 is batch only; the screen that starts it (CR00) belongs to S-07. No
  route registry line, no component.

## 3. Legacy vs migrated — parity

Fixtures: `app/data/ASCII/` (`cardxref.txt` 50, `trantype.txt` 7, `trancatg.txt` 18,
`tcatbal.txt` 50, `dailytran.txt` 300 × 350 bytes). There is no TRANSACT unload in the fixture set,
so the parity input was derived from `dailytran.txt` by populating `TRAN-PROC-TS`, giving 300
transactions of which **180** fall in `2022-07-01 … 2022-07-06`. It is committed as
`backend/src/test/resources/tranreport/transact.txt`.

Harness (outside the repository, under `~/parity`): GnuCOBOL compiled `CBTRN03C` with
`-fsign=EBCDIC` — the fixture amounts are zoned with the sign overpunched on the last digit
(`0000001838H` = +183.88), and GnuCOBOL's default ASCII sign handling misreads exactly those bytes —
plus indexed copies of the three reference files. The legacy run's 133-byte report was split into
lines and committed as `backend/src/test/resources/tranreport/CBTRN03C_golden_report.txt`
(**375 records**).

| Path | Legacy | Migrated |
| --- | --- | --- |
| Whole report, `2022-07-01 … 2022-07-06`, 180 selected transactions | 375 × 133-byte records | identical, byte for byte (`TranReportJobTest.runsTheThreeJclStepsAndReproducesTheLegacyReport`) |
| Detail amount `+183.88` | `        183.80` | same — `S9(09)V99` truncates the third decimal |
| Detail amount `−1307.75` | `-      1,307.70` | same |
| Total field, large value | `+123,456,789.01` | same |
| Page break | page total + fresh header block every 20 written lines | same |
| Last account | no `Account Total` line (Q-2) | same |
| Closing totals | last amount counted twice (Q-1) | same |
| Launch through the port | CR00 → internal reader → same JCL | `TransactionReportLauncher.submit("Yearly", …)` produces the same 375 records |

The three lookup failures were exercised against the same fixture with a record removed from the
reference data; the legacy program abends 999 on the first miss and writes nothing further, and the
migrated step fails the job at the same record.

`PRTCATBL` has no COBOL to run: parity is against the `OUTREC` statement itself
(`CategoryBalanceSortTaskletTest`, `PrintCategoryBalanceJobTest`), including the unsigned negative
balance (Q-5) and the 41-character record (C-1).

## 4. Tests

`migration/carddemo/backend/src/test/java/com/carddemo/batch/tranreport`:

| Class | Covers |
| --- | --- |
| `TranReportPicturesTest` | FR-1424 — both pictures, zero suppression, signs, truncation |
| `TransactionRecordImageTest` | FR-1411 — the 350-byte image and its column positions |
| `TransactionExtractSortTaskletTest` | FR-1412, FR-1413 — inclusive window, card order, stability |
| `TransactionReportWriterTest` | FR-1421…FR-1433, FR-1441…FR-1443 — layout, paging, totals, quirks, abends |
| `TranReportJobTest` | FR-1401…FR-1405, FR-1444, FR-1461 — the job end to end, parity, the launch port and the S-07 seam |
| `CategoryBalanceSortTaskletTest` | FR-1452…FR-1455 |
| `PrintCategoryBalanceJobTest` | FR-1451, FR-1453, FR-1454, FR-1456 |

No test outside the lane needed changing: `batch/dataload/DataLoadJobTest` had to stop autowiring
`Job` by type once this stream registered two more jobs, but another parallel stream had already
made that change on the integration branch.

## 5. Deliberately deferred / not done

- **`TCATBALF.BKUP` generations.** `PRTCATBL`'s unload is a GDG in the JCL; migrated it is a single
  work file, because nothing reads it after the sort step and the report itself is what `DELDEF`
  manages. If the backup generations are an operational requirement, they are a one-line change to
  the file naming.
- **The `DATEPARM` dataset.** Not recreated as a file; the dates are job parameters (B-3).
- **Open/close I/O error messages.** `ERROR OPENING …` / `ERROR CLOSING …` describe VSAM opens that
  no longer exist; the underlying failures still fail the step, but the messages are not synthesised.
- **A REST endpoint for the report.** The stream exposes a launch port, not an API; whether CR00's
  replacement calls it directly or through a controller is S-07's decision.

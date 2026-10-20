# S-11 DailyTransactionPosting — the daily transaction batch chain (`DAILYTRN`)

How the mainframe's daily transaction chain runs in the consolidated application. Requirements:
FR-S11-029 – FR-S11-037 in `DailyTransactionPosting_functional_requirement.md`.

## 1. What the schedulers ran

The two scheduler exports do **not** agree on the daily chain:

| Source | Daily chain |
| --- | --- |
| Control-M, `app/scheduler/CardDemo.controlm`, folder `DAILY-TransactionBackup` | `CLOSEFIL → TRANBKP → WAITSTEP → OPENFIL` |
| CA-7, `app/scheduler/CardDemo.ca7`, SCHID 030 | `CLOSEFIL → CBPAUP0J → POSTTRAN → WAITSTEP → OPENFIL` |

Neither is the full picture: Control-M takes the backup but never posts; CA-7 posts but never backs up.
The migrated chain is the union, backup first so the master POSTTRAN replaces is saved before it is
emptied (`CBTRN02C` opens TRANSACT `OUTPUT`). This order is an inference, not something either export
states. `CLOSEFIL`/`OPENFIL` stay removed per B-15 (one shared database, no CICS files to quiesce;
`FileControlJobsRemovedTest`).

```
DAILYTRN:  TRANBKP → CBPAUP0J → POSTTRAN → WAITSTEP
```

## 2. Shape of the implementation

| Legacy | Migrated |
| --- | --- |
| The scheduler chain | `com.carddemo.batch.dailytrn.DailyTransactionChainJobConfiguration` — job `DAILYTRN`, one `JobStep` per child, each step named after its job |
| `TRANBKP.jcl` | `com.carddemo.batch.posttran.TransactionBackupJobConfiguration` — job `TRANBKP` |
| `STEP05R EXEC PROC=REPROC` (`REPRO` KSDS → `TRANSACT.BKUP(+1)`) | step `STEP05R`: `JpaPagingItemReader` over `transactions` by id → `TransactionMasterImage` (CVTRA05Y, 350 bytes) → `FlatFileItemWriter` |
| `TRANSACT.BKUP` GDG, `LIMIT(5) SCRATCH` (`DEFGDGB.jcl`) | `TransactionBackupGenerations` → `<backup-dir>/TRANSACT.BKUP.G<nnnn>V00`, five kept; `carddemo.batch.posttran.backup-dir` (default `./run/transact-bkup`) |
| `STEP05 EXEC PGM=IDCAMS` (`DELETE` cluster + AIX) | step `STEP05`: deletes every `transactions` row |
| `STEP10 EXEC PGM=IDCAMS` (`DEFINE` cluster) | nothing — the table and `idx_transactions_proc_ts` are schema |
| `CBPAUP0J`, `POSTTRAN`, `WAITSTEP` | the existing jobs, unchanged |

`RejectFileGenerations` and `TransactionBackupGenerations` now share `GenerationDataGroup`, the one
implementation of "a GDG as files in a directory" (D-5).

## 3. Parameters, failures, restarts

- `runDate` (`yyyy-MM-dd`) is required; it makes each business day one `DAILYTRN` job instance.
- Every `DAILYTRN` parameter is passed to every child unchanged (`expiryDays`, `chkpFreq` for CBPAUP0J;
  `sysin` for WAITSTEP), so each child also gets one instance per business day and keeps its own job
  repository history.
- A child that fails fails its chain step and the chain; nothing after it runs; the launcher exits 12.
- Relaunching `DAILYTRN` with the same parameters restarts it: completed children are skipped, the failed
  child restarts from its own last checkpoint (POSTTRAN from its last committed chunk, TRANBKP into the same
  backup generation).
- A business date that already completed is refused by the job repository
  (`JobInstanceAlreadyCompleteException`); nothing runs.
- POSTTRAN's condition code 4 for rejects is still the process exit code of a completed chain.

```
java -jar target/carddemo.jar --spring.main.web-application-type=none \
     --spring.profiles.active=postgres --spring.batch.job.name=DAILYTRN \
     --runDate=2022-06-15 --sysin=00000100
```

Without `--sysin`, WAITSTEP uses the shipped control card (`00003600`, 36 seconds).

## 4. Deviation

`TRANBKP.jcl` `STEP05` has no `COND`, so on z/OS a REPRO that ends with a non-zero return code without
abending still lets the DELETE run and the master is gone without a backup. Here `STEP05` only runs after
`STEP05R` completed (FR-S11-032).

## 5. Not done

- No enterprise scheduler is wired; `DAILYTRN` is the unit a scheduler would trigger once per business day.
- The chain order should be confirmed with operations, because the two scheduler exports disagree (§1).

## 6. Shared fixes the batch launch needed

Running the packaged jar exposed two pre-existing gaps that affected every batch job, not only DAILYTRN:

- `--spring.main.web-application-type=none` failed at startup: `CommareaSession` took an `HttpSession` in its
  constructor. It now takes `ObjectProvider<HttpSession>` and resolves the session on use.
- The process always exited 0: `CardDemoApplication.main` never called `SpringApplication.exit`, so the
  `ExitCodeGenerator` beans (`BatchJobLauncher` 12, `PostTranJobListener` 4) were ignored. Without a web
  server it now exits with their code; with one it keeps serving as before.

# S-12 InterestCalculation — migration plan

How the stream was implemented in `migration/carddemo/backend`, the boundary decisions taken, and the
legacy-vs-migrated parity of the main paths. Analysis: `InterestCalculation_analysis.md`; the sign-off
oracle: `InterestCalculation_functional_requirement.md`.

## 1. Scope implemented

| Legacy | Migrated |
|---|---|
| `app/jcl/INTCALC.jcl` `STEP15 EXEC PGM=CBACT04C,PARM='2022071800'` | Spring Batch job `INTCALC`, step `STEP15` |
| `app/jcl/COMBTRAN.jcl` `STEP05R EXEC PGM=SORT` + `STEP10 EXEC PGM=IDCAMS` | Spring Batch job `COMBTRAN`, steps `STEP05R` and `STEP10` |
| `app/cbl/CBACT04C.cbl` | `com.carddemo.batch.intcalc.InterestCalculator` and the reader/writer around it |

Everything lives in `com.carddemo.batch.intcalc` (the package the lane rules reserve for this stream).
No shared entity, no other stream's package, no Flyway migration and no CI file was touched: the shared
schema created by the Phase 0/1 foundation (`accounts`, `card_xref`, `transaction_category_balances`,
`disclosure_groups`, `transactions`) already carries every field CBACT04C reads or writes, so the
reserved `V1200`–`V1299` band is unused.

## 2. Implementation mapping

| COBOL / JCL | Java |
|---|---|
| TCATBALF sequential read (`1000-TCATBALF-GET-NEXT`) | JPA paging reader ordered by `(acctId, typeCd, catCd)`, folded into one item per account by `AccountBalanceGroupReader` (an `ItemStreamReader<AccountBalanceGroup>` over a `SingleItemPeekableItemReader`) |
| account break logic (`1050-UPDATE-ACCOUNT` on key change) | `AccountBalanceGroup` is the chunk item; the break is the group boundary, and `lastGroupInRun` is set by peeking at the next row |
| `1100-GET-ACCT-DATA`, `1110-GET-XREF-DATA` | `AccountRepository.findById`, `CardXrefRepository.findByAcctId`, once per group |
| `1200-GET-INTEREST-RATE` + `1200-A-GET-DEFAULT-INT-RATE` | `InterestCalculator.rateFor` — exact key, then the `DEFAULT` retry with the same type/category |
| `1300-COMPUTE-INTEREST` | `InterestCalculator.monthlyInterest` — `BigDecimal.divide(1200, 2, RoundingMode.DOWN)` |
| `1300-B-WRITE-TX` | `InterestCalculator.interestTransaction` + `TranIdSequence` + `Db2Timestamp` |
| `1400-COMPUTE-FEES` (empty) | nothing — deliberately, see §5 |
| `TRANSACT DD ... SYSTRAN(+1)`, `RECFM=F,LRECL=350` | `FlatFileItemWriter` of `TransactionRecordLine.format(...)` inside `InterestPostingWriter` |
| account rewrite (`REWRITE FD-ACCTFILE-REC`) | `AccountRepository.save` from the same writer |
| `CALL 'CEE3ABD'` | `com.carddemo.common.batch.AbendService` (boundary B-01) |
| `STEP05R` DFSORT: `SORTIN` = TRANSACT backup + SYSTRAN, `SORT FIELDS=(TRAN-ID,A)` | `CombineTransactionsTasklet`: format every `transactions` row, append the SYSTRAN lines, sort on the first 16 characters, write the combined file |
| `STEP10` IDCAMS `REPRO INFILE(TRANSACT) OUTFILE(TRANVSAM)` | `FlatFileItemReader` of the combined file → `JpaItemWriter<TransactionRecord>` |

`spring.batch.job.enabled=false` still holds: both jobs are launched by name through the shared
`BatchJobLauncher`, e.g. `--job=INTCALC --run.date=2022071800`.

## 3. Boundary decisions

| # | Decision |
|---|---|
| D-a | **The run date is a job parameter, never a clock read** (D-3). `run.date` is mandatory, exactly 10 characters, and is *not* parsed as a date — CBACT04C only concatenates it into transaction ids, and it accepts any 10 characters, so the migrated job does too. The record timestamps come from a separate `Clock` read, exactly as the COBOL takes them from `CURRENT-DATE`. |
| D-b | **GDG generations become files in a work directory.** `SYSTRAN(+1)` and `TRANSACT.COMBINED(+1)` map to `carddemo.batch.intcalc.work-dir` (`SystranFiles`), overridable per run with the `systran.file` / `combined.file` job parameters. Nothing else in the target consumes a sequential dataset, so no object-store seam was introduced (B-12 stays registered). |
| D-c | **The `transactions` table stands in for the `TRANSACT.BKUP` generation** that COMBTRAN concatenates: in the target the master *is* the database, so `STEP05R` reads the table instead of a backup unload, and `STEP10` writes back to it. The REPRO of records that were already on the master is therefore a no-op, which is what IDCAMS `REPRO` into an existing KSDS does for equal keys. |
| D-d | **Abend = failed step, exit code 12** (B-01) rather than a reject file: CBACT04C has no reject path, it `CALL 'CEE3ABD'`s. The whole chunk rolls back, so no half-posted account survives a failure. |
| D-e | **Restart uses the shared `JobRepository`.** The transaction-id suffix is kept in the step execution context so a restarted run continues numbering instead of reissuing ids; the COBOL had no restart at all, this is the target-state equivalent (FR-I13a). |
| D-f | **`CLOSEFIL` / `OPENFIL` around the chain are not migrated** (B-15): there is no VSAM to quiesce. `WAITSTEP`/`MVSWAIT` (B-04) is likewise scheduler business. |
| D-g | **Chunk size 50 accounts.** No COBOL equivalent — a commit-frequency choice, invisible to the results because the account break is the item boundary. |

## 4. Parity — main paths, against `app/data/ASCII/**`

The fixtures are 50 accounts, 50 TCATBALF rows (type `01`, category `0001`, **all balances zero**),
50 XREF rows and 51 disclosure-group rows.

| Path | Legacy behaviour | Migrated behaviour | Evidence |
|---|---|---|---|
| Rate lookup on the fixtures | `ACCT-GROUP-ID` is blank in `acctdata.txt`, so the exact key misses, `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` are displayed and the `DEFAULT`/`01`/`0001` row (15.00%) is used | identical: every fixture account takes the DEFAULT row | `InterestCalculatorTest.fallsBackToTheDefaultDisclosureGroup`, fixture run in `InterestCalculationJobIntegrationTest` |
| Full fixture run | 50 interest transactions, `2022071800000001`…`2022071800000050`, all amount `0.00` (zero balances), each `Int. for a/c 000000000nn` | identical, 350-byte records in generation order | `InterestCalculationJobIntegrationTest.writesTheSystranGeneration` |
| Arithmetic | `999.99 × 15 ÷ 1200 = 12.4998…` stored in `S9(09)V99` without `ROUNDED` → `12.49` | `12.49` (`RoundingMode.DOWN`), *not* the `12.50` half-up would give | `InterestCalculationJobIntegrationTest.runsAsIntcalcStep15AndPostsTruncatedInterest`, `InterestCalculatorTest.truncatesInterestTowardsZero` |
| Account posting | previous account's balance += its total interest, cycle credit and debit zeroed | identical | `InterestCalculatorTest.postsTotalInterestAndResetsTheCycleTotals` |
| Last account of the run | never updated (the end-of-file branch is unreachable), but its transactions are written | identical, deliberately | `InterestCalculationJobIntegrationTest.doesNotUpdateTheLastAccountOfTheRun` |
| Missing XREF row | `ACCOUNT NOT FOUND: 00000000001`, `ERROR READING XREF FILE`, abend | identical, step FAILED, nothing posted | `InterestCalculationJobFailureTest.abendsAndPostsNothingWhenACardXrefIsMissing` |
| COMBTRAN | master + SYSTRAN sorted ascending on `TRAN-ID` (character, 1–16), REPROed into the master | identical | `CombineTransactionsJobIntegrationTest` |

Because every fixture balance is zero, the arithmetic paths cannot be exercised by the fixtures alone;
the integration test seeds two accounts (`999.99` and `1000.00`) to produce the truncation case above,
and the per-rule arithmetic lives in `InterestCalculatorTest`.

## 5. Deferred, deliberately

- **Fees.** `1400-COMPUTE-FEES` (`CBACT04C.cbl:518-520`) is an empty paragraph in the source. No fee rule
  exists to migrate, so the job computes interest only (FR-I22). This is a gap in the *legacy* system, not
  in the migration; a fee rule would have to come from the business, not from the code.
- **`CLOSEFIL` / `WAITSTEP` / `OPENFIL`** — scheduler and VSAM concerns, see D-f (B-04, B-15).
- **Scheduler integration** (B-14): the jobs expose exit codes; the Control-M chain
  `CLOSEFIL → INTCALC → COMBTRAN → WAITSTEP → OPENFIL` is documented, not built.

## 6. Source vs. inventory contradictions

- `migration/carddemo/README.md` suggests `com.carddemo.batch.interest` for this stream while the stream
  brief reserves `com.carddemo.batch.intcalc`. The brief wins; the package is `intcalc`.
- The inventory lists TCATBALF, DISCGRP, ACCTDAT and TRANSACT for S-12 but not XREFFILE, which
  `CBACT04C` reads for every account (`CBACT04C.cbl:393-412`) and without which it abends. Implemented
  from the source.
- The inventory describes COMBTRAN as "combine system transactions with daily ones"; the JCL concatenates
  the TRANSACT **backup** and SYSTRAN, not the daily-transaction file (`DALYTRAN`). Implemented from the JCL.

## 7. Verification

```
cd migration/carddemo/backend && mvn -B test     # 172 tests, green
cd migration/carddemo/frontend && npm ci && npm run build
```

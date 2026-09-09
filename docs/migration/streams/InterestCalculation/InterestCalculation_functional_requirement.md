# S-12 InterestCalculation — functional requirements

The business sign-off oracle for the stream. Every requirement is traceable to `app/cbl/CBACT04C.cbl`,
`app/jcl/INTCALC.jcl` or `app/jcl/COMBTRAN.jcl`, and every requirement is covered by at least one test in
`migration/carddemo/backend/src/test/java/com/carddemo/batch/intcalc/**`.

Amounts are `S9(09)V99` (transaction) and `S9(10)V99` (account balance) — two decimals, signed.
"Abend" means the step fails and the process exits with condition code 12 (boundary B-01,
`com.carddemo.common.batch.AbendService`).

---

## A. INTCALC job (`CBACT04C`)

| # | Requirement | Source | Test |
|---|---|---|---|
| FR-I1 | The job runs as one job named `INTCALC` with one step named `STEP15`, launched only by name; it never starts implicitly. | `INTCALC.jcl` `STEP15 EXEC PGM=CBACT04C` | `InterestCalculationJobIntegrationTest.runsAsIntcalcStep15AndPostsTruncatedInterest` |
| FR-I2 | The run date is a required job parameter `run.date` of exactly 10 characters, taken from the JCL `PARM='2022071800'`. It is not read from the clock, is not validated as a date, and is used only to build transaction ids. | `CBACT04C.cbl:176-178,476-480` | `InterestCalculationJobFailureTest.rejectsARunDateThatIsNotTenCharacters`, `TranIdSequenceTest.usesTheRunDateVerbatim` |
| FR-I3 | Transaction-category balances are processed in ascending `(account id, transaction type code, transaction category code)` order — the TCATBALF KSDS key order. | `CBACT04C.cbl:28-32,63-66` | `AccountBalanceGroupReaderTest.readsInKeyOrder` |
| FR-I4 | All balance rows of one account are processed as one unit: the account row and the card cross-reference row are read once per account and reused for every category of that account. | `CBACT04C.cbl:194-206` | `AccountBalanceGroupReaderTest.groupsAllCategoriesOfAnAccount`, `InterestCalculatorTest.usesOneAccountAndXrefPerGroup` |
| FR-I5 | The interest rate for a category is the `disclosure_groups` row keyed by the account's group id, the balance's transaction type code and the balance's transaction category code. | `CBACT04C.cbl:210-213,415-420` | `InterestCalculatorTest.usesTheExactDisclosureGroupRate` |
| FR-I6 | If that row does not exist, the lookup is retried once with the group id `DEFAULT` (padded to 10 characters) and the *same* type and category codes; the messages `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE` are logged. | `CBACT04C.cbl:418-419,436-439,443-460` | `InterestCalculatorTest.fallsBackToTheDefaultDisclosureGroup` |
| FR-I7 | If the `DEFAULT` row does not exist either, the run abends after logging `ERROR READING DEFAULT DISCLOSURE GROUP`. | `CBACT04C.cbl:444-459` | `InterestCalculatorTest.abendsWhenTheDefaultDisclosureGroupIsMissing` |
| FR-I8 | A rate of zero produces no interest and no transaction for that category; the balance is skipped entirely. | `CBACT04C.cbl:214-217` | `InterestCalculatorTest.skipsCategoriesWithAZeroRate` |
| FR-I9 | Monthly interest = `balance × rate ÷ 1200`, **truncated** (toward zero) to two decimals — never rounded half-up. | `CBACT04C.cbl:462-465` (`COMPUTE` without `ROUNDED`, `WS-MONTHLY-INT PIC S9(09)V99`) | `InterestCalculatorTest.truncatesInterestTowardsZero`, `InterestCalculatorTest.negativeBalancesTruncateTowardsZero` |
| FR-I10 | A non-zero rate on a zero balance produces a transaction with amount `0.00`. | `CBACT04C.cbl:214,462-468` | `InterestCalculatorTest.writesAZeroAmountTransactionForAZeroBalance` |
| FR-I11 | The account's total interest is the sum of the already-truncated per-category amounts. | `CBACT04C.cbl:467` | `InterestCalculatorTest.totalInterestIsTheSumOfTheTruncatedAmounts` |
| FR-I12 | Each computed interest amount produces one transaction record with: type `01`, category `5`, source `System`, description `Int. for a/c ` + the 11-digit zero-padded account id, amount = the truncated interest, merchant id `0`, blank merchant name, city and zip, and the account's cross-reference card number. | `CBACT04C.cbl:482-495` | `InterestCalculatorTest.buildsTheInterestTransaction` |
| FR-I13 | The transaction id is the 10-character run date followed by a 6-digit zero-padded sequence number that starts at `000001` and increments once per generated transaction across the whole run (not per account). | `CBACT04C.cbl:173,474-480` | `TranIdSequenceTest.numbersTransactionsDenselyAcrossAccounts` |
| FR-I13a | The transaction-id sequence survives a restart: it is kept in the step execution context, so a restarted run continues numbering rather than reissuing ids. | Spring Batch restart semantics over `CBACT04C.cbl:173` | `TranIdSequenceTest.resumesFromTheExecutionContextOnRestart` |
| FR-I14 | Both timestamp fields of a generated transaction carry the same value, the run clock time formatted as `YYYY-MM-DD-HH.MM.SS.hh0000` (26 characters, hundredths of a second then four zeros). | `CBACT04C.cbl:496-498,613-626` | `Db2TimestampTest.formatsTheCurrentDateLikeCbact04c` |
| FR-I15 | When the account changes, the *previous* account is updated: its current balance is increased by that account's total interest, and its current-cycle credit and debit are both set to zero. | `CBACT04C.cbl:194-196,350-356` | `InterestCalculatorTest.postsTotalInterestAndResetsTheCycleTotals`, `InterestCalculationJobIntegrationTest.runsAsIntcalcStep15AndPostsTruncatedInterest` |
| FR-I16 | **Quirk (preserved):** the last account in the run is never updated — no interest is posted to its balance and its cycle totals are not reset — although its transactions are still generated. The COBOL's end-of-file update branch is unreachable. | `CBACT04C.cbl:188-191,219-220` | `InterestCalculatorTest.lastAccountOfTheRunIsNeverUpdated`, `InterestCalculationJobIntegrationTest.doesNotUpdateTheLastAccountOfTheRun` |
| FR-I17 | An account row missing for a balance row abends the run after logging `ACCOUNT NOT FOUND: ` with the account id and `ERROR READING ACCOUNT FILE`. There is no reject file. | `CBACT04C.cbl:372-390` | `InterestCalculatorTest.abendsWhenTheAccountIsMissing` |
| FR-I18 | A cross-reference row missing for an account abends the run after logging `ACCOUNT NOT FOUND: ` with the account id and `ERROR READING XREF FILE`. | `CBACT04C.cbl:393-412` | `InterestCalculatorTest.abendsWhenTheCardXrefIsMissing`, `InterestCalculationJobFailureTest.abendsAndPostsNothingWhenACardXrefIsMissing` |
| FR-I19 | The generated transactions are written to the sequential `SYSTRAN` output as fixed 350-byte records in `CVTRA05Y` layout, in generation order; INTCALC never writes to the transaction master. | `INTCALC.jcl` `TRANSACT DD ... SYSTRAN(+1)`, `RECFM=F,LRECL=350`; `CBACT04C.cbl:53-56,500` | `TransactionRecordLineTest.formatsA350ByteCvtra05yRecord`, `InterestCalculationJobIntegrationTest.writesTheSystranGeneration` |
| FR-I20 | The run logs `START OF EXECUTION OF PROGRAM CBACT04C` before processing and `END OF EXECUTION OF PROGRAM CBACT04C` after it, and logs each balance record it reads. | `CBACT04C.cbl:181,193,230` | `InterestCalculationJobIntegrationTest.logsTheProgramBanners` |
| FR-I21 | Any abend fails the step and yields exit code 12; no partial account update of the failing chunk is committed. | `CBACT04C.cbl:628-632`; B-01 | `InterestCalculationJobFailureTest.abendsAndPostsNothingWhenACardXrefIsMissing` |
| FR-I22 | **Deferred behaviour, faithfully:** no fee is computed. `1400-COMPUTE-FEES` is empty in the source, so the migrated job computes interest only. | `CBACT04C.cbl:518-520` | `InterestCalculatorTest.computesNoFees` |

## B. COMBTRAN job

| # | Requirement | Source | Test |
|---|---|---|---|
| FR-C1 | The job runs as one job named `COMBTRAN` with two steps, `STEP05R` (sort/merge) and `STEP10` (load), in that order. | `COMBTRAN.jcl` | `CombineTransactionsJobIntegrationTest.runsBothStepsAndSortsTheCombinedFileByTransactionId` |
| FR-C2 | `STEP05R` merges the transaction-master backup with the `SYSTRAN` generation written by INTCALC and writes one combined 350-byte sequential file. | `COMBTRAN.jcl` `SORTIN` concatenation | `CombineTransactionsJobIntegrationTest.combinesTheMasterWithSystranAndReprosItBack`, `CombineTransactionsJobIntegrationTest.loadsTheInterestTransactionsInterestCalculationJustGenerated` |
| FR-C3 | The combined file is sorted ascending by the 16-character transaction id, compared as characters (position 1, length 16), matching the KSDS key order. | `COMBTRAN.jcl` `SYMNAMES TRAN-ID,1,16,CH`, `SORT FIELDS=(TRAN-ID,A)` | `CombineTransactionsJobIntegrationTest.runsBothStepsAndSortsTheCombinedFileByTransactionId` |
| FR-C4 | `STEP10` loads every record of the combined file into the transaction master; re-loading a record that is already there leaves one row with the loaded values (the REPRO of a backup that came from the master is a no-op). | `COMBTRAN.jcl` `REPRO INFILE(TRANSACT) OUTFILE(TRANVSAM)` | `CombineTransactionsJobIntegrationTest.combinesTheMasterWithSystranAndReprosItBack` |
| FR-C5 | A record read back from the combined file round-trips every `CVTRA05Y` field, including signed amounts written with a zoned-decimal overpunch sign and space-padded text fields. | `CVTRA05Y`; `app/data/ASCII/dailytran.txt` encoding | `TransactionRecordLineTest.roundTripsEveryField` |
| FR-C6 | If the `SYSTRAN` generation for the run date does not exist, the job fails rather than silently loading only the backup. | `COMBTRAN.jcl` `SORTIN DD DISP=SHR` (a missing dataset is a JCL allocation failure) | `CombineTransactionsJobIntegrationTest.failsWhenTheSystranGenerationIsMissing` |

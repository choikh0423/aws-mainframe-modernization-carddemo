# S-14 TransactionReporting — functional requirements

The business sign-off oracle for the stream. Every requirement is traceable to the COBOL/JCL source
and covered by an automated test in `com.carddemo.batch.tranreport`
(`migration/carddemo/backend/src/test/java/com/carddemo/batch/tranreport`).

Per-program detail: [`programs/CBTRN03C_functional_requirement.md`](programs/CBTRN03C_functional_requirement.md),
[`programs/PRTCATBL_functional_requirement.md`](programs/PRTCATBL_functional_requirement.md).

Conventions: **Q-n** marks a preserved legacy quirk, **C-n** a contradiction in the shipped source.

---

## FR-1400 Jobs and launch contract

| # | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-1401 | The daily transaction report runs as the batch job named `TRANREPT`, with one step per `EXEC PGM=` of the JCL, in the order `STEP05R` (unload TRANSACT) → `STEP05RSORT` (filter and sort) → `STEP10R` (write the report). | TRANREPT.jcl:23, :37, :59 | `TranReportJobTest.runsTheThreeJclStepsAndReproducesTheLegacyReport` |
| FR-1402 | `TRANREPT` takes three job parameters — `reportType`, `startDate`, `endDate` (`YYYY-MM-DD`) — and refuses to start if any is missing. | CORPT00C.cbl:214, :240, :433, :105-112 | `TranReportJobTest.refusesToStartWithoutTheReportTypeAndDateRange` |
| FR-1403 | `reportType` (`Monthly`, `Yearly`, `Custom`) is recorded on the run but does not change the report: the legacy JCL and CBTRN03C never read it. | CORPT00C.cbl:449-452; TRANREPT.jcl (no reference) | `TranReportJobTest.launchesTheJobThroughTheReportLaunchPort` |
| FR-1404 | The report can be launched in process by S-07's CR00 replacement through a launch port that takes the same three values and returns the run's status and the path of the report it produced, bound to S-07's `TransactionReportJobLauncher` seam in place of its stand-in. | CORPT00C.cbl:462-521 (`SUBMIT-JOB-TO-INTRDR`, `WRITEQ TD QUEUE('JOBS')`) | `TranReportJobTest.launchesTheJobThroughTheReportLaunchPort`, `.bindsTheReportingSeamToTheJob` |
| FR-1405 | Every submission is a new run, even for a range that was reported before, and it produces its own report file — the internal reader started a fresh job on every confirmation. | CORPT00C.cbl:462-521 | `TranReportJobTest.submitsANewJobInstanceForARangeAlreadyReported` |
| FR-1406 | Printing the transaction category balance file runs as the batch job named `PRTCATBL`, with the steps `DELDEF` → `STEP05R` → `STEP10R` and no parameters. | PRTCATBL.jcl:21, :29, :43 | `PrintCategoryBalanceJobTest.runsTheThreeJclStepsAndPrintsEveryCategoryBalance` |

## FR-1410 Unload and extract (TRANREPT `STEP05R`, `STEP05RSORT`)

| # | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-1411 | The unload step writes every posted transaction as a 350-byte fixed-width record, in transaction-id order, with the fields at the columns the SORT step addresses. | TRANREPT.jcl:26-33 (`REPRO`, `LRECL=350`); CVTRA05Y.cpy | `TransactionRecordImageTest.unloadsARowIntoTheColumnsTheSortStepAddresses`, `.roundTripsEveryFixtureRecord`, `.keepsTheCardNumberAtItsFullWidth`, `.writesTheSignAsAZonedOverpunch`, `TranReportJobTest.runsTheThreeJclStepsAndReproducesTheLegacyReport` |
| FR-1412 | The extract keeps the records whose processing date (columns 305-314) is between `startDate` and `endDate` **inclusive at both ends**. | TRANREPT.jcl:42, :47-48 | `TransactionExtractSortTaskletTest.keepsTheProcessingDatesInsideTheRangeAtBothEnds`, `.selectsTheReportedRecordsOutOfTheWholeFixture`, `TranReportJobTest.reportsOnlyTheRequestedDateRange` |
| FR-1413 | The extract is ordered by card number (columns 263-278) ascending, and records with the same card number keep their unload order. | TRANREPT.jcl:41, :46 | `TransactionExtractSortTaskletTest.sortsByCardNumberAscending`, `.keepsTheUnloadOrderWithinACard` |

## FR-1420 The report (TRANREPT `STEP10R`, CBTRN03C)

| # | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-1421 | Every report record is exactly 133 characters. | TRANREPT.jcl:78; CVTRA07Y.cpy | `TransactionReportWriterTest.everyRecordIs133Bytes`, `TranReportJobTest.reportsOnlyTheRequestedDateRange` |
| FR-1422 | A page begins with `DALYREPT`, `Daily Transaction Report`, `Date Range: <start> to <end>`, a blank line, the column header line and a rule line, at the copybook's field positions and with the labels verbatim. | CVTRA07Y.cpy:4-48; CBTRN03C.cbl:274-280, :324-341 | `TransactionReportWriterTest.writesTheHeaderBlockWithTheReportedDateRange` |
| FR-1423 | A detail line carries transaction id, account id (from the card cross-reference), `<type code>-<type description>`, `<category code>-<category description>`, source and amount, in that order and at those widths. | CVTRA07Y.cpy:15-31; CBTRN03C.cbl:361-374 | `TransactionReportWriterTest.laysOutTheDetailLineFieldByField` |
| FR-1424 | Detail amounts print as `-ZZZ,ZZZ,ZZZ.ZZ` (blank sign when not negative), totals as `+ZZZ,ZZZ,ZZZ.ZZ` (`+` when not negative); both blank leading zeros down to the decimal point, print `.00` for zero pence and truncate rather than round. | CVTRA07Y.cpy:30, :54, :60, :66 | `TranReportPicturesTest` (all six tests) |
| FR-1425 | After every 20 written lines the report closes the page with `Page Total....` and starts a new header block; the line counter counts headers, rules and total lines as well as details. | CBTRN03C.cbl:129-132, :282-285, :293-304 | `TransactionReportWriterTest.writesAPageTotalAndFreshHeadersEveryTwentyLines` |
| FR-1426 | When the card number changes, `Account Total....` for the previous card is written, followed by a rule line, and the account total restarts at zero. | CBTRN03C.cbl:181-184, :306-317 | `TransactionReportWriterTest.writesAnAccountTotalWhenTheCardNumberChanges` |
| FR-1427 | At the end of the extract the report writes a final page total and then `Grand Total....`; the grand total is the sum of the page totals. | CBTRN03C.cbl:198-203, :318-322 | `TransactionReportWriterTest.accumulatesTheGrandTotalOutOfThePageTotals`, `.countsTheLastRecordTwiceInTheClosingTotals` |
| FR-1428 | **Q-1** The amount of the last in-range transaction is added to the page and account totals a second time at end of file, so the closing page total and the grand total exceed the sum of the detail lines by that amount. Preserved, not fixed. | CBTRN03C.cbl:198-201 | `TransactionReportWriterTest.countsTheLastRecordTwiceInTheClosingTotals` |
| FR-1429 | **Q-2** The last card in the extract never gets an `Account Total` line, because account totals are written only on a change of card number. Preserved, not fixed. | CBTRN03C.cbl:181-184 | `TransactionReportWriterTest.writesAnAccountTotalWhenTheCardNumberChanges` |
| FR-1430 | **Q-3** The date window is applied twice — once by the SORT step, once by the report writer against the run's own dates — and a record outside the window is skipped silently. | TRANREPT.jcl:47-48; CBTRN03C.cbl:173-178 | `TransactionReportWriterTest.skipsTransactionsOutsideTheReportedRange` |
| FR-1431 | **Q-4** If the last record of the extract is outside the window, the run ends with no closing page total and no grand total (`NEXT SENTENCE` skips the end-of-file branch). Preserved, not fixed. | CBTRN03C.cbl:173-178, :198-203 | `TransactionReportWriterTest.writesNoClosingTotalsWhenTheLastRecordIsOutOfRange` |
| FR-1432 | If nothing is selected, the report is empty: no headers, no totals — the header block is written from the first detail line, not at open time. | CBTRN03C.cbl:274-280 | `TransactionReportWriterTest.writesAnEmptyReportWhenNothingIsSelected` |
| FR-1433 | A failed report step leaves no totals appended to the report. | CBTRN03C.cbl:198-203 (only reached at a clean end of file) | `TransactionReportWriterTest.writesNoTotalsWhenTheStepFails` |

## FR-1440 Reference data and failure

| # | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-1441 | The account id on a detail line comes from the card cross-reference, looked up once per card number, at the change of card. | CBTRN03C.cbl:181-188, :484-492 | `TransactionReportWriterTest.laysOutTheDetailLineFieldByField`, `.writesAnAccountTotalWhenTheCardNumberChanges` |
| FR-1442 | The type description comes from the transaction type reference data keyed by the 2-character type code, and the category description from the transaction category reference data keyed by type code + 4-digit category code. | CBTRN03C.cbl:189-195, :494-511 | `TransactionReportWriterTest.laysOutTheDetailLineFieldByField` |
| FR-1443 | A missing cross-reference, type or category record abends the run with abend code `0999`, culprit `CBTRN03C`, reason `FILE STATUS IS: NNNN0023` and the legacy message `INVALID CARD NUMBER : <card>`, `INVALID TRANSACTION TYPE : <code>` or `INVALID TRAN CATG KEY : <type><category>`. There is no skip-and-continue path. | CBTRN03C.cbl:484-511, :627-631, :633-644 | `TransactionReportWriterTest.abendsWhenTheCardIsNotInTheCrossReference`, `.abendsWhenTheTransactionTypeIsNotFound`, `.abendsWhenTheTransactionCategoryIsNotFound`, `TranReportJobTest.failsTheReportStepWhenALookupMisses` |
| FR-1444 | An abend fails the step and therefore the job, so the run is not reported as successful. | CBTRN03C.cbl:627-631 (`CEE3ABD`); `.migration/04_boundary_register.md` B-01 | `TranReportJobTest.failsTheReportStepWhenALookupMisses` |

## FR-1450 PRTCATBL

| # | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-1451 | The previous category-balance report is deleted before a new one is written, so a run never appends to it. | PRTCATBL.jcl:21-25 | `PrintCategoryBalanceJobTest.deletesThePreviousReportBeforeWritingANewOne` |
| FR-1452 | The unload writes every category balance as a 50-byte record: account id (11, zero-padded), type code (2), category code (4, zero-padded), balance (11, zoned with the sign overpunched on the last digit). | PRTCATBL.jcl:37, :47-50; CVTRA01Y.cpy | `CategoryBalanceSortTaskletTest.writesTheFiftyByteUnloadImage`, `.writesNegativeBalancesWithAnOverpunchSign` |
| FR-1453 | The report is ordered by account id, then transaction type code, then category code, all ascending. | PRTCATBL.jcl:52 | `CategoryBalanceSortTaskletTest.sortsByAccountThenTypeThenCategory`, `PrintCategoryBalanceJobTest.runsTheThreeJclStepsAndPrintsEveryCategoryBalance` |
| FR-1454 | Each printed record is `<acct 11> <type 2> <cat 4> <balance nnnnnnnnn.nn>` followed by 9 blanks — 41 characters, with no zero suppression in the balance. **C-1**: the JCL's `SORTOUT` declares `LRECL=40`, which contradicts its own `OUTREC`; the `OUTREC` content is taken as the specification. | PRTCATBL.jcl:53-56 vs :61 | `CategoryBalanceSortTaskletTest.formatsTheOutrecRecordAsFortyOneCharacters`, `PrintCategoryBalanceJobTest.runsTheThreeJclStepsAndPrintsEveryCategoryBalance` |
| FR-1455 | **Q-5** A negative balance prints exactly like a positive one: `EDIT=(TTTTTTTTT.TT)` has no sign position. Preserved, not fixed. | PRTCATBL.jcl:56 | `CategoryBalanceSortTaskletTest.printsNegativeBalancesWithoutASign` |
| FR-1456 | Every category balance in the file is printed — the job has no filter. | PRTCATBL.jcl:43-56 | `PrintCategoryBalanceJobTest.runsTheThreeJclStepsAndPrintsEveryCategoryBalance` |

## FR-1460 Parity

| # | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-1461 | For the same input data and date range, the migrated `TRANREPT` produces the daily transaction report byte for byte as the legacy `CBTRN03C` produces it. | Parity run, see the migration plan | `TranReportJobTest.runsTheThreeJclStepsAndReproducesTheLegacyReport`, `.launchesTheJobThroughTheReportLaunchPort` |

# S-17 OperationsChain — functional requirements

Sign-off oracle for the stream. Every requirement cites the source it comes from and names the test that
proves it. Tests live in `migration/carddemo/backend/src/test/java/com/carddemo/batch/operations/`.

Legend: **source** = the legacy artefact the requirement is derived from; **test** = the method that
fails if the requirement is broken.

## A. WAITSTEP / COBSWAIT (B-04)

| # | Requirement | Source | Test |
|---|---|---|---|
| FR-OC-01 | The wait is a batch job named `WAITSTEP` with a single step named `WAIT`, runnable with `--spring.batch.job.name=WAITSTEP`. | `WAITSTEP.jcl:1` (job name), `:22` (step `//WAIT`) | `WaitStepJobTest.jobAndStepAreNamedAfterTheJclTheyReplace`, `…waitsForTheRequestedNumberOfCentisecondsAndCompletes` |
| FR-OC-02 | Only the first record of the control card input is read; further records are ignored. | `COBSWAIT.cbl:36` — `ACCEPT … FROM SYSIN` reads one record | `WaitControlCardTest.readsOnlyTheFirstRecord` |
| FR-OC-03 | Only columns 1–8 of that record carry the value; columns 9+ are commentary and are ignored. | `COBSWAIT.cbl:31` (`PARM-VALUE PIC X(8)`), `WAITSTEP.jcl:26` (`00003600      VALUE IN CENTISECONDS`) | `WaitControlCardTest.ignoresEverythingAfterColumnEight`, `…readsTheEightValueColumnsAsCentiseconds` |
| FR-OC-04 | With no control card supplied, the job uses the card shipped in the JCL, `00003600      VALUE IN CENTISECONDS`, i.e. 3 600 centiseconds. | `WAITSTEP.jcl:25-26` | `WaitStepJobTest.defaultsToTheControlCardShippedWithTheJcl` |
| FR-OC-05 | The value is a count of **centiseconds** (1 cs = 10 ms); `00003600` is 36 seconds, not 3 600. | `WAITSTEP.jcl:20` ("EG: 00003600 = 36 SECONDS"), `COBSWAIT.cbl:5` | `WaitControlCardTest.readsTheEightValueColumnsAsCentiseconds` |
| FR-OC-06 | A value of `00000000` waits for no time and completes. | `COBSWAIT.cbl:37-38` — zero is passed to the timer unmodified | `WaitControlCardTest.zeroWaitsNotAtAll` |
| FR-OC-07 | The job waits at least the requested duration and then completes normally (exit code 0). | `MVSWAIT.asm:23` (interval timer), `:26` (R15 zeroed — the wait cannot fail), `COBSWAIT.cbl:40` (`STOP RUN`) | `WaitStepJobTest.waitsForTheRequestedNumberOfCentisecondsAndCompletes` |
| FR-OC-08 | The largest accepted value is `99999999` centiseconds, the capacity of `MVSWAIT-TIME`. | `COBSWAIT.cbl:30` (`PIC 9(8) COMP`) | `WaitControlCardTest.acceptsTheLargestValueMvswaitTimeCanHold` |
| FR-OC-09 | A card whose columns 1–8 are not all digits is rejected. | `COBSWAIT.cbl:37` — an alphanumeric-to-`COMP` MOVE of non-numeric data is undefined on z/OS; see the deviation note below | `WaitControlCardTest.rejectsAValueThatIsNotEightDigits` |
| FR-OC-10 | A rejected card abends the step, so the job ends FAILED and the process exits 12. | `com.carddemo.batch.BatchJobLauncher.EXIT_CODE_FAILED`, target state D-6 | `WaitStepJobTest.failsTheJobWhenTheControlCardIsNotNumeric` |
| FR-OC-11 | A card shorter than eight characters, including an empty one, is rejected — it is not treated as zero or right-aligned. | `COBSWAIT.cbl:31` — short data is blank-filled to `X(8)`, and blanks are not digits (FR-OC-09) | `WaitControlCardTest.rejectsAShortCard`, `…rejectsAnEmptySysin` |
| FR-OC-12 | The job reads and writes no business data. | `COBSWAIT.cbl:25` (empty `INPUT-OUTPUT SECTION`), no FD, no `OPEN`/`READ`/`WRITE` | `WaitStepJobTest.waitsForTheRequestedNumberOfCentisecondsAndCompletes` (read and write counts are zero) |

**Deviation, recorded deliberately (FR-OC-09/FR-OC-10).** COBSWAIT validates nothing: a non-numeric card
is MOVEd into a binary field with an undefined result, and the job would then wait for an arbitrary
length of time — potentially days — with return code 0. That is not behaviour that can be reproduced
faithfully (it has no defined outcome) and reproducing the *worst* reading of it would hang a migrated
chain silently. The target rejects the card instead. This is the only place where S-17 does not preserve
legacy behaviour; every other requirement above is a faithful reproduction.

**Quirks preserved.** The unit is centiseconds although both the program comment (`COBSWAIT.cbl:5`) and
the JCL comment (`WAITSTEP.jcl:20`) say the value arrives in a `PARM=` — it does not, it comes from SYSIN
(FR-OC-02). The commentary text after column 8 is retained verbatim in the packaged control card.

## B. CLOSEFIL / OPENFIL (B-15)

| # | Requirement | Source | Test |
|---|---|---|---|
| FR-OC-20 | No file open/close (VSAM quiescing) job or equivalent exists in the target: no job named `OPENFIL`, `CLOSEFIL`, `CLOSEFIL1` or `CLOSEFIL2` is registered, and nothing in the target quiesces storage before a batch job. | `CLOSEFIL.jcl:22-30`, `OPENFIL.jcl:22-30` — the entire content of both jobs is five CEMT commands against the CICS region `CICSAWSA`, which has no counterpart in the target (target state D-2: one shared PostgreSQL database, no CICS) | `FileControlJobsRemovedTest.noFileOpenOrCloseJobExistsInTheTarget` |

Reasoning, per B-15: the two jobs exist only because CICS holds the VSAM datasets open and a batch step
cannot update them concurrently. The target has a single shared database with transactional concurrency
control, so there is no dataset to release and nothing to re-enable afterwards. Building an equivalent
would mean inventing an availability toggle the legacy system never had a business rule for. The five
file names map to tables owned by other streams: `ACCTDAT`, `CCXREF`, `CXACAIX`, `TRANSACT`, `USRSEC`.

## C. Scheduler orchestration (B-14)

| # | Requirement | Source | Test |
|---|---|---|---|
| FR-OC-30 | The documented Control-M dependency graph is exactly the graph defined in the export: 12 edges over four folders, each released by the named condition. | `app/scheduler/CardDemo.controlm` | `OperationsChainScheduleTest.controlMGraphMatchesTheFolderDefinitions` |
| FR-OC-31 | The documented CA-7 dependency graph is exactly the graph in the LJOB listing: 28 triggers in listing order, each with its SCHID. | `app/scheduler/CardDemo.ca7` | `OperationsChainScheduleTest.ca7GraphMatchesTheLjobListing` |
| FR-OC-32 | The orchestration contract document lists every trigger of both schedulers, so the other batch streams' schedules can be checked against it. | both exports | `OperationsChainScheduleTest.contractDocumentListsEveryTrigger` |
| FR-OC-33 | Migrated jobs signal completion by process exit code only — 0 on completion, 12 otherwise — because no scheduler edge in either export tests a condition code. | `CardDemo.controlm` (`INCOND`/`OUTCOND`, no code tests), `CardDemo.ca7` (TRIGGERED JOBS on completion) | `WaitStepJobTest.waitsForTheRequestedNumberOfCentisecondsAndCompletes` (COMPLETED) and `…failsTheJobWhenTheControlCardIsNotNumeric` (FAILED → exit 12) |

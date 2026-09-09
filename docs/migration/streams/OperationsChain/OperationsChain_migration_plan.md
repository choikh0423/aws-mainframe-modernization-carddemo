# S-17 OperationsChain — migration plan

## 1. What was built

| Artefact | Purpose |
|---|---|
| `com.carddemo.batch.operations.WaitControlCard` | Reads the SYSIN card exactly as COBSWAIT does (FR-OC-02, -03, -05, -06, -08, -09, -11) |
| `com.carddemo.batch.operations.WaitStepJobConfiguration` | Job `WAITSTEP`, one step `WAIT` — the migrated WAITSTEP.jcl (FR-OC-01, -04, -07, -10, -12) |
| `src/main/resources/ctl/WAITSTEP.sysin` | The in-stream SYSIN of WAITSTEP.jcl, verbatim, as the default control card |
| `com.carddemo.batch.operations.OperationsChainSchedule` | The Control-M and CA-7 dependency graphs as data, so the contract can be machine-checked (FR-OC-30, -31) |
| `OperationsChain_orchestration_contract.md` | The B-14 contract other batch streams check their schedules against (FR-OC-32) |
| Analysis and FR documents | Source-cited analysis and the sign-off oracle |

No Flyway migration: the stream owns no data, so its reserved band `V1700`–`V1799`
(`migration/carddemo/README.md:84`) is untouched. No frontend change: S-17 is BATCH-only, it has no BMS
map and no CICS transaction, so no component and no route-registry line.

Package: `com.carddemo.batch.operations`, the package the README assigns to S-17
(`migration/carddemo/README.md:37`). The task brief said `com.carddemo.batch.ops`; the README is the
stated contract for parallel sessions and other streams reference it, so the README name was used and the
discrepancy is reported rather than resolved silently. Either way the code is inside this stream's lane
and no other stream's package was touched.

### One change outside the stream's own files

`backend/src/test/java/com/carddemo/batch/dataload/DataLoadJobTest.java` gained three lines: it autowires
`dataLoadJob` by qualifier and calls `jobLauncherTestUtils.setJob(dataLoadJob)`. `JobLauncherTestUtils`
resolves its job by type, so it fails with "The Job must not be null" as soon as a *second* stream
registers a `Job` bean — which S-17 is the first to do, and every remaining batch stream would hit too.
The change is in the foundation's own test, touches no production code and no assertion, and unblocks
S-11…S-20 rather than only this stream. New batch streams should follow `WaitStepJobTest` instead:
autowire the job by `@Qualifier` and launch it through `JobLauncher` directly.

## 2. Boundary decisions

### B-15 — CICS file open/close: removed, not migrated

`CLOSEFIL` and `OPENFIL` are five CEMT commands each against the CICS region `CICSAWSA`
(`CLOSEFIL.jcl:26-30`, `OPENFIL.jcl:26-30`). They exist because CICS holds VSAM datasets open and a batch
step cannot update them concurrently: the chain closes them, runs, waits, reopens them. The target has
one shared PostgreSQL database with transactional concurrency control (target state D-2), so there is no
dataset to release, no region to command, and no availability window to manage. Nothing was built. The
migrated schedule drops the jobs and connects each CLOSEFIL/OPENFIL predecessor directly to its
successor.

The decision is enforced, not merely written down: `FileControlJobsRemovedTest` fails if any job named
`OPENFIL`, `CLOSEFIL`, `CLOSEFIL1` or `CLOSEFIL2` is ever registered in the application context, so a
later stream cannot quietly reintroduce a quiescing step.

### B-04 — `CALL 'MVSWAIT'`: minimal job provided, and why

The boundary register says the wait "becomes a scheduler-level wait; the migrated batch has no equivalent
step". Long term that is right and this plan does not argue with it. A job was nevertheless provided,
minimally, for one reason: WAITSTEP is an endpoint of 21 of the 40 edges in the two scheduler graphs, and
it is the only S-17 artefact that other streams' chains actually run *through*. Dropping it before any
target scheduler exists would leave those chains with a named job that does not resolve. The job is
therefore the interim: it performs the wait itself, exactly as documented in the JCL, and the contract
says that a scheduler able to express a delay should express it there and drop the job.

The Assembler module is not ported (B-03/B-04): `MVSWAIT` is a register-level wrapper around the MVS
interval timer (`MVSWAIT.asm:17-30`) with no business logic, and its only observable behaviour — elapse
an interval, return 0 — is a thread sleep in the target.

### B-14 — scheduler graphs: documented as the orchestration contract

Both exports were parsed and reproduced edge by edge in `OperationsChain_orchestration_contract.md`, with
the Control-M condition name or the CA-7 SCHID on every edge. Because this document is the reference the
other batch streams are checked against, it is kept honest mechanically:
`OperationsChainScheduleTest` re-parses `app/scheduler/CardDemo.controlm` (XML `INCOND`/`OUTCOND`) and
`app/scheduler/CardDemo.ca7` (LJOB "TRIGGERED JOBS" blocks) on every build and fails if the exports, the
`OperationsChainSchedule` class or the markdown tables disagree. Editing the document without editing the
scheduler export breaks the build, and vice versa.

No scheduler is implemented (B-14 explicitly: "the migrated jobs expose exit codes only"). Neither export
tests a return code anywhere, so completion signalling in the target is exit code 0 / 12 via
`BatchJobLauncher` (FR-OC-33).

## 3. Deliberate deviation from legacy behaviour

One, FR-OC-09/FR-OC-10: a control card whose value columns are not eight digits is rejected and abends
the step. COBSWAIT has no validation at all (`COBSWAIT.cbl:34-40`) — it MOVEs the characters into a
`PIC 9(8) COMP` field, which for non-numeric data is undefined on z/OS, and then waits for whatever value
that produced with return code 0. There is no defined behaviour to reproduce, and the plausible readings
are all bad (a chain hanging for hours, silently). Rejecting the card fails fast and visibly instead.
Everything else in the stream is reproduced as-is, including the quirks: centiseconds not seconds, the
value coming from SYSIN although both comments call it a PARM, columns 9+ ignored, and only the first
SYSIN record read.

## 4. Parity — legacy vs migrated

No fixture in `app/data/ASCII/**` is involved: none of the four artefacts in this stream reads or writes
a data file (`COBSWAIT.cbl:25-26`; `CLOSEFIL.jcl`/`OPENFIL.jcl` have no dataset DDs at all). The parity
evidence for a stream whose entire content is a control card and a dependency graph is the card's reading
rules and the graph itself, and both are checked against the source on every build.

| Path | Legacy | Migrated | Evidence |
|---|---|---|---|
| Shipped card `00003600      VALUE IN CENTISECONDS` | reads columns 1-8 → 3 600 centiseconds → waits 36 s → RC 0 | `sysin` default from `ctl/WAITSTEP.sysin` → 3 600 centiseconds → sleeps 36 000 ms → COMPLETED, exit 0 | `WaitStepJobTest.defaultsToTheControlCardShippedWithTheJcl`, `…waitsForTheRequestedNumberOfCentisecondsAndCompletes` |
| Commentary after column 8 | ignored (`PIC X(8)`) | ignored | `WaitControlCardTest.ignoresEverythingAfterColumnEight` |
| Extra SYSIN records | ignored (single `ACCEPT`) | ignored | `WaitControlCardTest.readsOnlyTheFirstRecord` |
| `00000000` | timer returns immediately, RC 0 | no wait, COMPLETED | `WaitControlCardTest.zeroWaitsNotAtAll` |
| `99999999` | accepted, the field's maximum | accepted | `WaitControlCardTest.acceptsTheLargestValueMvswaitTimeCanHold` |
| Non-numeric card | undefined MOVE, arbitrary wait, RC 0 | rejected, step abends, job FAILED, exit 12 — **deviation, §3** | `WaitStepJobTest.failsTheJobWhenTheControlCardIsNotNumeric` |
| CLOSEFIL / OPENFIL | five CEMT commands per job | no job exists | `FileControlJobsRemovedTest` |
| Control-M chain order and conditions | 12 edges | same 12 edges documented; no scheduler built | `OperationsChainScheduleTest.controlMGraphMatchesTheFolderDefinitions` |
| CA-7 trigger order and SCHIDs | 28 triggers | same 28 documented; no scheduler built | `OperationsChainScheduleTest.ca7GraphMatchesTheLjobListing` |

## 5. Running it

```bash
cd migration/carddemo/backend
mvn test                                     # includes the four S-17 test classes
java -jar target/carddemo.jar \
  --spring.main.web-application-type=none \
  --spring.profiles.active=postgres \
  --spring.batch.job.name=WAITSTEP \
  --sysin=00003600
```

## 6. Where the source contradicts the inventory

Recorded in full in `OperationsChain_analysis.md` §6: the inventory's one-line CA-7 summary
(`CardDemo_inventory.md:83-84`) is not the graph in the export — it invents an OPENFIL → TRANTYPE edge,
chains the reference-data branches into READACCT, and omits the statements and category-balance chains —
and S-17's trigger is given as "every Control-M folder" (`:144`) although `WEEKLY-TransactionTypesDBRefresh`
contains none of this stream's jobs. The orchestration contract follows the exports. The inventory was
not edited: it is another document's, and other streams may be reading it.

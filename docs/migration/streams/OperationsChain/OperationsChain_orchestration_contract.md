# OperationsChain — orchestration contract (B-14)

This is the reference the other batch streams' schedules are checked against: every trigger in
`app/scheduler/CardDemo.controlm` and `app/scheduler/CardDemo.ca7`, verbatim, plus what the target owes
each edge.

It is machine-checked. `com.carddemo.batch.operations.OperationsChainSchedule` holds the same graph, and
`OperationsChainScheduleTest` re-parses both scheduler exports on every build and fails if the exports,
the class or the tables below disagree (FR-OC-30…FR-OC-32).

## 1. How release works on each side

| | Control-M | CA-7 |
|---|---|---|
| Mechanism | Predecessor posts a condition (`OUTCOND` `SIGN="+"`); successor waits on it (`INCOND`) | Predecessor's LJOB block lists the jobs its completion triggers |
| Condition recorded below | The condition name | The `SCHID` on the trigger |
| Code checked? | No | No |

Neither scheduler tests a return code anywhere in the estate: **every edge fires on completion of the
predecessor**. The migrated jobs therefore only have to end with the right process exit code — 0 when the
job completes, 12 when it does not (`com.carddemo.batch.BatchJobLauncher`). No scheduler integration is
built (B-14).

## 2. Control-M (`app/scheduler/CardDemo.controlm`)

Ordering within a folder is by condition, not by listing order.

### DAILY-TransactionBackup — daily, all days, TIMETO 23:00

| Predecessor | Successor | Condition |
|---|---|---|
| CLOSEFIL | TRANBKP | DAILY-TransactionBackup-CLOSEFIL |
| TRANBKP | WAITSTEP | DAILY-TransactionBackup-TRANBKP |
| WAITSTEP | OPENFIL | DAILY-TransactionBackup-WAITSTEP |

### WEEKLY-TransactionTypesDBRefresh — smart folder, Saturdays

| Predecessor | Successor | Condition |
|---|---|---|
| MNTTRDB2 | TRANEXTR | WEEKLY-TransactionTypesDBRefresh-MNTTRDB2 |

MNTTRDB2 itself has no predecessor: it is the folder's entry point and runs on the calendar.
This folder has no CLOSEFIL/OPENFIL/WAITSTEP — it does not touch the CICS files.

### WEEKLY-DisclosureGroupsRefresh — smart folder, Saturdays

| Predecessor | Successor | Condition |
|---|---|---|
| MNTTRDB2 | CLOSEFIL | WEEKLY-TransactionTypesDBRefresh-MNTTRDB2 |
| CLOSEFIL | DISCGRP | WEEKLY-DisclosureGroupsRefresh-CLOSEFIL |
| DISCGRP | WAITSTEP | WEEKLY-DisclosureGroupsRefresh-DISCGRP |
| WAITSTEP | OPENFIL | WEEKLY-DisclosureGroupsRefresh-WAITSTEP |

The first row is the cross-folder gate: the disclosure-group refresh may not start until the weekly
transaction-type maintenance job has finished, because both consume the same condition.

### MONTHLY-InterestCalculation

| Predecessor | Successor | Condition |
|---|---|---|
| CLOSEFIL | INTCALC | MONTHLY-InterestCalculation-CLOSEFIL |
| INTCALC | COMBTRAN | MONTHLY-InterestCalculation-INTCALC |
| COMBTRAN | WAITSTEP | MONTHLY-InterestCalculation-COMBTRAN |
| WAITSTEP | OPENFIL | MONTHLY-InterestCalculation-WAITSTEP |

## 3. CA-7 (`app/scheduler/CardDemo.ca7`)

One network, six chains. The condition column is the trigger's `SCHID`; a job name occurring under two
SCHIDs is two scheduled occurrences of the same JCL.

### Posting (SCHID 030)

| Predecessor | Successor | Condition |
|---|---|---|
| CLOSEFIL | CBPAUP0J | 030 |
| CBPAUP0J | POSTTRAN | 030 |
| POSTTRAN | WAITSTEP | 030 |
| WAITSTEP | OPENFIL | 030 |

### Reference-data reload (SCHID 030 → parallel 031 / 032)

| Predecessor | Successor | Condition |
|---|---|---|
| CLOSEFIL | TRANTYPE | 030 |
| TRANTYPE | WAITSTEP | 030 |
| WAITSTEP | CLOSEFIL1 | 031 |
| WAITSTEP | CLOSEFIL2 | 032 |
| CLOSEFIL1 | TRANCATG | 031 |
| CLOSEFIL2 | TCATBALF | 032 |
| TRANCATG | WAITSTEP | 031 |
| TCATBALF | WAITSTEP | 032 |
| WAITSTEP | CLOSEFIL | 030 |

The WAITSTEP after TRANTYPE fans out into two independent branches, 031 and 032, which run in parallel;
each branch re-quiesces the files (CLOSEFIL1 / CLOSEFIL2) before its own load job. Both branches converge
on a WAITSTEP that triggers CLOSEFIL under SCHID 030 — the branch is closed by a CLOSEFIL, not by an
OPENFIL, so this chain leaves the CICS files closed for the next chain in the network.

### File prints (SCHID 030)

| Predecessor | Successor | Condition |
|---|---|---|
| CLOSEFIL | READACCT | 030 |
| READACCT | READCARD | 030 |
| READCARD | READCUST | 030 |
| READCUST | READXREF | 030 |
| READXREF | WAITSTEP | 030 |
| WAITSTEP | OPENFIL | 030 |

### Statements (SCHID 030)

| Predecessor | Successor | Condition |
|---|---|---|
| CLOSEFIL | CREASTMT | 030 |
| CREASTMT | TXT2PDF1 | 030 |
| TXT2PDF1 | WAITSTEP | 030 |
| WAITSTEP | OPENFIL | 030 |

### Category-balance report (SCHID 031)

| Predecessor | Successor | Condition |
|---|---|---|
| OPENFIL | CLOSEFIL | 031 |
| CLOSEFIL | PRTCATBL | 031 |
| PRTCATBL | WAITSTEP | 031 |
| WAITSTEP | OPENFIL | 031 |

The statements chain's WAITSTEP triggers OPENFIL under 030, and that OPENFIL triggers the category-balance
CLOSEFIL under 031 — the only place in the estate where an OPENFIL is a predecessor of anything.

## 4. What the target owes each job

CLOSEFIL / OPENFIL / CLOSEFIL1 / CLOSEFIL2 are **not migrated** (B-15): with a single shared PostgreSQL
database there is no dataset to quiesce, so wherever the tables above name one of them, the migrated
schedule simply omits it and wires the predecessor straight to the successor. WAITSTEP is retained as job
`WAITSTEP` only so a migrated chain that still names it stays runnable; a scheduler that can express a
delay between two jobs should express it there and omit the job (B-04).

| Mainframe job | Target | Stream (inventory §5) |
|---|---|---|
| CLOSEFIL, CLOSEFIL1, CLOSEFIL2, OPENFIL | removed, not migrated (B-15) | S-17 |
| WAITSTEP | job `WAITSTEP` | S-17 |
| TRANBKP, POSTTRAN | DailyTransactionPosting | S-11 |
| INTCALC, COMBTRAN | InterestCalculation | S-12 |
| CREASTMT, TXT2PDF1 | StatementGeneration | S-13 |
| PRTCATBL | TransactionReporting | S-14 |
| READACCT, READCARD, READCUST, READXREF | FileReadUtilities | S-15 |
| DISCGRP, TRANTYPE, TRANCATG, TCATBALF | DataLoadAndSetup (IDCAMS/IEBGENER reloads) | S-18 |
| MNTTRDB2, TRANEXTR | TransactionTypeDB2Refresh | S-19 |
| CBPAUP0J | PendingAuthPurgeAndIMSLoad | S-20 |

Stream ownership is quoted from `docs/migration/CardDemo_inventory.md` §5; the job → target-job-name
mapping is each owning stream's to declare. This document fixes only the *order and the conditions*.

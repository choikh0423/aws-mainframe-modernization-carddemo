# CORPT00C — functional requirements

Program `CORPT00C` (transaction `CR00`, mapset `CORPT00`, map `CORPT0A`) is the only program in
stream S-07. It is the *print a transaction report* screen: the operator picks a report period and
confirms, and the program submits the `TRANREPT` batch job through the CICS internal reader.

This document is the program-level view of the stream requirements: the same numbered FR-R IDs as
`../Reporting_functional_requirement.md`, arranged along the program's control flow so the COBOL can be
read against them paragraph by paragraph. The stream document is the sign-off oracle; this one is the
map from paragraph to requirement.

Line cites are `app/cbl/CORPT00C.cbl` unless stated otherwise.

## 1. Interfaces

| Interface | Legacy | Target |
|---|---|---|
| Screen | BMS map `CORPT0A` (`app/bms/CORPT00.bms`) | `frontend/src/pages/reporting/TransactionReportsPage.js` |
| Input | `CORPT0AI`: `MONTHLY`, `YEARLY`, `CUSTOM`, `SDTMM/SDTDD/SDTYYYY`, `EDTMM/EDTDD/EDTYYYY`, `CONFIRM` | JSON body of `POST /api/reports/transactions` (same ten fields, as typed) |
| Output | `CORPT0AO` with `ERRMSG` (row 23) and the cursor field | `ReportSubmitResponse` (200) / `ReportScreenErrorResponse` (400, 502) |
| State | `CARDDEMO-COMMAREA` re-entry flag (`CDEMO-PGM-REENTER`), lines 175-206 | none: each ENTER is a self-contained request |
| Called | `CSUTLDTC` (cbl:392, 412) | `com.carddemo.common.service.DateValidationService` via `CsutldtcFeedbackService` |
| Transfers | `COSGN00C` (cbl:173), `COMEN01C` (cbl:188) | route `/` and `/menu` |
| Job submission | `WRITEQ TD QUEUE('JOBS')` of the `JOB-DATA` deck (cbl:81-127, 498-523) | `TransactionReportJobLauncher` port (S-14 boundary) |

CR00 opens no file and issues no SQL: the program has no `FILE-CONTROL` and no `EXEC SQL`
(FR-R39). No Flyway migration is therefore contributed by this stream; the reserved band `V700`–`V799`
is left empty.

## 2. Control flow → requirements

| Paragraph (lines) | Behaviour | FR |
|---|---|---|
| `MAIN-PARA` entry (162-206) | `EIBCALEN = 0` → `COSGN00C`; first entry sends an empty map with the cursor on `MONTHLY`; later entries receive the map and dispatch on `EIBAID` | FR-R1, FR-R2 |
| `EIBAID` dispatch (182-203) | `DFHENTER` → `PROCESS-ENTER-KEY`; `DFHPF3` → `COMEN01C`; anything else → `Invalid key pressed. Please see below...` | FR-R3, FR-R4 |
| `SEND-TRNRPT-SCREEN` / `POPULATE-HEADER-INFO` (556-628) | header `Tran:`/`Prog:`/titles/date/time on every send; the message goes to `ERRMSG` | FR-R5 |
| `PROCESS-ENTER-KEY` — Monthly (212-238) | name `Monthly`; first of the current month to its last day, derived by adding a month and stepping back one day | FR-R6, FR-R8 |
| `PROCESS-ENTER-KEY` — Yearly (239-255) | name `Yearly`; `01/01` to `12/31` of the current year | FR-R6, FR-R9 |
| `PROCESS-ENTER-KEY` — Custom (256-436) | name `Custom`; the typed range, after the six empties, the four range tests and the two `CSUTLDTC` calls | FR-R6, FR-R10 to FR-R28 |
| — empty tests (259-300) | six ordered `= SPACES OR LOW-VALUES` tests, each ending the task | FR-R11 to FR-R17 |
| — `NUMVAL-C` (305-327) | every component recomputed into its picture before any range test | FR-R18, FR-R25 |
| — range tests (329-371) | `> '12'` on the months, `> '31'` on the days | FR-R19 to FR-R22, FR-R28 |
| — `CSUTLDTC` (381-426) | `YYYY-MM-DD` mask; severity `0000` or message `2513` passes | FR-R23, FR-R24, FR-R26 |
| — (no ordering test) | the two dates are never compared with each other | FR-R27 |
| no selection (437-442) | `Select a report type to print report...` | FR-R7 |
| `SUBMIT-JOB-TO-INTRDR` (462-495) | the `CONFIRM` gate: blank asks, `Y`/`y` submits, `N`/`n` clears, anything else is quoted back | FR-R29 to FR-R32 |
| `SUBMIT-JOB-TO-INTRDR` (496-523) | writes the 17-line `JOB-DATA` deck to TDQ `JOBS`, `/*EOF` included | FR-R33, FR-R34, FR-R37 |
| `WIRTE-JOBSUB-TDQ` failure (525-535) | `Unable to Write TDQ (JOBS)...` | FR-R35 |
| success (445-456) | fields cleared, `<name> report submitted for printing ...` | FR-R36 |
| target-only | the port binding while S-14 has not delivered `TRANREPT` | FR-R38 |

## 3. Field edits, in the order the program applies them

For a **Custom** report only; Monthly and Yearly skip straight to the confirmation gate
(cbl:238, 255). Each row ends the interaction when it fails — nothing below it runs (FR-R11).

| # | Test | Field | Message |
|---|---|---|---|
| 1 | not blank | `SDTMM` | `Start Date - Month can NOT be empty...` |
| 2 | not blank | `SDTDD` | `Start Date - Day can NOT be empty...` |
| 3 | not blank | `SDTYYYY` | `Start Date - Year can NOT be empty...` |
| 4 | not blank | `EDTMM` | `End Date - Month can NOT be empty...` |
| 5 | not blank | `EDTDD` | `End Date - Day can NOT be empty...` |
| 6 | not blank | `EDTYYYY` | `End Date - Year can NOT be empty...` |
| — | `NUMVAL-C` into `PIC 99` / `PIC 9(4)` — never fails | all six | — |
| 7 | `NOT > '12'` | `SDTMM` | `Start Date - Not a valid Month...` |
| 8 | `NOT > '31'` | `SDTDD` | `Start Date - Not a valid Day...` |
| 9 | `NOT > '12'` | `EDTMM` | `End Date - Not a valid Month...` |
| 10 | `NOT > '31'` | `EDTDD` | `End Date - Not a valid Day...` |
| 11 | `CSUTLDTC` severity `0000` or message `2513` | start date | `Start Date - Not a valid date...` |
| 12 | `CSUTLDTC` severity `0000` or message `2513` | end date | `End Date - Not a valid date...` |
| 13 | `CONFIRM` blank / `Y` / `y` / `N` / `n` | `CONFIRM` | `Please confirm to print the <name> report...` / `"<c>" is not a valid value to confirm...` |

The `Start Date - Not a valid Year...` and `End Date - Not a valid Year...` literals
(cbl:347-353, 373-379) sit behind `IF WS-START-DATE-YYYY IS NOT NUMERIC`, which cannot be true after the
`NUMVAL-C` compute: they are dead code in the legacy program and are dead in the target too (FR-R25).
They are kept in `ReportingMessages` so the constant set matches the program's literal set.

## 4. Quirks carried over deliberately

| Q | Quirk | FR |
|---|---|---|
| Q-1 | letters in a date component become `0` instead of being rejected as non-numeric | FR-R25 |
| Q-2 | the two `Not a valid Year...` messages are unreachable | FR-R25 |
| Q-3 | a pre-1582-10-15 date (`CSUTLDTC` message `2513`) is accepted | FR-R26 |
| Q-4 | the end date may precede the start date | FR-R27 |
| Q-5 | `02/31` passes the day test and is caught by `CSUTLDTC` | FR-R28 |
| Q-6 | the `/*EOF` terminator is written to the queue before the loop stops | FR-R34 |
| Q-7 | a value in more than one report-type field is resolved by `EVALUATE` order, not reported as an error | FR-R6 |

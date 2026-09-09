# S-07 Reporting — functional requirements

The business sign-off oracle for stream S-07 (transaction `CR00`, program `CORPT00C`, map `CORPT0A`).
Every requirement cites its source and is covered by a test; the coverage column names the test class.
The five entry/navigation requirements are screen-shell behaviour that the target realises in the shared
S-01 shell (`Layout`, `Header`, the route registry) rather than in S-07 code; the repository has no
frontend test harness and the CI frontend job only builds, so they are marked *shell* and are listed as a
deliberate deferral in the migration plan §6.
Message strings are **verbatim** from the COBOL literals / BMS map — trailing `...` included.

Legend for cursor: the field the legacy program positions the cursor on
(`MOVE -1 TO <field>L`), reproduced in the target as the `cursor` attribute of the error payload.

| ID | Requirement | Source | Covered by |
|---|---|---|---|
| **Entry and navigation** ||||
| FR-R1 | Entering `CR00` with no COMMAREA (`EIBCALEN = 0`) transfers to `COSGN00C` (sign-on) without displaying the report screen. | cbl:172-174, 540-551 | *shell* — the S-01 sign-on guard on every route |
| FR-R2 | The first display of the screen shows all input fields empty, no message, and the cursor on `MONTHLY`. | cbl:177-181 | *shell* — `TransactionReportsPage` initial state (`EMPTY_FORM`, cursor `MONTHLY`) |
| FR-R3 | `PF3` returns to the main menu program `COMEN01C`. | cbl:187-189 | *shell* — `TransactionReportsPage` PF3 handler → `/menu` |
| FR-R4 | Any AID other than `ENTER` and `PF3` shows `Invalid key pressed. Please see below...` with the cursor on `MONTHLY`; nothing is submitted. | cbl:190-194, `CSMSG01Y` | *unreachable in the target* — the screen can raise only ENTER and PF3 (plan §6) |
| FR-R5 | Every screen send carries the header `Tran: CR00`, `Prog: CORPT00C`, the `CCDA-TITLE01`/`CCDA-TITLE02` titles, the current date as `MM/DD/YY` and the current time as `HH:MM:SS`. | cbl:609-628 | *shell* — shared `Header` fed `tranId="CR00"`, `progName="CORPT00C"` |
| **Report type selection** ||||
| FR-R6 | A report type is selected by any character other than blank in `MONTHLY`, `YEARLY` or `CUSTOM`; when more than one is marked the precedence is Monthly, then Yearly, then Custom. | cbl:212-256 | `ReportRequestValidatorTest`, `ReportSubmissionServiceTest` |
| FR-R7 | With no report type marked, ENTER shows `Select a report type to print report...` with the cursor on `MONTHLY`; nothing is submitted. | cbl:437-442 | `ReportRequestValidatorTest`, `ReportSubmissionControllerTest` |
| FR-R8 | The **Monthly** report is named `Monthly` and covers the first day of the current month to the last day of the current month inclusive. | cbl:213-238 | `ReportPeriodTest`, `ReportSubmissionServiceTest` |
| FR-R9 | The **Yearly** report is named `Yearly` and covers `YYYY-01-01` to `YYYY-12-31` of the current year. | cbl:239-255 | `ReportPeriodTest`, `ReportSubmissionServiceTest` |
| FR-R10 | The **Custom** report is named `Custom` and covers the start and end dates typed on the screen, formatted `YYYY-MM-DD`. | cbl:256-436 | `ReportSubmissionServiceTest` |
| **Custom date-range validation (first failure wins; nothing after it is evaluated)** ||||
| FR-R11 | Validation stops at the first failing check: the task ends with that message and no later check, no confirmation and no submission is performed. | cbl:556-591 (`SEND-TRNRPT-SCREEN` → `GO TO RETURN-TO-CICS`) | `ReportRequestValidatorTest` |
| FR-R12 | Blank start month → `Start Date - Month can NOT be empty...`, cursor `SDTMM`. | cbl:259-265 | `ReportRequestValidatorTest` |
| FR-R13 | Blank start day → `Start Date - Day can NOT be empty...`, cursor `SDTDD`. | cbl:266-272 | `ReportRequestValidatorTest` |
| FR-R14 | Blank start year → `Start Date - Year can NOT be empty...`, cursor `SDTYYYY`. | cbl:273-279 | `ReportRequestValidatorTest` |
| FR-R15 | Blank end month → `End Date - Month can NOT be empty...`, cursor `EDTMM`. | cbl:280-286 | `ReportRequestValidatorTest` |
| FR-R16 | Blank end day → `End Date - Day can NOT be empty...`, cursor `EDTDD`. | cbl:287-293 | `ReportRequestValidatorTest` |
| FR-R17 | Blank end year → `End Date - Year can NOT be empty...`, cursor `EDTYYYY`. | cbl:294-300 | `ReportRequestValidatorTest` |
| FR-R18 | Each date component is normalised through `FUNCTION NUMVAL-C` into its picture and echoed back zero-padded: months/days to 2 digits, years to 4 (`1 ` → `01`, `20` → `0020`). The normalised values are what the screen shows after an error and what the submitted range is built from. | cbl:305-327 | `CobolNumvalTest`, `ReportRequestValidatorTest` |
| FR-R19 | Normalised start month greater than 12 → `Start Date - Not a valid Month...`, cursor `SDTMM`. | cbl:329-336 | `ReportRequestValidatorTest` |
| FR-R20 | Normalised start day greater than 31 → `Start Date - Not a valid Day...`, cursor `SDTDD`. | cbl:338-345 | `ReportRequestValidatorTest` |
| FR-R21 | Normalised end month greater than 12 → `End Date - Not a valid Month...`, cursor `EDTMM`. | cbl:355-362 | `ReportRequestValidatorTest` |
| FR-R22 | Normalised end day greater than 31 → `End Date - Not a valid Day...`, cursor `EDTDD`. | cbl:364-371 | `ReportRequestValidatorTest` |
| FR-R23 | The start date `YYYY-MM-DD` is validated by `CSUTLDTC`; a severity other than `0000` whose message number is not `2513` → `Start Date - Not a valid date...`, cursor `SDTMM`. | cbl:388-406 | `ReportRequestValidatorTest`, `CsutldtcFeedbackTest` |
| FR-R24 | The end date is validated the same way → `End Date - Not a valid date...`, cursor `EDTMM`. | cbl:408-426 | `ReportRequestValidatorTest`, `CsutldtcFeedbackTest` |
| **Quirks — preserved deliberately** ||||
| FR-R25 | *(Q-1/Q-2)* Non-numeric date input is **not** rejected as `Not a valid Month/Day/Year`: `NUMVAL-C` turns it into `0`, so `ab/cd/efgh` becomes `00/00/0000` and is rejected by `CSUTLDTC` as `Start Date - Not a valid date...`. The `Not a valid Year...` messages are unreachable for the same reason. | cbl:305-353 | `ReportRequestValidatorTest` |
| FR-R26 | *(Q-3)* A syntactically valid date outside the `CEEDAYS` Lillian range (before 1582-10-15), which returns severity ≠ `0000` with message number `2513`, is **accepted**. | cbl:399, 419; `CSUTLDTC.cbl:66` | `CsutldtcFeedbackTest`, `ReportRequestValidatorTest` |
| FR-R27 | *(Q-4)* The start date is never compared with the end date; a range whose end precedes its start is submitted unchanged. | cbl:381-436 | `ReportSubmissionServiceTest` |
| FR-R28 | *(Q-5)* A day that is out of range for its month (e.g. `02/31/2023`) passes the `> '31'` test and is rejected by `CSUTLDTC` as `Start Date - Not a valid date...`. | cbl:338-406 | `ReportRequestValidatorTest` |
| **Confirmation** ||||
| FR-R29 | A blank `CONFIRM` shows `Please confirm to print the <name> report...` (`<name>` = `Monthly`/`Yearly`/`Custom`) with the cursor on `CONFIRM`; nothing is submitted. Applies to all three report types. | cbl:464-474 | `ReportSubmissionServiceTest`, `ReportSubmissionControllerTest` |
| FR-R30 | `CONFIRM` = `Y` or `y` submits the job. | cbl:478-479 | `ReportSubmissionServiceTest` |
| FR-R31 | `CONFIRM` = `N` or `n` clears every input field and redisplays the screen with **no message**; nothing is submitted. | cbl:480-483 | `ReportSubmissionServiceTest`, `ReportSubmissionControllerTest` |
| FR-R32 | Any other `CONFIRM` value shows `"<c>" is not a valid value to confirm...` (the typed character between double quotes) with the cursor on `CONFIRM`. | cbl:484-493 | `ReportSubmissionServiceTest` |
| **Submission** ||||
| FR-R33 | On confirmation the `TRANREPT` job is submitted through the internal reader as the 17-line deck of `JOB-DATA`, each line exactly 80 characters, with the chosen range substituted into `PARM-START-DATE`, `PARM-END-DATE` (SYMNAMES) and the `DATEPARM` line. | cbl:81-127, 429-435, 498-508 | `TranReportJclDeckTest` |
| FR-R34 | *(Q-6)* The `/*EOF` terminator line is itself written to the queue before the write loop stops; the deck written is 17 lines. | cbl:498-508 | `TranReportJclDeckTest` |
| FR-R35 | A failure of the queue write shows `Unable to Write TDQ (JOBS)...` with the cursor on `MONTHLY`, and no success message. | cbl:525-535 | `ReportSubmissionServiceTest`, `ReportSubmissionControllerTest` |
| FR-R36 | A successful submission clears every input field, shows `<name> report submitted for printing ...` in green with the cursor on `MONTHLY`. | cbl:445-456 | `ReportSubmissionServiceTest`, `ReportSubmissionControllerTest` |
| FR-R37 | The submission reaches exactly one launch of the `TRANREPT` report job, carrying the report type and the resolved start/end dates; the launch happens once per confirmed request and never for a rejected one. | cbl:462-510 (boundary, see the migration plan §3) | `ReportSubmissionServiceTest`, `ReportSubmissionControllerTest` |
| FR-R38 | Until the S-14 `TRANREPT` Spring Batch job exists, the launch port is bound to a recording no-op adapter that logs the request and reports success; the screen behaviour of FR-R36 is unchanged when the real job arrives. | boundary decision, plan §3 | `NoOpTransactionReportJobLauncherTest` |
| FR-R39 | CR00 reads no business data; a submission changes no CardDemo table. | cbl:162-649 (no file control, no SQL) | `ReportSubmissionControllerTest` |

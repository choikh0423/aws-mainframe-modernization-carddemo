# S-07 Reporting — migration plan

How transaction `CR00` (`CORPT00C`, map `CORPT0A`) was migrated into the consolidated Spring Boot +
React application, the boundary decisions taken, and the legacy-vs-migrated parity evidence.

Companion documents: `Reporting_analysis.md` (source analysis with line cites),
`Reporting_functional_requirement.md` (FR-R1…FR-R39, the sign-off oracle),
`programs/CORPT00C_functional_requirement.md` (per-paragraph mapping).

## 1. Shape of the target

CR00 is a single-action screen: the operator marks a report period, confirms, and a batch job is
submitted. There is no file access and no state carried between interactions apart from the CICS
re-entry flag, so the target is one endpoint and one React page.

| Legacy | Target |
|---|---|
| `CORPT00C` PROCESS-ENTER-KEY | `POST /api/reports/transactions` → `ReportSubmissionService` |
| map `CORPT0A` | `frontend/src/pages/reporting/TransactionReportsPage.js` (route registry entry `CORPT00C` → `/reports`) |
| `ERRMSG` + `MOVE -1 TO <field>L` | `ReportScreenErrorResponse{message, cursor, dateFields}` |
| `EVALUATE` on the report type | `ReportRequestValidator.resolveReportType` → `ReportType` |
| `FUNCTION CURRENT-DATE` arithmetic | `ReportPeriodResolver` (injectable `Clock`) |
| `FUNCTION NUMVAL-C` + `MOVE` to `PIC 9(n)` | `CobolNumval.numvalCInto` |
| `CALL 'CSUTLDTC'` | shared `DateValidationService`, wrapped by `CsutldtcFeedbackService` |
| `JOB-DATA` deck + `WRITEQ TD QUEUE('JOBS')` | `TranReportJclDeck` + `TransactionReportJobLauncher` port |

Everything lives in `com.carddemo.reporting` (`controller`, `service`, `validator`, `dto`, `port`,
`adapter`, `util`, plus `ReportingMessages` and `ScreenField`). No shared class was modified.

### Design decisions

- **All edits stay on the server.** The React page sends the ten map fields exactly as typed and renders
  whatever text comes back, so the screen cannot drift from the COBOL literals. The message strings exist
  in exactly one place, `ReportingMessages`.
- **The screen fields are strings end to end.** `SDTMM`, `SDTYYYY` and friends are `PIC X` map fields
  that the program normalises itself; typing them as numbers in the DTO would silently fix Q-1/Q-2.
- **First failure ends the interaction.** `SEND-TRNRPT-SCREEN` never returns to the caller
  (`GO TO RETURN-TO-CICS`, cbl:556-591), so each edit throws `ReportValidationException` — the same
  "stop here, redisplay" control flow, not a collected list of violations.
- **The cursor is part of the contract.** Every failure names the field the program put the cursor on;
  the page focuses it.
- **No schema change.** CR00 opens no file and issues no SQL (FR-R39); the reserved Flyway band
  `V700`–`V799` is deliberately left empty.

### Status codes

| Outcome | Status | Body |
|---|---|---|
| job submitted (`Y`/`y`) | 200 | `submitted=true`, the green success text |
| declined (`N`/`n`) | 200 | `submitted=false`, empty message, `fieldsCleared=true` |
| any edit failure, incl. the confirmation prompt | 400 | ERRMSG text + cursor (+ normalised date fields) |
| the launch failed | 502 | `Unable to Write TDQ (JOBS)...` |

The confirmation prompt is a 400 because in the legacy program it is one of the error paths: `WS-ERR-FLG`
is set and the map is redisplayed with `ERRMSG` (cbl:464-474).

## 2. Date validation — reusing the shared CSUTLDTC

`DateValidationService` in `com.carddemo.common` is used as-is: it is the shared port of `CSUTLDTC` and
was not re-ported or modified. It exposes the severity code, which is all COTRN02C needed.

CORPT00C needs one thing more: it accepts a date whose severity is non-zero as long as the *message
number* is `2513` (cbl:396-406, 416-426). `CsutldtcFeedbackService`, inside this stream, wraps the shared
service and adds that message number:

- `2513` = `FC-UNSUPP-RANGE` (`app/cbl/CSUTLDTC.cbl:66`, token `X'000309D1'` = 2513) — a real date outside
  the range `CEEDAYS` supports, i.e. before the Lillian epoch `1582-10-15`. Accepted (FR-R26).
- `2521` = `FC-YEAR-IN-ERA-ZERO` — year `0000`, which is what `NUMVAL-C` produces from a typed year of
  letters. Rejected, and tested before the pre-Lillian escape because `java.time` reads year 0 as 1 BCE
  and would otherwise class it as a tolerable pre-Lillian date.
- everything else is reported as a plain bad value; CORPT00C only ever asks "is it 2513", so the
  remaining cause codes are not modelled individually.

If a later stream needs the same feedback area, the natural move is to lift the message number into the
shared service; that is a shared-code change and is **not** made here.

## 3. Boundary decision — S-07 → S-14 `TRANREPT`

`CORPT00C` submits the report job by writing a 17-line JCL deck to the CICS internal reader through
transient data queue `JOBS` (cbl:81-127, 498-523; `app/jcl/INTRDRJ1.JCL`, `INTRDRJ2.JCL`). The job it
submits, `TRANREPT` / `CBTRN03C`, belongs to stream **S-14 TransactionReporting**. This is the boundary
the inventory flagged as undecided (`docs/migration/CardDemo_inventory.md` §5, "One boundary is not yet in
the register"). It is decided as follows.

**The port** — `com.carddemo.reporting.port.TransactionReportJobLauncher`:

```java
public interface TransactionReportJobLauncher {
    void launch(TransactionReportJobRequest request);   // reportName + startDate + endDate
}
```

`TransactionReportJobRequest` carries only what the legacy deck carried: the report name
(`Monthly`/`Yearly`/`Custom`, the `WS-REPORT-NAME` literal) and the resolved range as `LocalDate`s, which
`PARM-START-DATE` / `PARM-END-DATE` / `DATEPARM` supplied to the job. It deliberately does not validate
that the end is not before the start (Q-4, FR-R27).

**The binding today** — `com.carddemo.reporting.adapter.NoOpTransactionReportJobLauncher` supplies a
`@ConditionalOnMissingBean` `TransactionReportJobLauncher`. It runs no reporting work: it records the
request and logs the deck `TranReportJclDeck` builds for it, then returns normally, so CR00 shows the
same green `... report submitted for printing ...` line it will show once the real job is wired in
(FR-R38). The recorded requests are the audit trail an operator would otherwise read off the JOBS queue.

**The handover** — when S-14 contributes its Spring Batch job in `com.carddemo.batch.tranreport`, it
registers a `TransactionReportJobLauncher` bean that starts `tranReportJob` with the range as job
parameters. The conditional then removes the no-op and **no code in `com.carddemo.reporting` changes**.
The one contract S-14 must honour: if its bean is not registered through an auto-configuration, it should
be marked `@Primary`, so that bean-definition ordering cannot leave both beans in the context.
`NoOpTransactionReportJobLauncherTest` pins both halves of this: the port is bound and working today, and
a competing launcher wins.

**Why the JCL deck is still built.** `TranReportJclDeck` is not dead scaffolding: it is the fidelity
evidence for FR-R33/FR-R34 (17 records of exactly 80 characters, `/*EOF` written before the loop stops),
it is what the no-op adapter logs, and it is the specification of the parameters S-14's job must accept.
The batch job itself is **not** implemented here.

## 4. Parity — legacy behaviour vs migrated behaviour

Fixtures: the ranges below are the ones exercised by the tests; CR00 reads no data files, so
`app/data/ASCII/**` is only relevant to the S-14 job that consumes the submitted range
(`daily-transaction.txt` → `CBTRN03C`), not to this screen.

| # | Input | Legacy CORPT00C | Migrated | Evidence |
|---|---|---|---|---|
| 1 | ENTER, nothing marked | `ERRMSG` = `Select a report type to print report...`, cursor `MONTHLY` | 400, same text, `cursor=MONTHLY` | `ReportSubmissionControllerTest.noReportTypeSelected` |
| 2 | `MONTHLY`=`S`, confirm blank, on 2023-07-15 | `Please confirm to print the Monthly report...`, cursor `CONFIRM` | 400, same text and cursor | `ReportSubmissionServiceTest.blankConfirmAsksForMonthly` |
| 3 | `MONTHLY`=`S`, confirm `Y`, on 2023-07-15 | deck submitted for `2023-07-01`…`2023-07-31`; `Monthly report submitted for printing ...` | 200, same text; one launch with that range | `ReportSubmissionServiceTest.launchCarriesTheRange`, `…ControllerTest.confirmedMonthlySubmission` |
| 4 | `MONTHLY`=`S`, confirm `Y`, on 2023-12-09 | `2023-12-01`…`2023-12-31` (month walk rolls the year) | identical | `ReportPeriodTest.monthlyRollsTheYearInDecember` |
| 5 | `YEARLY`=`S`, confirm `Y`, on 2023-07-15 | `2023-01-01`…`2023-12-31`, `Yearly report submitted for printing ...` | identical | `ReportPeriodTest.yearlyCoversTheCurrentYear`, `ReportSubmissionServiceTest` |
| 6 | all three fields marked | `EVALUATE` picks Monthly | Monthly | `ReportRequestValidatorTest.evaluateOrderFixesPrecedence` |
| 7 | `CUSTOM`=`S`, start `  /01/2023` | `Start Date - Month can NOT be empty...`, cursor `SDTMM`, no later check | 400, same text and cursor | `ReportRequestValidatorTest.startMonth` |
| 8 | `CUSTOM`=`S`, start `7/1/2023` valid | components redisplayed `07/01/2023` | `dateFields` = `07`,`01`,`2023` | `ReportRequestValidatorTest.normalisesAcceptedInput`, `…ControllerTest.redisplaysNormalisedFields` |
| 9 | `CUSTOM`=`S`, start `13/01/2023` | `Start Date - Not a valid Month...`, cursor `SDTMM` | identical | `ReportRequestValidatorTest.startMonthAbove12` |
| 10 | `CUSTOM`=`S`, start `02/31/2023` | passes `> '31'`, fails `CSUTLDTC` → `Start Date - Not a valid date...` | identical | `ReportRequestValidatorTest.impossibleDayOfMonthReachesCsutldtc` |
| 11 | `CUSTOM`=`S`, start `ab/cd/efgh` | `NUMVAL-C` → `00/00/0000` → `Start Date - Not a valid date...` (never "Not a valid Month/Year") | identical, `dateFields.startDate = 0000-00-00` | `ReportRequestValidatorTest.lettersFailAsInvalidDate`, `CobolNumvalTest` |
| 12 | `CUSTOM`=`S`, `01/01/1500` … `12/31/1500` | severity `0012`, message `2513` → accepted | accepted | `CsutldtcFeedbackTest.unsupportedRangeIsAccepted`, `ReportRequestValidatorTest.unsupportedRangeIsAccepted` |
| 13 | `CUSTOM`=`S`, `12/31/2023` … `01/01/2023`, confirm `Y` | no ordering test: submitted as typed | submitted as typed | `ReportSubmissionServiceTest.backwardsRangeIsSubmitted` |
| 14 | confirm `N` | all fields initialised, screen redisplayed with no message, nothing submitted | 200, `submitted=false`, empty message, nothing launched | `ReportSubmissionServiceTest.confirmNClearsTheScreen`, `…ControllerTest.declinedSubmission` |
| 15 | confirm `X` | `"X" is not a valid value to confirm...`, cursor `CONFIRM` | 400, same text and cursor | `ReportSubmissionServiceTest.invalidConfirmValue` |
| 16 | confirm `Y`, deck written | 17 records of 80 characters, `/*EOF` included, range in SYMNAMES and DATEPARM | identical deck | `TranReportJclDeckTest` |
| 17 | confirm `Y`, TDQ write fails | `Unable to Write TDQ (JOBS)...`, cursor `MONTHLY`, no success text | 502, same text and cursor | `ReportSubmissionServiceTest.failedSubmissionShowsTheTdqError`, `…ControllerTest.failedLaunch` |
| 18 | any submission | no file opened, no record written | transaction count unchanged | `ReportSubmissionControllerTest.touchesNoBusinessData` |

## 5. Verification

- `mvn -B test` — green, 364 tests after rebasing on `devin/carddemo-integration`, of which 86 are this
  stream's in `com.carddemo.reporting` (`CobolNumvalTest`,
  `CsutldtcFeedbackTest`, `ReportPeriodTest`, `ReportRequestValidatorTest`, `TranReportJclDeckTest`,
  `NoOpTransactionReportJobLauncherTest`, `ReportSubmissionServiceTest`,
  `ReportSubmissionControllerTest`) on top of the existing suite.
- `npm ci && CI=true npm run build` — green.

## 6. Deliberate deferrals and open points

1. **FR-R1/R2/R3/R5 are shell behaviour and are not covered by an automated test.** The repository has no
   frontend test harness (no `@testing-library`, and the CI frontend job only builds), and adding one
   would mean editing shared `package.json` while other streams are working in the same tree. The
   behaviour itself is implemented — the sign-on guard, the empty initial screen, the PF3 route to
   `/menu`, the shared header — but it is verified by inspection, not by a test.
2. **FR-R4 has no reachable path in the target.** The React screen can raise only ENTER and PF3, so
   `Invalid key pressed. Please see below...` cannot occur. The literal is left in the shared
   `CardDemoMessages`; no S-07 code produces it.
3. **The `Not a valid Year...` literals are unreachable in both the legacy program and the target**
   (Q-2, FR-R25). They are kept as constants so the literal set matches, and they are not raised.
4. **The `2513` tolerance is modelled in this stream, not in the shared date service.** Lifting the
   CSUTLDTC message number into `DateValidationService` would be a shared-code change; see §2.
5. **The batch job is not implemented** — that is S-14's, per the scope. Today the port runs the
   documented no-op adapter; the integration point is §3.

## 7. Where the source contradicted the inventory

- `docs/migration/CardDemo_inventory.md` §5 records the CR00 → TRANREPT internal-reader coupling as a
  boundary that "is not yet in the register" and "must be decided when either is planned". It is decided
  here (§3); the register `.migration/04_boundary_register.md` still has no entry for it (it holds B-02
  for CSUTLDTC only). The register was left untouched because it is a shared document.
- The inventory (line 192) locates the ported `DateValidationService` in
  `migration/transaction-management/backend`; in the consolidated app it is
  `com.carddemo.common.service.DateValidationService`, which is what this stream reuses. The frozen v1
  module was not touched.
- No contradiction was found between the inventory's S-07 scope (1 program, `CORPT00C`, map `CORPT00`)
  and the source.

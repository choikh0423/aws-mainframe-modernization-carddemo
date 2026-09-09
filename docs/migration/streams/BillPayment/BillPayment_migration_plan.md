# S-06 BillPayment — migration plan

How transaction **CB00** (`COBIL00C` + map `COBIL0A`) was migrated into the consolidated Spring Boot
+ React application, the parity evidence, and the boundary decisions taken. Requirements referenced
as FR-BP-nn are in
[`programs/COBIL00C_functional_requirement.md`](programs/COBIL00C_functional_requirement.md);
source evidence and the quirk register (Q-1 … Q-8) are in
[`BillPayment_analysis.md`](BillPayment_analysis.md).

## 1. What was built

| Layer | Artifact | Legacy counterpart |
|---|---|---|
| Backend | `com.carddemo.billpayment.service.BillPaymentService` | `PROCESS-ENTER-KEY` + the file paragraphs (cbl:154-244, 343-547) |
| Backend | `com.carddemo.billpayment.controller.BillPaymentController` — `POST /api/billpay/screen` | the ENTER leg of `RECEIVE-BILLPAY-SCREEN` / `SEND-BILLPAY-SCREEN` |
| Backend | `BillPaymentFormat` | the `PIC +9999999999.99` balance edit, the `TRAN-AMT` MOVE, `WS-TRAN-ID-NUM`, `GET-CURRENT-TIMESTAMP` |
| Backend | `BillPaymentMessages` | every COBOL message literal |
| Backend | `BillPaymentValidationException`, `AccountNotFoundException`, `DuplicateTranIdException`, `BillPaymentExceptionHandler` | the `WS-ERR-FLG` / `WS-MESSAGE` error legs |
| Frontend | `frontend/src/pages/billpayment/BillPaymentPage.js` | map `COBIL0A` |
| Frontend | one line in `frontend/src/routes/registry.js` (`element: <BillPaymentPage />`) | `CB00` → `COBIL00C` in `app/csd/CARDDEMO.CSD` |

No shared entity, shared service or schema change was needed: `AccountRecord`, `CardXrefRecord`,
`TransactionRecord` and their repositories already carry every field COBIL00C touches, so the
stream's Flyway band `V600`–`V699` is unused.

## 2. Design decisions

**D-A — one endpoint, one ENTER turn.** COBIL00C only reaches a file on ENTER: PF3 does an `XCTL`
and PF4 blanks working storage (cbl:128-137). The stream therefore exposes exactly one endpoint and
the React screen owns PF3/PF4, rather than inventing `/pf3` and `/pf4` calls that would have no
legacy behaviour behind them.

**D-B — no CB00 state on the server.** The program is pseudo-conversational and keeps nothing about
CB00 in the COMMAREA beyond the navigation fields (`CARDDEMO-COMMAREA`, cbl:76-80): each ENTER turn
re-reads ACCTDAT. The endpoint is likewise stateless, and the shared `CommareaSession` is left to
the shell (S-01), which already provides the "no commarea → COSGN00C" rule via `RequireSignOn`
(FR-BP-01).

**D-C — HTTP status mapping.** Turns that repaint a field (balance inquiry, "nothing to pay", the
`N` decline, a completed payment) are `200` with the whole screen; turns that only put text on the
ERRMSG line are the matching error status carrying the verbatim text in the shared
`common.dto.ErrorResponse`: `400` for the two input edits, `404` for `Account ID NOT found...`,
`409` for `Tran ID already exist...`. `You have nothing to pay...` stays a `200` with
`error: true` **because the legacy turn still shows the balance** (cbl:193-206) and `ErrorResponse`
carries no data field — the alternative would have been a stream-private error DTO, which the README
tells streams not to invent.

**D-D — atomicity.** CICS committed the TRANSACT `WRITE` and the ACCTDAT `REWRITE` under the task's
implicit syncpoint (cbl:233-235); `BillPaymentService.enter` is `@Transactional`, so a failing
balance update rolls the written payment back (FR-BP-40).

**D-E — clock.** `GET-CURRENT-TIMESTAMP` is `ASKTIME`/`FORMATTIME` on the CICS region's local time.
The service takes an optional `java.time.Clock` (system default zone in production, fixed in tests)
instead of declaring a `Clock` bean, which would be a de-facto shared bean other streams could
collide with.

**D-F — the balance edit is stream-local.** `common.util.CobolFormat.amountEdited` renders
`PIC +99999999.99` (the CT01 field). COBIL00C's `WS-CURR-BAL` is `PIC +9999999999.99`, two digits
wider (cbl:56), so the edit lives in `BillPaymentFormat` rather than being "fixed" in the shared
helper, which other streams depend on.

## 3. Parity — legacy vs migrated

Fixtures: the seeded account `1` (`app/data/ASCII/acctdata.txt` → `+0000000194.00`, card
`9680294154603697` from `cardxref.txt`) and the 30 seeded transactions, so the next id is
`0000000000000031`.

| Path | Legacy (COBIL00C) | Migrated | Test |
|---|---|---|---|
| Acct ID blank, ENTER | ERRMSG `Acct ID can NOT be empty...`, cursor ACTIDIN, no I/O | `400` + same text | `frBp10_emptyAccountId(IsBadRequest)` |
| Acct `1`, confirm blank | CURBAL `+0000000194.00`, ERRMSG `Confirm to make a bill payment...`, cursor CONFIRM | `200` with the same three values | `frBp24_balanceInquiry…` |
| Acct `1`, confirm `X` | ERRMSG `Invalid value. Valid values are (Y/N)...`, no read, balance not refreshed (Q-2) | `400` + same text, no balance in the body | `frBp13_confirmInvalid` |
| Acct `1`, confirm `N` | screen blanked, **no** message (Q-7) | `200`, all fields `""`, `message: ""` | `frBp12_confirmNo_clearsScreenSilently` |
| Acct `99999999999` | ERRMSG `Account ID NOT found...` | `404` + same text | `frBp21_accountNotFound` |
| Acct `ABCDEFGHIJK` | read fails → same "not found" (Q-4) | `404` + same text | `frBp14_nonNumericAccountId` |
| Balance `0.00`, confirm `Y` | ERRMSG `You have nothing to pay...`, CURBAL `+0000000000.00` | `200`, `error: true`, same text and balance | `frBp23_nothingToPay_stillShowsTheBalance` |
| Acct `1`, confirm `Y` | TRANSACT `0000000000000031` for `194.00` on card `9680294154603697`, ACCTDAT balance `0.00`, green `Payment successful.  Your Transaction ID is 0000000000000031.` | identical row, identical balance, identical text | `frBp38_paymentWritesTheRowAndZeroesTheBalance`, `frBp39_successMessage(OverHttp)` |
| Balance `1234567890.12`, confirm `Y` | `TRAN-AMT` truncates to `234567890.12`; `1000000000.00` stays owing (Q-1) | identical | `frBp35_amountTruncatedToNineDigits` |
| Empty TRANSACT | first id `0000000000000001` | identical | `frBp33_emptyTransactFile_firstIdIsOne` |
| Generated id exists | ERRMSG `Tran ID already exist...`, nothing persisted | `409` + same text, nothing persisted | `frBp37_duplicateTranId` |
| Missing xref | ERRMSG `Account ID NOT found...` (Q-3) | `404` + same text | `frBp31_xrefMissing…` |

Screen parity (`app/bms/COBIL00.bms` → `BillPaymentPage.js`):

| BMS field | Row | Migrated |
|---|---|---|
| `'Bill Payment'` title, `TRNNAME`/`PGMNAME`/date/time header | 1-4 | `Layout tranId="CB00" progName="COBIL00C" title="Bill Payment"` |
| `'Enter Acct ID:'` + `ACTIDIN` (11, unprotected) | 6 | label + `maxLength={11}` input |
| `'Your current balance is: '` + `CURBAL` (14, protected) | 8 | label + read-only span filled from `currentBalance` |
| `'Do you want to pay your balance now. Please confirm: '` + `CONFIRM` (1) + `'(Y/N)'` | 12 | label + `maxLength={1}` input + `(Y/N)` span |
| `ERRMSG` (78, red; green on success) | 23 | message div coloured from `messageColour` |
| `'ENTER=Continue  F3=Back  F4=Clear'` | 24 | `pfKeys` prop |

Cursor placement (`MOVE -1 TO ACTIDINL / CONFIRML`) is reproduced by focusing the field named in the
response's `cursor`, or the confirm field when the rejected turn is the confirm edit.

## 4. Preserved quirks

Q-1 partial payment above 10⁹ · Q-2 no balance on a rejected confirm · Q-3 missing xref reported as
missing account · Q-4 no numeric edit on the account id · Q-5 the success screen sent twice (one HTTP
response — the second SEND repaints the identical map) · Q-6 the `STARTBR NOTFND` leg
(`Transaction ID NOT found...`) is unreachable, an empty table is the ENDFILE case · Q-7 the silent
decline · Q-8 the `CDEMO-CB00-TRN-SELECTED` auto-submit path. None of them is corrected; each is a
numbered requirement (FR-BP-50 … FR-BP-55).

## 5. Boundary decisions

1. **Infrastructure-only messages are not migrated as behaviour.** `Unable to lookup Account...`,
   `Unable to Update Account...`, `Unable to lookup XREF AIX file...`, `Unable to lookup
   Transaction...` and `Unable to Add Bill pay Transaction...` are the CICS `RESP` legs for a failing
   VSAM call. With JPA the equivalent is an infrastructure exception (500); there is no relational
   condition that reproduces them. They are recorded in §F of the program FR rather than faked.
2. **`Invalid key pressed. Please see below...`** cannot be raised: the migrated screen offers ENTER,
   F3 and F4 and a browser has no PA keys or CLEAR (FR-BP-06).
3. **Q-8 auto-submit is dead code.** `CDEMO-CB00-TRN-SELECTED` (cbl:116-121) is set by no program in
   the estate, so it is documented, not implemented.
4. **Timestamp format.** COBIL00C writes `YYYY-MM-DD HH:MM:SS.000000` (space and colons), while the
   CT02 demo seed rows carry the batch `YYYY-MM-DD-HH.MM.SS.000000` form. The estate itself is
   inconsistent; the migrated CB00 writes what COBIL00C writes (FR-BP-36).
5. **Seed-data contradiction (reported, not fixed).** `R__seed_carddemo_data.sql` loads accounts and
   card cross-references for ids 1–50 from `app/data/ASCII/**`, while
   `R__seed_transaction_demo_data.sql` adds five CT02 cross-reference rows for accounts
   `10000000001`–`10000000005` that have **no** row in `accounts`. Those five ids therefore reach the
   CXACAIX lookup but fail the ACCTDAT read. This is a seed inconsistency owned by S-04/foundation,
   not by S-06, so the stream's tests use the ASCII-derived account `1` and the seeds are left
   untouched.

## 6. Deferred

* **No frontend tests.** The repository has no frontend test harness (no `@testing-library/*`, no
  test files — the S-04 reference stream ships none either), so FR-BP-02/03/04/05, the cursor rule
  and the label reproduction are evidenced by the mapping table in §3 and by a green
  `npm run build`, not by an automated assertion. Adding a harness would mean adding shared
  dev-dependencies and a lockfile change outside this stream's lane; it is left to the foundation.
* **Multi-user contention.** VSAM `READ ... UPDATE` held an exclusive lock for the duration of the
  task. The migrated service relies on the transaction's read-committed semantics; two concurrent
  payments on the same account would both read the same balance. The legacy program had the same
  exposure only in a narrower window. No optimistic-locking column was added, because the shared
  entities are read-only for streams — flagged for the foundation.

## 7. How to verify

```bash
cd migration/carddemo/backend && mvn -B test          # 187 tests green, 30 of them S-06
cd migration/carddemo/frontend && npm ci && npm run build
```

# COBIL00C — functional requirements (transaction CB00, Bill Payment)

Numbered, testable requirements for the only program in stream **S-06 BillPayment**. Each
requirement cites `app/cbl/COBIL00C.cbl` (bare line numbers), `app/bms/COBIL00.bms` (`bms:`) or a
copybook, and names the test that proves it. Background and quirk analysis:
[`../BillPayment_analysis.md`](../BillPayment_analysis.md).

Message text, labels and numeric edits are reproduced **verbatim**; trailing `...` and the double
space in the success message are part of the literal.

## Terminology

*Turn* = one ENTER/PF interaction. COBIL00C reaches the server only on ENTER — PF3 navigates and PF4
blanks the map without touching a file — so an ENTER turn is one `POST /api/billpay/screen` call and
the PF keys are handled entirely by the React screen. The server holds no CB00 state between turns,
exactly like the pseudo-conversational program.

Implementation: backend `com.carddemo.billpayment`, screen
`frontend/src/pages/billpayment/BillPaymentPage.js`. Requirements whose test column says *React
screen* are screen-rendering rules; the repository ships no frontend test harness (neither does the
S-04 reference stream), so they are evidenced by the BMS-to-JSX mapping table in the migration plan
and by the frontend build — see the plan's *Deferred* section.

---

## A. Navigation and screen shell

| # | Requirement | Source | Test |
|---|---|---|---|
| FR-BP-01 | With no commarea (no established session context) the program transfers to `COSGN00C` and does not render the Bill Payment screen. | 107-109, 275-284 | shell-provided: the registry entry renders behind `RequireSignOn` (S-01), which sends an unauthenticated visitor to the COSGN00C route. No S-06 code. |
| FR-BP-02 | First entry renders an empty screen: account id, balance, confirm and message all blank, cursor on the account field. It performs no file access. | 112-122 | React screen (`EMPTY_SCREEN`) |
| FR-BP-03 | The header carries `Tran: CB00`, `Prog: COBIL00C` and the current date/time, plus the `CCDA-TITLE01`/`CCDA-TITLE02` titles. | 319-338, `bms:34-74` | React screen (shared `Layout`/`Header`, `tranId="CB00"`, `progName="COBIL00C"`) |
| FR-BP-04 | PF3 returns to `CDEMO-FROM-PROGRAM`, or to `COMEN01C` when the caller is blank. Every estate path into CB00 is the main menu (option 10, `app/cpy/COMEN02Y.cpy`), so the migrated screen returns to `/menu`. | 128-135 | React screen (`backToCaller`) |
| FR-BP-05 | PF4 blanks account id, balance, confirm and the message, and leaves the cursor on the account field. No file is read. | 136-137, 552-566 | React screen (`clearScreen`) |
| FR-BP-06 | Any other AID (PF1/PF2/PF5…PF12, PA keys, CLEAR) produces `Invalid key pressed. Please see below...`. | 138-141, `app/cpy/CSMSG01Y.cpy` | not reachable after migration — see §F |
| FR-BP-07 | Every turn ends by re-displaying the same screen — there is no other exit — so no ENTER path may abort the transaction. | 146-149, 242 | `BillPaymentControllerTest` (every turn returns a screen body or an ERRMSG body the screen repaints) |

## B. ENTER — input edits

| # | Requirement | Source | Test |
|---|---|---|---|
| FR-BP-10 | A blank/omitted account id yields `Acct ID can NOT be empty...`, cursor on the account field, and **no** file access. | 158-167 | `BillPaymentServiceTest.frBp10_emptyAccountId`, `BillPaymentControllerTest.frBp10_emptyAccountIdIsBadRequest` |
| FR-BP-11 | The confirm field is evaluated on its raw single character: `Y`/`y` = pay, `N`/`n` = clear, blank = balance inquiry, anything else = invalid. The test is case-exact — only the two letter cases listed are accepted. | 173-191 | `BillPaymentServiceTest.frBp11_confirmCharacterEvaluation` |
| FR-BP-12 | Confirm `N`/`n` blanks all fields and shows **no message at all** (quirk Q-7); no account is read and no payment is made. | 178-181, 552-566 | `BillPaymentServiceTest.frBp12_confirmNo_clearsScreenSilently`, `BillPaymentControllerTest.frBp12_declineClearsTheScreen` |
| FR-BP-13 | Any other confirm character yields `Invalid value. Valid values are (Y/N)...` with the cursor on the confirm field; the account is **not** read and the balance stays blank (quirk Q-2). | 185-190 | `BillPaymentServiceTest.frBp13_confirmInvalid`, `BillPaymentControllerTest.frBp13_invalidConfirmIsBadRequest` |
| FR-BP-14 | The account id is never edited for numerics (unlike COTRN02C). A non-numeric or wrong-length entry is passed to the read and surfaces as `Account ID NOT found...` (quirk Q-4). | 170-171, 359-364 | `BillPaymentServiceTest.frBp14_nonNumericAccountId` |

## C. ENTER — account lookup and balance display

| # | Requirement | Source | Test |
|---|---|---|---|
| FR-BP-20 | A confirm of blank or `Y`/`y` reads ACCTDAT by account id for update. | 176-184, 345-354 | `BillPaymentServiceTest.frBp24_balanceInquiry_showsBalanceAndWritesNothing`, `frBp38_paymentWritesTheRowAndZeroesTheBalance` |
| FR-BP-21 | A missing ACCTDAT record yields `Account ID NOT found...` with the cursor on the account field. | 359-364 | `BillPaymentServiceTest.frBp21_accountNotFound`, `BillPaymentControllerTest.frBp21_unknownAccountIsNotFound` |
| FR-BP-22 | The balance is displayed as `WS-CURR-BAL PIC +9999999999.99` — a leading sign, ten zero-padded integer digits, `.`, two decimals (e.g. `194.00` → `+0000000194.00`, `-3.5` → `-0000000003.50`). | 56, 193-194, `bms:103-106` | `BillPaymentFormatTest.frBp22_currBalEdited` |
| FR-BP-23 | A balance of zero or less yields `You have nothing to pay...`, cursor on the account field — **and the balance is still displayed** on that turn. | 197-206 | `BillPaymentServiceTest.frBp23_nothingToPay_stillShowsTheBalance` |
| FR-BP-24 | A positive balance with a blank confirm is a balance inquiry: the balance is shown together with `Confirm to make a bill payment...`, cursor on the confirm field, and nothing is written. | 236-240 | `BillPaymentServiceTest.frBp24_balanceInquiry_showsBalanceAndWritesNothing`, `BillPaymentControllerTest.frBp24_balanceInquiryOverHttp` |

## D. ENTER — the payment

| # | Requirement | Source | Test |
|---|---|---|---|
| FR-BP-30 | Confirm `Y`/`y` reads the CXACAIX alternate index by account id to obtain `XREF-CARD-NUM`. | 211, 410-418 | `BillPaymentServiceMockTest.frBp30_resolvesCardFromXref` |
| FR-BP-31 | A missing CXACAIX entry yields `Account ID NOT found...` — the same text as a missing account (quirk Q-3) — and nothing is written. | 423-428 | `BillPaymentServiceMockTest.frBp31_xrefMissing_reportsAccountNotFoundAndWritesNothing` |
| FR-BP-32 | The new transaction id is the highest existing TRANSACT key + 1, rendered as 16 zero-padded digits (`STARTBR HIGH-VALUES` + `READPREV`). | 212-219 | `BillPaymentServiceMockTest.frBp32_tranIdIsMaxPlusOne`, `BillPaymentFormatTest.frBp32_tranIdIsSixteenZeroPaddedDigits` |
| FR-BP-33 | On an empty TRANSACT file the browse returns ENDFILE, the key is treated as zero and the first payment is written as `0000000000000001`. | 487-488 | `BillPaymentServiceMockTest.frBp33_emptyTransactFile_firstIdIsOne` |
| FR-BP-34 | The written record carries the fixed literals: type `02`, category `2`, source `POS TERM`, description `BILL PAYMENT - ONLINE`, merchant id `999999999`, merchant name `BILL PAYMENT`, merchant city `N/A`, merchant zip `N/A`, card number from the xref. | 218-229 | `BillPaymentServiceMockTest.frBp34_recordLiterals` |
| FR-BP-35 | The transaction amount is the account balance moved into `TRAN-AMT PIC S9(09)V99`: the sign and the two decimals are kept and any digit above 10⁹ is **truncated** (quirk Q-1). A balance of `1234567890.12` writes `234567890.12`. | 224 | `BillPaymentServiceMockTest.frBp35_amountTruncatedToNineDigits`, `BillPaymentFormatTest.frBp35_tranAmountTruncatesTheTenthDigit` |
| FR-BP-36 | `TRAN-ORIG-TS` and `TRAN-PROC-TS` are identical and formatted `YYYY-MM-DD HH:MM:SS.000000` — a blank date/time separator and always-zero microseconds. | 230-232, 249-267, `app/cpy/CSDAT01Y.cpy:42-55` | `BillPaymentServiceMockTest.frBp36_timestampFormat`, `BillPaymentFormatTest.frBp36_timestampHasBlankSeparatorAndZeroMicroseconds` |
| FR-BP-37 | If the generated id already exists the payment fails with `Tran ID already exist...` and neither the transaction nor the balance is persisted. | 533-539 | `BillPaymentServiceMockTest.frBp37_duplicateTranId` |
| FR-BP-38 | After a successful write the balance becomes `ACCT-CURR-BAL - TRAN-AMT` and the account is rewritten. For any balance below 10⁹ this lands exactly on `0.00`; above it the truncated remainder stays on the account (quirk Q-1). Both operands are scale-2, so no rounding occurs. | 234-235, 379-385 | `BillPaymentServiceMockTest.frBp38_balanceReducedByAmount`, `frBp35_amountTruncatedToNineDigits`; `BillPaymentServiceTest.frBp38_paymentWritesTheRowAndZeroesTheBalance` |
| FR-BP-39 | A successful payment shows `Payment successful.  Your Transaction ID is <16-digit id>.` (two spaces after the first period) in **green**, with account id, balance and confirm blanked. | 522-532 | `BillPaymentServiceMockTest.frBp39_successMessage`, `BillPaymentControllerTest.frBp39_successMessageOverHttp` |
| FR-BP-40 | The write and the balance rewrite are one unit of work: if either fails, neither is persisted (the CICS task's implicit syncpoint). | 233-235 | `BillPaymentAtomicityTest.frBp40_failedAccountUpdateRollsBackTheTransactionWrite` |

## E. Preserved quirks (each is behaviour, not a defect)

| # | Requirement | Quirk | Test |
|---|---|---|---|
| FR-BP-50 | A balance ≥ 10⁹ is only partly paid, because `TRAN-AMT` is one digit narrower than `ACCT-CURR-BAL`. | Q-1 | `BillPaymentServiceMockTest.frBp35_amountTruncatedToNineDigits` |
| FR-BP-51 | After `N` or an invalid confirm the balance field is not populated from a fresh read. | Q-2 | `BillPaymentServiceTest.frBp13_confirmInvalid` |
| FR-BP-52 | A missing xref is reported as a missing account. | Q-3 | `BillPaymentServiceMockTest.frBp31_xrefMissing_reportsAccountNotFoundAndWritesNothing` |
| FR-BP-53 | A non-numeric account id gives a "not found", never a numeric-format message. | Q-4 | `BillPaymentServiceTest.frBp14_nonNumericAccountId` |
| FR-BP-54 | The decline (`N`) turn shows no message. | Q-7 | `BillPaymentServiceTest.frBp12_confirmNo_clearsScreenSilently` |
| FR-BP-55 | `CDEMO-CB00-TRN-SELECTED` auto-submit is dead code in the estate and is **not** migrated. | Q-8 | documented in the migration plan; no code, no test |

## F. Not applicable after migration

Legacy paths that exist only because of VSAM/CICS mechanics and have no counterpart in the
relational target. They are listed so the sign-off oracle is complete.

| Legacy path | Message | Disposition |
|---|---|---|
| ACCTDAT read RESP other than NORMAL/NOTFND | `Unable to lookup Account...` | infrastructure failure; surfaces as a 500 from the JPA layer (no VSAM RESP equivalent) |
| ACCTDAT rewrite RESP other than NORMAL/NOTFND | `Unable to Update Account...` | as above |
| CXACAIX read RESP other than NORMAL/NOTFND | `Unable to lookup XREF AIX file...` | as above |
| `STARTBR` NOTFND | `Transaction ID NOT found...` | unreachable: a `SELECT ... ORDER BY id DESC LIMIT 1` on an empty table is the ENDFILE case (FR-BP-33), not a NOTFND (quirk Q-6) |
| `READPREV`/`ENDBR` RESP other than NORMAL/ENDFILE | `Unable to lookup Transaction...` | infrastructure failure |
| `WRITE` RESP other than NORMAL/DUPKEY/DUPREC | `Unable to Add Bill pay Transaction...` | infrastructure failure |
| Screen sent twice on success (532 then 242) | — | one HTTP response carries the identical final screen (quirk Q-5) |
| Any AID other than ENTER/PF3/PF4 (FR-BP-06) | `Invalid key pressed. Please see below...` | the screen has three controls (ENTER, F3, F4) and a browser has no PA keys or CLEAR, so no other AID can be raised; the text is kept in the analysis for the sign-off record only |

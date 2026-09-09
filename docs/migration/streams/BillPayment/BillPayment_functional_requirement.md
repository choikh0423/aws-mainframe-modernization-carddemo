# S-06 BillPayment — stream functional requirements

The business sign-off oracle for stream **S-06 BillPayment**: what the migrated CardDemo must do
when a cardholder pays their outstanding balance online. The stream contains exactly one program,
`COBIL00C` (transaction **CB00**, map **COBIL0A**), so the screen-level rules — every field edit,
every message string, every file access — are enumerated in
[`programs/COBIL00C_functional_requirement.md`](programs/COBIL00C_functional_requirement.md)
(FR-BP-01 … FR-BP-55). This document states the stream-level requirements those rules serve and maps
each one onto them; the source evidence is in
[`BillPayment_analysis.md`](BillPayment_analysis.md) and the implementation and boundary decisions
are in [`BillPayment_migration_plan.md`](BillPayment_migration_plan.md).

Every message, label and numeric edit quoted here is verbatim COBOL/BMS text, `...` included.

## Scope

| In scope | Out of scope |
|---|---|
| `app/cbl/COBIL00C.cbl`, `app/bms/COBIL00.bms`, transaction CB00 | every other program (other streams) |
| ACCTDAT balance read and update, TRANSACT payment write, CXACAIX card lookup | statement, interest and posting batch, which consume the written transaction |
| Main-menu option 10 reaching the screen | the menu itself (S-01) |

## Stream requirements

| # | Requirement | Realised by |
|---|---|---|
| SR-BP-1 | A signed-on user reaches Bill Payment from main-menu option 10 and sees an empty CB00 screen with the Acct ID, current balance and confirm fields, the `(Y/N)` prompt and the `ENTER=Continue  F3=Back  F4=Clear` key line. Unauthenticated access is refused to the sign-on screen. | FR-BP-01, FR-BP-02, FR-BP-03 |
| SR-BP-2 | Entering an account id alone is a **balance inquiry**: the current balance is displayed, signed and zero-padded (`+0000000194.00`), together with `Confirm to make a bill payment...`. Nothing is written. | FR-BP-20, FR-BP-22, FR-BP-24 |
| SR-BP-3 | An unknown or unusable account id is refused with `Account ID NOT found...`; a blank one with `Acct ID can NOT be empty...`. No numeric edit is applied to the field. | FR-BP-10, FR-BP-14, FR-BP-21 |
| SR-BP-4 | An account whose balance is zero or negative cannot pay: `You have nothing to pay...`, with the balance still shown. | FR-BP-23 |
| SR-BP-5 | Payment happens only on an explicit `Y`/`y` confirmation. `N`/`n` abandons the screen silently, blank re-prompts, anything else is refused with `Invalid value. Valid values are (Y/N)...`. | FR-BP-11, FR-BP-12, FR-BP-13 |
| SR-BP-6 | A confirmed payment pays the **whole** displayed balance: one TRANSACT record for that amount against the account's card, and the account balance reduced by the same amount. | FR-BP-30, FR-BP-34, FR-BP-35, FR-BP-38 |
| SR-BP-7 | The payment record is identifiable as an online bill payment: type `02`, category `2`, source `POS TERM`, description `BILL PAYMENT - ONLINE`, merchant `999999999` / `BILL PAYMENT` / `N/A` / `N/A`, both timestamps set to the moment of payment. | FR-BP-34, FR-BP-36 |
| SR-BP-8 | Transaction ids stay dense and gap-free: the new id is the highest existing TRANSACT key + 1 as 16 zero-padded digits, and `0000000000000001` on an empty file. A collision is refused with `Tran ID already exist...`. | FR-BP-32, FR-BP-33, FR-BP-37 |
| SR-BP-9 | The payment is all-or-nothing: a failure to write the transaction or to update the balance leaves both unchanged. | FR-BP-37, FR-BP-40 |
| SR-BP-10 | A completed payment is confirmed with `Payment successful.  Your Transaction ID is <id>.` in green, on a blanked screen ready for the next payment. | FR-BP-39 |
| SR-BP-11 | The user can leave (F3, back to the menu) or blank the screen (F4) at any time without side effects. | FR-BP-04, FR-BP-05 |
| SR-BP-12 | Legacy behaviour is preserved even where it is surprising: the partial payment of a balance ≥ 1,000,000,000.00, the silent decline, the "not found" reported for a missing card cross-reference, and the absent numeric edit. These are behaviour, not defects, and are not silently corrected. | FR-BP-50 … FR-BP-54 (quirks Q-1 … Q-8 in the analysis) |

## Traceability

Every stream requirement above decomposes into numbered program requirements, and every program
requirement names the automated test that proves it (`migration/carddemo/backend/src/test/java/com/carddemo/billpayment/**`).
The requirements not covered by an automated test are exactly those the program FR marks
*React screen*, *shell-provided* or *not reachable after migration*; the migration plan's *Deferred*
section explains each one.

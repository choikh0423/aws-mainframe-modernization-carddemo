# Transaction Management — Screen Recording Checklist (Verify phase, parity OFF)

Because parity mode is OFF, correctness is demonstrated by JUnit/E2E tests **plus** screen recordings of the migrated React UI proving each functional requirement matches legacy CardDemo behavior. Each case below maps to one or more FRs from `TransactionManagement_functional_requirement.md`.

## How to run the app for recording
- Backend: `cd migration/transaction-management/backend && mvn spring-boot:run` (H2 in-mem seeded with 30 transactions + card_xref rows; serves on `:8080`).
- Frontend: `cd migration/transaction-management/frontend && npm install && npm start` (CRA dev server on `:3000`, proxies `/api` → `:8080`).

## Business sign-off recording set
| REC | Screen | Steps | Expected (FR) |
|-----|--------|-------|---------------|
| REC-L-1 | CT00 List | Open `/transactions` | 10 rows, header, page 1, hasPrev=off (FR-L1) |
| REC-L-2 | CT00 List | Enter a start Tran ID, ENTER | List begins at/after it (FR-L2) |
| REC-L-3 | CT00 List | Click Next (PF8) to last page, Next again | Advances; "You have reached the bottom of the page..." at end (FR-L3) |
| REC-L-4 | CT00 List | Click Prev (PF7) on page 1 | "You are already at the top of the page..." (FR-L4) |
| REC-L-5 | CT00 List → View | Type `S` on a row, ENTER | Opens that txn in CT01 View (FR-L5) |
| REC-L-6 | CT00 List | Type a non-`S` flag | "Invalid selection. Valid value is S" (FR-L6) |
| REC-L-7 | CT00 List | Enter non-numeric filter | "Tran ID must be Numeric ..." (FR-L7) |
| REC-V-1 | CT01 View | Enter a valid Tran ID, ENTER | All fields shown (FR-V1) |
| REC-V-2 | CT01 View | Enter a non-existent Tran ID | "Transaction ID NOT found..." (FR-V2) |
| REC-V-3 | CT01 View | ENTER with empty Tran ID | "Tran ID can NOT be empty..." (FR-V3) |
| REC-V-4 | CT01 View | Press PF4 | Fields cleared (FR-V4) |
| REC-V-5 | CT01 View | Press PF5 | Returns to CT00 List (FR-V5) |
| REC-A-1 | CT02 Add | Enter Account ID | Card Number auto-resolved (FR-A1) |
| REC-A-2 | CT02 Add | Enter Card Number | Account ID auto-resolved (FR-A2) |
| REC-A-3 | CT02 Add | Leave both empty, submit | "Account or Card Number must be entered..." (FR-A3) |
| REC-A-6 | CT02 Add | Fill all fields, Confirm=Y, submit | "Transaction added successfully. Your Tran ID is <id>." (FR-A6) |
| REC-A-7/8/9/10 | CT02 Add | Trigger empty/numeric/amount/date errors | Exact field messages (FR-A7..A10) |
| REC-A-11 | CT02 Add | Confirm blank / non Y-N | "Confirm to add this transaction..." / "Invalid value. Valid values are (Y/N)..." (FR-A11) |
| REC-A-12 | CT02 Add | Press PF5 copy-last | Form pre-filled from latest txn (FR-A12) |
| REC-A-cross | Add → List | Add a txn, then open List | New row appears at max-key+1 (FR-A6 + FR-L1) |

## Status — captured 2026-07-14
The business sign-off golden-path recording (`txnmgmt-goldenpath`) was captured against the running app (backend `:8080` + React `:3000`) and covers, in order:

| REC captured | FR | Evidence in video |
|--------------|----|-------------------|
| REC-L-1 | FR-L1 | CT00 shows 10 rows, page 1, PF7 disabled, signed amounts + MM/DD/YY dates |
| REC-L-5 | FR-L5 | Typed `S` on row 1 → routed to CT01 view of that txn |
| REC-V-1 | FR-V1 | CT01 shows all CVTRA05Y fields (card, type, amount, merchant, timestamps) |
| REC-V-4 | FR-V4 | PF4 cleared the CT01 fields |
| REC-V-2 | FR-V2 | Unknown id → exact "Transaction ID NOT found..." |
| REC-A-1 | FR-A1 | Account `10000000001` auto-resolved card `4111111111111111` |
| REC-A-6 | FR-A6 | Confirm=Y → "Transaction added successfully.  Your Tran ID is 0000000000000031." (max-key+1) |
| REC-A-cross | FR-A6 + FR-V1 | New txn 31 persisted; reloaded in CT01 with all entered fields |

The remaining error/boundary cases (REC-L-2/3/4/6/7, REC-V-3/5, REC-A-2/3/7..12) are each covered by a named JUnit/E2E test asserting the exact legacy message (see the FR traceability matrix in `TransactionManagement_functional_requirement.md`); they can be recorded on request.

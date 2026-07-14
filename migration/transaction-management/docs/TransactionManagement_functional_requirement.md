# Transaction Management — Functional Requirements (Step 2a-FR: generate_fr_transaction)

**Stream:** Transaction Management (CT00 / CT01 / CT02)
**SOURCE:** `choikh0423/aws-mainframe-modernization-carddemo` @ `main` — `app/cbl/COTRN00C.cbl`, `COTRN01C.cbl`, `COTRN02C.cbl`
**Role:** This document is the **acceptance / sign-off oracle**. Each functional requirement (FR) is a business trigger → user-visible result, cited to source. Screen recordings and tests must trace back to these FRs.

---

## A. Functional requirements (business-visible)

### CT00 — Transaction List
| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-L1 | Open the transaction list | See up to 10 transactions (Tran ID, date MM/DD/YY, description, signed amount) with header (title/date/time/program) and page number | COTRN00C:279-326, 381-445 |
| FR-L2 | Enter a starting Tran ID and press ENTER | List begins at/after that Tran ID | COTRN00C:206-225, 281 |
| FR-L3 | Press PF8 | Go to the next page of 10; if already at the end see "You have reached the bottom of the page..." | COTRN00C:257-274, 639-645 |
| FR-L4 | Press PF7 | Go to the previous page; if already at the top see "You are already at the top of the page..." | COTRN00C:234-252 |
| FR-L5 | Type `S` beside a row and press ENTER | Open that transaction in the View screen (CT01) | COTRN00C:183-204 |
| FR-L6 | Type a non-`S` selection flag | See "Invalid selection. Valid value is S" | COTRN00C:196-203 |
| FR-L7 | Enter a non-numeric Tran ID filter | See "Tran ID must be Numeric ..." | COTRN00C:209-218 |
| FR-L8 | Press PF3 | Return to the main menu | COTRN00C:122-124 |

### CT01 — Transaction View
| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-V1 | Enter a Tran ID and press ENTER | See all transaction details: Tran ID, card #, type, category, source, amount, description, orig/proc timestamps, merchant id/name/city/zip | COTRN01C:158-192 |
| FR-V2 | Enter a Tran ID that does not exist | See "Transaction ID NOT found..." | COTRN01C:283-288 |
| FR-V3 | Press ENTER with an empty Tran ID | See "Tran ID can NOT be empty..." | COTRN01C:147-152 |
| FR-V4 | Press PF4 | Clear the screen fields | COTRN01C:123-124, 301-304 |
| FR-V5 | Press PF5 | Return to the transaction list (CT00) | COTRN01C:125-127 |
| FR-V6 | Press PF3 | Return to the caller/menu | COTRN01C:115-122 |
| FR-V7 | Arrive from the list via `S` | The selected Tran ID is auto-loaded and displayed | COTRN01C:103-108 |

### CT02 — Transaction Add
| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-A1 | Enter an Account ID | Card Number is auto-resolved from the card cross-reference | COTRN02C:196-209, 576-604 |
| FR-A2 | Enter a Card Number | Account ID is auto-resolved from the card cross-reference | COTRN02C:210-223, 609-637 |
| FR-A3 | Leave both Account and Card empty | See "Account or Card Number must be entered..." | COTRN02C:224-229 |
| FR-A4 | Enter an Account ID not in the xref | See "Account ID NOT found..." | COTRN02C:591-596 |
| FR-A5 | Enter a Card Number not in the xref | See "Card Number NOT found..." | COTRN02C:624-629 |
| FR-A6 | Fill all fields, set Confirm=Y, press ENTER | Transaction is written; see green "Transaction added successfully. Your Tran ID is <id>." | COTRN02C:169-172, 442-466, 723-734 |
| FR-A7 | Leave a required field empty | See the specific "<field> can NOT be empty..." message | COTRN02C:251-320 |
| FR-A8 | Enter a non-numeric Type/Category/Merchant ID | See the specific "<field> must be Numeric..." message | COTRN02C:322-337, 430-436 |
| FR-A9 | Enter amount not matching `-99999999.99` | See "Amount should be in format -99999999.99" | COTRN02C:339-351 |
| FR-A10 | Enter a date not matching `YYYY-MM-DD` / not a real date | See "Orig/Proc Date should be in format YYYY-MM-DD" or "…Not a valid date..." | COTRN02C:353-427 |
| FR-A11 | Leave Confirm blank / not Y-N | See "Confirm to add this transaction..." (blank) or "Invalid value. Valid values are (Y/N)..." | COTRN02C:169-188 |
| FR-A12 | Press PF5 (copy last) | The form is pre-filled with the most recent transaction's data | COTRN02C:471-495 |
| FR-A13 | Press PF4 | Clear the form | COTRN02C:754-757 |
| FR-A14 | Press PF3 | Return to the caller/menu | COTRN02C |

**Business rule (key generation):** a new transaction's Tran ID = (highest existing Tran ID) + 1, 16-digit numeric (COTRN02C:444-451). Duplicate → "Tran ID already exist..." (COTRN02C:735-741).

## B. Screen / UI field spec
See the `uiSurface` section of `TransactionManagement_analysis.md` §4 for the per-screen field list, lengths, PF-key map, and formatting (amount `+99999999.99`, date `MM/DD/YY` on list/view, `YYYY-MM-DD` input on add).

## C. Validation / error catalogue (exact text)
CT00: "Tran ID must be Numeric ...", "Invalid selection. Valid value is S", "You are already at the top of the page...", "You are already at the bottom of the page...", "You are at the top of the page...", "You have reached the bottom of the page...".
CT01: "Tran ID can NOT be empty...", "Transaction ID NOT found...", "Unable to lookup Transaction...".
CT02: "Account or Card Number must be entered...", "Account ID must be Numeric...", "Card Number must be Numeric...", "Account ID NOT found...", "Card Number NOT found...", "<Field> can NOT be empty..." (Type CD/Category CD/Source/Description/Amount/Orig Date/Proc Date/Merchant ID/Merchant Name/Merchant City/Merchant Zip), "Type CD must be Numeric...", "Category CD must be Numeric...", "Merchant ID must be Numeric...", "Amount should be in format -99999999.99", "Orig Date should be in format YYYY-MM-DD", "Proc Date should be in format YYYY-MM-DD", "Orig/Proc Date - Not a valid date...", "Confirm to add this transaction...", "Invalid value. Valid values are (Y/N)...", "Tran ID already exist...", "Transaction added successfully. Your Tran ID is <id>.".

## D. Acceptance criteria
1. Every FR above is reproducible on the migrated React UI against the Spring Boot/H2 backend with the same user-visible result and message text.
2. Amounts and dates render in the same formats as the legacy screens.
3. CT02 writes a persisted row (verified at the DB) with the max-key+1 Tran ID; the same seeded xref data resolves account↔card.
4. Paging on CT00 returns exactly 10 rows/page with correct boundary messages.
5. No behavior beyond the hard-stop boundary (posting/interest/statements/auth) is added.

## E. Traceability matrix (requirement ↔ source ↔ test ↔ UI-verification)
| FR | Source cite | Backend test (to author) | UI-verification case |
|----|-------------|--------------------------|----------------------|
| FR-L1..L8 | COTRN00C | `TransactionListControllerIT` / `...ServiceTest` (paging, filter, select, numeric filter) | REC-L-* |
| FR-V1..V7 | COTRN01C | `TransactionViewControllerIT` / `...ServiceTest` (found, not-found, empty) | REC-V-* |
| FR-A1..A14 | COTRN02C | `TransactionAddControllerIT` / `TransactionValidatorTest` (resolve, validation, date, confirm, key-gen, copy-last) | REC-A-* |

*(REC-* case ids are defined in `TransactionManagement_screen_recording_checklist.md` during the Verify phase.)*

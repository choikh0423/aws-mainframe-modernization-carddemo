# COTRN02C (CT02) — Transaction Add: CICS Business Use Case Flow Analysis

---

## Section 1: Use Case Overview

| Attribute        | Value                                                                 |
|------------------|-----------------------------------------------------------------------|
| **Use Case Name**    | Add Credit Card Transaction                                       |
| **Use Case ID**      | UC-CT02-001                                                       |
| **Actor(s)**         | Card Operations Clerk (Regular User)                              |
| **Business Goal**    | Record a new financial transaction against a customer's credit card account |
| **Preconditions**    | 1. User is authenticated and logged in<br>2. User has "Regular User" privileges<br>3. A valid account and/or card exists in the system |
| **Postconditions**   | A new transaction record is persisted with a system-generated unique Transaction ID |
| **Trigger**          | User selects "Transaction Add" (Option 8) from the Main Menu      |
| **Data Access**      | Read-Write (reads cross-reference data; writes new transaction)    |
| **Frequency**        | High — multiple times per day per clerk during transaction entry cycles |

### Business Context

The Transaction Add use case is a core data entry function in the CardDemo credit card management system. Operations clerks use this screen to manually record financial transactions — purchases, credits, payments, and adjustments — against customer accounts. The clerk identifies the target account by either Account ID or Card Number, fills in transaction details (type, category, amount, merchant information, dates), and confirms the addition. The system automatically generates a unique Transaction ID and persists the record.

This use case is part of the **Transaction Management** functional domain, which also includes viewing transaction lists and individual transaction details.

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CardDemo Functional Domains                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐   │
│  │  Account     │  │  Card        │  │  Transaction           │   │
│  │  Management  │  │  Management  │  │  Management            │   │
│  │              │  │              │  │                        │   │
│  │  - View      │  │  - List      │  │  - List (CT01)         │   │
│  │  - Update    │  │  - Detail    │  │  - View                │   │
│  │              │  │  - Update    │  │  ★ ADD (CT02) ★        │   │
│  └──────────────┘  └──────────────┘  └────────────────────────┘   │
│                                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐   │
│  │  Bill        │  │  Reports     │  │  User Administration   │   │
│  │  Payment     │  │              │  │  (Admin only)          │   │
│  └──────────────┘  └──────────────┘  └────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Section 2: User Journey (Happy Path)

### Sequence Diagram

```
┌──────┐                                    ┌──────────┐
│ User │                                    │  System  │
└──┬───┘                                    └────┬─────┘
   │                                             │
   │  Select "Transaction Add" from Main Menu    │
   │────────────────────────────────────────────>│
   │                                             │
   │  Display blank Add Transaction screen       │
   │<────────────────────────────────────────────│
   │                                             │
   │  Enter Account ID (or Card Number)          │
   │  Enter all transaction detail fields        │
   │  Type 'Y' in Confirm field                  │
   │  Press ENTER                                │
   │────────────────────────────────────────────>│
   │                                             │
   │         Validate account/card exists        │
   │         Validate all input fields           │
   │         Generate new Transaction ID         │
   │         Save transaction record             │
   │                                             │
   │  Display success message with new Tran ID   │
   │  Clear all input fields for next entry      │
   │<────────────────────────────────────────────│
   │                                             │
   │  (Continue entering or press F3 to exit)    │
   │────────────────────────────────────────────>│
   │                                             │
```

### Step-by-Step Narrative

| Step | Actor  | Action                                             | System Response                                                        |
|------|--------|----------------------------------------------------|------------------------------------------------------------------------|
| 1    | User   | Selects Option 8 ("Transaction Add") from Main Menu | System navigates to the Add Transaction screen with all fields blank; cursor is positioned on Account ID field |
| 2    | User   | Enters an Account ID (11-digit number) OR a Card Number (16-digit number) | — (awaiting further input) |
| 3    | User   | Enters Transaction Type Code (2-digit numeric)      | — |
| 4    | User   | Enters Category Code (4-digit numeric)              | — |
| 5    | User   | Enters Source (up to 10 characters)                 | — |
| 6    | User   | Enters Description (up to 60 characters)            | — |
| 7    | User   | Enters Amount in format +/-99999999.99              | — |
| 8    | User   | Enters Origination Date in YYYY-MM-DD format        | — |
| 9    | User   | Enters Processing Date in YYYY-MM-DD format         | — |
| 10   | User   | Enters Merchant ID (9-digit numeric)                | — |
| 11   | User   | Enters Merchant Name (up to 30 characters)          | — |
| 12   | User   | Enters Merchant City (up to 25 characters)          | — |
| 13   | User   | Enters Merchant Zip (up to 10 characters)           | — |
| 14   | User   | Types 'Y' in the Confirm field                      | — |
| 15   | User   | Presses ENTER                                       | System validates account/card exists, validates all fields, generates a unique Transaction ID, saves the record, clears all fields, and displays: "Transaction added successfully. Your Tran ID is XXXXXXXXXXXXXXXX." in green |
| 16   | User   | (Optional) Enters next transaction or presses F3    | If F3: returns to Main Menu. If new entry: cycle repeats from Step 2 |

---

## Section 3: Business Rules & Validations

### Input Validation Rules

| #   | Field             | Rule                                                                                     | Error Message Shown to User                            |
|-----|-------------------|------------------------------------------------------------------------------------------|--------------------------------------------------------|
| V1  | Account ID        | Must be numeric if provided                                                              | "Account ID must be Numeric..."                        |
| V2  | Account ID        | Must correspond to an existing account in the cross-reference                            | "Account ID NOT found..."                              |
| V3  | Card Number       | Must be numeric if provided                                                              | "Card Number must be Numeric..."                       |
| V4  | Card Number       | Must correspond to an existing card in the cross-reference                               | "Card Number NOT found..."                             |
| V5  | Account/Card      | At least one of Account ID or Card Number must be provided                               | "Account or Card Number must be entered..."            |
| V6  | Type Code         | Must not be empty                                                                        | "Type CD can NOT be empty..."                          |
| V7  | Type Code         | Must be numeric (2 digits)                                                               | "Type CD must be Numeric..."                           |
| V8  | Category Code     | Must not be empty                                                                        | "Category CD can NOT be empty..."                      |
| V9  | Category Code     | Must be numeric (4 digits)                                                               | "Category CD must be Numeric..."                       |
| V10 | Source            | Must not be empty                                                                        | "Source can NOT be empty..."                           |
| V11 | Description       | Must not be empty                                                                        | "Description can NOT be empty..."                      |
| V12 | Amount            | Must not be empty                                                                        | "Amount can NOT be empty..."                           |
| V13 | Amount            | Must be in signed decimal format: sign (+ or -), 8 digits, decimal point, 2 digits       | "Amount should be in format -99999999.99"              |
| V14 | Origination Date  | Must not be empty                                                                        | "Orig Date can NOT be empty..."                        |
| V15 | Origination Date  | Must be in YYYY-MM-DD format (4 digits, dash, 2 digits, dash, 2 digits)                  | "Orig Date should be in format YYYY-MM-DD"             |
| V16 | Origination Date  | Must be a valid calendar date                                                            | "Orig Date - Not a valid date..."                      |
| V17 | Processing Date   | Must not be empty                                                                        | "Proc Date can NOT be empty..."                        |
| V18 | Processing Date   | Must be in YYYY-MM-DD format (4 digits, dash, 2 digits, dash, 2 digits)                  | "Proc Date should be in format YYYY-MM-DD"             |
| V19 | Processing Date   | Must be a valid calendar date                                                            | "Proc Date - Not a valid date..."                      |
| V20 | Merchant ID       | Must not be empty                                                                        | "Merchant ID can NOT be empty..."                      |
| V21 | Merchant ID       | Must be numeric (9 digits)                                                               | "Merchant ID must be Numeric..."                       |
| V22 | Merchant Name     | Must not be empty                                                                        | "Merchant Name can NOT be empty..."                    |
| V23 | Merchant City     | Must not be empty                                                                        | "Merchant City can NOT be empty..."                    |
| V24 | Merchant Zip      | Must not be empty                                                                        | "Merchant Zip can NOT be empty..."                     |
| V25 | Confirm           | Must be 'Y' or 'N' (case-insensitive) if entered                                        | "Invalid value. Valid values are (Y/N)..."             |

### Business Rules

| #   | Rule                                                                                                    |
|-----|---------------------------------------------------------------------------------------------------------|
| BR1 | **Account/Card Mutual Resolution**: When an Account ID is entered, the system automatically looks up and displays the corresponding Card Number. When a Card Number is entered, the system automatically looks up and displays the corresponding Account ID. Only one identifier is required. |
| BR2 | **Confirmation Required**: The transaction is only saved when the user explicitly confirms with 'Y'. Entering 'N', leaving the field blank, or pressing ENTER without confirming prompts the user to confirm. |
| BR3 | **Sequential ID Generation**: Each new transaction receives a unique Transaction ID that is one greater than the highest existing Transaction ID in the system. |
| BR4 | **All Fields Mandatory**: Every transaction detail field (Type, Category, Source, Description, Amount, Origination Date, Processing Date, Merchant ID, Merchant Name, Merchant City, Merchant Zip) must be filled in before the transaction can be saved. |
| BR5 | **Data Entry Efficiency — Copy Last Transaction**: The user can press F5 to pre-fill all detail fields with data from the most recently added transaction, reducing repetitive data entry. |
| BR6 | **Screen Clear**: The user can press F4 to clear all input fields and start fresh without leaving the screen. |
| BR7 | **Post-Add Reset**: After a successful transaction add, all input fields are cleared and the screen is ready for the next transaction entry. |
| BR8 | **Key Field Validation First**: Account/Card validation is performed before data field validation. If the account/card lookup fails, all data fields are cleared. |
| BR9 | **Date Semantic Validation**: Dates must not only match the YYYY-MM-DD format but must also represent valid calendar dates (e.g., 2024-02-30 would fail). |
| BR10 | **Amount Normalization**: The entered amount is reformatted to a standardized display format after validation. |

### Decision Table

```
┌──────────────────────────────────────┬──────────────────────────────────────────────────┐
│ User Action                          │ System Response                                   │
├──────────────────────────────────────┼──────────────────────────────────────────────────┤
│ ENTER + all fields valid + Confirm=Y │ Save transaction, clear fields, show success msg │
│ ENTER + all fields valid + Confirm=N │ Display "Confirm to add this transaction..."     │
│ ENTER + all fields valid + Confirm=  │ Display "Confirm to add this transaction..."     │
│ ENTER + all fields valid + Confirm=X │ Display "Invalid value. Valid values are (Y/N)." │
│ ENTER + validation failure           │ Display specific error, cursor on failing field  │
│ F3                                   │ Return to Main Menu (or calling program)         │
│ F4                                   │ Clear all input fields, re-display blank screen  │
│ F5                                   │ Copy last transaction data into fields, validate │
│ Any other key                        │ Display "Invalid key pressed. Please see below." │
└──────────────────────────────────────┴──────────────────────────────────────────────────┘
```

---

## Section 4: Data Entities Involved

### Entity Descriptions

| Entity                  | Description                                                            | Role in This Use Case                                           |
|-------------------------|------------------------------------------------------------------------|-----------------------------------------------------------------|
| Transaction             | A financial event (purchase, credit, payment) recorded against a card  | **Created** — a new transaction record is written               |
| Card-Account Cross-Reference | Links a card number to its owning customer and account            | **Read** — used to validate and resolve account/card identifiers |
| Account (indirect)      | A credit card account with balance, limits, and status                 | Referenced indirectly via cross-reference for validation         |

### Entity Relationships

```
┌───────────────────┐         ┌──────────────────────────┐
│   Account         │ 1    * │  Card-Account            │
│                   │─────────│  Cross-Reference         │
│   (ACCT-ID)       │         │                          │
└───────────────────┘         │  (CARD-NUM, ACCT-ID,     │
                              │   CUST-ID)               │
                              └────────────┬─────────────┘
                                           │ 1
                                           │
                                           │ *
                              ┌─────────────────────────┐
                              │   Transaction           │
                              │                         │
                              │   (TRAN-ID, CARD-NUM,   │
                              │    amount, merchant...) │
                              └─────────────────────────┘

  One Account has many Cards (via cross-reference).
  One Card can have many Transactions.
```

### Data Fields Displayed

| #  | Field             | Format              | Source                   | Description                                 |
|----|-------------------|---------------------|--------------------------|---------------------------------------------|
| 1  | Account ID        | 11-digit numeric    | User input / auto-filled | Identifies the credit card account          |
| 2  | Card Number       | 16-digit numeric    | User input / auto-filled | Identifies the specific card on the account |
| 3  | Type Code         | 2-digit numeric     | User input               | Transaction type classification code        |
| 4  | Category Code     | 4-digit numeric     | User input               | Transaction category classification code    |
| 5  | Source            | Up to 10 chars      | User input               | Origin/channel of the transaction           |
| 6  | Description       | Up to 60 chars      | User input               | Free-text description of the transaction    |
| 7  | Amount            | +/-99999999.99      | User input               | Monetary value (positive or negative)       |
| 8  | Origination Date  | YYYY-MM-DD          | User input               | Date the transaction originated             |
| 9  | Processing Date   | YYYY-MM-DD          | User input               | Date the transaction was processed          |
| 10 | Merchant ID       | 9-digit numeric     | User input               | Unique identifier for the merchant          |
| 11 | Merchant Name     | Up to 30 chars      | User input               | Name of the merchant                        |
| 12 | Merchant City     | Up to 25 chars      | User input               | City where the merchant is located          |
| 13 | Merchant Zip      | Up to 10 chars      | User input               | Postal code for the merchant                |
| 14 | Confirm           | Y or N              | User input               | User confirmation to proceed with add       |

**Fields in the Transaction record but NOT displayed on this screen:**
- Transaction ID (system-generated, shown only in the success message)
- Filler/reserved bytes (20 bytes at end of record)

---

## Section 5: Screen / Interface Description

### Screen Layout

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│Tran: CT02       AWS Mainframe Modernization            Date: 05/07/26            │
│Prog: COTRN02C              CardDemo                    Time: 14:30:22            │
│                                                                                  │
│                              Add Transaction                                     │
│                                                                                  │
│     Enter Acct #: [00012345678]    (or)  Card #: [4000123456789012]              │
│                                                                                  │
│     ----------------------------------------------------------------------       │
│                                                                                  │
│     Type CD: [01] Category CD: [5010] Source: [POS       ]                       │
│                                                                                  │
│     Description: [Grocery purchase at FreshMart Store #42                  ]     │
│                                                                                  │
│     Amount: [-00000125.50] Orig Date: [2024-03-15] Proc Date: [2024-03-16]       │
│              (-99999999.99)            (YYYY-MM-DD)            (YYYY-MM-DD)       │
│     Merchant ID: [123456789] Merchant Name: [FreshMart Store #42            ]    │
│                                                                                  │
│     Merchant City: [Springfield              ] Merchant Zip: [62704     ]        │
│                                                                                  │
│                                                                                  │
│     You are about to add this transaction. Please confirm : [Y] (Y/N)           │
│                                                                                  │
│Transaction added successfully.  Your Tran ID is 0000000000000124.                │
│ENTER=Continue  F3=Back  F4=Clear  F5=Copy Last Tran.                             │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Interface Elements

| Element           | Type    | Description                                              |
|-------------------|---------|----------------------------------------------------------|
| Account ID        | Input   | 11-character field for entering the account identifier   |
| Card Number       | Input   | 16-character field for entering the card number          |
| Type Code         | Input   | 2-character field for transaction type code              |
| Category Code     | Input   | 4-character field for category classification            |
| Source            | Input   | 10-character field for transaction source/channel        |
| Description       | Input   | 60-character field for transaction description           |
| Amount            | Input   | 12-character field for signed monetary amount            |
| Origination Date  | Input   | 10-character field for date in YYYY-MM-DD format         |
| Processing Date   | Input   | 10-character field for date in YYYY-MM-DD format         |
| Merchant ID       | Input   | 9-character field for merchant identifier                |
| Merchant Name     | Input   | 30-character field for merchant name                     |
| Merchant City     | Input   | 25-character field for merchant city                     |
| Merchant Zip      | Input   | 10-character field for merchant postal code              |
| Confirm           | Input   | 1-character field for Y/N confirmation                   |
| Transaction ID    | Display | Shown in header area (current transaction code: CT02)    |
| Program Name      | Display | Shown in header area (COTRN02C)                          |
| Date              | Display | Current system date in mm/dd/yy format                   |
| Time              | Display | Current system time in hh:mm:ss format                   |
| Title Line 1      | Display | "AWS Mainframe Modernization"                            |
| Title Line 2      | Display | "CardDemo"                                               |
| Message Area      | Display | Row 23 — displays error messages (red) or success (green)|

### Available Actions

| Action         | How to Invoke | Description                                                       |
|----------------|---------------|-------------------------------------------------------------------|
| Submit/Continue| Press ENTER   | Validates all fields and either saves the transaction or shows errors |
| Go Back        | Press F3      | Returns to the Main Menu (or the screen that navigated here)      |
| Clear Fields   | Press F4      | Clears all input fields and resets the screen for fresh entry     |
| Copy Last Tran | Press F5      | Populates detail fields with data from the most recent transaction in the system |

---

## Section 6: Alternative & Error Flows

### AF1: Account ID Not Found

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User enters an Account ID that does not exist in the system                      |
| 2    | User presses ENTER                                                               |
| 3    | System looks up the Account ID in the card-account cross-reference               |
| 4    | System does not find a matching record                                           |
| 5    | System displays error: "Account ID NOT found..."                                 |
| 6    | Cursor is positioned on the Account ID field                                     |
| 7    | User corrects the Account ID and retries                                         |

### AF2: Card Number Not Found

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User enters a Card Number that does not exist in the system                      |
| 2    | User presses ENTER                                                               |
| 3    | System looks up the Card Number in the card cross-reference                      |
| 4    | System does not find a matching record                                           |
| 5    | System displays error: "Card Number NOT found..."                                |
| 6    | Cursor is positioned on the Card Number field                                    |
| 7    | User corrects the Card Number and retries                                        |

### AF3: Neither Account ID nor Card Number Entered

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User leaves both Account ID and Card Number blank                                |
| 2    | User presses ENTER                                                               |
| 3    | System detects no identifying field was provided                                 |
| 4    | System displays error: "Account or Card Number must be entered..."               |
| 5    | Cursor is positioned on the Account ID field                                     |

### AF4: Non-Numeric Account ID or Card Number

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User enters non-numeric characters in Account ID or Card Number                  |
| 2    | User presses ENTER                                                               |
| 3    | System detects the field is not numeric                                          |
| 4    | System displays error: "Account ID must be Numeric..." or "Card Number must be Numeric..." |
| 5    | Cursor is positioned on the invalid field                                        |

### AF5: Required Data Field Left Empty

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User enters a valid Account ID or Card Number                                    |
| 2    | User leaves one or more required detail fields empty (e.g., Type Code)           |
| 3    | User presses ENTER                                                               |
| 4    | System validates fields sequentially and stops at the first empty required field |
| 5    | System displays the appropriate error (e.g., "Type CD can NOT be empty...")      |
| 6    | Cursor is positioned on the first empty required field                           |

### AF6: Invalid Amount Format

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User enters an amount that does not match the required format (+/-99999999.99)   |
| 2    | User presses ENTER                                                               |
| 3    | System detects format violation                                                  |
| 4    | System displays error: "Amount should be in format -99999999.99"                 |
| 5    | Cursor is positioned on the Amount field                                         |

### AF7: Invalid Date Format

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User enters a date that does not match YYYY-MM-DD format                         |
| 2    | User presses ENTER                                                               |
| 3    | System detects format violation in the date characters                           |
| 4    | System displays: "Orig Date should be in format YYYY-MM-DD" or "Proc Date should be in format YYYY-MM-DD" |
| 5    | Cursor is positioned on the invalid date field                                   |

### AF8: Invalid Calendar Date

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User enters a correctly formatted but invalid calendar date (e.g., 2024-02-30)   |
| 2    | User presses ENTER                                                               |
| 3    | System passes format validation but fails semantic date validation               |
| 4    | System displays: "Orig Date - Not a valid date..." or "Proc Date - Not a valid date..." |
| 5    | Cursor is positioned on the invalid date field                                   |

### AF9: User Declines Confirmation

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User fills in all fields correctly                                               |
| 2    | User types 'N' in the Confirm field (or leaves it blank) and presses ENTER       |
| 3    | System displays: "Confirm to add this transaction..."                            |
| 4    | Cursor is positioned on the Confirm field                                        |
| 5    | User can change to 'Y' and press ENTER to proceed, or press F3 to cancel        |

### AF10: Invalid Confirmation Value

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User enters a character other than Y or N in the Confirm field                   |
| 2    | User presses ENTER                                                               |
| 3    | System displays: "Invalid value. Valid values are (Y/N)..."                      |
| 4    | Cursor is positioned on the Confirm field                                        |

### AF11: Duplicate Transaction ID (System Error)

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User confirms the transaction with 'Y'                                           |
| 2    | System generates a new Transaction ID                                            |
| 3    | A concurrent user has already written a record with the same ID (race condition) |
| 4    | System displays error: "Tran ID already exist..."                                |
| 5    | Cursor is positioned on the Account ID field                                     |

### AF12: System I/O Error on Cross-Reference Lookup

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User enters a valid Account ID or Card Number and presses ENTER                  |
| 2    | System attempts to read the cross-reference file                                 |
| 3    | An unexpected I/O error occurs                                                   |
| 4    | System displays: "Unable to lookup Acct in XREF AIX file..." or "Unable to lookup Card # in XREF file..." |
| 5    | Cursor is positioned on the identifier field                                     |

### AF13: System I/O Error on Transaction Write

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User confirms with 'Y' and all validations pass                                 |
| 2    | System attempts to write the new transaction record                              |
| 3    | An unexpected I/O error occurs                                                   |
| 4    | System displays: "Unable to Add Transaction..."                                  |
| 5    | Cursor is positioned on the Account ID field                                     |

### AF14: Invalid Function Key Pressed

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User presses a function key other than ENTER, F3, F4, or F5                      |
| 2    | System displays: "Invalid key pressed. Please see below..."                      |
| 3    | Screen is re-displayed with all previously entered data intact                   |

### AF15: Copy Last Transaction with No Valid Account/Card

| Step | Description                                                                      |
|------|----------------------------------------------------------------------------------|
| 1    | User presses F5 without entering a valid Account ID or Card Number               |
| 2    | System validates key fields and fails (see AF3, AF4, AF2)                        |
| 3    | System displays the relevant key field error                                     |
| 4    | Copy operation does not proceed                                                  |

---

## Section 7: Business Process Context

### Navigation Context

```
CardDemo Application
│
├── CC00 Sign-On Screen
│   └── CM00 Main Menu (Regular Users)
│       ├── Option 1: Account View
│       ├── Option 2: Account Update
│       ├── Option 3: Card List
│       ├── Option 4: Card Detail (from Card List)
│       ├── Option 5: Card Update (from Card List)
│       ├── Option 6: Transaction List
│       ├── Option 7: Transaction View (from Transaction List)
│       ├── ★ Option 8: Transaction Add (CT02 — THIS USE CASE) ★
│       ├── Option 9: Bill Payment
│       └── Option 10: Reports
│
└── CA00 Admin Menu (Admin Users)
    ├── User List
    ├── User Add
    ├── User Update
    └── User Delete
```

### Downstream Use Cases

| Destination     | Trigger                        | What Happens Next                                           |
|-----------------|--------------------------------|-------------------------------------------------------------|
| Main Menu (CM00)| User presses F3                | User returns to the Main Menu to select another function    |
| Sign-On (CC00)  | No COMMAREA present (edge case)| System redirects to sign-on if session state is lost        |

### Upstream Use Cases

| Source            | Navigation Path                                                    |
|-------------------|--------------------------------------------------------------------|
| Main Menu (CM00)  | User selects Option 8 ("Transaction Add") from the menu            |
| Transaction List  | User may arrive with a pre-selected card number (if TRN-SELECTED is set in shared session context) |

### End-to-End Journey Examples

**Journey 1: Manual Transaction Entry**
```
Sign-On ──> Main Menu ──> Transaction Add ──> (enter details) ──> Main Menu
```
_Business Scenario: A clerk receives a paper transaction slip and manually enters it into the system._

**Journey 2: Batch Transaction Entry with Copy**
```
Sign-On ──> Main Menu ──> Transaction Add ──> (enter 1st) ──> F5 (copy) ──> (modify) ──> repeat...
```
_Business Scenario: A clerk enters multiple similar transactions from the same merchant by copying the previous entry and modifying only the changed fields._

**Journey 3: Transaction Review and Additional Entry**
```
Sign-On ──> Main Menu ──> Transaction List ──> (review) ──> Main Menu ──> Transaction Add
```
_Business Scenario: A supervisor reviews the day's transactions, identifies a missing entry, and navigates to Transaction Add to record it._

**Journey 4: Card-Based Transaction Entry**
```
Sign-On ──> Main Menu ──> Card List ──> (find card) ──> Main Menu ──> Transaction Add (enter card #)
```
_Business Scenario: A clerk looks up a customer's card number in the Card List, then navigates to Transaction Add to record a transaction against that card._

---

## Section 8: Acceptance Criteria

### Core Functionality

| #     | Given                                        | When                                        | Then                                                                       |
|-------|----------------------------------------------|---------------------------------------------|----------------------------------------------------------------------------|
| AC-01 | User is on the Add Transaction screen        | User enters valid Account ID, all required fields, Confirm = Y, and presses ENTER | A new transaction record is saved and a success message is displayed with the new Transaction ID |
| AC-02 | User is on the Add Transaction screen        | User enters valid Card Number, all required fields, Confirm = Y, and presses ENTER | A new transaction record is saved and a success message is displayed with the new Transaction ID |
| AC-03 | A transaction has been successfully added     | The system displays the success message     | All input fields are cleared and ready for the next entry                  |
| AC-04 | A transaction has been successfully added     | The displayed Transaction ID is examined    | The Transaction ID is exactly one greater than the previous highest ID     |
| AC-05 | User enters a valid Account ID               | The system validates the account            | The corresponding Card Number is auto-populated on the screen              |
| AC-06 | User enters a valid Card Number              | The system validates the card               | The corresponding Account ID is auto-populated on the screen               |

### Input Validation

| #     | Given                                        | When                                        | Then                                                                       |
|-------|----------------------------------------------|---------------------------------------------|----------------------------------------------------------------------------|
| AC-07 | User leaves both Account ID and Card Number blank | User presses ENTER                     | Error "Account or Card Number must be entered..." is displayed             |
| AC-08 | User enters non-numeric Account ID           | User presses ENTER                          | Error "Account ID must be Numeric..." is displayed                         |
| AC-09 | User enters non-numeric Card Number          | User presses ENTER                          | Error "Card Number must be Numeric..." is displayed                        |
| AC-10 | User enters an Account ID not in the system  | User presses ENTER                          | Error "Account ID NOT found..." is displayed                               |
| AC-11 | User enters a Card Number not in the system  | User presses ENTER                          | Error "Card Number NOT found..." is displayed                              |
| AC-12 | User leaves Type Code empty                  | User presses ENTER                          | Error "Type CD can NOT be empty..." is displayed                           |
| AC-13 | User enters non-numeric Type Code            | User presses ENTER                          | Error "Type CD must be Numeric..." is displayed                            |
| AC-14 | User leaves Category Code empty              | User presses ENTER                          | Error "Category CD can NOT be empty..." is displayed                       |
| AC-15 | User enters non-numeric Category Code        | User presses ENTER                          | Error "Category CD must be Numeric..." is displayed                        |
| AC-16 | User leaves Source empty                     | User presses ENTER                          | Error "Source can NOT be empty..." is displayed                            |
| AC-17 | User leaves Description empty                | User presses ENTER                          | Error "Description can NOT be empty..." is displayed                       |
| AC-18 | User leaves Amount empty                     | User presses ENTER                          | Error "Amount can NOT be empty..." is displayed                            |
| AC-19 | User enters Amount in incorrect format       | User presses ENTER                          | Error "Amount should be in format -99999999.99" is displayed               |
| AC-20 | User leaves Origination Date empty           | User presses ENTER                          | Error "Orig Date can NOT be empty..." is displayed                         |
| AC-21 | User enters Orig Date in wrong format        | User presses ENTER                          | Error "Orig Date should be in format YYYY-MM-DD" is displayed              |
| AC-22 | User enters Orig Date with invalid date      | User presses ENTER                          | Error "Orig Date - Not a valid date..." is displayed                       |
| AC-23 | User leaves Processing Date empty            | User presses ENTER                          | Error "Proc Date can NOT be empty..." is displayed                         |
| AC-24 | User enters Proc Date in wrong format        | User presses ENTER                          | Error "Proc Date should be in format YYYY-MM-DD" is displayed              |
| AC-25 | User enters Proc Date with invalid date      | User presses ENTER                          | Error "Proc Date - Not a valid date..." is displayed                       |
| AC-26 | User leaves Merchant ID empty                | User presses ENTER                          | Error "Merchant ID can NOT be empty..." is displayed                       |
| AC-27 | User enters non-numeric Merchant ID          | User presses ENTER                          | Error "Merchant ID must be Numeric..." is displayed                        |
| AC-28 | User leaves Merchant Name empty              | User presses ENTER                          | Error "Merchant Name can NOT be empty..." is displayed                     |
| AC-29 | User leaves Merchant City empty              | User presses ENTER                          | Error "Merchant City can NOT be empty..." is displayed                     |
| AC-30 | User leaves Merchant Zip empty               | User presses ENTER                          | Error "Merchant Zip can NOT be empty..." is displayed                      |
| AC-31 | User enters invalid Confirm value (not Y/N)  | User presses ENTER                          | Error "Invalid value. Valid values are (Y/N)..." is displayed              |

### Navigation

| #     | Given                                        | When                                        | Then                                                                       |
|-------|----------------------------------------------|---------------------------------------------|----------------------------------------------------------------------------|
| AC-32 | User is on the Add Transaction screen        | User presses F3                             | User is returned to the Main Menu (or the program that navigated here)     |
| AC-33 | User is on the Add Transaction screen        | User presses F4                             | All input fields are cleared; screen is blank and ready for new entry       |
| AC-34 | User is on the Add Transaction screen with valid account/card | User presses F5 | Detail fields are populated with the last transaction's data               |
| AC-35 | User is on the Add Transaction screen        | User presses any unsupported key (e.g., F2) | Error "Invalid key pressed. Please see below..." is displayed              |

### Data Integrity

| #     | Given                                        | When                                        | Then                                                                       |
|-------|----------------------------------------------|---------------------------------------------|----------------------------------------------------------------------------|
| AC-36 | A transaction is saved successfully          | The transaction record is read back         | All field values match what was entered on the screen                       |
| AC-37 | The Transaction file contains N records      | A new transaction is added                  | The file now contains N+1 records with the new ID = previous max + 1       |
| AC-38 | Two users attempt to add simultaneously      | A duplicate ID is generated (race condition)| Error "Tran ID already exist..." is displayed; no data corruption occurs   |

---

## Section 9: Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                   | Technical Reference                                                              |
|-------------------------------------------------|----------------------------------------------------------------------------------|
| User selects Transaction Add from Main Menu     | COMEN01C performs XCTL to COTRN02C with COMMAREA (CDEMO-PGM-CONTEXT = 0)         |
| Display blank Add Transaction screen            | MAIN-PARA: `MOVE LOW-VALUES TO COTRN2AO`, PERFORM SEND-TRNADD-SCREEN            |
| Pre-populate card from selection context         | MAIN-PARA: `MOVE CDEMO-CT02-TRN-SELECTED TO CARDNINI`, PERFORM PROCESS-ENTER-KEY |
| Validate Account ID is numeric                  | VALIDATE-INPUT-KEY-FIELDS: `IF ACTIDINI OF COTRN2AI IS NOT NUMERIC`              |
| Look up card from Account ID                    | READ-CXACAIX-FILE: `EXEC CICS READ DATASET(WS-CXACAIX-FILE) RIDFLD(XREF-ACCT-ID)` |
| Validate Card Number is numeric                 | VALIDATE-INPUT-KEY-FIELDS: `IF CARDNINI OF COTRN2AI IS NOT NUMERIC`              |
| Look up account from Card Number                | READ-CCXREF-FILE: `EXEC CICS READ DATASET(WS-CCXREF-FILE) RIDFLD(XREF-CARD-NUM)` |
| Validate required fields not empty              | VALIDATE-INPUT-DATA-FIELDS: sequential `EVALUATE TRUE` on each field = SPACES/LOW-VALUES |
| Validate Type Code numeric                      | VALIDATE-INPUT-DATA-FIELDS: `WHEN TTYPCDI OF COTRN2AI NOT NUMERIC`               |
| Validate Category Code numeric                  | VALIDATE-INPUT-DATA-FIELDS: `WHEN TCATCDI OF COTRN2AI NOT NUMERIC`               |
| Validate Amount format                          | VALIDATE-INPUT-DATA-FIELDS: `EVALUATE TRUE` checking positions 1, 2:8, 10, 11:2  |
| Validate Origination Date format                | VALIDATE-INPUT-DATA-FIELDS: `EVALUATE TRUE` checking TORIGDTI positions 1:4, 5:1, 6:2, 8:1, 9:2 |
| Validate Origination Date is valid calendar date| `CALL 'CSUTLDTC' USING CSUTLDTC-DATE...` check CSUTLDTC-RESULT-SEV-CD ≠ '0000'  |
| Validate Processing Date format                 | VALIDATE-INPUT-DATA-FIELDS: `EVALUATE TRUE` checking TPROCDTI positions          |
| Validate Processing Date is valid calendar date | `CALL 'CSUTLDTC' USING CSUTLDTC-DATE...` (second call)                          |
| Validate Merchant ID numeric                    | VALIDATE-INPUT-DATA-FIELDS: `IF MIDI OF COTRN2AI IS NOT NUMERIC`                 |
| Check user confirmation                         | PROCESS-ENTER-KEY: `EVALUATE CONFIRMI OF COTRN2AI`                               |
| Generate new Transaction ID                     | ADD-TRANSACTION: STARTBR-TRANSACT-FILE (HIGH-VALUES), READPREV-TRANSACT-FILE, `ADD 1 TO WS-TRAN-ID-N` |
| Build and save transaction record               | ADD-TRANSACTION: `INITIALIZE TRAN-RECORD`, field MOVEs, WRITE-TRANSACT-FILE      |
| Write record to file                            | WRITE-TRANSACT-FILE: `EXEC CICS WRITE DATASET(WS-TRANSACT-FILE) FROM(TRAN-RECORD) RIDFLD(TRAN-ID)` |
| Display success message                         | WRITE-TRANSACT-FILE: `STRING 'Transaction added successfully. '...INTO WS-MESSAGE` |
| Clear all fields after success                  | INITIALIZE-ALL-FIELDS: MOVEs SPACES to all input map fields                      |
| Copy last transaction data                      | COPY-LAST-TRAN-DATA: STARTBR/READPREV to get last record, MOVE fields to screen  |
| Clear screen (F4)                               | CLEAR-CURRENT-SCREEN: PERFORM INITIALIZE-ALL-FIELDS, PERFORM SEND-TRNADD-SCREEN  |
| Return to previous screen (F3)                  | RETURN-TO-PREV-SCREEN: `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(...)`  |
| Handle invalid key                              | MAIN-PARA: `WHEN OTHER` → MOVE CCDA-MSG-INVALID-KEY TO WS-MESSAGE                |
| Send screen to terminal                         | SEND-TRNADD-SCREEN: `EXEC CICS SEND MAP('COTRN2A') MAPSET('COTRN02')...ERASE CURSOR` |
| Receive screen input from terminal              | RECEIVE-TRNADD-SCREEN: `EXEC CICS RECEIVE MAP('COTRN2A') MAPSET('COTRN02')...`   |
| Maintain pseudo-conversational state            | MAIN-PARA: `EXEC CICS RETURN TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA)`     |

### Source File Mapping

| Business Concept                     | Source File(s)                                                          |
|--------------------------------------|-------------------------------------------------------------------------|
| Transaction Add program logic        | `app/cbl/COTRN02C.cbl` (784 lines)                                     |
| Screen definition and field layout   | `app/bms/COTRN02.bms`                                                  |
| Date validation utility              | `app/cbl/CSUTLDTC.cbl`                                                 |
| Session/navigation state structure   | `app/cpy/COCOM01Y.cpy`                                                 |
| Transaction record layout            | `app/cpy/CVTRA05Y.cpy`                                                 |
| Card-Account cross-reference layout  | `app/cpy/CVACT03Y.cpy`                                                 |
| Account record layout (referenced)   | `app/cpy/CVACT01Y.cpy`                                                 |
| Screen title values                  | `app/cpy/COTTL01Y.cpy`                                                  |
| Date/time formatting structure       | `app/cpy/CSDAT01Y.cpy`                                                  |
| Common message text                  | `app/cpy/CSMSG01Y.cpy`                                                  |
| Main Menu (upstream)                 | `app/cbl/COMEN01C.cbl`                                                  |
| Menu options table                   | `app/cpy/COMEN02Y.cpy`                                                  |

### Migration Considerations

| #  | Consideration                                                                                              |
|----|------------------------------------------------------------------------------------------------------------|
| M1 | **Sequential ID generation → Database sequence or UUID**: The STARTBR + READPREV + increment pattern for generating transaction IDs has a race condition under concurrent access. A modernized system should use an auto-increment primary key or UUID. |
| M2 | **VSAM WRITE → Database INSERT with transaction control**: The current single-record WRITE has no rollback capability. The modernized version should wrap the INSERT in a database transaction with proper commit/rollback. |
| M3 | **Character-position amount validation → Decimal/numeric type**: The manual character-by-character format validation should be replaced with locale-aware numeric parsing and a decimal data type. |
| M4 | **CALL CSUTLDTC/CEEDAYS → Standard date library**: The custom date validation chain should be replaced with standard date parsing (e.g., `LocalDate.parse()` in Java or `datetime.strptime()` in Python). |
| M5 | **BMS screen map → Web form with client-side validation**: The 3270 terminal screen should become an HTML form with real-time client-side validation in addition to server-side validation. |
| M6 | **COMMAREA state passing → HTTP session or JWT**: The pseudo-conversational state management via COMMAREA should be replaced with standard web session management. |
| M7 | **XCTL navigation → URL routing**: Program-to-program transfer via XCTL should become standard URL-based navigation or API routing. |
| M8 | **No audit trail → Add audit columns**: The current system does not record who created the transaction or when. The modernized version should add created_by, created_at, and terminal/session identifiers. |
| M9 | **No referential integrity check → Foreign key constraints**: Type codes and category codes are validated only as numeric. A modernized system should validate these against reference/lookup tables with foreign key constraints. |
| M10 | **VSAM alternate index → Database JOIN or FK lookup**: The CXACAIX alternate index lookup pattern should become a standard database JOIN or indexed query on the cross-reference table. |
| M11 | **PF5 Copy Last Transaction → "Duplicate" or template feature**: This productivity feature should be preserved in the modernized UI, potentially enhanced with user-specific history rather than global last record. |
| M12 | **Single-field-at-a-time error reporting → Batch validation**: The current sequential validation (stopping at first error) should be replaced with batch validation that reports all errors simultaneously. |

---

## Section 10: Related Use Cases / End-to-End Composition

| Journey Name                         | Use Cases Involved                                          | Business Scenario                                                              |
|--------------------------------------|-------------------------------------------------------------|--------------------------------------------------------------------------------|
| Manual Transaction Data Entry        | UC-CC00-001 (Sign-On) → UC-CM00-001 (Main Menu) → UC-CT02-001 (Transaction Add) | Clerk signs in and enters one or more transactions from paper slips |
| Transaction Verification & Entry     | UC-CC00-001 → UC-CM00-001 → UC-CT01-001 (Transaction List) → UC-CM00-001 → UC-CT02-001 | Supervisor reviews existing transactions, identifies a gap, and adds the missing entry |
| Bulk Similar Transactions            | UC-CC00-001 → UC-CM00-001 → UC-CT02-001 (repeated with F5 Copy) | Clerk enters a batch of transactions from the same merchant using Copy Last Tran for efficiency |
| Card Lookup then Transaction Entry   | UC-CC00-001 → UC-CM00-001 → UC-CCLI-001 (Card List) → UC-CM00-001 → UC-CT02-001 | Clerk looks up a customer's card, notes the number, then enters a transaction against it |
| Full Account Lifecycle               | UC-CC00-001 → UC-CM00-001 → UC-CAVW-001 (Account View) → UC-CM00-001 → UC-CT02-001 → UC-CM00-001 → UC-CB00-001 (Bill Payment) | Agent views account status, adds a transaction, then processes a bill payment |

> **Note**: Individual use case flows must be composed into journey-level test documents for complete end-to-end validation. Some referenced use cases (UC-CT01-001, UC-CB00-001) may not yet have their own Business Use Case Flow documents.

---

## Document Footer

| Attribute                  | Value                                           |
|----------------------------|-------------------------------------------------|
| **Source Program**         | COTRN02C.cbl (784 lines)                        |
| **Transaction ID**         | CT02                                            |
| **Data Source**            | Technical Flow Analysis + COBOL source verification |
| **Acceptance Criteria**    | 38                                              |
| **Business Rules**         | 10                                              |
| **Validation Rules**       | 25                                              |
| **Alternative/Error Flows**| 15                                              |

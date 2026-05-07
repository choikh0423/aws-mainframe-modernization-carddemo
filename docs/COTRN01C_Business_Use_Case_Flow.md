# CT01 — Transaction View/Detail: Business Use Case Flow

## Section 1: Use Case Overview

| Attribute          | Value                                                                 |
|--------------------|-----------------------------------------------------------------------|
| **Use Case Name**  | View Transaction Detail                                               |
| **Use Case ID**    | UC-CT01-001                                                           |
| **Actor(s)**       | Cardholder Services Representative (regular user)                     |
| **Business Goal**  | View complete details of a specific credit card transaction           |
| **Preconditions**  | User is authenticated and has navigated to Transaction View (directly from Main Menu or via Transaction List selection) |
| **Postconditions** | Transaction details are displayed; no data is modified                |
| **Trigger**        | User selects a transaction from the Transaction List, or navigates directly to Transaction View and enters a Transaction ID |
| **Data Access**    | Read-only (transaction records)                                       |
| **Frequency**      | High — used routinely by customer service staff to review transaction history |

### Business Context

The Transaction View screen is the detail pane in the CardDemo credit card management system's transaction inquiry workflow. Customer service representatives use this screen to look up and display the full details of a single credit card transaction — including amount, merchant information, timestamps, and categorization — when responding to cardholder inquiries, investigating disputes, or verifying posted charges.

This use case supports two entry patterns: (1) drill-down from the Transaction List where a transaction was pre-selected, and (2) direct lookup by entering a known Transaction ID. In both cases, the screen displays the same comprehensive set of transaction attributes in a read-only format.

```
┌─────────────────────────────────────────────────────────────────┐
│                    CardDemo Functional Domains                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────┐   │
│  │   Account    │   │     Card     │   │   Transaction    │   │
│  │  Management  │   │  Management  │   │    Management    │   │
│  │              │   │              │   │                  │   │
│  │ - View       │   │ - List       │   │ - List (CT00)    │   │
│  │ - Update     │   │ - Detail     │   │ -*VIEW (CT01)*   │   │
│  │              │   │ - Update     │   │ - Add  (CT02)    │   │
│  └──────────────┘   └──────────────┘   └──────────────────┘   │
│                                                                 │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────┐   │
│  │    Billing   │   │  Reporting   │   │  Administration  │   │
│  │              │   │              │   │                  │   │
│  │ - Payment    │   │ - Generate   │   │ - User Mgmt      │   │
│  │              │   │              │   │ - Tran Types      │   │
│  └──────────────┘   └──────────────┘   └──────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Section 2: User Journey (Happy Path)

### Sequence Diagram

```
┌──────┐                                    ┌────────┐
│ User │                                    │ System │
└──┬───┘                                    └───┬────┘
   │                                            │
   │  Select transaction from Transaction List  │
   │  (or navigate directly to View screen)     │
   │ ──────────────────────────────────────────>│
   │                                            │
   │         Display Transaction View screen    │
   │         (pre-filled if selected from list) │
   │ <──────────────────────────────────────────│
   │                                            │
   │  [If direct entry] Type Transaction ID     │
   │  and press Enter                           │
   │ ──────────────────────────────────────────>│
   │                                            │
   │         Retrieve transaction record        │
   │         Display all transaction details    │
   │ <──────────────────────────────────────────│
   │                                            │
   │  Review transaction information            │
   │                                            │
   │  Press F3 to return to previous screen     │
   │ ──────────────────────────────────────────>│
   │                                            │
   │         Navigate back to calling screen    │
   │ <──────────────────────────────────────────│
   │                                            │
```

### Step-by-Step Narrative

| Step | Actor  | Action                                          | System Response                                                              |
|------|--------|-------------------------------------------------|------------------------------------------------------------------------------|
| 1    | User   | Selects a transaction on the Transaction List screen (or chooses Transaction View from Main Menu) | System transfers control to the Transaction View screen |
| 2    | System | —                                               | If a transaction was pre-selected from the list, the system automatically retrieves and displays its details. If accessed directly from Main Menu, a blank form is shown with the cursor positioned in the Transaction ID input field |
| 3    | User   | (If blank form) Enters the 16-character Transaction ID and presses Enter | — |
| 4    | System | —                                               | System retrieves the transaction record and displays all detail fields: Transaction ID, Card Number, Type Code, Category Code, Source, Description, Amount (formatted with sign and decimal), Origination Date, Processing Date, Merchant ID, Merchant Name, Merchant City, Merchant Zip |
| 5    | User   | Reviews the displayed transaction information   | — |
| 6    | User   | Presses F3 to return to the previous screen     | System navigates back to the calling screen (Transaction List or Main Menu) |

---

## Section 3: Business Rules & Validations

### Input Validation Rules

| #  | Field          | Rule                                           | Error Message Shown to User              |
|----|----------------|------------------------------------------------|------------------------------------------|
| V1 | Transaction ID | Must not be blank (spaces or empty)            | "Tran ID can NOT be empty..."            |
| V2 | Transaction ID | Must match an existing transaction record      | "Transaction ID NOT found..."            |

### Business Rules

| #   | Rule                                                                                                   |
|-----|--------------------------------------------------------------------------------------------------------|
| BR1 | **Read-Only Display** — The transaction record is displayed for viewing only; no fields are editable by the user |
| BR2 | **Exact-Match Lookup** — Transaction retrieval is performed by exact 16-character Transaction ID; no partial or wildcard matching is supported |
| BR3 | **Auto-Fetch on Selection** — When the user arrives from the Transaction List with a pre-selected transaction, the system automatically retrieves and displays it without requiring the user to press Enter |
| BR4 | **Amount Formatting** — The transaction amount is displayed with a leading sign (+/-) and two decimal places (e.g., "+00001234.56") |
| BR5 | **Navigation Memory** — When the user presses F3 (Back), the system returns to the screen that originally invoked the Transaction View (either Transaction List or Main Menu), preserving navigation context |
| BR6 | **Session Requirement** — The screen requires an active authenticated session; direct access without authentication redirects to the Sign-on screen |
| BR7 | **Clear Resets All** — The Clear function (F4) resets all displayed fields and the input field to blank, returning the screen to its initial empty state |

### Decision Table

```
┌─────────────────────────────────┬─────────────────────────────────────────────────┐
│ User Action                     │ System Response                                 │
├─────────────────────────────────┼─────────────────────────────────────────────────┤
│ Enter (with valid Tran ID)      │ Retrieve and display transaction details        │
│ Enter (with blank Tran ID)      │ Show error: "Tran ID can NOT be empty..."       │
│ Enter (with non-existent ID)    │ Show error: "Transaction ID NOT found..."       │
│ F3 (Back)                       │ Return to calling screen (List or Main Menu)    │
│ F4 (Clear)                      │ Clear all fields, reset screen to initial state │
│ F5 (Browse Transactions)        │ Navigate to Transaction List screen             │
│ Any other key                   │ Show error: "Invalid key pressed. Please see    │
│                                 │ below..."                                       │
└─────────────────────────────────┴─────────────────────────────────────────────────┘
```

---

## Section 4: Data Entities Involved

### Entity Descriptions

| Entity              | Description                                                   | Role in This Use Case                        |
|---------------------|---------------------------------------------------------------|----------------------------------------------|
| Transaction Record  | A single credit card transaction capturing a purchase, payment, or other financial activity | Primary entity — retrieved by ID and displayed in full detail |
| Card                | A credit card associated with a customer account              | Referenced via Card Number field in the transaction record (displayed, not separately retrieved) |
| Merchant            | A business establishment where a transaction occurred         | Merchant details (ID, Name, City, Zip) are embedded in the transaction record and displayed |

### Entity Relationships

```
┌──────────────────┐         ┌──────────────────┐
│   Transaction    │         │       Card       │
│                  │    N:1  │                  │
│  Transaction ID  │────────>│   Card Number    │
│  Amount          │         │                  │
│  Description     │         └──────────────────┘
│  Type Code       │                  │
│  Category Code   │                  │ N:1
│  Source          │                  v
│  Orig Date       │         ┌──────────────────┐
│  Proc Date       │         │     Account      │
│  Card Number ────┼────┐    │                  │
│  Merchant Info   │    │    │   Account ID     │
└──────────────────┘    │    └──────────────────┘
                        │
                        │    ┌──────────────────┐
                        └───>│    Merchant      │
                             │  (embedded)      │
                             │  Merchant ID     │
                             │  Merchant Name   │
                             │  Merchant City   │
                             │  Merchant Zip    │
                             └──────────────────┘
```

### Data Fields Displayed

| #  | Field            | Format                 | Source                       | Description                                         |
|----|------------------|------------------------|------------------------------|-----------------------------------------------------|
| 1  | Transaction ID   | 16 alphanumeric chars  | Transaction Record (key)     | Unique identifier for the transaction               |
| 2  | Card Number      | 16 characters          | Transaction Record           | Credit card number used in the transaction          |
| 3  | Type Code        | 2 characters           | Transaction Record           | Code indicating transaction type (e.g., purchase, refund) |
| 4  | Category Code    | 4 numeric digits       | Transaction Record           | Numeric code classifying the transaction category   |
| 5  | Source           | 10 characters          | Transaction Record           | Origin channel of the transaction                   |
| 6  | Description      | Up to 100 characters (60 displayed) | Transaction Record | Free-text description of the transaction            |
| 7  | Amount           | Signed decimal (+99999999.99) | Transaction Record (formatted) | Monetary value with sign and two decimal places |
| 8  | Origination Date | 10 characters          | Transaction Record (timestamp)| Date/time when the transaction was initiated        |
| 9  | Processing Date  | 10 characters          | Transaction Record (timestamp)| Date/time when the transaction was processed        |
| 10 | Merchant ID      | 9 numeric digits       | Transaction Record           | Unique identifier for the merchant                  |
| 11 | Merchant Name    | Up to 50 characters (30 displayed) | Transaction Record | Name of the merchant business                       |
| 12 | Merchant City    | Up to 50 characters (25 displayed) | Transaction Record | City where the merchant is located                  |
| 13 | Merchant Zip     | 10 characters          | Transaction Record           | Postal/zip code of the merchant                     |

> **Note:** The underlying transaction record also contains a 20-byte reserved/filler area that is NOT displayed on the screen. Additionally, the full Description (100 chars), Merchant Name (50 chars), and Merchant City (50 chars) are truncated to 60, 30, and 25 characters respectively on the display due to screen width constraints.

---

## Section 5: Screen / Interface Description

### Screen Layout

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│Tran: CT01       AWS Mainframe Modernization            Date: 05/07/26           │
│Prog: COTRN01C              CardDemo                    Time: 14:30:22           │
│                                                                                  │
│                              View Transaction                                    │
│                                                                                  │
│     Enter Tran ID: 0000000000098765                                              │
│                                                                                  │
│     ----------------------------------------------------------------------       │
│                                                                                  │
│     Transaction ID: 0000000000098765    Card Number: 4111222233334444            │
│                                                                                  │
│     Type CD: SA   Category CD: 5411   Source: ONLINE                             │
│                                                                                  │
│     Description: GROCERY PURCHASE - WHOLE FOODS MARKET #10234                    │
│                                                                                  │
│     Amount: +00000125.43  Orig Date: 2023-09-15  Proc Date: 2023-09-16          │
│                                                                                  │
│     Merchant ID: 000456789  Merchant Name: WHOLE FOODS MARKET INC               │
│                                                                                  │
│     Merchant City: SEATTLE                   Merchant Zip: 98101                 │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│Error/informational message area                                                  │
│ENTER=Fetch  F3=Back  F4=Clear  F5=Browse Tran.                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Interface Elements

| Element          | Type    | Description                                                     |
|------------------|---------|-----------------------------------------------------------------|
| Transaction ID Input (Enter Tran ID) | Input | 16-character field where the user types the transaction ID to look up; cursor is positioned here by default |
| Transaction ID   | Display | Shows the retrieved transaction's unique identifier             |
| Card Number      | Display | Shows the credit card number associated with the transaction    |
| Type Code        | Display | Shows the 2-character transaction type classification           |
| Category Code    | Display | Shows the 4-digit numeric category classification              |
| Source           | Display | Shows the 10-character transaction origin channel              |
| Description      | Display | Shows the first 60 characters of the transaction description   |
| Amount           | Display | Shows the formatted monetary amount with sign and decimals     |
| Origination Date | Display | Shows the date/time the transaction was originally submitted   |
| Processing Date  | Display | Shows the date/time the transaction was processed              |
| Merchant ID      | Display | Shows the 9-digit merchant identifier                          |
| Merchant Name    | Display | Shows the first 30 characters of the merchant business name    |
| Merchant City    | Display | Shows the first 25 characters of the merchant city             |
| Merchant Zip     | Display | Shows the 10-character merchant postal code                    |
| Message Area     | Display | System messages (errors, confirmations) shown in red highlight |
| Function Key Bar | Display | Available actions listed at screen bottom                      |

### Available Actions

| Action              | How to Invoke | Description                                                    |
|---------------------|---------------|----------------------------------------------------------------|
| Fetch Transaction   | Press Enter   | Retrieve and display the transaction identified by the entered Transaction ID |
| Go Back             | Press F3      | Return to the screen that invoked this view (Transaction List or Main Menu) |
| Clear Screen        | Press F4      | Reset all display fields and the input field to blank          |
| Browse Transactions | Press F5      | Navigate to the Transaction List screen to browse/search transactions |

---

## Section 6: Alternative & Error Flows

### Alternative Flow 1: Transaction Not Found

| Step | Description                                                                              |
|------|------------------------------------------------------------------------------------------|
| 1    | User enters a Transaction ID that does not exist in the system                           |
| 2    | System attempts to retrieve the transaction record                                       |
| 3    | System determines no matching record exists                                              |
| 4    | System displays error message: "Transaction ID NOT found..."                             |
| 5    | Cursor is repositioned to the Transaction ID input field                                 |
| 6    | User may correct the ID and try again, or navigate away using F3/F5                      |

### Alternative Flow 2: Empty Transaction ID Submitted

| Step | Description                                                                              |
|------|------------------------------------------------------------------------------------------|
| 1    | User presses Enter without entering a Transaction ID (field is blank)                    |
| 2    | System validates the input field                                                         |
| 3    | System displays error message: "Tran ID can NOT be empty..."                             |
| 4    | Cursor is repositioned to the Transaction ID input field                                 |
| 5    | User must enter a valid Transaction ID before the system will attempt retrieval          |

### Alternative Flow 3: Invalid Key Pressed

| Step | Description                                                                              |
|------|------------------------------------------------------------------------------------------|
| 1    | User presses a function key other than Enter, F3, F4, or F5                              |
| 2    | System detects the unsupported key                                                       |
| 3    | System displays error message: "Invalid key pressed. Please see below..."                |
| 4    | Screen is re-displayed with the error message; previously displayed data remains visible |
| 5    | User may press a valid key to continue                                                   |

### Alternative Flow 4: System I/O Error During Retrieval

| Step | Description                                                                              |
|------|------------------------------------------------------------------------------------------|
| 1    | User enters a valid Transaction ID and presses Enter                                     |
| 2    | System attempts to retrieve the transaction record                                       |
| 3    | An unexpected system error occurs during data retrieval                                  |
| 4    | System displays error message: "Unable to lookup Transaction..."                         |
| 5    | System logs the error details (response and reason codes) for technical support          |
| 6    | Cursor is repositioned to the Transaction ID input field                                 |
| 7    | User may retry or contact support if the error persists                                  |

### Alternative Flow 5: Direct Access Without Authentication

| Step | Description                                                                              |
|------|------------------------------------------------------------------------------------------|
| 1    | The Transaction View screen is invoked without a valid session (no communication area)   |
| 2    | System detects the absence of session context                                            |
| 3    | System redirects the user to the Sign-on screen                                         |
| 4    | User must authenticate before accessing any application functionality                    |

### Alternative Flow 6: Clear and Re-Enter

| Step | Description                                                                              |
|------|------------------------------------------------------------------------------------------|
| 1    | User has a transaction displayed and wants to look up a different one                    |
| 2    | User presses F4 (Clear)                                                                  |
| 3    | System clears all displayed transaction detail fields and the input field                |
| 4    | Cursor is repositioned to the Transaction ID input field                                 |
| 5    | User enters a new Transaction ID and presses Enter                                       |
| 6    | System retrieves and displays the new transaction (returns to happy path step 4)         |

---

## Section 7: Business Process Context

### Navigation Context

```
CardDemo Application
├── CC00 — Sign-on
│   └── CM00 — Main Menu (regular users)
│       ├── Option 1 — Account View
│       ├── Option 2 — Account Update
│       ├── Option 3 — Card List
│       ├── Option 4 — Card Detail
│       ├── Option 5 — Card Update
│       ├── Option 6 — Transaction List (CT00)
│       │   └── Select 'S' on a row
│       │       └── *** CT01 — Transaction View/Detail ***  <── THIS USE CASE
│       ├── Option 7 — Transaction View (CT01) [direct]
│       │   └── *** CT01 — Transaction View/Detail ***  <── THIS USE CASE
│       ├── Option 8 — Transaction Add (CT02)
│       ├── Option 9 — Bill Payment
│       └── Option 10 — Reports
│
├── CA00 — Admin Menu (admin users)
│   └── (User management, Transaction Type management)
```

### Downstream Use Cases

| Destination                 | Trigger            | What Happens Next                                         |
|-----------------------------|--------------------|-----------------------------------------------------------|
| Transaction List (CT00)     | User presses F5    | User is taken to the transaction browse/list screen to search for other transactions |
| Main Menu (CM00)            | User presses F3 (when accessed directly from Main Menu) | User returns to the Main Menu to select another function |
| Transaction List (CT00)     | User presses F3 (when accessed from List) | User returns to the Transaction List at their previous position |

### Upstream Use Cases

| Source                      | Navigation Path                                            |
|-----------------------------|------------------------------------------------------------|
| Transaction List (CT00)     | User selects a transaction row with 'S' action code        |
| Main Menu (CM00)            | User selects Option 7 (Transaction View) from Main Menu    |

### End-to-End Journey Examples

**Journey 1: Customer Dispute Investigation**
```
Sign-on ──> Main Menu ──> Transaction List ──> Transaction View ──> Back to List
```
> A customer calls to dispute a charge. The representative signs in, navigates to the Transaction List, filters by card number, selects the disputed transaction to view full details (merchant, amount, date), then returns to the list to check adjacent transactions.

**Journey 2: Direct Transaction Lookup**
```
Sign-on ──> Main Menu ──> Transaction View (direct) ──> Back to Menu
```
> A representative receives a reference number from a supervisor and needs to quickly look up a specific transaction. They navigate directly to Transaction View from the Main Menu, enter the Transaction ID, review the details, and return.

**Journey 3: Transaction Review Before Posting**
```
Sign-on ──> Main Menu ──> Transaction List ──> Transaction View ──> Browse (F5) ──> Transaction List
```
> An operations analyst reviews recently added transactions. They browse the Transaction List, drill into individual transactions for detail verification, then use F5 to return to the list for the next review.

**Journey 4: Multi-Transaction Comparison**
```
Sign-on ──> Main Menu ──> Transaction List ──> Transaction View ──> Clear (F4) ──> Enter new ID ──> View ──> Back
```
> A representative needs to compare two transactions. They view the first from the list, note the details, press F4 to clear the screen, manually enter the second Transaction ID, review it, then return to the list.

---

## Section 8: Acceptance Criteria

### Core Functionality

| #     | Given                                                    | When                                              | Then                                                                  |
|-------|----------------------------------------------------------|---------------------------------------------------|-----------------------------------------------------------------------|
| AC-01 | User is on the Transaction View screen                   | User enters a valid Transaction ID and presses Enter | All 13 transaction detail fields are displayed with correct values from the record |
| AC-02 | User selected a transaction on the Transaction List      | System transfers to Transaction View               | The selected transaction's details are automatically retrieved and displayed without requiring Enter |
| AC-03 | User navigates to Transaction View directly from Main Menu | Screen is displayed                              | A blank form is shown with cursor positioned in the Transaction ID input field |
| AC-04 | Transaction details are displayed                        | User reviews the Amount field                     | Amount is formatted with leading sign and two decimal places (e.g., "+00001234.56") |

### Input Validation

| #     | Given                                                    | When                                              | Then                                                                  |
|-------|----------------------------------------------------------|---------------------------------------------------|-----------------------------------------------------------------------|
| AC-05 | User is on the Transaction View screen                   | User presses Enter with a blank Transaction ID    | Error message "Tran ID can NOT be empty..." is displayed and cursor returns to input field |
| AC-06 | User is on the Transaction View screen                   | User enters a Transaction ID that does not exist  | Error message "Transaction ID NOT found..." is displayed and cursor returns to input field |
| AC-07 | User is on the Transaction View screen                   | A system error occurs during transaction retrieval | Error message "Unable to lookup Transaction..." is displayed and the error is logged |

### Navigation

| #     | Given                                                    | When                                              | Then                                                                  |
|-------|----------------------------------------------------------|---------------------------------------------------|-----------------------------------------------------------------------|
| AC-08 | User accessed Transaction View from Transaction List     | User presses F3                                   | User is returned to the Transaction List screen                       |
| AC-09 | User accessed Transaction View from Main Menu            | User presses F3                                   | User is returned to the Main Menu                                     |
| AC-10 | User is on the Transaction View screen                   | User presses F5                                   | User is navigated to the Transaction List (browse) screen             |
| AC-11 | User is on the Transaction View screen                   | User presses F4                                   | All display fields and the input field are cleared; cursor returns to input field |
| AC-12 | User is on the Transaction View screen                   | User presses any key other than Enter, F3, F4, F5 | Error message "Invalid key pressed. Please see below..." is displayed |

### Data Integrity

| #     | Given                                                    | When                                              | Then                                                                  |
|-------|----------------------------------------------------------|---------------------------------------------------|-----------------------------------------------------------------------|
| AC-13 | A transaction record exists in the system                | User views the transaction                        | All displayed fields exactly match the stored transaction record values (no modification on read) |
| AC-14 | User accesses Transaction View without a valid session   | Screen is invoked                                 | User is redirected to the Sign-on screen                              |

---

## Section 9: Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                    | Technical Reference                                                              |
|--------------------------------------------------|----------------------------------------------------------------------------------|
| Screen first displayed (blank or pre-filled)     | MAIN-PARA: `IF NOT CDEMO-PGM-REENTER` block; `MOVE LOW-VALUES TO COTRN1AO`; `PERFORM SEND-TRNVIEW-SCREEN` |
| Auto-fetch pre-selected transaction              | MAIN-PARA: `IF CDEMO-CT01-TRN-SELECTED NOT = SPACES AND LOW-VALUES` → `PERFORM PROCESS-ENTER-KEY` |
| User presses Enter to fetch transaction          | MAIN-PARA: `EVALUATE EIBAID` → `WHEN DFHENTER` → `PERFORM PROCESS-ENTER-KEY`   |
| Validate Transaction ID not empty                | PROCESS-ENTER-KEY: `WHEN TRNIDINI OF COTRN1AI = SPACES OR LOW-VALUES` → sets WS-ERR-FLG, moves error message |
| Retrieve transaction record                      | READ-TRANSACT-FILE: `EXEC CICS READ DATASET(WS-TRANSACT-FILE) INTO(TRAN-RECORD) RIDFLD(TRAN-ID) KEYLENGTH(LENGTH OF TRAN-ID) UPDATE RESP(WS-RESP-CD)` |
| Transaction not found error                      | READ-TRANSACT-FILE: `WHEN DFHRESP(NOTFND)` → moves "Transaction ID NOT found..." to WS-MESSAGE |
| System I/O error handling                        | READ-TRANSACT-FILE: `WHEN OTHER` → `DISPLAY 'RESP:' WS-RESP-CD 'REAS:' WS-REAS-CD`; moves "Unable to lookup Transaction..." |
| Display transaction details on screen            | PROCESS-ENTER-KEY: series of `MOVE TRAN-xxx TO xxxI OF COTRN1AI` statements (lines 177-190) |
| Format amount for display                        | PROCESS-ENTER-KEY: `MOVE TRAN-AMT TO WS-TRAN-AMT` (PIC +99999999.99 edit mask) |
| Navigate back (F3)                               | MAIN-PARA: `WHEN DFHPF3` → evaluates `CDEMO-FROM-PROGRAM`, sets `CDEMO-TO-PROGRAM`, `PERFORM RETURN-TO-PREV-SCREEN` |
| Return to previous screen                        | RETURN-TO-PREV-SCREEN: `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA)` |
| Clear screen (F4)                                | MAIN-PARA: `WHEN DFHPF4` → `PERFORM CLEAR-CURRENT-SCREEN` → `PERFORM INITIALIZE-ALL-FIELDS` |
| Browse transactions (F5)                         | MAIN-PARA: `WHEN DFHPF5` → `MOVE 'COTRN00C' TO CDEMO-TO-PROGRAM` → `PERFORM RETURN-TO-PREV-SCREEN` |
| Invalid key handling                             | MAIN-PARA: `WHEN OTHER` → `MOVE CCDA-MSG-INVALID-KEY TO WS-MESSAGE`            |
| Send screen to terminal                          | SEND-TRNVIEW-SCREEN: `EXEC CICS SEND MAP('COTRN1A') MAPSET('COTRN01') FROM(COTRN1AO) ERASE CURSOR` |
| Receive user input                               | RECEIVE-TRNVIEW-SCREEN: `EXEC CICS RECEIVE MAP('COTRN1A') MAPSET('COTRN01') INTO(COTRN1AI)` |
| Populate header (date, time, titles)             | POPULATE-HEADER-INFO: `FUNCTION CURRENT-DATE`, moves to CURDATEO/CURTIMEO/TITLE01O/TITLE02O |
| Redirect to sign-on (no session)                 | MAIN-PARA: `IF EIBCALEN = 0` → `MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM` → `PERFORM RETURN-TO-PREV-SCREEN` |
| Pseudo-conversational return                     | MAIN-PARA (end): `EXEC CICS RETURN TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA)` |

### Source File Mapping

| Business Concept                  | Source File(s)                                          |
|-----------------------------------|---------------------------------------------------------|
| Transaction View program logic    | `app/cbl/COTRN01C.cbl` (331 lines)                     |
| Screen layout and field definitions | `app/bms/COTRN01.bms` (mapset COTRN01, map COTRN1A)  |
| Transaction record structure      | `app/cpy/CVTRA05Y.cpy` (TRAN-RECORD, 350 bytes)        |
| Inter-program communication area  | `app/cpy/COCOM01Y.cpy` (CARDDEMO-COMMAREA)             |
| BMS symbolic map (auto-generated) | `app/cpy/COTRN01.cpy`                                  |
| Application screen titles         | `app/cpy/COTTL01Y.cpy` (CCDA-TITLE01, CCDA-TITLE02)    |
| Date/time formatting structure    | `app/cpy/CSDAT01Y.cpy` (WS-DATE-TIME)                  |
| Common error messages             | `app/cpy/CSMSG01Y.cpy` (CCDA-MSG-INVALID-KEY)          |
| Upstream: Transaction List        | `app/cbl/COTRN00C.cbl`                                 |
| Upstream: Main Menu               | `app/cbl/COMEN01C.cbl`                                 |
| Upstream: Sign-on                 | `app/cbl/COSGN00C.cbl`                                 |

### Migration Considerations

| #  | Consideration                                                                                           |
|----|---------------------------------------------------------------------------------------------------------|
| M1 | **Read Lock on View Screen** — The current implementation acquires an exclusive update lock on the transaction record during read (CICS READ with UPDATE). Since this is a read-only view, the modernized version should use a simple SELECT query without row locking to avoid unnecessary contention |
| M2 | **Pseudo-Conversational to Stateless** — The CICS pseudo-conversational pattern (RETURN TRANSID with COMMAREA) should be replaced by a stateless request/response model (e.g., REST API GET endpoint) or a session-state mechanism in the web tier |
| M3 | **COMMAREA Navigation to URL Routing** — Navigation context passed via COMMAREA fields (FROM-PROGRAM, TO-PROGRAM) should be replaced with URL routing, browser history, or breadcrumb navigation in a modern web UI |
| M4 | **BMS Map to Web Form** — The BMS 3270 terminal screen should be converted to a responsive web form or API response structure. Field positions and attributes translate to HTML form elements with CSS styling |
| M5 | **VSAM Keyed Read to SQL Primary Key Lookup** — The CICS READ by RIDFLD on a VSAM KSDS file translates directly to a SQL `SELECT * FROM transactions WHERE transaction_id = ?` query |
| M6 | **XCTL Navigation to Application Routing** — Program-to-program transfers via XCTL should be replaced with client-side routing (SPA) or server-side redirects, maintaining the back-navigation context through URL parameters or session state |
| M7 | **DISPLAY Error Logging to Structured Logging** — The `DISPLAY` statement for error logging writes to SYSOUT. The modernized version should use a structured logging framework (e.g., JSON logging to CloudWatch, ELK stack) with correlation IDs |
| M8 | **Field Truncation** — The BMS screen truncates Description (100→60), Merchant Name (50→30), and Merchant City (50→25) due to 80-column terminal limits. A modern UI can display full-length values or provide expandable fields |
| M9 | **Hardcoded Program Names** — Return targets ('COSGN00C', 'COMEN01C', 'COTRN00C') are hardcoded literals. A modern implementation should use a routing configuration or navigation registry for maintainability |
| M10 | **Amount Edit Mask** — The COBOL PIC +99999999.99 edit mask should be replaced with locale-aware currency formatting in the presentation layer |

---

## Section 10: Related Use Cases / End-to-End Composition

| Journey Name                         | Use Cases Involved                                    | Business Scenario                                                  |
|--------------------------------------|-------------------------------------------------------|--------------------------------------------------------------------|
| Transaction Dispute Investigation    | UC-CC00-001 (Sign-on) → UC-CM00-001 (Main Menu) → UC-CT00-001 (Transaction List) → **UC-CT01-001 (Transaction View)** | Customer calls to dispute a charge; representative looks up the transaction details to verify merchant, amount, and date |
| Direct Transaction Verification      | UC-CC00-001 (Sign-on) → UC-CM00-001 (Main Menu) → **UC-CT01-001 (Transaction View)** | Supervisor provides a Transaction ID for verification; representative looks up details directly without browsing |
| Transaction Browse and Review        | UC-CC00-001 (Sign-on) → UC-CM00-001 (Main Menu) → UC-CT00-001 (Transaction List) → **UC-CT01-001 (Transaction View)** → UC-CT00-001 (Transaction List) | Operations analyst reviews multiple transactions by drilling into details and returning to the list |
| End-of-Day Transaction Audit         | UC-CC00-001 (Sign-on) → UC-CM00-001 (Main Menu) → UC-CT00-001 (Transaction List) → **UC-CT01-001 (Transaction View)** [repeated] → UC-CR00-001 (Reports) | Auditor verifies individual transaction details before generating daily reports |
| Transaction Entry and Verification   | UC-CC00-001 (Sign-on) → UC-CM00-001 (Main Menu) → UC-CT02-001 (Transaction Add) → UC-CT00-001 (Transaction List) → **UC-CT01-001 (Transaction View)** | Staff enters a new manual transaction, then verifies it was recorded correctly by viewing its detail |

> **Note:** Individual use case flows referenced above (UC-CC00-001, UC-CM00-001, UC-CT00-001, UC-CT02-001, UC-CR00-001) must have their own Business Use Case Flow documents created for complete end-to-end validation. This use case can be tested independently using the acceptance criteria in Section 8, but full journey testing requires composing multiple use case flows.

---

## Document Footer

| Attribute                | Value                        |
|--------------------------|------------------------------|
| **Source Program**       | COTRN01C.cbl (331 lines)    |
| **Transaction ID**       | CT01                         |
| **Data Source**          | TRANSACT VSAM KSDS          |
| **Acceptance Criteria**  | 14                           |
| **Business Rules**       | 7                            |
| **Validation Rules**     | 2                            |
| **Alternative Flows**    | 6                            |
| **Migration Considerations** | 10                       |

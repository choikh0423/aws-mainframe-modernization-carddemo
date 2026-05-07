# COBIL00C — Bill Payment: Business Use Case Flow Analysis

## Section 1 — Use Case Overview

| Attribute            | Value                                                                                     |
|----------------------|-------------------------------------------------------------------------------------------|
| **Use Case Name**    | Bill Payment                                                                              |
| **Use Case ID**      | UC-CB00-001                                                                               |
| **Actor(s)**         | Authenticated Regular User (cardholder)                                                   |
| **Business Goal**    | Pay the full outstanding balance on a credit card account and record the payment as a transaction |
| **Preconditions**    | 1. User is signed in to the CardDemo application with a valid user ID and password         |
|                      | 2. User has navigated to the Main Menu                                                    |
|                      | 3. The target account exists and has a positive outstanding balance                       |
| **Postconditions**   | 1. A bill payment transaction record is created with a unique sequential ID                |
|                      | 2. The account balance is reduced to zero                                                 |
| **Trigger**          | User selects "Bill Payment" (option 10) from the Main Menu                                |
| **Data Access**      | Read-Write (reads account and card data; writes a new transaction; updates account balance)|
| **Frequency**        | On-demand — initiated by user when they wish to pay their account balance                 |

### Business Context

The Bill Payment use case is part of the CardDemo credit card management system's self-service capabilities. It allows authenticated cardholders to pay the entire outstanding balance on their account in a single action. The system enforces a full-balance-only policy — partial payments are not supported. Upon successful payment, the system generates a transaction record for audit and reporting purposes and updates the account balance to zero.

This use case participates in the broader account lifecycle: after transactions accumulate on an account (via purchases and other activities), the cardholder can use Bill Payment to settle the balance. The generated transaction record feeds into downstream batch reporting and statement generation processes.

```
┌─────────────────────────────────────────────────────────────────┐
│                    CardDemo Application                         │
│                                                                 │
│  ┌──────────────┐   ┌──────────────────────────────────────┐    │
│  │   Security   │   │       Account Management             │    │
│  │              │   │  ┌────────────┐  ┌────────────────┐  │    │
│  │  Sign-on     │   │  │ Account    │  │ Account Update │  │    │
│  │              │   │  │ View       │  │                │  │    │
│  └──────────────┘   │  └────────────┘  └────────────────┘  │    │
│                     └──────────────────────────────────────┘    │
│  ┌──────────────┐   ┌──────────────────────────────────────┐    │
│  │ Card Mgmt    │   │       Transaction Processing         │    │
│  │              │   │  ┌────────────┐  ┌────────────────┐  │    │
│  │ Card List    │   │  │ Tran List/ │  │ ┌────────────┐ │  │    │
│  │ Card Detail  │   │  │ View / Add │  │ │>>> BILL  <<<│ │  │    │
│  │ Card Update  │   │  │            │  │ │  PAYMENT   │ │  │    │
│  └──────────────┘   │  └────────────┘  │ └────────────┘ │  │    │
│                     │                  └────────────────┘  │    │
│  ┌──────────────┐   └──────────────────────────────────────┘    │
│  │  Reporting   │   ┌──────────────────────────────────────┐    │
│  │              │   │       Administration                  │    │
│  │  Reports     │   │  User List / Add / Update / Delete    │    │
│  └──────────────┘   └──────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Section 2 — User Journey (Happy Path)

### Sequence Diagram

```
     User                                          System
      │                                              │
      │  Select "Bill Payment" from Main Menu        │
      │─────────────────────────────────────────────>│
      │                                              │
      │  Display Bill Payment screen (blank)         │
      │<─────────────────────────────────────────────│
      │                                              │
      │  Enter Account ID, press Enter               │
      │─────────────────────────────────────────────>│
      │                                              │
      │  Look up account, display current balance    │
      │  Prompt: "Confirm to make a bill payment..." │
      │<─────────────────────────────────────────────│
      │                                              │
      │  Enter "Y" in Confirmation field,            │
      │  press Enter                                 │
      │─────────────────────────────────────────────>│
      │                                              │
      │  Process payment:                            │
      │    - Look up card number for account         │
      │    - Generate new transaction ID             │
      │    - Create payment transaction record       │
      │    - Set account balance to zero             │
      │  Display: "Payment successful. Your          │
      │    Transaction ID is {ID}."                  │
      │<─────────────────────────────────────────────│
      │                                              │
      │  Press F3 to return to Main Menu             │
      │─────────────────────────────────────────────>│
      │                                              │
      │  Navigate back to Main Menu                  │
      │<─────────────────────────────────────────────│
      │                                              │
```

### Step-by-Step Narrative

| Step | Actor  | Action                                              | System Response                                                                                          |
|------|--------|------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| 1    | User   | Selects "Bill Payment" (option 10) from Main Menu    | Navigates to the Bill Payment screen and displays a blank form with cursor in the Account ID field       |
| 2    | User   | Types an 11-digit Account ID and presses Enter       | Validates the Account ID is not empty; looks up the account; displays the current balance; prompts user to confirm payment with message "Confirm to make a bill payment..." |
| 3    | User   | Types "Y" in the Confirmation field and presses Enter| Looks up the associated card number; generates a new unique transaction ID; creates a bill payment transaction record for the full balance amount; updates the account balance to zero; displays success message "Payment successful. Your Transaction ID is {ID}." in green |
| 4    | User   | Presses F3 to return to the previous screen          | Navigates back to the Main Menu (or the originating screen)                                              |

---

## Section 3 — Business Rules & Validations

### Input Validation Rules

| #  | Field          | Rule                                                                 | Error Message Shown to User                          |
|----|----------------|----------------------------------------------------------------------|------------------------------------------------------|
| V1 | Account ID     | Must not be empty (blank or uninitialized)                           | `Acct ID can NOT be empty...`                        |
| V2 | Account ID     | Must correspond to an existing account in the system                 | `Account ID NOT found...`                            |
| V3 | Confirmation   | Must be one of: Y, y, N, n, or left blank                           | `Invalid value. Valid values are (Y/N)...`           |

### Business Rules

| #   | Rule                                                                                                       |
|-----|------------------------------------------------------------------------------------------------------------|
| BR1 | **Full Balance Payment Only** — The system always pays the entire outstanding account balance. Partial payments are not supported. The payment amount equals the current account balance at the time of processing. |
| BR2 | **Positive Balance Required** — A payment can only be processed if the account has a positive outstanding balance (greater than zero). Accounts with zero or negative balances cannot be paid. |
| BR3 | **Two-Step Confirmation** — The payment process requires explicit user confirmation. On the first submission, the system displays the current balance and asks the user to confirm. Payment is only executed when the user enters "Y" (or "y") and presses Enter. |
| BR4 | **Sequential Transaction ID Generation** — Each payment is assigned a unique transaction ID that is one greater than the highest existing transaction ID in the system. |
| BR5 | **Automatic Payment Recording** — Every successful payment creates a transaction record with type "02" (bill payment), category 2, and a description of "BILL PAYMENT - ONLINE". |
| BR6 | **Balance Zeroed on Payment** — After a successful payment, the account balance is set to exactly zero (balance minus payment amount, where payment amount equals the full balance). |
| BR7 | **Card Number Association** — The payment transaction record is linked to the card number associated with the account via the card-account cross-reference. |
| BR8 | **Timestamp Recording** — Both the origination and processing timestamps on the transaction record are set to the current date and time at the moment of payment. |
| BR9 | **Decline on "N" Confirmation** — If the user enters "N" (or "n") in the confirmation field, the screen is cleared and the payment is not processed. |
| BR10 | **Authentication Required** — If a user attempts to access the Bill Payment screen without being signed in (no session context), they are redirected to the Sign-on screen. |

### Decision Table

```
┌──────────────────────────────┬───────────────────────────────────────────────────────┐
│ User Action                  │ System Response                                       │
├──────────────────────────────┼───────────────────────────────────────────────────────┤
│ Enter with empty Account ID  │ Error: "Acct ID can NOT be empty..."                  │
│ Enter with invalid Account ID│ Error: "Account ID NOT found..."                      │
│ Enter with valid Account ID, │ Display current balance; prompt for confirmation:     │
│   Confirmation blank         │   "Confirm to make a bill payment..."                 │
│ Enter with valid Account ID, │ Process payment; display success message with         │
│   Confirmation = Y or y      │   transaction ID; clear form                          │
│ Enter with valid Account ID, │ Clear the screen; do not process payment              │
│   Confirmation = N or n      │                                                       │
│ Enter with valid Account ID, │ Error: "Invalid value. Valid values are (Y/N)..."     │
│   Confirmation = other value │                                                       │
│ Enter with valid Account ID  │ Error: "You have nothing to pay..."                   │
│   but balance is zero/neg.   │                                                       │
│ Press F3                     │ Navigate back to previous screen (Main Menu)           │
│ Press F4                     │ Clear all input fields and messages                    │
│ Press any other key          │ Error: "Invalid key pressed. Please see below..."     │
└──────────────────────────────┴───────────────────────────────────────────────────────┘
```

---

## Section 4 — Data Entities Involved

### Entity Descriptions

| Entity                     | Description                                                              | Role in This Use Case                                          |
|----------------------------|--------------------------------------------------------------------------|----------------------------------------------------------------|
| Account                    | Credit card account record containing balance, credit limits, and dates  | Read to display current balance; updated to zero after payment |
| Card Cross-Reference       | Mapping between accounts and their associated card numbers               | Read to retrieve the card number linked to the account         |
| Transaction                | Record of a financial transaction (purchase, payment, etc.)              | Written to create a new bill payment transaction record        |

### Entity Relationships

```
┌─────────────────┐        ┌──────────────────────┐        ┌─────────────────┐
│    Account      │ 1────M │  Card Cross-Reference│ M────1 │   Customer      │
│                 │        │                      │        │  (not directly  │
│  Account ID (PK)│<───────│  Account ID (FK)     │        │   accessed)     │
│  Balance        │        │  Card Number         │        │                 │
│  Credit Limit   │        │  Customer ID         │        │                 │
│  Status         │        │                      │        │                 │
└────────┬────────┘        └──────────┬───────────┘        └─────────────────┘
         │                            │
         │ 1                          │
         │                            │
         │ M                          │
┌────────┴────────┐                   │
│  Transaction    │                   │
│                 │                   │
│  Transaction ID │                   │
│  Type Code      │                   │
│  Amount         │                   │
│  Card Number    │───────────────────┘
│  Timestamps     │     (Card Number copied from Cross-Reference)
└─────────────────┘
```

### Data Fields Displayed

| #  | Field           | Format                | Source                     | Description                                                  |
|----|-----------------|-----------------------|----------------------------|--------------------------------------------------------------|
| 1  | Account ID      | 11-digit numeric      | User input                 | The account to pay; entered by the user                      |
| 2  | Current Balance | +9999999999.99        | Account entity             | The outstanding balance on the account, displayed after lookup|
| 3  | Confirmation    | Single character (Y/N)| User input                 | User's yes/no confirmation to proceed with payment           |

**Fields in the Account entity NOT displayed on this screen:** Active Status, Credit Limit, Cash Credit Limit, Open Date, Expiration Date, Reissue Date, Current Cycle Credit, Current Cycle Debit, ZIP Code, Group ID.

**Fields in the Transaction record NOT displayed on this screen:** Transaction ID (shown only in the success message), Type Code, Category Code, Source, Description, Merchant ID, Merchant Name, Merchant City, Merchant ZIP, Card Number, Origination Timestamp, Processing Timestamp.

---

## Section 5 — Screen / Interface Description

### Screen Layout

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ Tran: CB00   AWS Mainframe Modernization            Date: 05/07/26              │
│ Prog: COBIL00C              CardDemo                Time: 21:50:00              │
│                                                                                  │
│                                   Bill Payment                                   │
│                                                                                  │
│      Enter Acct ID: 00000012345                                                  │
│                                                                                  │
│      -----------------------------------------------------------------------     │
│                                                                                  │
│                                                                                  │
│      Your current balance is:  +0000001500.00                                    │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│      Do you want to pay your balance now. Please confirm:  Y (Y/N)              │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│ Payment successful. Your Transaction ID is 0000000000000124.                     │
│ ENTER=Continue  F3=Back  F4=Clear                                                │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Interface Elements

| Element                | Type    | Description                                                                            |
|------------------------|---------|----------------------------------------------------------------------------------------|
| Transaction ID         | Display | Shows "CB00" — identifies this screen's transaction                                    |
| Program Name           | Display | Shows "COBIL00C" — identifies the running program                                      |
| Title Line 1           | Display | Application title: "AWS Mainframe Modernization"                                       |
| Title Line 2           | Display | Application subtitle: "CardDemo"                                                       |
| Current Date           | Display | Today's date in mm/dd/yy format                                                       |
| Current Time           | Display | Current time in hh:mm:ss format                                                       |
| Bill Payment Heading   | Display | Screen heading "Bill Payment" centered on the screen                                   |
| Account ID             | Input   | 11-character field where the user enters the account number; cursor starts here        |
| Separator Line         | Display | Horizontal rule separating the input area from the results area                        |
| Current Balance        | Display | Shows the account's outstanding balance in +9999999999.99 format after account lookup  |
| Confirmation Prompt    | Display | Text asking user to confirm: "Do you want to pay your balance now. Please confirm:"   |
| Confirmation           | Input   | 1-character field for Y/N response; underlined                                         |
| Y/N Hint               | Display | Shows "(Y/N)" hint next to the confirmation field                                     |
| Message Area           | Display | 78-character area for error messages (red) or success messages (green)                 |
| Function Key Legend     | Display | Shows available keys: "ENTER=Continue  F3=Back  F4=Clear"                              |

### Available Actions

| Action            | How to Invoke       | Description                                                        |
|-------------------|---------------------|--------------------------------------------------------------------|
| Submit / Continue | Press Enter         | Validates input and processes the current step (lookup or payment)  |
| Go Back           | Press F3            | Returns to the previous screen (Main Menu or originating screen)   |
| Clear Form        | Press F4            | Clears all input fields (Account ID, Balance, Confirmation) and messages |

---

## Section 6 — Alternative & Error Flows

### AF1: Empty Account ID

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User presses Enter without entering an Account ID                        |
| 2    | System displays error: "Acct ID can NOT be empty..."                     |
| 3    | Cursor is placed in the Account ID field for correction                  |

### AF2: Account Not Found

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User enters an Account ID that does not exist in the system              |
| 2    | System displays error: "Account ID NOT found..."                         |
| 3    | Cursor is placed in the Account ID field for correction                  |

### AF3: Account Lookup System Error

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User enters an Account ID                                                |
| 2    | System encounters an unexpected error while looking up the account       |
| 3    | System displays error: "Unable to lookup Account..."                     |
| 4    | Cursor is placed in the Account ID field                                 |

### AF4: Zero or Negative Balance

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User enters a valid Account ID for an account with zero or negative balance |
| 2    | System retrieves the account and checks the balance                      |
| 3    | System displays error: "You have nothing to pay..."                      |
| 4    | Cursor is placed in the Account ID field                                 |

### AF5: Invalid Confirmation Value

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User enters a valid Account ID with a positive balance                   |
| 2    | User types a character other than Y, y, N, or n in the Confirmation field|
| 3    | System displays error: "Invalid value. Valid values are (Y/N)..."        |
| 4    | Cursor is placed in the Confirmation field for correction                |

### AF6: User Declines Payment (N)

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User enters a valid Account ID with a positive balance                   |
| 2    | User types "N" (or "n") in the Confirmation field and presses Enter      |
| 3    | System clears all input fields and messages; no payment is processed     |
| 4    | User can enter a new Account ID or navigate away                         |

### AF7: Card Cross-Reference Not Found

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User confirms payment with "Y"                                           |
| 2    | System finds the account but cannot find a linked card number            |
| 3    | System displays error: "Account ID NOT found..."                         |
| 4    | Cursor is placed in the Account ID field                                 |

### AF8: Card Cross-Reference System Error

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User confirms payment with "Y"                                           |
| 2    | System encounters an unexpected error looking up the card cross-reference|
| 3    | System displays error: "Unable to lookup XREF AIX file..."              |
| 4    | Cursor is placed in the Account ID field                                 |

### AF9: Transaction ID Generation Error

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User confirms payment with "Y"                                           |
| 2    | System encounters an error browsing the transaction file to generate ID  |
| 3    | System displays error: "Transaction ID NOT found..." or "Unable to lookup Transaction..." |
| 4    | Cursor is placed in the Account ID field                                 |

### AF10: Duplicate Transaction ID

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User confirms payment with "Y"                                           |
| 2    | System generates a transaction ID that already exists (concurrent usage) |
| 3    | System displays error: "Tran ID already exist..."                        |
| 4    | Cursor is placed in the Account ID field; payment is not completed       |

### AF11: Transaction Write System Error

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User confirms payment with "Y"                                           |
| 2    | System encounters an unexpected error writing the transaction record     |
| 3    | System displays error: "Unable to Add Bill pay Transaction..."           |
| 4    | Cursor is placed in the Account ID field                                 |

### AF12: Account Balance Update Error (Not Found)

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | Transaction record is written successfully                               |
| 2    | System encounters a "not found" error updating the account balance       |
| 3    | System displays error: "Account ID NOT found..."                         |
| 4    | Cursor is placed in the Account ID field                                 |

### AF13: Account Balance Update System Error

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | Transaction record is written successfully                               |
| 2    | System encounters an unexpected error updating the account balance       |
| 3    | System displays error: "Unable to Update Account..."                     |
| 4    | Cursor is placed in the Account ID field                                 |

### AF14: Invalid Key Pressed

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User presses any key other than Enter, F3, or F4                         |
| 2    | System displays error: "Invalid key pressed. Please see below..."        |
| 3    | Screen content is preserved; user can press a valid key                  |

### AF15: User Clears the Screen (F4)

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User presses F4 at any point                                             |
| 2    | System clears all input fields (Account ID, Balance, Confirmation) and messages |
| 3    | Cursor is placed in the Account ID field; user can start over           |

### AF16: Pre-Filled Account ID from Transaction List

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User navigates to Bill Payment with a pre-selected account from another screen |
| 2    | System pre-fills the Account ID field with the provided value            |
| 3    | System automatically looks up the account and displays the balance       |
| 4    | User proceeds with confirmation as in the happy path                     |

---

## Section 7 — Business Process Context

### Navigation Context

```
CardDemo Application
│
├── CC00 — Sign-on
│   │
│   ├── CM00 — Main Menu (Regular Users)
│   │   ├── Account View (CAVW)
│   │   ├── Account Update (CAUP)
│   │   ├── Card List (CCLI)
│   │   ├── Card Detail (CCDL)
│   │   ├── Card Update (CCUP)
│   │   ├── Transaction List (CT01)
│   │   ├── Transaction View (CT02)
│   │   ├── Transaction Add (CT03)
│   │   ├──>>> Bill Payment (CB00) <<<
│   │   └── Reports (CR00)
│   │
│   └── CA00 — Admin Menu (Admin Users)
│       ├── User List
│       ├── User Add
│       ├── User Update
│       └── User Delete
```

### Downstream Use Cases

| Destination      | Trigger                 | What Happens Next                                              |
|------------------|-------------------------|----------------------------------------------------------------|
| Main Menu (CM00) | User presses F3         | User returns to the Main Menu and can select another function  |
| Sign-on (CC00)   | No session context      | User is redirected to re-authenticate                          |

### Upstream Use Cases

| Source                  | Navigation Path                                                |
|-------------------------|----------------------------------------------------------------|
| Main Menu (CM00)        | User selects option 10 "Bill Payment" from the Main Menu       |
| Transaction List (CT01) | User may navigate with a pre-selected account (via shared session context) |

### End-to-End Journey Examples

**Journey 1: Cardholder Pays Full Balance**
```
Sign-on ──> Main Menu ──> Bill Payment ──> Enter Account ──> Confirm ──> Payment Recorded ──> Main Menu
```
_A cardholder signs in, navigates to Bill Payment, enters their account ID, confirms the payment, and returns to the Main Menu._

**Journey 2: Cardholder Reviews Account Then Pays**
```
Sign-on ──> Main Menu ──> Account View ──> Main Menu ──> Bill Payment ──> Confirm ──> Main Menu
```
_A cardholder first checks their account details to verify the balance, then navigates to Bill Payment to settle it._

**Journey 3: Cardholder Reviews Transactions Then Pays**
```
Sign-on ──> Main Menu ──> Transaction List ──> Bill Payment ──> Confirm ──> Main Menu
```
_A cardholder reviews their recent transactions, then navigates to Bill Payment with the account pre-filled from the transaction context._

**Journey 4: Cardholder Attempts Payment on Zero Balance**
```
Sign-on ──> Main Menu ──> Bill Payment ──> Enter Account ──> "You have nothing to pay..." ──> F3 ──> Main Menu
```
_A cardholder attempts to pay a balance that is already zero and is informed there is nothing to pay._

---

## Section 8 — Acceptance Criteria

### Core Functionality

| #     | Given                                                   | When                                                          | Then                                                                                                      |
|-------|---------------------------------------------------------|---------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| AC-01 | A user is signed in and on the Main Menu                | The user selects "Bill Payment" (option 10)                   | The Bill Payment screen is displayed with a blank Account ID field and the cursor in the Account ID field |
| AC-02 | The Bill Payment screen is displayed                    | The user enters a valid Account ID and presses Enter          | The system displays the current account balance and the message "Confirm to make a bill payment..."       |
| AC-03 | The current balance is displayed for a valid account    | The user enters "Y" in the Confirmation field and presses Enter | A payment transaction record is created for the full balance amount, the account balance is set to zero, and the message "Payment successful. Your Transaction ID is {ID}." is displayed in green |
| AC-04 | The current balance is displayed for a valid account    | The user enters "y" (lowercase) and presses Enter             | The system processes the payment identically to entering uppercase "Y"                                   |
| AC-05 | A payment has been successfully processed               | The user views the transaction records                        | The new transaction record exists with type "02", category 2, description "BILL PAYMENT - ONLINE", and the amount equal to the previous balance |

### Input Validation

| #     | Given                                                   | When                                                          | Then                                                                                                      |
|-------|---------------------------------------------------------|---------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| AC-06 | The Bill Payment screen is displayed                    | The user presses Enter without entering an Account ID         | The error message "Acct ID can NOT be empty..." is displayed and the cursor is in the Account ID field    |
| AC-07 | The Bill Payment screen is displayed                    | The user enters a non-existent Account ID and presses Enter   | The error message "Account ID NOT found..." is displayed and the cursor is in the Account ID field        |
| AC-08 | The current balance is displayed for a valid account    | The user enters an invalid character (not Y/y/N/n) in Confirmation | The error message "Invalid value. Valid values are (Y/N)..." is displayed and the cursor is in the Confirmation field |
| AC-09 | The user enters a valid Account ID with balance zero    | The user presses Enter                                        | The error message "You have nothing to pay..." is displayed and the cursor is in the Account ID field     |
| AC-10 | The user enters a valid Account ID with a negative balance | The user presses Enter                                     | The error message "You have nothing to pay..." is displayed and the cursor is in the Account ID field     |

### Navigation

| #     | Given                                                   | When                                                          | Then                                                                                                      |
|-------|---------------------------------------------------------|---------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| AC-11 | The Bill Payment screen is displayed                    | The user presses F3                                           | The system navigates back to the Main Menu (or the originating screen)                                   |
| AC-12 | The Bill Payment screen is displayed with data          | The user presses F4                                           | All input fields (Account ID, Balance display, Confirmation) and messages are cleared; cursor returns to Account ID |
| AC-13 | The Bill Payment screen is displayed                    | The user presses any key other than Enter, F3, or F4          | The error message "Invalid key pressed. Please see below..." is displayed                                |
| AC-14 | The user accesses the Bill Payment URL without a session | The system checks for authentication context                  | The user is redirected to the Sign-on screen                                                             |

### Confirmation Flow

| #     | Given                                                   | When                                                          | Then                                                                                                      |
|-------|---------------------------------------------------------|---------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| AC-15 | The current balance is displayed for a valid account    | The user enters "N" in the Confirmation field and presses Enter | The screen is cleared; no payment is processed                                                           |
| AC-16 | The current balance is displayed for a valid account    | The user enters "n" (lowercase) and presses Enter             | The screen is cleared identically to entering uppercase "N"                                              |

### Data Integrity

| #     | Given                                                   | When                                                          | Then                                                                                                      |
|-------|---------------------------------------------------------|---------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| AC-17 | A payment is successfully processed                     | The account balance is checked                                | The account balance is exactly zero                                                                      |
| AC-18 | A payment is successfully processed                     | The transaction record is checked                             | The transaction ID is unique and exactly one greater than the previously highest transaction ID           |
| AC-19 | A payment is successfully processed                     | The transaction record is checked                             | The card number on the transaction matches the card associated with the account via the cross-reference  |
| AC-20 | A payment is successfully processed                     | The transaction record timestamps are checked                 | Both origination and processing timestamps reflect the date and time of the payment                      |
| AC-21 | Two users attempt to pay different accounts simultaneously | Both payments are processed                                 | Each payment gets a unique transaction ID; no duplicate IDs are created                                  |

### Pre-Filled Account Context

| #     | Given                                                   | When                                                          | Then                                                                                                      |
|-------|---------------------------------------------------------|---------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| AC-22 | A user navigates to Bill Payment with a pre-selected account | The Bill Payment screen is displayed                       | The Account ID field is pre-filled and the account balance is automatically displayed                    |

---

## Section 9 — Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                      | Technical Reference                                                                                       |
|----------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| User selects Bill Payment from Main Menu           | COMEN01C issues XCTL to COBIL00C with COMMAREA; PGM-CONTEXT=0                                            |
| Display blank Bill Payment screen                  | MAIN-PARA: MOVE LOW-VALUES TO COBIL0AO; SEND MAP('COBIL0A') MAPSET('COBIL00'); RETURN TRANSID('CB00')    |
| Pre-fill Account ID from selection context         | MAIN-PARA: IF CDEMO-CB00-TRN-SELECTED NOT = SPACES, MOVE to ACTIDINI, PERFORM PROCESS-ENTER-KEY          |
| Validate Account ID is not empty                   | PROCESS-ENTER-KEY: EVALUATE ACTIDINI OF COBIL0AI = SPACES OR LOW-VALUES; error "Acct ID can NOT be empty..." (line 159-167) |
| Look up account record                             | READ-ACCTDAT-FILE: EXEC CICS READ DATASET('ACCTDAT') INTO(ACCOUNT-RECORD) RIDFLD(ACCT-ID) UPDATE (line 345-372) |
| Display current balance                            | PROCESS-ENTER-KEY: MOVE ACCT-CURR-BAL TO WS-CURR-BAL; MOVE WS-CURR-BAL TO CURBALI OF COBIL0AI (line 193-194) |
| Validate balance is positive                       | PROCESS-ENTER-KEY: IF ACCT-CURR-BAL <= ZEROS; error "You have nothing to pay..." (line 198-206)           |
| Validate confirmation field value                  | PROCESS-ENTER-KEY: EVALUATE CONFIRMI OF COBIL0AI — WHEN 'Y'/'y', 'N'/'n', SPACES, OTHER (line 173-191)   |
| Prompt for confirmation                            | PROCESS-ENTER-KEY (ELSE branch): MOVE 'Confirm to make a bill payment...' TO WS-MESSAGE (line 237-239)    |
| Look up card number for account                    | READ-CXACAIX-FILE: EXEC CICS READ DATASET('CXACAIX') INTO(CARD-XREF-RECORD) RIDFLD(XREF-ACCT-ID) (line 410-436) |
| Generate new transaction ID                        | STARTBR-TRANSACT-FILE (HIGH-VALUES), READPREV-TRANSACT-FILE, ENDBR-TRANSACT-FILE; ADD 1 TO WS-TRAN-ID-NUM (line 212-217) |
| Build transaction record                           | PROCESS-ENTER-KEY: INITIALIZE TRAN-RECORD; populate TRAN-TYPE-CD='02', TRAN-CAT-CD=2, etc. (line 218-232) |
| Get current timestamp                              | GET-CURRENT-TIMESTAMP: EXEC CICS ASKTIME/FORMATTIME (line 251-267)                                        |
| Write transaction record                           | WRITE-TRANSACT-FILE: EXEC CICS WRITE DATASET('TRANSACT') FROM(TRAN-RECORD) RIDFLD(TRAN-ID) (line 512-547) |
| Update account balance to zero                     | PROCESS-ENTER-KEY: COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT (line 234); UPDATE-ACCTDAT-FILE: EXEC CICS REWRITE (line 379-403) |
| Display success message                            | WRITE-TRANSACT-FILE (NORMAL): STRING 'Payment successful...' INTO WS-MESSAGE; set ERRMSGC to DFHGREEN (line 524-532) |
| Clear all fields (F4)                              | CLEAR-CURRENT-SCREEN: PERFORM INITIALIZE-ALL-FIELDS; PERFORM SEND-BILLPAY-SCREEN (line 552-555)           |
| Navigate back (F3)                                 | RETURN-TO-PREV-SCREEN: EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA) (line 273-284) |
| Handle invalid key                                 | MAIN-PARA: WHEN OTHER — MOVE CCDA-MSG-INVALID-KEY TO WS-MESSAGE (line 138-141)                            |
| Redirect unauthenticated user                      | MAIN-PARA: IF EIBCALEN = 0, MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM, PERFORM RETURN-TO-PREV-SCREEN (line 107-109) |

### Source File Mapping

| Business Concept                   | Source File(s)                                                                  |
|------------------------------------|---------------------------------------------------------------------------------|
| Bill Payment program logic         | `app/cbl/COBIL00C.cbl`                                                         |
| Screen layout and field definitions| `app/bms/COBIL00.bms`                                                          |
| Session/navigation shared area     | `app/cpy/COCOM01Y.cpy`                                                         |
| Application screen titles          | `app/cpy/COTTL01Y.cpy`                                                         |
| Date and time formatting           | `app/cpy/CSDAT01Y.cpy`                                                         |
| Shared error messages              | `app/cpy/CSMSG01Y.cpy`                                                         |
| Account record layout              | `app/cpy/CVACT01Y.cpy`                                                         |
| Card cross-reference record layout | `app/cpy/CVACT03Y.cpy`                                                         |
| Transaction record layout          | `app/cpy/CVTRA05Y.cpy`                                                         |
| Main Menu option routing           | `app/cpy/COMEN02Y.cpy`                                                         |

### Migration Considerations

| #  | Consideration                                                                                                      |
|----|---------------------------------------------------------------------------------------------------------------------|
| M1 | **Pseudo-conversational to stateless HTTP** — The CICS pseudo-conversational model (RETURN TRANSID with COMMAREA) must be replaced with stateless request/response (REST API or session-based web). The two-step confirmation flow (display balance, then confirm) maps naturally to a two-step web form or a modal confirmation dialog. |
| M2 | **Transaction atomicity** — The current implementation writes the transaction record and updates the account balance as two separate I/O operations without an explicit commit boundary. CICS provides an implicit syncpoint at task end. A modern implementation must wrap these in an explicit database transaction to ensure both succeed or both roll back. |
| M3 | **Sequential ID generation** — The browse-last-and-increment pattern for generating transaction IDs has a concurrency race condition. Modern implementations should use database auto-increment, UUID, or a sequence generator to guarantee uniqueness under concurrent access. |
| M4 | **Account-level locking** — The READ UPDATE command acquires a record lock on the account for the duration of the payment processing. Modern systems should implement optimistic concurrency control (e.g., version column) or pessimistic locking at the database level to prevent lost updates. |
| M5 | **Full-balance-only constraint** — The system only supports paying the full outstanding balance. A migration should evaluate whether partial payment capability is a business requirement and, if so, add amount input validation. |
| M6 | **Hard-coded transaction metadata** — Values like merchant ID (999999999), source ("POS TERM"), and description ("BILL PAYMENT - ONLINE") are hard-coded. These should be externalized to configuration in a modern system. |
| M7 | **3270 terminal to web/mobile UI** — The BMS map (24x80 fixed layout) must be replaced with a responsive web or mobile interface. The function key actions (Enter, F3, F4) map to button clicks or keyboard shortcuts in a modern UI. |
| M8 | **VSAM to relational database** — The three VSAM files (ACCTDAT, CXACAIX, TRANSACT) should be migrated to relational database tables with proper foreign keys and indexes. The alternate index path (CXACAIX) becomes a standard indexed lookup. |
| M9 | **No user audit trail** — The current transaction record does not capture which user made the payment (CDEMO-USER-ID is available in the session but not written to the record). A modern implementation should include user identification in the transaction for audit purposes. |
| M10 | **Error message standardization** — Error messages are embedded as literal strings in the program. A modern implementation should use a message catalog or internationalization framework for consistent, localizable error messages. |
| M11 | **COMMAREA to API contracts** — The shared COMMAREA structure used for inter-program navigation should be replaced with well-defined API contracts (request/response DTOs) between microservices or application layers. |

---

## Section 10 — Related Use Cases / End-to-End Composition

| Journey Name                          | Use Cases Involved                                           | Business Scenario                                                                             |
|---------------------------------------|--------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| Full Payment Cycle                    | Sign-on (CC00), Main Menu (CM00), Bill Payment (CB00)        | Cardholder signs in and pays their full account balance                                       |
| Review and Pay                        | Sign-on (CC00), Main Menu (CM00), Account View (CAVW), Bill Payment (CB00) | Cardholder reviews their account details to verify the balance, then pays it                  |
| Transaction Audit After Payment       | Sign-on (CC00), Main Menu (CM00), Bill Payment (CB00), Transaction List (CT01), Transaction View (CT02) | Cardholder pays their balance, then reviews the transaction list to confirm the payment record |
| Monthly Statement Cycle               | Bill Payment (CB00), Daily Transaction Posting (Batch), Report Generation (CR00) | After online payments are made, batch processing posts transactions and generates monthly statements |
| Account Lifecycle                     | Sign-on (CC00), Account View (CAVW), Transaction Add (CT03), Bill Payment (CB00), Account Update (CAUP) | Full account lifecycle from viewing, accumulating transactions, paying the balance, and updating account details |

**Note:** Individual use case flows must be composed into journey-level test documents for complete end-to-end validation. Some use cases referenced above (e.g., CT01, CT02, CT03) may not yet have their own Business Use Case Flow documents.

---

## Document Footer

| Attribute                    | Value                                    |
|------------------------------|------------------------------------------|
| **Source Program**           | COBIL00C.cbl (573 lines)                |
| **Transaction ID**           | CB00                                     |
| **Data Source**              | Technical Flow Analysis + COBOL source verification |
| **Acceptance Criteria**      | 22                                       |
| **Business Rules**           | 10                                       |
| **Validation Rules**         | 3                                        |
| **Alternative / Error Flows**| 16                                       |

# COTRN00C (CT00) — Transaction List: CICS Business Use Case Flow Analysis

---

## Section 1: Use Case Overview

| Attribute          | Value                                                                 |
|--------------------|-----------------------------------------------------------------------|
| **Use Case Name**  | View and Browse Transaction List                                      |
| **Use Case ID**    | UC-CT00-001                                                           |
| **Actor(s)**       | Authenticated Regular User (card holder / customer service agent)     |
| **Business Goal**  | Allow users to browse the full list of credit card transactions, search by Transaction ID, page through results, and select a transaction to view its details |
| **Preconditions**  | 1. User has successfully signed in to the CardDemo application        |
|                    | 2. User has navigated to the Main Menu                                |
|                    | 3. User selects option 6 ("Transaction List") from the Main Menu      |
| **Postconditions** | User has viewed transaction list data and optionally navigated to a transaction detail view, or returned to the Main Menu |
| **Trigger**        | User selects "Transaction List" (option 6) from the Main Menu         |
| **Data Access**    | Read-only (browse operations only; no records are created, updated, or deleted) |
| **Frequency**      | High — primary inquiry function for reviewing credit card transaction history |

### Business Context

The Transaction List use case is a core inquiry function in the CardDemo credit card management system. It provides users with a paginated, searchable view of all credit card transactions stored in the system. Users — typically customer service agents or account holders — use this screen to locate specific transactions by browsing sequentially or jumping to a specific Transaction ID. From the list, users can select any transaction to drill into its full details. This use case serves as the primary entry point for the transaction inquiry workflow and is a prerequisite for viewing individual transaction details.

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CardDemo Functional Domains                      │
├─────────────────┬─────────────────┬─────────────────────────────────┤
│   Account       │   Card          │   Transaction                   │
│   Management    │   Management    │   Management                    │
│                 │                 │                                 │
│  - View Account │  - Card List    │  ╔═══════════════════════════╗  │
│  - Update Acct  │  - Card Detail  │  ║ >> TRANSACTION LIST <<    ║  │
│                 │  - Update Card  │  ║    (This Use Case)        ║  │
│                 │                 │  ╚═══════════════════════════╝  │
│                 │                 │  - View Transaction Detail      │
│                 │                 │  - Add Transaction              │
├─────────────────┴─────────────────┴─────────────────────────────────┤
│   Billing               │   Reporting          │   Administration   │
│  - Bill Payment          │  - Generate Reports  │  - User Management │
└──────────────────────────┴──────────────────────┴────────────────────┘
```

---

## Section 2: User Journey (Happy Path)

### Sequence Diagram

```
 User                                        System
  │                                             │
  │  Select "Transaction List"                  │
  │  from Main Menu (option 6)                  │
  │────────────────────────────────────────────>│
  │                                             │
  │         Display first page of transactions  │
  │         (up to 10 records, page 1)          │
  │<────────────────────────────────────────────│
  │                                             │
  │  [Optional] Enter Transaction ID            │
  │  in Search field, press Enter               │
  │────────────────────────────────────────────>│
  │                                             │
  │         Display transactions starting       │
  │         from the entered ID                 │
  │<────────────────────────────────────────────│
  │                                             │
  │  [Optional] Press Forward (F8) to           │
  │  see next 10 transactions                   │
  │────────────────────────────────────────────>│
  │                                             │
  │         Display next page of transactions   │
  │         (page number incremented)           │
  │<────────────────────────────────────────────│
  │                                             │
  │  [Optional] Press Backward (F7) to          │
  │  see previous 10 transactions               │
  │────────────────────────────────────────────>│
  │                                             │
  │         Display previous page of            │
  │         transactions (page number           │
  │         decremented)                        │
  │<────────────────────────────────────────────│
  │                                             │
  │  Type 'S' next to a transaction             │
  │  and press Enter                            │
  │────────────────────────────────────────────>│
  │                                             │
  │         Navigate to Transaction             │
  │         Detail screen (CT01)                │
  │<────────────────────────────────────────────│
  │                                             │
```

### Step-by-Step Narrative

| Step | Actor  | Action                                                    | System Response                                                                                                  |
|------|--------|-----------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| 1    | User   | Selects "Transaction List" (option 6) from the Main Menu  | System displays the Transaction List screen with the first page of transactions (up to 10 records). Page number shows "1". The cursor is positioned on the Search Transaction ID field. |
| 2    | User   | Reviews the displayed transactions                        | Each row shows: Transaction ID, Date (MM/DD/YY), Description (first 26 characters), and Amount (signed, formatted with decimal). |
| 3    | User   | *(Optional)* Enters a Transaction ID in the Search field and presses Enter | System repositions the list to start at (or near) the entered Transaction ID and displays up to 10 transactions from that point. Page number resets to 1. |
| 4    | User   | *(Optional)* Presses F8 (Forward) to see more transactions | System displays the next 10 transactions. Page number increments by 1. If no more transactions exist beyond this page, the system notes this is the last page. |
| 5    | User   | *(Optional)* Presses F7 (Backward) to see earlier transactions | System displays the previous 10 transactions. Page number decrements by 1. Transactions are displayed in ascending order by Transaction ID. |
| 6    | User   | Types 'S' in the selection field next to a transaction and presses Enter | System navigates to the Transaction Detail screen, displaying the full details of the selected transaction. |
| 7    | User   | *(Alternative)* Presses F3 (Back) to return to the Main Menu | System returns the user to the Main Menu screen. |

---

## Section 3: Business Rules & Validations

### Input Validation Rules

| #  | Field                 | Rule                                                                                  | Error Message Shown to User                        |
|----|-----------------------|---------------------------------------------------------------------------------------|----------------------------------------------------|
| V1 | Search Transaction ID | Must be numeric if provided. Non-numeric values are rejected.                         | `Tran ID must be Numeric ...`                      |
| V2 | Selection Field       | Only the value 'S' (upper or lower case) is accepted as a valid selection action.     | `Invalid selection. Valid value is S`               |
| V3 | Function Keys         | Only Enter, F3, F7, and F8 are recognized. Any other key is rejected.                 | `Invalid key pressed. Please see below...`         |

### Business Rules

| #   | Rule                                                                                                                                                         |
|-----|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| BR1 | **Page Size**: The system displays a fixed maximum of 10 transactions per page.                                                                              |
| BR2 | **Sort Order**: Transactions are displayed in ascending order by Transaction ID (the natural key sequence of the data store).                                 |
| BR3 | **Search Positioning**: When a user enters a valid Transaction ID in the Search field, the system repositions the list to begin at or after that ID. The page counter resets to 1. |
| BR4 | **Default Start Position**: When no Search Transaction ID is provided (field is blank), the list starts from the very first transaction in the data store.    |
| BR5 | **Forward Paging**: The system reads 10 records forward from the current position. After loading a page, the system peeks ahead one record to determine if more pages exist. |
| BR6 | **Backward Paging**: The system reads 10 records backward from the first record of the current page. Records are displayed in ascending order regardless of the direction of retrieval. |
| BR7 | **Page Boundary — Top**: If the user attempts to page backward when already on page 1, the system displays an informational message and does not change the displayed data. |
| BR8 | **Page Boundary — Bottom**: If the user attempts to page forward when already on the last page, the system displays an informational message and does not change the displayed data. |
| BR9 | **Single Selection**: Only one transaction can be selected at a time. The system scans selection fields from row 1 to row 10 and processes the first non-blank selection it finds. |
| BR10 | **Drill-Down Navigation**: Selecting a transaction with 'S' navigates the user to the Transaction Detail screen. The selected Transaction ID is passed to the detail screen. |
| BR11 | **Authentication Required**: If the system detects that no valid session context exists (user arrived without going through sign-in), the user is redirected to the Sign-On screen. |
| BR12 | **Read-Only Access**: The Transaction List screen does not modify any transaction data. All data store operations are browse/read operations only.             |
| BR13 | **Date Display Format**: Transaction dates are extracted from the original transaction timestamp and displayed in MM/DD/YY format.                             |
| BR14 | **Amount Display Format**: Transaction amounts are displayed in signed decimal format with two decimal places (e.g., +00000125.50).                            |
| BR15 | **Partial Page Display**: If fewer than 10 transactions remain, the system displays only the available records. Empty rows are cleared (shown as blank).       |

### Decision Table

```
┌──────────────────────────────────┬────────────────────────────────────────────────────┐
│ User Action                      │ System Response                                    │
├──────────────────────────────────┼────────────────────────────────────────────────────┤
│ Enter (no search ID, no 'S')     │ Reload list from beginning of data store (page 1)  │
│ Enter (valid search ID, no 'S')  │ Reposition list starting at/near that ID (page 1)  │
│ Enter (invalid search ID)        │ Show error "Tran ID must be Numeric ..."            │
│ Enter ('S' on a row)             │ Navigate to Transaction Detail for selected record  │
│ Enter ('s' on a row)             │ Navigate to Transaction Detail for selected record  │
│ Enter (invalid char on a row)    │ Show error "Invalid selection. Valid value is S"    │
│ F3 (Back)                        │ Return to Main Menu                                 │
│ F7 (Backward) on page > 1       │ Display previous page of 10 transactions             │
│ F7 (Backward) on page 1         │ Show "You are already at the top of the page..."    │
│ F8 (Forward) with more pages     │ Display next page of 10 transactions                │
│ F8 (Forward) on last page        │ Show "You are already at the bottom of the page..." │
│ Any other key                    │ Show "Invalid key pressed. Please see below..."     │
└──────────────────────────────────┴────────────────────────────────────────────────────┘
```

---

## Section 4: Data Entities Involved

### Entity Descriptions

| Entity              | Description                                                                                     | Role in This Use Case                                                     |
|---------------------|-------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| Transaction Records | Credit card transaction records containing payment details, merchant information, and timestamps | Primary data entity — browsed and displayed on the list screen            |
| User Session        | Shared session context carrying user identity, navigation state, and pagination bookmarks        | Maintains user identity, page position, and selected transaction between interactions |

### Entity Relationships

```
┌──────────────────────┐
│   User Session       │
│  (1 per user)        │
├──────────────────────┤
│  User ID             │
│  User Type           │
│  Current Page        │
│  First ID on Page    │
│  Last ID on Page     │
│  Selected Tran ID    │
│  More Pages Flag     │
└─────────┬────────────┘
          │ browses
          │ 1:N
          v
┌──────────────────────┐
│ Transaction Records  │
│  (many records)      │
├──────────────────────┤
│  Transaction ID (PK) │
│  Type Code           │
│  Category Code       │
│  Source               │
│  Description         │
│  Amount              │
│  Merchant ID         │
│  Merchant Name       │
│  Merchant City       │
│  Merchant ZIP        │
│  Card Number         │
│  Original Timestamp  │
│  Processing Timestamp│
└──────────────────────┘
```

### Data Fields Displayed

| #  | Field            | Format           | Source                       | Description                                           |
|----|------------------|------------------|------------------------------|-------------------------------------------------------|
| 1  | Transaction ID   | 16 characters    | Transaction Record — ID      | Unique identifier for the transaction                 |
| 2  | Date             | MM/DD/YY         | Transaction Record — Original Timestamp (extracted) | Date the transaction was originally created  |
| 3  | Description      | 26 characters    | Transaction Record — Description (first 26 chars)    | Brief description of the transaction        |
| 4  | Amount           | +99999999.99     | Transaction Record — Amount  | Signed transaction amount with two decimal places     |

**Fields in the underlying record NOT displayed on this screen:**

| Field               | Description                                |
|---------------------|--------------------------------------------|
| Type Code           | Transaction type classification code       |
| Category Code       | Transaction category numeric code          |
| Source              | Origination source of the transaction      |
| Merchant ID         | Numeric identifier of the merchant         |
| Merchant Name       | Name of the merchant                       |
| Merchant City       | City where the merchant is located         |
| Merchant ZIP        | ZIP/postal code of the merchant            |
| Card Number         | Credit card number associated with txn     |
| Processing Timestamp| When the transaction was processed         |

---

## Section 5: Screen / Interface Description

### Screen Layout

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ Tran: CT00    AWS Mainframe Modernization                   Date: 05/07/26      │
│ Prog: COTRN00C           CardDemo                           Time: 14:30:45      │
│                                                                                  │
│                              List Transactions              Page: 00000001       │
│                                                                                  │
│     Search Tran ID: [________________]                                           │
│                                                                                  │
│  Sel  Transaction ID    Date      Description                Amount              │
│  ---  ----------------  --------  --------------------------  ------------       │
│  [ ]  0000000000000001  01/15/26  Grocery Store Purchase      +00000045.99       │
│  [ ]  0000000000000002  01/15/26  Gas Station Payment         +00000032.50       │
│  [ ]  0000000000000003  01/16/26  Online Subscription         +00000012.99       │
│  [ ]  0000000000000004  01/16/26  Restaurant Dining           +00000067.25       │
│  [ ]  0000000000000005  01/17/26  Pharmacy Purchase           +00000023.45       │
│  [ ]  0000000000000006  01/17/26  Department Store            +00000189.99       │
│  [ ]  0000000000000007  01/18/26  Utility Bill Payment        +00000150.00       │
│  [ ]  0000000000000008  01/18/26  Electronics Store           +00000299.99       │
│  [ ]  0000000000000009  01/19/26  Airline Ticket              +00000425.00       │
│  [ ]  0000000000000010  01/19/26  Hotel Reservation           +00000175.50       │
│                                                                                  │
│            Type 'S' to View Transaction details from the list                    │
│                                                                                  │
│ [error/informational message area]                                               │
│ ENTER=Continue  F3=Back  F7=Backward  F8=Forward                                │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Interface Elements

| Element                | Type    | Description                                                                      |
|------------------------|---------|----------------------------------------------------------------------------------|
| Transaction ID (header)| Display | Shows the current transaction code "CT00"                                        |
| Program Name (header)  | Display | Shows the current program "COTRN00C"                                             |
| Application Title      | Display | Shows "AWS Mainframe Modernization" and "CardDemo"                               |
| Date                   | Display | Current system date in MM/DD/YY format                                           |
| Time                   | Display | Current system time in HH:MM:SS format                                           |
| Page Number            | Display | Current page number (8 digits)                                                   |
| Search Tran ID         | Input   | 16-character field for entering a Transaction ID to search/reposition the list    |
| Selection Field (x10)  | Input   | 1-character field per row for entering 'S' to select a transaction               |
| Transaction ID (x10)   | Display | Transaction ID for each row                                                      |
| Date (x10)             | Display | Transaction date for each row in MM/DD/YY format                                 |
| Description (x10)      | Display | Transaction description for each row (26 characters)                             |
| Amount (x10)           | Display | Transaction amount for each row in +99999999.99 format                           |
| Instruction Text       | Display | Instruction "Type 'S' to View Transaction details from the list"                 |
| Error/Info Message     | Display | Area for error messages and informational messages (78 characters, highlighted)   |
| Function Key Legend     | Display | Shows available keys: ENTER, F3, F7, F8                                          |

### Available Actions

| Action                       | How to Invoke                                   | Description                                                        |
|------------------------------|------------------------------------------------|--------------------------------------------------------------------|
| Search/Reposition List       | Enter a Transaction ID in the Search field, press Enter | Repositions the list to start at or near the entered ID   |
| Refresh/Reload List          | Press Enter with no search ID and no selection  | Reloads the list from the beginning of the data store              |
| Select Transaction for Detail| Type 'S' next to a transaction row, press Enter | Navigates to the Transaction Detail screen for the selected record |
| Page Forward                 | Press F8                                        | Displays the next 10 transactions                                  |
| Page Backward                | Press F7                                        | Displays the previous 10 transactions                              |
| Return to Main Menu          | Press F3                                        | Returns the user to the Main Menu screen                           |

---

## Section 6: Alternative & Error Flows

### AF1: No Records Found (Search ID Not Found)

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | User enters a Transaction ID in the Search field that does not exist in the data store.           |
| 2    | System attempts to position the browse at the specified ID.                                       |
| 3    | The data store reports that no record matches the given key.                                      |
| 4    | System displays the message: `You are at the top of the page...`                                 |
| 5    | The transaction list area is not refreshed. The cursor returns to the Search field.               |

### AF2: Non-Numeric Search Transaction ID

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | User enters a non-numeric value (e.g., "ABCD") in the Search Transaction ID field.               |
| 2    | System validates the input and detects non-numeric characters.                                    |
| 3    | System displays the error message: `Tran ID must be Numeric ...`                                 |
| 4    | The screen is redisplayed without changes. The cursor returns to the Search field.                |

### AF3: Invalid Selection Value

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | User types a character other than 'S' or 's' (e.g., 'X', 'V', '1') in a selection field.        |
| 2    | User presses Enter.                                                                               |
| 3    | System detects the invalid selection value.                                                       |
| 4    | System displays the error message: `Invalid selection. Valid value is S`                          |
| 5    | The list data remains unchanged. The cursor returns to the Search field.                          |

### AF4: Invalid Function Key Pressed

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | User presses a key other than Enter, F3, F7, or F8 (e.g., F1, F2, F5, Clear).                   |
| 2    | System detects the unrecognized key.                                                              |
| 3    | System displays the error message: `Invalid key pressed. Please see below...`                    |
| 4    | The screen is redisplayed without changes. The cursor returns to the Search field.                |

### AF5: Page Forward at End of Data (Last Page)

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | User is on the last page of transactions (no more records exist beyond the current page).         |
| 2    | User presses F8 (Forward).                                                                        |
| 3    | System detects that no more pages are available.                                                  |
| 4    | System displays the message: `You are already at the bottom of the page...`                      |
| 5    | The displayed data remains unchanged. The screen is not erased.                                   |

### AF6: Page Backward at Beginning of Data (First Page)

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | User is on page 1 of the transaction list.                                                        |
| 2    | User presses F7 (Backward).                                                                       |
| 3    | System detects that the user is already on the first page.                                        |
| 4    | System displays the message: `You are already at the top of the page...`                         |
| 5    | The displayed data remains unchanged. The screen is not erased.                                   |

### AF7: End of File Reached During Forward Paging

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | User presses F8 (Forward) or Enter to load transactions.                                          |
| 2    | System begins reading records forward. Fewer than 10 records remain before the end of the data.   |
| 3    | System displays the available records (fewer than 10). Empty rows are cleared to blank.           |
| 4    | System displays the message: `You have reached the bottom of the page...`                        |
| 5    | The "more pages" indicator is set to No, preventing further forward paging.                       |

### AF8: End of File Reached During Backward Paging

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | User presses F7 (Backward) to page backward.                                                     |
| 2    | System begins reading records backward. Fewer than 10 records exist before the current position.  |
| 3    | System displays the available records. Empty rows at the top of the list are cleared to blank.    |
| 4    | System displays the message: `You have reached the top of the page...`                           |
| 5    | Page number is adjusted accordingly.                                                              |

### AF9: System I/O Error During Data Access

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | System encounters an unexpected error while accessing the transaction data store.                 |
| 2    | System sets the error flag and displays the message: `Unable to lookup transaction...`            |
| 3    | The screen is redisplayed with the error message. The cursor returns to the Search field.         |
| 4    | The user may retry the operation or return to the Main Menu via F3.                               |

### AF10: Direct Access Without Sign-In

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | A user (or system process) invokes the Transaction List without a valid session context.          |
| 2    | System detects that no session data was provided.                                                 |
| 3    | System redirects the user to the Sign-On screen for authentication.                              |

---

## Section 7: Business Process Context

### Navigation Context

```
CardDemo Application Navigation Hierarchy
==========================================

Sign-On (CC00)
  │
  ├── Main Menu (CM00) ─── [Regular Users]
  │     │
  │     ├── [1] Account View (CAVW)
  │     ├── [2] Account Update (CAUP)
  │     ├── [3] Card List (CCLI)
  │     ├── [4] Card Detail / Update (CCUP)
  │     ├── [5] Bill Payment (CB00)
  │     │
  │     ├── [6] ╔══════════════════════════════╗
  │     │       ║  >> TRANSACTION LIST (CT00) <<║
  │     │       ╚══════════════════════════════╝
  │     │             │
  │     │             └── Transaction Detail / View (CT01)
  │     │
  │     ├── [7] Transaction Add (CT02)
  │     └── [8] Reports (CR00)
  │
  └── Admin Menu (CA00) ─── [Admin Users]
        │
        ├── User List (CU01)
        ├── User Add (CU02)
        ├── User Update (CU03)
        └── User Delete (CU04)
```

### Downstream Use Cases

| Destination                     | Trigger                                              | What Happens Next                                                          |
|---------------------------------|------------------------------------------------------|----------------------------------------------------------------------------|
| Transaction Detail View (CT01)  | User types 'S' next to a transaction and presses Enter | The system navigates to the Transaction Detail screen, displaying the full record for the selected transaction. The user can review all fields (merchant details, timestamps, card number, etc.) and then return to the Transaction List or the Main Menu. |

### Upstream Use Cases

| Source                          | Navigation Path                                                  |
|---------------------------------|------------------------------------------------------------------|
| Main Menu (CM00)                | User selects option 6 ("Transaction List") from the Main Menu    |
| Transaction Detail View (CT01)  | User presses F5 (Return to List) from the Transaction Detail screen, which navigates back to the Transaction List |

### End-to-End Journey Examples

**Journey 1: Customer Service Agent Reviews Recent Transactions**

```
Sign-On ──> Main Menu ──> Transaction List ──> [Browse pages] ──> Main Menu
```
> A customer calls to inquire about recent charges. The agent signs in, navigates to the Transaction List, browses through several pages of transactions to review activity, then returns to the Main Menu.

**Journey 2: Investigate a Specific Transaction**

```
Sign-On ──> Main Menu ──> Transaction List ──> [Search by ID] ──> Select 'S' ──> Transaction Detail ──> Transaction List ──> Main Menu
```
> A customer disputes a charge and provides a transaction reference number. The agent enters the Transaction ID in the Search field, locates the transaction, selects it to view full details including merchant information, then returns to the list and back to the Main Menu.

**Journey 3: End-of-Day Transaction Review**

```
Sign-On ──> Main Menu ──> Transaction List ──> [Page Forward repeatedly] ──> Select 'S' ──> Transaction Detail ──> Transaction List ──> [Continue browsing] ──> Main Menu
```
> A supervisor reviews the day's transactions by paging through the entire list. When a transaction of interest is found, the supervisor drills into the detail, reviews it, returns to the list, and continues browsing.

**Journey 4: Transaction Lookup Then Report Generation**

```
Sign-On ──> Main Menu ──> Transaction List ──> [Browse/Search] ──> Main Menu ──> Reports
```
> An analyst first browses the Transaction List to confirm certain transactions exist, then returns to the Main Menu and navigates to the Reports function to generate a formal report.

---

## Section 8: Acceptance Criteria

### Core Functionality

| #     | Given                                                    | When                                               | Then                                                                                             |
|-------|----------------------------------------------------------|----------------------------------------------------|-------------------------------------------------------------------------------------------------|
| AC-01 | The user is on the Main Menu                             | The user selects option 6 ("Transaction List")      | The system displays the Transaction List screen with the first page of up to 10 transactions    |
| AC-02 | The Transaction List screen is displayed                 | The screen loads for the first time                 | The page number displays "1" and the cursor is on the Search Transaction ID field               |
| AC-03 | The Transaction List screen is displayed with data       | The user reviews the screen                         | Each row shows Transaction ID, Date (MM/DD/YY), Description (26 chars), and Amount (+99999999.99) |
| AC-04 | Transactions exist in the data store                     | The first page is loaded                            | Transactions are displayed in ascending order by Transaction ID                                  |

### Search Functionality

| #     | Given                                                    | When                                               | Then                                                                                             |
|-------|----------------------------------------------------------|----------------------------------------------------|-------------------------------------------------------------------------------------------------|
| AC-05 | The Transaction List screen is displayed                 | The user enters a valid numeric Transaction ID and presses Enter | The list repositions to start at or near the entered ID, page counter resets to 1              |
| AC-06 | The Transaction List screen is displayed                 | The user leaves the Search field blank and presses Enter | The list reloads from the very first transaction in the data store                            |
| AC-07 | The Transaction List screen is displayed                 | The user enters a non-numeric value in the Search field and presses Enter | The error message "Tran ID must be Numeric ..." is displayed and the list is not changed     |
| AC-08 | The user searches for a Transaction ID that does not exist | The system attempts to find the record             | The message "You are at the top of the page..." is displayed                                   |

### Pagination

| #     | Given                                                    | When                                               | Then                                                                                             |
|-------|----------------------------------------------------------|----------------------------------------------------|-------------------------------------------------------------------------------------------------|
| AC-09 | The Transaction List shows page N and more records exist  | The user presses F8 (Forward)                      | The system displays the next 10 transactions and the page number increments to N+1              |
| AC-10 | The Transaction List shows page N (N > 1)                | The user presses F7 (Backward)                     | The system displays the previous 10 transactions and the page number decrements                 |
| AC-11 | The user is on the last page of transactions              | The user presses F8 (Forward)                      | The message "You are already at the bottom of the page..." is displayed and the data is unchanged |
| AC-12 | The user is on page 1                                    | The user presses F7 (Backward)                     | The message "You are already at the top of the page..." is displayed and the data is unchanged  |
| AC-13 | Fewer than 10 transactions remain for the current page   | The page is loaded                                 | Only the available transactions are displayed; empty rows are cleared to blank                   |

### Selection and Navigation

| #     | Given                                                    | When                                               | Then                                                                                             |
|-------|----------------------------------------------------------|----------------------------------------------------|-------------------------------------------------------------------------------------------------|
| AC-14 | The Transaction List displays transactions                | The user types 'S' next to a transaction and presses Enter | The system navigates to the Transaction Detail screen with the selected transaction's data     |
| AC-15 | The Transaction List displays transactions                | The user types 's' (lowercase) next to a transaction and presses Enter | The system navigates to the Transaction Detail screen (case-insensitive selection)             |
| AC-16 | The Transaction List displays transactions                | The user types an invalid character (not 'S') next to a transaction and presses Enter | The error message "Invalid selection. Valid value is S" is displayed                          |
| AC-17 | The user types 'S' on multiple rows                      | The user presses Enter                              | The system processes only the first non-blank selection (scanning from row 1 to row 10)         |
| AC-18 | The Transaction List screen is displayed                 | The user presses F3 (Back)                          | The system returns the user to the Main Menu                                                    |

### Input Validation

| #     | Given                                                    | When                                               | Then                                                                                             |
|-------|----------------------------------------------------------|----------------------------------------------------|-------------------------------------------------------------------------------------------------|
| AC-19 | The Transaction List screen is displayed                 | The user presses any key other than Enter, F3, F7, or F8 | The error message "Invalid key pressed. Please see below..." is displayed                     |
| AC-20 | No valid session context exists                          | A user attempts to access the Transaction List directly | The system redirects the user to the Sign-On screen                                           |

### Data Integrity

| #     | Given                                                    | When                                               | Then                                                                                             |
|-------|----------------------------------------------------------|----------------------------------------------------|-------------------------------------------------------------------------------------------------|
| AC-21 | Transactions are displayed                               | The user navigates to Transaction Detail and returns | The Transaction List page position and state are preserved via the session context              |
| AC-22 | A system I/O error occurs during data access              | The system attempts to read transaction records     | The error message "Unable to lookup transaction..." is displayed and the user can retry or exit |

---

## Section 9: Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                  | Technical Reference                                                                                                    |
|------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| Display first page on entry                    | `MAIN-PARA`: checks `EIBCALEN` > 0, tests `CDEMO-PGM-REENTER`; if first entry (`CDEMO-PGM-CONTEXT = 0`), calls `PROCESS-ENTER-KEY` then `SEND-TRNLST-SCREEN` (lines 107-116) |
| Redirect to Sign-On if no session              | `MAIN-PARA`: `IF EIBCALEN = 0` → MOVE 'COSGN00C' TO `CDEMO-TO-PROGRAM`, PERFORM `RETURN-TO-PREV-SCREEN` (lines 107-109) |
| Receive user input on re-entry                 | `MAIN-PARA`: `PERFORM RECEIVE-TRNLST-SCREEN` → `EXEC CICS RECEIVE MAP('COTRN0A') MAPSET('COTRN00')` (lines 118, 554-562) |
| Evaluate which key the user pressed            | `MAIN-PARA`: `EVALUATE EIBAID` with WHEN clauses for `DFHENTER`, `DFHPF3`, `DFHPF7`, `DFHPF8`, `OTHER` (lines 119-134) |
| Validate Search Transaction ID is numeric      | `PROCESS-ENTER-KEY`: `IF TRNIDINI OF COTRN0AI IS NUMERIC` / `ELSE` → error message 'Tran ID must be Numeric ...' (lines 206-218) |
| Scan selection fields (rows 1-10)              | `PROCESS-ENTER-KEY`: `EVALUATE TRUE` checking `SEL0001I` through `SEL0010I` for non-blank values (lines 148-182) |
| Validate selection value is 'S'                | `PROCESS-ENTER-KEY`: `EVALUATE CDEMO-CT00-TRN-SEL-FLG WHEN 'S' WHEN 's'` → XCTL; `WHEN OTHER` → error 'Invalid selection. Valid value is S' (lines 185-203) |
| Navigate to Transaction Detail                 | `PROCESS-ENTER-KEY`: `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)` where `CDEMO-TO-PROGRAM = 'COTRN01C'`, passes `CARDDEMO-COMMAREA` (lines 188-195) |
| Return to Main Menu (F3)                       | `MAIN-PARA`: MOVE 'COMEN01C' TO `CDEMO-TO-PROGRAM`, PERFORM `RETURN-TO-PREV-SCREEN` → `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)` (lines 123-124, 510-521) |
| Invalid key handling                           | `MAIN-PARA`: `WHEN OTHER` → MOVE `CCDA-MSG-INVALID-KEY` TO `WS-MESSAGE`, PERFORM `SEND-TRNLST-SCREEN` (lines 129-133) |
| Page forward                                   | `PROCESS-PAGE-FORWARD`: `STARTBR-TRANSACT-FILE`, loop `READNEXT-TRANSACT-FILE` up to 10 times, peek one more `READNEXT` for next-page flag, `ENDBR-TRANSACT-FILE` (lines 279-328) |
| Page backward                                  | `PROCESS-PF7-KEY` → `PROCESS-PAGE-BACKWARD`: `STARTBR-TRANSACT-FILE` at `CDEMO-CT00-TRNID-FIRST`, loop `READPREV-TRANSACT-FILE` 10 times (rows 10→1), `ENDBR-TRANSACT-FILE` (lines 234-376) |
| Already at top boundary (page 1)               | `PROCESS-PF7-KEY`: `IF CDEMO-CT00-PAGE-NUM > 1` else MOVE 'You are already at the top of the page...' TO `WS-MESSAGE` (lines 245-252) |
| Already at bottom boundary                     | `PROCESS-PF8-KEY`: `IF NEXT-PAGE-YES` else MOVE 'You are already at the bottom of the page...' TO `WS-MESSAGE` (lines 267-274) |
| Populate transaction row data                  | `POPULATE-TRAN-DATA`: formats `TRAN-AMT` → `WS-TRAN-AMT`, extracts date from `TRAN-ORIG-TS` → `WS-TRAN-DATE`, moves fields to BMS output by `WS-IDX` (lines 381-445) |
| Track first/last Transaction ID on page        | `POPULATE-TRAN-DATA`: `WHEN 1` → MOVE `TRAN-ID` TO `CDEMO-CT00-TRNID-FIRST`; `WHEN 10` → MOVE `TRAN-ID` TO `CDEMO-CT00-TRNID-LAST` (lines 392-393, 438-439) |
| Initialize empty rows                          | `INITIALIZE-TRAN-DATA`: MOVE SPACES to all fields for each row index (lines 450-505)                                 |
| Populate screen header                         | `POPULATE-HEADER-INFO`: FUNCTION CURRENT-DATE, MOVE titles from `COTTL01Y`, format date/time to BMS output (lines 567-586) |
| Send screen (with/without erase)               | `SEND-TRNLST-SCREEN`: `EXEC CICS SEND MAP('COTRN0A') MAPSET('COTRN00')` with `ERASE` or without based on `WS-SEND-ERASE-FLG` (lines 527-549) |
| STARTBR on transaction file                    | `STARTBR-TRANSACT-FILE`: `EXEC CICS STARTBR DATASET(WS-TRANSACT-FILE) RIDFLD(TRAN-ID) KEYLENGTH(LENGTH OF TRAN-ID)` (lines 591-619) |
| READNEXT transaction record                    | `READNEXT-TRANSACT-FILE`: `EXEC CICS READNEXT DATASET(WS-TRANSACT-FILE) INTO(TRAN-RECORD)` (lines 624-653) |
| READPREV transaction record                    | `READPREV-TRANSACT-FILE`: `EXEC CICS READPREV DATASET(WS-TRANSACT-FILE) INTO(TRAN-RECORD)` (lines 658-687) |
| ENDBR (close browse session)                   | `ENDBR-TRANSACT-FILE`: `EXEC CICS ENDBR DATASET(WS-TRANSACT-FILE)` (lines 692-696)                                  |
| Return control to terminal (pseudo-conv)       | `MAIN-PARA`: `EXEC CICS RETURN TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA)` (lines 138-141)                      |
| STARTBR NOTFND error                           | `STARTBR-TRANSACT-FILE`: `WHEN DFHRESP(NOTFND)` → SET `TRANSACT-EOF`, message 'You are at the top of the page...' (lines 605-611) |
| READNEXT ENDFILE                               | `READNEXT-TRANSACT-FILE`: `WHEN DFHRESP(ENDFILE)` → SET `TRANSACT-EOF`, message 'You have reached the bottom of the page...' (lines 639-645) |
| READPREV ENDFILE                               | `READPREV-TRANSACT-FILE`: `WHEN DFHRESP(ENDFILE)` → SET `TRANSACT-EOF`, message 'You have reached the top of the page...' (lines 673-679) |
| Unexpected I/O error                           | All file paragraphs: `WHEN OTHER` → SET `WS-ERR-FLG = 'Y'`, message 'Unable to lookup transaction...' (lines 612-618, 646-652, 680-686) |

### Source File Mapping

| Business Concept               | Source File(s)                                                                           |
|--------------------------------|------------------------------------------------------------------------------------------|
| Transaction List program logic | `app/cbl/COTRN00C.cbl` (700 lines)                                                      |
| Screen layout and field defs   | `app/bms/COTRN00.bms` (465 lines) — mapset COTRN00, map COTRN0A                         |
| Session context structure      | `app/cpy/COCOM01Y.cpy` — CARDDEMO-COMMAREA shared across programs                       |
| Transaction record layout      | `app/cpy/CVTRA05Y.cpy` — TRAN-RECORD (350 bytes)                                        |
| Application title constants    | `app/cpy/COTTL01Y.cpy` — CCDA-TITLE01, CCDA-TITLE02                                     |
| Date/time formatting           | `app/cpy/CSDAT01Y.cpy` — WS-DATE-TIME, WS-TIMESTAMP                                    |
| Common error messages          | `app/cpy/CSMSG01Y.cpy` — CCDA-MSG-INVALID-KEY, CCDA-MSG-THANK-YOU                       |
| Function key constants         | DFHAID (system copybook) — DFHENTER, DFHPF3, DFHPF7, DFHPF8                             |
| BMS attribute constants        | DFHBMSCA (system copybook) — field attribute definitions                                 |
| Downstream program (detail)    | `app/cbl/COTRN01C.cbl` — Transaction Detail View                                        |
| Upstream program (menu)        | `app/cbl/COMEN01C.cbl` — Main Menu; `app/cpy/COMEN02Y.cpy` — menu option table           |
| Upstream program (sign-on)     | `app/cbl/COSGN00C.cbl` — Sign-On / Authentication                                       |

### Migration Considerations

| #  | Consideration                                                                                                                                                                                                  |
|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| M1 | **VSAM Browse to SQL Query**: The STARTBR/READNEXT/READPREV/ENDBR browse pattern should be replaced with SQL SELECT queries using ORDER BY and OFFSET/LIMIT (or keyset pagination using the Transaction ID as cursor). The peek-ahead read for the "next page" flag can be replaced with a COUNT query or by fetching N+1 rows. |
| M2 | **COMMAREA to Session/State Management**: The COMMAREA-based pagination state (first ID, last ID, page number, next-page flag) should be replaced with HTTP session state, JWT claims, URL query parameters, or client-side state management in a modern web application. |
| M3 | **BMS Screen to UI Component**: The fixed 24x80 BMS map (COTRN00.bms) should be replaced with a responsive HTML/CSS table or data grid component. The 10-row fixed page size should become configurable or dynamically determined by viewport size. |
| M4 | **XCTL Navigation to URL Routing**: The XCTL-based program-to-program navigation (COTRN00C → COTRN01C, COTRN00C → COMEN01C) should be replaced with URL routing (e.g., `/transactions`, `/transactions/:id`, `/menu`) or SPA navigation. |
| M5 | **Pseudo-Conversational Pattern to Stateless API**: The CICS pseudo-conversational lifecycle (SEND MAP → RETURN TRANSID → RECEIVE MAP) should be replaced with stateless REST API endpoints. Each page request becomes an independent GET request with pagination parameters. |
| M6 | **3270 Function Keys to UI Controls**: F3/F7/F8 should be mapped to clickable buttons (Back, Previous Page, Next Page). The 'S' selection field should become a clickable row or link. Consider keyboard shortcuts for power users. |
| M7 | **Fixed Record Layout to Dynamic Data Model**: The 350-byte fixed COBOL record (CVTRA05Y) should be replaced with a typed data model (DTO/entity class). Fields not displayed on the list screen (Merchant Name, Card Number, etc.) should still be available in the API response for the detail view. |
| M8 | **Date Formatting**: The manual timestamp-to-date substring extraction (`TRAN-ORIG-TS` → MM/DD/YY) should use standard date parsing and formatting libraries. Consider supporting locale-specific date formats. |
| M9 | **No Authorization in Program**: COTRN00C does not perform its own authorization check — it relies on the upstream menu (COMEN01C) to restrict access by user type. In a modern application, each API endpoint should independently verify authorization (defense in depth). |
| M10 | **Hardcoded Row Population**: The EVALUATE WS-IDX block that maps records to screen rows 1-10 should be replaced with a loop or list iteration in the modern implementation. |

---

## Section 10: Related Use Cases / End-to-End Composition

| Journey Name                        | Use Cases Involved                                    | Business Scenario                                                                                            |
|-------------------------------------|-------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| Transaction Inquiry                 | UC-CC00-001, UC-CM00-001, **UC-CT00-001**, UC-CT01-001 | Agent signs in, navigates to Transaction List, browses/searches transactions, and views detail of a specific transaction |
| Disputed Transaction Investigation  | UC-CC00-001, UC-CM00-001, **UC-CT00-001**, UC-CT01-001, UC-CM00-001 | Agent signs in, searches Transaction List by ID, views transaction detail to confirm dispute details, then returns to menu |
| Full Transaction Lifecycle Review   | UC-CC00-001, UC-CM00-001, UC-CT02-001, **UC-CT00-001**, UC-CT01-001 | Agent adds a new transaction, then navigates to the Transaction List to confirm it appears, and views its detail |
| Account Activity Audit              | UC-CC00-001, UC-CM00-001, UC-CAVW-001, **UC-CT00-001**, UC-CT01-001, UC-CR00-001 | Auditor reviews account details, browses associated transactions in the list, checks individual transaction details, then generates a report |
| End-of-Day Reconciliation           | UC-CC00-001, UC-CM00-001, **UC-CT00-001**, UC-CR00-001 | Supervisor browses the full Transaction List page-by-page to review the day's activity, then generates reports for record-keeping |

> **Note:** Individual use case flows (UC-CC00-001, UC-CM00-001, UC-CT01-001, UC-CT02-001, UC-CAVW-001, UC-CR00-001) must be composed into end-to-end journey documents for complete integration and regression testing. Not all referenced use cases may have their Business Use Case Flow documents created yet.

---

## Document Footer

| Attribute                | Value                                  |
|--------------------------|----------------------------------------|
| **Source Program**       | COTRN00C.cbl (700 lines)              |
| **Transaction ID**       | CT00                                   |
| **Data Source**          | COTRN00C_Flow_Analysis.md + COBOL source verification |
| **Acceptance Criteria**  | 22 (AC-01 through AC-22)              |
| **Business Rules**       | 15 (BR1 through BR15)                 |
| **Validation Rules**     | 3 (V1 through V3)                     |
| **Alternative Flows**    | 10 (AF1 through AF10)                 |

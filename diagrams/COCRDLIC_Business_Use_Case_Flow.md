# COCRDLIC — Credit Card List: Business Use Case Flow Analysis

> **Transaction:** CCLI | **Use Case:** List Credit Cards | **Actor:** Regular User
> **Technical Reference:** [COCRDLIC_Flow_Analysis.md](./COCRDLIC_Flow_Analysis.md)
> **Repository:** `choikh0423/aws-mainframe-modernization-carddemo`

---

## Section 1: Use Case Overview

| Attribute         | Value                                                                                     |
|-------------------|-------------------------------------------------------------------------------------------|
| **Use Case Name** | List Credit Cards                                                                         |
| **Use Case ID**   | UC-CCLI-001                                                                               |
| **Actor(s)**      | Regular User (all authenticated non-admin users)                                          |
| **Business Goal** | Allow a user to browse and search all credit cards in the system, optionally filtering by account number or card number, and select a card to view its details or update it |
| **Preconditions** | 1. User is authenticated (signed in via Login screen) <br> 2. User has "Regular User" role <br> 3. Credit card data exists in the system |
| **Postconditions**| 1. User has viewed a paginated list of credit cards <br> 2. Optionally: user has navigated to Card Detail View or Card Update for a selected card |
| **Trigger**       | User selects option **3 — "Credit Card List"** from the Main Menu                        |
| **Data Access**   | Read-only — this use case does not create, modify, or delete any data                     |
| **Frequency**     | On-demand, during online business hours                                                   |

### Business Context

The Credit Card List screen serves as the **central lookup and navigation hub** for all card-related operations. Before a user can view or update a specific credit card, they must first locate it. This screen provides:

- **Full browse** — scroll through all cards in the system
- **Filtered search** — narrow results by Account Number or Card Number
- **Direct navigation** — select a card to jump to Detail View or Update

```
  ┌─────────────────────────────────────────────────────────┐
  │                BUSINESS PROCESS CONTEXT                  │
  │                                                          │
  │  ┌──────────┐    ┌──────────────┐    ┌──────────────┐   │
  │  │  Account  │    │   Card       │    │ Transaction  │   │
  │  │  Mgmt     │    │   Mgmt       │    │ Processing   │   │
  │  │           │    │              │    │              │   │
  │  │ - View    │    │ - LIST ◄─── THIS USE CASE        │   │
  │  │ - Update  │    │ - View      │    │ - List       │   │
  │  │           │    │ - Update    │    │ - View       │   │
  │  └──────────┘    └──────────────┘    │ - Add        │   │
  │                                       │ - Report     │   │
  │                                       └──────────────┘   │
  └─────────────────────────────────────────────────────────┘
```

---

## Section 2: User Journey — Happy Path

```
  [User]                                           [System]
    │                                                  │
    │  ── Main Menu: Select "3. Credit Card List" ──►  │
    │                                                  │
    │                      ◄── Display card list ───── │
    │                          (page 1, 7 cards/page,  │
    │                           sorted by card number) │
    │                                                  │
    │  ── (Optional) Enter Account Number filter ───►  │
    │     OR Card Number filter                        │
    │     Press Enter                                  │
    │                                                  │
    │                      ◄── Display filtered list ─ │
    │                          (only matching cards)   │
    │                                                  │
    │  ── Press F8 (Forward) to see next page ──────►  │
    │                                                  │
    │                      ◄── Display next 7 cards ── │
    │                          (page number increments)│
    │                                                  │
    │  ── Press F7 (Backward) to see previous page ─►  │
    │                                                  │
    │                      ◄── Display previous cards  │
    │                                                  │
    │  ── Type 'S' next to a card, press Enter ─────►  │
    │     (Select for Detail View)                     │
    │                                                  │
    │                      ◄── Navigate to Card Detail │
    │                          View screen (CCDL)      │
    │                                                  │
    │      ─── OR ───                                  │
    │                                                  │
    │  ── Type 'U' next to a card, press Enter ─────►  │
    │     (Select for Update)                          │
    │                                                  │
    │                      ◄── Navigate to Card Update │
    │                          screen (CCUP)           │
    │                                                  │
    │  ── Press F3 to exit ─────────────────────────►  │
    │                                                  │
    │                      ◄── Return to Main Menu ─── │
    │                                                  │
```

### Step-by-Step Narrative

| Step | Actor  | Action                                                 | System Response                                              |
|------|--------|--------------------------------------------------------|--------------------------------------------------------------|
| 1    | User   | Selects "Credit Card List" (option 3) from Main Menu   | System transfers to the Credit Card List screen              |
| 2    | System | —                                                      | Reads card database from the beginning, displays first 7 cards sorted by card number. Shows page number, action hint message |
| 3    | User   | (Optional) Enters an Account Number in the filter field | System filters the list to show only cards belonging to that account |
| 4    | User   | (Optional) Enters a Card Number in the filter field     | System filters the list to show only that specific card      |
| 5    | User   | Presses Enter to apply filter                           | System re-reads the card database with the filter applied    |
| 6    | User   | Presses F8 (Forward) to page down                       | System reads the next 7 matching cards and displays them. Page number increments |
| 7    | User   | Presses F7 (Backward) to page up                        | System reads the previous 7 matching cards. Page number decrements |
| 8    | User   | Types 'S' in the Select column next to a card row       | System navigates to the **Card Detail View** screen, passing the selected card's account and card number |
| 9    | User   | Types 'U' in the Select column next to a card row       | System navigates to the **Card Update** screen, passing the selected card's account and card number |
| 10   | User   | Presses F3 (Exit)                                       | System returns to the Main Menu                              |

---

## Section 3: Business Rules & Validations

### Input Validation Rules

| #  | Field             | Rule                                           | Error Message Shown to User                        |
|----|-------------------|------------------------------------------------|----------------------------------------------------|
| V1 | Account Number    | Must be exactly 11 numeric digits if provided  | *(input error — account filter invalid)*           |
| V2 | Card Number       | Must be exactly 16 numeric digits if provided  | *(input error — card filter invalid)*              |
| V3 | Selection Code    | Must be 'S' (view), 'U' (update), or blank     | `INVALID ACTION CODE`                              |
| V4 | Selection Count   | Only one row may be selected at a time          | `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE`  |
| V5 | Account Number    | Both filters may be blank (show all cards)      | *(no error — full list displayed)*                 |

### Business Rules

| #   | Rule                                                                                          |
|-----|-----------------------------------------------------------------------------------------------|
| BR1 | **Read-only access**: This screen does not modify any card data. It is purely a lookup/browse function |
| BR2 | **Page size is 7 records**: Each page displays a maximum of 7 credit card rows               |
| BR3 | **Sort order is by Card Number**: Cards are listed in ascending card number order (primary key) |
| BR4 | **Filters are AND-combined**: If both Account Number and Card Number filters are provided, both must match |
| BR5 | **Filter persistence**: Filter values are preserved across pagination (F7/F8). Changing the filter and pressing Enter resets to page 1 |
| BR6 | **Empty result handling**: If no cards match the filter criteria, the system displays `NO RECORDS FOUND FOR THIS SEARCH CONDITION` |
| BR7 | **Selection passes context**: When a card is selected (S or U), the card's Account Number and Card Number are passed to the next screen so it can load the correct record |
| BR8 | **Invalid keys are treated as Enter**: Pressing any function key other than Enter, F3, F7, or F8 is treated the same as pressing Enter (refresh the current page) |
| BR9 | **Page boundary detection**: The system determines whether more pages exist after the current page and only allows forward paging (F8) when more records are available |

### Decision Table — User Actions

```
┌────────────────────┬──────────────────────────────────────────────────┐
│ User Action        │ System Behavior                                  │
├────────────────────┼──────────────────────────────────────────────────┤
│ Enter (no select)  │ Refresh/re-read current page with current filter│
│ Enter + 'S' on row │ Navigate to Card Detail View (CCDL)             │
│ Enter + 'U' on row │ Navigate to Card Update (CCUP)                  │
│ F3                 │ Exit to Main Menu                                │
│ F7 (on page 1)    │ Re-display page 1 (no backward movement)        │
│ F7 (on page 2+)   │ Navigate to previous page                       │
│ F8 (more pages)   │ Navigate to next page                           │
│ F8 (last page)    │ No action (stay on current page)                │
│ Any other key      │ Treated as Enter (refresh)                      │
└────────────────────┴──────────────────────────────────────────────────┘
```

---

## Section 4: Data Entities Involved

### Entity Descriptions

| Entity              | Description                                                                 | Role in This Use Case |
|---------------------|-----------------------------------------------------------------------------|----------------------|
| **Credit Card**     | A physical credit card with a unique 16-digit card number, linked to one account. Has an embossed name, expiration date, CVV, and active/inactive status | Primary entity — browsed and displayed |
| **Account**         | A credit card account identified by an 11-digit account number. One account can have multiple cards | Filter criterion — used to narrow card list to a specific account |
| **Card Cross-Reference** | Links customers, cards, and accounts together | Not directly accessed by this screen, but used by downstream screens |

### Entity Relationships

```
  [Customer] 1────────* [Credit Card] *────────1 [Account]
                              │
                              │ displayed on this screen:
                              │   - Card Number (16 digits)
                              │   - Account Number (11 digits)
                              │   - Active Status (Y/N)
                              │
                         ┌────┴────┐
                         │ Actions │
                         ├─────────┤
                         │ S: View │──── ► Card Detail View (CCDL)
                         │ U: Edit │──── ► Card Update (CCUP)
                         └─────────┘
```

### Data Fields Displayed

| # | Field           | Format         | Source             | Description                               |
|---|-----------------|----------------|--------------------|-------------------------------------------|
| 1 | Account Number  | 11 numeric     | CARD-ACCT-ID       | The account this card is linked to         |
| 2 | Card Number     | 16 numeric     | CARD-NUM            | Unique identifier of the credit card       |
| 3 | Active Status   | 1 character    | CARD-ACTIVE-STATUS  | Whether the card is currently active       |

**Note:** The underlying card record also contains CVV Code, Embossed Name, and Expiration Date, but these are **not displayed** on the list screen — they are only visible on the Card Detail View screen.

---

## Section 5: Screen / Interface Description

### Screen Layout

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Tran: CCLI    AWS Mainframe Modernization                    Date: 05/07/26 │
│ Prog: COCRDLIC            CardDemo                           Time: 14:30:22 │
│                                                                              │
│                            List Credit Cards                      Page 1     │
│                                                                              │
│                   Account Number    : [___________]                          │
│                   Credit Card Number: [________________]                     │
│                                                                              │
│       Select     Account Number   Card Number        Active                 │
│       ------     ---------------  ---------------    --------               │
│         [_]      00000000001      4111111111111111       Y                  │
│         [_]      00000000001      4222222222222222       Y                  │
│         [_]      00000000002      4333333333333333       N                  │
│         [_]      00000000002      4444444444444444       Y                  │
│         [_]      00000000003      4555555555555555       Y                  │
│         [_]      00000000003      4666666666666666       Y                  │
│         [_]      00000000004      4777777777777777       N                  │
│                                                                              │
│                                                                              │
│              TYPE S FOR DETAIL, U TO UPDATE ANY RECORD                       │
│                                                                              │
│                                                                              │
│ [error or info message area]                                                 │
│   F3=Exit F7=Backward  F8=Forward                                           │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Interface Elements

| Element              | Type         | Description                                         |
|----------------------|--------------|-----------------------------------------------------|
| Account Number       | Input field  | Optional filter — enter an 11-digit account number to show only cards for that account |
| Credit Card Number   | Input field  | Optional filter — enter a 16-digit card number to find a specific card |
| Select (per row)     | Input field  | Enter 'S' to view card details or 'U' to update the card |
| Account Number (row) | Display only | Shows the account linked to each card                |
| Card Number (row)    | Display only | Shows the credit card number                         |
| Active (row)         | Display only | Shows whether the card is active                     |
| Page number          | Display only | Current page number in the browse results            |
| Info message         | Display only | Instructional text (e.g., "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD") |
| Error message        | Display only | Validation error or system error messages (red text) |

### Available Actions

| Action       | How to Invoke  | Description                                           |
|--------------|----------------|-------------------------------------------------------|
| **Search**   | Enter filter + Enter key | Filter the card list by account or card number |
| **View Card**| Type 'S' + Enter | Open the selected card's detail view (read-only)   |
| **Edit Card**| Type 'U' + Enter | Open the selected card for editing                 |
| **Next Page**| F8             | Show the next page of results                        |
| **Prev Page**| F7             | Show the previous page of results                    |
| **Exit**     | F3             | Return to the Main Menu                              |

---

## Section 6: Alternative & Error Flows

### Alternative Flow 1: No Records Found

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User enters an Account Number or Card Number filter that has no matches  |
| 2    | System searches the card database and finds zero matching records        |
| 3    | System displays an empty list with the message: `NO RECORDS FOUND FOR THIS SEARCH CONDITION` |
| 4    | User can modify the filter and try again, or press F3 to exit           |

### Alternative Flow 2: Invalid Filter Value

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User enters a non-numeric value in Account Number or Card Number field   |
| 2    | System validates the input and detects the format error                  |
| 3    | System displays an error message and highlights the invalid field        |
| 4    | User corrects the input and presses Enter again                         |

### Alternative Flow 3: Multiple Selections

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User types 'S' or 'U' next to more than one card row                    |
| 2    | System detects that more than one row is selected                        |
| 3    | System displays: `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE`       |
| 4    | User clears extra selections and retries with only one                   |

### Alternative Flow 4: Invalid Selection Code

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User types a character other than 'S', 'U', or blank in the Select field |
| 2    | System detects the invalid action code                                   |
| 3    | System displays: `INVALID ACTION CODE`                                   |
| 4    | User corrects the selection code and presses Enter                       |

### Alternative Flow 5: Page Boundary — Already on First Page

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User presses F7 (Backward) while on page 1                              |
| 2    | System detects that this is already the first page                       |
| 3    | System re-displays page 1 (no error message, same data)                 |

### Alternative Flow 6: Page Boundary — Already on Last Page

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | User presses F8 (Forward) while on the last page                         |
| 2    | System detects that no more records exist beyond the current page        |
| 3    | System stays on the current page (no navigation occurs)                 |

### Error Flow: System I/O Error

| Step | Description                                                              |
|------|--------------------------------------------------------------------------|
| 1    | A system error occurs while reading the card database                    |
| 2    | System captures the error details (operation, file, response codes)      |
| 3    | System displays a technical error message in the error area              |
| 4    | User can press F3 to exit or retry with Enter                           |

---

## Section 7: Business Process Context — Where This Use Case Fits

### Navigation Context

```
                        ┌───────────────┐
                        │    Login      │
                        │   (CC00)      │
                        └───────┬───────┘
                                │
                                ▼
                        ┌───────────────┐
                        │  Main Menu    │
                        │   (CM00)      │
                        └───────┬───────┘
                                │
              ┌─────────────────┼─────────────────────────┐
              │                 │                          │
              ▼                 ▼                          ▼
     ┌──────────────┐  ┌───────────────┐         ┌──────────────┐
     │ Account Mgmt │  │ Card Mgmt     │         │ Transactions │
     │              │  │               │         │              │
     │ - View (CAVW)│  │ ► LIST (CCLI) │ ◄─THIS  │ - List (CT00)│
     │ - Update     │  │ - View (CCDL) │         │ - View (CT01)│
     │   (CAUP)     │  │ - Update      │         │ - Add  (CT02)│
     └──────────────┘  │   (CCUP)      │         │ - Report     │
                        └───────────────┘         │   (CR00)     │
                                                   │ - Bill Pay   │
                                                   │   (CB00)     │
                                                   └──────────────┘
```

### Downstream Use Cases (Where the User Goes From Here)

| Destination              | Trigger                | What Happens Next                                     |
|--------------------------|------------------------|-------------------------------------------------------|
| **Card Detail View (CCDL)** | User types 'S' on a row | The selected card's full details are displayed (read-only): card number, account, CVV, embossed name, expiration date, active status |
| **Card Update (CCUP)**      | User types 'U' on a row | The selected card's details are displayed in an editable form. User can modify card attributes and save |
| **Main Menu (CM00)**        | User presses F3        | User returns to the main navigation menu               |

### Upstream Use Cases (How the User Gets Here)

| Source              | Navigation Path                                           |
|---------------------|-----------------------------------------------------------|
| **Main Menu (CM00)**| User selects option 3 — "Credit Card List"                |
| **Card Detail View (CCDL)** | User presses F3 from Card Detail, returns to Card List |
| **Card Update (CCUP)**      | User presses F3 from Card Update, returns to Card List |

### End-to-End Journey Examples

These composite journeys show how this use case participates in larger business scenarios:

**Journey 1: "Find and Review a Credit Card"**
```
  Login ──► Main Menu ──► Credit Card List ──► Card Detail View ──► (back) ──► Main Menu
                          ^^^^^^^^^^^^^^^^^^^^
                          THIS USE CASE
```

**Journey 2: "Update a Credit Card"**
```
  Login ──► Main Menu ──► Credit Card List ──► Card Update ──► (save) ──► (back) ──► Main Menu
                          ^^^^^^^^^^^^^^^^^^^^
                          THIS USE CASE
```

**Journey 3: "Search for Cards by Account"**
```
  Login ──► Main Menu ──► Credit Card List ──► [enter account filter] ──► browse pages ──► Exit
                          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                          THIS USE CASE (entirely within)
```

---

## Section 8: Acceptance Criteria

These criteria define the expected behavior for a migrated/modernized version of this use case.

### Core Functionality

| ID    | Acceptance Criterion                                                                                    |
|-------|---------------------------------------------------------------------------------------------------------|
| AC-01 | **Given** the user selects "Credit Card List" from the menu, **When** the screen loads, **Then** the system displays the first page of credit cards sorted by card number, showing up to 7 cards per page |
| AC-02 | **Given** there are more than 7 cards in the system, **When** the user presses F8 (Forward), **Then** the next 7 cards are displayed and the page number increments by 1 |
| AC-03 | **Given** the user is not on the first page, **When** the user presses F7 (Backward), **Then** the previous 7 cards are displayed and the page number decrements by 1 |
| AC-04 | **Given** the user is on page 1, **When** the user presses F7 (Backward), **Then** the system re-displays page 1 without error |

### Filtering

| ID    | Acceptance Criterion                                                                                    |
|-------|---------------------------------------------------------------------------------------------------------|
| AC-05 | **Given** the user enters a valid 11-digit Account Number, **When** they press Enter, **Then** only cards linked to that account are displayed |
| AC-06 | **Given** the user enters a valid 16-digit Card Number, **When** they press Enter, **Then** only that specific card is displayed (if it exists) |
| AC-07 | **Given** no filter is entered, **When** the user presses Enter, **Then** all cards in the system are displayed |
| AC-08 | **Given** the user enters a filter that matches zero records, **When** they press Enter, **Then** the message `NO RECORDS FOUND FOR THIS SEARCH CONDITION` is displayed |

### Input Validation

| ID    | Acceptance Criterion                                                                                    |
|-------|---------------------------------------------------------------------------------------------------------|
| AC-09 | **Given** the user enters a non-numeric Account Number, **When** they press Enter, **Then** an error is displayed and the field is highlighted |
| AC-10 | **Given** the user enters a non-numeric Card Number, **When** they press Enter, **Then** an error is displayed and the field is highlighted |
| AC-11 | **Given** the user selects more than one row (multiple S or U), **When** they press Enter, **Then** the error `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE` is displayed |
| AC-12 | **Given** the user enters an invalid selection code (not S, U, or blank), **When** they press Enter, **Then** the error `INVALID ACTION CODE` is displayed |

### Navigation

| ID    | Acceptance Criterion                                                                                    |
|-------|---------------------------------------------------------------------------------------------------------|
| AC-13 | **Given** the user types 'S' next to a card row and presses Enter, **Then** the system navigates to the Card Detail View screen with the selected card's Account Number and Card Number pre-loaded |
| AC-14 | **Given** the user types 'U' next to a card row and presses Enter, **Then** the system navigates to the Card Update screen with the selected card's Account Number and Card Number pre-loaded |
| AC-15 | **Given** the user presses F3, **Then** the system returns to the Main Menu |

### Data Integrity

| ID    | Acceptance Criterion                                                                                    |
|-------|---------------------------------------------------------------------------------------------------------|
| AC-16 | This screen performs **read-only** access. No card records are created, modified, or deleted by this use case |
| AC-17 | Filter values entered by the user are **preserved** across pagination (F7/F8 do not clear filters) |

---

## Section 9: Technical Cross-Reference (Traceability)

This section maps every business concept back to the technical implementation for migration engineers.

### Business Step → Technical Implementation

| Business Step                        | Technical Reference                                                      |
|--------------------------------------|--------------------------------------------------------------------------|
| Display card list                    | `EXEC CICS SEND MAP('CCRDLIA') MAPSET('COCRDLI')` — BMS screen send     |
| Read cards from database             | `EXEC CICS STARTBR DATASET('CARDDAT')` + `READNEXT` loop — VSAM KSDS browse |
| Page forward                         | `READNEXT` from last card key on current page — paragraph `9000-READ-FORWARD` |
| Page backward                        | `STARTBR` at first card key + `READPREV` loop — paragraph `9100-READ-BACKWARDS` |
| Filter by Account Number             | In-memory filter in `9500-FILTER-RECORDS`: compare `CARD-ACCT-ID` to `CC-ACCT-ID` |
| Filter by Card Number                | In-memory filter in `9500-FILTER-RECORDS`: compare `CARD-NUM` to `CC-CARD-NUM-N` |
| Navigate to Card Detail View         | `EXEC CICS XCTL PROGRAM('COCRDSLC') COMMAREA(CARDDEMO-COMMAREA)` |
| Navigate to Card Update              | `EXEC CICS XCTL PROGRAM('COCRDUPC') COMMAREA(CARDDEMO-COMMAREA)` |
| Return to Main Menu                  | `EXEC CICS XCTL PROGRAM('COMEN01C') COMMAREA(CARDDEMO-COMMAREA)` |
| Validate Account Number is numeric   | Paragraph `2210-EDIT-ACCOUNT` — COBOL numeric check                      |
| Validate Card Number is numeric      | Paragraph `2220-EDIT-CARD` — COBOL numeric check                         |
| Validate selection codes             | Paragraph `2250-EDIT-ARRAY` — check for 'S', 'U', blank, count selections |
| Detect "more pages"                  | Peek-ahead `READNEXT` after 7 rows in `9000-READ-FORWARD` to check for additional records |
| Preserve state across interactions   | `RETURN TRANSID('CCLI') COMMAREA(...)` — pseudo-conversational pattern with COMMAREA |
| Pass selected card to next screen    | `CDEMO-ACCT-ID` and `CDEMO-CARD-NUM` fields in shared COMMAREA          |

### Source File Mapping

| Business Concept        | Source File(s)                                |
|-------------------------|-----------------------------------------------|
| Screen layout           | `app/bms/COCRDLI.bms`                         |
| Business logic          | `app/cbl/COCRDLIC.cbl` (1,460 lines)          |
| Card record structure   | `app/cpy/CVACT02Y.cpy` (CARD-RECORD, 150 bytes) |
| Shared session state    | `app/cpy/COCOM01Y.cpy` (CARDDEMO-COMMAREA)    |
| Work area & navigation  | `app/cpy/CVCRD01Y.cpy` (CC-WORK-AREAS)        |
| PF key mapping          | `app/cpy/CSSTRPFY.cpy` (YYYY-STORE-PFKEY)     |
| Downstream: Detail View | `app/cbl/COCRDSLC.cbl`                         |
| Downstream: Card Update | `app/cbl/COCRDUPC.cbl`                         |

### Migration Considerations

| #  | Consideration                                                                                          |
|----|--------------------------------------------------------------------------------------------------------|
| M1 | **Pagination**: The current implementation uses VSAM key-based cursor positioning (STARTBR/READNEXT/READPREV). A modern system would use SQL `LIMIT/OFFSET` or cursor-based pagination with `ORDER BY card_number` |
| M2 | **Filtering**: Currently done in-memory after reading every record sequentially. A modern system would use SQL `WHERE account_id = ?` for server-side filtering — significant performance improvement for large datasets |
| M3 | **Page size**: Hardcoded to 7 rows (constrained by 3270 terminal height of 24 rows). A modern UI should support configurable page sizes and responsive layouts |
| M4 | **Selection pattern**: The 'S'/'U' inline selection codes are a 3270 terminal pattern. A modern UI would use clickable links, buttons, or row-click actions |
| M5 | **State management**: Uses CICS COMMAREA (pseudo-conversational). A modern system would use HTTP sessions, JWT tokens, or client-side state management |
| M6 | **Navigation**: Uses CICS XCTL (permanent program transfer). A modern system would use client-side routing or API endpoint calls |
| M7 | **Real-time data**: The current browse reads data at point-in-time. Consider whether the modern version needs real-time refresh or caching strategies |

---

## Section 10: Related Use Cases — Composition for End-to-End Journeys

This use case is a **navigation hub** in the Card Management domain. To validate the full end-to-end business flow during migration, it must be tested in combination with:

| Journey Name               | Use Cases Involved (in order)                         | Business Scenario                                |
|----------------------------|-------------------------------------------------------|--------------------------------------------------|
| Card Lookup & Review       | Login → Menu → **Card List** → Card Detail View → Menu | Customer service rep looks up a card to answer a caller's question |
| Card Data Update           | Login → Menu → **Card List** → Card Update → Menu      | Operations team updates card attributes (e.g., disable a lost card) |
| Full Card Lifecycle        | Login → Menu → Account View → **Card List** → Card Detail → Card Update → Menu | Complete review and modification of a card within an account context |
| Filtered Search            | Login → Menu → **Card List** (with Account filter) → browse pages → Exit | Auditor searches for all cards under a specific account |

**To build these end-to-end journey documents, combine the individual Business Use Case Flows for each participating transaction (CC00, CM00, CCLI, CCDL, CCUP, CAVW) into a single narrative.**

---

*Business Use Case Flow generated from Technical Flow Analysis: COCRDLIC_Flow_Analysis.md*
*Source program: COCRDLIC.cbl (1,460 lines) | Transaction: CCLI | Data: CARDDAT (VSAM KSDS)*
*Acceptance Criteria: 17 | Business Rules: 9 | Validation Rules: 5 | Alternative Flows: 7*

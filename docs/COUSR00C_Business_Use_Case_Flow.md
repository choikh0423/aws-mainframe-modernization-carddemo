# CU00 — User List (Security/Admin) Business Use Case Flow

## Section 1: Use Case Overview

| Attribute        | Value                                                                 |
|------------------|-----------------------------------------------------------------------|
| **Use Case Name**    | View and Navigate User List                                       |
| **Use Case ID**      | UC-CU00-001                                                       |
| **Actor(s)**         | System Administrator                                              |
| **Business Goal**    | Browse and locate user accounts for update or deletion            |
| **Preconditions**    | Administrator is authenticated and has navigated to the Admin Menu |
| **Postconditions**   | Administrator has viewed user list and optionally selected a user for update or delete |
| **Trigger**          | Administrator selects "User List" (Option 1) from the Admin Menu  |
| **Data Access**      | Read-only (browse only; no create, update, or delete occurs here) |
| **Frequency**        | Multiple times per day — any time an administrator needs to manage user accounts |

### Business Context

The User List screen is the central hub for user account management in the CardDemo application. It provides system administrators with a paginated, searchable view of all registered users. From this list, administrators can select individual users to update their profile or delete their account. The screen supports forward and backward navigation through the full user directory, with the ability to jump to a specific position using a User ID search.

This use case is the first step in any user account management workflow — administrators must locate the target user here before they can perform any maintenance action.

```
┌─────────────────────────────────────────────────────────────┐
│                   CardDemo Application                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐     ┌──────────────────────────────────┐  │
│  │  User Auth   │     │     Transaction Processing       │  │
│  │  & Security  │     │  (Accounts, Cards, Transactions) │  │
│  └──────┬───────┘     └──────────────────────────────────┘  │
│         │                                                    │
│  ┌──────┴───────────────────────────────┐                   │
│  │  *** USER MANAGEMENT (Admin) ***      │                   │
│  │  ┌────────────┐  ┌───────────────┐   │                   │
│  │  │ User List  │  │ User Add      │   │                   │
│  │  │ (THIS UC)  │  │ (COUSR01C)    │   │                   │
│  │  └─────┬──────┘  └───────────────┘   │                   │
│  │        │                              │                   │
│  │  ┌─────┴──────┐  ┌───────────────┐   │                   │
│  │  │User Update │  │ User Delete   │   │                   │
│  │  │(COUSR02C)  │  │ (COUSR03C)    │   │                   │
│  │  └────────────┘  └───────────────┘   │                   │
│  └───────────────────────────────────────┘                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Section 2: User Journey (Happy Path)

### Sequence Diagram

```
┌──────────────┐                          ┌──────────────┐
│Administrator │                          │    System    │
└──────┬───────┘                          └──────┬───────┘
       │                                         │
       │  Select "User List" from Admin Menu     │
       │────────────────────────────────────────>│
       │                                         │
       │  Display first page of users (10 max)   │
       │<────────────────────────────────────────│
       │                                         │
       │  [Optional] Enter User ID in search     │
       │  and press Enter                        │
       │────────────────────────────────────────>│
       │                                         │
       │  Display users starting from that ID    │
       │<────────────────────────────────────────│
       │                                         │
       │  [Optional] Press Forward to see more   │
       │────────────────────────────────────────>│
       │                                         │
       │  Display next page of users             │
       │<────────────────────────────────────────│
       │                                         │
       │  Type 'U' next to a user and press Enter│
       │────────────────────────────────────────>│
       │                                         │
       │  Navigate to User Update screen         │
       │<────────────────────────────────────────│
       │                                         │
└──────┴───────┘                          └──────┴───────┘
```

### Step-by-Step Narrative

| Step | Actor          | Action                                          | System Response                                                        |
|------|----------------|-------------------------------------------------|------------------------------------------------------------------------|
| 1    | Administrator  | Selects Option 1 (User List) from Admin Menu    | Loads the User List screen; retrieves first page of users from the user security store, sorted alphabetically by User ID |
| 2    | System         | —                                               | Displays up to 10 user records showing User ID, First Name, Last Name, and User Type; shows page number as 1 |
| 3    | Administrator  | Reviews the displayed list of users             | Screen remains displayed, awaiting input                               |
| 4    | Administrator  | (Optional) Types a User ID in the search field and presses Enter | Repositions the list to begin at or after the specified User ID; displays the matching page of results |
| 5    | Administrator  | (Optional) Presses Forward to see more users    | Displays the next 10 users; increments the page number                 |
| 6    | Administrator  | (Optional) Presses Backward to see prior users  | Displays the previous 10 users; decrements the page number             |
| 7    | Administrator  | Types 'U' or 'D' in the selection column next to a user and presses Enter | System navigates to the Update User screen (for 'U') or Delete User screen (for 'D'), passing the selected User ID |
| 8    | Administrator  | (Alternative) Presses Back to return to Admin Menu | System returns to the Admin Menu screen                              |

---

## Section 3: Business Rules & Validations

### Input Validation Rules

| #  | Field          | Rule                                                    | Error Message Shown to User                             |
|----|----------------|---------------------------------------------------------|---------------------------------------------------------|
| V1 | Selection Code | Must be 'U', 'u', 'D', or 'd' when entered            | `Invalid selection. Valid values are U and D`           |
| V2 | Search User ID | Optional; if blank, list starts from the beginning      | *(no error — treated as "show all from start")*         |
| V3 | Search User ID | Maximum 8 characters (field length enforced by screen)  | *(field physically limited to 8 characters)*            |
| V4 | Function Keys  | Only Enter, F3, F7, and F8 are valid                   | `Invalid key pressed. Please see below...`             |

### Business Rules

| #   | Rule                                                                                                   |
|-----|--------------------------------------------------------------------------------------------------------|
| BR1 | **Page Size**: The system displays exactly 10 user records per page. If fewer than 10 records remain, only available records are shown. |
| BR2 | **Sort Order**: Users are listed in ascending alphabetical order by User ID.                           |
| BR3 | **Selection Priority**: The system processes the first non-empty selection field found (scanning top to bottom, rows 1 through 10). Only one selection per screen submission is processed. |
| BR4 | **Case-Insensitive Selection**: Both uppercase ('U', 'D') and lowercase ('u', 'd') selection codes are accepted as valid. |
| BR5 | **Search Positioning**: When a User ID is entered in the search field, the list repositions to display records starting at or after that User ID in sort order. |
| BR6 | **Page Boundary — Top**: If the user attempts to page backward when already on page 1, the system displays an informational message and does not change the display. |
| BR7 | **Page Boundary — Bottom**: If the user attempts to page forward when no more records exist, the system displays an informational message and does not change the display. |
| BR8 | **Next Page Indicator**: The system reads one extra record beyond the current page to determine whether additional pages exist. This controls whether the Forward action is available. |
| BR9 | **Read-Only Access**: This screen only retrieves and displays user data. No user records are created, modified, or deleted from this screen. |
| BR10 | **Authentication Prerequisite**: Access to this screen requires prior successful sign-on as an administrator through the application sign-on process. |
| BR11 | **Session Continuity**: When the user returns from the Update or Delete screens, the list screen re-displays from the beginning (page context resets). |

### Decision Table

```
┌─────────────────────────────────────┬─────────────────────────────────────────────────┐
│ User Action                         │ System Response                                  │
├─────────────────────────────────────┼─────────────────────────────────────────────────┤
│ Enter (no selection, no search)     │ Refresh list from beginning of user directory    │
│ Enter (no selection, search filled) │ Reposition list starting at/after search ID      │
│ Enter (selection = 'U' or 'u')      │ Navigate to Update User screen for selected user │
│ Enter (selection = 'D' or 'd')      │ Navigate to Delete User screen for selected user │
│ Enter (selection = other value)     │ Display validation error, stay on list           │
│ F3 (Back)                           │ Return to Admin Menu                             │
│ F7 (Backward) on page > 1          │ Display previous page of users                   │
│ F7 (Backward) on page 1            │ Display "already at top" message, stay on page   │
│ F8 (Forward) with more pages       │ Display next page of users                       │
│ F8 (Forward) on last page          │ Display "already at bottom" message, stay on page│
│ Any other key                       │ Display "invalid key" error message              │
└─────────────────────────────────────┴─────────────────────────────────────────────────┘
```

---

## Section 4: Data Entities Involved

### Entity Descriptions

| Entity                | Description                                                        | Role in This Use Case                  |
|-----------------------|--------------------------------------------------------------------|----------------------------------------|
| User Security Records | Contains all registered user accounts including credentials and roles | Primary data source — browsed to populate the user list display |

### Entity Relationships

```
┌───────────────────────┐
│  User Security Record │
│  (one per user)       │
├───────────────────────┤
│  User ID (key)        │
│  First Name           │
│  Last Name            │
│  Password             │
│  User Type            │
└───────────────────────┘
         │
         │ 1:1
         │
┌────────┴──────────────┐
│  Sign-on Session      │
│  (authenticated user) │
└───────────────────────┘

Note: Each User Security Record represents one system user.
There is no parent-child relationship within this entity —
the User List displays a flat collection of all records.
```

### Data Fields Displayed

| #  | Field       | Format               | Source                    | Description                                    |
|----|-------------|----------------------|---------------------------|------------------------------------------------|
| 1  | User ID     | 8 characters (alpha) | User Security Record, key field | Unique identifier for the user account        |
| 2  | First Name  | 20 characters        | User Security Record      | User's first name                              |
| 3  | Last Name   | 20 characters        | User Security Record      | User's last name                               |
| 4  | User Type   | 1 character          | User Security Record      | Role indicator: 'A' = Administrator, 'U' = Regular User |

**Fields NOT displayed on this screen (but present in the underlying record):**

| # | Field    | Format          | Reason Not Displayed                                            |
|---|----------|-----------------|------------------------------------------------------------------|
| 1 | Password | 8 characters    | Security-sensitive; not shown on list view for privacy protection |
| 2 | Filler   | 23 characters   | Reserved space; not user-facing data                             |

---

## Section 5: Screen / Interface Description

### Screen Layout

```
┌────────────────────────────────────────────────────────────────────────────────┐
│Tran: CU00    AWS Mainframe Modernization            Date: 05/07/26            │
│Prog: COUSR00C              CardDemo                 Time: 14:30:22            │
│                                                                                │
│                                  List Users                   Page: 00000001   │
│                                                                                │
│    Search User ID: ADMIN___                                                    │
│                                                                                │
│    Sel  User ID       First Name            Last Name             Type         │
│    ---  --------  --------------------  --------------------  ----             │
│     _   ADMIN001  Adriana               Martinez              A               │
│     _   ADMIN002  Benjamin              O'Sullivan            A               │
│     _   USER0001  Charlotte             Nakamura              U               │
│     _   USER0002  David                 Johansson             U               │
│     _   USER0003  Eleanor               Petrov                U               │
│     _   USER0004  Frederick             Al-Hassan             U               │
│     _   USER0005  Gabrielle             Yamamoto              U               │
│     _   USER0006  Harrison              Fernandez             U               │
│     _   USER0007  Isabella              Kowalski              U               │
│     _   USER0008  James                 Patel                 U               │
│                                                                                │
│           Type 'U' to Update or 'D' to Delete a User from the list            │
│                                                                                │
│                                                                                │
│ENTER=Continue  F3=Back  F7=Backward  F8=Forward                               │
└────────────────────────────────────────────────────────────────────────────────┘
```

### Interface Elements

| Element         | Type    | Description                                                      |
|-----------------|---------|------------------------------------------------------------------|
| Search User ID  | Input   | 8-character field to enter a User ID for search/filter positioning |
| Selection (Sel) | Input   | 1-character field per row (10 rows); accepts 'U' or 'D'          |
| User ID         | Display | Shows the unique User ID for each listed user                    |
| First Name      | Display | Shows the user's first name                                      |
| Last Name       | Display | Shows the user's last name                                       |
| User Type       | Display | Shows the role type ('A' for Admin, 'U' for User)               |
| Page Number     | Display | Shows current page number in the paginated list                  |
| Transaction ID  | Display | Shows 'CU00' — the current transaction identifier               |
| Program Name    | Display | Shows 'COUSR00C' — the current program name                     |
| Date            | Display | Shows current system date in MM/DD/YY format                     |
| Time            | Display | Shows current system time in HH:MM:SS format                     |
| Message Area    | Display | Shows error or status messages (row 23, red, bright)             |

### Available Actions

| Action            | How to Invoke                        | Description                                          |
|-------------------|--------------------------------------|------------------------------------------------------|
| Search/Filter     | Type User ID in search field + Enter | Reposition list to start at the specified User ID    |
| Continue/Refresh  | Press Enter (no selection)           | Refresh the list display                             |
| Select for Update | Type 'U' next to a user + Enter     | Navigate to the User Update screen for that user     |
| Select for Delete | Type 'D' next to a user + Enter     | Navigate to the User Delete screen for that user     |
| Page Forward      | Press F8                             | Display the next page of users                       |
| Page Backward     | Press F7                             | Display the previous page of users                   |
| Return to Menu    | Press F3                             | Return to the Admin Menu                             |

---

## Section 6: Alternative & Error Flows

### AF1: No Users Found (Search Returns No Results)

| Step | Description                                                                  |
|------|------------------------------------------------------------------------------|
| 1    | Administrator enters a User ID in the search field that does not exist       |
| 2    | System attempts to position to the specified User ID                         |
| 3    | No record is found at or after the search position                           |
| 4    | System displays the message: `You are at the top of the page...`            |
| 5    | The list area remains empty; cursor returns to the search field              |

### AF2: Invalid Selection Code Entered

| Step | Description                                                                  |
|------|------------------------------------------------------------------------------|
| 1    | Administrator types a character other than 'U', 'u', 'D', or 'd' in a selection field |
| 2    | Administrator presses Enter                                                  |
| 3    | System displays the message: `Invalid selection. Valid values are U and D`  |
| 4    | The current list page remains displayed; cursor returns to the search field  |

### AF3: Page Forward at End of List

| Step | Description                                                                  |
|------|------------------------------------------------------------------------------|
| 1    | Administrator is on the last page of the user list (no more records exist)   |
| 2    | Administrator presses F8 (Forward)                                           |
| 3    | System displays the message: `You are already at the bottom of the page...` |
| 4    | The current page remains displayed unchanged                                 |

### AF4: Page Backward at Beginning of List

| Step | Description                                                                  |
|------|------------------------------------------------------------------------------|
| 1    | Administrator is on page 1 of the user list                                  |
| 2    | Administrator presses F7 (Backward)                                          |
| 3    | System displays the message: `You are already at the top of the page...`    |
| 4    | The current page remains displayed unchanged                                 |

### AF5: Invalid Function Key Pressed

| Step | Description                                                                  |
|------|------------------------------------------------------------------------------|
| 1    | Administrator presses any key other than Enter, F3, F7, or F8                |
| 2    | System displays the message: `Invalid key pressed. Please see below...`     |
| 3    | The current page remains displayed unchanged; cursor returns to search field |

### AF6: System Error During User Lookup

| Step | Description                                                                  |
|------|------------------------------------------------------------------------------|
| 1    | Administrator initiates any action (Enter, F7, F8) that requires reading users |
| 2    | System encounters an unexpected error while reading the user security store   |
| 3    | System displays the message: `Unable to lookup User...`                     |
| 4    | The current page may be partially populated; cursor returns to search field   |

### AF7: End of File During Forward Paging

| Step | Description                                                                  |
|------|------------------------------------------------------------------------------|
| 1    | Administrator is paging forward through the user list                         |
| 2    | System reads through the records and reaches the end of the file             |
| 3    | System displays the message: `You have reached the bottom of the page...`   |
| 4    | Available records are displayed; Forward action becomes unavailable           |

### AF8: Beginning of File During Backward Paging

| Step | Description                                                                  |
|------|------------------------------------------------------------------------------|
| 1    | Administrator is paging backward through the user list                        |
| 2    | System reads backward and reaches the beginning of the file                  |
| 3    | System displays the message: `You have reached the top of the page...`      |
| 4    | Available records are displayed                                              |

---

## Section 7: Business Process Context

### Navigation Context

```
┌─────────────────────────────────────────────────────────────────────┐
│                     CardDemo Application Navigation                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Sign-on (CC00)                                                      │
│    │                                                                 │
│    ├── [Regular User] ──> Main Menu (CM00)                           │
│    │                        ├── Account View                         │
│    │                        ├── Account Update                       │
│    │                        ├── Card List                            │
│    │                        ├── Transaction List                     │
│    │                        ├── Bill Payment                         │
│    │                        └── Reports                              │
│    │                                                                 │
│    └── [Admin User] ──> Admin Menu (CA00)                            │
│                           │                                          │
│                           ├── *** User List (CU00) *** ◄── THIS UC   │
│                           │     ├── User Update (CU02)               │
│                           │     └── User Delete (CU03)               │
│                           │                                          │
│                           ├── User Add (CU01)                        │
│                           └── Transaction Type Mgmt                  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Downstream Use Cases

| Destination         | Trigger                                    | What Happens Next                                |
|---------------------|--------------------------------------------|--------------------------------------------------|
| User Update (CU02)  | Administrator types 'U' next to a user and presses Enter | System displays the selected user's details for editing (first name, last name, user type) |
| User Delete (CU03)  | Administrator types 'D' next to a user and presses Enter | System displays the selected user's details and prompts for deletion confirmation |

### Upstream Use Cases

| Source               | Navigation Path                                            |
|----------------------|------------------------------------------------------------|
| Admin Menu (CA00)    | Administrator selects Option 1 from the Admin Menu         |
| User Update (CU02)   | After completing or canceling an update, user returns via F3 (routes through Admin Menu) |
| User Delete (CU03)   | After completing or canceling a delete, user returns via F3 (routes through Admin Menu) |

### End-to-End Journey Examples

**Journey 1: Update a User's Role**
```
Sign-on ──> Admin Menu ──> User List ──> [Select 'U'] ──> User Update ──> Admin Menu
```
*Business Scenario: An administrator promotes a regular user to administrator status by locating them in the user list, selecting them for update, and changing their user type.*

**Journey 2: Remove a Departed Employee**
```
Sign-on ──> Admin Menu ──> User List ──> [Search by ID] ──> [Select 'D'] ──> User Delete ──> Admin Menu
```
*Business Scenario: An administrator removes a terminated employee's system access by searching for their User ID, selecting them for deletion, and confirming the removal.*

**Journey 3: Audit User Accounts**
```
Sign-on ──> Admin Menu ──> User List ──> [Page Forward] ──> [Page Forward] ──> ... ──> [F3] ──> Admin Menu
```
*Business Scenario: An administrator reviews the complete list of system users by paging through the entire directory to verify active accounts and identify any that should be removed.*

**Journey 4: Search and Update Specific User**
```
Sign-on ──> Admin Menu ──> User List ──> [Enter search ID] ──> [Select 'U'] ──> User Update ──> Admin Menu
```
*Business Scenario: An administrator receives a request to reset a user's information, uses the search field to quickly locate the account, then navigates to the update screen.*

---

## Section 8: Acceptance Criteria

### Core Functionality

| #     | Given                                     | When                                            | Then                                                         |
|-------|-------------------------------------------|-------------------------------------------------|--------------------------------------------------------------|
| AC-01 | Administrator is on the Admin Menu        | They select the User List option                | The User List screen displays with the first page of users sorted by User ID |
| AC-02 | User List screen is displayed             | There are more than 10 users in the system      | Exactly 10 users are shown per page                          |
| AC-03 | User List screen is displayed             | There are fewer than 10 users in the system     | Only the available users are shown (no blank filler rows with data) |
| AC-04 | User List screen is displayed             | Administrator types 'U' next to a user and presses Enter | System navigates to the User Update screen with the selected user's ID |
| AC-05 | User List screen is displayed             | Administrator types 'D' next to a user and presses Enter | System navigates to the User Delete screen with the selected user's ID |
| AC-06 | User List screen is displayed             | Administrator types 'u' (lowercase) next to a user and presses Enter | System navigates to the User Update screen (case-insensitive) |
| AC-07 | User List screen is displayed             | Administrator types 'd' (lowercase) next to a user and presses Enter | System navigates to the User Delete screen (case-insensitive) |

### Search / Filtering

| #     | Given                                     | When                                            | Then                                                         |
|-------|-------------------------------------------|-------------------------------------------------|--------------------------------------------------------------|
| AC-08 | User List screen is displayed             | Administrator enters a valid User ID in the search field and presses Enter | List repositions to show users starting at or after that User ID |
| AC-09 | User List screen is displayed             | Administrator leaves the search field blank and presses Enter | List displays from the beginning of the user directory       |
| AC-10 | User List screen is displayed             | Administrator enters a User ID that does not exist and no users follow it | System displays the message "You are at the top of the page..." |

### Pagination / Navigation

| #     | Given                                     | When                                            | Then                                                         |
|-------|-------------------------------------------|-------------------------------------------------|--------------------------------------------------------------|
| AC-11 | Page 1 is displayed and more users exist  | Administrator presses F8 (Forward)              | Next page of users is displayed; page number increments by 1 |
| AC-12 | A page after page 1 is displayed          | Administrator presses F7 (Backward)             | Previous page of users is displayed; page number decrements  |
| AC-13 | Page 1 is displayed                       | Administrator presses F7 (Backward)             | Message "You are already at the top of the page..." is shown; page does not change |
| AC-14 | Last page is displayed (no more records)  | Administrator presses F8 (Forward)              | Message "You are already at the bottom of the page..." is shown; page does not change |
| AC-15 | User List screen is displayed             | Administrator presses F3 (Back)                 | System returns to the Admin Menu                             |

### Input Validation

| #     | Given                                     | When                                            | Then                                                         |
|-------|-------------------------------------------|-------------------------------------------------|--------------------------------------------------------------|
| AC-16 | User List screen is displayed             | Administrator enters an invalid selection code (not U/D) and presses Enter | Message "Invalid selection. Valid values are U and D" is shown |
| AC-17 | User List screen is displayed             | Administrator presses an unsupported key (not Enter, F3, F7, F8) | Message "Invalid key pressed. Please see below..." is shown  |

### Data Integrity

| #     | Given                                     | When                                            | Then                                                         |
|-------|-------------------------------------------|-------------------------------------------------|--------------------------------------------------------------|
| AC-18 | User List screen is displayed             | Administrator views any page                    | Each row shows User ID, First Name, Last Name, and User Type from the user security store |
| AC-19 | User List screen is displayed             | Administrator views any page                    | Password fields are never shown on the screen                |
| AC-20 | Multiple selection fields have values      | Administrator presses Enter                     | Only the first non-empty selection (top to bottom) is processed |

---

## Section 9: Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                | Technical Reference                                                       |
|----------------------------------------------|---------------------------------------------------------------------------|
| Initial screen display on entry              | `MAIN-PARA`: checks `EIBCALEN`, moves `DFHCOMMAREA`, calls `PROCESS-ENTER-KEY` when `CDEMO-PGM-CONTEXT = 0` |
| Receive user input from screen               | `RECEIVE-USRLST-SCREEN`: `EXEC CICS RECEIVE MAP('COUSR0A') MAPSET('COUSR00')` |
| Evaluate which key was pressed               | `MAIN-PARA`: `EVALUATE EIBAID` — checks `DFHENTER`, `DFHPF3`, `DFHPF7`, `DFHPF8` |
| Process user selection (U/D)                 | `PROCESS-ENTER-KEY`: `EVALUATE` on `SEL0001I` through `SEL0010I`, then `EVALUATE CDEMO-CU00-USR-SEL-FLG` |
| Navigate to Update User                      | `PROCESS-ENTER-KEY`: `EXEC CICS XCTL PROGRAM('COUSR02C') COMMAREA(CARDDEMO-COMMAREA)` |
| Navigate to Delete User                      | `PROCESS-ENTER-KEY`: `EXEC CICS XCTL PROGRAM('COUSR03C') COMMAREA(CARDDEMO-COMMAREA)` |
| Search User ID positioning                   | `PROCESS-ENTER-KEY`: moves `USRIDINI OF COUSR0AI` to `SEC-USR-ID` (or `LOW-VALUES` if blank) |
| Page forward                                 | `PROCESS-PAGE-FORWARD`: `STARTBR-USER-SEC-FILE`, loop `READNEXT-USER-SEC-FILE` x10, peek one more `READNEXT` for next-page check |
| Page backward                                | `PROCESS-PAGE-BACKWARD`: `STARTBR-USER-SEC-FILE` at `CDEMO-CU00-USRID-FIRST`, loop `READPREV-USER-SEC-FILE` x10 |
| Top-of-page boundary check                   | `PROCESS-PF7-KEY`: `IF CDEMO-CU00-PAGE-NUM > 1` — else displays message |
| Bottom-of-page boundary check                | `PROCESS-PF8-KEY`: `IF NEXT-PAGE-YES` — else displays message |
| Start browse at position                     | `STARTBR-USER-SEC-FILE`: `EXEC CICS STARTBR DATASET('USRSEC') RIDFLD(SEC-USR-ID) KEYLENGTH(8)` |
| Read next user record                        | `READNEXT-USER-SEC-FILE`: `EXEC CICS READNEXT DATASET('USRSEC') INTO(SEC-USER-DATA)` |
| Read previous user record                    | `READPREV-USER-SEC-FILE`: `EXEC CICS READPREV DATASET('USRSEC') INTO(SEC-USER-DATA)` |
| End browse session                           | `ENDBR-USER-SEC-FILE`: `EXEC CICS ENDBR DATASET('USRSEC')` |
| Populate display row with user data          | `POPULATE-USER-DATA`: `EVALUATE WS-IDX` — maps `SEC-USR-ID`, `SEC-USR-FNAME`, `SEC-USR-LNAME`, `SEC-USR-TYPE` to map fields |
| Track first/last user on page                | `POPULATE-USER-DATA`: index 1 saves to `CDEMO-CU00-USRID-FIRST`; index 10 saves to `CDEMO-CU00-USRID-LAST` |
| Clear display rows before repopulating       | `INITIALIZE-USER-DATA`: moves `SPACES` to all fields for each index |
| Send screen to user                          | `SEND-USRLST-SCREEN`: `EXEC CICS SEND MAP('COUSR0A') MAPSET('COUSR00') FROM(COUSR0AO) ERASE CURSOR` |
| Populate header (date, time, titles)         | `POPULATE-HEADER-INFO`: `FUNCTION CURRENT-DATE`, formats `MM/DD/YY` and `HH:MM:SS`, moves `CCDA-TITLE01/02` |
| Return to Admin Menu                         | `RETURN-TO-PREV-SCREEN`: sets `CDEMO-TO-PROGRAM = 'COADM01C'`, `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)` |
| Pseudo-conversational return                 | `MAIN-PARA` bottom: `EXEC CICS RETURN TRANSID('CU00') COMMAREA(CARDDEMO-COMMAREA)` |
| Display invalid key error                    | `MAIN-PARA WHEN OTHER`: moves `CCDA-MSG-INVALID-KEY` to `WS-MESSAGE` |
| Display invalid selection error              | `PROCESS-ENTER-KEY WHEN OTHER`: moves literal `'Invalid selection. Valid values are U and D'` to `WS-MESSAGE` |
| Handle "not found" on browse start           | `STARTBR-USER-SEC-FILE`: `WHEN DFHRESP(NOTFND)` — sets EOF, displays `'You are at the top of the page...'` |
| Handle unexpected I/O error                  | `STARTBR-USER-SEC-FILE` / `READNEXT-USER-SEC-FILE` / `READPREV-USER-SEC-FILE`: `WHEN OTHER` — sets error flag, displays `'Unable to lookup User...'` |

### Source File Mapping

| Business Concept              | Source File(s)                                |
|-------------------------------|-----------------------------------------------|
| User List program logic       | `app/cbl/COUSR00C.cbl` (696 lines)           |
| Screen layout and fields      | `app/bms/COUSR00.bms` (464 lines)            |
| Inter-program communication   | `app/cpy/COCOM01Y.cpy`                       |
| User security record layout   | `app/cpy/CSUSR01Y.cpy`                       |
| Screen title constants        | `app/cpy/COTTL01Y.cpy`                       |
| Date/time formatting          | `app/cpy/CSDAT01Y.cpy`                       |
| Common error messages         | `app/cpy/CSMSG01Y.cpy`                       |
| Admin Menu (upstream)         | `app/cbl/COADM01C.cbl`                       |
| User Update (downstream)      | `app/cbl/COUSR02C.cbl`                       |
| User Delete (downstream)      | `app/cbl/COUSR03C.cbl`                       |

### Migration Considerations

| #  | Consideration                                                                                      |
|----|----------------------------------------------------------------------------------------------------|
| M1 | **Pseudo-conversational to stateless**: The current design uses COMMAREA to maintain page position (first/last User ID, page number, next-page flag) between interactions. In a modern web application, this maps to server-side session state, URL query parameters (e.g., `?page=2&startId=USER0011`), or cursor-based pagination tokens. |
| M2 | **VSAM browse to database query**: The STARTBR/READNEXT/READPREV/ENDBR pattern translates to SQL queries with `ORDER BY user_id ASC`, using `WHERE user_id >= :searchId` for forward paging and `WHERE user_id < :firstId ORDER BY user_id DESC LIMIT 10` for backward paging (keyset/cursor pagination). |
| M3 | **Fixed-format terminal to responsive UI**: The 24x80 screen with 10 fixed rows should become a responsive table/grid with configurable page size, sortable columns, and search-as-you-type functionality. |
| M4 | **Selection-to-XCTL to hyperlinks/buttons**: The 'U'/'D' selection pattern maps to action buttons or hyperlinks on each row (e.g., "Edit" and "Delete" icons/buttons) that route to the update or delete pages. |
| M5 | **Password security**: The underlying user record stores passwords in plain text (8-character field). Migration must implement proper password hashing (e.g., bcrypt, Argon2) and never expose passwords, even in API responses. |
| M6 | **Authorization enforcement**: The current program does not verify admin privileges — it relies on navigation flow. A modernized version must enforce role-based access control (RBAC) at the API/service layer, rejecting non-admin requests to the user management endpoints. |
| M7 | **BMS symbolic map to DTO**: The BMS-generated input/output structures (COUSR0AI/COUSR0AO) translate to request/response DTOs or view models in a modern framework. |
| M8 | **Page size configurability**: The hard-coded 10-record page size should become a configurable parameter (e.g., 10, 25, 50, 100 per page) to improve usability on modern displays. |
| M9 | **Audit logging**: Add audit trail for user list access — the current system has no logging of who viewed the user directory, which may be required for compliance. |

---

## Section 10: Related Use Cases / End-to-End Composition

| Journey Name                    | Use Cases Involved                        | Business Scenario                                                                 |
|---------------------------------|-------------------------------------------|------------------------------------------------------------------------------------|
| User Account Update             | UC-CU00-001, UC-CU02-001                 | Administrator browses the user list, selects a user, updates their profile (name, type), and returns to the list |
| User Account Removal            | UC-CU00-001, UC-CU03-001                 | Administrator searches for a specific user, selects them for deletion, confirms removal, and returns to the list |
| Full User Directory Audit       | UC-CU00-001 (repeated)                   | Administrator pages through the entire user directory to review all accounts, using forward/backward navigation |
| New User Onboarding (complete)  | UC-CU01-001, UC-CU00-001                 | Administrator adds a new user via User Add, then verifies the account appears in the User List |
| User Role Change                | UC-CU00-001, UC-CU02-001                 | Administrator locates a user by searching their ID, selects for update, changes role from Regular to Admin |
| Deactivation Workflow           | UC-CU00-001, UC-CU03-001 (repeated)      | Administrator removes multiple departed employees by repeatedly selecting users for deletion across multiple pages |

> **Note:** Individual use case flows (UC-CU01-001, UC-CU02-001, UC-CU03-001) must be composed into journey-level documents for complete end-to-end validation. These related use cases may not yet have their own Business Use Case Flow documents.

---

## Document Footer

| Metric                    | Value                          |
|---------------------------|--------------------------------|
| Source Program            | COUSR00C.cbl (696 lines)      |
| Transaction ID            | CU00                           |
| Data Source               | USRSEC (VSAM KSDS, read-only) |
| Acceptance Criteria       | 20                             |
| Business Rules            | 11                             |
| Validation Rules          | 4                              |
| Alternative/Error Flows   | 8                              |

---

*Generated for CardDemo Mainframe Modernization — CU00 Transaction (COUSR00C) Business Use Case Flow Analysis*

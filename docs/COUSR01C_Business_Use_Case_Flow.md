# COUSR01C — Business Use Case Flow Analysis

## Section 1 — Use Case Overview

| Attribute        | Value                                                                 |
|------------------|-----------------------------------------------------------------------|
| **Use Case Name**    | Add New User                                                      |
| **Use Case ID**      | UC-CU01-001                                                       |
| **Actor(s)**         | System Administrator                                              |
| **Business Goal**    | Create a new user account with login credentials and role assignment so the user can access the CardDemo application |
| **Preconditions**    | 1. Administrator is authenticated and signed in to the application<br>2. Administrator has navigated to the Admin Menu<br>3. Administrator has selected the "User Add" option from the Admin Menu |
| **Postconditions**   | A new user record exists in the security database with the provided credentials and role, and can be used to sign in to the application |
| **Trigger**          | Administrator selects "User Add" (option 2) from the Admin Menu   |
| **Data Access**      | Write (creates a new user record)                                 |
| **Frequency**        | Low — performed when onboarding new staff or granting system access |

### Business Context

The Add New User function is part of the **Security and Administration** domain of the CardDemo credit card management system. It enables system administrators to provision user accounts, assigning each user a unique identifier, display name, password, and role (either Administrator or Regular User). This is a prerequisite for any person to access the system — without a user record, sign-on is impossible.

The function is intentionally simple: it creates a single user record. It does not assign accounts, cards, or customers to the user. Role-based access control (Admin vs. Regular User) determines which menu the user sees after sign-on.

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CardDemo Functional Domains                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌───────────────────────┐    ┌───────────────────────────────────┐ │
│  │  Security & Admin     │    │  Credit Card Operations           │ │
│  │  ═══════════════════  │    │  ═══════════════════════════════  │ │
│  │  • Sign-On            │    │  • Account View / Update          │ │
│  │  • User List          │    │  • Card List / Detail / Update    │ │
│  │  ★ User Add ◄── THIS  │    │  • Transaction List / View / Add  │ │
│  │  • User Update        │    │  • Bill Payment                   │ │
│  │  • User Delete        │    │  • Reports                        │ │
│  └───────────────────────┘    └───────────────────────────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Section 2 — User Journey (Happy Path)

### Sequence Diagram

```
┌───────────────────┐                          ┌───────────────────┐
│   Administrator   │                          │      System       │
└────────┬──────────┘                          └────────┬──────────┘
         │                                              │
         │  Select "User Add" from Admin Menu           │
         │─────────────────────────────────────────────>│
         │                                              │
         │          Display empty Add User form         │
         │<─────────────────────────────────────────────│
         │                                              │
         │  Enter First Name, Last Name, User ID,       │
         │  Password, and User Type                     │
         │─────────────────────────────────────────────>│
         │                                              │
         │  Press Enter to submit                       │
         │─────────────────────────────────────────────>│
         │                                              │
         │          Validate all fields                 │
         │          ───────────────────                 │
         │          Create user record                  │
         │          ──────────────────                  │
         │                                              │
         │          Display success message:            │
         │          "User {ID} has been added ..."      │
         │          Clear form for next entry           │
         │<─────────────────────────────────────────────│
         │                                              │
         │  (Optionally) Add another user or press      │
         │  F3 to return to Admin Menu                  │
         │─────────────────────────────────────────────>│
         │                                              │
```

### Step-by-Step Narrative

| Step | Actor          | Action                                                    | System Response                                                                         |
|------|----------------|-----------------------------------------------------------|-----------------------------------------------------------------------------------------|
| 1    | Administrator  | Selects "User Add" (option 2) from the Admin Menu         | Navigates to the Add User screen and displays an empty form with cursor in First Name field |
| 2    | Administrator  | Types the new user's first name (up to 20 characters)     | Characters appear in the First Name field                                               |
| 3    | Administrator  | Tabs to Last Name and types it (up to 20 characters)      | Characters appear in the Last Name field                                                |
| 4    | Administrator  | Tabs to User ID and types it (up to 8 characters)         | Characters appear in the User ID field                                                  |
| 5    | Administrator  | Tabs to Password and types it (up to 8 characters)        | Field accepts input but characters are not displayed (masked)                           |
| 6    | Administrator  | Tabs to User Type and types 'A' (Admin) or 'U' (User)    | Single character appears in the User Type field                                         |
| 7    | Administrator  | Presses Enter to submit the new user                      | System validates all fields, creates the user record, clears the form, and displays a green success message: "User {ID} has been added ..." |
| 8    | Administrator  | (Optional) Enters another user or presses F3 to go back   | Either displays empty form ready for next user, or returns to Admin Menu                |

---

## Section 3 — Business Rules & Validations

### Input Validation Rules

| #  | Field       | Rule                                           | Error Message Shown to User              |
|----|-------------|------------------------------------------------|------------------------------------------|
| V1 | First Name  | Must not be blank (at least one character)     | "First Name can NOT be empty..."         |
| V2 | Last Name   | Must not be blank (at least one character)     | "Last Name can NOT be empty..."          |
| V3 | User ID     | Must not be blank (at least one character)     | "User ID can NOT be empty..."            |
| V4 | Password    | Must not be blank (at least one character)     | "Password can NOT be empty..."           |
| V5 | User Type   | Must not be blank (at least one character)     | "User Type can NOT be empty..."          |

### Business Rules

| #   | Rule                                                                                                                                      |
|-----|-------------------------------------------------------------------------------------------------------------------------------------------|
| BR1 | **Sequential validation**: Fields are validated in a fixed order (First Name → Last Name → User ID → Password → User Type). Only the first error is reported; the user must fix it and resubmit to discover any subsequent errors. |
| BR2 | **Unique User ID**: Each User ID must be unique across the entire user security database. If a User ID already exists, the system rejects the request with "User ID already exist..." |
| BR3 | **User Type accepts any non-blank character**: The system does not restrict User Type to specific values. While the screen hint shows "(A=Admin, U=User)", any single non-blank character is accepted and stored. |
| BR4 | **Password stored as-is**: The password is stored exactly as entered, with no transformation, hashing, or complexity requirements. Maximum length is 8 characters. |
| BR5 | **Form clears on success**: After a successful user creation, all input fields are cleared automatically, preparing the screen for the next user entry. |
| BR6 | **Cursor positioning on error**: When a validation error occurs, the cursor is automatically positioned on the offending field so the administrator can correct it immediately. |
| BR7 | **Authentication required**: The system enforces that only authenticated users with a valid session can access this function. Direct access without sign-on is blocked. |
| BR8 | **Filler space reserved**: The user record reserves 23 bytes of unused space for future field expansion, though no additional data is stored at creation time. |

### Decision Table

```
┌─────────────────────────────┬────────────────────────────────────────────────────┐
│ User Action                 │ System Response                                    │
├─────────────────────────────┼────────────────────────────────────────────────────┤
│ Press Enter (all valid)     │ Create user record, clear form, show green         │
│                             │ success message, position cursor to First Name     │
├─────────────────────────────┼────────────────────────────────────────────────────┤
│ Press Enter (field empty)   │ Show error for first empty field in sequence,      │
│                             │ position cursor on that field                      │
├─────────────────────────────┼────────────────────────────────────────────────────┤
│ Press Enter (duplicate ID)  │ Show "User ID already exist..." error,             │
│                             │ position cursor on User ID field                   │
├─────────────────────────────┼────────────────────────────────────────────────────┤
│ Press F3                    │ Return to Admin Menu (unsaved data is discarded)   │
├─────────────────────────────┼────────────────────────────────────────────────────┤
│ Press F4                    │ Clear all input fields, clear message area,        │
│                             │ position cursor on First Name field                │
├─────────────────────────────┼────────────────────────────────────────────────────┤
│ Press any other key         │ Show "Invalid key pressed. Please see below..."    │
│                             │ and position cursor on First Name field            │
├─────────────────────────────┼────────────────────────────────────────────────────┤
│ Access without sign-on      │ Redirect to Sign-On screen                         │
└─────────────────────────────┴────────────────────────────────────────────────────┘
```

---

## Section 4 — Data Entities Involved

### Entity Descriptions

| Entity            | Description                                              | Role in This Use Case                                      |
|-------------------|----------------------------------------------------------|------------------------------------------------------------|
| User Security     | Stores login credentials and role for all application users | Target entity — a new record is written with the user's ID, name, password, and role |
| Application Session | In-memory context passed between screens during a user's session | Carries the administrator's identity and navigation state; used to verify authorization and manage screen flow |

### Entity Relationships

```
┌──────────────────────┐         ┌──────────────────────┐
│   User Security      │         │   Application        │
│   (User Records)     │         │   Session            │
├──────────────────────┤         ├──────────────────────┤
│ • User ID (key)      │         │ • Current User ID    │
│ • First Name         │◄────────│ • Current User Type  │
│ • Last Name          │ created │ • Source Screen      │
│ • Password           │   by    │ • Target Screen      │
│ • User Type          │         │ • Navigation State   │
│ • (Reserved space)   │         │                      │
└──────────────────────┘         └──────────────────────┘
        │
        │ used by
        ▼
┌──────────────────────┐
│   Sign-On Process    │
│   (Authentication)   │
├──────────────────────┤
│ Reads User Security  │
│ to validate login    │
└──────────────────────┘
```

### Data Fields Displayed

| #  | Field       | Format                 | Source          | Description                                              |
|----|-------------|------------------------|-----------------|----------------------------------------------------------|
| 1  | First Name  | Text, up to 20 chars   | User input      | The new user's given name                                |
| 2  | Last Name   | Text, up to 20 chars   | User input      | The new user's surname                                   |
| 3  | User ID     | Text, up to 8 chars    | User input      | Unique login identifier for the new user                 |
| 4  | Password    | Text, up to 8 chars (masked on screen) | User input | The new user's login password (not visible when typed) |
| 5  | User Type   | Single character       | User input      | Role assignment: 'A' for Administrator, 'U' for Regular User |

**Fields in underlying record NOT displayed on screen:**
- Reserved filler space (23 bytes) — exists in the stored record but is not shown or editable on the screen. It is initialized to spaces when the record is created.

---

## Section 5 — Screen / Interface Description

### Screen Layout

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│Tran: CU01      AWS Mainframe Modernization             Date: 05/07/26           │
│Prog: COUSR01C              CardDemo                    Time: 14:30:22           │
│                                                                                  │
│                                   Add User                                       │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│     First Name: [John                ]    Last Name: [Smith               ]      │
│                                                                                  │
│                                                                                  │
│     User ID:  [JSMITH01] (8 Char)    Password: [________] (8 Char)              │
│                                                                                  │
│                                                                                  │
│     User Type: [U] (A=Admin, U=User)                                            │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│User JSMITH01 has been added ...                                                  │
│ENTER=Add User  F3=Back  F4=Clear  F12=Exit                                      │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Interface Elements

| Element     | Type    | Description                                                       |
|-------------|---------|-------------------------------------------------------------------|
| First Name  | Input   | Free-text field for the user's first/given name (20 characters max). Cursor starts here. |
| Last Name   | Input   | Free-text field for the user's last/family name (20 characters max) |
| User ID     | Input   | Unique login identifier for the new account (8 characters max)    |
| Password    | Input   | Login password for the new account (8 characters max, input is masked/hidden) |
| User Type   | Input   | Single-character role code: 'A' = Administrator, 'U' = Regular User |
| Transaction ID | Display | Shows "CU01" — identifies the current function                 |
| Program Name   | Display | Shows "COUSR01C" — identifies the running program             |
| Title Line 1  | Display | Application branding: "AWS Mainframe Modernization"           |
| Title Line 2  | Display | Application name: "CardDemo"                                  |
| Current Date   | Display | Today's date in MM/DD/YY format                               |
| Current Time   | Display | Current time in HH:MM:SS format                               |
| Message Area   | Display | Shows success messages (green) or error messages (red)        |
| Footer         | Display | Lists available keyboard actions                              |

### Available Actions

| Action          | How to Invoke | Description                                                        |
|-----------------|---------------|--------------------------------------------------------------------|
| Submit User     | Press Enter   | Validates all fields and creates the new user if valid             |
| Return to Menu  | Press F3      | Discards any unsaved input and returns to the Admin Menu           |
| Clear Form      | Press F4      | Erases all input fields and message area; resets for fresh entry   |
| (Exit - advertised but non-functional) | Press F12 | Displayed in footer but treated as an invalid key |

---

## Section 6 — Alternative & Error Flows

### Alt Flow 1: Empty Required Field

| Step | Description                                                                                     |
|------|-------------------------------------------------------------------------------------------------|
| 1    | Administrator leaves one or more required fields blank and presses Enter                        |
| 2    | System checks fields in order: First Name, Last Name, User ID, Password, User Type             |
| 3    | System finds the first blank field and displays the corresponding error message (e.g., "First Name can NOT be empty...") |
| 4    | Cursor is positioned on the offending field                                                     |
| 5    | All previously entered data remains on screen; administrator can correct and resubmit           |

### Alt Flow 2: Duplicate User ID

| Step | Description                                                                                     |
|------|-------------------------------------------------------------------------------------------------|
| 1    | Administrator fills all fields correctly and presses Enter                                      |
| 2    | System passes all input validations                                                             |
| 3    | System attempts to create the user record but discovers the User ID already exists              |
| 4    | System displays error: "User ID already exist..."                                              |
| 5    | Cursor is positioned on the User ID field so the administrator can change it                   |
| 6    | All other field values remain intact; administrator changes the User ID and resubmits           |

### Alt Flow 3: System Write Failure

| Step | Description                                                                                     |
|------|-------------------------------------------------------------------------------------------------|
| 1    | Administrator fills all fields correctly and presses Enter                                      |
| 2    | System passes all input validations                                                             |
| 3    | System attempts to create the user record but encounters an unexpected storage error            |
| 4    | System displays error: "Unable to Add User..."                                                 |
| 5    | Cursor is positioned on the First Name field                                                   |
| 6    | Administrator may retry or contact technical support                                            |

### Alt Flow 4: Invalid Key Pressed

| Step | Description                                                                                     |
|------|-------------------------------------------------------------------------------------------------|
| 1    | Administrator presses a key not recognized by the system (any key other than Enter, F3, F4)    |
| 2    | System displays message: "Invalid key pressed. Please see below..."                            |
| 3    | Cursor is positioned on the First Name field                                                   |
| 4    | All previously entered data remains on screen                                                   |

### Alt Flow 5: Clear Form (F4)

| Step | Description                                                                                     |
|------|-------------------------------------------------------------------------------------------------|
| 1    | Administrator presses F4 at any point while on the Add User screen                             |
| 2    | System erases all input fields (First Name, Last Name, User ID, Password, User Type)           |
| 3    | System clears the message area                                                                  |
| 4    | Cursor is positioned on the First Name field, ready for fresh data entry                       |

### Alt Flow 6: Unauthorized Access Attempt

| Step | Description                                                                                     |
|------|-------------------------------------------------------------------------------------------------|
| 1    | A user attempts to access the Add User function without first signing in                       |
| 2    | System detects no valid session context                                                         |
| 3    | System redirects the user to the Sign-On screen                                                |
| 4    | No error message is displayed on the Add User screen (user never sees it)                      |

---

## Section 7 — Business Process Context

### Navigation Context

```
CardDemo Application
│
├── Sign-On Screen (CC00)
│   │
│   ├── [Regular User] ──► Main Menu (CM00)
│   │                       ├── Account View
│   │                       ├── Card List
│   │                       ├── Transaction List
│   │                       ├── Bill Payment
│   │                       └── Reports
│   │
│   └── [Administrator] ──► Admin Menu (CA00)
│                           ├── User List
│                           ├── ★ User Add (CU01) ◄── THIS USE CASE
│                           ├── User Update
│                           └── User Delete
│
```

### Downstream Use Cases

| Destination         | Trigger                              | What Happens Next                                    |
|---------------------|--------------------------------------|------------------------------------------------------|
| Admin Menu          | Administrator presses F3             | Returns to the Admin Menu where the administrator can select another function |
| Sign-On (redirect)  | Session is invalid/missing          | User is forced to authenticate before accessing any function |

### Upstream Use Cases

| Source              | Navigation Path                                                    |
|---------------------|--------------------------------------------------------------------|
| Admin Menu          | Administrator selects "User Add" (option 2) from the Admin Menu   |
| Sign-On             | Administrator signs in → system routes to Admin Menu based on admin role |

### End-to-End Journey Examples

**Journey 1: Onboard a New Regular User**

```
Sign-On ──► Admin Menu ──► User Add ──► (create user) ──► Admin Menu ──► Sign-Off
```
*Business Scenario: An administrator provisions a new customer service representative who will view accounts and transactions but cannot manage users.*

**Journey 2: Onboard a New Administrator**

```
Sign-On ──► Admin Menu ──► User Add ──► (create admin user) ──► Admin Menu ──► Sign-Off
```
*Business Scenario: A senior administrator creates another admin account for a newly promoted team lead who will manage user access.*

**Journey 3: Bulk User Provisioning**

```
Sign-On ──► Admin Menu ──► User Add ──► (create user 1) ──► (create user 2) ──► ... ──► F3 ──► Admin Menu
```
*Business Scenario: An administrator onboards multiple new staff members in a single session, entering each user sequentially without leaving the Add User screen.*

**Journey 4: Verify New User Can Sign In**

```
[Admin] Sign-On ──► Admin Menu ──► User Add ──► (create user "NEWUSER1") ──► Sign-Off
[New User] Sign-On (as NEWUSER1) ──► Main Menu ──► (access granted)
```
*Business Scenario: After provisioning a new user, the administrator confirms the account works by having the new user sign in with the credentials just created.*

---

## Section 8 — Acceptance Criteria

### Core Functionality

| #     | Given                                        | When                                           | Then                                                                                  |
|-------|----------------------------------------------|------------------------------------------------|----------------------------------------------------------------------------------------|
| AC-01 | Administrator is on the Add User screen      | All fields are filled with valid data and Enter is pressed | A new user record is created and the success message "User {ID} has been added ..." is displayed in green |
| AC-02 | A user has just been successfully created    | The screen refreshes after success             | All input fields are cleared and the cursor is positioned on the First Name field      |
| AC-03 | Administrator is on the Add User screen      | Administrator presses F3                       | The system returns to the Admin Menu without creating a user                           |
| AC-04 | Administrator is on the Add User screen with data entered | Administrator presses F4       | All input fields are cleared, the message area is cleared, and the cursor is on First Name |

### Input Validation

| #     | Given                                        | When                                           | Then                                                                                  |
|-------|----------------------------------------------|------------------------------------------------|----------------------------------------------------------------------------------------|
| AC-05 | First Name field is blank                    | Enter is pressed                               | Error "First Name can NOT be empty..." is shown and cursor is on First Name           |
| AC-06 | First Name is filled but Last Name is blank  | Enter is pressed                               | Error "Last Name can NOT be empty..." is shown and cursor is on Last Name             |
| AC-07 | First and Last Name filled but User ID blank | Enter is pressed                               | Error "User ID can NOT be empty..." is shown and cursor is on User ID                 |
| AC-08 | Names and ID filled but Password is blank    | Enter is pressed                               | Error "Password can NOT be empty..." is shown and cursor is on Password               |
| AC-09 | All fields filled except User Type           | Enter is pressed                               | Error "User Type can NOT be empty..." is shown and cursor is on User Type             |
| AC-10 | All fields are blank                         | Enter is pressed                               | Only the First Name error is shown (sequential validation stops at the first error)   |

### Data Integrity

| #     | Given                                        | When                                           | Then                                                                                  |
|-------|----------------------------------------------|------------------------------------------------|----------------------------------------------------------------------------------------|
| AC-11 | A user with ID "EXISTUSR" already exists     | Administrator enters "EXISTUSR" as User ID and presses Enter | Error "User ID already exist..." is shown and cursor is on User ID field |
| AC-12 | An unexpected storage error occurs           | Administrator submits a valid form             | Error "Unable to Add User..." is shown and cursor is on First Name                    |
| AC-13 | Administrator enters 'A' as User Type        | The record is created successfully             | The stored record has User Type = 'A' and the user will see the Admin Menu on sign-in |
| AC-14 | Administrator enters 'U' as User Type        | The record is created successfully             | The stored record has User Type = 'U' and the user will see the Main Menu on sign-in  |
| AC-15 | Password field is filled                     | The record is created successfully             | The password is stored exactly as entered (no transformation)                         |

### Navigation

| #     | Given                                        | When                                           | Then                                                                                  |
|-------|----------------------------------------------|------------------------------------------------|----------------------------------------------------------------------------------------|
| AC-16 | User has no valid session                    | User attempts to access Add User directly      | System redirects to the Sign-On screen                                                |
| AC-17 | Administrator is on the Add User screen      | Any unrecognized key is pressed                | Error "Invalid key pressed. Please see below..." is shown                             |
| AC-18 | Administrator presses F12 (shown in footer)  | System processes the key                       | Same behavior as any unrecognized key — "Invalid key pressed" message is shown        |

### Display

| #     | Given                                        | When                                           | Then                                                                                  |
|-------|----------------------------------------------|------------------------------------------------|----------------------------------------------------------------------------------------|
| AC-19 | The Add User screen is displayed             | Administrator views the screen                 | The header shows transaction ID, program name, current date (MM/DD/YY) and time (HH:MM:SS) |
| AC-20 | Administrator types in the Password field    | Characters are entered                         | The characters are NOT visible on screen (field is masked/dark)                       |

---

## Section 9 — Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                | Technical Reference                                                                    |
|----------------------------------------------|----------------------------------------------------------------------------------------|
| Verify user is authenticated                 | `MAIN-PARA`: checks `EIBCALEN = 0`; if true, XCTLs to `COSGN00C`                     |
| Display empty Add User form (first entry)    | `MAIN-PARA`: checks `CDEMO-PGM-REENTER` = FALSE, sets to TRUE, initializes `COUSR1AO` to LOW-VALUES, PERFORMs `SEND-USRADD-SCREEN` |
| Receive user input                           | `RECEIVE-USRADD-SCREEN`: `EXEC CICS RECEIVE MAP('COUSR1A') MAPSET('COUSR01') INTO(COUSR1AI)` |
| Evaluate which key was pressed               | `MAIN-PARA`: `EVALUATE EIBAID` with conditions for `DFHENTER`, `DFHPF3`, `DFHPF4`, `OTHER` |
| Validate First Name not empty (V1)           | `PROCESS-ENTER-KEY` (line 118): `WHEN FNAMEI OF COUSR1AI = SPACES OR LOW-VALUES`      |
| Validate Last Name not empty (V2)            | `PROCESS-ENTER-KEY` (line 124): `WHEN LNAMEI OF COUSR1AI = SPACES OR LOW-VALUES`      |
| Validate User ID not empty (V3)              | `PROCESS-ENTER-KEY` (line 130): `WHEN USERIDI OF COUSR1AI = SPACES OR LOW-VALUES`     |
| Validate Password not empty (V4)             | `PROCESS-ENTER-KEY` (line 136): `WHEN PASSWDI OF COUSR1AI = SPACES OR LOW-VALUES`     |
| Validate User Type not empty (V5)            | `PROCESS-ENTER-KEY` (line 142): `WHEN USRTYPEI OF COUSR1AI = SPACES OR LOW-VALUES`    |
| Map input fields to record layout            | `PROCESS-ENTER-KEY` (lines 154-158): MOVEs from `COUSR1AI` symbolic map fields to `SEC-USER-DATA` fields |
| Write user record to security file           | `WRITE-USER-SEC-FILE`: `EXEC CICS WRITE DATASET(WS-USRSEC-FILE) FROM(SEC-USER-DATA) RIDFLD(SEC-USR-ID)` |
| Handle successful write                      | `WRITE-USER-SEC-FILE` (line 251): `WHEN DFHRESP(NORMAL)` → PERFORM `INITIALIZE-ALL-FIELDS`, compose success STRING, set `ERRMSGC` to `DFHGREEN` |
| Handle duplicate key                         | `WRITE-USER-SEC-FILE` (lines 260-261): `WHEN DFHRESP(DUPKEY)` / `WHEN DFHRESP(DUPREC)` → error message "User ID already exist..." |
| Handle other write errors                    | `WRITE-USER-SEC-FILE` (line 267): `WHEN OTHER` → error message "Unable to Add User..."  |
| Clear form (F4)                              | `CLEAR-CURRENT-SCREEN`: PERFORMs `INITIALIZE-ALL-FIELDS` then `SEND-USRADD-SCREEN`    |
| Return to Admin Menu (F3)                    | `MAIN-PARA` (line 94): MOVE `'COADM01C'` TO `CDEMO-TO-PROGRAM`, PERFORM `RETURN-TO-PREV-SCREEN` which issues `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA)` |
| Handle invalid key                           | `MAIN-PARA` (lines 99-102): `WHEN OTHER` → MOVE `CCDA-MSG-INVALID-KEY` TO `WS-MESSAGE` |
| Populate screen header                       | `POPULATE-HEADER-INFO`: uses `FUNCTION CURRENT-DATE`, moves titles from `COTTL01Y`, formats date as MM/DD/YY and time as HH:MM:SS |
| Pseudo-conversational return                 | `MAIN-PARA` (line 107): `EXEC CICS RETURN TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA)` |

### Source File Mapping

| Business Concept                  | Source File(s)                                              |
|-----------------------------------|-------------------------------------------------------------|
| Add User program logic            | `app/cbl/COUSR01C.cbl` (300 lines)                         |
| Screen layout and field definitions | `app/bms/COUSR01.bms` (map COUSR1A in mapset COUSR01)    |
| User record structure             | `app/cpy/CSUSR01Y.cpy` (SEC-USER-DATA, 80 bytes)           |
| Session/navigation context        | `app/cpy/COCOM01Y.cpy` (CARDDEMO-COMMAREA)                 |
| Application titles                | `app/cpy/COTTL01Y.cpy` (CCDA-TITLE01, CCDA-TITLE02)        |
| Date/time formatting              | `app/cpy/CSDAT01Y.cpy` (WS-CURDATE-MM-DD-YY, etc.)        |
| Common error messages             | `app/cpy/CSMSG01Y.cpy` (CCDA-MSG-INVALID-KEY)              |
| BMS symbolic map (generated)      | `app/cpy-bms/COUSR01.CPY` (field I/O structures)           |
| Admin Menu (upstream program)     | `app/cbl/COADM01C.cbl`                                     |
| Sign-On (entry point)             | `app/cbl/COSGN00C.cbl`                                     |

### Migration Considerations

| #  | Consideration                                                                                                                                     |
|----|---------------------------------------------------------------------------------------------------------------------------------------------------|
| M1 | **Password hashing required**: The current system stores passwords in plain text (PIC X(08)). Any modern implementation must use a secure hashing algorithm (e.g., bcrypt, Argon2) and should not impose an 8-character maximum. |
| M2 | **User Type enum validation**: The current system accepts any non-blank character for User Type. The modern system should enforce a strict enumeration (Admin, User) with validation at input time. |
| M3 | **Audit trail needed**: No record is kept of who created a user, when, or from where. The modern system should add created-by, created-timestamp, and source IP/session fields. |
| M4 | **Password complexity rules**: No minimum length or character-type requirements exist. Modern implementation should enforce complexity policies (minimum length, mixed case, special characters). |
| M5 | **Pseudo-conversational to stateless**: The CICS pseudo-conversational pattern (RETURN TRANSID with COMMAREA) maps to a standard HTTP request/response model. Session state in the COMMAREA becomes a server-side session or token-based context. |
| M6 | **BMS screen to web form**: The 24x80 terminal screen maps directly to an HTML form with five input fields. The masked password field becomes `<input type="password">`. Field length limits become `maxlength` attributes. |
| M7 | **VSAM WRITE to database INSERT**: The single CICS WRITE operation maps to a SQL `INSERT INTO users (...)` statement. The DUPKEY/DUPREC response becomes a unique constraint violation exception. |
| M8 | **XCTL navigation to routing**: The XCTL-based navigation (back to Admin Menu, redirect to Sign-On) maps to URL routing or SPA navigation with route guards for authentication. |
| M9 | **F12 inconsistency to resolve**: The screen advertises F12=Exit but the program does not handle it (falls through to invalid key error). The modern system should either implement Exit functionality or remove the option from the UI. |
| M10 | **Sequential validation to parallel**: The current "first error only" validation pattern should be modernized to validate all fields simultaneously and display all errors at once for better user experience. |

---

## Section 10 — Related Use Cases / End-to-End Composition

| Journey Name                   | Use Cases Involved                                    | Business Scenario                                                                                     |
|--------------------------------|-------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| New Employee Onboarding        | Sign-On → Admin Menu → User Add → Sign-Off           | Administrator creates a user account for a newly hired employee so they can begin accessing the system |
| User Provisioning and Verification | User Add → Sign-Off → Sign-On (as new user)      | After creating a user, verify the credentials work by signing in with the new account                 |
| Bulk Staff Provisioning        | Sign-On → Admin Menu → User Add (×N) → Admin Menu   | During a hiring event, administrator creates multiple user accounts in succession without leaving the screen |
| Full User Lifecycle            | User Add → User Update → User Delete                 | Complete management of a user from creation through modification to eventual deactivation              |
| Admin Self-Service Setup       | User Add (type=A) → Sign-On (new admin) → Admin Menu | A super-admin creates another admin account; the new admin then signs in and gains full admin access   |

**Note:** The User Update (CU02/COUSR02C), User Delete (CU03/COUSR03C), and User List (CU00/COUSR00C) use cases are referenced above. Each requires its own Business Use Case Flow document for complete end-to-end journey validation.

---

## Document Footer

| Attribute                | Value                                |
|--------------------------|--------------------------------------|
| **Source Program**       | COUSR01C.cbl (300 lines)             |
| **Transaction ID**       | CU01                                 |
| **Data Source**          | Technical Flow Analysis + COBOL source verification |
| **Acceptance Criteria**  | 20 (AC-01 through AC-20)            |
| **Business Rules**       | 8 (BR1 through BR8)                 |
| **Validation Rules**     | 5 (V1 through V5)                   |
| **Alternative Flows**    | 6                                    |
| **Migration Considerations** | 10 (M1 through M10)             |

# COADM01C (CA00) — Admin Menu: Business Use Case Flow Analysis

## Section 1 — Use Case Overview

| Attribute          | Value                                                                 |
|--------------------|-----------------------------------------------------------------------|
| **Use Case Name**  | Admin Menu Navigation                                                 |
| **Use Case ID**    | UC-CA00-001                                                           |
| **Actor(s)**       | Administrator (authenticated user with admin privileges)              |
| **Business Goal**  | Provide administrators with a centralized menu to access all user security management and transaction type maintenance functions |
| **Preconditions**  | 1. Actor has successfully signed in via the Sign-on screen<br>2. Actor's account is flagged as an administrator<br>3. The system has passed the actor's credentials and session context to this screen |
| **Postconditions** | Actor has navigated to a selected administrative function, or has returned to the Sign-on screen |
| **Trigger**        | Successful administrator sign-in routes the actor to this menu automatically |
| **Data Access**    | Read-only (this screen performs no direct data reads or writes; it is purely a navigation hub) |
| **Frequency**      | Every administrator session — this is the mandatory landing page after admin sign-in |

### Business Context

The Admin Menu is the central navigation hub for all administrative operations in the CardDemo credit card management system. After a successful sign-in, administrators are routed here instead of the regular Main Menu that standard users see. From this single screen, administrators can launch any of six administrative functions: four for managing user security records (list, add, update, delete) and two for managing transaction type reference data. The menu acts as a dispatcher — it validates the administrator's selection and hands off control to the appropriate function. It does not itself read or modify any business data.

### Application Functional Domain Map

```
┌─────────────────────────────────────────────────────────────────────┐
│                     CardDemo Application                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐                                                   │
│  │   Sign-on    │                                                   │
│  │   (CC00)     │                                                   │
│  └──────┬───────┘                                                   │
│         │                                                           │
│    ┌────┴────┐                                                      │
│    │ Admin?  │                                                      │
│    └────┬────┘                                                      │
│    YES  │       NO                                                  │
│    ┌────┴────┐  ┌──────────────┐                                    │
│    │         │  │  Main Menu   │                                    │
│    │ ╔══════════════════════╗  │  (CM00)      │                     │
│    │ ║  >>> ADMIN MENU <<< ║  │  └──────┬─────┘                     │
│    │ ║      (CA00)         ║  │         │                           │
│    │ ╚══════════╤═══════════╝  │  Account / Card / Transaction     │
│    │            │              │  Management Functions              │
│    │  ┌─────────┼─────────┐                                         │
│    │  │         │         │                                         │
│    │  v         v         v                                         │
│    │ ┌────┐  ┌────┐  ┌────────────────────┐                         │
│    │ │User│  │User│  │ Transaction Type   │                         │
│    │ │Mgmt│  │CRUD│  │ Management         │                         │
│    │ └────┘  └────┘  └────────────────────┘                         │
│    │                                                                │
└────┴────────────────────────────────────────────────────────────────┘
```

---

## Section 2 — User Journey (Happy Path)

### Sequence Diagram

```
┌──────────┐                              ┌──────────────┐
│   Admin  │                              │    System    │
│   User   │                              │              │
└────┬─────┘                              └──────┬───────┘
     │                                           │
     │  Signs in with admin credentials          │
     │ ─────────────────────────────────────────>│
     │                                           │
     │  System verifies admin role               │
     │  and routes to Admin Menu                 │
     │ <─────────────────────────────────────────│
     │                                           │
     │  Admin Menu displayed with 6 options,     │
     │  current date/time, and input prompt      │
     │ <─────────────────────────────────────────│
     │                                           │
     │  Admin reviews menu options               │
     │                                           │
     │  Types option number (e.g., "1")          │
     │  and presses Enter                        │
     │ ─────────────────────────────────────────>│
     │                                           │
     │  System validates the option number       │
     │                                           │
     │  System transfers control to the          │
     │  selected function (e.g., User List)      │
     │ <─────────────────────────────────────────│
     │                                           │
     │  Selected function screen displayed       │
     │ <─────────────────────────────────────────│
     │                                           │
```

### Step-by-Step Narrative

| Step | Actor        | Action                                      | System Response                                                                                  |
|------|--------------|---------------------------------------------|--------------------------------------------------------------------------------------------------|
| 1    | System       | —                                           | After successful admin sign-in, the system automatically transfers the actor to the Admin Menu    |
| 2    | System       | —                                           | Displays the Admin Menu screen with application title, current date and time, six numbered menu options, and an input prompt ("Please select an option :") |
| 3    | Admin User   | Reviews the available menu options           | —                                                                                                |
| 4    | Admin User   | Types an option number (1–6) in the input field | —                                                                                             |
| 5    | Admin User   | Presses Enter                               | System validates the option number                                                                |
| 6    | System       | —                                           | System transfers control to the selected administrative function screen                           |
| 7    | Admin User   | Completes work in the selected function      | —                                                                                                |
| 8    | Admin User   | Presses F3 (Exit) in the selected function   | System returns the actor to the Admin Menu                                                        |
| 9    | Admin User   | Presses F3 (Exit) on the Admin Menu          | System returns the actor to the Sign-on screen                                                    |

---

## Section 3 — Business Rules & Validations

### Input Validation Rules

| #  | Field          | Rule                                                                                     | Error Message Shown to User                        |
|----|----------------|------------------------------------------------------------------------------------------|---------------------------------------------------|
| V1 | Option Number  | Must be a numeric value                                                                  | `Please enter a valid option number...`           |
| V2 | Option Number  | Must be greater than zero                                                                | `Please enter a valid option number...`           |
| V3 | Option Number  | Must not exceed the total number of available menu options (currently 6)                  | `Please enter a valid option number...`           |

### Business Rules

| #   | Rule                                                                                                                                  |
|-----|---------------------------------------------------------------------------------------------------------------------------------------|
| BR1 | **Admin-Only Access:** Only users authenticated with an administrator role are routed to this menu. Standard users are directed to a separate Main Menu |
| BR2 | **Session Continuity Required:** If the system detects that no session context was passed (e.g., direct invocation of the transaction), the actor is immediately redirected to the Sign-on screen |
| BR3 | **Table-Driven Menu:** Menu options are defined in a centralized configuration table. Adding, removing, or reordering options does not require changes to the menu program itself |
| BR4 | **Graceful Handling of Unavailable Functions:** If a selected function is not installed in the system (e.g., features requiring a database extension), the system displays a non-blocking informational message and returns the actor to the menu rather than producing a system error |
| BR5 | **Placeholder Program Skip:** If a menu option's target program is defined as a placeholder (name starts with "DUMMY"), the system treats it as an unavailable function and displays the "not installed" message |
| BR6 | **Navigation Context Passing:** When transferring to a downstream function, the system records the originating menu's identity and transaction so that downstream functions can navigate back correctly |
| BR7 | **F3 Returns to Sign-on:** Pressing F3 (Exit) from the Admin Menu always returns the actor to the Sign-on screen, ending the administrative session |
| BR8 | **Invalid Key Rejection:** Any key press other than Enter or F3 is rejected with an error message, and the menu is redisplayed |

### Decision Table

```
┌───────────────────────────┬──────────────────────────────────────────────────────┐
│ User Action               │ System Response                                      │
├───────────────────────────┼──────────────────────────────────────────────────────┤
│ Enter + valid option      │ Transfer to selected function screen                 │
│ (1–6, function installed) │                                                      │
├───────────────────────────┼──────────────────────────────────────────────────────┤
│ Enter + valid option      │ Display "This option is not installed ..." (green)   │
│ (function not installed)  │ and redisplay menu                                   │
├───────────────────────────┼──────────────────────────────────────────────────────┤
│ Enter + valid option      │ Display "This option is not installed ..." (green)   │
│ (placeholder program)     │ and redisplay menu                                   │
├───────────────────────────┼──────────────────────────────────────────────────────┤
│ Enter + non-numeric value │ Display "Please enter a valid option number..."      │
│                           │ and redisplay menu                                   │
├───────────────────────────┼──────────────────────────────────────────────────────┤
│ Enter + zero              │ Display "Please enter a valid option number..."      │
│                           │ and redisplay menu                                   │
├───────────────────────────┼──────────────────────────────────────────────────────┤
│ Enter + number > 6        │ Display "Please enter a valid option number..."      │
│                           │ and redisplay menu                                   │
├───────────────────────────┼──────────────────────────────────────────────────────┤
│ F3 (Exit)                 │ Return to Sign-on screen                             │
├───────────────────────────┼──────────────────────────────────────────────────────┤
│ Any other key             │ Display "Invalid key pressed. Please see below..."   │
│ (F1, F2, F4–F24, etc.)   │ and redisplay menu                                   │
├───────────────────────────┼──────────────────────────────────────────────────────┤
│ Direct access without     │ Redirect to Sign-on screen (no menu displayed)       │
│ session context           │                                                      │
└───────────────────────────┴──────────────────────────────────────────────────────┘
```

---

## Section 4 — Data Entities Involved

### Entity Descriptions

| Entity                 | Description                                                                                      | Role in This Use Case                                                              |
|------------------------|--------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| User Security Records  | Stores administrator and standard user credentials, names, and role designations                  | Not accessed directly by this screen; managed by downstream functions launched from this menu |
| Admin Menu Options     | A configuration table defining the available menu choices, their display labels, and the program each option launches | Read at display time to populate the menu screen dynamically                        |
| Session Context        | Shared data area carrying the authenticated user's identity, role, and navigation state between screens | Read on entry to verify session validity; updated before transferring to a selected function |

### Entity Relationships

```
┌──────────────────────┐
│   Session Context    │
│  (per-user session)  │
└──────────┬───────────┘
           │ contains
           v
┌──────────────────────┐       ┌──────────────────────────┐
│   User Identity      │       │  Admin Menu Options      │
│  (1 per session)     │       │  (6 entries, fixed)      │
└──────────┬───────────┘       └──────────┬───────────────┘
           │ authenticated                │ each option
           │ as admin                     │ references
           v                             v
┌──────────────────────┐       ┌──────────────────────────┐
│   Admin Menu         │──────>│  Downstream Function     │
│   (this use case)    │ routes│  (1 of 6 programs)       │
└──────────────────────┘  to   └──────────┬───────────────┘
                                          │ manages
                                          v
                               ┌──────────────────────────┐
                               │  User Security Records   │
                               │  (0..* records)          │
                               └──────────────────────────┘
```

### Data Fields Displayed

| #  | Field               | Format      | Source                    | Description                                              |
|----|---------------------|-------------|---------------------------|----------------------------------------------------------|
| 1  | Transaction ID      | 4 characters | System constant           | Identifies this screen's transaction ("CA00")             |
| 2  | Program Name        | 8 characters | System constant           | Identifies the running program ("COADM01C")               |
| 3  | Application Title 1 | 40 characters | Application constant      | "AWS Mainframe Modernization" branding line               |
| 4  | Application Title 2 | 40 characters | Application constant      | "CardDemo" branding line                                  |
| 5  | Current Date        | MM/DD/YY    | System clock              | Today's date at the time the screen is displayed          |
| 6  | Current Time        | HH:MM:SS    | System clock              | Current time at the time the screen is displayed          |
| 7  | Menu Option 1       | Text        | Admin Menu Options table  | "01. User List (Security)"                                |
| 8  | Menu Option 2       | Text        | Admin Menu Options table  | "02. User Add (Security)"                                 |
| 9  | Menu Option 3       | Text        | Admin Menu Options table  | "03. User Update (Security)"                              |
| 10 | Menu Option 4       | Text        | Admin Menu Options table  | "04. User Delete (Security)"                              |
| 11 | Menu Option 5       | Text        | Admin Menu Options table  | "05. Transaction Type List/Update (Db2)"                  |
| 12 | Menu Option 6       | Text        | Admin Menu Options table  | "06. Transaction Type Maintenance (Db2)"                  |
| 13 | Option Input        | 2-digit numeric | User input             | The administrator's selection                             |
| 14 | Message Area        | Up to 78 characters | System-generated    | Error or informational messages                           |

**Note:** The menu screen supports up to 12 option display slots (rows 6–17), but only 6 are currently populated. The remaining 6 slots are reserved for future expansion and appear blank.

---

## Section 5 — Screen / Interface Description

### Screen Layout

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ Tran: CA00       AWS Mainframe Modernization              Date: 05/07/26        │
│ Prog: COADM01C              CardDemo                      Time: 14:30:25        │
│                                                                                  │
│                                   Admin Menu                                     │
│                                                                                  │
│                    01. User List (Security)                                       │
│                    02. User Add (Security)                                        │
│                    03. User Update (Security)                                     │
│                    04. User Delete (Security)                                     │
│                    05. Transaction Type List/Update (Db2)                         │
│                    06. Transaction Type Maintenance (Db2)                         │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│               Please select an option : __                                       │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│ ENTER=Continue  F3=Exit                                                          │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Interface Elements

| Element              | Type    | Description                                                                 |
|----------------------|---------|-----------------------------------------------------------------------------|
| Transaction ID       | Display | Shows "CA00" — identifies this screen                                       |
| Program Name         | Display | Shows "COADM01C" — identifies the running program                           |
| Application Title    | Display | Two-line application branding ("AWS Mainframe Modernization" / "CardDemo")  |
| Current Date         | Display | Today's date in MM/DD/YY format                                             |
| Current Time         | Display | Current time in HH:MM:SS format                                             |
| "Admin Menu" Heading | Display | Screen title centered on row 4                                              |
| Menu Options 1–6     | Display | Numbered list of available administrative functions                          |
| Option Input Field   | Input   | 2-character numeric field where the administrator types their selection. Right-justified with zero-fill. Cursor is automatically positioned here on screen display |
| Message Area         | Display | 78-character area on row 23 for error messages (red) or informational messages (green) |
| Key Legend           | Display | "ENTER=Continue  F3=Exit" — shows available keyboard actions                |

### Available Actions

| Action          | How to Invoke                      | Description                                                                    |
|-----------------|------------------------------------|--------------------------------------------------------------------------------|
| Select Option   | Type a number (1–6) and press Enter | Navigates to the corresponding administrative function                          |
| Exit to Sign-on | Press F3                           | Returns to the Sign-on screen, ending the admin session                         |
| Continue        | Press Enter (with valid option)    | Synonym for Select Option — processes the entered option number                 |

---

## Section 6 — Alternative & Error Flows

### AF-1: Invalid Option Number Entered

| Step | Description                                                                                      |
|------|--------------------------------------------------------------------------------------------------|
| 1    | Administrator types a value that is non-numeric, zero, or greater than 6 in the option field      |
| 2    | Administrator presses Enter                                                                       |
| 3    | System validates the input and determines it is out of range or non-numeric                       |
| 4    | System displays the message: `Please enter a valid option number...`                              |
| 5    | Menu is redisplayed with the error message; cursor returns to the option input field              |
| 6    | Administrator can correct their entry and try again                                               |

### AF-2: Selected Function Not Installed

| Step | Description                                                                                      |
|------|--------------------------------------------------------------------------------------------------|
| 1    | Administrator types a valid option number (e.g., 5 or 6) for a function that is not installed     |
| 2    | Administrator presses Enter                                                                       |
| 3    | System validates the option number successfully                                                    |
| 4    | System attempts to transfer control to the target function                                         |
| 5    | System detects that the target function program does not exist in the runtime environment           |
| 6    | System displays the informational message (in green): `This option is not installed ...`           |
| 7    | Menu is redisplayed with the informational message; administrator can select a different option    |

### AF-3: Placeholder Program Selected

| Step | Description                                                                                      |
|------|--------------------------------------------------------------------------------------------------|
| 1    | Administrator types a valid option number that maps to a placeholder (dummy) program entry        |
| 2    | Administrator presses Enter                                                                       |
| 3    | System validates the option number successfully                                                    |
| 4    | System detects that the target program name is a placeholder                                      |
| 5    | System displays the informational message (in green): `This option is not installed ...`           |
| 6    | Menu is redisplayed; administrator can select a different option                                  |

### AF-4: Invalid Key Pressed

| Step | Description                                                                                      |
|------|--------------------------------------------------------------------------------------------------|
| 1    | Administrator presses any key other than Enter or F3 (e.g., F1, F2, F4 through F24, Clear, PA keys) |
| 2    | System displays the error message: `Invalid key pressed. Please see below...`                     |
| 3    | Menu is redisplayed with the error message; cursor returns to the option input field              |
| 4    | Administrator can press a valid key (Enter or F3) to proceed                                      |

### AF-5: Direct Access Without Session Context

| Step | Description                                                                                      |
|------|--------------------------------------------------------------------------------------------------|
| 1    | An attempt is made to invoke this screen's transaction directly (e.g., typing "CA00" at a blank terminal) without an active session |
| 2    | System detects that no session context is present                                                 |
| 3    | System immediately redirects the actor to the Sign-on screen without displaying the Admin Menu     |
| 4    | Actor must sign in before accessing the Admin Menu                                                |

### AF-6: Empty Option (Enter with No Input)

| Step | Description                                                                                      |
|------|--------------------------------------------------------------------------------------------------|
| 1    | Administrator presses Enter without typing any value in the option field                          |
| 2    | The input field defaults to "00" (right-justified, zero-filled)                                   |
| 3    | System validates the input as zero, which fails the "greater than zero" check                     |
| 4    | System displays the message: `Please enter a valid option number...`                              |
| 5    | Menu is redisplayed with the error message                                                        |

---

## Section 7 — Business Process Context

### Navigation Context

```
CardDemo Application Navigation Hierarchy
│
├── CC00 — Sign-on
│   │
│   ├── (Admin users)
│   │   │
│   │   └── ╔══════════════════════════════════╗
│   │       ║  CA00 — Admin Menu  <<< HERE >>> ║
│   │       ╚═══════════════╤══════════════════╝
│   │                       │
│   │           ┌───────────┼───────────┬───────────┐
│   │           │           │           │           │
│   │           v           v           v           v
│   │       CU00        CU01        CU02        CU03
│   │     User List   User Add   User Upd   User Del
│   │           │                               │
│   │           ├── (Select 'U') ──> CU02       │
│   │           └── (Select 'D') ──> CU03       │
│   │                                           │
│   │       Option 5: Transaction Type List/Update (not installed)
│   │       Option 6: Transaction Type Maintenance (not installed)
│   │
│   └── (Standard users)
│       │
│       └── CM00 — Main Menu
│           │
│           ├── Account View / Update
│           ├── Card List / Detail / Update
│           ├── Transaction List / View / Add
│           ├── Bill Payment
│           └── Reports
│
```

### Downstream Use Cases

| Destination                              | Trigger                          | What Happens Next                                                                      |
|------------------------------------------|----------------------------------|----------------------------------------------------------------------------------------|
| User List (CU00)                         | Admin selects option 1           | System displays a paginated list of all user security records; admin can select a user to update or delete |
| User Add (CU01)                          | Admin selects option 2           | System displays a blank form for entering a new user's ID, name, password, and role     |
| User Update (CU02)                       | Admin selects option 3           | System prompts for a user ID, retrieves the record, and allows the admin to modify fields |
| User Delete (CU03)                       | Admin selects option 4           | System prompts for a user ID, retrieves the record, displays it for confirmation, and deletes upon approval |
| Transaction Type List/Update             | Admin selects option 5           | Currently not installed — system displays informational message and returns to menu      |
| Transaction Type Maintenance             | Admin selects option 6           | Currently not installed — system displays informational message and returns to menu      |
| Sign-on Screen (CC00)                    | Admin presses F3                 | Session ends; the Sign-on screen is displayed for the next user                         |

### Upstream Use Cases

| Source                   | Navigation Path                                                                      |
|--------------------------|--------------------------------------------------------------------------------------|
| Sign-on (CC00)           | Admin user signs in successfully → system routes to Admin Menu based on admin role    |
| User List (CU00)         | Admin presses F3 in User List → returns to Admin Menu                                 |
| User Add (CU01)          | Admin presses F3 in User Add → returns to Admin Menu                                  |
| User Update (CU02)       | Admin presses F3 in User Update → returns to Admin Menu                               |
| User Delete (CU03)       | Admin presses F3 in User Delete → returns to Admin Menu                               |

### End-to-End Journey Examples

**Journey 1: Add a New Administrator**
```
Sign-on ──> Admin Menu ──(option 2)──> User Add ──(enter details)──> Admin Menu ──(F3)──> Sign-on
```
_Business scenario: Security officer creates a new admin user account for a newly hired team lead._

**Journey 2: Review and Update an Existing User**
```
Sign-on ──> Admin Menu ──(option 1)──> User List ──(select user)──> User Update ──(save)──> Admin Menu ──(F3)──> Sign-on
```
_Business scenario: Administrator reviews the user list and changes a user's role from standard to administrator._

**Journey 3: Remove a Departed Employee's Access**
```
Sign-on ──> Admin Menu ──(option 1)──> User List ──(select user)──> User Delete ──(confirm)──> Admin Menu ──(F3)──> Sign-on
```
_Business scenario: Security officer deactivates a terminated employee's account by deleting their user record._

**Journey 4: Attempt Unavailable Function, Then Perform Available Task**
```
Sign-on ──> Admin Menu ──(option 5)──> "Not installed" ──(option 1)──> User List ──(F3)──> Admin Menu ──(F3)──> Sign-on
```
_Business scenario: Administrator attempts to access Transaction Type management, discovers it is not installed, and instead reviews the user list._

---

## Section 8 — Acceptance Criteria

### Core Functionality

| #     | Criterion                                                                                                                                     |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| AC-01 | **Given** an administrator has signed in successfully, **When** the system routes to the Admin Menu, **Then** the screen displays six numbered menu options, the application title, the current date and time, and an input prompt |
| AC-02 | **Given** the Admin Menu is displayed, **When** the administrator types "1" and presses Enter, **Then** the system navigates to the User List function |
| AC-03 | **Given** the Admin Menu is displayed, **When** the administrator types "2" and presses Enter, **Then** the system navigates to the User Add function |
| AC-04 | **Given** the Admin Menu is displayed, **When** the administrator types "3" and presses Enter, **Then** the system navigates to the User Update function |
| AC-05 | **Given** the Admin Menu is displayed, **When** the administrator types "4" and presses Enter, **Then** the system navigates to the User Delete function |
| AC-06 | **Given** a downstream function (User List, User Add, User Update, or User Delete) is active, **When** the administrator presses F3, **Then** the system returns to the Admin Menu |

### Unavailable Functions

| #     | Criterion                                                                                                                                     |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| AC-07 | **Given** the Admin Menu is displayed, **When** the administrator selects an option whose target function is not installed, **Then** the system displays "This option is not installed ..." in green text and redisplays the menu |
| AC-08 | **Given** the Admin Menu is displayed, **When** the administrator selects an option whose target is a placeholder program, **Then** the system displays "This option is not installed ..." in green text and redisplays the menu |

### Input Validation

| #     | Criterion                                                                                                                                     |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| AC-09 | **Given** the Admin Menu is displayed, **When** the administrator types a non-numeric value and presses Enter, **Then** the system displays "Please enter a valid option number..." and redisplays the menu |
| AC-10 | **Given** the Admin Menu is displayed, **When** the administrator presses Enter without entering any value, **Then** the system displays "Please enter a valid option number..." and redisplays the menu (field defaults to zero) |
| AC-11 | **Given** the Admin Menu is displayed, **When** the administrator types "0" and presses Enter, **Then** the system displays "Please enter a valid option number..." and redisplays the menu |
| AC-12 | **Given** the Admin Menu is displayed, **When** the administrator types a number greater than 6 (e.g., "7", "99") and presses Enter, **Then** the system displays "Please enter a valid option number..." and redisplays the menu |

### Navigation

| #     | Criterion                                                                                                                                     |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| AC-13 | **Given** the Admin Menu is displayed, **When** the administrator presses F3, **Then** the system returns to the Sign-on screen |
| AC-14 | **Given** the Admin Menu is displayed, **When** the administrator presses any key other than Enter or F3, **Then** the system displays "Invalid key pressed. Please see below..." and redisplays the menu |

### Session Security

| #     | Criterion                                                                                                                                     |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| AC-15 | **Given** no session context exists (e.g., direct transaction invocation), **When** the Admin Menu transaction is started, **Then** the system immediately redirects to the Sign-on screen without displaying the menu |
| AC-16 | **Given** the Admin Menu is displayed, **When** the administrator selects a valid option, **Then** the session context is updated with the originating menu's identity before transferring to the downstream function |

### Data Integrity

| #     | Criterion                                                                                                                                     |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| AC-17 | **Given** the Admin Menu is displayed and the administrator selects a valid option, **When** the system transfers to the downstream function, **Then** no business data is read, written, or modified by the Admin Menu itself |
| AC-18 | **Given** the menu options are defined in a configuration table with 6 entries, **When** the Admin Menu is displayed, **Then** exactly 6 options are shown on screen, matching the configuration table's content and order |

---

## Section 9 — Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                          | Technical Reference                                                                                      |
|--------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| Session context validation on entry                    | `MAIN-PARA`: `IF EIBCALEN = 0` check (line 86), restores COMMAREA via `MOVE DFHCOMMAREA(1:EIBCALEN) TO CARDDEMO-COMMAREA` (line 90) |
| Redirect to Sign-on when no session                    | `RETURN-TO-SIGNON-SCREEN` paragraph: `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)` (lines 168–170)         |
| First-time display of menu                             | `MAIN-PARA`: `IF NOT CDEMO-PGM-REENTER` → `SET CDEMO-PGM-REENTER TO TRUE`, `MOVE LOW-VALUES TO COADM1AO`, `PERFORM SEND-MENU-SCREEN` (lines 91–94) |
| Receive user input                                     | `RECEIVE-MENU-SCREEN` paragraph: `EXEC CICS RECEIVE MAP('COADM1A') MAPSET('COADM01') INTO(COADM1AI) RESP(WS-RESP-CD) RESP2(WS-REAS-CD)` (lines 194–200) |
| Process Enter key                                      | `MAIN-PARA`: `EVALUATE EIBAID WHEN DFHENTER PERFORM PROCESS-ENTER-KEY` (lines 97–99)                    |
| Process F3 key (exit)                                  | `MAIN-PARA`: `WHEN DFHPF3 MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM PERFORM RETURN-TO-SIGNON-SCREEN` (lines 100–102) |
| Reject invalid keys                                    | `MAIN-PARA`: `WHEN OTHER MOVE 'Y' TO WS-ERR-FLG MOVE CCDA-MSG-INVALID-KEY TO WS-MESSAGE` (lines 103–106) |
| Trim and normalize option input                        | `PROCESS-ENTER-KEY`: trailing-space loop (lines 121–125), `INSPECT WS-OPTION-X REPLACING ALL ' ' BY '0'` (line 127), `MOVE WS-OPTION-X TO WS-OPTION` (line 128) |
| Validate option range                                  | `PROCESS-ENTER-KEY`: `IF WS-OPTION IS NOT NUMERIC OR WS-OPTION > CDEMO-ADMIN-OPT-COUNT OR WS-OPTION = ZEROS` (lines 131–133) |
| Display validation error                               | `PROCESS-ENTER-KEY`: `MOVE 'Please enter a valid option number...' TO WS-MESSAGE` (lines 135–136)       |
| Check for placeholder program                          | `PROCESS-ENTER-KEY`: `IF CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION)(1:5) NOT = 'DUMMY'` (line 141)              |
| Transfer to selected function                          | `PROCESS-ENTER-KEY`: `EXEC CICS XCTL PROGRAM(CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION)) COMMAREA(CARDDEMO-COMMAREA)` (lines 145–148) |
| Set navigation context before transfer                 | `PROCESS-ENTER-KEY`: `MOVE WS-TRANID TO CDEMO-FROM-TRANID`, `MOVE WS-PGMNAME TO CDEMO-FROM-PROGRAM`, `MOVE ZEROS TO CDEMO-PGM-CONTEXT` (lines 142–144) |
| Handle missing program                                 | `PGMIDERR-ERR-PARA` paragraph (lines 270–283): `EXEC CICS HANDLE CONDITION PGMIDERR(PGMIDERR-ERR-PARA)` registered at line 77–79 |
| Display "not installed" message                        | `PGMIDERR-ERR-PARA` and post-DUMMY check in `PROCESS-ENTER-KEY`: `STRING 'This option ' 'is not installed ...' INTO WS-MESSAGE` (lines 152–156, 273–277) |
| Populate screen header (date, time, titles)            | `POPULATE-HEADER-INFO` paragraph (lines 205–224): `FUNCTION CURRENT-DATE`, date/time formatting |
| Build menu option display text                         | `BUILD-MENU-OPTIONS` paragraph (lines 229–266): loop `VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > CDEMO-ADMIN-OPT-COUNT`, builds "NN. Description" strings |
| Send screen to terminal                                | `SEND-MENU-SCREEN` paragraph: `EXEC CICS SEND MAP('COADM1A') MAPSET('COADM01') FROM(COADM1AO) ERASE` (lines 182–187) |
| Pseudo-conversational return                           | `MAIN-PARA`: `EXEC CICS RETURN TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA)` (lines 111–114)          |

### Source File Mapping

| Business Concept                     | Source File(s)                                                               |
|--------------------------------------|------------------------------------------------------------------------------|
| Admin Menu program logic             | `app/cbl/COADM01C.cbl` (289 lines)                                          |
| Admin Menu screen definition         | `app/bms/COADM01.bms` (168 lines)                                           |
| Menu option configuration table      | `app/cpy/COADM02Y.cpy` (63 lines)                                           |
| Session context (shared data area)   | `app/cpy/COCOM01Y.cpy` (48 lines)                                           |
| Application title constants          | `app/cpy/COTTL01Y.cpy` (28 lines)                                           |
| Date/time formatting structure       | `app/cpy/CSDAT01Y.cpy` (59 lines)                                           |
| Common error message constants       | `app/cpy/CSMSG01Y.cpy` (25 lines)                                           |
| User security record layout          | `app/cpy/CSUSR01Y.cpy` (27 lines)                                           |
| BMS symbolic map (auto-generated)    | `app/cpy/COADM01.cpy` (generated from BMS)                                  |

### Migration Considerations

| #  | Consideration                                                                                                                                                                                         |
|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| M1 | **Pseudo-conversational to stateless:** The CICS pseudo-conversational pattern (RETURN TRANSID with COMMAREA) maps to stateless HTTP request/response in a modern web application. Session state currently carried in the COMMAREA would move to server-side session storage or JWT tokens |
| M2 | **COMMAREA to session/API context:** The CARDDEMO-COMMAREA structure (navigation fields, user identity, program context) maps to a session object or authentication token. The FROM-PROGRAM/TO-PROGRAM navigation model becomes URL routing or SPA navigation |
| M3 | **BMS screen to HTML/UI component:** The 24x80 BMS map (COADM01.bms) maps to a responsive web page or UI component. The fixed row/column layout becomes a flexible CSS layout. The 12-slot option grid could become a card-based or list-based menu |
| M4 | **Table-driven menu dispatch to route configuration:** The COADM02Y copybook (option table with program names) maps directly to a routing configuration file or menu component definition in a modern framework |
| M5 | **XCTL program transfer to page navigation:** Each EXEC CICS XCTL becomes a page navigation, API call, or component transition. The COMMAREA passed with XCTL becomes route parameters or shared state |
| M6 | **HANDLE CONDITION PGMIDERR to feature flags:** The graceful handling of missing programs via PGMIDERR maps to a feature flag system or runtime capability check in a modern application |
| M7 | **3270 AID keys to UI controls:** ENTER key maps to a submit button or form submission. F3 maps to a "Back" or "Logout" button. The "invalid key" handling has no direct equivalent — modern UIs inherently constrain available actions through visible buttons |
| M8 | **Plaintext passwords:** The downstream User Security Records store passwords in plaintext (SEC-USR-PWD in CSUSR01Y). Any modernized system must implement proper password hashing (e.g., bcrypt, Argon2). This is a critical security migration requirement |
| M9 | **No audit logging:** The current system does not log administrative actions (user creation, modification, deletion). Modern compliance requirements typically mandate audit trails for all security-related operations |
| M10 | **Hard-coded return target:** The return-to-signon path hard-codes the Sign-on program name ('COSGN00C'). In a modern system, this would be configuration-driven or handled by an authentication framework's logout mechanism |

---

## Section 10 — Related Use Cases / End-to-End Composition

| Journey Name                        | Use Cases Involved                                       | Business Scenario                                                                                           |
|-------------------------------------|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| New User Onboarding                 | UC-CC00-001 (Sign-on) → UC-CA00-001 (Admin Menu) → UC-CU01-001 (User Add) | Security officer logs in and creates a new user account with appropriate role (admin or standard)            |
| User Access Review                  | UC-CC00-001 → UC-CA00-001 → UC-CU00-001 (User List) → UC-CU02-001 (User Update) | Administrator reviews user list, identifies accounts needing role changes, and updates them                  |
| Employee Offboarding                | UC-CC00-001 → UC-CA00-001 → UC-CU00-001 → UC-CU03-001 (User Delete) | Administrator locates a departed employee's account in the user list and deletes it                          |
| Full User Lifecycle                 | UC-CC00-001 → UC-CA00-001 → UC-CU01-001 → UC-CA00-001 → UC-CU00-001 → UC-CU02-001 → UC-CA00-001 → UC-CU00-001 → UC-CU03-001 | Complete lifecycle: create user, later update their role, and eventually delete the account when no longer needed |
| Admin Session with Unavailable Feature | UC-CC00-001 → UC-CA00-001 (option 5 — not installed) → UC-CA00-001 (option 1) → UC-CU00-001 | Administrator attempts a feature not yet installed, receives informational message, and proceeds to an available function |

**Note:** Individual use case flows (UC-CC00-001, UC-CU00-001, UC-CU01-001, UC-CU02-001, UC-CU03-001) must each have their own Business Use Case Flow document created for complete end-to-end validation. The Transaction Type use cases (options 5 and 6) reference programs not present in the current repository and would need to be documented separately if the DB2 feature set is in scope.

---

## Document Footer — Summary Statistics

| Attribute                | Value                              |
|--------------------------|------------------------------------|
| **Source Program**       | COADM01C.cbl (289 lines)          |
| **Transaction ID**       | CA00                               |
| **Data Source**          | Technical Flow Analysis + COBOL source verification |
| **Acceptance Criteria**  | 18 (AC-01 through AC-18)          |
| **Business Rules**       | 8 (BR1 through BR8)               |
| **Validation Rules**     | 3 (V1 through V3)                 |
| **Alternative Flows**    | 6 (AF-1 through AF-6)             |
| **Migration Considerations** | 10 (M1 through M10)           |

# CU02 — User Update (Security/Admin) — Business Use Case Flow

## Section 1: Use Case Overview

| Attribute         | Value                                                                                      |
|-------------------|--------------------------------------------------------------------------------------------|
| **Use Case Name** | Update User (Security/Admin)                                                               |
| **Use Case ID**   | UC-CU02-001                                                                                |
| **Actor(s)**      | System Administrator                                                                       |
| **Business Goal** | Allow an administrator to modify an existing user's profile (name, password, user type)     |
| **Preconditions** | 1. Administrator is authenticated and signed in to the CardDemo application                 |
|                   | 2. Administrator has navigated to the Admin Menu                                            |
|                   | 3. The target user record exists in the User Security file                                  |
| **Postconditions**| The selected user's profile fields are updated in the User Security file                    |
| **Trigger**       | Administrator selects a user with the "Update" action from the User List screen, or enters a User ID directly on the Update User screen |
| **Data Access**   | Read-Write (User Security file)                                                            |
| **Frequency**     | On-demand — initiated whenever an administrator needs to modify user credentials or profile |

### Business Context

The **Update User** use case is part of the CardDemo application's security administration module. It enables system administrators to maintain user accounts by modifying profile attributes such as first name, last name, password, and user type (Administrator or Regular User). This is a critical administrative function that supports user lifecycle management, password resets, role changes, and name corrections.

This use case is only accessible to users with the Administrator role. It is typically reached through the User List screen, where the administrator selects a specific user for editing. The screen can also be used standalone by entering a User ID directly.

```
┌─────────────────────────────────────────────────────────────────────┐
│                  CardDemo — Functional Domains                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐                                                   │
│  │  Sign-On     │                                                   │
│  │  (CC00)      │                                                   │
│  └──────┬───────┘                                                   │
│         │                                                           │
│    ┌────┴────┐                                                      │
│    │         │                                                      │
│    ▼         ▼                                                      │
│  ┌──────┐ ┌──────────────────────────────────────────────────┐      │
│  │ Main │ │ Admin Menu (CA00)                                │      │
│  │ Menu │ │  ┌────────────┐ ┌──────────┐ ┌───────────────┐  │      │
│  │(CM00)│ │  │ User List  │ │ User Add │ │ User Delete   │  │      │
│  │      │ │  │ (CU00)     │ │ (CU01)   │ │ (CU03)        │  │      │
│  └──────┘ │  └─────┬──────┘ └──────────┘ └───────────────┘  │      │
│           │        │                                         │      │
│           │        ▼                                         │      │
│           │  ╔═════════════╗                                 │      │
│           │  ║ USER UPDATE ║  <── YOU ARE HERE               │      │
│           │  ║ (CU02)      ║                                 │      │
│           │  ╚═════════════╝                                 │      │
│           └──────────────────────────────────────────────────┘      │
│                                                                     │
│  Main Menu Domain: Account, Card, Transaction, Bill Pay, Reports    │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Section 2: User Journey (Happy Path)

### Sequence Diagram

```
┌──────────────┐                              ┌──────────────────┐
│ Administrator│                              │     System       │
└──────┬───────┘                              └────────┬─────────┘
       │                                               │
       │  1. Select user "U" from User List            │
       │──────────────────────────────────────────────>│
       │                                               │
       │  2. Display Update User screen with           │
       │     pre-populated user details                │
       │<──────────────────────────────────────────────│
       │     "Press PF5 key to save your updates ..."  │
       │                                               │
       │  3. Modify First Name, Last Name,             │
       │     Password, and/or User Type fields         │
       │──────────────────────────────────────────────>│
       │                                               │
       │  4. Press F5 (Save)                           │
       │──────────────────────────────────────────────>│
       │                                               │
       │  5. Validate all fields are non-empty         │
       │  6. Read current record from file             │
       │  7. Compare screen values to stored values    │
       │  8. Write updated record back to file         │
       │                                               │
       │  9. Display success confirmation              │
       │     "User {ID} has been updated ..."          │
       │<──────────────────────────────────────────────│
       │                                               │
       │ 10. Press F3 (Save & Exit) or F12 (Cancel)   │
       │──────────────────────────────────────────────>│
       │                                               │
       │ 11. Return to Admin Menu                      │
       │<──────────────────────────────────────────────│
       │                                               │
```

### Step-by-Step Narrative

| Step | Actor          | Action                                                      | System Response                                                                                                    |
|------|----------------|-------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| 1    | Administrator  | Selects a user with the "Update" flag from the User List    | Transfers to the Update User screen, passing the selected User ID                                                  |
| 2    | System         | —                                                           | Automatically looks up the user record, populates screen fields (User ID, First Name, Last Name, Password, User Type), and displays the message "Press PF5 key to save your updates ..." |
| 3    | Administrator  | Reviews the displayed user information                      | All editable fields are available for modification                                                                 |
| 4    | Administrator  | Modifies one or more fields (First Name, Last Name, Password, User Type) | Fields accept keyboard input                                                                                       |
| 5    | Administrator  | Presses F5 (Save)                                           | System validates that no editable field is empty                                                                   |
| 6    | System         | —                                                           | System reads the current user record from the file (with update lock) to get the latest version                    |
| 7    | System         | —                                                           | System compares each screen field against the stored record to detect changes                                      |
| 8    | System         | —                                                           | System writes the modified fields back to the User Security file                                                   |
| 9    | System         | —                                                           | Displays success message "User {ID} has been updated ..." in green text                                           |
| 10   | Administrator  | Presses F3 (Save & Exit) or F12 (Cancel)                   | System navigates back to the previous screen (Admin Menu or calling program)                                       |

---

## Section 3: Business Rules & Validations

### Input Validation Rules

| #  | Field      | Rule                                                    | Error Message Shown to User                          |
|----|------------|---------------------------------------------------------|------------------------------------------------------|
| V1 | User ID    | Must not be empty when fetching a user                  | "User ID can NOT be empty..."                        |
| V2 | User ID    | Must not be empty when saving changes                   | "User ID can NOT be empty..."                        |
| V3 | First Name | Must not be empty when saving changes                   | "First Name can NOT be empty..."                     |
| V4 | Last Name  | Must not be empty when saving changes                   | "Last Name can NOT be empty..."                      |
| V5 | Password   | Must not be empty when saving changes                   | "Password can NOT be empty..."                       |
| V6 | User Type  | Must not be empty when saving changes                   | "User Type can NOT be empty..."                      |

### Business Rules

| #   | Rule                                                                                                                                                 |
|-----|------------------------------------------------------------------------------------------------------------------------------------------------------|
| BR1 | **Administrator-Only Access** — Only users with the Administrator role may access the Update User screen. Navigation is gated through the Admin Menu. |
| BR2 | **Sequential Validation** — When saving, fields are validated in a fixed order: User ID, First Name, Last Name, Password, User Type. The first empty field stops validation, and only one error message is shown at a time. |
| BR3 | **Change Detection** — The system compares each screen field individually against the stored record. Only fields that have actually changed are written to the file. |
| BR4 | **No-Change Guard** — If the administrator presses Save but no fields have been modified, the system displays a warning message "Please modify to update ..." in red and does not write to the file. |
| BR5 | **Record Re-Read Before Write** — Before applying changes, the system re-reads the current record from the file to ensure it is working with the latest version. This provides a degree of optimistic concurrency control. |
| BR6 | **User ID Is Read-Only for Updates** — The User ID field is used to look up the record but is not itself modifiable. The primary key of the user record cannot be changed through this screen. |
| BR7 | **Pre-Selection from User List** — When the administrator arrives from the User List screen with a pre-selected user, the system automatically fetches and displays that user's details without requiring the administrator to press Enter. |
| BR8 | **Save & Exit Behavior** — Pressing F3 first attempts to save any pending changes (following the same validation and update logic as F5), then navigates back to the previous screen. |
| BR9 | **Password Display** — The password field is displayed as a dark (hidden) field on screen for visual privacy, but the value is transmitted in the data stream. |
| BR10 | **Session Safety** — If the system detects no communication area (session context is missing), it redirects the user to the Sign-On screen to re-authenticate. |

### Decision Table

```
┌──────────────────────┬───────────────────────────────────────────────────────┐
│ User Action          │ System Response                                       │
├──────────────────────┼───────────────────────────────────────────────────────┤
│ Enter (with empty    │ Display error "User ID can NOT be empty..."          │
│   User ID)           │ Cursor moves to User ID field                        │
├──────────────────────┼───────────────────────────────────────────────────────┤
│ Enter (with valid    │ Look up user record; if found, populate all fields   │
│   User ID)           │ and show "Press PF5 key to save your updates ..."    │
├──────────────────────┼───────────────────────────────────────────────────────┤
│ Enter (with unknown  │ Display error "User ID NOT found..."                 │
│   User ID)           │ Cursor moves to User ID field                        │
├──────────────────────┼───────────────────────────────────────────────────────┤
│ F5 (Save) with       │ Display first empty-field error; cursor moves to     │
│   empty field(s)     │ the first empty field                                │
├──────────────────────┼───────────────────────────────────────────────────────┤
│ F5 (Save) with       │ Re-read record, compare fields, write changes;      │
│   valid changes      │ display "User {ID} has been updated ..." in green    │
├──────────────────────┼───────────────────────────────────────────────────────┤
│ F5 (Save) with       │ Display "Please modify to update ..." in red         │
│   no changes         │ No file write occurs                                 │
├──────────────────────┼───────────────────────────────────────────────────────┤
│ F3 (Save & Exit)     │ Attempt save (same as F5 logic), then navigate       │
│                      │ back to the calling program or Admin Menu            │
├──────────────────────┼───────────────────────────────────────────────────────┤
│ F4 (Clear)           │ Clear all input fields and message area; cursor      │
│                      │ moves to User ID field                               │
├──────────────────────┼───────────────────────────────────────────────────────┤
│ F12 (Cancel)         │ Navigate back to Admin Menu without saving           │
├──────────────────────┼───────────────────────────────────────────────────────┤
│ Any other key        │ Display "Invalid key pressed. Please see below..."   │
└──────────────────────┴───────────────────────────────────────────────────────┘
```

---

## Section 4: Data Entities Involved

### Entity Descriptions

| Entity               | Description                                                                                      | Role in This Use Case                                |
|----------------------|--------------------------------------------------------------------------------------------------|------------------------------------------------------|
| User Security Record | Stores user credentials and profile information for application authentication and authorization | Read and updated — the primary entity being modified |

### Entity Relationships

```
┌───────────────────────────┐
│   User Security Record    │
│                           │
│  User ID (key)            │
│  First Name               │
│  Last Name                │
│  Password                 │
│  User Type (Admin/User)   │
└───────────────────────────┘
        │
        │ 1:1 — Each record represents
        │       one application user
        ▼
┌───────────────────────────┐
│   Application Session     │
│                           │
│  Authenticated User ID    │
│  User Type determines     │
│  menu access (Admin vs.   │
│  Regular User menus)      │
└───────────────────────────┘
```

### Data Fields Displayed

| #  | Field      | Format            | Source               | Description                                              |
|----|------------|-------------------|----------------------|----------------------------------------------------------|
| 1  | User ID    | 8 characters      | User Security Record | Unique identifier for the user; entered by administrator or pre-populated from User List |
| 2  | First Name | 20 characters     | User Security Record | User's given name                                        |
| 3  | Last Name  | 20 characters     | User Security Record | User's family name                                       |
| 4  | Password   | 8 characters      | User Security Record | User's login password; displayed as a hidden (dark) field |
| 5  | User Type  | 1 character       | User Security Record | Role indicator: "A" for Administrator, "U" for Regular User |

**Fields that exist in the underlying record but are NOT displayed:**

| Field  | Description                                           |
|--------|-------------------------------------------------------|
| Filler | 23-byte reserved area at the end of each user record  |

---

## Section 5: Screen / Interface Description

### Screen Layout

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ Tran: CU02   AWS Mainframe Modernization              Date: 05/07/26            │
│ Prog: COUSR02C           CardDemo                     Time: 14:30:00            │
│                                                                                  │
│                                  Update User                                     │
│                                                                                  │
│      Enter User ID: JSMITH01                                                     │
│                                                                                  │
│      **********************************************************************      │
│                                                                                  │
│                                                                                  │
│      First Name: John                  Last Name: Smith                          │
│                                                                                  │
│      Password: ******** (8 Char)                                                 │
│                                                                                  │
│      User Type:  A (A=Admin, U=User)                                             │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│                                                                                  │
│ Press PF5 key to save your updates ...                                           │
│ ENTER=Fetch  F3=Save&Exit  F4=Clear  F5=Save  F12=Cancel                        │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Interface Elements

| Element    | Type    | Description                                                                 |
|------------|---------|-----------------------------------------------------------------------------|
| User ID    | Input   | 8-character field to enter or display the user identifier; receives initial cursor focus |
| First Name | Input   | 20-character field for the user's given name                                |
| Last Name  | Input   | 20-character field for the user's family name                               |
| Password   | Input   | 8-character hidden field for the user's password (characters not visible on screen) |
| User Type  | Input   | 1-character field accepting "A" (Administrator) or "U" (Regular User)       |
| Message    | Display | 78-character status/error message area on row 23; color indicates status (green=success, red=warning/error, neutral=informational) |

### Available Actions

| Action      | How to Invoke | Description                                                          |
|-------------|---------------|----------------------------------------------------------------------|
| Fetch User  | Press Enter   | Looks up the entered User ID and displays the user's current details |
| Save        | Press F5      | Validates all fields and saves changes to the User Security file     |
| Save & Exit | Press F3      | Saves changes (same as F5), then returns to the previous screen      |
| Clear       | Press F4      | Clears all input fields and the message area                         |
| Cancel      | Press F12     | Returns to the Admin Menu without saving any changes                 |

---

## Section 6: Alternative & Error Flows

### Alt-1: User ID Not Found

| Step | Description                                                                         |
|------|-------------------------------------------------------------------------------------|
| 1    | Administrator enters a User ID that does not exist in the User Security file        |
| 2    | Administrator presses Enter                                                         |
| 3    | System attempts to look up the record and finds no match                            |
| 4    | System displays error message "User ID NOT found..."                                |
| 5    | Cursor is positioned on the User ID field for correction                            |

### Alt-2: Empty User ID on Fetch

| Step | Description                                                                         |
|------|-------------------------------------------------------------------------------------|
| 1    | Administrator leaves the User ID field empty and presses Enter                      |
| 2    | System detects the empty User ID before attempting a lookup                         |
| 3    | System displays error message "User ID can NOT be empty..."                         |
| 4    | Cursor is positioned on the User ID field                                           |

### Alt-3: Empty Required Field on Save

| Step | Description                                                                         |
|------|-------------------------------------------------------------------------------------|
| 1    | Administrator clears one or more required fields (First Name, Last Name, Password, or User Type) |
| 2    | Administrator presses F5 (Save) or F3 (Save & Exit)                                |
| 3    | System validates fields in order: User ID, First Name, Last Name, Password, User Type |
| 4    | System stops at the first empty field and displays the corresponding error message  |
| 5    | Cursor is positioned on the empty field for correction                              |
| 6    | Only one error message is shown per save attempt                                    |

### Alt-4: No Changes Detected on Save

| Step | Description                                                                         |
|------|-------------------------------------------------------------------------------------|
| 1    | Administrator fetches a user record and reviews it                                  |
| 2    | Administrator presses F5 (Save) without modifying any fields                        |
| 3    | System compares each screen field with the stored record and finds no differences   |
| 4    | System displays warning message "Please modify to update ..." in red                |
| 5    | No data is written to the file                                                      |

### Alt-5: Invalid Key Pressed

| Step | Description                                                                         |
|------|-------------------------------------------------------------------------------------|
| 1    | Administrator presses a function key that is not Enter, F3, F4, F5, or F12          |
| 2    | System displays error message "Invalid key pressed. Please see below..."            |
| 3    | Screen contents are preserved; administrator can retry with a valid key             |

### Alt-6: System I/O Error on Read

| Step | Description                                                                         |
|------|-------------------------------------------------------------------------------------|
| 1    | Administrator enters a valid User ID and presses Enter or Save                      |
| 2    | System attempts to read the user record from the file but encounters an I/O error   |
| 3    | System displays error message "Unable to lookup User..."                            |
| 4    | Cursor is positioned on the First Name field                                        |

### Alt-7: System I/O Error on Write

| Step | Description                                                                         |
|------|-------------------------------------------------------------------------------------|
| 1    | Administrator modifies fields and presses F5 (Save) or F3 (Save & Exit)            |
| 2    | System validates fields and re-reads the record successfully                        |
| 3    | System attempts to write the updated record but encounters an I/O error             |
| 4    | System displays error message "Unable to Update User..."                            |
| 5    | Cursor is positioned on the First Name field                                        |

### Alt-8: Session Context Missing

| Step | Description                                                                         |
|------|-------------------------------------------------------------------------------------|
| 1    | System detects that no session context was passed (e.g., session timed out)         |
| 2    | System redirects the administrator to the Sign-On screen to re-authenticate         |

### Alt-9: Clear Screen

| Step | Description                                                                         |
|------|-------------------------------------------------------------------------------------|
| 1    | Administrator presses F4 (Clear) at any point                                       |
| 2    | System clears all input fields (User ID, First Name, Last Name, Password, User Type) |
| 3    | System clears the message area                                                      |
| 4    | Cursor is positioned on the User ID field                                           |
| 5    | Administrator can now enter a new User ID to look up a different user               |

---

## Section 7: Business Process Context

### Navigation Context

```
CardDemo Application
│
├── Sign-On (CC00)
│   │
│   ├── Main Menu (CM00) ─── [Regular Users]
│   │   ├── Account View
│   │   ├── Account Update
│   │   ├── Card List
│   │   ├── Card Detail / Update
│   │   ├── Transaction List / View / Add
│   │   ├── Bill Payment
│   │   └── Reports
│   │
│   └── Admin Menu (CA00) ─── [Administrators]
│       ├── User List (CU00)
│       │   ├── ╔══════════════════════════╗
│       │   │   ║ >>> User Update (CU02) <<<║
│       │   │   ╚══════════════════════════╝
│       │   └── User Delete (CU03)
│       └── User Add (CU01)
```

### Downstream Use Cases

| Destination           | Trigger                      | What Happens Next                                                  |
|-----------------------|------------------------------|--------------------------------------------------------------------|
| Admin Menu (CA00)     | F12 (Cancel) or F3 after save | Administrator returns to the Admin Menu to select another function |
| Sign-On Screen (CC00) | Session context missing       | Administrator must re-authenticate to continue                     |
| Calling Program       | F3 (Save & Exit)             | Returns to whichever program originally invoked the Update User screen (typically User List) |

### Upstream Use Cases

| Source                | Navigation Path                                                          |
|-----------------------|--------------------------------------------------------------------------|
| User List (CU00)      | Administrator selects a user with the "U" (Update) flag on the User List |
| Admin Menu (CA00)     | Administrator reaches the User List first, then selects a user to update |
| Sign-On (CC00)        | Administrator signs in, which leads to the Admin Menu                    |

### End-to-End Journey Examples

**Journey 1: Password Reset**

```
Sign-On ──> Admin Menu ──> User List ──> User Update ──> Admin Menu
 (CC00)      (CA00)         (CU00)        (CU02)         (CA00)
```
*Business scenario: An administrator resets a user's forgotten password by navigating to the user's profile and entering a new password value.*

**Journey 2: Role Change (Promote User to Administrator)**

```
Sign-On ──> Admin Menu ──> User List ──> User Update ──> User List
 (CC00)      (CA00)         (CU00)        (CU02)         (CU00)
```
*Business scenario: An administrator promotes a regular user to administrator status by changing the User Type field from "U" to "A".*

**Journey 3: New User Onboarding (Create then Verify)**

```
Sign-On ──> Admin Menu ──> User Add ──> Admin Menu ──> User List ──> User Update
 (CC00)      (CA00)        (CU01)       (CA00)         (CU00)        (CU02)
```
*Business scenario: An administrator creates a new user account, then navigates to the User Update screen to verify the user's details were saved correctly and make any corrections.*

**Journey 4: User Offboarding (Review then Delete)**

```
Sign-On ──> Admin Menu ──> User List ──> User Update ──> User List ──> User Delete
 (CC00)      (CA00)         (CU00)        (CU02)         (CU00)        (CU03)
```
*Business scenario: An administrator reviews a departing user's profile on the Update screen to confirm identity, then returns to the User List and selects the user for deletion.*

---

## Section 8: Acceptance Criteria

### Core Functionality

| #     | Given                                                          | When                                                      | Then                                                                                     |
|-------|----------------------------------------------------------------|-----------------------------------------------------------|------------------------------------------------------------------------------------------|
| AC-01 | An administrator is on the Update User screen                  | They enter a valid, existing User ID and press Enter      | The system displays the user's First Name, Last Name, Password, and User Type            |
| AC-02 | A user's details are displayed on screen                       | The administrator modifies the First Name and presses F5  | The First Name is updated in the User Security file and a success message is displayed    |
| AC-03 | A user's details are displayed on screen                       | The administrator modifies the Last Name and presses F5   | The Last Name is updated in the User Security file and a success message is displayed     |
| AC-04 | A user's details are displayed on screen                       | The administrator modifies the Password and presses F5    | The Password is updated in the User Security file and a success message is displayed      |
| AC-05 | A user's details are displayed on screen                       | The administrator changes the User Type and presses F5    | The User Type is updated in the User Security file and a success message is displayed     |
| AC-06 | A user's details are displayed on screen                       | The administrator modifies multiple fields and presses F5 | All modified fields are updated in a single write; success message reads "User {ID} has been updated ..." |

### Input Validation

| #     | Given                                                          | When                                                      | Then                                                                                     |
|-------|----------------------------------------------------------------|-----------------------------------------------------------|------------------------------------------------------------------------------------------|
| AC-07 | The Update User screen is displayed                            | The administrator leaves User ID empty and presses Enter  | Error message "User ID can NOT be empty..." is shown and cursor moves to User ID field   |
| AC-08 | The administrator has fetched a user                           | They clear First Name and press F5                        | Error message "First Name can NOT be empty..." is shown and cursor moves to First Name   |
| AC-09 | The administrator has fetched a user                           | They clear Last Name and press F5                         | Error message "Last Name can NOT be empty..." is shown and cursor moves to Last Name     |
| AC-10 | The administrator has fetched a user                           | They clear Password and press F5                          | Error message "Password can NOT be empty..." is shown and cursor moves to Password       |
| AC-11 | The administrator has fetched a user                           | They clear User Type and press F5                         | Error message "User Type can NOT be empty..." is shown and cursor moves to User Type     |
| AC-12 | Multiple fields are empty                                      | The administrator presses F5                              | Only the first empty field (in order: User ID, First Name, Last Name, Password, User Type) triggers an error message |

### Navigation

| #     | Given                                                          | When                                                      | Then                                                                                     |
|-------|----------------------------------------------------------------|-----------------------------------------------------------|------------------------------------------------------------------------------------------|
| AC-13 | The Update User screen is displayed                            | The administrator presses F12 (Cancel)                    | The system returns to the Admin Menu without saving any changes                          |
| AC-14 | The administrator has modified fields                          | They press F3 (Save & Exit)                               | Changes are saved and then the system navigates back to the previous screen               |
| AC-15 | The Update User screen is displayed                            | The administrator presses F4 (Clear)                      | All input fields and the message area are cleared; cursor returns to User ID field        |
| AC-16 | A user was pre-selected from the User List                     | The Update User screen opens                              | The selected user's details are automatically fetched and displayed without pressing Enter |

### Data Integrity

| #     | Given                                                          | When                                                      | Then                                                                                     |
|-------|----------------------------------------------------------------|-----------------------------------------------------------|------------------------------------------------------------------------------------------|
| AC-17 | A user's details are displayed without any modifications       | The administrator presses F5 (Save)                       | Warning "Please modify to update ..." is shown in red; no data is written to the file    |
| AC-18 | The administrator enters a non-existent User ID                | They press Enter                                          | Error message "User ID NOT found..." is displayed and no record is modified              |
| AC-19 | The system encounters a file I/O error during read             | The read operation fails                                  | Error message "Unable to lookup User..." is displayed                                    |
| AC-20 | The system encounters a file I/O error during write            | The write operation fails                                 | Error message "Unable to Update User..." is displayed                                    |
| AC-21 | The session context is missing or has expired                  | The user attempts to access the Update User screen        | The system redirects to the Sign-On screen                                               |

### Error Handling

| #     | Given                                                          | When                                                      | Then                                                                                     |
|-------|----------------------------------------------------------------|-----------------------------------------------------------|------------------------------------------------------------------------------------------|
| AC-22 | The Update User screen is displayed                            | The administrator presses an unsupported function key      | Error message "Invalid key pressed. Please see below..." is displayed                    |

---

## Section 9: Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                    | Technical Reference                                                                                  |
|--------------------------------------------------|------------------------------------------------------------------------------------------------------|
| Session context check                            | `MAIN-PARA`: `IF EIBCALEN = 0` check; XCTL to COSGN00C                                             |
| First-entry initialization                       | `MAIN-PARA`: `IF NOT CDEMO-PGM-REENTER` block; sets PGM-CONTEXT=1, initializes COUSR2AO to LOW-VALUES |
| Pre-selected user auto-fetch                     | `MAIN-PARA`: checks `CDEMO-CU02-USR-SELECTED NOT = SPACES AND LOW-VALUES`; performs PROCESS-ENTER-KEY |
| Receive user input                               | `RECEIVE-USRUPD-SCREEN`: EXEC CICS RECEIVE MAP('COUSR2A') MAPSET('COUSR02')                         |
| Evaluate function key pressed                    | `MAIN-PARA`: `EVALUATE EIBAID` with DFHENTER, DFHPF3, DFHPF4, DFHPF5, DFHPF12, OTHER              |
| Validate User ID not empty (fetch)               | `PROCESS-ENTER-KEY`: `WHEN USRIDINI OF COUSR2AI = SPACES OR LOW-VALUES`                             |
| Clear detail fields before fetch                 | `PROCESS-ENTER-KEY`: MOVE SPACES to FNAMEI, LNAMEI, PASSWDI, USRTYPEI                               |
| Read user record (with update lock)              | `READ-USER-SEC-FILE`: EXEC CICS READ DATASET('USRSEC') INTO(SEC-USER-DATA) UPDATE RESP              |
| Handle record-not-found on read                  | `READ-USER-SEC-FILE`: `WHEN DFHRESP(NOTFND)` — sets ERR-FLG, message "User ID NOT found..."         |
| Handle I/O error on read                         | `READ-USER-SEC-FILE`: `WHEN OTHER` — DISPLAY RESP/REAS, message "Unable to lookup User..."          |
| Populate screen fields from record               | `PROCESS-ENTER-KEY`: MOVE SEC-USR-FNAME/LNAME/PWD/TYPE to COUSR2AI fields                           |
| Validate all fields not empty (save)             | `UPDATE-USER-INFO`: sequential EVALUATE checking USRIDINI, FNAMEI, LNAMEI, PASSWDI, USRTYPEI        |
| Compare screen values to stored record           | `UPDATE-USER-INFO`: four IF statements comparing FNAMEI/LNAMEI/PASSWDI/USRTYPEI to SEC-USR-* fields |
| Set modification flag                            | `UPDATE-USER-INFO`: `SET USR-MODIFIED-YES TO TRUE` for each changed field                            |
| No-change guard                                  | `UPDATE-USER-INFO`: `IF USR-MODIFIED-YES` / `ELSE` — message "Please modify to update ..."          |
| Write updated record                             | `UPDATE-USER-SEC-FILE`: EXEC CICS REWRITE DATASET('USRSEC') FROM(SEC-USER-DATA) RESP                |
| Success confirmation                             | `UPDATE-USER-SEC-FILE`: `WHEN DFHRESP(NORMAL)` — STRING 'User ' SEC-USR-ID ' has been updated ...'  |
| Handle I/O error on write                        | `UPDATE-USER-SEC-FILE`: `WHEN OTHER` — message "Unable to Update User..."                           |
| Clear screen (F4)                                | `CLEAR-CURRENT-SCREEN`: performs INITIALIZE-ALL-FIELDS then SEND-USRUPD-SCREEN                      |
| Initialize all fields                            | `INITIALIZE-ALL-FIELDS`: MOVE SPACES to all input fields; MOVE -1 to USRIDINL (cursor)              |
| Send screen to terminal                          | `SEND-USRUPD-SCREEN`: performs POPULATE-HEADER-INFO, then EXEC CICS SEND MAP('COUSR2A') ERASE CURSOR |
| Populate header (date, time, titles)             | `POPULATE-HEADER-INFO`: FUNCTION CURRENT-DATE, MOVE to TITLE01O/TITLE02O/TRNNAMEO/PGMNAMEO/CURDATEO/CURTIMEO |
| Return to previous screen                        | `RETURN-TO-PREV-SCREEN`: sets CDEMO-FROM-TRANID/PROGRAM, EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)  |
| Pseudo-conversational return                     | `MAIN-PARA` (end): EXEC CICS RETURN TRANSID('CU02') COMMAREA(CARDDEMO-COMMAREA)                    |
| Invalid key handling                             | `MAIN-PARA`: `WHEN OTHER` — sets ERR-FLG, MOVE CCDA-MSG-INVALID-KEY to WS-MESSAGE                   |
| Save & Exit (F3)                                 | `MAIN-PARA`: performs UPDATE-USER-INFO, resolves CDEMO-TO-PROGRAM, performs RETURN-TO-PREV-SCREEN    |

### Source File Mapping

| Business Concept                   | Source File(s)                                                                     |
|------------------------------------|------------------------------------------------------------------------------------|
| Update User program logic          | `app/cbl/COUSR02C.cbl` (415 lines)                                                |
| Screen layout and field definitions| `app/bms/COUSR02.bms`                                                             |
| User Security record structure     | `app/cpy/CSUSR01Y.cpy`                                                            |
| Session communication area         | `app/cpy/COCOM01Y.cpy` + inline CDEMO-CU02-INFO extension in COUSR02C.cbl         |
| Application screen titles          | `app/cpy/COTTL01Y.cpy`                                                             |
| Date and time formatting           | `app/cpy/CSDAT01Y.cpy`                                                             |
| Common error messages              | `app/cpy/CSMSG01Y.cpy`                                                             |
| BMS symbolic map structures        | Auto-generated copybook COUSR02 (COUSR2AI / COUSR2AO)                              |
| Function key constants             | System copybook DFHAID                                                             |
| Screen attribute constants         | System copybook DFHBMSCA                                                           |
| Upstream: User List program        | `app/cbl/COUSR00C.cbl`                                                             |
| Upstream: Admin Menu program       | `app/cbl/COADM01C.cbl`                                                             |
| Upstream: Sign-On program          | `app/cbl/COSGN00C.cbl`                                                             |

### Migration Considerations

| #  | Consideration                                                                                                                                                                                                             |
|----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| M1 | **Password Storage** — Passwords are stored as plain-text 8-character values. The modernized application must implement password hashing (e.g., bcrypt, Argon2) and enforce a configurable password policy (minimum length, complexity). |
| M2 | **Password Display** — The current dark-field display only hides the password visually on a 3270 terminal. In a web/API migration, passwords should never be returned in read responses; use masked placeholders instead.    |
| M3 | **Concurrent Access** — The current pessimistic locking (read-with-update-lock) is released between pseudo-conversational interactions. The modernized version should implement optimistic concurrency control (e.g., version numbers or ETags) to detect conflicting updates. |
| M4 | **Audit Trail** — No audit logging exists for user modifications. The modernized application should log all changes to user accounts (who changed what, when, from what value to what value) for security compliance.       |
| M5 | **Role Model** — The single-character User Type field ("A"/"U") provides only two roles. Migration should consider role-based access control (RBAC) with configurable permissions and potentially multiple roles per user.   |
| M6 | **Field-Level Authorization** — Any administrator can modify any field, including changing another admin's password or role. Consider implementing separation of duties (e.g., preventing self-promotion, requiring approval for role changes). |
| M7 | **Session Management** — The communication-area-based session context maps to server-side session or JWT-based authentication in a modern architecture. The missing-context redirect to Sign-On maps to session expiry handling. |
| M8 | **Screen Navigation** — The hard-coded program-name navigation (XCTL with COMMAREA) maps to URL routing or API endpoint calls. The FROM-PROGRAM/TO-PROGRAM pattern can be replaced with a navigation stack or browser history. |
| M9 | **File-Based Storage** — The keyed sequential file access pattern maps to database CRUD operations. The READ-UPDATE-REWRITE pattern becomes a database UPDATE with a WHERE clause on the primary key and optimistic locking.   |
| M10 | **Input Validation** — The sequential, one-error-at-a-time validation pattern should be modernized to validate all fields simultaneously and return all errors in a single response for better user experience.             |
| M11 | **User ID Immutability** — The primary key (User ID) cannot be changed through this screen. This business rule should be preserved in the migration, likely as a read-only field in the edit form.                          |

---

## Section 10: Related Use Cases / End-to-End Composition

| Journey Name                          | Use Cases Involved                                   | Business Scenario                                                                                                   |
|---------------------------------------|------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| Password Reset                        | CC00 (Sign-On), CA00 (Admin Menu), CU00 (User List), **CU02 (User Update)** | An administrator locates a user whose password needs resetting, navigates to their profile, and updates the password field |
| User Role Change                      | CC00, CA00, CU00, **CU02**                           | An administrator changes a user's type from Regular User to Administrator (or vice versa) to adjust system access    |
| New User Setup & Verification         | CC00, CA00, CU01 (User Add), CU00, **CU02**         | After creating a new user account, the administrator verifies the details on the Update screen and corrects any errors |
| User Offboarding                      | CC00, CA00, CU00, **CU02**, CU03 (User Delete)      | An administrator reviews a user's profile before deletion to confirm identity, then proceeds to delete the account   |
| Full User Lifecycle                   | CC00, CA00, CU01, CU00, **CU02**, CU03              | Complete lifecycle: create a user (CU01), review/update profile details (CU02), and eventually delete the account (CU03) |
| Bulk User Administration              | CC00, CA00, CU00, **CU02** (repeated)                | An administrator works through the User List, updating multiple user profiles in sequence (e.g., annual password rotation) |

> **Note:** Individual use case flows must be composed into journey-level documents for complete end-to-end validation. Use cases CU01 (User Add) and CU03 (User Delete) may not yet have their own Business Use Case Flow documents.

---

## Document Footer

| Attribute                  | Value                                               |
|----------------------------|-----------------------------------------------------|
| **Source Program**         | COUSR02C.cbl (415 lines)                            |
| **Transaction ID**         | CU02                                                |
| **Data Source**            | Technical Flow Analysis + COBOL source verification |
| **Acceptance Criteria**    | 22                                                  |
| **Business Rules**         | 10                                                  |
| **Validation Rules**       | 6                                                   |
| **Alternative/Error Flows**| 9                                                   |

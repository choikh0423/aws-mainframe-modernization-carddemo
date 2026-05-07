# COUSR03C — Business Use Case Flow Analysis

## User Delete (Security/Admin) — CardDemo Application

---

## Section 1: Use Case Overview

| Attribute        | Value                                                                 |
|------------------|-----------------------------------------------------------------------|
| **Use Case Name**    | Delete User Account                                               |
| **Use Case ID**      | UC-CU03-001                                                       |
| **Actor(s)**         | System Administrator                                              |
| **Business Goal**    | Permanently remove a user's security credentials from the system  |
| **Preconditions**    | 1. Administrator is authenticated and on the Admin Menu or User List screen. 2. The target user account exists in the system. |
| **Postconditions**   | The user account is permanently removed. The deleted user can no longer sign in. |
| **Trigger**          | Administrator selects "User Delete" from the Admin Menu (option 4) or selects a user with the "D" action from the User List screen |
| **Data Access**      | Read-Write (reads user details for confirmation, then deletes the record) |
| **Frequency**        | Low — performed only when decommissioning user accounts            |

### Business Context

The Delete User function is part of the CardDemo application's security administration module. It allows authorized administrators to permanently remove user accounts from the system. This is a destructive, irreversible operation — once a user is deleted, their credentials are gone and they can no longer sign in. The system enforces a two-step confirmation workflow: the administrator must first look up the user to review their details, then explicitly confirm the deletion with a separate action.

This use case is a terminal action in the user lifecycle — it follows user creation and any number of user updates, and represents the final disposition of a user account.

```
┌─────────────────────────────────────────────────────────────────┐
│                    CardDemo Functional Domains                  │
├─────────────────┬──────────────────┬────────────────────────────┤
│  Account Mgmt   │  Transaction     │  Security / Admin          │
│                 │  Processing      │                            │
│  - View Account │  - View Txns     │  - User List               │
│  - Update Acct  │  - Add Txn       │  - Add User                │
│                 │                  │  - Update User             │
│  Card Mgmt      │  Reporting       │  ╔══════════════════════╗  │
│  - List Cards   │  - Bill Payment  │  ║ ** DELETE USER **    ║  │
│  - Card Detail  │  - Reports       │  ║    (UC-CU03-001)     ║  │
│  - Update Card  │                  │  ╚══════════════════════╝  │
└─────────────────┴──────────────────┴────────────────────────────┘
```

---

## Section 2: User Journey (Happy Path)

### Sequence Diagram

```
  Administrator                          System
       │                                    │
       │  1. Select "User Delete"           │
       │  ─────────────────────────────────>│
       │                                    │  2. Display Delete User screen
       │                                    │     (empty form, cursor on User ID)
       │  <─────────────────────────────────│
       │                                    │
       │  3. Enter User ID, press Enter     │
       │  ─────────────────────────────────>│
       │                                    │  4. Look up user record
       │                                    │  5. Display user details
       │                                    │     (First Name, Last Name, Type)
       │                                    │     Message: "Press PF5 key to
       │                                    │              delete this user ..."
       │  <─────────────────────────────────│
       │                                    │
       │  6. Review details, press F5       │
       │  ─────────────────────────────────>│
       │                                    │  7. Re-read user record (lock)
       │                                    │  8. Delete user record
       │                                    │  9. Clear all fields
       │                                    │  10. Display success message:
       │                                    │      "User XXXXXXXX has been
       │                                    │       deleted ..."
       │  <─────────────────────────────────│
       │                                    │
       │  11. Press F3 (Back) or F12        │
       │      (Admin Menu)                  │
       │  ─────────────────────────────────>│
       │                                    │  12. Return to previous screen
       │  <─────────────────────────────────│
       │                                    │
```

### Step-by-Step Narrative

| Step | Actor         | Action                                          | System Response                                                                                     |
|------|---------------|-------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| 1    | Administrator | Selects "User Delete" from Admin Menu (option 4), or selects a user with "D" from User List | System navigates to the Delete User screen |
| 2    | System        | —                                               | Displays the Delete User screen. If a user was pre-selected from the User List, the User ID is pre-filled and user details are automatically fetched and displayed. Otherwise, an empty form is shown with the cursor on the User ID field. |
| 3    | Administrator | Types a User ID and presses Enter                | —                                                                                                   |
| 4    | System        | —                                               | Looks up the user in the security database. If found, displays the user's First Name, Last Name, and User Type (Admin or Regular User). Shows the prompt: "Press PF5 key to delete this user ..." |
| 5    | Administrator | Reviews the displayed user details to confirm this is the correct user | —                                                                                                   |
| 6    | Administrator | Presses F5 to confirm deletion                  | —                                                                                                   |
| 7    | System        | —                                               | Re-reads the user record to acquire an exclusive lock, then permanently deletes the user record from the security database. Clears all screen fields and displays: "User XXXXXXXX has been deleted ..." (where XXXXXXXX is the deleted User ID) in green text. |
| 8    | Administrator | Presses F3 to go back or F12 to return to Admin Menu | —                                                                                              |
| 9    | System        | —                                               | Navigates to the previous screen (the screen that invoked User Delete) or to the Admin Menu.        |

---

## Section 3: Business Rules & Validations

### Input Validation Rules

| #  | Field   | Rule                                     | Error Message Shown to User          |
|----|---------|------------------------------------------|--------------------------------------|
| V1 | User ID | Must not be empty (spaces or blank)      | "User ID can NOT be empty..."        |
| V2 | User ID | Must match an existing user in the system | "User ID NOT found..."              |

### Business Rules

| #   | Rule                                                                                                             |
|-----|------------------------------------------------------------------------------------------------------------------|
| BR1 | **Two-step confirmation required.** The administrator must first fetch (Enter key) the user record to review details before the system allows deletion (F5 key). The system does not allow a blind delete without first displaying the user's information. |
| BR2 | **Deletion is permanent.** Once confirmed, the user record is physically removed from the security database. There is no undo, recycle bin, or soft-delete mechanism. |
| BR3 | **Record-level locking.** The system acquires an exclusive lock on the user record before deleting it. This prevents another administrator from modifying the same record simultaneously. |
| BR4 | **Pre-selection from User List.** When a user is selected for deletion from the User List screen (with the "D" action), the User ID is automatically populated and looked up — the administrator does not need to type it manually. |
| BR5 | **No self-delete prevention.** The system does not prevent an administrator from deleting their own account. An administrator can delete the account they are currently signed in with. |
| BR6 | **No referential integrity check.** The system does not verify whether the user being deleted has active sessions, owns resources, or has dependencies elsewhere in the system. The delete proceeds unconditionally. |
| BR7 | **No audit trail.** The deletion is not logged. There is no record of who deleted the user or when. |
| BR8 | **Administrator access only.** This function is accessible only through the Admin Menu, which requires administrator-level authentication. |
| BR9 | **Password is never displayed.** Although the user record contains a password field, it is not shown on the Delete User screen. Only First Name, Last Name, and User Type are displayed for confirmation. |

### Decision Table

```
┌─────────────────────────────┬─────────────────────────────────────────────────┐
│ User Action                 │ System Response                                 │
├─────────────────────────────┼─────────────────────────────────────────────────┤
│ Enter key + empty User ID   │ Error: "User ID can NOT be empty..."            │
│ Enter key + valid User ID   │ Display user details + "Press PF5 key to        │
│                             │ delete this user ..."                           │
│ Enter key + unknown User ID │ Error: "User ID NOT found..."                   │
│ F5 key + empty User ID     │ Error: "User ID can NOT be empty..."            │
│ F5 key + valid User ID     │ Re-read + delete record; success message:       │
│                             │ "User xxx has been deleted ..."                 │
│ F5 key + unknown User ID   │ Error: "User ID NOT found..."                   │
│ F3 key                     │ Navigate to the screen that invoked User Delete  │
│                             │ (or Admin Menu if invoked directly)             │
│ F4 key                     │ Clear all fields; reset form to blank state      │
│ F12 key                    │ Navigate to Admin Menu                           │
│ Any other key              │ Error: "Invalid key pressed. Please see          │
│                             │ below..."                                       │
└─────────────────────────────┴─────────────────────────────────────────────────┘
```

---

## Section 4: Data Entities Involved

### Entity Descriptions

| Entity                | Description                                                  | Role in This Use Case                                          |
|-----------------------|--------------------------------------------------------------|----------------------------------------------------------------|
| User Security Record  | Stores user credentials and profile for system authentication. Each record contains a User ID (unique identifier), first name, last name, password, and user type (Administrator or Regular User). | This is the record that is looked up for confirmation and then permanently deleted. |

### Entity Relationships

```
┌──────────────────────┐
│  User Security       │
│  Record              │
│                      │        1              *
│  (User ID = key)     │───────────────────────── Sign-on Sessions
│                      │       "authenticates"    (validated at sign-on
│  Fields:             │                           by COSGN00C)
│  - User ID           │
│  - First Name        │
│  - Last Name         │
│  - Password          │
│  - User Type (A/U)   │
└──────────────────────┘
        │
        │  Note: No foreign-key relationships exist to other
        │  entities (accounts, cards, customers, transactions).
        │  The User Security Record is a standalone entity used
        │  solely for authentication and authorization.
        │
```

### Data Fields Displayed

| #  | Field      | Format               | Source                | Description                                      |
|----|------------|----------------------|-----------------------|--------------------------------------------------|
| 1  | User ID    | 8 alphanumeric chars | User input / pre-selected from User List | Unique identifier for the user account |
| 2  | First Name | Up to 20 characters  | User Security Record  | User's first name (display only)                 |
| 3  | Last Name  | Up to 20 characters  | User Security Record  | User's last name (display only)                  |
| 4  | User Type  | 1 character          | User Security Record  | "A" for Administrator, "U" for Regular User (display only) |

> **Note:** The User Security Record also contains a **Password** field (8 characters) and a **Filler** field (23 characters). Neither is displayed on the Delete User screen. The password is intentionally hidden for security.

---

## Section 5: Screen / Interface Description

### Screen Layout

```
+------------------------------------------------------------------------------+
|Tran: CU03  AWS Mainframe Modernization             Date: 05/07/26           |
|Prog: COUSR03C        CardDemo                      Time: 21:50:30           |
|                                                                              |
|                                  Delete User                                 |
|                                                                              |
|     Enter User ID: [ADMIN001]                                                |
|                                                                              |
|     **********************************************************************   |
|                                                                              |
|                                                                              |
|     First Name: [John                ]                                       |
|                                                                              |
|     Last Name:  [Smith               ]                                       |
|                                                                              |
|     User Type:  [A] (A=Admin, U=User)                                        |
|                                                                              |
|                                                                              |
|                                                                              |
|                                                                              |
|                                                                              |
|                                                                              |
|                                                                              |
| Press PF5 key to delete this user ...                                        |
| ENTER=Fetch  F3=Back  F4=Clear  F5=Delete                                   |
+------------------------------------------------------------------------------+
```

### Interface Elements

| Element      | Type    | Description                                                               |
|--------------|---------|---------------------------------------------------------------------------|
| User ID      | Input   | The only editable field. Accepts up to 8 characters. Cursor is positioned here by default. |
| First Name   | Display | Shows the user's first name after a successful lookup. Read-only.          |
| Last Name    | Display | Shows the user's last name after a successful lookup. Read-only.           |
| User Type    | Display | Shows "A" (Administrator) or "U" (Regular User) after a successful lookup. Read-only. Adjacent label "(A=Admin, U=User)" explains the codes. |
| Status/Error | Display | A message line near the bottom of the screen. Shows prompts ("Press PF5 key to delete this user ..."), success messages ("User xxx has been deleted ..."), or error messages ("User ID NOT found..."). Color changes: green for success, red for errors, neutral for prompts. |
| Key Legend   | Display | Bottom line showing available keyboard actions.                            |

### Available Actions

| Action         | How to Invoke | Description                                                                    |
|----------------|---------------|--------------------------------------------------------------------------------|
| Fetch User     | Enter key     | Looks up the user by the entered User ID and displays their details.           |
| Delete User    | F5 key        | Permanently deletes the currently displayed user after re-confirming the record exists. |
| Go Back        | F3 key        | Returns to the previous screen (the screen that navigated here — typically User List or Admin Menu). |
| Clear Form     | F4 key        | Clears all fields (User ID, First Name, Last Name, User Type, and any messages) and resets the form. |
| Admin Menu     | F12 key       | Returns directly to the Admin Menu, regardless of how this screen was reached.  |

---

## Section 6: Alternative & Error Flows

### Alt-1: User ID Not Found

| Step | Description                                                                                        |
|------|----------------------------------------------------------------------------------------------------|
| 1    | Administrator enters a User ID that does not exist in the system and presses Enter (or F5).        |
| 2    | System looks up the User ID in the security database and finds no matching record.                 |
| 3    | System displays the error message: "User ID NOT found..."                                          |
| 4    | Cursor is repositioned to the User ID field so the administrator can correct the entry.            |
| 5    | Administrator can re-enter a different User ID, clear the form (F4), or navigate away (F3/F12).    |

### Alt-2: Empty User ID Submitted

| Step | Description                                                                                        |
|------|----------------------------------------------------------------------------------------------------|
| 1    | Administrator presses Enter (or F5) without entering a User ID (the field is blank).               |
| 2    | System detects that the User ID field is empty before attempting any database lookup.               |
| 3    | System displays the error message: "User ID can NOT be empty..."                                   |
| 4    | Cursor is repositioned to the User ID field.                                                       |
| 5    | Administrator must enter a User ID before proceeding.                                              |

### Alt-3: User Deleted Between Fetch and Delete Confirmation

| Step | Description                                                                                        |
|------|----------------------------------------------------------------------------------------------------|
| 1    | Administrator fetches a user's details (Enter key) and the user is displayed successfully.          |
| 2    | Before the administrator presses F5, another administrator deletes the same user from a different terminal. |
| 3    | Administrator presses F5 to confirm deletion.                                                      |
| 4    | System attempts to re-read the user record to acquire a lock but finds it no longer exists.        |
| 5    | System displays the error message: "User ID NOT found..."                                          |
| 6    | The screen continues to display the previously fetched details (stale data) until the administrator takes further action. |

### Alt-4: System I/O Error During Lookup

| Step | Description                                                                                        |
|------|----------------------------------------------------------------------------------------------------|
| 1    | Administrator enters a User ID and presses Enter.                                                  |
| 2    | System encounters an unexpected I/O error while reading the security database.                     |
| 3    | System displays the error message: "Unable to lookup User..."                                      |
| 4    | Cursor is repositioned to the First Name field (not the User ID field — this is a known quirk).    |
| 5    | Administrator can retry, clear the form, or navigate away.                                         |

### Alt-5: System I/O Error During Delete

| Step | Description                                                                                        |
|------|----------------------------------------------------------------------------------------------------|
| 1    | Administrator presses F5 to confirm deletion after reviewing user details.                         |
| 2    | System successfully re-reads the user record (lock acquired), but encounters an unexpected I/O error during the delete operation. |
| 3    | System displays the error message: "Unable to Update User..."                                      |
| 4    | Cursor is repositioned to the First Name field.                                                    |
| 5    | The user record may still exist in the system (the delete did not complete). Administrator can retry or navigate away. |

### Alt-6: Invalid Key Pressed

| Step | Description                                                                                        |
|------|----------------------------------------------------------------------------------------------------|
| 1    | Administrator presses any key other than Enter, F3, F4, F5, or F12.                                |
| 2    | System displays the error message: "Invalid key pressed. Please see below..."                      |
| 3    | The screen is re-displayed with the current contents preserved.                                    |
| 4    | Administrator must use one of the valid keys to proceed.                                           |

### Alt-7: Clear Form (F4)

| Step | Description                                                                                        |
|------|----------------------------------------------------------------------------------------------------|
| 1    | Administrator presses F4 at any point while on the Delete User screen.                             |
| 2    | System clears the User ID, First Name, Last Name, User Type, and any status/error messages.        |
| 3    | Cursor is repositioned to the User ID field.                                                       |
| 4    | The screen is ready for a new user lookup.                                                         |

### Alt-8: Direct Terminal Access Without Authentication

| Step | Description                                                                                        |
|------|----------------------------------------------------------------------------------------------------|
| 1    | A user attempts to invoke transaction CU03 directly from a terminal without first signing in (no session context). |
| 2    | System detects that no session data was passed to the program.                                     |
| 3    | System immediately redirects the user to the Sign-On screen.                                       |
| 4    | The Delete User screen is never displayed.                                                         |

---

## Section 7: Business Process Context

### Navigation Context

```
┌─────────────────────────────────────────────────────────────────┐
│                     CardDemo Application                        │
│                                                                 │
│  [CC00] Sign-On                                                 │
│     │                                                           │
│     ├──> [CM00] Main Menu (Regular Users)                       │
│     │       ├──> Account View / Update                          │
│     │       ├──> Card List / Detail / Update                    │
│     │       ├──> Transaction List / View / Add                  │
│     │       ├──> Bill Payment                                   │
│     │       └──> Reports                                        │
│     │                                                           │
│     └──> [CA00] Admin Menu (Administrators)                     │
│             ├──> [CU00] User List                               │
│             │       ├──> [CU01] Add User                        │
│             │       ├──> [CU02] Update User                     │
│             │       └──> ╔═══════════════════════════════╗      │
│             │            ║ [CU03] DELETE USER  ◄── HERE  ║      │
│             │            ╚═══════════════════════════════╝      │
│             ├──> [CU01] Add User (direct)                       │
│             ├──> [CU02] Update User (direct)                    │
│             ├──> [CU03] Delete User (direct)  ◄── also HERE     │
│             └──> Transaction Type Management                    │
└─────────────────────────────────────────────────────────────────┘
```

### Downstream Use Cases

| Destination                     | Trigger                  | What Happens Next                                           |
|---------------------------------|--------------------------|-------------------------------------------------------------|
| Admin Menu (CA00)               | F12 key or F3 key (when no prior screen is recorded) | Administrator returns to the Admin Menu to select another administrative function. |
| Calling Screen (dynamic)        | F3 key                   | Administrator returns to whichever screen invoked User Delete (typically the User List or Admin Menu). |
| Sign-On Screen (CC00)           | No session context       | If the program is invoked without an active session, the system redirects to Sign-On. |

### Upstream Use Cases

| Source                          | Navigation Path                                              |
|---------------------------------|--------------------------------------------------------------|
| Admin Menu (CA00)               | Administrator selects option 4 ("User Delete (Security)")    |
| User List (CU00)                | Administrator selects a user with the "D" action code        |

### End-to-End Journey Examples

**Journey 1: Admin reviews user list and deletes an inactive user**
```
Sign-On ──> Admin Menu ──> User List ──> [scroll to find user] ──>
Select user with "D" ──> DELETE USER (auto-populated) ──>
Review details ──> F5 (confirm delete) ──> F3 (back to User List)
```
> *Business scenario: An administrator identifies a former employee's account in the user list and removes it from the system.*

**Journey 2: Admin deletes a user by known User ID**
```
Sign-On ──> Admin Menu ──> Delete User (option 4) ──>
Type User ID ──> Enter (fetch) ──> Review details ──>
F5 (confirm delete) ──> F12 (Admin Menu)
```
> *Business scenario: An administrator receives a termination notice with a specific User ID and navigates directly to delete it.*

**Journey 3: Admin attempts to delete a user that no longer exists**
```
Sign-On ──> Admin Menu ──> Delete User ──>
Type User ID ──> Enter (fetch) ──> "User ID NOT found..." ──>
F4 (clear) ──> Try different ID ──> F3 (back to Admin Menu)
```
> *Business scenario: An administrator tries to delete a user account that was already removed by another administrator.*

**Journey 4: Full user lifecycle — create, update, then delete**
```
Sign-On ──> Admin Menu ──> Add User (create new user) ──>
Admin Menu ──> Update User (modify user details) ──>
Admin Menu ──> DELETE USER (decommission the account) ──>
Admin Menu
```
> *Business scenario: A temporary contractor's account is created, updated with new permissions, and eventually deleted when their contract ends.*

---

## Section 8: Acceptance Criteria

### Core Functionality

| #     | Criterion                                                                                                      |
|-------|----------------------------------------------------------------------------------------------------------------|
| AC-01 | **Given** an administrator is on the Delete User screen, **When** they enter a valid User ID and press Enter, **Then** the system displays the user's First Name, Last Name, and User Type, and shows the message "Press PF5 key to delete this user ..." |
| AC-02 | **Given** a user's details are displayed on screen, **When** the administrator presses F5, **Then** the system permanently deletes the user record, clears all screen fields, and displays "User XXXXXXXX has been deleted ..." in green text (where XXXXXXXX is the deleted User ID) |
| AC-03 | **Given** a user has been deleted, **When** someone attempts to sign in with that User ID, **Then** authentication fails because the record no longer exists |
| AC-04 | **Given** a user is selected with the "D" action from the User List screen, **When** the Delete User screen is displayed, **Then** the User ID is automatically pre-filled and the user details are automatically fetched and displayed without requiring the administrator to press Enter |

### Input Validation

| #     | Criterion                                                                                                      |
|-------|----------------------------------------------------------------------------------------------------------------|
| AC-05 | **Given** the User ID field is empty, **When** the administrator presses Enter, **Then** the system displays "User ID can NOT be empty..." and positions the cursor on the User ID field |
| AC-06 | **Given** the User ID field is empty, **When** the administrator presses F5, **Then** the system displays "User ID can NOT be empty..." and positions the cursor on the User ID field |
| AC-07 | **Given** the administrator enters a User ID that does not exist, **When** they press Enter, **Then** the system displays "User ID NOT found..." and positions the cursor on the User ID field |
| AC-08 | **Given** the administrator enters a User ID that does not exist, **When** they press F5, **Then** the system displays "User ID NOT found..." |

### Navigation

| #     | Criterion                                                                                                      |
|-------|----------------------------------------------------------------------------------------------------------------|
| AC-09 | **Given** the administrator is on the Delete User screen, **When** they press F3, **Then** the system returns to the screen that originally invoked User Delete (User List or Admin Menu) |
| AC-10 | **Given** the administrator is on the Delete User screen, **When** they press F12, **Then** the system returns to the Admin Menu regardless of how the screen was reached |
| AC-11 | **Given** the administrator is on the Delete User screen, **When** they press F4, **Then** all fields (User ID, First Name, Last Name, User Type) and all messages are cleared, and the cursor is repositioned to the User ID field |
| AC-12 | **Given** the administrator is on the Delete User screen, **When** they press any key other than Enter, F3, F4, F5, or F12, **Then** the system displays "Invalid key pressed. Please see below..." and the screen contents are preserved |
| AC-13 | **Given** a user invokes transaction CU03 without an active session (no prior sign-on), **When** the system starts, **Then** the user is immediately redirected to the Sign-On screen |

### Data Integrity

| #     | Criterion                                                                                                      |
|-------|----------------------------------------------------------------------------------------------------------------|
| AC-14 | **Given** user details are displayed after a fetch, **When** the administrator presses F5, **Then** the system re-reads the record with an exclusive lock before deleting, ensuring no other session can modify the record simultaneously |
| AC-15 | **Given** another administrator deletes the same user between the fetch (Enter) and the delete confirmation (F5), **When** the first administrator presses F5, **Then** the system displays "User ID NOT found..." because the record no longer exists |
| AC-16 | **Given** a system I/O error occurs during the lookup, **When** the administrator presses Enter, **Then** the system displays "Unable to lookup User..." |
| AC-17 | **Given** a system I/O error occurs during the delete, **When** the administrator presses F5, **Then** the system displays "Unable to Update User..." and the user record is not deleted |

---

## Section 9: Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                           | Technical Reference                                                                                          |
|---------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| Display Delete User screen                              | `SEND-USRDEL-SCREEN` paragraph — `EXEC CICS SEND MAP('COUSR3A') MAPSET('COUSR03')` (line 219)               |
| Receive user input from screen                          | `RECEIVE-USRDEL-SCREEN` paragraph — `EXEC CICS RECEIVE MAP('COUSR3A') MAPSET('COUSR03')` (line 232)         |
| Validate User ID is not empty                           | `PROCESS-ENTER-KEY` paragraph — `EVALUATE TRUE WHEN USRIDINI OF COUSR3AI = SPACES OR LOW-VALUES` (line 145) |
| Fetch user details from database                        | `READ-USER-SEC-FILE` paragraph — `EXEC CICS READ DATASET(WS-USRSEC-FILE) ... UPDATE` (line 269)             |
| Display user details (First Name, Last Name, Type)      | `PROCESS-ENTER-KEY` paragraph — `MOVE SEC-USR-FNAME TO FNAMEI`, etc. (lines 165-168)                        |
| Validate User ID before delete                          | `DELETE-USER-INFO` paragraph — `EVALUATE TRUE WHEN USRIDINI ... = SPACES OR LOW-VALUES` (line 177)          |
| Re-read user record with lock before delete             | `DELETE-USER-INFO` calls `READ-USER-SEC-FILE` (line 190) which issues `EXEC CICS READ ... UPDATE`           |
| Delete user record from database                        | `DELETE-USER-SEC-FILE` paragraph — `EXEC CICS DELETE DATASET(WS-USRSEC-FILE)` (line 307)                    |
| Show success message with User ID                       | `DELETE-USER-SEC-FILE` paragraph — `STRING 'User ' ... SEC-USR-ID ... ' has been deleted ...'` (lines 318-321) |
| Clear all screen fields                                 | `INITIALIZE-ALL-FIELDS` paragraph — moves SPACES to all input fields and message (lines 349-356)            |
| Navigate back to calling screen (F3)                    | `RETURN-TO-PREV-SCREEN` paragraph — `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)` (line 205)                  |
| Navigate to Admin Menu (F12)                            | `MAIN-PARA` — `MOVE 'COADM01C' TO CDEMO-TO-PROGRAM` then `PERFORM RETURN-TO-PREV-SCREEN` (line 124)        |
| Handle invalid key press                                | `MAIN-PARA` — `WHEN OTHER` clause — `MOVE CCDA-MSG-INVALID-KEY TO WS-MESSAGE` (line 128)                    |
| Redirect to Sign-On when no session                     | `MAIN-PARA` — `IF EIBCALEN = 0` → `MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM` (lines 90-92)                      |
| Populate screen header (date, time, titles)             | `POPULATE-HEADER-INFO` paragraph — `MOVE FUNCTION CURRENT-DATE TO WS-CURDATE-DATA` (line 245)               |
| Pre-selection from User List                            | `MAIN-PARA` — checks `CDEMO-CU03-USR-SELECTED NOT = SPACES AND LOW-VALUES` (line 99)                        |
| Pseudo-conversational cycle management                  | `CDEMO-PGM-CONTEXT` flag: 0 = first entry, 1 = re-entry; `EXEC CICS RETURN TRANSID('CU03')` (line 134)     |

### Source File Mapping

| Business Concept             | Source File(s)                                                |
|------------------------------|---------------------------------------------------------------|
| Delete User program logic    | `app/cbl/COUSR03C.cbl` (360 lines)                           |
| Delete User screen layout    | `app/bms/COUSR03.bms` (mapset COUSR03, map COUSR3A)          |
| BMS symbolic map structures  | `app/cpy-bms/COUSR03.CPY` (COUSR3AI input, COUSR3AO output)  |
| Session/navigation context   | `app/cpy/COCOM01Y.cpy` (CARDDEMO-COMMAREA)                   |
| User security record layout  | `app/cpy/CSUSR01Y.cpy` (SEC-USER-DATA, 80 bytes)             |
| Screen title constants       | `app/cpy/COTTL01Y.cpy` (CCDA-TITLE01, CCDA-TITLE02)          |
| Date/time formatting         | `app/cpy/CSDAT01Y.cpy` (WS-CURDATE-DATA)                     |
| Common error messages        | `app/cpy/CSMSG01Y.cpy` (CCDA-MSG-INVALID-KEY)                |
| Admin Menu (upstream)        | `app/cbl/COADM01C.cbl` (option 4 routes to COUSR03C)         |
| User List (upstream)         | `app/cbl/COUSR00C.cbl` ("D" action routes to COUSR03C)       |
| Sign-On (session gate)       | `app/cbl/COSGN00C.cbl` (authentication entry point)          |

### Migration Considerations

| #  | Consideration                                                                                                                              |
|----|--------------------------------------------------------------------------------------------------------------------------------------------|
| M1 | **VSAM to relational database.** The USRSEC file (keyed sequential dataset) maps directly to a `users` or `user_security` table. The 8-byte User ID becomes the primary key. The fixed 80-byte record structure with a 23-byte filler should be normalized — the filler can be dropped and the password field should be replaced with a hashed/salted column. |
| M2 | **Pseudo-conversational to stateless request/response.** The CICS pseudo-conversational pattern (RETURN TRANSID with COMMAREA) maps to standard HTTP request/response or session-based web interactions. The `CDEMO-PGM-CONTEXT` flag is replaced by natural request routing. |
| M3 | **BMS screen to web form.** The 3270 terminal screen with one input field and three display fields maps to a simple web form. The function keys (Enter, F3, F4, F5, F12) map to buttons or keyboard shortcuts. |
| M4 | **Add audit logging.** The current system has no audit trail (BR7). A modernized version should log all delete operations with the administrator who performed the action, the timestamp, and the deleted user's details. |
| M5 | **Implement soft delete.** Replace permanent physical deletion (BR2) with a logical/soft delete (e.g., a "deleted" flag or status column) to allow recovery and maintain audit history. |
| M6 | **Add self-delete prevention.** The current system allows administrators to delete their own accounts (BR5). A modernized version should prevent this or require additional confirmation. |
| M7 | **Add referential integrity checks.** Before deletion, verify that the user has no active sessions, pending transactions, or other dependencies (BR6). |
| M8 | **XCTL navigation to web routing.** The XCTL-based navigation (to COADM01C, COSGN00C, or the FROM-PROGRAM) maps to standard URL routing, browser history navigation, or breadcrumb patterns. |
| M9 | **COMMAREA to session state.** The CARDDEMO-COMMAREA structure that carries navigation context and the pre-selected User ID maps to server-side session state, JWT claims, or URL query parameters. |
| M10 | **READ-for-UPDATE locking to database transactions.** The CICS pattern of READ with UPDATE option followed by DELETE maps to a database transaction with SELECT FOR UPDATE followed by DELETE, or optimistic concurrency control with version checking. |
| M11 | **Password storage.** The plain-text PIC X(08) password field must be replaced with proper password hashing (e.g., bcrypt, Argon2) during migration. This affects the entire authentication subsystem, not just the delete function. |
| M12 | **Concurrency error messaging.** The generic "NOT found" error when another administrator deletes the same record (Alt-3) should be replaced with a specific "This user was already deleted" message in the modernized version to improve the user experience. |

---

## Section 10: Related Use Cases / End-to-End Composition

| Journey Name                    | Use Cases Involved                                                     | Business Scenario                                                                      |
|---------------------------------|------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| User Decommissioning            | Sign-On (CC00) → Admin Menu (CA00) → User List (CU00) → **Delete User (CU03)** | Administrator browses the user list, identifies an inactive account, and removes it.   |
| Direct User Deletion            | Sign-On (CC00) → Admin Menu (CA00) → **Delete User (CU03)**           | Administrator knows the exact User ID and navigates directly to delete it without browsing the list. |
| Full User Lifecycle             | Sign-On → Admin Menu → Add User (CU01) → Update User (CU02) → **Delete User (CU03)** | A user account is created for a contractor, modified during their tenure, and deleted when they leave. |
| Bulk User Cleanup               | Sign-On → Admin Menu → User List (CU00) → **Delete User (CU03)** → User List → **Delete User** → ... | Administrator systematically reviews and deletes multiple inactive accounts, returning to the User List between each deletion. |
| Failed Deletion with Recovery   | Sign-On → Admin Menu → **Delete User (CU03)** [error] → Clear (F4) → Retry → **Delete User** [success] | Administrator encounters an error during deletion, clears the form, and retries successfully. |

> **Note:** Individual use case flows (Add User, Update User, User List) should be composed into complete journey documents for full end-to-end validation. The User List (CU00), Add User (CU01), and Update User (CU02) use cases may not yet have their own Business Use Case Flow documents — they should be created to enable comprehensive testing.

---

## Document Footer

| Attribute                    | Value                              |
|------------------------------|------------------------------------|
| **Source Program**           | COUSR03C.cbl (360 lines)          |
| **Transaction ID**           | CU03                               |
| **Data Source**              | Technical Flow Analysis + COBOL source verification |
| **Acceptance Criteria**      | 17 (AC-01 through AC-17)          |
| **Business Rules**           | 9 (BR1 through BR9)               |
| **Validation Rules**         | 2 (V1 through V2)                 |
| **Alternative/Error Flows**  | 8 (Alt-1 through Alt-8)           |

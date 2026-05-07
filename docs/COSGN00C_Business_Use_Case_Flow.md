# CICS Business Use Case Flow Analysis: CC00 (COSGN00C) — Sign-on / Login Screen

---

## Section 1: Use Case Overview

| Attribute          | Value                                                                 |
|--------------------|-----------------------------------------------------------------------|
| **Use Case Name**  | User Sign-on / Login                                                  |
| **Use Case ID**    | UC-CC00-001                                                           |
| **Actor(s)**       | All CardDemo users (Administrators and Regular Users)                 |
| **Business Goal**  | Authenticate a user and route them to the appropriate application menu |
| **Preconditions**  | 1. User has a valid account in the user security database             |
|                    | 2. The application is available and the login screen is displayed     |
| **Postconditions** | 1. User identity is verified and recorded for the session             |
|                    | 2. User is routed to the Admin Menu (administrators) or Main Menu (regular users) |
| **Trigger**        | User launches the CardDemo application at the terminal                |
| **Data Access**    | Read-only (user credentials are verified but never modified)          |
| **Frequency**      | Once per user session — every user must sign on before accessing any application function |

### Business Context

The Sign-on screen is the **single entry point** for the entire CardDemo credit card management application. Every user — whether an administrator managing user accounts and transaction types, or a regular user performing account inquiries, card management, transaction processing, bill payments, or reporting — must first authenticate through this screen. The sign-on process verifies the user's identity against the security database and determines their role, which controls which set of application functions they can access.

```
+================================================================+
|                  CardDemo Functional Domains                    |
+================================================================+
|                                                                 |
|  +--------------+                                               |
|  |  *** SIGN-ON *** |  <-- THIS USE CASE (UC-CC00-001)         |
|  |  (All Users) |                                               |
|  +------+-------+                                               |
|         |                                                       |
|    +----+----+                                                  |
|    |         |                                                  |
|    v         v                                                  |
| +--+------+  +--+--------+                                      |
| | Admin   |  | Main Menu |                                      |
| | Menu    |  | (Regular  |                                      |
| | (Admin  |  |  Users)   |                                      |
| |  Users) |  +--+--------+                                      |
| +--+------+     |                                               |
|    |            +--+--+--+--+--+--+                              |
|    |            |  |  |  |  |  |  |                              |
|    v            v  v  v  v  v  v  v                              |
| +--------+  +------+------+------+------+------+------+------+  |
| | User   |  | Acct | Card | Card | Txn  | Txn  | Bill | Rpt  |  |
| | Mgmt   |  | View | List | Upd  | List | Add  | Pay  | Gen  |  |
| | (CRUD) |  |      |      |      |      |      |      |      |  |
| +--------+  +------+------+------+------+------+------+------+  |
|                                                                 |
+=================================================================+
```

---

## Section 2: User Journey (Happy Path)

### Sequence Diagram

```
 Actor (User)                              System
 ============                              ======
      |                                       |
      |  1. Launch application                |
      |-------------------------------------->|
      |                                       |
      |  2. Display blank sign-on screen      |
      |<--------------------------------------|
      |     (cursor on User ID field)         |
      |                                       |
      |  3. Enter User ID                     |
      |-------------------------------------->|
      |                                       |
      |  4. Enter Password                    |
      |-------------------------------------->|
      |                                       |
      |  5. Press ENTER                       |
      |-------------------------------------->|
      |                                       |
      |  6. Validate inputs                   |
      |  7. Look up user in security database |
      |  8. Verify password                   |
      |  9. Determine user role               |
      |                                       |
      |  10. Navigate to appropriate menu     |
      |<--------------------------------------|
      |     (Admin Menu or Main Menu)         |
      |                                       |
```

### Step-by-Step Narrative

| Step | Actor  | Action                                  | System Response                                                                                           |
|------|--------|-----------------------------------------|-----------------------------------------------------------------------------------------------------------|
| 1    | User   | Launches the CardDemo application       | System displays the sign-on screen with a blank User ID field, a blank Password field, the application title, current date and time, and a decorative banner. The cursor is positioned on the User ID field. |
| 2    | User   | Types their User ID (up to 8 characters)| Characters appear in the User ID field as typed.                                                          |
| 3    | User   | Types their Password (up to 8 characters)| Password input is hidden (characters are not displayed on screen for security).                           |
| 4    | User   | Presses ENTER to submit credentials     | System receives the entered User ID and Password.                                                         |
| 5    | System | —                                       | System converts User ID and Password to uppercase for case-insensitive comparison.                        |
| 6    | System | —                                       | System validates that both User ID and Password are provided (not blank).                                 |
| 7    | System | —                                       | System looks up the User ID in the user security database.                                                |
| 8    | System | —                                       | System compares the entered password against the stored password for that user.                            |
| 9    | System | —                                       | System reads the user's role (Administrator or Regular User) from the security record.                    |
| 10   | System | —                                       | System records the user's identity and role for the session, then navigates to the Admin Menu (for administrators) or the Main Menu (for regular users). |

---

## Section 3: Business Rules & Validations

### Input Validation Rules

| #  | Field    | Rule                                                          | Error Message Shown to User                |
|----|----------|---------------------------------------------------------------|--------------------------------------------|
| V1 | User ID  | Must not be blank. User must enter at least one character.    | `"Please enter User ID ..."`               |
| V2 | Password | Must not be blank. User must enter at least one character.    | `"Please enter Password ..."`              |

### Business Rules

| #   | Rule                                                                                                  |
|-----|-------------------------------------------------------------------------------------------------------|
| BR1 | **Case-Insensitive Authentication** — User ID and Password are converted to uppercase before any validation or lookup. Users may enter credentials in any combination of upper and lower case. |
| BR2 | **User Must Exist** — The entered User ID must match an existing record in the user security database. If no matching user is found, login is rejected. |
| BR3 | **Password Must Match** — The entered password (after uppercase conversion) must exactly match the stored password for that user. If it does not match, login is rejected. |
| BR4 | **Role-Based Routing (Administrator)** — If the authenticated user's role is "Administrator," the system navigates to the Admin Menu, which provides access to user management and transaction type maintenance functions. |
| BR5 | **Role-Based Routing (Regular User)** — If the authenticated user's role is "Regular User," the system navigates to the Main Menu, which provides access to account viewing, card management, transaction processing, bill payment, and reporting functions. |
| BR6 | **Session Initialization** — Upon successful authentication, the system records the user's identity, role, and originating screen in the session context, and signals to the target menu that this is a fresh login (not a return from a sub-screen). |
| BR7 | **Exit Without Login** — A user may exit the application from the sign-on screen without authenticating. The system displays a farewell message and terminates the session. |

### Decision Table

```
+---------------------+-----------------------------------------------------------+
| User Action         | System Response                                           |
+=====================+===========================================================+
| Press ENTER with    | Display error: "Please enter User ID ..."                 |
| blank User ID       | Cursor returns to User ID field.                          |
+---------------------+-----------------------------------------------------------+
| Press ENTER with    | Display error: "Please enter Password ..."                |
| blank Password      | Cursor returns to Password field.                         |
| (User ID provided)  |                                                           |
+---------------------+-----------------------------------------------------------+
| Press ENTER with    | Display error: "User not found. Try again ..."            |
| unknown User ID     | Cursor returns to User ID field.                          |
+---------------------+-----------------------------------------------------------+
| Press ENTER with    | Display error: "Wrong Password. Try again ..."            |
| wrong Password      | Cursor returns to Password field.                         |
+---------------------+-----------------------------------------------------------+
| Press ENTER with    | Navigate to Admin Menu (for administrators)               |
| valid credentials   | or Main Menu (for regular users).                         |
+---------------------+-----------------------------------------------------------+
| Press F3            | Display farewell message:                                 |
|                     | "Thank you for using CardDemo application..."             |
|                     | Session terminates.                                       |
+---------------------+-----------------------------------------------------------+
| Press any other key | Display error:                                            |
|                     | "Invalid key pressed. Please see below..."                |
|                     | Sign-on screen is re-displayed.                           |
+---------------------+-----------------------------------------------------------+
| System cannot       | Display error: "Unable to verify the User ..."            |
| access security     | Cursor returns to User ID field.                          |
| database            |                                                           |
+---------------------+-----------------------------------------------------------+
```

---

## Section 4: Data Entities Involved

### Entity Descriptions

| Entity             | Description                                                             | Role in This Use Case                                  |
|--------------------|-------------------------------------------------------------------------|--------------------------------------------------------|
| User Security      | Stores user credentials and role information for all CardDemo users.    | Read-only — looked up by User ID to verify password and determine user role. |
| Session Context    | In-memory data passed between screens to maintain session state.        | Written — populated with authenticated user identity, role, and navigation origin after successful login. |

### Entity Relationships

```
+------------------+          +------------------+
|  User Security   |          |  Session Context |
|  (Persistent)    |          |  (In-Memory)     |
+------------------+          +------------------+
| User ID (key)    |--1---1-->| User ID          |
| First Name       |          | User Role        |
| Last Name        |          | Origin Screen    |
| Password         |          | Origin Tran ID   |
| Role (A/U)       |          | Context Flag     |
| (Filler)         |          | (other fields    |
+------------------+          |  for downstream   |
                              |  use cases)       |
                              +------------------+
```

### Data Fields Displayed

| #  | Field           | Format           | Source              | Description                                       |
|----|-----------------|------------------|---------------------|---------------------------------------------------|
| 1  | Transaction ID  | 4 characters     | System constant     | Always displays "CC00"                            |
| 2  | Program Name    | 8 characters     | System constant     | Always displays "COSGN00C"                        |
| 3  | Application ID  | 8 characters     | System runtime      | The identifier of the running application instance |
| 4  | System ID       | 8 characters     | System runtime      | The identifier of the system hosting the application |
| 5  | Current Date    | MM/DD/YY         | System clock        | Today's date                                      |
| 6  | Current Time    | HH:MM:SS         | System clock        | Current time                                      |
| 7  | Title Line 1    | 40 characters    | System constant     | "AWS Mainframe Modernization"                     |
| 8  | Title Line 2    | 40 characters    | System constant     | "CardDemo"                                        |
| 9  | User ID         | Up to 8 chars    | User input          | The user-entered login identifier                 |
| 10 | Password        | Up to 8 chars    | User input (hidden) | The user-entered password (not visible on screen) |
| 11 | Error Message   | Up to 78 chars   | System-generated    | Validation or authentication error message        |

> **Note:** The User Security record contains First Name and Last Name fields, but these are **not displayed** on the sign-on screen. They are stored for use by other application functions (e.g., user management screens).

---

## Section 5: Screen / Interface Description

### Screen Layout

```
+--------------------------------------------------------------------------------+
| Tran : CC00   AWS Mainframe Modernization          Date : 07/15/25             |
| Prog : COSGN00C          CardDemo                  Time : 14:30:22             |
| AppID: CARDDEMO                                    SysID: AWSD                 |
|                                                                                |
|      This is a Credit Card Demo Application for Mainframe Modernization        |
|                                                                                |
|                     +========================================+                 |
|                     |%%%%%%%  NATIONAL RESERVE NOTE  %%%%%%%%|                 |
|                     |%(1)  THE UNITED STATES OF KICSLAND (1)%|                 |
|                     |%$$              ___       ********  $$%|                 |
|                     |%$    {x}       (o o)                 $%|                 |
|                     |%$     ******  (  V  )      O N E     $%|                 |
|                     |%(1)          ---m-m---             (1)%|                 |
|                     |%%~~~~~~~~~~~ ONE DOLLAR ~~~~~~~~~~~~~%%|                 |
|                     +========================================+                 |
|                                                                                |
|                Type your User ID and Password, then press ENTER:               |
|                                                                                |
|                             User ID     : USER0001  (8 Char)                   |
|                             Password    : ________  (8 Char)                   |
|                                                                                |
|                                                                                |
| Wrong Password. Try again ...                                                  |
| ENTER=Sign-on  F3=Exit                                                         |
+--------------------------------------------------------------------------------+
```

### Interface Elements

| Element         | Type    | Description                                                                           |
|-----------------|---------|---------------------------------------------------------------------------------------|
| User ID         | Input   | 8-character text field where the user enters their login identifier. Cursor starts here. |
| Password        | Input   | 8-character text field where the user enters their password. Input is hidden (not displayed on screen). |
| Error Message   | Display | A message area at the bottom of the screen that shows validation errors, authentication failures, or status messages. Displayed in red with bright intensity. |
| Transaction ID  | Display | Shows the current transaction identifier ("CC00").                                    |
| Program Name    | Display | Shows the current program name ("COSGN00C").                                          |
| Title Lines     | Display | Two-line application banner: "AWS Mainframe Modernization" and "CardDemo".            |
| Date            | Display | Current date in MM/DD/YY format.                                                      |
| Time            | Display | Current time in HH:MM:SS format.                                                      |
| Application ID  | Display | Runtime identifier for the application instance.                                      |
| System ID       | Display | Runtime identifier for the hosting system.                                            |
| Dollar Bill Art | Display | Decorative ASCII art banner occupying the center of the screen (rows 7-15).           |
| Action Bar      | Display | Bottom row showing available keyboard actions: "ENTER=Sign-on  F3=Exit".              |

### Available Actions

| Action          | How to Invoke | Description                                                                      |
|-----------------|---------------|----------------------------------------------------------------------------------|
| Sign On         | Press ENTER   | Submits the entered User ID and Password for authentication.                     |
| Exit            | Press F3      | Exits the application, displays a farewell message, and ends the session.        |
| (Invalid)       | Any other key | Any key other than ENTER or F3 is rejected with an error message.                |

---

## Section 6: Alternative & Error Flows

### AF-1: User ID Not Provided

| Step | Description                                                                                          |
|------|------------------------------------------------------------------------------------------------------|
| 1    | User presses ENTER without entering a User ID (field is blank).                                      |
| 2    | System displays the error message: `"Please enter User ID ..."`                                      |
| 3    | The sign-on screen is re-displayed with the cursor positioned on the User ID field.                  |
| 4    | User may re-enter credentials or press F3 to exit.                                                   |

### AF-2: Password Not Provided

| Step | Description                                                                                          |
|------|------------------------------------------------------------------------------------------------------|
| 1    | User enters a User ID but presses ENTER without entering a Password (field is blank).                |
| 2    | System displays the error message: `"Please enter Password ..."`                                     |
| 3    | The sign-on screen is re-displayed with the cursor positioned on the Password field.                 |
| 4    | User may enter their password or press F3 to exit.                                                   |

### AF-3: User Not Found

| Step | Description                                                                                          |
|------|------------------------------------------------------------------------------------------------------|
| 1    | User enters a User ID and Password and presses ENTER.                                                |
| 2    | System converts both inputs to uppercase and looks up the User ID in the security database.          |
| 3    | No matching user record is found.                                                                    |
| 4    | System displays the error message: `"User not found. Try again ..."`                                 |
| 5    | The sign-on screen is re-displayed with the cursor positioned on the User ID field.                  |
| 6    | User may correct the User ID and try again, or press F3 to exit.                                     |

### AF-4: Wrong Password

| Step | Description                                                                                          |
|------|------------------------------------------------------------------------------------------------------|
| 1    | User enters a valid User ID and an incorrect Password and presses ENTER.                             |
| 2    | System converts both inputs to uppercase, looks up the user, and compares the passwords.             |
| 3    | The entered password does not match the stored password.                                             |
| 4    | System displays the error message: `"Wrong Password. Try again ..."`                                 |
| 5    | The sign-on screen is re-displayed with the cursor positioned on the Password field.                 |
| 6    | User may re-enter the password and try again, or press F3 to exit.                                   |

### AF-5: Security Database Unavailable

| Step | Description                                                                                          |
|------|------------------------------------------------------------------------------------------------------|
| 1    | User enters a User ID and Password and presses ENTER.                                                |
| 2    | System attempts to access the user security database but encounters a system-level error.            |
| 3    | System displays the error message: `"Unable to verify the User ..."`                                 |
| 4    | The sign-on screen is re-displayed with the cursor positioned on the User ID field.                  |
| 5    | User may try again later or contact a system administrator.                                          |

### AF-6: Invalid Key Pressed

| Step | Description                                                                                          |
|------|------------------------------------------------------------------------------------------------------|
| 1    | User presses any key other than ENTER or F3 (e.g., F1, F2, F4-F12, PA keys, CLEAR).                 |
| 2    | System displays the error message: `"Invalid key pressed. Please see below..."`                      |
| 3    | The sign-on screen is re-displayed. The user may press ENTER to sign on or F3 to exit.               |

### AF-7: User Exits Application

| Step | Description                                                                                          |
|------|------------------------------------------------------------------------------------------------------|
| 1    | User presses F3 at any point on the sign-on screen.                                                  |
| 2    | System displays the farewell message: `"Thank you for using CardDemo application..."`                |
| 3    | The session is terminated. The terminal returns to the system prompt.                                |

---

## Section 7: Business Process Context

### Navigation Context

```
CardDemo Application
|
+-- >>> SIGN-ON (CC00 / COSGN00C) <<<   <-- THIS USE CASE
|   |
|   +-- [Admin Users] --> Admin Menu (CA00)
|   |   |
|   |   +-- User List
|   |   +-- User Add
|   |   +-- User Update
|   |   +-- User Delete
|   |   +-- Transaction Type List/Update
|   |   +-- Transaction Type Maintenance
|   |
|   +-- [Regular Users] --> Main Menu (CM00)
|       |
|       +-- Account View
|       +-- Account Update
|       +-- Credit Card List
|       +-- Credit Card View
|       +-- Credit Card Update
|       +-- Transaction List
|       +-- Transaction View
|       +-- Transaction Add
|       +-- Transaction Reports
|       +-- Bill Payment
|       +-- Pending Authorization View
|
+-- [F3 Exit] --> Session Terminated
```

### Downstream Use Cases

| Destination                  | Trigger                               | What Happens Next                                                        |
|------------------------------|---------------------------------------|--------------------------------------------------------------------------|
| Admin Menu (CA00)            | Successful login as Administrator     | User sees the admin menu with options for user management and transaction type maintenance. |
| Main Menu (CM00)             | Successful login as Regular User      | User sees the main menu with options for account inquiries, card management, transactions, payments, and reports. |

### Upstream Use Cases

| Source                       | Navigation Path                                                                |
|------------------------------|--------------------------------------------------------------------------------|
| Terminal / System Prompt     | User types the transaction code "CC00" to launch the application.              |
| Admin Menu (CA00)            | Administrator presses F3 to log out; system returns to the sign-on screen.     |
| Main Menu (CM00)             | Regular user presses F3 to log out; system returns to the sign-on screen.      |
| Various Sub-Screens          | Pressing F3 or exiting from any application screen eventually returns to sign-on through the menu chain. |

### End-to-End Journey Examples

**Journey 1: Administrator Manages a User Account**

```
Sign-on --> Admin Menu --> User List --> User Update --> Admin Menu --> Sign-on (logout)
```
> Business Scenario: An administrator logs in, navigates to the user list, updates a user's information, returns to the admin menu, and logs out.

**Journey 2: Regular User Views Account and Pays a Bill**

```
Sign-on --> Main Menu --> Account View --> Main Menu --> Bill Payment --> Main Menu --> Sign-on (logout)
```
> Business Scenario: A cardholder logs in, views their account details, pays their credit card bill, and logs out.

**Journey 3: Regular User Processes a Transaction and Generates a Report**

```
Sign-on --> Main Menu --> Transaction Add --> Main Menu --> Transaction Reports --> Main Menu --> Sign-on (logout)
```
> Business Scenario: An operator logs in, adds a new credit card transaction, generates a transaction report for review, and logs out.

**Journey 4: Failed Login Recovery**

```
Sign-on (wrong password) --> Sign-on (re-enter) --> Main Menu
```
> Business Scenario: A user enters the wrong password, sees an error message, re-enters the correct credentials, and proceeds to the main menu.

---

## Section 8: Acceptance Criteria

### Core Functionality

| #     | Given                                                  | When                                           | Then                                                                                       |
|-------|--------------------------------------------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------|
| AC-01 | The application is started                             | The sign-on screen loads for the first time     | A blank sign-on screen is displayed with the cursor on the User ID field, showing the current date, time, application title, and action bar. |
| AC-02 | A valid Administrator User ID and correct Password are entered | The user presses ENTER                    | The user is authenticated and navigated to the Admin Menu.                                 |
| AC-03 | A valid Regular User ID and correct Password are entered | The user presses ENTER                       | The user is authenticated and navigated to the Main Menu.                                  |
| AC-04 | The user is on the sign-on screen                      | The user presses F3                             | The farewell message `"Thank you for using CardDemo application..."` is displayed and the session ends. |

### Input Validation

| #     | Given                                                  | When                                           | Then                                                                                       |
|-------|--------------------------------------------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------|
| AC-05 | The User ID field is blank                             | The user presses ENTER                          | The error message `"Please enter User ID ..."` is displayed and the cursor is on the User ID field. |
| AC-06 | The User ID is entered but the Password field is blank | The user presses ENTER                          | The error message `"Please enter Password ..."` is displayed and the cursor is on the Password field. |
| AC-07 | The user enters credentials in lowercase               | The user presses ENTER                          | The system treats them identically to uppercase — authentication succeeds if credentials are otherwise correct. |

### Authentication

| #     | Given                                                  | When                                           | Then                                                                                       |
|-------|--------------------------------------------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------|
| AC-08 | A User ID is entered that does not exist in the security database | The user presses ENTER                | The error message `"User not found. Try again ..."` is displayed and the cursor is on the User ID field. |
| AC-09 | A valid User ID is entered with an incorrect Password  | The user presses ENTER                          | The error message `"Wrong Password. Try again ..."` is displayed and the cursor is on the Password field. |
| AC-10 | The security database is unavailable or a system error occurs | The user presses ENTER                     | The error message `"Unable to verify the User ..."` is displayed and the cursor is on the User ID field. |

### Navigation

| #     | Given                                                  | When                                           | Then                                                                                       |
|-------|--------------------------------------------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------|
| AC-11 | The user presses any key other than ENTER or F3        | The key is processed                            | The error message `"Invalid key pressed. Please see below..."` is displayed and the sign-on screen is re-displayed. |
| AC-12 | A login error occurs (any of AC-05 through AC-10)      | The error screen is displayed                   | The user can immediately re-attempt login by entering credentials and pressing ENTER, or exit with F3. |
| AC-13 | The user successfully authenticates                    | The target menu is displayed                    | The session context contains the correct user identity, role, and origin screen information. |

### Data Integrity

| #     | Given                                                  | When                                           | Then                                                                                       |
|-------|--------------------------------------------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------|
| AC-14 | The user signs on successfully                         | The session context is examined                 | The user identity and role in the session match the values stored in the security database. |
| AC-15 | The user enters credentials and the sign-on screen is re-displayed due to an error | The security database is examined | No records in the security database have been created, modified, or deleted (read-only access). |

### Screen Display

| #     | Given                                                  | When                                           | Then                                                                                       |
|-------|--------------------------------------------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------|
| AC-16 | The sign-on screen is displayed                        | The user observes the Password field             | Characters typed into the Password field are not visible on screen (hidden/dark input).    |
| AC-17 | The sign-on screen is displayed                        | The user observes the screen layout              | The screen shows: transaction ID, program name, application ID, system ID, current date, current time, application title, decorative banner, login prompt, User ID field, Password field, and action bar. |

---

## Section 9: Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                      | Technical Reference                                                                                   |
|----------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| Display blank sign-on screen (first visit)         | `MAIN-PARA`: When `EIBCALEN = 0`, `MOVE LOW-VALUES TO COSGN0AO`, `MOVE -1 TO USERIDL OF COSGN0AI`, then `PERFORM SEND-SIGNON-SCREEN` |
| Receive user input                                 | `PROCESS-ENTER-KEY`: `EXEC CICS RECEIVE MAP('COSGN0A') MAPSET('COSGN00')`                            |
| Validate User ID not blank (V1)                    | `PROCESS-ENTER-KEY`: `WHEN USERIDI OF COSGN0AI = SPACES OR LOW-VALUES` (line 118)                    |
| Validate Password not blank (V2)                   | `PROCESS-ENTER-KEY`: `WHEN PASSWDI OF COSGN0AI = SPACES OR LOW-VALUES` (line 123)                    |
| Convert inputs to uppercase (BR1)                  | `PROCESS-ENTER-KEY`: `MOVE FUNCTION UPPER-CASE(USERIDI OF COSGN0AI) TO WS-USER-ID` (line 132); `MOVE FUNCTION UPPER-CASE(PASSWDI OF COSGN0AI) TO WS-USER-PWD` (line 135) |
| Look up user in security database (BR2)            | `READ-USER-SEC-FILE`: `EXEC CICS READ DATASET(WS-USRSEC-FILE) INTO(SEC-USER-DATA) RIDFLD(WS-USER-ID) KEYLENGTH(LENGTH OF WS-USER-ID)` |
| Compare password (BR3)                             | `READ-USER-SEC-FILE`: `IF SEC-USR-PWD = WS-USER-PWD` (line 223)                                      |
| Route admin to Admin Menu (BR4)                    | `READ-USER-SEC-FILE`: `IF CDEMO-USRTYP-ADMIN` then `EXEC CICS XCTL PROGRAM('COADM01C') COMMAREA(CARDDEMO-COMMAREA)` (lines 230-234) |
| Route regular user to Main Menu (BR5)              | `READ-USER-SEC-FILE`: `EXEC CICS XCTL PROGRAM('COMEN01C') COMMAREA(CARDDEMO-COMMAREA)` (lines 236-239) |
| Initialize session context (BR6)                   | `READ-USER-SEC-FILE`: `MOVE WS-TRANID TO CDEMO-FROM-TRANID`, `MOVE WS-PGMNAME TO CDEMO-FROM-PROGRAM`, `MOVE WS-USER-ID TO CDEMO-USER-ID`, `MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE`, `MOVE ZEROS TO CDEMO-PGM-CONTEXT` (lines 224-228) |
| Exit application (BR7)                             | `MAIN-PARA`: `WHEN DFHPF3` then `MOVE CCDA-MSG-THANK-YOU TO WS-MESSAGE`, `PERFORM SEND-PLAIN-TEXT` (lines 88-90); `SEND-PLAIN-TEXT`: `EXEC CICS SEND TEXT`, `EXEC CICS RETURN` (without TRANSID) |
| Handle invalid key                                 | `MAIN-PARA`: `WHEN OTHER` then `MOVE CCDA-MSG-INVALID-KEY TO WS-MESSAGE`, `PERFORM SEND-SIGNON-SCREEN` (lines 91-94) |
| Handle user not found                              | `READ-USER-SEC-FILE`: `WHEN 13` (NOTFND RESP code) then `MOVE 'User not found. Try again ...' TO WS-MESSAGE` (lines 247-251) |
| Handle wrong password                              | `READ-USER-SEC-FILE`: password mismatch branch: `MOVE 'Wrong Password. Try again ...' TO WS-MESSAGE` (lines 242-245) |
| Handle system error on security database           | `READ-USER-SEC-FILE`: `WHEN OTHER` then `MOVE 'Unable to verify the User ...' TO WS-MESSAGE` (lines 252-256) |
| Populate screen header (date, time, titles)        | `POPULATE-HEADER-INFO`: `MOVE FUNCTION CURRENT-DATE TO WS-CURDATE-DATA`, `EXEC CICS ASSIGN APPLID(...)`, `EXEC CICS ASSIGN SYSID(...)` (lines 177-204) |
| Send sign-on screen to terminal                    | `SEND-SIGNON-SCREEN`: `EXEC CICS SEND MAP('COSGN0A') MAPSET('COSGN00') FROM(COSGN0AO) ERASE CURSOR` |
| Maintain pseudo-conversational loop                | `MAIN-PARA`: `EXEC CICS RETURN TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA) LENGTH(LENGTH OF CARDDEMO-COMMAREA)` (lines 98-102) |

### Source File Mapping

| Business Concept                 | Source File(s)                                                         |
|----------------------------------|------------------------------------------------------------------------|
| Sign-on program logic            | `app/cbl/COSGN00C.cbl` (261 lines)                                    |
| Screen layout and field definitions | `app/bms/COSGN00.bms` (mapset COSGN00, map COSGN0A)                |
| Session context structure        | `app/cpy/COCOM01Y.cpy` (CARDDEMO-COMMAREA, 160 bytes)                 |
| User security record layout      | `app/cpy/CSUSR01Y.cpy` (SEC-USER-DATA, 80 bytes)                      |
| Application title constants      | `app/cpy/COTTL01Y.cpy` (CCDA-TITLE01, CCDA-TITLE02)                   |
| Date/time formatting             | `app/cpy/CSDAT01Y.cpy` (WS-DATE-TIME)                                 |
| Common messages (farewell, invalid key) | `app/cpy/CSMSG01Y.cpy` (CCDA-MSG-THANK-YOU, CCDA-MSG-INVALID-KEY) |
| AID key constants                | `DFHAID` (IBM CICS system copybook)                                    |
| BMS attribute constants          | `DFHBMSCA` (IBM CICS system copybook)                                  |
| Admin Menu program               | `app/cbl/COADM01C.cbl` (XCTL target for admin users)                  |
| Main Menu program                | `app/cbl/COMEN01C.cbl` (XCTL target for regular users)                |

### Migration Considerations

| #  | Consideration                                                                                                                                      |
|----|----------------------------------------------------------------------------------------------------------------------------------------------------|
| M1 | **Plain-Text Password Storage** — Passwords are stored and compared in plain text in the USRSEC file. A modernized system must implement password hashing (e.g., bcrypt, Argon2) and secure comparison. This affects both the sign-on logic and any user management screens that create or update passwords. |
| M2 | **No Account Lockout** — The current system has no mechanism to track failed login attempts or lock accounts after repeated failures. The modernized system should implement configurable lockout policies (e.g., lock after 5 failed attempts, with a cooldown period). |
| M3 | **No Session Token Management** — The current system relies on an in-memory communication area passed between screens. A modernized system should implement proper session/token management (e.g., JWT, server-side sessions) with expiration and secure storage. |
| M4 | **No Password Complexity Rules** — The current system accepts any 8-character (or shorter) string as a password. The modernized system should enforce password complexity requirements (minimum length, character variety, etc.). |
| M5 | **No Audit Logging** — Successful and failed login attempts are not logged. The modernized system should maintain an audit trail of authentication events for security monitoring and compliance. |
| M6 | **Pseudo-Conversational Pattern** — The current system uses the mainframe pseudo-conversational model (send screen, return control, receive input on re-entry). This maps to a stateless request/response pattern in modern web applications (e.g., REST API with POST /login). |
| M7 | **Screen-to-UI Mapping** — The 24x80 terminal screen with fixed-position fields maps to a modern login form. The decorative ASCII art banner could be replaced with a graphical logo or branding element. The hidden password field (DRK attribute) maps to an HTML `<input type="password">`. |
| M8 | **Role-Based Routing** — The binary admin/regular-user routing maps to role-based access control (RBAC) in a modern system. Consider using a more flexible role/permission model rather than a single character type code. |
| M9 | **Transfer of Control (XCTL)** — The XCTL command transfers control to another program and removes the caller from the chain. In a modern system, this maps to page navigation (SPA routing) or API-driven menu loading. The caller is not "removed" in the same way — the back button and navigation history must be managed. |
| M10 | **Fixed Communication Area** — All programs share a fixed 160-byte COMMAREA regardless of their needs. A modern system can use targeted DTOs, session storage, or claims in a JWT to pass only the relevant data between components. |
| M11 | **BMS Map Compilation** — The BMS source generates a symbolic map copybook at compile time. In migration, the BMS map definitions serve as the specification for UI form design; the generated copybook fields map to form field names and types. |
| M12 | **Case-Insensitive Authentication** — The current system forces uppercase comparison. Modern systems typically support case-sensitive passwords while keeping usernames case-insensitive. The migration should preserve case-insensitive user IDs but implement case-sensitive passwords. |

---

## Section 10: Related Use Cases / End-to-End Composition

| Journey Name                           | Use Cases Involved                                         | Business Scenario                                                                                   |
|----------------------------------------|------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| Administrator User Management          | UC-CC00-001, UC-CA00-001, UC-CUSR-001 through UC-CUSR-004 | Admin logs in, navigates to user management, performs CRUD operations on user accounts, logs out.     |
| Cardholder Account Inquiry             | UC-CC00-001, UC-CM00-001, UC-CAVW-001                      | User logs in, navigates to account view, reviews account details, logs out.                          |
| Credit Card Lookup and Update          | UC-CC00-001, UC-CM00-001, UC-CCLI-001, UC-CCDL-001, UC-CCUP-001 | User logs in, lists credit cards, selects one, views details, updates card information, logs out. |
| Transaction Processing Cycle           | UC-CC00-001, UC-CM00-001, UC-CT02-001, UC-CTLS-001, UC-CT01-001 | User logs in, adds a new transaction, views transaction list, drills into transaction details, logs out. |
| Bill Payment                           | UC-CC00-001, UC-CM00-001, UC-CB00-001                       | User logs in, navigates to bill payment, processes a payment, logs out.                              |
| Report Generation                      | UC-CC00-001, UC-CM00-001, UC-CR00-001                       | User logs in, navigates to reports, generates a transaction report, logs out.                        |
| Full Account Lifecycle (Administrator) | UC-CC00-001, UC-CA00-001, UC-CUSR-002, UC-CM00-001, UC-CAVW-001, UC-CCLI-001 | Admin logs in, creates a new user, switches to verify the user can access account and card views.   |

> **Note:** Individual use case flows listed above must be composed into end-to-end journey documents for complete validation. Some referenced use cases (e.g., UC-CA00-001, UC-CUSR-001 through UC-CUSR-004) may not yet have their own Business Use Case Flow documents — they should be created following this same playbook.

---

## Document Footer — Summary Statistics

| Attribute                  | Value                            |
|----------------------------|----------------------------------|
| **Source Program**         | COSGN00C.cbl (261 lines)        |
| **Transaction ID**         | CC00                             |
| **Data Source**            | Technical Flow Analysis + COBOL Source Verification |
| **Acceptance Criteria**    | 17 (AC-01 through AC-17)        |
| **Business Rules**         | 7 (BR1 through BR7)             |
| **Validation Rules**       | 2 (V1 through V2)               |
| **Alternative/Error Flows**| 7 (AF-1 through AF-7)           |
| **Migration Considerations**| 12 (M1 through M12)            |

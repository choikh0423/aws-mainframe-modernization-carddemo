# CICS Business Use Case Flow Analysis: CM00 — Main Menu for Regular Users

**Program:** COMEN01C
**Transaction:** CM00
**Generated:** 2026-05-07
**Repository:** choikh0423/aws-mainframe-modernization-carddemo
**Branch:** demos/cobol-full-docs

---

## Section 1 — Use Case Overview

| Attribute         | Value                                                                 |
|-------------------|-----------------------------------------------------------------------|
| **Use Case Name** | Main Menu Navigation for Regular Users                                |
| **Use Case ID**   | UC-CM00-001                                                           |
| **Actor(s)**      | Regular User (authenticated, non-administrator)                       |
| **Business Goal** | Provide a central navigation hub from which regular users can access all available credit card management functions |
| **Preconditions** | 1. User has successfully signed in via the Sign-On screen<br>2. User has been authenticated as a "Regular User" (not an administrator)<br>3. A valid session context has been established by the Sign-On process |
| **Postconditions**| 1. User has navigated to a selected business function, OR<br>2. User has signed out and returned to the Sign-On screen |
| **Trigger**       | Successful sign-on by a regular user automatically transfers control to this menu |
| **Data Access**   | Read-only (no direct data file access; session context is read and passed through) |
| **Frequency**     | Every regular user session; this screen is displayed at least once per session and re-displayed each time a user returns from a sub-function |

### Business Context

The Main Menu is the central navigation point for all regular (non-administrative) users in the CardDemo credit card management system. After signing in, every regular user arrives at this screen. It presents a numbered list of eleven business functions spanning account management, credit card operations, transaction processing, reporting, and bill payment. The user selects an option by entering its number, and the system transfers them to the corresponding function. Pressing Exit returns the user to the Sign-On screen, effectively ending their session.

This menu does not perform any direct data operations — it serves purely as a routing and access-control gateway. It enforces role-based access by checking whether each menu option is authorized for the user's role before allowing navigation.

```
  +-----------------------------------------------------------------+
  |                  CardDemo Functional Domains                     |
  +-----------------------------------------------------------------+
  |                                                                   |
  |  +------------------+                                             |
  |  |   Sign-On        |                                             |
  |  |   (CC00)         |                                             |
  |  +--------+---------+                                             |
  |           |                                                       |
  |     +-----+------+                                                |
  |     |            |                                                 |
  |     v            v                                                 |
  |  +--------+  +--------+                                           |
  |  | Main   |  | Admin  |                                           |
  |  | Menu   |  | Menu   |                                           |
  |  |*(CM00)*|  | (CA00) |                                           |
  |  +---+----+  +--------+                                           |
  |      |                                                             |
  |      +---> Account Management (View, Update)                      |
  |      +---> Credit Card Management (List, View, Update)            |
  |      +---> Transaction Processing (List, View, Add)               |
  |      +---> Reporting (Transaction Reports)                        |
  |      +---> Billing (Bill Payment)                                 |
  |      +---> Authorization (Pending Authorization View)             |
  |                                                                   |
  +-----------------------------------------------------------------+

  * THIS USE CASE *
```

---

## Section 2 — User Journey (Happy Path)

### Sequence Diagram

```
  +-----------+                              +-------------------+
  |   User    |                              |      System       |
  +-----------+                              +-------------------+
       |                                              |
       |  (Signs in successfully)                     |
       |--------------------------------------------->|
       |                                              |
       |       Display Main Menu screen               |
       |<---------------------------------------------|
       |                                              |
       |  Title: "AWS Mainframe Modernization"        |
       |  Subtitle: "CardDemo"                        |
       |  11 numbered menu options displayed          |
       |  Prompt: "Please select an option :"         |
       |  Footer: "ENTER=Continue  F3=Exit"           |
       |                                              |
       |  User types option number (e.g., "3")        |
       |  User presses ENTER                          |
       |--------------------------------------------->|
       |                                              |
       |  System validates option number              |
       |  System checks user authorization            |
       |  System transfers to selected function       |
       |                                              |
       |       Display Credit Card List screen        |
       |<---------------------------------------------|
       |                                              |
```

### Step-by-Step Narrative

| Step | Actor  | Action                                              | System Response                                                                                         |
|------|--------|------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| 1    | System | User completes sign-on                               | System verifies credentials, identifies user as a regular user, and transfers control to the Main Menu  |
| 2    | System | Display Main Menu                                    | Screen shows application title, current date and time, 11 numbered menu options, an input field for the option number, and available actions (Enter to continue, F3 to exit) |
| 3    | User   | Reviews the menu options                             | Menu remains displayed, cursor is positioned in the option input field                                  |
| 4    | User   | Types a menu option number (e.g., "3")               | The entered number appears in the option input field                                                    |
| 5    | User   | Presses Enter                                        | System reads the entered option number                                                                  |
| 6    | System | Validates the option number                          | System confirms the input is a valid number between 1 and 11                                            |
| 7    | System | Checks user authorization for the selected option    | System confirms the selected option is authorized for regular users                                     |
| 8    | System | Transfers control to the selected function           | The selected business function screen is displayed (e.g., Credit Card List for option 3)                |

---

## Section 3 — Business Rules & Validations

### Input Validation Rules

| #  | Field          | Rule                                                                                              | Error Message Shown to User                       |
|----|----------------|---------------------------------------------------------------------------------------------------|---------------------------------------------------|
| V1 | Option Number  | Must be a numeric value                                                                           | `Please enter a valid option number...`           |
| V2 | Option Number  | Must be greater than zero                                                                         | `Please enter a valid option number...`           |
| V3 | Option Number  | Must not exceed the total number of available menu options (currently 11)                          | `Please enter a valid option number...`           |
| V4 | Option Number  | Spaces and leading blanks in the input are treated as zeros (right-justified, zero-filled)         | *(preprocessing — no error)*                      |

### Business Rules

| #   | Rule                                                                                                                                                    |
|-----|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| BR1 | **Session Requirement** — A user must have an active session established by the Sign-On process to access the Main Menu. If no session context exists, the system redirects to the Sign-On screen. |
| BR2 | **Role-Based Menu Access** — Each menu option has an associated authorized role. If a regular user selects an option restricted to administrators, the system denies access with an error message: `No access - Admin Only option...` |
| BR3 | **Table-Driven Menu** — The menu options (names, target functions, and role authorizations) are defined in a configuration table, not hard-coded in the navigation logic. The system iterates this table to build the displayed menu. |
| BR4 | **Program Availability Check** — For the "Pending Authorization View" option, the system verifies that the target function is installed and available before attempting to navigate. If it is not available, the system displays: `This option Pending Authorization View is not installed...` (in red) |
| BR5 | **Placeholder Functions** — If a menu option's target function name begins with "DUMMY", the system treats it as a planned-but-not-yet-available feature and displays: `This option [name] is coming soon...` (in green) |
| BR6 | **Session Context Propagation** — When transferring to a downstream function, the system sets the originating transaction and program identifiers so the downstream function can navigate back to this menu. |
| BR7 | **Exit Returns to Sign-On** — Pressing Exit (F3) always returns the user to the Sign-On screen. If no explicit return destination is set, the system defaults to the Sign-On screen. |
| BR8 | **Invalid Key Handling** — Any key press other than Enter or F3 is rejected with the message: `Invalid key pressed. Please see below...` |
| BR9 | **Current Menu Options** — All 11 menu options are currently authorized for regular users. No options are restricted to administrators in the present configuration. |

### Decision Table

```
+------------------------------+---------------------------------------------------+
| User Action                  | System Response                                   |
+------------------------------+---------------------------------------------------+
| Enter + valid option (1-10)  | Transfer to selected business function             |
| Enter + option 11            | Check if function is installed:                    |
|                              |   - Installed: transfer to function                |
|                              |   - Not installed: show error (red), stay on menu  |
| Enter + option for DUMMY pgm | Show "coming soon" message (green), stay on menu   |
| Enter + empty/blank input    | Show "Please enter a valid option number..."       |
| Enter + non-numeric input    | Show "Please enter a valid option number..."       |
| Enter + 0                    | Show "Please enter a valid option number..."       |
| Enter + number > 11          | Show "Please enter a valid option number..."       |
| Enter + admin-only option    | Show "No access - Admin Only option..."            |
|   (as regular user)          |                                                    |
| F3 (Exit)                    | Return to Sign-On screen                           |
| Any other key                | Show "Invalid key pressed. Please see below..."    |
| No session context           | Redirect to Sign-On screen (no message displayed)  |
+------------------------------+---------------------------------------------------+
```

---

## Section 4 — Data Entities Involved

### Entity Descriptions

| Entity                | Description                                                    | Role in This Use Case                                                              |
|-----------------------|----------------------------------------------------------------|------------------------------------------------------------------------------------|
| User Session Context  | In-memory data area carrying user identity, role, and navigation state across screens | Read on entry (user ID, role); updated with navigation context on exit             |
| Menu Configuration    | Static table defining available menu options with option number, display name, target function, and authorized role | Read to build the displayed menu and to validate/authorize the user's selection     |
| User Security Record  | Persistent record containing user credentials and role assignment | Not directly accessed by this screen; read by the upstream Sign-On process to establish the session |

### Entity Relationships

```
  +---------------------+        +------------------------+
  |  User Security      |        |  User Session Context  |
  |  Record             |        |                        |
  |---------------------|  1..1  |------------------------|
  | User ID (key)       |------->| User ID                |
  | First Name          |        | User Role              |
  | Last Name           |        | Source Transaction      |
  | Password            |        | Source Program          |
  | Role (User/Admin)   |        | Destination Program    |
  +---------------------+        | Program State          |
                                  | Customer Info          |
         +------------------------| Account Info           |
         |                        | Card Info              |
         |                        +------------------------+
         |
         |  1..* (one per option)
         v
  +---------------------+
  |  Menu Configuration |
  |---------------------|
  | Option Number       |
  | Display Name        |
  | Target Function     |
  | Authorized Role     |
  +---------------------+
```

### Data Fields Displayed

| #  | Field              | Format      | Source                | Description                                              |
|----|--------------------|-------------|----------------------|----------------------------------------------------------|
| 1  | Application Title  | Text (40)   | Application constants | "AWS Mainframe Modernization" — first line of header     |
| 2  | Application Name   | Text (40)   | Application constants | "CardDemo" — second line of header                       |
| 3  | Transaction ID     | Text (4)    | Program constant      | "CM00" — identifier for this transaction                 |
| 4  | Program Name       | Text (8)    | Program constant      | "COMEN01C" — identifier for this program                 |
| 5  | Current Date       | MM/DD/YY    | System clock          | Today's date formatted for display                       |
| 6  | Current Time       | HH:MM:SS    | System clock          | Current time formatted for display                       |
| 7  | Menu Option 1-11   | Text (40)   | Menu Configuration    | Formatted as "NN. Option Name" (e.g., "01. Account View") |
| 8  | Option Input       | Numeric (2) | User input            | The menu option number entered by the user               |
| 9  | Error/Status Msg   | Text (78)   | System-generated      | Validation errors or status messages (red or green)      |

**Fields NOT displayed on this screen:**
- User ID (carried in session context but not shown on the menu)
- User Role (used internally for authorization but not displayed)
- Customer, Account, and Card information fields (present in session context but not relevant to the menu)

---

## Section 5 — Screen / Interface Description

### Screen Layout

```
+--------------------------------------------------------------------------------+
|Tran: CM00      AWS Mainframe Modernization                       Date: 05/07/26|
|Prog: COMEN01C            CardDemo                                Time: 21:50:00|
|                                                                                |
|                                  Main Menu                                     |
|                                                                                |
|                   01. Account View                                             |
|                   02. Account Update                                           |
|                   03. Credit Card List                                         |
|                   04. Credit Card View                                         |
|                   05. Credit Card Update                                       |
|                   06. Transaction List                                         |
|                   07. Transaction View                                         |
|                   08. Transaction Add                                          |
|                   09. Transaction Reports                                      |
|                   10. Bill Payment                                             |
|                   11. Pending Authorization View                               |
|                                                                                |
|                                                                                |
|                                                                                |
|              Please select an option : 03                                      |
|                                                                                |
|                                                                                |
|                                                                                |
|ENTER=Continue  F3=Exit                                                         |
+--------------------------------------------------------------------------------+
```

### Interface Elements

| Element              | Type    | Description                                                                   |
|----------------------|---------|-------------------------------------------------------------------------------|
| Application Title    | Display | Two-line header showing "AWS Mainframe Modernization" and "CardDemo"          |
| Transaction ID       | Display | Shows "CM00" identifying the current transaction                              |
| Program Name         | Display | Shows "COMEN01C" identifying the current program                              |
| Current Date         | Display | System date in MM/DD/YY format                                               |
| Current Time         | Display | System time in HH:MM:SS format                                               |
| Main Menu Heading    | Display | Centered heading "Main Menu" in bright text                                   |
| Menu Options 1-11    | Display | Numbered list of available business functions                                 |
| Option Input Field   | Input   | 2-character numeric field where the user types their menu selection; cursor is automatically positioned here; right-justified with zero-fill |
| Error/Status Message | Display | 78-character message area on row 23; displays in red for errors, green for informational messages |
| Action Legend        | Display | "ENTER=Continue  F3=Exit" displayed at the bottom of the screen               |

### Available Actions

| Action    | How to Invoke       | Description                                                   |
|-----------|---------------------|---------------------------------------------------------------|
| Continue  | Type option + Enter | Validates the entered option number and navigates to the selected business function |
| Exit      | Press F3            | Returns to the Sign-On screen, effectively ending the session |

---

## Section 6 — Alternative & Error Flows

### Alt-1: Invalid Option Number (Non-Numeric, Zero, or Out of Range)

| Step | Description                                                                                    |
|------|-----------------------------------------------------------------------------------------------|
| 1    | User types a value that is not numeric, is zero, or exceeds 11 in the option input field      |
| 2    | User presses Enter                                                                            |
| 3    | System detects the invalid option number                                                      |
| 4    | System displays the message: `Please enter a valid option number...` in the error area (red)  |
| 5    | Menu screen is re-displayed with the error message; cursor returns to the option input field   |
| 6    | User may correct their entry and try again                                                    |

### Alt-2: Admin-Only Option Selected by Regular User

| Step | Description                                                                                    |
|------|-----------------------------------------------------------------------------------------------|
| 1    | User types an option number that is restricted to administrators                               |
| 2    | User presses Enter                                                                            |
| 3    | System validates the option number (passes V1-V3)                                             |
| 4    | System checks the authorized role for the selected option and finds it is admin-only           |
| 5    | System displays the message: `No access - Admin Only option...` in the error area (red)       |
| 6    | Menu screen is re-displayed with the error message; cursor returns to the option input field   |

**Note:** In the current configuration, all 11 options are authorized for regular users, so this flow does not trigger under normal operation. It would activate if an administrator-only option were added to the menu configuration.

### Alt-3: Selected Function Not Installed (Pending Authorization View)

| Step | Description                                                                                    |
|------|-----------------------------------------------------------------------------------------------|
| 1    | User types "11" (Pending Authorization View) in the option input field                        |
| 2    | User presses Enter                                                                            |
| 3    | System validates the option number (passes V1-V3) and authorization (passes BR2)              |
| 4    | System checks whether the Pending Authorization View function is installed                     |
| 5    | System determines the function is NOT installed                                                |
| 6    | System displays the message: `This option Pending Authorization View is not installed...` in the error area (red) |
| 7    | Menu screen is re-displayed with the error message; cursor returns to the option input field   |

### Alt-4: Placeholder / Coming Soon Function

| Step | Description                                                                                    |
|------|-----------------------------------------------------------------------------------------------|
| 1    | User types the option number for a function whose target begins with "DUMMY"                  |
| 2    | User presses Enter                                                                            |
| 3    | System validates the option number and authorization                                          |
| 4    | System detects the target function is a placeholder                                            |
| 5    | System displays the message: `This option [Function Name] is coming soon...` in the error area (green) |
| 6    | Menu screen is re-displayed with the informational message                                    |

**Note:** No current menu options use the "DUMMY" prefix. This flow would activate if a placeholder option were added to the menu configuration.

### Alt-5: Invalid Key Pressed

| Step | Description                                                                                    |
|------|-----------------------------------------------------------------------------------------------|
| 1    | User presses any key other than Enter or F3 (e.g., F1, F2, F4-F12, Clear, PA keys)           |
| 2    | System detects the unrecognized key                                                           |
| 3    | System displays the message: `Invalid key pressed. Please see below...` in the error area (red) |
| 4    | Menu screen is re-displayed with the error message; cursor returns to the option input field   |

### Alt-6: No Active Session (Direct Access Attempt)

| Step | Description                                                                                    |
|------|-----------------------------------------------------------------------------------------------|
| 1    | A user or process attempts to invoke the Main Menu transaction directly without a prior sign-on |
| 2    | System detects no session context is present                                                  |
| 3    | System immediately redirects to the Sign-On screen without displaying the menu                |
| 4    | No error message is shown — the Sign-On screen appears for the user to authenticate           |

---

## Section 7 — Business Process Context

### Navigation Context

```
  CardDemo Application Navigation Hierarchy
  ==========================================

  Sign-On (CC00)
  |
  +--- [Regular User] ---> *** Main Menu (CM00) ***    <--- THIS USE CASE
  |                         |
  |                         +--- 01. Account View (CAVW)
  |                         +--- 02. Account Update (CAUP)
  |                         +--- 03. Credit Card List (CCLI)
  |                         +--- 04. Credit Card View (CCDL)
  |                         +--- 05. Credit Card Update (CCUP)
  |                         +--- 06. Transaction List (CT00)
  |                         +--- 07. Transaction View (CT01)
  |                         +--- 08. Transaction Add (CT02)
  |                         +--- 09. Transaction Reports (CR00)
  |                         +--- 10. Bill Payment (CB00)
  |                         +--- 11. Pending Auth View (not installed)
  |
  +--- [Admin User] -----> Admin Menu (CA00)
                            |
                            +--- User List
                            +--- User Add
                            +--- User Update
                            +--- User Delete
```

### Downstream Use Cases

| Destination                  | Trigger                      | What Happens Next                                                    |
|------------------------------|------------------------------|----------------------------------------------------------------------|
| Account View                 | User selects option 1        | User can view account details by entering an account number          |
| Account Update               | User selects option 2        | User can modify account information                                  |
| Credit Card List             | User selects option 3        | User sees a paginated list of credit cards, can select one for detail |
| Credit Card View             | User selects option 4        | User can view credit card details by entering a card number          |
| Credit Card Update           | User selects option 5        | User can modify credit card information                              |
| Transaction List             | User selects option 6        | User sees a paginated list of transactions with filtering options    |
| Transaction View             | User selects option 7        | User can view details of a specific transaction                      |
| Transaction Add              | User selects option 8        | User can create a new transaction record                             |
| Transaction Reports          | User selects option 9        | User can generate and view transaction reports                       |
| Bill Payment                 | User selects option 10       | User can process a bill payment against a credit card account        |
| Pending Authorization View   | User selects option 11       | User can view pending authorization records (currently not installed) |

### Upstream Use Cases

| Source          | Navigation Path                                                                  |
|-----------------|----------------------------------------------------------------------------------|
| Sign-On (CC00)  | User signs in with valid credentials and is identified as a regular user; system automatically transfers to Main Menu |
| Account View    | User presses F3 (Exit) from Account View; system returns to Main Menu            |
| Account Update  | User presses F3 (Exit) from Account Update; system returns to Main Menu          |
| Credit Card List| User presses F3 (Exit) from Credit Card List; system returns to Main Menu        |
| Credit Card View| User presses F3 (Exit) from Credit Card View; system returns to Main Menu        |
| Credit Card Update | User presses F3 (Exit) from Credit Card Update; system returns to Main Menu   |
| Transaction List| User presses F3 (Exit) from Transaction List; system returns to Main Menu        |
| Transaction View| User presses F3 (Exit) from Transaction View; system returns to Main Menu        |
| Transaction Add | User presses F3 (Exit) from Transaction Add; system returns to Main Menu         |
| Transaction Reports | User presses F3 (Exit) from Transaction Reports; system returns to Main Menu |
| Bill Payment    | User presses F3 (Exit) from Bill Payment; system returns to Main Menu            |

### End-to-End Journey Examples

**Journey 1: View Account Details**

```
  Sign-On ──> Main Menu ──> Account View ──> Main Menu ──> Sign-On
                (opt 1)        (F3)            (F3)
```
*Business scenario: A customer service representative signs in, navigates to view a customer's account details, then exits.*

**Journey 2: Update a Credit Card and Review Transactions**

```
  Sign-On ──> Main Menu ──> Credit Card Update ──> Main Menu ──> Transaction List ──> Main Menu ──> Sign-On
                (opt 5)          (F3)                (opt 6)          (F3)              (F3)
```
*Business scenario: A user updates credit card information, returns to the menu, then reviews recent transactions before signing out.*

**Journey 3: Add a Transaction and Pay a Bill**

```
  Sign-On ──> Main Menu ──> Transaction Add ──> Main Menu ──> Bill Payment ──> Main Menu ──> Sign-On
                (opt 8)         (F3)              (opt 10)       (F3)           (F3)
```
*Business scenario: A user records a new transaction, returns to the menu, processes a bill payment, and then signs out.*

**Journey 4: Generate Reports**

```
  Sign-On ──> Main Menu ──> Transaction Reports ──> Main Menu ──> Sign-On
                (opt 9)           (F3)                (F3)
```
*Business scenario: A supervisor signs in to generate end-of-day transaction reports and then exits the system.*

---

## Section 8 — Acceptance Criteria

### Core Functionality

| #     | Given                                           | When                                            | Then                                                                                   |
|-------|-------------------------------------------------|-------------------------------------------------|----------------------------------------------------------------------------------------|
| AC-01 | A regular user has signed in successfully        | The system transfers control from Sign-On        | The Main Menu screen is displayed with all 11 options, header information, and the option input field |
| AC-02 | The Main Menu is displayed                       | The user types "1" and presses Enter             | The system transfers to the Account View function                                      |
| AC-03 | The Main Menu is displayed                       | The user types "2" and presses Enter             | The system transfers to the Account Update function                                    |
| AC-04 | The Main Menu is displayed                       | The user types "3" and presses Enter             | The system transfers to the Credit Card List function                                  |
| AC-05 | The Main Menu is displayed                       | The user types "5" and presses Enter             | The system transfers to the Credit Card Update function                                |
| AC-06 | The Main Menu is displayed                       | The user types "6" and presses Enter             | The system transfers to the Transaction List function                                  |
| AC-07 | The Main Menu is displayed                       | The user types "10" and presses Enter            | The system transfers to the Bill Payment function                                      |

### Input Validation

| #     | Given                                           | When                                            | Then                                                                                   |
|-------|-------------------------------------------------|-------------------------------------------------|----------------------------------------------------------------------------------------|
| AC-08 | The Main Menu is displayed                       | The user leaves the option field blank and presses Enter | The error message `Please enter a valid option number...` is displayed              |
| AC-09 | The Main Menu is displayed                       | The user types "0" and presses Enter             | The error message `Please enter a valid option number...` is displayed                 |
| AC-10 | The Main Menu is displayed                       | The user types "12" and presses Enter            | The error message `Please enter a valid option number...` is displayed                 |
| AC-11 | The Main Menu is displayed                       | The user types "99" and presses Enter            | The error message `Please enter a valid option number...` is displayed                 |
| AC-12 | The Main Menu is displayed                       | The user types "AB" (non-numeric) and presses Enter | The error message `Please enter a valid option number...` is displayed              |
| AC-13 | The Main Menu is displayed                       | The user types " 3" (leading space) and presses Enter | The system treats the input as "03", validates it, and transfers to Credit Card List |

### Authorization

| #     | Given                                           | When                                            | Then                                                                                   |
|-------|-------------------------------------------------|-------------------------------------------------|----------------------------------------------------------------------------------------|
| AC-14 | The Main Menu is displayed and a menu option is configured as admin-only | The regular user selects that option and presses Enter | The error message `No access - Admin Only option...` is displayed and the menu is re-shown |
| AC-15 | The Main Menu is displayed                       | The user selects option 11 (Pending Authorization View) and the function is not installed | The error message `This option Pending Authorization View is not installed...` is displayed in red |
| AC-16 | The Main Menu is displayed                       | The user selects an option whose function is a placeholder (DUMMY) | The message `This option [name] is coming soon...` is displayed in green              |

### Navigation

| #     | Given                                           | When                                            | Then                                                                                   |
|-------|-------------------------------------------------|-------------------------------------------------|----------------------------------------------------------------------------------------|
| AC-17 | The Main Menu is displayed                       | The user presses F3 (Exit)                       | The system returns to the Sign-On screen                                               |
| AC-18 | The Main Menu is displayed                       | The user presses any key other than Enter or F3  | The error message `Invalid key pressed. Please see below...` is displayed and the menu is re-shown |
| AC-19 | No session context exists                        | The Main Menu transaction is invoked directly    | The system redirects to the Sign-On screen without displaying the menu                 |

### Display

| #     | Given                                           | When                                            | Then                                                                                   |
|-------|-------------------------------------------------|-------------------------------------------------|----------------------------------------------------------------------------------------|
| AC-20 | The Main Menu is displayed                       | The user observes the screen                     | The header shows the correct application title, transaction ID ("CM00"), program name ("COMEN01C"), current date (MM/DD/YY), and current time (HH:MM:SS) |
| AC-21 | The Main Menu is displayed                       | The user observes the menu options               | All 11 options are listed in order with their numbers and descriptions, formatted as "NN. Option Name" |
| AC-22 | The Main Menu is displayed                       | The screen loads                                 | The cursor is automatically positioned in the option input field                       |

### Data Integrity

| #     | Given                                           | When                                            | Then                                                                                   |
|-------|-------------------------------------------------|-------------------------------------------------|----------------------------------------------------------------------------------------|
| AC-23 | The user selects a valid option and the system transfers to a downstream function | The downstream function receives control | The session context contains the correct originating transaction ("CM00"), originating program ("COMEN01C"), and the program state is reset for the downstream function's first entry |
| AC-24 | A downstream function sets the return destination to Main Menu and transfers back | The Main Menu receives control           | The Main Menu re-displays correctly, preserving the user's session identity and role    |

---

## Section 9 — Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                         | Technical Reference                                                                                       |
|-------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| Session context check (BR1)                           | `MAIN-PARA`: `IF EIBCALEN = 0` check (line 82); copies `DFHCOMMAREA` to `CARDDEMO-COMMAREA` (line 86)   |
| First-entry menu display                              | `MAIN-PARA`: `IF NOT CDEMO-PGM-REENTER` branch (line 87); `SET CDEMO-PGM-REENTER TO TRUE` (line 88); `PERFORM SEND-MENU-SCREEN` (line 90) |
| Receive user input                                    | `RECEIVE-MENU-SCREEN`: `EXEC CICS RECEIVE MAP('COMEN1A') MAPSET('COMEN01')` (lines 227-233)              |
| Enter key processing                                  | `MAIN-PARA`: `EVALUATE EIBAID / WHEN DFHENTER` (lines 93-95); `PERFORM PROCESS-ENTER-KEY`                |
| Option number extraction and formatting (V4)          | `PROCESS-ENTER-KEY`: right-trim loop (lines 117-121); `INSPECT REPLACING ALL ' ' BY '0'` (line 123); `MOVE WS-OPTION-X TO WS-OPTION` (line 124) |
| Option validation (V1, V2, V3)                        | `PROCESS-ENTER-KEY`: `IF WS-OPTION IS NOT NUMERIC OR WS-OPTION > CDEMO-MENU-OPT-COUNT OR WS-OPTION = ZEROS` (lines 127-129) |
| Admin-only authorization check (BR2)                  | `PROCESS-ENTER-KEY`: `IF CDEMO-USRTYP-USER AND CDEMO-MENU-OPT-USRTYPE(WS-OPTION) = 'A'` (lines 136-137) |
| Program availability check for option 11 (BR4)        | `PROCESS-ENTER-KEY`: `EXEC CICS INQUIRE PROGRAM(CDEMO-MENU-OPT-PGMNAME(WS-OPTION)) NOHANDLE` (lines 148-151); `IF EIBRESP = DFHRESP(NORMAL)` (line 152) |
| Placeholder function detection (BR5)                  | `PROCESS-ENTER-KEY`: `WHEN CDEMO-MENU-OPT-PGMNAME(WS-OPTION)(1:5) = 'DUMMY'` (line 169)                 |
| Transfer to downstream function (BR6)                 | `PROCESS-ENTER-KEY`: `MOVE WS-TRANID TO CDEMO-FROM-TRANID` (line 178); `MOVE WS-PGMNAME TO CDEMO-FROM-PROGRAM` (line 179); `MOVE ZEROS TO CDEMO-PGM-CONTEXT` (line 183); `EXEC CICS XCTL PROGRAM(...)  COMMAREA(CARDDEMO-COMMAREA)` (lines 184-187) |
| Exit to Sign-On (BR7)                                 | `MAIN-PARA`: `WHEN DFHPF3` (line 96); `MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM` (line 97); `PERFORM RETURN-TO-SIGNON-SCREEN` which issues `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)` (lines 196-203) |
| Invalid key handling (BR8)                            | `MAIN-PARA`: `WHEN OTHER` (line 99); `MOVE CCDA-MSG-INVALID-KEY TO WS-MESSAGE` (line 101)                |
| Build menu display from configuration (BR3)           | `BUILD-MENU-OPTIONS`: loops `WS-IDX` from 1 to `CDEMO-MENU-OPT-COUNT` (lines 264-265); `STRING` formats each option (lines 269-272); `EVALUATE WS-IDX` maps to output fields (lines 274-301) |
| Populate header (date, time, titles)                  | `POPULATE-HEADER-INFO`: `FUNCTION CURRENT-DATE` (line 240); title moves (lines 242-243); date/time formatting (lines 247-257) |
| Pseudo-conversational return                          | `MAIN-PARA` end: `EXEC CICS RETURN TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA)` (lines 107-110)      |

### Source File Mapping

| Business Concept                  | Source File(s)                                                    |
|-----------------------------------|-------------------------------------------------------------------|
| Main Menu program logic           | `app/cbl/COMEN01C.cbl` (309 lines)                               |
| Screen layout and field definitions | `app/bms/COMEN01.bms` (168 lines)                              |
| Session context structure          | `app/cpy/COCOM01Y.cpy` (CARDDEMO-COMMAREA)                      |
| Menu option configuration table    | `app/cpy/COMEN02Y.cpy` (CARDDEMO-MAIN-MENU-OPTIONS, 11 entries) |
| Application title constants        | `app/cpy/COTTL01Y.cpy` (CCDA-SCREEN-TITLE)                      |
| Date and time formatting           | `app/cpy/CSDAT01Y.cpy` (WS-DATE-TIME)                           |
| Common messages                    | `app/cpy/CSMSG01Y.cpy` (CCDA-MSG-INVALID-KEY, CCDA-MSG-THANK-YOU) |
| User security record layout        | `app/cpy/CSUSR01Y.cpy` (SEC-USER-DATA — included but not used)  |
| Symbolic map input/output          | `COMEN01` copybook (auto-generated from BMS; defines COMEN1AI/COMEN1AO) |
| AID key constants                  | `DFHAID` (IBM system copybook — DFHENTER, DFHPF3)               |
| Screen attribute constants         | `DFHBMSCA` (IBM system copybook — DFHRED, DFHGREEN)             |

### Migration Considerations

| #  | Consideration                                                                                                                                                                                   |
|----|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| M1 | **Pseudo-conversational pattern to stateful web session** — The current system suspends and resumes the transaction between user interactions, passing state via a communication area. In a modern web application, this maps to server-side session state or a stateless JWT-based session with a session store. |
| M2 | **Terminal screen map to web UI** — The 24x80 fixed-format terminal screen with positioned fields maps directly to an HTML form or a modern UI component (e.g., a sidebar navigation menu or a dashboard with links/cards). The single input field could become a clickable list. |
| M3 | **Function key mapping to UI controls** — Enter maps to a "Submit" or "Go" button (or clicking a menu item directly). F3 maps to a "Sign Out" or "Back" button/link. Invalid key handling becomes unnecessary in a web context where only defined buttons/links exist. |
| M4 | **Inter-program transfer to URL routing** — The transfer-to-program mechanism maps to client-side routing (SPA) or server-side redirects. The session context fields (source transaction, source program) become route parameters or session attributes. |
| M5 | **Table-driven menu to configuration-driven navigation** — The static menu configuration table can be migrated to a database table or JSON configuration file, enabling dynamic menu management without code changes. |
| M6 | **Role-based access control** — The per-option role check is a simple form of RBAC. In a modern system, this maps to middleware-level authorization (e.g., Spring Security roles, OIDC scopes) checking permissions before rendering menu items or allowing navigation. |
| M7 | **Program availability check** — The runtime check for whether a program is installed is unique to the mainframe environment. In a modern system, this could be replaced by feature flags or service health checks. |
| M8 | **DUMMY program placeholders** — The "coming soon" pattern for placeholder functions maps naturally to feature flags in a modern system (e.g., LaunchDarkly, database-driven toggles) that control menu item visibility and availability. |
| M9 | **Hardcoded 12-slot display limit** — The screen layout supports a maximum of 12 menu options (with slot 12 currently unused). A modern UI would use dynamic rendering (e.g., a loop over menu items) with no fixed slot limit. |
| M10 | **Unused USRSEC file declaration** — The USRSEC file variable is declared but never used in this program. This dead code should be removed during migration to keep the codebase clean. |

---

## Section 10 — Related Use Cases / End-to-End Composition

| Journey Name                       | Use Cases Involved                                             | Business Scenario                                                                |
|------------------------------------|----------------------------------------------------------------|----------------------------------------------------------------------------------|
| Account Inquiry                    | UC-CC00-001 (Sign-On) → **UC-CM00-001 (Main Menu)** → UC-CAVW-001 (Account View) | A user signs in and looks up account details for a customer inquiry              |
| Account Maintenance                | UC-CC00-001 → **UC-CM00-001** → UC-CAUP-001 (Account Update)  | A user signs in and updates account information (e.g., address change, status)   |
| Card Lookup and Review             | UC-CC00-001 → **UC-CM00-001** → UC-CCLI-001 (Card List) → UC-CCDL-001 (Card View) | A user browses the card list, selects a card, and reviews its details            |
| Card Update                        | UC-CC00-001 → **UC-CM00-001** → UC-CCUP-001 (Card Update)     | A user signs in and modifies credit card attributes                              |
| Transaction Investigation          | UC-CC00-001 → **UC-CM00-001** → UC-CT00-001 (Transaction List) → UC-CT01-001 (Transaction View) | A user searches for transactions and drills into a specific record               |
| New Transaction Entry              | UC-CC00-001 → **UC-CM00-001** → UC-CT02-001 (Transaction Add) | A user manually records a new transaction                                        |
| End-of-Day Reporting               | UC-CC00-001 → **UC-CM00-001** → UC-CR00-001 (Transaction Reports) | A supervisor generates daily transaction reports                                 |
| Bill Payment Processing            | UC-CC00-001 → **UC-CM00-001** → UC-CB00-001 (Bill Payment)    | A user processes a bill payment against a credit card account                    |
| Multi-Function Session             | UC-CC00-001 → **UC-CM00-001** → UC-CAVW-001 → **UC-CM00-001** → UC-CT00-001 → **UC-CM00-001** | A user performs multiple operations in a single session, returning to the menu between each |

**Note:** Individual use case flows referenced above (UC-CAVW-001, UC-CAUP-001, etc.) must be composed into end-to-end journey documents for complete validation. Some of these use case documents may not yet exist and will need to be created separately.

---

## Document Footer — Summary Statistics

| Attribute                    | Value                                      |
|------------------------------|--------------------------------------------|
| **Source Program**           | COMEN01C.cbl (309 lines)                   |
| **Transaction ID**           | CM00                                       |
| **Data Source**              | Technical Flow Analysis + COBOL source verification |
| **Acceptance Criteria**      | 24 (AC-01 through AC-24)                   |
| **Business Rules**           | 9 (BR1 through BR9)                        |
| **Validation Rules**         | 4 (V1 through V4)                          |
| **Alternative/Error Flows**  | 6 (Alt-1 through Alt-6)                    |
| **Migration Considerations** | 10 (M1 through M10)                        |

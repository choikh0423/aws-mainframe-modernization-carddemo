# CR00 — Report Submission: Business Use Case Flow

## Section 1: Use Case Overview

| Attribute            | Value                                                                                     |
|----------------------|-------------------------------------------------------------------------------------------|
| **Use Case Name**    | Submit Transaction Report for Printing                                                    |
| **Use Case ID**      | UC-CR00-001                                                                               |
| **Actor(s)**         | Authenticated Regular User (card operations staff, analyst, manager)                      |
| **Business Goal**    | Allow a user to request a printed transaction report — monthly, yearly, or for a custom date range — by submitting a background print job from the online application |
| **Preconditions**    | 1. User is signed in to the CardDemo application 2. User has navigated to the Main Menu   |
| **Postconditions**   | A transaction report print job has been submitted to the background job queue for processing, or the user has cancelled the request |
| **Trigger**          | User selects "Transaction Reports" (option 9) from the Main Menu                         |
| **Data Access**      | Write-only (submits a print job to the background job queue; no direct read or write to transaction data files) |
| **Frequency**        | On-demand — typically end-of-month, end-of-year, or ad hoc for auditing/reconciliation    |

### Business Context

The Report Submission function is the CardDemo application's mechanism for generating printed transaction reports. Rather than producing reports in real time (which would consume significant system resources), the application follows a deferred-processing model: the online user selects the desired report parameters, confirms the request, and the system queues a background print job. The background job reads the transaction file, filters by the requested date range, and produces the printed report.

This pattern separates the interactive user experience (fast, lightweight) from the resource-intensive report generation (batched, scheduled). It is a common design in high-volume transaction processing environments where online response time must be preserved.

Three report types are available:
- **Monthly** — covers all transactions in the current calendar month
- **Yearly** — covers all transactions in the current calendar year
- **Custom** — covers transactions within a user-specified date range

```
┌─────────────────────────────────────────────────────────┐
│                 CardDemo Functional Domains              │
├────────────┬────────────┬───────────┬───────────────────┤
│  Account   │   Card     │Transaction│   Administration  │
│ Management │ Management │ Managemnt │                   │
├────────────┼────────────┼───────────┼───────────────────┤
│ View Acct  │ List Cards │ List Txns │ List Users        │
│ Update Acct│ View Card  │ View Txn  │ Add User          │
│            │ Update Card│ Add Txn   │ Update User       │
│            │            │ Bill Pay  │ Delete User       │
│            │            │           │ Txn Type Mgmt     │
│            │            │           │                   │
│            │            │ ┌───────────────────────┐     │
│            │            │ │ ** REPORT SUBMISSION **│     │
│            │            │ │   (This Use Case)     │     │
│            │            │ └───────────────────────┘     │
└────────────┴────────────┴───────────┴───────────────────┘
```

---

## Section 2: User Journey (Happy Path)

### Sequence Diagram

```
┌──────┐                              ┌──────────┐                     ┌───────────┐
│ User │                              │  System  │                     │ Print Job │
│      │                              │ (Online) │                     │  Queue    │
└──┬───┘                              └────┬─────┘                     └─────┬─────┘
   │  Select "Transaction Reports"         │                                 │
   │  from Main Menu                       │                                 │
   │──────────────────────────────────────>│                                 │
   │                                       │                                 │
   │  Display Report Submission screen     │                                 │
   │<──────────────────────────────────────│                                 │
   │                                       │                                 │
   │  Mark report type & enter dates       │                                 │
   │  (if custom), then press Enter        │                                 │
   │──────────────────────────────────────>│                                 │
   │                                       │                                 │
   │  Validate inputs                      │                                 │
   │  Prompt for confirmation              │                                 │
   │<──────────────────────────────────────│                                 │
   │                                       │                                 │
   │  Enter 'Y' to confirm, press Enter   │                                 │
   │──────────────────────────────────────>│                                 │
   │                                       │  Submit print job               │
   │                                       │────────────────────────────────>│
   │                                       │                                 │
   │  Display success message              │                                 │
   │<──────────────────────────────────────│                                 │
   │                                       │                                 │
   │  Press F3 to return to Main Menu      │                                 │
   │──────────────────────────────────────>│                                 │
   │                                       │                                 │
```

### Step-by-Step Narrative

| Step | Actor  | Action                                                        | System Response                                                                                              |
|------|--------|---------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| 1    | User   | Selects option 9 ("Transaction Reports") from the Main Menu  | System navigates to the Report Submission screen. The cursor is positioned on the Monthly report selection field. All input fields are blank. |
| 2    | User   | Marks one report type by entering any non-blank character in the corresponding selection field (Monthly, Yearly, or Custom) | No immediate response — the system waits for the user to press Enter. |
| 3    | User   | *(If Custom selected)* Enters Start Date (MM/DD/YYYY) and End Date (MM/DD/YYYY) in the date fields | No immediate response — the system waits for the user to press Enter. |
| 4    | User   | Presses Enter to submit the report request                    | System validates the selection and date fields (if Custom). If valid, displays a confirmation prompt: "Please confirm to print the {Monthly/Yearly/Custom} report..." with cursor on the confirmation field. |
| 5    | User   | Enters 'Y' in the confirmation field and presses Enter        | System submits the print job to the background job queue. Displays success message in green: "{Monthly/Yearly/Custom} report submitted for printing ..." All input fields are cleared and the cursor returns to the Monthly selection field. |
| 6    | User   | Presses F3 to return to the Main Menu                         | System navigates back to the Main Menu.                                                                      |

---

## Section 3: Business Rules & Validations

### Input Validation Rules

| #   | Field              | Rule                                                                                   | Error Message Shown to User                          |
|-----|--------------------|----------------------------------------------------------------------------------------|------------------------------------------------------|
| V1  | Report Type        | At least one report type must be selected (Monthly, Yearly, or Custom)                 | "Select a report type to print report..."            |
| V2  | Start Date — Month | Must not be empty when Custom report is selected                                       | "Start Date - Month can NOT be empty..."             |
| V3  | Start Date — Day   | Must not be empty when Custom report is selected                                       | "Start Date - Day can NOT be empty..."               |
| V4  | Start Date — Year  | Must not be empty when Custom report is selected                                       | "Start Date - Year can NOT be empty..."              |
| V5  | End Date — Month   | Must not be empty when Custom report is selected                                       | "End Date - Month can NOT be empty..."               |
| V6  | End Date — Day     | Must not be empty when Custom report is selected                                       | "End Date - Day can NOT be empty..."                 |
| V7  | End Date — Year    | Must not be empty when Custom report is selected                                       | "End Date - Year can NOT be empty..."                |
| V8  | Start Date — Month | Must be numeric and not greater than 12                                                | "Start Date - Not a valid Month..."                  |
| V9  | Start Date — Day   | Must be numeric and not greater than 31                                                | "Start Date - Not a valid Day..."                    |
| V10 | Start Date — Year  | Must be numeric                                                                        | "Start Date - Not a valid Year..."                   |
| V11 | End Date — Month   | Must be numeric and not greater than 12                                                | "End Date - Not a valid Month..."                    |
| V12 | End Date — Day     | Must be numeric and not greater than 31                                                | "End Date - Not a valid Day..."                      |
| V13 | End Date — Year    | Must be numeric                                                                        | "End Date - Not a valid Year..."                     |
| V14 | Start Date         | Must be a valid calendar date (e.g., Feb 30 is rejected)                               | "Start Date - Not a valid date..."                   |
| V15 | End Date           | Must be a valid calendar date (e.g., Feb 30 is rejected)                               | "End Date - Not a valid date..."                     |
| V16 | Confirmation       | Must not be empty when a valid report type has been selected                           | "Please confirm to print the {report-name} report..."|
| V17 | Confirmation       | Must be 'Y', 'y', 'N', or 'n'                                                         | '"{value}" is not a valid value to confirm...'       |

### Business Rules

| #   | Rule                                                                                                       |
|-----|------------------------------------------------------------------------------------------------------------|
| BR1 | **Report type selection is mutually exclusive by priority.** If the user marks more than one report type, the system processes the first one found in this order: Monthly, Yearly, Custom. The remaining selections are ignored. |
| BR2 | **Monthly report date range is auto-calculated.** When Monthly is selected, the system automatically sets the start date to the first day of the current month and the end date to the last day of the current month. No manual date entry is required. |
| BR3 | **Yearly report date range is auto-calculated.** When Yearly is selected, the system automatically sets the start date to January 1 and the end date to December 31 of the current year. No manual date entry is required. |
| BR4 | **Custom report requires explicit date entry.** When Custom is selected, the user must provide both a start date and an end date in MM/DD/YYYY format. |
| BR5 | **Confirmation is mandatory before job submission.** The system will not submit the print job until the user explicitly confirms with 'Y' or 'y'. |
| BR6 | **Declining confirmation cancels the request.** If the user enters 'N' or 'n' at the confirmation prompt, all input fields are cleared and no job is submitted. No error message is displayed. |
| BR7 | **Custom date validation is sequential.** Validations are checked one at a time in order (V2 through V15). The first failing validation stops further checks and the cursor is positioned on the failing field. |
| BR8 | **Report submission is a background process.** The print job is queued for background processing. The user does not wait for the report to be generated — they receive immediate confirmation that the job was submitted. |
| BR9 | **Successful submission clears all fields.** After a successful job submission, all input fields (report type selections, date fields, confirmation) are reset to blank and the success message is displayed in green. |
| BR10| **Session context is required.** If the application detects that no session context exists (direct access without signing in), the user is redirected to the Sign-on screen. |

### Decision Table

```
┌───────────────────────────────┬──────────────────────────────────────────────────┐
│ User Action                   │ System Response                                  │
├───────────────────────────────┼──────────────────────────────────────────────────┤
│ Press Enter with Monthly      │ Auto-calculate current month date range,         │
│   selected                    │   prompt for confirmation                        │
├───────────────────────────────┼──────────────────────────────────────────────────┤
│ Press Enter with Yearly       │ Auto-calculate current year date range,          │
│   selected                    │   prompt for confirmation                        │
├───────────────────────────────┼──────────────────────────────────────────────────┤
│ Press Enter with Custom       │ Validate all date fields (V2-V15),              │
│   selected + valid dates      │   prompt for confirmation                        │
├───────────────────────────────┼──────────────────────────────────────────────────┤
│ Press Enter with Custom       │ Display first validation error found,            │
│   selected + invalid dates    │   cursor on failing field                        │
├───────────────────────────────┼──────────────────────────────────────────────────┤
│ Press Enter with no report    │ Display "Select a report type to print           │
│   type selected               │   report..."                                     │
├───────────────────────────────┼──────────────────────────────────────────────────┤
│ Confirm with 'Y' or 'y'      │ Submit print job, display green success          │
│                               │   message, clear all fields                      │
├───────────────────────────────┼──────────────────────────────────────────────────┤
│ Confirm with 'N' or 'n'      │ Cancel request, clear all fields,               │
│                               │   no message displayed                           │
├───────────────────────────────┼──────────────────────────────────────────────────┤
│ Confirm with invalid value    │ Display '"{value}" is not a valid value          │
│                               │   to confirm...'                                 │
├───────────────────────────────┼──────────────────────────────────────────────────┤
│ Press F3                      │ Return to Main Menu                              │
├───────────────────────────────┼──────────────────────────────────────────────────┤
│ Press any other key           │ Display "Invalid key pressed. Please see         │
│                               │   below..."                                      │
├───────────────────────────────┼──────────────────────────────────────────────────┤
│ Access without signing in     │ Redirect to Sign-on screen                       │
└───────────────────────────────┴──────────────────────────────────────────────────┘
```

---

## Section 4: Data Entities Involved

### Entity Descriptions

| Entity              | Description                                                                  | Role in This Use Case                                                              |
|---------------------|------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| Transaction Records | Individual credit card transaction records containing card number, amount, merchant details, timestamps, and categorization | Processed by the background print job (not accessed directly by this use case). The date range parameters submitted by the user determine which transactions appear in the report. |
| User Security       | User authentication and authorization records containing user ID, password, and user type | Read during sign-on (upstream). Determines whether the user can access this function. |
| Print Job Queue     | System queue that receives submitted print jobs for background processing    | This use case writes to the queue. Each report submission creates one complete job entry containing the report parameters and processing instructions. |

### Entity Relationships

```
┌─────────────────────┐
│   User Security     │
│   (Authentication)  │
└─────────┬───────────┘
          │ authenticates
          v
┌─────────────────────┐         ┌─────────────────────────┐
│   User Session      │ submits │   Print Job Queue       │
│   (Online)          │────────>│   (Background Jobs)     │
└─────────────────────┘         └────────────┬────────────┘
                                             │ reads
                                             v
                                ┌─────────────────────────┐
                                │  Transaction Records    │
                                │  (1 account : N txns)   │
                                └─────────────────────────┘
```

### Data Fields Displayed

| #  | Field              | Format      | Source         | Description                                       |
|----|--------------------|-------------|----------------|---------------------------------------------------|
| 1  | Transaction ID     | Display     | Header         | Current transaction identifier ("CR00")           |
| 2  | Program Name       | Display     | Header         | Current program name ("CORPT00C")                 |
| 3  | Application Title  | Display     | Header         | "AWS Mainframe Modernization" / "CardDemo"        |
| 4  | Current Date       | MM/DD/YY    | System clock   | Today's date                                      |
| 5  | Current Time       | HH:MM:SS    | System clock   | Current time                                      |
| 6  | Monthly Selection  | 1 character | User input     | Any non-blank character selects Monthly report    |
| 7  | Yearly Selection   | 1 character | User input     | Any non-blank character selects Yearly report     |
| 8  | Custom Selection   | 1 character | User input     | Any non-blank character selects Custom report     |
| 9  | Start Date Month   | 2 digits    | User input     | Month portion of custom start date (01-12)        |
| 10 | Start Date Day     | 2 digits    | User input     | Day portion of custom start date (01-31)          |
| 11 | Start Date Year    | 4 digits    | User input     | Year portion of custom start date (YYYY)          |
| 12 | End Date Month     | 2 digits    | User input     | Month portion of custom end date (01-12)          |
| 13 | End Date Day       | 2 digits    | User input     | Day portion of custom end date (01-31)            |
| 14 | End Date Year      | 4 digits    | User input     | Year portion of custom end date (YYYY)            |
| 15 | Confirmation       | 1 character | User input     | Y/N confirmation before job submission            |
| 16 | Error/Status Msg   | Up to 78 ch | System         | Error messages (red) or success messages (green)  |

> **Note:** The underlying transaction record contains additional fields (transaction type, category, source, description, amount, merchant ID, merchant name, merchant city, merchant ZIP, card number, original timestamp, processing timestamp) that are NOT displayed on this screen. These fields are processed by the background print job when generating the actual report.

---

## Section 5: Screen / Interface Description

### Screen Layout

```
+--------------------------------------------------------------------------------+
| Tran: CR00     AWS Mainframe Modernization            Date: 05/07/26           |
| Prog: CORPT00C              CardDemo                  Time: 21:45:30           |
|                                                                                |
|                              Transaction Reports                               |
|                                                                                |
|                                                                                |
|          _  Monthly (Current Month)                                            |
|                                                                                |
|          _  Yearly (Current Year)                                              |
|                                                                                |
|          _  Custom (Date Range)                                                |
|                                                                                |
|               Start Date : __/__/____  (MM/DD/YYYY)                            |
|                 End Date : __/__/____  (MM/DD/YYYY)                            |
|                                                                                |
|                                                                                |
|                                                                                |
|                                                                                |
|      The Report will be submitted for printing. Please confirm: _  (Y/N)      |
|                                                                                |
|                                                                                |
|                                                                                |
|                                                                                |
| ENTER=Continue  F3=Back                                                        |
+--------------------------------------------------------------------------------+
```

> Screen is 24 rows by 80 columns. Underscores (`_`) represent input fields. The error/status message area on row 23 is shown blank above; error messages appear in red, success messages in green.

### Interface Elements

| Element            | Type    | Description                                                                    |
|--------------------|---------|--------------------------------------------------------------------------------|
| Monthly Selection  | Input   | Single-character field. Enter any non-blank character to select Monthly report. Cursor starts here on initial entry. |
| Yearly Selection   | Input   | Single-character field. Enter any non-blank character to select Yearly report.  |
| Custom Selection   | Input   | Single-character field. Enter any non-blank character to select Custom report.  |
| Start Date Month   | Input   | Two-digit numeric field for the month of the custom start date.                |
| Start Date Day     | Input   | Two-digit numeric field for the day of the custom start date.                  |
| Start Date Year    | Input   | Four-digit numeric field for the year of the custom start date.                |
| End Date Month     | Input   | Two-digit numeric field for the month of the custom end date.                  |
| End Date Day       | Input   | Two-digit numeric field for the day of the custom end date.                    |
| End Date Year      | Input   | Four-digit numeric field for the year of the custom end date.                  |
| Confirmation       | Input   | Single-character field. Enter Y to confirm or N to cancel the report request.  |
| Error/Status Area  | Display | 78-character message area on row 23. Shows error messages in red or success messages in green. |
| Header Info        | Display | Transaction ID, program name, application titles, current date and time.       |
| Function Key Bar   | Display | Shows available keys: "ENTER=Continue  F3=Back"                               |

### Available Actions

| Action               | How to Invoke | Description                                                                    |
|----------------------|---------------|--------------------------------------------------------------------------------|
| Submit Report        | Enter key     | Validates inputs, prompts for confirmation, and submits the print job if confirmed |
| Return to Main Menu  | F3 key        | Returns to the Main Menu without submitting a report                           |
| Navigate Fields      | Tab key       | Moves cursor between input fields on the screen                                |

---

## Section 6: Alternative & Error Flows

### AF1: No Report Type Selected

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User presses Enter without marking any report type selection field.                                 |
| 2    | System displays error message: "Select a report type to print report..."                            |
| 3    | Cursor is positioned on the Monthly selection field.                                                |
| 4    | User can correct the selection and press Enter again, or press F3 to return to the Main Menu.       |

### AF2: Custom Report — Empty Date Field

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User selects Custom report type and presses Enter with one or more date fields left blank.          |
| 2    | System checks date fields in order: Start Month, Start Day, Start Year, End Month, End Day, End Year. |
| 3    | System displays an error for the first empty field found (e.g., "Start Date - Month can NOT be empty..."). |
| 4    | Cursor is positioned on the empty field.                                                            |
| 5    | User enters the missing value and presses Enter to retry validation.                                |

### AF3: Custom Report — Invalid Date Component

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User selects Custom report type and enters non-numeric or out-of-range values in a date field.      |
| 2    | System checks fields in order: Start Month (must be numeric, max 12), Start Day (must be numeric, max 31), Start Year (must be numeric), End Month, End Day, End Year. |
| 3    | System displays an error for the first invalid field found (e.g., "Start Date - Not a valid Month..."). |
| 4    | Cursor is positioned on the invalid field.                                                          |
| 5    | User corrects the value and presses Enter to retry validation.                                      |

### AF4: Custom Report — Invalid Calendar Date

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User enters date components that pass individual field checks but form an invalid calendar date (e.g., February 30). |
| 2    | System performs full calendar validation on the assembled date.                                      |
| 3    | System displays "Start Date - Not a valid date..." or "End Date - Not a valid date..." as appropriate. |
| 4    | Cursor is positioned on the month field of the invalid date.                                        |
| 5    | User corrects the date and presses Enter to retry validation.                                       |

### AF5: Invalid Confirmation Value

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User enters a character other than Y, y, N, or n in the confirmation field and presses Enter.       |
| 2    | System displays '"{value}" is not a valid value to confirm...' where {value} is the character entered. |
| 3    | Cursor is positioned on the confirmation field.                                                     |
| 4    | User enters a valid confirmation value (Y or N) and presses Enter.                                  |

### AF6: User Declines Confirmation

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User enters 'N' or 'n' in the confirmation field and presses Enter.                                |
| 2    | System clears all input fields (report type selections, date fields, confirmation).                 |
| 3    | No error message is displayed. Cursor returns to the Monthly selection field.                       |
| 4    | User can select a new report type or press F3 to return to the Main Menu.                           |

### AF7: Empty Confirmation Field

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User presses Enter after selecting a valid report type but leaves the confirmation field blank.      |
| 2    | System displays "Please confirm to print the {report-name} report..."                               |
| 3    | Cursor is positioned on the confirmation field.                                                     |
| 4    | User enters Y or N and presses Enter.                                                              |

### AF8: Invalid Key Pressed

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User presses any key other than Enter or F3.                                                        |
| 2    | System displays "Invalid key pressed. Please see below..."                                          |
| 3    | Cursor is positioned on the Monthly selection field.                                                |
| 4    | User presses a valid key (Enter or F3).                                                             |

### AF9: Print Job Queue Write Failure

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User confirms the report submission with 'Y'.                                                       |
| 2    | System attempts to write the job to the print job queue but encounters a system error.              |
| 3    | System displays "Unable to Write TDQ (JOBS)..."                                                     |
| 4    | Cursor is positioned on the Monthly selection field.                                                |
| 5    | User may retry by pressing Enter again, or press F3 to return to the Main Menu.                    |

### AF10: Direct Access Without Sign-on

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User (or system) invokes the Report Submission function without an active session.                  |
| 2    | System detects that no session context exists.                                                      |
| 3    | System redirects the user to the Sign-on screen.                                                    |

---

## Section 7: Business Process Context

### Navigation Context

```
CardDemo Application
│
├── CC00 — Sign-on
│   │
│   ├── CM00 — Main Menu (Regular Users)
│   │   │
│   │   ├── Account View
│   │   ├── Account Update
│   │   ├── Card List
│   │   ├── Card Detail View
│   │   ├── Card Update
│   │   ├── Transaction List
│   │   ├── Transaction View
│   │   ├── Transaction Add
│   │   ├── Bill Payment
│   │   │
│   │   └── *** CR00 — Report Submission (THIS USE CASE) ***
│   │
│   └── CA00 — Admin Menu (Admin Users)
│       │
│       ├── User List
│       ├── User Add
│       ├── User Update
│       └── User Delete
```

### Downstream Use Cases

| Destination                    | Trigger                                             | What Happens Next                                                    |
|--------------------------------|-----------------------------------------------------|----------------------------------------------------------------------|
| Background Print Job           | User confirms report submission with 'Y'            | A background job is queued to read the transaction file, filter by the specified date range, and produce a printed transaction report. |
| Main Menu (CM00)               | User presses F3                                     | User returns to the Main Menu and can select any other application function. |

### Upstream Use Cases

| Source                         | Navigation Path                                                              |
|--------------------------------|------------------------------------------------------------------------------|
| Sign-on (CC00)                 | User authenticates → Main Menu → Option 9 "Transaction Reports"             |
| Main Menu (CM00)               | User selects option 9 "Transaction Reports" from the Main Menu              |

### End-to-End Journey Examples

**Journey 1: Monthly Report for Reconciliation**
```
Sign-on → Main Menu → Report Submission → Select Monthly → Confirm → Job Submitted → Return to Menu
```
> *Business Scenario:* At month-end, a card operations analyst generates a monthly transaction report for reconciliation with the general ledger.

**Journey 2: Yearly Report for Annual Audit**
```
Sign-on → Main Menu → Report Submission → Select Yearly → Confirm → Job Submitted → Return to Menu
```
> *Business Scenario:* During annual audit preparation, a compliance officer generates a yearly transaction report covering all transactions for the current year.

**Journey 3: Custom Report for Dispute Investigation**
```
Sign-on → Main Menu → Transaction List → Identify date range → Return to Menu → Report Submission → Select Custom → Enter dates → Confirm → Job Submitted → Return to Menu
```
> *Business Scenario:* A fraud analyst reviews the transaction list to identify the date range of suspicious activity, then generates a custom-range report for detailed investigation.

**Journey 4: Report Request Cancelled**
```
Sign-on → Main Menu → Report Submission → Select Monthly → Decline (N) → Fields Cleared → F3 → Return to Menu
```
> *Business Scenario:* A user begins a report request but realizes the wrong report period and cancels before submission, returning to the menu to review account details first.

---

## Section 8: Acceptance Criteria

### Core Functionality

| #     | Given                                                        | When                                            | Then                                                                                       |
|-------|--------------------------------------------------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------|
| AC-01 | User is on the Report Submission screen                      | User marks Monthly and presses Enter             | System prompts for confirmation with "Please confirm to print the Monthly report..."       |
| AC-02 | User is on the Report Submission screen                      | User marks Yearly and presses Enter              | System prompts for confirmation with "Please confirm to print the Yearly report..."        |
| AC-03 | User is on the Report Submission screen with Custom selected and valid dates entered | User presses Enter | System prompts for confirmation with "Please confirm to print the Custom report..."        |
| AC-04 | System has prompted for confirmation                         | User enters 'Y' and presses Enter                | Print job is submitted; success message "{report} report submitted for printing ..." is displayed in green; all input fields are cleared |
| AC-05 | System has prompted for confirmation                         | User enters 'y' and presses Enter                | Print job is submitted (same behavior as 'Y' — case-insensitive confirmation)              |
| AC-06 | Monthly report is confirmed                                  | Print job is submitted                           | Date range covers the first day through the last day of the current calendar month         |
| AC-07 | Yearly report is confirmed                                   | Print job is submitted                           | Date range covers January 1 through December 31 of the current calendar year               |

### Input Validation

| #     | Given                                                        | When                                            | Then                                                                                       |
|-------|--------------------------------------------------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------|
| AC-08 | User is on the Report Submission screen                      | User presses Enter with no report type selected  | Error message "Select a report type to print report..." is displayed; cursor on Monthly field |
| AC-09 | Custom report is selected                                    | User presses Enter with Start Date Month blank   | Error "Start Date - Month can NOT be empty..." is displayed; cursor on Start Month field   |
| AC-10 | Custom report is selected                                    | User presses Enter with Start Date Day blank     | Error "Start Date - Day can NOT be empty..." is displayed; cursor on Start Day field       |
| AC-11 | Custom report is selected                                    | User presses Enter with Start Date Year blank    | Error "Start Date - Year can NOT be empty..." is displayed; cursor on Start Year field     |
| AC-12 | Custom report is selected                                    | User presses Enter with End Date Month blank     | Error "End Date - Month can NOT be empty..." is displayed; cursor on End Month field       |
| AC-13 | Custom report is selected                                    | User presses Enter with End Date Day blank       | Error "End Date - Day can NOT be empty..." is displayed; cursor on End Day field           |
| AC-14 | Custom report is selected                                    | User presses Enter with End Date Year blank      | Error "End Date - Year can NOT be empty..." is displayed; cursor on End Year field         |
| AC-15 | Custom report is selected                                    | User enters month > 12 for Start Date            | Error "Start Date - Not a valid Month..." is displayed                                     |
| AC-16 | Custom report is selected                                    | User enters day > 31 for Start Date              | Error "Start Date - Not a valid Day..." is displayed                                       |
| AC-17 | Custom report is selected                                    | User enters non-numeric value for Start Year     | Error "Start Date - Not a valid Year..." is displayed                                      |
| AC-18 | Custom report is selected                                    | User enters month > 12 for End Date              | Error "End Date - Not a valid Month..." is displayed                                       |
| AC-19 | Custom report is selected                                    | User enters day > 31 for End Date                | Error "End Date - Not a valid Day..." is displayed                                         |
| AC-20 | Custom report is selected                                    | User enters non-numeric value for End Year       | Error "End Date - Not a valid Year..." is displayed                                        |
| AC-21 | Custom report is selected with all fields numeric and in range | User enters an invalid calendar date (e.g., 02/30/2025) for Start Date | Error "Start Date - Not a valid date..." is displayed |
| AC-22 | Custom report is selected with all fields numeric and in range | User enters an invalid calendar date for End Date | Error "End Date - Not a valid date..." is displayed |

### Confirmation Handling

| #     | Given                                                        | When                                            | Then                                                                                       |
|-------|--------------------------------------------------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------|
| AC-23 | System has prompted for confirmation                         | User enters 'N' and presses Enter                | All input fields are cleared; no error message is displayed; cursor returns to Monthly field |
| AC-24 | System has prompted for confirmation                         | User enters 'n' and presses Enter                | Same behavior as 'N' — case-insensitive cancellation                                       |
| AC-25 | System has prompted for confirmation                         | User enters an invalid character (e.g., 'X')     | Error '"{value}" is not a valid value to confirm...' is displayed; cursor on confirmation field |
| AC-26 | Valid report type selected, confirmation field blank          | User presses Enter                               | Message "Please confirm to print the {report} report..." is displayed; cursor on confirmation field |

### Navigation

| #     | Given                                                        | When                                            | Then                                                                                       |
|-------|--------------------------------------------------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------|
| AC-27 | User is on the Report Submission screen                      | User presses F3                                  | System returns to the Main Menu                                                            |
| AC-28 | User is on the Report Submission screen                      | User presses any key other than Enter or F3      | Error "Invalid key pressed. Please see below..." is displayed                              |
| AC-29 | No active session exists (direct access)                     | System attempts to display the Report Submission screen | User is redirected to the Sign-on screen               |

### Data Integrity

| #     | Given                                                        | When                                            | Then                                                                                       |
|-------|--------------------------------------------------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------|
| AC-30 | Print job has been submitted successfully                    | System displays success message                  | All input fields (report type, date fields, confirmation) are reset to blank               |
| AC-31 | Print job queue encounters a write error                     | System attempts to submit the job                | Error "Unable to Write TDQ (JOBS)..." is displayed; no partial job is submitted            |

---

## Section 9: Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                       | Technical Reference                                                                                    |
|-----------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| User selects option 9 from Main Menu                | COMEN01C evaluates menu option, sets CDEMO-TO-PROGRAM = 'CORPT00C', EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM) |
| System displays Report Submission screen             | CORPT00C MAIN-PARA: detects NOT CDEMO-PGM-REENTER, MOVE LOW-VALUES TO CORPT0AO, PERFORM SEND-TRNRPT-SCREEN (EXEC CICS SEND MAP('CORPT0A') MAPSET('CORPT00') ... ERASE CURSOR) |
| System waits for user input (pseudo-conversational) | EXEC CICS RETURN TRANSID('CR00') COMMAREA(CARDDEMO-COMMAREA) at line 199-202                          |
| System receives user input                          | PERFORM RECEIVE-TRNRPT-SCREEN (EXEC CICS RECEIVE MAP('CORPT0A') MAPSET('CORPT00') INTO(CORPT0AI)) at lines 596-604 |
| Monthly report type detected                        | PROCESS-ENTER-KEY: EVALUATE TRUE — WHEN MONTHLYI OF CORPT0AI NOT = SPACES AND LOW-VALUES (line 213)   |
| Monthly date range calculated                       | Lines 215-236: FUNCTION CURRENT-DATE, WS-START-DATE-YYYY/MM/DD = current year/month/01; end date computed via INTEGER-OF-DATE / DATE-OF-INTEGER arithmetic for last day of month |
| Yearly report type detected                         | PROCESS-ENTER-KEY: WHEN YEARLYI OF CORPT0AI NOT = SPACES AND LOW-VALUES (line 239)                    |
| Yearly date range calculated                        | Lines 241-253: Start = YYYY-01-01, End = YYYY-12-31 using FUNCTION CURRENT-DATE                       |
| Custom report type detected                         | PROCESS-ENTER-KEY: WHEN CUSTOMI OF CORPT0AI NOT = SPACES AND LOW-VALUES (line 256)                    |
| Custom date field empty checks (V2-V7)              | Lines 258-303: EVALUATE TRUE with WHEN clauses checking each field against SPACES OR LOW-VALUES        |
| Custom date numeric/range checks (V8-V13)           | Lines 305-378: FUNCTION NUMVAL-C for normalization, then IF NOT NUMERIC OR > '12'/'31' checks          |
| Full calendar date validation (V14-V15)             | Lines 388-426: CALL 'CSUTLDTC' USING CSUTLDTC-DATE, CSUTLDTC-DATE-FORMAT, CSUTLDTC-RESULT; checks CSUTLDTC-RESULT-SEV-CD = '0000' |
| Confirmation empty check (V16)                      | SUBMIT-JOB-TO-INTRDR: IF CONFIRMI OF CORPT0AI = SPACES OR LOW-VALUES (line 464)                       |
| Confirmation value check (V17)                      | Lines 477-494: EVALUATE TRUE — WHEN 'Y' OR 'y', WHEN 'N' OR 'n', WHEN OTHER                          |
| Job submission to print queue                       | Lines 496-508: PERFORM VARYING WS-IDX FROM 1 BY 1 ... MOVE JOB-LINES(WS-IDX) TO JCL-RECORD ... PERFORM WIRTE-JOBSUB-TDQ |
| Write to print queue                                | WIRTE-JOBSUB-TDQ: EXEC CICS WRITEQ TD QUEUE('JOBS') FROM(JCL-RECORD) LENGTH(LENGTH OF JCL-RECORD) (lines 517-523) |
| Queue write error handling                          | Lines 525-535: EVALUATE WS-RESP-CD — DFHRESP(NORMAL) → continue, OTHER → error message               |
| Success message display                             | Lines 445-456: STRING WS-REPORT-NAME ' report submitted for printing ...' INTO WS-MESSAGE; ERRMSGC = DFHGREEN |
| Field initialization after success/decline          | INITIALIZE-ALL-FIELDS (lines 633-646): INITIALIZE all input fields and WS-MESSAGE                     |
| Return to Main Menu (F3)                            | RETURN-TO-PREV-SCREEN: MOVE 'COMEN01C' TO CDEMO-TO-PROGRAM, EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA) (lines 540-551) |
| Redirect to Sign-on (no session)                    | MAIN-PARA: IF EIBCALEN = 0, MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM, PERFORM RETURN-TO-PREV-SCREEN (lines 172-174) |
| Invalid key handling                                | Lines 190-194: MOVE 'Y' TO WS-ERR-FLG, MOVE CCDA-MSG-INVALID-KEY TO WS-MESSAGE                      |
| No report type selected                             | Lines 437-442: WHEN OTHER in EVALUATE, MOVE 'Select a report type to print report...' TO WS-MESSAGE  |
| Header population (date/time/titles)                | POPULATE-HEADER-INFO (lines 609-628): FUNCTION CURRENT-DATE, move CCDA-TITLE01/02, WS-TRANID, WS-PGMNAME, formatted date/time to screen output fields |

### Source File Mapping

| Business Concept                          | Source File(s)                                                                          |
|-------------------------------------------|-----------------------------------------------------------------------------------------|
| Report Submission screen and logic        | `app/cbl/CORPT00C.cbl` (649 lines)                                                     |
| Screen layout and field definitions       | `app/bms/CORPT00.bms` (Mapset: CORPT00, Map: CORPT0A)                                  |
| Date validation utility                   | `app/cbl/CSUTLDTC.cbl`                                                                 |
| Session navigation context (COMMAREA)     | `app/cpy/COCOM01Y.cpy` (CARDDEMO-COMMAREA)                                             |
| Transaction record structure              | `app/cpy/CVTRA05Y.cpy` (TRAN-RECORD, 350 bytes)                                        |
| Application titles                        | `app/cpy/COTTL01Y.cpy` (CCDA-TITLE01, CCDA-TITLE02)                                    |
| Date/time formatting                      | `app/cpy/CSDAT01Y.cpy` (WS-DATE-TIME structure)                                        |
| Common error messages                     | `app/cpy/CSMSG01Y.cpy` (CCDA-MSG-INVALID-KEY)                                          |
| Main Menu navigation and options          | `app/cbl/COMEN01C.cbl`, `app/cpy/COMEN02Y.cpy`                                         |
| User authentication                       | `app/cbl/COSGN00C.cbl`, `app/cpy/CSUSR01Y.cpy`                                         |

### Migration Considerations

| #   | Consideration                                                                                                                                                                   |
|-----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| M1  | **Print job queue replacement.** The extra-partition Transient Data Queue (TDQ) routed to the MVS internal reader is a mainframe-specific mechanism for submitting batch jobs from online programs. In a modernized environment, replace with an asynchronous job submission API (e.g., REST call to a job scheduler, message queue like SQS/RabbitMQ, or serverless function trigger). |
| M2  | **Embedded job instructions.** The batch job control statements are hardcoded in COBOL working storage as 80-byte fixed-length records (JOB-DATA structure). Replace with parameterized job templates or configuration files that can be maintained independently of the application code. |
| M3  | **Date validation utility replacement.** The CSUTLDTC sub-program wraps the IBM Language Environment CEEDAYS intrinsic for calendar date validation. Replace with the target platform's native date parsing/validation library (e.g., `java.time.LocalDate.parse()`, `datetime.strptime()`, `Date.parse()`). |
| M4  | **Pseudo-conversational pattern.** The program uses CICS pseudo-conversational design (RETURN TRANSID with COMMAREA) to maintain state across user interactions. In a modern web application, this maps naturally to HTTP request/response with server-side session state or client-side state management. |
| M5  | **Screen map to UI component.** The BMS map (CORPT0A) defines a fixed 24x80 terminal screen with specific field positions and attributes. Replace with a responsive web form containing radio buttons for report type, date picker components for custom date range, and a confirmation dialog or button. |
| M6  | **Field-position-based record references.** The batch job's SYMNAMES DD uses physical byte offsets (e.g., TRAN-CARD-NUM at offset 263, TRAN-PROC-DT at offset 305) to reference transaction record fields. In a modernized data layer, replace with named column references in SQL or an ORM. |
| M7  | **No audit trail.** The current implementation does not log report submission requests to any persistent store. The modernized version should include an audit log capturing who submitted what report, when, and with which parameters. |
| M8  | **Program navigation via XCTL.** Inter-program navigation uses CICS XCTL (transfer control) with COMMAREA. Replace with standard web application routing (URL-based navigation with session context). |
| M9  | **Session validation.** The EIBCALEN = 0 check (no COMMAREA) serves as a rudimentary session validation. Replace with proper authentication middleware and session management in the modern application. |

---

## Section 10: Related Use Cases / End-to-End Composition

| Journey Name                         | Use Cases Involved                                           | Business Scenario                                                                                  |
|--------------------------------------|--------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| Monthly Reconciliation               | Sign-on (CC00) → Main Menu (CM00) → Report Submission (CR00) | Card operations analyst generates a monthly transaction report at month-end for reconciliation with the general ledger. |
| Annual Audit Report                  | Sign-on (CC00) → Main Menu (CM00) → Report Submission (CR00) | Compliance officer produces a yearly transaction report for annual audit preparation.               |
| Dispute Investigation                | Sign-on (CC00) → Main Menu (CM00) → Transaction List (CT01) → Main Menu (CM00) → Report Submission (CR00) | Fraud analyst reviews the transaction list to identify a suspicious date range, then generates a custom-range report for detailed investigation. |
| End-of-Day Operations                | Sign-on (CC00) → Main Menu (CM00) → Transaction Add (CT02) → Main Menu (CM00) → Report Submission (CR00) | Operations staff enters daily transactions, then generates a custom report covering the current business day for supervisory review. |
| Account Review with Report           | Sign-on (CC00) → Main Menu (CM00) → Account View (CAVW) → Main Menu (CM00) → Report Submission (CR00) | Account manager reviews account details, then generates a monthly report to include in the customer's periodic statement package. |

> **Note:** Individual use case flows referenced above (Sign-on, Main Menu, Transaction List, Transaction Add, Account View) should be composed from their respective Business Use Case Flow documents for complete end-to-end validation. Use case flows for some of these may not yet exist and would need to be created.

---

## Document Footer

| Attribute                  | Value                                                     |
|----------------------------|-----------------------------------------------------------|
| **Source Program**         | CORPT00C.cbl (649 lines)                                 |
| **Transaction ID**         | CR00                                                      |
| **Data Source**            | Technical Flow Analysis (`docs/CORPT00C_Flow_Analysis.md`) verified against COBOL source code |
| **Acceptance Criteria**    | 31 (AC-01 through AC-31)                                  |
| **Business Rules**         | 10 (BR1 through BR10)                                     |
| **Validation Rules**       | 17 (V1 through V17)                                       |
| **Alternative/Error Flows**| 10 (AF1 through AF10)                                     |

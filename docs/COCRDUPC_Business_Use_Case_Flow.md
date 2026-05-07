# CCUP — Credit Card Update: Business Use Case Flow

## Section 1 — Use Case Overview

| Attribute        | Value                                                                 |
|------------------|-----------------------------------------------------------------------|
| **Use Case Name** | Update Credit Card Details                                           |
| **Use Case ID**  | UC-CCUP-001                                                           |
| **Actor(s)**     | Card Operations User (authenticated, user type)                       |
| **Business Goal** | Allow a user to modify the editable attributes of an existing credit card record (cardholder name, active status, expiry month/year) |
| **Preconditions** | 1. User is signed in to the CardDemo application<br>2. User has navigated to the Credit Card Update function (via Main Menu option 5 or from the Credit Card List screen)<br>3. The target credit card exists in the card database |
| **Postconditions** | The card record is updated with the new values; the original record is overwritten |
| **Trigger**      | User selects "Credit Card Update" from the Main Menu, or selects a card from the Credit Card List and navigates to the update screen |
| **Data Access**  | Read-Write (card database)                                            |
| **Frequency**    | On-demand — initiated by users as part of card maintenance operations |

### Business Context

The Credit Card Update function is a core maintenance operation within the CardDemo credit card management system. It enables card operations staff to modify key card attributes — specifically the embossed cardholder name, the card's active/inactive status, and the expiration date (month and year). The function enforces a confirmation workflow to prevent accidental changes: users must review their modifications and explicitly confirm before the update is committed. The system also protects against concurrent modification by detecting if another user has changed the same record between the time it was displayed and the time the update is submitted.

```
+-----------------------------------------------------------------------+
|                    CardDemo Functional Domains                         |
+-----------------------------------------------------------------------+
|                                                                       |
|  +------------------+    +------------------+    +------------------+ |
|  | User Security    |    | Account Mgmt     |    | Card Management  | |
|  |  - Sign-on       |    |  - Account View  |    |  - Card List     | |
|  |  - User Admin    |    |  - Account Update|    |  - Card View     | |
|  +------------------+    +------------------+    | *- Card Update*  | |
|                                                  +------------------+ |
|  +------------------+    +------------------+    +------------------+ |
|  | Transaction Mgmt |    | Billing          |    | Reporting        | |
|  |  - Tran List     |    |  - Bill Payment  |    |  - Daily Tran    | |
|  |  - Tran View     |    +------------------+    |  - Monthly Tran  | |
|  |  - Tran Add      |                           +------------------+ |
|  +------------------+                                                 |
+-----------------------------------------------------------------------+
         * Card Update (this use case) highlighted *
```

---

## Section 2 — User Journey (Happy Path)

### Sequence Diagram

```
+---------+                              +------------------+
|  User   |                              |     System       |
+---------+                              +------------------+
     |                                          |
     |  1. Navigate to Credit Card Update       |
     |----------------------------------------->|
     |                                          |
     |  2. Display empty search screen          |
     |<-----------------------------------------|
     |                                          |
     |  3. Enter Account # and Card #,         |
     |     press Enter                          |
     |----------------------------------------->|
     |                                          |
     |  4. Validate search keys                 |
     |  5. Retrieve card from database          |
     |  6. Display card details (editable)      |
     |<-----------------------------------------|
     |                                          |
     |  7. Modify name/status/expiry,           |
     |     press Enter                          |
     |----------------------------------------->|
     |                                          |
     |  8. Validate all modified fields         |
     |  9. Display confirmation prompt          |
     |     "Changes validated.Press F5 to save" |
     |<-----------------------------------------|
     |                                          |
     | 10. Press F5 to confirm save             |
     |----------------------------------------->|
     |                                          |
     | 11. Lock record                          |
     | 12. Check for concurrent changes         |
     | 13. Write updated record to database     |
     | 14. Display success message              |
     |     "Changes committed to database"      |
     |<-----------------------------------------|
     |                                          |
     | 15. Press F3 to exit (or Enter for       |
     |     another update)                      |
     |----------------------------------------->|
     |                                          |
```

### Step-by-Step Narrative

| Step | Actor  | Action                                      | System Response                                                                                         |
|------|--------|---------------------------------------------|---------------------------------------------------------------------------------------------------------|
| 1    | User   | Selects Credit Card Update from Main Menu (option 5) or selects a card from the Credit Card List | System transfers control to the Credit Card Update function |
| 2    | System | —                                           | If arriving from Main Menu: displays an empty search screen with editable Account Number and Card Number fields. Prompt: "Please enter Account and Card Number" |
| 2a   | System | —                                           | If arriving from Credit Card List: Account and Card numbers are pre-filled; system automatically retrieves card details and skips to Step 6 |
| 3    | User   | Enters the 11-digit account number and 16-digit card number, presses Enter | — |
| 4    | System | —                                           | Validates that both fields are supplied, numeric, and non-zero |
| 5    | System | —                                           | Retrieves the card record from the card database using the card number as the lookup key |
| 6    | System | —                                           | Displays card details: Name on Card, Card Active Status (Y/N), Expiry Month, Expiry Year are editable. Account and Card numbers become display-only. Message: "Details of selected card shown above" |
| 7    | User   | Modifies one or more editable fields (name, status, expiry month, expiry year), presses Enter | — |
| 8    | System | —                                           | Validates all modified fields against business rules (name must be alphabetic, status must be Y or N, month must be 1-12, year must be 1950-2099). Detects whether any actual changes were made by comparing to original values |
| 9    | System | —                                           | If validations pass and changes detected: locks all fields as display-only; shows "Changes validated.Press F5 to save"; reveals F5=Save option |
| 10   | User   | Presses F5 to confirm the update             | — |
| 11   | System | —                                           | Acquires an exclusive lock on the card record in the database |
| 12   | System | —                                           | Compares current database record against the originally displayed values to detect any concurrent modifications by other users |
| 13   | System | —                                           | Writes the updated record (new name, status, expiry date formatted as YYYY-MM-DD) to the database |
| 14   | System | —                                           | Displays success message: "Changes committed to database". All fields remain display-only. |
| 15   | User   | Presses F3 to exit back to the calling screen, or presses Enter to start a new search | System returns to calling screen (Credit Card List or Main Menu), or resets for a new card search |

---

## Section 3 — Business Rules & Validations

### Input Validation Rules

| #  | Field           | Rule                                                                                          | Error Message Shown to User                                          |
|----|-----------------|-----------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| V1 | Account Number  | Must be provided (non-blank)                                                                   | "Account number not provided"                                        |
| V2 | Account Number  | Must be numeric and exactly 11 digits, non-zero                                                | "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"              |
| V3 | Card Number     | Must be provided (non-blank)                                                                   | "Card number not provided"                                           |
| V4 | Card Number     | Must be numeric and exactly 16 digits                                                          | "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"              |
| V5 | Name on Card    | Must be provided (non-blank)                                                                   | "Card name not provided"                                             |
| V6 | Name on Card    | Must contain only alphabetic characters (A-Z, a-z) and spaces — no digits, punctuation, or special characters | "Card name can only contain alphabets and spaces"                   |
| V7 | Card Active Status | Must be provided and must be either "Y" or "N"                                             | "Card Active Status must be Y or N"                                  |
| V8 | Expiry Month    | Must be provided, numeric, and between 1 and 12                                               | "Card expiry month must be between 1 and 12"                        |
| V9 | Expiry Year     | Must be provided, numeric, and between 1950 and 2099                                          | "Invalid card expiry year"                                           |

### Business Rules

| #   | Rule                                                                                                              |
|-----|-------------------------------------------------------------------------------------------------------------------|
| BR1 | **Search requires both keys**: Both Account Number and Card Number must be supplied together to search for a card. If both are blank, the system shows "No input received". |
| BR2 | **Card lookup by card number**: The system retrieves the card using the card number as the primary lookup key. The account number is validated but used for context rather than as a composite key. |
| BR3 | **Change detection is case-insensitive**: The system compares user-entered values against stored values using case-insensitive comparison. If no changes are detected, the message "No change detected with respect to values fetched." is displayed and no update occurs. |
| BR4 | **Explicit confirmation required**: After changes pass validation, the user must explicitly press F5 to confirm. Simply pressing Enter at the confirmation stage does not commit the update. |
| BR5 | **Optimistic concurrency control**: Before writing, the system re-reads the database record and compares all field values against what was originally displayed to the user. If any field was changed by another user, the update is rejected with "Record changed by some one else. Please review" and refreshed values are displayed. |
| BR6 | **Pessimistic record locking**: The system acquires an exclusive lock on the record before writing. If the lock cannot be obtained (e.g., another user holds it), the message "Could not lock record for update" is shown. |
| BR7 | **Expiry day is preserved**: The day portion of the expiry date is hidden from the user and cannot be modified. It is preserved from the original record during the update. |
| BR8 | **Name stored as entered**: The cardholder name is stored in the case entered by the user (mixed case is permitted during entry). However, when displaying the current name from the database, the system normalizes it to uppercase for consistent display. |
| BR9 | **Auto-return after completion (from Card List)**: If the user arrived from the Credit Card List and the update completes (success or failure), pressing Enter or any key triggers an automatic return to the Card List. |
| BR10 | **Cancel reverts to fetched values**: Pressing F12 after editing discards all user changes and re-displays the originally fetched card details. |
| BR11 | **Blank required fields shown as asterisk**: When a required field is left blank, the system displays a red asterisk (*) in the field as a visual indicator. |
| BR12 | **Only four fields are user-editable**: Only Name on Card, Card Active Status, Expiry Month, and Expiry Year can be modified. Card Number, Account Number, CVV, and Expiry Day are never editable. |

### Decision Table

```
+-----------------------------------------------+---------------------------------------------------+
| User Action                                   | System Response                                   |
+-----------------------------------------------+---------------------------------------------------+
| Enter with blank Account # and Card #         | Error: "No input received"                        |
| Enter with blank Account # only               | Error: "Account number not provided"              |
| Enter with blank Card # only                  | Error: "Card number not provided"                 |
| Enter with non-numeric Account #              | Error: "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11   |
|                                               |   DIGIT NUMBER"                                   |
| Enter with non-numeric Card #                 | Error: "CARD ID FILTER,IF SUPPLIED MUST BE A 16   |
|                                               |   DIGIT NUMBER"                                   |
| Enter with valid search keys, card found      | Display card details in editable mode             |
| Enter with valid search keys, card not found  | Error: "Did not find cards for this search        |
|                                               |   condition"                                      |
| Enter with no changes to displayed values     | Message: "No change detected with respect to      |
|                                               |   values fetched."                                |
| Enter with invalid name (has digits/symbols)  | Error: "Card name can only contain alphabets      |
|                                               |   and spaces"                                     |
| Enter with blank name                         | Error: "Card name not provided"                   |
| Enter with invalid status (not Y or N)        | Error: "Card Active Status must be Y or N"        |
| Enter with invalid month (0 or >12)           | Error: "Card expiry month must be between 1       |
|                                               |   and 12"                                         |
| Enter with invalid year (<1950 or >2099)      | Error: "Invalid card expiry year"                 |
| Enter with all valid changes                  | Confirmation prompt: "Changes validated.Press     |
|                                               |   F5 to save". All fields locked.                 |
| F5 at confirmation (no concurrent change)     | Success: "Changes committed to database"          |
| F5 at confirmation (concurrent change found)  | Error: "Record changed by some one else.          |
|                                               |   Please review" — refreshed values shown         |
| F5 at confirmation (lock failed)              | Error: "Could not lock record for update"         |
| F5 at confirmation (write failed)             | Error: "Update of record failed"                  |
| F3 at any point                               | Exit to calling screen (Card List or Main Menu)   |
| F12 while card details shown                  | Cancel edits; re-fetch and re-display original    |
|                                               |   values                                          |
| Any other key                                 | Treated as Enter (re-processes current screen)    |
+-----------------------------------------------+---------------------------------------------------+
```

---

## Section 4 — Data Entities Involved

### Entity Descriptions

| Entity            | Description                                                                 | Role in This Use Case                                         |
|-------------------|-----------------------------------------------------------------------------|---------------------------------------------------------------|
| Card              | Represents a physical credit card issued to a customer, linked to an account | Primary entity — read for display, updated with new values    |
| Account           | A financial account that may have one or more cards associated with it       | Context — the account number is used to identify the card's owning account (display only) |
| User Security     | The signed-in user's identity and permissions                               | Authentication context — determines who is performing the update |

### Entity Relationships

```
+-------------------+          +-------------------+
|     Account       |          |     Customer      |
| (Account Number)  |<-------->| (Customer ID)     |
+-------------------+          +-------------------+
        |
        | 1:N (one account may have multiple cards)
        v
+-------------------+
|      Card         |
| (Card Number) PK  |
| - Account ID      |
| - CVV Code        |
| - Embossed Name   |
| - Expiration Date |
| - Active Status   |
+-------------------+
```

### Data Fields Displayed

| #  | Field             | Format          | Source           | Description                                           |
|----|-------------------|-----------------|------------------|-------------------------------------------------------|
| 1  | Account Number    | 11 digits       | Card record      | The account to which this card belongs                 |
| 2  | Card Number       | 16 digits       | Card record      | Unique identifier of the credit card                  |
| 3  | Name on Card      | Up to 50 chars  | Card record      | The cardholder name embossed on the physical card      |
| 4  | Card Active (Y/N) | 1 character      | Card record      | Whether the card is currently active                   |
| 5  | Expiry Month      | 2 digits (01-12)| Card record      | Month portion of the card's expiration date            |
| 6  | Expiry Year       | 4 digits (YYYY) | Card record      | Year portion of the card's expiration date             |

**Fields NOT displayed on screen (exist in underlying record):**

| Field             | Description                                                   |
|-------------------|---------------------------------------------------------------|
| CVV Code          | 3-digit security code — preserved during update but never shown |
| Expiry Day        | Day portion of expiration date — hidden field, preserved from original |
| Filler            | 59 bytes of reserved/unused space in the card record           |

---

## Section 5 — Screen / Interface Description

### Screen Layout

```
+--------------------------------------------------------------------------------+
|Tran: CCUP   AWS Mainframe Modernization                 Date: 05/07/26         |
|Prog: COCRDUPC         CardDemo                          Time: 14:30:22         |
|                                                                                |
|                              Update Credit Card Details                         |
|                                                                                |
|                                                                                |
|                       Account Number    : 00000000041                           |
|                       Card Number       : 4444111122220001                      |
|                                                                                |
|                                                                                |
|    Name on card      : JOHN A SMITH                                            |
|                                                                                |
|    Card Active Y/N   : Y                                                       |
|                                                                                |
|    Expiry Date       : 12 / 2025  15                                           |
|                                                                                |
|                                                                                |
|                                                                                |
|                                                                                |
|                         Details of selected card shown above                   |
|                                                                                |
|                                                                                |
|                                                                                |
|ENTER=Process F3=Exit F5=Save F12=Cancel                                        |
+--------------------------------------------------------------------------------+
```

### Interface Elements

| Element           | Type    | Description                                                            |
|-------------------|---------|------------------------------------------------------------------------|
| Account Number    | Input (becomes Display) | 11-digit account number; editable during search, display-only after card is retrieved |
| Card Number       | Input (becomes Display) | 16-digit card number; editable during search, display-only after card is retrieved |
| Name on Card      | Input   | Up to 50 characters; editable when card details are displayed           |
| Card Active Y/N   | Input   | Single character (Y or N); editable when card details are displayed     |
| Expiry Month      | Input   | 2-digit month (01-12); right-justified; editable when card details are displayed |
| Expiry Year       | Input   | 4-digit year (1950-2099); right-justified; editable when card details are displayed |
| Expiry Day        | Display (hidden) | 2-digit day; hidden from user view (dark attribute), preserved from original |
| Info Message      | Display | 40-character informational message area showing current state/guidance   |
| Error Message     | Display | 80-character error message displayed in bright red at bottom of screen   |
| Function Keys     | Display | Shows available actions; "F5=Save F12=Cancel" portion is hidden until confirmation step |

### Available Actions

| Action          | How to Invoke | Description                                                             |
|-----------------|---------------|-------------------------------------------------------------------------|
| Process/Submit  | Press Enter   | Validates input and advances to next step (search, validate edits, etc.) |
| Exit            | Press F3      | Returns to the calling screen (Credit Card List or Main Menu)           |
| Save            | Press F5      | Commits validated changes to the database (only available at confirmation step) |
| Cancel          | Press F12     | Discards changes and reverts to the originally fetched card details (only available after card is displayed) |

---

## Section 6 — Alternative & Error Flows

### Alternative Flow 1: Card Not Found

| Step | Description                                                                           |
|------|---------------------------------------------------------------------------------------|
| 1    | User enters a valid account number and card number and presses Enter                  |
| 2    | System searches the card database using the card number                                |
| 3    | No matching record is found                                                           |
| 4    | System displays error: "Did not find cards for this search condition"                 |
| 5    | Account and card number fields are highlighted in red                                  |
| 6    | Cursor is positioned at the account number field for correction                       |
| 7    | User may re-enter different search criteria                                           |

### Alternative Flow 2: Invalid Search Input

| Step | Description                                                                           |
|------|---------------------------------------------------------------------------------------|
| 1    | User enters incomplete or invalid search criteria (e.g., non-numeric account number)  |
| 2    | System validates input fields in order: account first, then card number               |
| 3    | System displays the first validation error encountered (only one error shown at a time)|
| 4    | The field with the error is highlighted in red; cursor moves to that field             |
| 5    | If a required field is blank, an asterisk (*) is shown in that field in red           |
| 6    | User corrects the input and presses Enter to retry                                    |

### Alternative Flow 3: No Changes Detected

| Step | Description                                                                           |
|------|---------------------------------------------------------------------------------------|
| 1    | Card details are displayed in editable mode                                           |
| 2    | User presses Enter without modifying any values (or enters identical values)          |
| 3    | System performs case-insensitive comparison and detects no changes                     |
| 4    | System displays message: "No change detected with respect to values fetched."         |
| 5    | Fields remain editable; cursor moves to the Name field                                |
| 6    | User may make changes or press F3 to exit                                             |

### Alternative Flow 4: Validation Errors on Edited Fields

| Step | Description                                                                           |
|------|---------------------------------------------------------------------------------------|
| 1    | Card details are displayed and user modifies one or more fields                       |
| 2    | User presses Enter to submit changes                                                  |
| 3    | System validates all editable fields (name, status, month, year)                      |
| 4    | One or more validation errors are found                                               |
| 5    | First error message is displayed in red at the bottom of the screen                   |
| 6    | Fields with errors are highlighted in red; blank required fields show a red asterisk   |
| 7    | Cursor is positioned at the first field with an error                                 |
| 8    | Informational message shows: "Update card details presented above."                   |
| 9    | User corrects errors and presses Enter to re-validate                                 |

### Alternative Flow 5: User Cancels Before Saving (F12)

| Step | Description                                                                           |
|------|---------------------------------------------------------------------------------------|
| 1    | Card details are displayed; user has made modifications                               |
| 2    | User presses F12 to cancel                                                            |
| 3    | System discards all user changes                                                      |
| 4    | System re-reads the card record from the database                                     |
| 5    | Original (current database) values are displayed in editable mode                     |
| 6    | Message shows: "Details of selected card shown above"                                 |

### Alternative Flow 6: Concurrent Modification Detected

| Step | Description                                                                           |
|------|---------------------------------------------------------------------------------------|
| 1    | User has confirmed changes by pressing F5                                             |
| 2    | System locks the record and re-reads it from the database                             |
| 3    | System compares the current database values against the values that were originally displayed to the user |
| 4    | One or more fields differ — another user modified the record                          |
| 5    | System displays error: "Record changed by some one else. Please review"               |
| 6    | System refreshes the displayed values with the current database values                |
| 7    | Fields become editable again so the user can review the new values and retry           |

### Alternative Flow 7: Record Lock Failure

| Step | Description                                                                           |
|------|---------------------------------------------------------------------------------------|
| 1    | User has confirmed changes by pressing F5                                             |
| 2    | System attempts to acquire an exclusive lock on the card record                       |
| 3    | Lock fails (another user or process holds the record)                                 |
| 4    | System displays error: "Could not lock record for update"                             |
| 5    | Informational message shows: "Changes unsuccessful. Please try again"                 |
| 6    | If user arrived from Card List: pressing Enter returns to Card List                   |

### Alternative Flow 8: Database Write Failure

| Step | Description                                                                           |
|------|---------------------------------------------------------------------------------------|
| 1    | System has successfully locked the record and passed the concurrent-change check      |
| 2    | System attempts to write the updated record to the database                           |
| 3    | The write operation fails (I/O error)                                                 |
| 4    | System displays error: "Update of record failed"                                      |
| 5    | Informational message shows: "Changes unsuccessful. Please try again"                 |
| 6    | If user arrived from Card List: pressing Enter returns to Card List                   |

### Alternative Flow 9: System I/O Error During Read

| Step | Description                                                                           |
|------|---------------------------------------------------------------------------------------|
| 1    | User submits valid search criteria                                                    |
| 2    | System attempts to read the card record from the database                             |
| 3    | An unexpected I/O error occurs (not a "not found" condition)                          |
| 4    | System displays a technical file error message with diagnostic codes                  |
| 5    | Search screen is re-displayed for the user to retry                                   |

---

## Section 7 — Business Process Context

### Navigation Context

```
CardDemo Application
├── Sign-on (CC00)
│   └── Main Menu (CM00)
│       ├── [Option 1] Account View (CAVW)
│       │   └── [Navigate] Credit Card Update ──────────┐
│       ├── [Option 2] Account Update (CAUP)            │
│       │   └── [Navigate] Credit Card Update ──────────┤
│       ├── [Option 3] Credit Card List (CCLI)          │
│       │   ├── [Select Card] Credit Card View (CCDL)   │
│       │   └── [Select Card / F12] ───────────────────>├──┐
│       ├── [Option 4] Transaction List                  │  │
│       ├── *[Option 5] CREDIT CARD UPDATE (CCUP)* <────┘  │
│       ├── [Option 6] Transaction View                     │
│       ├── [Option 7] Transaction Add                      │
│       ├── [Option 8] Bill Payment                         │
│       └── [Option 9+] Reports                             │
│                                                           │
│   ┌───────────────────────────────────────────────────────┘
│   v
│   **CREDIT CARD UPDATE (CCUP)** ← THIS USE CASE
│       ├── [F3] Return to calling screen
│       └── [After success from Card List] Return to Card List
```

### Downstream Use Cases

| Destination          | Trigger                                           | What Happens Next                                     |
|----------------------|---------------------------------------------------|-------------------------------------------------------|
| Credit Card List     | F3 pressed (when invoked from Card List)          | Returns to the card list showing the user's last search results |
| Main Menu            | F3 pressed (when invoked directly from Main Menu) | Returns to the Main Menu for further navigation       |
| Credit Card List     | Update completes or fails (when invoked from Card List) | Auto-returns to Card List on next Enter press   |

### Upstream Use Cases

| Source               | Navigation Path                                                |
|----------------------|----------------------------------------------------------------|
| Main Menu            | User selects Option 5 ("Credit Card Update")                   |
| Credit Card List     | User selects a card from the list; system transfers to update screen with card pre-selected |
| Account View         | User navigates from Account View to update a specific card     |
| Account Update       | User navigates from Account Update to update a specific card   |

### End-to-End Journey Examples

**Journey 1: Deactivate a Compromised Card**

```
Sign-on --> Main Menu --> [Opt 3] Card List --> Select Card --> Card Update
    --> Change Active Status from Y to N --> Confirm (F5) --> Success
    --> [Auto-return] Card List --> F3 --> Main Menu
```
_Business scenario: A fraud analyst receives a report of suspicious activity and deactivates the compromised card._

**Journey 2: Update Cardholder Name After Legal Name Change**

```
Sign-on --> Main Menu --> [Opt 5] Card Update --> Enter Account# + Card#
    --> Modify "Name on Card" --> Confirm (F5) --> Success
    --> Enter (new search) --> Enter another Card# --> Modify name
    --> Confirm (F5) --> Success --> F3 --> Main Menu
```
_Business scenario: A customer legally changes their name; the operator updates all cards on the account._

**Journey 3: Extend Card Expiry During Renewal**

```
Sign-on --> Main Menu --> [Opt 3] Card List --> Select Card --> Card Update
    --> Modify Expiry Month and Year --> Confirm (F5) --> Success
    --> [Auto-return] Card List --> Select next card --> Card Update
    --> Modify Expiry --> Confirm (F5) --> F3 --> Main Menu
```
_Business scenario: During a bulk renewal cycle, an operator extends expiration dates for cards approaching their expiry._

**Journey 4: Review After Concurrent Modification**

```
Sign-on --> Main Menu --> [Opt 5] Card Update --> Enter Account# + Card#
    --> Modify fields --> Confirm (F5) --> "Record changed by someone else"
    --> Review new values --> Re-modify as needed --> Confirm (F5) --> Success
    --> F3 --> Main Menu
```
_Business scenario: Two operators work on the same card simultaneously; the system ensures no changes are lost._

---

## Section 8 — Acceptance Criteria

### Core Functionality

| #     | Given                                                | When                                                    | Then                                                                                        |
|-------|------------------------------------------------------|---------------------------------------------------------|---------------------------------------------------------------------------------------------|
| AC-01 | User is on the Credit Card Update search screen      | User enters a valid 11-digit account number and 16-digit card number and presses Enter | System retrieves and displays the card details with editable fields for name, status, expiry month, and expiry year |
| AC-02 | Card details are displayed in editable mode          | User modifies the Name on Card to "JANE B DOE" and presses Enter | System validates the name (alphabetic + spaces), passes validation, and displays confirmation prompt |
| AC-03 | Card details are displayed in editable mode          | User changes Card Active Status from "Y" to "N" and presses Enter | System validates the status value, passes validation, and displays confirmation prompt |
| AC-04 | Card details are displayed in editable mode          | User changes Expiry Month to "06" and Expiry Year to "2026" and presses Enter | System validates month (1-12) and year (1950-2099), passes validation, and displays confirmation prompt |
| AC-05 | Confirmation prompt is displayed with changes locked  | User presses F5                                         | System locks the record, verifies no concurrent changes, writes the update, and displays "Changes committed to database" |
| AC-06 | Update has been successfully committed               | User presses Enter                                      | System resets the screen for a new card search (if from Main Menu) or returns to Card List (if from Card List) |

### Input Validation

| #     | Given                                                | When                                                    | Then                                                                                        |
|-------|------------------------------------------------------|---------------------------------------------------------|---------------------------------------------------------------------------------------------|
| AC-07 | User is on the search screen                         | User presses Enter without entering any values          | System displays error "No input received"                                                   |
| AC-08 | User is on the search screen                         | User enters a non-numeric account number                | System displays error "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"               |
| AC-09 | User is on the search screen                         | User enters a non-numeric card number                   | System displays error "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"               |
| AC-10 | User is on the search screen                         | User leaves account number blank but enters a card number | System displays error "Account number not provided"                                        |
| AC-11 | Card details are displayed                           | User clears the Name on Card field and presses Enter    | System displays error "Card name not provided" and shows a red asterisk in the name field   |
| AC-12 | Card details are displayed                           | User enters "JOHN 123" in the Name on Card field        | System displays error "Card name can only contain alphabets and spaces"                     |
| AC-13 | Card details are displayed                           | User enters "X" in the Card Active Status field         | System displays error "Card Active Status must be Y or N"                                   |
| AC-14 | Card details are displayed                           | User enters "13" in the Expiry Month field              | System displays error "Card expiry month must be between 1 and 12"                         |
| AC-15 | Card details are displayed                           | User enters "1900" in the Expiry Year field             | System displays error "Invalid card expiry year"                                            |

### Navigation

| #     | Given                                                | When                                                    | Then                                                                                        |
|-------|------------------------------------------------------|---------------------------------------------------------|---------------------------------------------------------------------------------------------|
| AC-16 | User is on any step of the Credit Card Update screen | User presses F3                                         | System returns to the calling screen (Card List or Main Menu) without committing any changes |
| AC-17 | Card details are displayed with user modifications   | User presses F12                                        | System discards modifications and re-displays the original card details from the database   |
| AC-18 | User arrived from Credit Card List                   | Card is displayed and user has not yet edited           | Account number and card number are pre-filled from the list selection; no search is needed   |
| AC-19 | User arrived from Credit Card List and update succeeds | User presses Enter after seeing success message        | System returns automatically to the Credit Card List                                        |
| AC-20 | Confirmation prompt is displayed                     | User presses Enter (without pressing F5)                | System continues to display the confirmation prompt without committing — F5 is required     |

### Data Integrity

| #     | Given                                                | When                                                    | Then                                                                                        |
|-------|------------------------------------------------------|---------------------------------------------------------|---------------------------------------------------------------------------------------------|
| AC-21 | User confirms changes with F5                        | Another user has modified the same card record since it was displayed | System displays "Record changed by some one else. Please review" and refreshes with current database values |
| AC-22 | User confirms changes with F5                        | The card record is locked by another process            | System displays "Could not lock record for update" and informs of failure                   |
| AC-23 | User confirms changes with F5                        | The database write operation fails                      | System displays "Update of record failed" and informs of failure                            |
| AC-24 | User modifies only the Name on Card                  | Update is committed successfully                        | The CVV code, account ID, card number, expiry day, and filler are preserved unchanged        |
| AC-25 | Card details are displayed                           | User enters changes that are identical (case-insensitive) to the original values | System displays "No change detected with respect to values fetched." and does not proceed to confirmation |
| AC-26 | Expiry Day is "15" in the original record            | User changes Expiry Month and Year and confirms         | The Expiry Day "15" is preserved in the updated record; the full date is stored as YYYY-MM-DD |

---

## Section 9 — Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                | Technical Reference                                                                |
|----------------------------------------------|------------------------------------------------------------------------------------|
| Navigate to Credit Card Update               | `EXEC CICS XCTL` from COMEN01C or COCRDLIC to COCRDUPC                            |
| Display empty search screen                  | Paragraph `3000-SEND-MAP` → `3100-SCREEN-INIT`, `3200-SETUP-SCREEN-VARS`, `3400-SEND-SCREEN` with `CCUP-DETAILS-NOT-FETCHED` state |
| Receive user input from screen               | `1100-RECEIVE-MAP`: `EXEC CICS RECEIVE MAP('CCRDUPA') MAPSET('COCRDUP') INTO(CCRDUPAI)` |
| Validate account number (V1, V2)             | Paragraph `1210-EDIT-ACCOUNT` — checks for LOW-VALUES/SPACES/ZEROS, then `IS NOT NUMERIC` |
| Validate card number (V3, V4)                | Paragraph `1220-EDIT-CARD` — checks for LOW-VALUES/SPACES/ZEROS, then `IS NOT NUMERIC` |
| Retrieve card from database                  | Paragraph `9100-GETCARD-BYACCTCARD`: `EXEC CICS READ FILE('CARDDAT') RIDFLD(WS-CARD-RID-CARDNUM) INTO(CARD-RECORD)` |
| Detect case-insensitive no-change (BR3)      | `1200-EDIT-MAP-INPUTS` line 680: `FUNCTION UPPER-CASE(CCUP-NEW-CARDDATA) EQUAL FUNCTION UPPER-CASE(CCUP-OLD-CARDDATA)` |
| Validate name (V5, V6)                       | Paragraph `1230-EDIT-NAME` — blank check, then `INSPECT CONVERTING` alphabetics to spaces and checks `TRIM` length = 0 |
| Validate card status (V7)                    | Paragraph `1240-EDIT-CARDSTATUS` — checks 88-level `FLG-YES-NO-VALID` (VALUES 'Y', 'N') |
| Validate expiry month (V8)                   | Paragraph `1250-EDIT-EXPIRY-MON` — checks 88-level `VALID-MONTH` (VALUES 1 THRU 12) |
| Validate expiry year (V9)                    | Paragraph `1260-EDIT-EXPIRY-YEAR` — checks 88-level `VALID-YEAR` (VALUES 1950 THRU 2099) |
| Set confirmation state (BR4)                 | `2000-DECIDE-ACTION`: `SET CCUP-CHANGES-OK-NOT-CONFIRMED TO TRUE` |
| Acquire exclusive record lock (BR6)          | `9200-WRITE-PROCESSING`: `EXEC CICS READ FILE('CARDDAT') UPDATE RIDFLD(WS-CARD-RID-CARDNUM) INTO(CARD-RECORD)` |
| Optimistic concurrency check (BR5)           | Paragraph `9300-CHECK-CHANGE-IN-REC` — field-by-field comparison of CARD-RECORD against CCUP-OLD-* values |
| Prepare update record                        | `9200-WRITE-PROCESSING` lines 1461-1475: `STRING` expiry as YYYY-MM-DD, move new values to `CARD-UPDATE-RECORD` |
| Write update to database                     | `9200-WRITE-PROCESSING`: `EXEC CICS REWRITE FILE('CARDDAT') FROM(CARD-UPDATE-RECORD)` |
| Display success message                      | `3250-SETUP-INFOMSG`: `SET CONFIRM-UPDATE-SUCCESS TO TRUE` → "Changes committed to database" |
| Exit to calling program                      | `0000-MAIN` PF3 path: `EXEC CICS SYNCPOINT` then `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA)` |
| Cancel edits (F12)                           | `2000-DECIDE-ACTION` WHEN `CCARD-AID-PFK12`: `PERFORM 9000-READ-DATA` to re-fetch, `SET CCUP-SHOW-DETAILS TO TRUE` |
| Normalize name to uppercase on read (BR8)    | `9000-READ-DATA` line 1356: `INSPECT CARD-EMBOSSED-NAME CONVERTING LIT-LOWER TO LIT-UPPER` |
| Preserve expiry day (BR7)                    | `3200-SETUP-SCREEN-VARS` line 1123: `MOVE CCUP-OLD-EXPDAY TO EXPDAYO`; `9200-WRITE-PROCESSING` line 1471: `CCUP-NEW-EXPDAY` in STRING (preserved from CCUP-OLD via `1100-RECEIVE-MAP` line 621) |
| Highlight error fields in red (BR11)         | `3300-SETUP-SCREEN-ATTRS`: conditional `MOVE DFHRED TO *field*C OF CCRDUPAO` and `MOVE '*' TO *field*O` |
| Reveal F5 key text at confirmation           | `3300-SETUP-SCREEN-ATTRS` line 1316: `MOVE DFHBMBRY TO FKEYSCA OF CCRDUPAI` when `PROMPT-FOR-CONFIRMATION` |
| Pseudo-conversational return                 | `COMMON-RETURN`: `EXEC CICS RETURN TRANSID('CCUP') COMMAREA(WS-COMMAREA) LENGTH(LENGTH OF WS-COMMAREA)` |
| State machine persistence                    | `WS-THIS-PROGCOMMAREA.CCUP-CHANGE-ACTION` packed into WS-COMMAREA at offset after CARDDEMO-COMMAREA |
| Abnormal termination handling                | `ABEND-ROUTINE`: `EXEC CICS SEND FROM(ABEND-DATA)`, `EXEC CICS HANDLE ABEND CANCEL`, `EXEC CICS ABEND ABCODE('9999')` |

### Source File Mapping

| Business Concept                     | Source File(s)                                         |
|--------------------------------------|--------------------------------------------------------|
| Credit Card Update program logic     | `app/cbl/COCRDUPC.cbl`                                |
| Screen layout and field definitions  | `app/bms/COCRDUP.bms`                                 |
| Card record structure                | `app/cpy/CVACT02Y.cpy`                                |
| Application communication area       | `app/cpy/COCOM01Y.cpy`                                |
| Work area (AID keys, navigation)     | `app/cpy/CVCRD01Y.cpy`                                |
| Screen titles                        | `app/cpy/COTTL01Y.cpy`                                |
| Date/time formatting                 | `app/cpy/CSDAT01Y.cpy`                                |
| Common messages                      | `app/cpy/CSMSG01Y.cpy`                                |
| Abend handling variables             | `app/cpy/CSMSG02Y.cpy`                                |
| PF key mapping procedure             | `app/cpy/CSSTRPFY.cpy`                                |
| User security record layout          | `app/cpy/CSUSR01Y.cpy`                                |
| Customer record layout (included)    | `app/cpy/CVCUS01Y.cpy`                                |

### Migration Considerations

| #  | Consideration                                                                                                   |
|----|------------------------------------------------------------------------------------------------------------------|
| M1 | **Pseudo-conversational state → session/token state**: The COMMAREA-based state machine (CCUP-CHANGE-ACTION with 7 states) must be replaced with server-side session state or a stateless token (e.g., JWT with embedded state, or database-backed session). |
| M2 | **BMS screen → Web form**: The 3270 terminal screen (24x80, field attributes, cursor positioning) maps to an HTML form with CSS styling. Dynamic field protection translates to enabling/disabling form inputs via JavaScript. |
| M3 | **VSAM keyed access → SQL queries**: `READ FILE('CARDDAT') RIDFLD(card-number)` becomes a `SELECT ... WHERE card_number = ?` query. `READ UPDATE` + `REWRITE` becomes an `UPDATE ... WHERE card_number = ? AND version = ?` (optimistic locking via version column). |
| M4 | **Optimistic concurrency → database version column**: The field-by-field comparison in `9300-CHECK-CHANGE-IN-REC` should be replaced with a single version/timestamp column check, which is simpler and more scalable. |
| M5 | **No audit trail**: The current implementation has no logging of who changed what and when. The modern equivalent must add audit logging (e.g., JPA @Audited, event store, or change-data-capture). |
| M6 | **Case normalization inconsistency**: On read, the name is uppercased for display; on write, it is stored as-entered. The modern system should define a consistent policy (store as-entered and compare case-insensitively, or always normalize). |
| M7 | **XCTL navigation → URL routing**: `EXEC CICS XCTL` to other programs becomes URL navigation (e.g., `/cards/{cardNumber}/edit`) or SPA route changes. The COMMAREA-based caller-tracking becomes browser history or referrer state. |
| M8 | **Hidden expiry day**: The day portion is preserved but hidden. Modern APIs typically handle expiry as month/year only. If the day field needs to be preserved for backward compatibility, store it as metadata or default to a fixed value (e.g., last day of month). |
| M9 | **Error message pattern → validation framework**: Individual COBOL 88-level error messages should be replaced with a validation framework (e.g., Bean Validation annotations @NotBlank, @Pattern, @Min, @Max) that generates consistent error responses. |
| M10 | **SYNCPOINT on exit → transaction boundaries**: The `EXEC CICS SYNCPOINT` issued only on PF3/exit should be replaced with proper transaction demarcation (e.g., @Transactional) around the write operation itself. |

---

## Section 10 — Related Use Cases / End-to-End Composition

| Journey Name                          | Use Cases Involved                                                     | Business Scenario                                                   |
|---------------------------------------|------------------------------------------------------------------------|---------------------------------------------------------------------|
| Card Deactivation Workflow            | Sign-on → Main Menu → Card List → **Card Update** → Card List         | Fraud team deactivates a compromised card after reviewing the card list |
| Bulk Name Change                      | Sign-on → Main Menu → **Card Update** (repeated)                      | Customer name change requires updating multiple cards on the same account |
| Card Renewal with Review              | Sign-on → Main Menu → Card List → Card View → Card List → **Card Update** | Operator reviews card details first, then navigates to update to extend expiry |
| Account Maintenance with Card Update  | Sign-on → Main Menu → Account View → **Card Update**                  | During account review, operator navigates to update an associated card's details |
| Concurrent Update Resolution          | Sign-on → Main Menu → **Card Update** → (retry after conflict)        | Two operators attempt simultaneous changes; system detects conflict and requires review |

> **Note:** Individual use case flows must be composed into journey documents for complete end-to-end validation. The Card List (UC-CCLI-001), Card View (UC-CCDL-001), Account View (UC-CAVW-001), and Account Update (UC-CAUP-001) use cases should each have their own Business Use Case Flow documents to enable full journey composition.

---

## Document Footer

| Attribute                  | Value                                         |
|----------------------------|-----------------------------------------------|
| **Source Program**         | COCRDUPC.cbl (1,561 lines)                    |
| **Transaction ID**         | CCUP                                          |
| **Data Source**            | Technical Flow Analysis + COBOL source verification |
| **Acceptance Criteria**    | 26                                            |
| **Business Rules**         | 12                                            |
| **Validation Rules**       | 9                                             |
| **Alternative/Error Flows**| 9                                             |

---

*Generated by CICS Business Use Case Flow Analysis*
*Repository: choikh0423/aws-mainframe-modernization-carddemo*
*Branch: demos/cobol-full-docs*

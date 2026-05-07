# CCDL Transaction — Business Use Case Flow Analysis
# COCRDSLC: Credit Card Detail / Search

---

## Section 1: Use Case Overview

| Attribute           | Value                                                                 |
|---------------------|-----------------------------------------------------------------------|
| **Use Case Name**   | View Credit Card Detail                                               |
| **Use Case ID**     | UC-CCDL-001                                                          |
| **Actor(s)**        | Authenticated regular user (non-admin)                                |
| **Business Goal**   | Look up and display the details of a specific credit card by providing an account number and card number |
| **Preconditions**   | 1. User is signed in to the CardDemo application. 2. User has navigated to this screen from the Main Menu or the Credit Card List screen. 3. The card data store is available and accessible. |
| **Postconditions**  | Card details (name on card, active status, expiry date) are displayed on screen, or an appropriate message is shown if no matching card is found. No data is modified. |
| **Trigger**         | User selects "Credit Card View" (option 4) from the Main Menu, or selects a specific card row from the Credit Card List screen |
| **Data Access**     | Read-only                                                             |
| **Frequency**       | On-demand; triggered each time a user needs to view a specific card's details |

### Business Context

The Credit Card Detail screen is the primary point-of-inquiry for individual credit card information within the CardDemo application. It serves two distinct business scenarios:

1. **Direct Search** — A user navigates from the Main Menu and manually enters an account number and card number to look up a specific card.
2. **Drill-Down from List** — A user browses the Credit Card List, selects a card row, and is taken directly to this screen with the card details pre-populated. In this mode, the search fields are locked and display-only.

In both cases the screen displays the cardholder's embossed name, the card's active/inactive status, and its expiration date. This is a read-only inquiry; no changes can be made on this screen.

### Functional Domain Diagram

```
+-----------------------------------------------------------------------+
|                      CardDemo Application                             |
+-----------------------------------------------------------------------+
|                                                                       |
|  +-----------------+     +-----------------+     +-----------------+  |
|  |  User Security  |     | Account Mgmt    |     | Card Management |  |
|  |  (Sign-on,      |     | (View, Update)  |     | (List, Detail,  |  |
|  |   User Admin)   |     |                 |     |  Update)        |  |
|  +-----------------+     +-----------------+     +---------+-------+  |
|                                                            |          |
|                                              +-------------+-------+  |
|                                              |                     |  |
|                                         +----v----+   +------------+  |
|                                         | Card    |   | Card       |  |
|                                         | List    |   | Update     |  |
|                                         +----+----+   +------------+  |
|                                              |                        |
|                                         +----v---------+             |
|                                         | >> CARD      |             |
|                                         | >> DETAIL << |             |
|                                         | (THIS USE    |             |
|                                         |  CASE)       |             |
|                                         +--------------+             |
|                                                                       |
|  +-----------------+     +-----------------+     +-----------------+  |
|  | Transaction     |     | Bill Payment    |     | Reporting       |  |
|  | Mgmt (List,     |     |                 |     |                 |  |
|  |  View, Add)     |     |                 |     |                 |  |
|  +-----------------+     +-----------------+     +-----------------+  |
|                                                                       |
+-----------------------------------------------------------------------+
```

---

## Section 2: User Journey (Happy Path)

### Sequence Diagram — Direct Search

```
  User                                          System
   |                                               |
   |  Navigate to "Credit Card View" from menu     |
   |---------------------------------------------->|
   |                                               |
   |          Display blank search form            |
   |<----------------------------------------------|
   |  with prompt "Please enter Account            |
   |  and Card Number"                             |
   |                                               |
   |  Enter account number and card number,        |
   |  press Enter                                  |
   |---------------------------------------------->|
   |                                               |
   |          Validate account number              |
   |          Validate card number                 |
   |          Look up card in data store           |
   |                                               |
   |          Display card details:                |
   |           - Name on card                      |
   |           - Active status (Y/N)               |
   |           - Expiry month / year               |
   |          with message "Displaying             |
   |          requested details"                   |
   |<----------------------------------------------|
   |                                               |
   |  Press F3 to exit                             |
   |---------------------------------------------->|
   |                                               |
   |          Return to previous screen            |
   |<----------------------------------------------|
   |                                               |
```

### Sequence Diagram — Drill-Down from Card List

```
  User                                          System
   |                                               |
   |  Select a card row ('S') on Card List screen  |
   |---------------------------------------------->|
   |                                               |
   |          Pre-populate account and card numbers |
   |          (fields are locked / read-only)       |
   |          Look up card in data store            |
   |          Display card details:                 |
   |           - Name on card                       |
   |           - Active status (Y/N)                |
   |           - Expiry month / year                |
   |          with message "Displaying              |
   |          requested details"                    |
   |<----------------------------------------------|
   |                                               |
   |  Press F3 to return to Card List              |
   |---------------------------------------------->|
   |                                               |
   |          Return to Credit Card List screen    |
   |<----------------------------------------------|
   |                                               |
```

### Step-by-Step Narrative (Direct Search)

| Step | Actor  | Action                                         | System Response                                                                                     |
|------|--------|------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| 1    | User   | Selects "Credit Card View" (option 4) from the Main Menu | System navigates to the Card Detail screen and displays a blank search form with the prompt "Please enter Account and Card Number" |
| 2    | User   | Enters an 11-digit account number into the Account Number field | Field accepts the input; cursor advances to the next field                                          |
| 3    | User   | Enters a 16-digit card number into the Card Number field | Field accepts the input                                                                             |
| 4    | User   | Presses Enter to submit the search              | System validates both fields, looks up the card in the data store by card number                    |
| 5    | System | —                                                | If card found: displays cardholder name, active status (Y or N), and expiry date (month/year). Info message reads "Displaying requested details" |
| 6    | User   | Reviews the displayed card details               | Screen remains displayed with card information                                                      |
| 7    | User   | Presses F3 to exit                               | System returns the user to the Main Menu (or to whichever screen originally navigated here)         |

### Step-by-Step Narrative (Drill-Down from Card List)

| Step | Actor  | Action                                         | System Response                                                                                     |
|------|--------|------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| 1    | User   | Selects a card row (types 'S') on the Credit Card List screen | System navigates to the Card Detail screen with account and card numbers pre-filled and locked       |
| 2    | System | —                                                | Looks up the card in the data store. Displays cardholder name, active status, and expiry date. Info message reads "Displaying requested details" |
| 3    | User   | Reviews the displayed card details               | Screen remains displayed with card information                                                      |
| 4    | User   | Presses F3 to return                             | System returns the user to the Credit Card List screen                                              |

---

## Section 3: Business Rules & Validations

### Input Validation Rules

| #  | Field          | Rule                                                                 | Error Message Shown to User                                          |
|----|----------------|----------------------------------------------------------------------|----------------------------------------------------------------------|
| V1 | Account Number | Must not be blank, spaces, or all zeros                              | `Account number not provided`                                        |
| V2 | Account Number | Must be numeric (11-digit number)                                    | `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`               |
| V3 | Card Number    | Must not be blank, spaces, or all zeros                              | `Card number not provided`                                           |
| V4 | Card Number    | Must be numeric (16-digit number)                                    | `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER`               |
| V5 | Both fields    | At least one field must contain a value; both blank is rejected      | `No input received`                                                  |

> **Note on V5:** Although V5 exists as a cross-field check, in practice V1 and V3 will each fire first if their respective fields are blank, so V5 is only reached if both fields pass through as blank simultaneously (e.g., due to low-value/null input edge cases).

### Business Rules

| #   | Rule                                                                                                      |
|-----|-----------------------------------------------------------------------------------------------------------|
| BR1 | **Read-Only Inquiry**: This screen is strictly read-only. No card data can be created, modified, or deleted from this screen. |
| BR2 | **Search by Card Number**: The system looks up the card record using the card number as the primary search key. The account number is collected for context but the lookup is performed solely on the card number. |
| BR3 | **Pre-Filled from List**: When the user arrives from the Credit Card List screen, the account number and card number are pre-populated from the selected row and the fields are locked (read-only). The user cannot change the search criteria in this mode. |
| BR4 | **Unlocked for Direct Search**: When the user arrives from the Main Menu or any screen other than the Credit Card List, the account and card number fields are editable and the user must manually enter both values. |
| BR5 | **Return Navigation**: Pressing F3 returns the user to the screen they came from. If no originating screen is recorded, the system defaults to the Main Menu. |
| BR6 | **Invalid Key Handling**: Any key other than Enter or F3 is treated as Enter (the search is submitted or the screen is refreshed). |
| BR7 | **Blank Field Indicator**: On re-submission, if a required field is left blank, the system displays a red asterisk (`*`) in that field to draw the user's attention. |
| BR8 | **Invalid Field Highlighting**: Fields that fail validation are highlighted in red to indicate an error. |
| BR9 | **First Error Wins**: When multiple validation errors exist, only the first error message encountered is displayed. Subsequent errors are still flagged visually (red highlighting, asterisks) but do not overwrite the first error message. |
| BR10 | **Card Not Found**: If the card number does not match any record in the data store, the message "Did not find cards for this search condition" is displayed and both input fields are highlighted in red. |

### Decision Table

```
+---------------------------+-------------------------------------------+
| User Action               | System Response                           |
+===========================+===========================================+
| Press Enter with valid    | Validate inputs, look up card, display    |
| account and card numbers  | card details with success message         |
+---------------------------+-------------------------------------------+
| Press Enter with blank    | Display error "Account number not         |
| account number            | provided"; red asterisk in account field   |
+---------------------------+-------------------------------------------+
| Press Enter with non-     | Display error "ACCOUNT FILTER,IF SUPPLIED |
| numeric account number    | MUST BE A 11 DIGIT NUMBER"; red highlight |
+---------------------------+-------------------------------------------+
| Press Enter with blank    | Display error "Card number not provided"; |
| card number               | red asterisk in card field                |
+---------------------------+-------------------------------------------+
| Press Enter with non-     | Display error "CARD ID FILTER,IF SUPPLIED |
| numeric card number       | MUST BE A 16 DIGIT NUMBER"; red highlight |
+---------------------------+-------------------------------------------+
| Press Enter with both     | Display error "No input received"         |
| fields blank              |                                           |
+---------------------------+-------------------------------------------+
| Press Enter with valid    | Display error "Did not find cards for     |
| inputs but no matching    | this search condition"; both fields red   |
| card                      |                                           |
+---------------------------+-------------------------------------------+
| Press F3                  | Return to the originating screen (Card    |
|                           | List, Main Menu, or other caller)         |
+---------------------------+-------------------------------------------+
| Press any other key       | Treated as Enter (search is submitted     |
| (PF1, PF2, PF4-PF12,     | or screen is refreshed)                   |
| Clear, PA1, PA2, etc.)    |                                           |
+---------------------------+-------------------------------------------+
| Arrive from Card List     | Account and card fields are locked with   |
| with pre-selected card    | pre-filled values; card details displayed |
+---------------------------+-------------------------------------------+
| Arrive from Main Menu     | Blank search form displayed; account and  |
| (no pre-selected card)    | card fields are editable                  |
+---------------------------+-------------------------------------------+
```

---

## Section 4: Data Entities Involved

### Entity Descriptions

| Entity                | Description                                                    | Role in This Use Case                                  |
|-----------------------|----------------------------------------------------------------|--------------------------------------------------------|
| Card                  | Represents a physical or virtual credit card issued to an account holder. Contains the card number, associated account, cardholder name, expiry date, CVV, and active status. | Primary entity. The system reads a single card record by card number and displays its details. |
| Card (Account Index)  | An alternate view of the Card entity, indexed by account number rather than card number. | Available but **not used** in the current implementation. Reserved for potential future account-based lookups. |
| Customer              | Represents an individual customer with personal information (name, address, SSN, credit score, etc.). | **Not used** in this use case. The record layout is included in the program but no customer data is read or displayed. |

### Entity Relationships

```
+-------------------+        1:N        +-------------------+
|     Account       |------------------>|       Card        |
| (Account Number)  |                   | (Card Number)     |
+-------------------+                   +-------------------+
                                               |
                                          Read by this
                                          use case
                                               |
                                               v
                                        +-------------+
                                        | Card Detail |
                                        | Screen      |
                                        +-------------+
```

> One account may have multiple cards. This use case retrieves and displays exactly one card at a time, identified by its card number.

### Data Fields Displayed

| #  | Field             | Format             | Source                          | Description                                      |
|----|-------------------|--------------------|---------------------------------|--------------------------------------------------|
| 1  | Account Number    | 11 numeric digits  | User input or pre-filled from Card List | The account associated with the card             |
| 2  | Card Number       | 16 numeric digits  | User input or pre-filled from Card List | The unique identifier of the credit card         |
| 3  | Name on Card      | Up to 50 characters| Card record — embossed name field | The name embossed on the physical card           |
| 4  | Card Active Y/N   | Single character (Y or N) | Card record — active status field | Whether the card is currently active              |
| 5  | Expiry Month      | 2 digits (MM)      | Card record — expiration date field (month portion) | The month the card expires                   |
| 6  | Expiry Year       | 4 digits (YYYY)    | Card record — expiration date field (year portion)  | The year the card expires                    |

**Fields present in the card record but NOT displayed on this screen:**

| Field            | Format          | Reason Not Displayed                                        |
|------------------|-----------------|-------------------------------------------------------------|
| CVV Code         | 3 numeric digits | Security-sensitive; not shown on the inquiry screen          |
| Expiry Day       | 2 digits (DD)   | The screen only shows month and year, not the full date      |
| Filler           | 59 bytes         | Reserved/unused space in the record                          |

---

## Section 5: Screen / Interface Description

### Screen Layout (24 x 80, 3270 Terminal)

```
+--------------------------------------------------------------------------------+
|Tran: CCDL      AWS Mainframe Modernization          Date: 05/07/26             |
|Prog: COCRDSLC              CardDemo                  Time: 21:45:30            |
|                                                                                |
|                              View Credit Card Detail                           |
|                                                                                |
|                                                                                |
|                       Account Number    : 00012345678                          |
|                       Card Number       : 4111111111111111                     |
|                                                                                |
|                                                                                |
|    Name on card      : John Q. Cardholder                                     |
|                                                                                |
|    Card Active Y/N   : Y                                                      |
|                                                                                |
|    Expiry Date       : 09/2026                                                |
|                                                                                |
|                                                                                |
|                                                                                |
|                                                                                |
|                         Displaying requested details                          |
|                                                                                |
|                                                                                |
|                                                                                |
|ENTER=Search Cards  F3=Exit                                                    |
+--------------------------------------------------------------------------------+
```

### Interface Elements

| Element          | Type    | Description                                                                 |
|------------------|---------|-----------------------------------------------------------------------------|
| Transaction ID   | Display | Shows "CCDL" — identifies the current transaction                           |
| Program Name     | Display | Shows "COCRDSLC" — identifies the running program                           |
| Title Line 1     | Display | "AWS Mainframe Modernization" — application banner                          |
| Title Line 2     | Display | "CardDemo" — application name                                              |
| Current Date     | Display | Today's date in MM/DD/YY format                                            |
| Current Time     | Display | Current time in HH:MM:SS format                                            |
| Screen Title     | Display | "View Credit Card Detail" — describes the screen's purpose                  |
| Account Number   | Input   | 11-character field for the account number. Editable in direct search mode; locked when arriving from Card List |
| Card Number      | Input   | 16-character field for the card number. Editable in direct search mode; locked when arriving from Card List |
| Name on Card     | Display | Up to 50 characters showing the name embossed on the card                   |
| Card Active Y/N  | Display | Single character indicating active (Y) or inactive (N) status               |
| Expiry Month     | Display | 2-digit month (MM) from the card's expiration date                          |
| Expiry Year      | Display | 4-digit year (YYYY) from the card's expiration date                         |
| Info Message     | Display | 40-character area for informational messages (e.g., prompt, success)        |
| Error Message    | Display | 80-character area at the bottom for error messages, displayed in bright red  |
| Function Keys    | Display | "ENTER=Search Cards  F3=Exit" — legend of available actions                 |

### Available Actions

| Action           | How to Invoke | Description                                                              |
|------------------|---------------|--------------------------------------------------------------------------|
| Search Card      | Press Enter   | Submit the account and card numbers to look up a card's details          |
| Exit / Go Back   | Press F3      | Return to the previous screen (Card List, Main Menu, or other caller)    |

---

## Section 6: Alternative & Error Flows

### AF-1: No Matching Card Found

| Step | Description                                                                                     |
|------|-------------------------------------------------------------------------------------------------|
| 1    | User enters a valid 11-digit account number and a valid 16-digit card number.                   |
| 2    | User presses Enter.                                                                             |
| 3    | System validates both fields (pass).                                                            |
| 4    | System searches for the card in the data store by card number.                                  |
| 5    | No matching card record exists.                                                                 |
| 6    | System displays the error message: "Did not find cards for this search condition".              |
| 7    | Both the account number and card number fields are highlighted in red.                          |
| 8    | The user can correct the values and press Enter again, or press F3 to exit.                     |

### AF-2: Account Number Not Provided

| Step | Description                                                                                     |
|------|-------------------------------------------------------------------------------------------------|
| 1    | User leaves the Account Number field blank (or enters all zeros).                               |
| 2    | User presses Enter.                                                                             |
| 3    | System detects that the account number is missing.                                              |
| 4    | System displays the error message: "Account number not provided".                               |
| 5    | The account number field shows a red asterisk (`*`) and the cursor is positioned there.         |
| 6    | The user can enter a valid account number and press Enter again.                                |

### AF-3: Account Number Not Numeric

| Step | Description                                                                                     |
|------|-------------------------------------------------------------------------------------------------|
| 1    | User enters a non-numeric value in the Account Number field (e.g., letters or special characters). |
| 2    | User presses Enter.                                                                             |
| 3    | System detects that the account number is not numeric.                                          |
| 4    | System displays the error message: "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER".     |
| 5    | The account number field is highlighted in red and the cursor is positioned there.              |
| 6    | The user can correct the value and press Enter again.                                           |

### AF-4: Card Number Not Provided

| Step | Description                                                                                     |
|------|-------------------------------------------------------------------------------------------------|
| 1    | User leaves the Card Number field blank (or enters all zeros).                                  |
| 2    | User presses Enter.                                                                             |
| 3    | System detects that the card number is missing.                                                 |
| 4    | System displays the error message: "Card number not provided".                                  |
| 5    | The card number field shows a red asterisk (`*`) and the cursor is positioned there.            |
| 6    | The user can enter a valid card number and press Enter again.                                   |

### AF-5: Card Number Not Numeric

| Step | Description                                                                                     |
|------|-------------------------------------------------------------------------------------------------|
| 1    | User enters a non-numeric value in the Card Number field.                                       |
| 2    | User presses Enter.                                                                             |
| 3    | System detects that the card number is not numeric.                                             |
| 4    | System displays the error message: "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER".     |
| 5    | The card number field is highlighted in red and the cursor is positioned there.                 |
| 6    | The user can correct the value and press Enter again.                                           |

### AF-6: Both Fields Blank (No Search Criteria)

| Step | Description                                                                                     |
|------|-------------------------------------------------------------------------------------------------|
| 1    | User submits the form with both the Account Number and Card Number fields blank.                |
| 2    | System detects that no search criteria were provided.                                           |
| 3    | System displays the error message: "No input received".                                        |
| 4    | Both fields show red asterisks (`*`).                                                           |
| 5    | The cursor is positioned on the Account Number field.                                           |
| 6    | The user can enter values and press Enter again.                                                |

### AF-7: System Data Store Error

| Step | Description                                                                                     |
|------|-------------------------------------------------------------------------------------------------|
| 1    | User enters valid account and card numbers and presses Enter.                                   |
| 2    | System validates both fields (pass) and attempts to read the card data store.                   |
| 3    | The data store returns an unexpected error (e.g., file unavailable, I/O error).                 |
| 4    | System displays a technical error message: "File Error: READ on CARDDAT  returned RESP {code},RESP2 {code}". |
| 5    | The account number field is highlighted in red.                                                 |
| 6    | The user can retry or press F3 to exit.                                                         |

### AF-8: Invalid Function Key

| Step | Description                                                                                     |
|------|-------------------------------------------------------------------------------------------------|
| 1    | User presses any key other than Enter or F3 (e.g., F1, F2, F4 through F12, Clear, PA1, PA2).   |
| 2    | System treats the key press as if Enter was pressed.                                            |
| 3    | If the screen is in the initial blank state, the form is re-displayed (effectively a no-op refresh). If values are present, a search is submitted. |

---

## Section 7: Business Process Context

### Navigation Context

```
CardDemo Application
|
+-- CC00  Sign-on
|   |
|   +-- CM00  Main Menu (regular users)
|   |   |
|   |   +-- (1) Account View
|   |   |       |
|   |   |       +-- >> CCDL Card Detail << (drill-down from Account View)
|   |   |
|   |   +-- (2) Account Update
|   |   |       |
|   |   |       +-- >> CCDL Card Detail << (drill-down from Account Update)
|   |   |
|   |   +-- (3) Card List
|   |   |       |
|   |   |       +-- >> CCDL Card Detail << (select a card row)
|   |   |
|   |   +-- (4) >> CCDL Card Detail << (direct search)   <<<< THIS USE CASE
|   |   |
|   |   +-- (5) Card Update
|   |   +-- (6) Transaction List
|   |   +-- (7) Transaction View
|   |   +-- (8) Transaction Add
|   |   +-- (9) Bill Payment
|   |   +-- (10) Reports
|   |
|   +-- CA00  Admin Menu (admin users)
|       |
|       +-- User List / Add / Update / Delete
|       +-- Transaction Type Management
```

### Downstream Use Cases

| Destination            | Trigger        | What Happens Next                                                  |
|------------------------|----------------|--------------------------------------------------------------------|
| Main Menu              | User presses F3 (when arrived from Main Menu) | User returns to the Main Menu and can select another function      |
| Credit Card List       | User presses F3 (when arrived from Card List)  | User returns to the Card List screen to browse other cards         |
| Account View           | User presses F3 (when arrived from Account View) | User returns to the Account View screen                           |
| Account Update         | User presses F3 (when arrived from Account Update) | User returns to the Account Update screen                         |

### Upstream Use Cases

| Source                 | Navigation Path                                                    |
|------------------------|--------------------------------------------------------------------|
| Main Menu              | User selects option 4 ("Credit Card View") from the Main Menu      |
| Credit Card List       | User types 'S' next to a card row on the Card List screen          |
| Account View           | Navigates to Card Detail for a linked card (via internal transfer) |
| Account Update         | Navigates to Card Detail for a linked card (via internal transfer) |

### End-to-End Journey Examples

**Journey 1: Customer Service — Card Status Inquiry**

```
Sign-on --> Main Menu --> Card Detail (direct search) --> View details --> Exit to Main Menu
```
> A customer service representative signs in, navigates directly to Card Detail, enters the customer's account and card numbers, confirms the card is active, and returns to the menu.

**Journey 2: Card Portfolio Review**

```
Sign-on --> Main Menu --> Card List --> Select card --> Card Detail --> F3 back to Card List --> Select another card --> Card Detail --> F3 --> F3 to Main Menu
```
> A user browses the full card list, drills down into individual cards to review details, returns to the list to check another card, and eventually exits to the menu.

**Journey 3: Account-to-Card Investigation**

```
Sign-on --> Main Menu --> Account View --> Card Detail (via account link) --> F3 to Account View --> F3 to Main Menu
```
> A user looks up an account, drills into the linked card to verify its status and expiry date, then returns to the account view.

**Journey 4: Pre-Update Verification**

```
Sign-on --> Main Menu --> Card List --> Select card --> Card Detail --> F3 to Card List --> Card Update --> (modify card) --> F3 to Main Menu
```
> A user reviews a card's current details before navigating to the Card Update screen to make changes. The Card Detail screen serves as a read-only verification step.

---

## Section 8: Acceptance Criteria

### Core Functionality

| #     | Given                                               | When                                              | Then                                                                                    |
|-------|-----------------------------------------------------|---------------------------------------------------|-----------------------------------------------------------------------------------------|
| AC-01 | User is on the Card Detail screen in direct search mode | User enters a valid 11-digit account number and valid 16-digit card number and presses Enter | The system displays the card details: name on card, active status (Y/N), and expiry date (MM/YYYY). The info message reads "Displaying requested details". |
| AC-02 | User is on the Card Detail screen in direct search mode | User enters valid inputs but the card number does not exist in the data store | The system displays the error "Did not find cards for this search condition". Both input fields are highlighted in red. No card details are shown. |
| AC-03 | User arrives from the Credit Card List screen with a pre-selected card | Screen loads | The account number and card number fields are pre-populated with the selected values and locked (read-only). Card details are displayed immediately. |
| AC-04 | User arrives from the Main Menu (option 4) with no pre-selected card | Screen loads | A blank search form is displayed. Both input fields are editable. The info message reads "Please enter Account and Card Number". |

### Input Validation

| #     | Given                                               | When                                              | Then                                                                                    |
|-------|-----------------------------------------------------|---------------------------------------------------|-----------------------------------------------------------------------------------------|
| AC-05 | User is on the Card Detail screen in direct search mode | User leaves the Account Number field blank and presses Enter | Error message "Account number not provided" is displayed. The account field shows a red asterisk (`*`). Cursor is on the account field. |
| AC-06 | User is on the Card Detail screen in direct search mode | User enters a non-numeric account number and presses Enter | Error message "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER" is displayed. The account field is highlighted in red. |
| AC-07 | User is on the Card Detail screen in direct search mode | User leaves the Card Number field blank and presses Enter | Error message "Card number not provided" is displayed. The card field shows a red asterisk (`*`). Cursor is on the card field. |
| AC-08 | User is on the Card Detail screen in direct search mode | User enters a non-numeric card number and presses Enter | Error message "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" is displayed. The card field is highlighted in red. |
| AC-09 | User is on the Card Detail screen in direct search mode | User leaves both fields blank and presses Enter | Error message "No input received" is displayed. Both fields show red asterisks. Cursor is on the account field. |

### Navigation

| #     | Given                                               | When                                              | Then                                                                                    |
|-------|-----------------------------------------------------|---------------------------------------------------|-----------------------------------------------------------------------------------------|
| AC-10 | User arrived from the Main Menu | User presses F3 | System returns the user to the Main Menu. |
| AC-11 | User arrived from the Credit Card List screen | User presses F3 | System returns the user to the Credit Card List screen. |
| AC-12 | User arrived from Account View | User presses F3 | System returns the user to the Account View screen. |
| AC-13 | User is on the Card Detail screen | User presses any function key other than Enter or F3 | The key is treated as Enter — the search is submitted or the screen is refreshed. |

### Data Integrity

| #     | Given                                               | When                                              | Then                                                                                    |
|-------|-----------------------------------------------------|---------------------------------------------------|-----------------------------------------------------------------------------------------|
| AC-14 | A card record exists with specific name, status, and expiry values | User searches for that card | The displayed name on card, active status, and expiry month/year exactly match the values stored in the card data store. |
| AC-15 | User performs any number of searches on this screen | Any sequence of searches | No data in the card data store is modified. This screen is strictly read-only. |
| AC-16 | The card data store experiences an unexpected error | User presses Enter with valid inputs | A system error message is displayed describing the error. The screen does not crash or become unresponsive. |

---

## Section 9: Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                  | Technical Reference                                                                                      |
|------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| Program initialization                         | Paragraph `0000-MAIN`: `EXEC CICS HANDLE ABEND LABEL(ABEND-ROUTINE)`, `INITIALIZE CC-WORK-AREA`, `INITIALIZE WS-MISC-STORAGE` |
| Restore session state from prior interaction   | `0000-MAIN` lines 268-279: `MOVE DFHCOMMAREA TO CARDDEMO-COMMAREA` and `WS-THIS-PROGCOMMAREA` |
| Map function key press to action               | `YYYY-STORE-PFKEY` paragraph (COPY CSSTRPFY): EVALUATE maps EIBAID to CCARD-AID flags |
| Validate that pressed key is Enter or F3       | `0000-MAIN` lines 291-299: `SET PFK-INVALID TO TRUE`, check `CCARD-AID-ENTER OR CCARD-AID-PFK03` |
| F3 — return to calling screen                  | `0000-MAIN` lines 304-334: EVALUATE WHEN `CCARD-AID-PFK03`, determine target via `CDEMO-FROM-PROGRAM`/`CDEMO-FROM-TRANID`, `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA)` |
| First entry from Card List — auto-display      | `0000-MAIN` lines 339-348: WHEN `CDEMO-PGM-ENTER AND CDEMO-FROM-PROGRAM = LIT-CCLISTPGM`, move COMMAREA account/card to work area, PERFORM `9000-READ-DATA`, PERFORM `1000-SEND-MAP` |
| First entry from Menu — show blank form        | `0000-MAIN` lines 349-356: WHEN `CDEMO-PGM-ENTER` (other context), PERFORM `1000-SEND-MAP` |
| Re-entry — process user search                 | `0000-MAIN` lines 357-371: WHEN `CDEMO-PGM-REENTER`, PERFORM `2000-PROCESS-INPUTS`, if valid PERFORM `9000-READ-DATA` + `1000-SEND-MAP` |
| Receive user input from screen                 | Paragraph `2100-RECEIVE-MAP`: `EXEC CICS RECEIVE MAP(LIT-THISMAP) MAPSET(LIT-THISMAPSET) INTO(CCRDSLAI)` |
| Replace asterisk/space with null in fields     | Paragraph `2200-EDIT-MAP-INPUTS` lines 615-627: IF ACCTSIDI = `'*'` OR SPACES, MOVE LOW-VALUES; same for CARDSIDI |
| Validate account number — blank/zero check     | Paragraph `2210-EDIT-ACCOUNT` lines 651-661: IF `CC-ACCT-ID EQUAL LOW-VALUES OR SPACES OR ZEROS`, SET `INPUT-ERROR`, SET `WS-PROMPT-FOR-ACCT` |
| Validate account number — numeric check        | Paragraph `2210-EDIT-ACCOUNT` lines 665-674: IF `CC-ACCT-ID IS NOT NUMERIC`, MOVE error message to `WS-RETURN-MSG` |
| Validate card number — blank/zero check        | Paragraph `2220-EDIT-CARD` lines 691-702: IF `CC-CARD-NUM EQUAL LOW-VALUES OR SPACES OR ZEROS`, SET `INPUT-ERROR`, SET `WS-PROMPT-FOR-CARD` |
| Validate card number — numeric check           | Paragraph `2220-EDIT-CARD` lines 706-715: IF `CC-CARD-NUM IS NOT NUMERIC`, MOVE error message to `WS-RETURN-MSG` |
| Cross-field check — both blank                 | Paragraph `2200-EDIT-MAP-INPUTS` lines 637-640: IF `FLG-ACCTFILTER-BLANK AND FLG-CARDFILTER-BLANK`, SET `NO-SEARCH-CRITERIA-RECEIVED` |
| Read card record by card number                | Paragraph `9100-GETCARD-BYACCTCARD`: `EXEC CICS READ FILE(LIT-CARDFILENAME) RIDFLD(WS-CARD-RID-CARDNUM) INTO(CARD-RECORD)` |
| Card found — set success flag                  | `9100-GETCARD-BYACCTCARD` line 754: WHEN `DFHRESP(NORMAL)`, SET `FOUND-CARDS-FOR-ACCOUNT` |
| Card not found — set error                     | `9100-GETCARD-BYACCTCARD` lines 755-761: WHEN `DFHRESP(NOTFND)`, SET `DID-NOT-FIND-ACCTCARD-COMBO` |
| File error — build error message               | `9100-GETCARD-BYACCTCARD` lines 762-772: WHEN OTHER, MOVE operation/file/RESP/RESP2 to `WS-FILE-ERROR-MESSAGE` |
| Initialize screen output buffer                | Paragraph `1100-SCREEN-INIT`: MOVE LOW-VALUES TO CCRDSLAO, populate titles, transaction ID, program name, date, time |
| Populate card detail fields on screen          | Paragraph `1200-SETUP-SCREEN-VARS` lines 474-485: IF `FOUND-CARDS-FOR-ACCOUNT`, MOVE CARD-EMBOSSED-NAME, CARD-EXPIRY-MONTH, CARD-EXPIRY-YEAR, CARD-ACTIVE-STATUS to screen output fields |
| Lock/unlock input fields based on context      | Paragraph `1300-SETUP-SCREEN-ATTRS` lines 505-512: IF from Card List mapset, MOVE DFHBMPRF (protect); ELSE MOVE DFHBMFSE (unprotect) |
| Position cursor to first invalid field         | Paragraph `1300-SETUP-SCREEN-ATTRS` lines 515-524: EVALUATE sets ACCTSIDL or CARDSIDL to -1 based on validation flags |
| Highlight invalid fields in red                | Paragraph `1300-SETUP-SCREEN-ATTRS` lines 533-538: IF filter NOT-OK, MOVE DFHRED to field color |
| Show red asterisk for blank required fields    | Paragraph `1300-SETUP-SCREEN-ATTRS` lines 541-551: IF filter BLANK AND PGM-REENTER, MOVE `'*'` to field, MOVE DFHRED to color |
| Send screen to terminal                        | Paragraph `1400-SEND-SCREEN`: SET `CDEMO-PGM-REENTER`, `EXEC CICS SEND MAP(CCARD-NEXT-MAP) MAPSET(CCARD-NEXT-MAPSET) FROM(CCRDSLAO) CURSOR ERASE FREEKB` |
| Pseudo-conversational return                   | `COMMON-RETURN` lines 402-406: `EXEC CICS RETURN TRANSID(LIT-THISTRANID) COMMAREA(WS-COMMAREA)` |
| Abend recovery                                 | Paragraph `ABEND-ROUTINE` lines 857-877: default message, `EXEC CICS SEND FROM(ABEND-DATA)`, `EXEC CICS HANDLE ABEND CANCEL`, `EXEC CICS ABEND ABCODE('9999')` |

### Source File Mapping

| Business Concept                | Source File(s)                                                         |
|---------------------------------|------------------------------------------------------------------------|
| Card Detail / Search program    | `app/cbl/COCRDSLC.cbl` (888 lines)                                    |
| Screen layout definition        | `app/bms/COCRDSL.bms` (mapset COCRDSL, map CCRDSLA)                   |
| Screen symbolic map (generated) | COPY COCRDSL (auto-generated from BMS; referenced at line 215)         |
| Card record layout              | `app/cpy/CVACT02Y.cpy` (CARD-RECORD, 150 bytes)                       |
| Customer record layout          | `app/cpy/CVCUS01Y.cpy` (CUSTOMER-RECORD, 500 bytes — included but unused) |
| Shared communication area       | `app/cpy/COCOM01Y.cpy` (CARDDEMO-COMMAREA)                            |
| Work area and control fields    | `app/cpy/CVCRD01Y.cpy` (CC-WORK-AREAS)                                |
| Screen titles                   | `app/cpy/COTTL01Y.cpy` (CCDA-TITLE01, CCDA-TITLE02)                   |
| Date/time formatting            | `app/cpy/CSDAT01Y.cpy` (WS-DATE-TIME)                                 |
| Common messages                 | `app/cpy/CSMSG01Y.cpy` (CCDA-MSG-THANK-YOU, CCDA-MSG-INVALID-KEY)     |
| Abend work areas                | `app/cpy/CSMSG02Y.cpy` (ABEND-DATA)                                   |
| User security record layout     | `app/cpy/CSUSR01Y.cpy` (SEC-USER-DATA — included but unused)          |
| Function key mapping            | `app/cpy/CSSTRPFY.cpy` (YYYY-STORE-PFKEY paragraph)                   |

### Migration Considerations

| #  | Consideration                                                                                                  |
|----|----------------------------------------------------------------------------------------------------------------|
| M1 | **VSAM-to-Database**: The CARDDAT file (keyed by 16-byte card number) maps directly to a `cards` database table with a primary key on card number. A simple `SELECT` query replaces the CICS READ command. |
| M2 | **Alternate Index (CARDAIX)**: The CARDAIX alternate index (by account ID) is defined but unused in this program. Migration should evaluate whether an account-based lookup is needed; if so, add a secondary index or query path on the `account_id` column. |
| M3 | **COMMAREA-to-Session/State**: The COMMAREA (used to pass context between programs) maps to server-side session state or URL/query parameters in a web application. Key fields: originating screen, account ID, card number, program context (first entry vs. re-entry). |
| M4 | **BMS Screen-to-UI Component**: The 24x80 BMS map (COCRDSL/CCRDSLA) maps to a web form or API response. The screen has 2 input fields and 4 display fields. Dynamic field protection (locked when from Card List, editable otherwise) maps to conditional `readonly` attributes in HTML or form state in a SPA framework. |
| M5 | **Pseudo-Conversational Pattern**: The CICS pseudo-conversational cycle (RETURN TRANSID, re-entry with PGM-CONTEXT) maps to standard HTTP request/response or a stateful SPA. No special handling needed — the modern equivalent is simply a page load followed by form submission. |
| M6 | **XCTL Navigation**: The XCTL transfer (used for F3 exit and incoming navigation) maps to page routing or URL navigation in a web application. The dynamic return target (back to caller) maps to browser history or a "return URL" parameter. |
| M7 | **Error Message Preservation**: The exact error messages (e.g., "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER") are mainframe-era phrasing. Migration should preserve the business intent but consider rewording for modern user experience (e.g., "Account number must be an 11-digit number"). |
| M8 | **Field Validation Logic**: The input validation (blank check, numeric check, cross-field check) maps directly to standard form validation in any modern framework. The "first error wins" pattern (only one error message displayed at a time) may be replaced with simultaneous multi-field validation in a modern UI. |
| M9 | **Dead Code Removal**: Paragraph `9150-GETCARD-BYACCT` (alternate index read) and the CVCUS01Y (customer record) copybook are included but never invoked. These should be evaluated during migration — either implement the intended functionality or remove the dead code. |
| M10 | **CVV Security**: The card record contains a CVV code that is intentionally not displayed on screen. The modern equivalent should ensure CVV is never exposed in API responses for detail/search endpoints, following PCI-DSS compliance requirements. |
| M11 | **Read-Only Endpoint**: Since this use case is entirely read-only, it maps naturally to an HTTP GET endpoint or a GraphQL query. No write/update/delete operations are needed. |

---

## Section 10: Related Use Cases / End-to-End Composition

| Journey Name                      | Use Cases Involved                                                | Business Scenario                                                                |
|-----------------------------------|-------------------------------------------------------------------|----------------------------------------------------------------------------------|
| Card Status Inquiry               | Sign-on (CC00) > Main Menu (CM00) > Card Detail (CCDL)           | Customer service agent signs in and looks up a specific card's status and expiry by entering account and card numbers directly. |
| Card List Browse and Detail       | Sign-on (CC00) > Main Menu (CM00) > Card List (CCLI) > Card Detail (CCDL) | User browses the paginated card list, selects a card, views its full details, then returns to the list. Repeat for multiple cards. |
| Account-to-Card Drill-Down        | Sign-on (CC00) > Main Menu (CM00) > Account View (CAVW) > Card Detail (CCDL) | User views an account's summary, then drills into the linked card to verify status and expiry. Returns to Account View when done. |
| Pre-Update Verification           | Sign-on (CC00) > Main Menu (CM00) > Card List (CCLI) > Card Detail (CCDL) > Card List (CCLI) > Card Update (CCUP) | User verifies current card details (read-only) before navigating to the Card Update screen to make changes. The detail view acts as a confirmation step. |
| Full Card Lifecycle Review        | Sign-on (CC00) > Main Menu (CM00) > Card List (CCLI) > Card Detail (CCDL) > Card List (CCLI) > Transaction List (CT01) | User reviews a card's details, returns to the list, then navigates to the transaction list to see recent transactions for that card. Provides a complete picture of card activity. |

> **Note:** Individual use case flows must be composed into journey-level test documents for complete end-to-end validation. Some use cases referenced above (e.g., Card Update, Transaction List) may not yet have their own Business Use Case Flow document.

---

## Document Footer

| Attribute                  | Value                            |
|----------------------------|----------------------------------|
| **Source Program**         | COCRDSLC.cbl (888 lines)        |
| **Transaction ID**         | CCDL                             |
| **Data Source**             | Technical Flow Analysis (`docs/COCRDSLC_Flow_Analysis.md`) cross-checked against COBOL source |
| **Acceptance Criteria**    | 16                               |
| **Business Rules**         | 10                               |
| **Validation Rules**       | 5                                |
| **Alternative/Error Flows**| 8                                |

# CCLI — Credit Card List: Business Use Case Flow

## Section 1: Use Case Overview

| Attribute          | Value                                                                 |
|--------------------|-----------------------------------------------------------------------|
| **Use Case Name**  | Browse and Select Credit Cards                                        |
| **Use Case ID**    | UC-CCLI-001                                                           |
| **Actor(s)**       | Authenticated CardDemo User (regular user or administrator)           |
| **Business Goal**  | Allow the user to browse a paginated list of credit cards, optionally filter by account number or card number, and select a card to view its details or update it |
| **Preconditions**  | 1. User has successfully signed in to CardDemo<br>2. User has navigated to the Main Menu |
| **Postconditions** | 1. User has viewed the credit card list, OR<br>2. User has navigated to Card Detail View for a selected card, OR<br>3. User has navigated to Card Update for a selected card, OR<br>4. User has returned to the Main Menu |
| **Trigger**        | User selects option 3 ("Credit Card List") from the Main Menu         |
| **Data Access**    | Read-only (card data is browsed but never modified by this use case)   |
| **Frequency**      | High — primary entry point for all card-level inquiries and updates    |

### Business Context

The Credit Card List screen is the central hub for card-related operations in CardDemo. It serves as a browse-and-select interface where users can scroll through all credit cards in the system, narrow results using account or card number filters, and then drill into a specific card for detailed viewing or modification. This screen is the mandatory gateway to both the Card Detail View and Card Update functions — users cannot access those screens without first locating a card through this list.

The list displays seven cards per page with forward and backward pagination. Each row shows the account number, card number, and active status. Users interact by typing a single-character action code next to a card row to select it.

### Functional Domain Map

```
+-----------------------------------------------------------------------+
|                     CardDemo Application                              |
+-----------------------------------------------------------------------+
|                                                                       |
|  +------------------+    +------------------+    +------------------+ |
|  |   User Security  |    |  Account Mgmt    |    |  Card Management | |
|  |  (Sign-on, User  |    |  (Account View,  |    |  +-----------+  | |
|  |   Admin)         |    |   Account Update) |    |  | CARD LIST |  | |
|  +------------------+    +------------------+    |  | <<UC-CCLI>>|  | |
|                                                   |  +-----------+  | |
|  +------------------+    +------------------+    |  Card Detail,   | |
|  | Transaction Mgmt |    |  Reporting &     |    |  Card Update    | |
|  | (Tran List/View, |    |  Billing         |    +------------------+ |
|  |  Tran Add)       |    |  (Reports, Bill  |                        |
|  +------------------+    |   Payment)       |                        |
|                          +------------------+                        |
+-----------------------------------------------------------------------+
```

---

## Section 2: User Journey (Happy Path)

### Sequence Diagram

```
     User                                          System
      |                                               |
      |  Select "Credit Card List" from Main Menu     |
      |---------------------------------------------->|
      |                                               |  Read first page of cards
      |                                               |  from Card data store
      |              Display card list (page 1)       |
      |<----------------------------------------------|
      |                                               |
      |  (Optional) Enter account or card filter      |
      |  Press Enter to apply filter                  |
      |---------------------------------------------->|
      |                                               |  Re-read cards applying
      |                                               |  filter criteria
      |              Display filtered results         |
      |<----------------------------------------------|
      |                                               |
      |  (Optional) Press F8 to view next page        |
      |---------------------------------------------->|
      |                                               |  Read next page of cards
      |              Display next page                |
      |<----------------------------------------------|
      |                                               |
      |  Type 'S' next to a card row, press Enter     |
      |---------------------------------------------->|
      |                                               |  Transfer to Card Detail
      |              Card Detail View screen          |  View with selected card
      |<----------------------------------------------|
      |                                               |
```

### Step-by-Step Narrative

| Step | Actor  | Action                                          | System Response                                                                                         |
|------|--------|--------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| 1    | User   | Selects option 3 from the Main Menu              | System transfers control to the Credit Card List screen                                                 |
| 2    | System | —                                                | Reads the first page of cards (up to 7 records) from the card data store; displays the list with page number 1; shows the prompt "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD" |
| 3    | User   | Reviews the displayed card list                  | List shows Account Number, Card Number, and Active Status for each card                                  |
| 4    | User   | (Optional) Types an account number in the Account Number filter field | System accepts the 11-digit numeric filter value                                                        |
| 5    | User   | (Optional) Types a card number in the Card Number filter field | System accepts the 16-digit numeric filter value                                                        |
| 6    | User   | Presses Enter to apply filters                   | System re-reads the card data store, showing only cards matching the filter(s); updates the list display |
| 7    | User   | (Optional) Presses F8 to page forward            | System reads the next 7 matching cards and displays them; increments page number                         |
| 8    | User   | (Optional) Presses F7 to page backward           | System reads the previous 7 matching cards and displays them; decrements page number                     |
| 9    | User   | Types 'S' in the Select field next to a card row and presses Enter | System transfers to the Card Detail View screen, passing the selected card's account number and card number |
| 10   | User   | (Alternative) Types 'U' in the Select field next to a card row and presses Enter | System transfers to the Card Update screen, passing the selected card's account number and card number    |
| 11   | User   | (Alternative) Presses F3 to exit                 | System returns to the Main Menu                                                                         |

---

## Section 3: Business Rules & Validations

### Input Validation Rules

| #  | Field                 | Rule                                                                                  | Error Message Shown to User                                      |
|----|-----------------------|---------------------------------------------------------------------------------------|------------------------------------------------------------------|
| V1 | Account Number Filter | Must be blank or a valid 11-digit numeric value. Spaces, low-values, and all-zeros are treated as "not supplied." Non-numeric input is rejected. | `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`           |
| V2 | Card Number Filter    | Must be blank or a valid 16-digit numeric value. Spaces, low-values, and all-zeros are treated as "not supplied." Non-numeric input is rejected. | `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER`           |
| V3 | Row Selection Code    | Each row's selection field must contain 'S' (view detail), 'U' (update), or be blank. Any other character is invalid. | `INVALID ACTION CODE`                                            |
| V4 | Selection Count       | At most one row may be selected at a time. If more than one row contains 'S' or 'U', the action is rejected. | `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE`                |

### Business Rules

| #   | Rule                                                                                                                                      |
|-----|-------------------------------------------------------------------------------------------------------------------------------------------|
| BR1 | **Page Size**: The list displays a maximum of 7 card records per page. This is a fixed system limit.                                      |
| BR2 | **Sort Order**: Cards are displayed in ascending order by card number (the primary key of the card data store).                            |
| BR3 | **Filter Logic (AND)**: When both account number and card number filters are supplied, both conditions must match for a card to appear. Each filter is applied independently — account filter excludes cards with a non-matching account, then card filter excludes cards with a non-matching card number. |
| BR4 | **Filter Persistence**: Filter values entered by the user are preserved across page navigation (forward/backward). The filter is re-applied on every page read. |
| BR5 | **Pagination Forward**: When the user pages forward, the system starts reading from the last card on the current page and displays the next 7 matching records. The system peeks ahead one additional record to determine if another page exists. |
| BR6 | **Pagination Backward**: When the user pages backward, the system reads in reverse from the first card on the current page and fills the screen from position 7 down to position 1. |
| BR7 | **Next-Page Indicator**: After filling a page, the system reads one more record to check if additional data exists. If no more records exist, forward paging is disabled. |
| BR8 | **First-Page Guard**: If the user is already on the first page and presses the backward navigation key, the system re-displays the current page with the message "NO PREVIOUS PAGES TO DISPLAY." |
| BR9 | **Last-Page Guard**: If the user is on the last page and presses the forward navigation key, the system displays "NO MORE PAGES TO DISPLAY" on the second attempt, after first showing the last-page info message. |
| BR10 | **Selection Routing**: Entering 'S' next to a card routes to Card Detail View. Entering 'U' routes to Card Update. The selected card's account number and card number are passed to the destination screen. |
| BR11 | **Empty Result Handling**: If no records match the current filters and page position (first page, zero records), the system displays "NO RECORDS FOUND FOR THIS SEARCH CONDITION." |
| BR12 | **Invalid Key Handling**: Any function key other than Enter, F3, F7, or F8 is treated as Enter. The system does not display an "invalid key" error; it silently defaults to the Enter action. |
| BR13 | **Fresh Start on Menu Entry**: When the user arrives from the Main Menu (or any program other than the list itself), the system resets pagination state to page 1 and starts a fresh card browse. |
| BR14 | **Selection Fields Protected on Filter Error**: When an account or card filter validation fails, the row selection fields are protected (made read-only) so the user cannot select a card until the filter error is corrected. |
| BR15 | **Error Row Highlighting**: When a selection error occurs (invalid code or multiple selections), the affected row selection fields are highlighted in red to draw the user's attention. |

### Decision Table

```
+---------------------------+------------------------------------------------------+
| User Action               | System Response                                      |
+---------------------------+------------------------------------------------------+
| Enter (no selection,      | Re-read card list from current position,             |
|  no filter change)        | refresh display                                      |
+---------------------------+------------------------------------------------------+
| Enter + 'S' on one row   | Transfer to Card Detail View with selected            |
|                           | card's account and card number                        |
+---------------------------+------------------------------------------------------+
| Enter + 'U' on one row   | Transfer to Card Update with selected                 |
|                           | card's account and card number                        |
+---------------------------+------------------------------------------------------+
| Enter + multiple 'S'/'U' | Error: "PLEASE SELECT ONLY ONE RECORD TO              |
|                           | VIEW OR UPDATE"; highlight offending rows in red      |
+---------------------------+------------------------------------------------------+
| Enter + invalid code      | Error: "INVALID ACTION CODE"; highlight               |
| (not S/U/blank)          | offending row in red                                  |
+---------------------------+------------------------------------------------------+
| Enter + invalid account   | Error: "ACCOUNT FILTER,IF SUPPLIED MUST BE            |
| filter                    | A 11 DIGIT NUMBER"; highlight filter in red;          |
|                           | protect selection fields                              |
+---------------------------+------------------------------------------------------+
| Enter + invalid card      | Error: "CARD ID FILTER,IF SUPPLIED MUST BE            |
| filter                    | A 16 DIGIT NUMBER"; highlight filter in red;          |
|                           | protect selection fields                              |
+---------------------------+------------------------------------------------------+
| F3 (from list screen)     | Return to Main Menu                                   |
+---------------------------+------------------------------------------------------+
| F3 (returning from        | Reset state and re-read card list from the            |
| another program)          | beginning                                             |
+---------------------------+------------------------------------------------------+
| F7 (not on first page)    | Read previous page of cards, decrement page           |
|                           | number, display results                               |
+---------------------------+------------------------------------------------------+
| F7 (on first page)        | Re-display current page with error: "NO               |
|                           | PREVIOUS PAGES TO DISPLAY"                            |
+---------------------------+------------------------------------------------------+
| F8 (next page exists)     | Read next page of cards, increment page               |
|                           | number, display results                               |
+---------------------------+------------------------------------------------------+
| F8 (no next page,         | Display info message; mark page as last page          |
| first attempt)            |                                                      |
+---------------------------+------------------------------------------------------+
| F8 (no next page,         | Display error: "NO MORE PAGES TO DISPLAY"             |
| second attempt)           |                                                      |
+---------------------------+------------------------------------------------------+
| Any other key             | Treated as Enter — re-read and refresh list           |
+---------------------------+------------------------------------------------------+
```

---

## Section 4: Data Entities Involved

### Entity Descriptions

| Entity               | Description                                                              | Role in This Use Case                                                    |
|----------------------|--------------------------------------------------------------------------|--------------------------------------------------------------------------|
| Card                 | A credit card record containing the card number, associated account, cardholder name, expiration date, CVV, and active status | Primary data entity — browsed and displayed in the list                  |
| Account              | A credit card account identified by an 11-digit account number           | Referenced indirectly — the account number is displayed per card row and used as a filter criterion |
| Shared Navigation State | A communication area passed between screens containing user identity, source/destination program info, and card/account context | Used to maintain state across screen transitions; carries selected card info to downstream screens |

### Entity Relationships

```
+----------------+        +----------------+        +------------------+
|    Account     |  1..*  |     Card       |  1..1  |   Card Active    |
|  (ACCT-ID)     |------->|  (CARD-NUM)    |------->|   Status (Y/N)   |
|                |        |  ACCT-ID (FK)  |        |                  |
+----------------+        |  CVV           |        +------------------+
                          |  Embossed Name |
                          |  Expiration    |
                          +----------------+
```

One account can have multiple cards. Each card has exactly one active status flag.

### Data Fields Displayed

| #  | Field           | Format           | Source                   | Description                                       |
|----|-----------------|------------------|--------------------------|---------------------------------------------------|
| 1  | Account Number  | 11-digit numeric | Card record, Account ID  | The account associated with the card               |
| 2  | Card Number     | 16-character     | Card record, Card Number | The credit card number (primary identifier)        |
| 3  | Active Status   | 1 character       | Card record, Active Status | Whether the card is active ('Y') or inactive ('N') |

**Fields in the card record that are NOT displayed on this screen:**
- CVV Code (3-digit numeric)
- Embossed Name (50 characters — the name printed on the card)
- Expiration Date (10 characters, YYYY-MM-DD format)
- 59 bytes of reserved/filler space

---

## Section 5: Screen / Interface Description

### Screen Layout

```
+------------------------------------------------------------------------------+
| Tran: CCLI    AWS Mainframe Modernization          Date: 05/07/26            |
| Prog: COCRDLIC          CardDemo                   Time: 14:30:22            |
|                                                                              |
|                              List Credit Cards                    Page 1     |
|                                                                              |
|                     Account Number    : 00000012345                          |
|                     Credit Card Number: ________________                     |
|                                                                              |
|         Select     Account Number   Card Number       Active                 |
|         ------     ---------------  ---------------   --------               |
|           S        00000012345      4111111111111111      Y                  |
|           _        00000012345      4222222222222222      Y                  |
|           _        00000067890      4333333333333333      N                  |
|           _        00000067890      4444444444444444      Y                  |
|           _        00000099999      4555555555555555      Y                  |
|           _        00000099999      4666666666666666      Y                  |
|           _        00000099999      4777777777777777      N                  |
|                                                                              |
|                                                                              |
|                  TYPE S FOR DETAIL, U TO UPDATE ANY RECORD                   |
|                                                                              |
|                                                                              |
|                                                                              |
|  F3=Exit F7=Backward  F8=Forward                                            |
+------------------------------------------------------------------------------+
```

### Interface Elements

| Element              | Type    | Description                                                                       |
|----------------------|---------|-----------------------------------------------------------------------------------|
| Transaction ID       | Display | Shows "CCLI" — identifies the current transaction                                 |
| Program Name         | Display | Shows "COCRDLIC" — identifies the running program                                 |
| Title Line 1         | Display | Shows "AWS Mainframe Modernization"                                               |
| Title Line 2         | Display | Shows "CardDemo"                                                                  |
| Date                 | Display | Current date in MM/DD/YY format                                                   |
| Time                 | Display | Current time in HH:MM:SS format                                                   |
| Page Number          | Display | Current page number (increments/decrements with pagination)                       |
| Account Number Filter| Input   | 11-character field for filtering by account number; cursor starts here by default  |
| Card Number Filter   | Input   | 16-character field for filtering by card number                                    |
| Select (rows 1-7)    | Input   | 1-character field per row for entering 'S' (view) or 'U' (update); dynamically protected when row is empty or filter is invalid |
| Account Number (rows 1-7) | Display | 11-character account number for each card row                                |
| Card Number (rows 1-7)    | Display | 16-character card number for each card row                                   |
| Active Status (rows 1-7)  | Display | 1-character active status ('Y' or 'N') for each card row                     |
| Info Message         | Display | 45-character area for informational messages (e.g., selection instructions)        |
| Error Message        | Display | 78-character area for error messages, displayed in red                             |
| Function Key Legend   | Display | Shows available keys: "F3=Exit F7=Backward F8=Forward"                           |

### Available Actions

| Action            | How to Invoke                              | Description                                          |
|-------------------|--------------------------------------------|------------------------------------------------------|
| View Card Detail  | Type 'S' next to a card row, press Enter   | Opens the Card Detail View for the selected card      |
| Update Card       | Type 'U' next to a card row, press Enter   | Opens the Card Update screen for the selected card    |
| Filter by Account | Type an 11-digit number in Account Number field, press Enter | Narrows the list to cards matching that account |
| Filter by Card    | Type a 16-digit number in Card Number field, press Enter | Narrows the list to the matching card number    |
| Clear Filters     | Clear the filter fields, press Enter       | Removes filter and shows all cards                    |
| Page Forward      | Press F8                                   | Displays the next page of cards                       |
| Page Backward     | Press F7                                   | Displays the previous page of cards                   |
| Exit to Menu      | Press F3                                   | Returns to the Main Menu                              |

---

## Section 6: Alternative & Error Flows

### AF1: No Records Found

| Step | Description                                                                                   |
|------|-----------------------------------------------------------------------------------------------|
| 1    | User enters filter criteria (account number and/or card number) and presses Enter             |
| 2    | System searches the card data store but finds no records matching the filter on the first page |
| 3    | System displays an empty list with the message: "NO RECORDS FOUND FOR THIS SEARCH CONDITION." |
| 4    | User may change the filter values and press Enter to search again, or press F3 to exit        |

### AF2: Invalid Account Filter

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User types a non-numeric value (e.g., "ABC") in the Account Number filter field and presses Enter   |
| 2    | System validates the input and detects it is not a valid 11-digit number                            |
| 3    | System highlights the Account Number field in red and positions the cursor there                     |
| 4    | System protects all row selection fields (user cannot select a card until the filter is corrected)   |
| 5    | System displays error: "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"                       |
| 6    | User corrects the account number or clears it and presses Enter                                     |

### AF3: Invalid Card Number Filter

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User types a non-numeric value in the Card Number filter field and presses Enter                    |
| 2    | System validates the input and detects it is not a valid 16-digit number                            |
| 3    | System highlights the Card Number field in red and positions the cursor there                        |
| 4    | System protects all row selection fields                                                            |
| 5    | System displays error: "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"                       |
| 6    | User corrects the card number or clears it and presses Enter                                        |

### AF4: Invalid Selection Code

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User types a character other than 'S', 'U', or blank (e.g., 'X') in a row's Select field           |
| 2    | User presses Enter                                                                                  |
| 3    | System validates the selection and detects the invalid code                                          |
| 4    | System highlights the offending row's Select field in red with an asterisk ('*') if it was blank     |
| 5    | System displays error: "INVALID ACTION CODE"                                                         |
| 6    | User corrects the selection code to 'S', 'U', or blank and presses Enter                            |

### AF5: Multiple Selections

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User types 'S' or 'U' next to more than one card row                                               |
| 2    | User presses Enter                                                                                  |
| 3    | System counts the selections and detects more than one                                               |
| 4    | System highlights all rows containing selection codes in red                                         |
| 5    | System displays error: "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE"                             |
| 6    | User removes extra selections, leaving only one (or none), and presses Enter                         |

### AF6: Page Backward on First Page

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User is viewing page 1 of the card list                                                             |
| 2    | User presses F7 (backward)                                                                          |
| 3    | System detects the user is already on the first page                                                 |
| 4    | System re-reads and re-displays the same first page                                                  |
| 5    | System displays error: "NO PREVIOUS PAGES TO DISPLAY"                                                |

### AF7: Page Forward Past Last Page

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | User is viewing the last page of card data                                                          |
| 2    | User presses F8 (forward) — first attempt                                                           |
| 3    | System detects no more records exist beyond the current page                                         |
| 4    | System displays the info message and marks the current page as the last page                         |
| 5    | User presses F8 again — second attempt                                                               |
| 6    | System displays error: "NO MORE PAGES TO DISPLAY"                                                    |

### AF8: System Data Read Error

| Step | Description                                                                                         |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | System attempts to read the next card record during browsing                                         |
| 2    | An unexpected error occurs (neither a successful read nor an end-of-data condition)                  |
| 3    | System constructs a technical error message containing the operation name, file name, and error codes |
| 4    | System ends the browse session and displays the error message to the user                            |
| 5    | User may press F3 to exit or press Enter to retry                                                    |

---

## Section 7: Business Process Context

### Navigation Context

```
CardDemo Application
|
+-- Sign-on (CC00)
|   |
|   +-- Main Menu (CM00)
|   |   |
|   |   +-- Account View (CAVW)
|   |   +-- Account Update (CAUP)
|   |   |
|   |   +-- ** Credit Card List (CCLI) ** <--- YOU ARE HERE
|   |   |   |
|   |   |   +-- Card Detail View (CCDL)
|   |   |   |   |
|   |   |   |   +-- (F3) Return to Credit Card List
|   |   |   |
|   |   |   +-- Card Update (CCUP)
|   |   |       |
|   |   |       +-- (F3) Return to Credit Card List
|   |   |
|   |   +-- Transaction List (CT00)
|   |   +-- Transaction View (CT02)
|   |   +-- Transaction Add (CT01)
|   |   +-- Bill Payment (CB00)
|   |   +-- Reports (CR00)
|   |
|   +-- Admin Menu (CA00) [admin users only]
|       |
|       +-- User List / Add / Update / Delete
```

### Downstream Use Cases

| Destination        | Trigger                              | What Happens Next                                                    |
|--------------------|--------------------------------------|----------------------------------------------------------------------|
| Card Detail View (CCDL) | User types 'S' next to a row and presses Enter | System displays full card details (card number, account, CVV, embossed name, expiration, status) in a read-only view. User can press F3 to return to the Credit Card List. |
| Card Update (CCUP)      | User types 'U' next to a row and presses Enter | System displays card details in an editable form. User can modify fields and confirm the update. User can press F3 to return to the Credit Card List. |
| Main Menu (CM00)         | User presses F3                                 | System returns to the Main Menu where the user can select another function. |

### Upstream Use Cases

| Source                   | Navigation Path                                                          |
|--------------------------|--------------------------------------------------------------------------|
| Main Menu (CM00)         | User selects option 3 ("Credit Card List") from the menu                 |
| Card Detail View (CCDL)  | User presses F3 from the detail view, returning to the card list         |
| Card Update (CCUP)       | User presses F3 from the update screen, returning to the card list       |
| Account View (CAVW)      | Account View references the card list as a navigation target             |
| Account Update (CAUP)    | Account Update references the card list as a navigation target           |

### End-to-End Journey Examples

**Journey 1: Card Inquiry**
```
Sign-on --> Main Menu --> Credit Card List --> [browse/filter] --> Card Detail View --> Credit Card List --> Main Menu
```
_Business scenario: A customer service agent signs in, navigates to the card list, filters by account number to find a specific card, selects it to view its full details (including embossed name and expiration), then returns to the list._

**Journey 2: Card Status Update**
```
Sign-on --> Main Menu --> Credit Card List --> [filter by account] --> Card Update --> Credit Card List --> Main Menu
```
_Business scenario: A card operations specialist locates a card that needs to be deactivated, selects it for update, changes the active status, and returns to the list to verify the change._

**Journey 3: Account Card Review**
```
Sign-on --> Main Menu --> Credit Card List --> [filter by account] --> Card Detail View --> Credit Card List --> Card Detail View --> Credit Card List --> Main Menu
```
_Business scenario: An auditor reviews all cards associated with a specific account by filtering the list, then viewing each card's details one at a time to verify embossed names and expiration dates._

**Journey 4: Cross-Function Navigation**
```
Sign-on --> Main Menu --> Account View --> Main Menu --> Credit Card List --> Card Detail View --> Credit Card List --> Main Menu
```
_Business scenario: A user first checks account-level information, then navigates to the card list to inspect the cards associated with that account._

---

## Section 8: Acceptance Criteria

### Core Functionality

| #     | Criterion                                                                                                |
|-------|----------------------------------------------------------------------------------------------------------|
| AC-01 | **Given** the user is on the Main Menu, **When** the user selects option 3, **Then** the system displays the Credit Card List screen with the first page of cards (up to 7 records) in ascending card number order. _(BR1, BR2, BR13)_ |
| AC-02 | **Given** the card list is displayed, **When** the user types 'S' next to a card row and presses Enter, **Then** the system navigates to the Card Detail View screen with the selected card's account number and card number pre-loaded. _(BR10)_ |
| AC-03 | **Given** the card list is displayed, **When** the user types 'U' next to a card row and presses Enter, **Then** the system navigates to the Card Update screen with the selected card's account number and card number pre-loaded. _(BR10)_ |
| AC-04 | **Given** the card list is displayed, **When** the user presses F3, **Then** the system returns to the Main Menu. |
| AC-05 | **Given** the card list is displayed with data, **When** the system displays the list, **Then** each row shows the Account Number (11 digits), Card Number (16 characters), and Active Status (Y/N). |

### Filtering

| #     | Criterion                                                                                                |
|-------|----------------------------------------------------------------------------------------------------------|
| AC-06 | **Given** the card list is displayed, **When** the user enters a valid 11-digit account number in the Account Number filter and presses Enter, **Then** only cards matching that account number are displayed. _(BR3)_ |
| AC-07 | **Given** the card list is displayed, **When** the user enters a valid 16-digit card number in the Card Number filter and presses Enter, **Then** only the matching card is displayed. _(BR3)_ |
| AC-08 | **Given** the user has entered both an account number filter and a card number filter, **When** the user presses Enter, **Then** the system applies both filters using AND logic — only cards matching both criteria are displayed. _(BR3)_ |
| AC-09 | **Given** the user has entered filters, **When** the user pages forward or backward, **Then** the filters remain applied to the new page of results. _(BR4)_ |
| AC-10 | **Given** the user has entered filters that match no records, **When** the user presses Enter, **Then** the system displays an empty list with the message "NO RECORDS FOUND FOR THIS SEARCH CONDITION." _(BR11)_ |

### Input Validation

| #     | Criterion                                                                                                |
|-------|----------------------------------------------------------------------------------------------------------|
| AC-11 | **Given** the user types a non-numeric value in the Account Number filter, **When** the user presses Enter, **Then** the system displays "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER", highlights the field in red, and protects all row selection fields. _(V1, BR14)_ |
| AC-12 | **Given** the user types a non-numeric value in the Card Number filter, **When** the user presses Enter, **Then** the system displays "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER", highlights the field in red, and protects all row selection fields. _(V2, BR14)_ |
| AC-13 | **Given** the user types a character other than 'S', 'U', or blank in a row selection field, **When** the user presses Enter, **Then** the system displays "INVALID ACTION CODE" and highlights the offending row in red. _(V3, BR15)_ |
| AC-14 | **Given** the user types 'S' or 'U' in more than one row selection field, **When** the user presses Enter, **Then** the system displays "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE" and highlights all selected rows in red. _(V4, BR15)_ |

### Navigation & Pagination

| #     | Criterion                                                                                                |
|-------|----------------------------------------------------------------------------------------------------------|
| AC-15 | **Given** there are more than 7 matching cards, **When** the user presses F8 on a page that is not the last, **Then** the system displays the next 7 cards and increments the page number. _(BR5, BR7)_ |
| AC-16 | **Given** the user is on the last page, **When** the user presses F8 a second time, **Then** the system displays "NO MORE PAGES TO DISPLAY." _(BR9)_ |
| AC-17 | **Given** the user is on a page after the first, **When** the user presses F7, **Then** the system displays the previous 7 cards and decrements the page number. _(BR6)_ |
| AC-18 | **Given** the user is on page 1, **When** the user presses F7, **Then** the system re-displays the first page with the message "NO PREVIOUS PAGES TO DISPLAY." _(BR8)_ |
| AC-19 | **Given** the user navigates from the Card Detail View or Card Update back to the card list, **When** the list screen appears, **Then** the system re-reads from the beginning and resets the page number. _(BR13)_ |
| AC-20 | **Given** the user presses any function key other than Enter, F3, F7, or F8, **When** the system processes the input, **Then** the key is treated as Enter and the list is refreshed. _(BR12)_ |

### Data Integrity

| #     | Criterion                                                                                                |
|-------|----------------------------------------------------------------------------------------------------------|
| AC-21 | **Given** the card list is displayed, **When** the user browses through multiple pages, **Then** no card records are skipped or duplicated across pages. _(BR5, BR6)_ |
| AC-22 | **Given** a data read error occurs during browsing, **When** the system detects the error, **Then** the system displays a descriptive error message including the operation and data source name, and stops browsing. |

---

## Section 9: Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                              | Technical Reference                                                                                          |
|--------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| User arrives from Main Menu                | `0000-MAIN`: checks `EIBCALEN = 0` for first entry; checks `CDEMO-PGM-ENTER AND CDEMO-FROM-PROGRAM NOT EQUAL LIT-THISPGM` to detect menu entry; initializes `WS-THIS-PROGCOMMAREA` |
| Map function key pressed                   | `YYYY-STORE-PFKEY` (copybook `CSSTRPFY.cpy`): EVALUATE on `EIBAID` maps to `CCARD-AID-xxx` flags            |
| Validate valid keys (Enter, F3, F7, F8)    | `0000-MAIN` lines 370-380: checks `CCARD-AID-ENTER`, `CCARD-AID-PFK03`, `CCARD-AID-PFK07`, `CCARD-AID-PFK08`; sets `PFK-VALID` or defaults to Enter if `PFK-INVALID` |
| Receive screen input                       | `2100-RECEIVE-SCREEN`: `EXEC CICS RECEIVE MAP('CCRDLIA') MAPSET('COCRDLI') INTO(CCRDLIAI)`; extracts `ACCTSIDI`, `CARDSIDI`, `CRDSEL1I`-`CRDSEL7I` |
| Validate account filter (V1)               | `2210-EDIT-ACCOUNT`: checks `CC-ACCT-ID IS NOT NUMERIC`; sets `INPUT-ERROR`, `FLG-ACCTFILTER-NOT-OK`, `FLG-PROTECT-SELECT-ROWS-YES`; MOVE error message |
| Validate card filter (V2)                  | `2220-EDIT-CARD`: checks `CC-CARD-NUM IS NOT NUMERIC`; sets `INPUT-ERROR`, `FLG-CARDFILTER-NOT-OK`, `FLG-PROTECT-SELECT-ROWS-YES`; MOVE error message |
| Validate selection codes (V3, V4)          | `2250-EDIT-ARRAY`: INSPECT tallying 'S' and 'U'; checks `I > 1` for multiple selections; PERFORM VARYING loop validates each row for `SELECT-OK`, `SELECT-BLANK`, or OTHER |
| Read cards forward (BR5)                   | `9000-READ-FORWARD`: `EXEC CICS STARTBR DATASET('CARDDAT') RIDFLD(WS-CARD-RID-CARDNUM) GTEQ`; loop `EXEC CICS READNEXT` up to `WS-MAX-SCREEN-LINES` (7); peek-ahead READNEXT for next-page check; `EXEC CICS ENDBR` |
| Read cards backward (BR6)                  | `9100-READ-BACKWARDS`: `EXEC CICS STARTBR` GTEQ; initial `EXEC CICS READPREV` to position; loop READPREV filling array from position 7 down to 1; `EXEC CICS ENDBR` |
| Filter records (BR3)                       | `9500-FILTER-RECORDS`: checks `FLG-ACCTFILTER-ISVALID` then `CARD-ACCT-ID = CC-ACCT-ID`; checks `FLG-CARDFILTER-ISVALID` then `CARD-NUM = CC-CARD-NUM-N`; sets `WS-EXCLUDE-THIS-RECORD` or `WS-DONOT-EXCLUDE-THIS-RECORD` |
| Send screen output                         | `1000-SEND-MAP` → `1100-SCREEN-INIT` (titles, date/time, page#) → `1200-SCREEN-ARRAY-INIT` (populate 7 rows) → `1250-SETUP-ARRAY-ATTRIBS` (protect/unprotect, error highlighting) → `1300-SETUP-SCREEN-ATTRS` (filter fields, cursor) → `1400-SETUP-MESSAGE` (info/error messages) → `1500-SEND-SCREEN` (`EXEC CICS SEND MAP('CCRDLIA') MAPSET('COCRDLI') FROM(CCRDLIAO) CURSOR ERASE`) |
| Navigate to Card Detail View (BR10 — 'S')  | `0000-MAIN` EVALUATE block, WHEN `CCARD-AID-ENTER AND VIEW-REQUESTED-ON(I-SELECTED)`: sets `CDEMO-ACCT-ID`, `CDEMO-CARD-NUM` from `WS-ROW-ACCTNO(I-SELECTED)`, `WS-ROW-CARD-NUM(I-SELECTED)`; `EXEC CICS XCTL PROGRAM('COCRDSLC') COMMAREA(CARDDEMO-COMMAREA)` |
| Navigate to Card Update (BR10 — 'U')       | `0000-MAIN` EVALUATE block, WHEN `CCARD-AID-ENTER AND UPDATE-REQUESTED-ON(I-SELECTED)`: same data passing; `EXEC CICS XCTL PROGRAM('COCRDUPC') COMMAREA(CARDDEMO-COMMAREA)` |
| Return to Main Menu                        | `0000-MAIN` lines 384-406: WHEN `CCARD-AID-PFK03 AND CDEMO-FROM-PROGRAM = LIT-THISPGM`: `EXEC CICS XCTL PROGRAM('COMEN01C') COMMAREA(CARDDEMO-COMMAREA)` |
| Pseudo-conversational return               | `COMMON-RETURN`: appends `WS-THIS-PROGCOMMAREA` to `WS-COMMAREA`; `EXEC CICS RETURN TRANSID('CCLI') COMMAREA(WS-COMMAREA) LENGTH(LENGTH OF WS-COMMAREA)` |
| Empty result detection (BR11)              | `9000-READ-FORWARD` ENDFILE handling: checks `WS-CA-SCREEN-NUM = 1 AND WS-SCRN-COUNTER = 0`; sets `WS-NO-RECORDS-FOUND` to TRUE which moves `'NO RECORDS FOUND FOR THIS SEARCH CONDITION.'` |
| End-of-data message                        | `9000-READ-FORWARD`: on ENDFILE, MOVE `'NO MORE RECORDS TO SHOW'` TO `WS-ERROR-MSG`; `1400-SETUP-MESSAGE`: handles `CA-FIRST-PAGE` + PF7 → `'NO PREVIOUS PAGES TO DISPLAY'`; `CA-LAST-PAGE-SHOWN` + PF8 → `'NO MORE PAGES TO DISPLAY'` |
| Error highlighting (BR15)                  | `1250-SETUP-ARRAY-ATTRIBS`: checks `WS-ROW-CRDSELECT-ERROR(n) = '1'`; MOVE `DFHRED` to color attribute; `1300-SETUP-SCREEN-ATTRS`: checks `FLG-ACCTFILTER-NOT-OK` / `FLG-CARDFILTER-NOT-OK`; MOVE `DFHRED` to filter color |

### Source File Mapping

| Business Concept                  | Source File(s)                                                       |
|-----------------------------------|----------------------------------------------------------------------|
| Main program logic                | `app/cbl/COCRDLIC.cbl` (1,460 lines)                                |
| Screen layout definition          | `app/bms/COCRDLI.bms` (mapset COCRDLI, map CCRDLIA)                 |
| Card record structure             | `app/cpy/CVACT02Y.cpy` (CARD-RECORD, 150 bytes)                     |
| Work area (AID keys, filters)     | `app/cpy/CVCRD01Y.cpy` (CC-WORK-AREAS)                              |
| Shared navigation state           | `app/cpy/COCOM01Y.cpy` (CARDDEMO-COMMAREA)                          |
| Screen titles                     | `app/cpy/COTTL01Y.cpy` (CCDA-TITLE01, CCDA-TITLE02)                 |
| Date/time formatting              | `app/cpy/CSDAT01Y.cpy` (WS-CURDATE-DATA, WS-CURTIME-HH-MM-SS)      |
| Common messages                   | `app/cpy/CSMSG01Y.cpy` (CCDA-MSG-THANK-YOU, CCDA-MSG-INVALID-KEY)   |
| User security structure           | `app/cpy/CSUSR01Y.cpy` (SEC-USER-DATA)                              |
| Function key mapping              | `app/cpy/CSSTRPFY.cpy` (YYYY-STORE-PFKEY paragraph)                 |
| Upstream: Main Menu               | `app/cbl/COMEN01C.cbl`                                              |
| Downstream: Card Detail View      | `app/cbl/COCRDSLC.cbl`                                              |
| Downstream: Card Update           | `app/cbl/COCRDUPC.cbl`                                              |

### Migration Considerations

| #  | Consideration                                                                                                                                                      |
|----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| M1 | **VSAM Browse to Database Query**: The forward/backward STARTBR/READNEXT/READPREV/ENDBR pattern should be replaced with a database query using OFFSET/LIMIT (or keyset pagination). The current key-based cursor navigation maps naturally to keyset pagination (WHERE card_num > :last_card ORDER BY card_num LIMIT 7). |
| M2 | **COMMAREA to Session/State Management**: The COMMAREA pattern (shared navigation area + program-specific extension appended at a byte offset) should be replaced with a session store or API state. The program-specific extension (first/last card keys, page number, next-page flag) maps to server-side pagination state or stateless query parameters. |
| M3 | **BMS Map to Modern UI**: The 24x80 fixed terminal layout with 7 rows should be replaced with a responsive table/grid component with dynamic pagination. Field attributes (color, protection, cursor positioning) map to CSS classes and HTML input attributes. |
| M4 | **XCTL to API Routing**: The XCTL transfers to COCRDSLC and COCRDUPC should become API route navigations (e.g., `/cards/:cardNum/detail` and `/cards/:cardNum/update`). The COMMAREA fields passed (account ID, card number) become route parameters or query parameters. |
| M5 | **Pseudo-Conversational to Stateless**: The SEND MAP / RETURN TRANSID / RECEIVE MAP cycle is inherently pseudo-conversational. A modern equivalent is a REST API with GET (list/page) and navigation links, or a single-page application with client-side state. |
| M6 | **Fixed Page Size**: The hardcoded 7-row page size should become configurable or dynamic based on the UI viewport. Consider supporting user-selectable page sizes (e.g., 10, 25, 50). |
| M7 | **Filter Validation**: The numeric-only filter validation (IS NOT NUMERIC) should be preserved but can be implemented as frontend input masks or backend validation. Consider adding partial/wildcard matching as a modern enhancement. |
| M8 | **Alternate Index Not Used**: The CARDAIX alternate index is defined but unused by this program. A migration could leverage a database index on account ID for more efficient account-based filtering instead of the current full-browse-and-filter approach. |
| M9 | **Error Message Format**: Technical error messages (containing operation names and response codes) should be translated to user-friendly messages in the modern system, with technical details logged server-side. |

---

## Section 10: Related Use Cases / End-to-End Composition

| Journey Name                    | Use Cases Involved                                                | Business Scenario                                                                                      |
|---------------------------------|-------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| Card Inquiry Journey            | UC-CC00-001 (Sign-on) → UC-CM00-001 (Main Menu) → **UC-CCLI-001 (Card List)** → UC-CCDL-001 (Card Detail) | Customer service agent looks up a specific card by account number and views its full details            |
| Card Update Journey             | UC-CC00-001 → UC-CM00-001 → **UC-CCLI-001** → UC-CCUP-001 (Card Update) | Card operations specialist finds a card and updates its status or details                               |
| Multi-Card Review Journey       | UC-CC00-001 → UC-CM00-001 → **UC-CCLI-001** → UC-CCDL-001 → **UC-CCLI-001** → UC-CCDL-001 → ... | Auditor reviews multiple cards for an account, returning to the list between each detail view           |
| Account-to-Card Investigation   | UC-CC00-001 → UC-CM00-001 → UC-CAVW-001 (Account View) → UC-CM00-001 → **UC-CCLI-001** → UC-CCDL-001 | Analyst checks account-level info first, then investigates the associated cards via the card list       |
| Card Deactivation Workflow      | UC-CC00-001 → UC-CM00-001 → **UC-CCLI-001** → UC-CCUP-001 → **UC-CCLI-001** | Operator locates a compromised card, updates it to inactive, and returns to the list to confirm the change |

> **Note:** Individual use case flows referenced above (UC-CC00-001, UC-CM00-001, UC-CCDL-001, UC-CCUP-001, UC-CAVW-001) may not yet have their own Business Use Case Flow documents. These must be created separately and then composed into full end-to-end journey documents for complete validation.

---

## Document Footer

| Attribute                    | Value                                                     |
|------------------------------|-----------------------------------------------------------|
| **Source Program**           | COCRDLIC.cbl (1,460 lines)                                |
| **Transaction ID**           | CCLI                                                      |
| **Data Source**              | Technical Flow Analysis (`docs/COCRDLIC_Flow_Analysis.md`) cross-checked against COBOL source code |
| **Acceptance Criteria**      | 22 (AC-01 through AC-22)                                  |
| **Business Rules**           | 15 (BR1 through BR15)                                     |
| **Validation Rules**         | 4 (V1 through V4)                                         |
| **Alternative/Error Flows**  | 8 (AF1 through AF8)                                       |
| **Migration Considerations** | 9 (M1 through M9)                                         |

# COACTVWC — Account View: Business Use Case Flow Analysis

---

## Section 1: Use Case Overview

| Attribute            | Value                                                                 |
|----------------------|-----------------------------------------------------------------------|
| **Use Case Name**    | View Account Details                                                  |
| **Use Case ID**      | UC-CAVW-001                                                           |
| **Actor(s)**         | Authenticated User (regular user role)                                |
| **Business Goal**    | Allow a user to look up and view the complete details of a credit card account, including financial balances, dates, and associated customer information |
| **Preconditions**    | 1. User is signed in to the CardDemo application<br>2. User has navigated to the Main Menu<br>3. The account being queried exists in the system |
| **Postconditions**   | Account and customer details are displayed on screen; no data is modified |
| **Trigger**          | User selects "Account View" (Option 1) from the Main Menu             |
| **Data Access**      | Read-only                                                             |
| **Frequency**        | On-demand; used by customer service representatives and account managers to review account status during customer inquiries |

### Business Context

The Account View use case is the primary read-only account inquiry function in the CardDemo credit card management system. It enables authorized users to retrieve and display a comprehensive snapshot of any credit card account by entering the 11-digit account number. The system resolves the account through a cross-reference lookup, then retrieves both the financial account data (balances, limits, dates) and the associated customer demographic data (name, address, contact information, credit score). This is a foundational use case that supports customer service operations, account verification, and routine account inquiries. Because it is strictly read-only, it carries no risk of data modification.

### Functional Domain Map

```
+-----------------------------------------------------------------------+
|                     CardDemo Application Domains                      |
+-----------------------------------------------------------------------+
|                                                                       |
|  +------------------+    +------------------+    +------------------+ |
|  |   User Access    |    | Account Mgmt     |    | Card Management  | |
|  |------------------|    |------------------|    |------------------| |
|  | - Sign-on        |    | >>>ACCOUNT VIEW<<<|   | - Card List      | |
|  | - Main Menu      |--->| - Account Update |--->| - Card Detail    | |
|  | - Admin Menu     |    |                  |    | - Card Update    | |
|  +------------------+    +------------------+    +------------------+ |
|                                                                       |
|  +------------------+    +------------------+    +------------------+ |
|  | Transactions     |    | Billing          |    | Reporting        | |
|  |------------------|    |------------------|    |------------------| |
|  | - Txn List       |    | - Bill Payment   |    | - Reports        | |
|  | - Txn View       |    |                  |    |                  | |
|  | - Txn Add        |    |                  |    |                  | |
|  +------------------+    +------------------+    +------------------+ |
+-----------------------------------------------------------------------+
```

---

## Section 2: User Journey (Happy Path)

### Sequence Diagram

```
  +---------+                           +------------------+
  |  User   |                           |     System       |
  +---------+                           +------------------+
       |                                         |
       |  1. Select "Account View" from menu     |
       |---------------------------------------->|
       |                                         |
       |  2. Display blank Account View screen   |
       |     with prompt for account number      |
       |<----------------------------------------|
       |                                         |
       |  3. Enter 11-digit account number       |
       |     and press Enter                     |
       |---------------------------------------->|
       |                                         |
       |         +---------------------------+   |
       |         | Validate account number   |   |
       |         | Look up cross-reference   |   |
       |         | Retrieve account record   |   |
       |         | Retrieve customer record  |   |
       |         +---------------------------+   |
       |                                         |
       |  4. Display account and customer        |
       |     details on screen                   |
       |<----------------------------------------|
       |                                         |
       |  5. Review displayed information        |
       |                                         |
       |  6. (Optional) Enter a different        |
       |     account number and press Enter      |
       |---------------------------------------->|
       |                                         |
       |  7. Display new account details         |
       |<----------------------------------------|
       |                                         |
       |  8. Press F3 to return to Main Menu     |
       |---------------------------------------->|
       |                                         |
       |  9. Display Main Menu                   |
       |<----------------------------------------|
       |                                         |
```

### Step-by-Step Narrative

| Step | Actor  | Action                                             | System Response                                                                                         |
|------|--------|----------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| 1    | User   | Selects "Account View" (Option 1) from Main Menu  | Navigates to the Account View screen                                                                    |
| 2    | System | --                                                 | Displays a blank Account View form with the prompt "Enter or update id of account to display"           |
| 3    | User   | Types an 11-digit account number and presses Enter | --                                                                                                      |
| 4    | System | --                                                 | Validates the account number, retrieves the account cross-reference, account record, and customer record |
| 5    | System | --                                                 | Populates all account fields (status, balances, limits, dates, group) and customer fields (name, address, contact, credit score) on screen. Displays "Enter or update id of account to display" as prompt |
| 6    | User   | Reviews the displayed account and customer details | --                                                                                                      |
| 7    | User   | (Optional) Enters a different account number and presses Enter | System repeats lookup and displays new account details                                      |
| 8    | User   | Presses F3 to exit                                 | --                                                                                                      |
| 9    | System | --                                                 | Returns the user to the Main Menu (or the screen they navigated from)                                   |

---

## Section 3: Business Rules & Validations

### Input Validation Rules

| #  | Field          | Rule                                                                                                    | Error Message Shown to User                                              |
|----|----------------|---------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| V1 | Account Number | Must not be blank or empty                                                                              | `Account number not provided`                                            |
| V2 | Account Number | Must be numeric (digits only) and must not be all zeros                                                 | `Account Filter must  be a non-zero 11 digit number`                     |
| V3 | Account Number | Must exist in the account cross-reference file                                                          | `Account:{id} not found in Cross ref file.  Resp:{code} Reas:{code}`    |
| V4 | Account Number | Must exist in the account master file                                                                   | `Account:{id} not found in Acct Master file.Resp:{code} Reas:{code}`    |
| V5 | Account Number | The associated customer must exist in the customer master file                                          | `CustId:{id} not found in customer master.Resp: {code} REAS:{code}`     |
| V6 | Account Number | The account number input field enforces "must fill" — the user must enter all 11 digits before the field is accepted | *(Field-level enforcement; no separate error message — the terminal prevents partial input)* |

### Business Rules

| #   | Rule                                                                                                                                                  |
|-----|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| BR1 | **Read-Only Access**: This screen displays account and customer information in a view-only mode. No data can be modified through this screen          |
| BR2 | **Cross-Reference Resolution**: Account lookup follows a chain — the system first resolves the account number through a cross-reference to find the associated customer and card, then retrieves the full account and customer records separately |
| BR3 | **Sequential Dependency**: Each data retrieval step depends on the success of the previous step. If the cross-reference lookup fails, the system does not attempt to read the account or customer records. If the account read fails, the system does not attempt to read the customer record |
| BR4 | **SSN Display Format**: The customer's Social Security Number is displayed in formatted form (XXX-XX-XXXX) rather than as a raw 9-digit number       |
| BR5 | **Currency Display Format**: All monetary fields (current balance, credit limit, cash credit limit, current cycle credit, current cycle debit) are displayed in signed currency format with comma separators (+ZZZ,ZZZ,ZZZ.99)                     |
| BR6 | **Single Account Display**: Only one account can be viewed at a time. To view a different account, the user enters a new account number in the same input field |
| BR7 | **Return Navigation**: When the user exits, the system returns them to the screen they came from. If the originating screen cannot be determined, the system defaults to the Main Menu |
| BR8 | **Invalid Key Handling**: Any function key other than Enter or F3 is treated as Enter. The system does not display an "invalid key" message — it simply processes the input as if Enter were pressed |
| BR9 | **Error Field Highlighting**: When the account number fails validation, the input field is highlighted in red to draw the user's attention to the error |
| BR10| **Blank Input Indicator**: If the user submits the form without entering an account number, the system displays an asterisk (*) in the account number field (in red) to indicate the missing input |

### Decision Table

```
+-------------------------------+--------------------------------------------------+
| User Action                   | System Response                                  |
+-------------------------------+--------------------------------------------------+
| First entry to screen         | Display blank form with input prompt              |
| Enter + valid account number  | Look up and display account/customer details      |
| Enter + blank account number  | Show error: account number not provided,          |
|                               | display '*' in red in the input field             |
| Enter + non-numeric input     | Show error: must be non-zero 11-digit number,     |
|                               | highlight field in red                            |
| Enter + all zeros             | Show error: must be non-zero 11-digit number,     |
|                               | highlight field in red                            |
| Enter + account not found     | Show error with account ID and response codes     |
| F3                            | Return to calling screen (default: Main Menu)     |
| Any other key (F1, F2, etc.) | Treated as Enter — process current input          |
+-------------------------------+--------------------------------------------------+
```

---

## Section 4: Data Entities Involved

### Entity Descriptions

| Entity                | Description                                                                                            | Role in This Use Case                                |
|-----------------------|--------------------------------------------------------------------------------------------------------|------------------------------------------------------|
| Account Cross-Reference | Links account numbers to their associated card numbers and customer identifiers                       | Used as the first lookup step to resolve the customer and card associated with the given account number |
| Account Master        | Contains the financial details of each credit card account: balances, limits, dates, status, and group | Primary data source for the account financial information displayed on screen                          |
| Customer Master       | Contains the demographic and contact details of each customer: name, address, phone, SSN, credit score | Data source for the customer information section of the display                                        |

### Entity Relationships

```
+-------------------------+       +-------------------------+
|  Account Cross-Ref      |       |  Account Master         |
|-------------------------|       |-------------------------|
| Card Number (key)       |       | Account ID (key)        |
| Customer ID             |       | Active Status           |
| Account ID              |------>| Current Balance         |
+------------+------------+       | Credit Limit            |
             |                    | Cash Credit Limit       |
             |                    | Open Date               |
             |                    | Expiration Date         |
             |                    | Reissue Date            |
             |                    | Cycle Credit / Debit    |
             |                    | Group ID                |
             |                    +-------------------------+
             |
             |  1        1
             +----------->+-------------------------+
                          |  Customer Master        |
                          |-------------------------|
                          | Customer ID (key)       |
                          | First / Middle / Last   |
                          | Address (3 lines)       |
                          | State / Country / ZIP   |
                          | Phone 1 / Phone 2       |
                          | SSN                     |
                          | Government ID           |
                          | Date of Birth           |
                          | EFT Account ID          |
                          | Primary Cardholder Flag |
                          | FICO Credit Score       |
                          +-------------------------+

Cardinality:
  Account Cross-Ref  1 ----> 1  Account Master   (by Account ID)
  Account Cross-Ref  1 ----> 1  Customer Master  (by Customer ID)
```

### Data Fields Displayed

| #  | Field                    | Format              | Source            | Description                                      |
|----|--------------------------|---------------------|-------------------|--------------------------------------------------|
| 1  | Account Number           | 11 digits           | User Input        | The account identifier entered by the user       |
| 2  | Active Status            | 1 character (Y/N)   | Account Master    | Whether the account is currently active           |
| 3  | Opened Date              | 10 characters       | Account Master    | Date the account was opened                       |
| 4  | Credit Limit             | +ZZZ,ZZZ,ZZZ.99    | Account Master    | Maximum credit limit for the account              |
| 5  | Expiration Date          | 10 characters       | Account Master    | Account expiration date                           |
| 6  | Cash Credit Limit        | +ZZZ,ZZZ,ZZZ.99    | Account Master    | Maximum cash advance limit                        |
| 7  | Reissue Date             | 10 characters       | Account Master    | Date the account/card was last reissued           |
| 8  | Current Balance          | +ZZZ,ZZZ,ZZZ.99    | Account Master    | Current outstanding balance                       |
| 9  | Current Cycle Credit     | +ZZZ,ZZZ,ZZZ.99    | Account Master    | Total credits in the current billing cycle        |
| 10 | Account Group            | 10 characters       | Account Master    | Account group classification                      |
| 11 | Current Cycle Debit      | +ZZZ,ZZZ,ZZZ.99    | Account Master    | Total debits in the current billing cycle         |
| 12 | Customer ID              | 9 digits            | Customer Master   | Unique identifier for the account holder          |
| 13 | SSN                      | XXX-XX-XXXX         | Customer Master   | Social Security Number (formatted with dashes)    |
| 14 | Date of Birth            | 10 characters       | Customer Master   | Customer's date of birth                          |
| 15 | FICO Score               | 3 digits            | Customer Master   | Customer's FICO credit score                      |
| 16 | First Name               | Up to 25 characters | Customer Master   | Customer's first name                             |
| 17 | Middle Name              | Up to 25 characters | Customer Master   | Customer's middle name                            |
| 18 | Last Name                | Up to 25 characters | Customer Master   | Customer's last name                              |
| 19 | Address Line 1           | Up to 50 characters | Customer Master   | Primary street address                            |
| 20 | State                    | 2 characters        | Customer Master   | State code                                        |
| 21 | Address Line 2           | Up to 50 characters | Customer Master   | Secondary address line                            |
| 22 | ZIP Code                 | Up to 5 characters  | Customer Master   | Postal code                                       |
| 23 | City                     | Up to 50 characters | Customer Master   | City name                                         |
| 24 | Country                  | 3 characters        | Customer Master   | Country code                                      |
| 25 | Phone 1                  | Up to 13 characters | Customer Master   | Primary phone number                              |
| 26 | Government Issued ID     | Up to 20 characters | Customer Master   | Government-issued identification reference        |
| 27 | Phone 2                  | Up to 13 characters | Customer Master   | Secondary phone number                            |
| 28 | EFT Account ID           | Up to 10 characters | Customer Master   | Electronic funds transfer account identifier      |
| 29 | Primary Card Holder      | 1 character (Y/N)   | Customer Master   | Whether this customer is the primary cardholder   |

**Fields in underlying records but NOT displayed on this screen:**

- Account Master: ZIP Code (ACCT-ADDR-ZIP) — present in the account record but not mapped to any screen field
- Cross-Reference: Card Number — retrieved during the lookup chain but not displayed on the Account View screen
- Cross-Reference: Customer ID — used internally to look up the customer record but the displayed customer ID comes from the customer master record
- Customer Master: The full 10-character ZIP code is available but only 5 characters are displayed on screen

---

## Section 5: Screen / Interface Description

### Screen Layout

```
+------------------------------------------------------------------------------+
| Tran: CAVW       AWS Mainframe Modernization          Date: 05/07/26        |
| Prog: COACTVWC             CardDemo                   Time: 14:30:22        |
|                                                                              |
|                                  View Account                                |
|                    Account Number : 00000000041      Active Y/N: Y           |
|         Opened: 2014-06-06            Credit Limit        :  +5,000,000.00   |
|         Expiry: 2024-12-31            Cash credit Limit   :  +5,000,000.00   |
|        Reissue: 2024-01-01            Current Balance     :     +10,000.00   |
|                                       Current Cycle Credit:      +1,000.00   |
|        Account Group: GROUP001        Current Cycle Debit :        +500.00   |
|                                 Customer Details                             |
|        Customer id  : 000000041       SSN: 123-45-6789                       |
|        Date of birth: 1975-06-15      FICO Score: 750                        |
| First Name            Middle Name:             Last Name :                   |
| Jane                      Marie                      Doe                     |
| Address: 123 Main Street                                      State  CA      |
|          Apt 4B                                               Zip    90210   |
| City   Los Angeles                                            Country USA    |
| Phone 1: 310-555-0101  Government Issued Id Ref    :  DL-CA-12345678        |
| Phone 2: 310-555-0102  EFT Account Id:  EFT0000041  Primary Card Holder Y/N: Y |
|                                                                              |
|                        Enter or update id of account to display              |
|                                                                              |
|   F3=Exit                                                                    |
+------------------------------------------------------------------------------+
```

### Interface Elements

| Element              | Type    | Description                                                                       |
|----------------------|---------|-----------------------------------------------------------------------------------|
| Account Number       | Input   | 11-digit numeric field where the user enters the account to look up. This is the only editable field on the screen. Cursor is positioned here on every screen display |
| Active Status        | Display | Shows Y or N indicating whether the account is active                             |
| Opened Date          | Display | Date the account was opened                                                       |
| Credit Limit         | Display | Maximum credit amount, formatted with sign and comma separators                   |
| Expiration Date      | Display | Account expiration date                                                           |
| Cash Credit Limit    | Display | Maximum cash advance amount, formatted with sign and comma separators             |
| Reissue Date         | Display | Date the card was last reissued                                                   |
| Current Balance      | Display | Outstanding balance, formatted with sign and comma separators                     |
| Current Cycle Credit | Display | Credits posted in the current billing cycle                                       |
| Account Group        | Display | Account group classification code                                                 |
| Current Cycle Debit  | Display | Debits posted in the current billing cycle                                        |
| Customer ID          | Display | Unique identifier of the associated customer                                      |
| SSN                  | Display | Social Security Number displayed as XXX-XX-XXXX                                   |
| Date of Birth        | Display | Customer's date of birth                                                          |
| FICO Score           | Display | Customer's credit score (3 digits)                                                |
| First / Middle / Last Name | Display | Customer's full name in three separate fields                               |
| Address Lines 1-2    | Display | Street address in two lines                                                       |
| City                 | Display | City name                                                                         |
| State                | Display | Two-character state code                                                          |
| ZIP Code             | Display | Postal code (5 characters displayed)                                              |
| Country              | Display | Three-character country code                                                      |
| Phone 1 / Phone 2   | Display | Primary and secondary phone numbers                                               |
| Government Issued ID | Display | Government identification reference                                               |
| EFT Account ID       | Display | Electronic funds transfer account identifier                                      |
| Primary Card Holder  | Display | Y/N flag indicating primary cardholder status                                     |
| Info Message         | Display | Informational prompt shown in the center of the screen (row 22)                   |
| Error Message        | Display | Error or status message displayed in red at the bottom of the screen (row 23)     |

### Available Actions

| Action            | How to Invoke | Description                                                                     |
|-------------------|---------------|---------------------------------------------------------------------------------|
| Look Up Account   | Enter         | Submit the account number to retrieve and display account and customer details   |
| Exit to Menu      | F3            | Return to the previous screen (typically the Main Menu)                         |
| Any Other Key     | Any key        | Treated identically to Enter — processes the current input                      |

---

## Section 6: Alternative & Error Flows

### Alt Flow 1: Account Number Not Provided (Blank Input)

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | User presses Enter without typing an account number (or the field contains only spaces)           |
| 2    | System detects the account number field is blank                                                  |
| 3    | System displays an asterisk (*) in red in the account number field to indicate the missing value  |
| 4    | System displays error message: `Account number not provided`                                      |
| 5    | Cursor is repositioned to the account number field                                                |
| 6    | User may enter a valid account number and try again                                               |

### Alt Flow 2: Account Number Not Numeric or All Zeros

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | User enters a value that contains non-numeric characters, or enters all zeros (00000000000)       |
| 2    | System detects the input is not a valid numeric account number                                    |
| 3    | System highlights the account number field in red                                                 |
| 4    | System displays error message: `Account Filter must  be a non-zero 11 digit number`              |
| 5    | Cursor is repositioned to the account number field                                                |
| 6    | User may correct the account number and try again                                                 |

### Alt Flow 3: Account Not Found in Cross-Reference

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | User enters a valid 11-digit numeric account number and presses Enter                             |
| 2    | System looks up the account number in the cross-reference file                                    |
| 3    | No matching record is found in the cross-reference                                                |
| 4    | System displays error message: `Account:{id} not found in Cross ref file.  Resp:{code} Reas:{code}` |
| 5    | System does NOT attempt to read account or customer records (sequential dependency — BR3)         |
| 6    | Cursor is repositioned to the account number field for correction                                 |

### Alt Flow 4: Account Not Found in Account Master

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | User enters a valid account number; cross-reference lookup succeeds                               |
| 2    | System attempts to read the account details from the account master file                          |
| 3    | No matching account record is found                                                               |
| 4    | System displays error message: `Account:{id} not found in Acct Master file.Resp:{code} Reas:{code}` |
| 5    | System does NOT attempt to read the customer record (sequential dependency — BR3)                 |
| 6    | Cursor is repositioned to the account number field for correction                                 |

### Alt Flow 5: Customer Not Found in Customer Master

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | User enters a valid account number; cross-reference and account lookups succeed                   |
| 2    | System attempts to read the customer record using the customer ID from the cross-reference        |
| 3    | No matching customer record is found                                                              |
| 4    | System displays error message: `CustId:{id} not found in customer master.Resp: {code} REAS:{code}` |
| 5    | Cursor is repositioned to the account number field for correction                                 |

### Alt Flow 6: System I/O Error on Cross-Reference File

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | User enters a valid account number and presses Enter                                              |
| 2    | System attempts to read the cross-reference file but encounters an unexpected I/O error           |
| 3    | System displays error message: `File Error: READ     on CXACAIX  returned RESP {code}   ,RESP2 {code}` |
| 4    | Cursor is repositioned to the account number field                                                |

### Alt Flow 7: System I/O Error on Account Master File

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | Cross-reference lookup succeeds, but reading the account master file encounters an I/O error      |
| 2    | System displays error message: `File Error: READ     on ACCTDAT  returned RESP {code}   ,RESP2 {code}` |
| 3    | Cursor is repositioned to the account number field                                                |

### Alt Flow 8: System I/O Error on Customer Master File

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | Cross-reference and account lookups succeed, but reading the customer master file encounters an I/O error |
| 2    | System displays error message: `File Error: READ     on CUSTDAT  returned RESP {code}   ,RESP2 {code}` |
| 3    | Cursor is repositioned to the account number field                                                |

### Alt Flow 9: Unexpected System Error (Abend)

| Step | Description                                                                                       |
|------|---------------------------------------------------------------------------------------------------|
| 1    | An unexpected system error occurs during processing                                               |
| 2    | System displays abend information including the program name and error details                    |
| 3    | The transaction is terminated (user must restart from the Main Menu)                              |

---

## Section 7: Business Process Context

### Navigation Context

```
CardDemo Application
|
+-- Sign-on (CC00)
|   |
|   +-- Main Menu (CM00) .................. [Regular Users]
|   |   |
|   |   +-- >>> Account View (CAVW) <<< ... [THIS USE CASE]
|   |   +-- Account Update (CAUP)
|   |   +-- Card List (CCLI)
|   |   +-- Card Detail (CCDL)
|   |   +-- Card Update (CCUP)
|   |   +-- Transaction List (CT01)
|   |   +-- Transaction View (CT02)
|   |   +-- Transaction Add (CT03)
|   |   +-- Bill Payment (CB00)
|   |   +-- Reports (CR00)
|   |
|   +-- Admin Menu (CA00) ................. [Admin Users]
|       |
|       +-- User List (CU01)
|       +-- User Add (CU02)
|       +-- User Update (CU03)
|       +-- User Delete (CU04)
```

### Downstream Use Cases

| Destination        | Trigger                                  | What Happens Next                                                         |
|--------------------|------------------------------------------|---------------------------------------------------------------------------|
| Main Menu (CM00)   | User presses F3                          | User returns to the Main Menu and can select any other function           |
| Calling Screen     | User presses F3 (when called from a different context) | User returns to whichever screen originally navigated to Account View |

**Note:** Account View does not directly navigate to any downstream operational screens. It is a terminal view-only screen. The user must return to the menu to access other functions.

### Upstream Use Cases

| Source              | Navigation Path                                                        |
|---------------------|------------------------------------------------------------------------|
| Main Menu (CM00)    | User selects Option 1 ("Account View") from the Main Menu             |

### End-to-End Journey Examples

**Journey 1: Customer Service — Account Status Inquiry**

```
Sign-on --> Main Menu --> Account View --> [review details] --> Main Menu
```
> A customer calls to ask about their account balance. The representative signs in, navigates to Account View, enters the account number, confirms the balance and credit limit, then returns to the menu.

**Journey 2: Account Verification Before Transaction Entry**

```
Sign-on --> Main Menu --> Account View --> [verify account] --> Main Menu --> Transaction Add
```
> Before manually adding a transaction, the user first views the account to confirm it is active and has sufficient credit, then returns to the menu and navigates to Transaction Add.

**Journey 3: Customer Onboarding Verification**

```
Sign-on --> Main Menu --> Account View --> [verify account & customer data] --> Main Menu --> Card List
```
> After a new account is set up through batch processing, a user views the account to verify all details were loaded correctly, then checks the Card List to confirm the associated card was created.

**Journey 4: Account Review and Update Workflow**

```
Sign-on --> Main Menu --> Account View --> [review current state] --> Main Menu --> Account Update
```
> A supervisor reviews the current account details before navigating to Account Update to make changes to the account record.

---

## Section 8: Acceptance Criteria

### Core Functionality

| #     | Given                                              | When                                             | Then                                                                              |
|-------|----------------------------------------------------|--------------------------------------------------|-----------------------------------------------------------------------------------|
| AC-01 | User is on the Main Menu                           | User selects "Account View" (Option 1)           | The Account View screen is displayed with a blank account number field and the prompt "Enter or update id of account to display" |
| AC-02 | User is on the Account View screen                 | User enters a valid 11-digit account number and presses Enter | The system displays all account details (status, balances, limits, dates, group) and all customer details (name, address, contact info, SSN, FICO score) |
| AC-03 | Account details are currently displayed            | User enters a different valid account number and presses Enter | The previously displayed data is replaced with the new account's details          |
| AC-04 | Account details are currently displayed            | User presses F3                                  | The system returns the user to the Main Menu (or the originating screen)          |
| AC-05 | User is on the Account View screen                 | User presses any key other than Enter or F3      | The system treats the key press as Enter and processes the current input           |

### Input Validation

| #     | Given                                              | When                                             | Then                                                                              |
|-------|----------------------------------------------------|--------------------------------------------------|-----------------------------------------------------------------------------------|
| AC-06 | User is on the Account View screen                 | User presses Enter without entering an account number | Error message "Account number not provided" is displayed; an asterisk appears in red in the account number field |
| AC-07 | User is on the Account View screen                 | User enters a non-numeric value and presses Enter | Error message "Account Filter must  be a non-zero 11 digit number" is displayed; the account number field is highlighted in red |
| AC-08 | User is on the Account View screen                 | User enters 00000000000 (all zeros) and presses Enter | Error message "Account Filter must  be a non-zero 11 digit number" is displayed; the account number field is highlighted in red |
| AC-09 | User is on the Account View screen                 | User enters a valid numeric account number that does not exist in the cross-reference | An error message is displayed indicating the account was not found in the cross-reference file, including response codes |
| AC-10 | User is on the Account View screen                 | User enters an account that exists in the cross-reference but not in the account master | An error message is displayed indicating the account was not found in the account master file, including response codes |
| AC-11 | User is on the Account View screen                 | User enters an account whose cross-reference exists and account exists, but the associated customer does not exist | An error message is displayed indicating the customer was not found in the customer master file, including response codes |

### Data Display

| #     | Given                                              | When                                             | Then                                                                              |
|-------|----------------------------------------------------|--------------------------------------------------|-----------------------------------------------------------------------------------|
| AC-12 | A valid account lookup has completed successfully  | The account details screen is displayed          | The SSN is displayed in formatted form XXX-XX-XXXX (not as a raw 9-digit number)  |
| AC-13 | A valid account lookup has completed successfully  | The account details screen is displayed          | All monetary fields (balance, limits, cycle amounts) are displayed in +ZZZ,ZZZ,ZZZ.99 format |
| AC-14 | A valid account lookup has completed successfully  | The account details screen is displayed          | All account and customer fields listed in Section 4 are populated from the correct data source |

### Navigation

| #     | Given                                              | When                                             | Then                                                                              |
|-------|----------------------------------------------------|--------------------------------------------------|-----------------------------------------------------------------------------------|
| AC-15 | User navigated to Account View from the Main Menu  | User presses F3                                  | System returns user to the Main Menu                                              |
| AC-16 | User navigated to Account View from a different screen | User presses F3                              | System returns user to that originating screen (not the Main Menu)                |

### Data Integrity

| #     | Given                                              | When                                             | Then                                                                              |
|-------|----------------------------------------------------|--------------------------------------------------|-----------------------------------------------------------------------------------|
| AC-17 | User views an account                              | Lookup completes successfully                    | No data in any file is created, modified, or deleted — the operation is strictly read-only |
| AC-18 | Cross-reference lookup fails                       | System processes the error                       | The system does not attempt to read the account master or customer master files    |
| AC-19 | Account master lookup fails                        | System processes the error                       | The system does not attempt to read the customer master file                       |

### Error Handling

| #     | Given                                              | When                                             | Then                                                                              |
|-------|----------------------------------------------------|--------------------------------------------------|-----------------------------------------------------------------------------------|
| AC-20 | An I/O error occurs reading any data file          | System processes the error                       | A descriptive error message is displayed including the file name and response codes |
| AC-21 | An unexpected system error occurs                  | The abend handler is triggered                   | Abend information is displayed and the transaction is terminated                  |

---

## Section 9: Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                    | Technical Reference                                                                                   |
|--------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| User selects Account View from Main Menu         | `COMEN01C` performs `EXEC CICS XCTL PROGRAM('COACTVWC')` with COMMAREA; `CDEMO-PGM-CONTEXT` set to 0 |
| Display blank form on first entry                | Paragraph `0000-MAIN`, `WHEN CDEMO-PGM-ENTER` branch → `1000-SEND-MAP`                               |
| Set prompt message for input                     | `1200-SETUP-SCREEN-VARS`: `SET WS-PROMPT-FOR-INPUT TO TRUE` (line 463/529)                            |
| Receive user input on re-entry                   | Paragraph `2100-RECEIVE-MAP`: `EXEC CICS RECEIVE MAP(LIT-THISMAP) MAPSET(LIT-THISMAPSET) INTO(CACTVWAI)` |
| Validate account number — blank check            | Paragraph `2210-EDIT-ACCOUNT` (lines 649-662): checks `CC-ACCT-ID EQUAL LOW-VALUES OR SPACES`         |
| Validate account number — numeric/zero check     | Paragraph `2210-EDIT-ACCOUNT` (lines 666-680): checks `CC-ACCT-ID IS NOT NUMERIC OR EQUAL ZEROES`     |
| Look up account in cross-reference               | Paragraph `9200-GETCARDXREF-BYACCT`: `EXEC CICS READ DATASET('CXACAIX') RIDFLD(WS-CARD-RID-ACCT-ID-X)` |
| Extract customer ID and card number from xref    | Lines 739-740: `MOVE XREF-CUST-ID TO CDEMO-CUST-ID`, `MOVE XREF-CARD-NUM TO CDEMO-CARD-NUM`          |
| Read account master record                       | Paragraph `9300-GETACCTDATA-BYACCT`: `EXEC CICS READ DATASET('ACCTDAT') RIDFLD(WS-CARD-RID-ACCT-ID-X)` |
| Read customer master record                      | Paragraph `9400-GETCUSTDATA-BYCUST`: `EXEC CICS READ DATASET('CUSTDAT') RIDFLD(WS-CARD-RID-CUST-ID-X)` |
| Populate account fields on screen                | Paragraph `1200-SETUP-SCREEN-VARS` (lines 471-491): moves from `ACCOUNT-RECORD` fields to `CACTVWAO` output fields |
| Populate customer fields on screen               | Paragraph `1200-SETUP-SCREEN-VARS` (lines 493-523): moves from `CUSTOMER-RECORD` fields to `CACTVWAO` output fields |
| Format SSN as XXX-XX-XXXX                        | Lines 496-504: `STRING CUST-SSN(1:3) '-' CUST-SSN(4:2) '-' CUST-SSN(6:4) INTO ACSTSSNO`              |
| Highlight account field in red on error          | Paragraph `1300-SETUP-SCREEN-ATTRS` (lines 557-559): `MOVE DFHRED TO ACCTSIDC OF CACTVWAO`            |
| Display asterisk for blank input on re-entry     | Paragraph `1300-SETUP-SCREEN-ATTRS` (lines 561-565): `MOVE '*' TO ACCTSIDO`, `MOVE DFHRED TO ACCTSIDC` |
| Send map to terminal                             | Paragraph `1400-SEND-SCREEN`: `EXEC CICS SEND MAP(CCARD-NEXT-MAP) MAPSET(CCARD-NEXT-MAPSET) FROM(CACTVWAO) CURSOR ERASE FREEKB` |
| Return and wait for next input                   | `COMMON-RETURN`: `EXEC CICS RETURN TRANSID('CAVW') COMMAREA(WS-COMMAREA)`                             |
| Handle PF3 — exit to calling program             | `0000-MAIN`, `WHEN CCARD-AID-PFK03` branch (lines 324-352): resolves return target, `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA)` |
| Map terminal key press to application flag       | `YYYY-STORE-PFKEY` paragraph (copybook `CSSTRPFY`): maps `EIBAID` to `CCARD-AID-xxx` flags             |
| Invalid key treated as Enter                     | Lines 306-314: `SET PFK-INVALID TO TRUE` → if not Enter or PF3 → `SET CCARD-AID-ENTER TO TRUE`        |
| Register abend handler                           | Line 264: `EXEC CICS HANDLE ABEND LABEL(ABEND-ROUTINE)`                                               |
| Abend handler processing                         | Paragraph `ABEND-ROUTINE` (lines 916-936): sends `ABEND-DATA`, cancels handler, issues `ABEND ABCODE('9999')` |

### Source File Mapping

| Business Concept                   | Source File(s)                                                                          |
|------------------------------------|-----------------------------------------------------------------------------------------|
| Account View program logic         | `app/cbl/COACTVWC.cbl` (942 lines)                                                     |
| Screen layout and field definitions| `app/bms/COACTVW.bms` (mapset COACTVW, map CACTVWA)                                    |
| Application navigation structure   | `app/cpy/COCOM01Y.cpy` (CARDDEMO-COMMAREA)                                             |
| Common work area and AID mapping   | `app/cpy/CVCRD01Y.cpy` (CC-WORK-AREAS)                                                 |
| PF key mapping logic               | `app/cpy/CSSTRPFY.cpy` (YYYY-STORE-PFKEY paragraph)                                    |
| Account record layout              | `app/cpy/CVACT01Y.cpy` (ACCOUNT-RECORD, 300 bytes)                                     |
| Card cross-reference record layout | `app/cpy/CVACT03Y.cpy` (CARD-XREF-RECORD, 50 bytes)                                    |
| Customer record layout             | `app/cpy/CVCUS01Y.cpy` (CUSTOMER-RECORD, 500 bytes)                                    |
| Card record layout (included, unused) | `app/cpy/CVACT02Y.cpy` (CARD-RECORD, 150 bytes — not used in I/O)                   |
| Screen title literals              | `app/cpy/COTTL01Y.cpy` (CCDA-SCREEN-TITLE)                                             |
| Date/time working storage          | `app/cpy/CSDAT01Y.cpy`                                                                 |
| Common messages                    | `app/cpy/CSMSG01Y.cpy` (CCDA-COMMON-MESSAGES)                                          |
| Abend data structure               | `app/cpy/CSMSG02Y.cpy` (ABEND-DATA)                                                    |
| User security data structure       | `app/cpy/CSUSR01Y.cpy` (SEC-USER-DATA)                                                 |
| Upstream: Main Menu program        | `app/cbl/COMEN01C.cbl`                                                                  |
| Upstream: Sign-on program          | `app/cbl/COSGN00C.cbl`                                                                  |

### Migration Considerations

| #  | Consideration                                                                                                                                                                                                      |
|----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| M1 | **Pseudo-conversational to stateful session**: The pseudo-conversational pattern (RETURN TRANSID with COMMAREA) must be replaced with a stateful web session or API-based request/response pattern. The COMMAREA fields that track navigation context (FROM-PROGRAM, FROM-TRANID, PGM-CONTEXT) should map to session state or URL routing |
| M2 | **Alternate index lookup**: The CXACAIX alternate index path over the cross-reference base cluster must be replicated as a secondary index or composite query in the target database (e.g., SQL index on account_id in the cross-reference table, or a secondary GSI in DynamoDB) |
| M3 | **BMS screen to modern UI**: The 24x80 fixed-format BMS screen should be replaced with a responsive web form. The single-input / display-all pattern maps naturally to a search-and-display page. Consider adding a search results list for partial matches (not in current implementation) |
| M4 | **SSN masking**: The current system displays the full SSN (formatted but unmasked). Modern compliance requirements (PCI-DSS, privacy regulations) typically require masking SSN to show only the last 4 digits (XXX-XX-6789). This should be added during migration |
| M5 | **Fixed-width to variable-length fields**: All data fields use fixed-width EBCDIC encoding. Migration targets should use variable-length Unicode (UTF-8) strings with appropriate maximum lengths |
| M6 | **Hardcoded messages to resource bundle**: Error messages and prompts are embedded as COBOL 88-level literals and inline STRING statements. These should be externalized to a message catalog or i18n resource bundle |
| M7 | **COMMAREA navigation to URL routing**: The XCTL-based navigation between programs with COMMAREA should be replaced with URL-based routing (e.g., REST endpoints, SPA routes). The "return to calling program" logic (BR7) maps to browser back navigation or breadcrumb patterns |
| M8 | **Read-only data access**: This use case is strictly read-only. The target architecture should use read replicas or optimized query paths where available. Consider caching frequently accessed account data |
| M9 | **CVACT02Y dead code**: The CARD-RECORD copybook (CVACT02Y) is included in working storage but never used in any file I/O operation. This dead code should be removed during migration rather than carried forward |
| M10| **Field-level security**: The current system displays all account and customer fields to any authenticated user. Modern systems should implement role-based field visibility (e.g., hide SSN, FICO score from users without appropriate clearance) |
| M11| **Audit logging**: Read operations are not logged in the current system. The migration target should add audit logging for account inquiries to meet regulatory compliance requirements |
| M12| **Input field enforcement**: The BMS map uses MUSTFILL validation for the account number field, requiring all 11 digits before the terminal accepts input. The modern UI equivalent would be client-side validation with a pattern/mask (e.g., `pattern="[0-9]{11}"`) combined with server-side validation |

---

## Section 10: Related Use Cases / End-to-End Composition

| Journey Name                          | Use Cases Involved                                          | Business Scenario                                                                                          |
|---------------------------------------|-------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| Account Inquiry and Update            | UC-CAVW-001 (Account View) → UC-CAUP-001 (Account Update)  | User reviews the current state of an account, then navigates to update account details (e.g., change status, adjust limits) |
| Account Verification and Card Review  | UC-CAVW-001 (Account View) → UC-CCLI-001 (Card List) → UC-CCDL-001 (Card Detail) | User verifies account details, then reviews the cards associated with the account and drills into card-level details |
| Pre-Transaction Account Check         | UC-CAVW-001 (Account View) → UC-CT03-001 (Transaction Add) | User verifies account status and available credit before manually posting a new transaction                  |
| Customer Service Full Inquiry         | UC-CAVW-001 (Account View) → UC-CCLI-001 (Card List) → UC-CT01-001 (Transaction List) → UC-CT02-001 (Transaction View) | Representative reviews the complete account picture: account details, associated cards, and recent transactions to assist a customer with a billing inquiry |
| Account Review and Bill Payment       | UC-CAVW-001 (Account View) → UC-CB00-001 (Bill Payment)    | User checks the current balance and cycle activity, then initiates a bill payment based on what they see    |

**Note:** Each use case referenced above requires its own Business Use Case Flow document. Individual use case flows must be composed into journey-level test scenarios for complete end-to-end validation of the modernized application.

---

## Document Footer

| Attribute                  | Value                            |
|----------------------------|----------------------------------|
| **Source Program**         | COACTVWC.cbl (942 lines)        |
| **Transaction ID**         | CAVW                             |
| **Data Source**            | Technical Flow Analysis + COBOL source verification |
| **Acceptance Criteria**    | 21                               |
| **Business Rules**         | 10                               |
| **Validation Rules**       | 6                                |
| **Alternative/Error Flows**| 9                                |

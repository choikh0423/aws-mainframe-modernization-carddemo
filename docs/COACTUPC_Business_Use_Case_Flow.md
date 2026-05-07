# CICS Business Use Case Flow Analysis
# CAUP Transaction -- Account Update (COACTUPC)

---

## 1. Use Case Overview

| Attribute              | Description                                                                                          |
|------------------------|------------------------------------------------------------------------------------------------------|
| **Use Case ID**        | UC-CAUP-001                                                                                          |
| **Use Case Name**      | Update Account and Customer Details                                                                  |
| **Primary Actor**      | CardDemo User (regular user role)                                                                    |
| **Supporting Actors**  | Account Master Data Store, Customer Master Data Store, Card Cross-Reference Data Store               |
| **Business Goal**      | Enable a user to look up a credit card account by its 11-digit identifier, review the current account and customer information, modify editable fields, and persist the validated changes to the system of record. |
| **Preconditions**      | 1. The user has signed in to the CardDemo application and has been authenticated. 2. The user has navigated to the Account Update function from the Main Menu (or has been routed from a card-list drill-down). 3. The account to be updated already exists in the system. |
| **Postconditions**     | 1. On success: the account master record and the associated customer master record reflect the user's validated changes. 2. On failure / cancellation: no data has been modified; any partially applied changes have been rolled back. |
| **Trigger**            | The user selects the Account Update option from the Main Menu, or is routed to this function from a card-list screen. |
| **Data Accessed**      | Card Cross-Reference (read-only lookup), Account Master (read and update), Customer Master (read and update) |
| **Estimated Frequency**| On-demand -- executed whenever an operator needs to modify account or customer attributes.            |
| **Business Context**   | Account Update is one of the core maintenance functions in the CardDemo credit card management system. It is the only function that can modify both account-level financial attributes (credit limits, balances, dates) and customer-level demographic attributes (name, address, phone, SSN, FICO score) in a single operation. Because two separate data stores are updated atomically, the system enforces a confirmation step and a concurrent-change check to protect data integrity. |

### High-Level Flow

```
                    +--------------------------+
                    |       Main Menu          |
                    |       (CM00)             |
                    +-----------+--------------+
                                |
                                | User selects "Account Update"
                                v
                    +--------------------------+
                    |  Account Update Screen   |
                    |  (CAUP / UC-CAUP-001)    |
                    |                          |
                    |  1. Enter Account ID     |
                    |  2. View account +       |
                    |     customer details     |
                    |  3. Modify fields        |
                    |  4. Review & confirm     |
                    |  5. Save changes         |
                    +-----------+--------------+
                                |
                                | F3 = Exit
                                v
                    +--------------------------+
                    |       Main Menu          |
                    |       (CM00)             |
                    +--------------------------+
```

---

## 2. User Journey (Happy Path)

### Sequence Diagram

```
  User                    System                  Account       Customer     Card Xref
   |                        |                     Store          Store        Store
   |  1. Open screen        |                       |              |            |
   |----------------------->|                       |              |            |
   |  2. Empty form shown   |                       |              |            |
   |<-----------------------|                       |              |            |
   |  3. Enter Account ID   |                       |              |            |
   |  + press Enter         |                       |              |            |
   |----------------------->|                       |              |            |
   |                        |  4. Validate format   |              |            |
   |                        |---+                   |              |            |
   |                        |   |                   |              |            |
   |                        |<--+                   |              |            |
   |                        |  5. Look up card xref |              |            |
   |                        |---------------------------------------------->|  |
   |                        |  6. Xref found        |              |         |  |
   |                        |<----------------------------------------------|  |
   |                        |  7. Read account      |              |            |
   |                        |---------------------->|              |            |
   |                        |  8. Account found     |              |            |
   |                        |<----------------------|              |            |
   |                        |  9. Read customer     |              |            |
   |                        |------------------------------>|      |            |
   |                        | 10. Customer found    |       |      |            |
   |                        |<------------------------------|      |            |
   | 11. Display account +  |                       |              |            |
   |     customer details   |                       |              |            |
   |<-----------------------|                       |              |            |
   | 12. Modify fields      |                       |              |            |
   |  + press Enter         |                       |              |            |
   |----------------------->|                       |              |            |
   |                        | 13. Validate inputs   |              |            |
   |                        |---+                   |              |            |
   |                        |   |                   |              |            |
   |                        |<--+                   |              |            |
   | 14. Confirmation       |                       |              |            |
   |     prompt shown       |                       |              |            |
   |<-----------------------|                       |              |            |
   | 15. Press F5 (Save)    |                       |              |            |
   |----------------------->|                       |              |            |
   |                        | 16. Lock account      |              |            |
   |                        |---------------------->|              |            |
   |                        | 17. Lock customer     |              |            |
   |                        |------------------------------>|      |            |
   |                        | 18. Check no one else |       |      |            |
   |                        |     changed records   |       |      |            |
   |                        |---+                   |       |      |            |
   |                        |   |                   |       |      |            |
   |                        |<--+                   |       |      |            |
   |                        | 19. Write account     |       |      |            |
   |                        |---------------------->|       |      |            |
   |                        | 20. Write customer    |       |      |            |
   |                        |------------------------------>|      |            |
   | 21. Success message    |                       |              |            |
   |     "Changes committed |                       |              |            |
   |      to database"      |                       |              |            |
   |<-----------------------|                       |              |            |
   |                        |                       |              |            |
```

### Step-by-Step Narrative

| Step | Actor  | Action                                                                                               | System Response                                                                                       |
|------|--------|------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| 1    | User   | Navigates to Account Update from Main Menu                                                           | System displays an empty Account Update screen with the Account ID field editable and all other fields blank. The information area shows: `Enter or update id of account to update` |
| 2    | User   | Types an 11-digit account number into the Account ID field and presses **Enter**                     | System validates the account number format (must be 11 digits, numeric, non-zero).                    |
| 3    | System | --                                                                                                   | System looks up the account in the card cross-reference to find the linked customer and card numbers.  |
| 4    | System | --                                                                                                   | System reads the full account record (financial data, dates, status) from the Account Master.          |
| 5    | System | --                                                                                                   | System reads the full customer record (demographics, contact info) from the Customer Master.           |
| 6    | System | --                                                                                                   | System populates all screen fields with current values and shows `Details of selected account shown above`. All detail fields become editable; the Account ID field becomes read-only. |
| 7    | User   | Modifies one or more fields (e.g., credit limit, address, phone number) and presses **Enter**        | System compares the submitted values to the original values to detect whether any changes were made.    |
| 8    | System | --                                                                                                   | If changes are detected, the system validates every modified field against the applicable business rules. |
| 9    | System | --                                                                                                   | If all validations pass, the system displays `Changes validated.Press F5 to save` and shows the **F5=Save** key. All fields become read-only (confirmation mode). |
| 10   | User   | Reviews the changes and presses **F5** to confirm the save                                           | System acquires an exclusive lock on both the account and customer records.                            |
| 11   | System | --                                                                                                   | System performs a concurrent-change check by comparing the current database values to the snapshot taken when data was first displayed. |
| 12   | System | --                                                                                                   | If no concurrent changes are detected, the system writes the updated account record, then writes the updated customer record. Both writes succeed atomically. |
| 13   | System | --                                                                                                   | System displays `Changes committed to database` and resets the screen to accept a new account number. |

---

## 3. Business Rules & Validations

### Input Validation Rules

| ID   | Field                 | Rule                                                                                                  | Error Message (exact)                                                              |
|------|-----------------------|-------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| V-01 | Account ID            | Must be exactly 11 digits, numeric, and non-zero                                                      | `Account Number if supplied must be a 11 digit Non-Zero Number`                    |
| V-02 | Account ID            | Must not be blank when searching                                                                      | `Account number not provided`                                                      |
| V-03 | Account Status        | Must be `Y` or `N` (yes/no indicator); cannot be blank                                                | `Account Status must be Y or N.` / `Account Status must be supplied.`              |
| V-04 | Credit Limit          | Must be a valid signed numeric currency value; cannot be blank                                        | `Credit Limit must be supplied.` / `Credit Limit is not valid`                     |
| V-05 | Cash Credit Limit     | Must be a valid signed numeric currency value; cannot be blank                                        | `Cash Credit Limit must be supplied.` / `Cash Credit Limit is not valid`           |
| V-06 | Current Balance       | Must be a valid signed numeric currency value; cannot be blank                                        | `Current Balance must be supplied.` / `Current Balance is not valid`               |
| V-07 | Cycle Credit          | Must be a valid signed numeric currency value; cannot be blank                                        | `Current Cycle Credit Limit must be supplied.` / `Current Cycle Credit Limit is not valid` |
| V-08 | Cycle Debit           | Must be a valid signed numeric currency value; cannot be blank                                        | `Current Cycle Debit Limit must be supplied.` / `Current Cycle Debit Limit is not valid`   |
| V-09 | Open Date             | Must be a valid calendar date in CCYYMMDD format                                                      | (date validation messages from shared date routine)                                |
| V-10 | Expiry Date           | Must be a valid calendar date in CCYYMMDD format                                                      | (date validation messages from shared date routine)                                |
| V-11 | Reissue Date          | Must be a valid calendar date in CCYYMMDD format                                                      | (date validation messages from shared date routine)                                |
| V-12 | SSN (Part 1)          | First 3 digits: must be numeric, non-zero, and must not be 000, 666, or 900-999                       | `SSN: First 3 chars must be supplied.` / `SSN: First 3 chars: should not be 000, 666, or between 900 and 999` |
| V-13 | SSN (Part 2)          | Middle 2 digits: must be numeric and non-zero                                                         | `SSN 4th & 5th chars must be supplied.` / `SSN 4th & 5th chars must be all numeric.` / `SSN 4th & 5th chars must not be zero.` |
| V-14 | SSN (Part 3)          | Last 4 digits: must be numeric and non-zero                                                           | `SSN Last 4 chars must be supplied.` / `SSN Last 4 chars must be all numeric.` / `SSN Last 4 chars must not be zero.` |
| V-15 | Date of Birth         | Must be a valid calendar date; additionally validated as a birth date (not in future)                  | (date validation messages from shared date routine)                                |
| V-16 | FICO Score            | Must be a 3-digit number between 300 and 850 inclusive                                                | `FICO Score must be supplied.` / `FICO Score must be all numeric.` / `FICO Score: should be between 300 and 850` |
| V-17 | First Name            | Required; alphabetic characters and spaces only (max 25 characters)                                   | `First Name must be supplied.` / `First Name can have alphabets only.`             |
| V-18 | Middle Name           | Optional; if supplied, alphabetic characters and spaces only (max 25 characters)                      | `Middle Name can have alphabets only.`                                             |
| V-19 | Last Name             | Required; alphabetic characters and spaces only (max 25 characters)                                   | `Last Name must be supplied.` / `Last Name can have alphabets only.`               |
| V-20 | Address Line 1        | Required; cannot be blank (max 50 characters)                                                         | `Address Line 1 must be supplied.`                                                 |
| V-21 | State Code            | Required; must be alphabetic and a valid US state code from the lookup table                          | `State must be supplied.` / `State can have alphabets only.` / `State: is not a valid state code` |
| V-22 | City                  | Required; alphabetic characters and spaces only                                                       | `City must be supplied.` / `City can have alphabets only.`                         |
| V-23 | Zip Code              | Required; must be numeric (5 digits) and non-zero                                                     | `Zip must be supplied.` / `Zip must be all numeric.` / `Zip must not be zero.`     |
| V-24 | Country Code          | Required; must be alphabetic (3 characters)                                                           | `Country must be supplied.` / `Country can have alphabets only.`                   |
| V-25 | Phone Number 1        | Optional as a whole; if any part is entered, all three parts (area code, prefix, line number) must be valid. Area code: 3-digit non-zero numeric, must be a valid North American area code. Prefix: 3-digit non-zero numeric. Line number: 4-digit non-zero numeric. | `Phone Number 1: Area code must be supplied.` / `Phone Number 1: Area code must be A 3 digit number.` / `Phone Number 1: Area code cannot be zero` / `Phone Number 1: Not valid North America general purpose area code` / (similar messages for prefix and line number) |
| V-26 | Phone Number 2        | Same rules as Phone Number 1                                                                          | (same pattern as Phone Number 1 messages)                                          |
| V-27 | EFT Account ID        | Required; must be numeric (10 digits) and non-zero                                                    | `EFT Account Id must be supplied.` / `EFT Account Id must be all numeric.` / `EFT Account Id must not be zero.` |
| V-28 | Primary Card Holder   | Must be `Y` or `N`; cannot be blank                                                                  | `Primary Card Holder must be supplied.` / `Primary Card Holder must be Y or N.`    |
| V-29 | State + Zip (cross-field) | When both state and zip are individually valid, the first two digits of the zip must be consistent with the state code per USPS rules | `Invalid zip code for state`                                                       |

### Business Rules

| ID   | Rule Name                         | Description                                                                                                                          |
|------|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| BR-01| Account Lookup via Cross-Reference| When the user provides an account ID, the system first looks up the card cross-reference by account to obtain the linked customer ID and card number, then reads the account and customer records separately. |
| BR-02| Snapshot-Based Change Detection   | After the user submits changes, the system compares every field in the new submission against the original values displayed. If no differences are found, the system shows `No change detected with respect to values fetched.` and does not proceed to the confirmation step. The comparison is case-insensitive for text fields and exact for numeric and date fields. |
| BR-03| Two-Step Confirmation             | Data modifications require explicit two-step confirmation: (1) the user presses Enter to submit changes for validation, and (2) the user presses F5 to confirm and save. The user may press F12 to cancel during the confirmation step. |
| BR-04| Concurrent Change Protection      | Before writing updates, the system re-reads both the account and customer records from the data store and compares every field against the snapshot taken when data was first displayed. If any field has been changed by another user since the data was displayed, the update is rejected and the screen is refreshed with the latest values. The message shown is: `Record changed by some one else. Please review` |
| BR-05| Atomic Two-Record Update          | The account record and customer record are updated as a single atomic operation. If the customer record update fails after the account record update has succeeded, the account update is rolled back to maintain data consistency. |
| BR-06| Record Locking                    | Before performing updates, the system acquires exclusive locks on both the account record and the customer record. If either lock cannot be acquired, the update is aborted. Messages: `Could not lock account record for update` or `Could not lock customer record for update`. |
| BR-07| Screen State Machine              | The screen operates through a defined set of states: (1) Search mode -- only Account ID is editable; (2) Edit mode -- all detail fields are editable; (3) Confirmation mode -- all fields are read-only, F5 and F12 are visible; (4) Success/Failure mode -- message displayed, screen resets. |
| BR-08| Function Key Availability         | F5 (Save) and F12 (Cancel) are only active during the confirmation state. At all other times, only Enter and F3 (Exit) are recognized. Any other key press is treated as Enter. |
| BR-09| Address Line 2 is Optional        | Address Line 2 has no validation rules applied and may be left blank.                                                                 |
| BR-10| Phone Numbers Are Optional        | Phone Number 1 and Phone Number 2 are optional as a whole. However, if any part of a phone number is entered (area code, prefix, or line number), all three parts must be provided and must pass validation. |
| BR-11| Customer ID is Read-Only          | Although the Customer ID field appears on the screen, it is populated by the system from the cross-reference lookup and cannot be edited by the user. |
| BR-12| Post-Update Reset                 | After a successful update (or a failed update), the screen resets to the initial search state, clearing all detail fields and prompting for a new account number. |
| BR-13| Navigation on Exit                | When the user presses F3, the system returns to the program that invoked Account Update. If the invoking program is unknown (e.g., the session has no routing context), the system returns to the Main Menu. |

### Decision Table -- Update Outcome

| Changes Detected? | Validation Passes? | F5 Pressed? | Lock Acquired? | Concurrent Change? | Outcome                                   |
|--------------------|--------------------|-------------|----------------|---------------------|--------------------------------------------|
| No                 | --                 | --          | --             | --                  | Message: no changes detected; stay in edit |
| Yes                | No                 | --          | --             | --                  | Error message on first failing field       |
| Yes                | Yes                | No (Enter)  | --             | --                  | Remain in confirmation mode                |
| Yes                | Yes                | Yes         | No             | --                  | Error: could not lock record               |
| Yes                | Yes                | Yes         | Yes            | Yes                 | Refresh with latest data; user must re-edit|
| Yes                | Yes                | Yes         | Yes            | No                  | Update committed successfully              |

---

## 4. Data Entities Involved

### Entity Descriptions

| Entity                  | Business Description                                                                                         | Access Pattern          |
|-------------------------|--------------------------------------------------------------------------------------------------------------|-------------------------|
| **Card Cross-Reference**| Links card numbers to account and customer identifiers. Used during the account lookup phase to discover which customer is associated with the given account. | Read-only (by account ID via alternate index) |
| **Account Master**      | Contains the financial and administrative attributes of a credit card account: status, credit limits, balances, cycle amounts, key dates (open, expiry, reissue), and account group. | Read (initial display) and Update (save) |
| **Customer Master**     | Contains the demographic and contact attributes of the customer who owns the account: name, address, phone numbers, SSN, date of birth, FICO score, government ID, EFT account, and primary cardholder indicator. | Read (initial display) and Update (save) |

### Relationship Diagram

```
  +---------------------+          +---------------------+
  | Card Cross-Reference|          |   Account Master    |
  |---------------------|          |---------------------|
  | Card Number (key)   |          | Account ID (key)    |
  | Customer ID  -------+-----+   | Active Status       |
  | Account ID   -------+--+  |   | Credit Limit        |
  +---------------------+  |  |   | Cash Credit Limit   |
                            |  |   | Current Balance     |
          Lookup by Acct ID |  |   | Cycle Credit/Debit  |
          (alternate index) |  |   | Open/Expiry/Reissue |
                            |  |   | Account Group       |
                            |  |   +---------------------+
                            |  |
                            |  |   +---------------------+
                            |  +-->| Customer Master     |
                            |      |---------------------|
                            +----->| Customer ID (key)   |
                                   | First/Middle/Last   |
                                   | Address Lines 1-3   |
                                   | City/State/Zip      |
                                   | Country Code        |
                                   | Phone 1 & 2         |
                                   | SSN                 |
                                   | Date of Birth       |
                                   | FICO Score          |
                                   | Government ID       |
                                   | EFT Account ID      |
                                   | Primary Cardholder  |
                                   +---------------------+
```

### Data Fields Displayed on Screen

| #  | Field Name               | Source Entity      | Editable? | Format / Length          |
|----|--------------------------|--------------------|-----------|--------------------------|
| 1  | Account ID               | Account Master     | Search only | 11 digits              |
| 2  | Active Status            | Account Master     | Yes       | 1 character (Y/N)        |
| 3  | Credit Limit             | Account Master     | Yes       | Signed currency (12 chars)|
| 4  | Cash Credit Limit        | Account Master     | Yes       | Signed currency (12 chars)|
| 5  | Current Balance          | Account Master     | Yes       | Signed currency (12 chars)|
| 6  | Cycle Credit             | Account Master     | Yes       | Signed currency (12 chars)|
| 7  | Cycle Debit              | Account Master     | Yes       | Signed currency (12 chars)|
| 8  | Open Date (YYYY MM DD)   | Account Master     | Yes       | 3 fields: year(4) month(2) day(2) |
| 9  | Expiry Date (YYYY MM DD) | Account Master     | Yes       | 3 fields: year(4) month(2) day(2) |
| 10 | Reissue Date (YYYY MM DD)| Account Master     | Yes       | 3 fields: year(4) month(2) day(2) |
| 11 | Account Group            | Account Master     | Yes       | 10 characters            |
| 12 | Customer ID              | Card Cross-Ref     | No        | 9 digits                 |
| 13 | SSN (3 parts)            | Customer Master    | Yes       | 3-2-4 digits             |
| 14 | Date of Birth (YYYY MM DD)| Customer Master   | Yes       | 3 fields: year(4) month(2) day(2) |
| 15 | FICO Score               | Customer Master    | Yes       | 3 digits (300-850)       |
| 16 | First Name               | Customer Master    | Yes       | 25 characters (alpha)    |
| 17 | Middle Name              | Customer Master    | Yes       | 25 characters (alpha, optional) |
| 18 | Last Name                | Customer Master    | Yes       | 25 characters (alpha)    |
| 19 | Address Line 1           | Customer Master    | Yes       | 50 characters            |
| 20 | Address Line 2           | Customer Master    | Yes       | 50 characters (optional, no validation) |
| 21 | City                     | Customer Master    | Yes       | 50 characters (alpha)    |
| 22 | State                    | Customer Master    | Yes       | 2 characters (valid state code) |
| 23 | Zip Code                 | Customer Master    | Yes       | 10 characters (first 5 validated as numeric) |
| 24 | Country                  | Customer Master    | Yes       | 3 characters (alpha)     |
| 25 | Phone 1 (3 parts)        | Customer Master    | Yes       | 3-3-4 digits (optional)  |
| 26 | Phone 2 (3 parts)        | Customer Master    | Yes       | 3-3-4 digits (optional)  |
| 27 | Government ID            | Customer Master    | Yes       | 20 characters            |
| 28 | EFT Account ID           | Customer Master    | Yes       | 10 digits                |
| 29 | Primary Card Holder      | Customer Master    | Yes       | 1 character (Y/N)        |

---

## 5. Screen / Interface Description

### Screen Layout (24 rows x 80 columns)

```
+------------------------------------------------------------------------------+
| CAUP  AWS Mainframe Modernization          MM/DD/YY  COACTUPC  CardDemo  HH:MM:SS |
|                         Account Update                                       |
|                                                                              |
|  Account ID: [___________]                                                   |
|                                                                              |
|  Acct Status: [_] Opened: [____] [__] [__]   Credit Limit: [____________]   |
|  Expiry Dt:  [____] [__] [__]   Cash Credit Limit: [____________]           |
|  Reissue Dt: [____] [__] [__]   Curr Balance:     [____________]            |
|  Curr Cyc Credit: [____________]  Acct Group: [__________]                  |
|  Curr Cyc Debit:  [____________]                                             |
|                                                                              |
|  Customer ID: [_________]                                                    |
|  SSN: [___]-[__]-[____]   Date of Birth: [____] [__] [__]  FICO: [___]     |
|  First Name: [_________________________]  Mid: [_________________________]  |
|  Last Name:  [_________________________]                                     |
|  Address 1:  [__________________________________________________]           |
|  Address 2:  [__________________________________________________]           |
|  City:       [__________________________________________________]           |
|  State: [__]  Zip: [__________]  Country: [___]                             |
|  Phone 1: [___] [___] [____]  EFT Account Id: [__________]                 |
|  Phone 2: [___] [___] [____]  Primary Card Holder Y/N: [_]                 |
|                                                                              |
|                       [information message area]                             |
| [error / return message area                                                ]|
| ENTER=Process F3=Exit  F5=Save  F12=Cancel                                  |
+------------------------------------------------------------------------------+
```

### Interface Elements

| Element                | Type           | Position (Row, Col) | Length | Description                                      |
|------------------------|----------------|---------------------|--------|--------------------------------------------------|
| Transaction Name       | Display only   | Row 1               | 4      | Shows `CAUP`                                     |
| Title Line 1           | Display only   | Row 1               | 40     | `AWS Mainframe Modernization`                    |
| Current Date           | Display only   | Row 1               | 8      | MM/DD/YY format                                  |
| Program Name           | Display only   | Row 1               | 8      | Shows `COACTUPC`                                 |
| Title Line 2           | Display only   | Row 1               | 40     | `CardDemo`                                       |
| Current Time           | Display only   | Row 1               | 8      | HH:MM:SS format                                  |
| Screen Title           | Display only   | Row 2               | 40     | `Account Update`                                 |
| Account ID             | Input          | Row 4               | 11     | Editable in search mode only                     |
| Active Status          | Input          | Row 6               | 1      | Y or N                                           |
| Open Date (Yr/Mo/Dy)   | Input          | Row 6               | 4+2+2  | Year, Month, Day fields                          |
| Credit Limit           | Input          | Row 6               | 12     | Signed currency                                  |
| Expiry Date (Yr/Mo/Dy) | Input          | Row 7               | 4+2+2  | Year, Month, Day fields                          |
| Cash Credit Limit      | Input          | Row 7               | 12     | Signed currency                                  |
| Reissue Date (Yr/Mo/Dy)| Input          | Row 8               | 4+2+2  | Year, Month, Day fields                          |
| Current Balance        | Input          | Row 8               | 12     | Signed currency                                  |
| Cycle Credit           | Input          | Row 9               | 12     | Signed currency                                  |
| Account Group          | Input          | Row 9               | 10     | Alphanumeric                                     |
| Cycle Debit            | Input          | Row 10              | 12     | Signed currency                                  |
| Customer ID            | Display only   | Row 12              | 9      | System-populated from cross-reference            |
| SSN (3 parts)          | Input          | Row 13              | 3+2+4  | Numeric parts separated by dashes                |
| Date of Birth (Yr/Mo/Dy)| Input         | Row 13              | 4+2+2  | Year, Month, Day fields                          |
| FICO Score             | Input          | Row 13              | 3      | Numeric 300-850                                  |
| First Name             | Input          | Row 14              | 25     | Alphabetic only                                  |
| Middle Name            | Input          | Row 14              | 25     | Alphabetic only (optional)                       |
| Last Name              | Input          | Row 15              | 25     | Alphabetic only                                  |
| Address Line 1         | Input          | Row 16              | 50     | Required                                         |
| Address Line 2         | Input          | Row 17              | 50     | Optional, no validation                          |
| City                   | Input          | Row 18              | 50     | Alphabetic only                                  |
| State                  | Input          | Row 19              | 2      | Valid US state code                              |
| Zip Code               | Input          | Row 19              | 10     | Numeric (first 5 digits validated)               |
| Country                | Input          | Row 19              | 3      | Alphabetic only                                  |
| Phone 1 (3 parts)      | Input          | Row 20              | 3+3+4  | Area code, prefix, line number                   |
| EFT Account ID         | Input          | Row 20              | 10     | Numeric                                          |
| Phone 2 (3 parts)      | Input          | Row 20              | 3+3+4  | Area code, prefix, line number                   |
| Primary Card Holder    | Input          | Row 20              | 1      | Y or N                                           |
| Information Message    | Display only   | Row 22              | 45     | Context-sensitive guidance / status               |
| Error Message          | Display only   | Row 23              | 78     | Red text; shows validation errors or system errors|
| Function Key Legend     | Display only   | Row 24              | varies | `ENTER=Process F3=Exit F5=Save F12=Cancel`       |

### Available Actions

| Action          | Trigger     | Available When                          | Description                                                  |
|-----------------|-------------|------------------------------------------|--------------------------------------------------------------|
| Search          | Enter key   | Search mode (Account ID editable)        | Submits the account ID for lookup                            |
| Submit Changes  | Enter key   | Edit mode (detail fields visible)        | Submits modified fields for validation and change detection   |
| Save            | F5 key      | Confirmation mode only                   | Confirms and persists validated changes                      |
| Cancel Changes  | F12 key     | Confirmation or edit mode                | Discards pending changes and redisplays original values       |
| Exit            | F3 key      | Any mode                                 | Returns to Main Menu or calling program                      |

---

## 6. Alternative & Error Flows

### ALT-01: Account Not Found in Cross-Reference

| Step | Description                                                                                                  |
|------|--------------------------------------------------------------------------------------------------------------|
| 1    | User enters a valid 11-digit account number and presses Enter.                                               |
| 2    | System looks up the account ID in the card cross-reference data store.                                        |
| 3    | The account ID is not found in the cross-reference.                                                           |
| 4    | System displays error: `Account: XXXXXXXXXXX not found in Cross ref file. Resp: XX Reas: XX`                |
| 5    | The cursor is positioned on the Account ID field. The user may enter a different account number.              |

### ALT-02: Account Not Found in Account Master

| Step | Description                                                                                                  |
|------|--------------------------------------------------------------------------------------------------------------|
| 1    | The account ID was found in the cross-reference, but does not exist in the Account Master data store.         |
| 2    | System displays error: `Account: XXXXXXXXXXX not found in Acct Master file. Resp: XX Reas: XX`               |
| 3    | The cursor is positioned on the Account ID field. The user may enter a different account number.              |

### ALT-03: Customer Not Found in Customer Master

| Step | Description                                                                                                  |
|------|--------------------------------------------------------------------------------------------------------------|
| 1    | The account was found, but the linked customer ID does not exist in the Customer Master data store.           |
| 2    | System displays error: `CustId: XXXXXXXXX not found in customer master. Resp: XX REAS: XX`                  |
| 3    | The cursor is positioned on the Account ID field. The user may enter a different account number.              |

### ALT-04: Input Validation Failure

| Step | Description                                                                                                  |
|------|--------------------------------------------------------------------------------------------------------------|
| 1    | User modifies fields and presses Enter.                                                                       |
| 2    | System runs all validations (V-03 through V-29).                                                              |
| 3    | One or more fields fail validation.                                                                            |
| 4    | The error message for the first failing field is displayed in the error message area (red text).               |
| 5    | The cursor is positioned on the first field in error. Fields with errors are highlighted in red, and blank required fields show an asterisk (`*`). |
| 6    | The user corrects the errors and presses Enter again.                                                         |

### ALT-05: No Changes Detected

| Step | Description                                                                                                  |
|------|--------------------------------------------------------------------------------------------------------------|
| 1    | User presses Enter without modifying any field (or reverts all changes to original values).                   |
| 2    | System compares all submitted values to the original snapshot.                                                 |
| 3    | No differences are found.                                                                                      |
| 4    | System displays: `No change detected with respect to values fetched.`                                         |
| 5    | The screen remains in edit mode. The user may make changes or press F3 to exit.                               |

### ALT-06: User Cancels at Confirmation (F12)

| Step | Description                                                                                                  |
|------|--------------------------------------------------------------------------------------------------------------|
| 1    | System is in confirmation mode (`Changes validated.Press F5 to save`).                                        |
| 2    | User presses F12 instead of F5.                                                                               |
| 3    | System discards the pending changes, re-reads the account and customer data from the data store, and redisplays the original values. |
| 4    | The screen returns to edit mode.                                                                               |

### ALT-07: Record Lock Failure

| Step | Description                                                                                                  |
|------|--------------------------------------------------------------------------------------------------------------|
| 1    | User has confirmed changes by pressing F5.                                                                    |
| 2    | System attempts to acquire an exclusive lock on the account record.                                            |
| 3    | The lock cannot be acquired (another process holds the record).                                                |
| 4    | System displays: `Could not lock account record for update`                                                   |
| 5    | The screen resets to the initial search state.                                                                 |

*Note: The same flow applies to the customer record lock failure, with message: `Could not lock customer record for update`*

### ALT-08: Concurrent Change Detected

| Step | Description                                                                                                  |
|------|--------------------------------------------------------------------------------------------------------------|
| 1    | System has locked both records successfully.                                                                  |
| 2    | System compares the locked records against the snapshot from when data was first displayed.                    |
| 3    | One or more fields differ -- another user changed the data after it was displayed on this screen.              |
| 4    | System displays: `Record changed by some one else. Please review`                                            |
| 5    | The screen is refreshed with the latest data values and returns to edit mode. The user must re-apply changes. |

### ALT-09: Update Write Failure

| Step | Description                                                                                                  |
|------|--------------------------------------------------------------------------------------------------------------|
| 1    | System writes the account record successfully.                                                                |
| 2    | System writes the customer record, but the write fails.                                                       |
| 3    | System rolls back the account record update to maintain data consistency.                                      |
| 4    | System displays: `Update of record failed`                                                                    |
| 5    | The screen resets to the initial search state.                                                                 |

*Note: If the account record write itself fails (before the customer write), the same message is shown and the screen resets, but no rollback is needed.*

### ALT-10: User Presses F3 (Exit) at Any Point

| Step | Description                                                                                                  |
|------|--------------------------------------------------------------------------------------------------------------|
| 1    | User presses F3 from any screen state (search, edit, or confirmation mode).                                   |
| 2    | Any uncommitted changes are discarded.                                                                        |
| 3    | System performs a synchronization point to release any held resources.                                         |
| 4    | System transfers control to the calling program (or Main Menu if no caller is recorded).                      |

### ALT-11: Invalid Key Pressed

| Step | Description                                                                                                  |
|------|--------------------------------------------------------------------------------------------------------------|
| 1    | User presses a function key that is not recognized in the current state (e.g., F5 when not in confirmation).  |
| 2    | The key press is treated as if Enter was pressed.                                                              |
| 3    | Normal processing continues based on the current state.                                                        |

---

## 7. Business Process Context

### Navigation Context

```
  +-------------------+
  |     Sign-On       |
  |     (CC00)        |
  +--------+----------+
           |
           v
  +-------------------+      +-------------------+
  |    Main Menu      |----->| Account Update    |
  |    (CM00)         |      | (CAUP)            |
  +--------+----------+      | UC-CAUP-001       |
           |                  +-------------------+
           |                          |
           |                          | F3 = return
           |<-------------------------+
           |
           +-------> Card List (CCLI)
           +-------> Card Detail (CCDL)
           +-------> Card Update (CCUP)
           +-------> Account View (CAVW)
           +-------> Transaction List (CT01)
           +-------> Bill Payment (CB00)
           +-------> Reports (CR00)
```

### Upstream Use Cases

| Use Case                | Relationship                                                                                     |
|-------------------------|--------------------------------------------------------------------------------------------------|
| **UC-CM00: Main Menu**  | Primary entry point. The user selects the Account Update option from the menu to invoke CAUP.     |
| **UC-CCLI: Card List**  | Alternate entry point. The card list screen can route to Account Update with a pre-populated account context. |

### Downstream Use Cases

| Use Case                | Relationship                                                                                     |
|-------------------------|--------------------------------------------------------------------------------------------------|
| None (terminal)         | Account Update does not directly invoke any downstream use case. On exit, it returns to the calling program. |

### End-to-End Journey Examples

#### Journey 1: Update a Customer's Address After a Move

1. **Sign In** (UC-CC00) -- Operator authenticates.
2. **Main Menu** (UC-CM00) -- Operator selects "Account Update."
3. **Account Update** (UC-CAUP-001) -- Operator enters the account number, reviews the displayed address, updates Address Line 1, City, State, and Zip Code, confirms changes via F5.
4. **Main Menu** (UC-CM00) -- Operator presses F3 to return.

#### Journey 2: Adjust Credit Limit and Verify via Account View

1. **Sign In** (UC-CC00) -- Operator authenticates.
2. **Main Menu** (UC-CM00) -- Operator selects "Account Update."
3. **Account Update** (UC-CAUP-001) -- Operator enters the account number, increases the credit limit, confirms via F5.
4. **Main Menu** (UC-CM00) -- Operator selects "Account View."
5. **Account View** (UC-CAVW, *document not yet created*) -- Operator verifies the new credit limit is displayed correctly.

#### Journey 3: Correct Customer SSN from Card List Drill-Down

1. **Sign In** (UC-CC00) -- Operator authenticates.
2. **Main Menu** (UC-CM00) -- Operator selects "Card List."
3. **Card List** (UC-CCLI) -- Operator locates the card and drills down, eventually reaching Account Update with the account pre-populated.
4. **Account Update** (UC-CAUP-001) -- Operator corrects the SSN fields, confirms via F5.
5. **Main Menu** (UC-CM00) -- Operator presses F3 to exit back through the navigation chain.

---

## 8. Acceptance Criteria

### Core Functionality

| ID    | Criterion (Given / When / Then)                                                                                                                                  | Traces To        |
|-------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------|
| AC-01 | **Given** the user is on the Account Update screen in search mode, **when** the user enters a valid 11-digit account number and presses Enter, **then** the system displays account and customer details for that account and the information message reads `Details of selected account shown above`. | BR-01, V-01      |
| AC-02 | **Given** account and customer details are displayed, **when** the user modifies one or more fields and presses Enter, **then** the system validates all fields and, if all pass, displays `Changes validated.Press F5 to save` and enters confirmation mode. | BR-02, BR-03     |
| AC-03 | **Given** the screen is in confirmation mode, **when** the user presses F5, **then** the system saves both the account and customer records atomically and displays `Changes committed to database`. | BR-05, BR-06     |
| AC-04 | **Given** the screen is in confirmation mode, **when** the user presses F5 and the save succeeds, **then** the screen resets to search mode with all detail fields cleared and the information message reads `Enter or update id of account to update`. | BR-12            |
| AC-05 | **Given** any screen state, **when** the user presses F3, **then** the system returns to the Main Menu (or calling program) without modifying any data. | BR-13            |

### Input Validation

| ID    | Criterion (Given / When / Then)                                                                                                                                  | Traces To        |
|-------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------|
| AC-06 | **Given** the user is in search mode, **when** the user presses Enter without entering an account number, **then** the error message `No input received` is displayed and the cursor is on the Account ID field. | V-02             |
| AC-07 | **Given** the user is in search mode, **when** the user enters a non-numeric or zero account number and presses Enter, **then** the error message `Account Number if supplied must be a 11 digit Non-Zero Number` is displayed. | V-01             |
| AC-08 | **Given** detail fields are displayed, **when** the user enters a non-Y/N value in the Account Status field and presses Enter, **then** the error message `Account Status must be Y or N.` is displayed and the cursor is on the Account Status field. | V-03             |
| AC-09 | **Given** detail fields are displayed, **when** the user leaves the Credit Limit blank and presses Enter, **then** the error message `Credit Limit must be supplied.` is displayed. | V-04             |
| AC-10 | **Given** detail fields are displayed, **when** the user enters a non-numeric value in the Credit Limit and presses Enter, **then** the error message `Credit Limit is not valid` is displayed. | V-04             |
| AC-11 | **Given** detail fields are displayed, **when** the user enters a FICO score outside 300-850 and presses Enter, **then** the error message `FICO Score: should be between 300 and 850` is displayed. | V-16             |
| AC-12 | **Given** detail fields are displayed, **when** the user enters SSN first 3 digits as `000`, `666`, or a value between `900` and `999`, **then** the error message `SSN: First 3 chars: should not be 000, 666, or between 900 and 999` is displayed. | V-12             |
| AC-13 | **Given** detail fields are displayed, **when** the user enters numeric characters in the First Name field, **then** the error message `First Name can have alphabets only.` is displayed. | V-17             |
| AC-14 | **Given** detail fields are displayed, **when** the user leaves the First Name blank, **then** the error message `First Name must be supplied.` is displayed. | V-17             |
| AC-15 | **Given** detail fields are displayed, **when** the user enters a valid state code but a zip code whose first two digits are inconsistent with that state, **then** the error message `Invalid zip code for state` is displayed. | V-29             |
| AC-16 | **Given** detail fields are displayed, **when** the user enters a phone area code that is not a valid North American general-purpose area code, **then** the error message contains `Not valid North America general purpose area code`. | V-25             |
| AC-17 | **Given** the user leaves the Middle Name blank, **when** the user presses Enter, **then** no validation error is produced for Middle Name (it is optional). | V-18, BR-09      |
| AC-18 | **Given** detail fields are displayed, **when** the user enters a valid date of birth in the future, **then** the system rejects the date via the date validation routine. | V-15             |
| AC-19 | **Given** detail fields are displayed, **when** the user enters an invalid state code, **then** the error message `State: is not a valid state code` is displayed. | V-21             |

### Change Detection and Confirmation

| ID    | Criterion (Given / When / Then)                                                                                                                                  | Traces To        |
|-------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------|
| AC-20 | **Given** detail fields are displayed, **when** the user presses Enter without modifying any field, **then** the message `No change detected with respect to values fetched.` is displayed and the screen remains in edit mode. | BR-02            |
| AC-21 | **Given** the screen is in confirmation mode, **when** the user presses F12, **then** the pending changes are discarded, original values are redisplayed, and the screen returns to edit mode. | BR-03            |
| AC-22 | **Given** the screen is in confirmation mode, **when** the user presses Enter (instead of F5), **then** the screen remains in confirmation mode awaiting F5 or F12. | BR-08            |

### Data Integrity

| ID    | Criterion (Given / When / Then)                                                                                                                                  | Traces To        |
|-------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------|
| AC-23 | **Given** the user has confirmed changes, **when** another user has modified the same account since data was first displayed, **then** the system shows `Record changed by some one else. Please review`, refreshes the screen with the latest data, and does not apply the update. | BR-04            |
| AC-24 | **Given** the system has successfully updated the account record, **when** the customer record update fails, **then** the account record update is rolled back and the message `Update of record failed` is displayed. | BR-05            |
| AC-25 | **Given** the system attempts to lock the account record for update, **when** the lock cannot be acquired, **then** the message `Could not lock account record for update` is displayed and no data is modified. | BR-06            |
| AC-26 | **Given** the system attempts to lock the customer record for update, **when** the lock cannot be acquired, **then** the message `Could not lock customer record for update` is displayed and no data is modified. | BR-06            |

### Navigation

| ID    | Criterion (Given / When / Then)                                                                                                                                  | Traces To        |
|-------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------|
| AC-27 | **Given** the user arrived at Account Update from the Main Menu, **when** the user presses F3, **then** the system returns to the Main Menu. | BR-13            |
| AC-28 | **Given** the user arrived at Account Update from the Card List screen, **when** the user presses F3, **then** the system returns to the Card List screen. | BR-13            |
| AC-29 | **Given** an unrecognized function key is pressed, **when** the system processes the input, **then** the key is treated as Enter and normal processing continues. | BR-08            |

---

## 9. Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                           | COBOL Paragraph(s)                                     | Key Technical Details                                          |
|-----------------------------------------|---------------------------------------------------------|---------------------------------------------------------------|
| Display empty search screen             | `0000-MAIN` (EVALUATE: `ACUP-DETAILS-NOT-FETCHED` + `CDEMO-PGM-ENTER`), `3000-SEND-MAP`, `3201-SHOW-INITIAL-VALUES` | Initializes `WS-THIS-PROGCOMMAREA`, sends BMS map with all fields set to `LOW-VALUES`, sets `CDEMO-PGM-REENTER`. |
| Receive user input                      | `1000-PROCESS-INPUTS`, `1100-RECEIVE-MAP`               | `EXEC CICS RECEIVE MAP(CACTUPA) MAPSET(COACTUP)`. Maps screen fields to `ACUP-NEW-*` working storage. Fields with `*` or spaces are set to `LOW-VALUES`. |
| Validate account ID (search)            | `1200-EDIT-MAP-INPUTS`, `1210-EDIT-ACCOUNT`             | Checks `CC-ACCT-ID` is numeric and non-zero. Sets `FLG-ACCTFILTER-ISVALID` or `INPUT-ERROR`. |
| Look up card cross-reference            | `9000-READ-ACCT`, `9200-GETCARDXREF-BYACCT`            | `EXEC CICS READ DATASET(CXACAIX) RIDFLD(WS-CARD-RID-ACCT-ID-X)`. On `DFHRESP(NORMAL)`, extracts `XREF-CUST-ID` and `XREF-CARD-NUM`. On `DFHRESP(NOTFND)`, sets error message. |
| Read account master                     | `9300-GETACCTDATA-BYACCT`                               | `EXEC CICS READ DATASET(ACCTDAT) RIDFLD(WS-CARD-RID-ACCT-ID-X)`. On success, sets `FOUND-ACCT-IN-MASTER`. |
| Read customer master                    | `9400-GETCUSTDATA-BYCUST`                               | `EXEC CICS READ DATASET(CUSTDAT) RIDFLD(WS-CARD-RID-CUST-ID-X)`. On success, sets `FOUND-CUST-IN-MASTER`. |
| Store snapshot for change detection     | `9500-STORE-FETCHED-DATA`                               | Copies all fields from `ACCOUNT-RECORD` and `CUSTOMER-RECORD` to `ACUP-OLD-*` working storage. These become the baseline for BR-02 and BR-04. |
| Display account/customer details        | `3000-SEND-MAP`, `3202-SHOW-ORIGINAL-VALUES`            | Moves `ACUP-OLD-*` values to BMS output map `CACTUPAO`. Dates are split into year/month/day components. Currency values are formatted. |
| Compare old vs. new (change detection)  | `1205-COMPARE-OLD-NEW`                                  | Field-by-field comparison using `FUNCTION UPPER-CASE` and `FUNCTION TRIM` for text fields, direct comparison for numeric/date fields. Sets `CHANGE-HAS-OCCURRED` or `NO-CHANGES-DETECTED`. |
| Validate all input fields               | `1200-EDIT-MAP-INPUTS` (update path)                    | Calls specialized edit paragraphs: `1220-EDIT-YESNO` (status, primary holder), `1250-EDIT-SIGNED-9V2` (currency fields), `EDIT-DATE-CCYYMMDD` (dates), `1265-EDIT-US-SSN`, `1275-EDIT-FICO-SCORE`, `1225-EDIT-ALPHA-REQD` (names, city, state, country), `1235-EDIT-ALPHA-OPT` (middle name), `1215-EDIT-MANDATORY` (address), `1245-EDIT-NUM-REQD` (zip, EFT, FICO), `1260-EDIT-US-PHONE-NUM`, `1270-EDIT-US-STATE-CD`, `1280-EDIT-US-STATE-ZIP-CD`. |
| Enter confirmation mode                 | `2000-DECIDE-ACTION` (WHEN `ACUP-SHOW-DETAILS`)         | Sets `ACUP-CHANGES-OK-NOT-CONFIRMED`. Screen attribute setup (`3300-SETUP-SCREEN-ATTRS`) protects all fields. `3250-SETUP-INFOMSG` sets message to `Changes validated.Press F5 to save`. |
| Save (F5 processing)                    | `2000-DECIDE-ACTION` (WHEN `ACUP-CHANGES-OK-NOT-CONFIRMED` AND `CCARD-AID-PFK05`), `9600-WRITE-PROCESSING` | Sequence: (1) `EXEC CICS READ UPDATE FILE(ACCTDAT)` to lock account; (2) `EXEC CICS READ UPDATE FILE(CUSTDAT)` to lock customer; (3) `9700-CHECK-CHANGE-IN-REC` for concurrent change check; (4) Prepare `ACCT-UPDATE-RECORD` and `CUST-UPDATE-RECORD`; (5) `EXEC CICS REWRITE FILE(ACCTDAT)`; (6) `EXEC CICS REWRITE FILE(CUSTDAT)`. On customer REWRITE failure: `EXEC CICS SYNCPOINT ROLLBACK`. |
| Concurrent change check                 | `9700-CHECK-CHANGE-IN-REC`                              | Compares freshly-locked records against `ACUP-OLD-*` snapshot field by field. Uses `FUNCTION LOWER-CASE` for account group, `FUNCTION UPPER-CASE` for customer text fields. Sets `DATA-WAS-CHANGED-BEFORE-UPDATE` if mismatch found. |
| Cancel (F12 processing)                 | `2000-DECIDE-ACTION` (WHEN `CCARD-AID-PFK12`)           | Re-reads account data via `9000-READ-ACCT` and refreshes the display with original values. Returns to edit mode (`ACUP-SHOW-DETAILS`). |
| Exit (F3 processing)                    | `0000-MAIN` (EVALUATE: `CCARD-AID-PFK03`)               | `EXEC CICS SYNCPOINT` to release locks, then `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA)` to transfer to calling program. |
| Return to transaction                   | `COMMON-RETURN`                                         | `EXEC CICS RETURN TRANSID('CAUP') COMMAREA(WS-COMMAREA)`. Preserves session state in the communication area. |
| Abend handling                          | `ABEND-ROUTINE`                                         | Sends abend data to terminal, cancels abend handler, issues `EXEC CICS ABEND ABCODE('9999')`. |

### Source File Mapping

| Source File                   | Type            | Role in This Use Case                                    |
|-------------------------------|-----------------|----------------------------------------------------------|
| `app/cbl/COACTUPC.cbl`       | COBOL program   | Main program (4237 lines) implementing all use case logic |
| `app/bms/COACTUP.bms`        | BMS screen map  | Defines the terminal screen layout (513 lines)            |
| `app/cpy/COCOM01Y.cpy`       | Copybook        | Communication area shared across programs                 |
| `app/cpy/CVACT01Y.cpy`       | Copybook        | Account record layout (300-byte records)                  |
| `app/cpy/CVACT03Y.cpy`       | Copybook        | Card cross-reference record layout (50-byte records)      |
| `app/cpy/CVCUS01Y.cpy`       | Copybook        | Customer record layout (500-byte records)                 |
| `app/cpy/CVCRD01Y.cpy`       | Copybook        | Work area definitions for card/account/customer IDs       |
| `app/cpy/COTTL01Y.cpy`       | Copybook        | Screen title constants                                    |
| `app/cpy/CSDAT01Y.cpy`       | Copybook        | Date/time formatting work areas                           |
| `app/cpy/CSMSG01Y.cpy`       | Copybook        | Common application messages                               |
| `app/cpy/CSMSG02Y.cpy`       | Copybook        | Abend data structure                                      |
| `app/cpy/CSUSR01Y.cpy`       | Copybook        | User security data structure                              |
| `app/cpy/CSUTLDWY.cpy`       | Copybook        | Date validation working storage variables                 |
| `app/cpy/CSUTLDPY.cpy`       | Copybook        | Date validation procedures (inlined via COPY)             |
| `app/cpy/CSSTRPFY.cpy`       | Copybook        | Function key storage procedure (inlined via COPY)         |
| `app/cpy/CSLKPCDY.cpy`       | Copybook        | Lookup code repository (state codes, area codes, state-zip combos) |
| `app/cpy/CSSETATY.cpy`       | Copybook        | Screen field attribute template (COPY REPLACING pattern)  |

### Migration Considerations

| ID   | Consideration                                                                                                                                    |
|------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| M-01 | **Screen conversion**: The BMS map defines 40+ input fields with individual attribute control (color, protection, cursor positioning). A web UI replacement must support equivalent field-level validation feedback and dynamic field protection (search mode vs. edit mode vs. confirmation mode). |
| M-02 | **Pseudo-conversational state**: The multi-state lifecycle (NOT-FETCHED -> SHOW-DETAILS -> OK-NOT-CONFIRMED -> DONE/FAILED) is stored in an extended communication area between interactions. A modern implementation needs equivalent session state management (e.g., server-side session, JWT token, or database-backed state). |
| M-03 | **Optimistic concurrency control**: The field-by-field comparison in paragraph `9700-CHECK-CHANGE-IN-REC` must be replaced with an equivalent mechanism such as row version numbers, ETags, or database timestamps. |
| M-04 | **Atomic multi-table update**: The two-file update with SYNCPOINT ROLLBACK maps directly to a database transaction. The modern implementation should wrap the account and customer table updates in a single transaction with rollback on failure. |
| M-05 | **Data store migration**: Three data stores are accessed. The card cross-reference (50-byte records) provides the account-to-customer linkage that would become a foreign key relationship in a relational database. Account (300-byte) and customer (500-byte) records map to two tables. |
| M-06 | **Validation logic**: Approximately 30 distinct validation rules are implemented inline. These should be extracted into a reusable validation framework or service layer rather than replicated in UI code. |
| M-07 | **Phone number validation**: Area code validation uses a lookup table of valid North American general-purpose area codes. This lookup data should be externalized into a configuration table or reference data service. |
| M-08 | **State-zip cross-validation**: The state-to-zip-prefix validation table is embedded in the lookup copybook. This should be replaced with an external validation service or reference data table. |
| M-09 | **Navigation**: Transfer control (XCTL) with COMMAREA-based routing between programs is menu-driven. A modern implementation would use URL-based routing or API endpoints with session context. |

---

## 10. Related Use Cases / End-to-End Composition

| Composition                            | Use Cases Involved                                                                                    | Description                                                                                          |
|----------------------------------------|-------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| Address Change Workflow                 | UC-CC00 (Sign-On) -> UC-CM00 (Main Menu) -> **UC-CAUP-001 (Account Update)**                        | Operator signs in, navigates to Account Update, updates address fields, confirms, and exits.         |
| Credit Limit Adjustment & Verification | UC-CC00 -> UC-CM00 -> **UC-CAUP-001** -> UC-CM00 -> UC-CAVW (Account View, *not yet created*)       | Operator increases credit limit via Account Update, then verifies via Account View.                  |
| Card List Drill-Down to Account Update | UC-CC00 -> UC-CM00 -> UC-CCLI (Card List) -> **UC-CAUP-001**                                        | Operator finds a card in the list, drills down to update the associated account.                     |
| Full Customer Profile Maintenance      | UC-CC00 -> UC-CM00 -> **UC-CAUP-001** (update demographics) -> UC-CM00 -> UC-CCUP (Card Update, *not yet created*) | Operator updates customer demographics via Account Update, then updates card details separately.      |

*Note: Use cases marked "not yet created" reference programs that exist in the CardDemo application but do not yet have their own Business Use Case Flow documents.*

---

## Document Footer

| Attribute                     | Value                                |
|-------------------------------|--------------------------------------|
| Source Program                | COACTUPC (4237 lines)                |
| Transaction ID                | CAUP                                 |
| BMS Map                       | COACTUP / CACTUPA (513 lines)       |
| Data Sources                  | CXACAIX, ACCTDAT, CUSTDAT           |
| Technical Flow Analysis       | `docs/COACTUPC_Flow_Analysis.md`    |
| Acceptance Criteria Count     | 29                                   |
| Business Rules Count          | 13                                   |
| Input Validation Rules Count  | 29                                   |
| Alternative / Error Flows     | 11                                   |
| Migration Considerations      | 9                                    |

---

*Document generated from source inspection of `choikh0423/aws-mainframe-modernization-carddemo`
on branch `demos/cobol-full-docs`.*

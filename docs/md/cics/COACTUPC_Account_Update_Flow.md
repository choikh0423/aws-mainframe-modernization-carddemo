# Account Update (CAUP) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CAUP |
| Entry Program | COACTUPC |
| Business Domain | Account Maintenance |
| Owning Team | CardDemo Application Team |
| Design Pattern | Pseudo-conversational |
| Criticality | High |
| Response Time SLA | < 2 seconds |
| Peak Volume | N/A (demo application) |

---

## Table of Contents

1. Business Flow Analysis
2. Technical Flow Analysis
3. Screen Flow and BMS Maps
4. Program Inventory and Call Chain
5. File and Data Store Inventory
6. Data Flow Analysis
7. COMMAREA and Interface Contracts
8. Error Handling and User Feedback
9. Security and Authorization
10. Performance Characteristics
11. Testing Approach
12. Known Issues and Considerations

---

## 1. Business Flow Analysis

### 1.1 Business Context

- **What**: Allows an operator to view and update account master data and associated customer data for a specific credit card account. The update covers financial parameters (credit limit, cash limit, balances), account dates (open, expiry, reissue), and full customer demographics (name, address, phone, SSN, FICO score).
- **Who**: Any authenticated CardDemo user (regular users and admins) — menu option 2 ("Account Update").
- **When**: Real-time, on-demand during business hours when account or customer information needs modification.
- **Why**: Supports maintenance of account parameters and customer contact information as part of ongoing account lifecycle management (e.g., credit limit changes, address updates, card reissue date changes).
- **What** is the business outcome: Both the ACCTDAT (account master) and CUSTDAT (customer master) VSAM files are updated in a single pseudo-transactional operation with optimistic concurrency checking.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  User selects Option 2 ("Account Update") from Main Menu
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 1            │  Enter 11-digit Account Number
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ System Response          │  Cross-reference lookup validates account exists,
  └────────────┬─────────────┘  reads Account Master + Customer Master,
               │                displays all fields populated with current values
               ▼
  ┌──────────────────────────┐
  │ User Action 2            │  Modify any editable fields:
  └────────────┬─────────────┘  Status, Credit Limit, Cash Limit, Dates,
               │                Balances, Customer Name, Address, Phone, etc.
               ▼
  ┌──────────────────────────┐
  │ System Validation        │  Compare old vs new; validate all changed fields
  └────────────┬─────────────┘  (Y/N, numeric, date, alpha, phone, SSN, FICO, etc.)
               │
           ┌───┴───┐
           │Decision│  Validation passes?
           └───┬───┘
              ╱ ╲
        Yes ╱     ╲ No
           ▼       ▼
  ┌──────────────┐  ┌─────────────────────┐
  │ Confirm Save │  │ Error message,      │
  │ (Press F5)   │  │ cursor to bad field │
  └──────┬───────┘  └─────────────────────┘
         │
     ┌───┴───┐
     │ F5?   │
     └───┬───┘
        ╱ ╲
  F5  ╱     ╲ Other
     ▼       ▼
┌────────────────────────┐  ┌──────────────────────────────────┐
│ Lock records (READ     │  │ "Changes validated. Press F5 to  │
│ UPDATE), check for     │  │  save" message persists          │
│ concurrent changes,    │  └──────────────────────────────────┘
│ REWRITE both files     │
└──────────┬─────────────┘
           ▼
  ┌──────────────────────────┐
  │ Business Outcome         │  "Changes committed to database"
  └──────────────────────────┘   Screen resets for next account lookup.
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | Account number required | Account Number field is blank or spaces | Error: "Account number not provided" |
| BR-002 | Account number must be numeric | Account Number is not numeric or is zero | Error: "Account Number if supplied must be a 11 digit Non-Zero Number" |
| BR-003 | Account must exist in cross-reference | Account not found in CXACAIX | Error with RESP code details |
| BR-004 | Account must exist in account master | Account not found in ACCTDAT | Error with RESP code details |
| BR-005 | Customer must exist in customer master | Customer not found in CUSTDAT | Error with RESP code details |
| BR-006 | Change detection required | No fields changed from original values | Info: "No change detected with respect to values fetched." |
| BR-007 | Active Status must be Y or N | Status field is not 'Y' or 'N' | Error: "Account Status must be Y or N." |
| BR-008 | Credit Limit must be valid signed number | TEST-NUMVAL-C fails | Error: "Credit Limit is not valid" |
| BR-009 | Cash Credit Limit must be valid signed number | TEST-NUMVAL-C fails | Error: "Cash Credit Limit is not valid" |
| BR-010 | Current Balance must be valid signed number | TEST-NUMVAL-C fails | Error: "Current Balance is not valid" |
| BR-011 | Dates must be valid calendar dates | Date component fails EDIT-DATE-CCYYMMDD | Error: specific date field error |
| BR-012 | First Name required, alpha only | Blank or contains non-alpha | Error: "First Name must be supplied." / "First Name can have alphabets only." |
| BR-013 | Last Name required, alpha only | Blank or contains non-alpha | Error: "Last Name must be supplied." / "Last Name can have alphabets only." |
| BR-014 | Address Line 1 mandatory | Blank | Error: "Address Line 1 must be supplied." |
| BR-015 | State required, alpha only, valid code | Blank/non-alpha/invalid US state code | Error: "State: is not a valid state code" |
| BR-016 | Zip code required, numeric, non-zero | Blank or non-numeric | Error: "Zip must be all numeric." |
| BR-017 | State/Zip cross-validation | State and Zip first 2 digits don't match USPS mapping | Error: "Invalid zip code for state" |
| BR-018 | Phone numbers validated | Area code not valid North America code | Error: "Phone Number 1: Not valid North America general purpose area code" |
| BR-019 | SSN validation | Parts are blank, non-numeric, or contain invalid values (000, 666, 900-999) | Error: specific SSN part error |
| BR-020 | FICO Score must be 300-850 | Numeric value outside range | Error: "FICO Score: should be between 300 and 850" |
| BR-021 | Primary Card Holder must be Y/N | Not Y or N | Error: "Primary Card Holder must be Y or N." |
| BR-022 | Optimistic concurrency check | Record changed by another user between read and update | Error: "Record changed by some one else. Please review" |
| BR-023 | Confirmation via F5 | Changes validated, user presses F5 | Proceed with file update |
| BR-024 | Cancel via F12 | User presses F12 while editing | Reload original values from file |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Select Option 2 from Main Menu | XCTL to COACTUPC; display empty Account Update screen (CACTUPA) with cursor on Account Number | Navigate to account update |
| 2 | Enter 11-digit Account Number, press ENTER | Cross-reference lookup → Account read → Customer read; populate all fields with current values | Retrieve account for editing |
| 3 | Modify desired fields, press ENTER | Validate all changes; if valid, display "Changes validated. Press F5 to save"; if invalid, error message and cursor to first bad field | Submit changes for validation |
| 4a | Press F5 (confirmed) | Lock records, check for concurrent changes, REWRITE both ACCTDAT and CUSTDAT, display "Changes committed to database", reset screen | Commit changes |
| 4b | Press ENTER without F5 | "Changes validated. Press F5 to save" remains | User reviews before saving |
| Alt-A | Press PF3 | XCTL back to previous screen (or Main Menu COMEN01C) | Exit without saving |
| Alt-B | Press PF12 (while details shown) | Reload original values from database, discard edits | Cancel pending changes |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CAUP
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COACTUPC     │ (Entry & only online program)      │
  │  │ (Acct Update)│                                    │
  │  └──┬────┬──┬───┘                                    │
  │     │    │  │                                         │
  │     ▼    │  ▼                                         │
  │  ┌──────┐│ ┌────────────────────────────────────┐    │
  │  │BMS   ││ │ VSAM Files                         │    │
  │  │MAP:  ││ │  CXACAIX (Acct→Card XREF AIX)     │    │
  │  │CACTU ││ │  ACCTDAT (Account Master)          │    │
  │  │PA    ││ │  CUSTDAT (Customer Master)         │    │
  │  └──────┘│ └────────────────────────────────────┘    │
  │          │                                            │
  │          ▼                                            │
  │  ┌─────────────┐                                     │
  │  │ CSUTLDPY    │ (Date validation utility - COPY)    │
  │  └─────────────┘                                     │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1 (First entry — from Main Menu via XCTL):
  1. COMEN01C XCTLs to COACTUPC with CARDDEMO-COMMAREA
  2. EIBCALEN = 0 or (FROM-PROGRAM = COMEN01C and NOT PGM-REENTER)
  3. INITIALIZE CARDDEMO-COMMAREA, WS-THIS-PROGCOMMAREA
  4. SET CDEMO-PGM-ENTER, ACUP-DETAILS-NOT-FETCHED TO TRUE
  5. PERFORM YYYY-STORE-PFKEY (map AID to internal flag)
  6. EVALUATE → ACUP-DETAILS-NOT-FETCHED AND CDEMO-PGM-ENTER
  7. PERFORM 3000-SEND-MAP (empty screen with cursor on ACCTSID)
  8. SET CDEMO-PGM-REENTER, ACUP-DETAILS-NOT-FETCHED TO TRUE
  9. COMMON-RETURN: RETURN TRANSID('CAUP') COMMAREA(WS-COMMAREA)

  Iteration 2 (User enters Account Number — ENTER pressed):
  10. CICS dispatches → COACTUPC (COMMAREA restored from DFHCOMMAREA)
  11. EIBCALEN > 0; CDEMO-PGM-REENTER = 1
  12. EVALUATE → WHEN OTHER
  13. PERFORM 1000-PROCESS-INPUTS
      ├── 1100-RECEIVE-MAP: RECEIVE MAP('CACTUPA') INTO(CACTUPAI)
      │   └── Move ACCTSIDI to CC-ACCT-ID, ACUP-NEW-ACCT-ID-X
      │       (Details not fetched yet → skip remaining field mapping)
      └── 1200-EDIT-MAP-INPUTS: ACUP-DETAILS-NOT-FETCHED = TRUE
          ├── 1210-EDIT-ACCOUNT: validate numeric, non-zero, 11-digit
          └── If blank → "No input received"
  14. PERFORM 2000-DECIDE-ACTION
      ├── WHEN ACUP-DETAILS-NOT-FETCHED (or CCARD-AID-PFK12):
      │   └── PERFORM 9000-READ-ACCT
      │       ├── 9200-GETCARDXREF-BYACCT: READ CXACAIX by account
      │       ├── 9300-GETACCTDATA-BYACCT: READ ACCTDAT by account
      │       ├── 9400-GETCUSTDATA-BYCUST: READ CUSTDAT by customer
      │       └── 9500-STORE-FETCHED-DATA: copy to ACUP-OLD-DETAILS
      └── SET ACUP-SHOW-DETAILS TO TRUE
  15. PERFORM 3000-SEND-MAP (display populated fields)
  16. COMMON-RETURN: RETURN TRANSID('CAUP') COMMAREA(WS-COMMAREA)

  Iteration 3 (User modifies fields — ENTER pressed):
  17. RECEIVE MAP → map ALL input fields to ACUP-NEW-DETAILS
  18. 1200-EDIT-MAP-INPUTS: details already fetched
      ├── 1205-COMPARE-OLD-NEW: compare every field old vs new
      │   ├── No changes → SET NO-CHANGES-DETECTED (info message)
      │   └── Changes found → validate each field:
      │       ├── 1220-EDIT-YESNO (Active Status)
      │       ├── EDIT-DATE-CCYYMMDD (Open, Expiry, Reissue dates)
      │       ├── 1250-EDIT-SIGNED-9V2 (Credit Limit, Cash Limit, Balance, Cycle Credit/Debit)
      │       ├── 1265-EDIT-US-SSN (SSN parts)
      │       ├── EDIT-DATE-OF-BIRTH (DOB validation)
      │       ├── 1245-EDIT-NUM-REQD (FICO, Zip, EFT Account)
      │       ├── 1225-EDIT-ALPHA-REQD (First Name, Last Name, State, City, Country)
      │       ├── 1235-EDIT-ALPHA-OPT (Middle Name)
      │       ├── 1215-EDIT-MANDATORY (Address Line 1)
      │       ├── 1260-EDIT-US-PHONE-NUM (Phone 1, Phone 2)
      │       ├── 1270-EDIT-US-STATE-CD (valid state code)
      │       ├── 1275-EDIT-FICO-SCORE (300-850 range)
      │       ├── 1280-EDIT-US-STATE-ZIP-CD (state/zip cross-validation)
      │       └── 1220-EDIT-YESNO (Primary Card Holder)
      └── All valid → SET ACUP-CHANGES-OK-NOT-CONFIRMED
  19. 2000-DECIDE-ACTION: WHEN ACUP-SHOW-DETAILS → if no error, confirm
  20. 3000-SEND-MAP with "Changes validated. Press F5 to save"
  21. COMMON-RETURN

  Iteration 4 (User presses F5 to confirm):
  22. RECEIVE MAP (same changes)
  23. 1200-EDIT-MAP-INPUTS: ACUP-CHANGES-OK-NOT-CONFIRMED → skip validation
  24. 2000-DECIDE-ACTION: WHEN ACUP-CHANGES-OK-NOT-CONFIRMED AND PFK05
      └── PERFORM 9600-WRITE-PROCESSING
          ├── READ ACCTDAT UPDATE (lock account record)
          ├── READ CUSTDAT UPDATE (lock customer record)
          ├── 9700-CHECK-CHANGE-IN-REC (optimistic concurrency check)
          ├── Prepare ACCT-UPDATE-RECORD from ACUP-NEW fields
          ├── Prepare CUST-UPDATE-RECORD from ACUP-NEW fields
          ├── REWRITE ACCTDAT
          ├── REWRITE CUSTDAT
          └── If CUSTDAT fails → SYNCPOINT ROLLBACK
  25. SET ACUP-CHANGES-OKAYED-AND-DONE TO TRUE
  26. 3000-SEND-MAP with "Changes committed to database"
  27. COMMON-RETURN
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | 0 or >0 (from menu) | XCTL from menu (option 2) | Initial logic (PGM-ENTER) | CACTUPA (empty — account# only) | CAUP |
| 2 | >0 | Enter account number, press ENTER | 1000-PROCESS-INPUTS → 2000-DECIDE-ACTION → 9000-READ-ACCT | CACTUPA (populated with data) | CAUP |
| 3 | >0 | Modify fields, press ENTER | 1000 → 1200 (validate) → 2000 | CACTUPA (confirm prompt or error) | CAUP |
| 4 | >0 | Press F5 (confirm save) | 1000 → 2000 → 9600-WRITE-PROCESSING | CACTUPA (success, reset) | CAUP |
| N | >0 | PF3 (Exit) | XCTL to CDEMO-FROM-PROGRAM or COMEN01C | — | — (XCTL) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | HANDLE ABEND LABEL(ABEND-ROUTINE) | Set up abend handler | — |
| 2 | RECEIVE MAP('CACTUPA') MAPSET('COACTUP') INTO(CACTUPAI) | Capture user input from terminal | RESP/RESP2 stored |
| 3 | READ DATASET('CXACAIX') RIDFLD(WS-CARD-RID-ACCT-ID-X) | Lookup card cross-reference by account | NORMAL → continue; NOTFND → error; OTHER → file error |
| 4 | READ DATASET('ACCTDAT') RIDFLD(WS-CARD-RID-ACCT-ID-X) | Read account master record | NORMAL → continue; NOTFND → error; OTHER → file error |
| 5 | READ DATASET('CUSTDAT') RIDFLD(WS-CARD-RID-CUST-ID-X) | Read customer master record | NORMAL → continue; NOTFND → error; OTHER → file error |
| 6 | READ FILE('ACCTDAT') UPDATE RIDFLD | Lock account record for update | NORMAL → continue; OTHER → "Could not lock account record" |
| 7 | READ FILE('CUSTDAT') UPDATE RIDFLD | Lock customer record for update | NORMAL → continue; OTHER → "Could not lock customer record" |
| 8 | REWRITE FILE('ACCTDAT') FROM(ACCT-UPDATE-RECORD) | Update account master | NORMAL → continue; OTHER → "Update of record failed" |
| 9 | REWRITE FILE('CUSTDAT') FROM(CUST-UPDATE-RECORD) | Update customer master | NORMAL → continue; OTHER → SYNCPOINT ROLLBACK + "Update of record failed" |
| 10 | SYNCPOINT | Commit before XCTL on PF3 | — |
| 11 | SYNCPOINT ROLLBACK | Rollback on customer update failure | — |
| 12 | SEND MAP('CACTUPA') MAPSET('COACTUP') CURSOR ERASE FREEKB | Send screen to terminal | — |
| 13 | RETURN TRANSID('CAUP') COMMAREA(WS-COMMAREA) LENGTH(2000) | Pseudo-conversational return | — |
| 14 | XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA) | Transfer control on PF3 exit | — |
| 15 | SEND FROM(ABEND-DATA) ERASE | Send abend message on unexpected error | — |
| 16 | ABEND ABCODE('9999') | Force abend after sending error data | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────┐                ┌─────────────────────────────────────────────┐
  │  Main Menu  │   Option 2     │  CACTUPA (Account Update)                   │
  │  (COMEN01C) │───────────────▶│                                             │
  └─────────────┘                │  Account Number: ___________  Active Y/N: _ │
        ▲                        │  Opened: ____-__-__   Credit Limit: _______ │
        │ PF3                    │  Expiry: ____-__-__   Cash Limit:   _______ │
        │                        │  Reissue:____-__-__   Current Balance:_____ │
        │                        │  Account Group:_____   Cycle Credit: ______ │
        │                        │                        Cycle Debit:  ______ │
        │                        │  ──── Customer Details ────                 │
        │                        │  Customer ID: _________  SSN: ___-__-____   │
        │                        │  DOB: ____-__-__        FICO Score: ___     │
        │                        │  First Name / Middle Name / Last Name       │
        │                        │  Address: ________ State: __ Zip: _____     │
        │                        │  City: __________ Country: ___              │
        │                        │  Phone 1: ___-___-____  Govt ID: _________ │
        │                        │  Phone 2: ___-___-____  EFT: _____ PriHld:_│
        │                        │  [Info Message]                             │
        │                        │  [Error Message]                            │
        │                        │  ENTER=Process F3=Exit  F5=Save  F12=Cancel │
        └────────────────────────┤                                             │
                                 └─────────────────────────────────────────────┘
                                   │         │         │
                                   │ ENTER   │ F5      │ F12
                                   │(loops)  │(save)   │(reload)
                                   ▼         ▼         ▼
                                 [Same screen — CACTUPA]
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| CACTUPA | COACTUP | Account Update input/display screen | Account#, Status, Credit Limit, Cash Limit, Balance, Cycle Credit/Debit, Open/Expiry/Reissue dates, Account Group, Customer ID, SSN, DOB, FICO, Names, Address, Phones, Govt ID, EFT, Primary Holder | ENTER=Process, PF3=Exit, PF5=Save, PF12=Cancel |

### 3.3 Screen Field Details

#### Map: CACTUPA

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction ID (CAUP) |
| TITLE01 | ASKIP,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | ASKIP,NORM | 8 | Alpha | — | Program name (COACTUPC) |
| TITLE02 | ASKIP,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | ASKIP,NORM | 8 | Time | — | Current time hh:mm:ss |
| ACCTSID | IC,UNPROT | 11 | Numeric | Required, numeric, non-zero, 11 digits | Account Number (search key) |
| ACSTTUS | UNPROT | 1 | Alpha | Must be 'Y' or 'N' | Account Active Status |
| OPNYEAR | FSET,UNPROT | 4 | Numeric | Valid year (CCYY) | Account Open Date — Year |
| OPNMON | UNPROT | 2 | Numeric | Valid month (01-12) | Account Open Date — Month |
| OPNDAY | UNPROT | 2 | Numeric | Valid day (01-31) | Account Open Date — Day |
| ACRDLIM | FSET,UNPROT | 15 | Signed Decimal | Required, valid signed number | Credit Limit |
| EXPYEAR | UNPROT | 4 | Numeric | Valid year | Account Expiry Date — Year |
| EXPMON | UNPROT | 2 | Numeric | Valid month | Account Expiry Date — Month |
| EXPDAY | UNPROT | 2 | Numeric | Valid day | Account Expiry Date — Day |
| ACSHLIM | FSET,UNPROT | 15 | Signed Decimal | Required, valid signed number | Cash Credit Limit |
| RISYEAR | UNPROT | 4 | Numeric | Valid year | Card Reissue Date — Year |
| RISMON | UNPROT | 2 | Numeric | Valid month | Card Reissue Date — Month |
| RISDAY | UNPROT | 2 | Numeric | Valid day | Card Reissue Date — Day |
| ACURBAL | FSET,UNPROT | 15 | Signed Decimal | Required, valid signed number | Current Balance |
| ACRCYCR | FSET,UNPROT | 15 | Signed Decimal | Required, valid signed number | Current Cycle Credit |
| AADDGRP | UNPROT | 10 | AlphaNum | — | Account Group ID |
| ACRCYDB | FSET,UNPROT | 15 | Signed Decimal | Required, valid signed number | Current Cycle Debit |
| ACSTNUM | UNPROT | 9 | Numeric | Display only (protected in edit mode) | Customer ID |
| ACTSSN1 | UNPROT | 3 | Numeric | Required, numeric, not 000/666/900-999 | SSN Part 1 (Area) |
| ACTSSN2 | UNPROT | 2 | Numeric | Required, numeric, not 00 | SSN Part 2 (Group) |
| ACTSSN3 | UNPROT | 4 | Numeric | Required, numeric, not 0000 | SSN Part 3 (Serial) |
| DOBYEAR | UNPROT | 4 | Numeric | Valid year, valid DOB date | Date of Birth — Year |
| DOBMON | UNPROT | 2 | Numeric | Valid month | Date of Birth — Month |
| DOBDAY | UNPROT | 2 | Numeric | Valid day | Date of Birth — Day |
| ACSTFCO | UNPROT | 3 | Numeric | Required, numeric, 300-850 | FICO Credit Score |
| ACSFNAM | UNPROT | 25 | Alpha | Required, alphabets + spaces only | Customer First Name |
| ACSMNAM | UNPROT | 25 | Alpha | Optional, alphabets + spaces only | Customer Middle Name |
| ACSLNAM | UNPROT | 25 | Alpha | Required, alphabets + spaces only | Customer Last Name |
| ACSADL1 | UNPROT | 50 | AlphaNum | Required (mandatory) | Address Line 1 |
| ACSSTTE | UNPROT | 2 | Alpha | Required, valid US state code | State Code |
| ACSADL2 | UNPROT | 50 | AlphaNum | Optional (no validation) | Address Line 2 |
| ACSZIPC | UNPROT | 5 | Numeric | Required, numeric, non-zero, state/zip match | Zip Code |
| ACSCITY | UNPROT | 50 | Alpha | Required, alphabets + spaces only | City |
| ACSCTRY | UNPROT | 3 | Alpha | Protected (country locked to current) | Country Code |
| ACSPH1A | UNPROT | 3 | Numeric | Valid NA area code (if supplied) | Phone 1 — Area Code |
| ACSPH1B | UNPROT | 3 | Numeric | Required if area code present, numeric | Phone 1 — Prefix |
| ACSPH1C | UNPROT | 4 | Numeric | Required if prefix present, numeric | Phone 1 — Line Number |
| ACSGOVT | UNPROT | 20 | AlphaNum | — | Government Issued ID Reference |
| ACSPH2A | UNPROT | 3 | Numeric | Valid NA area code (if supplied) | Phone 2 — Area Code |
| ACSPH2B | UNPROT | 3 | Numeric | Required if area code present, numeric | Phone 2 — Prefix |
| ACSPH2C | UNPROT | 4 | Numeric | Required if prefix present, numeric | Phone 2 — Line Number |
| ACSEFTC | UNPROT | 10 | Numeric | Required, numeric, non-zero | EFT Account ID |
| ACSPFLG | UNPROT | 1 | Alpha | Must be 'Y' or 'N' | Primary Card Holder Flag |
| INFOMSG | ASKIP | 45 | Alpha | — | Information/status message |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/warning message (red) |
| FKEYS | ASKIP,NORM | 21 | Alpha | — | "ENTER=Process F3=Exit" |
| FKEY05 | ASKIP,DRK | 7 | Alpha | — | "F5=Save" (shown when confirm needed) |
| FKEY12 | ASKIP,DRK | 10 | Alpha | — | "F12=Cancel" (shown when changes made) |

### 3.4 PF Key / AID Key Handling

| AID Key | Context | Action |
|---------|---------|--------|
| ENTER | Any state | Process inputs: validate account search or validate field changes |
| PF3 | Any state | SYNCPOINT then XCTL to previous program (CDEMO-FROM-PROGRAM) or Main Menu (COMEN01C) |
| PF5 | ACUP-CHANGES-OK-NOT-CONFIRMED only | Confirm and save: lock records, check concurrency, REWRITE both files |
| PF12 | Details shown (not initial) | Cancel changes: re-read account and customer data from files, show original values |
| OTHER | Any state | Treated as ENTER (PFK-INVALID → SET CCARD-AID-ENTER) |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COACTUPC | Account Update — screen handler, validation, VSAM read/rewrite | TRANSID CAUP / XCTL from COMEN01C | 4236 | `app/cbl/COACTUPC.cbl` |
| COMEN01C | Main Menu — entry point for option 2 | XCTL (caller) | — | `app/cbl/COMEN01C.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CAUP
└── COACTUPC (Entry — Screen Handler, Validation, Data Access)
    ├── COPY CSUTLDPY — Date validation utility (inline)
    ├── COPY CSLKPCDY — North America phone area code lookup table
    ├── READ FILE('CXACAIX') — Account-to-Card cross-reference lookup
    ├── READ FILE('ACCTDAT') — Account master read
    ├── READ FILE('CUSTDAT') — Customer master read
    ├── READ FILE('ACCTDAT') UPDATE — Lock account for update
    ├── READ FILE('CUSTDAT') UPDATE — Lock customer for update
    ├── REWRITE FILE('ACCTDAT') — Update account master
    ├── REWRITE FILE('CUSTDAT') — Update customer master
    ├── SYNCPOINT / SYNCPOINT ROLLBACK — Transaction control
    ├── SEND MAP('CACTUPA') — Display screen
    ├── RECEIVE MAP('CACTUPA') — Capture input
    ├── RETURN TRANSID('CAUP') — Pseudo-conversational return
    └── XCTL → COMEN01C or CDEMO-FROM-PROGRAM (on PF3 exit)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COMEN01C | COACTUPC | XCTL | CARDDEMO-COMMAREA | — | User selects menu option 2 |
| COACTUPC | COMEN01C | XCTL | CARDDEMO-COMMAREA | — | PF3 exit back to main menu |
| COACTUPC | CSUTLDPY | COPY (inline) | WS-EDIT-DATE-CCYYMMDD | WS-EDIT-DATE-FLGS | Date validation of Open/Expiry/Reissue/DOB dates |
| COACTUPC | CSLKPCDY | COPY (inline) | WS-US-PHONE-AREA-CODE-TO-EDIT | 88-level VALID-GENERAL-PURP-CODE | Phone area code validation |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| Card Cross-Reference (AIX) | CXACAIX | VSAM KSDS (Alternate Index) | READ | COACTUPC | Lookup card/customer by account ID |
| Account Master | ACCTDAT | VSAM KSDS | READ / READ UPDATE / REWRITE | COACTUPC | Read and update account data |
| Customer Master | CUSTDAT | VSAM KSDS | READ / READ UPDATE / REWRITE | COACTUPC | Read and update customer data |

### 5.2 File Access Details

#### File: CXACAIX (Account-to-Card Cross-Reference AIX)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS Alternate Index |
| Copybook | CVACT03Y (CARD-XREF-RECORD) |
| Record Length | 50 bytes |
| Key | XREF-ACCT-ID (WS-CARD-RID-ACCT-ID-X), 11 bytes |
| Access | READ only |
| Locking | None |
| Shared With | COACTVWC, COCRDLIC, COTRN02C |

#### File: ACCTDAT (Account Master)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Copybook | CVACT01Y (ACCOUNT-RECORD) |
| Record Length | 300 bytes |
| Key | ACCT-ID (WS-CARD-RID-ACCT-ID-X), 11 bytes |
| Access | READ (browse), READ UPDATE (for lock), REWRITE |
| Locking | Exclusive (READ UPDATE) during write processing |
| Shared With | COACTVWC, batch programs |

#### File: CUSTDAT (Customer Master)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Copybook | CVCUS01Y (CUSTOMER-RECORD) |
| Record Length | 500 bytes |
| Key | CUST-ID (WS-CARD-RID-CUST-ID-X), 9 bytes |
| Access | READ (browse), READ UPDATE (for lock), REWRITE |
| Locking | Exclusive (READ UPDATE) during write processing |
| Shared With | COUSR00C, COUSR01C, COUSR02C, batch programs |

### 5.3 File Access Patterns

#### Initial Data Retrieval (9000-READ-ACCT)

| Step | File | Operation | Key | Success Action | Failure Action |
|------|------|-----------|-----|----------------|----------------|
| 1 | CXACAIX | READ | Account ID (11 bytes) | Extract CUST-ID, CARD-NUM from XREF | Error message with RESP/RESP2 |
| 2 | ACCTDAT | READ | Account ID (11 bytes) | Set FOUND-ACCT-IN-MASTER | Error message with RESP/RESP2 |
| 3 | CUSTDAT | READ | Customer ID (9 bytes) | Set FOUND-CUST-IN-MASTER | Error message with RESP/RESP2 |

#### Update Processing (9600-WRITE-PROCESSING)

| Step | File | Operation | Key | Success Action | Failure Action |
|------|------|-----------|-----|----------------|----------------|
| 1 | ACCTDAT | READ UPDATE | Account ID (11 bytes) | Lock acquired | "Could not lock account record for update" |
| 2 | CUSTDAT | READ UPDATE | Customer ID (9 bytes) | Lock acquired | "Could not lock customer record for update" |
| 3 | — | 9700-CHECK-CHANGE-IN-REC | — | Data unchanged → proceed | "Record changed by some one else. Please review" |
| 4 | ACCTDAT | REWRITE | — (locked record) | Account updated | "Update of record failed" |
| 5 | CUSTDAT | REWRITE | — (locked record) | Customer updated | SYNCPOINT ROLLBACK + "Update of record failed" |

### 5.4 TSQ/TDQ Usage

None. This transaction does not use Temporary Storage Queues or Transient Data Queues.

---

## 6. Data Flow Analysis

### 6.1 Data Flow Diagram

```
  ┌───────────────────┐          ┌────────────────────────┐
  │  CXACAIX          │          │  Screen (CACTUPAI)     │
  │  (XREF Record)    │          │                        │
  │  XREF-CARD-NUM    │─────┐    │  ACCTSIDI ────────────▶│ CC-ACCT-ID
  │  XREF-CUST-ID     │     │    │  ACSTTUSI ────────────▶│ ACUP-NEW-ACTIVE-STATUS
  │  XREF-ACCT-ID     │     │    │  ACRDLIMI ────────────▶│ ACUP-NEW-CREDIT-LIMIT-X
  └───────────────────┘     │    │  ACSHLIMI ────────────▶│ ACUP-NEW-CASH-CREDIT-LIMIT-X
                            │    │  ACURBALI ────────────▶│ ACUP-NEW-CURR-BAL-X
  ┌───────────────────┐     │    │  ACRCYCRI ────────────▶│ ACUP-NEW-CURR-CYC-CREDIT-X
  │  ACCTDAT          │     │    │  ACRCYDBI ────────────▶│ ACUP-NEW-CURR-CYC-DEBIT-X
  │  (ACCOUNT-RECORD) │     │    │  OPNYEARx ────────────▶│ ACUP-NEW-OPEN-DATE
  │  ACCT-ID          │─────┤    │  EXPYEARx ────────────▶│ ACUP-NEW-EXPIRAION-DATE
  │  ACCT-*           │     │    │  RISYEARx ────────────▶│ ACUP-NEW-REISSUE-DATE
  └───────────────────┘     │    │  AADDGRPI ────────────▶│ ACUP-NEW-GROUP-ID
                            │    │  ACSxxxxx ────────────▶│ ACUP-NEW-CUST-*
  ┌───────────────────┐     │    └────────────────────────┘
  │  CUSTDAT          │     │
  │  (CUSTOMER-RECORD)│     │              │
  │  CUST-ID          │─────┤              │ (Validated)
  │  CUST-*           │     │              ▼
  └───────────────────┘     │    ┌────────────────────────┐
                            │    │  ACCT-UPDATE-RECORD    │──▶ REWRITE ACCTDAT
                            │    │  CUST-UPDATE-RECORD    │──▶ REWRITE CUSTDAT
                            │    └────────────────────────┘
                            │
                            ▼
                   ┌────────────────────────┐
                   │  ACUP-OLD-DETAILS      │  (stored in COMMAREA extension)
                   │  (snapshot for compare │
                   │   and screen display)  │
                   └────────────────────────┘
```

### 6.2 Transformation Map

| Source Field | Target Field | Transformation |
|-------------|-------------|----------------|
| ACCTSIDI (screen) | CC-ACCT-ID / ACUP-NEW-ACCT-ID-X | Direct move |
| ACRDLIMI (screen) | ACUP-NEW-CREDIT-LIMIT-N | NUMVAL-C function (currency string → numeric) |
| ACSHLIMI (screen) | ACUP-NEW-CASH-CREDIT-LIMIT-N | NUMVAL-C function |
| ACURBALI (screen) | ACUP-NEW-CURR-BAL-N | NUMVAL-C function |
| ACRCYCRI (screen) | ACUP-NEW-CURR-CYC-CREDIT-N | NUMVAL-C function |
| ACRCYDBI (screen) | ACUP-NEW-CURR-CYC-DEBIT-N | NUMVAL-C function |
| OPNYEARI/OPNMONI/OPNDAYI | ACUP-NEW-OPEN-DATE (CCYYMMDD) | Concatenation of year+month+day |
| ACUP-NEW-OPEN-DATE | ACCT-UPDATE-OPEN-DATE | STRING year '-' month '-' day → CCYY-MM-DD |
| ACSPH1AI/ACSPH1BI/ACSPH1CI | CUST-UPDATE-PHONE-NUM-1 | STRING '(' area ')' prefix '-' line |
| ACUP-OLD-CURR-BAL-N | ACURBALO (screen) | Format via PIC +ZZZ,ZZZ,ZZZ.99 |
| ACCT-OPEN-DATE (file, CCYY-MM-DD) | ACUP-OLD-OPEN-YEAR/MON/DAY | Substring extraction (1:4), (6:2), (9:2) |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The program uses a two-part COMMAREA stored in WS-COMMAREA (PIC X(2000)):

#### Part 1: CARDDEMO-COMMAREA (Shared — COCOM01Y copybook)

| Offset | Field | PIC | Purpose |
|--------|-------|-----|---------|
| 1-4 | CDEMO-FROM-TRANID | X(04) | Calling transaction ID |
| 5-12 | CDEMO-FROM-PROGRAM | X(08) | Calling program name |
| 13-16 | CDEMO-TO-TRANID | X(04) | Target transaction ID |
| 17-24 | CDEMO-TO-PROGRAM | X(08) | Target program name |
| 25-32 | CDEMO-USER-ID | X(08) | Signed-on user ID |
| 33 | CDEMO-USER-TYPE | X(01) | 'A'=Admin, 'U'=User |
| 34 | CDEMO-PGM-CONTEXT | 9(01) | 0=Enter, 1=Reenter |
| 35-43 | CDEMO-CUST-ID | 9(09) | Current customer ID |
| 44-68 | CDEMO-CUST-FNAME | X(25) | Customer first name |
| 69-93 | CDEMO-CUST-MNAME | X(25) | Customer middle name |
| 94-118 | CDEMO-CUST-LNAME | X(25) | Customer last name |
| 119-129 | CDEMO-ACCT-ID | 9(11) | Current account ID |
| 130 | CDEMO-ACCT-STATUS | X(01) | Account status |
| 131-146 | CDEMO-CARD-NUM | 9(16) | Card number |
| 147-153 | CDEMO-LAST-MAP | X(7) | Last map sent |
| 154-160 | CDEMO-LAST-MAPSET | X(7) | Last mapset sent |

#### Part 2: WS-THIS-PROGCOMMAREA (Program-specific extension)

| Section | Field | Purpose |
|---------|-------|---------|
| ACUP-CHANGE-ACTION | PIC X(1) | State flag: spaces/LOW-VALUES=not fetched, 'S'=show, 'E'=errors, 'N'=not confirmed, 'C'=done, 'L'=lock error, 'F'=failed |
| ACUP-OLD-ACCT-DATA | ~120 bytes | Snapshot of account data at read time (for concurrency check + screen display) |
| ACUP-OLD-CUST-DATA | ~280 bytes | Snapshot of customer data at read time |
| ACUP-NEW-ACCT-DATA | ~120 bytes | User's modified account data (from screen) |
| ACUP-NEW-CUST-DATA | ~280 bytes | User's modified customer data (from screen) |

### 7.2 State Management

| State Value | Meaning | Triggered By | Next Valid States |
|-------------|---------|--------------|-------------------|
| ACUP-DETAILS-NOT-FETCHED | Initial / search mode | First entry, after successful update | ACUP-SHOW-DETAILS |
| ACUP-SHOW-DETAILS ('S') | Data displayed for editing | Successful read from files | ACUP-CHANGES-NOT-OK, ACUP-CHANGES-OK-NOT-CONFIRMED |
| ACUP-CHANGES-NOT-OK ('E') | Validation errors found | Failed validation | ACUP-SHOW-DETAILS, ACUP-CHANGES-OK-NOT-CONFIRMED |
| ACUP-CHANGES-OK-NOT-CONFIRMED ('N') | Changes valid, awaiting F5 | Successful validation | ACUP-CHANGES-OKAYED-AND-DONE, ACUP-CHANGES-OKAYED-LOCK-ERROR, ACUP-CHANGES-OKAYED-BUT-FAILED |
| ACUP-CHANGES-OKAYED-AND-DONE ('C') | Update successful | Successful REWRITE of both files | ACUP-DETAILS-NOT-FETCHED (reset) |
| ACUP-CHANGES-OKAYED-LOCK-ERROR ('L') | Could not lock record | READ UPDATE failed | ACUP-DETAILS-NOT-FETCHED (reset) |
| ACUP-CHANGES-OKAYED-BUT-FAILED ('F') | Update REWRITE failed | REWRITE returned error | ACUP-DETAILS-NOT-FETCHED (reset) |

### 7.3 Return Codes

No explicit return codes are passed. Success/failure is communicated via:
- WS-INFO-MSG: Informational messages displayed in INFOMSG field
- WS-RETURN-MSG: Error messages displayed in ERRMSG field
- ACUP-CHANGE-ACTION state flag preserved across pseudo-conversational iterations

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  ┌─────────────────────┐
  │ User Input Received │
  └──────────┬──────────┘
             │
             ▼
  ┌─────────────────────────────────────────────┐
  │ Validation Layer (1200-EDIT-MAP-INPUTS)      │
  │  ├── Account Number validation               │
  │  ├── Y/N field validation                    │
  │  ├── Signed number validation                │
  │  ├── Date validation (CCYYMMDD)              │
  │  ├── Alpha field validation                  │
  │  ├── Numeric field validation                │
  │  ├── Phone number validation                 │
  │  ├── SSN validation                          │
  │  ├── FICO score range validation             │
  │  ├── State code validation                   │
  │  └── State/Zip cross-validation              │
  └──────────┬──────────────────────────────────┘
             │
         ┌───┴───┐
         │Valid? │
         └───┬───┘
            ╱ ╲
      Yes ╱     ╲ No
         ▼       ▼
  ┌──────────┐  ┌────────────────────────────────┐
  │ Proceed  │  │ SET INPUT-ERROR TO TRUE        │
  │ to save  │  │ First error → WS-RETURN-MSG    │
  │ confirm  │  │ Position cursor to bad field   │
  └──────────┘  │ Highlight field in RED         │
                │ Show '*' for blank fields      │
                └────────────────────────────────┘
```

### 8.2 Error Handling Matrix

| Error Condition | RESP Code | Paragraph | Action | User Message |
|-----------------|-----------|-----------|--------|-------------|
| Account not in CXACAIX | NOTFND | 9200-GETCARDXREF-BYACCT | SET INPUT-ERROR, FLG-ACCTFILTER-NOT-OK | "Account:XXXXXXXXXXX not found in Cross ref file. Resp:XX Reas:XX" |
| CXACAIX file error | OTHER | 9200-GETCARDXREF-BYACCT | SET INPUT-ERROR | "File Error: READ on CXACAIX returned RESP XX,RESP2 XX" |
| Account not in ACCTDAT | NOTFND | 9300-GETACCTDATA-BYACCT | SET INPUT-ERROR, FLG-ACCTFILTER-NOT-OK | "Account:XXXXXXXXXXX not found in Acct Master file.Resp:XX Reas:XX" |
| ACCTDAT file error | OTHER | 9300-GETACCTDATA-BYACCT | SET INPUT-ERROR | "File Error: READ on ACCTDAT returned RESP XX,RESP2 XX" |
| Customer not in CUSTDAT | NOTFND | 9400-GETCUSTDATA-BYCUST | SET INPUT-ERROR, FLG-CUSTFILTER-NOT-OK | "CustId:XXXXXXXXX not found in customer master.Resp: XX REAS:XX" |
| CUSTDAT file error | OTHER | 9400-GETCUSTDATA-BYCUST | SET INPUT-ERROR | "File Error: READ on CUSTDAT returned RESP XX,RESP2 XX" |
| Cannot lock account | OTHER | 9600-WRITE-PROCESSING | SET INPUT-ERROR | "Could not lock account record for update" |
| Cannot lock customer | OTHER | 9600-WRITE-PROCESSING | SET INPUT-ERROR | "Could not lock customer record for update" |
| Data changed before update | — | 9700-CHECK-CHANGE-IN-REC | SET DATA-WAS-CHANGED-BEFORE-UPDATE | "Record changed by some one else. Please review" |
| Account REWRITE failed | OTHER | 9600-WRITE-PROCESSING | SET LOCKED-BUT-UPDATE-FAILED | "Update of record failed" |
| Customer REWRITE failed | OTHER | 9600-WRITE-PROCESSING | SYNCPOINT ROLLBACK, SET LOCKED-BUT-UPDATE-FAILED | "Update of record failed" |
| Unexpected state | — | 2000-DECIDE-ACTION | ABEND '9999' | "UNEXPECTED DATA SCENARIO" (sent via ABEND-ROUTINE) |

### 8.3 HANDLE ABEND

```cobol
EXEC CICS HANDLE ABEND LABEL(ABEND-ROUTINE) END-EXEC
```

The ABEND-ROUTINE:
1. Sets ABEND-MSG if not already set ("UNEXPECTED ABEND OCCURRED.")
2. Moves program name to ABEND-CULPRIT
3. SEND FROM(ABEND-DATA) ERASE — displays abend info to terminal
4. HANDLE ABEND CANCEL — prevents recursive abend handling
5. ABEND ABCODE('9999') — forces controlled abend

### 8.4 User Error Messages (Complete List)

| # | Message Text | Trigger Condition |
|---|-------------|-------------------|
| 1 | "Account number not provided" | Account Number field is blank |
| 2 | "Account Number if supplied must be a 11 digit Non-Zero Number" | Not numeric or equals zero |
| 3 | "No input received" | All search criteria blank |
| 4 | "No change detected with respect to values fetched." | All new values match old values |
| 5 | "Account Status must be Y or N." | Active Status not Y/N |
| 6 | "Credit Limit must be supplied." | Credit Limit blank |
| 7 | "Credit Limit is not valid" | TEST-NUMVAL-C fails |
| 8 | "Cash Credit Limit must be supplied." | Cash Credit Limit blank |
| 9 | "Cash Credit Limit is not valid" | TEST-NUMVAL-C fails |
| 10 | "Current Balance must be supplied." | Current Balance blank |
| 11 | "Current Balance is not valid" | TEST-NUMVAL-C fails |
| 12 | "Current Cycle Credit Limit must be supplied." | Cycle Credit blank |
| 13 | "Current Cycle Credit Limit is not valid" | TEST-NUMVAL-C fails |
| 14 | "Current Cycle Debit Limit must be supplied." | Cycle Debit blank |
| 15 | "Current Cycle Debit Limit is not valid" | TEST-NUMVAL-C fails |
| 16 | "First Name must be supplied." | First Name blank |
| 17 | "First Name can have alphabets only." | Non-alpha characters found |
| 18 | "Middle Name can have alphabets only." | Non-alpha characters found (optional field) |
| 19 | "Last Name must be supplied." | Last Name blank |
| 20 | "Last Name can have alphabets only." | Non-alpha characters found |
| 21 | "Address Line 1 must be supplied." | Address Line 1 blank |
| 22 | "State must be supplied." | State code blank |
| 23 | "State can have alphabets only." | Non-alpha in state |
| 24 | "State: is not a valid state code" | Not in US state code table |
| 25 | "Zip must be supplied." | Zip code blank |
| 26 | "Zip must be all numeric." | Non-numeric zip |
| 27 | "Zip must not be zero." | Zip is all zeros |
| 28 | "Invalid zip code for state" | State/Zip prefix mismatch |
| 29 | "City must be supplied." | City blank |
| 30 | "City can have alphabets only." | Non-alpha in city |
| 31 | "Country must be supplied." | Country blank |
| 32 | "Country can have alphabets only." | Non-alpha in country |
| 33 | "Phone Number 1: Area code must be supplied." | Area code blank when other parts present |
| 34 | "Phone Number 1: Area code must be A 3 digit number." | Area code non-numeric |
| 35 | "Phone Number 1: Area code cannot be zero" | Area code = 000 |
| 36 | "Phone Number 1: Not valid North America general purpose area code" | Not in CSLKPCDY lookup table |
| 37 | "Phone Number 1: Prefix must be supplied." | Prefix blank when area code present |
| 38 | "Phone Number 1: Prefix must be A 3 digit number." | Prefix non-numeric |
| 39 | "Phone Number 1: Line number must be supplied." | Line number blank when prefix present |
| 40 | "Phone Number 1: Line number must be A 4 digit number." | Line number non-numeric |
| 41 | "Phone Number 2: ..." | Same validations as Phone 1 |
| 42 | "SSN Part 1 must be supplied." | SSN area number blank |
| 43 | "SSN Part 1 must be all numeric." | Non-numeric SSN part 1 |
| 44 | "SSN Part 1 has invalid area number" | Value is 000, 666, or 900-999 |
| 45 | "SSN Part 2 must be supplied." | SSN group number blank |
| 46 | "SSN Part 2 must be all numeric." | Non-numeric SSN part 2 |
| 47 | "SSN Part 2 must not be zero." | SSN part 2 = 00 |
| 48 | "SSN Part 3 must be supplied." | SSN serial number blank |
| 49 | "SSN Part 3 must be all numeric." | Non-numeric SSN part 3 |
| 50 | "SSN Part 3 must not be zero." | SSN part 3 = 0000 |
| 51 | "FICO Score must be supplied." | FICO blank |
| 52 | "FICO Score must be all numeric." | Non-numeric FICO |
| 53 | "FICO Score: should be between 300 and 850" | Value outside 300-850 |
| 54 | "EFT Account Id must be supplied." | EFT Account ID blank |
| 55 | "EFT Account Id must be all numeric." | Non-numeric EFT |
| 56 | "EFT Account Id must not be zero." | EFT = 0 |
| 57 | "Primary Card Holder must be Y or N." | Not Y/N |
| 58 | "Could not lock account record for update" | READ UPDATE on ACCTDAT failed |
| 59 | "Could not lock customer record for update" | READ UPDATE on CUSTDAT failed |
| 60 | "Record changed by some one else. Please review" | Optimistic concurrency check failed |
| 61 | "Update of record failed" | REWRITE returned non-NORMAL |

### 8.5 Information Messages

| # | Message Text | Context |
|---|-------------|---------|
| 1 | "Enter or update id of account to update" | Initial entry / search mode |
| 2 | "Details of selected account shown above" | After successful data retrieval |
| 3 | "Update account details presented above." | Data displayed for editing |
| 4 | "Changes validated.Press F5 to save" | All validations passed |
| 5 | "Changes committed to database" | Successful update |
| 6 | "Changes unsuccessful. Please try again" | Lock error or update failure |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Aspect | Implementation |
|--------|---------------|
| Transaction Authorization | CICS transaction security via CARDDEMO.CSD definition (STATUS=ENABLED) |
| User Type | Both Regular Users ('U') and Admins ('A') can access (menu option 2) |
| Program Security | Standard CICS program security via CSD GROUP(CARDDEMO) |

### 9.2 Data Access Authorization

| Aspect | Implementation |
|--------|---------------|
| File Access | Controlled by CICS FCT definitions (ACCTDAT, CUSTDAT, CXACAIX) |
| Update Authority | Any authenticated user with access to CAUP transaction |
| Customer ID field | Protected (DFHBMPRF) during edit — cannot be changed by user |
| Country field | Protected during edit — prevents country changes due to US-specific validations |

### 9.3 Audit Trail

| Aspect | Implementation |
|--------|---------------|
| Change Logging | No explicit audit logging within this program |
| Concurrency Control | Optimistic locking via snapshot comparison (9700-CHECK-CHANGE-IN-REC) |
| Transaction Integrity | SYNCPOINT ROLLBACK on partial failure (customer update fails after account update succeeds) |

---

## 10. Performance Characteristics

### 10.1 Resource Usage

| Resource | Usage Pattern |
|----------|--------------|
| VSAM I/O (Read) | 3 reads per initial account fetch (XREF + ACCT + CUST) |
| VSAM I/O (Update) | 2 READ UPDATE + 2 REWRITE per save operation |
| COMMAREA Size | ~2000 bytes (shared + program-specific old/new data) |
| Working Storage | ~4000 bytes (validation flags, edit variables, file buffers) |
| CPU | Moderate — extensive field-by-field comparison and INSPECT operations |

### 10.2 Bottlenecks

| Concern | Description |
|---------|-------------|
| Record Locking | Exclusive locks on both ACCTDAT and CUSTDAT during write — potential contention in high-volume |
| No partial updates | Both files always rewritten even if only one changed |
| Inline validation | All validation performed in single program; no caching of lookup tables |

---

## 11. Testing Approach

### 11.1 Test Scenarios

| Scenario ID | Description | Input | Expected Output |
|------------|-------------|-------|-----------------|
| TC-001 | Valid account lookup | Enter valid 11-digit account number | All account and customer fields populated |
| TC-002 | Invalid account number (non-numeric) | Enter "ABCDEFGHIJK" | Error: "Account Number if supplied must be a 11 digit Non-Zero Number" |
| TC-003 | Account not found | Enter non-existent account number | Error with NOTFND details |
| TC-004 | No changes made | View data, press ENTER without changes | Info: "No change detected..." |
| TC-005 | Valid field update | Change Credit Limit, press ENTER | "Changes validated. Press F5 to save" |
| TC-006 | Confirm and save | Press F5 after validation | "Changes committed to database" |
| TC-007 | Invalid active status | Enter 'X' in Active Status | Error: "Account Status must be Y or N." |
| TC-008 | Invalid credit limit | Enter "ABC" in Credit Limit | Error: "Credit Limit is not valid" |
| TC-009 | Invalid date | Enter month 13 in open date | Date validation error |
| TC-010 | Invalid FICO score | Enter 200 | Error: "FICO Score: should be between 300 and 850" |
| TC-011 | Invalid phone area code | Enter 000 in Phone 1 area | Error: "Phone Number 1: Area code cannot be zero" |
| TC-012 | Invalid SSN | Enter 000 in SSN Part 1 | Error: "SSN Part 1 has invalid area number" |
| TC-013 | State/Zip mismatch | Enter CA with zip 10001 | Error: "Invalid zip code for state" |
| TC-014 | Concurrent modification | Another user changes record between read and F5 | Error: "Record changed by some one else. Please review" |
| TC-015 | Cancel changes (F12) | Make changes, press F12 | Original values restored from database |
| TC-016 | Exit (PF3) | Press PF3 at any time | Return to Main Menu (COMEN01C) |
| TC-017 | Blank account number | Press ENTER with empty account field | Error: "Account number not provided" |
| TC-018 | Name with numbers | Enter "John123" in First Name | Error: "First Name can have alphabets only." |

### 11.2 Test Data Requirements

| Data Element | Requirement |
|-------------|-------------|
| Valid Account | 11-digit account existing in CXACAIX, ACCTDAT with linked customer in CUSTDAT |
| Non-existent Account | 11-digit number not in any data file |
| Multiple accounts | For concurrency testing — two sessions accessing same record |
| Edge case accounts | Account at credit limit boundaries, zero balances |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| Issue | Description | Impact |
|-------|-------------|--------|
| No partial update | If only account data changes, customer record is still rewritten (and vice versa) | Unnecessary I/O and locking |
| Country field locked | ACSCTRY field is protected during edit; cannot change country code | Limits international use |
| Phone numbers optional | Phone validation only triggers if area code is present; allows partial phone entries | Data quality concern |
| Address Line 2 no validation | ACSADL2 has no validation rules applied | Inconsistency with Line 1 |
| Customer ID not editable | ACSTNUM protected in edit mode; reassignment requires different process | Design limitation |
| DOB offset issue | In 9700-CHECK-CHANGE-IN-REC, DOB month comparison uses offset (5:2) vs (6:2) | Potential false concurrency conflict |

### 12.2 Modernization Considerations

| Area | Current State | Modernization Approach |
|------|--------------|----------------------|
| Data Access | Direct VSAM READ/REWRITE with manual locking | Replace with database transactions (SQL UPDATE with WHERE clause for optimistic locking) |
| Validation | 1000+ lines of procedural validation code | Extract to reusable validation service/library with declarative rules |
| UI | BMS 3270 map with field-level cursor positioning | Modern web form with client-side and server-side validation |
| State Management | COMMAREA-based pseudo-conversational | HTTP session or JWT-based stateless API |
| Concurrency | Manual snapshot comparison (9700 paragraph) | Database-level optimistic concurrency (version column or timestamp) |
| Phone Validation | Hardcoded NA area code lookup table (CSLKPCDY) | External API or configurable reference data service |
| Address Validation | Crude state/zip prefix matching | Integration with address verification service (USPS API) |
| Error Handling | Single error message at a time (first error wins) | Return all validation errors simultaneously |
| Audit | No change tracking | Add audit trail table/event for all modifications |
| Transaction Scope | Account + Customer always updated together | Microservice pattern: separate account and customer update APIs |

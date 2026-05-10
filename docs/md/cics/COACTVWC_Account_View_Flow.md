# Account View (CAVW) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CAVW |
| Entry Program | COACTVWC |
| Business Domain | Account Management |
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

- **What**: Allows an operator to view the details of a credit card account, including account financial information (balances, limits, dates) and the associated customer demographic details (name, address, phone, SSN, FICO score).
- **Who**: Any authenticated CardDemo user (regular users and admins) — menu option 1 ("Account View").
- **When**: Real-time, on-demand during business hours when an operator needs to review account details.
- **Why**: Supports account inquiry for customer service, account review, and operational verification. This is a read-only function — no data modification occurs.
- **What** is the business outcome: The operator is presented with a comprehensive view of the account master record and the associated customer master record, displayed on a single BMS screen.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  User selects Option 1 ("Account View") from Main Menu
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 1            │  Enter Account Number (11-digit numeric)
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ System Validation        │  Validate account number:
  └────────────┬─────────────┘  - Must be provided (not blank)
               │                - Must be numeric
               │                - Must be non-zero
           ┌───┴───┐
           │Decision│  Validation passes?
           └───┬───┘
              ╱ ╲
        Yes ╱     ╲ No
           ▼       ▼
  ┌──────────────┐  ┌─────────────────┐
  │ Lookup       │  │ Error message,  │
  │ Cross-Ref    │  │ cursor to field │
  └──────┬───────┘  └─────────────────┘
         │
         ▼
  ┌──────────────────────────┐
  │ System Lookup            │  READ CXACAIX (Card XREF by Account)
  └────────────┬─────────────┘  → retrieves Customer ID, Card Number
               │
               ▼
  ┌──────────────────────────┐
  │ System Lookup            │  READ ACCTDAT (Account Master)
  └────────────┬─────────────┘  → retrieves account financial details
               │
               ▼
  ┌──────────────────────────┐
  │ System Lookup            │  READ CUSTDAT (Customer Master)
  └────────────┬─────────────┘  → retrieves customer demographics
               │
               ▼
  ┌──────────────────────────┐
  │ Business Outcome         │  Display full account + customer details
  └──────────────────────────┘  on the Account View screen (CACTVWA)
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | Account number required | Account Number field is blank or spaces | Error: "Account number not provided" / "No input received" |
| BR-002 | Account number must be numeric | Account Number contains non-numeric characters | Error: "Account Filter must be a non-zero 11 digit number" |
| BR-003 | Account number must be non-zero | Account Number is all zeroes | Error: "Account Filter must be a non-zero 11 digit number" |
| BR-004 | Account must exist in cross-reference | Account ID not found in CXACAIX | Error: "Account:<acctid> not found in Cross ref file. Resp:<resp> Reas:<reas>" |
| BR-005 | Account must exist in account master | Account ID not found in ACCTDAT | Error: "Account:<acctid> not found in Acct Master file. Resp:<resp> Reas:<reas>" |
| BR-006 | Customer must exist in customer master | Customer ID (from xref) not found in CUSTDAT | Error: "CustId:<custid> not found in customer master. Resp:<resp> REAS:<reas>" |
| BR-007 | Read-only display | All lookups succeed | Display account and customer details; all detail fields are protected (ASKIP) |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Select Option 1 from Main Menu | XCTL to COACTVWC; display Account View screen with empty account number field | Navigate to account inquiry |
| 2 | Enter Account Number, press ENTER | Validate input; if valid, read CXACAIX, ACCTDAT, and CUSTDAT; display full account + customer details | View account information |
| 3 | Enter different Account Number, press ENTER | Re-validate and display new account details | Query another account |
| Alt-A | Press PF3 | XCTL back to previous screen (or Main Menu COMEN01C) | Exit account view |
| Alt-B | Press any other key | Treated as ENTER (invalid keys default to ENTER behavior) | — |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CAVW
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COACTVWC     │ (Entry & only online program)      │
  │  │ (Acct View)  │                                    │
  │  └──┬────┬──┬───┘                                    │
  │     │    │  │                                         │
  │     ▼    │  ▼                                         │
  │  ┌──────┐│ ┌────────────────────────────────────┐    │
  │  │BMS   ││ │ VSAM Files                         │    │
  │  │MAP:  ││ │  CXACAIX (Acct→Card XREF AIX)     │    │
  │  │CACTV ││ │  ACCTDAT (Account Master)          │    │
  │  │WA    ││ │  CUSTDAT (Customer Master)          │    │
  │  └──────┘│ └────────────────────────────────────┘    │
  │          │                                            │
  │    (No sub-program calls)                             │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1 (First entry — from Main Menu via XCTL):
  1. COMEN01C XCTLs to COACTVWC with CARDDEMO-COMMAREA
  2. HANDLE ABEND LABEL(ABEND-ROUTINE)
  3. INITIALIZE CC-WORK-AREA, WS-MISC-STORAGE, WS-COMMAREA
  4. EIBCALEN = 0 OR (FROM-PROGRAM = COMEN01C AND NOT PGM-REENTER)
     └── INITIALIZE CARDDEMO-COMMAREA, WS-THIS-PROGCOMMAREA
  5. PERFORM YYYY-STORE-PFKEY (map EIBAID → CCARD-AID)
  6. PFK check: only ENTER and PF3 are valid; others default to ENTER
  7. EVALUATE: CDEMO-PGM-ENTER (context = 0, first entry)
     └── PERFORM 1000-SEND-MAP
         ├── 1100-SCREEN-INIT: set titles, date, time, program/tran name
         ├── 1200-SETUP-SCREEN-VARS: prompt "Enter or update id of account to display"
         ├── 1300-SETUP-SCREEN-ATTRS: set ACCTSID field FSET, cursor to ACCTSID
         └── 1400-SEND-SCREEN: SET PGM-REENTER, SEND MAP CACTVWA ERASE CURSOR
  8. GO TO COMMON-RETURN
     └── RETURN TRANSID('CAVW') COMMAREA(WS-COMMAREA)

  Iteration 2 (User enters account number — ENTER pressed):
  9.  CICS dispatches → COACTVWC (COMMAREA restored)
  10. EIBCALEN > 0 and CDEMO-PGM-REENTER = 1
  11. PERFORM YYYY-STORE-PFKEY
  12. EVALUATE: CDEMO-PGM-REENTER
      └── PERFORM 2000-PROCESS-INPUTS
          ├── 2100-RECEIVE-MAP: RECEIVE MAP('CACTVWA') INTO(CACTVWAI)
          └── 2200-EDIT-MAP-INPUTS:
              ├── Extract ACCTSIDI → CC-ACCT-ID
              └── 2210-EDIT-ACCOUNT:
                  ├── Blank/spaces → INPUT-ERROR, "Account number not provided"
                  ├── Not numeric or zeros → INPUT-ERROR, "Account Filter must be a non-zero 11 digit number"
                  └── Valid → MOVE CC-ACCT-ID TO CDEMO-ACCT-ID
  13. IF INPUT-ERROR → PERFORM 1000-SEND-MAP (redisplay with error), GO TO COMMON-RETURN
  14. IF INPUT-OK → PERFORM 9000-READ-ACCT:
      ├── 9200-GETCARDXREF-BYACCT:
      │   └── READ CXACAIX by account → CARD-XREF-RECORD
      │       ├── NORMAL → XREF-CUST-ID → CDEMO-CUST-ID, XREF-CARD-NUM → CDEMO-CARD-NUM
      │       ├── NOTFND → INPUT-ERROR, error message with RESP/REAS
      │       └── OTHER → INPUT-ERROR, file error message
      ├── 9300-GETACCTDATA-BYACCT:
      │   └── READ ACCTDAT by account → ACCOUNT-RECORD
      │       ├── NORMAL → FOUND-ACCT-IN-MASTER
      │       ├── NOTFND → INPUT-ERROR, error message with RESP/REAS
      │       └── OTHER → INPUT-ERROR, file error message
      └── 9400-GETCUSTDATA-BYCUST:
          └── READ CUSTDAT by customer ID → CUSTOMER-RECORD
              ├── NORMAL → FOUND-CUST-IN-MASTER
              ├── NOTFND → INPUT-ERROR, error message with RESP/REAS
              └── OTHER → INPUT-ERROR, file error message
  15. PERFORM 1000-SEND-MAP
      └── 1200-SETUP-SCREEN-VARS populates all output fields from
          ACCOUNT-RECORD and CUSTOMER-RECORD
  16. GO TO COMMON-RETURN
      └── RETURN TRANSID('CAVW') COMMAREA(WS-COMMAREA)
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | 0 or >0 | XCTL from menu (option 1) | Initial logic (PGM-ENTER) | CACTVWA (empty form, prompt) | CAVW |
| 2 | >0 | Enter Account Number, press ENTER | RECEIVE → 2000-PROCESS-INPUTS → 9000-READ-ACCT | CACTVWA (with account/customer data or error) | CAVW |
| N | >0 | Enter new Account Number, press ENTER | Same as Iteration 2 | CACTVWA (updated data or error) | CAVW |
| Exit | >0 | PF3 (Exit) | XCTL to CDEMO-FROM-PROGRAM or COMEN01C | — | — (XCTL) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | HANDLE ABEND LABEL(ABEND-ROUTINE) | Set abend handler for unexpected failures | Routes to ABEND-ROUTINE paragraph |
| 2 | RECEIVE MAP('CACTVWA') MAPSET('COACTVW') | Capture user input from terminal | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 3 | READ DATASET('CXACAIX') RIDFLD(WS-CARD-RID-ACCT-ID-X) | Lookup card xref by account ID | NORMAL → continue; NOTFND → error; OTHER → error |
| 4 | READ DATASET('ACCTDAT') RIDFLD(WS-CARD-RID-ACCT-ID-X) | Read account master record | NORMAL → continue; NOTFND → error; OTHER → error |
| 5 | READ DATASET('CUSTDAT') RIDFLD(WS-CARD-RID-CUST-ID-X) | Read customer master record | NORMAL → continue; NOTFND → error; OTHER → error |
| 6 | SEND MAP('CACTVWA') MAPSET('COACTVW') FROM(CACTVWAO) CURSOR ERASE FREEKB | Send screen to terminal with account/customer data | — |
| 7 | RETURN TRANSID('CAVW') COMMAREA(WS-COMMAREA) LENGTH(LENGTH OF WS-COMMAREA) | Pseudo-conversational return | — |
| 8 | XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA) | Transfer to previous screen on PF3 | — |
| 9 | SEND TEXT FROM(WS-RETURN-MSG) ERASE FREEKB | Plain text display for unexpected scenarios | Followed by RETURN (no TRANSID) |
| 10 | SEND FROM(ABEND-DATA) NOHANDLE | Display abend information | Followed by ABEND ABCODE('9999') |
| 11 | HANDLE ABEND CANCEL | Cancel abend handler during abend routine | Prevents recursive abend handling |
| 12 | ABEND ABCODE('9999') | Force abend after displaying abend data | Terminal CICS abend |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────┐                ┌─────────────────────────────────────────┐
  │  Main Menu  │   Option 1     │  CACTVWA (Account View)                 │
  │  (COMEN01C) │───────────────▶│                                         │
  └─────────────┘                │  Account Number : ___________           │
        ▲                        │  Active Y/N: _                          │
        │ PF3                    │  ─────────────────────────────────────   │
        │                        │  Opened: __________                     │
        │                        │  Credit Limit:    +ZZZ,ZZZ,ZZZ.99      │
        │                        │  Expiry: __________                     │
        │                        │  Cash Credit Limit: +ZZZ,ZZZ,ZZZ.99    │
        │                        │  Reissue: __________                    │
        │                        │  Current Balance:   +ZZZ,ZZZ,ZZZ.99    │
        │                        │  ─── Customer Details ──────────────    │
        │                        │  Customer id: _________  SSN: ______   │
        │                        │  Date of birth: ________  FICO: ___    │
        │                        │  Name / Address / Phone / etc.          │
        │                        │  [Info message line]                    │
        │                        │  [Error message line]                   │
        │                        │  F3=Exit                                │
        └────────────────────────┤                                         │
                                 └─────────────────────────────────────────┘
                                   │
                                   │ ENTER (with new account number)
                                   │ (loops back to self with updated data)
                                   ▼
                                 [Same screen — CACTVWA]
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| CACTVWA | COACTVW | Account View — display account and customer details | Account Number (input), Account status, dates, balances, limits, customer name/address/phone/SSN/FICO | ENTER=Submit query, PF3=Exit |

### 3.3 Screen Field Details

#### Map: CACTVWA

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction name (CAVW) |
| TITLE01 | ASKIP,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | ASKIP,NORM | 8 | Alpha | — | Program name (COACTVWC) |
| TITLE02 | ASKIP,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | ASKIP,NORM | 8 | Time | — | Current time hh:mm:ss |
| ACCTSID | FSET,IC,NORM,UNPROT | 11 | Numeric | MUSTFILL; must be numeric, non-zero, 11 digits; PICIN='99999999999' | Account Number (input field) |
| ACSTTUS | ASKIP | 1 | Alpha | — (output) | Account Active Status (Y/N) |
| ADTOPEN | — (output) | 10 | Date | — | Account Open Date |
| ACRDLIM | — (output) | 15 | Decimal | PICOUT='+ZZZ,ZZZ,ZZZ.99' | Credit Limit |
| AEXPDT | — (output) | 10 | Date | — | Account Expiration Date |
| ACSHLIM | — (output) | 15 | Decimal | PICOUT='+ZZZ,ZZZ,ZZZ.99' | Cash Credit Limit |
| AREISDT | — (output) | 10 | Date | — | Reissue Date |
| ACURBAL | — (output) | 15 | Decimal | PICOUT='+ZZZ,ZZZ,ZZZ.99' | Current Balance |
| ACRCYCR | — (output) | 15 | Decimal | PICOUT='+ZZZ,ZZZ,ZZZ.99' | Current Cycle Credit |
| AADDGRP | — (output) | 10 | Alpha | — | Account Group ID |
| ACRCYDB | — (output) | 15 | Decimal | PICOUT='+ZZZ,ZZZ,ZZZ.99' | Current Cycle Debit |
| ACSTNUM | — (output) | 9 | Numeric | — | Customer ID |
| ACSTSSN | — (output) | 12 | Alpha | — | Customer SSN (formatted XXX-XX-XXXX) |
| ACSTDOB | — (output) | 10 | Date | — | Customer Date of Birth |
| ACSTFCO | — (output) | 3 | Numeric | — | FICO Credit Score |
| ACSFNAM | — (output) | 25 | Alpha | — | Customer First Name |
| ACSMNAM | — (output) | 25 | Alpha | — | Customer Middle Name |
| ACSLNAM | — (output) | 25 | Alpha | — | Customer Last Name |
| ACSADL1 | — (output) | 50 | Alpha | — | Address Line 1 |
| ACSSTTE | — (output) | 2 | Alpha | — | State Code |
| ACSADL2 | — (output) | 50 | Alpha | — | Address Line 2 |
| ACSZIPC | — (output) | 5 | Alpha | JUSTIFY=(RIGHT) | Zip Code |
| ACSCITY | — (output) | 50 | Alpha | — | City (mapped from Address Line 3) |
| ACSCTRY | — (output) | 3 | Alpha | — | Country Code |
| ACSPHN1 | — (output) | 13 | Alpha | — | Phone Number 1 |
| ACSGOVT | — (output) | 20 | Alpha | — | Government Issued ID Reference |
| ACSPHN2 | — (output) | 13 | Alpha | — | Phone Number 2 |
| ACSEFTC | — (output) | 10 | Alpha | — | EFT Account ID |
| ACSPFLG | — (output) | 1 | Alpha | — | Primary Card Holder Flag (Y/N) |
| INFOMSG | PROT | 45 | Alpha | — | Informational message (prompt or status) |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/status message display (red) |

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | CACTVWA | Validate account number; if valid, read xref/account/customer and display details |
| PF3 | CACTVWA | Return to previous screen (CDEMO-FROM-PROGRAM or Main Menu COMEN01C) via XCTL |
| OTHER (any unrecognized key) | CACTVWA | Treated as ENTER — PFK-INVALID flag forces CCARD-AID-ENTER, then normal processing |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COACTVWC | Account View — screen handler, validation, read-only data retrieval | TRANSID CAVW / XCTL from COMEN01C | 941 | `app/cbl/COACTVWC.cbl` |
| COMEN01C | Main Menu — entry point for option 1 | XCTL (caller) | — | `app/cbl/COMEN01C.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CAVW
└── COACTVWC (Entry — Screen Handler, Validation, Data Retrieval)
    ├── READ FILE('CXACAIX') — Account-to-Card cross-reference lookup
    ├── READ FILE('ACCTDAT') — Account master record lookup
    ├── READ FILE('CUSTDAT') — Customer master record lookup
    ├── SEND MAP('CACTVWA') — Display account view screen
    ├── RECEIVE MAP('CACTVWA') — Capture input (account number)
    ├── RETURN TRANSID('CAVW') — Pseudo-conversational return
    └── XCTL → COMEN01C or CDEMO-FROM-PROGRAM (on PF3 exit)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COMEN01C | COACTVWC | XCTL | CARDDEMO-COMMAREA | — | User selects menu option 1 |
| COACTVWC | COMEN01C | XCTL | CARDDEMO-COMMAREA | — | PF3 exit back to main menu |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| Card Cross-Reference (AIX) | CXACAIX | VSAM KSDS (Alternate Index) | READ | COACTVWC | Lookup customer ID and card number by account ID |
| Account Master | ACCTDAT | VSAM KSDS | READ | COACTVWC | Read account financial details (balances, limits, dates) |
| Customer Master | CUSTDAT | VSAM KSDS | READ | COACTVWC | Read customer demographic details (name, address, phone, SSN) |

### 5.2 File Access Details

#### File: CXACAIX (Account-to-Card Cross-Reference AIX)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS (Alternate Index over CARDXREF) |
| Record Layout (Copybook) | CVACT03Y (CARD-XREF-RECORD, 50 bytes) |
| Key | WS-CARD-RID-ACCT-ID-X (PIC X(11), account ID via alternate index) |
| Access Mode | READ |
| Locking | No update, read-only |
| Shared With | COACTUPC (Account Update), COTRN02C (Add Transaction), COTRN00C (Transaction List) |

**Access Pattern**:
| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COACTVWC | READ DATASET('CXACAIX') RIDFLD(WS-CARD-RID-ACCT-ID-X) KEYLENGTH(11) | Account ID from screen (ACCTSIDI) | Returns XREF-CUST-ID, XREF-CARD-NUM |

#### File: ACCTDAT (Account Master)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CVACT01Y (ACCOUNT-RECORD, 300 bytes) |
| Key | WS-CARD-RID-ACCT-ID-X (PIC X(11), account ID) |
| Access Mode | READ |
| Locking | No update, read-only |
| Shared With | COACTUPC (Account Update), batch programs |

**Access Pattern**:
| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COACTVWC | READ DATASET('ACCTDAT') RIDFLD(WS-CARD-RID-ACCT-ID-X) KEYLENGTH(11) | Account ID | Returns full ACCOUNT-RECORD (status, balances, limits, dates, group) |

#### File: CUSTDAT (Customer Master)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CVCUS01Y (CUSTOMER-RECORD, 500 bytes) |
| Key | WS-CARD-RID-CUST-ID-X (PIC X(9), customer ID) |
| Access Mode | READ |
| Locking | No update, read-only |
| Shared With | COUSR00C (User List), COUSR01C (User Add), batch programs |

**Access Pattern**:
| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COACTVWC | READ DATASET('CUSTDAT') RIDFLD(WS-CARD-RID-CUST-ID-X) KEYLENGTH(9) | Customer ID (from XREF lookup) | Returns full CUSTOMER-RECORD (name, address, phone, SSN, FICO, etc.) |

### 5.3 Temporary Storage (TSQ) Usage

| TSQ Name | Purpose | Record Layout | Written By | Read By | Lifecycle |
|----------|---------|--------------|-----------|---------|-----------|
| — | Not used by this transaction | — | — | — | — |

### 5.4 Transient Data (TDQ) Usage

| TDQ Name | Type | Purpose | Record Layout | Triggered Program |
|----------|------|---------|--------------|-------------------|
| — | — | Not used by this transaction | — | — |

---

## 6. Data Flow Analysis

### 6.1 Data Flow Through Transaction

```
  Terminal Input (CACTVWA)
       │
       └── ACCTSIDI (Account Number, 11 digits)
               │
               ├──▶ CC-ACCT-ID (validation)
               │       │
               │       └──▶ CDEMO-ACCT-ID (COMMAREA)
               │               │
               │               └──▶ WS-CARD-RID-ACCT-ID
               │
               └──▶ READ CXACAIX → CARD-XREF-RECORD
                        │
                        ├── XREF-CUST-ID → CDEMO-CUST-ID → WS-CARD-RID-CUST-ID
                        │                                        │
                        │                                        └──▶ READ CUSTDAT → CUSTOMER-RECORD
                        │                                                 │
                        │                                                 ├── CUST-ID → ACSTNUMO
                        │                                                 ├── CUST-SSN → ACSTSSNO (formatted XXX-XX-XXXX)
                        │                                                 ├── CUST-FICO-CREDIT-SCORE → ACSTFCOO
                        │                                                 ├── CUST-DOB-YYYY-MM-DD → ACSTDOBO
                        │                                                 ├── CUST-FIRST-NAME → ACSFNAMO
                        │                                                 ├── CUST-MIDDLE-NAME → ACSMNAMO
                        │                                                 ├── CUST-LAST-NAME → ACSLNAMO
                        │                                                 ├── CUST-ADDR-LINE-1 → ACSADL1O
                        │                                                 ├── CUST-ADDR-LINE-2 → ACSADL2O
                        │                                                 ├── CUST-ADDR-LINE-3 → ACSCITYO
                        │                                                 ├── CUST-ADDR-STATE-CD → ACSSTTEO
                        │                                                 ├── CUST-ADDR-ZIP → ACSZIPCO
                        │                                                 ├── CUST-ADDR-COUNTRY-CD → ACSCTRYO
                        │                                                 ├── CUST-PHONE-NUM-1 → ACSPHN1O
                        │                                                 ├── CUST-PHONE-NUM-2 → ACSPHN2O
                        │                                                 ├── CUST-GOVT-ISSUED-ID → ACSGOVTO
                        │                                                 ├── CUST-EFT-ACCOUNT-ID → ACSEFTCO
                        │                                                 └── CUST-PRI-CARD-HOLDER-IND → ACSPFLGO
                        │
                        └── XREF-CARD-NUM → CDEMO-CARD-NUM
                                (stored in COMMAREA only; not displayed)

               └──▶ READ ACCTDAT → ACCOUNT-RECORD
                        │
                        ├── ACCT-ACTIVE-STATUS → ACSTTUSO
                        ├── ACCT-CURR-BAL → ACURBALO (+ZZZ,ZZZ,ZZZ.99)
                        ├── ACCT-CREDIT-LIMIT → ACRDLIMO (+ZZZ,ZZZ,ZZZ.99)
                        ├── ACCT-CASH-CREDIT-LIMIT → ACSHLIMO (+ZZZ,ZZZ,ZZZ.99)
                        ├── ACCT-CURR-CYC-CREDIT → ACRCYCRO (+ZZZ,ZZZ,ZZZ.99)
                        ├── ACCT-CURR-CYC-DEBIT → ACRCYDBO (+ZZZ,ZZZ,ZZZ.99)
                        ├── ACCT-OPEN-DATE → ADTOPENO
                        ├── ACCT-EXPIRAION-DATE → AEXPDTO
                        ├── ACCT-REISSUE-DATE → AREISDTO
                        └── ACCT-GROUP-ID → AADDGRPO
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| ACCTSIDI | Terminal (CACTVWAI) | Direct move → CC-ACCT-ID → CDEMO-ACCT-ID | WS-CARD-RID-ACCT-ID | READ key for CXACAIX and ACCTDAT |
| XREF-CUST-ID | CARD-XREF-RECORD | Direct move → CDEMO-CUST-ID | WS-CARD-RID-CUST-ID | READ key for CUSTDAT |
| ACCT-ACTIVE-STATUS | ACCOUNT-RECORD | Direct move PIC X(1) | ACSTTUSO | Screen output |
| ACCT-CURR-BAL | ACCOUNT-RECORD | PIC S9(10)V99 → PICOUT '+ZZZ,ZZZ,ZZZ.99' | ACURBALO | Screen output (formatted currency) |
| ACCT-CREDIT-LIMIT | ACCOUNT-RECORD | PIC S9(10)V99 → PICOUT '+ZZZ,ZZZ,ZZZ.99' | ACRDLIMO | Screen output (formatted currency) |
| ACCT-CASH-CREDIT-LIMIT | ACCOUNT-RECORD | PIC S9(10)V99 → PICOUT '+ZZZ,ZZZ,ZZZ.99' | ACSHLIMO | Screen output (formatted currency) |
| ACCT-CURR-CYC-CREDIT | ACCOUNT-RECORD | PIC S9(10)V99 → PICOUT '+ZZZ,ZZZ,ZZZ.99' | ACRCYCRO | Screen output (formatted currency) |
| ACCT-CURR-CYC-DEBIT | ACCOUNT-RECORD | PIC S9(10)V99 → PICOUT '+ZZZ,ZZZ,ZZZ.99' | ACRCYDBO | Screen output (formatted currency) |
| ACCT-OPEN-DATE | ACCOUNT-RECORD | Direct move PIC X(10) | ADTOPENO | Screen output |
| ACCT-EXPIRAION-DATE | ACCOUNT-RECORD | Direct move PIC X(10) | AEXPDTO | Screen output |
| ACCT-REISSUE-DATE | ACCOUNT-RECORD | Direct move PIC X(10) | AREISDTO | Screen output |
| ACCT-GROUP-ID | ACCOUNT-RECORD | Direct move PIC X(10) | AADDGRPO | Screen output |
| CUST-ID | CUSTOMER-RECORD | Direct move PIC 9(9) | ACSTNUMO | Screen output |
| CUST-SSN | CUSTOMER-RECORD | STRING: SSN(1:3) '-' SSN(4:2) '-' SSN(6:4) | ACSTSSNO | Screen output (formatted XXX-XX-XXXX) |
| CUST-FICO-CREDIT-SCORE | CUSTOMER-RECORD | Direct move PIC 9(3) | ACSTFCOO | Screen output |
| CUST-DOB-YYYY-MM-DD | CUSTOMER-RECORD | Direct move PIC X(10) | ACSTDOBO | Screen output |
| CUST-FIRST-NAME | CUSTOMER-RECORD | Direct move PIC X(25) | ACSFNAMO | Screen output |
| CUST-MIDDLE-NAME | CUSTOMER-RECORD | Direct move PIC X(25) | ACSMNAMO | Screen output |
| CUST-LAST-NAME | CUSTOMER-RECORD | Direct move PIC X(25) | ACSLNAMO | Screen output |
| CUST-ADDR-LINE-1 | CUSTOMER-RECORD | Direct move PIC X(50) | ACSADL1O | Screen output |
| CUST-ADDR-LINE-2 | CUSTOMER-RECORD | Direct move PIC X(50) | ACSADL2O | Screen output |
| CUST-ADDR-LINE-3 | CUSTOMER-RECORD | Direct move PIC X(50) | ACSCITYO | Screen output (mapped to City field) |
| CUST-ADDR-STATE-CD | CUSTOMER-RECORD | Direct move PIC X(2) | ACSSTTEO | Screen output |
| CUST-ADDR-ZIP | CUSTOMER-RECORD | Direct move PIC X(10) | ACSZIPCO | Screen output |
| CUST-ADDR-COUNTRY-CD | CUSTOMER-RECORD | Direct move PIC X(3) | ACSCTRYO | Screen output |
| CUST-PHONE-NUM-1 | CUSTOMER-RECORD | Direct move PIC X(15) | ACSPHN1O | Screen output |
| CUST-PHONE-NUM-2 | CUSTOMER-RECORD | Direct move PIC X(15) | ACSPHN2O | Screen output |
| CUST-GOVT-ISSUED-ID | CUSTOMER-RECORD | Direct move PIC X(20) | ACSGOVTO | Screen output |
| CUST-EFT-ACCOUNT-ID | CUSTOMER-RECORD | Direct move PIC X(10) | ACSEFTCO | Screen output |
| CUST-PRI-CARD-HOLDER-IND | CUSTOMER-RECORD | Direct move PIC X(1) | ACSPFLGO | Screen output |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`) plus a program-specific extension `WS-THIS-PROGCOMMAREA`:

**CARDDEMO-COMMAREA (COCOM01Y — shared portion):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-FROM-TRANID | X(04) | 4 | IN/OUT | Originating transaction ID |
| CDEMO-FROM-PROGRAM | X(08) | 8 | IN/OUT | Originating program name |
| CDEMO-TO-TRANID | X(04) | 4 | OUT | Target transaction ID |
| CDEMO-TO-PROGRAM | X(08) | 8 | OUT | Target program for XCTL |
| CDEMO-USER-ID | X(08) | 8 | IN | Authenticated user ID |
| CDEMO-USER-TYPE | X(01) | 1 | IN | 'A' = Admin, 'U' = User |
| CDEMO-PGM-CONTEXT | 9(01) | 1 | IN/OUT | 0 = first entry (PGM-ENTER), 1 = re-entry (PGM-REENTER) |
| CDEMO-CUST-ID | 9(09) | 9 | OUT | Customer ID (from XREF lookup) |
| CDEMO-CUST-FNAME | X(25) | 25 | — | Customer first name (not used by this program) |
| CDEMO-CUST-MNAME | X(25) | 25 | — | Customer middle name (not used by this program) |
| CDEMO-CUST-LNAME | X(25) | 25 | — | Customer last name (not used by this program) |
| CDEMO-ACCT-ID | 9(11) | 11 | IN/OUT | Account ID (from validated input) |
| CDEMO-ACCT-STATUS | X(01) | 1 | — | Account status (not used by this program) |
| CDEMO-CARD-NUM | 9(16) | 16 | OUT | Card Number (from XREF lookup) |
| CDEMO-LAST-MAP | X(7) | 7 | OUT | Last BMS map displayed ('CACTVWA') |
| CDEMO-LAST-MAPSET | X(7) | 7 | OUT | Last BMS mapset used ('COACTVW') |

**WS-THIS-PROGCOMMAREA (program-specific extension):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CA-FROM-PROGRAM | X(08) | 8 | IN/OUT | Calling program context |
| CA-FROM-TRANID | X(04) | 4 | IN/OUT | Calling transaction context |

**WS-COMMAREA (full COMMAREA passed to CICS RETURN):**

The full COMMAREA is assembled by concatenating CARDDEMO-COMMAREA and WS-THIS-PROGCOMMAREA into WS-COMMAREA (PIC X(2000)) before the RETURN command.

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. Errors are handled inline via screen messages:

| Code | Meaning | User Display |
|------|---------|-------------|
| INPUT-OK ('0') | Input validation passed | Continue to file lookups |
| INPUT-ERROR ('1') | Input validation failed or file lookup failed | Error message displayed in ERRMSG field |
| FLG-ACCTFILTER-ISVALID ('1') | Account number is valid format | Continue processing |
| FLG-ACCTFILTER-NOT-OK ('0') | Account number invalid or lookup failed | Error message, cursor to ACCTSID |
| FLG-ACCTFILTER-BLANK (' ') | Account number was not provided | Prompt message |
| FOUND-ACCT-IN-MASTER ('1') | Account found in ACCTDAT | Account data displayed |
| FOUND-CUST-IN-MASTER ('1') | Customer found in CUSTDAT | Customer data displayed |

### 7.3 Conversation State Management

- **COMMAREA preservation**: The entire CARDDEMO-COMMAREA plus WS-THIS-PROGCOMMAREA is serialized into WS-COMMAREA (PIC X(2000)) and passed on every RETURN TRANSID('CAVW'). CICS restores it automatically on the next dispatch.
- **Screen field state**: The ACCTSID field has BMS FSET attribute, ensuring the account number is always returned on RECEIVE even if the user does not re-type it.
- **No TSQ overflow**: All state fits within the COMMAREA; no temporary storage queues are used.
- **PGM-CONTEXT flag**: Distinguishes first entry (0 — display empty screen with prompt) from subsequent interactions (1 — process input and display results).
- **Lookup flags**: WS-ACCOUNT-MASTER-READ-FLAG and WS-CUST-MASTER-READ-FLAG track which files were successfully read, controlling which sections of the screen are populated in 1200-SETUP-SCREEN-VARS.

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path:
    Input → Validate Account# → READ CXACAIX → READ ACCTDAT → READ CUSTDAT → Display Details

  Input Validation Errors:
    Input → Validate ──BLANK──▶ "Account number not provided" / "No input received"
                                → Cursor to ACCTSID, RETURN TRANSID

    Input → Validate ──NOT NUMERIC / ZEROES──▶ "Account Filter must be a non-zero 11 digit number"
                                               → Cursor to ACCTSID, RETURN TRANSID

  Cross-Reference Lookup Error:
    Input → READ CXACAIX ──NOTFND──▶ "Account:<acctid> not found in Cross ref file. Resp:XX Reas:XX"
                                      → Cursor to ACCTSID, RETURN TRANSID

    Input → READ CXACAIX ──OTHER──▶  "File Error: READ on CXACAIX returned RESP XX,RESP2 XX"
                                      → Cursor to ACCTSID, RETURN TRANSID

  Account Master Lookup Error:
    Input → READ ACCTDAT ──NOTFND──▶ "Account:<acctid> not found in Acct Master file. Resp:XX Reas:XX"
                                      → Cursor to ACCTSID, RETURN TRANSID

    Input → READ ACCTDAT ──OTHER──▶  "File Error: READ on ACCTDAT returned RESP XX,RESP2 XX"
                                      → Cursor to ACCTSID, RETURN TRANSID

  Customer Master Lookup Error:
    Input → READ CUSTDAT ──NOTFND──▶ "CustId:<custid> not found in customer master. Resp:XX REAS:XX"
                                      → Cursor to ACCTSID, RETURN TRANSID

    Input → READ CUSTDAT ──OTHER──▶  "File Error: READ on CUSTDAT returned RESP XX,RESP2 XX"
                                      → Cursor to ACCTSID, RETURN TRANSID

  Unexpected Scenario:
    EVALUATE OTHER ──▶ "UNEXPECTED DATA SCENARIO" → SEND TEXT, RETURN (no TRANSID)

  Abend:
    HANDLE ABEND ──▶ "UNEXPECTED ABEND OCCURRED." → SEND ABEND-DATA, ABEND ABCODE('9999')
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| Account blank/spaces (re-entry) | — | SET INPUT-ERROR, FLG-ACCTFILTER-BLANK, cursor to ACCTSID | "Account number not provided" | — |
| Account blank (cross-field) | — | SET NO-SEARCH-CRITERIA-RECEIVED | "No input received" | — |
| Account not numeric or all zeros | — | SET INPUT-ERROR, FLG-ACCTFILTER-NOT-OK, cursor to ACCTSID | "Account Filter must be a non-zero 11 digit number" | — |
| Account not found in CXACAIX | NOTFND (13) | SET INPUT-ERROR, FLG-ACCTFILTER-NOT-OK | "Account:\<acctid\> not found in Cross ref file. Resp:\<resp\> Reas:\<reas\>" | — |
| CXACAIX read error | OTHER | SET INPUT-ERROR, FLG-ACCTFILTER-NOT-OK | "File Error: READ on CXACAIX returned RESP \<resp\>,RESP2 \<resp2\>" | — |
| Account not found in ACCTDAT | NOTFND (13) | SET INPUT-ERROR, FLG-ACCTFILTER-NOT-OK | "Account:\<acctid\> not found in Acct Master file. Resp:\<resp\> Reas:\<reas\>" | — |
| ACCTDAT read error | OTHER | SET INPUT-ERROR, FLG-ACCTFILTER-NOT-OK | "File Error: READ on ACCTDAT returned RESP \<resp\>,RESP2 \<resp2\>" | — |
| Customer not found in CUSTDAT | NOTFND (13) | SET INPUT-ERROR, FLG-CUSTFILTER-NOT-OK | "CustId:\<custid\> not found in customer master. Resp:\<resp\> REAS:\<reas\>" | — |
| CUSTDAT read error | OTHER | SET INPUT-ERROR, FLG-CUSTFILTER-NOT-OK | "File Error: READ on CUSTDAT returned RESP \<resp\>,RESP2 \<resp2\>" | — |
| Unexpected EVALUATE fall-through | — | SEND TEXT, RETURN (no TRANSID — terminates conversation) | "UNEXPECTED DATA SCENARIO" | — |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| ABEND (any) | ABEND-ROUTINE | Display ABEND-DATA (culprit program + reason), then ABEND ABCODE('9999') |

The ABEND-ROUTINE:
1. If ABEND-MSG is LOW-VALUES, set to "UNEXPECTED ABEND OCCURRED."
2. Set ABEND-CULPRIT to 'COACTVWC'
3. SEND FROM(ABEND-DATA) NOHANDLE — display abend information to terminal
4. HANDLE ABEND CANCEL — prevent recursive abend handling
5. ABEND ABCODE('9999') — force a controlled CICS abend

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "Enter or update id of account to display" | First entry or no info message set (prompt state) | INFOMSG (line 22, col 23) |
| — | "Displaying details of given Account" | Informational — after successful lookup (WS-INFORM-OUTPUT) | INFOMSG (line 22, col 23) |
| — | "Account number not provided" | Account field is blank/spaces on re-entry | ERRMSG (line 23, col 1) |
| — | "No input received" | Cross-field edit: account filter is blank | ERRMSG (line 23, col 1) |
| — | "Account Filter must be a non-zero 11 digit number" | Account is not numeric or is all zeros | ERRMSG (line 23, col 1) |
| — | "Account:\<acctid\> not found in Cross ref file. Resp:\<resp\> Reas:\<reas\>" | CXACAIX READ returns NOTFND | ERRMSG (line 23, col 1) |
| — | "File Error: READ on CXACAIX returned RESP \<resp\>,RESP2 \<resp2\>" | CXACAIX READ returns unexpected RESP code | ERRMSG (line 23, col 1) |
| — | "Account:\<acctid\> not found in Acct Master file. Resp:\<resp\> Reas:\<reas\>" | ACCTDAT READ returns NOTFND | ERRMSG (line 23, col 1) |
| — | "File Error: READ on ACCTDAT returned RESP \<resp\>,RESP2 \<resp2\>" | ACCTDAT READ returns unexpected RESP code | ERRMSG (line 23, col 1) |
| — | "CustId:\<custid\> not found in customer master. Resp:\<resp\> REAS:\<reas\>" | CUSTDAT READ returns NOTFND | ERRMSG (line 23, col 1) |
| — | "File Error: READ on CUSTDAT returned RESP \<resp\>,RESP2 \<resp2\>" | CUSTDAT READ returns unexpected RESP code | ERRMSG (line 23, col 1) |
| — | "UNEXPECTED DATA SCENARIO" | EVALUATE OTHER fall-through in main logic | Plain text (SEND TEXT) |
| — | "PF03 pressed.Exiting" | PF3 key press (WS-EXIT-MESSAGE — defined but not explicitly displayed before XCTL) | — |
| — | "UNEXPECTED ABEND OCCURRED." | ABEND-ROUTINE triggered | Plain text (SEND FROM ABEND-DATA) |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CAVW transaction must be authorized for user |
| Resource-level security | RACF FACILITY / File profiles for CXACAIX, ACCTDAT, CUSTDAT |
| Application-level check | If EIBCALEN = 0 (no COMMAREA — direct invocation without prior login), CARDDEMO-COMMAREA and WS-THIS-PROGCOMMAREA are initialized (effectively a clean start). The program does not XCTL to sign-on; it simply shows the empty screen. |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| CXACAIX file | READ | CICS File Control |
| ACCTDAT file | READ | CICS File Control |
| CUSTDAT file | READ | CICS File Control |

### 9.3 Audit Trail

| Event | Trigger | Data Logged | Destination |
|-------|---------|-------------|-------------|
| No explicit audit logging | — | — | Transaction may be audited via CICS journal/SMF records at the system level |
| Sensitive data displayed | Customer SSN, DOB, FICO score are displayed on screen | — | No application-level masking or logging of access |

---

## 10. Performance Characteristics

### 10.1 Performance Profile

| Metric | Baseline | Peak | SLA |
|--------|----------|------|-----|
| Response time | < 1 second | < 2 seconds | < 2 seconds |
| CPU per transaction | Very low (validation + 3 READs) | — | — |
| File I/O count | 3 per successful query (1 CXACAIX + 1 ACCTDAT + 1 CUSTDAT) | — | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal | — | — |

### 10.2 Performance Considerations

- **Three sequential READs**: The transaction performs three READ operations in sequence (CXACAIX → ACCTDAT → CUSTDAT). Each is a keyed READ on a VSAM KSDS, which is efficient. The sequential dependency is necessary because the customer ID is obtained from the cross-reference file.
- **Read-only access**: No file updates or locking overhead. All accesses are pure READ operations.
- **COMMAREA size**: WS-COMMAREA is PIC X(2000), which is generous for the amount of data stored. Most of the space is unused but does not cause performance issues.
- **BMS SEND/RECEIVE**: Standard map I/O with ERASE — one pair per pseudo-conversational iteration.
- **No sub-program calls**: Unlike COTRN02C which calls CSUTLDTC for date validation, this transaction has no CALL or LINK overhead.
- **Screen data volume**: The screen displays ~30 output fields, which is moderate. All data is populated from in-memory records after the file READs.

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Normal account view | Valid 11-digit Account ID that exists in all three files | All account and customer fields populated; info message "Displaying details of given Account" | High |
| TC-002 | Account number blank | Leave Account Number empty, press ENTER | "Account number not provided" / "No input received" in ERRMSG; cursor on ACCTSID | High |
| TC-003 | Account number non-numeric | Enter alphabetic characters in Account Number | "Account Filter must be a non-zero 11 digit number" in ERRMSG | High |
| TC-004 | Account number all zeros | Enter 00000000000 | "Account Filter must be a non-zero 11 digit number" in ERRMSG | High |
| TC-005 | Account not in cross-reference | Valid numeric account not in CXACAIX | "Account:<acctid> not found in Cross ref file..." in ERRMSG | High |
| TC-006 | Account not in account master | Account exists in CXACAIX but not in ACCTDAT | "Account:<acctid> not found in Acct Master file..." in ERRMSG | Medium |
| TC-007 | Customer not in customer master | Account + xref exist but customer ID not in CUSTDAT | "CustId:<custid> not found in customer master..." in ERRMSG | Medium |
| TC-008 | PF3 exit from first screen | Press PF3 on empty screen | XCTL to Main Menu (COMEN01C) | Medium |
| TC-009 | PF3 exit after viewing data | View an account, then press PF3 | XCTL to calling program or Main Menu | Medium |
| TC-010 | Re-query different account | View one account, then enter different Account Number | New account details displayed, replacing previous data | Medium |
| TC-011 | Invalid PF key (e.g., PF7) | Press PF7 | Treated as ENTER — normal processing occurs | Low |
| TC-012 | Account with asterisk in field | Account field shows '*' from previous blank entry | '*' replaced with LOW-VALUES, treated as blank → prompt for input | Low |
| TC-013 | First entry (EIBCALEN = 0) | Direct transaction invocation (no COMMAREA) | Empty screen with prompt "Enter or update id of account to display" | Low |
| TC-014 | Partial data — xref found, account found, customer not found | Valid account in CXACAIX and ACCTDAT, invalid customer ID | Account section populated but customer section empty; error message shown | Medium |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| CXACAIX | Pre-loaded with valid account-to-card cross-reference records with alternate index on account ID | VSAM DEFINE AIX + BLDINDEX over CARDXREF |
| ACCTDAT | Pre-loaded with account master records (300-byte records per CVACT01Y) | VSAM REPRO from test data (`app/data/`) |
| CUSTDAT | Pre-loaded with customer master records (500-byte records per CVCUS01Y) | VSAM REPRO from test data (`app/data/`) |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | Invalid PF keys are silently treated as ENTER rather than displaying an error message | User may be confused when pressing PF7, PF8, etc. triggers a query instead of showing "Invalid key" | Add explicit handling for unrecognized AID keys with an error message |
| KI-002 | Customer SSN is displayed on screen in XXX-XX-XXXX format with no masking | Security concern — full SSN visible to any operator with CAVW access | Implement partial masking (e.g., XXX-XX-1234) or role-based display |
| KI-003 | CUST-ADDR-LINE-3 is mapped to the "City" field on screen (ACSCITYO) | Address Line 3 may not always contain city name — semantic mismatch | Use a dedicated city field in the customer record or clarify mapping |
| KI-004 | When EIBCALEN = 0 (no COMMAREA), the program does not redirect to sign-on | A user could potentially access the screen directly without authentication | Add XCTL to COSGN00C when EIBCALEN = 0 (as other programs do) |
| KI-005 | Error messages for file not-found conditions include raw RESP/REAS codes | Technical RESP codes are not user-friendly | Replace with user-friendly messages (e.g., "Account not found. Please verify the account number.") |
| KI-006 | The account number field has MUSTFILL validation in BMS but the COBOL code also validates for blank — redundant validation | Minor — no functional impact | Remove one layer of validation for clarity |
| KI-007 | WS-INFORM-OUTPUT ("Displaying details of given Account") is defined as a 88-level value but is never explicitly SET in the code path after a successful read | The info message area may show the prompt message instead of confirmation after a successful lookup | Add SET WS-INFORM-OUTPUT TO TRUE after successful 9000-READ-ACCT |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Screen I/O | BMS Map CACTVWA / 3270 terminal | Web page or REST API GET endpoint returning JSON | Medium — many output fields to map |
| Data Access | Three sequential VSAM KSDS READs (CXACAIX, ACCTDAT, CUSTDAT) | Service layer with RDBMS JOIN (e.g., SELECT from account JOIN customer using xref) | Medium — three files could become one SQL query with JOINs |
| Validation | Inline COBOL EVALUATE/IF cascades | Framework validation (Bean Validation, JSON Schema) | Low — simple numeric/non-blank checks |
| State Management | COMMAREA pseudo-conversational | HTTP session or stateless REST (account ID in URL path) | Low — read-only transaction has minimal state |
| SSN Display | Full SSN shown in XXX-XX-XXXX format | Masked display (***-**-1234) with role-based full access | Low — UI-level change |
| Currency Formatting | BMS PICOUT '+ZZZ,ZZZ,ZZZ.99' | Client-side locale-aware number formatting | Low |
| Cross-Reference | VSAM alternate index (CXACAIX) for account→card lookup | SQL foreign key / JOIN | Low |
| Customer Lookup | Separate CUSTDAT READ using customer ID from XREF | SQL JOIN in single query (account JOIN xref JOIN customer) | Low — eliminates sequential file reads |
| Error Messages | Dynamic STRING with RESP/REAS codes | Externalized message catalog with user-friendly text | Low |
| Security | RACF transaction/resource security; no application-level audit | OAuth/RBAC with audit logging and data masking | Medium |

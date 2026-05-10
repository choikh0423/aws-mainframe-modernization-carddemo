# Add Transaction (CT02) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CT02 |
| Entry Program | COTRN02C |
| Business Domain | Transaction Processing |
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

- **What**: Allows an operator to add a new credit card transaction record to the TRANSACT VSAM file. The transaction captures all details of a purchase or payment including amount, merchant information, dates, and classification codes.
- **Who**: Any authenticated CardDemo user (regular users and admins) — menu option 8 ("Transaction Add").
- **When**: Real-time, on-demand during business hours when transactions need to be manually entered.
- **Why**: Supports manual transaction entry for cases where automated capture is unavailable (e.g., offline transactions, corrections, manual postings).
- **What** is the business outcome: A new transaction record is written to the TRANSACT VSAM file with a system-generated unique Transaction ID. The transaction will be picked up by the nightly batch cycle for posting to the account master.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  User selects Option 8 ("Transaction Add") from Main Menu
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 1            │  Enter Account ID or Card Number to identify the account
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ System Response          │  Cross-reference lookup validates account/card
  └────────────┬─────────────┘  and auto-fills the complementary field
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 2            │  Fill in transaction details:
  └────────────┬─────────────┘  Type CD, Category CD, Source, Description,
               │                Amount, Orig Date, Proc Date,
               │                Merchant ID/Name/City/Zip
               ▼
  ┌──────────────────────────┐
  │ System Validation        │  Validate all required fields, format checks
  └────────────┬─────────────┘  (numeric, date YYYY-MM-DD, amount sign format)
               │
           ┌───┴───┐
           │Decision│  Validation passes?
           └───┬───┘
              ╱ ╲
        Yes ╱     ╲ No
           ▼       ▼
  ┌──────────┐  ┌─────────────────┐
  │ Confirm? │  │ Error message,  │
  │  (Y/N)   │  │ cursor to field │
  └────┬─────┘  └─────────────────┘
       │
   ┌───┴───┐
   │  Y/N? │
   └───┬───┘
      ╱ ╲
Yes ╱     ╲ No
   ▼       ▼
┌───────────────────────┐  ┌──────────────────────────────────┐
│ Generate Transaction  │  │ "Confirm to add this             │
│ ID (last + 1), WRITE  │  │  transaction..." message         │
│ to TRANSACT file      │  └──────────────────────────────────┘
└───────────┬───────────┘
            ▼
  ┌──────────────────────────┐
  │ Business Outcome         │  "Transaction added successfully. Your Tran ID is XXXXXXXX."
  └──────────────────────────┘   Screen clears for next entry.
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | Account/Card identification required | Either Account ID or Card Number must be provided | Cross-reference lookup to resolve the counterpart field |
| BR-002 | Account/Card must exist | Account ID or Card Number must exist in the XREF files | Error: "Account ID NOT found..." or "Card Number NOT found..." |
| BR-003 | All transaction data fields are mandatory | Any of Type CD, Category CD, Source, Description, Amount, Orig Date, Proc Date, Merchant ID/Name/City/Zip is empty | Error with specific field message, cursor positioned to empty field |
| BR-004 | Numeric field validation | Account ID, Card Number, Type CD, Category CD, Merchant ID must be numeric | Error: "[Field] must be Numeric..." |
| BR-005 | Amount format | Amount must be in format ±99999999.99 | Error: "Amount should be in format -99999999.99" |
| BR-006 | Date format validation | Orig Date and Proc Date must be YYYY-MM-DD | Error: "[Date] should be in format YYYY-MM-DD" |
| BR-007 | Date validity | Orig Date and Proc Date must be valid calendar dates | CSUTLDTC utility validates; Error: "[Date] - Not a valid date..." |
| BR-008 | Confirmation required | User must confirm with 'Y' before write | Error: "Confirm to add this transaction..." |
| BR-009 | Auto-generated Transaction ID | New Tran ID = highest existing Tran ID + 1 | STARTBR with HIGH-VALUES, READPREV to find max, add 1 |
| BR-010 | Copy last transaction data | User presses PF5 | Copy data fields from the most recent transaction record (except key fields) |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Select Option 8 from Main Menu | XCTL to COTRN02C; display empty Add Transaction screen (COTRN2A) | Navigate to transaction entry |
| 2 | Enter Account ID (or Card Number) | Cross-reference lookup; auto-fill Card Number (or Account ID) | Identify target account |
| 3 | Fill transaction detail fields | Client-side field capture (FSET) | Capture transaction data |
| 4 | Press ENTER | Validate all fields; if valid, prompt for confirmation | Submit for processing |
| 5a | Type 'Y' in Confirm field, press ENTER | Generate Tran ID, WRITE record, display success message, clear screen | Transaction committed |
| 5b | Type 'N' or leave blank, press ENTER | Display "Confirm to add this transaction..." | User defers or cancels confirmation |
| Alt-A | Press PF3 | XCTL back to previous screen (or Main Menu COMEN01C) | Exit without saving |
| Alt-B | Press PF4 | Clear all input fields | Reset form |
| Alt-C | Press PF5 | Copy data fields from last transaction in file, then process ENTER | Rapid duplicate entry |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CT02
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COTRN02C     │ (Entry & only online program)      │
  │  │ (Add Tran)   │                                    │
  │  └──┬────┬──┬───┘                                    │
  │     │    │  │                                         │
  │     ▼    │  ▼                                         │
  │  ┌──────┐│ ┌────────────────────────────────────┐    │
  │  │BMS   ││ │ VSAM Files                         │    │
  │  │MAP:  ││ │  CXACAIX (Acct→Card XREF AIX)     │    │
  │  │COTRN ││ │  CCXREF  (Card→Acct XREF)         │    │
  │  │2A    ││ │  TRANSACT (Transaction master)     │    │
  │  └──────┘│ └────────────────────────────────────┘    │
  │          │                                            │
  │          ▼                                            │
  │  ┌─────────────┐                                     │
  │  │ CSUTLDTC    │ (Date validation utility - CALL)    │
  │  └─────────────┘                                     │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1 (First entry — from Main Menu via XCTL):
  1. COMEN01C XCTLs to COTRN02C with CARDDEMO-COMMAREA
  2. EIBCALEN > 0; CDEMO-PGM-REENTER = 0 (first entry)
  3. SET CDEMO-PGM-REENTER TO TRUE
  4. MOVE LOW-VALUES TO COTRN2AO (clear output map)
  5. Set cursor to Account ID field (ACTIDINL = -1)
  6. If CDEMO-CT02-TRN-SELECTED populated (from Transaction List):
     └── Pre-fill Card Number, PERFORM PROCESS-ENTER-KEY
  7. PERFORM SEND-TRNADD-SCREEN
     └── SEND MAP('COTRN2A') MAPSET('COTRN02') ERASE CURSOR
  8. RETURN TRANSID('CT02') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 2 (User submits data — ENTER pressed):
  9.  CICS dispatches → COTRN02C (COMMAREA restored)
  10. EIBCALEN > 0; CDEMO-PGM-REENTER = 1
  11. RECEIVE MAP('COTRN2A') INTO(COTRN2AI)
  12. EIBAID = DFHENTER → PERFORM PROCESS-ENTER-KEY
  13. PERFORM VALIDATE-INPUT-KEY-FIELDS
      ├── Account ID entered → validate numeric, READ CXACAIX for card#
      ├── Card Number entered → validate numeric, READ CCXREF for acct#
      └── Neither entered → error "Account or Card Number must be entered..."
  14. PERFORM VALIDATE-INPUT-DATA-FIELDS
      ├── Check each field is non-empty (Type CD, Cat CD, Source, Desc, Amt,
      │   Orig Date, Proc Date, Merchant ID/Name/City/Zip)
      ├── Check numeric fields (Type CD, Cat CD, Merchant ID)
      ├── Check amount format (±99999999.99)
      ├── Validate date formats (YYYY-MM-DD)
      └── CALL 'CSUTLDTC' for calendar date validation
  15. Check CONFIRM field:
      ├── 'Y'/'y' → PERFORM ADD-TRANSACTION
      ├── 'N'/'n'/spaces → "Confirm to add this transaction..."
      └── Other → "Invalid value. Valid values are (Y/N)..."

  Iteration 2a (ADD-TRANSACTION — when confirmed):
  16. MOVE HIGH-VALUES TO TRAN-ID
  17. STARTBR TRANSACT file (position to end)
  18. READPREV to get last (highest) TRAN-ID
  19. ENDBR to close browse
  20. ADD 1 to highest TRAN-ID → new unique TRAN-ID
  21. INITIALIZE TRAN-RECORD
  22. Populate all TRAN-RECORD fields from screen input
  23. WRITE TRANSACT file with new TRAN-RECORD
      ├── NORMAL → clear screen, display success with Tran ID
      ├── DUPREC → "Tran ID already exist..."
      └── OTHER → "Unable to Add Transaction..."
  24. RETURN TRANSID('CT02') COMMAREA(CARDDEMO-COMMAREA)
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | >0 | XCTL from menu (option 8) | Initial logic (PGM-ENTER) | COTRN2A (empty form) | CT02 |
| 2 | >0 | Fill fields, press ENTER | RECEIVE → PROCESS-ENTER-KEY | COTRN2A (with errors or confirm prompt) | CT02 |
| 3 | >0 | Confirm Y + ENTER | PROCESS-ENTER-KEY → ADD-TRANSACTION | COTRN2A (success message, cleared) | CT02 |
| N | >0 | PF3 (Exit) | XCTL to COMEN01C (menu) | — | — (XCTL) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | RECEIVE MAP('COTRN2A') MAPSET('COTRN02') | Capture user input from terminal | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 2 | READ DATASET('CXACAIX') RIDFLD(XREF-ACCT-ID) | Lookup card number by account ID | NORMAL → continue; NOTFND → error; OTHER → error |
| 3 | READ DATASET('CCXREF') RIDFLD(XREF-CARD-NUM) | Lookup account ID by card number | NORMAL → continue; NOTFND → error; OTHER → error |
| 4 | STARTBR DATASET('TRANSACT') RIDFLD(TRAN-ID) | Position browse at end of file (HIGH-VALUES) | NORMAL → continue; NOTFND/OTHER → error |
| 5 | READPREV DATASET('TRANSACT') | Read last (highest) transaction record | NORMAL → got last ID; ENDFILE → set TRAN-ID to ZEROS |
| 6 | ENDBR DATASET('TRANSACT') | End browse session | — |
| 7 | WRITE DATASET('TRANSACT') FROM(TRAN-RECORD) | Write new transaction record | NORMAL → success; DUPREC → error; OTHER → error |
| 8 | SEND MAP('COTRN2A') MAPSET('COTRN02') ERASE CURSOR | Send screen to terminal | — |
| 9 | RETURN TRANSID('CT02') COMMAREA(CARDDEMO-COMMAREA) | Pseudo-conversational return | — |
| 10 | XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA | Transfer to previous screen on PF3 | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────┐                ┌─────────────────────────────────────┐
  │  Main Menu  │   Option 8     │  COTRN2A (Add Transaction)          │
  │  (COMEN01C) │───────────────▶│                                     │
  └─────────────┘                │  Account ID: ___________  (or)      │
        ▲                        │  Card #:     ________________       │
        │ PF3                    │  ─────────────────────────────────   │
        │                        │  Type CD: __  Cat CD: ____          │
        │                        │  Source: __________                  │
        │                        │  Description: ____________________  │
        │                        │  Amount: ____________               │
        │                        │  Orig Date: __________              │
        │                        │  Proc Date: __________              │
        │                        │  Merchant ID: _________             │
        │                        │  Merchant Name: ______              │
        │                        │  Merchant City: _____               │
        │                        │  Merchant Zip:  __________          │
        │                        │  Confirm: _ (Y/N)                   │
        │                        │  [Error/Success message line]       │
        │                        │  ENTER=Continue F3=Back F4=Clear    │
        │                        │  F5=Copy Last Tran.                 │
        └────────────────────────┤                                     │
                                 └─────────────────────────────────────┘
                                   │         │
                                   │ ENTER   │ PF4 (Clear)
                                   │ (loops  │ (resets form,
                                   │  back   │  loops back)
                                   │  to     │
                                   │  self)  │
                                   ▼         ▼
                                 [Same screen — COTRN2A]
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| COTRN2A | COTRN02 | Add Transaction input/confirm screen | Account ID, Card#, Type CD, Category CD, Source, Description, Amount, Orig Date, Proc Date, Merchant ID/Name/City/Zip, Confirm | ENTER=Submit, PF3=Back, PF4=Clear, PF5=Copy Last Tran |

### 3.3 Screen Field Details

#### Map: COTRN2A

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction name (CT02) |
| TITLE01 | ASKIP,FSET,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,FSET,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | ASKIP,FSET,NORM | 8 | Alpha | — | Program name (COTRN02C) |
| TITLE02 | ASKIP,FSET,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | ASKIP,FSET,NORM | 8 | Time | — | Current time hh:mm:ss |
| ACTIDIN | FSET,IC,NORM,UNPROT | 11 | Numeric | Must be numeric; must exist in CXACAIX | Account ID |
| CARDNIN | FSET,NORM,UNPROT | 16 | Numeric | Must be numeric; must exist in CCXREF | Card Number |
| TTYPCD | FSET,NORM,UNPROT | 2 | Numeric | Non-empty, numeric | Transaction Type Code |
| TCATCD | FSET,NORM,UNPROT | 4 | Numeric | Non-empty, numeric | Transaction Category Code |
| TRNSRC | FSET,NORM,UNPROT | 10 | Alpha | Non-empty | Transaction Source |
| TDESC | FSET,NORM,UNPROT | 60 | AlphaNum | Non-empty | Transaction Description |
| TRNAMT | FSET,NORM,UNPROT | 12 | Signed Decimal | Non-empty, format ±99999999.99 | Transaction Amount |
| TORIGDT | FSET,NORM,UNPROT | 10 | Date | Non-empty, YYYY-MM-DD, valid calendar date | Origination Date |
| TPROCDT | FSET,NORM,UNPROT | 10 | Date | Non-empty, YYYY-MM-DD, valid calendar date | Processing Date |
| MID | FSET,NORM,UNPROT | 9 | Numeric | Non-empty, numeric | Merchant ID |
| MNAME | FSET,NORM,UNPROT | 30 | Alpha | Non-empty | Merchant Name |
| MCITY | FSET,NORM,UNPROT | 25 | Alpha | Non-empty | Merchant City |
| MZIP | FSET,NORM,UNPROT | 10 | Alpha | Non-empty | Merchant Zip Code |
| CONFIRM | FSET,NORM,UNPROT | 1 | Alpha | Y/N only | Confirmation flag |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/success message display |

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | COTRN2A | Validate input fields; if valid and confirmed, write transaction |
| PF3 | COTRN2A | Return to previous screen (CDEMO-FROM-PROGRAM or Main Menu COMEN01C) via XCTL |
| PF4 | COTRN2A | Clear all input fields (INITIALIZE-ALL-FIELDS), redisplay empty form |
| PF5 | COTRN2A | Copy data fields from the last transaction record in TRANSACT file, then process as ENTER |
| CLEAR | COTRN2A | Not explicitly handled — falls to "Invalid key pressed" |
| OTHER | COTRN2A | Display "Invalid key pressed. Please see below..." |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COTRN02C | Add Transaction — screen handler, validation, VSAM write | TRANSID CT02 / XCTL from COMEN01C | 783 | `app/cbl/COTRN02C.cbl` |
| CSUTLDTC | Date validation utility — validates date strings against calendar | CALL from COTRN02C | ~100 | `app/cbl/CSUTLDTC.cbl` |
| COMEN01C | Main Menu — entry point for option 8 | XCTL (caller) | — | `app/cbl/COMEN01C.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CT02
└── COTRN02C (Entry — Screen Handler, Validation, Data Access)
    ├── CALL → CSUTLDTC (Date validation utility)
    │   ├── Validates Origination Date (TORIGDTI)
    │   └── Validates Processing Date (TPROCDTI)
    ├── READ FILE('CXACAIX') — Account-to-Card cross-reference lookup
    ├── READ FILE('CCXREF')  — Card-to-Account cross-reference lookup
    ├── STARTBR FILE('TRANSACT') — Browse to find max Tran ID
    ├── READPREV FILE('TRANSACT') — Read last record for ID generation
    ├── ENDBR FILE('TRANSACT')
    ├── WRITE FILE('TRANSACT') — Write new transaction record
    ├── SEND MAP('COTRN2A') — Display screen
    ├── RECEIVE MAP('COTRN2A') — Capture input
    ├── RETURN TRANSID('CT02') — Pseudo-conversational return
    └── XCTL → COMEN01C or CDEMO-FROM-PROGRAM (on PF3 exit)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COMEN01C | COTRN02C | XCTL | CARDDEMO-COMMAREA | — | User selects menu option 8 |
| COTRN02C | CSUTLDTC | CALL | CSUTLDTC-DATE, CSUTLDTC-DATE-FORMAT | CSUTLDTC-RESULT (sev code, msg) | Date validation of Orig/Proc dates |
| COTRN02C | COMEN01C | XCTL | CARDDEMO-COMMAREA | — | PF3 exit back to main menu |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| Card Cross-Reference (AIX) | CXACAIX | VSAM KSDS (Alternate Index) | READ | COTRN02C | Lookup card number by account ID |
| Card Cross-Reference | CCXREF | VSAM KSDS | READ | COTRN02C | Lookup account ID by card number |
| Transaction Master | TRANSACT | VSAM KSDS | BROWSE / WRITE | COTRN02C | Read last ID for generation; write new transaction record |

### 5.2 File Access Details

#### File: CXACAIX (Account-to-Card Cross-Reference AIX)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS (Alternate Index over CCXREF) |
| Record Layout (Copybook) | CVACT03Y (CARD-XREF-RECORD, 50 bytes) |
| Key | XREF-ACCT-ID (PIC 9(11), offset via alternate index) |
| Access Mode | READ |
| Locking | No update, read-only |
| Shared With | COTRN01C (View Transaction), COTRN00C (List Transactions) |

**Access Pattern**:
| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COTRN02C | READ DATASET('CXACAIX') RIDFLD(XREF-ACCT-ID) | Account ID from screen (ACTIDINI) | Returns XREF-CARD-NUM |

#### File: CCXREF (Card-to-Account Cross-Reference)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CVACT03Y (CARD-XREF-RECORD, 50 bytes) |
| Key | XREF-CARD-NUM (PIC X(16)) |
| Access Mode | READ |
| Locking | No update, read-only |
| Shared With | Multiple CardDemo programs |

**Access Pattern**:
| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COTRN02C | READ DATASET('CCXREF') RIDFLD(XREF-CARD-NUM) | Card Number from screen (CARDNINI) | Returns XREF-ACCT-ID |

#### File: TRANSACT (Transaction Master)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CVTRA05Y (TRAN-RECORD, 350 bytes) |
| Key | TRAN-ID (PIC X(16)) |
| Access Mode | BROWSE (STARTBR/READPREV/ENDBR) and WRITE |
| Locking | Record-level for WRITE |
| Shared With | COTRN00C (List), COTRN01C (View), Batch Post-Tran |

**Access Pattern**:
| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COTRN02C | STARTBR RIDFLD(TRAN-ID = HIGH-VALUES) | Position to end of file | — |
| 2 | COTRN02C | READPREV INTO(TRAN-RECORD) | Read last record backwards | TRAN-ID (to derive next ID) |
| 3 | COTRN02C | ENDBR | Close browse | — |
| 4 | COTRN02C | WRITE FROM(TRAN-RECORD) RIDFLD(TRAN-ID) | New TRAN-ID = last + 1 | All TRAN-RECORD fields |

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
  Terminal Input (COTRN2A)
       │
       ├── ACTIDINI (Account ID)
       │       │
       │       └──▶ WS-ACCT-ID-N → XREF-ACCT-ID
       │                 │
       │                 └──▶ READ CXACAIX → CARD-XREF-RECORD
       │                          │
       │                          └──▶ XREF-CARD-NUM → CARDNINI (auto-fill Card #)
       │
       ├── CARDNINI (Card Number) [alternative path]
       │       │
       │       └──▶ WS-CARD-NUM-N → XREF-CARD-NUM
       │                 │
       │                 └──▶ READ CCXREF → CARD-XREF-RECORD
       │                          │
       │                          └──▶ XREF-ACCT-ID → ACTIDINI (auto-fill Acct ID)
       │
       ├── Data Fields (TTYPCDI, TCATCDI, TRNSRCI, TDESCI, TRNAMTI,
       │   TORIGDTI, TPROCDTI, MIDI, MNAMEI, MCITYI, MZIPI)
       │       │
       │       ├──▶ Validation (format, numeric, date checks)
       │       │       │
       │       │       └──▶ CALL CSUTLDTC (date validation)
       │       │
       │       └──▶ TRAN-RECORD fields (after confirmation)
       │                 │
       │                 └──▶ WRITE to TRANSACT VSAM file
       │
       └── CONFIRMI ('Y'/'N')
               │
               └──▶ Controls whether ADD-TRANSACTION executes

  Transaction ID Generation:
       STARTBR TRANSACT (HIGH-VALUES)
            │
            └──▶ READPREV → last TRAN-ID
                      │
                      └──▶ WS-TRAN-ID-N + 1 → new TRAN-ID
                                │
                                └──▶ TRAN-RECORD.TRAN-ID
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| ACTIDINI | Terminal (COTRN2AI) | NUMVAL → PIC 9(11) | XREF-ACCT-ID | READ key for CXACAIX |
| CARDNINI | Terminal (COTRN2AI) | NUMVAL → PIC 9(16) | XREF-CARD-NUM | READ key for CCXREF |
| TRNAMTI | Terminal (COTRN2AI) | NUMVAL-C → PIC S9(9)V99 | TRAN-AMT | TRAN-RECORD field |
| TRNAMTI | Terminal (COTRN2AI) | NUMVAL-C → WS-TRAN-AMT-E (formatted) | TRNAMTI | Redisplayed on screen |
| TORIGDTI | Terminal (COTRN2AI) | Direct move (X(10)) | TRAN-ORIG-TS | TRAN-RECORD field (first 10 of 26) |
| TPROCDTI | Terminal (COTRN2AI) | Direct move (X(10)) | TRAN-PROC-TS | TRAN-RECORD field (first 10 of 26) |
| TTYPCDI | Terminal (COTRN2AI) | Direct move | TRAN-TYPE-CD | TRAN-RECORD |
| TCATCDI | Terminal (COTRN2AI) | Direct move | TRAN-CAT-CD | TRAN-RECORD |
| TRNSRCI | Terminal (COTRN2AI) | Direct move | TRAN-SOURCE | TRAN-RECORD |
| TDESCI | Terminal (COTRN2AI) | Direct move | TRAN-DESC | TRAN-RECORD |
| MIDI | Terminal (COTRN2AI) | Direct move | TRAN-MERCHANT-ID | TRAN-RECORD |
| MNAMEI | Terminal (COTRN2AI) | Direct move | TRAN-MERCHANT-NAME | TRAN-RECORD |
| MCITYI | Terminal (COTRN2AI) | Direct move | TRAN-MERCHANT-CITY | TRAN-RECORD |
| MZIPI | Terminal (COTRN2AI) | Direct move | TRAN-MERCHANT-ZIP | TRAN-RECORD |
| Last TRAN-ID | READPREV TRANSACT | ADD 1 | New TRAN-ID | TRAN-RECORD.TRAN-ID |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`) plus a program-specific extension `CDEMO-CT02-INFO`:

**CARDDEMO-COMMAREA (COCOM01Y — shared portion):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-FROM-TRANID | X(04) | 4 | IN/OUT | Originating transaction ID |
| CDEMO-FROM-PROGRAM | X(08) | 8 | IN/OUT | Originating program name |
| CDEMO-TO-TRANID | X(04) | 4 | OUT | Target transaction ID |
| CDEMO-TO-PROGRAM | X(08) | 8 | OUT | Target program for XCTL |
| CDEMO-USER-ID | X(08) | 8 | IN | Authenticated user ID |
| CDEMO-USER-TYPE | X(01) | 1 | IN | 'A' = Admin, 'U' = User |
| CDEMO-PGM-CONTEXT | 9(01) | 1 | IN/OUT | 0 = first entry, 1 = re-entry |
| CDEMO-CUST-ID | 9(09) | 9 | IN | Customer ID |
| CDEMO-ACCT-ID | 9(11) | 11 | IN | Account ID |
| CDEMO-CARD-NUM | 9(16) | 16 | IN | Card Number |
| CDEMO-LAST-MAP | X(7) | 7 | IN/OUT | Last BMS map displayed |
| CDEMO-LAST-MAPSET | X(7) | 7 | IN/OUT | Last BMS mapset used |

**CDEMO-CT02-INFO (program-specific extension, defined inline in COTRN02C):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-CT02-TRNID-FIRST | X(16) | 16 | IN/OUT | First transaction ID (pagination context) |
| CDEMO-CT02-TRNID-LAST | X(16) | 16 | IN/OUT | Last transaction ID (pagination context) |
| CDEMO-CT02-PAGE-NUM | 9(08) | 8 | IN/OUT | Page number |
| CDEMO-CT02-NEXT-PAGE-FLG | X(01) | 1 | IN/OUT | 'Y'/'N' — more pages available |
| CDEMO-CT02-TRN-SEL-FLG | X(01) | 1 | IN | Transaction selection flag |
| CDEMO-CT02-TRN-SELECTED | X(16) | 16 | IN | Pre-selected transaction (from list screen) |

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. Errors are handled inline via screen messages (WS-MESSAGE → ERRMSGO). The CSUTLDTC date utility uses:

| Code | Meaning | User Display |
|------|---------|-------------|
| CSUTLDTC-RESULT-SEV-CD = '0000' | Date is valid | — (continue processing) |
| CSUTLDTC-RESULT-SEV-CD != '0000' and MSG-NUM != '2513' | Invalid date | "Orig Date - Not a valid date..." / "Proc Date - Not a valid date..." |

### 7.3 Conversation State Management

- **COMMAREA preservation**: The entire CARDDEMO-COMMAREA (including CT02-specific fields) is passed on every RETURN TRANSID('CT02'). CICS restores it automatically on the next dispatch.
- **Screen field state**: BMS FSET attribute ensures all fields are returned on every RECEIVE, preserving user-entered data across iterations.
- **No TSQ overflow**: All state fits within the COMMAREA; no temporary storage queues are used.
- **PGM-CONTEXT flag**: Distinguishes first entry (0 — display empty screen) from subsequent interactions (1 — process input).

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path:
    Input → Validate Key Fields → Validate Data Fields → Confirm 'Y' → Generate ID → WRITE → Success

  Validation Error (Key Fields):
    Input → Validate ──FAIL──▶ "Account or Card Number must be entered..." / 
                                "Account ID must be Numeric..." /
                                "Card Number must be Numeric..."
                                → Cursor to field, RETURN TRANSID

  XREF Lookup Error:
    Input → READ CXACAIX/CCXREF ──NOTFND──▶ "Account ID NOT found..." /
                                             "Card Number NOT found..."
                                             → Cursor to field, RETURN TRANSID

  Data Validation Error:
    Input → Validate Data ──FAIL──▶ "[Field] can NOT be empty..." /
                                    "[Field] must be Numeric..." /
                                    "Amount should be in format..." /
                                    "Date should be in format YYYY-MM-DD" /
                                    "Date - Not a valid date..."
                                    → Cursor to field, RETURN TRANSID

  Confirmation Error:
    Input → Confirm = 'N'/spaces ──▶ "Confirm to add this transaction..."
    Input → Confirm = other     ──▶ "Invalid value. Valid values are (Y/N)..."

  Write Error:
    WRITE ──DUPREC──▶ "Tran ID already exist..."
    WRITE ──OTHER──▶  "Unable to Add Transaction..."
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| Account not found in CXACAIX | NOTFND (13) | Set ERR-FLG, cursor to ACTIDIN | "Account ID NOT found..." | — |
| Card not found in CCXREF | NOTFND (13) | Set ERR-FLG, cursor to CARDNIN | "Card Number NOT found..." | — |
| CXACAIX read error | OTHER | Set ERR-FLG, cursor to ACTIDIN | "Unable to lookup Acct in XREF AIX file..." | DISPLAY RESP/REAS to SYSOUT |
| CCXREF read error | OTHER | Set ERR-FLG, cursor to CARDNIN | "Unable to lookup Card # in XREF file..." | DISPLAY RESP/REAS to SYSOUT |
| TRANSACT browse error (STARTBR) | NOTFND/OTHER | Set ERR-FLG | "Transaction ID NOT found..." / "Unable to lookup Transaction..." | DISPLAY RESP/REAS to SYSOUT |
| TRANSACT READPREV error | OTHER | Set ERR-FLG | "Unable to lookup Transaction..." | DISPLAY RESP/REAS to SYSOUT |
| TRANSACT READPREV ENDFILE | ENDFILE | Set TRAN-ID = ZEROS (first record) | — | — |
| Duplicate transaction ID | DUPREC/DUPKEY | Set ERR-FLG | "Tran ID already exist..." | — |
| TRANSACT WRITE error | OTHER | Set ERR-FLG | "Unable to Add Transaction..." | DISPLAY RESP/REAS to SYSOUT |
| Map fail | MAPFAIL | Not explicitly handled | — | — |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| — | No explicit HANDLE ABEND | Default CICS abend handling applies |

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "Account or Card Number must be entered..." | Neither ACTIDIN nor CARDNIN provided | ERRMSG (line 23, col 1) |
| — | "Account ID must be Numeric..." | ACTIDINI is not numeric | ERRMSG (line 23, col 1) |
| — | "Card Number must be Numeric..." | CARDNINI is not numeric | ERRMSG (line 23, col 1) |
| — | "Account ID NOT found..." | CXACAIX READ returns NOTFND | ERRMSG (line 23, col 1) |
| — | "Card Number NOT found..." | CCXREF READ returns NOTFND | ERRMSG (line 23, col 1) |
| — | "Type CD can NOT be empty..." | TTYPCDI is spaces/low-values | ERRMSG (line 23, col 1) |
| — | "Category CD can NOT be empty..." | TCATCDI is spaces/low-values | ERRMSG (line 23, col 1) |
| — | "Source can NOT be empty..." | TRNSRCI is spaces/low-values | ERRMSG (line 23, col 1) |
| — | "Description can NOT be empty..." | TDESCI is spaces/low-values | ERRMSG (line 23, col 1) |
| — | "Amount can NOT be empty..." | TRNAMTI is spaces/low-values | ERRMSG (line 23, col 1) |
| — | "Orig Date can NOT be empty..." | TORIGDTI is spaces/low-values | ERRMSG (line 23, col 1) |
| — | "Proc Date can NOT be empty..." | TPROCDTI is spaces/low-values | ERRMSG (line 23, col 1) |
| — | "Merchant ID can NOT be empty..." | MIDI is spaces/low-values | ERRMSG (line 23, col 1) |
| — | "Merchant Name can NOT be empty..." | MNAMEI is spaces/low-values | ERRMSG (line 23, col 1) |
| — | "Merchant City can NOT be empty..." | MCITYI is spaces/low-values | ERRMSG (line 23, col 1) |
| — | "Merchant Zip can NOT be empty..." | MZIPI is spaces/low-values | ERRMSG (line 23, col 1) |
| — | "Type CD must be Numeric..." | TTYPCDI is not numeric | ERRMSG (line 23, col 1) |
| — | "Category CD must be Numeric..." | TCATCDI is not numeric | ERRMSG (line 23, col 1) |
| — | "Amount should be in format -99999999.99" | Amount fails format check | ERRMSG (line 23, col 1) |
| — | "Orig Date should be in format YYYY-MM-DD" | Orig Date fails format check | ERRMSG (line 23, col 1) |
| — | "Proc Date should be in format YYYY-MM-DD" | Proc Date fails format check | ERRMSG (line 23, col 1) |
| — | "Orig Date - Not a valid date..." | CSUTLDTC rejects origination date | ERRMSG (line 23, col 1) |
| — | "Proc Date - Not a valid date..." | CSUTLDTC rejects processing date | ERRMSG (line 23, col 1) |
| — | "Merchant ID must be Numeric..." | MIDI is not numeric | ERRMSG (line 23, col 1) |
| — | "Confirm to add this transaction..." | CONFIRM = 'N'/spaces/low-values | ERRMSG (line 23, col 1) |
| — | "Invalid value. Valid values are (Y/N)..." | CONFIRM is not Y/N/space | ERRMSG (line 23, col 1) |
| — | "Transaction added successfully. Your Tran ID is XXXX." | Successful WRITE | ERRMSG (line 23, col 1), color GREEN |
| — | "Tran ID already exist..." | WRITE returns DUPREC | ERRMSG (line 23, col 1) |
| — | "Unable to Add Transaction..." | WRITE returns unexpected error | ERRMSG (line 23, col 1) |
| — | "Invalid key pressed. Please see below..." | Unrecognized AID key | ERRMSG (line 23, col 1) |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CT02 transaction must be authorized for user |
| Resource-level security | RACF FACILITY / File profiles for TRANSACT, CCXREF, CXACAIX |
| Application-level check | If EIBCALEN = 0 (no COMMAREA — direct invocation without login), XCTL to sign-on screen COSGN00C |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| TRANSACT file | READ and UPDATE (for WRITE) | CICS File Control |
| CCXREF file | READ | CICS File Control |
| CXACAIX file | READ | CICS File Control |

### 9.3 Audit Trail

| Event | Trigger | Data Logged | Destination |
|-------|---------|-------------|-------------|
| Unexpected RESP codes | File I/O error (non-NORMAL, non-NOTFND) | RESP code, REAS code | DISPLAY → SYSOUT (CICS system log) |
| No explicit audit logging | — | — | Transaction may be audited via CICS journal/SMF records at the system level |

---

## 10. Performance Characteristics

### 10.1 Performance Profile

| Metric | Baseline | Peak | SLA |
|--------|----------|------|-----|
| Response time | < 1 second | < 2 seconds | < 2 seconds |
| CPU per transaction | Low (validation + 1 WRITE) | — | — |
| File I/O count | 4–5 per add (1 XREF READ + STARTBR + READPREV + ENDBR + WRITE) | — | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal | — | — |

### 10.2 Performance Considerations

- **STARTBR/READPREV pattern**: Browse to HIGH-VALUES then READPREV to find the maximum TRAN-ID. This is efficient for VSAM KSDS but could be a bottleneck under high-concurrency inserts if multiple users attempt to add transactions simultaneously (potential for duplicate key collisions).
- **Single WRITE per transaction**: Minimal I/O overhead for the actual insert.
- **XREF lookup**: One READ per key field entry — acceptable for online response times.
- **COMMAREA size**: Relatively small; no TSQ overflow needed.
- **BMS SEND/RECEIVE**: Standard map I/O with ERASE — one pair per pseudo-conversational iteration.
- **Date validation**: Two CALL invocations to CSUTLDTC per submit — negligible overhead.

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Normal add by Account ID | Valid Account ID, all data fields, Confirm=Y | "Transaction added successfully. Your Tran ID is XXXX." | High |
| TC-002 | Normal add by Card Number | Valid Card Number, all data fields, Confirm=Y | "Transaction added successfully. Your Tran ID is XXXX." | High |
| TC-003 | Missing Account and Card | Leave both blank, press ENTER | "Account or Card Number must be entered..." | High |
| TC-004 | Non-numeric Account ID | Enter alpha characters in Account ID | "Account ID must be Numeric..." | High |
| TC-005 | Account not found | Valid numeric but non-existent Account ID | "Account ID NOT found..." | High |
| TC-006 | Card not found | Valid numeric but non-existent Card Number | "Card Number NOT found..." | High |
| TC-007 | Empty required field | Leave any data field blank | "[Field] can NOT be empty..." | Medium |
| TC-008 | Invalid amount format | Enter "ABC" in Amount | "Amount should be in format -99999999.99" | Medium |
| TC-009 | Invalid date format | Enter "12/31/2025" instead of "2025-12-31" | "Orig/Proc Date should be in format YYYY-MM-DD" | Medium |
| TC-010 | Invalid calendar date | Enter "2025-02-30" | "Orig/Proc Date - Not a valid date..." | Medium |
| TC-011 | Confirm N | Fill all fields correctly, Confirm=N | "Confirm to add this transaction..." | Medium |
| TC-012 | Confirm invalid | Fill all fields, Confirm=X | "Invalid value. Valid values are (Y/N)..." | Medium |
| TC-013 | PF3 exit | Press PF3 at any point | Return to Main Menu (COMEN01C) | Medium |
| TC-014 | PF4 clear | Press PF4 with data on screen | All fields cleared | Low |
| TC-015 | PF5 copy last transaction | Press PF5 with valid Account/Card | Data fields populated from last TRANSACT record | Medium |
| TC-016 | Invalid PF key | Press PF2, PF6, etc. | "Invalid key pressed. Please see below..." | Low |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| CCXREF | Pre-loaded with valid card-to-account cross-reference records | VSAM REPRO from test data (`app/data/`) |
| CXACAIX | Alternate index built over CCXREF by account ID | VSAM DEFINE AIX + BLDINDEX |
| TRANSACT | Pre-loaded with at least one existing transaction record (for ID generation) | VSAM REPRO from test data |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | Transaction ID generation is not atomic — concurrent adds could generate duplicate IDs | DUPREC error under concurrent usage | Single-user operation or implement a sequence counter |
| KI-002 | TRAN-ORIG-TS and TRAN-PROC-TS are defined as PIC X(26) timestamps in the record layout, but only 10 characters (YYYY-MM-DD) are populated from the screen | Timestamp fields are partially filled (remaining 16 chars are spaces) | Full timestamp population would require additional logic |
| KI-003 | No explicit HANDLE ABEND — unexpected abends use default CICS handling | User sees CICS ABEND screen rather than a friendly error | Add HANDLE ABEND with a user-friendly error screen |
| KI-004 | Validation uses cascading EVALUATE with PERFORM SEND-TRNADD-SCREEN that issues RETURN — only the first validation error is reported per iteration | User must fix errors one at a time | Accumulate all errors before displaying |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Screen I/O | BMS Map COTRN2A / 3270 terminal | Web form / REST API POST endpoint | Medium — field mapping is 1:1 |
| Data Access | VSAM KSDS direct (TRANSACT, CCXREF, CXACAIX) | Service layer with RDBMS (e.g., RDS PostgreSQL) | Medium — simple READ/WRITE operations |
| ID Generation | STARTBR/READPREV + ADD 1 pattern | Database sequence or UUID | Low — replace with `NEXTVAL` or UUID generator |
| Validation | Inline COBOL EVALUATE cascades | Framework validation (Bean Validation, JSON Schema) | Low — validation rules are well-defined |
| Date Validation | CALL to CSUTLDTC utility | Language-native date parsing (Java `LocalDate.parse()`, etc.) | Low |
| State Management | COMMAREA pseudo-conversational | HTTP session or stateless REST with tokens | Medium |
| Confirmation | Two-step ENTER→Confirm(Y)→ENTER | Single-submit with client-side confirmation dialog | Low |
| Cross-Reference Lookup | VSAM alternate index (CXACAIX) | SQL JOIN or foreign key lookup | Low |
| Concurrency | No locking on ID generation — DUPREC risk | Database sequence guarantees uniqueness | Low |

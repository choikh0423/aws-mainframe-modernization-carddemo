# Transaction View (CT01) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CT01 |
| Entry Program | COTRN01C |
| Business Domain | Transaction Inquiry |
| Owning Team | CardDemo Application Team |
| Design Pattern | Pseudo-conversational |
| Criticality | Medium |
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

- **What**: Allows an operator to view the details of a single credit card transaction record from the TRANSACT VSAM file. The user enters a Transaction ID and the system retrieves and displays all fields of that transaction record including amount, merchant information, dates, and classification codes.
- **Who**: Any authenticated CardDemo user (regular users and admins) — menu option 7 ("Transaction View").
- **When**: Real-time, on-demand during business hours when a user needs to review or verify a specific transaction's details.
- **Why**: Supports transaction inquiry for customer service, auditing, and verification. Users can look up individual transactions by their unique Transaction ID to confirm details.
- **What** is the business outcome: The requested transaction record is retrieved from the TRANSACT VSAM file and all its details are displayed on the screen in a read-only format. No data modifications are made.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  User selects Option 7 ("Transaction View") from Main Menu
  └────────────┬─────────────┘  — OR — User selects a transaction from Transaction List (CT00)
               │
               ▼
  ┌──────────────────────────┐
  │ System Check             │  If a transaction was pre-selected from the list screen,
  └────────────┬─────────────┘  auto-populate the Tran ID and fetch it immediately
               │
               ▼
  ┌──────────────────────────┐
  │ User Action              │  Enter a Transaction ID in the "Enter Tran ID" field
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ System Validation        │  Validate Tran ID is not empty
  └────────────┬─────────────┘
               │
           ┌───┴───┐
           │Decision│  Tran ID provided?
           └───┬───┘
              ╱ ╲
        Yes ╱     ╲ No
           ▼       ▼
  ┌──────────────┐  ┌─────────────────────┐
  │ READ         │  │ Error: "Tran ID     │
  │ TRANSACT     │  │ can NOT be empty..." │
  │ file by key  │  └─────────────────────┘
  └──────┬───────┘
         │
     ┌───┴───┐
     │Found? │
     └───┬───┘
        ╱ ╲
  Yes ╱     ╲ No
     ▼       ▼
  ┌────────────────────┐  ┌──────────────────────────────┐
  │ Display all fields │  │ Error: "Transaction ID       │
  │ of the transaction │  │ NOT found..."                │
  │ record on screen   │  └──────────────────────────────┘
  └────────────────────┘
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | Transaction ID required | Tran ID field must not be empty | Error: "Tran ID can NOT be empty..." |
| BR-002 | Transaction must exist | Tran ID must exist in the TRANSACT VSAM file | Error: "Transaction ID NOT found..." |
| BR-003 | Read-only display | All transaction detail fields are display-only (ASKIP) | User cannot modify any retrieved data |
| BR-004 | Pre-selection from list | If navigated from Transaction List (COTRN00C) with a selected transaction, auto-fetch it | CDEMO-CT01-TRN-SELECTED is pre-populated; auto-invokes PROCESS-ENTER-KEY |
| BR-005 | Navigation to Transaction List | User can press PF5 to navigate to the Transaction List screen | XCTL to COTRN00C |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Select Option 7 from Main Menu (or select a transaction from List screen) | XCTL to COTRN01C; display Transaction View screen (COTRN1A) — empty or pre-filled | Navigate to transaction inquiry |
| 2 | Enter Transaction ID in "Enter Tran ID" field | Field capture via FSET | Identify target transaction |
| 3 | Press ENTER | Validate Tran ID, READ TRANSACT file, display all fields | Retrieve and view transaction |
| Alt-A | Press PF3 | XCTL back to previous screen (CDEMO-FROM-PROGRAM or Main Menu COMEN01C) | Exit transaction view |
| Alt-B | Press PF4 | Clear all fields on screen | Reset form for new inquiry |
| Alt-C | Press PF5 | XCTL to COTRN00C (Transaction List) | Browse transactions |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CT01
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COTRN01C     │ (Entry & only online program)      │
  │  │ (View Tran)  │                                    │
  │  └──┬────┬──────┘                                    │
  │     │    │                                            │
  │     ▼    ▼                                            │
  │  ┌──────┐ ┌────────────────────────────────────┐     │
  │  │BMS   │ │ VSAM Files                         │     │
  │  │MAP:  │ │  TRANSACT (Transaction master)     │     │
  │  │COTRN │ └────────────────────────────────────┘     │
  │  │1A    │                                            │
  │  └──────┘                                            │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1 (First entry — from Main Menu or Transaction List via XCTL):
  1. COMEN01C (or COTRN00C) XCTLs to COTRN01C with CARDDEMO-COMMAREA
  2. EIBCALEN > 0; CDEMO-PGM-REENTER = 0 (first entry)
  3. SET CDEMO-PGM-REENTER TO TRUE
  4. MOVE LOW-VALUES TO COTRN1AO (clear output map)
  5. Set cursor to "Enter Tran ID" field (TRNIDINL = -1)
  6. If CDEMO-CT01-TRN-SELECTED is populated (from Transaction List):
     └── Pre-fill TRNIDINI with selected Tran ID
     └── PERFORM PROCESS-ENTER-KEY (auto-fetch)
  7. PERFORM SEND-TRNVIEW-SCREEN
     └── SEND MAP('COTRN1A') MAPSET('COTRN01') ERASE CURSOR
  8. RETURN TRANSID('CT01') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 2+ (User enters Tran ID and presses ENTER):
  9.  CICS dispatches → COTRN01C (COMMAREA restored)
  10. EIBCALEN > 0; CDEMO-PGM-REENTER = 1
  11. RECEIVE MAP('COTRN1A') INTO(COTRN1AI)
  12. EIBAID = DFHENTER → PERFORM PROCESS-ENTER-KEY
  13. Validate Tran ID is not empty
      └── Empty → error "Tran ID can NOT be empty...", SEND screen, RETURN
  14. Clear all display fields
  15. MOVE TRNIDINI to TRAN-ID
  16. PERFORM READ-TRANSACT-FILE
      ├── NORMAL → continue
      ├── NOTFND → error "Transaction ID NOT found..."
      └── OTHER  → error "Unable to lookup Transaction..."
  17. If no error:
      ├── Format TRAN-AMT → WS-TRAN-AMT (+99999999.99)
      ├── Populate all display fields from TRAN-RECORD
      └── PERFORM SEND-TRNVIEW-SCREEN
  18. RETURN TRANSID('CT01') COMMAREA(CARDDEMO-COMMAREA)
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | >0 | XCTL from menu (option 7) or list screen | Initial logic (PGM-ENTER) | COTRN1A (empty or pre-filled) | CT01 |
| 2 | >0 | Enter Tran ID, press ENTER | RECEIVE → PROCESS-ENTER-KEY | COTRN1A (with transaction details or error) | CT01 |
| N | >0 | PF3 (Exit) | XCTL to COMEN01C or CDEMO-FROM-PROGRAM | — | — (XCTL) |
| N | >0 | PF5 (Browse Tran) | XCTL to COTRN00C | — | — (XCTL) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | RECEIVE MAP('COTRN1A') MAPSET('COTRN01') INTO(COTRN1AI) | Capture user input from terminal | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 2 | READ DATASET('TRANSACT') INTO(TRAN-RECORD) RIDFLD(TRAN-ID) KEYLENGTH(LENGTH OF TRAN-ID) UPDATE | Read transaction record by key | NORMAL → continue; NOTFND → error; OTHER → error |
| 3 | SEND MAP('COTRN1A') MAPSET('COTRN01') FROM(COTRN1AO) ERASE CURSOR | Send screen to terminal | — |
| 4 | RETURN TRANSID('CT01') COMMAREA(CARDDEMO-COMMAREA) | Pseudo-conversational return | — |
| 5 | XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA) | Transfer to previous screen on PF3 or to COTRN00C on PF5 | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────┐                ┌─────────────────────────────────────┐
  │  Main Menu  │   Option 7     │  COTRN1A (View Transaction)         │
  │  (COMEN01C) │───────────────▶│                                     │
  └─────────────┘                │  Enter Tran ID: ________________    │
        ▲                        │  ─────────────────────────────────   │
        │ PF3                    │  Transaction ID: ________________   │
        │                        │  Card Number:    ________________   │
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
        │                        │  [Error/Success message line]       │
        │                        │  ENTER=Fetch F3=Back F4=Clear       │
        │                        │  F5=Browse Tran.                    │
        └────────────────────────┤                                     │
                                 └─────────────────────────────────────┘
                                   │         │         │
                                   │ ENTER   │ PF4     │ PF5
                                   │ (fetch  │ (clear  │
                                   │  tran,  │  form,  │
                                   │  loops  │  loops  │
                                   │  back)  │  back)  │
                                   ▼         ▼         ▼
                                 [Same      [Same    ┌──────────────┐
                                  screen]    screen]  │ COTRN00C     │
                                                      │ (Tran List)  │
                                                      └──────────────┘
```

```
  ┌─────────────┐                ┌─────────────────────────────────────┐
  │  Tran List  │  Select a      │  COTRN1A (View Transaction)         │
  │  (COTRN00C) │  transaction   │                                     │
  │             │───────────────▶│  Pre-fills Tran ID and auto-fetches │
  └─────────────┘                └─────────────────────────────────────┘
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| COTRN1A | COTRN01 | Transaction View inquiry/display screen | Enter Tran ID (input), Transaction ID, Card#, Type CD, Category CD, Source, Description, Amount, Orig Date, Proc Date, Merchant ID/Name/City/Zip (all display) | ENTER=Fetch, PF3=Back, PF4=Clear, PF5=Browse Tran. |

### 3.3 Screen Field Details

#### Map: COTRN1A

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction name (CT01) |
| TITLE01 | ASKIP,FSET,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,FSET,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | ASKIP,FSET,NORM | 8 | Alpha | — | Program name (COTRN01C) |
| TITLE02 | ASKIP,FSET,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | ASKIP,FSET,NORM | 8 | Time | — | Current time hh:mm:ss |
| TRNIDIN | FSET,IC,NORM,UNPROT | 16 | AlphaNum | Non-empty | Enter Transaction ID (user input) |
| TRNID | ASKIP,NORM | 16 | AlphaNum | — | Transaction ID (display from record) |
| CARDNUM | ASKIP,NORM | 16 | AlphaNum | — | Card Number (display from record) |
| TTYPCD | ASKIP,NORM | 2 | AlphaNum | — | Transaction Type Code (display) |
| TCATCD | ASKIP,NORM | 4 | AlphaNum | — | Transaction Category Code (display) |
| TRNSRC | ASKIP,NORM | 10 | Alpha | — | Transaction Source (display) |
| TDESC | ASKIP,NORM | 60 | AlphaNum | — | Transaction Description (display) |
| TRNAMT | ASKIP,NORM | 12 | Formatted Decimal | — | Transaction Amount (display, +99999999.99) |
| TORIGDT | ASKIP,NORM | 10 | Timestamp | — | Origination Timestamp (display) |
| TPROCDT | ASKIP,NORM | 10 | Timestamp | — | Processing Timestamp (display) |
| MID | ASKIP,NORM | 9 | Numeric | — | Merchant ID (display) |
| MNAME | ASKIP,NORM | 30 | Alpha | — | Merchant Name (display) |
| MCITY | ASKIP,NORM | 25 | Alpha | — | Merchant City (display) |
| MZIP | ASKIP,NORM | 10 | AlphaNum | — | Merchant Zip Code (display) |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/info message display |

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | COTRN1A | Validate Tran ID input; if valid, READ TRANSACT file and display transaction details |
| PF3 | COTRN1A | Return to previous screen (CDEMO-FROM-PROGRAM or Main Menu COMEN01C) via XCTL |
| PF4 | COTRN1A | Clear all input and display fields (INITIALIZE-ALL-FIELDS), redisplay empty form |
| PF5 | COTRN1A | Navigate to Transaction List screen (COTRN00C) via XCTL |
| CLEAR | COTRN1A | Not explicitly handled — falls to "Invalid key pressed" |
| OTHER | COTRN1A | Display "Invalid key pressed. Please see below..." |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COTRN01C | Transaction View — screen handler, VSAM read, display | TRANSID CT01 / XCTL from COMEN01C or COTRN00C | 330 | `app/cbl/COTRN01C.cbl` |
| COMEN01C | Main Menu — entry point for option 7 | XCTL (caller) | — | `app/cbl/COMEN01C.cbl` |
| COTRN00C | Transaction List — can pre-select a transaction for viewing | XCTL (caller/callee) | — | `app/cbl/COTRN00C.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CT01
└── COTRN01C (Entry — Screen Handler, VSAM Read, Display)
    ├── READ FILE('TRANSACT') — Read transaction record by key
    ├── SEND MAP('COTRN1A') — Display screen
    ├── RECEIVE MAP('COTRN1A') — Capture input
    ├── RETURN TRANSID('CT01') — Pseudo-conversational return
    ├── XCTL → COMEN01C or CDEMO-FROM-PROGRAM (on PF3 exit)
    └── XCTL → COTRN00C (on PF5 — browse transactions)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COMEN01C | COTRN01C | XCTL | CARDDEMO-COMMAREA | — | User selects menu option 7 |
| COTRN00C | COTRN01C | XCTL | CARDDEMO-COMMAREA (with CDEMO-CT01-TRN-SELECTED) | — | User selects a transaction from list |
| COTRN01C | COMEN01C | XCTL | CARDDEMO-COMMAREA | — | PF3 exit back to main menu |
| COTRN01C | COTRN00C | XCTL | CARDDEMO-COMMAREA | — | PF5 navigate to transaction list |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| Transaction Master | TRANSACT | VSAM KSDS | READ (with UPDATE) | COTRN01C | Read transaction record by Transaction ID |

### 5.2 File Access Details

#### File: TRANSACT (Transaction Master)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CVTRA05Y (TRAN-RECORD, 350 bytes) |
| Key | TRAN-ID (PIC X(16)) |
| Access Mode | READ with UPDATE option |
| Locking | Record-level lock acquired (UPDATE keyword used) |
| Shared With | COTRN00C (List), COTRN02C (Add), Batch Post-Tran |

**Access Pattern**:
| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COTRN01C | READ DATASET('TRANSACT') INTO(TRAN-RECORD) RIDFLD(TRAN-ID) KEYLENGTH(LENGTH OF TRAN-ID) UPDATE | TRAN-ID from TRNIDINI screen input | All TRAN-RECORD fields retrieved for display |

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
  Terminal Input (COTRN1A)
       │
       └── TRNIDINI (Enter Tran ID — user input)
               │
               └──▶ TRAN-ID (key field for VSAM READ)
                         │
                         └──▶ READ TRANSACT → TRAN-RECORD
                                   │
                                   ├──▶ TRAN-ID        → TRNIDI   (Transaction ID display)
                                   ├──▶ TRAN-CARD-NUM  → CARDNUMI (Card Number display)
                                   ├──▶ TRAN-TYPE-CD   → TTYPCDI  (Type CD display)
                                   ├──▶ TRAN-CAT-CD    → TCATCDI  (Category CD display)
                                   ├──▶ TRAN-SOURCE    → TRNSRCI  (Source display)
                                   ├──▶ TRAN-AMT ──▶ WS-TRAN-AMT ──▶ TRNAMTI (Amount display, formatted)
                                   ├──▶ TRAN-DESC      → TDESCI   (Description display)
                                   ├──▶ TRAN-ORIG-TS   → TORIGDTI (Origination Timestamp display)
                                   ├──▶ TRAN-PROC-TS   → TPROCDTI (Processing Timestamp display)
                                   ├──▶ TRAN-MERCHANT-ID   → MIDI   (Merchant ID display)
                                   ├──▶ TRAN-MERCHANT-NAME → MNAMEI (Merchant Name display)
                                   ├──▶ TRAN-MERCHANT-CITY → MCITYI (Merchant City display)
                                   └──▶ TRAN-MERCHANT-ZIP  → MZIPI  (Merchant Zip display)

  Header Population (POPULATE-HEADER-INFO):
       FUNCTION CURRENT-DATE → WS-CURDATE-DATA
            ├──▶ WS-CURDATE-MM-DD-YY → CURDATEO (mm/dd/yy format)
            └──▶ WS-CURTIME-HH-MM-SS → CURTIMEO (hh:mm:ss format)
       CCDA-TITLE01 → TITLE01O ("AWS Mainframe Modernization")
       CCDA-TITLE02 → TITLE02O ("CardDemo")
       WS-TRANID    → TRNNAMEO ("CT01")
       WS-PGMNAME   → PGMNAMEO ("COTRN01C")
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| TRNIDINI | Terminal (COTRN1AI) | Direct move (X(16)) | TRAN-ID | READ key for TRANSACT |
| TRAN-ID | TRAN-RECORD (CVTRA05Y) | Direct move | TRNIDI | Screen output (COTRN1AO) |
| TRAN-CARD-NUM | TRAN-RECORD | Direct move | CARDNUMI | Screen output |
| TRAN-TYPE-CD | TRAN-RECORD | Direct move | TTYPCDI | Screen output |
| TRAN-CAT-CD | TRAN-RECORD | Direct move | TCATCDI | Screen output |
| TRAN-SOURCE | TRAN-RECORD | Direct move | TRNSRCI | Screen output |
| TRAN-AMT | TRAN-RECORD | PIC S9(09)V99 → WS-TRAN-AMT PIC +99999999.99 | TRNAMTI | Screen output (formatted with sign and decimal) |
| TRAN-DESC | TRAN-RECORD | Direct move | TDESCI | Screen output |
| TRAN-ORIG-TS | TRAN-RECORD | Direct move (X(26) → X(10), truncated to 10) | TORIGDTI | Screen output |
| TRAN-PROC-TS | TRAN-RECORD | Direct move (X(26) → X(10), truncated to 10) | TPROCDTI | Screen output |
| TRAN-MERCHANT-ID | TRAN-RECORD | Direct move (9(09)) | MIDI | Screen output |
| TRAN-MERCHANT-NAME | TRAN-RECORD | Direct move (X(50) → X(30), truncated to 30) | MNAMEI | Screen output |
| TRAN-MERCHANT-CITY | TRAN-RECORD | Direct move (X(50) → X(25), truncated to 25) | MCITYI | Screen output |
| TRAN-MERCHANT-ZIP | TRAN-RECORD | Direct move | MZIPI | Screen output |
| WS-CURDATE-MONTH | CURRENT-DATE | Reformat YYYYMMDD → MM/DD/YY | CURDATEO | Screen header |
| WS-CURTIME-HOURS | CURRENT-DATE | Reformat HHMMSSMS → HH:MM:SS | CURTIMEO | Screen header |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`) plus a program-specific extension `CDEMO-CT01-INFO`:

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

**CDEMO-CT01-INFO (program-specific extension, defined inline in COTRN01C):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-CT01-TRNID-FIRST | X(16) | 16 | IN/OUT | First transaction ID (pagination context from list) |
| CDEMO-CT01-TRNID-LAST | X(16) | 16 | IN/OUT | Last transaction ID (pagination context from list) |
| CDEMO-CT01-PAGE-NUM | 9(08) | 8 | IN/OUT | Page number (pagination context from list) |
| CDEMO-CT01-NEXT-PAGE-FLG | X(01) | 1 | IN/OUT | 'Y'/'N' — more pages available |
| CDEMO-CT01-TRN-SEL-FLG | X(01) | 1 | IN | Transaction selection flag |
| CDEMO-CT01-TRN-SELECTED | X(16) | 16 | IN | Pre-selected transaction ID (from Transaction List screen) |

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. Errors are handled inline via screen messages (WS-MESSAGE → ERRMSGO). All error communication is through the ERRMSG field on the BMS map.

### 7.3 Conversation State Management

- **COMMAREA preservation**: The entire CARDDEMO-COMMAREA (including CT01-specific fields) is passed on every RETURN TRANSID('CT01'). CICS restores it automatically on the next dispatch.
- **Screen field state**: The TRNIDIN field uses BMS FSET attribute, ensuring the user's input is returned on every RECEIVE, preserving the entered Transaction ID across iterations. Display fields are output-only (ASKIP) and repopulated from the file on each ENTER press.
- **No TSQ overflow**: All state fits within the COMMAREA; no temporary storage queues are used.
- **PGM-CONTEXT flag**: Distinguishes first entry (0 — display empty screen or pre-selected transaction) from subsequent interactions (1 — process user input).
- **Pre-selection**: When navigated from Transaction List (COTRN00C), the `CDEMO-CT01-TRN-SELECTED` field carries the selected Transaction ID, enabling auto-fetch on first entry.

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path:
    Input → Validate Tran ID → READ TRANSACT → Display fields → RETURN

  Empty Tran ID:
    Input → Validate ──FAIL──▶ "Tran ID can NOT be empty..."
                                → Cursor to TRNIDIN, SEND screen, RETURN

  Transaction Not Found:
    Input → READ TRANSACT ──NOTFND──▶ "Transaction ID NOT found..."
                                       → Cursor to TRNIDIN, SEND screen, RETURN

  File I/O Error:
    Input → READ TRANSACT ──OTHER──▶  DISPLAY RESP/REAS to SYSOUT
                                       "Unable to lookup Transaction..."
                                       → Cursor to TRNIDIN, SEND screen, RETURN

  Invalid Key:
    User presses unrecognized key ──▶ "Invalid key pressed. Please see below..."
                                      → SEND screen, RETURN
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| Tran ID is empty (spaces/low-values) | — (application validation) | Set ERR-FLG='Y', cursor to TRNIDIN | "Tran ID can NOT be empty..." | — |
| Transaction not found in TRANSACT | NOTFND (13) | Set ERR-FLG='Y', cursor to TRNIDIN | "Transaction ID NOT found..." | — |
| TRANSACT read error | OTHER | Set ERR-FLG='Y', cursor to TRNIDIN | "Unable to lookup Transaction..." | DISPLAY RESP/REAS to SYSOUT |
| Invalid AID key pressed | — (unrecognized EIBAID) | Set ERR-FLG='Y' | "Invalid key pressed. Please see below..." | — |
| Map receive error | — | RESP/RESP2 captured but not evaluated | — | — |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| — | No explicit HANDLE ABEND | Default CICS abend handling applies |

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "Tran ID can NOT be empty..." | TRNIDINI is spaces or low-values | ERRMSG (line 23, col 1) |
| — | "Transaction ID NOT found..." | READ TRANSACT returns NOTFND | ERRMSG (line 23, col 1) |
| — | "Unable to lookup Transaction..." | READ TRANSACT returns unexpected RESP code | ERRMSG (line 23, col 1) |
| — | "Invalid key pressed. Please see below..." | Unrecognized AID key (not ENTER, PF3, PF4, PF5) | ERRMSG (line 23, col 1) |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CT01 transaction must be authorized for user |
| Resource-level security | RACF FACILITY / File profiles for TRANSACT |
| Application-level check | If EIBCALEN = 0 (no COMMAREA — direct invocation without login), XCTL to sign-on screen COSGN00C |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| TRANSACT file | READ and UPDATE (UPDATE keyword used on READ command) | CICS File Control |

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
| CPU per transaction | Very low (1 READ + display) | — | — |
| File I/O count | 1 per inquiry (1 TRANSACT READ) | — | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal | — | — |

### 10.2 Performance Considerations

- **Single READ per inquiry**: The transaction performs exactly one VSAM READ per ENTER key press, making it very efficient.
- **UPDATE keyword on READ**: The READ command uses the UPDATE option, which acquires a record-level lock. This is unnecessary for a view-only transaction and could cause contention under concurrent access (see Known Issues).
- **COMMAREA size**: Relatively small; no TSQ overflow needed.
- **BMS SEND/RECEIVE**: Standard map I/O with ERASE — one pair per pseudo-conversational iteration.
- **No external CALL**: Unlike other CardDemo programs, COTRN01C does not CALL any external utility programs, reducing overhead.
- **Pre-selection optimization**: When navigated from Transaction List with a pre-selected ID, the fetch occurs during the first iteration, avoiding an extra round-trip.

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Normal view by valid Tran ID | Enter existing Transaction ID, press ENTER | All transaction detail fields populated correctly | High |
| TC-002 | Empty Tran ID | Leave "Enter Tran ID" blank, press ENTER | "Tran ID can NOT be empty..." | High |
| TC-003 | Non-existent Tran ID | Enter a valid-format but non-existent Transaction ID | "Transaction ID NOT found..." | High |
| TC-004 | Pre-selected from Transaction List | Navigate from COTRN00C with a selected transaction | Screen displays with Tran ID pre-filled and details auto-fetched | High |
| TC-005 | PF3 exit to Main Menu | Press PF3 (no CDEMO-FROM-PROGRAM) | Return to Main Menu (COMEN01C) | Medium |
| TC-006 | PF3 exit to previous screen | Press PF3 (CDEMO-FROM-PROGRAM set, e.g., COTRN00C) | Return to the originating screen | Medium |
| TC-007 | PF4 clear screen | Enter data then press PF4 | All input and display fields cleared, cursor on Tran ID | Medium |
| TC-008 | PF5 navigate to Transaction List | Press PF5 | Transfer to COTRN00C (Transaction List) | Medium |
| TC-009 | Invalid PF key | Press PF2, PF6, etc. | "Invalid key pressed. Please see below..." | Low |
| TC-010 | Direct invocation without login | Invoke CT01 directly (EIBCALEN = 0) | Redirect to sign-on screen (COSGN00C) | Medium |
| TC-011 | Sequential lookups | View one transaction, then enter a different Tran ID and press ENTER | Previous fields cleared, new transaction details displayed | Medium |
| TC-012 | Amount formatting | View a transaction with negative amount | Amount displayed with sign format (+/-99999999.99) | Medium |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| TRANSACT | Pre-loaded with multiple transaction records covering various amounts, dates, and merchant details | VSAM REPRO from test data (`app/data/`) |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | READ command uses UPDATE keyword unnecessarily — this is a view-only transaction that never modifies data | Acquires a record-level lock on every view, potentially blocking concurrent updates (COTRN02C adds) and other readers | Remove the UPDATE keyword from the READ command |
| KI-002 | TRAN-ORIG-TS and TRAN-PROC-TS are PIC X(26) in the record layout but the screen fields (TORIGDT, TPROCDT) are only PIC X(10) — only the first 10 characters are displayed | Full timestamp precision (time portion) is not visible to the user | Extend screen fields or add separate time fields |
| KI-003 | TRAN-MERCHANT-NAME is PIC X(50) in the record but MNAME screen field is PIC X(30) — truncation occurs | Merchant names longer than 30 characters are silently truncated on display | Extend screen field or add scrolling |
| KI-004 | TRAN-MERCHANT-CITY is PIC X(50) in the record but MCITY screen field is PIC X(25) — truncation occurs | Merchant city names longer than 25 characters are silently truncated on display | Extend screen field or add scrolling |
| KI-005 | No explicit HANDLE ABEND — unexpected abends use default CICS handling | User sees CICS ABEND screen rather than a friendly error | Add HANDLE ABEND with a user-friendly error screen |
| KI-006 | RECEIVE MAP does not evaluate WS-RESP-CD — a MAPFAIL condition is not explicitly handled | If user presses ENTER without modifying any fields and no FSET fields have data, MAPFAIL could occur | Add MAPFAIL handling in RECEIVE |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Screen I/O | BMS Map COTRN1A / 3270 terminal | Web page / REST API GET endpoint | Low — read-only display, straightforward mapping |
| Data Access | VSAM KSDS direct (TRANSACT) | Service layer with RDBMS (e.g., RDS PostgreSQL) | Low — single READ by primary key |
| State Management | COMMAREA pseudo-conversational | HTTP session or stateless REST (Tran ID in URL path) | Low — minimal state, could be fully stateless |
| Pre-selection | COMMAREA field from calling program | URL parameter or query string (e.g., `/transactions/{id}`) | Low |
| Amount Formatting | COBOL PIC edit mask (+99999999.99) | Language-native number formatting (DecimalFormat, Intl.NumberFormat) | Low |
| Error Messages | Inline COBOL string literals → ERRMSG field | Framework-level error handling (HTTP status codes, JSON error bodies) | Low |
| Navigation | XCTL to/from other CICS programs | Client-side routing or hyperlinks | Low |
| Locking | Unnecessary UPDATE lock on READ | Remove lock; use optimistic concurrency if needed | Low — simply omit the lock |

# Credit Card View/Detail (CCDL) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CCDL |
| Entry Program | COCRDSLC |
| Business Domain | Credit Card Management |
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

- **What**: Displays the details of a specific credit card record, including the embossed name, active status, and expiration date. The user identifies the card by providing an Account Number and Card Number.
- **Who**: Any authenticated CardDemo user (regular users and admins) — accessible via menu option 4 ("Credit Card View") or by selecting a card from the Credit Card List screen (COCRDLIC).
- **When**: Real-time, on-demand during business hours for inquiry/lookup purposes.
- **Why**: Supports customer service and account management operations that require viewing credit card details such as status, embossed name, and expiry information.
- **What** is the business outcome: The credit card detail fields (name on card, active status, expiry month/year) are displayed as read-only information on the screen. No data is modified — this is a view-only transaction.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  User selects Option 4 ("Credit Card View") from Main Menu
  └────────────┬─────────────┘  OR selects a card from the Credit Card List (COCRDLIC)
               │
               ▼
  ┌──────────────────────────┐
  │ System Check             │  Coming from Credit Card List?
  └────────────┬─────────────┘
          ┌────┴────┐
          │ Yes/No? │
          └────┬────┘
          ╱          ╲
    Yes ╱              ╲ No
       ▼                ▼
  ┌─────────────┐  ┌──────────────────────────┐
  │ Auto-fill   │  │ Display empty search form │
  │ Acct & Card │  │ Prompt: "Please enter     │
  │ from COMM-  │  │ Account and Card Number"  │
  │ AREA, READ  │  └────────────┬──────────────┘
  │ card data   │               │
  └──────┬──────┘               ▼
         │         ┌──────────────────────────┐
         │         │ User Action              │  Enter Account Number and Card Number
         │         └────────────┬─────────────┘
         │                      │
         │                      ▼
         │         ┌──────────────────────────┐
         │         │ System Validation        │  Validate Account (11-digit numeric)
         │         └────────────┬─────────────┘  Validate Card (16-digit numeric)
         │                      │
         │                 ┌────┴────┐
         │                 │Decision │  Validation passes?
         │                 └────┬────┘
         │                 ╱          ╲
         │           Yes ╱              ╲ No
         │              ▼                ▼
         │    ┌──────────────┐  ┌─────────────────────┐
         │    │ READ CARDDAT │  │ Error message,      │
         │    │ by Card Num  │  │ cursor to field,    │
         │    └──────┬───────┘  │ redisplay form      │
         │           │          └─────────────────────┘
         ▼           ▼
  ┌──────────────────────────┐
  │ Business Outcome         │  Display card details:
  └──────────────────────────┘  Name on Card, Active Status (Y/N), Expiry Month/Year
                                 Message: "Displaying requested details"
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | Account Number required | Account Number is blank, spaces, or zeroes | Error: "Account number not provided" |
| BR-002 | Card Number required | Card Number is blank, spaces, or zeroes | Error: "Card number not provided" |
| BR-003 | Both fields required | Neither Account nor Card Number provided | Error: "No input received" |
| BR-004 | Account must be numeric | Account Number contains non-numeric characters | Error: "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER" |
| BR-005 | Card must be numeric | Card Number contains non-numeric characters | Error: "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" |
| BR-006 | Card record must exist | Card Number not found in CARDDAT file | Error: "Did not find cards for this search condition" |
| BR-007 | View-only operation | User presses ENTER with valid data | Card details displayed; no data modification occurs |
| BR-008 | Protected fields from list | Coming from Credit Card List screen (COCRDLIC) | Account and Card Number fields are protected (read-only) |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1a | Select Option 4 from Main Menu | XCTL to COCRDSLC; display empty search form (CCRDSLA) | Navigate to card detail view |
| 1b | Select a card from Credit Card List | XCTL to COCRDSLC; auto-fill Account/Card from COMMAREA, READ and display details | Quick drill-down from list |
| 2 | Enter Account Number and Card Number | Validate inputs, READ CARDDAT, display card details | Search for specific card |
| 3 | Press ENTER again | Re-process inputs and refresh display | Perform another lookup |
| Alt-A | Press PF3 | XCTL back to previous screen (Credit Card List or Main Menu) | Exit card view |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CCDL
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COCRDSLC     │ (Entry & only online program)      │
  │  │ (Card Detail)│                                    │
  │  └──┬────┬──────┘                                    │
  │     │    │                                           │
  │     ▼    ▼                                           │
  │  ┌──────┐ ┌────────────────────────────────────┐    │
  │  │BMS   │ │ VSAM Files                         │    │
  │  │MAP:  │ │  CARDDAT  (Card master — primary)  │    │
  │  │CCRDS │ │  CARDAIX  (Card file — AIX by      │    │
  │  │LA    │ │           account ID, unused here)  │    │
  │  └──────┘ └────────────────────────────────────┘    │
  │                                                      │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1a (First entry — from Main Menu via XCTL, no prior card data):
  1.  COMEN01C XCTLs to COCRDSLC with CARDDEMO-COMMAREA
  2.  EIBCALEN > 0; CDEMO-PGM-ENTER = TRUE (first entry)
  3.  CDEMO-FROM-PROGRAM != LIT-CCLISTPGM (not from card list)
  4.  → Falls to WHEN CDEMO-PGM-ENTER (standalone context)
  5.  PERFORM 1000-SEND-MAP:
      ├── 1100-SCREEN-INIT: Clear map, set titles/date/time
      ├── 1200-SETUP-SCREEN-VARS: No COMMAREA data → prompt for input
      ├── 1300-SETUP-SCREEN-ATTRS: Unprotect Acct/Card fields (DFHBMFSE)
      └── 1400-SEND-SCREEN: SEND MAP ERASE CURSOR
  6.  SET CDEMO-PGM-REENTER TO TRUE
  7.  RETURN TRANSID('CCDL') COMMAREA(WS-COMMAREA)

  Iteration 1b (First entry — from Credit Card List via XCTL, card pre-selected):
  1.  COCRDLIC XCTLs to COCRDSLC with CARDDEMO-COMMAREA
  2.  EIBCALEN > 0; CDEMO-PGM-ENTER = TRUE
  3.  CDEMO-FROM-PROGRAM = 'COCRDLIC' (from card list)
  4.  → WHEN CDEMO-PGM-ENTER AND CDEMO-FROM-PROGRAM = LIT-CCLISTPGM
  5.  SET INPUT-OK TO TRUE
  6.  MOVE CDEMO-ACCT-ID → CC-ACCT-ID-N
  7.  MOVE CDEMO-CARD-NUM → CC-CARD-NUM-N
  8.  PERFORM 9000-READ-DATA:
      └── 9100-GETCARD-BYACCTCARD:
          └── READ CARDDAT BY WS-CARD-RID-CARDNUM → CARD-RECORD
              ├── NORMAL → SET FOUND-CARDS-FOR-ACCOUNT
              ├── NOTFND → INPUT-ERROR, "Did not find cards..."
              └── OTHER → INPUT-ERROR, file error message
  9.  PERFORM 1000-SEND-MAP:
      ├── 1100-SCREEN-INIT: Clear map, set titles/date/time
      ├── 1200-SETUP-SCREEN-VARS: Populate Acct/Card + detail fields
      ├── 1300-SETUP-SCREEN-ATTRS: Protect Acct/Card fields (DFHBMPRF)
      └── 1400-SEND-SCREEN: SEND MAP ERASE CURSOR
  10. GO TO COMMON-RETURN → RETURN TRANSID('CCDL')

  Iteration 2 (User submits search — ENTER pressed):
  11. CICS dispatches → COCRDSLC (COMMAREA restored)
  12. EIBCALEN > 0; CDEMO-PGM-REENTER = TRUE
  13. PERFORM 2000-PROCESS-INPUTS:
      ├── 2100-RECEIVE-MAP: RECEIVE MAP('CCRDSLA') INTO(CCRDSLAI)
      └── 2200-EDIT-MAP-INPUTS:
          ├── Replace '*'/spaces with LOW-VALUES for Acct/Card
          ├── 2210-EDIT-ACCOUNT: Validate numeric, non-blank
          ├── 2220-EDIT-CARD: Validate numeric, non-blank
          └── Cross-field: both blank → "No input received"
  14. IF INPUT-ERROR:
      └── PERFORM 1000-SEND-MAP → redisplay with error → COMMON-RETURN
  15. ELSE (INPUT-OK):
      ├── PERFORM 9000-READ-DATA → READ CARDDAT
      ├── PERFORM 1000-SEND-MAP → display with card details
      └── GO TO COMMON-RETURN

  COMMON-RETURN:
  16. MOVE WS-RETURN-MSG → CCARD-ERROR-MSG
  17. MOVE CARDDEMO-COMMAREA + WS-THIS-PROGCOMMAREA → WS-COMMAREA
  18. RETURN TRANSID('CCDL') COMMAREA(WS-COMMAREA)
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1a (first, direct) | >0 | XCTL from menu (option 4) | CDEMO-PGM-ENTER (standalone) | CCRDSLA (empty form, prompt) | CCDL |
| 1b (first, from list) | >0 | XCTL from COCRDLIC (card selected) | CDEMO-PGM-ENTER + FROM-PROGRAM=COCRDLIC | CCRDSLA (with card details) | CCDL |
| 2 | >0 | Fill Acct/Card, press ENTER | CDEMO-PGM-REENTER → 2000-PROCESS-INPUTS | CCRDSLA (with details or errors) | CCDL |
| N | >0 | PF3 (Exit) | XCTL to COCRDLIC or COMEN01C | — | — (XCTL) |
| 0 (direct, no COMMAREA) | 0 | Direct CCDL invoke | EIBCALEN=0 → INITIALIZE COMMAREA | CCRDSLA (empty form, prompt) | CCDL |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | HANDLE ABEND LABEL(ABEND-ROUTINE) | Set up abend handler for unexpected failures | ABEND-ROUTINE sends error data, issues ABEND '9999' |
| 2 | RECEIVE MAP('CCRDSLA') MAPSET('COCRDSL') INTO(CCRDSLAI) | Capture user input from terminal | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 3 | READ FILE('CARDDAT') RIDFLD(WS-CARD-RID-CARDNUM) | Read card record by card number (primary key) | NORMAL → display details; NOTFND → error; OTHER → file error |
| 4 | SEND MAP('CCRDSLA') MAPSET('COCRDSL') FROM(CCRDSLAO) ERASE CURSOR FREEKB | Send screen to terminal with card details or error | — |
| 5 | RETURN TRANSID('CCDL') COMMAREA(WS-COMMAREA) | Pseudo-conversational return | — |
| 6 | XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA) | Transfer to previous screen on PF3 | — |
| 7 | SEND TEXT FROM(WS-RETURN-MSG) ERASE FREEKB | Debug/error plain text output (SEND-PLAIN-TEXT) | Followed by RETURN (no TRANSID) |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────────┐              ┌─────────────────────────────────────┐
  │  Main Menu      │  Option 4    │  CCRDSLA (View Credit Card Detail)  │
  │  (COMEN01C)     │─────────────▶│                                     │
  └─────────────────┘              │  Account Number   : ___________     │
        ▲                          │  Card Number      : ________________│
        │ PF3 (if no caller)       │                                     │
        │                          │  Name on card     : ________________│
        │                          │  Card Active Y/N  : _               │
  ┌─────────────────┐              │  Expiry Date      : MM/YYYY         │
  │  Credit Card    │  Select      │                                     │
  │  List           │─────────────▶│  [Info message line]                │
  │  (COCRDLIC)     │              │  [Error message line]               │
  └─────────────────┘              │  ENTER=Search Cards  F3=Exit        │
        ▲                          └─────────────────────────────────────┘
        │ PF3 (if from list)                │         │
        │                                   │ ENTER   │ PF3
        │                                   │ (search │ (exit)
        │                                   │  loops  │
        │                                   │  back)  │
        └───────────────────────────────────┘         │
                                                      ▼
                                              [Return to caller]
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| CCRDSLA | COCRDSL | View Credit Card Detail — search and display card information | Account Number, Card Number, Name on Card, Card Active Status, Expiry Month, Expiry Year | ENTER=Search Cards, PF3=Exit |

### 3.3 Screen Field Details

#### Map: CCRDSLA

| Field Name | BMS Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction name (CCDL) |
| TITLE01 | TITLE01 | ASKIP,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | CURDATE | ASKIP,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | PGMNAME | ASKIP,NORM | 8 | Alpha | — | Program name (COCRDSLC) |
| TITLE02 | TITLE02 | ASKIP,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | CURTIME | ASKIP,NORM | 8 | Time | — | Current time hh:mm:ss |
| ACCTSID | ACCTSID | FSET,IC,NORM,UNPROT (or PROT when from list) | 11 | Numeric | Must be numeric 11-digit; non-zero; non-blank | Account Number |
| CARDSID | CARDSID | FSET,NORM,UNPROT (or PROT when from list) | 16 | Numeric | Must be numeric 16-digit; non-zero; non-blank | Card Number |
| CRDNAME | CRDNAME | (display, no explicit attr) | 50 | Alpha | — (output only) | Name embossed on card |
| CRDSTCD | CRDSTCD | ASKIP | 1 | Alpha | — (output only) | Card Active Status (Y/N) |
| EXPMON | EXPMON | ASKIP | 2 | Numeric | — (output only) | Expiry Month (MM) |
| EXPYEAR | EXPYEAR | ASKIP | 4 | Numeric | — (output only) | Expiry Year (YYYY) |
| INFOMSG | INFOMSG | PROT | 40 | Alpha | — (output only) | Informational message ("Displaying requested details" or "Please enter Account and Card Number") |
| ERRMSG | ERRMSG | ASKIP,BRT,FSET | 80 | Alpha | — (output only) | Error/status message display |
| FKEYS | FKEYS | ASKIP,NORM | 75 | Alpha | — | "ENTER=Search Cards  F3=Exit" |

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | CCRDSLA | Validate Account/Card inputs; if valid, READ CARDDAT and display card details |
| PF3 | CCRDSLA | Return to previous screen — if CDEMO-FROM-PROGRAM is set, XCTL to that program (e.g., COCRDLIC); otherwise XCTL to Main Menu (COMEN01C) |
| OTHER (any invalid PF key) | CCRDSLA | Treated as ENTER — invalid PF keys are remapped to ENTER (PFK-INVALID → SET CCARD-AID-ENTER) |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COCRDSLC | Credit Card View/Detail — screen handler, input validation, VSAM read | TRANSID CCDL / XCTL from COMEN01C or COCRDLIC | 887 | `app/cbl/COCRDSLC.cbl` |
| COCRDLIC | Credit Card List — list screen from which a card can be selected | XCTL (caller) | — | `app/cbl/COCRDLIC.cbl` |
| COMEN01C | Main Menu — entry point for option 4 | XCTL (caller) | — | `app/cbl/COMEN01C.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CCDL
└── COCRDSLC (Entry — Screen Handler, Validation, Data Access)
    ├── READ FILE('CARDDAT')  — Card record lookup by card number
    ├── SEND MAP('CCRDSLA')   — Display screen
    ├── RECEIVE MAP('CCRDSLA') — Capture input
    ├── RETURN TRANSID('CCDL') — Pseudo-conversational return
    └── XCTL → COCRDLIC or COMEN01C (on PF3 exit)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COMEN01C | COCRDSLC | XCTL | CARDDEMO-COMMAREA | — | User selects menu option 4 |
| COCRDLIC | COCRDSLC | XCTL | CARDDEMO-COMMAREA (with CDEMO-ACCT-ID, CDEMO-CARD-NUM) | — | User selects a card from the list |
| COCRDSLC | COCRDLIC | XCTL | CARDDEMO-COMMAREA | — | PF3 exit back to card list (if from list) |
| COCRDSLC | COMEN01C | XCTL | CARDDEMO-COMMAREA | — | PF3 exit back to main menu (if not from list) |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| Card Master | CARDDAT | VSAM KSDS | READ | COCRDSLC | Look up card details by card number (primary key path) |
| Card Master (AIX) | CARDAIX | VSAM KSDS (Alternate Index) | READ (defined but not used in active flow) | COCRDSLC | Alternate path to look up card by account ID (9150-GETCARD-BYACCT, not invoked) |

### 5.2 File Access Details

#### File: CARDDAT (Card Master — Primary Key Path)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CVACT02Y (CARD-RECORD, 150 bytes) |
| Key | CARD-NUM (PIC X(16)) — Card Number |
| Access Mode | READ |
| Locking | No update, read-only |
| Shared With | COCRDLIC (Card List), COCRDUPC (Card Update), Batch programs |

**Access Pattern**:
| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COCRDSLC | READ FILE('CARDDAT') RIDFLD(WS-CARD-RID-CARDNUM) KEYLENGTH(16) INTO(CARD-RECORD) | Card Number from screen or COMMAREA | Returns CARD-RECORD: CARD-NUM, CARD-ACCT-ID, CARD-CVV-CD, CARD-EMBOSSED-NAME, CARD-EXPIRAION-DATE, CARD-ACTIVE-STATUS |

#### File: CARDAIX (Card Master — Alternate Index by Account ID)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS (Alternate Index over CARDDAT) |
| Record Layout (Copybook) | CVACT02Y (CARD-RECORD, 150 bytes) |
| Key | WS-CARD-RID-ACCT-ID (PIC 9(11)) — Account ID |
| Access Mode | READ |
| Locking | No update, read-only |
| Shared With | Multiple CardDemo programs |

**Access Pattern** (defined in paragraph 9150-GETCARD-BYACCT but not invoked in active flow):
| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COCRDSLC | READ FILE('CARDAIX') RIDFLD(WS-CARD-RID-ACCT-ID) KEYLENGTH(11) INTO(CARD-RECORD) | Account ID | Returns CARD-RECORD |

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
  Terminal Input (CCRDSLA)
       │
       ├── ACCTSIDI (Account Number)
       │       │
       │       └──▶ CC-ACCT-ID (via 2200-EDIT-MAP-INPUTS)
       │                │
       │                └──▶ Validation: numeric, non-blank, non-zero
       │                         │
       │                         └──▶ CDEMO-ACCT-ID (COMMAREA)
       │
       ├── CARDSIDI (Card Number)
       │       │
       │       └──▶ CC-CARD-NUM (via 2200-EDIT-MAP-INPUTS)
       │                │
       │                ├──▶ Validation: numeric, non-blank, non-zero
       │                │         │
       │                │         └──▶ CDEMO-CARD-NUM (COMMAREA)
       │                │
       │                └──▶ WS-CARD-RID-CARDNUM (READ key)
       │                         │
       │                         └──▶ READ CARDDAT → CARD-RECORD
       │
       └── (From COMMAREA when called from COCRDLIC)
               ├── CDEMO-ACCT-ID → CC-ACCT-ID-N → ACCTSIDO
               └── CDEMO-CARD-NUM → CC-CARD-NUM-N → CARDSIDO
                        │
                        └──▶ WS-CARD-RID-CARDNUM → READ CARDDAT

  CARD-RECORD (output from READ):
       │
       ├── CARD-EMBOSSED-NAME ──▶ CRDNAMEO (Name on Card field)
       ├── CARD-ACTIVE-STATUS ──▶ CRDSTCDO (Card Active Y/N field)
       ├── CARD-EXPIRAION-DATE ──▶ CARD-EXPIRAION-DATE-X (WS redefine)
       │        ├── CARD-EXPIRY-MONTH ──▶ EXPMONO (Expiry Month field)
       │        └── CARD-EXPIRY-YEAR  ──▶ EXPYEARO (Expiry Year field)
       └── CARD-ACCT-ID (available but not separately displayed)
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| ACCTSIDI | Terminal (CCRDSLAI) | Direct move (replace '*'/spaces with LOW-VALUES) | CC-ACCT-ID | CC-WORK-AREA (CVCRD01Y) |
| CC-ACCT-ID | CC-WORK-AREA | Numeric validation, move to COMMAREA | CDEMO-ACCT-ID | CARDDEMO-COMMAREA |
| CARDSIDI | Terminal (CCRDSLAI) | Direct move (replace '*'/spaces with LOW-VALUES) | CC-CARD-NUM | CC-WORK-AREA (CVCRD01Y) |
| CC-CARD-NUM | CC-WORK-AREA | Numeric validation, move to COMMAREA | CDEMO-CARD-NUM | CARDDEMO-COMMAREA |
| CC-CARD-NUM | CC-WORK-AREA | Direct move | WS-CARD-RID-CARDNUM | WS-CARD-RID (READ key) |
| CARD-EMBOSSED-NAME | CARD-RECORD (CVACT02Y) | Direct move | CRDNAMEO | CCRDSLAO (output map) |
| CARD-EXPIRAION-DATE | CARD-RECORD (CVACT02Y) | Redefine → CARD-EXPIRY-MONTH | EXPMONO | CCRDSLAO (output map) |
| CARD-EXPIRAION-DATE | CARD-RECORD (CVACT02Y) | Redefine → CARD-EXPIRY-YEAR | EXPYEARO | CCRDSLAO (output map) |
| CARD-ACTIVE-STATUS | CARD-RECORD (CVACT02Y) | Direct move | CRDSTCDO | CCRDSLAO (output map) |
| CDEMO-ACCT-ID | CARDDEMO-COMMAREA | Move to CC-ACCT-ID-N, then to output | ACCTSIDO | CCRDSLAO (output map) |
| CDEMO-CARD-NUM | CARDDEMO-COMMAREA | Move to CC-CARD-NUM-N, then to output | CARDSIDO | CCRDSLAO (output map) |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`) plus a program-specific extension `WS-THIS-PROGCOMMAREA`:

**CARDDEMO-COMMAREA (COCOM01Y — shared portion):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-FROM-TRANID | X(04) | 4 | IN/OUT | Originating transaction ID |
| CDEMO-FROM-PROGRAM | X(08) | 8 | IN/OUT | Originating program name (e.g., 'COCRDLIC') |
| CDEMO-TO-TRANID | X(04) | 4 | OUT | Target transaction ID (set on PF3 exit) |
| CDEMO-TO-PROGRAM | X(08) | 8 | OUT | Target program for XCTL (set on PF3 exit) |
| CDEMO-USER-ID | X(08) | 8 | IN | Authenticated user ID |
| CDEMO-USER-TYPE | X(01) | 1 | IN | 'A' = Admin, 'U' = User |
| CDEMO-PGM-CONTEXT | 9(01) | 1 | IN/OUT | 0 = first entry (PGM-ENTER), 1 = re-entry (PGM-REENTER) |
| CDEMO-CUST-ID | 9(09) | 9 | IN | Customer ID |
| CDEMO-CUST-FNAME | X(25) | 25 | IN | Customer first name |
| CDEMO-CUST-MNAME | X(25) | 25 | IN | Customer middle name |
| CDEMO-CUST-LNAME | X(25) | 25 | IN | Customer last name |
| CDEMO-ACCT-ID | 9(11) | 11 | IN/OUT | Account ID — pre-filled when coming from COCRDLIC |
| CDEMO-ACCT-STATUS | X(01) | 1 | IN | Account status |
| CDEMO-CARD-NUM | 9(16) | 16 | IN/OUT | Card Number — pre-filled when coming from COCRDLIC |
| CDEMO-LAST-MAP | X(7) | 7 | IN/OUT | Last BMS map displayed |
| CDEMO-LAST-MAPSET | X(7) | 7 | IN/OUT | Last BMS mapset used |

**WS-THIS-PROGCOMMAREA (program-specific extension, defined inline in COCRDSLC):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CA-FROM-PROGRAM | X(08) | 8 | IN/OUT | Program that called this program (local context) |
| CA-FROM-TRANID | X(04) | 4 | IN/OUT | Transaction ID of the calling program (local context) |

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. All feedback is communicated via screen messages:
- `WS-INFO-MSG` → `INFOMSGO` (informational messages, neutral color)
- `WS-RETURN-MSG` → `ERRMSGO` (error messages, red color)
- `CCARD-ERROR-MSG` → persisted in COMMAREA for cross-iteration display

### 7.3 Conversation State Management

- **COMMAREA preservation**: The CARDDEMO-COMMAREA and WS-THIS-PROGCOMMAREA are concatenated into WS-COMMAREA (PIC X(2000)) and passed on every RETURN TRANSID('CCDL'). CICS restores it on the next dispatch.
- **Screen field state**: BMS FSET attribute on ACCTSID and CARDSID ensures user-entered values are returned on every RECEIVE.
- **No TSQ overflow**: All state fits within the 2000-byte WS-COMMAREA; no temporary storage queues are used.
- **PGM-CONTEXT flag**: Distinguishes first entry (0 — display empty/pre-filled screen) from subsequent interactions (1 — process user input).
- **Caller tracking**: CDEMO-FROM-PROGRAM and CDEMO-FROM-TRANID are used to determine PF3 exit target — either return to the Credit Card List (COCRDLIC) or the Main Menu (COMEN01C).

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path (from Credit Card List):
    XCTL with COMMAREA → READ CARDDAT → Display details → RETURN

  Normal Path (manual search):
    Input → Validate Acct/Card → READ CARDDAT → Display details → RETURN

  Validation Error (Account):
    Input → 2210-EDIT-ACCOUNT ──FAIL──▶ "Account number not provided" /
                                         "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"
                                         → Cursor to ACCTSID, '*' marker, RETURN TRANSID

  Validation Error (Card):
    Input → 2220-EDIT-CARD ──FAIL──▶ "Card number not provided" /
                                      "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"
                                      → Cursor to CARDSID, '*' marker, RETURN TRANSID

  Cross-Field Validation Error:
    Input → Both blank ──▶ "No input received"
                           → Cursor to ACCTSID, RETURN TRANSID

  Card Lookup Error:
    Input → READ CARDDAT ──NOTFND──▶ "Did not find cards for this search condition"
                                      → Cursor to ACCTSID, RETURN TRANSID
    Input → READ CARDDAT ──OTHER──▶  "File Error: READ     on CARDDAT  returned RESP XXXX,RESP2 XXXX"
                                      → Cursor to ACCTSID, RETURN TRANSID

  Unexpected Data Scenario:
    EVALUATE OTHER → "UNEXPECTED DATA SCENARIO" → SEND-PLAIN-TEXT → RETURN
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| Account number blank/spaces/zeroes | — (input validation) | SET INPUT-ERROR, SET FLG-ACCTFILTER-BLANK | "Account number not provided" | — |
| Account number not numeric | — (input validation) | SET INPUT-ERROR, SET FLG-ACCTFILTER-NOT-OK | "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER" | — |
| Card number blank/spaces/zeroes | — (input validation) | SET INPUT-ERROR, SET FLG-CARDFILTER-BLANK | "Card number not provided" | — |
| Card number not numeric | — (input validation) | SET INPUT-ERROR, SET FLG-CARDFILTER-NOT-OK | "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" | — |
| Both account and card blank | — (cross-field validation) | SET NO-SEARCH-CRITERIA-RECEIVED | "No input received" | — |
| Card not found in CARDDAT | NOTFND (13) | SET INPUT-ERROR, SET FLG-ACCTFILTER-NOT-OK, SET FLG-CARDFILTER-NOT-OK | "Did not find cards for this search condition" | — |
| CARDDAT read error | OTHER | SET INPUT-ERROR, SET FLG-ACCTFILTER-NOT-OK | "File Error: READ on CARDDAT returned RESP XXXX,RESP2 XXXX" | Error details in WS-FILE-ERROR-MESSAGE |
| Card not found in CARDAIX (alt path) | NOTFND (13) | SET INPUT-ERROR, SET FLG-ACCTFILTER-NOT-OK | "Did not find this account in cards database" | — |
| CARDAIX read error (alt path) | OTHER | SET INPUT-ERROR, SET FLG-ACCTFILTER-NOT-OK | "File Error: READ on CARDAIX returned RESP XXXX,RESP2 XXXX" | Error details in WS-FILE-ERROR-MESSAGE |
| Unexpected EVALUATE branch | — | MOVE 'UNEXPECTED DATA SCENARIO' to WS-RETURN-MSG, ABEND-CODE='0001' | "UNEXPECTED DATA SCENARIO" (plain text) | SEND TEXT to terminal |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| ABEND (any) | ABEND-ROUTINE | Move ABEND-MSG ("UNEXPECTED ABEND OCCURRED."), set ABEND-CULPRIT='COCRDSLC', SEND FROM(ABEND-DATA), HANDLE ABEND CANCEL, ABEND ABCODE('9999') |

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "Please enter Account and Card Number" | First display (no data), or no info message set | INFOMSG (line 20, col 25) |
| — | "Displaying requested details" | Card found successfully (FOUND-CARDS-FOR-ACCOUNT) | INFOMSG (line 20, col 25) |
| — | "Account number not provided" | Account Number is blank/spaces/zeroes on re-entry | ERRMSG (line 23, col 1) |
| — | "Card number not provided" | Card Number is blank/spaces/zeroes on re-entry | ERRMSG (line 23, col 1) |
| — | "No input received" | Both Account and Card Number are blank on re-entry | ERRMSG (line 23, col 1) |
| — | "Account number must be a non zero 11 digit number" | Account Number is zeroes (SEARCHED-ACCT-ZEROES) | ERRMSG (line 23, col 1) |
| — | "Account number must be a non zero 11 digit number" | Account Number is not numeric (SEARCHED-ACCT-NOT-NUMERIC) | ERRMSG (line 23, col 1) |
| — | "Card number if supplied must be a 16 digit number" | Card Number is not numeric (SEARCHED-CARD-NOT-NUMERIC) | ERRMSG (line 23, col 1) |
| — | "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER" | Account Number not numeric (inline MOVE in 2210-EDIT-ACCOUNT) | ERRMSG (line 23, col 1) |
| — | "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" | Card Number not numeric (inline MOVE in 2220-EDIT-CARD) | ERRMSG (line 23, col 1) |
| — | "Did not find cards for this search condition" | READ CARDDAT returns NOTFND | ERRMSG (line 23, col 1) |
| — | "Did not find this account in cards database" | READ CARDAIX returns NOTFND (alt path) | ERRMSG (line 23, col 1) |
| — | "Error reading Card Data File" | XREF-READ-ERROR condition | ERRMSG (line 23, col 1) |
| — | "File Error: READ on CARDDAT returned RESP XXXX,RESP2 XXXX" | READ CARDDAT returns unexpected RESP | ERRMSG (line 23, col 1) |
| — | "PF03 pressed.Exiting" | PF3 pressed (WS-EXIT-MESSAGE, not displayed — XCTL occurs) | — |
| — | "Looks Good.... so far" | CODING-TO-BE-DONE placeholder (not triggered in normal flow) | ERRMSG (line 23, col 1) |
| — | "UNEXPECTED DATA SCENARIO" | EVALUATE falls to WHEN OTHER | Plain text (SEND TEXT) |
| — | "UNEXPECTED ABEND OCCURRED." | Abend handler triggered | Plain text (SEND FROM ABEND-DATA) |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CCDL transaction must be authorized for user |
| Resource-level security | RACF FACILITY / File profiles for CARDDAT, CARDAIX |
| Application-level check | If EIBCALEN = 0 (no COMMAREA — direct invocation without login) OR (from menu and not re-entering), INITIALIZE COMMAREA → user can still view the screen but has no pre-filled data; CDEMO-USRTYP-USER is set on PF3 exit |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| CARDDAT file | READ | CICS File Control |
| CARDAIX file | READ | CICS File Control |

### 9.3 Audit Trail

| Event | Trigger | Data Logged | Destination |
|-------|---------|-------------|-------------|
| File I/O error | READ returns unexpected RESP | RESP code, REAS code, file name, operation name | WS-FILE-ERROR-MESSAGE (displayed on screen) |
| Abend | Unexpected program abend | ABEND-CODE, ABEND-CULPRIT ('COCRDSLC'), ABEND-MSG | SEND to terminal + CICS ABEND '9999' |
| No explicit audit logging | — | — | Transaction may be audited via CICS journal/SMF records at the system level |

---

## 10. Performance Characteristics

### 10.1 Performance Profile

| Metric | Baseline | Peak | SLA |
|--------|----------|------|-----|
| Response time | < 1 second | < 2 seconds | < 2 seconds |
| CPU per transaction | Very low (validation + 1 READ) | — | — |
| File I/O count | 1 per search (1 READ on CARDDAT) | — | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal | — | — |

### 10.2 Performance Considerations

- **Single READ per search**: Only one VSAM READ per user submit — very efficient for online response times.
- **No browse operations**: Unlike transaction list screens, this program does a direct keyed read with no STARTBR/READNEXT overhead.
- **Read-only operation**: No WRITE or REWRITE commands — zero contention with batch or other update transactions.
- **COMMAREA size**: WS-COMMAREA is PIC X(2000) — relatively small; no TSQ overflow needed.
- **BMS SEND/RECEIVE**: Standard map I/O with ERASE — one pair per pseudo-conversational iteration.
- **Unused code**: Paragraph 9150-GETCARD-BYACCT (alternate index lookup) is defined but never PERFORMed in the active flow — no runtime impact but increases program size slightly.

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Normal view from Credit Card List | Pre-selected Account/Card via COMMAREA from COCRDLIC | Card details displayed (name, status, expiry); fields protected; "Displaying requested details" | High |
| TC-002 | Normal view by manual search | Valid Account Number + valid Card Number, press ENTER | Card details displayed (name, status, expiry); "Displaying requested details" | High |
| TC-003 | Missing Account Number | Leave Account blank, enter Card Number | "Account number not provided"; cursor on Account field | High |
| TC-004 | Missing Card Number | Enter Account Number, leave Card blank | "Card number not provided"; cursor on Card field | High |
| TC-005 | Both fields blank | Leave both Account and Card blank, press ENTER | "No input received"; cursor on Account field | High |
| TC-006 | Non-numeric Account | Enter "ABCDEFGHIJK" in Account Number | "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER" | High |
| TC-007 | Non-numeric Card | Enter alpha in Card Number | "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" | High |
| TC-008 | Card not found | Valid numeric Account + Card that doesn't exist in CARDDAT | "Did not find cards for this search condition" | High |
| TC-009 | PF3 exit to menu | Press PF3 with no caller context | XCTL to COMEN01C (Main Menu) | Medium |
| TC-010 | PF3 exit to card list | Enter via COCRDLIC, press PF3 | XCTL back to COCRDLIC | Medium |
| TC-011 | First entry no COMMAREA | Direct CCDL invocation (EIBCALEN = 0) | Empty form with "Please enter Account and Card Number" | Medium |
| TC-012 | Protected fields from list | Enter via COCRDLIC | Account and Card Number fields are protected (DFHBMPRF) | Medium |
| TC-013 | Unprotected fields from menu | Enter via Main Menu | Account and Card Number fields are unprotected (DFHBMFSE) | Medium |
| TC-014 | Re-search after viewing | View card, then change Card Number, press ENTER | New card details displayed | Low |
| TC-015 | Error field highlighting | Enter non-numeric account | Account field turns red (DFHRED) | Low |
| TC-016 | Blank field marker | Leave account blank on re-entry | Account field shows '*' in red | Low |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| CARDDAT | Pre-loaded with valid card records (CARD-NUM as key, with CARD-EMBOSSED-NAME, CARD-ACTIVE-STATUS, CARD-EXPIRAION-DATE) | VSAM REPRO from test data (`app/data/`) |
| CARDAIX (optional) | Alternate index built over CARDDAT by account ID | VSAM DEFINE AIX + BLDINDEX |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | Paragraph 9150-GETCARD-BYACCT is defined but never PERFORMed — dead code for alternate index lookup by account ID | No functional impact; increases code maintenance burden | Remove or enable via feature flag |
| KI-002 | Account Number validation accepts any non-zero numeric 11-digit value but does not verify the account actually exists in an account master file | User may enter a valid-looking but non-existent account; the card lookup by card number may still succeed | Add cross-reference validation against ACCTDAT or CARDAIX |
| KI-003 | The CARD-EXPIRAION-DATE field uses a redefine with format YYYY-MM-DD (10 chars), but the display splits into separate EXPMON (month) and EXPYEAR (year) fields — the day portion is not displayed | Expiry day information is lost in the UI | Add a day field or change to MM/YYYY display explicitly |
| KI-004 | Invalid PF keys are silently remapped to ENTER rather than showing an error message | User may not realize they pressed an invalid key | Add explicit "Invalid key pressed" message for non-ENTER/non-PF3 keys |
| KI-005 | The 88-level messages WS-PROMPT-FOR-INPUT and FOUND-CARDS-FOR-ACCOUNT share the same WS-INFO-MSG field; the prompt is overwritten if both conditions occur in sequence | Minor — logic correctly handles the sequence | No workaround needed |
| KI-006 | Error messages in 2210-EDIT-ACCOUNT and 2220-EDIT-CARD use different wording than the 88-level condition names (e.g., inline 'ACCOUNT FILTER,...' vs. 88-level 'Account number must be a non zero 11 digit number') | Inconsistent error messages depending on code path | Standardize on one set of error messages |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Screen I/O | BMS Map CCRDSLA / 3270 terminal | Web page / REST API GET endpoint | Low — simple display screen with 6 output fields |
| Data Access | VSAM KSDS direct (CARDDAT) | Service layer with RDBMS (e.g., RDS PostgreSQL) | Low — single READ by primary key |
| Input Validation | Inline COBOL IF/EVALUATE with 88-level conditions | Framework validation (Bean Validation, JSON Schema) | Low — only 2 input fields to validate |
| State Management | COMMAREA pseudo-conversational (2000-byte buffer) | HTTP session or stateless REST with query parameters | Low — minimal state (Account ID + Card Number) |
| Navigation | XCTL between COBOL programs (COCRDLIC → COCRDSLC → COMEN01C) | Client-side routing or REST API calls | Low |
| Field Protection | BMS DFHBMPRF/DFHBMFSE dynamic attribute setting | HTML readonly/disabled attributes or UI role-based rendering | Low |
| Error Handling | HANDLE ABEND + ABEND ABCODE('9999') | Try/catch with structured error responses (HTTP 4xx/5xx) | Low |
| Dead Code | 9150-GETCARD-BYACCT (unused alternate index read) | Remove or implement as an alternative search endpoint | Low |
| Character Encoding | EBCDIC (native mainframe) | UTF-8 (modern systems) | Low — all fields are simple alphanumeric |

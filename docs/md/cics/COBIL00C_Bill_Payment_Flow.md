# Bill Payment (CB00) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CB00 |
| Entry Program | COBIL00C |
| Business Domain | Bill Payment / Account Servicing |
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

- **What**: Allows an authenticated user to pay the full outstanding balance on a credit card account. A bill-payment transaction record is written to the TRANSACT file and the account's current balance is reduced to zero.
- **Who**: Any authenticated CardDemo user (regular users and admins) — menu option 10 ("Bill Payment").
- **When**: Real-time, on-demand when a cardholder or operator wishes to settle the account balance in full.
- **Why**: Provides an online self-service channel for immediate full-balance bill payment, avoiding the batch payment cycle.
- **What** is the business outcome: A new transaction record of type '02' (Bill Payment) is written to the TRANSACT VSAM file with an auto-generated Transaction ID, and the account's current balance in ACCTDAT is reduced by the payment amount (set to zero). The user receives a confirmation message with the assigned Transaction ID.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  User selects Option 10 ("Bill Payment") from Main Menu
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 1            │  Enter Account ID to identify the account
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ System Response          │  READ ACCTDAT to retrieve account record;
  └────────────┬─────────────┘  display current balance on screen
               │
           ┌───┴───┐
           │Decision│  Balance > 0?
           └───┬───┘
              ╱ ╲
        Yes ╱     ╲ No
           ▼       ▼
  ┌──────────────┐  ┌─────────────────────────┐
  │ Prompt user  │  │ "You have nothing to    │
  │ to confirm   │  │  pay..." — no action    │
  │ payment(Y/N) │  └─────────────────────────┘
  └──────┬───────┘
         │
     ┌───┴───┐
     │  Y/N? │
     └───┬───┘
        ╱ ╲
  Yes ╱     ╲ No
     ▼       ▼
┌───────────────────────────┐  ┌──────────────────────────────────┐
│ 1. READ CXACAIX for card# │  │ Screen cleared; user may         │
│ 2. STARTBR/READPREV to    │  │ re-enter or exit                 │
│    get last Tran ID       │  └──────────────────────────────────┘
│ 3. Generate new Tran ID   │
│ 4. WRITE TRANSACT record  │
│ 5. REWRITE ACCTDAT with   │
│    balance reduced to 0   │
└───────────┬───────────────┘
            ▼
  ┌──────────────────────────┐
  │ Business Outcome         │  "Payment successful. Your Transaction ID is XXXXXXXX."
  └──────────────────────────┘   Screen fields cleared for next entry.
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | Account ID required | Account ID field is empty (spaces or low-values) | Error: "Acct ID can NOT be empty..." |
| BR-002 | Account must exist | Account ID must exist in ACCTDAT VSAM file | Error: "Account ID NOT found..." |
| BR-003 | Balance must be positive | Account current balance must be > 0 | Error: "You have nothing to pay..." |
| BR-004 | Confirmation required | User must confirm with 'Y' before payment | Prompt: "Confirm to make a bill payment..." |
| BR-005 | Confirmation value validation | Confirm field must be Y, y, N, n, or blank | Error: "Invalid value. Valid values are (Y/N)..." |
| BR-006 | Auto-generated Transaction ID | New Tran ID = highest existing Tran ID + 1 | STARTBR with HIGH-VALUES, READPREV to find max, add 1 |
| BR-007 | Full balance payment | Payment amount equals the entire current balance | TRAN-AMT = ACCT-CURR-BAL; new balance = 0 |
| BR-008 | Account balance updated immediately | After payment, ACCTDAT record is rewritten | ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT |
| BR-009 | Card number resolved from XREF | Card number for the transaction is looked up from CXACAIX | READ CXACAIX by XREF-ACCT-ID to obtain XREF-CARD-NUM |
| BR-010 | Transaction record metadata | Payment transaction is coded as Type '02', Category 2, Source 'POS TERM', Description 'BILL PAYMENT - ONLINE' | Hard-coded values in program |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Select Option 10 from Main Menu | XCTL to COBIL00C; display empty Bill Payment screen (COBIL0A) | Navigate to bill payment |
| 2 | Enter Account ID, press ENTER | READ ACCTDAT; display current balance; prompt for confirmation | Identify target account and show balance |
| 3a | Type 'Y' in Confirm field, press ENTER | Lookup card XREF, generate Tran ID, WRITE transaction, REWRITE account, display success | Payment committed |
| 3b | Type 'N' in Confirm field, press ENTER | Clear screen fields | User cancels payment |
| 3c | Leave Confirm blank, press ENTER | Display "Confirm to make a bill payment..." | User has not yet confirmed |
| Alt-A | Press PF3 | XCTL back to previous screen (or Main Menu COMEN01C) | Exit without paying |
| Alt-B | Press PF4 | Clear all input fields | Reset form |
| Alt-C | Press unrecognized key | Display "Invalid key pressed. Please see below..." | Invalid key feedback |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CB00
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COBIL00C     │ (Entry & only online program)      │
  │  │ (Bill Pay)   │                                    │
  │  └──┬────┬──┬───┘                                    │
  │     │    │  │                                         │
  │     ▼    │  ▼                                         │
  │  ┌──────┐│ ┌────────────────────────────────────┐    │
  │  │BMS   ││ │ VSAM Files                         │    │
  │  │MAP:  ││ │  ACCTDAT  (Account Master)         │    │
  │  │COBIL ││ │  CXACAIX  (Acct→Card XREF AIX)    │    │
  │  │0A    ││ │  TRANSACT (Transaction Master)     │    │
  │  └──────┘│ └────────────────────────────────────┘    │
  │          │                                            │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1 (First entry — from Main Menu via XCTL):
  1. COMEN01C XCTLs to COBIL00C with CARDDEMO-COMMAREA
  2. EIBCALEN > 0; CDEMO-PGM-REENTER = 0 (first entry)
  3. SET CDEMO-PGM-REENTER TO TRUE
  4. MOVE LOW-VALUES TO COBIL0AO (clear output map)
  5. Set cursor to Account ID field (ACTIDINL = -1)
  6. If CDEMO-CB00-TRN-SELECTED is populated (pre-selected from another screen):
     └── Pre-fill Account ID into ACTIDINI, PERFORM PROCESS-ENTER-KEY
  7. PERFORM SEND-BILLPAY-SCREEN
     └── SEND MAP('COBIL0A') MAPSET('COBIL00') ERASE CURSOR
  8. RETURN TRANSID('CB00') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 2 (User submits data — ENTER pressed):
  9.  CICS dispatches → COBIL00C (COMMAREA restored)
  10. EIBCALEN > 0; CDEMO-PGM-REENTER = 1
  11. RECEIVE MAP('COBIL0A') INTO(COBIL0AI)
  12. EIBAID = DFHENTER → PERFORM PROCESS-ENTER-KEY
  13. Validate Account ID:
      └── Empty? → Error "Acct ID can NOT be empty..."
  14. MOVE ACTIDINI → ACCT-ID and XREF-ACCT-ID
  15. Check CONFIRM field:
      ├── 'Y'/'y' → SET CONF-PAY-YES, PERFORM READ-ACCTDAT-FILE
      ├── 'N'/'n' → PERFORM CLEAR-CURRENT-SCREEN, set ERR-FLG
      ├── Spaces/LOW-VALUES → PERFORM READ-ACCTDAT-FILE (display balance)
      └── Other → Error "Invalid value. Valid values are (Y/N)..."
  16. Display current balance: ACCT-CURR-BAL → WS-CURR-BAL → CURBALI
  17. Check balance > 0:
      └── Balance <= 0 → Error "You have nothing to pay..."

  Iteration 2a (Payment — when CONF-PAY-YES):
  18. PERFORM READ-CXACAIX-FILE (get card number for TRAN-CARD-NUM)
  19. MOVE HIGH-VALUES TO TRAN-ID
  20. PERFORM STARTBR-TRANSACT-FILE (position to end)
  21. PERFORM READPREV-TRANSACT-FILE (get last/highest TRAN-ID)
  22. PERFORM ENDBR-TRANSACT-FILE (close browse)
  23. ADD 1 to TRAN-ID → new unique TRAN-ID
  24. INITIALIZE TRAN-RECORD, populate all fields:
      ├── TRAN-TYPE-CD = '02'
      ├── TRAN-CAT-CD = 2
      ├── TRAN-SOURCE = 'POS TERM'
      ├── TRAN-DESC = 'BILL PAYMENT - ONLINE'
      ├── TRAN-AMT = ACCT-CURR-BAL
      ├── TRAN-CARD-NUM = XREF-CARD-NUM
      ├── TRAN-MERCHANT-ID = 999999999
      ├── TRAN-MERCHANT-NAME = 'BILL PAYMENT'
      ├── TRAN-MERCHANT-CITY = 'N/A'
      ├── TRAN-MERCHANT-ZIP = 'N/A'
      └── TRAN-ORIG-TS / TRAN-PROC-TS = current timestamp
  25. PERFORM WRITE-TRANSACT-FILE
      ├── NORMAL → clear screen, display success with Tran ID
      ├── DUPREC → "Tran ID already exist..."
      └── OTHER → "Unable to Add Bill pay Transaction..."
  26. COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT
  27. PERFORM UPDATE-ACCTDAT-FILE (REWRITE account record)
  28. RETURN TRANSID('CB00') COMMAREA(CARDDEMO-COMMAREA)
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | >0 | XCTL from menu (option 10) | Initial logic (PGM-ENTER) | COBIL0A (empty form) | CB00 |
| 2 | >0 | Enter Account ID, press ENTER | RECEIVE → PROCESS-ENTER-KEY | COBIL0A (balance shown, confirm prompt) | CB00 |
| 3 | >0 | Confirm Y + ENTER | PROCESS-ENTER-KEY → payment logic | COBIL0A (success message, cleared) | CB00 |
| N | >0 | PF3 (Exit) | XCTL to COMEN01C (menu) | — | — (XCTL) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | RECEIVE MAP('COBIL0A') MAPSET('COBIL00') INTO(COBIL0AI) | Capture user input from terminal | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 2 | READ DATASET('ACCTDAT') RIDFLD(ACCT-ID) UPDATE | Read account record (with update intent for REWRITE) | NORMAL → continue; NOTFND → error; OTHER → error |
| 3 | READ DATASET('CXACAIX') RIDFLD(XREF-ACCT-ID) | Lookup card number by account ID | NORMAL → continue; NOTFND → error; OTHER → error |
| 4 | STARTBR DATASET('TRANSACT') RIDFLD(TRAN-ID) | Position browse at end of file (HIGH-VALUES) | NORMAL → continue; NOTFND/OTHER → error |
| 5 | READPREV DATASET('TRANSACT') INTO(TRAN-RECORD) | Read last (highest) transaction record | NORMAL → got last ID; ENDFILE → set TRAN-ID to ZEROS |
| 6 | ENDBR DATASET('TRANSACT') | End browse session | — |
| 7 | WRITE DATASET('TRANSACT') FROM(TRAN-RECORD) RIDFLD(TRAN-ID) | Write new bill payment transaction record | NORMAL → success; DUPREC → error; OTHER → error |
| 8 | REWRITE DATASET('ACCTDAT') FROM(ACCOUNT-RECORD) | Update account balance to zero | NORMAL → continue; NOTFND → error; OTHER → error |
| 9 | ASKTIME ABSTIME(WS-ABS-TIME) | Get current absolute time for timestamp | — |
| 10 | FORMATTIME ABSTIME(WS-ABS-TIME) YYYYMMDD DATESEP TIME TIMESEP | Format timestamp into date and time strings | — |
| 11 | SEND MAP('COBIL0A') MAPSET('COBIL00') FROM(COBIL0AO) ERASE CURSOR | Send screen to terminal | — |
| 12 | RETURN TRANSID('CB00') COMMAREA(CARDDEMO-COMMAREA) | Pseudo-conversational return | — |
| 13 | XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA) | Transfer to previous screen on PF3 | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────┐                ┌─────────────────────────────────────┐
  │  Main Menu  │   Option 10    │  COBIL0A (Bill Payment)             │
  │  (COMEN01C) │───────────────▶│                                     │
  └─────────────┘                │  Enter Acct ID: ___________         │
        ▲                        │  ─────────────────────────────────   │
        │ PF3                    │                                     │
        │                        │  Your current balance is: __________│
        │                        │                                     │
        │                        │  Do you want to pay your balance    │
        │                        │  now. Please confirm: _ (Y/N)       │
        │                        │                                     │
        │                        │  [Error/Success message line]       │
        │                        │  ENTER=Continue  F3=Back  F4=Clear  │
        └────────────────────────┤                                     │
                                 └─────────────────────────────────────┘
                                   │         │
                                   │ ENTER   │ PF4 (Clear)
                                   │ (loops  │ (resets form,
                                   │  back   │  loops back)
                                   │  to     │
                                   │  self)  │
                                   ▼         ▼
                                 [Same screen — COBIL0A]
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| COBIL0A | COBIL00 | Bill Payment input/confirm screen | Account ID, Current Balance (display), Confirm | ENTER=Submit, PF3=Back, PF4=Clear |

### 3.3 Screen Field Details

#### Map: COBIL0A

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction name (CB00) |
| TITLE01 | ASKIP,FSET,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,FSET,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | ASKIP,FSET,NORM | 8 | Alpha | — | Program name (COBIL00C) |
| TITLE02 | ASKIP,FSET,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | ASKIP,FSET,NORM | 8 | Time | — | Current time hh:mm:ss |
| ACTIDIN | FSET,IC,NORM,UNPROT | 11 | AlphaNum | Non-empty; must exist in ACCTDAT | Account ID |
| CURBAL | ASKIP,FSET,NORM | 14 | Signed Decimal | — (display only) | Current account balance |
| CONFIRM | FSET,NORM,UNPROT | 1 | Alpha | Y/N only | Payment confirmation flag |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/success message display |

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | COBIL0A | Validate Account ID; if valid and confirmed, process bill payment |
| PF3 | COBIL0A | Return to previous screen (CDEMO-FROM-PROGRAM or Main Menu COMEN01C) via XCTL |
| PF4 | COBIL0A | Clear all input fields (INITIALIZE-ALL-FIELDS), redisplay empty form |
| OTHER | COBIL0A | Display "Invalid key pressed. Please see below..." |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COBIL00C | Bill Payment — screen handler, validation, payment processing, VSAM write/rewrite | TRANSID CB00 / XCTL from COMEN01C | 572 | `app/cbl/COBIL00C.cbl` |
| COMEN01C | Main Menu — entry point for option 10 | XCTL (caller) | — | `app/cbl/COMEN01C.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CB00
└── COBIL00C (Entry — Screen Handler, Validation, Payment Processing)
    ├── READ FILE('ACCTDAT') — Account master lookup (with UPDATE intent)
    ├── READ FILE('CXACAIX') — Account-to-Card cross-reference lookup
    ├── STARTBR FILE('TRANSACT') — Browse to find max Tran ID
    ├── READPREV FILE('TRANSACT') — Read last record for ID generation
    ├── ENDBR FILE('TRANSACT')
    ├── WRITE FILE('TRANSACT') — Write new bill payment transaction record
    ├── REWRITE FILE('ACCTDAT') — Update account balance to zero
    ├── ASKTIME / FORMATTIME — Get current timestamp for transaction record
    ├── SEND MAP('COBIL0A') — Display screen
    ├── RECEIVE MAP('COBIL0A') — Capture input
    ├── RETURN TRANSID('CB00') — Pseudo-conversational return
    └── XCTL → COMEN01C or CDEMO-FROM-PROGRAM (on PF3 exit)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COMEN01C | COBIL00C | XCTL | CARDDEMO-COMMAREA | — | User selects menu option 10 |
| COBIL00C | COMEN01C | XCTL | CARDDEMO-COMMAREA | — | PF3 exit back to main menu |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| Account Master | ACCTDAT | VSAM KSDS | READ (UPDATE) / REWRITE | COBIL00C | Look up account balance; update balance after payment |
| Card Cross-Reference (AIX) | CXACAIX | VSAM KSDS (Alternate Index) | READ | COBIL00C | Lookup card number by account ID for transaction record |
| Transaction Master | TRANSACT | VSAM KSDS | BROWSE / WRITE | COBIL00C | Read last ID for generation; write new bill payment record |

### 5.2 File Access Details

#### File: ACCTDAT (Account Master)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CVACT01Y (ACCOUNT-RECORD, 300 bytes) |
| Key | ACCT-ID (PIC 9(11)) |
| Access Mode | READ with UPDATE, REWRITE |
| Locking | Record-level lock held from READ UPDATE through REWRITE |
| Shared With | COACTVWC (Account View), COACTUPC (Account Update), Batch programs |

**Access Pattern**:
| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COBIL00C | READ DATASET('ACCTDAT') RIDFLD(ACCT-ID) UPDATE | Account ID from screen (ACTIDINI) | Returns ACCT-CURR-BAL (and full ACCOUNT-RECORD for REWRITE) |
| 2 | COBIL00C | REWRITE DATASET('ACCTDAT') FROM(ACCOUNT-RECORD) | Same record from READ UPDATE | Updates ACCT-CURR-BAL (reduced by payment amount) |

#### File: CXACAIX (Account-to-Card Cross-Reference AIX)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS (Alternate Index over CCXREF) |
| Record Layout (Copybook) | CVACT03Y (CARD-XREF-RECORD, 50 bytes) |
| Key | XREF-ACCT-ID (PIC 9(11), offset via alternate index) |
| Access Mode | READ |
| Locking | No update, read-only |
| Shared With | COTRN02C (Add Transaction), COTRN01C (View Transaction), COTRN00C (List Transactions) |

**Access Pattern**:
| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COBIL00C | READ DATASET('CXACAIX') RIDFLD(XREF-ACCT-ID) | Account ID from screen (ACTIDINI) | Returns XREF-CARD-NUM (used as TRAN-CARD-NUM) |

#### File: TRANSACT (Transaction Master)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CVTRA05Y (TRAN-RECORD, 350 bytes) |
| Key | TRAN-ID (PIC X(16)) |
| Access Mode | BROWSE (STARTBR/READPREV/ENDBR) and WRITE |
| Locking | Record-level for WRITE |
| Shared With | COTRN00C (List), COTRN01C (View), COTRN02C (Add Transaction), Batch Post-Tran |

**Access Pattern**:
| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COBIL00C | STARTBR RIDFLD(TRAN-ID = HIGH-VALUES) | Position to end of file | — |
| 2 | COBIL00C | READPREV INTO(TRAN-RECORD) | Read last record backwards | TRAN-ID (to derive next ID) |
| 3 | COBIL00C | ENDBR | Close browse | — |
| 4 | COBIL00C | WRITE FROM(TRAN-RECORD) RIDFLD(TRAN-ID) | New TRAN-ID = last + 1 | All TRAN-RECORD fields |

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
  Terminal Input (COBIL0A)
       │
       ├── ACTIDINI (Account ID)
       │       │
       │       ├──▶ ACCT-ID (key for ACCTDAT READ)
       │       │       │
       │       │       └──▶ READ ACCTDAT → ACCOUNT-RECORD
       │       │                │
       │       │                ├──▶ ACCT-CURR-BAL → WS-CURR-BAL → CURBALI (display)
       │       │                └──▶ ACCT-CURR-BAL → TRAN-AMT (payment amount)
       │       │
       │       └──▶ XREF-ACCT-ID (key for CXACAIX READ)
       │                │
       │                └──▶ READ CXACAIX → CARD-XREF-RECORD
       │                         │
       │                         └──▶ XREF-CARD-NUM → TRAN-CARD-NUM
       │
       └── CONFIRMI ('Y'/'N')
               │
               └──▶ Controls whether payment processing executes

  Transaction ID Generation:
       STARTBR TRANSACT (HIGH-VALUES)
            │
            └──▶ READPREV → last TRAN-ID
                      │
                      └──▶ WS-TRAN-ID-NUM + 1 → new TRAN-ID
                                │
                                └──▶ TRAN-RECORD.TRAN-ID

  Transaction Record Population (hard-coded values):
       ├── TRAN-TYPE-CD     = '02'
       ├── TRAN-CAT-CD      = 2
       ├── TRAN-SOURCE       = 'POS TERM'
       ├── TRAN-DESC         = 'BILL PAYMENT - ONLINE'
       ├── TRAN-MERCHANT-ID  = 999999999
       ├── TRAN-MERCHANT-NAME = 'BILL PAYMENT'
       ├── TRAN-MERCHANT-CITY = 'N/A'
       ├── TRAN-MERCHANT-ZIP  = 'N/A'
       └── TRAN-ORIG-TS / TRAN-PROC-TS = GET-CURRENT-TIMESTAMP result

  Account Update:
       COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT
            │
            └──▶ REWRITE ACCTDAT (balance now zero)
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| ACTIDINI | Terminal (COBIL0AI) | Direct move | ACCT-ID | READ key for ACCTDAT |
| ACTIDINI | Terminal (COBIL0AI) | Direct move | XREF-ACCT-ID | READ key for CXACAIX |
| ACCT-CURR-BAL | ACCOUNT-RECORD (ACCTDAT) | MOVE to WS-CURR-BAL (PIC +9999999999.99) | CURBALI | Output map field (display) |
| ACCT-CURR-BAL | ACCOUNT-RECORD (ACCTDAT) | Direct move | TRAN-AMT | TRAN-RECORD field |
| XREF-CARD-NUM | CARD-XREF-RECORD (CXACAIX) | Direct move | TRAN-CARD-NUM | TRAN-RECORD field |
| Last TRAN-ID | READPREV TRANSACT | WS-TRAN-ID-NUM + 1 | New TRAN-ID | TRAN-RECORD.TRAN-ID |
| '02' | Hard-coded literal | Direct move | TRAN-TYPE-CD | TRAN-RECORD field |
| 2 | Hard-coded literal | Direct move | TRAN-CAT-CD | TRAN-RECORD field |
| 'POS TERM' | Hard-coded literal | Direct move | TRAN-SOURCE | TRAN-RECORD field |
| 'BILL PAYMENT - ONLINE' | Hard-coded literal | Direct move | TRAN-DESC | TRAN-RECORD field |
| 999999999 | Hard-coded literal | Direct move | TRAN-MERCHANT-ID | TRAN-RECORD field |
| 'BILL PAYMENT' | Hard-coded literal | Direct move | TRAN-MERCHANT-NAME | TRAN-RECORD field |
| 'N/A' | Hard-coded literal | Direct move | TRAN-MERCHANT-CITY | TRAN-RECORD field |
| 'N/A' | Hard-coded literal | Direct move | TRAN-MERCHANT-ZIP | TRAN-RECORD field |
| ASKTIME/FORMATTIME | CICS timestamp services | WS-CUR-DATE-X10 + WS-CUR-TIME-X08 → WS-TIMESTAMP | TRAN-ORIG-TS / TRAN-PROC-TS | TRAN-RECORD fields |
| ACCT-CURR-BAL - TRAN-AMT | COMPUTE | Arithmetic subtraction | ACCT-CURR-BAL | ACCOUNT-RECORD (REWRITE) |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`) plus a program-specific extension `CDEMO-CB00-INFO`:

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

**CDEMO-CB00-INFO (program-specific extension, defined inline in COBIL00C):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-CB00-TRNID-FIRST | X(16) | 16 | IN/OUT | First transaction ID (pagination context) |
| CDEMO-CB00-TRNID-LAST | X(16) | 16 | IN/OUT | Last transaction ID (pagination context) |
| CDEMO-CB00-PAGE-NUM | 9(08) | 8 | IN/OUT | Page number |
| CDEMO-CB00-NEXT-PAGE-FLG | X(01) | 1 | IN/OUT | 'Y'/'N' — more pages available |
| CDEMO-CB00-TRN-SEL-FLG | X(01) | 1 | IN | Transaction selection flag |
| CDEMO-CB00-TRN-SELECTED | X(16) | 16 | IN | Pre-selected transaction (from another screen) |

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. Errors are handled inline via screen messages (WS-MESSAGE → ERRMSGO). All file I/O results are checked via CICS RESP codes within EVALUATE blocks.

### 7.3 Conversation State Management

- **COMMAREA preservation**: The entire CARDDEMO-COMMAREA (including CB00-specific fields) is passed on every RETURN TRANSID('CB00'). CICS restores it automatically on the next dispatch.
- **Screen field state**: BMS FSET attribute ensures all fields are returned on every RECEIVE, preserving user-entered data across iterations.
- **No TSQ overflow**: All state fits within the COMMAREA; no temporary storage queues are used.
- **PGM-CONTEXT flag**: Distinguishes first entry (0 — display empty screen) from subsequent interactions (1 — process input).
- **READ UPDATE lock**: The ACCTDAT record is read with UPDATE intent, holding a VSAM record lock until the REWRITE completes. This ensures data integrity for the balance update within a single pseudo-conversational iteration.

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path:
    Input → Validate Acct ID → READ ACCTDAT → Check Balance > 0
         → Confirm 'Y' → READ CXACAIX → Generate ID
         → WRITE TRANSACT → REWRITE ACCTDAT → Success

  Validation Error (Account ID):
    Input → Validate ──EMPTY──▶ "Acct ID can NOT be empty..."
                                → Cursor to ACTIDIN, RETURN TRANSID

  Account Lookup Error:
    Input → READ ACCTDAT ──NOTFND──▶ "Account ID NOT found..."
                                     → Cursor to ACTIDIN, RETURN TRANSID

  Balance Check Error:
    Input → Balance <= 0 ──▶ "You have nothing to pay..."
                             → Cursor to ACTIDIN, RETURN TRANSID

  Confirmation Error:
    Input → Confirm = 'N'/'n'    ──▶ Clear screen
    Input → Confirm = spaces     ──▶ "Confirm to make a bill payment..."
    Input → Confirm = other      ──▶ "Invalid value. Valid values are (Y/N)..."

  XREF Lookup Error:
    READ CXACAIX ──NOTFND──▶ "Account ID NOT found..."
    READ CXACAIX ──OTHER──▶  "Unable to lookup XREF AIX file..."

  Transaction Browse Error:
    STARTBR ──NOTFND──▶ "Transaction ID NOT found..."
    STARTBR ──OTHER──▶  "Unable to lookup Transaction..."
    READPREV ──OTHER──▶ "Unable to lookup Transaction..."

  Write Error:
    WRITE ──DUPREC/DUPKEY──▶ "Tran ID already exist..."
    WRITE ──OTHER──▶         "Unable to Add Bill pay Transaction..."

  Account Update Error:
    REWRITE ──NOTFND──▶ "Account ID NOT found..."
    REWRITE ──OTHER──▶  "Unable to Update Account..."
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| Account not found in ACCTDAT | NOTFND (13) | Set ERR-FLG, cursor to ACTIDIN | "Account ID NOT found..." | — |
| ACCTDAT read error | OTHER | Set ERR-FLG, cursor to ACTIDIN | "Unable to lookup Account..." | DISPLAY RESP/REAS to SYSOUT |
| Account not found in CXACAIX | NOTFND (13) | Set ERR-FLG, cursor to ACTIDIN | "Account ID NOT found..." | — |
| CXACAIX read error | OTHER | Set ERR-FLG, cursor to ACTIDIN | "Unable to lookup XREF AIX file..." | DISPLAY RESP/REAS to SYSOUT |
| TRANSACT browse error (STARTBR) | NOTFND | Set ERR-FLG, cursor to ACTIDIN | "Transaction ID NOT found..." | — |
| TRANSACT browse error (STARTBR) | OTHER | Set ERR-FLG, cursor to ACTIDIN | "Unable to lookup Transaction..." | DISPLAY RESP/REAS to SYSOUT |
| TRANSACT READPREV error | OTHER | Set ERR-FLG, cursor to ACTIDIN | "Unable to lookup Transaction..." | DISPLAY RESP/REAS to SYSOUT |
| TRANSACT READPREV ENDFILE | ENDFILE | Set TRAN-ID = ZEROS (first record) | — | — |
| Duplicate transaction ID | DUPREC/DUPKEY | Set ERR-FLG, cursor to ACTIDIN | "Tran ID already exist..." | — |
| TRANSACT WRITE error | OTHER | Set ERR-FLG, cursor to ACTIDIN | "Unable to Add Bill pay Transaction..." | DISPLAY RESP/REAS to SYSOUT |
| Account not found on REWRITE | NOTFND (13) | Set ERR-FLG, cursor to ACTIDIN | "Account ID NOT found..." | — |
| ACCTDAT REWRITE error | OTHER | Set ERR-FLG, cursor to ACTIDIN | "Unable to Update Account..." | DISPLAY RESP/REAS to SYSOUT |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| — | No explicit HANDLE ABEND | Default CICS abend handling applies |

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "Acct ID can NOT be empty..." | ACTIDINI is spaces or low-values | ERRMSG (line 23, col 1) |
| — | "Account ID NOT found..." | ACCTDAT or CXACAIX READ returns NOTFND | ERRMSG (line 23, col 1) |
| — | "Unable to lookup Account..." | ACCTDAT READ returns unexpected RESP | ERRMSG (line 23, col 1) |
| — | "You have nothing to pay..." | ACCT-CURR-BAL <= 0 | ERRMSG (line 23, col 1) |
| — | "Confirm to make a bill payment..." | CONFIRM is spaces/low-values (not yet confirmed) | ERRMSG (line 23, col 1) |
| — | "Invalid value. Valid values are (Y/N)..." | CONFIRM is not Y/y/N/n/space | ERRMSG (line 23, col 1) |
| — | "Unable to lookup XREF AIX file..." | CXACAIX READ returns unexpected RESP | ERRMSG (line 23, col 1) |
| — | "Transaction ID NOT found..." | STARTBR returns NOTFND | ERRMSG (line 23, col 1) |
| — | "Unable to lookup Transaction..." | STARTBR or READPREV returns unexpected RESP | ERRMSG (line 23, col 1) |
| — | "Tran ID already exist..." | WRITE returns DUPREC/DUPKEY | ERRMSG (line 23, col 1) |
| — | "Unable to Add Bill pay Transaction..." | WRITE returns unexpected RESP | ERRMSG (line 23, col 1) |
| — | "Account ID NOT found..." | REWRITE returns NOTFND | ERRMSG (line 23, col 1) |
| — | "Unable to Update Account..." | REWRITE returns unexpected RESP | ERRMSG (line 23, col 1) |
| — | "Payment successful. Your Transaction ID is XXXX." | Successful WRITE | ERRMSG (line 23, col 1), color GREEN |
| — | "Invalid key pressed. Please see below..." | Unrecognized AID key (not ENTER, PF3, PF4) | ERRMSG (line 23, col 1) |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CB00 transaction must be authorized for user |
| Resource-level security | RACF FACILITY / File profiles for ACCTDAT, CXACAIX, TRANSACT |
| Application-level check | If EIBCALEN = 0 (no COMMAREA — direct invocation without login), XCTL to sign-on screen COSGN00C |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| ACCTDAT file | READ and UPDATE (for READ UPDATE + REWRITE) | CICS File Control |
| CXACAIX file | READ | CICS File Control |
| TRANSACT file | READ (browse) and UPDATE (for WRITE) | CICS File Control |

### 9.3 Audit Trail

| Event | Trigger | Data Logged | Destination |
|-------|---------|-------------|-------------|
| Unexpected RESP codes | File I/O error (non-NORMAL, non-NOTFND) | RESP code, REAS code | DISPLAY → SYSOUT (CICS system log) |
| Bill payment transaction written | Successful WRITE to TRANSACT | Full TRAN-RECORD with TRAN-ID, amount, timestamp | TRANSACT VSAM file (permanent record) |
| No explicit audit logging | — | — | Transaction may be audited via CICS journal/SMF records at the system level |

---

## 10. Performance Characteristics

### 10.1 Performance Profile

| Metric | Baseline | Peak | SLA |
|--------|----------|------|-----|
| Response time | < 1 second | < 2 seconds | < 2 seconds |
| CPU per transaction | Low (validation + 1 WRITE + 1 REWRITE) | — | — |
| File I/O count | 6–7 per payment (1 ACCTDAT READ + 1 CXACAIX READ + STARTBR + READPREV + ENDBR + WRITE + REWRITE) | — | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal | — | — |

### 10.2 Performance Considerations

- **READ UPDATE lock duration**: The ACCTDAT record is locked from READ UPDATE until REWRITE. During the confirmed payment path, the lock spans the CXACAIX read, TRANSACT browse, TRANSACT write, and REWRITE — a relatively short window but could cause contention under high concurrency.
- **STARTBR/READPREV pattern**: Browse to HIGH-VALUES then READPREV to find the maximum TRAN-ID. Same pattern as COTRN02C — potential bottleneck under concurrent bill payments if multiple users attempt to generate IDs simultaneously.
- **Single WRITE + single REWRITE per payment**: Two write I/Os total — moderate overhead.
- **CXACAIX lookup**: One READ per payment — acceptable for online response times.
- **COMMAREA size**: Relatively small; no TSQ overflow needed.
- **BMS SEND/RECEIVE**: Standard map I/O with ERASE — one pair per pseudo-conversational iteration.
- **Timestamp generation**: ASKTIME + FORMATTIME — negligible overhead.

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Normal bill payment | Valid Account ID with positive balance, Confirm=Y | "Payment successful. Your Transaction ID is XXXX." Screen cleared. | High |
| TC-002 | Account ID empty | Leave Account ID blank, press ENTER | "Acct ID can NOT be empty..." | High |
| TC-003 | Account not found | Non-existent Account ID | "Account ID NOT found..." | High |
| TC-004 | Zero balance | Account with ACCT-CURR-BAL = 0 | "You have nothing to pay..." | High |
| TC-005 | Negative balance | Account with ACCT-CURR-BAL < 0 | "You have nothing to pay..." | High |
| TC-006 | Confirm N | Valid Account ID, Confirm=N | Screen cleared (cancel payment) | Medium |
| TC-007 | Confirm blank | Valid Account ID, Confirm=blank | "Confirm to make a bill payment..." | Medium |
| TC-008 | Confirm invalid | Valid Account ID, Confirm=X | "Invalid value. Valid values are (Y/N)..." | Medium |
| TC-009 | Account exists in ACCTDAT but not CXACAIX | Account ID not in cross-reference | "Account ID NOT found..." (from CXACAIX lookup) | Medium |
| TC-010 | PF3 exit | Press PF3 at any point | Return to Main Menu (COMEN01C) or previous screen | Medium |
| TC-011 | PF4 clear | Press PF4 with data on screen | All fields cleared | Low |
| TC-012 | Invalid PF key | Press PF2, PF5, etc. | "Invalid key pressed. Please see below..." | Low |
| TC-013 | Pre-selected account from another screen | CDEMO-CB00-TRN-SELECTED populated | Account ID pre-filled, balance displayed | Medium |
| TC-014 | Verify account balance updated | After successful payment, verify ACCTDAT | ACCT-CURR-BAL = 0 | High |
| TC-015 | Verify transaction record | After successful payment, verify TRANSACT | New record with TRAN-TYPE-CD='02', correct amount and timestamp | High |
| TC-016 | Direct invocation (no COMMAREA) | Invoke CB00 without sign-on | XCTL to COSGN00C (sign-on screen) | Medium |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| ACCTDAT | Pre-loaded with accounts having various balances (positive, zero, negative) | VSAM REPRO from test data (`app/data/`) |
| CXACAIX | Alternate index built over CCXREF by account ID; entries for test accounts | VSAM DEFINE AIX + BLDINDEX |
| TRANSACT | Pre-loaded with at least one existing transaction record (for ID generation) | VSAM REPRO from test data |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | Transaction ID generation is not atomic — concurrent bill payments could generate duplicate IDs | DUPREC error under concurrent usage | Single-user operation or implement a sequence counter |
| KI-002 | READ UPDATE on ACCTDAT is issued even when CONF-PAY-YES is not set (i.e., on first display of balance) — the record lock is held but may not be released if the user does not confirm | Potential lock contention on ACCTDAT if user abandons the screen without PF3 | CICS will release the lock when the task ends (RETURN TRANSID), but the lock persists for the remainder of the pseudo-conversational iteration |
| KI-003 | No explicit HANDLE ABEND — unexpected abends use default CICS handling | User sees CICS ABEND screen rather than a friendly error | Add HANDLE ABEND with a user-friendly error screen |
| KI-004 | The payment always pays the full balance — no partial payment option | Users cannot make partial payments | Enhance the screen to accept a payment amount and validate it against the balance |
| KI-005 | TRAN-ORIG-TS and TRAN-PROC-TS both receive the same timestamp (current time) | No distinction between origination and processing timestamps | Populate separately if business rules differ |
| KI-006 | Hard-coded merchant metadata (ID=999999999, Name='BILL PAYMENT', City/Zip='N/A') | All bill payments look identical in transaction reports | Consider parameterizing or using a dedicated bill payment type |
| KI-007 | If WRITE succeeds but REWRITE fails, the transaction record exists but the account balance is not updated — no rollback mechanism | Data inconsistency between TRANSACT and ACCTDAT | Implement two-phase commit or compensating transaction logic |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Screen I/O | BMS Map COBIL0A / 3270 terminal | Web form / REST API POST endpoint | Low — only 3 user fields (Account ID, Balance display, Confirm) |
| Data Access | VSAM KSDS direct (ACCTDAT, CXACAIX, TRANSACT) | Service layer with RDBMS (e.g., RDS PostgreSQL) | Medium — READ UPDATE + REWRITE pattern maps to SQL UPDATE within a DB transaction |
| ID Generation | STARTBR/READPREV + ADD 1 pattern | Database sequence or UUID | Low — replace with `NEXTVAL` or UUID generator |
| Balance Update | COMPUTE + REWRITE pattern | SQL `UPDATE accounts SET balance = balance - :amount WHERE acct_id = :id` | Low |
| Partial Payments | Not supported (full balance only) | Accept user-specified amount with validation | Low — add amount field and validation |
| Transaction Atomicity | No rollback between WRITE and REWRITE | Database transaction with COMMIT/ROLLBACK | Medium — critical for data integrity |
| State Management | COMMAREA pseudo-conversational | HTTP session or stateless REST with tokens | Medium |
| Confirmation | Two-step ENTER→Confirm(Y)→ENTER | Single-submit with client-side confirmation dialog | Low |
| Cross-Reference Lookup | VSAM alternate index (CXACAIX) | SQL JOIN or foreign key lookup | Low |
| Concurrency | No locking on ID generation — DUPREC risk; READ UPDATE lock on ACCTDAT | Database sequence + row-level locking via SQL | Low |
| Timestamp | CICS ASKTIME/FORMATTIME | Language-native timestamp (`Instant.now()`, `datetime.utcnow()`) | Low |

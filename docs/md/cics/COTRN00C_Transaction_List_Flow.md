# Transaction List (CT00) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CT00 |
| Entry Program | COTRN00C |
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

- **What**: Displays a paginated list of credit card transactions from the TRANSACT VSAM file. Each page shows up to 10 transaction summaries (Transaction ID, Date, Description, Amount). The user can browse forward/backward, search by Transaction ID, and select a transaction to view its details.
- **Who**: Any authenticated CardDemo user (regular users and admins) — menu option 6 ("Transaction List").
- **When**: Real-time, on-demand during business hours when a user needs to review or locate specific transactions.
- **Why**: Provides the primary inquiry entry point for browsing the transaction file. Users can locate a specific transaction to drill down for viewing (via COTRN01C).
- **What** is the business outcome: The user can browse through all transactions in the TRANSACT file with forward/backward pagination. Selecting a transaction with 'S' transfers control to the Transaction View screen (COTRN01C) for detailed inspection.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  User selects Option 6 ("Transaction List") from Main Menu
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ System Response          │  Display first page of transactions (up to 10 rows)
  └────────────┬─────────────┘  with Transaction ID, Date, Description, Amount
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 1 (optional) │  Enter a Transaction ID in the "Search Tran ID" field
  └────────────┬─────────────┘  and press ENTER to jump to that position
               │
               ▼
  ┌──────────────────────────┐
  │ System Response          │  Display page of transactions starting at or after
  └────────────┬─────────────┘  the entered Transaction ID
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 2 (browse)   │  Press PF7 (backward) or PF8 (forward) to page
  └────────────┬─────────────┘  through the transaction list
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 3 (select)   │  Type 'S' next to a transaction row and press ENTER
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ Business Outcome         │  XCTL to COTRN01C (Transaction View) with selected
  └──────────────────────────┘  transaction ID passed via COMMAREA
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | Page size is 10 transactions | Each page displays up to 10 transaction rows | READNEXT/READPREV loop limited to 10 iterations |
| BR-002 | Search by Transaction ID | User enters a numeric value in "Search Tran ID" field | STARTBR positions browse at or after the entered ID |
| BR-003 | Transaction ID must be numeric | Non-numeric value entered in Search Tran ID | Error: "Tran ID must be Numeric ..." |
| BR-004 | Selection flag validation | User enters a value other than 'S' or 's' next to a row | Error: "Invalid selection. Valid value is S" |
| BR-005 | Forward paging | PF8 pressed when more records exist | Browse forward and display next 10 transactions |
| BR-006 | Backward paging | PF7 pressed when on page > 1 | Browse backward and display previous 10 transactions |
| BR-007 | Top of file boundary | PF7 pressed when already on page 1 | Message: "You are already at the top of the page..." |
| BR-008 | Bottom of file boundary | PF8 pressed when no more records | Message: "You are already at the bottom of the page..." |
| BR-009 | Empty search defaults to beginning | Search Tran ID is blank, press ENTER | STARTBR from LOW-VALUES (beginning of file) |
| BR-010 | Drill-down to Transaction View | 'S' typed next to a row, ENTER pressed | XCTL to COTRN01C with selected Transaction ID in COMMAREA |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Select Option 6 from Main Menu | XCTL to COTRN00C; display first page of transactions (COTRN0A) | Navigate to transaction list |
| 2a | Press ENTER (no search ID) | Display first page of transactions from beginning of file | Browse from start |
| 2b | Enter Transaction ID, press ENTER | Display page starting at or after the specified Transaction ID | Jump to specific position in file |
| 3 | Press PF8 (Forward) | Display next 10 transactions | Page forward |
| 4 | Press PF7 (Backward) | Display previous 10 transactions | Page backward |
| 5 | Type 'S' next to a row, press ENTER | XCTL to COTRN01C (Transaction View) | Drill down to view details |
| Alt-A | Press PF3 | XCTL back to Main Menu (COMEN01C) | Exit transaction list |
| Alt-B | Press any other key | Display "Invalid key pressed. Please see below..." | Invalid key rejection |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CT00
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COTRN00C     │ (Entry & only online program)      │
  │  │ (Tran List)  │                                    │
  │  └──┬───────┬───┘                                    │
  │     │       │                                         │
  │     ▼       ▼                                         │
  │  ┌──────┐  ┌────────────────────────────────────┐    │
  │  │BMS   │  │ VSAM Files                         │    │
  │  │MAP:  │  │  TRANSACT (Transaction master)     │    │
  │  │COTRN │  │                                    │    │
  │  │0A    │  │                                    │    │
  │  └──────┘  └────────────────────────────────────┘    │
  │                                                      │
  │          ┌─────────────┐                              │
  │          │ COTRN01C    │ (Transaction View — via XCTL)│
  │          └─────────────┘                              │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1 (First entry — from Main Menu via XCTL):
  1. COMEN01C XCTLs to COTRN00C with CARDDEMO-COMMAREA
  2. EIBCALEN > 0; CDEMO-PGM-REENTER = 0 (first entry)
  3. SET CDEMO-PGM-REENTER TO TRUE
  4. MOVE LOW-VALUES TO COTRN0AO (clear output map)
  5. PERFORM PROCESS-ENTER-KEY
     ├── No selection flag set (first entry) → skip selection logic
     ├── TRNIDINI = SPACES → MOVE LOW-VALUES TO TRAN-ID
     ├── MOVE 0 TO CDEMO-CT00-PAGE-NUM
     └── PERFORM PROCESS-PAGE-FORWARD
         ├── STARTBR TRANSACT RIDFLD(TRAN-ID) [from beginning]
         ├── Initialize 10 row slots to SPACES
         ├── READNEXT loop (up to 10 records)
         │   └── POPULATE-TRAN-DATA for each record (rows 1–10)
         ├── Check for 11th record → set NEXT-PAGE-YES/NO
         ├── Increment page number
         ├── ENDBR TRANSACT
         └── SEND MAP('COTRN0A') ERASE CURSOR
  6. RETURN TRANSID('CT00') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 2+ (User presses ENTER with or without search ID):
  7.  CICS dispatches → COTRN00C (COMMAREA restored)
  8.  EIBCALEN > 0; CDEMO-PGM-REENTER = 1
  9.  RECEIVE MAP('COTRN0A') INTO(COTRN0AI)
  10. EIBAID = DFHENTER → PERFORM PROCESS-ENTER-KEY
      ├── Check SEL0001I..SEL0010I for selection flag
      │   ├── If 'S'/'s' found → set CDEMO-CT00-TRN-SELECTED,
      │   │   XCTL to COTRN01C (Transaction View)
      │   └── If other value → "Invalid selection. Valid value is S"
      ├── If TRNIDINI is blank → MOVE LOW-VALUES TO TRAN-ID
      ├── If TRNIDINI is numeric → MOVE to TRAN-ID
      ├── If TRNIDINI is not numeric → "Tran ID must be Numeric ..."
      ├── MOVE 0 TO CDEMO-CT00-PAGE-NUM
      └── PERFORM PROCESS-PAGE-FORWARD (same as iteration 1 step 5)

  Iteration 2+ (User presses PF7 — Page Backward):
  11. EIBAID = DFHPF7 → PERFORM PROCESS-PF7-KEY
      ├── Use CDEMO-CT00-TRNID-FIRST as starting position
      ├── If page > 1:
      │   └── PERFORM PROCESS-PAGE-BACKWARD
      │       ├── STARTBR TRANSACT at TRNID-FIRST
      │       ├── READPREV to skip current page's first record
      │       ├── Initialize 10 row slots
      │       ├── READPREV loop (10 records, filling rows 10→1)
      │       ├── Adjust page number (decrement)
      │       ├── ENDBR TRANSACT
      │       └── SEND MAP('COTRN0A') CURSOR
      └── If page = 1 → "You are already at the top of the page..."

  Iteration 2+ (User presses PF8 — Page Forward):
  12. EIBAID = DFHPF8 → PERFORM PROCESS-PF8-KEY
      ├── Use CDEMO-CT00-TRNID-LAST as starting position
      ├── If NEXT-PAGE-YES:
      │   └── PERFORM PROCESS-PAGE-FORWARD (same browse logic)
      └── If NEXT-PAGE-NO → "You are already at the bottom of the page..."
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | >0 | XCTL from menu (option 6) | Initial logic (PGM-ENTER) | COTRN0A (first page of transactions) | CT00 |
| 2 | >0 | Press ENTER (optional search ID) | RECEIVE → PROCESS-ENTER-KEY | COTRN0A (page from search position) | CT00 |
| N | >0 | PF8 (page forward) | RECEIVE → PROCESS-PF8-KEY | COTRN0A (next page) | CT00 |
| N | >0 | PF7 (page backward) | RECEIVE → PROCESS-PF7-KEY | COTRN0A (previous page) | CT00 |
| N | >0 | 'S' + ENTER (select) | PROCESS-ENTER-KEY → XCTL COTRN01C | — | — (XCTL) |
| N | >0 | PF3 (Exit) | XCTL to COMEN01C (menu) | — | — (XCTL) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | RECEIVE MAP('COTRN0A') MAPSET('COTRN00') | Capture user input from terminal | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 2 | STARTBR DATASET('TRANSACT') RIDFLD(TRAN-ID) KEYLENGTH(16) | Position browse cursor at or after specified key | NORMAL → continue; NOTFND → EOF, "You are at the top of the page..."; OTHER → error |
| 3 | READNEXT DATASET('TRANSACT') INTO(TRAN-RECORD) | Read next transaction record in forward direction | NORMAL → populate row; ENDFILE → "You have reached the bottom of the page..."; OTHER → error |
| 4 | READPREV DATASET('TRANSACT') INTO(TRAN-RECORD) | Read previous transaction record in backward direction | NORMAL → populate row; ENDFILE → "You have reached the top of the page..."; OTHER → error |
| 5 | ENDBR DATASET('TRANSACT') | End browse session | — |
| 6 | SEND MAP('COTRN0A') MAPSET('COTRN00') FROM(COTRN0AO) ERASE CURSOR | Send screen to terminal (with erase on first send or ENTER/PF3) | — |
| 7 | SEND MAP('COTRN0A') MAPSET('COTRN00') FROM(COTRN0AO) CURSOR | Send screen to terminal (without erase on PF7/PF8 boundary messages) | — |
| 8 | RETURN TRANSID('CT00') COMMAREA(CARDDEMO-COMMAREA) | Pseudo-conversational return | — |
| 9 | XCTL PROGRAM('COTRN01C') COMMAREA(CARDDEMO-COMMAREA) | Transfer to Transaction View on 'S' selection | — |
| 10 | XCTL PROGRAM('COMEN01C') COMMAREA(CARDDEMO-COMMAREA) | Transfer to Main Menu on PF3 | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────┐                ┌──────────────────────────────────────────────┐
  │  Main Menu  │   Option 6     │  COTRN0A (Transaction List)                  │
  │  (COMEN01C) │───────────────▶│                                              │
  └─────────────┘                │  Search Tran ID: ________________            │
        ▲                        │  ─────────────────────────────────────────    │
        │ PF3                    │  Sel  Transaction ID    Date      Description │
        │                        │  ---  ----------------  --------  ----------  │
        │                        │   _   0000000000000001  01/15/26  Purchase... │
        │                        │   _   0000000000000002  01/16/26  Payment...  │
        │                        │   _   ... (up to 10 rows) ...                │
        │                        │                                              │
        │                        │  Type 'S' to View Transaction details...     │
        │                        │  [Error/Success message line]                │
        │                        │  ENTER=Continue F3=Back F7=Backward F8=Forwar│
        └────────────────────────┤                                              │
                                 └────────────┬─────────────────────────────────┘
                                              │
                                   ┌──────────┤
                                   │          │
                           'S'+ENTER│    PF7/PF8
                                   │    (pages through
                                   │     same screen)
                                   ▼
                          ┌─────────────────┐
                          │  COTRN01C       │
                          │  (Transaction   │
                          │   View)         │
                          └─────────────────┘
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| COTRN0A | COTRN00 | Transaction List — paginated display of transaction summaries | Search Tran ID, 10× (Sel, Tran ID, Date, Description, Amount) | ENTER=Search/Select, PF3=Back, PF7=Backward, PF8=Forward |

### 3.3 Screen Field Details

#### Map: COTRN0A

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction name (CT00) |
| TITLE01 | ASKIP,FSET,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,FSET,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | ASKIP,FSET,NORM | 8 | Alpha | — | Program name (COTRN00C) |
| TITLE02 | ASKIP,FSET,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | ASKIP,FSET,NORM | 8 | Time | — | Current time hh:mm:ss |
| PAGENUM | ASKIP,FSET,NORM | 8 | Numeric | — | Current page number |
| TRNIDIN | FSET,NORM,UNPROT | 16 | AlphaNum | Must be numeric if provided | Search Transaction ID (starting position) |
| SEL0001 | FSET,NORM,UNPROT | 1 | Alpha | 'S'/'s' only | Selection flag for row 1 |
| TRNID01 | ASKIP,FSET,NORM | 16 | Alpha | — | Transaction ID row 1 |
| TDATE01 | ASKIP,FSET,NORM | 8 | Date | — | Transaction Date row 1 (mm/dd/yy) |
| TDESC01 | ASKIP,FSET,NORM | 26 | Alpha | — | Transaction Description row 1 |
| TAMT001 | ASKIP,FSET,NORM | 12 | Decimal | — | Transaction Amount row 1 |
| SEL0002 | FSET,NORM,UNPROT | 1 | Alpha | 'S'/'s' only | Selection flag for row 2 |
| TRNID02 | ASKIP,FSET,NORM | 16 | Alpha | — | Transaction ID row 2 |
| TDATE02 | ASKIP,FSET,NORM | 8 | Date | — | Transaction Date row 2 (mm/dd/yy) |
| TDESC02 | ASKIP,FSET,NORM | 26 | Alpha | — | Transaction Description row 2 |
| TAMT002 | ASKIP,FSET,NORM | 12 | Decimal | — | Transaction Amount row 2 |
| SEL0003 | FSET,NORM,UNPROT | 1 | Alpha | 'S'/'s' only | Selection flag for row 3 |
| TRNID03 | ASKIP,FSET,NORM | 16 | Alpha | — | Transaction ID row 3 |
| TDATE03 | ASKIP,FSET,NORM | 8 | Date | — | Transaction Date row 3 (mm/dd/yy) |
| TDESC03 | ASKIP,FSET,NORM | 26 | Alpha | — | Transaction Description row 3 |
| TAMT003 | ASKIP,FSET,NORM | 12 | Decimal | — | Transaction Amount row 3 |
| SEL0004 | FSET,NORM,UNPROT | 1 | Alpha | 'S'/'s' only | Selection flag for row 4 |
| TRNID04 | ASKIP,FSET,NORM | 16 | Alpha | — | Transaction ID row 4 |
| TDATE04 | ASKIP,FSET,NORM | 8 | Date | — | Transaction Date row 4 (mm/dd/yy) |
| TDESC04 | ASKIP,FSET,NORM | 26 | Alpha | — | Transaction Description row 4 |
| TAMT004 | ASKIP,FSET,NORM | 12 | Decimal | — | Transaction Amount row 4 |
| SEL0005 | FSET,NORM,UNPROT | 1 | Alpha | 'S'/'s' only | Selection flag for row 5 |
| TRNID05 | ASKIP,FSET,NORM | 16 | Alpha | — | Transaction ID row 5 |
| TDATE05 | ASKIP,FSET,NORM | 8 | Date | — | Transaction Date row 5 (mm/dd/yy) |
| TDESC05 | ASKIP,FSET,NORM | 26 | Alpha | — | Transaction Description row 5 |
| TAMT005 | ASKIP,FSET,NORM | 12 | Decimal | — | Transaction Amount row 5 |
| SEL0006 | FSET,NORM,UNPROT | 1 | Alpha | 'S'/'s' only | Selection flag for row 6 |
| TRNID06 | ASKIP,FSET,NORM | 16 | Alpha | — | Transaction ID row 6 |
| TDATE06 | ASKIP,FSET,NORM | 8 | Date | — | Transaction Date row 6 (mm/dd/yy) |
| TDESC06 | ASKIP,FSET,NORM | 26 | Alpha | — | Transaction Description row 6 |
| TAMT006 | ASKIP,FSET,NORM | 12 | Decimal | — | Transaction Amount row 6 |
| SEL0007 | FSET,NORM,UNPROT | 1 | Alpha | 'S'/'s' only | Selection flag for row 7 |
| TRNID07 | ASKIP,FSET,NORM | 16 | Alpha | — | Transaction ID row 7 |
| TDATE07 | ASKIP,FSET,NORM | 8 | Date | — | Transaction Date row 7 (mm/dd/yy) |
| TDESC07 | ASKIP,FSET,NORM | 26 | Alpha | — | Transaction Description row 7 |
| TAMT007 | ASKIP,FSET,NORM | 12 | Decimal | — | Transaction Amount row 7 |
| SEL0008 | FSET,NORM,UNPROT | 1 | Alpha | 'S'/'s' only | Selection flag for row 8 |
| TRNID08 | ASKIP,FSET,NORM | 16 | Alpha | — | Transaction ID row 8 |
| TDATE08 | ASKIP,FSET,NORM | 8 | Date | — | Transaction Date row 8 (mm/dd/yy) |
| TDESC08 | ASKIP,FSET,NORM | 26 | Alpha | — | Transaction Description row 8 |
| TAMT008 | ASKIP,FSET,NORM | 12 | Decimal | — | Transaction Amount row 8 |
| SEL0009 | FSET,NORM,UNPROT | 1 | Alpha | 'S'/'s' only | Selection flag for row 9 |
| TRNID09 | ASKIP,FSET,NORM | 16 | Alpha | — | Transaction ID row 9 |
| TDATE09 | ASKIP,FSET,NORM | 8 | Date | — | Transaction Date row 9 (mm/dd/yy) |
| TDESC09 | ASKIP,FSET,NORM | 26 | Alpha | — | Transaction Description row 9 |
| TAMT009 | ASKIP,FSET,NORM | 12 | Decimal | — | Transaction Amount row 9 |
| SEL0010 | FSET,NORM,UNPROT | 1 | Alpha | 'S'/'s' only | Selection flag for row 10 |
| TRNID10 | ASKIP,FSET,NORM | 16 | Alpha | — | Transaction ID row 10 |
| TDATE10 | ASKIP,FSET,NORM | 8 | Date | — | Transaction Date row 10 (mm/dd/yy) |
| TDESC10 | ASKIP,FSET,NORM | 26 | Alpha | — | Transaction Description row 10 |
| TAMT010 | ASKIP,FSET,NORM | 12 | Decimal | — | Transaction Amount row 10 |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/informational message display |

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | COTRN0A | Check for row selection ('S' → XCTL to COTRN01C); validate Search Tran ID; browse forward from specified position |
| PF3 | COTRN0A | Return to Main Menu (COMEN01C) via XCTL |
| PF7 | COTRN0A | Page backward — display previous 10 transactions; if at page 1, display "You are already at the top of the page..." |
| PF8 | COTRN0A | Page forward — display next 10 transactions; if at end, display "You are already at the bottom of the page..." |
| CLEAR | COTRN0A | Not explicitly handled — falls to "Invalid key pressed" |
| OTHER | COTRN0A | Display "Invalid key pressed. Please see below..." |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COTRN00C | Transaction List — browse TRANSACT file, paginated display, selection for drill-down | TRANSID CT00 / XCTL from COMEN01C | 699 | `app/cbl/COTRN00C.cbl` |
| COMEN01C | Main Menu — entry point for option 6 | XCTL (caller) | — | `app/cbl/COMEN01C.cbl` |
| COTRN01C | Transaction View — displays selected transaction detail | XCTL (callee on 'S' selection) | — | `app/cbl/COTRN01C.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CT00
└── COTRN00C (Entry — Screen Handler, Browse, Pagination)
    ├── STARTBR FILE('TRANSACT') — Position browse cursor
    ├── READNEXT FILE('TRANSACT') — Read records forward (up to 10 per page)
    ├── READPREV FILE('TRANSACT') — Read records backward (up to 10 per page)
    ├── ENDBR FILE('TRANSACT') — End browse session
    ├── SEND MAP('COTRN0A') — Display transaction list screen
    ├── RECEIVE MAP('COTRN0A') — Capture user input
    ├── RETURN TRANSID('CT00') — Pseudo-conversational return
    ├── XCTL → COTRN01C (on 'S' selection — Transaction View)
    └── XCTL → COMEN01C (on PF3 exit — Main Menu)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COMEN01C | COTRN00C | XCTL | CARDDEMO-COMMAREA | — | User selects menu option 6 |
| COTRN00C | COTRN01C | XCTL | CARDDEMO-COMMAREA (with CDEMO-CT00-TRN-SELECTED) | — | User types 'S' next to a transaction row |
| COTRN00C | COMEN01C | XCTL | CARDDEMO-COMMAREA | — | PF3 exit back to Main Menu |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| Transaction Master | TRANSACT | VSAM KSDS | BROWSE (STARTBR/READNEXT/READPREV/ENDBR) | COTRN00C | Browse and paginate through transaction records |

### 5.2 File Access Details

#### File: TRANSACT (Transaction Master)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CVTRA05Y (TRAN-RECORD, 350 bytes) |
| Key | TRAN-ID (PIC X(16)) |
| Access Mode | BROWSE (STARTBR, READNEXT, READPREV, ENDBR) — read-only |
| Locking | No update, read-only browse |
| Shared With | COTRN01C (View Transaction), COTRN02C (Add Transaction), Batch Post-Tran |

**Access Pattern — Forward Paging (PROCESS-PAGE-FORWARD)**:

| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COTRN00C | STARTBR DATASET('TRANSACT') RIDFLD(TRAN-ID) KEYLENGTH(16) | TRAN-ID from search field or CDEMO-CT00-TRNID-LAST | Position browse cursor |
| 2 | COTRN00C | READNEXT INTO(TRAN-RECORD) | Sequential from cursor position | Skip past current position (if not first page) |
| 3 | COTRN00C | READNEXT INTO(TRAN-RECORD) (×10 loop) | Sequential | TRAN-ID, TRAN-AMT, TRAN-ORIG-TS, TRAN-DESC for display |
| 4 | COTRN00C | READNEXT INTO(TRAN-RECORD) | 11th read to check for more pages | Sets NEXT-PAGE-YES/NO |
| 5 | COTRN00C | ENDBR DATASET('TRANSACT') | — | Close browse |

**Access Pattern — Backward Paging (PROCESS-PAGE-BACKWARD)**:

| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COTRN00C | STARTBR DATASET('TRANSACT') RIDFLD(TRAN-ID) KEYLENGTH(16) | CDEMO-CT00-TRNID-FIRST | Position at first record of current page |
| 2 | COTRN00C | READPREV INTO(TRAN-RECORD) | Skip current page boundary | — |
| 3 | COTRN00C | READPREV INTO(TRAN-RECORD) (×10 loop, filling rows 10→1) | Sequential backward | TRAN-ID, TRAN-AMT, TRAN-ORIG-TS, TRAN-DESC for display |
| 4 | COTRN00C | READPREV INTO(TRAN-RECORD) | Check if more pages backward | Adjusts page number |
| 5 | COTRN00C | ENDBR DATASET('TRANSACT') | — | Close browse |

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
  Terminal Input (COTRN0A)
       │
       ├── TRNIDINI (Search Transaction ID)
       │       │
       │       ├── If SPACES → MOVE LOW-VALUES TO TRAN-ID (browse from beginning)
       │       │
       │       ├── If NUMERIC → MOVE to TRAN-ID (browse from specified ID)
       │       │
       │       └── If NOT NUMERIC → Error: "Tran ID must be Numeric ..."
       │
       ├── SEL0001I..SEL0010I (Selection flags)
       │       │
       │       ├── If 'S'/'s' → MOVE corresponding TRNIDnnI to CDEMO-CT00-TRN-SELECTED
       │       │                 XCTL to COTRN01C
       │       │
       │       └── If other value → Error: "Invalid selection. Valid value is S"
       │
       └── (PF7/PF8 — no input data, uses COMMAREA state)
               │
               └── CDEMO-CT00-TRNID-FIRST / CDEMO-CT00-TRNID-LAST
                         │
                         └──▶ TRAN-ID (STARTBR key)

  TRANSACT VSAM File (via READNEXT/READPREV)
       │
       └──▶ TRAN-RECORD
               │
               ├── TRAN-ID        → TRNIDnnI OF COTRN0AI (row n)
               │                     CDEMO-CT00-TRNID-FIRST (row 1)
               │                     CDEMO-CT00-TRNID-LAST (row 10)
               │
               ├── TRAN-AMT       → WS-TRAN-AMT (formatted +99999999.99)
               │                     → TAMTnnnI OF COTRN0AI (row n)
               │
               ├── TRAN-ORIG-TS   → WS-TIMESTAMP
               │                     → WS-CURDATE-MM-DD-YY (mm/dd/yy format)
               │                     → TDATEnnI OF COTRN0AI (row n)
               │
               └── TRAN-DESC      → TDESCnnI OF COTRN0AI (row n, 26 chars)
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| TRNIDINI | Terminal (COTRN0AI) | Direct move (if numeric) | TRAN-ID | STARTBR key for TRANSACT |
| TRAN-ID | TRAN-RECORD (READNEXT/READPREV) | Direct move PIC X(16) | TRNIDnnI | Screen row n (COTRN0AI) |
| TRAN-ID (row 1) | TRAN-RECORD | Direct move | CDEMO-CT00-TRNID-FIRST | COMMAREA (pagination state) |
| TRAN-ID (row 10) | TRAN-RECORD | Direct move | CDEMO-CT00-TRNID-LAST | COMMAREA (pagination state) |
| TRAN-AMT | TRAN-RECORD | MOVE to WS-TRAN-AMT PIC +99999999.99 | TAMTnnnI | Screen row n (COTRN0AI) |
| TRAN-ORIG-TS | TRAN-RECORD | Extract YYYY→YY, MM, DD → WS-CURDATE-MM-DD-YY (mm/dd/yy) | TDATEnnI | Screen row n (COTRN0AI) |
| TRAN-DESC | TRAN-RECORD | Direct move PIC X(100) → PIC X(26) (truncated) | TDESCnnI | Screen row n (COTRN0AI) |
| SEL000nI | Terminal (COTRN0AI) | Direct move | CDEMO-CT00-TRN-SEL-FLG | COMMAREA (selection state) |
| TRNIDnnI | Screen row n | Direct move (corresponding to selected row) | CDEMO-CT00-TRN-SELECTED | COMMAREA (passed to COTRN01C) |
| CDEMO-CT00-PAGE-NUM | COMMAREA | Increment/decrement per page | PAGENUMI | Screen page number display |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`) plus a program-specific extension `CDEMO-CT00-INFO`:

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

**CDEMO-CT00-INFO (program-specific extension, defined inline in COTRN00C):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-CT00-TRNID-FIRST | X(16) | 16 | IN/OUT | Transaction ID of first row on current page (used for backward paging) |
| CDEMO-CT00-TRNID-LAST | X(16) | 16 | IN/OUT | Transaction ID of last row on current page (used for forward paging) |
| CDEMO-CT00-PAGE-NUM | 9(08) | 8 | IN/OUT | Current page number |
| CDEMO-CT00-NEXT-PAGE-FLG | X(01) | 1 | IN/OUT | 'Y' = more pages forward, 'N' = at end |
| CDEMO-CT00-TRN-SEL-FLG | X(01) | 1 | OUT | Selection flag ('S'/'s') from user input |
| CDEMO-CT00-TRN-SELECTED | X(16) | 16 | OUT | Transaction ID selected for drill-down to COTRN01C |

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. Errors are handled inline via screen messages (WS-MESSAGE → ERRMSGO). The drill-down to COTRN01C passes the selected transaction ID via CDEMO-CT00-TRN-SELECTED.

### 7.3 Conversation State Management

- **COMMAREA preservation**: The entire CARDDEMO-COMMAREA (including CT00-specific fields) is passed on every RETURN TRANSID('CT00'). CICS restores it automatically on the next dispatch.
- **Pagination state**: CDEMO-CT00-TRNID-FIRST and CDEMO-CT00-TRNID-LAST track the key range of the currently displayed page. CDEMO-CT00-PAGE-NUM tracks the page number. CDEMO-CT00-NEXT-PAGE-FLG indicates whether more records exist beyond the current page.
- **Screen field state**: BMS FSET attribute ensures all fields are returned on every RECEIVE, preserving user-entered data across iterations.
- **No TSQ overflow**: All state fits within the COMMAREA; no temporary storage queues are used.
- **PGM-CONTEXT flag**: Distinguishes first entry (0 — display first page with erase) from subsequent interactions (1 — process input).
- **SEND-ERASE-FLG**: Controls whether SEND MAP uses ERASE. Set to 'Y' on first entry and ENTER; set to 'N' for boundary messages on PF7/PF8 to preserve existing screen content.

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path:
    ENTER → Check Selection → Check Search ID → STARTBR → READNEXT ×10 → Display Page

  Selection Error:
    ENTER → SEL field value not 'S' ──▶ "Invalid selection. Valid value is S"
                                        → Cursor to TRNIDIN, RETURN TRANSID

  Search ID Error:
    ENTER → TRNIDINI not numeric ──▶ "Tran ID must be Numeric ..."
                                     → Cursor to TRNIDIN, RETURN TRANSID

  STARTBR Error:
    STARTBR ──NOTFND──▶ "You are at the top of the page..."
                         → EOF set, SEND screen, RETURN TRANSID
    STARTBR ──OTHER──▶  "Unable to lookup transaction..."
                         → ERR-FLG set, SEND screen, RETURN TRANSID

  READNEXT Error:
    READNEXT ──ENDFILE──▶ "You have reached the bottom of the page..."
                           → EOF set, SEND screen, RETURN TRANSID
    READNEXT ──OTHER──▶   "Unable to lookup transaction..."
                           → ERR-FLG set, SEND screen, RETURN TRANSID

  READPREV Error:
    READPREV ──ENDFILE──▶ "You have reached the top of the page..."
                           → EOF set, SEND screen, RETURN TRANSID
    READPREV ──OTHER──▶   "Unable to lookup transaction..."
                           → ERR-FLG set, SEND screen, RETURN TRANSID

  Paging Boundary:
    PF7 at page 1 ──▶ "You are already at the top of the page..."
    PF8 at last page ──▶ "You are already at the bottom of the page..."

  Invalid Key:
    Any unrecognized AID key ──▶ "Invalid key pressed. Please see below..."
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| STARTBR record not found | NOTFND (13) | Set TRANSACT-EOF, send screen | "You are at the top of the page..." | — |
| STARTBR other error | OTHER | Set ERR-FLG, send screen | "Unable to lookup transaction..." | DISPLAY RESP/REAS to SYSOUT |
| READNEXT end of file | ENDFILE (20) | Set TRANSACT-EOF, send screen | "You have reached the bottom of the page..." | — |
| READNEXT other error | OTHER | Set ERR-FLG, send screen | "Unable to lookup transaction..." | DISPLAY RESP/REAS to SYSOUT |
| READPREV end of file | ENDFILE (20) | Set TRANSACT-EOF, send screen | "You have reached the top of the page..." | — |
| READPREV other error | OTHER | Set ERR-FLG, send screen | "Unable to lookup transaction..." | DISPLAY RESP/REAS to SYSOUT |
| Non-numeric search ID | — (app validation) | Set ERR-FLG, send screen | "Tran ID must be Numeric ..." | — |
| Invalid selection flag | — (app validation) | Set message, send screen | "Invalid selection. Valid value is S" | — |
| Invalid AID key | — (not ENTER/PF3/PF7/PF8) | Set ERR-FLG, send screen | "Invalid key pressed. Please see below..." | — |
| PF7 at page 1 | — (boundary check) | Send screen (no erase) | "You are already at the top of the page..." | — |
| PF8 at last page | — (boundary check) | Send screen (no erase) | "You are already at the bottom of the page..." | — |
| No COMMAREA (EIBCALEN = 0) | — | XCTL to COSGN00C (sign-on) | — | — |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| — | No explicit HANDLE ABEND | Default CICS abend handling applies |

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "Tran ID must be Numeric ..." | TRNIDINI is not numeric | ERRMSG (line 23, col 1) |
| — | "Invalid selection. Valid value is S" | SEL field contains value other than 'S'/'s' | ERRMSG (line 23, col 1) |
| — | "You are at the top of the page..." | STARTBR returns NOTFND | ERRMSG (line 23, col 1) |
| — | "You are already at the top of the page..." | PF7 pressed when page number = 1 | ERRMSG (line 23, col 1) |
| — | "You have reached the bottom of the page..." | READNEXT returns ENDFILE | ERRMSG (line 23, col 1) |
| — | "You are already at the bottom of the page..." | PF8 pressed when NEXT-PAGE-FLG = 'N' | ERRMSG (line 23, col 1) |
| — | "You have reached the top of the page..." | READPREV returns ENDFILE | ERRMSG (line 23, col 1) |
| — | "Unable to lookup transaction..." | STARTBR/READNEXT/READPREV returns unexpected RESP code | ERRMSG (line 23, col 1) |
| — | "Invalid key pressed. Please see below..." | Any AID key other than ENTER, PF3, PF7, PF8 | ERRMSG (line 23, col 1) |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CT00 transaction must be authorized for user |
| Resource-level security | RACF FACILITY / File profiles for TRANSACT |
| Application-level check | If EIBCALEN = 0 (no COMMAREA — direct invocation without login), XCTL to sign-on screen COSGN00C |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| TRANSACT file | READ (browse only) | CICS File Control |

### 9.3 Audit Trail

| Event | Trigger | Data Logged | Destination |
|-------|---------|-------------|-------------|
| Unexpected RESP codes | File I/O error (non-NORMAL, non-NOTFND, non-ENDFILE) | RESP code, REAS code | DISPLAY → SYSOUT (CICS system log) |
| No explicit audit logging | — | — | Transaction may be audited via CICS journal/SMF records at the system level |

---

## 10. Performance Characteristics

### 10.1 Performance Profile

| Metric | Baseline | Peak | SLA |
|--------|----------|------|-----|
| Response time | < 1 second | < 2 seconds | < 2 seconds |
| CPU per transaction | Low (browse + format 10 records) | — | — |
| File I/O count | 12–13 per page (1 STARTBR + 10–11 READNEXT/READPREV + 1 ENDBR) | — | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal | — | — |

### 10.2 Performance Considerations

- **STARTBR/READNEXT pattern**: Sequential browse through VSAM KSDS is efficient for forward pagination. CICS optimizes look-ahead for READNEXT operations.
- **READPREV for backward paging**: Reverse browse is slightly less efficient than forward browse in VSAM but acceptable for 10-record page sizes.
- **Page size of 10**: Fixed at compile time. Larger page sizes would increase I/O but reduce user interactions.
- **One extra READNEXT per page**: After reading 10 records, an 11th READNEXT is issued to determine if more records exist (NEXT-PAGE-FLG). This is a minimal overhead for next-page detection.
- **No data modification**: Read-only browse — no locking overhead or contention with writers.
- **COMMAREA size**: Relatively small; no TSQ overflow needed.
- **BMS SEND/RECEIVE**: Standard map I/O with ERASE (or without for boundary messages) — one pair per pseudo-conversational iteration.
- **Date formatting**: Simple MOVE operations to convert YYYY-MM-DD timestamp to mm/dd/yy — negligible overhead.

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Initial page display | Select Option 6 from Main Menu | First 10 transactions displayed, page number = 1 | High |
| TC-002 | Page forward | Press PF8 on first page | Next 10 transactions displayed, page number = 2 | High |
| TC-003 | Page backward | Press PF7 on page 2 | First 10 transactions displayed, page number = 1 | High |
| TC-004 | Search by Transaction ID | Enter valid numeric Tran ID, press ENTER | Page starting at or after specified Tran ID | High |
| TC-005 | Select transaction for view | Type 'S' next to row 3, press ENTER | XCTL to COTRN01C with selected Tran ID | High |
| TC-006 | Select with lowercase 's' | Type 's' next to a row, press ENTER | XCTL to COTRN01C (same as 'S') | Medium |
| TC-007 | Non-numeric search ID | Enter "ABCDEF" in Search Tran ID, press ENTER | "Tran ID must be Numeric ..." | High |
| TC-008 | Invalid selection value | Type 'X' next to a row, press ENTER | "Invalid selection. Valid value is S" | Medium |
| TC-009 | Page backward at page 1 | Press PF7 on page 1 | "You are already at the top of the page..." | Medium |
| TC-010 | Page forward at end of file | Press PF8 on last page | "You are already at the bottom of the page..." | Medium |
| TC-011 | Empty search (browse from start) | Leave Search Tran ID blank, press ENTER | First page of transactions from beginning | Medium |
| TC-012 | PF3 exit | Press PF3 at any point | Return to Main Menu (COMEN01C) | Medium |
| TC-013 | Invalid PF key | Press PF2, PF5, PF6, etc. | "Invalid key pressed. Please see below..." | Low |
| TC-014 | No COMMAREA (direct invocation) | Invoke CT00 without login | XCTL to sign-on screen (COSGN00C) | Low |
| TC-015 | Empty TRANSACT file | Browse with no records | "You are at the top of the page..." (STARTBR NOTFND) | Low |
| TC-016 | Partial last page | Fewer than 10 records on final page | Records displayed correctly, no blank-row artifacts | Medium |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| TRANSACT | Pre-loaded with at least 25+ transaction records to test multi-page browsing | VSAM REPRO from test data (`app/data/`) |
| TRANSACT | Records with known Transaction IDs for search testing | VSAM REPRO with specific key values |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | READNEXT ENDFILE handler performs SEND-TRNLST-SCREEN inline during the browse loop, which sends the screen and then continues back to the loop control — the screen is sent but the loop may re-enter for one more iteration | Cosmetic — the "bottom of page" message is displayed correctly but the send is embedded inside the browse logic rather than after it | No functional impact; the TRANSACT-EOF flag prevents further reads |
| KI-002 | Backward paging uses READPREV in a decrementing loop (10→0) but stores records in forward order (row 10 filled first, then row 9, etc.) — if fewer than 10 records exist backward, earlier rows may be blank | Partial page on backward navigation may show blanks in the lower rows | No workaround needed — this is a display artifact for the first page |
| KI-003 | No explicit HANDLE ABEND — unexpected abends use default CICS handling | User sees CICS ABEND screen rather than a friendly error | Add HANDLE ABEND with a user-friendly error screen |
| KI-004 | Date display format uses only 2-digit year (mm/dd/yy) extracted from timestamp | Potential Y2K-style ambiguity between 20xx and 19xx dates | Expand to 4-digit year format |
| KI-005 | Transaction Description is truncated from PIC X(100) in the record to PIC X(26) on screen | User sees only the first 26 characters of the description | View full description via Transaction View (COTRN01C) |
| KI-006 | The STARTBR does not use GTEQ option (commented out in source) — relies on default CICS GTEQ behavior for STARTBR | No functional impact in current CICS versions (GTEQ is the default for STARTBR) | Uncomment GTEQ for explicitness |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Screen I/O | BMS Map COTRN0A / 3270 terminal with 10-row fixed page | Web-based data grid / REST API GET endpoint with dynamic pagination | Medium — requires state management redesign |
| Data Access | VSAM KSDS browse (STARTBR/READNEXT/READPREV) | Service layer with RDBMS (e.g., RDS PostgreSQL) using SQL LIMIT/OFFSET or keyset pagination | Medium — browse semantics map to SQL cursor/keyset patterns |
| Pagination | COMMAREA-based state (first/last IDs, page number, next-page flag) | Stateless pagination with query parameters or cursor tokens | Low — COMMAREA state maps directly to pagination parameters |
| Selection/Drill-Down | 'S' flag + XCTL to COTRN01C | Hyperlink/click handler + REST GET for transaction detail | Low — 1:1 mapping to navigation |
| Date Formatting | COBOL MOVE with 2-digit year extraction | Language-native date formatting (ISO 8601 or locale-aware) | Low |
| Amount Formatting | MOVE to PIC +99999999.99 | Language-native currency formatting | Low |
| Description Truncation | PIC X(26) screen field truncates PIC X(100) record | Full text display with responsive layout or tooltip | Low |
| State Management | COMMAREA pseudo-conversational | HTTP session or stateless REST with cursor tokens | Medium |
| Error Handling | Inline DISPLAY to SYSOUT for unexpected errors | Structured logging (JSON, centralized log aggregation) | Low |

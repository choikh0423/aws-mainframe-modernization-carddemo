# Admin Menu (CA00) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CA00 |
| Entry Program | COADM01C |
| Business Domain | Administration / Security Management |
| Owning Team | CardDemo Application Team |
| Design Pattern | Pseudo-conversational |
| Criticality | High |
| Response Time SLA | < 1 second |
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

- **What**: Displays the Admin Menu screen, allowing an administrator to select from a list of administrative functions including user security management (list, add, update, delete) and DB2 transaction type maintenance.
- **Who**: Authenticated CardDemo users with Admin user type (`CDEMO-USER-TYPE = 'A'`). Regular users are directed to the regular Main Menu (COMEN01C) instead.
- **When**: Real-time, on-demand after an admin user signs in. The admin menu is the primary hub for all administrative operations.
- **Why**: Provides a centralized navigation point for all administrative functions. Separates administrative capabilities (user management, DB2 maintenance) from regular user operations (account/transaction management).
- **What** is the business outcome: The administrator is transferred (via XCTL) to the selected administrative sub-function program. No data is modified by this menu program itself — it is purely navigational.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  Admin user signs in → COSGN00C detects admin type
  └────────────┬─────────────┘  and XCTLs to COADM01C
               │
               ▼
  ┌──────────────────────────┐
  │ System Response          │  Display Admin Menu screen (COADM1A) with 6 options:
  └────────────┬─────────────┘  1. User List (Security)
               │                2. User Add (Security)
               │                3. User Update (Security)
               │                4. User Delete (Security)
               │                5. Transaction Type List/Update (Db2)
               │                6. Transaction Type Maintenance (Db2)
               ▼
  ┌──────────────────────────┐
  │ User Action              │  Enter an option number (1–6) and press ENTER
  └────────────┬─────────────┘
               │
           ┌───┴───┐
           │Decision│  Valid option?
           └───┬───┘
              ╱ ╲
        Yes ╱     ╲ No
           ▼       ▼
  ┌──────────────┐  ┌─────────────────────────┐
  │ Program      │  │ Error: "Please enter a   │
  │ installed?   │  │ valid option number..."  │
  └──────┬───────┘  └─────────────────────────┘
        ╱ ╲
  Yes ╱     ╲ No (DUMMY or PGMIDERR)
     ▼       ▼
  ┌───────────────────────┐  ┌──────────────────────────────────┐
  │ XCTL to target        │  │ "This option is not              │
  │ program with COMMAREA │  │  installed ..." (green)          │
  └───────────────────────┘  └──────────────────────────────────┘

  Alternative:
  ┌──────────────────────────┐
  │ User presses PF3         │  XCTL to Sign-on screen (COSGN00C)
  └──────────────────────────┘
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | Valid option range | Option must be numeric, 1–6 (CDEMO-ADMIN-OPT-COUNT), non-zero | Error: "Please enter a valid option number..." |
| BR-002 | Program must be installed | Target program name must not start with 'DUMMY' | Info: "This option is not installed ..." (green text) |
| BR-003 | Program must exist in CICS | XCTL to target program must succeed (no PGMIDERR) | Info: "This option is not installed ..." (green text) via PGMIDERR handler |
| BR-004 | Admin authentication required | EIBCALEN must be > 0 (user must arrive via sign-on flow) | XCTL to sign-on screen COSGN00C |
| BR-005 | PF3 exits to sign-on | User presses PF3 | XCTL to COSGN00C (sign-on screen) |
| BR-006 | Menu options are data-driven | Options are defined in COADM02Y copybook, not hard-coded in the program | Menu dynamically built from CARDDEMO-ADMIN-MENU-OPTIONS array |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Admin user signs in | COSGN00C authenticates, detects admin type, XCTLs to COADM01C | Navigate to admin hub |
| 2 | View menu | Display COADM1A screen with 6 numbered admin options | Present available functions |
| 3a | Enter valid option (1–6), press ENTER | XCTL to corresponding program (e.g., COUSR00C for option 1) | Navigate to selected admin function |
| 3b | Enter invalid option, press ENTER | Error: "Please enter a valid option number..." | Reject invalid selection |
| 3c | Select uninstalled option, press ENTER | Info: "This option is not installed ..." (green) | Inform option is unavailable |
| Alt-A | Press PF3 | XCTL to COSGN00C (sign-on screen) | Exit admin menu / log out |
| Alt-B | Press any other key | Error: "Invalid key pressed. Please see below..." | Reject unrecognized key |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CA00
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COADM01C     │ (Entry program — menu display      │
  │  │ (Admin Menu) │  and option routing)                │
  │  └──┬────┬──────┘                                    │
  │     │    │                                            │
  │     ▼    ▼                                            │
  │  ┌──────┐  ┌────────────────────────────────────┐    │
  │  │BMS   │  │ Target Programs (via XCTL)          │    │
  │  │MAP:  │  │  COUSR00C (User List)               │    │
  │  │COADM │  │  COUSR01C (User Add)                │    │
  │  │1A    │  │  COUSR02C (User Update)              │    │
  │  └──────┘  │  COUSR03C (User Delete)              │    │
  │            │  COTRTLIC (Tran Type List - Db2)      │    │
  │            │  COTRTUPC (Tran Type Maint - Db2)     │    │
  │            │  COSGN00C (Sign-on — on PF3/exit)     │    │
  │            └────────────────────────────────────┘    │
  │                                                      │
  │  No file I/O — purely navigational                   │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1 (First entry — from Sign-on via XCTL):
  1. COSGN00C XCTLs to COADM01C with CARDDEMO-COMMAREA
  2. HANDLE CONDITION PGMIDERR(PGMIDERR-ERR-PARA)
  3. SET ERR-FLG-OFF TO TRUE
  4. MOVE SPACES TO WS-MESSAGE and ERRMSGO
  5. EIBCALEN > 0 → MOVE DFHCOMMAREA to CARDDEMO-COMMAREA
  6. CDEMO-PGM-REENTER = 0 (first entry)
  7. SET CDEMO-PGM-REENTER TO TRUE
  8. MOVE LOW-VALUES TO COADM1AO (clear output map)
  9. PERFORM SEND-MENU-SCREEN
     ├── PERFORM POPULATE-HEADER-INFO
     │   ├── FUNCTION CURRENT-DATE → WS-CURDATE-DATA
     │   ├── MOVE CCDA-TITLE01 → TITLE01O ("AWS Mainframe Modernization")
     │   ├── MOVE CCDA-TITLE02 → TITLE02O ("CardDemo")
     │   ├── MOVE WS-TRANID → TRNNAMEO ("CA00")
     │   ├── MOVE WS-PGMNAME → PGMNAMEO ("COADM01C")
     │   └── Format date (mm/dd/yy) and time (hh:mm:ss)
     ├── PERFORM BUILD-MENU-OPTIONS
     │   └── Loop WS-IDX 1 to CDEMO-ADMIN-OPT-COUNT (6)
     │       ├── Build string: "NN. Option Name" → WS-ADMIN-OPT-TXT
     │       └── Move to OPTN00xO based on WS-IDX
     ├── MOVE WS-MESSAGE TO ERRMSGO
     └── SEND MAP('COADM1A') MAPSET('COADM01') FROM(COADM1AO) ERASE
  10. RETURN TRANSID('CA00') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 2+ (User submits option — ENTER pressed):
  11. CICS dispatches → COADM01C (COMMAREA restored)
  12. EIBCALEN > 0; CDEMO-PGM-REENTER = 1
  13. PERFORM RECEIVE-MENU-SCREEN
      └── RECEIVE MAP('COADM1A') INTO(COADM1AI) RESP/RESP2
  14. EVALUATE EIBAID:
      ├── DFHENTER → PERFORM PROCESS-ENTER-KEY (step 15)
      ├── DFHPF3 → MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM
      │             PERFORM RETURN-TO-SIGNON-SCREEN
      │             └── XCTL PROGRAM('COSGN00C')
      └── OTHER → WS-ERR-FLG = 'Y'
                   WS-MESSAGE = CCDA-MSG-INVALID-KEY
                   PERFORM SEND-MENU-SCREEN
  15. PROCESS-ENTER-KEY:
      ├── Trim trailing spaces from OPTIONI
      ├── Replace spaces with '0' in WS-OPTION-X
      ├── MOVE WS-OPTION-X TO WS-OPTION (numeric)
      ├── MOVE WS-OPTION TO OPTIONO (echo back)
      ├── Validate: NOT NUMERIC or > CDEMO-ADMIN-OPT-COUNT or = 0
      │   └── Error: "Please enter a valid option number..."
      └── If valid and no error:
          ├── If CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION)(1:5) NOT = 'DUMMY':
          │   ├── MOVE WS-TRANID TO CDEMO-FROM-TRANID
          │   ├── MOVE WS-PGMNAME TO CDEMO-FROM-PROGRAM
          │   ├── MOVE ZEROS TO CDEMO-PGM-CONTEXT
          │   └── XCTL PROGRAM(CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION))
          │         COMMAREA(CARDDEMO-COMMAREA)
          └── If 'DUMMY' or after XCTL succeeds (fall-through):
              ├── MOVE DFHGREEN TO ERRMSGC
              ├── STRING "This option is not installed ..."
              └── PERFORM SEND-MENU-SCREEN

  PGMIDERR Handler (program not found in CICS):
  16. MOVE DFHGREEN TO ERRMSGC
  17. STRING "This option is not installed ..."
  18. PERFORM SEND-MENU-SCREEN
  19. RETURN TRANSID('CA00') COMMAREA(CARDDEMO-COMMAREA)
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | >0 | XCTL from COSGN00C (sign-on) | Initial logic (PGM-ENTER) | COADM1A (menu with 6 options) | CA00 |
| 2 | >0 | Enter option, press ENTER | RECEIVE → PROCESS-ENTER-KEY | COADM1A (error msg) or XCTL to target | CA00 (if error) |
| N | >0 | PF3 (Exit) | XCTL to COSGN00C (sign-on) | — | — (XCTL) |
| — | 0 | Direct invocation (no COMMAREA) | RETURN-TO-SIGNON-SCREEN | — | — (XCTL to COSGN00C) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | HANDLE CONDITION PGMIDERR(PGMIDERR-ERR-PARA) | Trap program-not-found on XCTL | Routes to PGMIDERR-ERR-PARA paragraph |
| 2 | RECEIVE MAP('COADM1A') MAPSET('COADM01') INTO(COADM1AI) | Capture user input (option number) | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 3 | XCTL PROGRAM(CDEMO-ADMIN-OPT-PGMNAME) COMMAREA(CARDDEMO-COMMAREA) | Transfer to selected admin function | PGMIDERR → handler; normal → no return |
| 4 | SEND MAP('COADM1A') MAPSET('COADM01') FROM(COADM1AO) ERASE | Send admin menu screen to terminal | — |
| 5 | RETURN TRANSID('CA00') COMMAREA(CARDDEMO-COMMAREA) | Pseudo-conversational return | — |
| 6 | XCTL PROGRAM(CDEMO-TO-PROGRAM) | Transfer to sign-on screen on PF3 or EIBCALEN=0 | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────┐                ┌─────────────────────────────────────┐
  │  Sign-on     │   Admin Login  │  COADM1A (Admin Menu)               │
  │  (COSGN00C)  │───────────────▶│                                     │
  └─────────────┘                │  Tran: CA00          Date: mm/dd/yy │
        ▲                        │  Prog: COADM01C      Time: hh:mm:ss │
        │ PF3                    │                                     │
        │                        │           Admin Menu                │
        │                        │                                     │
        │                        │  01. User List (Security)           │
        │                        │  02. User Add (Security)            │
        │                        │  03. User Update (Security)         │
        │                        │  04. User Delete (Security)         │
        │                        │  05. Transaction Type List/Upd (Db2)│
        │                        │  06. Transaction Type Maint (Db2)   │
        │                        │                                     │
        │                        │  Please select an option : __       │
        │                        │                                     │
        │                        │  [Error/Info message line]          │
        │                        │  ENTER=Continue  F3=Exit            │
        └────────────────────────┤                                     │
                                 └──────────┬──────────────────────────┘
                                            │
                          ┌─────────────────┼─────────────────┐
                    Opt 1 │           Opt 2 │           Opt 3 │  ...
                          ▼                 ▼                 ▼
                   ┌───────────┐     ┌───────────┐     ┌───────────┐
                   │ COUSR00C  │     │ COUSR01C  │     │ COUSR02C  │
                   │ User List │     │ User Add  │     │ User Upd  │
                   └───────────┘     └───────────┘     └───────────┘
                    Opt 4 │           Opt 5 │           Opt 6 │
                          ▼                 ▼                 ▼
                   ┌───────────┐     ┌───────────┐     ┌───────────┐
                   │ COUSR03C  │     │ COTRTLIC  │     │ COTRTUPC  │
                   │ User Del  │     │ TranType  │     │ TranType  │
                   └───────────┘     │ List(Db2) │     │ Maint(Db2)│
                                     └───────────┘     └───────────┘
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| COADM1A | COADM01 | Admin Menu — display menu options and capture user selection | OPTION (input), OPTN001–OPTN012 (display), ERRMSG | ENTER=Process option, PF3=Exit to sign-on |

### 3.3 Screen Field Details

#### Map: COADM1A

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction ID ("CA00") |
| TITLE01 | ASKIP,FSET,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,FSET,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | ASKIP,FSET,NORM | 8 | Alpha | — | Program name ("COADM01C") |
| TITLE02 | ASKIP,FSET,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | ASKIP,FSET,NORM | 8 | Time | — | Current time hh:mm:ss |
| (unnamed) | ASKIP,BRT | 10 | Alpha | — | Screen title "Admin Menu" (line 4, col 35) |
| OPTN001 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 1 text ("01. User List (Security)") |
| OPTN002 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 2 text ("02. User Add (Security)") |
| OPTN003 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 3 text ("03. User Update (Security)") |
| OPTN004 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 4 text ("04. User Delete (Security)") |
| OPTN005 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 5 text ("05. Transaction Type List/Update (Db2)") |
| OPTN006 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 6 text ("06. Transaction Type Maintenance (Db2)") |
| OPTN007 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 7 text (unused — blank) |
| OPTN008 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 8 text (unused — blank) |
| OPTN009 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 9 text (unused — blank) |
| OPTN010 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 10 text (unused — blank) |
| OPTN011 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 11 text (unused — blank) |
| OPTN012 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 12 text (unused — blank) |
| (unnamed) | ASKIP,BRT | 25 | Alpha | — | Prompt "Please select an option :" (line 20, col 15) |
| OPTION | FSET,IC,NORM,NUM,UNPROT | 2 | Numeric | Must be numeric, 1–6, non-zero | User-entered menu option number |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/info message display area |
| (unnamed) | ASKIP,NORM | 23 | Alpha | — | Key legend "ENTER=Continue  F3=Exit" (line 24, col 1) |

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | COADM1A | Validate option number; if valid, XCTL to target program |
| PF3 | COADM1A | Set CDEMO-TO-PROGRAM = 'COSGN00C', XCTL to sign-on screen |
| OTHER | COADM1A | Display "Invalid key pressed. Please see below..." (CCDA-MSG-INVALID-KEY) |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COADM01C | Admin Menu — display menu options, validate selection, route to target | TRANSID CA00 / XCTL from COSGN00C | 288 | `app/cbl/COADM01C.cbl` |
| COSGN00C | Sign-on Screen — authenticates users, routes admin to COADM01C | XCTL (caller and PF3 target) | — | `app/cbl/COSGN00C.cbl` |
| COUSR00C | User List (Security) — displays list of security users | XCTL from COADM01C (option 1) | — | `app/cbl/COUSR00C.cbl` |
| COUSR01C | User Add (Security) — adds a new security user | XCTL from COADM01C (option 2) | — | `app/cbl/COUSR01C.cbl` |
| COUSR02C | User Update (Security) — updates an existing security user | XCTL from COADM01C (option 3) | — | `app/cbl/COUSR02C.cbl` |
| COUSR03C | User Delete (Security) — deletes a security user | XCTL from COADM01C (option 4) | — | `app/cbl/COUSR03C.cbl` |
| COTRTLIC | Transaction Type List/Update (Db2) — list/update transaction types | XCTL from COADM01C (option 5) | — | `app/cbl/COTRTLIC.cbl` |
| COTRTUPC | Transaction Type Maintenance (Db2) — maintain transaction types | XCTL from COADM01C (option 6) | — | `app/cbl/COTRTUPC.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CA00
└── COADM01C (Entry — Menu Display and Option Routing)
    ├── SEND MAP('COADM1A') — Display admin menu screen
    ├── RECEIVE MAP('COADM1A') — Capture option selection
    ├── RETURN TRANSID('CA00') — Pseudo-conversational return
    ├── XCTL → COUSR00C (Option 1: User List)
    ├── XCTL → COUSR01C (Option 2: User Add)
    ├── XCTL → COUSR02C (Option 3: User Update)
    ├── XCTL → COUSR03C (Option 4: User Delete)
    ├── XCTL → COTRTLIC (Option 5: Tran Type List/Update - Db2)
    ├── XCTL → COTRTUPC (Option 6: Tran Type Maintenance - Db2)
    └── XCTL → COSGN00C (PF3 exit or EIBCALEN=0)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COSGN00C | COADM01C | XCTL | CARDDEMO-COMMAREA (user ID, type='A') | — | Admin user signs in |
| COADM01C | COUSR00C | XCTL | CARDDEMO-COMMAREA (FROM-TRANID='CA00', FROM-PROGRAM='COADM01C', PGM-CONTEXT=0) | — | User selects option 1 |
| COADM01C | COUSR01C | XCTL | CARDDEMO-COMMAREA | — | User selects option 2 |
| COADM01C | COUSR02C | XCTL | CARDDEMO-COMMAREA | — | User selects option 3 |
| COADM01C | COUSR03C | XCTL | CARDDEMO-COMMAREA | — | User selects option 4 |
| COADM01C | COTRTLIC | XCTL | CARDDEMO-COMMAREA | — | User selects option 5 |
| COADM01C | COTRTUPC | XCTL | CARDDEMO-COMMAREA | — | User selects option 6 |
| COADM01C | COSGN00C | XCTL | CARDDEMO-COMMAREA (no COMMAREA passed on bare XCTL) | — | PF3 exit or EIBCALEN=0 |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| — | — | — | — | — | **No file I/O** — COADM01C is a purely navigational menu program |

This transaction does not perform any VSAM, DB2, or other file operations. All data displayed on the screen (menu options, titles) comes from WORKING-STORAGE copybooks.

### 5.2 File Access Details

Not applicable — no file access is performed by this transaction.

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
  WORKING-STORAGE Copybooks
       │
       ├── COTTL01Y (Screen Titles)
       │       │
       │       ├── CCDA-TITLE01 ("AWS Mainframe Modernization")
       │       │       └──▶ TITLE01O of COADM1AO → Terminal
       │       │
       │       └── CCDA-TITLE02 ("CardDemo")
       │               └──▶ TITLE02O of COADM1AO → Terminal
       │
       ├── COADM02Y (Admin Menu Options)
       │       │
       │       └── CDEMO-ADMIN-OPT (array, 1–6)
       │               ├── CDEMO-ADMIN-OPT-NUM(n)  ──┐
       │               ├── CDEMO-ADMIN-OPT-NAME(n) ──┼──▶ STRING → WS-ADMIN-OPT-TXT
       │               │                              │         └──▶ OPTN00nO → Terminal
       │               └── CDEMO-ADMIN-OPT-PGMNAME(n) ──▶ XCTL PROGRAM target
       │
       ├── CSDAT01Y (Date/Time)
       │       │
       │       └── FUNCTION CURRENT-DATE → WS-CURDATE-DATA
       │               ├── WS-CURDATE-MM/DD/YY → CURDATEO → Terminal
       │               └── WS-CURTIME-HH/MM/SS → CURTIMEO → Terminal
       │
       ├── WS-VARIABLES
       │       ├── WS-TRANID ("CA00") ──▶ TRNNAMEO → Terminal
       │       └── WS-PGMNAME ("COADM01C") ──▶ PGMNAMEO → Terminal
       │
       └── Terminal Input (COADM1AI)
               │
               └── OPTIONI (2-char option number)
                       │
                       ├── Trim trailing spaces
                       ├── Replace spaces with '0' → WS-OPTION-X
                       ├── MOVE to WS-OPTION (numeric PIC 9(02))
                       ├── MOVE to OPTIONO (echo back to screen)
                       └── Used as index into CDEMO-ADMIN-OPT array
                               └── CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION) → XCTL target
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| CCDA-TITLE01 | COTTL01Y (WS) | Direct move | TITLE01O | COADM1AO → Terminal |
| CCDA-TITLE02 | COTTL01Y (WS) | Direct move | TITLE02O | COADM1AO → Terminal |
| WS-TRANID | WS-VARIABLES | Direct move | TRNNAMEO | COADM1AO → Terminal |
| WS-PGMNAME | WS-VARIABLES | Direct move | PGMNAMEO | COADM1AO → Terminal |
| FUNCTION CURRENT-DATE | COBOL intrinsic | Extract year(3:2)/month/day → mm/dd/yy | CURDATEO | COADM1AO → Terminal |
| FUNCTION CURRENT-DATE | COBOL intrinsic | Extract hours/minute/second → hh:mm:ss | CURTIMEO | COADM1AO → Terminal |
| CDEMO-ADMIN-OPT-NUM(n) | COADM02Y (WS) | STRING with ". " separator | WS-ADMIN-OPT-TXT | → OPTN00nO → Terminal |
| CDEMO-ADMIN-OPT-NAME(n) | COADM02Y (WS) | STRING concatenation | WS-ADMIN-OPT-TXT | → OPTN00nO → Terminal |
| OPTIONI | Terminal (COADM1AI) | Trim spaces, replace ' ' with '0', move to PIC 9(02) | WS-OPTION | Index into CDEMO-ADMIN-OPT array |
| CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION) | COADM02Y (WS) | Direct use | XCTL PROGRAM argument | CICS XCTL command |
| WS-TRANID | WS-VARIABLES | Direct move | CDEMO-FROM-TRANID | CARDDEMO-COMMAREA (before XCTL) |
| WS-PGMNAME | WS-VARIABLES | Direct move | CDEMO-FROM-PROGRAM | CARDDEMO-COMMAREA (before XCTL) |
| ZEROS | Literal | Direct move | CDEMO-PGM-CONTEXT | CARDDEMO-COMMAREA (reset for target) |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`). There is no program-specific extension — the admin menu operates entirely on the shared portion.

**CARDDEMO-COMMAREA (COCOM01Y — shared portion):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-FROM-TRANID | X(04) | 4 | IN/OUT | Originating transaction ID (set to 'CA00' before XCTL) |
| CDEMO-FROM-PROGRAM | X(08) | 8 | IN/OUT | Originating program name (set to 'COADM01C' before XCTL) |
| CDEMO-TO-TRANID | X(04) | 4 | OUT | Target transaction ID |
| CDEMO-TO-PROGRAM | X(08) | 8 | OUT | Target program for XCTL (set to 'COSGN00C' on PF3) |
| CDEMO-USER-ID | X(08) | 8 | IN | Authenticated user ID (from sign-on) |
| CDEMO-USER-TYPE | X(01) | 1 | IN | 'A' = Admin, 'U' = User (must be 'A' for admin menu) |
| CDEMO-PGM-CONTEXT | 9(01) | 1 | IN/OUT | 0 = first entry (PGM-ENTER), 1 = re-entry (PGM-REENTER) |
| CDEMO-CUST-ID | 9(09) | 9 | IN | Customer ID (passed through) |
| CDEMO-CUST-FNAME | X(25) | 25 | IN | Customer first name (passed through) |
| CDEMO-CUST-MNAME | X(25) | 25 | IN | Customer middle name (passed through) |
| CDEMO-CUST-LNAME | X(25) | 25 | IN | Customer last name (passed through) |
| CDEMO-ACCT-ID | 9(11) | 11 | IN | Account ID (passed through) |
| CDEMO-ACCT-STATUS | X(01) | 1 | IN | Account status (passed through) |
| CDEMO-CARD-NUM | 9(16) | 16 | IN | Card number (passed through) |
| CDEMO-LAST-MAP | X(7) | 7 | IN/OUT | Last BMS map displayed |
| CDEMO-LAST-MAPSET | X(7) | 7 | IN/OUT | Last BMS mapset used |

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. All communication with the user is via screen messages (WS-MESSAGE → ERRMSGO). There are no called sub-programs that return status codes.

### 7.3 Conversation State Management

- **COMMAREA preservation**: The entire CARDDEMO-COMMAREA is passed on every `RETURN TRANSID('CA00')`. CICS restores it automatically on the next dispatch.
- **PGM-CONTEXT flag**: Distinguishes first entry (0 — display menu screen) from subsequent interactions (1 — receive and process user input).
- **Before XCTL**: The program sets `CDEMO-FROM-TRANID = 'CA00'`, `CDEMO-FROM-PROGRAM = 'COADM01C'`, and resets `CDEMO-PGM-CONTEXT = 0` so the target program knows it is being entered for the first time.
- **No TSQ or file state**: All state fits within the COMMAREA; no temporary storage queues or files are used.
- **Screen field state**: BMS `FSET` attribute on the OPTION field ensures the user's input is returned on every RECEIVE.

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path:
    Display Menu → User enters option → Validate → XCTL to target program

  Validation Error (Invalid Option):
    User enters option → NOT NUMERIC or > 6 or = 0
                         ──▶ "Please enter a valid option number..."
                             → Redisplay menu, RETURN TRANSID

  Program Not Installed (DUMMY):
    User enters option → CDEMO-ADMIN-OPT-PGMNAME starts with 'DUMMY'
                         ──▶ "This option is not installed ..." (green)
                             → Redisplay menu, RETURN TRANSID

  Program Not Found (PGMIDERR):
    User enters option → XCTL fails with PGMIDERR
                         ──▶ "This option is not installed ..." (green)
                             → Redisplay menu, RETURN TRANSID

  Invalid Key:
    User presses unsupported key → EIBAID not DFHENTER or DFHPF3
                                   ──▶ "Invalid key pressed. Please see below..."
                                       → Redisplay menu, RETURN TRANSID

  No COMMAREA (Direct Invocation):
    EIBCALEN = 0 → XCTL to COSGN00C (sign-on screen)
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| EIBCALEN = 0 (no COMMAREA) | — | Set CDEMO-FROM-PROGRAM = 'COSGN00C', XCTL to sign-on | — (redirected) | — |
| Option not numeric | — | Set ERR-FLG = 'Y' | "Please enter a valid option number..." | — |
| Option > CDEMO-ADMIN-OPT-COUNT (6) | — | Set ERR-FLG = 'Y' | "Please enter a valid option number..." | — |
| Option = 0 | — | Set ERR-FLG = 'Y' | "Please enter a valid option number..." | — |
| Program name starts with 'DUMMY' | — | Set ERRMSGC = DFHGREEN | "This option is not installed ..." | — |
| PGMIDERR on XCTL | PGMIDERR condition | HANDLE CONDITION → PGMIDERR-ERR-PARA | "This option is not installed ..." | — |
| Unrecognized AID key | — | Set ERR-FLG = 'Y' | CCDA-MSG-INVALID-KEY ("Invalid key pressed. Please see below...") | — |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| PGMIDERR | PGMIDERR-ERR-PARA | Display "This option is not installed ..." in green, SEND-MENU-SCREEN, RETURN TRANSID('CA00') |
| — | No explicit HANDLE ABEND | Default CICS abend handling applies |

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "Please enter a valid option number..." | Option not numeric, > 6, or = 0 | ERRMSG (line 23, col 1), color RED |
| — | "This option is not installed ..." | Target program starts with 'DUMMY' or PGMIDERR on XCTL | ERRMSG (line 23, col 1), color GREEN |
| — | "Invalid key pressed. Please see below..." | Any AID key other than ENTER or PF3 | ERRMSG (line 23, col 1), color RED |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CA00 transaction must be authorized for user |
| Resource-level security | RACF FACILITY for CICS program resources |
| Application-level check | If EIBCALEN = 0 (no COMMAREA — direct invocation without login), XCTL to sign-on screen COSGN00C |
| User type restriction | Only admin users (CDEMO-USER-TYPE = 'A') should reach this menu; sign-on program (COSGN00C) enforces routing based on user type |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| — | — | No file or data access — menu is purely navigational |
| CICS Program Resources | EXECUTE authority for target programs (COUSR00C, etc.) | CICS Program Control (XCTL) |

### 9.3 Audit Trail

| Event | Trigger | Data Logged | Destination |
|-------|---------|-------------|-------------|
| No explicit audit logging | — | — | Transaction may be audited via CICS journal/SMF records at the system level |
| PGMIDERR condition | XCTL to non-existent program | PGMIDERR condition raised | CICS system log (implicit) |

---

## 10. Performance Characteristics

### 10.1 Performance Profile

| Metric | Baseline | Peak | SLA |
|--------|----------|------|-----|
| Response time | < 0.5 seconds | < 1 second | < 1 second |
| CPU per transaction | Very low (no file I/O, no validation logic) | — | — |
| File I/O count | 0 (no file access) | 0 | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal | — | — |

### 10.2 Performance Considerations

- **No file I/O**: This is a purely navigational menu program. All data (menu options, titles) comes from WORKING-STORAGE copybooks compiled into the program. Response time is dominated by BMS SEND/RECEIVE and terminal I/O.
- **COMMAREA size**: Uses only the shared CARDDEMO-COMMAREA (approximately 160 bytes); no overflow to TSQ.
- **BMS SEND/RECEIVE**: Standard map I/O with ERASE — one pair per pseudo-conversational iteration.
- **BUILD-MENU-OPTIONS loop**: Iterates up to CDEMO-ADMIN-OPT-COUNT (6) times using STRING — negligible CPU overhead.
- **XCTL transfer**: When a valid option is selected, control is transferred immediately via XCTL with no additional processing overhead.

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Display admin menu on first entry | Admin user signs in | COADM1A displayed with 6 options, current date/time | High |
| TC-002 | Select option 1 (User List) | Enter "1", press ENTER | XCTL to COUSR00C — User List screen displayed | High |
| TC-003 | Select option 2 (User Add) | Enter "2", press ENTER | XCTL to COUSR01C — User Add screen displayed | High |
| TC-004 | Select option 3 (User Update) | Enter "3", press ENTER | XCTL to COUSR02C — User Update screen displayed | High |
| TC-005 | Select option 4 (User Delete) | Enter "4", press ENTER | XCTL to COUSR03C — User Delete screen displayed | High |
| TC-006 | Select option 5 (Tran Type List - Db2) | Enter "5", press ENTER | XCTL to COTRTLIC — Tran Type List screen displayed | High |
| TC-007 | Select option 6 (Tran Type Maint - Db2) | Enter "6", press ENTER | XCTL to COTRTUPC — Tran Type Maint screen displayed | High |
| TC-008 | Invalid option — too high | Enter "7", press ENTER | "Please enter a valid option number..." | High |
| TC-009 | Invalid option — zero | Enter "0", press ENTER | "Please enter a valid option number..." | High |
| TC-010 | Invalid option — non-numeric | Enter "AB", press ENTER | "Please enter a valid option number..." | Medium |
| TC-011 | Invalid option — blank | Press ENTER with empty option | "Please enter a valid option number..." (after space→0 conversion, option = 0) | Medium |
| TC-012 | PF3 exit | Press PF3 | XCTL to COSGN00C — sign-on screen displayed | High |
| TC-013 | Invalid PF key | Press PF2, PF4, PF5, etc. | "Invalid key pressed. Please see below..." | Medium |
| TC-014 | CLEAR key | Press CLEAR | "Invalid key pressed. Please see below..." | Low |
| TC-015 | Uninstalled option (DUMMY) | Option configured with 'DUMMY' prefix program name | "This option is not installed ..." (green text) | Medium |
| TC-016 | PGMIDERR — program not in CICS | Option points to program not defined in CSD | "This option is not installed ..." (green text) | Medium |
| TC-017 | Direct invocation without login | Execute CA00 with no COMMAREA (EIBCALEN=0) | Redirect to COSGN00C sign-on screen | High |
| TC-018 | Return from sub-function | XCTL back from COUSR00C etc. | Admin menu redisplayed with options | Medium |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| USRSEC | Pre-loaded with at least one admin user (SEC-USR-TYPE = 'A') | VSAM REPRO from test data (`app/data/`) |
| CSD Definitions | All target programs (COUSR00C, COUSR01C, COUSR02C, COUSR03C, COTRTLIC, COTRTUPC) must be defined | CARDDEMO.CSD installed via CICS CEDA |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | Option count is hard-coded in COADM02Y (CDEMO-ADMIN-OPT-COUNT = 6) but the option array is defined as OCCURS 9 TIMES — mismatch between count and array size | Adding options requires updating the count value in COADM02Y and recompiling | Maintain both count and option data in sync during maintenance |
| KI-002 | BUILD-MENU-OPTIONS uses EVALUATE with hard-coded WHEN 1 through WHEN 10 — limits display to 10 options even though 12 BMS fields exist (OPTN001–OPTN012) | Options 11 and 12 can never be populated | Refactor to use indexed array access instead of EVALUATE |
| KI-003 | No explicit HANDLE ABEND — unexpected abends use default CICS handling | User sees CICS ABEND screen rather than a friendly error | Add HANDLE ABEND with a user-friendly error screen |
| KI-004 | The "not installed" message is displayed for both DUMMY programs and PGMIDERR — the PGMIDERR handler duplicates the logic but adds its own RETURN TRANSID, creating two possible return points | Maintenance complexity — two code paths produce the same result | Consolidate into a single error-handling paragraph |
| KI-005 | The RETURN-TO-SIGNON-SCREEN paragraph on PF3 exit does not pass COMMAREA on the bare XCTL to COSGN00C | COSGN00C receives EIBCALEN=0, which may be its expected behavior for a fresh sign-on | Confirm COSGN00C design intent for EIBCALEN=0 entry |
| KI-006 | WS-USRSEC-FILE (PIC X(08) VALUE 'USRSEC') is defined in WORKING-STORAGE but never used in the program | Dead code — leftover from copy/paste or planned feature | Remove unused variable |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Screen I/O | BMS Map COADM1A / 3270 terminal | Web-based admin dashboard or SPA with navigation menu | Low — simple menu with no data input |
| Navigation | XCTL-based program transfer with COMMAREA | URL routing / SPA router (React Router, Angular Router) | Low — 1:1 mapping of menu options to routes |
| Menu Configuration | Hard-coded in COADM02Y copybook, requires recompile to change | Database-driven or configuration file (JSON/YAML) menu definitions | Low — externalize menu configuration |
| State Management | COMMAREA pseudo-conversational | HTTP session, JWT token, or SPA client-side state | Low — minimal state (user ID, type, navigation context) |
| User Type Routing | Sign-on program routes admin vs. regular users to different menus | Role-based access control (RBAC) with conditional UI rendering | Low — replace with role-based menu filtering |
| Authentication | CICS transaction security (RACF) + application-level EIBCALEN check | OAuth2/OIDC, SAML, or API key authentication | Medium — depends on target identity provider |
| Error Handling | HANDLE CONDITION PGMIDERR + inline message display | Framework exception handling with user-friendly error pages | Low |
| Program Existence Check | Runtime PGMIDERR condition trapping | Build-time dependency validation or feature flags | Low — replace with configuration-driven feature toggles |

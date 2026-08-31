# Main Menu — Regular Users (CM00) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CM00 |
| Entry Program | COMEN01C |
| Business Domain | Navigation / Application Shell |
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

- **What**: Presents the primary navigation menu for regular (non-admin) CardDemo users. Displays a numbered list of application functions (Account View, Credit Card List, Transaction List, etc.) and routes the user to the selected sub-application via XCTL.
- **Who**: Any authenticated regular user (CDEMO-USER-TYPE = 'U'). Admin users can also access this menu but see the same options; admin-only options are blocked at runtime.
- **When**: Immediately after successful sign-on (COSGN00C transfers control here), or when a sub-application returns via PF3/exit.
- **Why**: Provides a centralized hub for all CardDemo online functions, enforcing role-based access control by restricting admin-only menu options from regular users.
- **What** is the business outcome: The user is transferred (XCTL) to the selected sub-application program with the shared COMMAREA, or returned to the sign-on screen on PF3.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  User signs in via COSGN00C → XCTL to COMEN01C
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ System Response          │  Display Main Menu screen (COMEN1A) with
  └────────────┬─────────────┘  numbered list of 11 application options
               │
               ▼
  ┌──────────────────────────┐
  │ User Action              │  Enter a menu option number (01–11) and
  └────────────┬─────────────┘  press ENTER
               │
               ▼
           ┌───┴───┐
           │Decision│  Valid option?
           └───┬───┘
              ╱ ╲
        Yes ╱     ╲ No
           ▼       ▼
  ┌────────────────┐  ┌──────────────────────────┐
  │ Check user     │  │ Error: "Please enter a    │
  │ authorization  │  │ valid option number..."   │
  └───────┬────────┘  └──────────────────────────┘
          │
      ┌───┴───┐
      │Authzd?│  Regular user selecting admin-only option?
      └───┬───┘
         ╱ ╲
   Yes ╱     ╲ No
      ▼       ▼
  ┌──────────────────┐  ┌──────────────────────────┐
  │ Check program    │  │ Error: "No access -       │
  │ installed?       │  │ Admin Only option..."     │
  └───────┬──────────┘  └──────────────────────────┘
          │
      ┌───┴────┐
      │Install?│
      └───┬────┘
         ╱ ╲
   Yes ╱     ╲ No
      ▼       ▼
  ┌──────────────────┐  ┌──────────────────────────┐
  │ XCTL to selected │  │ Error: "This option       │
  │ program          │  │ [name] is not installed"  │
  └──────────────────┘  │ or "is coming soon..."    │
                        └──────────────────────────┘

  PF3 pressed at any time:
  ┌──────────────────────────┐
  │ XCTL to COSGN00C         │  Return to sign-on screen
  └──────────────────────────┘
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | Valid option required | Option number must be numeric, 1–11 (CDEMO-MENU-OPT-COUNT) | Error: "Please enter a valid option number..." |
| BR-002 | Role-based access | Regular user (CDEMO-USRTYP-USER) selects option with USRTYPE = 'A' | Error: "No access - Admin Only option..." |
| BR-003 | Program availability — COPAUS0C | CICS INQUIRE PROGRAM on COPAUS0C (Pending Authorization View) | If not installed: "This option [name] is not installed..." (RED) |
| BR-004 | Stub programs | Selected program name starts with 'DUMMY' | "This option [name] is coming soon..." (GREEN) |
| BR-005 | Normal navigation | Valid option, authorized, program exists and not DUMMY | XCTL to target program with CARDDEMO-COMMAREA |
| BR-006 | Sign-on required | EIBCALEN = 0 (no COMMAREA — direct invocation without login) | XCTL to sign-on screen COSGN00C |
| BR-007 | Exit to sign-on | PF3 pressed | XCTL to COSGN00C (or CDEMO-TO-PROGRAM if populated) |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Sign in via COSGN00C | XCTL to COMEN01C; display Main Menu (COMEN1A) with 11 options | Enter application |
| 2 | Enter option number (01–11), press ENTER | Validate option, check authorization, transfer to selected program | Navigate to sub-application |
| Alt-A | Press PF3 | XCTL to COSGN00C sign-on screen | Exit application / sign off |
| Alt-B | Press any other key | "Invalid key pressed. Please see below..." | Invalid key rejection |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram — Overview

```
  TERMINAL
     │
     │  TRANSID: CM00
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COMEN01C     │ (Entry program — menu router)      │
  │  │ (Main Menu)  │                                    │
  │  └──┬────┬──────┘                                    │
  │     │    │                                            │
  │     ▼    ▼                                            │
  │  ┌──────┐  ┌─────────────────────────────────────┐   │
  │  │BMS   │  │ Target Programs (via XCTL)           │   │
  │  │MAP:  │  │  COACTVWC (Account View)             │   │
  │  │COMEN │  │  COACTUPC (Account Update)           │   │
  │  │1A    │  │  COCRDLIC (Credit Card List)         │   │
  │  └──────┘  │  COCRDSLC (Credit Card View)         │   │
  │            │  COCRDUPC (Credit Card Update)        │   │
  │            │  COTRN00C (Transaction List)          │   │
  │            │  COTRN01C (Transaction View)          │   │
  │            │  COTRN02C (Transaction Add)           │   │
  │            │  CORPT00C (Transaction Reports)       │   │
  │            │  COBIL00C (Bill Payment)              │   │
  │            │  COPAUS0C (Pending Auth View)         │   │
  │            │  COSGN00C (Sign-on — exit target)     │   │
  │            └─────────────────────────────────────┘   │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram — Detailed (Step-by-Step)

```
  Iteration 1 (First entry — from COSGN00C via XCTL):
  1. COSGN00C XCTLs to COMEN01C with CARDDEMO-COMMAREA
  2. EIBCALEN > 0; CDEMO-PGM-REENTER = 0 (first entry)
  3. SET CDEMO-PGM-REENTER TO TRUE
  4. MOVE LOW-VALUES TO COMEN1AO (clear output map)
  5. PERFORM SEND-MENU-SCREEN
     ├── PERFORM POPULATE-HEADER-INFO
     │   ├── FUNCTION CURRENT-DATE → WS-CURDATE-DATA
     │   ├── MOVE CCDA-TITLE01 → TITLE01O ("AWS Mainframe Modernization")
     │   ├── MOVE CCDA-TITLE02 → TITLE02O ("CardDemo")
     │   ├── MOVE WS-TRANID → TRNNAMEO ("CM00")
     │   ├── MOVE WS-PGMNAME → PGMNAMEO ("COMEN01C")
     │   ├── Format date → CURDATEO ("mm/dd/yy")
     │   └── Format time → CURTIMEO ("hh:mm:ss")
     ├── PERFORM BUILD-MENU-OPTIONS
     │   └── Loop WS-IDX 1 to CDEMO-MENU-OPT-COUNT (11)
     │       └── STRING option number ". " option name → OPTNnnnO
     ├── MOVE WS-MESSAGE → ERRMSGO
     └── SEND MAP('COMEN1A') MAPSET('COMEN01') FROM(COMEN1AO) ERASE
  6. RETURN TRANSID('CM00') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 2+ (User selects option — ENTER pressed):
  7.  CICS dispatches → COMEN01C (COMMAREA restored)
  8.  EIBCALEN > 0; CDEMO-PGM-REENTER = 1
  9.  PERFORM RECEIVE-MENU-SCREEN
      └── RECEIVE MAP('COMEN1A') MAPSET('COMEN01') INTO(COMEN1AI)
  10. EVALUATE EIBAID:
      ├── DFHENTER → PERFORM PROCESS-ENTER-KEY
      ├── DFHPF3  → MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM
      │               PERFORM RETURN-TO-SIGNON-SCREEN
      │               └── XCTL PROGRAM(CDEMO-TO-PROGRAM)
      └── OTHER   → Set ERR-FLG, MOVE CCDA-MSG-INVALID-KEY to WS-MESSAGE
                     PERFORM SEND-MENU-SCREEN
  11. RETURN TRANSID('CM00') COMMAREA(CARDDEMO-COMMAREA)

  PROCESS-ENTER-KEY (when ENTER pressed):
  12. Trim trailing spaces from OPTIONI, right-justify into WS-OPTION-X
  13. Replace spaces with '0', MOVE to WS-OPTION (numeric)
  14. Validate:
      ├── WS-OPTION not numeric, > 11, or = 0
      │   → ERR-FLG = 'Y', "Please enter a valid option number..."
      │   → PERFORM SEND-MENU-SCREEN
      └── WS-OPTION valid → continue
  15. Authorization check:
      ├── CDEMO-USRTYP-USER AND CDEMO-MENU-OPT-USRTYPE(WS-OPTION) = 'A'
      │   → ERR-FLG = 'Y', "No access - Admin Only option..."
      │   → PERFORM SEND-MENU-SCREEN
      └── Authorized → continue
  16. If not ERR-FLG-ON, EVALUATE TRUE:
      ├── WHEN program = 'COPAUS0C'
      │   ├── EXEC CICS INQUIRE PROGRAM(pgm) NOHANDLE
      │   ├── If EIBRESP = NORMAL:
      │   │   ├── MOVE WS-TRANID → CDEMO-FROM-TRANID
      │   │   ├── MOVE WS-PGMNAME → CDEMO-FROM-PROGRAM
      │   │   ├── MOVE ZEROS → CDEMO-PGM-CONTEXT
      │   │   └── XCTL PROGRAM(pgm) COMMAREA(CARDDEMO-COMMAREA)
      │   └── Else:
      │       └── "This option [name] is not installed..." (RED)
      ├── WHEN program(1:5) = 'DUMMY'
      │   └── "This option [name] is coming soon..." (GREEN)
      └── WHEN OTHER (normal navigation)
          ├── MOVE WS-TRANID → CDEMO-FROM-TRANID
          ├── MOVE WS-PGMNAME → CDEMO-FROM-PROGRAM
          ├── MOVE ZEROS → CDEMO-PGM-CONTEXT
          └── XCTL PROGRAM(pgm) COMMAREA(CARDDEMO-COMMAREA)
  17. PERFORM SEND-MENU-SCREEN (only reached if error/info message)
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | >0 | XCTL from COSGN00C (sign-on) | Initial logic (PGM-ENTER) | COMEN1A (menu with 11 options) | CM00 |
| 2 | >0 | Enter option number, press ENTER | RECEIVE → PROCESS-ENTER-KEY | COMEN1A (with error) or XCTL to target | CM00 (if error) or — (XCTL) |
| N | >0 | PF3 (Exit) | XCTL to COSGN00C (sign-on) | — | — (XCTL) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | RECEIVE MAP('COMEN1A') MAPSET('COMEN01') INTO(COMEN1AI) | Capture user input (option selection) | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 2 | INQUIRE PROGRAM(CDEMO-MENU-OPT-PGMNAME) NOHANDLE | Check if COPAUS0C program is installed | EIBRESP = NORMAL → installed; other → not installed |
| 3 | XCTL PROGRAM(target) COMMAREA(CARDDEMO-COMMAREA) | Transfer control to selected sub-application | — (control does not return) |
| 4 | XCTL PROGRAM(COSGN00C) | Return to sign-on screen (PF3 or EIBCALEN=0) | — (control does not return) |
| 5 | SEND MAP('COMEN1A') MAPSET('COMEN01') FROM(COMEN1AO) ERASE | Send menu screen to terminal | — |
| 6 | RETURN TRANSID('CM00') COMMAREA(CARDDEMO-COMMAREA) | Pseudo-conversational return | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────────┐                 ┌──────────────────────────────────────┐
  │ Sign-on Screen  │   Successful    │  COMEN1A (Main Menu)                 │
  │ (COSGN00C)      │   sign-on       │                                      │
  │ TRANSID: CC00   │────────────────▶│  Tran: CM00  AWS Mainframe...  Date  │
  └─────────────────┘                 │  Prog: COMEN01C  CardDemo      Time  │
        ▲                             │                                      │
        │ PF3                         │           Main Menu                  │
        │                             │                                      │
        │                             │  01. Account View                    │
        │                             │  02. Account Update                  │
        │                             │  03. Credit Card List                │
        │                             │  04. Credit Card View                │
        │                             │  05. Credit Card Update              │
        │                             │  06. Transaction List                │
        │                             │  07. Transaction View                │
        │                             │  08. Transaction Add                 │
        │                             │  09. Transaction Reports             │
        │                             │  10. Bill Payment                    │
        │                             │  11. Pending Authorization View      │
        │                             │                                      │
        │                             │  Please select an option : __        │
        │                             │                                      │
        │                             │  [Error/Info message line]           │
        │                             │  ENTER=Continue  F3=Exit             │
        └─────────────────────────────┤                                      │
                                      └───────────┬──────────────────────────┘
                                                  │
                                          Option 1–11 + ENTER
                                                  │
                                                  ▼
                              ┌─────────────────────────────────────┐
                              │  Target Sub-Application (via XCTL)  │
                              │  COACTVWC / COACTUPC / COCRDLIC /   │
                              │  COCRDSLC / COCRDUPC / COTRN00C /   │
                              │  COTRN01C / COTRN02C / CORPT00C /   │
                              │  COBIL00C / COPAUS0C                │
                              └─────────────────────────────────────┘
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| COMEN1A | COMEN01 | Main Menu display and option selection | OPTION (input), OPTN001–OPTN012 (dynamic menu text), TRNNAME, PGMNAME, CURDATE, CURTIME, TITLE01, TITLE02, ERRMSG | ENTER=Select option, PF3=Exit to sign-on |

### 3.3 Screen Field Details

#### Map: COMEN1A

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction ID ("CM00") |
| TITLE01 | ASKIP,FSET,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,FSET,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | ASKIP,FSET,NORM | 8 | Alpha | — | Program name ("COMEN01C") |
| TITLE02 | ASKIP,FSET,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | ASKIP,FSET,NORM | 8 | Time | — | Current time hh:mm:ss |
| (literal) | ASKIP,BRT | 9 | Alpha | — | "Main Menu" label (row 4, col 35) |
| OPTN001 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 1 text (e.g., "01. Account View") |
| OPTN002 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 2 text (e.g., "02. Account Update") |
| OPTN003 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 3 text (e.g., "03. Credit Card List") |
| OPTN004 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 4 text (e.g., "04. Credit Card View") |
| OPTN005 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 5 text (e.g., "05. Credit Card Update") |
| OPTN006 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 6 text (e.g., "06. Transaction List") |
| OPTN007 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 7 text (e.g., "07. Transaction View") |
| OPTN008 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 8 text (e.g., "08. Transaction Add") |
| OPTN009 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 9 text (e.g., "09. Transaction Reports") |
| OPTN010 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 10 text (e.g., "10. Bill Payment") |
| OPTN011 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 11 text (e.g., "11. Pending Authorization View") |
| OPTN012 | ASKIP,FSET,NORM | 40 | Alpha | — | Menu option 12 text (unused — reserved slot) |
| (literal) | ASKIP,BRT | 25 | Alpha | — | "Please select an option :" prompt (row 20) |
| OPTION | FSET,IC,NORM,NUM,UNPROT | 2 | Numeric | Must be numeric, 01–11 | User-entered menu option number |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/info message display area |
| (literal) | ASKIP,NORM | 23 | Alpha | — | "ENTER=Continue  F3=Exit" (row 24) |

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | COMEN1A | Validate option number, check authorization, XCTL to target program |
| PF3 | COMEN1A | XCTL to COSGN00C sign-on screen (exit application) |
| OTHER | COMEN1A | Set ERR-FLG, display "Invalid key pressed. Please see below..." |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COMEN01C | Main Menu — display menu, validate selection, route to sub-application | TRANSID CM00 / XCTL from COSGN00C | 308 | `app/cbl/COMEN01C.cbl` |
| COSGN00C | Sign-on Screen — authentication, entry point | XCTL (caller/return target) | — | `app/cbl/COSGN00C.cbl` |
| COACTVWC | Account View | XCTL (target — option 1) | — | `app/cbl/COACTVWC.cbl` |
| COACTUPC | Account Update | XCTL (target — option 2) | — | `app/cbl/COACTUPC.cbl` |
| COCRDLIC | Credit Card List | XCTL (target — option 3) | — | `app/cbl/COCRDLIC.cbl` |
| COCRDSLC | Credit Card View | XCTL (target — option 4) | — | `app/cbl/COCRDSLC.cbl` |
| COCRDUPC | Credit Card Update | XCTL (target — option 5) | — | `app/cbl/COCRDUPC.cbl` |
| COTRN00C | Transaction List | XCTL (target — option 6) | — | `app/cbl/COTRN00C.cbl` |
| COTRN01C | Transaction View | XCTL (target — option 7) | — | `app/cbl/COTRN01C.cbl` |
| COTRN02C | Transaction Add | XCTL (target — option 8) | — | `app/cbl/COTRN02C.cbl` |
| CORPT00C | Transaction Reports | XCTL (target — option 9) | — | `app/cbl/CORPT00C.cbl` |
| COBIL00C | Bill Payment | XCTL (target — option 10) | — | `app/cbl/COBIL00C.cbl` |
| COPAUS0C | Pending Authorization View | XCTL (target — option 11, with INQUIRE check) | — | `app/cbl/COPAUS0C.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CM00
└── COMEN01C (Entry — Menu Display and Routing)
    ├── RECEIVE MAP('COMEN1A') — Capture option selection
    ├── INQUIRE PROGRAM('COPAUS0C') — Check installation (option 11 only)
    ├── XCTL → COACTVWC  (option 1: Account View)
    ├── XCTL → COACTUPC  (option 2: Account Update)
    ├── XCTL → COCRDLIC  (option 3: Credit Card List)
    ├── XCTL → COCRDSLC  (option 4: Credit Card View)
    ├── XCTL → COCRDUPC  (option 5: Credit Card Update)
    ├── XCTL → COTRN00C  (option 6: Transaction List)
    ├── XCTL → COTRN01C  (option 7: Transaction View)
    ├── XCTL → COTRN02C  (option 8: Transaction Add)
    ├── XCTL → CORPT00C  (option 9: Transaction Reports)
    ├── XCTL → COBIL00C  (option 10: Bill Payment)
    ├── XCTL → COPAUS0C  (option 11: Pending Auth View)
    ├── XCTL → COSGN00C  (PF3: exit to sign-on)
    ├── SEND MAP('COMEN1A') — Display/refresh menu screen
    └── RETURN TRANSID('CM00') — Pseudo-conversational return
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COSGN00C | COMEN01C | XCTL | CARDDEMO-COMMAREA (user ID, user type populated) | — | After successful sign-on |
| COMEN01C | COACTVWC | XCTL | CARDDEMO-COMMAREA (FROM-TRANID=CM00, FROM-PROGRAM=COMEN01C, PGM-CONTEXT=0) | — | Option 1 selected |
| COMEN01C | COACTUPC | XCTL | CARDDEMO-COMMAREA | — | Option 2 selected |
| COMEN01C | COCRDLIC | XCTL | CARDDEMO-COMMAREA | — | Option 3 selected |
| COMEN01C | COCRDSLC | XCTL | CARDDEMO-COMMAREA | — | Option 4 selected |
| COMEN01C | COCRDUPC | XCTL | CARDDEMO-COMMAREA | — | Option 5 selected |
| COMEN01C | COTRN00C | XCTL | CARDDEMO-COMMAREA | — | Option 6 selected |
| COMEN01C | COTRN01C | XCTL | CARDDEMO-COMMAREA | — | Option 7 selected |
| COMEN01C | COTRN02C | XCTL | CARDDEMO-COMMAREA | — | Option 8 selected |
| COMEN01C | CORPT00C | XCTL | CARDDEMO-COMMAREA | — | Option 9 selected |
| COMEN01C | COBIL00C | XCTL | CARDDEMO-COMMAREA | — | Option 10 selected |
| COMEN01C | COPAUS0C | XCTL | CARDDEMO-COMMAREA | — | Option 11 selected (after INQUIRE check) |
| COMEN01C | COSGN00C | XCTL | — (no COMMAREA) | — | PF3 exit or EIBCALEN = 0 |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| — | — | — | — | — | This transaction does not access any VSAM files or databases directly |

This transaction is purely a navigation/routing program. All data access occurs in the target sub-applications after XCTL.

### 5.2 File Access Details

No file access is performed by COMEN01C. The menu option definitions are hardcoded in the COMEN02Y copybook (working storage), not read from a file.

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
  COSGN00C (Sign-on)
       │
       └──▶ CARDDEMO-COMMAREA
                │
                ├── CDEMO-FROM-PROGRAM = 'COSGN00C'
                ├── CDEMO-USER-ID (authenticated user)
                ├── CDEMO-USER-TYPE ('U' = regular, 'A' = admin)
                └── CDEMO-PGM-CONTEXT = 0 (first entry)
                         │
                         ▼
                    COMEN01C
                         │
  Iteration 1:           │
  ┌──────────────────────┘
  │
  ├──▶ COMEN02Y (hardcoded menu options table)
  │        │
  │        └──▶ BUILD-MENU-OPTIONS loop (1 to 11)
  │                 │
  │                 └──▶ STRING OPT-NUM ". " OPT-NAME → WS-MENU-OPT-TXT
  │                          │
  │                          └──▶ OPTN001O through OPTN011O (screen output fields)
  │
  ├──▶ POPULATE-HEADER-INFO
  │        ├── FUNCTION CURRENT-DATE → WS-CURDATE-DATA
  │        ├── CCDA-TITLE01 → TITLE01O
  │        ├── CCDA-TITLE02 → TITLE02O
  │        ├── WS-TRANID → TRNNAMEO
  │        ├── WS-PGMNAME → PGMNAMEO
  │        ├── Formatted date → CURDATEO
  │        └── Formatted time → CURTIMEO
  │
  └──▶ SEND MAP('COMEN1A') → Terminal

  Iteration 2+:
  Terminal
       │
       └──▶ RECEIVE MAP('COMEN1A') → COMEN1AI
                │
                └──▶ OPTIONI (user-entered option number)
                         │
                         ├──▶ Trim trailing spaces, right-justify
                         ├──▶ INSPECT REPLACING spaces with '0'
                         └──▶ WS-OPTION (PIC 9(02))
                                  │
                                  ├──▶ CDEMO-MENU-OPT-PGMNAME(WS-OPTION) → XCTL target
                                  ├──▶ CDEMO-MENU-OPT-USRTYPE(WS-OPTION) → authorization check
                                  └──▶ CDEMO-MENU-OPT-NAME(WS-OPTION) → error message text
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| OPTIONI | Terminal (COMEN1AI) | Trim spaces, right-justify, replace spaces with '0' | WS-OPTION-X | Working storage PIC X(02) |
| WS-OPTION-X | Working storage | MOVE (alpha to numeric) | WS-OPTION | Working storage PIC 9(02) |
| WS-OPTION | Working storage | Array index into COMEN02Y table | CDEMO-MENU-OPT-PGMNAME(n) | XCTL target program |
| CDEMO-MENU-OPT-NUM(n) | COMEN02Y copybook | STRING with ". " and OPT-NAME | WS-MENU-OPT-TXT | Working storage PIC X(40) |
| WS-MENU-OPT-TXT | Working storage | MOVE via EVALUATE on WS-IDX | OPTNnnnO | Output map field (COMEN1AO) |
| FUNCTION CURRENT-DATE | Intrinsic function | Extract YYYY, MM, DD, HH, MM, SS | WS-CURDATE-DATA | Date/time working storage |
| WS-CURDATE-MONTH | Date storage | MOVE to formatted field | WS-CURDATE-MM | MM/DD/YY display string |
| WS-CURDATE-DAY | Date storage | MOVE to formatted field | WS-CURDATE-DD | MM/DD/YY display string |
| WS-CURDATE-YEAR(3:2) | Date storage | Reference modification (2-digit year) | WS-CURDATE-YY | MM/DD/YY display string |
| WS-CURTIME-HOURS | Time storage | MOVE to formatted field | WS-CURTIME-HH | HH:MM:SS display string |
| WS-CURTIME-MINUTE | Time storage | MOVE to formatted field | WS-CURTIME-MM | HH:MM:SS display string |
| WS-CURTIME-SECOND | Time storage | MOVE to formatted field | WS-CURTIME-SS | HH:MM:SS display string |
| WS-CURDATE-MM-DD-YY | Formatted date | MOVE | CURDATEO | Screen output field |
| WS-CURTIME-HH-MM-SS | Formatted time | MOVE | CURTIMEO | Screen output field |
| CCDA-TITLE01 | COTTL01Y copybook | Direct MOVE | TITLE01O | Screen output field |
| CCDA-TITLE02 | COTTL01Y copybook | Direct MOVE | TITLE02O | Screen output field |
| WS-TRANID | Working storage | Direct MOVE | TRNNAMEO | Screen output field |
| WS-PGMNAME | Working storage | Direct MOVE | PGMNAMEO | Screen output field |
| WS-TRANID | Working storage | Direct MOVE | CDEMO-FROM-TRANID | COMMAREA (before XCTL) |
| WS-PGMNAME | Working storage | Direct MOVE | CDEMO-FROM-PROGRAM | COMMAREA (before XCTL) |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`). COMEN01C does not define a program-specific extension — it uses only the general portion.

**CARDDEMO-COMMAREA (COCOM01Y — shared portion):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-FROM-TRANID | X(04) | 4 | IN/OUT | Originating transaction ID (set to 'CM00' before XCTL) |
| CDEMO-FROM-PROGRAM | X(08) | 8 | IN/OUT | Originating program name (set to 'COMEN01C' before XCTL) |
| CDEMO-TO-TRANID | X(04) | 4 | OUT | Target transaction ID |
| CDEMO-TO-PROGRAM | X(08) | 8 | OUT | Target program for XCTL (set to 'COSGN00C' on PF3 exit) |
| CDEMO-USER-ID | X(08) | 8 | IN | Authenticated user ID (set by COSGN00C) |
| CDEMO-USER-TYPE | X(01) | 1 | IN | 'A' = Admin, 'U' = User (used for authorization check) |
| CDEMO-PGM-CONTEXT | 9(01) | 1 | IN/OUT | 0 = first entry (display menu), 1 = re-entry (process input) |
| CDEMO-CUST-ID | 9(09) | 9 | Passthrough | Customer ID (preserved for sub-applications) |
| CDEMO-CUST-FNAME | X(25) | 25 | Passthrough | Customer first name |
| CDEMO-CUST-MNAME | X(25) | 25 | Passthrough | Customer middle name |
| CDEMO-CUST-LNAME | X(25) | 25 | Passthrough | Customer last name |
| CDEMO-ACCT-ID | 9(11) | 11 | Passthrough | Account ID (preserved for sub-applications) |
| CDEMO-ACCT-STATUS | X(01) | 1 | Passthrough | Account status |
| CDEMO-CARD-NUM | 9(16) | 16 | Passthrough | Card number (preserved for sub-applications) |
| CDEMO-LAST-MAP | X(7) | 7 | IN/OUT | Last BMS map displayed |
| CDEMO-LAST-MAPSET | X(7) | 7 | IN/OUT | Last BMS mapset used |

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. All error feedback is communicated inline via screen messages (WS-MESSAGE → ERRMSGO on the COMEN1A map). Navigation is handled entirely via XCTL (no LINK/RETURN with data).

### 7.3 Conversation State Management

- **COMMAREA preservation**: The entire CARDDEMO-COMMAREA is passed on every RETURN TRANSID('CM00'). CICS restores it automatically on the next dispatch. Before XCTL to a sub-application, CDEMO-FROM-TRANID and CDEMO-FROM-PROGRAM are set so the target program knows its origin.
- **PGM-CONTEXT flag**: Distinguishes first entry (0 — display empty menu screen) from subsequent interactions (1 — process user input). Set to TRUE (1) on first entry; reset to ZEROS before XCTL to any sub-application so the target program starts fresh.
- **No TSQ overflow**: All state fits within the COMMAREA; no temporary storage queues are used.
- **Screen field state**: BMS FSET attribute on OPTION field ensures the option value is returned on every RECEIVE.

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path:
    Input → Validate Option → Check Auth → Check Install → XCTL → Sub-application

  Invalid Option:
    Input → Validate ──FAIL──▶ "Please enter a valid option number..."
                                → Redisplay menu, RETURN TRANSID

  Admin-Only Option (regular user):
    Input → Auth Check ──FAIL──▶ "No access - Admin Only option..."
                                  → Redisplay menu, RETURN TRANSID

  Program Not Installed (COPAUS0C):
    Input → INQUIRE ──NOT NORMAL──▶ "This option [name] is not installed..."
                                     → Redisplay menu (RED text), RETURN TRANSID

  Stub/Dummy Program:
    Input → Program(1:5) = 'DUMMY' ──▶ "This option [name] is coming soon..."
                                        → Redisplay menu (GREEN text), RETURN TRANSID

  Invalid Key Pressed:
    AID key not ENTER or PF3 ──▶ "Invalid key pressed. Please see below..."
                                 → Redisplay menu, RETURN TRANSID

  No COMMAREA (direct invocation):
    EIBCALEN = 0 ──▶ XCTL to COSGN00C (sign-on screen)
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| EIBCALEN = 0 (no COMMAREA) | — | MOVE 'COSGN00C' TO CDEMO-FROM-PROGRAM, XCTL to COSGN00C | — (redirect) | — |
| Invalid option (non-numeric, 0, or >11) | — | Set ERR-FLG = 'Y' | "Please enter a valid option number..." | — |
| Admin-only option for regular user | — | Set ERR-FLG = 'Y' | "No access - Admin Only option..." | — |
| COPAUS0C not installed | EIBRESP != NORMAL (INQUIRE) | Display error message | "This option [name] is not installed..." | — |
| Dummy/stub program | — (program name starts with 'DUMMY') | Display info message | "This option [name] is coming soon..." | — |
| Invalid AID key | — | Set ERR-FLG = 'Y' | CCDA-MSG-INVALID-KEY ("Invalid key pressed. Please see below...") | — |
| RECEIVE MAP error | RESP stored in WS-RESP-CD | No explicit error handling for MAPFAIL | — | — |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| — | No explicit HANDLE ABEND | Default CICS abend handling applies |

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "Please enter a valid option number..." | WS-OPTION not numeric, = 0, or > CDEMO-MENU-OPT-COUNT (11) | ERRMSG (line 23, col 1) |
| — | "No access - Admin Only option..." | CDEMO-USRTYP-USER and CDEMO-MENU-OPT-USRTYPE(WS-OPTION) = 'A' | ERRMSG (line 23, col 1) |
| — | "This option [name] is not installed..." | CICS INQUIRE PROGRAM(COPAUS0C) returns non-NORMAL EIBRESP | ERRMSG (line 23, col 1), color RED |
| — | "This option [name] is coming soon..." | Selected program name(1:5) = 'DUMMY' | ERRMSG (line 23, col 1), color GREEN |
| — | "Invalid key pressed. Please see below..." | AID key is not DFHENTER and not DFHPF3 | ERRMSG (line 23, col 1) |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CM00 transaction must be authorized for user |
| Resource-level security | RACF FACILITY profiles for COMEN01C program |
| Application-level check (no COMMAREA) | If EIBCALEN = 0 (direct invocation without login), XCTL to sign-on screen COSGN00C |
| Application-level check (user type) | CDEMO-USER-TYPE from COMMAREA checked against CDEMO-MENU-OPT-USRTYPE; regular users blocked from admin-only options |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| — | — | No file/data access is performed by this transaction |

### 9.3 Audit Trail

| Event | Trigger | Data Logged | Destination |
|-------|---------|-------------|-------------|
| No explicit audit logging | — | — | Transaction may be audited via CICS journal/SMF records at the system level |

---

## 10. Performance Characteristics

### 10.1 Performance Profile

| Metric | Baseline | Peak | SLA |
|--------|----------|------|-----|
| Response time | < 0.1 second | < 0.5 seconds | < 1 second |
| CPU per transaction | Minimal (no file I/O, no CALL) | — | — |
| File I/O count | 0 (no file access) | 0 (INQUIRE PROGRAM is in-memory) | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal (SEND/RECEIVE only) | — | — |

### 10.2 Performance Considerations

- **No file I/O**: The menu is built entirely from the in-memory COMEN02Y copybook data structure. No VSAM reads, no DB2 queries.
- **INQUIRE PROGRAM**: Only issued for option 11 (COPAUS0C); this is an in-memory CICS catalog lookup, extremely fast.
- **Single SEND/RECEIVE pair**: One BMS SEND MAP per iteration to render the menu, one RECEIVE MAP per iteration to capture the selection.
- **XCTL transfer**: Control transfer to the target program is near-instantaneous.
- **BUILD-MENU-OPTIONS loop**: Iterates 11 times with STRING operations — negligible CPU overhead.
- **COMMAREA size**: Standard CARDDEMO-COMMAREA (~200 bytes); no TSQ overflow needed.
- **This is the most lightweight transaction in the CardDemo application** — it is purely a routing/navigation program.

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Normal navigation — Account View | Enter option "1", press ENTER | XCTL to COACTVWC, Account View screen displayed | High |
| TC-002 | Normal navigation — Account Update | Enter option "2", press ENTER | XCTL to COACTUPC, Account Update screen displayed | High |
| TC-003 | Normal navigation — Credit Card List | Enter option "3", press ENTER | XCTL to COCRDLIC, Credit Card List screen displayed | High |
| TC-004 | Normal navigation — Credit Card View | Enter option "4", press ENTER | XCTL to COCRDSLC, Credit Card View screen displayed | Medium |
| TC-005 | Normal navigation — Credit Card Update | Enter option "5", press ENTER | XCTL to COCRDUPC, Credit Card Update screen displayed | Medium |
| TC-006 | Normal navigation — Transaction List | Enter option "6", press ENTER | XCTL to COTRN00C, Transaction List screen displayed | High |
| TC-007 | Normal navigation — Transaction View | Enter option "7", press ENTER | XCTL to COTRN01C, Transaction View screen displayed | Medium |
| TC-008 | Normal navigation — Transaction Add | Enter option "8", press ENTER | XCTL to COTRN02C, Transaction Add screen displayed | High |
| TC-009 | Normal navigation — Transaction Reports | Enter option "9", press ENTER | XCTL to CORPT00C, Transaction Reports screen displayed | Medium |
| TC-010 | Normal navigation — Bill Payment | Enter option "10", press ENTER | XCTL to COBIL00C, Bill Payment screen displayed | Medium |
| TC-011 | Normal navigation — Pending Auth View | Enter option "11", press ENTER | INQUIRE PROGRAM; if installed XCTL to COPAUS0C; if not "is not installed..." | Medium |
| TC-012 | Invalid option — zero | Enter "0", press ENTER | "Please enter a valid option number..." | High |
| TC-013 | Invalid option — exceeds max | Enter "12" or "99", press ENTER | "Please enter a valid option number..." | High |
| TC-014 | Invalid option — non-numeric | Enter "AB", press ENTER | "Please enter a valid option number..." | High |
| TC-015 | Invalid option — blank | Press ENTER without entering a number | "Please enter a valid option number..." | High |
| TC-016 | Admin-only option restriction | Regular user selects an option with USRTYPE='A' | "No access - Admin Only option..." | High |
| TC-017 | PF3 exit | Press PF3 | XCTL to COSGN00C sign-on screen | High |
| TC-018 | Invalid PF key | Press PF2, PF5, PF7, CLEAR, etc. | "Invalid key pressed. Please see below..." | Medium |
| TC-019 | Direct invocation (no COMMAREA) | Invoke CM00 transaction directly (no prior sign-on) | Redirect to COSGN00C sign-on screen | High |
| TC-020 | Menu display completeness | Sign in as regular user | All 11 options displayed with correct numbering and names | High |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| USRSEC | Pre-loaded with test user records (at least one regular user, one admin user) | VSAM REPRO from test data (`app/data/`) |
| CSD | CM00 transaction and COMEN01C program defined and enabled | CEDA DEFINE or CSD batch utility |
| Target programs | All 11 target programs installed and enabled in CICS region | CEDA DEFINE or CSD batch utility |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | No explicit HANDLE ABEND — unexpected abends use default CICS handling | User sees CICS ABEND screen rather than a friendly error | Add HANDLE ABEND with a user-friendly error screen |
| KI-002 | RECEIVE MAP error (MAPFAIL) is not explicitly handled | If MAPFAIL occurs, unpredictable behavior | Add RESP check after RECEIVE MAP |
| KI-003 | COMEN02Y menu option table is hardcoded — adding/removing menu options requires a recompile | Inflexible menu management | Externalize menu definitions to a VSAM control file or DB2 table |
| KI-004 | The COPAUS0C special-case INQUIRE check is only for that specific program — other programs that might not be installed are not checked | XCTL to a non-existent program causes CICS PGMIDERR abend | Generalize the INQUIRE check to all target programs |
| KI-005 | All current menu options have USRTYPE = 'U' in COMEN02Y — the admin-only restriction logic exists but no options are currently flagged as admin-only | The BR-002 authorization check never triggers in practice | Define admin-only options with USRTYPE = 'A' when appropriate |
| KI-006 | Option 12 slot exists in the BMS map (OPTN012) but CDEMO-MENU-OPT-COUNT is 11 — the 12th slot is always blank | Minor screen real estate waste; inconsistency between map and data | Align BMS map slots with actual menu option count, or reserve for future use |
| KI-007 | The RETURN-TO-SIGNON-SCREEN paragraph checks CDEMO-TO-PROGRAM and defaults to 'COSGN00C', but the PF3 handler already sets it — the double-check is redundant | No functional impact; minor code clarity issue | Simplify exit logic |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Screen I/O | BMS Map COMEN1A / 3270 terminal | Web-based navigation (sidebar, top nav, SPA routing) | Medium — menu concept maps to navigation component |
| Menu Configuration | Hardcoded COMEN02Y copybook (WORKING-STORAGE) | Database-driven or config-file-driven menu definitions | Low — simple table structure |
| Role-Based Access | Single-character USRTYPE flag ('A'/'U') checked against menu option table | Framework-level RBAC (Spring Security roles, JWT claims, etc.) | Medium — need to define granular permissions |
| Program Routing | CICS XCTL with COMMAREA | URL routing / SPA navigation / REST API dispatch | Medium — XCTL → HTTP redirect or client-side routing |
| State Management | COMMAREA pseudo-conversational (FROM-TRANID, FROM-PROGRAM, PGM-CONTEXT) | HTTP session, JWT token, or client-side state (Redux, etc.) | Low — menu state is minimal |
| Program Availability Check | CICS INQUIRE PROGRAM for COPAUS0C | Feature flags, health checks, or service registry | Low — replaces one CICS-specific call |
| Header/Footer | BMS map literal fields + COBOL FUNCTION CURRENT-DATE | HTML template with dynamic date/time (JavaScript) | Low — direct 1:1 mapping |
| Menu Option Text | STRING concatenation in BUILD-MENU-OPTIONS loop | Template rendering (React JSX, Thymeleaf, etc.) | Low — simple iteration |
| Error Messages | WS-MESSAGE → ERRMSGO field on BMS map | Toast notifications, inline alerts, or form validation messages | Low — direct message mapping |

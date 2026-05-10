# User List (CU00) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CU00 |
| Entry Program | COUSR00C |
| Business Domain | User Administration |
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

- **What**: Displays a paginated list of all users registered in the CardDemo security file (USRSEC). The operator can browse the list, search by User ID, and select a user for update or deletion.
- **Who**: Admin users only — the User List screen is accessible from the Admin Menu (COADM01C). Regular users do not have access to user management functions.
- **When**: Real-time, on-demand during business hours when an administrator needs to view, update, or delete user accounts.
- **Why**: Provides the central navigation point for user administration. Administrators must be able to browse the complete user roster, locate specific users by ID, and initiate update or delete actions.
- **What** is the business outcome: The administrator sees a paginated table of users (10 per page) showing User ID, First Name, Last Name, and User Type. The administrator can then select a user for update ('U') or delete ('D') operations, which transfer control to the respective programs (COUSR02C or COUSR03C).

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  Admin selects User List from Admin Menu (COADM01C)
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ System Action            │  XCTL to COUSR00C; display first page of users
  └────────────┬─────────────┘  from USRSEC file (10 users per page)
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 1            │  Browse the list:
  └────────────┬─────────────┘  - PF7 = Page backward
               │                - PF8 = Page forward
               │                - Enter a User ID in Search field to jump
               ▼
  ┌──────────────────────────┐
  │ User Action 2            │  Select a user by typing a selection code
  └────────────┬─────────────┘  next to their row:
               │                - 'U' = Update user
           ┌───┴───┐            - 'D' = Delete user
           │Decision│
           └───┬───┘
              ╱ ╲
        'U' ╱     ╲ 'D'
           ▼       ▼
  ┌──────────┐  ┌──────────┐
  │ XCTL to  │  │ XCTL to  │
  │ COUSR02C │  │ COUSR03C │
  │ (Update) │  │ (Delete) │
  └──────────┘  └──────────┘
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | Page displays 10 users at a time | USRSEC file has more than 10 records | Paginate with forward/backward navigation |
| BR-002 | Search by User ID | User enters a value in the Search User ID field | STARTBR positions to that User ID (or next available) and loads page |
| BR-003 | Selection code 'U' triggers update | User types 'U' next to a row and presses ENTER | XCTL to COUSR02C (User Update) with selected user in COMMAREA |
| BR-004 | Selection code 'D' triggers delete | User types 'D' next to a row and presses ENTER | XCTL to COUSR03C (User Delete) with selected user in COMMAREA |
| BR-005 | Invalid selection code | User types any character other than 'U' or 'D' | Error: "Invalid selection. Valid values are U and D" |
| BR-006 | Page backward at top | User presses PF7 when already on page 1 | Message: "You are already at the top of the page..." |
| BR-007 | Page forward at bottom | User presses PF8 when no more records exist | Message: "You are already at the bottom of the page..." |
| BR-008 | PF3 returns to Admin Menu | User presses PF3 | XCTL to COADM01C (Admin Menu) |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Select User List from Admin Menu | XCTL to COUSR00C; display first page of users (COUSR0A) | Navigate to user administration |
| 2 | View list of users | 10 users displayed per page with ID, First Name, Last Name, Type | Browse user roster |
| 3a | Press PF8 (Forward) | Load next 10 users from USRSEC file | Page forward through user list |
| 3b | Press PF7 (Backward) | Load previous 10 users from USRSEC file | Page backward through user list |
| 3c | Enter User ID in search field, press ENTER | STARTBR positions to that ID, loads page from that point | Search/jump to specific user |
| 4a | Type 'U' next to a user row, press ENTER | XCTL to COUSR02C with selected User ID in COMMAREA | Initiate user update |
| 4b | Type 'D' next to a user row, press ENTER | XCTL to COUSR03C with selected User ID in COMMAREA | Initiate user delete |
| Alt-A | Press PF3 | XCTL to COADM01C (Admin Menu) | Return to Admin Menu |
| Alt-B | Press invalid key | Display "Invalid key pressed. Please see below..." | Invalid key notification |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CU00
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COUSR00C     │ (Entry & only online program)      │
  │  │ (User List)  │                                    │
  │  └──┬────┬──┬───┘                                    │
  │     │    │  │                                         │
  │     ▼    │  ▼                                         │
  │  ┌──────┐│ ┌────────────────────────────────────┐    │
  │  │BMS   ││ │ VSAM Files                         │    │
  │  │MAP:  ││ │  USRSEC (User Security master)     │    │
  │  │COUSR ││ └────────────────────────────────────┘    │
  │  │0A    ││                                           │
  │  └──────┘│                                           │
  │          │                                            │
  │          ├──▶ XCTL → COUSR02C (User Update)          │
  │          ├──▶ XCTL → COUSR03C (User Delete)          │
  │          └──▶ XCTL → COADM01C (Admin Menu / PF3)    │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1 (First entry — from Admin Menu via XCTL):
  1. COADM01C XCTLs to COUSR00C with CARDDEMO-COMMAREA
  2. EIBCALEN > 0; CDEMO-PGM-REENTER = 0 (first entry)
  3. SET CDEMO-PGM-REENTER TO TRUE
  4. MOVE LOW-VALUES TO COUSR0AO (clear output map)
  5. PERFORM PROCESS-ENTER-KEY
     └── No selection detected → USRIDINI = spaces → SEC-USR-ID = LOW-VALUES
     └── CDEMO-CU00-PAGE-NUM = 0
     └── PERFORM PROCESS-PAGE-FORWARD
         a. STARTBR USRSEC (RIDFLD = SEC-USR-ID = LOW-VALUES → position to start)
         b. Initialize all 10 user data slots to SPACES
         c. READNEXT 10 records, populate map fields (USRID01-10, FNAME01-10, etc.)
         d. Track CDEMO-CU00-USRID-FIRST (row 1) and CDEMO-CU00-USRID-LAST (row 10)
         e. Peek-ahead: READNEXT one more to check if next page exists
         f. ENDBR USRSEC
         g. Set page number, SEND MAP COUSR0A with ERASE
  6. RETURN TRANSID('CU00') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 2+ (User presses ENTER — selection or search):
  7.  CICS dispatches → COUSR00C (COMMAREA restored)
  8.  EIBCALEN > 0; CDEMO-PGM-REENTER = 1
  9.  RECEIVE MAP('COUSR0A') INTO(COUSR0AI)
  10. EIBAID = DFHENTER → PERFORM PROCESS-ENTER-KEY
      a. Check SEL0001I–SEL0010I for selection code
      b. If selection found:
         ├── 'U'/'u' → XCTL to COUSR02C (User Update)
         └── 'D'/'d' → XCTL to COUSR03C (User Delete)
         └── Other → Error "Invalid selection. Valid values are U and D"
      c. If no selection: use USRIDINI as search key
      d. CDEMO-CU00-PAGE-NUM = 0
      e. PERFORM PROCESS-PAGE-FORWARD (same as step 5)
  11. RETURN TRANSID('CU00') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 2+ (User presses PF7 — page backward):
  12. RECEIVE MAP → EIBAID = DFHPF7
  13. PERFORM PROCESS-PF7-KEY
      a. Set SEC-USR-ID = CDEMO-CU00-USRID-FIRST (first ID on current page)
      b. If CDEMO-CU00-PAGE-NUM > 1:
         └── PERFORM PROCESS-PAGE-BACKWARD
             i.   STARTBR USRSEC at CDEMO-CU00-USRID-FIRST
             ii.  READPREV to skip current position
             iii. Initialize all 10 user data slots
             iv.  READPREV 10 records (fills slots 10→1)
             v.   Peek-ahead: READPREV to check if previous page exists
             vi.  Adjust page number
             vii. ENDBR, SEND MAP
      c. Else: "You are already at the top of the page..."
  14. RETURN TRANSID('CU00') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 2+ (User presses PF8 — page forward):
  15. RECEIVE MAP → EIBAID = DFHPF8
  16. PERFORM PROCESS-PF8-KEY
      a. Set SEC-USR-ID = CDEMO-CU00-USRID-LAST (last ID on current page)
      b. If NEXT-PAGE-YES:
         └── PERFORM PROCESS-PAGE-FORWARD (from last ID)
      c. Else: "You are already at the bottom of the page..."
  17. RETURN TRANSID('CU00') COMMAREA(CARDDEMO-COMMAREA)
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | >0 | XCTL from Admin Menu | Initial logic (PGM-ENTER) | COUSR0A (first page of users) | CU00 |
| 2 | >0 | Press ENTER (search/select) | RECEIVE → PROCESS-ENTER-KEY | COUSR0A (refreshed list or error) | CU00 |
| 3 | >0 | Press PF8 (forward) | RECEIVE → PROCESS-PF8-KEY | COUSR0A (next page) | CU00 |
| 4 | >0 | Press PF7 (backward) | RECEIVE → PROCESS-PF7-KEY | COUSR0A (previous page) | CU00 |
| N | >0 | Select 'U' + ENTER | PROCESS-ENTER-KEY → XCTL COUSR02C | — | — (XCTL) |
| N | >0 | Select 'D' + ENTER | PROCESS-ENTER-KEY → XCTL COUSR03C | — | — (XCTL) |
| N | >0 | PF3 (Exit) | XCTL to COADM01C (Admin Menu) | — | — (XCTL) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | RECEIVE MAP('COUSR0A') MAPSET('COUSR00') INTO(COUSR0AI) | Capture user input from terminal | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 2 | STARTBR DATASET('USRSEC') RIDFLD(SEC-USR-ID) KEYLENGTH(8) | Position browse cursor in user file | NORMAL → continue; NOTFND → EOF, "You are at the top of the page..."; OTHER → error |
| 3 | READNEXT DATASET('USRSEC') INTO(SEC-USER-DATA) RIDFLD(SEC-USR-ID) | Read next user record sequentially | NORMAL → continue; ENDFILE → "You have reached the bottom of the page..."; OTHER → error |
| 4 | READPREV DATASET('USRSEC') INTO(SEC-USER-DATA) RIDFLD(SEC-USR-ID) | Read previous user record (backward paging) | NORMAL → continue; ENDFILE → "You have reached the top of the page..."; OTHER → error |
| 5 | ENDBR DATASET('USRSEC') | End browse session | — |
| 6 | SEND MAP('COUSR0A') MAPSET('COUSR00') FROM(COUSR0AO) ERASE CURSOR | Send screen to terminal (with erase) | — |
| 7 | SEND MAP('COUSR0A') MAPSET('COUSR00') FROM(COUSR0AO) CURSOR | Send screen to terminal (without erase — for messages) | — |
| 8 | RETURN TRANSID('CU00') COMMAREA(CARDDEMO-COMMAREA) | Pseudo-conversational return | — |
| 9 | XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA) | Transfer to COUSR02C, COUSR03C, or COADM01C | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────┐                ┌─────────────────────────────────────┐
  │ Admin Menu  │   User List    │  COUSR0A (User List)                │
  │ (COADM01C)  │───────────────▶│                                     │
  └─────────────┘                │  Search User ID: ________           │
        ▲                        │  ────────────────────────────────── │
        │ PF3                    │  Sel  User ID   First Name          │
        │                        │  ---  --------  --------------------│
        │                        │   _   ADMIN001  FIRST01   LAST01  A │
        │                        │   _   USER0001  FIRST02   LAST02  U │
        │                        │   _   USER0002  FIRST03   LAST03  U │
        │                        │       ... (up to 10 rows)           │
        │                        │                                     │
        │                        │  Type 'U' to Update or 'D' to      │
        │                        │  Delete a User from the list        │
        │                        │  [Error/Info message line]          │
        │                        │  ENTER=Continue F3=Back             │
        │                        │  F7=Backward F8=Forward             │
        └────────────────────────┤                                     │
                                 └──────────┬──────────┬───────────────┘
                                            │          │
                                   Sel='U'  │          │ Sel='D'
                                            ▼          ▼
                                 ┌──────────┐  ┌──────────┐
                                 │ COUSR02C │  │ COUSR03C │
                                 │ (Update) │  │ (Delete) │
                                 └──────────┘  └──────────┘
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| COUSR0A | COUSR00 | User List — paginated display of USRSEC records with selection | Search User ID, Sel×10, UserID×10, FName×10, LName×10, UType×10 | ENTER=Search/Select, PF3=Back, PF7=Backward, PF8=Forward |

### 3.3 Screen Field Details

#### Map: COUSR0A

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction name (CU00) |
| TITLE01 | ASKIP,FSET,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,FSET,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | ASKIP,FSET,NORM | 8 | Alpha | — | Program name (COUSR00C) |
| TITLE02 | ASKIP,FSET,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | ASKIP,FSET,NORM | 8 | Time | — | Current time hh:mm:ss |
| PAGENUM | ASKIP,FSET,NORM | 8 | Numeric | — | Current page number |
| USRIDIN | FSET,NORM,UNPROT | 8 | Alpha | Used as STARTBR key for search | Search User ID input field |
| SEL0001 | FSET,NORM,UNPROT | 1 | Alpha | 'U'/'D' only | Selection code for row 1 |
| USRID01 | ASKIP,FSET,NORM | 8 | Alpha | — | User ID for row 1 |
| FNAME01 | ASKIP,FSET,NORM | 20 | Alpha | — | First Name for row 1 |
| LNAME01 | ASKIP,FSET,NORM | 20 | Alpha | — | Last Name for row 1 |
| UTYPE01 | ASKIP,FSET,NORM | 1 | Alpha | — | User Type for row 1 ('A'=Admin, 'U'=User) |
| SEL0002 | FSET,NORM,UNPROT | 1 | Alpha | 'U'/'D' only | Selection code for row 2 |
| USRID02 | ASKIP,FSET,NORM | 8 | Alpha | — | User ID for row 2 |
| FNAME02 | ASKIP,FSET,NORM | 20 | Alpha | — | First Name for row 2 |
| LNAME02 | ASKIP,FSET,NORM | 20 | Alpha | — | Last Name for row 2 |
| UTYPE02 | ASKIP,FSET,NORM | 1 | Alpha | — | User Type for row 2 |
| SEL0003 | FSET,NORM,UNPROT | 1 | Alpha | 'U'/'D' only | Selection code for row 3 |
| USRID03 | ASKIP,FSET,NORM | 8 | Alpha | — | User ID for row 3 |
| FNAME03 | ASKIP,FSET,NORM | 20 | Alpha | — | First Name for row 3 |
| LNAME03 | ASKIP,FSET,NORM | 20 | Alpha | — | Last Name for row 3 |
| UTYPE03 | ASKIP,FSET,NORM | 1 | Alpha | — | User Type for row 3 |
| SEL0004 | FSET,NORM,UNPROT | 1 | Alpha | 'U'/'D' only | Selection code for row 4 |
| USRID04 | ASKIP,FSET,NORM | 8 | Alpha | — | User ID for row 4 |
| FNAME04 | ASKIP,FSET,NORM | 20 | Alpha | — | First Name for row 4 |
| LNAME04 | ASKIP,FSET,NORM | 20 | Alpha | — | Last Name for row 4 |
| UTYPE04 | ASKIP,FSET,NORM | 1 | Alpha | — | User Type for row 4 |
| SEL0005 | FSET,NORM,UNPROT | 1 | Alpha | 'U'/'D' only | Selection code for row 5 |
| USRID05 | ASKIP,FSET,NORM | 8 | Alpha | — | User ID for row 5 |
| FNAME05 | ASKIP,FSET,NORM | 20 | Alpha | — | First Name for row 5 |
| LNAME05 | ASKIP,FSET,NORM | 20 | Alpha | — | Last Name for row 5 |
| UTYPE05 | ASKIP,FSET,NORM | 1 | Alpha | — | User Type for row 5 |
| SEL0006 | FSET,NORM,UNPROT | 1 | Alpha | 'U'/'D' only | Selection code for row 6 |
| USRID06 | ASKIP,FSET,NORM | 8 | Alpha | — | User ID for row 6 |
| FNAME06 | ASKIP,FSET,NORM | 20 | Alpha | — | First Name for row 6 |
| LNAME06 | ASKIP,FSET,NORM | 20 | Alpha | — | Last Name for row 6 |
| UTYPE06 | ASKIP,FSET,NORM | 1 | Alpha | — | User Type for row 6 |
| SEL0007 | FSET,NORM,UNPROT | 1 | Alpha | 'U'/'D' only | Selection code for row 7 |
| USRID07 | ASKIP,FSET,NORM | 8 | Alpha | — | User ID for row 7 |
| FNAME07 | ASKIP,FSET,NORM | 20 | Alpha | — | First Name for row 7 |
| LNAME07 | ASKIP,FSET,NORM | 20 | Alpha | — | Last Name for row 7 |
| UTYPE07 | ASKIP,FSET,NORM | 1 | Alpha | — | User Type for row 7 |
| SEL0008 | FSET,NORM,UNPROT | 1 | Alpha | 'U'/'D' only | Selection code for row 8 |
| USRID08 | ASKIP,FSET,NORM | 8 | Alpha | — | User ID for row 8 |
| FNAME08 | ASKIP,FSET,NORM | 20 | Alpha | — | First Name for row 8 |
| LNAME08 | ASKIP,FSET,NORM | 20 | Alpha | — | Last Name for row 8 |
| UTYPE08 | ASKIP,FSET,NORM | 1 | Alpha | — | User Type for row 8 |
| SEL0009 | FSET,NORM,UNPROT | 1 | Alpha | 'U'/'D' only | Selection code for row 9 |
| USRID09 | ASKIP,FSET,NORM | 8 | Alpha | — | User ID for row 9 |
| FNAME09 | ASKIP,FSET,NORM | 20 | Alpha | — | First Name for row 9 |
| LNAME09 | ASKIP,FSET,NORM | 20 | Alpha | — | Last Name for row 9 |
| UTYPE09 | ASKIP,FSET,NORM | 1 | Alpha | — | User Type for row 9 |
| SEL0010 | FSET,NORM,UNPROT | 1 | Alpha | 'U'/'D' only | Selection code for row 10 |
| USRID10 | ASKIP,FSET,NORM | 8 | Alpha | — | User ID for row 10 |
| FNAME10 | ASKIP,FSET,NORM | 20 | Alpha | — | First Name for row 10 |
| LNAME10 | ASKIP,FSET,NORM | 20 | Alpha | — | Last Name for row 10 |
| UTYPE10 | ASKIP,FSET,NORM | 1 | Alpha | — | User Type for row 10 |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/info message display |

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | COUSR0A | Check for selection code (U/D) on any row; if found, XCTL to update/delete program. Otherwise, use Search User ID to refresh list. |
| PF3 | COUSR0A | Return to Admin Menu (COADM01C) via XCTL |
| PF7 | COUSR0A | Page backward — STARTBR at CDEMO-CU00-USRID-FIRST, READPREV 10 records |
| PF8 | COUSR0A | Page forward — STARTBR at CDEMO-CU00-USRID-LAST, READNEXT 10 records |
| OTHER | COUSR0A | Display "Invalid key pressed. Please see below..." |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COUSR00C | User List — screen handler, browse USRSEC, selection routing | TRANSID CU00 / XCTL from COADM01C | 695 | `app/cbl/COUSR00C.cbl` |
| COADM01C | Admin Menu — entry point for user management | XCTL (caller) | — | `app/cbl/COADM01C.cbl` |
| COUSR02C | User Update — update selected user record | XCTL (target on 'U' selection) | — | `app/cbl/COUSR02C.cbl` |
| COUSR03C | User Delete — delete selected user record | XCTL (target on 'D' selection) | — | `app/cbl/COUSR03C.cbl` |
| COSGN00C | Sign-on Screen — fallback if no COMMAREA | XCTL (fallback) | — | `app/cbl/COSGN00C.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CU00
└── COUSR00C (Entry — Screen Handler, Browse, Selection Router)
    ├── STARTBR FILE('USRSEC')  — Position browse at search key or start
    ├── READNEXT FILE('USRSEC') — Read forward through user records
    ├── READPREV FILE('USRSEC') — Read backward through user records
    ├── ENDBR FILE('USRSEC')    — Close browse session
    ├── SEND MAP('COUSR0A')     — Display user list screen
    ├── RECEIVE MAP('COUSR0A')  — Capture user input
    ├── RETURN TRANSID('CU00')  — Pseudo-conversational return
    ├── XCTL → COUSR02C         — (on 'U' selection — User Update)
    ├── XCTL → COUSR03C         — (on 'D' selection — User Delete)
    ├── XCTL → COADM01C         — (on PF3 — Admin Menu)
    └── XCTL → COSGN00C         — (if EIBCALEN=0 — no valid session)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COADM01C | COUSR00C | XCTL | CARDDEMO-COMMAREA | — | Admin selects User List from Admin Menu |
| COUSR00C | COUSR02C | XCTL | CARDDEMO-COMMAREA (CDEMO-CU00-USR-SELECTED = User ID) | — | User types 'U' next to a row |
| COUSR00C | COUSR03C | XCTL | CARDDEMO-COMMAREA (CDEMO-CU00-USR-SELECTED = User ID) | — | User types 'D' next to a row |
| COUSR00C | COADM01C | XCTL | CARDDEMO-COMMAREA | — | PF3 exit to Admin Menu |
| COUSR00C | COSGN00C | XCTL | CARDDEMO-COMMAREA | — | EIBCALEN=0 (no session / direct invocation) |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| User Security Master | USRSEC | VSAM KSDS | BROWSE (STARTBR / READNEXT / READPREV / ENDBR) | COUSR00C | Browse user records for list display |

### 5.2 File Access Details

#### File: USRSEC (User Security Master)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CSUSR01Y (SEC-USER-DATA, 80 bytes) |
| Key | SEC-USR-ID (PIC X(08), offset 0) |
| Access Mode | BROWSE (STARTBR, READNEXT, READPREV, ENDBR) |
| Locking | No update, read-only browse |
| Shared With | COSGN00C (Sign-on), COUSR01C (User Add), COUSR02C (User Update), COUSR03C (User Delete) |

**Record Layout (CSUSR01Y):**

| Field | PIC | Offset | Length | Description |
|-------|-----|--------|--------|-------------|
| SEC-USR-ID | X(08) | 0 | 8 | User ID (primary key) |
| SEC-USR-FNAME | X(20) | 8 | 20 | First Name |
| SEC-USR-LNAME | X(20) | 28 | 20 | Last Name |
| SEC-USR-PWD | X(08) | 48 | 8 | Password |
| SEC-USR-TYPE | X(01) | 56 | 1 | User Type ('A'=Admin, 'U'=User) |
| SEC-USR-FILLER | X(23) | 57 | 23 | Reserved/Filler |

**Access Pattern — Forward Paging:**

| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COUSR00C | STARTBR DATASET('USRSEC') RIDFLD(SEC-USR-ID) KEYLENGTH(8) | SEC-USR-ID (from search field or last ID on page) | Positions browse cursor |
| 2 | COUSR00C | READNEXT INTO(SEC-USER-DATA) RIDFLD(SEC-USR-ID) | Sequential from current position | SEC-USR-ID, SEC-USR-FNAME, SEC-USR-LNAME, SEC-USR-TYPE |
| 3 | COUSR00C | READNEXT (peek-ahead) | Check if 11th record exists | Sets NEXT-PAGE-YES/NO flag |
| 4 | COUSR00C | ENDBR DATASET('USRSEC') | — | Close browse |

**Access Pattern — Backward Paging:**

| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COUSR00C | STARTBR DATASET('USRSEC') RIDFLD(SEC-USR-ID) KEYLENGTH(8) | SEC-USR-ID = CDEMO-CU00-USRID-FIRST | Positions browse cursor at first ID on current page |
| 2 | COUSR00C | READPREV INTO(SEC-USER-DATA) RIDFLD(SEC-USR-ID) | Skip current position | — |
| 3 | COUSR00C | READPREV (×10) INTO(SEC-USER-DATA) | Read 10 records backward | SEC-USR-ID, SEC-USR-FNAME, SEC-USR-LNAME, SEC-USR-TYPE |
| 4 | COUSR00C | READPREV (peek-ahead) | Check if previous page exists | Adjusts page number |
| 5 | COUSR00C | ENDBR DATASET('USRSEC') | — | Close browse |

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
  USRSEC VSAM File (Browse)
       │
       ├── STARTBR (position by SEC-USR-ID key)
       │
       ├── READNEXT/READPREV → SEC-USER-DATA record
       │       │
       │       ├── SEC-USR-ID    ──▶ USRIDnnI of COUSR0AI (rows 01–10)
       │       │                      └──▶ CDEMO-CU00-USRID-FIRST (row 1)
       │       │                      └──▶ CDEMO-CU00-USRID-LAST  (row 10)
       │       ├── SEC-USR-FNAME ──▶ FNAMEnnI of COUSR0AI (rows 01–10)
       │       ├── SEC-USR-LNAME ──▶ LNAMEnnI of COUSR0AI (rows 01–10)
       │       └── SEC-USR-TYPE  ──▶ UTYPEnnI of COUSR0AI (rows 01–10)
       │
       └── ENDBR
  
  Terminal Input (COUSR0A)
       │
       ├── USRIDINI (Search User ID)
       │       │
       │       └──▶ SEC-USR-ID (STARTBR key for next browse)
       │
       ├── SEL0001I–SEL0010I (Selection codes)
       │       │
       │       └──▶ CDEMO-CU00-USR-SEL-FLG (selection flag)
       │
       └── USRIDnnI (User ID on selected row)
               │
               └──▶ CDEMO-CU00-USR-SELECTED (passed to COUSR02C/COUSR03C)
  
  Header Population:
       FUNCTION CURRENT-DATE ──▶ WS-CURDATE-DATA ──▶ CURDATEO, CURTIMEO
       CCDA-TITLE01           ──▶ TITLE01O
       CCDA-TITLE02           ──▶ TITLE02O
       WS-TRANID              ──▶ TRNNAMEO
       WS-PGMNAME             ──▶ PGMNAMEO
       CDEMO-CU00-PAGE-NUM    ──▶ PAGENUMI
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| SEC-USR-ID | READNEXT/READPREV (USRSEC) | Direct move | USRIDnnI | COUSR0AI map field (rows 01–10) |
| SEC-USR-FNAME | READNEXT/READPREV (USRSEC) | Direct move | FNAMEnnI | COUSR0AI map field (rows 01–10) |
| SEC-USR-LNAME | READNEXT/READPREV (USRSEC) | Direct move | LNAMEnnI | COUSR0AI map field (rows 01–10) |
| SEC-USR-TYPE | READNEXT/READPREV (USRSEC) | Direct move | UTYPEnnI | COUSR0AI map field (rows 01–10) |
| SEC-USR-ID (row 1) | READNEXT/READPREV | Direct move | CDEMO-CU00-USRID-FIRST | COMMAREA (pagination anchor) |
| SEC-USR-ID (row 10) | READNEXT/READPREV | Direct move | CDEMO-CU00-USRID-LAST | COMMAREA (pagination anchor) |
| USRIDINI | Terminal (COUSR0AI) | Direct move (or LOW-VALUES if blank) | SEC-USR-ID | STARTBR key for search |
| SELnnnnI | Terminal (COUSR0AI) | Direct move | CDEMO-CU00-USR-SEL-FLG | COMMAREA (selection flag) |
| USRIDnnI (selected row) | Terminal (COUSR0AI) | Direct move | CDEMO-CU00-USR-SELECTED | COMMAREA (selected user ID for XCTL) |
| CDEMO-CU00-PAGE-NUM | COMMAREA | Direct move | PAGENUMI | COUSR0AI map field |
| FUNCTION CURRENT-DATE | COBOL intrinsic | MM/DD/YY formatting | CURDATEO | COUSR0AO map field |
| FUNCTION CURRENT-DATE | COBOL intrinsic | HH:MM:SS formatting | CURTIMEO | COUSR0AO map field |
| CCDA-TITLE01 | COTTL01Y copybook | Direct move | TITLE01O | COUSR0AO map field |
| CCDA-TITLE02 | COTTL01Y copybook | Direct move | TITLE02O | COUSR0AO map field |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`) plus a program-specific extension `CDEMO-CU00-INFO`:

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
| CDEMO-ACCT-STATUS | X(01) | 1 | IN | Account status |
| CDEMO-CARD-NUM | 9(16) | 16 | IN | Card Number |
| CDEMO-LAST-MAP | X(7) | 7 | IN/OUT | Last BMS map displayed |
| CDEMO-LAST-MAPSET | X(7) | 7 | IN/OUT | Last BMS mapset used |

**CDEMO-CU00-INFO (program-specific extension, defined inline in COUSR00C):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-CU00-USRID-FIRST | X(08) | 8 | IN/OUT | First User ID on current page (pagination anchor for backward) |
| CDEMO-CU00-USRID-LAST | X(08) | 8 | IN/OUT | Last User ID on current page (pagination anchor for forward) |
| CDEMO-CU00-PAGE-NUM | 9(08) | 8 | IN/OUT | Current page number |
| CDEMO-CU00-NEXT-PAGE-FLG | X(01) | 1 | IN/OUT | 'Y' = more pages forward, 'N' = at end |
| CDEMO-CU00-USR-SEL-FLG | X(01) | 1 | OUT | Selection code entered by user ('U' or 'D') |
| CDEMO-CU00-USR-SELECTED | X(08) | 8 | OUT | User ID of the selected row (passed to COUSR02C/COUSR03C) |

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. Errors are handled inline via screen messages (WS-MESSAGE → ERRMSGO). The CDEMO-CU00-USR-SEL-FLG and CDEMO-CU00-USR-SELECTED fields communicate the user's selection to the target programs (COUSR02C or COUSR03C).

### 7.3 Conversation State Management

- **COMMAREA preservation**: The entire CARDDEMO-COMMAREA (including CU00-specific fields) is passed on every RETURN TRANSID('CU00'). CICS restores it automatically on the next dispatch.
- **Pagination state**: CDEMO-CU00-USRID-FIRST and CDEMO-CU00-USRID-LAST track the boundaries of the current page. CDEMO-CU00-PAGE-NUM tracks the page number. CDEMO-CU00-NEXT-PAGE-FLG indicates whether forward paging is possible.
- **Screen field state**: BMS FSET attribute ensures all fields are returned on every RECEIVE, preserving user-entered data across iterations.
- **No TSQ overflow**: All state fits within the COMMAREA; no temporary storage queues are used.
- **PGM-CONTEXT flag**: Distinguishes first entry (0 — display initial list) from subsequent interactions (1 — process input).

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path:
    First Entry → STARTBR USRSEC → READNEXT ×10 → Populate Map → SEND MAP
    
  Pagination:
    PF8 → STARTBR at USRID-LAST → READNEXT ×10 → SEND MAP
    PF7 → STARTBR at USRID-FIRST → READPREV ×10 → SEND MAP

  Selection Path:
    ENTER + SEL='U' ──▶ XCTL COUSR02C
    ENTER + SEL='D' ──▶ XCTL COUSR03C
    ENTER + SEL=other ──▶ "Invalid selection. Valid values are U and D"

  Browse Error:
    STARTBR ──NOTFND──▶ "You are at the top of the page..."
                        → EOF flag set, display message
    STARTBR ──OTHER──▶  "Unable to lookup User..."
                        → ERR-FLG set, display message

  Read Error:
    READNEXT ──ENDFILE──▶ "You have reached the bottom of the page..."
    READPREV ──ENDFILE──▶ "You have reached the top of the page..."
    READNEXT/READPREV ──OTHER──▶ "Unable to lookup User..."

  Boundary:
    PF7 at page 1 ──▶ "You are already at the top of the page..."
    PF8 at last page ──▶ "You are already at the bottom of the page..."

  Invalid Key:
    Other AID key ──▶ "Invalid key pressed. Please see below..."
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| STARTBR — record not found | NOTFND (13) | Set USER-SEC-EOF, display message | "You are at the top of the page..." | — |
| STARTBR — unexpected error | OTHER | Set ERR-FLG, display message | "Unable to lookup User..." | DISPLAY RESP/REAS to SYSOUT |
| READNEXT — end of file | ENDFILE (20) | Set USER-SEC-EOF, display message | "You have reached the bottom of the page..." | — |
| READNEXT — unexpected error | OTHER | Set ERR-FLG, display message | "Unable to lookup User..." | DISPLAY RESP/REAS to SYSOUT |
| READPREV — end of file | ENDFILE (20) | Set USER-SEC-EOF, display message | "You have reached the top of the page..." | — |
| READPREV — unexpected error | OTHER | Set ERR-FLG, display message | "Unable to lookup User..." | DISPLAY RESP/REAS to SYSOUT |
| Invalid selection code | — (application logic) | Set ERR-FLG, cursor to USRIDIN | "Invalid selection. Valid values are U and D" | — |
| PF7 at page 1 | — (application logic) | Display message, no erase | "You are already at the top of the page..." | — |
| PF8 at last page | — (application logic) | Display message, no erase | "You are already at the bottom of the page..." | — |
| Invalid AID key | — (application logic) | Set ERR-FLG, cursor to USRIDIN | "Invalid key pressed. Please see below..." | — |
| EIBCALEN = 0 (no COMMAREA) | — | XCTL to COSGN00C (sign-on) | — | — |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| — | No explicit HANDLE ABEND | Default CICS abend handling applies |

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "Invalid key pressed. Please see below..." | Unrecognized AID key (not ENTER, PF3, PF7, PF8) | ERRMSG (line 23, col 1) |
| — | "Invalid selection. Valid values are U and D" | Selection code is not 'U', 'u', 'D', or 'd' | ERRMSG (line 23, col 1) |
| — | "You are at the top of the page..." | STARTBR returns NOTFND (search key beyond end of file) | ERRMSG (line 23, col 1) |
| — | "You are already at the top of the page..." | PF7 pressed when CDEMO-CU00-PAGE-NUM ≤ 1 | ERRMSG (line 23, col 1) |
| — | "You are already at the bottom of the page..." | PF8 pressed when NEXT-PAGE-NO | ERRMSG (line 23, col 1) |
| — | "You have reached the bottom of the page..." | READNEXT returns ENDFILE | ERRMSG (line 23, col 1) |
| — | "You have reached the top of the page..." | READPREV returns ENDFILE | ERRMSG (line 23, col 1) |
| — | "Unable to lookup User..." | STARTBR, READNEXT, or READPREV returns unexpected RESP code | ERRMSG (line 23, col 1) |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CU00 transaction must be authorized for user |
| Resource-level security | RACF FACILITY / File profiles for USRSEC |
| Application-level check | If EIBCALEN = 0 (no COMMAREA — direct invocation without login), XCTL to sign-on screen COSGN00C |
| Admin-only access | User List is accessible only from the Admin Menu (COADM01C), which is restricted to admin users (CDEMO-USER-TYPE = 'A') |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| USRSEC file | READ (browse only) | CICS File Control |

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
| CPU per transaction | Low (browse + map I/O only) | — | — |
| File I/O count | 12–13 per page (STARTBR + 10 READNEXT + 1 peek-ahead + ENDBR) | — | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal | — | — |

### 10.2 Performance Considerations

- **STARTBR/READNEXT pattern**: Sequential browse through USRSEC KSDS. Efficient for small-to-medium user sets. For very large user files (thousands of records), deep pagination could involve many I/O operations as the browse repositions.
- **10 records per page**: Fixed page size limits I/O to 10–11 READNEXTs per page load.
- **READPREV for backward paging**: Same efficiency as READNEXT but in reverse direction.
- **Peek-ahead read**: One extra READNEXT after filling 10 slots to determine if a next page exists — minimal overhead.
- **COMMAREA size**: Relatively small; no TSQ overflow needed.
- **BMS SEND/RECEIVE**: Standard map I/O with ERASE — one pair per pseudo-conversational iteration.
- **No CALL to external utilities**: Unlike transaction-oriented screens, this list screen has no external program calls (no date validation, no cross-reference lookups).

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Initial display — first page | Navigate to User List from Admin Menu | First 10 users displayed, page number = 1 | High |
| TC-002 | Page forward | Press PF8 on first page | Next 10 users displayed, page number = 2 | High |
| TC-003 | Page backward | Press PF7 on page 2 | Previous 10 users displayed, page number = 1 | High |
| TC-004 | Page forward at last page | Press PF8 when no more records | "You are already at the bottom of the page..." | High |
| TC-005 | Page backward at first page | Press PF7 when on page 1 | "You are already at the top of the page..." | High |
| TC-006 | Search by existing User ID | Enter valid User ID in search field, press ENTER | List refreshes starting from that User ID | High |
| TC-007 | Search by non-existent User ID | Enter non-existent but valid User ID | List starts from next available User ID (or "at the top..." if beyond all IDs) | Medium |
| TC-008 | Select user for update | Type 'U' next to a user row, press ENTER | XCTL to COUSR02C with selected User ID | High |
| TC-009 | Select user for delete | Type 'D' next to a user row, press ENTER | XCTL to COUSR03C with selected User ID | High |
| TC-010 | Invalid selection code | Type 'X' next to a user row, press ENTER | "Invalid selection. Valid values are U and D" | Medium |
| TC-011 | PF3 exit | Press PF3 at any point | Return to Admin Menu (COADM01C) | Medium |
| TC-012 | Invalid PF key | Press PF2, PF5, PF6, etc. | "Invalid key pressed. Please see below..." | Low |
| TC-013 | Empty search + no selection | Leave all fields blank, press ENTER | List refreshes from beginning (LOW-VALUES key) | Medium |
| TC-014 | Case-insensitive selection | Type 'u' or 'd' (lowercase) next to a row | Same behavior as 'U' or 'D' | Low |
| TC-015 | Direct invocation without login | Invoke CU00 directly without COMMAREA | XCTL to sign-on screen COSGN00C | Medium |
| TC-016 | Partial page at end of file | USRSEC has fewer than 10 remaining records | Partial page displayed correctly, remaining rows blank | Medium |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| USRSEC | Pre-loaded with at least 25+ user records to test multi-page scenarios | VSAM REPRO from test data (`app/data/`) |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | Only the first selection code found (scanning SEL0001–SEL0010 in order) is processed; multiple selections on the same screen are ignored | If user marks two rows, only the first is processed | Single selection per screen submit |
| KI-002 | No explicit HANDLE ABEND — unexpected abends use default CICS handling | User sees CICS ABEND screen rather than a friendly error | Add HANDLE ABEND with a user-friendly error screen |
| KI-003 | The PROCESS-PAGE-BACKWARD populates rows in reverse order (10→1) via READPREV, but POPULATE-USER-DATA maps WS-IDX to row numbers directly — row 10 gets the first READPREV record, row 9 the second, etc. | Backward page shows users in correct ascending order because READPREV returns records in descending key order and they are placed from slot 10 down to 1 | Working as designed but logic is non-obvious |
| KI-004 | The SEND-ERASE-NO flag is used for boundary messages (top/bottom of page) to avoid redrawing the entire screen, but the list data is not refreshed in these cases | User sees stale list data with an informational message overlay | No functional impact — message is informational |
| KI-005 | Validation uses cascading EVALUATE for selection — only the first non-empty selection field is evaluated | User must select only one row per ENTER | Document in user guide |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Screen I/O | BMS Map COUSR0A / 3270 terminal | Web UI data grid with sorting, filtering, pagination | Medium — field mapping is 1:1 but pagination model changes |
| Data Access | VSAM KSDS browse (STARTBR/READNEXT/READPREV/ENDBR) | Service layer with RDBMS (e.g., RDS PostgreSQL) using SQL `SELECT ... LIMIT/OFFSET` or cursor-based pagination | Medium — browse pattern maps to SQL pagination |
| Pagination | COMMAREA-based anchors (first/last User ID, page number, next-page flag) | Server-side cursor or offset-based pagination with REST API query parameters | Medium — state management changes |
| Selection/Navigation | Selection code ('U'/'D') typed next to row, XCTL to target program | Hyperlinks or action buttons per row; REST API calls for update/delete | Low — straightforward UI mapping |
| State Management | COMMAREA pseudo-conversational | HTTP session or stateless REST with tokens | Medium |
| User Search | STARTBR repositioning by key prefix | SQL `WHERE user_id >= ?` or full-text search | Low |
| Security | RACF transaction/resource security + EIBCALEN check | Role-based access control (RBAC) in application layer | Medium |
| Admin-only access | Controlled by menu routing (Admin Menu vs. Regular Menu) | Fine-grained RBAC permissions on API endpoints | Low |

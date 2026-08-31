# User Delete (CU03) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CU03 |
| Entry Program | COUSR03C |
| Business Domain | User Security Administration |
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

- **What**: Allows an administrator to delete an existing user record from the USRSEC security file. The transaction looks up a user by User ID, displays the user's details (first name, last name, user type), and upon explicit confirmation via PF5, permanently removes the record.
- **Who**: System administrators only — this transaction is accessible from the Admin Menu (COADM01C). It may also be invoked from the User List screen (COUSR00C) with a pre-selected user ID.
- **When**: Real-time, on-demand when an administrator needs to remove a user's access from the CardDemo application.
- **Why**: Supports user lifecycle management — removing terminated employees, revoking compromised accounts, or cleaning up test users.
- **What** is the business outcome: The user record is permanently deleted from the USRSEC VSAM file. The user can no longer authenticate to the CardDemo application.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  Admin selects User Delete from Admin Menu
  └────────────┬─────────────┘  or arrives with pre-selected User ID from User List
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 1            │  Enter User ID to identify the target user
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ System Response          │  READ USRSEC file by User ID;
  └────────────┬─────────────┘  display First Name, Last Name, User Type;
               │                prompt "Press PF5 key to delete this user ..."
               ▼
  ┌──────────────────────────┐
  │ User Action 2            │  Press PF5 to confirm deletion
  └────────────┬─────────────┘
               │
           ┌───┴───┐
           │Decision│  PF5 pressed?
           └───┬───┘
              ╱ ╲
        Yes ╱     ╲ No (other key)
           ▼       ▼
  ┌──────────────┐  ┌─────────────────────┐
  │ DELETE record │  │ Other key handling:  │
  │ from USRSEC  │  │ ENTER = re-fetch     │
  └──────┬───────┘  │ PF3 = back           │
         │          │ PF4 = clear           │
         ▼          │ PF12 = back to admin  │
  ┌──────────────────────────┐  └─────────────────────┘
  │ Business Outcome         │  "User <ID> has been deleted ..."
  └──────────────────────────┘   Screen clears for next entry.
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | User ID is required | User ID field is empty (spaces or low-values) | Error: "User ID can NOT be empty..." |
| BR-002 | User must exist | User ID not found in USRSEC file | Error: "User ID NOT found..." |
| BR-003 | Two-step deletion | ENTER fetches and displays user details; PF5 confirms deletion | Prevents accidental deletion |
| BR-004 | Record locked for update | READ with UPDATE option acquires exclusive lock before DELETE | Ensures data integrity during delete |
| BR-005 | Pre-selected user from list | CDEMO-CU03-USR-SELECTED populated from User List screen | Auto-fills User ID and fetches details on first entry |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Navigate to User Delete (from Admin Menu or User List) | XCTL to COUSR03C; display Delete User screen (COUSR3A) | Navigate to user deletion |
| 2 | Enter User ID, press ENTER | READ USRSEC, display First Name, Last Name, User Type; message "Press PF5 key to delete this user ..." | Identify and confirm target user |
| 3 | Press PF5 | READ USRSEC with UPDATE, DELETE record, display "User <ID> has been deleted ...", clear all fields | User permanently removed |
| Alt-A | Press PF3 | XCTL back to previous screen (CDEMO-FROM-PROGRAM or Admin Menu COADM01C) | Exit without deleting |
| Alt-B | Press PF4 | Clear all input fields | Reset form |
| Alt-C | Press PF12 | XCTL to Admin Menu (COADM01C) | Return to admin menu |
| Alt-D | Press invalid key | Display "Invalid key pressed. Please see below..." | Unrecognized key |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CU03
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COUSR03C     │ (Entry & only online program)      │
  │  │ (User Delete)│                                    │
  │  └──┬────┬──────┘                                    │
  │     │    │                                            │
  │     ▼    ▼                                            │
  │  ┌──────┐ ┌──────────────────────────┐               │
  │  │BMS   │ │ VSAM Files               │               │
  │  │MAP:  │ │  USRSEC (User Security)  │               │
  │  │COUSR │ └──────────────────────────┘               │
  │  │3A    │                                             │
  │  └──────┘                                             │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1 (First entry — from Admin Menu or User List via XCTL):
  1. Caller XCTLs to COUSR03C with CARDDEMO-COMMAREA
  2. EIBCALEN > 0; CDEMO-PGM-REENTER = 0 (first entry)
  3. SET CDEMO-PGM-REENTER TO TRUE
  4. MOVE LOW-VALUES TO COUSR3AO (clear output map)
  5. Set cursor to User ID field (USRIDINL = -1)
  6. If CDEMO-CU03-USR-SELECTED is not spaces/low-values:
     ├── MOVE CDEMO-CU03-USR-SELECTED TO USRIDINI
     └── PERFORM PROCESS-ENTER-KEY (auto-fetch user details)
  7. PERFORM SEND-USRDEL-SCREEN
     └── SEND MAP('COUSR3A') MAPSET('COUSR03') ERASE CURSOR
  8. RETURN TRANSID('CU03') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 2 (User enters User ID — ENTER pressed):
  9.  CICS dispatches → COUSR03C (COMMAREA restored)
  10. EIBCALEN > 0; CDEMO-PGM-REENTER = 1
  11. RECEIVE MAP('COUSR3A') INTO(COUSR3AI)
  12. EIBAID = DFHENTER → PERFORM PROCESS-ENTER-KEY
  13. Validate User ID is not empty
      ├── Empty → error "User ID can NOT be empty..."
      └── Not empty → continue
  14. MOVE USRIDINI TO SEC-USR-ID
  15. PERFORM READ-USER-SEC-FILE
      └── READ DATASET('USRSEC') INTO(SEC-USER-DATA) RIDFLD(SEC-USR-ID)
           UPDATE RESP(WS-RESP-CD) RESP2(WS-REAS-CD)
      ├── NORMAL → display user details, message "Press PF5 key to
      │             delete this user ..."
      ├── NOTFND → error "User ID NOT found..."
      └── OTHER  → error "Unable to lookup User..."
  16. PERFORM SEND-USRDEL-SCREEN
  17. RETURN TRANSID('CU03') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 3 (User confirms deletion — PF5 pressed):
  18. CICS dispatches → COUSR03C (COMMAREA restored)
  19. RECEIVE MAP('COUSR3A') INTO(COUSR3AI)
  20. EIBAID = DFHPF5 → PERFORM DELETE-USER-INFO
  21. Validate User ID is not empty (same check)
  22. MOVE USRIDINI TO SEC-USR-ID
  23. PERFORM READ-USER-SEC-FILE (READ with UPDATE to lock record)
  24. PERFORM DELETE-USER-SEC-FILE
      └── DELETE DATASET('USRSEC') RESP(WS-RESP-CD) RESP2(WS-REAS-CD)
      ├── NORMAL → INITIALIZE-ALL-FIELDS, display
      │             "User <ID> has been deleted ..." (GREEN color)
      ├── NOTFND → error "User ID NOT found..."
      └── OTHER  → error "Unable to Update User..."
  25. PERFORM SEND-USRDEL-SCREEN
  26. RETURN TRANSID('CU03') COMMAREA(CARDDEMO-COMMAREA)
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | >0 | XCTL from Admin Menu or User List | Initial logic (PGM-ENTER) | COUSR3A (empty or pre-filled) | CU03 |
| 2 | >0 | Enter User ID, press ENTER | RECEIVE → PROCESS-ENTER-KEY | COUSR3A (user details + PF5 prompt) | CU03 |
| 3 | >0 | Press PF5 | DELETE-USER-INFO → DELETE-USER-SEC-FILE | COUSR3A (success message, cleared) | CU03 |
| N | >0 | PF3 (Exit) | XCTL to CDEMO-FROM-PROGRAM or COADM01C | — | — (XCTL) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | RECEIVE MAP('COUSR3A') MAPSET('COUSR03') | Capture user input from terminal | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 2 | READ DATASET('USRSEC') INTO(SEC-USER-DATA) RIDFLD(SEC-USR-ID) UPDATE | Read user record with exclusive lock | NORMAL → display details; NOTFND → error; OTHER → error |
| 3 | DELETE DATASET('USRSEC') | Delete the previously READ-for-UPDATE record | NORMAL → success; NOTFND → error; OTHER → error |
| 4 | SEND MAP('COUSR3A') MAPSET('COUSR03') ERASE CURSOR | Send screen to terminal | — |
| 5 | RETURN TRANSID('CU03') COMMAREA(CARDDEMO-COMMAREA) | Pseudo-conversational return | — |
| 6 | XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA | Transfer to previous screen on PF3/PF12 | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────┐                ┌─────────────────────────────────────┐
  │ Admin Menu  │   User Delete  │  COUSR3A (Delete User)              │
  │ (COADM01C)  │───────────────▶│                                     │
  └─────────────┘                │  Enter User ID: ________            │
        ▲                        │  ─────────────────────────────────   │
        │ PF3/PF12               │  First Name: ____________________   │
        │                        │  Last Name:  ____________________   │
        │                        │  User Type:  _ (A=Admin, U=User)    │
        │                        │  [Error/Success message line]       │
        │                        │  ENTER=Fetch F3=Back F4=Clear       │
        │                        │  F5=Delete                          │
        └────────────────────────┤                                     │
                                 └─────────────────────────────────────┘
                                   │         │         │
                                   │ ENTER   │ PF4     │ PF5
                                   │ (fetch  │ (clear) │ (delete &
                                   │  user,  │         │  clear)
                                   │  loops) │         │
                                   ▼         ▼         ▼
                                 [Same screen — COUSR3A]

  ┌─────────────┐
  │ User List   │   Pre-selected User ID
  │ (COUSR00C)  │───────────────▶ [COUSR3A with auto-fetched details]
  └─────────────┘
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| COUSR3A | COUSR03 | Delete User — lookup, display, confirm delete | User ID (input), First Name, Last Name, User Type (display-only) | ENTER=Fetch, PF3=Back, PF4=Clear, PF5=Delete, PF12=Admin Menu |

### 3.3 Screen Field Details

#### Map: COUSR3A

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction name (CU03) |
| TITLE01 | ASKIP,FSET,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,FSET,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | ASKIP,FSET,NORM | 8 | Alpha | — | Program name (COUSR03C) |
| TITLE02 | ASKIP,FSET,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | ASKIP,FSET,NORM | 8 | Time | — | Current time hh:mm:ss |
| USRIDIN | FSET,IC,NORM,UNPROT | 8 | Alpha | Non-empty; must exist in USRSEC | User ID to delete |
| FNAME | ASKIP,FSET,NORM | 20 | Alpha | — (display-only) | User's first name (from USRSEC) |
| LNAME | ASKIP,FSET,NORM | 20 | Alpha | — (display-only) | User's last name (from USRSEC) |
| USRTYPE | ASKIP,FSET,NORM | 1 | Alpha | — (display-only) | User type: 'A' = Admin, 'U' = User |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/success message display |

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | COUSR3A | Validate User ID, READ USRSEC, display user details and "Press PF5 key to delete this user ..." |
| PF3 | COUSR3A | Return to previous screen (CDEMO-FROM-PROGRAM, or Admin Menu COADM01C if blank) via XCTL |
| PF4 | COUSR3A | Clear all input fields (INITIALIZE-ALL-FIELDS), redisplay empty form |
| PF5 | COUSR3A | Validate User ID, READ USRSEC with UPDATE, DELETE record, display success message |
| PF12 | COUSR3A | Return to Admin Menu (COADM01C) via XCTL |
| OTHER | COUSR3A | Display "Invalid key pressed. Please see below..." |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COUSR03C | User Delete — screen handler, VSAM read/delete | TRANSID CU03 / XCTL from COADM01C or COUSR00C | 359 | `app/cbl/COUSR03C.cbl` |
| COADM01C | Admin Menu — caller for user management functions | XCTL (caller) | — | `app/cbl/COADM01C.cbl` |
| COUSR00C | User List — may pre-select a user for deletion | XCTL (caller) | — | `app/cbl/COUSR00C.cbl` |
| COSGN00C | Sign-on Screen — redirect target if no COMMAREA | XCTL (fallback) | — | `app/cbl/COSGN00C.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CU03
└── COUSR03C (Entry — Screen Handler, Validation, Data Access)
    ├── READ FILE('USRSEC') UPDATE — Read user record with lock
    ├── DELETE FILE('USRSEC') — Delete the locked record
    ├── SEND MAP('COUSR3A') — Display screen
    ├── RECEIVE MAP('COUSR3A') — Capture input
    ├── RETURN TRANSID('CU03') — Pseudo-conversational return
    └── XCTL → COADM01C or CDEMO-FROM-PROGRAM (on PF3/PF12 exit)
         └── XCTL → COSGN00C (if EIBCALEN = 0, no login session)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COADM01C | COUSR03C | XCTL | CARDDEMO-COMMAREA | — | Admin selects User Delete option |
| COUSR00C | COUSR03C | XCTL | CARDDEMO-COMMAREA (with CDEMO-CU03-USR-SELECTED) | — | Admin selects a user from list for deletion |
| COUSR03C | COADM01C | XCTL | CARDDEMO-COMMAREA | — | PF3/PF12 exit back to admin menu |
| COUSR03C | COSGN00C | XCTL | CARDDEMO-COMMAREA | — | No COMMAREA (EIBCALEN = 0) — redirect to sign-on |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| User Security | USRSEC | VSAM KSDS | READ (UPDATE) / DELETE | COUSR03C | Look up user by ID, lock record, delete record |

### 5.2 File Access Details

#### File: USRSEC (User Security)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CSUSR01Y (SEC-USER-DATA, 80 bytes) |
| Key | SEC-USR-ID (PIC X(08)) |
| Access Mode | READ with UPDATE / DELETE |
| Locking | Record-level exclusive lock via READ UPDATE |
| Shared With | COUSR00C (User List), COUSR01C (User Add), COUSR02C (User Update), COSGN00C (Sign-on) |

**Record Layout (CSUSR01Y):**

| Field | PIC | Offset | Length | Description |
|-------|-----|--------|--------|-------------|
| SEC-USR-ID | X(08) | 0 | 8 | User ID (primary key) |
| SEC-USR-FNAME | X(20) | 8 | 20 | First name |
| SEC-USR-LNAME | X(20) | 28 | 20 | Last name |
| SEC-USR-PWD | X(08) | 48 | 8 | Password |
| SEC-USR-TYPE | X(01) | 56 | 1 | User type ('A' = Admin, 'U' = User) |
| SEC-USR-FILLER | X(23) | 57 | 23 | Reserved filler |

**Access Pattern:**

| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COUSR03C | READ DATASET('USRSEC') RIDFLD(SEC-USR-ID) UPDATE | User ID from screen (USRIDINI) | Returns SEC-USR-FNAME, SEC-USR-LNAME, SEC-USR-TYPE |
| 2 | COUSR03C | DELETE DATASET('USRSEC') | Implicit — deletes the record locked by previous READ UPDATE | Record removed from file |

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
  Terminal Input (COUSR3A)
       │
       ├── USRIDINI (User ID)
       │       │
       │       └──▶ SEC-USR-ID
       │                 │
       │                 └──▶ READ USRSEC (with UPDATE lock)
       │                          │
       │                          ├──▶ SEC-USR-FNAME → FNAMEI  (display on screen)
       │                          ├──▶ SEC-USR-LNAME → LNAMEI  (display on screen)
       │                          └──▶ SEC-USR-TYPE  → USRTYPEI (display on screen)
       │
       │  (User presses PF5)
       │       │
       │       └──▶ SEC-USR-ID (re-read from USRIDINI)
       │                 │
       │                 └──▶ READ USRSEC (with UPDATE lock)
       │                          │
       │                          └──▶ DELETE USRSEC
       │                                    │
       │                                    └──▶ Record permanently removed
       │
       └── Success message: "User <SEC-USR-ID> has been deleted ..."
           (constructed via STRING)

  Header Population:
       FUNCTION CURRENT-DATE → WS-CURDATE-DATA
            ├──▶ WS-CURDATE-MM-DD-YY → CURDATEO
            └──▶ WS-CURTIME-HH-MM-SS → CURTIMEO
       CCDA-TITLE01 → TITLE01O
       CCDA-TITLE02 → TITLE02O
       WS-TRANID    → TRNNAMEO
       WS-PGMNAME   → PGMNAMEO
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| USRIDINI | Terminal (COUSR3AI) | Direct move | SEC-USR-ID | READ key for USRSEC |
| SEC-USR-FNAME | USRSEC record | Direct move | FNAMEI | Screen display (COUSR3AI) |
| SEC-USR-LNAME | USRSEC record | Direct move | LNAMEI | Screen display (COUSR3AI) |
| SEC-USR-TYPE | USRSEC record | Direct move | USRTYPEI | Screen display (COUSR3AI) |
| SEC-USR-ID | Working Storage | STRING concatenation with literals | WS-MESSAGE | Success message on ERRMSG line |
| FUNCTION CURRENT-DATE | System | Split into MM/DD/YY and HH:MM:SS | CURDATEO / CURTIMEO | Header fields |
| CCDA-TITLE01 | COTTL01Y copybook | Direct move | TITLE01O | Header line 1 |
| CCDA-TITLE02 | COTTL01Y copybook | Direct move | TITLE02O | Header line 2 |
| WS-TRANID | Working Storage (VALUE 'CU03') | Direct move | TRNNAMEO | Header transaction name |
| WS-PGMNAME | Working Storage (VALUE 'COUSR03C') | Direct move | PGMNAMEO | Header program name |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`) plus a program-specific extension `CDEMO-CU03-INFO`:

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
| CDEMO-CUST-ID | 9(09) | 9 | IN | Customer ID (not used by CU03) |
| CDEMO-ACCT-ID | 9(11) | 11 | IN | Account ID (not used by CU03) |
| CDEMO-CARD-NUM | 9(16) | 16 | IN | Card Number (not used by CU03) |
| CDEMO-LAST-MAP | X(7) | 7 | IN/OUT | Last BMS map displayed |
| CDEMO-LAST-MAPSET | X(7) | 7 | IN/OUT | Last BMS mapset used |

**CDEMO-CU03-INFO (program-specific extension, defined inline in COUSR03C):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-CU03-USRID-FIRST | X(08) | 8 | IN/OUT | First user ID (pagination context from User List) |
| CDEMO-CU03-USRID-LAST | X(08) | 8 | IN/OUT | Last user ID (pagination context from User List) |
| CDEMO-CU03-PAGE-NUM | 9(08) | 8 | IN/OUT | Page number |
| CDEMO-CU03-NEXT-PAGE-FLG | X(01) | 1 | IN/OUT | 'Y'/'N' — more pages available |
| CDEMO-CU03-USR-SEL-FLG | X(01) | 1 | IN | User selection flag |
| CDEMO-CU03-USR-SELECTED | X(08) | 8 | IN | Pre-selected User ID (from User List screen) |

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. Errors are handled inline via screen messages (WS-MESSAGE → ERRMSGO). File I/O errors are checked via RESP/RESP2:

| RESP Code | Meaning | User Display |
|-----------|---------|-------------|
| DFHRESP(NORMAL) | Operation successful | Continue processing / success message |
| DFHRESP(NOTFND) | User ID not found in USRSEC | "User ID NOT found..." |
| OTHER | Unexpected file error | "Unable to lookup User..." or "Unable to Update User..." |

### 7.3 Conversation State Management

- **COMMAREA preservation**: The entire CARDDEMO-COMMAREA (including CU03-specific fields) is passed on every RETURN TRANSID('CU03'). CICS restores it automatically on the next dispatch.
- **Screen field state**: BMS FSET attribute on USRIDIN ensures the User ID is returned on every RECEIVE, preserving user-entered data across iterations. FNAME, LNAME, and USRTYPE are ASKIP (protected) display-only fields populated by the program.
- **No TSQ overflow**: All state fits within the COMMAREA; no temporary storage queues are used.
- **PGM-CONTEXT flag**: Distinguishes first entry (0 — display empty screen or pre-fill from User List) from subsequent interactions (1 — process input).
- **READ UPDATE lock**: The READ with UPDATE in READ-USER-SEC-FILE acquires an exclusive lock on the record. The subsequent DELETE in DELETE-USER-SEC-FILE operates on this locked record. If the user does not press PF5, the lock is implicitly released when the task ends (RETURN TRANSID).

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path (Fetch):
    Input → Validate User ID → READ USRSEC (UPDATE) → Display Details
            + "Press PF5 key to delete this user ..."

  Normal Path (Delete):
    PF5 → Validate User ID → READ USRSEC (UPDATE) → DELETE USRSEC
           → "User <ID> has been deleted ..."

  Validation Error:
    Input → Validate ──FAIL──▶ "User ID can NOT be empty..."
                                → Cursor to USRIDIN, RETURN TRANSID

  Lookup Error (READ):
    Input → READ USRSEC ──NOTFND──▶ "User ID NOT found..."
                                    → Cursor to USRIDIN, RETURN TRANSID
    Input → READ USRSEC ──OTHER──▶ "Unable to lookup User..."
                                   → Cursor to FNAME, RETURN TRANSID

  Delete Error:
    PF5 → DELETE USRSEC ──NOTFND──▶ "User ID NOT found..."
                                    → Cursor to USRIDIN, RETURN TRANSID
    PF5 → DELETE USRSEC ──OTHER──▶  "Unable to Update User..."
                                    → Cursor to FNAME, RETURN TRANSID

  Invalid Key:
    Other AID key ──▶ "Invalid key pressed. Please see below..."
                      → RETURN TRANSID
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| User ID empty (ENTER) | — | Set ERR-FLG, cursor to USRIDIN | "User ID can NOT be empty..." | — |
| User ID empty (PF5) | — | Set ERR-FLG, cursor to USRIDIN | "User ID can NOT be empty..." | — |
| User not found (READ) | NOTFND (13) | Set ERR-FLG, cursor to USRIDIN | "User ID NOT found..." | — |
| USRSEC read error | OTHER | Set ERR-FLG, cursor to FNAME | "Unable to lookup User..." | DISPLAY RESP/REAS to SYSOUT |
| User not found (DELETE) | NOTFND (13) | Set ERR-FLG, cursor to USRIDIN | "User ID NOT found..." | — |
| USRSEC delete error | OTHER | Set ERR-FLG, cursor to FNAME | "Unable to Update User..." | DISPLAY RESP/REAS to SYSOUT |
| Map receive error | RESP stored | Not explicitly handled beyond RESP capture | — | — |
| Invalid AID key | — | Set ERR-FLG | "Invalid key pressed. Please see below..." | — |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| — | No explicit HANDLE ABEND | Default CICS abend handling applies |

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "User ID can NOT be empty..." | USRIDINI is spaces or low-values (on ENTER or PF5) | ERRMSG (line 23, col 1) |
| — | "User ID NOT found..." | USRSEC READ returns NOTFND | ERRMSG (line 23, col 1) |
| — | "Unable to lookup User..." | USRSEC READ returns unexpected RESP code | ERRMSG (line 23, col 1) |
| — | "Unable to Update User..." | USRSEC DELETE returns unexpected RESP code | ERRMSG (line 23, col 1) |
| — | "Press PF5 key to delete this user ..." | Successful READ of user record (informational, NEUTRAL color) | ERRMSG (line 23, col 1) |
| — | "User _\<ID\>_ has been deleted ..." | Successful DELETE of user record (GREEN color) | ERRMSG (line 23, col 1) |
| — | "Invalid key pressed. Please see below..." | Unrecognized AID key (from CCDA-MSG-INVALID-KEY) | ERRMSG (line 23, col 1) |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CU03 transaction must be authorized for user |
| Resource-level security | RACF FACILITY / File profiles for USRSEC |
| Application-level check | If EIBCALEN = 0 (no COMMAREA — direct invocation without login), XCTL to sign-on screen COSGN00C |
| Admin-only access | Transaction is reachable only from Admin Menu (COADM01C), which restricts access to admin users (CDEMO-USRTYP-ADMIN = 'A') |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| USRSEC file | READ and UPDATE (for READ UPDATE + DELETE) | CICS File Control |

### 9.3 Audit Trail

| Event | Trigger | Data Logged | Destination |
|-------|---------|-------------|-------------|
| Unexpected RESP codes | File I/O error (non-NORMAL, non-NOTFND) | RESP code, REAS code | DISPLAY → SYSOUT (CICS system log) |
| No explicit audit logging for deletions | — | — | Transaction may be audited via CICS journal/SMF records at the system level |

---

## 10. Performance Characteristics

### 10.1 Performance Profile

| Metric | Baseline | Peak | SLA |
|--------|----------|------|-----|
| Response time | < 1 second | < 2 seconds | < 2 seconds |
| CPU per transaction | Very low (1 READ + 1 DELETE) | — | — |
| File I/O count | 2 per delete (1 READ UPDATE + 1 DELETE); 1 per fetch (1 READ UPDATE) | — | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal | — | — |

### 10.2 Performance Considerations

- **READ with UPDATE lock**: Every ENTER key press and PF5 press acquires an exclusive lock on the user record via READ UPDATE. The lock is held until the task completes (RETURN TRANSID). This is a short duration in pseudo-conversational mode since the task ends after each SEND/RETURN.
- **Single DELETE per transaction**: Minimal I/O overhead for the actual delete operation.
- **No cross-file lookups**: Unlike transaction or account operations, user delete only accesses a single file (USRSEC), making it very lightweight.
- **COMMAREA size**: Relatively small; no TSQ overflow needed.
- **BMS SEND/RECEIVE**: Standard map I/O with ERASE — one pair per pseudo-conversational iteration.
- **Two READs on PF5 path**: The DELETE-USER-INFO paragraph calls READ-USER-SEC-FILE before DELETE-USER-SEC-FILE, meaning the record is read with UPDATE lock immediately before deletion. This ensures the record still exists and is locked.

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Normal delete flow | Valid User ID, ENTER to fetch, PF5 to delete | "User <ID> has been deleted ..." (GREEN), fields cleared | High |
| TC-002 | Fetch user details | Valid User ID, press ENTER | First Name, Last Name, User Type displayed; "Press PF5 key to delete this user ..." | High |
| TC-003 | Empty User ID on ENTER | Leave User ID blank, press ENTER | "User ID can NOT be empty..." | High |
| TC-004 | Empty User ID on PF5 | Leave User ID blank, press PF5 | "User ID can NOT be empty..." | High |
| TC-005 | User not found | Enter non-existent User ID, press ENTER | "User ID NOT found..." | High |
| TC-006 | Pre-selected user from list | Navigate from User List with selected user | User ID pre-filled, details auto-fetched | Medium |
| TC-007 | PF3 exit to previous screen | Press PF3 at any point | Return to previous screen (CDEMO-FROM-PROGRAM) | Medium |
| TC-008 | PF12 exit to admin menu | Press PF12 at any point | Return to Admin Menu (COADM01C) | Medium |
| TC-009 | PF4 clear screen | Enter data, press PF4 | All fields cleared, cursor on User ID | Medium |
| TC-010 | Invalid PF key | Press PF2, PF6, etc. | "Invalid key pressed. Please see below..." | Low |
| TC-011 | Delete already-deleted user | Fetch user, another admin deletes same user, press PF5 | "User ID NOT found..." (record gone between fetch and delete) | Medium |
| TC-012 | No COMMAREA (direct invocation) | Invoke CU03 directly without login session | Redirect to sign-on screen COSGN00C | Medium |
| TC-013 | Delete and re-delete | Delete a user, then try to fetch same User ID again | "User ID NOT found..." | Low |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| USRSEC | Pre-loaded with multiple user records (both admin 'A' and regular 'U' types) | VSAM REPRO from test data (`app/data/`) |
| USRSEC | At least one user record with a known User ID for deletion testing | Manual or script insert |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | READ with UPDATE lock acquired on every ENTER and PF5 — even the initial fetch acquires an update lock unnecessarily | Holds exclusive lock on the record until task ends, blocking other users from reading for update | Use READ without UPDATE for the initial fetch (ENTER path), and READ UPDATE only on PF5 path |
| KI-002 | No explicit HANDLE ABEND — unexpected abends use default CICS handling | User sees CICS ABEND screen rather than a friendly error | Add HANDLE ABEND with a user-friendly error screen |
| KI-003 | DELETE error message says "Unable to Update User..." instead of "Unable to Delete User..." | Confusing error message for delete failures | Correct the message text to reflect the actual operation |
| KI-004 | No confirmation prompt beyond PF5 — user presses PF5 and delete happens immediately | Accidental PF5 press deletes the user with no undo | Add a Y/N confirmation field before final deletion |
| KI-005 | No check whether the user being deleted is the currently logged-in admin | Admin could delete their own account, potentially locking themselves out | Add a check comparing CDEMO-USER-ID with USRIDINI |
| KI-006 | WS-USR-MODIFIED flag is defined but never set to 'Y' — appears to be dead code | No functional impact, but confusing for maintainers | Remove unused flag or implement intended tracking |
| KI-007 | On PF5 path, the record is READ with UPDATE then immediately DELETEd — if READ succeeds but DELETE fails with NOTFND, this indicates a logic inconsistency | Unlikely but possible under extreme conditions | The READ UPDATE should guarantee the record exists for the subsequent DELETE |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Screen I/O | BMS Map COUSR3A / 3270 terminal | Web form / REST API DELETE endpoint | Low — simple form with few fields |
| Data Access | VSAM KSDS direct (USRSEC) | Service layer with RDBMS (e.g., RDS PostgreSQL) | Low — single table READ/DELETE |
| Authentication | USRSEC flat file with plaintext passwords | Modern identity provider (Cognito, LDAP, OAuth) | High — security model change |
| Two-Step Delete | ENTER to fetch, PF5 to delete | Single-page with confirmation dialog | Low — simplify to REST DELETE with client-side confirm |
| State Management | COMMAREA pseudo-conversational | HTTP session or stateless REST with tokens | Medium |
| Error Messages | Inline COBOL string literals | Externalized message catalog / i18n | Low |
| Audit Logging | No explicit deletion audit trail | Database triggers or application-level audit log | Medium — important for compliance |
| Self-Delete Prevention | No check against current user | Server-side validation preventing self-deletion | Low |
| Cascade Delete | No cascade — user record deleted but no cleanup of related data | Cascade or soft-delete with referential integrity | Medium — depends on data model relationships |

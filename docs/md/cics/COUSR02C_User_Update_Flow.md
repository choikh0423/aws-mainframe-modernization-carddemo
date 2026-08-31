# User Update (CU02) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CU02 |
| Entry Program | COUSR02C |
| Business Domain | User Administration / Security |
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

- **What**: Allows an administrator to update an existing user record in the USRSEC (User Security) VSAM file. The administrator can modify the user's first name, last name, password, and user type (Admin/User).
- **Who**: System administrators only — this transaction is accessed from the Admin Menu (COADM01C) or the User List screen (COUSR00C). It may also be pre-populated with a selected user ID from the User List.
- **When**: Real-time, on-demand when an administrator needs to modify a user's profile or credentials.
- **Why**: Supports administrative management of user accounts including credential resets, name changes, and role (user type) modifications.
- **What** is the business outcome: The selected user's record in the USRSEC VSAM file is updated with the modified field values. The administrator receives a confirmation message indicating the user has been updated.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  Admin selects "User Update" from Admin Menu,
  └────────────┬─────────────┘  or selects a user from User List (COUSR00C)
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 1            │  Enter User ID to look up
  └────────────┬─────────────┘  (or pre-filled from User List selection)
               │
               ▼
  ┌──────────────────────────┐
  │ System Response          │  READ USRSEC file by User ID;
  └────────────┬─────────────┘  populate First Name, Last Name, Password,
               │                User Type on screen
               ▼
  ┌──────────────────────────┐
  │ User Action 2            │  Modify any combination of:
  └────────────┬─────────────┘  First Name, Last Name, Password, User Type
               │
               ▼
  ┌──────────────────────────┐
  │ Press PF5 (Save) or     │  Submit changes for update
  │ PF3 (Save & Exit)       │
  └────────────┬─────────────┘
               │
           ┌───┴───┐
           │Decision│  Fields modified?
           └───┬───┘
              ╱ ╲
        Yes ╱     ╲ No
           ▼       ▼
  ┌──────────────┐  ┌───────────────────────────┐
  │ REWRITE      │  │ "Please modify to         │
  │ USRSEC record│  │  update ..." (RED)         │
  └──────┬───────┘  └───────────────────────────┘
         │
         ▼
  ┌──────────────────────────┐
  │ Business Outcome         │  "User XXXXXXXX has been updated ..." (GREEN)
  └──────────────────────────┘
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | User ID required for lookup | User ID field is empty (spaces or LOW-VALUES) | Error: "User ID can NOT be empty..." |
| BR-002 | User must exist | User ID not found in USRSEC file | Error: "User ID NOT found..." |
| BR-003 | First Name required for update | First Name is empty when PF5/PF3 pressed | Error: "First Name can NOT be empty..." |
| BR-004 | Last Name required for update | Last Name is empty when PF5/PF3 pressed | Error: "Last Name can NOT be empty..." |
| BR-005 | Password required for update | Password is empty when PF5/PF3 pressed | Error: "Password can NOT be empty..." |
| BR-006 | User Type required for update | User Type is empty when PF5/PF3 pressed | Error: "User Type can NOT be empty..." |
| BR-007 | Modification required | PF5/PF3 pressed but no fields changed from current values | Error: "Please modify to update ..." (RED) |
| BR-008 | Field-level change detection | Each field (First Name, Last Name, Password, User Type) compared individually to current USRSEC record | Only changed fields trigger the modified flag |
| BR-009 | Pre-selection from User List | CDEMO-CU02-USR-SELECTED is populated on first entry | Auto-fill User ID and perform lookup without user typing |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Navigate from Admin Menu or User List | XCTL to COUSR02C; display User Update screen (COUSR2A) | Navigate to user update |
| 2a | Enter User ID, press ENTER | READ USRSEC; populate First Name, Last Name, Password, User Type | Fetch user for editing |
| 2b | (Auto) User pre-selected from User List | Auto-fill User ID, READ USRSEC, populate fields | Direct edit from list |
| 3 | Modify field(s) on screen | Client-side field capture (FSET) | Capture changes |
| 4a | Press PF5 (Save) | Validate all fields, detect changes, REWRITE USRSEC, display success | Save and remain on screen |
| 4b | Press PF3 (Save & Exit) | Validate, detect changes, REWRITE USRSEC, then XCTL to previous screen | Save and return |
| Alt-A | Press PF4 (Clear) | Clear all input fields, reset screen | Reset form |
| Alt-B | Press PF12 (Cancel) | XCTL back to Admin Menu (COADM01C) | Exit without saving |
| Alt-C | Press unsupported key | "Invalid key pressed. Please see below..." | Invalid key feedback |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CU02
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COUSR02C     │ (Entry & only online program)      │
  │  │ (User Update)│                                    │
  │  └──┬────┬──────┘                                    │
  │     │    │                                            │
  │     ▼    ▼                                            │
  │  ┌──────┐ ┌────────────────────────────────────┐     │
  │  │BMS   │ │ VSAM Files                         │     │
  │  │MAP:  │ │  USRSEC  (User Security master)    │     │
  │  │COUSR │ └────────────────────────────────────┘     │
  │  │2A    │                                            │
  │  └──────┘                                            │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1 (First entry — from Admin Menu / User List via XCTL):
  1. COADM01C (or COUSR00C) XCTLs to COUSR02C with CARDDEMO-COMMAREA
  2. EIBCALEN > 0; CDEMO-PGM-REENTER = 0 (first entry)
  3. SET CDEMO-PGM-REENTER TO TRUE
  4. MOVE LOW-VALUES TO COUSR2AO (clear output map)
  5. Set cursor to User ID field (USRIDINL = -1)
  6. If CDEMO-CU02-USR-SELECTED is populated (from User List):
     ├── Move selected user ID to USRIDINI
     └── PERFORM PROCESS-ENTER-KEY (auto-lookup)
  7. PERFORM SEND-USRUPD-SCREEN
     └── SEND MAP('COUSR2A') MAPSET('COUSR02') ERASE CURSOR
  8. RETURN TRANSID('CU02') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 2 (User presses ENTER to fetch user):
  9.  CICS dispatches → COUSR02C (COMMAREA restored)
  10. EIBCALEN > 0; CDEMO-PGM-REENTER = 1
  11. RECEIVE MAP('COUSR2A') INTO(COUSR2AI)
  12. EIBAID = DFHENTER → PERFORM PROCESS-ENTER-KEY
  13. Validate User ID is not empty
      ├── Empty → "User ID can NOT be empty...", cursor to USRIDIN
      └── Not empty → continue
  14. Clear display fields (FNAME, LNAME, PASSWD, USRTYPE)
  15. MOVE USRIDINI to SEC-USR-ID
  16. PERFORM READ-USER-SEC-FILE
      ├── READ DATASET('USRSEC') INTO(SEC-USER-DATA) RIDFLD(SEC-USR-ID)
      │   UPDATE (hold for potential REWRITE)
      ├── NORMAL → populate screen fields from SEC-USER-DATA
      │   "Press PF5 key to save your updates ..." (NEUTRAL color)
      ├── NOTFND → "User ID NOT found..."
      └── OTHER → DISPLAY RESP/REAS, "Unable to lookup User..."
  17. SEND MAP('COUSR2A') with populated fields
  18. RETURN TRANSID('CU02') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 3 (User presses PF5 to save, or PF3 to save & exit):
  19. CICS dispatches → COUSR02C
  20. RECEIVE MAP('COUSR2A') INTO(COUSR2AI)
  21. EIBAID = DFHPF5 → PERFORM UPDATE-USER-INFO
      (or EIBAID = DFHPF3 → PERFORM UPDATE-USER-INFO, then XCTL)
  22. Validate all fields non-empty:
      ├── User ID empty → "User ID can NOT be empty..."
      ├── First Name empty → "First Name can NOT be empty..."
      ├── Last Name empty → "Last Name can NOT be empty..."
      ├── Password empty → "Password can NOT be empty..."
      └── User Type empty → "User Type can NOT be empty..."
  23. READ USRSEC again (with UPDATE) to get current record
  24. Compare each screen field to file record:
      ├── FNAMEI ≠ SEC-USR-FNAME → update, set USR-MODIFIED-YES
      ├── LNAMEI ≠ SEC-USR-LNAME → update, set USR-MODIFIED-YES
      ├── PASSWDI ≠ SEC-USR-PWD → update, set USR-MODIFIED-YES
      └── USRTYPEI ≠ SEC-USR-TYPE → update, set USR-MODIFIED-YES
  25. If USR-MODIFIED-YES:
      ├── PERFORM UPDATE-USER-SEC-FILE
      │   └── REWRITE DATASET('USRSEC') FROM(SEC-USER-DATA)
      │       ├── NORMAL → "User XXXXXXXX has been updated ..." (GREEN)
      │       ├── NOTFND → "User ID NOT found..."
      │       └── OTHER → "Unable to Update User..."
      └── Else → "Please modify to update ..." (RED)
  26. SEND MAP (updated screen)
  27. If PF3: XCTL to CDEMO-FROM-PROGRAM (or COADM01C)
      If PF5: RETURN TRANSID('CU02') COMMAREA
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | >0 | XCTL from Admin Menu or User List | Initial logic (PGM-ENTER) | COUSR2A (empty or pre-filled) | CU02 |
| 2 | >0 | Enter User ID, press ENTER | RECEIVE → PROCESS-ENTER-KEY | COUSR2A (with user data) | CU02 |
| 3 | >0 | Modify fields, press PF5 | RECEIVE → UPDATE-USER-INFO | COUSR2A (success or error) | CU02 |
| N | >0 | PF3 (Save & Exit) | UPDATE-USER-INFO → XCTL | — | — (XCTL) |
| N | >0 | PF12 (Cancel) | XCTL to COADM01C | — | — (XCTL) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | RECEIVE MAP('COUSR2A') MAPSET('COUSR02') INTO(COUSR2AI) | Capture user input from terminal | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 2 | READ DATASET('USRSEC') INTO(SEC-USER-DATA) RIDFLD(SEC-USR-ID) UPDATE | Read user record with update intent | NORMAL → continue; NOTFND → error; OTHER → error |
| 3 | REWRITE DATASET('USRSEC') FROM(SEC-USER-DATA) | Write modified user record back | NORMAL → success; NOTFND → error; OTHER → error |
| 4 | SEND MAP('COUSR2A') MAPSET('COUSR02') FROM(COUSR2AO) ERASE CURSOR | Send screen to terminal | — |
| 5 | RETURN TRANSID('CU02') COMMAREA(CARDDEMO-COMMAREA) | Pseudo-conversational return | — |
| 6 | XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA) | Transfer to previous screen on PF3/PF12 | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────┐                ┌─────────────────────────────────────┐
  │ Admin Menu  │  User Update   │  COUSR2A (Update User)              │
  │ (COADM01C)  │───────────────▶│                                     │
  └─────────────┘                │  Enter User ID: ________            │
        ▲                        │  *******************************    │
  ┌─────┤                        │  First Name: ____________________  │
  │     │                        │            Last Name: ____________  │
  │     │ PF3 (Save & Exit)      │  Password:  ________ (8 Char)      │
  │     │ PF12 (Cancel)          │  User Type: _ (A=Admin, U=User)    │
  │     │                        │                                     │
  │     │                        │  [Error/Success message line]       │
  │     │                        │  ENTER=Fetch F3=Save&Exit F4=Clear  │
  │     │                        │  F5=Save F12=Cancel                 │
  │     └────────────────────────┤                                     │
  │                              └─────────────────────────────────────┘
  │                                │         │         │
  │  ┌─────────────┐               │ ENTER   │ PF4     │ PF5
  │  │ User List   │               │ (fetch  │ (clear  │ (save,
  │  │ (COUSR00C)  │───────────────│  user)  │  form)  │  stay)
  │  └─────────────┘  Pre-select   │         │         │
  │        ▲          User ID      ▼         ▼         ▼
  │        │                     [Same screen — COUSR2A]
  │        │
  └────────┘
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| COUSR2A | COUSR02 | User Update input/display screen | User ID, First Name, Last Name, Password, User Type | ENTER=Fetch, PF3=Save&Exit, PF4=Clear, PF5=Save, PF12=Cancel |

### 3.3 Screen Field Details

#### Map: COUSR2A

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction name (CU02) |
| TITLE01 | ASKIP,FSET,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,FSET,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | ASKIP,FSET,NORM | 8 | Alpha | — | Program name (COUSR02C) |
| TITLE02 | ASKIP,FSET,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | ASKIP,FSET,NORM | 8 | Time | — | Current time hh:mm:ss |
| USRIDIN | FSET,IC,NORM,UNPROT | 8 | Alpha | Non-empty; must exist in USRSEC | User ID (key field for lookup) |
| FNAME | FSET,NORM,UNPROT | 20 | Alpha | Non-empty (on update) | First Name |
| LNAME | FSET,NORM,UNPROT | 20 | Alpha | Non-empty (on update) | Last Name |
| PASSWD | DRK,FSET,UNPROT | 8 | Alpha | Non-empty (on update) | Password (dark/hidden attribute) |
| USRTYPE | FSET,NORM,UNPROT | 1 | Alpha | Non-empty (on update); valid: 'A' or 'U' | User Type (A=Admin, U=User) |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/success message display |

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | COUSR2A | Validate User ID; if valid, READ USRSEC and populate screen fields |
| PF3 | COUSR2A | PERFORM UPDATE-USER-INFO (save changes), then XCTL to previous screen (CDEMO-FROM-PROGRAM or COADM01C) |
| PF4 | COUSR2A | Clear all input fields (INITIALIZE-ALL-FIELDS), redisplay empty form |
| PF5 | COUSR2A | PERFORM UPDATE-USER-INFO — validate, detect changes, REWRITE if modified |
| PF12 | COUSR2A | XCTL back to Admin Menu (COADM01C) — no save |
| OTHER | COUSR2A | Display "Invalid key pressed. Please see below..." |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COUSR02C | User Update — screen handler, validation, VSAM read/rewrite | TRANSID CU02 / XCTL from COADM01C or COUSR00C | 414 | `app/cbl/COUSR02C.cbl` |
| COADM01C | Admin Menu — entry point for user management functions | XCTL (caller) | — | `app/cbl/COADM01C.cbl` |
| COUSR00C | User List — provides pre-selected user ID | XCTL (caller) | — | `app/cbl/COUSR00C.cbl` |
| COSGN00C | Sign-on Screen — redirect target when EIBCALEN = 0 | XCTL (fallback) | — | `app/cbl/COSGN00C.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CU02
└── COUSR02C (Entry — Screen Handler, Validation, Data Access)
    ├── READ FILE('USRSEC') — Read user record (with UPDATE intent)
    ├── REWRITE FILE('USRSEC') — Write back modified user record
    ├── SEND MAP('COUSR2A') — Display screen
    ├── RECEIVE MAP('COUSR2A') — Capture input
    ├── RETURN TRANSID('CU02') — Pseudo-conversational return
    └── XCTL → COADM01C or CDEMO-FROM-PROGRAM (on PF3/PF12 exit)
         └── XCTL → COSGN00C (if EIBCALEN = 0, unauthenticated access)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COADM01C | COUSR02C | XCTL | CARDDEMO-COMMAREA | — | Admin selects User Update |
| COUSR00C | COUSR02C | XCTL | CARDDEMO-COMMAREA (with CDEMO-CU02-USR-SELECTED) | — | Admin selects a user from User List |
| COUSR02C | COADM01C | XCTL | CARDDEMO-COMMAREA | — | PF3 (save & exit) or PF12 (cancel) |
| COUSR02C | COSGN00C | XCTL | — | — | EIBCALEN = 0 (no COMMAREA, unauthenticated) |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| User Security | USRSEC | VSAM KSDS | READ (UPDATE) / REWRITE | COUSR02C | Read user record for display; rewrite with modifications |

### 5.2 File Access Details

#### File: USRSEC (User Security)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CSUSR01Y (SEC-USER-DATA, 80 bytes) |
| Key | SEC-USR-ID (PIC X(08)) |
| Access Mode | READ with UPDATE / REWRITE |
| Locking | Record-level lock held between READ UPDATE and REWRITE |
| Shared With | COUSR00C (User List), COUSR01C (User Add), COUSR03C (User Delete), COSGN00C (Sign-on) |

**Record Layout (CSUSR01Y):**

| Field | PIC | Length | Offset | Description |
|-------|-----|--------|--------|-------------|
| SEC-USR-ID | X(08) | 8 | 0 | User ID (primary key) |
| SEC-USR-FNAME | X(20) | 20 | 8 | First Name |
| SEC-USR-LNAME | X(20) | 20 | 28 | Last Name |
| SEC-USR-PWD | X(08) | 8 | 48 | Password |
| SEC-USR-TYPE | X(01) | 1 | 56 | User Type ('A'=Admin, 'U'=User) |
| SEC-USR-FILLER | X(23) | 23 | 57 | Filler (reserved) |

**Access Pattern:**

| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COUSR02C | READ DATASET('USRSEC') INTO(SEC-USER-DATA) RIDFLD(SEC-USR-ID) UPDATE | User ID from screen (USRIDINI) | Returns SEC-USR-FNAME, SEC-USR-LNAME, SEC-USR-PWD, SEC-USR-TYPE |
| 2 | COUSR02C | REWRITE DATASET('USRSEC') FROM(SEC-USER-DATA) | (Implicit — rewrites record held by READ UPDATE) | Modified SEC-USER-DATA fields |

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
  Terminal Input (COUSR2A)
       │
       ├── USRIDINI (User ID)
       │       │
       │       └──▶ SEC-USR-ID
       │                 │
       │                 └──▶ READ USRSEC (with UPDATE) → SEC-USER-DATA
       │                          │
       │                          ├──▶ SEC-USR-FNAME → FNAMEI  (populate screen)
       │                          ├──▶ SEC-USR-LNAME → LNAMEI  (populate screen)
       │                          ├──▶ SEC-USR-PWD  → PASSWDI (populate screen)
       │                          └──▶ SEC-USR-TYPE → USRTYPEI(populate screen)
       │
       │  (User modifies fields on screen)
       │
       ├── FNAMEI  (First Name — modified)
       │       └──▶ Compare to SEC-USR-FNAME
       │                 └──▶ If different → SEC-USR-FNAME := FNAMEI
       │
       ├── LNAMEI  (Last Name — modified)
       │       └──▶ Compare to SEC-USR-LNAME
       │                 └──▶ If different → SEC-USR-LNAME := LNAMEI
       │
       ├── PASSWDI (Password — modified)
       │       └──▶ Compare to SEC-USR-PWD
       │                 └──▶ If different → SEC-USR-PWD := PASSWDI
       │
       └── USRTYPEI (User Type — modified)
               └──▶ Compare to SEC-USR-TYPE
                         └──▶ If different → SEC-USR-TYPE := USRTYPEI
       
  If any field modified:
       SEC-USER-DATA → REWRITE USRSEC → Success message
  Else:
       "Please modify to update ..." (RED)
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| USRIDINI | Terminal (COUSR2AI) | Direct move | SEC-USR-ID | READ key for USRSEC |
| SEC-USR-FNAME | USRSEC record | Direct move | FNAMEI (COUSR2AI) | Screen display |
| SEC-USR-LNAME | USRSEC record | Direct move | LNAMEI (COUSR2AI) | Screen display |
| SEC-USR-PWD | USRSEC record | Direct move | PASSWDI (COUSR2AI) | Screen display (dark) |
| SEC-USR-TYPE | USRSEC record | Direct move | USRTYPEI (COUSR2AI) | Screen display |
| FNAMEI | Terminal (COUSR2AI) | Direct move (if changed) | SEC-USR-FNAME | REWRITE to USRSEC |
| LNAMEI | Terminal (COUSR2AI) | Direct move (if changed) | SEC-USR-LNAME | REWRITE to USRSEC |
| PASSWDI | Terminal (COUSR2AI) | Direct move (if changed) | SEC-USR-PWD | REWRITE to USRSEC |
| USRTYPEI | Terminal (COUSR2AI) | Direct move (if changed) | SEC-USR-TYPE | REWRITE to USRSEC |
| CCDA-TITLE01 | COTTL01Y copybook | Direct move | TITLE01O | Screen header line 1 |
| CCDA-TITLE02 | COTTL01Y copybook | Direct move | TITLE02O | Screen header line 2 |
| WS-CURDATE-MM-DD-YY | CSDAT01Y (CURRENT-DATE) | Reformatted from YYYYMMDD | CURDATEO | Screen date display |
| WS-CURTIME-HH-MM-SS | CSDAT01Y (CURRENT-DATE) | Reformatted from HHMMSS | CURTIMEO | Screen time display |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`) plus a program-specific extension `CDEMO-CU02-INFO`:

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

**CDEMO-CU02-INFO (program-specific extension, defined inline in COUSR02C):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-CU02-USRID-FIRST | X(08) | 8 | IN/OUT | First user ID (pagination context from User List) |
| CDEMO-CU02-USRID-LAST | X(08) | 8 | IN/OUT | Last user ID (pagination context from User List) |
| CDEMO-CU02-PAGE-NUM | 9(08) | 8 | IN/OUT | Page number (pagination context) |
| CDEMO-CU02-NEXT-PAGE-FLG | X(01) | 1 | IN/OUT | 'Y'/'N' — more pages available |
| CDEMO-CU02-USR-SEL-FLG | X(01) | 1 | IN | User selection flag |
| CDEMO-CU02-USR-SELECTED | X(08) | 8 | IN | Pre-selected User ID (from User List screen) |

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. Errors are handled inline via screen messages (WS-MESSAGE → ERRMSGO). The message color attribute on ERRMSGC is set dynamically:

| Color | Meaning |
|-------|---------|
| DFHGREEN | Success — user updated |
| DFHRED | Warning — no modifications detected |
| DFHNEUTR | Informational — "Press PF5 key to save your updates ..." |
| (default RED from BMS) | Error messages |

### 7.3 Conversation State Management

- **COMMAREA preservation**: The entire CARDDEMO-COMMAREA (including CU02-specific fields) is passed on every RETURN TRANSID('CU02'). CICS restores it automatically on the next dispatch.
- **Screen field state**: BMS FSET attribute ensures all fields are returned on every RECEIVE, preserving user-entered data across iterations.
- **No TSQ overflow**: All state fits within the COMMAREA; no temporary storage queues are used.
- **PGM-CONTEXT flag**: Distinguishes first entry (0 — display empty screen or pre-filled from User List) from subsequent interactions (1 — process input).
- **READ UPDATE lock**: The record is locked by READ with UPDATE and held until REWRITE completes. Note that the lock may be released by CICS between pseudo-conversational iterations since each iteration is a separate task.

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path (ENTER — Fetch):
    Input → Validate User ID → READ USRSEC → Populate screen fields
                                              → "Press PF5 key to save..."

  Normal Path (PF5 — Save):
    Input → Validate all fields → READ USRSEC → Compare fields
         → REWRITE → "User XXXXXXXX has been updated ..." (GREEN)

  Validation Error (Fetch):
    Input → User ID empty ──▶ "User ID can NOT be empty..."
                               → Cursor to USRIDIN, RETURN TRANSID

  Validation Error (Update):
    Input → Field empty ──▶ "First Name can NOT be empty..." /
                            "Last Name can NOT be empty..." /
                            "Password can NOT be empty..." /
                            "User Type can NOT be empty..."
                            → Cursor to field, RETURN TRANSID

  Lookup Error:
    READ USRSEC ──NOTFND──▶ "User ID NOT found..."
                             → Cursor to USRIDIN, RETURN TRANSID
    READ USRSEC ──OTHER──▶  "Unable to lookup User..."
                             → Cursor to FNAME, RETURN TRANSID

  No Modification:
    Compare ──No changes──▶ "Please modify to update ..." (RED)

  Rewrite Error:
    REWRITE ──NOTFND──▶ "User ID NOT found..."
    REWRITE ──OTHER──▶  "Unable to Update User..."

  Invalid Key:
    EIBAID = OTHER ──▶ "Invalid key pressed. Please see below..."
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| User not found in USRSEC (READ) | NOTFND (13) | Set ERR-FLG, cursor to USRIDIN | "User ID NOT found..." | — |
| USRSEC READ error | OTHER | Set ERR-FLG, cursor to FNAME | "Unable to lookup User..." | DISPLAY RESP/REAS to SYSOUT |
| User not found in USRSEC (REWRITE) | NOTFND (13) | Set ERR-FLG, cursor to USRIDIN | "User ID NOT found..." | — |
| USRSEC REWRITE error | OTHER | Set ERR-FLG, cursor to FNAME | "Unable to Update User..." | DISPLAY RESP/REAS to SYSOUT |
| No COMMAREA (unauthenticated) | EIBCALEN = 0 | XCTL to COSGN00C | — | — |
| Map fail | MAPFAIL | Not explicitly handled | — | — |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| — | No explicit HANDLE ABEND | Default CICS abend handling applies |

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "User ID can NOT be empty..." | USRIDINI is spaces or LOW-VALUES (on ENTER or PF5/PF3) | ERRMSG (line 23, col 1) |
| — | "First Name can NOT be empty..." | FNAMEI is spaces or LOW-VALUES (on PF5/PF3) | ERRMSG (line 23, col 1) |
| — | "Last Name can NOT be empty..." | LNAMEI is spaces or LOW-VALUES (on PF5/PF3) | ERRMSG (line 23, col 1) |
| — | "Password can NOT be empty..." | PASSWDI is spaces or LOW-VALUES (on PF5/PF3) | ERRMSG (line 23, col 1) |
| — | "User Type can NOT be empty..." | USRTYPEI is spaces or LOW-VALUES (on PF5/PF3) | ERRMSG (line 23, col 1) |
| — | "User ID NOT found..." | READ or REWRITE returns NOTFND | ERRMSG (line 23, col 1) |
| — | "Unable to lookup User..." | READ returns unexpected RESP code | ERRMSG (line 23, col 1) |
| — | "Unable to Update User..." | REWRITE returns unexpected RESP code | ERRMSG (line 23, col 1) |
| — | "Please modify to update ..." | PF5/PF3 pressed but no fields differ from current record | ERRMSG (line 23, col 1), color RED |
| — | "Press PF5 key to save your updates ..." | Successful READ after ENTER (user fetched) | ERRMSG (line 23, col 1), color NEUTRAL |
| — | "User XXXXXXXX has been updated ..." | Successful REWRITE (STRING: 'User ' + SEC-USR-ID + ' has been updated ...') | ERRMSG (line 23, col 1), color GREEN |
| — | "Invalid key pressed. Please see below..." | Unrecognized AID key (from CCDA-MSG-INVALID-KEY in CSMSG01Y) | ERRMSG (line 23, col 1) |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CU02 transaction must be authorized for user |
| Resource-level security | RACF FACILITY / File profiles for USRSEC |
| Application-level check | If EIBCALEN = 0 (no COMMAREA — direct invocation without login), XCTL to sign-on screen COSGN00C |
| Admin-only access | Transaction is reachable only from Admin Menu (COADM01C) — requires CDEMO-USRTYP-ADMIN |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| USRSEC file | READ and UPDATE (for READ UPDATE + REWRITE) | CICS File Control |

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
| CPU per transaction | Low (validation + 1 READ + 1 REWRITE) | — | — |
| File I/O count | 2–3 per update (1–2 READ UPDATE + 1 REWRITE) | — | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal | — | — |

### 10.2 Performance Considerations

- **READ UPDATE pattern**: The record is read with UPDATE intent which acquires an exclusive lock. In a high-concurrency environment, multiple admins updating the same user simultaneously could experience lock contention. However, this is an admin-only operation with low expected concurrency.
- **Double READ**: The UPDATE-USER-INFO paragraph performs a second READ UPDATE to re-fetch the current record for comparison. This ensures the comparison is against the latest data but doubles the I/O for the update path.
- **Single REWRITE per update**: Minimal I/O overhead for the actual write operation.
- **COMMAREA size**: Relatively small; no TSQ overflow needed.
- **BMS SEND/RECEIVE**: Standard map I/O with ERASE — one pair per pseudo-conversational iteration.
- **No external program calls**: Unlike some transactions (e.g., CT02 which calls CSUTLDTC), this transaction has no CALL or LINK to external programs, keeping overhead low.

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Normal update via PF5 | Valid User ID, modify First Name, press PF5 | "User XXXXXXXX has been updated ..." (GREEN) | High |
| TC-002 | Normal update via PF3 (save & exit) | Valid User ID, modify Last Name, press PF3 | Update saved, XCTL to Admin Menu | High |
| TC-003 | Fetch user by ENTER | Enter valid User ID, press ENTER | Screen populated with user data; "Press PF5 key to save your updates ..." | High |
| TC-004 | Pre-selected user from User List | Navigate from User List with selected user | User ID auto-filled, fields populated | High |
| TC-005 | Empty User ID on ENTER | Leave User ID blank, press ENTER | "User ID can NOT be empty..." | High |
| TC-006 | User ID not found | Enter non-existent User ID, press ENTER | "User ID NOT found..." | High |
| TC-007 | Empty First Name on PF5 | Fetch user, clear First Name, press PF5 | "First Name can NOT be empty..." | Medium |
| TC-008 | Empty Last Name on PF5 | Fetch user, clear Last Name, press PF5 | "Last Name can NOT be empty..." | Medium |
| TC-009 | Empty Password on PF5 | Fetch user, clear Password, press PF5 | "Password can NOT be empty..." | Medium |
| TC-010 | Empty User Type on PF5 | Fetch user, clear User Type, press PF5 | "User Type can NOT be empty..." | Medium |
| TC-011 | No modifications | Fetch user, press PF5 without changes | "Please modify to update ..." (RED) | Medium |
| TC-012 | Update all fields | Modify First Name, Last Name, Password, User Type | "User XXXXXXXX has been updated ..." (GREEN) | Medium |
| TC-013 | PF4 clear | Press PF4 with data on screen | All fields cleared, cursor to User ID | Low |
| TC-014 | PF12 cancel | Press PF12 at any point | Return to Admin Menu (COADM01C) | Medium |
| TC-015 | Invalid PF key | Press PF2, PF6, etc. | "Invalid key pressed. Please see below..." | Low |
| TC-016 | Direct invocation (no COMMAREA) | Invoke CU02 directly without login | XCTL to sign-on screen COSGN00C | Low |
| TC-017 | Password update only | Fetch user, modify only Password, press PF5 | "User XXXXXXXX has been updated ..." (GREEN) | High |
| TC-018 | User Type change (User→Admin) | Fetch user, change Type from 'U' to 'A', press PF5 | "User XXXXXXXX has been updated ..." (GREEN) | High |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| USRSEC | Pre-loaded with valid user records (at least one Admin and one regular User) | VSAM REPRO from test data (`app/data/`) |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | READ UPDATE lock is not preserved across pseudo-conversational iterations — between the fetch (ENTER) and the save (PF5), another task could modify the record | Lost update anomaly possible in multi-admin scenarios | The program re-reads with UPDATE before REWRITE in UPDATE-USER-INFO, which mitigates stale data but introduces a small window between re-read and rewrite |
| KI-002 | No explicit HANDLE ABEND — unexpected abends use default CICS handling | User sees CICS ABEND screen rather than a friendly error | Add HANDLE ABEND with a user-friendly error screen |
| KI-003 | Validation in UPDATE-USER-INFO uses cascading EVALUATE with PERFORM SEND-USRUPD-SCREEN — only the first validation error is reported per iteration | User must fix errors one at a time | Accumulate all errors before displaying |
| KI-004 | Password is displayed in the PASSWD field with DRK (dark) attribute, but BMS FSET causes it to be sent back on every RECEIVE — the password travels in the clear over the 3270 data stream | Security concern for sensitive data in transit | Encrypt 3270 session (TN3270E SSL/TLS) |
| KI-005 | No User Type validation — the program checks for empty but does not validate that the value is 'A' or 'U' | Invalid user type (e.g., 'X') could be written to USRSEC | Add EVALUATE to check for valid 'A'/'U' values |
| KI-006 | The double-READ pattern (once on ENTER, once on PF5/PF3) means the UPDATE lock from the first READ is released before the second READ in UPDATE-USER-INFO | Slight inefficiency and brief lock gap | Could be restructured to hold the lock through a single iteration |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Screen I/O | BMS Map COUSR2A / 3270 terminal | Web form / REST API PUT endpoint | Medium — field mapping is 1:1 |
| Data Access | VSAM KSDS direct (USRSEC) | Service layer with RDBMS (e.g., RDS PostgreSQL) | Low — simple READ/REWRITE operations |
| Validation | Inline COBOL EVALUATE cascades | Framework validation (Bean Validation, JSON Schema) | Low — validation rules are simple (non-empty checks) |
| State Management | COMMAREA pseudo-conversational | HTTP session or stateless REST with tokens | Medium |
| Password Handling | Plaintext storage in VSAM, dark BMS attribute for display | Hashed passwords (bcrypt/scrypt), never displayed | High — requires credential migration strategy |
| User Type | Single character flag ('A'/'U') in flat file | Role-based access control (RBAC) with database-backed roles | Medium |
| Change Detection | Field-by-field COBOL comparison | ORM dirty tracking or optimistic concurrency (version column) | Low |
| Audit | No explicit audit trail | Database audit triggers or application-level logging | Medium |
| Authentication Guard | EIBCALEN = 0 check → XCTL to sign-on | JWT/OAuth2 middleware, role-based route guards | Medium |
| Concurrent Access | READ UPDATE + REWRITE (pessimistic, per-iteration) | Optimistic concurrency control with version/timestamp | Low |

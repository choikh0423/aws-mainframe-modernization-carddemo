# User Add (CU01) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CU01 |
| Entry Program | COUSR01C |
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

- **What**: Allows an administrator to add a new user (Regular or Admin) to the USRSEC security file. The user record includes the user ID, first name, last name, password, and user type.
- **Who**: Authenticated CardDemo administrators only — Admin Menu option 2 ("User Add (Security)").
- **When**: Real-time, on-demand when a new user account needs to be provisioned for the CardDemo application.
- **Why**: Supports security administration by enabling the creation of user credentials for system access. New users require a USRSEC record before they can sign on to CardDemo.
- **What** is the business outcome: A new user security record is written to the USRSEC VSAM file. The user can then sign on to CardDemo using the assigned user ID and password.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  Admin selects Option 2 ("User Add") from Admin Menu
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 1            │  Enter user details:
  └────────────┬─────────────┘  First Name, Last Name, User ID,
               │                Password, User Type (A=Admin, U=User)
               ▼
  ┌──────────────────────────┐
  │ System Validation        │  Validate all required fields are non-empty
  └────────────┬─────────────┘
               │
           ┌───┴───┐
           │Decision│  Validation passes?
           └───┬───┘
              ╱ ╲
        Yes ╱     ╲ No
           ▼       ▼
  ┌──────────────┐  ┌─────────────────┐
  │ Write to     │  │ Error message,  │
  │ USRSEC file  │  │ cursor to field │
  └──────┬───────┘  └─────────────────┘
         │
     ┌───┴───┐
     │Result?│
     └───┬───┘
        ╱ ╲
  OK  ╱     ╲ Dup
     ▼       ▼
┌───────────────────────┐  ┌──────────────────────────────────┐
│ Success message:      │  │ "User ID already exist..."       │
│ "User XXXX has been   │  │ cursor to User ID field          │
│  added ..."           │  └──────────────────────────────────┘
│ Screen clears for     │
│ next entry.           │
└───────────────────────┘
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | First Name is mandatory | First Name field is empty (spaces or low-values) | Error: "First Name can NOT be empty..." |
| BR-002 | Last Name is mandatory | Last Name field is empty (spaces or low-values) | Error: "Last Name can NOT be empty..." |
| BR-003 | User ID is mandatory | User ID field is empty (spaces or low-values) | Error: "User ID can NOT be empty..." |
| BR-004 | Password is mandatory | Password field is empty (spaces or low-values) | Error: "Password can NOT be empty..." |
| BR-005 | User Type is mandatory | User Type field is empty (spaces or low-values) | Error: "User Type can NOT be empty..." |
| BR-006 | User ID must be unique | User ID already exists in USRSEC file | Error: "User ID already exist..." |
| BR-007 | Validation is sequential | Fields are validated in order: First Name → Last Name → User ID → Password → User Type | Only the first error encountered is displayed per iteration |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Select Option 2 from Admin Menu | XCTL to COUSR01C; display empty Add User screen (COUSR1A) | Navigate to user creation |
| 2 | Fill in First Name, Last Name, User ID, Password, User Type | Client-side field capture (FSET) | Capture new user data |
| 3 | Press ENTER | Validate all fields; if valid, write user record | Submit for processing |
| 3a | — (Validation passes) | WRITE to USRSEC; display success message "User XXXX has been added ..."; clear all fields | User account created |
| 3b | — (Validation fails) | Display error message, cursor positioned to offending field | User corrects input |
| Alt-A | Press PF3 | XCTL back to Admin Menu (COADM01C) | Exit without saving |
| Alt-B | Press PF4 | Clear all input fields, redisplay empty form | Reset form |
| Alt-C | Press any other key | Display "Invalid key pressed. Please see below..." | Unrecognized key |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CU01
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COUSR01C     │ (Entry & only online program)      │
  │  │ (Add User)   │                                    │
  │  └──┬────┬──────┘                                    │
  │     │    │                                            │
  │     ▼    ▼                                            │
  │  ┌──────┐ ┌────────────────────────────────────┐     │
  │  │BMS   │ │ VSAM Files                         │     │
  │  │MAP:  │ │  USRSEC (User Security Master)     │     │
  │  │COUSR │ └────────────────────────────────────┘     │
  │  │1A    │                                            │
  │  └──────┘                                            │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1 (First entry — from Admin Menu via XCTL):
  1. COADM01C XCTLs to COUSR01C with CARDDEMO-COMMAREA
  2. EIBCALEN > 0; CDEMO-PGM-REENTER = 0 (first entry)
  3. SET CDEMO-PGM-REENTER TO TRUE
  4. MOVE LOW-VALUES TO COUSR1AO (clear output map)
  5. Set cursor to First Name field (FNAMEL = -1)
  6. PERFORM SEND-USRADD-SCREEN
     └── SEND MAP('COUSR1A') MAPSET('COUSR01') ERASE CURSOR
  7. RETURN TRANSID('CU01') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 2 (User submits data — ENTER pressed):
  8.  CICS dispatches → COUSR01C (COMMAREA restored)
  9.  EIBCALEN > 0; CDEMO-PGM-REENTER = 1
  10. PERFORM RECEIVE-USRADD-SCREEN
      └── RECEIVE MAP('COUSR1A') INTO(COUSR1AI)
  11. EIBAID = DFHENTER → PERFORM PROCESS-ENTER-KEY
  12. EVALUATE TRUE (sequential field validation):
      ├── FNAMEI = SPACES/LOW-VALUES → error "First Name can NOT be empty..."
      ├── LNAMEI = SPACES/LOW-VALUES → error "Last Name can NOT be empty..."
      ├── USERIDI = SPACES/LOW-VALUES → error "User ID can NOT be empty..."
      ├── PASSWDI = SPACES/LOW-VALUES → error "Password can NOT be empty..."
      ├── USRTYPEI = SPACES/LOW-VALUES → error "User Type can NOT be empty..."
      └── OTHER → all fields valid, set cursor to FNAME, CONTINUE
  13. IF NOT ERR-FLG-ON:
      ├── Move screen fields to SEC-USER-DATA record:
      │   USERIDI  → SEC-USR-ID
      │   FNAMEI   → SEC-USR-FNAME
      │   LNAMEI   → SEC-USR-LNAME
      │   PASSWDI  → SEC-USR-PWD
      │   USRTYPEI → SEC-USR-TYPE
      └── PERFORM WRITE-USER-SEC-FILE
  14. WRITE DATASET('USRSEC') FROM(SEC-USER-DATA) RIDFLD(SEC-USR-ID)
      ├── NORMAL → INITIALIZE-ALL-FIELDS, set ERRMSG color GREEN,
      │            display "User XXXX has been added ..."
      ├── DUPKEY/DUPREC → "User ID already exist..."
      └── OTHER → "Unable to Add User..."
  15. RETURN TRANSID('CU01') COMMAREA(CARDDEMO-COMMAREA)
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | >0 | XCTL from Admin Menu (option 2) | Initial logic (PGM-ENTER) | COUSR1A (empty form) | CU01 |
| 2 | >0 | Fill fields, press ENTER | RECEIVE → PROCESS-ENTER-KEY | COUSR1A (with errors or success + cleared) | CU01 |
| 3 | >0 | Fill again or correct, press ENTER | RECEIVE → PROCESS-ENTER-KEY | COUSR1A (success or error) | CU01 |
| N | >0 | PF3 (Exit) | XCTL to COADM01C (Admin Menu) | — | — (XCTL) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | RECEIVE MAP('COUSR1A') MAPSET('COUSR01') INTO(COUSR1AI) | Capture user input from terminal | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 2 | WRITE DATASET('USRSEC') FROM(SEC-USER-DATA) RIDFLD(SEC-USR-ID) KEYLENGTH(LENGTH OF SEC-USR-ID) | Write new user record to USRSEC | NORMAL → success; DUPKEY/DUPREC → duplicate; OTHER → error |
| 3 | SEND MAP('COUSR1A') MAPSET('COUSR01') FROM(COUSR1AO) ERASE CURSOR | Send screen to terminal | — |
| 4 | RETURN TRANSID('CU01') COMMAREA(CARDDEMO-COMMAREA) | Pseudo-conversational return | — |
| 5 | XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA) | Transfer to Admin Menu on PF3 | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌──────────────┐                ┌─────────────────────────────────────┐
  │  Admin Menu  │   Option 2     │  COUSR1A (Add User)                 │
  │  (COADM01C)  │───────────────▶│                                     │
  └──────────────┘                │  First Name: ____________________   │
        ▲                         │  Last Name:  ____________________   │
        │ PF3                     │                                     │
        │                         │  User ID: ________ (8 Char)         │
        │                         │  Password: ________ (8 Char)        │
        │                         │                                     │
        │                         │  User Type: _ (A=Admin, U=User)     │
        │                         │                                     │
        │                         │  [Error/Success message line]       │
        │                         │  ENTER=Add User F3=Back F4=Clear    │
        │                         │  F12=Exit                           │
        └─────────────────────────┤                                     │
                                  └─────────────────────────────────────┘
                                    │         │
                                    │ ENTER   │ PF4 (Clear)
                                    │ (loops  │ (resets form,
                                    │  back   │  loops back)
                                    │  to     │
                                    │  self)  │
                                    ▼         ▼
                                  [Same screen — COUSR1A]
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| COUSR1A | COUSR01 | Add User input screen | First Name, Last Name, User ID, Password, User Type | ENTER=Add User, PF3=Back, PF4=Clear, F12=Exit |

### 3.3 Screen Field Details

#### Map: COUSR1A

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction name (CU01) |
| TITLE01 | ASKIP,FSET,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,FSET,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | ASKIP,FSET,NORM | 8 | Alpha | — | Program name (COUSR01C) |
| TITLE02 | ASKIP,FSET,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | ASKIP,FSET,NORM | 8 | Time | — | Current time hh:mm:ss |
| FNAME | FSET,IC,NORM,UNPROT | 20 | Alpha | Non-empty | First Name of new user |
| LNAME | FSET,NORM,UNPROT | 20 | Alpha | Non-empty | Last Name of new user |
| USERID | FSET,NORM,UNPROT | 8 | AlphaNum | Non-empty; unique in USRSEC | User ID (login identifier) |
| PASSWD | DRK,FSET,UNPROT | 8 | AlphaNum | Non-empty | Password (dark attribute — not displayed on screen) |
| USRTYPE | FSET,NORM,UNPROT | 1 | Alpha | Non-empty; 'A' or 'U' expected | User Type (A=Admin, U=User) |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/success message display |

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | COUSR1A | Validate all input fields; if valid, write user record to USRSEC |
| PF3 | COUSR1A | Set CDEMO-TO-PROGRAM to 'COADM01C', XCTL to Admin Menu |
| PF4 | COUSR1A | Clear all input fields (INITIALIZE-ALL-FIELDS), redisplay empty form |
| OTHER | COUSR1A | Display "Invalid key pressed. Please see below..." |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COUSR01C | Add User — screen handler, validation, VSAM write | TRANSID CU01 / XCTL from COADM01C | 299 | `app/cbl/COUSR01C.cbl` |
| COADM01C | Admin Menu — entry point for option 2 | XCTL (caller) | — | `app/cbl/COADM01C.cbl` |
| COSGN00C | Sign-on Screen — redirect target when EIBCALEN = 0 | XCTL (fallback) | — | `app/cbl/COSGN00C.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CU01
└── COUSR01C (Entry — Screen Handler, Validation, Data Access)
    ├── WRITE FILE('USRSEC') — Write new user security record
    ├── SEND MAP('COUSR1A') — Display screen
    ├── RECEIVE MAP('COUSR1A') — Capture input
    ├── RETURN TRANSID('CU01') — Pseudo-conversational return
    └── XCTL → COADM01C (on PF3 exit) or COSGN00C (if EIBCALEN = 0)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COADM01C | COUSR01C | XCTL | CARDDEMO-COMMAREA | — | Admin selects menu option 2 |
| COUSR01C | COADM01C | XCTL | CARDDEMO-COMMAREA | — | PF3 exit back to Admin Menu |
| COUSR01C | COSGN00C | XCTL | CARDDEMO-COMMAREA | — | EIBCALEN = 0 (no valid session) |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| User Security Master | USRSEC | VSAM KSDS | WRITE | COUSR01C | Store new user credentials and profile |

### 5.2 File Access Details

#### File: USRSEC (User Security Master)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CSUSR01Y (SEC-USER-DATA, 80 bytes) |
| Key | SEC-USR-ID (PIC X(08)) |
| Access Mode | WRITE |
| Locking | Record-level for WRITE |
| Shared With | COSGN00C (Sign-on — READ for authentication), COUSR00C (User List — BROWSE), COUSR02C (User Update — READ/REWRITE), COUSR03C (User Delete — READ/DELETE) |

**Record Layout (CSUSR01Y — SEC-USER-DATA):**

| Field | PIC | Offset | Length | Description |
|-------|-----|--------|--------|-------------|
| SEC-USR-ID | X(08) | 0 | 8 | User ID (primary key) |
| SEC-USR-FNAME | X(20) | 8 | 20 | First Name |
| SEC-USR-LNAME | X(20) | 28 | 20 | Last Name |
| SEC-USR-PWD | X(08) | 48 | 8 | Password |
| SEC-USR-TYPE | X(01) | 56 | 1 | User Type ('A' = Admin, 'U' = User) |
| SEC-USR-FILLER | X(23) | 57 | 23 | Reserved / filler |

**Access Pattern:**

| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COUSR01C | WRITE DATASET('USRSEC') FROM(SEC-USER-DATA) RIDFLD(SEC-USR-ID) KEYLENGTH(LENGTH OF SEC-USR-ID) | SEC-USR-ID from screen (USERIDI) | All SEC-USER-DATA fields |

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
  Terminal Input (COUSR1A)
       │
       ├── FNAMEI (First Name)
       │       │
       │       └──▶ SEC-USR-FNAME (in SEC-USER-DATA)
       │
       ├── LNAMEI (Last Name)
       │       │
       │       └──▶ SEC-USR-LNAME (in SEC-USER-DATA)
       │
       ├── USERIDI (User ID)
       │       │
       │       ├──▶ SEC-USR-ID (primary key in SEC-USER-DATA)
       │       │
       │       └──▶ RIDFLD for WRITE to USRSEC
       │
       ├── PASSWDI (Password)
       │       │
       │       └──▶ SEC-USR-PWD (in SEC-USER-DATA)
       │
       └── USRTYPEI (User Type)
               │
               └──▶ SEC-USR-TYPE (in SEC-USER-DATA)

  After validation passes:
       SEC-USER-DATA (fully populated)
            │
            └──▶ WRITE to USRSEC VSAM file
                      │
                      ├── NORMAL → INITIALIZE-ALL-FIELDS
                      │            → Success message with SEC-USR-ID
                      ├── DUPKEY/DUPREC → Error message
                      └── OTHER → Error message
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| FNAMEI | Terminal (COUSR1AI) | Direct move (PIC X(20)) | SEC-USR-FNAME | SEC-USER-DATA record |
| LNAMEI | Terminal (COUSR1AI) | Direct move (PIC X(20)) | SEC-USR-LNAME | SEC-USER-DATA record |
| USERIDI | Terminal (COUSR1AI) | Direct move (PIC X(08)) | SEC-USR-ID | SEC-USER-DATA record (key) |
| PASSWDI | Terminal (COUSR1AI) | Direct move (PIC X(08)) | SEC-USR-PWD | SEC-USER-DATA record |
| USRTYPEI | Terminal (COUSR1AI) | Direct move (PIC X(01)) | SEC-USR-TYPE | SEC-USER-DATA record |
| CCDA-TITLE01 | COTTL01Y copybook | Direct move (PIC X(40)) | TITLE01O | Screen header line 1 |
| CCDA-TITLE02 | COTTL01Y copybook | Direct move (PIC X(40)) | TITLE02O | Screen header line 2 |
| WS-TRANID | Working Storage | Direct move (PIC X(04)) | TRNNAMEO | Screen transaction name |
| WS-PGMNAME | Working Storage | Direct move (PIC X(08)) | PGMNAMEO | Screen program name |
| FUNCTION CURRENT-DATE | COBOL intrinsic | Reformatted MM/DD/YY | CURDATEO | Screen date |
| FUNCTION CURRENT-DATE | COBOL intrinsic | Reformatted HH:MM:SS | CURTIMEO | Screen time |
| SEC-USR-ID | SEC-USER-DATA | STRING with delimiters | WS-MESSAGE | Success message "User XXXX has been added ..." |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`). There is no program-specific extension for COUSR01C — only the general portion is used.

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
| CDEMO-CUST-ID | 9(09) | 9 | — | Not used by this transaction |
| CDEMO-CUST-FNAME | X(25) | 25 | — | Not used by this transaction |
| CDEMO-CUST-MNAME | X(25) | 25 | — | Not used by this transaction |
| CDEMO-CUST-LNAME | X(25) | 25 | — | Not used by this transaction |
| CDEMO-ACCT-ID | 9(11) | 11 | — | Not used by this transaction |
| CDEMO-ACCT-STATUS | X(01) | 1 | — | Not used by this transaction |
| CDEMO-CARD-NUM | 9(16) | 16 | — | Not used by this transaction |
| CDEMO-LAST-MAP | X(7) | 7 | — | Not used by this transaction |
| CDEMO-LAST-MAPSET | X(7) | 7 | — | Not used by this transaction |

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. Errors are handled inline via screen messages (WS-MESSAGE → ERRMSGO). The WRITE command RESP code is checked directly:

| RESP Code | Meaning | User Display |
|-----------|---------|-------------|
| DFHRESP(NORMAL) | Write successful | "User XXXX has been added ..." (green) |
| DFHRESP(DUPKEY) / DFHRESP(DUPREC) | Duplicate user ID | "User ID already exist..." |
| OTHER | Unexpected write error | "Unable to Add User..." |

### 7.3 Conversation State Management

- **COMMAREA preservation**: The CARDDEMO-COMMAREA is passed on every RETURN TRANSID('CU01'). CICS restores it automatically on the next dispatch.
- **Screen field state**: BMS FSET attribute on all input fields ensures they are returned on every RECEIVE, preserving user-entered data across iterations.
- **No TSQ overflow**: All state fits within the COMMAREA; no temporary storage queues are used.
- **PGM-CONTEXT flag**: Distinguishes first entry (0 — display empty screen) from subsequent interactions (1 — process input).
- **RETURN-TO-PREV-SCREEN**: Resets CDEMO-PGM-CONTEXT to 0 before XCTL, so the target program treats it as a fresh entry.

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path:
    Input → Validate Fields (sequential) → All Valid → WRITE USRSEC → Success

  Validation Error:
    Input → Validate ──FAIL──▶ "First Name can NOT be empty..." /
                                "Last Name can NOT be empty..." /
                                "User ID can NOT be empty..." /
                                "Password can NOT be empty..." /
                                "User Type can NOT be empty..."
                                → Cursor to field, RETURN TRANSID

  Write Error (Duplicate):
    WRITE ──DUPKEY/DUPREC──▶ "User ID already exist..."
                             → Cursor to User ID, RETURN TRANSID

  Write Error (Other):
    WRITE ──OTHER──▶ "Unable to Add User..."
                     → Cursor to First Name, RETURN TRANSID

  Invalid Key:
    AID ──OTHER──▶ "Invalid key pressed. Please see below..."
                   → Cursor to First Name, RETURN TRANSID
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| First Name empty | — (application validation) | Set ERR-FLG, cursor to FNAME | "First Name can NOT be empty..." | — |
| Last Name empty | — (application validation) | Set ERR-FLG, cursor to LNAME | "Last Name can NOT be empty..." | — |
| User ID empty | — (application validation) | Set ERR-FLG, cursor to USERID | "User ID can NOT be empty..." | — |
| Password empty | — (application validation) | Set ERR-FLG, cursor to PASSWD | "Password can NOT be empty..." | — |
| User Type empty | — (application validation) | Set ERR-FLG, cursor to USRTYPE | "User Type can NOT be empty..." | — |
| Duplicate user ID | DUPKEY (14) / DUPREC (15) | Set ERR-FLG, cursor to USERID | "User ID already exist..." | — |
| Unexpected write error | OTHER | Set ERR-FLG, cursor to FNAME | "Unable to Add User..." | — (DISPLAY commented out in source) |
| Invalid AID key | — | Set ERR-FLG, cursor to FNAME | "Invalid key pressed. Please see below..." | — |
| No COMMAREA (EIBCALEN=0) | — | XCTL to COSGN00C | — | — |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| — | No explicit HANDLE ABEND | Default CICS abend handling applies |

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "First Name can NOT be empty..." | FNAMEI = SPACES or LOW-VALUES | ERRMSG (line 23, col 1) |
| — | "Last Name can NOT be empty..." | LNAMEI = SPACES or LOW-VALUES | ERRMSG (line 23, col 1) |
| — | "User ID can NOT be empty..." | USERIDI = SPACES or LOW-VALUES | ERRMSG (line 23, col 1) |
| — | "Password can NOT be empty..." | PASSWDI = SPACES or LOW-VALUES | ERRMSG (line 23, col 1) |
| — | "User Type can NOT be empty..." | USRTYPEI = SPACES or LOW-VALUES | ERRMSG (line 23, col 1) |
| — | "User ID already exist..." | WRITE returns DUPKEY or DUPREC | ERRMSG (line 23, col 1) |
| — | "Unable to Add User..." | WRITE returns unexpected RESP code | ERRMSG (line 23, col 1) |
| — | "User XXXX has been added ..." | WRITE returns NORMAL | ERRMSG (line 23, col 1), color GREEN |
| — | "Invalid key pressed. Please see below..." | Unrecognized AID key (from CCDA-MSG-INVALID-KEY) | ERRMSG (line 23, col 1) |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CU01 transaction must be authorized for user |
| Resource-level security | RACF FACILITY / File profiles for USRSEC |
| Application-level check | If EIBCALEN = 0 (no COMMAREA — direct invocation without login), XCTL to sign-on screen COSGN00C |
| Menu-level restriction | CU01 is only accessible from Admin Menu (COADM01C), option 2 — Admin users only |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| USRSEC file | READ and UPDATE (for WRITE) | CICS File Control |

### 9.3 Audit Trail

| Event | Trigger | Data Logged | Destination |
|-------|---------|-------------|-------------|
| No explicit audit logging | — | — | Transaction may be audited via CICS journal/SMF records at the system level |
| DISPLAY commented out | Write error (OTHER branch) | WS-RESP-CD, WS-REAS-CD | Would go to SYSOUT if uncommented |

---

## 10. Performance Characteristics

### 10.1 Performance Profile

| Metric | Baseline | Peak | SLA |
|--------|----------|------|-----|
| Response time | < 1 second | < 1 second | < 2 seconds |
| CPU per transaction | Very low (validation + 1 WRITE) | — | — |
| File I/O count | 1 per add (1 WRITE to USRSEC) | — | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal | — | — |

### 10.2 Performance Considerations

- **Single WRITE per transaction**: Minimal I/O overhead. No browse operations or cross-reference lookups needed.
- **No external CALLs**: Unlike COTRN02C, COUSR01C does not call any external subroutines (no date validation utility). All validation is inline.
- **COMMAREA size**: Small; only the shared COCOM01Y portion is used. No program-specific extension.
- **BMS SEND/RECEIVE**: Standard map I/O with ERASE — one pair per pseudo-conversational iteration.
- **Sequential validation**: Uses cascading EVALUATE, so validation stops at the first error per iteration. This minimizes processing but may require multiple iterations for the user to correct all errors.

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Normal add — Regular user | First=John, Last=Doe, ID=JDOE0001, Pass=Pass1234, Type=U | "User JDOE0001 has been added ..." (green); fields cleared | High |
| TC-002 | Normal add — Admin user | First=Jane, Last=Admin, ID=JADM0001, Pass=Adm12345, Type=A | "User JADM0001 has been added ..." (green); fields cleared | High |
| TC-003 | Empty First Name | First=(blank), rest filled | "First Name can NOT be empty..."; cursor on First Name | High |
| TC-004 | Empty Last Name | First=John, Last=(blank), rest filled | "Last Name can NOT be empty..."; cursor on Last Name | High |
| TC-005 | Empty User ID | First=John, Last=Doe, ID=(blank), rest filled | "User ID can NOT be empty..."; cursor on User ID | High |
| TC-006 | Empty Password | First=John, Last=Doe, ID=TEST0001, Pass=(blank), Type=U | "Password can NOT be empty..."; cursor on Password | High |
| TC-007 | Empty User Type | First=John, Last=Doe, ID=TEST0001, Pass=Pass1234, Type=(blank) | "User Type can NOT be empty..."; cursor on User Type | High |
| TC-008 | Duplicate User ID | Use existing user ID (e.g., already added) | "User ID already exist..."; cursor on User ID | High |
| TC-009 | PF3 exit | Press PF3 at any point | Return to Admin Menu (COADM01C) | Medium |
| TC-010 | PF4 clear | Press PF4 with data on screen | All fields cleared; empty form displayed | Medium |
| TC-011 | Invalid PF key | Press PF2, PF5, PF6, etc. | "Invalid key pressed. Please see below..." | Low |
| TC-012 | Multiple consecutive adds | Add user 1, then add user 2 without exiting | Both users created; screen clears between adds | Medium |
| TC-013 | Password not visible | Enter password and check screen display | Password field displays as dark (not readable on screen) | Medium |
| TC-014 | Max-length fields | Fill all fields to maximum length (FNAME=20, LNAME=20, USERID=8, PASSWD=8, TYPE=1) | Record written with full-length values | Low |
| TC-015 | No COMMAREA (direct invoke) | Invoke CU01 without signing in | XCTL to COSGN00C (sign-on screen) | Low |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| USRSEC | Pre-loaded with at least one existing user record (for duplicate testing) | VSAM REPRO from test data (`app/data/`) |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | No validation of User Type value — any single character is accepted, not just 'A' or 'U' | Invalid user types could be stored; sign-on logic may not recognize them | Add EVALUATE to reject values other than 'A' and 'U' |
| KI-002 | Password stored in plain text in USRSEC file | Security risk — passwords are not encrypted or hashed | Implement password hashing before WRITE |
| KI-003 | No explicit HANDLE ABEND — unexpected abends use default CICS handling | User sees CICS ABEND screen rather than a friendly error | Add HANDLE ABEND with a user-friendly error screen |
| KI-004 | Validation uses cascading EVALUATE with PERFORM SEND-USRADD-SCREEN — only the first validation error is reported per iteration | User must fix errors one at a time | Accumulate all errors before displaying |
| KI-005 | DISPLAY for RESP/REAS codes in WRITE-USER-SEC-FILE is commented out | No diagnostic logging for unexpected write errors | Uncomment DISPLAY or add proper logging |
| KI-006 | No password complexity requirements — any 8-character string is accepted | Weak passwords could be set | Add password strength validation rules |
| KI-007 | No confirmation step before write — user presses ENTER and record is immediately created | Accidental submissions cannot be caught | Add a confirmation prompt similar to COTRN02C |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Screen I/O | BMS Map COUSR1A / 3270 terminal | Web form / REST API POST endpoint | Medium — field mapping is 1:1 |
| Data Access | VSAM KSDS direct (USRSEC) | Service layer with RDBMS (e.g., RDS PostgreSQL users table) | Low — simple WRITE operation |
| Password Security | Plain text storage in VSAM record | Bcrypt/Argon2 hashed passwords | Medium — requires hash-on-write and hash-compare-on-read changes across sign-on and user management |
| User Type Validation | No value validation beyond non-empty check | Enum constraint ('A', 'U') enforced at application and database level | Low |
| Validation | Inline COBOL EVALUATE cascade | Framework validation (Bean Validation, JSON Schema) | Low — validation rules are simple |
| State Management | COMMAREA pseudo-conversational | HTTP session or stateless REST with tokens | Medium |
| Authentication Integration | Separate USRSEC file with custom sign-on | Identity provider (Cognito, Okta, LDAP) | High — requires architectural change to authentication model |
| Authorization | Admin menu access control + RACF | RBAC / IAM roles | Medium |
| Audit | No explicit audit trail | Database audit log / CloudTrail | Low — add INSERT trigger or application logging |

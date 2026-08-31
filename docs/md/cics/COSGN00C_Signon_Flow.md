# Sign-on Screen (CC00) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CC00 |
| Entry Program | COSGN00C |
| Business Domain | Security / Authentication |
| Owning Team | CardDemo Application Team |
| Design Pattern | Pseudo-conversational |
| Criticality | Critical |
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

- **What**: Provides the sign-on (login) screen for the CardDemo application. Authenticates users by validating their User ID and Password against the USRSEC security file, then routes them to the appropriate menu based on their user type (Admin or Regular User).
- **Who**: All CardDemo users — this is the first screen every user sees when initiating the CC00 transaction. No prior authentication is required.
- **When**: At the start of every user session. The CC00 transaction is typically the initial transaction configured for the CICS terminal or invoked manually.
- **Why**: Enforces application-level authentication before granting access to any CardDemo functionality. Ensures only authorized users with valid credentials can access account, card, and transaction management features.
- **What** is the business outcome: Upon successful authentication, the user is transferred to the Admin Menu (COADM01C) if they are an administrator, or to the Main Menu (COMEN01C) if they are a regular user. On failure, the user remains on the sign-on screen with an appropriate error message.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  User initiates transaction CC00 at the terminal
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ System Response          │  Display sign-on screen with User ID and Password fields
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ User Action              │  Enter User ID and Password, press ENTER
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ Input Validation         │  Check User ID not empty, Password not empty
  └────────────┬─────────────┘
               │
           ┌───┴───┐
           │Decision│  Fields populated?
           └───┬───┘
              ╱ ╲
        Yes ╱     ╲ No
           ▼       ▼
  ┌──────────────┐  ┌─────────────────────┐
  │ READ USRSEC  │  │ Error: "Please      │
  │ file for     │  │ enter User ID/      │
  │ credentials  │  │ Password ..."       │
  └──────┬───────┘  └─────────────────────┘
         │
     ┌───┴───┐
     │ Found?│
     └───┬───┘
        ╱ ╲
  Yes ╱     ╲ No
     ▼       ▼
  ┌──────────┐  ┌──────────────────────┐
  │ Password │  │ "User not found.     │
  │ match?   │  │  Try again ..."      │
  └────┬─────┘  └──────────────────────┘
      ╱ ╲
Yes ╱     ╲ No
   ▼       ▼
┌───────────────────────┐  ┌──────────────────────────────────┐
│ Check User Type:      │  │ "Wrong Password.                 │
│ Admin → COADM01C      │  │  Try again ..."                  │
│ Regular → COMEN01C    │  └──────────────────────────────────┘
└───────────────────────┘
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | User ID is required | User ID field is empty (spaces or low-values) | Error: "Please enter User ID ..." |
| BR-002 | Password is required | Password field is empty (spaces or low-values) | Error: "Please enter Password ..." |
| BR-003 | User must exist in security file | User ID not found in USRSEC VSAM file (RESP=13 NOTFND) | Error: "User not found. Try again ..." |
| BR-004 | Password must match stored credential | Password entered does not match SEC-USR-PWD in USRSEC record | Error: "Wrong Password. Try again ..." |
| BR-005 | Admin users route to Admin Menu | SEC-USR-TYPE = 'A' (CDEMO-USRTYP-ADMIN) | XCTL to COADM01C (Admin Menu) |
| BR-006 | Regular users route to Main Menu | SEC-USR-TYPE = 'U' (CDEMO-USRTYP-USER) | XCTL to COMEN01C (Main Menu) |
| BR-007 | User ID is uppercased before lookup | User enters any case | FUNCTION UPPER-CASE applied to User ID input |
| BR-008 | Password is uppercased before comparison | User enters any case | FUNCTION UPPER-CASE applied to Password input |
| BR-009 | PF3 exits the application | User presses PF3 at sign-on screen | Display "Thank you for using CardDemo application..." and RETURN (end session) |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Initiate transaction CC00 | Display sign-on screen (COSGN0A) with empty User ID and Password fields, cursor on User ID | Application entry point |
| 2 | Enter User ID and Password, press ENTER | Validate fields, read USRSEC file, check password | Authentication attempt |
| 3a | Credentials valid, user is Admin | XCTL to COADM01C (Admin Menu) with COMMAREA populated | Admin session begins |
| 3b | Credentials valid, user is Regular | XCTL to COMEN01C (Main Menu) with COMMAREA populated | Regular user session begins |
| 3c | Credentials invalid | Redisplay sign-on screen with error message, cursor on relevant field | Retry authentication |
| Alt-A | Press PF3 | Display "Thank you for using CardDemo application..." text, session ends | Graceful exit |
| Alt-B | Press any other key | Redisplay sign-on screen with "Invalid key pressed. Please see below..." | Invalid action |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CC00
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COSGN00C     │ (Entry & only online program)      │
  │  │ (Sign-on)    │                                    │
  │  └──┬────┬──┬───┘                                    │
  │     │    │  │                                         │
  │     ▼    │  ▼                                         │
  │  ┌──────┐│ ┌────────────────────────────────────┐    │
  │  │BMS   ││ │ VSAM File                          │    │
  │  │MAP:  ││ │  USRSEC (User Security master)     │    │
  │  │COSGN ││ │                                     │    │
  │  │0A    ││ └────────────────────────────────────┘    │
  │  └──────┘│                                           │
  │          │                                            │
  │          ▼                                            │
  │  ┌─────────────┐    ┌─────────────┐                  │
  │  │ COADM01C    │    │ COMEN01C    │                  │
  │  │ (Admin Menu)│    │ (Main Menu) │                  │
  │  └─────────────┘    └─────────────┘                  │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1 (First entry — EIBCALEN = 0):
  1. Terminal sends TRANSID CC00 → CICS dispatches COSGN00C
  2. EIBCALEN = 0 (no COMMAREA — first invocation)
  3. SET ERR-FLG-OFF TO TRUE
  4. MOVE SPACES TO WS-MESSAGE and ERRMSGO
  5. MOVE LOW-VALUES TO COSGN0AO (clear all output fields)
  6. MOVE -1 TO USERIDL (set cursor to User ID field)
  7. PERFORM SEND-SIGNON-SCREEN
     ├── PERFORM POPULATE-HEADER-INFO
     │   ├── FUNCTION CURRENT-DATE → WS-CURDATE-DATA
     │   ├── CCDA-TITLE01 → TITLE01O ("AWS Mainframe Modernization")
     │   ├── CCDA-TITLE02 → TITLE02O ("CardDemo")
     │   ├── WS-TRANID → TRNNAMEO ("CC00")
     │   ├── WS-PGMNAME → PGMNAMEO ("COSGN00C")
     │   ├── Format date → CURDATEO (mm/dd/yy)
     │   ├── Format time → CURTIMEO (hh:mm:ss)
     │   ├── CICS ASSIGN APPLID → APPLIDO
     │   └── CICS ASSIGN SYSID → SYSIDO
     └── SEND MAP('COSGN0A') MAPSET('COSGN00') ERASE CURSOR
  8. RETURN TRANSID('CC00') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 2+ (User submits credentials — ENTER pressed):
  9.  CICS dispatches → COSGN00C (COMMAREA restored)
  10. EIBCALEN > 0
  11. SET ERR-FLG-OFF TO TRUE
  12. MOVE SPACES TO WS-MESSAGE and ERRMSGO
  13. EIBAID = DFHENTER → PERFORM PROCESS-ENTER-KEY
  14. RECEIVE MAP('COSGN0A') MAPSET('COSGN00')
  15. EVALUATE TRUE:
      ├── USERIDI = SPACES/LOW-VALUES → error "Please enter User ID ..."
      │   └── MOVE -1 TO USERIDL, PERFORM SEND-SIGNON-SCREEN
      ├── PASSWDI = SPACES/LOW-VALUES → error "Please enter Password ..."
      │   └── MOVE -1 TO PASSWDL, PERFORM SEND-SIGNON-SCREEN
      └── OTHER → CONTINUE (both fields populated)
  16. UPPER-CASE(USERIDI) → WS-USER-ID, CDEMO-USER-ID
  17. UPPER-CASE(PASSWDI) → WS-USER-PWD
  18. IF NOT ERR-FLG-ON → PERFORM READ-USER-SEC-FILE
  19. RETURN TRANSID('CC00') COMMAREA(CARDDEMO-COMMAREA)

  READ-USER-SEC-FILE:
  20. READ DATASET('USRSEC') INTO(SEC-USER-DATA) RIDFLD(WS-USER-ID)
  21. EVALUATE WS-RESP-CD:
      ├── 0 (NORMAL) → User found
      │   ├── SEC-USR-PWD = WS-USER-PWD → Password matches
      │   │   ├── MOVE WS-TRANID → CDEMO-FROM-TRANID
      │   │   ├── MOVE WS-PGMNAME → CDEMO-FROM-PROGRAM
      │   │   ├── MOVE WS-USER-ID → CDEMO-USER-ID
      │   │   ├── MOVE SEC-USR-TYPE → CDEMO-USER-TYPE
      │   │   ├── MOVE ZEROS → CDEMO-PGM-CONTEXT
      │   │   ├── IF CDEMO-USRTYP-ADMIN:
      │   │   │   └── XCTL PROGRAM('COADM01C') COMMAREA(CARDDEMO-COMMAREA)
      │   │   └── ELSE:
      │   │       └── XCTL PROGRAM('COMEN01C') COMMAREA(CARDDEMO-COMMAREA)
      │   └── SEC-USR-PWD ≠ WS-USER-PWD → "Wrong Password. Try again ..."
      │       └── MOVE -1 TO PASSWDL, PERFORM SEND-SIGNON-SCREEN
      ├── 13 (NOTFND) → "User not found. Try again ..."
      │   └── MOVE -1 TO USERIDL, PERFORM SEND-SIGNON-SCREEN
      └── OTHER → "Unable to verify the User ..."
          └── MOVE -1 TO USERIDL, PERFORM SEND-SIGNON-SCREEN

  PF3 Pressed:
  22. MOVE CCDA-MSG-THANK-YOU → WS-MESSAGE
  23. PERFORM SEND-PLAIN-TEXT
      ├── SEND TEXT FROM(WS-MESSAGE) ERASE FREEKB
      └── RETURN (no TRANSID — session ends)
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | 0 | Initiate CC00 | MAIN-PARA (EIBCALEN=0 branch) | COSGN0A (empty sign-on form) | CC00 |
| 2 | >0 | Enter User ID + Password, press ENTER | PROCESS-ENTER-KEY | COSGN0A (with error) or XCTL to menu | CC00 (if error) |
| N | >0 | PF3 (Exit) | SEND-PLAIN-TEXT → RETURN (no TRANSID) | Plain text "Thank you..." | — (session ends) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | RECEIVE MAP('COSGN0A') MAPSET('COSGN00') | Capture user input (User ID and Password) from terminal | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 2 | READ DATASET('USRSEC') INTO(SEC-USER-DATA) RIDFLD(WS-USER-ID) KEYLENGTH(LENGTH OF WS-USER-ID) | Look up user record by User ID | NORMAL(0) → validate password; NOTFND(13) → error; OTHER → error |
| 3 | SEND MAP('COSGN0A') MAPSET('COSGN00') FROM(COSGN0AO) ERASE CURSOR | Send sign-on screen to terminal | — |
| 4 | SEND TEXT FROM(WS-MESSAGE) LENGTH(80) ERASE FREEKB | Send plain-text "Thank you" message on PF3 | — |
| 5 | ASSIGN APPLID(APPLIDO) | Retrieve CICS application ID for header display | — |
| 6 | ASSIGN SYSID(SYSIDO) | Retrieve CICS system ID for header display | — |
| 7 | XCTL PROGRAM('COADM01C') COMMAREA(CARDDEMO-COMMAREA) | Transfer to Admin Menu on successful admin login | — |
| 8 | XCTL PROGRAM('COMEN01C') COMMAREA(CARDDEMO-COMMAREA) | Transfer to Main Menu on successful regular user login | — |
| 9 | RETURN TRANSID('CC00') COMMAREA(CARDDEMO-COMMAREA) LENGTH(LENGTH OF CARDDEMO-COMMAREA) | Pseudo-conversational return (re-enter on next input) | — |
| 10 | RETURN (no TRANSID) | End session after PF3 exit | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────────────────────────────────────────────────────────────────┐
  │  COSGN0A (Sign-on Screen)                                              │
  │                                                                        │
  │  Tran: CC00    AWS Mainframe Modernization        Date: mm/dd/yy       │
  │  Prog: COSGN00C         CardDemo                  Time: hh:mm:ss       │
  │  AppID: xxxxxxxx                                  SysID: xxxx          │
  │                                                                        │
  │    This is a Credit Card Demo Application for Mainframe Modernization  │
  │                                                                        │
  │               +========================================+               │
  │               |%%%%%%%  NATIONAL RESERVE NOTE  %%%%%%%%|               │
  │               |%(1)  THE UNITED STATES OF KICSLAND (1)%|               │
  │               |%$$              ___       ********  $$%|               │
  │               |%$    {x}       (o o)                 $%|               │
  │               |%$     ******  (  V  )      O N E     $%|               │
  │               |%(1)          ---m-m---             (1)%|               │
  │               |%%~~~~~~~~~~~ ONE DOLLAR ~~~~~~~~~~~~~%%|               │
  │               +========================================+               │
  │                                                                        │
  │          Type your User ID and Password, then press ENTER:             │
  │                                                                        │
  │                          User ID     : ________ (8 Char)               │
  │                          Password    : ________ (8 Char)               │
  │                                                                        │
  │  [Error/Success message line — 78 chars]                               │
  │  ENTER=Sign-on  F3=Exit                                                │
  └─────────────────────────────────────────────────────────────────────────┘
       │              │
       │ ENTER        │ PF3
       │ (auth loop)  │ (exit)
       ▼              ▼
  ┌──────────────┐  ┌──────────────────────────────────┐
  │ On success:  │  │ Plain text:                      │
  │ Admin →      │  │ "Thank you for using CardDemo    │
  │  COADM01C    │  │  application..."                 │
  │ User →       │  │ (Session ends)                   │
  │  COMEN01C    │  └──────────────────────────────────┘
  └──────────────┘
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| COSGN0A | COSGN00 | Sign-on (login) screen — captures User ID and Password | User ID, Password | ENTER=Sign-on, PF3=Exit |

### 3.3 Screen Field Details

#### Map: COSGN0A

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction name (CC00) |
| TITLE01 | ASKIP,FSET,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,FSET,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | FSET,NORM,PROT | 8 | Alpha | — | Program name (COSGN00C) |
| TITLE02 | ASKIP,FSET,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | FSET,NORM,PROT | 9 | Time | — | Current time hh:mm:ss |
| APPLID | FSET,NORM,PROT | 8 | Alpha | — | CICS Application ID (from ASSIGN APPLID) |
| SYSID | FSET,NORM,PROT | 8 | Alpha | — | CICS System ID (from ASSIGN SYSID) |
| USERID | FSET,IC,NORM,UNPROT | 8 | Alpha | Non-empty (spaces/low-values rejected) | User ID for authentication |
| PASSWD | DRK,FSET,UNPROT | 8 | Alpha | Non-empty (spaces/low-values rejected) | Password (dark attribute — not displayed) |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/success message display area |

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | COSGN0A | Validate User ID and Password; read USRSEC file; authenticate and route to menu |
| PF3 | COSGN0A | Display "Thank you for using CardDemo application..." via SEND TEXT, then RETURN (session ends) |
| OTHER | COSGN0A | Set ERR-FLG, display "Invalid key pressed. Please see below...", redisplay sign-on screen |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COSGN00C | Sign-on screen handler — input validation, USRSEC file lookup, credential verification, routing to menu | TRANSID CC00 | 260 | `app/cbl/COSGN00C.cbl` |
| COADM01C | Admin Menu — target for admin user login | XCTL from COSGN00C | — | `app/cbl/COADM01C.cbl` |
| COMEN01C | Main Menu — target for regular user login | XCTL from COSGN00C | — | `app/cbl/COMEN01C.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CC00
└── COSGN00C (Entry — Sign-on Screen Handler)
    ├── RECEIVE MAP('COSGN0A') — Capture User ID and Password
    ├── READ FILE('USRSEC') — User security record lookup
    ├── SEND MAP('COSGN0A') — Display sign-on screen (with errors or initial)
    ├── SEND TEXT — Display "Thank you" message (PF3 exit)
    ├── ASSIGN APPLID — Get CICS application ID for header
    ├── ASSIGN SYSID — Get CICS system ID for header
    ├── XCTL → COADM01C (Admin Menu — on successful admin login)
    ├── XCTL → COMEN01C (Main Menu — on successful regular user login)
    ├── RETURN TRANSID('CC00') — Pseudo-conversational return
    └── RETURN (no TRANSID) — End session (PF3)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COSGN00C | COADM01C | XCTL | CARDDEMO-COMMAREA (populated with FROM-TRANID, FROM-PROGRAM, USER-ID, USER-TYPE='A', PGM-CONTEXT=0) | — | Successful admin login |
| COSGN00C | COMEN01C | XCTL | CARDDEMO-COMMAREA (populated with FROM-TRANID, FROM-PROGRAM, USER-ID, USER-TYPE='U', PGM-CONTEXT=0) | — | Successful regular user login |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| User Security Master | USRSEC | VSAM KSDS | READ | COSGN00C | Authenticate user credentials (User ID, Password, User Type) |

### 5.2 File Access Details

#### File: USRSEC (User Security Master)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CSUSR01Y (SEC-USER-DATA, 80 bytes) |
| Key | SEC-USR-ID (PIC X(08)) — User ID |
| Access Mode | READ |
| Locking | No update, read-only |
| Shared With | COUSR00C (User List), COUSR01C (User Add), COUSR02C (User Update), COUSR03C (User Delete) |

**Record Layout (CSUSR01Y):**

| Field | PIC | Offset | Length | Purpose |
|-------|-----|--------|--------|---------|
| SEC-USR-ID | X(08) | 0 | 8 | User ID (key) |
| SEC-USR-FNAME | X(20) | 8 | 20 | First Name |
| SEC-USR-LNAME | X(20) | 28 | 20 | Last Name |
| SEC-USR-PWD | X(08) | 48 | 8 | Password (plaintext) |
| SEC-USR-TYPE | X(01) | 56 | 1 | User Type ('A'=Admin, 'U'=User) |
| SEC-USR-FILLER | X(23) | 57 | 23 | Reserved filler |

**Access Pattern:**

| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COSGN00C | READ DATASET('USRSEC') INTO(SEC-USER-DATA) RIDFLD(WS-USER-ID) KEYLENGTH(LENGTH OF WS-USER-ID) | WS-USER-ID (upper-cased User ID from screen) | SEC-USR-PWD (password comparison), SEC-USR-TYPE (routing decision) |

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
  Terminal Input (COSGN0A)
       │
       ├── USERIDI (User ID — 8 chars)
       │       │
       │       └──▶ FUNCTION UPPER-CASE
       │                 │
       │                 ├──▶ WS-USER-ID (PIC X(08)) — READ key
       │                 │         │
       │                 │         └──▶ READ USRSEC → SEC-USER-DATA
       │                 │                   │
       │                 │                   ├──▶ SEC-USR-PWD → compared with WS-USER-PWD
       │                 │                   │
       │                 │                   └──▶ SEC-USR-TYPE → CDEMO-USER-TYPE
       │                 │                              │
       │                 │                              ├── 'A' → XCTL COADM01C
       │                 │                              └── 'U' → XCTL COMEN01C
       │                 │
       │                 └──▶ CDEMO-USER-ID (COMMAREA field)
       │
       └── PASSWDI (Password — 8 chars, dark attribute)
               │
               └──▶ FUNCTION UPPER-CASE
                         │
                         └──▶ WS-USER-PWD (PIC X(08))
                                   │
                                   └──▶ Compared with SEC-USR-PWD from USRSEC record

  COMMAREA Population (on successful login):
       WS-TRANID ('CC00')          → CDEMO-FROM-TRANID
       WS-PGMNAME ('COSGN00C')    → CDEMO-FROM-PROGRAM
       WS-USER-ID                  → CDEMO-USER-ID
       SEC-USR-TYPE                → CDEMO-USER-TYPE
       ZEROS                       → CDEMO-PGM-CONTEXT (first entry into menu)

  Header Population (POPULATE-HEADER-INFO):
       FUNCTION CURRENT-DATE       → WS-CURDATE-DATA
       WS-CURDATE-MONTH/DAY/YEAR  → CURDATEO (mm/dd/yy)
       WS-CURTIME-HOURS/MIN/SEC   → CURTIMEO (hh:mm:ss)
       CCDA-TITLE01                → TITLE01O
       CCDA-TITLE02                → TITLE02O
       WS-TRANID                   → TRNNAMEO
       WS-PGMNAME                  → PGMNAMEO
       CICS ASSIGN APPLID          → APPLIDO
       CICS ASSIGN SYSID           → SYSIDO
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| USERIDI | Terminal (COSGN0AI) | FUNCTION UPPER-CASE | WS-USER-ID | Working Storage — READ key for USRSEC |
| USERIDI | Terminal (COSGN0AI) | FUNCTION UPPER-CASE | CDEMO-USER-ID | CARDDEMO-COMMAREA |
| PASSWDI | Terminal (COSGN0AI) | FUNCTION UPPER-CASE | WS-USER-PWD | Working Storage — compared with SEC-USR-PWD |
| SEC-USR-TYPE | USRSEC record (CSUSR01Y) | Direct move | CDEMO-USER-TYPE | CARDDEMO-COMMAREA |
| WS-TRANID | Working Storage literal 'CC00' | Direct move | CDEMO-FROM-TRANID | CARDDEMO-COMMAREA |
| WS-PGMNAME | Working Storage literal 'COSGN00C' | Direct move | CDEMO-FROM-PROGRAM | CARDDEMO-COMMAREA |
| ZEROS | Literal | Direct move | CDEMO-PGM-CONTEXT | CARDDEMO-COMMAREA (signals first entry to target menu) |
| FUNCTION CURRENT-DATE | System intrinsic | Extract month/day/year, reformat | CURDATEO | Screen output (mm/dd/yy) |
| FUNCTION CURRENT-DATE | System intrinsic | Extract hours/minutes/seconds | CURTIMEO | Screen output (hh:mm:ss) |
| CCDA-TITLE01 | COTTL01Y copybook | Direct move | TITLE01O | Screen output header line 1 |
| CCDA-TITLE02 | COTTL01Y copybook | Direct move | TITLE02O | Screen output header line 2 |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`). COSGN00C does not define a program-specific extension — it only populates the general-info fields before transferring control.

**CARDDEMO-COMMAREA (COCOM01Y — shared portion):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-FROM-TRANID | X(04) | 4 | OUT | Set to 'CC00' — identifies sign-on as originating transaction |
| CDEMO-FROM-PROGRAM | X(08) | 8 | OUT | Set to 'COSGN00C' — identifies sign-on as originating program |
| CDEMO-TO-TRANID | X(04) | 4 | — | Not set by COSGN00C (populated by target program) |
| CDEMO-TO-PROGRAM | X(08) | 8 | — | Not set by COSGN00C (populated by target program) |
| CDEMO-USER-ID | X(08) | 8 | OUT | Authenticated user ID (upper-cased) |
| CDEMO-USER-TYPE | X(01) | 1 | OUT | 'A' = Admin, 'U' = User (from USRSEC record) |
| CDEMO-PGM-CONTEXT | 9(01) | 1 | OUT | Set to 0 (CDEMO-PGM-ENTER) — signals first entry to target menu |
| CDEMO-CUST-ID | 9(09) | 9 | — | Not set by COSGN00C |
| CDEMO-ACCT-ID | 9(11) | 11 | — | Not set by COSGN00C |
| CDEMO-CARD-NUM | 9(16) | 16 | — | Not set by COSGN00C |
| CDEMO-LAST-MAP | X(7) | 7 | — | Not set by COSGN00C |
| CDEMO-LAST-MAPSET | X(7) | 7 | — | Not set by COSGN00C |

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. Authentication result is communicated implicitly:
- **Success**: XCTL to target menu program (no return to COSGN00C)
- **Failure**: Screen redisplayed with error message (WS-MESSAGE → ERRMSGO)

### 7.3 Conversation State Management

- **COMMAREA preservation**: The entire CARDDEMO-COMMAREA is passed on every RETURN TRANSID('CC00'). CICS restores it automatically on the next dispatch. However, the COMMAREA is primarily used as an output contract — COSGN00C populates it only on successful login before the XCTL.
- **First invocation detection**: EIBCALEN = 0 indicates the very first entry (no COMMAREA exists yet). The program displays a blank sign-on screen.
- **Re-entry detection**: EIBCALEN > 0 indicates a return from a pseudo-conversational wait. The program processes user input (ENTER, PF3, or other keys).
- **No PGM-CONTEXT usage**: Unlike other CardDemo programs, COSGN00C does not use CDEMO-PGM-CONTEXT to distinguish first-entry vs. re-entry. It uses EIBCALEN = 0 for first entry detection.
- **Screen field state**: BMS FSET attribute ensures the USERID field value is returned on RECEIVE. The PASSWD field also uses FSET but with DRK (dark) attribute so the password is not displayed.
- **No TSQ overflow**: All state fits within the COMMAREA; no temporary storage queues are used.

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path (Successful Login):
    Input → Validate User ID → Validate Password → READ USRSEC →
    Password Match → XCTL to Menu (COADM01C or COMEN01C)

  Empty User ID:
    Input → USERIDI = SPACES/LOW-VALUES ──▶ "Please enter User ID ..."
                                            → Cursor to User ID, RETURN TRANSID

  Empty Password:
    Input → PASSWDI = SPACES/LOW-VALUES ──▶ "Please enter Password ..."
                                            → Cursor to Password, RETURN TRANSID

  User Not Found:
    Input → READ USRSEC ──NOTFND(13)──▶ "User not found. Try again ..."
                                        → Cursor to User ID, RETURN TRANSID

  Wrong Password:
    Input → READ USRSEC ──NORMAL──▶ SEC-USR-PWD ≠ WS-USER-PWD
                                    → "Wrong Password. Try again ..."
                                    → Cursor to Password, RETURN TRANSID

  Unexpected File Error:
    Input → READ USRSEC ──OTHER──▶ "Unable to verify the User ..."
                                   → Cursor to User ID, RETURN TRANSID

  Invalid Key:
    Input → EIBAID ≠ DFHENTER and ≠ DFHPF3 ──▶ "Invalid key pressed.
                                                  Please see below..."
                                                → RETURN TRANSID

  PF3 Exit:
    PF3 → "Thank you for using CardDemo application..." via SEND TEXT
        → RETURN (no TRANSID — session ends)
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| User ID field empty | — (input validation) | Set ERR-FLG, cursor to USERID | "Please enter User ID ..." | — |
| Password field empty | — (input validation) | Set ERR-FLG, cursor to PASSWD | "Please enter Password ..." | — |
| User not found in USRSEC | NOTFND (13) | Set ERR-FLG, cursor to USERID | "User not found. Try again ..." | — |
| Wrong password | NORMAL (0) but pwd mismatch | Cursor to PASSWD | "Wrong Password. Try again ..." | — |
| Unexpected USRSEC read error | OTHER (any non-0, non-13) | Set ERR-FLG, cursor to USERID | "Unable to verify the User ..." | — |
| Invalid AID key pressed | — (EIBAID check) | Set ERR-FLG | "Invalid key pressed. Please see below..." (CCDA-MSG-INVALID-KEY) | — |
| Map RECEIVE error | RESP in WS-RESP-CD | Not explicitly handled beyond RESP capture | — | — |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| — | No explicit HANDLE ABEND | Default CICS abend handling applies |

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "Please enter User ID ..." | USERIDI is spaces or low-values | ERRMSG (line 23, col 1) |
| — | "Please enter Password ..." | PASSWDI is spaces or low-values | ERRMSG (line 23, col 1) |
| — | "User not found. Try again ..." | READ USRSEC returns NOTFND (RESP=13) | ERRMSG (line 23, col 1) |
| — | "Wrong Password. Try again ..." | SEC-USR-PWD does not match WS-USER-PWD | ERRMSG (line 23, col 1) |
| — | "Unable to verify the User ..." | READ USRSEC returns unexpected RESP code | ERRMSG (line 23, col 1) |
| — | "Invalid key pressed. Please see below..." | EIBAID is not DFHENTER or DFHPF3 (from CCDA-MSG-INVALID-KEY in CSMSG01Y) | ERRMSG (line 23, col 1) |
| — | "Thank you for using CardDemo application..." | User presses PF3 (from CCDA-MSG-THANK-YOU in CSMSG01Y) | Plain text via SEND TEXT (full screen, not in map) |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CC00 transaction must be authorized for terminal/user |
| Resource-level security | RACF FACILITY / File profiles for USRSEC |
| Application-level authentication | COSGN00C performs its own user/password validation against the USRSEC VSAM file. This is an application-level check, separate from CICS RACF security |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| USRSEC file | READ | CICS File Control |

### 9.3 Audit Trail

| Event | Trigger | Data Logged | Destination |
|-------|---------|-------------|-------------|
| No explicit audit logging | — | — | Transaction may be audited via CICS journal/SMF records at the system level |
| Failed login attempts | Not explicitly logged by program | — | No application-level logging of failed authentication attempts |

### 9.4 Security Concerns

| ID | Concern | Description |
|----|---------|-------------|
| SC-001 | Plaintext password storage | Passwords are stored in plaintext in the USRSEC VSAM file (SEC-USR-PWD field). No hashing or encryption is applied. |
| SC-002 | Plaintext password comparison | Password comparison is done as a simple string match: `IF SEC-USR-PWD = WS-USER-PWD` |
| SC-003 | No account lockout | No mechanism to lock accounts after repeated failed login attempts |
| SC-004 | No failed login logging | Failed authentication attempts are not logged for security auditing |
| SC-005 | Password displayed in COMMAREA | WS-USER-PWD remains in working storage but is not passed in COMMAREA — acceptable for single-session scope |

---

## 10. Performance Characteristics

### 10.1 Performance Profile

| Metric | Baseline | Peak | SLA |
|--------|----------|------|-----|
| Response time | < 1 second | < 1 second | < 2 seconds |
| CPU per transaction | Very low (1 READ + comparison) | — | — |
| File I/O count | 1 per login attempt (1 READ to USRSEC) | — | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal | — | — |

### 10.2 Performance Considerations

- **Single READ per attempt**: Only one VSAM READ is performed per login attempt — minimal I/O overhead.
- **No browse operations**: Unlike transaction-oriented programs, there are no STARTBR/READNEXT patterns.
- **COMMAREA size**: Standard CARDDEMO-COMMAREA shared structure; no program-specific extension. Compact and efficient.
- **BMS SEND/RECEIVE**: Standard map I/O with ERASE — one pair per pseudo-conversational iteration.
- **XCTL on success**: On successful login, control transfers immediately to the target menu program via XCTL (no additional I/O).
- **UPPER-CASE function**: Two FUNCTION UPPER-CASE calls per submit — negligible overhead.

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Successful login as regular user | Valid User ID + correct Password (type='U') | XCTL to COMEN01C (Main Menu) | High |
| TC-002 | Successful login as admin user | Valid User ID + correct Password (type='A') | XCTL to COADM01C (Admin Menu) | High |
| TC-003 | Empty User ID | Leave User ID blank, enter Password, press ENTER | "Please enter User ID ..." cursor on User ID | High |
| TC-004 | Empty Password | Enter valid User ID, leave Password blank, press ENTER | "Please enter Password ..." cursor on Password | High |
| TC-005 | Both fields empty | Leave both blank, press ENTER | "Please enter User ID ..." cursor on User ID (first validation wins) | Medium |
| TC-006 | User not found | Enter non-existent User ID + any Password | "User not found. Try again ..." cursor on User ID | High |
| TC-007 | Wrong password | Enter valid User ID + incorrect Password | "Wrong Password. Try again ..." cursor on Password | High |
| TC-008 | Case-insensitive User ID | Enter User ID in lowercase | Should match USRSEC record (UPPER-CASE applied) | Medium |
| TC-009 | Case-insensitive Password | Enter Password in mixed case | Should match after UPPER-CASE applied | Medium |
| TC-010 | PF3 exit | Press PF3 at sign-on screen | "Thank you for using CardDemo application..." session ends | Medium |
| TC-011 | Invalid PF key | Press PF1, PF2, PF4, etc. | "Invalid key pressed. Please see below..." | Low |
| TC-012 | CLEAR key | Press CLEAR key | "Invalid key pressed. Please see below..." | Low |
| TC-013 | First invocation (EIBCALEN=0) | Initiate CC00 transaction | Empty sign-on screen displayed, cursor on User ID | High |
| TC-014 | Re-entry after error | Submit bad credentials, then correct credentials | First iteration shows error, second iteration succeeds | Medium |
| TC-015 | USRSEC file unavailable | USRSEC file closed or unavailable | "Unable to verify the User ..." | Low |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| USRSEC | Pre-loaded with at least one admin user (SEC-USR-TYPE='A') and one regular user (SEC-USR-TYPE='U') with known passwords | VSAM REPRO from test data (`app/data/`) |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | Passwords stored in plaintext in USRSEC VSAM file | Security vulnerability — passwords can be read by anyone with file access | Implement password hashing (e.g., one-way hash before storage and comparison) |
| KI-002 | No account lockout after failed login attempts | Susceptible to brute-force password guessing | Implement a failed-attempt counter in USRSEC record with lockout threshold |
| KI-003 | No failed login audit trail | Security events are not logged for forensic analysis | Add WRITE to an audit log file or TDQ on failed authentication |
| KI-004 | No explicit HANDLE ABEND — unexpected abends use default CICS handling | User sees CICS ABEND screen rather than a friendly error | Add HANDLE ABEND with a user-friendly error screen |
| KI-005 | Password field uses DRK attribute but no additional protection | Password is not visible on screen but is transmitted in clear text over the 3270 data stream | Use encrypted TN3270E sessions (SSL/TLS) |
| KI-006 | UPPER-CASE forced on both User ID and Password | Passwords are case-insensitive, reducing the keyspace for password security | Allow case-sensitive password comparison |
| KI-007 | COMMAREA is initialized but not fully cleared before XCTL | Residual data from previous sessions could theoretically persist in uninitialized COMMAREA fields | Explicitly INITIALIZE CARDDEMO-COMMAREA before populating fields |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Authentication | Plaintext password in USRSEC VSAM file, application-level check | OAuth 2.0 / OIDC, LDAP/AD integration, or JWT-based authentication | High — requires identity provider integration |
| Password Storage | Plaintext PIC X(08) in VSAM record | Hashed + salted storage (bcrypt, Argon2) | Medium — schema change + hash logic |
| Screen I/O | BMS Map COSGN0A / 3270 terminal | Web login form / REST API POST `/auth/login` | Medium — field mapping is straightforward |
| Data Access | VSAM KSDS READ on USRSEC file | Service layer with RDBMS user table (e.g., RDS PostgreSQL `users` table) | Low — single READ operation |
| Session Management | COMMAREA-based identity propagation via XCTL | HTTP session cookies, JWT tokens, or server-side session store | Medium — requires session infrastructure |
| Security Hardening | No lockout, no audit, no MFA | Account lockout policy, audit logging, multi-factor authentication | High — cross-cutting concern |
| User Routing | XCTL to COADM01C (Admin) or COMEN01C (User) based on SEC-USR-TYPE | Role-based access control (RBAC) with permission grants | Medium — role model needs design |
| State Management | EIBCALEN=0 first-entry detection, COMMAREA pseudo-conversational | Stateless REST or HTTP session-based authentication | Low |

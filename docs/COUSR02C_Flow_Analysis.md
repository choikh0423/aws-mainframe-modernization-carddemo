# CU02 Transaction Flow Analysis — COUSR02C (User Update - Security/Admin)

## Section 1: Flow Overview

| Attribute            | Value                                      |
|----------------------|--------------------------------------------|
| **Transaction ID**   | CU02                                       |
| **Entry Program**    | COUSR02C                                   |
| **Function**         | Update an existing user in the USRSEC file |
| **Transaction Type** | CICS Online — Pseudo-conversational        |
| **Data Access**      | VSAM KSDS (USRSEC)                         |
| **Security**         | Admin users only (User-Type = 'A')         |

### Complete Navigation Path

```
 ┌──────────────────────────────────────────────────────────────────┐
 │                     CardDemo Navigation Path                     │
 └──────────────────────────────────────────────────────────────────┘

 ┌─────────────┐   XCTL    ┌─────────────┐   XCTL    ┌─────────────┐
 │  COSGN00C   │──────────>│  COADM01C   │──────────>│  COUSR00C   │
 │  Tran: CC00 │ (Admin)   │  Tran: CA00 │ (Opt 1)  │  Tran: CU00 │
 │  Sign-on    │           │  Admin Menu │           │  User List  │
 └─────────────┘           └─────────────┘           └──────┬──────┘
                                                            │
                                                     XCTL   │ (Select 'U')
                                                            │
                                                     ┌──────▼──────┐
                                                     │  COUSR02C   │
                                                     │  Tran: CU02 │
                                                     │ User Update │
                                                     └─────────────┘
```

**Step-by-step navigation:**

1. **COSGN00C** (CC00) — User signs on with admin credentials
2. **COADM01C** (CA00) — Admin menu is displayed; admin selects option **1** ("User List (Security)")
3. **COUSR00C** (CU00) — User list screen shows paginated USRSEC records; admin selects a user with flag **'U'**
4. **COUSR02C** (CU02) — User update screen displays selected user's details for editing

All program transfers use **EXEC CICS XCTL** with COMMAREA.

### Pseudo-Conversational Lifecycle

```
  ┌──────────────────────────────────────────────────────────────────────┐
  │              COUSR02C Pseudo-Conversational Lifecycle                │
  └──────────────────────────────────────────────────────────────────────┘

  ┌───────────────────┐
  │ XCTL from         │
  │ COUSR00C          │
  │ (PGM-CONTEXT = 0) │
  └────────┬──────────┘
           │
           ▼
  ┌────────────────────────────────┐
  │  FIRST ENTRY (PGM-ENTER)      │
  │  1. Set PGM-CONTEXT = 1       │
  │  2. Initialize screen fields   │
  │  3. If user pre-selected:      │
  │     - READ USRSEC for user     │
  │     - Populate screen fields   │
  │  4. SEND MAP COUSR2A           │
  │  5. RETURN TRANSID('CU02')    │
  └────────────────────────────────┘
           │
           ▼  (CICS suspends task; terminal waits for input)
           │
  ┌────────────────────────────────┐
  │  RE-ENTRY (PGM-REENTER)       │
  │  1. RECEIVE MAP COUSR2A       │
  │  2. EVALUATE EIBAID:          │
  │     - ENTER: Fetch user data   │
  │     - PF3:   Save & Exit       │
  │     - PF4:   Clear screen      │
  │     - PF5:   Save updates      │
  │     - PF12:  Cancel (go back)  │
  │     - Other: Invalid key msg   │
  │  3. SEND MAP / XCTL / RETURN  │
  └────────────────────────────────┘
           │
           ▼
  (Loop continues until PF3, PF12, or EIBCALEN=0 redirect)
```

---

## Section 2: Programs Involved

| Program    | Type               | Function                        | Called By  | Call Method | Source Path          |
|------------|--------------------|---------------------------------|------------|-------------|----------------------|
| COSGN00C   | CICS Online        | Sign-on / Authentication        | (entry)    | —           | `app/cbl/COSGN00C.cbl` |
| COADM01C   | CICS Online        | Admin Menu                      | COSGN00C   | XCTL        | `app/cbl/COADM01C.cbl` |
| COUSR00C   | CICS Online        | User List (Security)            | COADM01C   | XCTL        | `app/cbl/COUSR00C.cbl` |
| **COUSR02C** | **CICS Online**  | **User Update (Security/Admin)**| COUSR00C   | XCTL        | `app/cbl/COUSR02C.cbl` |

**Notes:**
- All transfers use **XCTL** (permanent control transfer; caller removed from chain)
- No **LINK** or **CALL** statements exist in COUSR02C
- No Language Environment intrinsics or external sub-programs are called
- COUSR02C can XCTL back to **COADM01C** (PF3/PF12) or **COSGN00C** (EIBCALEN=0 guard)

---

## Section 3: Copybooks

| Copybook   | Used By             | Type                    | Purpose                                           |
|------------|---------------------|-------------------------|----------------------------------------------------|
| COCOM01Y   | COUSR02C, COSGN00C, COADM01C, COUSR00C | COMMAREA definition | Navigation fields, customer/account/card info, program context |
| COUSR02    | COUSR02C            | BMS symbolic map        | Auto-generated input/output map structures for COUSR2A screen (COUSR2AI / COUSR2AO) |
| COTTL01Y   | COUSR02C, COSGN00C, COADM01C, COUSR00C | Screen title/message | Application title constants (CCDA-TITLE01, CCDA-TITLE02) |
| CSDAT01Y   | COUSR02C, COSGN00C, COADM01C, COUSR00C | Date/time structure  | Current date/time working storage fields for header display |
| CSMSG01Y   | COUSR02C, COSGN00C, COADM01C, COUSR00C | Screen messages      | Common messages (CCDA-MSG-INVALID-KEY, CCDA-MSG-THANK-YOU) |
| CSUSR01Y   | COUSR02C, COSGN00C, COUSR00C | Record layout        | USRSEC VSAM record layout (SEC-USER-DATA: 80 bytes) |
| DFHAID     | COUSR02C (+ all)    | CICS system copybook    | AID key constants (DFHENTER, DFHPF3, DFHPF4, etc.) |
| DFHBMSCA   | COUSR02C (+ all)    | CICS system copybook    | BMS attribute constants (DFHRED, DFHGREEN, DFHNEUTR, etc.) |
| COADM02Y   | COADM01C            | Menu options data       | Admin menu option table — maps option numbers to program names |

### Record Layout: CSUSR01Y (SEC-USER-DATA) — USRSEC Record

```
01 SEC-USER-DATA.                              Total: 80 bytes
   05 SEC-USR-ID          PIC X(08).           Offset 1-8    User ID (key)
   05 SEC-USR-FNAME       PIC X(20).           Offset 9-28   First Name
   05 SEC-USR-LNAME       PIC X(20).           Offset 29-48  Last Name
   05 SEC-USR-PWD         PIC X(08).           Offset 49-56  Password
   05 SEC-USR-TYPE        PIC X(01).           Offset 57     User Type (A/U)
   05 SEC-USR-FILLER      PIC X(23).           Offset 58-80  Filler
```

### Record Layout: COCOM01Y (CARDDEMO-COMMAREA)

```
01 CARDDEMO-COMMAREA.
   05 CDEMO-GENERAL-INFO.
      10 CDEMO-FROM-TRANID          PIC X(04).
      10 CDEMO-FROM-PROGRAM         PIC X(08).
      10 CDEMO-TO-TRANID            PIC X(04).
      10 CDEMO-TO-PROGRAM           PIC X(08).
      10 CDEMO-USER-ID              PIC X(08).
      10 CDEMO-USER-TYPE            PIC X(01).
         88 CDEMO-USRTYP-ADMIN      VALUE 'A'.
         88 CDEMO-USRTYP-USER       VALUE 'U'.
      10 CDEMO-PGM-CONTEXT          PIC 9(01).
         88 CDEMO-PGM-ENTER         VALUE 0.
         88 CDEMO-PGM-REENTER       VALUE 1.
   05 CDEMO-CUSTOMER-INFO.
      10 CDEMO-CUST-ID              PIC 9(09).
      10 CDEMO-CUST-FNAME           PIC X(25).
      10 CDEMO-CUST-MNAME           PIC X(25).
      10 CDEMO-CUST-LNAME           PIC X(25).
   05 CDEMO-ACCOUNT-INFO.
      10 CDEMO-ACCT-ID              PIC 9(11).
      10 CDEMO-ACCT-STATUS          PIC X(01).
   05 CDEMO-CARD-INFO.
      10 CDEMO-CARD-NUM             PIC 9(16).
   05 CDEMO-MORE-INFO.
      10 CDEMO-LAST-MAP             PIC X(7).
      10 CDEMO-LAST-MAPSET          PIC X(7).
```

### Program-Specific COMMAREA Extension (inline in COUSR02C)

```
   05 CDEMO-CU02-INFO.
      10 CDEMO-CU02-USRID-FIRST     PIC X(08).
      10 CDEMO-CU02-USRID-LAST      PIC X(08).
      10 CDEMO-CU02-PAGE-NUM        PIC 9(08).
      10 CDEMO-CU02-NEXT-PAGE-FLG   PIC X(01).
         88 NEXT-PAGE-YES            VALUE 'Y'.
         88 NEXT-PAGE-NO             VALUE 'N'.
      10 CDEMO-CU02-USR-SEL-FLG     PIC X(01).
      10 CDEMO-CU02-USR-SELECTED    PIC X(08).
```

### Copybook: COTTL01Y (Screen Titles)

```
01 CCDA-SCREEN-TITLE.
   05 CCDA-TITLE01    PIC X(40) VALUE '      AWS Mainframe Modernization       '.
   05 CCDA-TITLE02    PIC X(40) VALUE '              CardDemo                  '.
   05 CCDA-THANK-YOU  PIC X(40) VALUE 'Thank you for using CCDA application... '.
```

### Copybook: CSDAT01Y (Date/Time Structure)

```
01 WS-DATE-TIME.
   05 WS-CURDATE-DATA.
      10 WS-CURDATE.
         15 WS-CURDATE-YEAR          PIC 9(04).
         15 WS-CURDATE-MONTH         PIC 9(02).
         15 WS-CURDATE-DAY           PIC 9(02).
      10 WS-CURTIME.
         15 WS-CURTIME-HOURS         PIC 9(02).
         15 WS-CURTIME-MINUTE        PIC 9(02).
         15 WS-CURTIME-SECOND        PIC 9(02).
         15 WS-CURTIME-MILSEC        PIC 9(02).
   05 WS-CURDATE-MM-DD-YY.
      10 WS-CURDATE-MM               PIC 9(02).
      10 FILLER                      PIC X(01) VALUE '/'.
      10 WS-CURDATE-DD               PIC 9(02).
      10 FILLER                      PIC X(01) VALUE '/'.
      10 WS-CURDATE-YY               PIC 9(02).
   05 WS-CURTIME-HH-MM-SS.
      10 WS-CURTIME-HH               PIC 9(02).
      10 FILLER                      PIC X(01) VALUE ':'.
      10 WS-CURTIME-MM               PIC 9(02).
      10 FILLER                      PIC X(01) VALUE ':'.
      10 WS-CURTIME-SS               PIC 9(02).
   05 WS-TIMESTAMP.
      (timestamp fields for YYYY-MM-DD HH:MM:SS.ffffff format)
```

### Copybook: CSMSG01Y (Common Messages)

```
01 CCDA-COMMON-MESSAGES.
   05 CCDA-MSG-THANK-YOU     PIC X(50) VALUE 'Thank you for using CardDemo application...      '.
   05 CCDA-MSG-INVALID-KEY   PIC X(50) VALUE 'Invalid key pressed. Please see below...         '.
```

---

## Section 4: VSAM File Operations

### File Inventory

| DD Name  | File Description          | Key Field    | Key Length | Record Copybook | Record Length |
|----------|---------------------------|--------------|------------|-----------------|---------------|
| USRSEC   | User Security Master File | SEC-USR-ID   | 8 bytes    | CSUSR01Y        | 80 bytes      |

### VSAM Operations in COUSR02C

| # | Command      | Dataset  | Key/RIDFLD | Paragraph              | Purpose                                      | RESP Handling                    |
|---|-------------|----------|------------|------------------------|----------------------------------------------|----------------------------------|
| 1 | READ UPDATE | USRSEC   | SEC-USR-ID | READ-USER-SEC-FILE     | Read user record with intent to update        | NORMAL / NOTFND / OTHER          |
| 2 | REWRITE     | USRSEC   | (implicit) | UPDATE-USER-SEC-FILE   | Write back modified user record               | NORMAL / NOTFND / OTHER          |

### VSAM File Flow Diagram

```
  ┌──────────────────────────────────────────────────────────────────────┐
  │                 COUSR02C — USRSEC File Access Pattern               │
  └──────────────────────────────────────────────────────────────────────┘

  ┌──────────┐                    ┌───────────┐
  │ Terminal  │                    │  USRSEC   │
  │ (3270)    │                    │  (VSAM    │
  │           │                    │   KSDS)   │
  └─────┬────┘                    └─────┬─────┘
        │                               │
        │  1. Enter User ID             │
        │  ─────────────────>           │
        │                    READ UPDATE│
        │                    ──────────>│
        │                               │
        │  2. Display user details      │
        │  <─────────────────           │
        │     (SEND MAP)                │
        │                               │
        │  3. Modify fields + PF5       │
        │  ─────────────────>           │
        │                    READ UPDATE│
        │                    ──────────>│
        │                               │
        │  4. Compare & REWRITE         │
        │                    REWRITE    │
        │                    ──────────>│
        │                               │
        │  5. Success confirmation      │
        │  <─────────────────           │
        │     (SEND MAP)                │
        └───────────────────────────────┘
```

### Detailed EXEC CICS Commands — READ-USER-SEC-FILE

```cobol
EXEC CICS READ
     DATASET   (WS-USRSEC-FILE)        Value: 'USRSEC  '
     INTO      (SEC-USER-DATA)          Record area from CSUSR01Y
     LENGTH    (LENGTH OF SEC-USER-DATA) 80 bytes
     RIDFLD    (SEC-USR-ID)             8-byte user ID key
     KEYLENGTH (LENGTH OF SEC-USR-ID)   8 bytes
     UPDATE                             Locks record for update
     RESP      (WS-RESP-CD)
     RESP2     (WS-REAS-CD)
END-EXEC.
```

**RESP handling:**
- **NORMAL**: Displays "Press PF5 key to save your updates ..." (DFHNEUTR color)
- **NOTFND**: Sets error flag, displays "User ID NOT found..."
- **OTHER**: DISPLAYs RESP/REAS codes, sets error flag, displays "Unable to lookup User..."

### Detailed EXEC CICS Commands — UPDATE-USER-SEC-FILE

```cobol
EXEC CICS REWRITE
     DATASET   (WS-USRSEC-FILE)        Value: 'USRSEC  '
     FROM      (SEC-USER-DATA)          Modified record
     LENGTH    (LENGTH OF SEC-USER-DATA) 80 bytes
     RESP      (WS-RESP-CD)
     RESP2     (WS-REAS-CD)
END-EXEC.
```

**RESP handling:**
- **NORMAL**: Displays "User {ID} has been updated ..." (DFHGREEN color)
- **NOTFND**: Sets error flag, displays "User ID NOT found..."
- **OTHER**: DISPLAYs RESP/REAS codes, sets error flag, displays "Unable to Update User..."

---

## Section 5: BMS Screen Map

### Map Definition

| Attribute      | Value                |
|----------------|----------------------|
| **Mapset**     | COUSR02              |
| **Map**        | COUSR2A              |
| **Size**       | 24 rows x 80 columns |
| **Source**      | `app/bms/COUSR02.bms` |
| **CTRL**       | ALARM, FREEKB        |
| **LANG**       | COBOL                |
| **MODE**       | INOUT                |
| **STORAGE**    | AUTO                 |

### ASCII Screen Layout

```
Row Col  Field
 1   1   Tran: CU02                AWS Mainframe Modernization              Date: mm/dd/yy
 2   1   Prog: COUSR02C                    CardDemo                         Time: hh:mm:ss
 3
 4  35                  Update User
 5
 6   6   Enter User ID: [________]
 7
 8   6   **********************************************************************
 9
10
11   6   First Name: [____________________]     Last Name: [____________________]
12
13   6   Password: [________] (8 Char)
14
15   6   User Type:  [_] (A=Admin, U=User)
16
17-22     (empty)
23   1   [error/status message area — 78 chars                                            ]
24   1   ENTER=Fetch  F3=Save&Exit  F4=Clear  F5=Save  F12=Cancel
```

### Field Inventory

| Field Name | Row | Col | Length | Attributes         | Color     | Purpose                          |
|------------|-----|-----|--------|--------------------|-----------|----------------------------------|
| TRNNAME    | 1   | 7   | 4      | ASKIP,FSET,NORM    | Blue      | Transaction ID display           |
| TITLE01    | 1   | 21  | 40     | ASKIP,FSET,NORM    | Yellow    | Application title line 1         |
| CURDATE    | 1   | 71  | 8      | ASKIP,FSET,NORM    | Blue      | Current date (mm/dd/yy)          |
| PGMNAME    | 2   | 7   | 8      | ASKIP,FSET,NORM    | Blue      | Program name display             |
| TITLE02    | 2   | 21  | 40     | ASKIP,FSET,NORM    | Yellow    | Application title line 2         |
| CURTIME    | 2   | 71  | 8      | ASKIP,FSET,NORM    | Blue      | Current time (hh:mm:ss)          |
| USRIDIN    | 6   | 21  | 8      | FSET,IC,NORM,UNPROT| Green     | **Input:** User ID to look up    |
| FNAME      | 11  | 18  | 20     | FSET,NORM,UNPROT   | Green     | **Input:** First Name            |
| LNAME      | 11  | 56  | 20     | FSET,NORM,UNPROT   | Green     | **Input:** Last Name             |
| PASSWD     | 13  | 16  | 8      | DRK,FSET,UNPROT    | Green     | **Input:** Password (dark/hidden)|
| USRTYPE    | 15  | 17  | 1      | FSET,NORM,UNPROT   | Green     | **Input:** User Type (A or U)    |
| ERRMSG     | 23  | 1   | 78     | ASKIP,BRT,FSET     | Red       | Error/status message area        |

**Input fields** (UNPROT): USRIDIN, FNAME, LNAME, PASSWD, USRTYPE
**Display fields** (ASKIP): TRNNAME, TITLE01, TITLE02, CURDATE, CURTIME, PGMNAME, ERRMSG

### Function Key Assignments

| Key    | EIBAID Constant | Action                                    | Paragraph             |
|--------|-----------------|-------------------------------------------|-----------------------|
| ENTER  | DFHENTER        | Fetch user record by entered User ID      | PROCESS-ENTER-KEY     |
| PF3    | DFHPF3          | Save changes and exit to previous screen  | UPDATE-USER-INFO, then RETURN-TO-PREV-SCREEN |
| PF4    | DFHPF4          | Clear all input fields                    | CLEAR-CURRENT-SCREEN  |
| PF5    | DFHPF5          | Save changes (stay on screen)             | UPDATE-USER-INFO      |
| PF12   | DFHPF12         | Cancel — return to Admin Menu (COADM01C)  | RETURN-TO-PREV-SCREEN |
| Other  | —               | Display "Invalid key pressed" error       | SEND-USRUPD-SCREEN    |

---

## Section 6: COMMAREA Structure

### COMMAREA Field Usage by Program

| Field                  | COSGN00C | COADM01C | COUSR00C | COUSR02C |
|------------------------|----------|----------|----------|----------|
| CDEMO-FROM-TRANID      | W        | W        | W        | W        |
| CDEMO-FROM-PROGRAM     | W        | W        | W        | W        |
| CDEMO-TO-TRANID        | —        | —        | —        | —        |
| CDEMO-TO-PROGRAM       | W        | W        | W        | R/W      |
| CDEMO-USER-ID          | W        | R        | R        | R        |
| CDEMO-USER-TYPE        | W        | R        | R        | R        |
| CDEMO-PGM-CONTEXT      | W        | R/W      | R/W      | R/W      |
| CDEMO-CU02-USR-SELECTED| —        | —        | W        | R        |

*(W = Writes, R = Reads, R/W = Reads and Writes)*

### COMMAREA Flow Diagram

```
  COSGN00C                COADM01C              COUSR00C              COUSR02C
  ────────                ────────              ────────              ────────
  Sets:                   Reads:                Reads:                Reads:
  - FROM-TRANID='CC00'    - PGM-CONTEXT         - PGM-CONTEXT         - PGM-CONTEXT
  - FROM-PROGRAM          Sets:                 Sets:                 - FROM-PROGRAM
  - USER-ID               - FROM-TRANID='CA00'  - FROM-TRANID='CU00'  - CU02-USR-SELECTED
  - USER-TYPE             - FROM-PROGRAM        - FROM-PROGRAM
  - PGM-CONTEXT=0         - PGM-CONTEXT=0       - TO-PROGRAM=         Sets:
                          - TO-PROGRAM           'COUSR02C'           - FROM-TRANID='CU02'
                          (XCTL target from      - PGM-CONTEXT=0       - FROM-PROGRAM
                           menu table)           - CU02-USR-SELECTED   - TO-PROGRAM
                                                                       - PGM-CONTEXT
         ─── XCTL ──>          ─── XCTL ──>          ─── XCTL ──>
         COMMAREA              COMMAREA              COMMAREA
```

### PGM-CONTEXT Lifecycle in COUSR02C

| Value | Condition (88-level)   | Meaning                                        |
|-------|------------------------|------------------------------------------------|
| 0     | CDEMO-PGM-ENTER        | First entry — initialize screen, load pre-selected user |
| 1     | CDEMO-PGM-REENTER      | Re-entry — process user input (ENTER/PF keys)  |

---

## Section 7: Business Logic Flow

### Step-by-Step Narrative

#### 1. Entry and Initialization

```
IF EIBCALEN = 0
    → No COMMAREA: redirect to Sign-on (COSGN00C) via XCTL
ELSE
    → Move DFHCOMMAREA to CARDDEMO-COMMAREA
    IF PGM-CONTEXT = 0 (first entry)
        → Set PGM-CONTEXT = 1 (mark as re-entry for next iteration)
        → Initialize screen to LOW-VALUES
        → Set cursor to USRIDIN field
        → IF CDEMO-CU02-USR-SELECTED is not empty
            → Pre-populate USRIDIN with selected user ID
            → PERFORM PROCESS-ENTER-KEY (auto-fetch user data)
        → SEND MAP COUSR2A
    ELSE (re-entry)
        → RECEIVE MAP COUSR2A
        → EVALUATE EIBAID (see function key table)
```

#### 2. Fetch User Data (PROCESS-ENTER-KEY)

```
VALIDATE:
    → IF USRIDIN is empty: error "User ID can NOT be empty..."
    → Set cursor to USRIDIN

IF validation passes:
    → Clear FNAME, LNAME, PASSWD, USRTYPE fields
    → Move entered User ID to SEC-USR-ID
    → READ USRSEC file with UPDATE lock
    → IF NORMAL response:
        → Populate screen: FNAME, LNAME, PASSWD, USRTYPE from record
        → Display "Press PF5 key to save your updates ..."
    → IF NOTFND: error "User ID NOT found..."
    → IF OTHER: error "Unable to lookup User..."
    → SEND MAP COUSR2A
```

#### 3. Save User Updates (UPDATE-USER-INFO) — triggered by PF3 or PF5

```
VALIDATE (field-by-field, in order):
    1. USRIDIN empty  → error "User ID can NOT be empty..."
    2. FNAME empty    → error "First Name can NOT be empty..."
    3. LNAME empty    → error "Last Name can NOT be empty..."
    4. PASSWD empty   → error "Password can NOT be empty..."
    5. USRTYPE empty  → error "User Type can NOT be empty..."

IF all fields valid:
    → Move entered User ID to SEC-USR-ID
    → READ USRSEC file with UPDATE lock (re-read for currency)
    → Compare each screen field with record field:
        - IF FNAME changed → update SEC-USR-FNAME, set USR-MODIFIED-YES
        - IF LNAME changed → update SEC-USR-LNAME, set USR-MODIFIED-YES
        - IF PASSWD changed → update SEC-USR-PWD, set USR-MODIFIED-YES
        - IF USRTYPE changed → update SEC-USR-TYPE, set USR-MODIFIED-YES
    → IF any field modified:
        → REWRITE record to USRSEC
        → IF NORMAL: "User {ID} has been updated ..." (green)
        → IF NOTFND: "User ID NOT found..."
        → IF OTHER: "Unable to Update User..."
    → IF no fields modified:
        → "Please modify to update ..." (red)
```

#### 4. Clear Screen (PF4)

```
→ Reset all input fields to SPACES
→ Set cursor to USRIDIN
→ Clear message area
→ SEND MAP COUSR2A
```

#### 5. Exit Paths

| Trigger | Behavior |
|---------|----------|
| PF3     | Save changes (UPDATE-USER-INFO), then XCTL to FROM-PROGRAM or COADM01C |
| PF12    | XCTL to COADM01C (Admin Menu) without saving |
| EIBCALEN=0 | XCTL to COSGN00C (Sign-on) |

#### 6. Error Handling Summary

| Error Condition | Message | Color | Cursor Position |
|-----------------|---------|-------|-----------------|
| Empty User ID (fetch) | "User ID can NOT be empty..." | Default | USRIDIN |
| Empty User ID (save) | "User ID can NOT be empty..." | Default | USRIDIN |
| Empty First Name | "First Name can NOT be empty..." | Default | FNAME |
| Empty Last Name | "Last Name can NOT be empty..." | Default | LNAME |
| Empty Password | "Password can NOT be empty..." | Default | PASSWD |
| Empty User Type | "User Type can NOT be empty..." | Default | USRTYPE |
| User not found (READ) | "User ID NOT found..." | Default | USRIDIN |
| VSAM I/O error (READ) | "Unable to lookup User..." | Default | FNAME |
| User not found (REWRITE) | "User ID NOT found..." | Default | USRIDIN |
| VSAM I/O error (REWRITE) | "Unable to Update User..." | Default | FNAME |
| No changes detected | "Please modify to update ..." | Red (DFHRED) | — |
| Invalid key pressed | CCDA-MSG-INVALID-KEY | Default | — |
| Successful update | "User {ID} has been updated ..." | Green (DFHGREEN) | — |
| Ready for edits | "Press PF5 key to save your updates ..." | Neutral (DFHNEUTR) | — |

---

## Section 8: Complete File Inventory

### COBOL Source Files

| File                  | Path                      | Role in CU02 Flow |
|-----------------------|---------------------------|--------------------|
| COUSR02C.cbl          | `app/cbl/COUSR02C.cbl`   | Target program     |
| COUSR00C.cbl          | `app/cbl/COUSR00C.cbl`   | Upstream — User List |
| COADM01C.cbl          | `app/cbl/COADM01C.cbl`   | Upstream — Admin Menu |
| COSGN00C.cbl          | `app/cbl/COSGN00C.cbl`   | Upstream — Sign-on |

### Copybook Files

| File                  | Path                      | Type                |
|-----------------------|---------------------------|---------------------|
| COCOM01Y.cpy          | `app/cpy/COCOM01Y.cpy`   | COMMAREA definition |
| COTTL01Y.cpy          | `app/cpy/COTTL01Y.cpy`   | Screen titles       |
| CSDAT01Y.cpy          | `app/cpy/CSDAT01Y.cpy`   | Date/time structure |
| CSMSG01Y.cpy          | `app/cpy/CSMSG01Y.cpy`   | Common messages     |
| CSUSR01Y.cpy          | `app/cpy/CSUSR01Y.cpy`   | USRSEC record layout |
| COUSR02 *(BMS symbolic)* | *(auto-generated from BMS)* | BMS symbolic map |
| DFHAID *(system)*     | *(IBM CICS system)*       | AID key constants   |
| DFHBMSCA *(system)*   | *(IBM CICS system)*       | BMS attribute constants |

### BMS Map Files

| File                  | Path                      | Mapset  | Map     |
|-----------------------|---------------------------|---------|---------|
| COUSR02.bms           | `app/bms/COUSR02.bms`    | COUSR02 | COUSR2A |

### VSAM Data Files

| DD Name  | Description               | Key         | Record Layout |
|----------|---------------------------|-------------|---------------|
| USRSEC   | User Security Master File | SEC-USR-ID  | CSUSR01Y      |

### CSD Definitions

| Resource Type | Name      | Group     | Key Attributes              |
|---------------|-----------|-----------|-----------------------------|
| TRANSACTION   | CU02      | CARDDEMO  | PROGRAM(COUSR02C)           |
| PROGRAM       | COUSR02C  | CARDDEMO  | LANGUAGE(COBOL)             |
| MAPSET        | COUSR02   | CARDDEMO  | —                           |

---

## Section 9: Transaction Record Layout

### USRSEC Record (Read and Rewritten by COUSR02C)

```
Offset  Length  Field           PIC         Description
------  ------  --------------- ----------  ---------------------------
1-8     8       SEC-USR-ID      X(08)       User ID (primary key)
9-28    20      SEC-USR-FNAME   X(20)       First Name
29-48   20      SEC-USR-LNAME   X(20)       Last Name
49-56   8       SEC-USR-PWD     X(08)       Password
57      1       SEC-USR-TYPE    X(01)       User Type: 'A'=Admin, 'U'=User
58-80   23      SEC-USR-FILLER  X(23)       Reserved/Filler
------  ------  --------------- ----------  ---------------------------
Total:  80 bytes
```

**Fields modified by COUSR02C:**
- SEC-USR-FNAME (First Name)
- SEC-USR-LNAME (Last Name)
- SEC-USR-PWD (Password)
- SEC-USR-TYPE (User Type)

**Fields NOT modified:**
- SEC-USR-ID (primary key — used for lookup only)
- SEC-USR-FILLER (reserved)

---

## Section 10: Observations

### What This Flow Demonstrates Well

1. **Clean pseudo-conversational pattern** — Standard CICS RETURN TRANSID / PGM-CONTEXT lifecycle with clear first-entry vs. re-entry separation
2. **COMMAREA-driven navigation** — FROM-PROGRAM, TO-PROGRAM, PGM-CONTEXT fields provide a well-structured navigation framework
3. **Field-level change detection** — The program compares each screen field against the existing record before writing, avoiding unnecessary I/O
4. **Comprehensive input validation** — All five editable fields are validated for empty/spaces before update
5. **Consistent error handling** — Every EXEC CICS I/O command checks RESP/RESP2 with NORMAL/NOTFND/OTHER evaluation
6. **READ with UPDATE + REWRITE pattern** — Proper VSAM pessimistic locking (READ UPDATE followed by REWRITE)
7. **Pre-selection from upstream** — CDEMO-CU02-USR-SELECTED allows the User List screen to pass a selected user ID, auto-populating the update screen

### Gaps and Migration Considerations

1. **No concurrent update protection beyond VSAM lock** — The READ UPDATE lock is released when the task returns (pseudo-conversational). Between the display and the PF5 save, another user could modify the same record. The re-read before REWRITE partially mitigates this but does not detect conflicts
2. **Password stored in plain text** — SEC-USR-PWD is stored as PIC X(08) with no hashing or encryption. Migration target should implement proper password hashing
3. **Password displayed in dark field** — The BMS PASSWD field uses DRK attribute, which is only a display-level protection; the data is transmitted in clear text
4. **No audit trail** — User modifications are not logged. Migration should add audit logging for security compliance
5. **Single-byte user type** — SEC-USR-TYPE uses a single character ('A'/'U'). Migration to role-based access control (RBAC) would require a richer permission model
6. **Hard-coded program names** — Return-to-program logic defaults to 'COADM01C' and 'COSGN00C' as string literals. Migration should externalize routing configuration
7. **No field-level security** — Any admin can modify any field including user type and password. No separation of duties
8. **80-byte fixed-length record** — VSAM KSDS with 23 bytes of filler. Migration to a relational database would normalize this structure

---

## Section 11: Comparison — Flow Complexity Summary

| Metric                          | COUSR02C (CU02)              |
|---------------------------------|------------------------------|
| Programs in execution path      | 4 (COSGN00C → COADM01C → COUSR00C → COUSR02C) |
| Copybooks (application)         | 5 (COCOM01Y, COTTL01Y, CSDAT01Y, CSMSG01Y, CSUSR01Y) |
| Copybooks (BMS symbolic)        | 1 (COUSR02)                  |
| Copybooks (system)              | 2 (DFHAID, DFHBMSCA)        |
| BMS maps                        | 1 mapset / 1 map (COUSR02 / COUSR2A) |
| VSAM files accessed             | 1 (USRSEC)                   |
| EXEC CICS commands (target pgm) | 5 (SEND MAP, RECEIVE MAP, READ, REWRITE, RETURN TRANSID, XCTL) |
| CALL statements                 | 0                            |
| LINK statements                 | 0                            |
| Screen input fields             | 5 (USRIDIN, FNAME, LNAME, PASSWD, USRTYPE) |
| Function keys handled           | 5 (ENTER, PF3, PF4, PF5, PF12) |
| Validation rules                | 5 (one per input field — non-empty check) |
| Error messages                  | 10+ distinct messages        |
| Lines of COBOL (target)         | 415                          |
| Complexity rating               | **Low-Medium** — Single-file CRUD with simple validation |

### All EXEC CICS Commands in COUSR02C

| #  | Command         | Paragraph                | Parameters                                                        |
|----|----------------|--------------------------|-------------------------------------------------------------------|
| 1  | SEND MAP       | SEND-USRUPD-SCREEN       | MAP('COUSR2A') MAPSET('COUSR02') FROM(COUSR2AO) ERASE CURSOR     |
| 2  | RECEIVE MAP    | RECEIVE-USRUPD-SCREEN    | MAP('COUSR2A') MAPSET('COUSR02') INTO(COUSR2AI) RESP RESP2       |
| 3  | READ           | READ-USER-SEC-FILE       | DATASET(WS-USRSEC-FILE) INTO(SEC-USER-DATA) RIDFLD(SEC-USR-ID) KEYLENGTH UPDATE RESP RESP2 |
| 4  | REWRITE        | UPDATE-USER-SEC-FILE     | DATASET(WS-USRSEC-FILE) FROM(SEC-USER-DATA) RESP RESP2           |
| 5  | RETURN         | MAIN-PARA (end)          | TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA)                   |
| 6  | XCTL           | RETURN-TO-PREV-SCREEN    | PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA)            |

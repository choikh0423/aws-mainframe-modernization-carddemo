# CICS Transaction Flow Analysis: CC00 (COSGN00C) — Sign-on / Login Screen

## Section 1: Flow Overview

| Attribute          | Value                                              |
|--------------------|----------------------------------------------------|
| **Transaction ID** | CC00                                               |
| **Entry Program**  | COSGN00C                                           |
| **Function**       | Sign-on / Login Screen for the CardDemo Application|
| **Application**    | CardDemo — AWS Mainframe Modernization Demo        |
| **Data Access**    | VSAM (USRSEC KSDS)                                |
| **BMS Mapset**     | COSGN00 (Map: COSGN0A)                            |

### Navigation Path (ASCII Diagram)

```
 +============================================================+
 |                  CardDemo Application Flow                  |
 +============================================================+

 TERMINAL START (CC00)
        |
        v
 +--------------+
 | COSGN00C     |  Transaction: CC00
 | Sign-on      |  BMS Map: COSGN0A / COSGN00
 | Screen       |  VSAM: USRSEC (READ)
 +--------------+
        |
        |--- ENTER pressed, credentials valid
        |
        +--- User Type = 'A' (Admin) -----> XCTL -----> +--------------+
        |                                                | COADM01C     |
        |                                                | Admin Menu   |
        |                                                | Tran: CA00   |
        |                                                +--------------+
        |
        +--- User Type = 'U' (Regular) ---> XCTL -----> +--------------+
        |                                                | COMEN01C     |
        |                                                | Main Menu    |
        |                                                | Tran: CM00   |
        |                                                +--------------+
        |
        +--- PF3 pressed -----> SEND TEXT ("Thank you...") --> RETURN (no TRANSID)
        |
        +--- Invalid key -----> Re-display sign-on screen --> RETURN TRANSID(CC00)
        |
        +--- Validation error -> Re-display with message ---> RETURN TRANSID(CC00)
```

### Pseudo-Conversational Lifecycle

```
 Iteration 1 (First Entry — EIBCALEN = 0):
 +------------------------------------------+
 |  EIBCALEN = 0 (no COMMAREA)             |
 |  -> Initialize screen to LOW-VALUES     |
 |  -> Set cursor to User ID field         |
 |  -> SEND MAP COSGN0A (blank sign-on)    |
 |  -> RETURN TRANSID('CC00')              |
 |     COMMAREA(CARDDEMO-COMMAREA)         |
 +------------------------------------------+

 Iteration 2+ (Re-entry — EIBCALEN > 0):
 +------------------------------------------+
 |  EIBCALEN > 0 (COMMAREA present)        |
 |  EVALUATE EIBAID:                        |
 |    DFHENTER -> PROCESS-ENTER-KEY         |
 |      -> RECEIVE MAP                      |
 |      -> Validate User ID / Password      |
 |      -> READ USRSEC file                 |
 |      -> Compare password                 |
 |      -> XCTL to COADM01C or COMEN01C    |
 |    DFHPF3  -> SEND TEXT "Thank you"      |
 |              -> RETURN (exit)            |
 |    OTHER   -> Set error message          |
 |              -> SEND MAP (re-display)    |
 |              -> RETURN TRANSID('CC00')   |
 +------------------------------------------+
```

> **Note:** COSGN00C does NOT check `CDEMO-PGM-CONTEXT` to distinguish first-entry vs re-entry. Instead, it relies solely on `EIBCALEN = 0` to detect initial invocation (no COMMAREA passed). On re-entry, the COMMAREA is present (`EIBCALEN > 0`) and the program evaluates `EIBAID` directly.

---

## Section 2: Programs Involved

| # | Program    | Type       | Function                        | Called By   | Call Method             |
|---|------------|------------|---------------------------------|-------------|-------------------------|
| 1 | COSGN00C   | CICS COBOL | Sign-on / Login Screen          | CICS (CC00) | Initial entry (TRANSID) |
| 2 | COADM01C   | CICS COBOL | Admin Menu for Admin Users      | COSGN00C    | XCTL with COMMAREA      |
| 3 | COMEN01C   | CICS COBOL | Main Menu for Regular Users     | COSGN00C    | XCTL with COMMAREA      |

### Downstream Programs (called by COADM01C — Admin Menu)

| # | Program    | Function                              |
|---|------------|---------------------------------------|
| 1 | COUSR00C   | User List (Security)                  |
| 2 | COUSR01C   | User Add (Security)                   |
| 3 | COUSR02C   | User Update (Security)                |
| 4 | COUSR03C   | User Delete (Security)                |
| 5 | COTRTLIC   | Transaction Type List/Update (Db2)    |
| 6 | COTRTUPC   | Transaction Type Maintenance (Db2)    |

### Downstream Programs (called by COMEN01C — Main Menu)

| # | Program    | Function                              |
|---|------------|---------------------------------------|
| 1  | COACTVWC  | Account View                          |
| 2  | COACTUPC  | Account Update                        |
| 3  | COCRDLIC  | Credit Card List                      |
| 4  | COCRDSLC  | Credit Card View                      |
| 5  | COCRDUPC  | Credit Card Update                    |
| 6  | COTRN00C  | Transaction List                      |
| 7  | COTRN01C  | Transaction View                      |
| 8  | COTRN02C  | Transaction Add                       |
| 9  | CORPT00C  | Transaction Reports                   |
| 10 | COBIL00C  | Bill Payment                          |
| 11 | COPAUS0C  | Pending Authorization View            |

### Upstream Callers (programs that XCTL back to COSGN00C)

The following programs set `CDEMO-TO-PROGRAM = 'COSGN00C'` and then execute `XCTL PROGRAM(CDEMO-TO-PROGRAM)` to return to the sign-on screen (typically on PF3 / exit):

| Program    | Context                                       |
|------------|-----------------------------------------------|
| COADM01C   | PF3 from Admin Menu or EIBCALEN=0 guard       |
| COMEN01C   | PF3 from Main Menu or EIBCALEN=0 guard        |
| COBIL00C   | PF3 exit / EIBCALEN=0 guard                   |
| COCRDSLC   | *(via menu return chain)*                      |
| CORPT00C   | PF3 exit / EIBCALEN=0 guard                   |
| COTRN00C   | PF3 exit / EIBCALEN=0 guard                   |
| COTRN01C   | PF3 exit / EIBCALEN=0 guard                   |
| COTRN02C   | PF3 exit / EIBCALEN=0 guard                   |
| COUSR00C   | PF3 exit / EIBCALEN=0 guard                   |
| COUSR01C   | PF3 exit / EIBCALEN=0 guard                   |
| COUSR02C   | PF3 exit / EIBCALEN=0 guard                   |
| COUSR03C   | PF3 exit / EIBCALEN=0 guard                   |

---

## Section 3: Copybooks

### Copybooks Used by COSGN00C

| # | Copybook   | Used By    | Classification           | Purpose                                           |
|---|------------|------------|--------------------------|---------------------------------------------------|
| 1 | COCOM01Y   | COSGN00C   | COMMAREA definition      | CardDemo inter-program communication area          |
| 2 | COSGN00    | COSGN00C   | BMS symbolic map         | Sign-on screen symbolic map (auto-generated)       |
| 3 | COTTL01Y   | COSGN00C   | Screen title/message     | Application title lines and thank-you text         |
| 4 | CSDAT01Y   | COSGN00C   | Date/time structure      | Current date and time formatting fields            |
| 5 | CSMSG01Y   | COSGN00C   | Screen title/message     | Common messages (thank-you, invalid key)           |
| 6 | CSUSR01Y   | COSGN00C   | Record layout            | USRSEC VSAM file record layout (user security)     |
| 7 | DFHAID     | COSGN00C   | CICS system copybook     | AID key constants (DFHENTER, DFHPF3, etc.)        |
| 8 | DFHBMSCA   | COSGN00C   | CICS system copybook     | BMS attribute constants                            |

### Record Layouts

#### COCOM01Y — CARDDEMO-COMMAREA (Communication Area)

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

**Total COMMAREA size:** 4 + 8 + 4 + 8 + 8 + 1 + 1 + 9 + 25 + 25 + 25 + 11 + 1 + 16 + 7 + 7 = **160 bytes**

#### CSUSR01Y — SEC-USER-DATA (USRSEC Record Layout)

```
01 SEC-USER-DATA.
   05 SEC-USR-ID                    PIC X(08).    Offset 1-8
   05 SEC-USR-FNAME                 PIC X(20).    Offset 9-28
   05 SEC-USR-LNAME                 PIC X(20).    Offset 29-48
   05 SEC-USR-PWD                   PIC X(08).    Offset 49-56
   05 SEC-USR-TYPE                  PIC X(01).    Offset 57
   05 SEC-USR-FILLER                PIC X(23).    Offset 58-80
```

**Total record size:** 80 bytes. Key field: `SEC-USR-ID` (8 bytes, alphanumeric).

#### COTTL01Y — CCDA-SCREEN-TITLE (Title Constants)

```
01 CCDA-SCREEN-TITLE.
   05 CCDA-TITLE01     PIC X(40)  VALUE '      AWS Mainframe Modernization       '.
   05 CCDA-TITLE02     PIC X(40)  VALUE '              CardDemo                  '.
   05 CCDA-THANK-YOU   PIC X(40)  VALUE 'Thank you for using CCDA application... '.
```

#### CSDAT01Y — WS-DATE-TIME (Date/Time Formatting)

```
01 WS-DATE-TIME.
   05 WS-CURDATE-DATA.
      10 WS-CURDATE.
         15 WS-CURDATE-YEAR         PIC 9(04).
         15 WS-CURDATE-MONTH        PIC 9(02).
         15 WS-CURDATE-DAY          PIC 9(02).
      10 WS-CURTIME.
         15 WS-CURTIME-HOURS        PIC 9(02).
         15 WS-CURTIME-MINUTE       PIC 9(02).
         15 WS-CURTIME-SECOND       PIC 9(02).
         15 WS-CURTIME-MILSEC       PIC 9(02).
   05 WS-CURDATE-MM-DD-YY.          (formatted: MM/DD/YY)
   05 WS-CURTIME-HH-MM-SS.          (formatted: HH:MM:SS)
   05 WS-TIMESTAMP.                  (formatted: YYYY-MM-DD HH:MM:SS.ffffff)
```

#### CSMSG01Y — CCDA-COMMON-MESSAGES (Message Constants)

```
01 CCDA-COMMON-MESSAGES.
   05 CCDA-MSG-THANK-YOU    PIC X(50) VALUE 'Thank you for using CardDemo application...      '.
   05 CCDA-MSG-INVALID-KEY  PIC X(50) VALUE 'Invalid key pressed. Please see below...         '.
```

---

## Section 4: VSAM File Operations

### Files Accessed by COSGN00C

| VSAM File | DD Name  | Key Field     | Key Len | Record Copybook | Record Length | Access Pattern    |
|-----------|----------|---------------|---------|-----------------|---------------|-------------------|
| USRSEC    | USRSEC   | SEC-USR-ID    | 8 bytes | CSUSR01Y        | 80 bytes      | Direct READ only  |

### VSAM I/O Operations Detail

| # | Command    | Dataset  | RIDFLD      | KEYLENGTH        | INTO            | Purpose                           |
|---|------------|----------|-------------|------------------|-----------------|-----------------------------------|
| 1 | EXEC CICS READ | USRSEC (via WS-USRSEC-FILE) | WS-USER-ID | LENGTH OF WS-USER-ID (8) | SEC-USER-DATA | Authenticate user credentials |

### RESP Code Handling for USRSEC READ

| RESP Code | Meaning           | Action Taken                                              |
|-----------|--------------------|-----------------------------------------------------------|
| 0         | Normal (found)     | Compare SEC-USR-PWD with entered password                 |
| 13        | NOTFND             | Display "User not found. Try again ..." message           |
| OTHER     | Unexpected error   | Display "Unable to verify the User ..." message           |

### VSAM File Flow Diagram

```
                     COSGN00C
                        |
                        | (1) EXEC CICS READ
                        |     DATASET('USRSEC')
                        |     RIDFLD(WS-USER-ID)
                        v
              +-------------------+
              |   USRSEC          |
              |   (User Security  |
              |    VSAM KSDS)     |
              |                   |
              |  Key: User-ID     |
              |  Rec: 80 bytes    |
              +-------------------+
                        |
                +-------+--------+
                |                |
          RESP = 0          RESP = 13
          (Found)           (Not Found)
                |                |
                v                v
         Compare PWD      "User not found"
                |          error message
          +-----+-----+
          |           |
       Match       Mismatch
          |           |
          v           v
    Set COMMAREA   "Wrong Password"
    fields and      error message
    XCTL to menu
```

### CSD Definition for USRSEC

```
DEFINE FILE(USRSEC) GROUP(CARDDEMO)
       DSNAME(AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS)
       RECORDFORMAT(V) ADD(YES) BROWSE(YES) DELETE(YES)
       READ(YES) UPDATE(YES)
```

---

## Section 5: BMS Screen Map

### Mapset: COSGN00 / Map: COSGN0A

**BMS Source:** `app/bms/COSGN00.bms`

**Mapset Attributes:**
- CTRL=(ALARM,FREEKB)
- EXTATT=YES
- LANG=COBOL
- MODE=INOUT
- STORAGE=AUTO
- TIOAPFX=YES

**Map Dimensions:** 24 rows x 80 columns

### ASCII Screen Layout

```
Row Col  Content
--- ---  ---------------------------------------------------------------
 1   1   Tran : CC00        AWS Mainframe Modernization         Date : mm/dd/yy
 2   1   Prog : COSGN00C              CardDemo                  Time : hh:mm:ss
 3   1   AppID: ________                                        SysID: ________
 4
 5   6        This is a Credit Card Demo Application for Mainframe Modernization
 6
 7  21   +========================================+
 8  21   |%%%%%%%  NATIONAL RESERVE NOTE  %%%%%%%%|
 9  21   |%(1)  THE UNITED STATES OF KICSLAND (1)%|
10  21   |%$$              ___       ********  $$%|
11  21   |%$    {x}       (o o)                 $%|
12  21   |%$     ******  (  V  )      O N E     $%|
13  21   |%(1)          ---m-m---             (1)%|
14  21   |%%~~~~~~~~~~~ ONE DOLLAR ~~~~~~~~~~~~~%%|
15  21   +========================================+
16
17  16   Type your User ID and Password, then press ENTER:
18
19  29   User ID     : [________]  (8 Char)
20  29   Password    : [________]  (8 Char)
21
22
23   1   [Error message area — 78 chars, RED, bright]
24   1   ENTER=Sign-on  F3=Exit
```

### Field Inventory

| # | Field Name | Row | Col | Length | Attributes         | Color     | Purpose                         |
|---|------------|-----|-----|--------|--------------------|-----------|----------------------------------|
| 1 | TRNNAME    | 1   | 8   | 4      | ASKIP,FSET,NORM    | BLUE      | Transaction ID display           |
| 2 | TITLE01    | 1   | 21  | 40     | ASKIP,FSET,NORM    | YELLOW    | Title line 1                     |
| 3 | CURDATE    | 1   | 71  | 8      | ASKIP,FSET,NORM    | BLUE      | Current date (MM/DD/YY)          |
| 4 | PGMNAME    | 2   | 8   | 8      | FSET,NORM,PROT     | BLUE      | Program name display             |
| 5 | TITLE02    | 2   | 21  | 40     | ASKIP,FSET,NORM    | YELLOW    | Title line 2                     |
| 6 | CURTIME    | 2   | 71  | 9      | FSET,NORM,PROT     | BLUE      | Current time (HH:MM:SS)          |
| 7 | APPLID     | 3   | 8   | 8      | FSET,NORM,PROT     | BLUE      | CICS Application ID              |
| 8 | SYSID      | 3   | 71  | 8      | FSET,NORM,PROT     | BLUE      | CICS System ID                   |
| 9 | USERID     | 19  | 43  | 8      | FSET,IC,NORM,UNPROT| GREEN     | **User ID input** (initial cursor)|
| 10| PASSWD     | 20  | 43  | 8      | DRK,FSET,UNPROT    | GREEN     | **Password input** (dark/hidden) |
| 11| ERRMSG     | 23  | 1   | 78     | ASKIP,BRT,FSET     | RED       | Error/status message             |

### Input Fields (UNPROT)

| Field  | Row | Col | Length | Notes                                     |
|--------|-----|-----|--------|-------------------------------------------|
| USERID | 19  | 43  | 8      | Initial cursor position (IC attribute)     |
| PASSWD | 20  | 43  | 8      | Dark attribute — input is hidden on screen |

### Function Key Assignments

| Key     | EIBAID Constant | Action                                           |
|---------|-----------------|--------------------------------------------------|
| ENTER   | DFHENTER        | Process sign-on: validate and authenticate       |
| PF3     | DFHPF3          | Exit: display "Thank you" message and terminate  |
| OTHER   | (all others)    | Display "Invalid key pressed" error message      |

---

## Section 6: COMMAREA Structure

### COMMAREA Field Usage by COSGN00C

| Field                 | PIC       | Read/Write | Purpose in COSGN00C                          |
|-----------------------|-----------|------------|-----------------------------------------------|
| CDEMO-FROM-TRANID     | X(04)     | Write      | Set to 'CC00' before XCTL to menu            |
| CDEMO-FROM-PROGRAM    | X(08)     | Write      | Set to 'COSGN00C' before XCTL to menu        |
| CDEMO-TO-TRANID       | X(04)     | —          | Not used by COSGN00C                          |
| CDEMO-TO-PROGRAM      | X(08)     | —          | Not used by COSGN00C                          |
| CDEMO-USER-ID         | X(08)     | Write      | Set to authenticated user ID (uppercased)     |
| CDEMO-USER-TYPE       | X(01)     | Write/Read | Set from SEC-USR-TYPE; read to branch on 'A'  |
| CDEMO-PGM-CONTEXT     | 9(01)     | Write      | Set to ZEROS (0 = first entry for target pgm) |
| CDEMO-CUST-ID         | 9(09)     | —          | Not used by COSGN00C                          |
| CDEMO-CUST-FNAME      | X(25)     | —          | Not used by COSGN00C                          |
| CDEMO-CUST-MNAME      | X(25)     | —          | Not used by COSGN00C                          |
| CDEMO-CUST-LNAME      | X(25)     | —          | Not used by COSGN00C                          |
| CDEMO-ACCT-ID         | 9(11)     | —          | Not used by COSGN00C                          |
| CDEMO-ACCT-STATUS     | X(01)     | —          | Not used by COSGN00C                          |
| CDEMO-CARD-NUM        | 9(16)     | —          | Not used by COSGN00C                          |
| CDEMO-LAST-MAP        | X(7)      | —          | Not used by COSGN00C                          |
| CDEMO-LAST-MAPSET     | X(7)      | —          | Not used by COSGN00C                          |

### COMMAREA Tree Diagram

```
CARDDEMO-COMMAREA (160 bytes)
|
+-- CDEMO-GENERAL-INFO
|   +-- CDEMO-FROM-TRANID      X(04)  [COSGN00C writes 'CC00']
|   +-- CDEMO-FROM-PROGRAM     X(08)  [COSGN00C writes 'COSGN00C']
|   +-- CDEMO-TO-TRANID        X(04)  [not used by COSGN00C]
|   +-- CDEMO-TO-PROGRAM       X(08)  [not used by COSGN00C]
|   +-- CDEMO-USER-ID          X(08)  [COSGN00C writes authenticated user ID]
|   +-- CDEMO-USER-TYPE        X(01)  [COSGN00C writes from SEC-USR-TYPE]
|   |   88 CDEMO-USRTYP-ADMIN  'A'    [checked to branch to Admin Menu]
|   |   88 CDEMO-USRTYP-USER   'U'    [default -> Main Menu]
|   +-- CDEMO-PGM-CONTEXT      9(01)  [COSGN00C writes 0 = first entry]
|       88 CDEMO-PGM-ENTER     0
|       88 CDEMO-PGM-REENTER   1
|
+-- CDEMO-CUSTOMER-INFO
|   +-- CDEMO-CUST-ID          9(09)  [not used by COSGN00C]
|   +-- CDEMO-CUST-FNAME       X(25)  [not used by COSGN00C]
|   +-- CDEMO-CUST-MNAME       X(25)  [not used by COSGN00C]
|   +-- CDEMO-CUST-LNAME       X(25)  [not used by COSGN00C]
|
+-- CDEMO-ACCOUNT-INFO
|   +-- CDEMO-ACCT-ID          9(11)  [not used by COSGN00C]
|   +-- CDEMO-ACCT-STATUS      X(01)  [not used by COSGN00C]
|
+-- CDEMO-CARD-INFO
|   +-- CDEMO-CARD-NUM         9(16)  [not used by COSGN00C]
|
+-- CDEMO-MORE-INFO
    +-- CDEMO-LAST-MAP         X(7)   [not used by COSGN00C]
    +-- CDEMO-LAST-MAPSET      X(7)   [not used by COSGN00C]
```

### Inter-Program COMMAREA Flow

```
COSGN00C                        COADM01C / COMEN01C
(Sign-on)                       (Menu Programs)
    |                                  |
    |  On successful login:            |
    |  CDEMO-FROM-TRANID = 'CC00'      |  Reads CDEMO-FROM-PROGRAM
    |  CDEMO-FROM-PROGRAM = 'COSGN00C' |  Reads CDEMO-USER-ID
    |  CDEMO-USER-ID = <user>          |  Reads CDEMO-USER-TYPE
    |  CDEMO-USER-TYPE = <type>        |  Reads CDEMO-PGM-CONTEXT (=0)
    |  CDEMO-PGM-CONTEXT = 0           |    -> knows this is first entry
    |                                  |
    |-------- XCTL + COMMAREA -------->|
    |                                  |
    |  On PF3 from menu:               |
    |<------- XCTL back to COSGN00C---|
    |  (CDEMO-TO-PROGRAM = 'COSGN00C') |
```

---

## Section 7: Business Logic Flow

### Step-by-Step Narrative

#### 1. Entry and Initialization

1. Program `COSGN00C` is invoked by CICS when transaction `CC00` is entered at the terminal (or via XCTL from a downstream program returning to sign-on).
2. The error flag `WS-ERR-FLG` is set to `'N'` (off).
3. Working storage message fields and the screen error message area are cleared to spaces.

#### 2. First Entry Detection (EIBCALEN = 0)

4. If `EIBCALEN = 0` (no COMMAREA — direct terminal invocation):
   - Screen output area `COSGN0AO` is initialized to `LOW-VALUES`.
   - Cursor is positioned to the User ID field (`USERIDL OF COSGN0AI = -1`).
   - The sign-on screen is sent to the terminal via `SEND MAP`.
   - Control returns to CICS with `RETURN TRANSID('CC00')` and the COMMAREA, establishing the pseudo-conversational loop.

#### 3. Re-entry Processing (EIBCALEN > 0)

5. If `EIBCALEN > 0` (COMMAREA present — user interaction occurred):
   - **EIBAID** is evaluated to determine which key was pressed.

#### 4. ENTER Key Processing (PROCESS-ENTER-KEY)

6. `EXEC CICS RECEIVE MAP('COSGN0A') MAPSET('COSGN00')` retrieves user input.
7. **Validation sequence:**
   - If `USERIDI` is spaces or LOW-VALUES: error "Please enter User ID ...", cursor on User ID field.
   - If `PASSWDI` is spaces or LOW-VALUES: error "Please enter Password ...", cursor on Password field.
8. Both User ID and Password are converted to uppercase using `FUNCTION UPPER-CASE`.
9. User ID is stored in both `WS-USER-ID` (for VSAM key) and `CDEMO-USER-ID` (COMMAREA).

#### 5. User Authentication (READ-USER-SEC-FILE)

10. `EXEC CICS READ DATASET(WS-USRSEC-FILE) RIDFLD(WS-USER-ID)` reads the USRSEC VSAM file.
11. **RESP code evaluation:**
    - **RESP = 0 (NORMAL):** Record found.
      - Compare `SEC-USR-PWD` (from VSAM record) with `WS-USER-PWD` (entered password).
      - **Password matches:**
        - Set COMMAREA navigation fields:
          - `CDEMO-FROM-TRANID = 'CC00'`
          - `CDEMO-FROM-PROGRAM = 'COSGN00C'`
          - `CDEMO-USER-ID = WS-USER-ID`
          - `CDEMO-USER-TYPE = SEC-USR-TYPE`
          - `CDEMO-PGM-CONTEXT = ZEROS` (first entry for target program)
        - If `CDEMO-USRTYP-ADMIN` (type = 'A'): **XCTL to COADM01C** (Admin Menu) with COMMAREA.
        - Else (type = 'U'): **XCTL to COMEN01C** (Main Menu) with COMMAREA.
      - **Password mismatch:** "Wrong Password. Try again ..." — cursor on Password field. Re-display sign-on screen.
    - **RESP = 13 (NOTFND):** "User not found. Try again ..." — cursor on User ID field.
    - **OTHER:** "Unable to verify the User ..." — cursor on User ID field.

#### 6. PF3 Key Processing (Exit)

12. `CCDA-MSG-THANK-YOU` message is sent as plain text via `EXEC CICS SEND TEXT`.
13. `EXEC CICS RETURN` (without TRANSID) terminates the transaction — the terminal returns to the CICS "blank screen" or native prompt.

#### 7. Invalid Key Processing

14. Error flag set to `'Y'`.
15. `CCDA-MSG-INVALID-KEY` message displayed.
16. Sign-on screen re-displayed with error message.
17. `RETURN TRANSID('CC00')` continues the pseudo-conversational loop.

### Error Handling Summary

| Error Condition             | Message Displayed                        | Cursor Position | Next Action            |
|-----------------------------|------------------------------------------|-----------------|------------------------|
| User ID empty               | "Please enter User ID ..."               | User ID field   | Re-display sign-on     |
| Password empty              | "Please enter Password ..."              | Password field  | Re-display sign-on     |
| User not found (RESP=13)    | "User not found. Try again ..."          | User ID field   | Re-display sign-on     |
| Wrong password              | "Wrong Password. Try again ..."          | Password field  | Re-display sign-on     |
| VSAM error (RESP=other)     | "Unable to verify the User ..."          | User ID field   | Re-display sign-on     |
| Invalid key pressed         | "Invalid key pressed. Please see below..." | (unchanged)   | Re-display sign-on     |

---

## Section 8: Complete File Inventory

### COBOL Source Files

| # | File Name    | Path                 | Role in This Flow                        |
|---|--------------|----------------------|------------------------------------------|
| 1 | COSGN00C.cbl | app/cbl/COSGN00C.cbl | **Target program** — Sign-on screen      |
| 2 | COADM01C.cbl | app/cbl/COADM01C.cbl | XCTL target — Admin Menu                 |
| 3 | COMEN01C.cbl | app/cbl/COMEN01C.cbl | XCTL target — Main Menu                  |

### Copybook Files

| # | File Name    | Path                 | Classification           |
|---|--------------|----------------------|--------------------------|
| 1 | COCOM01Y.cpy | app/cpy/COCOM01Y.cpy | COMMAREA definition      |
| 2 | COTTL01Y.cpy | app/cpy/COTTL01Y.cpy | Screen title constants   |
| 3 | CSDAT01Y.cpy | app/cpy/CSDAT01Y.cpy | Date/time structure      |
| 4 | CSMSG01Y.cpy | app/cpy/CSMSG01Y.cpy | Common messages          |
| 5 | CSUSR01Y.cpy | app/cpy/CSUSR01Y.cpy | User security record     |

### CICS System Copybooks (not in app/cpy/)

| # | Copybook   | Classification        | Purpose                               |
|---|------------|-----------------------|---------------------------------------|
| 1 | DFHAID     | IBM CICS system       | AID key constants (DFHENTER, DFHPF3)  |
| 2 | DFHBMSCA   | IBM CICS system       | BMS attribute constants                |

### BMS Symbolic Map Copybook (auto-generated from BMS source)

| # | Copybook   | Path                 | Classification        | Purpose                  |
|---|------------|----------------------|-----------------------|--------------------------|
| 1 | COSGN00    | app/cpy/COSGN00.*   | BMS symbolic map      | Generated from COSGN00.bms; defines COSGN0AI (input) and COSGN0AO (output) structures |

> **Note:** The COPY COSGN00 statement includes the BMS-generated symbolic map. The BMS source is at `app/bms/COSGN00.bms`. The symbolic map copybook may exist as `COSGN00.cpy` in `app/cpy/` or may be generated at compile time. In this repository, it is **not present** as a separate `.cpy` file in `app/cpy/` — it is generated from the BMS source during the build process.

### BMS Map Source Files

| # | File Name   | Path                 | Mapset  | Map     |
|---|-------------|----------------------|---------|---------|
| 1 | COSGN00.bms | app/bms/COSGN00.bms | COSGN00 | COSGN0A |

### VSAM Data Files

| # | DD Name | CSD Dataset Name                        | Type      | Key          |
|---|---------|-----------------------------------------|-----------|--------------|
| 1 | USRSEC  | AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS       | VSAM KSDS | User ID (8B) |

---

## Section 9: Transaction Record Layout

### USRSEC — User Security Record (Read-Only by COSGN00C)

COSGN00C only reads this file; it does not write, rewrite, or delete records.

```
Offset  Length  Field            PIC        Description
------  ------  ---------------  ---------  ----------------------------------
  1       8     SEC-USR-ID       X(08)      User ID (primary key)
  9      20     SEC-USR-FNAME    X(20)      User first name
 29      20     SEC-USR-LNAME    X(20)      User last name
 49       8     SEC-USR-PWD      X(08)      User password (plain text)
 57       1     SEC-USR-TYPE     X(01)      User type: 'A'=Admin, 'U'=Regular
 58      23     SEC-USR-FILLER   X(23)      Reserved/filler
------  ------
Total:  80 bytes
```

---

## Section 10: Observations

### What This Flow Demonstrates Well

1. **Clean pseudo-conversational pattern:** The program uses the standard CICS pattern of SEND MAP → RETURN TRANSID → RECEIVE MAP on re-entry. The use of `EIBCALEN = 0` as the first-entry guard is idiomatic.

2. **Simple, focused responsibility:** COSGN00C has a single purpose — authenticate the user and route to the appropriate menu. It accesses only one VSAM file (USRSEC) and performs only one I/O operation (READ).

3. **Clear XCTL branching:** The program cleanly separates admin vs. regular user flows via `CDEMO-USRTYP-ADMIN` condition, transferring control to two distinct menu programs.

4. **COMMAREA initialization:** The program properly initializes COMMAREA navigation fields (`FROM-TRANID`, `FROM-PROGRAM`, `USER-ID`, `USER-TYPE`, `PGM-CONTEXT`) before transferring control, establishing the contract for downstream programs.

5. **Input validation:** Both User ID and Password are validated for emptiness before the VSAM lookup, avoiding unnecessary I/O.

6. **RESP code handling:** All three RESP outcomes (NORMAL, NOTFND, OTHER) are handled with user-friendly messages.

7. **Uppercase normalization:** User input is converted to uppercase using `FUNCTION UPPER-CASE` before comparison, ensuring case-insensitive authentication.

### Gaps and Migration Considerations

1. **Plain-text password storage:** Passwords are stored and compared in plain text in the USRSEC VSAM file. A modernized system must implement hashed/encrypted password storage and comparison.

2. **No account lockout:** There is no mechanism to track failed login attempts or lock accounts after repeated failures. This is a security gap for the modernized system.

3. **No session management:** The sign-on does not create a CICS session token or security context beyond the COMMAREA. In a modernized environment, proper session/token management (e.g., JWT) would be needed.

4. **No password complexity enforcement:** The program accepts any 8-character string as a password with no complexity rules.

5. **No audit logging:** Successful and failed login attempts are not logged to any audit file or transaction journal.

6. **BMS-generated copybook dependency:** The `COPY COSGN00` statement relies on a BMS-generated symbolic map that is not present as a static copybook in the repository. Migration tooling must account for BMS map compilation generating these copybooks.

7. **XCTL removes caller from chain:** When COSGN00C transfers control via XCTL, it is removed from the program chain. The menu programs must explicitly XCTL back to `COSGN00C` to return to the sign-on screen — there is no automatic "return" mechanism. This pattern must be replicated in the modernized navigation.

8. **COMMAREA size is fixed:** All programs share the same 160-byte COMMAREA regardless of which fields they use. This is a common mainframe pattern but could be replaced with more targeted data transfer in a modern architecture.

---

## Section 11: Comparison — Flow Complexity Summary

| Dimension                    | CC00 (COSGN00C)                     | Notes                                    |
|------------------------------|--------------------------------------|------------------------------------------|
| **Programs in direct flow**  | 3 (COSGN00C + 2 XCTL targets)       | Low complexity                           |
| **Total downstream programs**| 17+ (via menu chains)                | Sign-on is gateway to entire application |
| **Copybooks used**           | 8 (5 app + 2 system + 1 BMS-gen)    | Moderate                                 |
| **VSAM files accessed**      | 1 (USRSEC, read-only)               | Minimal I/O                              |
| **BMS maps**                 | 1 mapset, 1 map                      | Single screen                            |
| **EXEC CICS commands**       | 8 distinct commands                  | See detail below                         |
| **CALL statements**          | 0                                    | No sub-program calls                     |
| **Lines of COBOL**           | 261                                  | Small program                            |
| **Pseudo-conv iterations**   | 2 (first entry + re-entry)           | Simple lifecycle                         |
| **Input fields**             | 2 (User ID, Password)               | Minimal                                  |
| **Error conditions handled** | 6                                    | Thorough for scope                       |

### All EXEC CICS Commands in COSGN00C

| # | Command                | Location (Para)        | Parameters                                                  |
|---|------------------------|------------------------|-------------------------------------------------------------|
| 1 | RECEIVE MAP            | PROCESS-ENTER-KEY      | MAP('COSGN0A') MAPSET('COSGN00') RESP RESP2                |
| 2 | SEND MAP               | SEND-SIGNON-SCREEN     | MAP('COSGN0A') MAPSET('COSGN00') FROM(COSGN0AO) ERASE CURSOR |
| 3 | SEND TEXT              | SEND-PLAIN-TEXT        | FROM(WS-MESSAGE) LENGTH ERASE FREEKB                       |
| 4 | RETURN (exit)          | SEND-PLAIN-TEXT        | *(no TRANSID — terminal return)*                            |
| 5 | ASSIGN APPLID          | POPULATE-HEADER-INFO   | APPLID(APPLIDO OF COSGN0AO)                                |
| 6 | ASSIGN SYSID           | POPULATE-HEADER-INFO   | SYSID(SYSIDO OF COSGN0AO)                                  |
| 7 | READ                   | READ-USER-SEC-FILE     | DATASET(WS-USRSEC-FILE) INTO(SEC-USER-DATA) RIDFLD(WS-USER-ID) KEYLENGTH RESP RESP2 |
| 8 | XCTL (Admin)           | READ-USER-SEC-FILE     | PROGRAM('COADM01C') COMMAREA(CARDDEMO-COMMAREA)            |
| 9 | XCTL (Regular)         | READ-USER-SEC-FILE     | PROGRAM('COMEN01C') COMMAREA(CARDDEMO-COMMAREA)            |
| 10| RETURN TRANSID         | MAIN-PARA              | TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA) LENGTH      |

# COADM01C (CA00) — Admin Menu Transaction Flow Analysis

## Section 1: Flow Overview

| Attribute            | Value                                              |
|----------------------|----------------------------------------------------|
| **Transaction ID**   | CA00                                               |
| **Entry Program**    | COADM01C                                           |
| **Function**         | Admin Menu for Admin users                         |
| **Program Type**     | CICS COBOL (pseudo-conversational)                 |
| **Data Access**      | VSAM (no DB2 or IMS)                               |
| **BMS Mapset**       | COADM01 (map COADM1A)                             |

### Complete Navigation Path

```
+------------------+        +------------------+        +------------------+
|    COSGN00C      |  XCTL  |    COADM01C      |  XCTL  |    COUSR00C      |
|  (CC00 Sign-on)  |------->|  (CA00 Admin     |------->|  (CU00 User List)|
|                  |  admin  |   Menu)          | opt 1  |                  |
+------------------+  user   +------------------+        +------------------+
                              |  XCTL opt 2              |  XCTL 'U'
                              v                          v
                      +------------------+        +------------------+
                      |    COUSR01C      |        |    COUSR02C      |
                      |  (CU01 User Add) |        |  (CU02 User Upd) |
                      +------------------+        +------------------+
                              |  XCTL opt 3
                              v
                      +------------------+        +------------------+
                      |    COUSR02C      |        |    COUSR03C      |
                      |  (CU02 User Upd) |        |  (CU03 User Del) |
                      +------------------+        +------------------+
                              |  XCTL opt 4       ^  XCTL 'D' from
                              v                      COUSR00C
                      +------------------+
                      |    COUSR03C      |
                      |  (CU03 User Del) |
                      +------------------+
                              |  XCTL opt 5 (DB2)
                              v
                      +------------------+
                      |    COTRTLIC      |
                      |  (not in repo)   |
                      +------------------+
                              |  XCTL opt 6 (DB2)
                              v
                      +------------------+
                      |    COTRTUPC      |
                      |  (not in repo)   |
                      +------------------+
```

**Return Path (PF3):**
```
COUSR00C / COUSR01C / COUSR02C / COUSR03C  --XCTL-->  COADM01C  --XCTL-->  COSGN00C
```

### Pseudo-Conversational Lifecycle

```
                  CICS Task Start
                       |
              +--------v--------+
              | EIBCALEN = 0?   |
              +---+--------+----+
              YES |        | NO
                  v        v
          XCTL to    COMMAREA present
          COSGN00C   restore state
                       |
              +--------v--------+
              | CDEMO-PGM-      |
              | REENTER = 0?    |
              +---+--------+----+
              YES |        | NO (re-entry)
                  v        v
          Set REENTER=1   RECEIVE MAP
          SEND menu       EVALUATE EIBAID
          (first display)      |
                  |       +----+----+----+
                  |       |    |         |
                  v       v    v         v
              RETURN   ENTER  PF3     OTHER
              TRANSID  key    key     (invalid)
              'CA00'    |      |         |
                        v      v         v
                   Process  XCTL to   Error msg
                   option   COSGN00C  SEND menu
                     |                    |
                     v                    v
                XCTL to target       RETURN
                program              TRANSID
                (or error msg)       'CA00'
```

---

## Section 2: Programs Involved

| Program    | Type          | Function                          | Called By   | Call Method | Transaction | Source Path        |
|------------|---------------|-----------------------------------|-------------|-------------|-------------|--------------------|
| COSGN00C   | CICS COBOL    | Sign-on Screen                    | (entry)     | —           | CC00        | app/cbl/COSGN00C.cbl |
| COADM01C   | CICS COBOL    | Admin Menu                        | COSGN00C    | XCTL        | CA00        | app/cbl/COADM01C.cbl |
| COUSR00C   | CICS COBOL    | User List (Security)              | COADM01C    | XCTL (opt 1)| CU00        | app/cbl/COUSR00C.cbl |
| COUSR01C   | CICS COBOL    | User Add (Security)               | COADM01C    | XCTL (opt 2)| CU01        | app/cbl/COUSR01C.cbl |
| COUSR02C   | CICS COBOL    | User Update (Security)            | COADM01C    | XCTL (opt 3)| CU02        | app/cbl/COUSR02C.cbl |
| COUSR03C   | CICS COBOL    | User Delete (Security)            | COADM01C    | XCTL (opt 4)| CU03        | app/cbl/COUSR03C.cbl |
| COTRTLIC   | CICS COBOL    | Transaction Type List/Update (DB2)| COADM01C    | XCTL (opt 5)| —           | **Not in repo** (DB2 feature) |
| COTRTUPC   | CICS COBOL    | Transaction Type Maintenance (DB2)| COADM01C    | XCTL (opt 6)| —           | **Not in repo** (DB2 feature) |

**Notes:**
- Options 5 and 6 (COTRTLIC, COTRTUPC) are DB2-specific extensions added for a DB2 release. Their source is not present in the VSAM-only repository.
- COADM01C uses `HANDLE CONDITION PGMIDERR` to gracefully handle missing programs — if an XCTL target does not exist, the PGMIDERR handler displays "This option is not installed ...".
- All downstream programs (COUSR00C–COUSR03C) navigate back to COADM01C via XCTL on PF3/PF12.

---

## Section 3: Copybooks

### Copybooks Used by COADM01C

| Copybook   | Used By            | Type                    | Purpose                                                  | Source Path            |
|------------|--------------------|-------------------------|----------------------------------------------------------|------------------------|
| COCOM01Y   | COADM01C + all pgms| COMMAREA definition     | Defines CARDDEMO-COMMAREA — shared navigation & data     | app/cpy/COCOM01Y.cpy  |
| COADM02Y   | COADM01C           | Menu option table       | Admin menu options array (program names, descriptions)    | app/cpy/COADM02Y.cpy  |
| COADM01    | COADM01C           | BMS symbolic map        | Auto-generated from COADM01.bms — defines COADM1AI/COADM1AO | app/cpy/COADM01.cpy (generated) |
| COTTL01Y   | COADM01C + all pgms| Screen title constants  | CCDA-TITLE01, CCDA-TITLE02, CCDA-THANK-YOU               | app/cpy/COTTL01Y.cpy   |
| CSDAT01Y   | COADM01C + all pgms| Date/time structure     | WS-CURDATE-DATA, WS-CURTIME, formatted display fields    | app/cpy/CSDAT01Y.cpy  |
| CSMSG01Y   | COADM01C + all pgms| Common messages         | CCDA-MSG-THANK-YOU, CCDA-MSG-INVALID-KEY                 | app/cpy/CSMSG01Y.cpy  |
| CSUSR01Y   | COADM01C + all pgms| User security record    | SEC-USER-DATA record layout for USRSEC VSAM file         | app/cpy/CSUSR01Y.cpy  |
| DFHAID     | COADM01C + all pgms| CICS system copybook    | AID key constants (DFHENTER, DFHPF3, etc.)               | **IBM system copybook** |
| DFHBMSCA   | COADM01C + all pgms| CICS system copybook    | BMS attribute constants (DFHGREEN, DFHRED, etc.)         | **IBM system copybook** |

### Record Layout: COCOM01Y (COMMAREA)

```
01 CARDDEMO-COMMAREA.
   05 CDEMO-GENERAL-INFO.
      10 CDEMO-FROM-TRANID             PIC X(04).
      10 CDEMO-FROM-PROGRAM            PIC X(08).
      10 CDEMO-TO-TRANID               PIC X(04).
      10 CDEMO-TO-PROGRAM              PIC X(08).
      10 CDEMO-USER-ID                 PIC X(08).
      10 CDEMO-USER-TYPE               PIC X(01).
         88 CDEMO-USRTYP-ADMIN         VALUE 'A'.
         88 CDEMO-USRTYP-USER          VALUE 'U'.
      10 CDEMO-PGM-CONTEXT             PIC 9(01).
         88 CDEMO-PGM-ENTER            VALUE 0.
         88 CDEMO-PGM-REENTER          VALUE 1.
   05 CDEMO-CUSTOMER-INFO.
      10 CDEMO-CUST-ID                 PIC 9(09).
      10 CDEMO-CUST-FNAME              PIC X(25).
      10 CDEMO-CUST-MNAME              PIC X(25).
      10 CDEMO-CUST-LNAME              PIC X(25).
   05 CDEMO-ACCOUNT-INFO.
      10 CDEMO-ACCT-ID                 PIC 9(11).
      10 CDEMO-ACCT-STATUS             PIC X(01).
   05 CDEMO-CARD-INFO.
      10 CDEMO-CARD-NUM                PIC 9(16).
   05 CDEMO-MORE-INFO.
      10 CDEMO-LAST-MAP                PIC X(7).
      10 CDEMO-LAST-MAPSET             PIC X(7).
```

### Record Layout: COADM02Y (Admin Menu Options)

```
01 CARDDEMO-ADMIN-MENU-OPTIONS.
   05 CDEMO-ADMIN-OPT-COUNT           PIC 9(02) VALUE 6.
   05 CDEMO-ADMIN-OPTIONS-DATA.
      (6 entries, each 45 bytes: 2-byte num + 35-byte name + 8-byte pgmname)
      Option 1: '01' 'User List (Security)               ' 'COUSR00C'
      Option 2: '02' 'User Add (Security)                ' 'COUSR01C'
      Option 3: '03' 'User Update (Security)             ' 'COUSR02C'
      Option 4: '04' 'User Delete (Security)             ' 'COUSR03C'
      Option 5: '05' 'Transaction Type List/Update (Db2) ' 'COTRTLIC'
      Option 6: '06' 'Transaction Type Maintenance (Db2) ' 'COTRTUPC'
   05 CDEMO-ADMIN-OPTIONS REDEFINES CDEMO-ADMIN-OPTIONS-DATA.
      10 CDEMO-ADMIN-OPT OCCURS 9 TIMES.
         15 CDEMO-ADMIN-OPT-NUM        PIC 9(02).
         15 CDEMO-ADMIN-OPT-NAME       PIC X(35).
         15 CDEMO-ADMIN-OPT-PGMNAME    PIC X(08).
```

### Record Layout: CSUSR01Y (User Security Record)

```
01 SEC-USER-DATA.
   05 SEC-USR-ID                 PIC X(08).    <- VSAM key
   05 SEC-USR-FNAME              PIC X(20).
   05 SEC-USR-LNAME              PIC X(20).
   05 SEC-USR-PWD                PIC X(08).
   05 SEC-USR-TYPE               PIC X(01).    <- 'A' = admin, 'U' = user
   05 SEC-USR-FILLER             PIC X(23).
   Total: 80 bytes
```

### Record Layout: COTTL01Y (Screen Titles)

```
01 CCDA-SCREEN-TITLE.
   05 CCDA-TITLE01    PIC X(40) VALUE '      AWS Mainframe Modernization       '.
   05 CCDA-TITLE02    PIC X(40) VALUE '              CardDemo                  '.
   05 CCDA-THANK-YOU  PIC X(40) VALUE 'Thank you for using CCDA application... '.
```

### Record Layout: CSDAT01Y (Date/Time)

```
01 WS-DATE-TIME.
   05 WS-CURDATE-DATA.
      10 WS-CURDATE.
         15 WS-CURDATE-YEAR         PIC 9(04).
         15 WS-CURDATE-MONTH        PIC 9(02).
         15 WS-CURDATE-DAY          PIC 9(02).
      10 WS-CURDATE-N REDEFINES WS-CURDATE PIC 9(08).
      10 WS-CURTIME.
         15 WS-CURTIME-HOURS        PIC 9(02).
         15 WS-CURTIME-MINUTE       PIC 9(02).
         15 WS-CURTIME-SECOND       PIC 9(02).
         15 WS-CURTIME-MILSEC       PIC 9(02).
      10 WS-CURTIME-N REDEFINES WS-CURTIME PIC 9(08).
   05 WS-CURDATE-MM-DD-YY.
      10 WS-CURDATE-MM              PIC 9(02).
      10 FILLER                     PIC X(01) VALUE '/'.
      10 WS-CURDATE-DD              PIC 9(02).
      10 FILLER                     PIC X(01) VALUE '/'.
      10 WS-CURDATE-YY              PIC 9(02).
   05 WS-CURTIME-HH-MM-SS.
      10 WS-CURTIME-HH              PIC 9(02).
      10 FILLER                     PIC X(01) VALUE ':'.
      10 WS-CURTIME-MM              PIC 9(02).
      10 FILLER                     PIC X(01) VALUE ':'.
      10 WS-CURTIME-SS              PIC 9(02).
   05 WS-TIMESTAMP.
      (YYYY-MM-DD HH:MM:SS.ffffff format)
```

### Record Layout: CSMSG01Y (Common Messages)

```
01 CCDA-COMMON-MESSAGES.
   05 CCDA-MSG-THANK-YOU         PIC X(50) VALUE
        'Thank you for using CardDemo application...      '.
   05 CCDA-MSG-INVALID-KEY       PIC X(50) VALUE
        'Invalid key pressed. Please see below...         '.
```

---

## Section 4: VSAM File Operations

### COADM01C Direct File Operations

**COADM01C itself does NOT perform any direct VSAM file I/O.** It is purely a menu/navigation program. However, it declares `WS-USRSEC-FILE PIC X(08) VALUE 'USRSEC  '` in WORKING-STORAGE (inherited pattern from the application template), though this variable is never used in an EXEC CICS READ/WRITE within COADM01C.

### VSAM Files in the Admin Menu Flow (Downstream Programs)

| VSAM File | DD Name  | Key Field    | Key Length | Record Copybook | Record Length | Programs Using It           |
|-----------|----------|-------------|------------|-----------------|---------------|-----------------------------|
| USRSEC    | USRSEC   | SEC-USR-ID  | 8 bytes    | CSUSR01Y        | 80 bytes      | COSGN00C, COUSR00C, COUSR01C, COUSR02C, COUSR03C |

### File Access Patterns by Program

| Program   | Operation     | CICS Command                  | Key/RIDFLD   | Purpose                      |
|-----------|---------------|-------------------------------|--------------|------------------------------|
| COSGN00C  | Direct READ   | `EXEC CICS READ DATASET(USRSEC)` | WS-USER-ID   | Validate user credentials    |
| COUSR00C  | STARTBR       | `EXEC CICS STARTBR DATASET(USRSEC)` | SEC-USR-ID | Position browse cursor       |
| COUSR00C  | READNEXT      | `EXEC CICS READNEXT DATASET(USRSEC)` | SEC-USR-ID | Forward page through users   |
| COUSR00C  | READPREV      | `EXEC CICS READPREV DATASET(USRSEC)` | SEC-USR-ID | Backward page through users  |
| COUSR00C  | ENDBR         | `EXEC CICS ENDBR DATASET(USRSEC)` | —          | End browse session           |
| COUSR01C  | WRITE         | `EXEC CICS WRITE DATASET(USRSEC)` | SEC-USR-ID  | Add new user record          |
| COUSR02C  | READ UPDATE   | `EXEC CICS READ DATASET(USRSEC) UPDATE` | SEC-USR-ID | Read user for update    |
| COUSR02C  | REWRITE       | `EXEC CICS REWRITE DATASET(USRSEC)` | —          | Save user changes            |
| COUSR03C  | READ UPDATE   | `EXEC CICS READ DATASET(USRSEC) UPDATE` | SEC-USR-ID | Read user for delete    |
| COUSR03C  | DELETE        | `EXEC CICS DELETE DATASET(USRSEC)` | —           | Remove user record           |

### VSAM File Flow Diagram

```
                        USRSEC (VSAM KSDS)
                        Key: SEC-USR-ID (8 bytes)
                        Record: 80 bytes (CSUSR01Y)
                              |
         +--------------------+--------------------+
         |                    |                    |
    +----v----+         +----v----+          +----v----+
    | COSGN00C|         | COUSR00C|          | COUSR01C|
    | READ    |         | STARTBR |          | WRITE   |
    | (auth)  |         | READNEXT|          | (add)   |
    +----+----+         | READPREV|          +---------+
         |              | ENDBR   |
         |              +----+----+          +----v----+
         |                   |               | COUSR02C|
         |                   |               | READ UPD|
         |                   |               | REWRITE |
         |                   |               +---------+
         |                   |
         |                   |               +----v----+
         |                   |               | COUSR03C|
         |                   |               | READ UPD|
         |                   |               | DELETE  |
         |                   |               +---------+
         |                   |
    Sign-on auth        User listing         CRUD operations
```

---

## Section 5: BMS Screen Map

### BMS Source: COADM01.bms

- **Mapset:** COADM01
- **Map:** COADM1A
- **Size:** 24 rows x 80 columns
- **Attributes:** CTRL=(ALARM,FREEKB), EXTATT=YES, LANG=COBOL, MODE=INOUT, STORAGE=AUTO

### ASCII Screen Layout

```
Row Col  Field
 1   1   Tran: CA00       AWS Mainframe Modernization              Date: mm/dd/yy
 2   1   Prog: COADM01C              CardDemo                     Time: hh:mm:ss
 3
 4  35                    Admin Menu
 5
 6  20   1. User List (Security)
 7  20   2. User Add (Security)
 8  20   3. User Update (Security)
 9  20   4. User Delete (Security)
10  20   5. Transaction Type List/Update (Db2)
11  20   6. Transaction Type Maintenance (Db2)
12  20   (option 7 slot — unused)
13  20   (option 8 slot — unused)
14  20   (option 9 slot — unused)
15  20   (option 10 slot — unused)
16  20   (option 11 slot — unused)
17  20   (option 12 slot — unused)
18
19
20  15   Please select an option : __
21
22
23   1   <error message area — 78 chars, red>
24   1   ENTER=Continue  F3=Exit
```

### Field Inventory

| Field    | Row | Col | Length | Attributes            | Color    | Purpose                          |
|----------|-----|-----|--------|-----------------------|----------|----------------------------------|
| TRNNAME  | 1   | 7   | 4      | ASKIP, FSET, NORM     | Blue     | Transaction ID display           |
| TITLE01  | 1   | 21  | 40     | ASKIP, FSET, NORM     | Yellow   | Application title line 1         |
| CURDATE  | 1   | 71  | 8      | ASKIP, FSET, NORM     | Blue     | Current date (mm/dd/yy)          |
| PGMNAME  | 2   | 7   | 8      | ASKIP, FSET, NORM     | Blue     | Program name display             |
| TITLE02  | 2   | 21  | 40     | ASKIP, FSET, NORM     | Yellow   | Application title line 2         |
| CURTIME  | 2   | 71  | 8      | ASKIP, FSET, NORM     | Blue     | Current time (hh:mm:ss)          |
| (label)  | 4   | 35  | 10     | ASKIP, BRT            | Neutral  | "Admin Menu" literal             |
| OPTN001  | 6   | 20  | 40     | ASKIP, FSET, NORM     | Blue     | Menu option 1 text               |
| OPTN002  | 7   | 20  | 40     | ASKIP, FSET, NORM     | Blue     | Menu option 2 text               |
| OPTN003  | 8   | 20  | 40     | ASKIP, FSET, NORM     | Blue     | Menu option 3 text               |
| OPTN004  | 9   | 20  | 40     | ASKIP, FSET, NORM     | Blue     | Menu option 4 text               |
| OPTN005  | 10  | 20  | 40     | ASKIP, FSET, NORM     | Blue     | Menu option 5 text               |
| OPTN006  | 11  | 20  | 40     | ASKIP, FSET, NORM     | Blue     | Menu option 6 text               |
| OPTN007  | 12  | 20  | 40     | ASKIP, FSET, NORM     | Blue     | Menu option 7 text               |
| OPTN008  | 13  | 20  | 40     | ASKIP, FSET, NORM     | Blue     | Menu option 8 text               |
| OPTN009  | 14  | 20  | 40     | ASKIP, FSET, NORM     | Blue     | Menu option 9 text               |
| OPTN010  | 15  | 20  | 40     | ASKIP, FSET, NORM     | Blue     | Menu option 10 text              |
| OPTN011  | 16  | 20  | 40     | ASKIP, FSET, NORM     | Blue     | Menu option 11 text              |
| OPTN012  | 17  | 20  | 40     | ASKIP, FSET, NORM     | Blue     | Menu option 12 text              |
| OPTION   | 20  | 41  | 2      | FSET, IC, NUM, UNPROT | (underline) | **User input:** option number |
| ERRMSG   | 23  | 1   | 78     | ASKIP, BRT, FSET      | Red      | Error/info message area          |
| (label)  | 24  | 1   | 23     | ASKIP, NORM           | Yellow   | "ENTER=Continue  F3=Exit"        |

### Function Key Assignments

| Key     | Action in COADM01C                                         |
|---------|-------------------------------------------------------------|
| ENTER   | Process selected menu option — validate and XCTL to target  |
| PF3     | Return to sign-on screen (XCTL to COSGN00C)                |
| Other   | Display "Invalid key pressed" error message                 |

---

## Section 6: COMMAREA Structure

### COMMAREA Tree Diagram

```
CARDDEMO-COMMAREA
├── CDEMO-GENERAL-INFO
│   ├── CDEMO-FROM-TRANID        PIC X(04)   Navigation: source transaction
│   ├── CDEMO-FROM-PROGRAM       PIC X(08)   Navigation: source program
│   ├── CDEMO-TO-TRANID          PIC X(04)   Navigation: target transaction
│   ├── CDEMO-TO-PROGRAM         PIC X(08)   Navigation: target program
│   ├── CDEMO-USER-ID            PIC X(08)   Authenticated user ID
│   ├── CDEMO-USER-TYPE          PIC X(01)   'A'=admin, 'U'=regular
│   └── CDEMO-PGM-CONTEXT        PIC 9(01)   0=first entry, 1=re-entry
├── CDEMO-CUSTOMER-INFO
│   ├── CDEMO-CUST-ID            PIC 9(09)   (not used in admin flow)
│   ├── CDEMO-CUST-FNAME         PIC X(25)   (not used in admin flow)
│   ├── CDEMO-CUST-MNAME         PIC X(25)   (not used in admin flow)
│   └── CDEMO-CUST-LNAME         PIC X(25)   (not used in admin flow)
├── CDEMO-ACCOUNT-INFO
│   ├── CDEMO-ACCT-ID            PIC 9(11)   (not used in admin flow)
│   └── CDEMO-ACCT-STATUS        PIC X(01)   (not used in admin flow)
├── CDEMO-CARD-INFO
│   └── CDEMO-CARD-NUM           PIC 9(16)   (not used in admin flow)
└── CDEMO-MORE-INFO
    ├── CDEMO-LAST-MAP           PIC X(7)    Last BMS map displayed
    └── CDEMO-LAST-MAPSET        PIC X(7)    Last BMS mapset displayed
```

### COMMAREA Field Read/Write by Program

| Field                | COSGN00C      | COADM01C      | COUSR00C      | COUSR01C      | COUSR02C      | COUSR03C      |
|----------------------|---------------|---------------|---------------|---------------|---------------|---------------|
| CDEMO-FROM-TRANID    | **Write**     | **Write**     | **Write**     | **Write**     | **Write**     | **Write**     |
| CDEMO-FROM-PROGRAM   | **Write**     | **Write**     | **Write**     | **Write**     | **Write**     | **Write**     |
| CDEMO-TO-TRANID      | —             | —             | —             | —             | —             | —             |
| CDEMO-TO-PROGRAM     | —             | Read/**Write**| **Write**/Read| **Write**/Read| **Write**/Read| **Write**/Read|
| CDEMO-USER-ID        | **Write**     | Read          | Read          | Read          | Read          | Read          |
| CDEMO-USER-TYPE      | **Write**     | Read          | Read          | Read          | Read          | Read          |
| CDEMO-PGM-CONTEXT    | **Write** (0) | Read/**Write**| Read/**Write**| Read/**Write**| Read/**Write**| Read/**Write**|

### Inter-Program COMMAREA Flow

```
COSGN00C sets:                  COADM01C sets:              COUSR0xC sets:
  CDEMO-FROM-TRANID = 'CC00'     CDEMO-FROM-TRANID = 'CA00'  CDEMO-FROM-TRANID = 'CUxx'
  CDEMO-FROM-PROGRAM = 'COSGN00C' CDEMO-FROM-PROGRAM = 'COADM01C' CDEMO-FROM-PROGRAM = 'COUSR0xC'
  CDEMO-USER-ID = <user>         CDEMO-PGM-CONTEXT = 0       CDEMO-PGM-CONTEXT = 0
  CDEMO-USER-TYPE = 'A'                                      CDEMO-TO-PROGRAM = 'COADM01C'
  CDEMO-PGM-CONTEXT = 0                                        (for PF3 return)
       |                              |                             |
       +--- XCTL to COADM01C --->     +--- XCTL to COUSR0xC --->   +--- XCTL back to COADM01C
```

---

## Section 7: Business Logic Flow

### Step-by-Step Execution of COADM01C

#### 1. Entry and Initialization
1. `EXEC CICS HANDLE CONDITION PGMIDERR(PGMIDERR-ERR-PARA)` — Register handler for missing programs
2. Set `ERR-FLG-OFF` to TRUE
3. Clear `WS-MESSAGE` and `ERRMSGO` output field

#### 2. EIBCALEN Check (Direct Invocation Guard)
- **IF EIBCALEN = 0** (no COMMAREA — direct invocation or abnormal entry):
  - Set `CDEMO-FROM-PROGRAM = 'COSGN00C'`
  - XCTL to COSGN00C (redirect to sign-on)
- **ELSE** (normal transfer from another program with COMMAREA):
  - Restore COMMAREA: `MOVE DFHCOMMAREA(1:EIBCALEN) TO CARDDEMO-COMMAREA`

#### 3. First Entry vs Re-Entry
- **IF NOT CDEMO-PGM-REENTER** (PGM-CONTEXT = 0, first time):
  - Set `CDEMO-PGM-REENTER` to TRUE (PGM-CONTEXT = 1)
  - Initialize output map: `MOVE LOW-VALUES TO COADM1AO`
  - PERFORM `SEND-MENU-SCREEN` (populate header, build options, send map)
- **ELSE** (re-entry, user has submitted input):
  - PERFORM `RECEIVE-MENU-SCREEN`
  - EVALUATE EIBAID (which key was pressed)

#### 4. Key Processing
- **DFHENTER (Enter key):** PERFORM `PROCESS-ENTER-KEY`
- **DFHPF3 (PF3 key):**
  - Set `CDEMO-TO-PROGRAM = 'COSGN00C'`
  - PERFORM `RETURN-TO-SIGNON-SCREEN` (XCTL to sign-on)
- **OTHER:** Set error flag, display "Invalid key pressed" message

#### 5. PROCESS-ENTER-KEY — Option Validation and Dispatch
1. **Extract and normalize option input:**
   - Trim trailing spaces from `OPTIONI` of `COADM1AI`
   - Replace spaces with zeros, move to `WS-OPTION` (numeric)
2. **Validate option:**
   - Check `WS-OPTION IS NUMERIC`
   - Check `WS-OPTION <= CDEMO-ADMIN-OPT-COUNT` (currently 6)
   - Check `WS-OPTION > 0`
   - If invalid: set error flag, display "Please enter a valid option number..."
3. **Dispatch to target program:**
   - Check if target program name starts with 'DUMMY' (placeholder check)
   - **If real program:**
     - Set `CDEMO-FROM-TRANID = 'CA00'`
     - Set `CDEMO-FROM-PROGRAM = 'COADM01C'`
     - Set `CDEMO-PGM-CONTEXT = 0` (first entry for target)
     - `EXEC CICS XCTL PROGRAM(CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION)) COMMAREA(CARDDEMO-COMMAREA)`
   - **If DUMMY or PGMIDERR occurs:**
     - Display "This option is not installed ..." in green

#### 6. SEND-MENU-SCREEN — Screen Assembly
1. PERFORM `POPULATE-HEADER-INFO`:
   - Get current date/time via `FUNCTION CURRENT-DATE`
   - Set title lines (CCDA-TITLE01, CCDA-TITLE02)
   - Set transaction ID and program name
   - Format date as MM/DD/YY and time as HH:MM:SS
2. PERFORM `BUILD-MENU-OPTIONS`:
   - Loop through `CDEMO-ADMIN-OPT-COUNT` entries (1 to 6)
   - Build display string: `"NN. Description"` for each option
   - Move to corresponding `OPTNxxxO` field (OPTN001O through OPTN010O)
3. Move `WS-MESSAGE` to `ERRMSGO`
4. `EXEC CICS SEND MAP('COADM1A') MAPSET('COADM01') FROM(COADM1AO) ERASE`

#### 7. Pseudo-Conversational Return
- `EXEC CICS RETURN TRANSID('CA00') COMMAREA(CARDDEMO-COMMAREA)`
- CICS suspends the task; terminal waits for user input
- On next user input, CICS starts a new task with transaction ID CA00

#### 8. Error Handling
- **PGMIDERR handler:** When `EXEC CICS XCTL` fails because the target program is not defined in the CICS region, control goes to `PGMIDERR-ERR-PARA` which displays "This option is not installed ..." and returns to the menu.
- **Invalid key:** Any key other than ENTER or PF3 produces "Invalid key pressed" message.
- **Invalid option number:** Non-numeric, zero, or out-of-range option produces "Please enter a valid option number..." message.

---

## Section 8: Complete File Inventory

### COBOL Source Files

| File                       | Program   | Role in CA00 Flow                |
|----------------------------|-----------|----------------------------------|
| app/cbl/COADM01C.cbl      | COADM01C  | **Target program** — Admin Menu  |
| app/cbl/COSGN00C.cbl      | COSGN00C  | Upstream — Sign-on screen        |
| app/cbl/COUSR00C.cbl      | COUSR00C  | Downstream — User List           |
| app/cbl/COUSR01C.cbl      | COUSR01C  | Downstream — User Add            |
| app/cbl/COUSR02C.cbl      | COUSR02C  | Downstream — User Update         |
| app/cbl/COUSR03C.cbl      | COUSR03C  | Downstream — User Delete         |

### Copybook Files

| File                       | Type                   | Used By                          |
|----------------------------|------------------------|----------------------------------|
| app/cpy/COCOM01Y.cpy      | COMMAREA definition    | All programs in flow             |
| app/cpy/COADM02Y.cpy      | Menu option table      | COADM01C                         |
| app/cpy/COTTL01Y.cpy      | Screen title constants | All programs in flow             |
| app/cpy/CSDAT01Y.cpy      | Date/time structure    | All programs in flow             |
| app/cpy/CSMSG01Y.cpy      | Common messages        | All programs in flow             |
| app/cpy/CSUSR01Y.cpy      | User security record   | All programs in flow             |

### BMS Symbolic Map Copybooks (auto-generated from BMS, in app/cpy/)

| File                       | Type                   | Used By                          |
|----------------------------|------------------------|----------------------------------|
| app/cpy/COADM01.cpy       | BMS symbolic map       | COADM01C (COPY COADM01)         |

### BMS Map Source Files

| File                       | Mapset   | Map      | Used By                          |
|----------------------------|----------|----------|----------------------------------|
| app/bms/COADM01.bms       | COADM01  | COADM1A  | COADM01C                         |

### CICS System Copybooks (not in repository)

| Copybook   | Purpose                                          |
|------------|--------------------------------------------------|
| DFHAID     | AID key constants (DFHENTER, DFHPF3, etc.)      |
| DFHBMSCA   | BMS attribute constants (DFHGREEN, DFHRED, etc.) |

### VSAM Data Files

| DD Name  | Description              | Key           | Record Copybook | CSD Definition               |
|----------|--------------------------|---------------|-----------------|------------------------------|
| USRSEC   | User security records    | SEC-USR-ID (8)| CSUSR01Y        | app/csd/CARDDEMO.CSD line 88 |

### CSD Definitions

| File                       | Relevant Entries                                 |
|----------------------------|--------------------------------------------------|
| app/csd/CARDDEMO.CSD      | DEFINE PROGRAM(COADM01C), DEFINE MAPSET(COADM01), DEFINE FILE(USRSEC) |

### Programs NOT in Repository (DB2 Extensions)

| Program   | Referenced By | Reason Missing                           |
|-----------|---------------|------------------------------------------|
| COTRTLIC  | COADM02Y opt 5 | DB2-specific; not in VSAM-only repo    |
| COTRTUPC  | COADM02Y opt 6 | DB2-specific; not in VSAM-only repo    |

---

## Section 9: Transaction Record Layout

COADM01C does not directly read or write any VSAM records. The USRSEC record layout (used by downstream programs) is:

### USRSEC Record (CSUSR01Y — 80 bytes)

| Offset | Length | Field          | PIC Clause | Description                        |
|--------|--------|----------------|------------|------------------------------------|
| 0      | 8      | SEC-USR-ID     | X(08)      | User ID (VSAM primary key)         |
| 8      | 20     | SEC-USR-FNAME  | X(20)      | First name                         |
| 28     | 20     | SEC-USR-LNAME  | X(20)      | Last name                          |
| 48     | 8      | SEC-USR-PWD    | X(08)      | Password (plaintext)               |
| 56     | 1      | SEC-USR-TYPE   | X(01)      | User type: 'A' = Admin, 'U' = User|
| 57     | 23     | SEC-USR-FILLER | X(23)      | Reserved filler                    |
| **Total** | **80** |             |            |                                    |

---

## Section 10: Observations

### What This Flow Demonstrates Well

1. **Clean pseudo-conversational pattern:** COADM01C follows the standard CICS pseudo-conversational model with clear EIBCALEN check, PGM-CONTEXT flag for first-entry vs re-entry, and RETURN TRANSID for task suspension.

2. **Table-driven menu dispatch:** The admin menu uses a copybook-defined option table (COADM02Y) with program names resolved by array index, making it easy to add/remove menu options without changing COADM01C program logic.

3. **Graceful error handling for missing programs:** The `HANDLE CONDITION PGMIDERR` pattern allows the menu to reference programs that may not be installed (e.g., DB2-specific options 5 and 6) without abending.

4. **Consistent COMMAREA navigation model:** All programs in the flow use the same COMMAREA structure (COCOM01Y) with FROM-PROGRAM, TO-PROGRAM, and PGM-CONTEXT fields, creating a uniform navigation framework.

5. **Separation of concerns:** COADM01C is purely a navigation hub — it performs no VSAM I/O itself. All data operations are delegated to the downstream programs (COUSR00C–COUSR03C).

### Gaps and Migration Considerations

1. **No COMMAREA LENGTH field:** The COMMAREA is passed without explicit length control in COADM01C's `EXEC CICS RETURN`. Downstream programs (COUSR00C, COUSR02C, COUSR03C) extend the COMMAREA with program-specific sections (e.g., `CDEMO-CU00-INFO`) appended after the base COCOM01Y definition. This overlapping COMMAREA extension pattern requires careful handling during migration.

2. **Plaintext passwords:** The USRSEC file stores passwords in plaintext (SEC-USR-PWD). Any modernized system must implement proper password hashing.

3. **HANDLE CONDITION (legacy API):** `HANDLE CONDITION PGMIDERR` is an older CICS API. The RESP/RESP2 pattern (used in RECEIVE-MENU-SCREEN) is the modern equivalent. A migration should standardize on RESP-based error handling.

4. **DB2 program stubs:** Options 5 and 6 reference COTRTLIC and COTRTUPC which are not in the VSAM-only repository. These would need to be sourced or replaced if the DB2 feature set is in scope for migration.

5. **Hard-coded program names:** While the menu table is data-driven, the return-to-signon path hard-codes `'COSGN00C'`. A migration could externalize this to configuration.

6. **No audit trail:** User CRUD operations (add/update/delete) in COUSR01C–COUSR03C do not log changes. Modern systems typically require audit logging for security-related operations.

---

## Section 11: Comparison — CICS vs Modern Target Environment

| Aspect                    | CICS (Current)                              | Modern Target (e.g., REST/Web)                |
|---------------------------|---------------------------------------------|-----------------------------------------------|
| **Navigation Model**      | XCTL chain with COMMAREA                    | URL routing / SPA navigation                  |
| **State Management**      | COMMAREA passed via RETURN TRANSID          | Session/JWT token + server-side state          |
| **Screen Definition**     | BMS map (row/col positioning)               | HTML/CSS responsive layout                    |
| **User Input**            | 3270 terminal (AID keys + fields)           | Form fields + buttons + keyboard shortcuts    |
| **Authentication**        | VSAM file lookup (plaintext pwd)            | OAuth2 / LDAP / hashed credentials            |
| **Menu Dispatch**         | COBOL table → XCTL PROGRAM(name)           | Route table → controller/handler              |
| **Error Handling**        | HANDLE CONDITION / RESP codes               | try-catch / HTTP status codes                 |
| **Data Access**           | EXEC CICS READ/WRITE VSAM                  | SQL / ORM / REST API calls                    |
| **Concurrency**           | CICS task isolation + VSAM record locking   | Database transactions + row-level locking     |
| **Pseudo-conversational** | RETURN TRANSID suspends task                | Stateless HTTP request/response               |
| **Complexity**            | Low — pure menu, no I/O, 289 LOC           | Low — equivalent to a simple route definition |

---

## EXEC CICS Commands in COADM01C — Complete Reference

| # | Command                          | Location (Paragraph)           | Parameters                                                | Error Handling                    |
|---|----------------------------------|--------------------------------|-----------------------------------------------------------|-----------------------------------|
| 1 | `HANDLE CONDITION PGMIDERR`      | MAIN-PARA (line 77-79)        | Condition: PGMIDERR → PGMIDERR-ERR-PARA                   | Branches to error paragraph       |
| 2 | `RETURN TRANSID COMMAREA`        | MAIN-PARA (line 111-114)      | TRANSID('CA00'), COMMAREA(CARDDEMO-COMMAREA)              | —                                 |
| 3 | `XCTL PROGRAM COMMAREA`         | PROCESS-ENTER-KEY (line 145-148) | PROGRAM(CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION)), COMMAREA(CARDDEMO-COMMAREA) | PGMIDERR handler |
| 4 | `XCTL PROGRAM`                   | RETURN-TO-SIGNON-SCREEN (line 168-170) | PROGRAM(CDEMO-TO-PROGRAM)                          | —                                 |
| 5 | `SEND MAP`                       | SEND-MENU-SCREEN (line 182-187) | MAP('COADM1A'), MAPSET('COADM01'), FROM(COADM1AO), ERASE | —                                 |
| 6 | `RECEIVE MAP`                    | RECEIVE-MENU-SCREEN (line 194-200) | MAP('COADM1A'), MAPSET('COADM01'), INTO(COADM1AI), RESP(WS-RESP-CD), RESP2(WS-REAS-CD) | RESP/RESP2 captured |
| 7 | `RETURN TRANSID COMMAREA`        | PGMIDERR-ERR-PARA (line 280-283) | TRANSID('CA00'), COMMAREA(CARDDEMO-COMMAREA)            | —                                 |

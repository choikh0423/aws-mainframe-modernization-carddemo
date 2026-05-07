# CICS Transaction Flow Analysis: CM00 (COMEN01C)

## Main Menu for Regular Users

**Generated:** 2026-05-07
**Repository:** choikh0423/aws-mainframe-modernization-carddemo
**Branch:** demos/cobol-full-docs

---

## Section 1: Flow Overview

| Attribute           | Value                                      |
|---------------------|--------------------------------------------|
| **Transaction ID**  | CM00                                       |
| **Entry Program**   | COMEN01C                                   |
| **Function**        | Main Menu for Regular Users                |
| **Transaction Type**| Pseudo-conversational (RETURN TRANSID)     |
| **Data Access**     | None (menu-only; no direct VSAM I/O)       |
| **CSD Group**       | CARDDEMO                                   |

### Complete Navigation Path

```
  +-------------------+
  | CC00 / COSGN00C   |   Sign-on Screen
  | (Signon)          |
  +--------+----------+
           |
           | XCTL (if user type = 'U')
           | COMMAREA: user-id, user-type, from-tranid, from-program
           v
  +-------------------+
  | CM00 / COMEN01C   |   Main Menu (Regular Users)   <-- THIS PROGRAM
  | (Main Menu)       |
  +--------+----------+
           |
           | XCTL (based on menu option selected)
           | COMMAREA: from-tranid, from-program, pgm-context=0
           v
  +-------------------------------------------+
  | Downstream Programs (by menu option):     |
  |  1. COACTVWC  - Account View              |
  |  2. COACTUPC  - Account Update            |
  |  3. COCRDLIC  - Credit Card List          |
  |  4. COCRDSLC  - Credit Card View          |
  |  5. COCRDUPC  - Credit Card Update        |
  |  6. COTRN00C  - Transaction List          |
  |  7. COTRN01C  - Transaction View          |
  |  8. COTRN02C  - Transaction Add           |
  |  9. CORPT00C  - Transaction Reports       |
  | 10. COBIL00C  - Bill Payment              |
  | 11. COPAUS0C  - Pending Auth View (*)     |
  +-------------------------------------------+
           |
           | PF3 => XCTL back to COSGN00C
           v
  +-------------------+
  | CC00 / COSGN00C   |
  | (Signon)          |
  +-------------------+

  (*) COPAUS0C is checked via EXEC CICS INQUIRE PROGRAM before XCTL.
      It does NOT exist in the repository (.cbl not present).
```

### Pseudo-Conversational Lifecycle

```
  Terminal                     CICS Region                   Program COMEN01C
  --------                     -----------                   ----------------
     |                              |                              |
     |  (1) Transaction CM00        |                              |
     |  initiated by COSGN00C XCTL  |                              |
     |----------------------------->|  Attach task                 |
     |                              |----------------------------->|
     |                              |  EIBCALEN > 0                |
     |                              |  CDEMO-PGM-ENTER (context=0) |
     |                              |  SET CDEMO-PGM-REENTER       |
     |                              |  SEND MAP COMEN1A            |
     |  <--- Display Menu ---------|<-----------------------------|
     |                              |  RETURN TRANSID('CM00')      |
     |                              |  COMMAREA(CARDDEMO-COMMAREA) |
     |                              |  --- Task ends ---           |
     |                              |                              |
     |  (2) User presses ENTER      |                              |
     |  with option number           |                              |
     |----------------------------->|  New task (CM00)             |
     |                              |----------------------------->|
     |                              |  EIBCALEN > 0                |
     |                              |  CDEMO-PGM-REENTER (ctx=1)   |
     |                              |  RECEIVE MAP COMEN1A         |
     |                              |  EVALUATE EIBAID             |
     |                              |    DFHENTER => PROCESS-ENTER |
     |                              |    Validate option           |
     |                              |    XCTL to target program    |
     |                              |  --- Control transferred --- |
     |                              |                              |
     |  (Alt) User presses PF3      |                              |
     |----------------------------->|  New task (CM00)             |
     |                              |----------------------------->|
     |                              |  XCTL to COSGN00C            |
     |                              |  --- Return to signon ---    |
```

**Lifecycle States:**

| State | PGM-CONTEXT | Trigger | Action |
|-------|-------------|---------|--------|
| First entry | 0 (CDEMO-PGM-ENTER) | XCTL from COSGN00C | Set PGM-REENTER, SEND MAP, RETURN TRANSID |
| Re-entry | 1 (CDEMO-PGM-REENTER) | User input (ENTER/PF3/other) | RECEIVE MAP, process input, XCTL or re-display |

---

## Section 2: Programs Involved

| # | Program    | Type           | Function                          | Caller     | Call Method | Source Path          |
|---|------------|----------------|-----------------------------------|------------|-------------|----------------------|
| 1 | COSGN00C   | CICS Online    | Sign-on Screen                    | (entry)    | Transaction CC00 | `app/cbl/COSGN00C.cbl` |
| 2 | **COMEN01C** | **CICS Online** | **Main Menu (Regular Users)**    | COSGN00C   | XCTL        | `app/cbl/COMEN01C.cbl` |
| 3 | COACTVWC   | CICS Online    | Account View                      | COMEN01C   | XCTL (opt 1) | `app/cbl/COACTVWC.cbl` |
| 4 | COACTUPC   | CICS Online    | Account Update                    | COMEN01C   | XCTL (opt 2) | `app/cbl/COACTUPC.cbl` |
| 5 | COCRDLIC   | CICS Online    | Credit Card List                  | COMEN01C   | XCTL (opt 3) | `app/cbl/COCRDLIC.cbl` |
| 6 | COCRDSLC   | CICS Online    | Credit Card View                  | COMEN01C   | XCTL (opt 4) | `app/cbl/COCRDSLC.cbl` |
| 7 | COCRDUPC   | CICS Online    | Credit Card Update                | COMEN01C   | XCTL (opt 5) | `app/cbl/COCRDUPC.cbl` |
| 8 | COTRN00C   | CICS Online    | Transaction List                  | COMEN01C   | XCTL (opt 6) | `app/cbl/COTRN00C.cbl` |
| 9 | COTRN01C   | CICS Online    | Transaction View                  | COMEN01C   | XCTL (opt 7) | `app/cbl/COTRN01C.cbl` |
| 10| COTRN02C   | CICS Online    | Transaction Add                   | COMEN01C   | XCTL (opt 8) | `app/cbl/COTRN02C.cbl` |
| 11| CORPT00C   | CICS Online    | Transaction Reports               | COMEN01C   | XCTL (opt 9) | `app/cbl/CORPT00C.cbl` |
| 12| COBIL00C   | CICS Online    | Bill Payment                      | COMEN01C   | XCTL (opt 10)| `app/cbl/COBIL00C.cbl` |
| 13| COPAUS0C   | CICS Online    | Pending Authorization View        | COMEN01C   | XCTL (opt 11)| **Not in repo** (checked via INQUIRE) |

**Notes:**
- All downstream transfers from COMEN01C use **XCTL** (permanent control transfer), not LINK
- COPAUS0C (option 11) is special-cased: the program issues `EXEC CICS INQUIRE PROGRAM` before attempting XCTL, and displays an error message if the program is not installed
- There are no CALL statements in COMEN01C
- There are no LINK statements in COMEN01C

### Programs That Return to COMEN01C

The following downstream programs set `CDEMO-TO-PROGRAM = 'COMEN01C'` and XCTL back:

| Program  | Context |
|----------|---------|
| COACTVWC | PF3 from Account View |
| COACTUPC | PF3 from Account Update |
| COCRDLIC | PF3 from Credit Card List |
| COCRDSLC | PF3 from Credit Card View |
| COCRDUPC | PF3 from Credit Card Update |
| COTRN00C | PF3 from Transaction List |
| COTRN01C | PF3 from Transaction View |
| COTRN02C | PF3 from Transaction Add |
| CORPT00C | PF3 from Transaction Reports |
| COBIL00C | PF3 from Bill Payment |

---

## Section 3: Copybooks

| # | Copybook   | Used By    | Classification        | Purpose |
|---|------------|------------|-----------------------|---------|
| 1 | COCOM01Y   | COMEN01C, COSGN00C, all downstream | COMMAREA definition | CardDemo inter-program communication area |
| 2 | COMEN02Y   | COMEN01C   | Menu data structure   | Main menu option table (11 options with program names, labels, user-type flags) |
| 3 | COMEN01    | COMEN01C   | BMS symbolic map (auto-generated) | Symbolic map for COMEN1A screen (input/output structures COMEN1AI/COMEN1AO) |
| 4 | COTTL01Y   | COMEN01C, COSGN00C | Screen title data | Application title lines and thank-you message |
| 5 | CSDAT01Y   | COMEN01C, COSGN00C | Date/time structure | Current date/time working storage fields |
| 6 | CSMSG01Y   | COMEN01C, COSGN00C | Common messages | Shared message constants (thank-you, invalid-key) |
| 7 | CSUSR01Y   | COMEN01C, COSGN00C | User security record | USRSEC VSAM file record layout (used by COSGN00C; included but not directly used by COMEN01C) |
| 8 | DFHAID     | COMEN01C, COSGN00C | CICS system copybook | AID key constants (DFHENTER, DFHPF3, etc.) |
| 9 | DFHBMSCA   | COMEN01C, COSGN00C | CICS system copybook | BMS attribute constants (DFHRED, DFHGREEN, etc.) |

### Copybook Record Layouts

#### COCOM01Y — CARDDEMO-COMMAREA (Full layout in Section 6)

```
01 CARDDEMO-COMMAREA
   05 CDEMO-GENERAL-INFO
      10 CDEMO-FROM-TRANID        PIC X(04)
      10 CDEMO-FROM-PROGRAM       PIC X(08)
      10 CDEMO-TO-TRANID          PIC X(04)
      10 CDEMO-TO-PROGRAM         PIC X(08)
      10 CDEMO-USER-ID            PIC X(08)
      10 CDEMO-USER-TYPE          PIC X(01)
         88 CDEMO-USRTYP-ADMIN    VALUE 'A'
         88 CDEMO-USRTYP-USER     VALUE 'U'
      10 CDEMO-PGM-CONTEXT        PIC 9(01)
         88 CDEMO-PGM-ENTER       VALUE 0
         88 CDEMO-PGM-REENTER     VALUE 1
   05 CDEMO-CUSTOMER-INFO
      10 CDEMO-CUST-ID            PIC 9(09)
      10 CDEMO-CUST-FNAME         PIC X(25)
      10 CDEMO-CUST-MNAME         PIC X(25)
      10 CDEMO-CUST-LNAME         PIC X(25)
   05 CDEMO-ACCOUNT-INFO
      10 CDEMO-ACCT-ID            PIC 9(11)
      10 CDEMO-ACCT-STATUS        PIC X(01)
   05 CDEMO-CARD-INFO
      10 CDEMO-CARD-NUM           PIC 9(16)
   05 CDEMO-MORE-INFO
      10 CDEMO-LAST-MAP           PIC X(7)
      10 CDEMO-LAST-MAPSET        PIC X(7)
```

#### COMEN02Y — CARDDEMO-MAIN-MENU-OPTIONS

```
01 CARDDEMO-MAIN-MENU-OPTIONS
   05 CDEMO-MENU-OPT-COUNT       PIC 9(02) VALUE 11
   05 CDEMO-MENU-OPTIONS-DATA     (552 bytes total: 11 x 46-byte entries + 1 spare)
      10 entries, each:
         PIC 9(02)  — option number
         PIC X(35)  — option display name
         PIC X(08)  — target program name
         PIC X(01)  — user type flag ('U'=user, 'A'=admin)
   05 CDEMO-MENU-OPTIONS REDEFINES CDEMO-MENU-OPTIONS-DATA
      10 CDEMO-MENU-OPT OCCURS 12 TIMES
         15 CDEMO-MENU-OPT-NUM      PIC 9(02)
         15 CDEMO-MENU-OPT-NAME     PIC X(35)
         15 CDEMO-MENU-OPT-PGMNAME  PIC X(08)
         15 CDEMO-MENU-OPT-USRTYPE  PIC X(01)

   Menu Option Table Contents:
   +-----+-----------------------------------+----------+------+
   | Opt | Name                              | Program  | Type |
   +-----+-----------------------------------+----------+------+
   |  01 | Account View                      | COACTVWC | U    |
   |  02 | Account Update                    | COACTUPC | U    |
   |  03 | Credit Card List                  | COCRDLIC | U    |
   |  04 | Credit Card View                  | COCRDSLC | U    |
   |  05 | Credit Card Update                | COCRDUPC | U    |
   |  06 | Transaction List                  | COTRN00C | U    |
   |  07 | Transaction View                  | COTRN01C | U    |
   |  08 | Transaction Add                   | COTRN02C | U    |
   |  09 | Transaction Reports               | CORPT00C | U    |
   |  10 | Bill Payment                      | COBIL00C | U    |
   |  11 | Pending Authorization View        | COPAUS0C | U    |
   +-----+-----------------------------------+----------+------+
```

#### COTTL01Y — Screen Title Constants

```
01 CCDA-SCREEN-TITLE
   05 CCDA-TITLE01    PIC X(40) VALUE '      AWS Mainframe Modernization       '
   05 CCDA-TITLE02    PIC X(40) VALUE '              CardDemo                  '
   05 CCDA-THANK-YOU  PIC X(40) VALUE 'Thank you for using CCDA application... '
```

#### CSDAT01Y — Date/Time Working Storage

```
01 WS-DATE-TIME
   05 WS-CURDATE-DATA
      10 WS-CURDATE
         15 WS-CURDATE-YEAR       PIC 9(04)
         15 WS-CURDATE-MONTH      PIC 9(02)
         15 WS-CURDATE-DAY        PIC 9(02)
      10 WS-CURDATE-N REDEFINES WS-CURDATE  PIC 9(08)
      10 WS-CURTIME
         15 WS-CURTIME-HOURS      PIC 9(02)
         15 WS-CURTIME-MINUTE     PIC 9(02)
         15 WS-CURTIME-SECOND     PIC 9(02)
         15 WS-CURTIME-MILSEC     PIC 9(02)
      10 WS-CURTIME-N REDEFINES WS-CURTIME  PIC 9(08)
   05 WS-CURDATE-MM-DD-YY
      10 WS-CURDATE-MM            PIC 9(02)
      10 FILLER                   PIC X(01) VALUE '/'
      10 WS-CURDATE-DD            PIC 9(02)
      10 FILLER                   PIC X(01) VALUE '/'
      10 WS-CURDATE-YY            PIC 9(02)
   05 WS-CURTIME-HH-MM-SS
      10 WS-CURTIME-HH            PIC 9(02)
      10 FILLER                   PIC X(01) VALUE ':'
      10 WS-CURTIME-MM            PIC 9(02)
      10 FILLER                   PIC X(01) VALUE ':'
      10 WS-CURTIME-SS            PIC 9(02)
   05 WS-TIMESTAMP                (26-byte ISO timestamp)
```

#### CSMSG01Y — Common Messages

```
01 CCDA-COMMON-MESSAGES
   05 CCDA-MSG-THANK-YOU    PIC X(50) VALUE 'Thank you for using CardDemo application...'
   05 CCDA-MSG-INVALID-KEY  PIC X(50) VALUE 'Invalid key pressed. Please see below...'
```

#### CSUSR01Y — SEC-USER-DATA (USRSEC VSAM Record)

```
01 SEC-USER-DATA
   05 SEC-USR-ID              PIC X(08)   — User ID (key)
   05 SEC-USR-FNAME           PIC X(20)   — First name
   05 SEC-USR-LNAME           PIC X(20)   — Last name
   05 SEC-USR-PWD             PIC X(08)   — Password
   05 SEC-USR-TYPE            PIC X(01)   — User type ('A'=Admin, 'U'=User)
   05 SEC-USR-FILLER          PIC X(23)   — Reserved
   Total record length: 80 bytes
```

---

## Section 4: VSAM File Operations

### Direct File I/O in COMEN01C

**COMEN01C performs NO direct VSAM file I/O.** It is a pure navigation/menu program.

The variable `WS-USRSEC-FILE PIC X(08) VALUE 'USRSEC  '` is declared in WORKING-STORAGE but is **not used** in any EXEC CICS READ/WRITE/DELETE statement within COMEN01C. It appears to be inherited from copy/paste of the COSGN00C template.

### Upstream File I/O (COSGN00C — sets up COMMAREA before XCTL to COMEN01C)

| File     | DD Name  | Operation | Key Field  | Record Copybook | Purpose |
|----------|----------|-----------|------------|-----------------|---------|
| USRSEC   | USRSEC   | READ      | WS-USER-ID (PIC X(08)) | CSUSR01Y (SEC-USER-DATA) | Authenticate user credentials; set CDEMO-USER-TYPE |

```
COSGN00C File Access Flow:
  
  User enters ID + Password
        |
        v
  EXEC CICS READ DATASET('USRSEC')
    INTO(SEC-USER-DATA)
    RIDFLD(WS-USER-ID)
        |
        +-- RESP=0 (found) --> Compare SEC-USR-PWD with WS-USER-PWD
        |     +-- Match --> Set COMMAREA fields, XCTL to COMEN01C (user) or COADM01C (admin)
        |     +-- No match --> "Wrong Password" error
        +-- RESP=13 (not found) --> "User not found" error
        +-- RESP=other --> "Unable to verify" error
```

### VSAM Files Referenced Across the Full Flow

| # | DD Name   | Description                  | Key Field      | Used By (from menu) |
|---|-----------|------------------------------|----------------|----------------------|
| 1 | USRSEC    | User Security                | User ID (X(08))| COSGN00C             |
| 2 | ACCTDAT   | Account Master               | Account ID     | COACTVWC, COACTUPC   |
| 3 | CARDDAT   | Card Master                  | Card Number    | COCRDLIC, COCRDSLC, COCRDUPC |
| 4 | CUSTDAT   | Customer Master              | Customer ID    | COCRDSLC, COCRDUPC   |
| 5 | TRANSACT  | Transaction File             | Trans ID       | COTRN00C, COTRN01C, COTRN02C |
| 6 | CCXREF    | Card-Customer Cross-Reference| Card Number    | COCRDLIC, COCRDSLC   |
| 7 | CXACAIX   | Card-Account AIX             | Account ID     | COCRDLIC             |

---

## Section 5: BMS Screen Map

### Map Details

| Attribute   | Value    |
|-------------|----------|
| Mapset      | COMEN01  |
| Map         | COMEN1A  |
| Size        | 24 x 80  |
| Source      | `app/bms/COMEN01.bms` |
| CTRL        | ALARM, FREEKB |
| LANG        | COBOL    |
| MODE        | INOUT    |
| STORAGE     | AUTO     |

### ASCII Screen Layout

```
Row Col  Field
--- ---  -----
  1   1  Tran: CM00         AWS Mainframe Modernization              Date: mm/dd/yy
  2   1  Prog: COMEN01C              CardDemo                        Time: hh:mm:ss
  3
  4  35                      Main Menu
  5
  6  20  01. Account View
  7  20  02. Account Update
  8  20  03. Credit Card List
  9  20  04. Credit Card View
 10  20  05. Credit Card Update
 11  20  06. Transaction List
 12  20  07. Transaction View
 13  20  08. Transaction Add
 14  20  09. Transaction Reports
 15  20  10. Bill Payment
 16  20  11. Pending Authorization View
 17  20  (slot 12 - unused)
 18
 19
 20  15  Please select an option : [__]
 21
 22
 23   1  <error message area - 78 chars, RED>
 24   1  ENTER=Continue  F3=Exit
```

### Field Inventory

| # | Field Name | Row | Col | Length | Attributes         | Color     | Purpose |
|---|------------|-----|-----|--------|--------------------|-----------|---------|
| 1 | (literal)  |  1  |  1  |   5    | ASKIP,NORM         | BLUE      | "Tran:" label |
| 2 | TRNNAME    |  1  |  7  |   4    | ASKIP,FSET,NORM    | BLUE      | Transaction ID (CM00) |
| 3 | TITLE01    |  1  | 21  |  40    | ASKIP,FSET,NORM    | YELLOW    | Title line 1 |
| 4 | (literal)  |  1  | 65  |   5    | ASKIP,NORM         | BLUE      | "Date:" label |
| 5 | CURDATE    |  1  | 71  |   8    | ASKIP,FSET,NORM    | BLUE      | Current date (mm/dd/yy) |
| 6 | (literal)  |  2  |  1  |   5    | ASKIP,NORM         | BLUE      | "Prog:" label |
| 7 | PGMNAME    |  2  |  7  |   8    | ASKIP,FSET,NORM    | BLUE      | Program name (COMEN01C) |
| 8 | TITLE02    |  2  | 21  |  40    | ASKIP,FSET,NORM    | YELLOW    | Title line 2 |
| 9 | (literal)  |  2  | 65  |   5    | ASKIP,NORM         | BLUE      | "Time:" label |
| 10| CURTIME    |  2  | 71  |   8    | ASKIP,FSET,NORM    | BLUE      | Current time (hh:mm:ss) |
| 11| (literal)  |  4  | 35  |   9    | ASKIP,BRT          | NEUTRAL   | "Main Menu" heading |
| 12| OPTN001    |  6  | 20  |  40    | ASKIP,FSET,NORM    | BLUE      | Menu option 1 text |
| 13| OPTN002    |  7  | 20  |  40    | ASKIP,FSET,NORM    | BLUE      | Menu option 2 text |
| 14| OPTN003    |  8  | 20  |  40    | ASKIP,FSET,NORM    | BLUE      | Menu option 3 text |
| 15| OPTN004    |  9  | 20  |  40    | ASKIP,FSET,NORM    | BLUE      | Menu option 4 text |
| 16| OPTN005    | 10  | 20  |  40    | ASKIP,FSET,NORM    | BLUE      | Menu option 5 text |
| 17| OPTN006    | 11  | 20  |  40    | ASKIP,FSET,NORM    | BLUE      | Menu option 6 text |
| 18| OPTN007    | 12  | 20  |  40    | ASKIP,FSET,NORM    | BLUE      | Menu option 7 text |
| 19| OPTN008    | 13  | 20  |  40    | ASKIP,FSET,NORM    | BLUE      | Menu option 8 text |
| 20| OPTN009    | 14  | 20  |  40    | ASKIP,FSET,NORM    | BLUE      | Menu option 9 text |
| 21| OPTN010    | 15  | 20  |  40    | ASKIP,FSET,NORM    | BLUE      | Menu option 10 text |
| 22| OPTN011    | 16  | 20  |  40    | ASKIP,FSET,NORM    | BLUE      | Menu option 11 text |
| 23| OPTN012    | 17  | 20  |  40    | ASKIP,FSET,NORM    | BLUE      | Menu option 12 text (unused) |
| 24| (literal)  | 20  | 15  |  25    | ASKIP,BRT          | TURQUOISE | "Please select an option :" prompt |
| 25| **OPTION** | 20  | 41  |   2    | **UNPROT**,FSET,IC,NUM | UNDERLINE | **User input: option number** |
| 26| (stopper)  | 20  | 44  |   0    | ASKIP,NORM         | GREEN     | Field delimiter after OPTION |
| 27| ERRMSG     | 23  |  1  |  78    | ASKIP,BRT,FSET     | RED       | Error/status message |
| 28| (literal)  | 24  |  1  |  23    | ASKIP,NORM         | YELLOW    | "ENTER=Continue  F3=Exit" |

**Input Fields:** 1 (OPTION — the menu selection number)
**Output Fields:** 27 (all others are display-only ASKIP fields)

### Function Key Assignments

| Key    | EIBAID Constant | Action |
|--------|-----------------|--------|
| ENTER  | DFHENTER        | Process selected menu option (PROCESS-ENTER-KEY) |
| PF3    | DFHPF3          | Return to sign-on screen (XCTL to COSGN00C) |
| Other  | (any other)     | Display "Invalid key pressed" error message |

---

## Section 6: COMMAREA Structure

### CARDDEMO-COMMAREA Field Documentation

```
01 CARDDEMO-COMMAREA                          Total: ~200 bytes
   |
   +-- 05 CDEMO-GENERAL-INFO                  (34 bytes)
   |   +-- 10 CDEMO-FROM-TRANID    PIC X(04)  Navigation: source transaction
   |   +-- 10 CDEMO-FROM-PROGRAM   PIC X(08)  Navigation: source program
   |   +-- 10 CDEMO-TO-TRANID      PIC X(04)  Navigation: target transaction
   |   +-- 10 CDEMO-TO-PROGRAM     PIC X(08)  Navigation: target program
   |   +-- 10 CDEMO-USER-ID        PIC X(08)  Authenticated user ID
   |   +-- 10 CDEMO-USER-TYPE      PIC X(01)  'A'=Admin, 'U'=User
   |   |   +-- 88 CDEMO-USRTYP-ADMIN  VALUE 'A'
   |   |   +-- 88 CDEMO-USRTYP-USER   VALUE 'U'
   |   +-- 10 CDEMO-PGM-CONTEXT    PIC 9(01)  Pseudo-conv state flag
   |       +-- 88 CDEMO-PGM-ENTER     VALUE 0  (first entry)
   |       +-- 88 CDEMO-PGM-REENTER   VALUE 1  (re-entry)
   |
   +-- 05 CDEMO-CUSTOMER-INFO                 (84 bytes)
   |   +-- 10 CDEMO-CUST-ID        PIC 9(09)
   |   +-- 10 CDEMO-CUST-FNAME     PIC X(25)
   |   +-- 10 CDEMO-CUST-MNAME     PIC X(25)
   |   +-- 10 CDEMO-CUST-LNAME     PIC X(25)
   |
   +-- 05 CDEMO-ACCOUNT-INFO                  (12 bytes)
   |   +-- 10 CDEMO-ACCT-ID        PIC 9(11)
   |   +-- 10 CDEMO-ACCT-STATUS    PIC X(01)
   |
   +-- 05 CDEMO-CARD-INFO                     (16 bytes)
   |   +-- 10 CDEMO-CARD-NUM       PIC 9(16)
   |
   +-- 05 CDEMO-MORE-INFO                     (14 bytes)
       +-- 10 CDEMO-LAST-MAP       PIC X(7)
       +-- 10 CDEMO-LAST-MAPSET    PIC X(7)
```

### COMMAREA Field Read/Write by Program

| Field               | COSGN00C (upstream) | COMEN01C (this program) | Downstream Programs |
|---------------------|---------------------|-------------------------|---------------------|
| CDEMO-FROM-TRANID   | **WRITE** (CC00)    | **WRITE** (CM00)        | READ                |
| CDEMO-FROM-PROGRAM  | **WRITE** (COSGN00C)| **WRITE** (COMEN01C)    | READ                |
| CDEMO-TO-TRANID     | —                   | —                       | WRITE (return nav)  |
| CDEMO-TO-PROGRAM    | —                   | READ (PF3 path)         | **WRITE** (COMEN01C)|
| CDEMO-USER-ID       | **WRITE**           | READ (implicit via COMMAREA pass) | READ     |
| CDEMO-USER-TYPE     | **WRITE**           | READ (admin check)      | READ                |
| CDEMO-PGM-CONTEXT   | **WRITE** (0)       | READ / **WRITE** (0→1)  | READ / WRITE        |
| CDEMO-CUST-*        | —                   | —                       | WRITE (lookups)     |
| CDEMO-ACCT-*        | —                   | —                       | WRITE (lookups)     |
| CDEMO-CARD-NUM      | —                   | —                       | WRITE (lookups)     |
| CDEMO-LAST-MAP      | —                   | —                       | WRITE               |
| CDEMO-LAST-MAPSET   | —                   | —                       | WRITE               |

### COMMAREA Flow Between Programs

```
  COSGN00C                          COMEN01C                        Downstream
  --------                          --------                        ----------
  Sets:                             Reads:                          Reads:
   CDEMO-FROM-TRANID = 'CC00'       CDEMO-USER-TYPE                 CDEMO-FROM-TRANID
   CDEMO-FROM-PROGRAM = 'COSGN00C'  CDEMO-PGM-CONTEXT               CDEMO-FROM-PROGRAM
   CDEMO-USER-ID = <user>           CDEMO-TO-PROGRAM (PF3)          CDEMO-USER-ID
   CDEMO-USER-TYPE = <type>                                         CDEMO-USER-TYPE
   CDEMO-PGM-CONTEXT = 0          Sets:                             CDEMO-PGM-CONTEXT
                                    CDEMO-FROM-TRANID = 'CM00'
        --- XCTL --->               CDEMO-FROM-PROGRAM = 'COMEN01C'
                                    CDEMO-PGM-CONTEXT = 0 (on XCTL)
                                    CDEMO-PGM-CONTEXT = 1 (on RETURN)
                                         --- XCTL --->
```

---

## Section 7: Business Logic Flow

### Step-by-Step Execution Narrative

#### 1. Entry Point (MAIN-PARA)

1. Initialize error flag to OFF; clear message areas
2. Check `EIBCALEN`:
   - **If EIBCALEN = 0** (no COMMAREA — direct invocation, not from another program):
     - Set `CDEMO-FROM-PROGRAM = 'COSGN00C'`
     - XCTL to COSGN00C (redirect to sign-on)
   - **If EIBCALEN > 0** (COMMAREA present — normal flow):
     - Copy DFHCOMMAREA into CARDDEMO-COMMAREA
     - Check `CDEMO-PGM-CONTEXT`:

#### 2. First Entry (PGM-CONTEXT = 0, CDEMO-PGM-ENTER)

1. Set `CDEMO-PGM-REENTER` (context = 1) for next iteration
2. Initialize output map COMEN1AO to LOW-VALUES
3. SEND MAP (display menu screen)
4. RETURN TRANSID('CM00') with COMMAREA — task ends, awaiting user input

#### 3. Re-Entry (PGM-CONTEXT = 1, CDEMO-PGM-REENTER)

1. RECEIVE MAP to get user input
2. Evaluate EIBAID:

##### 3a. ENTER Key Pressed (PROCESS-ENTER-KEY)

1. **Extract option number:** Scan OPTIONI from right to left, trimming trailing spaces
2. **Format option:** Replace spaces with '0', move to WS-OPTION (numeric)
3. **Validate option number:**
   - If not numeric, > CDEMO-MENU-OPT-COUNT (11), or = 0:
     - Set error flag, display "Please enter a valid option number..."
4. **Check user authorization:**
   - If user type is 'U' (regular user) AND the selected option's USRTYPE = 'A' (admin-only):
     - Set error flag, display "No access - Admin Only option..."
   - (Note: In the current COMEN02Y configuration, all 11 options have USRTYPE='U', so this check never triggers for regular users)
5. **Process valid option:**
   - **Special case — COPAUS0C (option 11):**
     - Issue `EXEC CICS INQUIRE PROGRAM(COPAUS0C) NOHANDLE`
     - If EIBRESP = NORMAL (program installed):
       - Set navigation fields (FROM-TRANID, FROM-PROGRAM, PGM-CONTEXT=0)
       - XCTL to COPAUS0C with COMMAREA
     - If EIBRESP != NORMAL (program not installed):
       - Set ERRMSG color to RED (DFHRED)
       - Display "This option Pending Authorization View is not installed..."
   - **Special case — program name starts with 'DUMMY':**
     - Set ERRMSG color to GREEN (DFHGREEN)
     - Display "This option [name] is coming soon..."
   - **Default (all other valid options):**
     - Set `CDEMO-FROM-TRANID = 'CM00'`
     - Set `CDEMO-FROM-PROGRAM = 'COMEN01C'`
     - Set `CDEMO-PGM-CONTEXT = 0`
     - XCTL to `CDEMO-MENU-OPT-PGMNAME(WS-OPTION)` with COMMAREA

##### 3b. PF3 Key Pressed

1. Set `CDEMO-TO-PROGRAM = 'COSGN00C'` (if not already set)
2. XCTL to COSGN00C — return to sign-on screen

##### 3c. Any Other Key

1. Set error flag
2. Display `CCDA-MSG-INVALID-KEY` ("Invalid key pressed. Please see below...")
3. Re-send menu screen

#### 4. Screen Display (SEND-MENU-SCREEN)

1. **POPULATE-HEADER-INFO:**
   - Get current date/time via `FUNCTION CURRENT-DATE`
   - Set title lines (CCDA-TITLE01, CCDA-TITLE02)
   - Set transaction ID (CM00) and program name (COMEN01C)
   - Format date as MM/DD/YY and time as HH:MM:SS
2. **BUILD-MENU-OPTIONS:**
   - Loop from 1 to CDEMO-MENU-OPT-COUNT (11)
   - For each option: format string as "{num}. {name}" and move to corresponding OPTN00xO field
3. Move WS-MESSAGE to ERRMSGO
4. SEND MAP('COMEN1A') MAPSET('COMEN01') with ERASE

#### 5. Pseudo-Conversational Return

After all processing paths (except XCTL transfers), the program reaches:
```cobol
EXEC CICS RETURN
    TRANSID (WS-TRANID)          -- 'CM00'
    COMMAREA (CARDDEMO-COMMAREA)
END-EXEC
```
This suspends the transaction, preserving the COMMAREA for the next user interaction.

### Error Handling Summary

| Condition | Error Message | Action |
|-----------|---------------|--------|
| EIBCALEN = 0 (no COMMAREA) | — | XCTL to COSGN00C |
| Invalid option (non-numeric, 0, >11) | "Please enter a valid option number..." | Re-display menu |
| Admin-only option for regular user | "No access - Admin Only option..." | Re-display menu |
| Program not installed (COPAUS0C) | "This option ... is not installed..." | Re-display menu (RED) |
| DUMMY program | "This option ... is coming soon..." | Re-display menu (GREEN) |
| Invalid AID key | "Invalid key pressed. Please see below..." | Re-display menu |

---

## Section 8: Complete File Inventory

### COBOL Source Files

| # | File | Path | Role in Flow |
|---|------|------|-------------|
| 1 | COSGN00C.cbl | `app/cbl/COSGN00C.cbl` | Upstream: sign-on screen |
| 2 | COMEN01C.cbl | `app/cbl/COMEN01C.cbl` | **Target program: main menu** |
| 3 | COACTVWC.cbl | `app/cbl/COACTVWC.cbl` | Downstream: Account View |
| 4 | COACTUPC.cbl | `app/cbl/COACTUPC.cbl` | Downstream: Account Update |
| 5 | COCRDLIC.cbl | `app/cbl/COCRDLIC.cbl` | Downstream: Credit Card List |
| 6 | COCRDSLC.cbl | `app/cbl/COCRDSLC.cbl` | Downstream: Credit Card View |
| 7 | COCRDUPC.cbl | `app/cbl/COCRDUPC.cbl` | Downstream: Credit Card Update |
| 8 | COTRN00C.cbl | `app/cbl/COTRN00C.cbl` | Downstream: Transaction List |
| 9 | COTRN01C.cbl | `app/cbl/COTRN01C.cbl` | Downstream: Transaction View |
| 10| COTRN02C.cbl | `app/cbl/COTRN02C.cbl` | Downstream: Transaction Add |
| 11| CORPT00C.cbl | `app/cbl/CORPT00C.cbl` | Downstream: Transaction Reports |
| 12| COBIL00C.cbl | `app/cbl/COBIL00C.cbl` | Downstream: Bill Payment |

**Not in repo:** COPAUS0C (Pending Authorization View) — referenced but verified absent via EXEC CICS INQUIRE

### Copybook Files

| # | File | Path | Classification |
|---|------|------|---------------|
| 1 | COCOM01Y.cpy | `app/cpy/COCOM01Y.cpy` | COMMAREA definition |
| 2 | COMEN02Y.cpy | `app/cpy/COMEN02Y.cpy` | Menu option data table |
| 3 | COTTL01Y.cpy | `app/cpy/COTTL01Y.cpy` | Screen title constants |
| 4 | CSDAT01Y.cpy | `app/cpy/CSDAT01Y.cpy` | Date/time structure |
| 5 | CSMSG01Y.cpy | `app/cpy/CSMSG01Y.cpy` | Common messages |
| 6 | CSUSR01Y.cpy | `app/cpy/CSUSR01Y.cpy` | User security record |

**BMS Symbolic Map (auto-generated, not in app/cpy/):**
| # | Copybook | Source BMS | Notes |
|---|----------|-----------|-------|
| 7 | COMEN01  | `app/bms/COMEN01.bms` | Generated at compile time; defines COMEN1AI/COMEN1AO |

**CICS System Copybooks (not in repo — IBM-supplied):**
| # | Copybook | Purpose |
|---|----------|---------|
| 8 | DFHAID   | AID key constants (DFHENTER, DFHPF3, etc.) |
| 9 | DFHBMSCA | BMS attribute constants (DFHRED, DFHGREEN, etc.) |

### BMS Map Source Files

| # | File | Path | Mapset | Map |
|---|------|------|--------|-----|
| 1 | COMEN01.bms | `app/bms/COMEN01.bms` | COMEN01 | COMEN1A |

### CSD Definitions

| Resource | Definition | Source |
|----------|-----------|--------|
| Transaction CM00 | `DEFINE TRANSACTION(CM00) GROUP(CARDDEMO) PROGRAM(COMEN01C)` | `app/csd/CARDDEMO.CSD` |
| Program COMEN01C | `DEFINE PROGRAM(COMEN01C) GROUP(CARDDEMO) LANGUAGE(COBOL)` | `app/csd/CARDDEMO.CSD` |
| Mapset COMEN01 | `DEFINE MAPSET(COMEN01) GROUP(CARDDEMO)` | `app/csd/CARDDEMO.CSD` |

---

## Section 9: Transaction Record Layout

COMEN01C **does not read or write any VSAM records**. It is a pure menu navigation program.

The only record layout relevant to this flow is the **USRSEC** record read by the upstream program COSGN00C (documented in Section 3 under CSUSR01Y).

For reference, the USRSEC record layout:

```
Offset  Length  Field           PIC         Description
------  ------  -----           ---         -----------
  0       8     SEC-USR-ID      X(08)       User ID (record key)
  8      20     SEC-USR-FNAME   X(20)       First name
 28      20     SEC-USR-LNAME   X(20)       Last name
 48       8     SEC-USR-PWD     X(08)       Password
 56       1     SEC-USR-TYPE    X(01)       User type (A=Admin, U=User)
 57      23     SEC-USR-FILLER  X(23)       Reserved/filler
------  ------
Total:   80 bytes
```

---

## Section 10: Observations

### Strengths

1. **Clean separation of concerns:** COMEN01C is a pure navigation hub with no data access logic. All business logic resides in the downstream programs, making it straightforward to replace with a modern menu/routing component.

2. **Table-driven menu:** The menu options are defined in a copybook (COMEN02Y) as a data table, not hard-coded in the PROCEDURE DIVISION. This is a good practice that simplifies adding/removing menu items.

3. **Consistent COMMAREA protocol:** All programs in the flow use the same COMMAREA structure (COCOM01Y) with standardized navigation fields (FROM-TRANID, FROM-PROGRAM, TO-PROGRAM, PGM-CONTEXT). This is a well-designed inter-program communication pattern.

4. **Defensive programming:** The INQUIRE PROGRAM check before XCTL to COPAUS0C gracefully handles missing programs. The EIBCALEN = 0 check redirects unauthorized direct invocations to the sign-on screen.

5. **User-type authorization:** The menu enforces admin/user access control at the option level via the USRTYPE field in the menu table, though all options are currently set to 'U'.

### Gaps / Migration Considerations

1. **No COPAUS0C implementation:** Option 11 (Pending Authorization View) references a program that does not exist in the repository. This is either a planned-but-unimplemented feature or requires an external dependency.

2. **Unused WS-USRSEC-FILE:** The USRSEC file variable is declared but never used in COMEN01C. This is dead code from template reuse.

3. **No session timeout handling:** There is no explicit session timeout or inactivity detection. The pseudo-conversational pattern relies on CICS transaction timeout settings (DTIMOUT=NO in CSD).

4. **Password in clear text:** The upstream COSGN00C stores and compares passwords in plain text (SEC-USR-PWD). Modern migration targets will require proper password hashing.

5. **Fixed 12-option limit:** The BUILD-MENU-OPTIONS paragraph uses a hardcoded EVALUATE with cases 1-12, not a dynamic field reference. Adding a 13th option would require code changes.

6. **BMS symbolic map not in cpy/:** The COMEN01 copybook is auto-generated from BMS source at compile time. Migration will need to replicate this map structure as form/page definitions.

---

## Section 11: Migration Complexity Comparison

| Dimension | COMEN01C (This Flow) | Typical CICS Menu Program |
|-----------|----------------------|---------------------------|
| Lines of COBOL | 309 | 200-500 |
| EXEC CICS commands | 5 (2 XCTL, 1 SEND, 1 RECEIVE, 1 RETURN) + 1 INQUIRE | 3-8 |
| VSAM file I/O | 0 | 0-2 |
| Copybooks | 9 (6 app + 1 BMS + 2 system) | 5-12 |
| BMS maps | 1 | 1-2 |
| Downstream programs | 11 (via XCTL) | 3-15 |
| COMMAREA fields used | ~6 of 15 | 5-20 |
| Business logic complexity | Low (menu navigation only) | Low-Medium |
| CALL statements | 0 | 0-3 |
| LINK statements | 0 | 0-2 |
| Dynamic program resolution | Yes (table-driven XCTL) | Varies |

### Migration Effort Estimate

| Component | Effort | Notes |
|-----------|--------|-------|
| Menu screen → Web UI | Low | Simple list/navigation component |
| COMMAREA → Session state | Low | Map to session object or JWT claims |
| XCTL navigation → Routing | Low | Map to URL routes or SPA navigation |
| PF-key handling → UI events | Low | Map to buttons/keyboard shortcuts |
| Authorization check | Low | Map to role-based access control middleware |
| BMS map → HTML/CSS | Low | Standard form layout |
| **Total** | **Low** | Pure navigation program with no data access |

---

## EXEC CICS Command Inventory (Complete)

| # | Command | Location (Paragraph) | Parameters | Error Handling |
|---|---------|----------------------|------------|----------------|
| 1 | SEND MAP | SEND-MENU-SCREEN | MAP('COMEN1A') MAPSET('COMEN01') FROM(COMEN1AO) ERASE | None |
| 2 | RECEIVE MAP | RECEIVE-MENU-SCREEN | MAP('COMEN1A') MAPSET('COMEN01') INTO(COMEN1AI) RESP(WS-RESP-CD) RESP2(WS-REAS-CD) | RESP/RESP2 captured |
| 3 | RETURN | MAIN-PARA (end) | TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA) | None |
| 4 | INQUIRE PROGRAM | PROCESS-ENTER-KEY | PROGRAM(CDEMO-MENU-OPT-PGMNAME(WS-OPTION)) NOHANDLE | NOHANDLE; checks EIBRESP after |
| 5 | XCTL | PROCESS-ENTER-KEY (COPAUS0C path) | PROGRAM(CDEMO-MENU-OPT-PGMNAME(WS-OPTION)) COMMAREA(CARDDEMO-COMMAREA) | None (follows INQUIRE check) |
| 6 | XCTL | PROCESS-ENTER-KEY (default path) | PROGRAM(CDEMO-MENU-OPT-PGMNAME(WS-OPTION)) COMMAREA(CARDDEMO-COMMAREA) | None |
| 7 | XCTL | RETURN-TO-SIGNON-SCREEN | PROGRAM(CDEMO-TO-PROGRAM) | None |

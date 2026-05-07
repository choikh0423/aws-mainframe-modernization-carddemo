# CU00 Transaction Flow Analysis — COUSR00C (User List / Security Admin)

## Section 1: Flow Overview

| Attribute          | Value                                              |
|--------------------|----------------------------------------------------|
| **Transaction ID** | `CU00`                                             |
| **Entry Program**  | `COUSR00C`                                         |
| **Function**       | List all users from the USRSEC VSAM file           |
| **Type**           | CICS COBOL — pseudo-conversational                 |
| **Application**    | CardDemo (AWS Mainframe Modernization)             |

### Navigation Path

```
  CC00 (COSGN00C)            Sign-on Screen
        |
        | [Admin user XCTL]
        v
  CA00 (COADM01C)            Admin Menu
        |
        | [Option 1 XCTL via menu table]
        v
  CU00 (COUSR00C)  <------   User List Screen   (THIS PROGRAM)
        |                         |
        | [Sel='U' XCTL]         | [Sel='D' XCTL]
        v                         v
  CU02 (COUSR02C)            CU03 (COUSR03C)
  Update User                Delete User
```

### Pseudo-Conversational Lifecycle

```
  +-----------+      EIBCALEN=0       +-----------+
  |  CICS     | --------------------> | XCTL to   |
  |  starts   |                       | COSGN00C  |
  |  CU00     |                       +-----------+
  +-----------+
       |  EIBCALEN > 0
       v
  +------------------+   PGM-CONTEXT=0   +--------------------+
  | MAIN-PARA        | ----------------> | First entry:       |
  | Move COMMAREA    |                   |  PROCESS-ENTER-KEY |
  +------------------+                   |  SEND screen       |
       |  PGM-CONTEXT=1                  +--------------------+
       v                                          |
  +------------------+                            v
  | Re-entry:        |                   +--------------------+
  | RECEIVE screen   |                   | RETURN TRANSID     |
  | EVALUATE EIBAID  |                   | ('CU00')           |
  +------------------+                   | COMMAREA           |
       |                                 +--------------------+
       +------+--------+--------+
       |      |        |        |
    ENTER   PF3      PF7      PF8       OTHER
       |      |        |        |          |
  Process  XCTL to  Page     Page     Error msg
  Enter    COADM01C Backward Forward  + SEND
  Key
       |
       +--- User selected? --+--> 'U': XCTL COUSR02C
                              +--> 'D': XCTL COUSR03C
                              +--> other: error msg
                              +--> none: browse USRSEC, SEND list
```

---

## Section 2: Programs Involved

| Program    | Type        | Transaction | Function                          | Called From  | Call Method              |
|------------|-------------|-------------|-----------------------------------|--------------|--------------------------|
| COSGN00C   | CICS COBOL  | CC00        | Sign-on screen                    | (entry)      | Initial CICS transaction |
| COADM01C   | CICS COBOL  | CA00        | Admin menu                        | COSGN00C     | XCTL                     |
| **COUSR00C** | **CICS COBOL** | **CU00** | **User list (Security/Admin)**    | COADM01C     | XCTL (menu option 1)     |
| COUSR02C   | CICS COBOL  | CU02        | Update user                       | COUSR00C     | XCTL                     |
| COUSR03C   | CICS COBOL  | CU03        | Delete user                       | COUSR00C     | XCTL                     |

> **Note:** There are no `CALL` statements in COUSR00C. All program transfers use `EXEC CICS XCTL`.

---

## Section 3: Copybooks

| Copybook   | Used By              | Classification               | Purpose                                              |
|------------|----------------------|------------------------------|------------------------------------------------------|
| COCOM01Y   | COUSR00C, COADM01C, COSGN00C, COUSR01C, COUSR02C, COUSR03C | COMMAREA definition | Defines `CARDDEMO-COMMAREA` — navigation fields, customer/account/card info |
| COUSR00    | COUSR00C             | BMS symbolic map (generated) | Symbolic map for COUSR0A — input (`COUSR0AI`) and output (`COUSR0AO`) structures |
| COTTL01Y   | COUSR00C, COADM01C, COSGN00C, COUSR01C, COUSR02C, COUSR03C | Screen title constants | Application title lines (`CCDA-TITLE01`, `CCDA-TITLE02`) |
| CSDAT01Y   | COUSR00C, COADM01C, COSGN00C, COUSR01C, COUSR02C, COUSR03C | Date/time structure | `WS-CURDATE-DATA`, formatted date `MM/DD/YY`, time `HH:MM:SS` |
| CSMSG01Y   | COUSR00C, COADM01C, COSGN00C, COUSR01C, COUSR02C, COUSR03C | Common messages | `CCDA-MSG-INVALID-KEY`, `CCDA-MSG-THANK-YOU` |
| CSUSR01Y   | COUSR00C, COADM01C, COSGN00C, COUSR01C, COUSR02C, COUSR03C | VSAM record layout | `SEC-USER-DATA` — user security record (80 bytes) |
| DFHAID     | COUSR00C (+ others)  | CICS system copybook         | AID key constants (`DFHENTER`, `DFHPF3`, `DFHPF7`, `DFHPF8`) |
| DFHBMSCA   | COUSR00C (+ others)  | CICS system copybook         | BMS attribute constants (`DFHGREEN`, `DFHRED`, `DFHNEUTR`) |
| COADM02Y   | COADM01C             | Menu options table           | Admin menu option definitions — maps option numbers to program names |

### Copybook Record Layouts

#### COCOM01Y — CARDDEMO-COMMAREA

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

#### CDEMO-CU00-INFO (inline in COUSR00C, appended to COMMAREA)

```
   05 CDEMO-CU00-INFO.
      10 CDEMO-CU00-USRID-FIRST     PIC X(08).
      10 CDEMO-CU00-USRID-LAST      PIC X(08).
      10 CDEMO-CU00-PAGE-NUM        PIC 9(08).
      10 CDEMO-CU00-NEXT-PAGE-FLG   PIC X(01).
         88 NEXT-PAGE-YES                     VALUE 'Y'.
         88 NEXT-PAGE-NO                      VALUE 'N'.
      10 CDEMO-CU00-USR-SEL-FLG     PIC X(01).
      10 CDEMO-CU00-USR-SELECTED    PIC X(08).
```

#### CSUSR01Y — SEC-USER-DATA (USRSEC VSAM Record)

```
01 SEC-USER-DATA.                         Total: 80 bytes
   05 SEC-USR-ID                 PIC X(08).    Offset 0   Key field
   05 SEC-USR-FNAME              PIC X(20).    Offset 8
   05 SEC-USR-LNAME              PIC X(20).    Offset 28
   05 SEC-USR-PWD                PIC X(08).    Offset 48
   05 SEC-USR-TYPE               PIC X(01).    Offset 56
   05 SEC-USR-FILLER             PIC X(23).    Offset 57
```

#### COTTL01Y — Screen Titles

```
01 CCDA-SCREEN-TITLE.
   05 CCDA-TITLE01    PIC X(40) VALUE '      AWS Mainframe Modernization       '.
   05 CCDA-TITLE02    PIC X(40) VALUE '              CardDemo                  '.
   05 CCDA-THANK-YOU  PIC X(40) VALUE 'Thank you for using CCDA application... '.
```

#### CSDAT01Y — Date/Time Work Areas

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
```

#### CSMSG01Y — Common Messages

```
01 CCDA-COMMON-MESSAGES.
   05 CCDA-MSG-THANK-YOU         PIC X(50)  'Thank you for using CardDemo application...'.
   05 CCDA-MSG-INVALID-KEY       PIC X(50)  'Invalid key pressed. Please see below...'.
```

---

## Section 4: VSAM File Operations

### File Summary

| DD Name  | VSAM Type | Key Field    | Key Len | Record Copybook | Record Length | Access in COUSR00C      |
|----------|-----------|--------------|---------|-----------------|---------------|-------------------------|
| USRSEC   | KSDS      | SEC-USR-ID   | 8 bytes | CSUSR01Y        | 80 bytes      | Browse (STARTBR/READNEXT/READPREV/ENDBR) |

### Detailed I/O Operations

| # | Command    | Dataset  | RIDFLD      | Purpose                                  | RESP Handling                                    |
|---|------------|----------|-------------|------------------------------------------|--------------------------------------------------|
| 1 | STARTBR    | USRSEC   | SEC-USR-ID  | Position browse cursor at search key     | NORMAL: continue; NOTFND: EOF msg; OTHER: error  |
| 2 | READNEXT   | USRSEC   | SEC-USR-ID  | Read next user record in forward browse  | NORMAL: continue; ENDFILE: EOF msg; OTHER: error |
| 3 | READPREV   | USRSEC   | SEC-USR-ID  | Read previous record in backward browse  | NORMAL: continue; ENDFILE: EOF msg; OTHER: error |
| 4 | ENDBR      | USRSEC   | —           | End browse session                       | (none)                                           |

### VSAM File Flow Diagram

```
  USRSEC (User Security VSAM KSDS)
  +=================================+
  | Key: SEC-USR-ID (8 bytes)       |
  | Record: 80 bytes (CSUSR01Y)     |
  +=================================+
           |
  +--------+---------------------------+
  |                                    |
  v (Forward Paging)                   v (Backward Paging)
  STARTBR (SEC-USR-ID)                STARTBR (SEC-USR-ID)
     |                                    |
     v                                    v
  READNEXT x10 (fill page)            READPREV x10 (fill page)
     |                                    |
     v                                    v
  READNEXT +1 (peek next page)        READPREV +1 (check prev)
     |                                    |
     v                                    v
  ENDBR                                ENDBR
     |                                    |
     v                                    v
  SEND MAP (display list)             SEND MAP (display list)
```

---

## Section 5: BMS Screen Map

### Map Definition

| Attribute      | Value       |
|----------------|-------------|
| **Mapset**     | `COUSR00`   |
| **Map**        | `COUSR0A`   |
| **Size**       | 24 rows x 80 cols |
| **CTRL**       | ALARM, FREEKB |
| **LANG**       | COBOL       |
| **MODE**       | INOUT       |

### ASCII Screen Layout

```
Row Col  Field
--- ---  -----
 1   1   Tran: CU00                AWS Mainframe Modernization            Date: mm/dd/yy
 2   1   Prog: COUSR00C                    CardDemo                       Time: hh:mm:ss
 3
 4  35                          List Users                          Page: 00000001
 5
 6   5   Search User ID: [________]
 7
 8   5   Sel  User ID       First Name            Last Name             Type
 9   5   ---  --------  --------------------  --------------------  ----
10   6   [_]  ADMIN001  John                  Smith                 A
11   6   [_]  ADMIN002  Jane                  Doe                   A
12   6   [_]  USER0001  Alice                 Johnson               U
13   6   [_]  USER0002  Bob                   Williams              U
14   6   [_]  USER0003  Carol                 Brown                 U
15   6   [_]  USER0004  David                 Davis                 U
16   6   [_]  USER0005  Eve                   Miller                U
17   6   [_]  USER0006  Frank                 Wilson                U
18   6   [_]  USER0007  Grace                 Moore                 U
19   6   [_]  USER0008  Henry                 Taylor                U
20
21  12   Type 'U' to Update or 'D' to Delete a User from the list
22
23   1   [Error/status message area — 78 chars, RED, BRT]
24   1   ENTER=Continue  F3=Back  F7=Backward  F8=Forward
```

> Note: Data rows 10-19 show sample data. Actual content is populated from USRSEC browse.
> `[_]` indicates UNPROT input fields; other data fields are ASKIP display-only.

### Field Inventory

| Field Name | Row | Col | Length | Attributes        | Color    | Purpose                     |
|------------|-----|-----|--------|-------------------|----------|-----------------------------|
| TRNNAME    | 1   | 7   | 4      | ASKIP,FSET,NORM   | BLUE     | Transaction ID              |
| TITLE01    | 1   | 21  | 40     | ASKIP,FSET,NORM   | YELLOW   | Application title line 1    |
| CURDATE    | 1   | 71  | 8      | ASKIP,FSET,NORM   | BLUE     | Current date (mm/dd/yy)     |
| PGMNAME    | 2   | 7   | 8      | ASKIP,FSET,NORM   | BLUE     | Program name                |
| TITLE02    | 2   | 21  | 40     | ASKIP,FSET,NORM   | YELLOW   | Application title line 2    |
| CURTIME    | 2   | 71  | 8      | ASKIP,FSET,NORM   | BLUE     | Current time (hh:mm:ss)     |
| PAGENUM    | 4   | 71  | 8      | ASKIP,FSET,NORM   | BLUE     | Page number                 |
| USRIDIN    | 6   | 21  | 8      | FSET,NORM,UNPROT  | GREEN    | Search User ID (input)      |
| SEL0001    | 10  | 6   | 1      | FSET,NORM,UNPROT  | GREEN    | Selection field row 1       |
| USRID01    | 10  | 12  | 8      | ASKIP,FSET,NORM   | BLUE     | User ID row 1               |
| FNAME01    | 10  | 24  | 20     | ASKIP,FSET,NORM   | BLUE     | First name row 1            |
| LNAME01    | 10  | 48  | 20     | ASKIP,FSET,NORM   | BLUE     | Last name row 1             |
| UTYPE01    | 10  | 73  | 1      | ASKIP,FSET,NORM   | BLUE     | User type row 1             |
| SEL0002    | 11  | 6   | 1      | FSET,NORM,UNPROT  | GREEN    | Selection field row 2       |
| USRID02    | 11  | 12  | 8      | ASKIP,FSET,NORM   | BLUE     | User ID row 2               |
| FNAME02    | 11  | 24  | 20     | ASKIP,FSET,NORM   | BLUE     | First name row 2            |
| LNAME02    | 11  | 48  | 20     | ASKIP,FSET,NORM   | BLUE     | Last name row 2             |
| UTYPE02    | 11  | 73  | 1      | ASKIP,FSET,NORM   | BLUE     | User type row 2             |
| SEL0003    | 12  | 6   | 1      | FSET,NORM,UNPROT  | GREEN    | Selection field row 3       |
| USRID03    | 12  | 12  | 8      | ASKIP,FSET,NORM   | BLUE     | User ID row 3               |
| FNAME03    | 12  | 24  | 20     | ASKIP,FSET,NORM   | BLUE     | First name row 3            |
| LNAME03    | 12  | 48  | 20     | ASKIP,FSET,NORM   | BLUE     | Last name row 3             |
| UTYPE03    | 12  | 73  | 1      | ASKIP,FSET,NORM   | BLUE     | User type row 3             |
| SEL0004    | 13  | 6   | 1      | FSET,NORM,UNPROT  | GREEN    | Selection field row 4       |
| USRID04    | 13  | 12  | 8      | ASKIP,FSET,NORM   | BLUE     | User ID row 4               |
| FNAME04    | 13  | 24  | 20     | ASKIP,FSET,NORM   | BLUE     | First name row 4            |
| LNAME04    | 13  | 48  | 20     | ASKIP,FSET,NORM   | BLUE     | Last name row 4             |
| UTYPE04    | 13  | 73  | 1      | ASKIP,FSET,NORM   | BLUE     | User type row 4             |
| SEL0005    | 14  | 6   | 1      | FSET,NORM,UNPROT  | GREEN    | Selection field row 5       |
| USRID05    | 14  | 12  | 8      | ASKIP,FSET,NORM   | BLUE     | User ID row 5               |
| FNAME05    | 14  | 24  | 20     | ASKIP,FSET,NORM   | BLUE     | First name row 5            |
| LNAME05    | 14  | 48  | 20     | ASKIP,FSET,NORM   | BLUE     | Last name row 5             |
| UTYPE05    | 14  | 73  | 1      | ASKIP,FSET,NORM   | BLUE     | User type row 5             |
| SEL0006    | 15  | 6   | 1      | FSET,NORM,UNPROT  | GREEN    | Selection field row 6       |
| USRID06    | 15  | 12  | 8      | ASKIP,FSET,NORM   | BLUE     | User ID row 6               |
| FNAME06    | 15  | 24  | 20     | ASKIP,FSET,NORM   | BLUE     | First name row 6            |
| LNAME06    | 15  | 48  | 20     | ASKIP,FSET,NORM   | BLUE     | Last name row 6             |
| UTYPE06    | 15  | 73  | 1      | ASKIP,FSET,NORM   | BLUE     | User type row 6             |
| SEL0007    | 16  | 6   | 1      | FSET,NORM,UNPROT  | GREEN    | Selection field row 7       |
| USRID07    | 16  | 12  | 8      | ASKIP,FSET,NORM   | BLUE     | User ID row 7               |
| FNAME07    | 16  | 24  | 20     | ASKIP,FSET,NORM   | BLUE     | First name row 7            |
| LNAME07    | 16  | 48  | 20     | ASKIP,FSET,NORM   | BLUE     | Last name row 7             |
| UTYPE07    | 16  | 73  | 1      | ASKIP,FSET,NORM   | BLUE     | User type row 7             |
| SEL0008    | 17  | 6   | 1      | FSET,NORM,UNPROT  | GREEN    | Selection field row 8       |
| USRID08    | 17  | 12  | 8      | ASKIP,FSET,NORM   | BLUE     | User ID row 8               |
| FNAME08    | 17  | 24  | 20     | ASKIP,FSET,NORM   | BLUE     | First name row 8            |
| LNAME08    | 17  | 48  | 20     | ASKIP,FSET,NORM   | BLUE     | Last name row 8             |
| UTYPE08    | 17  | 73  | 1      | ASKIP,FSET,NORM   | BLUE     | User type row 8             |
| SEL0009    | 18  | 6   | 1      | FSET,NORM,UNPROT  | GREEN    | Selection field row 9       |
| USRID09    | 18  | 12  | 8      | ASKIP,FSET,NORM   | BLUE     | User ID row 9               |
| FNAME09    | 18  | 24  | 20     | ASKIP,FSET,NORM   | BLUE     | First name row 9            |
| LNAME09    | 18  | 48  | 20     | ASKIP,FSET,NORM   | BLUE     | Last name row 9             |
| UTYPE09    | 18  | 73  | 1      | ASKIP,FSET,NORM   | BLUE     | User type row 9             |
| SEL0010    | 19  | 6   | 1      | FSET,NORM,UNPROT  | GREEN    | Selection field row 10      |
| USRID10    | 19  | 12  | 8      | ASKIP,FSET,NORM   | BLUE     | User ID row 10              |
| FNAME10    | 19  | 24  | 20     | ASKIP,FSET,NORM   | BLUE     | First name row 10           |
| LNAME10    | 19  | 48  | 20     | ASKIP,FSET,NORM   | BLUE     | Last name row 10            |
| UTYPE10    | 19  | 73  | 1      | ASKIP,FSET,NORM   | BLUE     | User type row 10            |
| ERRMSG     | 23  | 1   | 78     | ASKIP,BRT,FSET    | RED      | Error/status message        |

### Function Key Assignments

| Key   | Action                                          | Handler Paragraph     |
|-------|-------------------------------------------------|-----------------------|
| ENTER | Process selection or browse from search User ID | PROCESS-ENTER-KEY     |
| PF3   | Return to Admin Menu (XCTL to COADM01C)        | RETURN-TO-PREV-SCREEN |
| PF7   | Page backward through user list                 | PROCESS-PF7-KEY       |
| PF8   | Page forward through user list                  | PROCESS-PF8-KEY       |
| OTHER | Display "Invalid key pressed" error message      | (inline in EVALUATE)  |

---

## Section 6: COMMAREA Structure

### COMMAREA Field Usage by Program

| Field                    | COSGN00C (CC00) | COADM01C (CA00) | COUSR00C (CU00) | COUSR02C (CU02) | COUSR03C (CU03) |
|--------------------------|:---------------:|:----------------:|:----------------:|:----------------:|:----------------:|
| CDEMO-FROM-TRANID        | W               | W                | W                | W                | W                |
| CDEMO-FROM-PROGRAM       | W               | W                | W                | W                | W                |
| CDEMO-TO-TRANID          | —               | —                | —                | —                | —                |
| CDEMO-TO-PROGRAM         | —               | W                | W                | W                | W                |
| CDEMO-USER-ID            | W               | R                | R                | R                | R                |
| CDEMO-USER-TYPE          | W               | R                | R                | R                | R                |
| CDEMO-PGM-CONTEXT        | W(0)            | R/W              | R/W              | R/W              | R/W              |
| CDEMO-CU00-USRID-FIRST   | —               | —                | W                | —                | —                |
| CDEMO-CU00-USRID-LAST    | —               | —                | W                | —                | —                |
| CDEMO-CU00-PAGE-NUM      | —               | —                | R/W              | —                | —                |
| CDEMO-CU00-NEXT-PAGE-FLG | —               | —                | R/W              | —                | —                |
| CDEMO-CU00-USR-SEL-FLG   | —               | —                | W                | —                | —                |
| CDEMO-CU00-USR-SELECTED  | —               | —                | W                | R                | R                |

> **W** = writes, **R** = reads, **R/W** = reads and writes, **—** = not used

### COMMAREA Inter-Program Flow

```
  COSGN00C                COADM01C                COUSR00C
  +-----------+           +-----------+           +----------------+
  | Sets:     |  XCTL     | Sets:     |  XCTL    | Sets:          |
  | FROM-TRAN | --------> | FROM-TRAN | -------> | FROM-TRAN      |
  | FROM-PGM  |  COMMAREA | FROM-PGM  | COMMAREA | FROM-PGM       |
  | USER-ID   |           | PGM-CTX=0 |          | PGM-CTX=0/1    |
  | USER-TYPE |           +-----------+          | CU00-USRID-*   |
  | PGM-CTX=0 |                                  | CU00-PAGE-NUM  |
  +-----------+                                  | CU00-SEL-FLG   |
                                                 | CU00-USR-SEL   |
                                                 +-------+--------+
                                                         |
                                     +-------------------+-------------------+
                                     | XCTL (sel='U')                       | XCTL (sel='D')
                                     v                                       v
                               COUSR02C                                COUSR03C
                               +-----------+                           +-----------+
                               | Reads:    |                           | Reads:    |
                               | FROM-PGM  |                           | FROM-PGM  |
                               | USR-SEL   |                           | USR-SEL   |
                               +-----------+                           +-----------+
```

---

## Section 7: Business Logic Flow

### 1. Entry and Initialization (MAIN-PARA)

1. Reset flags: `ERR-FLG-OFF`, `USER-SEC-NOT-EOF`, `NEXT-PAGE-NO`, `SEND-ERASE-YES`
2. Clear `WS-MESSAGE` and `ERRMSGO`
3. Set cursor to `USRIDINL` (search field, cursor position = -1)
4. **EIBCALEN = 0** (no COMMAREA — direct invocation): XCTL to `COSGN00C`
5. **EIBCALEN > 0**: Move DFHCOMMAREA into `CARDDEMO-COMMAREA`

### 2. First Entry (PGM-CONTEXT = 0)

1. Set `CDEMO-PGM-REENTER` to TRUE (PGM-CONTEXT = 1)
2. Initialize map output `COUSR0AO` to LOW-VALUES
3. Perform `PROCESS-ENTER-KEY` (initial browse with no search criteria)
4. Send the user list screen

### 3. Re-entry (PGM-CONTEXT = 1) — EIBAID Handling

1. **RECEIVE MAP** `COUSR0A` from terminal
2. **EVALUATE EIBAID:**
   - **ENTER**: Process selection or re-browse
   - **PF3**: XCTL back to `COADM01C`
   - **PF7**: Page backward
   - **PF8**: Page forward
   - **OTHER**: Display invalid key message

### 4. PROCESS-ENTER-KEY — Selection and Browse

1. **Check all 10 selection fields** (`SEL0001I` through `SEL0010I`):
   - If a selection field is non-empty, capture the selection flag and corresponding User ID
   - Store in `CDEMO-CU00-USR-SEL-FLG` and `CDEMO-CU00-USR-SELECTED`
2. **If a user is selected:**
   - `'U'` or `'u'`: Set `CDEMO-TO-PROGRAM = 'COUSR02C'`, set FROM fields, reset PGM-CONTEXT to 0, **XCTL** to COUSR02C (User Update)
   - `'D'` or `'d'`: Set `CDEMO-TO-PROGRAM = 'COUSR03C'`, set FROM fields, reset PGM-CONTEXT to 0, **XCTL** to COUSR03C (User Delete)
   - Other value: Display "Invalid selection. Valid values are U and D"
3. **Search User ID processing:**
   - If `USRIDINI` is empty, set `SEC-USR-ID` to LOW-VALUES (start from beginning)
   - Otherwise, use the entered value as the browse starting key
4. Reset page number to 0
5. Perform `PROCESS-PAGE-FORWARD`

### 5. PROCESS-PAGE-FORWARD

1. `STARTBR` on USRSEC at `SEC-USR-ID`
2. If not on ENTER/PF7/PF3, perform one `READNEXT` to skip current position
3. Initialize all 10 display rows to SPACES
4. Loop `READNEXT` up to 10 times, populating rows 1-10 with user data
5. Increment page number
6. Attempt one more `READNEXT` to check if a next page exists:
   - If successful: set `NEXT-PAGE-YES`
   - If ENDFILE: set `NEXT-PAGE-NO`
7. `ENDBR` to end browse
8. Send the screen

### 6. PROCESS-PAGE-BACKWARD (PF7)

1. If already at page 1, display "You are already at the top of the page..."
2. `STARTBR` on USRSEC at the first User ID of the current page
3. If not on ENTER/PF8, perform one `READPREV` to skip current position
4. Initialize all 10 display rows
5. Loop `READPREV` up to 10 times, populating rows 10 down to 1
6. Adjust page number (decrement by 1)
7. `ENDBR` to end browse
8. Send the screen

### 7. Populate and Initialize User Data

- **POPULATE-USER-DATA**: For each index 1-10, moves `SEC-USR-ID`, `SEC-USR-FNAME`, `SEC-USR-LNAME`, `SEC-USR-TYPE` into the corresponding map fields
  - Index 1 also saves `SEC-USR-ID` to `CDEMO-CU00-USRID-FIRST`
  - Index 10 also saves `SEC-USR-ID` to `CDEMO-CU00-USRID-LAST`
- **INITIALIZE-USER-DATA**: Clears all fields for a given index row to SPACES

### 8. Screen Send/Receive

- **SEND-USRLST-SCREEN**: Calls `POPULATE-HEADER-INFO`, moves `WS-MESSAGE` to `ERRMSGO`, sends map `COUSR0A` (with or without ERASE based on `WS-SEND-ERASE-FLG`)
- **RECEIVE-USRLST-SCREEN**: Receives map `COUSR0A` into `COUSR0AI` with RESP/RESP2
- **POPULATE-HEADER-INFO**: Gets `CURRENT-DATE`, formats date/time, moves titles, transaction name, and program name to header fields

### 9. RETURN (Pseudo-Conversational)

After all processing, the program issues:
```cobol
EXEC CICS RETURN
    TRANSID (WS-TRANID)        -- 'CU00'
    COMMAREA (CARDDEMO-COMMAREA)
END-EXEC
```
This suspends the task. CICS will restart COUSR00C when the user presses a key.

### 10. Error Handling

| Condition        | RESP Code        | Action                                         |
|------------------|------------------|-------------------------------------------------|
| STARTBR NOTFND   | DFHRESP(NOTFND)  | Set EOF, display "You are at the top of page"  |
| STARTBR OTHER    | Other            | Set error flag, "Unable to lookup User..."     |
| READNEXT ENDFILE | DFHRESP(ENDFILE) | Set EOF, "You have reached the bottom..."      |
| READNEXT OTHER   | Other            | Set error flag, "Unable to lookup User..."     |
| READPREV ENDFILE | DFHRESP(ENDFILE) | Set EOF, "You have reached the top..."         |
| READPREV OTHER   | Other            | Set error flag, "Unable to lookup User..."     |
| Invalid AID key  | —                | Display CCDA-MSG-INVALID-KEY                   |
| Invalid selection| —                | "Invalid selection. Valid values are U and D"  |

---

## Section 8: Complete File Inventory

### COBOL Source Files

| File                 | Path                  | Role in CU00 Flow             |
|----------------------|-----------------------|-------------------------------|
| COUSR00C.cbl         | `app/cbl/COUSR00C.cbl` | Target program (User List)   |
| COADM01C.cbl         | `app/cbl/COADM01C.cbl` | Upstream — Admin Menu        |
| COSGN00C.cbl         | `app/cbl/COSGN00C.cbl` | Upstream — Sign-on           |
| COUSR02C.cbl         | `app/cbl/COUSR02C.cbl` | Downstream — User Update     |
| COUSR03C.cbl         | `app/cbl/COUSR03C.cbl` | Downstream — User Delete     |

### Copybook Files

| File                 | Path                  | Classification                |
|----------------------|-----------------------|-------------------------------|
| COCOM01Y.cpy         | `app/cpy/COCOM01Y.cpy` | COMMAREA definition          |
| COTTL01Y.cpy         | `app/cpy/COTTL01Y.cpy` | Screen title constants       |
| CSDAT01Y.cpy         | `app/cpy/CSDAT01Y.cpy` | Date/time structure          |
| CSMSG01Y.cpy         | `app/cpy/CSMSG01Y.cpy` | Common messages              |
| CSUSR01Y.cpy         | `app/cpy/CSUSR01Y.cpy` | USRSEC record layout         |
| DFHAID               | (system)              | CICS system — AID key constants |
| DFHBMSCA             | (system)              | CICS system — BMS attribute constants |

### BMS Map Files

| File                 | Path                  | Mapset / Map                  |
|----------------------|-----------------------|-------------------------------|
| COUSR00.bms          | `app/bms/COUSR00.bms` | COUSR00 / COUSR0A            |

### BMS Symbolic Map Copybooks (Generated)

| Copybook             | Path                  | Generated From               |
|----------------------|-----------------------|------------------------------|
| COUSR00              | `app/cpy/COUSR00.cpy` | N/A — included via `COPY COUSR00.` statement; provides COUSR0AI (input) and COUSR0AO (output) structures. Not a file in `app/cpy/` — this is a BMS-generated symbolic map. |

> **Note:** The `COPY COUSR00.` statement in COUSR00C references the BMS-generated symbolic map copybook. In standard mainframe builds this is auto-generated from `COUSR00.bms`. The copybook file does not exist as a standalone `.cpy` in the `app/cpy/` directory — this is expected behavior for BMS symbolic maps.

### VSAM Data Files

| DD Name  | CSD Definition                                    | VSAM Type | DSN                                       |
|----------|---------------------------------------------------|-----------|--------------------------------------------|
| USRSEC   | `DEFINE FILE(USRSEC) GROUP(CARDDEMO)`             | KSDS      | `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS`        |

---

## Section 9: User Security Record Layout (USRSEC)

```
Offset  Length  Field           PIC         Description
------  ------  --------------- ----------  ---------------------------
0       8       SEC-USR-ID      X(08)       User ID (primary key)
8       20      SEC-USR-FNAME   X(20)       First name
28      20      SEC-USR-LNAME   X(20)       Last name
48      8       SEC-USR-PWD     X(08)       Password
56      1       SEC-USR-TYPE    X(01)       User type (A=Admin, U=User)
57      23      SEC-USR-FILLER  X(23)       Filler/reserved
------  ------
Total:  80 bytes
```

> COUSR00C only browses (reads) USRSEC — it does **not** write, update, or delete records. Write/update/delete operations are performed by COUSR01C, COUSR02C, and COUSR03C respectively.

---

## Section 10: Observations

### Strengths

1. **Clean pseudo-conversational pattern**: Standard RETURN TRANSID with COMMAREA — straightforward to convert to a stateless web request/response model
2. **Single VSAM file dependency**: COUSR00C only accesses USRSEC, making it a self-contained read-only list screen
3. **Simple navigation model**: PF-key driven with clear XCTL chain — maps directly to REST endpoints or page routes
4. **No CALL statements**: All inter-program transfers use XCTL, simplifying the call graph
5. **Consistent error handling**: All VSAM operations check RESP/RESP2 with appropriate user messages
6. **Paginated browse**: Forward/backward paging with 10 records per page — translates to standard pagination with offset/limit

### Migration Considerations

1. **BMS-generated symbolic maps**: The `COPY COUSR00.` statement references an auto-generated copybook that defines the screen I/O structure. A migration must replace this with equivalent input/output DTOs or form bindings
2. **COMMAREA state management**: Page position (`CU00-USRID-FIRST`, `CU00-USRID-LAST`, `CU00-PAGE-NUM`) is carried in COMMAREA between pseudo-conversational iterations. In a web app this maps to session state, URL parameters, or client-side state
3. **VSAM browse semantics**: STARTBR/READNEXT/READPREV with key positioning needs to be translated to database queries with ORDER BY and WHERE clauses (keyset pagination)
4. **Inline COMMAREA extension**: The `CDEMO-CU00-INFO` group is defined inline in COUSR00C (not in a shared copybook), appended after `COCOM01Y`. This pattern means each program may extend the COMMAREA differently
5. **Selection-to-XCTL pattern**: The user list acts as a hub — selecting a row with 'U' or 'D' triggers XCTL to a detail screen. In a modern app this would be a hyperlink or button routing to an update/delete page
6. **No DB2/IMS**: This is pure VSAM — no SQL, no DL/I calls. Migration to a relational database requires defining equivalent table schema from the CSUSR01Y copybook

### Gaps

1. **No audit trail**: User list browsing is not logged; no transaction tracking for who viewed the list
2. **Password stored in plain text**: SEC-USR-PWD is an 8-byte clear-text field — a migration must add proper password hashing
3. **No authorization check within program**: COUSR00C does not verify that the current user is an admin — it relies on the upstream navigation path (COADM01C is only reachable if COSGN00C routes admin users there)
4. **Hard-coded page size**: The 10-record page size is embedded in the COBOL logic and BMS map — not configurable

---

## Section 11: Complexity Comparison

| Dimension                  | COUSR00C (CU00)        | Notes                                         |
|----------------------------|------------------------|-----------------------------------------------|
| Programs in execution path | 5                      | COSGN00C → COADM01C → COUSR00C → COUSR02C/03C |
| Copybooks                  | 7 app + 2 system       | 9 total                                       |
| VSAM files accessed        | 1 (USRSEC)             | Read-only browse                              |
| BMS maps                   | 1 (COUSR0A)            | Single screen                                 |
| EXEC CICS commands         | 10                     | 2 SEND, 1 RECEIVE, 1 STARTBR, 1 READNEXT, 1 READPREV, 1 ENDBR, 2 XCTL, 1 RETURN |
| CALL statements            | 0                      | No sub-program calls                          |
| Lines of COBOL             | 696                    | Medium complexity                             |
| DB2 SQL statements         | 0                      | Pure VSAM                                     |
| Error handling paths       | 8                      | 3 VSAM error conditions x 2 + 2 UI errors    |
| Input fields per screen    | 11                     | 1 search + 10 selection                       |

**Overall Complexity: LOW-MEDIUM** — This is a standard list/browse screen with pagination and selection routing. The single-file read-only access pattern and absence of business rule validation make it one of the simpler CICS transactions to migrate.

---

*Generated for CardDemo Mainframe Modernization — CU00 Transaction (COUSR00C)*

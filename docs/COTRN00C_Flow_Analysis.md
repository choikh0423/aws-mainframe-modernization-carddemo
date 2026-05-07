# COTRN00C (CT00) — Transaction List: CICS Transaction Flow Analysis

## Section 1: Flow Overview

| Attribute           | Value                                           |
|---------------------|-------------------------------------------------|
| **Transaction ID**  | CT00                                            |
| **Entry Program**   | COTRN00C                                        |
| **Function**        | List Transactions from the TRANSACT VSAM file   |
| **Application**     | CardDemo — Credit Card Management System        |
| **Data Access**     | VSAM (KSDS)                                     |

### Complete Navigation Path

```
 COSGN00C (CC00)          COMEN01C (CM00)           COTRN00C (CT00)           COTRN01C (CT01)
 +-----------+            +-----------+             +-----------+             +-----------+
 |  Sign-on  |--XCTL-->  | Main Menu |--XCTL-->   | Tran List |--XCTL-->   | Tran View |
 |           |            | (Option 6)|             |           |             |           |
 +-----------+            +-----------+             +-----------+             +-----------+
                               ^                      |   ^                      |
                               |                      |   |                      |
                               +---PF3 (XCTL)---------+   +---PF5 (XCTL)--------+
                                                           +---PF3 (XCTL)--------+
```

**Navigation chain:** CC00 (COSGN00C) Sign-on --> CM00 (COMEN01C) Main Menu --> **CT00 (COTRN00C) Transaction List** --> CT01 (COTRN01C) Transaction View

### Pseudo-Conversational Lifecycle

```
  Terminal User                    CICS Region                     COTRN00C
       |                               |                               |
       |--- initiate CT00 ------------>|                               |
       |                               |--- attach COTRN00C --------->|
       |                               |                               |
       |                               |    [EIBCALEN=0?]             |
       |                               |      YES -> XCTL COSGN00C   |
       |                               |      NO  -> load COMMAREA    |
       |                               |                               |
       |                               |    [PGM-CONTEXT=0?]          |
       |                               |      YES (first entry):      |
       |                               |        Set PGM-CONTEXT=1     |
       |                               |        STARTBR TRANSACT      |
       |                               |        READNEXT x10 records  |
       |                               |        ENDBR TRANSACT        |
       |                               |        SEND MAP COTRN0A      |
       |                               |      NO (re-entry):          |
       |                               |        RECEIVE MAP COTRN0A   |
       |                               |        EVALUATE EIBAID       |
       |                               |                               |
       |<-- screen displayed -----------|<-- RETURN TRANSID(CT00) -----|
       |                               |    COMMAREA(CARDDEMO-COMMAREA)|
       |                               |                               |
       |--- user presses key --------->|--- re-attach COTRN00C ------>|
       |                               |    (re-entry, PGM-CONTEXT=1) |
       |                               |                               |
  [cycle repeats until PF3 or XCTL to COTRN01C]
```

**Lifecycle states:**

| PGM-CONTEXT | Meaning     | Action                                                        |
|-------------|-------------|---------------------------------------------------------------|
| 0           | First entry | Set to 1, browse first page of TRANSACT, SEND MAP, RETURN    |
| 1           | Re-entry    | RECEIVE MAP, evaluate EIBAID, process key, SEND MAP, RETURN  |

---

## Section 2: Programs Involved

| Program    | Type            | Transaction | Function                            | Called By    | Call Method                  |
|------------|-----------------|-------------|-------------------------------------|--------------|------------------------------|
| COSGN00C   | CICS COBOL      | CC00        | Sign-on / Authentication            | (entry point)| Direct CICS invocation       |
| COMEN01C   | CICS COBOL      | CM00        | Main Menu for Regular Users         | COSGN00C     | XCTL                         |
| **COTRN00C** | **CICS COBOL** | **CT00**    | **List Transactions (TRANSACT)**    | COMEN01C     | XCTL (menu option 6)         |
| COTRN01C   | CICS COBOL      | CT01        | View Transaction Detail             | COTRN00C     | XCTL (selection 'S')         |

### Program Relationship Detail

- **COSGN00C** (CC00): Sign-on screen. After successful authentication, XCTLs to COMEN01C (regular users) or COADM01C (admin users).
- **COMEN01C** (CM00): Main Menu. COTRN00C is menu option 6 ("Transaction List"). The menu table in COMEN02Y.cpy maps option 6 to program `COTRN00C` with user-type `U`.
- **COTRN00C** (CT00): **Target program.** Browses TRANSACT VSAM file, displays 10 records per page with pagination. User can select a transaction with 'S' to drill into COTRN01C.
- **COTRN01C** (CT01): Transaction detail view. On PF3, returns to CDEMO-FROM-PROGRAM (i.e., COTRN00C or COMEN01C). On PF5, explicitly XCTLs back to COTRN00C.

---

## Section 3: Copybooks

| Copybook   | Used By                     | Type                  | Purpose                                             |
|------------|-----------------------------|-----------------------|-----------------------------------------------------|
| COCOM01Y   | COTRN00C, COMEN01C, COTRN01C | COMMAREA definition  | Shared communication area between all CardDemo programs |
| COTRN00    | COTRN00C                    | BMS symbolic map      | Auto-generated symbolic map for COTRN0A (input `COTRN0AI` / output `COTRN0AO`) |
| COTTL01Y   | COTRN00C, COMEN01C, COTRN01C | Screen title data    | Application title constants (CCDA-TITLE01, CCDA-TITLE02) |
| CSDAT01Y   | COTRN00C, COMEN01C, COTRN01C | Date/time structure  | WS-DATE-TIME: current date, time, timestamp formatting fields |
| CSMSG01Y   | COTRN00C, COMEN01C, COTRN01C | Common messages      | CCDA-MSG-INVALID-KEY, CCDA-MSG-THANK-YOU             |
| CVTRA05Y   | COTRN00C, COTRN01C          | Record layout         | TRAN-RECORD: Transaction file record layout (350 bytes) |
| DFHAID     | COTRN00C, COMEN01C, COTRN01C | CICS system copybook | AID key constants (DFHENTER, DFHPF3, DFHPF7, DFHPF8, etc.) |
| DFHBMSCA   | COTRN00C, COMEN01C, COTRN01C | CICS system copybook | BMS attribute constants (colors, highlights, field attributes) |
| COMEN02Y   | COMEN01C                    | Menu option table     | Main menu options array — maps option 6 to COTRN00C  |
| CSUSR01Y   | COMEN01C                    | Record layout         | SEC-USER-DATA: User security record layout (80 bytes) |

### Copybook Record Layouts

#### COCOM01Y — CARDDEMO-COMMAREA (See Section 6 for full detail)

```
01 CARDDEMO-COMMAREA
   05 CDEMO-GENERAL-INFO
      10 CDEMO-FROM-TRANID        PIC X(04)
      10 CDEMO-FROM-PROGRAM       PIC X(08)
      10 CDEMO-TO-TRANID          PIC X(04)
      10 CDEMO-TO-PROGRAM         PIC X(08)
      10 CDEMO-USER-ID            PIC X(08)
      10 CDEMO-USER-TYPE          PIC X(01)   88 ADMIN='A', USER='U'
      10 CDEMO-PGM-CONTEXT        PIC 9(01)   88 ENTER=0, REENTER=1
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

#### CVTRA05Y — TRAN-RECORD (350 bytes)

```
01 TRAN-RECORD
   05 TRAN-ID                     PIC X(16)       Offset 1    Key field
   05 TRAN-TYPE-CD                PIC X(02)       Offset 17
   05 TRAN-CAT-CD                 PIC 9(04)       Offset 19
   05 TRAN-SOURCE                 PIC X(10)       Offset 23
   05 TRAN-DESC                   PIC X(100)      Offset 33
   05 TRAN-AMT                    PIC S9(09)V99   Offset 133
   05 TRAN-MERCHANT-ID            PIC 9(09)       Offset 144
   05 TRAN-MERCHANT-NAME          PIC X(50)       Offset 153
   05 TRAN-MERCHANT-CITY          PIC X(50)       Offset 203
   05 TRAN-MERCHANT-ZIP           PIC X(10)       Offset 253
   05 TRAN-CARD-NUM               PIC X(16)       Offset 263
   05 TRAN-ORIG-TS                PIC X(26)       Offset 279
   05 TRAN-PROC-TS                PIC X(26)       Offset 305
   05 FILLER                      PIC X(20)       Offset 331
```

#### COTTL01Y — CCDA-SCREEN-TITLE

```
01 CCDA-SCREEN-TITLE
   05 CCDA-TITLE01    PIC X(40)   '      AWS Mainframe Modernization       '
   05 CCDA-TITLE02    PIC X(40)   '              CardDemo                  '
   05 CCDA-THANK-YOU  PIC X(40)   'Thank you for using CCDA application... '
```

#### CSDAT01Y — WS-DATE-TIME

```
01 WS-DATE-TIME
   05 WS-CURDATE-DATA
      10 WS-CURDATE
         15 WS-CURDATE-YEAR       PIC 9(04)
         15 WS-CURDATE-MONTH      PIC 9(02)
         15 WS-CURDATE-DAY        PIC 9(02)
      10 WS-CURTIME
         15 WS-CURTIME-HOURS      PIC 9(02)
         15 WS-CURTIME-MINUTE     PIC 9(02)
         15 WS-CURTIME-SECOND     PIC 9(02)
         15 WS-CURTIME-MILSEC     PIC 9(02)
   05 WS-CURDATE-MM-DD-YY         (formatted mm/dd/yy)
   05 WS-CURTIME-HH-MM-SS         (formatted hh:mm:ss)
   05 WS-TIMESTAMP                 (formatted yyyy-mm-dd hh:mm:ss.ssssss)
```

#### CSMSG01Y — CCDA-COMMON-MESSAGES

```
01 CCDA-COMMON-MESSAGES
   05 CCDA-MSG-THANK-YOU      PIC X(50)   'Thank you for using CardDemo application...'
   05 CCDA-MSG-INVALID-KEY    PIC X(50)   'Invalid key pressed. Please see below...'
```

---

## Section 4: VSAM File Operations

### File Inventory

| DD Name   | File Type | Key Field | Key Length | Record Copybook | Record Length | Access Mode     |
|-----------|-----------|-----------|------------|-----------------|---------------|-----------------|
| TRANSACT  | VSAM KSDS | TRAN-ID   | 16 bytes   | CVTRA05Y        | 350 bytes     | Browse (sequential) |

### EXEC CICS File I/O Commands in COTRN00C

| #  | Command    | DATASET    | RIDFLD  | Purpose                                      | RESP Handling              |
|----|------------|------------|---------|----------------------------------------------|----------------------------|
| 1  | STARTBR    | TRANSACT   | TRAN-ID | Position browse cursor at given key           | NORMAL, NOTFND, OTHER      |
| 2  | READNEXT   | TRANSACT   | TRAN-ID | Read next record in forward direction         | NORMAL, ENDFILE, OTHER     |
| 3  | READPREV   | TRANSACT   | TRAN-ID | Read previous record in backward direction    | NORMAL, ENDFILE, OTHER     |
| 4  | ENDBR      | TRANSACT   | —       | End browse session                            | (no explicit RESP check)   |

### VSAM File Flow Diagram

```
                    COTRN00C
                       |
                       v
          +------------------------+
          | STARTBR TRANSACT       |
          | RIDFLD(TRAN-ID)        |
          | KEYLENGTH(16)          |
          +------------------------+
                  |
         RESP OK |         NOTFND
                  v            |
          +------------+       v
          |            |   Set TRANSACT-EOF
          |  Forward?  |   Send "top of page"
          |            |   message
          +-----+------+
                |
     +----------+-----------+
     |                       |
     v                       v
 READNEXT x10            READPREV x10
 (page forward)          (page backward)
     |                       |
     v                       v
 Populate rows 1-10      Populate rows 10-1
 in screen fields        in screen fields
     |                       |
     v                       v
 +-- peek READNEXT --+   (check if more
 |   to check for    |    pages backward)
 |   next page        |
 +--------------------+
     |
     v
 ENDBR TRANSACT
     |
     v
 SEND MAP COTRN0A
 RETURN TRANSID(CT00)
```

### Error Handling for File Operations

| Command  | RESP Code      | Action                                                     |
|----------|----------------|------------------------------------------------------------|
| STARTBR  | NORMAL         | Continue processing                                        |
| STARTBR  | NOTFND         | Set TRANSACT-EOF, display "You are at the top of the page" |
| STARTBR  | OTHER          | Set ERR-FLG, display "Unable to lookup transaction..."     |
| READNEXT | NORMAL         | Continue — populate screen row                             |
| READNEXT | ENDFILE        | Set TRANSACT-EOF, display "reached bottom of page"         |
| READNEXT | OTHER          | Set ERR-FLG, display "Unable to lookup transaction..."     |
| READPREV | NORMAL         | Continue — populate screen row                             |
| READPREV | ENDFILE        | Set TRANSACT-EOF, display "reached top of page"            |
| READPREV | OTHER          | Set ERR-FLG, display "Unable to lookup transaction..."     |

---

## Section 5: BMS Screen Map

### Mapset and Map Names

| Mapset    | Map      | Source File    | Size     |
|-----------|----------|----------------|----------|
| COTRN00   | COTRN0A  | app/bms/COTRN00.bms | 24 x 80 |

### ASCII Screen Layout

```
Row Col  Content
--- ---  -----------------------------------------------------------------------
 1   1   Tran: CT00    AWS Mainframe Modernization          Date: mm/dd/yy
 2   1   Prog: COTRN00C           CardDemo                  Time: hh:mm:ss
 3
 4  30                    List Transactions              Page: 00000001
 5
 6   5   Search Tran ID: [________________]
 7
 8   2   Sel  Transaction ID   Date      Description                Amount
 9   2   ---  ----------------  --------  --------------------------  ------------
10   3   [_]  XXXXXXXXXXXXXXXX  mm/dd/yy  XXXXXXXXXXXXXXXXXXXXXXXXXX  +99999999.99
11   3   [_]  XXXXXXXXXXXXXXXX  mm/dd/yy  XXXXXXXXXXXXXXXXXXXXXXXXXX  +99999999.99
12   3   [_]  XXXXXXXXXXXXXXXX  mm/dd/yy  XXXXXXXXXXXXXXXXXXXXXXXXXX  +99999999.99
13   3   [_]  XXXXXXXXXXXXXXXX  mm/dd/yy  XXXXXXXXXXXXXXXXXXXXXXXXXX  +99999999.99
14   3   [_]  XXXXXXXXXXXXXXXX  mm/dd/yy  XXXXXXXXXXXXXXXXXXXXXXXXXX  +99999999.99
15   3   [_]  XXXXXXXXXXXXXXXX  mm/dd/yy  XXXXXXXXXXXXXXXXXXXXXXXXXX  +99999999.99
16   3   [_]  XXXXXXXXXXXXXXXX  mm/dd/yy  XXXXXXXXXXXXXXXXXXXXXXXXXX  +99999999.99
17   3   [_]  XXXXXXXXXXXXXXXX  mm/dd/yy  XXXXXXXXXXXXXXXXXXXXXXXXXX  +99999999.99
18   3   [_]  XXXXXXXXXXXXXXXX  mm/dd/yy  XXXXXXXXXXXXXXXXXXXXXXXXXX  +99999999.99
19   3   [_]  XXXXXXXXXXXXXXXX  mm/dd/yy  XXXXXXXXXXXXXXXXXXXXXXXXXX  +99999999.99
20
21  12   Type 'S' to View Transaction details from the list
22
23   1   [error message area - 78 chars, red, bright]
24   1   ENTER=Continue  F3=Back  F7=Backward  F8=Forward
```

### Field Inventory

| Field Name | Row | Col | Length | Attributes       | Color    | Purpose                          |
|------------|-----|-----|--------|------------------|----------|----------------------------------|
| TRNNAME    |  1  |  7  |   4    | ASKIP,FSET       | BLUE     | Transaction ID display           |
| TITLE01    |  1  | 21  |  40    | ASKIP,FSET       | YELLOW   | Application title line 1         |
| CURDATE    |  1  | 71  |   8    | ASKIP,FSET       | BLUE     | Current date (mm/dd/yy)          |
| PGMNAME    |  2  |  7  |   8    | ASKIP,FSET       | BLUE     | Program name display             |
| TITLE02    |  2  | 21  |  40    | ASKIP,FSET       | YELLOW   | Application title line 2         |
| CURTIME    |  2  | 71  |   8    | ASKIP,FSET       | BLUE     | Current time (hh:mm:ss)          |
| PAGENUM    |  4  | 71  |   8    | ASKIP,FSET       | BLUE     | Current page number              |
| TRNIDIN    |  6  | 21  |  16    | **UNPROT**,FSET  | GREEN    | **Input:** Search Transaction ID |
| SEL0001-10 | 10-19| 3  |   1    | **UNPROT**,FSET  | GREEN    | **Input:** Selection field ('S') |
| TRNID01-10 | 10-19| 8  |  16    | ASKIP,FSET       | BLUE     | Transaction ID display           |
| TDATE01-10 | 10-19| 27 |   8    | ASKIP,FSET       | BLUE     | Transaction date display         |
| TDESC01-10 | 10-19| 38 |  26    | ASKIP,FSET       | BLUE     | Transaction description          |
| TAMT001-10 | 10-19| 67 |  12    | ASKIP,FSET       | BLUE     | Transaction amount               |
| ERRMSG     | 23  |  1  |  78    | ASKIP,BRT,FSET   | RED      | Error/info message area          |

### Function Key Assignments

| Key    | EIBAID Constant | Action                                       |
|--------|-----------------|----------------------------------------------|
| ENTER  | DFHENTER        | Process selection or search; page forward     |
| PF3    | DFHPF3          | Return to Main Menu (XCTL to COMEN01C)       |
| PF7    | DFHPF7          | Page backward (previous 10 transactions)     |
| PF8    | DFHPF8          | Page forward (next 10 transactions)          |
| OTHER  | (any other key) | Display "Invalid key pressed" error message  |

---

## Section 6: COMMAREA Structure

### Full Field-Level Documentation

The COMMAREA is defined by copybook `COCOM01Y.cpy` plus a program-specific extension area `CDEMO-CT00-INFO` defined inline in COTRN00C.

```
01 CARDDEMO-COMMAREA
|
+-- 05 CDEMO-GENERAL-INFO
|   +-- 10 CDEMO-FROM-TRANID        PIC X(04)
|   +-- 10 CDEMO-FROM-PROGRAM       PIC X(08)
|   +-- 10 CDEMO-TO-TRANID          PIC X(04)
|   +-- 10 CDEMO-TO-PROGRAM         PIC X(08)
|   +-- 10 CDEMO-USER-ID            PIC X(08)
|   +-- 10 CDEMO-USER-TYPE          PIC X(01)  [A=Admin, U=User]
|   +-- 10 CDEMO-PGM-CONTEXT        PIC 9(01)  [0=Enter, 1=Reenter]
|
+-- 05 CDEMO-CUSTOMER-INFO
|   +-- 10 CDEMO-CUST-ID            PIC 9(09)
|   +-- 10 CDEMO-CUST-FNAME         PIC X(25)
|   +-- 10 CDEMO-CUST-MNAME         PIC X(25)
|   +-- 10 CDEMO-CUST-LNAME         PIC X(25)
|
+-- 05 CDEMO-ACCOUNT-INFO
|   +-- 10 CDEMO-ACCT-ID            PIC 9(11)
|   +-- 10 CDEMO-ACCT-STATUS        PIC X(01)
|
+-- 05 CDEMO-CARD-INFO
|   +-- 10 CDEMO-CARD-NUM           PIC 9(16)
|
+-- 05 CDEMO-MORE-INFO
|   +-- 10 CDEMO-LAST-MAP           PIC X(7)
|   +-- 10 CDEMO-LAST-MAPSET        PIC X(7)
|
+-- 05 CDEMO-CT00-INFO  (program-specific, defined inline in COTRN00C)
    +-- 10 CDEMO-CT00-TRNID-FIRST   PIC X(16)   First tran ID on page
    +-- 10 CDEMO-CT00-TRNID-LAST    PIC X(16)   Last tran ID on page
    +-- 10 CDEMO-CT00-PAGE-NUM      PIC 9(08)   Current page number
    +-- 10 CDEMO-CT00-NEXT-PAGE-FLG PIC X(01)   'Y'=more pages, 'N'=last page
    +-- 10 CDEMO-CT00-TRN-SEL-FLG   PIC X(01)   Selection flag ('S')
    +-- 10 CDEMO-CT00-TRN-SELECTED  PIC X(16)   Selected transaction ID
```

### COMMAREA Field Read/Write by Program

| Field                    | COSGN00C | COMEN01C | COTRN00C      | COTRN01C       |
|--------------------------|----------|----------|---------------|----------------|
| CDEMO-FROM-TRANID        | W        | W        | W             | W              |
| CDEMO-FROM-PROGRAM       | W        | W        | W             | W              |
| CDEMO-TO-TRANID          |          |          |               |                |
| CDEMO-TO-PROGRAM         | W        | W        | W             | W              |
| CDEMO-USER-ID            | W        |          | (pass-through)| (pass-through) |
| CDEMO-USER-TYPE          | W        | R        | (pass-through)| (pass-through) |
| CDEMO-PGM-CONTEXT        | W        | R/W      | R/W           | R/W            |
| CDEMO-CT00-TRNID-FIRST   |          |          | W             |                |
| CDEMO-CT00-TRNID-LAST    |          |          | W             |                |
| CDEMO-CT00-PAGE-NUM      |          |          | R/W           |                |
| CDEMO-CT00-NEXT-PAGE-FLG |          |          | R/W           |                |
| CDEMO-CT00-TRN-SEL-FLG   |          |          | W             | (via CT01-INFO)|
| CDEMO-CT00-TRN-SELECTED  |          |          | W             | (via CT01-INFO)|

### Inter-Program COMMAREA Flow

```
COMEN01C (CM00)                     COTRN00C (CT00)                     COTRN01C (CT01)
+-------------------+               +-------------------+               +-------------------+
| Sets:             |               | Reads:            |               | Reads:            |
|  FROM-TRANID=CM00 |  --XCTL-->   |  PGM-CONTEXT      |  --XCTL-->   |  FROM-PROGRAM     |
|  FROM-PROGRAM=    |               |  CT00-TRNID-FIRST |               |  CT01-TRN-SELECTED|
|    COMEN01C       |               |  CT00-TRNID-LAST  |               |                   |
|  PGM-CONTEXT=0    |               |  CT00-PAGE-NUM    |               | Sets:             |
|                   |               |  CT00-NEXT-PAGE-FLG|              |  FROM-TRANID=CT01 |
+-------------------+               |                   |               |  FROM-PROGRAM=    |
                                    | Sets:             |               |    COTRN01C       |
                                    |  FROM-TRANID=CT00 |               |  TO-PROGRAM=      |
                                    |  FROM-PROGRAM=    |               |    COTRN00C (PF5) |
                                    |    COTRN00C       |               |  PGM-CONTEXT=0    |
                                    |  TO-PROGRAM=      |               +-------------------+
                                    |    COTRN01C       |
                                    |  PGM-CONTEXT=0    |
                                    |  CT00-TRN-SEL-FLG |
                                    |  CT00-TRN-SELECTED|
                                    |  CT00-TRNID-FIRST |
                                    |  CT00-TRNID-LAST  |
                                    |  CT00-PAGE-NUM    |
                                    |  CT00-NEXT-PAGE-FLG|
                                    +-------------------+
```

---

## Section 7: Business Logic Flow

### Step-by-Step Narrative

#### 1. Entry and Initialization (MAIN-PARA)

1. Reset flags: `ERR-FLG-OFF`, `TRANSACT-NOT-EOF`, `NEXT-PAGE-NO`, `SEND-ERASE-YES`
2. Clear message areas
3. Set cursor position to Search Tran ID field (`TRNIDINL = -1`)
4. **Check EIBCALEN:**
   - If `EIBCALEN = 0` (no COMMAREA — direct invocation): XCTL to sign-on screen COSGN00C
   - If `EIBCALEN > 0`: Load COMMAREA into `CARDDEMO-COMMAREA`

#### 2. First Entry vs Re-entry

- **First entry** (`CDEMO-PGM-CONTEXT = 0`):
  1. Set `CDEMO-PGM-REENTER = TRUE` (PGM-CONTEXT = 1)
  2. Initialize output map `COTRN0AO` to LOW-VALUES
  3. Execute PROCESS-ENTER-KEY (initial data load)
  4. Send screen with ERASE

- **Re-entry** (`CDEMO-PGM-CONTEXT = 1`):
  1. RECEIVE MAP from terminal
  2. Evaluate EIBAID (which key the user pressed)

#### 3. ENTER Key Processing (PROCESS-ENTER-KEY)

1. **Selection check:** Scan all 10 selection fields (SEL0001I through SEL0010I). If any is non-blank, capture:
   - The selection flag value into `CDEMO-CT00-TRN-SEL-FLG`
   - The corresponding transaction ID into `CDEMO-CT00-TRN-SELECTED`

2. **Selection action:** If both flag and selected ID are non-blank:
   - If flag = 'S' or 's': 
     - Set `CDEMO-TO-PROGRAM = 'COTRN01C'`
     - Set `CDEMO-FROM-TRANID = CT00`, `CDEMO-FROM-PROGRAM = COTRN00C`
     - Set `CDEMO-PGM-CONTEXT = 0`
     - **XCTL to COTRN01C** with COMMAREA (permanent transfer — does not return)
   - If flag = anything else: Display "Invalid selection. Valid value is S"

3. **Search Tran ID validation:** 
   - If Search Tran ID field is blank: set TRAN-ID to LOW-VALUES (start from beginning)
   - If Search Tran ID is numeric: use as browse starting key
   - If Search Tran ID is non-numeric: set error flag, display "Tran ID must be Numeric ..."

4. **Data retrieval:** Reset page counter to 0, perform PROCESS-PAGE-FORWARD

#### 4. Page Forward (PROCESS-PAGE-FORWARD)

1. STARTBR on TRANSACT file at TRAN-ID position
2. If not on first entry via ENTER/PF7/PF3: do one READNEXT to skip past the current position
3. Initialize all 10 display rows to spaces
4. Loop READNEXT up to 10 records, populating screen fields for each row
5. After 10 records, do one more READNEXT to peek ahead:
   - If successful: set `NEXT-PAGE-YES` (more data available)
   - If ENDFILE: set `NEXT-PAGE-NO` (last page)
6. Increment page counter
7. ENDBR to close the browse
8. Set PAGENUM display field
9. SEND MAP

#### 5. Page Backward (PROCESS-PF7-KEY / PROCESS-PAGE-BACKWARD)

1. Check if already at page 1 — if so, display "already at top" message
2. Otherwise, STARTBR at `CDEMO-CT00-TRNID-FIRST` (first ID on current page)
3. If not entering from ENTER/PF8: do one READPREV to skip past current position
4. Initialize all 10 display rows to spaces
5. Loop READPREV up to 10 records, populating rows **10 down to 1** (reverse order so display is ascending)
6. Adjust page counter (subtract 1 if > 1)
7. ENDBR to close the browse
8. Set PAGENUM display field
9. SEND MAP

#### 6. Page Forward via PF8 (PROCESS-PF8-KEY)

1. If `CDEMO-CT00-TRNID-LAST` is blank: set TRAN-ID to HIGH-VALUES
2. Otherwise, use the last transaction ID from current page as the starting key
3. If `NEXT-PAGE-YES`: perform PROCESS-PAGE-FORWARD
4. If not: display "already at bottom" message

#### 7. Screen Population (POPULATE-TRAN-DATA)

For each TRAN-RECORD read from the browse:
1. Format `TRAN-AMT` into `WS-TRAN-AMT` (PIC +99999999.99)
2. Extract date from `TRAN-ORIG-TS` timestamp into `WS-TRAN-DATE` (MM/DD/YY format via CSDAT01Y fields)
3. Based on `WS-IDX` (1-10), move TRAN-ID, WS-TRAN-DATE, TRAN-DESC, and WS-TRAN-AMT to the corresponding BMS output fields
4. For row 1: also save TRAN-ID as `CDEMO-CT00-TRNID-FIRST`
5. For row 10: also save TRAN-ID as `CDEMO-CT00-TRNID-LAST`

#### 8. Header Population (POPULATE-HEADER-INFO)

1. Get current date/time via `FUNCTION CURRENT-DATE`
2. Move application titles (CCDA-TITLE01, CCDA-TITLE02) to screen
3. Move transaction ID (CT00) and program name (COTRN00C) to screen
4. Format date as MM/DD/YY and time as HH:MM:SS

#### 9. Return Flow

After every screen SEND:
```cobol
EXEC CICS RETURN
    TRANSID (WS-TRANID)        -- 'CT00'
    COMMAREA (CARDDEMO-COMMAREA)
END-EXEC
```
This suspends the program. When the user presses a key, CICS re-attaches COTRN00C with the saved COMMAREA.

### Validation Summary

| Validation                | Error Message                               | Recovery                     |
|---------------------------|---------------------------------------------|------------------------------|
| Search Tran ID non-numeric| "Tran ID must be Numeric ..."              | Redisplay screen, cursor on field |
| Invalid selection value   | "Invalid selection. Valid value is S"       | Redisplay screen              |
| Invalid AID key           | "Invalid key pressed. Please see below..."  | Redisplay screen              |
| STARTBR NOTFND            | "You are at the top of the page..."        | Set EOF, redisplay            |
| READNEXT ENDFILE          | "You have reached the bottom of the page..." | Set EOF, redisplay          |
| READPREV ENDFILE          | "You have reached the top of the page..."  | Set EOF, redisplay            |
| STARTBR/READ OTHER        | "Unable to lookup transaction..."          | Set error flag, redisplay     |
| Page backward at page 1   | "You are already at the top of the page..." | Redisplay without erase      |
| Page forward at last page | "You are already at the bottom of the page..."| Redisplay without erase    |

---

## Section 8: Complete File Inventory

### COBOL Source Files

| File                 | Path                  | Role in CT00 Flow                    |
|----------------------|-----------------------|--------------------------------------|
| COTRN00C.cbl         | app/cbl/COTRN00C.cbl  | **Target program** — Transaction List |
| COTRN01C.cbl         | app/cbl/COTRN01C.cbl  | Downstream — Transaction View         |
| COMEN01C.cbl         | app/cbl/COMEN01C.cbl  | Upstream — Main Menu                  |
| COSGN00C.cbl         | app/cbl/COSGN00C.cbl  | Upstream — Sign-on                    |

### Copybook Files

| File                 | Path                  | Classification                       |
|----------------------|-----------------------|--------------------------------------|
| COCOM01Y.cpy         | app/cpy/COCOM01Y.cpy  | COMMAREA definition                  |
| COTTL01Y.cpy         | app/cpy/COTTL01Y.cpy  | Screen title constants               |
| CSDAT01Y.cpy         | app/cpy/CSDAT01Y.cpy  | Date/time working storage            |
| CSMSG01Y.cpy         | app/cpy/CSMSG01Y.cpy  | Common message constants             |
| CVTRA05Y.cpy         | app/cpy/CVTRA05Y.cpy  | TRANSACT record layout               |
| COMEN02Y.cpy         | app/cpy/COMEN02Y.cpy  | Menu options table (upstream)        |
| CSUSR01Y.cpy         | app/cpy/CSUSR01Y.cpy  | User security record (upstream)      |
| DFHAID               | (system)               | CICS AID key constants               |
| DFHBMSCA             | (system)               | CICS BMS attribute constants         |

### BMS Map Files

| File                 | Path                  | Mapset   | Map      |
|----------------------|-----------------------|----------|----------|
| COTRN00.bms          | app/bms/COTRN00.bms   | COTRN00  | COTRN0A  |

### BMS Symbolic Map Copybooks (Auto-Generated)

| Copybook Name | Included By  | Source BMS     | Structures Generated        |
|---------------|--------------|----------------|-----------------------------|
| COTRN00       | COTRN00C     | COTRN00.bms    | COTRN0AI (input), COTRN0AO (output) |

### VSAM Data Files

| DD Name    | Description                        | Key Field | Record Copybook |
|------------|------------------------------------|-----------|-----------------|
| TRANSACT   | Transaction master file            | TRAN-ID   | CVTRA05Y        |

### CSD Definitions

| Resource Type | Name      | Group      | Source File              |
|---------------|-----------|------------|--------------------------|
| PROGRAM       | COTRN00C  | CARDDEMO   | app/csd/CARDDEMO.CSD    |
| MAPSET        | COTRN00   | CARDDEMO   | app/csd/CARDDEMO.CSD    |
| TRANSACTION   | CT00      | CARDDEMO   | app/csd/CARDDEMO.CSD    |

---

## Section 9: Transaction Record Layout

The TRANSACT file uses the TRAN-RECORD layout defined in CVTRA05Y.cpy:

```
Offset  Length  Field Name          PIC Clause       Purpose
------  ------  ------------------  ---------------  ---------------------------------
0       16      TRAN-ID             X(16)            Transaction ID (primary key)
16       2      TRAN-TYPE-CD        X(02)            Transaction type code
18       4      TRAN-CAT-CD         9(04)            Transaction category code
22      10      TRAN-SOURCE         X(10)            Transaction source
32     100      TRAN-DESC           X(100)           Transaction description
132     11      TRAN-AMT            S9(09)V99        Transaction amount (signed decimal)
143      9      TRAN-MERCHANT-ID    9(09)            Merchant identifier
152     50      TRAN-MERCHANT-NAME  X(50)            Merchant name
202     50      TRAN-MERCHANT-CITY  X(50)            Merchant city
252     10      TRAN-MERCHANT-ZIP   X(10)            Merchant ZIP code
262     16      TRAN-CARD-NUM       X(16)            Card number
278     26      TRAN-ORIG-TS        X(26)            Original timestamp
304     26      TRAN-PROC-TS        X(26)            Processing timestamp
330     20      FILLER              X(20)            Reserved
------  ------
Total: 350 bytes
```

**Fields displayed on the list screen:**

| Record Field  | Screen Field     | Format on Screen |
|---------------|------------------|------------------|
| TRAN-ID       | TRNIDnnI         | As-is (16 chars) |
| TRAN-ORIG-TS  | TDATEnnI         | MM/DD/YY (extracted from timestamp) |
| TRAN-DESC     | TDESCnnI         | First 26 chars   |
| TRAN-AMT      | TAMTnnnI         | +99999999.99     |

---

## Section 10: Observations

### What This Flow Demonstrates Well

1. **Standard CICS pseudo-conversational pattern:** Clean separation of first-entry vs re-entry using PGM-CONTEXT flag. RETURN TRANSID preserves state across user interactions.

2. **Browse pagination model:** Forward and backward browsing using STARTBR/READNEXT/READPREV/ENDBR with proper page boundary tracking (`TRNID-FIRST`, `TRNID-LAST`, `PAGE-NUM`, `NEXT-PAGE-FLG`).

3. **Clean COMMAREA state management:** Program-specific extension area (`CDEMO-CT00-INFO`) keeps pagination state between pseudo-conversational iterations without global variables.

4. **Selection-to-detail drill-down:** Standard list-to-detail pattern using XCTL — user selects a row with 'S', program passes the selected transaction ID via COMMAREA, and XCTLs to COTRN01C.

5. **Comprehensive error handling:** Every VSAM I/O operation checks RESP/RESP2 with meaningful user messages for NOTFND, ENDFILE, and unexpected errors.

6. **Shared copybook architecture:** Common copybooks (COCOM01Y, COTTL01Y, CSDAT01Y, CSMSG01Y) promote consistency across all CardDemo programs.

### Gaps / Migration Considerations

1. **No direct DB access:** All data is VSAM-based. Migration to a relational database would require replacing STARTBR/READNEXT/READPREV with SQL cursors and implementing OFFSET/LIMIT or keyset pagination.

2. **Hardcoded screen dimensions:** 10 rows per page is hardcoded. A modern UI would need dynamic row counts based on viewport size.

3. **EVALUATE WS-IDX pattern:** Rows 1-10 are populated via a large EVALUATE block instead of an array. Migration should use a loop with indexed field access.

4. **Date formatting logic:** The timestamp-to-date conversion uses manual substring operations on `TRAN-ORIG-TS`. A modern implementation would use date parsing libraries.

5. **No authorization checks in COTRN00C:** The program does not check `CDEMO-USER-TYPE`. Authorization is handled upstream at the menu level (COMEN01C checks `CDEMO-MENU-OPT-USRTYPE`).

6. **BMS map dependency:** The symbolic map (COTRN00) is generated from the BMS source. Migration eliminates BMS entirely in favor of HTML/CSS templates or API response models.

7. **Transaction ID is a 16-character string key:** In the VSAM model, browse ordering depends on the key's collation. Migration to SQL requires explicit ORDER BY clauses.

---

## Section 11: Comparison — CICS/VSAM vs Modern Target

| Aspect                  | CICS/VSAM (Current)                        | Modern Target (e.g., Java/REST/SQL)          |
|-------------------------|--------------------------------------------|----------------------------------------------|
| **UI Rendering**        | BMS MAP SEND/RECEIVE (3270 terminal)       | HTML/React + REST API                        |
| **State Management**    | COMMAREA passed via RETURN TRANSID          | HTTP session / JWT token / URL state         |
| **Pagination**          | STARTBR/READNEXT/READPREV with key tracking| SQL OFFSET/LIMIT or keyset pagination        |
| **Data Access**         | VSAM KSDS (TRANSACT file)                  | RDBMS table with indexes                     |
| **Navigation**          | XCTL / LINK between COBOL programs          | URL routing / SPA navigation                 |
| **Error Handling**      | RESP/RESP2 codes per CICS command           | Try/catch + HTTP status codes                |
| **Screen Layout**       | Fixed 24x80 character grid                  | Responsive layout, variable row count        |
| **Selection Model**     | Type 'S' in row selection field             | Click/tap row, or checkbox selection         |
| **Program Count**       | 4 programs in flow (sign-on to detail)      | Likely 2-3 API endpoints + 2-3 UI components|
| **Record Format**       | Fixed 350-byte COBOL record (CVTRA05Y)     | JSON/DTO with typed fields                   |
| **Deployment**          | CSD-defined programs/transactions/mapsets   | Container/serverless deployment              |

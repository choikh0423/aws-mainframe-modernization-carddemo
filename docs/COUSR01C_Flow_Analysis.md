# CU01 Transaction Flow Analysis — COUSR01C (User Add / Security Admin)

## Section 1: Flow Overview

| Attribute           | Value                                      |
|---------------------|--------------------------------------------|
| **Transaction ID**  | `CU01`                                     |
| **Entry Program**   | `COUSR01C`                                 |
| **Function**        | Add a new Regular or Admin user to the USRSEC VSAM file |
| **Transaction Type**| CICS Online (pseudo-conversational)        |
| **Data Access**     | VSAM KSDS                                  |
| **CSD Group**       | `CARDDEMO`                                 |

### Complete Navigation Path

```
Terminal
  |
  |  CC00 (Tran ID)
  v
+------------------+     XCTL (admin)     +------------------+     XCTL (option 2)     +------------------+
|    COSGN00C      | ------------------->  |    COADM01C      | ----------------------> |    COUSR01C      |
|  Sign-On Screen  |                       |  Admin Menu      |                         |  Add User Screen |
|  Tran: CC00      |                       |  Tran: CA00      |                         |  Tran: CU01      |
+------------------+                       +------------------+                         +------------------+
        |                                          ^                                           |
        | XCTL (regular user)                      | XCTL (PF3)                                | RETURN TRANSID
        v                                          |                                           | (CU01) pseudo-conv
+------------------+                               +-------------------------------------------+
|    COMEN01C      |                                         PF3 = Back to Admin Menu
|  Main Menu       |
|  Tran: CM00      |
+------------------+
```

### Pseudo-Conversational Lifecycle

```
  CICS START (CU01)
        |
        v
  [EIBCALEN = 0?] ---YES---> XCTL to COSGN00C (no COMMAREA = not authorized)
        |
       NO
        v
  [CDEMO-PGM-REENTER?] ---NO (first entry)---> Set REENTER flag
        |                                            |
        |                                     Initialize screen (LOW-VALUES)
        |                                            |
        |                                     SEND MAP COUSR1A (empty form)
        |                                            |
        |                                     RETURN TRANSID('CU01')
        |                                            |
       YES (re-entry)                         <------+  (user types data, presses key)
        |
  RECEIVE MAP COUSR1A
        |
  EVALUATE EIBAID
        |
        +--- DFHENTER ---> PROCESS-ENTER-KEY (validate + write)
        |
        +--- DFHPF3   ---> XCTL to COADM01C (back to admin menu)
        |
        +--- DFHPF4   ---> CLEAR-CURRENT-SCREEN (reset all fields)
        |
        +--- OTHER     ---> Display "Invalid key" message
        |
        v
  RETURN TRANSID('CU01') COMMAREA(CARDDEMO-COMMAREA)
```

---

## Section 2: Programs Involved

| # | Program    | Type             | Function                        | Caller     | Call Method       |
|---|------------|------------------|---------------------------------|------------|-------------------|
| 1 | COSGN00C   | CICS COBOL       | Sign-on / Authentication        | (entry)    | Transaction CC00  |
| 2 | COADM01C   | CICS COBOL       | Admin Menu                      | COSGN00C   | XCTL              |
| 3 | COUSR01C   | CICS COBOL       | Add User (target program)       | COADM01C   | XCTL              |

**Notes:**
- COUSR01C contains **no CALL statements** and **no LINK commands**. It is a leaf-level program.
- There are no sub-programs or Language Environment intrinsics invoked by COUSR01C.

---

## Section 3: Copybooks

### 3.1 Copybooks Referenced by COUSR01C

| # | Copybook   | Used By    | Classification              | Purpose                                        |
|---|------------|------------|-----------------------------|-------------------------------------------------|
| 1 | COCOM01Y   | COUSR01C, COADM01C, COSGN00C | COMMAREA definition | Communication area shared by all programs       |
| 2 | COUSR01    | COUSR01C   | BMS symbolic map (generated)| Input/output field structures for COUSR1A map   |
| 3 | COTTL01Y   | COUSR01C, COADM01C, COSGN00C | Screen titles      | Application title constants                     |
| 4 | CSDAT01Y   | COUSR01C, COADM01C, COSGN00C | Date/time structure | Current date/time working storage fields        |
| 5 | CSMSG01Y   | COUSR01C, COADM01C, COSGN00C | Common messages     | Shared message constants (invalid key, etc.)    |
| 6 | CSUSR01Y   | COUSR01C, COADM01C, COSGN00C | Record layout       | SEC-USER-DATA record for USRSEC VSAM file       |
| 7 | DFHAID     | COUSR01C   | CICS system copybook        | AID key constants (DFHENTER, DFHPF3, etc.)     |
| 8 | DFHBMSCA   | COUSR01C   | CICS system copybook        | BMS attribute constants (DFHGREEN, etc.)        |

### 3.2 Additional Copybooks (Upstream Programs)

| # | Copybook   | Used By    | Classification              | Purpose                                        |
|---|------------|------------|-----------------------------|-------------------------------------------------|
| 9 | COADM02Y   | COADM01C   | Menu options table           | Admin menu option names and target program array|
|10 | COADM01    | COADM01C   | BMS symbolic map (generated) | Input/output fields for COADM1A admin menu map  |
|11 | COSGN00    | COSGN00C   | BMS symbolic map (generated) | Input/output fields for COSGN0A sign-on map     |

### 3.3 Record Layouts

#### CSUSR01Y — SEC-USER-DATA (USRSEC VSAM Record)

```
01 SEC-USER-DATA.                          Total: 80 bytes
   05 SEC-USR-ID         PIC X(08).        Offset 1-8    User ID (primary key)
   05 SEC-USR-FNAME      PIC X(20).        Offset 9-28   First name
   05 SEC-USR-LNAME      PIC X(20).        Offset 29-48  Last name
   05 SEC-USR-PWD        PIC X(08).        Offset 49-56  Password
   05 SEC-USR-TYPE       PIC X(01).        Offset 57     User type (A=Admin, U=User)
   05 SEC-USR-FILLER     PIC X(23).        Offset 58-80  Reserved filler
```

#### COTTL01Y — Screen Titles

```
01 CCDA-SCREEN-TITLE.
   05 CCDA-TITLE01       PIC X(40).        '      AWS Mainframe Modernization       '
   05 CCDA-TITLE02       PIC X(40).        '              CardDemo                  '
   05 CCDA-THANK-YOU     PIC X(40).        'Thank you for using CCDA application... '
```

#### CSDAT01Y — Date/Time Working Storage

```
01 WS-DATE-TIME.
   05 WS-CURDATE-DATA.
      10 WS-CURDATE.
         15 WS-CURDATE-YEAR    PIC 9(04).
         15 WS-CURDATE-MONTH   PIC 9(02).
         15 WS-CURDATE-DAY     PIC 9(02).
      10 WS-CURDATE-N REDEFINES WS-CURDATE PIC 9(08).
      10 WS-CURTIME.
         15 WS-CURTIME-HOURS   PIC 9(02).
         15 WS-CURTIME-MINUTE  PIC 9(02).
         15 WS-CURTIME-SECOND  PIC 9(02).
         15 WS-CURTIME-MILSEC  PIC 9(02).
      10 WS-CURTIME-N REDEFINES WS-CURTIME PIC 9(08).
   05 WS-CURDATE-MM-DD-YY.
      10 WS-CURDATE-MM         PIC 9(02).
      10 FILLER                PIC X(01)  VALUE '/'.
      10 WS-CURDATE-DD         PIC 9(02).
      10 FILLER                PIC X(01)  VALUE '/'.
      10 WS-CURDATE-YY         PIC 9(02).
   05 WS-CURTIME-HH-MM-SS.
      10 WS-CURTIME-HH         PIC 9(02).
      10 FILLER                PIC X(01)  VALUE ':'.
      10 WS-CURTIME-MM         PIC 9(02).
      10 FILLER                PIC X(01)  VALUE ':'.
      10 WS-CURTIME-SS         PIC 9(02).
   05 WS-TIMESTAMP.
      10 WS-TIMESTAMP-DT-YYYY  PIC 9(04).
      10 FILLER                PIC X(01)  VALUE '-'.
      10 WS-TIMESTAMP-DT-MM    PIC 9(02).
      10 FILLER                PIC X(01)  VALUE '-'.
      10 WS-TIMESTAMP-DT-DD    PIC 9(02).
      10 FILLER                PIC X(01)  VALUE ' '.
      10 WS-TIMESTAMP-TM-HH    PIC 9(02).
      10 FILLER                PIC X(01)  VALUE ':'.
      10 WS-TIMESTAMP-TM-MM    PIC 9(02).
      10 FILLER                PIC X(01)  VALUE ':'.
      10 WS-TIMESTAMP-TM-SS    PIC 9(02).
      10 FILLER                PIC X(01)  VALUE '.'.
      10 WS-TIMESTAMP-TM-MS6   PIC 9(06).
```

#### CSMSG01Y — Common Messages

```
01 CCDA-COMMON-MESSAGES.
   05 CCDA-MSG-THANK-YOU    PIC X(50).  'Thank you for using CardDemo application...'
   05 CCDA-MSG-INVALID-KEY  PIC X(50).  'Invalid key pressed. Please see below...'
```

---

## Section 4: VSAM File Operations

### 4.1 VSAM Files Accessed by COUSR01C

| DD Name  | VSAM Type | Key Field     | Record Copybook | Record Length | Operation(s) |
|----------|-----------|---------------|-----------------|---------------|--------------|
| USRSEC   | KSDS      | SEC-USR-ID (8 bytes) | CSUSR01Y (SEC-USER-DATA) | 80 bytes | WRITE |

### 4.2 CSD File Definition (USRSEC)

```
DEFINE FILE(USRSEC) GROUP(CARDDEMO)
       DSNAME(AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS)
       RECORDFORMAT(V) ADD(YES) BROWSE(YES) DELETE(YES) READ(YES) UPDATE(YES)
```

### 4.3 Detailed I/O Operations

#### WRITE — USRSEC (WRITE-USER-SEC-FILE paragraph, line 240)

```cobol
EXEC CICS WRITE
     DATASET   (WS-USRSEC-FILE)       Value: 'USRSEC  '
     FROM      (SEC-USER-DATA)         Record: CSUSR01Y copybook
     LENGTH    (LENGTH OF SEC-USER-DATA)
     RIDFLD    (SEC-USR-ID)            Key: 8-byte User ID
     KEYLENGTH (LENGTH OF SEC-USR-ID)
     RESP      (WS-RESP-CD)
     RESP2     (WS-REAS-CD)
END-EXEC.
```

**RESP Handling:**

| RESP Code         | Action                                                  |
|--------------------|---------------------------------------------------------|
| DFHRESP(NORMAL)    | Success: clear fields, display green "User xxx has been added ..." |
| DFHRESP(DUPKEY)    | Duplicate: display "User ID already exist..."           |
| DFHRESP(DUPREC)    | Duplicate: display "User ID already exist..."           |
| OTHER              | Error: display "Unable to Add User..."                  |

### 4.4 VSAM File Flow Diagram

```
                         COUSR01C
                            |
                            |  EXEC CICS WRITE
                            |  DATASET('USRSEC')
                            |  FROM(SEC-USER-DATA)
                            |  RIDFLD(SEC-USR-ID)
                            v
                    +----------------+
                    |    USRSEC      |
                    |  VSAM KSDS    |
                    |  Key: USR-ID  |
                    |  Rec: 80 bytes |
                    +----------------+
                            |
                  +---------+---------+
                  |         |         |
               NORMAL    DUPKEY/   OTHER
                  |       DUPREC     |
                  v         |        v
           "User added"    |   "Unable to
            (green msg)    |    Add User"
                           v
                    "User ID already
                      exist..."
```

### 4.5 VSAM Files Accessed by Upstream Programs

| Program   | DD Name | Operation | Purpose                           |
|-----------|---------|-----------|-----------------------------------|
| COSGN00C  | USRSEC  | READ      | Authenticate user during sign-on  |

---

## Section 5: BMS Screen Map

### 5.1 Map Identification

| Attribute    | Value         |
|--------------|---------------|
| **Mapset**   | `COUSR01`     |
| **Map**      | `COUSR1A`     |
| **Source**   | `app/bms/COUSR01.bms` |
| **Symbolic** | `app/cpy-bms/COUSR01.CPY` (BMS-generated, referenced via `COPY COUSR01.`) |
| **Size**     | 24 rows x 80 columns |

### 5.2 ASCII Screen Layout

```
Row Col  Field
--- ---  -----------------------------------------------------------------------
 1   1   Tran: CU01      AWS Mainframe Modernization             Date: mm/dd/yy
 2   1   Prog: COUSR01C              CardDemo                    Time: hh:mm:ss
 3
 4  35                    Add User
 5
 6
 7
 8   6   First Name: [____________________]    Last Name: [____________________]
 9
10
11   6   User ID:  [________] (8 Char)    Password: [________] (8 Char)
12
13
14   6   User Type: [_] (A=Admin, U=User)
15-22     (blank)
23   1   [Error/Success message area - 78 chars                                ]
24   1   ENTER=Add User  F3=Back  F4=Clear  F12=Exit
```

### 5.3 Field Inventory

| # | Field Name | Row | Col | Length | Attributes        | Color     | Purpose                  |
|---|------------|-----|-----|--------|-------------------|-----------|--------------------------|
| 1 | TRNNAME    |  1  |  7  |   4    | ASKIP,FSET,NORM   | Blue      | Transaction ID display   |
| 2 | TITLE01    |  1  | 21  |  40    | ASKIP,FSET,NORM   | Yellow    | Application title line 1 |
| 3 | CURDATE    |  1  | 71  |   8    | ASKIP,FSET,NORM   | Blue      | Current date (mm/dd/yy)  |
| 4 | PGMNAME    |  2  |  7  |   8    | ASKIP,FSET,NORM   | Blue      | Program name display     |
| 5 | TITLE02    |  2  | 21  |  40    | ASKIP,FSET,NORM   | Yellow    | Application title line 2 |
| 6 | CURTIME    |  2  | 71  |   8    | ASKIP,FSET,NORM   | Blue      | Current time (hh:mm:ss)  |
| 7 | (literal)  |  4  | 35  |   9    | ASKIP,BRT         | Neutral   | "Add User" screen title  |
| 8 | FNAME      |  8  | 18  |  20    | FSET,IC,UNPROT    | Green/UL  | **INPUT:** First name    |
| 9 | LNAME      |  8  | 56  |  20    | FSET,UNPROT       | Green/UL  | **INPUT:** Last name     |
|10 | USERID     | 11  | 15  |   8    | FSET,UNPROT       | Green/UL  | **INPUT:** User ID       |
|11 | PASSWD     | 11  | 55  |   8    | DRK,FSET,UNPROT   | Green/UL  | **INPUT:** Password (dark)|
|12 | USRTYPE    | 14  | 17  |   1    | FSET,UNPROT       | Green/UL  | **INPUT:** User type     |
|13 | ERRMSG     | 23  |  1  |  78    | ASKIP,BRT,FSET    | Red       | Error/success messages   |

### 5.4 Function Key Assignments

| Key     | EIBAID Constant | Action                                           |
|---------|-----------------|--------------------------------------------------|
| ENTER   | DFHENTER        | Validate input fields and write user to USRSEC   |
| PF3     | DFHPF3          | XCTL back to COADM01C (Admin Menu)              |
| PF4     | DFHPF4          | Clear all input fields and redisplay empty form  |
| OTHER   | (any other)     | Display "Invalid key pressed" error message      |

**Note:** The BMS footer text also mentions F12=Exit, but COUSR01C does not explicitly handle DFHPF12 in its EVALUATE EIBAID block. Pressing F12 falls into the `WHEN OTHER` branch and displays the invalid key message.

---

## Section 6: COMMAREA Structure

### 6.1 COMMAREA Definition (COCOM01Y)

```
01 CARDDEMO-COMMAREA.                          Total size: ~168 bytes
   05 CDEMO-GENERAL-INFO.
      10 CDEMO-FROM-TRANID      PIC X(04).     Source transaction ID
      10 CDEMO-FROM-PROGRAM     PIC X(08).     Source program name
      10 CDEMO-TO-TRANID        PIC X(04).     Target transaction ID
      10 CDEMO-TO-PROGRAM       PIC X(08).     Target program name
      10 CDEMO-USER-ID          PIC X(08).     Authenticated user ID
      10 CDEMO-USER-TYPE        PIC X(01).     User type: 'A'=Admin, 'U'=User
         88 CDEMO-USRTYP-ADMIN  VALUE 'A'.
         88 CDEMO-USRTYP-USER   VALUE 'U'.
      10 CDEMO-PGM-CONTEXT      PIC 9(01).     Pseudo-conversational state
         88 CDEMO-PGM-ENTER     VALUE 0.       First entry (display screen)
         88 CDEMO-PGM-REENTER   VALUE 1.       Re-entry (process input)
   05 CDEMO-CUSTOMER-INFO.
      10 CDEMO-CUST-ID          PIC 9(09).     Customer ID
      10 CDEMO-CUST-FNAME       PIC X(25).     Customer first name
      10 CDEMO-CUST-MNAME       PIC X(25).     Customer middle name
      10 CDEMO-CUST-LNAME       PIC X(25).     Customer last name
   05 CDEMO-ACCOUNT-INFO.
      10 CDEMO-ACCT-ID          PIC 9(11).     Account ID
      10 CDEMO-ACCT-STATUS      PIC X(01).     Account status
   05 CDEMO-CARD-INFO.
      10 CDEMO-CARD-NUM         PIC 9(16).     Card number
   05 CDEMO-MORE-INFO.
      10 CDEMO-LAST-MAP         PIC X(7).      Last map displayed
      10 CDEMO-LAST-MAPSET      PIC X(7).      Last mapset used
```

### 6.2 COMMAREA Field Usage by Program

| Field                | COSGN00C (Sign-On) | COADM01C (Admin Menu) | COUSR01C (Add User) |
|----------------------|---------------------|-----------------------|---------------------|
| CDEMO-FROM-TRANID    | WRITE (CC00)        | WRITE (CA00)          | WRITE (CU01)        |
| CDEMO-FROM-PROGRAM   | WRITE (COSGN00C)    | WRITE (COADM01C)      | WRITE (COUSR01C)    |
| CDEMO-TO-PROGRAM     | -                   | WRITE (COSGN00C)      | WRITE (COADM01C/COSGN00C) |
| CDEMO-USER-ID        | WRITE               | READ                  | READ (implicit)     |
| CDEMO-USER-TYPE      | WRITE               | READ (for routing)    | READ (implicit)     |
| CDEMO-PGM-CONTEXT    | WRITE (set to 0)    | READ/WRITE            | READ/WRITE          |

### 6.3 COMMAREA Inter-Program Flow

```
COSGN00C                    COADM01C                    COUSR01C
-----------                 -----------                 -----------
Sets:                       Sets:                       Sets:
  FROM-TRANID = CC00          FROM-TRANID = CA00          FROM-TRANID = CU01
  FROM-PROGRAM = COSGN00C     FROM-PROGRAM = COADM01C     FROM-PROGRAM = COUSR01C
  USER-ID = (input)           PGM-CONTEXT = 0             TO-PROGRAM = COADM01C
  USER-TYPE = (from USRSEC)                                  (on PF3 back)
  PGM-CONTEXT = 0                                         PGM-CONTEXT = 0|1
         |                           |                           |
         | XCTL w/COMMAREA           | XCTL w/COMMAREA           | RETURN TRANSID
         +-------------------------->+-------------------------->| w/COMMAREA
                                                                 |
                                                          (pseudo-conv loop)
```

### 6.4 Key COMMAREA Observations

- **COUSR01C does not use the CUSTOMER-INFO, ACCOUNT-INFO, or CARD-INFO sections** of the COMMAREA. These sections are used by transaction/account management programs but are irrelevant for user administration.
- **CDEMO-PGM-CONTEXT** is the critical pseudo-conversational control field:
  - Value `0` = first entry: display empty form
  - Value `1` = re-entry: process user input
- **CDEMO-TO-PROGRAM** is set dynamically for the XCTL back-navigation target.

---

## Section 7: Business Logic Flow

### 7.1 Entry Conditions and Initialization

1. **Direct invocation check (EIBCALEN = 0):** If the program is entered without a COMMAREA (e.g., directly from a terminal without sign-on), it immediately XCTLs to `COSGN00C` to force authentication.

2. **First entry (CDEMO-PGM-CONTEXT = 0):**
   - Set `CDEMO-PGM-REENTER` to TRUE (context = 1)
   - Initialize screen output area (`COUSR1AO`) to LOW-VALUES
   - Set cursor to First Name field (`FNAMEL = -1`)
   - SEND empty Add User screen
   - RETURN with TRANSID `CU01` (pseudo-conversational suspend)

3. **Re-entry (CDEMO-PGM-CONTEXT = 1):**
   - RECEIVE MAP to get user input
   - Evaluate which key was pressed

### 7.2 Input Validation Sequence (PROCESS-ENTER-KEY)

When ENTER is pressed, fields are validated in strict order. The **first** failing field stops validation:

| Order | Field    | Symbolic Name            | Validation Rule            | Error Message                        |
|-------|----------|--------------------------|----------------------------|--------------------------------------|
| 1     | First Name | FNAMEI OF COUSR1AI     | Not SPACES or LOW-VALUES   | "First Name can NOT be empty..."     |
| 2     | Last Name  | LNAMEI OF COUSR1AI     | Not SPACES or LOW-VALUES   | "Last Name can NOT be empty..."      |
| 3     | User ID    | USERIDI OF COUSR1AI    | Not SPACES or LOW-VALUES   | "User ID can NOT be empty..."        |
| 4     | Password   | PASSWDI OF COUSR1AI    | Not SPACES or LOW-VALUES   | "Password can NOT be empty..."       |
| 5     | User Type  | USRTYPEI OF COUSR1AI   | Not SPACES or LOW-VALUES   | "User Type can NOT be empty..."      |

**Note:** There is no format validation for User Type (e.g., checking it is 'A' or 'U'). Any non-blank single character is accepted.

### 7.3 Data Write Sequence

If all validations pass:

1. **Map screen input to VSAM record:**
   ```
   SEC-USR-ID    <-- USERIDI  OF COUSR1AI
   SEC-USR-FNAME <-- FNAMEI   OF COUSR1AI
   SEC-USR-LNAME <-- LNAMEI   OF COUSR1AI
   SEC-USR-PWD   <-- PASSWDI  OF COUSR1AI
   SEC-USR-TYPE  <-- USRTYPEI OF COUSR1AI
   ```

2. **EXEC CICS WRITE** to USRSEC dataset with SEC-USR-ID as the RIDFLD key.

3. **Response handling:**
   - **NORMAL:** Call `INITIALIZE-ALL-FIELDS` to clear all input fields, compose success message "User {ID} has been added ..." in green, redisplay screen.
   - **DUPKEY / DUPREC:** Set error flag, display "User ID already exist..." with cursor on User ID field.
   - **OTHER:** Set error flag, display "Unable to Add User..." with cursor on First Name field.

### 7.4 Screen Clear (PF4)

1. Call `INITIALIZE-ALL-FIELDS`:
   - Set cursor to First Name (`FNAMEL = -1`)
   - Clear all input fields to SPACES: USERIDI, FNAMEI, LNAMEI, PASSWDI, USRTYPEI
   - Clear WS-MESSAGE to SPACES
2. Redisplay the empty Add User screen via `SEND-USRADD-SCREEN`.

### 7.5 Back Navigation (PF3)

1. Set `CDEMO-TO-PROGRAM` to `'COADM01C'` (Admin Menu)
2. Call `RETURN-TO-PREV-SCREEN`:
   - If `CDEMO-TO-PROGRAM` is blank, default to `'COSGN00C'`
   - Set `CDEMO-FROM-TRANID` = `'CU01'`
   - Set `CDEMO-FROM-PROGRAM` = `'COUSR01C'`
   - Set `CDEMO-PGM-CONTEXT` = 0 (so admin menu enters as first-entry)
   - XCTL to `COADM01C` with COMMAREA

### 7.6 Header Population (POPULATE-HEADER-INFO)

Every SEND MAP call first populates the screen header:
- `TITLE01O` = Application title line 1 (from COTTL01Y)
- `TITLE02O` = Application title line 2 (from COTTL01Y)
- `TRNNAMEO` = Transaction ID ('CU01')
- `PGMNAMEO` = Program name ('COUSR01C')
- `CURDATEO` = Current date in MM/DD/YY format
- `CURTIMEO` = Current time in HH:MM:SS format

### 7.7 Error Handling Summary

| Condition                     | Error Flag | Cursor Position | Message                                   |
|-------------------------------|------------|-----------------|-------------------------------------------|
| EIBCALEN = 0 (no COMMAREA)   | -          | -               | XCTL to sign-on screen                   |
| Empty First Name              | Y          | FNAME           | "First Name can NOT be empty..."          |
| Empty Last Name               | Y          | LNAME           | "Last Name can NOT be empty..."           |
| Empty User ID                 | Y          | USERID          | "User ID can NOT be empty..."             |
| Empty Password                | Y          | PASSWD          | "Password can NOT be empty..."            |
| Empty User Type               | Y          | USRTYPE         | "User Type can NOT be empty..."           |
| Duplicate user ID (WRITE)     | Y          | USERID          | "User ID already exist..."                |
| Other WRITE error             | Y          | FNAME           | "Unable to Add User..."                   |
| Invalid key pressed           | Y          | FNAME           | "Invalid key pressed. Please see below..."|
| Successful write              | N          | FNAME           | "User {ID} has been added ..." (green)    |

---

## Section 8: Complete File Inventory

### 8.1 COBOL Source Programs

| # | File                          | Program    | Role in CU01 Flow               |
|---|-------------------------------|------------|----------------------------------|
| 1 | `app/cbl/COUSR01C.cbl`        | COUSR01C   | Target program (Add User)        |
| 2 | `app/cbl/COADM01C.cbl`        | COADM01C   | Upstream: Admin Menu             |
| 3 | `app/cbl/COSGN00C.cbl`        | COSGN00C   | Upstream: Sign-On                |

### 8.2 Copybooks

| # | File                          | Type                       |
|---|-------------------------------|----------------------------|
| 1 | `app/cpy/COCOM01Y.cpy`        | COMMAREA definition        |
| 2 | `app/cpy/COTTL01Y.cpy`        | Screen title constants     |
| 3 | `app/cpy/CSDAT01Y.cpy`        | Date/time structure        |
| 4 | `app/cpy/CSMSG01Y.cpy`        | Common messages            |
| 5 | `app/cpy/CSUSR01Y.cpy`        | USRSEC record layout       |
| 6 | `app/cpy/COADM02Y.cpy`        | Admin menu options table   |

### 8.3 BMS-Generated Symbolic Map Copybooks

| # | File                           | Notes                                          |
|---|--------------------------------|------------------------------------------------|
| 1 | `app/cpy-bms/COUSR01.CPY`      | Generated from COUSR01.bms; included via `COPY COUSR01.` |

**Note:** The `COPY COUSR01.` statement in COUSR01C.cbl references the BMS-generated symbolic map. This file resides in `app/cpy-bms/` (not `app/cpy/`). The COBOL compiler's COPY path must include both directories.

### 8.4 BMS Map Sources

| # | File                          | Mapset   | Map      |
|---|-------------------------------|----------|----------|
| 1 | `app/bms/COUSR01.bms`          | COUSR01  | COUSR1A  |

### 8.5 CICS System Copybooks (Not in Repository)

| # | Copybook   | Description                                   |
|---|------------|-----------------------------------------------|
| 1 | DFHAID     | AID key constants (DFHENTER, DFHPF3, etc.)   |
| 2 | DFHBMSCA   | BMS attribute constants (DFHGREEN, etc.)      |

### 8.6 VSAM Data Files

| # | DD Name  | DSNAME (from CSD)                            | Type     |
|---|----------|----------------------------------------------|----------|
| 1 | USRSEC   | AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS            | KSDS     |

### 8.7 CSD Definitions

| # | Resource         | Definition                                      |
|---|------------------|-------------------------------------------------|
| 1 | Transaction CU01 | `DEFINE TRANSACTION(CU01) GROUP(CARDDEMO) PROGRAM(COUSR01C)` |
| 2 | Program COUSR01C | `DEFINE PROGRAM(COUSR01C) GROUP(CARDDEMO) LANGUAGE(COBOL)`   |
| 3 | Mapset COUSR01   | `DEFINE MAPSET(COUSR01) GROUP(CARDDEMO)`                     |
| 4 | File USRSEC      | `DEFINE FILE(USRSEC) GROUP(CARDDEMO)`                        |

---

## Section 9: Transaction Record Layout

### USRSEC Record (Written by COUSR01C)

```
Offset  Length  Field          PIC         Description
------  ------  -------------- ----------  --------------------------------
  1       8     SEC-USR-ID     X(08)       User ID (primary KSDS key)
  9      20     SEC-USR-FNAME  X(20)       First name
 29      20     SEC-USR-LNAME  X(20)       Last name
 49       8     SEC-USR-PWD    X(08)       Password (stored in clear text)
 57       1     SEC-USR-TYPE   X(01)       User type: 'A'=Admin, 'U'=User
 58      23     SEC-USR-FILLER X(23)       Reserved filler
------  ------
  1      80     TOTAL
```

**Key observations:**
- Password is stored in **clear text** (PIC X(08)) with no encryption or hashing.
- The User Type field accepts any single character; validation is only for non-blank.
- The 23-byte filler suggests the record was designed with room for future extension.

---

## Section 10: Observations

### What the Flow Demonstrates Well

1. **Clean pseudo-conversational pattern:** Clear separation of first-entry (display) vs re-entry (process) using `CDEMO-PGM-CONTEXT`. This is a textbook CICS design that translates directly to a web request/response model.

2. **Centralized COMMAREA:** All programs share `COCOM01Y`, making inter-program data flow explicit and traceable. This simplifies migration to a session/context object.

3. **Simple CRUD operation:** COUSR01C performs a single WRITE with straightforward error handling (NORMAL, DUPKEY/DUPREC, OTHER). No complex browse or multi-file transactions.

4. **Consistent screen header pattern:** The `POPULATE-HEADER-INFO` pattern is reused identically across programs, suggesting a common base that could become a shared utility.

5. **Menu-driven navigation via table:** COADM01C uses the `COADM02Y` option table to dynamically resolve XCTL targets, making it easy to add/remove admin functions without code changes.

### Gaps and Migration Considerations

1. **Clear-text password storage:** The USRSEC record stores passwords in plain text. Any modern target must implement proper password hashing (bcrypt, Argon2, etc.).

2. **No User Type validation beyond non-blank:** The program accepts any character for User Type. Migration should add enum validation (only 'A' or 'U').

3. **No audit trail:** The WRITE operation does not log who created the user, when, or from which terminal. Modern systems should add audit fields.

4. **No password complexity rules:** No minimum length, no character requirements. The field accepts 1-8 characters.

5. **Single-file, single-operation scope:** This program only writes to USRSEC. There are no cross-file consistency concerns, making it one of the simpler programs to migrate.

6. **BMS symbolic map in separate directory:** The `COPY COUSR01.` statement resolves to `app/cpy-bms/COUSR01.CPY`, not `app/cpy/`. Migration tooling must account for this split COPY path.

7. **F12 advertised but not handled:** The screen footer shows "F12=Exit" but the program does not handle DFHPF12 — it falls through to the invalid key message. This is a UI inconsistency to resolve during migration.

---

## Section 11: Comparison — Flow Complexity Assessment

| Dimension                  | COUSR01C (CU01)              | Typical Complex CICS Program  |
|----------------------------|------------------------------|-------------------------------|
| Programs in chain          | 3 (sign-on, menu, target)    | 5-10+                        |
| VSAM files accessed        | 1 (USRSEC)                   | 3-5+                         |
| VSAM operations            | 1 (WRITE only)               | 5-15 (READ/WRITE/BROWSE)    |
| BMS maps                   | 1 (COUSR1A)                  | 2-4 (list + detail + confirm)|
| Input fields               | 5                            | 10-30+                       |
| CALL sub-programs           | 0                            | 2-5                          |
| LINK sub-programs           | 0                            | 1-3                          |
| Browse operations           | 0                            | 1-3                          |
| DB2 SQL statements          | 0                            | 5-20                         |
| Copybooks                  | 8 (6 app + 2 system)         | 15-25                        |
| Lines of COBOL             | 300                          | 800-2000+                    |
| Pseudo-conv iterations     | Simple (2-state)             | Multi-state (3-5 contexts)   |
| **Migration Complexity**   | **Low**                      | **Medium to High**           |

COUSR01C is a **low-complexity** CICS program suitable as an early migration candidate or proof-of-concept target. Its simple single-file WRITE operation, straightforward validation, and clean pseudo-conversational pattern make it an ideal starting point for establishing migration patterns.

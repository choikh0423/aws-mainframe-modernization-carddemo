# COUSR03C (CU03) Transaction Flow Analysis

## User Delete (Security/Admin) - CardDemo Application

---

## Section 1: Flow Overview

| Attribute          | Value                                      |
|--------------------|--------------------------------------------|
| **Transaction ID** | CU03                                       |
| **Entry Program**  | COUSR03C                                   |
| **Function**       | Delete a user from the USRSEC security file|
| **Access Type**    | VSAM KSDS                                  |
| **User Type**      | Admin only                                 |
| **CSD Group**      | CARDDEMO                                   |

### Complete Navigation Path

```
  Terminal
     |
     v
 [CC00] COSGN00C  (Sign-on)
     |
     | Admin user authenticates
     | XCTL with COMMAREA
     v
 [CA00] COADM01C  (Admin Menu)
     |
     | Option 4: "User Delete (Security)"
     | XCTL PROGRAM(COUSR03C) via menu table
     v
 [CU00] COUSR00C  (User List)         <--- alternate entry
     |
     | Select user with 'D' flag
     | XCTL PROGRAM('COUSR03C') with COMMAREA
     v
 [CU03] COUSR03C  (User Delete)  <--- TARGET PROGRAM
     |
     | PF3/PF12: XCTL back to COADM01C or FROM-PROGRAM
     | EIBCALEN=0: XCTL to COSGN00C
     v
 RETURN TRANSID('CU03') with COMMAREA
```

### Pseudo-Conversational Lifecycle

```
 ITERATION 1 (First Entry - CDEMO-PGM-CONTEXT = 0)
 +--------------------------------------------------+
 |  EIBCALEN = 0?                                   |
 |    YES --> XCTL to COSGN00C (no COMMAREA)        |
 |    NO  --> Move DFHCOMMAREA to CARDDEMO-COMMAREA |
 |            CDEMO-PGM-REENTER = FALSE              |
 |            Set CDEMO-PGM-REENTER = TRUE           |
 |            Initialize screen (LOW-VALUES)         |
 |            If user pre-selected from list:        |
 |              Move user ID to screen               |
 |              PERFORM PROCESS-ENTER-KEY (fetch)    |
 |            SEND MAP COUSR3A                       |
 |            RETURN TRANSID('CU03') + COMMAREA      |
 +--------------------------------------------------+
                       |
                       v
 ITERATION 2+ (Re-entry - CDEMO-PGM-CONTEXT = 1)
 +--------------------------------------------------+
 |  RECEIVE MAP COUSR3A                              |
 |  EVALUATE EIBAID:                                 |
 |    ENTER --> PROCESS-ENTER-KEY (fetch user)       |
 |    PF3   --> XCTL to FROM-PROGRAM or COADM01C    |
 |    PF4   --> Clear screen fields                  |
 |    PF5   --> DELETE-USER-INFO                     |
 |    PF12  --> XCTL to COADM01C                     |
 |    OTHER --> Error: "Invalid key pressed"          |
 |  RETURN TRANSID('CU03') + COMMAREA                |
 +--------------------------------------------------+
```

---

## Section 2: Programs Involved

| Program    | Type              | Transaction | Function                          | Called By   | Call Method        |
|------------|-------------------|-------------|-----------------------------------|-------------|--------------------|
| COSGN00C   | CICS COBOL        | CC00        | Sign-on screen                    | (terminal)  | Initial TRANSID    |
| COADM01C   | CICS COBOL        | CA00        | Admin menu                        | COSGN00C    | XCTL               |
| COUSR00C   | CICS COBOL        | CU00        | User list (browse USRSEC)         | COADM01C    | XCTL (option 1)    |
| COUSR03C   | CICS COBOL        | CU03        | **User delete** (target)          | COADM01C (option 4) / COUSR00C ('D' selection) | XCTL |

### XCTL Targets from COUSR03C

| Target Variable      | Resolved Value | Condition                                       |
|----------------------|----------------|--------------------------------------------------|
| CDEMO-TO-PROGRAM     | COSGN00C       | EIBCALEN = 0 (no COMMAREA, direct invocation)   |
| CDEMO-FROM-PROGRAM   | (dynamic)      | PF3 — returns to calling program                |
| CDEMO-TO-PROGRAM     | COADM01C       | PF3 when FROM-PROGRAM is empty                  |
| CDEMO-TO-PROGRAM     | COADM01C       | PF12 — always returns to admin menu             |

---

## Section 3: Copybooks

| Copybook   | Used By                          | Type                    | Purpose                                              |
|------------|----------------------------------|-------------------------|------------------------------------------------------|
| COCOM01Y   | COUSR03C, COADM01C, COSGN00C, COUSR00C | COMMAREA definition     | Navigation fields, customer/account/card info, program context |
| COUSR03    | COUSR03C                         | BMS symbolic map        | Auto-generated from COUSR03.bms; defines COUSR3AI (input) and COUSR3AO (output) structures |
| COTTL01Y   | COUSR03C, COADM01C, COSGN00C, COUSR00C | Screen titles           | Application title constants (CCDA-TITLE01, CCDA-TITLE02) |
| CSDAT01Y   | COUSR03C, COADM01C, COSGN00C, COUSR00C | Date/time structure     | WS-CURDATE-DATA, formatted date/time fields          |
| CSMSG01Y   | COUSR03C, COADM01C, COSGN00C, COUSR00C | Common messages         | CCDA-MSG-INVALID-KEY, CCDA-MSG-THANK-YOU             |
| CSUSR01Y   | COUSR03C, COSGN00C, COUSR00C    | VSAM record layout      | SEC-USER-DATA record for USRSEC file (80 bytes)      |
| COADM02Y   | COADM01C                        | Admin menu options      | Menu table mapping option numbers to program names   |
| DFHAID     | COUSR03C, COADM01C, COSGN00C, COUSR00C | **System copybook**     | CICS AID key constants (DFHENTER, DFHPF3, etc.)     |
| DFHBMSCA   | COUSR03C, COADM01C, COSGN00C, COUSR00C | **System copybook**     | BMS attribute constants (DFHNEUTR, DFHGREEN, etc.)   |

### CSUSR01Y Record Layout (SEC-USER-DATA) - USRSEC File

```
01 SEC-USER-DATA.                          (80 bytes total)
   05 SEC-USR-ID              PIC X(08).   Offset 0    - User ID (primary key)
   05 SEC-USR-FNAME           PIC X(20).   Offset 8    - First name
   05 SEC-USR-LNAME           PIC X(20).   Offset 28   - Last name
   05 SEC-USR-PWD             PIC X(08).   Offset 48   - Password
   05 SEC-USR-TYPE            PIC X(01).   Offset 56   - User type (A=Admin, U=User)
   05 SEC-USR-FILLER          PIC X(23).   Offset 57   - Reserved/filler
```

### COTTL01Y Record Layout

```
01 CCDA-SCREEN-TITLE.
   05 CCDA-TITLE01            PIC X(40).   '      AWS Mainframe Modernization       '
   05 CCDA-TITLE02            PIC X(40).   '              CardDemo                  '
   05 CCDA-THANK-YOU          PIC X(40).   'Thank you for using CCDA application... '
```

### CSDAT01Y Record Layout

```
01 WS-DATE-TIME.
   05 WS-CURDATE-DATA.
      10 WS-CURDATE.
         15 WS-CURDATE-YEAR   PIC 9(04).
         15 WS-CURDATE-MONTH  PIC 9(02).
         15 WS-CURDATE-DAY    PIC 9(02).
      10 WS-CURTIME.
         15 WS-CURTIME-HOURS  PIC 9(02).
         15 WS-CURTIME-MINUTE PIC 9(02).
         15 WS-CURTIME-SECOND PIC 9(02).
         15 WS-CURTIME-MILSEC PIC 9(02).
   05 WS-CURDATE-MM-DD-YY.      (formatted mm/dd/yy)
   05 WS-CURTIME-HH-MM-SS.      (formatted hh:mm:ss)
   05 WS-TIMESTAMP.             (formatted yyyy-mm-dd hh:mm:ss.ssssss)
```

### CSMSG01Y Record Layout

```
01 CCDA-COMMON-MESSAGES.
   05 CCDA-MSG-THANK-YOU      PIC X(50).   'Thank you for using CardDemo application...'
   05 CCDA-MSG-INVALID-KEY    PIC X(50).   'Invalid key pressed. Please see below...'
```

---

## Section 4: VSAM File Operations

### Files Accessed by COUSR03C

| DD Name  | VSAM Type | Key Field   | Key Length | Record Copybook | Record Length | DSN Pattern                               |
|----------|-----------|-------------|------------|-----------------|---------------|-------------------------------------------|
| USRSEC   | KSDS      | SEC-USR-ID  | 8 bytes    | CSUSR01Y        | 80 bytes      | AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS         |

### I/O Operations Detail

| # | Paragraph              | Command       | Dataset  | RIDFLD      | Options          | Purpose                                | RESP Handling |
|---|------------------------|---------------|----------|-------------|------------------|----------------------------------------|---------------|
| 1 | READ-USER-SEC-FILE     | EXEC CICS READ| USRSEC   | SEC-USR-ID  | UPDATE, KEYLENGTH| Read user record for display/confirm   | NORMAL: display user info + PF5 prompt; NOTFND: error "User ID NOT found"; OTHER: error "Unable to lookup User" |
| 2 | DELETE-USER-SEC-FILE   | EXEC CICS DELETE| USRSEC | (implicit)  | (uses prior UPDATE lock) | Delete the user record          | NORMAL: success "User xxx has been deleted"; NOTFND: error "User ID NOT found"; OTHER: error "Unable to Update User" |

### VSAM File Flow Diagram

```
COUSR03C
    |
    |  [1] EXEC CICS READ DATASET(USRSEC)
    |      RIDFLD(SEC-USR-ID) UPDATE
    |      +-- NORMAL: Display user details
    |      |   (First Name, Last Name, Type)
    |      |   Prompt: "Press PF5 key to delete"
    |      +-- NOTFND: "User ID NOT found"
    |      +-- OTHER:  "Unable to lookup User"
    |
    | User presses PF5
    |
    |  [2] EXEC CICS DELETE DATASET(USRSEC)
    |      (deletes record held by prior READ UPDATE)
    |      +-- NORMAL: "User xxx has been deleted"
    |      |   Clear all screen fields
    |      +-- NOTFND: "User ID NOT found"
    |      +-- OTHER:  "Unable to Update User"
    v
```

**Important pattern:** The READ is issued with the UPDATE option, which places a lock on the record. The subsequent DELETE (without RIDFLD) deletes the record held by the previous READ-for-UPDATE. This is the standard CICS READ-UPDATE-DELETE pattern.

---

## Section 5: BMS Screen Map

### Map Details

| Attribute   | Value          |
|-------------|----------------|
| Mapset      | COUSR03        |
| Map         | COUSR3A        |
| Source File | app/bms/COUSR03.bms |
| Size        | 24 rows x 80 columns |
| Options     | CTRL=(ALARM,FREEKB), EXTATT=YES, LANG=COBOL, MODE=INOUT, STORAGE=AUTO, TIOAPFX=YES |

### ASCII Screen Layout

```
Row Col  Field
+------------------------------------------------------------------------------+
|Tran: CU03  AWS Mainframe Modernization             Date: mm/dd/yy           | Row 1
|Prog: COUSR03C        CardDemo                      Time: hh:mm:ss           | Row 2
|                                                                              | Row 3
|                                  Delete User                                 | Row 4
|                                                                              | Row 5
|     Enter User ID: [________]                                                | Row 6
|                                                                              | Row 7
|     **********************************************************************   | Row 8
|                                                                              | Row 9
|                                                                              | Row 10
|     First Name: [____________________]                                       | Row 11
|                                                                              | Row 12
|     Last Name:  [____________________]                                       | Row 13
|                                                                              | Row 14
|     User Type:  [_] (A=Admin, U=User)                                        | Row 15
|                                                                              | Row 16-22
|                                                                              |
| Error/Status message (78 chars)                                              | Row 23
| ENTER=Fetch  F3=Back  F4=Clear  F5=Delete                                   | Row 24
+------------------------------------------------------------------------------+
```

### Field Inventory

| Field Name | Row | Col | Length | Attributes         | Color     | Purpose                    |
|------------|-----|-----|--------|--------------------|-----------|----------------------------|
| TRNNAME    | 1   | 7   | 4      | ASKIP, FSET        | Blue      | Transaction ID (CU03)      |
| TITLE01    | 1   | 21  | 40     | ASKIP, FSET        | Yellow    | Application title line 1   |
| CURDATE    | 1   | 71  | 8      | ASKIP, FSET        | Blue      | Current date (mm/dd/yy)    |
| PGMNAME    | 2   | 7   | 8      | ASKIP, FSET        | Blue      | Program name (COUSR03C)    |
| TITLE02    | 2   | 21  | 40     | ASKIP, FSET        | Yellow    | Application title line 2   |
| CURTIME    | 2   | 71  | 8      | ASKIP, FSET        | Blue      | Current time (hh:mm:ss)    |
| USRIDIN    | 6   | 21  | 8      | **UNPROT**, FSET, IC | Green   | **User ID input** (editable)|
| FNAME      | 11  | 18  | 20     | ASKIP, FSET        | Blue      | First name (display only)  |
| LNAME      | 13  | 18  | 20     | ASKIP, FSET        | Blue      | Last name (display only)   |
| USRTYPE    | 15  | 17  | 1      | ASKIP, FSET        | Blue      | User type (display only)   |
| ERRMSG     | 23  | 1   | 78     | ASKIP, BRT, FSET   | Red       | Error/status messages      |

### Function Key Assignments

| Key    | EIBAID Constant | Action                                               |
|--------|-----------------|------------------------------------------------------|
| ENTER  | DFHENTER        | Fetch user details from USRSEC file                  |
| PF3    | DFHPF3          | Return to calling program (FROM-PROGRAM or COADM01C) |
| PF4    | DFHPF4          | Clear all input/display fields                       |
| PF5    | DFHPF5          | **Delete the displayed user record**                 |
| PF12   | DFHPF12         | Return to Admin Menu (COADM01C)                      |

---

## Section 6: COMMAREA Structure

### COCOM01Y - CARDDEMO-COMMAREA

```
01 CARDDEMO-COMMAREA.
   05 CDEMO-GENERAL-INFO.
      10 CDEMO-FROM-TRANID          PIC X(04).   Source transaction ID
      10 CDEMO-FROM-PROGRAM         PIC X(08).   Source program name
      10 CDEMO-TO-TRANID            PIC X(04).   Target transaction ID
      10 CDEMO-TO-PROGRAM           PIC X(08).   Target program name
      10 CDEMO-USER-ID              PIC X(08).   Logged-in user ID
      10 CDEMO-USER-TYPE            PIC X(01).   User type (A/U)
         88 CDEMO-USRTYP-ADMIN      VALUE 'A'.
         88 CDEMO-USRTYP-USER       VALUE 'U'.
      10 CDEMO-PGM-CONTEXT          PIC 9(01).   Pseudo-conversational flag
         88 CDEMO-PGM-ENTER         VALUE 0.     First entry
         88 CDEMO-PGM-REENTER       VALUE 1.     Re-entry
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

### COUSR03C Program-Specific Extension (CDEMO-CU03-INFO)

Defined inline in COUSR03C.cbl immediately after `COPY COCOM01Y`:

```
   05 CDEMO-CU03-INFO.
      10 CDEMO-CU03-USRID-FIRST     PIC X(08).   First user ID on page
      10 CDEMO-CU03-USRID-LAST      PIC X(08).   Last user ID on page
      10 CDEMO-CU03-PAGE-NUM        PIC 9(08).   Current page number
      10 CDEMO-CU03-NEXT-PAGE-FLG   PIC X(01).   More pages flag (Y/N)
         88 NEXT-PAGE-YES            VALUE 'Y'.
         88 NEXT-PAGE-NO             VALUE 'N'.
      10 CDEMO-CU03-USR-SEL-FLG     PIC X(01).   Selection flag from list
      10 CDEMO-CU03-USR-SELECTED    PIC X(08).   Selected user ID from list
```

### COMMAREA Field Usage by Program

| Field                | COSGN00C (CC00)  | COADM01C (CA00)  | COUSR00C (CU00)  | COUSR03C (CU03)  |
|----------------------|------------------|-------------------|-------------------|-------------------|
| CDEMO-FROM-TRANID    | Write            | Write             | Write             | Write             |
| CDEMO-FROM-PROGRAM   | Write            | Write             | Write             | Write             |
| CDEMO-TO-PROGRAM     | Write            | Write             | Write             | Read/Write        |
| CDEMO-USER-ID        | Write            | -                 | -                 | -                 |
| CDEMO-USER-TYPE      | Write            | -                 | -                 | -                 |
| CDEMO-PGM-CONTEXT    | Write (0)        | Write (0)         | Write (0)         | Read/Write        |
| CDEMO-CU03-USR-SELECTED | -             | -                 | Write             | Read              |

### Inter-Program COMMAREA Flow

```
COSGN00C                 COADM01C                COUSR00C               COUSR03C
  |                        |                       |                       |
  | Sets:                  | Sets:                 | Sets:                 | Reads:
  | CDEMO-USER-ID          | CDEMO-FROM-TRANID     | CDEMO-TO-PROGRAM      | CDEMO-PGM-CONTEXT
  | CDEMO-USER-TYPE        |   = 'CA00'            |   = 'COUSR03C'        | CDEMO-FROM-PROGRAM
  | CDEMO-FROM-TRANID      | CDEMO-FROM-PROGRAM    | CDEMO-FROM-TRANID     | CDEMO-CU03-USR-SELECTED
  |   = 'CC00'             |   = 'COADM01C'        |   = 'CU00'            |
  | CDEMO-FROM-PROGRAM     | CDEMO-PGM-CONTEXT     | CDEMO-FROM-PROGRAM    | Writes:
  |   = 'COSGN00C'         |   = 0                 |   = 'COUSR00C'        | CDEMO-FROM-TRANID
  | CDEMO-PGM-CONTEXT      |                       | CDEMO-PGM-CONTEXT     |   = 'CU03'
  |   = 0                  | XCTL ----+            |   = 0                 | CDEMO-FROM-PROGRAM
  |                        |          |            |                       |   = 'COUSR03C'
  | XCTL to COADM01C      |          v            | XCTL to COUSR03C      | CDEMO-TO-PROGRAM
  +----------------------->+   COUSR03C            +---------------------> | CDEMO-PGM-CONTEXT
                           |  (via menu opt 4)     | (with USR-SELECTED)   |   = 0 (on XCTL out)
                           +---------------------->+                       |   = 1 (on RETURN)
```

---

## Section 7: Business Logic Flow

### Step-by-Step Narrative

#### 1. Entry and Initialization

1. Transaction CU03 invokes program COUSR03C
2. Clear error flag (`ERR-FLG-OFF`) and modification flag (`USR-MODIFIED-NO`)
3. Clear message areas
4. **EIBCALEN check:** If EIBCALEN = 0 (no COMMAREA, direct terminal entry), redirect to sign-on screen (COSGN00C) via XCTL
5. If EIBCALEN > 0, copy DFHCOMMAREA to CARDDEMO-COMMAREA

#### 2. First Entry (CDEMO-PGM-CONTEXT = 0)

1. Set CDEMO-PGM-REENTER = TRUE (context = 1) for next iteration
2. Initialize screen fields to LOW-VALUES
3. Set cursor to User ID input field
4. **Pre-selection check:** If CDEMO-CU03-USR-SELECTED is non-blank (user selected from COUSR00C list screen):
   - Move selected user ID to the screen input field
   - Perform PROCESS-ENTER-KEY to auto-fetch user details
5. Send the Delete User screen (COUSR3A)
6. RETURN TRANSID('CU03') with COMMAREA — suspend

#### 3. Re-Entry (CDEMO-PGM-CONTEXT = 1)

1. Receive the screen (COUSR3A) into COUSR3AI
2. Evaluate EIBAID (which key the user pressed)

#### 4. ENTER Key - Fetch User Details (PROCESS-ENTER-KEY)

1. **Validation:** If User ID is empty → error "User ID can NOT be empty..."
2. Clear display fields (FNAME, LNAME, USRTYPE)
3. Move User ID from screen to SEC-USR-ID
4. **READ USRSEC** with UPDATE option:
   - **NORMAL:** Display user's first name, last name, and type on screen; show message "Press PF5 key to delete this user..." with neutral color
   - **NOTFND:** Error "User ID NOT found..."
   - **OTHER:** Display RESP/REAS codes; error "Unable to lookup User..."
5. Send updated screen

#### 5. PF5 Key - Delete User (DELETE-USER-INFO)

1. **Validation:** If User ID is empty → error "User ID can NOT be empty..."
2. Move User ID from screen to SEC-USR-ID
3. **READ USRSEC** with UPDATE (to acquire lock)
4. **DELETE USRSEC** (deletes the locked record):
   - **NORMAL:** Clear all screen fields; show success message "User xxx has been deleted..." in green
   - **NOTFND:** Error "User ID NOT found..."
   - **OTHER:** Display RESP/REAS codes; error "Unable to Update User..."
5. Send updated screen

#### 6. PF3 Key - Return to Previous Screen

1. If CDEMO-FROM-PROGRAM is set → XCTL to that program
2. If CDEMO-FROM-PROGRAM is empty → XCTL to COADM01C (admin menu)
3. Sets CDEMO-FROM-TRANID = 'CU03', CDEMO-FROM-PROGRAM = 'COUSR03C', CDEMO-PGM-CONTEXT = 0

#### 7. PF4 Key - Clear Screen

1. Reset cursor to User ID field
2. Clear User ID, First Name, Last Name, User Type, and message fields

#### 8. PF12 Key - Return to Admin Menu

1. Always XCTL to COADM01C (admin menu)
2. Sets navigation fields same as PF3

#### 9. Other Keys - Error

1. Set error flag
2. Display "Invalid key pressed. Please see below..."
3. Re-send screen

### Error Handling Summary

| Condition                | Error Message                              | Error Flag | Cursor Position |
|--------------------------|--------------------------------------------|------------|-----------------|
| Empty User ID (ENTER)   | "User ID can NOT be empty..."              | Y          | User ID field   |
| Empty User ID (PF5)     | "User ID can NOT be empty..."              | Y          | User ID field   |
| User not found (READ)   | "User ID NOT found..."                     | Y          | User ID field   |
| Read I/O error           | "Unable to lookup User..."                 | Y          | First Name field|
| Delete not found         | "User ID NOT found..."                     | Y          | User ID field   |
| Delete I/O error         | "Unable to Update User..."                 | Y          | First Name field|
| Invalid key              | "Invalid key pressed. Please see below..." | Y          | (unchanged)     |

---

## Section 8: Complete File Inventory

### Source Programs

| File                 | Path                    | Type        | Transaction |
|----------------------|-------------------------|-------------|-------------|
| COUSR03C.cbl         | app/cbl/COUSR03C.cbl    | CICS COBOL  | CU03        |
| COADM01C.cbl         | app/cbl/COADM01C.cbl    | CICS COBOL  | CA00        |
| COSGN00C.cbl         | app/cbl/COSGN00C.cbl    | CICS COBOL  | CC00        |
| COUSR00C.cbl         | app/cbl/COUSR00C.cbl    | CICS COBOL  | CU00        |

### Application Copybooks

| File                 | Path                    | Type                   |
|----------------------|-------------------------|------------------------|
| COCOM01Y.cpy         | app/cpy/COCOM01Y.cpy    | COMMAREA definition    |
| COTTL01Y.cpy         | app/cpy/COTTL01Y.cpy    | Screen title constants |
| CSDAT01Y.cpy         | app/cpy/CSDAT01Y.cpy    | Date/time structure    |
| CSMSG01Y.cpy         | app/cpy/CSMSG01Y.cpy    | Common messages        |
| CSUSR01Y.cpy         | app/cpy/CSUSR01Y.cpy    | User record layout     |
| COADM02Y.cpy         | app/cpy/COADM02Y.cpy    | Admin menu options     |

### BMS Symbolic Map Copybooks

| File                 | Path                        | Type                       |
|----------------------|-----------------------------|----------------------------|
| COUSR03.CPY          | app/cpy-bms/COUSR03.CPY     | BMS-generated symbolic map |

### System Copybooks (IBM-supplied, not in repository)

| Copybook   | Purpose                                          |
|------------|--------------------------------------------------|
| DFHAID     | CICS AID key constants (DFHENTER, DFHPF3, etc.) |
| DFHBMSCA   | BMS attribute constants (DFHNEUTR, DFHGREEN, etc.) |

### BMS Map Source

| File                 | Path                    | Mapset  | Map     |
|----------------------|-------------------------|---------|---------|
| COUSR03.bms          | app/bms/COUSR03.bms     | COUSR03 | COUSR3A |

### VSAM Data Files

| DD Name  | DSN                                      | Key       | Record Copybook |
|----------|------------------------------------------|-----------|-----------------|
| USRSEC   | AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS        | SEC-USR-ID (8 bytes) | CSUSR01Y |

### CSD Definitions

| Resource Type | Name     | Group    | Key Properties                    |
|---------------|----------|----------|-----------------------------------|
| TRANSACTION   | CU03     | CARDDEMO | PROGRAM(COUSR03C)                 |
| PROGRAM       | COUSR03C | CARDDEMO | LANGUAGE(COBOL)                   |
| MAPSET        | COUSR03  | CARDDEMO | -                                 |
| FILE          | USRSEC   | CARDDEMO | KSDS, ADD/BROWSE/DELETE/READ/UPDATE |

---

## Section 9: Transaction Record Layout

### USRSEC Record (SEC-USER-DATA from CSUSR01Y.cpy)

This is the record that is READ and DELETEd by COUSR03C.

| Offset | Length | Field           | PIC      | Description                |
|--------|--------|-----------------|----------|----------------------------|
| 0      | 8      | SEC-USR-ID      | X(08)    | User ID (primary KSDS key) |
| 8      | 20     | SEC-USR-FNAME   | X(20)    | First name                 |
| 28     | 20     | SEC-USR-LNAME   | X(20)    | Last name                  |
| 48     | 8      | SEC-USR-PWD     | X(08)    | Password                   |
| 56     | 1      | SEC-USR-TYPE    | X(01)    | User type (A=Admin, U=User)|
| 57     | 23     | SEC-USR-FILLER  | X(23)    | Reserved                   |
| **Total** | **80** |              |          |                            |

---

## Section 10: Observations

### Strengths

1. **Clean pseudo-conversational pattern:** COUSR03C follows the standard CICS RETURN TRANSID pattern with a clear CDEMO-PGM-CONTEXT flag distinguishing first entry from re-entry
2. **Two-step delete confirmation:** The user must first fetch (ENTER) then explicitly confirm deletion (PF5), preventing accidental deletes
3. **READ-for-UPDATE pattern:** The program correctly uses READ with UPDATE option before DELETE, ensuring record-level locking
4. **Consistent error handling:** All VSAM operations check RESP codes and provide user-friendly messages
5. **Pre-selection support:** The program accepts a pre-selected user ID from the User List screen (COUSR00C) via COMMAREA, providing a seamless workflow
6. **Consistent navigation:** PF3 returns to the calling program (dynamic), PF12 always returns to admin menu

### Gaps / Migration Considerations

1. **No audit trail:** The delete operation does not log who deleted which user or when, which would be expected in a modern system
2. **No soft delete:** Records are physically deleted from VSAM with no recovery mechanism; modern systems typically use logical/soft deletes
3. **No referential integrity check:** The program does not verify whether the user being deleted has active sessions, owns resources, or has dependencies in other files
4. **Password stored in plain text:** The USRSEC record stores passwords as plain PIC X(08) without any encryption or hashing
5. **No self-delete prevention:** An admin can delete their own user account, which could lock them out of the system
6. **Fixed 80-byte record:** The USRSEC record has a rigid 80-byte fixed structure with a 23-byte filler, limiting extensibility
7. **No concurrency messaging:** If another terminal deletes the same user between the ENTER (fetch) and PF5 (delete), the user gets a generic "NOT found" error rather than a specific "already deleted" message
8. **Single VSAM file access:** Only USRSEC is accessed; migration to a relational database is straightforward for this program

---

## Section 11: Migration Complexity Comparison

| Dimension                     | COUSR03C (CU03)         | Notes                                    |
|-------------------------------|-------------------------|------------------------------------------|
| **Programs in flow**          | 4 (COSGN00C, COADM01C, COUSR00C, COUSR03C) | Linear chain, no sub-programs |
| **VSAM files accessed**       | 1 (USRSEC)              | Simple, single-file operation            |
| **EXEC CICS commands**        | 5 unique types          | SEND MAP, RECEIVE MAP, READ, DELETE, RETURN, XCTL |
| **CALL sub-programs**         | 0                       | No CALL statements; self-contained       |
| **Copybooks**                 | 9 (7 app + 2 system)    | Standard set, all shared across programs |
| **BMS maps**                  | 1 (COUSR3A)             | Simple single-screen form                |
| **Input fields**              | 1 (User ID)             | Minimal input validation required        |
| **Business rules**            | Low complexity          | Fetch + delete; no calculations or cross-file lookups |
| **Error handling paths**      | 7 conditions            | Standard RESP code handling              |
| **Migration difficulty**      | **Low**                 | Single table DELETE operation with simple UI |

### Migration Recommendations

1. **Database:** Map USRSEC to a `users` or `user_security` table with the same column structure
2. **UI:** The 3270 screen maps directly to a simple web form with one input field and read-only display fields
3. **Business logic:** Add audit logging, soft delete, self-delete prevention, and referential integrity checks during modernization
4. **Authentication:** Replace plain-text password storage with hashed/salted passwords
5. **Navigation:** The XCTL-based navigation maps to standard web routing/navigation patterns

---

## Appendix: All EXEC CICS Commands in COUSR03C

| # | Line | Command         | Full Parameters                                                                 | Paragraph              |
|---|------|-----------------|---------------------------------------------------------------------------------|------------------------|
| 1 | 134  | RETURN          | TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA)                                 | MAIN-PARA              |
| 2 | 205  | XCTL            | PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA)                           | RETURN-TO-PREV-SCREEN  |
| 3 | 219  | SEND MAP        | MAP('COUSR3A') MAPSET('COUSR03') FROM(COUSR3AO) ERASE CURSOR                   | SEND-USRDEL-SCREEN     |
| 4 | 232  | RECEIVE MAP     | MAP('COUSR3A') MAPSET('COUSR03') INTO(COUSR3AI) RESP(WS-RESP-CD) RESP2(WS-REAS-CD) | RECEIVE-USRDEL-SCREEN |
| 5 | 269  | READ            | DATASET(WS-USRSEC-FILE) INTO(SEC-USER-DATA) LENGTH(LENGTH OF SEC-USER-DATA) RIDFLD(SEC-USR-ID) KEYLENGTH(LENGTH OF SEC-USR-ID) UPDATE RESP(WS-RESP-CD) RESP2(WS-REAS-CD) | READ-USER-SEC-FILE |
| 6 | 307  | DELETE          | DATASET(WS-USRSEC-FILE) RESP(WS-RESP-CD) RESP2(WS-REAS-CD)                    | DELETE-USER-SEC-FILE   |

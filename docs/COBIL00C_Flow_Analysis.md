# CB00 Transaction Flow Analysis — COBIL00C (Bill Payment)

## Section 1: Flow Overview

| Attribute           | Value                                              |
|---------------------|----------------------------------------------------|
| **Transaction ID**  | CB00                                               |
| **Entry Program**   | COBIL00C                                           |
| **Function**        | Bill Payment — pay account balance in full and write a transaction record for the online bill payment |
| **Transaction Type**| CICS Online (pseudo-conversational)                |
| **CSD Definition**  | `DEFINE TRANSACTION(CB00) GROUP(CARDDEMO) PROGRAM(COBIL00C)` |

### Complete Navigation Path

```
 Terminal
   |
   |  CC00 (Transaction ID)
   v
 COSGN00C  -----> Sign-on screen
   |               - Validates user ID / password against USRSEC VSAM file
   |               - Sets CDEMO-USER-ID, CDEMO-USER-TYPE in COMMAREA
   |
   | XCTL (regular user)
   v
 COMEN01C  -----> Main Menu (CM00)
   |               - Displays 11 menu options from COMEN02Y table
   |               - Option 10 = "Bill Payment" -> COBIL00C
   |               - Sets CDEMO-FROM-TRANID=CM00, CDEMO-FROM-PROGRAM=COMEN01C
   |               - Resets CDEMO-PGM-CONTEXT=0
   |
   | XCTL (option 10)
   v
 COBIL00C  -----> Bill Payment (CB00)
                   - Displays bill payment screen (COBIL0A)
                   - Reads account balance from ACCTDAT
                   - Reads card cross-reference from CXACAIX
                   - Generates new transaction ID from TRANSACT (browse last)
                   - Writes bill payment transaction to TRANSACT
                   - Updates account balance in ACCTDAT
                   - Returns pseudo-conversationally with TRANSID('CB00')
```

### Pseudo-Conversational Lifecycle

```
  XCTL from COMEN01C (PGM-CONTEXT=0)
       |
       v
  [First Entry - CDEMO-PGM-ENTER]
       |
       +-- Check if CDEMO-CB00-TRN-SELECTED is set
       |     YES: Pre-fill account ID, call PROCESS-ENTER-KEY
       |     NO:  Display blank bill payment screen
       |
       +-- SEND MAP COBIL0A
       |
       +-- RETURN TRANSID('CB00') COMMAREA(...)
       |         (PGM-CONTEXT now = 1)
       |
       v
  [Re-Entry - CDEMO-PGM-REENTER]
       |
       +-- RECEIVE MAP COBIL0A
       |
       +-- EVALUATE EIBAID
       |     ENTER: PROCESS-ENTER-KEY
       |     PF3:   XCTL back to CDEMO-FROM-PROGRAM (or COMEN01C)
       |     PF4:   CLEAR-CURRENT-SCREEN
       |     OTHER: Error "Invalid key pressed"
       |
       +-- SEND MAP COBIL0A
       |
       +-- RETURN TRANSID('CB00') COMMAREA(...)
       |
       v
  [Loop continues until PF3 exits]
```

---

## Section 2: Programs Involved

| # | Program    | Type            | Function                          | Called By  | Call Method        |
|---|------------|-----------------|-----------------------------------|------------|--------------------|
| 1 | COSGN00C   | CICS Online     | Sign-on / Authentication          | Terminal   | Transaction CC00   |
| 2 | COMEN01C   | CICS Online     | Main Menu (Regular Users)         | COSGN00C   | XCTL               |
| 3 | COBIL00C   | CICS Online     | Bill Payment                      | COMEN01C   | XCTL (option 10)   |

**Notes:**
- COBIL00C does not issue any CALL, LINK, or XCTL to other application programs during normal processing
- COBIL00C uses XCTL only to return to the previous screen (CDEMO-TO-PROGRAM, which resolves to COMEN01C or COSGN00C)
- No Language Environment intrinsics or external sub-programs are called

---

## Section 3: Copybooks

| # | Copybook   | Used By           | Type                    | Purpose                                          |
|---|------------|-------------------|-------------------------|--------------------------------------------------|
| 1 | COCOM01Y   | COBIL00C, COMEN01C, COSGN00C | COMMAREA definition | Shared communication area for all CardDemo programs |
| 2 | COBIL00    | COBIL00C          | BMS symbolic map        | Auto-generated symbolic map for COBIL0A screen (input COBIL0AI / output COBIL0AO structures) |
| 3 | COTTL01Y   | COBIL00C, COMEN01C, COSGN00C | Screen title data | Application title lines (CCDA-TITLE01, CCDA-TITLE02) |
| 4 | CSDAT01Y   | COBIL00C, COMEN01C, COSGN00C | Date/time structure | Current date/time formatting (WS-CURDATE-DATA, WS-TIMESTAMP) |
| 5 | CSMSG01Y   | COBIL00C, COMEN01C, COSGN00C | Common messages   | Shared messages (CCDA-MSG-THANK-YOU, CCDA-MSG-INVALID-KEY) |
| 6 | CVACT01Y   | COBIL00C          | VSAM record layout      | Account record (ACCOUNT-RECORD, 300 bytes) for ACCTDAT file |
| 7 | CVACT03Y   | COBIL00C          | VSAM record layout      | Card cross-reference record (CARD-XREF-RECORD, 50 bytes) for CCXREF/CXACAIX |
| 8 | CVTRA05Y   | COBIL00C          | VSAM record layout      | Transaction record (TRAN-RECORD, 350 bytes) for TRANSACT file |
| 9 | DFHAID     | COBIL00C, COMEN01C, COSGN00C | CICS system copybook | AID key constants (DFHENTER, DFHPF3, DFHPF4, etc.) |
| 10| DFHBMSCA   | COBIL00C, COMEN01C, COSGN00C | CICS system copybook | BMS attribute constants (DFHGREEN, DFHRED, etc.) |
| 11| COMEN02Y   | COMEN01C          | Menu option table       | Main menu options data — option 10 maps to COBIL00C |
| 12| CSUSR01Y   | COMEN01C, COSGN00C | VSAM record layout     | User security record (SEC-USER-DATA, 80 bytes) for USRSEC file |

### Record Layout: ACCOUNT-RECORD (CVACT01Y — 300 bytes)

```
Level  Field                    PIC             Offset  Length  Purpose
-----  -----                    ---             ------  ------  -------
01     ACCOUNT-RECORD                           0       300     Full account record
  05   ACCT-ID                  9(11)           0       11      Account ID (primary key)
  05   ACCT-ACTIVE-STATUS       X(01)           11      1       Active status flag
  05   ACCT-CURR-BAL            S9(10)V99       12      12      Current balance (signed, 2 dec)
  05   ACCT-CREDIT-LIMIT        S9(10)V99       24      12      Credit limit
  05   ACCT-CASH-CREDIT-LIMIT   S9(10)V99       36      12      Cash credit limit
  05   ACCT-OPEN-DATE           X(10)           48      10      Account open date
  05   ACCT-EXPIRAION-DATE      X(10)           58      10      Expiration date
  05   ACCT-REISSUE-DATE        X(10)           68      10      Reissue date
  05   ACCT-CURR-CYC-CREDIT     S9(10)V99       78      12      Current cycle credit
  05   ACCT-CURR-CYC-DEBIT      S9(10)V99       90      12      Current cycle debit
  05   ACCT-ADDR-ZIP            X(10)           102     10      ZIP code
  05   ACCT-GROUP-ID            X(10)           112     10      Group ID
  05   FILLER                   X(178)          122     178     Reserved
```

### Record Layout: CARD-XREF-RECORD (CVACT03Y — 50 bytes)

```
Level  Field                    PIC             Offset  Length  Purpose
-----  -----                    ---             ------  ------  -------
01     CARD-XREF-RECORD                         0       50      Card cross-reference
  05   XREF-CARD-NUM            X(16)           0       16      Card number
  05   XREF-CUST-ID             9(09)           16      9       Customer ID
  05   XREF-ACCT-ID             9(11)           25      11      Account ID (alt key for CXACAIX)
  05   FILLER                   X(14)           36      14      Reserved
```

### Record Layout: TRAN-RECORD (CVTRA05Y — 350 bytes)

```
Level  Field                    PIC             Offset  Length  Purpose
-----  -----                    ---             ------  ------  -------
01     TRAN-RECORD                               0       350     Transaction record
  05   TRAN-ID                  X(16)           0       16      Transaction ID (primary key)
  05   TRAN-TYPE-CD             X(02)           16      2       Type code ('02' = bill pay)
  05   TRAN-CAT-CD              9(04)           18      4       Category code (2 = bill pay)
  05   TRAN-SOURCE              X(10)           22      10      Source ('POS TERM')
  05   TRAN-DESC                X(100)          32      100     Description
  05   TRAN-AMT                 S9(09)V99       132     11      Transaction amount
  05   TRAN-MERCHANT-ID         9(09)           143     9       Merchant ID
  05   TRAN-MERCHANT-NAME       X(50)           152     50      Merchant name
  05   TRAN-MERCHANT-CITY       X(50)           202     50      Merchant city
  05   TRAN-MERCHANT-ZIP        X(10)           252     10      Merchant ZIP
  05   TRAN-CARD-NUM            X(16)           262     16      Card number
  05   TRAN-ORIG-TS             X(26)           278     26      Origination timestamp
  05   TRAN-PROC-TS             X(26)           304     26      Processing timestamp
  05   FILLER                   X(20)           330     20      Reserved
```

---

## Section 4: VSAM File Operations

| # | File (DD)  | Key Field       | Record Copybook | Record Length | Access Pattern        |
|---|------------|-----------------|-----------------|---------------|-----------------------|
| 1 | ACCTDAT    | ACCT-ID (9(11)) | CVACT01Y        | 300           | READ UPDATE + REWRITE |
| 2 | CXACAIX    | XREF-ACCT-ID (9(11)) | CVACT03Y  | 50            | READ (AIX path)       |
| 3 | TRANSACT   | TRAN-ID (X(16)) | CVTRA05Y        | 350           | STARTBR + READPREV + ENDBR + WRITE |

### Detailed File Operations

#### ACCTDAT (Account Master)
| Operation | Paragraph             | Key         | Purpose                              | Error Handling |
|-----------|-----------------------|-------------|--------------------------------------|----------------|
| READ UPDATE | READ-ACCTDAT-FILE   | ACCT-ID     | Read account for balance & hold lock  | NOTFND: "Account ID NOT found..." / OTHER: "Unable to lookup Account..." |
| REWRITE   | UPDATE-ACCTDAT-FILE   | (held record)| Update balance after payment         | NOTFND: "Account ID NOT found..." / OTHER: "Unable to Update Account..." |

#### CXACAIX (Card-Account Alternate Index)
| Operation | Paragraph             | Key            | Purpose                           | Error Handling |
|-----------|-----------------------|----------------|-----------------------------------|----------------|
| READ      | READ-CXACAIX-FILE     | XREF-ACCT-ID   | Look up card number for account   | NOTFND: "Account ID NOT found..." / OTHER: "Unable to lookup XREF AIX file..." |

#### TRANSACT (Transaction File)
| Operation | Paragraph                | Key      | Purpose                           | Error Handling |
|-----------|--------------------------|----------|-----------------------------------|----------------|
| STARTBR   | STARTBR-TRANSACT-FILE   | TRAN-ID (HIGH-VALUES) | Position to end of file  | NOTFND: "Transaction ID NOT found..." / OTHER: "Unable to lookup Transaction..." |
| READPREV  | READPREV-TRANSACT-FILE  | TRAN-ID  | Read last transaction to get max ID| ENDFILE: Set TRAN-ID to ZEROS / OTHER: error |
| ENDBR     | ENDBR-TRANSACT-FILE     | —        | End browse session                 | None |
| WRITE     | WRITE-TRANSACT-FILE     | TRAN-ID  | Write new bill payment transaction | NORMAL: success message / DUPKEY/DUPREC: "Tran ID already exist..." / OTHER: "Unable to Add Bill pay Transaction..." |

### VSAM File Flow Diagram

```
                        COBIL00C
                           |
        +------------------+------------------+
        |                  |                  |
        v                  v                  v
   [ACCTDAT]          [CXACAIX]          [TRANSACT]
                                              |
   1. READ UPDATE      1. READ           1. STARTBR (HIGH-VALUES)
      (by ACCT-ID)        (by XREF-       2. READPREV (get last ID)
      -> get balance       ACCT-ID)       3. ENDBR
                          -> get card#    4. WRITE (new tran record)
   2. REWRITE                                 |
      (zero balance)                          v
                                    New TRAN-ID = last + 1
                                    TRAN-TYPE-CD = '02'
                                    TRAN-AMT = current balance
                                    TRAN-CARD-NUM from CXACAIX
```

---

## Section 5: BMS Screen Map

### Mapset: COBIL00 / Map: COBIL0A

**BMS Source:** `app/bms/COBIL00.bms`
**Screen Size:** 24 rows x 80 columns
**Attributes:** CTRL=(ALARM,FREEKB), EXTATT=YES, LANG=COBOL, MODE=INOUT, STORAGE=AUTO

### ASCII Screen Layout

```
Row Col  Content
--- ---  -------
 1   1   Tran: CB00           AWS Mainframe Modernization            Date: mm/dd/yy
 2   1   Prog: COBIL00C                    CardDemo                  Time: hh:mm:ss
 3
 4  35                        Bill Payment
 5
 6   6   Enter Acct ID: ___________
 7
 8   6   -----------------------------------------------------------------------
 9
10
11   6   Your current balance is:  +9999999999.99
12
13
14
15   6   Do you want to pay your balance now. Please confirm:  _ (Y/N)
16
...
23   1   <error/success message — 78 chars, RED or GREEN>
24   1   ENTER=Continue  F3=Back  F4=Clear
```

### Field Inventory

| # | Field Name | Row | Col | Length | Attr             | Color    | Purpose                        |
|---|------------|-----|-----|--------|------------------|----------|--------------------------------|
| 1 | TRNNAME    | 1   | 7   | 4      | ASKIP,FSET,NORM  | BLUE     | Transaction ID (CB00)          |
| 2 | TITLE01    | 1   | 21  | 40     | ASKIP,FSET,NORM  | YELLOW   | Title line 1                   |
| 3 | CURDATE    | 1   | 71  | 8      | ASKIP,FSET,NORM  | BLUE     | Current date (mm/dd/yy)        |
| 4 | PGMNAME    | 2   | 7   | 8      | ASKIP,FSET,NORM  | BLUE     | Program name (COBIL00C)        |
| 5 | TITLE02    | 2   | 21  | 40     | ASKIP,FSET,NORM  | YELLOW   | Title line 2                   |
| 6 | CURTIME    | 2   | 71  | 8      | ASKIP,FSET,NORM  | BLUE     | Current time (hh:mm:ss)        |
| 7 | *(literal)*| 4   | 35  | 12     | ASKIP,BRT        | NEUTRAL  | "Bill Payment" heading         |
| 8 | ACTIDIN    | 6   | 21  | 11     | FSET,IC,UNPROT   | GREEN    | **Account ID input** (underlined) |
| 9 | CURBAL     | 11  | 32  | 14     | ASKIP,FSET,NORM  | BLUE     | Current balance display         |
| 10| CONFIRM    | 15  | 60  | 1      | FSET,UNPROT      | GREEN    | **Confirmation input** (Y/N, underlined) |
| 11| ERRMSG     | 23  | 1   | 78     | ASKIP,BRT,FSET   | RED      | Error/success message area      |

### Input Fields (UNPROT)

| Field   | Length | Validation                                                    |
|---------|--------|---------------------------------------------------------------|
| ACTIDIN | 11     | Must not be empty; must be a valid account ID in ACCTDAT      |
| CONFIRM | 1      | Valid values: Y, y, N, n, SPACES; other values rejected       |

### Function Key Assignments

| Key   | Action                                            | Implementation                    |
|-------|---------------------------------------------------|-----------------------------------|
| ENTER | Process bill payment (validate, display, confirm) | PROCESS-ENTER-KEY paragraph       |
| PF3   | Return to previous screen                         | XCTL to CDEMO-FROM-PROGRAM or COMEN01C |
| PF4   | Clear all input fields                            | CLEAR-CURRENT-SCREEN paragraph    |
| Other | Display error message                             | "Invalid key pressed..."          |

---

## Section 6: COMMAREA Structure

### CARDDEMO-COMMAREA (COCOM01Y)

```
01 CARDDEMO-COMMAREA
   05 CDEMO-GENERAL-INFO
      10 CDEMO-FROM-TRANID          PIC X(04)    -- Source transaction ID
      10 CDEMO-FROM-PROGRAM         PIC X(08)    -- Source program name
      10 CDEMO-TO-TRANID            PIC X(04)    -- Target transaction ID
      10 CDEMO-TO-PROGRAM           PIC X(08)    -- Target program name
      10 CDEMO-USER-ID              PIC X(08)    -- Logged-in user ID
      10 CDEMO-USER-TYPE            PIC X(01)    -- 'A'=Admin, 'U'=User
      10 CDEMO-PGM-CONTEXT          PIC 9(01)    -- 0=Enter, 1=Reenter
   05 CDEMO-CUSTOMER-INFO
      10 CDEMO-CUST-ID              PIC 9(09)    -- Customer ID
      10 CDEMO-CUST-FNAME           PIC X(25)    -- First name
      10 CDEMO-CUST-MNAME           PIC X(25)    -- Middle name
      10 CDEMO-CUST-LNAME           PIC X(25)    -- Last name
   05 CDEMO-ACCOUNT-INFO
      10 CDEMO-ACCT-ID              PIC 9(11)    -- Account ID
      10 CDEMO-ACCT-STATUS          PIC X(01)    -- Account status
   05 CDEMO-CARD-INFO
      10 CDEMO-CARD-NUM             PIC 9(16)    -- Card number
   05 CDEMO-MORE-INFO
      10 CDEMO-LAST-MAP             PIC X(7)     -- Last map name
      10 CDEMO-LAST-MAPSET          PIC X(7)     -- Last mapset name
```

### Program-Specific Extension: CDEMO-CB00-INFO (in COBIL00C WORKING-STORAGE)

```
   05 CDEMO-CB00-INFO
      10 CDEMO-CB00-TRNID-FIRST     PIC X(16)    -- First tran ID (for paging)
      10 CDEMO-CB00-TRNID-LAST      PIC X(16)    -- Last tran ID (for paging)
      10 CDEMO-CB00-PAGE-NUM        PIC 9(08)    -- Page number
      10 CDEMO-CB00-NEXT-PAGE-FLG   PIC X(01)    -- 'Y'/'N' next page flag
      10 CDEMO-CB00-TRN-SEL-FLG     PIC X(01)    -- Transaction selected flag
      10 CDEMO-CB00-TRN-SELECTED    PIC X(16)    -- Selected transaction ID
```

### COMMAREA Field Flow

```
Program     | Fields Read                          | Fields Written
------------|--------------------------------------|----------------------------------
COSGN00C    | (none - initial entry)               | CDEMO-FROM-TRANID = 'CC00'
            |                                      | CDEMO-FROM-PROGRAM = 'COSGN00C'
            |                                      | CDEMO-USER-ID (from login)
            |                                      | CDEMO-USER-TYPE (from USRSEC)
            |                                      | CDEMO-PGM-CONTEXT = 0
------------|--------------------------------------|----------------------------------
COMEN01C    | CDEMO-PGM-CONTEXT (enter/reenter)    | CDEMO-FROM-TRANID = 'CM00'
            | CDEMO-USER-TYPE (access check)       | CDEMO-FROM-PROGRAM = 'COMEN01C'
            | CDEMO-MENU-OPT-PGMNAME(10)           | CDEMO-PGM-CONTEXT = 0
------------|--------------------------------------|----------------------------------
COBIL00C    | CDEMO-PGM-CONTEXT (enter/reenter)    | CDEMO-PGM-CONTEXT = 1 (on enter)
            | CDEMO-FROM-PROGRAM (for PF3 return)  | CDEMO-TO-PROGRAM (on PF3)
            | CDEMO-CB00-TRN-SELECTED (pre-fill)   | CDEMO-FROM-TRANID = 'CB00' (on exit)
            |                                      | CDEMO-FROM-PROGRAM = 'COBIL00C' (on exit)
            |                                      | CDEMO-PGM-CONTEXT = 0 (on exit)
```

### COMMAREA Inter-Program Flow Diagram

```
  COSGN00C                  COMEN01C                  COBIL00C
  --------                  --------                  --------
  Sets:                     Sets:                     Reads:
  - FROM-TRANID='CC00'      - FROM-TRANID='CM00'      - PGM-CONTEXT
  - FROM-PROGRAM='COSGN00C' - FROM-PROGRAM='COMEN01C'  - FROM-PROGRAM
  - USER-ID                 - PGM-CONTEXT=0            - CB00-TRN-SELECTED
  - USER-TYPE                                         Sets:
  - PGM-CONTEXT=0                                     - PGM-CONTEXT=1 (reenter)
       |                         |                    - TO-PROGRAM (on PF3)
       | XCTL + COMMAREA         | XCTL + COMMAREA    - FROM-TRANID='CB00' (exit)
       +------------------------>+-------------------->- FROM-PROGRAM='COBIL00C'
                                                      - PGM-CONTEXT=0 (exit)
```

---

## Section 7: Business Logic Flow

### Step-by-Step Narrative

#### 1. Entry and Initialization (MAIN-PARA)

1. Reset error flag (`ERR-FLG-OFF`) and user-modified flag (`USR-MODIFIED-NO`)
2. Clear message areas (`WS-MESSAGE`, `ERRMSGO`)
3. **EIBCALEN = 0 check:** If no COMMAREA passed (direct terminal entry), redirect to sign-on screen (`COSGN00C`) via XCTL
4. Copy DFHCOMMAREA into `CARDDEMO-COMMAREA`

#### 2. First Entry (PGM-CONTEXT = 0)

5. Set `CDEMO-PGM-REENTER` (PGM-CONTEXT = 1) for subsequent iterations
6. Initialize screen fields to LOW-VALUES
7. Set cursor to Account ID field (`ACTIDINL = -1`)
8. **Pre-fill check:** If `CDEMO-CB00-TRN-SELECTED` contains a value (not SPACES/LOW-VALUES), move it to the Account ID input field and immediately call `PROCESS-ENTER-KEY` to look up the account
9. Send the bill payment screen (`SEND MAP COBIL0A`)

#### 3. Re-Entry (PGM-CONTEXT = 1)

10. Receive user input from screen (`RECEIVE MAP COBIL0A`)
11. Evaluate the AID key pressed:
    - **ENTER** -> `PROCESS-ENTER-KEY`
    - **PF3** -> Return to calling program (XCTL)
    - **PF4** -> Clear all fields
    - **Other** -> Display "Invalid key pressed" error

#### 4. Processing Enter Key (PROCESS-ENTER-KEY)

12. Reset confirmation flag (`CONF-PAY-NO`)
13. **Validate Account ID:** If ACTIDIN is empty -> error "Acct ID can NOT be empty..."
14. Move Account ID to `ACCT-ID` and `XREF-ACCT-ID`
15. **Evaluate Confirmation field:**
    - `Y` or `y`: Set `CONF-PAY-YES`, read account record
    - `N` or `n`: Clear screen, set error flag
    - SPACES/LOW-VALUES (first time): Read account record (display balance only)
    - Other: Error "Invalid value. Valid values are (Y/N)..."
16. Display current balance: `ACCT-CURR-BAL` -> `WS-CURR-BAL` -> `CURBALI`

#### 5. Balance Validation

17. If `ACCT-CURR-BAL <= 0` -> error "You have nothing to pay..."

#### 6. Payment Processing (when CONF-PAY-YES)

18. **Read CXACAIX:** Look up card number for the account via alternate index
19. **Generate new Transaction ID:**
    - Set `TRAN-ID = HIGH-VALUES`
    - STARTBR on TRANSACT file (positions to end)
    - READPREV to get the last (highest) transaction ID
    - ENDBR to end browse
    - Increment last ID by 1 for new transaction
20. **Build transaction record:**
    - `TRAN-ID` = new sequential ID
    - `TRAN-TYPE-CD` = '02' (bill payment type)
    - `TRAN-CAT-CD` = 2 (bill payment category)
    - `TRAN-SOURCE` = 'POS TERM'
    - `TRAN-DESC` = 'BILL PAYMENT - ONLINE'
    - `TRAN-AMT` = current account balance
    - `TRAN-CARD-NUM` = card number from CXACAIX lookup
    - `TRAN-MERCHANT-ID` = 999999999
    - `TRAN-MERCHANT-NAME` = 'BILL PAYMENT'
    - `TRAN-MERCHANT-CITY` = 'N/A'
    - `TRAN-MERCHANT-ZIP` = 'N/A'
21. **Get timestamp:** ASKTIME + FORMATTIME to get current date/time
    - Set `TRAN-ORIG-TS` and `TRAN-PROC-TS` to formatted timestamp
22. **Write transaction:** WRITE to TRANSACT file
23. **Update account balance:** `ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT` (zeroes out balance)
24. **Rewrite account:** REWRITE to ACCTDAT file
25. On success: Display green message "Payment successful. Your Transaction ID is {ID}."

#### 7. Non-Confirmation Path (CONF-PAY-NO, no confirm yet)

26. Display message: "Confirm to make a bill payment..."
27. Set cursor to Confirm field

#### 8. Error Handling Paths

| Condition                         | Message                                    | Cursor To  |
|-----------------------------------|--------------------------------------------|------------|
| Empty Account ID                  | "Acct ID can NOT be empty..."              | ACTIDIN    |
| Account not found (ACCTDAT)       | "Account ID NOT found..."                  | ACTIDIN    |
| Account lookup error (ACCTDAT)    | "Unable to lookup Account..."              | ACTIDIN    |
| Zero/negative balance             | "You have nothing to pay..."               | ACTIDIN    |
| Invalid confirm value             | "Invalid value. Valid values are (Y/N)..." | CONFIRM    |
| CXACAIX not found                 | "Account ID NOT found..."                  | ACTIDIN    |
| CXACAIX lookup error              | "Unable to lookup XREF AIX file..."        | ACTIDIN    |
| Transaction browse error          | "Transaction ID NOT found..." / "Unable to lookup Transaction..." | ACTIDIN |
| Duplicate transaction ID          | "Tran ID already exist..."                 | ACTIDIN    |
| Transaction write error           | "Unable to Add Bill pay Transaction..."    | ACTIDIN    |
| Account update not found          | "Account ID NOT found..."                  | ACTIDIN    |
| Account update error              | "Unable to Update Account..."              | ACTIDIN    |
| Invalid key pressed               | "Invalid key pressed. Please see below..." | (current)  |

---

## Section 8: Complete File Inventory

### COBOL Source Files

| # | File                   | Path                        | Purpose                    |
|---|------------------------|-----------------------------|----------------------------|
| 1 | COBIL00C.cbl           | `app/cbl/COBIL00C.cbl`     | Bill Payment (target)      |
| 2 | COMEN01C.cbl           | `app/cbl/COMEN01C.cbl`     | Main Menu (upstream)       |
| 3 | COSGN00C.cbl           | `app/cbl/COSGN00C.cbl`     | Sign-on (upstream)         |

### Copybook Files

| # | File                   | Path                        | Purpose                    |
|---|------------------------|-----------------------------|----------------------------|
| 1 | COCOM01Y.cpy           | `app/cpy/COCOM01Y.cpy`     | COMMAREA definition        |
| 2 | COTTL01Y.cpy           | `app/cpy/COTTL01Y.cpy`     | Screen titles              |
| 3 | CSDAT01Y.cpy           | `app/cpy/CSDAT01Y.cpy`     | Date/time structure        |
| 4 | CSMSG01Y.cpy           | `app/cpy/CSMSG01Y.cpy`     | Common messages            |
| 5 | CVACT01Y.cpy           | `app/cpy/CVACT01Y.cpy`     | Account record layout      |
| 6 | CVACT03Y.cpy           | `app/cpy/CVACT03Y.cpy`     | Card XREF record layout    |
| 7 | CVTRA05Y.cpy           | `app/cpy/CVTRA05Y.cpy`     | Transaction record layout  |
| 8 | COMEN02Y.cpy           | `app/cpy/COMEN02Y.cpy`     | Menu options table         |

### BMS Symbolic Map Copybook (Auto-Generated)

| # | File                   | Path                        | Purpose                    |
|---|------------------------|-----------------------------|----------------------------|
| 1 | COBIL00                | *(BMS-generated, not in app/cpy/)* | Symbolic map for COBIL0A (COBIL0AI input / COBIL0AO output) |

### CICS System Copybooks

| # | File                   | Path                        | Purpose                    |
|---|------------------------|-----------------------------|----------------------------|
| 1 | DFHAID                 | *(IBM system copybook)*     | AID key constants          |
| 2 | DFHBMSCA               | *(IBM system copybook)*     | BMS attribute constants    |

### BMS Map Files

| # | File                   | Path                        | Mapset  | Map     |
|---|------------------------|-----------------------------|---------|---------|
| 1 | COBIL00.bms            | `app/bms/COBIL00.bms`       | COBIL00 | COBIL0A |

### VSAM Data Files

| # | DD Name    | Key Field      | Record Length | Record Copybook | Description             |
|---|------------|----------------|---------------|-----------------|-------------------------|
| 1 | ACCTDAT    | ACCT-ID        | 300           | CVACT01Y        | Account master          |
| 2 | CXACAIX    | XREF-ACCT-ID   | 50            | CVACT03Y        | Card-account AIX path   |
| 3 | TRANSACT   | TRAN-ID        | 350           | CVTRA05Y        | Transaction file        |

### CSD Definitions

| Resource    | Name     | Group     | Associated Program |
|-------------|----------|-----------|--------------------|
| TRANSACTION | CB00     | CARDDEMO  | COBIL00C           |

---

## Section 9: Transaction Record Layout

When COBIL00C processes a bill payment, it writes the following record to the TRANSACT file:

### Bill Payment Transaction Record (TRAN-RECORD — 350 bytes)

```
Offset  Length  Field               Value Set By COBIL00C
------  ------  -----               ----------------------------
0       16      TRAN-ID             Sequential (max existing + 1)
16      2       TRAN-TYPE-CD        '02'
18      4       TRAN-CAT-CD         0002
22      10      TRAN-SOURCE         'POS TERM  '
32      100     TRAN-DESC           'BILL PAYMENT - ONLINE' + spaces
132     11      TRAN-AMT            ACCT-CURR-BAL (full balance)
143     9       TRAN-MERCHANT-ID    999999999
152     50      TRAN-MERCHANT-NAME  'BILL PAYMENT' + spaces
202     50      TRAN-MERCHANT-CITY  'N/A' + spaces
252     10      TRAN-MERCHANT-ZIP   'N/A' + spaces
262     16      TRAN-CARD-NUM       From CXACAIX lookup (XREF-CARD-NUM)
278     26      TRAN-ORIG-TS        YYYY-MM-DD HH:MM:SS.000000
304     26      TRAN-PROC-TS        YYYY-MM-DD HH:MM:SS.000000
330     20      FILLER              Spaces (initialized)
```

**Post-write account update:** `ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT` (effectively zeroes the balance since TRAN-AMT = ACCT-CURR-BAL)

---

## Section 10: Observations

### What the Flow Demonstrates Well

1. **Clean pseudo-conversational pattern:** Clear separation between first entry (PGM-CONTEXT=0) and re-entry (PGM-CONTEXT=1) with proper RETURN TRANSID
2. **Confirmation workflow:** Two-step process — first displays balance, then requires Y/N confirmation before processing payment
3. **Sequential ID generation:** Uses STARTBR/READPREV to find the highest existing transaction ID and increments by 1
4. **Comprehensive error handling:** Every EXEC CICS I/O command checks RESP/RESP2 with specific error messages
5. **Standard CardDemo navigation:** Follows the shared COMMAREA pattern with FROM-TRANID/FROM-PROGRAM/TO-PROGRAM fields
6. **VSAM alternate index usage:** Uses CXACAIX (alternate index path) to look up card number by account ID rather than card number

### Gaps and Migration Considerations

1. **No explicit SYNCPOINT/COMMIT:** The WRITE and REWRITE are separate operations with no explicit SYNCPOINT. If the WRITE succeeds but REWRITE fails, the transaction record exists but the balance is unchanged. In CICS, the implicit syncpoint at task end handles this, but a migration target may need explicit transaction boundaries.
2. **Race condition on ID generation:** The STARTBR/READPREV/ENDBR + WRITE sequence to generate a new transaction ID is not atomic. Two concurrent bill payments could generate the same ID. The DUPKEY/DUPREC handling mitigates this but does not retry.
3. **Full balance payment only:** The program always pays the entire `ACCT-CURR-BAL` — there is no partial payment option. Migration should preserve this constraint or consider adding partial payment.
4. **No audit trail fields:** The transaction record does not capture the user who made the payment (`CDEMO-USER-ID` is not written to the TRAN-RECORD).
5. **Hard-coded values:** Merchant ID (999999999), source ('POS TERM'), and description are hard-coded literals. These should be externalized in a migration target.
6. **READ UPDATE lock duration:** The account record is locked (READ UPDATE) from the point of balance lookup through the entire payment processing. In high-concurrency environments, this could cause contention.
7. **BMS symbolic map not in repository:** The `COPY COBIL00` copybook is auto-generated from `COBIL00.bms` and not present in `app/cpy/`. The migration toolchain must generate or manually create this symbolic map.

---

## Section 11: Comparison — Flow Complexity Summary

| Dimension                  | CB00 (COBIL00C) Bill Payment              |
|----------------------------|--------------------------------------------|
| **Programs in path**       | 3 (COSGN00C -> COMEN01C -> COBIL00C)      |
| **VSAM files accessed**    | 3 (ACCTDAT, CXACAIX, TRANSACT)            |
| **EXEC CICS commands**     | 12 total in COBIL00C                       |
| **Copybooks**              | 10 (7 application + 1 BMS symbolic + 2 system) |
| **Screen maps**            | 1 (COBIL0A in COBIL00 mapset)             |
| **Input fields**           | 2 (Account ID, Confirm Y/N)               |
| **Function keys**          | 3 (ENTER, PF3, PF4)                       |
| **CALL sub-programs**      | 0                                          |
| **LINK sub-programs**      | 0                                          |
| **Business rules**         | Balance > 0 check, Y/N confirmation       |
| **Error messages**         | 12 distinct error conditions               |
| **Complexity**             | **Low-Medium** — straightforward single-screen payment with no sub-program calls, no DB2, no complex validation |

### EXEC CICS Command Summary (COBIL00C)

| # | Command      | Location (Paragraph)           | Parameters                          |
|---|-------------|--------------------------------|-------------------------------------|
| 1 | RETURN       | MAIN-PARA                      | TRANSID('CB00'), COMMAREA           |
| 2 | SEND MAP     | SEND-BILLPAY-SCREEN            | MAP('COBIL0A'), MAPSET('COBIL00'), FROM(COBIL0AO), ERASE, CURSOR |
| 3 | RECEIVE MAP  | RECEIVE-BILLPAY-SCREEN         | MAP('COBIL0A'), MAPSET('COBIL00'), INTO(COBIL0AI), RESP, RESP2 |
| 4 | READ         | READ-ACCTDAT-FILE              | DATASET(ACCTDAT), INTO(ACCOUNT-RECORD), RIDFLD(ACCT-ID), UPDATE, RESP, RESP2 |
| 5 | REWRITE      | UPDATE-ACCTDAT-FILE            | DATASET(ACCTDAT), FROM(ACCOUNT-RECORD), RESP, RESP2 |
| 6 | READ         | READ-CXACAIX-FILE              | DATASET(CXACAIX), INTO(CARD-XREF-RECORD), RIDFLD(XREF-ACCT-ID), RESP, RESP2 |
| 7 | STARTBR      | STARTBR-TRANSACT-FILE          | DATASET(TRANSACT), RIDFLD(TRAN-ID), RESP, RESP2 |
| 8 | READPREV     | READPREV-TRANSACT-FILE         | DATASET(TRANSACT), INTO(TRAN-RECORD), RIDFLD(TRAN-ID), RESP, RESP2 |
| 9 | ENDBR        | ENDBR-TRANSACT-FILE            | DATASET(TRANSACT)                   |
| 10| WRITE        | WRITE-TRANSACT-FILE            | DATASET(TRANSACT), FROM(TRAN-RECORD), RIDFLD(TRAN-ID), RESP, RESP2 |
| 11| ASKTIME      | GET-CURRENT-TIMESTAMP          | ABSTIME(WS-ABS-TIME)               |
| 12| FORMATTIME   | GET-CURRENT-TIMESTAMP          | ABSTIME, YYYYMMDD, DATESEP, TIME, TIMESEP |
| 13| XCTL         | RETURN-TO-PREV-SCREEN          | PROGRAM(CDEMO-TO-PROGRAM), COMMAREA |

# CCDL Transaction Flow Analysis — COCRDSLC (Credit Card Detail / Search)

## Section 1: Flow Overview

| Attribute            | Value                                              |
|----------------------|----------------------------------------------------|
| **Transaction ID**   | CCDL                                               |
| **Entry Program**    | COCRDSLC                                           |
| **Function**         | Accept and process credit card detail request       |
| **Type**             | Pseudo-conversational CICS online program           |
| **CSD Definition**   | `DEFINE PROGRAM(COCRDSLC) ... TRANSID(CCDL)`       |
| **CSD Transaction**  | `DEFINE TRANSACTION(CCDL) ... PROGRAM(COCRDSLC)`   |

### Complete Navigation Path

```
COSGN00C (CC00 Sign-on)
    |
    |  XCTL (user login successful, regular user)
    v
COMEN01C (CM00 Main Menu)
    |
    |  XCTL via menu option 4 "Credit Card View"
    v
COCRDSLC (CCDL Credit Card Detail / Search)  <-- TARGET
    ^
    |  XCTL (select 'S' on a row)
    |
COCRDLIC (CCLI Credit Card List)
    ^
    |  XCTL via menu option 3 "Credit Card List"
    |
COMEN01C (CM00 Main Menu)
```

Additional upstream programs that define COCRDSLC as a target:
- **COACTVWC** — Account View (defines LIT-CARDDTLPGM = 'COCRDSLC')
- **COACTUPC** — Account Update (defines LIT-CARDDTLPGM = 'COCRDSLC')

### PF3 Return Path (from COCRDSLC)

```
COCRDSLC
    |
    |  XCTL (PF3) --> CDEMO-FROM-PROGRAM (calling program)
    |                  or COMEN01C if no calling program set
    v
COCRDLIC / COMEN01C / (original caller)
```

### Pseudo-Conversational Lifecycle

```
  First Entry (PGM-CONTEXT = 0)
  +-----------------------------------------+
  |  From COCRDLIC with account/card?       |
  |  YES: Read CARDDAT, send map with data  |
  |  NO:  Send blank search form            |
  +-------------------+---------------------+
                      |
                      v
            RETURN TRANSID('CCDL')
            (program suspends)
                      |
                      v
  Re-Entry (PGM-CONTEXT = 1)
  +-----------------------------------------+
  |  RECEIVE MAP (user input)               |
  |  Validate account/card numbers          |
  |  If valid: READ CARDDAT, send results   |
  |  If invalid: send error, re-display     |
  +-------------------+---------------------+
                      |
                      v
            RETURN TRANSID('CCDL')
            (cycle repeats until PF3)
```

---

## Section 2: Programs Involved

| Program    | Type        | Function                              | Caller(s)              | Call Method       |
|------------|-------------|---------------------------------------|------------------------|-------------------|
| COSGN00C   | CICS online | Sign-on screen                        | (terminal entry CC00)  | Initial TRANSID   |
| COMEN01C   | CICS online | Main Menu (regular users)             | COSGN00C               | XCTL              |
| COCRDLIC   | CICS online | Credit Card List                      | COMEN01C               | XCTL (option 3)   |
| COCRDSLC   | CICS online | Credit Card Detail / Search (TARGET)  | COMEN01C, COCRDLIC, COACTVWC, COACTUPC | XCTL |
| COACTVWC   | CICS online | Account View                          | COMEN01C               | XCTL (option 1)   |
| COACTUPC   | CICS online | Account Update                        | COMEN01C               | XCTL (option 2)   |

> **Note:** COCRDSLC contains no CALL statements and no LINK commands. The only inter-program transfers are XCTL (to the calling program or menu on PF3).

---

## Section 3: Copybooks

| Copybook    | Used By         | Type                    | Purpose / Record Layout Summary |
|-------------|-----------------|-------------------------|---------------------------------|
| CVCRD01Y    | COCRDSLC        | Work area               | CC-WORK-AREAS: AID key flags (CCARD-AID-ENTER/PFK03 etc.), navigation fields (CCARD-NEXT-PROG, CCARD-NEXT-MAPSET, CCARD-NEXT-MAP), error/return messages, account/card/customer ID fields |
| COCOM01Y    | COCRDSLC, COMEN01C, COCRDLIC, COSGN00C | COMMAREA definition | CARDDEMO-COMMAREA: navigation (FROM-TRANID/PROGRAM, TO-TRANID/PROGRAM), user info (USER-ID, USER-TYPE), PGM-CONTEXT (0=enter, 1=reenter), customer/account/card info, last map/mapset |
| DFHBMSCA    | COCRDSLC        | CICS system copybook    | BMS attribute constants (DFHBMPRF, DFHBMFSE, DFHBMDAR, DFHRED, DFHNEUTR, DFHDFCOL, etc.) |
| DFHAID      | COCRDSLC        | CICS system copybook    | AID key constants (DFHENTER, DFHPF3, DFHCLEAR, etc.) |
| COTTL01Y    | COCRDSLC        | Screen titles           | CCDA-TITLE01 ('AWS Mainframe Modernization'), CCDA-TITLE02 ('CardDemo'), CCDA-THANK-YOU |
| COCRDSL     | COCRDSLC        | BMS symbolic map (auto-generated) | Generated from COCRDSL.bms. Defines CCRDSLAI (input) and CCRDSLAO (output) symbolic map structures for the credit card detail screen |
| CSDAT01Y    | COCRDSLC        | Date/time structure     | WS-CURDATE-DATA (year/month/day), WS-CURTIME (hours/min/sec), formatted MM/DD/YY and HH:MM:SS, timestamp |
| CSMSG01Y    | COCRDSLC        | Common messages         | CCDA-MSG-THANK-YOU, CCDA-MSG-INVALID-KEY |
| CSMSG02Y    | COCRDSLC        | Abend work areas        | ABEND-DATA: ABEND-CODE (X(4)), ABEND-CULPRIT (X(8)), ABEND-REASON (X(50)), ABEND-MSG (X(72)) |
| CSUSR01Y    | COCRDSLC        | User security record    | SEC-USER-DATA: SEC-USR-ID (X(8)), SEC-USR-FNAME (X(20)), SEC-USR-LNAME (X(20)), SEC-USR-PWD (X(8)), SEC-USR-TYPE (X(1)) |
| CVACT02Y    | COCRDSLC        | VSAM record layout      | CARD-RECORD (150 bytes): CARD-NUM (X(16)), CARD-ACCT-ID (9(11)), CARD-CVV-CD (9(3)), CARD-EMBOSSED-NAME (X(50)), CARD-EXPIRAION-DATE (X(10)), CARD-ACTIVE-STATUS (X(1)), FILLER (X(59)) |
| CVCUS01Y    | COCRDSLC        | VSAM record layout      | CUSTOMER-RECORD (500 bytes): CUST-ID (9(9)), names, address fields, SSN, DOB, FICO score, filler |
| CSSTRPFY    | COCRDSLC        | PF key mapping          | YYYY-STORE-PFKEY paragraph: maps EIBAID to CCARD-AID-xxx flags via EVALUATE |

### Copybook Classification

| Classification              | Copybooks |
|-----------------------------|-----------|
| COMMAREA definition         | COCOM01Y |
| BMS symbolic map (auto-gen) | COCRDSL |
| VSAM record layouts         | CVACT02Y, CVCUS01Y |
| Work area / control         | CVCRD01Y |
| Screen titles/messages      | COTTL01Y, CSMSG01Y |
| Date/time structure         | CSDAT01Y |
| Abend handling              | CSMSG02Y |
| User security record        | CSUSR01Y |
| PF key mapping (COPY in PROCEDURE DIVISION) | CSSTRPFY |
| CICS system copybooks       | DFHAID, DFHBMSCA |

---

## Section 4: VSAM File Operations

### Files Accessed

| DD Name   | Variable              | Key Field              | Key Length | Record Copybook | Record Length | Purpose |
|-----------|-----------------------|------------------------|------------|-----------------|---------------|---------|
| CARDDAT   | LIT-CARDFILENAME      | WS-CARD-RID-CARDNUM   | 16         | CVACT02Y (CARD-RECORD) | 150 bytes | Primary card data file — keyed by card number |
| CARDAIX   | LIT-CARDFILENAME-ACCT-PATH | WS-CARD-RID-ACCT-ID | 11       | CVACT02Y (CARD-RECORD) | 150 bytes | Alternate index over CARDDAT — keyed by account ID |

### I/O Operations

| # | Paragraph                  | Command    | File    | Key Used               | Purpose | Error Handling |
|---|----------------------------|------------|---------|------------------------|---------|----------------|
| 1 | 9100-GETCARD-BYACCTCARD   | EXEC CICS READ | CARDDAT | WS-CARD-RID-CARDNUM (card number, 16 bytes) | Read card record by card number | NORMAL: set FOUND-CARDS-FOR-ACCOUNT; NOTFND: set DID-NOT-FIND-ACCTCARD-COMBO; OTHER: build file error message |
| 2 | 9150-GETCARD-BYACCT       | EXEC CICS READ | CARDAIX | WS-CARD-RID-ACCT-ID (account ID, 11 bytes) | Read card record by account ID (alternate index) | NORMAL: set FOUND-CARDS-FOR-ACCOUNT; NOTFND: set DID-NOT-FIND-ACCT-IN-CARDXREF; OTHER: build file error message |

> **Note:** Paragraph 9150-GETCARD-BYACCT exists in the source but is **not invoked** in the current execution flow. The main flow only calls 9100-GETCARD-BYACCTCARD (read by card number). The alternate index read is available but unused (likely reserved for future use or an alternative search path).

### VSAM File Flow Diagram

```
User Input: Account# + Card#
         |
         v
  +------------------+
  | Validate inputs  |
  | (2200-EDIT-MAP)  |
  +--------+---------+
           |
           v
  +------------------------------+
  | 9100-GETCARD-BYACCTCARD      |
  | READ CARDDAT                 |
  | KEY = Card Number (16 bytes) |
  +--------+---------------------+
           |
     +-----+------+--------+
     |             |        |
  NORMAL       NOTFND    OTHER
     |             |        |
  Display      "Did not   Build file
  card         find cards  error msg
  details      for this
               search"
```

---

## Section 5: BMS Screen Map

### Map Information

| Attribute    | Value     |
|--------------|-----------|
| Mapset       | COCRDSL   |
| Map          | CCRDSLA   |
| Size         | 24 x 80   |
| Source file  | app/bms/COCRDSL.bms |
| LANG         | COBOL     |
| MODE         | INOUT     |

### ASCII Screen Layout

```
+--------------------------------------------------------------------------------+
| Row 1: Tran: CCDL       AWS Mainframe Modernization          Date: mm/dd/yy   |
| Row 2: Prog: COCRDSLC              CardDemo                  Time: hh:mm:ss   |
| Row 3:                                                                         |
| Row 4:                              View Credit Card Detail                    |
| Row 5:                                                                         |
| Row 6:                                                                         |
| Row 7:                       Account Number    : [___________]                 |
| Row 8:                       Card Number       : [________________]            |
| Row 9:                                                                         |
| Row 10:                                                                        |
| Row 11:    Name on card      : [__________________________________________________]|
| Row 12:                                                                        |
| Row 13:    Card Active Y/N   : [_]                                             |
| Row 14:                                                                        |
| Row 15:    Expiry Date       : [MM]/[YYYY]                                     |
| Row 16-19:                                                                     |
| Row 20:                         (info message area, 40 chars)                  |
| Row 21-22:                                                                     |
| Row 23: (error message - red, bright, 80 chars)                               |
| Row 24: ENTER=Search Cards  F3=Exit                                            |
+--------------------------------------------------------------------------------+
```

### Field Inventory

| Field   | Row | Col | Length | Attributes          | Color     | Purpose |
|---------|-----|-----|--------|---------------------|-----------|---------|
| TRNNAME |  1  |  7  |   4    | ASKIP,FSET,NORM     | Blue      | Transaction ID display |
| TITLE01 |  1  | 21  |  40    | ASKIP,NORM          | Yellow    | Screen title line 1 |
| CURDATE |  1  | 71  |   8    | ASKIP,NORM          | Blue      | Current date (mm/dd/yy) |
| PGMNAME |  2  |  7  |   8    | ASKIP,NORM          | Blue      | Program name display |
| TITLE02 |  2  | 21  |  40    | ASKIP,NORM          | Yellow    | Screen title line 2 |
| CURTIME |  2  | 71  |   8    | ASKIP,NORM          | Blue      | Current time (hh:mm:ss) |
| ACCTSID |  7  | 45  |  11    | FSET,IC,NORM,UNPROT | Default (underline) | Account number input |
| CARDSID |  8  | 45  |  16    | FSET,NORM,UNPROT    | Default (underline) | Card number input |
| CRDNAME | 11  | 25  |  50    | (default)           | (underline) | Name embossed on card (display) |
| CRDSTCD | 13  | 25  |   1    | ASKIP               | (underline) | Card active status Y/N (display) |
| EXPMON  | 15  | 25  |   2    | ASKIP               | (underline) | Expiry month (display) |
| EXPYEAR | 15  | 30  |   4    | ASKIP               | (underline) | Expiry year (display) |
| INFOMSG | 20  | 25  |  40    | PROT                | Neutral   | Information message |
| ERRMSG  | 23  |  1  |  80    | ASKIP,BRT,FSET      | Red       | Error message |
| FKEYS   | 24  |  1  |  75    | ASKIP,NORM          | Yellow    | Function key legend |

### Input vs Display Fields

| Type     | Fields |
|----------|--------|
| Input (UNPROT) | ACCTSID (account number), CARDSID (card number) |
| Display (ASKIP/PROT) | TRNNAME, TITLE01, TITLE02, CURDATE, CURTIME, PGMNAME, CRDNAME, CRDSTCD, EXPMON, EXPYEAR, INFOMSG, ERRMSG, FKEYS |

### Function Key Assignments

| Key    | Action | Implementation |
|--------|--------|----------------|
| ENTER  | Search for card details | Validates inputs, reads CARDDAT, displays results |
| PF3    | Exit / Return to caller | XCTL to CDEMO-FROM-PROGRAM or COMEN01C |

### Dynamic Field Behavior

- When arriving from **COCRDLIC** (card list), ACCTSID and CARDSID are **protected** (DFHBMPRF) and display-only (data pre-filled from list selection).
- When arriving from **menu or other context**, ACCTSID and CARDSID are **unprotected** with fset (DFHBMFSE) for user input.
- Invalid field values turn **red** (DFHRED).
- Blank required fields show `*` in red on re-entry.

---

## Section 6: COMMAREA Structure

### CARDDEMO-COMMAREA (COCOM01Y.cpy)

```
01 CARDDEMO-COMMAREA
   05 CDEMO-GENERAL-INFO
      10 CDEMO-FROM-TRANID          PIC X(04)    -- Source transaction ID
      10 CDEMO-FROM-PROGRAM         PIC X(08)    -- Source program name
      10 CDEMO-TO-TRANID            PIC X(04)    -- Target transaction ID
      10 CDEMO-TO-PROGRAM           PIC X(08)    -- Target program name
      10 CDEMO-USER-ID              PIC X(08)    -- Signed-on user ID
      10 CDEMO-USER-TYPE            PIC X(01)    -- 'A'=Admin, 'U'=User
         88 CDEMO-USRTYP-ADMIN      VALUE 'A'
         88 CDEMO-USRTYP-USER       VALUE 'U'
      10 CDEMO-PGM-CONTEXT          PIC 9(01)    -- 0=Enter, 1=Reenter
         88 CDEMO-PGM-ENTER         VALUE 0
         88 CDEMO-PGM-REENTER       VALUE 1
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
      10 CDEMO-LAST-MAP             PIC X(7)     -- Last map displayed
      10 CDEMO-LAST-MAPSET          PIC X(7)     -- Last mapset used
```

### Program-Specific Extension (WS-THIS-PROGCOMMAREA)

COCRDSLC appends its own COMMAREA after CARDDEMO-COMMAREA:

```
01 WS-THIS-PROGCOMMAREA
   05 CA-CALL-CONTEXT
      10 CA-FROM-PROGRAM            PIC X(08)    -- Program that called us
      10 CA-FROM-TRANID             PIC X(04)    -- Transaction of caller
```

### COMMAREA Field Usage by Program

| Field                | COSGN00C (Set) | COMEN01C (Set) | COCRDLIC (Set) | COCRDSLC (Read) | COCRDSLC (Set) |
|----------------------|:-:|:-:|:-:|:-:|:-:|
| CDEMO-FROM-TRANID    | W | W | W | R | W |
| CDEMO-FROM-PROGRAM   | W | W | W | R | W |
| CDEMO-TO-TRANID      |   |   |   |   | W |
| CDEMO-TO-PROGRAM     |   |   |   |   | W |
| CDEMO-USER-ID        | W |   |   |   |   |
| CDEMO-USER-TYPE      | W |   |   |   | W |
| CDEMO-PGM-CONTEXT    | W | W | W | R | W |
| CDEMO-ACCT-ID        |   |   | W | R | W |
| CDEMO-CARD-NUM       |   |   | W | R | W |
| CDEMO-LAST-MAP       |   |   | W | R | W |
| CDEMO-LAST-MAPSET    |   |   | W | R | W |

### COMMAREA Inter-Program Flow

```
COCRDLIC --> COCRDSLC:
  Sets: CDEMO-FROM-TRANID = 'CCLI'
        CDEMO-FROM-PROGRAM = 'COCRDLIC'
        CDEMO-PGM-CONTEXT = 0 (ENTER)
        CDEMO-ACCT-ID = (selected account)
        CDEMO-CARD-NUM = (selected card)
        CDEMO-LAST-MAPSET = 'COCRDLI'
        CDEMO-LAST-MAP = 'CCRDLIA'

COMEN01C --> COCRDSLC (direct via option 4):
  Sets: CDEMO-FROM-TRANID = 'CM00'
        CDEMO-FROM-PROGRAM = 'COMEN01C'
        CDEMO-PGM-CONTEXT = 0 (ENTER)

COCRDSLC --> (caller) on PF3:
  Sets: CDEMO-FROM-TRANID = 'CCDL'
        CDEMO-FROM-PROGRAM = 'COCRDSLC'
        CDEMO-TO-TRANID = (caller's tranid or 'CM00')
        CDEMO-TO-PROGRAM = (caller's program or 'COMEN01C')
        CDEMO-PGM-CONTEXT = 0 (ENTER)
        CDEMO-LAST-MAPSET = 'COCRDSL'
        CDEMO-LAST-MAP = 'CCRDSLA'
```

---

## Section 7: Business Logic Flow

### Step-by-Step Narrative

#### 1. Program Entry and Initialization (0000-MAIN)

1. **HANDLE ABEND** is set to label ABEND-ROUTINE for crash recovery.
2. CC-WORK-AREA, WS-MISC-STORAGE, and WS-COMMAREA are initialized.
3. WS-TRANID is set to `'CCDL'`.
4. Error message flag (WS-RETURN-MSG-OFF) is cleared.

#### 2. COMMAREA Processing

5. **If EIBCALEN = 0** (direct terminal invocation, no COMMAREA):
   - Initialize CARDDEMO-COMMAREA and WS-THIS-PROGCOMMAREA to zeros/spaces.
6. **Else if coming from menu (CDEMO-FROM-PROGRAM = 'COMEN01C') and not re-entering (PGM-CONTEXT = 0)**:
   - Initialize COMMAREA (fresh start from menu).
7. **Else** (re-entry or from another program):
   - Move DFHCOMMAREA into CARDDEMO-COMMAREA and WS-THIS-PROGCOMMAREA.

#### 3. PF Key Mapping

8. YYYY-STORE-PFKEY (CSSTRPFY copybook) maps EIBAID to CCARD-AID flags.
9. Only ENTER and PF3 are valid keys; any other key defaults to ENTER.

#### 4. Main EVALUATE Decision

10. **WHEN PF3** (Exit):
    - Determine return target: if CDEMO-FROM-TRANID is set, return to original caller; else return to menu (COMEN01C/CM00).
    - Set navigation fields in COMMAREA.
    - **EXEC CICS XCTL** to CDEMO-TO-PROGRAM with CARDDEMO-COMMAREA.

11. **WHEN PGM-ENTER AND FROM-PROGRAM = COCRDLIC** (arriving from Card List):
    - Input is pre-validated (account and card already selected from list).
    - Move CDEMO-ACCT-ID and CDEMO-CARD-NUM to CC-ACCT-ID-N and CC-CARD-NUM-N.
    - Perform 9000-READ-DATA (reads CARDDAT by card number).
    - Perform 1000-SEND-MAP (display card details).
    - GO TO COMMON-RETURN.

12. **WHEN PGM-ENTER (other context)** (arriving from menu or other program, first entry):
    - No data to display yet; send blank search form.
    - Perform 1000-SEND-MAP.
    - GO TO COMMON-RETURN.

13. **WHEN PGM-REENTER** (user submitted search form):
    - Perform 2000-PROCESS-INPUTS (receive and validate).
    - If INPUT-ERROR: re-send map with error messages.
    - If valid: perform 9000-READ-DATA, then 1000-SEND-MAP to display results.
    - GO TO COMMON-RETURN.

14. **WHEN OTHER** (unexpected scenario):
    - Set ABEND-CULPRIT, ABEND-CODE = '0001'.
    - Send plain text error message.

#### 5. COMMON-RETURN

15. Move WS-RETURN-MSG to CCARD-ERROR-MSG.
16. Combine CARDDEMO-COMMAREA and WS-THIS-PROGCOMMAREA into WS-COMMAREA.
17. **EXEC CICS RETURN TRANSID('CCDL') COMMAREA(WS-COMMAREA)** — pseudo-conversational suspend.

#### 6. Screen Send Logic (1000-SEND-MAP)

18. **1100-SCREEN-INIT**: Initialize CCRDSLAO to LOW-VALUES, populate titles, transaction ID, program name, current date and time.
19. **1200-SETUP-SCREEN-VARS**: Populate account/card search fields; if data was found, populate name, expiry month/year, and status.
20. **1300-SETUP-SCREEN-ATTRS**: 
    - If from COCRDLIC: protect ACCTSID and CARDSID (read-only view).
    - Otherwise: unprotect for user input (DFHBMFSE).
    - Position cursor to first invalid/blank field.
    - Set field colors: red for invalid, default color when from list.
    - Blank fields on re-entry show `*` in red.
21. **1400-SEND-SCREEN**: 
    - Set PGM-CONTEXT to REENTER.
    - **EXEC CICS SEND MAP(CCARD-NEXT-MAP) MAPSET(CCARD-NEXT-MAPSET) FROM(CCRDSLAO) CURSOR ERASE FREEKB**.

#### 7. Input Processing (2000-PROCESS-INPUTS)

22. **2100-RECEIVE-MAP**: **EXEC CICS RECEIVE MAP(LIT-THISMAP) MAPSET(LIT-THISMAPSET) INTO(CCRDSLAI)**.
23. **2200-EDIT-MAP-INPUTS**: 
    - Initialize all flags to OK/VALID.
    - Replace `*` or spaces in account/card fields with LOW-VALUES.
    - Call 2210-EDIT-ACCOUNT and 2220-EDIT-CARD.
    - Cross-field check: if both blank, set NO-SEARCH-CRITERIA-RECEIVED.

#### 8. Account Validation (2210-EDIT-ACCOUNT)

24. If blank/zeros: INPUT-ERROR, "Account number not provided".
25. If not numeric: INPUT-ERROR, "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER".
26. If valid: move to CDEMO-ACCT-ID, set FLG-ACCTFILTER-ISVALID.

#### 9. Card Validation (2220-EDIT-CARD)

27. If blank/zeros: INPUT-ERROR, "Card number not provided".
28. If not numeric: INPUT-ERROR, "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER".
29. If valid: move to CDEMO-CARD-NUM, set FLG-CARDFILTER-ISVALID.

#### 10. Data Read (9000-READ-DATA → 9100-GETCARD-BYACCTCARD)

30. Move CC-CARD-NUM to WS-CARD-RID-CARDNUM (the key).
31. **EXEC CICS READ FILE(LIT-CARDFILENAME) RIDFLD(WS-CARD-RID-CARDNUM) INTO(CARD-RECORD)**.
32. RESP handling:
    - **NORMAL**: Set FOUND-CARDS-FOR-ACCOUNT (success).
    - **NOTFND**: Set DID-NOT-FIND-ACCTCARD-COMBO ("Did not find cards for this search condition").
    - **OTHER**: Build file error message with operation name, file, RESP, RESP2.

#### 11. Abend Handling (ABEND-ROUTINE)

33. If no abend message set, default to "UNEXPECTED ABEND OCCURRED."
34. Set ABEND-CULPRIT = LIT-THISPGM.
35. **EXEC CICS SEND FROM(ABEND-DATA)** with NOHANDLE.
36. **EXEC CICS HANDLE ABEND CANCEL**.
37. **EXEC CICS ABEND ABCODE('9999')**.

#### 12. Debug/Diagnostic Paragraphs

38. **SEND-LONG-TEXT**: Sends WS-LONG-MSG via EXEC CICS SEND TEXT, then RETURN (no TRANSID — ends conversation).
39. **SEND-PLAIN-TEXT**: Sends WS-RETURN-MSG via EXEC CICS SEND TEXT, then RETURN (no TRANSID — ends conversation).

---

## Section 8: Complete File Inventory

### COBOL Source Files

| File | Path | Purpose |
|------|------|---------|
| COCRDSLC.cbl | app/cbl/COCRDSLC.cbl | Target program — Credit Card Detail/Search |
| COCRDLIC.cbl | app/cbl/COCRDLIC.cbl | Upstream — Credit Card List |
| COMEN01C.cbl | app/cbl/COMEN01C.cbl | Upstream — Main Menu |
| COSGN00C.cbl | app/cbl/COSGN00C.cbl | Upstream — Sign-on |
| COACTVWC.cbl | app/cbl/COACTVWC.cbl | Upstream — Account View (references COCRDSLC) |
| COACTUPC.cbl | app/cbl/COACTUPC.cbl | Upstream — Account Update (references COCRDSLC) |

### Copybook Files

| File | Path | Classification |
|------|------|----------------|
| CVCRD01Y.cpy | app/cpy/CVCRD01Y.cpy | Work area (AID flags, navigation, IDs) |
| COCOM01Y.cpy | app/cpy/COCOM01Y.cpy | COMMAREA definition |
| COTTL01Y.cpy | app/cpy/COTTL01Y.cpy | Screen titles |
| CSDAT01Y.cpy | app/cpy/CSDAT01Y.cpy | Date/time structure |
| CSMSG01Y.cpy | app/cpy/CSMSG01Y.cpy | Common messages |
| CSMSG02Y.cpy | app/cpy/CSMSG02Y.cpy | Abend work areas |
| CSUSR01Y.cpy | app/cpy/CSUSR01Y.cpy | User security record layout |
| CVACT02Y.cpy | app/cpy/CVACT02Y.cpy | Card record layout (CARDDAT) |
| CVCUS01Y.cpy | app/cpy/CVCUS01Y.cpy | Customer record layout |
| CSSTRPFY.cpy | app/cpy/CSSTRPFY.cpy | PF key mapping paragraph |

### BMS Map Files

| File | Path | Mapset | Map |
|------|------|--------|-----|
| COCRDSL.bms | app/bms/COCRDSL.bms | COCRDSL | CCRDSLA |

### BMS Symbolic Map Copybooks (Auto-Generated)

| Copybook | Referenced In | Note |
|----------|---------------|------|
| COCRDSL  | COCRDSLC.cbl (line 215: `COPY COCRDSL.`) | BMS-generated symbolic map; does not exist as a separate .cpy file in app/cpy/. Defines CCRDSLAI (input) and CCRDSLAO (output) structures. |

### CICS System Copybooks (IBM-Supplied)

| Copybook | Referenced In | Note |
|----------|---------------|------|
| DFHAID   | COCRDSLC.cbl (line 209) | AID key constants — not in app/cpy/ |
| DFHBMSCA | COCRDSLC.cbl (line 208) | BMS attribute constants — not in app/cpy/ |

### VSAM Data Files

| DD Name | Key | Key Length | Record Length | Record Copybook |
|---------|-----|------------|---------------|-----------------|
| CARDDAT | Card Number | 16 | 150 | CVACT02Y |
| CARDAIX | Account ID  | 11 | 150 | CVACT02Y |

### CSD Definitions

| Type | Name | Group | Key Attributes |
|------|------|-------|----------------|
| PROGRAM | COCRDSLC | CARDDEMO | CONCURRENCY(QUASIRENT) API(CICSAPI) TRANSID(CCDL) |
| TRANSACTION | CCDL | CARDDEMO | PROGRAM(COCRDSLC) PROFILE(DFHCICST) STATUS(ENABLED) |

---

## Section 9: Card Record Layout (CVACT02Y)

### CARD-RECORD (150 bytes total)

| Offset | Length | Field | PIC | Description |
|--------|--------|-------|-----|-------------|
| 0 | 16 | CARD-NUM | X(16) | Card number (primary key for CARDDAT) |
| 16 | 11 | CARD-ACCT-ID | 9(11) | Associated account ID |
| 27 | 3 | CARD-CVV-CD | 9(03) | CVV security code |
| 30 | 50 | CARD-EMBOSSED-NAME | X(50) | Name embossed on card |
| 80 | 10 | CARD-EXPIRAION-DATE | X(10) | Expiration date (YYYY-MM-DD format) |
| 90 | 1 | CARD-ACTIVE-STATUS | X(01) | Active status flag (Y/N) |
| 91 | 59 | FILLER | X(59) | Reserved/unused |

### Working Storage Output Edit Fields

COCRDSLC defines additional output formatting fields in WS-MISC-STORAGE:

| Field | PIC | Purpose |
|-------|-----|---------|
| CARD-ACCT-ID-X | X(11) | Account ID (alphanumeric view) |
| CARD-ACCT-ID-N | 9(11) | Account ID (numeric redefine) |
| CARD-CVV-CD-X | X(03) | CVV code (alphanumeric) |
| CARD-CVV-CD-N | 9(03) | CVV code (numeric redefine) |
| CARD-CARD-NUM-X | X(16) | Card number (alphanumeric) |
| CARD-CARD-NUM-N | 9(16) | Card number (numeric redefine) |
| CARD-NAME-EMBOSSED-X | X(50) | Name on card |
| CARD-STATUS-X | X | Card status |
| CARD-EXPIRAION-DATE-X | X(10) | Expiry date composite |
| CARD-EXPIRY-YEAR | X(4) | Expiry year (extracted) |
| CARD-EXPIRY-MONTH | X(2) | Expiry month (extracted) |
| CARD-EXPIRY-DAY | X(2) | Expiry day (extracted) |

---

## Section 10: Observations

### What the Flow Demonstrates Well

1. **Clean pseudo-conversational pattern**: Clear separation between first entry (PGM-CONTEXT=0) and re-entry (PGM-CONTEXT=1), with proper RETURN TRANSID for session management.

2. **Context-aware screen behavior**: The program dynamically adjusts field protection and colors based on the calling context (from card list vs. direct entry). This is a good pattern for screen reuse.

3. **Standard COMMAREA navigation**: Uses the shared CARDDEMO-COMMAREA for inter-program navigation with FROM/TO program and transaction fields, plus a program-specific extension area.

4. **Defensive input validation**: Both account and card number are individually validated for presence, numeric content, and cross-field consistency (both blank = no criteria).

5. **Structured error handling**: Every VSAM READ has RESP/RESP2 handling with meaningful error messages constructed from a template (WS-FILE-ERROR-MESSAGE).

6. **Shared copybook architecture**: Common copybooks (COTTL01Y, CSDAT01Y, CSMSG01Y, CSMSG02Y, CSUSR01Y, CSSTRPFY) promote consistency across all CardDemo programs.

### Gaps and Migration Considerations

1. **Dead code**: Paragraph 9150-GETCARD-BYACCT (alternate index read via CARDAIX) is defined but never called in the current flow. The CVCUS01Y (customer record) copybook is included but not used in any I/O operation.

2. **No DB2/SQL**: All data access is VSAM READ only — straightforward mapping to SQL SELECT statements during migration.

3. **Read-only flow**: COCRDSLC only reads data (no WRITE/REWRITE/DELETE). Migration target can implement this as a simple query/display service.

4. **BMS symbolic map dependency**: The `COPY COCRDSL.` statement references a BMS-generated copybook that does not exist as a standalone .cpy file. Migration must handle symbolic map generation or replace with modern UI framework bindings.

5. **Single-record display**: The screen only shows one card at a time. The card list (COCRDLIC) provides the multi-record view with pagination.

6. **No transaction logging or audit trail**: The program does not write any transaction records or audit entries.

7. **CARDAIX alternate index**: The alternate index path exists for account-based lookup but is unused in the current program. Migration should evaluate whether this access path is needed.

---

## Section 11: Complexity Comparison

| Dimension | COCRDSLC (This Flow) | Typical Migration Target |
|-----------|----------------------|--------------------------|
| Programs in chain | 1 main + 3-5 upstream | Single microservice endpoint |
| VSAM files accessed | 1 (CARDDAT) + 1 unused (CARDAIX) | 1 database table (cards) |
| I/O operations | 1 READ | 1 SELECT query |
| Screen maps | 1 (CCRDSLA) | 1 HTML/React form |
| Input fields | 2 (account, card number) | 2 form fields |
| Display fields | 4 (name, status, expiry month, year) | Card detail component |
| COMMAREA fields used | ~12 | REST request/session state |
| Copybooks | 12 application + 2 system | Shared DTOs/models |
| Validation rules | 4 (blank, numeric, cross-field) | Form validation library |
| Error paths | 6 distinct messages | API error responses |
| Complexity rating | **Low** | Straightforward CRUD read |

This is one of the simpler programs in the CardDemo suite — a single-record lookup with basic input validation. It makes an ideal candidate for early migration as a proof-of-concept for the card detail view.

---

## Appendix: Complete EXEC CICS Command Inventory

| # | Location | Command | Parameters | RESP Handling |
|---|----------|---------|------------|---------------|
| 1 | 0000-MAIN (line 250) | HANDLE ABEND | LABEL(ABEND-ROUTINE) | N/A |
| 2 | 0000-MAIN (line 331) | XCTL | PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA) | None (PF3 exit) |
| 3 | COMMON-RETURN (line 402) | RETURN | TRANSID(LIT-THISTRANID) COMMAREA(WS-COMMAREA) LENGTH(LENGTH OF WS-COMMAREA) | None |
| 4 | 1400-SEND-SCREEN (line 569) | SEND MAP | MAP(CCARD-NEXT-MAP) MAPSET(CCARD-NEXT-MAPSET) FROM(CCRDSLAO) CURSOR ERASE FREEKB | RESP(WS-RESP-CD) |
| 5 | 2100-RECEIVE-MAP (line 597) | RECEIVE MAP | MAP(LIT-THISMAP) MAPSET(LIT-THISMAPSET) INTO(CCRDSLAI) | RESP(WS-RESP-CD) RESP2(WS-REAS-CD) |
| 6 | 9100-GETCARD-BYACCTCARD (line 742) | READ | FILE(LIT-CARDFILENAME) RIDFLD(WS-CARD-RID-CARDNUM) KEYLENGTH(LENGTH OF WS-CARD-RID-CARDNUM) INTO(CARD-RECORD) LENGTH(LENGTH OF CARD-RECORD) | RESP(WS-RESP-CD) RESP2(WS-REAS-CD) |
| 7 | 9150-GETCARD-BYACCT (line 783) | READ | FILE(LIT-CARDFILENAME-ACCT-PATH) RIDFLD(WS-CARD-RID-ACCT-ID) KEYLENGTH(LENGTH OF WS-CARD-RID-ACCT-ID) INTO(CARD-RECORD) LENGTH(LENGTH OF CARD-RECORD) | RESP(WS-RESP-CD) RESP2(WS-REAS-CD) |
| 8 | SEND-LONG-TEXT (line 821) | SEND TEXT | FROM(WS-LONG-MSG) LENGTH(LENGTH OF WS-LONG-MSG) ERASE FREEKB | None |
| 9 | SEND-LONG-TEXT (line 828) | RETURN | (no parameters — ends conversation) | None |
| 10 | SEND-PLAIN-TEXT (line 839) | SEND TEXT | FROM(WS-RETURN-MSG) LENGTH(LENGTH OF WS-RETURN-MSG) ERASE FREEKB | None |
| 11 | SEND-PLAIN-TEXT (line 846) | RETURN | (no parameters — ends conversation) | None |
| 12 | ABEND-ROUTINE (line 865) | SEND | FROM(ABEND-DATA) LENGTH(LENGTH OF ABEND-DATA) NOHANDLE | NOHANDLE |
| 13 | ABEND-ROUTINE (line 871) | HANDLE ABEND | CANCEL | N/A |
| 14 | ABEND-ROUTINE (line 875) | ABEND | ABCODE('9999') | N/A |

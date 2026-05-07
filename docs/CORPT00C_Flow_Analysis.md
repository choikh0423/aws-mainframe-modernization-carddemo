# CR00 Transaction Flow Analysis — CORPT00C (Report Submission)

## Section 1: Flow Overview

| Attribute           | Value                                                      |
|---------------------|------------------------------------------------------------|
| **Transaction ID**  | CR00                                                       |
| **Entry Program**   | CORPT00C                                                   |
| **Function**        | Print Transaction Reports by submitting a batch job from online using an extra-partition TDQ |
| **Program Type**    | CICS COBOL — pseudo-conversational                         |
| **Data Access**     | VSAM (no DB2 or IMS)                                       |

### Complete Navigation Path

```
 Terminal
   |
   | CC00
   v
 COSGN00C  (Sign-on)
   |
   | XCTL  (regular user)
   v
 COMEN01C  (Main Menu, CM00)
   |
   | XCTL  (menu option 9 — "Transaction Reports")
   v
 CORPT00C  (Report Submission, CR00)  <-- TARGET
   |
   | PF3 => XCTL back to COMEN01C
   | EIBCALEN=0 => XCTL to COSGN00C
   |
   | CALL 'CSUTLDTC' (date validation utility)
   |       |
   |       | CALL 'CEEDAYS' (LE intrinsic)
   |       v
   | WRITEQ TD => JOBS queue (extra-partition TDQ)
   v
 RETURN TRANSID('CR00')  (pseudo-conversational loop)
```

### Pseudo-Conversational Lifecycle

```
 Iteration 1 (First Entry — CDEMO-PGM-CONTEXT = 0):
   COMEN01C sets PGM-CONTEXT to 0, XCTLs to CORPT00C
   -> CORPT00C detects NOT CDEMO-PGM-REENTER
   -> Sets CDEMO-PGM-REENTER (PGM-CONTEXT = 1)
   -> Initializes screen fields to LOW-VALUES
   -> SEND MAP CORPT0A with ERASE
   -> RETURN TRANSID('CR00') COMMAREA(CARDDEMO-COMMAREA)

 Iteration 2..N (Re-entry — CDEMO-PGM-CONTEXT = 1):
   CICS dispatches CR00 -> CORPT00C with COMMAREA
   -> CORPT00C detects CDEMO-PGM-REENTER
   -> RECEIVE MAP CORPT0A
   -> EVALUATE EIBAID:
       DFHENTER -> PROCESS-ENTER-KEY (validate, submit job)
       DFHPF3   -> XCTL to COMEN01C (return to menu)
       OTHER    -> error message, re-send screen
   -> RETURN TRANSID('CR00') COMMAREA(CARDDEMO-COMMAREA)

 Exit Conditions:
   - PF3: XCTL to COMEN01C (control does not return)
   - EIBCALEN=0: XCTL to COSGN00C (no COMMAREA — direct invocation)
```

---

## Section 2: Programs Involved

| # | Program    | Type              | Function                                  | Caller     | Call Method         |
|---|------------|-------------------|-------------------------------------------|------------|---------------------|
| 1 | COSGN00C   | CICS COBOL        | Sign-on screen — authenticates users      | Terminal   | Transaction CC00    |
| 2 | COMEN01C   | CICS COBOL        | Main Menu for regular users               | COSGN00C   | XCTL                |
| 3 | CORPT00C   | CICS COBOL        | Report Submission — submits batch report job via TDQ | COMEN01C | XCTL (menu option 9) |
| 4 | CSUTLDTC   | Batch COBOL (sub-program) | Date validation utility — wraps CEEDAYS  | CORPT00C   | CALL (literal)      |
| 5 | CEEDAYS    | LE Intrinsic      | Language Environment date conversion      | CSUTLDTC   | CALL (literal)      |

### Program Detail Notes

- **COSGN00C** (CC00): Entry point. Reads USRSEC VSAM file to authenticate. On success, XCTLs to COMEN01C (regular user) or COADM01C (admin user).
- **COMEN01C** (CM00): Displays main menu. Menu options defined in COMEN02Y copybook. Option 9 = "Transaction Reports" → XCTL to CORPT00C.
- **CORPT00C** (CR00): Target program. Displays report type selection screen. Validates dates (for custom range). Builds JCL in working storage. Writes JCL records to JOBS TDQ via WRITEQ TD. No direct VSAM file I/O.
- **CSUTLDTC**: Called by CORPT00C to validate start/end dates. Accepts date string and format, calls CEEDAYS, returns severity code and message. Linkage: LS-DATE (X(10)), LS-DATE-FORMAT (X(10)), LS-RESULT (X(80)).
- **CEEDAYS**: IBM Language Environment intrinsic. Converts date to Lilian format; returns feedback code indicating validity. Not in repository — external system routine.

---

## Section 3: Copybooks

| # | Copybook   | Used By          | Classification           | Purpose / Layout Summary |
|---|------------|------------------|--------------------------|--------------------------|
| 1 | COCOM01Y   | CORPT00C, COMEN01C, COSGN00C | COMMAREA definition | Defines CARDDEMO-COMMAREA — navigation fields, customer/account/card info (see Section 6) |
| 2 | CORPT00    | CORPT00C         | BMS symbolic map (auto-generated) | Defines CORPT0AI (input) and CORPT0AO (output) structures for CORPT0A map. Generated from CORPT00.bms |
| 3 | COTTL01Y   | CORPT00C, COMEN01C, COSGN00C | Screen title/message | CCDA-TITLE01 ("AWS Mainframe Modernization"), CCDA-TITLE02 ("CardDemo"), CCDA-THANK-YOU |
| 4 | CSDAT01Y   | CORPT00C, COMEN01C, COSGN00C | Date/time structure | WS-DATE-TIME: WS-CURDATE-DATA (year/month/day), WS-CURTIME, formatted display fields (MM/DD/YY, HH:MM:SS), timestamp |
| 5 | CSMSG01Y   | CORPT00C, COMEN01C, COSGN00C | Common messages | CCDA-MSG-THANK-YOU, CCDA-MSG-INVALID-KEY |
| 6 | CVTRA05Y   | CORPT00C         | Record layout (VSAM) | TRAN-RECORD (350 bytes): TRAN-ID, TRAN-TYPE-CD, TRAN-CAT-CD, TRAN-SOURCE, TRAN-DESC, TRAN-AMT, merchant fields, TRAN-CARD-NUM, timestamps |
| 7 | DFHAID     | CORPT00C, COMEN01C, COSGN00C | CICS system copybook | AID key constants (DFHENTER, DFHPF3, DFHCLEAR, etc.). IBM-supplied — not in app/cpy/ |
| 8 | DFHBMSCA   | CORPT00C, COMEN01C, COSGN00C | CICS system copybook | BMS attribute constants (DFHBMASK, DFHBMPRF, DFHBMPRO, DFHGREEN, DFHRED, etc.). IBM-supplied — not in app/cpy/ |
| 9 | COMEN02Y   | COMEN01C         | Menu option data | Defines 11 menu options (num, name, program, user-type). Option 9 maps to CORPT00C |
| 10| CSUSR01Y   | COMEN01C, COSGN00C | Record layout (VSAM) | SEC-USER-DATA: user security record for USRSEC file |

### CVTRA05Y — Transaction Record Layout (TRAN-RECORD, 350 bytes)

```
Level  Field                PIC             Bytes  Offset  Purpose
-----  -------------------  --------------  -----  ------  -------------------------
01     TRAN-RECORD                          350    0       Transaction record
 05    TRAN-ID              X(16)           16     0       Transaction identifier
 05    TRAN-TYPE-CD         X(02)           2      16      Transaction type code
 05    TRAN-CAT-CD          9(04)           4      18      Transaction category code
 05    TRAN-SOURCE          X(10)           10     22      Transaction source
 05    TRAN-DESC            X(100)          100    32      Transaction description
 05    TRAN-AMT             S9(09)V99       11     132     Transaction amount (packed)
 05    TRAN-MERCHANT-ID     9(09)           9      143     Merchant identifier
 05    TRAN-MERCHANT-NAME   X(50)           50     152     Merchant name
 05    TRAN-MERCHANT-CITY   X(50)           50     202     Merchant city
 05    TRAN-MERCHANT-ZIP    X(10)           10     252     Merchant ZIP code
 05    TRAN-CARD-NUM        X(16)           16     262     Card number
 05    TRAN-ORIG-TS         X(26)           26     278     Original timestamp
 05    TRAN-PROC-TS         X(26)           26     304     Processing timestamp
 05    FILLER               X(20)           20     330     Reserved
```

> **Note:** CVTRA05Y is included via COPY in CORPT00C but CORPT00C does not directly read/write the TRANSACT VSAM file. The copybook is included because the JCL data embedded in working storage references transaction record field positions (TRAN-CARD-NUM at offset 263, TRAN-PROC-DT at offset 305) for the batch SORT/report utility.

---

## Section 4: VSAM File Operations

### Direct VSAM File I/O by CORPT00C

**CORPT00C performs NO direct VSAM file I/O.** It does not contain any EXEC CICS READ, WRITE, REWRITE, DELETE, STARTBR, READNEXT, or ENDBR commands.

Instead, CORPT00C submits a batch job via the JOBS extra-partition Transient Data Queue (TDQ). The batch job (TRNRPT00 / PROC=TRANREPT) performs the actual transaction file processing.

### TDQ Operations

| Operation        | Queue  | Type              | Record     | Length | Purpose                        |
|------------------|--------|-------------------|------------|--------|--------------------------------|
| EXEC CICS WRITEQ TD | JOBS | Extra-partition TDQ | JCL-RECORD | 80     | Write JCL lines to internal reader for batch job submission |

### WRITEQ TD Detail

```cobol
EXEC CICS WRITEQ TD
  QUEUE ('JOBS')
  FROM (JCL-RECORD)
  LENGTH (LENGTH OF JCL-RECORD)
  RESP(WS-RESP-CD)
  RESP2(WS-REAS-CD)
END-EXEC
```

**Error handling:** EVALUATE WS-RESP-CD — DFHRESP(NORMAL) → continue; OTHER → set ERR-FLG-ON, display "Unable to Write TDQ (JOBS)..." message.

### VSAM Files Referenced Indirectly (via batch JCL)

The JCL embedded in CORPT00C's working storage (JOB-DATA) references the TRANSACT VSAM file indirectly through the TRANREPT batch procedure. The batch job uses:
- **TRANSACT** — Transaction file (VSAM KSDS, key = TRAN-ID)
- Field references in SYMNAMES: TRAN-CARD-NUM (offset 263, length 16, ZD), TRAN-PROC-DT (offset 305, length 10, CH)

### Upstream VSAM File Access (Navigation Path)

| Program  | VSAM File | Operation | Key Field    | Record Copybook | Purpose                    |
|----------|-----------|-----------|--------------|-----------------|----------------------------|
| COSGN00C | USRSEC    | READ      | WS-USER-ID   | CSUSR01Y        | Authenticate user at sign-on |

### TDQ / Batch Job Flow Diagram

```
 CORPT00C (Online CICS)
   |
   | User selects report type (Monthly/Yearly/Custom)
   | User confirms (Y/N)
   |
   | PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL /*EOF
   |   MOVE JOB-LINES(WS-IDX) TO JCL-RECORD
   |   EXEC CICS WRITEQ TD QUEUE('JOBS') FROM(JCL-RECORD)
   |
   v
 JOBS TDQ (Extra-partition)
   |
   | Routes to MVS Internal Reader
   v
 TRNRPT00 JOB
   |
   | //JOBLIB JCLLIB ORDER=('AWS.M2.CARDDEMO.PROC')
   | //STEP10 EXEC PROC=TRANREPT
   | //STEP05R.SYMNAMES DD * (field definitions)
   | //STEP10R.DATEPARM DD * (start-date end-date)
   |
   v
 TRANSACT VSAM File (batch read)
   |
   v
 Transaction Report Output
```

---

## Section 5: BMS Screen Map

### Map Information

| Attribute    | Value    |
|--------------|----------|
| **Mapset**   | CORPT00  |
| **Map**      | CORPT0A  |
| **Size**     | 24 rows x 80 columns |
| **Source**    | app/bms/CORPT00.bms |
| **Symbolic** | COPY CORPT00 (auto-generated, not in app/cpy/) |

### ASCII Screen Layout

```
Row Col  Field
--- ---  -----------------------------------------------------------------------
 1   1   Tran: CR00          AWS Mainframe Modernization            Date: mm/dd/yy
 2   1   Prog: CORPT00C              CardDemo                       Time: hh:mm:ss
 3
 4  30                  Transaction Reports
 5
 6
 7  10   [_] Monthly (Current Month)
 8
 9  10   [_] Yearly (Current Year)
10
11  10   [_] Custom (Date Range)
12
13  15   Start Date : [MM]/[DD]/[YYYY]  (MM/DD/YYYY)
14  15     End Date : [MM]/[DD]/[YYYY]  (MM/DD/YYYY)
15
16
17
18
19   6   The Report will be submitted for printing. Please confirm: [_] (Y/N)
20
21
22
23   1   <error message area — 78 chars, RED>
24   1   ENTER=Continue  F3=Back
```

> `[_]` = unprotected input field (1 char), `[MM]` = unprotected numeric (2 chars), `[YYYY]` = unprotected numeric (4 chars)

### Field Inventory

| Field     | Row | Col | Length | Attributes         | Color     | Purpose                          |
|-----------|-----|-----|--------|--------------------|-----------|----------------------------------|
| TRNNAME   | 1   | 7   | 4      | ASKIP, FSET        | BLUE      | Transaction ID display           |
| TITLE01   | 1   | 21  | 40     | ASKIP, FSET        | YELLOW    | Application title line 1         |
| CURDATE   | 1   | 71  | 8      | ASKIP, FSET        | BLUE      | Current date (mm/dd/yy)          |
| PGMNAME   | 2   | 7   | 8      | ASKIP, FSET        | BLUE      | Program name display             |
| TITLE02   | 2   | 21  | 40     | ASKIP, FSET        | YELLOW    | Application title line 2         |
| CURTIME   | 2   | 71  | 8      | ASKIP, FSET        | BLUE      | Current time (hh:mm:ss)          |
| MONTHLY   | 7   | 10  | 1      | UNPROT, FSET, IC   | GREEN     | Monthly report selection (input)  |
| YEARLY    | 9   | 10  | 1      | UNPROT, FSET       | GREEN     | Yearly report selection (input)   |
| CUSTOM    | 11  | 10  | 1      | UNPROT, FSET       | GREEN     | Custom report selection (input)   |
| SDTMM     | 13  | 29  | 2      | UNPROT, FSET, NUM  | GREEN     | Start date — month (input)        |
| SDTDD     | 13  | 34  | 2      | UNPROT, FSET, NUM  | GREEN     | Start date — day (input)          |
| SDTYYYY   | 13  | 39  | 4      | UNPROT, FSET, NUM  | GREEN     | Start date — year (input)         |
| EDTMM     | 14  | 29  | 2      | UNPROT, FSET, NUM  | GREEN     | End date — month (input)          |
| EDTDD     | 14  | 34  | 2      | UNPROT, FSET, NUM  | GREEN     | End date — day (input)            |
| EDTYYYY   | 14  | 39  | 4      | UNPROT, FSET, NUM  | GREEN     | End date — year (input)           |
| CONFIRM   | 19  | 66  | 1      | UNPROT, FSET       | GREEN     | Confirmation (Y/N) (input)        |
| ERRMSG    | 23  | 1   | 78     | ASKIP, BRT, FSET   | RED       | Error/status message display      |

### Function Key Assignments

| Key     | Action                                           |
|---------|--------------------------------------------------|
| ENTER   | Process report selection — validate and submit    |
| PF3     | Return to Main Menu (XCTL to COMEN01C)           |
| OTHER   | Display "Invalid key pressed" error message       |

---

## Section 6: COMMAREA Structure

### CARDDEMO-COMMAREA — Defined in COCOM01Y.cpy

```
Level  Field                    PIC         Bytes  Purpose
-----  -----------------------  ----------  -----  ----------------------------------
01     CARDDEMO-COMMAREA                    178    Full COMMAREA
 05    CDEMO-GENERAL-INFO                   34     Navigation & session context
  10   CDEMO-FROM-TRANID        X(04)       4      Source transaction ID
  10   CDEMO-FROM-PROGRAM       X(08)       8      Source program name
  10   CDEMO-TO-TRANID          X(04)       4      Target transaction ID
  10   CDEMO-TO-PROGRAM         X(08)       8      Target program name
  10   CDEMO-USER-ID            X(08)       8      Authenticated user ID
  10   CDEMO-USER-TYPE          X(01)       1      'A' = Admin, 'U' = User
  10   CDEMO-PGM-CONTEXT        9(01)       1      0 = first entry, 1 = re-entry
 05    CDEMO-CUSTOMER-INFO                  84     Customer context
  10   CDEMO-CUST-ID            9(09)       9      Customer ID
  10   CDEMO-CUST-FNAME         X(25)       25     Customer first name
  10   CDEMO-CUST-MNAME         X(25)       25     Customer middle name
  10   CDEMO-CUST-LNAME         X(25)       25     Customer last name
 05    CDEMO-ACCOUNT-INFO                   12     Account context
  10   CDEMO-ACCT-ID            9(11)       11     Account ID
  10   CDEMO-ACCT-STATUS        X(01)       1      Account status
 05    CDEMO-CARD-INFO                      16     Card context
  10   CDEMO-CARD-NUM           9(16)       16     Card number
 05    CDEMO-MORE-INFO                      14     Map tracking
  10   CDEMO-LAST-MAP           X(7)        7      Last map sent
  10   CDEMO-LAST-MAPSET        X(7)        7      Last mapset sent
```

> **Note:** Unlike many other CardDemo programs (e.g., COTRN02C which has a program-specific CDEMO-CT02-INFO extension), CORPT00C does not define or use a program-specific COMMAREA extension. It uses only the general navigation fields. All report-specific state (report type, date ranges, JCL data) is maintained in WORKING-STORAGE variables and the BMS screen map fields.

### COMMAREA Field Read/Write by Program

| Field                | COSGN00C (CC00)   | COMEN01C (CM00)    | CORPT00C (CR00)    |
|----------------------|-------------------|--------------------|--------------------|
| CDEMO-FROM-TRANID    | Write (CC00)      | Write (CM00)       | Write (CR00)       |
| CDEMO-FROM-PROGRAM   | Write (COSGN00C)  | Write (COMEN01C)   | Write (CORPT00C)   |
| CDEMO-TO-TRANID      | —                 | —                  | —                  |
| CDEMO-TO-PROGRAM     | Write (target pgm)| Write (target pgm) | Write (COMEN01C or COSGN00C) |
| CDEMO-USER-ID        | Write             | Read               | Read (implicit)    |
| CDEMO-USER-TYPE      | Write             | Read               | Read (implicit)    |
| CDEMO-PGM-CONTEXT    | Write (0)         | Write (0)          | Read/Write (0→1)   |
| CDEMO-CUST-ID        | —                 | —                  | —                  |
| CDEMO-ACCT-ID        | —                 | —                  | —                  |
| CDEMO-CARD-NUM       | —                 | —                  | —                  |
| CDEMO-LAST-MAP       | —                 | —                  | —                  |
| CDEMO-LAST-MAPSET    | —                 | —                  | —                  |

### COMMAREA Inter-Program Flow Diagram

```
 COSGN00C                    COMEN01C                    CORPT00C
 --------                    --------                    --------
 Sets:                       Receives COMMAREA           Receives COMMAREA
   FROM-TRANID = CC00          via XCTL                    via XCTL
   FROM-PROGRAM = COSGN00C   Sets:                       Checks:
   USER-ID = <input>           FROM-TRANID = CM00          PGM-CONTEXT (0 or 1)
   USER-TYPE = <from USRSEC>   FROM-PROGRAM = COMEN01C   Sets:
   PGM-CONTEXT = 0             PGM-CONTEXT = 0             PGM-CONTEXT = 1 (reenter)
   TO-PROGRAM = COMEN01C     XCTLs to CORPT00C           On PF3:
 XCTLs to COMEN01C                                         TO-PROGRAM = COMEN01C
                                                            FROM-TRANID = CR00
                                                            FROM-PROGRAM = CORPT00C
                                                            PGM-CONTEXT = 0
                                                          XCTLs to COMEN01C
                                                        On pseudo-conv return:
                                                          RETURN TRANSID(CR00)
                                                            COMMAREA(CARDDEMO-COMMAREA)
```

---

## Section 7: Business Logic Flow

### Step-by-Step Narrative

#### 1. Entry and Initialization (MAIN-PARA)

1. Reset flags: ERR-FLG-OFF, TRANSACT-NOT-EOF, SEND-ERASE-YES
2. Clear WS-MESSAGE and ERRMSGO
3. **EIBCALEN check:**
   - If EIBCALEN = 0 (no COMMAREA — direct invocation): Set CDEMO-TO-PROGRAM = 'COSGN00C', XCTL to sign-on screen
   - If EIBCALEN > 0: Move DFHCOMMAREA to CARDDEMO-COMMAREA

#### 2. First Entry vs Re-entry

- **First entry** (CDEMO-PGM-CONTEXT = 0):
  - Set CDEMO-PGM-REENTER (context = 1)
  - Initialize CORPT0AO to LOW-VALUES
  - Set cursor to MONTHLY field (MONTHLYL = -1)
  - SEND MAP with ERASE → RETURN TRANSID('CR00')

- **Re-entry** (CDEMO-PGM-CONTEXT = 1):
  - RECEIVE MAP CORPT0A
  - EVALUATE EIBAID for key handling

#### 3. Key Handling (EVALUATE EIBAID)

- **DFHENTER:** → PROCESS-ENTER-KEY
- **DFHPF3:** → Set CDEMO-TO-PROGRAM = 'COMEN01C', XCTL to menu (RETURN-TO-PREV-SCREEN)
- **OTHER:** → Set error flag, display "Invalid key pressed" message, re-send screen

#### 4. Report Type Selection (PROCESS-ENTER-KEY)

The EVALUATE TRUE block checks which report type field the user selected:

##### 4a. Monthly Report
- Condition: MONTHLYI not = SPACES and LOW-VALUES
- Sets WS-REPORT-NAME = 'Monthly'
- Gets CURRENT-DATE
- Calculates start date: first day of current month (YYYY-MM-01)
- Calculates end date: last day of current month (computed via INTEGER-OF-DATE / DATE-OF-INTEGER arithmetic)
- Populates PARM-START-DATE-1, PARM-START-DATE-2, PARM-END-DATE-1, PARM-END-DATE-2 in JOB-DATA
- Calls SUBMIT-JOB-TO-INTRDR

##### 4b. Yearly Report
- Condition: YEARLYI not = SPACES and LOW-VALUES
- Sets WS-REPORT-NAME = 'Yearly'
- Gets CURRENT-DATE
- Start date: YYYY-01-01 (January 1 of current year)
- End date: YYYY-12-31 (December 31 of current year)
- Populates date parameters in JOB-DATA
- Calls SUBMIT-JOB-TO-INTRDR

##### 4c. Custom Date Range Report
- Condition: CUSTOMI not = SPACES and LOW-VALUES
- **Validation sequence** (field-by-field, stops on first error):
  1. Start Date Month — empty check
  2. Start Date Day — empty check
  3. Start Date Year — empty check
  4. End Date Month — empty check
  5. End Date Day — empty check
  6. End Date Year — empty check
  7. Numeric conversion (NUMVAL-C) for all six date fields
  8. Start Month — numeric check AND range check (> 12)
  9. Start Day — numeric check AND range check (> 31)
  10. Start Year — numeric check
  11. End Month — numeric check AND range check (> 12)
  12. End Day — numeric check AND range check (> 31)
  13. End Year — numeric check
  14. Start Date — CALL 'CSUTLDTC' for full date validation (severity check, message number 2513 tolerated)
  15. End Date — CALL 'CSUTLDTC' for full date validation
- On all validations passed: Populate PARM dates, set WS-REPORT-NAME = 'Custom', call SUBMIT-JOB-TO-INTRDR

##### 4d. No Selection
- Condition: WHEN OTHER (no report type selected)
- Display "Select a report type to print report..." message

#### 5. Job Submission (SUBMIT-JOB-TO-INTRDR)

1. **Confirmation check:** If CONFIRMI is spaces/low-values → prompt "Please confirm to print the {report-name} report..."
2. **Confirmation evaluation:**
   - 'Y' or 'y' → proceed
   - 'N' or 'n' → initialize all fields, set error flag
   - Other → display "{value} is not a valid value to confirm..."
3. **JCL submission loop:**
   - SET END-LOOP-NO
   - PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > 1000 OR END-LOOP-YES OR ERR-FLG-ON
   - Move JOB-LINES(WS-IDX) to JCL-RECORD
   - If JCL-RECORD = '/*EOF' or SPACES/LOW-VALUES → SET END-LOOP-YES
   - PERFORM WIRTE-JOBSUB-TDQ (writes to JOBS TDQ)

#### 6. TDQ Write (WIRTE-JOBSUB-TDQ)

```cobol
EXEC CICS WRITEQ TD
  QUEUE ('JOBS')
  FROM (JCL-RECORD)
  LENGTH (LENGTH OF JCL-RECORD)
  RESP(WS-RESP-CD) RESP2(WS-REAS-CD)
END-EXEC
```
- DFHRESP(NORMAL) → continue
- OTHER → set error flag, display "Unable to Write TDQ (JOBS)..."

#### 7. Success Confirmation

If no errors after job submission:
- PERFORM INITIALIZE-ALL-FIELDS (reset all input fields)
- Set ERRMSGC to DFHGREEN (green color for success)
- Display "{report-name} report submitted for printing ..."
- Re-send screen

#### 8. Screen Send (SEND-TRNRPT-SCREEN)

- PERFORM POPULATE-HEADER-INFO (date/time, titles, tran/pgm names)
- Move WS-MESSAGE to ERRMSGO
- If SEND-ERASE-YES → SEND MAP with ERASE and CURSOR
- Else → SEND MAP with CURSOR only (no ERASE)
- GO TO RETURN-TO-CICS (RETURN TRANSID CR00)

#### 9. Error Handling Paths

| Error Condition                     | Message                                          | Cursor Position |
|-------------------------------------|--------------------------------------------------|-----------------|
| EIBCALEN = 0                        | (redirect to sign-on)                            | N/A             |
| Invalid key pressed                 | "Invalid key pressed. Please see below..."       | MONTHLY         |
| No report type selected             | "Select a report type to print report..."        | MONTHLY         |
| Start date month empty              | "Start Date - Month can NOT be empty..."         | SDTMM           |
| Start date day empty                | "Start Date - Day can NOT be empty..."           | SDTDD           |
| Start date year empty               | "Start Date - Year can NOT be empty..."          | SDTYYYY         |
| End date month empty                | "End Date - Month can NOT be empty..."           | EDTMM           |
| End date day empty                  | "End Date - Day can NOT be empty..."             | EDTDD           |
| End date year empty                 | "End Date - Year can NOT be empty..."            | EDTYYYY         |
| Start month not numeric or > 12     | "Start Date - Not a valid Month..."              | SDTMM           |
| Start day not numeric or > 31       | "Start Date - Not a valid Day..."                | SDTDD           |
| Start year not numeric              | "Start Date - Not a valid Year..."               | SDTYYYY         |
| End month not numeric or > 12       | "End Date - Not a valid Month..."                | EDTMM           |
| End day not numeric or > 31         | "End Date - Not a valid Day..."                  | EDTDD           |
| End year not numeric                | "End Date - Not a valid Year..."                 | EDTYYYY         |
| Start date invalid (CSUTLDTC)       | "Start Date - Not a valid date..."               | SDTMM           |
| End date invalid (CSUTLDTC)         | "End Date - Not a valid date..."                 | EDTMM           |
| Confirmation empty                  | "Please confirm to print the {name} report..."   | CONFIRM         |
| Confirmation not Y/N                | "\"{value}\" is not a valid value to confirm..."  | CONFIRM         |
| TDQ write failure                   | "Unable to Write TDQ (JOBS)..."                  | MONTHLY         |

---

## Section 8: Complete File Inventory

### COBOL Source Programs

| File                  | Path                    | Role in Flow                   |
|-----------------------|-------------------------|--------------------------------|
| CORPT00C.cbl          | app/cbl/CORPT00C.cbl    | Target program — Report Submission |
| CSUTLDTC.cbl          | app/cbl/CSUTLDTC.cbl    | Date validation sub-program    |
| COMEN01C.cbl          | app/cbl/COMEN01C.cbl    | Upstream — Main Menu           |
| COSGN00C.cbl          | app/cbl/COSGN00C.cbl    | Upstream — Sign-on             |

### Copybooks

| File                  | Path                    | Classification                 |
|-----------------------|-------------------------|--------------------------------|
| COCOM01Y.cpy          | app/cpy/COCOM01Y.cpy    | COMMAREA definition            |
| COTTL01Y.cpy          | app/cpy/COTTL01Y.cpy    | Screen titles                  |
| CSDAT01Y.cpy          | app/cpy/CSDAT01Y.cpy    | Date/time structure            |
| CSMSG01Y.cpy          | app/cpy/CSMSG01Y.cpy    | Common messages                |
| CVTRA05Y.cpy          | app/cpy/CVTRA05Y.cpy    | Transaction record layout      |
| COMEN02Y.cpy          | app/cpy/COMEN02Y.cpy    | Menu option definitions        |
| CSUSR01Y.cpy          | app/cpy/CSUSR01Y.cpy    | User security record layout    |
| DFHAID               | (system)                 | CICS AID key constants         |
| DFHBMSCA             | (system)                 | CICS BMS attribute constants   |

### BMS Screen Maps

| File                  | Path                    | Mapset / Map                   |
|-----------------------|-------------------------|--------------------------------|
| CORPT00.bms           | app/bms/CORPT00.bms     | CORPT00 / CORPT0A              |

### BMS Symbolic Map Copybooks (Auto-generated)

| Copybook Name | Included In  | Source BMS     | Note                          |
|---------------|-------------|----------------|-------------------------------|
| CORPT00       | CORPT00C    | CORPT00.bms    | Not in app/cpy/ — auto-generated from BMS source |

### VSAM Data Files (from CSD)

| DD Name   | DSN                                          | Key Field     | Record Length | Used By          |
|-----------|----------------------------------------------|---------------|---------------|------------------|
| TRANSACT  | AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS          | TRAN-ID       | 350 (V)       | Batch job (indirect) |
| USRSEC    | AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS            | USER-ID       | Variable      | COSGN00C         |

### TDQ Queues

| Queue Name | Type              | Record Length | Used By    | Purpose                    |
|------------|-------------------|---------------|------------|----------------------------|
| JOBS       | Extra-partition   | 80            | CORPT00C   | JCL submission to internal reader |

### CSD Definitions

| Resource Type | Name     | Group     | Key Attributes                     |
|---------------|----------|-----------|------------------------------------|
| TRANSACTION   | CR00     | CARDDEMO  | PROGRAM(CORPT00C), PROFILE(DFHCICST) |
| PROGRAM       | CORPT00C | CARDDEMO  | LANGUAGE(COBOL)                    |
| MAPSET        | CORPT00  | CARDDEMO  | (BMS mapset)                       |

---

## Section 9: Transaction Record Layout

CORPT00C does not directly read or write transaction records. However, it references the TRANSACT file indirectly through the batch JCL it submits. The transaction record layout (TRAN-RECORD) is defined in CVTRA05Y.cpy — see Section 3 for the byte-level layout.

### JCL Data Structure (JOB-DATA in Working Storage)

The JCL is assembled in working storage as a series of 80-byte fixed-length records:

```
Line  Content                                              Purpose
----  ---------------------------------------------------  --------------------------
  1   //TRNRPT00 JOB 'TRAN REPORT',CLASS=A,...             Job card
  2   // NOTIFY=&SYSUID                                    Job notification
  3   //*                                                  Comment
  4   //JOBLIB JCLLIB ORDER=('AWS.M2.CARDDEMO.PROC')       Procedure library
  5   //*                                                  Comment
  6   //STEP10 EXEC PROC=TRANREPT                           Execute report proc
  7   //*                                                  Comment
  8   //STEP05R.SYMNAMES DD *                               Symbolic name definitions
  9   TRAN-CARD-NUM,263,16,ZD                               Card number field def
 10   TRAN-PROC-DT,305,10,CH                                Processing date field def
 11   PARM-START-DATE,C'{start-date}'                       Start date parameter
 12   PARM-END-DATE,C'{end-date}'                           End date parameter
 13   /*                                                   End of SYMNAMES
 14   //STEP10R.DATEPARM DD *                               Date parameters
 15   {start-date} {end-date}                               Date range values
 16   /*                                                   End of DATEPARM
 17   /*EOF                                                End marker
```

The start-date and end-date values are populated dynamically based on the report type selection (Monthly, Yearly, or Custom).

---

## Section 10: Observations

### What the Flow Demonstrates Well

1. **Clean separation of online and batch processing:** CORPT00C handles user interaction and validation online, then delegates the heavy report generation to a batch job. This is a classic mainframe pattern for long-running operations.

2. **Robust date validation:** Custom date range validation is thorough — empty checks, numeric checks, range checks, and full calendar validation via CSUTLDTC/CEEDAYS.

3. **Standard pseudo-conversational pattern:** Uses PGM-CONTEXT flag to distinguish first entry from re-entry. Standard COMMAREA-based navigation with XCTL for program transfers and RETURN TRANSID for pseudo-conversational loops.

4. **Confirmation workflow:** Requires explicit Y/N confirmation before job submission, preventing accidental report generation.

5. **Reusable COMMAREA:** Uses the standard CARDDEMO-COMMAREA without program-specific extensions. All report state is transient (working storage + screen fields).

6. **Consistent error handling:** Every error condition sets the cursor to the appropriate field and displays a descriptive error message.

### Gaps and Migration Considerations

1. **TDQ-to-Internal-Reader pattern:** The WRITEQ TD to JOBS queue for batch job submission is a z/OS-specific mechanism (extra-partition TDQ routed to MVS internal reader). This has no direct equivalent in cloud-native environments and requires a migration strategy — e.g., replacing with an API call to a job scheduling service, message queue, or serverless function trigger.

2. **Embedded JCL:** The batch JCL is hardcoded in COBOL working storage. In a modernized environment, this would need to be replaced with parameterized job templates or API-based job submission.

3. **CEEDAYS dependency:** CSUTLDTC calls the IBM Language Environment CEEDAYS intrinsic. This would need to be replaced with a platform-native date validation library.

4. **No program-specific COMMAREA extension:** While simple, this means all report parameters are lost between pseudo-conversational iterations unless the user re-enters them. The program relies on re-entry detection (PGM-CONTEXT) rather than carrying report state in the COMMAREA.

5. **SYMNAMES and DATEPARM DD cards:** The batch report procedure uses proprietary field-position-based symbolic names to reference record fields. This tight coupling to physical record layout would need to be decoupled in a modernized data access layer.

6. **No logging or audit trail:** Report submission is not logged to a VSAM file or any persistent store. In a modernized system, an audit trail of report requests would be expected.

7. **Paragraph name typo:** `WIRTE-JOBSUB-TDQ` (should be WRITE) — cosmetic but indicates limited code review. Should be corrected during migration.

---

## Section 11: Comparison — Migration Complexity Assessment

| Dimension                      | CORPT00C (CR00) Complexity | Notes                                               |
|--------------------------------|---------------------------|-----------------------------------------------------|
| **Program count**              | Low (2 in-flow + 2 upstream) | CORPT00C + CSUTLDTC + COMEN01C + COSGN00C           |
| **Copybook count**             | Low-Medium (7 app + 2 system) | Standard CardDemo shared copybooks                   |
| **VSAM file I/O**              | None (direct)             | All file I/O is in the batch job                     |
| **BMS screens**                | 1 map, 1 mapset           | Simple form with 10 input fields                     |
| **COMMAREA complexity**        | Low                       | Uses general fields only, no program-specific extension |
| **Business logic complexity**  | Medium                    | Date validation, JCL assembly, TDQ write loop        |
| **CICS command variety**       | Low                       | SEND MAP, RECEIVE MAP, WRITEQ TD, XCTL, RETURN      |
| **External dependencies**      | Medium                    | CEEDAYS (LE intrinsic), JOBS TDQ, batch procedure    |
| **Migration difficulty**       | Medium                    | TDQ/internal-reader pattern requires architectural redesign |
| **Testing complexity**         | Medium                    | Need to verify date validation, JCL assembly, TDQ write, and batch job trigger |

### Key Migration Risks

1. **TDQ replacement** — The WRITEQ TD → internal reader → batch job chain is the primary migration challenge. Requires identifying a cloud-native job submission mechanism.
2. **Batch procedure dependency** — PROC=TRANREPT must also be migrated and its SYMNAMES/DATEPARM interface preserved or redesigned.
3. **CEEDAYS replacement** — Need a date validation library equivalent in the target platform.

---

## Appendix A: Complete EXEC CICS Command Inventory for CORPT00C

| # | Command          | Location (Paragraph)         | Parameters                                                   | Error Handling              |
|---|------------------|------------------------------|--------------------------------------------------------------|-----------------------------|
| 1 | RETURN           | MAIN-PARA                    | TRANSID(WS-TRANID), COMMAREA(CARDDEMO-COMMAREA)             | None                        |
| 2 | XCTL             | RETURN-TO-PREV-SCREEN        | PROGRAM(CDEMO-TO-PROGRAM), COMMAREA(CARDDEMO-COMMAREA)       | None                        |
| 3 | SEND MAP         | SEND-TRNRPT-SCREEN (erase)   | MAP('CORPT0A'), MAPSET('CORPT00'), FROM(CORPT0AO), ERASE, CURSOR | None                   |
| 4 | SEND MAP         | SEND-TRNRPT-SCREEN (no-erase)| MAP('CORPT0A'), MAPSET('CORPT00'), FROM(CORPT0AO), CURSOR    | None                        |
| 5 | RETURN           | RETURN-TO-CICS               | TRANSID(WS-TRANID), COMMAREA(CARDDEMO-COMMAREA)             | None                        |
| 6 | RECEIVE MAP      | RECEIVE-TRNRPT-SCREEN        | MAP('CORPT0A'), MAPSET('CORPT00'), INTO(CORPT0AI), RESP, RESP2 | RESP/RESP2 captured      |
| 7 | WRITEQ TD        | WIRTE-JOBSUB-TDQ             | QUEUE('JOBS'), FROM(JCL-RECORD), LENGTH(80), RESP, RESP2     | DFHRESP(NORMAL)/OTHER       |

## Appendix B: Complete CALL Statement Inventory for CORPT00C

| # | Call Type | Target     | Location (Paragraph)  | Parameters (USING)                               | Classification      |
|---|-----------|------------|-----------------------|--------------------------------------------------|---------------------|
| 1 | Literal   | CSUTLDTC   | PROCESS-ENTER-KEY (start date) | CSUTLDTC-DATE, CSUTLDTC-DATE-FORMAT, CSUTLDTC-RESULT | In-repo (app/cbl/CSUTLDTC.cbl) |
| 2 | Literal   | CSUTLDTC   | PROCESS-ENTER-KEY (end date)   | CSUTLDTC-DATE, CSUTLDTC-DATE-FORMAT, CSUTLDTC-RESULT | In-repo (app/cbl/CSUTLDTC.cbl) |

## Appendix C: Complete CALL Statement Inventory for CSUTLDTC

| # | Call Type | Target   | Location (Paragraph) | Parameters (USING)                                    | Classification      |
|---|-----------|----------|----------------------|-------------------------------------------------------|---------------------|
| 1 | Literal   | CEEDAYS  | A000-MAIN            | WS-DATE-TO-TEST, WS-DATE-FORMAT, OUTPUT-LILLIAN, FEEDBACK-CODE | LE Intrinsic (external) |

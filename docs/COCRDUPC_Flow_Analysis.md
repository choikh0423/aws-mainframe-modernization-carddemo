# CCUP Transaction Flow Analysis — COCRDUPC (Credit Card Update)

## Section 1: Flow Overview

| Attribute            | Value                                              |
|----------------------|----------------------------------------------------|
| **Transaction ID**   | CCUP                                               |
| **Entry Program**    | COCRDUPC                                           |
| **Function**         | Accept and process credit card detail update        |
| **Mapset / Map**     | COCRDUP / CCRDUPA                                  |
| **Data Access**      | VSAM (CARDDAT)                                     |
| **Pseudo-Conv.**     | Yes — `RETURN TRANSID('CCUP') COMMAREA(WS-COMMAREA)` |

### Complete Navigation Path

```
  COSGN00C (CC00 Sign-on)
       |
       v
  COMEN01C (CM00 Main Menu)
       |
       +--[Option 3]--> COCRDLIC (CCLI Credit Card List)
       |                     |
       |                     +--[Select card / PF12]--> COCRDUPC (CCUP Credit Card Update) **
       |
       +--[Option 5]--> COCRDUPC (CCUP Credit Card Update) **  (direct from menu)
```

COCRDUPC can also be reached from:
- **COACTVWC** (Account View) — which holds `LIT-CARDUPDATEPGM = 'COCRDUPC'`
- **COACTUPC** (Account Update) — which holds a literal `'COCRDUPC'` / `'CCUP'`

### Pseudo-Conversational Lifecycle

```
  +-----------+     RETURN TRANSID('CCUP')      +-----------+
  |  1st Entry|  -----------------------------> | CICS CEDA |
  | EIBCALEN=0|  <-----------------------------  | (suspend) |
  |  or fresh |     terminal input (EIBAID)     +-----------+
  +-----------+
       |
       v
  Display empty      User enters         Validate &        User presses
  search screen  --> Acct# + Card# -->  fetch card   -->  edits fields
       |                                  details          (name, status,
       |                                     |              exp month/year)
       v                                     v                   |
  RETURN TRANSID  <--  Show card data   RETURN TRANSID  <--------+
       |                  on screen          |
       v                                     v
  Re-enter:                            Re-enter:
  receive edits,                       validate changes,
  compare old/new                      prompt F5 to confirm
       |                                     |
       v                                     v
  No change? -->  re-display           F5 pressed -->  READ UPDATE + REWRITE
  Changes?   -->  show confirmation         |
                  "Press F5 to save"        v
                                       Success --> "Changes committed"
                                       Failure --> error message
                                            |
                                            v
                                       PF3 / Enter --> XCTL back to caller
```

**PGM-CONTEXT flag** (`CDEMO-PGM-CONTEXT`):
- `0` (CDEMO-PGM-ENTER): First entry into the program
- `1` (CDEMO-PGM-REENTER): Subsequent pseudo-conversational re-entry

**CCUP-CHANGE-ACTION states** (program-specific state machine in `WS-THIS-PROGCOMMAREA`):
| Value | 88-level Name                    | Meaning                                    |
|-------|----------------------------------|--------------------------------------------|
| LOW-VALUES/SPACES | CCUP-DETAILS-NOT-FETCHED | Initial state, no card data loaded |
| `'S'` | CCUP-SHOW-DETAILS               | Card details fetched and displayed          |
| `'E'` | CCUP-CHANGES-NOT-OK             | Edit validation errors found                |
| `'N'` | CCUP-CHANGES-OK-NOT-CONFIRMED   | Changes validated, awaiting F5 confirm      |
| `'C'` | CCUP-CHANGES-OKAYED-AND-DONE    | Update committed successfully               |
| `'L'` | CCUP-CHANGES-OKAYED-LOCK-ERROR  | Could not lock record for update            |
| `'F'` | CCUP-CHANGES-OKAYED-BUT-FAILED  | Lock obtained but REWRITE failed            |

---

## Section 2: Programs Involved

| Program    | Type          | Function                              | Called By        | Call Method              |
|------------|---------------|---------------------------------------|------------------|--------------------------|
| COSGN00C   | CICS online   | Sign-on screen                        | (entry point)    | Transaction CC00         |
| COMEN01C   | CICS online   | Main Menu                             | COSGN00C         | XCTL                     |
| COCRDLIC   | CICS online   | Credit Card List                      | COMEN01C (opt 3) | XCTL                     |
| COCRDUPC   | CICS online   | **Credit Card Update (target)**       | COMEN01C (opt 5) / COCRDLIC | XCTL      |
| COACTVWC   | CICS online   | Account View                          | COMEN01C (opt 1) | XCTL                     |
| COACTUPC   | CICS online   | Account Update                        | COMEN01C (opt 2) | XCTL                     |
| COCRDSLC   | CICS online   | Credit Card View (detail)             | COCRDLIC         | XCTL                     |

**Notes:**
- COCRDUPC does **not** issue any `CALL` statements — all inter-program communication is via `EXEC CICS XCTL`.
- COCRDUPC does **not** issue any `EXEC CICS LINK` statements.
- COACTVWC and COACTUPC hold COCRDUPC program name as a literal but XCTL to it via `CDEMO-TO-PROGRAM` variable resolution.

---

## Section 3: Copybooks

| Copybook   | Used By   | Classification            | Purpose / Layout Summary                                                |
|------------|-----------|---------------------------|-------------------------------------------------------------------------|
| COCOM01Y   | COCRDUPC  | COMMAREA definition       | `CARDDEMO-COMMAREA`: navigation fields (FROM/TO TRANID/PROGRAM), user info, customer/account/card IDs, last map/mapset |
| CVCRD01Y   | COCRDUPC  | Work area                 | `CC-WORK-AREAS`: AID key flag (CCARD-AID-*), next prog/mapset/map, error/return messages, account/card/customer IDs |
| COTTL01Y   | COCRDUPC  | Screen title              | `CCDA-SCREEN-TITLE`: TITLE01 ("AWS Mainframe Modernization"), TITLE02 ("CardDemo"), THANK-YOU message |
| COCRDUP    | COCRDUPC  | BMS symbolic map (generated) | `CCRDUPAI` (input) / `CCRDUPAO` (output): screen field structures for CCRDUPA map — includes ACCTSID, CARDSID, CRDNAME, CRDSTCD, EXPMON, EXPYEAR, EXPDAY, INFOMSG, ERRMSG, FKEYS, FKEYSC |
| CSDAT01Y   | COCRDUPC  | Date/time structure       | `WS-DATE-TIME`: current date (YYYY-MM-DD), time (HH:MM:SS), formatted date (MM/DD/YY), timestamp |
| CSMSG01Y   | COCRDUPC  | Common messages           | `CCDA-COMMON-MESSAGES`: thank-you message, invalid key message         |
| CSMSG02Y   | COCRDUPC  | Abend variables           | `ABEND-DATA`: abend code, culprit program, reason, message (used in ABEND-ROUTINE) |
| CSUSR01Y   | COCRDUPC  | Signed-on user data       | `SEC-USER-DATA`: user ID, first/last name, password, user type, filler |
| CVACT02Y   | COCRDUPC  | Card record layout        | `CARD-RECORD` (150 bytes): CARD-NUM(X16), CARD-ACCT-ID(9-11), CARD-CVV-CD(9-3), CARD-EMBOSSED-NAME(X50), CARD-EXPIRAION-DATE(X10), CARD-ACTIVE-STATUS(X1), FILLER(X59) |
| CVCUS01Y   | COCRDUPC  | Customer record layout    | `CUSTOMER-RECORD` (500 bytes): CUST-ID, name, address, phone, SSN, DOB, EFT account, FICO score, filler |
| CSSTRPFY   | COCRDUPC  | PF key mapping (procedure)| `YYYY-STORE-PFKEY` paragraph: maps EIBAID to CCARD-AID-* 88-level flags (ENTER, CLEAR, PA1, PA2, PFK01-PFK12) |
| DFHAID     | COCRDUPC  | CICS system copybook      | IBM-supplied AID byte constants (DFHENTER, DFHCLEAR, DFHPF1-DFHPF24, DFHPA1-DFHPA3) |
| DFHBMSCA   | COCRDUPC  | CICS system copybook      | IBM-supplied BMS attribute constants (DFHBMFSE, DFHBMPRF, DFHBMDAR, DFHBMBRY, DFHRED, DFHDFCOL, etc.) |
| COMEN02Y   | COMEN01C  | Menu options table         | `CARDDEMO-MAIN-MENU-OPTIONS`: 11 menu options with number, name, program name, user type — option 5 maps to COCRDUPC |

---

## Section 4: VSAM File Operations

### Files Accessed by COCRDUPC

| VSAM File (DD) | CSD DSNAME                                  | Key Field               | Record Copybook | Record Length |
|----------------|---------------------------------------------|-------------------------|-----------------|---------------|
| CARDDAT        | AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS         | CARD-NUM (X16)          | CVACT02Y (CARD-RECORD) | 150 bytes |
| CARDAIX        | AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX.PATH     | (Alternate index path)  | CVACT02Y (CARD-RECORD) | 150 bytes |

> **Note:** `CARDAIX` is defined as `LIT-CARDFILENAME-ACCT-PATH` in WORKING-STORAGE but is **not used** in any EXEC CICS command in COCRDUPC. Only `CARDDAT` (`LIT-CARDFILENAME`) is actively accessed.

### I/O Operations Detail

| # | Section               | Command         | File     | Key (RIDFLD)          | Into/From         | Purpose                                             | RESP Handling |
|---|----------------------|-----------------|----------|-----------------------|-------------------|------------------------------------------------------|---------------|
| 1 | 9100-GETCARD-BYACCTCARD | `READ`       | CARDDAT  | WS-CARD-RID-CARDNUM (X16) | INTO(CARD-RECORD) | Fetch card details by card number for display         | NORMAL: set found flag; NOTFND: set not-found error; OTHER: build file error message |
| 2 | 9200-WRITE-PROCESSING   | `READ UPDATE` | CARDDAT  | WS-CARD-RID-CARDNUM (X16) | INTO(CARD-RECORD) | Lock record for update (pessimistic locking)          | NORMAL: proceed; OTHER: set COULD-NOT-LOCK-FOR-UPDATE |
| 3 | 9200-WRITE-PROCESSING   | `REWRITE`     | CARDDAT  | (held from READ UPDATE) | FROM(CARD-UPDATE-RECORD) | Write updated card details back to VSAM            | NORMAL: continue; OTHER: set LOCKED-BUT-UPDATE-FAILED |

### VSAM File Flow Diagram

```
                     COCRDUPC
                        |
    +-------------------+-------------------+
    |                                       |
    v                                       v
 [9100-GETCARD]                    [9200-WRITE-PROCESSING]
    |                                       |
    | READ CARDDAT                          | READ UPDATE CARDDAT
    | Key: WS-CARD-RID-CARDNUM              | Key: WS-CARD-RID-CARDNUM
    | Into: CARD-RECORD                     | Into: CARD-RECORD
    |                                       |
    | RESP?                                 | RESP = NORMAL?
    |  NORMAL  -> display card              |   Yes -> check for concurrent change
    |  NOTFND  -> error msg                 |          (9300-CHECK-CHANGE-IN-REC)
    |  OTHER   -> file error msg            |   No  -> COULD-NOT-LOCK-FOR-UPDATE
    |                                       |
    |                                       | Record unchanged?
    |                                       |   Yes -> REWRITE CARDDAT
    |                                       |          FROM: CARD-UPDATE-RECORD
    |                                       |          RESP = NORMAL -> success
    |                                       |          OTHER -> LOCKED-BUT-UPDATE-FAILED
    |                                       |   No  -> DATA-WAS-CHANGED-BEFORE-UPDATE
    |                                       |          (refresh old values, re-display)
    v                                       v
```

---

## Section 5: BMS Screen Map

### Map: CCRDUPA (Mapset: COCRDUP)

**Screen Size:** 24 rows x 80 columns

```
Row Col  Field
+------------------------------------------------------------------------+
|Tran: CCUP   AWS Mainframe Modernization          Date: mm/dd/yy       | Row 1
|Prog: COCRDUPC         CardDemo                   Time: hh:mm:ss       | Row 2
|                                                                        | Row 3
|                              Update Credit Card Details                | Row 4
|                                                                        | Row 5
|                                                                        | Row 6
|                       Account Number    : ___________                  | Row 7
|                       Card Number       : ________________             | Row 8
|                                                                        | Row 9
|                                                                        | Row 10
|    Name on card      : ________________________________________________| Row 11
|                                                                        | Row 12
|    Card Active Y/N   : _                                               | Row 13
|                                                                        | Row 14
|    Expiry Date       : MM / YYYY  dd                                   | Row 15
|                                                                        | Rows 16-19
|                         (info message area)                            | Row 20
|                                                                        | Rows 21-22
|(error message - 80 chars, red, bright)                                 | Row 23
|ENTER=Process F3=Exit F5=Save F12=Cancel                                | Row 24
+------------------------------------------------------------------------+
```

### Field Inventory

| Field Name | Row | Col | Length | Attributes          | Color     | Purpose                    |
|------------|-----|-----|--------|---------------------|-----------|----------------------------|
| TRNNAME    | 1   | 7   | 4      | ASKIP, FSET, NORM   | Blue      | Transaction ID display     |
| TITLE01    | 1   | 21  | 40     | ASKIP, NORM         | Yellow    | Application title line 1   |
| CURDATE    | 1   | 71  | 8      | ASKIP, NORM         | Blue      | Current date (mm/dd/yy)    |
| PGMNAME    | 2   | 7   | 8      | ASKIP, NORM         | Blue      | Program name display       |
| TITLE02    | 2   | 21  | 40     | ASKIP, NORM         | Yellow    | Application title line 2   |
| CURTIME    | 2   | 71  | 8      | ASKIP, NORM         | Blue      | Current time (hh:mm:ss)    |
| ACCTSID    | 7   | 45  | 11     | FSET, IC, NORM, PROT | Default (underline) | Account number (input/display) |
| CARDSID    | 8   | 45  | 16     | FSET, NORM, UNPROT  | Default (underline) | Card number (input)        |
| CRDNAME    | 11  | 25  | 50     | UNPROT (underline)  | Default   | Name on card (editable)    |
| CRDSTCD    | 13  | 25  | 1      | UNPROT (underline)  | Default   | Card active status Y/N     |
| EXPMON     | 15  | 25  | 2      | UNPROT (underline, RIGHT) | Default | Expiry month (MM)    |
| EXPYEAR    | 15  | 30  | 4      | UNPROT (underline, RIGHT) | Default | Expiry year (YYYY)   |
| EXPDAY     | 15  | 36  | 2      | DRK, FSET, PROT     | Default   | Expiry day (hidden)        |
| INFOMSG    | 20  | 25  | 40     | PROT                | Neutral   | Informational message      |
| ERRMSG     | 23  | 1   | 80     | ASKIP, BRT, FSET    | Red       | Error message              |
| FKEYS      | 24  | 1   | 21     | ASKIP, NORM         | Yellow    | "ENTER=Process F3=Exit"    |
| FKEYSC     | 24  | 23  | 18     | ASKIP, DRK          | Yellow    | "F5=Save F12=Cancel" (initially hidden) |

### Function Key Assignments

| Key   | Action                                                                       |
|-------|-----------------------------------------------------------------------------|
| ENTER | Process current input — validate search keys or validate field edits         |
| PF3   | Exit — XCTL back to calling program (COCRDLIC or COMEN01C)                  |
| PF5   | Save — Commit validated changes (only active when CCUP-CHANGES-OK-NOT-CONFIRMED) |
| PF12  | Cancel — Revert to fetched card details (only active when details are shown)  |

### Dynamic Attribute Behavior

The program dynamically adjusts field attributes based on the current state:

| State                      | ACCTSID/CARDSID | CRDNAME/CRDSTCD/EXPMON/EXPYEAR |
|----------------------------|-----------------|-------------------------------|
| CCUP-DETAILS-NOT-FETCHED   | DFHBMFSE (editable, cursor set) | DFHBMPRF (protected) |
| CCUP-SHOW-DETAILS          | DFHBMPRF (protected) | DFHBMFSE (editable)          |
| CCUP-CHANGES-NOT-OK        | DFHBMPRF (protected) | DFHBMFSE (editable)          |
| CCUP-CHANGES-OK-NOT-CONFIRMED | DFHBMPRF (protected) | DFHBMPRF (protected, all locked) |
| CCUP-CHANGES-OKAYED-AND-DONE | DFHBMPRF (protected) | DFHBMPRF (protected)        |

Fields with validation errors are highlighted in **DFHRED** (red). Blank required fields show `'*'` in red.

---

## Section 6: COMMAREA Structure

### CARDDEMO-COMMAREA (from COCOM01Y.cpy)

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
      10 CDEMO-CUST-FNAME           PIC X(25)    -- Customer first name
      10 CDEMO-CUST-MNAME           PIC X(25)    -- Customer middle name
      10 CDEMO-CUST-LNAME           PIC X(25)    -- Customer last name
   05 CDEMO-ACCOUNT-INFO
      10 CDEMO-ACCT-ID              PIC 9(11)    -- Account ID
      10 CDEMO-ACCT-STATUS          PIC X(01)    -- Account status
   05 CDEMO-CARD-INFO
      10 CDEMO-CARD-NUM             PIC 9(16)    -- Card number
   05 CDEMO-MORE-INFO
      10 CDEMO-LAST-MAP             PIC X(7)     -- Last map displayed
      10 CDEMO-LAST-MAPSET          PIC X(7)     -- Last mapset displayed
```

### WS-THIS-PROGCOMMAREA (COCRDUPC-specific, appended after CARDDEMO-COMMAREA)

```
01 WS-THIS-PROGCOMMAREA
   05 CARD-UPDATE-SCREEN-DATA
      10 CCUP-CHANGE-ACTION         PIC X(1)     -- State machine flag
   05 CCUP-OLD-DETAILS                            -- Original card values
      10 CCUP-OLD-ACCTID            PIC X(11)
      10 CCUP-OLD-CARDID            PIC X(16)
      10 CCUP-OLD-CVV-CD            PIC X(3)
      10 CCUP-OLD-CARDDATA
         20 CCUP-OLD-CRDNAME        PIC X(50)
         20 CCUP-OLD-EXPIRAION-DATE
            25 CCUP-OLD-EXPYEAR     PIC X(4)
            25 CCUP-OLD-EXPMON      PIC X(2)
            25 CCUP-OLD-EXPDAY      PIC X(2)
         20 CCUP-OLD-CRDSTCD        PIC X(1)
   05 CCUP-NEW-DETAILS                            -- User-entered new values
      10 CCUP-NEW-ACCTID            PIC X(11)
      10 CCUP-NEW-CARDID            PIC X(16)
      10 CCUP-NEW-CVV-CD            PIC X(3)
      10 CCUP-NEW-CARDDATA
         20 CCUP-NEW-CRDNAME        PIC X(50)
         20 CCUP-NEW-EXPIRAION-DATE
            25 CCUP-NEW-EXPYEAR     PIC X(4)
            25 CCUP-NEW-EXPMON      PIC X(2)
            25 CCUP-NEW-EXPDAY      PIC X(2)
         20 CCUP-NEW-CRDSTCD        PIC X(1)
   05 CARD-UPDATE-RECORD                          -- Prepared update buffer
      10 CARD-UPDATE-NUM            PIC X(16)
      10 CARD-UPDATE-ACCT-ID        PIC 9(11)
      10 CARD-UPDATE-CVV-CD         PIC 9(03)
      10 CARD-UPDATE-EMBOSSED-NAME  PIC X(50)
      10 CARD-UPDATE-EXPIRAION-DATE PIC X(10)
      10 CARD-UPDATE-ACTIVE-STATUS  PIC X(01)
      10 FILLER                     PIC X(59)
```

### COMMAREA Passing Mechanism

The COMMAREA is passed as a **concatenation** of `CARDDEMO-COMMAREA` + `WS-THIS-PROGCOMMAREA` in `WS-COMMAREA` (PIC X(2000)):

```cobol
MOVE CARDDEMO-COMMAREA    TO WS-COMMAREA
MOVE WS-THIS-PROGCOMMAREA TO
     WS-COMMAREA(LENGTH OF CARDDEMO-COMMAREA + 1:
                  LENGTH OF WS-THIS-PROGCOMMAREA)
EXEC CICS RETURN TRANSID(LIT-THISTRANID) COMMAREA(WS-COMMAREA) ...
```

On re-entry, the first part is unpacked into `CARDDEMO-COMMAREA` and the remainder into `WS-THIS-PROGCOMMAREA`.

### Inter-Program COMMAREA Flow

```
COMEN01C                    COCRDLIC                   COCRDUPC
----------                  ----------                 ----------
Sets:                       Sets:                      Reads:
 CDEMO-FROM-TRANID='CM00'   CDEMO-FROM-TRANID='CCLI'   CDEMO-FROM-TRANID
 CDEMO-FROM-PROGRAM=         CDEMO-FROM-PROGRAM=         CDEMO-FROM-PROGRAM
   'COMEN01C'                  'COCRDLIC'                CDEMO-ACCT-ID
 CDEMO-PGM-CONTEXT=0        CDEMO-ACCT-ID              CDEMO-CARD-NUM
                             CDEMO-CARD-NUM             CDEMO-PGM-CONTEXT
                             CDEMO-PGM-CONTEXT=0        CDEMO-LAST-MAPSET
                             CDEMO-LAST-MAPSET=
                               'COCRDLI'
                                                       Sets (on exit PF3):
                                                        CDEMO-FROM-TRANID='CCUP'
                                                        CDEMO-FROM-PROGRAM=
                                                          'COCRDUPC'
                                                        CDEMO-TO-PROGRAM=
                                                          (caller or COMEN01C)
                                                        CDEMO-LAST-MAPSET=
                                                          'COCRDUP'
                                                        CDEMO-LAST-MAP=
                                                          'CCRDUPA'
```

---

## Section 7: Business Logic Flow

### Step-by-Step Narrative

#### 1. Entry and Initialization (0000-MAIN)

1. `EXEC CICS HANDLE ABEND LABEL(ABEND-ROUTINE)` — set up abend handler
2. Initialize `CC-WORK-AREA`, `WS-MISC-STORAGE`, `WS-COMMAREA`
3. Set `WS-TRANID = 'CCUP'`
4. Clear error messages (`WS-RETURN-MSG-OFF`)
5. **EIBCALEN check**: If EIBCALEN = 0 (no COMMAREA — direct start) or (FROM-PROGRAM = COMEN01C and not re-entering):
   - Initialize `CARDDEMO-COMMAREA` and `WS-THIS-PROGCOMMAREA`
   - Set `CDEMO-PGM-ENTER` (context = 0)
   - Set `CCUP-DETAILS-NOT-FETCHED`
6. Otherwise: unpack COMMAREA from `DFHCOMMAREA` into `CARDDEMO-COMMAREA` + `WS-THIS-PROGCOMMAREA`
7. Map EIBAID to `CCARD-AID-*` flag via `YYYY-STORE-PFKEY` (CSSTRPFY copybook)

#### 2. PF Key Validation

Valid keys at this point:
- **ENTER** — always valid
- **PF3** — always valid (exit)
- **PF5** — valid only when `CCUP-CHANGES-OK-NOT-CONFIRMED` (save confirmation)
- **PF12** — valid only when details have been fetched (cancel edits)

Invalid keys are treated as ENTER.

#### 3. Main EVALUATE Dispatch (lines 429-543)

| Condition | Action |
|-----------|--------|
| **PF3 pressed** OR **update done/failed and came from card list** | XCTL back to calling program (or main menu if no caller). Executes `SYNCPOINT` before transfer. If came from COCRDLIC, zeroes out ACCT-ID and CARD-NUM. |
| **First entry from COCRDLIC** (CDEMO-PGM-ENTER + FROM-PROGRAM = COCRDLIC) OR **PF12 from COCRDLIC** | Account/card already in COMMAREA — immediately fetch card data (`9000-READ-DATA`), display details, RETURN TRANSID |
| **Fresh entry** (CCUP-DETAILS-NOT-FETCHED + CDEMO-PGM-ENTER) OR **from main menu** | Show empty search screen, set re-enter flag, RETURN TRANSID |
| **Update completed or failed** | Reset state, show empty search screen for new search |
| **OTHER** (re-entry with data on screen) | Process inputs (`1000`), decide action (`2000`), send map (`3000`), RETURN TRANSID |

#### 4. Input Processing (1000-PROCESS-INPUTS)

**4a. Receive Map (1100-RECEIVE-MAP)**
- `EXEC CICS RECEIVE MAP(LIT-THISMAP) MAPSET(LIT-THISMAPSET) INTO(CCRDUPAI)`
- Extract fields from BMS symbolic map into `CCUP-NEW-*` fields
- Replace `'*'` or SPACES with LOW-VALUES (blank indicator)

**4b. Edit Map Inputs (1200-EDIT-MAP-INPUTS)**

If details not yet fetched (search phase):
- **1210-EDIT-ACCOUNT**: Validate account number — must be supplied, numeric, 11 digits
- **1220-EDIT-CARD**: Validate card number — must be supplied, numeric, 16 digits
- If both blank: "No input received" error

If details already fetched (edit phase):
- Compare `CCUP-NEW-CARDDATA` to `CCUP-OLD-CARDDATA` (case-insensitive via `FUNCTION UPPER-CASE`)
- If no changes detected: set `NO-CHANGES-DETECTED`, skip field edits
- If changes exist:
  - **1230-EDIT-NAME**: Must be non-blank, alphabetic characters and spaces only (uses INSPECT CONVERTING)
  - **1240-EDIT-CARDSTATUS**: Must be 'Y' or 'N'
  - **1250-EDIT-EXPIRY-MON**: Must be numeric, 1-12
  - **1260-EDIT-EXPIRY-YEAR**: Must be numeric, 1950-2099

If all edits pass: set `CCUP-CHANGES-OK-NOT-CONFIRMED`

#### 5. Decide Action (2000-DECIDE-ACTION)

| Current State | Action |
|--------------|--------|
| CCUP-DETAILS-NOT-FETCHED or PF12 (cancel) | If valid search keys: `9000-READ-DATA` to fetch card, then `CCUP-SHOW-DETAILS` |
| CCUP-SHOW-DETAILS | If input error or no changes: do nothing; else set `CCUP-CHANGES-OK-NOT-CONFIRMED` |
| CCUP-CHANGES-NOT-OK | Continue (re-display with errors) |
| CCUP-CHANGES-OK-NOT-CONFIRMED + PF5 | Execute `9200-WRITE-PROCESSING`; result: lock error, update failed, data changed by another user, or success |
| CCUP-CHANGES-OK-NOT-CONFIRMED (no PF5) | Continue (re-display confirmation prompt) |
| CCUP-CHANGES-OKAYED-AND-DONE | Reset, show updated details |
| OTHER | Unexpected state — ABEND with code '9999' |

#### 6. Data Fetch (9000-READ-DATA → 9100-GETCARD-BYACCTCARD)

1. Initialize `CCUP-OLD-DETAILS`
2. Set key: `WS-CARD-RID-CARDNUM = CC-CARD-NUM`
3. `EXEC CICS READ FILE(CARDDAT) RIDFLD(WS-CARD-RID-CARDNUM) INTO(CARD-RECORD)`
4. On NORMAL: set `FOUND-CARDS-FOR-ACCOUNT`, copy card fields to CCUP-OLD-* (convert name to uppercase)
5. On NOTFND: set error "Did not find cards for this search condition"
6. On OTHER: build file error message with RESP/RESP2 codes

#### 7. Update Processing (9200-WRITE-PROCESSING)

1. `EXEC CICS READ FILE(CARDDAT) UPDATE RIDFLD(WS-CARD-RID-CARDNUM) INTO(CARD-RECORD)` — pessimistic lock
2. If lock fails: `COULD-NOT-LOCK-FOR-UPDATE`, exit
3. **Optimistic concurrency check** (`9300-CHECK-CHANGE-IN-REC`):
   - Compare current CARD-RECORD fields against `CCUP-OLD-*` values
   - If any field changed by another user: `DATA-WAS-CHANGED-BEFORE-UPDATE`, refresh old values, exit
4. Prepare `CARD-UPDATE-RECORD`:
   - Card number, account ID, CVV code, embossed name
   - Expiration date formatted as `YYYY-MM-DD` via STRING
   - Active status
5. `EXEC CICS REWRITE FILE(CARDDAT) FROM(CARD-UPDATE-RECORD)`
6. On success: processing continues (caller sets `CCUP-CHANGES-OKAYED-AND-DONE`)
7. On failure: `LOCKED-BUT-UPDATE-FAILED`

#### 8. Screen Output (3000-SEND-MAP)

1. **3100-SCREEN-INIT**: Initialize output map, set titles, date/time
2. **3200-SETUP-SCREEN-VARS**: Populate output fields based on state (old values for show, new values for changes-made)
3. **3250-SETUP-INFOMSG**: Set informational message based on state
4. **3300-SETUP-SCREEN-ATTRS**: Set field protection (editable/protected) and color (red for errors) based on state; position cursor at first error field
5. **3400-SEND-SCREEN**: `EXEC CICS SEND MAP(CCARD-NEXT-MAP) MAPSET(CCARD-NEXT-MAPSET) FROM(CCRDUPAO) CURSOR ERASE FREEKB`

#### 9. Pseudo-Conversational Return (COMMON-RETURN)

```cobol
MOVE WS-RETURN-MSG TO CCARD-ERROR-MSG
MOVE CARDDEMO-COMMAREA TO WS-COMMAREA
MOVE WS-THIS-PROGCOMMAREA TO WS-COMMAREA(offset)
EXEC CICS RETURN TRANSID(LIT-THISTRANID) COMMAREA(WS-COMMAREA) LENGTH(LENGTH OF WS-COMMAREA)
```

#### 10. Abend Handling (ABEND-ROUTINE)

1. If no message set: "UNEXPECTED ABEND OCCURRED."
2. Set culprit = `LIT-THISPGM`
3. `EXEC CICS SEND FROM(ABEND-DATA) LENGTH(...) NOHANDLE ERASE`
4. `EXEC CICS HANDLE ABEND CANCEL`
5. `EXEC CICS ABEND ABCODE('9999')`

### Error Handling Summary

| Error Condition | Message | Recovery |
|----------------|---------|----------|
| Account not supplied | "Account number not provided" | Re-display search screen |
| Account not numeric | "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER" | Re-display search screen |
| Card not supplied | "Card number not provided" | Re-display search screen |
| Card not numeric | "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" | Re-display search screen |
| No input at all | "No input received" | Re-display search screen |
| Card not found | "Did not find cards for this search condition" | Re-display search screen |
| Name blank | "Card name not provided" | Re-display edit screen |
| Name not alpha | "Card name can only contain alphabets and spaces" | Re-display edit screen |
| Status not Y/N | "Card Active Status must be Y or N" | Re-display edit screen |
| Month invalid | "Card expiry month must be between 1 and 12" | Re-display edit screen |
| Year invalid | "Invalid card expiry year" | Re-display edit screen |
| No changes | "No change detected with respect to values fetched." | Re-display edit screen |
| Lock failure | "Could not lock record for update" | Re-display, inform failure |
| Concurrent change | "Record changed by some one else. Please review" | Refresh old values, re-display |
| Rewrite failure | "Update of record failed" | Re-display, inform failure |
| File I/O error | "File Error: READ on CARDDAT returned RESP xxx,RESP2 xxx" | Re-display with error |
| Unexpected state | ABEND '9999' "UNEXPECTED DATA SCENARIO" | Program terminates |

---

## Section 8: Complete File Inventory

### COBOL Source Files

| File | Path | Role |
|------|------|------|
| COCRDUPC.cbl | `app/cbl/COCRDUPC.cbl` | **Target program** — Credit Card Update |
| COCRDLIC.cbl | `app/cbl/COCRDLIC.cbl` | Upstream — Credit Card List (XCTL to COCRDUPC) |
| COMEN01C.cbl | `app/cbl/COMEN01C.cbl` | Upstream — Main Menu (XCTL to COCRDUPC via option 5) |
| COSGN00C.cbl | `app/cbl/COSGN00C.cbl` | Upstream — Sign-on screen |
| COACTVWC.cbl | `app/cbl/COACTVWC.cbl` | Related — Account View (holds COCRDUPC literal) |
| COACTUPC.cbl | `app/cbl/COACTUPC.cbl` | Related — Account Update (holds COCRDUPC literal) |
| COCRDSLC.cbl | `app/cbl/COCRDSLC.cbl` | Related — Credit Card View (sibling via COCRDLIC) |

### Copybook Files

| File | Path | Classification |
|------|------|---------------|
| COCOM01Y.cpy | `app/cpy/COCOM01Y.cpy` | COMMAREA definition |
| CVCRD01Y.cpy | `app/cpy/CVCRD01Y.cpy` | Work area (AID keys, navigation) |
| COTTL01Y.cpy | `app/cpy/COTTL01Y.cpy` | Screen titles |
| COCRDUP.CPY  | `app/cpy-bms/COCRDUP.CPY` | BMS symbolic map (generated) |
| CSDAT01Y.cpy | `app/cpy/CSDAT01Y.cpy` | Date/time structure |
| CSMSG01Y.cpy | `app/cpy/CSMSG01Y.cpy` | Common messages |
| CSMSG02Y.cpy | `app/cpy/CSMSG02Y.cpy` | Abend variables |
| CSUSR01Y.cpy | `app/cpy/CSUSR01Y.cpy` | User security data |
| CVACT02Y.cpy | `app/cpy/CVACT02Y.cpy` | Card record layout |
| CVCUS01Y.cpy | `app/cpy/CVCUS01Y.cpy` | Customer record layout |
| CSSTRPFY.cpy | `app/cpy/CSSTRPFY.cpy` | PF key mapping procedure |
| COMEN02Y.cpy | `app/cpy/COMEN02Y.cpy` | Menu options table (used by COMEN01C) |
| DFHAID     | (system)   | CICS system copybook — AID key constants |
| DFHBMSCA   | (system)   | CICS system copybook — BMS attribute constants |

### BMS Map Files

| File | Path | Map Name | Mapset Name |
|------|------|----------|-------------|
| COCRDUP.bms | `app/bms/COCRDUP.bms` | CCRDUPA | COCRDUP |

### CSD Definitions

| Resource | Type | Path |
|----------|------|------|
| COCRDUP  | MAPSET | `app/csd/CARDDEMO.CSD` (line 128) |
| COCRDUPC | PROGRAM | `app/csd/CARDDEMO.CSD` |
| CARDDAT  | FILE | `app/csd/CARDDEMO.CSD` (line 25) |
| CARDAIX  | FILE | `app/csd/CARDDEMO.CSD` (line 13) |

---

## Section 9: Transaction Record Layout

### CARD-RECORD (VSAM CARDDAT — CVACT02Y.cpy)

| Offset | Length | Field Name            | PIC        | Description                    |
|--------|--------|-----------------------|------------|--------------------------------|
| 0      | 16     | CARD-NUM              | X(16)      | Card number (primary key)      |
| 16     | 11     | CARD-ACCT-ID          | 9(11)      | Associated account ID          |
| 27     | 3      | CARD-CVV-CD           | 9(03)      | CVV security code              |
| 30     | 50     | CARD-EMBOSSED-NAME    | X(50)      | Name embossed on card          |
| 80     | 10     | CARD-EXPIRAION-DATE   | X(10)      | Expiry date (YYYY-MM-DD)       |
| 90     | 1      | CARD-ACTIVE-STATUS    | X(01)      | Active flag (Y/N)              |
| 91     | 59     | FILLER                | X(59)      | Reserved/unused                |
| **Total** | **150** |                    |            |                                |

### CARD-UPDATE-RECORD (in-program update buffer)

| Offset | Length | Field Name                  | PIC        | Description                    |
|--------|--------|-----------------------------|------------|--------------------------------|
| 0      | 16     | CARD-UPDATE-NUM             | X(16)      | Card number                    |
| 16     | 11     | CARD-UPDATE-ACCT-ID         | 9(11)      | Account ID                     |
| 27     | 3      | CARD-UPDATE-CVV-CD          | 9(03)      | CVV code                       |
| 30     | 50     | CARD-UPDATE-EMBOSSED-NAME   | X(50)      | Updated name                   |
| 80     | 10     | CARD-UPDATE-EXPIRAION-DATE  | X(10)      | Updated expiry (YYYY-MM-DD)    |
| 90     | 1      | CARD-UPDATE-ACTIVE-STATUS   | X(01)      | Updated active status          |
| 91     | 59     | FILLER                      | X(59)      | Reserved                       |
| **Total** | **150** |                          |            |                                |

### Fields Modified by Update

| Field | Editable by User | Validation |
|-------|-----------------|------------|
| CARD-EMBOSSED-NAME | Yes | Non-blank, alpha + spaces only |
| CARD-ACTIVE-STATUS | Yes | Must be 'Y' or 'N' |
| CARD-EXPIRAION-DATE (month) | Yes | Numeric, 1-12 |
| CARD-EXPIRAION-DATE (year) | Yes | Numeric, 1950-2099 |
| CARD-EXPIRAION-DATE (day) | No (hidden, preserved from original) | N/A |
| CARD-NUM | No (display only) | N/A |
| CARD-ACCT-ID | No (display only) | N/A |
| CARD-CVV-CD | No (preserved from original) | N/A |

---

## Section 10: Observations

### What the Flow Demonstrates Well

1. **Pseudo-conversational pattern**: Clean implementation with `CCUP-CHANGE-ACTION` state machine providing fine-grained control over the multi-step update lifecycle (search → display → edit → confirm → commit).

2. **Optimistic concurrency control**: The `9300-CHECK-CHANGE-IN-REC` paragraph compares all mutable fields against stored original values before committing, preventing lost updates from concurrent users.

3. **Pessimistic locking**: Uses `READ UPDATE` to acquire an exclusive lock on the VSAM record before the rewrite, combined with the optimistic check for a defense-in-depth approach.

4. **Input validation**: Comprehensive field-level validation with specific error messages, color highlighting (red for errors), cursor positioning to the first error field, and `'*'` placeholder for blank required fields.

5. **Navigation flexibility**: Supports both direct access (menu option 5) and indirect access (from card list COCRDLIC), with proper return-path tracking via COMMAREA FROM-PROGRAM / FROM-TRANID.

6. **Separation of concerns**: Clear paragraph structure separating input processing (1000), decision logic (2000), screen output (3000), data read (9000/9100), and write processing (9200).

### Gaps and Migration Considerations

1. **No audit trail**: Updates are committed directly to CARDDAT with no transaction log, audit record, or timestamp of the change. A modernized version should add audit logging.

2. **Case normalization**: Card name is converted to uppercase on read (`INSPECT CONVERTING LIT-LOWER TO LIT-UPPER`) but not on write — the new name is stored as-entered. This inconsistency could cause comparison issues.

3. **CARDAIX unused**: The `LIT-CARDFILENAME-ACCT-PATH` variable (CARDAIX) is defined but never used in any EXEC CICS command within COCRDUPC. It may be a remnant from an earlier design.

4. **CVCUS01Y included but unused**: The customer record layout (`CUSTOMER-RECORD`) is copied but no customer file I/O occurs in COCRDUPC. The copybook may have been included for future expansion or was inherited from a template.

5. **CSMSG01Y included but unused inline**: The common messages copybook is included but the messages (`CCDA-MSG-THANK-YOU`, `CCDA-MSG-INVALID-KEY`) are not referenced in the PROCEDURE DIVISION of COCRDUPC.

6. **Hidden expiry day**: The EXPDAY field is DRK+PROT (hidden from user) — users cannot change the day portion of the expiry date. This business rule should be documented for the migration target.

7. **SYNCPOINT only on PF3/exit path**: The `EXEC CICS SYNCPOINT` is issued only when exiting via PF3 or after completion, not after the REWRITE itself. The REWRITE is implicitly committed on the next SYNCPOINT or program termination.

8. **WS-COMMAREA size**: Fixed at PIC X(2000), which may be larger than needed. Modern implementations should right-size the communication buffer.

9. **No DB2/IMS**: This is a pure VSAM application — no SQL or DL/I calls to trace. Migration to a relational database will require mapping the VSAM key-based access patterns to SQL queries.

---

## Section 11: Comparison — CICS vs Modern Target

| Aspect | CICS/COBOL (Current) | Modern Target (e.g., Java/Spring, .NET) |
|--------|---------------------|----------------------------------------|
| **UI** | BMS 3270 terminal map (COCRDUP.bms) | Web form / SPA (React, Angular) |
| **State Management** | COMMAREA + pseudo-conversational RETURN TRANSID | HTTP session / JWT token / database session |
| **Data Access** | VSAM KSDS via EXEC CICS READ/REWRITE | JPA/Hibernate or JDBC against RDBMS |
| **Concurrency** | READ UPDATE (lock) + field-by-field comparison | Optimistic locking via version column / ETag |
| **Navigation** | EXEC CICS XCTL with COMMAREA | URL routing / REST API endpoints |
| **Validation** | COBOL EVALUATE/IF with 88-levels | Bean Validation (@NotNull, @Pattern) / form validators |
| **Error Handling** | RESP/RESP2 codes + 88-level flags | Exceptions / error response DTOs |
| **Program Structure** | Paragraph-based (PERFORM THRU) | Method-based (service/controller layers) |
| **Audit** | None | Built-in audit trail (JPA Auditing, event sourcing) |
| **Complexity** | 1561 lines, 1 VSAM file, 1 BMS map | Moderate — standard CRUD with validation |

---

## EXEC CICS Command Inventory

| # | Line(s) | Command | Parameters | Section |
|---|---------|---------|------------|---------|
| 1 | 370-372 | `HANDLE ABEND` | `LABEL(ABEND-ROUTINE)` | 0000-MAIN |
| 2 | 469-471 | `SYNCPOINT` | (none) | PF3 exit path |
| 3 | 473-476 | `XCTL` | `PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA)` | PF3 exit path |
| 4 | 554-558 | `RETURN` | `TRANSID(LIT-THISTRANID) COMMAREA(WS-COMMAREA) LENGTH(LENGTH OF WS-COMMAREA)` | COMMON-RETURN |
| 5 | 579-584 | `RECEIVE MAP` | `MAP(LIT-THISMAP) MAPSET(LIT-THISMAPSET) INTO(CCRDUPAI) RESP(WS-RESP-CD) RESP2(WS-REAS-CD)` | 1100-RECEIVE-MAP |
| 6 | 1329-1336 | `SEND MAP` | `MAP(CCARD-NEXT-MAP) MAPSET(CCARD-NEXT-MAPSET) FROM(CCRDUPAO) CURSOR ERASE FREEKB RESP(WS-RESP-CD)` | 3400-SEND-SCREEN |
| 7 | 1382-1390 | `READ` | `FILE(LIT-CARDFILENAME) RIDFLD(WS-CARD-RID-CARDNUM) KEYLENGTH(LENGTH OF WS-CARD-RID-CARDNUM) INTO(CARD-RECORD) LENGTH(LENGTH OF CARD-RECORD) RESP(WS-RESP-CD) RESP2(WS-REAS-CD)` | 9100-GETCARD-BYACCTCARD |
| 8 | 1427-1436 | `READ UPDATE` | `FILE(LIT-CARDFILENAME) UPDATE RIDFLD(WS-CARD-RID-CARDNUM) KEYLENGTH(LENGTH OF WS-CARD-RID-CARDNUM) INTO(CARD-RECORD) LENGTH(LENGTH OF CARD-RECORD) RESP(WS-RESP-CD) RESP2(WS-REAS-CD)` | 9200-WRITE-PROCESSING |
| 9 | 1477-1483 | `REWRITE` | `FILE(LIT-CARDFILENAME) FROM(CARD-UPDATE-RECORD) LENGTH(LENGTH OF CARD-UPDATE-RECORD) RESP(WS-RESP-CD) RESP2(WS-REAS-CD)` | 9200-WRITE-PROCESSING |
| 10 | 1539-1543 | `SEND` | `FROM(ABEND-DATA) LENGTH(LENGTH OF ABEND-DATA) NOHANDLE ERASE` | ABEND-ROUTINE |
| 11 | 1546-1548 | `HANDLE ABEND` | `CANCEL` | ABEND-ROUTINE |
| 12 | 1550-1552 | `ABEND` | `ABCODE('9999')` | ABEND-ROUTINE |

---

*Generated by CICS Transaction Flow Analysis — COCRDUPC (CCUP)*
*Repository: choikh0423/aws-mainframe-modernization-carddemo*
*Branch: demos/cobol-full-docs*

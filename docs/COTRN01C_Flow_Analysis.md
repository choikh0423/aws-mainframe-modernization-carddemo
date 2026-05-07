# CT01 Transaction Flow Analysis — COTRN01C (Transaction View/Detail)

## Section 1: Flow Overview

| Attribute           | Value                                              |
|---------------------|----------------------------------------------------|
| **Transaction ID**  | CT01                                               |
| **Entry Program**   | COTRN01C                                           |
| **Function**        | View a single transaction record from TRANSACT file|
| **Data Access**     | VSAM (TRANSACT KSDS)                               |
| **BMS Mapset**      | COTRN01 / Map COTRN1A                              |
| **Pseudo-conv.**    | Yes — RETURN TRANSID('CT01') with COMMAREA         |

### Complete Navigation Path

```
 Terminal
   |
   | CC00 (COSGN00C) Sign-on Screen
   |   User enters User ID + Password
   |   EXEC CICS READ USRSEC -> validate credentials
   |   If regular user:
   |     EXEC CICS XCTL -> COMEN01C
   |
   | CM00 (COMEN01C) Main Menu
   |   Option 6: "Transaction List"   -> XCTL -> COTRN00C
   |   Option 7: "Transaction View"   -> XCTL -> COTRN01C  (direct)
   |
   | CT00 (COTRN00C) Transaction List
   |   User selects a transaction row with 'S'
   |   Sets CDEMO-CT00-TRN-SELECTED with chosen Tran ID
   |   EXEC CICS XCTL -> COTRN01C
   |
   v
 CT01 (COTRN01C) Transaction View  <-- TARGET
   |   Displays single transaction detail
   |   EXEC CICS READ TRANSACT by TRAN-ID
   |   PF3 -> XCTL back to caller (COTRN00C or COMEN01C)
   |   PF5 -> XCTL to COTRN00C (Browse Transactions)
   |   PF4 -> Clear screen
   |   ENTER -> Fetch transaction by entered ID
```

### Pseudo-Conversational Lifecycle

```
  First Entry (CDEMO-PGM-CONTEXT = 0)
  +-----------------------------------------+
  | 1. Set CDEMO-PGM-REENTER = 1            |
  | 2. MOVE LOW-VALUES to map output area   |
  | 3. If TRN-SELECTED not empty:           |
  |    - Pre-fill TRNIDIN with selection     |
  |    - PERFORM PROCESS-ENTER-KEY (auto-   |
  |      fetch the selected transaction)     |
  | 4. SEND MAP COTRN1A                     |
  | 5. RETURN TRANSID('CT01') w/ COMMAREA   |
  +-----------------------------------------+
            |
            v
  Re-Entry (CDEMO-PGM-CONTEXT = 1)
  +-----------------------------------------+
  | 1. RECEIVE MAP COTRN1A                  |
  | 2. EVALUATE EIBAID:                     |
  |    ENTER -> PROCESS-ENTER-KEY           |
  |    PF3   -> XCTL to caller              |
  |    PF4   -> Clear screen & re-send      |
  |    PF5   -> XCTL to COTRN00C           |
  |    OTHER -> "Invalid key" message       |
  | 3. RETURN TRANSID('CT01') w/ COMMAREA   |
  +-----------------------------------------+
```

---

## Section 2: Programs Involved

| # | Program    | Type         | Tran ID | Function                      | Called By   | Call Method       |
|---|------------|-------------|---------|-------------------------------|-------------|-------------------|
| 1 | COSGN00C   | CICS Online | CC00    | Sign-on / Authentication      | (terminal)  | Initial entry     |
| 2 | COMEN01C   | CICS Online | CM00    | Main Menu (regular users)     | COSGN00C    | XCTL              |
| 3 | COTRN00C   | CICS Online | CT00    | Transaction List (browse)     | COMEN01C    | XCTL (option 6)   |
| 4 | **COTRN01C** | **CICS Online** | **CT01** | **Transaction View/Detail** | COTRN00C / COMEN01C | **XCTL** |

### Program Source Paths

| Program    | Source Path               |
|------------|---------------------------|
| COSGN00C   | `app/cbl/COSGN00C.cbl`   |
| COMEN01C   | `app/cbl/COMEN01C.cbl`   |
| COTRN00C   | `app/cbl/COTRN00C.cbl`   |
| COTRN01C   | `app/cbl/COTRN01C.cbl`   |

> **Note:** COTRN01C has NO `CALL` statements. There are no sub-program invocations. All logic is self-contained within the single program.

---

## Section 3: Copybooks

| # | Copybook   | Used By              | Classification          | Purpose                                             |
|---|------------|----------------------|-------------------------|-----------------------------------------------------|
| 1 | COCOM01Y   | COTRN01C, COTRN00C, COMEN01C, COSGN00C | COMMAREA definition | Navigation fields, customer/account/card info       |
| 2 | COTRN01    | COTRN01C             | BMS symbolic map        | Auto-generated symbolic map for COTRN1A (I/O areas) |
| 3 | COTTL01Y   | COTRN01C, COTRN00C, COMEN01C, COSGN00C | Screen title/message | Application title strings (CCDA-TITLE01, TITLE02)   |
| 4 | CSDAT01Y   | COTRN01C, COTRN00C, COMEN01C, COSGN00C | Date/time structure  | WS-CURDATE-DATA, WS-CURTIME, WS-TIMESTAMP formats  |
| 5 | CSMSG01Y   | COTRN01C, COTRN00C, COMEN01C            | Screen message       | Common messages (CCDA-MSG-INVALID-KEY, THANK-YOU)   |
| 6 | CVTRA05Y   | COTRN01C, COTRN00C   | Record layout           | TRAN-RECORD layout for TRANSACT VSAM file (350 bytes)|
| 7 | DFHAID     | COTRN01C, COTRN00C, COMEN01C, COSGN00C | CICS system copybook | AID key constants (DFHENTER, DFHPF3, etc.)          |
| 8 | DFHBMSCA   | COTRN01C, COTRN00C, COMEN01C, COSGN00C | CICS system copybook | BMS attribute constants                              |

> **DFHAID** and **DFHBMSCA** are IBM CICS system-supplied copybooks. They are NOT expected in `app/cpy/`.

### Copybook Record Layouts

#### COCOM01Y — COMMAREA (CARDDEMO-COMMAREA)

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

#### CDEMO-CT01-INFO (Program-Specific Extension in COTRN01C)

Defined inline immediately after `COPY COCOM01Y`:

```
   05 CDEMO-CT01-INFO.
      10 CDEMO-CT01-TRNID-FIRST        PIC X(16).
      10 CDEMO-CT01-TRNID-LAST         PIC X(16).
      10 CDEMO-CT01-PAGE-NUM           PIC 9(08).
      10 CDEMO-CT01-NEXT-PAGE-FLG      PIC X(01) VALUE 'N'.
         88 NEXT-PAGE-YES              VALUE 'Y'.
         88 NEXT-PAGE-NO               VALUE 'N'.
      10 CDEMO-CT01-TRN-SEL-FLG        PIC X(01).
      10 CDEMO-CT01-TRN-SELECTED       PIC X(16).
```

#### CVTRA05Y — TRAN-RECORD (TRANSACT File, 350 bytes)

```
01  TRAN-RECORD.
    05  TRAN-ID                        PIC X(16).
    05  TRAN-TYPE-CD                   PIC X(02).
    05  TRAN-CAT-CD                    PIC 9(04).
    05  TRAN-SOURCE                    PIC X(10).
    05  TRAN-DESC                      PIC X(100).
    05  TRAN-AMT                       PIC S9(09)V99.
    05  TRAN-MERCHANT-ID               PIC 9(09).
    05  TRAN-MERCHANT-NAME             PIC X(50).
    05  TRAN-MERCHANT-CITY             PIC X(50).
    05  TRAN-MERCHANT-ZIP              PIC X(10).
    05  TRAN-CARD-NUM                  PIC X(16).
    05  TRAN-ORIG-TS                   PIC X(26).
    05  TRAN-PROC-TS                   PIC X(26).
    05  FILLER                         PIC X(20).
```

#### COTTL01Y — Screen Titles

```
01 CCDA-SCREEN-TITLE.
   05 CCDA-TITLE01    PIC X(40) VALUE '      AWS Mainframe Modernization       '.
   05 CCDA-TITLE02    PIC X(40) VALUE '              CardDemo                  '.
   05 CCDA-THANK-YOU  PIC X(40) VALUE 'Thank you for using CCDA application... '.
```

#### CSDAT01Y — Date/Time Structure

```
01 WS-DATE-TIME.
   05 WS-CURDATE-DATA.
      10 WS-CURDATE.
         15 WS-CURDATE-YEAR            PIC 9(04).
         15 WS-CURDATE-MONTH           PIC 9(02).
         15 WS-CURDATE-DAY             PIC 9(02).
      10 WS-CURDATE-N REDEFINES WS-CURDATE   PIC 9(08).
      10 WS-CURTIME.
         15 WS-CURTIME-HOURS           PIC 9(02).
         15 WS-CURTIME-MINUTE          PIC 9(02).
         15 WS-CURTIME-SECOND          PIC 9(02).
         15 WS-CURTIME-MILSEC          PIC 9(02).
      10 WS-CURTIME-N REDEFINES WS-CURTIME   PIC 9(08).
   05 WS-CURDATE-MM-DD-YY.
      10 WS-CURDATE-MM                 PIC 9(02).
      10 FILLER                        PIC X(01) VALUE '/'.
      10 WS-CURDATE-DD                 PIC 9(02).
      10 FILLER                        PIC X(01) VALUE '/'.
      10 WS-CURDATE-YY                 PIC 9(02).
   05 WS-CURTIME-HH-MM-SS.
      10 WS-CURTIME-HH                 PIC 9(02).
      10 FILLER                        PIC X(01) VALUE ':'.
      10 WS-CURTIME-MM                 PIC 9(02).
      10 FILLER                        PIC X(01) VALUE ':'.
      10 WS-CURTIME-SS                 PIC 9(02).
   05 WS-TIMESTAMP.
      10 WS-TIMESTAMP-DT-YYYY          PIC 9(04).
      10 FILLER                        PIC X(01) VALUE '-'.
      10 WS-TIMESTAMP-DT-MM            PIC 9(02).
      10 FILLER                        PIC X(01) VALUE '-'.
      10 WS-TIMESTAMP-DT-DD            PIC 9(02).
      10 FILLER                        PIC X(01) VALUE ' '.
      10 WS-TIMESTAMP-TM-HH            PIC 9(02).
      10 FILLER                        PIC X(01) VALUE ':'.
      10 WS-TIMESTAMP-TM-MM            PIC 9(02).
      10 FILLER                        PIC X(01) VALUE ':'.
      10 WS-TIMESTAMP-TM-SS            PIC 9(02).
      10 FILLER                        PIC X(01) VALUE '.'.
      10 WS-TIMESTAMP-TM-MS6           PIC 9(06).
```

#### CSMSG01Y — Common Messages

```
01 CCDA-COMMON-MESSAGES.
   05 CCDA-MSG-THANK-YOU         PIC X(50) VALUE
        'Thank you for using CardDemo application...      '.
   05 CCDA-MSG-INVALID-KEY       PIC X(50) VALUE
        'Invalid key pressed. Please see below...         '.
```

---

## Section 4: VSAM File Operations

### Files Accessed by COTRN01C

| VSAM File  | DD Name    | Key Field | Key Len | Record Copybook | Record Len | Access Pattern    |
|------------|------------|-----------|---------|-----------------|------------|-------------------|
| TRANSACT   | TRANSACT   | TRAN-ID   | 16      | CVTRA05Y        | 350        | Direct READ (keyed) |

### EXEC CICS File I/O Commands in COTRN01C

| # | Command       | Dataset    | RIDFLD  | Into/From     | Options             | Paragraph               | Error Handling                           |
|---|---------------|-----------|---------|---------------|---------------------|--------------------------|------------------------------------------|
| 1 | READ          | TRANSACT  | TRAN-ID | TRAN-RECORD   | UPDATE, KEYLENGTH   | READ-TRANSACT-FILE       | NORMAL: continue; NOTFND: error msg "Transaction ID NOT found..."; OTHER: error msg "Unable to lookup Transaction..." |

### VSAM File Flow Diagram

```
  COTRN01C
     |
     |  User enters Transaction ID in TRNIDIN field
     |  (or pre-filled from COTRN00C selection)
     |
     v
  +---------------------------+
  | MOVE TRNIDINI -> TRAN-ID  |
  +---------------------------+
     |
     v
  +----------------------------------+
  | EXEC CICS READ                   |
  |   DATASET('TRANSACT')            |
  |   INTO(TRAN-RECORD)              |
  |   RIDFLD(TRAN-ID)                |
  |   KEYLENGTH(16)                  |
  |   UPDATE                         |
  |   RESP(WS-RESP-CD)              |
  +----------------------------------+
     |
     +-- NORMAL -----> Display all fields on screen
     |                 (TRAN-ID, TRAN-CARD-NUM, TRAN-TYPE-CD,
     |                  TRAN-CAT-CD, TRAN-SOURCE, TRAN-AMT,
     |                  TRAN-DESC, TRAN-ORIG-TS, TRAN-PROC-TS,
     |                  TRAN-MERCHANT-ID, TRAN-MERCHANT-NAME,
     |                  TRAN-MERCHANT-CITY, TRAN-MERCHANT-ZIP)
     |
     +-- NOTFND -----> "Transaction ID NOT found..."
     |
     +-- OTHER -------> "Unable to lookup Transaction..."
                        DISPLAY 'RESP:' WS-RESP-CD 'REAS:' WS-REAS-CD
```

> **Note:** The READ command uses the `UPDATE` option, which places an exclusive lock on the record. This is unusual for a view-only screen and may represent an oversight or a pattern where the record was originally intended to be modifiable. For migration, this could be relaxed to a non-update read.

---

## Section 5: BMS Screen Map

### Map Details

| Attribute     | Value           |
|---------------|-----------------|
| Mapset        | COTRN01         |
| Map           | COTRN1A         |
| Source        | `app/bms/COTRN01.bms` |
| Size          | 24 rows x 80 columns |
| CTRL          | ALARM, FREEKB   |
| LANG          | COBOL           |
| MODE          | INOUT           |

### ASCII Screen Layout

```
Row Col Field          Content / Purpose
--- --- -------------- --------------------------------------------------
 1   1  (literal)      "Tran:"
 1   7  TRNNAME        Transaction ID (CT01) — ASKIP, Blue
 1  21  TITLE01        "      AWS Mainframe Modernization       " — Yellow
 1  65  (literal)      "Date:"
 1  71  CURDATE        mm/dd/yy — ASKIP, Blue
 2   1  (literal)      "Prog:"
 2   7  PGMNAME        Program name (COTRN01C) — ASKIP, Blue
 2  21  TITLE02        "              CardDemo                  " — Yellow
 2  65  (literal)      "Time:"
 2  71  CURTIME        hh:mm:ss — ASKIP, Blue
 4  30  (literal)      "View Transaction" — BRT, Neutral
 6   6  (literal)      "Enter Tran ID:" — Turquoise
 6  21  TRNIDIN        [________________] — UNPROT, Green, Underline, IC (16)
 8   6  (literal)      "----------------------------------------------..." (separator line)
10   6  (literal)      "Transaction ID:" — Turquoise
10  22  TRNID          [________________] — ASKIP, Blue (16)
10  45  (literal)      "Card Number:" — Turquoise
10  58  CARDNUM        [________________] — ASKIP, Blue (16)
12   6  (literal)      "Type CD:" — Turquoise
12  15  TTYPCD         [__] — ASKIP, Blue (2)
12  23  (literal)      "Category CD:" — Turquoise
12  36  TCATCD         [____] — ASKIP, Blue (4)
12  46  (literal)      "Source:" — Turquoise
12  54  TRNSRC         [__________] — ASKIP, Blue (10)
14   6  (literal)      "Description:" — Turquoise
14  19  TDESC          [____________________________...] — ASKIP, Blue (60)
16   6  (literal)      "Amount:" — Turquoise
16  14  TRNAMT         [____________] — ASKIP, Blue (12)
16  31  (literal)      "Orig Date:" — Turquoise
16  42  TORIGDT        [__________] — ASKIP, Blue (10)
16  57  (literal)      "Proc Date:" — Turquoise
16  68  TPROCDT        [__________] — ASKIP, Blue (10)
18   6  (literal)      "Merchant ID:" — Turquoise
18  19  MID            [_________] — ASKIP, Blue (9)
18  33  (literal)      "Merchant Name:" — Turquoise
18  48  MNAME          [______________________________] — ASKIP, Blue (30)
20   6  (literal)      "Merchant City:" — Turquoise
20  21  MCITY          [_________________________] — ASKIP, Blue (25)
20  53  (literal)      "Merchant Zip:" — Turquoise
20  67  MZIP           [__________] — ASKIP, Blue (10)
23   1  ERRMSG         [error/info message area — 78 chars] — BRT, Red
24   1  (literal)      "ENTER=Fetch  F3=Back  F4=Clear  F5=Browse Tran." — Yellow
```

### Field Inventory

| Field Name | Row | Col | Length | Attributes         | Color     | Purpose                   |
|------------|-----|-----|--------|--------------------|-----------|---------------------------|
| TRNNAME    |  1  |  7  |   4    | ASKIP,FSET,NORM    | Blue      | Transaction ID header     |
| TITLE01    |  1  | 21  |  40    | ASKIP,FSET,NORM    | Yellow    | Application title line 1  |
| CURDATE    |  1  | 71  |   8    | ASKIP,FSET,NORM    | Blue      | Current date              |
| PGMNAME    |  2  |  7  |   8    | ASKIP,FSET,NORM    | Blue      | Program name header       |
| TITLE02    |  2  | 21  |  40    | ASKIP,FSET,NORM    | Yellow    | Application title line 2  |
| CURTIME    |  2  | 71  |   8    | ASKIP,FSET,NORM    | Blue      | Current time              |
| TRNIDIN    |  6  | 21  |  16    | UNPROT,FSET,IC,NORM| Green     | **Input:** Transaction ID |
| TRNID      | 10  | 22  |  16    | ASKIP,NORM         | Blue      | Display: Transaction ID   |
| CARDNUM    | 10  | 58  |  16    | ASKIP,NORM         | Blue      | Display: Card number      |
| TTYPCD     | 12  | 15  |   2    | ASKIP,NORM         | Blue      | Display: Type code        |
| TCATCD     | 12  | 36  |   4    | ASKIP,NORM         | Blue      | Display: Category code    |
| TRNSRC     | 12  | 54  |  10    | ASKIP,NORM         | Blue      | Display: Source           |
| TDESC      | 14  | 19  |  60    | ASKIP,NORM         | Blue      | Display: Description      |
| TRNAMT     | 16  | 14  |  12    | ASKIP,NORM         | Blue      | Display: Amount           |
| TORIGDT    | 16  | 42  |  10    | ASKIP,NORM         | Blue      | Display: Orig timestamp   |
| TPROCDT    | 16  | 68  |  10    | ASKIP,NORM         | Blue      | Display: Proc timestamp   |
| MID        | 18  | 19  |   9    | ASKIP,NORM         | Blue      | Display: Merchant ID      |
| MNAME      | 18  | 48  |  30    | ASKIP,NORM         | Blue      | Display: Merchant name    |
| MCITY      | 20  | 21  |  25    | ASKIP,NORM         | Blue      | Display: Merchant city    |
| MZIP       | 20  | 67  |  10    | ASKIP,NORM         | Blue      | Display: Merchant zip     |
| ERRMSG     | 23  |  1  |  78    | ASKIP,BRT,FSET     | Red       | Error/info message area   |

### Function Key Assignments

| Key   | Action                                                      | Target Program |
|-------|-------------------------------------------------------------|----------------|
| ENTER | Fetch transaction by ID entered in TRNIDIN                  | (self)         |
| PF3   | Go back to calling program (FROM-PROGRAM or COMEN01C)       | Dynamic        |
| PF4   | Clear all display fields and reset screen                   | (self)         |
| PF5   | Browse Transactions — navigate to transaction list          | COTRN00C       |
| OTHER | Display "Invalid key pressed" error message                 | (self)         |

---

## Section 6: COMMAREA Structure

### COMMAREA Field Flow Between Programs

```
  COSGN00C (Sign-on)
  Sets:
    CDEMO-FROM-TRANID  = 'CC00'
    CDEMO-FROM-PROGRAM = 'COSGN00C'
    CDEMO-USER-ID      = (authenticated user)
    CDEMO-USER-TYPE    = 'A' or 'U'
    CDEMO-PGM-CONTEXT  = 0
       |
       | XCTL -> COMEN01C
       v
  COMEN01C (Main Menu)
  Reads:  CDEMO-USER-TYPE (to check admin access)
  Sets:
    CDEMO-FROM-TRANID  = 'CM00'
    CDEMO-FROM-PROGRAM = 'COMEN01C'
    CDEMO-PGM-CONTEXT  = 0
       |
       | XCTL -> COTRN00C (option 6) or COTRN01C (option 7)
       v
  COTRN00C (Transaction List)
  Reads:  CDEMO-CT00-TRN-SELECTED, CDEMO-CT00-TRN-SEL-FLG
  Sets:
    CDEMO-TO-PROGRAM   = 'COTRN01C'
    CDEMO-FROM-TRANID  = 'CT00'
    CDEMO-FROM-PROGRAM = 'COTRN00C'
    CDEMO-PGM-CONTEXT  = 0
    CDEMO-CT00-TRN-SELECTED = (selected tran ID)
       |
       | XCTL -> COTRN01C
       v
  COTRN01C (Transaction View) <-- TARGET
  Reads:
    CDEMO-PGM-CONTEXT        (0=first entry, 1=re-entry)
    CDEMO-CT01-TRN-SELECTED  (pre-selected tran ID from list)
    CDEMO-FROM-PROGRAM       (to determine PF3 return target)
  Sets:
    CDEMO-PGM-REENTER = 1    (on first entry)
    CDEMO-FROM-TRANID = 'CT01'
    CDEMO-FROM-PROGRAM = 'COTRN01C'
    CDEMO-PGM-CONTEXT = 0    (before XCTL out)
    CDEMO-TO-PROGRAM   = target program (PF3/PF5)
```

### COMMAREA Tree Diagram

```
CARDDEMO-COMMAREA
├── CDEMO-GENERAL-INFO
│   ├── CDEMO-FROM-TRANID          PIC X(04)   [R/W by COTRN01C]
│   ├── CDEMO-FROM-PROGRAM         PIC X(08)   [R/W by COTRN01C]
│   ├── CDEMO-TO-TRANID            PIC X(04)   [not used by COTRN01C]
│   ├── CDEMO-TO-PROGRAM           PIC X(08)   [W by COTRN01C for XCTL target]
│   ├── CDEMO-USER-ID              PIC X(08)   [R — set by COSGN00C]
│   ├── CDEMO-USER-TYPE            PIC X(01)   [R — set by COSGN00C]
│   └── CDEMO-PGM-CONTEXT          PIC 9(01)   [R/W — controls pseudo-conv]
├── CDEMO-CUSTOMER-INFO
│   ├── CDEMO-CUST-ID              PIC 9(09)   [not used by COTRN01C]
│   ├── CDEMO-CUST-FNAME           PIC X(25)   [not used by COTRN01C]
│   ├── CDEMO-CUST-MNAME           PIC X(25)   [not used by COTRN01C]
│   └── CDEMO-CUST-LNAME           PIC X(25)   [not used by COTRN01C]
├── CDEMO-ACCOUNT-INFO
│   ├── CDEMO-ACCT-ID              PIC 9(11)   [not used by COTRN01C]
│   └── CDEMO-ACCT-STATUS          PIC X(01)   [not used by COTRN01C]
├── CDEMO-CARD-INFO
│   └── CDEMO-CARD-NUM             PIC 9(16)   [not used by COTRN01C]
├── CDEMO-MORE-INFO
│   ├── CDEMO-LAST-MAP             PIC X(7)    [not used by COTRN01C]
│   └── CDEMO-LAST-MAPSET          PIC X(7)    [not used by COTRN01C]
└── CDEMO-CT01-INFO  (program-specific extension)
    ├── CDEMO-CT01-TRNID-FIRST     PIC X(16)   [not actively used in COTRN01C]
    ├── CDEMO-CT01-TRNID-LAST      PIC X(16)   [not actively used in COTRN01C]
    ├── CDEMO-CT01-PAGE-NUM        PIC 9(08)   [not actively used in COTRN01C]
    ├── CDEMO-CT01-NEXT-PAGE-FLG   PIC X(01)   [not actively used in COTRN01C]
    ├── CDEMO-CT01-TRN-SEL-FLG     PIC X(01)   [not actively used in COTRN01C]
    └── CDEMO-CT01-TRN-SELECTED    PIC X(16)   [R — pre-selected tran from list]
```

> **Note:** The `CDEMO-CT01-INFO` extension mirrors the structure of `CDEMO-CT00-INFO` from COTRN00C. In COTRN01C, only `CDEMO-CT01-TRN-SELECTED` is actively read (to auto-fetch a transaction selected from the list screen). The remaining fields in this extension are defined but not actively referenced in the COTRN01C procedure division.

---

## Section 7: Business Logic Flow

### Step-by-Step Narrative

#### 1. Entry and Initialization (MAIN-PARA)

1. Reset flags: `ERR-FLG-OFF`, `USR-MODIFIED-NO`
2. Clear message areas: `WS-MESSAGE`, `ERRMSGO`
3. **EIBCALEN check:** If `EIBCALEN = 0` (no COMMAREA — direct invocation without transfer from another program), redirect to sign-on screen (`COSGN00C`) via XCTL

#### 2. First Entry (CDEMO-PGM-CONTEXT = 0)

1. Copy `DFHCOMMAREA` into `CARDDEMO-COMMAREA`
2. Set `CDEMO-PGM-REENTER = 1` (so next iteration is a re-entry)
3. Initialize map output area (`COTRN1AO`) to `LOW-VALUES`
4. Set cursor to Transaction ID input field (`TRNIDINL = -1`)
5. **Auto-fetch check:** If `CDEMO-CT01-TRN-SELECTED` is not spaces/low-values (i.e., a transaction was pre-selected from the list screen):
   - Copy selected transaction ID to `TRNIDINI` input field
   - Perform `PROCESS-ENTER-KEY` to immediately fetch and display the transaction
6. Send the screen via `SEND-TRNVIEW-SCREEN`
7. Return with `TRANSID('CT01')` and COMMAREA (suspend — wait for user input)

#### 3. Re-Entry (CDEMO-PGM-CONTEXT = 1)

1. Receive map input via `RECEIVE-TRNVIEW-SCREEN`
2. Evaluate `EIBAID` (which key the user pressed):

   **ENTER key:**
   - Validate that `TRNIDINI` is not empty; if empty, display error "Tran ID can NOT be empty..."
   - Clear all display fields
   - Copy `TRNIDINI` (user-entered transaction ID) to `TRAN-ID`
   - Perform `READ-TRANSACT-FILE` to read the TRANSACT VSAM file
   - If successful: populate all display fields from `TRAN-RECORD` and send screen
   - If NOTFND: display "Transaction ID NOT found..."
   - If other error: display "Unable to lookup Transaction..."

   **PF3 key (Back):**
   - If `CDEMO-FROM-PROGRAM` is set, return to that program via XCTL
   - Otherwise, default to `COMEN01C` (Main Menu)
   - Before XCTL: set `CDEMO-FROM-TRANID = 'CT01'`, `CDEMO-FROM-PROGRAM = 'COTRN01C'`, `CDEMO-PGM-CONTEXT = 0`

   **PF4 key (Clear):**
   - Reset all display fields to spaces
   - Set cursor back to Transaction ID input
   - Re-send the cleared screen

   **PF5 key (Browse Transactions):**
   - Set `CDEMO-TO-PROGRAM = 'COTRN00C'`
   - XCTL to transaction list screen

   **Any other key:**
   - Set error flag
   - Display "Invalid key pressed. Please see below..."
   - Re-send the screen with error message

3. After processing, return with `TRANSID('CT01')` and COMMAREA

#### 4. Data Field Mapping (PROCESS-ENTER-KEY → Display)

| TRAN-RECORD Field    | Screen Field | Map Field (COTRN1AI/O) |
|----------------------|-------------|------------------------|
| TRAN-ID              | Transaction ID | TRNIDI              |
| TRAN-CARD-NUM        | Card Number | CARDNUMI              |
| TRAN-TYPE-CD         | Type CD     | TTYPCDI               |
| TRAN-CAT-CD          | Category CD | TCATCDI               |
| TRAN-SOURCE          | Source      | TRNSRCI               |
| TRAN-AMT (formatted) | Amount      | TRNAMTI               |
| TRAN-DESC            | Description | TDESCI                |
| TRAN-ORIG-TS         | Orig Date   | TORIGDTI              |
| TRAN-PROC-TS         | Proc Date   | TPROCDTI              |
| TRAN-MERCHANT-ID     | Merchant ID | MIDI                  |
| TRAN-MERCHANT-NAME   | Merchant Name | MNAMEI              |
| TRAN-MERCHANT-CITY   | Merchant City | MCITYI              |
| TRAN-MERCHANT-ZIP    | Merchant Zip | MZIPI                |

> **Amount formatting:** `TRAN-AMT` (`PIC S9(09)V99`) is moved to `WS-TRAN-AMT` (`PIC +99999999.99`) before display, converting the packed decimal to an edited numeric format with sign and decimal point.

### Error Handling Paths

| Condition                          | Error Message                          | Action                    |
|------------------------------------|----------------------------------------|---------------------------|
| EIBCALEN = 0 (no COMMAREA)        | (none)                                 | XCTL to COSGN00C         |
| TRNIDIN is empty                   | "Tran ID can NOT be empty..."          | Re-send screen            |
| TRANSACT READ: NOTFND              | "Transaction ID NOT found..."          | Re-send screen            |
| TRANSACT READ: OTHER               | "Unable to lookup Transaction..."      | DISPLAY RESP/REAS + re-send |
| Invalid AID key                    | "Invalid key pressed. Please see below..."| Re-send screen          |

---

## Section 8: Complete File Inventory

### COBOL Source Files

| # | File                    | Path                        | Role in CT01 Flow       |
|---|-------------------------|-----------------------------|-------------------------|
| 1 | COTRN01C.cbl           | `app/cbl/COTRN01C.cbl`     | Target program          |
| 2 | COTRN00C.cbl           | `app/cbl/COTRN00C.cbl`     | Upstream: Tran List     |
| 3 | COMEN01C.cbl           | `app/cbl/COMEN01C.cbl`     | Upstream: Main Menu     |
| 4 | COSGN00C.cbl           | `app/cbl/COSGN00C.cbl`     | Upstream: Sign-on       |

### Copybooks

| # | File                    | Path                        | Classification          |
|---|-------------------------|-----------------------------|-------------------------|
| 1 | COCOM01Y.cpy           | `app/cpy/COCOM01Y.cpy`     | COMMAREA definition     |
| 2 | COTRN01.cpy            | `app/cpy/COTRN01.cpy`      | BMS symbolic map (auto-generated) |
| 3 | COTTL01Y.cpy           | `app/cpy/COTTL01Y.cpy`     | Screen titles           |
| 4 | CSDAT01Y.cpy           | `app/cpy/CSDAT01Y.cpy`     | Date/time structure     |
| 5 | CSMSG01Y.cpy           | `app/cpy/CSMSG01Y.cpy`     | Common messages         |
| 6 | CVTRA05Y.cpy           | `app/cpy/CVTRA05Y.cpy`     | TRAN-RECORD layout      |
| 7 | DFHAID                 | (system)                    | CICS system copybook — AID keys |
| 8 | DFHBMSCA               | (system)                    | CICS system copybook — BMS attributes |

### BMS Maps

| # | File                    | Path                        | Mapset / Map            |
|---|-------------------------|-----------------------------|-------------------------|
| 1 | COTRN01.bms            | `app/bms/COTRN01.bms`      | COTRN01 / COTRN1A      |

### VSAM Data Files

| # | DD Name    | Dataset Name (from CSD)                    | Type     | Key Field | Key Len |
|---|------------|--------------------------------------------|----------|-----------|---------|
| 1 | TRANSACT   | AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS        | VSAM KSDS| TRAN-ID   | 16      |

---

## Section 9: Transaction Record Layout

### TRAN-RECORD — Byte-Level Layout (CVTRA05Y, 350 bytes)

| Offset | Length | Field               | PIC Clause     | Description                  |
|--------|--------|---------------------|----------------|------------------------------|
| 1      | 16     | TRAN-ID             | X(16)          | Transaction identifier (key) |
| 17     | 2      | TRAN-TYPE-CD        | X(02)          | Transaction type code        |
| 19     | 4      | TRAN-CAT-CD         | 9(04)          | Transaction category code    |
| 23     | 10     | TRAN-SOURCE         | X(10)          | Transaction source           |
| 33     | 100    | TRAN-DESC           | X(100)         | Transaction description      |
| 133    | 11     | TRAN-AMT            | S9(09)V99      | Transaction amount (signed)  |
| 144    | 9      | TRAN-MERCHANT-ID    | 9(09)          | Merchant identifier          |
| 153    | 50     | TRAN-MERCHANT-NAME  | X(50)          | Merchant name                |
| 203    | 50     | TRAN-MERCHANT-CITY  | X(50)          | Merchant city                |
| 253    | 10     | TRAN-MERCHANT-ZIP   | X(10)          | Merchant zip code            |
| 263    | 16     | TRAN-CARD-NUM       | X(16)          | Card number                  |
| 279    | 26     | TRAN-ORIG-TS        | X(26)          | Original timestamp           |
| 305    | 26     | TRAN-PROC-TS        | X(26)          | Processing timestamp         |
| 331    | 20     | FILLER              | X(20)          | Reserved                     |
| **Total** | **350** |                  |                |                              |

---

## Section 10: Observations

### What the Flow Demonstrates Well

1. **Clean pseudo-conversational pattern:** COTRN01C follows the standard CICS pseudo-conversational model with clear separation of first-entry vs re-entry using `CDEMO-PGM-CONTEXT`
2. **Consistent COMMAREA navigation:** The navigation chain (sign-on -> menu -> list -> view) passes context through COMMAREA fields (`FROM-PROGRAM`, `TO-PROGRAM`, `PGM-CONTEXT`) consistently across all programs
3. **Pre-selection pattern:** The flow from COTRN00C (list) to COTRN01C (detail) uses `CDEMO-CT01-TRN-SELECTED` to auto-populate and fetch the record — a clean master-detail pattern
4. **Centralized error handling:** Each CICS command checks RESP/RESP2 with EVALUATE and provides meaningful user-facing error messages
5. **Single VSAM file access:** Simple, focused data access — one READ to one file per iteration. No complex multi-file joins
6. **Reusable copybooks:** Consistent use of shared copybooks (COCOM01Y, COTTL01Y, CSDAT01Y, CSMSG01Y) across all programs in the navigation chain

### Gaps and Migration Considerations

1. **READ with UPDATE on a view screen:** The `EXEC CICS READ ... UPDATE` acquires an exclusive lock on the transaction record, which is unnecessary for a read-only view. This should be changed to a non-update READ in the modernized version to avoid contention
2. **No CALL statements:** COTRN01C is entirely self-contained with no sub-program invocations, simplifying migration
3. **No data validation beyond emptiness:** The only input validation is checking if the Transaction ID is empty. There is no format or range validation on the entered ID
4. **BMS-dependent screen rendering:** All screen I/O depends on BMS SEND MAP / RECEIVE MAP. Migration requires replacing BMS maps with a modern UI framework (web forms, REST API, etc.)
5. **COMMAREA size is implicit:** The COMMAREA size depends on EIBCALEN and is not explicitly bounded in the program. The COCOM01Y base structure plus program-specific extensions must be carefully sized during migration
6. **Hardcoded program names:** Return targets like `'COSGN00C'`, `'COMEN01C'`, `'COTRN00C'` are hardcoded string literals. A modern implementation might use a routing table or configuration
7. **DISPLAY for error logging:** The `DISPLAY 'RESP:' WS-RESP-CD 'REAS:' WS-REAS-CD` statement writes to SYSOUT/CEEMSG. In a modernized environment, this should be replaced with structured logging

---

## Section 11: Complexity Comparison

### COTRN01C Transaction Complexity Profile

| Dimension                  | COTRN01C (CT01)          | Notes                                    |
|----------------------------|--------------------------|------------------------------------------|
| Programs in chain          | 4 (incl. upstream)       | Low — linear chain                       |
| Copybooks referenced       | 8 (6 app + 2 system)    | Low                                      |
| VSAM files accessed        | 1 (TRANSACT)             | Very low                                 |
| EXEC CICS commands         | 4 unique types           | Low (SEND MAP, RECEIVE MAP, READ, XCTL, RETURN) |
| CALL statements            | 0                        | None                                     |
| BMS maps                   | 1 (COTRN1A)              | Low                                      |
| Input fields               | 1 (TRNIDIN)              | Very low                                 |
| Display fields             | 13                       | Moderate                                 |
| Function keys              | 4 (ENTER, PF3, PF4, PF5)| Low                                      |
| Error handling paths       | 5                        | Moderate                                 |
| Lines of COBOL             | 331                      | Small program                            |
| Business rules             | Minimal (lookup only)    | Simple read-and-display                  |
| Data modification          | None (read-only view)    | Simplest category                        |

### Migration Effort Assessment

| Category             | Effort   | Rationale                                                |
|----------------------|----------|----------------------------------------------------------|
| Business logic       | Low      | Simple lookup — no calculations, no updates, no complex rules |
| Screen conversion    | Moderate | 13 display fields + 1 input need UI mapping; field positions and attributes must be preserved |
| Data access          | Low      | Single keyed READ on one VSAM file — direct mapping to SQL SELECT by primary key |
| Navigation           | Moderate | XCTL-based navigation chain needs routing framework replacement |
| COMMAREA conversion  | Moderate | Shared COMMAREA structure used by multiple programs — requires session/state management in modern architecture |
| Testing              | Low      | Small program with predictable paths — 5 test scenarios cover all branches |

---

## Appendix: All EXEC CICS Commands in COTRN01C

| # | Command       | Paragraph               | Parameters                                              |
|---|---------------|--------------------------|---------------------------------------------------------|
| 1 | SEND MAP      | SEND-TRNVIEW-SCREEN      | MAP('COTRN1A') MAPSET('COTRN01') FROM(COTRN1AO) ERASE CURSOR |
| 2 | RECEIVE MAP   | RECEIVE-TRNVIEW-SCREEN   | MAP('COTRN1A') MAPSET('COTRN01') INTO(COTRN1AI) RESP(WS-RESP-CD) RESP2(WS-REAS-CD) |
| 3 | READ          | READ-TRANSACT-FILE       | DATASET(WS-TRANSACT-FILE) INTO(TRAN-RECORD) LENGTH(LENGTH OF TRAN-RECORD) RIDFLD(TRAN-ID) KEYLENGTH(LENGTH OF TRAN-ID) UPDATE RESP(WS-RESP-CD) RESP2(WS-REAS-CD) |
| 4 | XCTL          | RETURN-TO-PREV-SCREEN    | PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA)   |
| 5 | RETURN        | MAIN-PARA (end)          | TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA)          |

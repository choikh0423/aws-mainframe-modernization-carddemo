# CCLI Transaction Flow Analysis — COCRDLIC (Credit Card List)

## Section 1: Flow Overview

| Attribute            | Value                                      |
|----------------------|--------------------------------------------|
| **Transaction ID**   | CCLI                                       |
| **Entry Program**    | COCRDLIC                                   |
| **Function**         | List credit cards with browsing and filtering |
| **Pattern**          | Pseudo-conversational (SEND MAP / RETURN TRANSID) |
| **Data Access**      | VSAM (CARDDAT — KSDS browse)               |
| **BMS Mapset / Map** | COCRDLI / CCRDLIA                          |

### Complete Navigation Path

```
  COSGN00C (CC00)          Sign-on Screen
      |
      | XCTL (on successful login)
      v
  COMEN01C (CM00)          Main Menu (regular users)
      |
      | XCTL (menu option 3 — "Credit Card List")
      v
 +==========================================+
 |  COCRDLIC (CCLI)   Credit Card List      |
 |  (TARGET PROGRAM)                        |
 +==========================================+
      |                          |
      | XCTL (Select 'S')       | XCTL (Select 'U')
      v                          v
  COCRDSLC (CCDL)          COCRDUPC (CCUP)
  Card Detail View          Card Update
      |                          |
      | XCTL (PF3)              | XCTL (PF3)
      v                          v
  COCRDLIC (CCLI)          COCRDLIC (CCLI)
  (returns to list)         (returns to list)
```

### Pseudo-Conversational Lifecycle

```
  +-----------+     EXEC CICS RETURN      +------------+
  | First     |     TRANSID('CCLI')       | CICS Task  |
  | Entry     |  ---------------------->  | Suspended  |
  | EIBCALEN=0|     COMMAREA passed       | (terminal  |
  +-----------+                            | waiting)   |
       |                                   +------------+
       |  Initialize COMMAREA                    |
       |  Set PGM-CONTEXT = 0 (ENTER)           |  User presses
       |  Browse CARDDAT forward                 |  Enter/PF7/PF8
       |  SEND MAP CCRDLIA                       |
       |  RETURN TRANSID('CCLI')                 v
       |                                  +-----------+
       +--------------------------------> | Re-Entry  |
                                          | EIBCALEN>0|
                                          +-----------+
                                               |
                                    RECEIVE MAP CCRDLIA
                                    Edit inputs (account/card filter, selection)
                                    Evaluate EIBAID:
                                      PF3  -> XCTL to COMEN01C (menu)
                                      PF7  -> Page backward (browse READPREV)
                                      PF8  -> Page forward  (browse READNEXT)
                                      Enter + 'S' -> XCTL to COCRDSLC (view)
                                      Enter + 'U' -> XCTL to COCRDUPC (update)
                                      Enter (no sel) -> Refresh list
                                    SEND MAP CCRDLIA
                                    RETURN TRANSID('CCLI')
```

---

## Section 2: Programs Involved

| # | Program    | Type            | Transaction | Function                          | Called By  | Call Method         |
|---|------------|-----------------|-------------|-----------------------------------|------------|---------------------|
| 1 | COSGN00C   | CICS Online     | CC00        | Sign-on screen                    | (entry)    | Initial transaction |
| 2 | COMEN01C   | CICS Online     | CM00        | Main Menu (regular users)         | COSGN00C   | XCTL                |
| 3 | **COCRDLIC** | **CICS Online** | **CCLI**  | **Credit Card List (TARGET)**     | COMEN01C   | XCTL (option 3)     |
| 4 | COCRDSLC   | CICS Online     | CCDL        | Credit Card Detail View           | COCRDLIC   | XCTL                |
| 5 | COCRDUPC   | CICS Online     | CCUP        | Credit Card Update                | COCRDLIC   | XCTL                |

### Notes on Program Flow

- **COMEN01C** uses a menu option table defined in copybook `COMEN02Y.cpy`. Option 3 maps to program `COCRDLIC`.
- **COCRDSLC** and **COCRDUPC** both define `LIT-CCLISTPGM = 'COCRDLIC'` and return to COCRDLIC via XCTL on PF3.
- **COACTUPC** and **COACTVWC** also reference COCRDLIC as `LIT-CCLISTPGM`, indicating they can navigate to the card list screen.
- There are **no CALL statements** in COCRDLIC. All inter-program communication uses XCTL.
- There are **no LINK statements** in COCRDLIC.

---

## Section 3: Copybooks

| # | Copybook   | Used By     | Classification         | Purpose                                              |
|---|------------|-------------|------------------------|------------------------------------------------------|
| 1 | CVCRD01Y   | COCRDLIC    | Work area definition   | CC-WORK-AREAS: AID key flags, navigation fields, error/return messages, account/card/customer IDs |
| 2 | COCOM01Y   | COCRDLIC, COMEN01C, COCRDSLC, COCRDUPC | COMMAREA definition | CARDDEMO-COMMAREA: navigation, customer, account, card info |
| 3 | DFHBMSCA   | COCRDLIC    | **CICS system copybook** | BMS attribute constants (DFHBMPRF, DFHBMPRO, DFHBMFSE, DFHRED, etc.) |
| 4 | DFHAID     | COCRDLIC    | **CICS system copybook** | AID key constants (DFHENTER, DFHPF3, DFHPF7, DFHPF8, etc.) |
| 5 | COTTL01Y   | COCRDLIC    | Screen title constants | CCDA-TITLE01, CCDA-TITLE02, CCDA-THANK-YOU |
| 6 | COCRDLI    | COCRDLIC    | **BMS symbolic map** (auto-generated) | Symbolic map for CCRDLIA: input structure CCRDLIAI, output structure CCRDLIAO |
| 7 | CSDAT01Y   | COCRDLIC    | Date/time structure    | WS-CURDATE-DATA, WS-CURDATE-MM-DD-YY, WS-CURTIME-HH-MM-SS |
| 8 | CSMSG01Y   | COCRDLIC    | Common messages        | CCDA-MSG-THANK-YOU, CCDA-MSG-INVALID-KEY |
| 9 | CSUSR01Y   | COCRDLIC    | User data structure    | SEC-USER-DATA: user ID, name, password, type |
| 10| CVACT02Y   | COCRDLIC    | VSAM record layout     | CARD-RECORD: card number, account ID, CVV, embossed name, expiration, status (150 bytes) |
| 11| CSSTRPFY   | COCRDLIC    | PF key mapping routine | YYYY-STORE-PFKEY paragraph: maps EIBAID to CCARD-AID-xxx flags |

### Key Record Layout — CVACT02Y (CARD-RECORD, 150 bytes)

```
01  CARD-RECORD.
    05  CARD-NUM                  PIC X(16).     Pos 1-16    Primary key
    05  CARD-ACCT-ID              PIC 9(11).     Pos 17-27   Account ID
    05  CARD-CVV-CD               PIC 9(03).     Pos 28-30   CVV code
    05  CARD-EMBOSSED-NAME        PIC X(50).     Pos 31-80   Name on card
    05  CARD-EXPIRAION-DATE       PIC X(10).     Pos 81-90   Expiry (YYYY-MM-DD)
    05  CARD-ACTIVE-STATUS        PIC X(01).     Pos 91      Active flag (Y/N)
    05  FILLER                    PIC X(59).     Pos 92-150  Reserved
```

### Key Work Area — CVCRD01Y (CC-WORK-AREAS)

```
01  CC-WORK-AREAS.
    05 CC-WORK-AREA.
       10 CCARD-AID               PIC X(5).      AID key flag
          88 CCARD-AID-ENTER      VALUE 'ENTER'.
          88 CCARD-AID-PFK03      VALUE 'PFK03'.  (F3 - Exit)
          88 CCARD-AID-PFK07      VALUE 'PFK07'.  (F7 - Page Up)
          88 CCARD-AID-PFK08      VALUE 'PFK08'.  (F8 - Page Down)
          ... (PFK01-PFK12, CLEAR, PA1, PA2)
       10 CCARD-NEXT-PROG         PIC X(8).      Next program to XCTL to
       10 CCARD-NEXT-MAPSET       PIC X(7).      Next mapset
       10 CCARD-NEXT-MAP          PIC X(7).      Next map
       10 CCARD-ERROR-MSG         PIC X(75).     Error message
       10 CCARD-RETURN-MSG        PIC X(75).     Return message
       10 CC-ACCT-ID              PIC X(11).     Account filter
       10 CC-CARD-NUM             PIC X(16).     Card filter
       10 CC-CUST-ID              PIC X(09).     Customer ID
```

---

## Section 4: VSAM File Operations

### File Inventory

| DD Name  | Constant           | Key Field              | Key Length | Record Copybook | Record Length | Access Pattern |
|----------|--------------------|------------------------|------------|-----------------|---------------|----------------|
| CARDDAT  | LIT-CARD-FILE      | WS-CARD-RID-CARDNUM   | 16 bytes   | CVACT02Y        | 150 bytes     | Browse (STARTBR/READNEXT/READPREV/ENDBR) |
| CARDAIX  | LIT-CARD-FILE-ACCT-PATH | (defined but unused) | —          | —               | —             | Not used in COCRDLIC |

### VSAM I/O Operations Detail

| # | Command   | Dataset  | Paragraph              | Key/RIDFLD             | Purpose                                   | RESP Handling |
|---|-----------|----------|------------------------|------------------------|-------------------------------------------|---------------|
| 1 | STARTBR   | CARDDAT  | 9000-READ-FORWARD      | WS-CARD-RID-CARDNUM    | Start forward browse at current position  | RESP + RESP2  |
| 2 | READNEXT  | CARDDAT  | 9000-READ-FORWARD      | WS-CARD-RID-CARDNUM    | Read next card record in loop             | NORMAL, DUPREC, ENDFILE, OTHER |
| 3 | READNEXT  | CARDDAT  | 9000-READ-FORWARD      | WS-CARD-RID-CARDNUM    | Peek ahead to check if next page exists   | NORMAL, DUPREC, ENDFILE, OTHER |
| 4 | ENDBR     | CARDDAT  | 9000-READ-FORWARD      | —                      | End forward browse                        | None          |
| 5 | STARTBR   | CARDDAT  | 9100-READ-BACKWARDS    | WS-CARD-RID-CARDNUM    | Start backward browse at current position | RESP + RESP2  |
| 6 | READPREV  | CARDDAT  | 9100-READ-BACKWARDS    | WS-CARD-RID-CARDNUM    | Initial backward read to position         | NORMAL, DUPREC, OTHER |
| 7 | READPREV  | CARDDAT  | 9100-READ-BACKWARDS    | WS-CARD-RID-CARDNUM    | Read previous card records in loop        | NORMAL, DUPREC, OTHER |
| 8 | ENDBR     | CARDDAT  | 9100-READ-BACKWARDS    | —                      | End backward browse                       | None          |

### File Access Flow Diagram

```
                        COCRDLIC
                           |
        +------------------+------------------+
        |                                     |
  9000-READ-FORWARD                   9100-READ-BACKWARDS
        |                                     |
   STARTBR CARDDAT                       STARTBR CARDDAT
   (GTEQ, key=card#)                    (GTEQ, key=card#)
        |                                     |
   +----v----+                           +----v----+
   | READNEXT|<--+                       |READPREV |<--+
   | CARDDAT |   | (loop up to 7        | CARDDAT |   | (loop up to 7
   +---------+   |  records per page)    +---------+   |  records per page)
        |        |                            |        |
   9500-FILTER --+                       9500-FILTER --+
   (account/card                         (account/card
    filter check)                         filter check)
        |                                     |
   Peek READNEXT                              |
   (check next page)                          |
        |                                     |
   ENDBR CARDDAT                         ENDBR CARDDAT
```

### Error Handling Pattern

All browse I/O checks `WS-RESP-CD` via `EVALUATE`:
- **DFHRESP(NORMAL)** / **DFHRESP(DUPREC)** — Process record normally
- **DFHRESP(ENDFILE)** — Set `CA-NEXT-PAGE-NOT-EXISTS`, display "NO MORE RECORDS TO SHOW"
- **OTHER** — Build error message from `WS-FILE-ERROR-MESSAGE` (includes operation name, file name, RESP, RESP2)

---

## Section 5: BMS Screen Map

### Map Definition

| Attribute | Value       |
|-----------|-------------|
| Mapset    | COCRDLI     |
| Map       | CCRDLIA     |
| Size      | 24 rows x 80 columns |
| Mode      | INOUT       |
| Language  | COBOL       |
| Source    | `app/bms/COCRDLI.bms` |

### ASCII Screen Layout

```
Row Col  Field
 1   1   Tran: CCLI          AWS Mainframe Modernization              Date: mm/dd/yy
 2   1   Prog: COCRDLIC              CardDemo                         Time: hh:mm:ss
 3
 4  31                    List Credit Cards                       Page nnn
 5
 6  22   Account Number    : [___________]
 7  22   Credit Card Number: [________________]
 8
 9  10   Select     Account Number  Card Number    Active
10  10   ------     ---------------  ---------------  --------
11  12   [_]        XXXXXXXXXXX      XXXXXXXXXXXXXXXX    X
12  12   [_]        XXXXXXXXXXX      XXXXXXXXXXXXXXXX    X
13  12   [_]        XXXXXXXXXXX      XXXXXXXXXXXXXXXX    X
14  12   [_]        XXXXXXXXXXX      XXXXXXXXXXXXXXXX    X
15  12   [_]        XXXXXXXXXXX      XXXXXXXXXXXXXXXX    X
16  12   [_]        XXXXXXXXXXX      XXXXXXXXXXXXXXXX    X
17  12   [_]        XXXXXXXXXXX      XXXXXXXXXXXXXXXX    X
18
19
20  19   [info message area - 45 chars                          ]
21
22
23   1   [error message area - 78 chars                                               ]
24   1     F3=Exit F7=Backward  F8=Forward
```

### Field Inventory

| Field     | Row | Col | Length | Attributes        | Color     | Purpose                    |
|-----------|-----|-----|--------|-------------------|-----------|----------------------------|
| TRNNAME   | 1   | 7   | 4      | ASKIP,FSET,NORM   | Blue      | Transaction ID display     |
| TITLE01   | 1   | 21  | 40     | ASKIP,NORM        | Yellow    | Title line 1               |
| CURDATE   | 1   | 71  | 8      | ASKIP,NORM        | Blue      | Current date (mm/dd/yy)    |
| PGMNAME   | 2   | 7   | 8      | ASKIP,NORM        | Blue      | Program name display       |
| TITLE02   | 2   | 21  | 40     | ASKIP,NORM        | Yellow    | Title line 2               |
| CURTIME   | 2   | 71  | 8      | ASKIP,NORM        | Blue      | Current time (hh:mm:ss)    |
| PAGENO    | 4   | 76  | 3      | (default)         | (default) | Page number                |
| ACCTSID   | 6   | 44  | 11     | **UNPROT**,FSET,IC| Green     | Account number filter (input) |
| CARDSID   | 7   | 44  | 16     | **UNPROT**,FSET   | Green     | Card number filter (input) |
| CRDSEL1-7 | 11-17| 12 | 1      | FSET,PROT (dynamic)| Default  | Row selection (S/U input)  |
| ACCTNO1-7 | 11-17| 22 | 11     | PROT              | Default   | Account number display     |
| CRDNUM1-7 | 11-17| 43 | 16     | PROT              | Default   | Card number display        |
| CRDSTS1-7 | 11-17| 67 | 1      | PROT              | Default   | Active status display      |
| INFOMSG   | 20  | 19  | 45     | PROT              | Neutral   | Info message               |
| ERRMSG    | 23  | 1   | 78     | ASKIP,BRT,FSET    | Red       | Error message              |

### Function Key Assignments

| Key   | Action                                           | Condition                    |
|-------|--------------------------------------------------|------------------------------|
| Enter | Process selection or refresh list                 | Valid 'S' or 'U' in select field, or no selection |
| PF3   | Exit — XCTL to COMEN01C (menu) or calling program | Always                      |
| PF7   | Page backward (READPREV browse)                  | Not on first page            |
| PF8   | Page forward (READNEXT browse)                   | Next page exists             |
| Other | Treated as Enter (invalid keys default to Enter)  | PFK-INVALID → forced Enter  |

---

## Section 6: COMMAREA Structure

### CARDDEMO-COMMAREA (from COCOM01Y)

```
01 CARDDEMO-COMMAREA.
   05 CDEMO-GENERAL-INFO.
      10 CDEMO-FROM-TRANID        PIC X(04).    Source transaction ID
      10 CDEMO-FROM-PROGRAM       PIC X(08).    Source program name
      10 CDEMO-TO-TRANID          PIC X(04).    Target transaction ID
      10 CDEMO-TO-PROGRAM         PIC X(08).    Target program name
      10 CDEMO-USER-ID            PIC X(08).    Logged-in user ID
      10 CDEMO-USER-TYPE          PIC X(01).    'A'=Admin, 'U'=User
         88 CDEMO-USRTYP-ADMIN    VALUE 'A'.
         88 CDEMO-USRTYP-USER     VALUE 'U'.
      10 CDEMO-PGM-CONTEXT        PIC 9(01).    0=Enter (first), 1=Reenter
         88 CDEMO-PGM-ENTER       VALUE 0.
         88 CDEMO-PGM-REENTER     VALUE 1.
   05 CDEMO-CUSTOMER-INFO.
      10 CDEMO-CUST-ID            PIC 9(09).    Customer ID
      10 CDEMO-CUST-FNAME         PIC X(25).    First name
      10 CDEMO-CUST-MNAME         PIC X(25).    Middle name
      10 CDEMO-CUST-LNAME         PIC X(25).    Last name
   05 CDEMO-ACCOUNT-INFO.
      10 CDEMO-ACCT-ID            PIC 9(11).    Account ID
      10 CDEMO-ACCT-STATUS        PIC X(01).    Account status
   05 CDEMO-CARD-INFO.
      10 CDEMO-CARD-NUM           PIC 9(16).    Card number
   05 CDEMO-MORE-INFO.
      10 CDEMO-LAST-MAP           PIC X(7).     Last map displayed
      10 CDEMO-LAST-MAPSET        PIC X(7).     Last mapset used
```

### Program-Specific Extension (WS-THIS-PROGCOMMAREA)

Appended after CARDDEMO-COMMAREA in the RETURN COMMAREA:

```
01 WS-THIS-PROGCOMMAREA.
   10 WS-CA-LAST-CARDKEY.
      15 WS-CA-LAST-CARD-NUM      PIC X(16).    Last card# on current page
      15 WS-CA-LAST-CARD-ACCT-ID  PIC 9(11).    Last acct# on current page
   10 WS-CA-FIRST-CARDKEY.
      15 WS-CA-FIRST-CARD-NUM     PIC X(16).    First card# on current page
      15 WS-CA-FIRST-CARD-ACCT-ID PIC 9(11).    First acct# on current page
   10 WS-CA-SCREEN-NUM            PIC 9(1).     Current page number
      88 CA-FIRST-PAGE            VALUE 1.
   10 WS-CA-LAST-PAGE-DISPLAYED   PIC 9(1).     Last page indicator
      88 CA-LAST-PAGE-SHOWN       VALUE 0.
      88 CA-LAST-PAGE-NOT-SHOWN   VALUE 9.
   10 WS-CA-NEXT-PAGE-IND         PIC X(1).     Next page exists flag
      88 CA-NEXT-PAGE-NOT-EXISTS  VALUE LOW-VALUES.
      88 CA-NEXT-PAGE-EXISTS      VALUE 'Y'.
   10 WS-RETURN-FLAG              PIC X(1).     Return flag
```

### COMMAREA Flow Between Programs

```
 COMEN01C                    COCRDLIC                    COCRDSLC / COCRDUPC
 --------                    --------                    -------------------
 Sets:                       Reads:                      Reads:
  CDEMO-FROM-TRANID='CM00'   CDEMO-FROM-PROGRAM          CDEMO-FROM-PROGRAM
  CDEMO-FROM-PROGRAM=         (to detect source)          CDEMO-ACCT-ID
    'COMEN01C'               CDEMO-PGM-CONTEXT           CDEMO-CARD-NUM
  CDEMO-PGM-CONTEXT=0         (enter vs reenter)         CDEMO-FROM-TRANID
    (ENTER)                  CDEMO-ACCT-ID
                               (account filter)         Sets (on PF3 return):
 XCTL to COCRDLIC           CDEMO-CARD-NUM               CDEMO-FROM-TRANID
  with CARDDEMO-COMMAREA      (card filter)               CDEMO-FROM-PROGRAM
                                                          CDEMO-PGM-CONTEXT=0
                             Sets:
                              CDEMO-FROM-TRANID='CCLI'
                              CDEMO-FROM-PROGRAM=
                                'COCRDLIC'
                              CDEMO-PGM-CONTEXT=0
                              CDEMO-LAST-MAP='CCRDLIA'
                              CDEMO-LAST-MAPSET='COCRDLI'
                              CDEMO-ACCT-ID (selected row)
                              CDEMO-CARD-NUM (selected row)

                             XCTL to COCRDSLC or COCRDUPC
                              with CARDDEMO-COMMAREA
```

### COMMAREA Passing Mechanism

COCRDLIC appends `WS-THIS-PROGCOMMAREA` after `CARDDEMO-COMMAREA` when returning:

```cobol
MOVE CARDDEMO-COMMAREA    TO WS-COMMAREA
MOVE WS-THIS-PROGCOMMAREA TO
     WS-COMMAREA(LENGTH OF CARDDEMO-COMMAREA + 1:
                  LENGTH OF WS-THIS-PROGCOMMAREA)

EXEC CICS RETURN
     TRANSID (LIT-THISTRANID)
     COMMAREA (WS-COMMAREA)
     LENGTH(LENGTH OF WS-COMMAREA)
END-EXEC
```

On re-entry, it splits the COMMAREA back:

```cobol
MOVE DFHCOMMAREA(1:LENGTH OF CARDDEMO-COMMAREA) TO CARDDEMO-COMMAREA
MOVE DFHCOMMAREA(LENGTH OF CARDDEMO-COMMAREA + 1:
                  LENGTH OF WS-THIS-PROGCOMMAREA) TO WS-THIS-PROGCOMMAREA
```

---

## Section 7: Business Logic Flow

### Step-by-Step Narrative

#### 1. Entry and Initialization (0000-MAIN)

1. Initialize `CC-WORK-AREA`, `WS-MISC-STORAGE`, `WS-COMMAREA`
2. Set `WS-TRANID = 'CCLI'`
3. Clear error message (`WS-ERROR-MSG-OFF`)
4. **If EIBCALEN = 0** (direct invocation, no COMMAREA):
   - Initialize `CARDDEMO-COMMAREA` and `WS-THIS-PROGCOMMAREA`
   - Set FROM-TRANID = 'CCLI', FROM-PROGRAM = 'COCRDLIC'
   - Set user type = User, PGM-CONTEXT = 0 (ENTER)
   - Set page 1, last page not shown
5. **If EIBCALEN > 0** (transfer from another program):
   - Split DFHCOMMAREA into `CARDDEMO-COMMAREA` + `WS-THIS-PROGCOMMAREA`
6. **If coming from menu** (PGM-ENTER and FROM-PROGRAM != 'COCRDLIC'):
   - Re-initialize `WS-THIS-PROGCOMMAREA` (fresh start)
   - Reset to page 1

#### 2. PF Key Mapping (YYYY-STORE-PFKEY via CSSTRPFY)

Maps EIBAID byte to `CCARD-AID-xxx` flag. Supports Enter, Clear, PA1, PA2, PF1-PF24 (PF13-24 mapped to PF1-12).

#### 3. Input Reception (2000-RECEIVE-MAP)

Only performed when `EIBCALEN > 0 AND CDEMO-FROM-PROGRAM = 'COCRDLIC'` (pseudo-conversational re-entry from self):

- **2100-RECEIVE-SCREEN**: `EXEC CICS RECEIVE MAP` into `CCRDLIAI`. Extracts account filter (`ACCTSIDI`), card filter (`CARDSIDI`), and 7 row selections (`CRDSEL1I` - `CRDSEL7I`).
- **2200-EDIT-INPUTS**: Validates inputs:
  - **2210-EDIT-ACCOUNT**: Account filter must be blank or a valid 11-digit numeric value
  - **2220-EDIT-CARD**: Card filter must be blank or a valid 16-digit numeric value
  - **2250-EDIT-ARRAY**: Selection fields must be 'S' (view), 'U' (update), or blank. At most one selection allowed. Invalid codes flagged.

#### 4. Main Decision Logic (EVALUATE TRUE)

| Condition | Action |
|-----------|--------|
| `INPUT-ERROR` | Display error message, re-send map |
| `PF7 + first page` | Re-read forward from first card, display |
| `PF3 (from self)` | Set COMMAREA, XCTL to COMEN01C (menu) |
| `PF3 (from other)` or `PGM-REENTER + not from self` | Reset COMMAREA, read forward, display |
| `PF8 + next page exists` | Read forward from last card key, display next page |
| `PF7 + not first page` | Read backward from first card key, display previous page |
| `Enter + 'S' selected` | Set CDEMO-ACCT-ID/CARD-NUM from selected row, XCTL to COCRDSLC |
| `Enter + 'U' selected` | Set CDEMO-ACCT-ID/CARD-NUM from selected row, XCTL to COCRDUPC |
| `OTHER` | Re-read forward from first card, display |

#### 5. Forward Browse (9000-READ-FORWARD)

1. `STARTBR CARDDAT` with GTEQ on card number key
2. Loop `READNEXT` up to 7 records (WS-MAX-SCREEN-LINES):
   - Call `9500-FILTER-RECORDS` to apply account/card filters
   - Store matching records in screen data array (`WS-SCREEN-ROWS`)
   - Track first/last card keys for pagination
3. After 7 records, peek-ahead `READNEXT` to determine if next page exists
4. `ENDBR CARDDAT`
5. Handle ENDFILE (no more records) and errors

#### 6. Backward Browse (9100-READ-BACKWARDS)

1. Copy first card key to last card key position
2. `STARTBR CARDDAT` with GTEQ
3. Initial `READPREV` to position cursor
4. Loop `READPREV` filling screen array from position 7 down to 1:
   - Call `9500-FILTER-RECORDS` for filtering
   - Track first card key when counter reaches 0
5. `ENDBR CARDDAT`

#### 7. Record Filtering (9500-FILTER-RECORDS)

- If account filter is valid: exclude records where `CARD-ACCT-ID != CC-ACCT-ID`
- If card filter is valid: exclude records where `CARD-NUM != CC-CARD-NUM-N`
- Both filters can be combined (AND logic)

#### 8. Screen Output (1000-SEND-MAP)

1. **1100-SCREEN-INIT**: Set titles, date/time, page number, initialize info message
2. **1200-SCREEN-ARRAY-INIT**: Move 7 rows of data (account, card#, status) to BMS output fields
3. **1250-SETUP-ARRAY-ATTRIBS**: Set field attributes — protect empty rows, enable selection for data rows, highlight errors in red
4. **1300-SETUP-SCREEN-ATTRS**: Populate filter fields, position cursor, highlight invalid filters in red
5. **1400-SETUP-MESSAGE**: Set appropriate info/error messages based on pagination state
6. **1500-SEND-SCREEN**: `EXEC CICS SEND MAP` with CURSOR and ERASE

#### 9. Debug Paragraphs (SEND-PLAIN-TEXT, SEND-LONG-TEXT)

Utility paragraphs for debugging — send plain text to terminal and return without TRANSID (non-pseudo-conversational). Not used in normal flow.

---

## Section 8: Complete File Inventory

### COBOL Source Programs

| # | File Path                    | Program    | Role                    |
|---|------------------------------|------------|-------------------------|
| 1 | `app/cbl/COSGN00C.cbl`      | COSGN00C   | Sign-on (upstream)      |
| 2 | `app/cbl/COMEN01C.cbl`      | COMEN01C   | Main Menu (upstream)    |
| 3 | `app/cbl/COCRDLIC.cbl`      | COCRDLIC   | **Target program**      |
| 4 | `app/cbl/COCRDSLC.cbl`      | COCRDSLC   | Card Detail (downstream)|
| 5 | `app/cbl/COCRDUPC.cbl`      | COCRDUPC   | Card Update (downstream)|

### Copybooks

| # | File Path                    | Copybook   | Classification          |
|---|------------------------------|------------|-------------------------|
| 1 | `app/cpy/CVCRD01Y.cpy`      | CVCRD01Y   | Work area               |
| 2 | `app/cpy/COCOM01Y.cpy`      | COCOM01Y   | COMMAREA                |
| 3 | *(CICS system — not in repo)*| DFHBMSCA   | System copybook         |
| 4 | *(CICS system — not in repo)*| DFHAID     | System copybook         |
| 5 | `app/cpy/COTTL01Y.cpy`      | COTTL01Y   | Screen titles           |
| 6 | *(BMS-generated — not in app/cpy/)*| COCRDLI | BMS symbolic map       |
| 7 | `app/cpy/CSDAT01Y.cpy`      | CSDAT01Y   | Date/time               |
| 8 | `app/cpy/CSMSG01Y.cpy`      | CSMSG01Y   | Common messages         |
| 9 | `app/cpy/CSUSR01Y.cpy`      | CSUSR01Y   | User data               |
| 10| `app/cpy/CVACT02Y.cpy`      | CVACT02Y   | Card record layout      |
| 11| `app/cpy/CSSTRPFY.cpy`      | CSSTRPFY   | PF key store routine    |

### BMS Maps

| # | File Path                    | Mapset  | Map     |
|---|------------------------------|---------|---------|
| 1 | `app/bms/COCRDLI.bms`        | COCRDLI | CCRDLIA |

### VSAM Data Files

| # | DD Name  | Description             | Key         | Record Layout |
|---|----------|-------------------------|-------------|---------------|
| 1 | CARDDAT  | Card master data (KSDS) | Card Number (16 bytes) | CVACT02Y (150 bytes) |
| 2 | CARDAIX  | Card alternate index path (defined in WS, unused by COCRDLIC) | — | — |

### CSD Definition

```
DEFINE TRANSACTION(CCLI) GROUP(CARDDEMO)
       PROGRAM(COCRDLIC) TWASIZE(0) PROFILE(DFHCICST) STATUS(ENABLED)
       TASKDATALOC(ANY) TASKDATAKEY(USER)
```

---

## Section 9: Card Record Layout (CVACT02Y)

### Byte-Level Layout

| Offset | Length | Field                  | PIC        | Description            |
|--------|--------|------------------------|------------|------------------------|
| 1      | 16     | CARD-NUM               | X(16)      | Card number (primary key) |
| 17     | 11     | CARD-ACCT-ID           | 9(11)      | Account ID             |
| 28     | 3      | CARD-CVV-CD            | 9(03)      | CVV code               |
| 31     | 50     | CARD-EMBOSSED-NAME     | X(50)      | Name embossed on card  |
| 81     | 10     | CARD-EXPIRAION-DATE    | X(10)      | Expiration date (YYYY-MM-DD) |
| 91     | 1      | CARD-ACTIVE-STATUS     | X(01)      | Active status (Y/N)    |
| 92     | 59     | FILLER                 | X(59)      | Reserved/padding       |
| **Total** | **150** |                     |            |                        |

### Fields Displayed on Screen

COCRDLIC displays only 3 fields per row from the card record:
- `CARD-ACCT-ID` → ACCTNO columns
- `CARD-NUM` → CRDNUM columns
- `CARD-ACTIVE-STATUS` → CRDSTS columns

---

## Section 10: Observations

### What This Flow Demonstrates Well

1. **Clean pseudo-conversational pattern**: COCRDLIC follows the standard CICS SEND-RETURN-RECEIVE cycle with proper COMMAREA state management across iterations.

2. **Pagination via VSAM browse**: Forward (STARTBR/READNEXT) and backward (STARTBR/READPREV) browsing with bookmark keys (first/last card numbers) stored in the program-specific COMMAREA extension.

3. **Record filtering**: Runtime filtering by account number and/or card number applied during browse, demonstrating a common mainframe pattern for user-driven search.

4. **Separation of navigation and data**: The COMMAREA is split into a shared navigation area (CARDDEMO-COMMAREA) and a program-specific extension (WS-THIS-PROGCOMMAREA), allowing each program to maintain its own pagination state.

5. **Consistent error handling**: Every VSAM I/O operation checks RESP/RESP2 with EVALUATE blocks covering NORMAL, DUPREC, ENDFILE, and OTHER conditions.

6. **Menu-driven architecture**: The COMEN02Y copybook defines a table-driven menu system where program names are resolved from an array indexed by option number.

### Gaps Relative to Migration

1. **No explicit data validation on browse keys**: The STARTBR uses GTEQ which silently positions past invalid keys. A modernized version should validate key ranges.

2. **Hardcoded screen dimensions**: 7 rows per page is fixed in `WS-MAX-SCREEN-LINES`. A modern UI would use dynamic pagination.

3. **No audit trail**: Card list browsing is not logged. Modern compliance requirements may need access logging.

4. **CARDAIX alternate index defined but unused**: The `LIT-CARD-FILE-ACCT-PATH = 'CARDAIX'` constant is declared but never referenced in EXEC CICS commands, suggesting incomplete or planned functionality.

5. **BMS symbolic map is auto-generated**: The `COPY COCRDLI` copybook is generated from the BMS source file and does not exist as a standalone `.cpy` file. Migration tools need to handle BMS-to-UI conversion.

6. **Debug paragraphs in production code**: `SEND-PLAIN-TEXT` and `SEND-LONG-TEXT` are debugging utilities that should be removed or disabled in production.

---

## Section 11: Migration Complexity Comparison

| Dimension                  | COCRDLIC (CCLI)                                | Complexity |
|----------------------------|------------------------------------------------|------------|
| **Program count**          | 1 target + 2 upstream + 2 downstream = 5       | Low        |
| **Copybook count**         | 11 (8 app + 2 system + 1 BMS-generated)        | Low        |
| **VSAM files**             | 1 (CARDDAT browse only, read-only)             | Low        |
| **BMS maps**               | 1 mapset, 1 map                                | Low        |
| **EXEC CICS commands**     | 18 total (2 SEND MAP, 1 RECEIVE MAP, 2 STARTBR, 3 READNEXT, 2 READPREV, 2 ENDBR, 3 XCTL, 1 RETURN TRANSID, 2 SEND TEXT, 1 RETURN) | Medium |
| **CALL statements**        | 0                                              | None       |
| **LINK statements**        | 0                                              | None       |
| **Input validation**       | Account (11-digit numeric), Card (16-digit numeric), Selection (S/U) | Low |
| **Business rules**         | Filtering, pagination                          | Low        |
| **Error handling paths**   | VSAM browse errors, input validation errors    | Low        |
| **Pseudo-conversational**  | Yes — standard SEND/RETURN/RECEIVE cycle       | Standard   |
| **COMMAREA complexity**    | Shared area + program-specific extension        | Medium     |

**Overall Migration Complexity: LOW-MEDIUM**

This is a straightforward list/browse screen with no database writes, no sub-program calls, and no complex business logic. The primary migration challenges are:
1. Converting BMS screen I/O to a modern UI framework
2. Replacing VSAM browse operations with database queries (e.g., SQL with OFFSET/LIMIT)
3. Preserving the COMMAREA-based state management in a stateless architecture

# CICS Transaction Flow Analysis: CAUP (COACTUPC) -- Account Update

## 1. Flow Overview

### Transaction Summary

| Attribute          | Value                |
|--------------------|----------------------|
| Transaction ID     | `CAUP`               |
| Program            | `COACTUPC`           |
| Source File        | `app/cbl/COACTUPC.cbl` (4237 lines) |
| Description        | Account Update       |
| Mapset / Map       | `COACTUP` / `CACTUPA`|
| Pattern            | Pseudo-conversational|
| Data Access        | VSAM (no DB2/IMS)    |

### High-Level Flow Diagram

```
   TERMINAL
      |
      | CAUP
      v
+-------------+     PF3      +-----------+
| COACTUPC    |------------->| COMEN01C  |  (Main Menu, CM00)
| Account     |              +-----------+
| Update      |
|             |--- ENTER (account ID) ---> READ CXACAIX
|             |                             READ ACCTDAT
|             |                             READ CUSTDAT
|             |--- ENTER (confirm)  ------> READ ACCTDAT (UPDATE)
|             |                             READ CUSTDAT (UPDATE)
|             |                             REWRITE ACCTDAT
|             |                             REWRITE CUSTDAT
|             |
|             |--- RETURN TRANSID('CAUP')
|             |         COMMAREA(WS-COMMAREA)
+-------------+
```

### Navigation Context

```
+-------------+     XCTL      +-------------+     XCTL      +-------------+
| COSGN00C    |  -----------> | COMEN01C    |  -----------> | COACTUPC    |
| Sign-on     |               | Main Menu   |               | Account     |
| Tran: CC00  |               | Tran: CM00  |               | Update      |
+-------------+               +-------------+               | Tran: CAUP  |
                                     ^                       +-------------+
                                     |          PF3 / XCTL        |
                                     +----------------------------+
```

---

## 2. Programs Involved

### Target Program

| Program    | Source File                | Lines | Description       |
|------------|----------------------------|-------|-------------------|
| COACTUPC   | `app/cbl/COACTUPC.cbl`     | 4237  | Account Update    |

### Programs Referenced via XCTL

| Literal Constant        | Value      | Transfer Type | Context                              |
|--------------------------|-----------|---------------|--------------------------------------|
| `LIT-MENUPGM`           | `COMEN01C`| XCTL          | PF3 exit to Main Menu                |
| `CDEMO-TO-PROGRAM`      | (dynamic) | XCTL          | Returns to calling program via COMMAREA |
| `LIT-CARDUPDATE-PGM`    | `COCRDUPC`| (literal only)| Card Update -- defined but not XCTLed |
| `LIT-CCLISTPGM`         | `COCRDLIC`| (literal only)| Credit Card List -- defined but not XCTLed |

> **Note:** `COCRDUPC` and `COCRDLIC` are defined as literal constants (lines 541-548) but
> COACTUPC does not XCTL/LINK to them directly. They are stored in the COMMAREA for use by
> the menu system. The only runtime XCTL target is `CDEMO-TO-PROGRAM` (line 957), which
> resolves to `COMEN01C` or the value in `CDEMO-FROM-PROGRAM`.

### Upstream Program (Caller)

| Program    | Source File                | Transfer Type | Evidence                               |
|------------|----------------------------|---------------|----------------------------------------|
| COMEN01C   | `app/cbl/COMEN01C.cbl`     | XCTL          | Menu option 2 "Account Update" maps to `COACTUPC` via `COMEN02Y.cpy` (line 34) |

### Sub-Programs (Inline COPY -- Procedure Division)

| Copybook   | Source File                    | Inclusion     | Description                          |
|------------|--------------------------------|---------------|--------------------------------------|
| CSUTLDPY   | `app/cpy/CSUTLDPY.cpy` (376 lines) | `COPY CSUTLDPY` (line 4232) | Date validation routines (EDIT-DATE-CCYYMMDD, etc.) |
| CSSTRPFY   | `app/cpy/CSSTRPFY.cpy` (86 lines)  | `COPY 'CSSTRPFY'` (line 4199) | AID-to-PFKey mapping (YYYY-STORE-PFKEY) |

> **No CALL statements** are present in COACTUPC. All sub-program logic is inlined via COPY.

---

## 3. Copybooks

### Complete Copybook Inventory

| # | COPY Statement  | Source File                      | Category            | Description                                       |
|---|-----------------|----------------------------------|---------------------|---------------------------------------------------|
| 1 | `COPY CVCRD01Y` | `app/cpy/CVCRD01Y.cpy` (47 lines) | Work area           | Card work areas, AID definitions, navigation fields |
| 2 | `COPY DFHBMSCA` | *(IBM system copybook)*          | CICS system         | BMS attribute constants (colors, highlighting)     |
| 3 | `COPY DFHAID`   | *(IBM system copybook)*          | CICS system         | AID key constants (DFHENTER, DFHPF3, etc.)        |
| 4 | `COPY COTTL01Y` | `app/cpy/COTTL01Y.cpy` (28 lines)  | Screen title        | Screen titles (CCDA-TITLE01, CCDA-TITLE02)         |
| 5 | `COPY COACTUP`  | `app/cpy-bms/COACTUP.CPY` (669 lines) | BMS symbolic map | Generated symbolic map for CACTUPA (I/O structures)|
| 6 | `COPY CSDAT01Y` | `app/cpy/CSDAT01Y.cpy` (59 lines)  | Date/time           | Date and time work areas                           |
| 7 | `COPY CSMSG01Y` | `app/cpy/CSMSG01Y.cpy` (25 lines)  | Messages            | Common messages (thank-you, invalid key)           |
| 8 | `COPY CSMSG02Y` | `app/cpy/CSMSG02Y.cpy` (36 lines)  | Abend handling      | Abend data structure                               |
| 9 | `COPY CSUSR01Y` | `app/cpy/CSUSR01Y.cpy` (27 lines)  | User security       | User security data (ID, name, password, type)      |
|10 | `COPY CVACT01Y` | `app/cpy/CVACT01Y.cpy` (21 lines)  | Record layout       | Account entity record (RECLN 300)                  |
|11 | `COPY CVACT03Y` | `app/cpy/CVACT03Y.cpy` (12 lines)  | Record layout       | Card cross-reference record (RECLN 50)             |
|12 | `COPY CVCUS01Y` | `app/cpy/CVCUS01Y.cpy` (27 lines)  | Record layout       | Customer entity record (RECLN 500)                 |
|13 | `COPY COCOM01Y` | `app/cpy/COCOM01Y.cpy` (48 lines)  | COMMAREA            | CardDemo shared COMMAREA structure                 |
|14 | `COPY CSUTLDPY` | `app/cpy/CSUTLDPY.cpy` (376 lines) | Procedure (inline)  | Date validation routines                           |
|15 | `COPY CSSTRPFY` | `app/cpy/CSSTRPFY.cpy` (86 lines)  | Procedure (inline)  | PFKey store routine (YYYY-STORE-PFKEY)             |

> **System Copybooks:** `DFHBMSCA` and `DFHAID` are IBM-supplied CICS copybooks and are
> NOT present in `app/cpy/`. They are provided by the CICS runtime environment.

> **BMS Symbolic Map:** `COACTUP` is the BMS-generated symbolic map residing in
> `app/cpy-bms/COACTUP.CPY` (not in `app/cpy/`). It defines the input (`CACTUPAI`)
> and output (`CACTUPAO`) structures.

### Copybooks Referenced by Inline COPY Procedures

The date validation routines in `CSUTLDPY.cpy` reference an additional working-storage
copybook:

| Copybook   | Source File                        | Category    | Description                    |
|------------|------------------------------------|-------------|--------------------------------|
| CSUTLDWY   | `app/cpy/CSUTLDWY.cpy` (90 lines) | Work area   | Date validation working storage|

And the lookup code repository:

| Copybook   | Source File                          | Category    | Description                               |
|------------|--------------------------------------|-------------|-------------------------------------------|
| CSLKPCDY   | `app/cpy/CSLKPCDY.cpy` (1318 lines) | Lookup data | Phone area codes, state codes, zip ranges |
| CSSETATY   | `app/cpy/CSSETATY.cpy` (31 lines)   | UI template | Field attribute setting (color/highlight) |

---

## 4. VSAM File Operations

### File Summary

| DD Name   | Literal Variable              | Value      | Record Copybook | Record Layout         | Key Field            | RECLN |
|-----------|-------------------------------|------------|-----------------|----------------------|----------------------|-------|
| CXACAIX   | `LIT-CARDXREFNAME-ACCT-PATH`  | `CXACAIX`  | CVACT03Y        | `CARD-XREF-RECORD`   | Account ID (AIX)     | 50    |
| ACCTDAT   | `LIT-ACCTFILENAME`            | `ACCTDAT`  | CVACT01Y        | `ACCOUNT-RECORD`     | Account ID           | 300   |
| CUSTDAT   | `LIT-CUSTFILENAME`            | `CUSTDAT`  | CVCUS01Y        | `CUSTOMER-RECORD`    | Customer ID          | 500   |

> **CXACAIX** is an alternate index (AIX) path over the card cross-reference base cluster.
> It allows lookup of card/customer cross-reference data by Account ID instead of the
> primary Card Number key.

### Detailed I/O Operations

#### 4.1 CXACAIX -- Card Cross-Reference by Account (Alternate Index)

| # | Operation | Paragraph                  | Line  | RIDFLD                     | Mode     | RESP Handling |
|---|-----------|----------------------------|-------|----------------------------|----------|---------------|
| 1 | READ      | `9200-GETCARDXREF-BYACCT`  | 3654  | `WS-CARD-RID-ACCT-ID-X`   | Read-only| RESP/RESP2    |

**Purpose:** Look up the `CARD-XREF-RECORD` to obtain `XREF-CUST-ID` for the given account.
This maps Account ID -> Customer ID for subsequent customer data retrieval.

**Error Handling (lines 3663-3697):**
- `DFHRESP(NORMAL)` -- success, continue
- Other -- set `INPUT-ERROR`, display error message

#### 4.2 ACCTDAT -- Account Master

| # | Operation | Paragraph                  | Line  | RIDFLD                     | Mode       | RESP Handling |
|---|-----------|----------------------------|-------|----------------------------|------------|---------------|
| 1 | READ      | `9300-GETACCTDATA-BYACCT`  | 3703  | `WS-CARD-RID-ACCT-ID-X`   | Read-only  | RESP/RESP2    |
| 2 | READ UPDATE| `9600-WRITE-PROCESSING`   | 3894  | `WS-CARD-RID-ACCT-ID-X`   | Update lock| RESP/RESP2    |
| 3 | REWRITE   | `9600-WRITE-PROCESSING`    | 4065  | *(uses locked record)*     | Write-back | RESP/RESP2    |

**Read-only (line 3703):** Fetches account data for display. Populates `ACCOUNT-RECORD` fields.

**Read for Update (line 3894):** Acquires exclusive lock on account record before modification.
- Error path: `COULD-NOT-LOCK-ACCT-FOR-UPDATE` flag set, exits write processing.

**Rewrite (line 4065):** Writes updated account data from `ACCT-UPDATE-RECORD`.
- Error path: `LOCKED-BUT-UPDATE-FAILED` flag set, exits.

#### 4.3 CUSTDAT -- Customer Master

| # | Operation | Paragraph                  | Line  | RIDFLD                     | Mode       | RESP Handling |
|---|-----------|----------------------------|-------|----------------------------|------------|---------------|
| 1 | READ      | `9400-GETCUSTDATA-BYCUST`  | 3753  | `WS-CARD-RID-CUST-ID-X`   | Read-only  | RESP/RESP2    |
| 2 | READ UPDATE| `9600-WRITE-PROCESSING`   | 3921  | `WS-CARD-RID-CUST-ID-X`   | Update lock| RESP/RESP2    |
| 3 | REWRITE   | `9600-WRITE-PROCESSING`    | 4085  | *(uses locked record)*     | Write-back | RESP/RESP2    |

**Read-only (line 3753):** Fetches customer data for display.

**Read for Update (line 3921):** Acquires exclusive lock on customer record.
- Error path: `COULD-NOT-LOCK-CUST-FOR-UPDATE` flag set, exits write processing.

**Rewrite (line 4085):** Writes updated customer data from `CUST-UPDATE-RECORD`.
- Error path: `LOCKED-BUT-UPDATE-FAILED` flag set, `SYNCPOINT ROLLBACK` issued (line 4099-4101)
  to undo the account REWRITE.

### Data Access Flow Diagram

```
1. User enters Account ID
        |
        v
  READ CXACAIX (by Acct ID)  -->  CARD-XREF-RECORD
        |                              |
        |                     XREF-CUST-ID
        v                              |
  READ ACCTDAT (by Acct ID)            |
        |                              v
        v                     READ CUSTDAT (by Cust ID)
  ACCOUNT-RECORD                       |
        |                              v
        |                     CUSTOMER-RECORD
        v
  Display combined Account + Customer data on screen
        |
        | (User modifies fields, presses Enter to confirm)
        v
  READ ACCTDAT UPDATE (lock)
  READ CUSTDAT UPDATE (lock)
  9700-CHECK-CHANGE-IN-REC (optimistic concurrency)
        |
        v
  REWRITE ACCTDAT
  REWRITE CUSTDAT
        |
  (If CUSTDAT REWRITE fails -> SYNCPOINT ROLLBACK)
```

---

## 5. BMS Screen Map

### Mapset / Map Identification

| Attribute      | Value         |
|----------------|---------------|
| Mapset Name    | `COACTUP`     |
| Map Name       | `CACTUPA`     |
| BMS Source      | `app/bms/COACTUP.bms` (513 lines) |
| Symbolic Map   | `app/cpy-bms/COACTUP.CPY` (669 lines) |
| Screen Size    | 24 rows x 80 columns |
| Input Structure | `CACTUPAI`   |
| Output Structure| `CACTUPAO`   |

### Screen Field Inventory

#### Header Section (Rows 1-5)

| Field     | Row | Col | Len | Type     | Description           |
|-----------|-----|-----|-----|----------|-----------------------|
| TRNNAME   | 1   | 5   | 4   | Output   | Transaction name      |
| TITLE01   | 1   | 21  | 40  | Output   | Screen title line 1   |
| CURDATE   | 1   | 65  | 8   | Output   | Current date          |
| PGMNAME   | 2   | 5   | 8   | Output   | Program name          |
| TITLE02   | 2   | 21  | 40  | Output   | Screen title line 2   |
| CURTIME   | 2   | 65  | 8   | Output   | Current time          |

#### Account Section (Rows 5-13)

| Field     | Row | Col | Len | Type     | Description                |
|-----------|-----|-----|-----|----------|----------------------------|
| ACCTSID   | 5   | 27  | 11  | Input    | Account ID (search key)    |
| ACSTTUS   | 5   | 62  | 1   | Input    | Account active status      |
| OPNYEAR   | 7   | 27  | 4   | Input    | Open date -- year          |
| OPNMON    | 7   | 32  | 2   | Input    | Open date -- month         |
| OPNDAY    | 7   | 35  | 2   | Input    | Open date -- day           |
| ACRDLIM   | 7   | 62  | 15  | Input    | Credit limit               |
| EXPYEAR   | 8   | 27  | 4   | Input    | Expiry date -- year        |
| EXPMON    | 8   | 32  | 2   | Input    | Expiry date -- month       |
| EXPDAY    | 8   | 35  | 2   | Input    | Expiry date -- day         |
| ACSHLIM   | 8   | 62  | 15  | Input    | Cash credit limit          |
| RISYEAR   | 9   | 27  | 4   | Input    | Reissue date -- year       |
| RISMON    | 9   | 32  | 2   | Input    | Reissue date -- month      |
| RISDAY    | 9   | 35  | 2   | Input    | Reissue date -- day        |
| ACURBAL   | 9   | 62  | 15  | Input    | Current balance            |
| ACRCYCR   | 10  | 62  | 15  | Input    | Current cycle credit       |
| AADDGRP   | 10  | 27  | 10  | Input    | Account group ID           |
| ACRCYDB   | 11  | 62  | 15  | Input    | Current cycle debit        |

#### Customer Section (Rows 12-22)

| Field     | Row | Col | Len | Type     | Description                |
|-----------|-----|-----|-----|----------|----------------------------|
| ACSTNUM   | 12  | 27  | 9   | Output   | Customer ID                |
| ACTSSN1   | 12  | 62  | 3   | Input    | SSN part 1                 |
| ACTSSN2   | 12  | 66  | 2   | Input    | SSN part 2                 |
| ACTSSN3   | 12  | 69  | 4   | Input    | SSN part 3                 |
| DOBYEAR   | 13  | 27  | 4   | Input    | Date of birth -- year      |
| DOBMON    | 13  | 32  | 2   | Input    | Date of birth -- month     |
| DOBDAY    | 13  | 35  | 2   | Input    | Date of birth -- day       |
| ACSTFCO   | 13  | 62  | 3   | Input    | FICO credit score          |
| ACSFNAM   | 14  | 27  | 25  | Input    | Customer first name        |
| ACSMNAM   | 14  | 55  | 25  | Input    | Customer middle name       |
| ACSLNAM   | 15  | 27  | 25  | Input    | Customer last name         |
| ACSADL1   | 16  | 27  | 50  | Input    | Address line 1             |
| ACSSTE    | 16  | 62  | 2   | Input    | State code                 |
| ACSADL2   | 17  | 27  | 50  | Input    | Address line 2             |
| ACSZIPC   | 17  | 62  | 5   | Input    | Zip code                   |
| ACSCITY   | 18  | 27  | 50  | Input    | City                       |
| ACSCTRY   | 18  | 62  | 3   | Input    | Country code               |
| ACSPH1A   | 19  | 27  | 3   | Input    | Phone 1 area code          |
| ACSPH1B   | 19  | 32  | 3   | Input    | Phone 1 prefix             |
| ACSPH1C   | 19  | 36  | 4   | Input    | Phone 1 number             |
| ACSGOVT   | 19  | 62  | 20  | Input    | Government issued ID       |
| ACSPH2A   | 20  | 27  | 3   | Input    | Phone 2 area code          |
| ACSPH2B   | 20  | 32  | 3   | Input    | Phone 2 prefix             |
| ACSPH2C   | 20  | 36  | 4   | Input    | Phone 2 number             |
| ACSEFTID  | 20  | 62  | 10  | Input    | EFT account ID             |
| ACSPRICH  | 21  | 27  | 1   | Input    | Primary card holder ind    |

#### Footer Section (Rows 23-24)

| Field     | Row | Col | Len | Type     | Description                |
|-----------|-----|-----|-----|----------|----------------------------|
| INFOMSGO  | 23  | 1   | 79  | Output   | Informational/error message|
| ERRMSGO   | 24  | 1   | 79  | Output   | Error message line         |

### BMS I/O Commands

| # | Command      | Line  | Paragraph            | Map/Mapset              | Buffer     | Options            |
|---|-------------|-------|----------------------|-------------------------|------------|--------------------|
| 1 | RECEIVE MAP | 1040  | `1100-RECEIVE-MAP`   | `CACTUPA` / `COACTUP`   | `CACTUPAI` | RESP, RESP2        |
| 2 | SEND MAP    | 3594  | `3000-SEND-MAP`      | Dynamic via `CCARD-NEXT-MAP`/`CCARD-NEXT-MAPSET` | `CACTUPAO` | CURSOR, ERASE, FREEKB, RESP |
| 3 | SEND (raw)  | 4211  | `ABEND-ROUTINE`      | *(no map -- raw data)*   | `ABEND-DATA` | NOHANDLE, ERASE  |

---

## 6. COMMAREA Structure

### COMMAREA Layout (COCOM01Y.cpy)

The COMMAREA is the primary inter-program communication mechanism. It is defined in
`COCOM01Y.cpy` and included via `COPY COCOM01Y` (line 650).

```
01 CARDDEMO-COMMAREA.
   05 CDEMO-GENERAL-INFO.
      10 CDEMO-FROM-TRANID             PIC X(04)     Navigation
      10 CDEMO-FROM-PROGRAM            PIC X(08)     Navigation
      10 CDEMO-TO-TRANID               PIC X(04)     Navigation
      10 CDEMO-TO-PROGRAM              PIC X(08)     Navigation
      10 CDEMO-USER-ID                 PIC X(08)     Security
      10 CDEMO-USER-TYPE               PIC X(01)     Security
         88 CDEMO-USRTYP-ADMIN         VALUE 'A'
         88 CDEMO-USRTYP-USER          VALUE 'U'
      10 CDEMO-PGM-CONTEXT             PIC 9(01)     Lifecycle
         88 CDEMO-PGM-ENTER            VALUE 0       (first entry)
         88 CDEMO-PGM-REENTER          VALUE 1       (re-entry)
   05 CDEMO-CUSTOMER-INFO.
      10 CDEMO-CUST-ID                 PIC 9(09)     Data
      10 CDEMO-CUST-FNAME              PIC X(25)     Data
      10 CDEMO-CUST-MNAME              PIC X(25)     Data
      10 CDEMO-CUST-LNAME              PIC X(25)     Data
   05 CDEMO-ACCOUNT-INFO.
      10 CDEMO-ACCT-ID                 PIC 9(11)     Data
      10 CDEMO-ACCT-STATUS             PIC X(01)     Data
   05 CDEMO-CARD-INFO.
      10 CDEMO-CARD-NUM                PIC 9(16)     Data
   05 CDEMO-MORE-INFO.
      10 CDEMO-LAST-MAP                PIC X(7)      Navigation
      10 CDEMO-LAST-MAPSET             PIC X(7)      Navigation
```

### Extended COMMAREA (WS-THIS-PROGCOMMAREA)

COACTUPC extends the COMMAREA beyond `CARDDEMO-COMMAREA` with a program-specific section
called `WS-THIS-PROGCOMMAREA`. This is appended to `WS-COMMAREA` at offset
`LENGTH OF CARDDEMO-COMMAREA + 1`.

Key fields in `WS-THIS-PROGCOMMAREA`:
- `ACUP-OLD-*` -- snapshot of original record values (for optimistic concurrency check)
- `ACUP-NEW-*` -- modified values from screen input
- `ACUP-DETAILS-FETCHED` / `ACUP-DETAILS-NOT-FETCHED` -- data fetch status flags
- `ACUP-CHANGES-OK-NOT-CONFIRMED` / `ACUP-CHANGES-OKAYED-AND-DONE` / `ACUP-CHANGES-FAILED` -- update lifecycle flags

### COMMAREA Read/Write Ownership

| Field                 | COMEN01C (upstream) | COACTUPC (target) | Direction       |
|-----------------------|---------------------|-------------------|-----------------|
| `CDEMO-FROM-TRANID`   | Writes              | Reads             | Caller -> COACTUPC |
| `CDEMO-FROM-PROGRAM`  | Writes              | Reads             | Caller -> COACTUPC |
| `CDEMO-TO-TRANID`     | --                  | Writes            | COACTUPC -> Caller |
| `CDEMO-TO-PROGRAM`    | --                  | Writes            | COACTUPC -> Caller |
| `CDEMO-USER-ID`       | Writes              | Reads             | Shared           |
| `CDEMO-USER-TYPE`     | Writes              | Reads/Writes      | Shared           |
| `CDEMO-PGM-CONTEXT`   | Writes (ENTER)      | Reads/Writes      | Lifecycle control |
| `CDEMO-CUST-ID`       | May set             | Reads/Writes      | Data             |
| `CDEMO-ACCT-ID`       | May set             | Reads/Writes      | Data             |
| `CDEMO-LAST-MAP`      | --                  | Writes            | State            |
| `CDEMO-LAST-MAPSET`   | --                  | Writes            | State            |

### COMMAREA Initialization Logic (lines 880-893)

```
IF EIBCALEN = 0
OR (CDEMO-FROM-PROGRAM = LIT-MENUPGM AND NOT CDEMO-PGM-REENTER)
   INITIALIZE CARDDEMO-COMMAREA
              WS-THIS-PROGCOMMAREA
   SET CDEMO-PGM-ENTER TO TRUE
   SET ACUP-DETAILS-NOT-FETCHED TO TRUE
ELSE
   MOVE DFHCOMMAREA(1:LENGTH OF CARDDEMO-COMMAREA) TO CARDDEMO-COMMAREA
   MOVE DFHCOMMAREA(offset) TO WS-THIS-PROGCOMMAREA
END-IF
```

**Interpretation:**
- `EIBCALEN = 0`: Direct invocation (no COMMAREA) -- initialize fresh
- `FROM-PROGRAM = COMEN01C AND NOT REENTER`: First arrival from menu -- initialize fresh
- Otherwise: Pseudo-conversational re-entry -- restore saved COMMAREA

---

## 7. Business Logic Flow

### Pseudo-Conversational Lifecycle

```
                    TERMINAL INPUT (CAUP)
                           |
                           v
                    +------+------+
                    | EIBCALEN=0? |
                    +------+------+
                    YES    |    NO
                     |     |     |
                     v     |     v
              Initialize   | Restore COMMAREA
              COMMAREA     |
                     |     |     |
                     +-----+-----+
                           |
                           v
                    Store PFKey (YYYY-STORE-PFKEY)
                           |
                           v
                    +------+------+
                    |  EVALUATE   |
                    |   EIBAID    |
                    +------+------+
                     |     |     |
          PF3        |   First   |   Other
          Exit       |   Entry   |   (re-entry)
                     |     |     |
                     v     v     v
              XCTL   Send  1000-PROCESS-INPUTS
              to     blank  1100-RECEIVE-MAP
              caller map    1200-EDIT-MAP-INPUTS
                     |     2000-DECIDE-ACTION
                     |     3000-SEND-MAP
                     |           |
                     +-----+-----+
                           |
                           v
                    COMMON-RETURN
                    RETURN TRANSID('CAUP')
                    COMMAREA(WS-COMMAREA)
```

### EIBAID Handling (Main EVALUATE, lines 921-1004)

| AID Key   | Condition                              | Action                              |
|-----------|----------------------------------------|-------------------------------------|
| PF3       | Always                                 | XCTL to caller (COMEN01C or FROM-PROGRAM) |
| ENTER     | `ACUP-DETAILS-NOT-FETCHED` AND `CDEMO-PGM-ENTER` | Send blank map, set REENTER |
| ENTER     | `CDEMO-FROM-PROGRAM = MENUPGM` AND NOT `REENTER` | Send blank map, set REENTER |
| ENTER     | `ACUP-CHANGES-OKAYED-AND-DONE`         | Reset, send blank map for new search |
| ENTER     | `ACUP-CHANGES-FAILED`                  | Reset, send blank map for new search |
| PF5       | `ACUP-CHANGES-OK-NOT-CONFIRMED`        | Confirm and execute update           |
| PF12      | `ACUP-DETAILS-NOT-FETCHED` is false    | Valid key (treated as OTHER path)    |
| Other     | Any other key                          | `1000-PROCESS-INPUTS` -> `2000-DECIDE-ACTION` -> `3000-SEND-MAP` |

### Update Lifecycle State Machine

```
+----------------------------+
| ACUP-DETAILS-NOT-FETCHED   |  Initial state
+----------------------------+
        | User enters Account ID, presses ENTER
        v
+----------------------------+
| ACUP-DETAILS-FETCHED       |  Account + Customer data displayed
+----------------------------+
        | User modifies fields, presses ENTER
        v
+----------------------------+
| ACUP-CHANGES-OK-NOT-       |  Changes validated, awaiting PF5 confirmation
| CONFIRMED                  |
+----------------------------+
        | User presses PF5 to confirm
        v
+----------------------------+
| ACUP-CHANGES-OKAYED-AND-   |  REWRITE succeeded
| DONE                       |
+----------------------------+
        OR
+----------------------------+
| ACUP-CHANGES-FAILED        |  REWRITE failed
+----------------------------+
```

### Input Validation (1200-EDIT-MAP-INPUTS)

The edit routines perform extensive validation on user-entered fields:

1. **Account ID** -- must be numeric, non-zero
2. **Active Status** -- must be 'Y' or 'N'
3. **Date fields** (Open, Expiry, Reissue, DOB) -- validated via `CSUTLDPY` date routines:
   - Year: valid range (EDIT-YEAR-CCYY)
   - Month: 01-12 (EDIT-MONTH)
   - Day: 01-28/29/30/31 based on month (EDIT-DAY)
   - Full date: EDIT-DATE-CCYYMMDD
4. **Numeric fields** (Credit Limit, Cash Limit, Balance, Cycle Credit/Debit) -- `TEST-NUMVAL-C` intrinsic function
5. **FICO Score** -- range validation (300-850)
6. **State Code** -- validated via `CSLKPCDY` lookup tables
7. **Phone Area Codes** -- validated via `CSLKPCDY` phone area code tables

### Write Processing (9600-WRITE-PROCESSING, lines 3888-4107)

1. **Lock records**: `READ UPDATE` on ACCTDAT and CUSTDAT
2. **Optimistic concurrency check**: `9700-CHECK-CHANGE-IN-REC` compares current record
   values against `ACUP-OLD-*` snapshot. If data changed by another user, update is rejected.
3. **Prepare update records**: Copy `ACUP-NEW-*` fields into `ACCT-UPDATE-RECORD` and
   `CUST-UPDATE-RECORD`, formatting dates as YYYY-MM-DD strings
4. **REWRITE ACCTDAT**: Update account record
5. **REWRITE CUSTDAT**: Update customer record
6. **Rollback on failure**: If customer REWRITE fails after account REWRITE succeeded,
   `SYNCPOINT ROLLBACK` (line 4099-4101) is issued to undo the account update.

### Error Handling

| Error Condition                       | Flag / 88-Level                    | Response                     |
|---------------------------------------|-------------------------------------|------------------------------|
| Record not found on READ              | `INPUT-ERROR`                       | Error message displayed      |
| Cannot lock account for update        | `COULD-NOT-LOCK-ACCT-FOR-UPDATE`   | Error message, no update     |
| Cannot lock customer for update       | `COULD-NOT-LOCK-CUST-FOR-UPDATE`   | Error message, no update     |
| Data changed by another user          | `DATA-WAS-CHANGED-BEFORE-UPDATE`   | Reject update, re-fetch      |
| Account REWRITE failed                | `LOCKED-BUT-UPDATE-FAILED`         | Error message                |
| Customer REWRITE failed               | `LOCKED-BUT-UPDATE-FAILED`         | `SYNCPOINT ROLLBACK`, error  |
| Unexpected abend                      | `ABEND-ROUTINE` (line 4203)        | SEND error, ABEND '9999'    |

---

## 8. Complete EXEC CICS Command Inventory

| # | Command         | Line  | Paragraph / Context            | Details                              |
|---|-----------------|-------|--------------------------------|--------------------------------------|
| 1 | HANDLE ABEND    | 862   | `0000-MAIN`                    | Route abends to ABEND-ROUTINE        |
| 2 | SYNCPOINT       | 952   | PF3 exit path                  | Commit before XCTL                   |
| 3 | XCTL            | 956   | PF3 exit path                  | Transfer to `CDEMO-TO-PROGRAM`       |
| 4 | RETURN          | 1015  | `COMMON-RETURN`                | TRANSID('CAUP'), COMMAREA            |
| 5 | RECEIVE MAP     | 1040  | `1100-RECEIVE-MAP`             | MAP(CACTUPA), MAPSET(COACTUP)        |
| 6 | SEND MAP        | 3594  | `3000-SEND-MAP`                | Dynamic map/mapset, CURSOR, ERASE    |
| 7 | READ            | 3654  | `9200-GETCARDXREF-BYACCT`      | DATASET(CXACAIX), read-only          |
| 8 | READ            | 3703  | `9300-GETACCTDATA-BYACCT`      | DATASET(ACCTDAT), read-only          |
| 9 | READ            | 3753  | `9400-GETCUSTDATA-BYCUST`      | DATASET(CUSTDAT), read-only          |
|10 | READ UPDATE     | 3894  | `9600-WRITE-PROCESSING`        | FILE(ACCTDAT), update lock           |
|11 | READ UPDATE     | 3921  | `9600-WRITE-PROCESSING`        | FILE(CUSTDAT), update lock           |
|12 | REWRITE         | 4065  | `9600-WRITE-PROCESSING`        | FILE(ACCTDAT), account update        |
|13 | REWRITE         | 4085  | `9600-WRITE-PROCESSING`        | FILE(CUSTDAT), customer update       |
|14 | SYNCPOINT ROLLBACK | 4099 | `9600-WRITE-PROCESSING`       | Rollback on customer REWRITE failure |
|15 | SEND (raw)      | 4211  | `ABEND-ROUTINE`                | Send abend message, NOHANDLE, ERASE  |
|16 | HANDLE ABEND CANCEL | 4218 | `ABEND-ROUTINE`              | Cancel abend handling                |
|17 | ABEND           | 4222  | `ABEND-ROUTINE`                | ABCODE('9999')                       |

---

## 9. Complete File Inventory

### Source Files Accessed

| # | File                              | Type          | Role                         |
|---|-----------------------------------|---------------|------------------------------|
| 1 | `app/cbl/COACTUPC.cbl`           | COBOL program | Target program (4237 lines)  |
| 2 | `app/cbl/COMEN01C.cbl`           | COBOL program | Upstream -- Main Menu        |
| 3 | `app/cbl/CSUTLDTC.cbl`           | COBOL program | Date utility (referenced by CSUTLDPY) |
| 4 | `app/bms/COACTUP.bms`            | BMS source    | Screen map definition (513 lines) |
| 5 | `app/cpy-bms/COACTUP.CPY`        | BMS symbolic  | Generated symbolic map (669 lines) |
| 6 | `app/cpy/COCOM01Y.cpy`           | Copybook      | COMMAREA structure           |
| 7 | `app/cpy/CVACT01Y.cpy`           | Copybook      | Account record layout        |
| 8 | `app/cpy/CVACT03Y.cpy`           | Copybook      | Card xref record layout      |
| 9 | `app/cpy/CVCUS01Y.cpy`           | Copybook      | Customer record layout       |
|10 | `app/cpy/CVCRD01Y.cpy`           | Copybook      | Card work areas              |
|11 | `app/cpy/COTTL01Y.cpy`           | Copybook      | Screen titles                |
|12 | `app/cpy/CSDAT01Y.cpy`           | Copybook      | Date/time work areas         |
|13 | `app/cpy/CSMSG01Y.cpy`           | Copybook      | Common messages              |
|14 | `app/cpy/CSMSG02Y.cpy`           | Copybook      | Abend data structure         |
|15 | `app/cpy/CSUSR01Y.cpy`           | Copybook      | User security data           |
|16 | `app/cpy/CSUTLDWY.cpy`           | Copybook      | Date validation WS           |
|17 | `app/cpy/CSUTLDPY.cpy`           | Copybook      | Date validation procedures   |
|18 | `app/cpy/CSSTRPFY.cpy`           | Copybook      | PFKey store routine          |
|19 | `app/cpy/CSLKPCDY.cpy`           | Copybook      | Lookup code repository       |
|20 | `app/cpy/CSSETATY.cpy`           | Copybook      | Field attribute template     |
|21 | `app/cpy/COMEN02Y.cpy`           | Copybook      | Menu options (upstream ref)  |

### System Resources

| Resource      | Description                              |
|---------------|------------------------------------------|
| `DFHBMSCA`    | IBM CICS system copybook -- BMS attributes |
| `DFHAID`      | IBM CICS system copybook -- AID key constants |

---

## 10. Transaction Record Layout

### Account Update Record (ACCT-UPDATE-RECORD)

Built from `CVACT01Y.cpy` layout. Key fields written during update:

| Field                       | PIC             | Source                         |
|-----------------------------|-----------------|--------------------------------|
| ACCT-UPDATE-ID              | 9(11)           | ACUP-NEW-ACCT-ID               |
| ACCT-UPDATE-ACTIVE-STATUS   | X(01)           | ACUP-NEW-ACTIVE-STATUS         |
| ACCT-UPDATE-CURR-BAL        | S9(10)V9(2)     | ACUP-NEW-CURR-BAL-N            |
| ACCT-UPDATE-CREDIT-LIMIT    | S9(10)V9(2)     | ACUP-NEW-CREDIT-LIMIT-N        |
| ACCT-UPDATE-CASH-CREDIT-LIMIT | S9(10)V9(2)  | ACUP-NEW-CASH-CREDIT-LIMIT-N   |
| ACCT-UPDATE-OPEN-DATE       | X(10)           | YYYY-MM-DD string              |
| ACCT-UPDATE-EXPIRAION-DATE  | X(10)           | YYYY-MM-DD string              |
| ACCT-UPDATE-REISSUE-DATE    | X(10)           | YYYY-MM-DD string              |
| ACCT-UPDATE-CURR-CYC-CREDIT | S9(10)V9(2)    | ACUP-NEW-CURR-CYC-CREDIT-N     |
| ACCT-UPDATE-CURR-CYC-DEBIT  | S9(10)V9(2)    | ACUP-NEW-CURR-CYC-DEBIT-N      |
| ACCT-UPDATE-GROUP-ID        | X(10)           | ACUP-NEW-GROUP-ID              |

### Customer Update Record (CUST-UPDATE-RECORD)

Built from `CVCUS01Y.cpy` layout. Key fields written during update:

| Field                          | PIC           | Source                         |
|--------------------------------|---------------|--------------------------------|
| CUST-UPDATE-ID                 | 9(09)         | ACUP-NEW-CUST-ID               |
| CUST-UPDATE-FIRST-NAME         | X(25)         | ACUP-NEW-CUST-FIRST-NAME       |
| CUST-UPDATE-MIDDLE-NAME        | X(25)         | ACUP-NEW-CUST-MIDDLE-NAME      |
| CUST-UPDATE-LAST-NAME          | X(25)         | ACUP-NEW-CUST-LAST-NAME        |
| CUST-UPDATE-ADDR-LINE-1        | X(50)         | ACUP-NEW-CUST-ADDR-LINE-1      |
| CUST-UPDATE-ADDR-LINE-2        | X(50)         | ACUP-NEW-CUST-ADDR-LINE-2      |
| CUST-UPDATE-ADDR-LINE-3        | X(50)         | ACUP-NEW-CUST-ADDR-LINE-3      |
| CUST-UPDATE-ADDR-STATE-CD      | X(02)         | ACUP-NEW-CUST-ADDR-STATE-CD    |
| CUST-UPDATE-ADDR-COUNTRY-CD    | X(03)         | ACUP-NEW-CUST-ADDR-COUNTRY-CD  |
| CUST-UPDATE-ADDR-ZIP           | X(10)         | ACUP-NEW-CUST-ADDR-ZIP         |
| CUST-UPDATE-PHONE-NUM-1        | X(15)         | Formatted from 3 parts          |
| CUST-UPDATE-PHONE-NUM-2        | X(15)         | Formatted from 3 parts          |
| CUST-UPDATE-SSN                | X(09)         | ACUP-NEW-CUST-SSN              |
| CUST-UPDATE-GOVT-ISSUED-ID     | X(20)         | ACUP-NEW-CUST-GOVT-ISSUED-ID   |
| CUST-UPDATE-DOB-YYYY-MM-DD     | X(10)         | YYYY-MM-DD string              |
| CUST-UPDATE-EFT-ACCOUNT-ID     | X(10)         | ACUP-NEW-CUST-EFT-ACCOUNT-ID   |
| CUST-UPDATE-PRI-CARD-IND       | X(01)         | ACUP-NEW-CUST-PRI-HOLDER-IND   |
| CUST-UPDATE-FICO-CREDIT-SCORE  | 9(03)         | ACUP-NEW-CUST-FICO-SCORE       |

---

## 11. Observations and Migration Complexity

### Architecture Observations

1. **Pseudo-conversational complexity**: The program uses a multi-state lifecycle
   (`NOT-FETCHED` -> `FETCHED` -> `OK-NOT-CONFIRMED` -> `DONE`/`FAILED`) stored in extended
   COMMAREA. This requires careful session state management in a modernized architecture.

2. **Optimistic concurrency control**: The `9700-CHECK-CHANGE-IN-REC` paragraph compares
   every field in the account and customer records against a snapshot taken when data was
   first displayed. This is a sophisticated pattern that must be preserved or replaced with
   equivalent mechanisms (e.g., ETags, version columns).

3. **Two-phase update with rollback**: The program updates two VSAM files (ACCTDAT, CUSTDAT)
   in sequence. If the second REWRITE fails, `SYNCPOINT ROLLBACK` undoes the first. This
   maps to a database transaction with ACID guarantees in a modernized system.

4. **BMS field-level I/O**: The program manipulates individual screen fields via the BMS
   symbolic map. Each field has length (`xxxL`), flag (`xxxF`), attribute (`xxxA`), and
   input (`xxxI`) / output (`xxxO`) sub-fields. This must be mapped to form fields in a
   web UI.

5. **Inline COPY for procedures**: Rather than using CALL for sub-programs, COACTUPC
   includes `CSUTLDPY` and `CSSTRPFY` directly into its PROCEDURE DIVISION via COPY.
   This increases the effective program size significantly (~4700 lines when expanded).

6. **No CALL statements**: All logic is self-contained or inlined. There are no external
   runtime CALL dependencies, simplifying the deployment unit.

### Migration Considerations

| Concern                       | Complexity | Notes                                               |
|-------------------------------|------------|-----------------------------------------------------|
| Screen conversion (BMS->Web)  | High       | 40+ input fields, complex layout, attribute control  |
| State management              | High       | Extended COMMAREA with old/new value pairs           |
| VSAM to RDBMS                 | Medium     | 3 files, clear key relationships, straightforward    |
| Date validation               | Low        | Standard CCYYMMDD routines, well-isolated            |
| Concurrency control           | High       | Full field-by-field comparison requires redesign     |
| Transaction integrity         | Medium     | 2-file update with rollback, maps to DB transaction  |
| Navigation (XCTL/RETURN)      | Medium     | Menu-driven, COMMAREA-based routing                  |
| Lookup validation             | Low        | State/zip/phone code tables, can be externalized     |

### Comparison with Similar CardDemo Programs

| Attribute              | COACTUPC (Account Update) | Typical View Program |
|------------------------|---------------------------|----------------------|
| VSAM I/O operations    | 8 (READ, READ UPDATE, REWRITE) | 3-4 (READ only) |
| BMS fields             | 40+                        | 20-30               |
| Update lifecycle states| 4                          | 1 (display only)     |
| Concurrency check      | Yes (field-by-field)       | N/A                  |
| SYNCPOINT ROLLBACK     | Yes                        | No                   |
| Program lines          | 4237                       | 1000-2000            |
| COPY statements        | 15                         | 8-10                 |

---

*Analysis generated from source inspection of `choikh0423/aws-mainframe-modernization-carddemo`
on branch `demos/cobol-full-docs`.*

# COACTVWC (CAVW) — Account View Transaction Flow Analysis

## Section 1: Flow Overview

| Attribute              | Value                                      |
|------------------------|--------------------------------------------|
| **Transaction ID**     | `CAVW`                                     |
| **Entry Program**      | `COACTVWC`                                 |
| **Function**           | Display account details (read-only view)   |
| **Mapset / Map**       | `COACTVW` / `CACTVWA`                      |
| **Conversation Type**  | Pseudo-conversational (RETURN TRANSID)     |
| **Data Access**        | VSAM (read-only)                           |
| **CSD Group**          | CARDDEMO                                   |

### Navigation Path

```
 COSGN00C        COMEN01C            COACTVWC
 (CC00)          (CM00)              (CAVW)
 Sign-on    -->  Main Menu     -->   Account View
               Option 1: "Account View"
               via XCTL with COMMAREA
```

### Pseudo-Conversational Lifecycle

```
  Terminal                          CICS                         VSAM
     |                               |                            |
     |--- CC00 Sign-on ------------->|                            |
     |<-- Menu Screen (CM00) --------|                            |
     |--- Select Option 1 --------->|                            |
     |    COMEN01C XCTLs to         |                            |
     |    COACTVWC (CAVW)           |                            |
     |                               |                            |
     |  [Iteration 1 — First Entry: PGM-CONTEXT=0]               |
     |<-- SEND MAP (empty form) ----|                            |
     |    RETURN TRANSID('CAVW')    |                            |
     |                               |                            |
     |  [Iteration 2+ — Re-entry: PGM-CONTEXT=1]                 |
     |--- Enter (account number) -->|                            |
     |    RECEIVE MAP               |                            |
     |    Validate account number   |                            |
     |    If valid:                 |                            |
     |                              |-- READ CXACAIX ----------->|
     |                              |<- CARD-XREF-RECORD --------|
     |                              |-- READ ACCTDAT ----------->|
     |                              |<- ACCOUNT-RECORD ----------|
     |                              |-- READ CUSTDAT ----------->|
     |                              |<- CUSTOMER-RECORD ---------|
     |<-- SEND MAP (with data) ----|                            |
     |    RETURN TRANSID('CAVW')    |                            |
     |                               |                            |
     |  [Exit — PF3 pressed]         |                            |
     |--- PF3 --------------------->|                            |
     |    XCTL to calling program   |                            |
     |    (COMEN01C / CM00)         |                            |
     v                               v                            v
```

---

## Section 2: Programs Involved

| # | Program    | Type         | Function                           | Called By  | Call Method                    |
|---|------------|--------------|------------------------------------|------------|--------------------------------|
| 1 | COSGN00C   | CICS online  | Sign-on / Authentication           | (terminal) | Transaction CC00               |
| 2 | COMEN01C   | CICS online  | Main Menu (regular users)          | COSGN00C   | XCTL                           |
| 3 | COACTVWC   | CICS online  | Account View (target program)      | COMEN01C   | XCTL via menu table option 1   |

**Notes:**
- COACTVWC does not CALL or LINK to any sub-programs.
- On PF3 (exit), COACTVWC XCTLs back to the calling program (resolved from `CDEMO-FROM-PROGRAM`, defaulting to COMEN01C).

---

## Section 3: Copybooks

| # | Copybook    | Used By   | Classification               | Purpose                                          |
|---|-------------|-----------|------------------------------|--------------------------------------------------|
| 1 | CVCRD01Y    | COACTVWC  | Work area                    | Common card-demo work area: AID keys, navigation fields, account/card/customer IDs |
| 2 | COCOM01Y    | COACTVWC  | COMMAREA definition          | Application COMMAREA: navigation, customer, account, card info |
| 3 | DFHBMSCA    | COACTVWC  | CICS system copybook         | BMS attribute constants (DFHBMFSE, DFHRED, DFHDFCOL, etc.) |
| 4 | DFHAID      | COACTVWC  | CICS system copybook         | AID key constants (DFHENTER, DFHPF3, DFHCLEAR, etc.) |
| 5 | COTTL01Y    | COACTVWC  | Screen title/message         | Screen title lines ("AWS Mainframe Modernization", "CardDemo") |
| 6 | COACTVW     | COACTVWC  | BMS symbolic map (generated) | Symbolic map for CACTVWA map — field I/O structures (CACTVWAI / CACTVWAO) |
| 7 | CSDAT01Y    | COACTVWC  | Date/time structure          | Current date/time working storage fields          |
| 8 | CSMSG01Y    | COACTVWC  | Common messages              | Shared message literals (thank-you, invalid key)  |
| 9 | CSMSG02Y    | COACTVWC  | Abend variables              | ABEND-DATA structure (code, culprit, reason, msg) |
|10 | CSUSR01Y    | COACTVWC  | User security data           | SEC-USER-DATA structure (user id, name, pwd, type)|
|11 | CVACT01Y    | COACTVWC  | Record layout (VSAM)         | ACCOUNT-RECORD layout for ACCTDAT file (300 bytes)|
|12 | CVACT02Y    | COACTVWC  | Record layout (VSAM)         | CARD-RECORD layout for CARDDAT file (150 bytes)   |
|13 | CVACT03Y    | COACTVWC  | Record layout (VSAM)         | CARD-XREF-RECORD layout for CCXREF/CXACAIX (50 bytes) |
|14 | CVCUS01Y    | COACTVWC  | Record layout (VSAM)         | CUSTOMER-RECORD layout for CUSTDAT file (500 bytes)|
|15 | CSSTRPFY    | COACTVWC  | Common code (PROCEDURE DIV)  | YYYY-STORE-PFKEY paragraph — maps EIBAID to CCARD-AID flags |

### Record Layout Details

#### CVACT01Y — ACCOUNT-RECORD (ACCTDAT, 300 bytes)

| Level | Field                    | PIC Clause       | Description                |
|-------|--------------------------|------------------|----------------------------|
| 05    | ACCT-ID                  | 9(11)            | Account identifier (key)   |
| 05    | ACCT-ACTIVE-STATUS       | X(01)            | Active flag (Y/N)          |
| 05    | ACCT-CURR-BAL            | S9(10)V99        | Current balance            |
| 05    | ACCT-CREDIT-LIMIT        | S9(10)V99        | Credit limit               |
| 05    | ACCT-CASH-CREDIT-LIMIT   | S9(10)V99        | Cash credit limit          |
| 05    | ACCT-OPEN-DATE           | X(10)            | Account open date          |
| 05    | ACCT-EXPIRAION-DATE      | X(10)            | Expiration date            |
| 05    | ACCT-REISSUE-DATE        | X(10)            | Reissue date               |
| 05    | ACCT-CURR-CYC-CREDIT     | S9(10)V99        | Current cycle credit       |
| 05    | ACCT-CURR-CYC-DEBIT      | S9(10)V99        | Current cycle debit        |
| 05    | ACCT-ADDR-ZIP            | X(10)            | ZIP code                   |
| 05    | ACCT-GROUP-ID            | X(10)            | Account group              |
| 05    | FILLER                   | X(178)           | Reserved                   |

#### CVACT03Y — CARD-XREF-RECORD (CCXREF / CXACAIX, 50 bytes)

| Level | Field           | PIC Clause | Description                      |
|-------|-----------------|------------|----------------------------------|
| 05    | XREF-CARD-NUM   | X(16)      | Card number                      |
| 05    | XREF-CUST-ID    | 9(09)      | Customer ID                      |
| 05    | XREF-ACCT-ID    | 9(11)      | Account ID                       |
| 05    | FILLER          | X(14)      | Reserved                         |

#### CVCUS01Y — CUSTOMER-RECORD (CUSTDAT, 500 bytes)

| Level | Field                       | PIC Clause | Description                 |
|-------|-----------------------------|------------|-----------------------------|
| 05    | CUST-ID                     | 9(09)      | Customer ID (key)           |
| 05    | CUST-FIRST-NAME             | X(25)      | First name                  |
| 05    | CUST-MIDDLE-NAME            | X(25)      | Middle name                 |
| 05    | CUST-LAST-NAME              | X(25)      | Last name                   |
| 05    | CUST-ADDR-LINE-1            | X(50)      | Address line 1              |
| 05    | CUST-ADDR-LINE-2            | X(50)      | Address line 2              |
| 05    | CUST-ADDR-LINE-3            | X(50)      | City                        |
| 05    | CUST-ADDR-STATE-CD          | X(02)      | State code                  |
| 05    | CUST-ADDR-COUNTRY-CD        | X(03)      | Country code                |
| 05    | CUST-ADDR-ZIP               | X(10)      | ZIP code                    |
| 05    | CUST-PHONE-NUM-1            | X(15)      | Phone number 1              |
| 05    | CUST-PHONE-NUM-2            | X(15)      | Phone number 2              |
| 05    | CUST-SSN                    | 9(09)      | Social security number      |
| 05    | CUST-GOVT-ISSUED-ID         | X(20)      | Government-issued ID        |
| 05    | CUST-DOB-YYYY-MM-DD         | X(10)      | Date of birth               |
| 05    | CUST-EFT-ACCOUNT-ID         | X(10)      | EFT account ID              |
| 05    | CUST-PRI-CARD-HOLDER-IND    | X(01)      | Primary cardholder (Y/N)    |
| 05    | CUST-FICO-CREDIT-SCORE      | 9(03)      | FICO credit score           |
| 05    | FILLER                      | X(168)     | Reserved                    |

#### CVACT02Y — CARD-RECORD (CARDDAT, 150 bytes)

| Level | Field                  | PIC Clause | Description              |
|-------|------------------------|------------|--------------------------|
| 05    | CARD-NUM               | X(16)      | Card number (key)        |
| 05    | CARD-ACCT-ID           | 9(11)      | Account ID               |
| 05    | CARD-CVV-CD            | 9(03)      | CVV code                 |
| 05    | CARD-EMBOSSED-NAME     | X(50)      | Name on card             |
| 05    | CARD-EXPIRAION-DATE    | X(10)      | Expiration date          |
| 05    | CARD-ACTIVE-STATUS     | X(01)      | Active flag              |
| 05    | FILLER                 | X(59)      | Reserved                 |

**Note:** CVACT02Y (CARD-RECORD) is included via COPY but is **not directly used** by COACTVWC's file I/O operations. It is available in working storage but the program only reads from CXACAIX (xref), ACCTDAT (accounts), and CUSTDAT (customers).

---

## Section 4: VSAM File Operations

### File Summary

| # | VSAM File | DD Name    | Record Copybook | Key Field             | Record Length | Access   |
|---|-----------|------------|------------------|-----------------------|---------------|----------|
| 1 | CXACAIX   | CXACAIX    | CVACT03Y         | ACCT-ID (11 bytes)    | 50 bytes      | Read     |
| 2 | ACCTDAT   | ACCTDAT    | CVACT01Y         | ACCT-ID (11 bytes)    | 300 bytes     | Read     |
| 3 | CUSTDAT   | CUSTDAT    | CVCUS01Y         | CUST-ID (9 bytes)     | 500 bytes     | Read     |

### Detailed I/O Operations

#### Operation 1: Read Card Cross-Reference (9200-GETCARDXREF-BYACCT)

```
EXEC CICS READ
     DATASET   (LIT-CARDXREFNAME-ACCT-PATH)    -- 'CXACAIX'
     RIDFLD    (WS-CARD-RID-ACCT-ID-X)          -- Account ID (11 bytes)
     KEYLENGTH (LENGTH OF WS-CARD-RID-ACCT-ID-X)
     INTO      (CARD-XREF-RECORD)
     LENGTH    (LENGTH OF CARD-XREF-RECORD)
     RESP      (WS-RESP-CD)
     RESP2     (WS-REAS-CD)
END-EXEC
```

- **Purpose:** Look up the card cross-reference record by account ID via the CXACAIX alternate index path (alternate index over CCXREF base cluster).
- **On NORMAL:** Extracts `XREF-CUST-ID` -> `CDEMO-CUST-ID` and `XREF-CARD-NUM` -> `CDEMO-CARD-NUM`.
- **On NOTFND:** Sets `INPUT-ERROR`, builds error message with account ID and RESP/RESP2 codes.
- **On OTHER:** Sets `INPUT-ERROR`, builds generic file error message.

#### Operation 2: Read Account Master (9300-GETACCTDATA-BYACCT)

```
EXEC CICS READ
     DATASET   (LIT-ACCTFILENAME)               -- 'ACCTDAT'
     RIDFLD    (WS-CARD-RID-ACCT-ID-X)          -- Account ID (11 bytes)
     KEYLENGTH (LENGTH OF WS-CARD-RID-ACCT-ID-X)
     INTO      (ACCOUNT-RECORD)
     LENGTH    (LENGTH OF ACCOUNT-RECORD)
     RESP      (WS-RESP-CD)
     RESP2     (WS-REAS-CD)
END-EXEC
```

- **Purpose:** Read the full account record from the account master file.
- **On NORMAL:** Sets `FOUND-ACCT-IN-MASTER` flag.
- **On NOTFND:** Sets `INPUT-ERROR`, builds error message with account ID and RESP/RESP2 codes.
- **On OTHER:** Sets `INPUT-ERROR`, builds generic file error message.

#### Operation 3: Read Customer Master (9400-GETCUSTDATA-BYCUST)

```
EXEC CICS READ
     DATASET   (LIT-CUSTFILENAME)               -- 'CUSTDAT'
     RIDFLD    (WS-CARD-RID-CUST-ID-X)          -- Customer ID (9 bytes)
     KEYLENGTH (LENGTH OF WS-CARD-RID-CUST-ID-X)
     INTO      (CUSTOMER-RECORD)
     LENGTH    (LENGTH OF CUSTOMER-RECORD)
     RESP      (WS-RESP-CD)
     RESP2     (WS-REAS-CD)
END-EXEC
```

- **Purpose:** Read the full customer record using the customer ID obtained from the xref record.
- **On NORMAL:** Sets `FOUND-CUST-IN-MASTER` flag.
- **On NOTFND:** Sets `INPUT-ERROR`, builds error message with customer ID and RESP/RESP2 codes.
- **On OTHER:** Sets `INPUT-ERROR`, builds generic file error message.

### VSAM File Flow Diagram

```
                    User enters Account ID
                            |
                            v
              +----------------------------+
              | 9200-GETCARDXREF-BYACCT    |
              | READ CXACAIX (AIX path)    |
              | Key: Account ID (11 bytes) |
              +----------------------------+
                     |            |
                 NORMAL        NOTFND/OTHER
                     |            |
                     v            v
              Get CUST-ID    Error Message
              Get CARD-NUM   (return)
                     |
                     v
              +----------------------------+
              | 9300-GETACCTDATA-BYACCT    |
              | READ ACCTDAT               |
              | Key: Account ID (11 bytes) |
              +----------------------------+
                     |            |
                 NORMAL        NOTFND/OTHER
                     |            |
                     v            v
              FOUND-ACCT     Error Message
              flag set       (return)
                     |
                     v
              +----------------------------+
              | 9400-GETCUSTDATA-BYCUST    |
              | READ CUSTDAT               |
              | Key: Customer ID (9 bytes) |
              +----------------------------+
                     |            |
                 NORMAL        NOTFND/OTHER
                     |            |
                     v            v
              FOUND-CUST     Error Message
              flag set       (return)
                     |
                     v
              Display account + customer
              details on screen
```

**Note on CXACAIX:** This is an alternate index path over the CCXREF base cluster. The base cluster (CCXREF) is keyed by card number. The CXACAIX alternate index allows lookup by account ID. CSD definition confirms: `DSNAME(AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH)`.

---

## Section 5: BMS Screen Map

### Map Identification

| Attribute     | Value        |
|---------------|--------------|
| **Mapset**    | `COACTVW`    |
| **Map**       | `CACTVWA`    |
| **Size**      | 24 rows x 80 columns |
| **Mode**      | INOUT        |
| **Source**     | `app/bms/COACTVW.bms` |

### ASCII Screen Layout

```
Row  1: Tran: CAVW       AWS Mainframe Modernization          Date: mm/dd/yy
Row  2: Prog: COACTVWC             CardDemo                   Time: hh:mm:ss
Row  3:
Row  4:                                  View Account
Row  5:                    Account Number : [___________]      Active Y/N: [_]
Row  6:         Opened: [__________]            Credit Limit        : [+ZZZ,ZZZ,ZZZ.99]
Row  7:         Expiry: [__________]            Cash credit Limit   : [+ZZZ,ZZZ,ZZZ.99]
Row  8:        Reissue: [__________]            Current Balance     : [+ZZZ,ZZZ,ZZZ.99]
Row  9:                                         Current Cycle Credit: [+ZZZ,ZZZ,ZZZ.99]
Row 10:        Account Group: [__________]      Current Cycle Debit : [+ZZZ,ZZZ,ZZZ.99]
Row 11:                                 Customer Details
Row 12:        Customer id  : [_________]       SSN: [____________]
Row 13:        Date of birth: [__________]      FICO Score: [___]
Row 14: First Name            Middle Name:             Last Name :
Row 15: [_________________________] [_________________________] [_________________________]
Row 16: Address: [__________________________________________________] State  [__]
Row 17:          [__________________________________________________] Zip    [_____]
Row 18: City   [__________________________________________________] Country [___]
Row 19: Phone 1: [_____________]  Government Issued Id Ref    :  [____________________]
Row 20: Phone 2: [_____________]  EFT Account Id:  [__________]  Primary Card Holder Y/N: [_]
Row 21:
Row 22:                        [Info message area (45 chars)]
Row 23: [Error message area (78 chars, red, bright)]
Row 24:   F3=Exit
```

### Field Inventory

| # | Field Name | Row | Col | Length | Attr          | Color     | Purpose                    |
|---|------------|-----|-----|--------|---------------|-----------|----------------------------|
| 1 | TRNNAME    |  1  |  7  |   4    | ASKIP,FSET    | Blue      | Transaction ID (CAVW)      |
| 2 | TITLE01    |  1  | 21  |  40    | ASKIP         | Yellow    | Screen title line 1        |
| 3 | CURDATE    |  1  | 71  |   8    | ASKIP         | Blue      | Current date (mm/dd/yy)    |
| 4 | PGMNAME    |  2  |  7  |   8    | ASKIP         | Blue      | Program name (COACTVWC)    |
| 5 | TITLE02    |  2  | 21  |  40    | ASKIP         | Yellow    | Screen title line 2        |
| 6 | CURTIME    |  2  | 71  |   8    | ASKIP         | Blue      | Current time (hh:mm:ss)    |
| 7 | ACCTSID    |  5  | 38  |  11    | FSET,IC,UNPROT| Green     | **Account number (INPUT)**  |
| 8 | ACSTTUS    |  5  | 70  |   1    | ASKIP         | (default) | Active status (Y/N)        |
| 9 | ADTOPEN    |  6  | 17  |  10    | (default)     | (default) | Account open date          |
|10 | ACRDLIM    |  6  | 61  |  15    | (default)     | (default) | Credit limit (formatted)   |
|11 | AEXPDT     |  7  | 17  |  10    | (default)     | (default) | Expiration date            |
|12 | ACSHLIM    |  7  | 61  |  15    | (default)     | (default) | Cash credit limit          |
|13 | AREISDT    |  8  | 17  |  10    | (default)     | (default) | Reissue date               |
|14 | ACURBAL    |  8  | 61  |  15    | (default)     | (default) | Current balance            |
|15 | ACRCYCR    |  9  | 61  |  15    | (default)     | (default) | Current cycle credit       |
|16 | AADDGRP    | 10  | 23  |  10    | (default)     | (default) | Account group              |
|17 | ACRCYDB    | 10  | 61  |  15    | (default)     | (default) | Current cycle debit        |
|18 | ACSTNUM    | 12  | 23  |   9    | (default)     | (default) | Customer ID                |
|19 | ACSTSSN    | 12  | 54  |  12    | (default)     | (default) | SSN (formatted XXX-XX-XXXX)|
|20 | ACSTDOB    | 13  | 23  |  10    | (default)     | (default) | Date of birth              |
|21 | ACSTFCO    | 13  | 61  |   3    | (default)     | (default) | FICO credit score          |
|22 | ACSFNAM    | 15  |  1  |  25    | (default)     | (default) | First name                 |
|23 | ACSMNAM    | 15  | 28  |  25    | (default)     | (default) | Middle name                |
|24 | ACSLNAM    | 15  | 55  |  25    | (default)     | (default) | Last name                  |
|25 | ACSADL1    | 16  | 10  |  50    | (default)     | (default) | Address line 1             |
|26 | ACSSTTE    | 16  | 73  |   2    | (default)     | (default) | State code                 |
|27 | ACSADL2    | 17  | 10  |  50    | (default)     | (default) | Address line 2             |
|28 | ACSZIPC    | 17  | 73  |   5    | (default)     | (default) | ZIP code                   |
|29 | ACSCITY    | 18  | 10  |  50    | (default)     | (default) | City                       |
|30 | ACSCTRY    | 18  | 73  |   3    | (default)     | (default) | Country code               |
|31 | ACSPHN1    | 19  | 10  |  13    | (default)     | (default) | Phone number 1             |
|32 | ACSGOVT    | 19  | 58  |  20    | (default)     | (default) | Government-issued ID       |
|33 | ACSPHN2    | 20  | 10  |  13    | (default)     | (default) | Phone number 2             |
|34 | ACSEFTC    | 20  | 41  |  10    | (default)     | (default) | EFT account ID             |
|35 | ACSPFLG    | 20  | 78  |   1    | (default)     | (default) | Primary cardholder (Y/N)   |
|36 | INFOMSG    | 22  | 23  |  45    | PROT          | Neutral   | Informational message      |
|37 | ERRMSG     | 23  |  1  |  78    | ASKIP,BRT,FSET| Red       | Error message              |

**Input Fields:** Only `ACCTSID` (account number) is unprotected for user input. All other data fields are display-only.

### Function Key Assignments

| Key    | Action                                                        |
|--------|---------------------------------------------------------------|
| Enter  | Submit account number for lookup                              |
| PF3    | Exit — XCTL to calling program (COMEN01C) or menu            |
| Other  | Treated as Enter (invalid keys reset to Enter)                |

---

## Section 6: COMMAREA Structure

### COMMAREA Copybook: COCOM01Y

```
01 CARDDEMO-COMMAREA.
   05 CDEMO-GENERAL-INFO.
      10 CDEMO-FROM-TRANID          PIC X(04)    -- Source transaction ID
      10 CDEMO-FROM-PROGRAM         PIC X(08)    -- Source program name
      10 CDEMO-TO-TRANID            PIC X(04)    -- Target transaction ID
      10 CDEMO-TO-PROGRAM           PIC X(08)    -- Target program name
      10 CDEMO-USER-ID              PIC X(08)    -- Logged-in user ID
      10 CDEMO-USER-TYPE            PIC X(01)    -- 'A'=Admin, 'U'=User
      10 CDEMO-PGM-CONTEXT          PIC 9(01)    -- 0=Enter, 1=Reenter
   05 CDEMO-CUSTOMER-INFO.
      10 CDEMO-CUST-ID              PIC 9(09)    -- Customer ID
      10 CDEMO-CUST-FNAME           PIC X(25)    -- First name
      10 CDEMO-CUST-MNAME           PIC X(25)    -- Middle name
      10 CDEMO-CUST-LNAME           PIC X(25)    -- Last name
   05 CDEMO-ACCOUNT-INFO.
      10 CDEMO-ACCT-ID              PIC 9(11)    -- Account ID
      10 CDEMO-ACCT-STATUS          PIC X(01)    -- Account status
   05 CDEMO-CARD-INFO.
      10 CDEMO-CARD-NUM             PIC 9(16)    -- Card number
   05 CDEMO-MORE-INFO.
      10 CDEMO-LAST-MAP             PIC X(7)     -- Last map displayed
      10 CDEMO-LAST-MAPSET          PIC X(7)     -- Last mapset used
```

### Program-Local Extension: WS-THIS-PROGCOMMAREA

```
01 WS-THIS-PROGCOMMAREA.
   05 CA-CALL-CONTEXT.
      10 CA-FROM-PROGRAM            PIC X(08)    -- Calling program
      10 CA-FROM-TRANID             PIC X(04)    -- Calling transaction
```

The full COMMAREA passed on RETURN is: `CARDDEMO-COMMAREA` + `WS-THIS-PROGCOMMAREA` concatenated into `WS-COMMAREA` (PIC X(2000)).

### COMMAREA Field Read/Write by Program

| Field                | COMEN01C (Writer) | COACTVWC (Reader)     | COACTVWC (Writer)        |
|----------------------|-------------------|-----------------------|--------------------------|
| CDEMO-FROM-TRANID    | Sets to CM00      | Reads for PF3 return  | Sets to CAVW on exit     |
| CDEMO-FROM-PROGRAM   | Sets to COMEN01C  | Reads for PF3 return  | Sets to COACTVWC on exit |
| CDEMO-TO-TRANID      | Sets to CAVW      | --                    | Sets on PF3 exit         |
| CDEMO-TO-PROGRAM     | Sets to COACTVWC  | --                    | Sets on PF3 exit         |
| CDEMO-USER-TYPE      | Sets (U/A)        | --                    | Sets to 'U' on PF3 exit |
| CDEMO-PGM-CONTEXT    | Sets to 0 (Enter) | Reads to branch logic | Sets to 1 (Reenter)     |
| CDEMO-ACCT-ID        | --                | --                    | Sets from validated input|
| CDEMO-CUST-ID        | --                | --                    | Sets from XREF read      |
| CDEMO-CARD-NUM       | --                | --                    | Sets from XREF read      |
| CDEMO-LAST-MAP       | --                | --                    | Sets to CACTVWA          |
| CDEMO-LAST-MAPSET    | --                | --                    | Sets to COACTVW          |

### COMMAREA Flow Diagram

```
  COMEN01C                        COACTVWC                        COMEN01C
  (Menu)                          (Account View)                  (Return)
     |                                |                              |
     |-- XCTL with COMMAREA -------->|                              |
     |   CDEMO-FROM-PROGRAM=COMEN01C |                              |
     |   CDEMO-FROM-TRANID=CM00      |                              |
     |   CDEMO-PGM-CONTEXT=0         |                              |
     |   CDEMO-TO-PROGRAM=COACTVWC   |                              |
     |                                |                              |
     |                 [Pseudo-conv iterations]                      |
     |                 RETURN TRANSID('CAVW')                        |
     |                 COMMAREA = CARDDEMO-COMMAREA                  |
     |                          + WS-THIS-PROGCOMMAREA               |
     |                 CDEMO-PGM-CONTEXT set to 1                    |
     |                 CDEMO-ACCT-ID set from input                  |
     |                 CDEMO-CUST-ID set from XREF                   |
     |                 CDEMO-CARD-NUM set from XREF                  |
     |                                |                              |
     |                 [On PF3]       |                              |
     |                                |-- XCTL with COMMAREA ------->|
     |                                |   CDEMO-FROM-PROGRAM=COACTVWC|
     |                                |   CDEMO-FROM-TRANID=CAVW     |
     |                                |   CDEMO-TO-PROGRAM=COMEN01C  |
     |                                |   CDEMO-TO-TRANID=CM00       |
     |                                |   CDEMO-PGM-CONTEXT=0        |
```

---

## Section 7: Business Logic Flow

### Step-by-Step Narrative

#### 1. Entry and Initialization (0000-MAIN)

1. **HANDLE ABEND** is registered to label `ABEND-ROUTINE`.
2. Work areas (`CC-WORK-AREA`, `WS-MISC-STORAGE`, `WS-COMMAREA`) are initialized.
3. `WS-TRANID` is set to `'CAVW'`.
4. Error message is cleared (`WS-RETURN-MSG-OFF`).

#### 2. COMMAREA Handling

5. **If EIBCALEN = 0** (no COMMAREA — direct invocation) **OR** (arriving from menu and not re-entering):
   - Initialize `CARDDEMO-COMMAREA` and `WS-THIS-PROGCOMMAREA` to defaults.
6. **Else** (COMMAREA passed):
   - Unpack `DFHCOMMAREA` into `CARDDEMO-COMMAREA` + `WS-THIS-PROGCOMMAREA`.

#### 3. PF Key Mapping

7. Perform `YYYY-STORE-PFKEY` (CSSTRPFY copybook) to map `EIBAID` to `CCARD-AID-xxx` flag.
8. Only **Enter** and **PF3** are valid. All other keys are treated as Enter.

#### 4. Main Dispatch (EVALUATE TRUE)

**WHEN CCARD-AID-PFK03 (PF3 — Exit):**

9. Determine return target:
   - If `CDEMO-FROM-TRANID` is empty → use `CM00` (menu)
   - Else → use original `CDEMO-FROM-TRANID`
   - If `CDEMO-FROM-PROGRAM` is empty → use `COMEN01C` (menu)
   - Else → use original `CDEMO-FROM-PROGRAM`
10. Set COMMAREA navigation fields for return:
    - `CDEMO-FROM-TRANID` = `CAVW`
    - `CDEMO-FROM-PROGRAM` = `COACTVWC`
    - `CDEMO-USRTYP-USER` = `TRUE`
    - `CDEMO-PGM-ENTER` = `TRUE` (context=0)
    - `CDEMO-LAST-MAPSET` = `COACTVW`
    - `CDEMO-LAST-MAP` = `CACTVWA`
11. **EXEC CICS XCTL** to `CDEMO-TO-PROGRAM` with `CARDDEMO-COMMAREA`.

**WHEN CDEMO-PGM-ENTER (First entry — context=0):**

12. Perform `1000-SEND-MAP` to display the empty form with prompt message.
13. Go to `COMMON-RETURN` (RETURN TRANSID).

**WHEN CDEMO-PGM-REENTER (Re-entry — context=1):**

14. Perform `2000-PROCESS-INPUTS`:
    - `2100-RECEIVE-MAP`: EXEC CICS RECEIVE MAP to get user input into `CACTVWAI`.
    - `2200-EDIT-MAP-INPUTS`: Validate the account number:
      - Copy `ACCTSIDI` to `CC-ACCT-ID`.
      - `2210-EDIT-ACCOUNT`:
        - **Blank/spaces:** Set `INPUT-ERROR`, message "Account number not provided".
        - **Not numeric or all zeros:** Set `INPUT-ERROR`, message "Account Filter must be a non-zero 11 digit number".
        - **Valid:** Move to `CDEMO-ACCT-ID`, set `FLG-ACCTFILTER-ISVALID`.
15. **If INPUT-ERROR:** Perform `1000-SEND-MAP` with error message, go to `COMMON-RETURN`.
16. **If valid:** Perform `9000-READ-ACCT`:
    - `9200-GETCARDXREF-BYACCT`: READ CXACAIX by account ID.
      - NORMAL: Extract `XREF-CUST-ID` → `CDEMO-CUST-ID`, `XREF-CARD-NUM` → `CDEMO-CARD-NUM`.
      - NOTFND: Error — "Account not found in Cross ref file".
      - OTHER: Error — generic file error.
    - If xref failed, exit.
    - `9300-GETACCTDATA-BYACCT`: READ ACCTDAT by account ID.
      - NORMAL: Set `FOUND-ACCT-IN-MASTER`.
      - NOTFND: Error — "Account not found in Acct Master file".
      - OTHER: Error — generic file error.
    - If account read failed, exit.
    - `9400-GETCUSTDATA-BYCUST`: READ CUSTDAT by customer ID.
      - NORMAL: Set `FOUND-CUST-IN-MASTER`.
      - NOTFND: Error — "CustId not found in customer master".
      - OTHER: Error — generic file error.
17. Perform `1000-SEND-MAP` to display results, go to `COMMON-RETURN`.

**WHEN OTHER (unexpected):**

18. Set abend code '0001', send plain text error, and return.

#### 5. Screen Display (1000-SEND-MAP)

19. `1100-SCREEN-INIT`: Initialize map output area (`CACTVWAO`), set title lines, transaction/program names, current date/time.
20. `1200-SETUP-SCREEN-VARS`: If data was found, populate all account and customer fields into `CACTVWAO` output structure. SSN is formatted as XXX-XX-XXXX using STRING.
21. `1300-SETUP-SCREEN-ATTRS`: Set field attributes:
    - `ACCTSID` gets FSET (field set) attribute.
    - Cursor positioned to account number field.
    - Color coding: default color normally, red if validation failed.
    - If blank input on re-entry: display `'*'` in red.
    - Info message: dark if empty, neutral if has content.
22. `1400-SEND-SCREEN`: Set `CDEMO-PGM-REENTER` to TRUE, EXEC CICS SEND MAP.

#### 6. Return (COMMON-RETURN)

23. Copy error message to `CCARD-ERROR-MSG`.
24. Pack `CARDDEMO-COMMAREA` + `WS-THIS-PROGCOMMAREA` into `WS-COMMAREA`.
25. **EXEC CICS RETURN TRANSID('CAVW') COMMAREA(WS-COMMAREA)** — suspend and wait for next user input.

#### 7. Error/Abend Handling

- **SEND-PLAIN-TEXT:** Sends `WS-RETURN-MSG` as plain text and returns (no TRANSID — exits transaction).
- **SEND-LONG-TEXT:** Sends `WS-LONG-MSG` as plain text and returns (debugging only).
- **ABEND-ROUTINE:** Sends `ABEND-DATA` to terminal, cancels abend handling, then issues ABEND with code '9999'.

### Validation Rules

| Rule                          | Error Message                                                  |
|-------------------------------|----------------------------------------------------------------|
| Account number blank/spaces   | "Account number not provided"                                  |
| Account number not numeric    | "Account Filter must be a non-zero 11 digit number"            |
| Account number all zeros      | "Account Filter must be a non-zero 11 digit number"            |
| Account not in CXACAIX        | "Account:{id} not found in Cross ref file. Resp:... Reas:..."  |
| Account not in ACCTDAT        | "Account:{id} not found in Acct Master file. Resp:... Reas:..."|
| Customer not in CUSTDAT       | "CustId:{id} not found in customer master. Resp:... Reas:..."  |
| File I/O error (any file)     | "File Error: READ on {file} returned RESP {n},RESP2 {n}"      |

---

## Section 8: Complete File Inventory

### COBOL Source Files

| # | File                  | Path                          | Role                     |
|---|-----------------------|-------------------------------|--------------------------|
| 1 | COACTVWC.cbl          | `app/cbl/COACTVWC.cbl`       | Target program           |
| 2 | COMEN01C.cbl          | `app/cbl/COMEN01C.cbl`       | Upstream: Main Menu      |
| 3 | COSGN00C.cbl          | `app/cbl/COSGN00C.cbl`       | Upstream: Sign-on        |

### Copybook Files

| # | File                  | Path                          | Classification           |
|---|-----------------------|-------------------------------|--------------------------|
| 1 | CVCRD01Y.cpy          | `app/cpy/CVCRD01Y.cpy`       | Work area                |
| 2 | COCOM01Y.cpy          | `app/cpy/COCOM01Y.cpy`       | COMMAREA definition      |
| 3 | DFHBMSCA              | *(system copybook)*           | BMS attribute constants  |
| 4 | DFHAID                | *(system copybook)*           | AID key constants        |
| 5 | COTTL01Y.cpy          | `app/cpy/COTTL01Y.cpy`       | Screen titles            |
| 6 | COACTVW               | *(BMS-generated symbolic map)*| Symbolic map (from COACTVW.bms) |
| 7 | CSDAT01Y.cpy          | `app/cpy/CSDAT01Y.cpy`       | Date/time structure      |
| 8 | CSMSG01Y.cpy          | `app/cpy/CSMSG01Y.cpy`       | Common messages          |
| 9 | CSMSG02Y.cpy          | `app/cpy/CSMSG02Y.cpy`       | Abend variables          |
|10 | CSUSR01Y.cpy          | `app/cpy/CSUSR01Y.cpy`       | User security data       |
|11 | CVACT01Y.cpy          | `app/cpy/CVACT01Y.cpy`       | Account record layout    |
|12 | CVACT02Y.cpy          | `app/cpy/CVACT02Y.cpy`       | Card record layout       |
|13 | CVACT03Y.cpy          | `app/cpy/CVACT03Y.cpy`       | Card xref record layout  |
|14 | CVCUS01Y.cpy          | `app/cpy/CVCUS01Y.cpy`       | Customer record layout   |
|15 | CSSTRPFY.cpy          | `app/cpy/CSSTRPFY.cpy`       | PF key mapping code      |

### BMS Map Files

| # | File                  | Path                          | Mapset / Map             |
|---|-----------------------|-------------------------------|--------------------------|
| 1 | COACTVW.bms           | `app/bms/COACTVW.bms`        | COACTVW / CACTVWA        |

### VSAM Data Files (CSD Definitions)

| # | DD Name   | DSN                                           | Key / AIX            |
|---|-----------|-----------------------------------------------|----------------------|
| 1 | CXACAIX   | AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH       | AIX by Account ID    |
| 2 | ACCTDAT   | AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS           | Primary: Account ID  |
| 3 | CUSTDAT   | AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS           | Primary: Customer ID |

---

## Section 9: Transaction Record Layout

COACTVWC is a **read-only** program. It does not WRITE, REWRITE, or DELETE any records. No transaction records are modified.

The records **read** are:

- **CARD-XREF-RECORD** (CVACT03Y) — 50 bytes via CXACAIX alternate index
- **ACCOUNT-RECORD** (CVACT01Y) — 300 bytes from ACCTDAT
- **CUSTOMER-RECORD** (CVCUS01Y) — 500 bytes from CUSTDAT

See Section 3 for detailed field-level layouts.

---

## Section 10: Observations

### What the Flow Demonstrates Well

1. **Clean pseudo-conversational pattern:** Clear separation between first entry (context=0) and re-entry (context=1) using `CDEMO-PGM-CONTEXT`.
2. **Structured COMMAREA navigation:** Uses `FROM-PROGRAM`/`FROM-TRANID` to track the caller and enable proper PF3 back-navigation.
3. **Layered data access:** Follows XREF → Account → Customer chain, with each step gated on success of the previous.
4. **Comprehensive error handling:** Every EXEC CICS READ checks RESP for NORMAL, NOTFND, and OTHER, with descriptive error messages including RESP/RESP2 codes.
5. **Single input field:** Simple, focused UI — only the account number is input; everything else is display-only.
6. **Shared copybook architecture:** Reuses common copybooks (CVCRD01Y, CSSTRPFY, COTTL01Y, CSDAT01Y) shared across all CardDemo programs.

### Gaps / Migration Considerations

1. **No pagination or list display:** Only displays one account at a time. Modern UIs typically expect search-with-results-list patterns.
2. **No field-level security:** All account and customer fields are displayed to any user who reaches this screen. SSN is displayed in full (formatted but unmasked).
3. **Hardcoded error messages:** Validation messages are embedded as 88-level literals. Modern apps typically externalize message catalogs.
4. **CVACT02Y included but unused:** The CARD-RECORD copybook is included in working storage but no CARDDAT file I/O occurs. This is dead code that could be removed.
5. **Alternate index dependency:** CXACAIX is an alternate index path over CCXREF. Migration targets need to replicate this secondary index capability (e.g., composite key in SQL, secondary index in NoSQL).
6. **BMS symbolic map is auto-generated:** The `COPY COACTVW.` in the source references a BMS-generated copybook that defines `CACTVWAI`/`CACTVWAO` structures. Migration requires mapping these to UI component models.
7. **Fixed-width fields and EBCDIC:** All PIC clauses assume fixed-width EBCDIC data. Migration to variable-length Unicode strings requires careful field mapping.
8. **No audit trail:** Read operations are not logged. If audit logging is needed in the migration target, it must be added.

---

## Section 11: Complexity Comparison

| Dimension                  | COACTVWC (Account View)                     | Typical Migration Complexity |
|----------------------------|---------------------------------------------|------------------------------|
| Programs in flow           | 3 (sign-on, menu, target)                   | Low                          |
| VSAM files accessed        | 3 (CXACAIX, ACCTDAT, CUSTDAT)              | Low-Medium                   |
| I/O operations             | 3 READs (all read-only)                     | Low                          |
| BMS maps                   | 1 map, 37 fields                            | Medium                       |
| Copybooks                  | 15 (including 2 system, 1 BMS-generated)    | Medium                       |
| COMMAREA complexity        | Standard navigation + account/customer info | Low                          |
| Validation rules           | 3 input checks + 3 file-not-found checks    | Low                          |
| Business logic complexity  | Read-only lookup chain, no writes            | Low                          |
| CALL/LINK sub-programs     | 0                                           | Low                          |
| Error handling paths       | 6 distinct error conditions                 | Low-Medium                   |
| **Overall complexity**     | **Low**                                     |                              |

This is one of the simpler transactions in the CardDemo application — a straightforward read-only data viewer with a single input field and a three-file lookup chain. It serves as a good starting point for migration proof-of-concept work.

---

## Appendix: Complete EXEC CICS Command Inventory

| # | Command         | Location (Paragraph)          | Parameters                                               | RESP Handling      |
|---|-----------------|-------------------------------|----------------------------------------------------------|--------------------|
| 1 | HANDLE ABEND    | 0000-MAIN                     | LABEL(ABEND-ROUTINE)                                     | N/A                |
| 2 | XCTL            | 0000-MAIN (PF3 branch)        | PROGRAM(CDEMO-TO-PROGRAM), COMMAREA(CARDDEMO-COMMAREA)   | None               |
| 3 | RETURN          | COMMON-RETURN                 | TRANSID(LIT-THISTRANID), COMMAREA(WS-COMMAREA), LENGTH   | None               |
| 4 | SEND MAP        | 1400-SEND-SCREEN              | MAP(CCARD-NEXT-MAP), MAPSET(CCARD-NEXT-MAPSET), FROM(CACTVWAO), CURSOR, ERASE, FREEKB | RESP(WS-RESP-CD) |
| 5 | RECEIVE MAP     | 2100-RECEIVE-MAP              | MAP(LIT-THISMAP), MAPSET(LIT-THISMAPSET), INTO(CACTVWAI) | RESP, RESP2        |
| 6 | READ            | 9200-GETCARDXREF-BYACCT       | DATASET(CXACAIX), RIDFLD, KEYLENGTH, INTO(CARD-XREF-RECORD) | RESP, RESP2     |
| 7 | READ            | 9300-GETACCTDATA-BYACCT       | DATASET(ACCTDAT), RIDFLD, KEYLENGTH, INTO(ACCOUNT-RECORD)| RESP, RESP2        |
| 8 | READ            | 9400-GETCUSTDATA-BYCUST       | DATASET(CUSTDAT), RIDFLD, KEYLENGTH, INTO(CUSTOMER-RECORD)| RESP, RESP2       |
| 9 | SEND TEXT       | SEND-PLAIN-TEXT               | FROM(WS-RETURN-MSG), LENGTH, ERASE, FREEKB              | None               |
|10 | RETURN          | SEND-PLAIN-TEXT               | *(no TRANSID — terminal return)*                         | None               |
|11 | SEND TEXT       | SEND-LONG-TEXT                | FROM(WS-LONG-MSG), LENGTH, ERASE, FREEKB                | None               |
|12 | RETURN          | SEND-LONG-TEXT                | *(no TRANSID — terminal return)*                         | None               |
|13 | SEND            | ABEND-ROUTINE                 | FROM(ABEND-DATA), LENGTH, NOHANDLE                       | NOHANDLE           |
|14 | HANDLE ABEND    | ABEND-ROUTINE                 | CANCEL                                                   | N/A                |
|15 | ABEND           | ABEND-ROUTINE                 | ABCODE('9999')                                           | N/A                |

# COTRN02C (CT02) — Transaction Add: CICS Transaction Flow Analysis

## Section 1: Flow Overview

| Attribute           | Value                                              |
|---------------------|----------------------------------------------------|
| **Transaction ID**  | CT02                                               |
| **Entry Program**   | COTRN02C                                           |
| **Function**        | Add a new transaction to the TRANSACT VSAM file    |
| **Application**     | CardDemo — AWS Mainframe Modernization Demo        |
| **Data Access**     | VSAM (no DB2 or IMS)                               |

### Navigation Path

```
  COSGN00C (CC00)          COMEN01C (CM00)           COTRN02C (CT02)
  ┌──────────────┐        ┌──────────────┐          ┌──────────────┐
  │  Sign-On     │─XCTL──>│  Main Menu   │──XCTL──> │ Transaction  │
  │  Screen      │        │ (Regular Usr)│  Opt 8   │    Add       │
  └──────────────┘        └──────────────┘          └──────────────┘
        ^                       ^                     │
        │                       │                     │
        └───────────────────────┴─────────XCTL────────┘
                          (PF3 / Back)
```

- **COSGN00C** authenticates the user against USRSEC. On success for regular users, it XCTLs to **COMEN01C**.
- **COMEN01C** displays menu options from `COMEN02Y.cpy`. Option **8** ("Transaction Add") resolves to program **COTRN02C** via table lookup. COMEN01C sets `CDEMO-FROM-TRANID`, `CDEMO-FROM-PROGRAM`, resets `CDEMO-PGM-CONTEXT` to 0, and XCTLs with the COMMAREA.
- **COTRN02C** operates pseudo-conversationally under transaction ID **CT02**. It validates input, looks up account/card cross-references, and writes a new transaction record. PF3 returns to the calling program (COMEN01C) via XCTL.

### Pseudo-Conversational Lifecycle

```
  ┌─────────────────────────────────────────────────────────────┐
  │                   CT02 Lifecycle                            │
  │                                                             │
  │  1. First Entry (PGM-CONTEXT = 0)                          │
  │     ├─ Set PGM-CONTEXT = 1 (reenter mode)                  │
  │     ├─ Initialize screen to LOW-VALUES                     │
  │     ├─ If TRN-SELECTED set: pre-populate card number       │
  │     │   and PERFORM PROCESS-ENTER-KEY                      │
  │     ├─ SEND MAP COTRN2A                                    │
  │     └─ RETURN TRANSID('CT02') COMMAREA                     │
  │                                                             │
  │  2. Re-Entry (PGM-CONTEXT = 1)                             │
  │     ├─ RECEIVE MAP COTRN2A                                 │
  │     ├─ EVALUATE EIBAID:                                    │
  │     │   ├─ ENTER → Validate + Confirm + Add Transaction    │
  │     │   ├─ PF3   → XCTL back to caller                    │
  │     │   ├─ PF4   → Clear all fields, re-send screen       │
  │     │   ├─ PF5   → Copy last transaction data, re-enter   │
  │     │   └─ OTHER → Error message, re-send screen          │
  │     └─ RETURN TRANSID('CT02') COMMAREA                     │
  └─────────────────────────────────────────────────────────────┘
```

---

## Section 2: Programs Involved

| Program    | Type             | Function                              | Called By  | Call Method       |
|------------|------------------|---------------------------------------|------------|-------------------|
| COSGN00C   | CICS Online      | Sign-on / Authentication              | (initial)  | CICS TRANSID CC00 |
| COMEN01C   | CICS Online      | Main Menu (regular users)             | COSGN00C   | XCTL              |
| COTRN02C   | CICS Online      | Transaction Add                       | COMEN01C   | XCTL (option 8)   |
| CSUTLDTC   | Called Sub-pgm   | Date validation (calls CEEDAYS)       | COTRN02C   | CALL              |
| CEEDAYS    | LE Intrinsic     | IBM Language Environment date routine | CSUTLDTC   | CALL              |

### CSUTLDTC — Date Validation Utility

- **Source**: `app/cbl/CSUTLDTC.cbl`
- **LINKAGE SECTION**: `LS-DATE PIC X(10)`, `LS-DATE-FORMAT PIC X(10)`, `LS-RESULT PIC X(80)`
- **Function**: Accepts a date string and format mask, calls IBM LE intrinsic `CEEDAYS` to validate. Returns a severity code (`0000` = valid) and message number in `LS-RESULT`.
- **Called twice** by COTRN02C: once for Origination Date, once for Processing Date.
- **CEEDAYS** is an IBM Language Environment intrinsic — no source in the repository.

---

## Section 3: Copybooks

| Copybook   | Used By              | Type                | Purpose                                              |
|------------|----------------------|---------------------|------------------------------------------------------|
| COCOM01Y   | COTRN02C, COMEN01C, COSGN00C | COMMAREA definition | Navigation fields, user info, customer/account/card  |
| COTRN02    | COTRN02C             | BMS Symbolic Map    | Auto-generated input/output map structures (COTRN2AI/COTRN2AO) |
| COTTL01Y   | COTRN02C, COMEN01C, COSGN00C | Screen titles       | CCDA-TITLE01, CCDA-TITLE02, CCDA-THANK-YOU           |
| CSDAT01Y   | COTRN02C, COMEN01C, COSGN00C | Date/time structure | WS-CURDATE-DATA, formatting fields                   |
| CSMSG01Y   | COTRN02C, COMEN01C, COSGN00C | Common messages     | CCDA-MSG-THANK-YOU, CCDA-MSG-INVALID-KEY             |
| CVTRA05Y   | COTRN02C             | Record layout       | TRAN-RECORD (350 bytes) — Transaction VSAM record    |
| CVACT01Y   | COTRN02C             | Record layout       | ACCOUNT-RECORD (300 bytes) — Account VSAM record     |
| CVACT03Y   | COTRN02C             | Record layout       | CARD-XREF-RECORD (50 bytes) — Card/account xref      |
| COMEN02Y   | COMEN01C             | Menu options table  | Maps option numbers to program names (option 8 → COTRN02C) |
| CSUSR01Y   | COSGN00C             | Record layout       | SEC-USER-DATA (80 bytes) — User security record      |
| DFHAID     | COTRN02C, COMEN01C, COSGN00C | **System copybook** | CICS AID key constants (DFHENTER, DFHPF3, etc.)     |
| DFHBMSCA   | COTRN02C, COMEN01C, COSGN00C | **System copybook** | BMS attribute constants (DFHGREEN, DFHRED, etc.)     |

> **Note**: DFHAID and DFHBMSCA are IBM CICS system-supplied copybooks. They are not present in `app/cpy/` and should not be expected there.

> **Note**: COTRN02 (the BMS symbolic map copybook) is auto-generated from `app/bms/COTRN02.bms` and exists in `app/cpy/` as a `.cpy` file used via `COPY COTRN02` in the program source.

### Key Record Layouts

#### CVTRA05Y — TRAN-RECORD (350 bytes)

| Level | Field                | PIC              | Bytes | Purpose                    |
|-------|----------------------|------------------|-------|----------------------------|
| 01    | TRAN-RECORD          |                  | 350   | Transaction record         |
| 05    | TRAN-ID              | X(16)            | 16    | Transaction ID (key)       |
| 05    | TRAN-TYPE-CD         | X(02)            | 2     | Transaction type code      |
| 05    | TRAN-CAT-CD          | 9(04)            | 4     | Transaction category code  |
| 05    | TRAN-SOURCE          | X(10)            | 10    | Transaction source         |
| 05    | TRAN-DESC            | X(100)           | 100   | Transaction description    |
| 05    | TRAN-AMT             | S9(09)V99        | 11    | Transaction amount         |
| 05    | TRAN-MERCHANT-ID     | 9(09)            | 9     | Merchant ID                |
| 05    | TRAN-MERCHANT-NAME   | X(50)            | 50    | Merchant name              |
| 05    | TRAN-MERCHANT-CITY   | X(50)            | 50    | Merchant city              |
| 05    | TRAN-MERCHANT-ZIP    | X(10)            | 10    | Merchant ZIP code          |
| 05    | TRAN-CARD-NUM        | X(16)            | 16    | Card number                |
| 05    | TRAN-ORIG-TS         | X(26)            | 26    | Origination timestamp      |
| 05    | TRAN-PROC-TS         | X(26)            | 26    | Processing timestamp       |
| 05    | FILLER               | X(20)            | 20    | Reserved                   |

#### CVACT03Y — CARD-XREF-RECORD (50 bytes)

| Level | Field            | PIC     | Bytes | Purpose                  |
|-------|------------------|---------|-------|--------------------------|
| 01    | CARD-XREF-RECORD |         | 50    | Card/Account xref record |
| 05    | XREF-CARD-NUM    | X(16)   | 16    | Card number (primary key)|
| 05    | XREF-CUST-ID     | 9(09)   | 9     | Customer ID              |
| 05    | XREF-ACCT-ID     | 9(11)   | 11    | Account ID               |
| 05    | FILLER           | X(14)   | 14    | Reserved                 |

#### CVACT01Y — ACCOUNT-RECORD (300 bytes)

| Level | Field                  | PIC           | Bytes | Purpose                |
|-------|------------------------|---------------|-------|------------------------|
| 01    | ACCOUNT-RECORD         |               | 300   | Account master record  |
| 05    | ACCT-ID                | 9(11)         | 11    | Account ID (key)       |
| 05    | ACCT-ACTIVE-STATUS     | X(01)         | 1     | Active status flag     |
| 05    | ACCT-CURR-BAL          | S9(10)V99     | 12    | Current balance        |
| 05    | ACCT-CREDIT-LIMIT      | S9(10)V99     | 12    | Credit limit           |
| 05    | ACCT-CASH-CREDIT-LIMIT | S9(10)V99     | 12    | Cash credit limit      |
| 05    | ACCT-OPEN-DATE         | X(10)         | 10    | Account open date      |
| 05    | ACCT-EXPIRAION-DATE    | X(10)         | 10    | Expiration date        |
| 05    | ACCT-REISSUE-DATE      | X(10)         | 10    | Reissue date           |
| 05    | ACCT-CURR-CYC-CREDIT   | S9(10)V99     | 12    | Current cycle credits  |
| 05    | ACCT-CURR-CYC-DEBIT    | S9(10)V99     | 12    | Current cycle debits   |
| 05    | ACCT-ADDR-ZIP          | X(10)         | 10    | Address ZIP code       |
| 05    | ACCT-GROUP-ID          | X(10)         | 10    | Group ID               |
| 05    | FILLER                 | X(178)        | 178   | Reserved               |

---

## Section 4: VSAM File Operations

| VSAM File  | DD Name   | Record Copybook | Key Field      | Operations                            |
|------------|-----------|-----------------|----------------|---------------------------------------|
| TRANSACT   | TRANSACT  | CVTRA05Y        | TRAN-ID X(16)  | STARTBR, READPREV, ENDBR, WRITE      |
| CCXREF     | CCXREF    | CVACT03Y        | XREF-CARD-NUM X(16) | READ (by card number)           |
| CXACAIX    | CXACAIX   | CVACT03Y        | XREF-ACCT-ID 9(11)  | READ (by account ID via AIX)   |

> **CXACAIX** is an alternate index path over the CCXREF base cluster. It allows lookup of the card cross-reference record using the account ID as the key instead of the card number. Both CCXREF and CXACAIX access the same underlying VSAM data.

### Detailed VSAM Operations

#### TRANSACT File — Transaction Records

| # | Command   | Paragraph                | Key Value         | Purpose                                                 |
|---|-----------|--------------------------|-------------------|---------------------------------------------------------|
| 1 | STARTBR   | STARTBR-TRANSACT-FILE    | HIGH-VALUES       | Position at end of file for reverse browse              |
| 2 | READPREV  | READPREV-TRANSACT-FILE   | TRAN-ID           | Read the last (highest key) transaction record          |
| 3 | ENDBR     | ENDBR-TRANSACT-FILE      | —                 | End the browse session                                  |
| 4 | WRITE     | WRITE-TRANSACT-FILE      | New TRAN-ID       | Write the new transaction record                        |

**ID Generation**: The program browses backwards from HIGH-VALUES to find the current highest TRAN-ID, adds 1 to generate the next sequential ID, then writes the new record.

#### CCXREF File — Card Cross-Reference (by Card Number)

| # | Command | Paragraph          | Key Value       | Purpose                                |
|---|---------|--------------------|-----------------|-----------------------------------------|
| 1 | READ    | READ-CCXREF-FILE   | XREF-CARD-NUM  | Look up account ID from card number     |

#### CXACAIX File — Card Cross-Reference AIX (by Account ID)

| # | Command | Paragraph           | Key Value      | Purpose                                |
|---|---------|---------------------|----------------|-----------------------------------------|
| 1 | READ    | READ-CXACAIX-FILE   | XREF-ACCT-ID  | Look up card number from account ID     |

### VSAM File Access Flow

```
  User enters Account ID or Card Number
           │
           ├── Account ID provided?
           │   └── READ CXACAIX (acct→card lookup)
           │       ├── NORMAL → populate Card Number
           │       ├── NOTFND → "Account ID NOT found"
           │       └── OTHER  → "Unable to lookup Acct in XREF AIX file"
           │
           └── Card Number provided?
               └── READ CCXREF (card→acct lookup)
                   ├── NORMAL → populate Account ID
                   ├── NOTFND → "Card Number NOT found"
                   └── OTHER  → "Unable to lookup Card # in XREF file"

  After validation passes + user confirms (Y):
           │
           ├── STARTBR TRANSACT (at HIGH-VALUES)
           │     └── NOTFND / OTHER → error
           ├── READPREV TRANSACT (get last record)
           │     ├── NORMAL → capture TRAN-ID
           │     └── ENDFILE → set TRAN-ID = 0
           ├── ENDBR TRANSACT
           ├── New TRAN-ID = last TRAN-ID + 1
           ├── Build TRAN-RECORD from screen fields
           └── WRITE TRANSACT
                 ├── NORMAL → "Transaction added successfully"
                 ├── DUPKEY/DUPREC → "Tran ID already exist"
                 └── OTHER → "Unable to Add Transaction"
```

---

## Section 5: BMS Screen Map

### Map Details

| Attribute   | Value               |
|-------------|---------------------|
| Mapset      | COTRN02             |
| Map         | COTRN2A             |
| Source      | `app/bms/COTRN02.bms` |
| Size        | 24 rows x 80 cols   |
| Mode        | INOUT               |
| Language    | COBOL               |

### ASCII Screen Layout

```
Row  1: Tran: XXXX       AWS Mainframe Modernization            Date: mm/dd/yy
Row  2: Prog: XXXXXXXX              CardDemo                    Time: hh:mm:ss
Row  3:
Row  4:                              Add Transaction
Row  5:
Row  6:      Enter Acct #: [___________]    (or)  Card #: [________________]
Row  7:
Row  8:      ----------------------------------------------------------------------
Row  9:
Row 10:      Type CD: [__] Category CD: [____] Source: [__________]
Row 11:
Row 12:      Description: [____________________________________________________________]
Row 13:
Row 14:      Amount: [____________] Orig Date: [__________] Proc Date: [__________]
Row 15:               (-99999999.99)            (YYYY-MM-DD)            (YYYY-MM-DD)
Row 16:      Merchant ID: [_________] Merchant Name: [______________________________]
Row 17:
Row 18:      Merchant City: [_________________________] Merchant Zip: [__________]
Row 19:
Row 20:
Row 21:      You are about to add this transaction. Please confirm : [_] (Y/N)
Row 22:
Row 23: XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
Row 24: ENTER=Continue  F3=Back  F4=Clear  F5=Copy Last Tran.
```

### Field Inventory

| Field Name | Row | Col | Length | Attribute     | Color     | Purpose                   |
|------------|-----|-----|--------|---------------|-----------|---------------------------|
| TRNNAME    | 1   | 7   | 4      | ASKIP,FSET    | Blue      | Transaction ID display     |
| TITLE01    | 1   | 21  | 40     | ASKIP,FSET    | Yellow    | Application title line 1   |
| CURDATE    | 1   | 71  | 8      | ASKIP,FSET    | Blue      | Current date (mm/dd/yy)    |
| PGMNAME    | 2   | 7   | 8      | ASKIP,FSET    | Blue      | Program name display       |
| TITLE02    | 2   | 21  | 40     | ASKIP,FSET    | Yellow    | Application title line 2   |
| CURTIME    | 2   | 71  | 8      | ASKIP,FSET    | Blue      | Current time (hh:mm:ss)    |
| ACTIDIN    | 6   | 21  | 11     | UNPROT,FSET,IC| Green     | Account ID input           |
| CARDNIN    | 6   | 55  | 16     | UNPROT,FSET   | Green     | Card number input          |
| TTYPCD     | 10  | 15  | 2      | UNPROT,FSET   | Green     | Transaction type code      |
| TCATCD     | 10  | 36  | 4      | UNPROT,FSET   | Green     | Transaction category code  |
| TRNSRC     | 10  | 54  | 10     | UNPROT,FSET   | Green     | Transaction source         |
| TDESC      | 12  | 19  | 60     | UNPROT,FSET   | Green     | Transaction description    |
| TRNAMT     | 14  | 14  | 12     | UNPROT,FSET   | Green     | Transaction amount         |
| TORIGDT    | 14  | 42  | 10     | UNPROT,FSET   | Green     | Origination date           |
| TPROCDT    | 14  | 68  | 10     | UNPROT,FSET   | Green     | Processing date            |
| MID        | 16  | 19  | 9      | UNPROT,FSET   | Green     | Merchant ID                |
| MNAME      | 16  | 48  | 30     | UNPROT,FSET   | Green     | Merchant name              |
| MCITY      | 18  | 21  | 25     | UNPROT,FSET   | Green     | Merchant city              |
| MZIP       | 18  | 67  | 10     | UNPROT,FSET   | Green     | Merchant ZIP code          |
| CONFIRM    | 21  | 63  | 1      | UNPROT,FSET   | Green     | Confirmation (Y/N)         |
| ERRMSG     | 23  | 1   | 78     | ASKIP,BRT,FSET| Red       | Error/success message      |

### Function Key Assignments

| Key   | Action                                                    |
|-------|-----------------------------------------------------------|
| ENTER | Validate input fields, prompt for / process confirmation  |
| PF3   | Return to previous screen (COMEN01C or CDEMO-FROM-PROGRAM)|
| PF4   | Clear all input fields on the current screen               |
| PF5   | Copy data from the last transaction in TRANSACT file       |
| Other | Display "Invalid key pressed" error message                |

---

## Section 6: COMMAREA Structure

### COCOM01Y — CARDDEMO-COMMAREA

```
01 CARDDEMO-COMMAREA
   05 CDEMO-GENERAL-INFO
      10 CDEMO-FROM-TRANID          PIC X(04)      ← Set by caller
      10 CDEMO-FROM-PROGRAM         PIC X(08)      ← Set by caller
      10 CDEMO-TO-TRANID            PIC X(04)
      10 CDEMO-TO-PROGRAM           PIC X(08)      ← Set for XCTL target
      10 CDEMO-USER-ID              PIC X(08)      ← Set at sign-on
      10 CDEMO-USER-TYPE            PIC X(01)      ← 'A'=Admin, 'U'=User
         88 CDEMO-USRTYP-ADMIN      VALUE 'A'
         88 CDEMO-USRTYP-USER       VALUE 'U'
      10 CDEMO-PGM-CONTEXT          PIC 9(01)      ← 0=Enter, 1=Reenter
         88 CDEMO-PGM-ENTER         VALUE 0
         88 CDEMO-PGM-REENTER       VALUE 1
   05 CDEMO-CUSTOMER-INFO
      10 CDEMO-CUST-ID              PIC 9(09)
      10 CDEMO-CUST-FNAME           PIC X(25)
      10 CDEMO-CUST-MNAME           PIC X(25)
      10 CDEMO-CUST-LNAME           PIC X(25)
   05 CDEMO-ACCOUNT-INFO
      10 CDEMO-ACCT-ID              PIC 9(11)
      10 CDEMO-ACCT-STATUS          PIC X(01)
   05 CDEMO-CARD-INFO
      10 CDEMO-CARD-NUM             PIC 9(16)
   05 CDEMO-MORE-INFO
      10 CDEMO-LAST-MAP             PIC X(7)
      10 CDEMO-LAST-MAPSET          PIC X(7)
```

### Program-Specific Extension — CDEMO-CT02-INFO

Defined inline in COTRN02C.cbl immediately after the `COPY COCOM01Y` statement:

```
   05 CDEMO-CT02-INFO
      10 CDEMO-CT02-TRNID-FIRST     PIC X(16)      ← First tran ID on page
      10 CDEMO-CT02-TRNID-LAST      PIC X(16)      ← Last tran ID on page
      10 CDEMO-CT02-PAGE-NUM        PIC 9(08)      ← Current page number
      10 CDEMO-CT02-NEXT-PAGE-FLG   PIC X(01)      ← 'Y'=more pages
         88 NEXT-PAGE-YES           VALUE 'Y'
         88 NEXT-PAGE-NO            VALUE 'N'
      10 CDEMO-CT02-TRN-SEL-FLG    PIC X(01)      ← Selection flag
      10 CDEMO-CT02-TRN-SELECTED   PIC X(16)      ← Selected tran ID
```

### COMMAREA Inter-Program Flow

```
  COSGN00C sets:                    COMEN01C sets:               COTRN02C uses:
  ┌────────────────────┐           ┌────────────────────┐       ┌────────────────────┐
  │ CDEMO-FROM-TRANID  │──CC00──> │ CDEMO-FROM-TRANID  │─CM00─>│ CDEMO-FROM-TRANID  │
  │ CDEMO-FROM-PROGRAM │─COSGN00C>│ CDEMO-FROM-PROGRAM │─COMEN>│ CDEMO-FROM-PROGRAM │ R
  │ CDEMO-USER-ID      │──(uid)──>│                    │──────>│ CDEMO-USER-ID      │ R
  │ CDEMO-USER-TYPE    │──(type)─>│                    │──────>│ CDEMO-USER-TYPE    │ R
  │ CDEMO-PGM-CONTEXT  │────0───> │ CDEMO-PGM-CONTEXT  │───0──>│ CDEMO-PGM-CONTEXT  │ R/W
  └────────────────────┘           └────────────────────┘       └────────────────────┘

  COTRN02C writes back:
  ┌─────────────────────────┐
  │ CDEMO-FROM-TRANID  = CT02          (on XCTL back)          │
  │ CDEMO-FROM-PROGRAM = COTRN02C     (on XCTL back)          │
  │ CDEMO-TO-PROGRAM   = caller       (for XCTL navigation)   │
  │ CDEMO-PGM-CONTEXT  = 0            (reset on XCTL back)    │
  │ CDEMO-PGM-CONTEXT  = 1            (on RETURN TRANSID)     │
  └─────────────────────────┘

  R = Read only    R/W = Read and Write
```

---

## Section 7: Business Logic Flow

### Step-by-Step Narrative

#### 1. Entry and Initialization

1. Program receives control from CICS with COMMAREA.
2. If `EIBCALEN = 0` (no COMMAREA — direct invocation), redirect to sign-on screen (COSGN00C) via XCTL.
3. Move DFHCOMMAREA to `CARDDEMO-COMMAREA`.
4. If first entry (`CDEMO-PGM-CONTEXT = 0`):
   - Set `CDEMO-PGM-REENTER = 1` for subsequent iterations.
   - Initialize screen map to LOW-VALUES.
   - Set cursor to Account ID field.
   - If `CDEMO-CT02-TRN-SELECTED` is populated (card number pre-selected from another screen), move it to CARDNIN input field and perform `PROCESS-ENTER-KEY` to pre-validate.
   - Send the Add Transaction screen.
5. If re-entry (`CDEMO-PGM-CONTEXT = 1`):
   - Receive screen input.
   - Evaluate the AID key pressed.

#### 2. Input Validation — Key Fields (VALIDATE-INPUT-KEY-FIELDS)

The program accepts **either** an Account ID or a Card Number (not both required):

1. **If Account ID entered**:
   - Validate numeric.
   - Convert to numeric using `FUNCTION NUMVAL`.
   - READ CXACAIX (alternate index) to look up the card number from the account ID.
   - On NORMAL: populate Card Number field from XREF-CARD-NUM.
   - On NOTFND: error "Account ID NOT found".
2. **If Card Number entered**:
   - Validate numeric.
   - Convert to numeric using `FUNCTION NUMVAL`.
   - READ CCXREF to look up the account ID from the card number.
   - On NORMAL: populate Account ID field from XREF-ACCT-ID.
   - On NOTFND: error "Card Number NOT found".
3. **If neither entered**: error "Account or Card Number must be entered".

#### 3. Input Validation — Data Fields (VALIDATE-INPUT-DATA-FIELDS)

If key field validation failed, clear all data fields first. Then validate each field sequentially:

| Field          | Validation Rule                                    | Error Message                                |
|----------------|-----------------------------------------------------|----------------------------------------------|
| Type CD        | Not empty; must be numeric                         | "Type CD can NOT be empty" / "must be Numeric"|
| Category CD    | Not empty; must be numeric                         | "Category CD can NOT be empty" / "must be Numeric"|
| Source         | Not empty                                          | "Source can NOT be empty"                     |
| Description    | Not empty                                          | "Description can NOT be empty"                |
| Amount         | Not empty; format `-99999999.99` (sign + 8 digits + `.` + 2 digits) | "Amount can NOT be empty" / "Amount should be in format..." |
| Orig Date      | Not empty; format `YYYY-MM-DD`; valid date via CSUTLDTC | "Orig Date can NOT be empty" / "should be in format..." / "Not a valid date" |
| Proc Date      | Not empty; format `YYYY-MM-DD`; valid date via CSUTLDTC | "Proc Date can NOT be empty" / "should be in format..." / "Not a valid date" |
| Merchant ID    | Not empty; must be numeric                         | "Merchant ID can NOT be empty" / "must be Numeric" |
| Merchant Name  | Not empty                                          | "Merchant Name can NOT be empty"              |
| Merchant City  | Not empty                                          | "Merchant City can NOT be empty"              |
| Merchant Zip   | Not empty                                          | "Merchant Zip can NOT be empty"               |

#### 4. Date Validation via CSUTLDTC

For both Origination Date and Processing Date:
1. Move date value and format mask (`YYYY-MM-DD`) to CSUTLDTC parameter area.
2. `CALL 'CSUTLDTC'` which internally calls IBM LE `CEEDAYS`.
3. Check `CSUTLDTC-RESULT-SEV-CD`:
   - `0000` = valid date.
   - Other severity + `CSUTLDTC-RESULT-MSG-NUM` not `2513` = invalid date → error.

#### 5. Confirmation and Transaction Add

After all validation passes:
1. Check CONFIRM field:
   - **'Y'/'y'**: Proceed to `ADD-TRANSACTION`.
   - **'N'/'n'/spaces/LOW-VALUES**: Display "Confirm to add this transaction...".
   - **Other**: Display "Invalid value. Valid values are (Y/N)...".

2. `ADD-TRANSACTION` logic:
   - Set `TRAN-ID = HIGH-VALUES` and browse backwards to find the highest existing TRAN-ID.
   - Compute new TRAN-ID = highest + 1.
   - Initialize `TRAN-RECORD` and populate all fields from screen input.
   - Amount is converted via `FUNCTION NUMVAL-C`.
   - `WRITE` the new record to TRANSACT.
   - On success: clear all fields, display success message (green) with new TRAN-ID.
   - On DUPKEY/DUPREC: error "Tran ID already exist".
   - On other error: error "Unable to Add Transaction".

#### 6. Copy Last Transaction (PF5)

1. Validate key fields (Account ID or Card Number).
2. Browse backwards from HIGH-VALUES to read the last transaction record.
3. Populate all data fields on screen from the last transaction's values.
4. Then perform `PROCESS-ENTER-KEY` for immediate validation.

#### 7. Error Handling Summary

All VSAM I/O operations check RESP/RESP2 codes:

| Operation            | NORMAL | NOTFND/ENDFILE       | DUPKEY/DUPREC     | OTHER                           |
|----------------------|--------|----------------------|-------------------|---------------------------------|
| READ CXACAIX         | Continue| "Account ID NOT found"| —                 | "Unable to lookup Acct in XREF AIX file" |
| READ CCXREF          | Continue| "Card Number NOT found"| —                | "Unable to lookup Card # in XREF file" |
| STARTBR TRANSACT     | Continue| "Transaction ID NOT found"| —             | "Unable to lookup Transaction"  |
| READPREV TRANSACT    | Continue| Set TRAN-ID = 0       | —                | "Unable to lookup Transaction"  |
| WRITE TRANSACT       | Success | —                    | "Tran ID already exist" | "Unable to Add Transaction" |

---

## Section 8: Complete File Inventory

### COBOL Source Files

| File                    | Path                        | Role                          |
|-------------------------|-----------------------------|-------------------------------|
| COTRN02C.cbl           | `app/cbl/COTRN02C.cbl`     | Target program — Transaction Add |
| COMEN01C.cbl           | `app/cbl/COMEN01C.cbl`     | Upstream — Main Menu           |
| COSGN00C.cbl           | `app/cbl/COSGN00C.cbl`     | Upstream — Sign-on             |
| CSUTLDTC.cbl           | `app/cbl/CSUTLDTC.cbl`     | Called — Date validation       |

### Copybook Files

| File          | Path                      | Present in Repo |
|---------------|---------------------------|-----------------|
| COCOM01Y.cpy  | `app/cpy/COCOM01Y.cpy`   | Yes             |
| COTRN02       | (BMS-generated)           | No file — auto-generated from `app/bms/COTRN02.bms` at assembly time |
| COTTL01Y.cpy  | `app/cpy/COTTL01Y.cpy`   | Yes             |
| CSDAT01Y.cpy  | `app/cpy/CSDAT01Y.cpy`   | Yes             |
| CSMSG01Y.cpy  | `app/cpy/CSMSG01Y.cpy`   | Yes             |
| CVTRA05Y.cpy  | `app/cpy/CVTRA05Y.cpy`   | Yes             |
| CVACT01Y.cpy  | `app/cpy/CVACT01Y.cpy`   | Yes             |
| CVACT03Y.cpy  | `app/cpy/CVACT03Y.cpy`   | Yes             |
| COMEN02Y.cpy  | `app/cpy/COMEN02Y.cpy`   | Yes             |
| CSUSR01Y.cpy  | `app/cpy/CSUSR01Y.cpy`   | Yes             |
| DFHAID        | (system)                  | No — IBM CICS system copybook |
| DFHBMSCA      | (system)                  | No — IBM CICS system copybook |

### BMS Map Files

| File          | Path                      | Mapset  | Map     |
|---------------|---------------------------|---------|---------|
| COTRN02.bms   | `app/bms/COTRN02.bms`    | COTRN02 | COTRN2A |

### VSAM Data Files (CSD Definitions)

| DD Name   | DSN                                            | Type      | Key                |
|-----------|------------------------------------------------|-----------|--------------------|
| TRANSACT  | AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS           | VSAM KSDS | TRAN-ID X(16)      |
| CCXREF    | AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS           | VSAM KSDS | XREF-CARD-NUM X(16)|
| CXACAIX   | AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH       | VSAM AIX  | XREF-ACCT-ID 9(11) |

---

## Section 9: Transaction Record Layout

The WRITE operation in `WRITE-TRANSACT-FILE` produces a 350-byte VSAM record as defined in CVTRA05Y:

```
Offset  Length  Field                PIC            Source (Screen Field)
------  ------  -------------------  -------------  --------------------
0       16      TRAN-ID              X(16)          Generated (last ID + 1)
16      2       TRAN-TYPE-CD         X(02)          TTYPCDI  (Type CD)
18      4       TRAN-CAT-CD          9(04)          TCATCDI  (Category CD)
22      10      TRAN-SOURCE          X(10)          TRNSRCI  (Source)
32      100     TRAN-DESC            X(100)         TDESCI   (Description)
132     11      TRAN-AMT             S9(09)V99      TRNAMTI  (Amount, NUMVAL-C)
143     9       TRAN-MERCHANT-ID     9(09)          MIDI     (Merchant ID)
152     50      TRAN-MERCHANT-NAME   X(50)          MNAMEI   (Merchant Name)
202     50      TRAN-MERCHANT-CITY   X(50)          MCITYI   (Merchant City)
252     10      TRAN-MERCHANT-ZIP    X(10)          MZIPI    (Merchant Zip)
262     16      TRAN-CARD-NUM        X(16)          CARDNINI (Card Number)
278     26      TRAN-ORIG-TS         X(26)          TORIGDTI (Orig Date)
304     26      TRAN-PROC-TS         X(26)          TPROCDTI (Proc Date)
330     20      FILLER               X(20)          (reserved/unused)
------  ------
Total: 350 bytes
```

---

## Section 10: Observations

### What the Flow Demonstrates Well

1. **Clean pseudo-conversational pattern**: The `CDEMO-PGM-CONTEXT` flag (0/1) cleanly separates first-entry initialization from re-entry processing. Each iteration ends with `RETURN TRANSID` + COMMAREA.

2. **Robust input validation**: Every field is validated for both presence and format. Numeric fields get numeric checks, dates get format validation followed by semantic validation via the `CSUTLDTC`/`CEEDAYS` utility.

3. **Shared COMMAREA design**: The `COCOM01Y` copybook provides a universal header (navigation, user context) while each program appends its own extension area. This is a well-established mainframe pattern.

4. **Sequential ID generation**: The STARTBR/READPREV pattern to find the highest existing key and increment it is a standard VSAM approach for generating sequential IDs.

5. **Cross-reference lookup**: The program supports lookup by either Account ID or Card Number using the CCXREF base cluster and CXACAIX alternate index path — a common VSAM design pattern.

6. **Copy Last Transaction (PF5)**: A user-friendly feature that pre-populates the form with the last transaction's data, reducing data entry effort.

### Gaps and Migration Considerations

1. **No database transactions / commit scope**: VSAM WRITE is immediate — there is no two-phase commit. In a modernized environment, the ADD operation should be wrapped in a database transaction.

2. **Sequential ID generation is not concurrency-safe**: The STARTBR + READPREV + increment pattern has a race condition under concurrent access. A modernized system should use database sequences or UUIDs.

3. **No audit trail**: The program does not record who added the transaction (no user ID in the record), when it was added (no system timestamp), or from which terminal.

4. **Hardcoded validation**: Type codes, category codes, and source values are validated only for numeric/non-empty — there is no lookup table validation. A modernized system should validate against reference data.

5. **Amount format coupling**: The screen-level amount format (`-99999999.99`) is tightly coupled to the validation logic with character-position checks. A modernized system should use locale-aware numeric parsing.

6. **Error handling**: All error paths re-send the screen and return. There is no centralized error logging or error code framework.

---

## Section 11: Comparison — CICS vs. Modern Target Environment

| Aspect                    | CICS/COBOL (Current)                        | Modern Target (e.g., Java/Spring)             |
|---------------------------|---------------------------------------------|-----------------------------------------------|
| **UI Rendering**          | BMS maps, 3270 terminal, SEND MAP/RECEIVE MAP | HTML/React, REST API, browser               |
| **State Management**      | COMMAREA passed via RETURN TRANSID          | HTTP session or JWT token                     |
| **Navigation**            | XCTL/LINK with COMMAREA                     | URL routing, controller dispatch              |
| **Data Access**           | EXEC CICS READ/WRITE on VSAM KSDS          | JPA/JDBC on RDBMS (PostgreSQL, etc.)          |
| **ID Generation**         | STARTBR + READPREV + increment              | Database sequence / UUID                      |
| **Date Validation**       | CALL CSUTLDTC → CALL CEEDAYS               | `java.time.LocalDate.parse()` or equivalent  |
| **Input Validation**      | Character-position PIC checks in COBOL      | Bean Validation annotations (@NotNull, etc.)  |
| **Cross-Reference**       | VSAM alternate index (CXACAIX)              | Database JOIN or foreign key                  |
| **Concurrency Control**   | CICS task isolation, VSAM record locks      | Database transactions, optimistic locking     |
| **Error Handling**        | RESP/RESP2 evaluation per I/O               | Try/catch, global exception handler           |
| **Program Structure**     | Paragraphs with PERFORM                     | Service classes with methods                  |
| **Screen Fields**         | 14 input fields, 1 confirmation             | Form with equivalent fields + client validation|
| **Complexity**            | ~784 lines COBOL, 3 VSAM files, 1 sub-pgm  | Medium — standard CRUD with validation        |

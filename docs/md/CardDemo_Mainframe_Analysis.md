# CardDemo – Mainframe Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin AI / AWS CardDemo Team  
**Status**: Draft  
**Related System Documentation**: [README.md](./README.md)

---

## Table of Contents

1. System Overview and Processing Architecture
2. Program Design and Call Structure
3. Data Structures and File Layouts
4. Program Interface Specifications
5. Code Architecture and Organization
6. Transaction and Batch Interface Specifications
7. Security Implementation Details
8. File and Database Design
9. Deployment and Promotion Procedures
10. Operations and Runbooks
11. Monitoring and Observability
12. Performance and Capacity Analysis
13. Testing Strategy and Test Cases
14. Known Issues and Technical Debt

---

## 1. System Overview and Processing Architecture

CardDemo is a comprehensive mainframe credit card management application that simulates account maintenance, transaction processing, reporting, and user administration via CICS online transactions and batch JCL job streams. It is designed as a reference application for demonstrating AWS mainframe modernization and migration patterns.

### 1.1 Technology Stack

| Layer | Technology | Version | Notes |
|-------|-----------|---------|-------|
| Language | COBOL | Enterprise COBOL (standard) | Primary language for all online and batch programs |
| Online TP | CICS | TS 5.x (7.3 CSD level) | Region name: CICSAWSA |
| Database | VSAM (KSDS with AIX) | N/A | Primary data persistence for all core files |
| Database (Optional) | DB2 | N/A | Transaction type management module |
| Database (Optional) | IMS DB | N/A | Pending authorization module |
| Messaging (Optional) | MQ Series | N/A | Authorization request/response, account inquiry |
| Scheduler | Control-M | N/A | Daily and weekly batch scheduling |
| Screen Handler | BMS (Basic Mapping Support) | N/A | 17 BMS map definitions for 3270 screens |
| Assembler | IBM HLASM | N/A | MVSWAIT (timer control), COBDATFT (date conversion) |
| Source Control | Git (GitHub) | N/A | Repository-based; originally PDS-based |

### 1.2 Overall Technical Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        CICS REGION (CICSAWSA)                          │
│                                                                         │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐         │
│  │ CC00     │    │ CM00     │    │ CAVW/    │    │ CT00/    │         │
│  │ Sign-On  │───▶│ Main     │───▶│ CAUP     │    │ CT01/    │         │
│  │ COSGN00C │    │ Menu     │    │ Account  │    │ CT02     │         │
│  └──────────┘    │ COMEN01C │    │ View/Upd │    │ Trans    │         │
│                  └──────────┘    └──────────┘    └──────────┘         │
│                       │                                                 │
│           ┌───────────┼────────────┬────────────┐                      │
│           ▼           ▼            ▼            ▼                      │
│      ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐                 │
│      │ CCLI/   │ │ CR00    │ │ CB00    │ │ CA00    │                 │
│      │ CCDL/   │ │ Reports │ │ Bill    │ │ Admin   │                 │
│      │ CCUP    │ │ CORPT00C│ │ Payment │ │ Menu    │                 │
│      │ Cards   │ └─────────┘ │ COBIL00C│ │ COADM01C│                 │
│      └─────────┘             └─────────┘ └─────────┘                 │
│                                               │                       │
│                                    ┌──────────┼──────────┐            │
│                                    ▼          ▼          ▼            │
│                               ┌────────┐ ┌────────┐ ┌────────┐      │
│                               │ CU00-  │ │ CTTU   │ │ CTLI   │      │
│                               │ CU03   │ │ TranTyp│ │ TranTyp│      │
│                               │ Users  │ │ Add/Edt│ │ List   │      │
│                               └────────┘ └────────┘ └────────┘      │
└────────────┬────────────────────────────────────────────────┬─────────┘
             │                                                │
             ▼                                                ▼
┌────────────────────────┐                    ┌───────────────────────┐
│    VSAM Files          │                    │   Optional Systems     │
│  ┌──────────────────┐  │                    │  ┌─────────────────┐  │
│  │ ACCTDAT (Account)│  │                    │  │ DB2 Tables      │  │
│  │ CARDDAT (Card)   │  │                    │  │ (TRNTYPE,       │  │
│  │ CUSTDAT (Cust)   │  │                    │  │  TRNTYCAT)      │  │
│  │ CCXREF (Xref)    │  │                    │  ├─────────────────┤  │
│  │ TRANSACT (Trans) │  │                    │  │ IMS DB          │  │
│  │ USRSEC (Security)│  │                    │  │ (Auth Data)     │  │
│  │ TCATBALF (Bal)   │  │                    │  ├─────────────────┤  │
│  │ DISCGRP (Disc)   │  │                    │  │ MQ Queues       │  │
│  │ CARDAIX (AIX)    │  │                    │  │ (Auth Req/Resp) │  │
│  │ CXACAIX (AIX)    │  │                    │  └─────────────────┘  │
│  └──────────────────┘  │                    └───────────────────────┘
└────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          BATCH JOB STREAM                               │
│                                                                         │
│  CLOSEFIL → ACCTFILE/CARDFILE/CUSTFILE/XREFFILE → TRANBKP →           │
│  POSTTRAN → INTCALC → COMBTRAN → CREASTMT → TRANIDX → OPENFIL        │
│                                                                         │
│  Programs: CBTRN02C, CBACT04C, CBSTM03A, CBTRN03C, SORT, IDCAMS      │
│  Utilities: CBEXPORT, CBIMPORT (Branch Migration)                      │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.3 Overall Business Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CARDDEMO BUSINESS PROCESS FLOW                       │
│                                                                         │
│  ┌──────────┐         ┌─────────────┐        ┌────────────────┐        │
│  │ User     │         │ Account     │        │ Transaction    │        │
│  │ Login    │────────▶│ Management  │───────▶│ Processing     │        │
│  │          │         │ (View/Edit) │        │ (Add/View/List)│        │
│  └──────────┘         └─────────────┘        └────────────────┘        │
│       │                      │                       │                  │
│       │               ┌──────┴──────┐          ┌─────┴──────┐          │
│       │               ▼             ▼          ▼            ▼          │
│       │         ┌──────────┐  ┌──────────┐ ┌────────┐ ┌─────────┐    │
│       │         │ Card     │  │ Customer │ │ Bill   │ │ Reports │    │
│       │         │ Mgmt     │  │ Data     │ │ Payment│ │ (Daily  │    │
│       │         │ (Search/ │  │ Lookup   │ │        │ │  Trans) │    │
│       │         │  Update) │  │          │ │        │ │         │    │
│       │         └──────────┘  └──────────┘ └────────┘ └─────────┘    │
│       │                                                                │
│       │    ┌────────────────── Admin Path ──────────────────┐          │
│       └───▶│ Admin Login → User CRUD → Tran Type Mgmt (DB2)│          │
│            └────────────────────────────────────────────────┘          │
│                                                                         │
│  ═══════════════════ Nightly Batch Window ═══════════════════          │
│                                                                         │
│  Close CICS Files → Backup Transactions → Post Daily Transactions →   │
│  Calculate Interest/Fees → Combine Transactions → Generate Statements →│
│  Reopen CICS Files                                                     │
│                                                                         │
│  ═══════════════════ Branch Migration (On-demand) ═══════════════════  │
│  Export Customer/Account/Card Data → Transfer → Import at Target       │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.4 Component Interaction Patterns

- **CICS XCTL (Transfer of Control)**: The primary navigation pattern. The sign-on program (COSGN00C) uses `EXEC CICS XCTL` to transfer to the main menu (COMEN01C), which in turn XCTLs to the selected function program. Each program uses COMMAREA to pass session context (user ID, user type, navigation history) across program boundaries.
- **CICS LINK (Call with Return)**: Not heavily used in the core flow; programs primarily use XCTL for screen-to-screen navigation. LINK is used sparingly for subprogram calls where return is needed.
- **CICS SEND MAP / RECEIVE MAP**: All online programs follow the pseudo-conversational pattern: SEND MAP → RETURN TRANSID → RECEIVE MAP on re-entry. BMS maps handle all 3270 terminal I/O.
- **COMMAREA-based Data Passing**: The `CARDDEMO-COMMAREA` (copybook COCOM01Y) is the central data shuttle between all online programs, carrying user context, navigation state, customer/account/card identifiers, and last map/mapset information.
- **Batch CALL**: The statement program (CBSTM03A) uses `CALL` to invoke the subroutine CBSTM03B for HTML formatting. The interest calculator (CBACT04C) and transaction posting (CBTRN02C) are standalone batch programs executed directly via JCL `EXEC PGM=`.
- **Online-to-Batch Handoff**: The daily batch cycle is triggered externally via the Control-M scheduler. CICS files must be closed (CLOSEFIL job) before batch processing and reopened (OPENFIL job) after completion. The transaction report (TRANREPT) can be submitted from CICS using an internal reader.

### 1.5 Error Handling and Recovery Patterns

- **CICS RESP Code Checking**: All CICS programs check `WS-RESP-CD` after file operations (READ, WRITE, REWRITE, DELETE). Non-zero responses trigger error messages displayed on screen via `ERRMSGO` fields in BMS maps.
- **File Status Checking**: Batch programs check file status codes after every I/O operation. CBTRN02C writes rejected transactions to a DALYREJS reject file with an appended reason code (record length 430 = 350-byte transaction + 80-byte rejection reason).
- **Abend Handling**: The copybook `CSMSG02Y` defines the `ABEND-DATA` structure with fields for abend code, culprit program, reason, and message. Programs use `EXEC CICS ABEND ABCODE(...)` for unrecoverable errors.
- **JCL Conditional Execution**: Batch JCL uses `COND` parameters and `MAXCC` settings to control step execution flow. IDCAMS `SET MAXCC = 0` is used to suppress non-critical errors (e.g., DELETE of non-existent datasets).
- **Validation Error Handling**: Online programs (especially COACTUPC, COCRDUPC) perform extensive field-level validation using the `CSSETATY` copybook pattern, which highlights invalid fields in red and marks blank required fields with asterisks.

---

## 2. Program Design and Call Structure

### 2.1 Programs

#### 2.1.1 Program: COSGN00C

**Transaction ID**: CC00  
**Program Type**: Online (CICS)  
**Purpose**: Sign-on screen for the CardDemo application  
**Responsibilities**: Authenticate users against the USRSEC VSAM file; route to appropriate menu based on user type (Admin/Regular)

**Interfaces**:
- **Inbound**: CICS terminal via CC00 transaction
- **Outbound**: XCTL to COMEN01C (regular user menu) or COADM01C (admin menu)
- **COMMAREA/Linkage**: Populates CDEMO-USER-ID, CDEMO-USER-TYPE, CDEMO-TO-TRANID

**Flow Summary**:
- If EIBCALEN = 0 (first entry), send the sign-on map
- On re-entry, receive user ID and password from screen
- Read USRSEC file to validate credentials
- If valid, populate COMMAREA and XCTL to the appropriate menu
- If invalid, redisplay with error message

**Copybooks Used**: COCOM01Y (COMMAREA), COSGN00 (BMS map), COTTL01Y (titles), CSDAT01Y (date/time), CSMSG01Y (messages), CSUSR01Y (user record)  
**Code Location**: `app/cbl/COSGN00C.cbl` (260 lines)

#### 2.1.2 Program: COMEN01C

**Transaction ID**: CM00  
**Program Type**: Online (CICS)  
**Purpose**: Main menu for regular users  
**Responsibilities**: Display menu options (11 items); route to selected function via XCTL

**Interfaces**:
- **Inbound**: XCTL from COSGN00C or return from sub-functions
- **Outbound**: XCTL to selected function program (COACTVWC, COACTUPC, COCRDLIC, etc.)
- **COMMAREA/Linkage**: Reads CDEMO-USER-TYPE to verify authorization; sets CDEMO-TO-PROGRAM

**Flow Summary**:
- Display menu with 11 options (Account View, Account Update, Card List, Card Search, Card Update, Transaction List, Transaction View, Transaction Add, Reports, Bill Payment, Pending Authorizations)
- Validate user selection against COMEN02Y option table
- XCTL to the selected program with updated COMMAREA

**Copybooks Used**: COCOM01Y, COMEN02Y (menu option definitions), COMEN01 (BMS), COTTL01Y, CSDAT01Y, CSMSG01Y, CSUSR01Y  
**Code Location**: `app/cbl/COMEN01C.cbl` (308 lines)

#### 2.1.3 Program: COACTUPC

**Transaction ID**: CAUP  
**Program Type**: Online (CICS)  
**Purpose**: Account update screen — the largest and most complex online program  
**Responsibilities**: Display account details for editing; validate all input fields; rewrite account record to VSAM

**Interfaces**:
- **Inbound**: XCTL from menu or account view
- **Outbound**: XCTL back to menu; reads/writes ACCTDAT, CARDDAT, CCXREF, CUSTDAT files
- **COMMAREA/Linkage**: Receives account ID via CDEMO-ACCT-ID

**Flow Summary**:
- Retrieve account, card, customer, and cross-reference records
- Display editable fields (status, credit limits, dates, group ID, etc.)
- Extensive field-by-field validation (date formats, numeric ranges, credit limits, state codes, zip codes)
- Uses CSSETATY copybook pattern to highlight errors
- On successful validation, rewrite account record

**Copybooks Used**: COCOM01Y, CVACT01Y, CVACT02Y, CVACT03Y, CVCUS01Y, CVCRD01Y, COACTUP (BMS), COTTL01Y, CSDAT01Y, CSMSG01Y, CSMSG02Y, CSUTLDWY, CSUTLDPY, CSLKPCDY, CSSTRPFY, CSSETATY  
**Code Location**: `app/cbl/COACTUPC.cbl` (4,236 lines)

#### 2.1.4 Program: COACTVWC

**Transaction ID**: CAVW  
**Program Type**: Online (CICS)  
**Purpose**: View account information (read-only)  
**Responsibilities**: Display account, card, and customer details

**Interfaces**:
- **Inbound**: XCTL from menu
- **Outbound**: Reads ACCTDAT, CARDDAT, CCXREF, CUSTDAT files
- **COMMAREA/Linkage**: Receives account ID via CDEMO-ACCT-ID

**Code Location**: `app/cbl/COACTVWC.cbl` (941 lines)

#### 2.1.5 Program: COCRDLIC

**Transaction ID**: CCLI  
**Program Type**: Online (CICS)  
**Purpose**: List credit cards with browsing capability  
**Responsibilities**: Browse CARDDAT/CARDAIX files; display paginated card list

**Interfaces**:
- **Inbound**: XCTL from menu
- **Outbound**: Reads CARDDAT, CARDAIX (AIX path); XCTL to COCRDSLC for card detail view
- **COMMAREA/Linkage**: Uses CDEMO-ACCT-ID for filtering

**Code Location**: `app/cbl/COCRDLIC.cbl` (1,459 lines)

#### 2.1.6 Program: COCRDSLC

**Transaction ID**: CCDL  
**Program Type**: Online (CICS)  
**Purpose**: Credit card detail view and search  
**Responsibilities**: Display detailed card information; search by card number or account

**Code Location**: `app/cbl/COCRDSLC.cbl` (887 lines)

#### 2.1.7 Program: COCRDUPC

**Transaction ID**: CCUP  
**Program Type**: Online (CICS)  
**Purpose**: Credit card update  
**Responsibilities**: Edit card details (embossed name, expiration date, status)

**Code Location**: `app/cbl/COCRDUPC.cbl` (1,560 lines)

#### 2.1.8 Program: COTRN00C

**Transaction ID**: CT00  
**Program Type**: Online (CICS)  
**Purpose**: Transaction list  
**Responsibilities**: Browse and display transaction records from TRANSACT file

**Code Location**: `app/cbl/COTRN00C.cbl` (699 lines)

#### 2.1.9 Program: COTRN01C

**Transaction ID**: CT01  
**Program Type**: Online (CICS)  
**Purpose**: Transaction view (detail)  
**Responsibilities**: Display individual transaction details

**Code Location**: `app/cbl/COTRN01C.cbl` (330 lines)

#### 2.1.10 Program: COTRN02C

**Transaction ID**: CT02  
**Program Type**: Online (CICS)  
**Purpose**: Add a new transaction  
**Responsibilities**: Validate and write new transaction records to TRANSACT VSAM file

**Code Location**: `app/cbl/COTRN02C.cbl` (783 lines)

#### 2.1.11 Program: CORPT00C

**Transaction ID**: CR00  
**Program Type**: Online (CICS)  
**Purpose**: Transaction reports — submits batch report job from CICS  
**Responsibilities**: Accept date range; submit TRANREPT batch job via internal reader

**Code Location**: `app/cbl/CORPT00C.cbl` (649 lines)

#### 2.1.12 Program: COBIL00C

**Transaction ID**: CB00  
**Program Type**: Online (CICS)  
**Purpose**: Bill payment processing  
**Responsibilities**: Process credit card bill payments; update account balances

**Code Location**: `app/cbl/COBIL00C.cbl` (572 lines)

#### 2.1.13 Program: COADM01C

**Transaction ID**: CA00  
**Program Type**: Online (CICS)  
**Purpose**: Admin menu  
**Responsibilities**: Display admin functions (User CRUD, Transaction Type management with DB2)

**Code Location**: `app/cbl/COADM01C.cbl` (288 lines)

#### 2.1.14 Program: COUSR00C / COUSR01C / COUSR02C / COUSR03C

**Transaction IDs**: CU00, CU01, CU02, CU03  
**Program Type**: Online (CICS)  
**Purpose**: User security management (List, Add, Update, Delete)  
**Responsibilities**: CRUD operations on the USRSEC VSAM file

**Code Locations**: `app/cbl/COUSR00C.cbl` (695 lines), `app/cbl/COUSR01C.cbl` (299 lines), `app/cbl/COUSR02C.cbl` (414 lines), `app/cbl/COUSR03C.cbl` (359 lines)

#### 2.1.15 Program: CBTRN02C (Batch)

**Program Type**: Batch  
**Purpose**: Post daily transaction records to the transaction master  
**Responsibilities**: Read daily transaction file (DALYTRAN); validate card via XREF; write to TRANSACT VSAM; update TCATBALF balances; update ACCTDAT cycle credits/debits; write rejects to DALYREJS

**Interfaces**:
- **Inbound**: JCL EXEC PGM=CBTRN02C (POSTTRAN job)
- **Outbound**: Reads DALYTRAN, XREFFILE; writes TRANSACT, DALYREJS; updates ACCTFILE, TCATBALF

**Code Location**: `app/cbl/CBTRN02C.cbl` (731 lines)  
**JCL**: `app/jcl/POSTTRAN.jcl`

#### 2.1.16 Program: CBACT04C (Batch)

**Program Type**: Batch  
**Purpose**: Interest and fee calculator  
**Responsibilities**: Read transaction category balances; look up disclosure group interest rates; calculate interest; create system-generated transactions; update account balances

**Interfaces**:
- **Inbound**: JCL EXEC PGM=CBACT04C with PARM='YYYYMMDD00' (INTCALC job)
- **Outbound**: Reads TCATBALF, XREFFILE, DISCGRP; writes TRANSACT (GDG); updates ACCTFILE

**Code Location**: `app/cbl/CBACT04C.cbl` (652 lines)  
**JCL**: `app/jcl/INTCALC.jcl`

#### 2.1.17 Program: CBSTM03A (Batch)

**Program Type**: Batch  
**Purpose**: Generate account statements in plain text and HTML format  
**Responsibilities**: Read sorted transaction file; produce per-card statements; call CBSTM03B subroutine for HTML formatting

**Interfaces**:
- **Inbound**: JCL EXEC PGM=CBSTM03A (CREASTMT job, step STEP040)
- **Outbound**: Reads TRNXFILE, XREFFILE, ACCTFILE, CUSTFILE; writes STMTFILE, HTMLFILE
- **CALL**: Static CALL to CBSTM03B for HTML generation

**Code Location**: `app/cbl/CBSTM03A.CBL` (924 lines)  
**JCL**: `app/jcl/CREASTMT.JCL`

#### 2.1.18 Program: CBSTM03B (Batch)

**Program Type**: Called Subprogram  
**Purpose**: HTML formatting subroutine for statement generation  
**Responsibilities**: Generate HTML markup for account statements

**Code Location**: `app/cbl/CBSTM03B.CBL` (230 lines)

#### 2.1.19 Program: CBTRN03C (Batch)

**Program Type**: Batch  
**Purpose**: Transaction report generation  
**Responsibilities**: Produce daily transaction report; can be submitted from CICS via CORPT00C

**Code Location**: `app/cbl/CBTRN03C.cbl` (649 lines)  
**JCL**: `app/jcl/TRANREPT.jcl`

#### 2.1.20 Program: CBTRN01C (Batch)

**Program Type**: Batch  
**Purpose**: Batch transaction read utility  
**Responsibilities**: Read and process transaction records sequentially

**Code Location**: `app/cbl/CBTRN01C.cbl` (494 lines)

#### 2.1.21 Program: CBACT01C / CBACT02C / CBACT03C (Batch)

**Program Type**: Batch  
**Purpose**: Account, card, and customer sequential read utilities  
**Responsibilities**: Read and display records from VSAM files

**Code Locations**: `app/cbl/CBACT01C.cbl` (430 lines), `app/cbl/CBACT02C.cbl` (178 lines), `app/cbl/CBACT03C.cbl` (178 lines)

#### 2.1.22 Program: CBCUS01C (Batch)

**Program Type**: Batch  
**Purpose**: Customer data sequential reader  
**Responsibilities**: Read customer records from CUSTDAT VSAM file

**Code Location**: `app/cbl/CBCUS01C.cbl` (178 lines)

#### 2.1.23 Program: CBEXPORT / CBIMPORT (Batch)

**Program Type**: Batch  
**Purpose**: Branch migration data export/import utilities  
**Responsibilities**: CBEXPORT reads normalized CardDemo VSAM files and creates multi-record export file with COMP/COMP-3 fields, REDEFINES, and OCCURS structures. CBIMPORT reads the export file and loads data into target environment.

**Code Locations**: `app/cbl/CBEXPORT.cbl` (582 lines), `app/cbl/CBIMPORT.cbl` (487 lines)  
**JCL**: `app/jcl/CBEXPORT.jcl`, `app/jcl/CBIMPORT.jcl`

#### 2.1.24 Program: CSUTLDTC (Batch Utility)

**Program Type**: Utility Subprogram  
**Purpose**: Date validation and conversion utilities  
**Responsibilities**: Validate CCYYMMDD dates, check leap years, month/day ranges

**Code Location**: `app/cbl/CSUTLDTC.cbl` (157 lines)

#### 2.1.25 Program: COBSWAIT (Batch)

**Program Type**: Batch  
**Purpose**: Batch wait/delay utility — calls assembler MVSWAIT  
**Responsibilities**: Introduce a timed wait in batch job processing

**Code Location**: `app/cbl/COBSWAIT.cbl` (41 lines)

#### 2.1.26 Assembler Programs

| Program | Type | Purpose |
|---------|------|---------|
| MVSWAIT | HLASM | Timer control — accepts a delay value in hundredths of a second and issues ASMWAIT macro |
| COBDATFT | HLASM | Date format conversion — converts between YYYYMMDD and YYYY-MM-DD formats |

**Code Location**: `app/asm/MVSWAIT.asm`, `app/asm/COBDATFT.asm`

### 2.2 Program Call Hierarchy Diagram

```
CICS Online Hierarchy (XCTL-based navigation):
═══════════════════════════════════════════════

CC00 (COSGN00C) ── Sign-On
  │
  ├──XCTL──▶ CM00 (COMEN01C) ── Main Menu (Regular User)
  │            ├──XCTL──▶ CAVW (COACTVWC) ── Account View
  │            ├──XCTL──▶ CAUP (COACTUPC) ── Account Update
  │            ├──XCTL──▶ CCLI (COCRDLIC) ── Card List
  │            │            └──XCTL──▶ CCDL (COCRDSLC) ── Card Detail
  │            │                         └──XCTL──▶ CCUP (COCRDUPC) ── Card Update
  │            ├──XCTL──▶ CT00 (COTRN00C) ── Transaction List
  │            │            └──XCTL──▶ CT01 (COTRN01C) ── Transaction View
  │            ├──XCTL──▶ CT02 (COTRN02C) ── Transaction Add
  │            ├──XCTL──▶ CR00 (CORPT00C) ── Reports
  │            ├──XCTL──▶ CB00 (COBIL00C) ── Bill Payment
  │            └──XCTL──▶ CPVS (COPAUS0C) ── Pending Auth Summary [Optional]
  │                         └──XCTL──▶ CPVD (COPAUS1C) ── Pending Auth Detail [Optional]
  │
  └──XCTL──▶ CA00 (COADM01C) ── Admin Menu (Admin User)
               ├──XCTL──▶ CU00 (COUSR00C) ── User List
               │            ├──XCTL──▶ CU01 (COUSR01C) ── Add User
               │            ├──XCTL──▶ CU02 (COUSR02C) ── Update User
               │            └──XCTL──▶ CU03 (COUSR03C) ── Delete User
               ├──XCTL──▶ CTTU (COTRTUPC) ── Tran Type Add/Edit [DB2 Optional]
               └──XCTL──▶ CTLI (COTRTLIC) ── Tran Type List [DB2 Optional]


Batch Hierarchy (JCL EXEC / CALL):
═══════════════════════════════════

POSTTRAN (JCL) ──▶ CBTRN02C (EXEC PGM)

INTCALC  (JCL) ──▶ CBACT04C (EXEC PGM, PARM='YYYYMMDD00')

CREASTMT (JCL) ──▶ SORT (STEP010)
                ──▶ IDCAMS (STEP020 - REPRO)
                ──▶ CBSTM03A (STEP040 - EXEC PGM)
                      └── CALL ──▶ CBSTM03B (Static CALL - HTML subroutine)

COMBTRAN (JCL) ──▶ SORT (STEP05R)
                ──▶ IDCAMS (STEP10 - REPRO)

TRANREPT (JCL) ──▶ CBTRN03C (EXEC PGM)

CBEXPORT (JCL) ──▶ IDCAMS (STEP01 - DEFINE CLUSTER)
                ──▶ CBEXPORT (STEP02 - EXEC PGM)

CBIMPORT (JCL) ──▶ CBIMPORT (EXEC PGM)

WAITSTEP (JCL) ──▶ COBSWAIT (EXEC PGM)
                      └── CALL ──▶ MVSWAIT (Assembler - timer control)
```

### 2.3 Paragraph/Section Flow Diagrams

For the most complex program, COACTUPC (4,236 lines):

```
COACTUPC Paragraph Flow:
════════════════════════

MAIN-PARA
  │
  ├── (EIBCALEN = 0?) ──Yes──▶ SEND Map → RETURN TRANSID
  │
  ├── RECEIVE-MAP-PARA
  │     └── EXEC CICS RECEIVE MAP
  │
  ├── EVALUATE EIBAID
  │     ├── PF3 → Return to Menu
  │     ├── PF5 → Refresh / Clear
  │     ├── ENTER → PROCESS-ENTER-PARA
  │     └── OTHER → Invalid Key Message
  │
  ├── PROCESS-ENTER-PARA
  │     ├── EVALUATE CDEMO-PGM-CONTEXT
  │     │     ├── 0 (ENTER) → PROCESS-ACCT-LOOKUP
  │     │     └── 1 (REENTER) → PROCESS-ACCT-UPDATE
  │     │
  │     ├── PROCESS-ACCT-LOOKUP
  │     │     ├── READ-ACCT-DATA (READ ACCTDAT)
  │     │     ├── READ-CARD-DATA (READ CARDDAT via CCXREF)
  │     │     ├── READ-CUST-DATA (READ CUSTDAT)
  │     │     └── POPULATE-SCREEN-FIELDS
  │     │
  │     └── PROCESS-ACCT-UPDATE
  │           ├── EDIT-ACCT-STATUS
  │           ├── EDIT-CREDIT-LIMIT
  │           ├── EDIT-CASH-CREDIT-LIMIT
  │           ├── EDIT-OPEN-DATE (PERFORM EDIT-DATE-CCYYMMDD)
  │           ├── EDIT-EXPIRATION-DATE
  │           ├── EDIT-REISSUE-DATE
  │           ├── EDIT-GROUP-ID
  │           │     └── Uses CSSETATY pattern for each field
  │           │
  │           ├── (All fields valid?)
  │           │     ├── Yes → REWRITE ACCTDAT
  │           │     └── No → Highlight errors → Redisplay
  │           │
  │           └── SEND-MAP-PARA
  │
  └── SEND-MAP-PARA
        └── EXEC CICS SEND MAP → RETURN TRANSID
```

---

## 3. Data Structures and File Layouts

### 3.1 Copybook Definitions

| Copybook | Purpose | Used By | Location |
|----------|---------|---------|----------|
| COCOM01Y | COMMAREA — central communication area between all CICS programs | All online programs | `app/cpy/` |
| CVACT01Y | Account record layout (RECLN 300) | COACTUPC, COACTVWC, CBTRN02C, CBACT04C, CBSTM03A, CBEXPORT | `app/cpy/` |
| CVACT02Y | Card record layout (RECLN 150) | COCRDLIC, COCRDSLC, COCRDUPC, COACTUPC | `app/cpy/` |
| CVACT03Y | Card cross-reference record (RECLN 50) | COCRDLIC, COACTUPC, CBTRN02C, CBACT04C | `app/cpy/` |
| CVCUS01Y | Customer record layout (RECLN 500) | COACTUPC, COACTVWC, CBSTM03A, CBEXPORT | `app/cpy/` |
| CVTRA01Y | Transaction category balance (RECLN 50) | CBTRN02C, CBACT04C | `app/cpy/` |
| CVTRA02Y | Disclosure group record (RECLN 50) | CBACT04C | `app/cpy/` |
| CVTRA03Y | Transaction type record (RECLN 60) | CBTRN03C, COTRN02C | `app/cpy/` |
| CVTRA04Y | Transaction category type record (RECLN 60) | CBTRN03C | `app/cpy/` |
| CVTRA05Y | Transaction record (RECLN 350) | COTRN00C, COTRN01C, COTRN02C, CBTRN02C | `app/cpy/` |
| CVTRA06Y | Daily transaction record (RECLN 350) | CBTRN02C | `app/cpy/` |
| CVTRA07Y | Transaction report header/detail structures | CBTRN03C | `app/cpy/` |
| CSUSR01Y | User security record (RECLN 80) | COSGN00C, COUSR00C-03C | `app/cpy/` |
| CVCRD01Y | Card work areas and PFKey AID mapping | All online programs | `app/cpy/` |
| COTTL01Y | Screen title constants | All online programs | `app/cpy/` |
| CSDAT01Y | Date/time working storage fields | All online programs | `app/cpy/` |
| CSMSG01Y | Common screen messages | All online programs | `app/cpy/` |
| CSMSG02Y | Abend data structure | Error handling programs | `app/cpy/` |
| COMEN02Y | Main menu option table (11 options) | COMEN01C | `app/cpy/` |
| COADM02Y | Admin menu option table (6 options) | COADM01C | `app/cpy/` |
| CSUTLDWY | Date validation working storage | COACTUPC, COCRDUPC | `app/cpy/` |
| CSUTLDPY | Date validation procedure division | COACTUPC, COCRDUPC | `app/cpy/` |
| CSLKPCDY | Lookup code repository (phone area codes, state codes, zip codes) | COACTUPC | `app/cpy/` |
| CSSTRPFY | PFKey store paragraph | All online programs | `app/cpy/` |
| CSSETATY | Field attribute setting (error highlight) macro-like copybook | COACTUPC, COCRDUPC | `app/cpy/` |
| CODATECN | Date conversion record (REDEFINES for YYYYMMDD / YYYY-MM-DD) | Date utility programs | `app/cpy/` |
| CVEXPORT | Multi-record export layout (500 bytes) with REDEFINES and OCCURS | CBEXPORT, CBIMPORT | `app/cpy/` |
| CUSTREC | Alternate customer record layout (RECLN 500) | Legacy compatibility | `app/cpy/` |
| COSTM01 | Statement processing copybook | CBSTM03A | `app/cpy/` |
| UNUSED1Y | Placeholder / unused copybook | None | `app/cpy/` |

**Key Record Layouts:**

**ACCOUNT-RECORD (CVACT01Y, 300 bytes)**:
```
01  ACCOUNT-RECORD.
    05  ACCT-ID                      PIC 9(11).         [Key]
    05  ACCT-ACTIVE-STATUS           PIC X(01).
    05  ACCT-CURR-BAL                PIC S9(10)V99.
    05  ACCT-CREDIT-LIMIT            PIC S9(10)V99.
    05  ACCT-CASH-CREDIT-LIMIT       PIC S9(10)V99.
    05  ACCT-OPEN-DATE               PIC X(10).
    05  ACCT-EXPIRAION-DATE          PIC X(10).
    05  ACCT-REISSUE-DATE            PIC X(10).
    05  ACCT-CURR-CYC-CREDIT         PIC S9(10)V99.
    05  ACCT-CURR-CYC-DEBIT          PIC S9(10)V99.
    05  ACCT-ADDR-ZIP                PIC X(10).
    05  ACCT-GROUP-ID                PIC X(10).
    05  FILLER                       PIC X(178).
```

**CARD-RECORD (CVACT02Y, 150 bytes)**:
```
01  CARD-RECORD.
    05  CARD-NUM                     PIC X(16).          [Key]
    05  CARD-ACCT-ID                 PIC 9(11).
    05  CARD-CVV-CD                  PIC 9(03).
    05  CARD-EMBOSSED-NAME           PIC X(50).
    05  CARD-EXPIRAION-DATE          PIC X(10).
    05  CARD-ACTIVE-STATUS           PIC X(01).
    05  FILLER                       PIC X(59).
```

**CUSTOMER-RECORD (CVCUS01Y, 500 bytes)**:
```
01  CUSTOMER-RECORD.
    05  CUST-ID                      PIC 9(09).          [Key]
    05  CUST-FIRST-NAME              PIC X(25).
    05  CUST-MIDDLE-NAME             PIC X(25).
    05  CUST-LAST-NAME               PIC X(25).
    05  CUST-ADDR-LINE-1             PIC X(50).
    05  CUST-ADDR-LINE-2             PIC X(50).
    05  CUST-ADDR-LINE-3             PIC X(50).
    05  CUST-ADDR-STATE-CD           PIC X(02).
    05  CUST-ADDR-COUNTRY-CD         PIC X(03).
    05  CUST-ADDR-ZIP                PIC X(10).
    05  CUST-PHONE-NUM-1             PIC X(15).
    05  CUST-PHONE-NUM-2             PIC X(15).
    05  CUST-SSN                     PIC 9(09).
    05  CUST-GOVT-ISSUED-ID          PIC X(20).
    05  CUST-DOB-YYYY-MM-DD          PIC X(10).
    05  CUST-EFT-ACCOUNT-ID          PIC X(10).
    05  CUST-PRI-CARD-HOLDER-IND     PIC X(01).
    05  CUST-FICO-CREDIT-SCORE       PIC 9(03).
    05  FILLER                       PIC X(168).
```

**TRAN-RECORD (CVTRA05Y, 350 bytes)**:
```
01  TRAN-RECORD.
    05  TRAN-ID                      PIC X(16).          [Key]
    05  TRAN-TYPE-CD                 PIC X(02).
    05  TRAN-CAT-CD                  PIC 9(04).
    05  TRAN-SOURCE                  PIC X(10).
    05  TRAN-DESC                    PIC X(100).
    05  TRAN-AMT                     PIC S9(09)V99.
    05  TRAN-MERCHANT-ID             PIC 9(09).
    05  TRAN-MERCHANT-NAME           PIC X(50).
    05  TRAN-MERCHANT-CITY           PIC X(50).
    05  TRAN-MERCHANT-ZIP            PIC X(10).
    05  TRAN-CARD-NUM                PIC X(16).
    05  TRAN-ORIG-TS                 PIC X(26).
    05  TRAN-PROC-TS                 PIC X(26).
    05  FILLER                       PIC X(20).
```

**CARD-XREF-RECORD (CVACT03Y, 50 bytes)**:
```
01  CARD-XREF-RECORD.
    05  XREF-CARD-NUM                PIC X(16).          [Primary Key]
    05  XREF-CUST-ID                 PIC 9(09).
    05  XREF-ACCT-ID                 PIC 9(11).          [Alternate Key]
    05  FILLER                       PIC X(14).
```

**SEC-USER-DATA (CSUSR01Y, 80 bytes)**:
```
01  SEC-USER-DATA.
    05  SEC-USR-ID                   PIC X(08).          [Key]
    05  SEC-USR-FNAME                PIC X(20).
    05  SEC-USR-LNAME                PIC X(20).
    05  SEC-USR-PWD                  PIC X(08).
    05  SEC-USR-TYPE                 PIC X(01).
    05  SEC-USR-FILLER               PIC X(23).
```

### 3.2 VSAM File Specifications

| Attribute | ACCTDAT | CARDDAT | CUSTDAT | CCXREF | TRANSACT | USRSEC |
|-----------|---------|---------|---------|--------|----------|--------|
| Cluster Name | AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS | AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS | AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS | AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS | AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS | AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS |
| File Type | KSDS | KSDS | KSDS | KSDS | KSDS | KSDS |
| Primary Key | ACCT-ID (9(11), offset 0) | CARD-NUM (X(16), offset 0) | CUST-ID (9(09), offset 0) | XREF-CARD-NUM (X(16), offset 0) | TRAN-ID (X(16), offset 0) | SEC-USR-ID (X(08), offset 0) |
| Alternate Key(s) | None | Via AIX Path (CARDAIX) | None | Via AIX Path (CXACAIX) — XREF-ACCT-ID | None | None |
| Record Format | Variable (CSD) | Variable (CSD) | Variable (CSD) | Variable (CSD) | Variable (CSD) | Variable (CSD) |
| Max Record Length | 300 | 150 | 500 | 50 | 350 | 80 |
| SHAREOPTIONS | ALLREQS (CSD) | ALLREQS (CSD) | ALLREQS (CSD) | ALLREQS (CSD) | ALLREQS (CSD) | ALLREQS (CSD) |

**Additional VSAM Files:**

| File | Cluster | Key | RECLN | Purpose |
|------|---------|-----|-------|---------|
| TCATBALF | AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS | TRAN-CAT-KEY (ACCT-ID + TYPE-CD + CAT-CD) | 50 | Transaction category balances |
| DISCGRP | AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS | DIS-GROUP-KEY (GROUP-ID + TYPE-CD + CAT-CD) | 50 | Disclosure group interest rates |
| TRANTYPE | (loaded from PS) | TRAN-TYPE (X(02)) | 60 | Transaction type descriptions |
| TRANCATG | (loaded from PS) | TRAN-CAT-KEY (TYPE-CD + CAT-CD) | 60 | Transaction category descriptions |

### 3.3 DB2 Table Definitions (Optional Module)

**Transaction Type Management Tables** (defined in `app/app-transaction-type-db2/ddl/`):

- **TRNTYPE**: Transaction type reference table (type code, description)
- **TRNTYCAT**: Transaction category reference table (type + category code, description)

**Authorization Tables** (defined in `app/app-authorization-ims-db2-mq/ddl/`):

- **AUTHFRDS**: Authorization fraud/logging records

DCL files: `app/app-transaction-type-db2/dcl/DCLTRTYP.dcl`, `app/app-transaction-type-db2/dcl/DCLTRCAT.dcl`, `app/app-authorization-ims-db2-mq/dcl/AUTHFRDS.dcl`

### 3.4 Working Storage Key Structures

**CARDDEMO-COMMAREA (COCOM01Y)** — Central session state:
```
01 CARDDEMO-COMMAREA.
   05 CDEMO-GENERAL-INFO.
      10 CDEMO-FROM-TRANID          PIC X(04).     [Source transaction]
      10 CDEMO-FROM-PROGRAM         PIC X(08).     [Source program]
      10 CDEMO-TO-TRANID            PIC X(04).     [Target transaction]
      10 CDEMO-TO-PROGRAM           PIC X(08).     [Target program]
      10 CDEMO-USER-ID              PIC X(08).     [Authenticated user]
      10 CDEMO-USER-TYPE            PIC X(01).     [A=Admin, U=User]
      10 CDEMO-PGM-CONTEXT          PIC 9(01).     [0=Enter, 1=Reenter]
   05 CDEMO-CUSTOMER-INFO.
      10 CDEMO-CUST-ID              PIC 9(09).
      10 CDEMO-CUST-FNAME/MNAME/LNAME PIC X(25) each.
   05 CDEMO-ACCOUNT-INFO.
      10 CDEMO-ACCT-ID              PIC 9(11).
      10 CDEMO-ACCT-STATUS          PIC X(01).
   05 CDEMO-CARD-INFO.
      10 CDEMO-CARD-NUM             PIC 9(16).
   05 CDEMO-MORE-INFO.
      10 CDEMO-LAST-MAP             PIC X(7).
      10 CDEMO-LAST-MAPSET          PIC X(7).
```

**CC-WORK-AREAS (CVCRD01Y)** — PFKey mapping and navigation:
```
01 CC-WORK-AREAS.
   05 CC-WORK-AREA.
      10 CCARD-AID                  PIC X(5).      [Mapped PFKey name]
         88 CCARD-AID-ENTER         VALUE 'ENTER'.
         88 CCARD-AID-PFK01..PFK12  VALUE 'PFK01'..'PFK12'.
      10 CCARD-NEXT-PROG           PIC X(8).       [Next program to XCTL]
      10 CCARD-NEXT-MAPSET         PIC X(7).
      10 CCARD-NEXT-MAP            PIC X(7).
      10 CCARD-ERROR-MSG           PIC X(75).
      10 CCARD-RETURN-MSG          PIC X(75).
```

**Flags and Switches** (typical in each program):
- `WS-ERR-FLG` (PIC X(01)) — 'Y' = error found, 'N' = no error; level 88s: ERR-FLG-ON, ERR-FLG-OFF
- `FLG-<field>-NOT-OK` / `FLG-<field>-BLANK` — per-field validation flags used with CSSETATY
- `CDEMO-PGM-ENTER` (VALUE 0) / `CDEMO-PGM-REENTER` (VALUE 1) — pseudo-conversational state

---

## 4. Program Interface Specifications

### 4.1 COMMAREA Contracts

**CARDDEMO-COMMAREA** (all online programs, ~160 bytes):

| Field | Offset | PIC | Direction | Purpose |
|-------|--------|-----|-----------|---------|
| CDEMO-FROM-TRANID | 0 | X(04) | BOTH | Originating transaction ID |
| CDEMO-FROM-PROGRAM | 4 | X(08) | BOTH | Originating program name |
| CDEMO-TO-TRANID | 12 | X(04) | BOTH | Destination transaction ID |
| CDEMO-TO-PROGRAM | 16 | X(08) | BOTH | Destination program name |
| CDEMO-USER-ID | 24 | X(08) | IN | Authenticated user ID |
| CDEMO-USER-TYPE | 32 | X(01) | IN | 'A' = Admin, 'U' = Regular |
| CDEMO-PGM-CONTEXT | 33 | 9(01) | BOTH | 0 = First entry, 1 = Re-entry |
| CDEMO-CUST-ID | 34 | 9(09) | BOTH | Current customer ID |
| CDEMO-CUST-FNAME | 43 | X(25) | BOTH | Customer first name |
| CDEMO-CUST-MNAME | 68 | X(25) | BOTH | Customer middle name |
| CDEMO-CUST-LNAME | 93 | X(25) | BOTH | Customer last name |
| CDEMO-ACCT-ID | 118 | 9(11) | BOTH | Current account ID |
| CDEMO-ACCT-STATUS | 129 | X(01) | BOTH | Account status |
| CDEMO-CARD-NUM | 130 | 9(16) | BOTH | Current card number |
| CDEMO-LAST-MAP | 146 | X(7) | BOTH | Last displayed BMS map |
| CDEMO-LAST-MAPSET | 153 | X(7) | BOTH | Last used BMS mapset |

**Return Code Conventions**: No formal return codes in the COMMAREA; errors are communicated via BMS screen messages (ERRMSGO fields). Navigation failures fall back to menu display.

### 4.2 Linkage Section Contracts

**CBSTM03A → CBSTM03B** (Static CALL):
- HTML line data passed via CALL ... USING parameters
- CBSTM03B generates HTML tags and formatting
- Returns formatted HTML string for file output

**COBSWAIT → MVSWAIT** (Static CALL to Assembler):
- Parameter 1: Binary fullword containing delay value (hundredths of seconds)
- MVSWAIT issues z/OS WAIT macro for the specified duration

### 4.3 File I/O Contracts

| File (CICS DD) | Operations | Status Codes Handled | Programs |
|----------------|-----------|---------------------|----------|
| ACCTDAT | READ, REWRITE, BROWSE | NORMAL, NOTFND, DUPREC | COACTUPC, COACTVWC, CBTRN02C, CBACT04C |
| CARDDAT | READ, BROWSE | NORMAL, NOTFND | COCRDLIC, COCRDSLC, COCRDUPC, COACTUPC |
| CARDAIX | BROWSE (via AIX path) | NORMAL, NOTFND, ENDFILE | COCRDLIC |
| CUSTDAT | READ | NORMAL, NOTFND | COACTUPC, COACTVWC |
| CCXREF | READ, BROWSE | NORMAL, NOTFND | COACTUPC, CBTRN02C, CBACT04C |
| CXACAIX | BROWSE (via AIX path) | NORMAL, NOTFND, ENDFILE | CBACT04C |
| TRANSACT | READ, WRITE, BROWSE | NORMAL, NOTFND, DUPREC | COTRN00C-02C, CBTRN02C |
| USRSEC | READ, WRITE, REWRITE, DELETE | NORMAL, NOTFND, DUPREC | COSGN00C, COUSR00C-03C |
| TCATBALF | READ, WRITE, REWRITE | NORMAL, NOTFND | CBTRN02C, CBACT04C |
| DISCGRP | READ | NORMAL, NOTFND | CBACT04C |

**Batch File Status Codes**: Standard file status codes (00 = success, 10 = end of file, 23 = record not found, 22 = duplicate key). All batch programs test file status after every I/O and display errors via DISPLAY statements.

### 4.4 MQ Message Contracts (Optional Module)

**Authorization Request/Response** (app-authorization-ims-db2-mq):
- **Request Queue**: Authorization requests placed by external systems
- **Response Queue**: Authorization approvals/denials
- **CP00 (COPAUA0C)**: MQ-triggered program that processes authorization requests, reads IMS DB for customer data, and writes response messages
- **CDRD transaction**: Inquire system date via MQ request/response pattern
- **CDRA transaction**: Inquire account details via MQ request/response pattern

---

## 5. Code Architecture and Organization

### 5.1 Division Structure

**IDENTIFICATION DIVISION Conventions**:
- PROGRAM-ID follows naming convention: `CO*` for online CICS programs, `CB*` for batch programs
- AUTHOR field is consistently 'AWS'
- DATE-WRITTEN used in some programs (e.g., COACTUPC: "July 2022")

**ENVIRONMENT DIVISION**:
- Online programs: No FILE-CONTROL section (all file I/O via CICS commands)
- Batch programs: SELECT/ASSIGN for each file with ORGANIZATION IS INDEXED/SEQUENTIAL, ACCESS MODE, RECORD KEY, and FILE STATUS clauses

**DATA DIVISION**:
- File Section (FD): Batch programs only — define file descriptors for each SELECT
- Working-Storage Section: Program-specific variables followed by COPY statements for shared copybooks
- Copybook inclusion order is consistent: COCOM01Y (COMMAREA) → Program-specific → BMS map copy → Standard (COTTL01Y, CSDAT01Y, CSMSG01Y, CSUSR01Y) → DFHAID, DFHBMSCA

**PROCEDURE DIVISION**:
- All online programs start with MAIN-PARA
- Pseudo-conversational pattern: Check EIBCALEN → RECEIVE MAP → EVALUATE EIBAID → Process → SEND MAP → RETURN TRANSID

### 5.2 Paragraph/Section Naming Conventions

- **MAIN-PARA**: Entry point for all online programs
- **PROCESS-ENTER-PARA**: Handle ENTER key processing
- **SEND-MAP-PARA**: Send BMS map to terminal
- **RECEIVE-MAP-PARA**: Receive BMS map from terminal
- **READ-*-DATA**: File read paragraphs (e.g., READ-ACCT-DATA, READ-CARD-DATA)
- **EDIT-***: Validation paragraphs (e.g., EDIT-ACCT-STATUS, EDIT-CREDIT-LIMIT)
- **YYYY-STORE-PFKEY** (CSSTRPFY copybook): Maps EIBAID to friendly PFKey names
- **EDIT-DATE-CCYYMMDD** (CSUTLDPY copybook): Date validation reusable paragraph
- Batch programs use numbered steps: STEP-010, STEP-020, or descriptive names: 1000-INIT-PARA, 2000-PROCESS-PARA, 9000-CLEANUP-PARA

### 5.3 Configuration and Control

**JCL Parameters**:
- CBACT04C accepts PARM='YYYYMMDD00' for processing date via JCL EXEC statement
- Most batch programs use hardcoded DSN references with HLQ `AWS.M2.CARDDEMO`
- GDG relative generation references: `(+1)` for new, `(0)` for current

**CICS Resource Definitions** (from CSD file `app/csd/CARDDEMO.CSD`):
- All resources defined in GROUP(CARDDEMO)
- CICS Region: CICSAWSA
- Library: COM2DOLL (DSNAME: &HLQ..LOADLIB)
- All files defined with DISPOSITION(SHARE), DSNSHARING(ALLREQS)

**Runtime Switches**:
- `CDEMO-USER-TYPE`: Controls menu display (Admin vs. Regular)
- `CDEMO-PGM-CONTEXT`: Controls pseudo-conversational state (first display vs. data processing)
- `WS-ERR-FLG`: Per-program error flag driving screen redisplay

---

## 6. Transaction and Batch Interface Specifications

### 6.1 CICS Transaction Definitions

| Transaction ID | Entry Program | Screen Flow | Design Pattern | Terminal I/O |
|----------------|---------------|-------------|----------------|--------------|
| CC00 | COSGN00C | COSGN0A → (Menu) | Pseudo-conversational | BMS SEND/RECEIVE |
| CM00 | COMEN01C | COMEN1A → (Selected Function) | Pseudo-conversational | BMS SEND/RECEIVE |
| CAVW | COACTVWC | COACTVW map | Pseudo-conversational | BMS SEND/RECEIVE |
| CAUP | COACTUPC | COACTUP map | Pseudo-conversational | BMS SEND/RECEIVE |
| CCLI | COCRDLIC | COCRDLI map | Pseudo-conversational | BMS SEND/RECEIVE |
| CCDL | COCRDSLC | COCRDSL map | Pseudo-conversational | BMS SEND/RECEIVE |
| CCUP | COCRDUPC | COCRDUP map | Pseudo-conversational | BMS SEND/RECEIVE |
| CT00 | COTRN00C | COTRN00 map | Pseudo-conversational | BMS SEND/RECEIVE |
| CT01 | COTRN01C | COTRN01 map | Pseudo-conversational | BMS SEND/RECEIVE |
| CT02 | COTRN02C | COTRN02 map | Pseudo-conversational | BMS SEND/RECEIVE |
| CR00 | CORPT00C | CORPT00 map | Pseudo-conversational | BMS SEND/RECEIVE |
| CB00 | COBIL00C | COBIL00 map | Pseudo-conversational | BMS SEND/RECEIVE |
| CA00 | COADM01C | COADM01 map | Pseudo-conversational | BMS SEND/RECEIVE |
| CU00 | COUSR00C | COUSR00 map | Pseudo-conversational | BMS SEND/RECEIVE |
| CU01 | COUSR01C | COUSR01 map | Pseudo-conversational | BMS SEND/RECEIVE |
| CU02 | COUSR02C | COUSR02 map | Pseudo-conversational | BMS SEND/RECEIVE |
| CU03 | COUSR03C | COUSR03 map | Pseudo-conversational | BMS SEND/RECEIVE |
| CPVS | COPAUS0C | COPAU00 map | Pseudo-conversational | BMS SEND/RECEIVE (Optional) |
| CPVD | COPAUS1C | COPAU01 map | Pseudo-conversational | BMS SEND/RECEIVE (Optional) |
| CTTU | COTRTUPC | COTRTUP map | Pseudo-conversational | BMS SEND/RECEIVE (Optional: DB2) |
| CTLI | COTRTLIC | COTRTLI map | Pseudo-conversational | BMS SEND/RECEIVE (Optional: DB2) |
| CDRD | CODATE01 | N/A (MQ) | MQ Request/Response | MQ (Optional) |
| CDRA | COACCT01 | N/A (MQ) | MQ Request/Response | MQ (Optional) |

### 6.2 Batch Job Specifications

#### POSTTRAN — Post Daily Transactions
```
JCL: app/jcl/POSTTRAN.jcl
Step: STEP15 — EXEC PGM=CBTRN02C
  DD: DALYTRAN (INPUT)  — AWS.M2.CARDDEMO.DALYTRAN.PS
  DD: TRANFILE (I/O)    — AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS
  DD: XREFFILE (INPUT)  — AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS
  DD: DALYREJS (OUTPUT)  — AWS.M2.CARDDEMO.DALYREJS(+1) [GDG]
  DD: ACCTFILE (I/O)    — AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS
  DD: TCATBALF (I/O)    — AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS
```

#### INTCALC — Interest Calculation
```
JCL: app/jcl/INTCALC.jcl
Step: STEP15 — EXEC PGM=CBACT04C,PARM='2022071800'
  DD: TCATBALF (INPUT)  — AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS
  DD: XREFFILE (INPUT)  — AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS
  DD: XREFFIL1 (INPUT)  — AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH
  DD: ACCTFILE (I/O)    — AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS
  DD: DISCGRP  (INPUT)  — AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS
  DD: TRANSACT (OUTPUT)  — AWS.M2.CARDDEMO.SYSTRAN(+1) [GDG]
```

#### COMBTRAN — Combine Transactions
```
JCL: app/jcl/COMBTRAN.jcl
Step: STEP05R — EXEC PGM=SORT
  SORTIN: AWS.M2.CARDDEMO.TRANSACT.BKUP(0) + AWS.M2.CARDDEMO.SYSTRAN(0)
  SORTOUT: AWS.M2.CARDDEMO.TRANSACT.COMBINED(+1)
  SORT FIELDS=(TRAN-ID,A)

Step: STEP10 — EXEC PGM=IDCAMS
  REPRO from COMBINED to TRANSACT.VSAM.KSDS
```

#### CREASTMT — Create Statements
```
JCL: app/jcl/CREASTMT.JCL
Step: DELDEF01 — IDCAMS: Delete/Define TRXFL work VSAM cluster
Step: STEP010  — SORT: Rekey transaction file by card number + tran ID
Step: STEP020  — IDCAMS REPRO: Load sorted file to TRXFL VSAM
Step: STEP030  — IEFBR14: Delete previous statement output files
Step: STEP040  — EXEC PGM=CBSTM03A: Generate statements (text + HTML)
  DD: TRNXFILE (INPUT)  — AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS
  DD: XREFFILE (INPUT)  — AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS
  DD: ACCTFILE (INPUT)  — AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS
  DD: CUSTFILE (INPUT)  — AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS
  DD: STMTFILE (OUTPUT) — AWS.M2.CARDDEMO.STATEMNT.PS
  DD: HTMLFILE (OUTPUT) — AWS.M2.CARDDEMO.STATEMNT.HTML
```

#### CLOSEFIL / OPENFIL — CICS File Management
```
JCL: app/jcl/CLOSEFIL.jcl / OPENFIL.jcl
Uses SDSF to issue CEMT commands to CICS region CICSAWSA:
  CEMT SET FIL(TRANSACT) CLO/OPE
  CEMT SET FIL(CCXREF) CLO/OPE
  CEMT SET FIL(ACCTDAT) CLO/OPE
  CEMT SET FIL(CXACAIX) CLO/OPE
  CEMT SET FIL(USRSEC) CLO/OPE
```

### 6.3 File-by-File Technical Flow Diagrams

**File Interaction Matrix**:

| File / Dataset | Read By | Written By | Updated By | Deleted By |
|---------------|---------|-----------|-----------|-----------|
| ACCTDAT (Account) | COACTVWC, COACTUPC, CBTRN02C, CBACT04C, CBSTM03A, CBEXPORT | ACCTFILE.jcl (IDCAMS REPRO) | COACTUPC (REWRITE), CBTRN02C, CBACT04C | N/A |
| CARDDAT (Card) | COCRDLIC, COCRDSLC, COCRDUPC, COACTUPC, COACTVWC | CARDFILE.jcl (IDCAMS REPRO) | COCRDUPC (REWRITE) | N/A |
| CUSTDAT (Customer) | COACTUPC, COACTVWC, CBSTM03A, CBEXPORT | CUSTFILE.jcl (IDCAMS REPRO) | N/A (read-only in app) | N/A |
| CCXREF (Xref) | COACTUPC, CBTRN02C, CBACT04C, CBSTM03A, CBEXPORT | XREFFILE.jcl (IDCAMS REPRO) | N/A | N/A |
| TRANSACT (Trans) | COTRN00C, COTRN01C, CBTRN02C | COTRN02C (online), CBTRN02C (batch WRITE) | N/A | N/A |
| USRSEC (Security) | COSGN00C, COUSR00C | COUSR01C (WRITE) | COUSR02C (REWRITE) | COUSR03C (DELETE) |
| TCATBALF (Balance) | CBTRN02C, CBACT04C | CBTRN02C (WRITE new), TCATBALF.jcl | CBTRN02C (REWRITE) | N/A |
| DISCGRP (Disclosure) | CBACT04C | DISCGRP.jcl (IDCAMS REPRO) | N/A | N/A |
| DALYTRAN.PS (Daily) | CBTRN02C | External feed | N/A | N/A |
| DALYREJS (Rejects) | N/A | CBTRN02C | N/A | N/A |
| SYSTRAN (GDG) | COMBTRAN (SORT input) | CBACT04C | N/A | N/A |
| STATEMNT.PS/HTML | N/A | CBSTM03A | N/A | CREASTMT STEP030 (delete prior run) |

### 6.4 File-by-File Business Flow Diagrams

**Business Data Flow Summary**:

| Business Data | Source | File/Dataset | Consumer | Business Purpose |
|--------------|--------|-------------|----------|-----------------|
| Account Profiles | Initial data load | ACCTDAT | Online screens, Batch processing | Account management and balance tracking |
| Credit Cards | Initial data load | CARDDAT | Card list/search/update screens | Card lifecycle management |
| Customer Info | Initial data load | CUSTDAT | Account view/update screens, Statements | Customer identity and contact |
| Card-Account Xref | Initial data load | CCXREF | All programs needing card↔account lookup | Links cards to customers and accounts |
| Daily Transactions | External transaction feed | DALYTRAN.PS | POSTTRAN batch job | Daily credit card transactions for posting |
| Posted Transactions | POSTTRAN processing | TRANSACT | Online viewing, Statements, Reports | Permanent transaction record |
| Rejected Transactions | POSTTRAN validation | DALYREJS (GDG) | Operations review | Failed transactions for investigation |
| Interest Charges | INTCALC calculation | SYSTRAN (GDG) | COMBTRAN merge | System-generated interest/fee transactions |
| Account Statements | CREASTMT processing | STATEMNT.PS / STATEMNT.HTML | Customer distribution | Monthly/periodic account statements |
| User Credentials | Admin user management | USRSEC | Sign-on authentication | Application access control |
| Export Data | CBEXPORT processing | EXPORT.DATA | CBIMPORT at target | Branch migration data transfer |

### 6.5 Inter-System Interfaces

- **CICS-to-Batch Handoff**: The CLOSEFIL/OPENFIL jobs use SDSF to issue CEMT commands to the CICSAWSA region, closing/opening shared VSAM files. This is the standard z/OS pattern for file sharing between online and batch.
- **CICS Internal Reader**: The report program (CORPT00C) can submit the TRANREPT batch job from CICS using an internal reader, enabling on-demand report generation.
- **MQ-based Messaging** (Optional): The authorization module uses MQ for asynchronous request/response between external authorization systems and the CardDemo IMS/DB2 backend.
- **Control-M Scheduling**: Three job folders are defined:
  1. **DAILY-TransactionBackup**: CLOSEFIL → TRANBKP → WAITSTEP → OPENFIL (runs daily, all months)
  2. **WEEKLY-TransactionTypesDBRefresh**: MNTTRDB2 → TRANEXTR → TRANCATG → TRANTYPE (runs weekly, requires DB2 module)
  3. **MONTHLY-TransactionReporting**: POSTTRAN → INTCALC → COMBTRAN → CREASTMT → TRANREPT (runs monthly)

---

## 7. Security Implementation Details

### 7.1 RACF/ACF2/TopSecret Controls

- **Dataset-level Access**: Not explicitly defined in the application code; assumed to be controlled by the z/OS security product (RACF) at the dataset profile level.
- **Transaction-level Security**: CICS transaction definitions in the CSD do not specify explicit security settings beyond standard CICS region security.
- **Program Access**: All programs defined with `EXECKEY(USER)` in the CSD, running in user key.

### 7.2 Application-Level Security

- **User Authentication**: The sign-on program (COSGN00C) authenticates users by reading the USRSEC VSAM file and comparing the entered password against `SEC-USR-PWD`.
  - Passwords are stored in plaintext in the USRSEC file (PIC X(08))
  - Default credentials: ADMIN001/PASSWORD (admin), USER0001/PASSWORD (regular user)
- **Authorization Checks**: After sign-on, `CDEMO-USER-TYPE` ('A' or 'U') is set in the COMMAREA and checked by COMEN01C/COADM01C to route to the correct menu. Admin-only functions (user management, transaction type management) are only accessible through the admin menu.
- **Session Management**: Pseudo-conversational design with COMMAREA-based state. No explicit session timeout handling beyond CICS region configuration.

### 7.3 Encryption

- **Data-at-rest**: No application-level encryption. Passwords stored in plaintext.
- **Data-in-transit**: No application-level encryption; relies on infrastructure-level controls.

### 7.4 Audit Trail

- No explicit SMF recording or application audit logging is implemented in the base application.
- Transaction journaling is disabled for all files in the CSD (`JOURNAL(NO)`).

---

## 8. File and Database Design

### 8.1 VSAM Optimization

- **SHAREOPTIONS**: All files use DSNSHARING(ALLREQS) in the CSD, allowing multiple concurrent readers.
- **Buffer Allocation**: CSD defines DATABUFFERS(2) and INDEXBUFFERS(1) for all VSAM files — minimal buffering suitable for a demo application.
- **LSRPOOLNUM**: All files assigned to LSR Pool 1 for shared buffer management.
- **FREESPACE**: Not explicitly defined at the application level; dependent on IDCAMS DEFINE CLUSTER parameters used during file creation. The export file (CBEXPORT.jcl) specifies `FREESPACE(10 10)`.

### 8.2 DB2 Optimization (Optional Module)

- **Tables**: TRNTYPE (transaction types), TRNTYCAT (transaction categories), AUTHFRDS (authorization records)
- **DDL**: Available in `app/app-transaction-type-db2/ddl/` and `app/app-authorization-ims-db2-mq/ddl/`
- **Access Patterns**: DB2 programs (COTRTUPC, COTRTLIC, COBTUPDT) use standard embedded SQL with cursor-based browsing for list displays and single-row operations for updates.
- **Index Definitions**: Defined in DDL files (XTRNTYPE.ddl, XTRNTYCAT.ddl, XAUTHFRD.ddl)

### 8.3 Sequential File Processing

- **SORT**: Used in COMBTRAN (merge backup + system transactions by TRAN-ID) and CREASTMT (rekey transactions by card number)
- **GDG Management**: Three GDG bases defined:
  - `AWS.M2.CARDDEMO.DALYREJS` — rejected transaction generations
  - `AWS.M2.CARDDEMO.SYSTRAN` — system-generated interest/fee transactions
  - `AWS.M2.CARDDEMO.TRANSACT.COMBINED` — merged transaction file
  - `AWS.M2.CARDDEMO.TRANSACT.BKUP` — transaction backup generations
- **Record Formats**: FB (Fixed Block) used for all sequential files. LRECL varies: 80 (user security), 150 (card), 300 (account), 350 (transactions), 430 (rejects = 350 + 80-byte reason), 500 (customer/export).

---

## 9. Deployment and Promotion Procedures

### 9.1 Promotion Path

- Source code managed in GitHub repository (`choikh0423/aws-mainframe-modernization-carddemo`)
- Transfer to mainframe via FTP or file transfer tools
- Binary transfer mode required for EBCDIC data files in `app/data/EBCDIC/`
- Text transfer mode for source code (COBOL, JCL, copybooks, BMS maps)

### 9.2 JCL and Procedure Libraries

- **HLQ Convention**: `AWS.M2.CARDDEMO.*`
- **Dataset Layout**:
  - `AWS.M2.CARDDEMO.JCL` (FB 80) — Job Control Language members
  - `AWS.M2.CARDDEMO.PROC` (FB 80) — Cataloged procedures
  - `AWS.M2.CARDDEMO.CBL` (FB 80) — COBOL source
  - `AWS.M2.CARDDEMO.CPY` (FB 80) — Copybook library
  - `AWS.M2.CARDDEMO.BMS` (FB 80) — BMS map source
  - `AWS.M2.CARDDEMO.ASM` (FB 80) — Assembler source
  - `AWS.M2.CARDDEMO.MACLIB` (FB 80) — Macro library
  - `AWS.M2.CARDDEMO.LOADLIB` — Load library for compiled programs
- **Procedures**: `REPROC.prc` and `TRANREPT.prc` defined in `app/proc/`
- **GDG Bases**: Defined by DEFGDGB.jcl and DEFGDGD.jcl

### 9.3 CICS Resource Definitions

- **CSD Group**: CARDDEMO (defined in `app/csd/CARDDEMO.CSD`, 505 lines)
- **Library Definition**: COM2DOLL — DSNAME01(&HLQ..LOADLIB)
- **File Definitions**: 10 VSAM files (ACCTDAT, CARDDAT, CARDAIX, CCXREF, CUSTDAT, CXACAIX, TRANSACT, USRSEC, TCATBALF, DISCGRP) plus optional module files
- **Mapset Definitions**: 17 mapsets (COACTUP, COACTVW, COADM01, COBIL00, COCRDLI, COCRDSL, COCRDUP, COMEN01, CORPT00, COSGN00, COTRN00, COTRN01, COTRN02, COUSR00-03)
- **Program Definitions**: All programs defined with CONCURRENCY(QUASIRENT), API(CICSAPI), EXECUTIONSET(FULLAPI), CEDF(YES)
- **Transaction Definitions**: 20+ transactions (CC00, CM00, CAVW, CAUP, CCLI, CCDL, CCUP, CT00-02, CR00, CB00, CA00, CU00-03, plus optional module transactions)
- **Installation**: Use DFHCSDUP batch utility with the CSD file, or CEDA online transaction for manual definition

### 9.4 Environment Setup

**Initial Environment Setup Sequence**:
1. DUSRSECJ — Load user security VSAM
2. CLOSEFIL — Close CICS files
3. ACCTFILE — Load account data
4. CARDFILE — Load card data
5. CUSTFILE — Load customer data
6. XREFFILE — Load cross-reference
7. TRANFILE — Load initial transactions
8. DISCGRP — Load disclosure groups
9. TCATBALF — Load category balances
10. TRANCATG — Load transaction categories
11. TRANTYPE — Load transaction types
12. OPENFIL — Open CICS files
13. DEFGDGB — Define GDG bases
14. Optional: CREADB21 (DB2 setup), TRANEXTR (DB2 extract), DEFGDGD (DB2 GDG bases)

---

## 10. Operations and Runbooks

### 10.1 Batch Operations

**Daily Batch Cycle** (per Control-M DAILY-TransactionBackup folder):
1. CLOSEFIL — Close VSAM files in CICS (runs daily, all months)
2. TRANBKP — Backup transaction VSAM to GDG
3. WAITSTEP — Timed delay (uses COBSWAIT/MVSWAIT assembler)
4. OPENFIL — Reopen VSAM files in CICS

**Monthly Batch Cycle** (per Control-M MONTHLY-TransactionReporting folder):
1. POSTTRAN — Post daily transactions (CBTRN02C)
2. INTCALC — Calculate interest/fees (CBACT04C)
3. COMBTRAN — Merge backup + system transactions (SORT + IDCAMS)
4. CREASTMT — Generate statements in text and HTML (CBSTM03A)
5. TRANREPT — Generate transaction reports (CBTRN03C)

**Weekly Batch Cycle** (per Control-M WEEKLY-TransactionTypesDBRefresh folder, requires DB2):
1. MNTTRDB2 — Maintain transaction type DB2 table (COBTUPDT)
2. TRANEXTR — Extract DB2 data to sequential (DSNTIAUL)
3. TRANCATG — Load transaction categories to VSAM
4. TRANTYPE — Load transaction types to VSAM

**On-Demand**:
- CBEXPORT — Export customer data for branch migration
- CBIMPORT — Import customer data at target environment
- TRANREPT — Can be submitted from CICS via CORPT00C (internal reader)

### 10.2 Online Operations

- **CICS Region**: CICSAWSA
- **Startup**: Install CARDDEMO group using `CEDA INSTALL GROUP(CARDDEMO)`
- **Program Refresh**: `CEMT SET PROG(programname) NEWCOPY` after recompilation
- **File Management**: `CEMT SET FIL(filename) OPE/CLO/ENA/DIS`
- **Application Entry**: Transaction CC00 (sign-on screen)

### 10.3 Incident Response

| Abend/Error | Likely Cause | Resolution Steps |
|-------------|-------------|------------------|
| File Status 23 | Record not found (VSAM READ) | Verify key value; check if file was properly loaded |
| File Status 22 | Duplicate key on WRITE | Check for duplicate transaction IDs; review data integrity |
| File Status 35 | File not found / not defined | Verify CICS file definition; check CLOSEFIL/OPENFIL status |
| RESP NOTFND | CICS file read — record not found | Application handles gracefully with error message |
| RESP DUPREC | CICS file write — duplicate key | Application handles with error message |
| DALYREJS output | Transaction validation failure | Review reject file (430-byte records); correct source data |
| SORT RC 16 | Sort failure in COMBTRAN/CREASTMT | Check input file availability; verify SORT control statements |

### 10.4 Backup and Recovery

- **VSAM Backup**: TRANBKP job uses IDCAMS REPRO to backup transaction VSAM to GDG generation
- **GDG Rollback**: Previous transaction states available via GDG relative generation references
- **File Refresh**: All master files (account, card, customer, xref) can be reloaded from sequential PS datasets using the initialization JCL jobs

---

## 11. Monitoring and Observability

### 11.1 Batch Monitoring

- **Control-M Scheduling**: Jobs have MAXWAIT=7, MAXRERUN=5, AUTOARCH=1 configured
- **Job Dependencies**: Control-M INCOND/OUTCOND conditions enforce sequential execution within each folder
- **Output Verification**: Batch programs produce DISPLAY statements to SYSOUT showing record counts and processing statistics
- **DALYREJS Review**: Rejected transaction GDG generations should be reviewed after each POSTTRAN run

### 11.2 CICS Monitoring

- Standard CICS monitoring applies (CICS Statistics, CMF)
- All programs defined with CEDF(YES) enabling CICS Execution Diagnostic Facility for debugging
- Error messages displayed to users via ERRMSGO fields on BMS maps
- Programs use RESP code checking after all CICS commands

### 11.3 SMF and Logging

- No application-specific SMF recording configured
- Standard CICS journaling disabled (JOURNAL(NO) on all files)
- Batch programs output processing statistics via COBOL DISPLAY to SYSOUT

### 11.4 Alerting

- Control-M provides job-level alerting for failures and overruns
- No application-specific alerting beyond batch job completion status

---

## 12. Performance and Capacity Analysis

### 12.1 Batch Window Analysis

- **POSTTRAN**: Single-step job; performance depends on daily transaction volume
- **INTCALC**: Reads all category balances sequentially; O(n) where n = number of category balance records
- **COMBTRAN**: Uses SORT utility for merging — efficient for large volumes
- **CREASTMT**: Multi-step with SORT, IDCAMS, and COBOL processing; generates per-card statements

### 12.2 CICS Performance

- All transactions use pseudo-conversational design (minimal CICS resource consumption between interactions)
- CONCURRENCY(QUASIRENT) on all programs (standard single-threading per task)
- VSAM files share LSR Pool 1 buffers with minimal buffer allocation (DATABUFFERS(2), INDEXBUFFERS(1))

### 12.3 Capacity Planning

- **DASD**: VSAM files use standard allocation; GDG files accumulate with each batch run cycle
- **File Growth**: Transaction file (TRANSACT) grows with each daily posting cycle; COMBTRAN merges keep the active file current
- This is a demo application — capacity planning would need to be assessed based on actual data volumes in a production migration scenario

---

## 13. Testing Strategy and Test Cases

### 13.1 Unit Testing

**Sample Data Provided** (in `app/data/`):
- ASCII format: `app/data/ASCII/` — Human-readable text files for account, card, customer, transaction, and reference data
- EBCDIC format: `app/data/EBCDIC/` — Mainframe-native binary files for direct upload

**Key Test Scenarios**:
- Sign-on with valid/invalid credentials (ADMIN001/PASSWORD, USER0001/PASSWORD)
- Account view/update with data validation
- Card list browsing with pagination
- Transaction add with validation
- Bill payment processing
- Batch daily transaction posting (valid and reject scenarios)
- Interest calculation with disclosure group rate lookup
- Statement generation in text and HTML formats
- Export/import for branch migration

### 13.2 Integration Testing

- Full daily batch cycle: CLOSEFIL → POSTTRAN → INTCALC → COMBTRAN → CREASTMT → OPENFIL
- Online-to-batch: Submit report from CICS (CORPT00C) via internal reader
- DB2 optional module: Transaction type CRUD, batch extraction
- IMS/MQ optional module: Authorization request/response flow

### 13.3 Regression Testing

- Compare transaction file before/after POSTTRAN posting
- Verify reject file contents against known bad input
- Compare statement output against expected format
- Verify account balance updates after interest calculation

### 13.4 Performance Testing

- The application is a demo/reference system — performance testing would be relevant in the context of mainframe migration, comparing original mainframe performance against modernized target platform performance.

---

## 14. Known Issues and Technical Debt

### 14.1 Known Issues

| ID | Description | Impact | Workaround | Target Resolution |
|----|-------------|--------|-----------|-------------------|
| 1 | Passwords stored in plaintext in USRSEC VSAM | Security risk | N/A (demo application) | Implement hashed passwords for production use |
| 2 | ACCT-EXPIRAION-DATE field name typo (missing 'T') in CVACT01Y | Cosmetic / maintainability | None needed | Rename in modernization effort |
| 3 | CARD-EXPIRAION-DATE field name typo in CVACT02Y | Cosmetic / maintainability | None needed | Rename in modernization effort |
| 4 | No explicit session timeout handling | Users remain authenticated indefinitely within CICS | CICS region-level timeout configuration | Add application-level timeout logic |
| 5 | JOURNAL(NO) on all files — no transaction journaling | No point-in-time recovery capability | GDG backups provide generation-level recovery | Enable journaling for production files |

### 14.2 Technical Debt

| ID | Description | Risk | Effort | Priority |
|----|-------------|------|--------|----------|
| 1 | Monolithic COACTUPC program (4,236 lines) | Difficult to maintain and test | High | Medium |
| 2 | Copybook CSSETATY uses pseudo-macro pattern with placeholder variables (TESTVAR1, SCRNVAR2, MAPNAME3) requiring manual substitution | Error-prone maintenance | Medium | Low |
| 3 | Hardcoded HLQ (`AWS.M2.CARDDEMO`) in all JCL | Environment portability | Low (JCL parameterization) | Medium |
| 4 | Mixed coding styles (intentional for testing modernization tools) | Inconsistent code quality | Medium | Low (by design) |
| 5 | INTCALC uses hardcoded date in JCL PARM (`2022071800`) | Requires manual update for each run | Low (update PARM) | Low |
| 6 | Assembler programs (MVSWAIT, COBDATFT) with no COBOL equivalent | Platform-specific; harder to modernize | Medium | Medium |
| 7 | UNUSED1Y copybook present with no references | Dead code | Low (cleanup) | Low |

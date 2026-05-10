# CardDemo System Overview — Architecture, Flows & Business Logic

## Table of Contents

- [1. Repository Tree Diagram](#1-repository-tree-diagram)
- [2. System Architecture Overview](#2-system-architecture-overview)
  - [2.1 Technology Stack](#21-technology-stack)
  - [2.2 Application Modes](#22-application-modes)
  - [2.3 Data Architecture](#23-data-architecture)
- [3. Data Model — VSAM Files & Copybook Layouts](#3-data-model--vsam-files--copybook-layouts)
  - [3.1 Core Data Entities](#31-core-data-entities)
  - [3.2 Entity Relationship Diagram (Text)](#32-entity-relationship-diagram-text)
  - [3.3 Reference Data Entities](#33-reference-data-entities)
- [4. COMMAREA — Inter-Program Communication](#4-commarea--inter-program-communication)
- [5. Online (CICS) Transaction Flows](#5-online-cics-transaction-flows)
  - [5.1 High-Level Screen Navigation Flow](#51-high-level-screen-navigation-flow)
  - [5.2 CC00 — Sign-On (COSGN00C)](#52-cc00--sign-on-cosgn00c)
  - [5.3 CM00 — Main Menu (COMEN01C)](#53-cm00--main-menu-comen01c)
  - [5.4 CAVW — Account View (COACTVWC)](#54-cavw--account-view-coactvwc)
  - [5.5 CAUP — Account Update (COACTUPC)](#55-caup--account-update-coactupc)
  - [5.6 CCLI — Credit Card List (COCRDLIC)](#56-ccli--credit-card-list-cocrdlic)
  - [5.7 CCDL — Credit Card View (COCRDSLC)](#57-ccdl--credit-card-view-cocrdslc)
  - [5.8 CCUP — Credit Card Update (COCRDUPC)](#58-ccup--credit-card-update-cocrdupc)
  - [5.9 CT00 — Transaction List (COTRN00C)](#59-ct00--transaction-list-cotrn00c)
  - [5.10 CT01 — Transaction View (COTRN01C)](#510-ct01--transaction-view-cotrn01c)
  - [5.11 CT02 — Transaction Add (COTRN02C)](#511-ct02--transaction-add-cotrn02c)
  - [5.12 CR00 — Transaction Reports (CORPT00C)](#512-cr00--transaction-reports-corpt00c)
  - [5.13 CB00 — Bill Payment (COBIL00C)](#513-cb00--bill-payment-cobil00c)
  - [5.14 CA00 — Admin Menu (COADM01C)](#514-ca00--admin-menu-coadm01c)
  - [5.15 CU00 — User List (COUSR00C)](#515-cu00--user-list-cousr00c)
  - [5.16 CU01 — User Add (COUSR01C)](#516-cu01--user-add-cousr01c)
  - [5.17 CU02 — User Update (COUSR02C)](#517-cu02--user-update-cousr02c)
  - [5.18 CU03 — User Delete (COUSR03C)](#518-cu03--user-delete-cousr03c)
- [6. Batch Processing Flows](#6-batch-processing-flows)
  - [6.1 Daily Batch Cycle — End-to-End Flow](#61-daily-batch-cycle--end-to-end-flow)
  - [6.2 POSTTRAN — Transaction Posting (CBTRN02C)](#62-posttran--transaction-posting-cbtrn02c)
  - [6.3 INTCALC — Interest Calculation (CBACT04C)](#63-intcalc--interest-calculation-cbact04c)
  - [6.4 CREASTMT — Statement Generation (CBSTM03A / CBSTM03B)](#64-creastmt--statement-generation-cbstm03a--cbstm03b)
  - [6.5 TRANREPT — Transaction Report (CBTRN03C)](#65-tranrept--transaction-report-cbtrn03c)
  - [6.6 Data Reader Utilities](#66-data-reader-utilities)
  - [6.7 Branch Migration (CBEXPORT / CBIMPORT)](#67-branch-migration-cbexport--cbimport)
  - [6.8 Utility Programs](#68-utility-programs)
- [7. Optional Module Flows](#7-optional-module-flows)
  - [7.1 Pending Authorizations (IMS-DB2-MQ)](#71-pending-authorizations-ims-db2-mq)
  - [7.2 Transaction Type Management (DB2)](#72-transaction-type-management-db2)
  - [7.3 Account Extractions (MQ-VSAM)](#73-account-extractions-mq-vsam)
- [8. Security Model](#8-security-model)
- [9. Job Scheduling](#9-job-scheduling)
- [10. Complete Program Inventory](#10-complete-program-inventory)

---

## 1. Repository Tree Diagram

```
aws-mainframe-modernization-carddemo/
├── README.md                           # Project documentation
├── CODE_OF_CONDUCT.md
├── CONTRIBUTING.md
├── LICENSE / NOTICE
│
├── app/                                # ===== APPLICATION SOURCE =====
│   ├── cbl/                            # COBOL Programs (Online + Batch)
│   │   ├── COSGN00C.cbl                #   Sign-on screen (CC00)
│   │   ├── COMEN01C.cbl                #   Main menu (CM00)
│   │   ├── COACTVWC.cbl                #   Account view (CAVW)
│   │   ├── COACTUPC.cbl                #   Account update (CAUP)
│   │   ├── COCRDLIC.cbl                #   Credit card list (CCLI)
│   │   ├── COCRDSLC.cbl                #   Credit card view (CCDL)
│   │   ├── COCRDUPC.cbl                #   Credit card update (CCUP)
│   │   ├── COTRN00C.cbl                #   Transaction list (CT00)
│   │   ├── COTRN01C.cbl                #   Transaction view (CT01)
│   │   ├── COTRN02C.cbl                #   Transaction add (CT02)
│   │   ├── CORPT00C.cbl                #   Transaction reports (CR00)
│   │   ├── COBIL00C.cbl                #   Bill payment (CB00)
│   │   ├── COADM01C.cbl                #   Admin menu (CA00)
│   │   ├── COUSR00C.cbl                #   User list (CU00)
│   │   ├── COUSR01C.cbl                #   User add (CU01)
│   │   ├── COUSR02C.cbl                #   User update (CU02)
│   │   ├── COUSR03C.cbl                #   User delete (CU03)
│   │   ├── CBTRN01C.cbl                #   Batch: daily tran posting (step 1)
│   │   ├── CBTRN02C.cbl                #   Batch: daily tran posting (core)
│   │   ├── CBTRN03C.cbl                #   Batch: transaction detail report
│   │   ├── CBACT01C.cbl                #   Batch: read/write account file
│   │   ├── CBACT02C.cbl                #   Batch: read/print card data
│   │   ├── CBACT03C.cbl                #   Batch: read/print xref data
│   │   ├── CBACT04C.cbl                #   Batch: interest calculation
│   │   ├── CBCUS01C.cbl                #   Batch: read/print customer data
│   │   ├── CBSTM03A.CBL                #   Batch: statement generation (main)
│   │   ├── CBSTM03B.CBL                #   Batch: statement generation (sub)
│   │   ├── CBEXPORT.cbl                #   Batch: branch migration export
│   │   ├── CBIMPORT.cbl                #   Batch: branch migration import
│   │   ├── COBSWAIT.cbl                #   Batch: timer wait utility
│   │   └── CSUTLDTC.cbl                #   Utility: date validation (CEEDAYS)
│   │
│   ├── cpy/                            # Copybooks (shared data structures)
│   │   ├── COCOM01Y.cpy                #   COMMAREA layout
│   │   ├── CVACT01Y.cpy                #   Account record (300 bytes)
│   │   ├── CVACT02Y.cpy                #   Card record (150 bytes)
│   │   ├── CVACT03Y.cpy                #   Card cross-reference (50 bytes)
│   │   ├── CVCUS01Y.cpy                #   Customer record (500 bytes)
│   │   ├── CSUSR01Y.cpy                #   User security record (80 bytes)
│   │   ├── CVTRA01Y.cpy                #   Transaction category balance (50 bytes)
│   │   ├── CVTRA02Y.cpy                #   Disclosure group (50 bytes)
│   │   ├── CVTRA03Y.cpy                #   Transaction type (60 bytes)
│   │   ├── CVTRA04Y.cpy                #   Transaction category type (60 bytes)
│   │   ├── CVTRA05Y.cpy                #   Transaction record (350 bytes)
│   │   ├── CVTRA06Y.cpy                #   Daily transaction record (350 bytes)
│   │   ├── CVTRA07Y.cpy                #   Transaction report layout
│   │   ├── CVEXPORT.cpy                #   Export multi-record layout (500 bytes)
│   │   ├── COMEN02Y.cpy                #   Main menu option definitions
│   │   ├── COADM02Y.cpy                #   Admin menu option definitions
│   │   ├── COTTL01Y.cpy                #   Title line layout
│   │   ├── CSDAT01Y.cpy                #   Date formatting
│   │   ├── CSMSG01Y.cpy                #   Standard messages
│   │   ├── CSMSG02Y.cpy                #   Additional messages
│   │   ├── CSSETATY.cpy                #   Screen attribute settings
│   │   ├── CSSTRPFY.cpy                #   String processing functions
│   │   ├── CSLKPCDY.cpy                #   Lookup code definitions
│   │   ├── CSUTLDPY.cpy                #   Date utility parameter layout
│   │   ├── CSUTLDWY.cpy                #   Date utility work area
│   │   ├── CODATECN.cpy                #   Date conversion constants
│   │   ├── COSTM01.CPY                 #   Statement layout constants
│   │   ├── CUSTREC.cpy                 #   Customer record alternate layout
│   │   └── UNUSED1Y.cpy                #   Reserved/unused
│   │
│   ├── cpy-bms/                        # BMS Map Copybooks (generated)
│   │   ├── COSGN00.CPY ... COUSR03.CPY #   One per screen map
│   │
│   ├── bms/                            # BMS Screen Definitions
│   │   ├── COSGN00.bms                 #   Sign-on screen
│   │   ├── COMEN01.bms                 #   Main menu
│   │   ├── COACTVW.bms                 #   Account view
│   │   ├── COACTUP.bms                 #   Account update
│   │   ├── COCRDLI.bms                 #   Credit card list
│   │   ├── COCRDSL.bms                 #   Credit card detail
│   │   ├── COCRDUP.bms                 #   Credit card update
│   │   ├── COTRN00.bms                 #   Transaction list
│   │   ├── COTRN01.bms                 #   Transaction view
│   │   ├── COTRN02.bms                 #   Transaction add
│   │   ├── CORPT00.bms                 #   Report parameters
│   │   ├── COBIL00.bms                 #   Bill payment
│   │   ├── COADM01.bms                 #   Admin menu
│   │   ├── COUSR00.bms                 #   User list
│   │   ├── COUSR01.bms                 #   User add
│   │   ├── COUSR02.bms                 #   User update
│   │   └── COUSR03.bms                 #   User delete
│   │
│   ├── jcl/                            # JCL (Batch Job Control)
│   │   ├── CLOSEFIL.jcl                #   Close VSAM files in CICS
│   │   ├── OPENFIL.jcl                 #   Open VSAM files in CICS
│   │   ├── ACCTFILE.jcl                #   Refresh account master
│   │   ├── CARDFILE.jcl                #   Refresh card master
│   │   ├── CUSTFILE.jcl                #   Refresh customer master
│   │   ├── XREFFILE.jcl                #   Load cross-reference
│   │   ├── TRANFILE.jcl                #   Load transaction master
│   │   ├── DISCGRP.jcl                 #   Load disclosure groups
│   │   ├── TRANCATG.jcl                #   Load transaction categories
│   │   ├── TRANTYPE.jcl                #   Load transaction types
│   │   ├── TCATBALF.jcl                #   Load category balances
│   │   ├── DUSRSECJ.jcl                #   Load user security
│   │   ├── POSTTRAN.jcl                #   Post daily transactions
│   │   ├── INTCALC.jcl                 #   Interest calculation
│   │   ├── TRANBKP.jcl                 #   Backup transaction file
│   │   ├── COMBTRAN.jcl                #   Combine transaction files (SORT)
│   │   ├── CREASTMT.JCL                #   Statement generation
│   │   ├── TRANREPT.jcl                #   Transaction report
│   │   ├── TRANIDX.jcl                 #   Define alternate indexes
│   │   ├── DEFGDGB.jcl                 #   Define GDG bases
│   │   ├── DEFGDGD.jcl                 #   Define GDG bases (DB2)
│   │   ├── DALYREJS.jcl                #   Daily rejects
│   │   ├── PRTCATBL.jcl                #   Print category balances
│   │   ├── READACCT.jcl                #   Read account data
│   │   ├── READCARD.jcl                #   Read card data
│   │   ├── READCUST.jcl                #   Read customer data
│   │   ├── READXREF.jcl                #   Read cross-reference
│   │   ├── REPTFILE.jcl                #   Report file
│   │   ├── WAITSTEP.jcl                #   Wait step
│   │   ├── CBEXPORT.jcl                #   Branch export
│   │   ├── CBIMPORT.jcl                #   Branch import
│   │   ├── CBADMCDJ.jcl                #   Admin batch job
│   │   ├── DEFCUST.jcl                 #   Define customer file
│   │   ├── ESDSRRDS.jcl                #   Create ESDS/RRDS datasets
│   │   ├── FTPJCL.JCL                  #   FTP integration
│   │   ├── INTRDRJ1.JCL                #   Internal reader job 1
│   │   ├── INTRDRJ2.JCL                #   Internal reader job 2
│   │   └── TXT2PDF1.JCL                #   Text to PDF conversion
│   │
│   ├── csd/                            # CICS Resource Definitions
│   │   └── CARDDEMO.CSD                #   File, program, transaction, mapset defs
│   │
│   ├── scheduler/                      # Job Scheduling
│   │   ├── CardDemo.controlm            #   Control-M XML job definitions
│   │   └── CardDemo.ca7                 #   CA-7 job scheduling definitions
│   │
│   ├── data/                           # Sample Data
│   │   ├── ASCII/                       #   Text-readable samples
│   │   │   ├── acctdata.txt             #   Account data
│   │   │   ├── carddata.txt             #   Card data
│   │   │   ├── custdata.txt             #   Customer data
│   │   │   ├── cardxref.txt             #   Cross-reference data
│   │   │   ├── dailytran.txt            #   Daily transactions
│   │   │   ├── trantype.txt             #   Transaction types
│   │   │   ├── trancatg.txt             #   Transaction categories
│   │   │   ├── tcatbal.txt              #   Category balances
│   │   │   └── discgrp.txt              #   Disclosure groups
│   │   └── EBCDIC/                      #   Mainframe-native binary data
│   │       ├── AWS.M2.CARDDEMO.ACCTDATA.PS
│   │       ├── AWS.M2.CARDDEMO.CARDDATA.PS
│   │       ├── AWS.M2.CARDDEMO.CUSTDATA.PS
│   │       ├── AWS.M2.CARDDEMO.CARDXREF.PS
│   │       ├── AWS.M2.CARDDEMO.DALYTRAN.PS
│   │       ├── AWS.M2.CARDDEMO.DALYTRAN.PS.INIT
│   │       ├── AWS.M2.CARDDEMO.TRANTYPE.PS
│   │       ├── AWS.M2.CARDDEMO.TRANCATG.PS
│   │       ├── AWS.M2.CARDDEMO.TCATBALF.PS
│   │       ├── AWS.M2.CARDDEMO.DISCGRP.PS
│   │       ├── AWS.M2.CARDDEMO.USRSEC.PS
│   │       ├── AWS.M2.CARDDEMO.ACCDATA.PS
│   │       └── AWS.M2.CARDDEMO.EXPORT.DATA.PS
│   │
│   ├── app-authorization-ims-db2-mq/   # Optional: Pending Authorizations
│   │   ├── cbl/                         #   COBOL programs (COPAUS0C, etc.)
│   │   ├── bms/                         #   Screen maps
│   │   ├── cpy/ & cpy-bms/             #   Copybooks
│   │   ├── ims/                         #   IMS DBD/PSB definitions
│   │   ├── dcl/ & ddl/                  #   DB2 table definitions
│   │   ├── jcl/                         #   Batch jobs
│   │   ├── csd/                         #   CICS resource definitions
│   │   └── data/                        #   IMS sample data
│   │
│   ├── app-transaction-type-db2/        # Optional: Transaction Type Mgmt
│   │   ├── cbl/                         #   COTRTLIC, COTRTUPC, COBTUPDT
│   │   ├── bms/ & cpy-bms/             #   Screen maps
│   │   ├── cpy/                         #   DB2 copybooks
│   │   └── csd/                         #   CICS resource definitions
│   │
│   └── app-vsam-mq/                    # Optional: Account Extractions
│       ├── cbl/                         #   CODATE01, COACCT01
│       └── csd/                         #   CICS resource definitions
│
├── diagrams/                           # Architecture diagrams (PNG/drawio)
│   ├── CARDDEMO-DataModel.drawio
│   ├── Application-Flow-User.png
│   ├── Application-Flow-Admin.png
│   ├── Signon-Screen.png
│   ├── Main-Menu.png
│   └── Admin-Menu.png
│
├── samples/                            # Sample runtime configurations
│   └── m2/                              #   Micro Focus / UniKix artifacts
│
└── scripts/                            # Utility scripts
```

---

## 2. System Architecture Overview

### 2.1 Technology Stack

| Layer | Technology | Purpose |
|:------|:-----------|:--------|
| **Language** | COBOL | All business logic (online and batch) |
| **Transaction Monitor** | CICS | Online transaction processing, screen I/O, pseudo-conversational |
| **Data Storage** | VSAM KSDS (with AIX) | Primary data persistence (indexed files) |
| **Batch Orchestration** | JCL | Job control, step execution, dataset allocation |
| **Screen Rendering** | BMS (Basic Mapping Support) | 3270 terminal screen definitions |
| **Security** | VSAM-based (USRSEC file) | User ID, password, and role management |
| **Job Scheduling** | Control-M / CA-7 | Automated batch job execution |
| **System Utilities** | IDCAMS, IEBGENER, SORT | VSAM management, file copy, sorting |
| **Assembler** | HLASM | MVSWAIT (timer), COBDATFT (date format) |
| **Optional: Database** | DB2 | Transaction type tables, authorization fraud logs |
| **Optional: Messaging** | MQ | Asynchronous request/response patterns |
| **Optional: Hierarchical DB** | IMS DB | Authorization master data |

### 2.2 Application Modes

```
┌──────────────────────────────────────────────────────────────────┐
│                     CardDemo Application                         │
├─────────────────────────────┬────────────────────────────────────┤
│    ONLINE (CICS)            │         BATCH (JCL)                │
│                             │                                    │
│  Pseudo-conversational      │  Sequential file processing        │
│  3270 terminal screens      │  Daily cycle jobs                  │
│  Real-time user interaction │  Overnight processing              │
│                             │                                    │
│  ┌─────────────────────┐    │  ┌──────────────────────────┐      │
│  │ User Functions      │    │  │ Transaction Posting      │      │
│  │ • Account View/Edit │    │  │ Interest Calculation     │      │
│  │ • Card Mgmt         │    │  │ Statement Generation     │      │
│  │ • Transactions      │    │  │ Report Printing          │      │
│  │ • Bill Payment      │    │  │ File Refresh/Backup      │      │
│  │ • Reports           │    │  │ Data Export/Import       │      │
│  └─────────────────────┘    │  └──────────────────────────┘      │
│  ┌─────────────────────┐    │                                    │
│  │ Admin Functions     │    │                                    │
│  │ • User CRUD         │    │                                    │
│  │ • Tran Type Mgmt*   │    │                                    │
│  └─────────────────────┘    │                                    │
│         * = Optional DB2    │                                    │
└─────────────────────────────┴────────────────────────────────────┘
```

### 2.3 Data Architecture

```
┌──────────────── VSAM KSDS Files ────────────────────────────┐
│                                                              │
│  ACCTDAT ─────── Account Master (CVACT01Y, 300 bytes)       │
│      Key: ACCT-ID (11 digits)                               │
│                                                              │
│  CARDDAT ─────── Card Master (CVACT02Y, 150 bytes)          │
│      Key: CARD-NUM (16 chars)                               │
│                                                              │
│  CUSTDAT ─────── Customer Master (CVCUS01Y, 500 bytes)      │
│      Key: CUST-ID (9 digits)                                │
│                                                              │
│  CCXREF ──────── Card Cross-Reference (CVACT03Y, 50 bytes)  │
│      Key: XREF-CARD-NUM (16 chars)                          │
│      AIX: CXACAIX (by XREF-ACCT-ID)                        │
│                                                              │
│  TRANSACT ────── Transaction Master (CVTRA05Y, 350 bytes)   │
│      Key: TRAN-ID (16 chars)                                │
│                                                              │
│  USRSEC ──────── User Security (CSUSR01Y, 80 bytes)         │
│      Key: SEC-USR-ID (8 chars)                              │
│                                                              │
│  DISCGRP ─────── Disclosure Groups (CVTRA02Y, 50 bytes)     │
│      Key: DIS-GROUP-KEY (compound)                          │
│                                                              │
│  TCATBALF ────── Category Balances (CVTRA01Y, 50 bytes)     │
│      Key: TRAN-CAT-KEY (compound)                           │
│                                                              │
│  TRANCATG ────── Transaction Categories (CVTRA04Y, 60 bytes)│
│      Key: TRAN-CAT-KEY (compound)                           │
│                                                              │
│  TRANTYPE ────── Transaction Types (CVTRA03Y, 60 bytes)     │
│      Key: TRAN-TYPE (2 chars)                               │
│                                                              │
└──────────────────────────────────────────────────────────────┘

┌──────────────── Sequential Files ───────────────────────────┐
│  DALYTRAN ────── Daily Transactions (CVTRA06Y, 350 bytes)   │
│  DALYREJS ────── Daily Rejects (posting failures)           │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. Data Model — VSAM Files & Copybook Layouts

### 3.1 Core Data Entities

#### Account Record (`CVACT01Y.cpy`) — 300 bytes, VSAM KSDS `ACCTDAT`
```
Field                    PIC             Offset   Description
─────────────────────────────────────────────────────────────────────
ACCT-ID                  9(11)           1-11     Primary key
ACCT-ACTIVE-STATUS       X(01)           12       Y=Active, N=Closed
ACCT-CURR-BAL            S9(10)V99       13-24    Current balance (signed)
ACCT-CREDIT-LIMIT        S9(10)V99       25-36    Credit limit
ACCT-CASH-CREDIT-LIMIT   S9(10)V99       37-48    Cash advance limit
ACCT-OPEN-DATE           X(10)           49-58    YYYY-MM-DD
ACCT-EXPIRAION-DATE      X(10)           59-68    Expiration date
ACCT-REISSUE-DATE        X(10)           69-78    Reissue date
ACCT-CURR-CYC-CREDIT     S9(10)V99      79-90    Current cycle credits
ACCT-CURR-CYC-DEBIT      S9(10)V99      91-102   Current cycle debits
ACCT-ADDR-ZIP            X(10)           103-112  Account ZIP code
ACCT-GROUP-ID            X(10)           113-122  Disclosure group ID
FILLER                   X(178)          123-300  Reserved
```

#### Card Record (`CVACT02Y.cpy`) — 150 bytes, VSAM KSDS `CARDDAT`
```
Field                    PIC             Description
─────────────────────────────────────────────────────────────────────
CARD-NUM                 X(16)           Primary key (card number)
CARD-ACCT-ID             9(11)           Linked account ID
CARD-CVV-CD              9(03)           Card verification value
CARD-EMBOSSED-NAME       X(50)           Name on card
CARD-EXPIRAION-DATE      X(10)           Expiration date
CARD-ACTIVE-STATUS       X(01)           Y=Active
FILLER                   X(59)           Reserved
```

#### Customer Record (`CVCUS01Y.cpy`) — 500 bytes, VSAM KSDS `CUSTDAT`
```
Field                    PIC             Description
─────────────────────────────────────────────────────────────────────
CUST-ID                  9(09)           Primary key
CUST-FIRST-NAME          X(25)           First name
CUST-MIDDLE-NAME         X(25)           Middle name
CUST-LAST-NAME           X(25)           Last name
CUST-ADDR-LINE-1/2/3     X(50) each     Address lines
CUST-ADDR-STATE-CD       X(02)           State code
CUST-ADDR-COUNTRY-CD     X(03)           Country code
CUST-ADDR-ZIP            X(10)           ZIP code
CUST-PHONE-NUM-1/2       X(15) each     Phone numbers
CUST-SSN                 9(09)           Social Security Number
CUST-GOVT-ISSUED-ID      X(20)           Government ID
CUST-DOB-YYYY-MM-DD      X(10)          Date of birth
CUST-EFT-ACCOUNT-ID      X(10)          EFT/bank account
CUST-PRI-CARD-HOLDER-IND X(01)          Primary holder flag
CUST-FICO-CREDIT-SCORE   9(03)          FICO score
FILLER                   X(168)          Reserved
```

#### Card Cross-Reference (`CVACT03Y.cpy`) — 50 bytes, VSAM KSDS `CCXREF`
```
Field                    PIC             Description
─────────────────────────────────────────────────────────────────────
XREF-CARD-NUM            X(16)           Primary key (card number)
XREF-CUST-ID             9(09)           Customer ID → CUSTDAT
XREF-ACCT-ID             9(11)           Account ID → ACCTDAT
FILLER                   X(14)           Reserved
```

#### Transaction Record (`CVTRA05Y.cpy`) — 350 bytes, VSAM KSDS `TRANSACT`
```
Field                    PIC             Description
─────────────────────────────────────────────────────────────────────
TRAN-ID                  X(16)           Primary key (system-generated)
TRAN-TYPE-CD             X(02)           Transaction type code → TRANTYPE
TRAN-CAT-CD              9(04)           Transaction category → TRANCATG
TRAN-SOURCE              X(10)           Source system/channel
TRAN-DESC                X(100)          Transaction description
TRAN-AMT                 S9(09)V99       Amount (signed, 2 decimal)
TRAN-MERCHANT-ID         9(09)           Merchant identifier
TRAN-MERCHANT-NAME       X(50)           Merchant name
TRAN-MERCHANT-CITY       X(50)           Merchant city
TRAN-MERCHANT-ZIP        X(10)           Merchant ZIP
TRAN-CARD-NUM            X(16)           Card used → CCXREF
TRAN-ORIG-TS             X(26)           Origination timestamp
TRAN-PROC-TS             X(26)           Processing timestamp
FILLER                   X(20)           Reserved
```

#### User Security Record (`CSUSR01Y.cpy`) — 80 bytes, VSAM KSDS `USRSEC`
```
Field                    PIC             Description
─────────────────────────────────────────────────────────────────────
SEC-USR-ID               X(08)           Primary key (user ID)
SEC-USR-FNAME            X(20)           First name
SEC-USR-LNAME            X(20)           Last name
SEC-USR-PWD              X(08)           Password (plaintext)
SEC-USR-TYPE             X(01)           A=Admin, U=Regular user
SEC-USR-FILLER           X(23)           Reserved
```

### 3.2 Entity Relationship Diagram (Text)

```
                           ┌─────────────┐
                           │  CUSTOMER   │
                           │  (CUSTDAT)  │
                           │  Key:       │
                           │  CUST-ID    │
                           └──────┬──────┘
                                  │
                           1:N via XREF
                                  │
                           ┌──────┴──────┐
                           │  CARD XREF  │
                           │  (CCXREF)   │
                           │  Key:       │
                           │  CARD-NUM   │
                           │  Contains:  │
                           │  CUST-ID    │
                           │  ACCT-ID    │
                           └──┬───────┬──┘
                              │       │
                  FK: CARD-NUM│       │FK: ACCT-ID
                              │       │
                    ┌─────────┴─┐   ┌─┴──────────┐
                    │   CARD    │   │  ACCOUNT    │
                    │ (CARDDAT) │   │ (ACCTDAT)   │
                    │ Key:      │   │ Key:        │
                    │ CARD-NUM  │   │ ACCT-ID     │
                    └─────────┬─┘   │ Contains:   │
                              │     │ GROUP-ID ───┼──┐
                              │     └─────────────┘  │
                   FK: CARD-NUM                      │
                              │                FK: GROUP-ID +
                    ┌─────────┴──────┐        TYPE-CD + CAT-CD
                    │  TRANSACTION   │               │
                    │  (TRANSACT)    │     ┌─────────┴──────┐
                    │  Key: TRAN-ID  │     │ DISCLOSURE GRP │
                    │  Contains:     │     │  (DISCGRP)     │
                    │  CARD-NUM      │     │  Key: compound │
                    │  TYPE-CD ──────┼──┐  │  INT-RATE      │
                    │  CAT-CD ───────┼─┐│  └────────────────┘
                    └────────────────┘ ││
                                       ││
                    ┌──────────────┐    ││   ┌──────────────────┐
                    │ TRAN TYPE    │◄───┘│   │ TRAN CAT BAL     │
                    │ (TRANTYPE)   │     │   │ (TCATBALF)       │
                    │ Key: TYPE-CD │     │   │ Key: ACCT-ID +   │
                    │ DESC         │     │   │      TYPE-CD +   │
                    └──────────────┘     │   │      CAT-CD      │
                    ┌──────────────┐     │   │ BAL (running)    │
                    │ TRAN CATG    │◄────┘   └──────────────────┘
                    │ (TRANCATG)   │
                    │ Key: TYPE-CD │
                    │    + CAT-CD  │
                    │ DESC         │
                    └──────────────┘
```

### 3.3 Reference Data Entities

#### Transaction Type (`CVTRA03Y.cpy`) — 60 bytes
```
TRAN-TYPE       X(02)    Code (e.g., "SA"=Sale, "CR"=Credit, "RE"=Return)
TRAN-TYPE-DESC  X(50)    Description
```

#### Transaction Category (`CVTRA04Y.cpy`) — 60 bytes
```
TRAN-TYPE-CD    X(02)    Transaction type code
TRAN-CAT-CD     9(04)    Category code within type
TRAN-CAT-DESC   X(50)    Category description
```

#### Disclosure Group (`CVTRA02Y.cpy`) — 50 bytes
```
DIS-ACCT-GROUP-ID  X(10)   Account group (from ACCT-GROUP-ID)
DIS-TRAN-TYPE-CD   X(02)   Transaction type
DIS-TRAN-CAT-CD    9(04)   Transaction category
DIS-INT-RATE       S9(04)V99  Interest rate for this combination
```

#### Transaction Category Balance (`CVTRA01Y.cpy`) — 50 bytes
```
TRANCAT-ACCT-ID    9(11)   Account ID
TRANCAT-TYPE-CD    X(02)   Transaction type
TRANCAT-CD         9(04)   Category code
TRAN-CAT-BAL       S9(09)V99  Running balance for this combo
```

---

## 4. COMMAREA — Inter-Program Communication

All CICS programs share state via `CARDDEMO-COMMAREA` (defined in `COCOM01Y.cpy`):

```
CARDDEMO-COMMAREA
├── CDEMO-GENERAL-INFO
│   ├── CDEMO-FROM-TRANID       X(04)   Calling transaction ID
│   ├── CDEMO-FROM-PROGRAM      X(08)   Calling program name
│   ├── CDEMO-TO-TRANID         X(04)   Target transaction ID
│   ├── CDEMO-TO-PROGRAM        X(08)   Target program name
│   ├── CDEMO-USER-ID           X(08)   Authenticated user ID
│   ├── CDEMO-USER-TYPE         X(01)   A=Admin, U=User
│   └── CDEMO-PGM-CONTEXT       9(01)   0=First entry, 1=Re-entry
│
├── CDEMO-CUSTOMER-INFO
│   ├── CDEMO-CUST-ID           9(09)   Current customer context
│   ├── CDEMO-CUST-FNAME        X(25)
│   ├── CDEMO-CUST-MNAME        X(25)
│   └── CDEMO-CUST-LNAME        X(25)
│
├── CDEMO-ACCOUNT-INFO
│   ├── CDEMO-ACCT-ID           9(11)   Current account context
│   └── CDEMO-ACCT-STATUS       X(01)
│
├── CDEMO-CARD-INFO
│   └── CDEMO-CARD-NUM          9(16)   Current card context
│
├── CDEMO-MORE-INFO
│   ├── CDEMO-LAST-MAP          X(07)   Last BMS map sent
│   └── CDEMO-LAST-MAPSET       X(07)   Last BMS mapset
│
└── [Transaction-specific extensions appended after COCOM01Y]
    Examples:
    ├── CDEMO-CT00-INFO (Transaction List paging)
    ├── CDEMO-CT01-INFO (Transaction View context)
    ├── CDEMO-CT02-INFO (Transaction Add context)
    ├── CDEMO-CB00-INFO (Bill Payment context)
    ├── CDEMO-CU00-INFO (User List paging)
    ├── CDEMO-CU02-INFO (User Update context)
    └── CDEMO-CU03-INFO (User Delete context)
```

The COMMAREA is passed between transactions via `EXEC CICS RETURN TRANSID(...) COMMAREA(...)`. This enables pseudo-conversational programming: CICS frees the task between user interactions, preserving context only in the COMMAREA.

---

## 5. Online (CICS) Transaction Flows

### 5.1 High-Level Screen Navigation Flow

```
                                ┌──────────────┐
                                │   CC00       │
                                │  SIGN-ON     │
                                │ (COSGN00C)   │
                                └──────┬───────┘
                                       │ Auth OK
                            ┌──────────┴──────────┐
                            │                     │
                     User Type = 'U'        User Type = 'A'
                            │                     │
                     ┌──────┴──────┐       ┌──────┴──────┐
                     │   CM00      │       │   CA00      │
                     │ MAIN MENU   │       │ ADMIN MENU  │
                     │(COMEN01C)   │       │(COADM01C)   │
                     └──────┬──────┘       └──────┬──────┘
                            │                     │
          ┌────┬────┬───┬───┼───┬────┬─────┐      ├──── CU00 User List
          │    │    │   │   │   │    │     │      ├──── CU01 User Add
         CAVW CAUP CCLI│  CT00 CR00 CB00  │      ├──── CU02 User Update
         Acct Acct Card│  Tran Rpt  Bill  │      ├──── CU03 User Delete
         View Upd  List│  List      Pay   │      ├──── CTLI* Tran Type List
              │    │   │   │              │      └──── CTTU* Tran Type Upd
              │    │  CCDL CT01           │
              │    │  Card Tran           CPVS* Pending Auth
              │    │  View View           │     Summary
              │    │   │    │            CPVD* Pending Auth
              │    │  CCUP CT02          │     Details
              │    │  Card Tran
              │    │  Upd  Add       * = Optional modules
              │    │
              │    └── Each card screen reads CARDDAT, CCXREF
              └── Account screens read ACCTDAT, CUSTDAT
```

**Navigation rules:**
- **PF3** always returns to the previous screen / parent menu
- **Enter** processes the current action (submit, select, navigate)
- All screens share the same COMMAREA to maintain session context
- The user type (Admin/Regular) determines which menu loads after sign-on

---

### 5.2 CC00 — Sign-On (`COSGN00C`)

**Transaction ID:** `CC00`  
**BMS Map:** `COSGN00`  
**Program:** `COSGN00C.cbl` (260 lines)  
**VSAM File:** `USRSEC` (read)

#### Flow Diagram
```
User enters CC00 transaction
          │
          ▼
┌─────────────────────┐
│ First call?         │──Yes──► Display empty sign-on screen
│ (EIBCALEN = 0)      │         (SEND MAP COSGN0A)
└─────────┬───────────┘
          │ No (re-entry)
          ▼
┌─────────────────────┐
│ Which key pressed?  │
├─────────────────────┤
│ ENTER               │──► PROCESS-ENTER-KEY
│ PF3                 │──► Display "Thank You" → End session
│ Other               │──► Error: "Invalid key pressed"
└─────────────────────┘
          │
          ▼
    PROCESS-ENTER-KEY
          │
          ▼
┌─────────────────────┐
│ Validate inputs     │
│ • User ID blank?    │──Yes──► Error: "Please enter User ID"
│ • Password blank?   │──Yes──► Error: "Please enter Password"
└─────────┬───────────┘
          │ Both provided
          ▼
┌─────────────────────────┐
│ EXEC CICS READ          │
│   FILE('USRSEC')        │
│   RIDFLD(WS-USER-ID)    │
│   INTO(SEC-USER-DATA)   │
└─────────┬───────────────┘
          │
     ┌────┴────┐
  NOTFND     NORMAL
     │         │
  Error:      ▼
  "User ID   ┌─────────────────┐
  not found" │ Password match? │
             │ PASSWDI =       │
             │ SEC-USR-PWD?    │
             └───┬─────────┬───┘
                 │         │
              No │         │ Yes
                 ▼         ▼
           Error:     ┌────────────────┐
           "Wrong     │ Set COMMAREA:  │
           password"  │ USER-ID        │
                      │ USER-TYPE      │
                      │ (A or U)       │
                      └───────┬────────┘
                              │
                    ┌─────────┴─────────┐
                    │ A (Admin)         │ U (User)
                    ▼                   ▼
              XCTL to              XCTL to
              COADM01C (CA00)      COMEN01C (CM00)
```

#### Business Logic
1. **Authentication**: Simple VSAM lookup — user ID is the record key, password is compared byte-for-byte
2. **Role routing**: `SEC-USR-TYPE = 'A'` routes to Admin Menu, `'U'` routes to User Main Menu
3. **Session state**: User ID and type are stored in COMMAREA and persist for the entire session
4. **No lockout**: No failed-attempt counter or account locking mechanism

---

### 5.3 CM00 — Main Menu (`COMEN01C`)

**Transaction ID:** `CM00`  
**BMS Map:** `COMEN01`  
**Program:** `COMEN01C.cbl` (308 lines)

#### Flow Diagram
```
COMEN01C receives control
          │
          ▼
┌────────────────────┐
│ No COMMAREA?       │──Yes──► XCTL back to COSGN00C
│ (EIBCALEN = 0)     │
└─────────┬──────────┘
          │ Has COMMAREA
          ▼
┌────────────────────┐
│ First entry?       │──Yes──► Display menu with 11 options
│ (PGM-CONTEXT = 0)  │         Populate from COMEN02Y table
└─────────┬──────────┘
          │ Re-entry
          ▼
    Read user's option number
          │
          ▼
┌──────────────────────────────────────────┐
│ Menu Options (from COMEN02Y.cpy):       │
│                                          │
│  1. Account View        → COACTVWC      │
│  2. Account Update      → COACTUPC      │
│  3. Credit Card List    → COCRDLIC      │
│  4. Credit Card View    → COCRDSLC      │
│  5. Credit Card Update  → COCRDUPC      │
│  6. Transaction List    → COTRN00C      │
│  7. Transaction View    → COTRN01C      │
│  8. Transaction Add     → COTRN02C      │
│  9. Transaction Reports → CORPT00C      │
│ 10. Bill Payment        → COBIL00C      │
│ 11. Pending Auth View*  → COPAUS0C      │
│                         * = Optional     │
└──────────────────────────────────────────┘
          │
          ▼
    Validate option number (1-11)
          │
     ┌────┴────┐
  Invalid    Valid
     │         │
  Error:      ▼
  "Invalid   Set COMMAREA:
  option"    CDEMO-TO-PROGRAM = selected program
             CDEMO-FROM-PROGRAM = 'COMEN01C'
             │
             ▼
         EXEC CICS XCTL PROGRAM(target)
```

#### Business Logic
- Menu options are defined as a table in copybook `COMEN02Y.cpy` with option number, description, target program name, and user type filter
- The program validates the option is numeric and within range
- PF3 returns to the sign-on screen (effectively a logout)
- Each option stores the target program name in COMMAREA and transfers control

---

### 5.4 CAVW — Account View (`COACTVWC`)

**Transaction ID:** `CAVW`  
**BMS Map:** `COACTVW`  
**Program:** `COACTVWC.cbl` (941 lines)  
**VSAM Files:** `ACCTDAT` (read), `CUSTDAT` (read), `CCXREF` / `CXACAIX` (read)

#### Flow Diagram
```
COACTVWC receives control
          │
          ▼
┌────────────────────────┐
│ Account ID in COMMAREA?│
│ or entered on screen?  │
└─────────┬──────────────┘
          │
          ▼
┌──────────────────────────┐
│ READ ACCTDAT             │
│ Key: ACCT-ID             │
│                          │
│ Returns: balance, limits,│
│ dates, status, group-id  │
└─────────┬────────────────┘
          │
          ▼
┌──────────────────────────┐
│ Browse CCXREF (via AIX)  │
│ by ACCT-ID               │
│ → Get XREF-CUST-ID      │
└─────────┬────────────────┘
          │
          ▼
┌──────────────────────────┐
│ READ CUSTDAT             │
│ Key: CUST-ID             │
│                          │
│ Returns: name, address,  │
│ phone, SSN, FICO score   │
└─────────┬────────────────┘
          │
          ▼
    Display all fields on
    COACTVW screen (read-only)
```

#### Business Logic
- Joins three VSAM files: Account → XREF (by alternate index on ACCT-ID) → Customer
- Displays account financial details: current balance, credit limit, cash credit limit, cycle credits/debits
- Displays linked customer information: name, address, contact details
- Read-only — no update capability from this screen
- PF3 returns to main menu

---

### 5.5 CAUP — Account Update (`COACTUPC`)

**Transaction ID:** `CAUP`  
**BMS Map:** `COACTUP`  
**Program:** `COACTUPC.cbl` (4,236 lines — largest program)  
**VSAM Files:** `ACCTDAT` (read/update), `CUSTDAT` (read/update), `CCXREF` / `CXACAIX` (read), `CARDDAT` (read)

#### Flow Diagram
```
COACTUPC receives control
          │
          ▼
    Same initial read as Account View
    (ACCTDAT → XREF → CUSTDAT)
          │
          ▼
    Display editable form with:
    • Account fields (status, limits, dates)
    • Customer fields (name, address, phone, SSN)
          │
          ▼
    User modifies fields and presses ENTER
          │
          ▼
┌────────────────────────────┐
│ EXTENSIVE INPUT VALIDATION │
│                            │
│ • Alpha-only fields        │
│ • Alphanumeric fields      │
│ • Signed number fields     │
│ • US phone format          │
│ • Yes/No fields            │
│ • Mandatory field checks   │
│ • Date validation          │
│   (calls CSUTLDTC for     │
│    CEEDAYS validation)     │
│ • Credit limit ≥ balance   │
│ • State code validation    │
└──────────┬─────────────────┘
           │
      ┌────┴────┐
   Errors?    All OK
      │         │
   Display     ▼
   error    ┌───────────────────────┐
   messages │ EXEC CICS READ UPDATE │
            │   FILE('ACCTDAT')     │
            │ Modify fields         │
            │ EXEC CICS REWRITE     │
            └───────────┬───────────┘
                        │
                        ▼
            ┌───────────────────────┐
            │ EXEC CICS READ UPDATE │
            │   FILE('CUSTDAT')     │
            │ Modify fields         │
            │ EXEC CICS REWRITE     │
            └───────────┬───────────┘
                        │
                        ▼
                 Display "Update
                 Successful" message
```

#### Business Logic
- **Most complex program** in the system (4,236 lines)
- Performs comprehensive field-level validation with error highlighting
- Updates both Account and Customer records in separate VSAM REWRITE operations
- Validates dates using the `CSUTLDTC` subroutine (which calls IBM LE `CEEDAYS`)
- Validates US phone number format: `(NNN)NNN-NNNN`
- Cross-validates credit limits against current balance
- Uses BMS attribute byte manipulation for field highlighting on errors

---

### 5.6 CCLI — Credit Card List (`COCRDLIC`)

**Transaction ID:** `CCLI`  
**BMS Map:** `COCRDLI`  
**Program:** `COCRDLIC.cbl` (1,459 lines)  
**VSAM Files:** `CARDDAT` (browse), `CCXREF` (read), `ACCTDAT` (read)

#### Flow Diagram
```
COCRDLIC receives control
          │
          ▼
┌───────────────────────────────┐
│ Admin user?                   │
│ (CDEMO-USER-TYPE = 'A')      │
├───────────────────────────────┤
│ Yes → Browse ALL cards        │
│ No  → Browse cards for the   │
│        ACCT-ID in COMMAREA   │
└───────────┬───────────────────┘
            │
            ▼
┌───────────────────────────┐
│ EXEC CICS STARTBR         │
│   FILE('CARDDAT')         │
│   (starting position)     │
│                           │
│ Loop: READNEXT up to      │
│   page-size records       │
│   For each card:          │
│   • Read CCXREF for       │
│     ACCT-ID               │
│   • Read ACCTDAT for      │
│     account status        │
│                           │
│ EXEC CICS ENDBR           │
└───────────┬───────────────┘
            │
            ▼
    Display paginated list:
    Card Number | Acct ID | Status
            │
      ┌─────┴──────┐
      │ User       │
      │ selects    │
      │ a card     │
      ▼            ▼
   PF7/PF8     Set CDEMO-CARD-NUM
   Page Up/     in COMMAREA
   Down         XCTL to COCRDSLC
                (Card View)
```

#### Business Logic
- Role-based filtering: admins see all cards, regular users see only cards linked to their account
- Paginated browsing using CICS STARTBR/READNEXT/ENDBR pattern
- Card selection stores the card number in COMMAREA for downstream screens
- Cross-references each card to its account for display enrichment

---

### 5.7 CCDL — Credit Card View (`COCRDSLC`)

**Transaction ID:** `CCDL`  
**BMS Map:** `COCRDSL`  
**Program:** `COCRDSLC.cbl` (887 lines)  
**VSAM Files:** `CARDDAT` (read), `CCXREF` (read), `ACCTDAT` (read)

#### Business Logic
- Displays full card details: number, embossed name, CVV, expiration, status
- Cross-references to show linked account and customer information
- Read-only display — use CCUP for modifications
- PF3 returns to card list

---

### 5.8 CCUP — Credit Card Update (`COCRDUPC`)

**Transaction ID:** `CCUP`  
**BMS Map:** `COCRDUP`  
**Program:** `COCRDUPC.cbl` (1,560 lines)  
**VSAM Files:** `CARDDAT` (read/update), `CCXREF` (read), `ACCTDAT` (read)

#### Flow Diagram
```
COCRDUPC receives control
          │
          ▼
    Read CARDDAT by CARD-NUM
    Read CCXREF for account info
    Read ACCTDAT for account details
          │
          ▼
    Display editable card form
          │
          ▼
    User modifies and presses ENTER
          │
          ▼
┌──────────────────────────┐
│ Validate changes:        │
│ • Card status (Y/N)      │
│ • Embossed name          │
│ • Expiration date        │
│   (future date check)    │
│ • Account ID exists?     │
└──────────┬───────────────┘
           │
      ┌────┴────┐
   Errors     OK → REWRITE CARDDAT
      │              │
   Redisplay      "Update Successful"
```

#### Business Logic
- Validates card number format and account cross-reference integrity
- Checks that expiration date is in the future
- Updates CARDDAT via EXEC CICS READ UPDATE / REWRITE pattern
- Maintains referential integrity with CCXREF

---

### 5.9 CT00 — Transaction List (`COTRN00C`)

**Transaction ID:** `CT00`  
**BMS Map:** `COTRN00`  
**Program:** `COTRN00C.cbl` (699 lines)  
**VSAM File:** `TRANSACT` (browse)

#### Flow Diagram
```
COTRN00C receives control
          │
          ▼
┌─────────────────────────┐
│ STARTBR TRANSACT file   │
│ Browse transactions     │
│ (paginated, 10/page)    │
│                         │
│ For each transaction:   │
│ • Format amount         │
│ • Format date           │
│ • Display summary line  │
└─────────┬───────────────┘
          │
          ▼
    Display list:
    Tran ID | Type | Amount | Date
          │
    ┌─────┴──────────┐
    │ User actions:  │
    │ Enter+select → │─── XCTL to CT01 (View)
    │ PF7/PF8     → │─── Page Up/Down
    │ PF3         → │─── Return to menu
    └────────────────┘
```

#### Business Logic
- Sequential browse of the TRANSACT VSAM file
- Paginated display with forward/backward navigation via PF7/PF8
- Tracks first and last transaction IDs per page for bi-directional paging
- Selection stores transaction ID in COMMAREA for CT01 detail view

---

### 5.10 CT01 — Transaction View (`COTRN01C`)

**Transaction ID:** `CT01`  
**BMS Map:** `COTRN01`  
**Program:** `COTRN01C.cbl` (330 lines)  
**VSAM File:** `TRANSACT` (read)

#### Business Logic
- Reads single transaction by TRAN-ID from COMMAREA
- Displays all fields: ID, type, category, source, description, amount, merchant details, card number, timestamps
- Read-only view — no update capability
- PF3 returns to transaction list (CT00)

---

### 5.11 CT02 — Transaction Add (`COTRN02C`)

**Transaction ID:** `CT02`  
**BMS Map:** `COTRN02`  
**Program:** `COTRN02C.cbl` (783 lines)  
**VSAM Files:** `TRANSACT` (write), `ACCTDAT` (read/update), `CCXREF` / `CXACAIX` (read)

#### Flow Diagram
```
COTRN02C receives control
          │
          ▼
    Display empty transaction form
          │
          ▼
    User enters: Card Number, Type,
    Category, Description, Amount,
    Merchant info
          │
          ▼
┌─────────────────────────────┐
│ Validate inputs:            │
│ • Card number exists in     │
│   CCXREF? → get ACCT-ID    │
│ • Account active?           │
│ • Transaction type valid?   │
│ • Amount numeric?           │
│ • Date valid?               │
│   (calls CSUTLDTC)         │
└──────────┬──────────────────┘
           │
      ┌────┴────┐
   Errors     OK
      │         │
   Redisplay   ▼
            ┌──────────────────────┐
            │ Generate TRAN-ID     │
            │ (system timestamp-   │
            │  based unique ID)    │
            │                      │
            │ Set TRAN-ORIG-TS     │
            │ Set TRAN-PROC-TS     │
            │                      │
            │ WRITE to TRANSACT    │
            │                      │
            │ UPDATE ACCTDAT:      │
            │   ACCT-CURR-BAL +=   │
            │     TRAN-AMT         │
            └──────────┬───────────┘
                       │
                       ▼
                 "Transaction Added
                  Successfully"
```

#### Business Logic
- **Cross-file validation**: Verifies card exists in CCXREF, retrieves account ID, checks account is active
- **Transaction ID generation**: Uses CICS ASKTIME to generate a unique 16-character transaction ID
- **Balance update**: Immediately updates account current balance in ACCTDAT (debit/credit depending on transaction type)
- **Date validation**: Calls `CSUTLDTC` subroutine for date format validation using IBM LE `CEEDAYS` API
- **Atomic operation**: Transaction write + account balance update performed in same CICS task

---

### 5.12 CR00 — Transaction Reports (`CORPT00C`)

**Transaction ID:** `CR00`  
**BMS Map:** `CORPT00`  
**Program:** `CORPT00C.cbl` (649 lines)  
**Mechanism:** Submits batch job via CICS extrapartition TDQ (Transient Data Queue)

#### Flow Diagram
```
CORPT00C receives control
          │
          ▼
    Display report parameters screen:
    • Monthly / Yearly / Custom
    • Start Date (YYYY-MM-DD)
    • End Date (YYYY-MM-DD)
          │
          ▼
    User selects report type & dates
          │
          ▼
┌────────────────────────────┐
│ Validate date inputs       │
│ (calls CSUTLDTC)          │
│ End date > Start date?     │
└──────────┬─────────────────┘
           │
           ▼
┌────────────────────────────────────┐
│ Build JCL dynamically:            │
│                                    │
│ //TRNRPT00 JOB 'TRAN REPORT',...  │
│ //STEP10 EXEC PROC=TRANREPT       │
│ //SYSIN DD *                       │
│ <start-date> <end-date>            │
│ /*                                 │
│                                    │
│ Write JCL records to               │
│ Extrapartition TDQ (INTRDR)        │
│ → Internal Reader picks up         │
│ → Submits CBTRN03C batch job       │
└────────────────────────────────────┘
           │
           ▼
    "Report Job Submitted Successfully"
```

#### Business Logic
- **Online-to-batch bridge**: Uses CICS extrapartition TDQ to write JCL to the internal reader
- **Dynamic JCL**: Constructs JCL on-the-fly with user-specified date range as SYSIN parameters
- **Three report types**: Monthly (auto-calculates month boundaries), Yearly, and Custom date range
- The actual report is generated by batch program `CBTRN03C` (see Section 6.5)

---

### 5.13 CB00 — Bill Payment (`COBIL00C`)

**Transaction ID:** `CB00`  
**BMS Map:** `COBIL00`  
**Program:** `COBIL00C.cbl` (572 lines)  
**VSAM Files:** `TRANSACT` (write), `ACCTDAT` (read/update), `CCXREF` / `CXACAIX` (read)

#### Flow Diagram
```
COBIL00C receives control
          │
          ▼
┌────────────────────────┐
│ Read account via XREF  │
│ (by ACCT-ID from       │
│  COMMAREA)             │
│                        │
│ Display:               │
│ • Account ID           │
│ • Current Balance      │
│ • "Pay Full Balance?"  │
│   [Y/N] confirmation   │
└─────────┬──────────────┘
          │
          ▼
    User confirms with 'Y'
          │
          ▼
┌────────────────────────────────────┐
│ 1. Generate new TRAN-ID           │
│    (CICS ASKTIME → unique ID)     │
│                                    │
│ 2. Create payment transaction:     │
│    TRAN-TYPE-CD = payment type     │
│    TRAN-AMT = -(ACCT-CURR-BAL)    │
│    TRAN-SOURCE = 'ONLINE'          │
│    TRAN-DESC = 'BILL PAYMENT'      │
│    WRITE to TRANSACT               │
│                                    │
│ 3. Update ACCTDAT:                 │
│    ACCT-CURR-BAL = 0               │
│    (balance zeroed out)            │
│                                    │
└────────────────────────────────────┘
          │
          ▼
    "Bill Payment Successful"
    Display updated balance ($0.00)
```

#### Business Logic
- **Full balance payment only** — pays the entire current balance in one operation
- **Two-step confirmation**: Displays balance, then requires 'Y' confirmation
- **Creates audit trail**: Writes a credit transaction record to TRANSACT with negative amount
- **Immediate balance update**: Sets account current balance to zero
- **Timestamp**: Uses CICS ASKTIME/FORMATTIME for transaction and processing timestamps

---

### 5.14 CA00 — Admin Menu (`COADM01C`)

**Transaction ID:** `CA00`  
**BMS Map:** `COADM01`  
**Program:** `COADM01C.cbl` (288 lines)

#### Menu Options (from `COADM02Y.cpy`)
```
1. User List (Security)              → COUSR00C
2. User Add (Security)               → COUSR01C
3. User Update (Security)            → COUSR02C
4. User Delete (Security)            → COUSR03C
5. Transaction Type List/Update*     → COTRTLIC (DB2)
6. Transaction Type Maintenance*     → COTRTUPC (DB2)
                                     * = Optional DB2 module
```

#### Business Logic
- Identical navigation pattern to Main Menu (COMEN01C) but with admin options
- Only accessible when `CDEMO-USER-TYPE = 'A'`
- Options 5-6 require the optional DB2 Transaction Type Management module

---

### 5.15 CU00 — User List (`COUSR00C`)

**Transaction ID:** `CU00`  
**BMS Map:** `COUSR00`  
**Program:** `COUSR00C.cbl` (695 lines)  
**VSAM File:** `USRSEC` (browse)

#### Business Logic
- Paginated browse of the USRSEC file (10 users per page)
- Displays: User ID, Full Name, User Type (Admin/Regular)
- User selection → stores in COMMAREA → navigates to User Update (CU02) or Delete (CU03)
- PF7/PF8 for paging, PF3 returns to admin menu

---

### 5.16 CU01 — User Add (`COUSR01C`)

**Transaction ID:** `CU01`  
**BMS Map:** `COUSR01`  
**Program:** `COUSR01C.cbl` (299 lines)  
**VSAM File:** `USRSEC` (write)

#### Flow Diagram
```
User enters: User ID, First Name,
Last Name, Password, Type (A/U)
          │
          ▼
┌─────────────────────────────┐
│ Validate:                   │
│ • All fields non-blank      │
│ • User ID not already in    │
│   USRSEC (duplicate check)  │
│ • Type = 'A' or 'U'         │
└──────────┬──────────────────┘
           │
           ▼
    EXEC CICS WRITE
      FILE('USRSEC')
      FROM(SEC-USER-DATA)
           │
           ▼
    "User Added Successfully"
```

#### Business Logic
- Checks for duplicate user IDs before writing
- Stores password in plaintext in the USRSEC file
- No password complexity validation
- Immediate availability — new user can log in right away

---

### 5.17 CU02 — User Update (`COUSR02C`)

**Transaction ID:** `CU02`  
**Program:** `COUSR02C.cbl` (414 lines)  
**VSAM File:** `USRSEC` (read/update)

#### Business Logic
- Reads existing user record for editing
- Allows modification of: First Name, Last Name, Password, User Type
- User ID is display-only (primary key cannot change)
- Uses EXEC CICS READ UPDATE / REWRITE pattern

---

### 5.18 CU03 — User Delete (`COUSR03C`)

**Transaction ID:** `CU03`  
**Program:** `COUSR03C.cbl` (359 lines)  
**VSAM File:** `USRSEC` (read/delete)

#### Flow Diagram
```
Display user details (read-only)
          │
          ▼
    "Confirm delete? (Y/N)"
          │
      ┌───┴───┐
      Y       N
      │       │
      ▼       ▼
  EXEC CICS  Return to
  DELETE     user list
  FILE('USRSEC')
      │
      ▼
  "User Deleted"
```

#### Business Logic
- Two-step confirmation: displays the full record first, then requires explicit 'Y'
- EXEC CICS DELETE removes the record from USRSEC
- No cascading delete of related data (user's transactions remain)

---

## 6. Batch Processing Flows

### 6.1 Daily Batch Cycle — End-to-End Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    DAILY BATCH CYCLE                             │
│                                                                  │
│  ┌──────────┐    ┌──────────┐                                   │
│  │ CLOSEFIL │───►│ File     │  Close VSAM files held by CICS    │
│  │ (IEFBR14)│    │ Refresh  │                                   │
│  └──────────┘    │ Jobs     │                                   │
│                  ├──────────┤                                   │
│                  │ ACCTFILE │  Reload Account Master from PS     │
│                  │ CARDFILE │  Reload Card Master from PS        │
│                  │ XREFFILE │  Reload Cross-Reference from PS    │
│                  │ CUSTFILE │  Reload Customer Master from PS    │
│                  │ TRANBKP  │  Backup Transaction file           │
│                  │ TRANCATG │  Refresh Transaction Categories    │
│                  │ TRANTYPE │  Refresh Transaction Types         │
│                  │ DISCGRP  │  Refresh Disclosure Groups         │
│                  │ TCATBALF │  Refresh Category Balances         │
│                  │ DUSRSECJ │  Refresh User Security             │
│                  └────┬─────┘                                   │
│                       │                                          │
│                       ▼                                          │
│  ┌─────────────────────────────────────┐                        │
│  │ POSTTRAN (CBTRN02C)                 │                        │
│  │ Core transaction posting:           │                        │
│  │ • Read daily transactions           │                        │
│  │ • Validate each transaction         │                        │
│  │ • Post to transaction master        │                        │
│  │ • Update account balances           │                        │
│  │ • Update category balances          │                        │
│  │ • Write rejects to DALYREJS         │                        │
│  └─────────────┬───────────────────────┘                        │
│                │                                                 │
│                ▼                                                 │
│  ┌─────────────────────────────────────┐                        │
│  │ INTCALC (CBACT04C)                  │                        │
│  │ Interest calculation:               │                        │
│  │ • Read category balances            │                        │
│  │ • Look up disclosure group rates    │                        │
│  │ • Calculate interest per category   │                        │
│  │ • Generate interest transactions    │                        │
│  │ • Update account balances           │                        │
│  └─────────────┬───────────────────────┘                        │
│                │                                                 │
│                ▼                                                 │
│  ┌─────────────────────────────────────┐                        │
│  │ TRANBKP                             │                        │
│  │ Backup updated transaction file     │                        │
│  └─────────────┬───────────────────────┘                        │
│                │                                                 │
│                ▼                                                 │
│  ┌─────────────────────────────────────┐                        │
│  │ COMBTRAN (SORT)                     │                        │
│  │ Merge system-generated transactions │                        │
│  │ with daily input transactions       │                        │
│  └─────────────┬───────────────────────┘                        │
│                │                                                 │
│                ▼                                                 │
│  ┌─────────────────────────────────────┐                        │
│  │ CREASTMT (CBSTM03A + CBSTM03B)     │                        │
│  │ Statement generation:               │                        │
│  │ • Read transactions by card         │                        │
│  │ • Group by customer/account         │                        │
│  │ • Generate plain text statements    │                        │
│  │ • Generate HTML statements          │                        │
│  └─────────────┬───────────────────────┘                        │
│                │                                                 │
│                ▼                                                 │
│  ┌──────────────────────────────┐  ┌────────────┐               │
│  │ TRANIDX (IDCAMS)            │  │ OPENFIL    │               │
│  │ Rebuild alternate indexes   │──►│ (IEFBR14)  │               │
│  │ on transaction file         │  │ Reopen     │               │
│  └──────────────────────────────┘  │ VSAM files │               │
│                                    │ for CICS   │               │
│  ┌──────────────────────────────┐  └────────────┘               │
│  │ WAITSTEP (COBSWAIT)         │                                │
│  │ Optional delay between jobs │                                │
│  └──────────────────────────────┘                                │
└──────────────────────────────────────────────────────────────────┘
```

---

### 6.2 POSTTRAN — Transaction Posting (`CBTRN02C`)

**JCL Job:** `POSTTRAN`  
**Program:** `CBTRN02C.cbl` (731 lines)  
**Input Files:** `DALYTRAN` (daily transactions, sequential), `XREFFILE` (indexed), `ACCTFILE` (indexed)  
**Output Files:** `TRANFILE` (transaction master, indexed), `TCATBALF` (category balance, indexed), `DALYREJS` (rejects, sequential)

#### Flow Diagram
```
CBTRN02C starts
      │
      ▼
Open all files (DALYTRAN, TRANFILE, XREFFILE, ACCTFILE, TCATBALF, DALYREJS)
      │
      ▼
┌──────────────────────────────────────────────────────┐
│ LOOP: Read next record from DALYTRAN                 │
│       │                                              │
│       ▼                                              │
│  ┌────────────────────────┐                          │
│  │ Validate transaction:  │                          │
│  │ • Card number exists   │                          │
│  │   in XREFFILE?         │─── No → Write to         │
│  │ • Account active in    │         DALYREJS (reject) │
│  │   ACCTFILE?            │                          │
│  │ • Amount valid?        │                          │
│  └──────────┬─────────────┘                          │
│             │ Yes (valid)                            │
│             ▼                                        │
│  ┌─────────────────────────────────────────┐         │
│  │ 1. Generate TRAN-ID (unique key)        │         │
│  │ 2. Set TRAN-PROC-TS = current timestamp │         │
│  │ 3. WRITE to TRANFILE (transaction       │         │
│  │    master VSAM KSDS)                    │         │
│  │                                         │         │
│  │ 4. UPDATE ACCTFILE:                     │         │
│  │    ACCT-CURR-BAL += TRAN-AMT            │         │
│  │    ACCT-CURR-CYC-DEBIT or CREDIT +=     │         │
│  │                                         │         │
│  │ 5. UPDATE TCATBALF:                     │         │
│  │    TRAN-CAT-BAL += TRAN-AMT             │         │
│  │    (by ACCT-ID + TYPE-CD + CAT-CD)      │         │
│  │    If no existing record → WRITE new    │         │
│  └─────────────────────────────────────────┘         │
│       │                                              │
│       ▼                                              │
│  Continue loop until EOF on DALYTRAN                 │
└──────────────────────────────────────────────────────┘
      │
      ▼
Close all files, display statistics
(records read, posted, rejected)
```

#### Business Logic
- **Core batch engine** — processes the day's transactions accumulated during online operations
- **Three-file update**: Each valid transaction updates the master transaction file, account balance, and category balance
- **Reject handling**: Invalid transactions (bad card, inactive account) are written to DALYREJS with reason codes
- **Category balance tracking**: Maintains running balances per account/type/category combination for interest calculation
- **Idempotency**: Each transaction gets a unique system-generated ID to prevent duplicate posting

---

### 6.3 INTCALC — Interest Calculation (`CBACT04C`)

**JCL Job:** `INTCALC`  
**Program:** `CBACT04C.cbl` (652 lines)  
**Input Files:** `TCATBALF` (category balances), `XREFFILE` (cross-reference), `DISCGRP` (disclosure/interest rates), `ACCTFILE` (accounts)  
**Output File:** `TRANSACT` (interest transactions written sequentially)

#### Flow Diagram
```
CBACT04C starts
      │
      ▼
Open files (TCATBALF, XREFFILE, ACCTFILE, DISCGRP, TRANSACT)
      │
      ▼
┌────────────────────────────────────────────────────────┐
│ LOOP: Read next record from TCATBALF (sequential)      │
│       │                                                │
│       ▼                                                │
│  ┌──────────────────────────────────────────────┐      │
│  │ For this ACCT-ID + TYPE-CD + CAT-CD combo:   │      │
│  │                                               │      │
│  │ 1. Read ACCTFILE by ACCT-ID                  │      │
│  │    → Get ACCT-GROUP-ID                       │      │
│  │                                               │      │
│  │ 2. Read DISCGRP by                           │      │
│  │    GROUP-ID + TYPE-CD + CAT-CD               │      │
│  │    → Get DIS-INT-RATE                        │      │
│  │                                               │      │
│  │ 3. Calculate interest:                        │      │
│  │    INTEREST = TRAN-CAT-BAL × DIS-INT-RATE    │      │
│  │              ÷ 1200 (monthly rate)            │      │
│  │                                               │      │
│  │ 4. If INTEREST > 0:                           │      │
│  │    • Create interest transaction record        │      │
│  │    • WRITE to TRANSACT file                   │      │
│  │    • Update ACCTFILE:                          │      │
│  │      ACCT-CURR-BAL += INTEREST                │      │
│  │    • Update TCATBALF:                          │      │
│  │      TRAN-CAT-BAL += INTEREST                 │      │
│  └──────────────────────────────────────────────┘      │
│       │                                                │
│  Continue until EOF on TCATBALF                        │
└────────────────────────────────────────────────────────┘
```

#### Business Logic
- **Interest rate lookup chain**: Category Balance → Account Group ID → Disclosure Group → Interest Rate
- **Per-category interest**: Calculates interest separately for each transaction type/category combination
- **Monthly rate**: Annual interest rate divided by 12
- **Self-posting**: Generates interest charge transactions that update the same account balance and category balance files
- **Disclosure groups**: Enable different interest rates for different transaction types (e.g., purchases vs. cash advances)

---

### 6.4 CREASTMT — Statement Generation (`CBSTM03A` / `CBSTM03B`)

**JCL Job:** `CREASTMT`  
**Programs:** `CBSTM03A.CBL` (924 lines, main) + `CBSTM03B.CBL` (230 lines, subroutine)  
**Input Files:** Transaction file (via `CBSTM03B`), XREF, Customer, Account  
**Output Files:** `STMTFILE` (plain text statements), `HTMLFILE` (HTML statements)

#### Business Logic
- **Two-format output**: Generates both plain text and HTML account statements
- **Subroutine pattern**: `CBSTM03A` calls `CBSTM03B` for all file I/O operations using a shared parameter area
- **Statement structure**: Groups transactions by card number within each account
- **Statement sections**: Header (customer name/address), account summary (balance, limit), transaction detail lines, page/account totals
- **Technical showcase features**: Uses COMP/COMP-3 variables, 2D arrays, ALTER/GO TO statements, mainframe control block addressing — intentionally exercises various COBOL constructs for modernization tooling testing
- **The `CBSTM03B` subroutine** handles: file open/close, keyed and sequential reads, writes, and rewrites — abstracted via an operation code parameter (`O`=Open, `C`=Close, `R`=Read, `K`=Keyed Read, `W`=Write, `Z`=Rewrite)

---

### 6.5 TRANREPT — Transaction Report (`CBTRN03C`)

**JCL Job:** `TRANREPT` (submitted from online via CR00 or run standalone)  
**Program:** `CBTRN03C.cbl` (649 lines)  
**Input Files:** `TRANFILE` (transactions), `CARDXREF` (cross-reference), `TRANTYPE` (types), `TRANCATG` (categories), `DATEPARM` (date parameters)  
**Output File:** `TRANREPT` (formatted report)

#### Flow Diagram
```
CBTRN03C starts
      │
      ▼
Read DATEPARM file:
    Start Date, End Date
      │
      ▼
┌──────────────────────────────────────────────┐
│ LOOP: Read TRANFILE sequentially             │
│   │                                          │
│   ▼                                          │
│ Is TRAN-ORIG-TS within date range?           │
│   │                                          │
│   ├── No → Skip                              │
│   │                                          │
│   ├── Yes:                                   │
│   │   1. Read CARDXREF → Get ACCT-ID        │
│   │   2. Read TRANTYPE → Get type desc      │
│   │   3. Read TRANCATG → Get category desc  │
│   │   4. Format report line                  │
│   │   5. Write to TRANREPT                   │
│   │   6. Accumulate page/account totals      │
│   │                                          │
│   ▼                                          │
│ Page break handling:                         │
│   Print headers, page totals, account totals │
└──────────────────────────────────────────────┘
```

#### Business Logic
- **Date-range filtering**: Only includes transactions within the specified date range (from DATEPARM or SYSIN)
- **Enrichment joins**: Cross-references each transaction to its account, type description, and category description
- **Report layout** (from `CVTRA07Y.cpy`): Transaction ID, Account ID, Type+Description, Category+Description, Source, Amount
- **Subtotals**: Page-level and account-level running totals with grand total at end

---

### 6.6 Data Reader Utilities

| Program | JCL Job | Function |
|:--------|:--------|:---------|
| `CBACT01C` | `READACCT` | Read account file, write to output with COMP-3 fields, arrays, and variable-length records |
| `CBACT02C` | `READCARD` | Read and display card data file sequentially |
| `CBACT03C` | `READXREF` | Read and display cross-reference data |
| `CBCUS01C` | `READCUST` | Read and display customer data file |

These are diagnostic/utility programs that dump file contents. `CBACT01C` also demonstrates advanced COBOL features:
- **OCCURS arrays**: Writes account balance data in an array structure
- **COMP-3 (packed decimal)**: Uses packed decimal for debit field
- **Variable-length records**: Writes VB format records with `RECORDING MODE IS V`
- **Multiple output formats**: Same data written in three formats (flat, array, variable-length)

---

### 6.7 Branch Migration (`CBEXPORT` / `CBIMPORT`)

**CBEXPORT.cbl** (582 lines) — Export all customer data into a multi-record export file for branch migration.

```
┌─────────────────────────────────────────────────┐
│ CBEXPORT reads ALL source files sequentially:   │
│ CUSTFILE → ACCTFILE → XREFFILE → TRANSACT →     │
│ CARDFILE                                         │
│                                                  │
│ For each source, writes export records with:     │
│ • Record type indicator (C/A/X/T/D)             │
│ • Timestamp                                      │
│ • Sequence number                                │
│ • Branch ID / Region code                        │
│ • Source-specific data (per CVEXPORT.cpy layout) │
│                                                  │
│ Uses REDEFINES for multiple record types in a    │
│ single 500-byte export record structure          │
│                                                  │
│ Generates processing statistics                  │
└─────────────────────────────────────────────────┘
```

**CBIMPORT.cbl** (487 lines) — Reverse of export: reads the multi-record export file and splits it back into normalized target files.

```
┌─────────────────────────────────────────────────┐
│ CBIMPORT reads the export file sequentially:    │
│                                                  │
│ For each record:                                │
│ 1. Check record type (C/A/X/T)                  │
│ 2. Route to appropriate output file             │
│ 3. Validate data integrity (checksums)          │
│ 4. Write to CUSTOUT/ACCTOUT/XREFOUT/TRNXOUT     │
│ 5. Track import statistics & errors             │
└─────────────────────────────────────────────────┘
```

#### Business Logic
- **Multi-record layout**: Uses `CVEXPORT.cpy` with REDEFINES to handle Customer, Account, Transaction, and Card XREF records in a single 500-byte structure
- **Data optimization**: Uses COMP and COMP-3 fields for numeric compression in export records
- **OCCURS DEPENDING ON**: Export copybook uses OCCURS clauses for address lines and phone numbers
- **Integrity validation**: Import validates checksums and sequence numbers
- **Migration use case**: Designed to move data between branches/regions of a credit card operation

---

### 6.8 Utility Programs

| Program | Function | Details |
|:--------|:---------|:--------|
| `CSUTLDTC` | Date validation | Calls IBM LE `CEEDAYS` API to validate dates against format masks. Used by online programs (COTRN02C, CORPT00C, COACTUPC) via CICS LINK |
| `COBSWAIT` | Timer wait | Reads centisecond value from SYSIN, calls assembler `MVSWAIT` to pause batch job execution |
| `CBTRN01C` | Daily tran posting (step 1) | Earlier version of transaction posting — reads DALYTRAN, validates against CUSTFILE/XREFFILE/CARDFILE/ACCTFILE, writes to TRANFILE |

---

## 7. Optional Module Flows

### 7.1 Pending Authorizations (IMS-DB2-MQ)

**Location:** `app/app-authorization-ims-db2-mq/`

```
┌───────────────────────────────────────────────────────┐
│ Authorization Request Flow:                           │
│                                                       │
│ External System ──MQ──► CP00 (COPAUA0C)              │
│                         │                             │
│                         ▼                             │
│                   Process authorization:              │
│                   • Read customer from IMS DB         │
│                   • Validate card/account             │
│                   • Check fraud rules                 │
│                   • Insert to IMS (pending auth)      │
│                   • Log to DB2 (fraud detection)      │
│                   • Send MQ response                  │
│                                                       │
│ CPVS (COPAUS0C) ── View pending auth summary         │
│                    (reads IMS + VSAM)                 │
│                                                       │
│ CPVD (COPAUS1C) ── View/process auth details         │
│                    (updates IMS, inserts DB2)         │
│                                                       │
│ CBPAUP0J (CBPAUP0C) ── Batch purge expired auths     │
└───────────────────────────────────────────────────────┘
```

**Technologies exercised:** IMS DB (hierarchical database), DB2 (relational), MQ (message queuing), CICS triggers

---

### 7.2 Transaction Type Management (DB2)

**Location:** `app/app-transaction-type-db2/`

```
┌───────────────────────────────────────────────────────┐
│ Online:                                               │
│ CTLI (COTRTLIC) ── List/update/delete tran types     │
│                    Uses DB2 cursors for browsing      │
│                    SQL DELETE for removal             │
│                                                       │
│ CTTU (COTRTUPC) ── Add/edit transaction types        │
│                    SQL INSERT/UPDATE on DB2 tables    │
│                                                       │
│ Batch:                                                │
│ MNTTRDB2 (COBTUPDT) ── Batch maintenance of         │
│                         transaction type table       │
│ TRANEXTR (DSNTIAUL)  ── Extract DB2 data to flat     │
│                         files for VSAM loading       │
└───────────────────────────────────────────────────────┘
```

**Technologies exercised:** DB2 SQL (cursors, INSERT, UPDATE, DELETE), DSNTIAUL utility, embedded SQL in COBOL

---

### 7.3 Account Extractions (MQ-VSAM)

**Location:** `app/app-vsam-mq/`

```
┌───────────────────────────────────────────────────────┐
│ CDRD (CODATE01) ── System date inquiry via MQ        │
│                    Demonstrates request/response      │
│                    pattern using MQ channels          │
│                                                       │
│ CDRA (COACCT01) ── Account details inquiry via MQ    │
│                    Reads VSAM, returns via MQ         │
│                    Demonstrates async processing      │
└───────────────────────────────────────────────────────┘
```

---

## 8. Security Model

```
┌────────────────────────────────────────────────────────┐
│                    SECURITY MODEL                      │
│                                                        │
│  Authentication:                                       │
│  ┌──────────────────────────────────┐                  │
│  │ USRSEC VSAM File                │                  │
│  │ • User ID (key)                 │                  │
│  │ • Password (plaintext, 8 chars) │                  │
│  │ • User Type (A=Admin, U=User)   │                  │
│  │ • First/Last Name               │                  │
│  └──────────────────────────────────┘                  │
│                                                        │
│  Authorization:                                        │
│  ┌──────────────────────────────────────┐              │
│  │ Role    │ Capabilities               │              │
│  ├─────────┼────────────────────────────┤              │
│  │ Admin   │ • All user functions       │              │
│  │ (A)     │ • User management (CRUD)   │              │
│  │         │ • Admin menu access        │              │
│  │         │ • Browse ALL cards         │              │
│  │         │ • Tran type mgmt (opt.)    │              │
│  ├─────────┼────────────────────────────┤              │
│  │ Regular │ • Account view/update      │              │
│  │ (U)     │ • Card list (own acct)     │              │
│  │         │ • Card view/update         │              │
│  │         │ • Transaction ops          │              │
│  │         │ • Bill payment             │              │
│  │         │ • Reports                  │              │
│  └─────────┴────────────────────────────┘              │
│                                                        │
│  Session Management:                                   │
│  • Pseudo-conversational (no persistent sessions)      │
│  • User context stored in COMMAREA between calls       │
│  • PF3 from main/admin menu → return to sign-on       │
│  • No session timeout (CICS-level timeout applies)     │
│                                                        │
│  Default credentials:                                  │
│  • Admin:   ADMIN001 / PASSWORD                        │
│  • User:    USER0001 / PASSWORD                        │
└────────────────────────────────────────────────────────┘
```

---

## 9. Job Scheduling

The `app/scheduler/` directory contains scheduling definitions for two enterprise job schedulers:

**Control-M** (`CardDemo.controlm`) — XML-based job dependency definitions with:
- Job ordering and dependencies
- File trigger conditions
- Success/failure actions
- Time-based scheduling windows

**CA-7** (`CardDemo.ca7`) — Mainframe-native scheduling definitions with:
- Job network definitions
- Predecessor/successor relationships
- Calendar-based triggers

Both define the same logical job flow as shown in Section 6.1 (Daily Batch Cycle).

---

## 10. Complete Program Inventory

### Online Programs (CICS)

| Program | Trans ID | Lines | Function | VSAM Files Used |
|:--------|:---------|------:|:---------|:----------------|
| COSGN00C | CC00 | 260 | Sign-on/Authentication | USRSEC (R) |
| COMEN01C | CM00 | 308 | Main Menu (Regular Users) | — |
| COADM01C | CA00 | 288 | Admin Menu | — |
| COACTVWC | CAVW | 941 | Account View | ACCTDAT (R), CUSTDAT (R), CCXREF (R) |
| COACTUPC | CAUP | 4,236 | Account Update | ACCTDAT (RW), CUSTDAT (RW), CCXREF (R), CARDDAT (R) |
| COCRDLIC | CCLI | 1,459 | Credit Card List | CARDDAT (B), CCXREF (R), ACCTDAT (R) |
| COCRDSLC | CCDL | 887 | Credit Card View | CARDDAT (R), CCXREF (R), ACCTDAT (R) |
| COCRDUPC | CCUP | 1,560 | Credit Card Update | CARDDAT (RW), CCXREF (R), ACCTDAT (R) |
| COTRN00C | CT00 | 699 | Transaction List | TRANSACT (B) |
| COTRN01C | CT01 | 330 | Transaction View | TRANSACT (R) |
| COTRN02C | CT02 | 783 | Transaction Add | TRANSACT (W), ACCTDAT (RW), CCXREF (R) |
| CORPT00C | CR00 | 649 | Report Submission | — (submits batch via TDQ) |
| COBIL00C | CB00 | 572 | Bill Payment | TRANSACT (W), ACCTDAT (RW), CCXREF (R) |
| COUSR00C | CU00 | 695 | User List | USRSEC (B) |
| COUSR01C | CU01 | 299 | User Add | USRSEC (W) |
| COUSR02C | CU02 | 414 | User Update | USRSEC (RW) |
| COUSR03C | CU03 | 359 | User Delete | USRSEC (RD) |

*Legend: R=Read, W=Write, RW=Read+Update, B=Browse, RD=Read+Delete*

### Batch Programs

| Program | JCL Job | Lines | Function | Key Input/Output |
|:--------|:--------|------:|:---------|:-----------------|
| CBTRN02C | POSTTRAN | 731 | Transaction Posting (core) | DALYTRAN → TRANFILE + ACCTFILE + TCATBALF |
| CBTRN01C | (alt post) | 494 | Transaction Posting (step 1) | DALYTRAN → TRANFILE (with card/cust validation) |
| CBACT04C | INTCALC | 652 | Interest Calculation | TCATBALF + DISCGRP → TRANSACT + ACCTFILE |
| CBSTM03A | CREASTMT | 924 | Statement Generation (main) | Transactions → STMTFILE + HTMLFILE |
| CBSTM03B | (sub) | 230 | Statement File I/O (sub) | Called by CBSTM03A |
| CBTRN03C | TRANREPT | 649 | Transaction Detail Report | TRANFILE → TRANREPT |
| CBACT01C | READACCT | 430 | Account File Reader | ACCTFILE → OUTFILE + ARRYFILE + VBRCFILE |
| CBACT02C | READCARD | 178 | Card File Reader | CARDFILE → display |
| CBACT03C | READXREF | 178 | XREF File Reader | XREFFILE → display |
| CBCUS01C | READCUST | 178 | Customer File Reader | CUSTFILE → display |
| CBEXPORT | CBEXPORT | 582 | Branch Migration Export | All masters → EXPFILE |
| CBIMPORT | CBIMPORT | 487 | Branch Migration Import | EXPFILE → normalized files |
| COBSWAIT | WAITSTEP | 41 | Timer Wait | SYSIN (centiseconds) → MVSWAIT |
| CSUTLDTC | (utility) | 157 | Date Validation | Calls CEEDAYS API |

### Total Code Volume
- **Online COBOL programs**: 17 programs, ~13,739 lines
- **Batch COBOL programs**: 14 programs, ~5,931 lines
- **Copybooks**: 30 files (data structures, menu definitions, utilities)
- **BMS Maps**: 17 screen definitions
- **JCL Jobs**: 38 job scripts
- **Grand total**: ~19,670 lines of COBOL + copybooks

---

*Document generated from source analysis of the `choikh0423/aws-mainframe-modernization-carddemo` repository.*

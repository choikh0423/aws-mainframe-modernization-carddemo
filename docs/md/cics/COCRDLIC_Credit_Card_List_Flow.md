# Credit Card List (CCLI) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CCLI |
| Entry Program | COCRDLIC |
| Business Domain | Credit Card Management |
| Owning Team | CardDemo Application Team |
| Design Pattern | Pseudo-conversational |
| Criticality | Medium |
| Response Time SLA | < 2 seconds |
| Peak Volume | N/A (demo application) |

---

## Table of Contents

1. Business Flow Analysis
2. Technical Flow Analysis
3. Screen Flow and BMS Maps
4. Program Inventory and Call Chain
5. File and Data Store Inventory
6. Data Flow Analysis
7. COMMAREA and Interface Contracts
8. Error Handling and User Feedback
9. Security and Authorization
10. Performance Characteristics
11. Testing Approach
12. Known Issues and Considerations

---

## 1. Business Flow Analysis

### 1.1 Business Context

- **What**: Displays a paginated list of credit cards from the CARDDAT VSAM file. Supports filtering by Account Number and/or Card Number. Users can select a card from the list to view its details or navigate to the update screen.
- **Who**: Any authenticated CardDemo user (regular users and admins) — menu option 3 ("Credit Card List").
- **When**: Real-time, on-demand during business hours when users need to browse or locate credit card records.
- **Why**: Provides the primary navigation mechanism for finding credit cards. Serves as the gateway to the Card Detail (COCRDSLC) and Card Update (COCRDUPC) screens.
- **What** is the business outcome: User views a paginated list of up to 7 cards per page, filtered by optional account/card criteria. From this list, user can select a single card for detail view or update.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  User selects Option 3 ("Credit Card List") from Main Menu
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ System Response          │  Display empty Credit Card List screen with filter fields
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 1            │  Optionally enter Account Number filter (11-digit numeric)
  └────────────┬─────────────┘  Optionally enter Card Number filter (16-digit numeric)
               │
               ▼
  ┌──────────────────────────┐
  │ System Response          │  Browse CARDDAT file, apply filters, display up to 7 cards
  └────────────┬─────────────┘  Show Account Number, Card Number, Active Status per row
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 2            │  Navigate pages (F7/F8) or select a card:
  └────────────┬─────────────┘  Type 'S' to view detail, 'U' to update
               │
           ┌───┴───┐
           │Decision│  Selection type?
           └───┬───┘
              ╱ ╲
         S  ╱     ╲ U
           ▼       ▼
  ┌──────────────┐  ┌──────────────┐
  │ XCTL to      │  │ XCTL to      │
  │ COCRDSLC     │  │ COCRDUPC     │
  │ (Card Detail)│  │ (Card Update)│
  └──────────────┘  └──────────────┘
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | Account filter must be numeric | Account Number supplied but not numeric | Error: "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER" |
| BR-002 | Card filter must be numeric | Card Number supplied but not numeric | Error: "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" |
| BR-003 | Only one record selection allowed | More than one 'S' or 'U' entered across 7 rows | Error: "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE" |
| BR-004 | Valid selection codes only | Selection field contains value other than 'S', 'U', or blank | Error: "INVALID ACTION CODE" |
| BR-005 | Page size is 7 records | Screen displays maximum 7 card records per page | Browse reads up to WS-MAX-SCREEN-LINES (7) matching records |
| BR-006 | Filter application | Account and/or Card filter populated with valid value | Only display records matching filter criteria |
| BR-007 | 'S' navigates to Card Detail | User types 'S' next to a record and presses ENTER | XCTL to COCRDSLC with selected card/account in COMMAREA |
| BR-008 | 'U' navigates to Card Update | User types 'U' next to a record and presses ENTER | XCTL to COCRDUPC with selected card/account in COMMAREA |
| BR-009 | No records found | Browse returns no matching records | Message: "NO RECORDS FOUND FOR THIS SEARCH CONDITION." |
| BR-010 | End of file reached | No more records beyond current page | Message: "NO MORE RECORDS TO SHOW" |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Select Option 3 from Main Menu | XCTL to COCRDLIC; display Credit Card List screen (CCRDLIA) with first page of cards | Navigate to card listing |
| 2 | Optionally enter Account Number and/or Card Number filter | Filters stored for browse operation | Narrow down card list |
| 3 | Press ENTER | Validate filters; browse CARDDAT file; display matching cards | Retrieve filtered list |
| 4a | Press F8 (Forward) | Display next page of matching cards | Page forward through list |
| 4b | Press F7 (Backward) | Display previous page of matching cards | Page backward through list |
| 5a | Type 'S' next to a record, press ENTER | XCTL to COCRDSLC (Card Detail View) | View card details |
| 5b | Type 'U' next to a record, press ENTER | XCTL to COCRDUPC (Card Update) | Update card details |
| Alt-A | Press PF3 | XCTL back to Main Menu (COMEN01C) | Exit card list |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CCLI
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COCRDLIC     │ (Entry & only online program)      │
  │  │ (Card List)  │                                    │
  │  └──┬────┬──────┘                                    │
  │     │    │                                            │
  │     ▼    ▼                                            │
  │  ┌──────┐  ┌────────────────────────────────────┐    │
  │  │BMS   │  │ VSAM Files                         │    │
  │  │MAP:  │  │  CARDDAT  (Card master file)       │    │
  │  │CCRDL │  │  CARDAIX  (Card AIX by account)    │    │
  │  │IA    │  │                                    │    │
  │  └──────┘  └────────────────────────────────────┘    │
  │                                                      │
  │  XCTL Targets:                                       │
  │  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
  │  │COMEN01C  │  │COCRDSLC  │  │COCRDUPC  │           │
  │  │(Menu)    │  │(Card Dtl)│  │(Card Upd)│           │
  │  └──────────┘  └──────────┘  └──────────┘           │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1 (First entry — from Main Menu via XCTL):
  1. COMEN01C XCTLs to COCRDLIC with CARDDEMO-COMMAREA
  2. EIBCALEN = 0 OR (CDEMO-PGM-ENTER AND CDEMO-FROM-PROGRAM ≠ COCRDLIC)
  3. INITIALIZE CARDDEMO-COMMAREA, WS-THIS-PROGCOMMAREA
  4. SET CA-FIRST-PAGE, CA-LAST-PAGE-NOT-SHOWN
  5. PERFORM YYYY-STORE-PFKEY (map EIBAID → CCARD-AID)
  6. PFK is treated as ENTER (first entry)
  7. EVALUATE → WHEN OTHER
  8. PERFORM 9000-READ-FORWARD (browse CARDDAT from LOW-VALUES)
     ├── STARTBR CARDDAT RIDFLD(WS-CARD-RID-CARDNUM) GTEQ
     ├── Loop: READNEXT → 9500-FILTER-RECORDS → populate WS-SCREEN-ROWS
     ├── Fill up to 7 rows; check for next page existence
     └── ENDBR CARDDAT
  9. PERFORM 1000-SEND-MAP
     ├── 1100-SCREEN-INIT (set titles, date, time, page number)
     ├── 1200-SCREEN-ARRAY-INIT (move row data to map output)
     ├── 1250-SETUP-ARRAY-ATTRIBS (protect/unprotect select fields)
     ├── 1300-SETUP-SCREEN-ATTRS (populate filter display fields)
     ├── 1400-SETUP-MESSAGE (set info/error messages)
     └── 1500-SEND-SCREEN (SEND MAP CCRDLIA MAPSET COCRDLI)
  10. COMMON-RETURN: RETURN TRANSID('CCLI') COMMAREA(WS-COMMAREA)

  Iteration 2+ (User presses ENTER/F7/F8 — re-entry):
  11. CICS dispatches → COCRDLIC (COMMAREA restored)
  12. EIBCALEN > 0; CDEMO-FROM-PROGRAM = COCRDLIC
  13. PERFORM 2000-RECEIVE-MAP
      ├── 2100-RECEIVE-SCREEN: RECEIVE MAP(CCRDLIA) INTO(CCRDLIAI)
      │   └── Move filter fields and selection fields to working storage
      └── 2200-EDIT-INPUTS
          ├── 2210-EDIT-ACCOUNT (validate account filter if supplied)
          ├── 2220-EDIT-CARD (validate card filter if supplied)
          └── 2250-EDIT-ARRAY (validate selection codes, count selections)
  14. PERFORM YYYY-STORE-PFKEY
  15. EVALUATE TRUE:
      ├── WHEN INPUT-ERROR → display error, re-read, re-send
      ├── WHEN PFK07 AND CA-FIRST-PAGE → re-read forward from first key
      ├── WHEN PFK07 AND NOT CA-FIRST-PAGE → 9100-READ-BACKWARDS
      ├── WHEN PFK08 AND CA-NEXT-PAGE-EXISTS → 9000-READ-FORWARD from last key
      ├── WHEN ENTER AND VIEW-REQUESTED → XCTL to COCRDSLC
      ├── WHEN ENTER AND UPDATE-REQUESTED → XCTL to COCRDUPC
      └── WHEN OTHER → re-read forward, re-send
  16. PERFORM 1000-SEND-MAP
  17. COMMON-RETURN: RETURN TRANSID('CCLI') COMMAREA(WS-COMMAREA)
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | 0 or >0 | XCTL from menu (option 3) | Initial logic (PGM-ENTER) | CCRDLIA (first page of cards) | CCLI |
| 2 | >0 | Press ENTER (with/without filter) | RECEIVE → EDIT-INPUTS → READ-FORWARD | CCRDLIA (filtered results) | CCLI |
| 3 | >0 | Press F8 (Page Down) | RECEIVE → READ-FORWARD from last key | CCRDLIA (next page) | CCLI |
| 4 | >0 | Press F7 (Page Up) | RECEIVE → READ-BACKWARDS from first key | CCRDLIA (previous page) | CCLI |
| N | >0 | Type 'S' + ENTER | RECEIVE → XCTL to COCRDSLC | — | — (XCTL) |
| N | >0 | Type 'U' + ENTER | RECEIVE → XCTL to COCRDUPC | — | — (XCTL) |
| N | >0 | PF3 (Exit) | XCTL to COMEN01C (menu) | — | — (XCTL) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | RECEIVE MAP('CCRDLIA') MAPSET('COCRDLI') INTO(CCRDLIAI) | Capture user input (filters + selections) | RESP stored in WS-RESP-CD |
| 2 | STARTBR DATASET('CARDDAT') RIDFLD(WS-CARD-RID-CARDNUM) GTEQ | Position browse at start key | RESP/RESP2 stored |
| 3 | READNEXT DATASET('CARDDAT') INTO(CARD-RECORD) | Read next card record sequentially | NORMAL → filter & store; ENDFILE → end; OTHER → error |
| 4 | ENDBR FILE('CARDDAT') | End browse session | — |
| 5 | STARTBR DATASET('CARDDAT') RIDFLD(WS-CARD-RID-CARDNUM) GTEQ | Position for backward read (page up) | RESP/RESP2 stored |
| 6 | READPREV DATASET('CARDDAT') INTO(CARD-RECORD) | Read previous card record (page up) | NORMAL → filter & store; OTHER → error |
| 7 | ENDBR FILE('CARDDAT') | End backward browse | — |
| 8 | SEND MAP('CCRDLIA') MAPSET('COCRDLI') FROM(CCRDLIAO) CURSOR ERASE FREEKB | Send screen to terminal | — |
| 9 | RETURN TRANSID('CCLI') COMMAREA(WS-COMMAREA) LENGTH(LENGTH OF WS-COMMAREA) | Pseudo-conversational return | — |
| 10 | XCTL PROGRAM('COMEN01C') COMMAREA(CARDDEMO-COMMAREA) | Return to Main Menu on PF3 | — |
| 11 | XCTL PROGRAM('COCRDSLC') COMMAREA(CARDDEMO-COMMAREA) | Transfer to Card Detail on 'S' | — |
| 12 | XCTL PROGRAM('COCRDUPC') COMMAREA(CARDDEMO-COMMAREA) | Transfer to Card Update on 'U' | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────┐                ┌─────────────────────────────────────────┐
  │  Main Menu  │   Option 3     │  CCRDLIA (Credit Card List)             │
  │  (COMEN01C) │───────────────▶│                                         │
  └─────────────┘                │  Tran: CCLI          Date: mm/dd/yy     │
        ▲                        │  Prog: COCRDLIC      Time: hh:mm:ss     │
        │ PF3                    │                                         │
        │                        │         List Credit Cards     Page N    │
        │                        │                                         │
        │                        │  Account Number    : ___________        │
        │                        │  Credit Card Number: ________________   │
        │                        │                                         │
        │                        │  Select  Account Number  Card Number  Active│
        │                        │  ------  ---------------  --------------- --------│
        │                        │  _       99999999999      9999999999999999  Y│
        │                        │  _       99999999999      9999999999999999  Y│
        │                        │  _       99999999999      9999999999999999  Y│
        │                        │  _       99999999999      9999999999999999  Y│
        │                        │  _       99999999999      9999999999999999  Y│
        │                        │  _       99999999999      9999999999999999  Y│
        │                        │  _       99999999999      9999999999999999  Y│
        │                        │                                         │
        │                        │  [Info message line]                    │
        │                        │  [Error message line]                   │
        │                        │  F3=Exit F7=Backward  F8=Forward        │
        └────────────────────────┤                                         │
                                 └─────────────┬───────────┬───────────────┘
                                               │           │
                         'S' + ENTER           │           │  'U' + ENTER
                                               ▼           ▼
                                 ┌──────────────┐  ┌──────────────┐
                                 │  COCRDSLC    │  │  COCRDUPC    │
                                 │  (Card       │  │  (Card       │
                                 │   Detail)    │  │   Update)    │
                                 └──────────────┘  └──────────────┘
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| CCRDLIA | COCRDLI | Credit Card List — display paginated card records with filters and selection | ACCTSID, CARDSID, CRDSELn(1-7), ACCTNOn(1-7), CRDNUMn(1-7), CRDSTSn(1-7) | ENTER=Submit/Select, PF3=Exit, PF7=Page Up, PF8=Page Down |

### 3.3 Screen Field Details

#### Map: CCRDLIA

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction name (CCLI) |
| TITLE01 | ASKIP,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | ASKIP,NORM | 8 | Alpha | — | Program name (COCRDLIC) |
| TITLE02 | ASKIP,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | ASKIP,NORM | 8 | Time | — | Current time hh:mm:ss |
| PAGENO | — | 3 | Numeric | — | Current page number |
| ACCTSID | FSET,IC,NORM,UNPROT | 11 | Numeric | Must be numeric 11-digit if supplied | Account Number filter |
| CARDSID | FSET,NORM,UNPROT | 16 | Numeric | Must be numeric 16-digit if supplied | Card Number filter |
| CRDSEL1 | FSET,NORM,PROT* | 1 | Alpha | 'S', 'U', or blank | Row 1 selection code |
| ACCTNO1 | NORM,PROT | 11 | Numeric | — | Row 1 account number (display) |
| CRDNUM1 | NORM,PROT | 16 | Numeric | — | Row 1 card number (display) |
| CRDSTS1 | NORM,PROT | 1 | Alpha | — | Row 1 active status (display) |
| CRDSEL2 | FSET,NORM,PROT* | 1 | Alpha | 'S', 'U', or blank | Row 2 selection code |
| ACCTNO2 | NORM,PROT | 11 | Numeric | — | Row 2 account number (display) |
| CRDNUM2 | NORM,PROT | 16 | Numeric | — | Row 2 card number (display) |
| CRDSTS2 | NORM,PROT | 1 | Alpha | — | Row 2 active status (display) |
| CRDSEL3 | FSET,NORM,PROT* | 1 | Alpha | 'S', 'U', or blank | Row 3 selection code |
| ACCTNO3 | NORM,PROT | 11 | Numeric | — | Row 3 account number (display) |
| CRDNUM3 | NORM,PROT | 16 | Numeric | — | Row 3 card number (display) |
| CRDSTS3 | NORM,PROT | 1 | Alpha | — | Row 3 active status (display) |
| CRDSEL4 | FSET,NORM,PROT* | 1 | Alpha | 'S', 'U', or blank | Row 4 selection code |
| ACCTNO4 | NORM,PROT | 11 | Numeric | — | Row 4 account number (display) |
| CRDNUM4 | NORM,PROT | 16 | Numeric | — | Row 4 card number (display) |
| CRDSTS4 | NORM,PROT | 1 | Alpha | — | Row 4 active status (display) |
| CRDSEL5 | FSET,NORM,PROT* | 1 | Alpha | 'S', 'U', or blank | Row 5 selection code |
| ACCTNO5 | NORM,PROT | 11 | Numeric | — | Row 5 account number (display) |
| CRDNUM5 | NORM,PROT | 16 | Numeric | — | Row 5 card number (display) |
| CRDSTS5 | NORM,PROT | 1 | Alpha | — | Row 5 active status (display) |
| CRDSEL6 | FSET,NORM,PROT* | 1 | Alpha | 'S', 'U', or blank | Row 6 selection code |
| ACCTNO6 | NORM,PROT | 11 | Numeric | — | Row 6 account number (display) |
| CRDNUM6 | NORM,PROT | 16 | Numeric | — | Row 6 card number (display) |
| CRDSTS6 | NORM,PROT | 1 | Alpha | — | Row 6 active status (display) |
| CRDSEL7 | FSET,NORM,PROT* | 1 | Alpha | 'S', 'U', or blank | Row 7 selection code |
| ACCTNO7 | NORM,PROT | 11 | Numeric | — | Row 7 account number (display) |
| CRDNUM7 | NORM,PROT | 16 | Numeric | — | Row 7 card number (display) |
| CRDSTS7 | NORM,PROT | 1 | Alpha | — | Row 7 active status (display) |
| INFOMSG | PROT | 45 | Alpha | — | Informational message display |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/status message display |

*Note: CRDSELn fields start as PROT (protected) in the BMS definition. At runtime, paragraph 1250-SETUP-ARRAY-ATTRIBS dynamically unprotects rows that contain data (DFHBMFSE) and keeps rows without data protected (DFHBMPRO).

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | CCRDLIA | Validate filters; if a row is selected ('S'/'U'), XCTL to detail/update; otherwise re-read forward and display |
| PF3 | CCRDLIA | Return to Main Menu (COMEN01C) via XCTL |
| PF7 | CCRDLIA | Page backward — perform 9100-READ-BACKWARDS from first displayed record key |
| PF8 | CCRDLIA | Page forward — perform 9000-READ-FORWARD from last displayed record key |
| OTHER | CCRDLIA | Treated as ENTER (PFK-INVALID → SET CCARD-AID-ENTER TO TRUE) |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COCRDLIC | Credit Card List — screen handler, browse, filter, selection dispatch | TRANSID CCLI / XCTL from COMEN01C | 1459 | `app/cbl/COCRDLIC.cbl` |
| COMEN01C | Main Menu — entry point for option 3 | XCTL (caller/return target) | — | `app/cbl/COMEN01C.cbl` |
| COCRDSLC | Card Detail View — target when 'S' selected | XCTL (target) | — | `app/cbl/COCRDSLC.cbl` |
| COCRDUPC | Card Update — target when 'U' selected | XCTL (target) | — | `app/cbl/COCRDUPC.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CCLI
└── COCRDLIC (Entry — Screen Handler, Browse, Filter, Selection Dispatch)
    ├── STARTBR FILE('CARDDAT') — Position browse for forward read
    ├── READNEXT FILE('CARDDAT') — Read records forward (page down / initial)
    ├── ENDBR FILE('CARDDAT') — End forward browse
    ├── STARTBR FILE('CARDDAT') — Position browse for backward read
    ├── READPREV FILE('CARDDAT') — Read records backward (page up)
    ├── ENDBR FILE('CARDDAT') — End backward browse
    ├── SEND MAP('CCRDLIA') — Display screen
    ├── RECEIVE MAP('CCRDLIA') — Capture input
    ├── RETURN TRANSID('CCLI') — Pseudo-conversational return
    ├── XCTL → COMEN01C (on PF3 exit)
    ├── XCTL → COCRDSLC (on 'S' selection)
    └── XCTL → COCRDUPC (on 'U' selection)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COMEN01C | COCRDLIC | XCTL | CARDDEMO-COMMAREA | — | User selects menu option 3 |
| COCRDLIC | COMEN01C | XCTL | CARDDEMO-COMMAREA | — | PF3 exit back to main menu |
| COCRDLIC | COCRDSLC | XCTL | CARDDEMO-COMMAREA (with CDEMO-ACCT-ID, CDEMO-CARD-NUM populated) | — | User types 'S' next to a row |
| COCRDLIC | COCRDUPC | XCTL | CARDDEMO-COMMAREA (with CDEMO-ACCT-ID, CDEMO-CARD-NUM populated) | — | User types 'U' next to a row |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| Card Master | CARDDAT | VSAM KSDS | BROWSE (STARTBR/READNEXT/READPREV/ENDBR) | COCRDLIC | Primary data source for card listing |
| Card Account AIX | CARDAIX | VSAM KSDS (Alternate Index) | — (referenced but not actively browsed in this program) | COCRDLIC | Declared as LIT-CARD-FILE-ACCT-PATH but filtering is done programmatically |

### 5.2 File Access Details

#### File: CARDDAT (Card Master)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Layout (Copybook) | CVACT02Y (CARD-RECORD, 150 bytes) |
| Key | CARD-NUM (PIC X(16)) — first 16 bytes of record |
| Access Mode | BROWSE (STARTBR/READNEXT/READPREV/ENDBR) |
| Locking | No update — browse only (read-only) |
| Shared With | COCRDSLC (Card Detail), COCRDUPC (Card Update), Batch programs |

**Record Layout (CVACT02Y):**

| Field | PIC | Offset | Length | Description |
|-------|-----|--------|--------|-------------|
| CARD-NUM | X(16) | 0 | 16 | Card number (primary key) |
| CARD-ACCT-ID | 9(11) | 16 | 11 | Associated account ID |
| CARD-CVV-CD | 9(03) | 27 | 3 | CVV code |
| CARD-EMBOSSED-NAME | X(50) | 30 | 50 | Name embossed on card |
| CARD-EXPIRAION-DATE | X(10) | 80 | 10 | Expiration date |
| CARD-ACTIVE-STATUS | X(01) | 90 | 1 | Active status flag |
| FILLER | X(59) | 91 | 59 | Reserved |

**Forward Browse Access Pattern (9000-READ-FORWARD):**

| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COCRDLIC | STARTBR DATASET('CARDDAT') RIDFLD(WS-CARD-RID-CARDNUM) GTEQ | Card number from last page position (or LOW-VALUES for first page) | — |
| 2 | COCRDLIC | READNEXT DATASET('CARDDAT') INTO(CARD-RECORD) | Sequential from browse position | CARD-NUM, CARD-ACCT-ID, CARD-ACTIVE-STATUS |
| 3 | COCRDLIC | 9500-FILTER-RECORDS (programmatic) | CC-ACCT-ID, CC-CARD-NUM (if filters active) | Include/exclude record based on filter match |
| 4 | COCRDLIC | READNEXT (peek-ahead after 7 records) | Check if next page exists | Sets CA-NEXT-PAGE-EXISTS or CA-NEXT-PAGE-NOT-EXISTS |
| 5 | COCRDLIC | ENDBR FILE('CARDDAT') | End browse | — |

**Backward Browse Access Pattern (9100-READ-BACKWARDS):**

| Step | Program | CICS Command | Key/Criteria | Fields Used |
|------|---------|-------------|-------------|-------------|
| 1 | COCRDLIC | STARTBR DATASET('CARDDAT') RIDFLD(WS-CARD-RID-CARDNUM) GTEQ | Card number of first record on current page | — |
| 2 | COCRDLIC | READPREV DATASET('CARDDAT') INTO(CARD-RECORD) | Initial READPREV to position | — |
| 3 | COCRDLIC | READPREV (loop) | Read backwards filling screen rows from position 7 down to 1 | CARD-NUM, CARD-ACCT-ID, CARD-ACTIVE-STATUS |
| 4 | COCRDLIC | ENDBR FILE('CARDDAT') | End browse | — |

### 5.3 Temporary Storage (TSQ) Usage

| TSQ Name | Purpose | Record Layout | Written By | Read By | Lifecycle |
|----------|---------|--------------|-----------|---------|-----------|
| — | Not used by this transaction | — | — | — | — |

### 5.4 Transient Data (TDQ) Usage

| TDQ Name | Type | Purpose | Record Layout | Triggered Program |
|----------|------|---------|--------------|-------------------|
| — | — | Not used by this transaction | — | — |

---

## 6. Data Flow Analysis

### 6.1 Data Flow Through Transaction

```
  Terminal Input (CCRDLIA)
       │
       ├── ACCTSIDI (Account Number Filter)
       │       │
       │       └──▶ CC-ACCT-ID (working storage)
       │                 │
       │                 └──▶ 2210-EDIT-ACCOUNT: validate numeric
       │                          │
       │                          ├── Valid → SET FLG-ACCTFILTER-ISVALID
       │                          │           MOVE CC-ACCT-ID → CDEMO-ACCT-ID
       │                          │
       │                          └── Invalid → SET INPUT-ERROR
       │                                        Error message displayed
       │
       ├── CARDSIDI (Card Number Filter)
       │       │
       │       └──▶ CC-CARD-NUM (working storage)
       │                 │
       │                 └──▶ 2220-EDIT-CARD: validate numeric
       │                          │
       │                          ├── Valid → SET FLG-CARDFILTER-ISVALID
       │                          │           MOVE CC-CARD-NUM-N → CDEMO-CARD-NUM
       │                          │
       │                          └── Invalid → SET INPUT-ERROR
       │                                        Error message displayed
       │
       ├── CRDSELnI (Selection codes, n=1..7)
       │       │
       │       └──▶ WS-EDIT-SELECT(n)
       │                 │
       │                 └──▶ 2250-EDIT-ARRAY: count selections, validate codes
       │                          │
       │                          ├── Single 'S' → I-SELECTED set; XCTL to COCRDSLC
       │                          ├── Single 'U' → I-SELECTED set; XCTL to COCRDUPC
       │                          ├── Multiple → INPUT-ERROR: "SELECT ONLY ONE"
       │                          └── Invalid code → INPUT-ERROR: "INVALID ACTION"
       │
       └── (Browse loop — 9000-READ-FORWARD / 9100-READ-BACKWARDS)
                 │
                 └──▶ CARD-RECORD (from CARDDAT VSAM)
                          │
                          ├── 9500-FILTER-RECORDS:
                          │     ├── FLG-ACCTFILTER-ISVALID?
                          │     │     └── CARD-ACCT-ID = CC-ACCT-ID? Include : Exclude
                          │     └── FLG-CARDFILTER-ISVALID?
                          │           └── CARD-NUM = CC-CARD-NUM-N? Include : Exclude
                          │
                          └──▶ WS-SCREEN-ROWS(n)
                                    │
                                    ├── WS-ROW-ACCTNO(n) ← CARD-ACCT-ID
                                    ├── WS-ROW-CARD-NUM(n) ← CARD-NUM
                                    └── WS-ROW-CARD-STATUS(n) ← CARD-ACTIVE-STATUS
                                              │
                                              └──▶ Screen Output (CCRDLIAO)
                                                    ├── ACCTNOnO
                                                    ├── CRDNUMnO
                                                    └── CRDSTSnO
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| ACCTSIDI | Terminal (CCRDLIAI) | Direct move X(11) | CC-ACCT-ID | Working storage (CVCRD01Y) |
| CARDSIDI | Terminal (CCRDLIAI) | Direct move X(16) | CC-CARD-NUM | Working storage (CVCRD01Y) |
| CC-ACCT-ID | Working storage | Direct move | CDEMO-ACCT-ID | COMMAREA (COCOM01Y) — PIC 9(11) |
| CC-CARD-NUM-N | Working storage | Direct move (numeric redefine) | CDEMO-CARD-NUM | COMMAREA (COCOM01Y) — PIC 9(16) |
| CARD-NUM | CARDDAT record | Direct move X(16) | WS-ROW-CARD-NUM(n) | Screen data array |
| CARD-ACCT-ID | CARDDAT record | Direct move 9(11) | WS-ROW-ACCTNO(n) | Screen data array |
| CARD-ACTIVE-STATUS | CARDDAT record | Direct move X(1) | WS-ROW-CARD-STATUS(n) | Screen data array |
| WS-ROW-ACCTNO(n) | Screen data array | Direct move | ACCTNOnO | Map output (CCRDLIAO) |
| WS-ROW-CARD-NUM(n) | Screen data array | Direct move | CRDNUMnO | Map output (CCRDLIAO) |
| WS-ROW-CARD-STATUS(n) | Screen data array | Direct move | CRDSTSnO | Map output (CCRDLIAO) |
| WS-ROW-ACCTNO(I-SELECTED) | Screen data array | Direct move → CDEMO-ACCT-ID | CARDDEMO-COMMAREA | Passed to detail/update via XCTL |
| WS-ROW-CARD-NUM(I-SELECTED) | Screen data array | Direct move → CDEMO-CARD-NUM | CARDDEMO-COMMAREA | Passed to detail/update via XCTL |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`) plus a program-specific extension `WS-THIS-PROGCOMMAREA`:

**CARDDEMO-COMMAREA (COCOM01Y — shared portion):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-FROM-TRANID | X(04) | 4 | IN/OUT | Originating transaction ID |
| CDEMO-FROM-PROGRAM | X(08) | 8 | IN/OUT | Originating program name |
| CDEMO-TO-TRANID | X(04) | 4 | OUT | Target transaction ID |
| CDEMO-TO-PROGRAM | X(08) | 8 | OUT | Target program for XCTL |
| CDEMO-USER-ID | X(08) | 8 | IN | Authenticated user ID |
| CDEMO-USER-TYPE | X(01) | 1 | IN | 'A' = Admin, 'U' = User |
| CDEMO-PGM-CONTEXT | 9(01) | 1 | IN/OUT | 0 = first entry (ENTER), 1 = re-entry (REENTER) |
| CDEMO-CUST-ID | 9(09) | 9 | IN | Customer ID |
| CDEMO-ACCT-ID | 9(11) | 11 | IN/OUT | Account ID (used for filter and XCTL to detail/update) |
| CDEMO-ACCT-STATUS | X(01) | 1 | — | Account status |
| CDEMO-CARD-NUM | 9(16) | 16 | IN/OUT | Card Number (used for filter and XCTL to detail/update) |
| CDEMO-LAST-MAP | X(7) | 7 | IN/OUT | Last BMS map displayed |
| CDEMO-LAST-MAPSET | X(7) | 7 | IN/OUT | Last BMS mapset used |

**WS-THIS-PROGCOMMAREA (program-specific extension, defined inline in COCRDLIC):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| WS-CA-LAST-CARD-NUM | X(16) | 16 | IN/OUT | Last card number on current page (for forward paging) |
| WS-CA-LAST-CARD-ACCT-ID | 9(11) | 11 | IN/OUT | Last account ID on current page |
| WS-CA-FIRST-CARD-NUM | X(16) | 16 | IN/OUT | First card number on current page (for backward paging) |
| WS-CA-FIRST-CARD-ACCT-ID | 9(11) | 11 | IN/OUT | First account ID on current page |
| WS-CA-SCREEN-NUM | 9(1) | 1 | IN/OUT | Current page number |
| WS-CA-LAST-PAGE-DISPLAYED | 9(1) | 1 | IN/OUT | 0 = last page shown; 9 = last page not shown |
| WS-CA-NEXT-PAGE-IND | X(1) | 1 | IN/OUT | LOW-VALUES = no next page; 'Y' = next page exists |
| WS-RETURN-FLAG | X(1) | 1 | IN/OUT | LOW-VALUES = off; '1' = return flag on |

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. Errors are handled inline via screen messages (WS-ERROR-MSG → ERRMSGO). Navigation state is communicated through:

| Indicator | Meaning | User Display |
|-----------|---------|-------------|
| CA-FIRST-PAGE (WS-CA-SCREEN-NUM = 1) | Currently on first page | PF7 shows "NO PREVIOUS PAGES TO DISPLAY" |
| CA-NEXT-PAGE-EXISTS (WS-CA-NEXT-PAGE-IND = 'Y') | More records exist after current page | — (PF8 enabled) |
| CA-NEXT-PAGE-NOT-EXISTS (WS-CA-NEXT-PAGE-IND = LOW-VALUES) | No more records after current page | "NO MORE PAGES TO DISPLAY" on subsequent PF8 |
| CA-LAST-PAGE-SHOWN (WS-CA-LAST-PAGE-DISPLAYED = 0) | Last page has been displayed | "NO MORE PAGES TO DISPLAY" |

### 7.3 Conversation State Management

- **COMMAREA preservation**: The entire CARDDEMO-COMMAREA followed by WS-THIS-PROGCOMMAREA is concatenated into WS-COMMAREA (PIC X(2000)) and passed on every RETURN TRANSID('CCLI'). CICS restores it automatically on the next dispatch.
- **Pagination keys**: WS-CA-FIRST-CARDKEY and WS-CA-LAST-CARDKEY track the card numbers at the boundaries of the current page for forward/backward navigation.
- **Screen data not preserved**: The 7-row screen data array (WS-ALL-ROWS, 196 bytes) is re-read from the file on every iteration — it is NOT stored in the COMMAREA.
- **Filter preservation**: Filter values (CC-ACCT-ID, CC-CARD-NUM) are re-received from the map on each iteration (BMS FSET ensures they are returned).
- **PGM-CONTEXT flag**: Distinguishes first entry (0 — display initial list) from subsequent interactions (1 — process input/navigation).

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path:
    Input → Validate Filters → Browse CARDDAT → Filter Records → Display Page
                                                                       │
                                            ┌──────────────────────────┘
                                            ▼
                               Info: "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD"

  Filter Validation Error:
    Input → Validate Account ──FAIL──▶ "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"
                                       → Cursor to ACCTSID, rows protected
    Input → Validate Card    ──FAIL──▶ "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"
                                       → Cursor to CARDSID, rows protected

  Selection Validation Error:
    Input → Count Selections ──>1──▶ "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE"
                                     → Error rows highlighted in RED
    Input → Invalid Code     ──▶ "INVALID ACTION CODE"
                                  → Error row highlighted in RED

  No Records Found:
    Browse → ENDFILE (first page, zero records) ──▶
                      "NO RECORDS FOUND FOR THIS SEARCH CONDITION."

  End of File (subsequent pages):
    Browse → ENDFILE after displaying records ──▶ "NO MORE RECORDS TO SHOW"

  Pagination Boundary:
    PF7 on first page ──▶ "NO PREVIOUS PAGES TO DISPLAY"
    PF8 on last page  ──▶ "NO MORE PAGES TO DISPLAY"

  File I/O Error:
    READNEXT/READPREV → unexpected RESP ──▶
         "File Error: READ     on CARDDAT  returned RESP nnnn,RESP2 nnnn"
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| Account filter not numeric | — (application edit) | SET INPUT-ERROR, FLG-ACCTFILTER-NOT-OK, protect rows | "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER" | — |
| Card filter not numeric | — (application edit) | SET INPUT-ERROR, FLG-CARDFILTER-NOT-OK, protect rows | "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" | — |
| Multiple selections | — (application edit) | SET INPUT-ERROR, WS-MORE-THAN-1-ACTION | "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE" | — |
| Invalid selection code | — (application edit) | SET INPUT-ERROR, mark row error | "INVALID ACTION CODE" | — |
| No records for search | ENDFILE (first page) | SET WS-NO-RECORDS-FOUND | "NO RECORDS FOUND FOR THIS SEARCH CONDITION." | — |
| End of file (during browse) | ENDFILE | SET CA-NEXT-PAGE-NOT-EXISTS, READ-LOOP-EXIT | "NO MORE RECORDS TO SHOW" | — |
| Peek-ahead reaches EOF | ENDFILE (after 7 records) | SET CA-NEXT-PAGE-NOT-EXISTS | "NO MORE RECORDS TO SHOW" | — |
| Unexpected READ error (forward) | OTHER | SET READ-LOOP-EXIT, build error msg | "File Error: READ on CARDDAT returned RESP nnnn,RESP2 nnnn" | Error message includes RESP/RESP2 codes |
| Unexpected READ error (backward) | OTHER | SET READ-LOOP-EXIT, build error msg | "File Error: READ on CARDDAT returned RESP nnnn,RESP2 nnnn" | Error message includes RESP/RESP2 codes |
| PF7 on first page | — (page state check) | Re-read forward from first key | "NO PREVIOUS PAGES TO DISPLAY" | — |
| PF8 when no more pages | — (page state check) | Message only | "NO MORE PAGES TO DISPLAY" | — |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| — | No explicit HANDLE ABEND | Default CICS abend handling applies |

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER" | CC-ACCT-ID is not numeric (and not blank/zero) | ERRMSG (line 23, col 1) |
| — | "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" | CC-CARD-NUM is not numeric (and not blank/zero) | ERRMSG (line 23, col 1) |
| — | "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE" | More than one 'S' or 'U' found across 7 selection fields | ERRMSG (line 23, col 1) |
| — | "INVALID ACTION CODE" | Selection field contains value other than 'S', 'U', blank, or LOW-VALUES | ERRMSG (line 23, col 1) |
| — | "NO RECORDS FOUND FOR THIS SEARCH CONDITION." | First page browse returns zero matching records | ERRMSG (line 23, col 1) |
| — | "NO MORE RECORDS TO SHOW" | ENDFILE reached during browse | ERRMSG (line 23, col 1) |
| — | "NO PREVIOUS PAGES TO DISPLAY" | User presses PF7 when already on first page | ERRMSG (line 23, col 1) |
| — | "NO MORE PAGES TO DISPLAY" | User presses PF8 when last page was already displayed | ERRMSG (line 23, col 1) |
| — | "PF03 PRESSED.EXITING" | User presses PF3 (set but screen not re-sent — XCTL occurs) | — (not displayed, immediate XCTL) |
| — | "File Error: READ on CARDDAT returned RESP nnnn,RESP2 nnnn" | Unexpected RESP from READNEXT/READPREV | ERRMSG (line 23, col 1) |
| — | "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD" | Normal info message when records are displayed and more pages exist | INFOMSG (line 20, col 19) |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CCLI transaction must be authorized for user |
| Resource-level security | RACF FACILITY / File profiles for CARDDAT |
| Application-level check | If EIBCALEN = 0 (first invocation — direct from menu with no context), program initializes fresh COMMAREA. No explicit sign-on redirect in this program. |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| CARDDAT file | READ | CICS File Control |

### 9.3 Audit Trail

| Event | Trigger | Data Logged | Destination |
|-------|---------|-------------|-------------|
| No explicit audit logging | — | — | Transaction may be audited via CICS journal/SMF records at the system level |
| XCTL to detail/update | User selects 'S' or 'U' | CDEMO-ACCT-ID, CDEMO-CARD-NUM passed in COMMAREA | — (implicit via CICS transaction flow) |

---

## 10. Performance Characteristics

### 10.1 Performance Profile

| Metric | Baseline | Peak | SLA |
|--------|----------|------|-----|
| Response time | < 1 second | < 2 seconds | < 2 seconds |
| CPU per transaction | Low (browse + filter) | — | — |
| File I/O count | 7–15+ per page (7 displayed records + filter skips + 1 peek-ahead) | Higher with selective filters | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal | — | — |

### 10.2 Performance Considerations

- **Sequential Browse with Programmatic Filter**: The program browses the CARDDAT file sequentially and applies account/card filters in COBOL code (paragraph 9500-FILTER-RECORDS). If filters are selective, many records may be read and skipped before filling 7 screen rows. This is acceptable for small datasets but could be slow with large VSAM files when filters exclude most records.
- **No Alternate Index Browse**: Although `LIT-CARD-FILE-ACCT-PATH` ('CARDAIX') is defined, the program does NOT browse via the alternate index. Filtering by account number still requires a full sequential browse of CARDDAT with programmatic filtering.
- **Page-ahead Check**: After filling 7 rows, one additional READNEXT is performed to determine if more pages exist. This adds one extra I/O per page display.
- **Backward Paging**: Uses STARTBR/READPREV from the first displayed key. Must also filter records during backward traversal, adding potential extra I/O.
- **COMMAREA Size**: WS-COMMAREA is PIC X(2000) — relatively large but well within CICS limits.
- **BMS SEND/RECEIVE**: Standard map I/O with ERASE and CURSOR positioning — one pair per pseudo-conversational iteration.

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Initial display (no filters) | Select option 3 from menu | First page of all cards displayed, page 1, info message shown | High |
| TC-002 | Filter by valid Account Number | Enter valid 11-digit account number, press ENTER | Only cards for that account shown | High |
| TC-003 | Filter by valid Card Number | Enter valid 16-digit card number, press ENTER | Only matching card displayed | High |
| TC-004 | Combined filter | Enter both Account Number and Card Number | Only records matching both criteria shown | Medium |
| TC-005 | Invalid Account filter (non-numeric) | Enter "ABCDEFGHIJK" in Account Number | "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER", rows protected | High |
| TC-006 | Invalid Card filter (non-numeric) | Enter "ABCDEFGHIJKLMNOP" in Card Number | "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER", rows protected | High |
| TC-007 | Page forward (F8) | Press F8 when more records exist | Next page of cards displayed, page number incremented | High |
| TC-008 | Page forward at end | Press F8 on last page | "NO MORE PAGES TO DISPLAY" | Medium |
| TC-009 | Page backward (F7) | Press F7 when not on first page | Previous page of cards displayed, page number decremented | High |
| TC-010 | Page backward on first page | Press F7 on page 1 | "NO PREVIOUS PAGES TO DISPLAY", same page redisplayed | Medium |
| TC-011 | Select card for detail ('S') | Type 'S' next to a record, press ENTER | XCTL to COCRDSLC with correct card/account in COMMAREA | High |
| TC-012 | Select card for update ('U') | Type 'U' next to a record, press ENTER | XCTL to COCRDUPC with correct card/account in COMMAREA | High |
| TC-013 | Multiple selections | Type 'S' on two rows, press ENTER | "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE", error rows in RED | Medium |
| TC-014 | Invalid selection code | Type 'X' next to a record, press ENTER | "INVALID ACTION CODE" | Medium |
| TC-015 | No records match filter | Enter account number with no associated cards | "NO RECORDS FOUND FOR THIS SEARCH CONDITION." | Medium |
| TC-016 | PF3 exit | Press PF3 | Return to Main Menu (COMEN01C) | Medium |
| TC-017 | Invalid PF key | Press PF2, PF6, etc. | Treated as ENTER — list redisplayed | Low |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| CARDDAT | Pre-loaded with multiple card records across different accounts; mix of active ('Y') and inactive ('N') status | VSAM REPRO from test data (`app/data/`) |
| CARDDAT | At least 8+ records for pagination testing (exceed 7-per-page limit) | Ensure multiple accounts with multiple cards |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | Programmatic filtering instead of alternate index browse — when filtering by account number, program still browses entire CARDDAT file sequentially | Performance degradation on large files with selective filters | Use CARDAIX alternate index for account-based browsing |
| KI-002 | No explicit HANDLE ABEND — unexpected abends use default CICS handling | User sees CICS ABEND screen rather than a friendly error | Add HANDLE ABEND with a user-friendly error screen |
| KI-003 | Page numbering uses PIC 9(1) — supports only single-digit page numbers (1-9) | Cannot display page numbers beyond 9 | Increase WS-CA-SCREEN-NUM to PIC 9(3) or higher |
| KI-004 | Selection fields (CRDSELn) are defined as PROT in BMS but dynamically changed at runtime — this means the initial BMS definition doesn't reflect actual field behavior | Confusing for developers reading BMS source | Document the dynamic attribute modification behavior |
| KI-005 | Commented-out code references account-path filtering (CARDAIX alternate index) that was apparently planned but not implemented | Filter-by-account performance opportunity missed | Implement alternate index browse path |
| KI-006 | CRDSEL1 (row 1) lacks the hidden stopper field (CRDSTPn) that rows 2-7 have | Inconsistency in BMS definition between first and subsequent rows | Cosmetic — does not affect functionality |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Screen I/O | BMS Map CCRDLIA / 3270 terminal | Web UI data grid / REST API GET endpoint with pagination | Medium — list pattern maps well to REST |
| Data Access | VSAM KSDS sequential browse with programmatic filter | SQL SELECT with WHERE clause and LIMIT/OFFSET | Low — replace browse+filter with SQL query |
| Pagination | COMMAREA-based key tracking (first/last card num) | Cursor-based or offset pagination in API | Low — standard REST pagination patterns |
| Filtering | Programmatic record filtering in COBOL (9500-FILTER-RECORDS) | SQL WHERE clause (account_id = ? AND card_num = ?) | Low — direct SQL predicate pushdown |
| Selection/Navigation | 'S'/'U' codes in selection column → XCTL | Hyperlinks/buttons → route to detail/edit pages | Low — standard web UI patterns |
| State Management | COMMAREA pseudo-conversational with page keys | Stateless REST API with query parameters | Low — pagination state in URL params |
| Performance | Sequential browse of entire file for filtering | Database index-based queries | Significant improvement expected |
| Display | Fixed 7-row screen with static field definitions | Dynamic table with configurable page size | Low — straightforward UI modernization |

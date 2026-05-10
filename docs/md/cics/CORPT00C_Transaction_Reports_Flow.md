# Transaction Reports (CR00) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CR00 |
| Entry Program | CORPT00C |
| Business Domain | Transaction Reporting |
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

- **What**: Allows an operator to request the printing of a transaction report by submitting a batch JCL job through an extra-partition Transient Data Queue (TDQ). Three report types are available: Monthly (current month), Yearly (current year), and Custom (user-specified date range).
- **Who**: Any authenticated CardDemo user (regular users and admins) — menu option 9 ("Transaction Reports").
- **When**: Real-time, on-demand during business hours when a printed transaction report is required.
- **Why**: Provides a mechanism to generate printed reports of transaction activity for audit, reconciliation, or management review purposes.
- **What** is the business outcome: A batch JCL job (`TRNRPT00`) is submitted to the JES internal reader via the `JOBS` TDQ. The job executes procedure `TRANREPT` with the appropriate date parameters, producing a printed transaction report. The user receives an on-screen confirmation that the report has been submitted.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  User selects Option 9 ("Transaction Reports") from Main Menu
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 1            │  Select report type:
  └────────────┬─────────────┘  - Monthly (mark the Monthly field)
               │                - Yearly (mark the Yearly field)
               │                - Custom (mark Custom + enter Start/End dates)
               ▼
  ┌──────────────────────────┐
  │ System Validation        │  For Custom: validate Start/End date fields
  └────────────┬─────────────┘  (non-empty, numeric, valid month/day/year,
               │                 valid calendar date via CSUTLDTC)
           ┌───┴───┐
           │Decision│  Validation passes?
           └───┬───┘
              ╱ ╲
        Yes ╱     ╲ No
           ▼       ▼
  ┌──────────┐  ┌─────────────────┐
  │ Confirm? │  │ Error message,  │
  │  (Y/N)   │  │ cursor to field │
  └────┬─────┘  └─────────────────┘
       │
   ┌───┴───┐
   │  Y/N? │
   └───┬───┘
      ╱ ╲
Yes ╱     ╲ No
   ▼       ▼
┌───────────────────────┐  ┌──────────────────────────────────┐
│ Write JCL lines to    │  │ "Please confirm to print the     │
│ JOBS TDQ (internal    │  │  <type> report..." message       │
│ reader) for batch     │  └──────────────────────────────────┘
│ job submission        │
└───────────┬───────────┘
            ▼
  ┌──────────────────────────┐
  │ Business Outcome         │  "<Type> report submitted for printing ..."
  └──────────────────────────┘   Screen fields cleared for next request.
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | Report type must be selected | None of Monthly, Yearly, or Custom fields marked | Error: "Select a report type to print report..." |
| BR-002 | Monthly report uses current month | Monthly field is non-empty | Auto-calculate start date = 1st of current month, end date = last day of current month |
| BR-003 | Yearly report uses current year | Yearly field is non-empty | Auto-calculate start date = Jan 1 of current year, end date = Dec 31 of current year |
| BR-004 | Custom report requires complete date range | Custom field is non-empty | User must provide all Start Date and End Date components (MM, DD, YYYY) |
| BR-005 | Start Date fields cannot be empty | Custom selected, any Start Date field empty | Error: "Start Date - Month/Day/Year can NOT be empty..." |
| BR-006 | End Date fields cannot be empty | Custom selected, any End Date field empty | Error: "End Date - Month/Day/Year can NOT be empty..." |
| BR-007 | Month must be valid (01–12) | Month field not numeric or > 12 | Error: "Start/End Date - Not a valid Month..." |
| BR-008 | Day must be valid (01–31) | Day field not numeric or > 31 | Error: "Start/End Date - Not a valid Day..." |
| BR-009 | Year must be numeric | Year field not numeric | Error: "Start/End Date - Not a valid Year..." |
| BR-010 | Date must be a valid calendar date | Assembled date fails CSUTLDTC validation | Error: "Start/End Date - Not a valid date..." |
| BR-011 | Confirmation required | User must confirm with 'Y' before job submission | Prompt: "Please confirm to print the <type> report..." |
| BR-012 | Confirmation must be Y or N | CONFIRM field is not Y/y/N/n | Error: '"<value>" is not a valid value to confirm...' |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Select Option 9 from Main Menu | XCTL to CORPT00C; display Transaction Reports screen (CORPT0A) | Navigate to report request |
| 2a | Mark Monthly field, press ENTER | Calculate current month date range, prompt for confirmation | Request monthly report |
| 2b | Mark Yearly field, press ENTER | Calculate current year date range, prompt for confirmation | Request yearly report |
| 2c | Mark Custom field + enter Start/End dates, press ENTER | Validate date fields; if valid, prompt for confirmation | Request custom date range report |
| 3a | Type 'Y' in Confirm field, press ENTER | Write JCL to JOBS TDQ, display success message, clear fields | Report job submitted |
| 3b | Type 'N' in Confirm field, press ENTER | Clear all fields, redisplay empty form | User cancels submission |
| Alt-A | Press PF3 | XCTL back to Main Menu (COMEN01C) | Exit without submitting |
| Alt-B | Press any other key | Display "Invalid key pressed. Please see below..." | Unsupported key |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CR00
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ CORPT00C     │ (Entry & only online program)      │
  │  │ (Tran Rpts)  │                                    │
  │  └──┬────┬──┬───┘                                    │
  │     │    │  │                                         │
  │     ▼    │  ▼                                         │
  │  ┌──────┐│ ┌────────────────────────────────────┐    │
  │  │BMS   ││ │ TDQ                                │    │
  │  │MAP:  ││ │  JOBS (Extra-partition TDQ →        │    │
  │  │CORPT ││ │        JES Internal Reader)         │    │
  │  │0A    ││ └────────────────────────────────────┘    │
  │  └──────┘│                                           │
  │          │                                            │
  │          ▼                                            │
  │  ┌─────────────┐                                     │
  │  │ CSUTLDTC    │ (Date validation utility - CALL)    │
  │  └─────────────┘                                     │
  └──────────────────────────────────────────────────────┘
         │
         ▼ (via JOBS TDQ → JES Internal Reader)
  ┌──────────────────────────────────────────────────────┐
  │ BATCH REGION                                         │
  │  ┌─────────────────────┐                             │
  │  │ JOB: TRNRPT00       │                             │
  │  │ PROC: TRANREPT      │                             │
  │  │ (Transaction Report  │                             │
  │  │  Generation)         │                             │
  │  └─────────────────────┘                             │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1 (First entry — from Main Menu via XCTL):
  1. COMEN01C XCTLs to CORPT00C with CARDDEMO-COMMAREA
  2. EIBCALEN > 0; CDEMO-PGM-REENTER = 0 (first entry)
  3. SET CDEMO-PGM-REENTER TO TRUE
  4. MOVE LOW-VALUES TO CORPT0AO (clear output map)
  5. Set cursor to Monthly field (MONTHLYL = -1)
  6. PERFORM SEND-TRNRPT-SCREEN
     └── POPULATE-HEADER-INFO (titles, date, time)
     └── SEND MAP('CORPT0A') MAPSET('CORPT00') ERASE CURSOR
  7. GO TO RETURN-TO-CICS
     └── RETURN TRANSID('CR00') COMMAREA(CARDDEMO-COMMAREA)

  Iteration 2 (User submits data — ENTER pressed):
  8.  CICS dispatches → CORPT00C (COMMAREA restored)
  9.  EIBCALEN > 0; CDEMO-PGM-REENTER = 1
  10. PERFORM RECEIVE-TRNRPT-SCREEN
      └── RECEIVE MAP('CORPT0A') MAPSET('CORPT00') INTO(CORPT0AI)
  11. EIBAID = DFHENTER → PERFORM PROCESS-ENTER-KEY
  12. EVALUATE TRUE (report type selection):
      ├── MONTHLYI not empty:
      │   ├── Get CURRENT-DATE
      │   ├── Start = YYYY-MM-01 (current month 1st)
      │   ├── Advance month by 1 (wrap year if needed)
      │   ├── End = last day of current month (DATE-OF-INTEGER trick)
      │   └── PERFORM SUBMIT-JOB-TO-INTRDR
      ├── YEARLYI not empty:
      │   ├── Get CURRENT-DATE
      │   ├── Start = YYYY-01-01
      │   ├── End = YYYY-12-31
      │   └── PERFORM SUBMIT-JOB-TO-INTRDR
      ├── CUSTOMI not empty:
      │   ├── Validate all 6 date components (empty check)
      │   ├── NUMVAL-C conversion for each component
      │   ├── Validate month (numeric, ≤12), day (numeric, ≤31), year (numeric)
      │   ├── Assemble WS-START-DATE, WS-END-DATE
      │   ├── CALL 'CSUTLDTC' for start date calendar validation
      │   ├── CALL 'CSUTLDTC' for end date calendar validation
      │   ├── If no errors: PERFORM SUBMIT-JOB-TO-INTRDR
      │   └── (Any error: set ERR-FLG, SEND screen with error, RETURN)
      └── OTHER (no report type selected):
          └── Error: "Select a report type to print report..."

  Iteration 2a (SUBMIT-JOB-TO-INTRDR — when report type resolved):
  13. Check CONFIRMI:
      ├── Spaces/LOW-VALUES → Prompt "Please confirm to print the <type> report..."
      ├── 'Y'/'y' → Continue to write JCL
      ├── 'N'/'n' → INITIALIZE-ALL-FIELDS, set ERR-FLG (clears form)
      └── Other → Error: '"<value>" is not a valid value to confirm...'
  14. If confirmed:
      ├── Loop WS-IDX from 1 to 1000 (or until /*EOF or empty line)
      │   ├── MOVE JOB-LINES(WS-IDX) TO JCL-RECORD
      │   └── PERFORM WIRTE-JOBSUB-TDQ
      │       └── WRITEQ TD QUEUE('JOBS') FROM(JCL-RECORD)
      └── (Error in TDQ write → "Unable to Write TDQ (JOBS)...")
  15. If no errors:
      ├── PERFORM INITIALIZE-ALL-FIELDS
      ├── Set ERRMSG color to GREEN
      ├── Display "<Type> report submitted for printing ..."
      └── PERFORM SEND-TRNRPT-SCREEN
  16. RETURN TRANSID('CR00') COMMAREA(CARDDEMO-COMMAREA)
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|-------------|---------------------|-------------|--------------|
| 1 (first) | >0 | XCTL from menu (option 9) | Initial logic (PGM-ENTER) | CORPT0A (empty form) | CR00 |
| 2 | >0 | Select report type + ENTER | RECEIVE → PROCESS-ENTER-KEY | CORPT0A (confirm prompt or error) | CR00 |
| 3 | >0 | Confirm Y + ENTER | PROCESS-ENTER-KEY → SUBMIT-JOB-TO-INTRDR | CORPT0A (success message, cleared) | CR00 |
| N | >0 | PF3 (Exit) | XCTL to COMEN01C (menu) | — | — (XCTL) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | RECEIVE MAP('CORPT0A') MAPSET('CORPT00') | Capture user input from terminal | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 2 | WRITEQ TD QUEUE('JOBS') FROM(JCL-RECORD) | Write one JCL line to JOBS TDQ (JES internal reader) | NORMAL → continue; OTHER → error "Unable to Write TDQ (JOBS)..." |
| 3 | SEND MAP('CORPT0A') MAPSET('CORPT00') ERASE CURSOR | Send screen to terminal (first send with ERASE) | — |
| 4 | SEND MAP('CORPT0A') MAPSET('CORPT00') CURSOR | Send screen to terminal (subsequent sends without ERASE) | — |
| 5 | RETURN TRANSID('CR00') COMMAREA(CARDDEMO-COMMAREA) | Pseudo-conversational return | — |
| 6 | XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA | Transfer to previous screen on PF3 | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────┐                ┌─────────────────────────────────────┐
  │  Main Menu  │   Option 9     │  CORPT0A (Transaction Reports)      │
  │  (COMEN01C) │───────────────▶│                                     │
  └─────────────┘                │  Tran: CR00          Date: mm/dd/yy │
        ▲                        │  Prog: CORPT00C      Time: hh:mm:ss │
        │ PF3                    │                                     │
        │                        │      Transaction Reports            │
        │                        │                                     │
        │                        │  _ Monthly (Current Month)          │
        │                        │                                     │
        │                        │  _ Yearly (Current Year)            │
        │                        │                                     │
        │                        │  _ Custom (Date Range)              │
        │                        │     Start Date : __/__/____         │
        │                        │       End Date : __/__/____         │
        │                        │                  (MM/DD/YYYY)       │
        │                        │                                     │
        │                        │  The Report will be submitted for   │
        │                        │  printing. Please confirm: _ (Y/N)  │
        │                        │                                     │
        │                        │  [Error/Success message line]       │
        │                        │  ENTER=Continue  F3=Back            │
        └────────────────────────┤                                     │
                                 └─────────────────────────────────────┘
                                   │
                                   │ ENTER
                                   │ (loops back to self)
                                   ▼
                                 [Same screen — CORPT0A]
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| CORPT0A | CORPT00 | Transaction Reports — select report type, enter date range, confirm submission | Monthly, Yearly, Custom, Start Date (MM/DD/YYYY), End Date (MM/DD/YYYY), Confirm | ENTER=Submit, PF3=Back |

### 3.3 Screen Field Details

#### Map: CORPT0A

| Field Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction name (CR00) |
| TITLE01 | ASKIP,FSET,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | ASKIP,FSET,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | ASKIP,FSET,NORM | 8 | Alpha | — | Program name (CORPT00C) |
| TITLE02 | ASKIP,FSET,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | ASKIP,FSET,NORM | 8 | Time | — | Current time hh:mm:ss |
| MONTHLY | FSET,IC,NORM,UNPROT | 1 | Alpha | Non-empty to select Monthly report | Monthly report selection (mark with any character) |
| YEARLY | FSET,NORM,UNPROT | 1 | Alpha | Non-empty to select Yearly report | Yearly report selection (mark with any character) |
| CUSTOM | FSET,NORM,UNPROT | 1 | Alpha | Non-empty to select Custom report | Custom date range report selection |
| SDTMM | FSET,NORM,NUM,UNPROT | 2 | Numeric | Non-empty, numeric, ≤12 | Start Date — Month (MM) |
| SDTDD | FSET,NORM,NUM,UNPROT | 2 | Numeric | Non-empty, numeric, ≤31 | Start Date — Day (DD) |
| SDTYYYY | FSET,NORM,NUM,UNPROT | 4 | Numeric | Non-empty, numeric | Start Date — Year (YYYY) |
| EDTMM | FSET,NORM,NUM,UNPROT | 2 | Numeric | Non-empty, numeric, ≤12 | End Date — Month (MM) |
| EDTDD | FSET,NORM,NUM,UNPROT | 2 | Numeric | Non-empty, numeric, ≤31 | End Date — Day (DD) |
| EDTYYYY | FSET,NORM,NUM,UNPROT | 4 | Numeric | Non-empty, numeric | End Date — Year (YYYY) |
| CONFIRM | FSET,NORM,UNPROT | 1 | Alpha | Y/N only | Confirmation flag for report submission |
| ERRMSG | ASKIP,BRT,FSET | 78 | Alpha | — | Error/success message display |

### 3.4 PF Key / AID Key Handling

| AID Key | Context (which map) | Action |
|---------|-------------------|--------|
| ENTER | CORPT0A | Validate report selection and date inputs; if valid and confirmed, submit batch JCL to JOBS TDQ |
| PF3 | CORPT0A | Return to Main Menu (COMEN01C) via XCTL |
| OTHER | CORPT0A | Display "Invalid key pressed. Please see below..." |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| CORPT00C | Transaction Reports — screen handler, date validation, JCL submission to TDQ | TRANSID CR00 / XCTL from COMEN01C | 649 | `app/cbl/CORPT00C.cbl` |
| CSUTLDTC | Date validation utility — validates date strings against calendar | CALL from CORPT00C | ~100 | `app/cbl/CSUTLDTC.cbl` |
| COMEN01C | Main Menu — entry point for option 9 | XCTL (caller) | — | `app/cbl/COMEN01C.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CR00
└── CORPT00C (Entry — Screen Handler, Validation, TDQ Job Submission)
    ├── CALL → CSUTLDTC (Date validation utility)
    │   ├── Validates Custom Start Date
    │   └── Validates Custom End Date
    ├── WRITEQ TD QUEUE('JOBS') — Write JCL lines to TDQ (JES internal reader)
    ├── SEND MAP('CORPT0A') — Display screen
    ├── RECEIVE MAP('CORPT0A') — Capture input
    ├── RETURN TRANSID('CR00') — Pseudo-conversational return
    └── XCTL → COMEN01C or COSGN00C (on PF3 exit / no COMMAREA)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COMEN01C | CORPT00C | XCTL | CARDDEMO-COMMAREA | — | User selects menu option 9 |
| CORPT00C | CSUTLDTC | CALL | CSUTLDTC-DATE (X(10)), CSUTLDTC-DATE-FORMAT (X(10)) | CSUTLDTC-RESULT (sev code, msg num, msg text) | Custom date validation — called twice (start date, end date) |
| CORPT00C | COMEN01C | XCTL | CARDDEMO-COMMAREA | — | PF3 exit back to main menu |
| CORPT00C | COSGN00C | XCTL | CARDDEMO-COMMAREA | — | EIBCALEN = 0 (no COMMAREA — direct invocation without login) |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| — | — | — | — | — | CORPT00C does not directly access any VSAM files or DB2 tables |

### 5.2 File Access Details

This transaction does not perform any direct file I/O (READ, WRITE, BROWSE). All data access is delegated to the batch job (`TRNRPT00 / TRANREPT`) that is submitted via the TDQ.

### 5.3 Temporary Storage (TSQ) Usage

| TSQ Name | Purpose | Record Layout | Written By | Read By | Lifecycle |
|----------|---------|--------------|-----------|---------|-----------|
| — | Not used by this transaction | — | — | — | — |

### 5.4 Transient Data (TDQ) Usage

| TDQ Name | Type | Purpose | Record Layout | Triggered Program |
|----------|------|---------|--------------|-------------------|
| JOBS | Extra-partition (mapped to JES Internal Reader) | Submit batch JCL job for transaction report generation | JCL-RECORD PIC X(80) — one JCL line per WRITEQ TD | TRNRPT00 (batch job, PROC TRANREPT) |

**TDQ Access Pattern**:
| Step | Program | CICS Command | Data Written | When |
|------|---------|-------------|-------------|------|
| 1–N | CORPT00C | WRITEQ TD QUEUE('JOBS') FROM(JCL-RECORD) LENGTH(80) | One JCL line per call; loops through JOB-LINES array until `/*EOF` or empty line encountered | After user confirms report submission (CONFIRMI = 'Y'/'y') |

**JCL Job Structure Written to TDQ**:
```
//TRNRPT00 JOB 'TRAN REPORT',CLASS=A,MSGCLASS=0,
// NOTIFY=&SYSUID
//*
//JOBLIB JCLLIB ORDER=('AWS.M2.CARDDEMO.PROC')
//*
//STEP10 EXEC PROC=TRANREPT
//*
//STEP05R.SYMNAMES DD *
TRAN-CARD-NUM,263,16,ZD
TRAN-PROC-DT,305,10,CH
PARM-START-DATE,C'<start-date>'
PARM-END-DATE,C'<end-date>'
/*
//STEP10R.DATEPARM DD *
<start-date> <end-date>
/*
/*EOF
```

---

## 6. Data Flow Analysis

### 6.1 Data Flow Through Transaction

```
  Terminal Input (CORPT0A)
       │
       ├── MONTHLYI (Monthly report selection)
       │       │
       │       └──▶ FUNCTION CURRENT-DATE → WS-CURDATE-DATA
       │                 │
       │                 ├──▶ WS-START-DATE = YYYY-MM-01 (1st of current month)
       │                 │         │
       │                 │         └──▶ PARM-START-DATE-1 (JCL SYMNAMES)
       │                 │         └──▶ PARM-START-DATE-2 (JCL DATEPARM)
       │                 │
       │                 └──▶ WS-END-DATE = last day of current month
       │                           (computed via INTEGER-OF-DATE / DATE-OF-INTEGER)
       │                           │
       │                           └──▶ PARM-END-DATE-1 (JCL SYMNAMES)
       │                           └──▶ PARM-END-DATE-2 (JCL DATEPARM)
       │
       ├── YEARLYI (Yearly report selection)
       │       │
       │       └──▶ FUNCTION CURRENT-DATE → WS-CURDATE-DATA
       │                 │
       │                 ├──▶ WS-START-DATE = YYYY-01-01
       │                 └──▶ WS-END-DATE   = YYYY-12-31
       │                       │
       │                       └──▶ PARM-START-DATE-1/2, PARM-END-DATE-1/2
       │
       ├── CUSTOMI (Custom report selection)
       │       │
       │       ├── SDTMMI / SDTDDI / SDTYYYYI
       │       │       │
       │       │       ├──▶ NUMVAL-C conversion → WS-NUM-99 / WS-NUM-9999
       │       │       ├──▶ Validation (numeric, range, calendar via CSUTLDTC)
       │       │       └──▶ WS-START-DATE (YYYY-MM-DD)
       │       │                 │
       │       │                 └──▶ PARM-START-DATE-1/2
       │       │
       │       └── EDTMMI / EDTDDI / EDTYYYYI
       │               │
       │               ├──▶ NUMVAL-C conversion → WS-NUM-99 / WS-NUM-9999
       │               ├──▶ Validation (numeric, range, calendar via CSUTLDTC)
       │               └──▶ WS-END-DATE (YYYY-MM-DD)
       │                         │
       │                         └──▶ PARM-END-DATE-1/2
       │
       └── CONFIRMI ('Y'/'N')
               │
               └──▶ Controls whether JCL is written to JOBS TDQ
                         │
                         └──▶ JOB-LINES(1..N) → JCL-RECORD → WRITEQ TD QUEUE('JOBS')
                                   (Contains PARM-START-DATE and PARM-END-DATE
                                    embedded in JCL SYMNAMES and DATEPARM DD cards)
```

### 6.2 Data Transformation Map

| Source | Source Location | Transformation | Target | Target Location |
|--------|---------------|----------------|--------|-----------------|
| MONTHLYI | Terminal (CORPT0AI) | Selection trigger — any non-space value | WS-REPORT-NAME = 'Monthly' | Working Storage |
| YEARLYI | Terminal (CORPT0AI) | Selection trigger — any non-space value | WS-REPORT-NAME = 'Yearly' | Working Storage |
| CUSTOMI | Terminal (CORPT0AI) | Selection trigger — any non-space value | WS-REPORT-NAME = 'Custom' | Working Storage |
| SDTMMI | Terminal (CORPT0AI) | NUMVAL-C → PIC 99 | WS-START-DATE-MM | Working Storage → JCL PARM |
| SDTDDI | Terminal (CORPT0AI) | NUMVAL-C → PIC 99 | WS-START-DATE-DD | Working Storage → JCL PARM |
| SDTYYYYI | Terminal (CORPT0AI) | NUMVAL-C → PIC 9999 | WS-START-DATE-YYYY | Working Storage → JCL PARM |
| EDTMMI | Terminal (CORPT0AI) | NUMVAL-C → PIC 99 | WS-END-DATE-MM | Working Storage → JCL PARM |
| EDTDDI | Terminal (CORPT0AI) | NUMVAL-C → PIC 99 | WS-END-DATE-DD | Working Storage → JCL PARM |
| EDTYYYYI | Terminal (CORPT0AI) | NUMVAL-C → PIC 9999 | WS-END-DATE-YYYY | Working Storage → JCL PARM |
| WS-START-DATE | Working Storage | Direct move to JCL template fields | PARM-START-DATE-1 / PARM-START-DATE-2 | JOB-DATA (JCL template) |
| WS-END-DATE | Working Storage | Direct move to JCL template fields | PARM-END-DATE-1 / PARM-END-DATE-2 | JOB-DATA (JCL template) |
| CONFIRMI | Terminal (CORPT0AI) | Y/y → proceed; N/n → cancel; other → error | Controls WRITEQ TD execution | — |
| FUNCTION CURRENT-DATE | System | Extract YEAR, MONTH, DAY | WS-CURDATE-DATA fields | Used for Monthly/Yearly date range calculation |
| WS-CURDATE-N | Working Storage | INTEGER-OF-DATE → subtract 1 → DATE-OF-INTEGER | WS-END-DATE (last day of month) | Monthly report end date |
| JOB-LINES(WS-IDX) | JOB-DATA (WS) | Direct move | JCL-RECORD PIC X(80) | Written to JOBS TDQ |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The transaction uses the shared CARDDEMO-COMMAREA (copybook `COCOM01Y`). No program-specific COMMAREA extension is defined for CORPT00C.

**CARDDEMO-COMMAREA (COCOM01Y — shared portion):**

| Field | PIC | Length | Direction | Purpose |
|-------|-----|--------|-----------|---------|
| CDEMO-FROM-TRANID | X(04) | 4 | IN/OUT | Originating transaction ID |
| CDEMO-FROM-PROGRAM | X(08) | 8 | IN/OUT | Originating program name |
| CDEMO-TO-TRANID | X(04) | 4 | OUT | Target transaction ID |
| CDEMO-TO-PROGRAM | X(08) | 8 | OUT | Target program for XCTL |
| CDEMO-USER-ID | X(08) | 8 | IN | Authenticated user ID |
| CDEMO-USER-TYPE | X(01) | 1 | IN | 'A' = Admin, 'U' = User |
| CDEMO-PGM-CONTEXT | 9(01) | 1 | IN/OUT | 0 = first entry, 1 = re-entry |
| CDEMO-CUST-ID | 9(09) | 9 | IN | Customer ID |
| CDEMO-ACCT-ID | 9(11) | 11 | IN | Account ID |
| CDEMO-CARD-NUM | 9(16) | 16 | IN | Card Number |
| CDEMO-LAST-MAP | X(7) | 7 | IN/OUT | Last BMS map displayed |
| CDEMO-LAST-MAPSET | X(7) | 7 | IN/OUT | Last BMS mapset used |

### 7.2 Return Code Conventions

This transaction does not use formal return codes in the COMMAREA. Errors are handled inline via screen messages (WS-MESSAGE → ERRMSGO). The CSUTLDTC date utility uses:

| Code | Meaning | User Display |
|------|---------|-------------|
| CSUTLDTC-RESULT-SEV-CD = '0000' | Date is valid | — (continue processing) |
| CSUTLDTC-RESULT-SEV-CD != '0000' and MSG-NUM != '2513' | Invalid date | "Start Date - Not a valid date..." / "End Date - Not a valid date..." |

### 7.3 Conversation State Management

- **COMMAREA preservation**: The entire CARDDEMO-COMMAREA is passed on every RETURN TRANSID('CR00'). CICS restores it automatically on the next dispatch.
- **Screen field state**: BMS FSET attribute ensures all fields are returned on every RECEIVE, preserving user-entered data across iterations.
- **No TSQ overflow**: All state fits within the COMMAREA; no temporary storage queues are used.
- **PGM-CONTEXT flag**: Distinguishes first entry (0 — display empty screen) from subsequent interactions (1 — process input).
- **JCL template in WORKING-STORAGE**: The JOB-DATA structure contains the complete JCL template with embedded parameter placeholders (PARM-START-DATE-1/2, PARM-END-DATE-1/2) that are populated at runtime before writing to the TDQ. The template is defined as a REDEFINES of JOB-DATA-1 with JOB-LINES OCCURS 1000 TIMES PIC X(80).

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  Normal Path:
    Input → Select Report Type → (Custom: Validate Dates) → Confirm 'Y'
    → Write JCL to JOBS TDQ → Success Message

  No Report Type Selected:
    Input → EVALUATE ──WHEN OTHER──▶ "Select a report type to print report..."
                                     → Cursor to MONTHLY, RETURN TRANSID

  Custom Date Validation Error (Empty Field):
    Input → CUSTOMI selected → Check fields
    ──EMPTY──▶ "Start/End Date - Month/Day/Year can NOT be empty..."
               → Cursor to empty field, RETURN TRANSID

  Custom Date Validation Error (Invalid Value):
    Input → CUSTOMI selected → Validate
    ──FAIL──▶ "Start/End Date - Not a valid Month/Day/Year..."
              → Cursor to field, RETURN TRANSID

  Custom Date Calendar Validation Error:
    Input → Assemble date → CALL CSUTLDTC
    ──FAIL──▶ "Start/End Date - Not a valid date..."
              → Cursor to SDTMM/EDTMM, RETURN TRANSID

  Confirmation Error:
    Input → Confirm = spaces/LOW-VALUES ──▶ "Please confirm to print the <type> report..."
    Input → Confirm = 'N'/'n'           ──▶ (Clear all fields, redisplay)
    Input → Confirm = other             ──▶ '"<value>" is not a valid value to confirm...'

  TDQ Write Error:
    WRITEQ TD ──OTHER──▶ "Unable to Write TDQ (JOBS)..."

  Invalid Key:
    Any key not ENTER or PF3 ──▶ "Invalid key pressed. Please see below..."
```

### 8.2 Error Handling Matrix

| Error Condition | RESP/RESP2 Code | Program Action | User Message | Logging |
|----------------|----------------|----------------|-------------|---------|
| TDQ write failure | OTHER (not NORMAL) | Set ERR-FLG, cursor to MONTHLY | "Unable to Write TDQ (JOBS)..." | DISPLAY RESP/REAS to SYSOUT |
| Map receive error | — | RESP/RESP2 captured but not explicitly evaluated | — | — |

### 8.3 HANDLE ABEND / HANDLE CONDITION

| Condition | Handler Paragraph | Action |
|-----------|------------------|--------|
| — | No explicit HANDLE ABEND | Default CICS abend handling applies |

### 8.4 User Error Messages

| Message ID | Text | Trigger Condition | Screen Position |
|-----------|------|-------------------|-----------------|
| — | "Select a report type to print report..." | No report type field (MONTHLY, YEARLY, CUSTOM) is marked | ERRMSG (line 23, col 1) |
| — | "Start Date - Month can NOT be empty..." | CUSTOMI selected, SDTMMI is spaces/LOW-VALUES | ERRMSG (line 23, col 1) |
| — | "Start Date - Day can NOT be empty..." | CUSTOMI selected, SDTDDI is spaces/LOW-VALUES | ERRMSG (line 23, col 1) |
| — | "Start Date - Year can NOT be empty..." | CUSTOMI selected, SDTYYYYI is spaces/LOW-VALUES | ERRMSG (line 23, col 1) |
| — | "End Date - Month can NOT be empty..." | CUSTOMI selected, EDTMMI is spaces/LOW-VALUES | ERRMSG (line 23, col 1) |
| — | "End Date - Day can NOT be empty..." | CUSTOMI selected, EDTDDI is spaces/LOW-VALUES | ERRMSG (line 23, col 1) |
| — | "End Date - Year can NOT be empty..." | CUSTOMI selected, EDTYYYYI is spaces/LOW-VALUES | ERRMSG (line 23, col 1) |
| — | "Start Date - Not a valid Month..." | SDTMMI not numeric or > '12' | ERRMSG (line 23, col 1) |
| — | "Start Date - Not a valid Day..." | SDTDDI not numeric or > '31' | ERRMSG (line 23, col 1) |
| — | "Start Date - Not a valid Year..." | SDTYYYYI not numeric | ERRMSG (line 23, col 1) |
| — | "End Date - Not a valid Month..." | EDTMMI not numeric or > '12' | ERRMSG (line 23, col 1) |
| — | "End Date - Not a valid Day..." | EDTDDI not numeric or > '31' | ERRMSG (line 23, col 1) |
| — | "End Date - Not a valid Year..." | EDTYYYYI not numeric | ERRMSG (line 23, col 1) |
| — | "Start Date - Not a valid date..." | CSUTLDTC rejects assembled start date (sev != '0000', msg != '2513') | ERRMSG (line 23, col 1) |
| — | "End Date - Not a valid date..." | CSUTLDTC rejects assembled end date (sev != '0000', msg != '2513') | ERRMSG (line 23, col 1) |
| — | "Please confirm to print the <type> report..." | CONFIRMI is spaces or LOW-VALUES | ERRMSG (line 23, col 1) |
| — | '"<value>" is not a valid value to confirm...' | CONFIRMI is not Y/y/N/n and not spaces | ERRMSG (line 23, col 1) |
| — | "Unable to Write TDQ (JOBS)..." | WRITEQ TD returns non-NORMAL response | ERRMSG (line 23, col 1) |
| — | "Invalid key pressed. Please see below..." | AID key is not ENTER or PF3 (from CCDA-MSG-INVALID-KEY) | ERRMSG (line 23, col 1) |
| — | "<Type> report submitted for printing ..." | Successful completion of all TDQ writes | ERRMSG (line 23, col 1), color GREEN |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Control | Implementation |
|---------|---------------|
| Transaction authorization | RACF TCICSTRN class — CR00 transaction must be authorized for user |
| Resource-level security | RACF FACILITY / TDQ profiles for JOBS queue |
| Application-level check | If EIBCALEN = 0 (no COMMAREA — direct invocation without login), XCTL to sign-on screen COSGN00C |

### 9.2 Data Access Authorization

| Data Resource | Required Authority | Check Point |
|--------------|-------------------|-------------|
| JOBS TDQ | WRITE (WRITEQ TD) | CICS Transient Data resource security |

### 9.3 Audit Trail

| Event | Trigger | Data Logged | Destination |
|-------|---------|-------------|-------------|
| TDQ write failure | WRITEQ TD returns non-NORMAL RESP | RESP code, REAS code | DISPLAY → SYSOUT (CICS system log) |
| JCL submission | Each WRITEQ TD QUEUE('JOBS') | JCL line content (80 bytes) | JOBS TDQ → JES internal reader |
| No explicit audit logging | — | — | Transaction may be audited via CICS journal/SMF records at the system level |

---

## 10. Performance Characteristics

### 10.1 Performance Profile

| Metric | Baseline | Peak | SLA |
|--------|----------|------|-----|
| Response time | < 1 second | < 2 seconds | < 2 seconds |
| CPU per transaction | Low (validation + TDQ writes) | — | — |
| File I/O count | 0 VSAM I/O; ~15–20 TDQ WRITEQ TD calls per submission | — | — |
| DB2 getpages | 0 (no DB2 access) | 0 | — |
| CICS suspend time | Minimal | — | — |

### 10.2 Performance Considerations

- **No VSAM file I/O**: This transaction does not directly access any VSAM files; all data access is delegated to the submitted batch job.
- **TDQ write loop**: The JCL template contains approximately 15–20 lines. Each line is written individually via WRITEQ TD. This is a lightweight operation as the JOBS TDQ is an extra-partition queue mapped to the JES internal reader.
- **Date computation**: Monthly report uses FUNCTION CURRENT-DATE, INTEGER-OF-DATE, and DATE-OF-INTEGER intrinsic functions to calculate the last day of the month — negligible overhead.
- **CALL to CSUTLDTC**: Two CALL invocations for Custom date range — negligible overhead.
- **COMMAREA size**: Standard shared COMMAREA only; no program-specific extension needed.
- **BMS SEND/RECEIVE**: Standard map I/O with ERASE on first send — one pair per pseudo-conversational iteration.
- **Batch job execution**: The report generation itself runs asynchronously in batch; the online transaction completes as soon as the JCL is submitted to the TDQ.

---

## 11. Testing Approach

### 11.1 Test Scenarios

| ID | Scenario | Input | Expected Screen/Output | Priority |
|----|----------|-------|----------------------|----------|
| TC-001 | Monthly report submission | Mark Monthly field, Confirm=Y, press ENTER | "Monthly report submitted for printing ..." (GREEN), fields cleared | High |
| TC-002 | Yearly report submission | Mark Yearly field, Confirm=Y, press ENTER | "Yearly report submitted for printing ..." (GREEN), fields cleared | High |
| TC-003 | Custom report — valid date range | Mark Custom, Start=01/15/2025, End=06/30/2025, Confirm=Y | "Custom report submitted for printing ..." (GREEN), fields cleared | High |
| TC-004 | No report type selected | Leave all type fields blank, press ENTER | "Select a report type to print report..." | High |
| TC-005 | Custom — empty start month | Mark Custom, leave SDTMM blank | "Start Date - Month can NOT be empty..." | Medium |
| TC-006 | Custom — empty start day | Mark Custom, fill SDTMM, leave SDTDD blank | "Start Date - Day can NOT be empty..." | Medium |
| TC-007 | Custom — empty start year | Mark Custom, fill SDTMM+SDTDD, leave SDTYYYY blank | "Start Date - Year can NOT be empty..." | Medium |
| TC-008 | Custom — empty end month | Mark Custom, fill all start fields, leave EDTMM blank | "End Date - Month can NOT be empty..." | Medium |
| TC-009 | Custom — empty end day | Mark Custom, fill start + EDTMM, leave EDTDD blank | "End Date - Day can NOT be empty..." | Medium |
| TC-010 | Custom — empty end year | Mark Custom, fill start + EDTMM+EDTDD, leave EDTYYYY blank | "End Date - Year can NOT be empty..." | Medium |
| TC-011 | Custom — invalid start month (>12) | Mark Custom, SDTMM=15 | "Start Date - Not a valid Month..." | Medium |
| TC-012 | Custom — invalid start day (>31) | Mark Custom, SDTDD=35 | "Start Date - Not a valid Day..." | Medium |
| TC-013 | Custom — non-numeric start year | Mark Custom, SDTYYYY=ABCD | "Start Date - Not a valid Year..." | Medium |
| TC-014 | Custom — invalid calendar start date | Mark Custom, Start=02/30/2025 | "Start Date - Not a valid date..." | Medium |
| TC-015 | Custom — invalid calendar end date | Mark Custom, End=04/31/2025 | "End Date - Not a valid date..." | Medium |
| TC-016 | Confirm prompt — no confirmation | Select report type, leave Confirm blank | "Please confirm to print the <type> report..." | Medium |
| TC-017 | Confirm N — cancel | Select report type, Confirm=N | Fields cleared, no submission | Medium |
| TC-018 | Confirm invalid value | Select report type, Confirm=X | '"X" is not a valid value to confirm...' | Medium |
| TC-019 | PF3 exit | Press PF3 at any point | Return to Main Menu (COMEN01C) | Medium |
| TC-020 | Invalid PF key | Press PF2, PF4, etc. | "Invalid key pressed. Please see below..." | Low |
| TC-021 | TDQ write failure | Simulate JOBS TDQ unavailability | "Unable to Write TDQ (JOBS)..." | Low |
| TC-022 | Direct invocation without login | Invoke CR00 without COMMAREA (EIBCALEN=0) | XCTL to COSGN00C (sign-on screen) | Low |

### 11.2 Test Data Requirements

| File/Table | Setup | Method |
|-----------|-------|--------|
| JOBS TDQ | Extra-partition TDQ defined and mapped to JES internal reader | CICS CSD: DEFINE TDQUEUE(JOBS) TYPE(EXTRA) ... |
| TRANREPT PROC | JCL procedure installed in AWS.M2.CARDDEMO.PROC | JCL library setup |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| ID | Description | Impact | Workaround |
|----|-------------|--------|-----------|
| KI-001 | Typo in paragraph name: `WIRTE-JOBSUB-TDQ` instead of `WRITE-JOBSUB-TDQ` | No functional impact (cosmetic) | None needed — paragraph is called correctly despite the typo |
| KI-002 | Custom date validation does not check that Start Date < End Date | User could submit a report with inverted date range (end before start) | Rely on batch job to handle date range ordering, or add validation |
| KI-003 | The EVALUATE for report type selection is not mutually exclusive — if both MONTHLY and YEARLY fields are marked, MONTHLY takes precedence (first match) | User may unintentionally mark multiple options | Add explicit mutual exclusion check or clear other fields |
| KI-004 | No explicit HANDLE ABEND — unexpected abends use default CICS handling | User sees CICS ABEND screen rather than a friendly error | Add HANDLE ABEND with a user-friendly error screen |
| KI-005 | Custom date validation uses cascading IF statements that do not short-circuit — each validation runs independently even after an error is found | Multiple error messages may be set but only the last error is displayed; cursor positioning may be overwritten | Restructure to stop after first error |
| KI-006 | SEND-TRNRPT-SCREEN issues GO TO RETURN-TO-CICS which performs RETURN TRANSID — when called mid-validation, this effectively exits the validation chain | Only the first validation error triggers a screen return; subsequent validations for the same iteration are skipped | This is by design but could confuse maintainers |
| KI-007 | JOB-DATA template hardcodes JES job card parameters (CLASS=A, MSGCLASS=0, NOTIFY=&SYSUID) | Not configurable without code change | Externalize JCL template to a file or configuration |

### 12.2 Modernization Considerations

| Area | Current State | Target State | Complexity |
|------|--------------|-------------|-----------|
| Screen I/O | BMS Map CORPT0A / 3270 terminal | Web form with radio buttons for report type, date pickers for custom range | Low — simple form with 3 radio buttons + date inputs |
| Report Submission | JCL submitted to JES via TDQ (WRITEQ TD to internal reader) | REST API endpoint triggering a report generation service (e.g., microservice, Lambda, or scheduled task) | Medium — replace TDQ/JCL with async job submission |
| Date Handling | Manual field-by-field date entry (MM/DD/YYYY as separate fields), CSUTLDTC utility for validation | HTML5 date picker with native browser validation, language-native date parsing (Java `LocalDate.parse()`, etc.) | Low |
| Report Generation | Batch COBOL program (TRANREPT procedure) reading VSAM TRANSACT file | SQL query on RDBMS, reporting service (e.g., JasperReports, Crystal, or modern BI tools) | Medium — depends on batch program complexity |
| JCL Template | Hardcoded in WORKING-STORAGE as 80-byte FILLER lines | Configuration file, environment variables, or template engine | Low |
| Confirmation | Two-step ENTER→Confirm(Y)→ENTER | Single-submit with client-side confirmation dialog (JavaScript confirm) | Low |
| State Management | COMMAREA pseudo-conversational | HTTP session or stateless REST with tokens | Low — minimal state required (no program-specific COMMAREA extension) |
| Asynchronous Processing | JES batch job (fire-and-forget from online) | Message queue (SQS, RabbitMQ) or async API call with job status tracking | Medium — adds job monitoring capability |
| Report Delivery | Printed output (SYSOUT) | PDF download, email delivery, or dashboard integration | Medium |

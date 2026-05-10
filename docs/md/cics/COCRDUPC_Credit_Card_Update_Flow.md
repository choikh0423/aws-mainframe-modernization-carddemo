# Credit Card Update (CCUP) – CICS Online Transaction Analysis

**Document Version**: 1.0  
**Last Updated**: 2026-05-10  
**Author(s)**: Devin / CardDemo Modernization Team  
**Status**: Draft  
**Parent System Document**: CardDemo Mainframe Analysis

---

## Identification

| Attribute | Value |
|-----------|-------|
| Transaction ID | CCUP |
| Entry Program | COCRDUPC |
| Business Domain | Credit Card Management |
| Owning Team | CardDemo Application Team |
| Design Pattern | Pseudo-conversational |
| Criticality | High |
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

- **What**: Allows a user to update the mutable attributes of an existing credit card record — specifically the embossed name, active status (Y/N), and expiration date (month/year). The card number and account association are immutable and cannot be changed.
- **Who**: Any authenticated CardDemo user (regular users and admins) — menu option 5 ("Credit Card Update"). Also reachable from the Credit Card List screen (COCRDLIC).
- **When**: Real-time, on-demand during business hours when a card's details need to be corrected or updated (e.g., name change, status toggle, expiry extension).
- **Why**: Supports operational maintenance of credit card master records — cardholders may change names, cards may be activated/deactivated, or expiration dates extended.
- **What** is the business outcome: The CARDDAT VSAM file is updated with the modified card record. The update uses optimistic concurrency — if another user changed the record between fetch and save, the updated values are re-displayed for review.

### 1.2 Business Flow Diagram

```
  ┌──────────────────────────┐
  │ Business Trigger         │  User selects Option 5 ("Credit Card Update") from Main Menu
  └────────────┬─────────────┘  OR navigates from Credit Card List screen (COCRDLIC)
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 1            │  Enter Account Number and Card Number to identify the card
  └────────────┬─────────────┘
               │
               ▼
  ┌──────────────────────────┐
  │ System Response          │  READ card record from CARDDAT by Card Number
  └────────────┬─────────────┘  Display card details (name, status, expiry)
               │
               ▼
  ┌──────────────────────────┐
  │ User Action 2            │  Modify editable fields:
  └────────────┬─────────────┘  Name on Card, Active Status (Y/N),
               │                Expiry Month, Expiry Year
               ▼
  ┌──────────────────────────┐
  │ System Validation        │  Validate all changed fields:
  └────────────┬─────────────┘  Name (alpha+spaces only), Status (Y/N),
               │                Month (1–12), Year (1950–2099)
           ┌───┴───┐
           │Decision│  Validation passes?
           └───┬───┘
              ╱ ╲
        Yes ╱     ╲ No
           ▼       ▼
  ┌───────────────┐  ┌─────────────────────┐
  │ Prompt: "Press│  │ Error message shown, │
  │ F5 to save"   │  │ cursor to bad field  │
  └───────┬───────┘  └─────────────────────┘
          │
      ┌───┴───┐
      │ F5?   │
      └───┬───┘
         ╱ ╲
   Yes ╱     ╲ No (other key)
      ▼       ▼
┌─────────────────────────┐  ┌──────────────────────────────────┐
│ READ with UPDATE lock   │  │ Changes remain pending;          │
│ Check optimistic lock   │  │ user can continue editing        │
│ REWRITE CARDDAT         │  └──────────────────────────────────┘
└───────────┬─────────────┘
            │
        ┌───┴───┐
        │Result?│
        └───┬───┘
           ╱ ╲
     OK  ╱     ╲ Changed by others
        ▼       ▼
┌──────────────────┐  ┌────────────────────────────────────────┐
│ "Changes         │  │ "Record changed by some one else.      │
│  committed to    │  │  Please review" — refreshed data shown │
│  database"       │  └────────────────────────────────────────┘
└──────────────────┘
```

### 1.3 Business Rules

| Rule ID | Description | Condition | Action |
|---------|-------------|-----------|--------|
| BR-001 | Account number required | Account Number is blank or non-numeric | Error: "Account number not provided" or "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER" |
| BR-002 | Card number required | Card Number is blank or non-numeric | Error: "Card number not provided" or "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" |
| BR-003 | Card must exist | Card Number not found in CARDDAT file | Error: "Did not find cards for this search condition" |
| BR-004 | Card name required | Name on Card is blank | Error: "Card name not provided" |
| BR-005 | Card name alpha only | Name on Card contains non-alphabetic, non-space characters | Error: "Card name can only contain alphabets and spaces" |
| BR-006 | Card status Y or N | Active Status is not 'Y' or 'N' | Error: "Card Active Status must be Y or N" |
| BR-007 | Expiry month valid | Expiry Month is blank or not between 1 and 12 | Error: "Card expiry month must be between 1 and 12" |
| BR-008 | Expiry year valid | Expiry Year is blank or not between 1950 and 2099 | Error: "Invalid card expiry year" |
| BR-009 | Change detection | New card data equals old card data (case-insensitive) | Info: "No change detected with respect to values fetched." |
| BR-010 | Confirmation before save | User must press F5 after validation passes | Info: "Changes validated.Press F5 to save" |
| BR-011 | Optimistic concurrency | Record changed by another user between fetch and save | Error: "Record changed by some one else. Please review" — refreshed data shown |
| BR-012 | Lock failure | Cannot obtain UPDATE lock on record | Error: "Could not lock record for update" |
| BR-013 | Rewrite failure | REWRITE CICS command fails | Error: "Update of record failed" |
| BR-014 | Name case conversion | Card name is uppercased on read | INSPECT CONVERTING lower TO upper applied to CARD-EMBOSSED-NAME on fetch |

### 1.4 User Interactions

| Step | User Action | System Response | Business Meaning |
|------|-------------|-----------------|-----------------|
| 1 | Select Option 5 from Main Menu | XCTL to COCRDUPC; display empty search screen (CCRDUPA) with Account/Card fields | Navigate to credit card update |
| 1a | (Alternate) Select a card from Credit Card List (COCRDLIC) | XCTL to COCRDUPC with COMMAREA pre-populated; fetch card data and display details | Navigate from list to update |
| 2 | Enter Account Number and Card Number, press ENTER | Validate search keys, READ card from CARDDAT, display card details | Identify card to update |
| 3 | Modify Name, Status, Expiry Month, Expiry Year, press ENTER | Validate all changed fields; if valid, prompt "Changes validated.Press F5 to save" | Submit changes for review |
| 4a | Press F5 (Save) | Lock record, check for concurrent changes, REWRITE record, display "Changes committed to database" | Commit update |
| 4b | Press ENTER (without F5) | Re-validate; show "Changes validated.Press F5 to save" again | User defers saving |
| Alt-A | Press PF3 | XCTL back to previous screen (Credit Card List or Main Menu) | Exit without saving |
| Alt-B | Press PF12 (Cancel) | Discard pending changes, re-read original data from file, display original values | Cancel edits |
| Alt-C | Press any other key | Treated as ENTER (invalid keys remapped) | Invalid key handled gracefully |

---

## 2. Technical Flow Analysis

### 2.1 Technical Flow Diagram – Overview

```
  TERMINAL
     │
     │  TRANSID: CCUP
     ▼
  ┌──────────────────────────────────────────────────────┐
  │ CICS REGION                                          │
  │                                                      │
  │  ┌──────────────┐                                    │
  │  │ COCRDUPC     │ (Entry & only online program)      │
  │  │ (Card Update)│                                    │
  │  └──┬────┬──────┘                                    │
  │     │    │                                            │
  │     ▼    ▼                                            │
  │  ┌──────┐ ┌────────────────────────────────────┐     │
  │  │BMS   │ │ VSAM Files                         │     │
  │  │MAP:  │ │  CARDDAT (Card master KSDS)        │     │
  │  │CCRDU │ │                                    │     │
  │  │PA    │ │                                    │     │
  │  └──────┘ └────────────────────────────────────┘     │
  │                                                      │
  └──────────────────────────────────────────────────────┘
```

### 2.2 Technical Flow Diagram – Detailed (Step-by-Step)

```
  Iteration 1a (Fresh entry — from Main Menu via XCTL):
  1. COMEN01C XCTLs to COCRDUPC with CARDDEMO-COMMAREA
  2. EIBCALEN > 0; CDEMO-FROM-PROGRAM = 'COMEN01C', CDEMO-PGM-REENTER = 0
  3. INITIALIZE WS-THIS-PROGCOMMAREA
  4. SET CDEMO-PGM-REENTER TO TRUE
  5. SET CCUP-DETAILS-NOT-FETCHED TO TRUE
  6. PERFORM 3000-SEND-MAP → send empty search screen (CCRDUPA)
     └── Info msg: "Please enter Account and Card Number"
  7. RETURN TRANSID('CCUP') COMMAREA(WS-COMMAREA)

  Iteration 1b (Entry from Credit Card List — COCRDLIC):
  1. COCRDLIC XCTLs to COCRDUPC with CARDDEMO-COMMAREA
     (CDEMO-ACCT-ID and CDEMO-CARD-NUM pre-populated)
  2. EIBCALEN > 0; CDEMO-FROM-PROGRAM = 'COCRDLIC', CDEMO-PGM-ENTER = TRUE
  3. MOVE CDEMO-ACCT-ID → CC-ACCT-ID-N, CDEMO-CARD-NUM → CC-CARD-NUM-N
  4. PERFORM 9000-READ-DATA
     └── PERFORM 9100-GETCARD-BYACCTCARD
         └── READ FILE('CARDDAT') RIDFLD(WS-CARD-RID-CARDNUM)
         └── RESP NORMAL → populate CCUP-OLD-DETAILS with card data
  5. SET CCUP-SHOW-DETAILS TO TRUE
  6. PERFORM 3000-SEND-MAP → display card details for editing
     └── Info msg: "Details of selected card shown above"
  7. RETURN TRANSID('CCUP') COMMAREA(WS-COMMAREA)

  Iteration 2 (User enters search keys — ENTER pressed):
  8.  CICS dispatches → COCRDUPC (COMMAREA restored)
  9.  EIBCALEN > 0; CDEMO-PGM-REENTER = 1; CCUP-DETAILS-NOT-FETCHED
  10. PERFORM 1000-PROCESS-INPUTS
      └── 1100-RECEIVE-MAP: RECEIVE MAP('CCRDUPA') INTO(CCRDUPAI)
      └── 1200-EDIT-MAP-INPUTS:
          ├── 1210-EDIT-ACCOUNT: validate Account Number (non-blank, numeric)
          └── 1220-EDIT-CARD: validate Card Number (non-blank, numeric)
  11. PERFORM 2000-DECIDE-ACTION
      └── CCUP-DETAILS-NOT-FETCHED:
          └── PERFORM 9000-READ-DATA → READ CARDDAT
  12. PERFORM 3000-SEND-MAP → display card details or error
  13. RETURN TRANSID('CCUP') COMMAREA(WS-COMMAREA)

  Iteration 3 (User modifies data — ENTER pressed):
  14. CICS dispatches → COCRDUPC (COMMAREA restored)
  15. PERFORM 1000-PROCESS-INPUTS
      └── 1100-RECEIVE-MAP
      └── 1200-EDIT-MAP-INPUTS:
          ├── Compare CCUP-NEW-CARDDATA vs CCUP-OLD-CARDDATA (UPPER-CASE)
          ├── If no change → "No change detected..."
          ├── If changed:
          │   ├── 1230-EDIT-NAME: non-blank, alpha+spaces only
          │   ├── 1240-EDIT-CARDSTATUS: must be 'Y' or 'N'
          │   ├── 1250-EDIT-EXPIRY-MON: must be 1–12
          │   └── 1260-EDIT-EXPIRY-YEAR: must be 1950–2099
          └── If all valid → SET CCUP-CHANGES-OK-NOT-CONFIRMED
  16. PERFORM 2000-DECIDE-ACTION
      └── CCUP-SHOW-DETAILS: if no errors → SET CCUP-CHANGES-OK-NOT-CONFIRMED
  17. PERFORM 3000-SEND-MAP
      └── Info msg: "Changes validated.Press F5 to save"
      └── F5/F12 key hints shown (FKEYSC made visible)
  18. RETURN TRANSID('CCUP') COMMAREA(WS-COMMAREA)

  Iteration 4 (User presses F5 — save confirmed):
  19. CICS dispatches → COCRDUPC (COMMAREA restored)
  20. CCARD-AID-PFK05 AND CCUP-CHANGES-OK-NOT-CONFIRMED → valid F5
  21. PERFORM 1000-PROCESS-INPUTS (receive and re-validate)
  22. PERFORM 2000-DECIDE-ACTION
      └── CCUP-CHANGES-OK-NOT-CONFIRMED AND CCARD-AID-PFK05:
          └── PERFORM 9200-WRITE-PROCESSING
              ├── READ FILE('CARDDAT') UPDATE (lock record)
              │   └── RESP ≠ NORMAL → "Could not lock record for update"
              ├── PERFORM 9300-CHECK-CHANGE-IN-REC
              │   └── Compare current record vs CCUP-OLD-DETAILS
              │   └── If changed → "Record changed by some one else. Please review"
              │       (refresh CCUP-OLD-DETAILS, redisplay)
              ├── Prepare CARD-UPDATE-RECORD from CCUP-NEW-DETAILS
              │   └── STRING expiry as YYYY-MM-DD
              └── REWRITE FILE('CARDDAT') FROM(CARD-UPDATE-RECORD)
                  ├── NORMAL → SET CCUP-CHANGES-OKAYED-AND-DONE
                  └── OTHER → "Update of record failed"
  23. PERFORM 3000-SEND-MAP
      └── Info msg: "Changes committed to database"
  24. RETURN TRANSID('CCUP') COMMAREA(WS-COMMAREA)
```

### 2.3 Pseudo-Conversational Flow

| Iteration | EIBCALEN | CCUP-CHANGE-ACTION | User Action | Program Entry Point | Screen Sent | Next TRANSID |
|-----------|----------|--------------------|-------------|---------------------|-------------|--------------|
| 1a (fresh) | >0 | LOW-VALUES (not fetched) | XCTL from Main Menu | EVALUATE → CCUP-DETAILS-NOT-FETCHED AND CDEMO-PGM-ENTER | CCRDUPA (empty search) | CCUP |
| 1b (from list) | >0 | LOW-VALUES (not fetched) | XCTL from Credit Card List | EVALUATE → CDEMO-PGM-ENTER AND CDEMO-FROM-PROGRAM = COCRDLIC | CCRDUPA (card details shown) | CCUP |
| 2 | >0 | 'S' (show details) | Enter search keys, ENTER | 1000-PROCESS-INPUTS → 2000-DECIDE-ACTION | CCRDUPA (card details or error) | CCUP |
| 3 | >0 | 'N' (changes OK, not confirmed) | Modify fields, ENTER | 1000-PROCESS-INPUTS → 2000-DECIDE-ACTION | CCRDUPA ("Press F5 to save") | CCUP |
| 4 | >0 | 'C' (changes done) | Press F5 | 9200-WRITE-PROCESSING | CCRDUPA (success message) | CCUP |
| N | >0 | Any | PF3 | XCTL to CDEMO-TO-PROGRAM | — | — (XCTL) |

### 2.4 CICS Command Flow

| Step | CICS Command | Purpose | Response Handling |
|------|-------------|---------|-------------------|
| 1 | HANDLE ABEND LABEL(ABEND-ROUTINE) | Register abend handler | — |
| 2 | RECEIVE MAP('CCRDUPA') MAPSET('COCRDUP') INTO(CCRDUPAI) | Capture user input from terminal | RESP/RESP2 stored in WS-RESP-CD/WS-REAS-CD |
| 3 | READ FILE('CARDDAT') RIDFLD(WS-CARD-RID-CARDNUM) INTO(CARD-RECORD) | Read card record by card number (browse mode) | NORMAL → populate details; NOTFND → error; OTHER → file error message |
| 4 | READ FILE('CARDDAT') UPDATE RIDFLD(WS-CARD-RID-CARDNUM) INTO(CARD-RECORD) | Lock card record for update | NORMAL → continue; OTHER → "Could not lock record for update" |
| 5 | REWRITE FILE('CARDDAT') FROM(CARD-UPDATE-RECORD) | Write updated card record | NORMAL → success; OTHER → "Update of record failed" |
| 6 | SEND MAP('CCRDUPA') MAPSET('COCRDUP') FROM(CCRDUPAO) CURSOR ERASE FREEKB | Send screen to terminal | RESP stored in WS-RESP-CD |
| 7 | SYNCPOINT | Commit changes before XCTL on exit | — |
| 8 | RETURN TRANSID('CCUP') COMMAREA(WS-COMMAREA) LENGTH(2000) | Pseudo-conversational return | — |
| 9 | XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA(CARDDEMO-COMMAREA) | Transfer control to caller on PF3 | — |
| 10 | SEND FROM(ABEND-DATA) NOHANDLE ERASE | Send abend message to terminal | — |
| 11 | HANDLE ABEND CANCEL | Cancel abend handler before ABEND | — |
| 12 | ABEND ABCODE('9999') | Force abend for unexpected scenarios | — |

---

## 3. Screen Flow and BMS Maps

### 3.1 Screen Flow Diagram

```
  ┌─────────────┐                ┌─────────────────────────────────────┐
  │  Main Menu  │   Option 5     │  CCRDUPA (Credit Card Update)       │
  │  (COMEN01C) │───────────────▶│                                     │
  └─────────────┘                │  Account Number  : ___________      │
        ▲                        │  Card Number     : ________________ │
        │ PF3                    │  ─────────────────────────────────   │
        │                        │  Name on card    : ________________ │
        │                        │  Card Active Y/N : _                │
        │                        │  Expiry Date     : MM / YYYY        │
  ┌─────────────┐                │                                     │
  │  Card List  │                │  [Info message line]                │
  │  (COCRDLIC) │───────────────▶│  [Error message line]               │
  └─────────────┘  Select card   │  ENTER=Process F3=Exit              │
        ▲                        │  F5=Save F12=Cancel                 │
        │ PF3 (if came           └─────────────────────────────────────┘
        │  from list)              │         │            │
        │                          │ ENTER   │ F12        │ F5 (when
        └──────────────────────────┤ (loops  │ (cancel,   │  confirmed)
                                   │  back   │  re-read   │
                                   │  to     │  original)  │
                                   │  self)  │            │
                                   ▼         ▼            ▼
                                 [Same screen — CCRDUPA]
```

### 3.2 BMS Map Inventory

| Map Name | Mapset | Purpose | Fields (Key) | PF Key Actions |
|----------|--------|---------|-------------|----------------|
| CCRDUPA | COCRDUP | Credit Card Update — search, display, edit, confirm screen | Account Number, Card Number, Name on Card, Card Active Status, Expiry Month, Expiry Year | ENTER=Process, PF3=Exit, PF5=Save (when confirmed), PF12=Cancel |

### 3.3 Screen Field Details

#### Map: CCRDUPA

| Field Name | BMS Name | Attribute | Length | Type | Validation | Business Meaning |
|-----------|----------|-----------|--------|------|-----------|-----------------|
| TRNNAME | TRNNAME | ASKIP,FSET,NORM | 4 | Alpha | — | Transaction ID (CCUP) |
| TITLE01 | TITLE01 | ASKIP,NORM | 40 | Alpha | — | "AWS Mainframe Modernization" |
| CURDATE | CURDATE | ASKIP,NORM | 8 | Date | — | Current date mm/dd/yy |
| PGMNAME | PGMNAME | ASKIP,NORM | 8 | Alpha | — | Program name (COCRDUPC) |
| TITLE02 | TITLE02 | ASKIP,NORM | 40 | Alpha | — | "CardDemo" |
| CURTIME | CURTIME | ASKIP,NORM | 8 | Time | — | Current time hh:mm:ss |
| Account Number | ACCTSID | FSET,IC,NORM,PROT (initially); toggled FSET/PROT by context | 11 | Numeric | Non-blank, numeric, 11-digit | Account ID associated with the card |
| Card Number | CARDSID | FSET,NORM,UNPROT (initially); toggled FSET/PROT by context | 16 | Numeric | Non-blank, numeric, 16-digit | Credit card number (primary key) |
| Name on Card | CRDNAME | UNPROT | 50 | Alpha | Non-blank; alphabets and spaces only (INSPECT converts alpha→spaces; if residue exists → error) | Embossed name on the card |
| Card Active Y/N | CRDSTCD | UNPROT | 1 | Alpha | Must be 'Y' or 'N' | Card active status flag |
| Expiry Month | EXPMON | UNPROT,JUSTIFY(RIGHT) | 2 | Numeric | Non-blank; must be 1–12 (88 VALID-MONTH) | Card expiration month |
| Expiry Year | EXPYEAR | UNPROT,JUSTIFY(RIGHT) | 4 | Numeric | Non-blank; must be 1950–2099 (88 VALID-YEAR) | Card expiration year |
| Expiry Day | EXPDAY | DRK,FSET,PROT | 2 | Numeric | — (hidden, not user-editable; preserved from original record) | Card expiration day (hidden) |
| Info Message | INFOMSG | PROT | 40 | Alpha | — | Informational/status message |
| Error Message | ERRMSG | ASKIP,BRT,FSET | 80 | Alpha | — | Error/validation message (red) |
| Function Keys | FKEYS | ASKIP,NORM | 21 | Alpha | — | "ENTER=Process F3=Exit" (always shown) |
| Function Keys (Confirm) | FKEYSC | ASKIP,DRK | 18 | Alpha | — | "F5=Save F12=Cancel" (shown when confirmation pending) |

### 3.4 PF Key / AID Key Handling

| AID Key | Context | Action |
|---------|---------|--------|
| ENTER | Any | Process inputs: validate search keys or validate data changes |
| PF3 | Any | Exit — SYNCPOINT then XCTL to CDEMO-FROM-PROGRAM (Credit Card List or Main Menu) |
| PF5 | CCUP-CHANGES-OK-NOT-CONFIRMED only | Save confirmed changes — lock, check optimistic concurrency, REWRITE record |
| PF12 | After details shown (not initial search) | Cancel changes — re-read original data from CARDDAT, redisplay original values |
| OTHER | Any | Treated as ENTER (invalid keys are remapped to ENTER by PFK-INVALID logic) |

---

## 4. Program Inventory and Call Chain

### 4.1 Program Inventory

| Program ID | Purpose | Entry Via | LOC | Source Location |
|-----------|---------|-----------|-----|-----------------|
| COCRDUPC | Credit Card Update — search, display, edit, validate, REWRITE | TRANSID CCUP / XCTL from COMEN01C or COCRDLIC | 1560 | `app/cbl/COCRDUPC.cbl` |
| COMEN01C | Main Menu — entry point for option 5 | XCTL (caller) | — | `app/cbl/COMEN01C.cbl` |
| COCRDLIC | Credit Card List — entry point when user selects a card | XCTL (caller) | — | `app/cbl/COCRDLIC.cbl` |

### 4.2 Call Chain Diagram

```
TRANSID: CCUP
└── COCRDUPC (Entry — Screen Handler, Validation, Data Access)
    ├── READ FILE('CARDDAT') — Fetch card record by card number
    ├── READ FILE('CARDDAT') UPDATE — Lock record for update
    ├── REWRITE FILE('CARDDAT') — Write updated card record
    ├── SEND MAP('CCRDUPA') MAPSET('COCRDUP') — Display screen
    ├── RECEIVE MAP('CCRDUPA') MAPSET('COCRDUP') — Capture input
    ├── SYNCPOINT — Commit before exit
    ├── RETURN TRANSID('CCUP') — Pseudo-conversational return
    └── XCTL → COMEN01C or COCRDLIC (on PF3 exit)
```

### 4.3 Call Details

| Caller | Called | Mechanism | Data Passed | Return Data | When |
|--------|--------|-----------|-------------|-------------|------|
| COMEN01C | COCRDUPC | XCTL | CARDDEMO-COMMAREA (CDEMO-FROM-PROGRAM = COMEN01C) | — | User selects menu option 5 |
| COCRDLIC | COCRDUPC | XCTL | CARDDEMO-COMMAREA (CDEMO-ACCT-ID, CDEMO-CARD-NUM populated) | — | User selects a card from the list |
| COCRDUPC | COMEN01C | XCTL | CARDDEMO-COMMAREA | — | PF3 exit back to main menu (when CDEMO-FROM not set) |
| COCRDUPC | COCRDLIC | XCTL | CARDDEMO-COMMAREA | — | PF3 exit back to card list (when came from list and update done/failed) |

---

## 5. File and Data Store Inventory

### 5.1 File Inventory for This Transaction

| File/Table | DD/FCT Name | Type | Access Mode | Program | Purpose |
|-----------|-------------|------|-------------|---------|---------|
| Card Master | CARDDAT | VSAM KSDS | READ / READ UPDATE / REWRITE | COCRDUPC | Read card details, lock for update, write updated record |

### 5.2 File Access Details

#### File: CARDDAT (Card Master)

| Attribute | Value |
|-----------|-------|
| Type | VSAM KSDS |
| Record Copybook | CVACT02Y (CARD-RECORD) |
| Record Length | 150 bytes |
| Key Field | CARD-NUM (PIC X(16)) — positions 1–16 |
| Access Mode (browse) | READ RIDFLD(WS-CARD-RID-CARDNUM) KEYLENGTH(16) |
| Access Mode (lock) | READ UPDATE RIDFLD(WS-CARD-RID-CARDNUM) KEYLENGTH(16) |
| Access Mode (update) | REWRITE FROM(CARD-UPDATE-RECORD) |
| Locking | READ UPDATE acquires exclusive lock; released on REWRITE or SYNCPOINT |
| Shared With | COCRDLIC (list), COCRDSLC (view), COCRDUPC (update), batch programs |

### 5.3 File Access Patterns

#### 9100-GETCARD-BYACCTCARD (Read for Display)

| Step | Operation | Key / RID | Response Handling |
|------|-----------|-----------|-------------------|
| 1 | READ FILE('CARDDAT') | WS-CARD-RID-CARDNUM (16 bytes) | NORMAL → populate CCUP-OLD-DETAILS |
| | | | NOTFND → "Did not find cards for this search condition" |
| | | | OTHER → "File Error: READ on CARDDAT returned RESP nn,RESP2 nn" |

#### 9200-WRITE-PROCESSING (Lock, Verify, Update)

| Step | Operation | Key / RID | Response Handling |
|------|-----------|-----------|-------------------|
| 1 | READ FILE('CARDDAT') UPDATE | WS-CARD-RID-CARDNUM (16 bytes) | NORMAL → continue; OTHER → "Could not lock record for update" |
| 2 | 9300-CHECK-CHANGE-IN-REC | Compare CARD-RECORD fields vs CCUP-OLD-DETAILS | Match → continue; Mismatch → "Record changed by some one else. Please review" |
| 3 | REWRITE FILE('CARDDAT') | FROM(CARD-UPDATE-RECORD) | NORMAL → success; OTHER → "Update of record failed" |

### 5.4 TSQ / TDQ Usage

None. This transaction does not use Temporary Storage Queues or Transient Data Queues.

---

## 6. Data Flow Analysis

### 6.1 Data Flow Diagram

```
  ┌─────────────────┐
  │ CCRDUPA Screen   │
  │ (BMS Map Input)  │
  │                  │
  │ ACCTSIDI ────────┼──▶ CC-ACCT-ID ──▶ CCUP-NEW-ACCTID / CDEMO-ACCT-ID
  │ CARDSIDI ────────┼──▶ CC-CARD-NUM ──▶ CCUP-NEW-CARDID / CDEMO-CARD-NUM
  │ CRDNAMEI ────────┼──▶ CCUP-NEW-CRDNAME ──▶ CARD-UPDATE-EMBOSSED-NAME
  │ CRDSTCDI ────────┼──▶ CCUP-NEW-CRDSTCD ──▶ CARD-UPDATE-ACTIVE-STATUS
  │ EXPMONI  ────────┼──▶ CCUP-NEW-EXPMON ──┐
  │ EXPYEARI ────────┼──▶ CCUP-NEW-EXPYEAR ─┤──▶ STRING → CARD-UPDATE-EXPIRAION-DATE
  │ EXPDAYI  ────────┼──▶ CCUP-NEW-EXPDAY ──┘    (YYYY-MM-DD format)
  └─────────────────┘
           ▲
           │ (Output — CCRDUPAO)
  ┌─────────────────┐
  │ CARDDAT          │
  │ (CARD-RECORD)    │
  │                  │
  │ CARD-NUM ────────┼──▶ CCUP-OLD-CARDID ──▶ CARDSIDO
  │ CARD-ACCT-ID ────┼──▶ CCUP-OLD-ACCTID ──▶ ACCTSIDO
  │ CARD-CVV-CD ─────┼──▶ CCUP-OLD-CVV-CD (preserved, not displayed)
  │ CARD-EMBOSSED    │
  │  -NAME ──────────┼──▶ INSPECT UPPER ──▶ CCUP-OLD-CRDNAME ──▶ CRDNAMEO
  │ CARD-EXPIRAION   │
  │  -DATE ──────────┼──▶ (1:4) → CCUP-OLD-EXPYEAR ──▶ EXPYEARO
  │                  │──▶ (6:2) → CCUP-OLD-EXPMON  ──▶ EXPMONO
  │                  │──▶ (9:2) → CCUP-OLD-EXPDAY  ──▶ EXPDAYO
  │ CARD-ACTIVE      │
  │  -STATUS ────────┼──▶ CCUP-OLD-CRDSTCD ──▶ CRDSTCDO
  └─────────────────┘
```

### 6.2 Transformation Map

| Source Field | Transformation | Target Field | When |
|-------------|----------------|-------------|------|
| ACCTSIDI (screen) | Direct move; '*'/SPACES → LOW-VALUES | CC-ACCT-ID, CCUP-NEW-ACCTID | 1100-RECEIVE-MAP |
| CARDSIDI (screen) | Direct move; '*'/SPACES → LOW-VALUES | CC-CARD-NUM, CCUP-NEW-CARDID | 1100-RECEIVE-MAP |
| CRDNAMEI (screen) | Direct move; '*'/SPACES → LOW-VALUES | CCUP-NEW-CRDNAME | 1100-RECEIVE-MAP |
| CRDSTCDI (screen) | Direct move; '*'/SPACES → LOW-VALUES | CCUP-NEW-CRDSTCD | 1100-RECEIVE-MAP |
| EXPMONI (screen) | Direct move; '*'/SPACES → LOW-VALUES | CCUP-NEW-EXPMON | 1100-RECEIVE-MAP |
| EXPYEARI (screen) | Direct move; '*'/SPACES → LOW-VALUES | CCUP-NEW-EXPYEAR | 1100-RECEIVE-MAP |
| EXPDAYI (screen) | Direct move (hidden field) | CCUP-NEW-EXPDAY | 1100-RECEIVE-MAP |
| CARD-EMBOSSED-NAME (file) | INSPECT CONVERTING lower TO upper | CCUP-OLD-CRDNAME | 9000-READ-DATA |
| CARD-EXPIRAION-DATE (file) | Substring (1:4) | CCUP-OLD-EXPYEAR | 9000-READ-DATA |
| CARD-EXPIRAION-DATE (file) | Substring (6:2) | CCUP-OLD-EXPMON | 9000-READ-DATA |
| CARD-EXPIRAION-DATE (file) | Substring (9:2) | CCUP-OLD-EXPDAY | 9000-READ-DATA |
| CCUP-NEW-EXPYEAR + CCUP-NEW-EXPMON + CCUP-NEW-EXPDAY | STRING with '-' delimiters → YYYY-MM-DD | CARD-UPDATE-EXPIRAION-DATE | 9200-WRITE-PROCESSING |
| CCUP-NEW-CVV-CD | Via CARD-CVV-CD-X → CARD-CVV-CD-N conversion | CARD-UPDATE-CVV-CD | 9200-WRITE-PROCESSING |
| CCUP-NEW-CARDDATA vs CCUP-OLD-CARDDATA | FUNCTION UPPER-CASE comparison | Change detection flag | 1200-EDIT-MAP-INPUTS |

---

## 7. COMMAREA and Interface Contracts

### 7.1 COMMAREA Layout

The COMMAREA consists of two concatenated structures passed in WS-COMMAREA (PIC X(2000)):

#### Part 1: CARDDEMO-COMMAREA (shared, from COCOM01Y.cpy)

| Offset | Field | PIC | Length | Description |
|--------|-------|-----|--------|-------------|
| 1 | CDEMO-FROM-TRANID | X(04) | 4 | Source transaction ID (e.g., 'CCLI', 'CM00') |
| 5 | CDEMO-FROM-PROGRAM | X(08) | 8 | Source program (e.g., 'COCRDLIC', 'COMEN01C') |
| 13 | CDEMO-TO-TRANID | X(04) | 4 | Target transaction ID |
| 17 | CDEMO-TO-PROGRAM | X(08) | 8 | Target program |
| 25 | CDEMO-USER-ID | X(08) | 8 | Signed-on user ID |
| 33 | CDEMO-USER-TYPE | X(01) | 1 | 'A' = Admin, 'U' = User |
| 34 | CDEMO-PGM-CONTEXT | 9(01) | 1 | 0 = Enter (first time), 1 = Reenter (returning) |
| 35 | CDEMO-CUST-ID | 9(09) | 9 | Customer ID |
| 44 | CDEMO-CUST-FNAME | X(25) | 25 | Customer first name |
| 69 | CDEMO-CUST-MNAME | X(25) | 25 | Customer middle name |
| 94 | CDEMO-CUST-LNAME | X(25) | 25 | Customer last name |
| 119 | CDEMO-ACCT-ID | 9(11) | 11 | Account ID (key field passed from caller) |
| 130 | CDEMO-ACCT-STATUS | X(01) | 1 | Account status |
| 131 | CDEMO-CARD-NUM | 9(16) | 16 | Card number (key field passed from caller) |
| 147 | CDEMO-LAST-MAP | X(7) | 7 | Last BMS map displayed |
| 154 | CDEMO-LAST-MAPSET | X(7) | 7 | Last BMS mapset used |

#### Part 2: WS-THIS-PROGCOMMAREA (program-specific extension)

| Offset | Field | PIC | Length | Description |
|--------|-------|-----|--------|-------------|
| 1 | CCUP-CHANGE-ACTION | X(1) | 1 | State flag: LOW-VALUES/SPACES = not fetched; 'S' = show details; 'E' = changes not OK; 'N' = changes OK not confirmed; 'C' = changes done; 'L' = lock error; 'F' = update failed |
| 2 | CCUP-OLD-ACCTID | X(11) | 11 | Original account ID from file |
| 13 | CCUP-OLD-CARDID | X(16) | 16 | Original card number from file |
| 29 | CCUP-OLD-CVV-CD | X(3) | 3 | Original CVV code from file |
| 32 | CCUP-OLD-CRDNAME | X(50) | 50 | Original embossed name (uppercased) |
| 82 | CCUP-OLD-EXPYEAR | X(4) | 4 | Original expiry year |
| 86 | CCUP-OLD-EXPMON | X(2) | 2 | Original expiry month |
| 88 | CCUP-OLD-EXPDAY | X(2) | 2 | Original expiry day |
| 90 | CCUP-OLD-CRDSTCD | X(1) | 1 | Original active status |
| 91 | CCUP-NEW-ACCTID | X(11) | 11 | New/user-entered account ID |
| 102 | CCUP-NEW-CARDID | X(16) | 16 | New/user-entered card number |
| 118 | CCUP-NEW-CVV-CD | X(3) | 3 | New CVV code (preserved from old) |
| 121 | CCUP-NEW-CRDNAME | X(50) | 50 | New embossed name (user input) |
| 171 | CCUP-NEW-EXPYEAR | X(4) | 4 | New expiry year (user input) |
| 175 | CCUP-NEW-EXPMON | X(2) | 2 | New expiry month (user input) |
| 177 | CCUP-NEW-EXPDAY | X(2) | 2 | New expiry day (preserved from old) |
| 179 | CCUP-NEW-CRDSTCD | X(1) | 1 | New active status (user input) |
| 180 | CARD-UPDATE-RECORD | X(150) | 150 | Staging area for the REWRITE record |

### 7.2 State Management

The program uses `CCUP-CHANGE-ACTION` as its primary state machine variable:

| State Value | 88-Level Name | Meaning |
|-------------|---------------|---------|
| LOW-VALUES / SPACES | CCUP-DETAILS-NOT-FETCHED | Initial state — no card fetched yet |
| 'S' | CCUP-SHOW-DETAILS | Card data fetched and displayed |
| 'E' | CCUP-CHANGES-NOT-OK | User made changes but validation failed |
| 'N' | CCUP-CHANGES-OK-NOT-CONFIRMED | Changes validated, awaiting F5 confirmation |
| 'C' | CCUP-CHANGES-OKAYED-AND-DONE | REWRITE successful |
| 'L' | CCUP-CHANGES-OKAYED-LOCK-ERROR | Could not acquire UPDATE lock |
| 'F' | CCUP-CHANGES-OKAYED-BUT-FAILED | Lock acquired but REWRITE failed |

### 7.3 Return Codes

This program does not use explicit return codes. Success/failure is communicated via:
- `CCUP-CHANGE-ACTION` state transitions in COMMAREA
- `WS-INFO-MSG` (informational messages on screen)
- `WS-RETURN-MSG` (error messages on screen)

---

## 8. Error Handling and User Feedback

### 8.1 Error Flow Diagram

```
  ┌──────────────────────────┐
  │ Error Source              │
  │ (Validation / File I/O)  │
  └────────────┬─────────────┘
               │
           ┌───┴───┐
           │ Type? │
           └───┬───┘
              ╱│╲
            ╱  │  ╲
           ▼   ▼   ▼
   ┌──────────┐ ┌──────────┐ ┌──────────────┐
   │Input     │ │File      │ │Unexpected    │
   │Validation│ │Error     │ │Error         │
   └────┬─────┘ └────┬─────┘ └──────┬───────┘
        │             │              │
        ▼             ▼              ▼
   ┌──────────┐ ┌──────────┐ ┌──────────────┐
   │Set       │ │Build     │ │ABEND-ROUTINE │
   │WS-RETURN │ │WS-FILE-  │ │Send ABEND-   │
   │-MSG      │ │ERROR-    │ │DATA, ABEND   │
   │(88-level)│ │MESSAGE   │ │ABCODE('9999')│
   └────┬─────┘ └────┬─────┘ └──────────────┘
        │             │
        └──────┬──────┘
               ▼
   ┌─────────────────────────┐
   │ ERRMSGO of CCRDUPAO     │
   │ (Red, bright, line 23)  │
   └─────────────────────────┘
```

### 8.2 Error Handling Matrix

| Error Condition | RESP Code | Paragraph | Error Message (WS-RETURN-MSG) | Recovery |
|----------------|-----------|-----------|-------------------------------|----------|
| Account number blank | — | 1210-EDIT-ACCOUNT | "Account number not provided" | Cursor to ACCTSID |
| Account number not numeric | — | 1210-EDIT-ACCOUNT | "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER" | Cursor to ACCTSID |
| Card number blank | — | 1220-EDIT-CARD | "Card number not provided" | Cursor to CARDSID |
| Card number not numeric | — | 1220-EDIT-CARD | "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" | Cursor to CARDSID |
| No search criteria entered | — | 1200-EDIT-MAP-INPUTS | "No input received" | Cursor to ACCTSID |
| Card not found in CARDDAT | NOTFND | 9100-GETCARD-BYACCTCARD | "Did not find cards for this search condition" | Cursor to ACCTSID |
| CARDDAT file read error | OTHER | 9100-GETCARD-BYACCTCARD | "File Error: READ on CARDDAT returned RESP nn,RESP2 nn" | Cursor to ACCTSID |
| Card name blank | — | 1230-EDIT-NAME | "Card name not provided" | Cursor to CRDNAME |
| Card name non-alpha | — | 1230-EDIT-NAME | "Card name can only contain alphabets and spaces" | Cursor to CRDNAME |
| Card status not Y/N | — | 1240-EDIT-CARDSTATUS | "Card Active Status must be Y or N" | Cursor to CRDSTCD |
| Card status blank | — | 1240-EDIT-CARDSTATUS | "Card Active Status must be Y or N" | Cursor to CRDSTCD |
| Expiry month blank/invalid | — | 1250-EDIT-EXPIRY-MON | "Card expiry month must be between 1 and 12" | Cursor to EXPMON |
| Expiry year blank/invalid | — | 1260-EDIT-EXPIRY-YEAR | "Invalid card expiry year" | Cursor to EXPYEAR |
| No changes detected | — | 1200-EDIT-MAP-INPUTS | "No change detected with respect to values fetched." | Cursor to CRDNAME |
| Lock failure on update | NOT NORMAL | 9200-WRITE-PROCESSING | "Could not lock record for update" | State → CCUP-CHANGES-OKAYED-LOCK-ERROR |
| Concurrent modification | — | 9300-CHECK-CHANGE-IN-REC | "Record changed by some one else. Please review" | Refresh CCUP-OLD-DETAILS, redisplay |
| REWRITE failure | NOT NORMAL | 9200-WRITE-PROCESSING | "Update of record failed" | State → CCUP-CHANGES-OKAYED-BUT-FAILED |
| Unexpected data scenario | — | 2000-DECIDE-ACTION (OTHER) | ABEND with code '0001', msg "UNEXPECTED DATA SCENARIO" | ABEND ABCODE('9999') |
| Unexpected abend | — | ABEND-ROUTINE | "UNEXPECTED ABEND OCCURRED." | ABEND ABCODE('9999') |

### 8.3 HANDLE ABEND

| Registration | Label | Behavior |
|-------------|-------|----------|
| 0000-MAIN (entry) | ABEND-ROUTINE | Set ABEND-CULPRIT = 'COCRDUPC', SEND ABEND-DATA, HANDLE ABEND CANCEL, ABEND ABCODE('9999') |

### 8.4 Complete Error Message List

| Message (WS-RETURN-MSG / WS-INFO-MSG) | Type | 88-Level Name | Trigger Condition |
|---------------------------------------|------|---------------|-------------------|
| "Please enter Account and Card Number" | Info | PROMPT-FOR-SEARCH-KEYS | Initial entry or details not fetched |
| "Details of selected card shown above" | Info | FOUND-CARDS-FOR-ACCOUNT | Card data successfully fetched |
| "Update card details presented above." | Info | PROMPT-FOR-CHANGES | Changes have validation errors |
| "Changes validated.Press F5 to save" | Info | PROMPT-FOR-CONFIRMATION | All changes pass validation |
| "Changes committed to database" | Info | CONFIRM-UPDATE-SUCCESS | REWRITE successful |
| "Changes unsuccessful. Please try again" | Info | INFORM-FAILURE | Lock error or REWRITE failure |
| "Account number not provided" | Error | WS-PROMPT-FOR-ACCT | Account field blank/zero |
| "Card number not provided" | Error | WS-PROMPT-FOR-CARD | Card field blank/zero |
| "Card name not provided" | Error | WS-PROMPT-FOR-NAME | Card name blank |
| "Card name can only contain alphabets and spaces" | Error | WS-NAME-MUST-BE-ALPHA | Card name contains non-alpha |
| "No input received" | Error | NO-SEARCH-CRITERIA-RECEIVED | Both search fields blank |
| "No change detected with respect to values fetched." | Error | NO-CHANGES-DETECTED | New data equals old data |
| "Account number must be a non zero 11 digit number" | Error | SEARCHED-ACCT-ZEROES / SEARCHED-ACCT-NOT-NUMERIC | Account zero or non-numeric |
| "Card number if supplied must be a 16 digit number" | Error | SEARCHED-CARD-NOT-NUMERIC | Card non-numeric |
| "Card Active Status must be Y or N" | Error | CARD-STATUS-MUST-BE-YES-NO | Status not Y/N |
| "Card expiry month must be between 1 and 12" | Error | CARD-EXPIRY-MONTH-NOT-VALID | Month blank or out of range |
| "Invalid card expiry year" | Error | CARD-EXPIRY-YEAR-NOT-VALID | Year blank or out of range |
| "Did not find this account in cards database" | Error | DID-NOT-FIND-ACCT-IN-CARDXREF | Account not in XREF |
| "Did not find cards for this search condition" | Error | DID-NOT-FIND-ACCTCARD-COMBO | Card not found in CARDDAT |
| "Could not lock record for update" | Error | COULD-NOT-LOCK-FOR-UPDATE | READ UPDATE failed |
| "Record changed by some one else. Please review" | Error | DATA-WAS-CHANGED-BEFORE-UPDATE | Optimistic concurrency violation |
| "Update of record failed" | Error | LOCKED-BUT-UPDATE-FAILED | REWRITE failed |
| "Error reading Card Data File" | Error | XREF-READ-ERROR | Card data file read error |
| "Looks Good.... so far" | Error | CODING-TO-BE-DONE | Placeholder (unused in flow) |
| "PF03 pressed.Exiting" | Error | WS-EXIT-MESSAGE | PF3 exit |
| "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER" | Error | (inline) | Account not numeric |
| "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" | Error | (inline) | Card not numeric |
| "File Error: {OP} on {FILE} returned RESP {R1},RESP2 {R2}" | Error | WS-FILE-ERROR-MESSAGE | Any unexpected file I/O error |

---

## 9. Security and Authorization

### 9.1 Transaction Security

| Aspect | Implementation |
|--------|---------------|
| Transaction ID | CCUP — defined in CSD GROUP(CARDDEMO) |
| CSD Security | STATUS(ENABLED), ISOLATE(YES), TASKDATAKEY(USER) |
| User Authentication | Relies on CICS sign-on (COSGN00C / CC00 transaction); CDEMO-USER-ID passed via COMMAREA |
| User Type Check | No explicit admin vs. user check in COCRDUPC — menu option 5 is type 'U' (available to all users) |

### 9.2 Data Access Authorization

| Aspect | Implementation |
|--------|---------------|
| File CARDDAT | FCT-level security; program accesses with EXECKEY(USER) |
| Record-Level | No record-level security; any authenticated user can update any card |
| Field-Level | Account Number and Card Number are protected after fetch (DFHBMPRF); user cannot change key fields |

### 9.3 Audit Trail

| Aspect | Implementation |
|--------|---------------|
| Transaction Logging | CICS journal/log records for REWRITE operations (standard CICS logging) |
| Application Audit | No explicit application-level audit logging in COCRDUPC |
| Before/After Image | Old values preserved in CCUP-OLD-DETAILS; however, no explicit before-image logging to a file |
| User Tracking | CDEMO-USER-ID available in COMMAREA but not written to an audit record |

---

## 10. Performance Characteristics

### 10.1 CICS Resource Usage

| Resource | Usage |
|----------|-------|
| File I/O per iteration | 1 READ (fetch) or 1 READ UPDATE + 1 REWRITE (save) |
| Maximum file I/O per save | 2 (READ UPDATE + REWRITE) |
| BMS map sends per iteration | 1 SEND MAP |
| BMS map receives per iteration | 1 RECEIVE MAP (except first entry) |
| COMMAREA size | 2000 bytes |
| Programs loaded | 1 (COCRDUPC only — no subprogram calls) |

### 10.2 Potential Bottlenecks

| Area | Concern | Mitigation |
|------|---------|------------|
| Record Locking | READ UPDATE holds exclusive lock until REWRITE/SYNCPOINT | Lock window is minimized — lock acquired immediately before write |
| Optimistic Concurrency | 9300-CHECK-CHANGE-IN-REC re-reads and compares all fields | Adds negligible overhead but prevents lost updates |
| No Pagination | Single record operation — no browsing performance concerns | N/A |

---

## 11. Testing Approach

### 11.1 Test Scenarios

| Scenario ID | Description | Pre-Conditions | Input | Expected Output |
|------------|-------------|----------------|-------|-----------------|
| TC-001 | Fresh entry — empty search screen | Navigate from Main Menu option 5 | — | Empty CCRDUPA screen with "Please enter Account and Card Number" |
| TC-002 | Valid search — card found | Card exists in CARDDAT | Valid Account# + Card# | Card details displayed; "Details of selected card shown above" |
| TC-003 | Search — card not found | Card does not exist | Non-existent Card# | "Did not find cards for this search condition" |
| TC-004 | Search — blank account | No account entered | Blank Account#, valid Card# | "Account number not provided" |
| TC-005 | Search — blank card | No card entered | Valid Account#, blank Card# | "Card number not provided" |
| TC-006 | Search — non-numeric account | Letters in account field | Alpha Account# | "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER" |
| TC-007 | Search — non-numeric card | Letters in card field | Alpha Card# | "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" |
| TC-008 | Edit — valid name change | Card details displayed | Change name to valid alpha string | "Changes validated.Press F5 to save" |
| TC-009 | Edit — name with special chars | Card details displayed | Name with digits or symbols | "Card name can only contain alphabets and spaces" |
| TC-010 | Edit — blank name | Card details displayed | Clear name field | "Card name not provided" |
| TC-011 | Edit — valid status change | Card details displayed | Change 'Y' to 'N' or vice versa | "Changes validated.Press F5 to save" |
| TC-012 | Edit — invalid status | Card details displayed | Enter 'X' for status | "Card Active Status must be Y or N" |
| TC-013 | Edit — valid expiry change | Card details displayed | Month=06, Year=2025 | "Changes validated.Press F5 to save" |
| TC-014 | Edit — invalid month | Card details displayed | Month=13 | "Card expiry month must be between 1 and 12" |
| TC-015 | Edit — invalid year | Card details displayed | Year=1900 | "Invalid card expiry year" |
| TC-016 | Edit — no changes made | Card details displayed | Press ENTER without changes | "No change detected with respect to values fetched." |
| TC-017 | Save — F5 success | Changes validated and confirmed | Press F5 | "Changes committed to database" |
| TC-018 | Save — concurrent modification | Another user changes record between fetch and F5 | Press F5 | "Record changed by some one else. Please review" — refreshed data |
| TC-019 | Cancel — PF12 | Changes pending | Press PF12 | Original values re-read and displayed |
| TC-020 | Exit — PF3 from Main Menu | Entered from Main Menu | Press PF3 | Return to Main Menu (COMEN01C) |
| TC-021 | Exit — PF3 from Card List | Entered from Card List | Press PF3 | Return to Card List (COCRDLIC) |
| TC-022 | Entry from Card List | Card selected in COCRDLIC | XCTL with CDEMO-ACCT-ID/CARD-NUM | Card details displayed automatically |
| TC-023 | Both search fields blank | No input at all | ENTER with both blank | "No input received" |

### 11.2 Test Data Requirements

| Data Element | Requirement |
|-------------|-------------|
| CARDDAT file | Must contain card records with known card numbers and account IDs |
| Valid card record | Card# = known 16-digit, Account# = known 11-digit, Name = alpha, Status = Y/N |
| Concurrent test setup | Two terminal sessions accessing the same card record simultaneously |

---

## 12. Known Issues and Considerations

### 12.1 Known Issues

| Issue ID | Description | Impact | Status |
|---------|-------------|--------|--------|
| KI-001 | Expiry day field is hidden (DRK,PROT) and not user-editable; preserved from original record | User cannot change expiry day — always uses original value | By design |
| KI-002 | No cross-reference validation against CARDAIX alternate index path — card is looked up by card number only, account number from screen input is not validated against XREF | User could enter a mismatched account number (accepted but not verified) | Potential data integrity concern |
| KI-003 | Optimistic concurrency check compares individual fields but does not use a version number or timestamp | Theoretical race condition if fields are changed and then changed back by another user | Low probability in practice |
| KI-004 | Several 88-level error messages are defined but unreachable in the current flow (e.g., SEARCHED-ACCT-ZEROES, SEARCHED-ACCT-NOT-NUMERIC, SEARCHED-CARD-NOT-NUMERIC, CODING-TO-BE-DONE, DID-NOT-FIND-ACCT-IN-CARDXREF, XREF-READ-ERROR) | Dead code — these messages were likely intended for a different validation path | No runtime impact |
| KI-005 | CARD-CVV-CD is read and preserved during update but never displayed or editable | CVV is passed through CCUP-OLD-CVV-CD → CCUP-NEW-CVV-CD → CARD-UPDATE-CVV-CD | By design — CVV is sensitive |

### 12.2 Modernization Considerations

| Area | Current State | Modernization Approach | Complexity |
|------|--------------|----------------------|------------|
| Data Access | VSAM KSDS READ/REWRITE with manual record locking | Replace with SQL UPDATE with row-level locking (e.g., SELECT ... FOR UPDATE or ORM) | Medium |
| Screen Handling | BMS maps with manual attribute manipulation (DFHBMPRF, DFHBMFSE, colors) | Replace with HTML/React form with CSS classes, conditional rendering | Medium |
| State Machine | Manual COMMAREA-based state (CCUP-CHANGE-ACTION flag) | Replace with session state or stateless REST API with CSRF token | Medium |
| Validation | Paragraph-based validation with 88-level flags and first-error-wins pattern | Replace with declarative validation (Bean Validation / Zod schema) | Low |
| Optimistic Concurrency | Manual field-by-field comparison in 9300-CHECK-CHANGE-IN-REC | Replace with version column (ETag / optimistic locking annotation) | Low |
| Pseudo-Conversational | RETURN TRANSID with COMMAREA for state preservation | Replace with stateless HTTP request/response or WebSocket | Medium |
| Navigation | XCTL-based screen-to-screen navigation with COMMAREA passing | Replace with client-side routing (React Router, etc.) | Low |
| Error Handling | 88-level message flags with first-error-wins pattern | Replace with validation result collection (return all errors at once) | Low |
| Case Conversion | INSPECT CONVERTING for uppercase | Database collation or application-level toUpperCase() | Low |
| Date Formatting | STRING concatenation for YYYY-MM-DD | Native date type in database; date picker in UI | Low |
| Abend Handling | HANDLE ABEND with SEND FROM + ABEND ABCODE | Replace with try/catch exception handling and structured error response | Low |

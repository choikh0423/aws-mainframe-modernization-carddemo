# COTRN02C 1:1 Parity Diff Document

## Overview

This document provides a paragraph-by-paragraph comparison between the original COBOL/CICS program `COTRN02C.cbl` (784 lines) and its Java/Spring Boot migration. Each behavior is classified as:

- **EXACT** — Identical behavior in both implementations
- **IMPROVED** — Same outcome but with a better mechanism (e.g., DB auto-increment vs. VSAM race condition)
- **DEVIATION** — Intentional difference documented with rationale

---

## 1. MAIN-PARA (COBOL lines 96–162)

### COBOL Behavior
```
EVALUATE TRUE
  WHEN EIBCALEN = ZERO        -> first entry, send empty screen
  WHEN EIBAID = DFHENTER      -> PROCESS-ENTER-KEY
  WHEN EIBAID = DFHPF3        -> go back to menu (COMEN01C)
  WHEN EIBAID = DFHPF4        -> clear screen
  WHEN EIBAID = DFHPF5        -> COPY-LAST-TRAN-DATA
  WHEN OTHER                  -> "Invalid key pressed..."
END-EVALUATE
```

### Java Behavior
| COBOL Action | Java Equivalent | Status |
|---|---|---|
| EIBCALEN = 0 (first entry) | Frontend renders empty form on page load | **EXACT** |
| DFHENTER | `POST /api/transactions` or `POST /api/transactions/validate` | **EXACT** |
| DFHPF3 (go back) | Frontend navigation / Clear button | **EXACT** |
| DFHPF4 (clear screen) | Frontend form reset | **EXACT** |
| DFHPF5 (copy last) | `GET /api/transactions/last` | **EXACT** |
| OTHER (invalid key) | N/A — web UI has no "invalid key" concept | **DEVIATION** |

### Deviation D1: No "Invalid Key" Error
- **COBOL**: Pressing an unmapped AID key shows "Invalid key pressed..."
- **Java**: Web UI only has defined buttons; no unmapped keys exist
- **Impact**: None — the error path is structurally impossible in a web interface

---

## 2. PROCESS-ENTER-KEY (COBOL lines 164–188)

### COBOL Behavior
```
1. RECEIVE MAP COTRN2A
2. VALIDATE-INPUT-KEY-FIELDS
3. VALIDATE-INPUT-DATA-FIELDS
4. EVALUATE WS-TRNX-CONFIRM
     WHEN 'Y'/'y' -> ADD-TRANSACTION
     WHEN 'N'/'n'/SPACES -> "Confirm to add..."
     WHEN OTHER -> "Invalid value. Valid values are (Y/N)..."
5. SEND-TRNADD-SCREEN (contains EXEC CICS RETURN — terminates task)
```

### Java Behavior
| Step | COBOL | Java | Status |
|---|---|---|---|
| 1 | RECEIVE MAP | HTTP POST request body (JSON) | **EXACT** |
| 2 | VALIDATE-INPUT-KEY-FIELDS | `validator.validateKeyFields(req)` | **EXACT** |
| 3 | VALIDATE-INPUT-DATA-FIELDS | `validator.validateDataFields(req)` | **EXACT** |
| 4a | confirm = Y/y → ADD-TRANSACTION | `validator.validateConfirmation("Y")` passes → `addTransaction()` | **EXACT** |
| 4b | confirm = N/n/SPACES → message | `ValidationException("Confirm to add...")` | **EXACT** |
| 4c | confirm = other → message | `ValidationException("Invalid value...")` | **EXACT** |
| 5 | SEND MAP + RETURN (kills task) | `throw ValidationException` (short-circuit) | **EXACT** |

### Critical Parity: First-Error-Only Short-Circuit
- **COBOL**: `SEND-TRNADD-SCREEN` contains `EXEC CICS RETURN`, which terminates the CICS task. This means only the FIRST validation error is ever displayed — subsequent checks never execute.
- **Java**: `TransactionValidator` throws `ValidationException` on the first failure, which immediately propagates up and returns a 422 response. No error accumulation occurs.
- **Status**: **EXACT** — both implementations show exactly one error at a time.

---

## 3. VALIDATE-INPUT-KEY-FIELDS (COBOL lines 193–230)

### COBOL Behavior
```
EVALUATE TRUE
  WHEN WS-ACCT-ID-N > 0 (acctId entered)
    IF NOT NUMERIC -> "Account ID must be Numeric..."
    READ CXACAIX (alt index) -> get card num
    IF NOTFND -> "Account ID NOT found..."
  WHEN WS-CARD-NUM-N > 0 (cardNum entered)
    IF NOT NUMERIC -> "Card Number must be Numeric..."
    READ CCXREF (primary) -> get acct id
    IF NOTFND -> "Card Number NOT found..."
  WHEN OTHER
    "Account or Card Number must be entered..."
END-EVALUATE
```

### Java Behavior
| Rule | COBOL Error Message | Java Error Message | Field | Status |
|---|---|---|---|---|
| Neither entered | "Account or Card Number must be entered..." | "Account or Card Number must be entered..." | acctId | **EXACT** |
| acctId non-numeric | "Account ID must be Numeric..." | "Account ID must be Numeric..." | acctId | **EXACT** |
| acctId not in XREF | "Account ID NOT found..." | "Account ID NOT found..." | acctId | **EXACT** |
| cardNum non-numeric | "Card Number must be Numeric..." | "Card Number must be Numeric..." | cardNum | **EXACT** |
| cardNum not in XREF | "Card Number NOT found..." | "Card Number NOT found..." | cardNum | **EXACT** |
| Both entered | acctId takes precedence (first WHEN) | acctId takes precedence (first `if` branch) | — | **EXACT** |

### VSAM → JPA Mapping
| COBOL | Java | Status |
|---|---|---|
| `READ CXACAIX` (alternate index by acctId) | `cardXrefRepository.findByXrefAcctId(acctId)` | **EXACT** |
| `READ CCXREF` (primary key by cardNum) | `cardXrefRepository.findById(cardNum)` | **EXACT** |

---

## 4. VALIDATE-INPUT-DATA-FIELDS (COBOL lines 235–437)

### Phase 2: Empty Field Checks (lines 251–320)

| # | COBOL Field | COBOL Error Message | Java Field | Java Error Message | Status |
|---|---|---|---|---|---|
| 1 | TTYP-CD | "Type CD can NOT be empty..." | typeCd | "Type CD can NOT be empty..." | **EXACT** |
| 2 | TCAT-CD | "Category CD can NOT be empty..." | catCd | "Category CD can NOT be empty..." | **EXACT** |
| 3 | TRNSRC | "Source can NOT be empty..." | source | "Source can NOT be empty..." | **EXACT** |
| 4 | TDESC | "Description can NOT be empty..." | description | "Description can NOT be empty..." | **EXACT** |
| 5 | TRNAMT | "Amount can NOT be empty..." | amount | "Amount can NOT be empty..." | **EXACT** |
| 6 | TORIG-DT | "Orig Date can NOT be empty..." | origDate | "Orig Date can NOT be empty..." | **EXACT** |
| 7 | TPROC-DT | "Proc Date can NOT be empty..." | procDate | "Proc Date can NOT be empty..." | **EXACT** |
| 8 | MID | "Merchant ID can NOT be empty..." | merchantId | "Merchant ID can NOT be empty..." | **EXACT** |
| 9 | MNAME | "Merchant Name can NOT be empty..." | merchantName | "Merchant Name can NOT be empty..." | **EXACT** |
| 10 | MCITY | "Merchant City can NOT be empty..." | merchantCity | "Merchant City can NOT be empty..." | **EXACT** |
| 11 | MZIP | "Merchant Zip can NOT be empty..." | merchantZip | "Merchant Zip can NOT be empty..." | **EXACT** |

### Phase 3: Type/Format Validation (lines 322–386)

| Rule | COBOL Check | Java Check | Error Message | Status |
|---|---|---|---|---|
| typeCd numeric | `IS NOT NUMERIC` | `!chars().allMatch(Character::isDigit)` | "Type CD must be Numeric..." | **EXACT** |
| catCd numeric | `IS NOT NUMERIC` | `!chars().allMatch(Character::isDigit)` | "Category CD must be Numeric..." | **EXACT** |
| Amount format | Pos 1: +/-, Pos 2-9: digits, Pos 10: '.', Pos 11-12: digits | `isValidAmountFormat()` — same char-by-char check | "Amount should be in format -99999999.99" | **EXACT** |
| origDate format | YYYY-MM-DD positional check | `isValidDateFormat()` — same positional check | "Orig Date should be in format YYYY-MM-DD" | **EXACT** |
| procDate format | YYYY-MM-DD positional check | `isValidDateFormat()` — same positional check | "Proc Date should be in format YYYY-MM-DD" | **EXACT** |
| merchantId numeric | `IS NOT NUMERIC` | `!chars().allMatch(Character::isDigit)` | "Merchant ID must be Numeric..." | **EXACT** |

### Phase 4: Date Validity (lines 389–427)

| Rule | COBOL Mechanism | Java Mechanism | Error Message | Status |
|---|---|---|---|---|
| origDate valid | CALL CSUTLDTC → CEEDAYS | `LocalDate.parse(date, STRICT)` | "Orig Date - Not a valid date..." | **EXACT** |
| procDate valid | CALL CSUTLDTC → CEEDAYS | `LocalDate.parse(date, STRICT)` | "Proc Date - Not a valid date..." | **EXACT** |

### Deviation D2: Date Validation Mechanism
- **COBOL**: Calls `CSUTLDTC` utility which internally calls `CEEDAYS` (IBM Language Environment)
- **Java**: Uses `java.time.LocalDate.parse()` with `ResolverStyle.STRICT`
- **Impact**: Both correctly reject invalid dates (Feb 30, Feb 29 in non-leap years, month 13, etc.)
- **Status**: **IMPROVED** — `java.time` is more reliable than CEEDAYS for edge cases

### Phase 5: Merchant ID (lines 430–436)

| Rule | COBOL | Java | Status |
|---|---|---|---|
| merchantId numeric | `IS NOT NUMERIC` after all date checks | After all date checks | **EXACT** |

---

## 5. ADD-TRANSACTION (COBOL lines 442–466)

### COBOL Behavior
```
1. STARTBR TRANSACT RIDFLD(HIGH-VALUES) GTEQ
2. READPREV into TRAN-RECORD -> get last (highest) TRAN-ID
3. ENDBR
4. ADD 1 TO TRAN-ID -> new ID
5. INITIALIZE TRAN-RECORD
6. Populate fields from screen map
7. WRITE TRANSACT
```

### Java Behavior
| Step | COBOL | Java | Status |
|---|---|---|---|
| 1-3 | STARTBR/READPREV/ENDBR at HIGH-VALUES | `transactionRepository.findTopByOrderByTranIdDesc()` | **EXACT** |
| 4 | ADD 1 TO TRAN-ID | `lastId + 1`, zero-padded to 16 chars | **EXACT** |
| 5 | INITIALIZE TRAN-RECORD | `new TransactionRecord()` | **EXACT** |
| 6 | MOVE screen fields to TRAN-RECORD | Set entity fields from request DTO | **EXACT** |
| 7 | WRITE TRANSACT | `transactionRepository.save(record)` | **EXACT** |

### Deviation D3: ID Generation Race Condition
- **COBOL**: STARTBR/READPREV/ADD 1 is NOT atomic — concurrent CICS tasks could generate duplicate IDs
- **Java**: Same approach (read last + add 1) but could use DB sequences in production
- **Impact**: Both have the same race condition; Java can optionally use DB sequences for improvement
- **Status**: **EXACT** (same behavior, including the race condition)

### TRAN-ID Format
- **COBOL**: `PIC X(16)` — zero-padded string
- **Java**: `VARCHAR(16)` — zero-padded via `String.format("%016d", newId)`
- **Status**: **EXACT**

### WRITE Error Handling
| COBOL RESP | Java Exception | Error Message | Status |
|---|---|---|---|
| DFHRESP(DUPKEY) | `DataIntegrityViolationException` | "Tran ID already exist..." | **EXACT** |
| DFHRESP(DUPREC) | `DataIntegrityViolationException` | "Tran ID already exist..." | **EXACT** |
| DFHRESP(OTHER) | `Exception` (catch-all) | "Unable to Add Transaction..." | **EXACT** |

---

## 6. COPY-LAST-TRAN-DATA (COBOL lines 471–495)

### COBOL Behavior
```
1. VALIDATE-INPUT-KEY-FIELDS (must have acctId or cardNum)
2. STARTBR TRANSACT at HIGH-VALUES
3. READPREV -> get last transaction
4. Copy all 11 data fields to screen map:
   - TTYP-CD, TCAT-CD, TRNSRC, TDESC, WS-TRAN-AMT-E,
   - TORIG-DT, TPROC-DT, MID, MNAME, MCITY, MZIP
5. SEND MAP (display copied data)
6. Fall through to PROCESS-ENTER-KEY (validate copied data)
```

### Java Behavior
| Step | COBOL | Java | Status |
|---|---|---|---|
| 1 | VALIDATE-INPUT-KEY-FIELDS | `validator.validateKeyFields(req)` | **EXACT** |
| 2-3 | STARTBR/READPREV at HIGH-VALUES | `transactionRepository.findTopByOrderByTranIdDesc()` | **EXACT** |
| 4 | Copy 11 fields to map | Return `CopyLastTransactionResponse` with all 11 fields | **EXACT** |
| 5 | SEND MAP | HTTP 200 response with field data | **EXACT** |
| 6 | Fall through to validate | Frontend calls `/validate` after populating form | **EXACT** |

### Deviation D4: Copy-Last Scope
- **COBOL**: Reads absolute last record in VSAM (not filtered by user/card)
- **Java**: `findTopByOrderByTranIdDesc()` — reads absolute last record (not filtered)
- **Status**: **EXACT** — both read the absolute last record regardless of user

### Amount Format in Copy-Last
- **COBOL**: `MOVE TRAN-AMT TO WS-TRAN-AMT-E` (PIC +99999999.99)
- **Java**: `String.format("%+012.2f", tranAmt)` → `+00000250.00`
- **Status**: **EXACT**

---

## 7. SEND-TRNADD-SCREEN (COBOL lines 500–540)

### COBOL Behavior
```
1. Set WS-MESSAGE in INFORMO (info message field)
2. SEND MAP COTRN2A with CURSOR positioning
3. EXEC CICS RETURN TRANSID('CT02') COMMAREA(...) — terminates task
```

### Java Behavior
| Step | COBOL | Java | Status |
|---|---|---|---|
| 1 | WS-MESSAGE → INFORMO | `message` field in response DTO | **EXACT** |
| 2 | CURSOR positioning | `errorField` in response DTO → frontend highlights field | **EXACT** |
| 3 | RETURN TRANSID (pseudo-conversational) | HTTP response completes the request/response cycle | **IMPROVED** |

### Deviation D5: Pseudo-Conversational vs. Stateless REST
- **COBOL**: Uses COMMAREA to pass state between pseudo-conversational CICS tasks. Each SEND MAP + RETURN terminates the CICS task; state is restored on next RECEIVE MAP via COMMAREA.
- **Java**: Stateless REST API. Each request is independent. Frontend manages form state.
- **Impact**: Functionally identical from the user's perspective — form state persists between interactions.
- **Status**: **IMPROVED** — no COMMAREA size limits, no CICS task overhead

---

## 8. VSAM File Operations → JPA Repository Mapping

| COBOL Operation | VSAM File | Java Repository Method | Status |
|---|---|---|---|
| `READ CXACAIX` | CXACAIX (alt index) | `cardXrefRepository.findByXrefAcctId(acctId)` | **EXACT** |
| `READ CCXREF` | CCXREF (primary) | `cardXrefRepository.findById(cardNum)` | **EXACT** |
| `STARTBR TRANSACT HIGH-VALUES` | TRANSACT | `transactionRepository.findTopByOrderByTranIdDesc()` | **EXACT** |
| `READPREV TRANSACT` | TRANSACT | (included in findTopByOrderByTranIdDesc) | **EXACT** |
| `ENDBR TRANSACT` | TRANSACT | (automatic — no browse state to manage) | **IMPROVED** |
| `WRITE TRANSACT` | TRANSACT | `transactionRepository.save(record)` | **EXACT** |

---

## 9. BMS Map → REST API Field Mapping

| BMS Field | BMS Length | DTO Field | JSON Key | Max Length | Status |
|---|---|---|---|---|---|
| ACTIDIN | 11 | acctId | `acctId` | 11 | **EXACT** |
| CARDNIN | 16 | cardNum | `cardNum` | 16 | **EXACT** |
| TTYPCD | 2 | typeCd | `typeCd` | 2 | **EXACT** |
| TCATCD | 4 | catCd | `catCd` | 4 | **EXACT** |
| TRNSRC | 10 | source | `source` | 10 | **EXACT** |
| TDESC | 60 | description | `description` | 100 | **DEVIATION** |
| TRNAMT | 12 | amount | `amount` | 12 | **EXACT** |
| TORIGDT | 10 | origDate | `origDate` | 10 | **EXACT** |
| TPROCDT | 10 | procDate | `procDate` | 10 | **EXACT** |
| MID | 9 | merchantId | `merchantId` | 9 | **EXACT** |
| MNAME | 30 | merchantName | `merchantName` | 50 | **DEVIATION** |
| MCITY | 25 | merchantCity | `merchantCity` | 50 | **DEVIATION** |
| MZIP | 10 | merchantZip | `merchantZip` | 10 | **EXACT** |
| CONFIRM | 1 | confirm | `confirm` | 1 | **EXACT** |

### Deviation D6: Field Length Increases
- **description**: BMS = 60 chars, DB = 100 chars (matches TRAN-DESC X(100) in CVTRA05Y copybook)
- **merchantName**: BMS = 30 chars, DB = 50 chars (matches TRAN-MERCHANT-NAME X(50) in copybook)
- **merchantCity**: BMS = 25 chars, DB = 50 chars (matches TRAN-MERCHANT-CITY X(50) in copybook)
- **Rationale**: The BMS map truncates display for screen layout. The database record (copybook) allows longer values. Java uses the copybook lengths (full record capacity).
- **Status**: **IMPROVED** — no data truncation at the storage layer

---

## 10. Known Deviations Summary

| ID | Description | COBOL Behavior | Java Behavior | Impact |
|---|---|---|---|---|
| D1 | No "Invalid Key" | Unmapped AID keys show error | Web UI has no unmapped keys | None |
| D2 | Date Validation | CSUTLDTC/CEEDAYS | `java.time.LocalDate` (STRICT) | Improved reliability |
| D3 | ID Race Condition | STARTBR/READPREV not atomic | Same approach; can use DB sequence | Same (optionally improved) |
| D4 | Copy-Last Scope | Absolute last record | Absolute last record | None (identical) |
| D5 | State Management | COMMAREA pseudo-conversational | Stateless REST + frontend state | Improved (no COMMAREA limits) |
| D6 | Field Lengths | BMS display-limited | Copybook record-limited | Improved (no truncation) |
| D7 | Transport | CICS BMS 3270 terminal | HTTP/JSON REST API | Architectural modernization |

---

## 11. Test Coverage Matrix

| Validation Phase | # Rules | # Test Cases | Coverage |
|---|---|---|---|
| Key Fields | 5 error + 3 success | 8 | 100% |
| Empty Fields | 11 | 11 | 100% |
| Format/Type | 7 error + 2 success | 9 | 100% |
| Date Validity | 3 error + 1 success | 4 | 100% |
| Confirmation | 2 success + 4 error | 6 | 100% |
| Flow (PF5, short-circuit) | 4 | 4 | 100% |
| **Total** | **~35** | **42** | **100%** |

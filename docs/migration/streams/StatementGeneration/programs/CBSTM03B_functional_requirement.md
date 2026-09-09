# CBSTM03B — Functional Requirements

**Program:** `app/cbl/CBSTM03B.CBL` (BATCH subroutine, 230 lines) — "Does file processing related to
Transact Report" (`CBSTM03B.CBL:5-8`).
**Called by:** CBSTM03A only (`docs/migration/CardDemo_inventory.md:193`), statically as
`CALL 'CBSTM03B' USING WS-M03B-AREA`.
**Target:** `com.carddemo.batch.statement.StatementFileAccess` — the same operation/return-code contract over
the JPA repositories, used by step `STEP040`.

IDs are numbered `FR-B*`.

## 1. Interface

| ID | Requirement | Source |
| --- | --- | --- |
| FR-B1 | The single parameter is a 1036-byte area: DD name X(8), operation X(1), return code X(2), key X(25), key length S9(4), record data X(1000). | `CBSTM03B.CBL:100-112` |
| FR-B2 | The DD name selects the file: `TRNXFILE`, `XREFFILE`, `CUSTFILE`, `ACCTFILE`. Any other value returns immediately **without** setting a return code, leaving the caller's previous value in place. | `CBSTM03B.CBL:118-131` |
| FR-B3 | Operations are `O` open, `C` close, `R` sequential read, `K` keyed read (`W` write and `Z` rewrite are declared but not implemented, see FR-B8). | `CBSTM03B.CBL:102-108` |
| FR-B4 | After any implemented operation the file's COBOL FILE STATUS is returned in the return-code field: `00` success, `10` end of file, `23` record not found, other values as raised by the access method. | `CBSTM03B.CBL:151-152, 175-176, 200-201, 225-226` |

## 2. Per-file behaviour

| ID | Requirement | Source |
| --- | --- | --- |
| FR-B5 | `TRNXFILE` (indexed, key card number + transaction id, 350-byte record) and `XREFFILE` (indexed, key card number, 50-byte record) support open, **sequential** read in ascending key order, and close. | `CBSTM03B.CBL:31-41, 58-68, 133-179` |
| FR-B6 | `CUSTFILE` (indexed random, key X(9)) and `ACCTFILE` (indexed random, key 9(11)) support open, **keyed** read and close. The key is taken from the first `key-length` characters of the key field. | `CBSTM03B.CBL:43-53, 70-78, 181-229` |
| FR-B7 | A keyed read that finds no record returns `23` and leaves the record data unchanged. | `CBSTM03B.CBL:188-193, 213-218` |

## 3. Quirks reproduced

| ID | Requirement | Source |
| --- | --- | --- |
| FR-B8 **Q-3** | `W` and `Z` have no implementation: the paragraph falls straight through to the exit, which returns the file's *previous* status rather than an error. The statement stream never issues them. | `CBSTM03B.CBL:107-108, 133-155` |
| FR-B9 **Q-4** | The sequential reads are on `ORGANIZATION INDEXED ACCESS SEQUENTIAL` files, so records arrive in key order, not in insertion order. | `CBSTM03B.CBL:31-41` |
| FR-B10 **Q-5** | `CUSTFILE`'s record key is `PIC X(09)` while the customer id is `PIC 9(09)`; the lookup is a character comparison on the zero-padded digits. | `CBSTM03B.CBL:72`, `CUSTREC.cpy:5` |
| FR-B11 | An unknown DD name is silently ignored (FR-B2). The caller would then evaluate a stale return code; CBSTM03A never passes one. | `CBSTM03B.CBL:127-131` |

## 4. Traceability matrix

Tests are in `migration/carddemo/backend/src/test/java/com/carddemo/batch/statement/`.

| ID | Test |
| --- | --- |
| FR-B1, FR-B3 | `StatementFileAccessTest.readsXreffileSequentiallyInCardNumberOrderAndReportsEndOfFile` |
| FR-B2, FR-B8, FR-B11 | `StatementFileAccessTest.leavesTheReturnCodeUntouchedForUnknownDdNamesAndUnimplementedOperations` |
| FR-B4 | `StatementFileAccessTest.readsXreffileSequentiallyInCardNumberOrderAndReportsEndOfFile`, `StatementFileAccessTest.reportsAnErrorWhenAFileIsReadBeforeItIsOpened` |
| FR-B5, FR-B9 | `StatementFileAccessTest.readsXreffileSequentiallyInCardNumberOrderAndReportsEndOfFile`, `TrnxLayoutTest.reproRoundTripsTheWorkRecordThroughTheKeyedStore` |
| FR-B6, FR-B10 | `StatementFileAccessTest.readsCustfileAndAcctfileByKey` |
| FR-B7 | `StatementFileAccessTest.reportsNotFoundForAKeyThatIsNotOnFile` |

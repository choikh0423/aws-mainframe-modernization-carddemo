# Transaction Management — Stream Analysis (Step 2a: analyze_stream)

**Stream:** Transaction Management (CT00 / CT01 / CT02)
**SOURCE repo:** `choikh0423/aws-mainframe-modernization-carddemo` @ `main`
**Integration branch:** `devin/1784031395-migrate-transaction-mgmt`
**Target stack (confirmed STOP 0):** Spring Boot + H2 (in-memory) + JPA backend; React frontend; **parity mode OFF**.
**Hard-stop boundary:** this slice owns the **TRANSACT** file (read/browse/write). It only *reads* CARDXREF (by account AIX + by card) and does not mutate Account/Card/Customer data. Anything past writing a TRANSACT record (posting, interest, statements, authorizations) is out of scope.

---

## 1. Program inventory

| Program | Tx | BMS map/mapset | Function | Source | LOC |
|---------|----|----|----------|--------|-----|
| COTRN00C | CT00 | COTRN0A / COTRN00 | List transactions, paged 10/screen | `app/cbl/COTRN00C.cbl` | 699 |
| COTRN01C | CT01 | COTRN1A / COTRN01 | View one transaction by Tran ID | `app/cbl/COTRN01C.cbl` | 330 |
| COTRN02C | CT02 | COTRN2A / COTRN02 | Add a transaction | `app/cbl/COTRN02C.cbl` | 783 |
| CSUTLDTC | — | — | Date-validation utility (CALLed by COTRN02C) | `app/cbl/CSUTLDTC.cbl` | leaf util |

**Shared copybooks:** `COCOM01Y` (COMMAREA), `CVTRA05Y` (TRAN-RECORD 350B), `CVACT03Y` (CARD-XREF-RECORD 50B, COTRN02C only), `CVACT01Y` (account, COTRN02C only), `COTTL01Y`/`CSDAT01Y`/`CSMSG01Y` (titles/date/msg), `DFHAID`/`DFHBMSCA` (CICS attrs).

## 2. Data stores & access patterns

| Logical store | VSAM/file | Key | Record copybook | Ops used | By |
|---------------|-----------|-----|-----------------|----------|-----|
| TRANSACT | KSDS `TRANSACT` | TRAN-ID X(16), numeric | CVTRA05Y (350B) | STARTBR/READNEXT/READPREV/ENDBR (browse), READ (by key, UPDATE), WRITE | 00C browse, 01C read, 02C read-last+write |
| CARDXREF (by account) | AIX `CXACAIX` | XREF-ACCT-ID 9(11) | CVACT03Y (50B) | READ | 02C (resolve card from account) |
| CARDXREF (by card) | KSDS `CCXREF` | XREF-CARD-NUM X(16) | CVACT03Y (50B) | READ | 02C (validate card, resolve account) |

**Key generation (COTRN02C ADD):** browse TRANSACT to highest key (`MOVE HIGH-VALUES`, STARTBR, READPREV), take that TRAN-ID, `+1`, write. New Tran ID = max existing + 1 (16-digit numeric). Duplicate-key → "Tran ID already exist".

## 3. Leaf-first dependency DAG

```
                 ┌────────────────────────────────────────────┐
   Wave A (leaves / foundation)                                │
   ┌─ TransactionRecord (entity, CVTRA05Y)                     │
   ├─ CardXrefRecord (entity, CVACT03Y)                        │
   ├─ TransactionRepository  (findById, browse page fwd/back,  │
   │     findTopByOrderByIdDesc, save)                         │
   ├─ CardXrefRepository (findByAcctId, findByCardNum)         │
   ├─ DateValidationService  (CSUTLDTC → YYYY-MM-DD validity)  │
   └─ Spring Boot app shell + H2 schema/seed + React app shell │
        │            │              │
        ▼            ▼              ▼
   Wave B         Wave C         Wave D
   COTRN01C       COTRN00C       COTRN02C
   (View, read)   (List, browse) (Add: xref read + date util + write)
```

- The three online programs are independent CICS screens (no program-to-program `LINK/CALL`; they only `XCTL` for navigation). Their **shared code dependency** is the data-access layer + entities, which is why those are Wave A.
- **Leaf-first order:** Wave A (data/entities/util/shell) → B (View) → C (List) → D (Add). View before List/Add because it is the simplest single-key read and establishes the Transaction DTO/mapping the others reuse; Add is last because it depends on the CardXref reads, the date utility, the max-key generation, and the write path.
- `CSUTLDTC` is a pure leaf utility (date validity) — migrated in Wave A as `DateValidationService`.

## 4. uiSurface per program

### CT00 — COTRN00C (List) — map COTRN0A
- **Header:** TITLE01, TITLE02, TRNNAME (=CT00), PGMNAME, CURDATE (MM/DD/YY), CURTIME (HH:MM:SS).
- **Filter input:** `TRNIDIN` — start-from Tran ID (numeric; blank = from start).
- **Rows (10):** per row `SELnnnn` (select flag, valid = `S`), `TRNIDnn` (Tran ID), `TDATEnn` (date MM/DD/YY from TRAN-ORIG-TS), `TDESCnn` (description), `TAMTnnn` (signed amount `+99999999.99`).
- **Footer:** `PAGENUM`, `ERRMSG`.
- **Keys:** ENTER (apply filter / select row `S` → view CT01), PF3 (back to menu COMEN01C), PF7 (page up), PF8 (page down).
- **Messages:** "You are at the top of the page...", "You have reached the bottom of the page...", "Tran ID must be Numeric ...", "Invalid selection. Valid value is S".

### CT01 — COTRN01C (View) — map COTRN1A
- **Header:** same 6 header fields (TRNNAME=CT01).
- **Input:** `TRNIDIN` (Tran ID to view).
- **Display (read-only):** TRNID, CARDNUM, TTYPCD, TCATCD, TRNSRC, TRNAMT, TDESC, TORIGDT, TPROCDT, MID, MNAME, MCITY, MZIP, ERRMSG.
- **Keys:** ENTER (fetch & display), PF3 (back to caller/menu), PF4 (clear screen), PF5 (back to list CT00).
- **Messages:** "Tran ID can NOT be empty...", "Transaction ID NOT found...", "Unable to lookup Transaction...".

### CT02 — COTRN02C (Add) — map COTRN2A
- **Header:** same 6 header fields (TRNNAME=CT02).
- **Key inputs:** `ACTIDIN` (Account ID) OR `CARDNIN` (Card Number) — either one resolves the other via CARDXREF.
- **Data inputs:** `TTYPCD` (type, numeric), `TCATCD` (category, numeric), `TRNSRC` (source), `TDESC` (description), `TRNAMT` (`-99999999.99`), `TORIGDT`/`TPROCDT` (`YYYY-MM-DD`), `MID` (merchant id, numeric), `MNAME`, `MCITY`, `MZIP`.
- **Confirm:** `CONFIRM` (Y/N) — must be `Y` to write.
- **Keys:** ENTER (validate → on Y write), PF3 (back), PF4 (clear), PF5 (copy last transaction's data into the form).
- **Success:** green "Transaction added successfully. Your Tran ID is <id>.".

## 5. Field dictionary (canonical)

### TRAN-RECORD (CVTRA05Y, 350B) → `transactions` table
| COBOL field | PIC | Java type | Column |
|-------------|-----|-----------|--------|
| TRAN-ID | X(16) | String(16) | id (PK) |
| TRAN-TYPE-CD | X(02) | String(2) | type_cd |
| TRAN-CAT-CD | 9(04) | Integer | cat_cd |
| TRAN-SOURCE | X(10) | String(10) | source |
| TRAN-DESC | X(100) | String(100) | description |
| TRAN-AMT | S9(09)V99 | BigDecimal(11,2) | amount |
| TRAN-MERCHANT-ID | 9(09) | Long | merchant_id |
| TRAN-MERCHANT-NAME | X(50) | String(50) | merchant_name |
| TRAN-MERCHANT-CITY | X(50) | String(50) | merchant_city |
| TRAN-MERCHANT-ZIP | X(10) | String(10) | merchant_zip |
| TRAN-CARD-NUM | X(16) | String(16) | card_num |
| TRAN-ORIG-TS | X(26) | String(26) | orig_ts |
| TRAN-PROC-TS | X(26) | String(26) | proc_ts |
| FILLER | X(20) | — | — |

### CARD-XREF-RECORD (CVACT03Y, 50B) → `card_xref` table
| COBOL field | PIC | Java type | Column |
|-------------|-----|-----------|--------|
| XREF-CARD-NUM | X(16) | String(16) | card_num (PK) |
| XREF-CUST-ID | 9(09) | Long | cust_id |
| XREF-ACCT-ID | 9(11) | Long | acct_id (indexed for AIX access) |

## 6. Wave grouping

| Wave | Content | Repos touched | UI-bearing | Depends on |
|------|---------|---------------|------------|------------|
| **A** | Foundation: `TransactionRecord`/`CardXrefRecord` entities, `TransactionRepository`/`CardXrefRepository`, `DateValidationService` (CSUTLDTC), H2 `schema.sql`+`data.sql` seed, Spring Boot app shell, React app shell + shared layout/header | BACKEND + FRONTEND | shell only | — |
| **B** | COTRN01C View (`GET /api/transactions/{id}`, view page) | BACKEND + FRONTEND | yes | A |
| **C** | COTRN00C List (`GET /api/transactions?startId&dir` paged 10, list page w/ PF7/PF8 + select) | BACKEND + FRONTEND | yes | A |
| **D** | COTRN02C Add (account/card resolve, field+date validation, max-key+1, write; add page w/ confirm + copy-last) | BACKEND + FRONTEND | yes | A (+ reuses view/list DTOs) |

## 7. Stored-procedure candidate table

| Data-access leaf | Physical layer | SP? | Decision |
|------------------|----------------|-----|----------|
| TRANSACT read/browse/write | H2 (in-memory) via JPA | **NO** | Plain Spring Data JPA repository. No stored procedures. |
| CARDXREF reads (by acct / by card) | H2 via JPA | **NO** | Plain JPA repository (`findByAcctId`, `findByCardNum`). |

**No stored procedures** for this slice: the target DB is H2 and parity mode is OFF, so all data access is plain JPA. No `!accurate_stored_proc`, no DBA registration requests. (Recorded per playbook Phase 1 requirement.)

## 8. Absent-module / out-of-scope flags
- CARDXREF, ACCTDAT, CUSTDAT are **owned by other streams** (Account/Card Management). This slice seeds a minimal read-only CARDXREF (+ any account rows needed for CT02 validation) so CT02 can resolve account↔card; it does not migrate those streams' screens.
- No IMS / MQ / DB2-optional dependencies in this stream.
- CICS pseudo-conversational state (COMMAREA `CDEMO-CT00-INFO` paging cursor) becomes REST request params (`startId`, `dir`) — stateless; no server session.

## 9. Notable behaviors to preserve (1:1)
- **Paging (CT00):** exactly 10 rows/page; PF7 up / PF8 down; "top/bottom of page" boundary messages; page number display; `S` selects a row → opens CT01 for that Tran ID.
- **Tran ID must be numeric** filter validation (CT00).
- **CT02 key resolution:** entering Account ID resolves Card Number via `CXACAIX` AIX; entering Card Number resolves Account via `CCXREF`; both-empty error "Account or Card Number must be entered...".
- **CT02 field validation order** and exact messages (empty checks, numeric checks, amount format `-99999999.99`, dates `YYYY-MM-DD` validated by CSUTLDTC, merchant id numeric).
- **CT02 new-key generation:** max existing TRAN-ID + 1; duplicate → "Tran ID already exist...".
- **CT02 confirm gate:** must be `Y`/`y` to write; otherwise "Confirm to add this transaction..." or "Invalid value. Valid values are (Y/N)...".
- **CT02 copy-last (PF5):** pre-fills the form with the last transaction's data.
- **Amount display:** signed, 2 decimals; list shows `+99999999.99` formatting.

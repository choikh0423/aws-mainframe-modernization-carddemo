# CardDemo Online Module — Stream Map (Step 1: identify_module_streams)

**Module / entry transaction:** `CC00` (COSGN00C sign-on) → `CM00` (COMEN01C main menu) → functional streams
**Repo (SOURCE):** `choikh0423/aws-mainframe-modernization-carddemo` @ `main`
**Source paths:** `app/cbl/` (programs), `app/cpy/` (copybooks), `app/bms/` (BMS maps)
**Navigation mechanism:** CICS pseudo-conversational; screen-to-screen routing via `XCTL` using `CDEMO-TO-PROGRAM` / `CDEMO-FROM-PROGRAM` fields in the shared COMMAREA copybook `COCOM01Y`.

## Purpose
Enumerate the independent functional streams of the CardDemo online (CICS) application so a single stream can be selected for 1:1 migration. Streams are grouped by functional area and the VSAM/DB2 data they own.

## Stream inventory

| # | Stream | Transactions | Programs | BMS maps | Primary data | Status |
|---|--------|--------------|----------|----------|--------------|--------|
| 1 | **Transaction Management** | CT00, CT01, CT02 | COTRN00C, COTRN01C, COTRN02C | COTRN00, COTRN01, COTRN02 | TRANSACT (KSDS, CVTRA05Y); reads CARDXREF (CVACT03Y), ACCTDAT (CVACT01Y) | **ACTIVE — SELECTED** |
| 2 | Account Management | CAVW, CAUP | COACTVWC, COACTUPC | COACTVW, COACTUP | ACCTDAT (CVACT01Y), CUSTDAT, CARDXREF | active |
| 3 | Card Management | CCLI, CCDL, CCUP | COCRDLIC, COCRDSLC, COCRDUPC | COCRDLI, COCRDSL, COCRDUP | CARDDAT (CVACT02Y), CARDXREF (CVACT03Y) | active |
| 4 | User Management (Security) | CU00, CU01, CU02, CU03 | COUSR00C, COUSR01C, COUSR02C, COUSR03C | COUSR00-03 | USRSEC (CSUSR01Y) | active |
| 5 | Auth / Sign-on + Menu | CC00, CM00, CA00 | COSGN00C, COMEN01C, COADM01C | COSGN00, COMEN01, COADM01 | USRSEC; menu routing tables | active (cross-cutting shell) |
| 6 | Bill Payment | CB00 | COBIL00C | COBIL00 | ACCTDAT, TRANSACT | active |
| 7 | Reporting | CR00 | CORPT00C | CORPT00 | TRANSACT, TCATBALF; submits batch (JCL) | active |
| 8 | Transaction Type Mgmt (DB2, optional) | CTTU, CTLI | COTRTUPC, COTRTLIC | COTRTUP, COTRTLI | DB2 Transaction Type tables | optional module (Db2) |
| 9 | Pending Authorizations (IMS-DB2-MQ, optional) | CPVS, CPVD, CP00 | COPAUS0C, COPAUS1C, COPAUA0C | COPAU00, COPAU01 | IMS DB + DB2 + MQ | optional module (IMS/MQ) — **blocked** (out of scope infra) |
| 10 | MQ Inquiry (optional) | CDRD, CDRA | CODATE01, COACCT01 | — | MQ request/response | optional module (MQ) — **blocked** (out of scope infra) |

## Selected stream — #1 Transaction Management (detail)

| Transaction | Program | Function | Data access | UI-bearing |
|-------------|---------|----------|-------------|------------|
| CT00 | COTRN00C | List transactions (paged browse) | STARTBR/READNEXT/READPREV/ENDBR on TRANSACT | yes (COTRN00 map) |
| CT01 | COTRN01C | View single transaction by ID | READ TRANSACT by key | yes (COTRN01 map) |
| CT02 | COTRN02C | Add a transaction | READ CARDXREF + ACCTDAT (validate), WRITE TRANSACT; date validation via CSUTLDTC | yes (COTRN02 map) |

**Shared copybooks:** `COCOM01Y` (COMMAREA), `CVTRA05Y` (TRAN-RECORD, 350 bytes), `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `DFHAID`, `DFHBMSCA`. COTRN02C additionally uses `CVACT01Y` (account) and `CVACT03Y` (card xref).

**Intra-stream navigation:** COMEN01/COADM01 → COTRN00C (list); COTRN00C —PF/select→ COTRN01C (view); menu → COTRN02C (add); all return to caller (`CDEMO-FROM-PROGRAM`) or COMEN01C/COSGN00C.

**External dependency:** `CSUTLDTC` (date validation utility, CALLed by COTRN02C) — a leaf utility, in-scope as a helper.

## Blocked / out-of-scope streams
- **#9 Pending Authorizations** and **#10 MQ Inquiry** depend on IMS DB and MQ infrastructure not provisioned for this migration — excluded.
- **#8 Transaction Type Mgmt** depends on the optional Db2 module; excluded unless explicitly requested.

## Recommendation
Migrate **Stream #1 Transaction Management** as a self-contained slice: it owns the TRANSACT file, has clean read/browse/write leaf operations, and only reads (does not mutate) the Account/Card data owned by other streams — a clean hard-stop boundary.

# S-08 TransactionTypeManagement — Migration Plan

Companion to `TransactionTypeManagement_analysis.md` (source analysis) and
`TransactionTypeManagement_functional_requirement.md` (the numbered FRs this plan implements).

Scope: CICS **CTLI** (`COTRTLIC`) and **CTTU** (`COTRTUPC`), batch `COBTUPDT` under `MNTTRDB2.jcl`,
plus the DB2 jobs `TRANEXTR.jcl` and `CREADB21.jcl`, and the BMS maps `COTRTLI` / `COTRTUP`.

---

## 1. Boundary decisions

| # | Decision | Rationale |
|---|---|---|
| B-08 (D-7) | Embedded `EXEC SQL` against `CARDDEMO.TRANSACTION_TYPE` / `…_CATEGORY` becomes **JPA/Spring Data** against `db2_transaction_type` / `db2_transaction_type_category` in the same PostgreSQL target | The tables already exist in the foundation baseline (`V1__baseline_carddemo_estate.sql`) with the DDL column definitions; the entities in `com.carddemo.common.domain` are reused unchanged. **No Flyway migration is added** — the S-08 band `V800`–`V899` stays empty. |
| B-08a | DB2 `SQLCODE` handling is translated to **stream-private exceptions**, one per legacy `SQLCODE` branch, each carrying the verbatim COBOL literal | The screens' error text is part of the contract (FR-L23/L24, FR-U18/U19). `SQLCODE +100` → not-found exception, `-532` (RI child rows) → the child-record refusal, `-911` → the deadlock message. `-532` is detected as a `DataIntegrityViolationException` on the FK from the category table. |
| B-08b | The CICS **pseudo-conversation** is modelled as one request per 3270 interaction: the request carries the AID key, the map fields and the COMMAREA state; the response carries the new map fields, the new state and the message line | CTLI and CTTU are state machines (`TTUP-*` flags, `WS-*-FLAG` paging state), not CRUD screens. Modelling the turn keeps every legacy transition, quirk and message directly testable, and keeps the browser free of business rules. The stateless `startKey`/`dir` style of the S-04 reference is kept underneath for the read paths. |
| B-08c | `EXEC CICS XCTL` targets are returned as `nextProgram` in the response and resolved by the React registry (`pathForProgram`) | Matches the foundation's navigation seam (README §4); no path is hard-coded. |
| B-01 | `CALL 'CEE3ABD'` / `MOVE 4 TO RETURN-CODE` becomes `AbendService` + a failed step | Foundation seam; `BatchJobLauncher` turns the failed job into exit code 12. |
| D-3 | One Spring Batch `Job` per JCL job, one `Step` per `EXEC PGM=` | `MNTTRDB2` → job `MNTTRDB2`, `TRANEXTR` → job `TRANEXTR` (5 steps), `CREADB21` → job `CREADB21` (5 steps). |
| Package | Online **and** batch code lives under `com.carddemo.trantype` (`…​.controller/.service/.dto/.exception/.batch`) | The stream brief says "backend code goes in `com.carddemo.trantype` only". This diverges from `migration/carddemo/README.md`, which lists S-08 as `com.carddemo.transactiontype` and would put the jobs in `com.carddemo.batch.db2refresh`. The brief is the more specific instruction and keeping every file in one tree removes any chance of colliding with a parallel stream. Recorded as a contradiction in §6. |
| Data dir | `TRANEXTR` reads/writes files under `carddemo.batch.data-dir`, and `MNTTRDB2` reads its SYSIN-equivalent from the `inputFile` job parameter | Same convention as the `DATALOAD` reference job. |

---

## 2. Backend implementation

```
com.carddemo.trantype
├── controller
│   ├── TransactionTypeListController        CTLI  /api/admin/transaction-types
│   └── TransactionTypeUpdateController      CTTU  /api/admin/transaction-types/maintenance
├── dto        request/response records — one pair per screen turn
├── exception  one exception per legacy SQLCODE / validation branch + @RestControllerAdvice
├── service
│   ├── TransactionTypeListService           the CTLI state machine (paging, filters, actions)
│   └── TransactionTypeUpdateService         the CTTU state machine (TTUP-* transitions)
├── validator  TransactionTypeValidator      the field edits shared by both screens
└── batch      TranTypeMaintenanceJobConfiguration (MNTTRDB2)
              TranTypeExtractJobConfiguration     (TRANEXTR)
              TranTypeLoadJobConfiguration        (CREADB21)
```

Both screens sit under `/api/admin/**`, so the foundation `SecurityConfig` requires the `ADMIN` role —
the same gate `COADM01C` applied by only ever offering CTLI/CTTU from the admin menu.

### CTLI — `POST /api/admin/transaction-types` (one screen turn)

Request: `aid` (`ENTER`/`PF2`/`PF3`/`PF7`/`PF8`/`PF10`), `typeFilter`, `descFilter`, the seven rows
(`selection`, `type`, `description`), and the carried state (`pageNumber`, `firstKey`, `lastKey`,
`lastPageShown`, `pendingAction`, `pendingKey`).
Response: the seven rows to display, the carried state, `infoMessage`, `errorMessage`, `nextProgram`.

Mapping of the legacy paragraphs:

| Legacy | Target |
|---|---|
| `0000-MAIN` first entry (`EIBCALEN=0`) | request with no state → page 1, `Type U to update, D to delete any record` |
| `2000-PROCESS-INPUTS` / `2100-RECEIVE-MAP` filter edits | `TransactionTypeValidator.validateFilters` |
| `9000-READ-FORWARD` (7 rows + 1 look-ahead) | `findPageForward` — `Pageable` of 8, the 8th row only sets `hasNextPage` |
| `9100-READ-BACKWARD` | `findPageBackward` — descending fetch, reversed for display |
| `9500-UPDATE-DB` / `9600-DELETE-DB` | `applyRowAction`, two-turn confirm (`PF10`) |

The forward/backward cursors become derived Spring Data queries plus a `@Query` for the
`TR_DESCRIPTION LIKE :pattern` filter; the legacy `%…%` wrapping of the description filter is kept.

### CTTU — `POST /api/admin/transaction-types/maintenance` (one screen turn)

Request: `aid` (`ENTER`/`PF3`/`PF4`/`PF5`/`PF12`), `type`, `description`, and the carried
`state` + `originalType`/`originalDescription`.
Response: `state`, `type`, `description`, `infoMessage`, `errorMessage`, `nextProgram`.

`state` is the `TTUP-*` flag set verbatim (`DETAILS_NOT_FETCHED`, `INVALID_SEARCH_KEYS`,
`DETAILS_NOT_FOUND`, `SHOW_DETAILS`, `CREATE_NEW_RECORD`, `CHANGES_NOT_OK`, `CHANGES_OK_NOT_CONFIRMED`,
`CHANGES_OKAYED_AND_DONE`, `CHANGES_BACKED_OUT`, `DELETE_*`). The save path reproduces the legacy
**UPDATE-first, INSERT on `SQLCODE +100`** sequence (FR-U22) rather than the "natural" upsert.

### Preserved quirks (implemented deliberately, tested explicitly)

1. CTLI's type-filter edit only tests *numeric*, never length, although the message says "2 DIGIT NUMBER" (FR-L06).
2. CTTU normalises the type by numeric conversion, so `7` becomes `07` and `1 ` becomes `01` (FR-U07).
3. CTTU treats `*` and spaces in the input fields as low-values (FR-U05).
4. CTTU's save does UPDATE then INSERT on `+100` (FR-U22).
5. `COBTUPDT`'s abend routine sets RC 4 and keeps reading the next record (FR-B09).
6. `COTRTUP.bms` advertises `F6=Add` although `COTRTUPC` never enables it — the key is rendered and
   answered with the legacy `Invalid key pressed` (FR-U21).
7. `CREADB21` loads with plain `INSERT`, so a second run fails on duplicate keys (FR-C04).

---

## 3. Batch implementation

| Job | Steps | Notes |
|---|---|---|
| `MNTTRDB2` | `STEP01` (`IKJEFT01` → `RUN PROGRAM(COBTUPDT)`) | Chunk-oriented: `FlatFileItemReader` over the 53-byte input (`inputFile` job parameter), a processor that decodes `A/U/D/*` and applies the row, and the legacy `DISPLAY` lines logged verbatim. An invalid operation code raises the abend through `AbendService`; per FR-B09 the reader keeps going and the step still ends failed. |
| `TRANEXTR` | `STEP10`, `STEP20`, `STEP30`, `STEP40`, `STEP50` | Backup / backup / delete / unload types / unload categories. Steps 40 and 50 write fixed 60-byte records with the trailing `0` filler, ordered by primary key. |
| `CREADB21` | `FREEPLN`, `CRCRDDB`, `LDTTYPE`, `RUNTEP2`, `LDTCCAT` | `FREEPLN` and `LDTTYPE` have no target equivalent and are documented no-ops; `CRCRDDB` asserts the Flyway-created tables exist instead of issuing DDL; `RUNTEP2`/`LDTCCAT` insert the control members' literal rows. |

All three run through the existing launcher:

```bash
java -jar target/carddemo.jar --spring.main.web-application-type=none \
     --spring.profiles.active=postgres --spring.batch.job.name=MNTTRDB2 \
     --inputFile=/path/to/trantype-updates.txt
```

---

## 4. Frontend

| Screen | File | Route registry line |
|---|---|---|
| CTLI `COTRTLI` / `CTRTLIA` | `frontend/src/pages/trantype/TransactionTypeListPage.js` | `COTRTLIC` entry — `element` only |
| CTTU `COTRTUP` / `CTRTUPA` | `frontend/src/pages/trantype/TransactionTypeUpdatePage.js` | `COTRTUPC` entry — `element` only |

Both pages use the shared `Layout` (header `TRNNAME`/`PGMNAME`/`TITLE01`/`TITLE02`/`CURDATE`/`CURTIME`
and the row-24 PF line). Field labels, the seven-row grid, the PF-key lines
(`F2=Add  F3=Exit  F7=Page Up  F8=Page Dn  F10=Save` and
`ENTER=Process  F3=Exit  F4=Delete  F5=Save  F6=Add  F12=Cancel`) and every message string come
verbatim from the maps and the COBOL literals. Physical F-keys are bound to the same actions as the
buttons. No business rule is duplicated in the browser: each keystroke posts the screen turn and renders
whatever the backend returns.

---

## 5. Test strategy and parity

Every FR is covered — see the traceability matrix in
`TransactionTypeManagement_functional_requirement.md`. Tests run on the default H2 profile with the
foundation seed; the mutating suites use their own in-memory database URL so they do not disturb the
shared seeded context, exactly as `DataLoadJobTest` does.

| Layer | Tests |
|---|---|
| Validation / business rules | `TransactionTypeValidatorTest`, `TransactionTypeListServiceTest`, `TransactionTypeUpdateServiceTest` |
| Endpoints | `TransactionTypeListControllerTest`, `TransactionTypeUpdateControllerTest` (MockMvc, `@WithMockUser(roles="ADMIN")`) |
| Batch | `TranTypeMaintenanceJobTest`, `TranTypeExtractJobTest`, `TranTypeLoadJobTest` |

### Parity — main paths

| Path | Legacy behaviour | Migrated behaviour |
|---|---|---|
| CTLI open | 7 rows from the top of `TRANSACTION_TYPE`, info line `Type U to update, D to delete any record` | identical: 7 seeded rows `01`…`07`, same info line |
| CTLI PF8 at the end | rows stay, `No more pages to display` | identical |
| CTLI `U` + new description + PF10 | row updated, `HIGHLIGHTED row was updated` | identical |
| CTLI `D` + PF10 on a type with categories | `Please delete associated child records first:` | identical (FK violation mapped to the same text) |
| CTTU lookup `01` | details shown, `Selected transaction type shown above` | identical |
| CTTU lookup `99` | `No record found for this key in database` | identical |
| CTTU add (`F5` on not-found, then ENTER, then `F5`) | `Changes validated.Press F5 to save` → `Changes committed to database` | identical |
| `MNTTRDB2` with `A`/`U`/`D`/`*`/bad records | inserts, updates, deletes, ignores; bad op → `ERROR: TYPE NOT VALID`, RC 4, processing continues | identical, step ends failed, launcher exits 12 |
| `TRANEXTR` | 7 × 60-byte type records + 18 × 60-byte category records | identical byte layout |

Fixtures: the seeded reference data derives from `app/data/ASCII/trantype.txt` and `trancatg.txt`
(the same 7 types / 18 categories the DB2 control members load); the `MNTTRDB2` test input is a
53-byte fixture built from that data.

---

## 6. Contradictions and deferrals

1. **Package name.** README §1 says S-08 is `com.carddemo.transactiontype`; the stream brief says
   `com.carddemo.trantype`. Implemented as `com.carddemo.trantype`.
2. **Batch package.** README §6 says jobs go in `com.carddemo.batch.<job>` (and lists
   `com.carddemo.batch.db2refresh` for S-19); the brief's "`com.carddemo.trantype` only" wins, so the
   three job configurations are in `com.carddemo.trantype.batch`.
3. **Stream ownership of `TRANEXTR`/`CREADB21`.** The inventory files them under S-19
   TransactionTypeDB2Refresh; the brief executes them here. Implemented here.
4. **Seed spelling.** The foundation seed carries mixed-case descriptions and `Reversal`, while
   `ctl/DB2LTTYP.ctl` has upper-case descriptions and `REVERAL`. The seed is left untouched (shared
   data); the `CREADB21` job carries the legacy literals.
5. **`TYPRUN=SCAN` on `CREADB21`.** The shipped JCL is syntax-check-only. The job is implemented as
   runnable; nothing invokes it automatically.
6. **DB2 plan/package management** (`FREEPLN`, BIND) has no target equivalent and is not re-implemented.

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
| B-08a | DB2 `SQLCODE` handling is translated in the services themselves, each branch producing the verbatim COBOL literal through `TranTypeMessages` | The screens' error text is part of the contract (FR-L19/L24, FR-U17/U23). An empty repository result is the `+100` branch, a `DataIntegrityViolationException` on the category FK is the `-532` child-record refusal, a `PessimisticLockingFailureException`/`CannotAcquireLockException` is the `-911` deadlock message. Only the batch abend has its own type (`TranTypeAbendException`). |
| B-08b | The CICS **pseudo-conversation** is modelled as one request per 3270 interaction: the request carries the AID key, the map fields and the COMMAREA state; the response carries the new map fields, the new state and the message line | CTLI and CTTU are state machines (`TTUP-*` flags, `WS-*-FLAG` paging state), not CRUD screens. Modelling the turn keeps every legacy transition, quirk and message directly testable, and keeps the browser free of business rules. The stateless `startKey`/`dir` style of the S-04 reference is kept underneath for the read paths. |
| B-08c | `EXEC CICS XCTL` targets are returned as `nextProgram` in the response and resolved by the React registry (`pathForProgram`) | Matches the foundation's navigation seam (README §4); no path is hard-coded. |
| B-01 | `CALL 'CEE3ABD'` / `MOVE 4 TO RETURN-CODE` becomes the step exit status `RC=4` on an otherwise completed step | `9999-ABEND` in `COBTUPDT` displays the reason and moves 4 to RETURN-CODE but does **not** stop the run (FR-B09), so a failed step would be wrong: the JCL saw condition code 4 and the later records were still applied. The exit status carries the 4; `TranTypeAbendException` carries the reason text. |
| D-3 | One Spring Batch `Job` per JCL job, one `Step` per `EXEC PGM=` | `MNTTRDB2` → job `MNTTRDB2`, `TRANEXTR` → job `TRANEXTR` (5 steps), `CREADB21` → job `CREADB21` (5 steps). |
| Package | Online **and** batch code lives under `com.carddemo.trantype` (`…​.controller/.service/.dto/.exception/.batch`) | The stream brief says "backend code goes in `com.carddemo.trantype` only". This diverges from `migration/carddemo/README.md`, which lists S-08 as `com.carddemo.transactiontype` and would put the jobs in `com.carddemo.batch.db2refresh`. The brief is the more specific instruction and keeping every file in one tree removes any chance of colliding with a parallel stream. Recorded as a contradiction in §6. |
| Data dir | `TRANEXTR` reads/writes its datasets under the `hlq` job parameter (the directory standing in for `AWS.M2.CARDDEMO`), and `MNTTRDB2` reads INPFILE from the `inpfile` job parameter | Job parameters keep both jobs runnable through the shared `BatchJobLauncher` without new configuration properties. |

---

## 2. Backend implementation

```
com.carddemo.trantype
├── controller
│   ├── TranTypeListController          CTLI  POST /api/admin/transaction-types/list
│   └── TranTypeUpdateController        CTTU  POST /api/admin/transaction-types/update
├── dto        request/response/state records — one set per screen turn
├── exception  TranTypeAbendException     the COBTUPDT 9999-ABEND seam
├── message    TranTypeMessages           every COBOL literal, in one place
├── repository TranTypeBrowseRepository, TranTypeCategoryLookupRepository (read-only, over the shared entities)
├── service
│   ├── TranTypeListService             the CTLI state machine (paging, filters, actions)
│   └── TranTypeUpdateService           the CTTU state machine (TTUP-* transitions)
├── validator  TranTypeValidator          the field edits shared by both screens
└── batch      TranTypeMaintenanceJobConfiguration (MNTTRDB2)
              TranTypeExtractJobConfiguration     (TRANEXTR)
              CreateDb2TablesJobConfiguration     (CREADB21)
```

Both screens sit under `/api/admin/**`, so the foundation `SecurityConfig` requires the `ADMIN` role —
the same gate `COADM01C` applied by only ever offering CTLI/CTTU from the admin menu.

### CTLI — `POST /api/admin/transaction-types/list` (one screen turn)

Request: `aid` (`ENTER`/`PF2`/`PF3`/`PF7`/`PF8`/`PF10`), `typeFilter`, `descFilter`, the seven rows
(`selection`, `description`), and the carried `state` (the COMMAREA: `typeFilter`, `descFilter`,
`screenNum`, `firstTypeCode`, `lastTypeCode`, `nextPageExists`, `lastPageShown`, `rowSelected`,
`updateRequested`, `deleteRequested`, the fetched rows).
Response: the seven display rows (`typeCode`, `description`, `highlighted`, `inError`), the carried
state, `pageNumber`, `infoMessage`, `errorMessage`, `protectSelectRows`, `nextProgram`, `nextTranId`.

Mapping of the legacy paragraphs:

| Legacy | Target |
|---|---|
| `0000-MAIN` first entry (`EIBCALEN=0`) | request with no state → page 1, `Type U to update, D to delete any record` |
| `2000-PROCESS-INPUTS` / `2100-RECEIVE-MAP` filter edits | `TransactionTypeValidator.validateFilters` |
| `8000-READ-FORWARD` (7 rows + 1 look-ahead) | `readForward` — `TranTypeBrowseRepository.readForward` fetches 8, the 8th only sets `nextPageExists` |
| `8100-READ-BACKWARD` | `readBackward` — descending fetch, reversed for display |
| `9500-UPDATE-TRAN-TYPE` / `9600-DELETE-TRAN-TYPE` | `updateRecord` / `deleteRecord`, two-turn confirm (`PF10`) |

The forward/backward cursors are two JPQL `@Query` methods over the shared entity, each carrying the
optional type equality and the `TR_DESCRIPTION LIKE :pattern` filter; the legacy `%…%` wrapping of the
description filter is kept.

### CTTU — `POST /api/admin/transaction-types/update` (one screen turn)

Request: `aid` (`ENTER`/`PF3`/`PF4`/`PF5`/`PF12`), `typeCode`, `description`, and the carried `state`
(`changeAction`, the old/new type and description, `programReenter`).
Response: `typeCode`, `description`, `infoMessage`, `errorMessage`, the field/key enablement flags
(`typeCodeEditable`, `descriptionEditable`, `enterEnabled`, `f4Enabled`, `f5Enabled`, `f12Enabled`),
`nextProgram`, `nextTranId` and the new `state`.

`state.changeAction` carries the `TTUP-CHANGE-ACTION` values verbatim (`' '` not fetched, `K` invalid
search keys, `X` details not found, `S` show details, `R` create new record, `E`/`N`/`L`/`F`/`C`/`B`
for the change path and `9`/`8`/`7`/`6` for the delete path). The save path reproduces the legacy
**UPDATE-first, INSERT on `SQLCODE +100`** sequence (FR-U22) rather than the "natural" upsert.

### Preserved quirks (implemented deliberately, tested explicitly)

1. CTLI's type-filter edit only tests *numeric*, never length, although the message says "2 DIGIT NUMBER" (FR-L03).
2. CTTU normalises the type by numeric conversion, so `7` becomes `07` and `1 ` becomes `01` (FR-U07).
3. CTTU treats `*` and spaces in the input fields as low-values (FR-U24).
8. The F8 that *reaches* the last page reports `No more pages for these search conditions`; only a
   further F8 gives `No more pages to display` (FR-L09).
9. A blank CTTU key answers `Tran Type code must be supplied.`, so the program's `No input received`
   literal is effectively unreachable from the key field (FR-U04).
4. CTTU's save does UPDATE then INSERT on `+100` (FR-U22).
5. `COBTUPDT`'s abend routine sets RC 4 and keeps reading the next record (FR-B09).
6. `COTRTUP.bms` advertises `F6=Add` although `COTRTUPC` never tests `CCARD-AID-PFK06` — the caption is
   documented in the page but no F6 button is offered, and an F6 send is answered with the legacy
   `Invalid key pressed` (FR-U20).
7. `CREADB21` loads with plain `INSERT`, so a second run fails on duplicate keys (FR-C04).

---

## 3. Batch implementation

| Job | Steps | Notes |
|---|---|---|
| `MNTTRDB2` | `STEP1` (`IKJEFT01` → `RUN PROGRAM(COBTUPDT)`) | A tasklet, not a chunk, because `COBTUPDT` is a read loop that survives its own abend: it reads the 53-byte INPFILE (`inpfile` job parameter), decodes `A/U/D/*` and logs the legacy `DISPLAY` lines verbatim. Each record runs in its own transaction, so a failure leaves the earlier records applied and the loop carries on (FR-B09); if any record abended the step ends `COMPLETED` with exit status `RC=4`. |
| `TRANEXTR` | `STEP10`, `STEP20`, `STEP30`, `STEP40`, `STEP50` | Backup / backup / delete / unload types / unload categories, under the `hlq` directory. The backups are GDG generations (`…BKUP.G0001V00`, `G0002V00`, …); as on z/OS, STEP10 fails when the previous run's PS dataset is missing, so a first run on a fresh installation ends on a JCL error. Steps 40 and 50 write fixed 60-byte records with the trailing `0` filler, ordered by primary key. |
| `CREADB21` | `FREEPLN`, `CRCRDDB`, `LDTTYPE`, `RUNTEP2`, `LDTCCAT` | `FREEPLN` (BIND/FREE of the DB2 plan) has no target equivalent and is a logged no-op; `CRCRDDB` asserts the Flyway-created tables exist instead of issuing DDL; `LDTTYPE`, `RUNTEP2` and `LDTCCAT` insert the control members' literal rows, keeping `CODER=AWS`, `LBNM=AWS.M2.CARDDEMO` and `DB2S=DAZ1` as constants. |

All three run through the existing launcher:

```bash
java -jar target/carddemo.jar --spring.main.web-application-type=none \
     --spring.profiles.active=postgres --spring.batch.job.name=MNTTRDB2 \
     --inpfile=/path/to/INPFILE
```

---

## 4. Frontend

| Screen | File | Route registry line |
|---|---|---|
| CTLI `COTRTLI` / `CTRTLIA` | `frontend/src/pages/trantype/TranTypeListPage.js` | `COTRTLIC` entry — `element` only |
| CTTU `COTRTUP` / `CTRTUPA` | `frontend/src/pages/trantype/TranTypeUpdatePage.js` | `COTRTUPC` entry — `element` only |

Both pages post their screen turn through `frontend/src/api/tranTypes.js`.

Both pages use the shared `Layout` (header `TRNNAME`/`PGMNAME`/`TITLE01`/`TITLE02`/`CURDATE`/`CURTIME`
and the row-24 PF line). Field labels, the seven-row grid, the PF-key lines
(`ENTER=Process  F2=Add  F3=Exit  F7=Page Up  F8=Page Dn  F10=Save` and
`ENTER=Process  F3=Exit` plus the state-dependent `F4=Delete`, `F5=Save`, `F12=Cancel`) and every
message string come verbatim from the maps and the COBOL literals. Physical F-keys are bound to the same actions as the
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
| Validation / business rules | `TranTypeValidatorTest`, `TranTypeListServiceTest`, `TranTypeUpdateServiceTest` |
| Endpoints | `TranTypeControllerTest` (MockMvc, `@WithMockUser(roles="ADMIN")`, plus the non-admin and anonymous refusals) |
| Batch | `TranTypeMaintenanceJobTest`, `TranTypeExtractJobTest`, `CreateDb2TablesJobTest` |

### Parity — main paths

| Path | Legacy behaviour | Migrated behaviour |
|---|---|---|
| CTLI open | 7 rows from the top of `TRANSACTION_TYPE`, info line `Type U to update, D to delete any record` | identical: 7 seeded rows `01`…`07`, same info line |
| CTLI PF8 at the end | the F8 reaching the last page shows `No more pages for these search conditions`, a further F8 `No more pages to display` | identical |
| CTLI `U` + new description + PF10 | row updated, `HIGHLIGHTED row was updated` | identical |
| CTLI `D` + PF10 on a type with categories | `Please delete associated child records first:` | identical (FK violation mapped to the same text) |
| CTTU lookup `01` | details shown, `Selected transaction type shown above` | identical |
| CTTU lookup `99` | `No record found for this key in database` | identical |
| CTTU add (`F5` on not-found, then ENTER, then `F5`) | `Changes validated.Press F5 to save` → `Changes committed to database` | identical |
| `MNTTRDB2` with `A`/`U`/`D`/`*`/bad records | inserts, updates, deletes, ignores; bad op → `ERROR: TYPE NOT VALID`, RC 4, processing continues | identical; the step completes with exit status `RC=4` |
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

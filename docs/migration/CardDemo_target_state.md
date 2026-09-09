# CardDemo — Target State (what "migrated" means)

Produced by `!mf_ingest_target_state`. Status: **CONFIRMED at STOP A (2026-09-09) except the IMS/MQ/DB2 mapping in §6, which is pending a follow-up confirmation.**

### STOP A decisions (confirmed by Kyu, 2026-09-09)

| # | Decision | Outcome |
|---|---|---|
| D-1 | App topology | **One consolidated backend** (`com.carddemo.<stream>` packages) + **one React app** with the sign-on/menu shell |
| D-2 | Data target | **PostgreSQL** as the real target; **H2 profile for CI/tests** |
| D-3 | Batch runtime | **Spring Batch 5** — one `Job` per JCL job, one `Step` per `EXEC PGM`, chunked, restartable via `JobRepository` |
| D-4 | Scope | **Everything**: core CICS + batch **plus** the DB2, IMS and MQ add-on modules |
| D-5 | STOP D autonomy | Notify only |

Engagement: migrate the **entire CardDemo estate — CICS online + batch** from COBOL/CICS/VSAM/JCL to the
target stack below.

Every field is either **FACT** (cited from an existing source in this repo) or **PROPOSED** (a default that
must be confirmed or corrected at STOP A). Nothing here may be guessed downstream: the stream's process type
selects CORE + the matching surface profile + DATA/BOUNDARY.

---

## 0. Repository topology (roles → routes)

| Role | Repo | Branch | Path | Evidence |
|---|---|---|---|---|
| SOURCE | `choikh0423/aws-mainframe-modernization-carddemo` | `main` | `app/cbl`, `app/cpy`, `app/bms`, `app/jcl`, `app/proc`, `app/scheduler`, `app/csd`, `app/ctl`, `app/data` | FACT — 31 COBOL programs, 17 BMS maps, 38 JCL, 30 copybooks |
| BACKEND | same repo | `main` | `migration/carddemo/backend` (per D-1) | reference: `migration/transaction-management/backend/pom.xml` |
| FRONTEND | same repo | `main` | `migration/carddemo/frontend` (per D-1) | reference: `migration/transaction-management/frontend/package.json` |
| DOCS | same repo | `main` | `docs/migration/`, `docs/migration/streams/<Stream>/` | CONFIRMED at STOP A |

**D-1 (confirmed): one consolidated backend + one React app.** Routes:

| Role | Path |
|---|---|
| BACKEND | `migration/carddemo/backend` — `com.carddemo.<stream>` package per stream, `com.carddemo.common` for shared services, `com.carddemo.batch` for the batch jobs |
| FRONTEND | `migration/carddemo/frontend` — one React app, sign-on + menu shell, one route per legacy screen |
| DOCS | `docs/migration/` (engagement) and `docs/migration/streams/<Stream>/` (per stream) |

The already-migrated Transaction Management module (`migration/transaction-management/**`) is the **reference
implementation**; its code is folded into the consolidated app as the Transaction Management stream rather
than re-migrated, and is not forked.

---

## 1. CORE profile (applies to every surface)

| Field | Value | Status |
|---|---|---|
| Language / runtime | Java 17 | FACT — `migration/transaction-management/backend/pom.xml` (`<java.version>17</java.version>`) |
| Framework | Spring Boot 3.2.5 (`spring-boot-starter-parent`) | FACT — same pom |
| Build tool | Maven (`mvn -B test`) | FACT — same pom; `.github/workflows/transaction-mgmt-ci.yml` |
| Layering | `controller` → `service` → `repository` → `entity`, plus `dto`, `validator`, `exception`, `util` | FACT — `backend/src/main/java/com/carddemo/transaction/**` |
| Package root | `com.carddemo.<stream>` | FACT — `com.carddemo.transaction` |
| DTO / mapping style | Hand-written request/response DTOs per use case (`TransactionListResponse`, `TransactionAddRequest`); no entity leakage past the service layer; no mapping framework | FACT — `dto/` |
| COBOL type mapping | `PIC X(n)` → `String` w/ `length=n`; `PIC 9(n)` → `Integer`/`Long`; `PIC S9(9)V99` → `BigDecimal(11,2)`; `PIC X(26)` timestamps kept as `String` in the record and formatted at the edge; FILLER not stored | FACT — `entity/TransactionRecord.java`, `resources/schema.sql` |
| Display formatting | COBOL PICTURE-accurate edited output isolated in a dependency-free util (`CobolFormat.amountEdited` → `+00000013.75`) with the COBOL line cited in the javadoc | FACT — `util/CobolFormat.java` |
| Error handling | Typed domain exceptions (`TransactionNotFoundException`, …) + `@RestControllerAdvice` per stream mapping to an `ErrorResponse` carrying the legacy screen message text verbatim | FACT — `exception/**` |
| Logging / observability | Spring Boot default logging; no bespoke framework | FACT (absence) / PROPOSED to keep |
| Test framework | JUnit 5 + Spring Boot Test + MockMvc; layered tests (repository, service, service-mock, controller) plus an end-to-end integration test per stream | FACT — `backend/src/test/java/**` |
| Test convention | Every FR requirement id (`FR-L1`, …) traceable to at least one test; javadoc on the controller cites the FR ids it implements | FACT — `controller/TransactionListController.java` |
| CI gate | GitHub Actions: backend `mvn -B test` + frontend `npm run build` on PR, path-filtered per migrated module | FACT — `.github/workflows/transaction-mgmt-ci.yml` |
| Forbidden | Lombok, mapping frameworks (MapStruct/ModelMapper), `ddl-auto` schema generation, entity classes as API payloads | PROPOSED (inferred from the reference module's absence of all four) |

---

## 2. ONLINE profile (CICS screen streams)

| Field | Value | Status |
|---|---|---|
| API style | REST, `/api/<resource>`; one controller per legacy screen; query params for browse/paging | FACT — `GET /api/transactions?startId=&dir=next\|prev` |
| Versioning | none (`/api/...` unversioned) | FACT |
| Request/response | JSON DTOs; edited COBOL display strings computed server-side so the UI renders exactly what the 3270 map showed | FACT — `CobolFormat`, `TransactionListRow` |
| Conversational state | CICS pseudo-conversational COMMAREA state (`COCOM01Y`, `CDEMO-FROM-PROGRAM`/`CDEMO-TO-PROGRAM`) is **not** carried server-side; paging keys are passed explicitly as request params and navigation is client-side routing | FACT — `TransactionListController` + `react-router-dom` |
| Validation + error surfacing | Validation in a `validator` class mirroring the COBOL edit order; the exact legacy message text is returned (e.g. `"Tran ID must be Numeric ..."`) | FACT — `validator/TransactionValidator.java` |
| UI framework | React 18 + `react-router-dom` 6 (Create React App / `react-scripts` 5), `npm run build` gate, dev proxy to `http://localhost:8080` | FACT — `frontend/package.json` |
| Component library / theming | none — plain components styled to resemble the 3270 map layout (field order, labels, PF-key footer) | FACT (absence) / PROPOSED to keep |
| i18n / accessibility | none; English labels taken verbatim from the BMS maps | FACT (absence) |
| Frontend tests | `react-scripts test` available; the reference module gates on **build only** in CI | FACT — CI workflow |
| PF keys | Legacy PF keys surfaced as buttons with the legacy caption (PF3 back, PF7/PF8 page) | FACT — reference frontend pages |

---

## 3. BATCH profile (JCL/COBOL batch chains)

**No reference implementation and no stack document exists for this surface** — every field below is
**PROPOSED** and needs a decision at STOP A. This is the thinnest part of the engagement and the decisions
here (restart semantics, dataset mapping, job launch) have the longest consequences.

| Field | Proposal | Notes / evidence from SOURCE |
|---|---|---|
| Batch runtime | **Spring Batch 5** (Boot 3.2.x), one `Job` per JCL job, one `Step` per `EXEC PGM=` step | 38 JCL jobs in `app/jcl`, e.g. `POSTTRAN.jcl` (`STEP15 EXEC PGM=CBTRN02C`), `INTCALC.jcl` (`CBACT04C`), `CREASTMT.JCL` (IDCAMS + `CBSTM03A`) |
| Job/step model | Chunk-oriented reader/processor/writer for record-at-a-time COBOL loops; tasklet for utility steps (IDCAMS define/delete, file copy) | `CBTRN02C` reads `DALYTRAN` sequentially, validates against XREF/ACCT, writes TRANSACT + rejects |
| Job launch seam | CLI: `java -jar batch.jar --spring.batch.job.name=POSTTRAN --run.date=YYYY-MM-DD` (no HTTP trigger) | replaces `//JOB` submit |
| Parameters / control cards | JCL `PARM=` and `SYSIN` control cards → Spring Batch `JobParameters`; PDS control members → resource files under `src/main/resources/ctl` | `INTCALC.jcl` `PARM='2022071800'`; `app/ctl` |
| Restart / checkpoint | Spring Batch `JobRepository` persisted in the same database; chunk commit interval 1000; restartable jobs; a rerun re-executes from the last failed chunk | no mainframe restart logic exists in the COBOL to preserve |
| Rerun semantics | Idempotent steps where the COBOL was (delete-then-define pattern); `JobParameters` include the business run date so a rerun of the same date is explicit | `CREASTMT.JCL` `DELETE`/`DEFINE` prologue |
| Scheduler integration | Out of scope for execution; the Control-M / CA7 definitions (`app/scheduler/CardDemo.controlm`, `CardDemo.ca7`) are **documented as the job dependency graph**, and job exit codes (0 / 4 / 12) are the completion signal | |
| Dataset mapping | VSAM KSDS → tables (same rule as ONLINE); QSAM/PS flat files → tables where a migrated stream owns the data, otherwise files under a configurable `carddemo.batch.data-dir`; GDG `(+1)` generations → date/sequence-suffixed files in that directory; reports (`SYSOUT`, TXT2PDF) → files | `POSTTRAN.jcl` `DALYREJS(+1)`, `TRANREPT`, `CREASTMT` statements (HTML + plain text) |
| Output / reports | Written to the run directory, byte-comparable to the COBOL report layout where the COBOL wrote fixed-format lines | `CBSTM03A` statement generation |
| Observability | Spring Batch step/job metrics logged at step end (read/write/skip counts), mirroring the COBOL display counts | |
| Tests | Per-step unit tests + a `JobLauncherTestUtils` end-to-end job test asserting output rows/files against a seeded fixture | |

---

## 4. SUBTRANSACTION profile (called sub-flows / leaf utilities)

| Field | Proposal | Status |
|---|---|---|
| Exposure | A called COBOL subroutine becomes an internal Spring `@Service` in a shared package (`com.carddemo.common.<name>`), not an endpoint | FACT-adjacent — `CSUTLDTC` (date validation, CALLed by `COTRN02C`) was migrated as `service/DateValidationService.java` |
| Parameter / status mapping | The COBOL return/status field is mapped to a typed result object or a domain exception; the numeric status codes are preserved where a caller branches on them | FACT — `DateValidationService` |
| Idempotency / transaction participation | Sub-services join the caller's transaction; no independent commit | PROPOSED |
| Shared-program rule | A subroutine CALLed by more than one stream (`CSUTLDTC`, `CBSTM03B` I/O module, `COBDATFT`) is ported **once** into the shared package and reused; the module inventory's shared map is authoritative | PROPOSED |

---

## 5. DATA / BOUNDARY profile

| Field | Proposal / value | Status |
|---|---|---|
| Data target | Reference module uses **H2 in-memory** (`jdbc:h2:mem:carddemo`) with `schema.sql` + `data.sql` seeds | FACT — `backend/src/main/resources/application.yml` |
| Data target (D-2, confirmed) | **PostgreSQL** as the real target (docker-compose for local, `spring.profiles.active=postgres`); **H2 profile for CI and tests**. Spring Batch `JobRepository` lives in the same database. | CONFIRMED |
| Persistence style | Spring Data JPA repositories; derived + `@Query` methods; no native SQL except where VSAM browse ordering demands it | FACT — `repository/**` |
| Schema authoring | Hand-written DDL derived field-by-field from the copybook, each column commented with its COBOL picture | FACT — `resources/schema.sql` |
| Migration tooling | **Flyway** (`db/migration/V<n>__<desc>.sql`), applied to both the PostgreSQL and H2 profiles; the reference module's `schema.sql`/`data.sql` are folded into the first migrations | PROPOSED — follows from D-2 |
| Transaction boundary | Service-level `@Transactional`; one unit of work per screen action / per batch chunk | FACT (online) / PROPOSED (batch) |
| Stored procedures | None in the estate; the SP conventions section is **N/A** | FACT (absence) |
| DB2 (add-on module, in scope per D-4) | The DB2 tables behind the Transaction Type module become ordinary PostgreSQL tables derived from the DCLGEN/copybooks; embedded SQL becomes JPA/`@Query` | PROPOSED |
| IMS DB (add-on module, in scope per D-4) | The hierarchical PCB segments are flattened into relational parent/child tables; `GU`/`GN`/`GHU`/`ISRT`/`REPL` DL/I calls become repository operations, with the DL/I status code mapped to a typed result | PROPOSED |
| MQ (add-on module, in scope per D-4) | `MQPUT`/`MQGET` become a messaging seam over **ActiveMQ Artemis** (embedded broker for local/CI) via `JmsTemplate` and `@JmsListener`; queue names carried in configuration, message payloads mapped from the request/response copybooks | PROPOSED |
| Outbound integration seam | N/A for the in-scope surfaces (MQ / IMS / DB2-optional modules are excluded — see below) | PROPOSED |
| Coexistence / strangler routing | None; the migrated app is standalone and seeded from the exported VSAM data (`app/data`, `CBEXPORT`/`CBIMPORT`) | PROPOSED |
| Shared reference data | XREF (`CVACT03Y`), ACCTDAT (`CVACT01Y`), CUSTDAT, CARDDAT (`CVACT02Y`), DISCGRP, TCATBALF are read by several streams — owned by exactly one stream, read-only elsewhere | PROPOSED |

---

## 6. Add-on modules (all IN SCOPE per D-4) and surfaces marked N/A

| Surface / area | Status | Target shape |
|---|---|---|
| DB2 module (`app/app-transaction-type-db2`, CTTU/CTLI) | IN SCOPE | ONLINE profile; DB2 tables → PostgreSQL tables, embedded SQL → JPA |
| IMS DB module (`app/app-authorization-ims-db2-mq`, CPVS/CPVD/CP00) | IN SCOPE | ONLINE profile; IMS segments → relational parent/child tables, DL/I calls → repositories |
| MQ (`app/app-vsam-mq`, CDRD/CDRA + the MQ legs of the authorization module) | IN SCOPE | SUBTRANSACTION profile; MQ queues → Artemis via `JmsTemplate`/`@JmsListener`. **Note:** the legacy MQ/IMS infrastructure itself is not provisioned, so these streams are migrated against the copybook contracts and seeded fixtures rather than a live mainframe comparison |
| Assembler (`MVSWAIT`, `COBDATFT`) | N/A as-is | System-level utilities with no business behaviour to preserve; replaced by JDK equivalents |
| RACF security | N/A | Sign-on is migrated against the USRSEC data, not RACF |

---

## 7. Non-negotiables and defaults

- Non-negotiables stated by the customer: **none recorded yet** — to be captured at STOP A.
- Defaults applied where no opinion exists: everything marked PROPOSED above.

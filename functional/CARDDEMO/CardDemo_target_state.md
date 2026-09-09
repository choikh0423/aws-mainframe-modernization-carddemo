# CardDemo — Target State (CORE + per-surface profiles)

Produced by `!mf_ingest_target_state`. Status: **DRAFT — awaiting STOP A confirmation**.

- **Module**: CARDDEMO (CICS online + JCL batch, credit-card management)
- **SOURCE repo**: `choikh0423/aws-mainframe-modernization-carddemo` @ `main` — `app/cbl`, `app/cpy`, `app/bms`, `app/jcl`, `app/proc`, `app/csd`, `app/scheduler`, `app/asm`
- **Reference (already migrated) module**: `migration/transaction-management/` (CT00/CT01/CT02, merged PRs #32–#36) — the strongest evidence for CORE and ONLINE.
- Every field below is marked **FACT** (cited from source in this repo) or **PROPOSED** (default chosen here, to be confirmed at STOP A).

## Sources per surface

| Surface | Source available | What it is |
|---|---|---|
| CORE | reference repository | `migration/transaction-management/backend` (Spring Boot 3.2.5 / Java 17) |
| ONLINE | reference repository | same module: REST controllers + React 18 SPA |
| BATCH | **none** | no migrated batch job exists; whole profile is PROPOSED |
| SUBTRANSACTION | partial | `DateValidationService` (the Java form of the `CSUTLDTC` CALLed utility) |
| DATA / BOUNDARY | reference repository | `schema.sql` / `data.sql` / JPA repositories, H2 in-memory |

---

## CORE profile (applies to every stream)

| Field | Value | Status |
|---|---|---|
| Language / runtime | Java 17 | FACT — `backend/pom.xml` `<java.version>17</java.version>` |
| Build tool | Maven (Spring Boot parent 3.2.5) | FACT — `backend/pom.xml` |
| Framework | Spring Boot 3.2.5 (`spring-boot-starter-web`, `-data-jpa`, `-validation`) | FACT — `backend/pom.xml` |
| Module layout | one Maven module per migrated stream under `migration/<stream-slug>/backend` (+ `/frontend` when UI-bearing) | FACT — `migration/transaction-management/` |
| Package root | `com.carddemo.<stream>` | FACT — `com.carddemo.transaction` |
| Layering | `controller` → `service` → `repository` → `entity`, plus `dto`, `validator`, `util`, `exception` | FACT — backend `src/main/java` tree |
| DTO / mapping style | explicit request/response DTOs per use case (`TransactionAddRequest`, `TransactionListResponse`); entities never leave the service layer | FACT — `dto/` package |
| COBOL type mapping | `PIC X(n)` → `String` with `length = n`; `9(n)` → `Integer`/`Long`; `S9(9)V99` → `BigDecimal(11,2)`; key fields stay zero-padded strings to preserve VSAM key order; COBOL display formatting (e.g. `+00000013.75`) isolated in `util/CobolFormat` | FACT — `entity/TransactionRecord.java`, `util/CobolFormat.java`, `schema.sql` |
| Error handling | typed exceptions per business error + `@RestControllerAdvice` handler per stream, mapped to `ErrorResponse` carrying the original COBOL screen message text | FACT — `exception/` package |
| Logging / observability | Spring Boot default logging; `show-sql: false`; `/health` endpoint | FACT — `application.yml`, `controller/HealthController.java` |
| Test framework | JUnit 5 + Spring Boot Test + MockMvc; unit tests per service/validator, `@DataJpaTest`-style repository tests, one end-to-end integration test per stream | FACT — `src/test/java` (13 test classes) |
| Repository tests | annotated `@Transactional` to avoid shared-H2 cross-test pollution | FACT — commit `de61191` |
| CI gates | GitHub Actions workflow per stream: `mvn -B test` (backend) and `npm install && CI=false npm run build` (frontend) | FACT — `.github/workflows/transaction-mgmt-ci.yml` |
| Forbidden | Lombok, MapStruct, field injection, entities as API payloads, `Object`/raw maps for COBOL records, silent truncation of `PIC` lengths | PROPOSED (no such usage in the reference module) |
| Naming | Java classes named after the business function, not the COBOL program; each class Javadoc cites its COBOL origin (`COTRN02C`, copybook, line ranges) | FACT — Javadoc in `entity/`, `service/` |

**Drift rules (PR rejected on CORE):** entity exposed by a controller; a COBOL field length not carried into `@Column(length=…)`/DDL; money as `double`/`float`; new dependency outside the Spring Boot BOM; a use case with no test; a business error returned without its COBOL message text.

---

## ONLINE profile (CICS/BMS streams)

| Field | Value | Status |
|---|---|---|
| API style | REST, JSON, `/api/<resource>` — one controller per screen/use case | FACT — `TransactionListController`, `TransactionViewController`, `TransactionAddController` |
| Versioning | none (unversioned `/api/...`) | FACT |
| Conversational state | CICS COMMAREA (`COCOM01Y`) is **not** ported; each screen becomes a stateless request, browse position is passed as explicit paging parameters | FACT — `TransactionListService` paging |
| Screen routing | BMS `XCTL` navigation (`CDEMO-TO-PROGRAM`) becomes React Router routes | FACT — `frontend/src`, `react-router-dom` 6.22.3 |
| Validation | `spring-boot-starter-validation` on DTOs + a per-stream `validator` class reproducing the COBOL field edits in the same order, producing the same message text | FACT — `validator/TransactionValidator.java` |
| Error surfacing | HTTP status + `ErrorResponse.message` = the original 3270 message string | FACT — `exception/` |
| UI framework | React 18 + `react-scripts` 5, CRA dev server proxying `localhost:8080` | FACT — `frontend/package.json` |
| Component library / theming | none — plain components, 3270-like layout | FACT |
| i18n / a11y | none (English only) | FACT — no i18n dependency |
| Frontend tests | `react-scripts test` available; CI gate is the production build | FACT — CI workflow |
| PF-key semantics | PF3/PF7/PF8 mapped to explicit buttons/actions with the same effect (back / page-up / page-down) | FACT — Transaction list pages |

**Drift rules:** a screen field that exists in the BMS map and not in the DTO; message text paraphrased instead of copied; paging that does not reproduce STARTBR/READPREV ordering; server-side session state introduced.

---

## BATCH profile (JCL streams) — **entirely PROPOSED, no reference exists**

| Field | Proposal | Status |
|---|---|---|
| Runtime | Spring Batch on Spring Boot 3.2.5, same Java 17 / Maven CORE | PROPOSED |
| Job/step model | one `Job` per JCL job, one `Step` per `EXEC PGM=` step; chunk-oriented reader/processor/writer, chunk size 1000 | PROPOSED |
| Launch seam | `CommandLineRunner` + `--spring.batch.job.name=<JOB>` and a `POST /api/batch/<job>` trigger for demo/UI use | PROPOSED |
| Parameters / control cards | JCL `PARM=` and `DATEPARM`-style control files become typed `JobParameters`; the parameter record layout keeps its copybook field names | PROPOSED (source: `INTCALC.jcl` `PARM='2022071800'`, `CBTRN03C` `DATEPARM`) |
| Restart / checkpoint | Spring Batch job repository in the same relational DB; steps restartable at chunk boundary; rerun of a completed instance requires a new run date parameter | PROPOSED |
| Scheduler integration | `app/scheduler/CardDemo.controlm` + `CardDemo.ca7` dependencies reproduced as a documented job graph; exit code 0/4/8+ mapped from COBOL `RETURN-CODE`/`CEE3ABD` aborts | PROPOSED (source: `app/scheduler/`) |
| Dataset mapping | VSAM KSDS → tables (shared with the online streams); QSAM/GDG sequential files → files under a configurable `batch.data.dir`; report output → text files, PDF/HTML generation kept as file output | PROPOSED |
| Utility steps | `IDCAMS`/`SORT`/`IEBGENER`/`IEFBR14` steps are **not** ported program-for-program; they become dataset lifecycle/ordering operations of the surrounding Java job, documented per step | PROPOSED |
| Assembler steps | `MVSWAIT` (via `COBSWAIT`) and `COBDATFT` become Java utilities (`Thread.sleep`-based wait, date formatter) | PROPOSED (source: `app/asm/`) |
| Observability | one structured log line per step with read/written/skipped counts; job-level summary matching the COBOL `DISPLAY` totals | PROPOSED |

**Drift rules:** a job step whose COBOL abend condition is not reproduced as a non-zero exit; records written outside a chunk transaction; a GDG generation collapsed to a single overwritten file; totals printed by the COBOL program missing from the Java job log.

---

## SUBTRANSACTION profile (CALLed sub-flows)

| Field | Value | Status |
|---|---|---|
| Exposure | an internal Spring `@Service` in the owning module (not an endpoint) | FACT — `DateValidationService` (the `CSUTLDTC` CALL) |
| Parameter / status mapping | the COBOL parameter copybook becomes the method signature; the return/severity code becomes a typed result or exception | FACT — `DateValidationService`, `CSUTLDPY` |
| Idempotency | pure/side-effect-free utilities only; any stateful sub-flow is escalated as a boundary decision | PROPOSED |
| Transaction participation | joins the caller's transaction (`REQUIRED`) | PROPOSED |
| Sharing across streams | a sub-flow used by ≥2 streams is ported once, by the first stream that needs it, and then imported (see B2 in the boundary register) | PROPOSED |

---

## DATA / BOUNDARY profile

| Field | Value | Status |
|---|---|---|
| Persistence | Spring Data JPA repositories; Hibernate `ddl-auto: none` | FACT — `application.yml` |
| Schema management | hand-authored `schema.sql` + `data.sql` seeded at startup (`spring.sql.init.mode: always`) | FACT |
| Data target | H2 in-memory (`jdbc:h2:mem:carddemo`) | FACT for the reference module; **PROPOSED to keep for CICS+batch, with the batch job repository in the same H2** — needs confirmation, since batch restart semantics are weak on an in-memory DB |
| Unit of work | `@Transactional` at the service layer; batch chunk = transaction | FACT (online) / PROPOSED (batch) |
| Stored procedures | none; the DB2 optional module is out of scope unless STOP B says otherwise | PROPOSED |
| Shared tables | one physical schema for the whole module — `transactions`, `card_xref`, accounts, customers, cards, users, etc. — owned by the first stream that migrates them, imported by later streams | PROPOSED (today `transactions`/`card_xref` live in the transaction-management module) |
| Strangler routing | none — streams are migrated whole; no traffic is split between COBOL and Java | PROPOSED |
| Coexistence with non-migrated writers | while COBOL batch still owns a file that a migrated online stream reads (or the reverse), the register records the direction and the cutover condition | PROPOSED |

**Open question (drives the whole engagement):** the reference module keeps its schema *inside* the stream module. Migrating the entire CICS + batch estate means ≥10 streams sharing the same tables (`ACCTDAT`, `CUSTDAT`, `CARDDAT`, `CCXREF`, `TRANSACT`, `USRSEC`, `TCATBALF`, `DISCGRP`). Two shapes are possible — see the STOP A question list.

---

## Cross-profile reconciliation

- CORE is shared verbatim by ONLINE and BATCH; only the runtime shape differs (web app vs. Spring Batch job).
- COBOL type mapping, error-message fidelity and test conventions are CORE, not per-surface.
- ONLINE keeps H2 by inheritance; BATCH's restart semantics are the one place where that choice is genuinely contested.
- `CSUTLDTC` is already ported inside the transaction-management module but is CALLed by `CORPT00C` too — a B2 shared-program boundary, not a duplicate port.

## Open questions for STOP A

1. **Repository topology** — everything (COBOL source, Java backends, React frontends, docs) currently lives in this one repo. Confirm single-repo, or name separate BACKEND/FRONTEND/DOCS repos.
2. **Module shape** — keep one Maven module *per stream* (as today), or converge on a single `migration/carddemo-online` backend + one `migration/carddemo-batch` backend that all streams contribute to? The second avoids ten copies of the shared schema.
3. **Data target** — stay on in-memory H2 (demo-friendly, weak batch restart), or move to PostgreSQL in a container with H2 kept for tests?
4. **Batch runtime** — Spring Batch (proposed) or plain Spring Boot `CommandLineRunner` jobs?
5. **Scheduler** — reproduce the Control-M/CA7 graph as documentation only, or as an executable orchestration (e.g. a job-graph runner)?
6. **Optional modules** — DB2 transaction types, IMS/DB2/MQ authorizations, VSAM-MQ inquiry: in or out of "the entire CICS and Batch"?

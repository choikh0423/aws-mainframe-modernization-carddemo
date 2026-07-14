# Transaction Management — Migration Plan (Step 2b: plan_stream_migration)

**Stream:** Transaction Management (CT00 / CT01 / CT02)
**Consumes:** `TransactionManagement_analysis.md` (2a) + `TransactionManagement_functional_requirement.md` (2a-FR)
**Topology (confirmed STOP 0):** single repo `choikh0423/aws-mainframe-modernization-carddemo`; SOURCE=`app/`, BACKEND/FRONTEND/DOCS all under `migration/transaction-management/` on branch `devin/1784031395-migrate-transaction-mgmt`.
**Stack (confirmed STOP 0):** Spring Boot + H2 (in-memory) + Spring Data JPA; React (CRA) frontend; **parity mode OFF**.

---

## 1. Target layout
```
migration/transaction-management/
├── docs/            # this + analysis + FR + signoff + audit + recording checklist
├── backend/         # single Spring Boot app serving all 3 screens' APIs
│   └── src/main/java/com/carddemo/transaction/
│       ├── entity/       TransactionRecord, CardXrefRecord
│       ├── repository/   TransactionRepository, CardXrefRepository
│       ├── dto/          per-screen request/response DTOs
│       ├── service/      DateValidationService, list/view/add services
│       ├── validation/   TransactionValidator
│       ├── controller/   TransactionListController, ...ViewController, ...AddController
│       └── resources/    application.yml, schema.sql, data.sql
└── frontend/        # single React app, one page per screen + shared layout
    └── src/pages/   TransactionListPage, TransactionViewPage, AddTransactionPage
```
Rationale: unlike the earlier per-program `migration/cotrn02c/` folder, this stream is delivered as **one backend + one frontend** app covering all three screens (they share the Transaction entity, repository, DTOs, and layout). This matches "migrate the whole online transaction module" and avoids three near-duplicate Spring apps.

## 2. Scaffolding reuse / deltas (per role)
- **Reuse from `demos/cobol-to-java-migration` (`migration/cotrn02c/`)** as the seed for Wave A: `pom.xml` (Spring Boot + web + data-jpa + h2 + validation + test), `application.yml` (H2 in-mem + schema/data init), `schema.sql`/`data.sql`, entity/repository/validator/service/controller patterns, the React app scaffold (`package.json`, `App.js`, pages, `testCases.js`), and the existing `COTRN02CParityTest`-style unit/IT test structure (kept as plain tests since parity mode is OFF).
- **Deltas:** generalize the single-screen app into a 3-screen app; add `TransactionListController` (paging), `TransactionViewController`, keep Add; add `CardXrefRepository.findByAcctId`; add `DateValidationService`; expand `schema.sql`/`data.sql` seed (transactions for paging + card_xref rows for CT02 resolution); React router + list/view pages + shared header/layout.
- **Phase 0 (parity harness / `/parity` Test-UI):** **SKIPPED** — parity mode OFF. No `ParityCatalogLoader`, no `/parity` UI, no confidence audit.

## 3. Phased plan
- **Phase 1 — DB mapping / persistence.** Derive `transactions` and `card_xref` schema from CVTRA05Y / CVACT03Y (analysis §5). H2 in-memory; `schema.sql` + `data.sql` seed. Repositories round-trip. **No stored procedures / no DBA requests** (analysis §7). Delivered as part of Wave A.
- **Phase 2 — program waves (one child session per wave, sequential, leaf-first):**
  | Wave | Program(s) | Deliverable | Repos | STOP 3 gate |
  |------|-----------|-------------|-------|-------------|
  | A | Foundation | entities, repositories, DateValidationService, schema/seed, Spring Boot shell, React shell + layout | BE+FE | `mvn test` green; app boots; `GET /actuator` or a smoke endpoint OK |
  | B | COTRN01C View | `GET /api/transactions/{id}`; View page | BE+FE | `mvn test` green; FR-V1..V7 covered |
  | C | COTRN00C List | `GET /api/transactions?startId&dir` (10/page, PF7/PF8, select); List page | BE+FE | `mvn test` green; FR-L1..L8 covered |
  | D | COTRN02C Add | resolve acct/card, validation, date util, max-key+1, write; Add page (confirm + copy-last) | BE+FE | `mvn test` green; FR-A1..A14 covered |
  Each wave → matching branch names `…-wave-a/-b/-c/-d` off the integration branch; one BACKEND+FRONTEND PR per wave (same app so typically one PR/wave). Merge wave N (green) before starting N+1.
- **Phase 4 — E2E + CI.** End-to-end integration test walking list→select→view and add→list-shows-new-row, asserting API responses **and** persisted H2 rows against the FR acceptance criteria. Add `transaction-mgmt-ci.yml` (mvn test + frontend build) as the regression gate.
- **Phase 5 — hardening + sign-off.** Edge/error-path coverage (not-found, duplicate key, invalid date, empty confirm), concurrency smoke on key-gen, produce `TransactionManagement_migration_signoff.md`.

## 4. Confidence model (parity mode OFF)
Correctness rests on: (a) `mvn test` unit + integration green; (b) every FR traces to a covering test (FR §E matrix); (c) screen recordings proving each screen matches legacy behavior; (d) the independent skeptical audit confirming 0 missing/stubbed in-scope programs. No `/parity` scoreboard, no Coverage-NO-HOLES, no Confidence-Audit gate.

## 5. Sign-off gate (tied to FR acceptance criteria)
Stream is signed off when: all 4 waves merged + green; Phase 4 E2E green in CI; each FR (FR-L*, FR-V*, FR-A*) has a passing test and a recording case (or is flagged for missing test data); independent audit passes.

## 6. Execution operating model
Driven by the COBOL Migration Orchestrator (this parent session). Waves run as **one child session each, sequentially**, handed the confirmed topology, parity mode (OFF), the wave's program(s), COBOL paths, `uiSurface`, the relevant FRs, and the field dictionary. Parent gathers + inspects each wave PR, confirms green, merges, then gates the next wave. STOP 3 is notify-by-default between waves. Final STOP 4 authorizes merge of the integration branch to `main`.

## 7. DB-target decision (pinned)
**H2 in-memory** (matches precedent; confirmed at STOP 0). Not DB2 — avoids Docker/DB2 driver drift for this demo slice. Schema/seed via `schema.sql`/`data.sql`.

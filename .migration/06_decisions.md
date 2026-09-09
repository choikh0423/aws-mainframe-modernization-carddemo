# 06 — Decision log (append-only)

| ID | Date | Decision | Rationale | Decided by |
|---|---|---|---|---|
| D-1 | 2026-09-09 | **One consolidated backend** (`migration/carddemo/backend`, `com.carddemo.<stream>` packages) and **one React app** (`migration/carddemo/frontend`) with the sign-on/menu shell | The legacy app is one CICS region behind one sign-on and menu; per-stream apps would fragment the COMMAREA navigation model | Kyu (STOP A) |
| D-2 | 2026-09-09 | **PostgreSQL** is the real data target; **H2** is the CI/test profile only | Keeps CI hermetic while proving the production dialect locally | Kyu (STOP A) |
| D-3 | 2026-09-09 | **Spring Batch 5**: one `Job` per JCL job, one `Step` per `EXEC PGM=`, chunk-oriented, restartable via `JobRepository` | Preserves the JCL job/step granularity so scheduler dependencies and restart semantics map 1:1 | Kyu (STOP A) |
| D-4 | 2026-09-09 | Scope is **everything**: core CICS + batch **plus** the DB2, IMS and MQ add-on modules | Requested explicitly | Kyu (STOP A) |
| D-5 | 2026-09-09 | STOP D (per-wave review) is **notify only** | Playbook default; keeps wave cadence | Kyu (STOP A) |
| D-6 | 2026-09-09 | The existing `migration/transaction-management/**` implementation is treated as the **reference and the seed of the consolidated app**, folded in rather than forked | Avoids two divergent Spring apps; its 102 passing tests are the regression floor | Devin, recorded at STOP A |
| D-7 | 2026-09-09 | Add-on target shapes: DB2 → PostgreSQL tables + JPA repositories; IMS segments → parent/child relational tables with DL/I calls as repository operations; MQ → ActiveMQ Artemis with `JmsTemplate`/`@JmsListener` | Keeps one data target (D-2) and one runtime; no live IMS/MQ is available to mirror | **Proposed** — pending confirmation at STOP B |
| D-8 | 2026-09-09 | Parity for IMS and MQ paths is proven against copybook contracts and the exported sample data in `app/data`, not against a live mainframe | No mainframe, CICS, DB2, IMS or MQ access exists in this engagement | **Proposed** — pending confirmation at STOP B |

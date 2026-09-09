# 00 — Engagement context

Created by `!mf_migration_setup` on 2026-09-09.

## Module in scope
**CardDemo, the entire estate**: the CICS online application, the batch estate, and the three add-on
modules (DB2 Transaction Type, IMS/DB2/MQ Pending Authorizations, VSAM/MQ inquiry) — confirmed at STOP A
(decision D-4).

## Repository topology

| Role | Repo | Base branch | Paths |
|---|---|---|---|
| SOURCE | `choikh0423/aws-mainframe-modernization-carddemo` | `main` | `app/cbl`, `app/cpy`, `app/cpy-bms`, `app/bms`, `app/jcl`, `app/proc`, `app/ctl`, `app/csd`, `app/scheduler`, `app/data`, `app/asm`, `app/app-transaction-type-db2`, `app/app-authorization-ims-db2-mq`, `app/app-vsam-mq` |
| BACKEND | same repo | `main` | `migration/carddemo/backend` |
| FRONTEND | same repo | `main` | `migration/carddemo/frontend` |
| DOCS | same repo | `main` | `docs/migration/`, `docs/migration/streams/<Stream>/` |

Reference implementation (not forked, folded in): `migration/transaction-management/**` — the
Transaction Management stream migrated under the v1 orchestrator.

## Target state
`docs/migration/CardDemo_target_state.md` (mirrored in `01_target_state.md`). CORE + ONLINE are
FACT-cited from the reference module; BATCH, SUBTRANSACTION and DATA/BOUNDARY were decided at STOP A.

## Artifact contract

| Artifact | Path |
|---|---|
| Target state | `docs/migration/CardDemo_target_state.md` |
| Module inventory | `docs/migration/CardDemo_inventory.md` |
| Stream analysis | `docs/migration/streams/<Stream>/<Stream>_analysis.md` |
| Stream FR | `docs/migration/streams/<Stream>/<Stream>_functional_requirement.md` |
| Stream migration plan | `docs/migration/streams/<Stream>/<Stream>_migration_plan.md` |
| Program FR | `docs/migration/streams/<Stream>/programs/<PROGRAM>_functional_requirement.md` |
| Parity results | `docs/migration/streams/<Stream>/parity/` |
| Verification evidence | `docs/migration/streams/<Stream>/evidence/` |
| Sign-off | `docs/migration/streams/<Stream>/<Stream>_signoff.md` |
| Independent audit | `docs/migration/streams/<Stream>/<Stream>_independent_audit.md` |

Naming: `<Stream>` is the CamelCase stream name from the inventory catalog (e.g. `AccountManagement`);
`<PROGRAM>` is the 8-character COBOL program name (e.g. `COACTVWC`).

## Environments
- Local: Java 17, Maven 3.6.3, Node 20 (via nvm), Docker. PostgreSQL 16 in Docker; H2 in-memory for tests.
- CI: GitHub Actions (`.github/workflows/`).
- **No mainframe, CICS, DB2, IMS or MQ access.** Everything is source-derived; parity is proven against
  the COBOL source and the exported sample data in `app/data`, never against a live region.

## Autonomy defaults (confirmed at STOP A)

| Stop | Behaviour |
|---|---|
| STOP A — target state + workspace | blocking (done 2026-09-09) |
| STOP B — stream choice + process type | blocking |
| STOP C — plan approval | blocking, per stream |
| STOP D — per-wave review | **notify only** (D-5) |
| STOP E — sign-off and merge | blocking, per stream |

## Macro chain
`!mf_ingest_target_state` → `!mf_migration_setup` → `!mf_module_inventory_analysis` → per stream:
`!mf_stream_analysis` → `!mf_stream_fr_generation` → `!mf_stream_migration_plan` →
`!mf_program_fr_generation` → `!mf_program_migration` (one child per wave) → `!mf_program_parity_test` →
`!mf_online_ui_testing` (UI-bearing streams only) → `!mf_stream_signoff`.

## Existing assets registered (cross-check only, never fact)
- `migration/transaction-management/docs/CardDemo-Stream-Map.md` — online stream map from the v1 run.
- `migration/transaction-management/docs/TransactionManagement_{analysis,functional_requirement,migration_plan,migration_signoff,independent_audit}.md`.
- `README.md` "Running Batch Jobs" — job list and purposes.
- `app/app-*/README.md` — add-on module notes.

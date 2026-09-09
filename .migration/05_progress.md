# 05 — Progress ledger

Last updated: 2026-09-09, after all 16 stream PRs merged into `devin/carddemo-integration`.

## Engagement-level

| Step | Macro | Status | Artifact |
|---|---|---|---|
| Target state | `!mf_ingest_target_state` | **DONE** — STOP A confirmed by Kyu 2026-09-09 (D-1…D-5) | `docs/migration/CardDemo_target_state.md` |
| Workspace | `!mf_migration_setup` | **DONE** | `.migration/00…07` |
| Module inventory | `!mf_module_inventory_analysis` | **DONE** — STOP B answered: migrate the entire estate, streams in parallel | `docs/migration/CardDemo_inventory.md` |
| Phase 0/1 foundation | consolidated backend + frontend + full schema | **DONE** — PR #40 | `migration/carddemo/**` |
| Phase 2 streams | 16 parallel stream sessions | **DONE** — PRs #41…#56 merged into `devin/carddemo-integration` | `docs/migration/streams/**` |
| Phase 4 sign-off | `!mf_stream_signoff` + independent audit | IN PROGRESS | — |

## Integration state

Branch `devin/carddemo-integration` (base for every stream PR; `main` untouched).
Backend `mvn test`: **916 tests, 0 failures, 0 errors**. Frontend `npm ci && npm run build`: compiled successfully.
CI (`carddemo-ci.yml`, backend H2 tests + frontend build) green on every merged PR.

## Stream ledger

Coverage: 44 COBOL programs = 42 in streams + 1 shared (CSUTLDTC) + 1 unreachable (CBTRN01C).
Artifacts per stream: `<Stream>_analysis.md`, `<Stream>_functional_requirement.md`, `<Stream>_migration_plan.md`,
`programs/<PROGRAM>_functional_requirement.md` (46 program FR docs total) under `docs/migration/streams/`.

| # | Stream | Type | Programs | Status | PR |
|---|---|---|---|---|---|
| S-01 | AuthAndShell | ONLINE | 3 | MIGRATED | #48 |
| S-02 | AccountManagement | ONLINE | 2 | MIGRATED | #53 |
| S-03 | CardManagement | ONLINE | 3 | MIGRATED (R-1 open: CDV1/COCRDSEC has no source, deferred) | #51 |
| S-04 | TransactionManagement | ONLINE | 3 | MIGRATED under the v1 orchestrator and folded into the consolidated app | #40 |
| S-05 | UserManagement | ONLINE | 4 | MIGRATED | #44 |
| S-06 | BillPayment | ONLINE | 1 | MIGRATED | #43 |
| S-07 | Reporting | ONLINE | 1 | MIGRATED | #45 |
| S-08 | TransactionTypeManagement | ONLINE + BATCH | 3 | MIGRATED (incl. S-19) | #56 |
| S-09 | PendingAuthorizations | ONLINE + BATCH | 8 | MIGRATED (incl. S-20) | #55 |
| S-10 | MQInquiry | SUBTRANSACTION | 2 | MIGRATED | #41 |
| S-11 | DailyTransactionPosting | BATCH | 1 | MIGRATED | #47 |
| S-12 | InterestCalculation | BATCH | 1 | MIGRATED | #49 |
| S-13 | StatementGeneration | BATCH | 2 | MIGRATED | #52 |
| S-14 | TransactionReporting | BATCH | 1 | MIGRATED | #54 |
| S-15 | FileReadUtilities | BATCH | 4 | MIGRATED | #50 |
| S-16 | DataExportImport | BATCH | 2 | MIGRATED | #46 |
| S-17 | OperationsChain | BATCH | 1 | MIGRATED | #42 |
| S-18 | DataLoadAndSetup | BATCH | 0 | REPLACED by Phase-1 schema + seeds | #40 |
| S-19 | TransactionTypeDB2Refresh | BATCH | 0 (COBTUPDT in S-08) | executed with S-08 | #56 |
| S-20 | PendingAuthPurgeAndIMSLoad | BATCH | 0 (in S-09) | executed with S-09 | #55 |

## Boundaries
15 registered (B-01…B-15), all decided by the owning stream's migration plan — implemented or documented
as deferred in that stream's plan and in `04_boundary_register.md`.

## Next action
Independent audit by a session that did no migration work, then **STOP E**: sign-off, evidence and audit
to Kyu, and merge authorization before `devin/carddemo-integration` goes to `main`.

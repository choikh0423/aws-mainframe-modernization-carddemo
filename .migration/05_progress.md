# 05 — Progress ledger

Last updated: 2026-09-09, after `!mf_module_inventory_analysis`.

## Engagement-level

| Step | Macro | Status | Artifact |
|---|---|---|---|
| Target state | `!mf_ingest_target_state` | **DONE** — STOP A confirmed by Kyu 2026-09-09 (D-1…D-5) | `docs/migration/CardDemo_target_state.md` |
| Workspace | `!mf_migration_setup` | **DONE** | `.migration/00…07` |
| Module inventory | `!mf_module_inventory_analysis` | **DONE** — awaiting STOP B (user picks the stream) | `docs/migration/CardDemo_inventory.md` |

## Stream ledger

Coverage: 44 COBOL programs = 42 in streams + 1 shared (CSUTLDTC) + 1 unreachable (CBTRN01C).

| # | Stream | Type | Programs | Status |
|---|---|---|---|---|
| S-01 | AuthAndShell | ONLINE | 3 | NOT STARTED |
| S-02 | AccountManagement | ONLINE | 2 | NOT STARTED |
| S-03 | CardManagement | ONLINE | 3 | NOT STARTED (R-1 open) |
| S-04 | TransactionManagement | ONLINE | 3 | **MIGRATED** under the v1 orchestrator (`migration/transaction-management/**`, 102 tests green); fold-in to the consolidated app outstanding |
| S-05 | UserManagement | ONLINE | 4 | NOT STARTED |
| S-06 | BillPayment | ONLINE | 1 | NOT STARTED |
| S-07 | Reporting | ONLINE | 1 | NOT STARTED |
| S-08 | TransactionTypeManagement | ONLINE + BATCH | 3 | NOT STARTED |
| S-09 | PendingAuthorizations | ONLINE + BATCH | 8 | NOT STARTED |
| S-10 | MQInquiry | SUBTRANSACTION | 2 | NOT STARTED |
| S-11 | DailyTransactionPosting | BATCH | 1 | NOT STARTED |
| S-12 | InterestCalculation | BATCH | 1 | NOT STARTED |
| S-13 | StatementGeneration | BATCH | 2 | NOT STARTED |
| S-14 | TransactionReporting | BATCH | 1 | NOT STARTED |
| S-15 | FileReadUtilities | BATCH | 4 | NOT STARTED |
| S-16 | DataExportImport | BATCH | 2 | NOT STARTED |
| S-17 | OperationsChain | BATCH | 1 | MIGRATED — `docs/migration/streams/OperationsChain/` (B-15 removed, B-04 minimal WAITSTEP job, B-14 orchestration contract) |
| S-18 | DataLoadAndSetup | BATCH | 0 | REPLACED by Phase-1 schema + seeds |
| S-19 | TransactionTypeDB2Refresh | BATCH | 0 (COBTUPDT in S-08) | executes with S-08 |
| S-20 | PendingAuthPurgeAndIMSLoad | BATCH | 0 (in S-09) | executes with S-09 |

## Boundaries
15 registered (B-01…B-15), 0 decided, 0 implemented, 0 deferred. See `04_boundary_register.md`.

## Next action
**STOP B** — the user selects the first stream and confirms its entry point, process type and hard stop.
Nothing downstream starts before that.

# 05 — Progress ledger

Last updated: 2026-09-09, after the independent audit (PR #57) reported on `devin/carddemo-integration`.

## Engagement-level

| Step | Macro | Status | Artifact |
|---|---|---|---|
| Target state | `!mf_ingest_target_state` | **DONE** — STOP A confirmed by Kyu 2026-09-09 (D-1…D-5) | `docs/migration/CardDemo_target_state.md` |
| Workspace | `!mf_migration_setup` | **DONE** | `.migration/00…07` |
| Module inventory | `!mf_module_inventory_analysis` | **DONE** — STOP B answered: migrate the entire estate, streams in parallel | `docs/migration/CardDemo_inventory.md` |
| Phase 0/1 foundation | consolidated backend + frontend + full schema | **DONE** — PR #40 | `migration/carddemo/**` |
| Phase 2 streams | 16 parallel stream sessions | **DONE** — PRs #41…#56 merged into `devin/carddemo-integration` | `docs/migration/streams/**` |
| Phase 4 audit | independent audit by a session that did no migration work | **DONE** — PR #57, verdict NOT fit to merge: 2 BLOCKER, 5 MAJOR, 3 MINOR | `docs/migration/CardDemo_independent_audit.md` |
| Phase 4 remediation | close the audit findings | IN PROGRESS | see the findings table below |
| Phase 4 sign-off | `!mf_stream_signoff` + STOP E | BLOCKED on remediation | — |

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
15 registered (B-01…B-15). Each now carries an explicit `IMPLEMENTED` / `DEFERRED` decision with a date and
the implementing class or deferral rationale, in the 2026-09-09 update table appended to
`04_boundary_register.md` (audit finding A-06 — the rows were previously left at `REGISTERED`).

## Audit findings and remediation

Source: `docs/migration/CardDemo_independent_audit.md` (PR #57).

| ID | Sev | Subject | Owner | Status |
|---|---|---|---|---|
| A-01 | BLOCKER | `auth_fraud.transaction_amt`/`approved_amt` narrowed to `NUMERIC(11,2)` vs `DECIMAL(12,2)` | S-09 session | **CLOSED** — PR #59, `V900__auth_fraud_column_types.sql` |
| A-02 | BLOCKER | `auth_fraud.fraud_rpt_date` stored as `CHAR(8)` `MM/dd/yy`; DB2 column is a `DATE`, century lost | S-09 session | **CLOSED** — PR #59, column is `DATE`, entity `LocalDate`, `MM/dd/yy` moved to presentation |
| A-03 | MAJOR | `pos_entry_mode` `CHAR(2)` vs `SMALLINT` | S-09 session | **CLOSED** — PR #59 |
| A-04 | MAJOR | `EXEC PGM=` count published without derivation | orchestrator | **CLOSED** — inventory §9 reconciliation, D-10 |
| A-05 | MAJOR | S-04 has no stream/program FR documents | orchestrator | **CLOSED** — inventory §10 states where its requirements live |
| A-06 | MAJOR | boundary register left every row at `REGISTERED` | orchestrator | **CLOSED** — decision table appended to `04_boundary_register.md` |
| A-07 | MAJOR | one frontend test, and CI never runs `npm test` | dedicated session | remediation dispatched |
| A-08 | MINOR | `merchant_name` `CHAR(22)` vs `VARCHAR(22)` | S-09 session | **CLOSED** — PR #59 |
| A-09 | MINOR | CBEXPORT/CBIMPORT operator SYSOUT contract neither reproduced nor documented as dropped | S-16 session | **CLOSED** — PR #58, `ExportImportSysout` |
| A-10 | MINOR | `PRTCATBL` `OUTREC`/`LRECL=40` contradiction resolved silently | orchestrator | **CLOSED** — D-9 |

The audit independently reproduced the 916/0/0/0 backend run and the frontend build, and confirmed the
44-program coverage arithmetic. The 916 figure is **backend-only**; there is no frontend test in CI (A-07).

## Next action
PRs #58 and #59 are merged into `devin/carddemo-integration` (both green). Remaining: the frontend
test/CI PR for A-07, then re-run the audit over the fixes, then **STOP E**: sign-off, evidence and audit
to Kyu, and merge authorization before `devin/carddemo-integration` goes to `main`.

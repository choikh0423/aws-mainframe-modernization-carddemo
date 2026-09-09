# 04 — Boundary register (append-only)

Every point where CardDemo execution leaves its own boundary. Rows are **appended** by
`!mf_module_inventory_analysis` (first pass, classification only) and later **decided** by
`!mf_stream_migration_plan` / `!mf_boundary_resolution`. Never rewrite a row; append a dated update.

Schema: `ID | boundary | class | direction | cite | streams affected | required action | status | decided on | decision`

Status values: `REGISTERED` (found, undecided) → `DECIDED` → `IMPLEMENTED` | `DEFERRED`.

| ID | Boundary | Class | Direction | Cite | Streams affected | Required action | Status | Decided on | Decision |
|---|---|---|---|---|---|---|---|---|---|
| B-01 | `CALL 'CEE3ABD'` — LE abend service used by every batch program on an unrecoverable file status | Runtime service | outbound | `app/cbl/CBTRN02C.cbl` and 10 further batch programs | all BATCH streams | Replace with a target-state abend seam (non-zero job exit + logged reason) | REGISTERED | | |
| B-02 | `CALL 'CSUTLDTC'` — shared date-validation subroutine (wraps LE `CEEDAYS`) | Shared subroutine | outbound | `app/cbl/COTRN02C.cbl`, `app/cbl/CORPT00C.cbl` | Transaction Management, Reporting | Port once into `com.carddemo.common`; already migrated as `DateValidationService` in the reference module | REGISTERED | | |
| B-03 | `CALL 'COBDATFT'` — Assembler date-format utility | Assembler utility | outbound | `app/cbl/CBACT01C.cbl`, `app/asm/` | Account/Card batch reads | Replace with JDK date formatting; no business logic to preserve | REGISTERED | | |
| B-04 | `CALL 'MVSWAIT'` — Assembler timer used by the WAITSTEP job | Assembler utility | outbound | `app/cbl/COBSWAIT.cbl`, `app/asm/` | Batch scheduling chains | Replace with a scheduler-level wait; the migrated batch has no equivalent step | REGISTERED | | |
| B-05 | MQ request/response queues — `MQOPEN/MQPUT/MQGET/MQCLOSE` | Message queue | both | `app/app-vsam-mq/cbl/COACCT01.cbl`, `app/app-vsam-mq/cbl/CODATE01.cbl` | MQ Inquiry | Implement the JMS/Artemis seam per the DATA/BOUNDARY profile | REGISTERED | | |
| B-06 | MQ authorization request/response — `MQPUT1`/`MQGET` | Message queue | both | `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl` | Pending Authorizations | Same seam as B-05; request/reply copybooks `CCPAURQY`/`CCPAURLY` are the contract | REGISTERED | | |
| B-07 | IMS DL/I calls — `CALL 'CBLTDLI'` against `DBPAUTP0`/`DBPAUTX0` | Hierarchical DB | outbound | `app/app-authorization-ims-db2-mq/cbl/{PAUDBLOD,PAUDBUNL,DBUNLDGS}.CBL` | Pending Authorizations | Flatten segments to relational tables; DL/I calls become repositories, status codes become typed results | REGISTERED | | |
| B-08 | DB2 embedded SQL — transaction type/category tables | Relational DB | outbound | `app/app-transaction-type-db2/cbl/{COTRTLIC,COTRTUPC,COBTUPDT}.cbl`, DCLGEN in `dcl/` | Transaction Type Management | Map DCLGEN to JPA entities on the same PostgreSQL target | REGISTERED | | |
| B-09 | DB2 fraud/authorization tables `AUTHFRDS` | Relational DB | outbound | `app/app-authorization-ims-db2-mq/ddl/AUTHFRDS.ddl`, `dcl/AUTHFRDS.dcl` | Pending Authorizations | Same as B-08 | REGISTERED | | |
| B-10 | `EXEC CICS LINK PROGRAM(WS-PGM-AUTH-FRAUD)` — dynamic link to a fraud-scoring program | Cross-module call | outbound | `app/app-authorization-ims-db2-mq/cbl/COPAUS1C.cbl` | Pending Authorizations | Resolve the literal MOVEd into `WS-PGM-AUTH-FRAUD`; if the program is absent from the repo this is a HIGH scope risk | REGISTERED | | |
| B-11 | `COCRDSEC` — program named by CSD transaction `CDV1` but **absent from the repo** | Absent module | outbound | `app/csd/CARDDEMO.CSD:388` | Card Management (security view) | Confirm with the customer whether CDV1 is in scope; cannot be migrated from source that does not exist | REGISTERED | | |
| B-12 | Dataset hand-offs to/from other applications: `CBEXPORT`/`CBIMPORT` branch-record transfer files and the FTP step | Dataset hand-off | both | `app/cbl/CBEXPORT.cbl`, `app/cbl/CBIMPORT.cbl`, `app/jcl/FTPJCL.JCL` | Data Export/Import | Decide file location and transfer mechanism in the target (object storage vs shared dir) | REGISTERED | | |
| B-13 | TXT2PDF / IKJEFT1B statement rendering step | External utility | outbound | `app/jcl/TXT2PDF1.JCL` | Statement Generation | Replace with a target-side PDF/HTML renderer or drop if the HTML statement suffices | REGISTERED | | |
| B-14 | Scheduler (Control-M / CA-7) job dependency graph and completion signalling | Scheduler | inbound | `app/scheduler/CardDemo.controlm`, `app/scheduler/CardDemo.ca7` | all BATCH streams | Document the dependency graph; the migrated jobs expose exit codes only (no scheduler integration built) | REGISTERED | | |
| B-15 | CICS file open/close jobs (`OPENFIL`/`CLOSEFIL` via SDSF) that quiesce VSAM for batch | Runtime coupling | both | `app/jcl/OPENFIL.jcl`, `app/jcl/CLOSEFIL.jcl` | all BATCH streams | Disappears in the target (shared database, no file quiescing); document as removed, not migrated | REGISTERED | | |

## Update — 2026-09-09, post-implementation decisions

Appended after all 16 stream PRs merged into `devin/carddemo-integration`, in response to
independent-audit finding **A-06** (every row above still read `REGISTERED` while `05_progress.md`
asserted the boundaries were closed). Rows above are left untouched; this table is the decision of
record for each.

| ID | Status | Decided on | Decision |
|---|---|---|---|
| B-01 | IMPLEMENTED | 2026-09-09 | `com.carddemo.common.batch.AbendService` / `AbendException` / `AbendData` raise a logged, coded abend that fails the step and the job with a non-zero exit; stream-local wrappers `batch.filereads.FileReadAbend`, `batch.statement.StatementAbend` and `trantype.exception.TranTypeAbendException` carry the legacy abend codes and text. |
| B-02 | IMPLEMENTED | 2026-09-09 | Ported once, for the whole module, as `com.carddemo.common.service.DateValidationService`; the Reporting stream FR/analysis/plan document its contract. No stream re-ported it. |
| B-03 | IMPLEMENTED | 2026-09-09 | `com.carddemo.batch.filereads.LegacyDateFormatter` reproduces the Assembler date formatting with an explicit written contract; no business logic was carried over. |
| B-04 | DEFERRED | 2026-09-09 | No wait step exists in the target. `com.carddemo.batch.operations.WaitStepJobConfiguration` / `WaitControlCard` preserve the control-card contract so the scheduler chain stays readable, but the timer itself is a scheduler concern (see B-14) and is not migrated. |
| B-05 | IMPLEMENTED | 2026-09-09 | `com.carddemo.mqinquiry.jms` (`AccountInquiryListener`, `DateInquiryListener`, `MqInquiryReplySender`, `MqInquiryQueues`) over the Artemis/JMS seam in `com.carddemo.common.jms`. Account and date requests use separate queues — with one queue, two listeners would steal each other's requests. |
| B-06 | IMPLEMENTED | 2026-09-09 | `com.carddemo.pendingauth.jms` (`AuthorizationRequestListener`, `PendingAuthQueues`) on the same seam; `CCPAURQY`/`CCPAURLY` are the wire contract. |
| B-07 | IMPLEMENTED | 2026-09-09 | `PAUTSUM0`/`PAUTDTL1` flattened to `pending_auth_summary` / `pending_auth_detail` with an explicit foreign key; DL/I calls became Spring Data repositories and DL/I status codes typed results. |
| B-08 | IMPLEMENTED | 2026-09-09 | DCLGEN mapped to JPA entities on the shared PostgreSQL target (`db2_transaction_type`, `db2_transaction_type_category`). |
| B-09 | IMPLEMENTED | 2026-09-09 | `auth_fraud` table plus `com.carddemo.pendingauth` repositories. **Audit findings A-01/A-02/A-03/A-08 corrected the column types against `AUTHFRDS.ddl`/DCLGEN**; this row is closed only once those fixes are merged. |
| B-10 | IMPLEMENTED | 2026-09-09 | The literal MOVEd into `WS-PGM-AUTH-FRAUD` resolves to `COPAUS2C`, which is present in the repo and migrated; the dynamic link became a direct service call. Not a scope risk. |
| B-11 | DEFERRED | 2026-09-09 | `COCRDSEC` has no source anywhere in the repository, so CDV1's security view cannot be migrated from source. Documented as inventory risk R-1 and raised with the customer; unresolved at sign-off. |
| B-12 | IMPLEMENTED | 2026-09-09 | Export/import files land in a configured directory (`carddemo.batch.export-import-dir`), read/written by `com.carddemo.batch.exportimport`. FTP transport itself is out of scope: the target exposes the directory, the transfer mechanism is an operations choice. |
| B-13 | DEFERRED | 2026-09-09 | `TXT2PDF` is a licensed TSO/REXX load library (`AWS.M2.LBD.TXT2PDF.LOAD`) that is not in the repository, so there is nothing to migrate from. The statement is produced as text and HTML; PDF rendering is left to a target-side renderer. Rationale in `docs/migration/streams/StatementGeneration/StatementGeneration_analysis.md`. |
| B-14 | DEFERRED | 2026-09-09 | The Control-M/CA-7 dependency graph is documented in the inventory; migrated jobs expose exit codes only. No scheduler integration was built — the target scheduler is a customer decision. |
| B-15 | IMPLEMENTED (as removed) | 2026-09-09 | Disappears against a shared relational target: with no VSAM to quiesce, `OPENFIL`/`CLOSEFIL` have no counterpart. Recorded as removed, not migrated. |

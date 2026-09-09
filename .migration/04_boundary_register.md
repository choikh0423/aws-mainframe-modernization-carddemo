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

# CardDemo modernization plan (driven by the mftb store)

**How to read this.** Every number and fact below comes from a file under `mftb-demo/store/` or from a source line (`path:line`). Facts the deterministic scanner produced are labelled **[scan]**. Conclusions Devin wrote from cited source are labelled **[agent]**. Target-state proposals are labelled **[recommendation]**: they are design choices for the customer to accept or change, not facts. Anything the store cannot prove is listed as an open evidence request (§8), never guessed.

Toolbox: `COG-GTM/devin-mainframe-cobol-toolbox` @ `419573efe3a9a4fe742e2ad62d8085ff04e71a8c`.

---

## 1. Scope and complexity per module

The estate sweep catalogued 240 files under `app/`, 203 of them placeable, all placed: 4 modules, 8 shared copybooks, 0 unplaced **[scan]** (`store/estate.json` `summary`; `store/modules.json`, checked with `mftb modules --check`).

| Module | What it is **[agent]** | Programs | Copybooks | JCL jobs | CICS TRANIDs | Data stores **[scan]** | Open findings (high / medium / info) **[scan]** |
|---|---|---|---|---|---|---|---|
| `CARDDEMO` | Core card back office: CICS screens + nightly/monthly batch over VSAM | 31 | 48 | 38 (101 steps) | 18 | 15 VSAM clusters, 33 sequential datasets | 4 / 174 / 385 |
| `CDAUTHFR` | Pending-authorization extension: IMS DB + DB2 + MQ | 8 | 42 | 5 | 3 (`CP00`, `CPVS`, `CPVD`) | IMS (3 PSBs), DB2 `CARDDEMO.AUTHFRDS`, MQ | 4 / 33 / 24 |
| `CDTRNTYP` | Transaction-type maintenance on DB2 | 3 | 36 | 3 | 2 (`CTLI`, `CTTU`) | DB2 `TRANSACTION_TYPE`, `TRANSACTION_TYPE_CATEGORY` | 4 / 25 / 8 |
| `CDVSAMMQ` | MQ request/reply account and date inquiry | 2 | 30 | 0 | 2 (`CDRA`, `CDRD`) | MQ | 4 / 26 / 5 |
| **Estate** | | **44** | **156** | **46** | **25** | | **16 / 258 / 422** |

Sources: program/copybook counts by `kind` in `store/<M>/programs.json`; jobs in `store/<M>/jobs.json`; data stores in `store/<M>/connections.json` `summary`, `db2.tables`, `ims`; findings in `store/<M>/findings.json`. TRANIDs: `store/CARDDEMO/online.json` (18 defined, 17 pseudo-conversational), `app/app-authorization-ims-db2-mq/csd/CRDDEMO2.csd:39,49,59`, `app/app-transaction-type-db2/csd/CRDDEMOD.csd:25,35`, `app/app-vsam-mq/csd/CRDDEMOM.csd:17,27`. Scheduler tables: 6 (`store/CARDDEMO/schedule.json`, from `app/scheduler/CardDemo.ca7` and `app/scheduler/CardDemo.controlm`).

**Complexity reading [agent].** `CARDDEMO` holds 70% of the programs and 83% of the jobs and is the hub every other module depends on (§5). `CDAUTHFR` is small but the hardest per line: it is the only module with IMS DL/I (7 programs, `store/CDAUTHFR/connections.json` `ims.programs`), and it adds DB2 and MQ. `CDVSAMMQ` is the simplest (2 programs, no batch).

The 4 high findings per module are the same profile gaps everywhere — `environment_topology`, `masking_policy`, `test_lpar`, and (extensions) `shared_code_boundary` — plus one code-level high in `CARDDEMO`: `CBIMPORT` opens `CARD-OUTPUT` through DD `CARDOUT` that its step does not allocate (`app/cbl/CBIMPORT.cbl:63`, `app/jcl/CBIMPORT.jcl:22`) **[scan]**.

## 2. Data stores and what they become

| Store today **[scan]** | Evidence | Who touches it | Target **[recommendation]** |
|---|---|---|---|
| VSAM KSDS `ACCTDATA` (accounts) | `app/csd/CARDDEMO.CSD:1`; `store/CARDDEMO/connections.json` | CICS read/rewrite (`app/cbl/COACTUPC.cbl:4065`, `app/cbl/COBIL00C.cbl:379`); batch `POSTTRAN`, `INTCALC` | Relational table `account`, key = account id |
| VSAM KSDS `CARDDATA` + AIX/path by account | `app/csd/CARDDEMO.CSD:13,25` | `COCRDLIC`, `COCRDSLC`, `COCRDUPC` (`app/cbl/COCRDUPC.cbl:1477`) | Table `card` with secondary index on account id |
| VSAM KSDS `CARDXREF` + AIX `CXACAIX` | `app/csd/CARDDEMO.CSD:37,63` | online lookups (`app/cbl/COTRN02C.cbl:578,611`); batch `CBTRN02C`, `CBACT04C`, `CBSTM03A` | Fold into `card` (card→account is 1:1 in the record) or a `card_account` table — confirm cardinality (§8) |
| VSAM KSDS `CUSTDATA` | `app/csd/CARDDEMO.CSD:50` | `COACTUPC` rewrite (`app/cbl/COACTUPC.cbl:4085`), `COACTVWC` | Table `customer` |
| VSAM KSDS `TRANSACT` + AIX on processed timestamp | `app/csd/CARDDEMO.CSD:76`; `app/jcl/TRANIDX.jcl` | online browse/write (`app/cbl/COTRN02C.cbl:713`, `app/cbl/COBIL00C.cbl:512`); batch post/merge/backup | Append-only `transaction` table, indexed by processed timestamp |
| VSAM KSDS `TCATBALF` (category balances) | `store/CARDDEMO/chains.json` (POSTTRAN writes, INTCALC reads) | `CBTRN02C`, `CBACT04C` | Table `account_category_balance` |
| VSAM KSDS `DISCGRP`, `TRANTYPE`, `TRANCATG` (reference) | `app/jcl/DISCGRP.jcl`, `app/jcl/TRANTYPE.jcl`, `app/jcl/TRANCATG.jcl` | batch lookups (`CBACT04C`, `CBTRN03C`) | Reference tables; `TRANTYPE`/`TRANCATG` merge with the DB2 tables of `CDTRNTYP` (one source of truth) |
| VSAM KSDS `USRSEC` (users, passwords) | `app/csd/CARDDEMO.CSD:88` | `COSGN00C` (`app/cbl/COSGN00C.cbl:211`), `COUSR0xC` | Do **not** migrate as a table: replace with the customer's identity provider; passwords are compared in clear in `COSGN00C` (`app/cbl/COSGN00C.cbl:223`) |
| DB2 `CARDDEMO.TRANSACTION_TYPE`, `TRANSACTION_TYPE_CATEGORY` | `app/app-transaction-type-db2/ddl/TRNTYPE.ddl:1`, `TRNTYCAT.ddl:1` | `CDTRNTYP` programs (27 SQL statements) | Same tables on the target RDBMS (schema-compatible lift) |
| DB2 `CARDDEMO.AUTHFRDS` (fraud flags) | `app/app-authorization-ims-db2-mq/ddl/AUTHFRDS.ddl:1` | `CDAUTHFR` | Same table on the target RDBMS |
| IMS DB pending-authorization database (PSBs `PSBPAUTB`, `PAUTBUNL`, `DLIGSAMP`) | `store/CDAUTHFR/connections.json` `ims` | 7 DL/I programs incl. `COPAUS0C`, `CBPAUP0C` | Parent/child relational tables (authorization summary → detail); DBD source must be supplied (§8) |
| MQ request/reply queues | `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl:262`; `app/app-vsam-mq/cbl/COACCT01.cbl:233` | `COPAUA0C`, `COACCT01`, `CODATE01` | Managed message broker or synchronous API; queue names are run-time values and must be supplied (§8) |
| Sequential/GDG files (33 in `CARDDEMO`), e.g. `DALYTRAN.PS`, `DALYREJS(+1)`, statements | `store/CARDDEMO/connections.json` `datasets` | batch | Object-storage files with retention matching the GDG limits (e.g. `LIMIT(5)` in `app/jcl/DEFGDGD.jcl`) |

**Constraint carried into the design [agent]:** every CICS file in `CARDDEMO.CSD` is `RECOVERY(NONE)` (`app/csd/CARDDEMO.CSD:9-10`), and `COACTUPC` rewrites account then customer in one task (`app/cbl/COACTUPC.cbl:4065-4090`; `store/CARDDEMO/onlinenotes.json`). Today a failure between the two rewrites is not backed out; the target should make that update one database transaction.

## 3. Batch chains → target jobs

Six scheduler tables are in the store **[scan]**: CA 7 `CARDDEMO` and Control-M `DAILY-TransactionBackup`, `WEEKLY-TransactionTypesDBRefresh`, `DisclosureGroupsRefresh`, `TransactionTypesDBRefresh`, `MONTHLY-InterestCalculation` (`store/CARDDEMO/schedule.json`). Two were analysed with `batch-chain-analysis`:

**CA 7 `CARDDEMO` — the daily posting cycle (selected as primary).** 17 jobs, 29 steps, 4 lineage edges, 28 open asks, 3 closed by the skill (`store/CARDDEMO/chains.json`). Why this one **[agent]**: it is the table that runs `POSTTRAN` (`app/scheduler/CardDemo.ca7:70`), and `POSTTRAN` runs `CBTRN02C` (`app/jcl/POSTTRAN.jcl:23`), the only program that validates and posts customer transactions to accounts and balances (reject codes 100–103 and the TRANSACT/ACCTDAT/TCATBALF writes, `app/cbl/CBTRN02C.cbl:370-580`). It also produces customer statements (`CREASTMT`, `app/scheduler/CardDemo.ca7:468`, `app/jcl/CREASTMT.JCL:19,79`). Report: `store/reports/CARDDEMO/chains/chain-CARDDEMO.html`.

**Control-M `MONTHLY-InterestCalculation` (secondary).** 5 jobs, 6 steps, 1 lineage edge, 8 open asks, 2 closed (`store/CARDDEMO/chains.json`). `INTCALC` → `CBACT04C` computes interest `TRAN-CAT-BAL * DIS-INT-RATE / 1200` (`app/jcl/INTCALC.jcl:22`, `app/cbl/CBACT04C.cbl:462-468`), then `COMBTRAN` merges the interest transactions (`app/scheduler/CardDemo.controlm:69,75`). Report: `store/reports/CARDDEMO/chains/chain-MONTHLY-InterestCalculation.html`.

| Today **[scan]** | Target **[recommendation]** |
|---|---|
| CA 7 / Control-M tables, success-path `OUT=`→`IN=` edges | One workflow per table in the target scheduler/orchestrator, same edges; the store's job order is the acceptance spec |
| `CLOSEFIL` / `OPENFIL` bracket around every update window (CEMT file close/open, `app/jcl/CLOSEFIL.jcl:26-30`, `app/jcl/OPENFIL.jcl:26-30`) | Not needed once online and batch share a transactional database; replace with row-level locking, or keep a maintenance window if the customer wants batch isolation (§8) |
| `POSTTRAN` / `CBTRN02C` (validate + post) | Batch posting service, re-runnable per input file, rejects to a reject table |
| `INTCALC` / `CBACT04C` with hard-coded `PARM='2022071800'` (`app/jcl/INTCALC.jcl:22`) | Interest job taking the business date from the scheduler; `1400-COMPUTE-FEES` is an unimplemented stub (`app/cbl/CBACT04C.cbl:518-520`) — migrate as-is or implement fees is a business decision (§8) |
| `COMBTRAN` SORT + REPRO | A set-based insert into `transaction` (no sort/reload) |
| `CREASTMT` → `CBSTM03A`/`CBSTM03B` (text + HTML statements); `TXT2PDF1` REXX | Statement renderer service; the REXX exec is outside the repo (§8) |
| IDCAMS/IEBGENER/IEFBR14 rebuild jobs (`ACCTFILE`, `CARDFILE`, `DEFGDGB`, …) | Retired: schema migrations and seed scripts replace delete/define/REPRO |
| `TRANBKP` (backup to GDG) | Database backup/PITR policy |

## 4. CICS transactions → target services and screens

`scan-online` derived 2 entry points (`CC00` sign-on, `CDV1`), 2 menus (`CM00` user, `CA00` admin) and 14 screens reached from them; 7 datasets cross the online/batch boundary and `CORPT00C` submits the `TRANREPT` job through TDQ `JOBS` (`store/CARDDEMO/online.json` `summary`; `app/cbl/CORPT00C.cbl:517`; `app/csd/CARDDEMO.CSD:499`) **[scan]**.

Selected transaction: **`CAUP` — account update** (`COACTUPC`). Why **[agent]**: it is the only core screen that rewrites two masters (account and customer) in one task, so it carries the most business risk. Reached from the main menu (`app/cbl/COMEN01C.cbl:156`), it reads `CXACAIX`, `ACCTDAT`, `CUSTDAT` and rewrites `ACCTDAT` and `CUSTDAT` (`store/CARDDEMO/online.json`). Screen flow is data-driven (AID key and change state, `app/cbl/COACTUPC.cbl:921-1004`; `store/CARDDEMO/onlinenotes.json`). Report: `store/reports/CARDDEMO/online/online-CAUP.html`. Its 23 asks: 18 `online-map-no-source` (info), 4 `online-transid-variable` (medium), 1 `online-screen-flow-unproven` (info).

| Transaction group **[scan]** | Programs | Target **[recommendation]** |
|---|---|---|
| Sign-on `CC00` | `COSGN00C` | Identity-provider login; user type (admin/regular) → role claim (`app/cbl/COSGN00C.cbl:232-237`) |
| Menus `CM00`, `CA00` | `COMEN01C`, `COADM01C` (option tables `app/cpy/COMEN02Y.cpy`, `app/cpy/COADM02Y.cpy`) | Web navigation generated from the same option tables; role-filtered |
| Accounts `CAVW`, `CAUP` | `COACTVWC`, `COACTUPC` | `GET/PUT /accounts/{id}` (account + customer in one transaction) + account screen |
| Cards `CCLI`, `CCDL`, `CCUP` | `COCRDLIC`, `COCRDSLC`, `COCRDUPC` | `GET /cards?account=`, `GET/PUT /cards/{id}` |
| Transactions `CT00`, `CT01`, `CT02` | `COTRN00C`, `COTRN01C`, `COTRN02C` | Paged `GET /transactions`, `GET /transactions/{id}`, `POST /transactions` |
| Bill pay `CB00` | `COBIL00C` | `POST /accounts/{id}/payments` |
| Users `CU00`–`CU03` | `COUSR00C`–`COUSR03C` | Identity-provider admin, not an application table |
| Report `CR00` | `CORPT00C` → `TRANREPT` | API call that starts the report job in the orchestrator (replaces in-line JCL to the internal reader) |

The XCTL-to-caller pattern (`CDEMO-TO-PROGRAM` / `CDEMO-FROM-PROGRAM` in `COCOM01Y`) becomes front-end routing; the COMMAREA becomes request/session state. The 18 `layout-mismatch` dynamic-call sites (§6) are this pattern.

## 5. Migration waves and dependency order

Dependencies proven in the store **[scan]** (`store/estate.json` `connections`, 24 edges):
- `CDAUTHFR` → `CARDDEMO`: copies 6 shared copybooks; `COPAUS0C` may XCTL to `COMEN01C`/`COSGN00C` (`app/app-authorization-ims-db2-mq/cbl/COPAUS0C.cbl:322`); `COMEN01C` may XCTL to `COPAUS0C` (`app/cbl/COMEN01C.cbl:156`); CA 7 `CARDDEMO` runs `CBPAUP0J` (`app/scheduler/CardDemo.ca7:43`).
- `CDTRNTYP` → `CARDDEMO`: copies 5 shared copybooks; `COTRTLIC` may XCTL to `COADM01C` (`app/app-transaction-type-db2/cbl/COTRTLIC.cbl:620`); Control-M `TRANEXTR` / `MNTTRDB2` refresh the type data (`app/scheduler/CardDemo.controlm:27-31,58-62`).
- `CDVSAMMQ` → `CARDDEMO`: shares only `CVACT01Y` (account record layout).
- Inside `CARDDEMO`: batch and online share the VSAM masters, serialised by `CLOSEFIL`/`OPENFIL` (`store/CARDDEMO/chainnotes.json`); `POSTTRAN` writes `TCATBALF`, which `INTCALC` reads (`store/CARDDEMO/chains.json` lineage).

Proposed waves **[recommendation]**, ordered so that nothing migrates before what it reads:

| Wave | Content | Why here (graph evidence) | Exit criteria |
|---|---|---|---|
| 0 — Evidence & contracts | Answer §8; freeze the 8 shared copybooks (`COCOM01Y`, `CSDAT01Y`, `CSUSR01Y`, `CVACT01Y`, `CVACT02Y`, `CVACT03Y`, `CVCRD01Y`, `CVCUS01Y`) as versioned data contracts | Every cross-module edge goes through these (`store/modules.json` `shared`) | Profile has no ASSUMED fields; contracts generated from copybooks |
| 1 — Core data | 15 `CARDDEMO` VSAM clusters → relational schema; one-time load via `CBEXPORT` layouts; merge `TRANTYPE`/`TRANCATG` with `CDTRNTYP` DB2 tables | Both batch and online read/write these; nothing else can move first | Record-count + checksum parity per file against the VSAM unload |
| 2 — Core batch | CA 7 `CARDDEMO` cycle (`POSTTRAN`, `CREASTMT`, reports) and `MONTHLY-InterestCalculation` | Batch writes the balances online reads; lineage `POSTTRAN`→`INTCALC` via `TCATBALF` | Parallel run: posted balances, rejects, interest and statements match the mainframe for the same input day |
| 3 — Core online | 18 `CARDDEMO` transactions as API + web screens, sign-on/menu first, then accounts/cards/transactions/bill pay | Depends on wave-1 data; `CC00`→`CM00`/`CA00` dispatch every other screen (`store/CARDDEMO/online.json` `entry_points`) | Screen-by-screen functional parity; `CAUP` update atomic |
| 4a — `CDTRNTYP` | DB2 type maintenance screens `CTLI`, `CTTU` | Already relational; depends only on admin menu + shared copybooks | |
| 4b — `CDVSAMMQ` | MQ account/date inquiry → API | One shared copybook, no batch | |
| 4c — `CDAUTHFR` | IMS authorization DB → relational; MQ authorization request → API/broker; DB2 fraud table | Most technology per line (IMS + DB2 + MQ); depends on core menu, sign-on and account data | Authorization decisions match on replayed MQ traffic |

## 6. Risk register (from `findings.json`)

**High [scan]**
| # | Finding | Evidence | Mitigation **[recommendation]** |
|---|---|---|---|
| H1 | `CBIMPORT` opens DD `CARDOUT` its step does not allocate | `app/cbl/CBIMPORT.cbl:63`, `app/jcl/CBIMPORT.jcl:22` | Confirm whether card import is used in production before relying on it for the wave-1 load |
| H2 | `environment_topology` unknown (all 4 modules) | `store/*/profile.json` | Needed to size target environments |
| H3 | `masking_policy` unknown (all 4) | `store/*/profile.json` | Blocks moving any production data off-platform for parallel runs |
| H4 | `test_lpar` unknown (all 4) | `store/*/profile.json` | Blocks parallel-run plan |
| H5 | `shared_code_boundary` unknown (3 extensions) | `store/CDAUTHFR/profile.json` etc. | Confirm the 8 shared copybooks are owned by the core |

**Medium — largest groups in `CARDDEMO` [scan]** (`store/CARDDEMO/findings.json`): `online-transid-variable` 68, `dynamic-call-open` 24, `unscheduled-job` 19, `online-commarea-unproven` 11, `chain-input-unproduced` 6, `file-dd-unbound` 6 (all `CBTRN01C`, which no scanned job runs — `app/cbl/CBTRN01C.cbl:29-58`), `chain-step-opaque` 4, `cics-resource-variable` 4, `external-control-cards` 3 (`app/proc/REPROC.prc:21`), `module-outside-scan` 2 (`COBDATFT`, `MVSWAIT` — HLASM, `app/asm/COBDATFT.asm:17`, `app/asm/MVSWAIT.asm:17`). Extensions: `cics-file-undefined` (`CDAUTHFR` 4, `CDVSAMMQ` 1), `ims-psb-unknown` 3, `mq-usage` (queue names set at run time).

**Unresolved dynamic calls [scan + agent]** — `store/CARDDEMO/dyncalls.json`: 24 sites, 3 resolved by literal, 21 partial, 0 open; with Devin's `annotations.json` applied the module report shows 6 resolved, 18 partial. The annotations narrow the 3 menu-table sites (`COMEN01C.cbl:156`, `COADM01C.cbl:145,168`) to the option table bounded by `CDEMO-MENU-OPT-COUNT` / `CDEMO-ADMIN-OPT-COUNT` (`app/cpy/COMEN02Y.cpy`, `app/cpy/COADM02Y.cpy`); the 18 `layout-mismatch` XCTL-to-`CDEMO-TO-PROGRAM` sites (e.g. `app/cbl/COACTUPC.cbl:956`, `app/cbl/COTRN00C.cbl:192`) stay partial: the target depends on the COMMAREA the caller passed, which is a run-time value.

**Missing artifacts [scan]**: HLASM `COBDATFT`, `MVSWAIT` (catalogued, not analysed); `AWS.M2.CARDDEMO.CNTL(REPROCT)` control cards; `AWS.M2.LBD.TXT2PDF.EXEC` REXX; the producer of `DALYTRAN.PS`; BMS macro source for the 18 mapsets `CAUP` uses (`online-map-no-source`); IMS DBD source for `CDAUTHFR`; EBCDIC data files are catalogued but untranslated (`store/catalog.json`).

**ASSUMED profile fields [scan]** — filled from the toolbox default, nobody confirmed (`store/CARDDEMO/profile.json`, basis "no evidence and no answer: mftb's typical-z/OS-shop default"): `database_managers` (Db2 12), `library_manager` (Endevor), `output_manager` (CA View), `security_manager` (RACF), `sort_product` (DFSORT), `standard_compiler` (Enterprise COBOL 6.3), `standard_compiler_options`, `tape_manager` (CA 1), `zos_version` (z/OS 2.5). 10 more fields are unanswered (e.g. `compiler`, `db2_subsystem`, `volumes`). Profile summary: 29 fields, 10 detected, 9 defaulted, 10 unanswered, 21 asks.

## 7. What the scanner did vs what Devin did

| Deterministic scanner (`python3 -m mftb …`) | Devin (skills, agentic) |
|---|---|
| Catalogued 240 files, typed them, found duplicates (`catalog.json`) | Proposed the 4-module split and the shared set (`modules.json`), checked by `modules --check` |
| Parsed COBOL/JCL/PROC/CSD/scheduler; built calls, datasets, lineage, traces; filed every unresolved item as a finding | Wrote 71 descriptions + 7 business summaries from source, each cited (`descriptions.json`, `--check --strict` passes) |
| Detected/defaulted 29 profile fields, flagged defaults as ASSUMED | Kept every default as an evidence request — did not answer for the customer |
| Traced 24 dynamic-call sites (3 resolved) | Narrowed menu-table sites with cited reasoning (`annotations.json`); left run-time ones partial |
| Built the two chains and the online closure, filed asks | Closed 5 chain asks across the two chains (3 + 2) with cited facts, added the `DALYTRAN` producer ask and 4 notes (`chainnotes.json`); wrote `CAUP` screen-flow and atomicity notes (`onlinenotes.json`) |
| Rendered all HTML reports | Wrote this plan: every target is a labelled recommendation |

## 8. Evidence requests the customer must answer

1. **Environment**: LPARs/environments (prod/test/DR), test LPAR availability, masking policy (H2–H4).
2. **Profile**: confirm or correct the 9 ASSUMED fields in §6 and the 10 unanswered ones.
3. **`DALYTRAN.PS`**: which system/job/transfer delivers the daily transaction file, arrival time, late/empty handling (`store/CARDDEMO/chainnotes.json`; `app/jcl/POSTTRAN.jcl:23-30`).
4. **Missing source**: HLASM `COBDATFT`/`MVSWAIT` behaviour, `CNTL(REPROCT)`, `TXT2PDF` REXX, BMS mapset source, IMS DBDs.
5. **`CBIMPORT` / `CBTRN01C`**: are they run in production, and from which JCL (H1, `file-dd-unbound`)?
6. **Batch isolation**: is the CICS file close/open window a business requirement or only a VSAM constraint?
7. **Fees**: should `1400-COMPUTE-FEES` stay unimplemented (`app/cbl/CBACT04C.cbl:518`)?
8. **Card ↔ account cardinality** for collapsing `CARDXREF`.
9. **MQ**: queue manager and queue names for `COPAUA0C`, `COACCT01`, `CODATE01` (`mq-usage`).
10. **Unscheduled jobs**: 19 `CARDDEMO` jobs are in no scheduler table (`unscheduled-job`) — run on request, retired, or scheduled elsewhere?

# CREADB21 — Functional Requirements

**Source:** `app/app-transaction-type-db2/jcl/CREADB21.jcl` (84 lines) plus the control members it points at (`ctl/DB2FREE.ctl`, `ctl/DB2CREAT.ctl`, `ctl/DB2LTTYP.ctl`, `ctl/DB2LTCAT.ctl`).
**Target:** Spring Batch job `CREADB21` in `com.carddemo.trantype.batch`.
Stream-level IDs (FR-C*) are in `../TransactionTypeManagement_functional_requirement.md`.

Inventory note: `docs/migration/CardDemo_inventory.md` files this job under **S-19**; it is executed in S-08 because it owns the transaction-type / transaction-category data.

## 1. Symbolic parameters (`:28-30`)

```
SET CODER=AWS
SET LBNM=&CODER..M2.CARDDEMO      → AWS.M2.CARDDEMO
SET DB2S=DAZ1
```

They resolve the DB2 subsystem load libraries (`OEM.DB2.DAZ1.*`) and the `&LBNM..CNTL(...)` control members. None of them has a target equivalent: the target has a single configured datasource, so the symbolics are recorded here and dropped.

The job also carries `TYPRUN=SCAN` (`:2`) — as shipped it is syntax-checked only, never executed.

## 2. Step map (one Spring Batch `Step` per `EXEC PGM=`, D-3)

| Step | PGM | Control member | Legacy action | Target |
|---|---|---|---|---|
| `FREEPLN` | `IKJEFT01` | `DB2FREE` | free the existing plan/package; ends RC 8 when they do not exist | **not applicable** — no DB2 plans/packages in the target; the step is represented as a documented no-op so the step count still matches the JCL |
| `CRCRDDB` | `IKJEFT01` → `DSNTIAD` | `DB2TIAD1` + `DB2CREAT` | create the CardDemo database, tablespaces, tables and indexes on STOGROUP `AWST1STG` | **replaced by Flyway** — `db2_transaction_type` and `db2_transaction_type_category` already exist in `V1__baseline_carddemo_estate.sql`; the step verifies the tables are present instead of issuing DDL |
| `LDTTYPE` | `IEFBR14` | — | placeholder step | no-op |
| `RUNTEP2` | `IKJEFT01` → `DSNTEP4` | `DB2TEP41` + `DB2LTTYP` | insert the 7 transaction types | insert the same 7 rows |
| `LDTCCAT` | `IKJEFT01` → `DSNTEP4` | `DB2TEP41` + `DB2LTCAT` | insert the 18 transaction-type categories | insert the same 18 rows |

## 3. Table definitions the DDL creates (`ddl/TRNTYPE.ddl`, `ddl/TRNTYCAT.ddl`)

```sql
CREATE TABLE CARDDEMO.TRANSACTION_TYPE
(   TR_TYPE        CHAR(2) NOT NULL,
    TR_DESCRIPTION VARCHAR(50) NOT NULL,
    PRIMARY KEY(TR_TYPE));

CREATE TABLE CARDDEMO.TRANSACTION_TYPE_CATEGORY
(   TRC_TYPE_CODE     CHAR(2) NOT NULL,
    TRC_TYPE_CATEGORY CHAR(4) NOT NULL,
    TRC_CAT_DATA      VARCHAR(50) NOT NULL,
    PRIMARY KEY(TRC_TYPE_CODE,TRC_TYPE_CATEGORY),
    FOREIGN KEY TRC_TYPE_CODE (TRC_TYPE_CODE)
    REFERENCES CARDDEMO.TRANSACTION_TYPE (TR_TYPE) ON DELETE RESTRICT);
```

`ON DELETE RESTRICT` is what produces `SQLCODE -532` on the CTLI/CTTU delete paths. The foundation schema reproduces both tables and the foreign key.

## 4. Seed data (verbatim from the control members)

`ctl/DB2LTTYP.ctl` — 7 rows: `01 PURCHASE`, `02 PAYMENT`, `03 CREDIT`, `04 AUTHORIZATION`, `05 REFUND`, `06 REVERAL` (sic — the misspelling is in the source), `07 ADJUSTMENT`.

`ctl/DB2LTCAT.ctl` — 18 rows, keyed `(type, category)`: `01/0001 REGULAR SALES DRAFT`, `01/0002 REGULAR CASH ADVANCE`, `01/0003 CONVENIENCE CHECK DEBIT`, `01/0004 ATM CASH ADVANCE`, `01/0005 INTEREST AMOUNT`, `02/0001 CASH PAYMENT`, … through `07/0001`.

## 5. Rules

| # | Rule | Source |
|---|---|---|
| R1 | Loading is done with plain `INSERT … SELECT … FROM SYSIBM.SYSDUMMY1 UNION ALL …` followed by `COMMIT`, not a `LOAD REPLACE` | `ctl/DB2LTTYP.ctl`, `ctl/DB2LTCAT.ctl` |
| R2 | (quirk) Because R1 is a plain insert, re-running the load against populated tables fails on the duplicate primary key (`SQLCODE -803`) — it is a one-shot bootstrap, not an idempotent refresh | same |
| R3 | Categories can only be loaded after the types, because of the foreign key | `ddl/TRNTYCAT.ddl` |
| R4 | Every step after the first is guarded by `COND=(0,NE)`, i.e. it only runs if all previous steps ended RC 0 | `:64, 77` |

## 6. Divergence from the foundation seed (documented, not "fixed")

`db/seed/R__seed_carddemo_data.sql` already seeds the same 7 types and 18 categories but in mixed case and with `06` spelled `Reversal`. The migrated `CREADB21` job carries the **legacy** literals (upper case, `REVERAL`) exactly as the control members define them, so running it on a seeded database hits R2 and fails — which is precisely the legacy behaviour. Tests exercise the job against empty tables.

# TRANEXTR — Functional Requirements

**Source:** `app/app-transaction-type-db2/jcl/TRANEXTR.jcl` (122 lines).
**Target:** Spring Batch job `TRANEXTR` in `com.carddemo.trantype.batch`.
Stream-level IDs (FR-X*) are in `../TransactionTypeManagement_functional_requirement.md`.

Purpose (job comment, `:19-21`): extract the transaction-type reference data used by transaction reporting; it runs once a day, so report changes only appear after the daily batch.

## 1. Step map (one Spring Batch `Step` per `EXEC PGM=`, D-3)

| Step | PGM | COND | Legacy action | Target action |
|---|---|---|---|---|
| `STEP10` | `IEBGENER` | — | copy `&HLQ..TRANTYPE.PS` → `&HLQ..TRANTYPE.BKUP(+1)` (LRECL 60, FB) | copy `trantype.txt` → `trantype.txt.bkup` in the batch data directory (no-op when the source file is absent) |
| `STEP20` | `IEBGENER` | `(0,NE)` | copy `&HLQ..TRANCATG.PS` → `&HLQ..TRANCATG.PS.BKUP(+1)` | copy `trancatg.txt` → `trancatg.txt.bkup` |
| `STEP30` | `IEFBR14` | `(0,NE)` | delete `TRANTYPE.PS` and `TRANCATG.PS` (DD01/DD02) | delete both output files |
| `STEP40` | `IKJEFT01` → `DSNTIAUL` | `(0,NE)` | unload `CARDDEMO.TRANSACTION_TYPE` to `SYSREC00` = `TRANTYPE.PS` | write `trantype.txt` from `db2_transaction_type` |
| `STEP50` | `IKJEFT01` → `DSNTIAUL` | `(4,LT)` | unload `CARDDEMO.TRANSACTION_TYPE_CATEGORY` to `SYSREC00` = `TRANCATG.PS` | write `trancatg.txt` from `db2_transaction_type_category` |

`COND=(0,NE)` means "skip unless every previous step ended RC 0"; the migrated job stops on the first failed step, which is the same observable outcome.

## 2. Output layouts (verbatim SQL, `:76-84` and `:106-115`)

**Transaction type — 60 bytes** (`STEP40`)

```sql
SELECT CAST(CONCAT(CONCAT(
  TR_TYPE
 ,CAST(TR_DESCRIPTION AS CHAR(50))
  )
 ,REPEAT('0',8)
) AS CHAR(60))
 FROM CARDDEMO.TRANSACTION_TYPE
 ORDER BY TR_TYPE;
```

| Offset | Len | Content |
|---|---|---|
| 1-2 | 2 | `TR_TYPE` |
| 3-52 | 50 | `TR_DESCRIPTION`, space-padded to 50 |
| 53-60 | 8 | literal `00000000` |

**Transaction category — 60 bytes** (`STEP50`)

```sql
SELECT CAST(
      TRC_TYPE_CODE
   || TRC_TYPE_CATEGORY
   || CAST(TRC_CAT_DATA AS CHAR(50))
   || REPEAT('0',4)
                    AS CHAR(60))
FROM  CARDDEMO.TRANSACTION_TYPE_CATEGORY
ORDER BY TRC_TYPE_CODE, TRC_TYPE_CATEGORY;
```

| Offset | Len | Content |
|---|---|---|
| 1-2 | 2 | `TRC_TYPE_CODE` |
| 3-6 | 4 | `TRC_TYPE_CATEGORY` |
| 7-56 | 50 | `TRC_CAT_DATA`, space-padded to 50 |
| 57-60 | 4 | literal `0000` |

Both files are `RECFM=FB,LRECL=60`; ordering is by the primary key, ascending.

## 3. Rules

| # | Rule | Source |
|---|---|---|
| R1 | The previous extract is backed up before it is deleted | STEP10/STEP20 |
| R2 | The extract files are deleted and recreated, never appended | STEP30 + `DISP=(NEW,CATLG,DELETE)` on `SYSREC00` |
| R3 | Records are fixed 60 bytes with the trailing zero filler, so downstream fixed-width readers keep working | STEP40/STEP50 SQL |
| R4 | An empty table produces an empty (0-record) extract file | DSNTIAUL semantics |

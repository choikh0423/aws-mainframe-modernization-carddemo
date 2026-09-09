# COBTUPDT / MNTTRDB2 — Functional Requirements

**Source:** `app/app-transaction-type-db2/cbl/COBTUPDT.cbl` (237 lines), driven by `jcl/MNTTRDB2.jcl`.
**Target:** Spring Batch job `MNTTRDB2` in `com.carddemo.trantype.batch`.
Stream-level IDs (FR-B*) are in `../TransactionTypeManagement_functional_requirement.md`.

## 1. Job contract (`MNTTRDB2.jcl`)

Single step `STEP05 EXEC PGM=IKJEFT01` issuing `DSN SYSTEM(DAZ1)` then `RUN PROGRAM(COBTUPDT) PLAN(CARDDEMO)`. The maintenance data arrives on DD `INPFILE`; `SYSPRINT`/`SYSOUT`/`SYSTSPRT` carry the `DISPLAY` output.

Migrated shape: one Spring Batch `Job` named `MNTTRDB2` with one `Step` (`COBTUPDT`) — one step per `EXEC PGM=` (D-3).

## 2. Record layout (`:71-77`)

```
01 WS-INPUT-REC.
   05 INPUT-REC-TYPE   PIC X(1).    col 1      operation: A / U / D / *
   05 INPUT-REC-NUMBER PIC X(2).    cols 2-3   transaction type code
   05 INPUT-REC-DESC   PIC X(50).   cols 4-53  description
```

Fixed 53-byte records, read sequentially until EOF. Short lines are space-padded by the reader.

## 3. Processing rules

| # | Rule | Console output | Source |
|---|---|---|---|
| P1 | Open the input file; report the open status | `OPEN FILE OK` / `OPEN FILE NOT OK` | :82-89 |
| P2 | Each record is echoed before dispatch | `PROCESSING   <record>` | :99-107 |
| P3 | `A` → insert `(type, description)` | `ADDING RECORD` then `RECORD INSERTED SUCCESSFULLY` | :111-113, 132-164 |
| P4 | `U` → update the description for the type | `UPDATING RECORD` then `RECORD UPDATED SUCCESSFULLY` | :114-116, 166-195 |
| P5 | `D` → delete the type | `DELETING RECORD` then `RECORD DELETED SUCCESSFULLY` | :117-119, 196-226 |
| P6 | `*` → skip | `IGNORING COMMENTED LINE` | :120-121 |
| P7 | anything else → error | `ERROR: TYPE NOT VALID`, abend routine | :122-129 |
| P8 | `U`/`D` with `SQLCODE +100` | `No records found.`, abend routine | :178-186, 208-217 |
| P9 | negative `SQLCODE` on any operation | `Error accessing: TRANSACTION_TYPE table. SQLCODE:<code>`, abend routine | :147-158, 187-193, 218-224 |
| P10 | close the file and stop at EOF | — | :103-107 |

## 4. Abend semantics (quirk — preserved)

`9999-ABEND` (`:230-233`) only does:

```cobol
DISPLAY WS-RETURN-MSG
MOVE 4 TO RETURN-CODE
```

It does **not** issue `STOP RUN` or `CEE3ABD`. Consequences that the migrated job reproduces exactly:

- a bad record does not stop the run; every subsequent record is still processed;
- the return code is never reset, so a single failure makes the whole step end with RC = 4;
- a clean run ends with RC = 0.

In the target this is expressed as: the step keeps processing, records the failure through `AbendService`, and the job exits with status code 4 instead of throwing on the first bad record.

## 5. Data access

`INSERT INTO CARDDEMO.TRANSACTION_TYPE (TR_TYPE, TR_DESCRIPTION) VALUES (:type, :desc)`,
`UPDATE CARDDEMO.TRANSACTION_TYPE SET TR_DESCRIPTION = :desc WHERE TR_TYPE = :type`,
`DELETE FROM CARDDEMO.TRANSACTION_TYPE WHERE TR_TYPE = :type`.
Migrated to `Db2TransactionTypeRepository` (`com.carddemo.common.domain`) under a step-scoped transaction.

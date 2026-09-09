# PAUDBLOD — Functional Requirement (IMS load utility)

Source: `app/app-authorization-ims-db2-mq/cbl/PAUDBLOD.CBL`, JCL `jcl/LOADPADB.JCL`
(`DFSRRC00 PARM='DLI,PAUDBLOD,PSBPAUTL'`; DBD created by `jcl/DBPAUTP0.jcl`).
Migrated to Spring Batch job `pendingAuthLoadJob` in `com.carddemo.batch.pendingauthims`.

## Inputs
* `INFILE1` — fixed 100-byte summary records, layout `cpy/CIPAUSMY.cpy` (packed fields).
* `INFILE2` — fixed 100-byte detail records prefixed by the 6-byte packed root key
  (`ROOT-SEG-KEY`), payload layout `cpy/CIPAUDTY.cpy`.

## Behaviour
1. Open both files; a failed open displays `ERROR IN OPENING INFILE1:`/`INFILE2:` and abends
   (return code 16).
2. For every summary record: `ISRT PAUTSUM0`. Status `' '` → `ROOT INSERT SUCCESS`; status `II`
   (already present) → `ROOT SEGMENT ALREADY IN DB` and continue; anything else →
   `ROOT INSERT FAILED  :<status>` and abend.
3. For every detail record whose root key is numeric: `GU PAUTSUM0` on that key, then
   `ISRT PAUTDTL1`. Status `II` tolerated (`CHILD SEGMENT ALREADY IN DB`), other non-blank status →
   `INSERT CALL FAIL FOR CHILD:<status>` and abend. **A detail whose root key is not numeric is
   silently skipped** (PAUDBLOD:262-266).
4. Close both files; a failed close is displayed but does not abend.

## Requirements
FR-PA-B07, FR-PA-B08.

## Migration notes
The module ships only an EBCDIC IMS image
(`app/app-authorization-ims-db2-mq/data/EBCDIC/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat`), so the
migrated load/unload pair defines an **ASCII display** rendering of the two segments (packed fields
written as zoned digits with an explicit sign, same field order and widths as the copybooks) —
boundary decision BD-6. `pendingAuthUnloadJob` writes exactly what `pendingAuthLoadJob` reads, and
the round trip is covered by a test. "Already in DB" maps to "row already present for that key",
which is skipped rather than treated as an error. Job parameters `summaryFile` and `detailFile` name
the inputs, as the JCL DD statements do.

# DBUNLDGS — Functional Requirement (GSAM unload utility)

Source: `app/app-authorization-ims-db2-mq/cbl/DBUNLDGS.CBL`, JCL `jcl/UNLDGSAM.JCL`
(`DFSRRC00 PARM='DLI,DBUNLDGS,PSBPAUTG'`).
Migrated as the `gsam` mode of `pendingAuthUnloadJob`
(`com.carddemo.batch.pendingauthims`), selected by the job parameter `output=gsam`.

## Behaviour
Identical traversal to PAUDBUNL, but the two record streams are written through the GSAM output PCBs
(`ISRT` against `GSAMOUT1` / `GSAMOUT2`) instead of QSAM `WRITE`s:

1. `GN PAUTSUM0` until `GB`; skip summaries with a non-numeric account id.
2. `ISRT` the summary record to the first GSAM PCB; a non-blank status displays
   `GSAM ISRT FAILED FOR ROOT :<status>` and abends with return code 16.
3. `GNP PAUTDTL1` until `GE`/`GB`; `ISRT` each detail record to the second GSAM PCB, with the same
   abend behaviour (`GSAM ISRT FAILED FOR CHILD:<status>`).
4. Display the summary and detail counts at the end.

## Requirements
FR-PA-B10.

## Migration notes
GSAM is IMS's sequential-file access method: outside IMS the distinction from QSAM disappears, so
the migrated job shares one writer implementation and the `output` job parameter only records which
legacy utility is being reproduced. The record layout is identical to PAUDBUNL's, so the load job
consumes either output unchanged.

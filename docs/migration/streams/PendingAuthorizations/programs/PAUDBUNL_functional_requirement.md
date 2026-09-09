# PAUDBUNL — Functional Requirement (IMS unload utility)

Source: `app/app-authorization-ims-db2-mq/cbl/PAUDBUNL.CBL`, JCL `jcl/UNLDPADB.JCL`
(`DFSRRC00 PARM='DLI,PAUDBUNL,PSBPAUTL'`).
Migrated to Spring Batch job `pendingAuthUnloadJob` in `com.carddemo.batch.pendingauthims`.

## Outputs
* `OPFILE1` — one 100-byte record per `PAUTSUM0` summary.
* `OPFILE2` — one 100-byte record per `PAUTDTL1` detail, prefixed by its root key.

## Behaviour
1. Open both output files; failure displays `ERROR IN OPENING OPFILE1:`/`OPFILE2:` and abends
   (return code 16).
2. `GN PAUTSUM0` until status `GB`. Any other non-blank status →
   `ROOT GN CALL FAILED :<status>` and abend.
3. A summary whose account id is not numeric is **skipped together with its children**
   (PAUDBUNL:150-155).
4. Otherwise write the summary record, then `GNP PAUTDTL1` until `GE` (end of children) or `GB`
   (end of database), writing each detail record. Other statuses →
   `CHILD GNP CALL FAILED:<status>` and abend.
5. Close both files and display the record counts.

## Requirements
FR-PA-B09; the ASCII rendering and its round trip with `pendingAuthLoadJob` are BD-6 in the
migration plan.

## Migration notes
`GN`/`GNP` hierarchical traversal becomes a paged read of `pending_auth_summary` in account-id order
with the children of each summary read in inverted-key order — the same order in which IMS returns
them, because the inverted key is the physical child sequence field. `GE`/`GB` become "no more
children" / "no more summaries" rather than status codes.

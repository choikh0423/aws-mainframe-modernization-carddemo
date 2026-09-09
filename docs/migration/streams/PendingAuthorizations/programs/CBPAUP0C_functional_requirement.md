# CBPAUP0C — Functional Requirement (purge expired authorizations)

Source: `app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl`, JCL `jcl/CBPAUP0J.jcl`
(`DFSRRC00 PARM='BMP,CBPAUP0C,PSBPAUTB'`, SYSIN `00,00001,00001,Y`).
Migrated to Spring Batch job `pendingAuthPurgeJob` in
`com.carddemo.batch.pendingauthpurge`.

## Parameters
`P-EXPIRY-DAYS 9(02), P-CHKP-FREQ X(05), P-CHKP-DIS-FREQ X(05), P-DEBUG-FLAG X(01)` read from SYSIN.
Defaults: expiry days 5 (when not numeric), checkpoint frequency 5, display frequency 10, debug `N`.
`00` in the shipped SYSIN is numeric, so the shipped run purges everything dated today or earlier.

## Algorithm
```
for each PAUTSUM0 (GN until GB):
    for each child PAUTDTL1 (GNP until GE/GB):
        auth_date = 99999 - PA-AUTH-DATE-9C
        if today_yyddd - auth_date >= expiry_days:
            if resp code = '00': approved cnt -= 1 ; approved amt -= approved amt
            else               : declined cnt -= 1 ; declined amt -= transaction amt
            DLET child
    if approved cnt <= 0 and approved cnt <= 0:   DLET root
```

## Requirements
FR-PA-B01 … FR-PA-B06.

## Quirks preserved
* **Q-7** the root-delete condition tests the approved count twice; the declined count is ignored,
  so a summary with only declined authorizations left is deleted.
* **Q-8** the decremented counters are never written back with a `REPL`, so after a purge the
  surviving summary still shows the pre-purge counts. The migrated job decrements a detached copy of
  the counters, uses them only for the delete decision and never saves the summary row, reproducing
  the same end state.

## Reporting
Counts of summaries read, details read, details deleted and summaries deleted are logged at the end
of the step (legacy `DISPLAY` block). An unexpected database status ends the job with a non-zero
exit code through `AbendService`.

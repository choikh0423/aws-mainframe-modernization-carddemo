# COACTVWC (CAVW) Verification Report

**Generated:** 2026-05-07 21:33:05 UTC
**Target Program:** COACTVWC
**Transaction ID:** CAVW

## Summary

| Metric         | Count |
|----------------|-------|
| Total Checks   | 54 |
| Passed         | 54  |
| Failed         | 0  |

**Result:** ALL CHECKS PASSED

## Detailed Results

| Result | Check | Detail |
|--------|-------|--------|
| PASS | Check1-Program | COACTVWC.cbl exists in app/cbl/ |
| PASS | Check1-Program | COMEN01C.cbl exists in app/cbl/ |
| PASS | Check1-Program | COSGN00C.cbl exists in app/cbl/ |
| PASS | Check2-Copybook | CVCRD01Y.cpy exists in app/cpy/ |
| PASS | Check2-Copybook | COCOM01Y.cpy exists in app/cpy/ |
| PASS | Check2-Copybook | COTTL01Y.cpy exists in app/cpy/ |
| PASS | Check2-Copybook | CSDAT01Y.cpy exists in app/cpy/ |
| PASS | Check2-Copybook | CSMSG01Y.cpy exists in app/cpy/ |
| PASS | Check2-Copybook | CSMSG02Y.cpy exists in app/cpy/ |
| PASS | Check2-Copybook | CSUSR01Y.cpy exists in app/cpy/ |
| PASS | Check2-Copybook | CVACT01Y.cpy exists in app/cpy/ |
| PASS | Check2-Copybook | CVACT02Y.cpy exists in app/cpy/ |
| PASS | Check2-Copybook | CVACT03Y.cpy exists in app/cpy/ |
| PASS | Check2-Copybook | CVCUS01Y.cpy exists in app/cpy/ |
| PASS | Check2-Copybook | CSSTRPFY.cpy exists in app/cpy/ |
| PASS | Check2-SysCopybook | DFHBMSCA correctly identified as system copybook (not in app/cpy/) |
| PASS | Check2-SysCopybook | DFHAID correctly identified as system copybook (not in app/cpy/) |
| PASS | Check2-BMSCopybook | COACTVW correctly identified as BMS-generated symbolic map (not in app/cpy/) |
| PASS | Check3-BMS | COACTVW.bms exists in app/bms/ |
| PASS | Check4-CICS | EXEC CICS ABEND documented in analysis |
| PASS | Check4-CICS | EXEC CICS HANDLE documented in analysis |
| PASS | Check4-CICS | EXEC CICS HANDLE documented in analysis |
| PASS | Check4-CICS | EXEC CICS READ documented in analysis |
| PASS | Check4-CICS | EXEC CICS READ documented in analysis |
| PASS | Check4-CICS | EXEC CICS READ documented in analysis |
| PASS | Check4-CICS | EXEC CICS RECEIVE documented in analysis |
| PASS | Check4-CICS | EXEC CICS RETURN documented in analysis |
| PASS | Check4-CICS | EXEC CICS RETURN documented in analysis |
| PASS | Check4-CICS | EXEC CICS RETURN documented in analysis |
| PASS | Check4-CICS | EXEC CICS SEND documented in analysis |
| PASS | Check4-CICS | EXEC CICS SEND documented in analysis |
| PASS | Check4-CICS | EXEC CICS SEND documented in analysis |
| PASS | Check4-CICS | EXEC CICS SEND documented in analysis |
| PASS | Check4-CICS | EXEC CICS XCTL documented in analysis |
| PASS | Check5-CALL | No CALL statements in source — analysis correctly states no sub-programs |
| PASS | Check6-COPY | COPY COACTVW documented in analysis |
| PASS | Check6-COPY | COPY COCOM01Y documented in analysis |
| PASS | Check6-COPY | COPY COTTL01Y documented in analysis |
| PASS | Check6-COPY | COPY CSDAT01Y documented in analysis |
| PASS | Check6-COPY | COPY CSMSG01Y documented in analysis |
| PASS | Check6-COPY | COPY CSMSG02Y documented in analysis |
| PASS | Check6-COPY | COPY CSSTRPFY documented in analysis |
| PASS | Check6-COPY | COPY CSUSR01Y documented in analysis |
| PASS | Check6-COPY | COPY CVACT01Y documented in analysis |
| PASS | Check6-COPY | COPY CVACT02Y documented in analysis |
| PASS | Check6-COPY | COPY CVACT03Y documented in analysis |
| PASS | Check6-COPY | COPY CVCRD01Y documented in analysis |
| PASS | Check6-COPY | COPY CVCUS01Y documented in analysis |
| PASS | Check6-COPY | COPY DFHAID documented in analysis |
| PASS | Check6-COPY | COPY DFHBMSCA documented in analysis |
| PASS | Check7-XCTL | XCTL target CDEMO-FROM-PROGRAM (dynamic — calling program) documented |
| PASS | Check7-XCTL | XCTL target LIT-MENUPGM (COMEN01C) is in program inventory |
| PASS | Check8-Upstream | COMEN01C (or its menu table COMEN02Y) references COACTVWC |
| PASS | Check8-Upstream | COACTVWC references COMEN01C (return target on PF3) |


## Check Descriptions

| Check | Description |
|-------|-------------|
| Check1-Program | Every program listed in the analysis exists as a .cbl file |
| Check2-Copybook | Every application copybook exists as a .cpy file |
| Check2-SysCopybook | System copybooks (DFHAID, DFHBMSCA) are correctly flagged as system |
| Check2-BMSCopybook | BMS-generated symbolic maps are correctly flagged as auto-generated |
| Check3-BMS | Every BMS mapset exists as a .bms file |
| Check4-CICS | Every EXEC CICS command in the source is documented in the analysis |
| Check5-CALL | All CALL statements (if any) are documented |
| Check6-COPY | Every COPY statement in the source is documented |
| Check7-XCTL | All XCTL/LINK targets are included in the program inventory |
| Check8-Upstream | Programs listed as upstream actually reference the target |

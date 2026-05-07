# COCRDUPC (CCUP) Flow Analysis — Verification Report

**Date:** 2026-05-07 21:33:35 UTC
**Repository:** choikh0423/aws-mainframe-modernization-carddemo
**Branch:** demos/cobol-full-docs
**Target Program:** COCRDUPC
**Transaction ID:** CCUP

## Summary

| Metric | Count |
|--------|-------|
| Total Checks | 52 |
| Passed | 52 |
| Failed | 0 |

**Result:** ALL CHECKS PASSED

## Detailed Results

| # | Result | Check | Detail |
|---|--------|-------|--------|
| 1 | PASS | Program COCRDUPC exists | /home/ubuntu/repos/aws-mainframe-modernization-carddemo/app/cbl/COCRDUPC.cbl found |
| 2 | PASS | Program COCRDLIC exists | /home/ubuntu/repos/aws-mainframe-modernization-carddemo/app/cbl/COCRDLIC.cbl found |
| 3 | PASS | Program COMEN01C exists | /home/ubuntu/repos/aws-mainframe-modernization-carddemo/app/cbl/COMEN01C.cbl found |
| 4 | PASS | Program COSGN00C exists | /home/ubuntu/repos/aws-mainframe-modernization-carddemo/app/cbl/COSGN00C.cbl found |
| 5 | PASS | Program COACTVWC exists | /home/ubuntu/repos/aws-mainframe-modernization-carddemo/app/cbl/COACTVWC.cbl found |
| 6 | PASS | Program COACTUPC exists | /home/ubuntu/repos/aws-mainframe-modernization-carddemo/app/cbl/COACTUPC.cbl found |
| 7 | PASS | Program COCRDSLC exists | /home/ubuntu/repos/aws-mainframe-modernization-carddemo/app/cbl/COCRDSLC.cbl found |
| 8 | PASS | Copybook COCOM01Y exists in app/cpy | Found |
| 9 | PASS | Copybook CVCRD01Y exists in app/cpy | Found |
| 10 | PASS | Copybook COTTL01Y exists in app/cpy | Found |
| 11 | PASS | Copybook CSDAT01Y exists in app/cpy | Found |
| 12 | PASS | Copybook CSMSG01Y exists in app/cpy | Found |
| 13 | PASS | Copybook CSMSG02Y exists in app/cpy | Found |
| 14 | PASS | Copybook CSUSR01Y exists in app/cpy | Found |
| 15 | PASS | Copybook CVACT02Y exists in app/cpy | Found |
| 16 | PASS | Copybook CVCUS01Y exists in app/cpy | Found |
| 17 | PASS | Copybook CSSTRPFY exists in app/cpy | Found |
| 18 | PASS | Copybook COMEN02Y exists in app/cpy | Found |
| 19 | PASS | BMS copybook COCRDUP exists in app/cpy-bms | Found |
| 20 | PASS | System copybook DFHAID correctly flagged (not in app/cpy) | Confirmed as system copybook |
| 21 | PASS | System copybook DFHBMSCA correctly flagged (not in app/cpy) | Confirmed as system copybook |
| 22 | PASS | BMS mapset COCRDUP exists | /home/ubuntu/repos/aws-mainframe-modernization-carddemo/app/bms/COCRDUP.bms found |
| 23 | PASS | EXEC CICS ABEND documented | Found in analysis |
| 24 | PASS | EXEC CICS HANDLE documented | Found in analysis |
| 25 | PASS | EXEC CICS READ documented | Found in analysis |
| 26 | PASS | EXEC CICS RECEIVE documented | Found in analysis |
| 27 | PASS | EXEC CICS RETURN documented | Found in analysis |
| 28 | PASS | EXEC CICS SEND documented | Found in analysis |
| 29 | PASS | EXEC CICS XCTL documented | Found in analysis |
| 30 | PASS | No CALL statements in COCRDUPC | Confirmed: 0 CALL statements found (as documented) |
| 31 | PASS | COPY COCOM01Y documented | Found in analysis |
| 32 | PASS | COPY COCRDUP documented | Found in analysis |
| 33 | PASS | COPY COTTL01Y documented | Found in analysis |
| 34 | PASS | COPY CSDAT01Y documented | Found in analysis |
| 35 | PASS | COPY CSMSG01Y documented | Found in analysis |
| 36 | PASS | COPY CSMSG02Y documented | Found in analysis |
| 37 | PASS | COPY CSSTRPFY documented | Found in analysis |
| 38 | PASS | COPY CSUSR01Y documented | Found in analysis |
| 39 | PASS | COPY CVACT02Y documented | Found in analysis |
| 40 | PASS | COPY CVCRD01Y documented | Found in analysis |
| 41 | PASS | COPY CVCUS01Y documented | Found in analysis |
| 42 | PASS | COPY DFHAID documented | Found in analysis |
| 43 | PASS | COPY DFHBMSCA documented | Found in analysis |
| 44 | PASS | XCTL commands found in COCRDUPC | 1 XCTL command(s) |
| 45 | PASS | XCTL target LIT-MENUPGM='COMEN01C' documented | COMEN01C is in program inventory |
| 46 | PASS | XCTL dynamic target CDEMO-FROM-PROGRAM traced | Resolves to calling program |
| 47 | PASS | No LINK commands in COCRDUPC (as documented) | Confirmed |
| 48 | PASS | COCRDLIC references COCRDUPC | Found literal 'COCRDUPC' in COCRDLIC.cbl |
| 49 | PASS | COMEN01C menu table includes COCRDUPC | Found 'COCRDUPC' in COMEN02Y.cpy (option 5) |
| 50 | PASS | COACTVWC references COCRDUPC | Found literal 'COCRDUPC' in COACTVWC.cbl |
| 51 | PASS | COACTUPC references COCRDUPC | Found literal 'COCRDUPC' in COACTUPC.cbl |
| 52 | PASS | COCRDLIC has XCTL command (path to COCRDUPC) | XCTL found in COCRDLIC.cbl |


## Check Categories

### Check 1 — Program Existence
Every program listed in the analysis exists as a `.cbl` file in `app/cbl/`.

### Check 2 — Copybook Existence
Every copybook listed exists as a `.cpy` file in `app/cpy/` or `app/cpy-bms/`. DFHAID and DFHBMSCA are confirmed as IBM system copybooks (not in application directories).

### Check 3 — BMS Map Existence
Every BMS mapset listed exists as a `.bms` file in `app/bms/`.

### Check 4 — EXEC CICS Extraction
Every `EXEC CICS` command type found in the target program source is documented in the analysis.

### Check 5 — CALL Extraction
Every `CALL` statement in the target program is documented. COCRDUPC has no CALL statements (confirmed).

### Check 6 — COPY Extraction
Every `COPY` statement in the target program is documented in the copybook inventory.

### Check 7 — XCTL/LINK Targets
Every program referenced by XCTL or LINK is included in the program inventory. Dynamic XCTL target `CDEMO-TO-PROGRAM` resolves to either the calling program or `COMEN01C` (main menu).

### Check 8 — Upstream Cross-Reference
Programs listed as upstream callers actually contain references to COCRDUPC in their source code.

---

*Generated by verify_cocrdupc_flow.sh*

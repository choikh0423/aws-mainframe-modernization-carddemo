# CORPT00C (CR00) Flow Analysis — Verification Report

**Date:** 2026-05-07 21:33:39 UTC
**Target Program:** CORPT00C
**Transaction ID:** CR00
**Analysis Document:** CORPT00C_Flow_Analysis.md

## Summary

| Metric        | Count |
|---------------|-------|
| Total checks  | 47 |
| Passed        | 47  |
| Failed        | 0  |

**Result: ALL CHECKS PASSED**

## Detailed Results

| # | Status | Check | Detail |
|---|--------|-------|--------|
| 1 | PASS | Program existence | CORPT00C.cbl exists in app/cbl/ |
| 2 | PASS | Program existence | CSUTLDTC.cbl exists in app/cbl/ |
| 3 | PASS | Program existence | COMEN01C.cbl exists in app/cbl/ |
| 4 | PASS | Program existence | COSGN00C.cbl exists in app/cbl/ |
| 5 | PASS | Program existence | CEEDAYS correctly classified as LE intrinsic (not in repo) |
| 6 | PASS | Copybook existence | COCOM01Y.cpy exists in app/cpy/ |
| 7 | PASS | Copybook existence | COTTL01Y.cpy exists in app/cpy/ |
| 8 | PASS | Copybook existence | CSDAT01Y.cpy exists in app/cpy/ |
| 9 | PASS | Copybook existence | CSMSG01Y.cpy exists in app/cpy/ |
| 10 | PASS | Copybook existence | CVTRA05Y.cpy exists in app/cpy/ |
| 11 | PASS | Copybook existence | COMEN02Y.cpy exists in app/cpy/ |
| 12 | PASS | Copybook existence | CSUSR01Y.cpy exists in app/cpy/ |
| 13 | PASS | Copybook existence | DFHAID correctly classified as CICS system copybook (not in app/cpy/) |
| 14 | PASS | Copybook existence | DFHBMSCA correctly classified as CICS system copybook (not in app/cpy/) |
| 15 | PASS | Copybook existence | CORPT00 (BMS symbolic map) correctly noted as auto-generated (not in app/cpy/) |
| 16 | PASS | BMS map existence | CORPT00.bms exists in app/bms/ |
| 17 | PASS | EXEC CICS extraction | EXEC CICS RECEIVE is documented in analysis |
| 18 | PASS | EXEC CICS extraction | EXEC CICS RETURN is documented in analysis |
| 19 | PASS | EXEC CICS extraction | EXEC CICS RETURN is documented in analysis |
| 20 | PASS | EXEC CICS extraction | EXEC CICS SEND is documented in analysis |
| 21 | PASS | EXEC CICS extraction | EXEC CICS SEND is documented in analysis |
| 22 | PASS | EXEC CICS extraction | EXEC CICS WRITEQ is documented in analysis |
| 23 | PASS | CALL extraction | CALL 'CSUTLDTC' is documented in analysis |
| 24 | PASS | CALL extraction | Documented CALL 'CSUTLDTC' confirmed in source |
| 25 | PASS | COPY extraction | COPY COCOM01Y is documented in analysis |
| 26 | PASS | COPY extraction | COPY CORPT00 is documented in analysis |
| 27 | PASS | COPY extraction | COPY COTTL01Y is documented in analysis |
| 28 | PASS | COPY extraction | COPY CSDAT01Y is documented in analysis |
| 29 | PASS | COPY extraction | COPY CSMSG01Y is documented in analysis |
| 30 | PASS | COPY extraction | COPY CVTRA05Y is documented in analysis |
| 31 | PASS | COPY extraction | COPY DFHAID is documented in analysis |
| 32 | PASS | COPY extraction | COPY DFHBMSCA is documented in analysis |
| 33 | PASS | COPY extraction | Documented COPY COCOM01Y confirmed in source |
| 34 | PASS | COPY extraction | Documented COPY CORPT00 confirmed in source |
| 35 | PASS | COPY extraction | Documented COPY COTTL01Y confirmed in source |
| 36 | PASS | COPY extraction | Documented COPY CSDAT01Y confirmed in source |
| 37 | PASS | COPY extraction | Documented COPY CSMSG01Y confirmed in source |
| 38 | PASS | COPY extraction | Documented COPY CVTRA05Y confirmed in source |
| 39 | PASS | COPY extraction | Documented COPY DFHAID confirmed in source |
| 40 | PASS | COPY extraction | Documented COPY DFHBMSCA confirmed in source |
| 41 | PASS | XCTL/LINK targets | XCTL target 'COMEN01C' is in program inventory |
| 42 | PASS | XCTL/LINK targets | XCTL target 'COSGN00C' is in program inventory |
| 43 | PASS | Upstream cross-ref | COMEN02Y.cpy (used by COMEN01C) contains 'CORPT00C' as menu option |
| 44 | PASS | Upstream cross-ref | COMEN01C.cbl has XCTL PROGRAM(CDEMO-MENU-OPT-PGMNAME) — menu-driven dispatch |
| 45 | PASS | Upstream cross-ref | COSGN00C.cbl contains XCTL to COMEN01C (upstream chain confirmed) |
| 46 | PASS | Upstream cross-ref | CORPT00C.cbl references COMEN01C as return target |
| 47 | PASS | Upstream cross-ref | CORPT00C.cbl references COSGN00C as EIBCALEN=0 fallback |

## Check Descriptions

| Check | Description |
|-------|-------------|
| 1. Program existence | Every program listed in the analysis exists as a .cbl file in app/cbl/ |
| 2. Copybook existence | Every copybook listed exists as a .cpy file in app/cpy/ (system copybooks excluded) |
| 3. BMS map existence | Every BMS mapset listed exists as a .bms file in app/bms/ |
| 4. EXEC CICS extraction | Every EXEC CICS command in the target program is documented |
| 5. CALL extraction | Every CALL statement in the target program is documented |
| 6. COPY extraction | Every COPY statement in the target program is documented |
| 7. XCTL/LINK targets | Every program referenced by XCTL or LINK is in the program inventory |
| 8. Upstream cross-ref | Programs listed as upstream actually reference the target |

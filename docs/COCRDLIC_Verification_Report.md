# COCRDLIC (CCLI) Flow Analysis — Verification Report

**Date:** 2026-05-07 21:35:32 UTC
**Target Program:** COCRDLIC
**Transaction ID:** CCLI
**Description:** Credit Card List

## Summary

| Metric        | Value |
|---------------|-------|
| Total checks  | 51 |
| Passed        | 51  |
| Failed        | 0  |
| **Result**    | ALL CHECKS PASSED |

## Detailed Results


### Check 1 — Program Existence

| # | Result | Detail |
|---|--------|--------|
| 1 | PASS | Program COSGN00C.cbl exists in app/cbl/ |
| 2 | PASS | Program COMEN01C.cbl exists in app/cbl/ |
| 3 | PASS | Program COCRDLIC.cbl exists in app/cbl/ |
| 4 | PASS | Program COCRDSLC.cbl exists in app/cbl/ |
| 5 | PASS | Program COCRDUPC.cbl exists in app/cbl/ |

### Check 2 — Copybook Existence

| # | Result | Detail |
|---|--------|--------|
| 6 | PASS | Copybook CVCRD01Y.cpy exists in app/cpy/ |
| 7 | PASS | Copybook COCOM01Y.cpy exists in app/cpy/ |
| 8 | PASS | Copybook COTTL01Y.cpy exists in app/cpy/ |
| 9 | PASS | Copybook CSDAT01Y.cpy exists in app/cpy/ |
| 10 | PASS | Copybook CSMSG01Y.cpy exists in app/cpy/ |
| 11 | PASS | Copybook CSUSR01Y.cpy exists in app/cpy/ |
| 12 | PASS | Copybook CVACT02Y.cpy exists in app/cpy/ |
| 13 | PASS | Copybook CSSTRPFY.cpy exists in app/cpy/ |
| 14 | PASS | System copybook DFHBMSCA correctly flagged as not in app/cpy/ |
| 15 | PASS | System copybook DFHAID correctly flagged as not in app/cpy/ |
| 16 | PASS | BMS symbolic map COCRDLI correctly flagged as auto-generated (not in app/cpy/) |

### Check 3 — BMS Map Existence

| # | Result | Detail |
|---|--------|--------|
| 17 | PASS | BMS map COCRDLI.bms exists in app/bms/ |

### Check 4 — EXEC CICS Command Extraction

| # | Result | Detail |
|---|--------|--------|
| 18 | PASS | All 18 EXEC CICS commands documented (expected 18) |
| 19 | PASS | EXEC CICS SEND MAP found in source (1 occurrence(s)) — documented |
| 20 | PASS | EXEC CICS RECEIVE MAP found in source (1 occurrence(s)) — documented |
| 21 | PASS | EXEC CICS STARTBR found in source (2 occurrence(s)) — documented |
| 22 | PASS | EXEC CICS READNEXT found in source (2 occurrence(s)) — documented |
| 23 | PASS | EXEC CICS READPREV found in source (2 occurrence(s)) — documented |
| 24 | PASS | EXEC CICS ENDBR found in source (1 occurrence(s)) — documented |
| 25 | PASS | EXEC CICS XCTL found in source (3 occurrence(s)) — documented |
| 26 | PASS | EXEC CICS RETURN found in source (3 occurrence(s)) — documented |
| 27 | PASS | EXEC CICS SEND TEXT found in source (2 occurrence(s)) — documented |

### Check 5 — CALL Statement Extraction

| # | Result | Detail |
|---|--------|--------|
| 28 | PASS | No CALL statements in COCRDLIC — correctly documented as 0 CALLs |

### Check 6 — COPY Statement Extraction

| # | Result | Detail |
|---|--------|--------|
| 29 | PASS | COPY COCOM01Y found in source and documented in analysis |
| 30 | PASS | COPY COCRDLI found in source and documented in analysis |
| 31 | PASS | COPY COTTL01Y found in source and documented in analysis |
| 32 | PASS | COPY CSDAT01Y found in source and documented in analysis |
| 33 | PASS | COPY CSMSG01Y found in source and documented in analysis |
| 34 | PASS | COPY CSSTRPFY found in source and documented in analysis |
| 35 | PASS | COPY CSUSR01Y found in source and documented in analysis |
| 36 | PASS | COPY CVACT02Y found in source and documented in analysis |
| 37 | PASS | COPY CVCRD01Y found in source and documented in analysis |
| 38 | PASS | COPY DFHAID found in source and documented in analysis |
| 39 | PASS | COPY DFHBMSCA found in source and documented in analysis |

### Check 7 — XCTL/LINK Target Verification

| # | Result | Detail |
|---|--------|--------|
| 40 | PASS | XCTL target COMEN01C exists as COMEN01C.cbl |
| 41 | PASS | XCTL target COCRDSLC exists as COCRDSLC.cbl |
| 42 | PASS | XCTL target COCRDUPC exists as COCRDUPC.cbl |
| 43 | PASS | XCTL to COMEN01C via LIT-MENUPGM confirmed in source |
| 44 | PASS | Reference to COCRDSLC via LIT-CARDDTLPGM confirmed in source |
| 45 | PASS | Reference to COCRDUPC via LIT-CARDUPDPGM confirmed in source |
| 46 | PASS | No EXEC CICS LINK in COCRDLIC — correctly documented |

### Check 8 — Upstream Cross-Reference

| # | Result | Detail |
|---|--------|--------|
| 47 | PASS | COMEN01C upstream: COCRDLIC found in menu options (COMEN02Y.cpy) |
| 48 | PASS | COCRDSLC downstream: references COCRDLIC (LIT-CCLISTPGM) |
| 49 | PASS | COCRDUPC downstream: references COCRDLIC (LIT-CCLISTPGM) |
| 50 | PASS | CSD definition: TRANSACTION(CCLI) found in CARDDEMO.CSD |
| 51 | PASS | CSD definition: PROGRAM(COCRDLIC) mapped to CCLI in CARDDEMO.CSD |


## Checks Performed

1. **Program Existence** — Verify every program listed in the analysis exists as a `.cbl` file
2. **Copybook Existence** — Verify every application copybook exists as a `.cpy` file; system copybooks (DFHBMSCA, DFHAID) correctly flagged as not in repo; BMS-generated map (COCRDLI) correctly flagged as auto-generated
3. **BMS Map Existence** — Verify every BMS mapset exists as a `.bms` file
4. **EXEC CICS Extraction** — Verify every EXEC CICS command in source is documented, with correct count and command types
5. **CALL Extraction** — Verify every CALL statement is documented (0 in this program)
6. **COPY Extraction** — Verify every COPY statement in source is documented and vice versa
7. **XCTL/LINK Targets** — Verify all XCTL target programs exist and are referenced in source
8. **Upstream Cross-Reference** — Verify upstream programs reference COCRDLIC, and CSD definition matches

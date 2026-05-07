# COUSR03C (CU03) Verification Report

**Generated:** 2026-05-07 21:34:53 UTC
**Target Program:** COUSR03C
**Transaction ID:** CU03
**Analysis File:** COUSR03C_Flow_Analysis.md

---

## Summary

| Metric        | Count |
|---------------|-------|
| Total Checks  | 36 |
| Passed        | 36  |
| Failed        | 0  |
| **Result**    | **ALL CHECKS PASSED** |

---

## Detailed Results

| Result | Check Description |
|--------|-------------------|
| PASS | Check 1 — Program COUSR03C exists as COUSR03C.cbl |
| PASS | Check 1 — Program COADM01C exists as COADM01C.cbl |
| PASS | Check 1 — Program COSGN00C exists as COSGN00C.cbl |
| PASS | Check 1 — Program COUSR00C exists as COUSR00C.cbl |
| PASS | Check 2 — Copybook COCOM01Y exists in app/cpy/ |
| PASS | Check 2 — Copybook COTTL01Y exists in app/cpy/ |
| PASS | Check 2 — Copybook CSDAT01Y exists in app/cpy/ |
| PASS | Check 2 — Copybook CSMSG01Y exists in app/cpy/ |
| PASS | Check 2 — Copybook CSUSR01Y exists in app/cpy/ |
| PASS | Check 2 — Copybook COADM02Y exists in app/cpy/ |
| PASS | Check 2 — BMS symbolic map COUSR03.CPY exists in app/cpy-bms/ |
| PASS | Check 2 — DFHAID correctly flagged as system copybook (not in app/cpy/) |
| PASS | Check 2 — DFHBMSCA correctly flagged as system copybook (not in app/cpy/) |
| PASS | Check 3 — BMS mapset COUSR03.bms exists |
| PASS | Check 4 — EXEC CICS DELETE documented and present in source |
| PASS | Check 4 — EXEC CICS READ documented and present in source |
| PASS | Check 4 — EXEC CICS RECEIVE documented and present in source |
| PASS | Check 4 — EXEC CICS RETURN documented and present in source |
| PASS | Check 4 — EXEC CICS SEND documented and present in source |
| PASS | Check 4 — EXEC CICS XCTL documented and present in source |
| PASS | Check 5 — No CALL statements in source (analysis correctly states 0 CALLs) |
| PASS | Check 6 — COPY COCOM01Y documented and present in source |
| PASS | Check 6 — COPY COUSR03 documented and present in source |
| PASS | Check 6 — COPY COTTL01Y documented and present in source |
| PASS | Check 6 — COPY CSDAT01Y documented and present in source |
| PASS | Check 6 — COPY CSMSG01Y documented and present in source |
| PASS | Check 6 — COPY CSUSR01Y documented and present in source |
| PASS | Check 6 — COPY DFHAID documented and present in source |
| PASS | Check 6 — COPY DFHBMSCA documented and present in source |
| PASS | Check 7 — XCTL target COSGN00C referenced in source |
| PASS | Check 7 — XCTL target COADM01C referenced in source |
| PASS | Check 7 — Dynamic XCTL via CDEMO-TO-PROGRAM confirmed in source |
| PASS | Check 7 — No LINK statements (consistent with analysis) |
| PASS | Check 8 — COADM01C admin menu table (COADM02Y) references COUSR03C |
| PASS | Check 8 — COUSR00C references COUSR03C (XCTL on 'D' selection) |
| PASS | Check 8 — COSGN00C references COADM01C (upstream chain verified) |


---

## Check Categories

### Check 1 — Program Existence
Verifies that every program listed in the analysis exists as a `.cbl` file in `app/cbl/`.

### Check 2 — Copybook Existence
Verifies that every application copybook exists in `app/cpy/`, BMS symbolic maps exist in `app/cpy-bms/`, and system copybooks (DFHAID, DFHBMSCA) are correctly flagged as IBM-supplied (not expected in app/cpy/).

### Check 3 — BMS Map Existence
Verifies that the BMS mapset source file exists in `app/bms/`.

### Check 4 — EXEC CICS Extraction
Verifies that every `EXEC CICS` command in COUSR03C.cbl is documented in the analysis, and no undocumented commands exist.

### Check 5 — CALL Extraction
Verifies that every `CALL` statement in the source is documented. COUSR03C has no CALL statements.

### Check 6 — COPY Extraction
Verifies that every `COPY` statement in COUSR03C.cbl is documented in the analysis, and no undocumented COPY statements exist.

### Check 7 — XCTL/LINK Target Verification
Verifies that all XCTL targets are included in the program inventory and that the dynamic XCTL pattern is correctly documented.

### Check 8 — Upstream Cross-Reference
Verifies that programs listed as upstream callers actually contain references to COUSR03C.

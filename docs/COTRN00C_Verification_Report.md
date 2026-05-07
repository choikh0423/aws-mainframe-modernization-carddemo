# COTRN00C (CT00) — Verification Report

**Generated:** 2026-05-07 21:31:21 UTC
**Target Program:** COTRN00C
**Transaction ID:** CT00
**Repository:** https://git-manager.devin.ai/proxy/github.com/choikh0423/aws-mainframe-modernization-carddemo

## Summary

| Metric        | Count |
|---------------|-------|
| Total Checks  | 40 |
| Passed        | 40 |
| Failed        | 0 |

**Result:** ALL CHECKS PASSED

## Detailed Results

| Check | Item | Status | Detail |
|-------|------|--------|--------|
| 1-Program | COTRN00C | PASS | Found at app/cbl/COTRN00C.cbl |
| 1-Program | COTRN01C | PASS | Found at app/cbl/COTRN01C.cbl |
| 1-Program | COMEN01C | PASS | Found at app/cbl/COMEN01C.cbl |
| 1-Program | COSGN00C | PASS | Found at app/cbl/COSGN00C.cbl |
| 2-Copybook | COCOM01Y | PASS | Found at app/cpy/COCOM01Y.cpy |
| 2-Copybook | COTTL01Y | PASS | Found at app/cpy/COTTL01Y.cpy |
| 2-Copybook | CSDAT01Y | PASS | Found at app/cpy/CSDAT01Y.cpy |
| 2-Copybook | CSMSG01Y | PASS | Found at app/cpy/CSMSG01Y.cpy |
| 2-Copybook | CVTRA05Y | PASS | Found at app/cpy/CVTRA05Y.cpy |
| 2-Copybook | COMEN02Y | PASS | Found at app/cpy/COMEN02Y.cpy |
| 2-Copybook | CSUSR01Y | PASS | Found at app/cpy/CSUSR01Y.cpy |
| 2-Copybook | DFHAID (system) | PASS | Correctly absent from app/cpy/ — system copybook |
| 2-Copybook | DFHBMSCA (system) | PASS | Correctly absent from app/cpy/ — system copybook |
| 3-BMS | COTRN00 | PASS | Found at app/bms/COTRN00.bms |
| 4-CICS-CMD | EXEC CICS ENDBR | PASS | Documented in analysis |
| 4-CICS-CMD | EXEC CICS READNEXT | PASS | Documented in analysis |
| 4-CICS-CMD | EXEC CICS READPREV | PASS | Documented in analysis |
| 4-CICS-CMD | EXEC CICS RECEIVE | PASS | Documented in analysis |
| 4-CICS-CMD | EXEC CICS RETURN | PASS | Documented in analysis |
| 4-CICS-CMD | EXEC CICS SEND | PASS | Documented in analysis |
| 4-CICS-CMD | EXEC CICS STARTBR | PASS | Documented in analysis |
| 5-CALL | No CALL statements | PASS | Source has 0 CALL statements, analysis does not document any |
| 6-COPY | COPY COCOM01Y | PASS | Documented in analysis |
| 6-COPY | COPY COTRN00 | PASS | Documented in analysis |
| 6-COPY | COPY COTTL01Y | PASS | Documented in analysis |
| 6-COPY | COPY CSDAT01Y | PASS | Documented in analysis |
| 6-COPY | COPY CSMSG01Y | PASS | Documented in analysis |
| 6-COPY | COPY CVTRA05Y | PASS | Documented in analysis |
| 6-COPY | COPY DFHAID | PASS | Documented in analysis |
| 6-COPY | COPY DFHBMSCA | PASS | Documented in analysis |
| 7-XCTL | COSGN00C | PASS | XCTL target exists at app/cbl/COSGN00C.cbl |
| 7-XCTL | COMEN01C | PASS | XCTL target exists at app/cbl/COMEN01C.cbl |
| 7-XCTL | COTRN01C | PASS | XCTL target exists at app/cbl/COTRN01C.cbl |
| 7-XCTL | COSGN00C (literal) | PASS | Literal 'COSGN00C' found in source |
| 7-XCTL | COMEN01C (literal) | PASS | Literal 'COMEN01C' found in source |
| 7-XCTL | COTRN01C (literal) | PASS | Literal 'COTRN01C' found in source |
| 8-XRef | COMEN01C->COTRN00C | PASS | COTRN00C found in COMEN02Y.cpy menu table |
| 8-XRef | COTRN01C->COTRN00C | PASS | Literal 'COTRN00C' found in COTRN01C.cbl |
| 8-XRef | COTRN00C->COTRN01C | PASS | Literal 'COTRN01C' found in COTRN00C.cbl |
| 8-XRef | COTRN00C->COMEN01C | PASS | Literal 'COMEN01C' found in COTRN00C.cbl |

## Check Descriptions

| Check # | Name | Description |
|---------|------|-------------|
| 1 | Program Existence | Every program listed in the analysis exists as a .cbl file in app/cbl/ |
| 2 | Copybook Existence | Every application copybook exists as .cpy in app/cpy/; system copybooks (DFHAID, DFHBMSCA) are correctly absent |
| 3 | BMS Map Existence | Every BMS mapset listed exists as a .bms file in app/bms/ |
| 4 | EXEC CICS Extraction | Every EXEC CICS command type in the target program source is documented |
| 5 | CALL Extraction | Every CALL statement in the target program is documented (none expected) |
| 6 | COPY Extraction | Every COPY statement in the target program is documented |
| 7 | XCTL/LINK Targets | Every program referenced by XCTL is in the inventory and confirmed in source |
| 8 | Upstream Cross-Ref | Programs listed as upstream actually contain references to the target |

# COADM01C (CA00) Flow Analysis — Verification Report

**Date:** 2026-05-07
**Script:** `verify_coadm01c_flow.sh`
**Repository:** choikh0423/aws-mainframe-modernization-carddemo
**Branch:** demos/cobol-full-docs

---

## Summary

| Metric         | Value |
|----------------|-------|
| Total Checks   | 57    |
| Passed         | 57    |
| Failed         | 0     |
| **Result**     | **ALL CHECKS PASSED** |

---

## Check 1 — Program Existence (8 checks)

Verifies every program listed in the analysis exists as a `.cbl` file, and programs flagged as missing are confirmed absent.

| Item                         | Status |
|------------------------------|--------|
| COADM01C.cbl exists          | PASS   |
| COSGN00C.cbl exists          | PASS   |
| COUSR00C.cbl exists          | PASS   |
| COUSR01C.cbl exists          | PASS   |
| COUSR02C.cbl exists          | PASS   |
| COUSR03C.cbl exists          | PASS   |
| COTRTLIC.cbl flagged not in repo | PASS |
| COTRTUPC.cbl flagged not in repo | PASS |

---

## Check 2 — Copybook Existence (9 checks)

Verifies every application copybook exists in `app/cpy/`, BMS symbolic maps are accounted for, and CICS system copybooks (DFHAID, DFHBMSCA) are correctly flagged as IBM-supplied.

| Item                                      | Status |
|-------------------------------------------|--------|
| COCOM01Y.cpy exists                       | PASS   |
| COADM02Y.cpy exists                       | PASS   |
| COTTL01Y.cpy exists                       | PASS   |
| CSDAT01Y.cpy exists                       | PASS   |
| CSMSG01Y.cpy exists                       | PASS   |
| CSUSR01Y.cpy exists                       | PASS   |
| COADM01 BMS symbolic map (auto-generated) | PASS   |
| DFHAID flagged as system copybook         | PASS   |
| DFHBMSCA flagged as system copybook       | PASS   |

**Note:** COADM01.cpy is a BMS-generated symbolic map copybook produced at compile time from `COADM01.bms`. It does not exist as a standalone `.cpy` file in the repository — the BMS source file `app/bms/COADM01.bms` was verified instead.

---

## Check 3 — BMS Map Existence (1 check)

| Item                         | Status |
|------------------------------|--------|
| COADM01.bms exists           | PASS   |

---

## Check 4 — EXEC CICS Command Extraction (6 checks)

Verifies the count and types of EXEC CICS commands in COADM01C match the analysis.

| Item                                  | Status |
|---------------------------------------|--------|
| Command count: 7 source = 7 analysis  | PASS   |
| EXEC CICS SEND documented             | PASS   |
| EXEC CICS RECEIVE documented          | PASS   |
| EXEC CICS XCTL documented             | PASS   |
| EXEC CICS HANDLE CONDITION documented | PASS   |
| EXEC CICS RETURN documented           | PASS   |

---

## Check 5 — CALL Statement Extraction (1 check)

| Item                                        | Status |
|---------------------------------------------|--------|
| No CALL statements in COADM01C (correct)    | PASS   |

---

## Check 6 — COPY Statement Extraction (18 checks)

Bidirectional verification: every COPY in source is documented, and every copybook in analysis is confirmed in source.

| Item                                         | Status |
|----------------------------------------------|--------|
| COPY COADM01 documented                     | PASS   |
| COPY COADM02Y documented                    | PASS   |
| COPY COCOM01Y documented                    | PASS   |
| COPY COTTL01Y documented                    | PASS   |
| COPY CSDAT01Y documented                    | PASS   |
| COPY CSMSG01Y documented                    | PASS   |
| COPY CSUSR01Y documented                    | PASS   |
| COPY DFHAID documented                      | PASS   |
| COPY DFHBMSCA documented                    | PASS   |
| Analysis: COCOM01Y confirmed in source      | PASS   |
| Analysis: COADM02Y confirmed in source      | PASS   |
| Analysis: COADM01 confirmed in source       | PASS   |
| Analysis: COTTL01Y confirmed in source      | PASS   |
| Analysis: CSDAT01Y confirmed in source      | PASS   |
| Analysis: CSMSG01Y confirmed in source      | PASS   |
| Analysis: CSUSR01Y confirmed in source      | PASS   |
| Analysis: DFHAID confirmed in source        | PASS   |
| Analysis: DFHBMSCA confirmed in source      | PASS   |

---

## Check 7 — XCTL/LINK Target Verification (8 checks)

Verifies all XCTL targets are documented, menu table entries match, and absence of LINK commands is correctly noted.

| Item                                          | Status |
|-----------------------------------------------|--------|
| XCTL commands present in COADM01C (2 found)  | PASS   |
| Menu target COUSR00C in COADM02Y             | PASS   |
| Menu target COUSR01C in COADM02Y             | PASS   |
| Menu target COUSR02C in COADM02Y             | PASS   |
| Menu target COUSR03C in COADM02Y             | PASS   |
| Menu target COTRTLIC in COADM02Y             | PASS   |
| Menu target COTRTUPC in COADM02Y             | PASS   |
| No LINK commands in COADM01C (correct)       | PASS   |

---

## Check 8 — Upstream Cross-Reference (6 checks)

Verifies upstream and downstream program references match the analysis claims.

| Item                                                  | Status |
|-------------------------------------------------------|--------|
| COSGN00C references COADM01C                         | PASS   |
| COSGN00C uses XCTL to COADM01C                      | PASS   |
| COUSR00C references COADM01C (return navigation)    | PASS   |
| COUSR01C references COADM01C (return navigation)    | PASS   |
| COUSR02C references COADM01C (return navigation)    | PASS   |
| COUSR03C references COADM01C (return navigation)    | PASS   |

---

## Conclusion

All 57 verification checks passed. The flow analysis document accurately reflects the repository contents:

- **6 programs** in the execution path (all source-verified), plus 2 DB2-only programs correctly flagged as absent
- **9 copybooks** referenced (7 application + 2 system), all accounted for
- **1 BMS mapset** (COADM01) with source verified
- **7 EXEC CICS commands** fully documented with parameters and error handling
- **0 CALL statements** (correctly noted — COADM01C is purely CICS)
- **Upstream/downstream navigation** cross-referenced against actual source code

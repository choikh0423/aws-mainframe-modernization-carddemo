# COTRN01C (CT01) Flow Analysis — Verification Report

## Summary

| Metric       | Value |
|-------------|-------|
| Total Checks | 33 |
| Passed       | 33 |
| Failed       | 0 |
| **Result**   | **ALL CHECKS PASSED** |

## Detailed Results

| # | Result | Check | Detail |
|---|--------|-------|--------|
| 1 | PASS | Program COTRN01C exists | COTRN01C.cbl found in app/cbl/ |
| 2 | PASS | Program COTRN00C exists | COTRN00C.cbl found in app/cbl/ |
| 3 | PASS | Program COMEN01C exists | COMEN01C.cbl found in app/cbl/ |
| 4 | PASS | Program COSGN00C exists | COSGN00C.cbl found in app/cbl/ |
| 5 | PASS | Copybook COCOM01Y exists | COCOM01Y.cpy found in app/cpy/ |
| 6 | PASS | Copybook COTTL01Y exists | COTTL01Y.cpy found in app/cpy/ |
| 7 | PASS | Copybook CSDAT01Y exists | CSDAT01Y.cpy found in app/cpy/ |
| 8 | PASS | Copybook CSMSG01Y exists | CSMSG01Y.cpy found in app/cpy/ |
| 9 | PASS | Copybook CVTRA05Y exists | CVTRA05Y.cpy found in app/cpy/ |
| 10 | PASS | BMS symbolic map copybook COTRN01 — generated from BMS source | COTRN01.cpy is BMS-generated; source COTRN01.bms exists |
| 11 | PASS | System copybook DFHAID flagged as CICS system | DFHAID is IBM CICS system-supplied — not expected in app/cpy/ |
| 12 | PASS | System copybook DFHBMSCA flagged as CICS system | DFHBMSCA is IBM CICS system-supplied — not expected in app/cpy/ |
| 13 | PASS | BMS mapset COTRN01 exists | COTRN01.bms found in app/bms/ |
| 14 | PASS | All EXEC CICS commands documented | Found 5, documented 5 |
| 15 | PASS | EXEC CICS SEND documented | 9 occurrence(s) found in source |
| 16 | PASS | EXEC CICS RECEIVE documented | 3 occurrence(s) found in source |
| 17 | PASS | EXEC CICS READ documented | 3 occurrence(s) found in source |
| 18 | PASS | EXEC CICS XCTL documented | 1 occurrence(s) found in source |
| 19 | PASS | EXEC CICS RETURN documented | 5 occurrence(s) found in source |
| 20 | PASS | No CALL statements in COTRN01C | Analysis correctly states no CALL statements |
| 21 | PASS | COPY COCOM01Y documented in analysis | Found in both source and analysis |
| 22 | PASS | COPY COTRN01 documented in analysis | Found in both source and analysis |
| 23 | PASS | COPY COTTL01Y documented in analysis | Found in both source and analysis |
| 24 | PASS | COPY CSDAT01Y documented in analysis | Found in both source and analysis |
| 25 | PASS | COPY CSMSG01Y documented in analysis | Found in both source and analysis |
| 26 | PASS | COPY CVTRA05Y documented in analysis | Found in both source and analysis |
| 27 | PASS | COPY DFHAID documented in analysis | Found in both source and analysis |
| 28 | PASS | COPY DFHBMSCA documented in analysis | Found in both source and analysis |
| 29 | PASS | XCTL target COMEN01C in program inventory | Listed in analysis |
| 30 | PASS | XCTL target COSGN00C in program inventory | Listed in analysis |
| 31 | PASS | XCTL target COTRN00C in program inventory | Listed in analysis |
| 32 | PASS | Upstream COTRN00C references COTRN01C | 1 reference(s) found |
| 33 | PASS | Menu table (COMEN02Y) lists COTRN01C | Option 7 maps to COTRN01C |

## Check Descriptions

| Check | Description |
|-------|-------------|
| 1 — Program existence | Every program listed in the analysis exists as a `.cbl` file in `app/cbl/` |
| 2 — Copybook existence | Every copybook listed exists as a `.cpy` file in `app/cpy/` (system copybooks exempted) |
| 3 — BMS map existence | Every BMS mapset listed exists as a `.bms` file in `app/bms/` |
| 4 — EXEC CICS extraction | Every `EXEC CICS` command in COTRN01C is documented in the analysis |
| 5 — CALL extraction | Every `CALL` statement in COTRN01C is documented (or correctly noted as absent) |
| 6 — COPY extraction | Every `COPY` statement in COTRN01C is documented in the analysis |
| 7 — XCTL/LINK targets | Every program referenced by XCTL is included in the program inventory |
| 8 — Cross-reference | Programs listed as upstream actually contain references to COTRN01C |

## Verification Environment

- **Repository:** choikh0423/aws-mainframe-modernization-carddemo
- **Branch:** demos/cobol-full-docs
- **Target Program:** COTRN01C (Transaction ID: CT01)
- **Analysis File:** docs/COTRN01C_Flow_Analysis.md
- **Date:** 2026-05-07 21:32:35 UTC

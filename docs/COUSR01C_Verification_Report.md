# COUSR01C (CU01) Flow Analysis — Verification Report

**Date:** 2026-05-07 21:31 UTC
**Repository:** choikh0423/aws-mainframe-modernization-carddemo
**Branch:** demos/cobol-full-docs
**Target Program:** COUSR01C
**Transaction ID:** CU01

---

## Summary

| Metric         | Value |
|----------------|-------|
| Total Checks   | 53    |
| Passed         | 53    |
| Failed         | 0     |
| **Result**     | **ALL CHECKS PASSED** |

---

## Check 1: Program Existence (3 checks)

All programs listed in the flow analysis exist as `.cbl` files in `app/cbl/`.

| Program    | File Path                  | Status |
|------------|----------------------------|--------|
| COUSR01C   | `app/cbl/COUSR01C.cbl`     | PASS   |
| COADM01C   | `app/cbl/COADM01C.cbl`     | PASS   |
| COSGN00C   | `app/cbl/COSGN00C.cbl`     | PASS   |

## Check 2: Copybook Existence (9 checks)

All application copybooks exist in their documented locations. System copybooks are correctly flagged as IBM-supplied.

| Copybook   | Location            | Type                  | Status |
|------------|---------------------|-----------------------|--------|
| COCOM01Y   | `app/cpy/`          | Application           | PASS   |
| COTTL01Y   | `app/cpy/`          | Application           | PASS   |
| CSDAT01Y   | `app/cpy/`          | Application           | PASS   |
| CSMSG01Y   | `app/cpy/`          | Application           | PASS   |
| CSUSR01Y   | `app/cpy/`          | Application           | PASS   |
| COADM02Y   | `app/cpy/`          | Application           | PASS   |
| COUSR01    | `app/cpy-bms/`      | BMS symbolic map      | PASS   |
| DFHAID     | (system)            | CICS system copybook  | PASS   |
| DFHBMSCA   | (system)            | CICS system copybook  | PASS   |

## Check 3: BMS Map Existence (1 check)

| Mapset     | File Path                  | Status |
|------------|----------------------------|--------|
| COUSR01    | `app/bms/COUSR01.bms`      | PASS   |

## Check 4: EXEC CICS Command Extraction (9 checks)

Every EXEC CICS command found in COUSR01C.cbl is documented in the analysis. No undocumented CICS commands were found.

| CICS Command      | In Source | In Analysis | Status |
|--------------------|-----------|-------------|--------|
| SEND MAP           | Yes       | Yes         | PASS   |
| RECEIVE MAP        | Yes       | Yes         | PASS   |
| WRITE              | Yes       | Yes         | PASS   |
| XCTL               | Yes       | Yes         | PASS   |
| RETURN TRANSID     | Yes       | Yes         | PASS   |
| Verb: RECEIVE      | Yes       | Yes         | PASS   |
| Verb: RETURN       | Yes       | Yes         | PASS   |
| Verb: SEND         | Yes       | Yes         | PASS   |
| Verb: WRITE        | Yes       | Yes         | PASS   |

## Check 5: CALL Statement Extraction (1 check)

| Check                                | Result | Status |
|--------------------------------------|--------|--------|
| No CALL statements in COUSR01C       | 0 found | PASS  |

The analysis correctly states that COUSR01C contains no CALL statements.

## Check 6: COPY Statement Extraction (16 checks)

Every COPY statement in COUSR01C.cbl is documented, and every documented copybook appears in the source.

| COPY Statement | In Source | In Analysis | Status |
|----------------|-----------|-------------|--------|
| COCOM01Y       | Yes       | Yes         | PASS   |
| COUSR01        | Yes       | Yes         | PASS   |
| COTTL01Y       | Yes       | Yes         | PASS   |
| CSDAT01Y       | Yes       | Yes         | PASS   |
| CSMSG01Y       | Yes       | Yes         | PASS   |
| CSUSR01Y       | Yes       | Yes         | PASS   |
| DFHAID         | Yes       | Yes         | PASS   |
| DFHBMSCA       | Yes       | Yes         | PASS   |

(Bidirectional verification: source-to-analysis and analysis-to-source = 16 checks total.)

## Check 7: XCTL/LINK Target Programs (5 checks)

All XCTL targets referenced in COUSR01C are included in the program inventory and have source files. No LINK commands exist (matching the analysis).

| Check                                           | Status |
|--------------------------------------------------|--------|
| XCTL target COADM01C referenced in source        | PASS   |
| XCTL target COSGN00C referenced in source        | PASS   |
| COADM01C source exists in `app/cbl/`             | PASS   |
| COSGN00C source exists in `app/cbl/`             | PASS   |
| No LINK commands in COUSR01C                      | PASS   |

## Check 8: Upstream Cross-Reference (9 checks)

Programs documented as upstream callers actually contain references to their downstream targets.

| Check                                                       | Status |
|--------------------------------------------------------------|--------|
| COADM01C/COADM02Y references COUSR01C                       | PASS   |
| COSGN00C references COADM01C (admin routing)                | PASS   |
| COUSR01C references COADM01C (PF3 back navigation)          | PASS   |
| Transaction ID CU01 found in COUSR01C source                | PASS   |
| USRSEC file reference found in COUSR01C source               | PASS   |
| CSD DEFINE TRANSACTION(CU01) exists                          | PASS   |
| CSD DEFINE PROGRAM(COUSR01C) exists                          | PASS   |
| CSD DEFINE MAPSET(COUSR01) exists                            | PASS   |
| CSD DEFINE FILE(USRSEC) exists                               | PASS   |

---

## Inventory Totals

| Category                  | Count |
|---------------------------|-------|
| COBOL programs in flow    | 3     |
| Application copybooks     | 6     |
| BMS symbolic map copybooks| 1     |
| System copybooks          | 2     |
| BMS map sources           | 1     |
| VSAM files accessed       | 1     |
| CSD resource definitions  | 4     |
| Lines of COBOL (target)   | 300   |

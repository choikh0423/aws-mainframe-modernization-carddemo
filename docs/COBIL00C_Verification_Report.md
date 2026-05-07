# COBIL00C (CB00) Flow Analysis — Verification Report

**Date:** 2026-05-07
**Target Program:** COBIL00C
**Transaction ID:** CB00
**Description:** Bill Payment

---

## Verification Summary

| Metric         | Value |
|----------------|-------|
| **Total Checks** | 71  |
| **Passed**       | 71  |
| **Failed**       | 0   |
| **Result**       | **ALL CHECKS PASSED** |

---

## Check 1: Program Existence (3 checks)

Every program listed in the analysis exists as a `.cbl` file in `app/cbl/`.

| Program  | Status |
|----------|--------|
| COBIL00C | PASS   |
| COMEN01C | PASS   |
| COSGN00C | PASS   |

## Check 2: Copybook Existence (11 checks)

All application copybooks exist in `app/cpy/`. BMS symbolic map and CICS system copybooks are correctly flagged as not present in the application copybook directory.

| Copybook   | Type                | Status |
|------------|---------------------|--------|
| COCOM01Y   | Application         | PASS   |
| COTTL01Y   | Application         | PASS   |
| CSDAT01Y   | Application         | PASS   |
| CSMSG01Y   | Application         | PASS   |
| CVACT01Y   | Application         | PASS   |
| CVACT03Y   | Application         | PASS   |
| CVTRA05Y   | Application         | PASS   |
| COMEN02Y   | Application         | PASS   |
| COBIL00    | BMS symbolic map    | PASS (correctly flagged as auto-generated, not in app/cpy/) |
| DFHAID     | CICS system         | PASS (correctly flagged as system copybook) |
| DFHBMSCA   | CICS system         | PASS (correctly flagged as system copybook) |

## Check 3: BMS Map Existence (1 check)

| Mapset       | Status |
|--------------|--------|
| COBIL00.bms  | PASS   |

## Check 4: EXEC CICS Command Extraction (25 checks)

Every EXEC CICS command found in `COBIL00C.cbl` source is documented in the analysis, and every command documented in the analysis exists in the source.

| Command     | In Source | In Analysis | Status |
|-------------|-----------|-------------|--------|
| ASKTIME     | Yes       | Yes         | PASS   |
| ENDBR       | Yes       | Yes         | PASS   |
| FORMATTIME  | Yes       | Yes         | PASS   |
| READ (x2)   | Yes       | Yes         | PASS   |
| READPREV    | Yes       | Yes         | PASS   |
| RECEIVE     | Yes       | Yes         | PASS   |
| RETURN      | Yes       | Yes         | PASS   |
| REWRITE     | Yes       | Yes         | PASS   |
| SEND        | Yes       | Yes         | PASS   |
| STARTBR     | Yes       | Yes         | PASS   |
| WRITE       | Yes       | Yes         | PASS   |
| XCTL        | Yes       | Yes         | PASS   |

## Check 5: CALL Statement Extraction (1 check)

| Check                                      | Status |
|--------------------------------------------|--------|
| No CALL statements in source (0 documented)| PASS   |

## Check 6: COPY Statement Extraction (20 checks)

Every COPY statement in `COBIL00C.cbl` is documented, and every documented copybook is found in the source.

| Copybook   | In Source | In Analysis | Status |
|------------|-----------|-------------|--------|
| COCOM01Y   | Yes       | Yes         | PASS   |
| COBIL00    | Yes       | Yes         | PASS   |
| COTTL01Y   | Yes       | Yes         | PASS   |
| CSDAT01Y   | Yes       | Yes         | PASS   |
| CSMSG01Y   | Yes       | Yes         | PASS   |
| CVACT01Y   | Yes       | Yes         | PASS   |
| CVACT03Y   | Yes       | Yes         | PASS   |
| CVTRA05Y   | Yes       | Yes         | PASS   |
| DFHAID     | Yes       | Yes         | PASS   |
| DFHBMSCA   | Yes       | Yes         | PASS   |

## Check 7: XCTL/LINK Targets (5 checks)

| Check                                        | Status |
|----------------------------------------------|--------|
| XCTL command found in COBIL00C source        | PASS   |
| XCTL target variable CDEMO-TO-PROGRAM found  | PASS   |
| XCTL resolved target COSGN00C exists         | PASS   |
| XCTL resolved target COMEN01C exists         | PASS   |
| No LINK statements in COBIL00C               | PASS   |

## Check 8: Upstream Cross-Reference (5 checks)

| Check                                          | Status |
|------------------------------------------------|--------|
| COMEN02Y menu table contains COBIL00C          | PASS   |
| COMEN01C contains XCTL (transfers to options)  | PASS   |
| COSGN00C references COMEN01C (XCTL target)     | PASS   |
| CSD defines TRANSACTION(CB00)                  | PASS   |
| CSD maps CB00 to PROGRAM(COBIL00C)             | PASS   |

---

## Inventory Totals

| Category               | Count |
|------------------------|-------|
| Programs in flow       | 3     |
| Application copybooks  | 8     |
| BMS symbolic maps      | 1 (auto-generated) |
| CICS system copybooks  | 2     |
| BMS map files          | 1     |
| VSAM files accessed    | 3     |
| EXEC CICS commands     | 13    |
| CALL sub-programs      | 0     |
| LINK sub-programs      | 0     |

---

## Conclusion

The COBIL00C (CB00) transaction flow analysis passes all 71 verification checks with 0 failures. All programs, copybooks, BMS maps, EXEC CICS commands, COPY statements, XCTL targets, and upstream cross-references are fully consistent between the analysis document and the actual repository contents.

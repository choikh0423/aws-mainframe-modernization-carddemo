# COUSR02C (CU02) Flow Analysis — Verification Report

| Attribute        | Value                                                    |
|------------------|----------------------------------------------------------|
| **Program**      | COUSR02C                                                 |
| **Transaction**  | CU02 — User Update (Security/Admin)                      |
| **Date**         | 2026-05-07                                               |
| **Script**       | `docs/verify_cousr02c_flow.sh`                           |
| **Result**       | **ALL 48 CHECKS PASSED — 0 FAILURES**                   |

---

## Check 1: Program Existence

| Program    | File                  | Status |
|------------|-----------------------|--------|
| COUSR02C   | `app/cbl/COUSR02C.cbl` | PASS   |
| COUSR00C   | `app/cbl/COUSR00C.cbl` | PASS   |
| COADM01C   | `app/cbl/COADM01C.cbl` | PASS   |
| COSGN00C   | `app/cbl/COSGN00C.cbl` | PASS   |

## Check 2: Copybook Existence

| Copybook   | File                    | Type              | Status |
|------------|-------------------------|-------------------|--------|
| COCOM01Y   | `app/cpy/COCOM01Y.cpy`  | COMMAREA          | PASS   |
| COTTL01Y   | `app/cpy/COTTL01Y.cpy`  | Screen titles     | PASS   |
| CSDAT01Y   | `app/cpy/CSDAT01Y.cpy`  | Date/time         | PASS   |
| CSMSG01Y   | `app/cpy/CSMSG01Y.cpy`  | Messages          | PASS   |
| CSUSR01Y   | `app/cpy/CSUSR01Y.cpy`  | USRSEC record     | PASS   |
| COUSR02    | *(BMS auto-generated)*  | BMS symbolic map  | INFO   |
| DFHAID     | *(IBM CICS system)*     | AID key constants | INFO   |
| DFHBMSCA   | *(IBM CICS system)*     | BMS attributes    | INFO   |

## Check 3: BMS Map Existence

| Mapset   | File                    | Status |
|----------|-------------------------|--------|
| COUSR02  | `app/bms/COUSR02.bms`   | PASS   |

## Check 4: EXEC CICS Command Extraction

| Command      | In Source | In Analysis | Status |
|-------------|-----------|-------------|--------|
| SEND MAP    | Yes       | Yes         | PASS   |
| RECEIVE MAP | Yes       | Yes         | PASS   |
| READ        | Yes       | Yes         | PASS   |
| REWRITE     | Yes       | Yes         | PASS   |
| RETURN      | Yes       | Yes         | PASS   |
| XCTL        | Yes       | Yes         | PASS   |

**Bidirectional check:** All 6 source commands documented; all 6 documented commands verified in source.

## Check 5: CALL Statement Extraction

| Finding                                    | Status |
|--------------------------------------------|--------|
| 0 CALL statements in source; 0 in analysis | PASS   |

## Check 6: COPY Statement Extraction

| Copybook   | In Source | In Analysis | Status |
|------------|-----------|-------------|--------|
| COCOM01Y   | Yes       | Yes         | PASS   |
| COUSR02    | Yes       | Yes         | PASS   |
| COTTL01Y   | Yes       | Yes         | PASS   |
| CSDAT01Y   | Yes       | Yes         | PASS   |
| CSMSG01Y   | Yes       | Yes         | PASS   |
| CSUSR01Y   | Yes       | Yes         | PASS   |
| DFHAID     | Yes       | Yes         | PASS   |
| DFHBMSCA   | Yes       | Yes         | PASS   |

**Bidirectional check:** All 8 source COPY statements documented; all 8 documented COPYs verified in source.

## Check 7: XCTL/LINK Target Verification

| Target Program | Resolved From              | File Exists | Status |
|----------------|----------------------------|-------------|--------|
| COADM01C       | PF3 default / PF12 literal | Yes         | PASS   |
| COSGN00C       | EIBCALEN=0 guard           | Yes         | PASS   |

## Check 8: Upstream Cross-Reference

| Check Description                                          | Status |
|------------------------------------------------------------|--------|
| COUSR00C.cbl references COUSR02C                           | PASS   |
| COADM02Y.cpy (admin menu table) references COUSR00C       | PASS   |
| COADM01C.cbl uses CDEMO-ADMIN-OPT-PGMNAME for dynamic XCTL| PASS   |
| COSGN00C.cbl references COADM01C                           | PASS   |
| CSD defines TRANSACTION(CU02)                              | PASS   |
| CSD defines PROGRAM(COUSR02C)                              | PASS   |
| CSD defines MAPSET(COUSR02)                                | PASS   |

---

## Summary

```
  Total checks:  48
  Passed:        48
  Failed:         0

  RESULT: ALL CHECKS PASSED
```

# COTRN02C (CT02) — Verification Report

**Date**: 2026-05-07 21:34:26 UTC
**Target Program**: COTRN02C
**Transaction ID**: CT02

## Summary

| Metric        | Count |
|---------------|-------|
| Total Checks  | 46 |
| Passed        | 46  |
| Failed        | 0  |

## Detailed Results

| Result | Check | Detail |
|--------|-------|--------|
| PASS | Program-Exist | COTRN02C found in app/cbl/ |
| PASS | Program-Exist | COMEN01C found in app/cbl/ |
| PASS | Program-Exist | COSGN00C found in app/cbl/ |
| PASS | Program-Exist | CSUTLDTC found in app/cbl/ |
| PASS | Program-Exist | CEEDAYS — IBM Language Environment intrinsic (external, not in repo) |
| PASS | Copybook-Exist | COCOM01Y found in app/cpy/ |
| PASS | Copybook-Exist | COTTL01Y found in app/cpy/ |
| PASS | Copybook-Exist | CSDAT01Y found in app/cpy/ |
| PASS | Copybook-Exist | CSMSG01Y found in app/cpy/ |
| PASS | Copybook-Exist | CVTRA05Y found in app/cpy/ |
| PASS | Copybook-Exist | CVACT01Y found in app/cpy/ |
| PASS | Copybook-Exist | CVACT03Y found in app/cpy/ |
| PASS | Copybook-Exist | COMEN02Y found in app/cpy/ |
| PASS | Copybook-Exist | CSUSR01Y found in app/cpy/ |
| PASS | Copybook-Exist | COTRN02 — BMS-generated symbolic map (source: app/bms/COTRN02.bms exists) |
| PASS | Copybook-System | DFHAID — IBM CICS system copybook (not expected in app/cpy/) |
| PASS | Copybook-System | DFHBMSCA — IBM CICS system copybook (not expected in app/cpy/) |
| PASS | BMS-Exist | COTRN02.bms found in app/bms/ |
| PASS | CICS-Cmd | EXEC CICS ENDBR documented in analysis |
| PASS | CICS-Cmd | EXEC CICS READ documented in analysis |
| PASS | CICS-Cmd | EXEC CICS READ documented in analysis |
| PASS | CICS-Cmd | EXEC CICS READPREV documented in analysis |
| PASS | CICS-Cmd | EXEC CICS RECEIVE documented in analysis |
| PASS | CICS-Cmd | EXEC CICS RETURN documented in analysis |
| PASS | CICS-Cmd | EXEC CICS RETURN documented in analysis |
| PASS | CICS-Cmd | EXEC CICS SEND documented in analysis |
| PASS | CICS-Cmd | EXEC CICS STARTBR documented in analysis |
| PASS | CICS-Cmd | EXEC CICS WRITE documented in analysis |
| PASS | CALL-Stmt | CALL 'CSUTLDTC' documented in analysis |
| PASS | CALL-Stmt | CSUTLDTC calls CEEDAYS (LE intrinsic) — documented |
| PASS | COPY-Stmt | COPY COCOM01Y documented in analysis |
| PASS | COPY-Stmt | COPY COTRN02 documented in analysis |
| PASS | COPY-Stmt | COPY COTTL01Y documented in analysis |
| PASS | COPY-Stmt | COPY CSDAT01Y documented in analysis |
| PASS | COPY-Stmt | COPY CSMSG01Y documented in analysis |
| PASS | COPY-Stmt | COPY CVACT01Y documented in analysis |
| PASS | COPY-Stmt | COPY CVACT03Y documented in analysis |
| PASS | COPY-Stmt | COPY CVTRA05Y documented in analysis |
| PASS | COPY-Stmt | COPY DFHAID documented in analysis |
| PASS | COPY-Stmt | COPY DFHBMSCA documented in analysis |
| PASS | XCTL-Target | XCTL target COSGN00C exists in program inventory |
| PASS | XCTL-Target | XCTL target COMEN01C exists in program inventory |
| PASS | XCTL-Target | XCTL uses variable CDEMO-TO-PROGRAM — resolved to COSGN00C/COMEN01C |
| PASS | Upstream-Xref | COMEN01C references COTRN02C via COMEN02Y menu table |
| PASS | Upstream-Xref | COMEN01C uses XCTL PROGRAM(CDEMO-MENU-OPT-PGMNAME) for dispatch |
| PASS | Upstream-Xref | COSGN00C references COMEN01C (XCTL for regular users) |


## Check Descriptions

| Check # | Name              | Description                                                        |
|---------|-------------------|--------------------------------------------------------------------|
| 1       | Program-Exist     | Every program in the analysis exists as a .cbl file in app/cbl/    |
| 2       | Copybook-Exist    | Every copybook exists as a .cpy file in app/cpy/ (or flagged system)|
| 3       | BMS-Exist         | Every BMS mapset exists as a .bms file in app/bms/                 |
| 4       | CICS-Cmd          | Every EXEC CICS command in source is documented in the analysis    |
| 5       | CALL-Stmt         | Every CALL statement in source is documented in the analysis       |
| 6       | COPY-Stmt         | Every COPY statement in source is documented in the analysis       |
| 7       | XCTL-Target       | Every XCTL/LINK target program is included in the program inventory|
| 8       | Upstream-Xref     | Upstream programs actually contain references to the target        |

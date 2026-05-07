#!/usr/bin/env bash
###############################################################################
# verify_cocrdupc_flow.sh
# Verification script for COCRDUPC (CCUP) Transaction Flow Analysis
#
# Checks that every program, copybook, BMS map, EXEC CICS command, CALL,
# COPY statement, and XCTL/LINK target documented in the analysis actually
# exists in the repository and source code.
###############################################################################

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
CPY_BMS_DIR="$REPO_ROOT/app/cpy-bms"
BMS_DIR="$REPO_ROOT/app/bms"
TARGET_PGM="COCRDUPC"
TARGET_SRC="$CBL_DIR/${TARGET_PGM}.cbl"

PASS=0
FAIL=0
TOTAL=0
REPORT=""

check() {
    local description="$1"
    local result="$2"  # PASS or FAIL
    local detail="$3"
    TOTAL=$((TOTAL + 1))
    if [ "$result" = "PASS" ]; then
        PASS=$((PASS + 1))
        REPORT+="| $TOTAL | PASS | $description | $detail |"$'\n'
    else
        FAIL=$((FAIL + 1))
        REPORT+="| $TOTAL | **FAIL** | $description | $detail |"$'\n'
    fi
}

echo "============================================================"
echo "COCRDUPC (CCUP) Flow Analysis Verification"
echo "Repository: $REPO_ROOT"
echo "============================================================"
echo ""

###############################################################################
# CHECK 1: Program Existence
# Every program listed in the analysis exists as a .cbl file
###############################################################################
echo "--- Check 1: Program Existence ---"

PROGRAMS=("COCRDUPC" "COCRDLIC" "COMEN01C" "COSGN00C" "COACTVWC" "COACTUPC" "COCRDSLC")

for pgm in "${PROGRAMS[@]}"; do
    if [ -f "$CBL_DIR/${pgm}.cbl" ]; then
        check "Program $pgm exists" "PASS" "$CBL_DIR/${pgm}.cbl found"
    else
        check "Program $pgm exists" "FAIL" "$CBL_DIR/${pgm}.cbl NOT found"
    fi
done

###############################################################################
# CHECK 2: Copybook Existence
# Every copybook listed exists as a .cpy file (except DFHAID, DFHBMSCA)
###############################################################################
echo "--- Check 2: Copybook Existence ---"

# Application copybooks in app/cpy/
APP_COPYBOOKS=("COCOM01Y" "CVCRD01Y" "COTTL01Y" "CSDAT01Y" "CSMSG01Y" "CSMSG02Y" "CSUSR01Y" "CVACT02Y" "CVCUS01Y" "CSSTRPFY" "COMEN02Y")

for cpb in "${APP_COPYBOOKS[@]}"; do
    # Check both .cpy and .CPY
    if [ -f "$CPY_DIR/${cpb}.cpy" ] || [ -f "$CPY_DIR/${cpb}.CPY" ]; then
        check "Copybook $cpb exists in app/cpy" "PASS" "Found"
    else
        check "Copybook $cpb exists in app/cpy" "FAIL" "NOT found in $CPY_DIR"
    fi
done

# BMS symbolic map copybook in app/cpy-bms/
BMS_COPYBOOKS=("COCRDUP")
for cpb in "${BMS_COPYBOOKS[@]}"; do
    if [ -f "$CPY_BMS_DIR/${cpb}.CPY" ] || [ -f "$CPY_BMS_DIR/${cpb}.cpy" ]; then
        check "BMS copybook $cpb exists in app/cpy-bms" "PASS" "Found"
    else
        check "BMS copybook $cpb exists in app/cpy-bms" "FAIL" "NOT found in $CPY_BMS_DIR"
    fi
done

# System copybooks - verify they are flagged as system (should NOT exist in app/cpy)
SYSTEM_COPYBOOKS=("DFHAID" "DFHBMSCA")
for cpb in "${SYSTEM_COPYBOOKS[@]}"; do
    if [ ! -f "$CPY_DIR/${cpb}.cpy" ] && [ ! -f "$CPY_DIR/${cpb}.CPY" ]; then
        check "System copybook $cpb correctly flagged (not in app/cpy)" "PASS" "Confirmed as system copybook"
    else
        check "System copybook $cpb correctly flagged (not in app/cpy)" "FAIL" "Found in app/cpy — should be system"
    fi
done

###############################################################################
# CHECK 3: BMS Map Existence
# Every BMS mapset listed exists as a .bms file
###############################################################################
echo "--- Check 3: BMS Map Existence ---"

BMS_MAPS=("COCRDUP")

for bms in "${BMS_MAPS[@]}"; do
    if [ -f "$BMS_DIR/${bms}.bms" ]; then
        check "BMS mapset $bms exists" "PASS" "$BMS_DIR/${bms}.bms found"
    else
        check "BMS mapset $bms exists" "FAIL" "$BMS_DIR/${bms}.bms NOT found"
    fi
done

###############################################################################
# CHECK 4: EXEC CICS Extraction
# Every EXEC CICS command type in the target program is documented
###############################################################################
echo "--- Check 4: EXEC CICS Command Extraction ---"

# Extract all unique EXEC CICS command types from source
# Pattern: EXEC CICS <COMMAND>
CICS_CMDS_IN_SRC=$(grep -oP 'EXEC\s+CICS\s+\K[A-Z]+' "$TARGET_SRC" | sort -u)

# Commands documented in the analysis
DOCUMENTED_CMDS=("HANDLE" "SYNCPOINT" "XCTL" "RETURN" "RECEIVE" "SEND" "READ" "REWRITE" "ABEND")

for cmd in $CICS_CMDS_IN_SRC; do
    found=0
    for doc_cmd in "${DOCUMENTED_CMDS[@]}"; do
        if [ "$cmd" = "$doc_cmd" ]; then
            found=1
            break
        fi
    done
    if [ $found -eq 1 ]; then
        check "EXEC CICS $cmd documented" "PASS" "Found in analysis"
    else
        check "EXEC CICS $cmd documented" "FAIL" "Present in source but NOT in analysis"
    fi
done

###############################################################################
# CHECK 5: CALL Extraction
# Every CALL statement in the target program is documented
###############################################################################
echo "--- Check 5: CALL Statement Extraction ---"

CALL_COUNT=$(grep -cP '^\s{6}\s+CALL\s' "$TARGET_SRC" 2>/dev/null || true)
CALL_COUNT=${CALL_COUNT:-0}

if [ "$CALL_COUNT" -eq 0 ]; then
    check "No CALL statements in $TARGET_PGM" "PASS" "Confirmed: 0 CALL statements found (as documented)"
else
    check "CALL statements documented" "FAIL" "$CALL_COUNT CALL statement(s) found but analysis says none"
fi

###############################################################################
# CHECK 6: COPY Extraction
# Every COPY statement in the target program is documented
###############################################################################
echo "--- Check 6: COPY Statement Extraction ---"

# Extract all COPY targets from source (excluding comment lines starting with *)
COPY_TARGETS=$(grep -P '^\s{6}\s+COPY\s' "$TARGET_SRC" | grep -v '^\s*\*' | sed "s/.*COPY\s\+['\"]*//" | sed "s/['\"].*//;s/\..*//;s/\s*$//" | sort -u)

# Documented copybooks (including system ones)
ALL_DOCUMENTED=("COCOM01Y" "CVCRD01Y" "COTTL01Y" "COCRDUP" "CSDAT01Y" "CSMSG01Y" "CSMSG02Y" "CSUSR01Y" "CVACT02Y" "CVCUS01Y" "CSSTRPFY" "DFHAID" "DFHBMSCA")

for target in $COPY_TARGETS; do
    found=0
    for doc in "${ALL_DOCUMENTED[@]}"; do
        if [ "$target" = "$doc" ]; then
            found=1
            break
        fi
    done
    if [ $found -eq 1 ]; then
        check "COPY $target documented" "PASS" "Found in analysis"
    else
        check "COPY $target documented" "FAIL" "Present in source but NOT in analysis"
    fi
done

###############################################################################
# CHECK 7: XCTL/LINK Targets
# Every program referenced by XCTL or LINK is included in the program inventory
###############################################################################
echo "--- Check 7: XCTL/LINK Target Resolution ---"

# In COCRDUPC, the XCTL target is CDEMO-TO-PROGRAM (a variable)
# Trace what values it can take:
# - CDEMO-FROM-PROGRAM (return to caller)
# - LIT-MENUPGM = 'COMEN01C' (default return)

# Check that XCTL exists in the program
XCTL_COUNT=$(grep -c 'EXEC CICS XCTL' "$TARGET_SRC" 2>/dev/null || true)
XCTL_COUNT=${XCTL_COUNT:-0}
if [ "$XCTL_COUNT" -gt 0 ]; then
    check "XCTL commands found in $TARGET_PGM" "PASS" "$XCTL_COUNT XCTL command(s)"
else
    check "XCTL commands found in $TARGET_PGM" "FAIL" "No XCTL found but analysis documents one"
fi

# Check the XCTL target variable resolves to documented programs
# The variable CDEMO-TO-PROGRAM is set to either CDEMO-FROM-PROGRAM or LIT-MENUPGM
if grep -q "LIT-MENUPGM" "$TARGET_SRC" && grep -q "'COMEN01C'" "$TARGET_SRC"; then
    check "XCTL target LIT-MENUPGM='COMEN01C' documented" "PASS" "COMEN01C is in program inventory"
else
    check "XCTL target LIT-MENUPGM='COMEN01C' documented" "FAIL" "Could not verify"
fi

# Check that CDEMO-FROM-PROGRAM is used as XCTL target
if grep -q "CDEMO-FROM-PROGRAM.*TO.*CDEMO-TO-PROGRAM" "$TARGET_SRC"; then
    check "XCTL dynamic target CDEMO-FROM-PROGRAM traced" "PASS" "Resolves to calling program"
else
    check "XCTL dynamic target CDEMO-FROM-PROGRAM traced" "FAIL" "Could not verify variable resolution"
fi

# Verify no LINK commands (analysis says none)
LINK_COUNT=$(grep -c 'EXEC CICS LINK' "$TARGET_SRC" 2>/dev/null || true)
LINK_COUNT=${LINK_COUNT:-0}
if [ "$LINK_COUNT" -eq 0 ]; then
    check "No LINK commands in $TARGET_PGM (as documented)" "PASS" "Confirmed"
else
    check "No LINK commands in $TARGET_PGM (as documented)" "FAIL" "$LINK_COUNT LINK command(s) found"
fi

###############################################################################
# CHECK 8: Cross-Reference — Upstream Programs
# Programs listed as upstream actually contain XCTL/LINK references to target
###############################################################################
echo "--- Check 8: Upstream Cross-Reference ---"

# COCRDLIC should reference COCRDUPC
if grep -q "COCRDUPC" "$CBL_DIR/COCRDLIC.cbl" 2>/dev/null; then
    check "COCRDLIC references COCRDUPC" "PASS" "Found literal 'COCRDUPC' in COCRDLIC.cbl"
else
    check "COCRDLIC references COCRDUPC" "FAIL" "No reference to COCRDUPC found"
fi

# COMEN01C should reference COCRDUPC via menu table (in COMEN02Y)
if grep -q "COCRDUPC" "$CPY_DIR/COMEN02Y.cpy" 2>/dev/null; then
    check "COMEN01C menu table includes COCRDUPC" "PASS" "Found 'COCRDUPC' in COMEN02Y.cpy (option 5)"
else
    check "COMEN01C menu table includes COCRDUPC" "FAIL" "No reference to COCRDUPC in COMEN02Y.cpy"
fi

# COACTVWC should reference COCRDUPC
if grep -q "COCRDUPC" "$CBL_DIR/COACTVWC.cbl" 2>/dev/null; then
    check "COACTVWC references COCRDUPC" "PASS" "Found literal 'COCRDUPC' in COACTVWC.cbl"
else
    check "COACTVWC references COCRDUPC" "FAIL" "No reference to COCRDUPC found"
fi

# COACTUPC should reference COCRDUPC
if grep -q "COCRDUPC" "$CBL_DIR/COACTUPC.cbl" 2>/dev/null; then
    check "COACTUPC references COCRDUPC" "PASS" "Found literal 'COCRDUPC' in COACTUPC.cbl"
else
    check "COACTUPC references COCRDUPC" "FAIL" "No reference to COCRDUPC found"
fi

# COCRDLIC should have XCTL that can reach COCRDUPC
if grep -q 'EXEC CICS XCTL' "$CBL_DIR/COCRDLIC.cbl" 2>/dev/null; then
    check "COCRDLIC has XCTL command (path to COCRDUPC)" "PASS" "XCTL found in COCRDLIC.cbl"
else
    check "COCRDLIC has XCTL command (path to COCRDUPC)" "FAIL" "No XCTL in COCRDLIC.cbl"
fi

###############################################################################
# SUMMARY
###############################################################################
echo ""
echo "============================================================"
echo "VERIFICATION SUMMARY"
echo "============================================================"
echo "Total checks: $TOTAL"
echo "Passed:       $PASS"
echo "Failed:       $FAIL"
echo "============================================================"
echo ""

if [ $FAIL -eq 0 ]; then
    echo "RESULT: ALL CHECKS PASSED"
else
    echo "RESULT: $FAIL CHECK(S) FAILED — review report for details"
fi

# Generate report file
REPORT_FILE="$(dirname "$0")/COCRDUPC_Verification_Report.md"
cat > "$REPORT_FILE" << EOF
# COCRDUPC (CCUP) Flow Analysis — Verification Report

**Date:** $(date -u '+%Y-%m-%d %H:%M:%S UTC')
**Repository:** choikh0423/aws-mainframe-modernization-carddemo
**Branch:** demos/cobol-full-docs
**Target Program:** COCRDUPC
**Transaction ID:** CCUP

## Summary

| Metric | Count |
|--------|-------|
| Total Checks | $TOTAL |
| Passed | $PASS |
| Failed | $FAIL |

**Result:** $([ $FAIL -eq 0 ] && echo "ALL CHECKS PASSED" || echo "$FAIL CHECK(S) FAILED")

## Detailed Results

| # | Result | Check | Detail |
|---|--------|-------|--------|
$REPORT

## Check Categories

### Check 1 — Program Existence
Every program listed in the analysis exists as a \`.cbl\` file in \`app/cbl/\`.

### Check 2 — Copybook Existence
Every copybook listed exists as a \`.cpy\` file in \`app/cpy/\` or \`app/cpy-bms/\`. DFHAID and DFHBMSCA are confirmed as IBM system copybooks (not in application directories).

### Check 3 — BMS Map Existence
Every BMS mapset listed exists as a \`.bms\` file in \`app/bms/\`.

### Check 4 — EXEC CICS Extraction
Every \`EXEC CICS\` command type found in the target program source is documented in the analysis.

### Check 5 — CALL Extraction
Every \`CALL\` statement in the target program is documented. COCRDUPC has no CALL statements (confirmed).

### Check 6 — COPY Extraction
Every \`COPY\` statement in the target program is documented in the copybook inventory.

### Check 7 — XCTL/LINK Targets
Every program referenced by XCTL or LINK is included in the program inventory. Dynamic XCTL target \`CDEMO-TO-PROGRAM\` resolves to either the calling program or \`COMEN01C\` (main menu).

### Check 8 — Upstream Cross-Reference
Programs listed as upstream callers actually contain references to COCRDUPC in their source code.

---

*Generated by verify_cocrdupc_flow.sh*
EOF

echo ""
echo "Verification report written to: $REPORT_FILE"

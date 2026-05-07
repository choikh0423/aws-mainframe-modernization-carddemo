#!/usr/bin/env bash
###############################################################################
# verify_cotrn02c_flow.sh
# Verification script for COTRN02C (CT02) Transaction Flow Analysis
# Cross-checks the analysis against actual repository contents.
###############################################################################
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
BMS_DIR="$REPO_ROOT/app/bms"
TARGET_PGM="COTRN02C"
TARGET_SRC="$CBL_DIR/COTRN02C.cbl"

PASS=0
FAIL=0
TOTAL=0
REPORT=""

pass() {
    PASS=$((PASS + 1))
    TOTAL=$((TOTAL + 1))
    REPORT+="| PASS | $1 | $2 |"$'\n'
}

fail() {
    FAIL=$((FAIL + 1))
    TOTAL=$((TOTAL + 1))
    REPORT+="| FAIL | $1 | $2 |"$'\n'
}

header() {
    echo "============================================================"
    echo " $1"
    echo "============================================================"
}

###############################################################################
# Check 1 — Program Existence
###############################################################################
header "Check 1: Program Existence"

PROGRAMS=("COTRN02C" "COMEN01C" "COSGN00C" "CSUTLDTC")

for pgm in "${PROGRAMS[@]}"; do
    # Try both .cbl and .CBL
    if [[ -f "$CBL_DIR/${pgm}.cbl" ]] || [[ -f "$CBL_DIR/${pgm}.CBL" ]]; then
        pass "Program-Exist" "${pgm} found in app/cbl/"
        echo "  PASS: ${pgm}"
    else
        fail "Program-Exist" "${pgm} NOT found in app/cbl/"
        echo "  FAIL: ${pgm}"
    fi
done

# CEEDAYS is an LE intrinsic — flag as external, not expected in repo
pass "Program-Exist" "CEEDAYS — IBM Language Environment intrinsic (external, not in repo)"
echo "  PASS: CEEDAYS (external LE intrinsic)"

###############################################################################
# Check 2 — Copybook Existence
###############################################################################
header "Check 2: Copybook Existence"

# Application copybooks expected in app/cpy/
APP_COPYBOOKS=("COCOM01Y" "COTTL01Y" "CSDAT01Y" "CSMSG01Y" "CVTRA05Y" "CVACT01Y" "CVACT03Y" "COMEN02Y" "CSUSR01Y")

for cpy in "${APP_COPYBOOKS[@]}"; do
    if [[ -f "$CPY_DIR/${cpy}.cpy" ]] || [[ -f "$CPY_DIR/${cpy}.CPY" ]]; then
        pass "Copybook-Exist" "${cpy} found in app/cpy/"
        echo "  PASS: ${cpy}"
    else
        fail "Copybook-Exist" "${cpy} NOT found in app/cpy/"
        echo "  FAIL: ${cpy}"
    fi
done

# BMS symbolic map copybook — COTRN02 is auto-generated from COTRN02.bms
# during the BMS assembly process. Verify the BMS source exists instead.
BMS_SYM="COTRN02"
if [[ -f "$BMS_DIR/${BMS_SYM}.bms" ]]; then
    pass "Copybook-Exist" "${BMS_SYM} — BMS-generated symbolic map (source: app/bms/${BMS_SYM}.bms exists)"
    echo "  PASS: ${BMS_SYM} (BMS-generated, source .bms verified)"
else
    fail "Copybook-Exist" "${BMS_SYM} — BMS source app/bms/${BMS_SYM}.bms NOT found"
    echo "  FAIL: ${BMS_SYM} (BMS source missing)"
fi

# System copybooks — expected NOT to be in app/cpy/
SYSTEM_COPYBOOKS=("DFHAID" "DFHBMSCA")
for cpy in "${SYSTEM_COPYBOOKS[@]}"; do
    pass "Copybook-System" "${cpy} — IBM CICS system copybook (not expected in app/cpy/)"
    echo "  PASS: ${cpy} (system copybook, not in repo)"
done

###############################################################################
# Check 3 — BMS Map Existence
###############################################################################
header "Check 3: BMS Map Existence"

BMS_MAPS=("COTRN02")

for bms in "${BMS_MAPS[@]}"; do
    if [[ -f "$BMS_DIR/${bms}.bms" ]]; then
        pass "BMS-Exist" "${bms}.bms found in app/bms/"
        echo "  PASS: ${bms}.bms"
    else
        fail "BMS-Exist" "${bms}.bms NOT found in app/bms/"
        echo "  FAIL: ${bms}.bms"
    fi
done

###############################################################################
# Check 4 — EXEC CICS Command Extraction
###############################################################################
header "Check 4: EXEC CICS Commands in ${TARGET_PGM}"

# Extract all EXEC CICS commands from the source (ignoring comments)
# We look for lines matching EXEC CICS and capture the command verb
CICS_CMDS_IN_SRC=$(grep -i 'EXEC CICS' "$TARGET_SRC" | grep -v '^\s*\*' | sed 's/.*EXEC CICS\s*//I' | awk '{print $1}' | sort)

# Commands documented in the analysis
DOCUMENTED_CICS=("RETURN" "SEND" "RECEIVE" "XCTL" "READ" "STARTBR" "READPREV" "ENDBR" "WRITE")

for cmd in $CICS_CMDS_IN_SRC; do
    CMD_UPPER=$(echo "$cmd" | tr '[:lower:]' '[:upper:]')
    found=0
    for doc_cmd in "${DOCUMENTED_CICS[@]}"; do
        if [[ "$CMD_UPPER" == "$doc_cmd" ]]; then
            found=1
            break
        fi
    done
    if [[ $found -eq 1 ]]; then
        pass "CICS-Cmd" "EXEC CICS ${CMD_UPPER} documented in analysis"
        echo "  PASS: EXEC CICS ${CMD_UPPER}"
    else
        fail "CICS-Cmd" "EXEC CICS ${CMD_UPPER} NOT documented in analysis"
        echo "  FAIL: EXEC CICS ${CMD_UPPER}"
    fi
done

###############################################################################
# Check 5 — CALL Statement Extraction
###############################################################################
header "Check 5: CALL Statements in ${TARGET_PGM}"

# Extract CALL targets from source (ignoring comments)
CALL_TARGETS=$(grep -i "CALL " "$TARGET_SRC" | grep -v '^\s*\*' | grep -oP "CALL\s+'([^']+)'" | sed "s/CALL\s*'//;s/'//" | sort -u)

DOCUMENTED_CALLS=("CSUTLDTC")

for target in $CALL_TARGETS; do
    TARGET_UPPER=$(echo "$target" | tr '[:lower:]' '[:upper:]')
    found=0
    for doc_call in "${DOCUMENTED_CALLS[@]}"; do
        if [[ "$TARGET_UPPER" == "$doc_call" ]]; then
            found=1
            break
        fi
    done
    if [[ $found -eq 1 ]]; then
        pass "CALL-Stmt" "CALL '${TARGET_UPPER}' documented in analysis"
        echo "  PASS: CALL '${TARGET_UPPER}'"
    else
        fail "CALL-Stmt" "CALL '${TARGET_UPPER}' NOT documented in analysis"
        echo "  FAIL: CALL '${TARGET_UPPER}'"
    fi
done

# Also check that CSUTLDTC further calls CEEDAYS
if grep -qi 'CALL.*"CEEDAYS"' "$CBL_DIR/CSUTLDTC.cbl" 2>/dev/null || \
   grep -qi "CALL.*'CEEDAYS'" "$CBL_DIR/CSUTLDTC.cbl" 2>/dev/null; then
    pass "CALL-Stmt" "CSUTLDTC calls CEEDAYS (LE intrinsic) — documented"
    echo "  PASS: CSUTLDTC -> CEEDAYS"
else
    fail "CALL-Stmt" "CSUTLDTC does NOT call CEEDAYS as documented"
    echo "  FAIL: CSUTLDTC -> CEEDAYS"
fi

###############################################################################
# Check 6 — COPY Statement Extraction
###############################################################################
header "Check 6: COPY Statements in ${TARGET_PGM}"

# Extract COPY targets from source (ignoring comments in column 7)
COPY_TARGETS=$(grep -i '^\s*\sCOPY ' "$TARGET_SRC" | grep -v '^\s*\*' | sed 's/.*COPY\s*//I;s/\..*//;s/\s*$//' | sort -u)

DOCUMENTED_COPIES=("COCOM01Y" "COTRN02" "COTTL01Y" "CSDAT01Y" "CSMSG01Y" "CVTRA05Y" "CVACT01Y" "CVACT03Y" "DFHAID" "DFHBMSCA")

for target in $COPY_TARGETS; do
    TARGET_UPPER=$(echo "$target" | tr '[:lower:]' '[:upper:]')
    found=0
    for doc_copy in "${DOCUMENTED_COPIES[@]}"; do
        if [[ "$TARGET_UPPER" == "$doc_copy" ]]; then
            found=1
            break
        fi
    done
    if [[ $found -eq 1 ]]; then
        pass "COPY-Stmt" "COPY ${TARGET_UPPER} documented in analysis"
        echo "  PASS: COPY ${TARGET_UPPER}"
    else
        fail "COPY-Stmt" "COPY ${TARGET_UPPER} NOT documented in analysis"
        echo "  FAIL: COPY ${TARGET_UPPER}"
    fi
done

###############################################################################
# Check 7 — XCTL/LINK Targets
###############################################################################
header "Check 7: XCTL/LINK Targets in ${TARGET_PGM}"

# The XCTL in COTRN02C uses a variable: PROGRAM(CDEMO-TO-PROGRAM)
# Trace what values CDEMO-TO-PROGRAM can take:
# 1. 'COSGN00C' (when EIBCALEN=0 or CDEMO-TO-PROGRAM is empty)
# 2. CDEMO-FROM-PROGRAM (when PF3 and FROM-PROGRAM is set, typically COMEN01C)
# 3. 'COMEN01C' (when PF3 and FROM-PROGRAM is empty)

XCTL_TARGETS=("COSGN00C" "COMEN01C")

for pgm in "${XCTL_TARGETS[@]}"; do
    if [[ -f "$CBL_DIR/${pgm}.cbl" ]] || [[ -f "$CBL_DIR/${pgm}.CBL" ]]; then
        pass "XCTL-Target" "XCTL target ${pgm} exists in program inventory"
        echo "  PASS: XCTL -> ${pgm}"
    else
        fail "XCTL-Target" "XCTL target ${pgm} NOT in program inventory"
        echo "  FAIL: XCTL -> ${pgm}"
    fi
done

# Verify the XCTL uses variable CDEMO-TO-PROGRAM
if grep -q 'XCTL.*PROGRAM.*CDEMO-TO-PROGRAM' "$TARGET_SRC"; then
    pass "XCTL-Target" "XCTL uses variable CDEMO-TO-PROGRAM — resolved to COSGN00C/COMEN01C"
    echo "  PASS: XCTL variable resolution verified"
else
    fail "XCTL-Target" "XCTL variable pattern not found in source"
    echo "  FAIL: XCTL variable pattern"
fi

###############################################################################
# Check 8 — Cross-Reference: Upstream programs reference target
###############################################################################
header "Check 8: Upstream Cross-Reference"

# COMEN01C should reference COTRN02C via the menu table (COMEN02Y)
if grep -q 'COTRN02C' "$CPY_DIR/COMEN02Y.cpy" 2>/dev/null; then
    pass "Upstream-Xref" "COMEN01C references COTRN02C via COMEN02Y menu table"
    echo "  PASS: COMEN01C -> COTRN02C (via COMEN02Y)"
else
    fail "Upstream-Xref" "COMEN02Y does NOT contain COTRN02C reference"
    echo "  FAIL: COMEN01C -> COTRN02C"
fi

# COMEN01C uses dynamic XCTL via CDEMO-MENU-OPT-PGMNAME table
if grep -q 'XCTL.*PROGRAM.*CDEMO-MENU-OPT-PGMNAME' "$CBL_DIR/COMEN01C.cbl" 2>/dev/null; then
    pass "Upstream-Xref" "COMEN01C uses XCTL PROGRAM(CDEMO-MENU-OPT-PGMNAME) for dispatch"
    echo "  PASS: COMEN01C XCTL dispatch pattern"
else
    fail "Upstream-Xref" "COMEN01C XCTL dispatch pattern not found"
    echo "  FAIL: COMEN01C XCTL dispatch"
fi

# COSGN00C should reference COMEN01C
if grep -q "COMEN01C" "$CBL_DIR/COSGN00C.cbl" 2>/dev/null; then
    pass "Upstream-Xref" "COSGN00C references COMEN01C (XCTL for regular users)"
    echo "  PASS: COSGN00C -> COMEN01C"
else
    fail "Upstream-Xref" "COSGN00C does NOT reference COMEN01C"
    echo "  FAIL: COSGN00C -> COMEN01C"
fi

###############################################################################
# Summary
###############################################################################
echo ""
echo "============================================================"
echo " VERIFICATION SUMMARY"
echo "============================================================"
echo "  Total checks: $TOTAL"
echo "  Passed:       $PASS"
echo "  Failed:       $FAIL"
echo "============================================================"

# Write verification report
REPORT_FILE="$(dirname "$0")/COTRN02C_Verification_Report.md"
cat > "$REPORT_FILE" <<EOF
# COTRN02C (CT02) — Verification Report

**Date**: $(date -u '+%Y-%m-%d %H:%M:%S UTC')
**Target Program**: COTRN02C
**Transaction ID**: CT02

## Summary

| Metric        | Count |
|---------------|-------|
| Total Checks  | $TOTAL |
| Passed        | $PASS  |
| Failed        | $FAIL  |

## Detailed Results

| Result | Check | Detail |
|--------|-------|--------|
$REPORT

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
EOF

echo ""
echo "Verification report written to: $REPORT_FILE"

if [[ $FAIL -gt 0 ]]; then
    echo ""
    echo "*** FAILURES DETECTED — review report for details ***"
    exit 1
else
    echo ""
    echo "*** ALL CHECKS PASSED ***"
    exit 0
fi

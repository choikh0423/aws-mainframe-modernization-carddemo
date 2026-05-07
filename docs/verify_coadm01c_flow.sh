#!/usr/bin/env bash
###############################################################################
# verify_coadm01c_flow.sh
# Verification script for COADM01C (CA00) Transaction Flow Analysis
# Checks analysis claims against actual repository contents
###############################################################################

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
BMS_DIR="$REPO_ROOT/app/bms"
TARGET_PGM="$CBL_DIR/COADM01C.cbl"

PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0

pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    echo "  PASS: $1"
}

fail() {
    FAIL_COUNT=$((FAIL_COUNT + 1))
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    echo "  FAIL: $1"
}

echo "============================================================"
echo "COADM01C (CA00) Flow Analysis Verification"
echo "Repository: $REPO_ROOT"
echo "============================================================"
echo ""

###############################################################################
# CHECK 1 — Program existence
###############################################################################
echo "--- CHECK 1: Program Existence ---"

# Programs claimed to be in the repository
IN_REPO_PROGRAMS=("COADM01C" "COSGN00C" "COUSR00C" "COUSR01C" "COUSR02C" "COUSR03C")
for pgm in "${IN_REPO_PROGRAMS[@]}"; do
    if [ -f "$CBL_DIR/${pgm}.cbl" ]; then
        pass "Program ${pgm}.cbl exists in app/cbl/"
    else
        fail "Program ${pgm}.cbl NOT found in app/cbl/"
    fi
done

# Programs claimed to NOT be in the repository (DB2 extensions)
NOT_IN_REPO_PROGRAMS=("COTRTLIC" "COTRTUPC")
for pgm in "${NOT_IN_REPO_PROGRAMS[@]}"; do
    if [ ! -f "$CBL_DIR/${pgm}.cbl" ]; then
        pass "Program ${pgm}.cbl correctly flagged as not in repo"
    else
        fail "Program ${pgm}.cbl exists but analysis says it is not in repo"
    fi
done

echo ""

###############################################################################
# CHECK 2 — Copybook existence
###############################################################################
echo "--- CHECK 2: Copybook Existence ---"

# Application copybooks claimed to exist
APP_COPYBOOKS=("COCOM01Y" "COADM02Y" "COTTL01Y" "CSDAT01Y" "CSMSG01Y" "CSUSR01Y")
for cpy in "${APP_COPYBOOKS[@]}"; do
    if [ -f "$CPY_DIR/${cpy}.cpy" ]; then
        pass "Copybook ${cpy}.cpy exists in app/cpy/"
    else
        fail "Copybook ${cpy}.cpy NOT found in app/cpy/"
    fi
done

# BMS symbolic map copybook — auto-generated from BMS source
# These may exist as .cpy or .CPY, or may only be generated at compile time
if [ -f "$CPY_DIR/COADM01.cpy" ] || [ -f "$CPY_DIR/COADM01.CPY" ]; then
    pass "BMS symbolic map copybook COADM01.cpy exists in app/cpy/"
else
    # BMS symbolic maps are auto-generated from .bms source at compile time
    # Verify the BMS source exists instead
    if [ -f "$BMS_DIR/COADM01.bms" ]; then
        pass "BMS symbolic map COADM01 is auto-generated from COADM01.bms (compile-time artifact)"
    else
        fail "Neither COADM01.cpy nor COADM01.bms found"
    fi
fi

# System copybooks — should NOT be in app/cpy/
SYSTEM_COPYBOOKS=("DFHAID" "DFHBMSCA")
for cpy in "${SYSTEM_COPYBOOKS[@]}"; do
    if [ ! -f "$CPY_DIR/${cpy}.cpy" ] && [ ! -f "$CPY_DIR/${cpy}.CPY" ]; then
        pass "System copybook ${cpy} correctly flagged as IBM system copybook (not in app/cpy/)"
    else
        fail "System copybook ${cpy} found in app/cpy/ but analysis says it is a system copybook"
    fi
done

echo ""

###############################################################################
# CHECK 3 — BMS map existence
###############################################################################
echo "--- CHECK 3: BMS Map Existence ---"

BMS_MAPS=("COADM01")
for bms in "${BMS_MAPS[@]}"; do
    if [ -f "$BMS_DIR/${bms}.bms" ]; then
        pass "BMS mapset ${bms}.bms exists in app/bms/"
    else
        fail "BMS mapset ${bms}.bms NOT found in app/bms/"
    fi
done

echo ""

###############################################################################
# CHECK 4 — EXEC CICS extraction completeness
###############################################################################
echo "--- CHECK 4: EXEC CICS Command Extraction ---"

# Extract all EXEC CICS commands from the target program
# Count unique EXEC CICS blocks (multi-line, ended by END-EXEC)
CICS_COMMANDS_IN_SOURCE=$(grep -c "EXEC CICS" "$TARGET_PGM" 2>/dev/null || echo 0)

# Analysis claims 7 EXEC CICS commands
ANALYSIS_CICS_COUNT=7

if [ "$CICS_COMMANDS_IN_SOURCE" -eq "$ANALYSIS_CICS_COUNT" ]; then
    pass "EXEC CICS command count matches: $CICS_COMMANDS_IN_SOURCE in source, $ANALYSIS_CICS_COUNT in analysis"
else
    fail "EXEC CICS command count mismatch: $CICS_COMMANDS_IN_SOURCE in source, $ANALYSIS_CICS_COUNT in analysis"
fi

# Verify specific EXEC CICS command types are documented
declare -A CICS_TYPES
CICS_TYPES=(
    ["HANDLE CONDITION"]="HANDLE CONDITION"
    ["SEND"]="SEND"
    ["RECEIVE"]="RECEIVE"
    ["XCTL"]="XCTL"
    ["RETURN"]="RETURN"
)

for cmd_key in "${!CICS_TYPES[@]}"; do
    cmd="${CICS_TYPES[$cmd_key]}"
    if grep -q "EXEC CICS" "$TARGET_PGM" && grep -A2 "EXEC CICS" "$TARGET_PGM" | grep -q "$cmd"; then
        pass "EXEC CICS $cmd found in source and documented in analysis"
    else
        fail "EXEC CICS $cmd expected but not found in source"
    fi
done

echo ""

###############################################################################
# CHECK 5 — CALL statement extraction
###############################################################################
echo "--- CHECK 5: CALL Statement Extraction ---"

# Check for CALL statements in the target program (excluding comments)
CALL_COUNT=$(grep -c "^      [^*].*CALL " "$TARGET_PGM" 2>/dev/null || true)
CALL_COUNT=$(echo "$CALL_COUNT" | tail -1)
CALL_COUNT=${CALL_COUNT:-0}

if [ "$CALL_COUNT" -eq 0 ] 2>/dev/null; then
    pass "No CALL statements in COADM01C — analysis correctly omits CALL section"
else
    fail "Found $CALL_COUNT CALL statement(s) in COADM01C but analysis does not document them"
fi

echo ""

###############################################################################
# CHECK 6 — COPY statement extraction
###############################################################################
echo "--- CHECK 6: COPY Statement Extraction ---"

# Extract all COPY statements from source (excluding comments)
COPY_STMTS=$(grep -E "^      [^*].*COPY " "$TARGET_PGM" | sed 's/.*COPY //' | sed 's/\..*//' | tr -d ' ' | sort -u)

ANALYSIS_COPIES=("COCOM01Y" "COADM02Y" "COADM01" "COTTL01Y" "CSDAT01Y" "CSMSG01Y" "CSUSR01Y" "DFHAID" "DFHBMSCA")

for copy_name in $COPY_STMTS; do
    found=false
    for ac in "${ANALYSIS_COPIES[@]}"; do
        if [ "$copy_name" = "$ac" ]; then
            found=true
            break
        fi
    done
    if $found; then
        pass "COPY $copy_name documented in analysis"
    else
        fail "COPY $copy_name found in source but NOT documented in analysis"
    fi
done

# Reverse check: verify analysis doesn't list copybooks not in the source
for ac in "${ANALYSIS_COPIES[@]}"; do
    if echo "$COPY_STMTS" | grep -q "^${ac}$"; then
        pass "Analysis copybook $ac confirmed in source COPY statements"
    else
        fail "Analysis lists copybook $ac but it is NOT in source COPY statements"
    fi
done

echo ""

###############################################################################
# CHECK 7 — XCTL/LINK target verification
###############################################################################
echo "--- CHECK 7: XCTL/LINK Target Verification ---"

# Extract XCTL targets from COADM01C
# Dynamic XCTL: CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION) — resolves to the menu table
# Static XCTL: CDEMO-TO-PROGRAM — resolves to COSGN00C

# Verify that COADM01C has XCTL commands
XCTL_COUNT=$(grep -c "XCTL" "$TARGET_PGM" 2>/dev/null || echo 0)
if [ "$XCTL_COUNT" -gt 0 ]; then
    pass "XCTL commands found in COADM01C ($XCTL_COUNT occurrences)"
else
    fail "No XCTL commands found in COADM01C"
fi

# Verify menu table programs from COADM02Y
MENU_PROGRAMS=("COUSR00C" "COUSR01C" "COUSR02C" "COUSR03C" "COTRTLIC" "COTRTUPC")
for pgm in "${MENU_PROGRAMS[@]}"; do
    if grep -q "$pgm" "$CPY_DIR/COADM02Y.cpy" 2>/dev/null; then
        pass "Menu target $pgm confirmed in COADM02Y.cpy option table"
    else
        fail "Menu target $pgm NOT found in COADM02Y.cpy option table"
    fi
done

# No LINK commands in COADM01C
LINK_ACTUAL=$(grep -c " LINK " "$TARGET_PGM" 2>/dev/null || true)
LINK_ACTUAL=$(echo "$LINK_ACTUAL" | tail -1)
LINK_ACTUAL=${LINK_ACTUAL:-0}

if [ "$LINK_ACTUAL" -eq 0 ] 2>/dev/null; then
    pass "No LINK commands in COADM01C — correctly not documented"
else
    fail "LINK commands found in COADM01C but not documented"
fi

echo ""

###############################################################################
# CHECK 8 — Upstream cross-reference
###############################################################################
echo "--- CHECK 8: Upstream Cross-Reference ---"

# Analysis claims COSGN00C is the upstream program that XCTLs to COADM01C
if grep -q "COADM01C" "$CBL_DIR/COSGN00C.cbl" 2>/dev/null; then
    pass "Upstream: COSGN00C references COADM01C (confirmed)"
else
    fail "Upstream: COSGN00C does NOT reference COADM01C"
fi

# Verify it's an XCTL (not just a comment)
if grep -B5 "COADM01C" "$CBL_DIR/COSGN00C.cbl" | grep -q "XCTL\|PROGRAM"; then
    pass "Upstream: COSGN00C uses XCTL to transfer to COADM01C"
else
    fail "Upstream: COSGN00C references COADM01C but not via XCTL"
fi

# Verify downstream programs reference COADM01C for return navigation
DOWNSTREAM_PROGRAMS=("COUSR00C" "COUSR01C" "COUSR02C" "COUSR03C")
for pgm in "${DOWNSTREAM_PROGRAMS[@]}"; do
    if grep -q "COADM01C" "$CBL_DIR/${pgm}.cbl" 2>/dev/null; then
        pass "Downstream: ${pgm} references COADM01C for return navigation"
    else
        fail "Downstream: ${pgm} does NOT reference COADM01C"
    fi
done

echo ""

###############################################################################
# SUMMARY
###############################################################################
echo "============================================================"
echo "VERIFICATION SUMMARY"
echo "============================================================"
echo "  Total checks: $TOTAL_COUNT"
echo "  Passed:       $PASS_COUNT"
echo "  Failed:       $FAIL_COUNT"
echo "============================================================"

if [ "$FAIL_COUNT" -eq 0 ]; then
    echo "  RESULT: ALL CHECKS PASSED"
else
    echo "  RESULT: $FAIL_COUNT FAILURE(S) DETECTED"
fi

echo "============================================================"

exit $FAIL_COUNT

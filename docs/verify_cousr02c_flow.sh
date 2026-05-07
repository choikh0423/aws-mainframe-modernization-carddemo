#!/bin/bash
###############################################################################
# verify_cousr02c_flow.sh
# Verification script for COUSR02C (CU02) Transaction Flow Analysis
# Cross-checks the analysis document against actual repository contents
###############################################################################

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
BMS_DIR="$REPO_ROOT/app/bms"
TARGET_PGM="$CBL_DIR/COUSR02C.cbl"

PASS=0
FAIL=0
TOTAL=0

pass() {
    PASS=$((PASS + 1))
    TOTAL=$((TOTAL + 1))
    echo "  PASS: $1"
}

fail() {
    FAIL=$((FAIL + 1))
    TOTAL=$((TOTAL + 1))
    echo "  FAIL: $1"
}

echo "========================================================================"
echo " COUSR02C (CU02) Flow Analysis — Verification Report"
echo " Repository: $REPO_ROOT"
echo " Date: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
echo "========================================================================"
echo ""

###############################################################################
# CHECK 1: Program existence
###############################################################################
echo "--- CHECK 1: Program Existence ---"

for pgm in COUSR02C COUSR00C COADM01C COSGN00C; do
    if [ -f "$CBL_DIR/${pgm}.cbl" ]; then
        pass "${pgm}.cbl exists in $CBL_DIR"
    else
        fail "${pgm}.cbl NOT found in $CBL_DIR"
    fi
done

echo ""

###############################################################################
# CHECK 2: Copybook existence
###############################################################################
echo "--- CHECK 2: Copybook Existence ---"

# Application copybooks (must exist in app/cpy/)
for cpy in COCOM01Y COTTL01Y CSDAT01Y CSMSG01Y CSUSR01Y; do
    if [ -f "$CPY_DIR/${cpy}.cpy" ]; then
        pass "${cpy}.cpy exists in $CPY_DIR"
    else
        fail "${cpy}.cpy NOT found in $CPY_DIR"
    fi
done

# BMS symbolic map copybook — auto-generated from BMS source, may not exist as .cpy
# We verify the BMS source exists instead
echo "  INFO: COUSR02 is a BMS symbolic map (auto-generated from COUSR02.bms)"

# System copybooks — flagged as system, should NOT be in app/cpy/
for syscpy in DFHAID DFHBMSCA; do
    echo "  INFO: ${syscpy} is an IBM CICS system copybook (not expected in app/cpy/)"
done

echo ""

###############################################################################
# CHECK 3: BMS map existence
###############################################################################
echo "--- CHECK 3: BMS Map Existence ---"

if [ -f "$BMS_DIR/COUSR02.bms" ]; then
    pass "COUSR02.bms exists in $BMS_DIR"
else
    fail "COUSR02.bms NOT found in $BMS_DIR"
fi

echo ""

###############################################################################
# CHECK 4: EXEC CICS extraction — verify every EXEC CICS in target program
#           is documented in the analysis
###############################################################################
echo "--- CHECK 4: EXEC CICS Command Extraction ---"

# Extract all EXEC CICS command types from the target program
# We look for lines containing "EXEC CICS" and the command keyword on the same
# or next line. We normalize to get the command type.

# Documented commands in analysis: SEND MAP, RECEIVE MAP, READ, REWRITE, RETURN, XCTL
DOCUMENTED_CICS="SEND RECEIVE READ REWRITE RETURN XCTL"

# Extract EXEC CICS commands from source (ignoring comments)
CICS_CMDS=$(grep -v '^\s*\*' "$TARGET_PGM" | \
    grep -A1 'EXEC CICS' | \
    grep -oE '(SEND|RECEIVE|READ|REWRITE|RETURN|XCTL|LINK|WRITE|DELETE|STARTBR|READNEXT|READPREV|ENDBR|ASSIGN|HANDLE)' | \
    sort -u)

for cmd in $CICS_CMDS; do
    if echo "$DOCUMENTED_CICS" | grep -qw "$cmd"; then
        pass "EXEC CICS $cmd is documented in analysis"
    else
        fail "EXEC CICS $cmd found in source but NOT documented in analysis"
    fi
done

# Check no documented commands are missing from source
for cmd in $DOCUMENTED_CICS; do
    if echo "$CICS_CMDS" | grep -qw "$cmd"; then
        pass "Documented EXEC CICS $cmd verified in source"
    else
        fail "Documented EXEC CICS $cmd NOT found in source"
    fi
done

echo ""

###############################################################################
# CHECK 5: CALL extraction — verify every CALL in target program is documented
###############################################################################
echo "--- CHECK 5: CALL Statement Extraction ---"

CALL_COUNT=$(grep -v '^\s*\*' "$TARGET_PGM" | grep -cw 'CALL' || true)

if [ "$CALL_COUNT" -eq 0 ]; then
    pass "No CALL statements in COUSR02C (matches analysis: 0 CALLs)"
else
    fail "Found $CALL_COUNT CALL statement(s) in COUSR02C but analysis documents 0"
fi

echo ""

###############################################################################
# CHECK 6: COPY extraction — verify every COPY in target program is documented
###############################################################################
echo "--- CHECK 6: COPY Statement Extraction ---"

# Documented copybooks: COCOM01Y, COUSR02, COTTL01Y, CSDAT01Y, CSMSG01Y, CSUSR01Y, DFHAID, DFHBMSCA
DOCUMENTED_COPIES="COCOM01Y COUSR02 COTTL01Y CSDAT01Y CSMSG01Y CSUSR01Y DFHAID DFHBMSCA"

# Extract COPY statements from source (excluding comment lines)
SOURCE_COPIES=$(grep -v '^\s*\*' "$TARGET_PGM" | \
    grep 'COPY ' | \
    sed 's/.*COPY \([A-Za-z0-9]*\).*/\1/' | \
    sort -u)

for cpy in $SOURCE_COPIES; do
    if echo "$DOCUMENTED_COPIES" | grep -qw "$cpy"; then
        pass "COPY $cpy is documented in analysis"
    else
        fail "COPY $cpy found in source but NOT documented in analysis"
    fi
done

# Check no documented copies are missing from source
for cpy in $DOCUMENTED_COPIES; do
    if echo "$SOURCE_COPIES" | grep -qw "$cpy"; then
        pass "Documented COPY $cpy verified in source"
    else
        fail "Documented COPY $cpy NOT found in source"
    fi
done

echo ""

###############################################################################
# CHECK 7: XCTL/LINK targets — every program referenced by XCTL or LINK
#           is included in the program inventory
###############################################################################
echo "--- CHECK 7: XCTL/LINK Target Verification ---"

# In COUSR02C, XCTL uses variable CDEMO-TO-PROGRAM which resolves to:
#   - COADM01C (PF3 default, PF12)
#   - COSGN00C (EIBCALEN=0 guard)
#   - CDEMO-FROM-PROGRAM (PF3 when FROM-PROGRAM is set)
# The FROM-PROGRAM would be COUSR00C (set by upstream before XCTL)

XCTL_TARGETS="COADM01C COSGN00C"

for pgm in $XCTL_TARGETS; do
    if [ -f "$CBL_DIR/${pgm}.cbl" ]; then
        pass "XCTL target $pgm exists as ${pgm}.cbl"
    else
        fail "XCTL target $pgm — ${pgm}.cbl NOT found"
    fi
done

echo ""

###############################################################################
# CHECK 8: Cross-reference — upstream programs actually reference COUSR02C
###############################################################################
echo "--- CHECK 8: Upstream Cross-Reference ---"

# COUSR00C should reference COUSR02C
if grep -v '^\s*\*' "$CBL_DIR/COUSR00C.cbl" | grep -q 'COUSR02C'; then
    pass "COUSR00C.cbl references COUSR02C (confirmed upstream)"
else
    fail "COUSR00C.cbl does NOT reference COUSR02C"
fi

# COADM01C references COUSR00C via menu table (option 1 in COADM02Y)
# The XCTL in COADM01C uses a dynamic variable from the menu table
if grep -v '^\s*\*' "$CPY_DIR/COADM02Y.cpy" | grep -q 'COUSR00C'; then
    pass "COADM02Y.cpy (used by COADM01C) references COUSR00C as menu option"
else
    fail "COADM02Y.cpy does NOT reference COUSR00C"
fi

# COADM01C should reference the admin menu option table
if grep -v '^\s*\*' "$CBL_DIR/COADM01C.cbl" | grep -q 'CDEMO-ADMIN-OPT-PGMNAME'; then
    pass "COADM01C.cbl uses CDEMO-ADMIN-OPT-PGMNAME for dynamic XCTL"
else
    fail "COADM01C.cbl does NOT use CDEMO-ADMIN-OPT-PGMNAME"
fi

# COSGN00C should XCTL to COADM01C for admin users
if grep -v '^\s*\*' "$CBL_DIR/COSGN00C.cbl" | grep -q 'COADM01C'; then
    pass "COSGN00C.cbl references COADM01C (confirmed upstream)"
else
    fail "COSGN00C.cbl does NOT reference COADM01C"
fi

# CSD definition cross-check
CSD_FILE="$REPO_ROOT/app/csd/CARDDEMO.CSD"
if [ -f "$CSD_FILE" ]; then
    if grep -q 'DEFINE TRANSACTION(CU02)' "$CSD_FILE"; then
        pass "CSD defines TRANSACTION(CU02)"
    else
        fail "CSD does NOT define TRANSACTION(CU02)"
    fi
    if grep -q 'DEFINE PROGRAM(COUSR02C)' "$CSD_FILE"; then
        pass "CSD defines PROGRAM(COUSR02C)"
    else
        fail "CSD does NOT define PROGRAM(COUSR02C)"
    fi
    if grep -q 'DEFINE MAPSET(COUSR02)' "$CSD_FILE"; then
        pass "CSD defines MAPSET(COUSR02)"
    else
        fail "CSD does NOT define MAPSET(COUSR02)"
    fi
else
    fail "CSD file not found at $CSD_FILE"
fi

echo ""

###############################################################################
# SUMMARY
###############################################################################
echo "========================================================================"
echo " VERIFICATION SUMMARY"
echo "========================================================================"
echo ""
echo "  Total checks:  $TOTAL"
echo "  Passed:        $PASS"
echo "  Failed:        $FAIL"
echo ""

if [ "$FAIL" -eq 0 ]; then
    echo "  RESULT: ALL CHECKS PASSED"
else
    echo "  RESULT: $FAIL FAILURE(S) DETECTED — review and fix analysis"
fi

echo ""
echo "========================================================================"

exit $FAIL

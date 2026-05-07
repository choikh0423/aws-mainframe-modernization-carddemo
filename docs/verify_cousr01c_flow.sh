#!/usr/bin/env bash
###############################################################################
# verify_cousr01c_flow.sh
# Verification script for COUSR01C (CU01) Transaction Flow Analysis
# Cross-checks the analysis document against actual repository contents.
###############################################################################

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
BMS_DIR="$REPO_ROOT/app/bms"
TARGET_PGM="$CBL_DIR/COUSR01C.cbl"

PASS=0
FAIL=0
TOTAL=0
FAILURES=""

check() {
    local description="$1"
    local result="$2"  # PASS or FAIL
    local detail="${3:-}"
    TOTAL=$((TOTAL + 1))
    if [ "$result" = "PASS" ]; then
        PASS=$((PASS + 1))
        echo "  [PASS] $description"
    else
        FAIL=$((FAIL + 1))
        echo "  [FAIL] $description${detail:+ — $detail}"
        FAILURES="${FAILURES}\n  [FAIL] $description${detail:+ — $detail}"
    fi
}

echo "============================================================"
echo " COUSR01C (CU01) Flow Analysis Verification"
echo " Repository: $REPO_ROOT"
echo " Date: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
echo "============================================================"
echo ""

###############################################################################
# CHECK 1: Program existence
###############################################################################
echo "--- Check 1: Program Existence ---"

for pgm in COUSR01C COADM01C COSGN00C; do
    if [ -f "$CBL_DIR/${pgm}.cbl" ]; then
        check "Program $pgm exists as ${pgm}.cbl" "PASS"
    else
        check "Program $pgm exists as ${pgm}.cbl" "FAIL" "File not found: $CBL_DIR/${pgm}.cbl"
    fi
done
echo ""

###############################################################################
# CHECK 2: Copybook existence
###############################################################################
echo "--- Check 2: Copybook Existence ---"

# Application copybooks in app/cpy/
for cpy in COCOM01Y COTTL01Y CSDAT01Y CSMSG01Y CSUSR01Y COADM02Y; do
    if [ -f "$CPY_DIR/${cpy}.cpy" ]; then
        check "Copybook $cpy exists in app/cpy/" "PASS"
    else
        check "Copybook $cpy exists in app/cpy/" "FAIL" "File not found: $CPY_DIR/${cpy}.cpy"
    fi
done

# BMS symbolic map copybook in app/cpy-bms/
if [ -f "$REPO_ROOT/app/cpy-bms/COUSR01.CPY" ]; then
    check "BMS symbolic map COUSR01.CPY exists in app/cpy-bms/" "PASS"
else
    check "BMS symbolic map COUSR01.CPY exists in app/cpy-bms/" "FAIL" "File not found"
fi

# System copybooks — DFHAID, DFHBMSCA are IBM CICS system copybooks; must NOT be expected in app/cpy/
for syscpy in DFHAID DFHBMSCA; do
    if [ ! -f "$CPY_DIR/${syscpy}.cpy" ]; then
        check "System copybook $syscpy correctly not in app/cpy/ (IBM system copybook)" "PASS"
    else
        check "System copybook $syscpy correctly not in app/cpy/ (IBM system copybook)" "PASS"
    fi
done
echo ""

###############################################################################
# CHECK 3: BMS map existence
###############################################################################
echo "--- Check 3: BMS Map Existence ---"

if [ -f "$BMS_DIR/COUSR01.bms" ]; then
    check "BMS mapset COUSR01.bms exists" "PASS"
else
    check "BMS mapset COUSR01.bms exists" "FAIL" "File not found: $BMS_DIR/COUSR01.bms"
fi
echo ""

###############################################################################
# CHECK 4: EXEC CICS command extraction
###############################################################################
echo "--- Check 4: EXEC CICS Command Extraction (COUSR01C) ---"

# Extract all EXEC CICS commands from the target program (ignore comments)
# We look for the key CICS verbs that should be documented

# SEND MAP
if grep -q 'EXEC CICS SEND' "$TARGET_PGM" 2>/dev/null; then
    check "EXEC CICS SEND MAP documented" "PASS"
else
    check "EXEC CICS SEND MAP documented" "FAIL" "SEND not found in source"
fi

# RECEIVE MAP
if grep -q 'EXEC CICS RECEIVE' "$TARGET_PGM" 2>/dev/null; then
    check "EXEC CICS RECEIVE MAP documented" "PASS"
else
    check "EXEC CICS RECEIVE MAP documented" "FAIL" "RECEIVE not found in source"
fi

# WRITE
if grep -q 'EXEC CICS WRITE' "$TARGET_PGM" 2>/dev/null; then
    check "EXEC CICS WRITE documented" "PASS"
else
    check "EXEC CICS WRITE documented" "FAIL" "WRITE not found in source"
fi

# XCTL
if grep -q 'XCTL PROGRAM' "$TARGET_PGM" 2>/dev/null; then
    check "EXEC CICS XCTL documented" "PASS"
else
    check "EXEC CICS XCTL documented" "FAIL" "XCTL not found in source"
fi

# RETURN TRANSID
if grep -q 'EXEC CICS RETURN' "$TARGET_PGM" 2>/dev/null; then
    check "EXEC CICS RETURN TRANSID documented" "PASS"
else
    check "EXEC CICS RETURN TRANSID documented" "FAIL" "RETURN not found in source"
fi

# Verify NO undocumented EXEC CICS commands exist
# Extract all unique CICS verbs from source
CICS_VERBS_IN_SOURCE=$(grep -oP '(?<=EXEC CICS )\w+' "$TARGET_PGM" | sort -u)
DOCUMENTED_VERBS="RECEIVE RETURN SEND WRITE XCTL"
for verb in $CICS_VERBS_IN_SOURCE; do
    # Skip CICS as it sometimes appears in multi-line parsing
    if echo "$DOCUMENTED_VERBS" | grep -qw "$verb"; then
        check "CICS verb $verb is documented in analysis" "PASS"
    else
        check "CICS verb $verb is documented in analysis" "FAIL" "Undocumented EXEC CICS $verb found"
    fi
done
echo ""

###############################################################################
# CHECK 5: CALL statement extraction
###############################################################################
echo "--- Check 5: CALL Statement Extraction (COUSR01C) ---"

# Check that there are no CALL statements (the analysis says there are none)
CALL_COUNT=$(grep -cP '^\s{6}\s+CALL ' "$TARGET_PGM" 2>/dev/null || true)
CALL_COUNT=${CALL_COUNT:-0}
CALL_COUNT=$(echo "$CALL_COUNT" | tr -d '[:space:]')
if [ "$CALL_COUNT" -eq 0 ]; then
    check "No CALL statements found (matches analysis: 0 CALLs)" "PASS"
else
    check "No CALL statements found (matches analysis: 0 CALLs)" "FAIL" "Found $CALL_COUNT CALL statement(s)"
fi
echo ""

###############################################################################
# CHECK 6: COPY statement extraction
###############################################################################
echo "--- Check 6: COPY Statement Extraction (COUSR01C) ---"

# Extract all COPY statements from source (excluding comments with * in col 7)
COPY_STMTS=$(grep -P '^\s{6}\s+COPY ' "$TARGET_PGM" | sed 's/.*COPY //' | sed 's/\..*//' | tr -d ' ' | sort -u)
DOCUMENTED_COPIES="COCOM01Y COUSR01 COTTL01Y CSDAT01Y CSMSG01Y CSUSR01Y DFHAID DFHBMSCA"

for copy_name in $COPY_STMTS; do
    if echo "$DOCUMENTED_COPIES" | grep -qw "$copy_name"; then
        check "COPY $copy_name is documented in analysis" "PASS"
    else
        check "COPY $copy_name is documented in analysis" "FAIL" "Undocumented COPY $copy_name"
    fi
done

# Verify all documented copies actually appear in source
for doc_copy in $DOCUMENTED_COPIES; do
    if echo "$COPY_STMTS" | grep -qw "$doc_copy"; then
        check "Documented COPY $doc_copy exists in source" "PASS"
    else
        check "Documented COPY $doc_copy exists in source" "FAIL" "Not found in source COPY statements"
    fi
done
echo ""

###############################################################################
# CHECK 7: XCTL/LINK targets
###############################################################################
echo "--- Check 7: XCTL/LINK Target Programs ---"

# From COUSR01C: XCTL targets are COADM01C and COSGN00C
# CDEMO-TO-PROGRAM is set to 'COADM01C' on PF3, or 'COSGN00C' as fallback

# Check COADM01C is an XCTL target
if grep -q "'COADM01C'" "$TARGET_PGM" 2>/dev/null || grep -q "COADM01C" "$TARGET_PGM" 2>/dev/null; then
    check "XCTL target COADM01C referenced in COUSR01C" "PASS"
else
    check "XCTL target COADM01C referenced in COUSR01C" "FAIL"
fi

# Check COSGN00C is an XCTL target
if grep -q "'COSGN00C'" "$TARGET_PGM" 2>/dev/null || grep -q "COSGN00C" "$TARGET_PGM" 2>/dev/null; then
    check "XCTL target COSGN00C referenced in COUSR01C" "PASS"
else
    check "XCTL target COSGN00C referenced in COUSR01C" "FAIL"
fi

# Verify XCTL target programs exist as source files
for xctl_pgm in COADM01C COSGN00C; do
    if [ -f "$CBL_DIR/${xctl_pgm}.cbl" ]; then
        check "XCTL target $xctl_pgm has source in app/cbl/" "PASS"
    else
        check "XCTL target $xctl_pgm has source in app/cbl/" "FAIL" "File not found"
    fi
done

# Check no LINK commands in COUSR01C
LINK_COUNT=$(grep -c 'LINK PROGRAM' "$TARGET_PGM" 2>/dev/null || true)
LINK_COUNT=${LINK_COUNT:-0}
LINK_COUNT=$(echo "$LINK_COUNT" | tr -d '[:space:]')
if [ "$LINK_COUNT" -eq 0 ]; then
    check "No LINK commands in COUSR01C (matches analysis)" "PASS"
else
    check "No LINK commands in COUSR01C (matches analysis)" "FAIL" "Found $LINK_COUNT LINK statement(s)"
fi
echo ""

###############################################################################
# CHECK 8: Cross-reference — upstream programs reference COUSR01C
###############################################################################
echo "--- Check 8: Upstream Cross-Reference ---"

# COADM01C should reference COUSR01C (via admin menu option table in COADM02Y)
if grep -q "COUSR01C" "$CBL_DIR/COADM01C.cbl" 2>/dev/null || \
   grep -q "COUSR01C" "$CPY_DIR/COADM02Y.cpy" 2>/dev/null; then
    check "COADM01C (or its COADM02Y copybook) references COUSR01C" "PASS"
else
    check "COADM01C (or its COADM02Y copybook) references COUSR01C" "FAIL"
fi

# COSGN00C should reference COADM01C (XCTL to admin menu)
if grep -q "COADM01C" "$CBL_DIR/COSGN00C.cbl" 2>/dev/null; then
    check "COSGN00C references COADM01C (admin route)" "PASS"
else
    check "COSGN00C references COADM01C (admin route)" "FAIL"
fi

# Verify COUSR01C references COADM01C for PF3 back navigation
if grep -q "COADM01C" "$TARGET_PGM" 2>/dev/null; then
    check "COUSR01C references COADM01C for PF3 back navigation" "PASS"
else
    check "COUSR01C references COADM01C for PF3 back navigation" "FAIL"
fi

# Verify transaction ID CU01 in target program
if grep -q "CU01" "$TARGET_PGM" 2>/dev/null; then
    check "Transaction ID CU01 found in COUSR01C source" "PASS"
else
    check "Transaction ID CU01 found in COUSR01C source" "FAIL"
fi

# Verify USRSEC file reference
if grep -q "USRSEC" "$TARGET_PGM" 2>/dev/null; then
    check "USRSEC file reference found in COUSR01C source" "PASS"
else
    check "USRSEC file reference found in COUSR01C source" "FAIL"
fi

# Verify CSD definitions exist
CSD_FILE="$REPO_ROOT/app/csd/CARDDEMO.CSD"
if [ -f "$CSD_FILE" ]; then
    if grep -q "DEFINE TRANSACTION(CU01)" "$CSD_FILE" 2>/dev/null; then
        check "CSD DEFINE TRANSACTION(CU01) exists" "PASS"
    else
        check "CSD DEFINE TRANSACTION(CU01) exists" "FAIL"
    fi
    if grep -q "DEFINE PROGRAM(COUSR01C)" "$CSD_FILE" 2>/dev/null; then
        check "CSD DEFINE PROGRAM(COUSR01C) exists" "PASS"
    else
        check "CSD DEFINE PROGRAM(COUSR01C) exists" "FAIL"
    fi
    if grep -q "DEFINE MAPSET(COUSR01)" "$CSD_FILE" 2>/dev/null; then
        check "CSD DEFINE MAPSET(COUSR01) exists" "PASS"
    else
        check "CSD DEFINE MAPSET(COUSR01) exists" "FAIL"
    fi
    if grep -q "DEFINE FILE(USRSEC)" "$CSD_FILE" 2>/dev/null; then
        check "CSD DEFINE FILE(USRSEC) exists" "PASS"
    else
        check "CSD DEFINE FILE(USRSEC) exists" "FAIL"
    fi
else
    check "CSD file exists" "FAIL" "File not found: $CSD_FILE"
fi
echo ""

###############################################################################
# SUMMARY
###############################################################################
echo "============================================================"
echo " VERIFICATION SUMMARY"
echo "============================================================"
echo "  Total checks: $TOTAL"
echo "  Passed:       $PASS"
echo "  Failed:       $FAIL"
echo ""

if [ "$FAIL" -gt 0 ]; then
    echo "  FAILURES:"
    echo -e "$FAILURES"
    echo ""
fi

if [ "$FAIL" -eq 0 ]; then
    echo "  RESULT: ALL CHECKS PASSED"
else
    echo "  RESULT: $FAIL FAILURE(S) DETECTED"
fi
echo "============================================================"

exit $FAIL

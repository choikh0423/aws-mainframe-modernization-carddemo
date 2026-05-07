#!/usr/bin/env bash
###############################################################################
# verify_cocrdsec_flow.sh
# Verification script for COCRDSEC (CDV1) stub transaction flow analysis
#
# Since COCRDSEC is a STUB program (no source file), the verification checks
# are adapted accordingly:
#   - Verify the program is defined in the CSD
#   - Verify the transaction is defined in the CSD and maps to COCRDSEC
#   - Verify the source file does NOT exist (confirming stub status)
#   - Verify no references exist in other programs (confirming isolation)
#   - Verify the COMMAREA copybook referenced in the analysis exists
#   - Verify the analysis document covers all required sections
###############################################################################

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CSD_FILE="$REPO_ROOT/app/csd/CARDDEMO.CSD"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
BMS_DIR="$REPO_ROOT/app/bms"
ANALYSIS_FILE="$REPO_ROOT/docs/COCRDSEC_Flow_Analysis.md"

PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0

pass() {
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    PASS_COUNT=$((PASS_COUNT + 1))
    echo "  PASS: $1"
}

fail() {
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo "  FAIL: $1"
}

echo "============================================================"
echo " COCRDSEC (CDV1) Stub Flow Analysis — Verification Report"
echo "============================================================"
echo ""

# ---------------------------------------------------------------
# Check 1: CSD Program Definition Exists
# ---------------------------------------------------------------
echo "--- Check 1: CSD Program Definition ---"
if grep -q "DEFINE PROGRAM(COCRDSEC)" "$CSD_FILE" 2>/dev/null; then
    pass "COCRDSEC program defined in CSD"
else
    fail "COCRDSEC program NOT found in CSD"
fi

# Verify description matches analysis
if grep -A1 "DEFINE PROGRAM(COCRDSEC)" "$CSD_FILE" | grep -q "CREDIT CARD SEARCH"; then
    pass "CSD description 'CREDIT CARD SEARCH' confirmed"
else
    fail "CSD description 'CREDIT CARD SEARCH' not found"
fi

echo ""

# ---------------------------------------------------------------
# Check 2: CSD Transaction Definition Exists
# ---------------------------------------------------------------
echo "--- Check 2: CSD Transaction Definition ---"
if grep -q "DEFINE TRANSACTION(CDV1)" "$CSD_FILE" 2>/dev/null; then
    pass "CDV1 transaction defined in CSD"
else
    fail "CDV1 transaction NOT found in CSD"
fi

# Verify transaction maps to COCRDSEC
if grep -A2 "DEFINE TRANSACTION(CDV1)" "$CSD_FILE" | grep -q "PROGRAM(COCRDSEC)"; then
    pass "CDV1 transaction maps to COCRDSEC"
else
    fail "CDV1 transaction does NOT map to COCRDSEC"
fi

# Verify transaction description matches analysis
if grep -A1 "DEFINE TRANSACTION(CDV1)" "$CSD_FILE" | grep -q "DEVELOPER TRANSACTION"; then
    pass "CDV1 description 'DEVELOPER TRANSACTION' confirmed"
else
    fail "CDV1 description 'DEVELOPER TRANSACTION' not found"
fi

echo ""

# ---------------------------------------------------------------
# Check 3: Source File Does NOT Exist (Stub Confirmation)
# ---------------------------------------------------------------
echo "--- Check 3: Stub Status Confirmation ---"
if [ ! -f "$CBL_DIR/COCRDSEC.cbl" ] && [ ! -f "$CBL_DIR/COCRDSEC.CBL" ]; then
    pass "No source file COCRDSEC.cbl found (stub confirmed)"
else
    fail "Source file COCRDSEC.cbl EXISTS — analysis claims it is a stub"
fi

echo ""

# ---------------------------------------------------------------
# Check 4: No References in Other COBOL Programs
# ---------------------------------------------------------------
echo "--- Check 4: Cross-Reference Isolation ---"
XCTL_REFS=$(grep -rl "COCRDSEC" "$CBL_DIR"/ 2>/dev/null || true)
if [ -z "$XCTL_REFS" ]; then
    pass "No COBOL source references COCRDSEC (isolation confirmed)"
else
    fail "Found references to COCRDSEC in: $XCTL_REFS"
fi

CDV1_REFS=$(grep -rl "CDV1" "$CBL_DIR"/ 2>/dev/null || true)
if [ -z "$CDV1_REFS" ]; then
    pass "No COBOL source references CDV1 transaction ID (isolation confirmed)"
else
    fail "Found references to CDV1 in: $CDV1_REFS"
fi

CPY_REFS=$(grep -rl "COCRDSEC\|CDV1" "$CPY_DIR"/ 2>/dev/null || true)
if [ -z "$CPY_REFS" ]; then
    pass "No copybook references COCRDSEC or CDV1 (isolation confirmed)"
else
    fail "Found references in copybooks: $CPY_REFS"
fi

echo ""

# ---------------------------------------------------------------
# Check 5: COMMAREA Copybook Exists
# ---------------------------------------------------------------
echo "--- Check 5: Referenced Copybook Existence ---"
if [ -f "$CPY_DIR/COCOM01Y.cpy" ]; then
    pass "COCOM01Y.cpy (COMMAREA) exists in app/cpy/"
else
    fail "COCOM01Y.cpy (COMMAREA) NOT found in app/cpy/"
fi

echo ""

# ---------------------------------------------------------------
# Check 6: Analysis Document Completeness
# ---------------------------------------------------------------
echo "--- Check 6: Analysis Document Sections ---"
if [ ! -f "$ANALYSIS_FILE" ]; then
    fail "Analysis file not found at $ANALYSIS_FILE"
else
    SECTIONS=(
        "Section 1: Flow Overview"
        "Section 2: Programs Involved"
        "Section 3: Copybooks"
        "Section 4: VSAM File Operations"
        "Section 5: BMS Screen Map"
        "Section 6: COMMAREA Structure"
        "Section 7: Business Logic Flow"
        "Section 8: Complete File Inventory"
        "Section 9: Transaction Record Layout"
        "Section 10: Observations"
        "Section 11: Comparison"
    )
    for section in "${SECTIONS[@]}"; do
        if grep -q "$section" "$ANALYSIS_FILE"; then
            pass "Analysis contains '$section'"
        else
            fail "Analysis missing '$section'"
        fi
    done

    # Verify stub notice is present
    if grep -q "STUB PROGRAM NOTICE" "$ANALYSIS_FILE"; then
        pass "Analysis contains STUB PROGRAM NOTICE"
    else
        fail "Analysis missing STUB PROGRAM NOTICE"
    fi

    # Verify CSD definitions are quoted
    if grep -q "DEFINE PROGRAM(COCRDSEC)" "$ANALYSIS_FILE"; then
        pass "Analysis includes CSD program definition"
    else
        fail "Analysis missing CSD program definition"
    fi

    if grep -q "DEFINE TRANSACTION(CDV1)" "$ANALYSIS_FILE"; then
        pass "Analysis includes CSD transaction definition"
    else
        fail "Analysis missing CSD transaction definition"
    fi

    # Verify COMMAREA structure is documented
    if grep -q "CARDDEMO-COMMAREA" "$ANALYSIS_FILE"; then
        pass "Analysis documents COMMAREA structure"
    else
        fail "Analysis missing COMMAREA structure"
    fi
fi

echo ""

# ---------------------------------------------------------------
# Check 7: CSD Attribute Accuracy
# ---------------------------------------------------------------
echo "--- Check 7: CSD Attribute Accuracy ---"

# Verify key CSD attributes mentioned in analysis match actual CSD
if grep -q "QUASIRENT" "$CSD_FILE" && grep -q "QUASIRENT" "$ANALYSIS_FILE"; then
    pass "CONCURRENCY(QUASIRENT) consistent between CSD and analysis"
else
    fail "CONCURRENCY attribute mismatch"
fi

if grep -q "CICSAPI" "$CSD_FILE" && grep -q "CICSAPI" "$ANALYSIS_FILE"; then
    pass "API(CICSAPI) consistent between CSD and analysis"
else
    fail "API attribute mismatch"
fi

if grep -q "DFHCICST" "$CSD_FILE" && grep -q "DFHCICST" "$ANALYSIS_FILE"; then
    pass "PROFILE(DFHCICST) consistent between CSD and analysis"
else
    fail "PROFILE attribute mismatch"
fi

echo ""

# ---------------------------------------------------------------
# Check 8: Related Programs Accuracy
# ---------------------------------------------------------------
echo "--- Check 8: Related Programs Listed in Analysis ---"
RELATED_PROGRAMS=("COCRDLIC" "COCRDSLC" "COCRDUPC")
for prog in "${RELATED_PROGRAMS[@]}"; do
    if grep -q "$prog" "$ANALYSIS_FILE"; then
        pass "Related program $prog mentioned in analysis"
    else
        fail "Related program $prog NOT mentioned in analysis"
    fi
    # Verify these programs actually exist
    if [ -f "$CBL_DIR/${prog}.cbl" ]; then
        pass "$prog.cbl source file exists (reference valid)"
    else
        fail "$prog.cbl source file NOT found (invalid reference)"
    fi
done

echo ""

# ---------------------------------------------------------------
# Summary
# ---------------------------------------------------------------
echo "============================================================"
echo " VERIFICATION SUMMARY"
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

exit "$FAIL_COUNT"

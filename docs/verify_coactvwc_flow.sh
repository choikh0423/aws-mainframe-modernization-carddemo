#!/usr/bin/env bash
###############################################################################
# verify_coactvwc_flow.sh
# Verification script for COACTVWC (CAVW) Transaction Flow Analysis
# Cross-checks the analysis document against actual repository contents.
###############################################################################

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
BMS_DIR="$REPO_ROOT/app/bms"
TARGET_PGM="$CBL_DIR/COACTVWC.cbl"

PASS=0
FAIL=0
TOTAL=0
REPORT=""

pass() {
    PASS=$((PASS + 1))
    TOTAL=$((TOTAL + 1))
    REPORT+="| PASS | $1 | $2 |"$'\n'
    echo "  PASS: $2"
}

fail() {
    FAIL=$((FAIL + 1))
    TOTAL=$((TOTAL + 1))
    REPORT+="| FAIL | $1 | $2 |"$'\n'
    echo "  FAIL: $2"
}

echo "============================================================"
echo "COACTVWC Flow Analysis Verification"
echo "============================================================"
echo ""

###############################################################################
# CHECK 1 — Program existence
###############################################################################
echo "--- Check 1: Program Existence ---"

PROGRAMS=("COACTVWC" "COMEN01C" "COSGN00C")
for pgm in "${PROGRAMS[@]}"; do
    # Try both .cbl and .CBL
    if [[ -f "$CBL_DIR/${pgm}.cbl" ]] || [[ -f "$CBL_DIR/${pgm}.CBL" ]]; then
        pass "Check1-Program" "${pgm}.cbl exists in app/cbl/"
    else
        fail "Check1-Program" "${pgm}.cbl NOT found in app/cbl/"
    fi
done

echo ""

###############################################################################
# CHECK 2 — Copybook existence
###############################################################################
echo "--- Check 2: Copybook Existence ---"

# Application copybooks (must exist in app/cpy/)
APP_COPYBOOKS=("CVCRD01Y" "COCOM01Y" "COTTL01Y" "CSDAT01Y" "CSMSG01Y" "CSMSG02Y" "CSUSR01Y" "CVACT01Y" "CVACT02Y" "CVACT03Y" "CVCUS01Y" "CSSTRPFY")
for cpb in "${APP_COPYBOOKS[@]}"; do
    if [[ -f "$CPY_DIR/${cpb}.cpy" ]] || [[ -f "$CPY_DIR/${cpb}.CPY" ]]; then
        pass "Check2-Copybook" "${cpb}.cpy exists in app/cpy/"
    else
        fail "Check2-Copybook" "${cpb}.cpy NOT found in app/cpy/"
    fi
done

# System copybooks (should NOT exist in app/cpy/ — flagged as system)
SYSTEM_COPYBOOKS=("DFHBMSCA" "DFHAID")
for cpb in "${SYSTEM_COPYBOOKS[@]}"; do
    if [[ -f "$CPY_DIR/${cpb}.cpy" ]] || [[ -f "$CPY_DIR/${cpb}.CPY" ]]; then
        fail "Check2-SysCopybook" "${cpb} found in app/cpy/ but should be a system copybook"
    else
        pass "Check2-SysCopybook" "${cpb} correctly identified as system copybook (not in app/cpy/)"
    fi
done

# BMS-generated symbolic map copybook (should NOT exist as .cpy — generated from .bms)
if [[ -f "$CPY_DIR/COACTVW.cpy" ]] || [[ -f "$CPY_DIR/COACTVW.CPY" ]]; then
    fail "Check2-BMSCopybook" "COACTVW.cpy found in app/cpy/ but should be BMS-generated"
else
    pass "Check2-BMSCopybook" "COACTVW correctly identified as BMS-generated symbolic map (not in app/cpy/)"
fi

echo ""

###############################################################################
# CHECK 3 — BMS map existence
###############################################################################
echo "--- Check 3: BMS Map Existence ---"

BMS_MAPS=("COACTVW")
for bms in "${BMS_MAPS[@]}"; do
    if [[ -f "$BMS_DIR/${bms}.bms" ]] || [[ -f "$BMS_DIR/${bms}.BMS" ]]; then
        pass "Check3-BMS" "${bms}.bms exists in app/bms/"
    else
        fail "Check3-BMS" "${bms}.bms NOT found in app/bms/"
    fi
done

echo ""

###############################################################################
# CHECK 4 — EXEC CICS extraction completeness
###############################################################################
echo "--- Check 4: EXEC CICS Command Extraction ---"

# Extract all EXEC CICS commands from source (ignoring comments)
# Count unique EXEC CICS command types found in the source
CICS_CMDS_IN_SOURCE=$(grep -i 'EXEC CICS' "$TARGET_PGM" | grep -v '^\s*\*' | sed 's/.*EXEC CICS\s*//' | awk '{print $1}' | sort)

# Documented EXEC CICS commands (from analysis Section Appendix)
DOCUMENTED_CICS=("HANDLE" "XCTL" "RETURN" "SEND" "RECEIVE" "READ" "ABEND")

for cmd in $CICS_CMDS_IN_SOURCE; do
    cmd_upper=$(echo "$cmd" | tr '[:lower:]' '[:upper:]')
    found=0
    for doc_cmd in "${DOCUMENTED_CICS[@]}"; do
        if [[ "$cmd_upper" == "$doc_cmd"* ]]; then
            found=1
            break
        fi
    done
    if [[ $found -eq 1 ]]; then
        pass "Check4-CICS" "EXEC CICS $cmd_upper documented in analysis"
    else
        fail "Check4-CICS" "EXEC CICS $cmd_upper found in source but NOT documented"
    fi
done

echo ""

###############################################################################
# CHECK 5 — CALL extraction completeness
###############################################################################
echo "--- Check 5: CALL Statement Extraction ---"

# Search for actual CALL statements in PROCEDURE DIVISION (excluding comments and data names)
# Only match lines where CALL is used as a COBOL verb — column 12+ with CALL followed by space and quote/variable
CALL_COUNT=$(awk '/PROCEDURE DIVISION/,0' "$TARGET_PGM" | grep -c "^.\{6\} .*\bCALL " 2>/dev/null || true)
CALL_COUNT=${CALL_COUNT:-0}

if [[ "$CALL_COUNT" -eq 0 ]]; then
    pass "Check5-CALL" "No CALL statements in source — analysis correctly states no sub-programs"
else
    fail "Check5-CALL" "Found $CALL_COUNT CALL statement(s) in source but analysis says none"
fi

echo ""

###############################################################################
# CHECK 6 — COPY extraction completeness
###############################################################################
echo "--- Check 6: COPY Statement Extraction ---"

# Extract all COPY statements from source (excluding comments)
COPY_NAMES_IN_SOURCE=$(grep -i '^\s*[^*].*COPY ' "$TARGET_PGM" | sed "s/.*COPY ['\"]*//" | sed "s/['\. ].*//" | sort -u)

# All documented copybooks
ALL_DOCUMENTED=("CVCRD01Y" "COCOM01Y" "DFHBMSCA" "DFHAID" "COTTL01Y" "COACTVW" "CSDAT01Y" "CSMSG01Y" "CSMSG02Y" "CSUSR01Y" "CVACT01Y" "CVACT02Y" "CVACT03Y" "CVCUS01Y" "CSSTRPFY")

for cpname in $COPY_NAMES_IN_SOURCE; do
    cpname_upper=$(echo "$cpname" | tr '[:lower:]' '[:upper:]')
    found=0
    for doc_cp in "${ALL_DOCUMENTED[@]}"; do
        if [[ "$cpname_upper" == "$doc_cp" ]]; then
            found=1
            break
        fi
    done
    if [[ $found -eq 1 ]]; then
        pass "Check6-COPY" "COPY $cpname_upper documented in analysis"
    else
        fail "Check6-COPY" "COPY $cpname_upper found in source but NOT documented"
    fi
done

echo ""

###############################################################################
# CHECK 7 — XCTL/LINK targets in program inventory
###############################################################################
echo "--- Check 7: XCTL/LINK Target Verification ---"

# The program uses dynamic XCTL: PROGRAM(CDEMO-TO-PROGRAM)
# Trace all MOVE statements that set CDEMO-TO-PROGRAM
XCTL_TARGETS=$(grep -i 'TO CDEMO-TO-PROGRAM' "$TARGET_PGM" | grep -v '^\s*\*' | sed 's/.*MOVE //' | sed 's/ *TO .*//' | tr -d "'" | sort -u)

echo "  Dynamic XCTL targets resolved from MOVE statements:"
for target in $XCTL_TARGETS; do
    target_upper=$(echo "$target" | tr '[:lower:]' '[:upper:]')
    echo "    - $target_upper"
    # Check if it's a variable reference (LIT-xxx) or literal
    if [[ "$target_upper" == "LIT-MENUPGM" ]]; then
        # Resolves to COMEN01C
        pass "Check7-XCTL" "XCTL target LIT-MENUPGM (COMEN01C) is in program inventory"
    elif [[ "$target_upper" == "CDEMO-FROM-PROGRAM" ]]; then
        # Dynamic — comes from COMMAREA, typically the calling program
        pass "Check7-XCTL" "XCTL target CDEMO-FROM-PROGRAM (dynamic — calling program) documented"
    else
        fail "Check7-XCTL" "XCTL target $target_upper not documented in program inventory"
    fi
done

echo ""

###############################################################################
# CHECK 8 — Upstream cross-reference verification
###############################################################################
echo "--- Check 8: Upstream Program Cross-Reference ---"

# Verify that COMEN01C contains a reference to COACTVWC
echo "  Checking if COMEN01C references COACTVWC..."
if grep -qi 'COACTVWC' "$CBL_DIR/COMEN01C.cbl" 2>/dev/null || \
   grep -qi 'COACTVWC' "$CPY_DIR/COMEN02Y.cpy" 2>/dev/null; then
    pass "Check8-Upstream" "COMEN01C (or its menu table COMEN02Y) references COACTVWC"
else
    fail "Check8-Upstream" "COMEN01C does NOT reference COACTVWC — upstream claim unverified"
fi

# Verify that COACTVWC references COMEN01C (for PF3 return)
echo "  Checking if COACTVWC references COMEN01C..."
if grep -qi 'COMEN01C' "$TARGET_PGM"; then
    pass "Check8-Upstream" "COACTVWC references COMEN01C (return target on PF3)"
else
    fail "Check8-Upstream" "COACTVWC does NOT reference COMEN01C"
fi

echo ""

###############################################################################
# SUMMARY
###############################################################################
echo "============================================================"
echo "VERIFICATION SUMMARY"
echo "============================================================"
echo "  Total checks:  $TOTAL"
echo "  Passed:        $PASS"
echo "  Failed:        $FAIL"
echo "============================================================"

if [[ $FAIL -eq 0 ]]; then
    echo "  RESULT: ALL CHECKS PASSED"
else
    echo "  RESULT: $FAIL FAILURE(S) — review and fix analysis"
fi

echo ""

# Generate verification report markdown
REPORT_FILE="$(dirname "$0")/COACTVWC_Verification_Report.md"
cat > "$REPORT_FILE" << HEREDOC
# COACTVWC (CAVW) Verification Report

**Generated:** $(date -u '+%Y-%m-%d %H:%M:%S UTC')
**Target Program:** COACTVWC
**Transaction ID:** CAVW

## Summary

| Metric         | Count |
|----------------|-------|
| Total Checks   | $TOTAL |
| Passed         | $PASS  |
| Failed         | $FAIL  |

**Result:** $(if [[ $FAIL -eq 0 ]]; then echo "ALL CHECKS PASSED"; else echo "$FAIL FAILURE(S)"; fi)

## Detailed Results

| Result | Check | Detail |
|--------|-------|--------|
$REPORT

## Check Descriptions

| Check | Description |
|-------|-------------|
| Check1-Program | Every program listed in the analysis exists as a .cbl file |
| Check2-Copybook | Every application copybook exists as a .cpy file |
| Check2-SysCopybook | System copybooks (DFHAID, DFHBMSCA) are correctly flagged as system |
| Check2-BMSCopybook | BMS-generated symbolic maps are correctly flagged as auto-generated |
| Check3-BMS | Every BMS mapset exists as a .bms file |
| Check4-CICS | Every EXEC CICS command in the source is documented in the analysis |
| Check5-CALL | All CALL statements (if any) are documented |
| Check6-COPY | Every COPY statement in the source is documented |
| Check7-XCTL | All XCTL/LINK targets are included in the program inventory |
| Check8-Upstream | Programs listed as upstream actually reference the target |
HEREDOC

echo "Verification report written to: $REPORT_FILE"

exit $FAIL

#!/usr/bin/env bash
###############################################################################
# verify_cousr03c_flow.sh
# Verification script for COUSR03C (CU03) Transaction Flow Analysis
# Cross-checks the analysis document against actual repository contents.
###############################################################################

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
BMS_DIR="$REPO_ROOT/app/bms"
TARGET_PGM="COUSR03C"
TARGET_SRC="$CBL_DIR/${TARGET_PGM}.cbl"

PASS=0
FAIL=0
TOTAL=0
REPORT=""

pass() {
    PASS=$((PASS + 1))
    TOTAL=$((TOTAL + 1))
    REPORT+="| PASS | $1 |"$'\n'
    echo "  PASS: $1"
}

fail() {
    FAIL=$((FAIL + 1))
    TOTAL=$((TOTAL + 1))
    REPORT+="| **FAIL** | $1 |"$'\n'
    echo "  FAIL: $1"
}

echo "============================================================"
echo " Verification: COUSR03C (CU03) Transaction Flow Analysis"
echo "============================================================"
echo ""

###############################################################################
# CHECK 1 — Program existence
###############################################################################
echo "--- Check 1: Program Existence ---"
for pgm in COUSR03C COADM01C COSGN00C COUSR00C; do
    if [[ -f "$CBL_DIR/${pgm}.cbl" ]]; then
        pass "Check 1 — Program $pgm exists as ${pgm}.cbl"
    else
        fail "Check 1 — Program $pgm NOT found at ${CBL_DIR}/${pgm}.cbl"
    fi
done
echo ""

###############################################################################
# CHECK 2 — Copybook existence
###############################################################################
echo "--- Check 2: Copybook Existence ---"

# Application copybooks (must exist in app/cpy/)
for cpy in COCOM01Y COTTL01Y CSDAT01Y CSMSG01Y CSUSR01Y COADM02Y; do
    if [[ -f "$CPY_DIR/${cpy}.cpy" ]]; then
        pass "Check 2 — Copybook $cpy exists in app/cpy/"
    else
        fail "Check 2 — Copybook $cpy NOT found in app/cpy/"
    fi
done

# BMS symbolic map copybook (in app/cpy-bms/)
if [[ -f "$REPO_ROOT/app/cpy-bms/COUSR03.CPY" ]]; then
    pass "Check 2 — BMS symbolic map COUSR03.CPY exists in app/cpy-bms/"
else
    fail "Check 2 — BMS symbolic map COUSR03.CPY NOT found in app/cpy-bms/"
fi

# System copybooks — must NOT be expected in app/cpy/
for syscpy in DFHAID DFHBMSCA; do
    if [[ ! -f "$CPY_DIR/${syscpy}.cpy" ]]; then
        pass "Check 2 — $syscpy correctly flagged as system copybook (not in app/cpy/)"
    else
        fail "Check 2 — $syscpy found in app/cpy/ but was flagged as system-only"
    fi
done
echo ""

###############################################################################
# CHECK 3 — BMS map existence
###############################################################################
echo "--- Check 3: BMS Map Existence ---"
if [[ -f "$BMS_DIR/COUSR03.bms" ]]; then
    pass "Check 3 — BMS mapset COUSR03.bms exists"
else
    fail "Check 3 — BMS mapset COUSR03.bms NOT found"
fi
echo ""

###############################################################################
# CHECK 4 — EXEC CICS extraction completeness
###############################################################################
echo "--- Check 4: EXEC CICS Command Extraction ---"

# Extract all EXEC CICS commands from COUSR03C.cbl
# Some commands appear on the same line as EXEC CICS, others on the next line.
# Use a multiline approach: collapse EXEC CICS + next line, then extract command keyword.
CICS_CMDS=$(awk '
    /EXEC CICS/ && !/^.{6}\*/ {
        line = $0
        # Check if command keyword is on same line
        sub(/.*EXEC CICS */, "", line)
        if (line ~ /^[A-Za-z]/) {
            split(line, a, " ")
            print toupper(a[1])
        } else {
            # Read next line for command keyword
            getline nextline
            sub(/^ */, "", nextline)
            split(nextline, a, " ")
            print toupper(a[1])
        }
    }
' "$TARGET_SRC" | sort -u)

# Expected commands documented in the analysis
EXPECTED_CMDS="DELETE READ RECEIVE RETURN SEND XCTL"

for cmd in $EXPECTED_CMDS; do
    if echo "$CICS_CMDS" | grep -qw "$cmd"; then
        pass "Check 4 — EXEC CICS $cmd documented and present in source"
    else
        fail "Check 4 — EXEC CICS $cmd documented but NOT found in source"
    fi
done

# Check for undocumented commands
for cmd in $CICS_CMDS; do
    if ! echo "$EXPECTED_CMDS" | grep -qw "$cmd"; then
        fail "Check 4 — EXEC CICS $cmd found in source but NOT documented"
    fi
done
echo ""

###############################################################################
# CHECK 5 — CALL extraction
###############################################################################
echo "--- Check 5: CALL Statement Extraction ---"

# Count CALL statements excluding comment lines (column 7 = *)
CALL_COUNT_NOCOMMENT=$(grep -i 'CALL ' "$TARGET_SRC" | grep -v '^.\{6\}\*' | grep -vc 'EIBCALEN' || true)
CALL_COUNT_NOCOMMENT=${CALL_COUNT_NOCOMMENT:-0}

if [[ "$CALL_COUNT_NOCOMMENT" -eq 0 ]]; then
    pass "Check 5 — No CALL statements in source (analysis correctly states 0 CALLs)"
else
    fail "Check 5 — Found $CALL_COUNT_NOCOMMENT CALL statement(s) in source but analysis states 0"
fi
echo ""

###############################################################################
# CHECK 6 — COPY extraction completeness
###############################################################################
echo "--- Check 6: COPY Statement Extraction ---"

# Extract all COPY statements from the source (excluding comment lines)
# COBOL lines: columns 1-6 are sequence, col 7 is indicator (* = comment), cols 8-72 are code
COPY_STMTS=$(grep -i 'COPY ' "$TARGET_SRC" | grep -v '^.\{6\}\*' | grep -vi 'copy of' | sed 's/.*COPY  *//' | sed 's/[. ].*//' | sort -u)

# Expected copybooks documented
EXPECTED_COPIES="COCOM01Y COUSR03 COTTL01Y CSDAT01Y CSMSG01Y CSUSR01Y DFHAID DFHBMSCA"

for cpy in $EXPECTED_COPIES; do
    if echo "$COPY_STMTS" | grep -qw "$cpy"; then
        pass "Check 6 — COPY $cpy documented and present in source"
    else
        fail "Check 6 — COPY $cpy documented but NOT found in source"
    fi
done

# Check for undocumented COPY statements
for cpy in $COPY_STMTS; do
    if ! echo "$EXPECTED_COPIES" | grep -qw "$cpy"; then
        fail "Check 6 — COPY $cpy found in source but NOT documented"
    fi
done
echo ""

###############################################################################
# CHECK 7 — XCTL/LINK target verification
###############################################################################
echo "--- Check 7: XCTL/LINK Target Verification ---"

# The program uses dynamic XCTL via CDEMO-TO-PROGRAM variable
# Resolved targets from analysis: COSGN00C, COADM01C, and dynamically CDEMO-FROM-PROGRAM
# All XCTL targets should be in the program inventory

# Check that COSGN00C is referenced (via MOVE to CDEMO-TO-PROGRAM)
if grep -q "COSGN00C" "$TARGET_SRC"; then
    pass "Check 7 — XCTL target COSGN00C referenced in source"
else
    fail "Check 7 — XCTL target COSGN00C NOT referenced in source"
fi

# Check that COADM01C is referenced
if grep -q "COADM01C" "$TARGET_SRC"; then
    pass "Check 7 — XCTL target COADM01C referenced in source"
else
    fail "Check 7 — XCTL target COADM01C NOT referenced in source"
fi

# Verify the XCTL uses CDEMO-TO-PROGRAM (dynamic)
if grep -q "XCTL PROGRAM(CDEMO-TO-PROGRAM)" "$TARGET_SRC"; then
    pass "Check 7 — Dynamic XCTL via CDEMO-TO-PROGRAM confirmed in source"
else
    fail "Check 7 — Dynamic XCTL via CDEMO-TO-PROGRAM NOT found in source"
fi

# Verify no LINK statements exist (analysis says 0 LINK)
if grep -qi 'EXEC CICS.*LINK' "$TARGET_SRC" 2>/dev/null; then
    fail "Check 7 — LINK statement found in source but not documented"
else
    pass "Check 7 — No LINK statements (consistent with analysis)"
fi
echo ""

###############################################################################
# CHECK 8 — Cross-reference: upstream programs reference the target
###############################################################################
echo "--- Check 8: Upstream Cross-Reference ---"

# COADM01C should reference COUSR03C via the menu table (COADM02Y copybook)
if grep -q "COUSR03C" "$CPY_DIR/COADM02Y.cpy"; then
    pass "Check 8 — COADM01C admin menu table (COADM02Y) references COUSR03C"
else
    fail "Check 8 — COADM01C admin menu table (COADM02Y) does NOT reference COUSR03C"
fi

# COUSR00C should have XCTL to COUSR03C
if grep -q "COUSR03C" "$CBL_DIR/COUSR00C.cbl"; then
    pass "Check 8 — COUSR00C references COUSR03C (XCTL on 'D' selection)"
else
    fail "Check 8 — COUSR00C does NOT reference COUSR03C"
fi

# COSGN00C should XCTL to COADM01C (upstream chain)
if grep -q "COADM01C" "$CBL_DIR/COSGN00C.cbl"; then
    pass "Check 8 — COSGN00C references COADM01C (upstream chain verified)"
else
    fail "Check 8 — COSGN00C does NOT reference COADM01C"
fi
echo ""

###############################################################################
# SUMMARY
###############################################################################
echo "============================================================"
echo " VERIFICATION SUMMARY"
echo "============================================================"
echo "  Total checks : $TOTAL"
echo "  Passed       : $PASS"
echo "  Failed       : $FAIL"
echo "============================================================"

if [[ $FAIL -eq 0 ]]; then
    echo "  RESULT: ALL CHECKS PASSED"
else
    echo "  RESULT: $FAIL FAILURE(S) DETECTED"
fi
echo ""

###############################################################################
# Generate verification report markdown
###############################################################################
REPORT_FILE="$(dirname "$0")/COUSR03C_Verification_Report.md"
cat > "$REPORT_FILE" <<EOF
# COUSR03C (CU03) Verification Report

**Generated:** $(date -u '+%Y-%m-%d %H:%M:%S UTC')
**Target Program:** COUSR03C
**Transaction ID:** CU03
**Analysis File:** COUSR03C_Flow_Analysis.md

---

## Summary

| Metric        | Count |
|---------------|-------|
| Total Checks  | $TOTAL |
| Passed        | $PASS  |
| Failed        | $FAIL  |
| **Result**    | $(if [[ $FAIL -eq 0 ]]; then echo "**ALL CHECKS PASSED**"; else echo "**$FAIL FAILURE(S)**"; fi) |

---

## Detailed Results

| Result | Check Description |
|--------|-------------------|
$REPORT

---

## Check Categories

### Check 1 — Program Existence
Verifies that every program listed in the analysis exists as a \`.cbl\` file in \`app/cbl/\`.

### Check 2 — Copybook Existence
Verifies that every application copybook exists in \`app/cpy/\`, BMS symbolic maps exist in \`app/cpy-bms/\`, and system copybooks (DFHAID, DFHBMSCA) are correctly flagged as IBM-supplied (not expected in app/cpy/).

### Check 3 — BMS Map Existence
Verifies that the BMS mapset source file exists in \`app/bms/\`.

### Check 4 — EXEC CICS Extraction
Verifies that every \`EXEC CICS\` command in COUSR03C.cbl is documented in the analysis, and no undocumented commands exist.

### Check 5 — CALL Extraction
Verifies that every \`CALL\` statement in the source is documented. COUSR03C has no CALL statements.

### Check 6 — COPY Extraction
Verifies that every \`COPY\` statement in COUSR03C.cbl is documented in the analysis, and no undocumented COPY statements exist.

### Check 7 — XCTL/LINK Target Verification
Verifies that all XCTL targets are included in the program inventory and that the dynamic XCTL pattern is correctly documented.

### Check 8 — Upstream Cross-Reference
Verifies that programs listed as upstream callers actually contain references to COUSR03C.
EOF

echo "Verification report written to: $REPORT_FILE"

exit $FAIL

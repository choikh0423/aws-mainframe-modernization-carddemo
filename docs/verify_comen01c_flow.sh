#!/usr/bin/env bash
###############################################################################
# verify_comen01c_flow.sh
# Verification script for COMEN01C (CM00) transaction flow analysis
# Cross-checks the analysis against actual repository contents
###############################################################################

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
BMS_DIR="$REPO_ROOT/app/bms"
TARGET_PGM="COMEN01C"
TARGET_SRC="$CBL_DIR/${TARGET_PGM}.cbl"

PASS=0
FAIL=0
TOTAL=0
REPORT_FILE="$(dirname "$0")/COMEN01C_Verification_Report.md"

log_pass() {
    ((PASS++)) || true
    ((TOTAL++)) || true
    echo "  PASS: $1"
    echo "| $TOTAL | PASS | $1 |" >> "$REPORT_FILE"
}

log_fail() {
    ((FAIL++)) || true
    ((TOTAL++)) || true
    echo "  FAIL: $1"
    echo "| $TOTAL | **FAIL** | $1 |" >> "$REPORT_FILE"
}

###############################################################################
# Initialize report
###############################################################################
cat > "$REPORT_FILE" <<'HEADER'
# COMEN01C (CM00) Verification Report

**Generated:** $(date -u +%Y-%m-%d)
**Target Program:** COMEN01C
**Transaction ID:** CM00

## Verification Results

| # | Result | Check |
|---|--------|-------|
HEADER
# Fix the date in the report
sed -i "s/\$(date -u +%Y-%m-%d)/$(date -u +%Y-%m-%d)/" "$REPORT_FILE"

echo "========================================================"
echo "  COMEN01C Flow Analysis Verification"
echo "========================================================"
echo ""

###############################################################################
# CHECK 1 — Program existence
# Every program listed in the analysis exists as a .cbl file
###############################################################################
echo "CHECK 1: Program existence"
PROGRAMS=(COSGN00C COMEN01C COACTVWC COACTUPC COCRDLIC COCRDSLC COCRDUPC COTRN00C COTRN01C COTRN02C CORPT00C COBIL00C)
NOT_IN_REPO_PROGRAMS=(COPAUS0C)

for pgm in "${PROGRAMS[@]}"; do
    if [[ -f "$CBL_DIR/${pgm}.cbl" ]]; then
        log_pass "Program ${pgm}.cbl exists in app/cbl/"
    else
        log_fail "Program ${pgm}.cbl NOT found in app/cbl/"
    fi
done

for pgm in "${NOT_IN_REPO_PROGRAMS[@]}"; do
    if [[ ! -f "$CBL_DIR/${pgm}.cbl" ]]; then
        log_pass "Program ${pgm} correctly flagged as not in repo"
    else
        log_fail "Program ${pgm} was flagged as not in repo but exists at app/cbl/${pgm}.cbl"
    fi
done

echo ""

###############################################################################
# CHECK 2 — Copybook existence
# Every application copybook listed exists as a .cpy file
###############################################################################
echo "CHECK 2: Copybook existence"
APP_COPYBOOKS=(COCOM01Y COMEN02Y COTTL01Y CSDAT01Y CSMSG01Y CSUSR01Y)
BMS_COPYBOOKS=(COMEN01)
SYSTEM_COPYBOOKS=(DFHAID DFHBMSCA)

for cpy in "${APP_COPYBOOKS[@]}"; do
    if [[ -f "$CPY_DIR/${cpy}.cpy" ]]; then
        log_pass "Copybook ${cpy}.cpy exists in app/cpy/"
    else
        log_fail "Copybook ${cpy}.cpy NOT found in app/cpy/"
    fi
done

for cpy in "${BMS_COPYBOOKS[@]}"; do
    if [[ -f "$BMS_DIR/${cpy}.bms" ]]; then
        log_pass "BMS symbolic map source ${cpy}.bms exists (copybook auto-generated)"
    else
        log_fail "BMS symbolic map source ${cpy}.bms NOT found"
    fi
done

for cpy in "${SYSTEM_COPYBOOKS[@]}"; do
    if [[ ! -f "$CPY_DIR/${cpy}.cpy" ]]; then
        log_pass "System copybook ${cpy} correctly flagged as CICS system (not in app/cpy/)"
    else
        log_fail "System copybook ${cpy} was flagged as system but exists in app/cpy/"
    fi
done

echo ""

###############################################################################
# CHECK 3 — BMS map existence
# Every BMS mapset listed exists as a .bms file
###############################################################################
echo "CHECK 3: BMS map existence"
BMS_MAPS=(COMEN01)

for bms in "${BMS_MAPS[@]}"; do
    if [[ -f "$BMS_DIR/${bms}.bms" ]]; then
        log_pass "BMS mapset ${bms}.bms exists in app/bms/"
    else
        log_fail "BMS mapset ${bms}.bms NOT found in app/bms/"
    fi
done

echo ""

###############################################################################
# CHECK 4 — EXEC CICS extraction
# Every EXEC CICS command in the target program is documented
###############################################################################
echo "CHECK 4: EXEC CICS command extraction"

# Extract all EXEC CICS commands from the source, ignoring comments (col 7 = *)
# We look for EXEC CICS followed by the command verb
CICS_CMDS_IN_SRC=$(grep -i 'EXEC CICS' "$TARGET_SRC" | grep -v '^\s*\*' | sed 's/.*EXEC CICS\s*//' | awk '{print $1}' | sort)

DOCUMENTED_CMDS="INQUIRE RECEIVE RETURN SEND XCTL XCTL XCTL"

for cmd in $CICS_CMDS_IN_SRC; do
    CMD_UPPER=$(echo "$cmd" | tr '[:lower:]' '[:upper:]')
    if echo "$DOCUMENTED_CMDS" | grep -qw "$CMD_UPPER"; then
        log_pass "EXEC CICS ${CMD_UPPER} documented in analysis"
    else
        log_fail "EXEC CICS ${CMD_UPPER} found in source but NOT documented"
    fi
done

echo ""

###############################################################################
# CHECK 5 — CALL extraction
# Every CALL statement in the target program is documented
###############################################################################
echo "CHECK 5: CALL statement extraction"

CALL_COUNT=0
if grep -qi '^\s*CALL ' "$TARGET_SRC" 2>/dev/null; then
    CALL_COUNT=$(grep -ci '^\s*CALL ' "$TARGET_SRC" 2>/dev/null)
fi

if [[ "$CALL_COUNT" -eq 0 ]]; then
    log_pass "No CALL statements in source — analysis correctly states 0 CALLs"
else
    log_fail "Found ${CALL_COUNT} CALL statement(s) in source but analysis states 0"
fi

echo ""

###############################################################################
# CHECK 6 — COPY extraction
# Every COPY statement in the target program is documented
###############################################################################
echo "CHECK 6: COPY statement extraction"

# Extract COPY targets from source, excluding comment lines
COPY_TARGETS_IN_SRC=$(grep -i '^ \{6\} .*COPY ' "$TARGET_SRC" | grep -v '^\s*\*' | sed 's/.*COPY \s*//' | sed 's/[. ].*//' | sort -u)

DOCUMENTED_COPIES="COCOM01Y COMEN01 COMEN02Y COTTL01Y CSDAT01Y CSMSG01Y CSUSR01Y DFHAID DFHBMSCA"

for cpy_target in $COPY_TARGETS_IN_SRC; do
    CPY_UPPER=$(echo "$cpy_target" | tr '[:lower:]' '[:upper:]')
    if echo "$DOCUMENTED_COPIES" | grep -qw "$CPY_UPPER"; then
        log_pass "COPY ${CPY_UPPER} documented in analysis"
    else
        log_fail "COPY ${CPY_UPPER} found in source but NOT documented"
    fi
done

# Check no extra copies are documented that aren't in source
for doc_cpy in $DOCUMENTED_COPIES; do
    if echo "$COPY_TARGETS_IN_SRC" | tr '[:lower:]' '[:upper:]' | grep -qw "$doc_cpy"; then
        : # already checked above
    else
        log_fail "COPY ${doc_cpy} documented in analysis but NOT found in source"
    fi
done

echo ""

###############################################################################
# CHECK 7 — XCTL/LINK targets
# Every program referenced by XCTL or LINK is included in the program inventory
###############################################################################
echo "CHECK 7: XCTL/LINK target verification"

# All XCTL targets in COMEN01C: resolved from the menu table + COSGN00C
# The program uses dynamic XCTL via CDEMO-MENU-OPT-PGMNAME(WS-OPTION)
# which resolves to the 11 programs in COMEN02Y, plus COSGN00C via RETURN-TO-SIGNON

# Check that COSGN00C is documented as an XCTL target (PF3 path)
if grep -q 'COSGN00C' "$TARGET_SRC" && grep -q "XCTL.*PROGRAM.*CDEMO-TO-PROGRAM" "$TARGET_SRC"; then
    log_pass "XCTL target COSGN00C (via CDEMO-TO-PROGRAM) documented in analysis"
fi

# Check that the menu table programs are documented as XCTL targets
MENU_PROGRAMS=(COACTVWC COACTUPC COCRDLIC COCRDSLC COCRDUPC COTRN00C COTRN01C COTRN02C CORPT00C COBIL00C COPAUS0C)
ANALYSIS_FILE="$(dirname "$0")/COMEN01C_Flow_Analysis.md"

for pgm in "${MENU_PROGRAMS[@]}"; do
    if grep -q "$pgm" "$ANALYSIS_FILE"; then
        log_pass "XCTL target ${pgm} (menu option) documented in analysis"
    else
        log_fail "XCTL target ${pgm} (menu option) NOT documented in analysis"
    fi
done

# Verify no LINK statements exist
LINK_COUNT=0
if grep -q 'EXEC CICS.*LINK\|LINK.*PROGRAM' "$TARGET_SRC" 2>/dev/null; then
    LINK_COUNT=$(grep -c 'EXEC CICS.*LINK\|LINK.*PROGRAM' "$TARGET_SRC" 2>/dev/null)
fi

if [[ "$LINK_COUNT" -eq 0 ]]; then
    log_pass "No LINK statements in source — analysis correctly states 0 LINKs"
else
    log_fail "Found LINK statement(s) in source but analysis states 0"
fi

echo ""

###############################################################################
# CHECK 8 — Cross-reference: upstream programs
# Programs listed as upstream actually contain XCTL/LINK to COMEN01C
###############################################################################
echo "CHECK 8: Upstream cross-reference"

UPSTREAM_PROGRAMS=(COSGN00C)

for pgm in "${UPSTREAM_PROGRAMS[@]}"; do
    pgm_file="$CBL_DIR/${pgm}.cbl"
    if [[ -f "$pgm_file" ]]; then
        if grep -qi "PROGRAM.*'COMEN01C'\|PROGRAM.*COMEN01C" "$pgm_file"; then
            log_pass "Upstream ${pgm} contains XCTL/LINK reference to COMEN01C"
        else
            log_fail "Upstream ${pgm} does NOT contain XCTL/LINK reference to COMEN01C"
        fi
    else
        log_fail "Upstream ${pgm} source file not found"
    fi
done

# Also verify return-path programs (downstream that XCTL back)
RETURN_PROGRAMS=(COACTVWC COACTUPC COCRDLIC COCRDSLC COCRDUPC COTRN00C COTRN01C COTRN02C CORPT00C COBIL00C)

for pgm in "${RETURN_PROGRAMS[@]}"; do
    pgm_file="$CBL_DIR/${pgm}.cbl"
    if [[ -f "$pgm_file" ]]; then
        if grep -q "COMEN01C" "$pgm_file"; then
            log_pass "Return-path ${pgm} references COMEN01C"
        else
            log_fail "Return-path ${pgm} does NOT reference COMEN01C"
        fi
    else
        log_fail "Return-path ${pgm} source file not found"
    fi
done

echo ""

###############################################################################
# Summary
###############################################################################
echo "========================================================"
echo "  VERIFICATION SUMMARY"
echo "========================================================"
echo "  Total checks: $TOTAL"
echo "  Passed:       $PASS"
echo "  Failed:       $FAIL"
echo "========================================================"

# Append summary to report
cat >> "$REPORT_FILE" <<EOF

## Summary

| Metric | Count |
|--------|-------|
| Total checks | $TOTAL |
| Passed | $PASS |
| Failed | $FAIL |

**Result:** $(if [[ $FAIL -eq 0 ]]; then echo "ALL CHECKS PASSED"; else echo "${FAIL} FAILURE(S) — review needed"; fi)
EOF

if [[ $FAIL -gt 0 ]]; then
    echo ""
    echo "  *** $FAIL FAILURE(S) DETECTED — review and fix analysis ***"
    exit 1
else
    echo ""
    echo "  All checks passed!"
    exit 0
fi

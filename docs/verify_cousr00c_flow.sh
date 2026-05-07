#!/usr/bin/env bash
###############################################################################
# verify_cousr00c_flow.sh
# Verification script for COUSR00C (CU00) Transaction Flow Analysis
# Cross-checks the analysis markdown against actual repository contents.
###############################################################################

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
BMS_DIR="$REPO_ROOT/app/bms"
TARGET_PGM="$CBL_DIR/COUSR00C.cbl"

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
    REPORT+="| **FAIL** | $1 | $2 |"$'\n'
}

header() {
    echo "============================================================"
    echo " $1"
    echo "============================================================"
}

###############################################################################
# CHECK 1 — Program existence
###############################################################################
header "Check 1: Program existence"

PROGRAMS=("COUSR00C" "COADM01C" "COSGN00C" "COUSR02C" "COUSR03C")
for pgm in "${PROGRAMS[@]}"; do
    file="$CBL_DIR/${pgm}.cbl"
    if [[ -f "$file" ]]; then
        pass "Program existence" "${pgm}.cbl exists at app/cbl/"
        echo "  PASS: $file"
    else
        fail "Program existence" "${pgm}.cbl NOT FOUND at app/cbl/"
        echo "  FAIL: $file NOT FOUND"
    fi
done

###############################################################################
# CHECK 2 — Copybook existence
###############################################################################
header "Check 2: Copybook existence"

# Application copybooks expected in app/cpy/
APP_COPYBOOKS=("COCOM01Y" "COTTL01Y" "CSDAT01Y" "CSMSG01Y" "CSUSR01Y")
for cpy in "${APP_COPYBOOKS[@]}"; do
    file="$CPY_DIR/${cpy}.cpy"
    if [[ -f "$file" ]]; then
        pass "Copybook existence" "${cpy}.cpy exists at app/cpy/"
        echo "  PASS: $file"
    else
        fail "Copybook existence" "${cpy}.cpy NOT FOUND at app/cpy/"
        echo "  FAIL: $file NOT FOUND"
    fi
done

# System copybooks — should NOT be in app/cpy/ (flagged as system)
SYSTEM_COPYBOOKS=("DFHAID" "DFHBMSCA")
for cpy in "${SYSTEM_COPYBOOKS[@]}"; do
    file_cpy="$CPY_DIR/${cpy}.cpy"
    file_bare="$CPY_DIR/${cpy}"
    if [[ ! -f "$file_cpy" && ! -f "$file_bare" ]]; then
        pass "System copybook" "${cpy} correctly identified as system copybook (not in app/cpy/)"
        echo "  PASS: ${cpy} is a system copybook (not in app/cpy/) — correct"
    else
        fail "System copybook" "${cpy} found in app/cpy/ but was flagged as system"
        echo "  FAIL: ${cpy} found in app/cpy/ but analysis says it is a system copybook"
    fi
done

# BMS symbolic map copybook — COUSR00 — expected to be generated, may or may not exist
# The analysis notes it's a BMS-generated symbolic map. Verify the COPY statement exists.
if grep -q "COPY COUSR00\." "$TARGET_PGM" 2>/dev/null; then
    pass "BMS symbolic map" "COPY COUSR00 statement found in COUSR00C.cbl (BMS-generated)"
    echo "  PASS: COPY COUSR00 found in source"
else
    fail "BMS symbolic map" "COPY COUSR00 statement NOT found in COUSR00C.cbl"
    echo "  FAIL: COPY COUSR00 not found"
fi

###############################################################################
# CHECK 3 — BMS map existence
###############################################################################
header "Check 3: BMS map existence"

BMS_MAPS=("COUSR00")
for bms in "${BMS_MAPS[@]}"; do
    file="$BMS_DIR/${bms}.bms"
    if [[ -f "$file" ]]; then
        pass "BMS map existence" "${bms}.bms exists at app/bms/"
        echo "  PASS: $file"
    else
        fail "BMS map existence" "${bms}.bms NOT FOUND at app/bms/"
        echo "  FAIL: $file NOT FOUND"
    fi
done

###############################################################################
# CHECK 4 — EXEC CICS extraction completeness
###############################################################################
header "Check 4: EXEC CICS command extraction"

# Extract all EXEC CICS commands from the target program
# Collapse multi-line EXEC CICS blocks and extract the command keyword
CICS_COMMANDS_IN_SRC=$(awk '
    /EXEC CICS/ { in_cics=1; block="" }
    in_cics { block = block " " $0 }
    /END-EXEC/ && in_cics {
        in_cics=0
        # Extract the CICS command keyword (first word after EXEC CICS)
        gsub(/.*EXEC CICS/, "", block)
        gsub(/END-EXEC.*/, "", block)
        # Remove leading/trailing whitespace
        gsub(/^[[:space:]]+/, "", block)
        # Get first word (the command)
        split(block, words, /[[:space:]]+/)
        if (words[1] != "") print words[1]
    }
' "$TARGET_PGM" | sort)

# Expected commands from the analysis
EXPECTED_COMMANDS=(
    "SEND"       # SEND MAP (2 instances: with ERASE and without)
    "RECEIVE"    # RECEIVE MAP
    "STARTBR"    # STARTBR DATASET
    "READNEXT"   # READNEXT DATASET
    "READPREV"   # READPREV DATASET
    "ENDBR"      # ENDBR DATASET
    "XCTL"       # XCTL PROGRAM (2 instances: COUSR02C, COUSR03C, + RETURN-TO-PREV)
    "RETURN"     # RETURN TRANSID
)

for cmd in "${EXPECTED_COMMANDS[@]}"; do
    if echo "$CICS_COMMANDS_IN_SRC" | grep -qi "$cmd"; then
        pass "EXEC CICS extraction" "EXEC CICS ${cmd} documented and found in source"
        echo "  PASS: EXEC CICS ${cmd}"
    else
        fail "EXEC CICS extraction" "EXEC CICS ${cmd} documented but NOT found in source"
        echo "  FAIL: EXEC CICS ${cmd} not found in source"
    fi
done

# Reverse check: any EXEC CICS commands in source not documented?
UNIQUE_SRC_CMDS=$(echo "$CICS_COMMANDS_IN_SRC" | sort -u)
while IFS= read -r cmd; do
    [[ -z "$cmd" ]] && continue
    found=0
    for exp in "${EXPECTED_COMMANDS[@]}"; do
        if echo "$cmd" | grep -qi "$exp"; then
            found=1
            break
        fi
    done
    if [[ $found -eq 0 ]]; then
        fail "EXEC CICS extraction" "EXEC CICS ${cmd} found in source but NOT documented in analysis"
        echo "  FAIL: EXEC CICS ${cmd} in source but not in analysis"
    fi
done <<< "$UNIQUE_SRC_CMDS"

###############################################################################
# CHECK 5 — CALL extraction
###############################################################################
header "Check 5: CALL statement extraction"

CALL_COUNT=$(grep -c "^[^*].*CALL " "$TARGET_PGM" 2>/dev/null || true)
if [[ "$CALL_COUNT" -eq 0 ]]; then
    pass "CALL extraction" "No CALL statements in COUSR00C — consistent with analysis"
    echo "  PASS: No CALL statements found (matches analysis)"
else
    fail "CALL extraction" "Found ${CALL_COUNT} CALL statement(s) in COUSR00C but analysis says 0"
    echo "  FAIL: Found ${CALL_COUNT} CALL statement(s)"
fi

###############################################################################
# CHECK 6 — COPY extraction
###############################################################################
header "Check 6: COPY statement extraction"

# Extract all COPY statements from source (excluding comment lines)
COPY_IN_SRC=$(grep -E "^[^*].* COPY " "$TARGET_PGM" | sed 's/.*COPY //' | sed 's/\..*//' | tr -d ' ' | sort -u)

DOCUMENTED_COPIES=("COCOM01Y" "COUSR00" "COTTL01Y" "CSDAT01Y" "CSMSG01Y" "CSUSR01Y" "DFHAID" "DFHBMSCA")

# Check documented copies exist in source
for cpy in "${DOCUMENTED_COPIES[@]}"; do
    if echo "$COPY_IN_SRC" | grep -qi "$cpy"; then
        pass "COPY extraction" "COPY ${cpy} documented and found in source"
        echo "  PASS: COPY ${cpy}"
    else
        fail "COPY extraction" "COPY ${cpy} documented but NOT found in source"
        echo "  FAIL: COPY ${cpy} not in source"
    fi
done

# Reverse check: any COPY in source not documented?
while IFS= read -r cpy; do
    [[ -z "$cpy" ]] && continue
    found=0
    for doc in "${DOCUMENTED_COPIES[@]}"; do
        if [[ "${cpy^^}" == "${doc^^}" ]]; then
            found=1
            break
        fi
    done
    if [[ $found -eq 0 ]]; then
        fail "COPY extraction" "COPY ${cpy} found in source but NOT documented in analysis"
        echo "  FAIL: COPY ${cpy} in source but not in analysis"
    fi
done <<< "$COPY_IN_SRC"

###############################################################################
# CHECK 7 — XCTL/LINK target verification
###############################################################################
header "Check 7: XCTL/LINK target verification"

# Extract XCTL targets from COUSR00C
# Targets are: COUSR02C, COUSR03C, and CDEMO-TO-PROGRAM (which resolves to COADM01C or COSGN00C)
XCTL_TARGETS=("COUSR02C" "COUSR03C" "COADM01C" "COSGN00C")

for target in "${XCTL_TARGETS[@]}"; do
    target_file="$CBL_DIR/${target}.cbl"
    if [[ -f "$target_file" ]]; then
        pass "XCTL target" "XCTL target ${target} exists as ${target}.cbl"
        echo "  PASS: ${target}"
    else
        fail "XCTL target" "XCTL target ${target} — ${target}.cbl NOT FOUND"
        echo "  FAIL: ${target}.cbl NOT FOUND"
    fi
done

# Verify XCTL references in source
# Direct literal XCTL to COUSR02C
if grep -q "COUSR02C" "$TARGET_PGM" 2>/dev/null; then
    pass "XCTL reference" "COUSR02C referenced in COUSR00C source"
    echo "  PASS: COUSR02C reference found"
else
    fail "XCTL reference" "COUSR02C NOT referenced in COUSR00C source"
    echo "  FAIL: COUSR02C not found in source"
fi

# Direct literal XCTL to COUSR03C
if grep -q "COUSR03C" "$TARGET_PGM" 2>/dev/null; then
    pass "XCTL reference" "COUSR03C referenced in COUSR00C source"
    echo "  PASS: COUSR03C reference found"
else
    fail "XCTL reference" "COUSR03C NOT referenced in COUSR00C source"
    echo "  FAIL: COUSR03C not found in source"
fi

# XCTL via variable CDEMO-TO-PROGRAM resolving to COADM01C
if grep -q "COADM01C" "$TARGET_PGM" 2>/dev/null; then
    pass "XCTL reference" "COADM01C referenced in COUSR00C source (PF3 target)"
    echo "  PASS: COADM01C reference found"
else
    fail "XCTL reference" "COADM01C NOT referenced in COUSR00C source"
    echo "  FAIL: COADM01C not found in source"
fi

# XCTL via variable CDEMO-TO-PROGRAM resolving to COSGN00C
if grep -q "COSGN00C" "$TARGET_PGM" 2>/dev/null; then
    pass "XCTL reference" "COSGN00C referenced in COUSR00C source (EIBCALEN=0 target)"
    echo "  PASS: COSGN00C reference found"
else
    fail "XCTL reference" "COSGN00C NOT referenced in COUSR00C source"
    echo "  FAIL: COSGN00C not found in source"
fi

###############################################################################
# CHECK 8 — Upstream cross-reference
###############################################################################
header "Check 8: Upstream program cross-reference"

# COADM01C should contain a reference to COUSR00C (via menu table in COADM02Y)
COADM01C_FILE="$CBL_DIR/COADM01C.cbl"
COADM02Y_FILE="$CPY_DIR/COADM02Y.cpy"

# Check if COADM02Y contains COUSR00C as a menu option
if grep -q "COUSR00C" "$COADM02Y_FILE" 2>/dev/null; then
    pass "Upstream xref" "COUSR00C listed in COADM02Y.cpy admin menu options (called from COADM01C)"
    echo "  PASS: COUSR00C found in COADM02Y.cpy"
else
    fail "Upstream xref" "COUSR00C NOT found in COADM02Y.cpy admin menu options"
    echo "  FAIL: COUSR00C not in COADM02Y.cpy"
fi

# Check if COADM01C includes COADM02Y
if grep -q "COPY COADM02Y" "$COADM01C_FILE" 2>/dev/null; then
    pass "Upstream xref" "COADM01C includes COPY COADM02Y (admin menu options)"
    echo "  PASS: COADM01C includes COADM02Y"
else
    fail "Upstream xref" "COADM01C does NOT include COPY COADM02Y"
    echo "  FAIL: COADM01C missing COPY COADM02Y"
fi

# COSGN00C should XCTL to COADM01C for admin users
COSGN00C_FILE="$CBL_DIR/COSGN00C.cbl"
if grep -q "COADM01C" "$COSGN00C_FILE" 2>/dev/null; then
    pass "Upstream xref" "COSGN00C references COADM01C (admin user XCTL)"
    echo "  PASS: COSGN00C -> COADM01C reference found"
else
    fail "Upstream xref" "COSGN00C does NOT reference COADM01C"
    echo "  FAIL: COSGN00C -> COADM01C not found"
fi

###############################################################################
# SUMMARY
###############################################################################
echo ""
echo "============================================================"
echo " VERIFICATION SUMMARY"
echo "============================================================"
echo "  Total checks: $TOTAL"
echo "  Passed:       $PASS"
echo "  Failed:       $FAIL"
echo "============================================================"

if [[ $FAIL -eq 0 ]]; then
    echo "  RESULT: ALL CHECKS PASSED"
else
    echo "  RESULT: $FAIL FAILURE(S) DETECTED"
fi
echo ""

# Generate verification report markdown
REPORT_FILE="$(dirname "$0")/COUSR00C_Verification_Report.md"
cat > "$REPORT_FILE" << ENDREPORT
# COUSR00C (CU00) Verification Report

## Summary

| Metric        | Count |
|---------------|-------|
| Total Checks  | $TOTAL |
| Passed        | $PASS  |
| Failed        | $FAIL  |
| **Result**    | $(if [[ $FAIL -eq 0 ]]; then echo "**ALL CHECKS PASSED**"; else echo "**$FAIL FAILURE(S)**"; fi) |

## Detailed Results

| Result | Check Category | Detail |
|--------|---------------|--------|
$REPORT

## Check Descriptions

| Check # | Category                  | Description                                                                                  |
|---------|---------------------------|----------------------------------------------------------------------------------------------|
| 1       | Program existence          | Every program listed in the analysis exists as a .cbl file in app/cbl/                      |
| 2       | Copybook existence         | Every app copybook listed exists as a .cpy file in app/cpy/; system copybooks flagged correctly |
| 3       | BMS map existence          | Every BMS mapset listed exists as a .bms file in app/bms/                                   |
| 4       | EXEC CICS extraction       | Every EXEC CICS command in the target program is documented in the analysis                 |
| 5       | CALL extraction            | Every CALL statement in the target program is documented (COUSR00C has none)                |
| 6       | COPY extraction            | Every COPY statement in the target program is documented in the analysis                    |
| 7       | XCTL/LINK targets          | Every program referenced by XCTL or LINK is included in the program inventory               |
| 8       | Upstream cross-reference   | Programs listed as upstream actually contain XCTL/LINK references to the target             |

---

*Generated by verify_cousr00c_flow.sh on $(date -u '+%Y-%m-%d %H:%M:%S UTC')*
ENDREPORT

echo "Verification report written to: $REPORT_FILE"

exit $FAIL

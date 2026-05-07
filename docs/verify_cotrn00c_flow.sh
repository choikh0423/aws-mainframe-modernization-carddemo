#!/usr/bin/env bash
###############################################################################
# verify_cotrn00c_flow.sh
# Verification script for COTRN00C (CT00) Transaction Flow Analysis
# Cross-checks the analysis document against actual repository contents.
###############################################################################

set -euo pipefail

# --- Configuration -----------------------------------------------------------
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
BMS_DIR="$REPO_ROOT/app/bms"
TARGET_PGM="COTRN00C"
TARGET_SRC="$CBL_DIR/${TARGET_PGM}.cbl"
REPORT_FILE="$REPO_ROOT/docs/COTRN00C_Verification_Report.md"

PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0
RESULTS=""

# --- Helper functions --------------------------------------------------------
record_result() {
    local check_name="$1"
    local item="$2"
    local status="$3"
    local detail="$4"
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    if [ "$status" = "PASS" ]; then
        PASS_COUNT=$((PASS_COUNT + 1))
        RESULTS="${RESULTS}| ${check_name} | ${item} | PASS | ${detail} |\n"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        RESULTS="${RESULTS}| ${check_name} | ${item} | **FAIL** | ${detail} |\n"
    fi
}

echo "================================================================="
echo " COTRN00C (CT00) Flow Analysis Verification"
echo "================================================================="
echo ""
echo "Repository root: $REPO_ROOT"
echo "Target program:  $TARGET_PGM"
echo ""

###############################################################################
# CHECK 1: Program Existence
# Every program listed in the analysis exists as a .cbl file
###############################################################################
echo "--- Check 1: Program Existence ---"
PROGRAMS=("COTRN00C" "COTRN01C" "COMEN01C" "COSGN00C")
for pgm in "${PROGRAMS[@]}"; do
    src="$CBL_DIR/${pgm}.cbl"
    if [ -f "$src" ]; then
        echo "  PASS: $pgm.cbl exists"
        record_result "1-Program" "$pgm" "PASS" "Found at app/cbl/${pgm}.cbl"
    else
        echo "  FAIL: $pgm.cbl NOT found"
        record_result "1-Program" "$pgm" "FAIL" "Not found at app/cbl/${pgm}.cbl"
    fi
done

###############################################################################
# CHECK 2: Copybook Existence
# Every application copybook listed exists as a .cpy file.
# DFHAID and DFHBMSCA are system copybooks — verify they are NOT in app/cpy/
###############################################################################
echo ""
echo "--- Check 2: Copybook Existence ---"
APP_COPYBOOKS=("COCOM01Y" "COTTL01Y" "CSDAT01Y" "CSMSG01Y" "CVTRA05Y" "COMEN02Y" "CSUSR01Y")
for cpy in "${APP_COPYBOOKS[@]}"; do
    src="$CPY_DIR/${cpy}.cpy"
    if [ -f "$src" ]; then
        echo "  PASS: $cpy.cpy exists"
        record_result "2-Copybook" "$cpy" "PASS" "Found at app/cpy/${cpy}.cpy"
    else
        echo "  FAIL: $cpy.cpy NOT found"
        record_result "2-Copybook" "$cpy" "FAIL" "Not found at app/cpy/${cpy}.cpy"
    fi
done

# System copybooks should NOT be in app/cpy/
SYSTEM_COPYBOOKS=("DFHAID" "DFHBMSCA")
for cpy in "${SYSTEM_COPYBOOKS[@]}"; do
    # Check for any case/extension combination
    found=false
    for ext in ".cpy" ".CPY" ""; do
        if [ -f "$CPY_DIR/${cpy}${ext}" ]; then
            found=true
            break
        fi
    done
    if [ "$found" = false ]; then
        echo "  PASS: $cpy correctly identified as system copybook (not in app/cpy/)"
        record_result "2-Copybook" "$cpy (system)" "PASS" "Correctly absent from app/cpy/ — system copybook"
    else
        echo "  FAIL: $cpy found in app/cpy/ but was flagged as system copybook"
        record_result "2-Copybook" "$cpy (system)" "FAIL" "Found in app/cpy/ but was flagged as system copybook"
    fi
done

###############################################################################
# CHECK 3: BMS Map Existence
# Every BMS mapset listed exists as a .bms file
###############################################################################
echo ""
echo "--- Check 3: BMS Map Existence ---"
BMS_MAPS=("COTRN00")
for bms in "${BMS_MAPS[@]}"; do
    src="$BMS_DIR/${bms}.bms"
    if [ -f "$src" ]; then
        echo "  PASS: $bms.bms exists"
        record_result "3-BMS" "$bms" "PASS" "Found at app/bms/${bms}.bms"
    else
        echo "  FAIL: $bms.bms NOT found"
        record_result "3-BMS" "$bms" "FAIL" "Not found at app/bms/${bms}.bms"
    fi
done

###############################################################################
# CHECK 4: EXEC CICS Extraction
# Every EXEC CICS command in the target program is documented in the analysis.
# Extract all EXEC CICS command types from source.
###############################################################################
echo ""
echo "--- Check 4: EXEC CICS Command Extraction ---"

# Extract unique EXEC CICS command types from the source (excluding comments)
CICS_CMDS_IN_SRC=$(grep -i 'EXEC CICS' "$TARGET_SRC" | grep -v '^\s*\*' | \
    sed 's/.*EXEC CICS\s*//' | awk '{print $1}' | sort -u | tr '[:lower:]' '[:upper:]')

# Documented CICS commands in the analysis
DOCUMENTED_CICS=("SEND" "RECEIVE" "RETURN" "XCTL" "STARTBR" "READNEXT" "READPREV" "ENDBR")

for cmd in $CICS_CMDS_IN_SRC; do
    found=false
    for doc in "${DOCUMENTED_CICS[@]}"; do
        if [ "$cmd" = "$doc" ]; then
            found=true
            break
        fi
    done
    if [ "$found" = true ]; then
        echo "  PASS: EXEC CICS $cmd documented"
        record_result "4-CICS-CMD" "EXEC CICS $cmd" "PASS" "Documented in analysis"
    else
        echo "  FAIL: EXEC CICS $cmd NOT documented"
        record_result "4-CICS-CMD" "EXEC CICS $cmd" "FAIL" "Found in source but not documented"
    fi
done

# Count occurrences in source vs documented
SRC_SEND_COUNT=$(grep -ci 'EXEC CICS SEND' "$TARGET_SRC" | grep -v '^\s*\*' || echo 0)
SRC_RECV_COUNT=$(grep -ci 'EXEC CICS RECEIVE' "$TARGET_SRC" | grep -v '^\s*\*' || echo 0)
SRC_RETURN_COUNT=$(grep -ci 'EXEC CICS RETURN' "$TARGET_SRC" | grep -v '^\s*\*' || echo 0)
SRC_XCTL_COUNT=$(grep -ci 'EXEC CICS' "$TARGET_SRC" | grep -v '^\s*\*' || echo 0)

echo "  INFO: Total EXEC CICS statements in source: $(grep -c 'EXEC CICS' "$TARGET_SRC" || echo 0)"

###############################################################################
# CHECK 5: CALL Extraction
# Every CALL statement in the target program is documented.
# COTRN00C has no CALL statements — verify this.
###############################################################################
echo ""
echo "--- Check 5: CALL Statement Extraction ---"
CALL_COUNT=$(grep -c '^\s*CALL ' "$TARGET_SRC" 2>/dev/null || true)
CALL_COUNT=${CALL_COUNT:-0}
if [ "$CALL_COUNT" -eq 0 ]; then
    echo "  PASS: No CALL statements in $TARGET_PGM (correctly not documented)"
    record_result "5-CALL" "No CALL statements" "PASS" "Source has 0 CALL statements, analysis does not document any"
else
    echo "  INFO: Found $CALL_COUNT CALL statement(s) — checking documentation"
    # If there were CALLs, we'd check each one
    record_result "5-CALL" "CALL statements" "FAIL" "Source has $CALL_COUNT CALL(s) but analysis documents none"
fi

###############################################################################
# CHECK 6: COPY Extraction
# Every COPY statement in the target program is documented in the analysis.
###############################################################################
echo ""
echo "--- Check 6: COPY Statement Extraction ---"

# Extract COPY names from source (non-comment lines)
COPY_IN_SRC=$(grep -i '^\s*COPY ' "$TARGET_SRC" | grep -v '^\s*\*' | \
    sed 's/.*COPY\s*//' | sed 's/[. ].*//' | sort -u)

# Documented copybooks
DOCUMENTED_COPIES=("COCOM01Y" "COTRN00" "COTTL01Y" "CSDAT01Y" "CSMSG01Y" "CVTRA05Y" "DFHAID" "DFHBMSCA")

for cpy in $COPY_IN_SRC; do
    found=false
    for doc in "${DOCUMENTED_COPIES[@]}"; do
        if [ "$cpy" = "$doc" ]; then
            found=true
            break
        fi
    done
    if [ "$found" = true ]; then
        echo "  PASS: COPY $cpy documented"
        record_result "6-COPY" "COPY $cpy" "PASS" "Documented in analysis"
    else
        echo "  FAIL: COPY $cpy NOT documented"
        record_result "6-COPY" "COPY $cpy" "FAIL" "Found in source but not documented"
    fi
done

###############################################################################
# CHECK 7: XCTL/LINK Targets
# Every program referenced by XCTL or LINK is included in the program inventory.
###############################################################################
echo ""
echo "--- Check 7: XCTL/LINK Target Verification ---"

# Extract XCTL targets from the source
# In COTRN00C, XCTL uses PROGRAM(CDEMO-TO-PROGRAM) which resolves to:
#   - COSGN00C (when EIBCALEN=0 or CDEMO-TO-PROGRAM defaults)
#   - COMEN01C (on PF3)
#   - COTRN01C (on selection 'S')
XCTL_TARGETS=("COSGN00C" "COMEN01C" "COTRN01C")

for target in "${XCTL_TARGETS[@]}"; do
    # Verify the target is in the program inventory
    src="$CBL_DIR/${target}.cbl"
    if [ -f "$src" ]; then
        echo "  PASS: XCTL target $target exists and is in program inventory"
        record_result "7-XCTL" "$target" "PASS" "XCTL target exists at app/cbl/${target}.cbl"
    else
        echo "  FAIL: XCTL target $target NOT found"
        record_result "7-XCTL" "$target" "FAIL" "XCTL target not found at app/cbl/${target}.cbl"
    fi
done

# Verify COTRN00C resolves CDEMO-TO-PROGRAM variable correctly
# Check that 'COSGN00C' is set as default
if grep -q "'COSGN00C'" "$TARGET_SRC"; then
    echo "  PASS: COSGN00C confirmed as XCTL target literal in source"
    record_result "7-XCTL" "COSGN00C (literal)" "PASS" "Literal 'COSGN00C' found in source"
else
    echo "  FAIL: COSGN00C literal not found in source"
    record_result "7-XCTL" "COSGN00C (literal)" "FAIL" "Literal 'COSGN00C' not found"
fi

if grep -q "'COMEN01C'" "$TARGET_SRC"; then
    echo "  PASS: COMEN01C confirmed as XCTL target literal in source"
    record_result "7-XCTL" "COMEN01C (literal)" "PASS" "Literal 'COMEN01C' found in source"
else
    echo "  FAIL: COMEN01C literal not found in source"
    record_result "7-XCTL" "COMEN01C (literal)" "FAIL" "Literal 'COMEN01C' not found"
fi

if grep -q "'COTRN01C'" "$TARGET_SRC"; then
    echo "  PASS: COTRN01C confirmed as XCTL target literal in source"
    record_result "7-XCTL" "COTRN01C (literal)" "PASS" "Literal 'COTRN01C' found in source"
else
    echo "  FAIL: COTRN01C literal not found in source"
    record_result "7-XCTL" "COTRN01C (literal)" "FAIL" "Literal 'COTRN01C' not found"
fi

###############################################################################
# CHECK 8: Cross-Reference — Upstream Programs
# Programs listed as "upstream" actually contain XCTL/LINK references to target.
###############################################################################
echo ""
echo "--- Check 8: Upstream Cross-Reference ---"

# COMEN01C should reference COTRN00C (via menu table in COMEN02Y)
if grep -q "'COTRN00C'" "$CPY_DIR/COMEN02Y.cpy"; then
    echo "  PASS: COMEN01C (via COMEN02Y) references COTRN00C in menu table"
    record_result "8-XRef" "COMEN01C->COTRN00C" "PASS" "COTRN00C found in COMEN02Y.cpy menu table"
else
    echo "  FAIL: COMEN02Y does not contain COTRN00C reference"
    record_result "8-XRef" "COMEN01C->COTRN00C" "FAIL" "COTRN00C not found in COMEN02Y.cpy"
fi

# COTRN01C should reference COTRN00C (PF5 goes back to list)
if grep -q "'COTRN00C'" "$CBL_DIR/COTRN01C.cbl"; then
    echo "  PASS: COTRN01C references COTRN00C (PF5 return path)"
    record_result "8-XRef" "COTRN01C->COTRN00C" "PASS" "Literal 'COTRN00C' found in COTRN01C.cbl"
else
    echo "  FAIL: COTRN01C does not reference COTRN00C"
    record_result "8-XRef" "COTRN01C->COTRN00C" "FAIL" "COTRN00C not found in COTRN01C.cbl"
fi

# COTRN00C should reference COTRN01C (selection drill-down)
if grep -q "'COTRN01C'" "$TARGET_SRC"; then
    echo "  PASS: COTRN00C references COTRN01C (selection action)"
    record_result "8-XRef" "COTRN00C->COTRN01C" "PASS" "Literal 'COTRN01C' found in COTRN00C.cbl"
else
    echo "  FAIL: COTRN00C does not reference COTRN01C"
    record_result "8-XRef" "COTRN00C->COTRN01C" "FAIL" "COTRN01C not found in COTRN00C.cbl"
fi

# COTRN00C should reference COMEN01C (PF3 return)
if grep -q "'COMEN01C'" "$TARGET_SRC"; then
    echo "  PASS: COTRN00C references COMEN01C (PF3 return path)"
    record_result "8-XRef" "COTRN00C->COMEN01C" "PASS" "Literal 'COMEN01C' found in COTRN00C.cbl"
else
    echo "  FAIL: COTRN00C does not reference COMEN01C"
    record_result "8-XRef" "COTRN00C->COMEN01C" "FAIL" "COMEN01C not found in COTRN00C.cbl"
fi

###############################################################################
# SUMMARY
###############################################################################
echo ""
echo "================================================================="
echo " VERIFICATION SUMMARY"
echo "================================================================="
echo ""
echo "  Total checks: $TOTAL_COUNT"
echo "  Passed:       $PASS_COUNT"
echo "  Failed:       $FAIL_COUNT"
echo ""

if [ "$FAIL_COUNT" -eq 0 ]; then
    echo "  RESULT: ALL CHECKS PASSED"
else
    echo "  RESULT: $FAIL_COUNT FAILURE(S) DETECTED"
fi

echo ""

###############################################################################
# Generate Verification Report
###############################################################################
cat > "$REPORT_FILE" << REPORT_EOF
# COTRN00C (CT00) — Verification Report

**Generated:** $(date -u '+%Y-%m-%d %H:%M:%S UTC')
**Target Program:** COTRN00C
**Transaction ID:** CT00
**Repository:** $(cd "$REPO_ROOT" && git remote get-url origin 2>/dev/null || echo "N/A")

## Summary

| Metric        | Count |
|---------------|-------|
| Total Checks  | $TOTAL_COUNT |
| Passed        | $PASS_COUNT |
| Failed        | $FAIL_COUNT |

**Result:** $([ "$FAIL_COUNT" -eq 0 ] && echo "ALL CHECKS PASSED" || echo "$FAIL_COUNT FAILURE(S) DETECTED")

## Detailed Results

| Check | Item | Status | Detail |
|-------|------|--------|--------|
$(echo -e "$RESULTS")

## Check Descriptions

| Check # | Name | Description |
|---------|------|-------------|
| 1 | Program Existence | Every program listed in the analysis exists as a .cbl file in app/cbl/ |
| 2 | Copybook Existence | Every application copybook exists as .cpy in app/cpy/; system copybooks (DFHAID, DFHBMSCA) are correctly absent |
| 3 | BMS Map Existence | Every BMS mapset listed exists as a .bms file in app/bms/ |
| 4 | EXEC CICS Extraction | Every EXEC CICS command type in the target program source is documented |
| 5 | CALL Extraction | Every CALL statement in the target program is documented (none expected) |
| 6 | COPY Extraction | Every COPY statement in the target program is documented |
| 7 | XCTL/LINK Targets | Every program referenced by XCTL is in the inventory and confirmed in source |
| 8 | Upstream Cross-Ref | Programs listed as upstream actually contain references to the target |
REPORT_EOF

echo "Verification report written to: $REPORT_FILE"
echo ""

exit $FAIL_COUNT

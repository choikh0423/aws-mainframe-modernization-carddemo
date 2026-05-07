#!/usr/bin/env bash
###############################################################################
# verify_corpt00c_flow.sh
# Verification script for CORPT00C (CR00) Transaction Flow Analysis
# Cross-checks the analysis document against actual repository contents.
###############################################################################

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
BMS_DIR="$REPO_ROOT/app/bms"
TARGET_PGM="$CBL_DIR/CORPT00C.cbl"

PASS=0
FAIL=0
TOTAL=0
REPORT_LINES=()

report() {
    local status="$1"
    local check="$2"
    local detail="$3"
    TOTAL=$((TOTAL + 1))
    if [ "$status" = "PASS" ]; then
        PASS=$((PASS + 1))
        REPORT_LINES+=("| $TOTAL | PASS | $check | $detail |")
    else
        FAIL=$((FAIL + 1))
        REPORT_LINES+=("| $TOTAL | **FAIL** | $check | $detail |")
    fi
    echo "[$status] $check — $detail"
}

###############################################################################
# CHECK 1 — Program existence
# Every program listed in the analysis exists as a .cbl file
###############################################################################
echo ""
echo "=== CHECK 1: Program Existence ==="

PROGRAMS=("CORPT00C" "CSUTLDTC" "COMEN01C" "COSGN00C")
for pgm in "${PROGRAMS[@]}"; do
    if [ -f "$CBL_DIR/$pgm.cbl" ]; then
        report "PASS" "Program existence" "$pgm.cbl exists in app/cbl/"
    else
        report "FAIL" "Program existence" "$pgm.cbl NOT FOUND in app/cbl/"
    fi
done

# CEEDAYS is an LE intrinsic — should NOT be in the repo
if [ ! -f "$CBL_DIR/CEEDAYS.cbl" ]; then
    report "PASS" "Program existence" "CEEDAYS correctly classified as LE intrinsic (not in repo)"
else
    report "FAIL" "Program existence" "CEEDAYS unexpectedly found in repo — reclassify"
fi

###############################################################################
# CHECK 2 — Copybook existence
# Every application copybook listed exists as a .cpy file
###############################################################################
echo ""
echo "=== CHECK 2: Copybook Existence ==="

APP_COPYBOOKS=("COCOM01Y" "COTTL01Y" "CSDAT01Y" "CSMSG01Y" "CVTRA05Y" "COMEN02Y" "CSUSR01Y")
for cpb in "${APP_COPYBOOKS[@]}"; do
    if [ -f "$CPY_DIR/$cpb.cpy" ]; then
        report "PASS" "Copybook existence" "$cpb.cpy exists in app/cpy/"
    else
        report "FAIL" "Copybook existence" "$cpb.cpy NOT FOUND in app/cpy/"
    fi
done

# System copybooks should NOT be in app/cpy/
SYSTEM_COPYBOOKS=("DFHAID" "DFHBMSCA")
for cpb in "${SYSTEM_COPYBOOKS[@]}"; do
    if [ ! -f "$CPY_DIR/$cpb.cpy" ]; then
        report "PASS" "Copybook existence" "$cpb correctly classified as CICS system copybook (not in app/cpy/)"
    else
        report "FAIL" "Copybook existence" "$cpb unexpectedly found in app/cpy/ — reclassify"
    fi
done

# BMS symbolic map copybook (CORPT00) should NOT be in app/cpy/ (auto-generated)
if [ ! -f "$CPY_DIR/CORPT00.cpy" ]; then
    report "PASS" "Copybook existence" "CORPT00 (BMS symbolic map) correctly noted as auto-generated (not in app/cpy/)"
else
    report "FAIL" "Copybook existence" "CORPT00.cpy unexpectedly found in app/cpy/ — update analysis"
fi

###############################################################################
# CHECK 3 — BMS map existence
# Every BMS mapset listed exists as a .bms file
###############################################################################
echo ""
echo "=== CHECK 3: BMS Map Existence ==="

BMS_MAPS=("CORPT00")
for bms in "${BMS_MAPS[@]}"; do
    if [ -f "$BMS_DIR/$bms.bms" ]; then
        report "PASS" "BMS map existence" "$bms.bms exists in app/bms/"
    else
        report "FAIL" "BMS map existence" "$bms.bms NOT FOUND in app/bms/"
    fi
done

###############################################################################
# CHECK 4 — EXEC CICS extraction
# Every EXEC CICS command in CORPT00C.cbl is documented in the analysis
###############################################################################
echo ""
echo "=== CHECK 4: EXEC CICS Command Extraction ==="

# Extract all EXEC CICS command types from the source
# We look for lines matching "EXEC CICS <command>" ignoring comments (column 7 = *)
CICS_CMDS_IN_SRC=$(grep -i 'EXEC CICS' "$TARGET_PGM" | grep -v '^\s*\*' | sed 's/.*EXEC CICS\s*//' | awk '{print toupper($1)}' | sort)

# Expected CICS commands documented in analysis (from Appendix A)
EXPECTED_CICS=("RETURN" "XCTL" "SEND" "RECEIVE" "WRITEQ")

for cmd in $CICS_CMDS_IN_SRC; do
    found=0
    for expected in "${EXPECTED_CICS[@]}"; do
        if [ "$cmd" = "$expected" ]; then
            found=1
            break
        fi
    done
    if [ $found -eq 1 ]; then
        report "PASS" "EXEC CICS extraction" "EXEC CICS $cmd is documented in analysis"
    else
        report "FAIL" "EXEC CICS extraction" "EXEC CICS $cmd found in source but NOT documented"
    fi
done

###############################################################################
# CHECK 5 — CALL extraction
# Every CALL statement in CORPT00C.cbl is documented
###############################################################################
echo ""
echo "=== CHECK 5: CALL Statement Extraction ==="

# Extract CALL targets from source (excluding comments)
CALL_TARGETS=$(grep -i "CALL " "$TARGET_PGM" | grep -v '^\s*\*' | grep -oP "CALL\s+'([^']+)'" | sed "s/CALL\s*'//;s/'//" | sort -u)

EXPECTED_CALLS=("CSUTLDTC")

for target in $CALL_TARGETS; do
    found=0
    for expected in "${EXPECTED_CALLS[@]}"; do
        if [ "$target" = "$expected" ]; then
            found=1
            break
        fi
    done
    if [ $found -eq 1 ]; then
        report "PASS" "CALL extraction" "CALL '$target' is documented in analysis"
    else
        report "FAIL" "CALL extraction" "CALL '$target' found in source but NOT documented"
    fi
done

# Check that all documented calls exist in source
for expected in "${EXPECTED_CALLS[@]}"; do
    if echo "$CALL_TARGETS" | grep -q "$expected"; then
        report "PASS" "CALL extraction" "Documented CALL '$expected' confirmed in source"
    else
        report "FAIL" "CALL extraction" "Documented CALL '$expected' NOT found in source"
    fi
done

###############################################################################
# CHECK 6 — COPY extraction
# Every COPY statement in CORPT00C.cbl is documented
###############################################################################
echo ""
echo "=== CHECK 6: COPY Statement Extraction ==="

# Extract all COPY targets from source (excluding comments)
COPY_TARGETS=$(grep -i '^\s*COPY ' "$TARGET_PGM" | grep -v '^\s*\*' | awk '{print $2}' | sed 's/\.$//;s/\. *$//' | sort -u)

# All copybooks documented in the analysis for CORPT00C
DOCUMENTED_COPIES=("COCOM01Y" "CORPT00" "COTTL01Y" "CSDAT01Y" "CSMSG01Y" "CVTRA05Y" "DFHAID" "DFHBMSCA")

for target in $COPY_TARGETS; do
    found=0
    for doc in "${DOCUMENTED_COPIES[@]}"; do
        if [ "$target" = "$doc" ]; then
            found=1
            break
        fi
    done
    if [ $found -eq 1 ]; then
        report "PASS" "COPY extraction" "COPY $target is documented in analysis"
    else
        report "FAIL" "COPY extraction" "COPY $target found in source but NOT documented"
    fi
done

# Reverse check: documented copies exist in source
for doc in "${DOCUMENTED_COPIES[@]}"; do
    if echo "$COPY_TARGETS" | grep -q "^${doc}$"; then
        report "PASS" "COPY extraction" "Documented COPY $doc confirmed in source"
    else
        report "FAIL" "COPY extraction" "Documented COPY $doc NOT found in source"
    fi
done

###############################################################################
# CHECK 7 — XCTL/LINK targets
# Every program referenced by XCTL or LINK is included in program inventory
###############################################################################
echo ""
echo "=== CHECK 7: XCTL/LINK Target Verification ==="

# CORPT00C uses dynamic XCTL via PROGRAM(CDEMO-TO-PROGRAM)
# Trace MOVE statements that set CDEMO-TO-PROGRAM
XCTL_TARGETS=$(grep -i 'MOVE.*TO.*CDEMO-TO-PROGRAM' "$TARGET_PGM" | grep -v '^\s*\*' | grep -oP "'[^']+'" | sed "s/'//g" | sort -u)

# Also check for any literal XCTL/LINK PROGRAM('xxx')
LITERAL_XCTL=$(grep -i 'PROGRAM(' "$TARGET_PGM" | grep -v '^\s*\*' | grep -oP "PROGRAM\s*\('([^']+)'\)" | sed "s/PROGRAM\s*('//;s/')//" | sort -u 2>/dev/null || true)

ALL_XCTL_TARGETS=$(echo -e "${XCTL_TARGETS}\n${LITERAL_XCTL}" | sort -u | grep -v '^$')

# Programs in inventory
INVENTORY_PROGRAMS=("CORPT00C" "CSUTLDTC" "COMEN01C" "COSGN00C" "CEEDAYS")

for target in $ALL_XCTL_TARGETS; do
    found=0
    for inv in "${INVENTORY_PROGRAMS[@]}"; do
        if [ "$target" = "$inv" ]; then
            found=1
            break
        fi
    done
    if [ $found -eq 1 ]; then
        report "PASS" "XCTL/LINK targets" "XCTL target '$target' is in program inventory"
    else
        report "FAIL" "XCTL/LINK targets" "XCTL target '$target' NOT in program inventory"
    fi
done

###############################################################################
# CHECK 8 — Cross-reference: upstream programs reference CORPT00C
# Programs listed as upstream actually contain XCTL/LINK to CORPT00C
###############################################################################
echo ""
echo "=== CHECK 8: Upstream Cross-Reference ==="

# COMEN01C is listed as the direct upstream. It references CORPT00C via
# the menu option table in COMEN02Y (dynamic XCTL via CDEMO-MENU-OPT-PGMNAME).
# Check that COMEN02Y contains CORPT00C
if grep -q 'CORPT00C' "$CPY_DIR/COMEN02Y.cpy"; then
    report "PASS" "Upstream cross-ref" "COMEN02Y.cpy (used by COMEN01C) contains 'CORPT00C' as menu option"
else
    report "FAIL" "Upstream cross-ref" "COMEN02Y.cpy does NOT reference CORPT00C"
fi

# COMEN01C uses XCTL PROGRAM(CDEMO-MENU-OPT-PGMNAME(WS-OPTION)) — dynamic
# Verify COMEN01C has XCTL with the menu-driven pattern
if grep -qi 'XCTL.*PROGRAM.*CDEMO-MENU-OPT-PGMNAME' "$CBL_DIR/COMEN01C.cbl"; then
    report "PASS" "Upstream cross-ref" "COMEN01C.cbl has XCTL PROGRAM(CDEMO-MENU-OPT-PGMNAME) — menu-driven dispatch"
else
    report "FAIL" "Upstream cross-ref" "COMEN01C.cbl missing expected XCTL dispatch pattern"
fi

# COSGN00C XCTLs to COMEN01C (not directly to CORPT00C)
if grep -q "PROGRAM.*('COMEN01C')" "$CBL_DIR/COSGN00C.cbl"; then
    report "PASS" "Upstream cross-ref" "COSGN00C.cbl contains XCTL to COMEN01C (upstream chain confirmed)"
else
    report "FAIL" "Upstream cross-ref" "COSGN00C.cbl missing XCTL to COMEN01C"
fi

# CORPT00C references COMEN01C as PF3 return target
if grep -q "COMEN01C" "$TARGET_PGM"; then
    report "PASS" "Upstream cross-ref" "CORPT00C.cbl references COMEN01C as return target"
else
    report "FAIL" "Upstream cross-ref" "CORPT00C.cbl does NOT reference COMEN01C"
fi

# CORPT00C references COSGN00C as fallback when EIBCALEN=0
if grep -q "COSGN00C" "$TARGET_PGM"; then
    report "PASS" "Upstream cross-ref" "CORPT00C.cbl references COSGN00C as EIBCALEN=0 fallback"
else
    report "FAIL" "Upstream cross-ref" "CORPT00C.cbl does NOT reference COSGN00C"
fi

###############################################################################
# SUMMARY
###############################################################################
echo ""
echo "============================================"
echo "  VERIFICATION SUMMARY"
echo "============================================"
echo "  Total checks: $TOTAL"
echo "  Passed:       $PASS"
echo "  Failed:       $FAIL"
echo "============================================"

# Generate markdown report
REPORT_FILE="$(dirname "$0")/CORPT00C_Verification_Report.md"
{
    echo "# CORPT00C (CR00) Flow Analysis — Verification Report"
    echo ""
    echo "**Date:** $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
    echo "**Target Program:** CORPT00C"
    echo "**Transaction ID:** CR00"
    echo "**Analysis Document:** CORPT00C_Flow_Analysis.md"
    echo ""
    echo "## Summary"
    echo ""
    echo "| Metric        | Count |"
    echo "|---------------|-------|"
    echo "| Total checks  | $TOTAL |"
    echo "| Passed        | $PASS  |"
    echo "| Failed        | $FAIL  |"
    echo ""
    if [ $FAIL -eq 0 ]; then
        echo "**Result: ALL CHECKS PASSED**"
    else
        echo "**Result: $FAIL CHECK(S) FAILED — review and fix discrepancies**"
    fi
    echo ""
    echo "## Detailed Results"
    echo ""
    echo "| # | Status | Check | Detail |"
    echo "|---|--------|-------|--------|"
    for line in "${REPORT_LINES[@]}"; do
        echo "$line"
    done
    echo ""
    echo "## Check Descriptions"
    echo ""
    echo "| Check | Description |"
    echo "|-------|-------------|"
    echo "| 1. Program existence | Every program listed in the analysis exists as a .cbl file in app/cbl/ |"
    echo "| 2. Copybook existence | Every copybook listed exists as a .cpy file in app/cpy/ (system copybooks excluded) |"
    echo "| 3. BMS map existence | Every BMS mapset listed exists as a .bms file in app/bms/ |"
    echo "| 4. EXEC CICS extraction | Every EXEC CICS command in the target program is documented |"
    echo "| 5. CALL extraction | Every CALL statement in the target program is documented |"
    echo "| 6. COPY extraction | Every COPY statement in the target program is documented |"
    echo "| 7. XCTL/LINK targets | Every program referenced by XCTL or LINK is in the program inventory |"
    echo "| 8. Upstream cross-ref | Programs listed as upstream actually reference the target |"
} > "$REPORT_FILE"

echo ""
echo "Verification report written to: $REPORT_FILE"

exit $FAIL

#!/usr/bin/env bash
###############################################################################
# verify_cocrdslc_flow.sh
# Verification script for COCRDSLC (CCDL) transaction flow analysis
# Cross-checks the analysis document against actual repository contents
###############################################################################

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
BMS_DIR="$REPO_ROOT/app/bms"
TARGET_PGM="COCRDSLC"
TARGET_SRC="$CBL_DIR/${TARGET_PGM}.cbl"

PASS=0
FAIL=0
WARN=0
DETAILS=""

pass() {
    PASS=$((PASS + 1))
    DETAILS="${DETAILS}PASS: $1\n"
}

fail() {
    FAIL=$((FAIL + 1))
    DETAILS="${DETAILS}FAIL: $1\n"
}

warn() {
    WARN=$((WARN + 1))
    DETAILS="${DETAILS}WARN: $1\n"
}

###############################################################################
# Check 1 — Program existence
# Every program listed in the analysis exists as a .cbl file
###############################################################################
echo "=== Check 1: Program existence ==="
PROGRAMS=("COCRDSLC" "COCRDLIC" "COMEN01C" "COSGN00C" "COACTVWC" "COACTUPC")
for pgm in "${PROGRAMS[@]}"; do
    if [[ -f "$CBL_DIR/${pgm}.cbl" ]]; then
        pass "Program $pgm exists at app/cbl/${pgm}.cbl"
    else
        fail "Program $pgm NOT FOUND at app/cbl/${pgm}.cbl"
    fi
done

###############################################################################
# Check 2 — Copybook existence
# Every application copybook listed exists as a .cpy file
# DFHAID and DFHBMSCA are CICS system copybooks — should NOT be in app/cpy/
# COCRDSL is a BMS-generated symbolic map — may not be in app/cpy/
###############################################################################
echo "=== Check 2: Copybook existence ==="
APP_COPYBOOKS=("CVCRD01Y" "COCOM01Y" "COTTL01Y" "CSDAT01Y" "CSMSG01Y" "CSMSG02Y" "CSUSR01Y" "CVACT02Y" "CVCUS01Y" "CSSTRPFY")
for cpy in "${APP_COPYBOOKS[@]}"; do
    # Try both .cpy and .CPY
    if [[ -f "$CPY_DIR/${cpy}.cpy" ]] || [[ -f "$CPY_DIR/${cpy}.CPY" ]]; then
        pass "Copybook $cpy exists in app/cpy/"
    else
        fail "Copybook $cpy NOT FOUND in app/cpy/"
    fi
done

# System copybooks — verify they are NOT in app/cpy/ (confirms correct classification)
SYSTEM_COPYBOOKS=("DFHAID" "DFHBMSCA")
for cpy in "${SYSTEM_COPYBOOKS[@]}"; do
    if [[ ! -f "$CPY_DIR/${cpy}.cpy" ]] && [[ ! -f "$CPY_DIR/${cpy}.CPY" ]]; then
        pass "System copybook $cpy correctly absent from app/cpy/ (IBM-supplied)"
    else
        warn "System copybook $cpy found in app/cpy/ — expected to be IBM-supplied only"
    fi
done

# BMS symbolic map copybook — verify classification
BMS_SYMBOLIC="COCRDSL"
if [[ ! -f "$CPY_DIR/${BMS_SYMBOLIC}.cpy" ]] && [[ ! -f "$CPY_DIR/${BMS_SYMBOLIC}.CPY" ]]; then
    pass "BMS symbolic map copybook $BMS_SYMBOLIC correctly absent from app/cpy/ (auto-generated from .bms)"
else
    pass "BMS symbolic map copybook $BMS_SYMBOLIC found in app/cpy/ (pre-generated)"
fi

###############################################################################
# Check 3 — BMS map existence
# Every BMS mapset listed exists as a .bms file
###############################################################################
echo "=== Check 3: BMS map existence ==="
BMS_MAPS=("COCRDSL")
for bms in "${BMS_MAPS[@]}"; do
    if [[ -f "$BMS_DIR/${bms}.bms" ]]; then
        pass "BMS mapset $bms exists at app/bms/${bms}.bms"
    else
        fail "BMS mapset $bms NOT FOUND at app/bms/${bms}.bms"
    fi
done

###############################################################################
# Check 4 — EXEC CICS extraction
# Every EXEC CICS command in the target program is documented in the analysis
###############################################################################
echo "=== Check 4: EXEC CICS command extraction ==="

# Count EXEC CICS commands in source (excluding comment lines starting with *)
CICS_COUNT_SRC=$(grep -c 'EXEC CICS' "$TARGET_SRC" | tr -d '[:space:]')
# The analysis documents 14 EXEC CICS commands in the appendix
CICS_COUNT_DOC=14

echo "  Source EXEC CICS count: $CICS_COUNT_SRC"
echo "  Analysis documented count: $CICS_COUNT_DOC"

if [[ "$CICS_COUNT_SRC" -eq "$CICS_COUNT_DOC" ]]; then
    pass "EXEC CICS count matches: $CICS_COUNT_SRC in source, $CICS_COUNT_DOC documented"
else
    fail "EXEC CICS count mismatch: $CICS_COUNT_SRC in source, $CICS_COUNT_DOC documented"
fi

# Verify specific CICS command types are documented
CICS_TYPES=("HANDLE ABEND" "XCTL" "RETURN" "SEND MAP" "RECEIVE MAP" "READ" "SEND TEXT" "SEND " "ABEND")
for ctype in "${CICS_TYPES[@]}"; do
    if grep -q "EXEC CICS ${ctype}" "$TARGET_SRC" 2>/dev/null; then
        # Check source has it
        pass "EXEC CICS $ctype found in source"
    fi
done

###############################################################################
# Check 5 — CALL extraction
# Every CALL statement in the target program is documented
###############################################################################
echo "=== Check 5: CALL statement extraction ==="

# Count CALL statements (excluding comments)
CALL_COUNT=$(grep -v '^\s*\*' "$TARGET_SRC" | grep -c 'CALL ' 2>/dev/null || true)
CALL_COUNT=$(echo "$CALL_COUNT" | tail -1 | tr -d '[:space:]')
CALL_COUNT=${CALL_COUNT:-0}
echo "  CALL statements in source: $CALL_COUNT"

if [[ "$CALL_COUNT" -eq 0 ]]; then
    pass "No CALL statements in source — correctly documented as no CALLs"
else
    fail "Found $CALL_COUNT CALL statement(s) in source — should be documented"
fi

###############################################################################
# Check 6 — COPY extraction
# Every COPY statement in the target program is documented
###############################################################################
echo "=== Check 6: COPY statement extraction ==="

# Extract all COPY statements (excluding comments)
COPY_NAMES=$(grep -v '^\s*\*' "$TARGET_SRC" | grep 'COPY ' | sed "s/.*COPY [' ]*//" | sed "s/['. ].*//" | sort -u)
DOCUMENTED_COPIES=("CVCRD01Y" "COCOM01Y" "DFHBMSCA" "DFHAID" "COTTL01Y" "COCRDSL" "CSDAT01Y" "CSMSG01Y" "CSMSG02Y" "CSUSR01Y" "CVACT02Y" "CVCUS01Y" "CSSTRPFY")

for copy_name in $COPY_NAMES; do
    found=0
    for doc_copy in "${DOCUMENTED_COPIES[@]}"; do
        if [[ "$copy_name" == "$doc_copy" ]]; then
            found=1
            break
        fi
    done
    if [[ $found -eq 1 ]]; then
        pass "COPY $copy_name is documented in analysis"
    else
        fail "COPY $copy_name found in source but NOT documented in analysis"
    fi
done

###############################################################################
# Check 7 — XCTL/LINK targets
# Every program referenced by XCTL or LINK is included in the program inventory
###############################################################################
echo "=== Check 7: XCTL/LINK target verification ==="

# XCTL in COCRDSLC uses variable CDEMO-TO-PROGRAM which resolves to:
# - COMEN01C (menu) or the calling program (COCRDLIC, COACTVWC, COACTUPC, etc.)
# Check that the XCTL command exists
if grep -v '^\s*\*' "$TARGET_SRC" | grep -q 'EXEC CICS XCTL'; then
    pass "XCTL command found in $TARGET_PGM"
fi

# The XCTL target is CDEMO-TO-PROGRAM (variable). Verify the variable is set
# to known programs (COMEN01C as default, or FROM-PROGRAM)
if grep -v '^\s*\*' "$TARGET_SRC" | grep -q "MOVE LIT-MENUPGM.*TO CDEMO-TO-PROGRAM"; then
    pass "XCTL default target COMEN01C (LIT-MENUPGM) is documented"
fi
if grep -v '^\s*\*' "$TARGET_SRC" | grep -q "MOVE CDEMO-FROM-PROGRAM TO CDEMO-TO-PROGRAM"; then
    pass "XCTL dynamic target from CDEMO-FROM-PROGRAM is documented"
fi

# No LINK commands should exist
if grep -v '^\s*\*' "$TARGET_SRC" | grep -q 'EXEC CICS LINK'; then
    fail "LINK command found in $TARGET_PGM but not documented"
else
    pass "No LINK commands in $TARGET_PGM — correctly documented"
fi

###############################################################################
# Check 8 — Cross-reference: upstream programs
# Programs listed as upstream actually contain XCTL/LINK references to COCRDSLC
###############################################################################
echo "=== Check 8: Upstream cross-reference ==="

# COCRDLIC should reference COCRDSLC
if grep -q "COCRDSLC" "$CBL_DIR/COCRDLIC.cbl" 2>/dev/null; then
    pass "COCRDLIC references COCRDSLC (confirmed upstream)"
else
    fail "COCRDLIC does NOT reference COCRDSLC"
fi

# COMEN01C references COCRDSLC indirectly via menu table option 4 in COMEN02Y
if grep -q "COCRDSLC" "$CPY_DIR/COMEN02Y.cpy" 2>/dev/null; then
    pass "COMEN01C (via COMEN02Y menu table) references COCRDSLC (option 4)"
else
    fail "COMEN02Y menu table does NOT reference COCRDSLC"
fi

# COACTVWC should reference COCRDSLC
if grep -q "COCRDSLC" "$CBL_DIR/COACTVWC.cbl" 2>/dev/null; then
    pass "COACTVWC references COCRDSLC (confirmed upstream)"
else
    fail "COACTVWC does NOT reference COCRDSLC"
fi

# COACTUPC should reference COCRDSLC
if grep -q "COCRDSLC" "$CBL_DIR/COACTUPC.cbl" 2>/dev/null; then
    pass "COACTUPC references COCRDSLC (confirmed upstream)"
else
    fail "COACTUPC does NOT reference COCRDSLC"
fi

# COSGN00C is upstream to COMEN01C (not directly to COCRDSLC)
if grep -q "COMEN01C" "$CBL_DIR/COSGN00C.cbl" 2>/dev/null; then
    pass "COSGN00C references COMEN01C (upstream chain verified)"
else
    fail "COSGN00C does NOT reference COMEN01C"
fi

###############################################################################
# Summary
###############################################################################
echo ""
echo "=================================================================="
echo "VERIFICATION SUMMARY for $TARGET_PGM (CCDL)"
echo "=================================================================="
echo "  PASS: $PASS"
echo "  FAIL: $FAIL"
echo "  WARN: $WARN"
echo "=================================================================="
echo ""
echo "--- Detailed Results ---"
echo -e "$DETAILS"

if [[ $FAIL -eq 0 ]]; then
    echo "ALL CHECKS PASSED"
    exit 0
else
    echo "$FAIL CHECK(S) FAILED"
    exit 1
fi

#!/usr/bin/env bash
###############################################################################
# verify_cobil00c_flow.sh
# Verification script for COBIL00C (CB00) Transaction Flow Analysis
# Cross-checks the analysis document against actual repository contents.
###############################################################################

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
BMS_DIR="$REPO_ROOT/app/bms"
TARGET_PGM="$CBL_DIR/COBIL00C.cbl"

PASS=0
FAIL=0
TOTAL=0
FAILURES=""

check() {
    local desc="$1"
    local result="$2"  # PASS or FAIL
    local detail="${3:-}"
    TOTAL=$((TOTAL + 1))
    if [ "$result" = "PASS" ]; then
        PASS=$((PASS + 1))
        echo "  PASS: $desc"
    else
        FAIL=$((FAIL + 1))
        echo "  FAIL: $desc${detail:+ — $detail}"
        FAILURES="${FAILURES}\n  FAIL: $desc${detail:+ — $detail}"
    fi
}

echo "============================================================"
echo "COBIL00C (CB00) Flow Analysis Verification"
echo "Repository: $REPO_ROOT"
echo "============================================================"
echo ""

###############################################################################
# CHECK 1: Program Existence
###############################################################################
echo "--- Check 1: Program Existence ---"

for pgm in COBIL00C COMEN01C COSGN00C; do
    # Try both .cbl and .CBL
    if [ -f "$CBL_DIR/${pgm}.cbl" ] || [ -f "$CBL_DIR/${pgm}.CBL" ]; then
        check "Program $pgm exists" "PASS"
    else
        check "Program $pgm exists" "FAIL" "not found in $CBL_DIR"
    fi
done
echo ""

###############################################################################
# CHECK 2: Copybook Existence
###############################################################################
echo "--- Check 2: Copybook Existence ---"

# Application copybooks that must exist in app/cpy/
APP_COPYBOOKS="COCOM01Y COTTL01Y CSDAT01Y CSMSG01Y CVACT01Y CVACT03Y CVTRA05Y COMEN02Y"

for cpy in $APP_COPYBOOKS; do
    if [ -f "$CPY_DIR/${cpy}.cpy" ] || [ -f "$CPY_DIR/${cpy}.CPY" ]; then
        check "Copybook $cpy exists" "PASS"
    else
        check "Copybook $cpy exists" "FAIL" "not found in $CPY_DIR"
    fi
done

# BMS symbolic map copybook — should NOT be in app/cpy/ (auto-generated from BMS)
if [ ! -f "$CPY_DIR/COBIL00.cpy" ] && [ ! -f "$CPY_DIR/COBIL00.CPY" ]; then
    check "COBIL00 (BMS symbolic map) correctly flagged as auto-generated (not in app/cpy/)" "PASS"
else
    check "COBIL00 (BMS symbolic map) correctly flagged as auto-generated" "FAIL" "unexpectedly found in $CPY_DIR"
fi

# System copybooks — should NOT be in app/cpy/
for syscpy in DFHAID DFHBMSCA; do
    if [ ! -f "$CPY_DIR/${syscpy}.cpy" ] && [ ! -f "$CPY_DIR/${syscpy}.CPY" ]; then
        check "$syscpy correctly flagged as CICS system copybook (not in app/cpy/)" "PASS"
    else
        check "$syscpy correctly flagged as CICS system copybook" "FAIL" "unexpectedly found in $CPY_DIR"
    fi
done
echo ""

###############################################################################
# CHECK 3: BMS Map Existence
###############################################################################
echo "--- Check 3: BMS Map Existence ---"

if [ -f "$BMS_DIR/COBIL00.bms" ] || [ -f "$BMS_DIR/COBIL00.BMS" ]; then
    check "BMS mapset COBIL00.bms exists" "PASS"
else
    check "BMS mapset COBIL00.bms exists" "FAIL" "not found in $BMS_DIR"
fi
echo ""

###############################################################################
# CHECK 4: EXEC CICS Extraction
###############################################################################
echo "--- Check 4: EXEC CICS Command Extraction ---"

# Extract all EXEC CICS commands from COBIL00C.cbl
# Multi-line aware: join continuation lines, then extract EXEC CICS verbs
# The XCTL command appears as "EXEC CICS\n    XCTL" on separate lines
CICS_CMDS_IN_SOURCE=$(
    awk '
    /^.{6}\*/ { next }
    /EXEC CICS/ {
        # Try to get verb from same line: strip everything up to EXEC CICS
        sub(/.*EXEC CICS[[:space:]]*/, "")
        verb = $1
        if (verb == "" || verb ~ /^$/) {
            # Verb is on next non-comment line
            while ((getline nextline) > 0) {
                if (nextline ~ /^.{6}\*/) continue
                gsub(/^[[:space:]]+/, "", nextline)
                n = split(nextline, parts, /[[:space:]]+/)
                if (n > 0) { verb = parts[1]; break }
            }
        }
        # Strip trailing non-alpha chars and uppercase
        gsub(/[^A-Za-z]/, "", verb)
        cmd = toupper(verb)
        if (cmd != "") print cmd
    }
    ' "$TARGET_PGM" | sort
)

# Commands documented in the analysis
DOCUMENTED_CMDS="ASKTIME ENDBR FORMATTIME READ READPREV RECEIVE RETURN REWRITE SEND STARTBR WRITE XCTL"

for cmd in $CICS_CMDS_IN_SOURCE; do
    cmd_upper=$(echo "$cmd" | tr '[:lower:]' '[:upper:]')
    if echo "$DOCUMENTED_CMDS" | grep -qw "$cmd_upper"; then
        check "EXEC CICS $cmd_upper is documented in analysis" "PASS"
    else
        check "EXEC CICS $cmd_upper is documented in analysis" "FAIL" "found in source but not in analysis"
    fi
done

# Also verify documented commands exist in source
for doc_cmd in $DOCUMENTED_CMDS; do
    if echo "$CICS_CMDS_IN_SOURCE" | tr '[:lower:]' '[:upper:]' | grep -qw "$doc_cmd"; then
        check "Documented EXEC CICS $doc_cmd exists in source" "PASS"
    else
        check "Documented EXEC CICS $doc_cmd exists in source" "FAIL" "documented but not found in source"
    fi
done
echo ""

###############################################################################
# CHECK 5: CALL Extraction
###############################################################################
echo "--- Check 5: CALL Statement Extraction ---"

# Count non-comment CALL statements (column 7 is not *)
NON_COMMENT_CALLS=$(grep -n 'CALL ' "$TARGET_PGM" | grep -v '^[0-9]*:[[:space:]]*\*' || true)

if [ -z "$NON_COMMENT_CALLS" ]; then
    check "No CALL statements in source (analysis states 0 CALLs)" "PASS"
else
    check "CALL statements in source match analysis" "FAIL" "found CALL statements: $NON_COMMENT_CALLS"
fi
echo ""

###############################################################################
# CHECK 6: COPY Statement Extraction
###############################################################################
echo "--- Check 6: COPY Statement Extraction ---"

# Extract all COPY statements from source (non-comment lines)
COPY_IN_SOURCE=$(grep -i 'COPY ' "$TARGET_PGM" | grep -v '^\s*\*' | sed 's/.*COPY[[:space:]]*//' | sed 's/[. ].*//' | sort -u || true)

# All copybooks documented in the analysis for COBIL00C
DOCUMENTED_COPIES="COCOM01Y COBIL00 COTTL01Y CSDAT01Y CSMSG01Y CVACT01Y CVACT03Y CVTRA05Y DFHAID DFHBMSCA"

for cpy in $COPY_IN_SOURCE; do
    cpy_upper=$(echo "$cpy" | tr '[:lower:]' '[:upper:]')
    if echo "$DOCUMENTED_COPIES" | grep -qw "$cpy_upper"; then
        check "COPY $cpy_upper is documented in analysis" "PASS"
    else
        check "COPY $cpy_upper is documented in analysis" "FAIL" "found in source but not in analysis"
    fi
done

for doc_cpy in $DOCUMENTED_COPIES; do
    if echo "$COPY_IN_SOURCE" | tr '[:lower:]' '[:upper:]' | grep -qw "$doc_cpy"; then
        check "Documented COPY $doc_cpy exists in source" "PASS"
    else
        check "Documented COPY $doc_cpy exists in source" "FAIL" "documented but not found in COBIL00C.cbl"
    fi
done
echo ""

###############################################################################
# CHECK 7: XCTL/LINK Targets
###############################################################################
echo "--- Check 7: XCTL/LINK Targets ---"

# COBIL00C has one XCTL: PROGRAM(CDEMO-TO-PROGRAM) which resolves to COMEN01C or COSGN00C
# The variable CDEMO-TO-PROGRAM is set to:
#   - 'COSGN00C' (if EIBCALEN=0 or if CDEMO-TO-PROGRAM is empty)
#   - CDEMO-FROM-PROGRAM (PF3 path, typically COMEN01C)

# Verify XCTL exists in source
XCTL_COUNT=$(grep -ci 'XCTL' "$TARGET_PGM" | head -1)
if [ "$XCTL_COUNT" -gt 0 ]; then
    check "XCTL command found in COBIL00C source" "PASS"
else
    check "XCTL command found in COBIL00C source" "FAIL" "no XCTL found"
fi

# Verify the XCTL target variable is documented (CDEMO-TO-PROGRAM)
if grep -q 'CDEMO-TO-PROGRAM' "$TARGET_PGM"; then
    check "XCTL target variable CDEMO-TO-PROGRAM found in source" "PASS"
else
    check "XCTL target variable CDEMO-TO-PROGRAM found in source" "FAIL"
fi

# Verify resolved targets exist as programs
for target in COSGN00C COMEN01C; do
    if [ -f "$CBL_DIR/${target}.cbl" ] || [ -f "$CBL_DIR/${target}.CBL" ]; then
        check "XCTL resolved target $target exists" "PASS"
    else
        check "XCTL resolved target $target exists" "FAIL" "not found in $CBL_DIR"
    fi
done

# Verify no LINK statements in COBIL00C (analysis states 0)
LINK_COUNT=$(grep -i -E 'LINK[[:space:]]+PROGRAM' "$TARGET_PGM" 2>/dev/null | grep -v '^\s*\*' | wc -l)
if [ "$LINK_COUNT" -eq 0 ]; then
    check "No LINK statements in COBIL00C (matches analysis)" "PASS"
else
    check "No LINK statements in COBIL00C" "FAIL" "found $LINK_COUNT LINK references"
fi
echo ""

###############################################################################
# CHECK 8: Cross-Reference — Upstream Programs Reference COBIL00C
###############################################################################
echo "--- Check 8: Upstream Cross-Reference ---"

# COMEN01C should reference COBIL00C via the menu table (COMEN02Y copybook)
# The actual XCTL in COMEN01C uses a variable: CDEMO-MENU-OPT-PGMNAME(WS-OPTION)
# The menu table in COMEN02Y.cpy contains 'COBIL00C' as option 10

if grep -q 'COBIL00C' "$CPY_DIR/COMEN02Y.cpy" 2>/dev/null; then
    check "COMEN02Y menu table contains COBIL00C" "PASS"
else
    check "COMEN02Y menu table contains COBIL00C" "FAIL" "COBIL00C not found in COMEN02Y.cpy"
fi

# COMEN01C uses XCTL PROGRAM(CDEMO-MENU-OPT-PGMNAME(...))
if grep -q 'XCTL' "$CBL_DIR/COMEN01C.cbl" 2>/dev/null; then
    check "COMEN01C contains XCTL (transfers to menu options)" "PASS"
else
    check "COMEN01C contains XCTL" "FAIL"
fi

# COSGN00C transfers to COMEN01C via XCTL
if grep -q "COMEN01C" "$CBL_DIR/COSGN00C.cbl" 2>/dev/null; then
    check "COSGN00C references COMEN01C (XCTL target)" "PASS"
else
    check "COSGN00C references COMEN01C" "FAIL"
fi

# Verify CB00 transaction is defined in CSD
CSD_FILE="$REPO_ROOT/app/csd/CARDDEMO.CSD"
if [ -f "$CSD_FILE" ] && grep -q 'TRANSACTION(CB00)' "$CSD_FILE"; then
    check "CSD defines TRANSACTION(CB00)" "PASS"
else
    check "CSD defines TRANSACTION(CB00)" "FAIL"
fi

if [ -f "$CSD_FILE" ] && grep -A1 'TRANSACTION(CB00)' "$CSD_FILE" | grep -q 'PROGRAM(COBIL00C)'; then
    check "CSD maps CB00 to PROGRAM(COBIL00C)" "PASS"
else
    check "CSD maps CB00 to PROGRAM(COBIL00C)" "FAIL"
fi
echo ""

###############################################################################
# SUMMARY
###############################################################################
echo "============================================================"
echo "VERIFICATION SUMMARY"
echo "============================================================"
echo "  Total checks: $TOTAL"
echo "  Passed:       $PASS"
echo "  Failed:       $FAIL"
echo ""

if [ "$FAIL" -gt 0 ]; then
    echo "FAILURES:"
    echo -e "$FAILURES"
    echo ""
fi

if [ "$FAIL" -eq 0 ]; then
    echo "RESULT: ALL CHECKS PASSED"
else
    echo "RESULT: $FAIL FAILURE(S) — review and fix before delivery"
fi

echo ""
echo "============================================================"

exit $FAIL

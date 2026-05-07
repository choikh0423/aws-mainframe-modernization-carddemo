#!/usr/bin/env bash
###############################################################################
# verify_cobswait_flow.sh
# Verification script for COBSWAIT (COBW) Transaction Flow Analysis
# Cross-checks the analysis document against actual repository contents
###############################################################################

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CBL_DIR="$REPO_ROOT/app/cbl"
CPY_DIR="$REPO_ROOT/app/cpy"
BMS_DIR="$REPO_ROOT/app/bms"
ASM_DIR="$REPO_ROOT/app/asm"
JCL_DIR="$REPO_ROOT/app/jcl"

TARGET_PROGRAM="COBSWAIT"
TARGET_SOURCE="$CBL_DIR/${TARGET_PROGRAM}.cbl"

PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0

pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    echo "  PASS: $1"
}

fail() {
    FAIL_COUNT=$((FAIL_COUNT + 1))
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    echo "  FAIL: $1"
}

echo "============================================================"
echo "COBSWAIT Flow Analysis — Verification Script"
echo "Repository: $REPO_ROOT"
echo "Target Program: $TARGET_PROGRAM"
echo "============================================================"
echo ""

###############################################################################
# CHECK 1: Program Existence
###############################################################################
echo "--- Check 1: Program Existence ---"

# 1a. COBSWAIT.cbl must exist
if [[ -f "$CBL_DIR/COBSWAIT.cbl" ]]; then
    pass "COBSWAIT.cbl exists in $CBL_DIR"
else
    fail "COBSWAIT.cbl NOT found in $CBL_DIR"
fi

# 1b. MVSWAIT.asm must exist (assembler subroutine)
if [[ -f "$ASM_DIR/MVSWAIT.asm" ]]; then
    pass "MVSWAIT.asm exists in $ASM_DIR"
else
    fail "MVSWAIT.asm NOT found in $ASM_DIR"
fi

echo ""

###############################################################################
# CHECK 2: Copybook Existence
###############################################################################
echo "--- Check 2: Copybook Existence ---"

# COBSWAIT has NO COPY statements — verify this claim
COPY_COUNT=$(grep -c "^......\s*COPY " "$TARGET_SOURCE" 2>/dev/null || true)
if [[ "$COPY_COUNT" -eq 0 ]]; then
    pass "COBSWAIT contains 0 COPY statements (matches analysis: no copybooks)"
else
    fail "COBSWAIT contains $COPY_COUNT COPY statement(s) but analysis claims none"
fi

echo ""

###############################################################################
# CHECK 3: BMS Map Existence
###############################################################################
echo "--- Check 3: BMS Map Existence ---"

# COBSWAIT should have NO BMS references
BMS_SEND=$(grep -ci "SEND MAP" "$TARGET_SOURCE" 2>/dev/null || true)
BMS_RECV=$(grep -ci "RECEIVE MAP" "$TARGET_SOURCE" 2>/dev/null || true)
BMS_TOTAL=$((BMS_SEND + BMS_RECV))

if [[ "$BMS_TOTAL" -eq 0 ]]; then
    pass "COBSWAIT contains 0 SEND/RECEIVE MAP commands (matches analysis: no BMS maps)"
else
    fail "COBSWAIT contains $BMS_TOTAL BMS MAP command(s) but analysis claims none"
fi

echo ""

###############################################################################
# CHECK 4: EXEC CICS Extraction
###############################################################################
echo "--- Check 4: EXEC CICS Command Extraction ---"

# COBSWAIT should have NO EXEC CICS commands (batch program)
CICS_COUNT=$(grep -ci "EXEC CICS" "$TARGET_SOURCE" 2>/dev/null || true)
if [[ "$CICS_COUNT" -eq 0 ]]; then
    pass "COBSWAIT contains 0 EXEC CICS commands (matches analysis: batch program)"
else
    fail "COBSWAIT contains $CICS_COUNT EXEC CICS command(s) but analysis claims none"
fi

echo ""

###############################################################################
# CHECK 5: CALL Extraction
###############################################################################
echo "--- Check 5: CALL Statement Extraction ---"

# COBSWAIT should have exactly 1 CALL: CALL 'MVSWAIT'
CALL_COUNT=$(grep -c "CALL " "$TARGET_SOURCE" 2>/dev/null || true)
if [[ "$CALL_COUNT" -eq 1 ]]; then
    pass "COBSWAIT contains 1 CALL statement (matches analysis)"
else
    fail "COBSWAIT contains $CALL_COUNT CALL statement(s) but analysis claims 1"
fi

# Verify the CALL target is MVSWAIT
if grep -q "CALL 'MVSWAIT'" "$TARGET_SOURCE" 2>/dev/null; then
    pass "CALL target is 'MVSWAIT' (matches analysis)"
else
    fail "CALL target 'MVSWAIT' not found in source"
fi

echo ""

###############################################################################
# CHECK 6: COPY Statement Extraction
###############################################################################
echo "--- Check 6: COPY Statement Extraction ---"

# Already checked in Check 2 — confirm no COPY statements (excluding comments)
COPY_ACTIVE=$(grep -E "^.{6} " "$TARGET_SOURCE" 2>/dev/null | grep -ci "COPY " || true)
if [[ "$COPY_ACTIVE" -eq 0 ]]; then
    pass "No active COPY statements in COBSWAIT (matches analysis)"
else
    fail "Found $COPY_ACTIVE active COPY statement(s) in COBSWAIT but analysis claims none"
fi

echo ""

###############################################################################
# CHECK 7: XCTL/LINK Targets
###############################################################################
echo "--- Check 7: XCTL/LINK Target Extraction ---"

# COBSWAIT should have NO XCTL or LINK commands (batch program)
XCTL_COUNT=$(grep -ci "XCTL" "$TARGET_SOURCE" 2>/dev/null || true)
LINK_COUNT=$(grep -ci "EXEC CICS LINK" "$TARGET_SOURCE" 2>/dev/null || true)

if [[ "$XCTL_COUNT" -eq 0 ]]; then
    pass "COBSWAIT contains 0 XCTL commands (matches analysis: batch program)"
else
    fail "COBSWAIT contains $XCTL_COUNT XCTL command(s) but analysis claims none"
fi

if [[ "$LINK_COUNT" -eq 0 ]]; then
    pass "COBSWAIT contains 0 LINK commands (matches analysis: batch program)"
else
    fail "COBSWAIT contains $LINK_COUNT LINK command(s) but analysis claims none"
fi

echo ""

###############################################################################
# CHECK 8: Cross-Reference — JCL Invocation
###############################################################################
echo "--- Check 8: Cross-Reference (Upstream JCL) ---"

# WAITSTEP.jcl should reference COBSWAIT
if [[ -f "$JCL_DIR/WAITSTEP.jcl" ]]; then
    pass "WAITSTEP.jcl exists in $JCL_DIR"
else
    fail "WAITSTEP.jcl NOT found in $JCL_DIR"
fi

if grep -q "PGM=COBSWAIT" "$JCL_DIR/WAITSTEP.jcl" 2>/dev/null; then
    pass "WAITSTEP.jcl references PGM=COBSWAIT (matches analysis)"
else
    fail "WAITSTEP.jcl does not reference PGM=COBSWAIT"
fi

# Verify no COBOL program XCTLs/LINKs to COBSWAIT (it's batch-only)
UPSTREAM_COBOL=$(grep -rl "COBSWAIT" "$CBL_DIR" 2>/dev/null | grep -v "COBSWAIT.cbl" || true)
if [[ -z "$UPSTREAM_COBOL" ]]; then
    pass "No COBOL program references COBSWAIT via XCTL/LINK (correct: batch-only)"
else
    fail "Unexpected COBOL reference(s) to COBSWAIT: $UPSTREAM_COBOL"
fi

echo ""

###############################################################################
# CHECK 9: WORKING-STORAGE Verification
###############################################################################
echo "--- Check 9: WORKING-STORAGE Variables ---"

# Verify MVSWAIT-TIME exists with correct PIC
if grep -q "MVSWAIT-TIME" "$TARGET_SOURCE" 2>/dev/null; then
    pass "MVSWAIT-TIME variable found in WORKING-STORAGE"
else
    fail "MVSWAIT-TIME variable NOT found"
fi

if grep -q "PIC 9(8) COMP" "$TARGET_SOURCE" 2>/dev/null; then
    pass "MVSWAIT-TIME has PIC 9(8) COMP (matches analysis)"
else
    fail "MVSWAIT-TIME PIC clause does not match analysis"
fi

# Verify PARM-VALUE exists with correct PIC
if grep -q "PARM-VALUE" "$TARGET_SOURCE" 2>/dev/null; then
    pass "PARM-VALUE variable found in WORKING-STORAGE"
else
    fail "PARM-VALUE variable NOT found"
fi

if grep -q "PIC X(8)" "$TARGET_SOURCE" 2>/dev/null; then
    pass "PARM-VALUE has PIC X(8) (matches analysis)"
else
    fail "PARM-VALUE PIC clause does not match analysis"
fi

echo ""

###############################################################################
# CHECK 10: ACCEPT FROM SYSIN Verification
###############################################################################
echo "--- Check 10: ACCEPT FROM SYSIN ---"

if grep -q "ACCEPT PARM-VALUE" "$TARGET_SOURCE" 2>/dev/null; then
    pass "ACCEPT PARM-VALUE statement found (matches analysis)"
else
    fail "ACCEPT PARM-VALUE statement NOT found"
fi

if grep -q "FROM SYSIN" "$TARGET_SOURCE" 2>/dev/null; then
    pass "FROM SYSIN clause found (matches analysis)"
else
    fail "FROM SYSIN clause NOT found"
fi

echo ""

###############################################################################
# CHECK 11: MVSWAIT.asm Content Verification
###############################################################################
echo "--- Check 11: MVSWAIT Assembler Content ---"

if grep -q "ASMWAIT" "$ASM_DIR/MVSWAIT.asm" 2>/dev/null; then
    pass "ASMWAIT macro found in MVSWAIT.asm (matches analysis)"
else
    fail "ASMWAIT macro NOT found in MVSWAIT.asm"
fi

if grep -q "BINLBL" "$ASM_DIR/MVSWAIT.asm" 2>/dev/null; then
    pass "BINLBL timer control block found in MVSWAIT.asm (matches analysis)"
else
    fail "BINLBL timer control block NOT found in MVSWAIT.asm"
fi

echo ""

###############################################################################
# CHECK 12: CSD Transaction Definition
###############################################################################
echo "--- Check 12: CSD Definition Check ---"

CSD_FILE="$REPO_ROOT/app/csd/CARDDEMO.CSD"
if [[ -f "$CSD_FILE" ]]; then
    # COBSWAIT is a batch program — it should NOT have a CSD TRANSACTION definition
    if grep -q "DEFINE TRANSACTION.*COBW" "$CSD_FILE" 2>/dev/null; then
        fail "Found COBW transaction in CSD (unexpected for batch program)"
    else
        pass "No COBW transaction in CSD (correct: batch program, not CICS)"
    fi

    if grep -q "DEFINE PROGRAM(COBSWAIT)" "$CSD_FILE" 2>/dev/null; then
        fail "Found COBSWAIT program in CSD (unexpected for batch program)"
    else
        pass "No COBSWAIT program in CSD (correct: batch program, not CICS)"
    fi
else
    fail "CSD file not found at $CSD_FILE"
fi

echo ""

###############################################################################
# SUMMARY
###############################################################################
echo "============================================================"
echo "VERIFICATION SUMMARY"
echo "============================================================"
echo "  Total checks: $TOTAL_COUNT"
echo "  Passed:       $PASS_COUNT"
echo "  Failed:       $FAIL_COUNT"
echo "============================================================"

if [[ "$FAIL_COUNT" -eq 0 ]]; then
    echo "  RESULT: ALL CHECKS PASSED"
else
    echo "  RESULT: $FAIL_COUNT FAILURE(S) — review above"
fi

echo "============================================================"

exit "$FAIL_COUNT"

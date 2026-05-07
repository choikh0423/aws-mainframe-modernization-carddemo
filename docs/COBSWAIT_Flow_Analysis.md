# COBSWAIT (COBW) — Batch Wait Utility: Transaction Flow Analysis

## Section 1: Flow Overview

| Attribute            | Value                                      |
|----------------------|--------------------------------------------|
| **Program Name**     | COBSWAIT                                   |
| **Transaction ID**   | COBW (JCL job step name: WAITSTEP)         |
| **Program Type**     | Batch COBOL Program                        |
| **Function**         | Utility to pause execution for a specified number of centiseconds |
| **Application**      | CardDemo                                   |
| **Invocation Method**| JCL (`EXEC PGM=COBSWAIT`) — **not** a CICS online transaction |

### Description

COBSWAIT is a **batch utility program**, not a CICS online transaction. Despite the transaction-ID convention used in the CardDemo inventory (COBW), this program runs in a batch region under JCL control. It accepts a wait duration (in centiseconds) from SYSIN, then calls the assembler subroutine MVSWAIT to suspend execution for that interval. It is used as a timing step between batch jobs (e.g., to allow file close/open propagation before the next job step begins).

### Execution Flow (ASCII Diagram)

```
 +-----------------+
 |   WAITSTEP.jcl  |
 |  (JCL Job)      |
 +--------+--------+
          |
          | EXEC PGM=COBSWAIT
          v
 +-----------------+
 |   COBSWAIT.cbl  |
 |  (Batch COBOL)  |
 +--------+--------+
          |
          | 1. ACCEPT PARM-VALUE FROM SYSIN
          | 2. MOVE PARM-VALUE TO MVSWAIT-TIME
          | 3. CALL 'MVSWAIT' USING MVSWAIT-TIME
          |
          v
 +-----------------+
 |  MVSWAIT.asm    |
 | (HLASM Routine) |
 +--------+--------+
          |
          | ASMWAIT (system timer)
          | Suspends for centiseconds
          |
          v
 +-----------------+
 |   STOP RUN      |
 | (Return to JCL) |
 +-----------------+
```

### Program Lifecycle

This is a **single-shot batch program** — no pseudo-conversational loop, no terminal interaction:

1. JCL scheduler invokes `COBSWAIT` via `EXEC PGM=`
2. Program reads wait duration from SYSIN DD
3. Program calls MVSWAIT assembler subroutine to sleep
4. MVSWAIT issues an `ASMWAIT` macro (system interval timer)
5. Control returns to COBSWAIT, which executes `STOP RUN`
6. JCL continues with the next job step

---

## Section 2: Programs Involved

| # | Program    | Type           | Language | Function                                 | Called By    | Call Method         |
|---|------------|----------------|----------|------------------------------------------|--------------|---------------------|
| 1 | COBSWAIT   | Batch main     | COBOL    | Accept wait time from SYSIN, invoke timer | WAITSTEP.jcl | JCL EXEC PGM=      |
| 2 | MVSWAIT    | Subroutine     | HLASM    | System-level interval wait (centiseconds) | COBSWAIT     | CALL 'MVSWAIT'      |

### Program Details

#### COBSWAIT (app/cbl/COBSWAIT.cbl)

- **PROGRAM-ID:** COBSWAIT
- **Type:** Batch COBOL
- **Lines of code:** 41
- **EXEC CICS commands:** None
- **COPY statements:** None
- **CALL statements:** 1 — `CALL 'MVSWAIT' USING MVSWAIT-TIME`
- **VSAM I/O:** None
- **BMS maps:** None
- **COMMAREA:** None

#### MVSWAIT (app/asm/MVSWAIT.asm)

- **Type:** IBM High-Level Assembler (HLASM) subroutine
- **Lines of code:** 31
- **Function:** Receives a fullword binary value (centiseconds) via standard COBOL linkage, stores it in a timer event control block (`BINLBL`), issues `ASMWAIT BINLBL` to invoke the system interval timer, then returns to the calling COBOL program
- **Register usage:**
  - R15 = base register (entry point)
  - R5 = address of delay parameter
  - R1 = delay value loaded from parameter
  - R14 = return address
- **Return code:** R15 zeroed (RC=0) on normal return

---

## Section 3: Copybooks

COBSWAIT contains **no COPY statements**. All data items are defined inline in WORKING-STORAGE.

| Copybook | Used By | Status |
|----------|---------|--------|
| *(none)* | —       | —      |

**Note:** Unlike CICS online programs in CardDemo (which use COCOM01Y, DFHAID, DFHBMSCA, etc.), this batch utility is entirely self-contained with no shared copybook dependencies.

---

## Section 4: VSAM File Operations

COBSWAIT performs **no VSAM file I/O**. It does not open, read, write, or browse any datasets.

| File     | Operation | Status |
|----------|-----------|--------|
| *(none)* | —         | —      |

### Data Input

The only data input is via the `ACCEPT ... FROM SYSIN` statement, which reads from the JCL SYSIN DD card:

```
//SYSIN    DD *
00003600      VALUE IN CENTISECONDS
/*
```

This provides the wait duration as an 8-character string (e.g., `00003600` = 36 seconds = 3600 centiseconds).

---

## Section 5: BMS Screen Map

COBSWAIT has **no BMS screen maps**. It is a batch program with no terminal interaction.

| Map/Mapset | Status |
|------------|--------|
| *(none)*   | —      |

---

## Section 6: COMMAREA Structure

COBSWAIT has **no COMMAREA**. As a batch program, it does not participate in the CICS COMMAREA data-passing mechanism used by the CardDemo online programs.

| Field | Status |
|-------|--------|
| *(none)* | — |

### Contrast with Online Programs

Online CardDemo programs (e.g., COSGN00C, COMEN01C, COTRN02C) pass the shared COMMAREA defined in `COCOM01Y.cpy` via `EXEC CICS RETURN TRANSID(...) COMMAREA(...)` and `EXEC CICS XCTL PROGRAM(...) COMMAREA(...)`. COBSWAIT bypasses this mechanism entirely because it runs outside the CICS region.

---

## Section 7: Business Logic Flow

### Step-by-Step Execution

```
Step 1: ACCEPT PARM-VALUE FROM SYSIN
        ┌─────────────────────────────────────────────────┐
        │ Reads 8-byte character string from SYSIN DD     │
        │ Example input: "00003600" (3600 centiseconds)   │
        └────────────────────┬────────────────────────────┘
                             │
Step 2: MOVE PARM-VALUE TO MVSWAIT-TIME
        ┌─────────────────────────────────────────────────┐
        │ Converts PIC X(8) string to PIC 9(8) COMP       │
        │ (binary fullword) for assembler consumption     │
        └────────────────────┬────────────────────────────┘
                             │
Step 3: CALL 'MVSWAIT' USING MVSWAIT-TIME
        ┌─────────────────────────────────────────────────┐
        │ Passes binary wait value to HLASM subroutine    │
        │ MVSWAIT issues ASMWAIT macro to system timer    │
        │ Execution suspends for the specified interval   │
        └────────────────────┬────────────────────────────┘
                             │
Step 4: STOP RUN
        ┌─────────────────────────────────────────────────┐
        │ Normal program termination                       │
        │ Control returns to JCL; next step executes      │
        └─────────────────────────────────────────────────┘
```

### Error Handling

COBSWAIT has **no explicit error handling**:
- No `ON EXCEPTION` or `NOT ON EXCEPTION` clauses on the CALL
- No validation of PARM-VALUE (e.g., non-numeric input will cause a data exception S0C7 abend)
- No RESP/RESP2 checks (not applicable — no EXEC CICS)
- If MVSWAIT abends, the abend propagates to the JCL step, which records a non-zero condition code

### Input Validation Gaps

| Scenario                  | Behavior                                    |
|---------------------------|---------------------------------------------|
| Valid numeric input       | Normal wait, RC=0                           |
| Non-numeric input         | S0C7 data exception abend on MOVE           |
| Zero value ("00000000")   | Immediate return (zero-length wait)         |
| Missing SYSIN DD          | JCL error (DD not found)                    |
| Empty SYSIN               | Spaces moved to COMP field — S0C7 abend     |

---

## Section 8: Complete File Inventory

### Source Files

| # | File                      | Type        | Path                        | Purpose                          |
|---|---------------------------|-------------|-----------------------------|----------------------------------|
| 1 | COBSWAIT.cbl              | COBOL       | `app/cbl/COBSWAIT.cbl`     | Main batch wait utility program  |
| 2 | MVSWAIT.asm               | HLASM       | `app/asm/MVSWAIT.asm`      | Assembler timer subroutine       |
| 3 | WAITSTEP.jcl              | JCL         | `app/jcl/WAITSTEP.jcl`     | JCL job to invoke COBSWAIT       |

### Copybooks

*(None)*

### BMS Maps

*(None)*

### VSAM Data Files

*(None)*

---

## Section 9: WORKING-STORAGE Record Layout

COBSWAIT defines only two data items (no record layouts for file I/O):

```
WORKING-STORAGE SECTION.
01 MVSWAIT-TIME                    PIC 9(8) COMP.
01 PARM-VALUE                      PIC X(8).
```

| Level | Field Name     | PIC Clause   | Bytes | Description                              |
|-------|----------------|--------------|-------|------------------------------------------|
| 01    | MVSWAIT-TIME   | 9(8) COMP    | 4     | Binary fullword — wait time in centiseconds, passed to MVSWAIT |
| 01    | PARM-VALUE     | X(8)         | 8     | Character input read from SYSIN DD       |

### MVSWAIT Linkage

MVSWAIT receives one parameter via standard OS linkage:
- R1 → parameter list → address of `MVSWAIT-TIME` (fullword binary, centiseconds)

### WAITSTEP JCL Parameters

```jcl
//WAIT     EXEC PGM=COBSWAIT
//STEPLIB  DD DSN=AWS.M2.CARDDEMO.LOADLIB,DISP=SHR
//SYSOUT   DD SYSOUT=*
//SYSIN    DD *
00003600      VALUE IN CENTISECONDS
/*
```

| DD Name | Purpose                                    |
|---------|--------------------------------------------|
| STEPLIB | Load library containing COBSWAIT and MVSWAIT modules |
| SYSOUT  | Standard print output                      |
| SYSIN   | Input: 8-byte centisecond value            |

Default wait: `00003600` = 3,600 centiseconds = **36 seconds**

---

## Section 10: Observations

### What This Flow Demonstrates Well

1. **Minimal batch utility pattern** — COBSWAIT is a textbook example of a single-purpose batch utility: accept input, perform one action, exit. This simplicity makes it straightforward to migrate.

2. **COBOL-to-Assembler interop** — The CALL to MVSWAIT demonstrates the standard COBOL-to-HLASM linkage convention (R1 parameter list, R13 save area chain, R14 return). This pattern is common in mainframe applications for system-level functions not available in COBOL.

3. **JCL-driven batch orchestration** — WAITSTEP.jcl shows the typical pattern of using a wait step between batch jobs to allow asynchronous operations (file closes, propagation delays) to complete.

### Gaps Relative to Migration

1. **Platform-specific timer** — The `ASMWAIT` macro is an IBM mainframe system service. On a modernized platform, this must be replaced with an equivalent (e.g., `Thread.sleep()` in Java, `time.sleep()` in Python, `WAITCICS` or OS-level sleep on rehosting platforms).

2. **Assembler dependency** — MVSWAIT.asm requires an assembler toolchain (HLASM). Migration options:
   - **Rewrite in COBOL:** Replace the CALL with a COBOL-native delay mechanism (platform-dependent)
   - **Rewrite in target language:** Trivial function in Java/Python/C
   - **Rehosting:** Micro Focus or UniKix may provide ASMWAIT emulation

3. **No input validation** — Non-numeric SYSIN input causes an abend. A modernized version should validate input and return meaningful error codes.

4. **SYSIN input mechanism** — The `ACCEPT ... FROM SYSIN` pattern maps to stdin or a configuration parameter in modernized environments.

5. **JCL dependency** — The WAITSTEP.jcl orchestration must be replaced with the target scheduler (Control-M replacement, cron, AWS Step Functions, etc.).

---

## Section 11: Migration Complexity Comparison

### Complexity Assessment

| Dimension                | COBSWAIT Rating | Notes                                   |
|--------------------------|----------------|-----------------------------------------|
| Lines of COBOL           | Very Low (41)  | Trivial program                         |
| Copybook dependencies    | None           | Self-contained                          |
| CICS dependencies        | None           | Batch program — no CICS API usage       |
| VSAM I/O                 | None           | No file operations                      |
| BMS screen maps          | None           | No terminal UI                          |
| COMMAREA usage           | None           | No inter-program data passing           |
| Assembler dependencies   | 1 (MVSWAIT)   | Requires platform-specific replacement  |
| DB2/IMS dependencies     | None           | No database access                      |
| Business logic           | Minimal        | Accept → Convert → Wait → Stop          |
| Error handling           | None           | No validation or exception handling     |

### Migration Effort Estimate

| Target Platform          | Effort     | Approach                                |
|--------------------------|------------|-----------------------------------------|
| **AWS Rehost (Micro Focus)** | Trivial | MVSWAIT may need recompilation or emulation; COBOL unchanged |
| **AWS Rehost (UniKix)**      | Trivial | Similar to Micro Focus approach         |
| **AWS Refactor (Java)**      | Trivial | Replace with `Thread.sleep(centiseconds * 10)` |
| **AWS Refactor (Python)**    | Trivial | Replace with `time.sleep(centiseconds / 100)` |
| **AWS Step Functions**       | Trivial | Replace with a Wait state in the state machine |
| **Kubernetes/ECS**           | Trivial | Replace with a sleep container or init delay |

### Key Migration Decision

The primary question is whether the **wait step is still needed** in the modernized architecture. Many mainframe wait steps exist because:
- VSAM file close/open between online and batch requires propagation time
- JES spool processing needs time to complete
- Control-M job dependencies are time-based rather than event-based

In a cloud-native architecture with event-driven orchestration (e.g., AWS Step Functions, EventBridge), explicit wait steps can often be **eliminated entirely** and replaced with event triggers or completion callbacks.

# COBSWAIT — functional requirement

Program `app/cbl/COBSWAIT.cbl`, run by job WAITSTEP. The only COBOL program in S-17.

## Legacy behaviour

Four statements (`COBSWAIT.cbl:34-40`) over two working-storage items (`:30-31`):

```cobol
       01 MVSWAIT-TIME                    PIC 9(8) COMP.
       01 PARM-VALUE                      PIC X(8).

       PROCEDURE DIVISION.

           ACCEPT PARM-VALUE      FROM SYSIN.
           MOVE  PARM-VALUE       TO MVSWAIT-TIME.
           CALL 'MVSWAIT'       USING MVSWAIT-TIME.

           STOP RUN.
```

| Aspect | Behaviour | Source |
|---|---|---|
| Entry | `PGM=COBSWAIT` from WAITSTEP.jcl; no `PROCEDURE DIVISION USING`, so no PARM is received despite the comments | `WAITSTEP.jcl:22`, `COBSWAIT.cbl:34` |
| Input | The first SYSIN record; columns 1-8 into `PARM-VALUE PIC X(8)` | `COBSWAIT.cbl:31, 36` |
| Conversion | `MOVE PARM-VALUE TO MVSWAIT-TIME` — alphanumeric to `PIC 9(8) COMP`, giving a binary count of centiseconds | `COBSWAIT.cbl:37` |
| Business rule | Wait for that many centiseconds | `WAITSTEP.jcl:20` |
| Call | `CALL 'MVSWAIT' USING MVSWAIT-TIME` — static call to the Assembler timer; a literal, not a symbolic program name, so there is no `CDEMO-TO-PROGRAM`/`CCARD-NEXT-PROG` indirection to trace | `COBSWAIT.cbl:38` |
| Validation | None | `COBSWAIT.cbl:34-40` — no `IF`, `EVALUATE` or `NUMERIC` test |
| Messages | None — no `DISPLAY`, no literal message text anywhere in the program | `COBSWAIT.cbl` |
| Files | None — empty `INPUT-OUTPUT SECTION`, no FD, no `OPEN`/`READ`/`WRITE` | `COBSWAIT.cbl:25-26` |
| Screens | None — batch program, no BMS map | inventory §2.2 |
| Return code | Not set. `MVSWAIT` zeroes R15 before returning (`MVSWAIT.asm:26`), so the step ends with code 0 unless the address space abends | `COBSWAIT.cbl:40`, `MVSWAIT.asm:24-27` |

`MVSWAIT` itself (`MVSWAIT.asm:17-30`) saves the caller's registers, loads the fullword it was passed and
issues `ASMWAIT` — "START INTERVAL CONTROL TIMER" (`:23`) — then restores the registers and returns 0.
No business logic; boundary B-04/B-03 classify it as replaced, not ported.

## Target behaviour

`com.carddemo.batch.operations.WaitControlCard` reproduces the reading rules (FR-OC-02, FR-OC-03,
FR-OC-05, FR-OC-06, FR-OC-08, FR-OC-11) and `WaitStepJobConfiguration` performs the wait itself with the
JDK's timer instead of `MVSWAIT` (FR-OC-07): a Java thread sleep of `centiseconds × 10` milliseconds,
which is the same observable behaviour — elapse the interval, then end normally.

One deliberate deviation, FR-OC-09/FR-OC-10: a card whose value columns are not eight digits is rejected
and abends the step, where the legacy program would MOVE it into a binary field with an undefined result
and wait for an arbitrary interval with return code 0. See the deviation note in
`OperationsChain_functional_requirement.md`.

Tests: `WaitControlCardTest`, `WaitStepJobTest`.

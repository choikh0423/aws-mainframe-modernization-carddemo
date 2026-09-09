# WAITSTEP — functional requirement

Job `app/jcl/WAITSTEP.jcl`, one step running COBSWAIT. The program's own requirements are in
`COBSWAIT_functional_requirement.md`; this document covers the job.

## Legacy behaviour

| Aspect | Behaviour | Source |
|---|---|---|
| Trigger | Scheduler, between the last processing job of a chain and the chain's OPENFIL (in the CA-7 reference-data chain also between two CLOSEFIL occurrences) | orchestration contract §2, §3 |
| Step | `//WAIT EXEC PGM=COBSWAIT`, STEPLIB `AWS.M2.CARDDEMO.LOADLIB` | `WAITSTEP.jcl:22-23` |
| Control card | In-stream SYSIN, one record: `00003600      VALUE IN CENTISECONDS` | `WAITSTEP.jcl:25-26` |
| Documented contract | "WAIT FOR CENTISECONDS IN THE PARM EG: 00003600 = 36 SECONDS" | `WAITSTEP.jcl:20` |
| Purpose | Give CICS time to complete the CLOSE requested by CLOSEFIL, and to settle before OPENFIL | inferred from the position of every WAITSTEP occurrence in both schedulers |
| Output | `SYSOUT DD SYSOUT=*`, unused — the program writes nothing | `WAITSTEP.jcl:24`, `COBSWAIT.cbl` |
| Return code | 0 | `MVSWAIT.asm:26` |

## Target behaviour

Job `WAITSTEP`, one step `WAIT`, in `com.carddemo.batch.operations.WaitStepJobConfiguration`
(FR-OC-01). Launched like any other migrated job:

```
--spring.batch.job.name=WAITSTEP [--sysin=00003600]
```

The SYSIN card becomes the `sysin` job parameter, per the control-card convention in
`migration/carddemo/README.md`. Omitting it uses the packaged control member
`src/main/resources/ctl/WAITSTEP.sysin`, which is the in-stream card of `WAITSTEP.jcl:26` verbatim
(FR-OC-04).

**Why the job exists at all (B-04).** The wait has no business meaning in the target: nothing is quiesced
(B-15), so there is nothing to wait for. It is provided only so that a migrated chain that still names
WAITSTEP between two other streams' jobs remains runnable end to end — WAITSTEP is an endpoint of 21 of
the 40 edges in the orchestration contract. A scheduler that can express a delay between two jobs should express it
there and drop this job; that is the intended end state, and this job is the interim.

Tests: `WaitStepJobTest`.

# CLOSEFIL — functional requirement

Job `app/jcl/CLOSEFIL.jcl`. No COBOL program: one SDSF step issuing MVS modify commands.

## Legacy behaviour

| Aspect | Behaviour | Source |
|---|---|---|
| Trigger | Scheduler, at the head of a batch chain | `CardDemo.controlm`, `CardDemo.ca7` (see the orchestration contract) |
| Input | None. No PARM, no dataset, no condition-code test | `CLOSEFIL.jcl:1-2, 22-25` |
| Step | `//CLCIFIL EXEC PGM=SDSF` with `ISFOUT`/`CMDOUT` to SYSOUT and in-stream `ISFIN` | `CLOSEFIL.jcl:22-25` |
| Action | Five console commands to CICS region `CICSAWSA`, in this order: `CEMT SET FIL(TRANSACT ) CLO`, `FIL(CCXREF ) CLO`, `FIL(ACCTDAT ) CLO`, `FIL(CXACAIX ) CLO`, `FIL(USRSEC ) CLO` | `CLOSEFIL.jcl:26-30` |
| Effect | The five VSAM datasets are released by CICS so the following batch step can open them for update | — |
| Validation | None | — |
| Messages | None issued by the job; CEMT's own responses go to `CMDOUT`/`ISFOUT` | `CLOSEFIL.jcl:23-24` |
| Failure | No failure path is coded and no successor tests the return code | `CardDemo.controlm`, `CardDemo.ca7` |
| Occurrences | Scheduled as `CLOSEFIL`, and additionally as `CLOSEFIL1`/`CLOSEFIL2` in the CA-7 reference-data branches — the same JCL under different schedule ids | `CardDemo.ca7:216-217` |

## Target behaviour

**Removed, not migrated** (boundary B-15, FR-OC-20). One shared PostgreSQL database, accessed
transactionally by both the online and batch sides, so there is no dataset to quiesce and no CICS region
to command. The migrated chains wire the predecessor of each CLOSEFIL straight to its successor; see
`OperationsChain_orchestration_contract.md` §4.

Verified by `FileControlJobsRemovedTest.noFileOpenOrCloseJobExistsInTheTarget`.

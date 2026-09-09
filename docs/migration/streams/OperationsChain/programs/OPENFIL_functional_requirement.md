# OPENFIL — functional requirement

Job `app/jcl/OPENFIL.jcl`. No COBOL program: one SDSF step issuing MVS modify commands. The mirror image
of CLOSEFIL.

## Legacy behaviour

| Aspect | Behaviour | Source |
|---|---|---|
| Trigger | Scheduler, at the tail of a batch chain — always after the chain's WAITSTEP | orchestration contract §2, §3 |
| Input | None. No PARM, no dataset, no condition-code test | `OPENFIL.jcl:1-2, 22-25` |
| Step | `//OPCIFIL EXEC PGM=SDSF` with `ISFOUT`/`CMDOUT` to SYSOUT and in-stream `ISFIN` | `OPENFIL.jcl:22-25` |
| Action | Five console commands to CICS region `CICSAWSA`, in this order: `CEMT SET FIL(TRANSACT ) OPE`, `FIL(CCXREF ) OPE`, `FIL(ACCTDAT ) OPE`, `FIL(CXACAIX ) OPE`, `FIL(USRSEC ) OPE` | `OPENFIL.jcl:26-30` |
| Effect | CICS re-opens the five files, restoring online availability after the batch window | — |
| Validation | None | — |
| Messages | None issued by the job | `OPENFIL.jcl:23-24` |
| Failure | No failure path is coded and no successor tests the return code | `CardDemo.controlm`, `CardDemo.ca7` |
| Quirk | In the CA-7 network OPENFIL is once a *predecessor*: the statements chain's OPENFIL (SCHID 030) triggers the category-balance CLOSEFIL (SCHID 031) | `CardDemo.ca7:529-532` |
| Quirk | The CA-7 reference-data chain ends on a CLOSEFIL, not an OPENFIL, so the files stay closed until a later chain re-opens them | `CardDemo.ca7:303`, `:335` |

## Target behaviour

**Removed, not migrated** (boundary B-15, FR-OC-20), for the same reason as CLOSEFIL: nothing is quiesced
in the target, so nothing has to be re-enabled. The two quirks above are properties of the mainframe
schedule only and disappear with the jobs; they are recorded here so that a reader of the schedule does
not mistake them for business rules.

Verified by `FileControlJobsRemovedTest.noFileOpenOrCloseJobExistsInTheTarget`.

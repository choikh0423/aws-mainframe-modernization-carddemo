# S-17 OperationsChain — source analysis

Scope (inventory §5, `docs/migration/CardDemo_inventory.md:144`): jobs `CLOSEFIL`, `OPENFIL`, `WAITSTEP`
and the single COBOL program `COBSWAIT`, plus the Assembler timer `MVSWAIT` it calls (B-04) and the two
scheduler exports that drive every batch chain (B-14).

Everything in this stream is *operational plumbing*: no screen, no record layout, no business data. The
sections below record that explicitly rather than by omission, because "no validation" is itself a
statement about the source that the FRs are built on.

---

## 1. `app/jcl/CLOSEFIL.jcl` — quiesce the VSAM files for batch

| Aspect | Finding |
|---|---|
| Entry conditions | Submitted by the scheduler at the head of a batch chain (`CardDemo.controlm`, `CardDemo.ca7`). No JCL PARM, no input dataset, no condition codes tested. |
| Steps | One: `//CLCIFIL EXEC PGM=SDSF` (`CLOSEFIL.jcl:22`). |
| DD statements | `ISFOUT` and `CMDOUT` to SYSOUT, `ISFIN` in-stream (`CLOSEFIL.jcl:23-25`). |
| Control cards | Five MVS modify commands to the CICS region `CICSAWSA` (`CLOSEFIL.jcl:26-30`): `CEMT SET FIL(TRANSACT ) CLO`, `FIL(CCXREF ) CLO`, `FIL(ACCTDAT ) CLO`, `FIL(CXACAIX ) CLO`, `FIL(USRSEC ) CLO`. Note the trailing blank inside the parentheses — CEMT pads the file name to eight characters. |
| Record layouts | None. SDSF consumes console commands, not records. |
| Validation | None in the job. CEMT rejects an unknown file name at the region, not here. |
| Business rules | None. The effect is purely operational: a closed CICS file frees the VSAM dataset so a batch step can open it for update. |
| Error paths / messages | No program-issued message text. CEMT responses land in `CMDOUT`/`ISFOUT`; the job's own return code is SDSF's. Nothing in the estate inspects it — the schedulers trigger the successor on *completion*, not on a code (see §5). |
| File access | Indirect. The five names are CICS FCT entries for `TRANSACT`, `CCXREF` (card cross-reference alternate path), `ACCTDAT`, `CXACAIX` (card AIX) and `USRSEC`. |
| Control flow | None — no COBOL, no `XCTL`, no `LINK`. |
| Boundary | B-15. |

`CLOSEFIL1` and `CLOSEFIL2` appear in the CA-7 network as separately scheduled occurrences of the same
work running in parallel branches (`CardDemo.ca7:215-216`, `:243-244`, `:270-271`); there is no
`CLOSEFIL1.jcl`/`CLOSEFIL2.jcl` member in `app/jcl` — they are scheduler job names over the same JCL.

## 2. `app/jcl/OPENFIL.jcl` — re-enable the VSAM files for CICS

Identical in shape to CLOSEFIL: one step `//OPCIFIL EXEC PGM=SDSF` (`OPENFIL.jcl:22`), the same three DDs
(`OPENFIL.jcl:23-25`), and the same five files with `OPE` instead of `CLO` (`OPENFIL.jcl:26-30`). Same
absence of layouts, validation, messages and control flow. Same boundary, B-15.

The pairing is strict: every chain that starts with CLOSEFIL ends with OPENFIL, so the region is never
left with its files closed (§5 confirms this for all eight chains).

## 3. `app/jcl/WAITSTEP.jcl` → `app/cbl/COBSWAIT.cbl` → `app/asm/MVSWAIT.asm` — the timed wait

### 3.1 JCL

| Aspect | Finding |
|---|---|
| Entry conditions | Scheduler-submitted, always between the last processing job of a chain and OPENFIL (except in the CA-7 reference-data chain, where it also separates two CLOSEFIL occurrences — §5). |
| Steps | One: `//WAIT EXEC PGM=COBSWAIT` (`WAITSTEP.jcl:22`), STEPLIB `AWS.M2.CARDDEMO.LOADLIB` (`:23`). |
| Control card | `SYSIN DD *` (`WAITSTEP.jcl:25`) with the single record `00003600      VALUE IN CENTISECONDS` (`:26`). The comment at `:20` states the contract: "WAIT FOR CENTISECONDS IN THE PARM EG: 00003600 = 36 SECONDS". |
| Quirk | The shipped value is 3 600 centiseconds = **36 seconds**, not 3 600 seconds. The `EG:` comment is the only documentation of the unit. |

### 3.2 COBSWAIT

The whole procedure division is four statements (`COBSWAIT.cbl:34-40`):

```cobol
       PROCEDURE DIVISION.                                              00040000

           ACCEPT PARM-VALUE      FROM SYSIN.
           MOVE  PARM-VALUE       TO MVSWAIT-TIME.
           CALL 'MVSWAIT'       USING MVSWAIT-TIME.

           STOP RUN.                                                    00060000
```

Working storage is two items (`COBSWAIT.cbl:30-31`): `MVSWAIT-TIME PIC 9(8) COMP` and
`PARM-VALUE PIC X(8)`.

| Aspect | Finding |
|---|---|
| Entry conditions | Started by JCL. Despite the `FUNCTION` comment "PARM IN CENTISECONDS" (`COBSWAIT.cbl:5`) and the JCL's "IN THE PARM" (`WAITSTEP.jcl:20`), the value is **not** taken from `PARM=`: there is no `PROCEDURE DIVISION USING`, and the value is read from SYSIN. Documentation quirk, preserved as-is. |
| Input layout | One SYSIN record; only columns 1-8 are moved, because `PARM-VALUE` is `PIC X(8)`. Columns 9+ of the shipped card (`VALUE IN CENTISECONDS`) are commentary and are never examined. `ACCEPT ... FROM SYSIN` reads exactly one record, so any further cards are ignored. |
| Screen layout | None — batch program, no BMS map. |
| Validation | **None.** There is no `NUMERIC` test, no `IF`, no `EVALUATE`, no error paragraph. A non-numeric card produces an undefined `MVSWAIT-TIME` under an alphanumeric-to-`COMP` MOVE. |
| Business rules | One: wait for the number of centiseconds on the card. |
| Error paths and message text | **None.** The program contains no `DISPLAY`, no `ABEND`, no `RETURN-CODE` setting, and no literal message string of any kind. Its return code is therefore 0 unless the runtime abends it. |
| File access | **None.** No `FILE-CONTROL`, no FD, no `OPEN`/`READ`/`WRITE`. The `INPUT-OUTPUT SECTION` at `COBSWAIT.cbl:25` is empty. |
| Control flow | `CALL 'MVSWAIT'` (`COBSWAIT.cbl:38`) is the only transfer of control — a static Assembler call, not `XCTL`/`LINK`, and not symbolic: the program name is a literal, so there is no `CDEMO-TO-PROGRAM`/`CCARD-NEXT-PROG` chain to trace. `STOP RUN` (`:40`) ends the job step. |
| Boundary | B-04. |

### 3.3 MVSWAIT

`MVSWAIT.asm:17-30`: saves the caller's registers (`:19`), loads the fullword the caller passed
(`:20-22`), issues `ASMWAIT BINLBL` — "START INTERVAL CONTROL TIMER" (`:23`) — restores the registers
(`:24`), zeroes R15 (`:26`) and returns (`:27`). `BINLBL DS F` (`:28`) is the timer event control block.

So the module has no business logic and, because R15 is unconditionally zeroed, **cannot report failure**:
a wait either happens or the address space abends. Consistent with B-03/B-04 ("replaced, not ported",
`docs/migration/CardDemo_inventory.md:196`).

## 4. Data touched by the stream

None. No file in `app/data/ASCII/**` is read or written by CLOSEFIL, OPENFIL, WAITSTEP or COBSWAIT — the
only input any of them takes is the eight-character wait value. The five names in the SDSF commands are
CICS file definitions whose datasets are owned and populated by other streams (`ACCTDAT` S-01/S-02,
`CCXREF`/`CXACAIX` S-03, `TRANSACT` S-04/S-11, `USRSEC` S-06).

## 5. Scheduler graphs (B-14)

Derived mechanically from the two exports; the full table, with conditions, is the orchestration
contract in `OperationsChain_orchestration_contract.md`, and
`OperationsChainScheduleTest` re-parses both exports on every build so the contract cannot drift.

Control-M (`app/scheduler/CardDemo.controlm`) — release is by named condition, `OUTCOND` `+` on the
predecessor matched by `INCOND` on the successor:

- `DAILY-TransactionBackup`: CLOSEFIL → TRANBKP → WAITSTEP → OPENFIL (`CardDemo.controlm:3-24`).
- `WEEKLY-TransactionTypesDBRefresh`: MNTTRDB2 → TRANEXTR — **no** CLOSEFIL/OPENFIL/WAITSTEP.
- `WEEKLY-DisclosureGroupsRefresh`: MNTTRDB2 → CLOSEFIL → DISCGRP → WAITSTEP → OPENFIL; the folder is
  gated on the weekly transaction-type refresh through the shared condition
  `WEEKLY-TransactionTypesDBRefresh-MNTTRDB2` (`CardDemo.controlm:26-63`).
- `MONTHLY-InterestCalculation`: CLOSEFIL → INTCALC → COMBTRAN → WAITSTEP → OPENFIL
  (`CardDemo.controlm:64-92`).

CA-7 (`app/scheduler/CardDemo.ca7`) — release is by "TRIGGERED JOBS" on job completion, qualified by
`SCHID`; the network splits into six chains, three of which run under SCHID 031/032 branches:

- Posting, SCHID 030: CLOSEFIL → CBPAUP0J → POSTTRAN → WAITSTEP → OPENFIL (`CardDemo.ca7:42-124`).
- Reference-data reload: CLOSEFIL → TRANTYPE → WAITSTEP, which fans out to CLOSEFIL1 (SCHID 031) and
  CLOSEFIL2 (SCHID 032); CLOSEFIL1 → TRANCATG → WAITSTEP → CLOSEFIL and CLOSEFIL2 → TCATBALF → WAITSTEP →
  CLOSEFIL (`CardDemo.ca7:162-335`).
- File prints, SCHID 030: CLOSEFIL → READACCT → READCARD → READCUST → READXREF → WAITSTEP → OPENFIL
  (`CardDemo.ca7:339-448`).
- Statements, SCHID 030: CLOSEFIL → CREASTMT → TXT2PDF1 → WAITSTEP → OPENFIL (`CardDemo.ca7:467-522`).
- Category balance report: OPENFIL → CLOSEFIL (SCHID 031) → PRTCATBL → WAITSTEP → OPENFIL
  (`CardDemo.ca7:529-570`).

Nothing in either export tests a condition code: every edge fires on *completion*. That is the whole of
the completion signalling the migrated jobs have to honour (B-14), and it is why the target only has to
expose exit code 0 / 12.

## 6. Where the source contradicts the inventory

1. `docs/migration/CardDemo_inventory.md:144` gives S-17's trigger as "every Control-M folder". The
   `WEEKLY-TransactionTypesDBRefresh` folder contains neither CLOSEFIL, OPENFIL nor WAITSTEP
   (`CardDemo.controlm`, and inventory's own table at `:79` shows only MNTTRDB2 → TRANEXTR).
2. `docs/migration/CardDemo_inventory.md:83-84` renders CA-7 as one linear network
   "… → WAITSTEP → OPENFIL → TRANTYPE → (CLOSEFIL1 → TRANCATG | CLOSEFIL2 → TCATBALF) → READACCT → …".
   The export does not contain an OPENFIL → TRANTYPE edge, nor a TRANCATG/TCATBALF → READACCT edge:
   TRANTYPE and READACCT are each triggered by a CLOSEFIL occurrence, and the CLOSEFIL1/CLOSEFIL2 fan-out
   hangs off WAITSTEP, not off TRANTYPE. The inventory also omits the statements
   (CREASTMT → TXT2PDF1) and category-balance (PRTCATBL) chains entirely.
3. Not an inventory contradiction but a source quirk worth recording: TRANEXTR is defined inside the
   `WEEKLY-TransactionTypesDBRefresh` smart folder yet carries `PARENT_FOLDER="WEEKLY-DisclosureGroupsRefresh"`
   (`CardDemo.controlm:58`), disagreeing with the enclosing folder's `FOLDER_NAME`
   (`CardDemo.controlm:57`). The contract attributes each job to the folder that encloses it.
4. The inventory (`:27`) counts "1 CA-7 network"; the export is one network but six independent chains by
   trigger structure, two of which run under SCHID 031/032 rather than 030.

The contract document follows the exports, not the inventory summary.

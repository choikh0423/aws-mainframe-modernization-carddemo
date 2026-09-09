# PRTCATBL — functional requirements

`app/jcl/PRTCATBL.jcl`, "Print Trasaction Category Balance File" (the job card's spelling). There is
no COBOL program in this job: the report is produced by utilities, so the JCL *is* the specification.
Requirement numbering continues the stream document
([`../TransactionReporting_functional_requirement.md`](../TransactionReporting_functional_requirement.md)).

## Entry conditions

No parameters. Input `AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS` (TCATBALF, `app/cpy/CVTRA01Y.cpy`),
output `AWS.M2.CARDDEMO.TCATBALF.REPT` plus the intermediate unload `TCATBALF.BKUP(+1)`.

## Record layouts

Unload record, 50 bytes (PRTCATBL.jcl:37), addressed by the SORT symbols (PRTCATBL.jcl:47-50):

```text
 1-11  TRANCAT-ACCT-ID   ZD   account id, zero padded
12-13  TRANCAT-TYPE-CD   CH   transaction type code
14-17  TRANCAT-CD        ZD   transaction category code, zero padded
18-28  TRAN-CAT-BAL      ZD   balance, 9(9)V99 with the sign overpunched on the last digit
29-50  filler
```

Report record (PRTCATBL.jcl:53-56):

```text
<acct 11> <type 2> <cat 4> <nnnnnnnnn.nn><9 blanks>      = 41 characters
```

## Requirements

- **FR-1451** `DELDEF EXEC PGM=IEFBR14` with `THEFILE DD DISP=(MOD,DELETE)` (PRTCATBL.jcl:21-25)
  removes the previous report before anything is written, allocating it first if it does not exist.
  A run therefore never appends to an older report.
- **FR-1452** `STEP05R EXEC PROC=REPROC` (PRTCATBL.jcl:29-39) is an IDCAMS `REPRO` of the KSDS into
  a sequential `LRECL=50` file, in key order, one 50-byte image per category balance as laid out
  above. A negative balance keeps its zoned overpunch sign (`…5O` = −…56).
- **FR-1453** `SORT FIELDS=(TRANCAT-ACCT-ID,A,TRANCAT-TYPE-CD,A,TRANCAT-CD,A)` (PRTCATBL.jcl:52):
  ascending by account id, then transaction type code, then category code. Because all three key
  fields are zero-padded fixed-width, a character sort is the same ordering as DFSORT's ZD/CH keys
  for the data this file holds (non-negative keys only).
- **FR-1454** `OUTREC FIELDS=(TRANCAT-ACCT-ID,X, TRANCAT-TYPE-CD,X, TRANCAT-CD,X,
  TRAN-CAT-BAL,EDIT=(TTTTTTTTT.TT),9X)` (PRTCATBL.jcl:53-56): the three key fields copied as they
  stand, single blanks between them, the balance edited into nine integer digits, a decimal point
  and two decimals — every pattern position is a `T`, so there is **no zero suppression** — then
  nine trailing blanks.

  **C-1 — contradiction in the shipped JCL.** That record is 11+1+2+1+4+1+12+9 = **41** bytes, but
  `SORTOUT` declares `DCB=(LRECL=40,RECFM=FB,...)` (PRTCATBL.jcl:61). The two statements cannot both
  hold. The migration follows `OUTREC`, since it is the statement that describes the report's
  content, and writes 41 characters; the discrepancy is reported rather than silently reconciled.
- **FR-1455 (Q-5)** `EDIT=(TTTTTTTTT.TT)` has no sign position and DFSORT's default is
  `SIGNS=(,,,)`, so a negative balance prints with exactly the same characters as the equivalent
  positive one — the report cannot show a credit. Preserved, not fixed.
- **FR-1456** No `INCLUDE`/`OMIT`: every record of the file is printed. (The comment above the step,
  "Filter the TCATBALFions for a the parm date and sort by card num", PRTCATBL.jcl:41, is copied
  from `TRANREPT.jcl` and describes neither this step nor this file.)

## Control flow targets

None: `IEFBR14`, `IDCAMS` and `SORT` only. The job is not launched from any online screen in the
estate; it is a submitted utility job, and in the migrated application it is the batch job
`PRTCATBL`.

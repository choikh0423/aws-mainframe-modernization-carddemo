# COMBTRAN — functional requirement

`app/jcl/COMBTRAN.jcl` has no COBOL program: both steps are z/OS utilities (DFSORT and IDCAMS) driven by
control cards. Their behaviour is the specification, and it is migrated as a two-step Spring Batch job.
Requirement ids in brackets refer to `../InterestCalculation_functional_requirement.md`.

## STEP05R — `EXEC PGM=SORT`

| # | Requirement | Source |
|---|---|---|
| C-1 | Input is the concatenation, in this order, of `AWS.M2.CARDDEMO.TRANSACT.BKUP(0)` (the current backup generation of the transaction master) and `AWS.M2.CARDDEMO.SYSTRAN(0)` (the generation INTCALC's `TRANSACT` DD just created). Both are F/350 `CVTRA05Y` records. [FR-C2] | `COMBTRAN.jcl` `SORTIN` |
| C-2 | The sort field is declared in `SYMNAMES` as `TRAN-ID,1,16,CH` and the control card is `SORT FIELDS=(TRAN-ID,A)`: ascending, character (not numeric) comparison of bytes 1–16. [FR-C3] | `COMBTRAN.jcl` `SYMNAMES`, `SYSIN` |
| C-3 | Output `SORTOUT` is a new `TRANSACT.COMBINED(+1)` generation with the same DCB as the input (F/350). Records are neither summed nor de-duplicated: `SORT` without `SUM FIELDS` keeps every input record. [FR-C2] | `COMBTRAN.jcl` `SORTOUT DD ... DCB=(*.SORTIN)` |
| C-4 | A missing input generation is a JCL allocation failure — the job does not run with a partial input. [FR-C6] | `COMBTRAN.jcl` `DISP=SHR` |

## STEP10 — `EXEC PGM=IDCAMS`

| # | Requirement | Source |
|---|---|---|
| C-5 | `REPRO INFILE(TRANSACT) OUTFILE(TRANVSAM)` copies every record of the combined generation into `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS`, the transaction master, keyed on `TRAN-ID`. [FR-C4] | `COMBTRAN.jcl` `SYSIN` |
| C-6 | The input is already in key order (C-2), which is what makes the sequential load into the KSDS valid. [FR-C3] | `COMBTRAN.jcl` |
| C-7 | Every `CVTRA05Y` field survives the round trip through the sequential generations unchanged, including the zoned-decimal overpunch sign of `TRAN-AMT` and the space padding of the text fields. [FR-C5] | `CVTRA05Y`; `app/data/ASCII` encoding |

## Boundary note

The `TRANSACT.BKUP(0)` generation is produced by the operations chain (S-17), not by this stream. In the
migrated application the transaction master is the `transactions` table, so the migrated `STEP05R` reads
the table itself as the backup input; see the migration plan, boundary decision B-12.3.

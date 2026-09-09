# CBACT01C — functional requirements (job READACCT)

Source: `app/cbl/CBACT01C.cbl`, `app/jcl/READACCT.jcl`, copybooks `app/cpy/CVACT01Y.cpy` and
`app/cpy/CODATECN.cpy`, Assembler `app/asm/COBDATFT.asm`.
Function: read ACCTDAT in key order, print each account, and derive three sequential output files.

Requirement ids are the stream ids from `../FileReadUtilities_functional_requirement.md`.

## Job shape

* **FR-A1** — job `READACCT`, steps `PREDEL` then `STEP05` (`READACCT.jcl:22,31`).
* **FR-A2** — `PREDEL` deletes `AWS.M2.CARDDEMO.ACCTDATA.PSCOMP`, `.ARRYPS` and `.VBPS` if present and
  succeeds when they are absent (`READACCT.jcl:22-28`).
* **FR-G3** — `STEP05` browses accounts in ascending `ACCT-ID` (`CBACT01C.cbl:29-33`).
* **FR-G5** — SYSOUT opens with `START OF EXECUTION OF PROGRAM CBACT01C` and closes with
  `END OF EXECUTION OF PROGRAM CBACT01C` (`CBACT01C.cbl:141,158`).
* **FR-G6** — an empty ACCTDAT yields exactly those two lines and a completed job.

## Print output, per account (in this order)

1. **FR-A3/FR-A4** — the eleven labelled lines of `1100-DISPLAY-ACCT-RECORD` (`CBACT01C.cbl:200-212`),
   label verbatim, value in raw copybook rendering (FR-G9), then the 49-dash separator.
2. **FR-A5** — `VBRC-REC1:` + the 12-byte VB record and `VBRC-REC2:` + the 39-byte VB record
   (`CBACT01C.cbl:283-284`).
3. **FR-A6** — the raw 300-byte `ACCOUNT-RECORD` image (`CBACT01C.cbl:151`).

## Derived output files

* **FR-A7** — `OUTFILE` (`LRECL=107,RECFM=FB`), one record per account, layout `CBACT01C.cbl:56-69`.
* **FR-A8** — its reissue date is `YYYYMMDD` + two blanks, produced by the JDK replacement of `COBDATFT`
  called with in-type `2` and out-type `2` (`CBACT01C.cbl:223-233`, B-03).
* **FR-A9** — quirk: cycle debit is `2525.00` only when the account's cycle debit is zero, otherwise the
  previous record's value persists in the output field (`CBACT01C.cbl:236-238`).
* **FR-A10** — `ARRYFILE` (`LRECL=110,RECFM=FB`), one record per account with occurrences 1-3 populated
  from the account balance and the literals `1005.00`, `1525.00`, `-1025.00`, `-2500.00`, occurrences 4-5
  zero and a 4-byte space filler (`CBACT01C.cbl:169,253-261`).
* **FR-A11** — `VBRCFILE` (`LRECL=84,RECFM=VB`), two records per account of 12 and 39 data bytes, each with
  a 4-byte RDW; record 2 carries only the **year** of the reissue date (`CBACT01C.cbl:276-315`).

## Errors

* **FR-A14** — `ERROR OPENING ACCTFILE`, `ERROR OPENING OUTFILE`, `ERROR OPENING ARRAYFILE`,
  `ERROR OPENING VBRC FILE`, `ERROR READING ACCOUNT FILE`, `ERROR CLOSING ACCOUNT FILE`
  (`CBACT01C.cbl:328,345,363,381,192,399`).
* **FR-A12** — a failed write to any of the three outputs emits `ACCOUNT FILE WRITE STATUS IS:` + status
  (`CBACT01C.cbl:246,268,294,309`); a write status of `00` or `10` is accepted.
* **FR-G8/FR-G7** — every error path prints `FILE STATUS IS: NNNN<nnnn>`, then `ABENDING PROGRAM`, and
  abends with code `0999`, culprit `CBACT01C` (`CBACT01C.cbl:406-426`).

## Date utility contract (B-03)

* **FR-A13** — the replacement reproduces `COBDATFT` exactly: in-type `1` + out-type `1` →
  `YYYY-MM-DD` from `YYYYMMDD`; in-type `2` + out-type `2` → `YYYYMMDD` from `YYYY-MM-DD`; a mismatched or
  unknown type, or an in-type `1` value with `-` in position 5, sets `INVALID INPUT` in the error field and
  leaves the output date untouched (`COBDATFT.asm:30-56`).

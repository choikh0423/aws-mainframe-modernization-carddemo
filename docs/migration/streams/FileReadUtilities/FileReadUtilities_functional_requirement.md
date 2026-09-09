# S-15 FileReadUtilities — functional requirements

The business sign-off oracle for the stream. Every requirement is traceable to the COBOL/JCL source and is
covered by at least one test in `com.carddemo.batch.filereads` (test class named in the last column).

Terms: **SYSOUT** is the print output of a job (`SYSOUT DD SYSOUT=*` in the JCL); in the target it is the
file `<carddemo.batch.output-dir>/<JOB>.SYSOUT.txt`, mirrored to the job log. **Record image** is the
copybook record rendered byte-for-byte the way `DISPLAY <record>` emitted it.

---

## Common requirements (all four jobs)

| Id | Requirement | Source | Test |
|---|---|---|---|
| FR-G1 | Each JCL job is one Spring Batch `Job` named after the JCL member (`READACCT`, `READCARD`, `READXREF`, `READCUST`), with one `Step` per `EXEC PGM=`, named after the JCL step name. | `READACCT.jcl:22,31`, `READCARD.jcl:22`, `READXREF.jcl:22`, `READCUST.jcl:21` | `ReadAcctJobTest`, `ReadCardJobTest`, `ReadXrefJobTest`, `ReadCustJobTest` |
| FR-G2 | A job runs only when named on the command line (`--spring.batch.job.name=…`); it ends with exit code 0 when it completes and 12 when a step fails. | `.../BatchJobLauncher`, D-3 | `FileReadJobLaunchTest` |
| FR-G3 | Records are read in ascending primary-key order — ACCTDAT by `ACCT-ID`, CARDDAT by `CARD-NUM`, CCXREF by `XREF-CARD-NUM`, CUSTDAT by `CUST-ID` — reproducing the sequential KSDS browse. | `CBACT01C.cbl:29-33`, `CBACT02C.cbl:29-33`, `CBACT03C.cbl:29-33`, `CBCUS01C.cbl:29-33` | the four job tests |
| FR-G4 | SYSOUT is written to `<output-dir>/<JOB>.SYSOUT.txt`, one line per `DISPLAY`, in execution order. | `READACCT.jcl:49`, `READCARD.jcl:26`, … | the four job tests |
| FR-G5 | The first SYSOUT line is `START OF EXECUTION OF PROGRAM <pgm>` and the last is `END OF EXECUTION OF PROGRAM <pgm>` for the program the step runs. | `CBACT01C.cbl:141,158`, `CBACT02C.cbl:71,85`, `CBACT03C.cbl:71,85`, `CBCUS01C.cbl:71,85` | the four job tests, `PrintWriterBehaviourTest` |
| FR-G6 | An empty input file produces only those two lines and the job completes normally (file status `10` on the first read ends the loop). | `CBACT01C.cbl:180-181`, `…:98-99` in the other three | `EmptyInputJobTest` |
| FR-G7 | An unrecoverable I/O condition emits the program's error literal, then the file-status line, then `ABENDING PROGRAM`, and abends through `AbendService` with code `0999` and the failing program as culprit (B-01); the step fails and the job exits 12. | `CBACT01C.cbl:192-195,406-410`, `CBCUS01C.cbl:154-158` | `FileReadAbendTest` |
| FR-G8 | The file-status line is `FILE STATUS IS: NNNN` immediately followed by four characters: for a numeric status whose first byte is not `9`, `00` + the two status bytes (`35` → `0035`); otherwise the first byte, then the second byte's binary value in three digits (`9`/`0x04` → `9004`). | `CBACT01C.cbl:413-426` | `IoStatusFormatterTest` |
| FR-G9 | A record image is the copybook layout rendered field by field: `PIC 9(n)` zero-padded, `PIC X(n)` left-justified and space-padded, `PIC S9(a)V99` as 12 zoned digits with the sign overpunched on the last digit (`{ABCDEFGHI` / `}JKLMNOPQR`), padded to the copybook record length. Each image is byte-identical to the corresponding line of the ASCII unload it was loaded from. | `app/cpy/CVACT01Y.cpy`, `CVACT02Y.cpy`, `CVACT03Y.cpy`, `CVCUS01Y.cpy`, `app/data/ASCII/**` | `RecordImagesTest`, the four job tests |

## READACCT / CBACT01C

| Id | Requirement | Source | Test |
|---|---|---|---|
| FR-A1 | `READACCT` has two steps in order: `PREDEL`, then `STEP05`. | `READACCT.jcl:22,31` | `ReadAcctJobTest` |
| FR-A2 | `PREDEL` deletes the three output datasets if they exist and succeeds when they do not (`DISP=(MOD,DELETE,DELETE)`). | `READACCT.jcl:22-28` | `ReadAcctJobTest` |
| FR-A3 | For every account, SYSOUT carries the eleven labelled field lines in copybook order followed by a separator line of 49 dashes. | `CBACT01C.cbl:200-213` | `ReadAcctJobTest` |
| FR-A4 | The eleven labels are reproduced verbatim, including their padding, and each value is the raw field rendering of FR-G9: `ACCT-ID                 :`, `ACCT-ACTIVE-STATUS      :`, `ACCT-CURR-BAL           :`, `ACCT-CREDIT-LIMIT       :`, `ACCT-CASH-CREDIT-LIMIT  :`, `ACCT-OPEN-DATE          :`, `ACCT-EXPIRAION-DATE     :`, `ACCT-REISSUE-DATE       :`, `ACCT-CURR-CYC-CREDIT    :`, `ACCT-CURR-CYC-DEBIT     :`, `ACCT-GROUP-ID           :`. | `CBACT01C.cbl:201-211` | `ReadAcctJobTest` |
| FR-A5 | After the separator, SYSOUT carries `VBRC-REC1:` + the 12-byte VB record 1 and `VBRC-REC2:` + the 39-byte VB record 2 for the same account. | `CBACT01C.cbl:283-284` | `ReadAcctJobTest` |
| FR-A6 | The last line for an account is the raw 300-byte `ACCOUNT-RECORD` image (the main loop's `DISPLAY`), emitted after the labelled block and the two VBRC lines. | `CBACT01C.cbl:147-154` | `ReadAcctJobTest` |
| FR-A7 | `OUTFILE` receives one 107-byte record per account: id `9(11)`, status `X(01)`, current balance, credit limit and cash credit limit as 12-byte zoned `S9(10)V99`, open/expiry/reissue dates `X(10)`, current cycle credit zoned, current cycle debit as a 7-byte `COMP-3` `S9(10)V99`, group id `X(10)`. | `CBACT01C.cbl:56-69`, `READACCT.jcl:37-40` | `AccountExtractBuilderTest`, `ReadAcctJobTest` |
| FR-A8 | The reissue date written to `OUTFILE` is the account's `ACCT-REISSUE-DATE` (`YYYY-MM-DD`) reformatted to `YYYYMMDD` followed by two blanks, matching `COBDATFT` in-type `2` / out-type `2` (B-03: JDK date formatting, no Assembler call). | `CBACT01C.cbl:223-233`, `COBDATFT.asm:46-54` | `LegacyDateFormatterTest`, `AccountExtractBuilderTest` |
| FR-A9 | **Quirk, preserved:** `OUT-ACCT-CURR-CYC-DEBIT` is set to `2525.00` only when the account's `ACCT-CURR-CYC-DEBIT` is zero; otherwise the field keeps the value left there by the previous record (zero for the first record of the run). The account's own cycle-debit value is never written. | `CBACT01C.cbl:236-238` | `AccountExtractBuilderTest` |
| FR-A10 | `ARRYFILE` receives one 110-byte record per account: id `9(11)`, then five occurrences of (zoned `S9(10)V99` balance, `COMP-3` `S9(10)V99` cycle debit) and a 4-byte space filler. Occurrence 1 = (account balance, `1005.00`), 2 = (account balance, `1525.00`), 3 = (`-1025.00`, `-2500.00`), 4 and 5 = (0, 0). | `CBACT01C.cbl:71-78,169,253-261`, `READACCT.jcl:41-44` | `AccountExtractBuilderTest` |
| FR-A11 | `VBRCFILE` receives two variable-length records per account, each prefixed by a 4-byte RDW (`RECFM=VB`): record 1 is 12 data bytes (id + active status), record 2 is 39 data bytes (id, current balance, credit limit, and the first four characters of the raw reissue date as the year). | `CBACT01C.cbl:80-85,123-137,276-315`, `READACCT.jcl:45-48` | `AccountExtractBuilderTest`, `ReadAcctJobTest` |
| FR-A12 | A failed write to any of the three outputs emits `ACCOUNT FILE WRITE STATUS IS:` + the file status — the same literal for the account, array and VB files — then the FR-G8 line and the FR-G7 abend. | `CBACT01C.cbl:242-315` | `PrintWriterBehaviourTest` |
| FR-A13 | The `COBDATFT` replacement reproduces the Assembler contract for both directions: in-type `1` (`YYYYMMDD`) → out-type `1` (`YYYY-MM-DD`); in-type `2` → out-type `2`; any other type combination, an unknown in-type, or an in-type `1` input whose 5th character is `-`, yields the error message `INVALID INPUT` and no output date. | `COBDATFT.asm:30-56`, `app/cpy/CODATECN.cpy` | `LegacyDateFormatterTest` |
| FR-A14 | Reading the account file, opening or closing it, or opening any output file, fails with `ERROR READING ACCOUNT FILE`, `ERROR CLOSING ACCOUNT FILE`, `ERROR OPENING ACCTFILE`, `ERROR OPENING OUTFILE`, `ERROR OPENING ARRAYFILE` or `ERROR OPENING VBRC FILE` respectively, then FR-G7. | `CBACT01C.cbl:192,328,345,363,381,399` | `FileReadAbendTest` |

## READCARD / CBACT02C

| Id | Requirement | Source | Test |
|---|---|---|---|
| FR-C1 | `READCARD` is one job with one step, `STEP05`, running the CBACT02C logic over CARDDAT. | `READCARD.jcl:22-25` | `ReadCardJobTest` |
| FR-C2 | **Quirk, preserved:** each card is printed exactly **once** — the `DISPLAY CARD-RECORD` inside the read paragraph is commented out in the source, so only the main loop prints. | `CBACT02C.cbl:78,96` | `ReadCardJobTest` |
| FR-C3 | The printed line is the 150-byte `CARD-RECORD` image: card number `X(16)`, account id `9(11)`, CVV `9(03)`, embossed name `X(50)`, expiry date `X(10)`, active status `X(01)`, `X(59)` filler. | `app/cpy/CVACT02Y.cpy` | `RecordImagesTest`, `ReadCardJobTest` |
| FR-C4 | Open, read and close failures emit `ERROR OPENING CARDFILE`, `ERROR READING CARDFILE`, `ERROR CLOSING CARDFILE` respectively, then FR-G7 with culprit `CBACT02C`. | `CBACT02C.cbl:110,129,147` | `FileReadAbendTest` |

## READXREF / CBACT03C

| Id | Requirement | Source | Test |
|---|---|---|---|
| FR-X1 | `READXREF` is one job with one step, `STEP05`, running the CBACT03C logic over CCXREF. | `READXREF.jcl:22-25` | `ReadXrefJobTest` |
| FR-X2 | **Quirk, preserved:** each xref record is printed **twice** — once inside the read paragraph and once in the main loop. | `CBACT03C.cbl:78,96` | `ReadXrefJobTest` |
| FR-X3 | The printed line is the 50-byte `CARD-XREF-RECORD` image: card number `X(16)`, customer id `9(09)`, account id `9(11)`, `X(14)` filler. | `app/cpy/CVACT03Y.cpy` | `RecordImagesTest`, `ReadXrefJobTest` |
| FR-X4 | Open, read and close failures emit `ERROR OPENING XREFFILE`, `ERROR READING XREFFILE`, `ERROR CLOSING XREFFILE` respectively, then FR-G7 with culprit `CBACT03C`. | `CBACT03C.cbl:110,129,147` | `FileReadAbendTest` |

## READCUST / CBCUS01C

| Id | Requirement | Source | Test |
|---|---|---|---|
| FR-U1 | `READCUST` is one job with one step, `STEP05`, running the CBCUS01C logic over CUSTDAT. | `READCUST.jcl:21-24` | `ReadCustJobTest` |
| FR-U2 | **Quirk, preserved:** each customer is printed **twice** — once inside the read paragraph and once in the main loop. | `CBCUS01C.cbl:78,96` | `ReadCustJobTest` |
| FR-U3 | The printed line is the 500-byte `CUSTOMER-RECORD` image in copybook order, ending with the `X(168)` filler. | `app/cpy/CVCUS01Y.cpy` | `RecordImagesTest`, `ReadCustJobTest` |
| FR-U4 | Open, read and close failures emit `ERROR OPENING CUSTFILE`, `ERROR READING CUSTOMER FILE`, `ERROR CLOSING CUSTOMER FILE` respectively, then FR-G7 with culprit `CBCUS01C`. | `CBCUS01C.cbl:110,129,147` | `FileReadAbendTest` |

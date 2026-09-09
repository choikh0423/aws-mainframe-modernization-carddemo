# S-15 FileReadUtilities — migration plan

How the four print/verify jobs were implemented in the consolidated app, the boundary decisions taken,
and the legacy-vs-migrated parity evidence. Requirement ids refer to
`FileReadUtilities_functional_requirement.md`.

## 1. Scope

| JCL | Program | Target job | Steps |
|---|---|---|---|
| `app/jcl/READACCT.jcl` | `CBACT01C` | `READACCT` | `PREDEL`, `STEP05` |
| `app/jcl/READCARD.jcl` | `CBACT02C` | `READCARD` | `STEP05` |
| `app/jcl/READXREF.jcl` | `CBACT03C` | `READXREF` | `STEP05` |
| `app/jcl/READCUST.jcl` | `CBCUS01C` | `READCUST` | `STEP05` |

No online screen: all four are batch-only, so nothing is added to the React app or the shared route
registry. No Flyway migration either — the four jobs only read tables the Phase 0/1 baseline already
creates and seeds, so the S-15 band `V1500`–`V1599` is left unused.

## 2. Structure

Everything lives in `com.carddemo.batch.filereads` (backend). The README's §6 shape (`com.carddemo.batch.<job>`,
one `@Configuration` per JCL job, one `@Bean Step` per `EXEC PGM=`, chunk-oriented, restartable through the
shared `JobRepository`) is followed, with the four job configurations in the one stream package the lane
rules reserve for S-15 rather than four sibling packages.

| Class | Role |
|---|---|
| `ReadAcctJobConfiguration`, `ReadCardJobConfiguration`, `ReadXrefJobConfiguration`, `ReadCustJobConfiguration` | One `Job` per JCL job; steps named after the JCL step names. |
| `PrintSteps` | The shared read-and-print step: `JpaPagingItemReader` (page size 100) over a key-ordered JPQL query, chunk size 100, wrapped in `AbendingItemReader`, writing through `RecordPrintWriter`. |
| `RecordImages` | Renders each copybook record byte-for-byte (300 / 150 / 50 / 500 bytes). |
| `CobolPicture` | `PIC X(n)`, `PIC 9(n)`, zoned `S9(i)V9(s)` with sign overpunch, and `COMP-3`. |
| `LegacyDateFormatter` | The `COBDATFT` replacement (B-03). |
| `IoStatusFormatter` | The `FILE STATUS IS: NNNN…` line, including the non-numeric status rendering. |
| `SysoutFile`, `FileReadOutputs` | SYSOUT and the three READACCT datasets under `carddemo.batch.output-dir`. |
| `AccountExtractBuilder`, `AccountExtract`, `AccountExtractWriter`, `AccountPrintWriter` | CBACT01C's extra work: the labelled print block and the `OUTFILE` / `ARRYFILE` / `VBRCFILE` records. |
| `FileReadAbend`, `AbendSink`, `AbendingItemReader` | The B-01 abend seam on every open / read / write / close path. |

Shared seams are used, not duplicated: `AbendService` for the abend, the `common.domain` entities and their
repositories for the data, `BatchJobLauncher` for job selection and the exit code. `common.batch.FixedWidthRecord`
and `common.util.CobolFormat` *parse* fixed-width input and format screen fields; `CobolPicture` and
`RecordImages` do the opposite direction (rendering a record image for `DISPLAY`), which no shared seam offers.
No shared entity was modified and no column added.

Datasets become files under `carddemo.batch.output-dir` (default `target/batch-output`), named after the JCL
DSNs — `AWS.M2.CARDDEMO.ACCTDATA.PSCOMP` / `.ARRYPS` / `.VBPS` — and SYSOUT becomes `<JOB>.SYSOUT.txt`,
mirrored to the job log. `PREDEL` reproduces `IEFBR14` with `DISP=(MOD,DELETE,DELETE)`: it deletes those three
files and succeeds when they are absent.

The VSAM sequential browse becomes an ascending primary-key JPQL query per file (`ACCT-ID`, `CARD-NUM`,
`XREF-CARD-NUM`, `CUST-ID`), which is the KSDS key order the programs read in. `AT END` (status `10`) is the
reader returning `null`, so an empty table prints the two execution messages and nothing else.

## 3. Boundary decisions

**B-01 — `CALL 'CEE3ABD'` → `AbendService`.** Every failure path first emits the program's own literal and
the `FILE STATUS IS: NNNN…` line to SYSOUT, then `ABENDING PROGRAM`, then calls `AbendService` with abend
code `0999` and the program as culprit. That fails the step, and `BatchJobLauncher` turns the failed job into
exit code 12 — the condition code the JCL chain expects. Failures with no COBOL file status of their own
(a Java `IOException` on an output file, for instance) are reported as status `30`, the permanent-error
status, which is what the mainframe access method would have given for the same condition.

**B-03 — `COBDATFT` → JDK formatting.** The Assembler utility (`app/asm/COBDATFT.asm`) only rearranges
characters between `YYYYMMDD` and `YYYY-MM-DD` and writes `INVALID INPUT` for a combination it does not
support; it validates no calendar and holds no business rule. `LegacyDateFormatter` reproduces that contract
exactly, including the quirks: it does not validate the date, it rejects an in-type `1` input that already
carries separators, and it rejects every type combination the Assembler rejects. CBACT01C's only call is
in-type `2` / out-type `2` for the reissue date, but both supported directions are implemented and tested
because the copybook `CODATECN.cpy` exposes both.

## 4. Quirks preserved (not fixed)

- **CBACT02C prints each card once**, because its read-paragraph `DISPLAY` is commented out in the source,
  while CBACT03C and CBCUS01C print each record twice (read paragraph + main loop). `RecordPrintWriter`
  takes the copy count per job (FR-C2, FR-X2, FR-U2).
- **`OUT-ACCT-CURR-CYC-DEBIT` never carries the account's own value** (FR-A9): CBACT01C moves `2525.00` into
  it only when the account's cycle debit is zero, so a non-zero account keeps whatever the previous record
  left in the record area — zero for the first account of the run. `AccountExtractBuilder` keeps that state
  across records.
- **`ARRYFILE` occurrence 3 is the hard-coded pair `-1025.00` / `-2500.00`** and occurrences 4 and 5 are
  zero, regardless of the account (FR-A10).
- **The misspelled label `ACCT-EXPIRAION-DATE`** is reproduced verbatim, as are the 49-dash separator and the
  exact label padding (FR-A4).
- **`ACCOUNT FILE WRITE STATUS IS:`** is the literal CBACT01C emits for a failed write to *any* of the three
  output files, not just the account one (FR-A12).

## 5. Parity

Legacy behaviour is taken from the COBOL source and the ASCII unloads in `app/data/ASCII/**`, which are the
same files the H2 seed is generated from, so a printed record image can be compared byte-for-byte with the
unload line it came from.

| Path | Legacy | Migrated | Evidence |
|---|---|---|---|
| READCARD, 50 cards | 50 × 150-byte `CARD-RECORD` images in card-number order between the two execution messages | identical list, compared to `carddata.txt` line by line | `ReadCardJobTest` |
| READXREF, 50 xrefs | each 50-byte image printed twice | identical, 100 lines, compared to `cardxref.txt` | `ReadXrefJobTest` |
| READCUST | each 500-byte image printed twice | identical, compared to `custdata.txt` | `ReadCustJobTest` |
| READACCT, 50 accounts | 15 SYSOUT lines per account (11 labelled fields, separator, 2 VBRC lines, record image) | identical; first account asserted field by field, all 50 images compared to `acctdata.txt` | `ReadAcctJobTest` |
| READACCT outputs | `OUTFILE` 107 × 50 bytes FB, `ARRYFILE` 110 × 50 bytes FB, `VBRCFILE` 50 × (12+4 + 39+4) bytes VB with RDWs | identical sizes and the first RDW `00 10 00 00` | `ReadAcctJobTest` |
| Zoned/packed rendering | `194.00` → `00000001940{`, `-1025.00` → `00000010250}`, `2525.00` packed → `0x00 0x00 0x02 0x52 0x50 0x0C` | identical | `CobolPictureTest` |
| Empty input | two execution messages, RC 0 | identical, no extract records | `EmptyInputJobTest` |
| Abend | error literal, `FILE STATUS IS: NNNN0035`, `ABENDING PROGRAM`, RC 12 | identical | `FileReadAbendTest`, `PrintWriterBehaviourTest`, `FileReadJobLaunchTest` |

Every requirement in the FR document names the test that covers it. `mvn -B test` and the frontend
`npm ci && CI=true npm run build` are green.

## 6. Deferred / not done

- No Flyway migration in the S-15 band: nothing in the stream needs a schema change.
- No frontend work: the stream has no BMS map and no online program.
- `VBRCFILE` records are written with a 4-byte RDW to reproduce `RECFM=VB`; the file is a byte-faithful
  sequential image, not a z/OS dataset, since the target has no VSAM.

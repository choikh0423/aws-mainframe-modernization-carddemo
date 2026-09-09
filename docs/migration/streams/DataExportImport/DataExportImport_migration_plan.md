# S-16 DataExportImport — migration plan

How CBEXPORT and CBIMPORT were migrated, the boundary decisions taken, and the parity evidence.
Source analysis: `DataExportImport_analysis.md`. Requirements: `DataExportImport_functional_requirement.md`,
`programs/CBEXPORT_functional_requirement.md`, `programs/CBIMPORT_functional_requirement.md`.

## 1. What was built

| Legacy | Migrated to |
| --- | --- |
| `app/jcl/CBEXPORT.jcl` | Job `CBEXPORT` — `com.carddemo.batch.exportimport.ExportJobConfiguration` |
| `app/jcl/CBIMPORT.jcl` | Job `CBIMPORT` — `com.carddemo.batch.exportimport.ImportJobConfiguration` |
| `app/cbl/CBEXPORT.cbl` | `ExportSourceItemReader`, `ExportRecordCodec`, `ExportFileItemWriter`, `ExportStatistics` |
| `app/cbl/CBIMPORT.cbl` | `ExportFileItemReader`, `ImportRecordCodec`, `ImportOutputItemWriter`, `ImportErrorRecord`, `ImportStatistics` |
| COBOL storage formats (`COMP`, `COMP-3`, DISPLAY, overpunch) | `MainframeFieldCodec` |
| `FUNCTION CURRENT-DATE` | `MainframeTimestamps` |
| `CALL 'CEE3ABD'` | `com.carddemo.common.batch.AbendService` (boundary B-01) |
| `AWS.M2.CARDDEMO.EXPORT.DATA` and the six output files | files in `${carddemo.batch.export-import-dir}` |

One `@Bean Step` per `EXEC PGM=`, per the README:

- **CBEXPORT / STEP01** — the IDCAMS `DELETE`/`DEFINE` step. There is no VSAM cluster behind a directory
  hand-off, so what survives of the step is its observable effect: the export file starts every run empty,
  so a rerun replaces rather than appends. The step also captures the single run timestamp
  (`1050-GENERATE-TIMESTAMP`) that every record in the run is stamped with.
- **CBEXPORT / STEP02** — the program: the five master tables read in the program's order
  (customers, accounts, cross-references, transactions, cards), each mapped to a 500-byte record.
- **CBIMPORT / STEP01** — the program: read the export file, dispatch on the type byte, write the
  normalised file for that type or, for an unrecognised type, an error record.

Both steps are chunk-oriented (chunk 100) and restartable through the shared `JobRepository`: the export
writer and the import writers checkpoint their byte position in the `ExecutionContext` and truncate back to
it on open, so a restart resumes at the last committed chunk instead of duplicating records.

The master data lives in the shared tables, not in VSAM KSDS files, so each source is a paging
`RepositoryItemReader` sorted on the legacy key of that file (`custId`, `acctId`, `cardNum`, transaction id,
`cardNum`) — the target's equivalent of `ACCESS MODE IS SEQUENTIAL` over a KSDS. No entity in
`com.carddemo.common.domain` was changed and this stream adds no Flyway migration: it owns no tables.

CBIMPORT writes files and touches no repository. The legacy program did the same — it never wrote to VSAM —
and loading files into the database belongs to DATALOAD (S-18).

## 2. Boundary decisions

**B-12 — the hand-off itself (`app/jcl/FTPJCL.JCL`).** Out of scope and not migrated. The JCL FTPs a fixed
dataset to an external host with clear-text credentials in the job stream; neither the credentials nor the
external transfer are reproduced. Both jobs read and write a **configurable local directory**,
`carddemo.batch.export-import-dir` (default `./data/exportimport`). CBEXPORT writes `export.data`; CBIMPORT
reads it and writes the six normalised files next to it.

**Deferred: the transfer mechanism.** Whether the directory is backed by object storage (S3, with the export
uploaded and the import triggered by an object-created event) or by a shared filesystem mounted into both
runtimes is a deployment decision that depends on where the external counterparty ends up, and it is not
answered by the target state or the boundary register. Both options are satisfied by the same code: the jobs
only require a directory path, so the decision is a mount or a sync sidecar, not a code change. It is
deliberately left open.

**Record format.** Byte-identical to the copybook: 500-byte fixed-length records, no delimiters, no
newlines, `COMP` fields big-endian binary, `COMP-3` packed with a sign nibble, DISPLAY numerics
zero-padded, signed DISPLAY with a trailing overpunch, text left-justified and space-padded. The file the
migrated CBEXPORT writes is the file the legacy CBIMPORT would have read.

**`CARDOUT` (source vs JCL).** `CBIMPORT.cbl` declares and writes `CARDOUT`, but `CBIMPORT.jcl` has no
`CARDOUT` DD — the legacy job would have failed the open. The migrated job writes the card file, because the
program is the specification and the JCL is a deployment artefact. Recorded as FR-I-24 and reported as a
source/inventory contradiction.

**Statistics.** The `DISPLAY` lines are produced verbatim and published on the job `ExecutionContext`
(`carddemo.exportimport.export.display`, `carddemo.exportimport.import.display`) as well as logged, so
operators and tests can assert on the exact legacy text.

## 3. Quirks preserved (not fixed)

| Quirk | Where | Behaviour kept |
| --- | --- | --- |
| CBIMPORT validates nothing | `3000-VALIDATE-IMPORT` | The run always logs `CBIMPORT: Import validation completed` and `CBIMPORT: No validation errors detected`, even when error records were written (FR-I-21) |
| Unknown type is not fatal | `WHEN OTHER` | One 132-byte error record, the run continues and completes (FR-I-15) |
| Error record is 132 bytes but its named fields total 130 | `ERROR-RECORD` | Two trailing spaces (FR-I-15) |
| Error timestamp is the raw `FUNCTION CURRENT-DATE` string | `WHEN OTHER` | `yyyyMMddHHmmssSS±HHMM` space-padded to 26, not the export timestamp format (FR-I-16) |
| Error sequence keeps only the low-order 7 digits | `ERR-SEQ PIC 9(7)` | A sequence above 9,999,999 truncates (FR-I-17) |
| Branch and region are literals | `1050-GENERATE-TIMESTAMP` | Always `0001` and `NORTH`, never derived (FR-E-08, FR-E-09) |
| Export timestamp is millisecond-truncated to `.00` | `WS-EXPORT-TIMESTAMP` | `YYYY-MM-DD HH:MM:SS.00` plus four spaces in the 26-byte field (FR-E-06) |
| An error-file write failure is not fatal | `9999-WRITE-ERROR` | Logged, processing continues; a *data* file write failure abends (FR-I-22) |

## 4. Parity — legacy vs migrated, main paths

Fixtures: the `app/data/ASCII/**` unloads, loaded by DATALOAD into the H2 test database, which is the same
data the legacy jobs ran against.

| Path | Legacy | Migrated | Evidence |
| --- | --- | --- | --- |
| Export volume | One 500-byte record per master record | Identical; file length is an exact multiple of 500 | `ExportImportJobIntegrationTest.cbexportWritesOneFiveHundredByteRecordPerMasterRecordInProgramOrder` |
| Export order | Customers, accounts, xrefs, transactions, cards; each in key order | Identical run-length of type bytes | same test |
| Sequence / stamp | Global sequence from 1, one timestamp per run, branch `0001`, region `NORTH` | Identical | `everyExportedRecordCarriesTheRunStampAndItsOwnSequenceNumber` |
| Rerun | IDCAMS `DELETE`/`DEFINE` empties the cluster | File replaced, not appended | `rerunningCbexportReplacesTheFileRatherThanAppendingToIt` |
| Import fan-out | Six files, fixed lengths 500/300/50/350/150/132 | Identical counts and lengths | `cbimportSplitsTheExportFileIntoItsSixNormalisedFiles` |
| Round trip | Export then import returns the original field values | Identical | `aCustomerSurvivesTheRoundTripUnchanged` |
| Unknown type | Error record, run continues, success reported | Identical, message verbatim | `anUnknownRecordTypeProducesAnErrorRecordAndDoesNotStopTheRun` |
| Job log | The COBOL `DISPLAY` lines | Verbatim, including nine-digit counters | `bothJobsLogTheCobolDisplayLines`, `ExportImportStatisticsTest` |
| Open failure | Message then `CEE3ABD` | Message then `AbendService`, step fails, launcher exits 12 | `cbimportAbendsWhenTheExportFileIsMissing` |
| Field encoding | `COMP`, `COMP-3`, overpunch, padding | Byte-for-byte | `MainframeFieldCodecTest`, `ExportRecordCodecTest`, `ImportRecordCodecTest` |

## 5. Running the jobs

```bash
java -jar target/carddemo.jar --spring.main.web-application-type=none \
     --spring.profiles.active=postgres --spring.batch.job.name=CBEXPORT \
     --carddemo.batch.export-import-dir=/srv/carddemo/exportimport
java -jar target/carddemo.jar --spring.main.web-application-type=none \
     --spring.profiles.active=postgres --spring.batch.job.name=CBIMPORT \
     --carddemo.batch.export-import-dir=/srv/carddemo/exportimport
```

Exit code 0 on success, 12 on abend — the JCL failure condition code.

## 6. Notes for the integration branch

Once a second batch stream registers a `Job` bean, `JobLauncherTestUtils` can no longer pick one by type, so
every batch test has to name its job with `setJob`. `DataLoadJobTest` already carries that line on the
integration branch; this stream's tests do the same. Nothing in `com.carddemo.batch.dataload` was changed.

# S-16 DataExportImport — functional requirements

The business sign-off oracle for the stream. Program-level requirements live in
`programs/CBEXPORT_functional_requirement.md` (FR-E-01 … FR-E-27) and
`programs/CBIMPORT_functional_requirement.md` (FR-I-01 … FR-I-27); this document holds the stream-level
requirements (FR-S-*) that span both programs, and the traceability summary.

Analysis and source citations: `DataExportImport_analysis.md`.
Implementation and boundary decisions: `DataExportImport_migration_plan.md`.

## Stream-level requirements

| # | Requirement | Source |
|---|---|---|
| FR-S-01 | The stream provides two independently runnable batch jobs, `CBEXPORT` and `CBIMPORT`, launched by name and returning condition code 0 on success and 12 on failure. | `app/jcl/CBEXPORT.jcl`, `app/jcl/CBIMPORT.jcl`, D-3 |
| FR-S-02 | `CBEXPORT` reads the five CardDemo master files (customers, accounts, card cross-references, transactions, cards) and produces a single multi-record export file. | `CBEXPORT.cbl:151-157` |
| FR-S-03 | `CBIMPORT` reads that export file and splits it back into one normalised file per record type, plus an error file. | `CBIMPORT.cbl:272-285` |
| FR-S-04 | The export file's byte layout is exactly `CVEXPORT.cpy`: 500-byte records, the common prefix at offsets 0/1/27/31/35, and the record-type-specific data area at offset 40, including its binary (`COMP`) and packed-decimal (`COMP-3`) fields. | `app/cpy/CVEXPORT.cpy` |
| FR-S-05 | The normalised output files' byte layout is exactly the estate copybooks — `CVCUS01Y` 500, `CVACT01Y` 300, `CVACT03Y` 50, `CVTRA05Y` 350, `CVACT02Y` 150 — all fields `DISPLAY`, signed amounts carrying a trailing overpunch, so an output file is interchangeable with the corresponding `app/data/ASCII/**` unload. | `CBIMPORT.cbl:81-104`, `app/data/ASCII/acctdata.txt` |
| FR-S-06 | Round trip: for the same source data, exporting and then importing reproduces every source record's field values exactly — ids, names, addresses, dates, statuses, signed amounts (including negatives and zero), FICO scores, CVVs and 26-character timestamps. | FR-E-15…FR-E-21 with FR-I-07…FR-I-13 |
| FR-S-07 | Round-trip record counts are conserved: the import's per-type counts equal the export's per-type counts, and the import's total-read equals the export's total written. | `CBEXPORT.cbl:563-573`, `CBIMPORT.cbl:465-478` |
| FR-S-08 | Both jobs are re-runnable: rerunning `CBEXPORT` fully replaces the export file, rerunning `CBIMPORT` fully replaces the output files; neither appends to a previous run's output. | `CBEXPORT.jcl:29-40`, `CBIMPORT.jcl` `DISP=(NEW,CATLG,DELETE)` |
| FR-S-09 | The location of the export file and of the normalised output files is configuration, not code: a single configurable directory holds them, so the hand-off medium can change without touching the jobs (boundary B-12). | `.migration/04_boundary_register.md:24` |
| FR-S-10 | The transfer of the export file to and from the external system is **not** performed by this stream. The legacy estate does not automate it either: `app/jcl/FTPJCL.JCL` transfers an unrelated dataset and is referenced by neither job nor by any scheduler definition. | `app/jcl/FTPJCL.JCL:28-38`, analysis §6 |
| FR-S-11 | Neither job mutates CardDemo data: `CBEXPORT` only reads the master data, `CBIMPORT` only writes files. Loading the normalised files back into the database remains the `DATALOAD` job's business (S-18). | `CBEXPORT.cbl:34-69`, `CBIMPORT.cbl:36-71` |
| FR-S-12 | Quirks are preserved rather than corrected: the hard-coded `0001`/`NORTH` branch and region (FR-E-13), the always-successful validation messages (FR-I-21), the 21-character error timestamp (FR-I-17), the 7-digit error sequence (FR-I-18) and the non-abending error-file write failure (FR-I-25). | analysis §4-§5 |

## Traceability

Every requirement is covered by at least one test in
`migration/carddemo/backend/src/test/java/com/carddemo/batch/exportimport/`:

| Requirement | Test |
|---|---|
| FR-E-01, FR-E-02, FR-E-03, FR-E-04 | `ExportJobIntegrationTest.runsBothStepsAndReplacesTheExportFileOnRerun` |
| FR-E-05, FR-E-06, FR-E-07, FR-E-08 | `ExportJobIntegrationTest.writesEveryMasterRecordInGroupAndKeyOrder` |
| FR-E-09 … FR-E-14 | `ExportRecordCodecTest.writesTheCommonPrefixOfEveryRecordType`, `ExportJobIntegrationTest.stampsOneTimestampAndAContinuousSequenceOnEveryRecord` |
| FR-E-15 | `ExportRecordCodecTest.encodesTheCustomerRecordAtTheCopybookOffsets` |
| FR-E-16 | `ExportRecordCodecTest.encodesTheAccountRecordAtTheCopybookOffsets` |
| FR-E-17 | `ExportRecordCodecTest.encodesTheCardXrefRecordAtTheCopybookOffsets` |
| FR-E-18 | `ExportRecordCodecTest.encodesTheTransactionRecordAtTheCopybookOffsets` |
| FR-E-19 | `ExportRecordCodecTest.encodesTheCardRecordAtTheCopybookOffsets` |
| FR-E-20 | `ExportRecordCodecTest.padsCharacterFieldsToTheirPictureLength` |
| FR-E-21 | `ExportRecordCodecTest.encodesNegativeAmountsInEveryStorageForm`, `MainframeFieldCodecTest` |
| FR-E-22, FR-E-23, FR-E-24, FR-E-25 | `ExportStatisticsTest.rendersTheLegacyDisplayLines`, `ExportJobIntegrationTest.reportsTheLegacyStatisticsLines` |
| FR-E-26, FR-E-27 | `ExportJobIntegrationTest.abendsWithConditionCode12WhenTheExportFileCannotBeWritten` |
| FR-I-01, FR-I-02, FR-I-03, FR-I-06 | `ImportJobIntegrationTest.readsEveryExportedRecordAndReportsTheCounts` |
| FR-I-04, FR-I-05 | `ImportRecordDispatchTest.dispatchesEachRecordTypeToItsTargetFile` |
| FR-I-07 … FR-I-11 | `ImportRecordCodecTest.decodesAndRewritesEach<Type>RecordAtTheCopybookOffsets` |
| FR-I-12 | `ImportJobIntegrationTest.producesFilesInterchangeableWithTheAsciiUnloads` |
| FR-I-13 | `ImportRecordCodecTest.rewritesNegativeAmountsWithATrailingOverpunch` |
| FR-I-14, FR-I-15, FR-I-16, FR-I-19 | `ImportRecordDispatchTest.writesOneErrorRecordForAnUnknownRecordType` |
| FR-I-17 | `ImportErrorRecordTest.usesTheCurrentDateFunctionFormatPaddedTo26Bytes` |
| FR-I-18 | `ImportErrorRecordTest.keepsTheLowOrderSevenDigitsOfTheSequenceNumber` |
| FR-I-20, FR-I-21, FR-I-22 | `ImportStatisticsTest.rendersTheLegacyDisplayLines`, `ImportJobIntegrationTest.alwaysReportsValidationSuccess` |
| FR-I-23, FR-I-24, FR-I-26 | `ImportJobIntegrationTest.abendsWithConditionCode12WhenTheExportFileIsUnreadable` |
| FR-I-25 | `ImportErrorRecordTest.countsAnErrorRecordEvenWhenTheWriteFails` |
| FR-I-27 | `ImportJobIntegrationTest.producesTheCardOutputFileTheJclNeverDefined` |
| FR-S-01, FR-S-08 | `ExportJobIntegrationTest.runsBothStepsAndReplacesTheExportFileOnRerun`, `ImportJobIntegrationTest.replacesTheOutputFilesOnRerun` |
| FR-S-02 … FR-S-05 | the export/import codec and job tests above |
| FR-S-06, FR-S-07 | `ExportImportRoundTripTest.roundTripsEveryMasterRecordThroughTheExportFile` |
| FR-S-09 | `ExportImportRoundTripTest` (both jobs run against a temporary configured directory) |
| FR-S-10, FR-S-11 | `ExportImportRoundTripTest.leavesTheDatabaseUntouched` |
| FR-S-12 | `ImportErrorRecordTest`, `ImportStatisticsTest.rendersTheLegacyDisplayLines`, `ExportRecordCodecTest.writesTheCommonPrefixOfEveryRecordType` |

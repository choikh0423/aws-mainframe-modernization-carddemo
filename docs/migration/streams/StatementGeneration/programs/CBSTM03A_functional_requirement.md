# CBSTM03A — Functional Requirements

**Program:** `app/cbl/CBSTM03A.CBL` (BATCH, 924 lines) — "Print Account Statements from Transaction data in
two formats : 1/plain text and 2/HTML" (`CBSTM03A.CBL:5-9`).
**Runs as:** `CREASTMT.JCL` `STEP040` (`CREASTMT.JCL:79`).
**Target:** `com.carddemo.batch.statement` — step `STEP040` of job `CREASTMT`.

IDs continue the stream numbering in `../StatementGeneration_functional_requirement.md`; requirements
specific to this program are numbered `FR-A*`.

## 1. Entry conditions

| ID | Requirement | Source |
| --- | --- | --- |
| FR-A1 | The program takes no parameters and no COMMAREA. Its inputs are the four DD names `TRNXFILE`, `XREFFILE`, `CUSTFILE`, `ACCTFILE`; its outputs are `STMTFILE` (X(80)) and `HTMLFILE` (X(100)). | `CBSTM03A.CBL:39-47`, `CREASTMT.JCL:83-96` |
| FR-A2 | Before any file is opened the program walks PSA → TCB → TIOT and displays `Running JCL : <job> Step <step>`, then `DD Names from TIOT: ` and one `: <ddname> -- valid UCB` / `: <ddname> --  null UCB` line per TIOT entry. This is diagnostic output only and does not affect the statements. | `CBSTM03A.CBL:266-291` |
| FR-A3 | Both output files are opened `OUTPUT` (i.e. replaced, not appended) and the in-memory transaction table is initialised before processing. | `CBSTM03A.CBL:293-294` |

## 2. File-open sequence

| ID | Requirement | Source |
| --- | --- | --- |
| FR-A4 | Files are opened in the fixed order TRNXFILE, XREFFILE, CUSTFILE, ACCTFILE, driven by the `WS-FL-DD` dispatcher and its `ALTER`ed `GO TO`. The transaction table is loaded between the TRNXFILE open and the XREFFILE open. | `CBSTM03A.CBL:296-314, 730-816` |
| FR-A5 | Each open accepts file status `00` or `04`; any other status is an abend (see FR-38). | `CBSTM03A.CBL:736-742, 771-777, 789-795, 807-813` |
| FR-A6 | Immediately after opening TRNXFILE the program reads the first work record; status `00` or `04` continues, anything else abends with `ERROR READING TRNXFILE`. | `CBSTM03A.CBL:744-754` |

## 3. Transaction table

| ID | Requirement | Source |
| --- | --- | --- |
| FR-A7 | Work records are read sequentially (card number + transaction id key order) and grouped by card number into a 51 × 10 table with a per-card counter. | `CBSTM03A.CBL:225-233, 818-853` |
| FR-A8 **Q-6** | The priming read does not store its record: it only seeds `WS-SAVE-CARD`, `CR-CNT = 1` and `TR-CNT = 0`, and the loading loop then stores that same record at occurrence (1, 1). Because the priming read accepts only `00`/`04`, an empty work store abends with `ERROR READING TRNXFILE` / `RETURN CODE: 10`. | `CBSTM03A.CBL:744-759, 818-830` |
| FR-A9 **Q-7** | No bound check exists on either dimension: an 11th transaction for a card or a 51st distinct card overruns the table silently in the legacy program. | `CBSTM03A.CBL:818-830` |
| FR-A10 | Loading stops on status `10` (end of file) and abends on any status other than `00`/`10` with `ERROR READING TRNXFILE`. | `CBSTM03A.CBL:837-847` |

## 4. Mainline

| ID | Requirement | Source |
| --- | --- | --- |
| FR-A11 | The mainline loops until CARDXREF end of file: read next xref, read the customer by `XREF-CUST-ID`, read the account by `XREF-ACCT-ID`, write the statement header, reset the running total, then write the transactions and footer. | `CBSTM03A.CBL:316-329` |
| FR-A12 | After the loop the four input files are closed in order TRNXFILE, XREFFILE, CUSTFILE, ACCTFILE, then the two output files. A close status other than `00`/`04` abends with `ERROR CLOSING <DDNAME>`. | `CBSTM03A.CBL:331-339, 856-919` |
| FR-A13 | The lookup of a card's transactions scans the table from occurrence 1 and stops at the first occurrence whose card number is greater than the xref card number, relying on both being in ascending card-number order. | `CBSTM03A.CBL:417-419` |
| FR-A14 | Every transaction of the matching card contributes one text line and one HTML row, and its amount is added to the statement total. | `CBSTM03A.CBL:420-431` |

## 5. Output

| ID | Requirement | Source |
| --- | --- | --- |
| FR-A15 | Text and HTML line content, order and edited pictures are as specified in FR-16…FR-37 of the stream FR document. | `CBSTM03A.CBL:85-223, 435-454, 458-723` |
| FR-A16 | `INITIALIZE STATEMENT-LINES` at the top of each statement clears the variable fields only; the `FILLER` literals (rules, banners, labels) keep their `VALUE`s. | `CBSTM03A.CBL:459` |

## 6. Error handling

| ID | Requirement | Source |
| --- | --- | --- |
| FR-A17 | Every failure path displays a fixed message, then `RETURN CODE: <status>`, then `ABENDING PROGRAM`, then calls `CEE3ABD`. The exact messages are `ERROR OPENING TRNXFILE/XREFFILE/CUSTFILE/ACCTFILE`, `ERROR READING TRNXFILE/XREFFILE/CUSTFILE/ACCTFILE` and `ERROR CLOSING TRNXFILE/XREFFILE/CUSTFILE/ACCTFILE`. | `CBSTM03A.CBL:359-361, 383-385, 407-409, 739-741, 751-753, 774-776, 792-794, 810-812, 844-846, 865-867, 882-884, 898-900, 914-916, 921-923` |
| FR-A18 | There is no recovery path: a missing customer or account for an existing xref record terminates the whole run, and the statements written so far stay on the output files. | `CBSTM03A.CBL:379-386, 403-410` |

## 7. Control flow targets

`CALL 'CBSTM03B' USING WS-M03B-AREA` at `CBSTM03A.CBL:351, 377, 401, 734, 746, 769, 787, 805, 835, 860,
877, 893, 909`; `CALL 'CEE3ABD'` at `CBSTM03A.CBL:923`. No `XCTL`, no `LINK`, no `CDEMO-TO-PROGRAM` /
`CCARD-NEXT-PROG` usage — this is a batch program with no screen and no navigation.

## 8. Traceability matrix

Tests are in `migration/carddemo/backend/src/test/java/com/carddemo/batch/statement/`.

| ID | Test |
| --- | --- |
| FR-A1 | `CreateStatementJobTest.runsTheFiveCreastmtStepsAndWritesBothReports` |
| FR-A2 | **Not migrated.** The PSA/TCB/TIOT walk is a z/OS control-block diagnostic with no target equivalent; see `StatementGeneration_migration_plan.md` §5. |
| FR-A3 | `CreateStatementJobTest.abendsWhenAnXrefPointsAtAMissingCustomer` (the reports are recreated, never appended to) |
| FR-A4, FR-A12 | `StatementItemReaderTest.closesEveryFileInTheLegacyOrder` |
| FR-A5, FR-A17 | `StatementItemReaderTest.abendsWhenAFileCannotBeOpened` |
| FR-A6, FR-A8 | `TransactionIndexTest.abendsWhenTheWorkStoreIsEmpty`, `TransactionIndexTest.groupsWorkRecordsByCardNumberStartingAtTheFirstRecord` |
| FR-A7 | `TransactionIndexTest.groupsWorkRecordsByCardNumberStartingAtTheFirstRecord` |
| FR-A9 | `TransactionIndexTest.holdsAtMostTenTransactionsPerCard`, `TransactionIndexTest.holdsAtMostFiftyOneCards` |
| FR-A10 | `TransactionIndexTest.abendsWhenTheWorkStoreIsEmpty` |
| FR-A11 | `StatementItemReaderTest.producesOneStatementPerXrefRecordInCardNumberOrder` |
| FR-A13 | `TransactionIndexTest.stopsScanningOnceTheTableIsPastTheXrefCard`, `TransactionIndexTest.returnsNoTransactionsForACardThatHasNone` |
| FR-A14 | `StatementRendererTest.writesOneTextDetailLinePerTransactionAndTheirTotal`, `StatementRendererTest.writesElevenHtmlRecordsPerTransaction` |
| FR-A15, FR-A16 | `StatementRendererTest.writesTheTextStatementInLegacyOrder`, `StatementRendererTest.writesTheHtmlDocumentInLegacyOrder` |
| FR-A18 | `StatementItemReaderTest.abendsWhenTheCustomerIsNotFound`, `StatementItemReaderTest.abendsWhenTheAccountIsNotFound` |

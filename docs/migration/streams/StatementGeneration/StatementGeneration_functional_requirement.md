# S-13 StatementGeneration — Functional Requirements

**Stream:** S-13 StatementGeneration (BATCH), job `CREASTMT`.
**SOURCE:** `app/jcl/CREASTMT.JCL`, `app/cbl/CBSTM03A.CBL`, `app/cbl/CBSTM03B.CBL`, `app/cpy/COSTM01.CPY`.
**Role:** this document is the **business sign-off oracle** for the stream. Every requirement is testable,
cited to source, and covered by a test (§6). Quirk requirements (marked **Q**) are deliberate reproductions
of legacy behaviour, not defects to fix.

Notation: `b` = one blank; a field written `X(n)` keeps its COBOL trailing padding; amounts are shown with
their COBOL edited picture.

---

## 1. Job structure

| ID | Requirement | Source |
| --- | --- | --- |
| FR-01 | The stream runs as one job named `CREASTMT` with five steps, in order `DELDEF01`, `STEP010`, `STEP020`, `STEP030`, `STEP040`. | `CREASTMT.JCL:22,44,56,66,79` |
| FR-02 | `DELDEF01` empties the transaction work store before it is rebuilt (IDCAMS `DELETE` + `DEFINE` of `TRXFL.SEQ` / `TRXFL.VSAM.KSDS`), and succeeds when the store does not yet exist (`SET MAXCC = 0`). | `CREASTMT.JCL:25-39` |
| FR-03 | `STEP010` reads every TRANSACT record and writes a 350-byte `COSTM01` work record ordered by card number ascending, then transaction id ascending. | `CREASTMT.JCL:53`, `COSTM01.CPY:20-36` |
| FR-04 | `STEP010` maps TRANSACT to `COSTM01` as: card number → 1-16, TRANSACT bytes 1-262 (tran id … merchant zip) → 17-278, TRANSACT bytes 279-328 → 279-328. | `CREASTMT.JCL:54`, `CVTRA05Y.cpy:5-18` |
| FR-05 **Q-1** | The 50-byte third `OUTREC` field truncates `TRAN-PROC-TS` to its first 24 characters and leaves work-record bytes 329-350 blank. The work record is intentionally not a lossless copy. | `CREASTMT.JCL:54` |
| FR-06 | `STEP020` loads every `TRXFL.SEQ` record into the keyed work store, keyed on card number + transaction id (`KEYS(32 0)`). | `CREASTMT.JCL:29-39, 61` |
| FR-07 | `STEP030` deletes the previous run's statement outputs (`STATEMNT.PS`, `STATEMNT.HTML`) and succeeds when they do not exist. | `CREASTMT.JCL:66-75` |
| FR-08 | `STEP040` runs the statement program with the transaction work store, CARDXREF, ACCTDATA and CUSTDATA as input and produces `STATEMNT.PS` (80-byte records) and `STATEMNT.HTML` (100-byte records). | `CREASTMT.JCL:79-96`, `CBSTM03A.CBL:44-47` |
| FR-09 | Steps `STEP020`, `STEP030` and `STEP040` carry `COND=(0,NE)`: a failing step stops the job and the job ends non-zero (condition code 12 in the target). | `CREASTMT.JCL:56,66,79` |

## 2. Statement selection and ordering

| ID | Requirement | Source |
| --- | --- | --- |
| FR-10 | One statement is produced per CARDXREF record, processed in ascending card-number order. | `CBSTM03A.CBL:317-329, 345-366`, `CBSTM03B.CBL:37-41` |
| FR-11 | For each CARDXREF record the customer is read by `XREF-CUST-ID` and the account by `XREF-ACCT-ID`. | `CBSTM03A.CBL:368-414` |
| FR-12 | A statement lists that card's transactions in ascending transaction-id order and totals their `TRNX-AMT`; the total resets per statement. | `CBSTM03A.CBL:324-326, 416-434` |
| FR-13 **Q-8** | A CARDXREF card with no transactions still produces a complete statement whose total line shows a zero-suppressed zero. Transactions whose card number has no CARDXREF record are never reported. | `CBSTM03A.CBL:416-437` |
| FR-14 **Q-7** | The in-memory transaction table holds at most 51 distinct cards and at most 10 transactions per card, so only a card's first 10 transactions can appear on a statement. The legacy program has no bound check and overruns its table; the migrated job keeps the same capacities but discards the excess records and logs them (the one deliberate deviation, see the migration plan). | `CBSTM03A.CBL:225-233, 818-830` |
| FR-14a **Q-6** | An empty transaction work store is an error, not an empty run: the priming read returns `10`, which the open path does not accept, so the program logs `ERROR READING TRNXFILE` / `RETURN CODE: 10` and abends. | `CBSTM03A.CBL:744-754` |
| FR-15 **Q-9** | The statement total is accumulated in `S9(9)V99`: a total above 999,999,999.99 is truncated to its low-order 9 integer digits, without a diagnostic. | `CBSTM03A.CBL:65, 429` |

## 3. Plain-text statement (`STATEMNT.PS`, 80-byte records)

Every record is exactly 80 characters. Line order per statement is:
`L0, L1, L2, L3, L4, L5, L6, L5, L7, L8, L9, L10, L11, L12, L13, L12, {L14 per transaction}, L12, L14A, L15`
(`CBSTM03A.CBL:460, 488-502, 679, 435-437`).

| ID | Requirement | Source |
| --- | --- | --- |
| FR-16 | `L0` is 31 `*`, `START OF STATEMENT`, 31 `*`. `L15` is 32 `*`, `END OF STATEMENT`, 32 `*`. | `CBSTM03A.CBL:86-89, 143-146` |
| FR-17 | `L5`, `L10` and `L12` are 80 `-`. | `CBSTM03A.CBL:101-102, 120-121, 126-127` |
| FR-18 | `L1` is the customer name in X(75) followed by 5 blanks. The name is first name, one blank, middle name, one blank, last name, one blank — each name truncated at its first embedded blank — left-justified and blank-padded to 75. | `CBSTM03A.CBL:90-92, 462-469` |
| FR-19 | `L2` / `L3` are customer address lines 1 / 2 in X(50) + 30 blanks. | `CBSTM03A.CBL:93-98, 470-471` |
| FR-20 | `L4` is X(80) holding address line 3, state code, country code and zip — each truncated at its first embedded blank — separated by single blanks and one trailing blank, blank-padded to 80. | `CBSTM03A.CBL:99-100, 472-481` |
| FR-21 | `L6` is 33 blanks + `Basic Detailsb` + 33 blanks; `L11` is 30 blanks + `TRANSACTION SUMMARYb` + 30 blanks. | `CBSTM03A.CBL:103-106, 122-125` |
| FR-22 | `L7` is `Account ID         :` + the 11-digit zero-padded account id left-justified in X(20) + 40 blanks. | `CBSTM03A.CBL:107-110, 483` |
| FR-23 | `L8` is `Current Balance    :` + the account balance as `PIC 9(9).99-` (9 zero-padded integer digits, `.`, 2 decimals, `-` when negative and a blank when not) + 47 blanks. | `CBSTM03A.CBL:111-115, 484` |
| FR-24 **Q-11** | A balance of 1,000,000,000.00 or more loses its high-order digits in `L8`, because `ACCT-CURR-BAL` is `S9(10)V99` and the edited field is `9(9).99-`. | `CVACT01Y.cpy:7`, `CBSTM03A.CBL:113, 484` |
| FR-25 | `L9` is `FICO Score         :` + the 3-digit FICO score left-justified in X(20) + 40 blanks. | `CBSTM03A.CBL:116-119, 485` |
| FR-26 | `L13` is `Tran ID` padded to 16 + `Tran Details` padded to 51 + `  Tran Amount`. | `CBSTM03A.CBL:128-131` |
| FR-27 | `L14` is the transaction id X(16) + one blank + the description in X(49) + `$` + the amount as `PIC Z(9).99-` (leading zeros blanked, `.`, 2 decimals, `-` when negative and a blank when not). | `CBSTM03A.CBL:132-137, 676-679` |
| FR-28 **Q-10** | Descriptions longer than 49 characters are truncated to 49 in `L14`, although `TRNX-DESC` is X(100). | `COSTM01.CPY:28`, `CBSTM03A.CBL:135, 677` |
| FR-29 | `L14A` is `Total EXP:` + 56 blanks + `$` + the statement total as `PIC Z(9).99-`. | `CBSTM03A.CBL:138-142, 433-434` |

## 4. HTML statement (`STATEMNT.HTML`, 100-byte records)

Every record is exactly 100 characters (blank-padded, truncated at 100).

| ID | Requirement | Source |
| --- | --- | --- |
| FR-30 | Each statement emits, in order: the document header (`<!DOCTYPE html>` … opening `<table>`), the account banner row, the `Bank of XYZ` / `410 Terry Ave N` / `Seattle WA 99999` row, the name/address row, the `Basic Details` row, the account-id / balance / FICO row, the `Transaction Summary` row, the `Tran ID` / `Tran Details` / `Amount` heading row, one row per transaction, and the `End of Statement` footer followed by `</table>`, `</body>`, `</html>`. | `CBSTM03A.CBL:506-555, 558-672, 675-723, 439-454` |
| FR-31 | Fixed HTML lines are reproduced verbatim, including the inline styles (`background-color:#1d1d96b3;`, `#FFAF33;`, `#f2f2f2;`, `#33FFD1;`, `#33FF5E;` and the `width:25%/55%/20%` cells). | `CBSTM03A.CBL:148-211` |
| FR-32 | The banner line is `<h3>Statement for Account Number: ` + the account id in X(20) + `</h3>`. | `CBSTM03A.CBL:212-216, 529-530` |
| FR-33 | The name line is `<p style="font-size:16px">` + the name up to its first double blank + two blanks + `</p>`; the address lines are `<p>` + the address value up to its first double blank + two blanks + `</p>`. | `CBSTM03A.CBL:560-592` |
| FR-34 **Q-13** | The HTML name is taken from the first 50 characters of the 75-character text name; anything beyond column 50 is dropped. | `CBSTM03A.CBL:217-220, 560` |
| FR-35 **Q-12** | An address value that contains an embedded double blank is cut at that double blank in the HTML lines (the text lines keep it in full). | `CBSTM03A.CBL:571, 579, 587` |
| FR-36 | The basic-details lines are `<p>Account ID         : ` + X(20) account id + `</p>`, `<p>Current Balance    : ` + the `9(9).99-` balance + `</p>`, `<p>FICO Score         : ` + X(20) FICO + `</p>` — the fixed-width fields keep their trailing blanks before `</p>`. | `CBSTM03A.CBL:613-633` |
| FR-37 | Each transaction row emits three cells whose contents are `<p>` + the X(16) transaction id + `</p>`, `<p>` + the X(49) description + `</p>` and `<p>` + the `Z(9).99-` amount + `</p>`, keeping the fixed-width trailing blanks inside the `<p>`. | `CBSTM03A.CBL:675-721` |

## 5. Error paths

| ID | Requirement | Source |
| --- | --- | --- |
| FR-38 | Opening any of the four input files with a status other than `00` or `04` logs `ERROR OPENING <DDNAME>` then `RETURN CODE: <status>` then `ABENDING PROGRAM`, and abends. | `CBSTM03A.CBL:736-742, 771-777, 789-795, 807-813, 921-923` |
| FR-39 | A CARDXREF read status other than `00` or `10` logs `ERROR READING XREFFILE`, the return code and `ABENDING PROGRAM`, and abends; `10` ends the run normally. | `CBSTM03A.CBL:353-362` |
| FR-40 | A customer read that does not return `00` (e.g. `23`, customer not found) logs `ERROR READING CUSTFILE`, the return code and `ABENDING PROGRAM`, and abends. There is no skip-and-continue path. | `CBSTM03A.CBL:379-386` |
| FR-41 | An account read that does not return `00` logs `ERROR READING ACCTFILE`, the return code and `ABENDING PROGRAM`, and abends. | `CBSTM03A.CBL:403-410` |
| FR-42 | An abend fails the step, so the job does not complete and the process exit code is 12. | `CBSTM03A.CBL:921-923`, `migration/carddemo/README.md` §6 |

## 6. Traceability matrix

All tests live in `migration/carddemo/backend/src/test/java/com/carddemo/batch/statement/`.

| ID | Test |
| --- | --- |
| FR-01, FR-08, FR-09 | `CreateStatementJobTest.runsTheFiveCreastmtStepsAndWritesBothReports` |
| FR-02, FR-06 | `CreateStatementJobTest.rebuildsTheWorkStoreFromScratchOnEveryRun` |
| FR-03, FR-04 | `TrnxLayoutTest.mapsTransactToTheCostm01WorkLayout`, `CreateStatementJobTest.printsTheTransactionsAndTotalOfTheCardTheyBelongTo` |
| FR-05 | `TrnxLayoutTest.truncatesProcTimestampToTwentyFourCharacters` |
| FR-06 | `TrnxLayoutTest.reproRoundTripsTheWorkRecordThroughTheKeyedStore`, `TrnxLayoutTest.keyIsTheSixteenByteCardNumberFollowedByTheTransactionId` |
| FR-07 | `CreateStatementJobTest.abendsWhenAnXrefPointsAtAMissingCustomer` |
| FR-10, FR-11 | `StatementItemReaderTest.producesOneStatementPerXrefRecordInCardNumberOrder`, `StatementFileAccessTest.readsXreffileSequentiallyInCardNumberOrderAndReportsEndOfFile` |
| FR-12 | `TransactionIndexTest.groupsWorkRecordsByCardNumberStartingAtTheFirstRecord`, `StatementRendererTest.writesOneTextDetailLinePerTransactionAndTheirTotal` |
| FR-13 | `TransactionIndexTest.returnsNoTransactionsForACardThatHasNone`, `StatementRendererTest.writesTheStatementForACardWithNoTransactions` |
| FR-14 | `TransactionIndexTest.holdsAtMostFiftyOneCards`, `TransactionIndexTest.holdsAtMostTenTransactionsPerCard` |
| FR-14a | `TransactionIndexTest.abendsWhenTheWorkStoreIsEmpty` |
| FR-15, FR-24 | `StatementFormatTest.truncatesEditedAmountsAboveNineIntegerDigits` |
| FR-16, FR-17 | `StatementRendererTest.writesTheTextStatementInLegacyOrder` |
| FR-18 | `StatementRendererTest.buildsTheNameLineFromTheThreeNamePartsCutAtTheirFirstBlank`, `StatementFormatTest.stringDelimitedByBlankStopsAtTheFirstDelimiter` |
| FR-19, FR-20 | `StatementRendererTest.buildsTheThreeAddressLinesAtTheirCopybookWidths` |
| FR-21, FR-26 | `StatementRendererTest.writesTheTextStatementInLegacyOrder` |
| FR-22, FR-25 | `StatementRendererTest.writesAccountIdAndFicoLeftJustifiedInTwentyColumns` |
| FR-23 | `StatementFormatTest.rendersPic9EditedBalances` |
| FR-27 | `StatementFormatTest.rendersPicZEditedAmountsWithLeadingZeroSuppression`, `StatementRendererTest.writesOneTextDetailLinePerTransactionAndTheirTotal` |
| FR-28 | `StatementRendererTest.truncatesTheDescriptionToFortyNineColumns` |
| FR-29 | `StatementRendererTest.writesOneTextDetailLinePerTransactionAndTheirTotal` |
| FR-30, FR-31 | `StatementRendererTest.writesTheHtmlDocumentInLegacyOrder`, `StatementRendererTest.writesElevenHtmlRecordsPerTransaction` |
| FR-32 | `StatementRendererTest.writesTheHtmlAccountBanner` |
| FR-33, FR-35 | `StatementRendererTest.cutsTheHtmlNameAndAddressAtTheFirstDoubleBlank` |
| FR-34 | `StatementRendererTest.dropsHtmlNameCharactersPastColumnFifty` |
| FR-36 | `StatementRendererTest.writesTheHtmlBasicDetailLinesWithTheirTrailingBlanks` |
| FR-37 | `StatementRendererTest.writesThreeHtmlCellsPerTransaction` |
| FR-08 (record lengths) | `StatementRendererTest.writesEveryTextRecordAtEightyCharacters`, `StatementRendererTest.writesEveryHtmlRecordAtOneHundredCharacters` |
| FR-38 | `StatementItemReaderTest.abendsWhenAFileCannotBeOpened` |
| FR-39 | `StatementItemReaderTest.producesOneStatementPerXrefRecordInCardNumberOrder` (end of file ends the run), `StatementItemReaderTest.closesEveryFileInTheLegacyOrder` |
| FR-40 | `StatementItemReaderTest.abendsWhenTheCustomerIsNotFound` |
| FR-41 | `StatementItemReaderTest.abendsWhenTheAccountIsNotFound` |
| FR-42 | `CreateStatementJobTest.abendsWhenAnXrefPointsAtAMissingCustomer` |

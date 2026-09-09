# S-08 TransactionTypeManagement — Functional Requirements

**Role:** business sign-off oracle for the stream. Every requirement is numbered, testable, cited to legacy source and covered by at least one automated test (see §6 traceability matrix).
**Source:** `app/app-transaction-type-db2/` (`cbl/COTRTLIC.cbl`, `cbl/COTRTUPC.cbl`, `cbl/COBTUPDT.cbl`, `bms/*.bms`, `jcl/*.jcl`).
**Detail:** per-program requirements live in `programs/<PROGRAM>_functional_requirement.md`; the field-level behaviour behind each FR is in `TransactionTypeManagement_analysis.md`.

Message strings below are verbatim from the COBOL literals, including spacing, missing spaces and inconsistent punctuation.

---

## 1. CTLI — Transaction Type List (`COTRTLIC`, map `CTRTLIA`)

| ID | As a user I can… | Expected user-visible result | Source |
|---|---|---|---|
| FR-L01 | Open the list screen | Up to 7 transaction types are listed ascending by type code, with `Select / Type / Description` columns, header (`Tran: CTLI`, `Prog: COTRTLIC`, title, `mm/dd/yy`, `hh:mm:ss`), `Page <n>`, and the info line `Type U to update, D to delete any record` | COTRTLIC:498-525, 1293-1323, 1550-1552 |
| FR-L02 | Enter a 2-digit numeric Type Filter | Only the matching type code is listed | COTRTLIC:1096-1124, 339-354 |
| FR-L03 | Enter a non-numeric Type Filter | `TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER`, the filter field is highlighted and no rows are actioned | COTRTLIC:1111-1118 |
| FR-L04 | Leave the Type Filter blank, spaces or zeros | The filter is ignored (not an error) | COTRTLIC:1101-1107 |
| FR-L05 | Enter a Description Filter | Rows whose description matches `%<value>%` are listed (case-sensitive `LIKE`, as in DB2) | COTRTLIC:1155-1163 |
| FR-L06 | Enter filters that match nothing | `No Records found for these filter conditions` and the select column is protected | COTRTLIC:1239-1268 |
| FR-L07 | Browse with no data at all matching the search | `No records found for this search condition.` | COTRTLIC:1603-1724 |
| FR-L08 | Press F8 with a further page available | The next 7 rows are shown and the page number increments | COTRTLIC:766-776 |
| FR-L09 | Press F8 on the last page | The F8 that reaches the last page shows `No more pages for these search conditions` (set by the fetch that hits `SQLCODE +100`) and flags the page as shown; a repeat F8 redisplays it with `No more pages to display` | COTRTLIC:1674-1680, 1694-1701, 1536-1549 |
| FR-L10 | Press F7 on a page after the first | The previous 7 rows are shown ascending and the page number decrements | COTRTLIC:780-790, 1727-1794 |
| FR-L11 | Press F7 on the first page | The page is redisplayed with `No previous pages to display` | COTRTLIC:721-734, 1532-1535 |
| FR-L12 | Type `U` next to one row and press ENTER | The row is highlighted, its description becomes editable, and the info line shows `Update HIGHLIGHTED row. Press F10 to save` | COTRTLIC:838-850, 1523-1531 |
| FR-L13 | Change the highlighted description and press F10 | The description is saved and `HIGHLIGHTED row was updated` is shown | COTRTLIC:854-868, 1837-1892, 1509-1510 |
| FR-L14 | Press F10 with an unchanged description | `No change detected with respect to database values.` and nothing is written | COTRTLIC:1064-1073 |
| FR-L15 | Press F10 with an empty description | `Transaction Desc must be supplied.` | COTRTLIC:1083-1088, 1181-1234 |
| FR-L16 | Press F10 with a description containing punctuation/symbols | `Transaction Desc can have numbers or alphabets only.` | COTRTLIC:1181-1234 |
| FR-L17 | Type `D` next to one row and press ENTER | The row is highlighted and `Delete HIGHLIGHTED row ? Press F10 to confirm` is shown; nothing is deleted yet | COTRTLIC:794-806, 1514-1522 |
| FR-L18 | Press F10 to confirm the delete | The row is deleted, `HIGHLIGHTED row deleted.Hit Enter to continue` is shown, and the list resets to page 1 | COTRTLIC:810-834, 1896-1938, 1507-1508 |
| FR-L19 | Delete a type that still has categories | `Please delete associated child records first:` followed by the SQL diagnostics; nothing is deleted | COTRTLIC:1917-1925 |
| FR-L20 | Select two or more rows | `Please select only 1 action` and no action is taken | COTRTLIC:1024-1027, 1049-1052 |
| FR-L21 | Type any flag other than `U`/`D`/blank | `Action code selected is invalid` and no action is taken | COTRTLIC:1034-1038 |
| FR-L22 | Change a filter in the same keystroke as a row selection | The selection is discarded and only the filter is applied | COTRTLIC:991-994 |
| FR-L23 | Press F10 after changing a filter or the selected row | It is treated as ENTER (re-confirmation is required) | COTRTLIC:666-678 |
| FR-L24 | Update a row that another user already deleted, or that another user holds locked | `Record not found. Deleted by others ? ` / `Deadlock. Someone else updating ?`, each with SQL diagnostics; the selection stays pending | COTRTLIC:1861-1883 |
| FR-L25 | Press F2 | The CTTU maintenance screen (`COTRTUPC`) is opened | COTRTLIC:630-652 |
| FR-L26 | Press F3 | Return to the admin menu (`COADM01C`, tran `CA00`) | COTRTLIC:591-625 |
| FR-L27 | Press any other key | It is silently treated as ENTER — the list screen has no "invalid key" message | COTRTLIC:574-587 |

## 2. CTTU — Transaction Type Maintenance (`COTRTUPC`, map `CTRTUPA`)

| ID | As a user I can… | Expected user-visible result | Source |
|---|---|---|---|
| FR-U01 | Open the maintenance screen | Empty `Transaction Type  :` / `Description       :` fields and the info line `Enter transaction type to be maintained` | COTRTUPC:465-478, 1213-1216 |
| FR-U02 | Enter an existing type and press ENTER | The stored description is displayed and the record is in "details shown" state | COTRTUPC:1447-1512, 984-997 |
| FR-U03 | Enter a type that does not exist | `No record found for this key in database` with the info line `Press F05 to add. F12 to cancel` | COTRTUPC:1495-1497, 1219-1220 |
| FR-U04 | Press ENTER with an empty type | `Tran Type code must be supplied.` — `1210-EDIT-TRANTYPE` runs first and turns the return message on, so the `No input received` literal of `1200-EDIT-MAP-INPUTS` is only reachable while the message is still off | COTRTUPC:716-726, 820-843, 907-974 |
| FR-U05 | Enter a non-numeric type | `Tran Type code must be numeric.` | COTRTUPC:907-974 |
| FR-U06 | Enter `00` as the type | `Tran Type code must not be zero.` | COTRTUPC:907-974 |
| FR-U07 | Enter a single-digit type such as `7` | It is normalised to `07` and looked up as `07` | COTRTUPC:834-842 |
| FR-U08 | Change the description of a shown record and press ENTER | `Changes validated.Press F5 to save` | COTRTUPC:1023-1030, 1237-1238 |
| FR-U09 | Press F5 to confirm | The row is updated, `Changes committed to database` is shown and the new value is redisplayed | COTRTUPC:514-520, 1531-1592, 1240-1241 |
| FR-U10 | Press ENTER without changing anything | `No change detected with respect to values fetched.` | COTRTUPC:783-816 |
| FR-U11 | Submit an empty description | `Transaction Desc must be supplied.` | COTRTUPC:849-903 |
| FR-U12 | Submit a description with punctuation/symbols | `Transaction Desc can have numbers or alphabets only.` | COTRTUPC:849-903 |
| FR-U13 | Press F5 on a not-found key | The screen switches to add mode with `Enter new transaction type details.` | COTRTUPC:503-508, 1233-1234 |
| FR-U14 | Enter a description in add mode and press ENTER, then F5 | The new row is inserted and `Changes committed to database` is shown | COTRTUPC:1531-1621 |
| FR-U15 | Press F4 on a shown record | `Delete this record ? Press F4 to confirm`; nothing is deleted yet | COTRTUPC:493-498, 1225-1226 |
| FR-U16 | Press F4 again | The row is deleted and `Delete successful.` is shown | COTRTUPC:482-489, 1624-1664, 1231-1232 |
| FR-U17 | Delete a type that still has categories | `Please delete associated child records first:SQLCODE :…` and nothing is deleted | COTRTUPC:1643-1650 |
| FR-U18 | Press F12 while confirming a delete | `Delete was cancelled` and the record is redisplayed | COTRTUPC:999-1008 |
| FR-U19 | Press F12 while changes are pending | `Update was cancelled` and the fetched values are redisplayed | COTRTUPC:999-1008, 1041-1042 |
| FR-U20 | Press a key that is not valid in the current state | `Invalid key pressed` and the screen is redisplayed unchanged | COTRTUPC:577-608 |
| FR-U21 | Press F3 | Return to the caller (`COTRTLIC` if that is where I came from, otherwise `COADM01C`) | COTRTUPC:429-460 |
| FR-U22 | (quirk) Have a record deleted by someone else while my update is pending, then press F5 | The update misses (`SQLCODE +100`) and the program **inserts** the row instead of failing | COTRTUPC:1555-1560 |
| FR-U23 | Hit a row lock | `Could not lock record for update` | COTRTUPC:1566-1573 |
| FR-U24 | Type `*` or spaces in a field | It is treated as empty input | COTRTUPC:652-685 |

## 3. MNTTRDB2 / `COBTUPDT` — batch maintenance

| ID | Requirement | Expected result | Source |
|---|---|---|---|
| FR-B01 | The job reads a fixed 53-byte record file: col 1 operation, cols 2-3 type, cols 4-53 description | Every record is processed in file order | COBTUPDT:71-107, MNTTRDB2.jcl |
| FR-B02 | Operation `A` | Row inserted; log `ADDING RECORD` then `RECORD INSERTED SUCCESSFULLY` | COBTUPDT:111-113, 132-164 |
| FR-B03 | Operation `U` | Description updated; log `UPDATING RECORD` then `RECORD UPDATED SUCCESSFULLY` | COBTUPDT:114-116, 166-195 |
| FR-B04 | Operation `D` | Row deleted; log `DELETING RECORD` then `RECORD DELETED SUCCESSFULLY` | COBTUPDT:117-119, 196-226 |
| FR-B05 | Operation `*` | Record skipped with `IGNORING COMMENTED LINE` | COBTUPDT:120-121 |
| FR-B06 | Any other operation | `ERROR: TYPE NOT VALID` and the abend routine runs | COBTUPDT:122-129 |
| FR-B07 | `U` or `D` for a type that does not exist | `No records found.` and the abend routine runs | COBTUPDT:178-186, 208-217 |
| FR-B08 | A SQL failure on any operation | `Error accessing: TRANSACTION_TYPE table. SQLCODE:<code>` and the abend routine runs | COBTUPDT:147-158, 187-193, 218-224 |
| FR-B09 | (quirk) The abend routine does not stop the run | Processing continues with the next record; the job/step ends with return code 4 | COBTUPDT:230-233 |
| FR-B10 | A clean run | The job completes with return code 0 | COBTUPDT:82-107 |

## 4. TRANEXTR — extract job

| ID | Requirement | Expected result | Source |
|---|---|---|---|
| FR-X01 | The job backs up the existing transaction-type and transaction-category extract files before overwriting them | Previous contents are preserved as `.BKUP` copies | TRANEXTR.jcl STEP10/STEP20 |
| FR-X02 | The job deletes the previous output files before the unload | The unload writes a fresh file | TRANEXTR.jcl STEP30 |
| FR-X03 | Transaction types are unloaded as 60-byte records: 2-char type + 50-char description + 8 zeroes, ordered by type | Byte-identical layout to the legacy DSNTIAUL output | TRANEXTR.jcl STEP40 |
| FR-X04 | Transaction categories are unloaded as 60-byte records: 2-char type + 4-char category + 50-char data + 4 zeroes, ordered by type then category | Byte-identical layout to the legacy DSNTIAUL output | TRANEXTR.jcl STEP50 |

## 5. CREADB21 — database create / load job

| ID | Requirement | Expected result | Source |
|---|---|---|---|
| FR-C01 | The transaction-type and transaction-category tables exist in the target with the legacy column definitions and the category→type foreign key | Provided by the foundation Flyway schema (`V1__baseline_carddemo_estate.sql`); the DDL step is not re-implemented | CREADB21.jcl `CRCRDDB`, `ddl/TRNTYPE.ddl`, `ddl/TRNTYCAT.ddl` |
| FR-C02 | The job loads the 7 legacy transaction types (`01 PURCHASE` … `07 ADJUSTMENT`, including the source's `REVERAL` spelling for `06`) | Rows inserted exactly as in the control member | `ctl/DB2LTTYP.ctl` |
| FR-C03 | The job loads the 18 legacy transaction-type categories | Rows inserted exactly as in the control member | `ctl/DB2LTCAT.ctl` |
| FR-C04 | (quirk) The load is plain `INSERT`, not `LOAD REPLACE` — running it against already-populated tables fails on the duplicate primary key | The step fails; nothing is silently overwritten | `ctl/DB2LTTYP.ctl`, `ctl/DB2LTCAT.ctl` |
| FR-C05 | The `FREEPLN` step frees the previous DB2 plan/package and is expected to end RC 8 on a new database | No target equivalent (no DB2 plans in the target); the step is recorded as not-applicable | CREADB21.jcl `FREEPLN` |

## 6. Traceability matrix

All tests live under `migration/carddemo/backend/src/test/java/com/carddemo/trantype/` and run on the
default H2 profile (`mvn -B test`).

| FRs | Test | Test method(s) |
|---|---|---|
| FR-L01, L07 | `TranTypeListServiceTest` | `firstEntryShowsTheFirstSevenTypesAndTheActionPrompt`, `anEmptyTableReportsThatNoRecordsWereFound` |
| FR-L02, L04, L05, L06 | `TranTypeListServiceTest` | `aTypeFilterNarrowsTheBrowseToOneRow`, `aDescriptionFilterMatchesOnAContainedString`, `aTypeFilterThatMatchesNothingIsRejected` |
| FR-L03, L15, L16 | `TranTypeValidatorTest`, `TranTypeListServiceTest` | `requiresTheDescription`, `allowsOnlyLettersDigitsAndSpacesInTheDescription`, `appliesTheCobolNumericTestCharacterByCharacter`, `aNonNumericTypeFilterIsRejected`, `selectingUWithAnInvalidDescriptionIsRefused` |
| FR-L08, L09, L10, L11 | `TranTypeListServiceTest` | `pageDownShowsTheRemainingTypesAndPageUpComesBack`, `pageDownPastTheEndSaysSoAndThenRefusesToMove`, `pageUpOnTheFirstPageSaysThereAreNoPreviousPages` |
| FR-L12, L13, L14 | `TranTypeListServiceTest` | `selectingUWithAChangePromptsForF10AndTheSaveUpdatesTheRow`, `selectingUWithoutChangingTheDescriptionReportsNoChange` |
| FR-L17, L18, L19 | `TranTypeListServiceTest` | `selectingDPromptsForF10AndTheConfirmationDeletesTheRow`, `deletingATypeThatStillHasCategoriesIsRefused` |
| FR-L20, L21 | `TranTypeListServiceTest` | `twoSelectionsAreRefused`, `anActionCodeOtherThanUpperCaseUOrDIsRefused` |
| FR-L25, L26, L27 | `TranTypeListServiceTest` | `f3ReturnsToTheAdminMenuAndF2GoesToTheUpdateScreen`, `anUnmappedKeyIsTreatedAsEnter` |
| FR-L24 | `TranTypeListServiceTest`, `TranTypeLockFailureTest` | `updatingARowSomeoneElseDeletedReportsIt`, `aLockedRowLeavesTheListUpdateRequestedWithTheDeadlockMessage` |
| FR-U23 | `TranTypeLockFailureTest` | `aLockedRowLeavesTheMaintenanceScreenInTheLockErrorState` |
| FR-L01 over HTTP, admin gate | `TranTypeControllerTest` | `listReturnsTheFirstPageOfTheMap`, `aNonAdministratorCannotReachTheMaintenanceScreens`, `anAnonymousCallerCannotReachTheMaintenanceScreens` |
| FR-U01, U02, U03 | `TranTypeUpdateServiceTest`, `TranTypeControllerTest` | `firstEntryAsksForTheKeyOnAnEmptyScreen`, `lookingUpAnExistingTypeShowsItsDetails`, `aKeyThatIsNotInTheTableOffersTheAddPath`, `updateAsksForTheKeyOnFirstEntry` |
| FR-U04, U05, U06, U07, U24 | `TranTypeValidatorTest`, `TranTypeUpdateServiceTest` | `requiresTheTransactionTypeCode`, `rejectsANonNumericTransactionTypeCode`, `rejectsAZeroTransactionTypeCode`, `acceptsAValidTransactionTypeCode`, `normalisesTheTypeCodeThroughATwoDigitNumericField`, `treatsStarAndSpacesAsNotSupplied`, `aMissingKeyIsRefusedAndComesBackAsAStar`, `aNonNumericKeyIsRefused`, `aZeroKeyIsRefused`, `aSingleDigitKeyIsPaddedToTwoDigits` |
| FR-U08, U09, U10, U11, U12 | `TranTypeUpdateServiceTest` | `aChangedDescriptionIsValidatedAndThenCommittedByF5`, `resendingTheSameDetailsReportsNoChange`, `anInvalidDescriptionIsRefused` |
| FR-U13, U14, U22 | `TranTypeUpdateServiceTest` | `f5OnAMissingKeyOpensTheAddScreenAndSavesTheNewRecord` (the save path is the legacy UPDATE-then-INSERT) |
| FR-U15, U16, U17 | `TranTypeUpdateServiceTest` | `f4TwiceDeletesARecordWithNoCategories`, `deletingATypeThatStillHasCategoriesIsRefused` |
| FR-U18, U19 | `TranTypeUpdateServiceTest` | `f12CancelsAPendingDelete`, `f12BacksOutAValidatedChange` |
| FR-L22, L23 | `TranTypeListServiceTest` | `twoSelectionsAreRefused`, `selectingUWithAChangePromptsForF10AndTheSaveUpdatesTheRow` (the F10 leg only acts on the re-confirmed selection) |
| FR-U20, U21 | `TranTypeUpdateServiceTest` | `aKeyTheCurrentStateDoesNotAllowIsRefused`, `f3LeavesForTheAdminMenu` |
| FR-B01, B02, B03, B04, B05, B10 | `TranTypeMaintenanceJobTest` | `appliesAddUpdateAndDeleteAndIgnoresCommentedLines`, `aShortRecordIsPaddedToTheFullFixedLength` |
| FR-B06, B07, B08, B09 | `TranTypeMaintenanceJobTest` | `anUnknownOperationEndsTheStepWithReturnCode4ButKeepsProcessing`, `aDuplicateInsertAMissingUpdateAndAConstrainedDeleteAllRaiseReturnCode4` |
| FR-X01, X02, X03, X04 | `TranTypeExtractJobTest` | `unloadsBothTablesAsSixtyByteRecordsAfterBackingUpTheLastRun`, `eachRunAddsTheNextGdgGeneration`, `aRunWithNoPreviousExtractEndsOnAJclError` |
| FR-C01, C02, C03, C05 | `CreateDb2TablesJobTest` | `loadsTheControlMembersThroughOneStepPerExecPgm` |
| FR-C04 | `CreateDb2TablesJobTest` | `aSecondRunAgainstPopulatedTablesFailsOnTheUniqueIndex` |

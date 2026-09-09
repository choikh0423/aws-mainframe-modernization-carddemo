# S-03 CardManagement — Functional Requirements

**Stream:** S-03 CardManagement — CCLI (`COCRDLIC`), CCDL (`COCRDSLC`), CCUP (`COCRDUPC`)
**Source of truth:** `app/cbl/COCRDLIC.cbl`, `app/cbl/COCRDSLC.cbl`, `app/cbl/COCRDUPC.cbl`,
`app/bms/COCRDLI.bms`, `COCRDSL.bms`, `COCRDUP.bms`
**Role:** business sign-off oracle for the stream. Every FR is testable, cited to source, and
covered by at least one automated test (§E).

Message strings below are **verbatim** legacy literals, including their spacing.

---

## A. CCLI — List Credit Cards

| ID | Trigger | Expected result | Source |
|----|---------|-----------------|--------|
| FR-L1 | Open the card list | Up to **7** rows (Account Number 11 digits zero-padded, Card Number 16 digits, Active `Y`/`N`), page number 1, info line `TYPE S FOR DETAIL, U TO UPDATE ANY RECORD` | COCRDLIC:177-178, 1123-1196, 895-922 |
| FR-L2 | Leave both filters blank | No filtering: the browse returns cards in card-number order from the start of the file | COCRDLIC:1003-1013, 1039-1048, 1382-1405 |
| FR-L3 | Enter a valid 11-digit account filter | Only cards of that account are listed | COCRDLIC:1017-1029, 1385-1394 |
| FR-L4 | Enter a valid 16-digit card filter | Only that card is listed | COCRDLIC:1052-1066, 1396-1405 |
| FR-L5 | Enter a non-numeric account filter | `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`, no rows are listed, row selection is protected | COCRDLIC:1017-1025, 431-435 |
| FR-L6 | Enter a non-numeric card filter | `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER`; if the account filter is also invalid the account message wins | COCRDLIC:1052-1060 |
| FR-L7 | Press PF8 with a further page available | Next 7 rows, page number incremented | COCRDLIC:486-497 |
| FR-L8 | Press PF8 when the browse hits end-of-file while filling the page | Page is shown and `NO MORE RECORDS TO SHOW` appears | COCRDLIC:1215-1221, 1233-1240 |
| FR-L9 | Press PF8 again once the last page has already been shown | `NO MORE PAGES TO DISPLAY`, the page does not move | COCRDLIC:905-916 |
| FR-L10 | Press PF7 on a page after the first | Previous 7 rows, page number decremented | COCRDLIC:501-513, 1264-1372 |
| FR-L11 | Press PF7 on page 1 | Page 1 is redisplayed with `NO PREVIOUS PAGES TO DISPLAY` | COCRDLIC:444-454, 901-904 |
| FR-L12 | Apply a filter that matches nothing | An empty list with `NO MORE RECORDS TO SHOW` on the error line and the usual info line. `NO RECORDS FOUND FOR THIS SEARCH CONDITION.` is set by the read but never reaches the screen (quirk Q-11) | COCRDLIC:1241-1245, 668-670, 895-930 |
| FR-L13 | Type `S` beside one row and press ENTER | Transfer to CCDL (`COCRDSLC`, mapset `COCRDSL`, map `CCRDSLA`) carrying that row's account id and card number | COCRDLIC:517-541 |
| FR-L14 | Type `U` beside one row and press ENTER | Transfer to CCUP (`COCRDUPC`, mapset `COCRDUP`, map `CCRDUPA`) carrying that row's account id and card number | COCRDLIC:545-569 |
| FR-L15 | Flag two or more rows with `S`/`U` | `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE`, no transfer | COCRDLIC:1079-1095 |
| FR-L16 | Type any flag other than `S`, `U` or blank | `INVALID ACTION CODE`, no transfer | COCRDLIC:1106-1113 |
| FR-L17 | Type a selection flag on the same submit as an invalid filter | The filter message is shown and the selection is not evaluated (quirk Q-5) | COCRDLIC:1075-1077 |
| FR-L18 | Press PF3 | Return to the main menu `COMEN01C` with `PF03 PRESSED.EXITING` | COCRDLIC:384-406, 119-120 |

## B. CCDL — View Credit Card Detail

| ID | Trigger | Expected result | Source |
|----|---------|-----------------|--------|
| FR-D1 | Enter a valid account number and card number for an existing card | Name on card, Card Active `Y`/`N`, expiry month and expiry year are displayed with info line `   Displaying requested details` | COCRDSLC:474-485, 736-754, 129-130 |
| FR-D2 | Open the screen with no keys yet | Info line `Please enter Account and Card Number` | COCRDSLC:459-460, 131-132 |
| FR-D3 | Submit a blank, all-zero or `*` account number (card supplied) | `Account number not provided` | COCRDSLC:615-620, 651-661 |
| FR-D4 | Submit a blank, all-zero or `*` card number (account supplied) | `Card number not provided` | COCRDSLC:622-627, 691-702 |
| FR-D5 | Submit both keys blank | `No input received` | COCRDSLC:637-640 |
| FR-D6 | Submit a non-numeric account number | `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` | COCRDSLC:665-674 |
| FR-D7 | Submit a non-numeric card number | `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` | COCRDSLC:706-715 |
| FR-D8 | Submit keys for a card that does not exist | `Did not find cards for this search condition` | COCRDSLC:755-761 |
| FR-D9 | Submit a card number with an account number that does not own it | The card is still displayed — the read is by card number only (quirk Q-3) | COCRDSLC:736-754, 779-806 |
| FR-D10 | Arrive from CCLI via `S` | The selected account/card are pre-filled and the details are displayed without a further submit | COCRDSLC:339-348 |
| FR-D11 | Press PF3 | Return to the calling program when known (CCLI), otherwise to `COMEN01C` | COCRDSLC:305-334 |

## C. CCUP — Update Credit Card Details

| ID | Trigger | Expected result | Source |
|----|---------|-----------------|--------|
| FR-U1 | Arrive from CCLI via `U`, or submit valid keys | The card's name, status, expiry month and expiry year are shown for editing with `Details of selected card shown above` | COCRDUPC:157-161, 1343-1369 |
| FR-U2 | Open the screen with no keys yet | `Please enter Account and Card Number` | COCRDUPC:159-161 |
| FR-U3 | Submit blank/zero/`*` account (card supplied) | `Account number not provided` | COCRDUPC:725-736 |
| FR-U4 | Submit blank/zero/`*` card (account supplied) | `Card number not provided` | COCRDUPC:768-780 |
| FR-U5 | Submit both keys blank | `No input received` | COCRDUPC:656-659 |
| FR-U6 | Submit a non-numeric account / card | `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` / `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` | COCRDUPC:740-750, 784-794 |
| FR-U7 | Submit keys for a card that does not exist | `Did not find cards for this search condition` | COCRDUPC:1395-1401 |
| FR-U8 | Submit the details unchanged (case-insensitively) | `No change detected with respect to values fetched.` and nothing is written (quirk Q-7) | COCRDUPC:680-693, 179-180 |
| FR-U9 | Clear the name | `Card name not provided` | COCRDUPC:811-819 |
| FR-U10 | Enter a name containing anything other than letters and spaces | `Card name can only contain alphabets and spaces` | COCRDUPC:822-837 |
| FR-U11 | Enter a status other than `Y` or `N` (including blank or lower case) | `Card Active Status must be Y or N` | COCRDUPC:845-872, 91 |
| FR-U12 | Enter an expiry month that is blank, non-numeric or outside 1–12 | `Card expiry month must be between 1 and 12` | COCRDUPC:877-907, 92-95 |
| FR-U13 | Enter an expiry year that is blank, non-numeric or outside 1950–2099 | `Invalid card expiry year` | COCRDUPC:913-943, 96-99 |
| FR-U14 | Submit valid changes without confirming | `Changes validated.Press F5 to save` and the record is **not** written | COCRDUPC:710-714, 163-166 |
| FR-U15 | Confirm the validated changes (PF5) | The card row is rewritten (new name, status, `YYYY-MM-DD` from new year + new month + the **unchanged** day; account id and CVV preserved) and `Changes committed to database` is shown | COCRDUPC:1461-1492, 167-168 |
| FR-U16 | Confirm changes after the record was changed by someone else | `Record changed by some one else. Please review`, nothing is written, the screen is refreshed with the values now on file | COCRDUPC:1453-1457, 1498-1519 |
| FR-U17 | Press PF12 (cancel) | The card is re-read and the unedited details are shown again | COCRDUPC:958-966 |
| FR-U18 | Press PF3 | Return to the calling program when known (CCLI), otherwise to `COMEN01C` | COCRDUPC:367-558 |
| FR-U19 | Confirm changes for a card that can no longer be read for update | `Could not lock record for update`, nothing is written | COCRDUPC:1427-1451, 205-206 |

## D. Cross-cutting requirements

| ID | Requirement | Source |
|----|-------------|--------|
| FR-X1 | Account numbers render as 11 digits zero-padded, card numbers as the stored 16 characters | CVACT02Y, COCRDLI.bms |
| FR-X2 | Expiry date is stored as `YYYY-MM-DD`; the screens show month (2) and year (4) only | CVACT02Y, COCRDUPC:1361-1366 |
| FR-X3 | Screen labels, field order, titles and PF-key captions are reproduced verbatim from the BMS maps | app/bms/COCRDLI.bms, COCRDSL.bms, COCRDUP.bms |
| FR-X4 | `CDV1` / `COCRDSEC` is **not** implemented: the CSD defines it but no source exists (R-1 / B-11) | docs/migration/CardDemo_inventory.md, .migration/04_boundary_register.md |

## E. Traceability (requirement → test)

All tests live under `migration/carddemo/backend/src/test/java/com/carddemo/card/` and are named
after the requirement they cover (`frL7_…` covers FR-L7).

| FR | Test |
|----|------|
| FR-L1 | `CardListServiceTest#frL1_enterBrowsesSevenRowsFromTheTopOfCarddat`, `CardSelectionServiceTest#frL1_enterWithNothingSelectedJustRedisplaysTheList`, `CardManagementControllerTest#ccli_firstPageReturnsSevenRowsAndThePageState` |
| FR-L2 | `CardSearchKeyValidatorTest#frL2_blankOrZeroAccountFilterMeansNoFilter` |
| FR-L3 | `CardListServiceTest#frL3_theAccountFilterIsAppliedToEachRecordRead`, `CardSearchKeyValidatorTest#frL3_frL5_accountFilterMustBeExactly11Digits` |
| FR-L4 | `CardListServiceTest#frL4_theCardFilterSelectsTheSingleMatchingRecord`, `CardSearchKeyValidatorTest#frL4_frL6_cardFilterMustBeExactly16Digits` |
| FR-L5 | `CardListServiceTest#frL5_aBadFilterSuppressesTheBrowseEntirely`, `CardManagementControllerTest#ccli_aMalformedAccountFilterIsRejectedWithTheLegacyText` |
| FR-L6 | `CardSearchKeyValidatorTest#frL4_frL6_cardFilterMustBeExactly16Digits` |
| FR-L7 | `CardListServiceTest#frL7_pf8ShowsTheNextSevenAndAdvancesThePageNumber` |
| FR-L8, FR-L9 | `CardListServiceTest#frL8_frL9_theLastPageEndsTheBrowseAndAFurtherPf8SaysThereAreNoMorePages` |
| FR-L10 | `CardListServiceTest#frL10_pf7ReturnsToThePreviousSeven` |
| FR-L11 | `CardListServiceTest#frL11_pf7OnPageOneSaysThereAreNoPreviousPages` |
| FR-L12 | `CardListServiceTest#frL12_theFiltersAreIndependentSoAMismatchedPairMatchesNothing` |
| FR-L13 | `CardSelectionServiceTest#frL13_sRoutesToTheDetailProgramWithTheRowKeys`, `#frL13_lowerCaseFlagsAreAccepted`, `CardManagementControllerTest#ccli_selectionReturnsTheXctlTargetAnd204WhenNothingIsSelected` |
| FR-L14 | `CardSelectionServiceTest#frL14_uRoutesToTheUpdateProgram` |
| FR-L15 | `CardSelectionServiceTest#frL15_twoSelectionsAreRejected`, `#frL15_theMultiSelectMessageWinsOverALaterInvalidCode` |
| FR-L16 | `CardSelectionServiceTest#frL16_anyOtherFlagIsAnInvalidActionCode` |
| FR-L17 | `CardListServiceTest#frL5_aBadFilterSuppressesTheBrowseEntirely` — the filter edit rejects the request before any row is read or any flag evaluated (boundary decision B-4) |
| FR-L18 | Frontend only: `CardListPage` PF3 handler. The repository has no frontend test harness (the reference stream has none either), so this is covered by review, not by an automated test |
| FR-D1 | `CardDetailServiceTest#frD1_aValidPairDisplaysTheCard`, `CardSearchKeyValidatorTest#frD1_bothKeysWellFormedPasses`, `CardManagementControllerTest#ccdl_readsTheCardAndReportsAnUnknownOneAs404` |
| FR-D2 | Frontend only: the `Please enter Account and Card Number` literal in `CardDetailPage` (no frontend test harness) |
| FR-D3, FR-D4 | `CardSearchKeyValidatorTest#frD3_frD4_theMissingKeyIsReportedAccountFirst` |
| FR-D5 | `CardSearchKeyValidatorTest#frD5_frU5_bothKeysBlankGivesNoInputReceived`, `CardDetailServiceTest#frD5_theKeyEditsRunBeforeTheRead` |
| FR-D6, FR-D7 | `CardSearchKeyValidatorTest#frD6_frD7_malformedKeysUseTheFilterMessages` |
| FR-D8 | `CardDetailServiceTest#frD8_anUnknownCardGivesTheNotFoundMessage`, `CardManagementControllerTest#ccdl_readsTheCardAndReportsAnUnknownOneAs404` |
| FR-D9 | `CardDetailServiceTest#frD9_anAccountThatDoesNotOwnTheCardStillDisplaysIt` |
| FR-D10 | `CardManagementControllerTest#ccli_selectionReturnsTheXctlTargetAnd204WhenNothingIsSelected` (the target keys) plus `CardDetailPage`'s auto-load on those query parameters |
| FR-D11, FR-U18 | Frontend only: the PF3 handlers in `CardDetailPage` / `CardUpdatePage` (no frontend test harness) |
| FR-U1 | `CardUpdateServiceTest#frU1_frU17_loadPresentsTheStoredDetails`, `CardManagementControllerTest#ccup_loadThenValidateThenSave` |
| FR-U2 | Frontend only: the `Please enter Account and Card Number` literal in `CardUpdatePage` (no frontend test harness) |
| FR-U3…FR-U7 | `CardSearchKeyValidatorTest` (same edits as FR-D3…FR-D7), `CardUpdateServiceTest#frU19_aRecordThatDisappearedCannotBeLocked` for the missing card on the load path |
| FR-U8 | `CardUpdateServiceTest#frU8_resubmittingTheFetchedValuesIsNoChange` |
| FR-U9…FR-U13 | `CardUpdateValidatorTest#frU9_nameMustBeSupplied`, `#frU10_nameIsAlphabetsAndSpacesOnly`, `#frU11_statusIsUpperCaseYorN`, `#frU12_expiryMonthIs1To12`, `#frU13_expiryYearIs1950To2099`, `#frU9_frU13_editsRunInMapOrderSoTheFirstFailureIsReported`, `CardManagementControllerTest#ccup_anInvalidStatusIsRejectedWithTheLegacyText` |
| FR-U14 | `CardUpdateServiceTest#frU14_validEditsWaitForPf5`, `CardManagementControllerTest#ccup_loadThenValidateThenSave` |
| FR-U15 | `CardUpdateServiceTest#frU15_pf5RewritesTheRecordAndKeepsTheExpiryDayCvvAndAccount`, `CardManagementControllerTest#ccup_loadThenValidateThenSave` |
| FR-U16 | `CardUpdateServiceTest#frU16_aRecordChangedSinceTheFetchIsRejectedAndRefreshed`, `CardManagementControllerTest#ccup_aRecordChangedMeanwhileIsRejectedWithTheRefreshedValues` |
| FR-U17 | `CardUpdateServiceTest#frU1_frU17_loadPresentsTheStoredDetails` — PF12 re-issues the same load |
| FR-U19 | `CardUpdateServiceTest#frU19_aRecordThatDisappearedCannotBeLocked` |
| FR-X1 | `CardListServiceTest#frX1_theAccountNumberIsShownZeroPaddedTo11Digits`, `CardManagementControllerTest#ccli_firstPageReturnsSevenRowsAndThePageState` |
| FR-X2 | `CardDetailServiceTest#frD1_aValidPairDisplaysTheCard`, `CardUpdateServiceTest#frU15_pf5RewritesTheRecordAndKeepsTheExpiryDayCvvAndAccount` |
| FR-X3 | React screens `CardListPage`, `CardDetailPage`, `CardUpdatePage` (labels/captions copied from the BMS maps) |
| FR-X4 | Documented deferral only — nothing implemented |

# S-09 PendingAuthorizations — Migration Plan

How the stream was implemented in `migration/carddemo`, the boundary decisions taken, the parity
between the legacy programs and the migrated code, and the requirement→test traceability.

Read with `PendingAuthorizations_analysis.md` (source analysis, with line cites),
`PendingAuthorizations_functional_requirement.md` (the FR-PA-* oracle) and the per-program FRs under
`programs/`.

## 1. Source → implementation map

### Online

| Legacy | Migrated to | Notes |
| --- | --- | --- |
| `COPAUS0C.cbl` (CPVS) + `COPAU00.bms` | `com.carddemo.pendingauth.service.PendingAuthListService`, `dto.PendingAuthListResponse` / `PendingAuthListRow` / `PendingAuthSelectionRequest` / `PendingAuthSelectionResponse`, `pages/pendingauth/PendingAuthListPage.js` | Validation, header build, five-row page, F7/F8, selection evaluation |
| `COPAUS1C.cbl` (CPVD) + `COPAU01.bms` | `service.PendingAuthDetailService`, `dto.PendingAuthDetailResponse`, `pages/pendingauth/PendingAuthDetailPage.js` | Field-for-field detail, decline-reason table, F8 chain walk, F5 fraud toggle |
| `COPAUS2C.cbl` (`EXEC CICS LINK`) | `service.AuthFraudService` | Called in-process from `PendingAuthDetailService.markFraud`, same transaction |
| `COPAUA0C.cbl` (CP00) | `service.AuthorizationDriverService`, `dto.AuthorizationRequest`, `jms.AuthorizationRequestListener`, `jms.PendingAuthQueues`, `service.AuthorizationErrorLog` | MQGET/MQPUT1 → `@JmsListener` + `JmsTemplate` |
| `EXEC CICS LINK`/`XCTL` targets | `PendingAuthSelectionResponse.nextProgram`/`nextTranId` and the route registry entries for `COPAUS0C`/`COPAUS1C` | The screen flow is data, not a redirect baked into the service |
| `WS-*` message literals | `service.PendingAuthMessages` | Every string verbatim from the COBOL literal |
| `PIC` edit masks | `util.PendingAuthFormat` | `-zzzzzzz9.99`, `-zzzz9.99`, `-zzzzzzzzz9.99`, `9(03)`, date/time/expiry editing |
| `PAUT9CTS` inverted key | `util.PendingAuthKey` | 9s-complement pair, 14-digit external form |

HTTP surface (`controller.PendingAuthController`, base `/api/pending-authorizations`):

| Endpoint | Legacy equivalent |
| --- | --- |
| `GET /` (`acctId`, `dir`, `startKey`, `pageNum`) | CPVS ENTER / F7 / F8 |
| `POST /selection` | CPVS PROCESS-ENTER-KEY selection evaluation (the `XCTL` decision) |
| `GET /detail` | CPVD initial display |
| `GET /detail/next` | CPVD F8 |
| `POST /detail/fraud` | CPVD F5 → `COPAUS2C` |

### Batch / utilities

| Legacy | Migrated to | Job name |
| --- | --- | --- |
| `CBPAUP0C.cbl` + `CBPAUP0J.jcl` | `com.carddemo.batch.authpurge.PendingAuthPurgeJobConfiguration`, `PendingAuthPurgeService`, `PendingAuthSummaryReader` | `CBPAUP0J` |
| `PAUDBUNL.CBL` + `UNLDPADB.JCL` | `PendingAuthUnloadJobConfiguration` (+ `PendingAuthUnloadSupport`, `PendingAuthSegmentFormat`) | `UNLDPADB` (`outfil1`, `outfil2`) |
| `DBUNLDGS.CBL` + `UNLDGSAM.JCL` | `PendingAuthGsamUnloadJobConfiguration` | `UNLDGSAM` (`pasfilop`, `padfilop`) |
| `PAUDBLOD.CBL` + `LOADPADB.JCL` | `PendingAuthLoadJobConfiguration` | `LOADPADB` (roots step then children step) |

### Data

| Legacy | Migrated to |
| --- | --- |
| IMS `DBPAUTP0` root `PAUTSUM0` (`CIPAUSMY`) | `pending_auth_summary` / `PendingAuthSummary` (foundation schema, unchanged) |
| IMS `DBPAUTP0` child `PAUTDTL1` (`CIPAUDTY`) | `pending_auth_detail` / `PendingAuthDetail` with `PendingAuthDetailId(acctId, date9c, time9c)` |
| Secondary index `DBPAUTX0` | The child primary key already orders by `(acctId, date9c, time9c)`; browsing uses that order |
| DB2 `AUTHFRDS` (`ddl/AUTHFRDS.ddl`, `dcl/AUTHFRDS.dcl`) | `auth_fraud` / `AuthFraud` with `AuthFraudId(cardNum, authTs)` |
| `CBLTDLI` GU/GN/GNP/ISRT/REPL/DLET | Spring Data derived queries in `repository.PendingAuthSummaryBrowseRepository` / `PendingAuthDetailBrowseRepository`; DL/I statuses become `Optional`/typed results (BD-4) |
| MQ `AWS.M2.CARDDEMO.PAUTH.REQUEST` / `.REPLY` | Artemis queues of the same names, `jms.PendingAuthQueues` |

No shared entity, no existing migration and no other stream's package was modified. The only shared
frontend file touched is `src/routes/registry.js`: two imports and the two S-09 `element:` values.
The only new resource is the S-09 demo seed
`backend/src/main/resources/db/seed/R__seed_pendingauth_demo_data.sql`; the stream needed **no schema
change**, so no Flyway version in the reserved band was consumed.

## 2. Boundary decisions

| Id | Decision |
| --- | --- |
| BD-1 | **CPVS paging is stateless.** `CDEMO-CPVS-PAGE-NUM`, `-PAUKEY-PREV-PG`, `-PAUKEY-LAST` and `-NEXT-PAGE-FLG` lived in the COMMAREA; they are returned to the client as `pageNum`/`firstKey`/`lastKey`/`nextPage` and passed back on the next request. Behaviour (including the sixth read that lights the next-page flag) is unchanged. |
| BD-2 | **The authorization key is one 14-digit string** (`99999-yyddd` zero-padded to 5, then `999999999-hhmmssSSS` zero-padded to 9) instead of two binary fields, so it survives a URL and a JSON body. Ordering is identical to segment order. |
| BD-3 | **`AUTHFRDS` keeps its DB2 key** `(card number, auth timestamp)`; the timestamp is rebuilt from `PA-AUTH-ORIG-DATE` and the de-inverted `PA-AUTH-TIME-9C` exactly as `COPAUS2C` builds it. The `-803` duplicate-key branch becomes "find by id, else insert", returning the same `ADD SUCCESS` / `UPDT SUCCESS` text. |
| BD-4 | **DL/I status codes become typed results**: `GE`/`GB` are an empty `Optional` (end of chain / end of database), `II` on load is "already there" and is tolerated, and any other non-blank status becomes an exception that fails the step — the migrated equivalent of `9999-ABEND`. |
| BD-5 | **`CCPAUERY` error records become structured log events** (`AuthorizationErrorLog`) with the same severity/subsystem/code/message/event-key fields and the verbatim message literals; there is no error queue in the target (D-7 covers MQ only). |
| BD-6 | **The unload/load record layout is ASCII fixed width**, `CIPAUSMY` and `CIPAUDTY` field by field, in copybook order and copybook widths (root 113, child 212 bytes). It is defined by `PendingAuthSegmentFormat` and is reversible: what `UNLDPADB`/`UNLDGSAM` write, `LOADPADB` reads back. |
| BD-7 | **GSAM and BSAM unload share the step logic** (`PendingAuthUnloadSupport`); the two jobs differ only in the parameter names of their two output files, matching `UNLDPADB.JCL` and `UNLDGSAM.JCL`. |
| BD-8 | **The demo hierarchy is derived, not converted.** `app/data/ASCII/**` ships no PAUT unload — the estate only has the EBCDIC IMS image — so the seed rows were built field by field from the copybooks and documented in the seed file. |

## 3. Legacy vs migrated parity — main paths

| Path | Legacy behaviour | Migrated behaviour |
| --- | --- | --- |
| CPVS, blank account | `Please enter Acct Id...`, nothing read | Same message, HTTP 200, empty rows |
| CPVS, non-numeric account | `Acct Id must be Numeric ...` | Same |
| CPVS, valid account with summary | Header from account/customer/summary; five rows newest first; sixth read sets next-page | Same, `nextPage=true` with seven children |
| CPVS, account without summary | Six summary fields zero, list empty, no message | Same |
| CPVS amount column | `PA-APPROVED-AMT` — declines show `0.00` (Q-1) | Same, preserved |
| CPVS F8 past the end | `You are already at the bottom of the page...`, page stays | Same |
| CPVS F7 on page 1 | `You are already at the top of the page...` | Same |
| CPVS selection | First non-blank of the five wins (Q-10); `S`/`s` → CPVD; anything else `Invalid selection. Valid value is S` | Same |
| CPVD display | 21 fields of `COPAU01`; `Auth Code` shows the processing code (Q-3); amount is the approved amount (Q-1) | Same, preserved |
| CPVD unknown reason code | `9999-ERROR` | Same |
| CPVD, bad account or no selection | Blank detail area, no message (Q-2) | Same, `found=false`, `message=null` |
| CPVD F8 at end of chain | `Already at the last Authorization...`, current detail stays | Same |
| CPVD F5 | Toggles `F`/`R`, LINKs `COPAUS2C`, `AUTH MARKED FRAUD...` / `AUTH FRAUD REMOVED...` | Same, `AuthFraudService` in the same transaction |
| `COPAUS2C` insert then re-report | Insert, then `-803` → update of the existing row | Insert, then update by id |
| CP00 request | 18 `UNSTRING`ed fields, `NUMVAL` amount | Same parser, signed/spaced/unpadded amounts accepted |
| CP00 limit check | Summary limit − credit balance if the summary exists, else account limit − current balance | Same |
| CP00 decline | `05`/`4100` over limit, `05`/`3100` unresolved card, approved amount 0 | Same |
| CP00 approval | `00`/`0000`, approved amount = transaction amount, summary counters and balances updated, detail `P` | Same |
| CP00 declined summary amount | Adds the *detail work area's* amount — the previous message's (Q-6) | Same, preserved |
| CP00 reply | `card,tranid,authid,resp,reason,amount,` with the trailing comma (Q-9) | Same, sent to `JMSReplyTo` or the default reply queue |
| CP00 unresolved card | No summary and no detail written, decline still replied | Same |
| Purge expiry | `today_yyddd - (99999 - date9c) >= expiry days`, plain subtraction | Same, including across a year boundary |
| Purge counters | Approved → count/approved amount; other → declined count/transaction amount | Same |
| Purge root delete | Tests the approved count twice, ignoring the declined count (Q-7) | Same, preserved |
| Purge root counters | Updated in memory, never `REPL`ed before the delete decision (Q-8) | Same, preserved |
| Load | Roots then children; non-numeric parent key skipped; `II` tolerated | Same |
| Unload | Every root to file 1, its children to file 2, in key order | Same, GSAM job identical records |

Known deviations from the legacy *mechanics* (not behaviour) are exactly BD-1…BD-8.

## 4. Requirement → test traceability

Test roots: `backend/src/test/java/com/carddemo/pendingauth/**` and `.../batch/authpurge/**`.

| FR | Test |
| --- | --- |
| FR-PA-X01 | `PendingAuthFormatTest.frF1_amount12MatchesPicMinusZzzzzzz9Dot99` |
| FR-PA-X02 | `PendingAuthFormatTest.frF2_amount9MatchesPicMinusZzzz9Dot99`, `.frF4_countIsThreeZeroPaddedDigits` |
| FR-PA-X03 | `PendingAuthFormatTest.frF5_dateTimeAndExpiryEditing` |
| FR-PA-X04 | `PendingAuthKeyTest.frK1_externalKeyIsTheTwoComplementedFieldsZeroPadded`, `.frK2_keyOrderEqualsSegmentOrder`, `.frK3_onlyFourteenDigitKeysAreUsable`, `.frK4_newKeyComplementsTheCicsClock` |
| FR-PA-X05 | `PendingAuthFormatTest.zeroPaddedIdsMatchTheMapFields`, `PendingAuthListServiceTest.frS4_headerCarriesTheAccountCustomerAndSummaryFields` |
| FR-PA-S01 | `PendingAuthControllerTest.listWithoutAnAccountIdReturns200AndTheLegacyPrompt` |
| FR-PA-S02 | `PendingAuthListServiceTest.frS1_blankAccountIdIsRejectedWithTheLegacyMessage`, `PendingAuthControllerTest.listWithoutAnAccountIdReturns200AndTheLegacyPrompt` |
| FR-PA-S03 | `PendingAuthListServiceTest.frS2_nonNumericAccountIdIsRejectedWithTheLegacyMessage` |
| FR-PA-S04, S05 | `PendingAuthListServiceTest.frS4_headerCarriesTheAccountCustomerAndSummaryFields`, `PendingAuthControllerTest.listReturnsTheHeaderAndTheFirstFiveRows` |
| FR-PA-S06 | `PendingAuthListServiceTest.frS8_anAccountWithNoSummarySegmentShowsZeroesAndNoRows` |
| FR-PA-S07 | `PendingAuthListServiceTest.frS5_firstPageShowsFiveRowsInSegmentOrderAndFlagsTheSixthRead`, `PendingAuthControllerTest.listReturnsTheHeaderAndTheFirstFiveRows` |
| FR-PA-S08, S09 | `PendingAuthListServiceTest.frS5_firstPageShowsFiveRowsInSegmentOrderAndFlagsTheSixthRead` (row 3 is a decline: `D`, `0.00`) |
| FR-PA-S10 | `PendingAuthListServiceTest.frS6_pf8PagesForwardFromTheLastKeyAndTheLastPageHasNoNextFlag`, `PendingAuthControllerTest.listPagesForwardAndBackWithTheKeysTheScreenCarries` |
| FR-PA-S11 | `PendingAuthListServiceTest.frS7_pf7PagesBackToThePageStartingAtTheGivenKey`, `PendingAuthControllerTest.listPagesForwardAndBackWithTheKeysTheScreenCarries` |
| FR-PA-S12, S13, S14 | `PendingAuthListServiceTest.frS9_theFirstNonBlankSelectionWinsAndOnlySTransfersToCpvd`, `PendingAuthControllerTest.selectionTransfersToCpvdOnlyForS` |
| FR-PA-S15, S16 | Key handling is the React screens' `pfKeys` map (`PendingAuthListPage.js`); no backend behaviour — see §6 |
| FR-PA-S17 | `PendingAuthListServiceTest.frS3_anAccountAbsentFromTheXrefReportsTheCicsResponse` |
| FR-PA-D01 | `PendingAuthDetailServiceTest.frD1_theSelectedAuthorizationIsRenderedFieldForField`, `PendingAuthControllerTest.detailReturnsTheAuthorizationAndABlankAreaForAnUnknownKey` |
| FR-PA-D02, D03 | `PendingAuthDetailServiceTest.frD2_aDeclinedAuthorizationShowsItsReasonFromTheTable` |
| FR-PA-D04, D06 | `PendingAuthDetailServiceTest.frD1_theSelectedAuthorizationIsRenderedFieldForField` |
| FR-PA-D05 | `PendingAuthDetailServiceTest.frD5_pf5MarksFraudAndFrC1_thePairIsInsertedIntoAuthfrds`, `.frD6_asecondPf5RemovesTheFraudMarkAndFrC2_updatesTheExistingRow` |
| FR-PA-D07 | `PendingAuthDetailServiceTest.frD3_anInvalidAccountOrKeyLeavesTheDetailAreaBlankWithNoMessage`, `PendingAuthControllerTest.detailReturnsTheAuthorizationAndABlankAreaForAnUnknownKey` |
| FR-PA-D08, D09 | `PendingAuthDetailServiceTest.frD5_…`, `.frD6_…`, `PendingAuthControllerTest.fraudMarksThenRemovesTheFlagOnTheSelectedAuthorization` |
| FR-PA-D10, D11 | `PendingAuthDetailServiceTest.frC4_fraudOnAnUnknownAuthorizationLeavesTheScreenBlank`; the DB2/IMS failure texts themselves are BD-4/BD-5 — see §6 |
| FR-PA-D12 | `PendingAuthDetailServiceTest.frD4_pf8WalksToTheNextChildAndStopsAtTheEndOfTheChain`, `PendingAuthControllerTest.detailNextStopsAtTheLastAuthorizationOfTheAccount` |
| FR-PA-D13, D14 | React `pfKeys` — see §6 |
| FR-PA-F01, F03 | `PendingAuthDetailServiceTest.frD5_pf5MarksFraudAndFrC1_thePairIsInsertedIntoAuthfrds` |
| FR-PA-F02 | `PendingAuthDetailServiceTest.frC3_theAuthTimestampIsRebuiltFromTheOriginalDateAndTheDeInvertedTime` |
| FR-PA-F04, F06 | `PendingAuthDetailServiceTest.frD6_asecondPf5RemovesTheFraudMarkAndFrC2_updatesTheExistingRow` |
| FR-PA-F05 | BD-4/BD-5: no `SQLCODE`/`SQLSTATE` in the target — see §6 |
| FR-PA-A01 | `AuthorizationRequestTest.frA1_allEighteenFieldsAreUnstrungInCopybookOrder`, `.frA1_missingTrailingFieldsStayBlankAndSurplusFieldsAreIgnored` |
| FR-PA-A02, A06, A14 | `AuthorizationDriverServiceTest.frP1_anUnknownCardIsDeclined3100AndWritesNothing` |
| FR-PA-A03 | Same lookup chain as A02 — see §6 |
| FR-PA-A04, A05 | `AuthorizationDriverServiceTest.frP3_anAmountOverTheRemainingLimitIsDeclined4100` |
| FR-PA-A07, A08, A10, A11, A13 | `AuthorizationDriverServiceTest.frP2_anApprovalRepliesOoAndOpensTheSummaryAndDetailSegments` |
| FR-PA-A09 | `AuthorizationRequestListenerTest.frP10_aRequestIsAnsweredOnItsReplyToQueueWithTheCorrelationIdEchoed`, `.frP11_aRequestWithoutAReplyToGoesToTheModulesReplyQueue` |
| FR-PA-A12 | `AuthorizationDriverServiceTest.frP4_theDeclinedAmountCarriesForwardFromThePreviousMessage` |
| FR-PA-A15 | BD-5 — see §6 |
| FR-PA-A16 | Not applicable to a listener (BD-5/D-7) — see §6 |
| FR-PA-B01 | `PendingAuthPurgeJobConfiguration` defaults (5/5) exercised by `PendingAuthBatchJobsIntegrationTest.frB6_…`; `NUMVAL` of the amount by `AuthorizationRequestTest.frA2_numvalTakesSignedSpacedAndUnpaddedAmounts` |
| FR-PA-B02 | `PendingAuthPurgeServiceTest.frB1_aChildIsExpiredWhenTheDayDifferenceReachesTheExpiryDays`, `.frB5_theExpiryTestIsPlainYydddSubtraction` |
| FR-PA-B03 | `PendingAuthPurgeServiceTest.frB2_expiredApprovalsDecrementTheApprovedCountAndAmount`, `.frB3_expiredDeclinesDecrementTheDeclinedCountAndTheTransactionAmount` |
| FR-PA-B04 | `PendingAuthBatchJobsIntegrationTest.frB6_cbpaup0jDeletesExpiredChildrenAndTheirRootAndKeepsTheRest` |
| FR-PA-B05 | `PendingAuthPurgeServiceTest.frB4_theRootIsDeletedOnTheApprovedCountAloneEvenWithDeclinesLeft`, `PendingAuthBatchJobsIntegrationTest.frB7_aRootWhoseApprovalsHaveAllExpiredGoesWithItsChildren` |
| FR-PA-B06, B08 | BD-4: an unexpected status fails the step (`FAILED`); the exit-code mapping is the launcher's — see §6 |
| FR-PA-B07 | `PendingAuthBatchJobsIntegrationTest.frU6_reloadingTheSameFilesSkipsTheSegmentsAlreadyInTheDatabase`, `PendingAuthSegmentFormatTest.frU3_theChildRecordIsFixedWidthAndCarriesItsRootKey` |
| FR-PA-B09 | `PendingAuthBatchJobsIntegrationTest.frU5_unldpadbWritesTheTwoFilesLoadpadbReadsBack` |
| FR-PA-B10 | `PendingAuthBatchJobsIntegrationTest.frU7_unldgsamWritesTheSameRecordsThroughItsTwoGsamStreams` |
| Record layouts (BD-6) | `PendingAuthSegmentFormatTest.frU1_…`, `.frU2_…`, `.frU3_…`, `.frU4_…` |

## 5. Fixtures

`app/data/ASCII/**` carries no PAUT unload (BD-8), so the browse/detail oracle is
`R__seed_pendingauth_demo_data.sql`: account 1 with seven children (five on page 1, next-page flag on)
and account 2 with one child (the end-of-chain case), keyed on real 9s-complement values. The account,
customer and card cross-reference rows those hang off are the foundation's own ASCII-derived seed, so
the header assertions do come from `app/data/ASCII/**` data.

## 6. Deliberately deferred / not migrated

1. **PF-key dispatch (FR-PA-S15, S16, D13, D14).** The "invalid key" and F3 paths are screen concerns; the
   React pages carry the `pfKeys` map and the backend has no notion of an AID. The literal
   `Invalid key pressed. Please see below...` is not asserted by a backend test.
2. **DB2/IMS failure texts (FR-PA-D10, D11, F05, A15, B06, B08).** `SQLCODE`/`SQLSTATE` and the DL/I
   status suffixes do not exist in the target (BD-4/BD-5): the rollback and job-failure *behaviour* is
   implemented and tested, the operator-facing text is not reproducible verbatim and is logged instead.
3. **The 501-message run limit (FR-PA-A16, quirk Q-4).** It is a property of the legacy MQGET polling
   loop; a `@JmsListener` has no run to end. Documented in the FR, not implemented.
4. **Unreachable decline reasons (quirk Q-5).** `4200`/`4300`/`5100`/`5200` are in the reason table
   (rendered by CPVD) but no source path assigns them, so nothing assigns them here either.
5. **`ACCT NOT FOUND IN XREF` / `CUST NOT FOUND IN XREF` (FR-PA-A03).** Implemented on the same lookup
   chain as the card case; only the card case has its own test, since the log text is BD-5.
6. **Checkpoint/restart granularity.** `CBPAUP0J` restarts from the last committed root through the
   step execution context (`pendingAuth.purge.lastAcctId`) rather than an IMS `CHKP` id; the
   checkpoint frequency parameter maps to the chunk size.

## 7. Where the source contradicted the inventory

- `docs/migration/CardDemo_inventory.md` lists S-09 as IMS + DB2 + MQ; the module also reads the VSAM
  card cross-reference, account and customer masters (`COPAUA0C` XREF/ACCT/CUST reads, `COPAUS0C`
  header build). Those are the foundation's existing tables, so no boundary was added, but the stream
  is not IMS-only.
- The inventory implies fixtures for every stream under `app/data/ASCII/**`; there is **no** PAUT
  fixture there (BD-8). The demo hierarchy had to be derived from the copybooks.
- `COPAUS1C` labels `PA-PROCESSING-CODE` as `Auth Code:` (quirk Q-3) while `PA-AUTH-ID-CODE` is the
  field the reply calls the auth id — the screen and the MQ contract disagree. The screen wins, as the
  source has it.

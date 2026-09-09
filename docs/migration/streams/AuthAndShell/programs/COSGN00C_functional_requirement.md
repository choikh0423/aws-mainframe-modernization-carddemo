# COSGN00C (CC00) — functional requirements

Source: `app/cbl/COSGN00C.cbl`, `app/bms/COSGN00.bms`, `app/cpy/CSUSR01Y.cpy`,
`app/cpy/COCOM01Y.cpy`, `app/cpy/CSMSG01Y.cpy`.
Migrated to `com.carddemo.authshell.SignOnService` / `AuthController` and
`frontend/src/pages/SignOnPage.js`.

| ID | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-SGN-01 | The sign-on screen shows, in this order: the `Tran :`/`Prog :`/`Date :`/`Time :`/`AppID:`/`SysID:` header, `This is a Credit Card Demo Application for Mainframe Modernization`, the nine 42-character banknote art lines, `Type your User ID and Password, then press ENTER:`, `User ID     :` + 8-char input + `(8 Char)`, `Password    :` + 8-char non-display input + `(8 Char)`, the error message line, and `ENTER=Sign-on  F3=Exit`. | `COSGN00.bms:29-205` | `AuthControllerIntegrationTest.signOnScreenServesTheMapLiterals`, `ScreenTextTest.signOnScreenTextIsVerbatimFromTheMap` |
| FR-SGN-02 | On first entry (no commarea) the screen is painted empty with the cursor in the User ID field and no message. | `COSGN00C.cbl:80-83` | `AuthControllerIntegrationTest.sessionIsUnauthorizedBeforeSignOn` |
| FR-SGN-03 | A blank (spaces or low-values) User ID is refused with `Please enter User ID ...` and the cursor on the User ID field; USRSEC is not read. | `COSGN00C.cbl:118-122,138-140` | `SignOnServiceTest.missingUserIdAsksForIt` |
| FR-SGN-04 | A blank Password (with a non-blank User ID) is refused with `Please enter Password ...` and the cursor on the Password field; USRSEC is not read. | `COSGN00C.cbl:123-127,138-140` | `SignOnServiceTest.missingPasswordAsksForIt` |
| FR-SGN-05 | Both inputs are upper-cased before the USRSEC read, so `user0001`/`password` signs on. | `COSGN00C.cbl:132-136` | `SignOnServiceTest.inputsAreUpperCasedBeforeTheUsrsecRead` |
| FR-SGN-06 | Both inputs are moved to PIC X(08) fields, so a longer value is truncated to its first 8 characters and still signs on (quirk: the 3270 map cannot produce one, an HTTP client can). | `COSGN00C.cbl:132-136` | `SignOnServiceTest.inputsLongerThanTheEightBytePicAreTruncated` |
| FR-SGN-07 | USRSEC is read by the 8-byte user id key; the password compares as fixed-length X(08), i.e. trailing blanks are insignificant and leading blanks are significant. | `COSGN00C.cbl:211-223` | `SignOnServiceTest.storedPasswordComparesAsAFixedLengthField`, `SignOnServiceTest.leadingBlanksAreNotIgnored` |
| FR-SGN-08 | A matching password with `SEC-USR-TYPE = 'A'` routes to `COADM01C`. | `COSGN00C.cbl:230-234` | `SignOnServiceTest.administratorIsRoutedToTheAdminMenu` |
| FR-SGN-09 | A matching password with any other `SEC-USR-TYPE` — `'U'` or an unexpected letter — routes to `COMEN01C`, because only `'A'` is tested. | `COSGN00C.cbl:230-239` | `SignOnServiceTest.ordinaryUserIsRoutedToTheMainMenu`, `SignOnServiceTest.anUnexpectedUserTypeFallsThroughToTheMainMenu` |
| FR-SGN-10 | A known user with a wrong password is refused with `Wrong Password. Try again ...` and the cursor on the Password field. | `COSGN00C.cbl:241-246` | `SignOnServiceTest.wrongPasswordIsReportedSeparately` |
| FR-SGN-11 | An unknown user id (VSAM response 13, `NOTFND`) is refused with `User not found. Try again ...` and the cursor on the User ID field. | `COSGN00C.cbl:247-251` | `SignOnServiceTest.unknownUserIdIsReportedAsNotFound` |
| FR-SGN-12 | Any other USRSEC read failure is refused with `Unable to verify the User ...` and the cursor on the User ID field. | `COSGN00C.cbl:252-256` | `SignOnServiceTest.aFailingUsrsecReadIsReportedAsUnableToVerify` |
| FR-SGN-13 | A successful sign-on builds the commarea the menu programs receive: `CDEMO-FROM-TRANID='CC00'`, `CDEMO-FROM-PROGRAM='COSGN00C'`, `CDEMO-USER-ID` = the upper-cased id, `CDEMO-USER-TYPE` = `SEC-USR-TYPE`, `CDEMO-PGM-CONTEXT=0`. | `COSGN00C.cbl:224-228` | `SignOnServiceTest.successfulSignOnBuildsTheCommareaCosgn00cHandsOn`, `AuthControllerIntegrationTest.signOnStoresTheCommareaForTheNextScreen` |
| FR-SGN-14 | A rejected sign-on leaves no session state behind (no commarea, no role). | `COSGN00C.cbl:138-140` | `AuthControllerIntegrationTest.rejectedSignOnLeavesNoSession` |
| FR-SGN-15 | PF3 on the sign-on screen ends the conversation with the plain text `Thank you for using CardDemo application...` and no commarea. | `COSGN00C.cbl:88-90,162-172` | `AuthControllerIntegrationTest.signOffAnswersWithTheThankYouTextAndDropsTheSession` |
| FR-SGN-16 | Any AID other than ENTER and PF3 redisplays the screen with `Invalid key pressed. Please see below...`. | `COSGN00C.cbl:91-94` | `AuthControllerIntegrationTest.invalidKeyRedisplaysTheSignOnMessage` |
| FR-SGN-17 | The header carries the transaction id `CC00`, the program name `COSGN00C`, `AWS Mainframe Modernization`, `CardDemo`, the current date as `mm/dd/yy`, the current time as `hh:mm:ss`, plus the CICS `APPLID` and `SYSID`. | `COSGN00C.cbl:177-204`, `COTTL01Y.cpy:18-21` | `AuthControllerIntegrationTest.signOnScreenServesTheHeaderFields` |

## Quirks preserved

* FR-SGN-06 — no length validation exists; the PIC truncates instead.
* FR-SGN-09 — the `'A'` test is one-sided, so a corrupt user type degrades to the
  ordinary main menu rather than being rejected.
* FR-SGN-10 — the wrong-password branch does not set `WS-ERR-FLG`; it is the last
  statement of the paragraph, so behaviour is unaffected. Not reproduced as state,
  only as the message.
* The blank-password branch still moves the typed user id into `CDEMO-USER-ID`
  (`COSGN00C.cbl:132-134`) before the error is returned. The migrated service
  returns no commarea at all on failure, which is indistinguishable to the
  operator because the failed turn re-sends the map and the next program never
  runs (see the migration plan, BD-6).

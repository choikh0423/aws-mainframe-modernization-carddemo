# COMEN01C (CM00) — functional requirements

Source: `app/cbl/COMEN01C.cbl`, `app/bms/COMEN01.bms`, `app/cpy/COMEN02Y.cpy`,
`app/cpy/COCOM01Y.cpy`, `app/cpy/CSMSG01Y.cpy`.
Migrated to `com.carddemo.authshell.MenuService` / `MenuCatalog` / `MenuController`
and `frontend/src/pages/MainMenuPage.js` + `components/MenuScreen.js`.

| ID | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-MEN-01 | The main menu screen shows the `CM00` / `COMEN01C` header, the title `Main Menu`, the option lines, `Please select an option :` with a 2-character numeric right-justified zero-filled input, the message line and `ENTER=Continue  F3=Exit`. | `COMEN01.bms:75-162` | `MenuControllerIntegrationTest.mainMenuScreenServesTheMapLiterals`, `ScreenTextTest.menuScreenTextIsVerbatimFromTheMaps` |
| FR-MEN-02 | The option list is exactly the 11 rows of `COMEN02Y` in table order: 1 Account View/COACTVWC, 2 Account Update/COACTUPC, 3 Credit Card List/COCRDLIC, 4 Credit Card View/COCRDSLC, 5 Credit Card Update/COCRDUPC, 6 Transaction List/COTRN00C, 7 Transaction View/COTRN01C, 8 Transaction Add/COTRN02C, 9 Transaction Reports/CORPT00C, 10 Bill Payment/COBIL00C, 11 Pending Authorization View/COPAUS0C, each with user type `U`. | `COMEN02Y.cpy:21-98` | `MenuCatalogTest.mainMenuIsTheElevenRowsOfComen02y` |
| FR-MEN-03 | Each option line reads `NN. <name>`, the number zero-padded to two digits and the name padded to the copybook's 35 characters inside a 40-character field. | `COMEN01C.cbl:262-303` | `MenuCatalogTest.optionLinesAreBuiltLikeBuildMenuOptions` |
| FR-MEN-04 | Reaching the menu without a commarea returns to `COSGN00C`. | `COMEN01C.cbl:82-84` | `MenuControllerIntegrationTest.mainMenuWithoutACommareaIsRefused` |
| FR-MEN-05 | The first paint of the menu sets `CDEMO-PGM-REENTER` (`CDEMO-PGM-CONTEXT = 1`) in the commarea. | `COMEN01C.cbl:87-90` | `MenuControllerIntegrationTest.paintingTheMainMenuSetsPgmReenter` |
| FR-MEN-06 | The typed option is normalised as `MOVE OPTIONI(1:last-non-blank) TO PIC X(02) JUST RIGHT`, `INSPECT REPLACING ALL ' ' BY '0'`: `1`, `1␠`, `␠1` and `01` all mean option 1, and the screen echoes the resulting two digits. | `COMEN01C.cbl:117-125` | `MenuServiceTest.spacesAreReplacedByZerosBeforeTheNumericTest`, `MenuServiceTest.theScreenEchoesTheTwoDigitOption` |
| FR-MEN-07 | An option that is non-numeric, zero or greater than `CDEMO-MENU-OPT-COUNT` (11) is refused with `Please enter a valid option number...` in the map's red, and nothing is dispatched. | `COMEN01C.cbl:127-134` | `MenuServiceTest.rejectsZeroTooHighAndNonNumericOptions` |
| FR-MEN-08 | A commarea user type of exactly `'U'` selecting an option whose `CDEMO-MENU-OPT-USRTYPE` is `'A'` is refused with `No access - Admin Only option... ` (trailing space included). Unreachable with the shipped table, in which every option is `U`. | `COMEN01C.cbl:136-143` | `MenuServiceTest.refusesAnAdminOnlyOptionToAUserTypeUser` |
| FR-MEN-09 | The `'A'` refusal applies only to user type `'U'`: a commarea user type that is neither `A` nor `U` is not stopped by it. | `COMEN01C.cbl:136` (`CDEMO-USRTYP-USER`) | `MenuServiceTest.anUnexpectedUserTypeIsNotStoppedByTheAdminOnlyRule` |
| FR-MEN-10 | Option 11 (`COPAUS0C`) is dispatched only when the program is installed; when it is not, the screen shows `This option Pending Authorization View is not installed...` in red and stays on the menu. | `COMEN01C.cbl:147-168` | `MenuServiceTest.copaus0cIsDispatchedOnlyWhenTheProgramIsInstalled`, `MenuServiceTest.anUninstalledCopaus0cIsReportedInRed` |
| FR-MEN-11 | An option whose program name starts with `DUMMY` shows `This option <first word of name>is coming soon ...` in green — the missing separator is the legacy `DELIMITED BY SPACE` behaviour. No shipped option maps to a `DUMMY` program. | `COMEN01C.cbl:169-176` | `MenuServiceTest.aDummyProgramIsReportedAsComingSoon` |
| FR-MEN-12 | Any other accepted option sets `CDEMO-FROM-TRANID='CM00'`, `CDEMO-FROM-PROGRAM='COMEN01C'`, `CDEMO-PGM-CONTEXT=0` and transfers to `CDEMO-MENU-OPT-PGMNAME`, leaving `CDEMO-USER-ID` / `CDEMO-USER-TYPE` as sign-on set them. | `COMEN01C.cbl:177-187` | `MenuControllerIntegrationTest.selectingAnOptionHandsTheCommareaToTheTargetProgram` |
| FR-MEN-13 | PF3 leaves the menu for `COSGN00C` with no commarea, so the sign-on screen restarts empty and without a message. | `COMEN01C.cbl:96-98,196-203` | `MenuControllerIntegrationTest.pf3ReturnsToTheSignOnScreenWithoutACommarea` |
| FR-MEN-14 | Any AID other than ENTER and PF3 redisplays the menu with `Invalid key pressed. Please see below...`. | `COMEN01C.cbl:99-102` | `MenuControllerIntegrationTest.anInvalidKeyRedisplaysTheMainMenuMessage` |

## Quirks preserved

* FR-MEN-06 — the parse accepts a leading blank and a trailing blank alike; `00`
  and blanks both become option zero, which is invalid.
* FR-MEN-09 — the admin-only test is `CDEMO-USRTYP-USER`, not "not admin".
* FR-MEN-11 — `This option Accountis coming soon ...` really has no space before
  `is`; the `STRING ... DELIMITED BY SPACE` stops at the first blank of the
  padded X(35) name.
* `COMEN01C.cbl:136` evaluates `CDEMO-MENU-OPT-USRTYPE(WS-OPTION)` even after the
  option was rejected, indexing the table with `0` or an out-of-range subscript.
  The migrated code stops at the first failure; the observable outcome is
  identical because `IF NOT ERR-FLG-ON` gates every dispatch.
* `COMEN01C.cbl:179-180` moves `WS-PGMNAME` into `CDEMO-FROM-PROGRAM` twice.

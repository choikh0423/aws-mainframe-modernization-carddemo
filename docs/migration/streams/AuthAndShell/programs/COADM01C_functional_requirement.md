# COADM01C (CA00) — functional requirements

Source: `app/cbl/COADM01C.cbl`, `app/bms/COADM01.bms`, `app/cpy/COADM02Y.cpy`,
`app/cpy/COCOM01Y.cpy`, `app/cpy/CSMSG01Y.cpy`.
Migrated to `com.carddemo.authshell.MenuService` / `MenuCatalog` / `MenuController`
and `frontend/src/pages/AdminMenuPage.js` + `components/MenuScreen.js`.

| ID | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-ADM-01 | The admin menu screen shows the `CA00` / `COADM01C` header, the title `Admin Menu`, the option lines, `Please select an option :` with a 2-character numeric right-justified zero-filled input, the message line and `ENTER=Continue  F3=Exit`. | `COADM01.bms:75-162` | `MenuControllerIntegrationTest.adminMenuScreenServesTheMapLiterals`, `ScreenTextTest.menuScreenTextIsVerbatimFromTheMaps` |
| FR-ADM-02 | The option list is exactly the 6 rows of `COADM02Y` in table order: 1 User List (Security)/COUSR00C, 2 User Add (Security)/COUSR01C, 3 User Update (Security)/COUSR02C, 4 User Delete (Security)/COUSR03C, 5 Transaction Type List/Update (Db2)/COTRTLIC, 6 Transaction Type Maintenance (Db2)/COTRTUPC. The copybook has no user-type column, so the options carry none. | `COADM02Y.cpy:22-59` | `MenuCatalogTest.adminMenuIsTheSixRowsOfCoadm02y` |
| FR-ADM-03 | Each option line reads `NN. <name>`, the number zero-padded to two digits and the name padded to 35 characters inside a 40-character field. | `COADM01C.cbl:229-266` | `MenuCatalogTest.optionLinesAreBuiltLikeBuildMenuOptions` |
| FR-ADM-04 | Reaching the menu without a commarea returns to `COSGN00C`. | `COADM01C.cbl:86-88` | `MenuControllerIntegrationTest.adminMenuWithoutACommareaIsRefused` |
| FR-ADM-05 | The first paint of the menu sets `CDEMO-PGM-REENTER` (`CDEMO-PGM-CONTEXT = 1`) in the commarea. | `COADM01C.cbl:91-94` | `MenuControllerIntegrationTest.paintingTheAdminMenuSetsPgmReenter` |
| FR-ADM-06 | The typed option is normalised exactly as on the main menu (right-justify, blanks to zeros, two-digit echo). | `COADM01C.cbl:121-129` | `MenuServiceTest.adminOptionsUseTheSameParsingAsTheMainMenu` |
| FR-ADM-07 | An option that is non-numeric, zero or greater than `CDEMO-ADMIN-OPT-COUNT` (6) is refused with `Please enter a valid option number...`, and nothing is dispatched. | `COADM01C.cbl:131-138` | `MenuServiceTest.rejectsAdminOptionsOutsideTheSixEntries` |
| FR-ADM-08 | `COADM01C` performs **no** user-type check: a commarea whose `CDEMO-USER-TYPE` is `'U'` may select any admin option. Access control is entirely `COSGN00C`'s routing plus the target programs. | `COADM01C.cbl:119-158` (no `CDEMO-USRTYP-*` test) | `MenuServiceTest.theAdminMenuHasNoUserTypeCheck` |
| FR-ADM-09 | An accepted option whose program name does not start with `DUMMY` sets `CDEMO-FROM-TRANID='CA00'`, `CDEMO-FROM-PROGRAM='COADM01C'`, `CDEMO-PGM-CONTEXT=0` and transfers to `CDEMO-ADMIN-OPT-PGMNAME`. | `COADM01C.cbl:141-149` | `MenuControllerIntegrationTest.selectingAnAdminOptionHandsTheCommareaToTheTargetProgram` |
| FR-ADM-10 | An option whose program name starts with `DUMMY` shows `This option is not installed ...` in green and stays on the menu — the admin message never names the option, because the name is commented out of the `STRING`. | `COADM01C.cbl:150-157` | `MenuServiceTest.aDummyAdminProgramIsReportedAsNotInstalled` |
| FR-ADM-11 | Transferring to a program that is not installed raises `PGMIDERR` and shows the same `This option is not installed ...` in green. | `COADM01C.cbl:77-79,270-281` | `MenuServiceTest.anUninstalledAdminTargetIsReportedAsNotInstalled` |
| FR-ADM-12 | PF3 leaves the menu for `COSGN00C` with no commarea. | `COADM01C.cbl:100-102,163-170` | `MenuControllerIntegrationTest.pf3ReturnsToTheSignOnScreenFromTheAdminMenu` |
| FR-ADM-13 | Any AID other than ENTER and PF3 redisplays the menu with `Invalid key pressed. Please see below...`. | `COADM01C.cbl:103-106` | `MenuControllerIntegrationTest.anInvalidKeyRedisplaysTheAdminMenuMessage` |

## Quirks preserved

* FR-ADM-08 — the admin menu is unprotected inside the program; the estate relies
  on `COSGN00C` only ever routing `'A'` users to `CA00`.
* FR-ADM-10/11 — the message is `This option is not installed ...` with a single
  space between `option` and `is`, and with the ` ...` spacing of the literal; it
  differs from the main menu's ` is not installed...`.
* `COADM02Y` declares `OCCURS 9` for 6 populated rows; the count field is the only
  guard. The migrated catalog carries the 6 rows and validates against 6.

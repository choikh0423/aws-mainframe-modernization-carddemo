# S-01 AuthAndShell — functional requirements

The business sign-off oracle for transactions **CC00** (sign-on), **CM00** (main
menu) and **CA00** (admin menu). Per-program requirements live in:

* [`programs/COSGN00C_functional_requirement.md`](programs/COSGN00C_functional_requirement.md) — FR-SGN-01…17
* [`programs/COMEN01C_functional_requirement.md`](programs/COMEN01C_functional_requirement.md) — FR-MEN-01…14
* [`programs/COADM01C_functional_requirement.md`](programs/COADM01C_functional_requirement.md) — FR-ADM-01…13

This document adds the cross-program requirements: the navigation contract every
other stream consumes, and the message catalogue.

## 1. Navigation contract (COMMAREA)

| ID | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-NAV-01 | One `CARDDEMO-COMMAREA` equivalent exists per signed-on session, carrying `CDEMO-FROM-TRANID`, `CDEMO-FROM-PROGRAM`, `CDEMO-TO-TRANID`, `CDEMO-TO-PROGRAM`, `CDEMO-USER-ID`, `CDEMO-USER-TYPE` and `CDEMO-PGM-CONTEXT`. | `COCOM01Y.cpy:20-31` | `MenuControllerIntegrationTest.selectingAnOptionHandsTheCommareaToTheTargetProgram` |
| FR-NAV-02 | Sign-on is the only producer of `CDEMO-USER-ID` / `CDEMO-USER-TYPE`; the menus never change them. | `COSGN00C.cbl:226-227`, `COMEN01C.cbl:181-182` | `MenuControllerIntegrationTest.theMenusNeverRewriteTheSignedOnUser` |
| FR-NAV-03 | Every transfer out of this stream leaves `CDEMO-FROM-TRANID` / `CDEMO-FROM-PROGRAM` set to the *sending* screen (`CC00`/`COSGN00C`, `CM00`/`COMEN01C`, `CA00`/`COADM01C`) and `CDEMO-PGM-CONTEXT = 0`, so the receiving program knows it is a first entry. | `COSGN00C.cbl:224-228`, `COMEN01C.cbl:153-155,178-183`, `COADM01C.cbl:142-144` | `MenuControllerIntegrationTest.selectingAnOptionHandsTheCommareaToTheTargetProgram`, `MenuControllerIntegrationTest.selectingAnAdminOptionHandsTheCommareaToTheTargetProgram` |
| FR-NAV-04 | A screen painted by this stream sets `CDEMO-PGM-CONTEXT = 1` for its own re-entry, and the value is reset to 0 on the way out. | `COMEN01C.cbl:87-88`, `COADM01C.cbl:91-92` | `MenuControllerIntegrationTest.paintingTheMainMenuSetsPgmReenter`, `MenuControllerIntegrationTest.selectingAnOptionHandsTheCommareaToTheTargetProgram` |
| FR-NAV-05 | Leaving the shell (PF3 on any of the three screens, or entering a menu without a commarea) drops the session state, exactly as the `XCTL PROGRAM('COSGN00C')` without `COMMAREA` does. | `COSGN00C.cbl:162-172`, `COMEN01C.cbl:196-203`, `COADM01C.cbl:163-170` | `MenuControllerIntegrationTest.pf3ReturnsToTheSignOnScreenWithoutACommarea` |
| FR-NAV-06 | A screen is reachable from a menu as soon as its program is known to the shell; the menus never hard-code a route. | `COMEN01C.cbl:156-159,184-187` | `MenuControllerIntegrationTest.selectingAnOptionHandsTheCommareaToTheTargetProgram` |

## 2. Message catalogue

Every string below is byte-for-byte the COBOL literal (including trailing
spaces where the literal has them) and is the only text the migrated screens may
show for that condition.

| ID | Message | Colour | Where |
| --- | --- | --- | --- |
| FR-MSG-01 | `Please enter User ID ...` | red (map default) | `COSGN00C.cbl:120` |
| FR-MSG-02 | `Please enter Password ...` | red | `COSGN00C.cbl:125` |
| FR-MSG-03 | `Wrong Password. Try again ...` | red | `COSGN00C.cbl:242` |
| FR-MSG-04 | `User not found. Try again ...` | red | `COSGN00C.cbl:249` |
| FR-MSG-05 | `Unable to verify the User ...` | red | `COSGN00C.cbl:254` |
| FR-MSG-06 | `Thank you for using CardDemo application...` | plain text screen | `CSMSG01Y.cpy:18-19`, `COSGN00C.cbl:89` |
| FR-MSG-07 | `Invalid key pressed. Please see below...` | red | `CSMSG01Y.cpy:20-21` |
| FR-MSG-08 | `Please enter a valid option number...` | red | `COMEN01C.cbl:131`, `COADM01C.cbl:135` |
| FR-MSG-09 | `No access - Admin Only option... ` (trailing space) | red | `COMEN01C.cbl:140` |
| FR-MSG-10 | `This option <name> is not installed...` | red (`DFHRED`) | `COMEN01C.cbl:163-167` |
| FR-MSG-11 | `This option <first word>is coming soon ...` | green (`DFHGREEN`) | `COMEN01C.cbl:172-176` |
| FR-MSG-12 | `This option is not installed ...` | green (`DFHGREEN`) | `COADM01C.cbl:152-156,273-277` |

Covered by `ScreenTextTest.everyShellMessageIsTheCobolLiteral` and by the
per-message tests listed in the program documents.

## 3. Screen text

| ID | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-TXT-01 | The three screens' static text (titles, prompts, labels, field hints, the banknote art and the PF-key lines) is served from the map transcription, verbatim, and rendered without rewording. | `COSGN00.bms`, `COMEN01.bms`, `COADM01.bms` | `ScreenTextTest.signOnScreenTextIsVerbatimFromTheMap`, `ScreenTextTest.menuScreenTextIsVerbatimFromTheMaps` |
| FR-TXT-02 | The header of every screen carries the transaction id, the program name, `AWS Mainframe Modernization`, `CardDemo`, `mm/dd/yy` and `hh:mm:ss`; only the sign-on screen carries `AppID`/`SysID`. | `COTTL01Y.cpy:18-21`, `COSGN00C.cbl:177-204`, `COMEN01C.cbl:238-257`, `COADM01C.cbl:205-224` | `AuthControllerIntegrationTest.signOnScreenServesTheHeaderFields`, `MenuControllerIntegrationTest.mainMenuScreenServesTheMapLiterals` |

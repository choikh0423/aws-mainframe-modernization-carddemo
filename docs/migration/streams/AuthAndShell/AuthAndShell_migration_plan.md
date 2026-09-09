# S-01 AuthAndShell — migration plan

How CC00 / CM00 / CA00 were migrated into `migration/carddemo`, the boundary
decisions taken, and the legacy-vs-migrated parity for the main paths.

Read with [`AuthAndShell_analysis.md`](AuthAndShell_analysis.md) (what the COBOL
does) and [`AuthAndShell_functional_requirement.md`](AuthAndShell_functional_requirement.md)
(what the migrated stream must do).

## 1. What was built

| Legacy artefact | Migrated to |
| --- | --- |
| `COSGN00C` sign-on logic | `com.carddemo.authshell.SignOnService` |
| `COMEN01C` / `COADM01C` option handling | `com.carddemo.authshell.MenuService` |
| `COMEN02Y.cpy` / `COADM02Y.cpy` option tables | `com.carddemo.authshell.MenuCatalog` |
| `EXEC CICS INQUIRE PROGRAM` / `PGMIDERR` | `com.carddemo.authshell.ProgramInstallation` |
| BMS map literals (`COSGN00`, `COMEN01`, `COADM01`) | `com.carddemo.authshell.ScreenText` |
| `EIBAID` (`DFHENTER` / `DFHPF3` / other) | `com.carddemo.authshell.Aid` |
| `CARDDEMO-COMMAREA` (`COCOM01Y.cpy`) | `com.carddemo.common.session.CommareaContext`, held per HTTP session by `ShellSession` |
| CC00 / CM00 / CA00 screens | `AuthController` / `MenuController` + `SignOnPage.js`, `MainMenuPage.js`, `AdminMenuPage.js`, `MenuScreen.js` |

Endpoints:

| Method | Path | Legacy equivalent |
| --- | --- | --- |
| `GET` | `/api/auth/screen` | CC00 first entry, `SEND MAP` of `COSGN0A` |
| `POST` | `/api/auth/signon` | CC00 `PROCESS-ENTER-KEY` |
| `POST` | `/api/auth/signoff` | `XCTL COSGN00C` after PF3 / the thank-you screen |
| `GET` | `/api/auth/session` | reading the commarea the next program receives |
| `GET` | `/api/menu/main` | CM00 `SEND-MENU-SCREEN` |
| `POST` | `/api/menu/main/select` | CM00 `PROCESS-ENTER-KEY` |
| `GET` | `/api/admin/menu` | CA00 `SEND-MENU-SCREEN` |
| `POST` | `/api/admin/menu/select` | CA00 `PROCESS-ENTER-KEY` |

No Flyway migration was added: `sec_users` and the seed rows this stream needs
already exist in the shared schema, so the reserved V100–V199 band is untouched.
No shared entity was modified.

## 2. Boundary decisions

**BD-1 — package name.** The task brief says `com.carddemo.auth`; the parallel
contract in `migration/carddemo/README.md` assigns S-01 the package
`com.carddemo.authshell`, and the scaffold already shipped classes there. The
README wins, because it is the document the other streams read; everything in
this PR is under `com.carddemo.authshell` and nothing outside it (plus the two
message constants noted in BD-2) is touched.

**BD-2 — message catalogue location.** The shell messages live in the shared
`com.carddemo.common.message.CardDemoMessages`, which already existed and
already held `THANK_YOU` / `INVALID_KEY` for the whole app. The S-01 literals
were added there rather than duplicated in the stream package, so the streams
routed from these menus render the same strings.

**BD-3 — admin endpoints sit under `/api/admin/**`.** The scaffold's security
configuration protects `/api/admin/**` with `ROLE_ADMIN`. Naming CA00's
endpoints `/api/menu/admin` would have put the administrator menu outside that
rule and required editing shared security configuration — a cross-lane change.
The paths are therefore `/api/admin/menu` and `/api/admin/menu/select`. The
consequence is visible in one place: an unauthenticated CA00 request is refused
by the shared rule with 403 rather than the 401 that CM00 returns. Both mean the
same thing the legacy system means — no commarea, no transaction.

**BD-4 — COMMAREA is HTTP-session state, not a request payload.** CICS hands the
commarea from program to program; the migrated shell keeps one
`CommareaContext` per HTTP session (`ShellSession`) and lets other streams read
it through `CommareaSession`. This keeps `CDEMO-USER-ID` / `CDEMO-USER-TYPE`
unforgeable by the browser, which a request-carried commarea would not.

**BD-5 — "is the program installed?" is a configuration seam.** `COMEN01C`
issues `EXEC CICS INQUIRE PROGRAM` for `COPAUS0C` and `COADM01C` relies on
`PGMIDERR` from the `XCTL`. Neither has a meaning in a single deployable, but
the *messages* they produce are in scope and the downstream screens are not yet
all migrated. `ProgramInstallation` reads
`carddemo.authshell.uninstalled-programs`, so an operator can reproduce the
legacy "not installed" turn exactly, and the default (everything installed) is
the behaviour of a complete estate.

**BD-6 — a failed sign-on returns no commarea.** `COSGN00C` moves the typed user
id into `CDEMO-USER-ID` *before* the validation branches (`COSGN00C.cbl:132-134`),
so a rejected turn leaves a partly-filled commarea in storage. That state is
unobservable — the failed turn re-sends the map and no other program runs — and
carrying it forward over HTTP would mean handing out a session for a failed
authentication. The migrated service returns no session on failure (FR-SGN-14).

**BD-7 — the screens serve their own text.** `ScreenText` transcribes the BMS
literals and the controllers serve them to React, instead of the literals being
retyped in JSX. `ScreenTextTest` reads `app/bms/*.bms` and `app/cpy/CSMSG01Y.cpy`
at test time and fails if a string drifts from the map, which makes the
verbatim-text rule (FR-TXT-01, FR-MSG-01…12) enforceable rather than a review
convention.

**BD-8 — the menus stay out of the screens they route to.** A selection returns
the target program name and updates the commarea; the React route registry maps
program name to path. Adding a screen to the estate is one registry line in that
stream's PR and no change here.

## 3. Legacy vs migrated parity

Fixtures: the seeded `sec_users` rows `USER0001` (`PASSWORD`, type `U`) and
`ADMIN001` (`PASSWORD`, type `A`) from
`migration/carddemo/backend/src/main/resources/db/seed/R__seed_carddemo_data.sql`.

### CC00 sign-on

| Input | Legacy `COSGN00C` | Migrated | Test |
| --- | --- | --- | --- |
| blank user id | `Please enter User ID ...`, cursor USERIDL, no read | same, `errorField=userId` | `SignOnServiceTest.missingUserIdAsksForIt` |
| user id, blank password | `Please enter Password ...`, cursor PASSWDL, no read | same, `errorField=password` | `SignOnServiceTest.missingPasswordAsksForIt` |
| `user0001` / `password` | upper-cased, read OK, `XCTL COMEN01C` | 200, `nextProgram=COMEN01C` | `SignOnServiceTest.inputsAreUpperCasedBeforeTheUsrsecRead` |
| `ADMIN001` / `PASSWORD` | `XCTL COADM01C` | 200, `nextProgram=COADM01C`, `ROLE_ADMIN` | `AuthControllerIntegrationTest.administratorSignsOnAndIsSentToTheAdminMenu` |
| `USER0001` / `NOTRIGHT` | `Wrong Password. Try again ...` | 401, same text | `AuthControllerIntegrationTest.rejectedSignOnLeavesNoSession` |
| `NOSUCH01` / anything | `NOTFND` → `User not found. Try again ...` | 401, same text | `SignOnServiceTest.unknownUserIdIsReportedAsNotFound` |
| read failure | `Unable to verify the User ...` | 401, same text | `SignOnServiceTest.aFailingUsrsecReadIsReportedAsUnableToVerify` |
| PF3 | thank-you screen, no commarea | 200 thank-you, session dropped | `AuthControllerIntegrationTest.signOffAnswersWithTheThankYouTextAndDropsTheSession` |
| PF9 | `Invalid key pressed. Please see below...` | 401, same text | `AuthControllerIntegrationTest.invalidKeyRedisplaysTheSignOnMessage` |

### CM00 main menu

| Input | Legacy `COMEN01C` | Migrated | Test |
| --- | --- | --- | --- |
| `1` | `01` echoed, `XCTL COACTVWC` | `optionEcho=01`, `programName=COACTVWC`, commarea `CM00`/`COMEN01C`/context 0 | `MenuControllerIntegrationTest.selectingAnOptionHandsTheCommareaToTheTargetProgram` |
| `0`, `12`, `a`, blank | `Please enter a valid option number...` in red | same text and colour, no navigation | `MenuServiceTest.rejectsZeroTooHighAndNonNumericOptions` |
| `11` with `COPAUS0C` absent | `This option Pending Authorization View is not installed...` in red | same | `MenuServiceTest.anUninstalledCopaus0cIsReportedInRed` |
| a `DUMMY` target | `This option Accountis coming soon ...` in green (missing blank preserved) | same | `MenuServiceTest.aDummyProgramIsReportedAsComingSoon` |
| an admin-only row typed by a `U` user | `No access - Admin Only option... ` (trailing space) | same | `MenuServiceTest.refusesAnAdminOnlyOptionToAUserTypeUser` |
| PF3 | `XCTL COSGN00C` without commarea | session dropped, back to sign-on | `MenuControllerIntegrationTest.pf3ReturnsToTheSignOnScreenWithoutACommarea` |

### CA00 admin menu

| Input | Legacy `COADM01C` | Migrated | Test |
| --- | --- | --- | --- |
| `1` | `XCTL COUSR00C` | `programName=COUSR00C`, commarea `CA00`/`COADM01C`/context 0 | `MenuControllerIntegrationTest.selectingAnAdminOptionHandsTheCommareaToTheTargetProgram` |
| `7` | `Please enter a valid option number...` | same | `MenuServiceTest.rejectsAdminOptionsOutsideTheSixEntries` |
| target absent (`PGMIDERR`) | `This option is not installed ...` in green, option not named | same | `MenuServiceTest.anUninstalledAdminTargetIsReportedAsNotInstalled` |
| any user type | no user-type test at all | no user-type test in the service; the HTTP boundary still requires `ROLE_ADMIN` (BD-3) | `MenuServiceTest.theAdminMenuHasNoUserTypeCheck` |

## 4. Quirks preserved deliberately

* `'A'` is tested one-sidedly, so an unexpected `SEC-USR-TYPE` reaches the
  ordinary main menu instead of being rejected (FR-SGN-09).
* Sign-on fields are `PIC X(08)`: an over-long value is truncated, not rejected
  (FR-SGN-06).
* The option field is two bytes, right-justified with `INSPECT ... REPLACING
  LEADING SPACE BY ZERO`, so `1`, ` 1` and `01` are the same option and the
  screen echoes `01` (FR-MEN-03).
* `This option <first word>is coming soon ...` really is missing the blank
  before `is`, because the name is `DELIMITED BY SPACE` (FR-MSG-11).
* `No access - Admin Only option... ` really does end in a space (FR-MSG-09).
* CA00's not-installed message names no option and is green, unlike CM00's
  red, named one (FR-MSG-12).

None of these were "fixed".

## 5. Deferred / out of lane

* The screens the menus route to (`COACTVWC`, `COCRDLIC`, `CORPT00C`,
  `COUSR0*`, `COTRT*`, …) belong to other streams. This PR routes to them by
  program name and adds no route entries for them.
* `COPAUS0C` and any `DUMMY` target are reachable only through
  `carddemo.authshell.uninstalled-programs` until their streams land (BD-5).
* The sign-on screen's `AppID`/`SysID` come from
  `carddemo.authshell.applid` / `carddemo.authshell.sysid` (defaulting to
  `CARDDEMO` and blank): there is no CICS region to interrogate, and the labels
  are part of the map so they cannot be dropped.

## 6. Where the source contradicted the inventory

* `docs/migration/CardDemo_inventory.md` lists USRSEC among the S-01 data
  stores and the brief points at `app/data/ASCII/**` for fixtures, but there is
  no USRSEC extract there — `app/data/ASCII/` carries the account, card,
  customer, transaction and reference files only. Sign-on parity is therefore
  proved against the seeded `sec_users` rows in the consolidated schema, which
  match `CSUSR01Y.cpy` field for field.
* `COMEN02Y.cpy` declares `OCCURS 12` but populates 11 rows, and
  `COADM02Y.cpy` declares `OCCURS 9` but populates 6; the counts the programs
  compare against (`CDEMO-MENU-OPT-COUNT`) are 11 and 6. `MenuCatalog` carries
  the populated rows only and validates against those counts.

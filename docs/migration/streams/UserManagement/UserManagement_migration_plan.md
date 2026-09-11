# S-05 UserManagement — migration plan

How CU00/CU01/CU02/CU03 were migrated into the consolidated Spring Boot + React application, the
boundary decisions taken, the legacy-vs-migrated parity evidence, and what was deliberately deferred.
Requirements referenced here are defined in `UserManagement_functional_requirement.md` and the four
program documents under `programs/`.

## 1. Scope and lane

| Legacy | Transaction | Migrated surface | React screen |
|---|---|---|---|
| `COUSR00C.cbl` / `COUSR00.bms` | CU00 | `GET /api/admin/users` | `pages/user/UserListPage.js` |
| `COUSR01C.cbl` / `COUSR01.bms` | CU01 | `POST /api/admin/users` | `pages/user/AddUserPage.js` |
| `COUSR02C.cbl` / `COUSR02.bms` | CU02 | `GET`/`PUT /api/admin/users/{userId}` | `pages/user/UpdateUserPage.js` |
| `COUSR03C.cbl` / `COUSR03.bms` | CU03 | `GET`/`DELETE /api/admin/users/{userId}` | `pages/user/DeleteUserPage.js` |

Everything backend lives under `com.carddemo.user`; everything frontend lives under
`src/pages/user/` plus `src/api/users.js`. The only shared frontend file touched is
`src/routes/registry.js`, where the four S-05 entries' `element` values (and their four imports)
changed — the one-line-per-screen convention from `migration/carddemo/README.md`. No file under
`app/**`, `migration/transaction-management/**`, another stream's package, the sign-on code, or the CI
workflow was modified.

## 2. Implementation approach

Package layout mirrors the worked reference stream `com.carddemo.transaction`:

```
com.carddemo.user
├── controller/   UserListController, UserAddController, UserUpdateController, UserDeleteController
├── service/      UserListService, UserAddService, UserUpdateService, UserDeleteService
├── validator/    UserValidator                (the COBOL edit blocks, in source order)
├── repository/   UserBrowseRepository         (ordered browse only)
├── domain/       UserFields                   (the CSUSR01Y picture widths)
├── dto/          request/response records for the five endpoints
├── exception/    typed exceptions + UserExceptionHandler (@RestControllerAdvice)
└── UserMessages  every COBOL literal this stream can emit, verbatim
```

- **Messages.** Every literal the four programs can display is a constant in `UserMessages`; no
  message text is built anywhere else, and `UserMessagesTest` asserts the literals character for
  character. The `User <id> has been added/updated/deleted ...` confirmations reproduce COBOL
  `STRING … DELIMITED BY SPACE` by cutting the id at its first blank (quirk Q12).
- **Validation.** `UserValidator` runs the five empty-field edits in the *source* order — add:
  first name, last name, user id, password, user type (`COUSR01C.cbl:115-152`); update: user id,
  first name, last name, password, user type (`COUSR02C.cbl:177-213`) — and reports only the first
  failure, exactly as the COBOL falls out of its `IF/ELSE` ladder.
- **Errors.** Typed exceptions map to HTTP once, in `UserExceptionHandler`: validation and
  `Please modify to update ...` → 400, not found → 404, duplicate id → 409, an unexpected store
  failure → 500. The body is always `{ "message": "<the legacy literal>" }`, so the React screens
  render backend errors without rewording them.
- **Persistence.** CRUD goes through the shared `SecUserRepository`/`SecUserRecord`
  (`com.carddemo.common`), unmodified. CU00's browse needs ordered key ranges that the shared
  repository does not expose, so the stream owns a narrow `UserBrowseRepository` (a Spring Data
  `Repository`, not a second `JpaRepository`) with the four derived queries the VSAM browse needs.

## 3. Boundary decisions

- **B-1 — Admin-only via the existing URL rule.** `SecurityConfig` already restricts `/api/admin/**`
  to `ROLE_ADMIN`, and sign-on already grants that role for `SEC-USR-TYPE = 'A'`. Mounting the stream
  at `/api/admin/users` reproduces "reachable only from the admin menu" (FR-US-1) without touching
  shared security or sign-on code.
- **B-2 — Stateless paging instead of the COMMAREA browse.** The pseudo-conversational
  `STARTBR`/`READNEXT`/`READPREV` cursor and `CDEMO-CU00-*` COMMAREA state become
  `?startId=&dir=next|prev`: no cursor is held between requests, the initial/search key is
  **inclusive** (GTEQ) and the `next`/`prev` keys are **exclusive**, which is what the discarded
  `READNEXT` at `COUSR00C.cbl:286-288` achieves. `hasNextPage`/`hasPrevPage` carry the boundary
  conditions the COBOL kept in `CDEMO-CU00-NEXT-PAGE-FLG` and the page counter. The PF7 *guard*,
  though, is the screen's page counter and not `hasPrevPage`: ENTER resets `CDEMO-CU00-PAGE-NUM`
  (`COUSR00C.cbl:227`), so a search result is page 1 and PF7 refuses to move even where the file
  holds lower ids (FR-UL-5).
- **B-3 — Screen-only behaviour stays on the screen.** Page numbering (FR-UL-14), PF-key routing,
  clear, "invalid key pressed", and the CU00 selection scan are 3270/BMS concerns with no server
  state behind them, so they live in the React components (and, for the selection scan, in the
  unit-tested helper `pages/user/selection.js`) rather than in an endpoint that only exists to hold
  screen state.
- **B-4 — No schema change, no Flyway migration.** `sec_users` already matches `CSUSR01Y` (id 8,
  first/last 20, password 8, type 1) and the consolidated seed already carries the ten `DUSRSECJ`
  records, so this stream consumed **none** of its reserved migration band.
- **B-5 — Clear-text passwords are preserved.** USRSEC stores `SEC-USR-PWD PIC X(08)` in the clear
  and CU02 paints it back onto the screen; the migration keeps that behaviour (FR-US-4) because
  changing it would break sign-on, which is another stream's lane. The React screens use
  `type="password"` inputs, which is the modern equivalent of the map's non-display attribute — it
  is a rendering choice, not a storage change. **This is a known security weakness carried over
  deliberately; it should be addressed estate-wide, not stream by stream.**
- **B-6 — Truncation over rejection.** The 3270 map physically cannot deliver more than the picture
  width, so the API truncates to the `CSUSR01Y` widths (`UserFields.fit`) instead of rejecting long
  input, which keeps stored values identical to the legacy ones (FR-US-2). The cut is positional:
  leading blanks occupy characters of the field and survive, only trailing padding is dropped.
- **B-7 — Quirks preserved, not fixed.** Q1 (no user-type domain check), Q2 (no id/password rules),
  Q3 (`Unable to Update User...` on a failed *delete*), Q4 (PF3 on CU02 saves then leaves, discarding
  the outcome), Q5 (PF5 against a missing id reports `User ID NOT found...` and creates nothing),
  Q7 (no delete confirmation and no last-admin guard), Q8 (only the first non-blank `Sel` acts),
  Q10 (CU01 advertises `F12=Exit` but treats PF12 as an invalid key) and Q12 (`DELIMITED BY SPACE`
  confirmations) are all implemented as-is and each is pinned by a test.
- **B-8 — Writes are flushed where their outcome is read.** `EXEC CICS WRITE`/`REWRITE`/`DELETE`
  hand their RESP straight back to the program, which then chooses the literal to display. The
  services therefore flush inside the `try` that translates the failure (`saveAndFlush`, and an
  explicit `flush()` after the delete) rather than leaving it to the commit, where a constraint
  violation would escape as a 500 instead of `Unable to Add User...` and friends. A constraint
  violation on the CU01 write is the DUPREC arm, so it yields `User ID already exist...`.

## 4. Parity — legacy vs migrated, main paths

| # | Path | Legacy (COBOL) | Migrated | Evidence |
|---|---|---|---|---|
| P-1 | CU00 open | `STARTBR` at low values, `READNEXT` ×10, ascending id | `GET /api/admin/users` → the ten `DUSRSECJ` records, `ADMIN001…USER0005` | `UserListServiceTest.frUL1_*`, `UserListControllerTest.frUL1_*` |
| P-2 | CU00 search `USER0003` | GTEQ browse from the key, page reset to 1 | `?startId=USER0003` → first row `USER0003` (inclusive) | `UserListServiceTest.frUL2_searchKeyIsInclusive` |
| P-3 | CU00 PF8 / PF7 | next/previous 10 relative to the shown ids, exclusive | `?dir=next&startId=<lastId>` / `?dir=prev&startId=<firstId>` | `UserListServiceTest.frUL3_*`, `.frUL4_*` |
| P-4 | CU00 boundaries | `You are already at the top/bottom of the page...` | `hasPrevPage`/`hasNextPage` false → the same literals on the screen | `UserListServiceTest.frUL5_*`, `.frUL6_*`, `UserMessagesTest` |
| P-5 | CU00 `X` in `Sel` | `Invalid selection. Valid values are U and D`, page re-listed | `resolveSelection` → `invalid`, message over a re-listed page 1 | `selection.test.js` |
| P-6 | CU01 blank first name | `First Name can NOT be empty...`, nothing written | 400 with the same literal, no row inserted | `UserValidatorTest.frUA2_*`, `UserAddControllerTest.frUA2_*` |
| P-7 | CU01 add `NEWUSR01` | record written, fields cleared, `User NEWUSR01 has been added ...` | 201 `{userId, message}` with the same literal | `UserAddServiceTest.frUA3_*`, `UserAddControllerTest.frUA4_*` |
| P-8 | CU01 add `ADMIN001` | `DUPKEY` → `User ID already exist...` | 409 with the same literal | `UserAddServiceTest.frUA5_*`, `UserAddControllerTest.frUA5_*` |
| P-9 | CU02 fetch `USER0001` | paints `LAWRENCE`/`THOMAS`/`PASSWORD`/`U`, `Press PF5 key to save your updates ...` | `GET /{id}` returns the same four values; the prompt is rendered by `UpdateUserPage` | `UserUpdateServiceTest.frUU3_*`, `UserUpdateControllerTest.frUU3_*` |
| P-10 | CU02 PF5, nothing changed | `Please modify to update ...`, no rewrite | 400 with the same literal, no write | `UserUpdateServiceTest.frUU8_*`, `UserUpdateControllerTest.frUU8_*` |
| P-11 | CU02 PF5, first name changed | rewrite, `User USER0001 has been updated ...` | 200 with the same literal, row updated | `UserUpdateServiceTest.frUU7_*`, `UserUpdateControllerTest.frUU9_*` |
| P-12 | CU02 PF5 on `NOSUCH01` | `User ID NOT found...`, no record created (quirk Q5) | 404 with the same literal, no insert | `UserUpdateServiceTest.frUU16_*` |
| P-13 | CU03 PF5 on `ADMIN001` | deleted, no confirmation and no last-admin guard, `User ADMIN001 has been deleted ...` | `DELETE /{id}` → 200, row gone, same literal | `UserDeleteServiceTest.frUD7_*`, `.frUD14_*`, `UserDeleteControllerTest.frUD8_*` |
| P-14 | CU03 delete failure | `Unable to Update User...` (the source's own wording, quirk Q3) | 500 with the identical wording | `UserDeleteServiceMockTest.frUD10_*`, `UserMessagesTest.frUD10_*` |
| P-15 | Non-admin caller | CU00–CU03 are not on the main menu at all | 403 on all five endpoints | `UserAdminSecurityTest.frUS1_*` |

### Fixture used for parity

There is **no ASCII USRSEC extract** under `app/data/ASCII/**` (see §6). The authoritative fixture for
this stream is the in-stream `DUSRSECJ` load data, `app/jcl/DUSRSECJ.jcl:34-43` — ten records,
`ADMIN001`…`ADMIN005` (type `A`) and `USER0001`…`USER0005` (type `U`), all with password `PASSWORD`.
Those exact ten records are what the consolidated H2/PostgreSQL seed already contains, and
`UserListServiceTest.frUL17_rowsCarryTheDusrsecjFixtureValuesAndTheRawTypeCharacter` asserts the
migrated list against them field by field, so the parity table above runs on legacy data.

## 5. Verification

- `mvn -B test` in `migration/carddemo/backend`, after rebasing onto
  `origin/devin/carddemo-integration`: **278 tests, 0 failures** (75 of them S-05).
- `npm run build` in `migration/carddemo/frontend`: compiled successfully.
- `npx react-scripts test --watchAll=false`: the `pages/user/selection.test.js` suite passes.
- Maven Central answers HTTP 429 in this environment; builds run with `~/.m2/settings.xml` mirroring
  `central` to `https://maven-central.storage-download.googleapis.com/maven2`, as the brief instructs.

## 6. Deliberate deferrals and contradictions

- **Source vs inventory — no ASCII USRSEC fixture.** `docs/migration/CardDemo_inventory.md` lists
  USRSEC as an S-05 data source and the brief points at `app/data/ASCII/**` for fixtures, but that
  directory contains no user-security extract (the file is created by `DUSRSECJ`, whose records are
  inline in the JCL). Parity therefore uses `app/jcl/DUSRSECJ.jcl:34-43`, as described above. No
  fixture file was added to `app/**`, which is off-limits.
- **React component render tests deferred.** The frontend has no component-testing library
  (`@testing-library/*` is not a dependency) and the reference stream ships no component tests;
  adding one would mean editing the shared `package.json`, which is outside this lane. The
  screen-level requirements (FR-UL-13…FR-UL-16, FR-UA-1/7/8/9, FR-UU-1/11…FR-UU-14, FR-UD-1/3/11…13)
  are therefore covered by the unit-tested `selection.js` helper plus the literals and handlers in the
  page components, and are traced to those in the coverage tables. If the estate later adds a
  component-testing dependency at the shell level, these are the first screens to backfill.
- **Password storage left in the clear** (B-5) — deliberate, estate-wide decision to make, not a
  stream-local one.
- **No last-admin / self-delete guard** (quirk Q7, FR-UD-14) — preserved on purpose; if the business
  wants a guard it is a new requirement, not a migration defect.

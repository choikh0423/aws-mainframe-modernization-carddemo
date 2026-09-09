# COUSR02C (CU02) — Update User — functional requirements

Source: `app/cbl/COUSR02C.cbl`, map `app/bms/COUSR02.bms` (`COUSR2A`), file USRSEC
(`app/cpy/CSUSR01Y.cpy`).

## Screen

| Element | Verbatim text | Cite |
|---|---|---|
| Title | `Update User` | `COUSR02.bms:79` |
| Key field | `Enter User ID:` — 8 chars, cursor here on entry | `:80-89` |
| Field 1 | `First Name:` — 20 chars | `:98-107` |
| Field 2 | `Last Name:` — 20 chars | `:111-120` |
| Field 3 | `Password:` — 8 chars, `DRK`, `(8 Char)` | `:125-139` |
| Field 4 | `User Type: ` — 1 char, `(A=Admin, U=User)` | `:140-154` |
| PF line | `ENTER=Fetch  F3=Save&Exit  F4=Clear  F5=Save  F12=Cancel` (`&&` in the map source is one `&`) | `:159-164` |

## Requirements

**FR-UU-1 — Arrival from the list.** When CU02 is entered with `CDEMO-CU02-USR-SELECTED` set (a `U`
selection on CU00) that id is placed in the key field and fetched immediately.
*(`COUSR02C.cbl:96-104`)*

**FR-UU-2 — Fetch needs a key.** ENTER with a blank user id displays `User ID can NOT be empty...`.
*(`COUSR02C.cbl:143-155`)*

**FR-UU-3 — Fetch.** ENTER with a user id blanks the four data fields, reads the record and, on
success, paints First Name, Last Name, Password and User Type from it and displays
`Press PF5 key to save your updates ...`. *(`COUSR02C.cbl:157-172`, `:334-339`)*

**FR-UU-4 — Fetch not found.** A read that finds nothing displays `User ID NOT found...`.
*(`COUSR02C.cbl:340-346`)*

**FR-UU-5 — Fetch failure.** Any other non-normal read response displays `Unable to lookup User...`.
*(`COUSR02C.cbl:347-352`)*

**FR-UU-6 — Save edit order.** PF5 validates in this order, first failure only: user id, first name,
last name, password, user type — each `<Field> can NOT be empty...` using the same literals as CU01
(`User ID can NOT be empty...`, `First Name can NOT be empty...`, `Last Name can NOT be empty...`,
`Password can NOT be empty...`, `User Type can NOT be empty...`). *(`COUSR02C.cbl:177-213`)*

**FR-UU-7 — Change detection.** PF5 re-reads the stored record and compares first name, last name,
password and user type independently; only differing fields are copied in, and the record is rewritten
only when at least one differs. *(`COUSR02C.cbl:215-237`)*

**FR-UU-8 — No change.** When no field differs nothing is written and the screen displays, in red,
`Please modify to update ...`. *(`COUSR02C.cbl:238-243`)*

**FR-UU-9 — Save success.** A successful rewrite displays, in green,
`User <id> has been updated ...` (`<id>` delimited at its first blank).
*(`COUSR02C.cbl:368-376`)*

**FR-UU-10 — Save failures.** A rewrite that finds nothing displays `User ID NOT found...`; any other
non-normal response displays `Unable to Update User...`. *(`COUSR02C.cbl:377-389`)*

**FR-UU-11 — Save and exit (quirk Q4).** PF3 runs the same update as PF5 and then returns to
`CDEMO-FROM-PROGRAM` — COUSR00C when the screen was reached from the list, otherwise COADM01C — so any
validation failure or error message produced by that update is discarded unseen.
*(`COUSR02C.cbl:111-119`)*

**FR-UU-12 — Clear.** PF4 blanks the screen. *(`COUSR02C.cbl:120-121`, `:395-411`)*

**FR-UU-13 — Cancel.** PF12 returns to COADM01C without writing. *(`COUSR02C.cbl:124-126`)*

**FR-UU-14 — Invalid key.** Any other AID key displays `Invalid key pressed. Please see below...`.
*(`COUSR02C.cbl:127-130`)*

**FR-UU-15 — The key is immutable.** Only the four data fields can be changed; the user id identifies
the record. *(`COUSR02C.cbl:215-237`)*

**FR-UU-16 — Save against a missing id (quirk Q5).** PF5 with an id that does not exist produces
`User ID NOT found...`: the read fails, the program still runs the comparison and the rewrite, and the
rewrite's `NOTFND` yields the same literal. No record is created. *(`COUSR02C.cbl:215-245`, `:377-383`)*

**FR-UU-17 — No user-type domain check (quirk Q1).** As in CU01, any non-blank character is a valid
user type. *(`COUSR02C.cbl:204-208`)*

**FR-UU-18 — Admin only.** CU02 is reachable from admin-menu option 3 or from a `U` selection on CU00.
*(`app/cpy/COADM02Y.cpy:36-39`)*

## Migrated surface

`GET /api/admin/users/{id}` (fetch, FR-UU-2…FR-UU-5) and `PUT /api/admin/users/{id}`
(save, FR-UU-6…FR-UU-10) → `UserUpdateController` / `UserUpdateService`.
`Press PF5 key to save your updates ...` is rendered by `UpdateUserPage` on a successful fetch.

## Test coverage

| Requirement | Test |
|---|---|
| FR-UU-1 | `UpdateUserPage` `?id=` bootstrap (fetches on mount) |
| FR-UU-2 | `UserUpdateServiceTest.frUU2_fetchWithABlankKey_reportsUserIdCanNotBeEmpty`, `UserUpdateControllerTest.frUU2_fetchWithABlankKey_returns400` |
| FR-UU-3 | `UserUpdateServiceTest.frUU3_fetch_returnsTheStoredRecord`, `UserUpdateControllerTest.frUU3_fetch_returns200WithTheRecord`, `UpdateUserPage` PF5 prompt constant |
| FR-UU-4 | `UserUpdateServiceTest.frUU4_fetchOfAnUnknownId_reportsUserIdNotFound`, `UserUpdateControllerTest.frUU4_fetchOfAnUnknownId_returns404` |
| FR-UU-5 | `UserUpdateServiceMockTest.frUU5_readFailure_reportsUnableToLookupUser` |
| FR-UU-6 | `UserValidatorTest.frUU6_missingUserId_reportedFirst`, `.frUU6_updateEditsFollowFirstLastPasswordType`, `UserUpdateServiceTest.frUU6_editsRunBeforeTheRead`, `UserUpdateControllerTest.frUU6_blankFieldOnUpdate_returns400WithTheLegacyLiteral` |
| FR-UU-7 | `UserUpdateServiceTest.frUU7_changedFields_areRewrittenAndConfirmed`, `.frUU7_everyEditableFieldIsComparedIndependently` |
| FR-UU-8 | `UserUpdateServiceTest.frUU8_noChange_reportsPleaseModifyToUpdate`, `UserUpdateControllerTest.frUU8_unchangedRecord_returns400PleaseModifyToUpdate` |
| FR-UU-9 | `UserUpdateControllerTest.frUU9_changedRecord_returns200AndTheUpdatedConfirmation`, `UserMessagesTest.frUS3_confirmationsQuoteTheIdVerbatim` |
| FR-UU-10 | `UserUpdateServiceMockTest.frUU10_rewriteFailure_reportsUnableToUpdateUser` |
| FR-UU-11 | `UpdateUserPage` PF3 handler (saves, then navigates and drops the outcome — quirk Q4) |
| FR-UU-12 | `UpdateUserPage` PF4 handler |
| FR-UU-13 | `UpdateUserPage` PF12 handler (`navigate('/admin')`, no write) |
| FR-UU-14 | `UpdateUserPage` unsupported-AID branch |
| FR-UU-15 | `UserUpdateServiceTest.frUU15_theUserIdIsNotAnEditableField` |
| FR-UU-16 | `UserUpdateServiceTest.frUU16_updateOfAnUnknownId_reportsUserIdNotFound_andCreatesNothing`, `UserUpdateControllerTest.frUU16_updateOfAnUnknownId_returns404` |
| FR-UU-17 | `UserUpdateServiceTest.frUU17_quirkQ1_anArbitraryUserTypeIsStored` |
| FR-UU-18 | `UserAdminSecurityTest.frUS1_nonAdminIsRefusedOnEveryEndpoint` |

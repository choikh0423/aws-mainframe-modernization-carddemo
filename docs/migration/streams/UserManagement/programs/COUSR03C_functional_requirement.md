# COUSR03C (CU03) — Delete User — functional requirements

Source: `app/cbl/COUSR03C.cbl`, map `app/bms/COUSR03.bms` (`COUSR3A`), file USRSEC
(`app/cpy/CSUSR01Y.cpy`).

## Screen

| Element | Verbatim text | Cite |
|---|---|---|
| Title | `Delete User` | `COUSR03.bms:79` |
| Key field | `Enter User ID:` — 8 chars, the only unprotected field | `:80-89` |
| Display 1 | `First Name:` — 20 chars, `ASKIP` | `:98-107` |
| Display 2 | `Last Name:` — 20 chars, `ASKIP` | `:111-120` |
| Display 3 | `User Type: ` — 1 char, `ASKIP`, `(A=Admin, U=User)` | `:125-139` |
| PF line | `ENTER=Fetch  F3=Back  F4=Clear  F5=Delete` | `:144-148` |

There is no password field on this map.

## Requirements

**FR-UD-1 — Arrival from the list.** When CU03 is entered with `CDEMO-CU03-USR-SELECTED` set (a `D`
selection on CU00) that id is placed in the key field and fetched immediately.
*(`COUSR03C.cbl:96-105`)*

**FR-UD-2 — Fetch needs a key.** ENTER with a blank user id displays `User ID can NOT be empty...`.
*(`COUSR03C.cbl:142-154`)*

**FR-UD-3 — Fetch.** ENTER with a user id blanks the display fields, reads the record and, on success,
shows First Name, Last Name and User Type read-only together with
`Press PF5 key to delete this user ...`. *(`COUSR03C.cbl:156-169`, `:281-286`)*

**FR-UD-4 — Fetch not found.** A read that finds nothing displays `User ID NOT found...`.
*(`COUSR03C.cbl:287-293`)*

**FR-UD-5 — Fetch failure.** Any other non-normal read response displays `Unable to lookup User...`.
*(`COUSR03C.cbl:294-299`)*

**FR-UD-6 — Delete needs a key.** PF5 with a blank user id displays `User ID can NOT be empty...`.
*(`COUSR03C.cbl:174-186`)*

**FR-UD-7 — Delete.** PF5 with a user id reads the record and deletes it; the deletion is
unconditional once the key is non-blank — there is no confirmation prompt (quirk Q7).
*(`COUSR03C.cbl:188-192`, `:305-311`)*

**FR-UD-8 — Delete success.** A successful delete clears the screen fields and displays, in green,
`User <id> has been deleted ...` (`<id>` delimited at its first blank). *(`COUSR03C.cbl:313-322`)*

**FR-UD-9 — Delete not found.** A delete against a missing id displays `User ID NOT found...`.
*(`COUSR03C.cbl:323-329`)*

**FR-UD-10 — Delete failure message (quirk Q3).** Any other non-normal delete response displays
`Unable to Update User...` — the delete screen reuses the update program's literal.
*(`COUSR03C.cbl:330-334`)*

**FR-UD-11 — Back.** PF3 returns to `CDEMO-FROM-PROGRAM` (COUSR00C when reached from the list,
otherwise COADM01C) **without** deleting. *(`COUSR03C.cbl:111-118`)*

**FR-UD-12 — Clear / Cancel.** PF4 blanks the screen; PF12 returns to COADM01C.
*(`COUSR03C.cbl:119-126`, `:341-356`)*

**FR-UD-13 — Invalid key.** Any other AID key displays `Invalid key pressed. Please see below...`.
*(`COUSR03C.cbl:127-130`)*

**FR-UD-14 — No self-protection (quirk Q7).** Nothing prevents deleting the signed-on administrator or
the last remaining administrator. *(`COUSR03C.cbl:174-192`)*

**FR-UD-15 — Admin only.** CU03 is reachable from admin-menu option 4 or from a `D` selection on CU00.
*(`app/cpy/COADM02Y.cpy:41-44`)*

## Migrated surface

`GET /api/admin/users/{id}` (fetch, shared with CU02) and `DELETE /api/admin/users/{id}` →
`UserDeleteController` / `UserDeleteService`. `Press PF5 key to delete this user ...` is rendered by
`DeleteUserPage` on a successful fetch.

## Test coverage

| Requirement | Test |
|---|---|
| FR-UD-1 | `DeleteUserPage` `?id=` bootstrap (fetches on mount) |
| FR-UD-2 | `UserUpdateServiceTest.frUU2_fetchWithABlankKey_reportsUserIdCanNotBeEmpty` (shared fetch), `UserValidatorTest.frUD2_blankKeyIsRejected` |
| FR-UD-3 | `UserUpdateServiceTest.frUU3_fetch_returnsTheStoredRecord` (shared fetch), `DeleteUserPage` read-only fields and PF5 prompt constant |
| FR-UD-4 | `UserUpdateControllerTest.frUU4_fetchOfAnUnknownId_returns404` |
| FR-UD-5 | `UserDeleteServiceMockTest.frUD5_readFailure_reportsUnableToLookupUser` |
| FR-UD-6 | `UserDeleteServiceTest.frUD6_blankKey_reportsUserIdCanNotBeEmpty`, `UserDeleteControllerTest.frUD6_blankKey_returns400` |
| FR-UD-7 | `UserDeleteServiceTest.frUD7_existingUser_isDeletedAndConfirmed` |
| FR-UD-8 | `UserDeleteControllerTest.frUD8_existingUser_returns200AndTheDeletedConfirmation`, `UserMessagesTest.frUS3_confirmationsQuoteTheIdVerbatim` |
| FR-UD-9 | `UserDeleteServiceTest.frUD9_unknownId_reportsUserIdNotFound`, `UserDeleteControllerTest.frUD9_unknownId_returns404` |
| FR-UD-10 | `UserDeleteServiceMockTest.frUD10_deleteFailure_reportsTheUpdateWording`, `UserMessagesTest.frUD10_quirkQ3_deleteFailureKeepsTheUpdateWording` |
| FR-UD-11 | `DeleteUserPage` PF3 handler (no delete) |
| FR-UD-12 | `DeleteUserPage` PF4 / PF12 handlers |
| FR-UD-13 | `DeleteUserPage` unsupported-AID branch |
| FR-UD-14 | `UserDeleteServiceTest.frUD14_quirkQ7_anAdministratorCanBeDeletedWithoutAGuard` |
| FR-UD-15 | `UserAdminSecurityTest.frUS1_nonAdminIsRefusedOnEveryEndpoint` |

# COUSR01C (CU01) — Add User — functional requirements

Source: `app/cbl/COUSR01C.cbl`, map `app/bms/COUSR01.bms` (`COUSR1A`), file USRSEC
(`app/cpy/CSUSR01Y.cpy`).

## Screen

| Element | Verbatim text | Cite |
|---|---|---|
| Title | `Add User` | `COUSR01.bms:79` |
| Field 1 | `First Name:` — 20 chars, unprotected, cursor here on entry | `:80-88` |
| Field 2 | `Last Name:` — 20 chars | `:92-101` |
| Field 3 | `User ID:` — 8 chars, annotated `(8 Char)` | `:106-120` |
| Field 4 | `Password:` — 8 chars, `DRK` (non-display), annotated `(8 Char)` | `:121-135` |
| Field 5 | `User Type: ` — 1 char, annotated `(A=Admin, U=User)` | `:136-150` |
| PF line | `ENTER=Add User  F3=Back  F4=Clear  F12=Exit` | `:155-159` |

## Requirements

**FR-UA-1 — Empty screen on entry.** CU01 opens with all five fields blank and the cursor on First
Name. *(`COUSR01C.cbl:85-88`)*

**FR-UA-2 — Edit order.** ENTER validates the fields in this order and reports only the first failure:

| # | Condition | Message |
|---|---|---|
| 1 | First Name blank | `First Name can NOT be empty...` |
| 2 | Last Name blank | `Last Name can NOT be empty...` |
| 3 | User ID blank | `User ID can NOT be empty...` |
| 4 | Password blank | `Password can NOT be empty...` |
| 5 | User Type blank | `User Type can NOT be empty...` |

*(`COUSR01C.cbl:115-152`)*

**FR-UA-3 — Write.** When all five fields are non-blank a USRSEC record is written with
`SEC-USR-ID`/`FNAME`/`LNAME`/`PWD`/`TYPE` taken from the screen. *(`COUSR01C.cbl:154-160`, `:238-248`)*

**FR-UA-4 — Success.** A successful write clears all five fields and displays, in green,
`User <id> has been added ...` where `<id>` is the user id up to its first blank
(`STRING … DELIMITED BY SPACE`). *(`COUSR01C.cbl:250-259`)*

**FR-UA-5 — Duplicate.** A write against an existing key displays `User ID already exist...` and the
screen keeps the entered values. *(`COUSR01C.cbl:260-266`)*

**FR-UA-6 — Other write failure.** Any other non-normal response displays `Unable to Add User...`.
*(`COUSR01C.cbl:267-272`)*

**FR-UA-7 — Back.** PF3 returns to COADM01C. *(`COUSR01C.cbl:93-95`)*

**FR-UA-8 — Clear.** PF4 blanks all five fields and the message, cursor back on First Name.
*(`COUSR01C.cbl:96-97`, `:279-293`)*

**FR-UA-9 — Invalid key (quirk Q10).** Any other AID key — **including PF12, which the PF line
advertises as `F12=Exit`** — displays `Invalid key pressed. Please see below...`.
*(`COUSR01C.cbl:90-103`)*

**FR-UA-10 — No user-type domain check (quirk Q1).** Any single non-blank character is accepted as the
user type and written to the record; `a`, `x` and `9` are all valid despite the `(A=Admin, U=User)`
caption. *(`COUSR01C.cbl:142-146`)*

**FR-UA-11 — No id/password rules (quirk Q2).** The user id is not checked for length, case or
character set, and the password has no format or strength rule; the password is stored in clear text.
*(`COUSR01C.cbl:115-160`, `app/cpy/CSUSR01Y.cpy:21`)*

**FR-UA-12 — Field widths.** Values are held in the record as `PIC X(20)`/`X(20)`/`X(8)`/`X(8)`/`X(1)`;
the 3270 map cannot deliver more than those widths. *(`app/cpy/CSUSR01Y.cpy:17-23`, `COUSR01.bms:84-149`)*

**FR-UA-13 — Admin only.** CU01 is reachable only from admin-menu option 2.
*(`app/cpy/COADM02Y.cpy:31-34`)*

## Migrated surface

`POST /api/admin/users` → `UserAddController` / `UserAddService` / `UserValidator`.
201 with `{userId, message}` on success; 400 for FR-UA-2 with the exact literal; 409 for FR-UA-5.

## Test coverage

| Requirement | Test |
|---|---|
| FR-UA-1 | `AddUserPage` initial blank state |
| FR-UA-2 | `UserValidatorTest.frUA2_*` (five literals in order + the passing case), `UserAddServiceTest.frUA2_theFirstFailingEditStopsTheWrite`, `UserAddControllerTest.frUA2_blankFirstName_returns400WithTheLegacyLiteral`, `.frUA2_blankUserType_returns400WithTheLegacyLiteral` |
| FR-UA-3 | `UserAddServiceTest.frUA3_validRequest_writesTheRecordAndConfirms` |
| FR-UA-4 | `UserAddControllerTest.frUA4_validRequest_returns201AndTheAddedConfirmation`, `UserMessagesTest.frUS3_confirmationsQuoteTheIdVerbatim`, `.frUS3_quirkQ12_idIsDelimitedByTheFirstSpace` |
| FR-UA-5 | `UserAddServiceTest.frUA5_existingId_isRejectedAsDuplicate`, `UserAddControllerTest.frUA5_duplicateId_returns409UserIdAlreadyExist` |
| FR-UA-6 | `UserAddServiceMockTest.frUA6_writeFailure_reportsUnableToAddUser` |
| FR-UA-7 | `AddUserPage` PF3 handler (`navigate('/admin')`) |
| FR-UA-8 | `AddUserPage` PF4 handler (blanks the five fields and the message) |
| FR-UA-9 | `AddUserPage` unsupported-AID branch, including F12 (quirk Q10) |
| FR-UA-10 | `UserValidatorTest.frUA10_anyNonBlankUserTypeIsAccepted_noDomainCheck`, `UserAddServiceTest.frUA10_quirkQ1_anArbitraryUserTypeIsStored` |
| FR-UA-11 | `UserValidatorTest.frUA11_shortIdAndWeakPasswordAreAccepted_noFormatCheck` |
| FR-UA-12 | `UserAddServiceTest.frUA12_overlongFieldsAreTruncatedToThePicture`, `UserFieldsTest.frUS2_*` |
| FR-UA-13 | `UserAdminSecurityTest.frUS1_nonAdminIsRefusedOnEveryEndpoint` |

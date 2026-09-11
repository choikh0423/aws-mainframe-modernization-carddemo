# S-05 UserManagement — functional requirements

The business sign-off oracle for the stream. Stream-wide requirements are numbered `FR-US-n`; the
per-screen requirements live in `programs/` and are listed here by identifier:

* `programs/COUSR00C_functional_requirement.md` — **FR-UL-1 … FR-UL-18** (CU00, List Users)
* `programs/COUSR01C_functional_requirement.md` — **FR-UA-1 … FR-UA-13** (CU01, Add User)
* `programs/COUSR02C_functional_requirement.md` — **FR-UU-1 … FR-UU-18** (CU02, Update User)
* `programs/COUSR03C_functional_requirement.md` — **FR-UD-1 … FR-UD-15** (CU03, Delete User)

Every requirement cites its source lines and is covered by at least one automated test; each program
document ends with its requirement-to-test table.

---

## Stream-wide requirements

**FR-US-1 — Administrator only.** All four screens are reachable only through the admin menu COADM01C
options 1-4, so only a user whose `SEC-USR-TYPE` is `A` can list, add, update or delete users.
*(`app/cpy/COADM02Y.cpy:26-44`)*
Migrated: every endpoint sits under `/api/admin/users`, which
`com.carddemo.common.security.SecurityConfig` restricts to `hasRole("ADMIN")`; sign-on grants
`ROLE_ADMIN` only for type `A`. A signed-on non-admin gets 403, an anonymous caller 401.

**FR-US-2 — Record shape.** A user consists of exactly the five USRSEC fields — user id `X(8)` (the
key), first name `X(20)`, last name `X(20)`, password `X(8)`, user type `X(1)` — and nothing else. A
value longer than its picture is truncated to it (COBOL `MOVE` semantics), by position: a blank the
operator typed ahead of the value occupies a character of the field and is kept, and the cut is made
at the picture width, not after the leading blanks are removed. Only trailing blanks are
insignificant. *(`app/cpy/CSUSR01Y.cpy:17-23`)*

**FR-US-3 — Messages are verbatim.** Every message the stream can show is one of the following
literals, reproduced character for character. No other user-facing text is produced.
*(source cites in the program documents; `Invalid key pressed. Please see below...` is
`app/cpy/CSMSG01Y.cpy:21-22`)*

| Message | Screens |
|---|---|
| `First Name can NOT be empty...` | CU01, CU02 |
| `Last Name can NOT be empty...` | CU01, CU02 |
| `User ID can NOT be empty...` | CU01, CU02, CU03 |
| `Password can NOT be empty...` | CU01, CU02 |
| `User Type can NOT be empty...` | CU01, CU02 |
| `User ID already exist...` | CU01 |
| `Unable to Add User...` | CU01 |
| `User <id> has been added ...` | CU01 |
| `User ID NOT found...` | CU02, CU03 |
| `Unable to lookup User...` | CU00, CU02, CU03 |
| `Press PF5 key to save your updates ...` | CU02 |
| `Please modify to update ...` | CU02 |
| `User <id> has been updated ...` | CU02 |
| `Unable to Update User...` | CU02, **CU03** (quirk Q3) |
| `Press PF5 key to delete this user ...` | CU03 |
| `User <id> has been deleted ...` | CU03 |
| `Invalid selection. Valid values are U and D` | CU00 |
| `You are already at the top of the page...` | CU00 |
| `You are already at the bottom of the page...` | CU00 |
| `You are at the top of the page...` | CU00 |
| `You have reached the bottom of the page...` | CU00 |
| `You have reached the top of the page...` | CU00 |
| `Invalid key pressed. Please see below...` | CU00, CU01, CU02, CU03 |

**FR-US-4 — Screen fidelity.** Each migrated screen reproduces its map's title, field labels, field
order, field widths, annotations (`(8 Char)`, `(A=Admin, U=User)`), the CU00 instruction line and the
row-24 PF-key line verbatim; the CU02 password field is non-display in the legacy map (`DRK`) and is
rendered as a password input; the CU03 data fields are `ASKIP` and are rendered read-only.
*(`app/bms/COUSR00.bms`, `COUSR01.bms`, `COUSR02.bms`, `COUSR03.bms`)*

**FR-US-5 — Navigation.** PF3 from CU00/CU01 returns to the admin menu; PF3 from CU02/CU03 returns to
the screen the user came from (CU00 when a row was selected there, otherwise the admin menu); PF12 on
CU02/CU03 always returns to the admin menu; selecting `U`/`D` on CU00 navigates to CU02/CU03 with the
selected id. *(`COUSR00C.cbl:125-127`, `:189-213`; `COUSR01C.cbl:93-95`; `COUSR02C.cbl:111-126`;
`COUSR03C.cbl:111-126`)*

**FR-US-6 — Unauthenticated access.** A screen entered with no COMMAREA transfers to the sign-on
program COSGN00C. *(`COUSR00C.cbl:105-107` and equivalents)* Migrated: an unauthenticated request is
rejected and the React shell routes to the sign-on page.

**FR-US-7 — Write ownership.** This stream owns every create, update and delete against the user
store; sign-on only reads it. No other behaviour of the user store is changed.
*(`COUSR01C.cbl:240`, `COUSR02C.cbl:360`, `COUSR03C.cbl:307`)*

**FR-US-8 — Quirks are preserved.** The behaviours catalogued as Q1-Q13 in
`UserManagement_analysis.md` are reproduced, not corrected. The load-bearing ones for sign-off:
no user-type domain check (Q1), clear-text passwords with no format rules (Q2), the
`Unable to Update User...` literal on the delete screen (Q3), PF3 on CU02 saving silently (Q4), and
CU00 honouring only the first selection flag (Q8).

---

## Test coverage (stream-wide requirements)

| Requirement | Test |
|---|---|
| FR-US-1 | `UserAdminSecurityTest.frUS1_anonymousCallerIsRefused`, `.frUS1_nonAdminIsRefusedOnEveryEndpoint`, `.frUS1_adminIsAdmitted` |
| FR-US-2 | `UserFieldsTest.frUS2_valueLongerThanThePictureIsTruncated`, `.frUS2_trailingPaddingIsNotSignificant`, `.frUS2_leadingBlanksOccupyPositionsAndAreKept`, `.frUS2_spacesOrLowValuesAreBlank`, `UserAddServiceTest.frUA12_overlongFieldsAreTruncatedToThePicture` |
| FR-US-3 | `UserMessagesTest.frUS3_confirmationsQuoteTheIdVerbatim`, `.frUS3_quirkQ12_idIsDelimitedByTheFirstSpace`, `.frUS3_boundaryAndSelectionLiteralsAreVerbatim`, `.frUD10_quirkQ3_deleteFailureKeepsTheUpdateWording`, plus the per-literal assertions in the service and controller tests |
| FR-US-4 | The four `pages/user/*Page.js` components (labels, field order, annotations, PF line, password input, read-only CU03 fields) |
| FR-US-5 | `pages/user/selection.js` + `selection.test.js` (CU00 `U`/`D` routing) and the PF3/PF12 handlers in the four pages |
| FR-US-6 | `UserAdminSecurityTest.frUS1_anonymousCallerIsRefused` and the shell's sign-on redirect |
| FR-US-7 | `UserAddServiceTest`, `UserUpdateServiceTest`, `UserDeleteServiceTest` (all writes go through the shared `SecUserRepository`; no sign-on code is touched) |
| FR-US-8 | `UserAddServiceTest.frUA10_quirkQ1_anArbitraryUserTypeIsStored` (Q1), `UserValidatorTest.frUA11_shortIdAndWeakPasswordAreAccepted_noFormatCheck` (Q2), `UserMessagesTest.frUD10_quirkQ3_deleteFailureKeepsTheUpdateWording` (Q3), `UpdateUserPage` PF3 handler (Q4), `UserUpdateServiceTest.frUU16_*` (Q5), `UserDeleteServiceTest.frUD14_*` (Q7), `selection.test.js` (Q8), `UserMessagesTest.frUS3_quirkQ12_idIsDelimitedByTheFirstSpace` (Q12) |

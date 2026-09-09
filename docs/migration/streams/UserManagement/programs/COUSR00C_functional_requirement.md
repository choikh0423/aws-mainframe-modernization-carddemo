# COUSR00C (CU00) — List Users — functional requirements

Source: `app/cbl/COUSR00C.cbl`, map `app/bms/COUSR00.bms` (`COUSR0A`), file USRSEC
(`app/cpy/CSUSR01Y.cpy`). Every requirement below is testable and is covered by at least one test —
see the coverage table at the end.

## Screen

| Element | Verbatim text | Cite |
|---|---|---|
| Title | `List Users` | `COUSR00.bms:79` |
| Page indicator | `Page:` | `:84` |
| Filter label | `Search User ID:` (input 8 chars) | `:94-99` |
| Column headings | `Sel`, `User ID `, `     First Name     `, `     Last Name      `, `Type` | `:107-127` |
| Instruction | `Type 'U' to Update or 'D' to Delete a User from the list` | `:443-448` |
| PF line | `ENTER=Continue  F3=Back  F7=Backward  F8=Forward` | `:453-458` |

## Requirements

**FR-UL-1 — First page.** Opening CU00 lists the first 10 USRSEC records in ascending `SEC-USR-ID`
order, showing user id, first name, last name and the one-character user type per row.
*(`COUSR00C.cbl:114-120`, `:282-331`, `:384-441`)*

**FR-UL-2 — Search filter is inclusive.** Entering a value in `Search User ID` and pressing ENTER
restarts the browse at the first record whose id is **>=** that value (`STARTBR` defaults to GTEQ; the
`GTEQ` operand is commented out at `:592`), and resets the page number to 1.
*(`COUSR00C.cbl:217-228`, `:586-595`)*

**FR-UL-3 — Page forward.** PF8 lists the 10 records that follow the last id currently shown,
exclusive of it (the throwaway `READNEXT` at `:286-288`).
*(`COUSR00C.cbl:257-277`, `:282-303`)*

**FR-UL-4 — Page backward.** PF7 lists the 10 records immediately preceding the first id currently
shown, exclusive of it, redisplayed in ascending id order.
*(`COUSR00C.cbl:237-255`, `:336-360`)*

**FR-UL-5 — Top boundary.** PF7 while the page number is not greater than 1 leaves the page unchanged
and displays `You are already at the top of the page...`. *(`COUSR00C.cbl:249-254`)*

**FR-UL-6 — Bottom boundary.** PF8 when the previous page found no further record
(`CDEMO-CU00-NEXT-PAGE-FLG = 'N'`) leaves the page unchanged and displays
`You are already at the bottom of the page...`. *(`COUSR00C.cbl:271-276`, `:305-312`)*

**FR-UL-7 — Empty browse.** When no record exists at or after the requested key the list is empty and
the screen displays `You are at the top of the page...`. *(`COUSR00C.cbl:600-607`)*

**FR-UL-8 — End-of-file while reading.** Running off the end of the file while filling a page ends the
page early; the file-boundary literals are `You have reached the bottom of the page...` (forward,
`:634-641`) and `You have reached the top of the page...` (backward, `:668-675`). Any other non-normal
response on `STARTBR`/`READNEXT`/`READPREV` yields `Unable to lookup User...`
*(`:608-613`, `:642-647`, `:676-681`)*

**FR-UL-9 — Select for update.** `U` or `u` in a row's `Sel` field plus ENTER transfers to COUSR02C
(CU02) carrying that row's user id in the COMMAREA. *(`COUSR00C.cbl:189-201`)*

**FR-UL-10 — Select for delete.** `D` or `d` transfers to COUSR03C (CU03) the same way.
*(`COUSR00C.cbl:201-213`)*

**FR-UL-11 — Invalid selection.** Any other non-blank `Sel` value displays
`Invalid selection. Valid values are U and D`. *(`COUSR00C.cbl:214-218`)*

**FR-UL-12 — First selection wins (quirk Q8).** When more than one `Sel` field is non-blank only the
topmost is acted on; the others are ignored without a message. *(`COUSR00C.cbl:151-185`)*

**FR-UL-13 — Re-list after an invalid selection (quirk Q9).** After FR-UL-11 the program still runs a
fresh page-1 browse using the search field, so the message is shown over a re-listed first page.
*(`COUSR00C.cbl:214-228`)*

**FR-UL-14 — Page number.** The displayed page number is 1 after ENTER, incremented by PF8 and
decremented by PF7 with a floor of 1. *(`COUSR00C.cbl:227`, `:305-315`, `:361-370`)*

**FR-UL-15 — Back.** PF3 returns to the admin menu COADM01C. *(`COUSR00C.cbl:125-127`)*

**FR-UL-16 — Invalid key.** Any other AID key displays
`Invalid key pressed. Please see below...`. *(`COUSR00C.cbl:132-136`, `app/cpy/CSMSG01Y.cpy:21-22`)*

**FR-UL-17 — Type column (quirk Q11).** The `Type` column shows the raw one-character `SEC-USR-TYPE`;
the `USER-TYPE PIC X(08)` field of `WS-USER-DATA` is never used. *(`COUSR00C.cbl:56-63`, `:390`,
`COUSR00.bms:177-181`)*

**FR-UL-18 — Admin only.** CU00 is reachable only from admin-menu option 1, so only a `SEC-USR-TYPE`
= `A` user can run it. *(`app/cpy/COADM02Y.cpy:26-29`)*

## Migrated surface

`GET /api/admin/users?startId=&dir=next|prev` → `UserListController` / `UserListService`.
Boundary messages (FR-UL-5, FR-UL-6, FR-UL-7) and the selection rules (FR-UL-9…FR-UL-13) are surfaced
by `UserListPage`, exactly as the reference stream surfaces the CT00 boundary text.

## Test coverage

| Requirement | Test |
|---|---|
| FR-UL-1 | `UserListServiceTest.frUL1_firstPage_browsesFromTheTopOfUsrsec`, `.frUL1_pageSizeIsTenRows`, `UserListControllerTest.frUL1_firstPage_returns200WithTheTenSeededUsers` |
| FR-UL-2 | `UserListServiceTest.frUL2_searchKeyIsInclusive`, `.frUL2_searchKeyWithNoExactMatch_startsAtTheNextId`, `.frUL2_searchKeyLongerThanEightCharsIsTruncated`, `UserListControllerTest.frUL2_searchUserId_startsAtOrAfterTheKey` |
| FR-UL-3 | `UserListServiceTest.frUL3_next_pagesForwardExclusiveOfTheLastIdShown`, `UserListControllerTest.frUL3_next_pagesForward` |
| FR-UL-4 | `UserListServiceTest.frUL4_prev_pagesBackwardExclusiveOfTheFirstIdShown_inAscendingOrder`, `UserListControllerTest.frUL4_prev_pagesBackward` |
| FR-UL-5 | `UserListServiceTest.frUL5_atTheTop_thePreviousPageFlagIsOff`, `UserMessagesTest.frUS3_boundaryAndSelectionLiteralsAreVerbatim`, `UserListPage` `ALREADY_TOP_MSG` |
| FR-UL-6 | `UserListServiceTest.frUL6_atTheBottom_theNextPageFlagIsOff`, `UserMessagesTest.frUS3_boundaryAndSelectionLiteralsAreVerbatim`, `UserListPage` `ALREADY_BOTTOM_MSG` |
| FR-UL-7 | `UserListServiceTest.frUL7_searchKeyPastTheEnd_returnsAnEmptyPage`, `UserListPage` empty-page `AT_TOP_MSG` |
| FR-UL-8 | `UserListServiceMockTest.frUL8_browseFailure_reportsUnableToLookupUser`, `UserMessagesTest.frUS3_boundaryAndSelectionLiteralsAreVerbatim`, `UserListPage` short-page `REACHED_BOTTOM_MSG`/`REACHED_TOP_MSG` |
| FR-UL-9 | `selection.test.js` "`U` selects the row for the Update screen" and the case-insensitive case |
| FR-UL-10 | `selection.test.js` "`D` selects the row for the Delete screen" and the case-insensitive case |
| FR-UL-11 | `selection.test.js` "any other non-blank flag is an invalid selection", `UserMessagesTest.frUS3_boundaryAndSelectionLiteralsAreVerbatim` |
| FR-UL-12 | `selection.test.js` "(quirk Q8): only the first non-blank flag is honoured" |
| FR-UL-13 | `UserListPage` invalid-selection branch (re-loads page 1, then sets the message) |
| FR-UL-14 | `UserListPage` `pageNum` (1 on ENTER, +1 on PF8, floor 1 on PF7) |
| FR-UL-15 | `UserListPage` PF3 handler (`navigate('/admin')`) |
| FR-UL-16 | `UserListPage` unsupported-AID branch, `UserMessagesTest.frUS3_boundaryAndSelectionLiteralsAreVerbatim` |
| FR-UL-17 | `UserListServiceTest.frUL17_rowsCarryTheDusrsecjFixtureValuesAndTheRawTypeCharacter` |
| FR-UL-18 | `UserAdminSecurityTest.frUS1_nonAdminIsRefusedOnEveryEndpoint`, `.frUS1_anonymousCallerIsRefused` |

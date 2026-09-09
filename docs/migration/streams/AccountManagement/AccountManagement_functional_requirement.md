# Account Management (S-02) — Functional Requirements

**Stream:** S-02 AccountManagement — `CAVW` (COACTVWC) and `CAUP` (COACTUPC)
**Role:** business sign-off oracle. Every FR is a trigger → user-visible result, cited to legacy source, and covered by at least one automated test (see §D).
Per-program detail: [`programs/COACTVWC_functional_requirement.md`](programs/COACTVWC_functional_requirement.md), [`programs/COACTUPC_functional_requirement.md`](programs/COACTUPC_functional_requirement.md).

Message text below is **verbatim** from the COBOL literals, including double spaces and missing spaces before punctuation.

---

## A. CAVW — View Account

| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-AV-01 | Open the View Account screen | Empty screen, info line `Enter or update id of account to display`, title `View Account`, PF line `F3=Exit` | COACTVWC:110-111, 470-520; COACTVW.bms |
| FR-AV-02 | Enter a valid 11-digit account id | All account and customer fields are displayed and the info line reads `Displaying details of given Account` | COACTVWC:112-113, 687-715, 880-1050 |
| FR-AV-03 | Press ENTER with a blank account id | `No input received` | COACTVWC:600-620, 649-658 |
| FR-AV-04 | Enter a non-numeric account id | `Account Filter must  be a non-zero 11 digit number` | COACTVWC:660-676 |
| FR-AV-05 | Enter an all-zero account id | `Account Filter must  be a non-zero 11 digit number` | COACTVWC:660-676 |
| FR-AV-06 | Enter an account id with no cross-reference row | `Account:<11-digit id> not found in Cross ref file.  Resp:000000013  Reas:0000` | COACTVWC:735-757 |
| FR-AV-07 | Enter an account id in the xref but not in ACCTDAT | `Account:<11-digit id> not found in Acct Master file.Resp:000000013  Reas:0000` | COACTVWC:786-806 |
| FR-AV-08 | Have an account whose customer is missing from CUSTDAT | `CustId:<9-digit id> not found in customer master.Resp: 000000013  REAS:0000000` | COACTVWC:837-858 |
| FR-AV-09 | See amounts | Balance, credit limit, cash credit limit, cycle credit and cycle debit render as `+ZZZ,ZZZ,ZZZ.99` (e.g. `+     -197.13`, `+    5,000.00`) | COACTVW.bms:120,141,162,174,195 |
| FR-AV-10 | See the SSN | Rendered `999-99-9999` | COACTVWC:1010-1017 |
| FR-AV-11 | See the account id echoed | Zero-padded to 11 digits | COACTVWC:880-890 |
| FR-AV-12 | Press F3 | Return to the caller screen, or the main menu (`CM00`/`COMEN01C`) when there is no caller | COACTVWC:472-520 |
| FR-AV-13 | Press any other key | Treated as ENTER (no "invalid key" message on this screen) | COACTVWC:441-470 |

## B. CAUP — Update Account

### B.1 Search and fetch
| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-AU-01 | Open the Update Account screen | Only the account id is enterable; info line `Enter or update id of account to update`; PF line `ENTER=Process F3=Exit`, `F5=Save`, `F12=Cancel` | COACTUPC:2955-2977, 2986-3006; COACTUP.bms |
| FR-AU-02 | Enter a valid account id | Account + customer details are shown in editable fields, info line `Update account details presented above.` | COACTUPC:2562-2580, 2787-2869, 2962-2963 |
| FR-AU-03 | Press ENTER with a blank account id | `No input received` | COACTUPC:1441-1443, 1787-1797 |
| FR-AU-04 | Enter a non-numeric or all-zero account id | `Account Number if supplied must be a 11 digit Non-Zero Number` | COACTUPC:1799-1813 |
| FR-AU-05 | Enter an account id absent from the xref / ACCTDAT / CUSTDAT | The corresponding CAVW-style not-found message (same text as FR-AV-06…08) | COACTUPC:3650-3800 |

### B.2 Change detection and confirmation
| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-AU-06 | Press ENTER without changing anything | `No change detected with respect to values fetched.` and the details stay on screen | COACTUPC:1460-1468, 1681-1782 |
| FR-AU-07 | Change a field to a different case only (status, group id, names, address, country, government id) | Still counts as **no change** — those comparisons are case-insensitive (and `TRIM`-ed for the customer fields) | COACTUPC:1684-1782 |
| FR-AU-08 | Submit valid changes | Info line `Changes validated.Press F5 to save`; all fields become protected until F5 or F12 | COACTUPC:1671-1675, 2966-2967, 3001-3003 |
| FR-AU-09 | Press F5 from the confirmation state | Account and customer rows are updated, info line `Changes committed to database` | COACTUPC:2596-2606, 3888-4107 |
| FR-AU-10 | Press F12 after details were fetched | The originally-stored values are re-read and shown again, discarding my edits | COACTUPC:2565-2580, 901-916 |
| FR-AU-11 | Press F5 outside the confirmation state | The key is ignored and treated as ENTER | COACTUPC:901-916 |
| FR-AU-12 | Press F3 | Return to the caller screen or `CM00`/`COMEN01C` | COACTUPC:921-959 |

### B.3 Validation — order and first-error rule
| ID | Rule | Result | Source cite |
|----|------|--------|-------------|
| FR-AU-13 | Fields are edited in the fixed order: Account Status, Open Date, Credit Limit, Expiry Date, Cash Credit Limit, Reissue Date, Current Balance, Current Cycle Credit Limit, Current Cycle Debit Limit, SSN, Date of Birth, FICO Score, First Name, Middle Name, Last Name, Address Line 1, State, Zip, City, Country, Phone Number 1, Phone Number 2, EFT Account Id, Primary Card Holder, then the State/Zip cross-edit | Only the **first** failing edit produces a message; later failures are suppressed (`IF WS-RETURN-MSG-OFF`) | COACTUPC:1472-1669 |
| FR-AU-14 | Account Status | blank → `Account Status must be supplied.`; not Y/N → `Account Status must be Y or N.` | COACTUPC:1472-1476, 1856-1892 |
| FR-AU-15 | Open Date / Expiry Date / Reissue Date / Date of Birth | Full `CCYYMMDD` edit: `<field> : Year must be supplied.`, `<field> must be 4 digit number.`, `<field> : Century is not valid.` (only 19xx/20xx), `<field> : Month must be supplied.`, `<field>: Month must be a number between 1 and 12.`, `<field> : Day must be supplied.`, `<field>:day must be a number between 1 and 31.`, `<field>:Cannot have 31 days in this month.`, `<field>:Cannot have 30 days in this month.`, `<field>:Not a leap year.Cannot have 29 days in this month.` | CSUTLDPY:18-367; COACTUPC:1478-1543 |
| FR-AU-16 | Date of Birth in the future | `Date of Birth:cannot be in the future` | CSUTLDPY:329-367 |
| FR-AU-17 | Credit Limit / Cash Credit Limit / Current Balance / Current Cycle Credit Limit / Current Cycle Debit Limit | blank → `<field> must be supplied.`; not a valid signed decimal → `<field> is not valid` | COACTUPC:1484-1527, 2180-2219 |
| FR-AU-18 | SSN | per part: `SSN: First 3 chars must be supplied.` / `must be all numeric.` / `must not be zero.`, then `SSN: First 3 chars: should not be 000, 666, or between 900 and 999`; `SSN 4th & 5th chars …`; `SSN Last 4 chars …` | COACTUPC:1529-1531, 2431-2492 |
| FR-AU-19 | FICO Score | non-numeric → `FICO Score must be all numeric.`; outside 300-850 → `FICO Score : should be between 300 and 850` | COACTUPC:1545-1556, 2514-2533 |
| FR-AU-20 | First Name / Last Name / City / Country / State | blank → `<field> must be supplied.`; contains anything other than letters and spaces → `<field> can have alphabets only.` | COACTUPC:1560-1630, 1898-1950 |
| FR-AU-21 | Middle Name | Optional: blank passes; non-alphabetic → `Middle Name can have alphabets only.` | COACTUPC:1568-1574, 2012-2060 |
| FR-AU-22 | Address Line 1 | blank → `Address Line 1 must be supplied.` (no character-set edit) | COACTUPC:1584-1590, 1824-1851 |
| FR-AU-23 | State | Must be a real US state/territory code from `CSLKPCDY`, else `State : is not a valid state code` | COACTUPC:1592-1602, 2493-2509 |
| FR-AU-24 | Zip | blank → `Zip must be supplied.`; non-numeric → `Zip must be all numeric.`; zero → `Zip must not be zero.` | COACTUPC:1605-1611, 2109-2174 |
| FR-AU-25 | State + Zip cross-edit | When both are individually valid, `state‖zip(1:2)` must be a known combination, else `Invalid zip code for state` | COACTUPC:1664-1669, 2536-2556 |
| FR-AU-26 | Phone Number 1 / 2 | All three parts blank → accepted (phone is optional). Otherwise: `<field>: Area code must be supplied.` / `: Area code must be A 3 digit number.` / `: Area code cannot be zero` / `: Not valid North America general purpose area code`; the same three edits for `Prefix code` and for `Line number code` | COACTUPC:1632-1646, 2225-2426 |
| FR-AU-27 | EFT Account Id | blank → `EFT Account Id must be supplied.`; non-numeric → `EFT Account Id must be all numeric.`; zero → `EFT Account Id must not be zero.` | COACTUPC:1648-1655, 2109-2174 |
| FR-AU-28 | Primary Card Holder | blank → `Primary Card Holder must be supplied.`; not Y/N → `Primary Card Holder must be Y or N.` | COACTUPC:1657-1662, 1856-1892 |
| FR-AU-29 | Any validation failure | Info line stays `Update account details presented above.` and the entered values are kept for correction | COACTUPC:1470, 2964-2965, 2870-2948 |

### B.4 Save, concurrency and failure
| ID | Rule | Result | Source cite |
|----|------|--------|-------------|
| FR-AU-30 | Save writes the account row first, then the customer row | Both rows carry the new values; dates are stored `YYYY-MM-DD`, phones `(AAA)PPP-LLLL`, SSN as a 9-digit number | COACTUPC:3955-4062 |
| FR-AU-31 | Someone else changed the account or customer row between fetch and F5 | Nothing is written; `Record changed by some one else. Please review` and the freshly-read details are shown | COACTUPC:3945-3953, 4109-4202 |
| FR-AU-32 | The account row cannot be locked | `Could not lock account record for update`, info line `Changes unsuccessful. Please try again` | COACTUPC:3895-3916, 2971-2974 |
| FR-AU-33 | The customer row cannot be locked | `Could not lock customer record for update`, info line `Changes unsuccessful. Please try again` | COACTUPC:3920-3944 |
| FR-AU-34 | A rewrite fails | `Update of record failed` and the whole unit of work is rolled back (`SYNCPOINT ROLLBACK`) — neither row is changed | COACTUPC:4065-4103 |
| FR-AU-35 | Press ENTER after a successful save | The screen returns to the details view of the same account (re-read) | COACTUPC:2607-2620 |
| FR-AU-36 | Press ENTER after a lock/update failure | The screen resets to the fresh-search state | COACTUPC:2621-2640 |

## C. Preserved legacy quirks (do **not** "fix")
| ID | Quirk | Cite |
|----|-------|------|
| FR-AQ-01 | A blank account filter shows `No input received`, never the declared `Account number not provided` — the blank branch's message is immediately overwritten by the caller | COACTVWC:600-620; COACTUPC:1441-1443 |
| FR-AQ-02 | The friendly 88-levels `Did not find this account in account card xref file` / `… in account master file` / `Did not find associated customer in master file` are declared in both programs but never used; the runtime messages embed the id, `Resp:` and `Reas:` values instead | COACTVWC:126-138; COACTUPC:498-504 |
| FR-AQ-03 | `COACTUPC` declares the 88-level `DID-NOT-FIND-ACCT-IN-CARDXREF` **twice**, with two different texts (`Did not find this account in account card xref file` and `Did not find this account in cards database`); both are dead code | COACTUPC:498-500, 512-513 |
| FR-AQ-04 | CAVW and CAUP use different texts for the same invalid-account-id condition (`Account Filter must  be a non-zero 11 digit number` vs `Account Number if supplied must be a 11 digit Non-Zero Number`) | COACTVWC:669-673; COACTUPC:1806-1810 |
| FR-AQ-05 | The stale-record check slices the stored DOB at `(1:4)(6:2)(9:2)` but the snapshot at `(1:4)(5:2)(7:2)` because the snapshot is `CCYYMMDD` while the record is `CCYY-MM-DD` | COACTUPC:4174-4179 |
| FR-AQ-06 | `'*'` typed into any update field means "not supplied" — it is converted to `LOW-VALUES` on receive | COACTUPC:1051-1428 |
| FR-AQ-07 | Zip is edited as a *number* (`must not be zero`), so `00000` is rejected with `Zip must not be zero.` rather than a zip-format message | COACTUPC:1605-1611 |
| FR-AQ-08 | The account's own `ACCT-ADDR-ZIP` column exists on ACCTDAT but appears on neither map and is never updated | CVACT01Y; COACTUPC:3955-4000 |

## D. Traceability (FR ↔ test)
| FR | Automated test |
|----|----------------|
| FR-AV-01, FR-AV-13 | `AccountViewControllerTest` (screen contract asserted through the response DTO), `AccountViewPage` route entry |
| FR-AV-02, FR-AV-09…11 | `AccountViewServiceTest#returnsEditedFields`, `AccountViewControllerTest#viewReturnsDetails`, `AccountManagementE2EIntegrationTest` |
| FR-AV-03…05 | `AccountFilterValidatorTest`, `AccountViewServiceTest#blank/nonNumeric/zero` |
| FR-AV-06…08 | `AccountViewServiceTest#xrefMissing/acctMissing/custMissing`, `AccountViewControllerTest` |
| FR-AV-12 | Route registry + `AccountViewPage` PF-key handler (frontend) |
| FR-AU-01…05 | `AccountUpdateServiceTest#fetch*`, `AccountUpdateControllerTest#fetch*` |
| FR-AU-06, FR-AU-07 | `AccountChangeDetectorTest`, `AccountUpdateServiceTest#noChangeDetected` |
| FR-AU-08…12 | `AccountUpdateServiceTest#validateReturnsConfirmationState`, `#saveRequiresConfirmedState`, `AccountUpdateControllerTest` |
| FR-AU-13 | `AccountUpdateValidatorTest#firstErrorWins`, `#validationOrder` |
| FR-AU-14…28 | `AccountUpdateValidatorTest` (one case per rule), `AccountDateValidatorTest`, `UsLookupTablesTest` |
| FR-AU-29 | `AccountUpdateControllerTest#validationFailureKeepsValues` |
| FR-AU-30 | `AccountUpdateServiceTest#savePersistsBothRows`, `AccountManagementE2EIntegrationTest` |
| FR-AU-31 | `AccountUpdateServiceTest#staleAccountRecord`, `#staleCustomerRecord` |
| FR-AU-32, FR-AU-33 | `AccountUpdateServiceTest#lockFailure*` |
| FR-AU-34 | `AccountUpdateServiceTest#rewriteFailureRollsBack` |
| FR-AU-35, FR-AU-36 | `AccountUpdateStateTest` |
| FR-AQ-01…08 | `AccountQuirkTest` (one assertion per quirk) |

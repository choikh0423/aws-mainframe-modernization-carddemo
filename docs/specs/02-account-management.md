# 02 — Account Management (CAVW / CAUP) — Functional & Technical Specification

## 1. Header

**Stream:** Account Management (stream #2 in [`CardDemo-Stream-Map.md`](../../migration/transaction-management/docs/CardDemo-Stream-Map.md))
**SOURCE:** `choikh0423/aws-mainframe-modernization-carddemo` @ `7fd2344fe22ea20b31dc8ae0d662d9379ec7d284`
**Source files (read in full):** `app/cbl/COACTVWC.cbl` (941 lines), `app/cbl/COACTUPC.cbl` (4,236 lines); `app/bms/COACTVW.bms`, `app/bms/COACTUP.bms`; `app/cpy/CVACT01Y.cpy`, `CVACT03Y.cpy`, `CVCUS01Y.cpy`, `CSLKPCDY.cpy`, `CSUTLDWY.cpy`, `CSUTLDPY.cpy`, `CSSETATY.cpy`, `CSSTRPFY.cpy`, `CODATECN.cpy`. Supporting copybooks consulted for context only: `COCOM01Y`, `CVCRD01Y`, `CSDAT01Y`, `CSMSG02Y`, `COTTL01Y`, `COMEN02Y`; CSD `app/csd/CARDDEMO.CSD`.
**Entry transactions:** `CAVW` → `COACTVWC` (CARDDEMO.CSD:317-318); `CAUP` → `COACTUPC` (CARDDEMO.CSD:306-308). Both are main-menu options 1 and 2 for regular users (COMEN02Y:25-35). No batch jobs.
**Out of scope / related:** CT00/CT01/CT02 are specified (and signed off) in [`TransactionManagement_functional_requirement.md`](../../migration/transaction-management/docs/TransactionManagement_functional_requirement.md); this document does not repeat them.

**Purpose.** CAVW is a read-only inquiry that, given an 11-digit account number, resolves the account's customer through the card cross-reference and displays the account master and customer master records on one screen. CAUP shows the same data in editable form, validates every changed field (dates, money, SSN, phone/area code, state/ZIP, FICO, Y/N flags, names), asks for an F5 confirmation, and then rewrites **both** the account (ACCTDAT) and the customer (CUSTDAT) records under CICS record locks with an optimistic "changed by someone else" check.

Citation convention: `PROGRAM:start-end` = line numbers in `app/cbl/PROGRAM.cbl`; `COPYBOOK:start-end` = `app/cpy/COPYBOOK.cpy`; `MAPSET.bms:n` = line of the DFHMDF statement in `app/bms/MAPSET.bms`. All cites verified against `7fd2344`.

---

## 2. Functional requirements (business-visible)

### 2.1 CAVW — Account View (COACTVWC, map CACTVWA)

| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| AV-FR-01 | Open Account View from the main menu (option 1) | "View Account" screen with header (Tran `CAVW`, Prog `COACTVWC`, titles, date `mm/dd/yy`, time `hh:mm:ss`), empty Account Number field with cursor, info line "Enter or update id of account to display", footer " F3=Exit " | COACTVWC:282-293, 353-360, 431-455, 460-464, 528-534, 541-552; COACTVW.bms:75, 84, 369 |
| AV-FR-02 | Enter a valid 11-digit account number and press ENTER | Account block shown (Active Y/N, Opened, Expiry, Reissue, Credit Limit, Cash credit Limit, Current Balance, Current Cycle Credit/Debit, Account Group) and Customer block (Customer id, SSN as `NNN-NN-NNNN`, Date of birth, FICO Score, First/Middle/Last name, Address lines 1-2, City, State, Zip, Country, Phone 1/2, Government Issued Id Ref, EFT Account Id, Primary Card Holder Y/N). Money fields formatted `+ZZZ,ZZZ,ZZZ.99`. Account number stays on screen | COACTVWC:361-374, 622-643, 687-718, 723-869, 460-525; COACTVW.bms:117, 138, 159, 171, 192 |
| AV-FR-03 | Press ENTER with the account number blank (or `*`) | Account field shows red `*`; error line "No input received" | COACTVWC:628-630, 640-642, 653-662, 561-565 |
| AV-FR-04 | Enter a non-numeric or all-zero account number | Account field turns red; error "Account Filter must  be a non-zero 11 digit number" (two spaces after "must") | COACTVWC:666-676, 557-559 |
| AV-FR-05 | Enter an account with no card cross-reference | Error "Account:<acct> not found in Cross ref file.  Resp:<resp> Reas:<resp2>"; no data shown | COACTVWC:693-699, 741-758 |
| AV-FR-06 | Enter an account present in the xref but missing from the account master | Error "Account:<acct> not found in Acct Master file.Resp:<resp> Reas:<resp2>" (customer lookup still proceeds — see Q-03) | COACTVWC:701-706, 789-807 |
| AV-FR-07 | Enter an account whose xref customer is missing from the customer master | Error "CustId:<cust> not found in customer master.Resp: <resp> REAS:<resp2>"; account block still shown | COACTVWC:708-715, 839-857, 471-491 |
| AV-FR-08 | Hit an unexpected VSAM/CICS error on any read | Error "File Error: READ     on <file> returned RESP <resp>,RESP2 <resp2>" | COACTVWC:86-105, 759-766, 809-816, 858-865 |
| AV-FR-09 | Press PF3 (or PF15) | Return (XCTL) to the calling program in `CDEMO-FROM-PROGRAM`, or to the main menu `COMEN01C`/`CM00` if none | COACTVWC:324-352; CSSTRPFY:34-35, 58-59 |
| AV-FR-10 | Press any key other than ENTER/PF3/PF15 | Treated exactly as ENTER (re-validates and re-displays) | COACTVWC:299-314 |
| AV-FR-11 | Reach the program in an unexpected context | Plain-text screen "UNEXPECTED DATA SCENARIO"; task ends | COACTVWC:375-382, 877-887 |

### 2.2 CAUP — Account Update (COACTUPC, map CACTUPA)

| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| AU-FR-01 | Open Account Update from the main menu (option 2) | "Update Account" screen, all data fields blank and protected, only Account Number enterable; info "Enter or update id of account to update"; footer "ENTER=Process F3=Exit" (F5=Save / F12=Cancel hidden) | COACTUPC:880-893, 964-973, 2698-2701, 2955-2959, 2986-2996, 3441-3494, 3566-3581; COACTUP.bms:75, 84, 493, 498, 503 |
| AU-FR-02 | Enter a valid account number and press ENTER | Account + customer data fetched and shown (money `+ZZZ,ZZZ,ZZZ.99`, dates split into YYYY / MM / DD, SSN 3/2/4, phones 3/3/4); info "Update account details presented above."; editable fields unprotected except Account Number, Customer id and Country; cursor on Active Y/N | COACTUPC:1429-1449, 1783-1818, 2568-2580, 3608-3644, 3801-3887, 2715-2717, 2787-2869, 2962-2963, 2997-3000, 3009-3012, 3500-3561 |
| AU-FR-03 | Press ENTER with the account number blank or `*` | Red `*` in Account Number; error "No input received" | COACTUPC:1051-1054, 1787-1797, 1441-1443, 3013-3015, 3180-3184 |
| AU-FR-04 | Enter a non-numeric or zero account number | Account Number red; error "Account Number if supplied must be a 11 digit Non-Zero Number" | COACTUPC:1801-1813, 3176-3178 |
| AU-FR-05 | Enter an account missing from xref / account master / customer master, or hit a file error | Same message texts as AV-FR-05..08; screen stays in search mode | COACTUPC:3650-3797, 2577-2579 |
| AU-FR-06 | Press ENTER after fetch without changing anything | Error "No change detected with respect to values fetched."; values unchanged; cursor on Active Y/N | COACTUPC:1460-1468, 1681-1779, 2585-2591, 3009-3012 |
| AU-FR-07 | Change one or more fields and press ENTER with at least one invalid value | The message of the **first** failing edit (edit order of BR-09) is shown; every failing field turns red (blank required field also shows `*`); cursor on the first failing field in screen order; entered values are redisplayed as typed; info "Update account details presented above."; "F12=Cancel" becomes visible | COACTUPC:1470-1669, 2596-2597, 2718-2720, 2870-2953, 2964-2965, 3009-3167, 3208-3436, 3573-3576; CSSETATY:18-27 |
| AU-FR-08 | Change fields so that all edits pass and press ENTER | Info "Changes validated.Press F5 to save"; all fields protected; "F5=Save" and "F12=Cancel" visible | COACTUPC:1671-1675, 2585-2591, 2620-2621, 2966-2967, 3001-3003, 3578-3581 |
| AU-FR-09 | Press F5 on the confirmation screen | Account and customer records are rewritten; info "Changes committed to database"; fields protected; the next key press returns a fresh, empty search screen | COACTUPC:905-911, 2602-2615, 3888-4107, 2968-2969, 979-989 |
| AU-FR-10 | Press F5 when the account record cannot be read for update (locked / missing) | Error "Could not lock account record for update"; info "Changes unsuccessful. Please try again"; next key press returns a fresh search screen | COACTUPC:3894-3915, 2607-2608, 2971-2972, 979-989 |
| AU-FR-11 | Press F5 when the account or customer record was changed by someone else after it was fetched | Error "Record changed by some one else. Please review"; screen returns to editable "show details" mode (see Q-04) | COACTUPC:3947-3952, 4109-4195, 2611-2612 |
| AU-FR-12 | Press F5 when a REWRITE fails | Error "Update of record failed"; info "Changes unsuccessful. Please try again"; a failed customer REWRITE also rolls back the account REWRITE | COACTUPC:4065-4103, 2609-2610, 2973-2974 |
| AU-FR-13 | Press F12 at any time after a successful fetch | Pending edits discarded; account re-read and original values redisplayed | COACTUPC:910, 2568-2580 |
| AU-FR-14 | Press PF3 (or PF15) | SYNCPOINT, then XCTL to `CDEMO-FROM-PROGRAM` or main menu `COMEN01C` | COACTUPC:927-959 |
| AU-FR-15 | Press F5 outside the confirmation state, F12 before a fetch, or any other key | Treated as ENTER | COACTUPC:905-916 |
| AU-FR-16 | Press any key after a committed or failed save | Fresh search screen; account number cleared from COMMAREA | COACTUPC:979-989 |
| AU-FR-17 | Reach an undefined internal state | ABEND-DATA "UNEXPECTED DATA SCENARIO" sent to terminal; transaction abends `9999` | COACTUPC:2633-2640, 4203-4228 |

Field-level validation FRs for CAUP are expressed as BR-10..BR-23 (each with its exact message in §6).

---

## 3. Screen / interface specification

### 3.1 Common header (both maps)
| Field | BMS | Len | I/O | Attributes | Content | Cite |
|-------|-----|-----|-----|-----------|---------|------|
| TRNNAME | (1,7) | 4 | O | ASKIP,FSET,NORM blue | `CAVW` / `CAUP` | COACTVW.bms:34; COACTUP.bms:34; COACTVWC:438; COACTUPC:2675 |
| TITLE01 / TITLE02 | (1,21)/(2,21) | 40 | O | ASKIP yellow | CCDA-TITLE01/02 (COTTL01Y) | COACTVWC:436-437; COACTUPC:2673-2674 |
| CURDATE | (1,71) | 8 | O | ASKIP blue | `mm/dd/yy` from CURRENT-DATE | COACTVWC:441-447; COACTUPC:2678-2684 |
| PGMNAME | (2,7) | 8 | O | ASKIP blue | `COACTVWC` / `COACTUPC` | COACTVWC:439; COACTUPC:2676 |
| CURTIME | (2,71) | 8 | O | ASKIP blue | `hh:mm:ss` | COACTVWC:449-453; COACTUPC:2686-2690 |
| INFOMSG | (22,23) | 45 | O | PROT (VW) / ASKIP (UP), neutral; program sets dark when empty, bright otherwise | info message (§6) | COACTVW.bms:356; COACTUP.bms:480; COACTVWC:567-571; COACTUPC:3566-3571 |
| ERRMSG | (23,1) | 78 | O | ASKIP,BRT,FSET red | error message (§6; `WS-RETURN-MSG` X(75)) | COACTVW.bms:365; COACTUP.bms:489 |

### 3.2 CACTVWA — View Account (COACTVW.bms)
All data fields are display-only (no ATTRB ⇒ BMS default ASKIP) except ACCTSID.

| Label | BMS field | Pos | Len | I/O | Attributes | Source / edit rule | Cite |
|-------|-----------|-----|-----|-----|-----------|-------------------|------|
| Account Number | ACCTSID | (5,38) | 11 | I/O | FSET,IC,NORM,UNPROT green underline, PICIN `99999999999`, VALIDN MUSTFILL; red when invalid, red `*` when blank | BR-01 | COACTVW.bms:84; COACTVWC:543-565 |
| Active Y/N | ACSTTUS | (5,70) | 1 | O | ASKIP | ACCT-ACTIVE-STATUS | COACTVW.bms:97; COACTVWC:473 |
| Opened | ADTOPEN | (6,17) | 10 | O | | ACCT-OPEN-DATE as stored | COACTVW.bms:107; COACTVWC:487 |
| Credit Limit | ACRDLIM | (6,61) | 15 | O | JUSTIFY RIGHT, PICOUT `+ZZZ,ZZZ,ZZZ.99` | ACCT-CREDIT-LIMIT | COACTVW.bms:117; COACTVWC:477 |
| Expiry | AEXPDT | (7,17) | 10 | O | | ACCT-EXPIRAION-DATE | COACTVW.bms:128; COACTVWC:488 |
| Cash credit Limit | ACSHLIM | (7,61) | 15 | O | PICOUT as above | ACCT-CASH-CREDIT-LIMIT | COACTVW.bms:138; COACTVWC:479-480 |
| Reissue | AREISDT | (8,17) | 10 | O | | ACCT-REISSUE-DATE | COACTVW.bms:149; COACTVWC:489 |
| Current Balance | ACURBAL | (8,61) | 15 | O | PICOUT | ACCT-CURR-BAL | COACTVW.bms:159; COACTVWC:475 |
| Current Cycle Credit | ACRCYCR | (9,61) | 15 | O | PICOUT | ACCT-CURR-CYC-CREDIT | COACTVW.bms:171; COACTVWC:482-483 |
| Account Group | AADDGRP | (10,23) | 10 | O | | ACCT-GROUP-ID | COACTVW.bms:182; COACTVWC:490 |
| Current Cycle Debit | ACRCYDB | (10,61) | 15 | O | PICOUT | ACCT-CURR-CYC-DEBIT | COACTVW.bms:192; COACTVWC:485 |
| Customer id | ACSTNUM | (12,23) | 9 | O | | CUST-ID | COACTVW.bms:207; COACTVWC:494 |
| SSN | ACSTSSN | (12,54) | 12 | O | | `CUST-SSN(1:3)-(4:2)-(6:4)` | COACTVW.bms:216; COACTVWC:496-504 |
| Date of birth | ACSTDOB | (13,23) | 10 | O | | CUST-DOB-YYYY-MM-DD | COACTVW.bms:225; COACTVWC:507 |
| FICO Score | ACSTFCO | (13,61) | 3 | O | | CUST-FICO-CREDIT-SCORE | COACTVW.bms:234; COACTVWC:505-506 |
| First / Middle / Last Name | ACSFNAM / ACSMNAM / ACSLNAM | (15,1)/(15,28)/(15,55) | 25 each | O | | CUST-FIRST/MIDDLE/LAST-NAME | COACTVW.bms:251, 256, 261; COACTVWC:508-510 |
| Address | ACSADL1 / ACSADL2 | (16,10)/(17,10) | 50 | O | | CUST-ADDR-LINE-1 / -2 | COACTVW.bms:268, 282; COACTVWC:511-512 |
| State | ACSSTTE | (16,73) | 2 | O | | CUST-ADDR-STATE-CD | COACTVW.bms:277; COACTVWC:514 |
| Zip | ACSZIPC | (17,73) | 5 | O | JUSTIFY RIGHT | CUST-ADDR-ZIP (X(10); first 5 shown) | COACTVW.bms:291; COACTVWC:515 |
| City | ACSCITY | (18,10) | 50 | O | | CUST-ADDR-LINE-3 | COACTVW.bms:301; COACTVWC:513 |
| Country | ACSCTRY | (18,73) | 3 | O | | CUST-ADDR-COUNTRY-CD | COACTVW.bms:310; COACTVWC:516 |
| Phone 1 / Phone 2 | ACSPHN1 / ACSPHN2 | (19,10)/(20,10) | 13 | O | | CUST-PHONE-NUM-1/-2 (X(15); first 13 shown) | COACTVW.bms:319, 335; COACTVWC:517-518 |
| Government Issued Id Ref | ACSGOVT | (19,58) | 20 | O | | CUST-GOVT-ISSUED-ID | COACTVW.bms:326; COACTVWC:519 |
| EFT Account Id | ACSEFTC | (20,41) | 10 | O | | CUST-EFT-ACCOUNT-ID | COACTVW.bms:342; COACTVWC:520 |
| Primary Card Holder Y/N | ACSPFLG | (20,78) | 1 | O | | CUST-PRI-CARD-HOLDER-IND | COACTVW.bms:351; COACTVWC:521-522 |

**PF-key map (CAVW):** ENTER = look up / redisplay; PF3 (PF15) = exit to caller/menu; every other AID (PF1-PF24 other than 3/15, PA1, PA2, CLEAR) = ENTER (COACTVWC:299-314; CSSTRPFY:17-79).

### 3.3 CACTUPA — Update Account (COACTUP.bms)
"Edit rule" refers to §4 BR numbers. *Prot.* column = attribute set by the program after a successful fetch in show/error states (3320-UNPROTECT-FEW-ATTRS); in search mode only ACCTSID is unprotected; in confirm/done states everything is protected (COACTUPC:2986-3006, 3441-3561).

| Label | BMS field(s) | Pos | Len | Prot. after fetch | Record field | Edit rule | Cite |
|-------|--------------|-----|-----|-------------------|--------------|-----------|------|
| Account Number | ACCTSID | (5,38) | 11 | protected (FSET) | ACCT-ID (key) | BR-01 | COACTUP.bms:84; COACTUPC:1051-1058, 3442 |
| Active Y/N | ACSTTUS | (5,70) | 1 | unprot (FSE) | ACCT-ACTIVE-STATUS | BR-10 | COACTUP.bms:94; COACTUPC:1065-1070, 3502 |
| Opened YYYY-MM-DD | OPNYEAR / OPNMON / OPNDAY | (6,17)/(6,24)/(6,29) | 4/2/2 | unprot | ACCT-OPEN-DATE | BR-11 | COACTUP.bms:104, 112, 120; COACTUPC:1144-1163, 3511-3513 |
| Credit Limit | ACRDLIM | (6,61) | 15 | unprot | ACCT-CREDIT-LIMIT | BR-06 | COACTUP.bms:132; COACTUPC:1073-1084, 3504 |
| Expiry YYYY-MM-DD | EXPYEAR / EXPMON / EXPDAY | (7,17)/(7,24)/(7,29) | 4/2/2 | unprot | ACCT-EXPIRAION-DATE | BR-11 | COACTUP.bms:142, 150, 158; COACTUPC:1167-1186, 3515-3517 |
| Cash credit Limit | ACSHLIM | (7,61) | 15 | unprot | ACCT-CASH-CREDIT-LIMIT | BR-06 | COACTUP.bms:170; COACTUPC:1087-1098, 3505 |
| Reissue YYYY-MM-DD | RISYEAR / RISMON / RISDAY | (8,17)/(8,24)/(8,29) | 4/2/2 | unprot | ACCT-REISSUE-DATE | BR-11 | COACTUP.bms:180, 188, 196; COACTUPC:1190-1209, 3519-3521 |
| Current Balance | ACURBAL | (8,61) | 15 | unprot | ACCT-CURR-BAL | BR-06 | COACTUP.bms:208; COACTUPC:1101-1112, 3506 |
| Current Cycle Credit | ACRCYCR | (9,61) | 15 | unprot | ACCT-CURR-CYC-CREDIT | BR-06 | COACTUP.bms:219; COACTUPC:1115-1126, 3507 |
| Account Group | AADDGRP | (10,23) | 10 | unprot | ACCT-GROUP-ID | not edited (BR-23) | COACTUP.bms:229; COACTUPC:1213-1218, 3529 |
| Current Cycle Debit | ACRCYDB | (10,61) | 15 | unprot | ACCT-CURR-CYC-DEBIT | BR-06 | COACTUP.bms:240; COACTUPC:1129-1140, 3508 |
| Customer id | ACSTNUM | (12,23) | 9 | **protected** | CUST-ID (key) | not edited | COACTUP.bms:254; COACTUPC:1224-1229, 3531 |
| SSN | ACTSSN1 / ACTSSN2 / ACTSSN3 | (12,55)/(12,61)/(12,66) | 3/2/4 | unprot | CUST-SSN | BR-21 | COACTUP.bms:264, 272, 280; COACTUPC:1233-1252, 3532-3534 |
| Date of birth YYYY-MM-DD | DOBYEAR / DOBMON / DOBDAY | (13,23)/(13,30)/(13,35) | 4/2/2 | unprot | CUST-DOB-YYYY-MM-DD | BR-11, BR-12 | COACTUP.bms:291, 299, 307; COACTUPC:1256-1275, 3523-3525 |
| FICO Score | ACSTFCO | (13,62) | 3 | unprot | CUST-FICO-CREDIT-SCORE | BR-13 | COACTUP.bms:318; COACTUPC:1279-1284, 3535 |
| First / Middle / Last Name | ACSFNAM / ACSMNAM / ACSLNAM | (15,1)/(15,28)/(15,55) | 25 each | unprot | CUST-*-NAME | BR-14 | COACTUP.bms:336, 342, 348; COACTUPC:1288-1311, 3537-3539 |
| Address line 1 / 2 | ACSADL1 / ACSADL2 | (16,10)/(17,10) | 50 | unprot | CUST-ADDR-LINE-1/-2 | BR-15 | COACTUP.bms:356, 372; COACTUPC:1315-1327, 3541-3542 |
| State | ACSSTTE | (16,73) | 2 | unprot | CUST-ADDR-STATE-CD | BR-17, BR-19 | COACTUP.bms:366; COACTUPC:1336-1341, 3544 |
| Zip | ACSZIPC | (17,73) | 5 | unprot | CUST-ADDR-ZIP | BR-18, BR-19 | COACTUP.bms:382; COACTUPC:1350-1355, 3545 |
| City | ACSCITY | (18,10) | 50 | unprot | CUST-ADDR-LINE-3 | BR-16 | COACTUP.bms:392; COACTUPC:1329-1334, 3543 |
| Country | ACSCTRY | (18,73) | 3 | **protected** | CUST-ADDR-COUNTRY-CD | BR-16 (edited although protected, Q-07) | COACTUP.bms:402; COACTUPC:1343-1348, 3547 |
| Phone 1 (area/prefix/line) | ACSPH1A / ACSPH1B / ACSPH1C | (19,10)/(19,14)/(19,18) | 3/3/4 | unprot | CUST-PHONE-NUM-1 | BR-20 | COACTUP.bms:412, 417, 422; COACTUPC:1357-1376, 3549-3551 |
| Government Issued Id Ref | ACSGOVT | (19,58) | 20 | unprot | CUST-GOVT-ISSUED-ID | not edited (BR-23) | COACTUP.bms:433; COACTUPC:1401-1406, 3557 |
| Phone 2 (area/prefix/line) | ACSPH2A / ACSPH2B / ACSPH2C | (20,10)/(20,14)/(20,18) | 3/3/4 | unprot | CUST-PHONE-NUM-2 | BR-20 | COACTUP.bms:443, 448, 453; COACTUPC:1378-1397, 3553-3555 |
| EFT Account Id | ACSEFTC | (20,41) | 10 | unprot | CUST-EFT-ACCOUNT-ID | BR-22 | COACTUP.bms:464; COACTUPC:1410-1415, 3558 |
| Primary Card Holder Y/N | ACSPFLG | (20,78) | 1 | unprot | CUST-PRI-CARD-HOLDER-IND | BR-10 | COACTUP.bms:474; COACTUPC:1419-1424, 3559 |
| Function keys | FKEYS / FKEY05 / FKEY12 | (24,1)/(24,23)/(24,31) | 21/7/10 | ASKIP; FKEY05/12 DRK by default | "ENTER=Process F3=Exit" / "F5=Save" / "F12=Cancel" | BR-31 | COACTUP.bms:493, 498, 503; COACTUPC:3573-3581 |

Input normalisation for every CACTUPA input: a field containing only `*` or spaces is treated as empty (LOW-VALUES) (COACTUPC:1051-1424). Display in show mode: money via `+ZZZ,ZZZ,ZZZ.99`; in error mode, an invalid money value is redisplayed as typed (COACTUPC:2797-2811, 2874-2909).

**PF-key map (CAUP)** (COACTUPC:905-916, 2562-2641):
| Key | Valid when | Effect |
|-----|-----------|--------|
| ENTER | always | fetch (search mode) / validate (show & error modes) / no-op in confirm mode |
| PF3 / PF15 | always | SYNCPOINT + exit to caller/menu |
| PF5 / PF17 | only in "changes OK, not confirmed" (`ACUP-CHANGE-ACTION = 'N'`) | save (9600-WRITE-PROCESSING) |
| PF12 / PF24 | only after details fetched | discard edits, re-read account |
| any other / PF5, PF12 outside their state | — | treated as ENTER |

---

## 4. Business rules & calculations

| ID | Rule | Source cite |
|----|------|-------------|
| BR-01 | **Account number filter.** Blank or `*` ⇒ "not supplied" (error). Otherwise must be numeric and not all zeros (11 chars). Valid value is copied to `CDEMO-ACCT-ID`; on error `CDEMO-ACCT-ID` is zeroed. | COACTVWC:628-633, 649-681; COACTUPC:1051-1058, 1783-1818 |
| BR-02 | **Lookup chain.** (1) READ `CXACAIX` (card-xref alternate index by account) keyed by the 11-char account id → `XREF-CUST-ID`, `XREF-CARD-NUM` copied to `CDEMO-CUST-ID`, `CDEMO-CARD-NUM`; stop on error. (2) READ `ACCTDAT` by account id. (3) READ `CUSTDAT` by the xref customer id. Only the first card of the account (first AIX hit) is used. | COACTVWC:687-718, 723-869; COACTUPC:3608-3644, 3650-3797 |
| BR-03 | **SSN presentation.** View: 9-digit `CUST-SSN` shown as `NNN-NN-NNNN`. Update: shown as three fields (1:3)(4:2)(6:4) and reassembled as a 9-digit number on save. | COACTVWC:496-504; COACTUPC:2829-2831, 830-835, 4044 |
| BR-04 | **Money presentation.** Credit limit, cash credit limit, current balance, cycle credit, cycle debit are S9(10)V99 and displayed with edit mask `+ZZZ,ZZZ,ZZZ.99`. | COACTVW.bms:117, 138, 159, 171, 192; COACTUPC:370-371, 2797-2811 |
| BR-05 | **Fetch snapshot.** After a successful fetch the account and customer values are saved in the COMMAREA (`ACUP-OLD-DETAILS`), dates as CCYYMMDD (from `YYYY-MM-DD` positions 1:4, 6:2, 9:2); COMMAREA customer/account/card info refreshed. | COACTUPC:669-756, 3801-3887 |
| BR-06 | **Signed money edit** (Credit Limit, Cash Credit Limit, Current Balance, Current Cycle Credit, Current Cycle Debit): required; must satisfy `TEST-NUMVAL-C = 0` (sign, commas, decimal point, currency sign accepted); value converted with `NUMVAL-C` into S9(10)V99. No range, sign, or cross-field check (e.g. cash ≤ credit limit) exists. | COACTUPC:1073-1140, 1484-1527, 2180-2223 |
| BR-07 | **Change detection.** After fetch, every ENTER compares new vs. snapshot. Account block: status (case-insensitive), money (raw 12-byte images), the three dates, group id (trimmed, case-insensitive). Customer block: cust id, names, address lines 1-3, state, country, zip, govt id, primary-holder flag (trimmed, case-insensitive); phone parts, SSN, DOB, EFT id, FICO (exact). Any difference ⇒ `CHANGE-HAS-OCCURRED`; otherwise "No change detected…" | COACTUPC:1681-1779 |
| BR-08 | **Edits run only on change.** If no change, or already in confirm (`N`) or done (`C`) state, field edits are skipped and flags cleared. Otherwise state is set to `E` before editing and to `N` if no edit fails. | COACTUPC:1460-1475, 1671-1675 |
| BR-09 | **Edit order / first-error-wins.** All edits always run (all flags set), but only the first failing edit writes `WS-RETURN-MSG` (each guarded by `WS-RETURN-MSG-OFF`). Order: Account Status, Open Date, Credit Limit, Expiry Date, Cash Credit Limit, Reissue Date, Current Balance, Current Cycle Credit, Current Cycle Debit, SSN (3 parts), Date of Birth, FICO, First Name, Middle Name, Last Name, Address Line 1, State, Zip, City, Country, Phone 1, Phone 2, EFT Account Id, Primary Card Holder, then State/Zip cross-check. Cursor goes to the first failing field in **screen** order (EVALUATE order), which can differ from the message's field. | COACTUPC:1472-1669, 3009-3167 |
| BR-10 | **Yes/No flags** (Account Status, Primary Card Holder): blank, spaces or zero ⇒ "must be supplied."; otherwise only uppercase `Y` or `N` accepted. | COACTUPC:76-80, 1472-1476, 1657-1662, 1856-1896 |
| BR-11 | **Date edit** (Open, Expiry, Reissue, Date of Birth; via `EDIT-DATE-CCYYMMDD`): year required, numeric, century must be 19 or 20; month required, 1-12; day required, numeric, 1-31; day 31 not allowed in 30-day months/Feb; Feb 30 rejected; Feb 29 only in leap years (year divisible by 4, or by 400 when YY = 00); finally validated by `CALL 'CSUTLDTC'` (format `YYYYMMDD`), non-zero severity ⇒ error. Year/month/day edits all execute (fall-through); cross checks and the CSUTLDTC call run only if year, month and day are each valid. | CSUTLDPY:18-331; CSUTLDWY:4-36, 58-85; COACTUPC:1478-1482, 1490-1494, 1503-1507, 1533-1538 |
| BR-12 | **Date of birth reasonableness:** DOB must be strictly earlier than today (`INTEGER-OF-DATE(today) > INTEGER-OF-DATE(DOB)`). | CSUTLDPY:341-372; COACTUPC:1539-1543 |
| BR-13 | **FICO score:** required, 3 numeric chars, non-zero, and within 300-850 inclusive. | COACTUPC:848-849, 1545-1556, 2109-2178, 2514-2533 |
| BR-14 | **Names:** First and Last Name required and may contain only A-Z, a-z and spaces; Middle Name optional, same character rule when present. | COACTUPC:1560-1582, 1898-1953, 2012-2059 |
| BR-15 | **Address:** Address Line 1 mandatory (any characters, not all blank). Address Line 2 is not validated. | COACTUPC:1584-1590, 1824-1854 |
| BR-16 | **City / Country:** City (ADDR-LINE-3, 50) and Country (3) required, letters and spaces only. | COACTUPC:1615-1630, 1898-1953 |
| BR-17 | **State:** required, letters only, must be one of 56 codes in `VALID-US-STATE-CODE` (50 states + DC, AS, GU, MP, PR, VI). | COACTUPC:1592-1602, 2493-2513; CSLKPCDY:1012-1069 |
| BR-18 | **ZIP:** required, 5 numeric chars, non-zero. | COACTUPC:1605-1611, 2109-2178 |
| BR-19 | **State/ZIP consistency:** only when State and ZIP each passed, `state code + first 2 ZIP digits` must be in `VALID-US-STATE-ZIP-CD2-COMBO` (240 entries); failure flags both fields. | COACTUPC:1665-1669, 2536-2560; CSLKPCDY:1071-1314 |
| BR-20 | **US phone (Phone 1, Phone 2):** optional — if area code, prefix and line number are all empty the phone is valid. Otherwise each part is edited: area code required, numeric (3), non-zero, and in `VALID-GENERAL-PURP-CODE` (410 North-American general-purpose codes); prefix required, numeric (3), non-zero; line number required, numeric (4), non-zero. All three parts are checked (fall-through); first message wins. | COACTUPC:82-115, 1632-1646, 2225-2429; CSLKPCDY:24, 521-930 |
| BR-21 | **SSN:** part 1 required, 3 numeric, non-zero, and not 000, 666 or 900-999; part 2 required, 2 numeric, non-zero; part 3 required, 4 numeric, non-zero. | COACTUPC:117-146, 1529-1531, 2431-2491 |
| BR-22 | **EFT Account Id:** required, 10 numeric chars, non-zero. | COACTUPC:1648-1655, 2109-2178 |
| BR-23 | **Not validated** (accepted as typed): Account Group, Government Issued Id, Address Line 2; Customer id and Account Number are protected after fetch and are not re-edited. | COACTUPC:1470-1669 (no edit), 3442, 3531 |
| BR-24 | **Update state machine** (`ACUP-CHANGE-ACTION`): LOW/SPACES = details not fetched → `S` show details (after successful fetch) → `E` changes have errors / `N` changes validated, not confirmed → on F5: `C` committed, `L` lock error, `F` update failed, or back to `S` if data changed by others. `C`, `L`, `F` reset to search mode on the next interaction. | COACTUPC:654-668, 964-1003, 2562-2641 |
| BR-25 | **Confirmation required:** data is written only on PF5 while state = `N`; ENTER in state `N` leaves the state unchanged. | COACTUPC:905-912, 2602-2621 |
| BR-26 | **Locking & concurrent-change detection:** on F5, READ UPDATE ACCTDAT (by account) then READ UPDATE CUSTDAT (by `CDEMO-CUST-ID`). The current records are compared to the fetch snapshot — account: status, 5 money fields, open/expiry/reissue date parts, group id (case-insensitive); customer: names, address lines 1-3, state, country (case-insensitive), zip, both phones, SSN, govt id (case-insensitive), DOB parts, EFT id, primary holder flag, FICO. Any mismatch ⇒ no write, "Record changed by some one else. Please review". | COACTUPC:3888-3952, 4109-4195 |
| BR-27 | **Record construction for REWRITE:** account fields from validated new values (money via NUMVAL-C result); dates rebuilt as `YYYY-MM-DD`; customer phones rebuilt as `(AAA)BBB-CCCC`; DOB as `YYYY-MM-DD`; SSN 9 digits; all other customer fields moved as entered. | COACTUPC:3956-4059 |
| BR-28 | **Write order & atomicity:** REWRITE ACCTDAT first; on failure stop ("Update of record failed"). Then REWRITE CUSTDAT; on failure `SYNCPOINT ROLLBACK` (undoes the account rewrite). Success is committed at task end (pseudo-conversational RETURN); PF3 issues an explicit SYNCPOINT before XCTL. | COACTUPC:4065-4103, 952-954, 1015-1019 |
| BR-29 | **COMMAREA propagation:** View sets `CDEMO-ACCT-ID`, `CDEMO-CUST-ID`, `CDEMO-CARD-NUM`; Update additionally sets `CDEMO-CUST-FNAME/MNAME/LNAME`, `CDEMO-ACCT-STATUS`. | COACTVWC:678, 739-740; COACTUPC:1815, 3666-3667, 3805-3811 |
| BR-30 | **PF-key validity:** View accepts ENTER/PF3 only; Update accepts ENTER, PF3, PF5 (state N), PF12 (after fetch); all others are mapped to ENTER. PF13-PF24 are folded to PF1-PF12. | COACTVWC:306-314; COACTUPC:905-916; CSSTRPFY:21-78 |
| BR-31 | **F-key labels:** F12=Cancel shown when changes were made (states E, N, L, F); F5=Save and F12=Cancel shown when prompting for confirmation. | COACTUPC:3573-3581 |
| BR-32 | **Error highlighting:** a field whose flag is NOT-OK or BLANK turns red on re-entry; a BLANK required field additionally displays `*`. Account Number red if invalid, red `*` if blank. | CSSETATY:18-27; COACTVWC:557-565; COACTUPC:3176-3184, 3208-3436 |
| BR-33 | **Exit COMMAREA:** on PF3 the program sets `CDEMO-TO-TRANID/PROGRAM` (caller or `CM00`/`COMEN01C`), `CDEMO-FROM-TRANID/PROGRAM` = self, `CDEMO-USRTYP-USER`, `CDEMO-PGM-ENTER`, `CDEMO-LAST-MAPSET/MAP` = own map. | COACTVWC:328-347; COACTUPC:930-950 |

---

## 5. Data access

| File (CICS name) | Underlying dataset | Program | Operation | Key | Record copybook | Cite |
|------------------|--------------------|---------|-----------|-----|-----------------|------|
| CXACAIX | `AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH` (AIX over CARDXREF by account) | COACTVWC, COACTUPC | READ | account id X(11) | CVACT03Y `CARD-XREF-RECORD` (50) | COACTVWC:192-193, 727-735; COACTUPC:581-582, 3654-3662; CARDDEMO.CSD:63-66 |
| ACCTDAT | `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | COACTVWC, COACTUPC | READ | ACCT-ID X(11) | CVACT01Y `ACCOUNT-RECORD` (300) | COACTVWC:776-784; COACTUPC:3703-3711 |
| ACCTDAT | same | COACTUPC | READ UPDATE + REWRITE | ACCT-ID | written from `ACCT-UPDATE-RECORD` (local layout, see Q-01) | COACTUPC:3894-3903, 4065-4071, 418-433 |
| CUSTDAT | `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` | COACTVWC, COACTUPC | READ | CUST-ID X(9) | CVCUS01Y `CUSTOMER-RECORD` (500) | COACTVWC:826-834; COACTUPC:3753-3761 |
| CUSTDAT | same | COACTUPC | READ UPDATE + REWRITE | CUST-ID | written from `CUST-UPDATE-RECORD` (local mirror of CVCUS01Y) | COACTUPC:3921-3930, 4085-4091, 434-456 |
| CARDDAT / CARDAIX | — | both | declared literal only, not accessed | — | — | COACTVWC:186-191; COACTUPC:577-580 |

No WRITE, DELETE or BROWSE operations. Utility call: `CSUTLDTC` (date validation) via CSUTLDPY:293-296.

---

## 6. Message catalogue

`WS-RETURN-MSG` (X(75)) → ERRMSG (row 23, red); `WS-INFO-MSG` (X(40)) → INFOMSG (row 22). `<…>` marks inserted values; texts are exact.

### 6.1 Info line
| Prog | Text | Condition | Cite |
|------|------|-----------|------|
| VW | "Enter or update id of account to display" | first display, and after every lookup (see Q-09) | COACTVWC:113-114, 462-463, 528-530, 689 |
| VW | "Displaying details of given Account" | defined, never set (Q-09) | COACTVWC:115-116 |
| UP | "Enter or update id of account to update" | entry / search mode / no info | COACTUPC:468-469, 2958-2961, 2975-2976 |
| UP | "Update account details presented above." | show-details or changes-with-errors | COACTUPC:470-471, 2791, 2962-2965 |
| UP | "Changes validated.Press F5 to save" | state N | COACTUPC:472-473, 2966-2967 |
| UP | "Changes committed to database" | state C | COACTUPC:474-475, 2968-2969 |
| UP | "Changes unsuccessful. Please try again" | state L or F | COACTUPC:476-477, 2971-2974 |
| UP | "Details of selected account shown above" | set internally after fetch; overridden by 3250 (never displayed) | COACTUPC:466-467, 1452 |

### 6.2 Error line — fixed texts
| Prog | Text | Condition | Cite |
|------|------|-----------|------|
| VW, UP | "No input received" | account number blank/`*` | COACTVWC:123-124, 640-642; COACTUPC:489-490, 1441-1443 |
| VW, UP | "Account number not provided" | set for blank account, immediately overwritten by "No input received" | COACTVWC:121-122, 657-659; COACTUPC:483-484, 1791-1793 |
| VW | "Account Filter must  be a non-zero 11 digit number" | non-numeric / zero account | COACTVWC:666-676 |
| UP | "Account Number if supplied must be a 11 digit Non-Zero Number" | non-numeric / zero account | COACTUPC:1802-1813 |
| UP | "No change detected with respect to values fetched." | ENTER with no changes | COACTUPC:491-492, 1769 |
| UP | "Could not lock account record for update" | READ UPDATE ACCTDAT not NORMAL | COACTUPC:517-518, 3907-3915 |
| UP | "Could not lock customer record for update" | READ UPDATE CUSTDAT not NORMAL | COACTUPC:519-520, 3934-3942 |
| UP | "Record changed by some one else. Please review" | 9700 mismatch | COACTUPC:521-522, 4143, 4189 |
| UP | "Update of record failed" | REWRITE ACCTDAT or CUSTDAT not NORMAL | COACTUPC:523-524, 4079, 4098 |
| UP | "Invalid zip code for state" | BR-19 failure | COACTUPC:2548-2554 |
| VW, UP | "PF03 pressed.Exiting              " | defined, never set | COACTVWC:119-120; COACTUPC:481-482 |
| VW, UP | "UNEXPECTED DATA SCENARIO" | undefined program state (VW: plain text; UP: ABEND-MSG) | COACTVWC:379-381; COACTUPC:2637-2638 |
| VW, UP | "UNEXPECTED ABEND OCCURRED." | ABEND-ROUTINE with no message | COACTVWC:918-920; COACTUPC:4205-4207 |

Defined but never referenced (dead 88-levels): VW "Account number must be a non zero 11 digit number" (125-128), "Did not find this account in account card xref file" (129-130), "Did not find this account in account master file" (131-132), "Did not find associated customer in master file" (133-134), "Error reading account card xref File" (135-136), "Looks Good.... so far" (137-138). UP "Last name not provided" (485-486), "Name can only contain alphabets and spaces" (487-488), "Account number must be a non zero 11 digit number" (493-496), "Did not find this account in account card xref file" (497-498), "Did not find this account in account master file" (499-500), "Did not find associated customer in master file" (501-502), "Account Active Status must be Y or N" (503-504), "Credit Limit must be supplied" (505-506), "Credit Limit is not valid" (507-508), "Card expiry month must be between 1 and 12" (509-510), "Invalid card expiry year" (511-512), "Did not find this account in cards database" (513-514), "Did not find cards for this search condition" (515-516), "Error reading Card Data File" (525-526), "Looks Good.... so far" (527-528).

### 6.3 Error line — templated texts
| Prog | Template (exact) | Condition | Cite |
|------|------------------|-----------|------|
| VW, UP | "Account:" + acct(11) + " not found in" + " Cross ref file.  Resp:" + RESP(10) + " Reas:" + RESP2(10) | CXACAIX NOTFND | COACTVWC:741-758; COACTUPC:3668-3685 |
| VW, UP | "Account:" + acct + " not found in" + " Acct Master file.Resp:" + RESP + " Reas:" + RESP2 | ACCTDAT NOTFND | COACTVWC:789-807; COACTUPC:3716-3734 |
| VW, UP | "CustId:" + cust(9) + " not found" + " in customer master.Resp: " + RESP + " REAS:" + RESP2 | CUSTDAT NOTFND | COACTVWC:839-857; COACTUPC:3766-3784 |
| VW, UP | "File Error: " + op(8) + " on " + file(9) + " returned RESP " + RESP(10) + ",RESP2 " + RESP2(10) | other READ RESP; op = `READ` | COACTVWC:86-105, 759-766; COACTUPC:389-408, 3686-3693 |
| UP | "<name> must be supplied." | blank required field (mandatory / Y-N / alpha / numeric / money) | COACTUPC:1838-1845, 1866-1873, 1912-1919, 2123-2130, 2188-2195 |
| UP | "<name> must be Y or N." | Y/N field not `Y`/`N` | COACTUPC:1883-1890 |
| UP | "<name> can have alphabets only." | non-alpha in name/state/city/country | COACTUPC:1938-1945, 2044-2051 |
| UP | "<name> must be all numeric." | non-numeric FICO/ZIP/EFT/SSN part | COACTUPC:2143-2150 |
| UP | "<name> must not be zero." | numeric value zero | COACTUPC:2160-2167 |
| UP | "<name> is not valid" | money fails TEST-NUMVAL-C | COACTUPC:2206-2212 |
| UP | "<name>: Area code must be supplied." / ": Area code must be A 3 digit number." / ": Area code cannot be zero" / ": Not valid North America general purpose area code" | phone area code | COACTUPC:2251-2257, 2269-2275, 2283-2289, 2303-2309 |
| UP | "<name>: Prefix code must be supplied." / ": Prefix code must be A 3 digit number." / ": Prefix code cannot be zero" | phone prefix | COACTUPC:2322-2328, 2340-2346, 2354-2360 |
| UP | "<name>: Line number code must be supplied." / ": Line number code must be A 4 digit number." / ": Line number code cannot be zero" | phone line number | COACTUPC:2375-2381, 2393-2399, 2407-2413 |
| UP | "<name>: should not be 000, 666, or between 900 and 999" | SSN part 1 | COACTUPC:2454-2460 |
| UP | "<name>: is not a valid state code" | state not in lookup | COACTUPC:2500-2506 |
| UP | "<name>: should be between 300 and 850" | FICO range | COACTUPC:2520-2526 |
| UP | "<name> : Year must be supplied." / "<name> must be 4 digit number." / "<name> : Century is not valid." | date year | CSUTLDPY:34-40, 51-57, 76-82 |
| UP | "<name> : Month must be supplied." / "<name>: Month must be a number between 1 and 12." | date month | CSUTLDPY:98-104, 116-122, 133-139 |
| UP | "<name> : Day must be supplied." / "<name>:day must be a number between 1 and 31." | date day | CSUTLDPY:158-164, 177-183, 192-198 |
| UP | "<name>:Cannot have 31 days in this month." / "<name>:Cannot have 30 days in this month." / "<name>:Not a leap year.Cannot have 29 days in this month." | day/month/leap | CSUTLDPY:218-224, 233-239, 263-269 |
| UP | "<name> validation error Sev code: " + sev(4) + " Message code: " + msgno(4) | CSUTLDTC non-zero severity | CSUTLDPY:305-313 |
| UP | "<name>:cannot be in the future " | DOB ≥ today | CSUTLDPY:360-366 |

`<name>` values (`WS-EDIT-VARIABLE-NAME`, exact): `Account Status`, `Open Date`, `Credit Limit`, `Expiry Date`, `Cash Credit Limit`, `Reissue Date`, `Current Balance`, `Current Cycle Credit Limit`, `Current Cycle Debit Limit`, `SSN: First 3 chars`, `SSN 4th & 5th chars`, `SSN Last 4 chars`, `Date of Birth`, `FICO Score`, `First Name`, `Middle Name`, `Last Name`, `Address Line 1`, `State`, `Zip`, `City`, `Country`, `Phone Number 1`, `Phone Number 2`, `EFT Account Id`, `Primary Card Holder` (COACTUPC:1472-1657, 2439, 2469, 2481). E.g. "FICO Score: should be between 300 and 850", "Open Date : Year must be supplied.", "Phone Number 1: Area code cannot be zero".

---

## 7. Navigation / integration

| Direction | Counterpart | Mechanism | Detail | Cite |
|-----------|-------------|-----------|--------|------|
| Inbound | COMEN01C main menu (options 1 "Account View", 2 "Account Update", user type `U`) | XCTL with CARDDEMO-COMMAREA, `CDEMO-PGM-CONTEXT = 0`, `CDEMO-FROM-PROGRAM = COMEN01C` | Program treats "from menu and not re-enter" as a fresh start and re-initialises the COMMAREA (account number is **not** pre-loaded) | COMEN02Y:25-35; COMEN01C:183-187; COACTVWC:282-286; COACTUPC:880-886 |
| Inbound (self) | CAVW / CAUP pseudo-conversation | `EXEC CICS RETURN TRANSID(own) COMMAREA(WS-COMMAREA) LENGTH(2000)` | COMMAREA = CARDDEMO-COMMAREA + program area | COACTVWC:394-406; COACTUPC:1007-1019 |
| Inbound (implied) | Card list COCRDLIC (`CDEMO-LAST-MAPSET = 'COCRDLI'`) | — | CAUP resets ACCTSID colour when last mapset was the card list; no XCTL to CAUP/CAVW from that program exists in scope (Q-12) | COACTUPC:3171-3173 |
| Outbound | `CDEMO-FROM-PROGRAM` or COMEN01C (`CM00`) | XCTL on PF3 | CAUP issues SYNCPOINT first | COACTVWC:349-352; COACTUPC:952-959 |
| Outbound | CSUTLDTC | static CALL | `USING WS-EDIT-DATE-CCYYMMDD, WS-DATE-FORMAT, WS-DATE-VALIDATION-RESULT` | CSUTLDPY:284-325 |

**COMMAREA fields used (COCOM01Y):** read — `CDEMO-FROM-TRANID`, `CDEMO-FROM-PROGRAM`, `CDEMO-PGM-CONTEXT`, `CDEMO-LAST-MAPSET`, `CDEMO-CUST-ID` (key for CUSTDAT reads/locks); written — `CDEMO-TO-TRANID`, `CDEMO-TO-PROGRAM`, `CDEMO-FROM-*`, `CDEMO-USER-TYPE`, `CDEMO-PGM-CONTEXT`, `CDEMO-LAST-MAP(SET)`, `CDEMO-ACCT-ID`, `CDEMO-CUST-ID`, `CDEMO-CARD-NUM`, plus (CAUP) `CDEMO-CUST-FNAME/MNAME/LNAME`, `CDEMO-ACCT-STATUS`. Program-private area: VW `CA-FROM-PROGRAM`, `CA-FROM-TRANID` (unused) (COACTVWC:213-216); UP `ACUP-CHANGE-ACTION`, `ACUP-OLD-DETAILS`, `ACUP-NEW-DETAILS` (COACTUPC:652-848).

---

## 8. Edge cases, error handling, abend / return codes

| # | Situation | Behaviour | Cite |
|---|-----------|-----------|------|
| E-01 | `EIBCALEN = 0` (transaction started directly) | Fresh COMMAREA; screen sent with prompt | COACTVWC:282-286; COACTUPC:880-886 |
| E-02 | CICS abend during task | `HANDLE ABEND` → ABEND-ROUTINE: sends ABEND-DATA (code, culprit = program, reason, message; default "UNEXPECTED ABEND OCCURRED.") then `ABEND ABCODE('9999')` | COACTVWC:264-266, 916-937; COACTUPC:862-864, 4203-4228; CSMSG02Y:21-28 |
| E-03 | Undefined state | VW: SEND TEXT "UNEXPECTED DATA SCENARIO" + bare RETURN (abend code '0001' set but not used). UP: ABEND-ROUTINE with code '0001' → abend 9999 | COACTVWC:375-382; COACTUPC:2633-2640 |
| E-04 | RECEIVE MAP response (e.g. MAPFAIL after CLEAR/PA key) | RESP captured but never tested; processing continues with whatever is in the input map | COACTVWC:610-617; COACTUPC:1039-1045 |
| E-05 | SEND MAP response | RESP captured, not tested | COACTVWC:583-590; COACTUPC:3594-3601 |
| E-06 | Money input longer than S9(10)V99 integer part | `NUMVAL-C` result truncated on COMPUTE (no ON SIZE ERROR) | COACTUPC:1079-1080, 1093-1094, 1107-1108, 1121-1122, 1135-1136 |
| E-07 | Optional field left empty (Middle Name, Address Line 2, Govt Id, both phones) | Stored value comes from LOW-VALUES; empty phone becomes `(` LOW `)` LOW `-` LOW in the record | COACTUPC:1297-1302, 1322-1327, 1401-1406, 4027-4041 |
| E-08 | Lowercase `y`/`n` in a Y/N field | Rejected ("must be Y or N."), but changing only the case of an existing value is not a change | COACTUPC:78, 1685-1688, 1878-1892 |
| E-09 | Wanting to update a different account after fetch | Account Number is protected; user must save/cancel through to the reset screen or PF3 | COACTUPC:2989-2990, 3441-3442, 3500-3502, 979-989 |
| E-10 | Customer lock failure after account lock | Account lock held until task end; no explicit UNLOCK/rollback (see Q-02 for state) | COACTUPC:3934-3942 |
| E-11 | Concurrent change detected | Both records stay locked until RETURN ends the task; no write | COACTUPC:3947-3952, 1015-1019 |
| E-12 | Expiry/Open/Reissue year ≥ 2100 or < 1900 | Rejected ("Century is not valid.") | CSUTLDPY:70-84; CSUTLDWY:9-10 |
| E-13 | DOB equal to today | Rejected ("cannot be in the future ") | CSUTLDPY:350-368 |

---

## 9. Open questions / ambiguities / apparent legacy bugs

| ID | Observation | Cite |
|----|-------------|------|
| Q-01 | **Account REWRITE layout mismatch.** `ACCT-UPDATE-RECORD` omits `ACCT-ADDR-ZIP` X(10) that CVACT01Y has between CURR-CYC-DEBIT and GROUP-ID (and uses FILLER 188 vs 178). On REWRITE, the group id lands in the ACCT-ADDR-ZIP bytes and the real ACCT-GROUP-ID bytes receive FILLER content — existing ACCT-ADDR-ZIP is lost and group id is corrupted. | COACTUPC:418-433, 4002, 4065-4071; CVACT01Y:4-17 |
| Q-02 | `COULD-NOT-LOCK-CUST-FOR-UPDATE` is not handled in 2000-DECIDE-ACTION; it falls to WHEN OTHER → state `C`, so the user sees info "Changes committed to database" alongside error "Could not lock customer record for update" though nothing was written. | COACTUPC:2606-2615, 3934-3942 |
| Q-03 | `DID-NOT-FIND-ACCT-IN-ACCTDAT` / `DID-NOT-FIND-CUST-IN-CUSTDAT` are tested but never set (SETs commented out). After an ACCTDAT NOTFND, the customer is still read; CAVW displays account fields from an unread ACCOUNT-RECORD when the customer is found, and CAUP moves to show-details/edit mode for an account that is not in the master. | COACTVWC:471-491, 704-715, 792; COACTUPC:2577-2578, 3627-3638, 3719, 3769 |
| Q-04 | After "Record changed by some one else", state returns to `S` but the snapshot is not re-read, so the screen shows the **stale** fetched values and every retry fails the same check until F12; F12 label is hidden in state `S`. | COACTUPC:2611-2612, 2787-2869, 3573-3576 |
| Q-05 | Phone "all blank" test compares `WS-EDIT-US-PHONE-NUMA = SPACES` where NUMC is intended (third clause). Masked in practice because blank inputs are LOW-VALUES. | COACTUPC:2234-2241 |
| Q-06 | Customer id is protected and not edited, yet `CUST-UPDATE-ID` is taken from screen input; Country is protected but still edited as required alpha — confirm which customer fields are meant to be maintainable. | COACTUPC:1224-1229, 1623-1630, 3531, 3547, 4009 |
| Q-07 | No cross-field financial rules (cash limit ≤ credit limit, balance vs. limit, non-negative limits, expiry ≥ open, reissue between open/expiry). Confirm whether the target should add them or preserve legacy permissiveness. | COACTUPC:1484-1527, 2180-2223 |
| Q-08 | The State/ZIP combo table includes military codes (`AA`, `AE`, `AP`…) but the state list does not, so those combos are unreachable. `VALID-PHONE-AREA-CODE` and `VALID-EASY-RECOG-AREA-CODE` tables are unused (only general-purpose codes are accepted). | CSLKPCDY:30-519, 931-1010, 1013-1069, 1073-1313; COACTUPC:2298 |
| Q-09 | CAVW never shows "Displaying details of given Account"; after a successful lookup the info line reverts to the input prompt because 9000-READ-ACCT clears the info message. | COACTVWC:115-116, 528-530, 689 |
| Q-10 | Message inconsistencies: CAVW "Account Filter must  be…" (double space) vs. CAUP "Account Number if supplied must be…"; edit labels "Current Cycle Credit Limit"/"Current Cycle Debit Limit" for non-limit fields; "some one". Preserve verbatim or correct? | COACTVWC:672; COACTUPC:1515, 1522, 522 |
| Q-11 | PF3 forces `CDEMO-USRTYP-USER`, so an admin who navigated here loses admin type in the COMMAREA. | COACTVWC:344; COACTUPC:947 |
| Q-12 | CAUP checks `CDEMO-LAST-MAPSET = 'COCRDLI'` and both programs declare card-list/card-detail/card-update literals, but no in-scope path uses them and the account id is never taken from the incoming COMMAREA. Intended deep-link from Card Management? | COACTVWC:151-166, 176-183; COACTUPC:541-556, 565-572, 3171-3173 |
| Q-13 | Dead / unreachable code: CAUP 2000 WHEN `ACUP-CHANGES-OKAYED-AND-DONE` (state C is intercepted in 0000-MAIN); CAVW IF INPUT-ERROR after the main EVALUATE; duplicate `0000-MAIN-EXIT` paragraph; `1230-`/`1240-EDIT-ALPHANUM-*` never performed; `SEND-LONG-TEXT` never performed; reissue date MOVE overwritten by STRING; duplicate 88 name `DID-NOT-FIND-ACCT-IN-CARDXREF`. | COACTUPC:2625-2632, 979-989, 1955-2107, 3993-4000, 497, 513; COACTVWC:387-392, 408-413, 896-909 |
| Q-14 | `9700-CHECK-CHANGE-IN-REC` exits via `GO TO 9600-WRITE-PROCESSING-EXIT` from inside a nested PERFORM range (non-structured exit from 9700 into 9600's exit). | COACTUPC:3947-3948, 4144, 4190 |
| Q-15 | RECEIVE/SEND MAP responses are not checked (MAPFAIL on CLEAR/PA keys is processed as an empty ENTER). | COACTVWC:610-617; COACTUPC:1039-1045, 3594-3601 |
| Q-16 | Optional/blank fields are persisted as LOW-VALUES (and empty phones as `(`LOW`)`LOW`-`LOW). Target data model needs a decision (NULL vs. spaces). | COACTUPC:1297-1424, 4027-4041 |
| Q-17 | Only the first card returned by the CXACAIX alternate-index READ is placed in `CDEMO-CARD-NUM`; accounts with several cards are not distinguished. | COACTVWC:727-740; COACTUPC:3654-3667 |
| Q-18 | Money values exceeding 10 integer digits are silently truncated (no SIZE ERROR); the 15-char screen field allows such input. | COACTUPC:1078-1080 |

---

## 10. Dependencies on other scopes

| Scope | Dependency | Cite |
|-------|-----------|------|
| Sign-on / Main menu (COSGN00C, COMEN01C, COMEN02Y, COCOM01Y) | Inbound XCTL from menu options 1-2; PF3 return target; shared CARDDEMO-COMMAREA contract | COMEN02Y:25-35; COMEN01C:183-187; COACTVWC:324-352 |
| Card Management (COCRDLIC/COCRDSLC/COCRDUPC, CARDXREF) | CXACAIX/CARDXREF ownership; `CDEMO-CARD-NUM` handed to card screens; `COCRDLI` last-mapset check | COACTVWC:723-740; COACTUPC:3171-3173 |
| Transaction Management (CT00-CT02, signed-off) | Reads the same ACCTDAT/CARDXREF entities this scope rewrites | [TransactionManagement FR](../../migration/transaction-management/docs/TransactionManagement_functional_requirement.md) |
| Bill Payment (COBIL00C) and batch posting/interest (CBTRN02C, CBACT04C) | Also REWRITE ACCTDAT (balances/cycle amounts) — concurrent-change detection (BR-26) and ACCT-ADDR-ZIP corruption (Q-01) affect them | COACTUPC:4109-4145 |
| Date utility CSUTLDTC | LE date validation called from CSUTLDPY | CSUTLDPY:293-296 |
| Data/infra (VSAM defs, AIX build, CSD) | ACCTDAT, CUSTDAT, CXACAIX file definitions | CARDDEMO.CSD:1-7, 50-56, 63-66 |
| Customer data migration (CBIMPORT/CBEXPORT) | Shares CVCUS01Y customer layout | CVCUS01Y:4-23 |

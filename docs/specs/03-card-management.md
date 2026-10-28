# 03 — Card Management (CCLI / CCDL / CCUP): Functional and Technical Specification

| Item | Value |
|------|-------|
| **Scope** | Credit Card List (CCLI), Credit Card Detail / View (CCDL), Credit Card Update (CCUP) |
| **Source** | `choikh0423/aws-mainframe-modernization-carddemo` @ `7fd2344fe22ea20b31dc8ae0d662d9379ec7d284` |
| **Programs** | `app/cbl/COCRDLIC.cbl` (list), `app/cbl/COCRDSLC.cbl` (detail), `app/cbl/COCRDUPC.cbl` (update) |
| **BMS maps** | `app/bms/COCRDLI.bms` (map `CCRDLIA`), `app/bms/COCRDSL.bms` (map `CCRDSLA`), `app/bms/COCRDUP.bms` (map `CCRDUPA`) |
| **Copybooks (in scope)** | `app/cpy/CVACT02Y.cpy` (CARD-RECORD, 150 bytes), `app/cpy/CVACT03Y.cpy` (CARD-XREF-RECORD; not used by these programs, see Q-19), `app/cpy/CVCRD01Y.cpy` (CC-WORK-AREAS) |
| **Shared copybooks used** | `COCOM01Y` (CARDDEMO-COMMAREA), `CSSTRPFY` (AID→PF key mapping), `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSMSG02Y` (ABEND-DATA), `CSUSR01Y`, `DFHAID`, `DFHBMSCA`; `CVCUS01Y` is copied but unused |
| **Entry transactions** | `CCLI` → COCRDLIC, `CCDL` → COCRDSLC, `CCUP` → COCRDUPC (`app/csd/CARDDEMO.CSD:347-369`); main-menu options 3/4/5 (`app/cpy/COMEN02Y.cpy:37-53`) |
| **Batch jobs** | None. (CARDDAT is defined/loaded by `app/jcl/CARDFILE.jcl`, outside this scope.) |
| **Out of scope / linked** | Transaction Management CT00/CT01/CT02 is specified and signed off in [`TransactionManagement_functional_requirement.md`](../../migration/transaction-management/docs/TransactionManagement_functional_requirement.md). Stream inventory: [`CardDemo-Stream-Map.md`](../../migration/transaction-management/docs/CardDemo-Stream-Map.md) (stream #3). |

**Purpose.** The Card Management stream lets a signed-on user browse the credit-card master file (CARDDAT) seven cards at a time, optionally filtered by account number and/or card number, and pick a single card to view (`S`) or update (`U`). The detail screen shows the embossed name, active flag and expiry month/year of one card looked up by card number. The update screen lets the user change embossed name, active status and expiry month/year, then validates, asks for an F5 confirmation, and rewrites the record under an optimistic-concurrency check against the values originally fetched.

All three programs are CICS pseudo-conversational: each interaction ends with `EXEC CICS RETURN TRANSID(<own tranid>) COMMAREA(WS-COMMAREA)` (2000 bytes = CARDDEMO-COMMAREA + program-private area). Cites: COCRDLIC:604-619, COCRDSLC:394-406, COCRDUPC:546-558.

Cite format: `PROGRAM:startLine-endLine` against the file at the commit above. BMS cites use the map name (e.g. `COCRDLI.bms:89`).

---

## 1. Functional requirements

### 1.1 CCLI — Credit Card List (COCRDLIC, map CCRDLIA)

| ID | As a user I can… | Expected visible result | Source cite |
|----|------------------|-------------------------|-------------|
| FR-CRD-L01 | Open the card list from main-menu option 3 ("Credit Card List") or by starting transaction `CCLI` | Screen "List Credit Cards" with header (Tran `CCLI`, Prog `COCRDLIC`, titles, date `MM/DD/YY`, time `HH:MM:SS`), `Page 1`, and up to 7 rows (Account Number, Card Number, Active flag) read from CARDDAT in ascending card-number order starting at the beginning of the file. No restriction by user type is applied (see BR-CRD-05). Info line: "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD" | COCRDLIC:315-343, 572-582, 642-672, 678-743, 917-919, 1123-1260; COMEN02Y:37-41 |
| FR-CRD-L02 | Type an 11-digit Account Number filter and press ENTER | Only cards whose `CARD-ACCT-ID` equals the filter are listed; the filter value stays on screen | COCRDLIC:969, 1003-1030, 1382-1394, 845-848 |
| FR-CRD-L03 | Type a 16-digit Credit Card Number filter and press ENTER | Only the card whose `CARD-NUM` equals the filter is listed; value stays on screen | COCRDLIC:970, 1036-1067, 1396-1405, 857-860 |
| FR-CRD-L04 | Supply both filters | Both conditions must match (AND) | COCRDLIC:1382-1407 |
| FR-CRD-L05 | Enter a non-numeric account filter | Error "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"; account field red with cursor; all row-select fields protected; list is not re-read | COCRDLIC:1017-1025, 872-875, 419-438, 748-831 |
| FR-CRD-L06 | Enter a non-numeric card filter | Error "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER" (only if no account-filter error already set); card field red with cursor; rows protected; list not re-read | COCRDLIC:1052-1062, 877-880, 419-438 |
| FR-CRD-L07 | Apply filters that match no card | Message "NO RECORDS FOUND FOR THIS SEARCH CONDITION."; info line suppressed | COCRDLIC:1233-1245, 924-930 |
| FR-CRD-L08 | Press PF8 when a further page exists | Next 7 matching cards shown, page number +1 | COCRDLIC:486-497, 1191-1214 |
| FR-CRD-L09 | Reach / page past the last page | The page that hits end-of-file shows "NO MORE RECORDS TO SHOW"; pressing PF8 again redisplays the same page, and a further PF8 shows "NO MORE PAGES TO DISPLAY" | COCRDLIC:1215-1221, 1233-1240, 572-582, 905-916, 408-414 |
| FR-CRD-L10 | Press PF7 on page 2 or later | Previous 7 matching cards shown, page number −1 | COCRDLIC:499-513, 1264-1380 |
| FR-CRD-L11 | Press PF7 on page 1 | Page 1 redisplayed with "NO PREVIOUS PAGES TO DISPLAY" | COCRDLIC:439-454, 901-904 |
| FR-CRD-L12 | Type `S` beside one row and press ENTER | Control transfers to Card Detail (COCRDSLC) for that row's account and card number | COCRDLIC:515-541, 1097-1102 |
| FR-CRD-L13 | Type `U` beside one row and press ENTER | Control transfers to Card Update (COCRDUPC) for that row's account and card number | COCRDLIC:543-569, 1097-1102 |
| FR-CRD-L14 | Mark more than one row with S/U | Error "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE"; every marked row's select field red | COCRDLIC:1079-1104, 755-831 |
| FR-CRD-L15 | Type any character other than `S`, `U` or blank in a select field | Error "INVALID ACTION CODE" (unless another error was set first); offending row red | COCRDLIC:1106-1113, 755-831 |
| FR-CRD-L16 | Press PF3 | Return to the main menu (COMEN01C), regardless of how the list was entered | COCRDLIC:384-405 |
| FR-CRD-L17 | Press any key other than ENTER/PF3/PF7/PF8 (PF13-24 alias PF1-12) | Treated as ENTER: current page refreshed with current filters | COCRDLIC:370-380, 572-582; CSSTRPFY:21-78 |
| FR-CRD-L18 | Return to the list from Card Detail (PF3) or from Card Update | List restarts at page 1 with no filters | COCRDLIC:336-343, 458-482, 572-582 |

### 1.2 CCDL — Credit Card Detail (COCRDSLC, map CCRDSLA)

| ID | As a user I can… | Expected visible result | Source cite |
|----|------------------|-------------------------|-------------|
| FR-CRD-D01 | Arrive from the list via `S` | Card is read and shown immediately: Account Number and Card Number (protected), Name on card, Card Active Y/N, Expiry Date `MM` / `YYYY`; info "   Displaying requested details" (three leading spaces) | COCRDSLC:339-348, 736-773, 457-497, 502-512 |
| FR-CRD-D02 | Open Card Detail from main-menu option 4 ("Credit Card View") or by starting `CCDL` | Empty search screen with editable Account Number and Card Number; info "Please enter Account and Card Number" | COCRDSLC:268-272, 349-356, 459-460, 489-492, 509-511; COMEN02Y:43-47 |
| FR-CRD-D03 | Enter Account Number and Card Number and press ENTER | Card details displayed (as D01) | COCRDSLC:357-371, 608-641, 726-773 |
| FR-CRD-D04 | Leave Account Number blank or zero | Error "Account number not provided"; account field shows `*` in red | COCRDSLC:651-661, 541-545 |
| FR-CRD-D05 | Leave Card Number blank or zero | Error "Card number not provided" (if no earlier error); card field `*` in red | COCRDSLC:691-702, 547-551 |
| FR-CRD-D06 | Leave both blank | Error "No input received" | COCRDSLC:637-640 |
| FR-CRD-D07 | Enter a non-numeric Account Number | Error "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"; field red | COCRDSLC:665-674, 533-535 |
| FR-CRD-D08 | Enter a non-numeric Card Number | Error "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"; field red | COCRDSLC:706-715, 537-539 |
| FR-CRD-D09 | Enter a card number that is not on file | Error "Did not find cards for this search condition"; both key fields red | COCRDSLC:755-761, 533-539 |
| FR-CRD-D10 | Press PF3 | Return to the calling program (the list, if called from it) or to the main menu if no caller is recorded | COCRDSLC:305-334 |
| FR-CRD-D11 | Press any key other than ENTER/PF3 | Treated as ENTER | COCRDSLC:291-299 |

### 1.3 CCUP — Credit Card Update (COCRDUPC, map CCRDUPA)

| ID | As a user I can… | Expected visible result | Source cite |
|----|------------------|-------------------------|-------------|
| FR-CRD-U01 | Arrive from the list via `U` | Card is read; Account/Card Number protected; Name (upper-cased), Active Y/N, Expiry month/year editable; info "Details of selected card shown above"; cursor on Name | COCRDUPC:482-497, 1343-1370, 1107-1112, 1181-1190, 1145-1146, 1212-1214 |
| FR-CRD-U02 | Open Card Update from main-menu option 5 ("Credit Card Update") or by starting `CCUP` | Search screen: Account/Card Number editable, detail fields protected and empty; info "Please enter Account and Card Number" | COCRDUPC:388-394, 502-511, 1141-1144, 1173-1180; COMEN02Y:49-53 |
| FR-CRD-U03 | Enter Account Number + Card Number and press ENTER | Card fetched and shown for editing (as U01) | COCRDUPC:535-542, 641-662, 948-966 |
| FR-CRD-U04 | Enter invalid search keys | Same messages as detail: "Account number not provided", "Card number not provided", "No input received", "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER", "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"; blank fields show `*` in red | COCRDUPC:721-800, 656-659, 1243-1261 |
| FR-CRD-U05 | Enter a card number that is not on file | "Did not find cards for this search condition"; key fields red | COCRDUPC:1395-1401 |
| FR-CRD-U06 | Press ENTER without changing anything | Error "No change detected with respect to values fetched." (comparison ignores letter case) | COCRDUPC:679-693 |
| FR-CRD-U07 | Blank the Name on card | "Card name not provided"; Name shows `*` in red | COCRDUPC:806-820, 1268-1272 |
| FR-CRD-U08 | Enter a name containing digits/punctuation | "Card name can only contain alphabets and spaces"; Name red | COCRDUPC:822-837, 1263-1266 |
| FR-CRD-U09 | Enter an Active flag other than `Y`/`N` (or blank) | "Card Active Status must be Y or N"; field red (or `*`) | COCRDUPC:845-873, 1274-1283 |
| FR-CRD-U10 | Enter an expiry month that is blank or not 1-12 | "Card expiry month must be between 1 and 12"; field red (or `*`) | COCRDUPC:877-908, 1287-1296 |
| FR-CRD-U11 | Enter an expiry year that is blank or not 1950-2099 | "Invalid card expiry year"; field red (or `*`) | COCRDUPC:913-944, 1298-1307 |
| FR-CRD-U12 | Enter valid changes and press ENTER | All fields protected; info "Changes validated.Press F5 to save"; "F5=Save F12=Cancel" legend becomes bright | COCRDUPC:710-714, 1149-1150, 1191-1199, 1309-1317 |
| FR-CRD-U13 | Press PF5 on the confirmation screen | Record rewritten; info "Changes committed to database"; fields protected | COCRDUPC:988-1001, 1420-1496, 1151-1152 |
| FR-CRD-U14 | Press PF5 when someone else changed the card since it was fetched | Error "Record changed by some one else. Please review"; the current on-file values are redisplayed for editing; nothing written | COCRDUPC:1451-1457, 1498-1520, 997-998, 1107-1112 |
| FR-CRD-U15 | Press PF5 when the record cannot be read for update | Error "Could not lock record for update"; info "Changes unsuccessful. Please try again" | COCRDUPC:1427-1449, 993-994, 1153-1154 |
| FR-CRD-U16 | Press PF5 when the REWRITE fails | Error "Update of record failed"; info "Changes unsuccessful. Please try again" | COCRDUPC:1477-1492, 995-996, 1155-1156 |
| FR-CRD-U17 | Press PF12 after a card has been fetched | Pending edits discarded; card re-read and original values redisplayed | COCRDUPC:413-419, 482-497, 958-966 |
| FR-CRD-U18 | Press any key after a save succeeded or failed | If the update was started from the card list: return to the list. Otherwise: blank search screen to update another card | COCRDUPC:435-476, 517-528 |
| FR-CRD-U19 | Press PF3 at any time | SYNCPOINT, then return to the calling program or main menu | COCRDUPC:435-476 |
| FR-CRD-U20 | Press PF5 before confirmation, PF12 before a fetch, or any other key | Treated as ENTER | COCRDUPC:413-424 |

---

## 2. Screen / interface specification

All three maps: 24×80, `CTRL=(FREEKB)`, `DSATTS/MAPATTS=(COLOR,HILIGHT,PS,VALIDN)`. Common header fields (all output, ASKIP): `TRNNAME` (4), `TITLE01` (40), `CURDATE` (8, `MM/DD/YY`), `PGMNAME` (8), `TITLE02` (40), `CURTIME` (8, `HH:MM:SS`). They are filled by `1100-SCREEN-INIT` (COCRDLIC:642-672) / `1100-SCREEN-INIT` (COCRDSLC:427-451) / `3100-SCREEN-INIT` (COCRDUPC:1052-1076).

### 2.1 COCRDLI / CCRDLIA — "List Credit Cards"

| Screen label | BMS field | Len | I/O | BMS attributes | Program edit / attribute rules | Cite |
|---|---|---|---|---|---|---|
| Page | `PAGENO` | 3 | O | default | `WS-CA-SCREEN-NUM` (PIC 9(1)) | COCRDLI.bms:82; COCRDLIC:667 |
| Account Number : | `ACCTSID` | 11 | I/O | FSET,IC,NORM,UNPROT, GREEN, UNDERLINE | Optional. Blank/spaces/low-values/all-zeros = no filter; else must be NUMERIC. Error → RED, cursor | COCRDLI.bms:89; COCRDLIC:1003-1030, 837-886 |
| Credit Card Number: | `CARDSID` | 16 | I/O | FSET,NORM,UNPROT, GREEN, UNDERLINE | Optional. Same blank rule; else must be NUMERIC. Error → RED, cursor | COCRDLI.bms:101; COCRDLIC:1036-1067 |
| Select (rows 1-7) | `CRDSEL1`…`CRDSEL7` | 1 | I/O | FSET,NORM,PROT, UNDERLINE | Program makes the field enterable (DFHBMFSE) when the row has data and no filter error; protected otherwise. Valid: `S`, `U`, blank/low-values. Error → RED | COCRDLI.bms:140-319; COCRDLIC:748-831, 1073-1117 |
| (hidden) | `CRDSTP2`…`CRDSTP7` | 1 | – | ASKIP,DRK,FSET | Not referenced by COCRDLIC (no `CRDSTP1`) | COCRDLI.bms:169-304 |
| Account Number (row) | `ACCTNO1`…`ACCTNO7` | 11 | O | NORM,PROT | `CARD-ACCT-ID` | COCRDLIC:678-743 |
| Card Number (row) | `CRDNUM1`…`CRDNUM7` | 16 | O | NORM,PROT | `CARD-NUM` | COCRDLIC:678-743 |
| Active (row) | `CRDSTS1`…`CRDSTS7` | 1 | O | NORM,PROT | `CARD-ACTIVE-STATUS` (raw) | COCRDLIC:678-743 |
| (info line) | `INFOMSG` | 45 | O | PROT, NEUTRAL | Dark by default; NEUTRAL when an info message is shown | COCRDLI.bms:324; COCRDLIC:669-671, 926-930 |
| (error line) | `ERRMSG` | 78 | O | ASKIP,BRT,FSET, RED | `WS-ERROR-MSG` | COCRDLI.bms:331; COCRDLIC:924 |
| (legend) | – | 78 | O | ASKIP | `  F3=Exit F7=Backward  F8=Forward` | COCRDLI.bms:339 |

**PF-key map (CCLI)**

| Key | Action | Cite |
|---|---|---|
| ENTER | Validate filters + selections; refresh current page, or XCTL to detail (`S`) / update (`U`) | COCRDLIC:517-582 |
| PF3 | XCTL to COMEN01C | COCRDLIC:384-405 |
| PF7 | Previous page (or "NO PREVIOUS PAGES TO DISPLAY" on page 1) | COCRDLIC:439-454, 499-513 |
| PF8 | Next page (or end-of-list messages) | COCRDLIC:486-497, 905-916 |
| any other | Treated as ENTER | COCRDLIC:370-380 |

### 2.2 COCRDSL / CCRDSLA — "View Credit Card Detail"

| Screen label | BMS field | Len | I/O | BMS attributes | Program edit / attribute rules | Cite |
|---|---|---|---|---|---|---|
| Account Number : | `ACCTSID` | 11 | I/O | FSET,IC,NORM,UNPROT, UNDERLINE | Required, non-zero, NUMERIC. Protected (DFHBMPRF, default colour) when entered from the list; `*`+RED when blank on re-entry; RED on error | COCRDSL.bms:84; COCRDSLC:502-551, 647-679 |
| Card Number : | `CARDSID` | 16 | I/O | FSET,NORM,UNPROT, UNDERLINE | Required, non-zero, NUMERIC. Same protection/colour rules | COCRDSL.bms:96; COCRDSLC:685-720 |
| Name on card : | `CRDNAME` | 50 | O | UNDERLINE | `CARD-EMBOSSED-NAME` | COCRDSL.bms:107; COCRDSLC:475-476 |
| Card Active Y/N : | `CRDSTCD` | 1 | O | ASKIP, UNDERLINE | `CARD-ACTIVE-STATUS` | COCRDSL.bms:116; COCRDSLC:484 |
| Expiry Date : (month) | `EXPMON` | 2 | O | ASKIP, UNDERLINE | `CARD-EXPIRAION-DATE(6:2)` | COCRDSL.bms:126; COCRDSLC:477-480 |
| `/` (year) | `EXPYEAR` | 4 | O | ASKIP, UNDERLINE | `CARD-EXPIRAION-DATE(1:4)` | COCRDSL.bms:133; COCRDSLC:482 |
| (info line) | `INFOMSG` | 40 | O | PROT, NEUTRAL | Dark when empty, NEUTRAL otherwise | COCRDSL.bms:139; COCRDSLC:553-557 |
| (error line) | `ERRMSG` | 80 | O | ASKIP,BRT,FSET, RED | `WS-RETURN-MSG` | COCRDSL.bms:144; COCRDSLC:494 |
| (legend) | `FKEYS` | 75 | O | ASKIP, YELLOW | `ENTER=Search Cards  F3=Exit` | COCRDSL.bms:148-152 |

PF keys: ENTER = search/redisplay (COCRDSLC:357-371); PF3 = return to caller/menu (COCRDSLC:305-334); other = ENTER (COCRDSLC:291-299). The CVV is never shown.

### 2.3 COCRDUP / CCRDUPA — "Update Credit Card Details"

| Screen label | BMS field | Len | I/O | BMS attributes | Program edit / attribute rules | Cite |
|---|---|---|---|---|---|---|
| Account Number : | `ACCTSID` | 11 | I/O | FSET,IC,NORM,PROT, UNDERLINE | Enterable only while no card is fetched; required, non-zero, NUMERIC | COCRDUP.bms:84; COCRDUPC:1172-1208, 721-756 |
| Card Number : | `CARDSID` | 16 | I/O | FSET,NORM,UNPROT, UNDERLINE | Enterable only while no card is fetched; required, non-zero, NUMERIC | COCRDUP.bms:96; COCRDUPC:762-800 |
| Name on card : | `CRDNAME` | 50 | I/O | UNPROT, UNDERLINE | Enterable after fetch. Required; letters A-Z/a-z and spaces only | COCRDUP.bms:107; COCRDUPC:806-840 |
| Card Active Y/N : | `CRDSTCD` | 1 | I/O | UNPROT, UNDERLINE | Required; `Y` or `N` (upper case only) | COCRDUP.bms:117; COCRDUPC:845-873 |
| Expiry Date : (month) | `EXPMON` | 2 | I/O | UNPROT, UNDERLINE, JUSTIFY=RIGHT | Required; 1-12 | COCRDUP.bms:127; COCRDUPC:877-908 |
| `/` (year) | `EXPYEAR` | 4 | I/O | UNPROT, UNDERLINE, JUSTIFY=RIGHT | Required; 1950-2099 | COCRDUP.bms:135; COCRDUPC:913-944 |
| (hidden day) | `EXPDAY` | 2 | I/O | DRK,FSET,PROT | Carries the stored expiry day; not editable, not validated | COCRDUP.bms:142; COCRDUPC:621, 1110, 1123, 1285 |
| (info line) | `INFOMSG` | 40 | O | PROT, NEUTRAL | Dark when empty, bright otherwise | COCRDUP.bms:149; COCRDUPC:1309-1313 |
| (error line) | `ERRMSG` | 80 | O | ASKIP,BRT,FSET, RED | `WS-RETURN-MSG` | COCRDUP.bms:154; COCRDUPC:1163 |
| (legend) | `FKEYS` | 21 | O | ASKIP, YELLOW | `ENTER=Process F3=Exit` | COCRDUP.bms:158-162 |
| (legend) | `FKEYSC` | 18 | O | ASKIP,DRK, YELLOW | `F5=Save F12=Cancel`; bright only when confirmation is pending | COCRDUP.bms:163-167; COCRDUPC:1315-1317 |

Field protection by state (COCRDUPC:1172-1208):

| State (`CCUP-CHANGE-ACTION`) | Acct/Card | Name/Status/Month/Year |
|---|---|---|
| not fetched (low-values/space) | enterable (FSE) | protected |
| `S` show details, `E` changes not OK | protected | enterable |
| `N` validated-not-confirmed, `C` done | protected | protected |
| other (`L`, `F`) | enterable | protected |

**PF-key map (CCUP)**

| Key | Valid when | Action | Cite |
|---|---|---|---|
| ENTER | always | Fetch (if keys pending) or validate changes | COCRDUPC:535-542, 948-1027 |
| PF3 | always | SYNCPOINT + XCTL to caller/menu | COCRDUPC:435-476 |
| PF5 | state `N` only | Save (READ UPDATE + concurrency check + REWRITE) | COCRDUPC:416, 988-1001 |
| PF12 | after a fetch | Re-read card, discard edits | COCRDUPC:418, 484-497, 958-966 |
| other / PF5 / PF12 when not valid | – | Treated as ENTER | COCRDUPC:422-424 |

---

## 3. Business rules and calculations

| ID | Rule | Source cite |
|----|------|-------------|
| BR-CRD-01 | The list shows at most 7 cards per page (`WS-MAX-SCREEN-LINES = 7`). | COCRDLIC:177-178, 1191 |
| BR-CRD-02 | The list browses CARDDAT by primary key (card number, 16 bytes) with `STARTBR … GTEQ`. The first page starts from an initialised key, i.e. the start of the file. | COCRDLIC:1123-1136, 1264-1280 |
| BR-CRD-03 | Filtering happens in the program while browsing: each record read is kept only if (account filter blank OR `CARD-ACCT-ID = CC-ACCT-ID`) AND (card filter blank OR `CARD-NUM = CC-CARD-NUM-N`). The account alternate index (`CARDAIX`) is declared but never used. | COCRDLIC:1382-1407, 215-217 |
| BR-CRD-04 | A list filter counts as "not supplied" when it is low-values, spaces or numerically zero. Otherwise it must pass the COBOL `NUMERIC` class test. | COCRDLIC:1007-1029, 1042-1066 |
| BR-CRD-05 | **Admin vs regular user:** the program header says admins see all cards and non-admins see only the COMMAREA account. **No such logic exists.** `CDEMO-USER-TYPE` is never tested, `CDEMO-ACCT-ID` from the COMMAREA is never used as a filter, and every user sees every card unless they type a filter. The program actually forces `CDEMO-USRTYP-USER` on each transfer. | COCRDLIC:4-7, 320, 388, 466, 522, 550, 1382-1407 |
| BR-CRD-06 | "Next page exists" is decided by one extra `READNEXT` after the 7th displayed row. That look-ahead record is **not** filtered. `WS-CA-LAST-CARD-NUM` becomes the look-ahead key, which is the start key for PF8. | COCRDLIC:1191-1231, 486-489 |
| BR-CRD-07 | PF7 re-positions at the first card of the current page, discards that record with one `READPREV`, then fills rows 7→1 with filtered records read backwards. The new first-row key becomes `WS-CA-FIRST-CARD-NUM`. | COCRDLIC:504-509, 1264-1372 |
| BR-CRD-08 | Page number: set to 1 on fresh entry, +1 on PF8 with a next page, −1 on PF7 when not on page 1. Held in `WS-CA-SCREEN-NUM PIC 9(1)`. | COCRDLIC:237-238, 324, 341, 492, 508, 1177-1178 |
| BR-CRD-09 | The "last page shown" flag is reset by every key except PF8, so "NO MORE PAGES TO DISPLAY" only appears on the second consecutive PF8 at the end. | COCRDLIC:239-241, 408-414, 905-916 |
| BR-CRD-10 | Row selection: count every `S` and `U` across the 7 select fields. More than one is an error. Any value other than `S`, `U`, space or low-values is an error. Exactly one gives `I-SELECTED`. Selection is only checked when the filters are valid. | COCRDLIC:76-82, 1073-1117 |
| BR-CRD-11 | On `S`/`U`, the selected row's account and card number go into `CDEMO-ACCT-ID` / `CDEMO-CARD-NUM`, then XCTL to COCRDSLC / COCRDUPC with `CDEMO-FROM-PROGRAM = 'COCRDLIC'`, `CDEMO-LAST-MAPSET = 'COCRDLI'` and `CDEMO-PGM-ENTER`. | COCRDLIC:517-569 |
| BR-CRD-12 | Detail and update both require Account Number and Card Number: non-blank, non-zero, NUMERIC. Both blank → "No input received". | COCRDSLC:608-724; COCRDUPC:641-662, 721-804 |
| BR-CRD-13 | Detail and update read CARDDAT **by card number only**. The account number is checked for format but never compared with the card's `CARD-ACCT-ID`. | COCRDSLC:736-750; COCRDUPC:1376-1390 |
| BR-CRD-14 | Expiry date is stored as `CARD-EXPIRAION-DATE X(10)` in `YYYY-MM-DD` layout. Screens split it into year `(1:4)`, month `(6:2)`, day `(9:2)`. The day is hidden on update and absent on detail. | CVACT02Y:9; COCRDSLC:84-92, 477-482; COCRDUPC:1361-1366 |
| BR-CRD-15 | On fetch for update, the stored embossed name is converted to upper case before display and snapshot. | COCRDUPC:1356-1360 |
| BR-CRD-16 | Change detection: `UPPER-CASE(new name+expiry Y/M/D+status) = UPPER-CASE(old …)` → "No change detected…". So a case-only change of the name counts as no change. | COCRDUPC:295-301, 307-313, 679-683 |
| BR-CRD-17 | Name: required (not low-values/spaces/zeros). After converting every A-Z/a-z to space, the trimmed length must be 0, i.e. only letters and spaces are allowed. | COCRDUPC:255-259, 806-840 |
| BR-CRD-18 | Active status: required; must be exactly `Y` or `N`. | COCRDUPC:89-91, 845-873 |
| BR-CRD-19 | Expiry month: required (zeros = blank); numeric value 1-12. | COCRDUPC:92-95, 877-908 |
| BR-CRD-20 | Expiry year: required (zeros = blank); numeric value 1950-2099. There is no check that the date is in the future. | COCRDUPC:96-99, 913-944 |
| BR-CRD-21 | All field edits run (name, status, month, year, in that order). Only the **first** error message is kept (`WS-RETURN-MSG-OFF` guard), but every failing field is coloured. The cursor goes to the first failing field in the order acct, card, name, status, month, year. | COCRDUPC:696-708, 1210-1235, 1263-1307 |
| BR-CRD-22 | Saving takes two steps: a clean validation moves the state to `N` (confirmation pending), and only PF5 in state `N` saves. ENTER in state `N` re-shows the confirmation. | COCRDUPC:413-419, 685-693, 710-714, 988-1007 |
| BR-CRD-23 | **Optimistic concurrency:** on PF5 the program does `READ … UPDATE` (record lock), upper-cases the on-file name, and compares on-file CVV, name, expiry year/month/day and active status with the snapshot (`CCUP-OLD-*`) taken at fetch. Any difference means no REWRITE, the snapshot is refreshed from file and the state goes back to `S`. Account ID and card number are not part of the comparison. | COCRDUPC:1427-1457, 1498-1520, 1343-1370, 997-998 |
| BR-CRD-24 | Rewritten record (`CARD-UPDATE-RECORD`, same layout as CVACT02Y): `CARD-NUM` ← screen card number; `CARD-ACCT-ID` ← screen account number; `CARD-CVV-CD` ← `CCUP-NEW-CVV-CD` (see Q-04); name ← as typed (case kept); expiry ← `new-year '-' new-month '-' hidden-day`; status ← new status; FILLER ← initialised. | COCRDUPC:314-321, 1461-1483 |
| BR-CRD-25 | Update state machine `CCUP-CHANGE-ACTION`: low-values/space = details not fetched; `S` = show details; `E` = changes not OK; `N` = OK, not confirmed; `C` = done; `L` = lock error; `F` = rewrite failed. | COCRDUPC:276-290 |
| BR-CRD-26 | After `C`, `L` or `F`, the next interaction returns to the caller if `CDEMO-LAST-MAPSET = 'COCRDLI'` (started from the list). Otherwise the program area and keys are cleared and a fresh search screen is shown. | COCRDUPC:435-476, 517-528 |
| BR-CRD-27 | Return routing for detail and update: PF3 XCTLs to `CDEMO-FROM-PROGRAM`/`CDEMO-FROM-TRANID`, or to `COMEN01C`/`CM00` when these are blank. Update also issues `SYNCPOINT` and zeroes `CDEMO-ACCT-ID`/`CDEMO-CARD-NUM` when it came from the list. The list's PF3 always goes to `COMEN01C`. | COCRDSLC:309-334; COCRDUPC:442-476; COCRDLIC:384-405 |
| BR-CRD-28 | Fresh-start detection. List: `CDEMO-PGM-ENTER` with a foreign `CDEMO-FROM-PROGRAM` resets paging and filters. Detail/update: `EIBCALEN = 0`, or entry from `COMEN01C` not in re-enter mode, initialises the whole CARDDEMO-COMMAREA. | COCRDLIC:315-343; COCRDSLC:268-279; COCRDUPC:388-401 |
| BR-CRD-29 | AID handling: PF13-PF24 are aliases for PF1-PF12. Keys not valid in the current program/state are treated as ENTER. | CSSTRPFY:21-78; COCRDLIC:370-380; COCRDSLC:291-299; COCRDUPC:413-424 |

---

## 4. Data access

| Program | File (CICS name) | Dataset | Operation | Key | Record copybook | Cite |
|---|---|---|---|---|---|---|
| COCRDLIC | `CARDDAT` | `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` | BROWSE: STARTBR (GTEQ), READNEXT, READPREV, ENDBR | `CARD-NUM` X(16) via `WS-CARD-RID-CARDNUM` | CVACT02Y `CARD-RECORD` | COCRDLIC:1129-1259, 1273-1377 |
| COCRDSLC | `CARDDAT` | same | READ | `CARD-NUM` X(16) | CVACT02Y | COCRDSLC:742-750 |
| COCRDSLC | `CARDAIX` | `AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX.PATH` | READ (paragraph `9150-GETCARD-BYACCT` is never PERFORMed: dead code) | `CARD-ACCT-ID` 9(11) | CVACT02Y | COCRDSLC:779-812 |
| COCRDUPC | `CARDDAT` | same | READ (fetch), READ UPDATE, REWRITE | `CARD-NUM` X(16) | CVACT02Y / `CARD-UPDATE-RECORD` | COCRDUPC:1382-1390, 1427-1436, 1477-1483 |

File definitions: `CARDDAT` and `CARDAIX` have `READINTEG(UNCOMMITTED)`, `UPDATEMODEL(LOCKING)`, `RECOVERY(NONE)` (app/csd/CARDDEMO.CSD:13-33). CARD-XREF (CVACT03Y), ACCTDAT and CUSTDAT are **not** accessed in this scope: CVACT03Y and CVACT01Y are commented out (COCRDSLC:237, 231; COCRDUPC:356, 350), and CVCUS01Y is copied but not referenced (COCRDSLC:240; COCRDUPC:359).

**CARD-RECORD (CVACT02Y, 150 bytes):** `CARD-NUM X(16)`, `CARD-ACCT-ID 9(11)`, `CARD-CVV-CD 9(03)`, `CARD-EMBOSSED-NAME X(50)`, `CARD-EXPIRAION-DATE X(10)`, `CARD-ACTIVE-STATUS X(01)`, `FILLER X(59)` (CVACT02Y:4-11).

**CVCRD01Y work area used by all three:** `CCARD-AID` (with 88s ENTER/CLEAR/PA1/PA2/PFK01-12), `CCARD-NEXT-PROG/MAPSET/MAP`, `CCARD-ERROR-MSG X(75)`, `CCARD-RETURN-MSG X(75)`, `CC-ACCT-ID X(11)`/`-N 9(11)`, `CC-CARD-NUM X(16)`/`-N 9(16)`, `CC-CUST-ID X(9)`/`-N 9(9)` (CVCRD01Y:1-42).

---

## 5. Message catalogue (exact text)

| # | Program | Text (verbatim) | Line | Condition | Cite |
|---|---|---|---|---|---|
| M01 | COCRDLIC | `TYPE S FOR DETAIL, U TO UPDATE ANY RECORD` | info | Default info line on any normal display | COCRDLIC:115-116, 910-919 |
| M02 | COCRDLIC | `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` | error | Account filter not numeric | COCRDLIC:1021-1023 |
| M03 | COCRDLIC | `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` | error | Card filter not numeric (and no earlier error) | COCRDLIC:1056-1060 |
| M04 | COCRDLIC | `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE` | error | More than one S/U | COCRDLIC:123-124, 1084-1086 |
| M05 | COCRDLIC | `INVALID ACTION CODE` | error | Select value not S/U/blank | COCRDLIC:125-126, 1108-1113 |
| M06 | COCRDLIC | `NO RECORDS FOUND FOR THIS SEARCH CONDITION.` | error | End of file on page 1 with 0 rows | COCRDLIC:121-122, 1241-1245 |
| M07 | COCRDLIC | `NO MORE RECORDS TO SHOW` | error | End of file reached while filling or looking ahead | COCRDLIC:1218-1221, 1238-1240 |
| M08 | COCRDLIC | `NO PREVIOUS PAGES TO DISPLAY` | error | PF7 on page 1 | COCRDLIC:901-904 |
| M09 | COCRDLIC | `NO MORE PAGES TO DISPLAY` | error | PF8, no next page, last page already shown | COCRDLIC:905-909 |
| M10 | COCRDLIC | `PF03 PRESSED.EXITING` | – | Set on PF3 but never displayed (XCTL follows) | COCRDLIC:119-120, 396 |
| M11 | COCRDSLC, COCRDUPC | `Please enter Account and Card Number` | info | Search keys needed (fresh entry / no info message) | COCRDSLC:131-132, 489-492; COCRDUPC:162-163, 1140-1158 |
| M12 | COCRDSLC | `   Displaying requested details` (three leading spaces) | info | Card found | COCRDSLC:129-130, 753-754 |
| M13 | COCRDSLC, COCRDUPC | `Account number not provided` | error | Account blank/zero | COCRDSLC:138-139, 656-658; COCRDUPC:177-178, 730-732 |
| M14 | COCRDSLC, COCRDUPC | `Card number not provided` | error | Card blank/zero | COCRDSLC:140-141, 696-698; COCRDUPC:179-180, 773-775 |
| M15 | COCRDSLC, COCRDUPC | `No input received` | error | Both keys blank | COCRDSLC:142-143, 637-640; COCRDUPC:185-186, 656-659 |
| M16 | COCRDSLC, COCRDUPC | `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` | error | Account not numeric | COCRDSLC:669-671; COCRDUPC:744-746 |
| M17 | COCRDSLC, COCRDUPC | `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` | error | Card not numeric | COCRDSLC:710-712; COCRDUPC:788-790 |
| M18 | COCRDSLC, COCRDUPC | `Did not find cards for this search condition` | error | READ NOTFND | COCRDSLC:153-154, 755-761; COCRDUPC:203-204, 1395-1401 |
| M19 | COCRDSLC | `Did not find this account in cards database` | error | AIX READ NOTFND, dead code only | COCRDSLC:151-152, 796-799 |
| M20 | COCRDUPC | `Details of selected card shown above` | info | State `S` | COCRDUPC:160-161, 1145-1146 |
| M21 | COCRDUPC | `Update card details presented above.` | info | State `E` (edit errors) | COCRDUPC:164-165, 1147-1148 |
| M22 | COCRDUPC | `Changes validated.Press F5 to save` | info | State `N` | COCRDUPC:166-167, 1149-1150 |
| M23 | COCRDUPC | `Changes committed to database` | info | State `C` | COCRDUPC:168-169, 1151-1152 |
| M24 | COCRDUPC | `Changes unsuccessful. Please try again` | info | State `L` or `F` | COCRDUPC:170-171, 1153-1156 |
| M25 | COCRDUPC | `No change detected with respect to values fetched.` | error | New = old (case-insensitive) | COCRDUPC:187-188, 679-683 |
| M26 | COCRDUPC | `Card name not provided` | error | Name blank | COCRDUPC:181-182, 811-818 |
| M27 | COCRDUPC | `Card name can only contain alphabets and spaces` | error | Name has other characters | COCRDUPC:183-184, 828-835 |
| M28 | COCRDUPC | `Card Active Status must be Y or N` | error | Status blank or not Y/N | COCRDUPC:195-196, 850-870 |
| M29 | COCRDUPC | `Card expiry month must be between 1 and 12` | error | Month blank or out of range | COCRDUPC:197-198, 883-905 |
| M30 | COCRDUPC | `Invalid card expiry year` | error | Year blank or out of 1950-2099 | COCRDUPC:199-200, 916-941 |
| M31 | COCRDUPC | `Could not lock record for update` | error | READ UPDATE not NORMAL | COCRDUPC:205-206, 1441-1448 |
| M32 | COCRDUPC | `Record changed by some one else. Please review` | error | Concurrency mismatch | COCRDUPC:207-208, 1503-1511 |
| M33 | COCRDUPC | `Update of record failed` | error | REWRITE not NORMAL | COCRDUPC:209-210, 1488-1492 |
| M34 | all | `File Error: ` + op (`READ`) + ` on ` + file (`CARDDAT `/`CARDAIX `) + ` returned RESP ` + resp + `,RESP2 ` + resp2 | error | Unexpected RESP on READ/browse (COCRDLIC's first filler is `'File Error:'` in X(12)) | COCRDLIC:153-171, 1222-1230, 1246-1254, 1308-1316, 1361-1369; COCRDSLC:102-121, 762-771; COCRDUPC:133-152, 1402-1411 |
| M35 | COCRDSLC | `UNEXPECTED DATA SCENARIO` | plain text | Main EVALUATE falls to WHEN OTHER; SEND TEXT then RETURN (ends conversation) | COCRDSLC:373-380, 838-848 |
| M36 | COCRDUPC | `UNEXPECTED DATA SCENARIO` (ABEND-MSG, code `0001`) | abend screen | `2000-DECIDE-ACTION` WHEN OTHER | COCRDUPC:1019-1026 |
| M37 | COCRDSLC, COCRDUPC | `UNEXPECTED ABEND OCCURRED.` | abend screen | HANDLE ABEND routine, when no ABEND-MSG was set | COCRDSLC:857-878; COCRDUPC:1531-1553 |
| – | COCRDSLC, COCRDUPC | Defined but never displayed: `PF03 pressed.Exiting `, `Account number must be a non zero 11 digit number`, `Card number if supplied must be a 16 digit number`, `Error reading Card Data File`, `Looks Good.... so far` | – | – | COCRDSLC:136-158; COCRDUPC:175-214 |

Legend: "error" = `ERRMSG` (red, row 23); "info" = `INFOMSG`.

---

## 6. Navigation / integration

### 6.1 Inbound

| Caller | Mechanism | Target | COMMAREA set by caller | Cite |
|---|---|---|---|---|
| COMEN01C (main menu) options 3/4/5, user type `U` | XCTL | COCRDLIC / COCRDSLC / COCRDUPC | `CDEMO-FROM-TRANID`, `CDEMO-FROM-PROGRAM = 'COMEN01C'`, `CDEMO-PGM-CONTEXT = 0` | COMEN02Y:37-53; COMEN01C:177-187 |
| COCRDLIC (`S`/`U`) | XCTL | COCRDSLC / COCRDUPC | `CDEMO-FROM-TRANID='CCLI'`, `CDEMO-FROM-PROGRAM='COCRDLIC'`, `CDEMO-USRTYP-USER`, `CDEMO-PGM-ENTER`, `CDEMO-LAST-MAPSET='COCRDLI'`, `CDEMO-LAST-MAP='CCRDLIA'`, `CDEMO-ACCT-ID`, `CDEMO-CARD-NUM` | COCRDLIC:517-569 |
| COCRDSLC / COCRDUPC (PF3 or update done) | XCTL back | COCRDLIC | `CDEMO-FROM-PROGRAM` = COCRDSLC/COCRDUPC, `CDEMO-PGM-ENTER` | COCRDSLC:323-334; COCRDUPC:456-476 |
| Terminal | Transaction `CCLI` / `CCDL` / `CCUP` with no COMMAREA | each program | `EIBCALEN = 0` → COMMAREA initialised | CARDDEMO.CSD:347-369; COCRDLIC:315-325; COCRDSLC:268-272; COCRDUPC:388-394 |
| Admin menu COADM01C | none | – | `COADM02Y` has no card options; COSGN00C sends admins to COADM01C | COSGN00C:230-238 |

### 6.2 Outbound

| From | Condition | Target | Cite |
|---|---|---|---|
| COCRDLIC | PF3 | XCTL `COMEN01C` | COCRDLIC:384-405 |
| COCRDLIC | ENTER + `S` | XCTL `COCRDSLC` | COCRDLIC:538-541 |
| COCRDLIC | ENTER + `U` | XCTL `COCRDUPC` | COCRDLIC:566-569 |
| COCRDSLC | PF3 | XCTL `CDEMO-FROM-PROGRAM`, or `COMEN01C` when blank | COCRDSLC:305-334 |
| COCRDUPC | PF3, or done/failed after entry from list | SYNCPOINT; XCTL `CDEMO-FROM-PROGRAM`, or `COMEN01C` when blank | COCRDUPC:435-476 |
| all | end of each interaction | `RETURN TRANSID(own) COMMAREA(2000 bytes)` | COCRDLIC:615-619; COCRDSLC:402-406; COCRDUPC:554-558 |

No CALL, LINK, START, TDQ/TSQ or batch submission exists in this scope. COACTVWC/COACTUPC declare literals for COCRDLIC/COCRDSLC/COCRDUPC but at this commit they only XCTL to `CDEMO-TO-PROGRAM`, set to the menu or their caller (COACTVWC:151-181, 326-350).

### 6.3 COMMAREA fields used (COCOM01Y)

| Field | PIC | Use in this scope |
|---|---|---|
| `CDEMO-FROM-TRANID` / `CDEMO-FROM-PROGRAM` | X(4)/X(8) | Caller detection, return routing, own-program re-entry test |
| `CDEMO-TO-TRANID` / `CDEMO-TO-PROGRAM` | X(4)/X(8) | XCTL target on PF3 (detail/update) |
| `CDEMO-USER-TYPE` (`A`/`U`) | X(1) | Only ever **set** to `U`; never tested |
| `CDEMO-PGM-CONTEXT` (0 enter / 1 re-enter) | 9(1) | First-time vs re-entry logic |
| `CDEMO-ACCT-ID` | 9(11) | Selected/entered account; list redisplay of filter |
| `CDEMO-CARD-NUM` | 9(16) | Selected/entered card |
| `CDEMO-ACCT-STATUS` | X(1) | Cleared in unreachable branch COCRDUPC:1017 |
| `CDEMO-LAST-MAP` / `CDEMO-LAST-MAPSET` | X(7)/X(7) | `'COCRDLI'` marks "came from list" (protects keys, return-to-list) |

Program-private COMMAREA areas (appended after CARDDEMO-COMMAREA):
- COCRDLIC `WS-THIS-PROGCOMMAREA`: first/last card key (card num + acct id), `WS-CA-SCREEN-NUM 9(1)`, `WS-CA-LAST-PAGE-DISPLAYED 9(1)` (0 = shown, 9 = not shown), `WS-CA-NEXT-PAGE-IND` (`Y`/low-values), return flag, and 7-row screen data (COCRDLIC:229-260).
- COCRDSLC `CA-FROM-PROGRAM`, `CA-FROM-TRANID`: declared, not used (COCRDSLC:200-203).
- COCRDUPC `CCUP-CHANGE-ACTION`, `CCUP-OLD-DETAILS` (snapshot), `CCUP-NEW-DETAILS`, `CARD-UPDATE-RECORD` (COCRDUPC:274-321).

---

## 7. Edge cases, error handling, abend / return codes

| Case | Behaviour | Cite |
|---|---|---|
| Browse READNEXT/READPREV RESP other than NORMAL/DUPREC/ENDFILE | Loop ends; M34 shown on the list screen; ENDBR issued | COCRDLIC:1246-1258, 1308-1317, 1361-1377 |
| READPREV hits start of file during PF7 | Treated as an error (WHEN OTHER) → M34, not a "top of list" message | COCRDLIC:1308-1317, 1361-1369 |
| DUPREC on browse | Treated as NORMAL | COCRDLIC:1157-1158, 1305-1306, 1333-1334 |
| Detail/update READ RESP other than NORMAL/NOTFND | M34 in error line; account field flagged red | COCRDSLC:762-771; COCRDUPC:1402-1411 |
| `SEND MAP` / `RECEIVE MAP` RESP | Captured in `WS-RESP-CD` but never checked (MAPFAIL ignored) | COCRDLIC:939-946, 963-967; COCRDSLC:569-576, 597-602; COCRDUPC:579-584, 1329-1336 |
| Any CICS abend in COCRDSLC/COCRDUPC | `HANDLE ABEND LABEL(ABEND-ROUTINE)` sends `ABEND-DATA` (code, culprit = program, reason, message), cancels the handler and abends with `ABCODE('9999')` | COCRDSLC:250-252, 857-878; COCRDUPC:370-372, 1531-1556 |
| COCRDLIC abend | No HANDLE ABEND; default CICS behaviour | COCRDLIC:297-623 |
| COCRDSLC unexpected `CDEMO-PGM-CONTEXT` | M35 as plain text, then `RETURN` without TRANSID (conversation ends) | COCRDSLC:373-380, 838-848 |
| COCRDUPC unexpected state in `2000-DECIDE-ACTION` | ABEND-CODE `0001`, ABEND-MSG `UNEXPECTED DATA SCENARIO`, then ABCODE `9999` | COCRDUPC:1019-1026 |
| Record lock lifetime | `READ UPDATE` lock is held until the task ends (RETURN) or the REWRITE; there is no explicit UNLOCK when the concurrency check fails | COCRDUPC:1427-1457, 554-558 |
| Transactional recovery | `SYNCPOINT` only on exit from COCRDUPC; CARDDAT is `RECOVERY(NONE)` | COCRDUPC:469-471; CARDDEMO.CSD:25-33 |
| Debug exits | `SEND-PLAIN-TEXT` / `SEND-LONG-TEXT` in COCRDLIC are never PERFORMed | COCRDLIC:1422-1454 |

---

## 8. Open questions, ambiguities and apparent legacy bugs

These are recorded as found and **not** fixed. The migration team must decide whether to keep or correct each one.

| ID | Finding | Cite |
|----|---------|------|
| Q-01 | **Admin vs regular-user filtering is not implemented.** The header promises "All cards if no context passed and admin user / Only the ones associated with ACCT in COMMAREA if user is not admin". In the code every user sees all cards, and `CDEMO-ACCT-ID` from the COMMAREA is never applied as a filter. Should the target system enforce the documented rule? | COCRDLIC:4-7, 1382-1407 |
| Q-02 | Every transfer forces `CDEMO-USRTYP-USER`, so an admin's user type is lost. Admins also have no menu path to these screens: COSGN00C sends them to COADM01C, whose options (COADM02Y) contain no card programs. | COCRDLIC:320, 388, 466, 522, 550; COCRDSLC:326; COCRDUPC:464; COSGN00C:230-238 |
| Q-03 | **Account/card mismatch.** Detail and update look up only by card number; the typed account is never checked against `CARD-ACCT-ID`. On update, the REWRITE stores the *screen* account number in `CARD-ACCT-ID`. Via the menu path, a user could therefore move a card to any 11-digit account. | COCRDSLC:736-750; COCRDUPC:1376-1390, 1463 |
| Q-04 | **CVV overwritten.** `CARD-UPDATE-CVV-CD` is built from `CCUP-NEW-CVV-CD`, which is initialised to spaces and never filled (`CCUP-OLD-CVV-CD` holds the real value). The stored CVV is replaced by a non-numeric/zero value on every successful update. | COCRDUPC:586, 1354, 1464-1465 |
| Q-05 | Expiry day is hidden, carried over and not validated against the new month (day 31 kept when the month changes to 02 gives an invalid date). There is no "not in the past" check (year floor 1950). | COCRDUPC:621, 1123, 1467-1474, 96-99 |
| Q-06 | Month/year are range-checked through 88-levels on `PIC 9` REDEFINES of `PIC X` input. For non-numeric input (e.g. `AB`) the result depends on the compiler (invalid numeric data). Single-digit or right-justified input with spaces (`JUSTIFY=RIGHT`) has the same problem. | COCRDUPC:92-99, 896-898, 932-934; COCRDUP.bms:127-139 |
| Q-07 | On a list input error (multiple selections / invalid action code), the re-read uses `WS-CARD-RID-CARDNUM`, which was initialised at task start and never set from `WS-CA-FIRST-CARD-NUM`. The browse restarts from the beginning of the file while the page number stays the same. | COCRDLIC:300-302, 431-435 (vs 446-447, 574-575) |
| Q-08 | The look-ahead record that decides "next page exists" is not filtered. With a filter active, PF8 can lead to an empty page that says "NO MORE RECORDS TO SHOW". | COCRDLIC:1191-1214 |
| Q-09 | A new filter entered on page > 1 is applied from the current page's first key (ENTER → WHEN OTHER) and the page number is not reset, so matching cards with lower keys are only reachable via PF7. Because PF7's backward browse treats start-of-file as a file error (M34), those cards can surface an error instead. | COCRDLIC:572-582, 1308-1317 |
| Q-10 | The page counter `WS-CA-SCREEN-NUM PIC 9(1)` wraps after page 9 (9+1 → 0), and page 0 is not "first page" for PF7. The BMS `PAGENO` field is 3 long. | COCRDLIC:237-238, 492; COCRDLI.bms:82 |
| Q-11 | `WS-CA-FIRST-CARD-NUM` is only updated when at least one row is shown, so an empty filtered page keeps stale paging keys. | COCRDLIC:1173-1176 |
| Q-12 | When no row is selected, `I-SELECTED = 0` and `VIEW-REQUESTED-ON(0)` / `UPDATE-REQUESTED-ON(0)` are still evaluated: an out-of-range subscript that relies on NOSSRANGE. | COCRDLIC:1097, 517-519, 545-547 |
| Q-13 | Row 1 attributes differ from rows 2-7: `DFHBMPRF` vs `DFHBMPRO` when protected; `*` marker only on row 1; cursor (`-1`) only on rows 2-7. BMS defines `CRDSTP2-7` but no `CRDSTP1`, and none are used. | COCRDLIC:751-831; COCRDLI.bms:169-304 |
| Q-14 | Returning to the list via PF3 from the detail screen runs `INITIALIZE CARDDEMO-COMMAREA`, which wipes the signed-on `CDEMO-USER-ID`/type and customer/account context. Detail/update do the same on fresh entry from the menu, so PF3 then falls back to COMEN01C because `CDEMO-FROM-PROGRAM` is blank. Is losing the user context acceptable? | COCRDLIC:458-466; COCRDSLC:268-272; COCRDUPC:388-394 |
| Q-15 | Duplicate `WHEN CCARD-AID-PFK07 AND CA-FIRST-PAGE` (the first WHEN has no body and falls through to the second). Harmless, but it shows copy-paste origin. | COCRDLIC:439-454 |
| Q-16 | `LIT-CCLISTMAP` is `'CCRDSLA'` (the detail map) in COCRDSLC and COCRDUPC; the list map is `CCRDLIA`. It is unused for XCTL, so the effect is latent. | COCRDSLC:177-178; COCRDUPC:233-234 |
| Q-17 | The `WHEN CCUP-CHANGES-OKAYED-AND-DONE` branch in `2000-DECIDE-ACTION` is unreachable (the main EVALUATE handles state `C` first), and so is the abend `WHEN OTHER`. | COCRDUPC:517-528, 1011-1027 |
| Q-18 | Saving any field also stores the name in **upper case**, because the fetched name is upper-cased for display and written back as shown. A case-only name change is rejected as "No change detected". | COCRDUPC:1356-1360, 680-683, 1466 |
| Q-19 | The stream map lists CARDXREF (CVACT03Y) for this stream, but no program here reads it (the copybook is commented out). `9150-GETCARD-BYACCT` (CARDAIX) is dead code, and `CVCUS01Y` is unused. Confirm that card↔account integrity is out of scope. | COCRDSLC:237, 240, 779-812; COCRDUPC:356, 359; CardDemo-Stream-Map.md row 3 |
| Q-20 | The name check relies on `FUNCTION LENGTH(FUNCTION TRIM(x)) = 0` for an all-space value. Confirm this on the target runtime: some runtimes return length 1 for an all-space TRIM, which would reject every valid name. | COCRDUPC:823-829 |
| Q-21 | `MOVE DFHBMDAR TO EXPDAYC` puts an attribute constant into the colour byte of the hidden day field. | COCRDUPC:1285 |
| Q-22 | The update concurrency check leaves out `CARD-ACCT-ID`, so a concurrent account re-link is silently overwritten (see Q-03). | COCRDUPC:1503-1508 |
| Q-23 | The detail screen's account/card fields are protected on entry from the list but still FSET, so ENTER re-reads the same card. No way to search another card without PF3; confirm the intent. | COCRDSLC:505-512 |
| Q-24 | Messages say "11 DIGIT" / "16 DIGIT", but the only check is the `NUMERIC` class test. Whether shorter input passes depends on BMS input padding; there is no explicit length check. | COCRDLIC:1015-1022, 1050-1058; COCRDSLC:663-671, 704-712 |
| Q-25 | The update flow never uses the card's active status or expiry to block updates (e.g. inactive cards remain editable). Confirm this is intended. | COCRDUPC:948-1027 |

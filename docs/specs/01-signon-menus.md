# 01 — Sign-on, Main Menu and Admin Menu (CC00 / CM00 / CA00): Functional + Technical Specification

## 1. Header

**Scope:** Sign-on (CC00 / `COSGN00C`), regular-user Main Menu (CM00 / `COMEN01C`), Admin Menu (CA00 / `COADM01C`), and the cross-cutting navigation contract (COMMAREA, XCTL routing, user-type gating, menu option tables) every other online stream relies on.
**SOURCE:** `choikh0423/aws-mainframe-modernization-carddemo` @ `7fd2344fe22ea20b31dc8ae0d662d9379ec7d284`
**Source files:** `app/cbl/COSGN00C.cbl`, `app/cbl/COMEN01C.cbl`, `app/cbl/COADM01C.cbl`; `app/cpy/COMEN02Y.cpy`, `COADM02Y.cpy`, `COCOM01Y.cpy`, `CSUSR01Y.cpy`, `COTTL01Y.cpy`, `CSDAT01Y.cpy`, `CSMSG01Y.cpy`, `CSMSG02Y.cpy`; `app/bms/COSGN00.bms`, `COMEN01.bms`, `COADM01.bms`; `app/csd/CARDDEMO.CSD`. Supporting (read-only, for context): `app/cpy-bms/COSGN00.CPY`, `COMEN01.CPY`, `COADM01.CPY` (generated symbolic maps), `app/jcl/DUSRSECJ.jcl` (USRSEC seed data).
**Entry transactions:** `CC00` → `COSGN00C` (CARDDEMO.CSD:378-387), `CM00` → `COMEN01C` (CARDDEMO.CSD:399-408), `CA00` → `COADM01C` (CARDDEMO.CSD:327-336). No batch jobs in scope (USRSEC is loaded by `DUSRSECJ`, see §5).
**Out of scope / linked:** CT00/CT01/CT02 behaviour is specified and signed off in [`TransactionManagement_functional_requirement.md`](../../migration/transaction-management/docs/TransactionManagement_functional_requirement.md); the stream inventory is in [`CardDemo-Stream-Map.md`](../../migration/transaction-management/docs/CardDemo-Stream-Map.md) (stream #5).

**Purpose.** CC00 authenticates a user ID/password against the `USRSEC` VSAM KSDS and, on success, routes the user by type: admins (`A`) to the Admin Menu, everyone else to the Main Menu. The two menus are table-driven launchers that validate a 1–2 digit option number and `XCTL` to the target program, passing the shared `CARDDEMO-COMMAREA`. PF3 from either menu signs the user off back to the sign-on screen; PF3 on the sign-on screen ends the session with a thank-you text.

Cite notation: `PROGRAM:start-end` = lines in `app/cbl/PROGRAM.cbl`; `COPYBOOK.cpy:…` = `app/cpy/`; `MAPSET.bms:…` = `app/bms/`; `CSD:…` = `app/csd/CARDDEMO.CSD`. All cites verified at `7fd2344`.

---

## 2. Functional requirements (business-visible)

### CC00 — Sign-on (`COSGN00C`, map `COSGN0A` / mapset `COSGN00`)
| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-SGN-01 | Start transaction `CC00` from a blank terminal | Sign-on screen is displayed with header (Tran `CC00`, Prog `COSGN00C`, titles, date `mm/dd/yy`, time `hh:mm:ss`, AppID, SysID), the banner, and the cursor in User ID | COSGN00C:80-83, 145-157, 177-204 |
| FR-SGN-02 | Press ENTER with User ID blank | Screen re-displayed with "Please enter User ID ..."; cursor on User ID | COSGN00C:118-122 |
| FR-SGN-03 | Press ENTER with User ID filled but Password blank | Screen re-displayed with "Please enter Password ..."; cursor on Password | COSGN00C:123-127 |
| FR-SGN-04 | Enter a User ID that is not in USRSEC | "User not found. Try again ..."; cursor on User ID | COSGN00C:247-251 |
| FR-SGN-05 | Enter a valid User ID with the wrong password | "Wrong Password. Try again ..."; cursor on Password | COSGN00C:241-246 |
| FR-SGN-06 | Sign on with valid credentials as an admin user (type `A`) | Admin Menu (CA00 / `COADM01C`) is shown | COSGN00C:222-234 |
| FR-SGN-07 | Sign on with valid credentials as a non-admin user | Main Menu (CM00 / `COMEN01C`) is shown | COSGN00C:222-229, 235-240 |
| FR-SGN-08 | Enter User ID / Password in lower or mixed case | Credentials are matched case-insensitively (both folded to upper case before lookup/compare) | COSGN00C:132-136, 211-223 |
| FR-SGN-09 | Press PF3 on the sign-on screen | Screen is erased and the plain text "Thank you for using CardDemo application..." is shown; the CICS conversation ends | COSGN00C:88-90, 162-172 |
| FR-SGN-10 | Press any key other than ENTER or PF3 (PF1/2/4–24, PA keys, CLEAR) | Sign-on screen re-displayed with "Invalid key pressed. Please see below..." | COSGN00C:91-94 |
| FR-SGN-11 | Sign on when USRSEC cannot be read (any RESP other than NORMAL / NOTFND) | "Unable to verify the User ..."; cursor on User ID | COSGN00C:252-256 |

### CM00 — Main Menu (`COMEN01C`, map `COMEN1A` / mapset `COMEN01`)
| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-MEN-01 | Arrive at the Main Menu after sign-on or on return from a function | Main Menu shows the header (Tran `CM00`, Prog `COMEN01C`) and the 11 numbered options from COMEN02Y as `NN. <name>` | COMEN01C:86-90, 208-220, 238-257, 262-303; COMEN02Y.cpy:21-90 |
| FR-MEN-02 | Type a valid option number (1–11) and press ENTER | Control transfers to the program for that option (table in §3.4); header context passed via COMMAREA | COMEN01C:115-125, 145-146, 177-187 |
| FR-MEN-03 | Type 0, blank, a non-numeric value, or a number greater than 11 | "Please enter a valid option number..." and the menu is re-displayed | COMEN01C:127-134 |
| FR-MEN-04 | As a regular user (type `U`) choose an option whose user type is `A` | "No access - Admin Only option... " and the menu is re-displayed (dormant: no option is currently `A`, see Q-05) | COMEN01C:136-143 |
| FR-MEN-05 | Choose option 11 (Pending Authorization View) when `COPAUS0C` is installed | Control transfers to `COPAUS0C` | COMEN01C:147-159 |
| FR-MEN-06 | Choose option 11 when `COPAUS0C` is not installed | Red message "This option Pending Authorization View is not installed..."; menu re-displayed | COMEN01C:160-168, 190 |
| FR-MEN-07 | Choose an option whose program name starts with `DUMMY` | Green "This option …is coming soon ..." message; menu re-displayed (dormant: no DUMMY entries, see Q-07) | COMEN01C:169-176, 190 |
| FR-MEN-08 | Press PF3 | Signed off: sign-on screen (CC00) is displayed fresh | COMEN01C:96-98, 196-203 |
| FR-MEN-09 | Press any other key | "Invalid key pressed. Please see below..." and the menu is re-displayed | COMEN01C:99-102 |
| FR-MEN-10 | Start `CM00` directly from a blank terminal (no COMMAREA) | Sent to the sign-on screen (no menu shown) | COMEN01C:82-84, 196-203 |
| FR-MEN-11 | See my last entered option echoed after an error | Option field re-displays the normalised 2-digit value (e.g. `1` → `01`) | COMEN01C:122-125 |

### CA00 — Admin Menu (`COADM01C`, map `COADM1A` / mapset `COADM01`)
| ID | As an admin I can… | Expected user-visible result | Source cite |
|----|--------------------|------------------------------|-------------|
| FR-ADM-01 | Arrive at the Admin Menu after sign-on or on return from an admin function | Admin Menu shows the header (Tran `CA00`, Prog `COADM01C`, title "Admin Menu") and the 6 options from COADM02Y as `NN. <name>` | COADM01C:90-94, 175-187, 205-224, 229-266; COADM02Y.cpy:22-53; COADM01.bms:75-79 |
| FR-ADM-02 | Type a valid option number (1–6) and press ENTER | Control transfers to the program for that option (table in §3.5) | COADM01C:119-129, 140-149 |
| FR-ADM-03 | Type 0, blank, a non-numeric value, or a number greater than 6 | "Please enter a valid option number..." and the menu is re-displayed | COADM01C:131-138 |
| FR-ADM-04 | Choose an option whose program is not defined to CICS (e.g. the optional Db2 options 5/6 on a base install) | Green "This option is not installed ..." and the menu is re-displayed | COADM01C:77-79, 270-283 |
| FR-ADM-05 | Choose an option whose program name starts with `DUMMY` | Green "This option is not installed ..." (dormant: no DUMMY entries) | COADM01C:141, 150-157 |
| FR-ADM-06 | Press PF3 | Signed off: sign-on screen (CC00) is displayed fresh | COADM01C:100-102, 163-170 |
| FR-ADM-07 | Press any other key | "Invalid key pressed. Please see below..." and the menu is re-displayed | COADM01C:103-106 |
| FR-ADM-08 | Start `CA00` directly from a blank terminal (no COMMAREA) | Sent to the sign-on screen | COADM01C:86-88, 163-170 |

**FR count: 30** (SGN 11, MEN 11, ADM 8).

---

## 3. Screen / interface spec

All three maps: 24×80, `MODE=INOUT`, `CTRL=(ALARM,FREEKB)`, `EXTATT=YES`, `LANG=COBOL` (COSGN00.bms:19-28; COMEN01.bms:19-28; COADM01.bms:19-28). Symbolic map output area redefines the input area (`COSGN0AO REDEFINES COSGN0AI`, `COMEN1AO REDEFINES COMEN1AI`, `COADM1AO REDEFINES COADM1AI` — `app/cpy-bms/*.CPY:85` / `:139`), so received values are echoed back on re-send.

### 3.1 Common header (all three maps)
| Field | BMS name | Len | I/O | Attributes | Content / rule | Cite |
|-------|----------|-----|-----|-----------|----------------|------|
| Transaction | TRNNAME | 4 | O | ASKIP,FSET,NORM; BLUE | `CC00` / `CM00` / `CA00` from WS-TRANID | COSGN00.bms:34-37; COMEN01.bms:34-37; COSGN00C:183; COMEN01C:244; COADM01C:211 |
| Title line 1 | TITLE01 | 40 | O | ASKIP,FSET,NORM; YELLOW | `'      AWS Mainframe Modernization       '` | COTTL01Y.cpy:18-19; COSGN00.bms:38-41 |
| Date | CURDATE | 8 | O | ASKIP,FSET,NORM; BLUE | `MM/DD/YY` from FUNCTION CURRENT-DATE (YY = year(3:2)) | CSDAT01Y.cpy:30-35; COSGN00C:179, 186-190 |
| Program | PGMNAME | 8 | O | COSGN00: FSET,NORM,PROT; menus: ASKIP,FSET,NORM; BLUE | `COSGN00C` / `COMEN01C` / `COADM01C` | COSGN00.bms:57-60; COMEN01.bms:57-60 |
| Title line 2 | TITLE02 | 40 | O | ASKIP,FSET,NORM; YELLOW | `'              CardDemo                  '` | COTTL01Y.cpy:20-22 |
| Time | CURTIME | COSGN00: 9; menus: 8 | O | COSGN00: FSET,NORM,PROT; menus: ASKIP,FSET,NORM; BLUE | `HH:MM:SS` | CSDAT01Y.cpy:36-41; COSGN00.bms:70-74; COMEN01.bms:70-74; COSGN00C:192-196 |
| AppID | APPLID | 8 | O | FSET,NORM,PROT; BLUE | `EXEC CICS ASSIGN APPLID` — **sign-on only** | COSGN00.bms:80-83; COSGN00C:198-200 |
| SysID | SYSID | 8 | O | FSET,NORM,PROT; BLUE | `EXEC CICS ASSIGN SYSID` — **sign-on only** | COSGN00.bms:89-93; COSGN00C:202-204 |
| Message line | ERRMSG | 78 | O | ASKIP,BRT,FSET; RED (default) | WS-MESSAGE (80 chars, truncated to 78 on map); colour overridden via ERRMSGC on menus | COSGN00.bms:197-200; COMEN01.bms:154-157; COSGN00C:149 |

### 3.2 COSGN0A — Sign-on body
| Field | BMS name | Len | I/O | Attributes | Edit rules | Cite |
|-------|----------|-----|-----|-----------|------------|------|
| Intro text | (unnamed) | 66 | static | ASKIP,NORM; NEUTRAL | "This is a Credit Card Demo Application for Mainframe Modernization" | COSGN00.bms:94-99 |
| Banner (dollar-note art) | (unnamed ×9) | 42 each | static | ASKIP,NORM; BLUE | rows 7-15 | COSGN00.bms:100-144 |
| Instruction | (unnamed) | 49 | static | ASKIP,NORM; TURQUOISE | "Type your User ID and Password, then press ENTER:" | COSGN00.bms:145-150 |
| User ID | USERID | 8 | I | FSET,IC,NORM,UNPROT; GREEN; initial cursor | Required (not spaces/low-values); folded to upper case; key into USRSEC | COSGN00.bms:156-160; COSGN00C:118-122, 132-134 |
| Password | PASSWD | 8 | I | DRK,FSET,UNPROT; GREEN; INITIAL `'________'` | Required; non-display; folded to upper case; compared to SEC-USR-PWD | COSGN00.bms:175-180; COSGN00C:123-127, 135-136, 223 |
| Hint | (unnamed ×2) | 8 | static | ASKIP; BLUE | "(8 Char)" beside each input | COSGN00.bms:165-169, 185-189 |
| Hidden 1-byte field | (unnamed) | 1 | I | DRK,UNPROT | Not referenced by program | COSGN00.bms:190-193 |
| Key legend | (unnamed) | 22 | static | ASKIP,NORM; YELLOW | "ENTER=Sign-on  F3=Exit" | COSGN00.bms:201-205 |

**PF-key map (CC00):** ENTER = validate & sign on (COSGN00C:86-87); PF3 = exit with thank-you text (COSGN00C:88-90); any other AID = "Invalid key pressed…" (COSGN00C:91-94).

### 3.3 COMEN1A / COADM1A — Menu body (maps are identical except title)
| Field | BMS name | Len | I/O | Attributes | Edit rules | Cite |
|-------|----------|-----|-----|-----------|------------|------|
| Menu title | (unnamed) | 9 / 10 | static | ASKIP,BRT; NEUTRAL | "Main Menu" / "Admin Menu" | COMEN01.bms:75-79; COADM01.bms:75-79 |
| Option lines | OPTN001–OPTN012 | 40 each | O | ASKIP,FSET,NORM; BLUE | `NN. <name>` built from option table; COMEN01C fills 1–12, COADM01C fills 1–10 (only up to OPT-COUNT populated) | COMEN01.bms:80-139; COMEN01C:262-303; COADM01C:229-266 |
| Prompt | (unnamed) | 25 | static | ASKIP,BRT; TURQUOISE | "Please select an option :" | COMEN01.bms:140-144 |
| Option | OPTION | 2 | I/O | FSET,IC,NORM,NUM,UNPROT; HILIGHT=UNDERLINE; JUSTIFY=(RIGHT,ZERO) | Trailing spaces trimmed, right-justified, spaces→`0`; must be numeric, 1..OPT-COUNT (see BR-08) | COMEN01.bms:145-149; COMEN01C:117-134; COADM01C:121-138 |
| Key legend | (unnamed) | 23 | static | ASKIP,NORM; YELLOW | "ENTER=Continue  F3=Exit" | COMEN01.bms:158-162 |

**PF-key map (CM00 / CA00):** ENTER = process option (COMEN01C:94-95; COADM01C:98-99); PF3 = sign off → COSGN00C (COMEN01C:96-98; COADM01C:100-102); any other AID = "Invalid key pressed…" (COMEN01C:99-102; COADM01C:103-106).

### 3.4 Main Menu option table (COMEN02Y, `CDEMO-MENU-OPT-COUNT = 11`, table `OCCURS 12`)
| # | Displayed name | Program | User type | Target transaction (CSD) | Owning stream | Cite |
|---|----------------|---------|-----------|--------------------------|---------------|------|
| 1 | Account View | COACTVWC | U | CAVW (CSD:317-326) | Account Mgmt | COMEN02Y.cpy:25-29 |
| 2 | Account Update | COACTUPC | U | CAUP (CSD:306-316) | Account Mgmt | COMEN02Y.cpy:31-35 |
| 3 | Credit Card List | COCRDLIC | U | CCLI (CSD:357-366) | Card Mgmt | COMEN02Y.cpy:37-41 |
| 4 | Credit Card View | COCRDSLC | U | CCDL (CSD:347-356) | Card Mgmt | COMEN02Y.cpy:43-47 |
| 5 | Credit Card Update | COCRDUPC | U | CCUP (CSD:367-377) | Card Mgmt | COMEN02Y.cpy:49-53 |
| 6 | Transaction List | COTRN00C | U | CT00 (CSD:419-428) | Transaction Mgmt | COMEN02Y.cpy:55-59 |
| 7 | Transaction View | COTRN01C | U | CT01 (CSD:429-438) | Transaction Mgmt | COMEN02Y.cpy:61-65 |
| 8 | Transaction Add | COTRN02C | U | CT02 (CSD:439-448) | Transaction Mgmt | COMEN02Y.cpy:67-72 (prior text "Transaction Add (Admin Only)" commented at :69) |
| 9 | Transaction Reports | CORPT00C | U | CR00 (CSD:409-418) | Reporting | COMEN02Y.cpy:74-78 |
| 10 | Bill Payment | COBIL00C | U | CB00 (CSD:337-346) | Bill Payment | COMEN02Y.cpy:80-84 |
| 11 | Pending Authorization View | COPAUS0C | U | not in CARDDEMO.CSD; defined in optional `app/app-authorization-ims-db2-mq/csd/CRDDEMO2.csd:18` | Pending Auth (optional) | COMEN02Y.cpy:86-90 |

Row layout: `NUM 9(02)`, `NAME X(35)`, `PGMNAME X(08)`, `USRTYPE X(01)` (COMEN02Y.cpy:93-98).

### 3.5 Admin Menu option table (COADM02Y, `CDEMO-ADMIN-OPT-COUNT = 6`, table `OCCURS 9`)
| # | Displayed name | Program | Target transaction | Owning stream | Cite |
|---|----------------|---------|--------------------|---------------|------|
| 1 | User List (Security) | COUSR00C | CU00 (CSD:449-458) | User Mgmt | COADM02Y.cpy:26-29 |
| 2 | User Add (Security) | COUSR01C | CU01 (CSD:459-468) | User Mgmt | COADM02Y.cpy:31-34 |
| 3 | User Update (Security) | COUSR02C | CU02 (CSD:469-478) | User Mgmt | COADM02Y.cpy:36-39 |
| 4 | User Delete (Security) | COUSR03C | CU03 (CSD:479-488) | User Mgmt | COADM02Y.cpy:41-44 |
| 5 | Transaction Type List/Update (Db2) | COTRTLIC | not in CARDDEMO.CSD; optional `app/app-transaction-type-db2/csd/CRDDEMOD.csd:11` (CTLI) | Tran Type Mgmt (Db2, optional) | COADM02Y.cpy:46-49 |
| 6 | Transaction Type Maintenance (Db2) | COTRTUPC | not in CARDDEMO.CSD; optional `CRDDEMOD.csd:18` (CTTU) | Tran Type Mgmt (Db2, optional) | COADM02Y.cpy:50-53 |

Row layout: `NUM 9(02)`, `NAME X(35)`, `PGMNAME X(08)` — **no user-type column** (COADM02Y.cpy:55-59). Count was 4 before the Db2 release (commented, COADM02Y.cpy:21).

---

## 4. Business rules & calculations

| ID | Rule | Cite |
|----|------|------|
| BR-01 | User ID and Password are mandatory; User ID is checked first, so if both are blank only the User ID message is shown. "Blank" = all spaces or all low-values. | COSGN00C:117-130 |
| BR-02 | User ID and Password are converted with `FUNCTION UPPER-CASE` before lookup and comparison; authentication is therefore case-insensitive and assumes upper-case stored passwords. | COSGN00C:132-136 |
| BR-03 | Authentication = keyed `READ` of USRSEC by the 8-byte upper-cased User ID, then exact equality `SEC-USR-PWD = WS-USER-PWD` (8-byte plaintext compare). No lockout, retry count or audit record. | COSGN00C:211-223 |
| BR-04 | On successful sign-on the COMMAREA is primed: `CDEMO-FROM-TRANID='CC00'`, `CDEMO-FROM-PROGRAM='COSGN00C'`, `CDEMO-USER-ID=<upper user id>`, `CDEMO-USER-TYPE=SEC-USR-TYPE`, `CDEMO-PGM-CONTEXT=0` (first entry). | COSGN00C:224-228 |
| BR-05 | Role routing: `CDEMO-USER-TYPE='A'` (88 `CDEMO-USRTYP-ADMIN`) → XCTL `COADM01C`; **any other value** → XCTL `COMEN01C`. | COSGN00C:230-240; COCOM01Y.cpy:26-28 |
| BR-06 | Pseudo-conversational re-entry: a menu receiving `CDEMO-PGM-CONTEXT=0` sets it to 1 (`CDEMO-PGM-REENTER`), clears the output map and sends the menu; with context 1 it RECEIVEs the map and processes the AID. Each pass ends with `RETURN TRANSID(own tranid) COMMAREA(CARDDEMO-COMMAREA)`. | COMEN01C:86-110; COADM01C:90-114; COCOM01Y.cpy:29-31 |
| BR-07 | Option normalisation: scan OPTIONI right-to-left to the last non-space character (stop at position 1), move the leading substring into a 2-byte `JUST RIGHT` field, replace spaces with `0`, move to `PIC 9(02)`. E.g. `"1 "` → `01`, `" 7"` → `07`, `"  "` → `00`. | COMEN01C:117-124; COADM01C:121-128 |
| BR-08 | Option validity: rejected when not numeric, zero, or greater than the table count (11 main / 6 admin). | COMEN01C:127-134; COADM01C:131-138 |
| BR-09 | Main-menu role gate: if the user is type `U` (`CDEMO-USRTYP-USER`) and the option's USRTYPE is `A`, access is denied. Admin users may select any main-menu option. Users whose type is neither `A` nor `U` are not gated. | COMEN01C:136-143; COCOM01Y.cpy:26-28 |
| BR-10 | Optional-module probe: for program `COPAUS0C` only, the main menu issues `EXEC CICS INQUIRE PROGRAM … NOHANDLE` and XCTLs only if `EIBRESP = NORMAL`; otherwise it shows the "not installed" message. | COMEN01C:147-168 |
| BR-11 | Placeholder options: a program name starting `DUMMY` is never XCTL'd; main menu shows "coming soon", admin menu shows "not installed". | COMEN01C:169-176; COADM01C:141, 150-157 |
| BR-12 | Outbound XCTL contract from a menu: set `CDEMO-FROM-TRANID=<CM00/CA00>`, `CDEMO-FROM-PROGRAM=<COMEN01C/COADM01C>`, `CDEMO-PGM-CONTEXT=0`, then `XCTL PROGRAM(<table PGMNAME>) COMMAREA(CARDDEMO-COMMAREA)`. `CDEMO-TO-PROGRAM`/`CDEMO-TO-TRANID` are not set. | COMEN01C:153-159, 178-187; COADM01C:142-148 |
| BR-13 | Admin menu missing-program handling: `HANDLE CONDITION PGMIDERR` routes a failed XCTL to `PGMIDERR-ERR-PARA`, which shows the "not installed" message, re-sends the menu and RETURNs with TRANSID `CA00`. The main menu has no equivalent handler for non-`COPAUS0C` options. | COADM01C:77-79, 270-283 |
| BR-14 | Sign-off: PF3 on a menu sets `CDEMO-TO-PROGRAM='COSGN00C'` and XCTLs to it **without** a COMMAREA, so COSGN00C starts with `EIBCALEN=0` and shows a fresh sign-on screen. If `CDEMO-TO-PROGRAM` is low-values/spaces it is defaulted to `COSGN00C`. | COMEN01C:96-98, 196-203; COADM01C:100-102, 163-170 |
| BR-15 | No-COMMAREA guard: a menu started with `EIBCALEN=0` (e.g. typing CM00/CA00 at a clear screen) sends the user to sign-on instead of showing the menu. | COMEN01C:82-84; COADM01C:86-88 |
| BR-16 | Sign-on screen first display: `EIBCALEN=0` → output map cleared to low-values (so BMS initial values are shown), cursor placed on User ID (`USERIDL=-1`), screen sent with ERASE and CURSOR. | COSGN00C:80-83, 151-157 |
| BR-17 | Cursor positioning on sign-on errors: User-ID errors (blank, not found, I/O) → cursor on User ID; password errors (blank, wrong) → cursor on Password. | COSGN00C:121, 126, 244, 250, 255 |
| BR-18 | Sign-on PF3 exit: `SEND TEXT` of the thank-you message with ERASE/FREEKB followed by `RETURN` with no TRANSID — the pseudo-conversation ends. | COSGN00C:88-90, 162-172 |
| BR-19 | Header date/time: `FUNCTION CURRENT-DATE` → date shown `MM/DD/YY` (2-digit year = positions 3-4 of the 4-digit year), time `HH:MM:SS`. | COSGN00C:179-196; COMEN01C:240-257; COADM01C:207-224; CSDAT01Y.cpy:17-41 |
| BR-20 | Menu text construction: each option line = `NUM` (2 digits, zero-filled) + `'. '` + `NAME` (35, DELIMITED BY SIZE) into a 40-byte field. | COMEN01C:267-272; COADM01C:234-239 |
| BR-21 | Message colour: main-menu "not installed" forced RED (`DFHRED`), "coming soon" GREEN (`DFHGREEN`); admin-menu "not installed" GREEN; all other messages use the BMS default RED/BRT. | COMEN01C:162, 171; COADM01C:151, 272; COMEN01.bms:154-157 |

**BR count: 21.**

---

## 5. Data access

| File (CSD) | Dataset | Program | Operation | Key | Record copybook | Cite |
|------------|---------|---------|-----------|-----|-----------------|------|
| USRSEC | `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS` (CSD:88-99) | COSGN00C | `READ` (no UPDATE), `INTO SEC-USER-DATA`, `RIDFLD WS-USER-ID`, `KEYLENGTH 8` | SEC-USR-ID X(08), offset 0 (`KEYS(8,0)`, DUSRSECJ.jcl:65) | CSUSR01Y (`SEC-USER-DATA`, 80 bytes) | COSGN00C:209-219 |
| USRSEC | — | COMEN01C, COADM01C | none (WS-USRSEC-FILE and CSUSR01Y declared but unused) | — | — | COMEN01C:39, 58; COADM01C:39, 58 |

**CSUSR01Y — SEC-USER-DATA (CSUSR01Y.cpy:17-23):**
| Field | PIC | Offset | Use in scope |
|-------|-----|--------|--------------|
| SEC-USR-ID | X(08) | 0 | key |
| SEC-USR-FNAME | X(20) | 8 | not used |
| SEC-USR-LNAME | X(20) | 28 | not used |
| SEC-USR-PWD | X(08) | 48 | compared to upper-cased input |
| SEC-USR-TYPE | X(01) | 56 | `A` admin / `U` user → CDEMO-USER-TYPE |
| SEC-USR-FILLER | X(23) | 57 | — |

Seed data (DUSRSECJ.jcl:35-44): `ADMIN001`–`ADMIN005` type `A`, `USER0001`–`USER0005` type `U`, all with password `PASSWORD`. USRSEC is maintained by the User Management stream (CU00–CU03), not by this scope.

No other files, queues, DB2 tables or TS/TD queues are touched by the three programs.

---

## 6. Message catalogue (exact text)

| # | Text (verbatim) | Program | Condition | Cite |
|---|-----------------|---------|-----------|------|
| M-01 | `Please enter User ID ...` | COSGN00C | ENTER with User ID spaces/low-values | COSGN00C:118-120 |
| M-02 | `Please enter Password ...` | COSGN00C | ENTER with Password spaces/low-values | COSGN00C:123-125 |
| M-03 | `Wrong Password. Try again ...` | COSGN00C | USRSEC READ NORMAL, password mismatch | COSGN00C:241-243 |
| M-04 | `User not found. Try again ...` | COSGN00C | USRSEC READ RESP=13 (NOTFND) | COSGN00C:247-249 |
| M-05 | `Unable to verify the User ...` | COSGN00C | USRSEC READ any other RESP | COSGN00C:252-254 |
| M-06 | `Thank you for using CardDemo application...` (CCDA-MSG-THANK-YOU, PIC X(50) space-padded; sent as 80-byte plain text) | COSGN00C | PF3 on sign-on | COSGN00C:88-90; CSMSG01Y.cpy:18-19 |
| M-07 | `Invalid key pressed. Please see below...` (CCDA-MSG-INVALID-KEY, X(50)) | all three | AID not ENTER/PF3 | COSGN00C:91-94; COMEN01C:99-102; COADM01C:103-106; CSMSG01Y.cpy:20-21 |
| M-08 | `Please enter a valid option number...` | COMEN01C, COADM01C | option non-numeric / 0 / > count | COMEN01C:131-132; COADM01C:135-136 |
| M-09 | `No access - Admin Only option... ` (note trailing space) | COMEN01C | type `U` user selects USRTYPE `A` option | COMEN01C:140-141 |
| M-10 | `This option ` + option name up to first double space + ` is not installed...` → today only `This option Pending Authorization View is not installed...` (RED) | COMEN01C | COPAUS0C INQUIRE not NORMAL | COMEN01C:160-167 |
| M-11 | `This option ` + option name up to first single space + `is coming soon ...` (GREEN; no space before "is", e.g. `This option Accountis coming soon ...`) | COMEN01C | PGMNAME(1:5)='DUMMY' (dormant) | COMEN01C:169-176 |
| M-12 | `This option is not installed ...` (GREEN) | COADM01C | PGMNAME(1:5)='DUMMY' or PGMIDERR on XCTL | COADM01C:150-156, 271-277 |

Static screen literals (not conditional): sign-on legend `ENTER=Sign-on  F3=Exit` (COSGN00.bms:205); menu legend `ENTER=Continue  F3=Exit` (COMEN01.bms:162); titles per §3.1. Unused literal: `CCDA-THANK-YOU` = `Thank you for using CCDA application... ` (COTTL01Y.cpy:23-24) — never referenced by these programs.

---

## 7. Navigation / integration

### 7.1 COMMAREA contract — `CARDDEMO-COMMAREA` (COCOM01Y.cpy:19-44)
| Field | PIC | Set by (this scope) | Read by (this scope) | Meaning |
|-------|-----|---------------------|----------------------|---------|
| CDEMO-FROM-TRANID | X(04) | COSGN00C:224 (`CC00`); COMEN01C:153,178 (`CM00`); COADM01C:142 (`CA00`) | — | Caller's transaction |
| CDEMO-FROM-PROGRAM | X(08) | COSGN00C:225; COMEN01C:83,154,179-180; COADM01C:87,143 | — (used by downstream programs for PF3 return) | Caller's program |
| CDEMO-TO-TRANID | X(04) | never set in scope | — | — |
| CDEMO-TO-PROGRAM | X(08) | COMEN01C:97,199; COADM01C:101,166 (`COSGN00C`) | COMEN01C:198,202; COADM01C:165,169 | XCTL target for sign-off/return |
| CDEMO-USER-ID | X(08) | COSGN00C:132-134, 226 | — (passed through) | Signed-on user (upper case) |
| CDEMO-USER-TYPE | X(01); 88 ADMIN='A', USER='U' | COSGN00C:227 | COSGN00C:230; COMEN01C:136 | Role |
| CDEMO-PGM-CONTEXT | 9(01); 88 ENTER=0, REENTER=1 | COSGN00C:228; COMEN01C:88,155,183; COADM01C:92,144 | COMEN01C:87; COADM01C:91 | First-entry vs re-entry |
| CDEMO-CUST-ID/FNAME/MNAME/LNAME | 9(09)/X(25)×3 | not touched | — | passed through to downstream |
| CDEMO-ACCT-ID / CDEMO-ACCT-STATUS | 9(11)/X(01) | not touched | — | passed through |
| CDEMO-CARD-NUM | 9(16) | not touched | — | passed through |
| CDEMO-LAST-MAP / CDEMO-LAST-MAPSET | X(7)/X(7) | not touched | — | passed through |

### 7.2 Inbound callers
| Caller | Into | How | Cite |
|--------|------|-----|------|
| Terminal (`CC00`) | COSGN00C | CSD TRANSACTION CC00 → PROGRAM COSGN00C (also `TRANSID(CC00)` on the PROGRAM def) | CSD:249-256, 378-387 |
| COSGN00C RETURN TRANSID | COSGN00C | next ENTER/PF on sign-on screen re-invokes CC00 with COMMAREA | COSGN00C:98-102 |
| COSGN00C | COADM01C / COMEN01C | XCTL with COMMAREA after successful sign-on | COSGN00C:230-240 |
| COMEN01C / COADM01C RETURN TRANSID | self | CM00 / CA00 with COMMAREA (re-entry) | COMEN01C:107-110; COADM01C:111-114 |
| COMEN01C / COADM01C (PF3, no COMMAREA) | COSGN00C | XCTL without COMMAREA | COMEN01C:196-203; COADM01C:163-170 |
| Main-menu functions returning on PF3 | COMEN01C | literal `'COMEN01C'` as return target: COACTUPC:558, COACTVWC:169, COBIL00C:130, COCRDLIC:188, COCRDSLC:180, COCRDUPC:236, CORPT00C:188, COTRN00C:123, COTRN01C:117, COTRN02C:138, COPAUS0C:35 (optional module) | grep at 7fd2344 (owned by those scopes) |
| Admin functions returning on PF3 | COADM01C | literal `'COADM01C'`: COUSR00C:126, COUSR01C:94, COUSR02C:114/125, COUSR03C:113/124, COTRTLIC:47, COTRTUPC:210 (optional Db2 module) | grep at 7fd2344 (owned by those scopes) |
| Downstream "no COMMAREA" fallbacks | COSGN00C | literal `'COSGN00C'` in COBIL00C, CORPT00C, COTRN00C/01C/02C, COUSR00C-03C, COPAUS0C | grep at 7fd2344 |

Downstream return pattern (example, owned by CT00 scope): `RETURN-TO-PREV-SCREEN` resets `CDEMO-PGM-CONTEXT` to 0 and XCTLs to `CDEMO-TO-PROGRAM` with COMMAREA (COTRN00C:510-521), so the menu re-displays as a first entry (BR-06).

### 7.3 Outbound
| From | To | Mechanism | COMMAREA | Cite |
|------|----|-----------|----------|------|
| COSGN00C | COADM01C | XCTL | yes | COSGN00C:231-234 |
| COSGN00C | COMEN01C | XCTL | yes | COSGN00C:236-239 |
| COMEN01C | option program (§3.4) | XCTL | yes | COMEN01C:156-159, 184-187 |
| COMEN01C | COPAUS0C | INQUIRE PROGRAM … NOHANDLE, then XCTL | yes | COMEN01C:148-159 |
| COADM01C | option program (§3.5) | XCTL (PGMIDERR handled) | yes | COADM01C:145-148 |
| COMEN01C / COADM01C | COSGN00C (or CDEMO-TO-PROGRAM) | XCTL | **no** | COMEN01C:201-203; COADM01C:168-170 |

No CALL, LINK, START, TD/TS queue or job submission in scope.

### 7.4 CICS resource definitions used (CARDDEMO.CSD)
| Resource | Lines | Notes |
|----------|-------|-------|
| TRANSACTION CC00 → COSGN00C | 378-387 | RESSEC(NO) CMDSEC(NO) |
| TRANSACTION CM00 → COMEN01C | 399-408 | RESSEC(NO) CMDSEC(NO) |
| TRANSACTION CA00 → COADM01C | 327-336 | RESSEC(NO) CMDSEC(NO) — no CICS-level security on the admin transaction |
| PROGRAM COSGN00C | 249-256 | `TRANSID(CC00)`; no `LANGUAGE` attribute (others specify COBOL) |
| PROGRAM COMEN01C / COADM01C | 235-241 / 189-195 | LANGUAGE(COBOL) |
| MAPSET COSGN00 / COMEN01 / COADM01 | 141-144 / 133-136 / 110-113 | |
| FILE USRSEC | 88-99 | READ/BROWSE/ADD/UPDATE/DELETE enabled at file level; this scope only READs |

---

## 8. Edge cases, error handling, abend / return codes

| Case | Behaviour | Cite |
|------|-----------|------|
| USRSEC READ RESP | 0 → password check; 13 (NOTFND) → M-04; anything else (e.g. NOTOPEN, DISABLED, IOERR) → M-05. RESP2 captured but not shown. | COSGN00C:217-218, 221-257 |
| RECEIVE MAP RESP (all three) | RESP/RESP2 captured into WS-RESP-CD/WS-REAS-CD but **never tested** (e.g. MAPFAIL ignored). | COSGN00C:110-115; COMEN01C:227-233; COADM01C:194-200 |
| Both User ID and Password blank | Only M-01 shown (EVALUATE order). | COSGN00C:117-130 |
| Error then fall-through on sign-on | After a blank-field error the screen is sent, uppercasing still runs, READ is skipped (ERR-FLG), then RETURN TRANSID CC00. | COSGN00C:132-140, 98-102 |
| Menu XCTL to undefined program (main menu, options 1–10) | No PGMIDERR handler and no RESP → CICS PGMIDERR raised on XCTL; task expected to abend (CICS standard abend for unhandled PGMIDERR). Not exercised in base install because all programs are defined in CSD. | COMEN01C:184-187 |
| Menu XCTL to undefined program (admin menu) | Caught by HANDLE CONDITION → M-12 and RETURN TRANSID CA00. | COADM01C:77-79, 270-283 |
| Option out of range in main menu | M-08 is sent, but execution continues to the role gate which subscripts the table with the invalid WS-OPTION (see Q-06). | COMEN01C:127-143 |
| Re-send after error echoes input | Output area redefines input area, so User ID, (dark) Password and Option are re-sent with the user's entered values. | app/cpy-bms/COSGN00.CPY:85; COMEN01.CPY:139 |
| CLEAR / PA keys | Treated as "other" AID → M-07 (map re-sent with ERASE). | COSGN00C:91-94; COMEN01C:99-102 |
| Explicit abend / return codes | None issued by these programs (no `EXEC CICS ABEND`, no RETURN-CODE). `CSMSG02Y` (ABEND-DATA) is not COPYed by any of the three. | COSGN00C:48-58; COMEN01C:50-61; COADM01C:50-61; CSMSG02Y.cpy:21-29 |

---

## 9. Open questions / ambiguities / apparent legacy bugs (not fixed)

| ID | Observation | Cite |
|----|-------------|------|
| Q-01 | **Case-insensitive plaintext passwords.** Input password is upper-cased before an exact compare with the stored value; stored mixed-case passwords can never match. No hashing, lockout or audit. Confirm whether target must preserve this. | COSGN00C:135-136, 223 |
| Q-02 | **PASSWD initial value `'________'` + FSET.** On first display the dark Password field contains 8 underscores that are transmitted even if untouched, so M-02 may never fire and a short password typed over the underscores would carry trailing `_`. Behaviour depends on terminal/emulator (EOF-erase); needs runtime confirmation. | COSGN00.bms:175-180; COSGN00C:81, 123 |
| Q-03 | **COSGN00C never loads DFHCOMMAREA** into CARDDEMO-COMMAREA; the COMMAREA passed on RETURN and on XCTL is working storage. Fields not explicitly set (CDEMO-TO-TRANID, CDEMO-TO-PROGRAM, customer/account/card/last-map) are uninitialised (no VALUE clauses) when handed to the menus. | COSGN00C:48, 98-102, 224-239; COCOM01Y.cpy:19-44 |
| Q-04 | **Users with a type other than `A`/`U`** (e.g. blank) are routed to the main menu (BR-05) and bypass the admin-only gate, which tests only `CDEMO-USRTYP-USER`. | COSGN00C:230-240; COMEN01C:136-137 |
| Q-05 | **Admin-only gate is dormant.** Every COMEN02Y entry has USRTYPE `U`; option 8 was formerly "Transaction Add (Admin Only)" (commented out). Confirm intended access level for CT02. | COMEN02Y.cpy:67-72 |
| Q-06 | **Out-of-bounds subscript in COMEN01C.** After an invalid option (0, >12 or non-numeric) the role gate still evaluates `CDEMO-MENU-OPT-USRTYPE(WS-OPTION)` (not guarded by ERR-FLG); index 0 / >12 references storage outside the table. A stray `'A'` byte there would overwrite M-08 with M-09. | COMEN01C:127-143 |
| Q-07 | **"coming soon" message formatting bug.** Name DELIMITED BY SPACE (first word only) and no space before `is coming soon ...` (e.g. `This option Accountis coming soon ...`). Dormant — no DUMMY entries. | COMEN01C:172-176 |
| Q-08 | **Admin "not installed" message omits the option name** (STRING operand commented out) and is GREEN, whereas the main-menu equivalent names the option and is RED. Inconsistent UX. | COADM01C:150-156, 271-277; COMEN01C:160-167 |
| Q-09 | **Main menu has no PGMIDERR handling** except the COPAUS0C INQUIRE special case; an undefined option program abends rather than showing a message. | COMEN01C:147-187 |
| Q-10 | **Admin options 5–6 (COTRTLIC/COTRTUPC) are not defined in base CARDDEMO.CSD** (only in the optional Db2 CSD), so on a base install they yield M-12 via PGMIDERR. Target must decide whether to show/hide them. | COADM02Y.cpy:46-53; CRDDEMOD.csd:11,18 |
| Q-11 | **COADM01C does not re-check `CDEMO-USER-TYPE`.** Admin authorisation relies solely on sign-on routing and on downstream PF3 return literals; CSD has RESSEC(NO)/CMDSEC(NO). A COMMAREA arriving with type `U` would still get the admin menu. | COADM01C:86-158; CSD:327-336 |
| Q-12 | **Admins cannot reach main-menu functions** (accounts, cards, transactions, reports, bill pay): COSGN00C routes admins only to COADM01C and COADM02Y lists only security/tran-type options, although BR-09 would permit admins on the main menu. Confirm intended role model. | COSGN00C:230-234; COADM02Y.cpy:26-53; COMEN01C:136-137 |
| Q-13 | **EIBCALEN=0 path in the menus moves `'COSGN00C'` to CDEMO-**FROM**-PROGRAM**, not TO; sign-off then relies on CDEMO-TO-PROGRAM being low-values/spaces in uninitialised WS. | COMEN01C:82-84, 198-200; COADM01C:86-88, 165-167 |
| Q-14 | **RECEIVE MAP RESP ignored** in all three programs (MAPFAIL, etc.). | COSGN00C:110-115; COMEN01C:227-233; COADM01C:194-200 |
| Q-15 | **Table OCCURS larger than populated data.** COMEN02Y data = 11 rows (506 bytes) redefined as OCCURS 12 (552); COADM02Y data = 6 rows (270) redefined as OCCURS 9 (405); admin BMS has 10 option slots. A level-05 REDEFINES larger than its base is compiler-dependent; count guards keep normal paths in range. | COMEN02Y.cpy:21-98; COADM02Y.cpy:22-59; COADM01C:241-264 |
| Q-16 | **Sign-on CURTIME is 9 bytes with INITIAL `'Ahh:mm:ss'`** (stray `A`) vs 8-byte time value and 8-byte field on the menus. Cosmetic. | COSGN00.bms:70-74; COMEN01.bms:70-74; CSDAT01Y.cpy:36-41 |
| Q-17 | **Redundant / dead code:** duplicate `MOVE WS-PGMNAME TO CDEMO-FROM-PROGRAM`; commented user-id/type moves; unused WS-USRSEC-FILE + CSUSR01Y in both menus; unused CCDA-THANK-YOU (COTTL01Y) vs used CCDA-MSG-THANK-YOU (CSMSG01Y) with different text; stray `.` sentence; unreferenced hidden 1-byte field on sign-on map. | COMEN01C:179-182, 39, 58; COADM01C:39, 58, 284; COTTL01Y.cpy:23-24; COSGN00.bms:190-193 |
| Q-18 | **CSD PROGRAM(COSGN00C) has no `LANGUAGE(COBOL)`** (other programs specify it) and carries `TRANSID(CC00)`. Confirm runtime defaults. | CSD:249-256 |
| Q-19 | **Password echoed back on error re-send** (dark field, but present in the outbound datastream) because the output map redefines the input map. | COSGN00C:145-157; app/cpy-bms/COSGN00.CPY:85 |
| Q-20 | **Downstream PF3 targets are hard-coded** (`COMEN01C` / `COADM01C`) rather than using CDEMO-FROM-PROGRAM, so the menu a user returns to is fixed per program, not per caller. Owned by other scopes; affects the navigation contract. | §7.2 grep list (e.g. COTRN00C:123) |

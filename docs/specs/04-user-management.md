# 04 — User Security Administration (CU00 / CU01 / CU02 / CU03) — Functional & Technical Specification

**Stream:** User Management (Security), stream #4 in [CardDemo-Stream-Map](../../migration/transaction-management/docs/CardDemo-Stream-Map.md)
**SOURCE:** `choikh0423/aws-mainframe-modernization-carddemo` @ `7fd2344fe22ea20b31dc8ae0d662d9379ec7d284`
**Source files:** `app/cbl/COUSR00C.cbl`, `COUSR01C.cbl`, `COUSR02C.cbl`, `COUSR03C.cbl`; `app/bms/COUSR00.bms`, `COUSR01.bms`, `COUSR02.bms`, `COUSR03.bms`; `app/cpy/CSUSR01Y.cpy`; `app/jcl/DUSRSECJ.jcl`
**Entry transactions / jobs:** `CU00` → COUSR00C (List Users), `CU01` → COUSR01C (Add User), `CU02` → COUSR02C (Update User), `CU03` → COUSR03C (Delete User) (CSD `app/csd/CARDDEMO.CSD:449-488`); batch job `DUSRSECJ` (USRSEC initial load).
**Out of scope / related:** Transaction Management CT00–CT02 is specified in [TransactionManagement_functional_requirement.md](../../migration/transaction-management/docs/TransactionManagement_functional_requirement.md) (house style followed here). Sign-on (COSGN00C) and Admin Menu (COADM01C) belong to stream #5 and are referenced only as integration points.

**Purpose.** These four CICS pseudo-conversational screens let an administrator maintain the `USRSEC` VSAM KSDS that holds CardDemo sign-on credentials: browse users 10 per page and pick one to update or delete, add a new user, update a user's name/password/type, and delete a user after a fetch-and-confirm (PF5) step. `DUSRSECJ` (re)creates the KSDS and seeds it with 5 admin and 5 regular users. The file is read by sign-on (COSGN00C) to authenticate and to route admins (`A`) to the admin menu.

Cite format: `PROGRAM:start-end` = line numbers in `app/cbl/<PROGRAM>.cbl`; BMS/copybook/JCL cites use the file name (e.g. `COUSR00.bms:153-181`, `DUSRSECJ:58-73`).

---

## 1. Functional requirements

### Common (all four programs)
| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-USR-C1 | Start CU00/CU01/CU02/CU03 directly with no COMMAREA (e.g. type the tran id on a blank screen) | Control is transferred to the sign-on program COSGN00C; no user-admin screen is shown | COUSR00C:110-112, 506-517; COUSR01C:78-80, 165-178; COUSR02C:90-92, 250-261; COUSR03C:90-92, 197-208 |
| FR-USR-C2 | See any user-admin screen | Header shows `Tran:` (CU0n), `Prog:` (COUSR0nC), titles from COTTL01Y, `Date:` MM/DD/YY and `Time:` HH:MM:SS of the current system clock | COUSR00C:562-581; COUSR01C:214-233; COUSR02C:296-315; COUSR03C:243-262; COUSR00.bms:29-74 |

### CU00 — List Users (COUSR00C / map COUSR0A)
| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-USR-L1 | Open the user list (Admin menu option 1, or return from CU02/CU03) | First page (page 1) of up to 10 users from the lowest User ID: Sel, User ID, First Name, Last Name, Type; search field blank | COUSR00C:115-119, 218-228, 282-331, 384-441 |
| FR-USR-L2 | Type a value in "Search User ID" and press ENTER | List restarts at the first User ID ≥ the value; page number reset to 1; search field is cleared on redisplay | COUSR00C:218-228, 586-595, 327-328 |
| FR-USR-L3 | Press PF8 when a further page exists | Next 10 users after the last one shown; page number +1 | COUSR00C:130-131, 260-271, 282-323 |
| FR-USR-L4 | Press PF8 when the current page is the last | Screen redisplayed unchanged (no erase) with "You are already at the bottom of the page..." | COUSR00C:270-277 |
| FR-USR-L5 | Page forward and hit end-of-file while filling/look-ahead | "You have reached the bottom of the page..." with the rows that were read | COUSR00C:300-318, 634-640 |
| FR-USR-L6 | Press PF7 when page > 1 | Previous 10 users; page number −1 (or set to 1 when no earlier record exists) | COUSR00C:128-129, 237-249, 336-379 |
| FR-USR-L7 | Press PF7 on page 1 (or before any paging) | Screen redisplayed unchanged (no erase) with "You are already at the top of the page..." | COUSR00C:248-255 |
| FR-USR-L8 | Page backward and hit start-of-file | "You have reached the top of the page..." | COUSR00C:354-360, 668-674 |
| FR-USR-L9 | Type `U` or `u` in a row's Sel field and press ENTER | Transfer to Update User (CU02) with that row's User ID pre-loaded (see FR-USR-U1) | COUSR00C:151-199 |
| FR-USR-L10 | Type `D` or `d` in a row's Sel field and press ENTER | Transfer to Delete User (CU03) with that row's User ID pre-loaded (see FR-USR-D1) | COUSR00C:151-185, 187-189, 200-209 |
| FR-USR-L11 | Type any other non-blank Sel value on a populated row and press ENTER | "Invalid selection. Valid values are U and D"; list is re-read and redisplayed | COUSR00C:187-189, 210-215, 218-228 |
| FR-USR-L12 | Press PF3 | Return to the Admin menu (COADM01C) | COUSR00C:125-127, 506-517 |
| FR-USR-L13 | Press any key other than ENTER/PF3/PF7/PF8 | "Invalid key pressed. Please see below..." | COUSR00C:132-136; CSMSG01Y:20-21 |
| FR-USR-L14 | Search for a User ID greater than every key (or list an empty file) | "You are at the top of the page..." (see §9 Q-11) | COUSR00C:597-606 |
| FR-USR-L15 | Encounter a file error during browse | "Unable to lookup User..."; RESP/RESP2 written to the job log | COUSR00C:607-613, 641-647, 675-681 |

### CU01 — Add User (COUSR01C / map COUSR1A)
| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-USR-A1 | Open Add User (Admin menu option 2) | Blank form (First Name, Last Name, User ID, Password, User Type), cursor in First Name | COUSR01C:83-87; COUSR01.bms:84-88 |
| FR-USR-A2 | Fill all five fields and press ENTER | Record written to USRSEC; form cleared; green "User <id> has been added ..." | COUSR01C:91-92, 153-159, 238-259, 287-295 |
| FR-USR-A3 | Leave a field blank and press ENTER | First failing check (order: First Name, Last Name, User ID, Password, User Type) shows "<Field> can NOT be empty..." with cursor on that field; nothing written | COUSR01C:117-151 |
| FR-USR-A4 | Enter a User ID that already exists | "User ID already exist..." with cursor on User ID; nothing written | COUSR01C:260-266 |
| FR-USR-A5 | Hit any other write error | "Unable to Add User..." with cursor on First Name | COUSR01C:267-273 |
| FR-USR-A6 | Press PF4 | All fields and message cleared, cursor on First Name | COUSR01C:96-97, 279-295 |
| FR-USR-A7 | Press PF3 | Return to Admin menu (COADM01C) without saving | COUSR01C:93-95, 165-178 |
| FR-USR-A8 | Press any other key (including PF12, although the footer advertises `F12=Exit`) | "Invalid key pressed. Please see below..." | COUSR01C:98-102; COUSR01.bms:155-159 |

### CU02 — Update User (COUSR02C / map COUSR2A)
| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-USR-U1 | Arrive from the list via `U` | Selected user is fetched and shown (First/Last Name, Password (non-display), Type) with neutral "Press PF5 key to save your updates ..." | COUSR02C:95-105, 143-172, 320-339 |
| FR-USR-U2 | Open Update User from the Admin menu (option 3) | Empty form, cursor on "Enter User ID" | COUSR02C:95-98, 105 |
| FR-USR-U3 | Enter a User ID and press ENTER | User fetched and displayed as in FR-USR-U1 | COUSR02C:109-110, 143-172 |
| FR-USR-U4 | Press ENTER with blank User ID | "User ID can NOT be empty..." | COUSR02C:145-151 |
| FR-USR-U5 | Enter a User ID that does not exist | "User ID NOT found..."; detail fields blank | COUSR02C:157-163, 340-345 |
| FR-USR-U6 | Change any of First Name / Last Name / Password / User Type and press PF5 | Record rewritten; green "User <id> has been updated ..."; values stay on screen | COUSR02C:122-123, 215-237, 358-376 |
| FR-USR-U7 | Press PF5 without changing anything | Red "Please modify to update ..." | COUSR02C:236-243 |
| FR-USR-U8 | Press PF5 with a blank field | First failing check (order: User ID, First Name, Last Name, Password, User Type) shows "<Field> can NOT be empty..." | COUSR02C:179-213 |
| FR-USR-U9 | Press PF3 | Pending changes are saved exactly as for PF5, then control returns to the caller (`CDEMO-FROM-PROGRAM`, e.g. COUSR00C) or to COADM01C if none (see §9 Q-3) | COUSR02C:111-119 |
| FR-USR-U10 | Press PF4 | All fields and message cleared, cursor on User ID | COUSR02C:120-121, 395-411 |
| FR-USR-U11 | Press PF12 | Return to Admin menu (COADM01C) without saving | COUSR02C:124-126 |
| FR-USR-U12 | Press any other key | "Invalid key pressed. Please see below..." | COUSR02C:127-130 |
| FR-USR-U13 | Hit a file error | "Unable to lookup User..." (read) or "Unable to Update User..." (rewrite); RESP/RESP2 logged | COUSR02C:346-352, 383-389 |

### CU03 — Delete User (COUSR03C / map COUSR3A)
| ID | As a user I can… | Expected user-visible result | Source cite |
|----|------------------|------------------------------|-------------|
| FR-USR-D1 | Arrive from the list via `D` | Selected user fetched; First/Last Name and Type shown protected; neutral "Press PF5 key to delete this user ..." | COUSR03C:95-105, 142-169, 267-286; COUSR03.bms:103-134 |
| FR-USR-D2 | Open Delete User from the Admin menu (option 4) | Empty form, cursor on "Enter User ID" | COUSR03C:95-98, 105 |
| FR-USR-D3 | Enter a User ID and press ENTER | User fetched and displayed as in FR-USR-D1 (confirmation step) | COUSR03C:109-110, 142-169 |
| FR-USR-D4 | Press ENTER or PF5 with blank User ID | "User ID can NOT be empty..." | COUSR03C:144-150, 176-186 |
| FR-USR-D5 | Enter a User ID that does not exist | "User ID NOT found..." | COUSR03C:287-292 |
| FR-USR-D6 | Press PF5 (confirm) | Record deleted; fields cleared; green "User <id> has been deleted ..." | COUSR03C:121-122, 174-192, 305-322, 349-356 |
| FR-USR-D7 | Press PF3 | Return to caller (`CDEMO-FROM-PROGRAM`) or COADM01C; nothing deleted | COUSR03C:111-118 |
| FR-USR-D8 | Press PF4 | All fields and message cleared | COUSR03C:119-120, 341-356 |
| FR-USR-D9 | Press PF12 (not shown in footer) | Return to Admin menu (COADM01C) | COUSR03C:123-125 |
| FR-USR-D10 | Press any other key | "Invalid key pressed. Please see below..." | COUSR03C:126-129 |
| FR-USR-D11 | Hit a file error | "Unable to lookup User..." (read) or "Unable to Update User..." (delete); RESP/RESP2 logged | COUSR03C:293-299, 329-335 |

### DUSRSECJ — USRSEC initial load (batch)
| ID | Given input… the job… | Expected output | Source cite |
|----|------------------------|-----------------|-------------|
| FR-USR-B1 | Given any prior `AWS.M2.CARDDEMO.USRSEC.PS`, step PREDEL (IEFBR14) | Deletes the PS dataset (allocates MOD if absent, then deletes) | DUSRSECJ:23-26 |
| FR-USR-B2 | Given the 10 in-stream records, STEP01 (IEBGENER) | Creates and catalogs `AWS.M2.CARDDEMO.USRSEC.PS` (FB, LRECL 80) holding the 10 seed users | DUSRSECJ:32-52, 35-44 |
| FR-USR-B3 | STEP02 (IDCAMS) | Deletes `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS` (error suppressed by `SET MAXCC = 0`) and defines a KSDS with KEYS(8,0), RECORDSIZE(80,80), REUSE | DUSRSECJ:58-73 |
| FR-USR-B4 | STEP03 (IDCAMS REPRO) | Loads the PS records into the KSDS | DUSRSECJ:80-88 |

---

## 2. Screen / interface spec

All maps: 24×80, `CTRL=(ALARM,FREEKB)`, `EXTATT=YES`, header rows 1–2 (TRNNAME 4, TITLE01 40, CURDATE 8, PGMNAME 8, TITLE02 40, CURTIME 8 — all ASKIP/output), ERRMSG at (23,1) length 78 `ASKIP,BRT,FSET` default RED (e.g. `COUSR00.bms:29-74, 449-452`). Edit rules for every input field: "required" means not SPACES and not LOW-VALUES; no other format/case/value edits are performed by any program.

### COUSR0A (COUSR00.bms) — List Users
| Field | BMS field | Pos | Len | I/O | Attributes | Edit rules / source |
|-------|-----------|-----|-----|-----|-----------|---------------------|
| Page number | PAGENUM | (4,71) | 8 | O | ASKIP,FSET | `CDEMO-CU00-PAGE-NUM` 9(8) (COUSR00C:327, 376; COUSR00.bms:85-89) |
| Search User ID | USRIDIN | (6,21) | 8 | I | UNPROT,FSET; cursor set via length −1 | optional; blank → start of file (COUSR00C:218-222; COUSR00.bms:95-99) |
| Sel (row 1..10) | SEL0001..SEL0010 | (10..19,6) | 1 | I | UNPROT,FSET | `U`/`u`/`D`/`d`; other non-blank → error (COUSR00C:151-215; COUSR00.bms:153-158 …414-419) |
| User ID (row n) | USRID01..10 | (r,12) | 8 | O | ASKIP,FSET | SEC-USR-ID |
| First Name (row n) | FNAME01..10 | (r,24) | 20 | O | ASKIP,FSET | SEC-USR-FNAME |
| Last Name (row n) | LNAME01..10 | (r,48) | 20 | O | ASKIP,FSET | SEC-USR-LNAME |
| Type (row n) | UTYPE01..10 | (r,73) | 1 | O | ASKIP,FSET | SEC-USR-TYPE (COUSR00C:384-441) |
| Instruction | (literal) | (21,12) | 56 | O | ASKIP,BRT | `Type 'U' to Update or 'D' to Delete a User from the list` (COUSR00.bms:443-448) |
| Footer | (literal) | (24,1) | 48 | O | ASKIP | `ENTER=Continue  F3=Back  F7=Backward  F8=Forward` (COUSR00.bms:453-458) |

PF-key map: ENTER = select/search/refresh; PF3 = back to COADM01C; PF7 = page back; PF8 = page forward; other = invalid key (COUSR00C:122-137).

### COUSR1A (COUSR01.bms) — Add User
| Field | BMS field | Pos | Len | I/O | Attributes | Edit rules |
|-------|-----------|-----|-----|-----|-----------|-----------|
| First Name | FNAME | (8,18) | 20 | I | UNPROT,FSET,IC | required (COUSR01C:118-123) |
| Last Name | LNAME | (8,56) | 20 | I | UNPROT,FSET | required (COUSR01C:124-129) |
| User ID | USERID | (11,15) | 8 | I | UNPROT,FSET; label `(8 Char)` | required; must not already exist (COUSR01C:130-135, 260-266) |
| Password | PASSWD | (11,55) | 8 | I | UNPROT,FSET,**DRK** (non-display); label `(8 Char)` | required (COUSR01C:136-141) |
| User Type | USRTYPE | (14,17) | 1 | I | UNPROT,FSET; label `(A=Admin, U=User)` | required; value **not** validated (COUSR01C:142-147) |
| Footer | (literal) | (24,1) | 43 | O | ASKIP | `ENTER=Add User  F3=Back  F4=Clear  F12=Exit` (COUSR01.bms:155-159) |

PF-key map: ENTER = add; PF3 = back to COADM01C; PF4 = clear; other (incl. PF12) = invalid key (COUSR01C:90-103).

### COUSR2A (COUSR02.bms) — Update User
| Field | BMS field | Pos | Len | I/O | Attributes | Edit rules |
|-------|-----------|-----|-----|-----|-----------|-----------|
| Enter User ID | USRIDIN | (6,21) | 8 | I | UNPROT,FSET,IC | required; lookup key (COUSR02C:145-151, 180-185) |
| First Name | FNAME | (11,18) | 20 | I/O | UNPROT,FSET | required on save (COUSR02C:186-191) |
| Last Name | LNAME | (11,56) | 20 | I/O | UNPROT,FSET | required on save (COUSR02C:192-197) |
| Password | PASSWD | (13,16) | 8 | I/O | UNPROT,FSET,**DRK** | required on save; current password is loaded into the field (COUSR02C:169, 198-203) |
| User Type | USRTYPE | (15,17) | 1 | I/O | UNPROT,FSET; label `(A=Admin, U=User)` | required on save; value not validated (COUSR02C:204-209) |
| Footer | (literal) | (24,1) | 58 | O | ASKIP | `ENTER=Fetch  F3=Save&Exit  F4=Clear  F5=Save  F12=Cancel` (COUSR02.bms:159-164; `&&` is the BMS escape for `&`) |

PF-key map: ENTER = fetch; PF3 = save then exit to caller; PF4 = clear; PF5 = save; PF12 = cancel to COADM01C; other = invalid key (COUSR02C:108-131).

### COUSR3A (COUSR03.bms) — Delete User
| Field | BMS field | Pos | Len | I/O | Attributes | Edit rules |
|-------|-----------|-----|-----|-----|-----------|-----------|
| Enter User ID | USRIDIN | (6,21) | 8 | I | UNPROT,FSET,IC | required (COUSR03C:144-150, 176-182) |
| First Name | FNAME | (11,18) | 20 | O | **ASKIP** (protected), BLUE | display only (COUSR03.bms:103-107) |
| Last Name | LNAME | (13,18) | 20 | O | ASKIP | display only (COUSR03.bms:116-120) |
| User Type | USRTYPE | (15,17) | 1 | O | ASKIP | display only (COUSR03.bms:130-134) |
| Footer | (literal) | (24,1) | 58 | O | ASKIP | `ENTER=Fetch  F3=Back  F4=Clear  F5=Delete` (COUSR03.bms:144-148) |

Password is not on the delete map. PF-key map: ENTER = fetch; PF3 = back to caller; PF4 = clear; PF5 = delete; PF12 = COADM01C (undocumented on screen); other = invalid key (COUSR03C:108-130).

### DUSRSECJ — DD / file I/O
| Step | Program | DD | Dataset | Disp / attributes | Direction |
|------|---------|----|---------|-------------------|-----------|
| PREDEL | IEFBR14 | DD01 | AWS.M2.CARDDEMO.USRSEC.PS | (MOD,DELETE,DELETE) | delete |
| STEP01 | IEBGENER | SYSUT1 | in-stream (10 cards) | — | in |
| STEP01 | IEBGENER | SYSUT2 | AWS.M2.CARDDEMO.USRSEC.PS | (NEW,CATLG,DELETE) LRECL=80 RECFM=FB DSORG=PS SPACE=(TRK,(10,5),RLSE) | out |
| STEP01 | IEBGENER | SYSPRINT / SYSIN | SYSOUT / DUMMY | — | — |
| STEP02 | IDCAMS | SYSIN | in-stream DELETE/DEFINE CLUSTER | KSDS KEYS(8,0) RECORDSIZE(80,80) REUSE TRACKS(45,15) FREESPACE(10,15) CISZ(8192) | define |
| STEP03 | IDCAMS | IN | AWS.M2.CARDDEMO.USRSEC.PS | SHR | in |
| STEP03 | IDCAMS | OUT | AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS | SHR | out (REPRO) |

(DUSRSECJ:23-88.) CICS file `USRSEC` maps to the same KSDS (CARDDEMO.CSD:88-97: ADD/BROWSE/DELETE/READ/UPDATE all YES).

---

## 3. Business rules & calculations

| ID | Rule | Source cite |
|----|------|-------------|
| BR-USR-01 | USRSEC record (`SEC-USER-DATA`) is 80 bytes: SEC-USR-ID X(8) (key, offset 0), SEC-USR-FNAME X(20), SEC-USR-LNAME X(20), SEC-USR-PWD X(8), SEC-USR-TYPE X(1), SEC-USR-FILLER X(23). | CSUSR01Y:17-23; DUSRSECJ:64-66 |
| BR-USR-02 | User Type meaning: `A` = Admin, `U` = User (screen label and COMMAREA 88-levels only; the user-admin programs accept any non-blank character). | COUSR01.bms:146-150; COCOM01Y:26-28; COUSR01C:142-147; COUSR02C:204-209 |
| BR-USR-03 | At sign-on (stream #5) a stored type of exactly `A` routes to COADM01C, any other value to COMEN01C; user ID and password are upper-cased before lookup/compare. | COSGN00C:132-136, 223-240 |
| BR-USR-04 | List page size is 10 rows. | COUSR00C:293-306, 352-360 |
| BR-USR-05 | Browse positioning uses STARTBR on SEC-USR-ID with no EQUAL/GTEQ option (`GTEQ` is commented out; CICS default for STARTBR is GTEQ) → first key ≥ start key. | COUSR00C:586-595 |
| BR-USR-06 | Start key: ENTER → search value or LOW-VALUES; PF8 → last ID on current page (`CDEMO-CU00-USRID-LAST`) or HIGH-VALUES; PF7 → first ID on current page (`CDEMO-CU00-USRID-FIRST`) or LOW-VALUES. | COUSR00C:218-222, 262-266, 239-243 |
| BR-USR-07 | Forward page skips the anchor record with one extra READNEXT unless the AID is ENTER, PF7 or PF3; backward page skips it with one extra READPREV unless the AID is ENTER or PF8. | COUSR00C:288-290, 342-344 |
| BR-USR-08 | Next-page detection: after 10 rows are loaded one look-ahead READNEXT sets `CDEMO-CU00-NEXT-PAGE-FLG` to `Y` if a record exists, else `N`; a short page sets it `N`. | COUSR00C:308-318 |
| BR-USR-09 | Page number: ENTER resets to 0 then forward paging adds 1 (full page) or adds 1 only if ≥1 row was loaded (short page); backward paging subtracts 1 when another earlier record exists and page > 1, else sets page to 1. | COUSR00C:227, 308-323, 362-371 |
| BR-USR-10 | PF7 is honoured only when page > 1; PF8 only when NEXT-PAGE-FLG = `Y`; otherwise the "already at the top/bottom" message is shown without erasing the screen. | COUSR00C:248-255, 270-277 |
| BR-USR-11 | Row 1 receives `CDEMO-CU00-USRID-FIRST` and row 10 `CDEMO-CU00-USRID-LAST` (persisted in COMMAREA for paging). | COUSR00C:388-389, 434-435 |
| BR-USR-12 | Row selection: rows evaluated top→bottom, first non-blank Sel wins; action taken only if both the Sel value and that row's User ID are non-blank; `U`/`u` → XCTL COUSR02C, `D`/`d` → XCTL COUSR03C, passing the ID in `CDEMO-CU00-USR-SELECTED`. | COUSR00C:151-216 |
| BR-USR-13 | Add: all five fields required, checked in order First Name, Last Name, User ID, Password, User Type; only the first failure is reported. | COUSR01C:117-151 |
| BR-USR-14 | Add stores the screen values verbatim (no upper-casing, trimming or format check); password stored in clear text. | COUSR01C:153-158 |
| BR-USR-15 | User ID uniqueness is enforced only by the KSDS key: WRITE DUPKEY/DUPREC → "User ID already exist...". | COUSR01C:240-266 |
| BR-USR-16 | Update: User ID is the key and is not changeable; First Name, Last Name, Password, User Type are updatable. | COUSR02C:215-234, 358-366 |
| BR-USR-17 | Update save validation order: User ID, First Name, Last Name, Password, User Type (first failure only). | COUSR02C:179-213 |
| BR-USR-18 | Update change detection: the record is re-read with UPDATE at save time and REWRITE happens only if at least one of the four fields differs from that re-read record; otherwise "Please modify to update ...". | COUSR02C:215-243 |
| BR-USR-19 | Update/delete hold the VSAM record lock only inside a single task (READ UPDATE → REWRITE/DELETE in the same task); there is no lock or version check across the pseudo-conversation. | COUSR02C:320-331, 358-366; COUSR03C:267-278, 305-311 |
| BR-USR-20 | Delete is two-step: ENTER fetches and displays (protected fields) with "Press PF5 key to delete this user ..."; PF5 re-reads with UPDATE and issues DELETE (no RIDFLD — deletes the record just read). | COUSR03C:142-169, 174-192, 305-311 |
| BR-USR-21 | Success messages are built as `'User '` + SEC-USR-ID (delimited by first space) + `' has been added ...'` / `' has been updated ...'` / `' has been deleted ...'`. | COUSR01C:255-258; COUSR02C:372-375; COUSR03C:318-321 |
| BR-USR-22 | Message colour: default RED (BMS); success GREEN (`DFHGREEN`); "Press PF5 …" prompts NEUTRAL (`DFHNEUTR`); "Please modify to update ..." explicitly RED. | COUSR00.bms:449-452; COUSR01C:254; COUSR02C:241, 338, 371; COUSR03C:285, 317 |
| BR-USR-23 | Navigation targets: CU00/CU01 PF3 → COADM01C; CU02/CU03 PF3 → `CDEMO-FROM-PROGRAM` if non-blank else COADM01C; CU02/CU03 PF12 → COADM01C; any blank target defaults to COSGN00C. | COUSR00C:125-127, 506-510; COUSR01C:93-95, 167-169; COUSR02C:111-119, 124-126, 252-254; COUSR03C:111-118, 123-125, 199-201 |
| BR-USR-24 | Pseudo-conversation state: `CDEMO-PGM-CONTEXT` 0 = first entry (initialise screen), 1 = re-entry (RECEIVE map and dispatch AID); every outbound XCTL sets it to 0; each turn ends with `RETURN TRANSID(CU0n) COMMAREA(CARDDEMO-COMMAREA)`. | COUSR00C:115-121, 141-144, 513; COUSR01C:83-89, 107-110, 174; COUSR02C:95-107, 135-138, 257; COUSR03C:95-107, 134-137, 204 |
| BR-USR-25 | Passwords are never shown: PASSWD is DRK on add/update maps; the list and delete maps have no password field. | COUSR01.bms:126-130; COUSR02.bms:130-134; COUSR00C:384-441; COUSR03.bms:98-139 |
| BR-USR-26 | Seed data: ADMIN001–ADMIN005 type `A`, USER0001–USER0005 type `U`, all password `PASSWORD` (cols 1-8 ID, 9-28 first, 29-48 last, 49-56 pwd, 57 type). | DUSRSECJ:35-44 |
| BR-USR-27 | Header date = CURRENT-DATE as MM/DD/YY (year last 2 digits), time HH:MM:SS. | COUSR00C:564-581 (same in COUSR01C:216-233, COUSR02C:298-315, COUSR03C:245-262) |

---

## 4. Data access

| Program | File (CICS name → dataset) | Operation | Key | Record copybook | Cite |
|---------|----------------------------|-----------|-----|-----------------|------|
| COUSR00C | USRSEC → AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS | BROWSE: STARTBR (GTEQ default), READNEXT, READPREV, ENDBR | SEC-USR-ID (8) | CSUSR01Y | COUSR00C:586-691 |
| COUSR01C | USRSEC | WRITE | SEC-USR-ID (8) | CSUSR01Y | COUSR01C:238-248 |
| COUSR02C | USRSEC | READ UPDATE; REWRITE | SEC-USR-ID (8) | CSUSR01Y | COUSR02C:320-331, 358-366 |
| COUSR03C | USRSEC | READ UPDATE; DELETE (of record held by READ UPDATE) | SEC-USR-ID (8) | CSUSR01Y | COUSR03C:267-278, 305-311 |
| DUSRSECJ | USRSEC PS → KSDS | DELETE/DEFINE/REPRO (full reload) | KEYS(8,0) | (80-byte card image; matches CSUSR01Y) | DUSRSECJ:23-88 |

Other readers of USRSEC (outside scope): COSGN00C READ (COSGN00C:211-219). `OPENFIL`/`CLOSEFIL` JCL open/close the CICS file (OPENFIL.jcl:30, CLOSEFIL.jcl:30).

---

## 5. Message catalogue (exact text)

| Program | Message | Condition | Colour | Cite |
|---------|---------|-----------|--------|------|
| all | `Invalid key pressed. Please see below...` (CCDA-MSG-INVALID-KEY, padded to 50) | Unsupported AID | red | CSMSG01Y:20-21; COUSR00C:135; COUSR01C:101; COUSR02C:129; COUSR03C:128 |
| COUSR00C | `Invalid selection. Valid values are U and D` | Sel not U/u/D/d | red | COUSR00C:210-213 |
| COUSR00C | `You are already at the top of the page...` | PF7 on page ≤ 1 | red | COUSR00C:251 |
| COUSR00C | `You are already at the bottom of the page...` | PF8 with NEXT-PAGE-FLG = N | red | COUSR00C:273 |
| COUSR00C | `You are at the top of the page...` | STARTBR NOTFND | red | COUSR00C:600-604 |
| COUSR00C | `You have reached the bottom of the page...` | READNEXT ENDFILE | red | COUSR00C:634-638 |
| COUSR00C | `You have reached the top of the page...` | READPREV ENDFILE | red | COUSR00C:668-672 |
| COUSR00C | `Unable to lookup User...` | Other RESP on STARTBR/READNEXT/READPREV | red | COUSR00C:610, 644, 678 |
| COUSR01C | `First Name can NOT be empty...` | FNAME blank | red | COUSR01C:120 |
| COUSR01C | `Last Name can NOT be empty...` | LNAME blank | red | COUSR01C:126 |
| COUSR01C | `User ID can NOT be empty...` | USERID blank | red | COUSR01C:132 |
| COUSR01C | `Password can NOT be empty...` | PASSWD blank | red | COUSR01C:138 |
| COUSR01C | `User Type can NOT be empty...` | USRTYPE blank | red | COUSR01C:144 |
| COUSR01C | `User <id> has been added ...` | WRITE NORMAL | green | COUSR01C:254-258 |
| COUSR01C | `User ID already exist...` | WRITE DUPKEY/DUPREC | red | COUSR01C:263 |
| COUSR01C | `Unable to Add User...` | Other WRITE RESP | red | COUSR01C:270 |
| COUSR02C | `User ID can NOT be empty...` | USRIDIN blank (ENTER or save) | red | COUSR02C:148, 182 |
| COUSR02C | `First Name can NOT be empty...` / `Last Name can NOT be empty...` / `Password can NOT be empty...` / `User Type can NOT be empty...` | Field blank on save | red | COUSR02C:188, 194, 200, 206 |
| COUSR02C | `Please modify to update ...` | Save with no change | red | COUSR02C:239-241 |
| COUSR02C | `Press PF5 key to save your updates ...` | READ NORMAL | neutral | COUSR02C:336-338 |
| COUSR02C | `User ID NOT found...` | READ or REWRITE NOTFND | red | COUSR02C:342, 379 |
| COUSR02C | `Unable to lookup User...` | Other READ RESP | red | COUSR02C:349 |
| COUSR02C | `User <id> has been updated ...` | REWRITE NORMAL | green | COUSR02C:371-375 |
| COUSR02C | `Unable to Update User...` | Other REWRITE RESP | red | COUSR02C:386 |
| COUSR03C | `User ID can NOT be empty...` | USRIDIN blank (ENTER or PF5) | red | COUSR03C:147, 179 |
| COUSR03C | `Press PF5 key to delete this user ...` | READ NORMAL | neutral | COUSR03C:283-285 |
| COUSR03C | `User ID NOT found...` | READ or DELETE NOTFND | red | COUSR03C:289, 325 |
| COUSR03C | `Unable to lookup User...` | Other READ RESP | red | COUSR03C:296 |
| COUSR03C | `User <id> has been deleted ...` | DELETE NORMAL | green | COUSR03C:317-321 |
| COUSR03C | `Unable to Update User...` | Other DELETE RESP (sic — "Update") | red | COUSR03C:332 |

Logged (CICS job log via `DISPLAY`, not shown to user): `'RESP:' WS-RESP-CD 'REAS:' WS-REAS-CD` on unexpected RESP — COUSR00C:608, 642, 676; COUSR02C:347, 384; COUSR03C:294, 330. (COUSR01C's equivalent is commented out: COUSR01C:268.)

---

## 6. Navigation / integration

| From | To | Mechanism | Trigger | Cite |
|------|----|-----------|---------|------|
| COADM01C (Admin menu, CA00) | COUSR00C / COUSR01C / COUSR02C / COUSR03C | XCTL with COMMAREA, context 0 | Options 1–4 `User List/Add/Update/Delete (Security)` | COADM02Y.cpy:26-44; COADM01C:141-148 |
| COUSR00C | COUSR02C | XCTL, `CDEMO-FROM-PROGRAM='COUSR00C'`, `CDEMO-CU00-USR-SELECTED` = ID | Sel `U`/`u` | COUSR00C:190-199 |
| COUSR00C | COUSR03C | XCTL (same) | Sel `D`/`d` | COUSR00C:200-209 |
| COUSR00C, COUSR01C | COADM01C | XCTL | PF3 | COUSR00C:125-127; COUSR01C:93-95 |
| COUSR02C, COUSR03C | `CDEMO-FROM-PROGRAM` (COUSR00C or COADM01C) | XCTL | PF3 | COUSR02C:111-119; COUSR03C:111-118 |
| COUSR02C, COUSR03C | COADM01C | XCTL | PF12 | COUSR02C:124-126; COUSR03C:123-125 |
| all | COSGN00C | XCTL | EIBCALEN = 0 or blank target | COUSR00C:110-112, 508-510 (and equivalents) |
| all | self (CU0n) | RETURN TRANSID COMMAREA | end of each turn | COUSR00C:141-144; COUSR01C:107-110; COUSR02C:135-138; COUSR03C:134-137 |

No CALL, LINK, TDQ/TSQ or job submission is used. DUSRSECJ is not referenced by the scheduler definitions under `app/scheduler/` (only by README and `scripts/*`).

**COMMAREA (`CARDDEMO-COMMAREA`, COCOM01Y:19-44) fields used**

| Field | Use | Cite |
|-------|-----|------|
| CDEMO-FROM-TRANID / CDEMO-FROM-PROGRAM | Set to own tran/program on every XCTL; read by CU02/CU03 PF3 as return target | COUSR00C:511-512; COUSR02C:113-117, 255-256; COUSR03C:112-116, 202-203 |
| CDEMO-TO-PROGRAM | XCTL target | COUSR00C:508-516 |
| CDEMO-PGM-CONTEXT (88 CDEMO-PGM-ENTER/REENTER) | First-entry vs re-entry | COUSR00C:115-116, 513 |
| CDEMO-USER-ID / CDEMO-USER-TYPE | **Not used** (moves commented out) — no authorisation check in this stream | COUSR01C:172-173 |
| Extension `CDEMO-CU00-INFO` (appended after COCOM01Y in COUSR00C): USRID-FIRST X(8), USRID-LAST X(8), PAGE-NUM 9(8), NEXT-PAGE-FLG X(1), USR-SEL-FLG X(1), USR-SELECTED X(8) | Paging state and selected ID | COUSR00C:67-75 |
| Extension `CDEMO-CU02-INFO` / `CDEMO-CU03-INFO` (same layout, different names) | Only `…-USR-SELECTED` is read, relying on identical byte offset to `CDEMO-CU00-USR-SELECTED` | COUSR02C:50-58, 99-102; COUSR03C:50-58, 99-102 |

---

## 7. Edge cases, error handling, abend/return codes

| Case | Behaviour | Cite |
|------|-----------|------|
| No COMMAREA | XCTL to COSGN00C (no screen, no abend) | COUSR00C:110-112 etc. |
| File errors | Every EXEC CICS file command except ENDBR uses `RESP`/`RESP2`; unexpected RESP → message + DISPLAY; task continues, no ABEND issued explicitly; no `HANDLE CONDITION`/`HANDLE ABEND` | COUSR00C:586-682; COUSR02C:320-390; COUSR03C:267-336 |
| ENDBR | Issued without RESP — any exceptional condition (e.g. INVREQ when no browse is active) would raise the default CICS abend | COUSR00C:687-691 |
| Multiple rows selected | Only the first (top-most) non-blank Sel is acted on; others ignored | COUSR00C:151-185 |
| Sel typed on an empty row | Ignored silently (no message) because User ID is blank | COUSR00C:187-188 |
| Exactly 10 records remaining | Page shows 10 rows plus "You have reached the bottom of the page..." from the look-ahead | COUSR00C:308-316, 634-640 |
| Return from CU02/CU03 to list | Context reset to 0 → list redisplays from page 1 (start of file), not the previous page | COUSR02C:257; COUSR00C:115-119, 218-228 |
| Multiple SENDs per task | Paragraphs SEND the map on errors/EOF and the caller SENDs again (e.g. READ NORMAL sends, then PROCESS-ENTER-KEY sends) — last SEND is what the user sees | COUSR00C:604-606, 329; COUSR02C:339, 171; COUSR03C:286, 168 |
| CU02 PF3 | Save attempted, any validation/"Please modify" message is sent, then XCTL to caller regardless of outcome | COUSR02C:111-119 |
| Batch return codes | STEP02 forces `MAXCC = 0` after DELETE so a missing cluster is not an error; no COND= on any step, so STEP03 runs even if STEP02 fails | DUSRSECJ:62-63, 58, 80 |

---

## 8. Dependencies on other scopes

- Sign-on (COSGN00C, stream #5): consumer of USRSEC; upper-cases credentials and routes on SEC-USR-TYPE = `A` (COSGN00C:132-136, 211-240).
- Admin menu (COADM01C + COADM02Y, stream #5): sole inbound caller (options 1–4) and PF3/PF12 target.
- Shared copybooks: COCOM01Y (COMMAREA), COTTL01Y (titles), CSDAT01Y (date/time work fields), CSMSG01Y (common messages), DFHAID/DFHBMSCA.
- CICS resource definitions: `app/csd/CARDDEMO.CSD` (FILE USRSEC, MAPSETs COUSR00-03, PROGRAMs, TRANSACTIONs CU00-CU03).
- Ops JCL: OPENFIL/CLOSEFIL (open/close USRSEC in CICS); ESDSRRDS.jcl builds ESDS/RRDS copies of USRSEC (not used by these programs).

---

## 9. Open questions / ambiguities / apparent legacy bugs

(Not fixed — called out for migration decisions. Items marked *inference* are reasoned from code and CICS semantics, not executed.)

| # | Finding | Cite |
|---|---------|------|
| Q-1 | Add/Update store User ID and Password as typed, but sign-on upper-cases both before lookup/compare → a user created with lower-case ID or password can never sign on. | COUSR01C:154-157; COUSR02C:228; COSGN00C:132-136, 223 |
| Q-2 | User Type is never validated against `A`/`U`; any character (incl. lower-case `a`) is accepted and is treated as a regular user at sign-on. | COUSR01C:142-147; COUSR02C:204-209; COSGN00C:229-240 |
| Q-3 | CU02 PF3 ("Save&Exit") XCTLs away even when validation fails or nothing changed; the error message is sent but immediately replaced by the caller's screen. | COUSR02C:111-119 |
| Q-4 | CU02 save: if the re-READ fails (NOTFND/other) the error flag is set but comparison and REWRITE still run; REWRITE without a successful READ UPDATE is expected to fail and replace "User ID NOT found..." with "Unable to Update User..." (*inference*). | COUSR02C:215-237, 340-352, 383-389 |
| Q-5 | CU03 PF5: DELETE is issued even if the preceding READ UPDATE failed; DELETE without RIDFLD and without a held record is expected to yield INVREQ → "Unable to Update User..." (*inference*); DELETE's NOTFND branch is effectively unreachable. | COUSR03C:188-192, 305-336 |
| Q-6 | Delete failure text says "Unable to Update User..." (copy/paste from update). | COUSR03C:332 |
| Q-7 | CU02/CU03 PF5 act on whatever ID is in "Enter User ID" at that moment, not the one last fetched: CU03 can delete a user that was never displayed (single-step delete); CU02 can overwrite another user's names/password/type with the values on screen. | COUSR02C:215-237; COUSR03C:174-192 |
| Q-8 | "Confirm" for delete is only the PF5 key after a fetch; there is no Y/N confirmation field (unlike CT02). Confirm whether the target needs an explicit confirm. | COUSR03C:281-286, 121-122 |
| Q-9 | No optimistic-lock/version check across the pseudo-conversation: concurrent edits are last-writer-wins. | COUSR02C:215-237 |
| Q-10 | Footer/key mismatches: COUSR01 advertises `F12=Exit` but COUSR01C has no PF12 branch (→ invalid key); COUSR03C handles PF12 but its footer omits it. | COUSR01.bms:159; COUSR01C:90-103; COUSR03.bms:148; COUSR03C:123-125 |
| Q-11 | STARTBR NOTFND (search beyond last key, empty file, or PF8 with HIGH-VALUES) shows the misleading "You are at the top of the page..."; processing then continues to ENDBR (no RESP) with no active browse, and on PF8 also to READNEXT → likely INVREQ ("Unable to lookup User...") and/or an abend at ENDBR (*inference*, needs CICS run). | COUSR00C:288-290, 325, 597-606, 687-691 |
| Q-12 | "Invalid selection…" message can be overwritten by the EOF message produced by the subsequent page refresh (same WS-MESSAGE). | COUSR00C:210-215, 228, 634-638 |
| Q-13 | Backward paging that reaches start-of-file before filling 10 rows leaves leading rows blank and does not refresh `CDEMO-CU00-USRID-FIRST` (only set from row 1) (*inference*; only when records were inserted since paging forward). | COUSR00C:346-360, 388-389 |
| Q-14 | Search filter is case-sensitive and positional; collation differs between EBCDIC (lower < upper < digits) and ASCII (digits < upper < lower) — the migrated list order and search results will differ unless collation is pinned. | COUSR00C:218-222, 586-595 |
| Q-15 | CU02/CU03 read `CDEMO-CU02/CU03-USR-SELECTED`, which only coincide by byte offset with `CDEMO-CU00-USR-SELECTED`; when entered from COADM01C these bytes lie beyond EIBCALEN and hold uninitialised working storage (no VALUE clause, CSD STORAGECLEAR(NO)) — auto-fetch could fire on garbage (*inference*). | COUSR02C:50-58, 94, 99-104; COUSR03C:50-58, 94, 99-104; COUSR00C:67-75 |
| Q-16 | No authorisation in CU00–CU03 (CDEMO-USER-TYPE not checked; CSD RESSEC(NO)/CMDSEC(NO)); protection relies on menu navigation only. No guard against deleting/demoting oneself or the last admin. | COUSR01C:172-173; CARDDEMO.CSD:449-488 |
| Q-17 | Passwords are stored in clear text, and CU02 sends the current password to the terminal (hidden only by DRK). | CSUSR01Y:21; COUSR02C:169; COUSR02.bms:130 |
| Q-18 | Success message uses `SEC-USR-ID DELIMITED BY SPACE` → an ID with an embedded space is truncated in the message. | COUSR01C:255-258; COUSR02C:372-375; COUSR03C:318-321 |
| Q-19 | Dead code: `WS-USER-DATA` (USER-REC OCCURS 10) is declared but never referenced; `WS-USR-MODIFIED` is reset but never tested in COUSR03C (copied from COUSR02C); ENTER-path redundant `MOVE SPACE TO USRIDINO` after paging. | COUSR00C:56-64, 230-232; COUSR03C:45-47, 85 |
| Q-20 | DUSRSECJ is a destructive full reload (DELETE/DEFINE REUSE) with no COND= checks and is not in the Control-M/CA-7 schedule; confirm whether the target needs a seed-only migration script and the seed passwords (`PASSWORD`) kept. | DUSRSECJ:23-88, 35-44 |
| Q-21 | CSD defines FILE USRSEC with RECORDFORMAT(V) while the cluster is fixed RECORDSIZE(80,80) — harmless in practice but inconsistent. | CARDDEMO.CSD:93; DUSRSECJ:66 |

# S-08 TransactionTypeManagement — Stream Analysis

**Stream:** S-08 TransactionTypeManagement (CICS `CTLI`, `CTTU` + DB2 batch add-on)
**Legacy module:** `app/app-transaction-type-db2/`
**Target:** consolidated Spring Boot + React app in `migration/carddemo/`, backend package `com.carddemo.trantype`
**Boundary:** B-08 — embedded `EXEC SQL` against DB2 `CARDDEMO.TRANSACTION_TYPE` / `CARDDEMO.TRANSACTION_TYPE_CATEGORY` becomes JPA against the same PostgreSQL target (D-7); batch JCL becomes Spring Batch 5, one `Job` per JCL job and one `Step` per `EXEC PGM=` (D-3).

All line cites below are physical line numbers in the files under `app/app-transaction-type-db2/`.

---

## 1. Inventory

| Artifact | Type | Tx | Map/mapset | Function | LOC |
|---|---|---|---|---|---|
| `cbl/COTRTLIC.cbl` | CICS online | `CTLI` | `CTRTLIA` / `COTRTLI` | Paged transaction-type list with filters, inline update, inline delete | 2098 |
| `cbl/COTRTUPC.cbl` | CICS online | `CTTU` | `CTRTUPA` / `COTRTUP` | Single transaction-type lookup / add / update / delete | 1702 |
| `cbl/COBTUPDT.cbl` | Batch | — | — | Apply A/U/D maintenance records from a flat file to `TRANSACTION_TYPE` | 237 |
| `jcl/MNTTRDB2.jcl` | JCL | — | — | `IKJEFT01` → `RUN PROGRAM(COBTUPDT) PLAN(CARDDEMO)` with `INPFILE` inline data | 29 |
| `jcl/TRANEXTR.jcl` | JCL | — | — | Back up + re-extract the transaction type and category flat files from DB2 | 122 |
| `jcl/CREADB21.jcl` | JCL | — | — | Free plan/package, create the DB2 database/tablespaces/tables, load types and categories | 84 |
| `bms/COTRTLI.bms` | BMS | — | — | `CTLI` screen | 338 |
| `bms/COTRTUP.bms` | BMS | — | — | `CTTU` screen | 137 |
| `ddl/*`, `dcl/DCLTRTYP.dcl`, `dcl/DCLTRCAT.dcl` | DDL / DCLGEN | — | — | Table definitions and host-variable structures | — |

### Data stores

| Table | Key | Columns | Used by |
|---|---|---|---|
| `CARDDEMO.TRANSACTION_TYPE` (`TRNTYPE`) | `TR_TYPE CHAR(2)` | `TR_DESCRIPTION VARCHAR(50)` | `COTRTLIC` (browse/update/delete), `COTRTUPC` (select/insert/update/delete), `COBTUPDT` (insert/update/delete), `TRANEXTR` (unload), `CREADB21` (create/load) |
| `CARDDEMO.TRANSACTION_TYPE_CATEGORY` (`TRNTYCAT`) | `TRC_TYPE_CODE CHAR(2)` + `TRC_TYPE_CATEGORY CHAR(4)` | `TRC_CAT_DATA VARCHAR(50)` | `TRANEXTR` (unload), `CREADB21` (create/load); referenced indirectly by `COTRTLIC`/`COTRTUPC` deletes through the FK (`SQLCODE -532`) |

`TR_DESCRIPTION` is DCLGEN'd as a varying-length field (`DCL-TR-DESCRIPTION-LEN` + `DCL-TR-DESCRIPTION-TEXT`), which is why `COTRTUPC` writes `FUNCTION TRIM(...)` with an explicit length (`COTRTUPC:1539-1542`) and reads `TEXT(1:LEN)` (`COTRTUPC:1524-1525`).

---

## 2. `COTRTLIC` (CTLI) — paged list, inline update, inline delete

### 2.1 Constants and navigation targets (`COTRTLIC:40-70`)

```
LIT-THISPGM     'COTRTLIC'   LIT-THISTRANID  'CTLI'
LIT-THISMAPSET  'COTRTLI'    LIT-THISMAP     'CTRTLIA'
LIT-ADMINPGM    'COADM01C'   LIT-ADMINTRANID 'CA00'
LIT-ADDTPGM     'COTRTUPC'   LIT-ADDTTRANID  'CTTU'
LIT-ADDTMAPSET  'COTRTUP'    LIT-ADDTMAP     'CTRTUPA'
LIT-DELETE-FLAG 'D'          LIT-UPDATE-FLAG 'U'
WS-MAX-SCREEN-LINES 7
```

### 2.2 Entry conditions (`COTRTLIC:498-566`)

- `EIBCALEN = 0` (first entry): initialize both COMMAREA halves, `CDEMO-FROM-TRANID='CTLI'`, `CDEMO-FROM-PROGRAM='COTRTLIC'`, user type ADMIN, `CDEMO-PGM-ENTER`, first page, last-page-not-shown (`:515-525`).
- Otherwise the COMMAREA is split into `CARDDEMO-COMMAREA` and the program-private `WS-THIS-PROGCOMMAREA` (`:526-532`).
- Arriving from another program, or returning from `CTTU` with PF3, resets the private COMMAREA and forces ENTER/first page (`:544-554`).
- The map is only received (and inputs edited) when `EIBCALEN > 0` **and** `CDEMO-FROM-PROGRAM = 'COTRTLIC'` (`:561-566`).

### 2.3 Control flow / XCTL targets

| Condition | Target | Cite |
|---|---|---|
| PF3 | `CDEMO-TO-PROGRAM`; the literal MOVEd in is `LIT-ADMINPGM` = `COADM01C` (tranid `CA00`) when the caller is unknown or is CTLI itself, otherwise `CDEMO-FROM-PROGRAM` is echoed back. `EXEC CICS SYNCPOINT` then `XCTL PROGRAM(CDEMO-TO-PROGRAM)` | `:591-625` |
| PF2 (from CTLI itself) | `XCTL PROGRAM(LIT-ADDTPGM)` = `COTRTUPC`; `CCARD-NEXT-MAPSET`=`COTRTUP`, `CCARD-NEXT-MAP`=`CTRTUPA`; note it also sets `CDEMO-USRTYP-USER` (quirk: the list screen is admin-only but hands off as a plain user) | `:630-652` |
| all other paths | `CCARD-NEXT-PROG = 'COTRTLIC'`, `EXEC CICS RETURN TRANSID('CTLI')` with the concatenated COMMAREA | `:895-914` |

**AID validation** (`:574-587`): valid keys are ENTER, PF2, PF3, PF7, PF8, and PF10 only while a delete or update is pending. Anything else is silently remapped to ENTER — the list screen has **no** "invalid key" message.

**PF8 / last-page latch** (`:657-661`): any key other than PF8 resets `CA-LAST-PAGE-NOT-SHOWN`.

**PF10 downgrade quirk** (`:666-678`): PF10 only counts as a confirmation if the type filter, the description filter and the selected row are all unchanged since the previous screen; otherwise it is treated as ENTER.

**DB2 priming** (`:684-691`): a priming query runs before dispatch; on failure the long DB2 error text is sent and the program returns.

### 2.4 Dispatch (`COTRTLIC:698-879`)

| Case | Behaviour | Cite |
|---|---|---|
| `INPUT-ERROR` | error message to `CCARD-ERROR-MSG`; re-read the current page **unless** a filter is flagged bad; resend map | `:699-720` |
| PF7 on first page | re-read forward from the current first key (no move) | `:721-734` |
| PF3, or re-entry from another program | full reset, read forward from the first key | `:738-762` |
| PF8 and next page exists | start key = last key of current page, page number +1, read forward, clear selections | `:766-776` |
| PF7 and not first page | start key = first key of current page, page number −1, read backwards, clear selections | `:780-790` |
| ENTER with ≥1 `D` selected | re-read the page and resend (this is the "please confirm" screen) | `:794-806` |
| PF10 with ≥1 `D` selected | `9300-DELETE-RECORD`; on success set `FLG-DELETED-YES`, resend, then reset the whole COMMAREA to first page | `:810-834` |
| ENTER with ≥1 `U` selected | re-read the page and resend (the "confirm update" screen) | `:838-850` |
| PF10 with ≥1 `U` selected | `9200-UPDATE-RECORD`, then re-read forward from the first key and resend | `:854-868` |
| OTHER | read forward from the current first key and resend | `:870-878` |

### 2.5 Screen input capture (`COTRTLIC:930-954`)

`TRTYPEI` → type filter, `TRDESCI` → description filter. For each of the 7 rows: `TRTSELI(i)` → action flag, `TRTTYPI(i)` → row type code, and `TRTYPDI(i)` → row description, where `*` or all-spaces is treated as low-values and anything else is trimmed.

### 2.6 Field-by-field validation

**Row action array `1210-EDIT-ARRAY` (`:982-1053`)**
- If either filter changed, all selection flags are discarded and no action is processed (`:991-994`).
- Counts blanks/low-values, `D`s and `U`s across the 7 rows; `WS-ACTIONS-REQUESTED = 7 − blanks`.
- Rows are scanned **bottom to top**, so with several selections the *lowest-numbered* row wins `I-SELECTED` (`:1017-1040`).
- Valid flag but more than one action → row flagged in error and `Please select only 1 action` (`:1024-1027`, `:1049-1052`).
- Any other non-blank flag → `Action code selected is invalid` (`:1034-1038`).
- Changing which row is selected sets `FLG-ROW-SELECTION-CHANGED-YES` (used by the PF10 downgrade above) (`:1042-1047`).

**Row description `1211-EDIT-ARRAY-DESC` (`:1060-1090`)** — only run for a `U` row.
- Compare upper-cased trimmed new vs. old **and** trimmed lengths; equal → `No change detected with respect to database values.` and stop (`:1064-1073`).
- Otherwise validate as required alphanumeric, name `Transaction Desc`, length 50 (`:1083-1088`).

**Type filter `1220-EDIT-TYPECD` (`:1096-1140`)**
- Low-values, spaces **or zeros** → no filter (`:1101-1107`).
- Not numeric → `TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER`, select rows protected (`:1111-1118`). Quirk: the message says "2 digit" but only numericity is checked; `1` is accepted and matched as `01`… whatever the map delivers, no length check happens.
- Change of filter value re-initialises the paging variables (`:1127-1137`).

**Description filter `1230-EDIT-DESC` (`:1142-1178`)**
- Blank/low-values → no filter; otherwise the SQL filter becomes `'%' || TRIM(value) || '%'` (`:1155-1163`).
- Change of value re-initialises paging (`:1166-1175`).

**Generic alphanumeric edit `1240-EDIT-ALPHANUM-REQD` (`:1181-1237`)**
- Empty → `<name> must be supplied.`
- Any character outside letters/digits/space → `<name> can have numbers or alphabets only.`
- Only the **first** error message survives (`WS-RETURN-MSG-OFF` guard).

**Cross edit `1290-CROSS-EDITS` (`:1239-1271`)** — when either filter is valid, `9100-CHECK-FILTERS` counts matching rows; a count of 0 flags both filters bad, protects the select rows and shows `No Records found for these filter conditions` (`:1263-1265`). This is a *different* message from the empty-page one in §2.8.

### 2.7 Screen build (`COTRTLIC:1274-1610`)

- `2100-SCREEN-INIT` (`:1293-1323`): titles from `CCDA-TITLE01/02`, `TRNNAME`=`CTLI`, `PGMNAME`=`COTRTLIC`, `CURDATE` `mm/dd/yy`, `CURTIME` `hh:mm:ss`, `PAGENO` from the COMMAREA.
- `2200-SETUP-ARRAY-ATTRIBS` (`:1329-1373`): empty rows and "filters bad" protect the select field; a bad action flag turns the row red and puts the cursor on it; the single confirmed `D`/`U` row is highlighted neutral; for `U` the description becomes editable (red when invalid).
- `2300-SCREEN-ARRAY-INIT` (`:1383-1430`): sets `CA-DELETE-REQUESTED` / `CA-UPDATE-REQUESTED` for the pending row, or clears the flag once the action completed; echoes the typed description back while a change is pending, otherwise the database value; a blank description echoes as `*`.
- `2400-SETUP-SCREEN-ATTRS` (`:1438-1498`): filter fields are protected/blue while an action is pending; red plus cursor when a filter is invalid.
- `2500-SETUP-MESSAGE` (`:1504-1555`), in priority order:

| Condition | Info/error text |
|---|---|
| delete just succeeded | `HIGHLIGHTED row deleted.Hit Enter to continue` |
| update just succeeded | `HIGHLIGHTED row was updated` |
| a filter is invalid | (keep the validation error) |
| ENTER + exactly one valid `D` and filters unchanged | `Delete HIGHLIGHTED row ? Press F10 to confirm` |
| ENTER + exactly one valid `U` and filters unchanged | `Update HIGHLIGHTED row. Press F10 to save` |
| PF7 on first page | `No previous pages to display` |
| PF8, no next page, last page already shown | `No more pages to display` |
| PF8, no next page (first time) | `Type U to update, D to delete any record`, latch last-page-shown |
| default / next page exists | `Type U to update, D to delete any record` |

### 2.8 Data access

**Forward cursor `C-TR-TYPE-FORWARD` (declared `COTRTLIC:339-354`; opened `9400-OPEN-FORWARD-CURSOR` `:1942-1965`, closed `9450` `:1970-1993`)**

```sql
SELECT TR_TYPE, TR_DESCRIPTION
  FROM CARDDEMO.TRANSACTION_TYPE
 WHERE TR_TYPE >= :WS-START-KEY
   AND ((:WS-EDIT-TYPE-FLAG = '1' AND TR_TYPE = :WS-TYPE-CD-FILTER)
        OR (:WS-EDIT-TYPE-FLAG <> '1'))
   AND ((:WS-EDIT-DESC-FLAG = '1'
         AND TR_DESCRIPTION LIKE TRIM(:WS-TYPE-DESC-FILTER))
        OR (:WS-EDIT-DESC-FLAG <> '1'))
 ORDER BY TR_TYPE
```

**Backward cursor `C-TR-TYPE-BACKWARD`** (declared `:355-372`; opened `9500` `:1997-2021`, closed `9550` `:2026-2049`) — identical but `TR_TYPE < :WS-START-KEY` and `ORDER BY TR_TYPE DESC`.

`8000-READ-FORWARD` (`:1603-1724`): clear the 7 output rows, open, fetch up to 7 rows recording the first and last key, then one look-ahead fetch that sets `CA-NEXT-PAGE-EXISTS` / `CA-NEXT-PAGE-NOT-EXISTS`; an empty first page yields `No records found for this search condition.`; PF8 with nothing more yields `No more pages for these search conditions`; close.

`8100-READ-BACKWARDS` (`:1727-1794`): start below the current first key, fetch descending into slots 7→1 so the page is displayed ascending, update the first key, close.

`9200-UPDATE-RECORD` (`:1837-1892`)

```sql
UPDATE CARDDEMO.TRANSACTION_TYPE SET TR_DESCRIPTION = :DCL-TR-DESCRIPTION
 WHERE TR_TYPE = :DCL-TR-TYPE
```

| SQLCODE | Behaviour |
|---|---|
| 0 | `EXEC CICS SYNCPOINT`, update succeeded |
| +100 | `Record not found. Deleted by others ? ` + SQLCODE/SQLERRM |
| −911 | `Deadlock. Someone else updating ?` + SQLCODE/SQLERRM |
| other <0 | `Update failed with` + SQLCODE/SQLERRM |

`9300-DELETE-RECORD` (`:1896-1938`)

```sql
DELETE FROM CARDDEMO.TRANSACTION_TYPE WHERE TR_TYPE = :DCL-TR-TYPE
```

| SQLCODE | Behaviour |
|---|---|
| 0 | `EXEC CICS SYNCPOINT`, delete succeeded |
| −532 | `Please delete associated child records first:` + SQLCODE/SQLERRM |
| other | `Delete failed with message:` + SQLCODE/SQLERRM |

`9100-CHECK-FILTERS` (`:1801-1834`) runs a `SELECT COUNT(*)` with the same filter predicates; a negative SQLCODE produces the formatted `Error reading TRANSACTION_TYPE table ` message (`:1826`).

---

## 3. `COTRTUPC` (CTTU) — single-record maintenance

### 3.1 State machine

`TTUP-DETAILS-FETCH-STATUS` (`COTRTUPC:298-327`) drives everything:

| Value | 88-level | Meaning |
|---|---|---|
| low-values/spaces | `TTUP-DETAILS-NOT-FETCHED` | ask for a key |
| `K` | `TTUP-INVALID-SEARCH-KEYS` | key failed validation |
| `X` | `TTUP-DETAILS-NOT-FOUND` | key valid, no row |
| `S` | `TTUP-SHOW-DETAILS` | row displayed |
| `R` | `TTUP-CREATE-NEW-RECORD` | user chose to add |
| `E` | `TTUP-CHANGES-NOT-OK` | edits failed |
| `N` | `TTUP-CHANGES-OK-NOT-CONFIRMED` | edits passed, awaiting F5 |
| `C` | `TTUP-CHANGES-OKAYED-AND-DONE` | committed |
| `B` | `TTUP-CHANGES-BACKED-OUT` | update cancelled |
| `L` / `F` | lock error / write failed | |
| `9` / `8` / `7` / `6` | confirm delete / start delete / delete done / delete failed | |

### 3.2 Entry (`:345-381`)

`EXEC CICS HANDLE ABEND LABEL(ABEND-ROUTINE)` is established first (`:348-350`). The COMMAREA is re-initialised when `EIBCALEN = 0`, or when arriving fresh from `COADM01C` or from `COTRTLIC` (`:366-374`); otherwise it is split as in CTLI.

### 3.3 PF-key validity (`0001-CHECK-PFKEYS`, `:577-608`)

Valid combinations: PF3 always; ENTER except while confirming a delete; PF4 in `SHOW-DETAILS` or `CONFIRM-DELETE`; PF5 in `CHANGES-OK-NOT-CONFIRMED`, `DETAILS-NOT-FOUND` or delete-in-progress; PF12 in `CHANGES-OK-NOT-CONFIRMED`, `SHOW-DETAILS`, `DETAILS-NOT-FOUND`, `CONFIRM-DELETE` or `CREATE-NEW-RECORD`. Anything else sets `Invalid key pressed` (note: no trailing period; the other literal `Invalid Key pressed. ` at `:171-172` is dead code).

The mirror of this logic (`3391-SETUP-PFKEY-ATTRS`, `:1397-1423`) brightens exactly the keys that are currently valid: F4 for show/confirm-delete, F5 for confirm-changes/not-found, F12 for the five cancellable states, and darkens the `ENTER=Process F3=Exit` line while confirming a delete. `F6=Add` is present in the BMS map but is never enabled by the program — dead UI (`bms/COTRTUP.bms:126-130`).

### 3.4 Dispatch (`:405-556`)

- States `CHANGES-OKAYED-AND-DONE`, `CHANGES-FAILED`, `DELETE-DONE`, `DELETE-FAILED`, backed-out-with-no-old-key, and PF12 from show/create/not-found are collapsed into "simulate fresh entry" (`:405-419`).
- **PF3** → same caller/menu resolution as CTLI, `SYNCPOINT`, `XCTL PROGRAM(CDEMO-TO-PROGRAM)`; the literal MOVEd in when the caller is unknown is `LIT-ADMINPGM` = `COADM01C` / `CA00` (`:429-460`).
- Fresh entry from `COADM01C` or `COTRTLIC`, or enter-with-nothing-fetched → clear and send the empty key screen (`:465-478`).
- **PF4 while `CONFIRM-DELETE`** → `9800-DELETE-PROCESSING` (`:482-489`).
- **PF4 while `SHOW-DETAILS`** → move to `CONFIRM-DELETE` (`:493-498`).
- **PF5 while `DETAILS-NOT-FOUND`** → move to `CREATE-NEW-RECORD` (`:503-508`).
- **PF5 while `CHANGES-OK-NOT-CONFIRMED`** → `9600-WRITE-PROCESSING` (`:514-520`).
- **PF12** in a cancellable state → re-decide and resend (`:524-533`).
- Invalid key → just resend with the message (`:539-542`).
- Otherwise → receive, edit, decide, send (`:548-555`).

### 3.5 Input capture and validation

`1150-STORE-MAP-IN-NEW` (`:652-685`): if the key was not found and the user re-sent the same key without PF5, keep the state; otherwise `*` or spaces become low-values, and both fields are trimmed.

`1200-EDIT-MAP-INPUTS` (`:689-777`):
- Not-found + same key: skip all edits, mark the filter valid; without PF5, fall back to `DETAILS-NOT-FETCHED` (`:698-710`).
- In `CREATE-NEW-RECORD` or `CHANGES-OK-NOT-CONFIRMED` the key is not re-edited (`:712-714`).
- Otherwise `1210-EDIT-TRANTYPE` (`:820-843`): required numeric of length 2 → `Tran Type code must be supplied.` / `Tran Type code must be numeric.` / `Tran Type code must not be zero.` (`1245-EDIT-NUM-REQD`, `:907-974`); a valid value is normalised through `NUMVAL` and re-padded with leading zeros, so `7` becomes `07` (`:834-842`).
- Blank key → `No input received` (`:720-726`); invalid key → `TTUP-INVALID-SEARCH-KEYS` (`:728-732`).
- `1205-COMPARE-OLD-NEW` (`:783-816`): equal upper-cased key, upper-cased trimmed description **and** trimmed length → `No change detected with respect to values fetched.`
- Description edited with `1230-EDIT-ALPHANUM-REQD` (`:849-903`) → `Transaction Desc must be supplied.` / `Transaction Desc can have numbers or alphabets only.`
- No errors → `TTUP-CHANGES-OK-NOT-CONFIRMED`.

### 3.6 Action decision (`2000-DECIDE-ACTION`, `:978-1082`)

- `DETAILS-NOT-FETCHED` or PF12 with a valid key → `9000-READ-TRANTYPE`; found → `SHOW-DETAILS`, not found → `DETAILS-NOT-FOUND` (`:984-997`).
- PF12 with an invalid key → `Delete was cancelled` (from confirm-delete) or `Update was cancelled` + `CHANGES-BACKED-OUT` (`:999-1008`).
- `SHOW-DETAILS` with clean input and real changes → `CHANGES-OK-NOT-CONFIRMED` (`:1023-1030`).
- `CHANGES-BACKED-OUT` → `CHANGES-NOT-OK` (`:1041-1042`).
- PF5 with `DETAILS-NOT-FOUND` → `CREATE-NEW-RECORD` (`:1053-1055`).
- `CHANGES-OKAYED-AND-DONE` → back to `SHOW-DETAILS` (`:1065-1072`).
- Anything else → **abend** with culprit `COTRTUPC`, code `0001`, message `UNEXPECTED DATA SCENARIO` (`:1073-1080`).

### 3.7 Messages (`:142-196`, selected at `3250-SETUP-INFOMSG`, `:1210-1265`)

Info line (centred in a 40-char field, `:1249-1262`):

| State | Text |
|---|---|
| fresh entry / not fetched / invalid keys / no info | `Enter transaction type to be maintained` |
| key not found | `Press F05 to add. F12 to cancel` |
| show details / backed out with no old key | (`Selected transaction type shown above` is defined but reached only through the show-details branch ordering) |
| backed out / changes not ok | `Update transaction type details shown.` |
| confirm delete | `Delete this record ? Press F4 to confirm` |
| delete failed / lock error / write failed | `Changes unsuccessful` |
| delete done | `Delete successful.` |
| create new record | `Enter new transaction type details.` |
| changes ok not confirmed | `Changes validated.Press F5 to save` |
| changes committed | `Changes committed to database` |

Error line: `PF03 pressed.Exiting`, `Invalid Key pressed. `, `Name can only contain alphabets and spaces`, `No record found for this key in database`, `No input received`, `No change detected with respect to values fetched.`, `Could not lock record for update`, `Record changed by some one else. Please review`, `Update was cancelled`, `Update of record failed`, `Delete of record failed`, `Delete was cancelled`, `Invalid key pressed`, `Looks Good.... so far`, plus the dynamically built `<name> must be supplied.` / `must be numeric.` / `must not be zero.` / `can have numbers or alphabets only.` texts.

### 3.8 Data access

`9100-GET-TRANSACTION-TYPE` (`:1469-1512`)

```sql
SELECT TR_TYPE, TR_DESCRIPTION INTO :DCL-TR-TYPE, :DCL-TR-DESCRIPTION
  FROM CARDDEMO.TRANSACTION_TYPE WHERE TR_TYPE = :DCL-TR-TYPE
```
0 → found; +100 → `No record found for this key in database`; <0 → `Error accessing: TRANSACTION_TYPE table. SQLCODE:<code>:<sqlerrm>`.

`9600-WRITE-PROCESSING` (`:1531-1592`)

```sql
UPDATE CARDDEMO.TRANSACTION_TYPE SET TR_DESCRIPTION = :DCL-TR-DESCRIPTION
 WHERE TR_TYPE = :DCL-TR-TYPE
```
0 → `SYNCPOINT`; **+100 → falls through to `9700-INSERT-RECORD`** (this is how "add" works — the add path is an update that misses; quirk, preserved); −911 → `Could not lock record for update` + `CHANGES-OKAYED-LOCK-ERROR`; other <0 → `Error updating: TRANSACTION_TYPE Table. SQLCODE:<code>:<sqlerrm>` + `CHANGES-OKAYED-BUT-FAILED`.

`9700-INSERT-RECORD` (`:1596-1621`) — `INSERT INTO CARDDEMO.TRANSACTION_TYPE (TR_TYPE, TR_DESCRIPTION) VALUES (...)`; 0 → `SYNCPOINT`; anything else → `Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:<code>:<sqlerrm>`.

`9800-DELETE-PROCESSING` (`:1624-1664`) — deletes `TTUP-OLD-TTYP-TYPE`; 0 → `DELETE-DONE` + `SYNCPOINT`; −532 → `Please delete associated child records first:SQLCODE :<code>:<sqlerrm><sqlerrm>` (the source concatenates `SQLERRM` twice, `:1645-1646`); other → `Delete failed with message:SQLCODE :<code>:<sqlerrm>` and `DELETE-FAILED`.

---

## 4. `COBTUPDT` — batch maintenance

Input file `INPFILE`, fixed 53-byte records (`:71-77`): 1 byte operation, 2 bytes type, 50 bytes description.

Flow (`:82-107`): open (`OPEN FILE OK` / `OPEN FILE NOT OK`), read-then-loop-until-EOF displaying `PROCESSING   <record>`, close.

Dispatch (`1003-TREAT-RECORD`, `:109-130`): `A` → `ADDING RECORD` + insert; `U` → `UPDATING RECORD` + update; `D` → `DELETING RECORD` + delete; `*` → `IGNORING COMMENTED LINE`; anything else → `ERROR: TYPE NOT VALID` + abend routine.

SQL results:

| Op | SQLCODE 0 | +100 | <0 |
|---|---|---|---|
| insert (`:132-164`) | `RECORD INSERTED SUCCESSFULLY` | — (not tested) | `Error accessing: TRANSACTION_TYPE table. SQLCODE:<code>` + abend |
| update (`:166-195`) | `RECORD UPDATED SUCCESSFULLY` | `No records found.` + abend | same as above |
| delete (`:196-226`) | `RECORD DELETED SUCCESSFULLY` | `No records found.` + abend | same as above |

**Quirk (`9999-ABEND`, `:230-233`):** despite the name it does **not** stop the run. It displays the message, sets `RETURN-CODE` 4 and returns; the read loop continues with the next record. The job therefore ends with RC=4 (or whatever the last abend set) but every record is still attempted. `RETURN-CODE` is also not reset, so one bad record makes the whole step RC=4.

---

## 5. JCL

### `MNTTRDB2.jcl`
Single step `STEP05 EXEC PGM=IKJEFT01` running `DSN SYSTEM(DAZ1)` / `RUN PROGRAM(COBTUPDT) PLAN(CARDDEMO)`. `INPFILE` is inline `SYSIN`-style data; the documented layout is col 1 = `A`/`D`/`U`/`*`, cols 2-3 = numeric transaction type, cols 4-53 = description.

### `TRANEXTR.jcl`
| Step | PGM | Purpose |
|---|---|---|
| `STEP10` | `IEBGENER` | copy `TRANTYPE` flat file to `.BKUP` |
| `STEP20` | `IEBGENER` | copy `TRANCATG` flat file to `.BKUP` |
| `STEP30` | `IEFBR14` | delete the two output datasets |
| `STEP40` | `IKJEFT01`→`DSNTIAUL` | unload transaction types |
| `STEP50` | `IKJEFT01`→`DSNTIAUL` | unload transaction categories |

Type unload (60-byte records): `SELECT CAST(CONCAT(CONCAT(TR_TYPE, CAST(TR_DESCRIPTION AS CHAR(50))), REPEAT('0',8)) AS CHAR(60)) FROM CARDDEMO.TRANSACTION_TYPE ORDER BY TR_TYPE;`
Category unload (60-byte records): `SELECT CAST(TRC_TYPE_CODE || TRC_TYPE_CATEGORY || CAST(TRC_CAT_DATA AS CHAR(50)) || REPEAT('0',4) AS CHAR(60)) FROM CARDDEMO.TRANSACTION_TYPE_CATEGORY ORDER BY TRC_TYPE_CODE, TRC_TYPE_CATEGORY;`

So a type record is `2 + 50 + 8 zeroes` and a category record is `2 + 4 + 50 + 4 zeroes`, both 60 bytes — matching `app/data/ASCII/trantype.txt` and `tcatbal`-style fixtures.

### `CREADB21.jcl`
Symbolics `CODER=AWS`, `LBNM=&CODER..M2.CARDDEMO`, `DB2S=DAZ1`. Steps free the old plan/package, run the DDL that creates the CardDemo database/tablespaces and the two tables, and load them from the flat files via `DB2LTTYP` / `DB2LTCAT`.

---

## 6. Target mapping

| Legacy | Target |
|---|---|
| `CARDDEMO.TRANSACTION_TYPE` | `com.carddemo.common.domain.Db2TransactionTypeRecord` → table `db2_transaction_type` (already in the foundation schema) |
| `CARDDEMO.TRANSACTION_TYPE_CATEGORY` | `com.carddemo.common.domain.Db2TransactionTypeCategoryRecord` → `db2_transaction_type_category` |
| `EXEC SQL` cursors | Spring Data JPA derived queries + `Pageable`/`Limit`, same `ORDER BY TR_TYPE` semantics |
| `SQLCODE +100` | empty `Optional` |
| `SQLCODE -532` | `DataIntegrityViolationException` (FK from the category table) |
| `SQLCODE -911` | `PessimisticLockingFailureException` / `CannotAcquireLockException` |
| CICS `SYNCPOINT` | `@Transactional` commit boundary |
| COMMAREA state (`WS-CA-*`, `TTUP-*`) | request/response DTO fields — the React screens hold the state, exactly as the 3270 COMMAREA did |
| `XCTL COADM01C` | React route `/admin/menu` |
| `XCTL COTRTUPC` (PF2) | React navigation to `/admin/transaction-types/update` |
| `CALL 'CEE3ABD'` / abend | `AbendService` + failed Spring Batch step |
| `IKJEFT01 RUN PROGRAM(COBTUPDT)` | Spring Batch job `MNTTRDB2` with one step per `EXEC PGM=` |
| `DSNTIAUL` unloads | Spring Batch job `TRANEXTR` writing the same 60-byte fixed-width records |
| `CREADB21` DDL + load | Spring Batch job `CREADB21`; DDL is already the foundation Flyway schema, so the job owns only the (re)load of the two tables from the flat files |

**No new Flyway migration is required**: `db2_transaction_type` and `db2_transaction_type_category` already exist in the foundation schema, so the S-08 band `V800`–`V899` stays unused.

---

## 7. Source vs. inventory contradictions

1. `docs/migration/CardDemo_inventory.md` assigns `TRANEXTR.jcl` and `CREADB21.jcl` to **S-19**, while the task scope executes them here in S-08. They are implemented in this stream because they own S-08 data.
2. `migration/carddemo/README.md:38` lists S-08's package as `com.carddemo.transactiontype`; the stream instruction says `com.carddemo.trantype`. The explicit instruction wins — code lives in `com.carddemo.trantype`.
3. `COTRTLIC` PF2 sets `CDEMO-USRTYP-USER` (`:634`) even though CTLI is an admin screen; `COTRTUPC` sets admin back on PF3. Preserved as-is.

# COTRTLIC (CTLI) — Functional Requirements

**Source:** `app/app-transaction-type-db2/cbl/COTRTLIC.cbl` (2098 lines), map `CTRTLIA` in `bms/COTRTLI.bms`.
**Target:** `com.carddemo.trantype` — `TransactionTypeListController` / `TransactionTypeListService`, React page `TransactionTypeListPage`.
Stream-level IDs (FR-L*) are defined in `../TransactionTypeManagement_functional_requirement.md`; this document adds the screen contract and the rules behind them.

## 1. Screen contract (verbatim from `bms/COTRTLI.bms`)

| Row/col | Field | Len | Content |
|---|---|---|---|
| 1,1 / 1,7 | label / `TRNNAME` | 5 / 4 | `Tran:` / `CTLI` |
| 1,21 | `TITLE01` | 40 | application title line 1 |
| 1,65 / 1,71 | label / `CURDATE` | 5 / 8 | `Date:` / `mm/dd/yy` |
| 2,1 / 2,7 | label / `PGMNAME` | 5 / 8 | `Prog:` / `COTRTLIC` |
| 2,21 | `TITLE02` | 40 | application title line 2 |
| 2,65 / 2,71 | label / `CURTIME` | 5 / 8 | `Time:` / `hh:mm:ss` |
| 4,28 | literal | 25 | `Maintain Transaction Type` |
| 4,70 / 4,76 | literal / `PAGENO` | 5 / 3 | `Page ` / page number |
| 6,30 / 6,44 | literal / `TRTYPE` | 12 / 2 | `Type Filter:` / input |
| 8,4 / 8,25 | literal / `TRDESC` | 19 / 50 | `Description Filter:` / input |
| 10,4 / 10,16 / 10,42 | column heads | 10 / 4 / 11 | `Select    ` / `Type` / `Description` |
| 11,4 / 11,15 / 11,25 | rules | 6 / 5 / 50 | `------` / `-----` / 50 dashes |
| 12-18 | `TRTSEL1-7` (1), `TRTTYP1-7` (2), `TRTYPD1-7` (50) | | 7 data rows |
| 19 | `TRTSELA`, `TRTTYPA`, `TRTDSCA` | 1 / 2 / 50 | spare 8th row, never populated by the program |
| 21,19 | `INFOMSG` | 45 | information line (centred) |
| 23,1 | `ERRMSG` | 78 | error line (centred) |
| 24 | PF-key line | | `F2=Add`  `F3=Exit`  `F7=Page Up`  `F8=Page Dn`  `F10=Save` |

Row descriptions are unprotected only for the row being updated; the select field is protected on empty rows and when a filter is invalid (`COTRTLIC:1329-1373`).

## 2. Entry conditions

| # | Rule | Source |
|---|---|---|
| E1 | First entry (`EIBCALEN = 0`): fresh COMMAREA, admin user type, page 1, "last page not shown", no map receive | :498-525 |
| E2 | Re-entry from another program, or return from CTTU via PF3: private COMMAREA reset, AID forced to ENTER, page 1 | :544-554 |
| E3 | Map is only received when `EIBCALEN > 0` **and** `CDEMO-FROM-PROGRAM = 'COTRTLIC'` | :561-566 |
| E4 | A DB2 priming query runs before dispatch; if it fails the formatted DB2 error is displayed and control returns | :684-691 |

## 3. Validation rules

| # | Field | Rule | Message | Source |
|---|---|---|---|---|
| V1 | Type Filter | low-values / spaces / zeros → no filter | — | :1101-1107 |
| V2 | Type Filter | must be numeric (no length check despite the wording) | `TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER` | :1111-1118 |
| V3 | Description Filter | blank → no filter; otherwise wrapped as `%value%` | — | :1155-1163 |
| V4 | Either filter | a changed value resets the page state and discards row selections | — | :991-994, 1127-1137, 1166-1175 |
| V5 | Both filters | if a filter is set and the count query returns 0, both filters are flagged bad | `No Records found for these filter conditions` | :1239-1268 |
| V6 | Row action | valid values are `U`, `D` or blank/low-values | `Action code selected is invalid` | :1034-1038 |
| V7 | Row action | at most one row may be actioned | `Please select only 1 action` | :1024-1027, 1049-1052 |
| V8 | Row description (`U`) | must differ from the stored value (upper-cased, trimmed, same trimmed length) | `No change detected with respect to database values.` | :1064-1073 |
| V9 | Row description (`U`) | required | `Transaction Desc must be supplied.` | :1083-1088, 1181-1234 |
| V10 | Row description (`U`) | letters, digits and spaces only | `Transaction Desc can have numbers or alphabets only.` | :1181-1234 |
| V11 | Errors | only the first error message of a keystroke is displayed | — | :1181-1234 |

## 4. Business rules

| # | Rule | Source |
|---|---|---|
| B1 | A page is 7 rows, ordered ascending by `TR_TYPE` | :62, 1603-1724 |
| B2 | Paging uses key ranges, not offsets: forward `TR_TYPE >= start`, backward `TR_TYPE < start` ordered descending and re-displayed ascending | :339-372, 1603-1794 |
| B3 | "Next page exists" is decided by one extra look-ahead fetch after the 7th row | :1603-1724 |
| B4 | Rows are scanned bottom-to-top, so with multiple selections the lowest-numbered row is the one reported as selected (before the "only 1 action" error fires) | :1017-1040 |
| B5 | `U`/`D` require a two-keystroke confirmation: ENTER to arm, F10 to commit | :794-868 |
| B6 | F10 is downgraded to ENTER whenever the filters or the selected row changed in the same keystroke | :666-678 |
| B7 | A successful delete resets the whole screen state back to page 1 | :810-834 |
| B8 | A successful update re-reads the current page from its first key | :854-868 |
| B9 | Update and delete each end with `EXEC CICS SYNCPOINT` — one record per transaction | :1837-1938 |
| B10 | Valid AIDs are ENTER, F2, F3, F7, F8 and (only with a pending action) F10; every other AID is silently treated as ENTER | :574-587 |

## 5. Error paths (exact text)

| SQL / condition | Message |
|---|---|
| empty result on the first page | `No records found for this search condition.` |
| F8 with nothing further | `No more pages for these search conditions` (read path) / `No more pages to display` (message setup, repeat F8) |
| F7 on page 1 | `No previous pages to display` |
| update `SQLCODE +100` | `Record not found. Deleted by others ? ` + `SQLCODE :<code>:<sqlerrm>` |
| update `SQLCODE -911` | `Deadlock. Someone else updating ?` + SQL diagnostics |
| other update failure | `Update failed with` + SQL diagnostics |
| delete `SQLCODE -532` | `Please delete associated child records first:` + SQL diagnostics |
| other delete failure | `Delete failed with message:` + SQL diagnostics |
| count/cursor failure | `Error reading TRANSACTION_TYPE table ` + SQL diagnostics |

Successful actions: `HIGHLIGHTED row was updated`, `HIGHLIGHTED row deleted.Hit Enter to continue`.
Info line default: `Type U to update, D to delete any record`; armed actions: `Update HIGHLIGHTED row. Press F10 to save`, `Delete HIGHLIGHTED row ? Press F10 to confirm`.

## 6. Control flow

| AID | Target | Literal traced | Source |
|---|---|---|---|
| F3 | `XCTL CDEMO-TO-PROGRAM` | `LIT-ADMINPGM` = `COADM01C` (tran `CA00`) when the caller is unknown/self, else the caller | :591-625 |
| F2 | `XCTL LIT-ADDTPGM` | `COTRTUPC`, mapset `COTRTUP`, map `CTRTUPA`, tran `CTTU` | :630-652 |
| all others | `RETURN TRANSID('CTLI')` | `CCARD-NEXT-PROG` = `COTRTLIC` | :895-914 |

## 7. Data access

`SELECT TR_TYPE, TR_DESCRIPTION FROM CARDDEMO.TRANSACTION_TYPE` with the key-range + optional equality/LIKE predicates (cursors declared at `:339-372`), `UPDATE … SET TR_DESCRIPTION` (`:1837-1892`), `DELETE …` (`:1896-1938`), `SELECT COUNT(*)` for the filter cross-edit (`:1801-1834`).

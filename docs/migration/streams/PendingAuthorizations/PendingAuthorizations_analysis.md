# S-09 PendingAuthorizations — Source Analysis

Module: `app/app-authorization-ims-db2-mq/`. All line cites are `<file>:<line>` against that module
unless a full path is given.

## 1. Estate

| Artefact | Type | Role |
|---|---|---|
| `cbl/COPAUS0C.cbl` | CICS/BMS online, tran **CPVS** | Pending-authorization summary + 5-row list |
| `cbl/COPAUS1C.cbl` | CICS/BMS online, tran **CPVD** | Pending-authorization detail, fraud toggle |
| `cbl/COPAUS2C.cbl` | CICS LINKed subprogram | Fraud record insert/update into DB2 `AUTHFRDS` |
| `cbl/COPAUA0C.cbl` | CICS MQ-triggered, tran **CP00** | Authorization decision driver (MQGET/MQPUT1) |
| `cbl/CBPAUP0C.cbl` | IMS BMP batch (`jcl/CBPAUP0J.jcl`) | Purge expired authorizations |
| `cbl/PAUDBLOD.CBL` | IMS load utility (`jcl/LOADPADB.JCL`) | Loads DBPAUTP0 from two flat files |
| `cbl/PAUDBUNL.CBL` | IMS unload utility (`jcl/UNLDPADB.JCL`) | Unloads DBPAUTP0 to two flat files |
| `cbl/DBUNLDGS.CBL` | IMS unload utility (`jcl/UNLDGSAM.JCL`) | Same unload, output via GSAM PCBs |
| `bms/COPAU00.bms` | BMS map `COPAU0A` | CPVS screen |
| `bms/COPAU01.bms` | BMS map `COPAU1A` | CPVD screen |
| `ims/DBPAUTP0.dbd`, `ims/DBPAUTX0.dbd` | IMS DBD | HDAM root `PAUTSUM0`, child `PAUTDTL1`; secondary index |
| `ims/PSBPAUTB`, `ims/PSBPAUTL` | PSB | BMP purge PSB; load/unload PSB |
| `ddl/AUTHFRDS.ddl`, `dcl/AUTHFRDS.dcl` | DB2 | Fraud table |
| `cpy/CIPAUSMY.cpy` | Copybook | `PAUTSUM0` segment layout |
| `cpy/CIPAUDTY.cpy` | Copybook | `PAUTDTL1` segment layout |
| `cpy/CCPAURQY.cpy`, `CCPAURLY.cpy`, `CCPAUERY.cpy` | Copybook | MQ request / reply / error-log layouts |
| `cpy/IMSFUNCS.cpy` | Copybook | DL/I function codes `GU GHU GN GHN GNP GHNP REPL ISRT DLET` |

### 1.1 Record layouts

`PAUTSUM0` (root, keyed on `PA-ACCT-ID`), `cpy/CIPAUSMY.cpy`:

```
PA-ACCT-ID            S9(11) COMP-3   PA-CUST-ID           9(09)
PA-AUTH-STATUS        X(01)           PA-ACCOUNT-STATUS    X(02) OCCURS 5
PA-CREDIT-LIMIT       S9(09)V99 C-3   PA-CASH-LIMIT        S9(09)V99 C-3
PA-CREDIT-BALANCE     S9(09)V99 C-3   PA-CASH-BALANCE      S9(09)V99 C-3
PA-APPROVED-AUTH-CNT  S9(04) COMP     PA-DECLINED-AUTH-CNT S9(04) COMP
PA-APPROVED-AUTH-AMT  S9(09)V99 C-3   PA-DECLINED-AUTH-AMT S9(09)V99 C-3
FILLER X(34)
```

`PAUTDTL1` (child, keyed on `PA-AUTHORIZATION-KEY`), `cpy/CIPAUDTY.cpy`. The key is a **9s-complement
inverted timestamp** so that DL/I key order returns the newest authorization first:

```
PA-AUTHORIZATION-KEY = PA-AUTH-DATE-9C S9(05) C-3  ||  PA-AUTH-TIME-9C S9(09) C-3
PA-AUTH-ORIG-DATE X(06) YYMMDD   PA-AUTH-ORIG-TIME X(06) HHMMSS
PA-CARD-NUM X(16)  PA-AUTH-TYPE X(04)  PA-CARD-EXPIRY-DATE X(04) YYMM
PA-MESSAGE-TYPE X(06)  PA-MESSAGE-SOURCE X(06)  PA-AUTH-ID-CODE X(06)
PA-AUTH-RESP-CODE X(02) (88 PA-AUTH-APPROVED '00')   PA-AUTH-RESP-REASON X(04)
PA-PROCESSING-CODE 9(06)  PA-TRANSACTION-AMT S9(10)V99 C-3  PA-APPROVED-AMT S9(10)V99 C-3
PA-MERCHANT-CATAGORY-CODE X(04)  PA-ACQR-COUNTRY-CODE X(03)  PA-POS-ENTRY-MODE 9(02)
PA-MERCHANT-ID X(15) PA-MERCHANT-NAME X(22) PA-MERCHANT-CITY X(13)
PA-MERCHANT-STATE X(02) PA-MERCHANT-ZIP X(09) PA-TRANSACTION-ID X(15)
PA-MATCH-STATUS X(01)  88 P pending / D declined / E pending-expired / M matched
PA-AUTH-FRAUD   X(01)  88 F confirmed / R removed
PA-FRAUD-RPT-DATE X(08)  FILLER X(17)
```

MQ request `cpy/CCPAURQY.cpy` — 18 comma-delimited fields in this order: auth date, auth time,
card num, auth type, card expiry, message type, message source, processing code, transaction amount
(`+9(10).99`), MCC, acquirer country, POS entry mode, merchant id, merchant name, merchant city,
merchant state, merchant zip, transaction id.

MQ reply `cpy/CCPAURLY.cpy` — card num, transaction id, auth id code, resp code, resp reason,
approved amount (`+9(10).99`); emitted comma-delimited **with a trailing comma** (COPAUA0C:722-731).

### 1.2 Boundaries

* **B-07 IMS** — `EXEC DLI` (online, BMP) and `CALL 'CBLTDLI'` (load/unload) against `DBPAUTP0`.
  Root `PAUTSUM0` keyed `ACCNTID`, child `PAUTDTL1` keyed `PAUT9CTS`.
* **B-09 DB2** — `CARDDEMO.AUTHFRDS`, PK `(CARD_NUM, AUTH_TS)` (`ddl/AUTHFRDS.ddl`).
* **B-06 MQ** — request queue `AWS.M2.CARDDEMO.PAUTH.REQUEST`, reply queue
  `AWS.M2.CARDDEMO.PAUTH.REPLY` (module README); the reply queue name is actually taken from the
  request message's `MQMD-REPLYTOQ` (COPAUA0C:413-414).
* **VSAM** — `CARDXREF` (`WS-CCXREF-FILE`), `ACCTDAT`, `CUSTDAT` read by CPVS and CP00.

---

## 2. COPAUS0C — CPVS summary/list

### 2.1 Entry conditions (COPAUS0C:176-256)

* `EIBCALEN = 0` → `INITIALIZE CARDDEMO-COMMAREA`, `CDEMO-TO-PROGRAM = 'COPAUS0C'`, set re-enter,
  clear map, send empty screen.
* First entry from another program (`NOT CDEMO-PGM-REENTER`): if `CDEMO-ACCT-ID` is numeric it is
  moved to the account field and `GATHER-DETAILS` is performed, else the account field is blanked.
* Re-entry: `RECEIVE`, then `EVALUATE EIBAID` — ENTER → `PROCESS-ENTER-KEY`; PF3 → return to
  `COMEN01C` (`WS-PGM-MENU`); PF7 → `PROCESS-PF7-KEY`; PF8 → `PROCESS-PF8-KEY`; any other key →
  `CCDA-MSG-INVALID-KEY` (`Invalid key pressed. Please see below...`, `cpy/CSMSG01Y.cpy`).
* Always `EXEC CICS RETURN TRANSID('CPVS') COMMAREA(CARDDEMO-COMMAREA)`.

### 2.2 Validation (COPAUS0C:261-338)

1. Account id blank/low-values → `Please enter Acct Id...`, cursor to account field, no data read.
2. Account id not numeric → `Acct Id must be Numeric ...`.
3. Otherwise the account is accepted and the five selection fields `SEL0001I..SEL0005I` are scanned
   **in order**; the *first* non-blank one wins and its row key is copied to
   `CDEMO-CPVS-PAU-SELECTED` (COPAUS0C:289-315).
4. Selection value `S`/`s` → `XCTL PROGRAM('COPAUS1C')` with `CDEMO-FROM-TRANID='CPVS'`,
   `CDEMO-FROM-PROGRAM='COPAUS0C'`, `CDEMO-PGM-CONTEXT=0`, `CDEMO-PGM-ENTER`. Any other value →
   `Invalid selection. Valid value is S` (COPAUS0C:328-333).
5. After validation `GATHER-DETAILS` always runs (COPAUS0C:341), so an invalid selection still
   refreshes page 1 of the list.

### 2.3 Account/summary gather (COPAUS0C:749-807)

`GETCARDXREF-BYACCT` (alternate-index read of `CXACAIX`), `GETACCTDATA-BYACCT`, `GETCUSTDATA-BYCUST`,
then the header fields:

* `CUSTIDO` ← `CUST-ID`; `CNAMEO` ← first + middle-initial + last (`STRING ... DELIMITED BY SPACES`).
* `CREDLIMO` ← `ACCT-CREDIT-LIMIT` via `PIC -zzzzzzz9.99` (12 chars);
  `CASHLIMO` ← `ACCT-CASH-CREDIT-LIMIT` via `PIC -zzzz9.99` (9 chars).
* Summary found → `APPRCNTO`/`DECLCNTO` via `PIC 9(03)`, `CREDBALO` (12), `CASHBALO`, `APPRAMTO`,
  `DECLAMTO` (9 chars each). Summary not found → all six fields get `ZERO`.
* Not-found paths set `WS-MESSAGE` from the shared file-error handling and suppress the list.

### 2.4 Paging (COPAUS0C:344-455)

* Page size 5 (`PERFORM UNTIL WS-IDX > 5`).
* `PROCESS-PAGE-FORWARD` performs `GET-AUTHORIZATIONS` (`EXEC DLI GNP SEGMENT(PAUTDTL1)`) per row;
  the key of the first row of each page is remembered in `CDEMO-CPVS-PAUKEY-PREV-PG(page)` and the
  key of the last row read in `CDEMO-CPVS-PAUKEY-LAST`.
* After the 5 rows, one extra `GNP` decides `NEXT-PAGE-YES/NO` (COPAUS0C:445-452).
* PF7 with `CDEMO-CPVS-PAGE-NUM > 1` decrements the page and repositions on the remembered key,
  else `You are already at the top of the page...` (COPAUS0C:378-395).
* PF8 repositions on `CDEMO-CPVS-PAUKEY-LAST`; if there is no next page,
  `You are already at the bottom of the page...` (COPAUS0C:400-419).
* IMS statuses: `' '` ok, `GE`/`GB` → end of list, anything else →
  `' System error while reading AUTH Details: Code:' + status` (COPAUS0C:475-480) or
  `' System error while repos. AUTH Details: Code:' + status` (COPAUS0C:505-510).

### 2.5 Row rendering (COPAUS0C:521-545)

* Amount column ← **`PA-APPROVED-AMT`** (COPAUS0C:525) through `PIC -zzzzzzz9.99` — declined rows therefore show
  `0.00`, not the requested amount (quirk Q-1).
* Date `YYMMDD` → `MM/DD/YY`; time `HHMMSS` → `HH:MM:SS`.
* `A/D` column: `A` when `PA-AUTH-RESP-CODE = '00'`, else `D`.
* `STS` column ← `PA-MATCH-STATUS` raw.
* `Sel` field of a populated row is unprotected (`DFHBMUNP`); empty rows are protected.

---

## 3. COPAUS1C — CPVD detail

### 3.1 Entry / keys (COPAUS1C:156-205)

`EIBCALEN = 0` → return to `COPAUS0C`. First entry → `PROCESS-ENTER-KEY`. Re-entry: ENTER → re-read;
PF3 → back to `COPAUS0C`; PF5 → `MARK-AUTH-FRAUD`; PF8 → `PROCESS-PF8-KEY`; other → re-read plus
`CCDA-MSG-INVALID-KEY`.

`PROCESS-ENTER-KEY` (COPAUS1C:208-226) requires `CDEMO-ACCT-ID` numeric **and**
`CDEMO-CPVD-PAU-SELECTED` non-blank; otherwise `ERR-FLG-ON` and the detail area is left empty with
**no message** (quirk Q-2).

`READ-AUTH-RECORD` (COPAUS1C:432-491): schedule PSB, `GU PAUTSUM0 WHERE ACCNTID = :acct`, then
`GNP PAUTDTL1 WHERE PAUT9CTS = :key`. Failure text:
`' System error while reading Auth Summary: Code:'` / `' System error while reading Auth Details: Code:'`.

### 3.2 Rendering (COPAUS1C:291-359)

`Card #` ← `PA-CARD-NUM`; `Auth Date` `MM/DD/YY`; `Auth Time` `HH:MM:SS`; `Amount` ←
`PA-APPROVED-AMT` via `PIC -zzzzzzz9.99`; `Auth Resp` `A` (green) when resp code `00` else `D` (red).

`Resp Reason` is `SEARCH ALL` over `WS-DECLINE-REASON-TAB` (COPAUS1C:60-73) rendered as
`cccc-DESCRIPTION`; on no match `9999-ERROR`:

```
0000 APPROVED        3100 INVALID CARD    4100 INSUFFICNT FUND
4200 CARD NOT ACTIVE 4300 ACCOUNT CLOSED  4400 EXCED DAILY LMT
5100 CARD FRAUD      5200 MERCHANT FRAUD  5300 LOST CARD       9000 UNKNOWN
```

`Auth Code` ← **`PA-PROCESSING-CODE`** (COPAUS1C:331), not `PA-AUTH-ID-CODE` (quirk Q-3).
`POS Entry Mode` ← `PA-POS-ENTRY-MODE`; `Source` ← `PA-MESSAGE-SOURCE`; `MCC Code` ←
`PA-MERCHANT-CATAGORY-CODE`; `Card Exp. Date` ← `YY/MM`; `Auth Type`, `Tran Id`, `Match Status` raw.
`Fraud Status` ← `f-MM/DD/YY` (`PA-AUTH-FRAUD`, `-`, `PA-FRAUD-RPT-DATE`) when the fraud flag is
`F` or `R`, else `-`.

### 3.3 Fraud toggle (COPAUS1C:230-266, 517-548)

1. Re-read the authorization.
2. `IF PA-FRAUD-CONFIRMED` → set `PA-FRAUD-REMOVED` and action `R`, else set `PA-FRAUD-CONFIRMED`
   and action `F`.
3. Build `WS-FRAUD-DATA` = acct id `9(11)` + cust id `9(9)` + the whole detail segment + action.
4. `EXEC CICS LINK PROGRAM(WS-PGM-AUTH-FRAUD)` where `WS-PGM-AUTH-FRAUD VALUE 'COPAUS2C'`
   (COPAUS1C:35) — the symbolic target resolves to **COPAUS2C**.
5. LINK normal and `WS-FRD-UPDT-SUCCESS` → `UPDATE-AUTH-DETAILS` (`REPL PAUTDTL1`), `SYNCPOINT`, and
   `AUTH FRAUD REMOVED...` / `AUTH MARKED FRAUD...`.
6. LINK normal but update failed → message is COPAUS2C's `WS-FRD-ACT-MSG`, `ROLLBACK`.
7. `REPL` failure → `ROLLBACK` and
   `' System error while FRAUD Tagging, ROLLBACK||' + IMS status`.

Note that COPAUS2C stamps `PA-FRAUD-RPT-DATE` in the *caller's* record (COPAUS2C:101), so the date
shown on the refreshed screen comes back through the COMMAREA.

### 3.4 PF8 (COPAUS1C:268-288)

Re-read the current authorization, then a bare `GNP PAUTDTL1`. End of chain → `SEND-ERASE-NO` and
`Already at the last Authorization...`; otherwise the new key becomes `CDEMO-CPVD-PAU-SELECTED`.

---

## 4. COPAUS2C — fraud persistence (DB2)

COMMAREA: acct id `9(11)`, cust id `9(9)`, the `PAUTDTL1` image, then action `X(01)`
(`F`/`R`), update status `X(01)` (`S`/`F`) and `WS-FRD-ACT-MSG X(50)` (COPAUS2C:73-86).

1. `ASKTIME`/`FORMATTIME MMDDYY DATESEP` → `PA-FRAUD-RPT-DATE` = `MM/DD/YY` (COPAUS2C:91-101).
2. `AUTH_TS` is rebuilt from the inverted key:
   `WS-AUTH-TIME = 999999999 - PA-AUTH-TIME-9C`, formatted
   `YY-MM-DD HH.MI.SSmmm000` and parsed with `TIMESTAMP_FORMAT(..,'YY-MM-DD HH24.MI.SSNNNNNN')`
   (COPAUS2C:103-114). Date part comes from `PA-AUTH-ORIG-DATE`.
3. `INSERT INTO CARDDEMO.AUTHFRDS` with all 26 columns; `FRAUD_RPT_DATE` = `CURRENT DATE`
   (COPAUS2C:141-198).
4. `SQLCODE = 0` → status `S`, message `ADD SUCCESS`.
5. `SQLCODE = -803` (duplicate key) → `FRAUD-UPDATE`: `UPDATE ... SET AUTH_FRAUD, FRAUD_RPT_DATE =
   CURRENT DATE WHERE CARD_NUM = :c AND AUTH_TS = :ts` → `UPDT SUCCESS`, else status `F` and
   `' UPDT ERROR DB2: CODE:' + SQLCODE + ', STATE: ' + SQLSTATE`.
6. Any other insert SQLCODE → status `F` and
   `' SYSTEM ERROR DB2: CODE:' + SQLCODE + ', STATE: ' + SQLSTATE`.
   `WS-SQLCODE` is `PIC +9(06)` and `WS-SQLSTATE` `PIC +9(09)`.

---

## 5. COPAUA0C — CP00 MQ authorization driver

### 5.1 Task shape (COPAUA0C:230-344)

`RETRIEVE` the MQ trigger message for the request queue name, `MQOPEN` (`MQOO-INPUT-SHARED`;
failure → critical `REQ MQ OPEN ERROR`), then `MQGET` with `MQGMO-WAIT` 5000 ms. The loop runs until
`MQRC-NO-MSG-AVAILABLE` or `WS-MSG-PROCESSED > WS-REQSTS-PROCESS-LIMIT (500)`; each iteration
processes one message and takes a CICS `SYNCPOINT`. Because the test is *after* the increment, the
task actually drains **501** messages before stopping (quirk Q-4).

### 5.2 Per-message flow (COPAUA0C:438-466)

`SCHD PSB` (failure → `IMS SCHD FAILED`) → `5100-READ-XREF-RECORD` → if the card resolved,
`5200-READ-ACCT-RECORD`, `5300-READ-CUST-RECORD`, `5500-READ-AUTH-SUMMRY` → `6000-MAKE-DECISION` →
`7100-SEND-RESPONSE` → if the card resolved, `8000-WRITE-AUTH-TO-DB`.

Reads and their error text: `CARD NOT FOUND IN XREF` (warning) / `FAILED TO READ XREF FILE`
(critical); `ACCT NOT FOUND IN XREF` / `FAILED TO READ ACCT FILE`; `CUST NOT FOUND IN XREF` /
`FAILED TO READ CUST FILE`; `IMS GET SUMMARY FAILED`; `IMS UPDATE SUMRY FAILED`;
`IMS INSERT DETL FAILED`; `FAILED TO PUT ON REPLY MQ`; `FAILED TO READ REQUEST MQ`;
`FAILED TO CLOSE REQUEST MQ`. Every one is written to TD queue `CSSL` as a `CCPAUERY` record;
`ERR-CRITICAL` additionally terminates the task (COPAUA0C:982-1010).

### 5.3 Decision (COPAUA0C:657-732)

```
auth id code := request auth time
if summary exists : available = PA-CREDIT-LIMIT - PA-CREDIT-BALANCE
elif account read : available = ACCT-CREDIT-LIMIT - ACCT-CURR-BAL
else              : decline
if amount > available : decline, insufficient funds
decline -> resp '05', approved amt 0 ; approve -> resp '00', approved amt = request amount
reason  -> 0000 default; 3100 card/acct/cust not found; 4100 insufficient; 4200 card not active;
           4300 account closed; 5100 card fraud; 5200 merchant fraud; 9000 otherwise
```

`CARD-NOT-ACTIVE`, `ACCOUNT-CLOSED`, `CARD-FRAUD`, `MERCHANT-FRAUD` are declared but never set by
this program, so 4200/4300/5100/5200 are unreachable today (quirk Q-5). Reply string is the six
reply fields each followed by `,` (trailing comma included), approved amount as `-zzzzzzzzz9.99`.

### 5.4 Persistence (COPAUA0C:786-933)

`8400-UPDATE-SUMMARY` runs **before** `8500-INSERT-AUTH`:

* summary missing → `INITIALIZE ... REPLACING NUMERIC DATA BY ZERO`, set acct/cust id from the XREF.
* always refresh `PA-CREDIT-LIMIT`/`PA-CASH-LIMIT` from the account record.
* approved → `+1` approved count, `+approved amt` approved amount, `+approved amt` credit balance,
  cash balance set to `0`.
* declined → `+1` declined count and `+ PA-TRANSACTION-AMT` declined amount — but
  `PA-TRANSACTION-AMT` lives in the *detail* work area which is only populated later at 8500, so the
  declined amount accumulates the **previous message's** transaction amount (zero for the first
  message of the task). This is quirk Q-6 and is preserved.
* `REPL` when the summary existed, `ISRT` when it did not.

`8500-INSERT-AUTH` builds the detail: `PA-AUTH-DATE-9C = 99999 - yyddd`,
`PA-AUTH-TIME-9C = 999999999 - (hhmmss*1000 + ms)`, copies the 18 request fields, copies auth id
code / resp code / resp reason / approved amount from the reply, sets match status `P` when approved
and `D` when declined, blanks fraud flag and fraud date, then
`ISRT PAUTSUM0 WHERE ACCNTID=:acct, PAUTDTL1 FROM (...)`.

---

## 6. CBPAUP0C — purge expired authorizations (BMP)

`jcl/CBPAUP0J.jcl` runs `DFSRRC00` with `PARM='BMP,CBPAUP0C,PSBPAUTB'` and SYSIN `00,00001,00001,Y`
mapped to `P-EXPIRY-DAYS, P-CHKP-FREQ, P-CHKP-DIS-FREQ, P-DEBUG-FLAG` (CBPAUP0C:196-214). Defaults
when the parm is not numeric/blank: expiry days 5, checkpoint frequency 5, display frequency 10,
debug `N`.

Algorithm (CBPAUP0C:140-160, 275-300):

```
GN PAUTSUM0 (root scan) until GB
  GNP PAUTDTL1 until GE/GB
     auth_date := 99999 - PA-AUTH-DATE-9C
     day_diff  := today_yyddd - auth_date
     if day_diff >= expiry_days:
        if PA-AUTH-RESP-CODE = '00': approved cnt -1, approved amt -= PA-APPROVED-AMT
        else                       : declined cnt -1, declined amt -= PA-TRANSACTION-AMT
        DLET PAUTDTL1
  if PA-APPROVED-AUTH-CNT <= 0 AND PA-APPROVED-AUTH-CNT <= 0:  DLET PAUTSUM0
```

The summary-delete condition (CBPAUP0C:156) tests the approved count **twice** — the declined count is never
checked (quirk Q-7). The counter updates are applied to the in-memory root and are persisted only by
the root `DLET`/checkpoint behaviour of the BMP; there is no `REPL` of `PAUTSUM0` in this program
(quirk Q-8).

## 7. IMS load / unload utilities

* `PAUDBLOD.CBL` — reads `INFILE1` (root records, 100 bytes) and `INFILE2` (child records, 100
  bytes, prefixed by the root key). Root: `ISRT PAUTSUM0`; status `II` is tolerated
  ("ROOT SEGMENT ALREADY IN DB"). Child: `GU` the parent by key, then `ISRT PAUTDTL1`; `II`
  tolerated. Any other status → `DISPLAY` + `MOVE 16 TO RETURN-CODE` (PAUDBLOD:236-306).
* `PAUDBUNL.CBL` — `GN PAUTSUM0` (until `GB`) writing `OPFILE1`, and per root `GNP PAUTDTL1` (until
  `GE`) writing `OPFILE2`; roots with a non-numeric account id are skipped (PAUDBUNL:141-196).
* `DBUNLDGS.CBL` — identical traversal, but the two output records are written with
  `ISRT` against GSAM PCBs instead of QSAM `WRITE`.

## 8. Screens (BMS)

`COPAU00.bms` (`COPAU0A`) — header `Tran:`/`Date:`/`Prog:`/`Time:`, title `View Authorizations`,
`Search Acct Id:`, `Name: `, `Customer Id: `, `Acct Status: `, `PH:`, `Approval # : `, `Decline #:`,
`Credit Lim:`, `Cash Lim:`, `Appr Amt:`, `Credit Bal:`, `Cash Bal:`, `Decl Amt:`, column headings
`Sel`, ` Transaction ID `, `  Date  `, `  Time  `, `Type `, `A/D`, `STS`, `   Amount   `,
`Type 'S' to View Authorization details from the list`,
`ENTER=Continue  F3=Back  F7=Backward  F8=Forward`.

`COPAU01.bms` (`COPAU1A`) — title `View Authorization Details`, `Card #:`, `Auth Date:`,
`Auth Time:`, `Auth Resp:`, `Resp Reason:`, `Auth Code:`, `Amount:`, `POS Entry Mode:`,
`Source   :`, `MCC Code:`, `Card Exp. Date:`, `Auth Type:`, `Tran Id:`, `Match Status:`,
`Fraud Status:`, `Merchant Details -------------------------------`, `Name:`, `Merchant ID:`,
`City:`, `State:`, `Zip:`, ` F3=Back  F5=Mark/Remove Fraud  F8=Next Auth`.

## 9. Quirk register

| Id | Quirk | Source |
|---|---|---|
| Q-1 | List/detail `Amount` shows `PA-APPROVED-AMT`, so declined rows show `0.00` | COPAUS0C:525, COPAUS1C:308 |
| Q-2 | CPVD entered without a numeric account or a selected key renders an empty screen with no message | COPAUS1C:208-222 |
| Q-3 | CPVD `Auth Code:` displays `PA-PROCESSING-CODE`, not `PA-AUTH-ID-CODE` | COPAUS1C:331 |
| Q-4 | CP00 drains 501 messages, not 500 | COPAUA0C:339 |
| Q-5 | Decline reasons 4200/4300/5100/5200 are unreachable in CP00 | COPAUA0C:707-714 |
| Q-6 | Declined summary amount accumulates the previous message's transaction amount | COPAUA0C:821 |
| Q-7 | Purge deletes the summary on `approved<=0 AND approved<=0` (declined count ignored) | CBPAUP0C:156 |
| Q-8 | Purge never `REPL`s the summary counters it decrements | CBPAUP0C:275-300 |
| Q-9 | CP00 reply string ends with a trailing comma | COPAUA0C:722-731 |
| Q-10 | CPVS selection scan takes the first non-blank of the five rows, ignoring the rest | COPAUS0C:289-315 |

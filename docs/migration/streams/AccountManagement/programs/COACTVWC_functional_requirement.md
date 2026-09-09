# COACTVWC (CAVW) — Functional Requirements

**Program:** `app/cbl/COACTVWC.cbl` · **Transaction:** `CAVW` · **Mapset/Map:** `COACTVW `/`CACTVWA` (`app/bms/COACTVW.bms`)
**Files:** `CXACAIX` (CCXREF alternate index by account), `ACCTDAT`, `CUSTDAT`
**Migrated surface:** `GET /api/accounts/{accountId}` → `com.carddemo.account.controller.AccountViewController`, React `pages/account/AccountViewPage.js` at `/accounts/view`.

## 1. Entry / exit
| ID | Requirement | Cite |
|----|-------------|------|
| V-01 | With no commarea (`EIBCALEN = 0`), or on arrival from `COMEN01C` without `CDEMO-PGM-REENTER`, the screen opens empty with the info line `Enter or update id of account to display`. | COACTVWC:416-440 |
| V-02 | Only ENTER and PF3 are honoured; every other AID is silently treated as ENTER. | COACTVWC:441-470 |
| V-03 | PF3 transfers to `CDEMO-FROM-TRANID`/`CDEMO-FROM-PROGRAM`; when those are empty it transfers to `CM00`/`COMEN01C`. | COACTVWC:472-520 |
| V-04 | Before every send/transfer the program stores its own identity in the navigation fields: tran `CAVW`, program `COACTVWC`, mapset `COACTVW `, map `CACTVWA`. | COACTVWC:142-149, 577-587 |

## 2. Input edit (`2200-EDIT-MAP-INPUTS` / `2210-EDIT-ACCOUNT`)
| ID | Condition | Message | Cite |
|----|-----------|---------|------|
| V-05 | Account id blank / `LOW-VALUES` | `No input received` (the blank-branch literal `Account number not provided` is overwritten by the caller — quirk FR-AQ-01) | COACTVWC:600-620, 649-658 |
| V-06 | Account id not numeric | `Account Filter must  be a non-zero 11 digit number` | COACTVWC:660-676 |
| V-07 | Account id numeric but zero | `Account Filter must  be a non-zero 11 digit number` | COACTVWC:660-676 |
| V-08 | Account id valid | Stored in `CDEMO-ACCT-ID`, lookups proceed | COACTVWC:677-680 |

## 3. File access (fixed order, stops at the first failure)
| ID | Step | Failure message | Cite |
|----|------|-----------------|------|
| V-09 | READ `CXACAIX` by account id → `XREF-CUST-ID`, `XREF-CARD-NUM` | `Account:<id11> not found in Cross ref file.  Resp:000000013  Reas:0000` | COACTVWC:723-771 |
| V-10 | READ `ACCTDAT` by account id | `Account:<id11> not found in Acct Master file.Resp:000000013  Reas:0000` | COACTVWC:774-821 |
| V-11 | READ `CUSTDAT` by `XREF-CUST-ID` | `CustId:<id9> not found in customer master.Resp: 000000013  REAS:0000000` | COACTVWC:825-870 |
| V-12 | All three reads succeed | Map is populated and the info line reads `Displaying details of given Account` | COACTVWC:687-715, 880-1050 |

The messages are built by `STRING` into `WS-RETURN-MSG PIC X(75)`, so they are **truncated at 75 characters** — this is why the `Reas:` value appears clipped. `Resp`/`Reas` are the CICS `RESP`/`RESP2` values moved into `PIC X(10)` fields (`NOTFND` = 13, reason 0).

## 4. Display formatting
| ID | Field | Format | Cite |
|----|-------|--------|------|
| V-13 | Account id | 11 digits, zero padded | COACTVW.bms:88 (`PICIN='99999999999'`) |
| V-14 | Current Balance, Credit Limit, Cash credit Limit, Current Cycle Credit, Current Cycle Debit | `+ZZZ,ZZZ,ZZZ.99` — fixed sign, comma grouping, blank-suppressed integer part, always 2 decimals | COACTVW.bms:120,141,162,174,195 |
| V-15 | SSN | `999-99-9999` | COACTVWC:1010-1017 |
| V-16 | Dates (opened / expiry / reissue / date of birth) | Shown as stored, `YYYY-MM-DD` | COACTVWC:900-960 |
| V-17 | Customer id | 9 digits, zero padded | COACTVWC:1000-1009 |
| V-18 | Field order and labels | Exactly as `COACTVW.bms`: account block (`Active Y/N:`, `Opened:`, `Credit Limit        :`, `Expiry:`, `Cash credit Limit   :`, `Reissue:`, `Current Balance     :`, `Current Cycle Credit:`, `Account Group:`, `Current Cycle Debit :`) then customer block (`Customer id  :` … `Primary Card Holder Y/N:`) | COACTVW.bms |
| V-19 | PF-key line | `  F3=Exit` | COACTVW.bms |

## 5. Migration mapping
| Legacy | Migrated |
|--------|----------|
| `2210-EDIT-ACCOUNT` | `AccountFilterValidator.validateViewFilter` → `AccountFilterException` → HTTP 400 |
| `9200/9300/9400` reads | `AccountLookupService.lookup` (xref → account → customer, same order) → `AccountNotFoundException` → HTTP 404 |
| Map population `3000-SEND-MAP` | `AccountViewResponse.from(...)` with `AccountFormat` for amounts/SSN/ids |
| `CCARD-NEXT-*` / `XCTL` | React route `/accounts/view`; F3 navigates back via `useNavigate` |

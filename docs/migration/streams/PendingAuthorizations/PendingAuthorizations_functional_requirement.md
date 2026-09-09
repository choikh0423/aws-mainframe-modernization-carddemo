# S-09 PendingAuthorizations — Functional Requirements

Business sign-off oracle for the stream. Every requirement is traceable to source (see
`PendingAuthorizations_analysis.md`) and covered by a test (see the traceability matrix in
`PendingAuthorizations_migration_plan.md`).

Requirement ids: `FR-PA-Snn` summary/list (CPVS), `FR-PA-Dnn` detail (CPVD), `FR-PA-Fnn` fraud
persistence, `FR-PA-Ann` MQ authorization driver (CP00), `FR-PA-Bnn` batch, `FR-PA-Xnn` cross-cutting.

## Cross-cutting

| Id | Requirement | Source |
|---|---|---|
| FR-PA-X01 | Amounts on both screens are rendered with COBOL edit mask `-zzzzzzz9.99` (12 chars): sign position blank when positive, leading zeros suppressed, always two decimals. | COPAUS0C:55-57 |
| FR-PA-X02 | Cash/appr/decl amounts on CPVS use `-zzzz9.99` (9 chars) and counts use `9(03)` (zero padded). | COPAUS0C:56-58 |
| FR-PA-X03 | Authorization dates stored as `YYMMDD` are displayed `MM/DD/YY`; times stored `HHMMSS` are displayed `HH:MM:SS`; card expiry `YYMM` is displayed `YY/MM`. | COPAUS0C:527-534, COPAUS1C:340-342 |
| FR-PA-X04 | The authorization key is the 9s-complement pair `(99999 - yyddd, 999999999 - hhmmssSSS)`; browsing a customer's authorizations in key order returns the newest first. | CIPAUDTY.cpy, COPAUA0C:874-875 |
| FR-PA-X05 | Account ids are 11 digits and customer ids 9 digits, zero padded on display. | CIPAUSMY.cpy |

## CPVS — summary and list (COPAUS0C)

| Id | Requirement | Source |
|---|---|---|
| FR-PA-S01 | Opening CPVS with no account id shows the empty screen with title `View Authorizations` and no message. | COPAUS0C:190-199 |
| FR-PA-S02 | A blank account id on ENTER is rejected with exactly `Please enter Acct Id...` and no data is read. | COPAUS0C:264-271 |
| FR-PA-S03 | A non-numeric account id is rejected with exactly `Acct Id must be Numeric ...`. | COPAUS0C:272-281 |
| FR-PA-S04 | For a valid account the header shows customer id, name (`first middle-initial last`), phone, credit limit, cash limit from the account/customer master. | COPAUS0C:757-783 |
| FR-PA-S05 | When a pending-authorization summary exists the header shows approved count, declined count, credit balance, cash balance, approved amount and declined amount from it. | COPAUS0C:786-799 |
| FR-PA-S06 | When no summary exists those six fields are zero and the list is empty. | COPAUS0C:800-807 |
| FR-PA-S07 | The list shows at most 5 authorizations per page: transaction id, date, time, type, A/D flag, match status and amount. | COPAUS0C:420-443 |
| FR-PA-S08 | The A/D column is `A` when the authorization response code is `00`, otherwise `D`. | COPAUS0C:531-535 |
| FR-PA-S09 | The `Amount` column shows the **approved** amount, so declined rows show `0.00` (quirk Q-1, preserved). | COPAUS0C:525 |
| FR-PA-S10 | F8 shows the next page; when there is no next page the message is exactly `You are already at the bottom of the page...` and the current page stays displayed. | COPAUS0C:400-418 |
| FR-PA-S11 | F7 shows the previous page; on page 1 the message is exactly `You are already at the top of the page...`. | COPAUS0C:378-394 |
| FR-PA-S12 | Selecting a row with `S` or `s` navigates to CPVD (`COPAUS1C`) for that authorization key, carrying the account id. | COPAUS0C:316-327 |
| FR-PA-S13 | Any other selection character produces exactly `Invalid selection. Valid value is S` and the list is redisplayed. | COPAUS0C:328-333 |
| FR-PA-S14 | Only the first non-blank selection field of the five rows is honoured (quirk Q-10, preserved). | COPAUS0C:289-315 |
| FR-PA-S15 | An unknown function key produces `Invalid key pressed. Please see below...`. | COPAUS0C:246-250 |
| FR-PA-S16 | F3 returns to the main menu. | COPAUS0C:236-239 |
| FR-PA-S17 | An account id that does not resolve through the card cross-reference / account master reports the legacy file-error message and shows no list. | COPAUS0C:GETCARDXREF-BYACCT..GETCUSTDATA-BYCUST |

## CPVD — detail (COPAUS1C)

| Id | Requirement | Source |
|---|---|---|
| FR-PA-D01 | CPVD displays card number, auth date, auth time, amount, response flag, response reason, auth code, POS entry mode, source, MCC, card expiry, auth type, transaction id, match status, fraud status and the merchant block, with the labels of `COPAU01.bms`. | COPAUS1C:291-359 |
| FR-PA-D02 | `Resp Reason` renders `cccc-DESCRIPTION` from the decline table (`0000 APPROVED`, `3100 INVALID CARD`, `4100 INSUFFICNT FUND`, `4200 CARD NOT ACTIVE`, `4300 ACCOUNT CLOSED`, `4400 EXCED DAILY LMT`, `5100 CARD FRAUD`, `5200 MERCHANT FRAUD`, `5300 LOST CARD`, `9000 UNKNOWN`). | COPAUS1C:60-73, 317-328 |
| FR-PA-D03 | A response reason that is not in the table renders `9999-ERROR`. | COPAUS1C:318-320 |
| FR-PA-D04 | `Auth Code:` shows the processing code (quirk Q-3, preserved). | COPAUS1C:331 |
| FR-PA-D05 | `Fraud Status:` shows `F-MM/DD/YY` or `R-MM/DD/YY` when the fraud flag is set, otherwise `-`. | COPAUS1C:344-350 |
| FR-PA-D06 | The displayed amount is the approved amount (quirk Q-1, preserved). | COPAUS1C:308 |
| FR-PA-D07 | Requesting CPVD without a numeric account id or without a selected authorization renders an empty detail screen with no message (quirk Q-2, preserved). | COPAUS1C:208-222 |
| FR-PA-D08 | F5 on an authorization that is not confirmed fraud marks it fraud (`F`), persists the fraud record, and reports exactly `AUTH MARKED FRAUD...`. | COPAUS1C:230-243, 528-534 |
| FR-PA-D09 | F5 on an authorization already confirmed fraud removes the mark (`R`) and reports exactly `AUTH FRAUD REMOVED...`. | COPAUS1C:234-237, 528-533 |
| FR-PA-D10 | The fraud action is delegated to the `COPAUS2C` equivalent; if it reports failure the returned message is shown and nothing is committed (rollback). | COPAUS1C:248-262 |
| FR-PA-D11 | A failure updating the authorization segment reports `' System error while FRAUD Tagging, ROLLBACK||'` followed by the status code, and rolls back. | COPAUS1C:536-546 |
| FR-PA-D12 | F8 shows the next authorization of the same account; at the end of the chain the message is exactly `Already at the last Authorization...` and the current authorization stays displayed. | COPAUS1C:268-288 |
| FR-PA-D13 | F3 returns to CPVS. | COPAUS1C:186-188 |
| FR-PA-D14 | An unknown key re-reads the authorization and reports `Invalid key pressed. Please see below...`. | COPAUS1C:194-198 |

## Fraud persistence (COPAUS2C)

| Id | Requirement | Source |
|---|---|---|
| FR-PA-F01 | A fraud action inserts a row into `AUTHFRDS` keyed `(card number, auth timestamp)` carrying every field of the authorization plus the account and customer id, the fraud action and the current date as fraud report date. | COPAUS2C:113-198 |
| FR-PA-F02 | The auth timestamp is reconstructed as `YY-MM-DD HH.MI.SS.mmm000` from `PA-AUTH-ORIG-DATE` and `999999999 - PA-AUTH-TIME-9C`. | COPAUS2C:103-114 |
| FR-PA-F03 | A successful insert returns status `S` and message exactly `ADD SUCCESS`. | COPAUS2C:199-201 |
| FR-PA-F04 | A duplicate key (`SQLCODE -803`) updates `AUTH_FRAUD` and `FRAUD_RPT_DATE` of the existing row and returns status `S` with message exactly `UPDT SUCCESS`. | COPAUS2C:203, 221-232 |
| FR-PA-F05 | Any other insert error returns status `F` with `' SYSTEM ERROR DB2: CODE:'`+sqlcode+`', STATE: '`+sqlstate; an update error returns `' UPDT ERROR DB2: CODE:'`+sqlcode+`', STATE: '`+sqlstate. | COPAUS2C:205-214, 233-242 |
| FR-PA-F06 | The fraud report date `MM/DD/YY` is written back onto the authorization record shown to the user. | COPAUS2C:91-101 |

## CP00 — MQ authorization driver (COPAUA0C)

| Id | Requirement | Source |
|---|---|---|
| FR-PA-A01 | A request message is 18 comma-delimited fields in the `CCPAURQY` order; the transaction amount is converted with `NUMVAL` semantics. | COPAUA0C:354-379 |
| FR-PA-A02 | The card number is resolved through the card cross-reference; when it is not found the authorization is declined and `CARD NOT FOUND IN XREF` is logged. | COPAUA0C:475-500 |
| FR-PA-A03 | Missing account or customer master records log `ACCT NOT FOUND IN XREF` / `CUST NOT FOUND IN XREF`. | COPAUA0C:538-547, 586-595 |
| FR-PA-A04 | Available amount is `credit limit - credit balance` from the pending-authorization summary when it exists, otherwise `account credit limit - account current balance`. | COPAUA0C:665-679 |
| FR-PA-A05 | A transaction amount above the available amount is declined with reason `4100`. | COPAUA0C:668-670, 705-706 |
| FR-PA-A06 | An unresolvable card/account/customer is declined with reason `3100`; any other decline is `9000`. | COPAUA0C:701-716 |
| FR-PA-A07 | Approved: response code `00`, reason `0000`, approved amount = transaction amount. Declined: response code `05`, approved amount `0`. | COPAUA0C:685-698 |
| FR-PA-A08 | The reply message is `card,tranid,authid,resp,reason,amount,` in `CCPAURLY` order, auth id code = request auth time, amount edited `-zzzzzzzzz9.99`, with a trailing comma (quirk Q-9, preserved). | COPAUA0C:660-731 |
| FR-PA-A09 | The reply is sent to the reply queue named by the request's reply-to header, defaulting to the configured reply queue. | COPAUA0C:413-414, 741-742 |
| FR-PA-A10 | When the card resolved, an authorization summary is created (`ISRT`) or updated (`REPL`); credit and cash limits are always refreshed from the account record. | COPAUA0C:801-834 |
| FR-PA-A11 | Approved authorizations increment the approved count, add the approved amount to approved amount and credit balance, and set cash balance to zero. | COPAUA0C:813-818 |
| FR-PA-A12 | Declined authorizations increment the declined count and add the *detail work area's* transaction amount — the previous message's amount, zero for the first message of a run (quirk Q-6, preserved). | COPAUA0C:819-822 |
| FR-PA-A13 | A detail segment is inserted with the inverted key derived from the current timestamp, the 18 request fields, the reply's auth id/response/reason/approved amount, match status `P` when approved and `D` when declined, and blank fraud flag and fraud date. | COPAUA0C:857-919 |
| FR-PA-A14 | No summary or detail is written when the card did not resolve; the decline reply is still sent. | COPAUA0C:461-465 |
| FR-PA-A15 | IMS/MQ failures log a `CCPAUERY` error record with severity, subsystem, codes, message and event key; the messages are exactly `IMS SCHD FAILED`, `IMS GET SUMMARY FAILED`, `IMS UPDATE SUMRY FAILED`, `IMS INSERT DETL FAILED`, `REQ MQ OPEN ERROR`, `FAILED TO READ REQUEST MQ`, `FAILED TO PUT ON REPLY MQ`, `FAILED TO CLOSE REQUEST MQ`, `FAILED TO READ XREF FILE`, `FAILED TO READ ACCT FILE`, `FAILED TO READ CUST FILE`. | COPAUA0C:280, 315, 426, 509, 557, 605, 637, 775, 844, 929, 974 |
| FR-PA-A16 | A driver run stops after 501 messages (quirk Q-4, preserved) or when the request queue is empty. | COPAUA0C:326-344 |

## Batch (CBPAUP0C, PAUDBLOD, PAUDBUNL, DBUNLDGS)

| Id | Requirement | Source |
|---|---|---|
| FR-PA-B01 | The purge job reads its parameters as `expiry-days,chkp-freq,chkp-dis-freq,debug-flag`; defaults are 5, 5, 10, `N`. | CBPAUP0C:196-214 |
| FR-PA-B02 | An authorization is expired when `today_yyddd - (99999 - PA-AUTH-DATE-9C) >= expiry days`. | CBPAUP0C:282-284 |
| FR-PA-B03 | Deleting an approved authorization (`resp code 00`) decrements the approved count and subtracts the approved amount; deleting any other decrements the declined count and subtracts the transaction amount. | CBPAUP0C:285-295 |
| FR-PA-B04 | Expired authorizations are deleted from the authorization detail. | CBPAUP0C:5000-DELETE-AUTH-DTL |
| FR-PA-B05 | A summary is deleted when its approved count is `<= 0` — the source tests the approved count twice and ignores the declined count (quirk Q-7, preserved). | CBPAUP0C:156 |
| FR-PA-B06 | The purge job reports the number of summaries read, details read and rows deleted, and ends with a non-zero code on an unexpected database status. | CBPAUP0C:9999-ABEND |
| FR-PA-B07 | The load utility loads summary records then detail records; a detail whose parent key is not numeric is skipped, and an already-present segment is tolerated. | PAUDBLOD:255-266, 273-283 |
| FR-PA-B08 | A load failure other than "already in DB" ends the job with return code 16. | PAUDBLOD:330-333 |
| FR-PA-B09 | The unload utility writes every summary to the summary file and its details to the detail file, skipping summaries with a non-numeric account id. | PAUDBUNL:141-172 |
| FR-PA-B10 | The GSAM unload writes the same two record streams through the GSAM output PCBs. | DBUNLDGS:179-199 |

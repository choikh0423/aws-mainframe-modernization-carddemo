# COPAUS1C (CPVD) — Functional Requirement

Source: `app/app-authorization-ims-db2-mq/cbl/COPAUS1C.cbl`, map `bms/COPAU01.bms` (`COPAU1A`).
Migrated to `com.carddemo.pendingauth` (`PendingAuthDetailService`, `PendingAuthorizationController`)
and `frontend/src/pages/pendingauth/PendingAuthDetailPage.js`
(route `/pending-authorizations/detail`).

## Purpose
Show one pending authorization in full, allow the user to mark or remove a fraud flag (F5) and to
walk forward through the account's authorizations (F8).

## Entry conditions
Requires `CDEMO-ACCT-ID` numeric and `CDEMO-CPVD-PAU-SELECTED` (authorization key) non-blank. With no
commarea the program returns to CPVS. When the two inputs are not satisfied the screen is rendered
empty **without a message** (quirk Q-2).

## Screen (verbatim labels)
`View Authorization Details`, `Card #:`, `Auth Date:`, `Auth Time:`, `Auth Resp:`, `Resp Reason:`,
`Auth Code:`, `Amount:`, `POS Entry Mode:`, `Source   :`, `MCC Code:`, `Card Exp. Date:`,
`Auth Type:`, `Tran Id:`, `Match Status:`, `Fraud Status:`,
`Merchant Details -------------------------------`, `Name:`, `Merchant ID:`, `City:`, `State:`,
`Zip:`, ` F3=Back  F5=Mark/Remove Fraud  F8=Next Auth`.

## Requirements
* **FR-PA-D01** field mapping and labels.
* **FR-PA-D02/D03** decline-reason table and `9999-ERROR` fallback.
* **FR-PA-D04** `Auth Code:` = processing code (quirk Q-3).
* **FR-PA-D05** fraud status rendering `F-MM/DD/YY` / `R-MM/DD/YY` / `-`.
* **FR-PA-D06** amount = approved amount (quirk Q-1).
* **FR-PA-D07** empty screen, no message, when the inputs are missing (quirk Q-2).
* **FR-PA-D08/D09/D10/D11** fraud toggle, delegation and rollback.
* **FR-PA-D12** F8 next authorization and end-of-chain message.
* **FR-PA-D13/D14** F3 and invalid key.

## Decline-reason table (COPAUS1C:60-73)
```
0000-APPROVED         3100-INVALID CARD     4100-INSUFFICNT FUND
4200-CARD NOT ACTIVE  4300-ACCOUNT CLOSED   4400-EXCED DAILY LMT
5100-CARD FRAUD       5200-MERCHANT FRAUD   5300-LOST CARD        9000-UNKNOWN
```

## Messages (verbatim)
```
AUTH MARKED FRAUD...
AUTH FRAUD REMOVED...
Already at the last Authorization...
Invalid key pressed. Please see below...
 System error while reading Auth Summary: Code:<status>
 System error while reading Auth Details: Code:<status>
 System error while reading next Auth: Code:<status>
 System error while FRAUD Tagging, ROLLBACK||<status>
```
Failure messages produced by COPAUS2C (`ADD SUCCESS`, `UPDT SUCCESS`,
` SYSTEM ERROR DB2: CODE:…`, ` UPDT ERROR DB2: CODE:…`) are surfaced unchanged when the fraud
update fails.

## Control flow
`EXEC CICS LINK PROGRAM(WS-PGM-AUTH-FRAUD)`, `WS-PGM-AUTH-FRAUD VALUE 'COPAUS2C'` (COPAUS1C:35) —
the symbolic target is **COPAUS2C**, migrated as a call from `PendingAuthDetailService` into
`AuthFraudService`. F3 → `COPAUS0C`.

## Migration notes
The legacy program performs the DB2 write first and the IMS update second, committing both with one
CICS syncpoint and rolling both back on failure. The migrated fraud toggle runs in a single
`@Transactional` method covering the `auth_fraud` upsert and the `pending_auth_detail` update, which
reproduces the all-or-nothing behaviour.

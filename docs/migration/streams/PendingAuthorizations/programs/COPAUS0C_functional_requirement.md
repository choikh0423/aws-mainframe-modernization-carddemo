# COPAUS0C (CPVS) — Functional Requirement

Source: `app/app-authorization-ims-db2-mq/cbl/COPAUS0C.cbl`, map `bms/COPAU00.bms` (`COPAU0A`).
Migrated to `com.carddemo.pendingauth` (`PendingAuthSummaryService`,
`PendingAuthorizationController`) and `frontend/src/pages/pendingauth/PendingAuthListPage.js`
(route `/pending-authorizations`).

## Purpose
Show the pending-authorization position of one account and browse its authorizations five at a time,
allowing one of them to be selected for the detail screen.

## Entry conditions
| Case | Behaviour |
|---|---|
| No commarea (`EIBCALEN = 0`) | Initialise commarea, blank screen, cursor on `Search Acct Id`. |
| Entered from another program | If `CDEMO-ACCT-ID` is numeric, load that account immediately; otherwise blank the field. |
| Re-entry | Receive the map and dispatch on the AID key. |

## Screen (verbatim labels)
`Tran:`, `Date:`, `Prog:`, `Time:`, title `View Authorizations`, `Search Acct Id:`, `Name: `,
`Customer Id: `, `Acct Status: `, `PH:`, `Approval # : `, `Decline #:`, `Credit Lim:`, `Cash Lim:`,
`Appr Amt:`, `Credit Bal:`, `Cash Bal:`, `Decl Amt:`; column headings `Sel`, ` Transaction ID `,
`  Date  `, `  Time  `, `Type `, `A/D`, `STS`, `   Amount   `;
`Type 'S' to View Authorization details from the list`;
`ENTER=Continue  F3=Back  F7=Backward  F8=Forward`.

## Requirements
* **FR-PA-S01** empty first screen, no message.
* **FR-PA-S02** blank account id → `Please enter Acct Id...`.
* **FR-PA-S03** non-numeric account id → `Acct Id must be Numeric ...`.
* **FR-PA-S04/S05/S06** header population from account, customer and summary; zeros when no summary.
* **FR-PA-S07/S08/S09** five rows per page, `A`/`D` flag, approved amount in the amount column.
* **FR-PA-S10/S11** F8/F7 paging and the two boundary messages.
* **FR-PA-S12/S13/S14** selection semantics.
* **FR-PA-S15/S16** invalid key and F3.
* **FR-PA-S17** cross-reference / master read failures.

## Messages (verbatim)
```
Please enter Acct Id...
Acct Id must be Numeric ...
Invalid selection. Valid value is S
You are already at the top of the page...
You are already at the bottom of the page...
Invalid key pressed. Please see below...
 System error while reading AUTH Details: Code:<status>
 System error while repos. AUTH Details: Code:<status>
```

## Data access
`CXACAIX`/`CARDXREF` (by account), `ACCTDAT`, `CUSTDAT`, IMS `GU PAUTSUM0 WHERE ACCNTID`,
`GNP PAUTDTL1` (page scan) and `GNP PAUTDTL1 WHERE PAUT9CTS = key` (reposition).

## Control flow
`XCTL COPAUS1C` on a valid `S` selection; `XCTL COMEN01C` (`WS-PGM-MENU`) on F3; otherwise
`RETURN TRANSID('CPVS')`.

## Migration notes
Paging is stateless: the REST API takes `startKey` (the inverted key of the first row of the page)
and returns `prevPageKeys` plus `nextPage`, which the React page keeps in component state exactly as
the legacy commarea kept `CDEMO-CPVS-PAUKEY-PREV-PG` / `CDEMO-CPVS-PAUKEY-LAST`.

# Tesco | CICS Transaction: `CAVW` (Account View)

> **Tesco | Mainframe Application Documentation**
> Application: **CardDemo** &nbsp;|&nbsp; Module: **Base application** &nbsp;|&nbsp; Classification: **Tesco Internal**
> Document owner: TODO: owning team &nbsp;|&nbsp; Last reviewed: 2026-11-13

Generated from [`docs/templates/TRANSACTION_DOC_TEMPLATE.md`](../templates/TRANSACTION_DOC_TEMPLATE.md). Back to the [transaction index](README.md).

---

## 1. Transaction ID

| Attribute | Value |
|:--|:--|
| Transaction ID | `CAVW` |
| CSD group | `CARDDEMO` |
| CSD definition | `app/csd/CARDDEMO.CSD` |
| CSD description | (none in CSD) |
| Profile / Tran class | `DFHCICST` / `DFHTCL00` |
| Other CSD attributes | `TASKDATALOC(ANY)`, `TASKDATAKEY(USER)`, `ISOLATE(YES)`, `RESSEC(NO)`, `CMDSEC(NO)` |
| README function | Account View |

## 2. Program name

| Attribute | Value |
|:--|:--|
| Initial program | `COACTVWC` |
| Source | [`app/cbl/COACTVWC.cbl`](../../app/cbl/COACTVWC.cbl) |
| Program type | CICS COBOL |
| Program header "Function" | Accept and process Account View request |

## 3. Mapset / BMS map

| Attribute | Value |
|:--|:--|
| Mapset | `COACTVW` ([`app/bms/COACTVW.bms`](../../app/bms/COACTVW.bms)) |
| Map | `CACTVWA` |
| Symbolic map copybook | [`app/cpy-bms/COACTVW.CPY`](../../app/cpy-bms/COACTVW.CPY) |

## 4. Purpose / Description

Read-only view of an account and its customer. The user enters an 11-digit account number; the program finds the card cross-reference via the account alternate index (`CXACAIX`), then reads the account (`ACCTDAT`) and customer (`CUSTDAT`) records and shows them on one screen.

## 5. Invocation

| Path | Detail |
|:--|:--|
| Menu path | `CC00` → `CM00` option 1 "Account View". |
| Direct tranid | With no COMMAREA the program starts a fresh COMMAREA and shows its screen. TODO: confirm the user/role context is set correctly when entered directly instead of through the menu. |
| Pseudo-conversational re-entry | `EXEC CICS RETURN TRANSID(CAVW) COMMAREA(...)` |

## 6. Inputs

### 6.1 COMMAREA

Shared CardDemo COMMAREA `CARDDEMO-COMMAREA` ([`app/cpy/COCOM01Y.cpy`](../../app/cpy/COCOM01Y.cpy)). Fields referenced by this program:

| Field | Picture | Usage |
|:--|:--|:--|
| `CDEMO-ACCT-ID` | `9(11)` | Account in context |
| `CDEMO-CARD-NUM` | `9(16)` | Card in context |
| `CDEMO-CUST-ID` | `9(09)` | Customer in context |
| `CDEMO-FROM-PROGRAM` | `X(08)` | Program of the calling screen (PF3 target) |
| `CDEMO-FROM-TRANID` | `X(04)` | Tranid of the calling screen |
| `CDEMO-LAST-MAP` | `X(7)` | Last map sent |
| `CDEMO-LAST-MAPSET` | `X(7)` | Last mapset sent |
| `CDEMO-TO-PROGRAM` | `X(08)` | Program to XCTL to |
| `CDEMO-TO-TRANID` | `X(04)` | Tranid to transfer to |

The program appends its own working fields after the shared area (paging keys, selection flags). See the program source for the layout.

### 6.2 Screen fields (unprotected)

| BMS field | Length | Label (same screen row) |
|:--|:--|:--|
| `ACCTSID` | 11 | Account Number |

### 6.3 Function keys

| Key | Action |
|:--|:--|
| ENTER | Validate account number and display account + customer |
| PF3 | Exit to the calling program / Main Menu |

## 7. Outputs

- **Screen:** `ACSTTUS`, `ADTOPEN`, `ACRDLIM`, `AEXPDT`, `ACSHLIM`, `AREISDT`, `ACURBAL`, `ACRCYCR`, `AADDGRP`, `ACRCYDB`, `ACSTNUM`, `ACSTSSN`, `ACSTDOB`, `ACSTFCO`, `ACSFNAM`, `ACSMNAM`, `ACSLNAM`, `ACSADL1`, `ACSSTTE`, `ACSADL2`, `ACSZIPC`, `ACSCITY`, `ACSCTRY`, `ACSPHN1`, `ACSGOVT`, `ACSPHN2`, `ACSEFTC`, `ACSPFLG`, `INFOMSG`, `ERRMSG` (plus the standard header: `TITLE01`, `TITLE02`, `TRNNAME`, `PGMNAME`, `CURDATE`, `CURTIME`).
- **Messages** (literals found in the program):
  - "UNEXPECTED DATA SCENARIO"
  - "Account Filter must be a non-zero 11 digit number"
  - "Enter or update id of account to display"
  - "Displaying details of given Account"
  - "PF03 pressed.Exiting"
  - "Account number not provided"
  - "No input received"
  - "Account number must be a non zero 11 digit number"
  - "Did not find this account in account card xref file"
  - "Did not find this account in account master file"
  - ... 3 more in the source.
- **Records written / messages sent:** None (read-only).

## 8. Data stores accessed

| Type | Resource | Physical name / definition | Record layout | Access |
|:--|:--|:--|:--|:--|
| VSAM | `ACCTDAT` | `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | `CVACT01Y` (key: Account ID) | `READ` |
| VSAM | `CUSTDAT` | `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` | `CVCUS01Y` (key: Customer ID) | `READ` |
| VSAM | `CXACAIX` | `AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH` | `CVACT03Y` (key: Account ID (alternate index path over CCXREF)) | `READ` |

No DB2, IMS, MQ access found in `COACTVWC`.

## 9. Called / linked programs (XCTL / LINK flow)

| Direction | Mechanism | Program (Tranid) | Condition |
|:--|:--|:--|:--|
| Outbound | XCTL | [`COMEN01C`](CM00.md) (`CM00`) | PF3 (exit): back to the Main Menu (or to `CDEMO-FROM-PROGRAM` when it was set by the caller) |
| Inbound | XCTL | [`COMEN01C`](CM00.md) (`CM00`) | Menu selection |

```mermaid
flowchart LR
    N0["COACTVWC / CAVW"]
    N0 -->|XCTL| N1["COMEN01C / CM00"]
    N1["COMEN01C / CM00"] -->|XCTL| N0
```

## 10. Error handling

| Condition | Detection | Behaviour / message |
|:--|:--|:--|
| NOTFND | `DFHRESP(NOTFND)` | Record not found: message shown, key field highlighted |
| Any other CICS response | `RESP` / `RESP2` not handled above | Generic "unable to ..." message with the response code |
| Unexpected abend | `EXEC CICS HANDLE ABEND LABEL(ABEND-ROUTINE)` | Abend details sent to the terminal; then `EXEC CICS ABEND ABCODE(9999)` |
| Invalid key pressed | AID not in the list in 6.3 | Invalid-key message, screen re-sent |

## 11. Security / authorization

| Control | Detail |
|:--|:--|
| Sign-on | User must have signed on through `CC00` (`USRSEC`); identity travels in `CDEMO-USER-ID` / `CDEMO-USER-TYPE`. |
| Role / function | Shows customer PII (name, address, phone, SSN, FICO score, date of birth). TODO: confirm masking rules for SSN on screen. |
| CICS resource security | CSD `RESSEC(NO)`, `CMDSEC(NO)`: no CICS resource or command checks. |
| External security (RACF / ACF2 / Top Secret) | TODO: transaction profile for `CAVW` (TCICSTRN / GCICSTRN) owned by the Tesco security team. |

## 12. Change history

| Date | Version / commit | Author | Change |
|:--|:--|:--|:--|
| 2026-11-13 | - | Devin | Initial document generated from template and source inspection. |
| 2022-09-01 | `8c797e2` | Abhijit Kshirsagar | Last repository change to `COACTVWC.cbl`: Release 1.0 |
| - | CSD | AWSUSER | CSD `DEFINETIME(22/05/13 12:56:05)`, `CHANGETIME(22/05/13 12:56:05)` |

## Open items (TODO)

- [ ] TODO: confirm external security (RACF) definitions for `CAVW`.
- [ ] TODO: assign a document owner.

---
*Tesco | Internal use only. Generated from `docs/templates/TRANSACTION_DOC_TEMPLATE.md`.*

# Tesco | CICS Transaction: `CDRD` (System Date Inquiry via MQ)

> **Tesco | Mainframe Application Documentation**
> Application: **CardDemo** &nbsp;|&nbsp; Module: **MQ Integration** &nbsp;|&nbsp; Classification: **Tesco Internal**
> Document owner: TODO: owning team &nbsp;|&nbsp; Last reviewed: 2026-11-13

Generated from [`docs/templates/TRANSACTION_DOC_TEMPLATE.md`](../templates/TRANSACTION_DOC_TEMPLATE.md). Back to the [transaction index](README.md).

---

## 1. Transaction ID

| Attribute | Value |
|:--|:--|
| Transaction ID | `CDRD` |
| CSD group | `CARDDEMO` |
| CSD definition | `app/app-vsam-mq/csd/CRDDEMOM.csd` |
| CSD description | (none in CSD) |
| Profile / Tran class | `DFHCICST` / `DFHTCL00` |
| Other CSD attributes | `TASKDATALOC(ANY)`, `TASKDATAKEY(USER)`, `ISOLATE(YES)`, `RESSEC(NO)`, `CMDSEC(NO)` |
| README function | Inquire System Date via MQ (Demonstrates MQ request/response pattern) |

## 2. Program name

| Attribute | Value |
|:--|:--|
| Initial program | `CODATE01` |
| Source | [`app/app-vsam-mq/cbl/CODATE01.cbl`](../../app/app-vsam-mq/cbl/CODATE01.cbl) |
| Program type | CICS COBOL + IBM MQ |
| Program header "Function" | TODO: no header "Function" line in source |

## 3. Mapset / BMS map

None. This transaction has no 3270 screen (it is driven by MQ).

## 4. Purpose / Description

Background MQ request / response service that returns the current CICS system date and time. Started by the MQ trigger monitor, it reads a request, formats `ASKTIME` / `FORMATTIME` output and puts the reply on `CARD.DEMO.REPLY.DATE`. Errors go to `CARD.DEMO.ERROR`.

## 5. Invocation

| Path | Detail |
|:--|:--|
| Menu path | None (no screen). |
| Direct tranid | Not intended. Started by the CICS-MQ trigger monitor; the program `RETRIEVE`s the trigger message (`MQTM`) to get the input queue name. |
| Other triggers | CICS-MQ trigger monitor (`CKTI`) starts the transaction; `EXEC CICS RETRIEVE` reads the `MQTM` trigger message. |

## 6. Inputs

### 6.1 COMMAREA

None. The program `RETRIEVE`s the MQ trigger message (`MQTM`) instead of receiving a COMMAREA.

### 6.2 Screen fields (unprotected)

None. Input is the MQ date request message. See [`app/app-vsam-mq/README.md`](../../app/app-vsam-mq/README.md) for the message formats.

### 6.3 Function keys

Not applicable (no terminal).

## 7. Outputs

- **Screen:** none.
- **Records written / messages sent:** Date / time reply on `CARD.DEMO.REPLY.DATE`; errors on `CARD.DEMO.ERROR`.

## 8. Data stores accessed

| Type | Resource | Physical name / definition | Record layout | Access |
|:--|:--|:--|:--|:--|
| MQ | Input queue: from trigger message `MQTM-QNAME` | Module README example: `CARDDEMO.REQUEST.QUEUE` (CICS MQQUEUE `CARDREQ`). TODO: confirm real name | Date request | `MQOPEN`, `MQGET`, `MQCLOSE` |
| MQ | `CARD.DEMO.REPLY.DATE` | Hard-coded in `CODATE01` | Date / time reply | `MQOPEN`, `MQPUT`, `MQCLOSE` |
| MQ | `CARD.DEMO.ERROR` | Hard-coded in `CODATE01` | Error message text | `MQOPEN`, `MQPUT`, `MQCLOSE` |

No DB2, IMS access found in `CODATE01`.

## 9. Called / linked programs (XCTL / LINK flow)

| Direction | Mechanism | Program (Tranid) | Condition |
|:--|:--|:--|:--|
| Inbound | START (trigger) | CICS-MQ trigger monitor `CKTI` | Message arrives on the initiation queue |

```mermaid
flowchart LR
    N0["CODATE01 / CDRD"]
    N1["CKTI"] -->|START (trigger)| N0
```

## 10. Error handling

| Condition | Detection | Behaviour / message |
|:--|:--|:--|
| Any other CICS response | `RESP` / `RESP2` not handled above | Generic "unable to ..." message with the response code |
| MQ call failure | MQ completion / reason code | Message put on `CARD.DEMO.ERROR`; TODO: confirm abend behaviour |

## 11. Security / authorization

| Control | Detail |
|:--|:--|
| Role / function | Runs under the trigger monitor user ID. Returns no customer data. TODO: confirm MQ queue security. |
| CICS resource security | CSD `RESSEC(NO)`, `CMDSEC(NO)`: no CICS resource or command checks. |
| External security (RACF / ACF2 / Top Secret) | TODO: transaction profile for `CDRD` (TCICSTRN / GCICSTRN) owned by the Tesco security team. |

## 12. Change history

| Date | Version / commit | Author | Change |
|:--|:--|:--|:--|
| 2026-11-13 | - | Devin | Initial document generated from template and source inspection. |
| 2025-08-03 | `fb78417` | Arunkumar Selvam | Last repository change to `CODATE01.cbl`: Add optional modules and enhanced functionality |
| - | CSD | GILBION | CSD `DEFINETIME(23/03/23 15:10:46)`, `CHANGETIME(23/03/23 15:10:46)` |

## Open items (TODO)

- [ ] TODO: confirm external security (RACF) definitions for `CDRD`.
- [ ] TODO: assign a document owner.

---
*Tesco | Internal use only. Generated from `docs/templates/TRANSACTION_DOC_TEMPLATE.md`.*

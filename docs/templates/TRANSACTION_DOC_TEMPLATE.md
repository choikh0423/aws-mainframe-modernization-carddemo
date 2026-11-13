<!--
  Tesco: CICS transaction documentation template.
  Copy this file to docs/transactions/<TRANID>.md and replace every {{placeholder}}.
  Anything not yet confirmed from source stays as a "TODO:" line so a reviewer can find it
  with `grep -rn "TODO" docs/transactions`.
-->

# Tesco | CICS Transaction: `{{TRANID}}` ({{Short title}})

> **Tesco | Mainframe Application Documentation**
> Application: **CardDemo** &nbsp;|&nbsp; Module: **{{Base application / optional module name}}** &nbsp;|&nbsp; Classification: **Tesco Internal**
> Document owner: {{Team / owner}} &nbsp;|&nbsp; Last reviewed: {{YYYY-MM-DD}}

---

## 1. Transaction ID

| Attribute | Value |
|:--|:--|
| Transaction ID | `{{TRANID}}` |
| CSD group | `{{GROUP}}` |
| CSD definition | `{{path/to/file.csd}}` |
| CSD description | {{DESCRIPTION(...) from the CSD, or "(none)"}} |
| Profile / Tran class | `{{PROFILE}}` / `{{TRANCLASS}}` |
| Other CSD attributes | {{e.g. TASKDATALOC, ISOLATE, RESSEC, CMDSEC}} |

## 2. Program name

| Attribute | Value |
|:--|:--|
| Initial program | `{{PROGRAM}}` |
| Source | `{{app/.../cbl/PROGRAM.cbl}}` |
| Program type | {{CICS COBOL / CICS COBOL + DB2 / IMS / MQ}} |
| Program header "Function" | {{quote from the program header comment}} |

## 3. Mapset / BMS map

| Attribute | Value |
|:--|:--|
| Mapset | `{{MAPSET}}` (`{{app/.../bms/MAPSET.bms}}`) |
| Map | `{{MAP}}` |
| Symbolic map copybook | `{{app/.../cpy-bms/MAPSET.CPY}}` |

{{For non-terminal (MQ-triggered / background) transactions write "None. This transaction has no 3270 screen."}}

## 4. Purpose / Description

{{Two to five sentences: what business function the transaction performs, who uses it, and its
outcome. Use the README application-inventory "Function" text as the starting point.}}

## 5. Invocation

| Path | Detail |
|:--|:--|
| Menu path | {{e.g. CC00 sign-on → CM00 Main Menu → option N "..."}} |
| Direct tranid | {{Can the tranid be typed on a clear screen? What happens without a COMMAREA?}} |
| Pseudo-conversational re-entry | {{EXEC CICS RETURN TRANSID(...) COMMAREA(...)}} |
| Other triggers | {{MQ trigger monitor / START / XCTL from another program}} |

## 6. Inputs

### 6.1 COMMAREA

| Field | Picture | Usage |
|:--|:--|:--|
| {{CDEMO-...}} | {{PIC ...}} | {{how the program uses it}} |

### 6.2 Screen fields (unprotected)

| BMS field | Length | Meaning / validation |
|:--|:--|:--|
| {{FIELD}} | {{n}} | {{edit rules}} |

### 6.3 Function keys

| Key | Action |
|:--|:--|
| {{ENTER / PF3 / ...}} | {{action}} |

## 7. Outputs

- **Screen:** {{output fields on the map}}
- **Messages:** {{info / error message text shown in ERRMSG / INFOMSG}}
- **Records written / messages sent:** {{VSAM writes, DB2 rows, MQ replies, TD queue records}}

## 8. Data stores accessed

| Type | Resource | Physical name / definition | Record layout | Access |
|:--|:--|:--|:--|:--|
| VSAM | `{{FILE}}` | `{{DSNAME}}` | `{{copybook}}` | {{READ / STARTBR / READNEXT / WRITE / REWRITE / DELETE}} |
| DB2 | `{{SCHEMA.TABLE}}` | `{{ddl file}}` | `{{DCLGEN}}` | {{SELECT / INSERT / UPDATE / DELETE / cursor}} |
| IMS | `{{SEGMENT}}` | DBD `{{DBD}}`, PSB `{{PSB}}` | `{{copybook}}` | {{GU / GNP / ISRT / REPL}} |
| MQ | `{{QUEUE}}` | {{queue manager / CSD MQQUEUE}} | `{{message layout}}` | {{MQGET / MQPUT / MQPUT1}} |
| CICS TS/TD | `{{QUEUE}}` | {{TDQ/TSQ}} | {{record}} | {{WRITEQ / READQ}} |

## 9. Called / linked programs (XCTL / LINK flow)

| Direction | Mechanism | Program (Tranid) | Condition |
|:--|:--|:--|:--|
| Outbound | {{XCTL / LINK / CALL}} | `{{PROGRAM}}` (`{{TRANID}}`) | {{key / selection / condition}} |
| Inbound | XCTL | `{{PROGRAM}}` (`{{TRANID}}`) | {{from which screen}} |

```mermaid
flowchart LR
    A[{{caller}}] -->|XCTL| B[{{this program}}]
    B -->|XCTL| C[{{target}}]
```

## 10. Error handling

| Condition | Detection | Behaviour / message |
|:--|:--|:--|
| {{e.g. record not found}} | {{RESP = DFHRESP(NOTFND)}} | {{message shown, cursor positioned}} |
| {{Abend}} | {{EXEC CICS HANDLE ABEND / ABEND ABCODE}} | {{abend code / routine}} |

## 11. Security / authorization

| Control | Detail |
|:--|:--|
| Sign-on | {{CC00 / USRSEC verification}} |
| Role check | {{CDEMO-USER-TYPE 'A' admin / 'U' user checks}} |
| CICS resource security | {{RESSEC / CMDSEC from the CSD}} |
| External security (RACF / ACF2 / Top Secret) | TODO: {{transaction profile / surrogate rules owned by the security team}} |
| Sensitive data | {{PAN, SSN, passwords, PII shown or stored}} |

## 12. Change history

| Date | Version / commit | Author | Change |
|:--|:--|:--|:--|
| {{YYYY-MM-DD}} | {{commit / release}} | {{author}} | {{description}} |

---
*Tesco | Internal use only. Generated from `docs/templates/TRANSACTION_DOC_TEMPLATE.md`.*

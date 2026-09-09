# COACCT01 (CDRA) — functional requirement

MQ-triggered CICS server that answers account inquiries from the ACCTDAT VSAM KSDS.
Source: `app/app-vsam-mq/cbl/COACCT01.cbl`. CSD: `app/app-vsam-mq/csd/CRDDEMOM.csd`
(`PROGRAM(COACCT01) TRANSID(CDRA)`). No BMS map, no COMMAREA, no `XCTL`/`LINK`.

## Interface

**Request** — 1000 bytes (`COACCT01.cbl:109-112`):

```
offset 0    WS-FUNC    PIC X(04)     'INQA' to be served
offset 4    WS-KEY     PIC 9(11)     account id, zero-padded
offset 15   WS-FILLER  PIC X(985)    ignored
```

**Reply** — 1000 bytes, put on `CARD.DEMO.REPLY.ACCT` (`COACCT01.cbl:198`, `:462-486`).

**Error report** — 1000 bytes, put on `CARD.DEMO.ERROR` (`COACCT01.cbl:294`, `:501-523`).

## Requirements

| ID | Requirement | Source |
| --- | --- | --- |
| FR-COACCT01-01 | The program serves a request only when `WS-FUNC` = `INQA` (exact, case-sensitive) and `WS-KEY` > 0. | `:393` |
| FR-COACCT01-02 | A served request reads `ACCTDAT` randomly by the 11-digit account id, with `KEYLENGTH(11)` and the record layout `CVACT01Y ACCOUNT-RECORD`. | `:394-404`, `:115-116`, `:171` |
| FR-COACCT01-03 | On a successful read the reply is the labelled 252-byte block `WS-ACCT-RESPONSE`, space-padded to 1000. Field order, labels and PICs: see the table below. | `:130-169`, `:407-426` |
| FR-COACCT01-04 | Amount fields are written as `S9(10)V99` zoned decimal: 12 bytes, no decimal point, sign as an overpunch on the last digit. `1940.00` → `00000019400{`, `-25.00` → `00000000250}`. | `:140-166` |
| FR-COACCT01-05 | `WS-ACCT-ID` is the account id zero-padded to 11 digits; the three date fields are the account record's `X(10)` values verbatim (`YYYY-MM-DD` in the seeded data); `WS-ACCT-GROUP-ID` is the `X(10)` group id, space-padded. | `:134`, `:152-169` |
| FR-COACCT01-06 | `ACCT-ADDR-ZIP` is not part of the reply even though it is part of the record. | `:130-169` vs `app/cpy/CVACT01Y.cpy` |
| FR-COACCT01-07 | When the read returns NOTFND the reply is `INVALID REQUEST PARAMETERS ACCT ID : nnnnnnnnnnn` (48 bytes) and is sent to the same reply queue. | `:428-435` |
| FR-COACCT01-08 | When the read fails otherwise, nothing is replied: an error report carrying return message `ERROR WHILE READING ACCTF`, the RESP as a 2-digit condition code, the RESP2 as a 5-digit reason code and the request queue name is put on `CARD.DEMO.ERROR`, and the task ends. | `:437-445`, `:501-536` |
| FR-COACCT01-09 | When the request fails the FR-COACCT01-01 test the reply is `INVALID REQUEST PARAMETERS ACCT ID : nnnnnnnnnnnFUNCTION : ffff` (63 bytes) — no separator between the key and the `FUNCTION : ` label. | `:448-456` |
| FR-COACCT01-10 | The reply carries the request's message id and correlation id, and always goes to `CARD.DEMO.REPLY.ACCT`; the request's reply-to queue is saved and never used. | `:366`, `:371`, `:469-470` |
| FR-COACCT01-11 | One syncpoint per message: the read and the reply of a single request are one unit of work. | `:325-331`, `:347-350`, `:475-477` |
| FR-COACCT01-12 | Messages are consumed from the triggered input queue one at a time, oldest first, with no selection by message or correlation id. | `:334-350` |

### Reply layout (offsets are zero-based)

| Offset | Len | Content |
| --- | --- | --- |
| 0 | 13 | `ACCOUNT ID : ` |
| 13 | 11 | account id `9(11)` |
| 24 | 17 | `ACCOUNT STATUS : ` |
| 41 | 1 | active status `X(01)` |
| 42 | 10 | `BALANCE : ` |
| 52 | 12 | current balance `S9(10)V99` |
| 64 | 15 | `CREDIT LIMIT : ` |
| 79 | 12 | credit limit `S9(10)V99` |
| 91 | 13 | `CASH LIMIT : ` |
| 104 | 12 | cash credit limit `S9(10)V99` |
| 116 | 12 | `OPEN DATE : ` |
| 128 | 10 | open date `X(10)` |
| 138 | 12 | `EXPR DATE : ` |
| 150 | 10 | expiration date `X(10)` |
| 160 | 12 | `REIS DATE : ` |
| 172 | 10 | reissue date `X(10)` |
| 182 | 13 | `CREDIT BAL : ` |
| 195 | 12 | current cycle credit `S9(10)V99` |
| 207 | 12 | `DEBIT BAL : ` |
| 219 | 12 | current cycle debit `S9(10)V99` |
| 231 | 11 | `GROUP ID : ` |
| 242 | 10 | group id `X(10)` |

## Quirks preserved

| Quirk | Source | Migrated behaviour |
| --- | --- | --- |
| The reply-to queue in the request message descriptor is read and discarded. | `:371` | Replies always go to `CARD.DEMO.REPLY.ACCT`. |
| `'ERROR WHILE READING ACCTFILE'` (28 bytes) is moved to a `X(25)` field. | `:442-443` | The error report carries the truncated `ERROR WHILE READING ACCTF`. |
| The condition code field is `9(02)`, so codes above 99 wrap. | `:63`, `:439` | The report keeps the low-order two digits. |
| No space separates the key from `FUNCTION : ` in the invalid-request reply. | `:449-451` | Reproduced byte for byte. |
| `WS-DATE-TIME` is initialised on every request and never used. | `:392` | Not carried over (no observable behaviour). |
| `WS-KEY` is a `9(11)` field filled by a group move, so a non-numeric key is compared as a number. | `:373`, `:393` | Treated as not greater than zero → invalid-request reply, key reported as 11 zeros. |

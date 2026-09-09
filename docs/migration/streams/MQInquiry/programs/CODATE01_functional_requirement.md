# CODATE01 (CDRD) — functional requirement

MQ-triggered CICS server that answers every request on its triggered queue with the system date and time.
Source: `app/app-vsam-mq/cbl/CODATE01.cbl`. CSD: `app/app-vsam-mq/csd/CRDDEMOM.csd`
(`PROGRAM(CODATE01) TRANSID(CDRD)`). No BMS map, no COMMAREA, no file access, no `XCTL`/`LINK`.

## Interface

**Request** — 1000 bytes, parsed into the same `REQUEST-MSG-COPY` layout as COACCT01
(`CODATE01.cbl:109-112`) and then never examined.

**Reply** — 1000 bytes, put on `CARD.DEMO.REPLY.DATE` (`CODATE01.cbl:147`, `:366-390`).

**Error report** — 1000 bytes, put on `CARD.DEMO.ERROR` (`CODATE01.cbl:243`, `:405-427`).

## Requirements

| ID | Requirement | Source |
| --- | --- | --- |
| FR-CODATE01-01 | Every message on the triggered queue is answered; no field of the request is validated or even read. | `:339-361` |
| FR-CODATE01-02 | The reply is `SYSTEM DATE : ` + the current date + `SYSTEM TIME : ` + the current time, 46 bytes, space-padded to 1000. | `:355-360` |
| FR-CODATE01-03 | The date is `MM-DD-YYYY` — `FORMATTIME MMDDYYYY` with `DATESEP('-')`. | `:347-350` |
| FR-CODATE01-04 | The time is `HH:MM:SS` on a 24-hour clock — `TIME(WS-TIME) TIMESEP` with the default `:` separator. | `:351-352` |
| FR-CODATE01-05 | Date and time come from a single clock reading (`ASKTIME ABSTIME` then one `FORMATTIME`), so they are always consistent with each other. | `:343-353` |
| FR-CODATE01-06 | The reply carries the request's message id and correlation id and always goes to `CARD.DEMO.REPLY.DATE`; the request's reply-to queue is saved and never used. | `:315`, `:320`, `:373-374` |
| FR-CODATE01-07 | One syncpoint per message: consuming a request and putting its reply are one unit of work. | `:274-280`, `:296-299` |
| FR-CODATE01-08 | Messages are consumed one at a time, oldest first, with no selection by message or correlation id. | `:283-299` |

## Quirks preserved

| Quirk | Source | Migrated behaviour |
| --- | --- | --- |
| The add-on README documents a `'DATE'` request type; the program never tests it. | `app/app-vsam-mq/README.md:99-112` vs `:339-361` | Any request content is answered with the date reply. |
| `REQUEST-MSG-COPY` is parsed and discarded. | `:322` | Parsed for symmetry with CDRA, not used for a decision. |
| The reply-to queue in the request message descriptor is read and discarded. | `:320` | Replies always go to `CARD.DEMO.REPLY.DATE`. |
| The date and time labels abut with no separator, so the reply reads `...MM-DD-YYYYSYSTEM TIME : ...`. | `:355-359` | Reproduced byte for byte. |

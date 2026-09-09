# COPAUA0C (CP00) — Functional Requirement (MQ authorization driver)

Source: `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl`, contracts `cpy/CCPAURQY.cpy`,
`cpy/CCPAURLY.cpy`, `cpy/CCPAUERY.cpy`.
Migrated to `com.carddemo.pendingauth.jms.AuthorizationRequestListener` +
`com.carddemo.pendingauth.service.AuthorizationDriverService`, queues
`AWS.M2.CARDDEMO.PAUTH.REQUEST` / `AWS.M2.CARDDEMO.PAUTH.REPLY`.

## Purpose
Triggered by the request queue, drain authorization requests, decide approve/decline, reply on the
reply queue and record the authorization in the pending-authorization database.

## Request (18 comma-delimited fields)
`auth date YYMMDD, auth time HHMMSS, card 16, auth type 4, card expiry YYMM, message type 6,
message source 6, processing code 6, amount +9(10).99, MCC 4, acquirer country 3, POS entry mode 2,
merchant id 15, merchant name 22, merchant city 13, merchant state 2, merchant zip 9,
transaction id 15`.

## Reply (6 comma-delimited fields, trailing comma)
`card, transaction id, auth id code (= request auth time), response code, response reason,
approved amount -zzzzzzzzz9.99,`

## Requirements
FR-PA-A01 … FR-PA-A16 (see the stream FR). Decision summary:

```
available = summary ? credit limit - credit balance
          : account ? account credit limit - account current balance
          : (decline)
amount > available          -> decline, reason 4100
card/account/customer miss  -> decline, reason 3100
otherwise                   -> approve, code 00, reason 0000, approved = requested
decline                     -> code 05, approved = 0
```

Reasons `4200 CARD NOT ACTIVE`, `4300 ACCOUNT CLOSED`, `5100 CARD FRAUD`, `5200 MERCHANT FRAUD` are
coded but unreachable (quirk Q-5) — the migrated driver keeps the same mapping table and the same
unreachable branches so a future rule change lands in one place.

## Persistence
* Summary: create with zeroed counters when absent; always refresh credit and cash limit from the
  account; approved → count +1, approved amount += approved, credit balance += approved, cash
  balance = 0; declined → count +1, declined amount += the detail work area's transaction amount
  (quirk Q-6).
* Detail: inverted key from the current timestamp, request fields, reply fields, match status `P`
  (approved) or `D` (declined), blank fraud flag/date.

## Error log (`CCPAUERY`)
Date, time, application `CP00`, program `COPAUA0C`, location code (`M001`, `M003`, `M004`, `M005`,
`I001`…`I004`, `A001`…`A003`, `C001`…`C003`), severity (`C` critical / `W` warning), subsystem
(`MQ`, `IMS`, `CICS`, `APP`), two codes, message and event key. Critical errors terminate the run.

## Migration notes
The CICS task loop (`MQGET` … `SYNCPOINT`, up to 501 messages) becomes a transacted
`@JmsListener` with concurrency 1: each message is its own transaction, which is what the legacy
per-message syncpoint achieved. The 501-message limit is preserved as a run counter on the listener
(`carddemo.pendingauth.mq.request-limit`, default 500 with the same "greater than" test), so a run
stops accepting messages after the same number as the legacy task; the counter resets when the
listener container restarts, matching a re-triggered CICS task.
The reply-to destination of the incoming message is used when present, otherwise the configured
reply queue.

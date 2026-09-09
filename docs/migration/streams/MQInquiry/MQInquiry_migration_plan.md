# S-10 MQInquiry — migration plan

How CDRA (`COACCT01`) and CDRD (`CODATE01`) were migrated into the consolidated app, and the boundary
decisions taken. Analysis: `MQInquiry_analysis.md`. Oracle: `MQInquiry_functional_requirement.md`.

## What was built

All backend code is in `com.carddemo.mqinquiry`; nothing in `com.carddemo.common` changed.

| Component | Legacy counterpart |
| --- | --- |
| `message.MqInquiryLayout` | The `PIC` clauses: 1000-byte buffer, `X(n)` space padding, `9(n)` zero padding, `S9(10)V99` overpunched zoned decimal |
| `message.InquiryRequestMessage` | `REQUEST-MSG-COPY` (`WS-FUNC`, `WS-KEY`, `WS-FILLER`) |
| `message.MqErrorReport` | `MQ-ERR-DISPLAY` |
| `service.AccountInquiryService` | `COACCT01` `4000-PROCESS-REQUEST-REPLY` |
| `service.DateInquiryService` | `CODATE01` `4000-PROCESS-REQUEST-REPLY` |
| `service.MqInquiryResult` | The choice between `4100-PUT-REPLY` and `9000-ERROR` + `8000-TERMINATION` |
| `jms.AccountInquiryListener`, `jms.DateInquiryListener` | The triggered transaction and its `MQGET` drain loop |
| `jms.MqInquiryReplySender` | `4100-PUT-REPLY` / `9000-ERROR` `MQPUT` |
| `jms.MqInquiryQueues` | `INPUT-QUEUE-NAME` (from `MQTM-QNAME`), now configuration |

Account data is read through the shared `AccountRepository` / `AccountRecord`; the `accounts` table
already carries every `CVACT01Y` field the reply needs, so **no Flyway migration was added** and the
S-10 band `V1000`–`V1099` is untouched.

No frontend change: CDRA and CDRD have no BMS maps — they are MQ request/reply services — so there is
no screen to add and no line to add to the shared route registry.

## Boundary decisions (B-05, D-7)

| # | Decision | Why |
| --- | --- | --- |
| 1 | `MQGET` → `@JmsListener` on the shared `cardDemoListenerContainerFactory` (transacted, concurrency 1); `MQPUT` → the shared `cardDemoJmsTemplate`. | D-7 and the README's seam; the factory's single-threaded transacted consumption is the drain loop's unit of work. |
| 2 | The seam stays behind `carddemo.mq.enabled`; nothing generalised into `common`. | S-09 needs the same seam; the stream owns only its listeners. |
| 3 | Request queues are configuration, not literals: `carddemo.mqinquiry.account-request-queue` (default `CARDDEMO.REQUEST.QUEUE`) and `carddemo.mqinquiry.date-request-queue` (default `CARDDEMO.DATE.REQUEST.QUEUE`). | The programs take their input queue from the CICS trigger message (`MQTM-QNAME`), so there is no literal to reproduce. The account default is the queue defined in `app/app-vsam-mq/README.md`; the date server needs a queue of its own because two JMS listeners on one queue would steal each other's requests, which triggered CICS transactions do not. |
| 4 | Reply and error queues stay the hard-coded literals (`CARD.DEMO.REPLY.ACCT`, `CARD.DEMO.REPLY.DATE`, `CARD.DEMO.ERROR`), and the request's reply-to destination is ignored. | The programs MOVE those literals and never use the saved `SAVE-REPLY2Q` (FR-MQI-003). |
| 5 | `MQMD-MSGID`/`MQMD-CORRELID` echo → `JMSCorrelationID` set from the request's correlation id, falling back to its message id. | JMS has one correlation slot; the fallback keeps a request without a correlation id matchable, as the legacy `MSGID` echo does. |
| 6 | `MQGMO-WAITINTERVAL 5000` and the "no message available ends the task" loop are not reproduced. | A triggered CICS transaction starts on arrival, drains and ends; a listener container is the same behaviour without a poll interval. Nothing observable on the queues depends on the wait. |
| 7 | The read-error report carries condition code `00` and reason code `00000`. | `WS-RESP-CD`/`WS-REAS-CD` are CICS file-control codes with no JPA equivalent; inventing values would misreport. The operator-visible field (`ERROR WHILE READING ACCTF`) and the queue name are reproduced exactly, and the JPA exception is logged. |
| 8 | `8000-TERMINATION` (close queues, end task) is not reproduced; the request that failed is rolled back instead. | The container owns the connection; rolling the message back is the closest equivalent to a task that ends without replying, and does not take the server down for the next request. |
| 9 | `artemis-jakarta-server` added test-scoped to the backend POM. | The only way to run the embedded broker the target state asks tests to use. No main-scope dependency and no shared code changed. |

## Parity — legacy vs migrated

| Path | Legacy (`COACCT01`/`CODATE01`) | Migrated | Evidence |
| --- | --- | --- | --- |
| Account found | `WS-ACCT-RESPONSE`, 252 bytes of labels and zoned-decimal fields, padded to 1000, on `CARD.DEMO.REPLY.ACCT` | identical bytes | `AccountInquiryServiceTest`, `MqInquiryListenerIntegrationTest` |
| Account field values | The `ACCTDAT` record bytes moved unchanged into the reply | the same bytes, for the first ten records of `app/data/ASCII/acctdata.txt` | `AccountInquiryParityTest` |
| Account not found | `INVALID REQUEST PARAMETERS ACCT ID : 99999999999` | identical | `AccountInquiryServiceTest`, `MqInquiryListenerIntegrationTest` |
| Wrong function / non-positive key | `INVALID REQUEST PARAMETERS ACCT ID : 00000000001FUNCTION : INQB`, no separator before `FUNCTION` | identical, including the missing space and the raw function bytes | `AccountInquiryServiceTest` |
| ACCTDAT unreadable | no reply; `MQ-ERR-DISPLAY` with the truncated `ERROR WHILE READING ACCTF` on `CARD.DEMO.ERROR` | identical layout and text; condition/reason codes zero (decision 7) | `AccountInquiryErrorIntegrationTest` |
| Date request | `SYSTEM DATE : MM-DD-YYYYSYSTEM TIME : HH:MM:SS` on `CARD.DEMO.REPLY.DATE`, for any request content | identical | `DateInquiryServiceTest`, `MqInquiryListenerIntegrationTest` |
| Ordering and units of work | one message at a time, one syncpoint each | one transacted listener, concurrency 1 | `MqInquiryListenerIntegrationTest` |

## Contradiction with the module README

`app/app-vsam-mq/README.md` documents a request layout of `REQUEST-ID X(8)` + `ACCOUNT-NUMBER X(11)` and
a `'DATE'` request type. Neither program implements it: both redefine the buffer as
`WS-FUNC X(04)` + `WS-KEY 9(11)` + `X(985)`, `COACCT01` tests for `INQA`, and `CODATE01` tests nothing.
The COBOL is the specification, so the implementation follows the source and the README's layout was
treated as aspirational documentation.

## Deferred

Nothing in the scope of the two programs. The MQ seam ships off (`carddemo.mq.enabled=false`), so
deploying these servers is an operational switch plus the two request-queue definitions, not a code
change.

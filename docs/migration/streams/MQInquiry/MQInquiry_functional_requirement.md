# S-10 MQInquiry — functional requirements

The business sign-off oracle for the MQ inquiry stream (CDRA/COACCT01, CDRD/CODATE01). Every requirement
is traceable to the COBOL source and covered by a test in
`migration/carddemo/backend/src/test/java/com/carddemo/mqinquiry/`.

Conventions: offsets are zero-based byte offsets in the message payload; `b` denotes a space. Signed
amounts are `S9(10)V99` zoned decimal — 12 bytes, implied decimal point, sign as an overpunch on the last
digit (`{ABCDEFGHI` = positive digits 0-9, `}JKLMNOPQR` = negative digits 0-9).

## Message contract

| ID | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-MQI-001 | A request message is 1000 bytes: `WS-FUNC X(04)` at 0, `WS-KEY 9(11)` at 4, `WS-FILLER X(985)` at 15. A payload shorter than 1000 bytes is space-padded before parsing; a longer one is truncated at 1000. | `COACCT01.cbl:109-112`, `:342`, `:373` | `InquiryRequestMessageTest` |
| FR-MQI-002 | A reply message is 1000 bytes: the reply text left-justified and space-padded. | `COACCT01.cbl:426`, `:467-468` | `InquiryRequestMessageTest`, `AccountInquiryServiceTest`, `DateInquiryServiceTest` |
| FR-MQI-003 | A reply echoes the request's correlation id; when the request carries none, the request's message id is used. The reply is always sent to the program's own reply queue, never to the request's reply-to destination, which is read and discarded. | `COACCT01.cbl:366`, `:371`, `:469-470` | `MqInquiryListenerIntegrationTest` |
| FR-MQI-004 | An error report is a 1000-byte message on `CARD.DEMO.ERROR`: `MQ-ERROR-PARA X(25)` at 0, 2 spaces, `MQ-APPL-RETURN-MESSAGE X(25)` at 27, 2 spaces, `MQ-APPL-CONDITION-CODE 9(02)` at 54, 2 spaces, `MQ-APPL-REASON-CODE 9(05)` at 58, 2 spaces, `MQ-APPL-QUEUE-NAME X(48)` at 65. | `COACCT01.cbl:58-67`, `:505-507` | `MqErrorReportTest` |

## CDRA — account inquiry (COACCT01)

| ID | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-MQI-010 | A request is served only when `WS-FUNC` is exactly `INQA` **and** `WS-KEY` is greater than zero. | `COACCT01.cbl:393` | `AccountInquiryServiceTest` |
| FR-MQI-011 | For a served request the account is read from ACCTDAT (`accounts`) by the 11-digit zero-padded `WS-KEY`. | `COACCT01.cbl:394-404` | `AccountInquiryServiceTest` |
| FR-MQI-011b | Every field of a reply carries the same bytes as the ACCTDAT unload the legacy program read. | `app/data/ASCII/acctdata.txt`, `app/cpy/CVACT01Y.cpy` | `AccountInquiryParityTest` |
| FR-MQI-012 | When the account exists the reply is the 252-byte `WS-ACCT-RESPONSE` block, in this field order with these verbatim labels: `ACCOUNT ID : ` + id `9(11)`, `ACCOUNT STATUS : ` + status `X(01)`, `BALANCE : ` + `S9(10)V99`, `CREDIT LIMIT : ` + `S9(10)V99`, `CASH LIMIT : ` + `S9(10)V99`, `OPEN DATE : ` + `X(10)`, `EXPR DATE : ` + `X(10)`, `REIS DATE : ` + `X(10)`, `CREDIT BAL : ` + `S9(10)V99`, `DEBIT BAL : ` + `S9(10)V99`, `GROUP ID : ` + `X(10)`. | `COACCT01.cbl:130-169`, `:407-426` | `AccountInquiryServiceTest`, `AccountInquiryParityTest` |
| FR-MQI-013 | When the account does not exist the reply is `INVALID REQUEST PARAMETERS ACCT ID : nnnnnnnnnnn` (48 bytes, key zero-padded to 11 digits). | `COACCT01.cbl:428-435` | `AccountInquiryServiceTest` |
| FR-MQI-014 | When `WS-FUNC` is not `INQA`, or `WS-KEY` is zero or not a positive number, the reply is `INVALID REQUEST PARAMETERS ACCT ID : nnnnnnnnnnnFUNCTION : ffff` (63 bytes; no space between the key and `FUNCTION`, `ffff` the raw 4 request bytes). A key whose bytes are not digits is treated as not greater than zero and takes this path, and is reported as 11 zeros. | `COACCT01.cbl:448-456` | `AccountInquiryServiceTest` |
| FR-MQI-015 | When the account read fails for any reason other than "not found", no reply is sent; an error report (FR-MQI-004) with return message `ERROR WHILE READING ACCTF` (the 28-byte literal truncated to the 25-byte field) and the request queue name is sent to `CARD.DEMO.ERROR`, and processing of that request ends. | `COACCT01.cbl:437-445` | `AccountInquiryErrorIntegrationTest` |
| FR-MQI-016 | The account address zip is never returned, although it is part of the account record. | `COACCT01.cbl:130-169` vs `app/cpy/CVACT01Y.cpy` | `AccountInquiryServiceTest` |

## CDRD — date service (CODATE01)

| ID | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-MQI-020 | Every request on the date queue is answered, whatever its `WS-FUNC` and `WS-KEY`; the request content is never examined. | `CODATE01.cbl:339-361` | `DateInquiryServiceTest` |
| FR-MQI-021 | The reply is `SYSTEM DATE : MM-DD-YYYYSYSTEM TIME : HH:MM:SS` (46 bytes): the label `SYSTEM DATE : `, the current date as `MM-DD-YYYY`, the label `SYSTEM TIME : `, the current time as `HH:MM:SS`. | `CODATE01.cbl:343-360` | `DateInquiryServiceTest` |
| FR-MQI-022 | Date and time come from one reading of the clock, so a reply never straddles a second boundary. | `CODATE01.cbl:343-353` | `DateInquiryServiceTest` |

## Messaging behaviour

| ID | Requirement | Source | Test |
| --- | --- | --- | --- |
| FR-MQI-030 | Each request is consumed, served and replied to in its own transaction; a failure to build or send the reply rolls the request back rather than losing it. | `COACCT01.cbl:325-331` (`SYNCPOINT` per message), `:347-350`, `:475-477` | `MqInquiryListenerIntegrationTest` |
| FR-MQI-031 | Requests are consumed one at a time, in queue order. | `COACCT01.cbl:214-218` (single-task drain loop) | `MqInquiryListenerIntegrationTest` |
| FR-MQI-032 | Account replies go to `CARD.DEMO.REPLY.ACCT`, date replies to `CARD.DEMO.REPLY.DATE`, error reports to `CARD.DEMO.ERROR`. | `COACCT01.cbl:198`, `:294`, `CODATE01.cbl:147` | `MqInquiryListenerIntegrationTest`, `MqInquiryDisabledTest` |
| FR-MQI-033 | The MQ inquiry servers are inactive unless the MQ seam is enabled (`carddemo.mq.enabled=true`), so the online app, CI and the batch jobs need no broker. | target state D-7, `migration/carddemo/README.md` §7 | `MqInquiryDisabledTest` |

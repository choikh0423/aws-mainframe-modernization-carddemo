# S-10 MQInquiry — source analysis

Scope: `app/app-vsam-mq/` — the MQ/VSAM add-on module. Two CICS transactions, no BMS maps.

| Transaction | Program | Source | Function |
| --- | --- | --- | --- |
| CDRA | COACCT01 | `app/app-vsam-mq/cbl/COACCT01.cbl` | Account inquiry over MQ, reading ACCTDAT |
| CDRD | CODATE01 | `app/app-vsam-mq/cbl/CODATE01.cbl` | System date/time service over MQ |

Both are MQ-triggered CICS servers: they are started by the MQ trigger monitor, learn the queue that
triggered them from the trigger message, drain that queue in a loop, and reply on a queue whose name is a
literal in the program. Neither has a screen, a COMMAREA, an `XCTL` or a `LINK`
(`COACCT01.cbl:174-176`, `CODATE01.cbl:123-125`: the LINKAGE SECTION is empty and `PROCEDURE DIVISION`
takes no parameters), so there is no navigation graph to trace for this stream.

CSD definitions (`app/app-vsam-mq/csd/CRDDEMOM.csd`): `PROGRAM(COACCT01) TRANSID(CDRA)`,
`PROGRAM(CODATE01) TRANSID(CDRD)`, both in `GROUP(CARDDEMO)`.

---

## 1. Entry conditions

Both programs are `PROGRAM-ID ... IS INITIAL` (`COACCT01.cbl:2`, `CODATE01.cbl:2`), so working storage is
re-initialised at every start — nothing survives between task starts.

Startup sequence, identical in both (`COACCT01.cbl:178-220`, `CODATE01.cbl:127-169`):

1. Clear `INPUT-QUEUE-NAME`, `QMGR-NAME`, `QUEUE-MESSAGE`; `INITIALIZE MQ-ERR-DISPLAY`.
2. `PERFORM 2100-OPEN-ERROR-QUEUE` — opens the literal `CARD.DEMO.ERROR`
   (`COACCT01.cbl:294`, `CODATE01.cbl:243`) for output. If that `MQOPEN` fails the program only
   `DISPLAY`s the error block and terminates (`COACCT01.cbl:315-321`) — it cannot report through the
   error queue it just failed to open.
3. `EXEC CICS RETRIEVE INTO(MQTM)` — the MQ trigger message. On `DFHRESP(NORMAL)` the triggered queue name
   `MQTM-QNAME` becomes `INPUT-QUEUE-NAME`, and the reply queue is a hard-coded literal:
   `CARD.DEMO.REPLY.ACCT` for COACCT01 (`COACCT01.cbl:198`) and `CARD.DEMO.REPLY.DATE` for CODATE01
   (`CODATE01.cbl:147`). On any other response the program writes `'CICS RETREIVE'` (sic — misspelled in
   COACCT01, `COACCT01.cbl:200`; `'CICS RETRIEVE'` in CODATE01, `CODATE01.cbl:149`) into `MQ-ERROR-PARA`
   with a `RESP: nnnnnnnnnnnnnnnnEND` string, puts it on the error queue and terminates.
4. `2300-OPEN-INPUT-QUEUE` — `MQOPEN` of the triggered queue with
   `MQOO-INPUT-SHARED + MQOO-SAVE-ALL-CONTEXT + MQOO-FAIL-IF-QUIESCING` (`COACCT01.cbl:229-231`).
5. `2400-OPEN-OUTPUT-QUEUE` — `MQOPEN` of the reply queue with
   `MQOO-OUTPUT + MQOO-PASS-ALL-CONTEXT + MQOO-FAIL-IF-QUIESCING` (`COACCT01.cbl:263-265`).
6. `PERFORM 3000-GET-REQUEST`, then `PERFORM 4000-MAIN-PROCESS UNTIL NO-MORE-MSGS`, then
   `8000-TERMINATION` (`COACCT01.cbl:214-218`).

`4000-MAIN-PROCESS` is `EXEC CICS SYNCPOINT` followed by another `3000-GET-REQUEST`
(`COACCT01.cbl:325-331`, `CODATE01.cbl:274-280`): one syncpoint per message, i.e. each request/reply pair
is its own unit of work.

### Message loop (`3000-GET-REQUEST`, `COACCT01.cbl:334-388`, `CODATE01.cbl:283-337`)

* `MQGMO-WAITINTERVAL = 5000` (5 s), options
  `MQGMO-SYNCPOINT + MQGMO-FAIL-IF-QUIESCING + MQGMO-CONVERT + MQGMO-WAIT`.
* `MQMD-MSGID = MQMI-NONE`, `MQMD-CORRELID = MQCI-NONE` — every `MQGET` takes the next message, no
  selection by id.
* Buffer length 1000; `MQ-BUFFER PIC X(1000)`.
* `MQCC-OK` → save `MQMD-MSGID`, `MQMD-CORRELID`, `MQMD-REPLYTOQ`, move the buffer to `REQUEST-MESSAGE`
  and then to `REQUEST-MSG-COPY`, process the request, `ADD 1 TO MQ-MSG-COUNT`.
* `MQRC-NO-MSG-AVAILABLE` → `SET NO-MORE-MSGS TO TRUE`, ending the loop after the 5 s wait expires.
* Any other reason code → `'INP MQGET ERR:'` to the error queue, then terminate.

**Quirk:** `MQMD-REPLYTOQ` is saved into `SAVE-REPLY2Q` (`COACCT01.cbl:371`) and never used. The reply
always goes to the program's hard-coded reply queue, not to the requester's `ReplyToQ`.

---

## 2. Record and message layouts

### Request — `REQUEST-MSG-COPY` (`COACCT01.cbl:109-112`, `CODATE01.cbl:109-112`)

| Field | PIC | Offset | Length |
| --- | --- | --- | --- |
| `WS-FUNC` | `X(04)` | 0 | 4 |
| `WS-KEY` | `9(11)` | 4 | 11 |
| `WS-FILLER` | `X(985)` | 15 | 985 |

Total 1000 bytes — the whole `MQ-BUFFER`. The buffer is moved as a group
(`MOVE REQUEST-MESSAGE TO REQUEST-MSG-COPY`, `COACCT01.cbl:373`), so `WS-KEY` receives raw bytes without
numeric editing.

The add-on README (`app/app-vsam-mq/README.md:99-128`) documents a different, aspirational layout
(`REQUEST-TYPE X(4)`, `REQUEST-ID X(8)`, `ACCOUNT-NUMBER X(11)`, `ACCOUNT-DATA X(300)`). **The programs do
not implement it** — there is no `REQUEST-ID` field anywhere in either program, and the account reply is
the labelled 252-byte `WS-ACCT-RESPONSE` block below. The COBOL is the specification (see the
contradiction list in the migration plan).

### Account reply — `WS-ACCT-RESPONSE` (`COACCT01.cbl:130-169`)

Built field by field, then `MOVE WS-ACCT-RESPONSE TO REPLY-MESSAGE` (`COACCT01.cbl:426`), which
space-pads to the 1000-byte `REPLY-MESSAGE`.

| Offset | Length | Field | PIC / literal |
| --- | --- | --- | --- |
| 0 | 13 | label | `'ACCOUNT ID : '` |
| 13 | 11 | `WS-ACCT-ID` | `9(11)` |
| 24 | 17 | label | `'ACCOUNT STATUS : '` |
| 41 | 1 | `WS-ACCT-ACTIVE-STATUS` | `X(01)` |
| 42 | 10 | label | `'BALANCE : '` |
| 52 | 12 | `WS-ACCT-CURR-BAL` | `S9(10)V99` |
| 64 | 15 | label | `'CREDIT LIMIT : '` |
| 79 | 12 | `WS-ACCT-CREDIT-LIMIT` | `S9(10)V99` |
| 91 | 13 | label | `'CASH LIMIT : '` |
| 104 | 12 | `WS-ACCT-CASH-CREDIT-LIMIT` | `S9(10)V99` |
| 116 | 12 | label | `'OPEN DATE : '` |
| 128 | 10 | `WS-ACCT-OPEN-DATE` | `X(10)` |
| 138 | 12 | label | `'EXPR DATE : '` |
| 150 | 10 | `WS-ACCT-EXPIRAION-DATE` | `X(10)` |
| 160 | 12 | label | `'REIS DATE : '` |
| 172 | 10 | `WS-ACCT-REISSUE-DATE` | `X(10)` |
| 182 | 13 | label | `'CREDIT BAL : '` |
| 195 | 12 | `WS-ACCT-CURR-CYC-CREDIT` | `S9(10)V99` |
| 207 | 12 | label | `'DEBIT BAL : '` |
| 219 | 12 | `WS-ACCT-CURR-CYC-DEBIT` | `S9(10)V99` |
| 231 | 11 | label | `'GROUP ID : '` |
| 242 | 10 | `WS-ACCT-GROUP-ID` | `X(10)` |
| | | | **252 bytes**, then spaces to 1000 |

`S9(10)V99` DISPLAY fields are 12 bytes with an implied decimal point and the sign carried as an overpunch
on the last digit (`{ABCDEFGHI` positive, `}JKLMNOPQR` negative) — the same encoding the ASCII unloads use
(`app/data/ASCII/acctdata.txt`) and that `com.carddemo.common.batch.FixedWidthRecord.signed` decodes.
`ACCT-ADDR-ZIP` is in the account record but **not** in the reply.

Source record: `ACCOUNT-RECORD` from `app/cpy/CVACT01Y.cpy` (RECLN 300), read from `ACCTDAT`
(`LIT-ACCTFILENAME` = `'ACCTDAT '`, `COACCT01.cbl:115-116`).

### Date reply (`CODATE01.cbl:355-360`)

`'SYSTEM DATE : ' WS-MMDDYYYY 'SYSTEM TIME : ' WS-TIME` — 14 + 10 + 14 + 8 = 46 bytes, then spaces to
1000. `WS-MMDDYYYY` comes from `EXEC CICS FORMATTIME ... MMDDYYYY(WS-MMDDYYYY) DATESEP('-')`
(`CODATE01.cbl:347-353`), i.e. `MM-DD-YYYY`; `WS-TIME` from `TIME(WS-TIME) TIMESEP` with no separator
value, i.e. the default `:` → `HH:MM:SS`. The clock is `EXEC CICS ASKTIME ABSTIME(WS-ABS-TIME)`
(`CODATE01.cbl:343-345`).

### Error message — `MQ-ERR-DISPLAY` (`COACCT01.cbl:58-67`, `CODATE01.cbl:58-67`)

| Offset | Length | Field |
| --- | --- | --- |
| 0 | 25 | `MQ-ERROR-PARA` |
| 25 | 2 | spaces |
| 27 | 25 | `MQ-APPL-RETURN-MESSAGE` |
| 52 | 2 | spaces |
| 54 | 2 | `MQ-APPL-CONDITION-CODE` `9(02)` |
| 56 | 2 | spaces |
| 58 | 5 | `MQ-APPL-REASON-CODE` `9(05)` |
| 63 | 2 | spaces |
| 65 | 48 | `MQ-APPL-QUEUE-NAME` |

113 bytes, moved to `ERROR-MESSAGE X(1000)` and put on `CARD.DEMO.ERROR` with buffer length 1000
(`COACCT01.cbl:505-523`).

---

## 3. Business rules and validation

### COACCT01 — `4000-PROCESS-REQUEST-REPLY` (`COACCT01.cbl:390-457`)

1. `MOVE SPACES TO REPLY-MESSAGE`, `INITIALIZE WS-DATE-TIME REPLACING NUMERIC BY ZEROES`
   (`COACCT01.cbl:391-392`). `WS-DATE-TIME` is never used afterwards in COACCT01 — dead initialisation
   inherited from CODATE01.
2. `IF WS-FUNC = 'INQA' AND WS-KEY > ZEROES` (`COACCT01.cbl:393`) — the only validation. The function code
   must be exactly `INQA` (4 bytes, case-sensitive) and the key must be numerically greater than zero.
3. Valid: `MOVE WS-KEY TO WS-CARD-RID-ACCT-ID` and
   `EXEC CICS READ DATASET('ACCTDAT ') RIDFLD(WS-CARD-RID-ACCT-ID-X) KEYLENGTH(11) INTO(ACCOUNT-RECORD)`
   (`COACCT01.cbl:394-404`). The RIDFLD is the redefinition of the `9(11)` field, so the key is the
   11-digit zero-padded account id.
   * `DFHRESP(NORMAL)` → copy 11 account fields into `WS-ACCT-RESPONSE`, move it to `REPLY-MESSAGE`,
     `PERFORM 4100-PUT-REPLY` (`COACCT01.cbl:407-427`).
   * `DFHRESP(NOTFND)` → `STRING 'INVALID REQUEST PARAMETERS ' 'ACCT ID : ' WS-KEY` into `REPLY-MESSAGE`
     and reply (`COACCT01.cbl:428-435`). 48 bytes: `INVALID REQUEST PARAMETERS ACCT ID : 00000000123`.
   * any other RESP → `MQ-APPL-CONDITION-CODE = WS-RESP-CD`, `MQ-APPL-REASON-CODE = WS-REAS-CD`,
     `MQ-APPL-QUEUE-NAME = INPUT-QUEUE-NAME`,
     `MQ-APPL-RETURN-MESSAGE = 'ERROR WHILE READING ACCTFILE'`, error queue, terminate
     (`COACCT01.cbl:437-445`). **Quirk:** the literal is 28 bytes into a `X(25)` field, so what is actually
     sent is `ERROR WHILE READING ACCTF`. **Quirk:** `MQ-APPL-CONDITION-CODE` is `9(02)`, so a RESP of 12
     (NOTOPEN) is sent as `12` but a three-digit RESP is truncated to its low-order two digits.
4. Invalid (`WS-FUNC` not `INQA`, or `WS-KEY` not > 0) → `ELSE` branch
   (`COACCT01.cbl:448-456`): `STRING 'INVALID REQUEST PARAMETERS ' 'ACCT ID : ' WS-KEY 'FUNCTION : '
   WS-FUNC` → 63 bytes: `INVALID REQUEST PARAMETERS ACCT ID : 00000000000FUNCTION : XXXX`. Note the
   missing space before `FUNCTION` — the fields abut exactly as written.

**Quirk:** `WS-KEY` is `PIC 9(11)` fed by a group move, so a request whose key bytes are not digits makes
`IF WS-KEY > ZEROES` a comparison over non-numeric data (undefined by the standard; on z/OS the zoned
digits are compared with their zone nibbles ignored, so `'0000000000A'` compares as 1 and passes). The
migrated code treats a non-numeric key as *not* greater than zero and takes the `ELSE` branch — recorded
as FR-MQI-014.

### CODATE01 — `4000-PROCESS-REQUEST-REPLY` (`CODATE01.cbl:339-364`)

No validation at all. `WS-FUNC` and `WS-KEY` are parsed into `REQUEST-MSG-COPY` and never examined: every
message on the triggered queue, whatever its content, gets the system date/time reply. The add-on README
speaks of a `'DATE'` request type; the program never tests it.

---

## 4. File access

| Program | File | Access | Key |
| --- | --- | --- | --- |
| COACCT01 | `ACCTDAT` (VSAM KSDS, `CVACT01Y`) | `EXEC CICS READ` (random, read-only) | `ACCT-ID`, 11 bytes |
| CODATE01 | none | — | — |

No file is ever written, browsed or deleted by either program.

## 5. Control flow / boundaries

* No `XCTL`, no `LINK`, no `CDEMO-TO-PROGRAM`/`CCARD-NEXT-PROG` handling in either program: nothing to
  trace symbolically. `8000-TERMINATION` closes whichever queues were opened and does `EXEC CICS RETURN`
  (`COACCT01.cbl:538-550`).
* Boundary **B-05**: `MQOPEN`/`MQPUT`/`MQGET`/`MQCLOSE` against `CARDDEMO.REQUEST.QUEUE` (triggered),
  `CARD.DEMO.REPLY.ACCT`, `CARD.DEMO.REPLY.DATE`, `CARD.DEMO.ERROR`.
* `4100-PUT-REPLY` (`COACCT01.cbl:462-499`) sets `MQMD-MSGID = SAVE-MSGID`, `MQMD-CORRELID =
  SAVE-CORELID` — the reply echoes the request's message id *and* correlation id — `MQMD-FORMAT =
  MQFMT-STRING`, `MQMD-CODEDCHARSETID = MQCCSI-Q-MGR`, and puts 1000 bytes under
  `MQPMO-SYNCPOINT + MQPMO-DEFAULT-CONTEXT + MQPMO-FAIL-IF-QUIESCING`.
* An `MQPUT` failure on the reply queue is reported as `'MQPUT ERR'` on the error queue and terminates the
  task (`COACCT01.cbl:492-498`).
* `8000-TERMINATION` is reached from every error path, so a failed read of ACCTDAT ends the *task*, not
  just the message: the remaining messages on the queue are left for the next trigger.

# S-11 DailyTransactionPosting — source analysis

Scope: job `app/jcl/POSTTRAN.jcl` → `STEP15 EXEC PGM=CBTRN02C`, program `app/cbl/CBTRN02C.cbl`, and the
three IDCAMS-only file-management neighbours in the same CA-7 posting network: `app/jcl/DALYREJS.jcl`,
`app/jcl/TRANBKP.jcl`, `app/jcl/TRANIDX.jcl`.

All line numbers below refer to the files as committed under `app/`.

---

## 1. Job and step

`POSTTRAN.jcl:23` is the only executable step of the job:

```
//STEP15 EXEC PGM=CBTRN02C
```

DD statements (`POSTTRAN.jcl:28-42`) and what each one is:

| DD | Dataset | Open mode in CBTRN02C | Meaning |
|---|---|---|---|
| `TRANFILE` | `...TRANSACT.VSAM.KSDS` | `OPEN OUTPUT` (`CBTRN02C.cbl:256`) | posted transaction master, **loaded from empty** |
| `DALYTRAN` | `...DALYTRAN.PS` | `OPEN INPUT` (`CBTRN02C.cbl:238`) | today's unposted transactions, QSAM sequential, LRECL 350 |
| `XREFFILE` | `...CARDXREF.VSAM.KSDS` | `OPEN INPUT` (`CBTRN02C.cbl:275`) | card → account/customer cross reference |
| `DALYREJS` | `...DALYREJS(+1)`, `RECFM=F,LRECL=430` | `OPEN OUTPUT` (`CBTRN02C.cbl:293`) | new GDG generation holding rejected records |
| `ACCTFILE` | `...ACCTDATA.VSAM.KSDS` | `OPEN I-O` (`CBTRN02C.cbl:311`) | account master, read and rewritten in place |
| `TCATBALF` | `...TCATBALF.VSAM.KSDS` | `OPEN I-O` (`CBTRN02C.cbl:329`) | transaction category balances, read/written/rewritten |

There is no `PARM=` and no `SYSIN`: the step takes no parameters. Entry condition is simply that the six
datasets exist; `DALYREJS(+1)` is allocated by the JCL itself.

### CA-7 position

`docs/migration/CardDemo_inventory.md:83` places the step in the posting network
`CLOSEFIL → CBPAUP0J → POSTTRAN → WAITSTEP → OPENFIL → …`, i.e. CICS files are closed before the job runs
and reopened afterwards, so the batch program owns the VSAM clusters exclusively while it runs.

### Neighbouring IDCAMS jobs

* `DALYREJS.jcl:140-144` — `DEFINE GENERATIONDATAGROUP (NAME(AWS.M2.CARDDEMO.DALYREJS) LIMIT(5) SCRATCH)`.
  One-off definition of the reject GDG; `LIMIT(5) SCRATCH` keeps the five most recent generations and
  scratches older ones.
* `TRANBKP.jcl:68-78` — `REPRO` of `TRANSACT.VSAM.KSDS` to `TRANSACT.BKUP(+1)` (LRECL 350), then
  `TRANBKP.jcl:82-113` deletes and re-defines the `TRANSACT` cluster (`KEYS(16 0)`, `RECORDSIZE(350 350)`)
  and deletes the alternate index. So the transaction master is **backed up and emptied before POSTTRAN
  runs**, which is consistent with CBTRN02C opening `TRANFILE` `OUTPUT`.
* `TRANIDX.jcl:173-182` — defines a **non-unique** alternate index over `TRANSACT` with
  `KEYS(26 304)`, i.e. 26 bytes at offset 304 (1-based 305) = `TRAN-PROC-TS` (`CVTRA05Y.cpy`), then
  `TRANIDX.jcl:190-192` defines its path and `TRANIDX.jcl:200-202` builds it with `BLDINDEX`. Its only
  effect is an access path on the processing timestamp; it holds no data of its own.

---

## 2. Record layouts

DALYTRAN in, `CVTRA06Y.cpy` (RECLN 350). Offsets are 1-based COBOL positions:

| Field | PIC | Pos | Len |
|---|---|---|---|
| `DALYTRAN-ID` | `X(16)` | 1 | 16 |
| `DALYTRAN-TYPE-CD` | `X(02)` | 17 | 2 |
| `DALYTRAN-CAT-CD` | `9(04)` | 19 | 4 |
| `DALYTRAN-SOURCE` | `X(10)` | 23 | 10 |
| `DALYTRAN-DESC` | `X(100)` | 33 | 100 |
| `DALYTRAN-AMT` | `S9(09)V99` | 133 | 11 |
| `DALYTRAN-MERCHANT-ID` | `9(09)` | 144 | 9 |
| `DALYTRAN-MERCHANT-NAME` | `X(50)` | 153 | 50 |
| `DALYTRAN-MERCHANT-CITY` | `X(50)` | 203 | 50 |
| `DALYTRAN-MERCHANT-ZIP` | `X(10)` | 253 | 10 |
| `DALYTRAN-CARD-NUM` | `X(16)` | 263 | 16 |
| `DALYTRAN-ORIG-TS` | `X(26)` | 279 | 26 |
| `DALYTRAN-PROC-TS` | `X(26)` | 305 | 26 |
| `FILLER` | `X(20)` | 331 | 20 |

`DALYTRAN-AMT` is a signed zoned decimal: the sign is an overpunch on the last digit
(`app/data/ASCII/dailytran.txt` carries e.g. `0000005047G` = +50.47).

TRANSACT out, `CVTRA05Y.cpy` (RECLN 350) is the same layout field for field with `TRAN-` names — the
posted record is a copy of the daily record with `TRAN-PROC-TS` filled in (§4).

TCATBALF, `CVTRA01Y.cpy` (RECLN 50): key `TRANCAT-ACCT-ID 9(11)` + `TRANCAT-TYPE-CD X(02)` +
`TRANCAT-CD 9(04)` (17 bytes), then `TRAN-CAT-BAL S9(09)V99` and `FILLER X(22)`.

CCXREF, `CVACT03Y.cpy` (RECLN 50): `XREF-CARD-NUM X(16)`, `XREF-CUST-ID 9(09)`, `XREF-ACCT-ID 9(11)`, filler.

ACCTDAT, `CVACT01Y.cpy` (RECLN 300): the fields this program touches are `ACCT-CURR-BAL`,
`ACCT-CREDIT-LIMIT`, `ACCT-EXPIRAION-DATE X(10)` (misspelling is in the copybook),
`ACCT-CURR-CYC-CREDIT`, `ACCT-CURR-CYC-DEBIT`.

Reject record, declared twice — as the FD (`CBTRN02C.cbl:81-84`) and in working storage
(`CBTRN02C.cbl:176-182`): `REJECT-TRAN-DATA X(350)` followed by `VALIDATION-TRAILER X(80)`, where the
trailer is `WS-VALIDATION-FAIL-REASON PIC 9(04)` + `WS-VALIDATION-FAIL-REASON-DESC PIC X(76)`. Total 430,
matching `DCB=(RECFM=F,LRECL=430)` in `POSTTRAN.jcl:36`.

---

## 3. Control flow

`CBTRN02C.cbl:193-234`:

1. `DISPLAY 'START OF EXECUTION OF PROGRAM CBTRN02C'` (line 194).
2. Open all six files (lines 195-200); any open whose file status is not `'00'` displays a per-file
   message, the file status, and abends (e.g. lines 247-250).
3. Main loop `PERFORM UNTIL END-OF-FILE = 'Y'` (lines 202-219): read the next DALYTRAN record; for each
   record read successfully — increment `WS-TRANSACTION-COUNT`, reset the validation trailer to
   reason `0` / spaces, `PERFORM 1500-VALIDATE-TRAN`, and then either `2000-POST-TRANSACTION` when the
   reason is still 0 or increment `WS-REJECT-COUNT` and `2500-WRITE-REJECT-REC` when it is not.
4. Close all six files (lines 221-226).
5. `DISPLAY 'TRANSACTIONS PROCESSED :' WS-TRANSACTION-COUNT` and
   `DISPLAY 'TRANSACTIONS REJECTED  :' WS-REJECT-COUNT` (lines 227-228), both `PIC 9(09)`, i.e.
   zero-padded to 9 digits.
6. `IF WS-REJECT-COUNT > 0 MOVE 4 TO RETURN-CODE` (lines 229-231) — rejects are a condition code 4, not a
   failure.
7. `DISPLAY 'END OF EXECUTION OF PROGRAM CBTRN02C'` and `GOBACK` (lines 232-234).

There is no CICS in this program: no `XCTL`, no `LINK`, no `CDEMO-TO-PROGRAM`/`CCARD-NEXT-PROG`
transfers. The only outbound call is `CALL 'CEE3ABD' USING ABCODE, TIMING` in `9999-ABEND-PROGRAM`
(`CBTRN02C.cbl:707-711`) with `ABCODE = 999`, `TIMING = 0` — boundary **B-01**.

`1000-DALYTRAN-GET-NEXT` (lines 345-369) maps file status `'00'` to "keep going", `'10'` to end of file,
anything else to `DISPLAY 'ERROR READING DALYTRAN FILE'` + file status + abend.

---

## 4. Field-by-field validation and business rules

### 4.1 `1500-VALIDATE-TRAN` (lines 370-378)

Runs `1500-A-LOOKUP-XREF` first, and `1500-B-LOOKUP-ACCT` **only if the reason code is still 0**
(lines 372-376). The comment `* ADD MORE VALIDATIONS HERE` (line 377) marks the intended extension point;
there are no other validations. Notably the program does **not** validate the transaction amount, the
type/category codes against TRANTYPE/TRANCATG, the account active status, or duplicate transaction ids.

### 4.2 `1500-A-LOOKUP-XREF` (lines 380-392)

`READ XREF-FILE` keyed on `DALYTRAN-CARD-NUM`. `INVALID KEY` → reason **100**,
`'INVALID CARD NUMBER FOUND'`.

### 4.3 `1500-B-LOOKUP-ACCT` (lines 393-422)

`READ ACCOUNT-FILE` keyed on `XREF-ACCT-ID` (the account from the cross reference, not from the
transaction). `INVALID KEY` → reason **101**, `'ACCOUNT RECORD NOT FOUND'`.

When the account is found, two independent checks run in this order:

1. Overlimit (lines 403-413):
   ```
   WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT
   IF ACCT-CREDIT-LIMIT >= WS-TEMP-BAL  → ok
   ELSE reason 102, 'OVERLIMIT TRANSACTION'
   ```
   The cycle-to-date figures, not `ACCT-CURR-BAL`, drive the check, and `WS-TEMP-BAL` is
   `PIC S9(09)V99` (line 187), so the comparison is on two decimals.
2. Expiration (lines 414-420):
   ```
   IF ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS (1:10) → ok
   ELSE reason 103, 'TRANSACTION RECEIVED AFTER ACCT EXPIRATION'
   ```
   This is an **alphanumeric** comparison of `X(10)` against the first 10 characters of the original
   timestamp, both `YYYY-MM-DD`, so it collates as a date comparison. An account expiring *on* the
   transaction date is accepted.

**Quirk (preserved):** the two checks are not mutually exclusive and the expiration check is not guarded
by the outcome of the overlimit check, so a transaction that is both overlimit and past expiry is
reported as **103 only** — the later `MOVE` overwrites the reason and the description. Only one reason
code ever reaches the reject file.

### 4.4 `2000-POST-TRANSACTION` (lines 424-444)

Copies all twelve DALYTRAN fields into the TRAN record verbatim (lines 425-436), including
`DALYTRAN-ORIG-TS`; `DALYTRAN-PROC-TS` from the input is *not* copied. It then calls
`Z-GET-DB2-FORMAT-TIMESTAMP` and moves the result to `TRAN-PROC-TS` (lines 437-438), and performs, in
order: `2700-UPDATE-TCATBAL`, `2800-UPDATE-ACCOUNT-REC`, `2900-WRITE-TRANSACTION-FILE`.

`Z-GET-DB2-FORMAT-TIMESTAMP` (lines 692-705) formats `FUNCTION CURRENT-DATE` as
`YYYY-MM-DD-HH.MM.SS.hh0000`: the layout at lines 159-174 is `X(26)` with `-` separators after the year,
month and day, `.` after hour, minute and second, `DB2-MIL PIC 9(02)` taking `COB-MIL` (hundredths of a
second from `FUNCTION CURRENT-DATE`) and a literal `'0000'` filling the remaining four digits.

### 4.5 `2700-UPDATE-TCATBAL` (lines 467-542)

Key is `XREF-ACCT-ID` + `DALYTRAN-TYPE-CD` + `DALYTRAN-CAT-CD` (lines 469-471). On `INVALID KEY`
(record absent) it displays

```
'TCATBAL record not found for key : ' FD-TRAN-CAT-KEY '.. Creating.'
```

(lines 476-477 — `DISPLAY` concatenates the operands with no separator, so the raw 17-byte key
`9(11)` + `X(02)` + `9(04)` is followed immediately by `.. Creating.`) and sets the create flag. File status `'00'` or `'23'`
(found / not found) are both acceptable (line 481); anything else displays
`'ERROR READING TRANSACTION BALANCE FILE'` and abends.

* Create path `2700-A-CREATE-TCATBAL-REC` (lines 503-524): `INITIALIZE` the record — so the balance
  starts at zero — set the three key fields, `ADD DALYTRAN-AMT TO TRAN-CAT-BAL`, `WRITE`. A non-`'00'`
  status displays `'ERROR WRITING TRANSACTION BALANCE FILE'` and abends.
* Update path `2700-B-UPDATE-TCATBAL-REC` (lines 526-542): `ADD DALYTRAN-AMT TO TRAN-CAT-BAL`, `REWRITE`.
  A non-`'00'` status displays `'ERROR REWRITING TRANSACTION BALANCE FILE'` and abends.

### 4.6 `2800-UPDATE-ACCOUNT-REC` (lines 545-560)

```
ADD DALYTRAN-AMT TO ACCT-CURR-BAL
IF DALYTRAN-AMT >= 0  ADD DALYTRAN-AMT TO ACCT-CURR-CYC-CREDIT
ELSE                  ADD DALYTRAN-AMT TO ACCT-CURR-CYC-DEBIT
REWRITE ... INVALID KEY MOVE 109, 'ACCOUNT RECORD NOT FOUND'
```

A zero or positive amount counts as *credit* activity and a negative amount as *debit* activity, and the
debit bucket therefore accumulates negative numbers, which is what makes
`ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT` in the overlimit check a sum of the cycle's activity.

**Quirk (preserved):** reason code **109** is dead. It is set after the record has already been accepted
and posted, the main loop only inspects the reason code *before* posting (line 211), and the next
iteration resets it to 0 (line 208). It can never produce a reject record, so 109 never appears in the
reject file. The account row is also the one that was just read by key in the same iteration, so the
`INVALID KEY` branch is unreachable in practice.

### 4.7 `2900-WRITE-TRANSACTION-FILE` (lines 562-579)

`WRITE` the TRAN record to the KSDS. Any status other than `'00'` displays
`'ERROR WRITING TO TRANSACTION FILE'`, the file status, and abends. Because `TRANFILE` was opened
`OUTPUT` the file starts empty, so the only realistic failure is a duplicate `TRAN-ID` (status `'22'`)
within one DALYTRAN file.

### 4.8 `2500-WRITE-REJECT-REC` (lines 446-465)

Writes the 430-byte record: the **unmodified 350-byte DALYTRAN image** followed by the trailer
(4-digit zero-padded reason, 76-character space-padded description). A non-`'00'` status displays
`'ERROR WRITING TO REJECTS FILE'` and abends.

---

## 5. Error paths and exact message text

Every message string in the program, in source order:

| Line | Message | Follow-up |
|---|---|---|
| 194 | `START OF EXECUTION OF PROGRAM CBTRN02C` | |
| 247 | `ERROR OPENING DALYTRAN` | status + abend |
| 265 | `ERROR OPENING TRANSACTION FILE` | status + abend |
| 284 | `ERROR OPENING CROSS REF FILE` | status + abend |
| 302 | `ERROR OPENING DALY REJECTS FILE` | status + abend |
| 320 | `ERROR OPENING ACCOUNT MASTER FILE` | status + abend |
| 338 | `ERROR OPENING TRANSACTION BALANCE FILE` | status + abend |
| 363 | `ERROR READING DALYTRAN FILE` | status + abend |
| 386 | `INVALID CARD NUMBER FOUND` | reject reason 100 |
| 398 | `ACCOUNT RECORD NOT FOUND` | reject reason 101 |
| 411 | `OVERLIMIT TRANSACTION` | reject reason 102 |
| 418 | `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` | reject reason 103 |
| 460 | `ERROR WRITING TO REJECTS FILE` | status + abend |
| 476 | `TCATBAL record not found for key : <key>.. Creating.` | informational |
| 489 | `ERROR READING TRANSACTION BALANCE FILE` | status + abend |
| 520 | `ERROR WRITING TRANSACTION BALANCE FILE` | status + abend |
| 538 | `ERROR REWRITING TRANSACTION BALANCE FILE` | status + abend |
| 557 | `ACCOUNT RECORD NOT FOUND` | reason 109, dead (§4.6) |
| 574 | `ERROR WRITING TO TRANSACTION FILE` | status + abend |
| 593 | `ERROR CLOSING DALYTRAN FILE` | status + abend |
| 611 | `ERROR CLOSING TRANSACTION FILE` | status + abend |
| 630 | `ERROR CLOSING CROSS REF FILE` | status + abend |
| 648 | `ERROR CLOSING DAILY REJECTS FILE` | status + abend |
| 666 | `ERROR CLOSING ACCOUNT FILE` | status + abend |
| 685 | `ERROR CLOSING TRANSACTION BALANCE FILE` | status + abend |
| 227 | `TRANSACTIONS PROCESSED :<9 digits>` | |
| 228 | `TRANSACTIONS REJECTED  :<9 digits>` | two spaces before the colon |
| 708 | `ABENDING PROGRAM` | `CALL 'CEE3ABD'`, ABCODE 999 |
| 721/725 | `FILE STATUS IS: NNNN<4 digits>` | `9910-DISPLAY-IO-STATUS` |

`9910-DISPLAY-IO-STATUS` (lines 714-727) renders a numeric status as `'0000'` with the two status
characters in positions 3-4 (e.g. status `'22'` → `FILE STATUS IS: NNNN0022`); for a non-numeric status,
or one whose first character is `'9'`, it prints the first character followed by the binary value of the
second byte as three digits.

**Quirk (preserved):** `9300-DALYREJS-CLOSE` reports `XREFFILE-STATUS`, not `DALYREJS-STATUS`
(`CBTRN02C.cbl:649`) — a copy/paste bug in the error path of the reject-file close.

---

## 6. File access pattern summary

| File | Access | Operations |
|---|---|---|
| DALYTRAN | sequential | `READ` until status `'10'` — one pass, no restart marker of any kind |
| CCXREF | indexed, random | `READ` by card number, once per input record |
| ACCTDAT | indexed, random, I-O | `READ` by account id then `REWRITE`, once per posted record |
| TCATBALF | indexed, random, I-O | `READ` by (account, type, category) then `WRITE` or `REWRITE`, once per posted record |
| TRANSACT | indexed, random, OUTPUT | `WRITE` only, once per posted record; the file is emptied by the open |
| DALYREJS | sequential, OUTPUT | `WRITE` once per rejected record |

Neither the account nor the category balance is cached: a second transaction on the same account in the
same run re-reads the row that the previous iteration just rewrote, so effects accumulate in input order.

---

## 7. Facts that decide the migration

1. The unit of work is one DALYTRAN record; there is no COBOL-level commit scope, so any chunk size is
   behaviour-preserving as long as effects accumulate in input order.
2. Rejects are data, not failures: the job ends with condition code 4 and every valid record is still
   posted.
3. The abend path is a hard stop; nothing is rolled back on z/OS, but the job's condition code is
   non-zero and the operations chain stops.
4. `TRANFILE` `OPEN OUTPUT` means POSTTRAN *replaces* the transaction master (TRANBKP has already backed
   it up), rather than appending to it.
5. Nothing in the stream reads `TRAN-PROC-TS` back; the alternate index built by TRANIDX is the access
   path later streams use to select posted transactions by processing timestamp.

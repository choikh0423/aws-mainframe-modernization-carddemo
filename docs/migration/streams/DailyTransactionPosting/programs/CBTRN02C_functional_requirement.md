# CBTRN02C — functional requirements

Program `app/cbl/CBTRN02C.cbl`, run as `STEP15` of `app/jcl/POSTTRAN.jcl`. Requirements are stated per
COBOL paragraph and mapped to the stream requirements in
`../DailyTransactionPosting_functional_requirement.md`.

## Entry conditions

| ID | Requirement | Source |
|---|---|---|
| FR-CBTRN02C-01 | The program takes no parameters: no `PARM=`, no `SYSIN`, no `LINKAGE SECTION`. | `POSTTRAN.jcl:23-42`, `CBTRN02C.cbl:193` |
| FR-CBTRN02C-02 | It requires six datasets: `DALYTRAN` (input, sequential), `XREFFILE` (input, indexed), `ACCTFILE` and `TCATBALF` (I-O, indexed), `TRANFILE` (output, indexed) and `DALYREJS` (output, sequential, LRECL 430). | `CBTRN02C.cbl:29-61`, `POSTTRAN.jcl:28-42` |
| FR-CBTRN02C-03 | `TRANFILE` is opened `OUTPUT`, so the transaction master is loaded from empty. → FR-S11-003 | `CBTRN02C.cbl:256` |
| FR-CBTRN02C-04 | A failure to open any of the six files displays the file-specific message, the file status, `ABENDING PROGRAM`, and terminates with a user abend (code 999). | `CBTRN02C.cbl:236-343, 707-711` |

## Main line (`PROCEDURE DIVISION`, lines 193-234)

| ID | Requirement | Source |
|---|---|---|
| FR-CBTRN02C-05 | Displays `START OF EXECUTION OF PROGRAM CBTRN02C` before opening files and `END OF EXECUTION OF PROGRAM CBTRN02C` after closing them. → FR-S11-020 | `CBTRN02C.cbl:194, 232` |
| FR-CBTRN02C-06 | Reads DALYTRAN until end of file; each record read increments `WS-TRANSACTION-COUNT`, resets the validation trailer to reason 0 / spaces, is validated and is then either posted or rejected. → FR-S11-002 | `CBTRN02C.cbl:202-219` |
| FR-CBTRN02C-07 | A rejected record increments `WS-REJECT-COUNT` and is written to DALYREJS; a valid record is posted. The two paths are exclusive. → FR-S11-023 | `CBTRN02C.cbl:211-216` |
| FR-CBTRN02C-08 | Displays `TRANSACTIONS PROCESSED :` and `TRANSACTIONS REJECTED  :` with `PIC 9(09)` counts (zero padded to 9 digits, two spaces before the second colon). → FR-S11-020 | `CBTRN02C.cbl:227-228` |
| FR-CBTRN02C-09 | Sets `RETURN-CODE` 4 if and only if at least one record was rejected; the run is otherwise 0. → FR-S11-021 | `CBTRN02C.cbl:229-231` |

## `1000-DALYTRAN-GET-NEXT` (lines 345-369)

| ID | Requirement | Source |
|---|---|---|
| FR-CBTRN02C-10 | File status `'10'` ends the loop normally; any status other than `'00'` or `'10'` displays `ERROR READING DALYTRAN FILE`, the file status, and abends. | `CBTRN02C.cbl:345-369` |

## `1500-VALIDATE-TRAN` and subordinates (lines 370-422)

| ID | Requirement | Source |
|---|---|---|
| FR-CBTRN02C-11 | Validation is: cross-reference lookup, then — only if it succeeded — account lookup, overlimit check and expiration check, in that order. → FR-S11-009 | `CBTRN02C.cbl:370-378` |
| FR-CBTRN02C-12 | `READ XREF-FILE` keyed on `DALYTRAN-CARD-NUM`; `INVALID KEY` sets reason 100 / `INVALID CARD NUMBER FOUND`. → FR-S11-004 | `CBTRN02C.cbl:380-392` |
| FR-CBTRN02C-13 | `READ ACCOUNT-FILE` keyed on `XREF-ACCT-ID`; `INVALID KEY` sets reason 101 / `ACCOUNT RECORD NOT FOUND`. → FR-S11-005 | `CBTRN02C.cbl:393-399` |
| FR-CBTRN02C-14 | `WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT` (two decimals); reason 102 / `OVERLIMIT TRANSACTION` when `ACCT-CREDIT-LIMIT` is below it. → FR-S11-006 | `CBTRN02C.cbl:403-413` |
| FR-CBTRN02C-15 | Reason 103 / `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` when `ACCT-EXPIRAION-DATE` collates below `DALYTRAN-ORIG-TS (1:10)`. The comparison is alphanumeric on `YYYY-MM-DD`. → FR-S11-007 | `CBTRN02C.cbl:414-420` |
| FR-CBTRN02C-16 | The expiration check runs regardless of the overlimit outcome, so both failing yields reason 103. (Quirk.) → FR-S11-008 | `CBTRN02C.cbl:407-420` |
| FR-CBTRN02C-17 | No other field is validated. (Quirk.) → FR-S11-026 | `CBTRN02C.cbl:377` |

## `2000-POST-TRANSACTION` and subordinates (lines 424-579)

| ID | Requirement | Source |
|---|---|---|
| FR-CBTRN02C-18 | Copies the twelve DALYTRAN fields onto the TRAN record; `DALYTRAN-PROC-TS` is not copied. → FR-S11-010 | `CBTRN02C.cbl:425-436` |
| FR-CBTRN02C-19 | `TRAN-PROC-TS` is `FUNCTION CURRENT-DATE` rendered as `YYYY-MM-DD-HH.MM.SS.hh0000`. → FR-S11-011 | `CBTRN02C.cbl:437-438, 692-705` |
| FR-CBTRN02C-20 | Order of effects per posted record: category balance, then account, then the transaction write. | `CBTRN02C.cbl:440-442` |
| FR-CBTRN02C-21 | Category balance keyed `XREF-ACCT-ID` + `DALYTRAN-TYPE-CD` + `DALYTRAN-CAT-CD`; absent → display `TCATBAL record not found for key : <key>.. Creating.` and create the record with balance = amount; present → add the amount. → FR-S11-012, FR-S11-013 | `CBTRN02C.cbl:467-542` |
| FR-CBTRN02C-22 | Category balance I/O errors display `ERROR READING TRANSACTION BALANCE FILE` / `ERROR WRITING TRANSACTION BALANCE FILE` / `ERROR REWRITING TRANSACTION BALANCE FILE` and abend. | `CBTRN02C.cbl:489, 520, 538` |
| FR-CBTRN02C-23 | Account update: amount added to `ACCT-CURR-BAL`, and to `ACCT-CURR-CYC-CREDIT` when it is `>= 0`, otherwise to `ACCT-CURR-CYC-DEBIT`. → FR-S11-014, FR-S11-015, FR-S11-016 | `CBTRN02C.cbl:545-552` |
| FR-CBTRN02C-24 | Reason 109 on the account rewrite is unreachable and can never be written to the reject file. (Quirk.) → FR-S11-027 | `CBTRN02C.cbl:554-559` |
| FR-CBTRN02C-25 | A transaction write whose status is not `'00'` displays `ERROR WRITING TO TRANSACTION FILE`, the file status and abends. → FR-S11-022 | `CBTRN02C.cbl:562-579` |

## `2500-WRITE-REJECT-REC` (lines 446-465)

| ID | Requirement | Source |
|---|---|---|
| FR-CBTRN02C-26 | Writes `REJECT-TRAN-DATA X(350)` (the daily record as read) + `VALIDATION-TRAILER X(80)` (reason `9(04)` + description `X(76)`). → FR-S11-018, FR-S11-019 | `CBTRN02C.cbl:176-182, 446-451` |
| FR-CBTRN02C-27 | A reject write whose status is not `'00'` displays `ERROR WRITING TO REJECTS FILE`, the file status and abends. | `CBTRN02C.cbl:460-463` |

## Termination paths

| ID | Requirement | Source |
|---|---|---|
| FR-CBTRN02C-28 | Every close error displays its own message (`ERROR CLOSING DALYTRAN FILE`, `ERROR CLOSING TRANSACTION FILE`, `ERROR CLOSING CROSS REF FILE`, `ERROR CLOSING DAILY REJECTS FILE`, `ERROR CLOSING ACCOUNT FILE`, `ERROR CLOSING TRANSACTION BALANCE FILE`), the file status and abends. `9300-DALYREJS-CLOSE` displays `XREFFILE-STATUS` rather than its own status. (Quirk.) | `CBTRN02C.cbl:582-690`, quirk at 649 |
| FR-CBTRN02C-29 | The abend path displays `ABENDING PROGRAM` and calls `CEE3ABD` with ABCODE 999, TIMING 0 (boundary B-01). → FR-S11-022 | `CBTRN02C.cbl:707-711` |
| FR-CBTRN02C-30 | `9910-DISPLAY-IO-STATUS` renders a numeric status as `FILE STATUS IS: NNNN00<ss>`; a non-numeric status, or one starting with `'9'`, prints the first character then the binary value of the second byte in three digits. | `CBTRN02C.cbl:714-727` |

# CBTRN03C — functional requirements

`app/cbl/CBTRN03C.cbl`, step `STEP10R` of `app/jcl/TRANREPT.jcl`. Batch, no screens.
Requirement numbering continues the stream document
([`../TransactionReporting_functional_requirement.md`](../TransactionReporting_functional_requirement.md));
the FR-14xx identifiers below are the same ones, restated program-locally with their detail.

## Entry conditions

| Input | Source | Notes |
| --- | --- | --- |
| `TRANFILE` | `TRANSACT.DALY(+1)`, the sorted extract | 350-byte records, card-number order, already date-filtered |
| `CARDXREF` | `CARDXREF.VSAM.KSDS` | keyed by 16-character card number |
| `TRANTYPE` | `TRANTYPE.VSAM.KSDS` | keyed by 2-character type code |
| `TRANCATG` | `TRANCATG.VSAM.KSDS` | keyed by type code + 4-digit category code |
| `DATEPARM` | `AWS.M2.CARDDEMO.DATEPARM` | one record: start date (10) + separator + end date (10) |
| `TRANREPT` | output, `LRECL=133 RECFM=FB` | the report |

Migrated equivalent: the extract file the sort step wrote, the shared JPA reference repositories, and
the `startDate`/`endDate` job parameters in place of `DATEPARM` (the two were the same two values on
the mainframe — CR00 wrote both, see the analysis §4).

## Requirements

### Report structure

- **FR-1421** Every record written is exactly 133 characters (CBTRN03C.cbl:343-359, TRANREPT.jcl:78).
- **FR-1422** The first detail line of the run triggers the header block (`WS-FIRST-TIME`,
  CBTRN03C.cbl:275-280): name header (`DALYREPT`, `Daily Transaction Report`,
  `Date Range: <start> to <end>`), a blank line, `TRANSACTION-HEADER-1`, `TRANSACTION-HEADER-2`
  (133 hyphens) — labels verbatim from `app/cpy/CVTRA07Y.cpy:4-48`.
- **FR-1423** The detail line is `TRANSACTION-DETAIL-REPORT` (CVTRA07Y.cpy:15-31), filled by
  `1120-WRITE-DETAIL` (CBTRN03C.cbl:361-374): transaction id, account id from the cross-reference,
  type code, `-`, type description, category code (4 digits), `-`, category description, source,
  amount.
- **FR-1424** Amount editing: details `-ZZZ,ZZZ,ZZZ.ZZ`, totals `+ZZZ,ZZZ,ZZZ.ZZ`
  (CVTRA07Y.cpy:30, :54, :60, :66). Leading zeros blank down to the decimal point, the sign position
  is blank for a non-negative detail and `+` for a non-negative total, and the value truncates
  because the accumulators are `S9(09)V99`.
- **FR-1425** Paging: `IF FUNCTION MOD(WS-LINE-COUNTER, WS-PAGE-SIZE) = 0` with
  `WS-PAGE-SIZE = 20` (CBTRN03C.cbl:131-132, :282-285). `WS-LINE-COUNTER` is incremented for header
  lines, rule lines, detail lines, page-total lines and account-total lines, but **not** for the
  grand total (CBTRN03C.cbl:318-322), so a page is 20 written lines of any kind.
- **FR-1426** Account totals: written only when `WS-CURR-CARD-NUM NOT= TRAN-CARD-NUM` and this is
  not the first record (CBTRN03C.cbl:181-184); the paragraph writes the total line, resets
  `WS-ACCOUNT-TOTAL` and writes a rule line (CBTRN03C.cbl:306-317).
- **FR-1427** End of file writes the page total (which rolls into the grand total) and then the
  grand total (CBTRN03C.cbl:198-203).

### Preserved quirks

- **FR-1428 (Q-1)** At end of file `TRAN-AMT` still holds the last record read and is added to
  `WS-PAGE-TOTAL` and `WS-ACCOUNT-TOTAL` again (CBTRN03C.cbl:200-201). The closing page total and
  the grand total are therefore too large by the last in-range amount. Reproduced exactly.
- **FR-1429 (Q-2)** The final card's account total is never printed (no card change follows it).
- **FR-1430 (Q-3)** The date window is re-applied here even though the SORT step already applied it
  (CBTRN03C.cbl:173-178). Nothing checks that the `DATEPARM` dates match the SORT symbols.
- **FR-1431 (Q-4)** `NEXT SENTENCE` on an out-of-range record jumps past the whole
  `IF END-OF-FILE = 'N' … ELSE …` sentence, so when the *last* record of the extract is out of
  range the end-of-file branch never runs: no closing page total and no grand total.
- **FR-1432** Nothing is written before the first in-range detail line, so an extract with no
  in-range record yields an empty (zero-byte) report.

### Reference data and failures

- **FR-1441** `1500-A-LOOKUP-XREF` (CBTRN03C.cbl:484-492) runs once per card number, at the change
  of card, and supplies `XREF-ACCT-ID` for every detail line of that card.
- **FR-1442** `1500-B-LOOKUP-TRANTYPE` (:494-502) and `1500-C-LOOKUP-TRANCATG` (:504-511) run for
  every record.
- **FR-1443** Any of the three misses displays its message, sets `IO-STATUS` to 23, displays
  `FILE STATUS IS: NNNN0023` and abends (`9999-ABEND-PROGRAM`, :627-631,
  `CALL 'CEE3ABD' USING ABCODE(999)`), with the messages verbatim:
  - `INVALID CARD NUMBER : ` + the 16-character card number,
  - `INVALID TRANSACTION TYPE : ` + the 2-character type code,
  - `INVALID TRAN CATG KEY : ` + type code + 4-digit category code.
  Migrated through the shared `AbendService` (boundary B-01) as abend code `0999`, culprit
  `CBTRN03C`, reason `FILE STATUS IS: NNNN0023`.
- **FR-1444** An abend fails the step and the job. Every I/O error in the program takes the same
  route — open (`ERROR OPENING …`, :387, :405, :423, :441, :459, :477), read
  (`ERROR READING TRANSACTION FILE` :266, `ERROR READING DATEPARM FILE` :238), write
  (`ERROR WRITING REPTFILE` :354) and close (`ERROR CLOSING …`, :525, :543, :562, :580, :598, :616)
  — there is no recovery path. In the migrated job those file/VSAM conditions are database and file
  I/O failures, which fail the step the same way; the messages that have no counterpart in a
  database-backed step (open/close of VSAM files) are not synthesised.

### Console output

`START OF EXECUTION OF PROGRAM CBTRN03C` (:160), `Reporting from <start> to <end>` (:232-233),
`DISPLAY TRAN-RECORD` for every selected record (:180), `TRAN-AMT`/`WS-PAGE-TOTAL` at end of file
(:198-199) and `END OF EXECUTION OF PROGRAM CBTRN03C` (:215). These are operator diagnostics on
SYSOUT, not business output; the migrated job logs the equivalent through the Spring Batch job and
step listeners rather than reproducing the per-record `DISPLAY`, which would be a log line for every
transaction. This is a deliberate boundary decision (see the migration plan) and the only piece of
CBTRN03C output not reproduced verbatim.

### Control flow targets

None. CBTRN03C is a batch program: no `XCTL`, no `LINK`, no `CDEMO-TO-PROGRAM` / `CCARD-NEXT-PROG`
handling. Its only external call is `CEE3ABD` (:630).

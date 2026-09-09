# CBACT04C — functional requirement

Program type: batch COBOL, standalone (no CICS, no BMS map, no `XCTL`/`LINK`, no COMMAREA).
Invoked by `app/jcl/INTCALC.jcl` step `STEP15` with `PARM='2022071800'`.
Stream requirement ids in brackets refer to `../InterestCalculation_functional_requirement.md`.

## 1. Entry conditions

| # | Requirement | Source |
|---|---|---|
| P-1 | The program receives one linkage parameter, `PARM-DATE PIC X(10)`, preceded by the halfword length. It is used verbatim; it is never validated, parsed or compared. [FR-I2, FR-I13, quirk 7.6] | `CBACT04C.cbl:175-178` |
| P-2 | The program opens, in this order, TCATBALF (input, sequential), XREFFILE (I-O, random, alternate key), DISCGRP (input, random), ACCTFILE (I-O, random) and TRANSACT (output, sequential). A non-`00` status on any open abends with that file's message. [FR-I21] | `CBACT04C.cbl:182-186,234-323` |
| P-3 | `START OF EXECUTION OF PROGRAM CBACT04C` is displayed before any file is opened. [FR-I20] | `CBACT04C.cbl:181` |

## 2. Main loop

| # | Requirement | Source |
|---|---|---|
| P-4 | TCATBALF is read sequentially in key order until status `10` (end of file). Any other non-`00` status displays `ERROR READING TRANSACTION CATEGORY FILE` and abends. [FR-I3, FR-I21] | `CBACT04C.cbl:188-192,325-348` |
| P-5 | Every record read is counted and displayed. [FR-I20] | `CBACT04C.cbl:192-193` |
| P-6 | When the account id differs from the previous record's, the previous account is updated (P-12) unless this is the first record of the run, the running interest total is reset to zero, the new account id is remembered, the account row is read by account id and the cross-reference row is read by the account-id alternate key. [FR-I4, FR-I15] | `CBACT04C.cbl:194-206` |
| P-7 | The account read failing (`INVALID KEY` or any non-`00` status) displays `ACCOUNT NOT FOUND: ` with the key, then `ERROR READING ACCOUNT FILE`, then abends. [FR-I17] | `CBACT04C.cbl:372-391` |
| P-8 | The cross-reference read failing displays `ACCOUNT NOT FOUND: ` with the account id, then `ERROR READING XREF FILE`, then abends. [FR-I18] | `CBACT04C.cbl:393-413` |

## 3. Rate lookup

| # | Requirement | Source |
|---|---|---|
| P-9 | The DISCGRP key is built from the *account's* group id and the *balance record's* transaction type and category codes, and read randomly. [FR-I5] | `CBACT04C.cbl:210-213,416` |
| P-10 | Status `23` (not found) displays `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, replaces only the group id with `'DEFAULT'` (space-padded to 10) and reads once more. A status other than `00`/`23` on the first read displays `ERROR READING DISCLOSURE GROUP FILE` and abends; a non-`00` status on the retry displays `ERROR READING DEFAULT DISCLOSURE GROUP` and abends. [FR-I6, FR-I7] | `CBACT04C.cbl:415-460` |
| P-11 | Interest is computed only when the resulting rate is non-zero; otherwise the balance record is skipped with no output. [FR-I8, quirk 7.4] | `CBACT04C.cbl:214-217` |

## 4. Calculation, output and posting

| # | Requirement | Source |
|---|---|---|
| P-12 | Monthly interest = `TRAN-CAT-BAL × DIS-INT-RATE ÷ 1200`, stored in `PIC S9(09)V99` by an unrounded `COMPUTE`, i.e. truncated toward zero to two decimals; it is added to the account's running total. [FR-I9, FR-I11, quirk 7.3] | `CBACT04C.cbl:462-467` |
| P-13 | Each computed amount writes one `CVTRA05Y` record to the sequential TRANSACT output with the field values of [FR-I12] and both timestamps set to the current clock time in DB2 format [FR-I14]. A write status other than `00` displays `ERROR WRITING TRANSACTION RECORD` and abends. [FR-I19] | `CBACT04C.cbl:473-515,613-626` |
| P-14 | The transaction id is `PARM-DATE` concatenated with a 6-digit run-wide counter incremented before each write. [FR-I13] | `CBACT04C.cbl:173,474-480` |
| P-15 | Updating an account adds its total interest to `ACCT-CURR-BAL`, sets `ACCT-CURR-CYC-CREDIT` and `ACCT-CURR-CYC-DEBIT` to zero and rewrites the row; a rewrite status other than `00` displays `ERROR RE-WRITING ACCOUNT FILE` and abends. [FR-I15] | `CBACT04C.cbl:350-370` |
| P-16 | **Quirk (preserved):** the account being accumulated when end of file is reached is never updated, because the `ELSE PERFORM 1050-UPDATE-ACCOUNT` branch is unreachable under `PERFORM UNTIL END-OF-FILE = 'Y'`. Its transactions are written; its balance and cycle totals are left untouched. [FR-I16, quirk 7.1] | `CBACT04C.cbl:188-191,219-220` |
| P-17 | `1400-COMPUTE-FEES` is empty: no fee is computed, no fee transaction is written. [FR-I22] | `CBACT04C.cbl:518-520` |

## 5. Termination

| # | Requirement | Source |
|---|---|---|
| P-18 | All five files are closed in the order TCATBALF, XREFFILE, DISCGRP, ACCTFILE, TRANSACT; a non-`00` close status displays that file's close message and abends. | `CBACT04C.cbl:224-228,522-611` |
| P-19 | `END OF EXECUTION OF PROGRAM CBACT04C` is displayed and the program returns normally. [FR-I20] | `CBACT04C.cbl:230-232` |
| P-20 | Any abend displays `ABENDING PROGRAM`, preceded by `FILE STATUS IS: NNNN` and the four-character file status, and terminates the step abnormally (ABCODE 999). [FR-I21] | `CBACT04C.cbl:628-648` |

## 6. Messages (verbatim)

See `../InterestCalculation_analysis.md` §6 for the full table, including
`ERROR OPENING DALY REJECTS FILE`, which the DISCGRP open path emits despite naming a different file
(quirk 7.2) and is reproduced unchanged.

# POSTTRAN — Tesco Clubcard Nightly Transaction Posting

> *Every little helps — especially when it's posted to the right account.*

The end-of-day till-roll consolidation job: once the tills close, every receipt of the day is checked, posted to the central ledger, added to the customer's running balance and rolled up into spend-per-category totals. Anything that doesn't check out goes into the rejects basket for the morning shift.

This page uses a Tesco Clubcard theme to explain the CardDemo `POSTTRAN` batch job. Every technical detail comes from the CardDemo source. CardDemo is a credit-card system: the Tesco terms are analogies, not real Tesco systems or data.

## 1. Overview

| Item | Value |
| :--- | :---- |
| Job name | `POSTTRAN` ([`app/jcl/POSTTRAN.jcl`](../app/jcl/POSTTRAN.jcl)) |
| Step / program | `STEP15 EXEC PGM=CBTRN02C` ([`app/cbl/CBTRN02C.cbl`](../app/cbl/CBTRN02C.cbl)) |
| Load library | `AWS.M2.CARDDEMO.LOADLIB` |
| JCL description | "Process and load daily transaction file and create transaction category balance and update transaction master vsam" |
| README role | "Core transaction processing job" |
| `scripts/run_full_batch.sh` | "Job 12 : Core processing job" |
| Return codes | `0` = everything posted; `4` = at least one record rejected; abend `999` (via `CEE3ABD`) on any file I/O error |

**Where it sits in the nightly schedule** (README "Running Batch Jobs" table):

```
CLOSEFIL ─► data refresh (ACCTFILE, CARDFILE, XREFFILE, CUSTFILE, TRANBKP,
            TRANEXTR*, TRANCATG, TRANTYPE, DISCGRP, TCATBALF, DUSRSECJ)
         ─► POSTTRAN ─► INTCALC ─► TRANBKP ─► COMBTRAN ─► CREASTMT ─► TRANIDX ─► OPENFIL
                                                            (* optional Db2 module)
```

**The Tesco analogy.** The store shuts its doors (`CLOSEFIL`), the back office restocks its reference data (the refresh jobs), and then the till rolls from every checkout are consolidated in one pass: each receipt is matched to a Clubcard, checked against the customer's account, posted to the central ledger and rolled into the customer's Groceries / Fuel / Clothing totals. Only after that do the follow-on jobs run: interest (`INTCALC`), back-ups (`TRANBKP`), merging (`COMBTRAN`), statements (`CREASTMT`), re-indexing (`TRANIDX`), and finally reopening the doors (`OPENFIL`).

## 2. What it does

`CBTRN02C` opens all six files, then loops over `DALYTRAN` one record at a time until end of file (file status `10`). For each receipt:

1. **Read the next till receipt** — `1000-DALYTRAN-GET-NEXT` reads the next `DALYTRAN` record and adds 1 to the processed count.
   *Tesco:* pull the next receipt off today's till roll.

2. **Check the Clubcard number against the customer registry** — `1500-A-LOOKUP-XREF` reads `XREFFILE` by the 16-byte card number to find the account ID. No match → reason **100** `INVALID CARD NUMBER FOUND`.
   *Tesco:* scan the Clubcard at the customer service desk; if the registry has never heard of it, the receipt is set aside.

3. **Check the customer's credit account** — `1500-B-LOOKUP-ACCT` reads `ACCTFILE` with the account ID from step 2:
   - account missing → reason **101** `ACCOUNT RECORD NOT FOUND`
   - `ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT` is above `ACCT-CREDIT-LIMIT` → reason **102** `OVERLIMIT TRANSACTION`
   - account expiry date is earlier than the date of the transaction's original timestamp (`DALYTRAN-ORIG-TS(1:10)`) → reason **103** `TRANSACTION RECEIVED AFTER ACCT EXPIRATION`

   Both the 102 and 103 checks run, so if both fail the trailer shows 103.
   *Tesco:* make sure the account exists, the basket wouldn't push the customer over their limit, and the card hadn't expired before they shopped.

4. **Post the receipt** — `2000-POST-TRANSACTION` copies the daily record into the transaction layout, stamps `TRAN-PROC-TS` with the current time in Db2 format, and then:
   - `2700-UPDATE-TCATBAL` adds the amount to the `TCATBALF` balance for key (account, type code, category code). If no record exists yet (status `23`), it creates one.
   - `2800-UPDATE-ACCOUNT-REC` adds the amount to `ACCT-CURR-BAL`, and to `ACCT-CURR-CYC-CREDIT` if the amount is zero or positive, or to `ACCT-CURR-CYC-DEBIT` if it is negative. Then it rewrites the account.
   - `2900-WRITE-TRANSACTION-FILE` writes the record to `TRANFILE`.

   *Tesco:* write the receipt into the central ledger, update the customer's balance, and add the spend to their Groceries / Fuel / etc. totals.

5. **Bag up the rejects and close the tills** — any receipt with a non-zero reason code is written by `2500-WRITE-REJECT-REC` to `DALYREJS`: the original 350-byte record plus an 80-byte trailer (4-digit reason + 76-character description), and the reject count goes up by 1. At end of file, all files are closed, `TRANSACTIONS PROCESSED :` and `TRANSACTIONS REJECTED  :` are printed to SYSOUT, and the return code is set to **4** if anything was rejected.
   *Tesco:* rejected receipts go into today's labelled rejects basket, and the shift manager signs off with a count of what was posted and what wasn't.

## 3. JCL file allocations

| DD name | Dataset (DSN) | DISP / attributes | How `CBTRN02C` opens it | Tesco analogy |
| :------ | :------------ | :---------------- | :---------------------- | :------------ |
| `STEPLIB` | `AWS.M2.CARDDEMO.LOADLIB` | `SHR` | — (program library) | The staff handbook the night team works from |
| `DALYTRAN` | `AWS.M2.CARDDEMO.DALYTRAN.PS` | `SHR`, sequential | `INPUT` | Today's till receipts — the day's till roll from every checkout |
| `TRANFILE` | `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` | `SHR`, VSAM KSDS, key = 16-byte tran ID | `OUTPUT` (random) | The central transaction ledger at head office |
| `XREFFILE` | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` | `SHR`, VSAM KSDS, key = 16-byte card number | `INPUT` (random) | The Clubcard → customer lookup (card number → customer ID + account ID) |
| `ACCTFILE` | `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | `SHR`, VSAM KSDS, key = 11-digit account ID | `I-O` (random) | Customer credit accounts — limits, balances, expiry dates |
| `TCATBALF` | `AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS` | `SHR`, VSAM KSDS, 17-byte composite key | `I-O` (random) | Spend-per-category totals per customer, such as Groceries or Fuel |
| `DALYREJS` | `AWS.M2.CARDDEMO.DALYREJS(+1)` | `(NEW,CATLG,DELETE)`, `SYSDA`, `RECFM=F`, `LRECL=430`, `SPACE=(CYL,(1,1),RLSE)` | `OUTPUT` | The rejected-receipts basket — a fresh, dated basket (GDG generation) each night |
| `SYSPRINT` / `SYSOUT` | `SYSOUT=*` | — | `DISPLAY` output | The shift manager's end-of-night summary |

The `DALYREJS` GDG base is defined by [`app/jcl/DALYREJS.jcl`](../app/jcl/DALYREJS.jcl) with `LIMIT(5) SCRATCH`, so the five most recent rejects baskets are kept.

## 4. Record layout highlights

**Daily transaction / posted transaction** (350 bytes — `FD-TRAN-RECORD` / `FD-TRANFILE-REC`, copybook `CVTRA06Y` for the daily file):

| Bytes | Field | Notes |
| :---- | :---- | :---- |
| 1–16 | `DALYTRAN-ID` / `FD-TRANS-ID` `X(16)` | Transaction ID — the `TRANFILE` KSDS key (the receipt number) |
| 17–350 | Data `X(334)` | Type `X(2)`, category `9(4)`, source `X(10)`, description `X(100)`, amount `S9(9)V99`, merchant ID `9(9)`, merchant name/city `X(50)` each, ZIP `X(10)`, card number `X(16)`, original and processed timestamps `X(26)` each, filler `X(20)` |

**Transaction category balance** (`TCATBALF`, 50 bytes, copybook `CVTRA01Y`):

| Bytes | Field | Tesco analogy |
| :---- | :---- | :------------ |
| 1–11 | `TRANCAT-ACCT-ID` `9(11)` | Which customer account |
| 12–13 | `TRANCAT-TYPE-CD` `X(02)` | Type of spend (e.g. purchase vs. refund) |
| 14–17 | `TRANCAT-CD` `9(04)` | Category (Groceries, Fuel, …) |
| 18–50 | `TRAN-CAT-BAL` `S9(9)V99` + filler `X(22)` | Running total for that bucket |

The key is bytes 1–17: account ID (11) + type code (2) + category code (4).

**Reject record** (`DALYREJS`, 430 bytes — matches `LRECL=430` in the JCL):

| Bytes | Field | Contents |
| :---- | :---- | :------- |
| 1–350 | `REJECT-TRAN-DATA` `X(350)` | The original daily transaction record, unchanged |
| 351–354 | `WS-VALIDATION-FAIL-REASON` `9(04)` | `0100`, `0101`, `0102` or `0103` |
| 355–430 | `WS-VALIDATION-FAIL-REASON-DESC` `X(76)` | e.g. `OVERLIMIT TRANSACTION` |

Bytes 351–430 together are the 80-byte validation trailer.

## 5. Operational notes

- **Scheduling.** [`scripts/run_full_batch.sh`](../scripts/run_full_batch.sh) submits the full chain over FTP/JES (`tnftp localhost 2121`, `quote site filetype=JES`). `POSTTRAN` is "Job 12 : Core processing job". It runs after the "Data Refresh Complete" marker, and the script waits 10 seconds before submitting `INTCALC`. [`scripts/run_posting.sh`](../scripts/run_posting.sh) is a shorter posting-only chain: `CLOSEFIL` → `ACCTFILE` → `TCATBALF` → `TRANBKP` → `POSTTRAN` → `TRANIDX` → `OPENFIL`.
  - The script does not submit `CREASTMT`, `TRANCATG` or `TRANEXTR` (all listed in the README table). Its job numbers skip 10 and repeat 15.
- **Checking the rejects basket.** A return code of **4** means at least one receipt was rejected. Look at `AWS.M2.CARDDEMO.DALYREJS(+1)` within the run (it is `(0)` once the job has ended) and read the reason code in bytes 351–354. The `TRANSACTIONS PROCESSED` / `TRANSACTIONS REJECTED` counts are in the step's SYSOUT.
- **Upstream dependencies.** `CLOSEFIL` first, then the refresh jobs that load `ACCTFILE`, `XREFFILE` and `TCATBALF`, plus `TRANBKP`, which deletes and redefines `TRANSACT.VSAM.KSDS`. The redefine matters because `CBTRN02C` opens `TRANFILE` as `OUTPUT`. The `DALYREJS` GDG base must already exist (`DALYREJS.jcl`), and `DALYTRAN.PS` must hold the day's receipts.
- **Downstream dependencies.** `INTCALC` reads the `TCATBALF` balances that `POSTTRAN` just updated. `TRANBKP` → `COMBTRAN` back up and merge the posted ledger. `CREASTMT` builds statements from it. `TRANIDX` rebuilds the alternate index on the transaction file. `OPENFIL` hands the files back to CICS.
- **CICS files must be closed.** `POSTTRAN` updates `ACCTDATA` and writes `TRANSACT` as a batch job. `CLOSEFIL` issues `CEMT SET FIL(...) CLO` for `TRANSACT`, `CCXREF`, `ACCTDAT`, `CXACAIX` and `USRSEC` before the chain runs, and `OPENFIL` reopens them afterwards. Don't run `POSTTRAN` while the online region still has those files open (in store terms: don't count the tills while customers are still at the checkouts).
- **Failure handling.** Any unexpected file status (open, read, write or rewrite) prints a message, shows the status, and abends with code **999**. After an abend, fix the cause and rerun from the refresh jobs: `TRANFILE` is opened `OUTPUT`, and `ACCTFILE` / `TCATBALF` may already be partly updated. Because `DALYREJS` is `(NEW,CATLG,DELETE)`, an abend does not leave a half-written generation catalogued.

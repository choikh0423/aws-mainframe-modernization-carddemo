# Bill Payment (CB00 / COBIL00C) — end-to-end migration plan

Scope: CardDemo Main Menu option 10, **Bill Payment**: program `app/cbl/COBIL00C.cbl`,
mapset `app/bms/COBIL00.bms`, symbolic map `app/cpy-bms/COBIL00.CPY`.
Files touched by the legacy program: `ACCTDAT` (READ UPDATE / REWRITE),
`CXACAIX` (READ via the CCXREF alternate index), `TRANSACT` (STARTBR / READPREV / ENDBR / WRITE).

## Deliverables (all under `migration/bill-payment/`)

| Folder | What it is |
| --- | --- |
| `legacy-runtime/` | Runs the **unmodified** COBIL00C source under GnuCOBOL. A small translator rewrites each `EXEC CICS … END-EXEC` into a `CALL 'CICSRT'`; a C bridge hands the call to a Python "CICS region" that keeps VSAM files in SQLite, does BMS SEND/RECEIVE MAP against the real `COBIL00.bms`, keeps the COMMAREA between pseudo-conversational tasks, and logs every CICS command. A browser page renders the 24x80 3270 screen. |
| `backend/` | Spring Boot 3 / Java 17 / H2 port of COBIL00C (`BillPaymentService`), REST in place of BMS + COMMAREA. |
| `frontend/` | React screen for CB00 (same fields, messages, ENTER / F3 / F4). |
| `compare` (served by the legacy runtime) | Side-by-side page: real CICS screen on the left, migrated React screen on the right, one "drive both" bar, and a parity panel that replays scripted scenarios on both sides and diffs the screen fields and the data written (ACCTDAT balance, new TRANSACT row). |
| `docs/` | This plan, the COBIL00C analysis (flow, rules, every message, legacy quirks) |

Both sides seed from the same source files: `app/data/ASCII/acctdata.txt`, `cardxref.txt`,
and `dailytran.txt` (TRANSACT as it looks after POSTTRAN has posted the daily file).

## Parity rules
- Screen texts and messages are kept character for character (e.g. `Payment successful.  Your Transaction ID is …` keeps its double space).
- Business rules mirrored: pay-in-full only, `You have nothing to pay...` when balance <= 0, new TRAN-ID = highest TRANSACT key + 1,
  type `02`, category `0002`, source `POS TERM`, merchant `999999999 / BILL PAYMENT / N/A / N/A`, balance reduced by the full amount.
- Legacy quirks that are reachable with the seed data are reproduced; unreachable defects are documented, not copied (see analysis).

## Verification
- Backend: JUnit + MockMvc tests on every message path; parity harness (`legacy-runtime/parity.py`) runs the same scenarios against the real COBOL and the Java API and fails on any diff.
- CI workflow for backend tests, frontend build, and the COBOL parity run.
- Recorded UI run of the side-by-side page.

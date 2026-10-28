# mftb-demo — CardDemo modernization with the mftb mainframe toolbox

Analysis of this repository's `app/` estate with [`COG-GTM/devin-mainframe-cobol-toolbox`](https://github.com/COG-GTM/devin-mainframe-cobol-toolbox), pinned at commit **`419573efe3a9a4fe742e2ad62d8085ff04e71a8c`** (mftb 0.2.3). Nothing under `app/` or `migration/` was changed.

| File | What it is |
|---|---|
| `DEMO.html` | Standalone walkthrough (no network needed): problem → estate → module → chain → transaction → plan → scanner vs Devin. Open it from this folder so the report links resolve. |
| `DEMO-SCRIPT.md` | 5–10 minute talk track with click path |
| `MODERNIZATION-PLAN.md` | Plan built only from the store; every claim cited |
| `store/` | The mftb store: scanner JSON, agent-written inputs, HTML reports |
| `screenshots/` | The four report pages |

## Output locations

| Report | Path |
|---|---|
| Estate | `store/reports/estate.html` |
| Module `CARDDEMO` (also `CDAUTHFR`, `CDTRNTYP`, `CDVSAMMQ`) | `store/reports/<MODULE>/<MODULE>-report.html` |
| Batch chain, CA 7 `CARDDEMO` | `store/reports/CARDDEMO/chains/chain-CARDDEMO.html` |
| Batch chain, Control-M `MONTHLY-InterestCalculation` | `store/reports/CARDDEMO/chains/chain-MONTHLY-InterestCalculation.html` |
| Online transaction `CAUP` | `store/reports/CARDDEMO/online/online-CAUP.html` |

Files written by Devin (the agentic layer; everything else in `store/` is scanner output):
`store/modules.json`, `store/CARDDEMO/annotations.json`, `store/CARDDEMO/descriptions.json` (descriptions/summaries; the worklist itself is scanner-seeded), `store/CARDDEMO/chainnotes.json`, `store/CARDDEMO/onlinenotes.json`.

## Reproduce

Requires Python 3 only (stdlib). Run from the root of this repository — not inside the toolbox — so the store lands here.

```bash
git clone https://github.com/COG-GTM/devin-mainframe-cobol-toolbox ~/mftb
git -C ~/mftb checkout 419573efe3a9a4fe742e2ad62d8085ff04e71a8c
export PYTHONPATH=~/mftb
S=mftb-demo/store

# 1. Estate (entry point: estate-analysis)
python3 -m mftb scan-estate app --store $S
#    store/modules.json is written by the module-classification skill (Devin); the committed one is used as-is
python3 -m mftb modules --store $S --check
for M in CARDDEMO CDAUTHFR CDTRNTYP CDVSAMMQ; do python3 -m mftb scan --module $M --from-catalog --store $S; done
python3 -m mftb report-estate --store $S

# 2. Module CARDDEMO (entry point: module-inventory-analysis)
python3 -m mftb scan-profile     --module CARDDEMO --store $S
python3 -m mftb scan-dyncalls    --module CARDDEMO --store $S   # reads annotations.json
python3 -m mftb scan-connections --module CARDDEMO --store $S
python3 -m mftb descriptions     --module CARDDEMO --store $S --check --strict
python3 -m mftb report           --module CARDDEMO --store $S

# 3. Batch chains (entry point: batch-chain-analysis; reads chainnotes.json)
python3 -m mftb scan-chains --module CARDDEMO --store $S CARDDEMO MONTHLY-InterestCalculation

# 4. Online transaction (entry point: online-transaction-analysis; reads onlinenotes.json)
python3 -m mftb scan-online --module CARDDEMO --store $S --transaction CAUP

# 5. Final re-render
python3 -m mftb report --module CARDDEMO --store $S
python3 -m mftb report-estate --store $S
```

Checks that must pass: `modules --check` → `4 module(s), 8 shared, 0 unplaced - 203 of 203 catalogued artifact(s) placed`; `descriptions --check --strict` → `71 of 71 subject(s) written, 7 of 7 business-logic summary/summaries written` and exit 0.

## Why these selectors

- **Modules.** `app/` holds one core application and three optional sub-applications, each with its own `cbl/`, `jcl/`, `csd/` and `ddl/` folders (`app/app-authorization-ims-db2-mq`, `app/app-transaction-type-db2`, `app/app-vsam-mq`). They use different technology (IMS/DB2/MQ, DB2, MQ) and connect to the core only through shared copybooks and menu XCTLs (`store/estate.json` `connections`), so they are separate modules: `CARDDEMO`, `CDAUTHFR`, `CDTRNTYP`, `CDVSAMMQ`. The 8 copybooks used by more than one module are `shared`.
- **Batch chain: CA 7 table `CARDDEMO`.** It schedules `POSTTRAN` (`app/scheduler/CardDemo.ca7:70`), which runs `CBTRN02C` (`app/jcl/POSTTRAN.jcl:23`) — the program that validates the day's transactions and posts them to accounts and category balances (`app/cbl/CBTRN02C.cbl:370-580`) — and `CREASTMT` (`app/scheduler/CardDemo.ca7:468`), the customer statements. The monthly interest chain (`INTCALC` at `app/scheduler/CardDemo.controlm:69`) consumes what `POSTTRAN` writes (`TCATBALF`) and is analysed as the secondary chain.
- **Online transaction: `CAUP`** (`COACTUPC`, account update). It is the only core screen that rewrites two masters (account and customer) in one task (`app/cbl/COACTUPC.cbl:4065`, `:4085`) over files defined `RECOVERY(NONE)` (`app/csd/CARDDEMO.CSD:9-10`), so its behaviour is the riskiest to reproduce.

## Known gaps

- Descriptions are written for `CARDDEMO` (the module reported on); the three extension modules still carry their description worklists (`descriptions-unwritten` finding).
- See `MODERNIZATION-PLAN.md` §6 and §8 for open findings and the evidence requests a customer must answer.

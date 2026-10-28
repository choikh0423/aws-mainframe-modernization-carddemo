# Demo script (≈8 minutes)

Open `mftb-demo/DEMO.html` in a browser (from a local clone; no network needed). Use the left navigation.

| Time | Click | Say |
|---|---|---|
| 0:00 | **1. The problem** | "CardDemo is a small but realistic estate: CICS screens, nightly and monthly batch, VSAM, DB2, IMS and MQ. Before anyone proposes a target, we need an inventory nobody has to trust on faith. The rule today: every fact cites a file and line, and anything we can't prove stays an open question." Point at the six headline numbers and the three labels: scan, agent, recommendation. |
| 1:00 | **2. Estate map** → *Open estate report* | "The scanner catalogued all 240 files. Devin proposed four modules — the core app and three extensions — and the toolbox *checks* that every one of the 203 artifacts is placed exactly once." Click the `CARDDEMO` bubble; show the connections table (shared copybooks, menu XCTLs). |
| 2:30 | **3. Module report** → *Open module report* | Programs tab: point at the *What it does* column, badged `agent`, with a *key points* link — "Devin wrote these from source; the citation is in the store and `--check --strict` fails if one is missing." Findings tab: filter by severity *high* — four, three of them environment questions only the customer can answer. Profile tab: ASSUMED fields. |
| 4:00 | **4. Batch chain report** → *Open chain report* | "This is the daily posting cycle that runs POSTTRAN. Jobs in scheduler order, every step's reads and writes, and the data lineage." Show the asks: Devin closed five with cited facts and added one — nothing in the estate produces the daily transaction file. "That's a question for you, not a guess." Optionally open the monthly interest chain: fees are a stub in the code. |
| 5:30 | **5. Transaction report** → *Open transaction report* | "Account update: sign-on, menu, then COACTUPC reads the account, customer and cross-reference and rewrites two files in one task — and those files have no CICS recovery. That becomes a design requirement: one database transaction in the target." |
| 6:30 | **6. Modernization plan** | Walk the five waves: "the order is not opinion — it follows the graphs: shared layouts, then data, then batch, then screens, then extensions." Open `MODERNIZATION-PLAN.md` to show citations on every row. Read the top evidence requests. |
| 7:30 | **7. Scanner vs Devin** | "Deterministic scanners do the counting and tracing and never guess. Devin does the judgment — module boundaries, descriptions, closing asks — and only with a citation. Re-run the README commands and you get the same store." |

Backup if a report link does not open: the screenshot under each section is the same page; *Show inline* embeds the report in the demo page.

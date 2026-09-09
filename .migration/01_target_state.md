# 01 — Target state (pointer)

The authoritative target-state document is committed at:

**`docs/migration/CardDemo_target_state.md`** — repo `choikh0423/aws-mainframe-modernization-carddemo`,
branch `devin/1788976673-carddemo-full-migration`.

It carries the CORE profile, the ONLINE / BATCH / SUBTRANSACTION profiles, the DATA + BOUNDARY profile,
the confirmed topology, and the STOP A decision table (D-1 … D-5, confirmed by Kyu on 2026-09-09).

Profile applicability by stream type:

| Stream process type | Profiles that apply |
|---|---|
| ONLINE | CORE + ONLINE + DATA/BOUNDARY |
| BATCH | CORE + BATCH + DATA/BOUNDARY |
| SUBTRANSACTION | CORE + SUBTRANSACTION + DATA/BOUNDARY |

If a stream's profile is missing or marked N/A, send it back to `!mf_ingest_target_state` before planning.

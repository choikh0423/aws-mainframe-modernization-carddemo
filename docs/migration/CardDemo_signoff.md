# CardDemo full-estate migration — sign-off (STOP E)

Date: 2026-09-09
Branch under sign-off: `devin/carddemo-integration`
Target of the requested merge: `main` (**not performed — awaiting authorization**)
Scope: the entire CICS + Batch estate, including the DB2, IMS and MQ add-on modules (decision D-4)

---

## 1. Verdict

The independent audit session — which performed none of the migration — re-checked the remediated branch
and recorded **`devin/carddemo-integration` is fit to merge into `main`** (`CardDemo_independent_audit.md`,
section `Re-audit — 2026-09-09`, PR #61).

Finding trajectory:

| Round | BLOCKER | MAJOR | MINOR | Verdict |
|---|---|---|---|---|
| Audit (PR #57) | 2 | 5 | 3 | NOT fit to merge |
| Re-audit (PR #61) | 0 | 1 (A-11) | 3 (A-12, A-13, A-14) | Fit to merge |
| After this round (PRs #62, #63 and this commit) | 0 | 0 | 0 open | — |

All fourteen findings are now closed. A-14 is closed **as documented** rather than as code: see §6.

## 2. Coverage

```
44 COBOL programs = 42 assigned to streams + 1 shared (CSUTLDTC) + 1 unreachable (CBTRN01C)
```

- 20 streams, S-01…S-20. S-04 was already migrated and was folded in (D-6); S-18 is replaced by the
  Phase-1 schema and seeds; S-19 and S-20 executed inside S-08 and S-09.
- 119 raw `EXEC PGM=` occurrences, reconciled in `CardDemo_inventory.md` §9: 96 carry no business logic
  into a relational target, the 23 that do are each represented by at least one of the target's 21
  Spring Batch jobs and 40 step definitions (D-10).
- 17 core BMS maps + 4 add-on maps, 24 CICS transactions, 4 IMS DBDs, 4 IMS PSBs, 5 DB2 DDL tables.
- The re-audit reproduced this arithmetic independently.

## 3. Evidence

| Check | Result |
|---|---|
| Backend `mvn -B test` (H2 profile) | 926 tests, 0 failures, 0 errors, 0 skipped |
| Frontend `npm test -- --watchAll=false` | 14 suites, 223 tests passed (212 at the re-audit, plus the A-11/A-12 boundary-literal tests) |
| Frontend `npm ci` + `CI=true npm run build` | clean |
| CI `carddemo-ci.yml` | green on every merged PR; now runs the frontend tests as well as the backend tests and the build |
| Lane discipline | no file under `app/**` or `migration/transaction-management/**` modified; `V1` baseline migration untouched |
| UI evidence | the CT02 add-transaction flow was driven end to end in a browser and recorded (sign-on → list → add → readback → validation failure); no defects |

PR trail: foundation #40; streams #41–#56; audit #57; remediation #58, #59, #60, #62, #63; re-audit #61.

## 4. Boundaries

15 registered, every one decided (`.migration/04_boundary_register.md`): **11 implemented, 4 deferred.**

| ID | Deferred because |
|---|---|
| B-04 | `MVSWAIT`'s timer is a scheduler concern (see B-14); the control-card contract is preserved so the chain stays readable |
| B-11 | `COCRDSEC` has no source anywhere in the repository, so CDV1's security view cannot be migrated from source |
| B-13 | `TXT2PDF` is a licensed TSO/REXX load library that is not in the repository; statements render as text and HTML, PDF is left to a target-side renderer |
| B-14 | No Control-M/CA-7 integration was built; migrated jobs expose exit codes only, and the target scheduler is a customer decision |

## 5. Residual risks

1. **`COCRDSEC` is missing (R-1, B-11).** CDV1 ships without its security view. This is a source gap, not a
   migration gap, and it is the one functional hole in the estate.
2. **Parity is source- and fixture-based (D-8).** No mainframe, CICS, DB2, IMS or MQ runtime exists in this
   engagement, so IMS and MQ paths are proven against copybook contracts and the exported sample data in
   `app/data`, not against live middleware.
3. **PostgreSQL is proven locally, not in CI (D-2).** CI runs the H2 profile; the production dialect is
   exercised outside CI.
4. **Scheduling is unbuilt (B-14).** Job dependencies are documented but not automated.

## 6. Known deviations recorded rather than fixed

| Ref | Deviation |
|---|---|
| D-9 | `PRTCATBL`'s `SORT OUTREC` is wider than the job's declared `LRECL=40`. The migration follows the `OUTREC` layout and does not truncate; the contradiction is a latent defect in the legacy JCL |
| D-11 (A-14) | `V900` drops and re-adds `pos_entry_mode` because no single statement casts `CHAR`→`SMALLINT` on both PostgreSQL and H2. Lossless here — the table has no seed rows — but a deployment against a populated `auth_fraud` must use the `USING pos_entry_mode::smallint` form recorded in D-11 |
| COUSR03C | A generic delete failure reports `Unable to Update User...`. Legacy quirk, preserved deliberately |
| COTRTUPC | Does not light the `F6=Add` caption. Legacy quirk, preserved deliberately |

## 7. Authorization requested

Merging `devin/carddemo-integration` into `main` requires Kyu's explicit authorization. `main` has not been
touched at any point in this engagement.

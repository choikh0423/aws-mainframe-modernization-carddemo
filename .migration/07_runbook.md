# 07 — Runbook (commands actually executed in this environment)

## Toolchain present
| Tool | Version / note |
|---|---|
| Java | 17 (`java -version`) |
| Maven | 3.6.3 — installed this session via `sudo apt-get install -y maven` |
| Node | 20 via nvm — `source ~/.nvm/nvm.sh && nvm use 20` |
| Docker | present |
| GnuCOBOL | `cobc` present (source parsing/compile checks only; no CICS/DB2/IMS runtime) |

## Maven Central mirror (required)
`repo.maven.apache.org` returns HTTP 429 from this network. `~/.m2/settings.xml` mirrors central to
`https://maven-central.storage-download.googleapis.com/maven2`. Without it every Maven build fails on
artifact download. **This lives outside the repo** and must be added to the environment blueprint so
future sessions inherit it.

## Reference backend — build and test
```bash
cd migration/transaction-management/backend
mvn -q test          # 102 tests, all green (verified 2026-09-09)
```

## Reference frontend — build
```bash
source ~/.nvm/nvm.sh && nvm use 20
cd migration/transaction-management/frontend
npm install --no-audit --no-fund
CI=false npm run build   # green (verified 2026-09-09)
```

## PostgreSQL (real data target, D-2)
```bash
docker run -d --name carddemo-pg -e POSTGRES_PASSWORD=carddemo \
  -e POSTGRES_USER=carddemo -e POSTGRES_DB=carddemo -p 5432:5432 postgres:16
docker exec carddemo-pg pg_isready -U carddemo
```
H2 stays the CI/test profile; nothing in CI depends on this container.

## Inventory call graph
```bash
python3 scripts/migration/build_call_graph.py > /tmp/cg.json
```
Emits `programs`, `edges` (CALL/XCTL/LINK), `dynamic_calls`, `jcl_steps` (`EXEC PGM=`) and
`csd_transactions` across `app/cbl` and the three add-on module trees. It is a **helper**, not evidence:
every fact it produces was re-read in source before entering the inventory.

## Not yet established
- Batch execution of the migrated jobs (no Spring Batch app exists yet).
- Any CICS / DB2 / IMS / MQ runtime — none is available; see `06_decisions.md` D-8.

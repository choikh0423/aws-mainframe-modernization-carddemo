#!/usr/bin/env bash
# Build everything and start: Spring Boot on :8080, CICS region + compare page on :8090.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"

(cd "$here/legacy-runtime" && ./build.sh)
(cd "$here/backend" && mvn -B -q package -DskipTests)
(cd "$here/frontend" && { [ -d node_modules ] || npm install --no-audit --no-fund; } && CI=false npm run build)

java -jar "$here/backend/target/bill-payment-1.0.0.jar" &
java_pid=$!
trap 'kill $java_pid 2>/dev/null' EXIT
until curl -sf http://localhost:8080/api/bill-payment >/dev/null; do sleep 1; done

echo "Open http://localhost:8090/ for the side-by-side comparison"
cd "$here/legacy-runtime" && python3 -m region.server --java http://localhost:8080

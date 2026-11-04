#!/usr/bin/env bash
# Three real red probes: two changed expectations in copies, then disabled Logger deduplication in a copied build.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MVN="${MVN:-mvn}"
ARTIFACTS="$ROOT/services/api-tests/target/mutations"
mkdir -p "$ARTIFACTS"
WORK="$(mktemp -d "$ROOT/services/api-tests/target/mutation-work.XXXXXX")"
COMPOSE=(docker compose -p smp-api-tests -f "$ROOT/docker-compose.api-tests.yaml")
RESTORE=false
cleanup() {
  status=$?
  trap - EXIT
  if [[ "$RESTORE" == true ]]; then
    "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait --wait-timeout 180 transaction-logger \
      > "$ARTIFACTS/restore.log" 2>&1 || status=1
  fi
  rm -rf "$WORK"
  exit "$status"
}
trap cleanup EXIT
mkdir -p "$WORK/services/api-tests"
cp services/pom.xml services/checkstyle.xml "$WORK/services/"
cp services/api-tests/pom.xml "$WORK/services/api-tests/"
cp -R services/api-tests/src "$WORK/services/api-tests/"
expect_red() {
  local name="$1"
  local project="$2"
  local test="$3"
  set +e
  "$MVN" -B -f "$project" -Papi-tests "-Dtest=$test" test > "$ARTIFACTS/$name.log" 2>&1
  result=$?
  set -e
  # A compiler/network/container failure is not proof that an assertion detected the mutation.
  python3 - "$project" "$ARTIFACTS/$name" "$result" <<'PY'
import pathlib
import shutil
import sys
import xml.etree.ElementTree as ET
project, destination, exit_code = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), int(sys.argv[3])
reports = project.parent / "api-tests" / "target" / "surefire-reports"
if not reports.is_dir():
    raise SystemExit("Mutation produced no JUnit report")
cases = [case for file in reports.glob("TEST-*.xml") for case in ET.parse(file).getroot().findall("testcase")]
failures = [case for case in cases if case.find("failure") is not None]
errors = [case for case in cases if case.find("error") is not None]
if exit_code == 0 or not failures or errors:
    raise SystemExit("Mutation must cause an assertion failure, not a build/transport error")
if destination.exists():
    shutil.rmtree(destination)
shutil.copytree(reports, destination)
print("Mutation detected:", ", ".join(case.attrib["name"] for case in failures))
PY
}
python3 - "$WORK" <<'PY'
import pathlib
import sys
root = pathlib.Path(sys.argv[1]) / "services/api-tests/src/test/java/com/processing/api"
path = root / "GatewayValidationTest.java"
source = path.read_text()
old = 'assertError(response, 400, "gateway", "Request body is required");'
assert old in source
path.write_text(source.replace(old, 'assertError(response, 401, "gateway", "Request body is required");'))
PY
expect_red wrong-http-status "$WORK/services/pom.xml" 'GatewayValidationTest#jsonNullBodyHasDedicatedError'
cp services/api-tests/src/test/java/com/processing/api/GatewayValidationTest.java \
  "$WORK/services/api-tests/src/test/java/com/processing/api/GatewayValidationTest.java"
python3 - "$WORK" <<'PY'
import pathlib
import sys
path = pathlib.Path(sys.argv[1]) / "services/api-tests/src/test/java/com/processing/api/AuthorizationApiTest.java"
source = path.read_text()
old = 'card(pan).body("availableBalance", equalTo(99900));'
assert old in source
path.write_text(source.replace(old, 'card(pan).body("availableBalance", equalTo(99901));'))
PY
rm -rf "$WORK/services/api-tests/target/surefire-reports"
expect_red wrong-balance "$WORK/services/pom.xml" 'AuthorizationApiTest#purchaseTraversesGatewaySwitchAuthorizationAndLogger'
mkdir -p "$WORK/services/common" "$WORK/services/transaction-logger"
cp services/common/pom.xml "$WORK/services/common/"
cp -R services/common/src "$WORK/services/common/"
cp services/transaction-logger/pom.xml services/transaction-logger/Dockerfile "$WORK/services/transaction-logger/"
cp -R services/transaction-logger/src "$WORK/services/transaction-logger/"
python3 - "$WORK" <<'PY'
import pathlib
import sys
path = pathlib.Path(sys.argv[1]) / "services/transaction-logger/src/main/java/com/processing/transactionlogger/service/TransactionService.java"
source = path.read_text()
old = "if (existingTransaction.isPresent()) {"
assert source.count(old) == 1
path.write_text(source.replace(old, "if (false && existingTransaction.isPresent()) {"))
PY
docker build -f "$WORK/services/transaction-logger/Dockerfile" -t smp-api-logger:dedup-disabled "$WORK" \
  > "$ARTIFACTS/build.log" 2>&1
cat > "$WORK/override.yaml" <<'YAML'
services:
  transaction-logger:
    image: smp-api-logger:dedup-disabled
YAML
RESTORE=true
"${COMPOSE[@]}" -f "$WORK/override.yaml" up -d --no-deps --wait --wait-timeout 180 transaction-logger \
  > "$ARTIFACTS/replace.log" 2>&1
# Use the real, unchanged test suite against the mutated service.
rm -rf services/api-tests/target/surefire-reports
expect_red dedup-disabled "$ROOT/services/pom.xml" 'LoggerApiTest#sameIdAndSamePayloadAreIdempotent'
"${COMPOSE[@]}" up -d --no-deps --force-recreate --wait --wait-timeout 180 transaction-logger \
  > "$ARTIFACTS/restore.log" 2>&1
RESTORE=false
"$MVN" -B -f services/pom.xml -Papi-tests '-Dtest=LoggerApiTest#sameIdAndSamePayloadAreIdempotent' test \
  > "$ARTIFACTS/restored-green.log" 2>&1
python3 - "$ROOT" "$ARTIFACTS" <<'PY'
import pathlib
import shutil
import sys
source = pathlib.Path(sys.argv[1]) / "services/api-tests/target/surefire-reports"
destination = pathlib.Path(sys.argv[2]) / "restored-green"
if destination.exists():
    shutil.rmtree(destination)
shutil.copytree(source, destination)
PY
echo 'All three mutations caused assertion failures; restored Logger idempotency test is green.'

#!/usr/bin/env bash
# Practice 5: a separate Compose project, API-only data setup, reproducible Maven/JUnit checks.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
ARTIFACTS="$ROOT/services/api-tests/target"
mkdir -p "$ARTIFACTS"
FAULTS=false
MUTATIONS=false
KEEP=false
SUSPEND_MAIN=false
ORIGINAL_CONTAINERS=()
for arg in "$@"; do
  case "$arg" in
    --faults) FAULTS=true ;;
    --mutations) MUTATIONS=true ;;
    --keep-stack) KEEP=true ;;
    --suspend-main) SUSPEND_MAIN=true ;;
    *) echo "Usage: $0 [--faults] [--mutations] [--keep-stack] [--suspend-main]" >&2; exit 2 ;;
  esac
done
if [[ -f "$ROOT/.api-test-env/state.json" ]]; then
  echo 'An interactive API session is active. Run ./scripts/api-test-env.py stop before the full check.' >&2
  exit 2
fi
if [[ "$SUSPEND_MAIN" == true && "$KEEP" == true ]]; then
  echo '--suspend-main restores the original stack, so it cannot be combined with --keep-stack.' >&2
  exit 2
fi
if ! mkdir "$ARTIFACTS/.api-tests.lock" 2>/dev/null; then
  echo 'Another API test run owns the isolated stack; do not run faults concurrently.' >&2
  exit 2
fi
COMPOSE=(docker compose -p smp-api-tests -f "$ROOT/docker-compose.api-tests.yaml")
cleanup() {
  status=$?
  trap - EXIT
  "${COMPOSE[@]}" logs --no-color > "$ARTIFACTS/compose.log" 2>&1 || true
  if [[ "$KEEP" != true ]]; then
    "${COMPOSE[@]}" down --timeout 10 > "$ARTIFACTS/cleanup.log" 2>&1 || true
  fi
  if [[ -n "${ORIGINAL_CONTAINERS[*]:-}" ]]; then
    echo 'Restoring the original SMP containers...'
    docker start "${ORIGINAL_CONTAINERS[@]}" > "$ARTIFACTS/restore-main.log" 2>&1 || status=1
    python3 - "${ORIGINAL_CONTAINERS[@]}" <<'PY' || status=1
import json
import subprocess
import sys
import time
deadline = time.monotonic() + 180
while True:
    containers = json.loads(subprocess.check_output(["docker", "inspect", *sys.argv[1:]]))
    ready = all(item["State"]["Running"] and
                item["State"].get("Health", {}).get("Status", "healthy") == "healthy" for item in containers)
    if ready:
        print("Original SMP containers are running and healthy.")
        break
    if time.monotonic() >= deadline:
        raise SystemExit("Original SMP readiness timed out; inspect target/restore-main.log")
    time.sleep(1)
PY
  fi
  rmdir "$ARTIFACTS/.api-tests.lock" || true
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
if [[ -x /opt/homebrew/opt/openjdk@21/bin/java ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! java -version 2>&1 | head -1 | grep -Eq 'version "21[.\"]'; then
  echo 'Java 21 is required. Set JAVA_HOME to your JDK 21 installation.' >&2
  exit 2
fi
if [[ -z "${MVN:-}" ]]; then
  if command -v mvn >/dev/null; then
    MVN="$(command -v mvn)"
  else
    # Local bootstrap stays in ignored target/. SHA-512 is verified before extraction.
    MAVEN_VERSION=3.9.11
    TOOLS="$ARTIFACTS/tools"
    MVN="$TOOLS/apache-maven-$MAVEN_VERSION/bin/mvn"
    if [[ ! -x "$MVN" ]]; then
      mkdir -p "$TOOLS"
      URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz"
      curl --fail --silent --show-error --location "$URL" -o "$TOOLS/maven.tar.gz"
      curl --fail --silent --show-error --location "$URL.sha512" -o "$TOOLS/maven.tar.gz.sha512"
      python3 - "$TOOLS" <<'PY'
import hashlib
import pathlib
import sys
directory = pathlib.Path(sys.argv[1])
actual = hashlib.sha512((directory / "maven.tar.gz").read_bytes()).hexdigest()
expected = (directory / "maven.tar.gz.sha512").read_text().split()[0]
if actual != expected:
    raise SystemExit("Maven SHA-512 verification failed")
PY
      tar -xzf "$TOOLS/maven.tar.gz" -C "$TOOLS"
    fi
  fi
fi
export MVN
"$MVN" --version > "$ARTIFACTS/versions.txt"
docker compose version >> "$ARTIFACTS/versions.txt"
echo 'Starting isolated smp-api-tests stack (127.0.0.1:28080–28097)...'
if [[ "$SUSPEND_MAIN" == true ]]; then
  while IFS= read -r container_id; do
    [[ -z "$container_id" ]] || ORIGINAL_CONTAINERS+=("$container_id")
  done < <(docker ps --filter label=com.docker.compose.project=smp --format '{{.ID}}')
  if [[ -n "${ORIGINAL_CONTAINERS[*]:-}" ]]; then
    echo "Temporarily stopping ${#ORIGINAL_CONTAINERS[@]} original SMP containers (data preserved)."
    docker stop --timeout 10 "${ORIGINAL_CONTAINERS[@]}" > "$ARTIFACTS/suspend-main.log" 2>&1
  fi
fi
"${COMPOSE[@]}" up -d --build --wait --wait-timeout 600 > "$ARTIFACTS/startup.log" 2>&1
snapshot() {
  local destination="$ARTIFACTS/runs/$1"
  rm -rf "$destination"
  mkdir -p "$destination"
  cp -R "$ARTIFACTS/surefire-reports" "$destination/"
  if [[ -d "$ARTIFACTS/api-evidence" ]]; then
    mv "$ARTIFACTS/api-evidence" "$destination/http"
  fi
}
for ((run = 1; run <= ${RUNS:-3}; run++)); do
  echo "API regression run $run/${RUNS:-3}"
  rm -rf "$ARTIFACTS/surefire-reports" "$ARTIFACTS/api-evidence"
  if ! "$MVN" -B -f services/pom.xml -Papi-tests verify 2>&1 | tee "$ARTIFACTS/run-$run.log"; then
    snapshot "failed-regression-$run"
    exit 1
  fi
  snapshot "regression-$run"
done
if [[ "$FAULTS" == true ]]; then
  echo 'Checking HTTP failure and rollback contracts on the isolated stack...'
  rm -rf "$ARTIFACTS/surefire-reports" "$ARTIFACTS/api-evidence"
  "$MVN" -B -f services/pom.xml -Papi-tests,api-faults -Dapi.faults.enabled=true verify \
    2>&1 | tee "$ARTIFACTS/faults.log"
  snapshot faults
fi
if [[ "$MUTATIONS" == true ]]; then
  "$ROOT/scripts/check-api-mutations.sh"
fi
python3 "$ROOT/scripts/summarize-api-evidence.py"
echo "API checks passed. Evidence: $ARTIFACTS/runs"

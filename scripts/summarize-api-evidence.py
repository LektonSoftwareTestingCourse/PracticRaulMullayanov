#!/usr/bin/env python3
"""Summarize real JUnit/HTTP artifacts; never infer a pass from Maven output alone."""
import hashlib
import json
import pathlib
import re
import sys
import urllib.parse
import xml.etree.ElementTree as ET
from datetime import datetime
from zoneinfo import ZoneInfo

ROOT = pathlib.Path(__file__).resolve().parent.parent
TARGET = ROOT / "services/api-tests/target"
DESTINATION = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else TARGET
DESTINATION.mkdir(parents=True, exist_ok=True)


def junit(directory):
    suites = []
    for file in sorted(directory.glob("TEST-*.xml")):
        element = ET.parse(file).getroot()
        cases = []
        for case in element.findall("testcase"):
            failure = case.find("failure")
            error = case.find("error")
            cases.append({"name": case.get("name"), "seconds": float(case.get("time", "0")),
                          "result": "failure" if failure is not None else "error" if error is not None
                          else "skipped" if case.find("skipped") is not None else "passed",
                          "message": (failure.get("message", "") if failure is not None
                                      else error.get("message", "") if error is not None else "")})
        suites.append({"class": element.get("name"), "tests": int(element.get("tests", "0")),
                       "failures": int(element.get("failures", "0")), "errors": int(element.get("errors", "0")),
                       "skipped": int(element.get("skipped", "0")), "seconds": float(element.get("time", "0")),
                       "cases": cases})
    return {"tests": sum(suite["tests"] for suite in suites),
            "failures": sum(suite["failures"] for suite in suites),
            "errors": sum(suite["errors"] for suite in suites),
            "skipped": sum(suite["skipped"] for suite in suites), "suites": suites}


def write(name, value):
    (DESTINATION / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


runs = {directory.name: junit(directory / "surefire-reports")
        for directory in sorted((TARGET / "runs").glob("regression-*"))}
faults = junit(TARGET / "runs/faults/surefire-reports")
strict = junit(TARGET / "runs/strict-contract/surefire-reports")
sources = list((ROOT / "services/api-tests/src").rglob("*.java")) + [
    ROOT / "services/api-tests/pom.xml", ROOT / "docker-compose.api-tests.yaml",
    ROOT / "services/api-tests/fault-proxy.py", ROOT / "scripts/run-api-tests.sh",
    ROOT / "scripts/check-api-mutations.sh", ROOT / ".github/workflows/api-tests.yml"]
write("local-checks.json", {"generated_at": datetime.now(ZoneInfo("Europe/Moscow")).isoformat(),
                            "timezone": "Europe/Moscow", "regression_runs": runs,
                            "faults": faults, "source_sha256": {
                                str(file.relative_to(ROOT)): hashlib.sha256(file.read_bytes()).hexdigest()
                                for file in sorted(sources)}})
write("strict-contract.json", strict)
write("mutations.json", {directory.name: junit(directory)
                         for directory in sorted((TARGET / "mutations").iterdir()) if directory.is_dir()}
      if (TARGET / "mutations").exists() else {})

coverage = {}
for directory in [TARGET / "runs/regression-1/http", TARGET / "runs/faults/http",
                  TARGET / "runs/strict-contract/http"]:
    for file in directory.glob("*.json"):
        entry = json.loads(file.read_text())
        url = urllib.parse.urlsplit(entry["uri"])
        if url.path.startswith("/__"):
            continue
        path = re.sub(r"/[0-9]{16}(?=/|$)", "/{pan}", url.path)
        path = re.sub(r"(?<=/api/bins)/[0-9]{6}$", "/{bin}", path)
        path = re.sub(r"(?<=/api/bin)/[0-9]{6}$", "/{bin}", path)
        key = f"{url.port} {entry['method']} {path}"
        row = coverage.setdefault(key, {"statuses": set(), "cases": set(), "requests": 0})
        row["statuses"].add(entry["status"])
        row["cases"].add(entry.get("test", "fixture"))
        row["requests"] += 1
write("endpoint-coverage.json", {key: {"statuses": sorted(row["statuses"]),
                                        "junit_cases_sending_requests": len(row["cases"]),
                                        "requests_including_setup_cleanup_and_polling": row["requests"]}
                                  for key, row in sorted(coverage.items())})
print(json.dumps({"regression": {name: {key: run[key] for key in ["tests", "failures", "errors", "skipped"]}
                                 for name, run in runs.items()},
                  "faults": {key: faults[key] for key in ["tests", "failures", "errors", "skipped"]},
                  "strict": {key: strict[key] for key in ["tests", "failures", "errors", "skipped"]}},
                 ensure_ascii=False))

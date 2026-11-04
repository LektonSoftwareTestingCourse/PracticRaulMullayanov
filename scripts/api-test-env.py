#!/usr/bin/env python3
"""Keep an API test session available for VS Code; restore the original containers on stop."""
import argparse
import json
import pathlib
import subprocess
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
SESSION = ROOT / ".api-test-env"
STATE = SESSION / "state.json"
COMPOSE = ["docker", "compose", "-p", "smp-api-tests", "-f", str(ROOT / "docker-compose.api-tests.yaml")]


def capture(*command):
    return subprocess.check_output(command, cwd=ROOT, text=True).strip()


def logged(command, name):
    with (SESSION / name).open("w") as output:
        subprocess.run(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, check=True)


def healthy(ids):
    if not ids:
        return False
    containers = json.loads(capture("docker", "inspect", *ids))
    return all(item["State"]["Running"] and
               item["State"].get("Health", {}).get("Status", "healthy") == "healthy" for item in containers)


def gateway_ready():
    try:
        with urllib.request.urlopen("http://localhost:28080/health", timeout=5) as response:
            return json.load(response).get("status") == "ok"
    except (OSError, ValueError):
        return False


def stop():
    print("Останавливаю тестовый стенд…", flush=True)
    logged(COMPOSE + ["down", "--timeout", "10"], "stop.log")
    if STATE.exists():
        ids = json.loads(STATE.read_text())["original_containers"]
        if ids:
            # Never restore containers from another project, even if the state file was edited.
            saved = json.loads(capture("docker", "inspect", *ids))
            if any(item["Config"]["Labels"].get("com.docker.compose.project") != "smp" for item in saved):
                raise RuntimeError("В сохранённом состоянии есть контейнеры другого проекта")
            print("Восстанавливаю исходные контейнеры СМП…", flush=True)
            logged(["docker", "start", *ids], "restore.log")
            deadline = time.monotonic() + 180
            while not healthy(ids):
                if time.monotonic() >= deadline:
                    raise RuntimeError("СМП не стал healthy; состояние сохранено — можно повторить stop")
                time.sleep(1)
        STATE.unlink()
    print("Готово. Тестовый стенд остановлен, сохранённый основной СМП восстановлен.", flush=True)


def start():
    if STATE.exists():
        ids = capture("docker", "ps", "--filter", "label=com.docker.compose.project=smp-api-tests",
                      "--format", "{{.ID}}").splitlines()
        expected = len(json.loads(capture(*COMPOSE, "config", "--format", "json"))["services"])
        if len(ids) == expected and healthy(ids) and gateway_ready():
            print("Тестовый стенд уже готов: http://localhost:28080/health", flush=True)
            return
    else:
        ids = capture("docker", "ps", "--filter", "label=com.docker.compose.project=smp",
                      "--format", "{{.ID}}").splitlines()
        STATE.write_text(json.dumps({"original_containers": ids}, indent=2) + "\n")
    try:
        original = json.loads(STATE.read_text())["original_containers"]
        if original:
            print("Временно останавливаю основной СМП, сохраняя его контейнеры и данные…", flush=True)
            logged(["docker", "stop", "--timeout", "10", *original], "suspend.log")
        print("Поднимаю тестовый стенд. Первый запуск может занять несколько минут…", flush=True)
        logged(COMPOSE + ["up", "-d", "--build", "--wait", "--wait-timeout", "600"], "start.log")
        deadline = time.monotonic() + 30
        while not gateway_ready():
            if time.monotonic() >= deadline:
                raise RuntimeError("Тестовый Gateway не стал готов")
            time.sleep(1)
        print("Готово: можно нажимать Run Test / Debug Test в VS Code.", flush=True)
        print("Gateway: http://localhost:28080/health; после работы: ./scripts/api-test-env.py stop", flush=True)
    except BaseException:
        print("Подготовка прервана. Восстанавливаю основной СМП…", flush=True)
        stop()
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["start", "stop", "status"])
    action = parser.parse_args().action
    SESSION.mkdir(exist_ok=True)
    if action == "status":
        subprocess.run(COMPOSE + ["ps"], cwd=ROOT, check=True)
        print("Сессия IDE сохранена:" , "да" if STATE.exists() else "нет")
        return
    if (ROOT / "services/api-tests/target/.api-tests.lock").exists():
        raise RuntimeError("Выполняется полный API-прогон; дождись его завершения")
    lock = SESSION / "lock"
    try:
        lock.mkdir()
    except FileExistsError:
        raise RuntimeError("Другая команда уже управляет стендом; дождись её завершения") from None
    try:
        start() if action == "start" else stop()
    finally:
        lock.rmdir()


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        raise SystemExit(f"Ошибка: {error}. Логи: {SESSION}") from None

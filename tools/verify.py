#!/usr/bin/env python3
"""Run checks in NEW profiles and preserve real process and smoke results (Python 3.9+)."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
ALLOWED = {"build", "runGameTestServer", "runIslandSmoke",
           "runInfectionGameTestServer"}
SAVED_ROOTS = ("run/world", "build/island-smoke/saves/seeded-island-check",
               "build/client-smoke/saves/fluid-chaotic-check",
               "build/client-smoke/saves/fluid-relief-check",
               "build/client-smoke/saves/fluid-visual-check")


def checksum(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def saves():
    return {str(p.relative_to(ROOT)): {"bytes": p.stat().st_size, "mtime_ns": p.stat().st_mtime_ns,
                                      "sha256": checksum(p)}
            for root in SAVED_ROOTS for p in sorted((ROOT / root).rglob("*")) if p.is_file()}


def write(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--label", required=True)
    parser.add_argument("tasks", nargs="+", choices=sorted(ALLOWED))
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_-]+", args.label):
        parser.error("label must contain only letters, digits, - and _")
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    evidence = ROOT / ".verification" / (stamp + "-" + args.label)
    evidence.mkdir(parents=True, exist_ok=False)
    env = os.environ.copy()
    if not env.get("JAVA_HOME") and sys.platform == "darwin":
        env["JAVA_HOME"] = subprocess.check_output(["/usr/libexec/java_home", "-v", "21"], text=True).strip()
    if env.get("JAVA_HOME"):
        env["PATH"] = env["JAVA_HOME"] + "/bin:" + env.get("PATH", "")
    before = saves()
    write(evidence / "saves-before.json", before)
    tracked = subprocess.check_output(["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=ROOT)
    write(evidence / "source-sha256.json", {str(p.relative_to(ROOT)): checksum(p)
          for name in tracked.decode().split("\0") if name for p in [ROOT / name] if p.is_file()})
    (evidence / "git-diff.patch").write_bytes(subprocess.check_output(["git", "diff", "--binary", "HEAD"], cwd=ROOT))
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    print("EVIDENCE " + str(evidence), flush=True)
    success = False
    completed = []
    failure = None
    try:
        for task in args.tasks:
            command = [str(ROOT / "gradlew"), "--no-daemon", "--console=plain",
                       "-PintersticeRunRoot=" + str(evidence / "profiles"), task]
            record = {"command": command, "cwd": str(ROOT), "source_commit": head,
                      "java_home": env.get("JAVA_HOME"), "started_utc": dt.datetime.now(dt.timezone.utc).isoformat()}
            started = time.monotonic()
            with (evidence / (task + ".log")).open("x") as stream:
                process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
                record["pid"] = process.pid
                write(evidence / (task + ".json"), record)
                code = process.wait()
            record.update(exit_code=code, duration_seconds=round(time.monotonic() - started, 3),
                          ended_utc=dt.datetime.now(dt.timezone.utc).isoformat())
            accepted = code == 0
            if code == 0 and task in ("runGameTestServer", "runInfectionGameTestServer"):
                log = (evidence / (task + ".log")).read_text(errors="replace")
                passed = re.search(r"All ([1-9][0-9]*) required tests passed", log)
                record["required_tests_passed"] = int(passed.group(1)) if passed else 0
                accepted = passed is not None
            if code == 0 and task in ("runIslandSmoke", "runInfectionGameTestServer"):
                paths = {"runIslandSmoke": ("islandSmoke", "island-validation.json"),
                         "runInfectionGameTestServer": ("infectionGameTestServer", "infection-baseline.json")}
                run, name = paths[task]
                result_path = evidence / "profiles" / run / name
                record["validation_file"] = str(result_path)
                try:
                    record["validation"] = json.loads(result_path.read_text())
                    accepted = accepted and record["validation"].get("passed") is True
                except (OSError, ValueError) as error:
                    record["validation_error"] = str(error)
                    accepted = False
            record["accepted"] = accepted
            write(evidence / (task + ".json"), record)
            completed.append(task)
            print(task + " exit=" + str(code) + " accepted=" + str(accepted) + " seconds=" + str(record["duration_seconds"]), flush=True)
            print("\n".join((evidence / (task + ".log")).read_text(errors="replace").splitlines()[-8:]), flush=True)
            if not accepted:
                break
        else:
            success = True
    except BaseException as error:
        failure = type(error).__name__ + ": " + str(error)
        raise
    finally:
        after = saves()
        unchanged = before == after
        write(evidence / "saves-verification.json", {"unchanged": unchanged, "files": len(before),
              "differences": [p for p in sorted(before.keys() | after.keys()) if before.get(p) != after.get(p)]})
        success = success and unchanged
        write(evidence / "summary.json", {"passed": success, "source_commit": head, "tasks": args.tasks,
              "completed_tasks": completed, "failure": failure,
              "saves_unchanged": unchanged, "evidence": str(evidence)})
    return 0 if success else 1


if __name__ == "__main__":
    raise SystemExit(main())

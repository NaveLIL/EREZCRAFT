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
ALLOWED = {"build", "assemble", "runGameTestServer", "runIslandSmoke", "runTallIslandSmoke", "runGeometrySmoke",
           "runPersistenceSmoke", "runInfectionGameTestServer", "runRiftGameTestServer", "runRiftPersistenceSmoke",
           "runExpeditionGameTestServer", "runKeyPersistenceSmoke", "runWatchpostSmoke", "runWatchpostPersistenceSmoke", "runLivingGameTestServer", "runEcologyGameTestServer", "runMiningGameTestServer", "runLivingRealmSmoke",
           "runSurvivalPreparationSmoke", "runVaultGameTestServer", "runVaultPersistenceSmoke", "runGardenGameTestServer", "runGardenPersistenceSmoke", "runFoodGameTestServer", "runCrownFoodSmoke", "runMiningSmoke", "runHydrologySmoke", "runAgricultureGameTestServer", "runAgricultureSmoke", "runAgriculturePropsSmoke", "runEquipmentGameTestServer", "runBackpackSmoke", "runRetortUiSmoke", "runWearBackpackSmoke", "runVanillaTerrainSmoke"}
ALLOWED.add("runGearGameTestServer")
ALLOWED.add("runLiftGameTestServer")
ALLOWED.add("runNativeFieldLiftSmoke")
ALLOWED.update({"runTetherGameTestServer","runGearSmoke","runWinchSmoke"})
SAVED_ROOTS = ("run/world", "build/playtest/saves", "build/island-smoke/saves/seeded-island-check",
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
            for root in SAVED_ROOTS for p in sorted((ROOT / root).rglob("*")) if p.is_file() and p.name != "session.lock"}


def active_worlds():
    """Windows Minecraft exclusively locks session.lock; never close that user's game."""
    active = []
    for root in SAVED_ROOTS:
        for lock in (ROOT / root).rglob("session.lock"):
            try:
                with lock.open("rb") as stream:
                    stream.read(1)
            except PermissionError:
                active.append(lock.parent.relative_to(ROOT).as_posix())
    return sorted(set(active))


def write(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


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
        env["PATH"] = os.path.join(env["JAVA_HOME"], "bin") + os.pathsep + env.get("PATH", "")
    active_before = active_worlds()
    before = saves()
    write(evidence / "saves-before.json", before)
    tracked = subprocess.check_output(["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=ROOT)
    write(evidence / "source-sha256.json", {str(p.relative_to(ROOT)): checksum(p)
          for name in tracked.decode("utf-8").split("\0") if name for p in [ROOT / name] if p.is_file()})
    (evidence / "git-diff.patch").write_bytes(subprocess.check_output(["git", "diff", "--binary", "HEAD"], cwd=ROOT))
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    print("EVIDENCE " + str(evidence), flush=True)
    success = False
    completed = []
    failure = None
    try:
        for task in args.tasks:
            wrapper = "gradlew.bat" if sys.platform == "win32" else "gradlew"
            launcher = [str(ROOT / wrapper)]
            if sys.platform == "win32" and env.get("JAVA_HOME"):
                # Direct Java avoids a dependency on Windows batch execution policy.
                launcher = [str(Path(env["JAVA_HOME"]) / "bin/java.exe"), "-classpath",
                            str(ROOT / "gradle/wrapper/gradle-wrapper.jar"), "org.gradle.wrapper.GradleWrapperMain"]
            command = launcher + ["--no-daemon", "--console=plain",
                       "-PintersticeRunRoot=" + str(evidence / "profiles"), task]
            record = {"command": command, "cwd": str(ROOT), "source_commit": head,
                      "java_home": env.get("JAVA_HOME"), "started_utc": dt.datetime.now(dt.timezone.utc).isoformat()}
            started = time.monotonic()
            with (evidence / (task + ".log")).open("x", encoding="utf-8") as stream:
                process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
                record["pid"] = process.pid
                write(evidence / (task + ".json"), record)
                code = process.wait()
            record.update(exit_code=code, duration_seconds=round(time.monotonic() - started, 3),
                          ended_utc=dt.datetime.now(dt.timezone.utc).isoformat())
            accepted = code == 0
            if code == 0 and task in ("build", "runGameTestServer", "runInfectionGameTestServer", "runRiftGameTestServer", "runExpeditionGameTestServer", "runVaultGameTestServer", "runGardenGameTestServer", "runFoodGameTestServer", "runLivingGameTestServer", "runEcologyGameTestServer", "runMiningGameTestServer", "runAgricultureGameTestServer", "runEquipmentGameTestServer", "runGearGameTestServer", "runLiftGameTestServer", "runTetherGameTestServer"):
                log = (evidence / (task + ".log")).read_text(encoding="utf-8", errors="replace")
                groups = [int(count) for count in re.findall(r"All ([1-9][0-9]*) required tests passed", log)]
                record["required_test_groups"] = groups
                record["required_tests_passed"] = sum(groups)
                accepted = bool(groups)
            if code == 0 and task in ("runRiftPersistenceSmoke", "runKeyPersistenceSmoke", "runWatchpostPersistenceSmoke", "runSurvivalPreparationSmoke", "runVaultPersistenceSmoke", "runGardenPersistenceSmoke", "runCrownFoodSmoke", "runLivingRealmSmoke", "runMiningSmoke", "runAgricultureSmoke", "runBackpackSmoke", "runWearBackpackSmoke", "runVanillaTerrainSmoke", "runNativeFieldLiftSmoke", "runGearSmoke", "runWinchSmoke"):
                record["validations"] = {}
                prefix, profile = {"runRiftPersistenceSmoke": ("rift", "riftPersistence"),
                                   "runKeyPersistenceSmoke": ("key", "keyPersistence"),
                                   "runWatchpostPersistenceSmoke": ("watchpost", "watchpostPersistence"),
                                   "runSurvivalPreparationSmoke": ("survival", "survivalRoute"),
                                   "runVaultPersistenceSmoke": ("vault", "vaultPersistence"),
                                   "runGardenPersistenceSmoke": ("garden", "gardenPersistence"),
                                   "runCrownFoodSmoke": ("crown", "crownFood"), "runLivingRealmSmoke":("living","livingRealm"), "runMiningSmoke": ("mining", "miningSmoke"), "runAgricultureSmoke":("agriculture","agricultureSmoke"), "runBackpackSmoke":("backpack","backpackSmoke"), "runWearBackpackSmoke":("wear-backpack","wearBackpackSmoke"), "runVanillaTerrainSmoke":("v5","vanillaTerrainSmoke"), "runNativeFieldLiftSmoke":("lift","fieldLiftSmoke"), "runGearSmoke":("gear","gearSmoke"), "runWinchSmoke":("winch","winchSmoke")}[task]
                names = ("survival-preparation.json", "survival-equipment.json") if task == "runSurvivalPreparationSmoke" else (prefix + "-create-validation.json", prefix + "-reload-validation.json")
                for name in names:
                    result_path = evidence / "profiles" / profile / name
                    try:
                        result = json.loads(result_path.read_text(encoding="utf-8"))
                        record["validations"][name] = result
                        accepted = accepted and result.get("passed") is True
                    except (OSError, ValueError) as error:
                        record["validation_error"] = str(error); accepted = False
            if code == 0 and task in ("runIslandSmoke", "runTallIslandSmoke", "runGeometrySmoke", "runPersistenceSmoke", "runInfectionGameTestServer", "runWatchpostSmoke", "runHydrologySmoke", "runAgriculturePropsSmoke", "runRetortUiSmoke"):
                paths = {"runIslandSmoke": ("islandSmoke", "island-validation.json"),
                         "runTallIslandSmoke": ("tallIslandSmoke", "island-validation.json"),
                         "runGeometrySmoke": ("geometrySmoke", "geometry-validation.json"),
                         "runPersistenceSmoke": ("persistenceSmoke", "persistence-validation.json"),
                         "runInfectionGameTestServer": ("infectionGameTestServer", "infection-baseline.json"),
                         "runWatchpostSmoke": ("watchpostSmoke", "watchpost-validation.json"), "runHydrologySmoke":("hydrologySmoke","hydrology-validation.json"), "runAgriculturePropsSmoke":("agriculturePropsSmoke","agriculture-props-validation.json"), "runRetortUiSmoke":("retortUiSmoke","retort-ui-validation.json")}
                run, name = paths[task]
                result_path = evidence / "profiles" / run / name
                record["validation_file"] = str(result_path)
                try:
                    record["validation"] = json.loads(result_path.read_text(encoding="utf-8"))
                    accepted = accepted and record["validation"].get("passed") is True
                except (OSError, ValueError) as error:
                    record["validation_error"] = str(error)
                    accepted = False
            record["accepted"] = accepted
            write(evidence / (task + ".json"), record)
            completed.append(task)
            print(task + " exit=" + str(code) + " accepted=" + str(accepted) + " seconds=" + str(record["duration_seconds"]), flush=True)
            print("\n".join((evidence / (task + ".log")).read_text(encoding="utf-8", errors="replace").splitlines()[-8:]), flush=True)
            if not accepted:
                break
        else:
            success = True
    except BaseException as error:
        failure = type(error).__name__ + ": " + str(error)
        raise
    finally:
        after = saves()
        active = sorted(set(active_before + active_worlds()))
        differences = [p for p in sorted(before.keys() | after.keys()) if before.get(p) != after.get(p)]
        def user_active(path):
            normalized = path.replace("\\", "/")
            return any(normalized.startswith(world + "/") for world in active)
        protected_differences = [p for p in differences if not user_active(p)]
        unchanged = before == after if not active else None
        protected_unchanged = not protected_differences
        write(evidence / "saves-verification.json", {"unchanged": unchanged, "files": len(before),
              "active_user_worlds": active, "inactive_saves_unchanged": protected_unchanged,
              "differences": differences, "inactive_differences": protected_differences,
              "note": "Active user worlds are externally changing; no immutability claim is made for them. Tests run only in the new evidence profile." if active else None})
        success = success and protected_unchanged
        write(evidence / "summary.json", {"passed": success, "source_commit": head, "tasks": args.tasks,
              "completed_tasks": completed, "failure": failure,
              "saves_unchanged": unchanged, "inactive_saves_unchanged": protected_unchanged,
              "active_user_worlds": active, "evidence": str(evidence)})
    return 0 if success else 1


if __name__ == "__main__":
    raise SystemExit(main())

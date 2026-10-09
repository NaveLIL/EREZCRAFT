#!/usr/bin/env python3
"""Run checks in NEW profiles and preserve real process and smoke results (Python 3.9+)."""
import argparse
import datetime as dt
import hashlib
import json
import math
import os
from pathlib import Path
import re
import subprocess
import struct
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
ALLOWED = {"build", "assemble", "runGameTestServer", "runIslandSmoke", "runTallIslandSmoke", "runGeometrySmoke",
           "runPersistenceSmoke", "runInfectionGameTestServer", "runRiftGameTestServer", "runRiftPersistenceSmoke",
           "runExpeditionGameTestServer", "runKeyPersistenceSmoke", "runWatchpostSmoke", "runWatchpostPersistenceSmoke", "runLivingGameTestServer", "runEcologyGameTestServer", "runMiningGameTestServer", "runLivingRealmSmoke",
           "runSurvivalPreparationSmoke", "runVaultGameTestServer", "runVaultPersistenceSmoke", "runGardenGameTestServer", "runGardenPersistenceSmoke", "runFoodGameTestServer", "runCrownFoodSmoke", "runMiningSmoke", "runHydrologySmoke", "runAgricultureGameTestServer", "runAgricultureSmoke", "runAgriculturePropsSmoke", "runEquipmentGameTestServer", "runBackpackSmoke", "runRetortUiSmoke", "runWearBackpackSmoke", "runVanillaTerrainSmoke"}
ALLOWED.add("runGearGameTestServer")
ALLOWED.add("runPaletteGallery")
ALLOWED.add("runFaunaGameTestServer")
ALLOWED.add("runTensionRealmSmoke")
ALLOWED.update({"runV6ProgressionSmoke", "runV6Performance"})
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


GAME_TEST_TASK = re.compile(r"run[A-Za-z0-9]*GameTestServer\Z")
TASK_HEADER = re.compile(r"^\s*>\s*Task\s+:([^\s]+)(?:\s+(.*))?\s*$")
REQUIRED_COMPLETION = re.compile(r"\bAll (-?[0-9]+) required tests passed\b")


def expected_build_game_tests(root):
    """Read every check closure; a missing/unrecognized source definition cannot prove a build."""
    source = (root / "build.gradle").read_text(encoding="utf-8")
    # Mask comments without destroying quoted Groovy task names or their source positions.
    cleaned = list(source)
    quote = None
    index = 0
    while index < len(source):
        char = source[index]
        if quote:
            if char == "\\":
                index += 2
                continue
            if char == quote:
                quote = None
        elif char in "\"'":
            quote = char
        elif source.startswith("//", index):
            end = source.find("\n", index)
            end = len(source) if end < 0 else end
            cleaned[index:end] = " " * (end - index)
            index = end
            continue
        elif source.startswith("/*", index):
            end = source.find("*/", index + 2)
            if end < 0:
                raise ValueError("Unterminated build.gradle comment")
            cleaned[index:end + 2] = " " * (end + 2 - index)
            index = end + 2
            continue
        index += 1
    source = "".join(cleaned)
    check_pattern = re.compile(r"tasks\s*\.\s*named\s*\(\s*(['\"])check\1\s*\)\s*\{")
    task_pattern = re.compile(r"tasks\s*\.\s*named\s*\(\s*(['\"])(run[A-Za-z0-9]*GameTestServer)\1\s*\)")
    expected = []
    for check in check_pattern.finditer(source):
        start = check.end()
        cursor, depth, quote = start, 1, None
        while cursor < len(source) and depth:
            char = source[cursor]
            if quote:
                if char == "\\":
                    cursor += 2
                    continue
                if char == quote:
                    quote = None
            elif char in "\"'":
                quote = char
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
            cursor += 1
        if depth:
            raise ValueError("Unterminated tasks.named('check') closure")
        body = source[start:cursor - 1]
        if "dependsOn" in body:
            for task in task_pattern.finditer(body):
                if task.group(2) not in expected:
                    expected.append(task.group(2))
    if not expected:
        raise ValueError("No explicit GameTest dependencies found in build.gradle check closures")
    return expected


def game_test_receipt(task, log, root):
    if not (root / "build.gradle").is_file():
        raise FileNotFoundError("GameTest receipts require the repository build.gradle")
    expected = expected_build_game_tests(root) if task == "build" else [task]
    sections, unscoped, current = {}, [], None
    for line in log.splitlines():
        header = TASK_HEADER.match(line)
        if header:
            name = header.group(1).split(":")[-1]
            current = None
            if GAME_TEST_TASK.fullmatch(name):
                current = {"status": (header.group(2) or "").strip(), "completion_counts": [], "failures": []}
                sections.setdefault(name, []).append(current)
            continue
        completions = [int(value) for value in REQUIRED_COMPLETION.findall(line)]
        if current is None:
            unscoped.extend(completions)
            continue
        current["completion_counts"].extend(completions)
        if re.search(r"GameTestAssert(?:Pos)?Exception|\b[1-9][0-9]* (?:required )?tests? failed\b|\brequired tests? failed\b|failed[^\n]*required tests?|required tests?[^\n]*failed|^\s*FAILURE:", line, re.I):
            current["failures"].append(line.strip())
    errors = []
    if re.search(r"^\s*(?:BUILD FAILED\b|FAILURE:)", log, re.M):
        errors.append("Gradle reports a failed build despite the process exit code")
    unexpected = sorted(set(sections) - set(expected))
    if unexpected:
        errors.append("Unexpected GameTest tasks: " + ", ".join(unexpected))
    if unscoped:
        errors.append("Required-test completion outside a GameTest task section")
    groups, per_task = [], {}
    for name in expected:
        executions = sections.get(name, [])
        counts = [count for execution in executions for count in execution["completion_counts"]]
        groups.extend(counts)
        per_task[name] = {"executions": executions, "completion_counts": counts,
                          "required_tests_passed": sum(max(0, count) for count in counts)}
        if len(executions) != 1:
            errors.append(name + ": expected exactly one executed task section, found " + str(len(executions)))
            continue
        execution = executions[0]
        if execution["status"]:
            errors.append(name + ": task status " + execution["status"] + " does not prove execution")
        if len(counts) != 1 or counts[0] <= 0:
            errors.append(name + ": expected exactly one positive required-test completion, found " + repr(counts))
        if execution["failures"]:
            errors.append(name + ": failed GameTest output")
    return {"expected_game_test_tasks": expected, "game_test_tasks": per_task,
            "unexpected_game_test_tasks": unexpected, "game_test_errors": errors,
            "required_test_groups": groups, "required_tests_passed": sum(max(0, count) for count in groups)}, not errors


PALETTE_SCENES = {"open-surface", "forest-eye", "lower-shore", "cave", "ore-host-panel",
                  "hazard-and-safe", "crown-fruit", "retort-and-ruin-materials"}
SHA256 = re.compile(r"[0-9a-f]{64}\Z")


def checked_png(profile, filename, expected_hash, width, height):
    if not isinstance(filename, str) or Path(filename).name != filename or "/" in filename or "\\" in filename:
        raise ValueError("Screenshot filename must remain inside its profile")
    path = profile / "screenshots" / filename
    content = path.read_bytes()
    if len(content) < 45 or content[:8] != b"\x89PNG\r\n\x1a\n" or content[12:16] != b"IHDR":
        raise ValueError("Missing valid native PNG: " + filename)
    if struct.unpack(">II", content[16:24]) != (width, height):
        raise ValueError("Screenshot dimensions differ from the comparison settings: " + filename)
    if not isinstance(expected_hash, str) or not SHA256.fullmatch(expected_hash) or hashlib.sha256(content).hexdigest() != expected_hash:
        raise ValueError("Screenshot bytes differ from their reported SHA256: " + filename)


def palette_gallery_receipt(result, profile):
    errors = []
    try:
        if result.get("passed") is not True or result.get("shutdown_pending") is not False or result.get("clean_generation_before_mc_stop") is not True:
            raise ValueError("Palette gallery did not finish successfully with clean shutdown")
        if result.get("same_geometry_all_palettes") is not True or result.get("actual_resource_reload_count") != 3:
            raise ValueError("Palette gallery lacks three real reloads on unchanged geometry")
        width, height = result["width"], result["height"]
        if type(width) is not int or type(height) is not int or (width, height) != (1280, 720):
            raise ValueError("Invalid rendered frame dimensions")
        scenes = result["scenes"]
        if len(scenes) != 8 or {s["id"] for s in scenes} != PALETTE_SCENES:
            raise ValueError("Palette gallery requires exactly its eight declared scenes")
        manifest = {s["id"]: s for s in scenes}
        reloads = result["pack_reloads"]
        if len(reloads) != 3 or {r["palette"] for r in reloads} != {"A", "B", "C"}:
            raise ValueError("Palette gallery requires separate A/B/C reload traces")
        active_hashes = set()
        for reload in reloads:
            palette, digest = reload["palette"], reload["active_riftstone_sha256"]
            texture = profile / "resourcepacks" / ("v6-palette-" + palette) / "assets/interstice/textures/block/riftstone.png"
            if reload.get("reload_completed") is not True or reload.get("pack_id") != "file/v6-palette-" + palette or not SHA256.fullmatch(digest) or checksum(texture) != digest:
                raise ValueError("Active native palette bytes/reload trace disagree: " + palette)
            active_hashes.add(digest)
        if len(active_hashes) != 3:
            raise ValueError("A/B/C did not render three distinct palette resources")
        captures = result["captures"]
        if len(captures) != 48:
            raise ValueError("Palette gallery requires all48 native screenshots")
        combinations, invariant, camera_invariant, variant_hashes = set(), {}, {}, {}
        camera_fields = ("target_x", "target_y", "target_z", "camera_x", "camera_y", "camera_z", "look_at_x", "look_at_y", "look_at_z", "prepared")
        for capture in captures:
            scene, palette, nv = capture["id"], capture["palette"], capture["night_vision"]
            if scene not in PALETTE_SCENES or palette not in {"A", "B", "C"} or type(nv) is not bool:
                raise ValueError("Invalid palette/scene/night-vision combination")
            combination = (scene, palette, nv)
            if combination in combinations:
                raise ValueError("Duplicate palette capture: " + repr(combination))
            combinations.add(combination)
            filename = "v6-palette-" + palette + "-" + scene + ("-nv.png" if nv else "-dark.png")
            if capture["file"] != filename:
                raise ValueError("Palette capture filename disagrees with its scene")
            checked_png(profile, filename, capture["png_sha256"], width, height)
            if any(field not in capture or field not in manifest[scene] or capture[field] != manifest[scene][field] for field in camera_fields):
                raise ValueError("Camera/target/preparation changed between palettes: " + scene)
            digest = capture["geometry_state_sha256"]
            if not isinstance(digest, str) or not SHA256.fullmatch(digest) or capture["stable_client_ticks"] < 80:
                raise ValueError("Missing stable geometry/camera evidence: " + scene)
            evidence = (digest, capture["atlas_phase_mod384"], capture["actual_yaw"], capture["actual_pitch"], capture["eye_y"])
            pose = (digest,) + evidence[2:]
            if scene in camera_invariant:
                baseline_pose = camera_invariant[scene]
                if pose[0] != baseline_pose[0] or not all(math.isclose(a, b, rel_tol=0, abs_tol=1e-5) for a, b in zip(pose[1:], baseline_pose[1:])):
                    raise ValueError("Geometry/camera differs between dark and NV frames: " + scene)
            else:
                camera_invariant[scene] = pose
            key = (scene, nv)
            if key in invariant:
                baseline = invariant[key]
                if evidence[:2] != baseline[:2] or not all(math.isclose(a, b, rel_tol=0, abs_tol=1e-5) for a, b in zip(evidence[2:], baseline[2:])):
                    raise ValueError("Rendered camera, geometry or fluid animation phase changed: " + repr(key))
            else:
                invariant[key] = evidence
            variant_hashes.setdefault(key, set()).add(capture["png_sha256"])
        if any(len(hashes) != 3 for hashes in variant_hashes.values()):
            raise ValueError("Matching A/B/C scene frames do not show three distinct rendered variants")
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
        errors.append(str(error))
    return {"palette_errors": errors, "palette_screenshots_checked": len(result.get("captures", [])) if isinstance(result, dict) else 0}, not errors


def tension_matrix_receipt(result, profile):
    errors = []
    try:
        seeds = {0, 1, -1, 20261006, 76198123, 4294967297}
        if result.get("passed") is not True or result.get("finished") is not True or result.get("six_distinct_real_worlds_created") is not True or result.get("fixed_origin_full_hashes_distinguish_six_seeds") is not True:
            raise ValueError("V6 matrix did not finish six distinct actual FULL seed worlds")
        worlds = result["worlds"]
        if len(worlds) != 6 or {world["requested_seed"] for world in worlds} != seeds:
            raise ValueError("V6 matrix must contain each of the six fixed seeds exactly once")
        names, origin_hashes = set(), set()
        for world in worlds:
            seed, name = world["requested_seed"], world["save_name"]
            if name != "v6-full-seed-" + str(seed) or name in names or not (profile / "saves" / name / "level.dat").is_file():
                raise ValueError("Missing/reused actual saved seed world: " + str(seed))
            names.add(name)
            if world.get("passed") is not True or world.get("criteria_met") is not True or world.get("clean_generation_before_world_disconnect") is not True or world.get("old_integrated_server_stopped") is not True:
                raise ValueError("Seed world did not finish its native checks and clean server stop: " + str(seed))
            natural = world["natural_full"]
            if natural["actual_server_seed"] != seed or natural["terrain_revision"] != 6 or natural["dimension"] != "interstice:islands_v6" or natural["missing_in_declared_windows"]:
                raise ValueError("Actual server seed/FULL scope or bounded criteria failed: " + str(seed))
            origin = natural["fixed_origin_full_2x2"]
            distant = natural["fixed_distant_full_neighbor_pairs"]
            neighbors = natural["natural_full_neighbor_pairs"]
            plots = natural["four_natural_plots"]
            if len(origin) != 4 or {(c["chunk_x"], c["chunk_z"]) for c in origin} != {(-1, -1), (-1, 0), (0, -1), (0, 0)} or len(distant) != 4 or len(neighbors) != 8 or len(plots) != 4 or {plot["biome"] for plot in plots} != {"ash_islands", "pale_gardens", "stone_vaults", "crimson_thickets"}:
                raise ValueError("Missing fixed/native four-biome FULL neighbor chunks: " + str(seed))
            for chunk in origin + distant + neighbors:
                if chunk["canonical_cells_compared_after_surface"] != 16 * 16 * 256 or not SHA256.fullmatch(chunk["actual_FULL_state_and_biome_sha256"]) or not SHA256.fullmatch(chunk["static_geology_fluid_sha256"]):
                    raise ValueError("Incomplete actual FULL block/biome evidence: " + str(seed))
            origin_hashes.add(origin[0]["actual_FULL_state_and_biome_sha256"])
            captures = world["captures"]
            if not captures or world["native_capture_count"] != len(captures):
                raise ValueError("Missing native seed-world screenshots: " + str(seed))
            cameras = natural["natural_cameras"]
            camera_ids = {camera["id"] for camera in cameras}
            if len(cameras) < 4 or len(camera_ids) != len(cameras) or len(captures) != 2 * len(cameras):
                raise ValueError("Missing declared native camera/NV pairs: " + str(seed))
            combinations = set()
            for capture in captures:
                identifier, nv = capture["id"], capture["night_vision"]
                key = (identifier, nv)
                if identifier not in camera_ids or type(nv) is not bool or key in combinations or capture["seed"] != seed or capture.get("natural_blocks_edited") != 0:
                    raise ValueError("Invalid/duplicate/native-edited capture: " + str(seed))
                combinations.add(key)
                checked_png(profile, capture["file"], capture["png_sha256"], 1280, 720)
        if len(origin_hashes) != 6:
            raise ValueError("Fixed actual FULL hashes fail to distinguish the six seed worlds")
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
        errors.append(str(error))
    return {"tension_matrix_errors": errors}, not errors


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
            if code == 0 and (task == "build" or GAME_TEST_TASK.fullmatch(task)):
                log = (evidence / (task + ".log")).read_text(encoding="utf-8", errors="replace")
                try:
                    receipt, accepted = game_test_receipt(task, log, ROOT)
                    record.update(receipt)
                    if task == "build":
                        record["game_test_dependency_source_sha256"] = checksum(ROOT / "build.gradle")
                except (OSError, ValueError) as error:
                    record["game_test_errors"] = [str(error)]
                    record["required_test_groups"] = []
                    record["required_tests_passed"] = 0
                    accepted = False
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
            if code == 0 and task in ("runIslandSmoke", "runTallIslandSmoke", "runGeometrySmoke", "runPersistenceSmoke", "runInfectionGameTestServer", "runWatchpostSmoke", "runHydrologySmoke", "runAgriculturePropsSmoke", "runRetortUiSmoke", "runPaletteGallery"):
                paths = {"runIslandSmoke": ("islandSmoke", "island-validation.json"),
                         "runTallIslandSmoke": ("tallIslandSmoke", "island-validation.json"),
                         "runGeometrySmoke": ("geometrySmoke", "geometry-validation.json"),
                         "runPersistenceSmoke": ("persistenceSmoke", "persistence-validation.json"),
                         "runInfectionGameTestServer": ("infectionGameTestServer", "infection-baseline.json"),
                         "runWatchpostSmoke": ("watchpostSmoke", "watchpost-validation.json"), "runHydrologySmoke":("hydrologySmoke","hydrology-validation.json"), "runAgriculturePropsSmoke":("agriculturePropsSmoke","agriculture-props-validation.json"), "runRetortUiSmoke":("retortUiSmoke","retort-ui-validation.json"), "runPaletteGallery":("paletteGallery","palette-gallery-validation.json")}
                run, name = paths[task]
                result_path = evidence / "profiles" / run / name
                record["validation_file"] = str(result_path)
                try:
                    record["validation"] = json.loads(result_path.read_text(encoding="utf-8"))
                    accepted = accepted and record["validation"].get("passed") is True
                    if task == "runPaletteGallery":
                        receipt, palette_passed = palette_gallery_receipt(record["validation"], evidence / "profiles" / run)
                        record.update(receipt)
                        accepted = accepted and palette_passed
                except (OSError, ValueError) as error:
                    record["validation_error"] = str(error)
                    accepted = False
            if code == 0 and task in ("runV6ProgressionSmoke", "runV6Performance"):
                record["validations"] = {}
                profile = evidence / "profiles" / ("v6ProgressionSmoke" if task == "runV6ProgressionSmoke" else "v6Performance")
                names = ("v6-progression-create-validation.json", "v6-progression-reload-validation.json") if task == "runV6ProgressionSmoke" else ("v6-performance-validation.json",)
                for name in names:
                    try:
                        result = json.loads((profile / name).read_text(encoding="utf-8"))
                        record["validations"][name] = result
                        accepted = accepted and result.get("passed") is True
                    except (OSError, ValueError) as error:
                        record["validation_error"] = str(error); accepted = False
                if task == "runV6ProgressionSmoke" and len(record["validations"]) == 2:
                    first, second = (record["validations"][name] for name in names)
                    accepted = accepted and first.get("creator_pid") != second.get("pid")
            if code == 0 and task == "runTensionRealmSmoke":
                profile = evidence / "profiles" / "tensionRealmSmoke"
                result_path = profile / "v6-full-matrix-validation.json"
                record["validation_file"] = str(result_path)
                try:
                    record["validation"] = json.loads(result_path.read_text(encoding="utf-8"))
                    receipt, accepted = tension_matrix_receipt(record["validation"], profile)
                    record.update(receipt)
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

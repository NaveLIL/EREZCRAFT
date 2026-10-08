"""Audit M10-M12 against real saved evidence; a missing final native run cannot pass."""
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / ".verification"
FINAL = EVIDENCE / "20261007T222425.334209Z-m10-m12-final"


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def native(label, filename, fields, profile="survivalRoute"):
    folder = EVIDENCE / label
    report_path = folder / "profiles" / profile / filename
    report = read(report_path)
    assert report.get("passed") is True, f"Failed report: {report_path}"
    for field, expected in fields.items():
        assert report.get(field) == expected, f"Unsupported requirement: {filename}.{field}"
    logs = list(folder.glob("run*.log"))
    assert logs and any("BUILD SUCCESSFUL" in p.read_text(encoding="utf-8", errors="replace") for p in logs), f"Native process did not finish successfully: {label}"
    return {"path": str(report_path.relative_to(ROOT)), "sha256": hashlib.sha256(report_path.read_bytes()).hexdigest(), "report": report}


def main():
    checks = {}
    summary = read(FINAL / "summary.json")
    assert summary["passed"] and summary["saves_unchanged"], "Final build or owner save integrity failed"
    log = (FINAL / "build.log").read_text(encoding="utf-8", errors="replace")
    groups = [int(count) for count in re.findall(r"All ([1-9][0-9]*) required tests passed", log)]
    assert sorted(groups) == [16, 17, 104], f"Missing behavioral suites: {groups}"
    checks["behavioral_tests"] = {"groups": groups, "total": sum(groups), "owner_saves_unchanged": True}
    for name in ("key-create-validation.json", "key-reload-validation.json"):
        result = read(EVIDENCE / "m10-native-auto-world-prompt/profiles/keyPersistence" / name)
        assert result.get("passed") is True
        if "create" in name:
            assert result.get("real_item_channel_ticks") == 200 and result.get("spent_survives_repeat_entry") is True
        else:
            assert result.get("spent_after_jvm_restart") is True and result.get("copied_key_refused") is True
        checks[name] = result
    assert read(EVIDENCE / "m10-native-auto-world-prompt/runKeyPersistenceSmoke.json")["exit_code"] == 0
    for name in ("watchpost-create-validation.json", "watchpost-reload-validation.json"):
        result = read(EVIDENCE / "20261007T201512.358918Z-m11-real-restart/profiles/watchpostPersistence" / name)
        assert result.get("passed") is True
        if "create" in name:
            assert result.get("naturally_generated") is True and result.get("component_count") == 1
        else:
            assert result.get("empty_barrel_after_restart") is True and result.get("player_alteration_preserved") is True
        checks[name] = result
    assert read(EVIDENCE / "20261007T201512.358918Z-m11-real-restart/summary.json")["passed"] is True
    checks["survival_start"] = native("m12-natural-chest-located", "survival-preparation.json", {"cheats": False, "bonus_chest": True}, "survivalRouteBootstrap")
    checks["natural_resource_preparation"] = native("m12-natural-coal-finish", "survival-ores.json", {})
    checks["replacement_tool"] = native("m12-legitimate-tool-replacement", "survival-tool-repair.json", {"replacement_crafted": True})
    checks["real_smelting_and_kit"] = native("m12-forge-native-placement", "survival-forge.json", {})
    checks["first_entry"] = native("m12-first-cauldron-entry", "survival-entry.json", {"actual_cauldron_interaction": True})
    checks["natural_tide"] = native("m12-natural-tide-and-route", "survival-natural-tide.json", {"natural_calendar": True, "roof_protected_actual_player": True})
    assert checks["natural_tide"]["report"]["surge_samples"] >= 60
    checks["expedition_return"] = native("m12-ruin-ground-pickups", "survival-ruin-return.json", {"actual_barrel_looted": True, "actual_echo_return": True})
    assert checks["expedition_return"]["report"]["riftstone"] >= 12
    checks["portal_indicator"] = native("m12-portal-sheltered-indicator", "survival-portal-indicator.json", {"indicator_crafted_and_used": True, "client_server_phase_match": True, "player_built_frame": True, "actual_portal_contact": True, "actual_echo_return": True})
    assert " -> " in checks["portal_indicator"]["report"]["natural_phase_change"]
    saved = read(FINAL / "final-saved-player.json")
    assert saved["passed"] and saved["dimension"] == "minecraft:overworld" and saved["game_type"] == 0 and saved["cheats"] is False
    assert saved["health"] > 0 and not any(saved["abilities"][key] for key in ("invulnerable", "flying", "mayfly", "instabuild"))
    checks["final_saved_player"] = saved
    # Native controller source is part of the evidence boundary: weather is the declared condition, not a resource or travel shortcut.
    forbidden = re.compile(r"\b(?:sendCommand|setItemInHand|teleportTo|setBlock|setGameType|setHealth|setPhase|setDeltaMovement|setPos|setFoodLevel|addFreshEntity)\s*\(|GameType\.CREATIVE")
    for path in (ROOT / "src/smoke/java/pro/erez/interstice/client").glob("Survival*.java"):
        assert not forbidden.search(path.read_text(encoding="utf-8")), f"Prohibited Survival shortcut: {path}"
    jar = ROOT / "build/libs/interstice-0.2.1.jar"
    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        for path in ("pro/erez/interstice/expedition/ExpeditionLedger.class", "pro/erez/interstice/worldgen/WatchpostRuins.class", "data/interstice/structure/watchpost_ruin.nbt", "data/interstice/recipe/tide_indicator.json"):
            assert path in names, f"Missing release resource: {path}"
        assert not any("/test/" in path or "Smoke" in path or path.endswith("/empty.nbt") for path in names), "Test code or fixture shipped"
    checks["jar"] = {"path": str(jar.relative_to(ROOT)), "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(), "test_code_absent": True}
    result = {"passed": True, "scope": "M10-M12 controlled, guided Survival acceptance", "conditions": {"seed": 20261006, "difficulty": "normal", "bonus_chest": True, "first_entry_weather": "controlled thunder", "tide_calendar": "unmodified"}, "checks": checks}
    (FINAL / "expedition-acceptance.json").write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"passed": True, "jar_sha256": checks["jar"]["sha256"], "evidence": str(FINAL)}, ensure_ascii=False))


if __name__ == "__main__":
    main()

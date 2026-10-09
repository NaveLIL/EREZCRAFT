#!/usr/bin/env python3
"""Exercise failure accounting without starting Java or touching game profiles."""
import contextlib
import copy
import io
import json
import os
import hashlib
from pathlib import Path
import tempfile
import struct
import unittest
import zlib
from unittest.mock import patch

import verify

FIXTURE_CHECK = "tasks.named('check') { dependsOn tasks.named('runGameTestServer') }\n" \
                "tasks.named('check') { dependsOn tasks.named('runRiftGameTestServer') }\n"
BUILD_SUCCESS_LOG = "> Task :runGameTestServer\nAll 31 required tests passed :)\n" \
                    "> Task :runRiftGameTestServer\nAll 49 required tests passed :)\n> Task :check\n> Task :build\nBUILD SUCCESSFUL\n"


class VerificationResultTests(unittest.TestCase):
    def exercise(self, task, log="", result=None, spawn_error=None, before=None, after=None, active=(), build_text=FIXTURE_CHECK):
        with tempfile.TemporaryDirectory(prefix="interstice-verifier-test-") as directory:
            root = Path(directory)
            if build_text is not None:
                (root / "build.gradle").write_text(build_text, encoding="utf-8")

            class Process:
                pid = 123

                def wait(self):
                    return 0

            def spawn(command, **kwargs):
                self.command = command
                self.environment = kwargs["env"]
                if spawn_error is not None:
                    raise spawn_error
                if verify.GAME_TEST_TASK.fullmatch(task) and "> Task :" not in log:
                    kwargs["stdout"].write("> Task :" + task + "\n")
                kwargs["stdout"].write(log)
                if result is not None:
                    evidence = next((root / ".verification").iterdir())
                    if task in ("runRiftPersistenceSmoke", "runKeyPersistenceSmoke", "runWatchpostPersistenceSmoke", "runSurvivalPreparationSmoke", "runVaultPersistenceSmoke", "runGardenPersistenceSmoke", "runCrownFoodSmoke", "runLivingRealmSmoke", "runMiningSmoke", "runAgricultureSmoke"):
                        profile = {"runRiftPersistenceSmoke": "riftPersistence", "runKeyPersistenceSmoke": "keyPersistence", "runWatchpostPersistenceSmoke": "watchpostPersistence", "runSurvivalPreparationSmoke": "survivalRoute", "runVaultPersistenceSmoke": "vaultPersistence", "runGardenPersistenceSmoke": "gardenPersistence", "runCrownFoodSmoke":"crownFood","runLivingRealmSmoke":"livingRealm", "runMiningSmoke":"miningSmoke", "runAgricultureSmoke":"agricultureSmoke"}[task]
                        destination = evidence / "profiles" / profile
                        destination.mkdir(parents=True)
                        for name, value in result.items():
                            (destination / name).write_text(json.dumps(value), encoding="utf-8")
                    elif task in ('runHydrologySmoke','runAgriculturePropsSmoke'):
                        destination = evidence / ('profiles/hydrologySmoke' if task=='runHydrologySmoke' else 'profiles/agriculturePropsSmoke')
                        destination.mkdir(parents=True)
                        (destination / ('hydrology-validation.json' if task=='runHydrologySmoke' else 'agriculture-props-validation.json')).write_text(json.dumps(result), encoding='utf-8')
                    else:
                        destination = evidence / "profiles/islandSmoke"
                        destination.mkdir(parents=True)
                        (destination / "island-validation.json").write_text(json.dumps(result), encoding="utf-8")
                return Process()

            def output(command, **kwargs):
                if command[1] == "rev-parse":
                    return "fixture-commit\n"
                return b""

            caught = None
            with patch.object(verify, "ROOT", root), patch.object(verify, "saves", side_effect=[before or {}, after or {}]), \
                    patch.object(verify, "active_worlds", return_value=list(active)), \
                    patch.object(verify.subprocess, "check_output", side_effect=output), \
                    patch.object(verify.subprocess, "Popen", side_effect=spawn), \
                    patch.dict(verify.os.environ, {"JAVA_HOME": "/fixture/jdk"}), \
                    patch.object(verify.sys, "argv", ["verify.py", "--label", "test", task]), \
                    contextlib.redirect_stdout(io.StringIO()):
                try:
                    code = verify.main()
                except OSError as error:
                    caught = error
                    code = None
            evidence = next((root / ".verification").iterdir())
            return code, caught, json.loads((evidence / "summary.json").read_text(encoding="utf-8"))

    def test_active_user_world_is_reported_without_false_unchanged_claim(self):
        key='build/playtest/saves/owned/level.dat'
        code,_,summary=self.exercise('build',log=BUILD_SUCCESS_LOG,before={key:1},after={key:2},active=['build/playtest/saves/owned'])
        self.assertEqual(code,0)
        self.assertIsNone(summary['saves_unchanged'])
        self.assertTrue(summary['inactive_saves_unchanged'])

    def test_active_world_does_not_exempt_another_protected_save(self):
        key='build/playtest/saves/other/level.dat'
        code,_,summary=self.exercise('build',log=BUILD_SUCCESS_LOG,before={key:1},after={key:2},active=['build/playtest/saves/owned'])
        self.assertEqual(code,1)
        self.assertFalse(summary['inactive_saves_unchanged'])

    def test_inactive_save_change_still_rejects_checks(self):
        code,_,summary=self.exercise('build',log=BUILD_SUCCESS_LOG,before={'run/world/level.dat':1},after={'run/world/level.dat':2})
        self.assertEqual(code,1)
        self.assertFalse(summary['saves_unchanged'])

    def test_windows_uses_direct_java_wrapper(self):
        with patch.object(verify.sys, "platform", "win32"):
            code, _, _ = self.exercise("build", log=BUILD_SUCCESS_LOG)
        self.assertEqual(code, 0)
        self.assertEqual(Path(self.command[0]).name, "java.exe")
        self.assertIn("org.gradle.wrapper.GradleWrapperMain", self.command)

    def test_garden_server_requires_executed_tests(self):
        for log, expected in (("BUILD SUCCESSFUL\n", 1), ("All 9 required tests passed :)\n", 0)):
            with self.subTest(log=log):
                code, _, _ = self.exercise("runGardenGameTestServer", log=log)
                self.assertEqual(code, expected)

    def test_food_server_requires_executed_tests(self):
        code, _, _ = self.exercise("runFoodGameTestServer", log="BUILD SUCCESSFUL\n")
        self.assertEqual(code, 1)
        code, _, _ = self.exercise("runFoodGameTestServer", log="All 7 required tests passed :)\n")
        self.assertEqual(code, 0)

    def test_crown_food_requires_saved_and_resumed_reaction(self):
        for result, expected in (({"crown-create-validation.json":{"passed":True}},1),
                                 ({"crown-create-validation.json":{"passed":True},"crown-reload-validation.json":{"passed":False}},1),
                                 ({"crown-create-validation.json":{"passed":True},"crown-reload-validation.json":{"passed":True}},0)):
            with self.subTest(result=result):
                code, _, _ = self.exercise("runCrownFoodSmoke", result=result)
                self.assertEqual(code, expected)

    def test_living_realm_requires_real_cold_restart(self):
        for result, expected in (({"living-create-validation.json":{"passed":True}},1),
                                 ({"living-create-validation.json":{"passed":True},"living-reload-validation.json":{"passed":False}},1),
                                 ({"living-create-validation.json":{"passed":True},"living-reload-validation.json":{"passed":True}},0)):
            with self.subTest(result=result):
                code, _, _ = self.exercise("runLivingRealmSmoke",result=result)
                self.assertEqual(code,expected)

    def test_living_and_ecology_require_executed_gametests(self):
        for task in ('runLivingGameTestServer','runEcologyGameTestServer','runMiningGameTestServer','runAgricultureGameTestServer'):
            self.assertEqual(self.exercise(task,log='BUILD SUCCESSFUL\n')[0],1)
            self.assertEqual(self.exercise(task,log='All 6 required tests passed :)\n')[0],0)

    def test_mining_requires_both_cold_restart_reports(self):
        for reports, expected in (({'mining-create-validation.json':{'passed':True}},1),
                                  ({'mining-create-validation.json':{'passed':True},'mining-reload-validation.json':{'passed':False}},1),
                                  ({'mining-create-validation.json':{'passed':True},'mining-reload-validation.json':{'passed':True}},0)):
            with self.subTest(reports=reports):
                self.assertEqual(self.exercise('runMiningSmoke',result=reports)[0],expected)

    def test_agriculture_requires_both_positive_jvm_reports(self):
        for reports,expected in (({'agriculture-create-validation.json':{'passed':True}},1),
                                 ({'agriculture-create-validation.json':{'passed':True},'agriculture-reload-validation.json':{'passed':False}},1),
                                 ({'agriculture-create-validation.json':{'passed':True},'agriculture-reload-validation.json':{'passed':True}},0)):
            with self.subTest(reports=reports):self.assertEqual(self.exercise('runAgricultureSmoke',result=reports)[0],expected)

    def test_garden_cold_restart_requires_both_reports(self):
        for reports, expected in (({"garden-create-validation.json": {"passed": True}}, 1),
                                  ({"garden-create-validation.json": {"passed": True}, "garden-reload-validation.json": {"passed": False}}, 1),
                                  ({"garden-create-validation.json": {"passed": True}, "garden-reload-validation.json": {"passed": True}}, 0)):
            with self.subTest(reports=reports):
                code, _, _ = self.exercise("runGardenPersistenceSmoke", result=reports)
                self.assertEqual(code, expected)

    def test_unix_uses_shell_wrapper(self):
        with patch.object(verify.sys, "platform", "linux"):
            code, _, _ = self.exercise("build", log=BUILD_SUCCESS_LOG)
        self.assertEqual(code, 0)
        self.assertEqual(Path(self.command[0]).name, "gradlew")

    def test_java_home_keeps_platform_path_separator(self):
        self.exercise("build")
        self.assertEqual(self.environment["PATH"].split(os.pathsep)[0], os.path.join("/fixture/jdk", "bin"))

    def test_evidence_is_written_as_utf8(self):
        with tempfile.TemporaryDirectory(prefix="interstice-verifier-test-") as directory:
            path = Path(directory) / "evidence.json"
            verify.write(path, {"path": "проверка/🌊"})
            self.assertEqual(json.loads(path.read_text(encoding="utf-8")), {"path": "проверка/🌊"})

    def test_process_start_failure_cannot_record_success(self):
        code, error, summary = self.exercise("build", spawn_error=OSError("fixture launch failure"))
        self.assertIsNone(code)
        self.assertIsInstance(error, OSError)
        self.assertFalse(summary["passed"])
        self.assertEqual(summary["completed_tasks"], [])
        self.assertIn("fixture launch failure", summary["failure"])

    def test_gradle_zero_without_running_gametests_is_rejected(self):
        code, _, summary = self.exercise("runGameTestServer", log="BUILD SUCCESSFUL\n")
        self.assertEqual(code, 1)
        self.assertFalse(summary["passed"])

    def test_build_without_required_gametests_is_rejected(self):
        code, _, summary = self.exercise("build", log="BUILD SUCCESSFUL\n")
        self.assertEqual(code, 1)
        self.assertFalse(summary["passed"])

    def test_missing_build_source_never_infers_completed_required_groups(self):
        code, _, summary = self.exercise("build", log=BUILD_SUCCESS_LOG, build_text=None)
        self.assertEqual(code, 1)
        self.assertFalse(summary["passed"])

    def test_failed_smoke_json_overrides_zero_process_exit(self):
        code, _, summary = self.exercise("runIslandSmoke", result={"passed": False})
        self.assertEqual(code, 1)
        self.assertFalse(summary["passed"])

    def test_missing_smoke_json_is_rejected(self):
        code, _, summary = self.exercise("runIslandSmoke")
        self.assertEqual(code, 1)
        self.assertFalse(summary["passed"])

    def test_real_gametest_completion_is_accepted(self):
        code, _, summary = self.exercise("runGameTestServer", log="All 21 required tests passed :)\n")
        self.assertEqual(code, 0)
        self.assertTrue(summary["passed"])

    def test_rift_server_requires_actual_gametest_completion(self):
        code, _, summary = self.exercise("runRiftGameTestServer", log="BUILD SUCCESSFUL\n")
        self.assertEqual(code, 1)
        self.assertFalse(summary["passed"])

    def test_hydrology_requires_positive_native_report(self):
        for report, expected in ((None,1),({'passed':False},1),({'passed':True},0)):
            with self.subTest(report=report):
                self.assertEqual(self.exercise('runHydrologySmoke',result=report)[0],expected)

    def test_agriculture_props_requires_positive_native_report(self):
        for report,expected in ((None,1),({'passed':False},1),({'passed':True},0)):
            with self.subTest(report=report):self.assertEqual(self.exercise('runAgriculturePropsSmoke',result=report)[0],expected)

    def test_rift_restart_missing_second_validation_is_rejected(self):
        code, _, summary = self.exercise("runRiftPersistenceSmoke", result={"rift-create-validation.json": {"passed": True}})
        self.assertEqual(code, 1)
        self.assertFalse(summary["passed"])

    def test_rift_restart_failed_second_validation_is_rejected(self):
        code, _, summary = self.exercise("runRiftPersistenceSmoke", result={
            "rift-create-validation.json": {"passed": True}, "rift-reload-validation.json": {"passed": False}})
        self.assertEqual(code, 1)
        self.assertFalse(summary["passed"])

    def test_rift_restart_requires_both_successful_jvms(self):
        code, _, summary = self.exercise("runRiftPersistenceSmoke", result={
            "rift-create-validation.json": {"passed": True}, "rift-reload-validation.json": {"passed": True}})
        self.assertEqual(code, 0)
        self.assertTrue(summary["passed"])

    def test_expeditions_need_actual_server_completion(self):
        code, _, _ = self.exercise("runExpeditionGameTestServer", log="BUILD SUCCESSFUL\n")
        self.assertEqual(code, 1)

    def test_key_and_ruin_restart_require_second_jvm(self):
        for task, prefix in (("runKeyPersistenceSmoke", "key"), ("runWatchpostPersistenceSmoke", "watchpost")):
            with self.subTest(task=task):
                code, _, _ = self.exercise(task, result={prefix + "-create-validation.json": {"passed": True}})
                self.assertEqual(code, 1)
                code, _, _ = self.exercise(task, result={prefix + "-create-validation.json": {"passed": True}, prefix + "-reload-validation.json": {"passed": True}})
                self.assertEqual(code, 0)

    def test_survival_preparation_does_not_accept_missing_equipment_run(self):
        code, _, _ = self.exercise("runSurvivalPreparationSmoke", result={"survival-preparation.json": {"passed": True}})
        self.assertEqual(code, 1)

    def test_vault_server_requires_positive_gametest_completion(self):
        for log, accepted in (("BUILD SUCCESSFUL\n", False),
                              ("All 0 required tests passed :)\n", False),
                              ("All 8 required tests passed :)\n", True)):
            with self.subTest(log=log):
                code, _, summary = self.exercise("runVaultGameTestServer", log=log)
                self.assertEqual(code, 0 if accepted else 1)
                self.assertEqual(summary["passed"], accepted)
                self.assertEqual(summary["completed_tasks"], ["runVaultGameTestServer"])

    def test_vault_restart_accepts_both_successful_validations(self):
        code, _, summary = self.exercise("runVaultPersistenceSmoke", result={
            "vault-create-validation.json": {"passed": True},
            "vault-reload-validation.json": {"passed": True},
        })
        self.assertEqual(code, 0)
        self.assertTrue(summary["passed"])
        self.assertEqual(summary["completed_tasks"], ["runVaultPersistenceSmoke"])

    def test_vault_restart_refuses_missing_or_failed_validation(self):
        cases = {
            "missing_reload": {"vault-create-validation.json": {"passed": True}},
            "failed_reload": {"vault-create-validation.json": {"passed": True},
                              "vault-reload-validation.json": {"passed": False}},
            "failed_create": {"vault-create-validation.json": {"passed": False},
                              "vault-reload-validation.json": {"passed": True}},
        }
        for reason, result in cases.items():
            with self.subTest(reason=reason):
                code, _, summary = self.exercise("runVaultPersistenceSmoke", result=result)
                self.assertEqual(code, 1)
                self.assertFalse(summary["passed"])


class EquipmentVerificationTests(unittest.TestCase):
    """New task gates use a zero-exit fake Java process and genuine on-disk report locations."""
    def exercise(self, task, log="", reports=None):
        with tempfile.TemporaryDirectory(prefix="interstice-equipment-verifier-") as directory:
            root = Path(directory)
            (root / "build.gradle").write_text(FIXTURE_CHECK, encoding="utf-8")

            class Process:
                pid = 456
                def wait(self):
                    return 0

            def spawn(command, **kwargs):
                if verify.GAME_TEST_TASK.fullmatch(task) and "> Task :" not in log:
                    kwargs["stdout"].write("> Task :" + task + "\n")
                kwargs["stdout"].write(log)
                evidence = next((root / ".verification").iterdir())
                profile = {"runWearBackpackSmoke": "wearBackpackSmoke", "runBackpackSmoke": "backpackSmoke",
                           "runRetortUiSmoke": "retortUiSmoke", "runVanillaTerrainSmoke": "vanillaTerrainSmoke",
                           "runNativeFieldLiftSmoke": "fieldLiftSmoke", "runGearSmoke": "gearSmoke",
                           "runWinchSmoke": "winchSmoke", "runPaletteGallery": "paletteGallery", "runTensionRealmSmoke": "tensionRealmSmoke"}.get(task, "retortUiSmoke")
                for name, value in (reports or {}).items():
                    path = evidence / "profiles" / profile / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text(json.dumps(value), encoding="utf-8")
                return Process()

            def output(command, **kwargs):
                return "equipment-fixture-commit\n" if command[1] == "rev-parse" else b""

            with patch.object(verify, "ROOT", root), patch.object(verify, "saves", return_value={}), \
                    patch.object(verify, "active_worlds", return_value=[]), \
                    patch.object(verify.subprocess, "check_output", side_effect=output), \
                    patch.object(verify.subprocess, "Popen", side_effect=spawn), \
                    patch.dict(verify.os.environ, {"JAVA_HOME": "/fixture/jdk"}), \
                    patch.object(verify.sys, "argv", ["verify.py", "--label", "equipment-test", task]), \
                    contextlib.redirect_stdout(io.StringIO()):
                code = verify.main()
            evidence = next((root / ".verification").iterdir())
            return code, json.loads((evidence / "summary.json").read_text(encoding="utf-8")), \
                json.loads((evidence / (task + ".json")).read_text(encoding="utf-8"))

    def test_equipment_server_requires_positive_executed_gametests(self):
        for log, accepted in (("BUILD SUCCESSFUL\n", False),
                              ("All 0 required tests passed :)\n", False),
                              ("All 11 required tests passed :)\n", True)):
            with self.subTest(log=log):
                code, summary, record = self.exercise("runEquipmentGameTestServer", log=log)
                self.assertEqual(code, 0 if accepted else 1)
                self.assertEqual(summary["passed"], accepted)
                self.assertEqual(record["accepted"], accepted)
                self.assertEqual(record["required_tests_passed"], 11 if accepted else 0)

    def test_backpack_smoke_requires_both_positive_cold_jvm_reports(self):
        create, reload = "backpack-create-validation.json", "backpack-reload-validation.json"
        cases = [({}, False), ({create: {"passed": True}}, False), ({reload: {"passed": True}}, False),
                 ({create: {"passed": False}, reload: {"passed": True}}, False),
                 ({create: {"passed": True}, reload: {"passed": False}}, False),
                 ({create: {"passed": "true"}, reload: {"passed": True}}, False),
                 ({create: {"passed": True}, reload: {"passed": True}}, True)]
        for reports, accepted in cases:
            with self.subTest(reports=reports):
                code, summary, record = self.exercise("runBackpackSmoke", reports=reports)
                self.assertEqual(code, 0 if accepted else 1)
                self.assertEqual(summary["passed"], accepted)
                self.assertEqual(record["accepted"], accepted)
                self.assertEqual(summary["completed_tasks"], ["runBackpackSmoke"])

    def test_retort_ui_smoke_rejects_missing_false_and_merely_truthy_report(self):
        for value, accepted in ((None, False), ({"passed": False}, False), ({"passed": 1}, False),
                                ({"passed": "true"}, False), ({"passed": True}, True)):
            with self.subTest(value=value):
                reports = {} if value is None else {"retort-ui-validation.json": value}
                code, summary, record = self.exercise("runRetortUiSmoke", reports=reports)
                self.assertEqual(code, 0 if accepted else 1)
                self.assertEqual(summary["passed"], accepted)
                self.assertEqual(record["accepted"], accepted)
                if value is None:
                    self.assertIn("validation_error", record)

    def test_worn_backpack_requires_both_cold_process_reports(self):
        create,reload="wear-backpack-create-validation.json","wear-backpack-reload-validation.json"
        for reports,accepted in (({},False),({create:{"passed":True}},False),
                                 ({create:{"passed":True},reload:{"passed":False}},False),
                                 ({create:{"passed":True},reload:{"passed":True}},True)):
            with self.subTest(reports=reports):
                code,summary,record=self.exercise("runWearBackpackSmoke",reports=reports)
                self.assertEqual(code,0 if accepted else 1)
                self.assertEqual(summary["passed"],accepted)
                self.assertEqual(record["accepted"],accepted)

    def test_new_gear_lift_and_tether_servers_require_positive_executed_groups(self):
        cases = [("BUILD SUCCESSFUL\n", 0, False), ("All 0 required tests passed :)\n", 0, False),
                 ("All -4 required tests passed :)\n", 0, False),
                 ("All 0 required tests passed :)\nAll 0 required tests passed :)\n", 0, False),
                 ("All 1 required tests passed :)\n", 1, True),
                 ("All 3 required tests passed :)\nAll 9 required tests passed :)\n", 12, False)]
        for task in ("runGearGameTestServer", "runLiftGameTestServer", "runTetherGameTestServer", "runFaunaGameTestServer"):
            for log, required_count, accepted in cases:
                with self.subTest(task=task, log=log):
                    code, summary, record = self.exercise(task, log=log)
                    self.assertEqual(code, 0 if accepted else 1)
                    self.assertEqual(summary["passed"], accepted)
                    self.assertEqual(record["accepted"], accepted)
                    self.assertEqual(record["required_tests_passed"], required_count)

    def check_cold_pair(self, task, prefix):
        create, reload = prefix + "-create-validation.json", prefix + "-reload-validation.json"
        cases = [({}, False), ({create: {"passed": True}}, False), ({reload: {"passed": True}}, False),
                 ({create: {"passed": False}, reload: {"passed": True}}, False),
                 ({create: {"passed": True}, reload: {"passed": False}}, False),
                 ({create: {"passed": 1}, reload: {"passed": True}}, False),
                 ({create: {"passed": True}, reload: {"passed": "true"}}, False),
                 ({create: {"passed": True}, reload: {}}, False),
                 ({create: {"passed": True}, reload: {"passed": True}}, True)]
        for reports, accepted in cases:
            with self.subTest(task=task, reports=reports):
                code, summary, record = self.exercise(task, reports=reports)
                self.assertEqual(code, 0 if accepted else 1)
                self.assertEqual(summary["passed"], accepted)
                self.assertEqual(record["accepted"], accepted)
                self.assertEqual(summary["completed_tasks"], [task])
                if accepted:
                    self.assertEqual(set(record["validations"]), {create, reload})

    def test_vanilla_terrain_requires_its_actual_create_and_reload_reports(self):
        self.check_cold_pair("runVanillaTerrainSmoke", "v5")

    def test_native_field_lift_requires_its_actual_create_and_reload_reports(self):
        self.check_cold_pair("runNativeFieldLiftSmoke", "lift")

    def test_gear_requires_its_actual_create_and_reload_reports(self):
        self.check_cold_pair("runGearSmoke", "gear")

    def test_winch_requires_its_actual_create_and_reload_reports(self):
        self.check_cold_pair("runWinchSmoke", "winch")


class GameTestReceiptTests(unittest.TestCase):
    def receipt(self, log, source=FIXTURE_CHECK, task="build"):
        with tempfile.TemporaryDirectory(prefix="interstice-groups-") as directory:
            root = Path(directory)
            (root / "build.gradle").write_text(source, encoding="utf-8")
            return verify.game_test_receipt(task, log, root)

    def test_all_check_closures_define_the_required_task_set(self):
        source = FIXTURE_CHECK + "// tasks.named('check') { dependsOn tasks.named('runIgnoredGameTestServer') }\n" \
                 "/* tasks.named('check') { dependsOn tasks.named('runIgnoredTooGameTestServer') } */\n" \
                 "tasks.named(\"check\") { dependsOn tasks.named(\"runFaunaGameTestServer\") }\n"
        log = BUILD_SUCCESS_LOG + "> Task :runFaunaGameTestServer\nAll 3 required tests passed :)\n"
        record, accepted = self.receipt(log, source)
        self.assertTrue(accepted)
        self.assertEqual(record["expected_game_test_tasks"], ["runGameTestServer", "runRiftGameTestServer", "runFaunaGameTestServer"])
        self.assertEqual(record["required_tests_passed"], 83)
        self.assertEqual(record["game_test_tasks"]["runFaunaGameTestServer"]["completion_counts"], [3])

    def test_one_positive_line_cannot_replace_all_required_groups(self):
        record, accepted = self.receipt("> Task :runGameTestServer\nAll 80 required tests passed :)\nBUILD SUCCESSFUL\n")
        self.assertFalse(accepted)
        self.assertEqual(record["game_test_tasks"]["runRiftGameTestServer"]["executions"], [])

    def test_unexpected_positive_task_does_not_prove_required_tasks(self):
        record, accepted = self.receipt("> Task :runFaunaGameTestServer\nAll 100 required tests passed :)\n")
        self.assertFalse(accepted)
        self.assertEqual(record["unexpected_game_test_tasks"], ["runFaunaGameTestServer"])

    def test_build_rejects_skipped_cached_failed_and_no_source_tasks(self):
        for status in ("SKIPPED", "UP-TO-DATE", "FROM-CACHE", "NO-SOURCE", "FAILED"):
            with self.subTest(status=status):
                record, accepted = self.receipt(BUILD_SUCCESS_LOG.replace(":runRiftGameTestServer\n", ":runRiftGameTestServer " + status + "\n"))
                self.assertFalse(accepted)
                self.assertTrue(record["game_test_errors"])

    def test_duplicate_sections_or_completions_are_rejected(self):
        logs = [BUILD_SUCCESS_LOG + "> Task :runRiftGameTestServer\nAll 49 required tests passed :)\n",
                BUILD_SUCCESS_LOG.replace("All 49 required tests passed :)\n", "All 49 required tests passed :)\nAll 49 required tests passed :)\n")]
        for log in logs:
            with self.subTest(log=log):
                self.assertFalse(self.receipt(log)[1])

    def test_zero_negative_missing_and_failed_groups_are_rejected(self):
        for replacement in ("All 0 required tests passed :)", "All -2 required tests passed :)",
                            "BUILD SUCCESSFUL", "1 required tests failed\nAll 49 required tests passed :)"):
            with self.subTest(replacement=replacement):
                self.assertFalse(self.receipt(BUILD_SUCCESS_LOG.replace("All 49 required tests passed :)", replacement))[1])

    def test_completion_outside_its_task_cannot_be_reassigned(self):
        for log in ("All 80 required tests passed :)\n", BUILD_SUCCESS_LOG + "All 1 required tests passed :)\n"):
            with self.subTest(log=log):
                self.assertFalse(self.receipt(log)[1])

    def test_single_server_also_requires_its_own_exact_task_section(self):
        self.assertFalse(self.receipt("All 7 required tests passed :)\n", task="runFaunaGameTestServer")[1])
        self.assertFalse(self.receipt("> Task :runGearGameTestServer\nAll 7 required tests passed :)\n", task="runFaunaGameTestServer")[1])
        self.assertTrue(self.receipt("> Task :runFaunaGameTestServer\nAll 7 required tests passed :)\n", task="runFaunaGameTestServer")[1])

    def test_initial_missing_properties_is_not_a_failed_required_test(self):
        log = "> Task :runFaunaGameTestServer\n[main/ERROR] [minecraft/Settings]: Failed to load properties from file: server.properties\nAll 7 required tests passed :)\n"
        self.assertTrue(self.receipt(log, task="runFaunaGameTestServer")[1])

    def test_failed_gradle_build_cannot_pass_with_stale_positive_receipts(self):
        self.assertFalse(self.receipt(BUILD_SUCCESS_LOG + "FAILURE: Build failed with an exception.\nBUILD FAILED\n")[1])


def fixture_png(red, green, blue):
    def chunk(tag, payload):
        return struct.pack(">I", len(payload)) + tag + payload + struct.pack(">I", zlib.crc32(tag + payload) & 0xffffffff)
    pixels = (b"\0" + bytes((red, green, blue)) * 1280) * 720
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1280, 720, 8, 2, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(pixels)) + chunk(b"IEND", b"")


class PaletteReceiptTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="interstice-palette-receipt-")
        self.addCleanup(self.directory.cleanup)
        self.profile = Path(self.directory.name)
        (self.profile / "screenshots").mkdir()
        self.report = {"passed": True, "shutdown_pending": False, "clean_generation_before_mc_stop": True,
                       "same_geometry_all_palettes": True, "actual_resource_reload_count": 3,
                       "width": 1280, "height": 720, "scenes": [], "captures": [], "pack_reloads": []}
        for index, identifier in enumerate(sorted(verify.PALETTE_SCENES)):
            scene = {"id": identifier, "target_x": index, "target_y": 64, "target_z": 3,
                     "camera_x": index + .5, "camera_y": 65, "camera_z": 4.5,
                     "look_at_x": index + 2.5, "look_at_y": 65, "look_at_z": 4.5, "prepared": index < 3}
            self.report["scenes"].append(scene)
        for palette_index, palette in enumerate(("A", "B", "C")):
            texture = fixture_png(30 + palette_index * 20, 50, 70)
            texture_path = self.profile / "resourcepacks" / ("v6-palette-" + palette) / "assets/interstice/textures/block/riftstone.png"
            texture_path.parent.mkdir(parents=True)
            texture_path.write_bytes(texture)
            self.report["pack_reloads"].append({"palette": palette, "pack_id": "file/v6-palette-" + palette,
                                                 "active_riftstone_sha256": hashlib.sha256(texture).hexdigest(), "reload_completed": True})
            for scene in self.report["scenes"]:
                for nv in (False, True):
                    content = fixture_png(30 + palette_index * 20, 50 + (20 if nv else 0), 70)
                    filename = "v6-palette-" + palette + "-" + scene["id"] + ("-nv.png" if nv else "-dark.png")
                    (self.profile / "screenshots" / filename).write_bytes(content)
                    self.report["captures"].append({**scene, "palette": palette, "night_vision": nv, "file": filename,
                                                     "png_sha256": hashlib.sha256(content).hexdigest(),
                                                     "geometry_state_sha256": hashlib.sha256(scene["id"].encode()).hexdigest(),
                                                     "atlas_phase_mod384": 192 if nv else 96, "stable_client_ticks": 80,
                                                     "actual_yaw": 1.0, "actual_pitch": 2.0, "eye_y": 66.62})

    def test_complete_native_palette_matrix_is_accepted(self):
        record, accepted = verify.palette_gallery_receipt(self.report, self.profile)
        self.assertTrue(accepted, record)
        self.assertEqual(record["palette_screenshots_checked"], 48)

    def test_positive_flag_cannot_replace_capture_matrix_or_true_booleans(self):
        cases = [dict(self.report, passed="true"), dict(self.report, shutdown_pending=True),
                 dict(self.report, captures=self.report["captures"][:-1]),
                 dict(self.report, captures=[self.report["captures"][0]] * 48),
                 dict(self.report, scenes=self.report["scenes"][:-1]), dict(self.report, pack_reloads=self.report["pack_reloads"][:-1])]
        for result in cases:
            with self.subTest(result_keys=result.keys()):
                self.assertFalse(verify.palette_gallery_receipt(result, self.profile)[1])

    def test_camera_geometry_phase_and_resource_trace_tampering_is_rejected(self):
        changes = [("camera_x", 900), ("actual_yaw", 90), ("geometry_state_sha256", "0" * 64),
                   ("atlas_phase_mod384", 0), ("night_vision", 1), ("stable_client_ticks", 1)]
        for key, value in changes:
            report = copy.deepcopy(self.report)
            report["captures"][16][key] = value
            with self.subTest(key=key):
                self.assertFalse(verify.palette_gallery_receipt(report, self.profile)[1])
        report = copy.deepcopy(self.report)
        report["pack_reloads"][1]["active_riftstone_sha256"] = report["pack_reloads"][0]["active_riftstone_sha256"]
        self.assertFalse(verify.palette_gallery_receipt(report, self.profile)[1])

    def test_missing_or_changed_png_bytes_are_rejected(self):
        shot = self.profile / "screenshots" / self.report["captures"][0]["file"]
        shot.write_bytes(shot.read_bytes() + b"changed")
        self.assertFalse(verify.palette_gallery_receipt(self.report, self.profile)[1])
        shot.unlink()
        self.assertFalse(verify.palette_gallery_receipt(self.report, self.profile)[1])

    def test_main_task_gate_rejects_merely_positive_palette_report(self):
        code, summary, record = EquipmentVerificationTests.exercise(self, "runPaletteGallery", reports={"palette-gallery-validation.json": {"passed": True}})
        self.assertEqual(code, 1)
        self.assertFalse(summary["passed"])
        self.assertTrue(record["palette_errors"])


class TensionMatrixReceiptTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="interstice-full-matrix-receipt-")
        self.addCleanup(self.directory.cleanup)
        self.profile = Path(self.directory.name)
        (self.profile / "screenshots").mkdir()
        self.report = {"passed": True, "finished": True, "six_distinct_real_worlds_created": True,
                       "fixed_origin_full_hashes_distinguish_six_seeds": True, "worlds": []}
        png = fixture_png(40, 80, 100)
        png_hash = hashlib.sha256(png).hexdigest()
        for seed in (0, 1, -1, 20261006, 76198123, 4294967297):
            name = "v6-full-seed-" + str(seed)
            save = self.profile / "saves" / name / "level.dat"
            save.parent.mkdir(parents=True)
            save.write_bytes(("unit fixture " + str(seed)).encode())
            def chunk(x, z):
                return {"chunk_x": x, "chunk_z": z, "canonical_cells_compared_after_surface": 65536,
                        "actual_FULL_state_and_biome_sha256": hashlib.sha256((str(seed) + ":" + str(x) + ":" + str(z)).encode()).hexdigest(),
                        "static_geology_fluid_sha256": hashlib.sha256((name + " static").encode()).hexdigest()}
            cameras = [{"id": identifier} for identifier in ("ash-eye", "garden-eye", "vault-eye", "crimson-eye")]
            natural = {"actual_server_seed": seed, "terrain_revision": 6, "dimension": "interstice:islands_v6", "missing_in_declared_windows": [],
                       "fixed_origin_full_2x2": [chunk(x, z) for x in (-1, 0) for z in (-1, 0)],
                       "fixed_distant_full_neighbor_pairs": [chunk(x, z) for x, z in ((47, -32), (48, -32), (-48, 31), (-47, 31))],
                       "natural_full_neighbor_pairs": [chunk(x, 7) for x in range(8)],
                       "four_natural_plots": [{"biome": biome} for biome in ("ash_islands", "pale_gardens", "stone_vaults", "crimson_thickets")],
                       "natural_cameras": cameras}
            captures = []
            for camera in cameras:
                for nv in (False, True):
                    filename = name + "-" + camera["id"] + ("-nv.png" if nv else "-dark.png")
                    (self.profile / "screenshots" / filename).write_bytes(png)
                    captures.append({**camera, "file": filename, "png_sha256": png_hash, "night_vision": nv, "seed": seed, "natural_blocks_edited": 0})
            self.report["worlds"].append({"requested_seed": seed, "save_name": name, "passed": True, "criteria_met": True,
                                           "clean_generation_before_world_disconnect": True, "old_integrated_server_stopped": True,
                                           "natural_full": natural, "captures": captures, "native_capture_count": len(captures)})

    def test_complete_actual_full_seed_receipt_is_accepted(self):
        record, accepted = verify.tension_matrix_receipt(self.report, self.profile)
        self.assertTrue(accepted, record)

    def test_partial_duplicate_or_synthetic_seed_scope_is_rejected(self):
        partial = copy.deepcopy(self.report)
        partial["worlds"].pop()
        duplicate = copy.deepcopy(self.report)
        duplicate["worlds"][5] = duplicate["worlds"][0]
        synthetic = copy.deepcopy(self.report)
        synthetic["worlds"][0]["natural_full"]["actual_server_seed"] = 100
        incomplete_full = copy.deepcopy(self.report)
        incomplete_full["worlds"][0]["natural_full"]["fixed_origin_full_2x2"][0]["canonical_cells_compared_after_surface"] = 100
        for report in (partial, duplicate, synthetic, incomplete_full):
            self.assertFalse(verify.tension_matrix_receipt(report, self.profile)[1])

    def test_failed_world_missing_biome_or_dirty_stop_is_rejected(self):
        for key, value in (("passed", False), ("passed", 1), ("criteria_met", False),
                           ("old_integrated_server_stopped", False), ("clean_generation_before_world_disconnect", False)):
            report = copy.deepcopy(self.report)
            report["worlds"][0][key] = value
            with self.subTest(key=key, value=value):
                self.assertFalse(verify.tension_matrix_receipt(report, self.profile)[1])
        report = copy.deepcopy(self.report)
        report["worlds"][0]["natural_full"]["four_natural_plots"][0]["biome"] = "pale_gardens"
        self.assertFalse(verify.tension_matrix_receipt(report, self.profile)[1])

    def test_native_world_and_screenshot_files_are_required(self):
        save = self.profile / "saves" / self.report["worlds"][0]["save_name"] / "level.dat"
        save.unlink()
        self.assertFalse(verify.tension_matrix_receipt(self.report, self.profile)[1])
        save.write_bytes(b"unit fixture")
        (self.profile / "screenshots" / self.report["worlds"][0]["captures"][0]["file"]).unlink()
        self.assertFalse(verify.tension_matrix_receipt(self.report, self.profile)[1])

    def test_main_task_gate_rejects_positive_flag_without_six_full_worlds(self):
        code, summary, record = EquipmentVerificationTests.exercise(self, "runTensionRealmSmoke", reports={"v6-full-matrix-validation.json": {"passed": True}})
        self.assertEqual(code, 1)
        self.assertFalse(summary["passed"])
        self.assertTrue(record["tension_matrix_errors"])


if __name__ == "__main__":
    unittest.main()

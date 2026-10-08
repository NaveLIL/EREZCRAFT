#!/usr/bin/env python3
"""Exercise failure accounting without starting Java or touching game profiles."""
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import verify


class VerificationResultTests(unittest.TestCase):
    def exercise(self, task, log="", result=None, spawn_error=None, before=None, after=None, active=()):
        with tempfile.TemporaryDirectory(prefix="interstice-verifier-test-") as directory:
            root = Path(directory)

            class Process:
                pid = 123

                def wait(self):
                    return 0

            def spawn(command, **kwargs):
                self.command = command
                self.environment = kwargs["env"]
                if spawn_error is not None:
                    raise spawn_error
                kwargs["stdout"].write(log)
                if result is not None:
                    evidence = next((root / ".verification").iterdir())
                    if task in ("runRiftPersistenceSmoke", "runKeyPersistenceSmoke", "runWatchpostPersistenceSmoke", "runSurvivalPreparationSmoke", "runVaultPersistenceSmoke", "runGardenPersistenceSmoke", "runCrownFoodSmoke", "runLivingRealmSmoke", "runMiningSmoke"):
                        profile = {"runRiftPersistenceSmoke": "riftPersistence", "runKeyPersistenceSmoke": "keyPersistence", "runWatchpostPersistenceSmoke": "watchpostPersistence", "runSurvivalPreparationSmoke": "survivalRoute", "runVaultPersistenceSmoke": "vaultPersistence", "runGardenPersistenceSmoke": "gardenPersistence", "runCrownFoodSmoke":"crownFood","runLivingRealmSmoke":"livingRealm", "runMiningSmoke":"miningSmoke"}[task]
                        destination = evidence / "profiles" / profile
                        destination.mkdir(parents=True)
                        for name, value in result.items():
                            (destination / name).write_text(json.dumps(value), encoding="utf-8")
                    elif task == 'runHydrologySmoke':
                        destination = evidence / 'profiles/hydrologySmoke'
                        destination.mkdir(parents=True)
                        (destination / 'hydrology-validation.json').write_text(json.dumps(result), encoding='utf-8')
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
        code,_,summary=self.exercise('build',log='All 80 required tests passed :)\n',before={key:1},after={key:2},active=['build/playtest/saves/owned'])
        self.assertEqual(code,0)
        self.assertIsNone(summary['saves_unchanged'])
        self.assertTrue(summary['inactive_saves_unchanged'])

    def test_active_world_does_not_exempt_another_protected_save(self):
        key='build/playtest/saves/other/level.dat'
        code,_,summary=self.exercise('build',log='All 80 required tests passed :)\n',before={key:1},after={key:2},active=['build/playtest/saves/owned'])
        self.assertEqual(code,1)
        self.assertFalse(summary['inactive_saves_unchanged'])

    def test_inactive_save_change_still_rejects_checks(self):
        code,_,summary=self.exercise('build',log='All 80 required tests passed :)\n',before={'run/world/level.dat':1},after={'run/world/level.dat':2})
        self.assertEqual(code,1)
        self.assertFalse(summary['saves_unchanged'])

    def test_windows_uses_direct_java_wrapper(self):
        with patch.object(verify.sys, "platform", "win32"):
            code, _, _ = self.exercise("build", log="All 80 required tests passed :)\n")
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
        for task in ('runLivingGameTestServer','runEcologyGameTestServer','runMiningGameTestServer'):
            self.assertEqual(self.exercise(task,log='BUILD SUCCESSFUL\n')[0],1)
            self.assertEqual(self.exercise(task,log='All 6 required tests passed :)\n')[0],0)

    def test_mining_requires_both_cold_restart_reports(self):
        for reports, expected in (({'mining-create-validation.json':{'passed':True}},1),
                                  ({'mining-create-validation.json':{'passed':True},'mining-reload-validation.json':{'passed':False}},1),
                                  ({'mining-create-validation.json':{'passed':True},'mining-reload-validation.json':{'passed':True}},0)):
            with self.subTest(reports=reports):
                self.assertEqual(self.exercise('runMiningSmoke',result=reports)[0],expected)

    def test_garden_cold_restart_requires_both_reports(self):
        for reports, expected in (({"garden-create-validation.json": {"passed": True}}, 1),
                                  ({"garden-create-validation.json": {"passed": True}, "garden-reload-validation.json": {"passed": False}}, 1),
                                  ({"garden-create-validation.json": {"passed": True}, "garden-reload-validation.json": {"passed": True}}, 0)):
            with self.subTest(reports=reports):
                code, _, _ = self.exercise("runGardenPersistenceSmoke", result=reports)
                self.assertEqual(code, expected)

    def test_unix_uses_shell_wrapper(self):
        with patch.object(verify.sys, "platform", "linux"):
            code, _, _ = self.exercise("build", log="All 80 required tests passed :)\n")
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


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""Exercise failure accounting without starting Java or touching game profiles."""
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import verify


class VerificationResultTests(unittest.TestCase):
    def exercise(self, task, log="", result=None, spawn_error=None):
        with tempfile.TemporaryDirectory(prefix="interstice-verifier-test-") as directory:
            root = Path(directory)

            class Process:
                pid = 123

                def wait(self):
                    return 0

            def spawn(command, **kwargs):
                if spawn_error is not None:
                    raise spawn_error
                kwargs["stdout"].write(log)
                if result is not None:
                    destination = next((root / ".verification").iterdir()) / "profiles/islandSmoke"
                    destination.mkdir(parents=True)
                    (destination / "island-validation.json").write_text(json.dumps(result))
                return Process()

            def output(command, **kwargs):
                if command[1] == "rev-parse":
                    return "fixture-commit\n"
                return b""

            caught = None
            with patch.object(verify, "ROOT", root), patch.object(verify, "saves", return_value={}), \
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
            return code, caught, json.loads((evidence / "summary.json").read_text())

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


if __name__ == "__main__":
    unittest.main()

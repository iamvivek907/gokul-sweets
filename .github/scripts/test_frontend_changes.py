import importlib.util
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("frontend_changes", Path(__file__).with_name("frontend-changes.py"))
scope = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scope)


class FrontendScopeTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.previous = os.getcwd()
        os.chdir(self.folder.name)
        self.git("init", "-q")
        self.git("config", "user.email", "ci@example.invalid")
        self.git("config", "user.name", "CI test")
        self.base = self.commit("backend/start.txt")

    def tearDown(self):
        os.chdir(self.previous)
        self.folder.cleanup()

    def git(self, *args):
        return subprocess.check_output(["git", *args], text=True).strip()

    def commit(self, name):
        path = Path(name)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(name)
        self.git("add", "--all")
        self.git("commit", "-qm", "fixture")
        return self.git("rev-parse", "HEAD")

    def push_paths(self, head):
        return scope.changed_paths("push", {"before": self.base, "after": head})

    def test_backend_and_documentation_do_not_trigger_frontend(self):
        self.commit("backend/controller.java")
        paths = self.push_paths(self.commit("docs/guide.md"))
        self.assertFalse(any(map(scope.affects_frontend, paths)))

    def test_entire_push_range_includes_earlier_frontend_commit(self):
        self.commit("frontend/app/page.tsx")
        paths = self.push_paths(self.commit("backend/controller.java"))
        self.assertTrue(any(map(scope.affects_frontend, paths)))

    def test_pr_uses_merge_base_not_unrelated_new_base_changes(self):
        head = self.commit("backend/controller.java")
        self.git("checkout", "-q", "-b", "base-advanced", self.base)
        base = self.commit("frontend/app/page.tsx")
        paths = scope.changed_paths("pull_request", {"pull_request": {"base": {"sha": base}, "head": {"sha": head}}})
        self.assertEqual(paths, ["backend/controller.java"])

    def test_move_out_of_frontend_still_runs_checks(self):
        self.base = self.commit("frontend/moved.txt")
        Path("docs").mkdir()
        self.git("mv", "frontend/moved.txt", "docs/moved.txt")
        self.git("commit", "-qm", "move fixture")
        self.assertTrue(any(map(scope.affects_frontend, self.push_paths(self.git("rev-parse", "HEAD")))))

    def test_ci_and_shared_build_inputs_trigger_checks(self):
        for path in ["frontend/package-lock.json", ".github/workflows/verify.yml", ".github/scripts/run-browser-suite.mjs", "tools/next-lint-glob/index.js", "vercel.json", ".npmrc"]:
            with self.subTest(path=path):
                self.assertTrue(scope.affects_frontend(path))

    def test_unknown_ranges_and_manual_runs_do_not_skip(self):
        for event, payload in [("workflow_dispatch", {}), ("push", {"before": "0" * 40, "after": self.base}), ("push", {"before": "f" * 40, "after": self.base})]:
            with self.subTest(event=event, payload=payload):
                self.assertIsNone(scope.changed_paths(event, payload))


if __name__ == "__main__":
    unittest.main()

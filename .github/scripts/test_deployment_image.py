import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name('build-deployment-image.sh').resolve()


class DeploymentImageTests(unittest.TestCase):
    def run_build(self, failures):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary)
            docker = folder / 'docker'
            docker.write_text('''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
calls = Path(os.environ['TEST_CALLS'])
entries = json.loads(calls.read_text()) if calls.exists() else []
entries.append(sys.argv[1:]); calls.write_text(json.dumps(entries))
failures = json.loads(os.environ['TEST_FAILURES'])
mode = failures[len(entries)-1] if len(entries) <= len(failures) else 'success'
if mode == 'rate':
 print('ERROR: failed to resolve source metadata for temurin: 429 Too Many Requests')
 sys.exit(19)
if mode == 'compiler':
 print('Java compilation failed')
 sys.exit(42)
if mode == 'unrelated429':
 print('Dependency download: 429 Too Many Requests')
 sys.exit(43)
print('Deployment image built')
''')
            docker.chmod(0o755)
            sleep = folder / 'sleep'
            sleep.write_text('#!/bin/sh\nexit 0\n')
            sleep.chmod(0o755)
            env = dict(os.environ, PATH=str(folder) + os.pathsep + os.environ['PATH'],
                       TEST_CALLS=str(folder / 'calls.json'), TEST_FAILURES=json.dumps(failures))
            result = subprocess.run(['bash', str(SCRIPT)], env=env, text=True, capture_output=True)
            return result, json.loads((folder / 'calls.json').read_text())

    def test_success_builds_the_actual_uncached_image(self):
        result, calls = self.run_build([])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(len(calls), 1)
        self.assertIn('--pull', calls[0])
        self.assertIn('--no-cache', calls[0])
        self.assertIn('JAVA_BUILD_IMAGE=public.ecr.aws/docker/library/eclipse-temurin:21-jdk', calls[0])
        self.assertEqual(calls[0][-3:], ['--tag', 'gokul-render-ci', 'backend'])

    def test_registry_limit_falls_back_to_docker_hub(self):
        result, calls = self.run_build(['rate'])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(len(calls), 2)
        self.assertIn('JAVA_BUILD_IMAGE=docker.io/library/eclipse-temurin:21-jdk', calls[1])
        self.assertIn('JAVA_RUNTIME_IMAGE=docker.io/library/eclipse-temurin:21-jre', calls[1])

    def test_compiler_failure_is_not_swallowed_by_tee_or_retried(self):
        result, calls = self.run_build(['compiler'])
        self.assertEqual(result.returncode, 42)
        self.assertEqual(len(calls), 1)

    def test_exhausted_registry_limits_fail_the_build(self):
        result, calls = self.run_build(['rate', 'rate', 'rate'])
        self.assertEqual(result.returncode, 19)
        self.assertEqual(len(calls), 3)

    def test_non_registry_429_is_not_retried(self):
        result, calls = self.run_build(['unrelated429'])
        self.assertEqual(result.returncode, 43)
        self.assertEqual(len(calls), 1)


if __name__ == '__main__':
    unittest.main()

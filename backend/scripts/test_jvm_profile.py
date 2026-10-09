"""Exercise the actual entrypoint with fake cgroup reads and Java, without a JVM."""
import os
import pathlib
import subprocess
import tempfile
import unittest

class JvmProfileTest(unittest.TestCase):
    def run_profile(self, limit, override=None):
        with tempfile.TemporaryDirectory() as directory:
            folder = pathlib.Path(directory)
            for name, body in [('cat', '#!/bin/sh\nprintf "%s" "$TEST_MEMORY_LIMIT"\n'), ('java', '#!/bin/sh\nprintf "%s\\n" "$JAVA_TOOL_OPTIONS" "$@"\n')]:
                script = folder / name
                script.write_text(body)
                script.chmod(0o755)
            env = dict(os.environ, PATH=directory + ':' + os.environ['PATH'], TEST_MEMORY_LIMIT=limit)
            env.pop('JAVA_TOOL_OPTIONS', None)
            if override is not None:
                env['JAVA_TOOL_OPTIONS'] = override
            return subprocess.check_output(['sh', str(pathlib.Path(__file__).parents[1] / 'docker-entrypoint.sh'), 'extra'], env=env, text=True)

    def test_512_mib_keeps_small_heap(self):
        self.assertIn('-Xmx192m -XX:+UseSerialGC', self.run_profile('536870912'))

    def test_2_gib_selects_candidate(self):
        self.assertIn('-Xmx768m -XX:+UseG1GC', self.run_profile('2147483648'))

    def test_unlimited_keeps_conservative_profile(self):
        self.assertIn('-Xmx192m', self.run_profile('max'))

    def test_operator_override_and_arguments_are_preserved(self):
        output = self.run_profile('2147483648', '-Xmx160m')
        self.assertTrue(output.startswith('-Xmx160m\n'))
        self.assertTrue(output.endswith('-jar\n/app/app.jar\nextra\n'))

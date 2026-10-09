"""Exercise the actual entrypoint with fake cgroup reads and Java, without a JVM."""
import os
import pathlib
import subprocess
import tempfile
import unittest

class JvmProfileTest(unittest.TestCase):
    def run_profile(self, limit, override=None, version=2, gc=False):
        with tempfile.TemporaryDirectory() as directory:
            folder = pathlib.Path(directory)
            java = folder / 'java'
            java.write_text('#!/bin/sh\nprintf "%s\\n" "$JAVA_TOOL_OPTIONS" "$@"\n')
            java.chmod(0o755)
            # Exercise entrypoint logic against actual fixture files; runner cgroup
            # paths may be unreadable, absent, or use a different hierarchy.
            original = pathlib.Path(__file__).parents[1] / 'docker-entrypoint.sh'
            source = original.read_text()
            for current, path in [(2, '/sys/fs/cgroup/memory.max'),
                                  (1, '/sys/fs/cgroup/memory/memory.limit_in_bytes')]:
                fixture = folder / ('memory-v' + str(current))
                if limit is not None and current == version:
                    fixture.write_text(limit)
                source = source.replace(path, str(fixture))
            entrypoint = folder / 'entrypoint.sh'
            entrypoint.write_text(source)
            env = dict(os.environ, PATH=directory + ':' + os.environ['PATH'])
            env.pop('JAVA_TOOL_OPTIONS', None)
            env['GOKUL_GC_DIAGNOSTICS_ENABLED'] = 'true' if gc else 'false'
            if override is not None:
                env['JAVA_TOOL_OPTIONS'] = override
            return subprocess.check_output(['sh', str(entrypoint), 'extra'], env=env, text=True)

    def test_v1_limits_and_absent_cgroups(self):
        self.assertIn('-Xmx192m -XX:+UseSerialGC', self.run_profile('536870912', version=1))
        self.assertIn('-Xmx768m -XX:+UseG1GC', self.run_profile('2147483648', version=1))
        self.assertIn('-Xmx192m -XX:+UseSerialGC', self.run_profile(None))

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

    def test_gc_diagnostics_preserve_heap_override_and_application_arguments(self):
        output = self.run_profile('536870912', gc=True)
        self.assertIn('-Xmx192m -XX:+UseSerialGC', output)
        self.assertIn('-Xlog:gc*,safepoint=info:stdout:time,uptime,level,tags\n-jar', output)
        output = self.run_profile('536870912', '-Xmx160m', gc=True)
        self.assertTrue(output.startswith('-Xmx160m\n'))
        self.assertTrue(output.endswith('-jar\n/app/app.jar\nextra\n'))
        self.assertNotIn('-Xlog:', self.run_profile('536870912'))

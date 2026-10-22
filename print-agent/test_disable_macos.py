"""Exercise the standalone shell control with fake macOS utilities, without stopping real jobs."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


@unittest.skipIf(os.name == "nt", "macOS shell control requires Unix paths and executable fixtures")
class DisableMacAlarmTest(unittest.TestCase):
    def run_control(self, installed=True, mode='running', platform='Darwin'):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            bins = root / 'bin'
            bins.mkdir()
            home = root / 'home'
            agents = home / 'Library/LaunchAgents'
            agents.mkdir(parents=True)
            if installed:
                (agents / 'in.gokulsweets.alarm.12.plist').write_text('fixture')
                (agents / 'unrelated.app.plist').write_text('fixture')
            for name, body in {
                'uname': f'echo {platform}',
                'id': 'echo 501',
                'launchctl': '''printf '%s\\n' "$*" >> "$TEST_CALLS"
case "$1" in
 disable) [ "$TEST_MODE" != disable-failure ] ;;
 bootout) [ "$TEST_MODE" = running ] ;;
 print) [ "$TEST_MODE" = still-running ] ;;
esac'''
            }.items():
                file = bins / name
                file.write_text('#!/bin/bash\n' + body + '\n')
                file.chmod(0o755)
            calls = root / 'calls'
            env = dict(os.environ, HOME=str(home), PATH=str(bins) + os.pathsep + os.environ['PATH'],
                       TEST_CALLS=str(calls), TEST_MODE=mode)
            result = subprocess.run(['bash', str(Path(__file__).with_name('disable-alert-macos.command'))],
                                    env=env, text=True, capture_output=True)
            return result, calls.read_text() if calls.exists() else ''

    def test_stops_only_gokul_services_and_preserves_resume_instructions(self):
        result, calls = self.run_control()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls.splitlines(), ['disable gui/501/in.gokulsweets.alarm.12',
                                             'bootout gui/501/in.gokulsweets.alarm.12'])
        self.assertIn('launchctl enable', result.stdout)
        self.assertIn('launchctl bootstrap', result.stdout)
        self.assertNotIn('unrelated', calls)

    def test_already_stopped_and_no_installed_services_are_safe(self):
        result, calls = self.run_control(mode='stopped')
        self.assertEqual(result.returncode, 0)
        self.assertIn('print gui/501/in.gokulsweets.alarm.12', calls)
        result, calls = self.run_control(installed=False)
        self.assertEqual(result.returncode, 0)
        self.assertEqual(calls, '')

    def test_reports_failure_if_service_still_running_or_disable_failed(self):
        for mode in ['still-running', 'disable-failure']:
            result, calls = self.run_control(mode=mode)
            self.assertEqual(result.returncode, 1)
            self.assertIn('Could not', result.stderr)
            if mode == 'disable-failure':
                self.assertNotIn('bootout', calls)

    def test_rejects_other_platforms_without_touching_services(self):
        result, calls = self.run_control(platform='Linux')
        self.assertEqual(result.returncode, 1)
        self.assertEqual(calls, '')

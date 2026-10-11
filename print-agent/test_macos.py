import plistlib
from pathlib import Path
import unittest
from macos import launch_definition, service


class MacInstallerTest(unittest.TestCase):
    def test_launch_agent_preserves_space_containing_paths_and_restarts_process(self):
        root = Path('/Users/shop/Library/Application Support/GokulAlarm/1')
        definition = launch_definition(root, '/Library/Frameworks/Python.framework/Versions/3.13/bin/python3', 'in.gokulsweets.alarm.1')
        restored = plistlib.loads(plistlib.dumps(definition))
        self.assertTrue(restored['KeepAlive'])
        self.assertTrue(restored['RunAtLoad'])
        self.assertFalse(restored['AbandonProcessGroup'])
        self.assertEqual(restored['ProgramArguments'][1], str(root / 'macos.py'))
        self.assertEqual(restored['ProgramArguments'][2], str(root / 'config.local.json'))
        self.assertNotIn('PRINT_AGENT_API_KEY', str(restored))

    def test_keychain_identity_is_scoped_to_backend_branch_and_agent(self):
        cfg = {'branch_id': 1, 'agent_id': 'kitchen', 'api_url': 'https://dev.example.test'}
        for key, value in [('branch_id', 2), ('agent_id', 'another'), ('api_url', 'https://prod.example.test')]:
            self.assertNotEqual(service(cfg), service(dict(cfg, **{key: value})))

    def test_fresh_python_receives_system_root_certificates(self):
        import tempfile
        from unittest.mock import Mock, patch
        from macos import install_certificates
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            certificate = '-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----\n'
            with patch('macos.subprocess.run', return_value=Mock(stdout=certificate)):
                install_certificates(root)
            self.assertEqual((root / 'cacert.pem').read_text(), certificate)
            with patch('macos.subprocess.run', return_value=Mock(stdout='')):
                with self.assertRaises(RuntimeError):
                    install_certificates(root)

    @unittest.skipUnless(__import__('sys').platform == 'darwin', 'requires macOS system certificates')
    def test_native_root_export_is_accepted_by_python_tls(self):
        import ssl
        import tempfile
        from macos import install_certificates
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            install_certificates(root)
            context = ssl.create_default_context(cafile=str(root / 'cacert.pem'))
            self.assertGreater(len(context.get_ca_certs()), 0)


@unittest.skipIf(__import__('sys').platform == 'win32', 'macOS installer uses Unix file locks')
class MacUpgradeTest(unittest.TestCase):
    def fixture(self, root):
        installed = root / 'installed'
        staged = installed / '.stage'
        staged.mkdir(parents=True)
        names = ('agent.py', 'alarm.py', 'macos.py', 'cacert.pem', 'config.local.json')
        for name in names:
            (installed / name).write_text('old-' + name)
            (staged / name).write_text('new-' + name)
        plist = root / 'launch.plist'
        plist.write_text('old-plist')
        new_plist = staged / 'launch.plist'
        new_plist.write_text('new-plist')
        (installed / 'config.local.json.alarm.json').write_text('cached-mode')
        return installed, staged, plist, new_plist, names

    def test_failed_activation_restores_every_file_plist_and_restarts_previous_service(self):
        import tempfile
        import subprocess
        from unittest.mock import Mock, patch
        from macos import activate
        with tempfile.TemporaryDirectory() as folder:
            installed, staged, plist, new_plist, names = self.fixture(Path(folder))
            starts = []
            def launch(args, **kwargs):
                if 'bootstrap' in args:
                    starts.append(plist.read_text())
                    if len(starts) == 1:
                        raise subprocess.CalledProcessError(1, args)
                return Mock(returncode=0)
            with patch('macos.subprocess.run', side_effect=launch):
                with self.assertRaisesRegex(RuntimeError, 'restored and restarted'):
                    activate(installed, staged, plist, new_plist, 'gui/1', 'alarm')
            self.assertEqual(starts, ['new-plist', 'old-plist'])
            for name in names:
                self.assertEqual((installed / name).read_text(), 'old-' + name)
            self.assertEqual((installed / 'config.local.json.alarm.json').read_text(), 'cached-mode')

    def test_partial_file_replacement_rolls_back_before_restarting(self):
        import tempfile
        import os
        from unittest.mock import Mock, patch
        from macos import activate
        with tempfile.TemporaryDirectory() as folder:
            installed, staged, plist, new_plist, names = self.fixture(Path(folder))
            replace = os.replace
            def fail_one(source, target):
                if Path(source) == staged / 'alarm.py':
                    raise OSError('disk write failed')
                replace(source, target)
            with patch('macos.os.replace', side_effect=fail_one), patch('macos.subprocess.run', return_value=Mock(returncode=0)):
                with self.assertRaisesRegex(RuntimeError, 'restored and restarted'):
                    activate(installed, staged, plist, new_plist, 'gui/1', 'alarm')
            for name in names:
                self.assertEqual((installed / name).read_text(), 'old-' + name)
            self.assertEqual(plist.read_text(), 'old-plist')

    def test_backup_failure_does_not_stop_existing_service(self):
        import tempfile
        from unittest.mock import patch
        from macos import activate
        with tempfile.TemporaryDirectory() as folder:
            installed, staged, plist, new_plist, names = self.fixture(Path(folder))
            with patch('macos.shutil.copyfile', side_effect=OSError('disk full')), patch('macos.subprocess.run') as launch:
                with self.assertRaises(OSError):
                    activate(installed, staged, plist, new_plist, 'gui/1', 'alarm')
                launch.assert_not_called()
            self.assertEqual((installed / 'agent.py').read_text(), 'old-agent.py')

    def test_overlapping_upgrade_cannot_mix_rollback_versions(self):
        import tempfile
        import shutil
        import subprocess
        from unittest.mock import Mock, patch
        from macos import activate, installation_lock
        with tempfile.TemporaryDirectory() as folder:
            installed, staged, plist, new_plist, names = self.fixture(Path(folder))
            other = installed / '.other'
            other.mkdir()
            for name in names:
                (other / name).write_text('other-' + name)
            (other / 'launch.plist').write_text('other-plist')
            copy = shutil.copyfile
            attempts = []
            def overlap(source, target):
                result = copy(source, target)
                if Path(target) == staged / 'backup' / 'agent.py':
                    with self.assertRaisesRegex(RuntimeError, 'Another installer'):
                        activate(installed, other, plist, other / 'launch.plist', 'gui/1', 'alarm')
                    attempts.append(True)
                return result
            def launch(args, **kwargs):
                if 'bootstrap' in args and plist.read_text() == 'new-plist':
                    raise subprocess.CalledProcessError(1, args)
                return Mock(returncode=0)
            with patch('macos.shutil.copyfile', side_effect=overlap), patch('macos.subprocess.run', side_effect=launch):
                with self.assertRaisesRegex(RuntimeError, 'restored and restarted'):
                    activate(installed, staged, plist, new_plist, 'gui/1', 'alarm')
            self.assertEqual(attempts, [True])
            self.assertFalse((other / 'backup').exists())
            for name in names:
                self.assertEqual((installed / name).read_text(), 'old-' + name)
            self.assertEqual(plist.read_text(), 'old-plist')
            with installation_lock(installed):
                pass

    def test_process_crash_releases_lock_without_removing_lock_file(self):
        import tempfile
        import subprocess
        import sys
        from macos import installation_lock
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            script = 'from pathlib import Path; from macos import installation_lock; import sys,time\nwith installation_lock(Path(sys.argv[1])):\n print("locked", flush=True)\n time.sleep(30)\n'
            process = subprocess.Popen([sys.executable, '-c', script, folder],
                cwd=str(Path(__file__).parent), stdout=subprocess.PIPE, text=True)
            try:
                self.assertEqual(process.stdout.readline().strip(), 'locked')
                with self.assertRaisesRegex(RuntimeError, 'Another installer'):
                    with installation_lock(root):
                        pass
            finally:
                process.kill()
                process.wait(timeout=5)
                process.stdout.close()
            self.assertTrue((root / '.install.lock').exists())
            with installation_lock(root):
                pass

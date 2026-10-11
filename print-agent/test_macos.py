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

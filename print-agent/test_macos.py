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

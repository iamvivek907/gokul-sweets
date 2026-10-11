"""Exercise installer control flow without modifying the host's Python or Keychain."""
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest


@unittest.skipIf(sys.platform == 'win32', 'macOS shell bootstrap requires bash')
class BootstrapTest(unittest.TestCase):
    def run_installer(self, failure='', existing=False):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = Path(__file__).with_name('install-macos.command').read_text()
            # Substitute only host tools in a copy; the production script uses fixed Apple paths.
            source = re.sub(r'for candidate in .*; do', 'for candidate in "' + str(root / 'python3') + '"; do', source)
            source = source.replace("python_runtime='/Library/Frameworks/Python.framework/Versions/3.13/bin/python3'", "python_runtime='" + str(root / 'python3') + "'")
            for tool in ('curl', 'shasum', 'grep', 'pkgutil', 'installer'):
                source = source.replace('/usr/bin/' + tool, str(root / tool)).replace('/usr/sbin/' + tool, str(root / tool))
            (root / 'install.command').write_text(source)
            scripts = {
                'uname': '#!/bin/bash\necho Darwin\n',
                'python3': '#!/bin/bash\nif [[ "$1" == macos.py ]]; then touch "$FIXTURE/continued"; exit 0; fi\n[[ "$EXISTING" == yes || -f "$FIXTURE/ready" ]]\n',
                'curl': '#!/bin/bash\n[[ "$FAILURE" != download ]] || exit 22\nwhile [[ "$1" != --output ]]; do shift; done\ntouch "$2"\n',
                'shasum': '#!/bin/bash\nif [[ "$FAILURE" == checksum ]]; then echo bad; else echo "30666509020b4da0dd8bc2e773255f34d76b7bb80b66960a928d5f6daa0192d7  python.pkg"; fi\n',
                'pkgutil': '#!/bin/bash\nif [[ "$FAILURE" == publisher ]]; then echo wrong; else echo "Developer ID Installer: Python Software Foundation"; fi\n',
                'sudo': '#!/bin/bash\n[[ "$FAILURE" != privilege ]] || exit 1\n"$@"\n',
                'installer': '#!/bin/bash\ntouch "$FIXTURE/ready" "$FIXTURE/installed"\n',
            }
            for name, script in scripts.items():
                path = root / name
                path.write_text(script)
                path.chmod(0o755)
            # grep is a real host utility; the rest are controlled simulations.
            (root / 'grep').symlink_to('/usr/bin/grep')
            result = subprocess.run(['bash', str(root / 'install.command')], input='\n', capture_output=True, text=True,
                env=dict(os.environ, PATH=str(root) + ':' + os.environ['PATH'], FIXTURE=str(root),
                         FAILURE=failure, EXISTING='yes' if existing else 'no'))
            return result.returncode, (root / 'installed').exists(), (root / 'continued').exists()

    def test_usable_python_skips_install_and_continues(self):
        self.assertEqual(self.run_installer(existing=True), (0, False, True))

    def test_missing_or_old_python_installs_then_continues(self):
        self.assertEqual(self.run_installer(), (0, True, True))

    def test_unverified_or_failed_download_never_installs(self):
        for failure in ('download', 'checksum', 'publisher', 'privilege'):
            with self.subTest(failure=failure):
                code, installed, continued = self.run_installer(failure)
                self.assertNotEqual(code, 0)
                self.assertFalse(installed)
                self.assertFalse(continued)

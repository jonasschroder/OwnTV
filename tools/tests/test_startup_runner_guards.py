"""Startup integration must refuse owner devices/data before building or installing anything."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

RUNNER = Path(__file__).resolve().parents[1] / 'run-startup-regressions.sh'


class StartupRunnerGuardsTest(unittest.TestCase):
    def test_non_ci_physical_and_existing_qa_are_refused_before_mutation(self):
        for ci, serial, qemu, existing, reason in [
            ('false', 'emulator-5554', '1', '', 'CI emulator required'),
            ('true', 'owner-chromecast', '0', '', 'Physical devices refused'),
            ('true', 'emulator-5554', '0', '', 'Physical devices refused'),
            ('true', 'emulator-5554', '1', 'package:/fixture/installed-qa.apk', 'Existing QA installation refused'),
        ]:
            with self.subTest(ci=ci, serial=serial, qemu=qemu, existing=bool(existing)), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                marker = root / 'mutation-attempted'
                adb = root / 'adb'
                adb.write_text('''#!/bin/bash
case "$*" in
  get-serialno) printf '%s\\n' "$STARTUP_TEST_SERIAL" ;;
  'shell getprop ro.kernel.qemu') printf '%s\\n' "$STARTUP_TEST_QEMU" ;;
  'shell pm path se.jonasschroder.mintv.qa') printf '%s' "$STARTUP_TEST_EXISTING" ;;
  *) touch "$STARTUP_TEST_MUTATION"; exit 88 ;;
esac
''')
                adb.chmod(0o755)
                gradle = root / 'gradlew'
                gradle.write_text('#!/bin/bash\ntouch "$STARTUP_TEST_MUTATION"\nexit 89\n')
                gradle.chmod(0o755)
                env = {**os.environ, 'PATH': str(root) + os.pathsep + os.environ['PATH'],
                       'GITHUB_ACTIONS': ci, 'STARTUP_TEST_SERIAL': serial, 'STARTUP_TEST_QEMU': qemu,
                       'STARTUP_TEST_EXISTING': existing, 'STARTUP_TEST_MUTATION': str(marker)}
                result = subprocess.run(['bash', str(RUNNER)], cwd=root, env=env, capture_output=True, text=True, timeout=10)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(reason, result.stdout + result.stderr)
                self.assertFalse(marker.exists(), 'No build, install, force-stop or device mutation may precede refusal')
                self.assertFalse((root / 'startup-evidence').exists())

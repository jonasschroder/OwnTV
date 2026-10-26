import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / 'tools/ci-i18n-gradle.sh'
TASKS = [':app:assembleStandardDebug', ':app:assembleStandardRelease', ':app:lintStandardRelease', ':app:assembleQaRelease', ':app:lintQaRelease']

class I18nBuildRunnerTest(unittest.TestCase):
    def invoke(self, tasks, failure=0):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'timeout').write_text('#!/bin/sh\nprintf "%s\\n" "$@" > timeout-args\nshift 3\nexec "$@"\n')
            (root / 'gradlew').write_text('#!/bin/sh\nprintf "%s\\n" "$@" > gradle-args\nexit "${FIXTURE_EXIT:-0}"\n')
            for p in ['timeout', 'gradlew']:
                (root / p).chmod(0o755)
            env = dict(os.environ, PATH=folder + os.pathsep + os.environ['PATH'], FIXTURE_EXIT=str(failure))
            result = subprocess.run(['bash', str(RUNNER), *tasks], cwd=root, env=env, capture_output=True, text=True)
            args = (root / 'gradle-args').read_text().splitlines() if (root / 'gradle-args').exists() else []
            timeout = (root / 'timeout-args').read_text().splitlines() if (root / 'timeout-args').exists() else []
            return result.returncode, args, timeout

    def test_every_identity_and_lint_task_is_preserved_and_bounded(self):
        workflow = (ROOT / '.github/workflows/i18n.yml').read_text()
        for task in TASKS:
            with self.subTest(task=task):
                code, args, timeout = self.invoke([task])
                self.assertEqual(0, code)
                self.assertEqual(task, args[0])
                self.assertIn('--max-workers=1', args)
                self.assertIn('--no-configuration-cache', args)
                self.assertIn('-Pkotlin.compiler.execution.strategy=in-process', args)
                self.assertEqual(['--signal=QUIT', '--kill-after=30s', '25m'], timeout[:3])
                self.assertIn('bash tools/ci-i18n-gradle.sh ' + task, workflow)
        self.assertIn('check_pseudo_locales.py --apk "$DBG" --mode debug', workflow)
        self.assertIn('check_pseudo_locales.py --apk "$QA" --mode release', workflow)
        self.assertIn('tools/signing/test_signer.py', workflow)
        self.assertNotIn('continue-on-error', workflow)

    def test_failed_required_build_stays_failed(self):
        self.assertEqual(17, self.invoke([TASKS[0]], failure=17)[0])

    def test_combined_or_unknown_tasks_are_rejected(self):
        self.assertEqual(2, self.invoke(TASKS[:2])[0])
        self.assertEqual(2, self.invoke(['help'])[0])

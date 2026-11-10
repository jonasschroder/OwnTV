"""Reproduce the real merge-base failure when main advances; no network or skipped ratchet."""
from pathlib import Path
import re
import shlex
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class I18nHistoryTest(unittest.TestCase):
    def test_full_checkout_keeps_merge_base_after_refreshing_advanced_main(self):
        def git(cwd, *args, check=True):
            return subprocess.run(['git', *args], cwd=cwd, check=check, capture_output=True, text=True)
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            origin = root / 'origin'; origin.mkdir()
            git(origin, 'init', '-qb', 'main')
            git(origin, 'config', 'user.name', 'Synthetic test')
            git(origin, 'config', 'user.email', 'synthetic@example.invalid')
            (origin / 'file').write_text('base')
            git(origin, 'add', 'file'); git(origin, 'commit', '-qm', 'base')
            base = git(origin, 'rev-parse', 'HEAD').stdout.strip()
            git(origin, 'checkout', '-qb', 'feature')
            (origin / 'feature').write_text('feature')
            git(origin, 'add', 'feature'); git(origin, 'commit', '-qm', 'feature')
            git(origin, 'checkout', '-q', 'main')
            (origin / 'file').write_text('advanced main')
            git(origin, 'commit', '-qam', 'advance main')
            for name in ['broken', 'fixed']:
                git(root, 'clone', '-q', '--no-local', str(origin), name)
                clone = root / name
                git(clone, 'checkout', '-q', 'feature')
                self.assertEqual(base, git(clone, 'merge-base', 'HEAD', 'origin/main').stdout.strip())
                if name == 'broken':
                    git(clone, 'fetch', '-q', '--depth=1', 'origin', 'main')
                    self.assertNotEqual(0, git(clone, 'merge-base', 'HEAD', 'origin/main', check=False).returncode)
                else:
                    workflow = (ROOT / '.github/workflows/i18n.yml').read_text()
                    self.assertIn('fetch-depth: 0', workflow)
                    command = re.search(r'run: (git fetch --no-tags[^\n]+)', workflow).group(1)
                    args = shlex.split(command.replace('${GITHUB_BASE_REF}', 'main'))
                    subprocess.run(args, cwd=clone, capture_output=True, check=True)
                    self.assertEqual(base, git(clone, 'merge-base', 'HEAD', 'origin/main').stdout.strip())
                    self.assertIn('verify-ci --base-sha "$BASE_SHA"', workflow)

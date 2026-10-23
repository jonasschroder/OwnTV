"""Repository/commit pin guards with real Git repositories and a local-only fetch transport."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = "https://github.com/jonasschroder/OwnTV_Core.git"
UPSTREAM = "https://github.com/ahXN00/OwnTV_Core.git"
GIT = shutil.which("git")


class CorePinTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.origin = self.root / "fixture-origin"
        self.origin.mkdir()
        self.git(self.origin, "init", "-q")
        self.git(self.origin, "config", "user.name", "Fixture")
        self.git(self.origin, "config", "user.email", "fixture@example.invalid")
        (self.origin / "marker").write_text("baseline")
        self.git(self.origin, "add", "marker")
        self.git(self.origin, "commit", "-qm", "baseline")
        self.base = self.git(self.origin, "rev-parse", "HEAD")
        (self.origin / "marker").write_text("subtitle fix")
        self.git(self.origin, "commit", "-qam", "M4")
        self.sha = self.git(self.origin, "rev-parse", "HEAD")
        self.app = self.root / "app"
        (self.app / "tools").mkdir(parents=True)
        (self.app / "gradle").mkdir()
        shutil.copy2(ROOT / "tools/prepare-core.sh", self.app / "tools/prepare-core.sh")
        self.repository_pin = self.app / "gradle/owntv-core.repository"
        self.commit_pin = self.app / "gradle/owntv-core.commit"
        self.repository_pin.write_text(SOURCE + "\n")
        self.commit_pin.write_text(self.sha + "\n")
        self.cache = self.app / ".gradle-cache/OwnTV_Core"
        self.calls = self.root / "fetches"
        bindir = self.root / "bin"
        bindir.mkdir()
        shim = bindir / "git"
        shim.write_text("""#!/usr/bin/env python3
import os, subprocess, sys
args = sys.argv[1:]
if 'fetch' in args:
    with open(os.environ['PIN_TEST_CALLS'], 'a') as out: out.write(args[-1] + '\\n')
    if os.environ.get('PIN_TEST_FAIL'):
        print('https://credential:secret@private.invalid/private-repo', file=sys.stderr)
        sys.exit(1)
    assert args[-2] == 'origin'
    args[-2] = os.environ['PIN_TEST_ORIGIN']
sys.exit(subprocess.call([os.environ['PIN_TEST_GIT'], *args]))
""")
        shim.chmod(0o755)
        self.env = dict(os.environ, PATH=str(bindir) + os.pathsep + os.environ['PATH'],
                        PIN_TEST_GIT=GIT, PIN_TEST_ORIGIN=str(self.origin), PIN_TEST_CALLS=str(self.calls),
                        GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull)

    def git(self, directory, *args):
        env = dict(os.environ, GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull)
        return subprocess.check_output([GIT, "-C", str(directory), *args], env=env,
                                       stderr=subprocess.DEVNULL, text=True).strip()

    def run_prepare(self, expected=0):
        result = subprocess.run(["bash", "tools/prepare-core.sh"], cwd=self.app, env=self.env,
                                capture_output=True, text=True)
        self.assertEqual(expected, result.returncode, result.stdout + result.stderr)
        return result.stdout + result.stderr

    def populate(self):
        self.run_prepare()
        self.assertEqual(self.sha, self.git(self.cache, "rev-parse", "HEAD"))

    def test_fresh_fetch_and_clean_cache_reuse_exact_source_and_detached_commit(self):
        self.populate()
        self.assertEqual(SOURCE, self.git(self.cache, "remote", "get-url", "origin"))
        detached = subprocess.run([GIT, "-C", str(self.cache), "symbolic-ref", "-q", "HEAD"], capture_output=True)
        self.assertEqual(1, detached.returncode)
        self.run_prepare()
        self.assertEqual([self.sha], self.calls.read_text().splitlines())
        self.assertEqual("", self.git(self.cache, "status", "--porcelain"))

    def test_rejects_unapproved_or_credential_bearing_urls_without_echoing_them(self):
        for url in ["http://github.com/jonasschroder/OwnTV_Core.git", "git@github.com:jonasschroder/OwnTV_Core.git",
                    "https://credential:secret@github.com/jonasschroder/OwnTV_Core.git",
                    "https://github.com/other/OwnTV_Core.git", SOURCE + "\nmalformed"]:
            with self.subTest(url=url):
                self.repository_pin.write_text(url + "\n")
                output = self.run_prepare(1)
                self.assertNotIn(url, output)
                self.assertNotIn("secret", output)
                self.assertFalse(self.calls.exists())

    def test_rejects_floating_or_malformed_commit_before_fetch(self):
        for sha in ["main", "a" * 39, "A" * 40, self.sha[:20] + "\n" + self.sha[20:], self.sha + " "]:
            self.commit_pin.write_text(sha + "\n")
            self.run_prepare(1)
            self.assertFalse(self.calls.exists())

    def test_rejects_dirty_tracked_and_untracked_cache_without_modifying_data(self):
        self.populate()
        for name in ["marker", "untracked"]:
            path = self.cache / name
            path.write_text("keep this data")
            self.run_prepare(1)
            self.assertEqual("keep this data", path.read_text())
            if name == "marker":
                self.git(self.cache, "checkout", "--", "marker")
            else:
                path.unlink()
        self.assertEqual([self.sha], self.calls.read_text().splitlines())

    def test_rejects_different_commit_without_resetting_it(self):
        self.populate()
        self.commit_pin.write_text(self.base + "\n")
        self.run_prepare(1)
        self.assertEqual(self.sha, self.git(self.cache, "rev-parse", "HEAD"))

    def test_rejects_different_approved_repository_and_multiple_origins(self):
        self.populate()
        self.git(self.cache, "remote", "set-url", "origin", UPSTREAM)
        self.run_prepare(1)
        self.assertEqual(UPSTREAM, self.git(self.cache, "remote", "get-url", "origin"))
        self.git(self.cache, "remote", "set-url", "origin", SOURCE)
        self.git(self.cache, "config", "--add", "remote.origin.url", UPSTREAM)
        self.run_prepare(1)

    def test_rejects_attached_branch_even_at_the_correct_commit(self):
        self.populate()
        self.git(self.cache, "switch", "-c", "floating")
        self.run_prepare(1)
        self.assertEqual("floating", self.git(self.cache, "symbolic-ref", "--short", "HEAD"))

    def test_rejects_symlink_cache_without_touching_the_target(self):
        self.populate()
        preserved = self.root / "preserved"
        self.cache.rename(preserved)
        self.cache.symlink_to(preserved, target_is_directory=True)
        self.run_prepare(1)
        self.assertEqual(self.sha, self.git(preserved, "rev-parse", "HEAD"))

    def test_transport_failure_is_redacted_and_does_not_publish_partial_cache(self):
        self.env['PIN_TEST_FAIL'] = '1'
        output = self.run_prepare(1)
        self.assertNotIn('secret', output)
        self.assertNotIn('private.invalid', output)
        self.assertFalse(self.cache.exists())
        self.assertEqual([], list(self.cache.parent.glob('core-fetch.*')))

    def test_rejects_effective_url_rewrite_before_network_access(self):
        config = self.root / 'gitconfig'
        config.write_text('[url "https://private.invalid/"]\n\tinsteadOf = https://github.com/\n')
        self.env['GIT_CONFIG_GLOBAL'] = str(config)
        output = self.run_prepare(1)
        self.assertNotIn('private.invalid', output)
        self.assertFalse(self.calls.exists())
        self.assertFalse(self.cache.exists())

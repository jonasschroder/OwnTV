"""Real download-artifact directory layout; synthetic metadata/native adapters, never publication."""
import contextlib
import copy
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/signing'))
import distribution_guard as guard
from signing_policy import PACKAGES, sha256
from update_metadata import validate


class DistributionArtifactTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.pin = 'ab' * 32
        self.config = {'schema': 1, 'allow_shared_qa_production_certificate': True,
                       'qa': {'application_id': PACKAGES['qa'], 'certificate_sha256': self.pin},
                       'stable': {'application_id': PACKAGES['stable'], 'certificate_sha256': self.pin,
                                  'initial_adoption': 'owner-approved-clean-install'}}
        self.fixture('qa')

    def fixture(self, channel):
        self.channel = channel
        directory = self.root / channel
        directory.mkdir()
        self.candidate = directory / 'candidate'
        self.public = directory / 'public'
        self.output = directory / 'github-output'
        version = '0.2.0-beta.2' if channel == 'qa' else '0.2.0'
        prefix = 'MinTV-Test' if channel == 'qa' else 'MinTV'
        self.apk_name = f'{prefix}-v{version}-1000002-arm.apk'
        self.manifest_name = f'MinTV-{channel}-update.json'
        # Model upload-artifact's repo-root LCA, including all seven public/evidence paths.
        apk = io.BytesIO()
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('assets/SYNTHETIC-TEST-ONLY.txt', 'Never distribute.')
        with zipfile.ZipFile(directory / 'artifact.zip', 'w') as archive:
            archive.writestr('signed/' + self.apk_name, apk.getvalue())
            archive.writestr('signed/' + self.manifest_name, 'synthetic metadata adapter input')
            for name in ('SHA256SUMS', 'SIGNING-CERTIFICATE.txt', 'RELEASE-NOTES.md', 'release-candidate.json'):
                archive.writestr('signed/' + name, 'UNTRUSTED EVIDENCE: never use for verification')
            archive.writestr('evidence/ci-evidence.json', '{"untrusted":true}')
            archive.extractall(self.candidate)
        self.apk = self.candidate / 'signed' / self.apk_name
        self.manifest = self.candidate / 'signed' / self.manifest_name
        self.data = {'schema': 1, 'channel': channel, 'application_id': PACKAGES[channel],
                     'version_code': 1000002, 'version_name': version,
                     'source_commit': 'a' * 40, 'apk': self.apk_name, 'apk_sha256': sha256(self.apk),
                     'apk_bytes': self.apk.stat().st_size, 'min_sdk': 26,
                     'abis': ['arm64-v8a', 'armeabi-v7a'], 'notes': 'Authenticated synthetic notes.',
                     'tag': f'{channel}-1000002'}
        validate(self.data, channel)

    def native(self, args):
        if Path(args[0]).name == 'aapt2':
            return (f"package: name='{PACKAGES[self.channel]}' versionCode='1000002' "
                    f"versionName='{self.data['version_name']}'\nsdkVersion:'26'\n"
                    "native-code: 'arm64-v8a' 'armeabi-v7a'\n")
        if Path(args[0]).name == 'apksigner':
            return ('Verified using v2 scheme (APK Signature Scheme v2): true\n'
                    'Verified using v3 scheme (APK Signature Scheme v3): true\n'
                    f'Signer #1 certificate SHA-256 digest: {self.pin}\n')
        self.assertEqual(['git', 'merge-base', '--is-ancestor', self.data['source_commit'], 'origin/main'], args)
        return ''

    def run_guard(self, *, metadata=None, native=None, api=None):
        # Real crypto/hash fixtures have their own tests; these adapters isolate layout and gates.
        with patch.object(guard, 'verify', side_effect=metadata or (lambda *args: copy.deepcopy(self.data))) as verified, \
                patch.object(guard, 'command', side_effect=native or self.native) as command, \
                patch.object(guard, 'api', side_effect=api or (lambda path: [])) as network, \
                contextlib.redirect_stdout(io.StringIO()):
            guard.verify_candidate(self.candidate, Path('/synthetic-build-tools'), self.config,
                                   self.channel, 1000002, self.public, self.output)
            return verified, command, network

    def test_real_seven_file_layout_stages_only_verified_assets_for_both_channels(self):
        for channel in ('qa', 'stable'):
            with self.subTest(channel=channel):
                if channel == 'stable': self.fixture(channel)
                before = {file.relative_to(self.candidate): file.read_bytes()
                          for file in self.candidate.rglob('*') if file.is_file()}
                self.assertEqual(7, len(before))
                # Root-level files must not substitute for signed/ assets.
                (self.candidate / self.manifest_name).write_text('ROOT DECOY')
                (self.candidate / self.apk_name).write_text('ROOT DECOY')
                verified, command, network = self.run_guard()
                self.assertEqual(self.manifest, verified.call_args_list[0].args[0])
                self.assertEqual(self.apk, verified.call_args_list[1].args[3])
                self.assertEqual(self.manifest, verified.call_args_list[1].args[0])
                self.assertEqual(3, command.call_count)  # aapt2, apksigner and source ancestry
                self.assertEqual(2, network.call_count)  # release history and existing tag
                self.assertEqual({self.apk_name, self.manifest_name, 'SHA256SUMS', 'RELEASE-NOTES.md'},
                                 {file.name for file in self.public.iterdir()})
                self.assertEqual(self.apk.read_bytes(), (self.public / self.apk_name).read_bytes())
                self.assertEqual(self.manifest.read_bytes(), (self.public / self.manifest_name).read_bytes())
                self.assertEqual(self.data['notes'], (self.public / 'RELEASE-NOTES.md').read_text())
                self.assertIn('tag=' + self.data['tag'], self.output.read_text())
                for path, content in before.items():
                    self.assertEqual(content, (self.candidate / path).read_bytes())

    def test_flat_manifest_is_rejected_instead_of_silently_selected(self):
        self.manifest.rename(self.candidate / self.manifest_name)
        with self.assertRaisesRegex(guard.DistributionError, 'candidate/signed/MinTV-qa-update.json'):
            self.run_guard()
        self.assertFalse(self.public.exists())

    def test_apk_must_be_beside_authenticated_manifest(self):
        self.apk.rename(self.candidate / self.apk_name)
        with self.assertRaisesRegex(guard.DistributionError, 'candidate/signed/MinTV-Test'):
            self.run_guard()
        self.assertFalse(self.public.exists())

    def test_flat_artifact_directory_has_actionable_error(self):
        (self.candidate / 'signed').rename(self.candidate / 'unexpected')
        with self.assertRaisesRegex(guard.DistributionError, 'retain the downloaded artifact layout'):
            self.run_guard()

    def test_symlink_assets_and_traversal_are_rejected(self):
        for path in (self.manifest, self.apk):
            outside = self.root / path.name
            path.rename(outside)
            path.symlink_to(outside)
            with self.subTest(path=path.name), self.assertRaises(guard.DistributionError):
                guard.signed_file(self.candidate, path.name)
        with self.assertRaises(guard.DistributionError): guard.signed_file(self.candidate, '../outside.apk')
        signed = self.candidate / 'signed'
        signed.rename(self.candidate / 'real-signed')
        signed.symlink_to(self.candidate / 'real-signed', target_is_directory=True)
        with self.assertRaises(guard.DistributionError): guard.signed_file(self.candidate, self.manifest_name)

    def test_signature_and_hash_size_failures_cannot_stage_assets(self):
        secret = ValueError('PRIVATE-NATIVE-DETAIL-DO-NOT-LOG')
        for sequence, reason in (([secret], 'Authenticated update manifest'),
                                 ([self.data, secret], 'APK filename, size or SHA256')):
            with self.subTest(reason=reason), self.assertRaisesRegex(guard.DistributionError, reason) as error:
                self.run_guard(metadata=sequence)
            self.assertNotIn('PRIVATE', str(error.exception))
            self.assertFalse(self.public.exists())

    def test_wrong_version_is_rejected_before_staging(self):
        with self.assertRaisesRegex(guard.DistributionError, 'versionCode'):
            self.run_guard(metadata=lambda *args: {**self.data, 'version_code': 1000003})
        self.assertFalse(self.public.exists())

    def test_native_identity_signature_and_source_gates_remain_required(self):
        cases = [('aapt2', 'APK package'), ('apksigner', 'APK certificate'), ('git', 'source commit')]
        for tool, reason in cases:
            def native(args):
                if Path(args[0]).name == tool: raise ValueError('PRIVATE TOOL OUTPUT')
                return self.native(args)
            with self.subTest(tool=tool), self.assertRaisesRegex(guard.DistributionError, reason) as error:
                self.run_guard(native=native)
            self.assertNotIn('PRIVATE', str(error.exception))
            self.assertFalse(self.public.exists())

    def test_forbidden_signing_material_still_blocks_publication(self):
        with zipfile.ZipFile(self.apk, 'a') as archive: archive.writestr('assets/key.jks', 'SYNTHETIC')
        with self.assertRaisesRegex(guard.DistributionError, 'forbidden signing material'):
            self.run_guard()
        self.assertFalse(self.public.exists())

    def test_existing_release_and_tag_still_block_staging(self):
        for existing_release in (True, False):
            def api(path):
                return [{'tag_name': self.data['tag']}] if existing_release or path.startswith('git/') else []
            with self.subTest(release=existing_release), self.assertRaises(guard.DistributionError):
                self.run_guard(api=api)
            self.assertFalse(self.public.exists())

    def test_production_floor_is_preserved(self):
        self.fixture('stable')
        self.config['stable']['installed_v01_version_code'] = 1000002
        with self.assertRaisesRegex(guard.DistributionError, 'not newer than installed'):
            self.run_guard()
        self.assertFalse(self.public.exists())

    def test_safe_cli_diagnostics_never_echo_untrusted_exception_or_http_url(self):
        private = 'PRIVATE-TOKEN-DO-NOT-LOG'
        errors = [guard.DistributionError('Missing candidate/signed manifest'), ValueError(private)]
        for error in errors:
            output = io.StringIO()
            with patch.object(guard, 'main', side_effect=error), contextlib.redirect_stderr(output):
                self.assertEqual(1, guard.cli())
            self.assertIn('BLOCKED:', output.getvalue())
            self.assertIn('Nothing published.', output.getvalue())
            self.assertNotIn(private, output.getvalue())
            self.assertNotIn('Traceback', output.getvalue())
        http = urllib.error.HTTPError('https://example.invalid/' + private, 403, private, {}, None)
        def denied(): raise http
        with self.assertRaisesRegex(guard.DistributionError, 'GitHub HTTP 403') as error:
            guard.checked('Release history lookup failed', denied)
        self.assertNotIn(private, str(error.exception))


if __name__ == '__main__': unittest.main()

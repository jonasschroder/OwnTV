"""Read-only update gate regression checks with synthetic metadata command adapters."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class UpdateGateTest(unittest.TestCase):
    def gate(self, previous, candidate, package=None):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            files = []
            for name, metadata in [('installed.apk', previous), ('candidate.apk', candidate)]:
                file = folder / name
                file.write_text(json.dumps(metadata))
                files.append(str(file))
            adapter = folder / 'read-metadata'
            adapter.write_text('#!' + sys.executable + '\n' + '''import json, sys
v = json.load(open(sys.argv[-1]))
if 'verify' in sys.argv:
    if v.get('invalid'): sys.exit(1)
    if v.get('signer'): print('Signer #1: certificate SHA-256 digest: ' + v['signer'])
else:
    print("package: name='%s' versionCode='%s'" % (v['package'], v['version']))
''')
            adapter.chmod(0o700)
            command = [sys.executable, str(ROOT / 'tools/check-apk-update.py'), '--previous', files[0],
                       '--candidate', files[1], '--aapt2', str(adapter), '--apksigner', str(adapter)]
            if package: command += ['--application-id', package]
            return subprocess.run(command, capture_output=True, text=True)

    def data(self, package='se.jonasschroder.mintv.qa', version=20, signer='ab' * 32):
        return {'package': package, 'version': version, 'signer': signer}

    def test_qa_must_be_selected_explicitly(self):
        self.assertNotEqual(0, self.gate(self.data(), self.data(version=21)).returncode)
        self.assertEqual(0, self.gate(self.data(), self.data(version=21), 'se.jonasschroder.mintv.qa').returncode)

    def test_regular_identity_retains_default(self):
        self.assertEqual(0, self.gate(self.data('se.jonasschroder.mintv'), self.data('se.jonasschroder.mintv', 21)).returncode)

    def test_signature_mismatch_blocks_qa(self):
        result = self.gate(self.data(), self.data(version=21, signer='cd' * 32), 'se.jonasschroder.mintv.qa')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('signer certificates differ', result.stderr)

    def test_regular_cannot_be_replaced_by_qa(self):
        result = self.gate(self.data('se.jonasschroder.mintv'), self.data(version=21), 'se.jonasschroder.mintv.qa')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('application IDs differ', result.stderr)

    def test_equal_or_lower_version_is_rejected(self):
        for version in (19, 20):
            result = self.gate(self.data(), self.data(version=version), 'se.jonasschroder.mintv.qa')
            self.assertNotEqual(0, result.returncode)
            self.assertIn('versionCode must increase', result.stderr)

    def test_missing_or_unverified_certificate_is_rejected(self):
        for metadata in (self.data(version=21, signer=''), {**self.data(version=21), 'invalid': True}):
            result = self.gate(self.data(), metadata, 'se.jonasschroder.mintv.qa')
            self.assertNotEqual(0, result.returncode)
            self.assertIn('could not verify', result.stderr)


if __name__ == '__main__':
    unittest.main()

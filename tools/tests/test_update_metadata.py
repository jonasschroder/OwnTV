import base64
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/signing'))
from update_metadata import validate, verify
from distribution_guard import signing_run, published_history


class AuthenticatedMetadataTest(unittest.TestCase):
    def setUp(self):
        self.path = ROOT / 'app/src/test/resources/updates/qa.json'
        self.envelope = json.loads(self.path.read_text())
        self.data = json.loads(base64.b64decode(self.envelope['payload']))
        self.pin = hashlib.sha256(base64.b64decode(self.envelope['certificate'])).hexdigest()
        self.config = {'schema': 1, 'qa': {'application_id': 'se.jonasschroder.mintv.qa', 'certificate_sha256': self.pin},
                       'stable': {'application_id': 'se.jonasschroder.mintv', 'certificate_sha256': None}}

    def test_actual_public_fixture_signature(self):
        self.assertEqual(self.data, verify(self.path, self.config, 'qa'))

    def test_remote_cert_cannot_choose_trust_anchor(self):
        self.config['qa']['certificate_sha256'] = 'ab' * 32
        with self.assertRaises(ValueError): verify(self.path, self.config, 'qa')

    def test_tampered_authenticated_bytes_fail(self):
        self.envelope['payload'] = base64.b64encode(b'{}').decode()
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'manifest.json'; path.write_text(json.dumps(self.envelope))
            with self.assertRaises(ValueError): verify(path, self.config, 'qa')

    def test_package_channel_hash_size_notes_sdk_abi_and_names_are_bounded(self):
        for key, value in [('application_id', 'se.jonasschroder.mintv'), ('channel', 'stable'), ('version_code', True),
                           ('version_code', 1000000), ('apk_sha256', 'wrong'), ('apk_bytes', 200000001),
                           ('min_sdk', 35), ('abis', ['x86_64']), ('notes', 'x' * 16001), ('apk', '../evil.apk'),
                           ('source_commit', 'not-reviewed'), ('tag', 'qa-current')]:
            data = copy.deepcopy(self.data); data[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError): validate(data, 'qa')

    def test_only_approved_main_dispatch_can_be_published(self):
        run = {'event': 'workflow_dispatch', 'head_branch': 'main', 'path': '.github/workflows/mintv-sign.yml',
               'status': 'completed', 'conclusion': 'success'}
        jobs = [{'name': name, 'conclusion': 'success'} for name in ('preflight', 'unsigned-build', 'sign-qa')]
        signing_run(run, jobs, 'qa')
        for key, value in [('event', 'pull_request'), ('head_branch', 'feat/untrusted'), ('path', '.github/workflows/android.yml'),
                           ('status', 'in_progress'), ('conclusion', 'failure')]:
            changed = dict(run); changed[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError): signing_run(changed, jobs, 'qa')
        with self.assertRaises(ValueError): signing_run(run, jobs, 'stable')

    def test_publication_scans_other_channel_history_and_rejects_unproven_order(self):
        qa_page = [{'tag_name': 'qa-1000200'}] * 100
        requests = []
        def fetch(page):
            requests.append(page)
            return qa_page if page == 1 else [{'tag_name': 'stable-1000102'}]
        with self.assertRaises(ValueError): published_history('stable', 1000101, fetch)
        self.assertEqual([1, 2], requests)
        with self.assertRaises(ValueError): published_history('stable', 1000102, fetch)
        requests.clear()
        published_history('stable', 1000103, fetch)
        self.assertEqual([1, 2], requests)
        requests.clear()
        def full(page): requests.append(page); return qa_page
        with self.assertRaises(ValueError): published_history('stable', 1000103, full)
        self.assertEqual([1, 2, 3], requests)


if __name__ == '__main__': unittest.main()

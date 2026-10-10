"""Security boundaries of protected signing; synthetic metadata, no credentials."""
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/signing'))
from signing_policy import (PACKAGES, anchor, badging, check_certificates, check_identity,
                            fingerprint, version_code, version_name)
from sign_apk import REQUIRED, required_secrets
from release_guard import protected_environment, required_jobs, monotonic_history


class SigningPolicyTest(unittest.TestCase):
    def config(self):
        return {'schema': 1, 'qa': {'application_id': PACKAGES['qa'], 'certificate_sha256': 'ab' * 32},
                'stable': {'application_id': PACKAGES['stable'], 'certificate_sha256': 'cd' * 32,
                           'installed_v01_certificate_sha256': 'cd' * 32, 'installed_v01_version_code': 1}}

    def info(self, channel='qa'):
        return {'package': PACKAGES[channel], 'version_code': 1000001, 'version_name': '0.2.0-beta.1',
                'abis': ['arm64-v8a', 'armeabi-v7a'], 'min_sdk': 26, 'debuggable': False}

    def test_missing_pins_fail_closed_in_checked_in_configuration(self):
        current = json.loads((ROOT / 'config/mintv-signing.json').read_text())
        for channel in PACKAGES:
            # Before owner setup neither channel is allowed. After setup, validate actual pins instead.
            if current[channel]['certificate_sha256'] is None:
                with self.assertRaises(ValueError): anchor(current, channel)
            else:
                fingerprint(current[channel]['certificate_sha256'])

    def test_separate_qa_and_production_pins(self):
        cfg = self.config()
        self.assertNotEqual(anchor(cfg, 'qa'), anchor(cfg, 'stable'))
        cfg['stable']['certificate_sha256'] = cfg['qa']['certificate_sha256']
        for channel in PACKAGES:
            with self.assertRaises(ValueError): anchor(cfg, channel)

    def test_production_requires_existing_v01_identity_and_version(self):
        for key, value in [('installed_v01_certificate_sha256', None), ('installed_v01_certificate_sha256', 'ef' * 32),
                           ('installed_v01_version_code', None), ('installed_v01_version_code', True)]:
            cfg = self.config(); cfg['stable'][key] = value
            with self.assertRaises(ValueError): anchor(cfg, 'stable')

    def test_channel_ids_are_not_interchangeable(self):
        cfg = self.config(); cfg['qa']['application_id'] = PACKAGES['stable']
        with self.assertRaises(ValueError): anchor(cfg, 'qa')
        with self.assertRaises(ValueError): anchor(self.config(), 'unknown')

    def test_monotonic_codes_and_no_reruns(self):
        self.assertGreater(version_code(20, 1), version_code(19, 1))
        for number, attempt in [(0, 1), (-1, 1), (20, 2), (20, 0), (2_100_000_000, 1)]:
            with self.assertRaises(ValueError): version_code(number, attempt)

    def test_separate_version_names_and_no_shell_syntax(self):
        self.assertEqual('0.2.0', version_name('stable', '0.2.0'))
        self.assertEqual('0.2.0-beta.1', version_name('qa', '0.2.0-beta.1'))
        for channel, name in [('stable', '0.2.0-beta.1'), ('qa', '0.2.0'), ('qa', '0.2.0-beta.0'),
                              ('stable', '$(secret)'), ('stable', '01.2.0'), ('qa', '0.2.0-beta.1\n')]:
            with self.assertRaises(ValueError): version_name(channel, name)

    def test_out_of_order_queued_dispatch_cannot_issue_older_channel_code(self):
        runs = [{'id': 21, 'run_number': 21}, {'id': 20, 'run_number': 20}]
        jobs = lambda run: [{'name': 'sign-qa', 'conclusion': 'success'}]
        with self.assertRaises(ValueError): monotonic_history(runs, 20, 'qa', jobs)
        monotonic_history(runs, 20, 'stable', jobs)
        monotonic_history(runs, 21, 'qa', jobs)
        with self.assertRaises(ValueError): monotonic_history(runs, 19, 'qa', jobs)

    def test_missing_secrets_are_named_without_values(self):
        for missing in REQUIRED:
            env = {name: 'private-test-only-value' for name in REQUIRED if name != missing}
            with self.assertRaises(ValueError) as error: required_secrets(env)
            self.assertIn(missing, str(error.exception))
            self.assertNotIn('private-test-only-value', str(error.exception))

    def test_invalid_secret_encoding_size_is_rejected(self):
        for value in ('not-base64', '', 'A' * 120001, 'YWJj'):
            env = {name: 'private-test-only-value' for name in REQUIRED}
            env['MINTV_KEYSTORE_BASE64'] = value
            with self.assertRaises(ValueError): required_secrets(env)

    def test_package_code_name_abi_android_and_debug_contract(self):
        good = self.info(); check_identity(good, 'qa', 1000001, '0.2.0-beta.1')
        for key, value in [('package', PACKAGES['stable']), ('version_code', 1000000), ('version_code', 1000002),
                           ('version_name', '0.3.0-beta.1'), ('abis', ['x86_64']), ('abis', ['arm64-v8a']),
                           ('min_sdk', 35), ('debuggable', True)]:
            with self.assertRaises(ValueError): check_identity({**good, key: value}, 'qa', 1000001, '0.2.0-beta.1')

    def test_emulator_override_never_passes_distribution_verification(self):
        info = {**self.info(), 'abis': ['x86_64'], 'debuggable': True}
        check_identity(info, 'qa', 1000001, '0.2.0-beta.1', emulator_test=True)
        with self.assertRaises(ValueError): check_identity(info, 'qa', 1000001, '0.2.0-beta.1')

    def test_badging_is_mandatory_and_parsed_from_actual_tool_shape(self):
        sample = "package: name='se.jonasschroder.mintv.qa' versionCode='1000001' versionName='0.2.0-beta.1'\nsdkVersion:'26'\nnative-code: 'arm64-v8a' 'armeabi-v7a'\n"
        self.assertEqual(self.info(), badging(sample))
        self.assertEqual(self.info(), badging(sample.replace('sdkVersion', 'minSdkVersion')))
        for partial in ('', sample.replace('sdkVersion', 'other'), sample.replace('native-code:', 'other:')):
            with self.assertRaises(ValueError): badging(partial)

    def test_verified_v2_v3_and_exact_single_cert_required(self):
        sample = 'Verified using v2 scheme (APK Signature Scheme v2): true\nVerified using v3 scheme (APK Signature Scheme v3): true\nSigner #1 certificate SHA-256 digest: ' + 'ab' * 32 + '\n'
        check_certificates(sample, 'ab' * 32)
        modern = sample.replace('Signer #1', 'V3 Signer:') + 'Number of signers: 1\nV2 Signer: certificate SHA-256 digest: ' + 'ab' * 32 + '\n'
        check_certificates(modern, 'ab' * 32)
        for invalid in ('', sample.replace(': true', ': false'), sample.replace('ab' * 32, 'cd' * 32),
                        sample + 'Signer #2 certificate SHA-256 digest: ' + 'ef' * 32 + '\n',
                        modern.replace('Number of signers: 1', 'Number of signers: 2')):
            with self.assertRaises(ValueError): check_certificates(invalid, 'ab' * 32)

    def test_green_checks_require_exact_named_jobs_and_no_failures(self):
        expected = {'check-and-build', 'home-device-regressions', 'signing-upgrade-regressions'}
        good = [{'name': name, 'conclusion': 'success'} for name in expected]
        required_jobs(good, expected)
        for invalid in ([], good[:-1], [{**job, 'conclusion': 'failure'} for job in good],
                        good + [{'name': 'other', 'conclusion': 'cancelled'}]):
            with self.assertRaises(ValueError): required_jobs(invalid, expected)

    def test_human_approval_and_main_only_environment_mandatory(self):
        info = {'protection_rules': [{'type': 'required_reviewers', 'reviewers': [{'reviewer': {'id': 1}}]}],
                'deployment_branch_policy': {'custom_branch_policies': True}}
        policies = {'branch_policies': [{'name': 'main', 'type': 'branch'}]}
        protected_environment(info, policies)
        for invalid in ({}, {**info, 'protection_rules': []}, {**info, 'deployment_branch_policy': {}}):
            with self.assertRaises(ValueError): protected_environment(invalid, policies)
        for invalid in ({}, {'branch_policies': [{'name': '*'}]}, {'branch_policies': [{'name': 'main', 'type': 'tag'}]},
                        {'branch_policies': [{'name': 'main'}, {'name': 'feat/*'}]}):
            with self.assertRaises(ValueError): protected_environment(info, invalid)


if __name__ == '__main__': unittest.main()

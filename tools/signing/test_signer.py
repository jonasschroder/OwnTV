#!/usr/bin/env python3
"""Native signing integration with disposable fixtures, never owner keys or downloadable APKs."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile
from signing_policy import PACKAGES, badging, command
from update_metadata import verify as verify_metadata


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--qa', required=True, help='Actual unsigned ARM QA release')
    parser.add_argument('--stable', required=True, help='Actual unsigned ARM regular release')
    parser.add_argument('--tools', required=True)
    args = parser.parse_args()
    tools = Path(args.tools)
    inputs = {'qa': Path(args.qa).resolve(), 'stable': Path(args.stable).resolve()}
    metadata = {channel: badging(command([str(tools / 'aapt2'), 'dump', 'badging', str(apk)])) for channel, apk in inputs.items()}
    os.umask(0o077)
    with tempfile.TemporaryDirectory(prefix='mintv-native-signing-fixtures-') as directory:
        private = Path(directory)
        environments, pins = {}, {}
        for channel in PACKAGES:
            key = private / (channel + '-TEST-ONLY.p12')
            password = secrets.token_urlsafe(32)
            env = {**os.environ, 'MINTV_KEYSTORE_PASSWORD': password, 'MINTV_KEY_PASSWORD': password,
                   'MINTV_KEY_ALIAS': 'fixture', 'RUNNER_TEMP': str(private)}
            command(['keytool', '-genkeypair', '-keystore', str(key), '-storetype', 'PKCS12', '-alias', 'fixture',
                     '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10', '-dname', 'CN=DISPOSABLE TEST ONLY',
                     '-storepass:env', 'MINTV_KEYSTORE_PASSWORD', '-keypass:env', 'MINTV_KEY_PASSWORD'], env=env)
            env['MINTV_KEYSTORE_BASE64'] = base64.b64encode(key.read_bytes()).decode()
            pem = command(['keytool', '-exportcert', '-rfc', '-keystore', str(key), '-alias', 'fixture',
                           '-storepass:env', 'MINTV_KEYSTORE_PASSWORD'], env=env)
            der = base64.b64decode(''.join(line for line in pem.splitlines() if not line.startswith('---')))
            pins[channel] = hashlib.sha256(der).hexdigest()
            environments[channel] = env
        assert pins['qa'] != pins['stable']
        config = {'schema': 1,
                  'qa': {'application_id': PACKAGES['qa'], 'certificate_sha256': pins['qa']},
                  'stable': {'application_id': PACKAGES['stable'], 'certificate_sha256': pins['stable'],
                             'installed_v01_certificate_sha256': pins['stable'], 'installed_v01_version_code': 1}}
        config_file = private / 'SYNTHETIC-NOT-INSTALLED-IDENTITY.json'
        config_file.write_text(json.dumps(config))
        notes = private / 'notes.md'; notes.write_text('Disposable native test only; never distribute.\n')
        count = 0

        def run(channel, label, *, env=None, apk=None, good=False, pin_config=None):
            nonlocal count
            out = private / label
            info = metadata[channel]
            result = subprocess.run([sys.executable, 'tools/signing/sign_apk.py', '--channel', channel,
                                     '--config', str(pin_config or config_file), '--unsigned', str(apk or inputs[channel]),
                                     '--tools', str(tools), '--code', str(info['version_code']), '--name', info['version_name'],
                                     '--source', 'a' * 40, '--notes', str(notes), '--output', str(out)],
                                    env=env or environments[channel], capture_output=True, text=True)
            if good:
                if result.returncode:
                    raise ValueError('Native positive signing case failed: ' + result.stdout + result.stderr)
                evidence = json.loads((out / 'release-candidate.json').read_text())
                assert evidence['certificate_sha256'] == pins[channel]
                assert evidence['application_id'] == PACKAGES[channel]
                verified = verify_metadata(out / ('MinTV-' + channel + '-update.json'), config, channel, out / evidence['apk'])
                assert verified['version_code'] == info['version_code']
                with (out / evidence['apk']).open('rb') as file:
                    assert hashlib.file_digest(file, 'sha256').hexdigest() == evidence['apk_sha256']
                assert set(path.name for path in out.iterdir()) == {evidence['apk'], 'SHA256SUMS',
                        'SIGNING-CERTIFICATE.txt', 'RELEASE-NOTES.md', 'release-candidate.json',
                        'MinTV-' + channel + '-update.json'}
            else:
                assert result.returncode != 0, label
                assert not out.exists(), label
                for secret in ('MINTV_KEYSTORE_BASE64', 'MINTV_KEYSTORE_PASSWORD', 'MINTV_KEY_PASSWORD'):
                    value = (env or environments[channel]).get(secret, '')
                    assert not value or value not in result.stdout + result.stderr
            count += 1
            print('PASS native case: ' + label, flush=True)
            return out

        for channel in PACKAGES:
            first = run(channel, channel + '-first', good=True)
            second = run(channel, channel + '-second', good=True)
            # Independent signing invocations retain exactly the same channel-specific cert.
            assert json.loads((first / 'release-candidate.json').read_text())['certificate_sha256'] == json.loads((second / 'release-candidate.json').read_text())['certificate_sha256']
            signed = next(first.glob('*.apk'))
            run(channel, channel + '-already-signed', apk=signed)
            run(channel, channel + '-other-package', apk=inputs['stable' if channel == 'qa' else 'qa'])
            run(channel, channel + '-other-key', env=environments['stable' if channel == 'qa' else 'qa'])
            run(channel, channel + '-wrong-password', env={**environments[channel], 'MINTV_KEYSTORE_PASSWORD': secrets.token_urlsafe(32)})
            run(channel, channel + '-wrong-key-password', env={**environments[channel], 'MINTV_KEY_PASSWORD': secrets.token_urlsafe(32)})
            run(channel, channel + '-wrong-alias', env={**environments[channel], 'MINTV_KEY_ALIAS': 'missing-fixture-alias'})
            for missing in ('MINTV_KEYSTORE_BASE64', 'MINTV_KEYSTORE_PASSWORD', 'MINTV_KEY_ALIAS', 'MINTV_KEY_PASSWORD'):
                env = dict(environments[channel]); del env[missing]
                run(channel, channel + '-missing-' + missing, env=env)
        broken = private / 'broken.apk'; broken.write_bytes(b'NOT AN APK')
        run('qa', 'corrupt-archive', apk=broken)
        missing = private / 'missing-pin.json'
        config['qa']['certificate_sha256'] = None; missing.write_text(json.dumps(config))
        run('qa', 'missing-public-pin', pin_config=missing)
        print(f'PASS: {count} actual native signing/verification cases; stable separate fixture certs, unsigned inputs, checksum/public outputs; invalid credentials, missing secrets, wrong package/key, signed and damaged input rejected. No permanent key or fixture artifact retained.')


if __name__ == '__main__': main()

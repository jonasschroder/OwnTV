#!/usr/bin/env python3
"""Protected-job signer. Keystore stays in RUNNER_TEMP; only verified public outputs escape."""
import argparse
import base64
import json
import os
from pathlib import Path
import shutil
import tempfile
from signing_policy import (PACKAGES, anchor, badging, check_certificates, check_identity,
                            command, no_key_material, read_config, sha256, version_name)

REQUIRED = ('MINTV_KEYSTORE_BASE64', 'MINTV_KEYSTORE_PASSWORD', 'MINTV_KEY_ALIAS', 'MINTV_KEY_PASSWORD')


def required_secrets(env):
    missing = [name for name in REQUIRED if not env.get(name)]
    if missing:
        raise ValueError('Configure the protected environment secrets: ' + ', '.join(missing))
    encoded = env['MINTV_KEYSTORE_BASE64']
    if len(encoded) > 120_000:
        raise ValueError('Keystore exceeds the supported secret size')
    try:
        raw = base64.b64decode(encoded, validate=True)
    except ValueError:
        raise ValueError('Invalid keystore secret encoding') from None
    if not 512 <= len(raw) <= 80_000:
        raise ValueError('Invalid keystore secret size')
    return raw


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--channel', choices=PACKAGES, required=True)
    parser.add_argument('--config', required=True)
    parser.add_argument('--unsigned', required=True)
    parser.add_argument('--tools', required=True)
    parser.add_argument('--code', type=int, required=True)
    parser.add_argument('--name', required=True)
    parser.add_argument('--source', required=True)
    parser.add_argument('--notes', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    config = read_config(args.config)
    expected = anchor(config, args.channel)
    version_name(args.channel, args.name)
    if args.channel == 'stable' and args.code <= config['stable']['installed_v01_version_code']:
        raise ValueError('Candidate must be newer than the verified installed production build')
    import re
    if not re.fullmatch('[0-9a-f]{40}', args.source) or not 1_000_000 < args.code <= 2_100_000_000:
        raise ValueError('Invalid approved source/versionCode')
    notes = Path(args.notes).read_text()
    if not notes.strip() or len(notes.encode('utf-8')) > 16_000:
        raise ValueError('Release notes must be nonempty and bounded')
    tools = Path(args.tools)
    apk = Path(args.unsigned)
    check_identity(badging(command([str(tools / 'aapt2'), 'dump', 'badging', str(apk)])), args.channel, args.code, args.name)
    no_key_material(apk)
    # Fail if the purportedly unsigned input is already signed. This job signs no development APK.
    import subprocess
    if subprocess.run([str(tools / 'apksigner'), 'verify', str(apk)], capture_output=True).returncode == 0:
        raise ValueError('Input must be an unsigned release APK')
    key = required_secrets(os.environ)
    os.umask(0o077)
    out = Path(args.output)
    if out.exists():
        raise ValueError('Output directory must be new')
    out.mkdir(parents=True)
    prefix = 'MinTV-Test' if args.channel == 'qa' else 'MinTV'
    name = f'{prefix}-v{args.name}-{args.code}-arm.apk'
    signed = out / name
    try:
        with tempfile.TemporaryDirectory(prefix='mintv-key-', dir=os.environ['RUNNER_TEMP']) as private:
            store = Path(private) / 'key.p12'
            store.write_bytes(key)
            store.chmod(0o600)
            # Inspect the actual public cert before any signing. Passwords remain in env, not argv.
            pem = command(['keytool', '-exportcert', '-rfc', '-keystore', str(store),
                           '-storepass:env', 'MINTV_KEYSTORE_PASSWORD', '-alias', os.environ['MINTV_KEY_ALIAS']])
            import hashlib
            der = base64.b64decode(''.join(line for line in pem.splitlines() if not line.startswith('---')))
            if hashlib.sha256(der).hexdigest() != expected:
                raise ValueError('Keystore does not match the reviewed public certificate pin')
            command([str(tools / 'apksigner'), 'sign', '--ks', str(store),
                     '--ks-pass', 'env:MINTV_KEYSTORE_PASSWORD', '--key-pass', 'env:MINTV_KEY_PASSWORD',
                     '--ks-key-alias', os.environ['MINTV_KEY_ALIAS'], '--v1-signing-enabled', 'true',
                     '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true',
                     '--v4-signing-enabled', 'false', '--out', str(signed), str(apk)])
        certs = command([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', '--Werr', str(signed)])
        check_certificates(certs, expected)
        check_identity(badging(command([str(tools / 'aapt2'), 'dump', 'badging', str(signed)])), args.channel, args.code, args.name)
        checksum = sha256(signed)
        (out / 'SHA256SUMS').write_text(f'{checksum}  {name}\n')
        (out / 'SIGNING-CERTIFICATE.txt').write_text(certs)
        (out / 'RELEASE-NOTES.md').write_text(notes)
        (out / 'release-candidate.json').write_text(json.dumps({
            'schema': 1, 'channel': args.channel, 'application_id': PACKAGES[args.channel],
            'source_commit': args.source, 'version_code': args.code, 'version_name': args.name,
            'certificate_sha256': expected, 'apk': name, 'apk_sha256': checksum,
            'apk_bytes': signed.stat().st_size, 'distribution': 'not-published',
            'notice': 'Build evidence only; NOT an authenticated in-app update manifest.'
        }, indent=2) + '\n')
        print(f'Verified {PACKAGES[args.channel]} code {args.code}; signed release candidate only. Nothing published.')
    except BaseException:
        shutil.rmtree(out, ignore_errors=True)
        raise


if __name__ == '__main__':
    try:
        main()
    except ValueError as error:
        # Policy/native-tool wrappers emit only fixed public reasons; native stderr stays private.
        import sys
        sys.exit('BLOCKED: ' + str(error) + '. No candidate produced.')
    except (OSError, KeyError):
        # File/env exceptions can include private filenames. Keep those details out of build logs.
        import sys
        sys.exit('BLOCKED: signing preflight or APK verification failed. Check the guide and protected configuration; no candidate produced.')

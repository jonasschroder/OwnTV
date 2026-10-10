#!/usr/bin/env python3
"""Actual Android update/data/isolation test. Disposable CI emulator ONLY, disposable fixture keys.

No fixture APK, keystore, password, or Gradle cache is uploaded. This is not permanent-key acceptance.
"""
import argparse
import base64
import hashlib
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
from signing_policy import PACKAGES, badging, check_certificates, check_identity, command

TEST_CLASS = 'tv.own.owntv.update.SameSignerUpgradeTest'


def run_instrumentation(adb, package, phase, code):
    result = subprocess.run([adb, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', TEST_CLASS,
                             '-e', 'phase', phase, '-e', 'expectedCode', str(code), '-e', 'disposableEmulator', 'true',
                             package + '.test/androidx.test.runner.AndroidJUnitRunner'], capture_output=True, text=True)
    if result.returncode or 'OK (1 test)' not in result.stdout or 'FAILURES' in result.stdout:
        print(result.stdout[-12000:])
        raise ValueError('Required on-device persistent-data test failed or did not execute')
    print(f'{package}: {phase}, code {code}, 1 actual Android test PASS')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--tools', required=True)
    args = parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        raise ValueError('This installer test runs only in disposable CI, never on a local device')
    adb = str(Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb')
    serial = command([adb, 'get-serialno']).strip()
    if not serial.startswith('emulator-') or command([adb, 'shell', 'getprop', 'ro.kernel.qemu']).strip() != '1':
        raise ValueError('Refusing to install on a physical device')
    for package in PACKAGES.values():
        found = subprocess.run([adb, 'shell', 'pm', 'path', package], capture_output=True, text=True)
        if found.returncode not in (0, 1) or found.stdout.strip() or found.stderr.strip():
            raise ValueError('Refusing to alter a pre-existing installation; use a NEW empty emulator')
    tools = Path(args.tools)
    os.umask(0o077)
    with tempfile.TemporaryDirectory(prefix='mintv-disposable-upgrade-', dir=os.environ['RUNNER_TEMP']) as directory:
        private = Path(directory)
        stores, fingerprints = {}, {}
        for channel in PACKAGES:
            env = {**os.environ, 'MINTV_KEYSTORE_PASSWORD': secrets.token_urlsafe(32)}
            env['MINTV_KEY_PASSWORD'] = env['MINTV_KEYSTORE_PASSWORD']
            key = private / (channel + '-FIXTURE-ONLY.p12')
            command(['keytool', '-genkeypair', '-keystore', str(key), '-storetype', 'PKCS12', '-alias', 'fixture',
                     '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10', '-dname', 'CN=DISPOSABLE TEST ONLY',
                     '-storepass:env', 'MINTV_KEYSTORE_PASSWORD', '-keypass:env', 'MINTV_KEY_PASSWORD'], env=env)
            pem = command(['keytool', '-exportcert', '-rfc', '-keystore', str(key), '-alias', 'fixture',
                           '-storepass:env', 'MINTV_KEYSTORE_PASSWORD'], env=env)
            der = base64.b64decode(''.join(line for line in pem.splitlines() if not line.startswith('---')))
            fingerprints[channel] = hashlib.sha256(der).hexdigest()
            stores[channel] = (key, env)
        assert fingerprints['qa'] != fingerprints['stable']

        def sign(input_apk, channel, output):
            key, env = stores[channel]
            command([str(tools / 'apksigner'), 'sign', '--ks', str(key), '--ks-pass', 'env:MINTV_KEYSTORE_PASSWORD',
                     '--key-pass', 'env:MINTV_KEY_PASSWORD', '--ks-key-alias', 'fixture', '--v1-signing-enabled', 'true',
                     '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false',
                     '--out', str(output), str(input_apk)], env=env)
            checked = command([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', '--Werr', str(output)])
            check_certificates(checked, fingerprints[channel])

        def install(apk, expected_error=None):
            result = subprocess.run([adb, 'install', '-r', str(apk)], capture_output=True, text=True)
            if expected_error:
                if result.returncode == 0 or expected_error not in result.stdout + result.stderr:
                    raise ValueError('Android did not reject the incompatible candidate as expected')
            elif result.returncode or 'Success' not in result.stdout:
                raise ValueError('Actual Android update failed')

        apks, test_apks = {}, {}
        for code in (1000101, 1000102):
            for channel, flavor in [('qa', 'qa'), ('stable', 'x86_64')]:
                title = 'Qa' if channel == 'qa' else 'X86_64'
                name = '0.2.0-beta.1' if channel == 'qa' else '0.2.0'
                build_env = {**os.environ, 'VERSION_CODE': str(code), 'MINTV_VERSION_NAME': name}
                tasks = [':app:assemble' + title + 'Debug']
                if code == 1000101:
                    tasks += [':app:assemble' + title + 'DebugAndroidTest']
                print(f'Build {channel} fixture APK {code} (no permanent signing credentials)', flush=True)
                subprocess.run(['./gradlew', *tasks, '-Pmintv.upgradeTestAbi=x86_64', '--max-workers=4', '--console=plain'],
                               check=True, env=build_env)
                input_apk = Path(f'app/build/outputs/apk/{flavor}/debug/app-{flavor}-debug.apk')
                info = badging(command([str(tools / 'aapt2'), 'dump', 'badging', str(input_apk)]))
                check_identity(info, channel, code, name, emulator_test=True)
                output = private / f'{channel}-{code}-FIXTURE-ONLY.apk'
                sign(input_apk, channel, output)
                apks[channel, code] = output
                if code == 1000101:
                    tests = list(Path(f'app/build/outputs/apk/androidTest/{flavor}/debug').glob('*.apk'))
                    if len(tests) != 1:
                        raise ValueError('Exactly one instrumentation APK required')
                    test_apks[channel] = private / (channel + '-tests-FIXTURE-ONLY.apk')
                    sign(tests[0], channel, test_apks[channel])

        for channel, package in PACKAGES.items():
            install(apks[channel, 1000101])
            install(test_apks[channel])
            run_instrumentation(adb, package, 'seed', 1000101)
        wrong_key = private / 'wrong-qa-signer-FIXTURE-ONLY.apk'
        sign(apks['qa', 1000102], 'stable', wrong_key)
        install(wrong_key, 'INSTALL_FAILED_UPDATE_INCOMPATIBLE')
        run_instrumentation(adb, PACKAGES['qa'], 'verify', 1000101)
        # Use the existing read-only checker on REAL APKs before actual updates, for each package.
        import sys
        for channel, package in PACKAGES.items():
            subprocess.run([sys.executable, 'tools/check-apk-update.py', '--application-id', package,
                            '--previous', str(apks[channel, 1000101]), '--candidate', str(apks[channel, 1000102]),
                            '--aapt2', str(tools / 'aapt2'), '--apksigner', str(tools / 'apksigner')], check=True)
            install(apks[channel, 1000102])
            run_instrumentation(adb, package, 'verify', 1000102)
            if channel == 'qa':
                run_instrumentation(adb, PACKAGES['stable'], 'verify', 1000101)
            install(apks[channel, 1000101], 'INSTALL_FAILED_VERSION_DOWNGRADE')
            run_instrumentation(adb, package, 'verify', 1000102)
        print('PASS: two separate fixture certificates, two builds each, same-signer upgrades accepted; data preserved; wrong signer/downgrades rejected; QA leaves regular data/version untouched.')
        print('8 actual persistent-data instrumentation executions passed. NOT acceptance of owner permanent keys, PackageInstaller UI or physical Chromecast.')


if __name__ == '__main__':
    main()

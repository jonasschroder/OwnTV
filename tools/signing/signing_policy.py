"""Shared fail-closed release candidate checks. No installer or GitHub publication code."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

PACKAGES = {'qa': 'se.jonasschroder.mintv.qa', 'stable': 'se.jonasschroder.mintv'}
CODE_BASE = 1_000_000
MAX_CODE = 2_100_000_000


def version_code(run_number, attempt):
    # One workflow owns both counters. Reject all reruns, including previously failed runs:
    # rerunning after signing could otherwise issue different bytes with the same versionCode.
    if attempt != 1 or not isinstance(run_number, int) or run_number < 1:
        raise ValueError('Start a NEW manual workflow run; signing reruns are forbidden')
    code = CODE_BASE + run_number
    if code > MAX_CODE:
        raise ValueError('Android versionCode range exhausted')
    return code


def version_name(channel, name):
    pattern = r'0|[1-9][0-9]{0,3}'
    base = rf'(?:{pattern})\.(?:{pattern})\.(?:{pattern})'
    suffix = r'-beta\.[1-9][0-9]{0,5}' if channel == 'qa' else ''
    if channel not in PACKAGES or re.fullmatch(base + suffix, name) is None:
        raise ValueError('Use X.Y.Z for Stable, X.Y.Z-beta.N for Test')
    return name


def fingerprint(value):
    if not isinstance(value, str) or re.fullmatch('[0-9a-f]{64}', value) is None:
        raise ValueError('Public certificate SHA-256 pin is missing or invalid; configure it via a reviewed PR')
    return value


def anchor(config, channel):
    if config.get('schema') != 1 or channel not in PACKAGES:
        raise ValueError('Unknown signing configuration/channel')
    item = config[channel]
    if item.get('application_id') != PACKAGES[channel]:
        raise ValueError('Wrong channel/application ID')
    expected = fingerprint(item.get('certificate_sha256'))
    other = config['stable' if channel == 'qa' else 'qa'].get('certificate_sha256')
    if other == expected:
        raise ValueError('QA and production MUST use different keys')
    if channel == 'stable':
        if fingerprint(item.get('installed_v01_certificate_sha256')) != expected:
            raise ValueError('Production key does not match installed v0.1; keep that installation untouched')
        if type(item.get('installed_v01_version_code')) is not int or not 0 < item['installed_v01_version_code'] < MAX_CODE:
            raise ValueError('Installed production versionCode must be verified first')
    return expected


def command(args, env=None):
    # Never echo tool stderr: malformed signing credentials can reveal aliases/paths/password inputs.
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, env=env)
    if result.returncode:
        raise ValueError(f'{Path(args[0]).name} failed; check credentials/tools privately (no secret diagnostics logged)')
    return result.stdout


def badging(text):
    identity = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", text, re.M)
    sdk = re.search(r"^sdkVersion:'(\d+)'", text, re.M)
    abi = re.search(r'^native-code:(.*)$', text, re.M)
    if not identity or not sdk or not abi:
        raise ValueError('APK identity/Android/ABI metadata missing')
    return {'package': identity[1], 'version_code': int(identity[2]), 'version_name': identity[3],
            'min_sdk': int(sdk[1]), 'abis': re.findall(r"'([^']+)'", abi[1]),
            'debuggable': 'application-debuggable' in text}


def check_identity(info, channel, code, name, *, emulator_test=False):
    if info['package'] != PACKAGES[channel] or info['version_code'] != code or info['version_name'] != name:
        raise ValueError('APK package/version does not match the approved candidate')
    expected_abis = {'x86_64'} if emulator_test else {'arm64-v8a', 'armeabi-v7a'}
    if set(info['abis']) != expected_abis or info['min_sdk'] > 34:
        raise ValueError('APK does not support the required Chromecast ABI/Android version')
    if info['debuggable'] and not emulator_test:
        raise ValueError('Development/debug APKs are never permanently signed by this workflow')


def check_certificates(text, expected):
    digests = re.findall(r'^(?:Signer #\d+:?|V\d+(?:\.\d+)? Signer:) certificate SHA-256 digest: ([0-9a-f]{64})$', text, re.M)
    numbered = set(re.findall(r'^Signer #(\d+)', text, re.M))
    count = re.search(r'^Number of signers: (\d+)$', text, re.M)
    if set(digests) != {fingerprint(expected)} or len(numbered) > 1 or (count and count[1] != '1'):
        raise ValueError('Unexpected or missing APK signing certificate')
    for scheme in ('v2', 'v3'):
        if not re.search(rf'^Verified using {scheme} scheme .*: true$', text, re.M):
            raise ValueError('Verified APK v2 AND v3 signatures are required')


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as source:
        for block in iter(lambda: source.read(64 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def no_key_material(path):
    with zipfile.ZipFile(path) as archive:
        for item in archive.namelist():
            lower = item.lower()
            if lower.endswith(('.p12', '.pfx', '.keystore', '.jks', 'signing.properties', 'keystore.properties')):
                raise ValueError('APK contains forbidden signing material')


def read_config(path):
    return json.loads(Path(path).read_text())

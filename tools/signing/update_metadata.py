"""Authenticated exact-byte manifest. No third-party crypto dependency or private-key export."""
import base64
import hashlib
import json
from pathlib import Path
import re
import tempfile
from signing_policy import PACKAGES, anchor, command, sha256, version_name

MAX_APK = 200_000_000


def payload(candidate, info, notes):
    channel = candidate['channel']
    return {'schema': 1, 'channel': channel, 'application_id': PACKAGES[channel],
            'version_code': candidate['version_code'], 'version_name': candidate['version_name'],
            'source_commit': candidate['source_commit'], 'apk': candidate['apk'],
            'apk_sha256': candidate['apk_sha256'], 'apk_bytes': candidate['apk_bytes'],
            'min_sdk': info['min_sdk'], 'abis': info['abis'], 'notes': notes,
            'tag': f"{channel}-{candidate['version_code']}"}


def validate(data, channel):
    code = data['version_code']
    if (data['schema'] != 1 or data['channel'] != channel or data['application_id'] != PACKAGES[channel]
            or type(code) is not int or not 1_000_000 < code <= 2_100_000_000
            or data['tag'] != f'{channel}-{code}'
            or not re.fullmatch('[0-9a-f]{40}', data['source_commit'])
            or not re.fullmatch('[0-9a-f]{64}', data['apk_sha256'])
            or type(data['apk_bytes']) is not int or not 1 <= data['apk_bytes'] <= MAX_APK
            or data['abis'] != ['arm64-v8a', 'armeabi-v7a']
            or type(data['min_sdk']) is not int or not 26 <= data['min_sdk'] <= 34
            or len(data['notes'].encode('utf-8')) > 16_000):
        raise ValueError('Invalid authenticated release metadata')
    version_name(channel, data['version_name'])
    prefix = 'MinTV-Test' if channel == 'qa' else 'MinTV'
    if data['apk'] != f"{prefix}-v{data['version_name']}-{code}-arm.apk":
        raise ValueError('Unexpected APK asset name')


def sign(store, cert, data, destination, private):
    validate(data, data['channel'])
    raw = json.dumps(data, separators=(',', ':'), ensure_ascii=False).encode('utf-8')
    body = Path(private) / 'metadata-payload.json'; body.write_bytes(raw)
    signature = Path(private) / 'metadata-signature.bin'
    command(['java', str(Path(__file__).with_name('ManifestSignature.java')), 'sign', str(store), str(body), str(signature)])
    Path(destination).write_text(json.dumps({'algorithm': 'SHA256withRSA',
            'certificate': base64.b64encode(cert).decode(), 'payload': base64.b64encode(raw).decode(),
            'signature': base64.b64encode(signature.read_bytes()).decode()}, separators=(',', ':')) + '\n')


def verify(path, config, channel, apk=None):
    raw = Path(path).read_bytes()
    if len(raw) > 40000: raise ValueError('Update manifest too large')
    envelope = json.loads(raw)
    if envelope['algorithm'] != 'SHA256withRSA': raise ValueError('Invalid metadata algorithm')
    cert = base64.b64decode(envelope['certificate'], validate=True)
    body = base64.b64decode(envelope['payload'], validate=True)
    signature = base64.b64decode(envelope['signature'], validate=True)
    if not 256 <= len(cert) <= 8192 or len(body) > 24000 or not 256 <= len(signature) <= 1024:
        raise ValueError('Invalid metadata bounds')
    if hashlib.sha256(cert).hexdigest() != anchor(config, channel):
        raise ValueError('Metadata signer differs from reviewed channel pin')
    with tempfile.TemporaryDirectory(prefix='mintv-public-metadata-') as temp:
        files = [Path(temp) / name for name in ('cert.der', 'payload.json', 'signature.bin')]
        for file, value in zip(files, (cert, body, signature)): file.write_bytes(value)
        command(['java', str(Path(__file__).with_name('ManifestSignature.java')), 'verify', *map(str, files)])
    data = json.loads(body); validate(data, channel)
    if apk is not None and (Path(apk).name != data['apk'] or Path(apk).stat().st_size != data['apk_bytes']
                            or sha256(apk) != data['apk_sha256']):
        raise ValueError('APK differs from authenticated metadata')
    return data

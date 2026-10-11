#!/usr/bin/env python3
"""Read-only approval/source guard and public-artifact verifier; never has a signing key."""
import argparse
import os
from pathlib import Path
import re
import shutil
import sys
import urllib.error
from release_guard import api, protected_environment, required_jobs
from signing_policy import anchor, badging, check_certificates, check_identity, command, no_key_material, read_config, production_floor
from update_metadata import verify


class DistributionError(ValueError):
    """Only controlled, public diagnostics may reach workflow logs."""


def checked(message, operation):
    try:
        return operation()
    except urllib.error.HTTPError as error:
        raise DistributionError(f'{message} (GitHub HTTP {error.code})') from None
    except Exception:
        # Do not echo native stderr, HTTP URLs, exception values or environment contents.
        raise DistributionError(message) from None


def signed_file(directory, name):
    # upload-artifact uses the repo root as LCA because signed/ AND evidence/ are uploaded.
    # download-artifact therefore preserves signed/. Never search recursively or use a decoy.
    signed = directory / 'signed'
    if signed.is_symlink() or not signed.is_dir():
        raise DistributionError('Missing or invalid candidate/signed directory; retain the downloaded artifact layout')
    if Path(name).name != name:
        raise DistributionError('Invalid signed asset filename')
    path = signed / name
    if path.is_symlink() or not path.is_file():
        raise DistributionError(f'Missing or invalid candidate/signed/{name}; download the complete approved signing artifact')
    return path


def verify_candidate(directory, tools, config, channel, code, public, output):
    expected = checked('Signing configuration or channel certificate pin is invalid', lambda: anchor(config, channel))
    manifest = signed_file(directory, f'MinTV-{channel}-update.json')
    data = checked('Authenticated update manifest verification failed: check certificate pin, signature and metadata',
                   lambda: verify(manifest, config, channel))
    if data['version_code'] != code:
        raise DistributionError('Candidate metadata versionCode differs from the approved signing run')
    apk = signed_file(directory, data['apk'])
    checked('APK filename, size or SHA256 differs from authenticated update metadata',
            lambda: verify(manifest, config, channel, apk))
    checked('APK package, version, ARM ABI, Android SDK or release/debug identity verification failed',
            lambda: check_identity(badging(command([str(tools / 'aapt2'), 'dump', 'badging', str(apk)])),
                                   channel, data['version_code'], data['version_name']))
    checked('APK certificate or v2/v3 signature verification failed',
            lambda: check_certificates(command([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', '--Werr', str(apk)]), expected))
    checked('APK archive inspection failed or forbidden signing material was found', lambda: no_key_material(apk))
    if channel == 'stable' and data['version_code'] <= production_floor(config):
        raise DistributionError('Production candidate is not newer than installed v0.1')
    # The signed payload binds source/notes/hash as well as package and code.
    checked('Authenticated source commit is missing or is not an ancestor of reviewed origin/main',
            lambda: command(['git', 'merge-base', '--is-ancestor', data['source_commit'], 'origin/main']))
    checked('Publication history verification failed: a release exists or channel order cannot be proven',
            lambda: published_history(channel, data['version_code'], lambda page: api(f'releases?per_page=100&page={page}')))
    if checked('Existing release tag lookup failed', lambda: api(f"git/matching-refs/tags/{data['tag']}")):
        raise DistributionError('Release tag already exists; do not change or reuse it')
    def stage():
        public.mkdir()  # Refuse stale/previously staged output rather than overwriting it.
        for source in (apk, manifest):
            shutil.copyfile(source, public / source.name)
        (public / 'SHA256SUMS').write_text(f"{data['apk_sha256']}  {data['apk']}\n")
        (public / 'RELEASE-NOTES.md').write_text(data['notes'])
        with output.open('a') as file:
            file.write(f"tag={data['tag']}\nsource={data['source_commit']}\nname={data['version_name']}\n")
    checked('Cannot stage verified public assets or workflow outputs; use a clean writable workspace', stage)
    print(f"Verified candidate/signed: {channel} build {data['version_code']}; public assets staged. Nothing published.")


def signing_run(run, jobs, channel):
    if (run.get('event') != 'workflow_dispatch' or run.get('head_branch') != 'main'
            or run.get('path') != '.github/workflows/mintv-sign.yml'
            or run.get('status') != 'completed' or run.get('conclusion') != 'success'):
        raise ValueError('Only a successful approved main signing run can be distributed')
    required_jobs(jobs, {'preflight', 'unsigned-build', 'sign-' + channel})


def published_history(channel, code, fetch_page):
    # Many QA releases can hide the newest Stable beyond the first page (or vice versa).
    # A bounded but unexhausted history cannot prove that publication is monotonic.
    for page in range(1, 4):
        items = fetch_page(page)
        if not isinstance(items, list) or len(items) > 100:
            raise ValueError('Invalid publication history')
        for item in items:
            tag = item.get('tag_name', '')
            if re.fullmatch(channel + '-[1-9][0-9]{6,9}', tag) and int(tag.split('-')[1]) >= code:
                raise ValueError('A same or newer channel release already exists; never overwrite/downgrade')
        if len(items) < 100:
            return
    raise ValueError('Publication history window exhausted; cannot prove monotonic channel order')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--verify', action='store_true')
    parser.add_argument('--tools')
    args = parser.parse_args()
    channel = os.environ.get('CHANNEL')
    if channel not in ('qa', 'stable'):
        raise DistributionError('CHANNEL must be qa or stable')
    config = checked('Cannot read reviewed signing configuration', lambda: read_config('config/mintv-signing.json'))
    checked('Signing configuration or channel certificate pin is invalid', lambda: anchor(config, channel))
    if args.verify:
        if not args.tools:
            raise DistributionError('APK verification requires --tools pointing to Android build-tools')
        code = checked('EXPECTED_CODE is missing or invalid', lambda: int(os.environ['EXPECTED_CODE']))
        output = checked('GITHUB_OUTPUT is missing', lambda: Path(os.environ['GITHUB_OUTPUT']))
        verify_candidate(Path('candidate'), Path(args.tools), config, channel, code, Path('public'), output)
    else:
        if (os.environ.get('GITHUB_REPOSITORY') != 'jonasschroder/OwnTV' or os.environ.get('GITHUB_REF') != 'refs/heads/main'
                or os.environ.get('GITHUB_EVENT_NAME') != 'workflow_dispatch'
                or os.environ.get('CONFIRM') != 'PUBLISH-' + channel.upper()):
            raise DistributionError('Explicit channel publication confirmation on reviewed main required')
        run_id = os.environ.get('CANDIDATE_RUN_ID', '')
        if not re.fullmatch('[1-9][0-9]{0,19}', run_id): raise DistributionError('Invalid signing run ID')
        run = checked('Cannot read approved signing run', lambda: api(f'actions/runs/{run_id}'))
        checked('Signing run or required channel jobs are not successful approved main signing jobs',
                lambda: signing_run(run, api(f'actions/runs/{run_id}/jobs?per_page=100')['jobs'], channel))
        environment = 'mintv-' + ('qa' if channel == 'qa' else 'production') + '-distribution'
        checked('Distribution environment approval/main-only/no-bypass protection verification failed',
                lambda: protected_environment(api('environments/' + environment), api('environments/' + environment + '/deployment-branch-policies')))
        code = checked('Approved signing run version counter is missing or invalid', lambda: 1_000_000 + run['run_number'])
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            output.write(f"artifact=MinTV-approved-{'QA' if channel == 'qa' else 'Stable'}-{code}\n")
            output.write(f'code={code}\n')


def cli():
    try:
        main()
    except DistributionError as error:
        print(f'BLOCKED: {error}. Nothing published.', file=sys.stderr)
        return 1
    except Exception:
        print('BLOCKED: unexpected distribution verification failure; check workflow inputs and workspace. Nothing published.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(cli())

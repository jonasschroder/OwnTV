#!/usr/bin/env python3
"""Read-only approval/source guard and public-artifact verifier; never has a signing key."""
import argparse
import os
from pathlib import Path
import re
from release_guard import api, protected_environment, required_jobs
from signing_policy import anchor, badging, check_certificates, check_identity, command, no_key_material, read_config
from update_metadata import verify


def signing_run(run, jobs, channel):
    if (run.get('event') != 'workflow_dispatch' or run.get('head_branch') != 'main'
            or run.get('path') != '.github/workflows/mintv-sign.yml'
            or run.get('status') != 'completed' or run.get('conclusion') != 'success'):
        raise ValueError('Only a successful approved main signing run can be distributed')
    required_jobs(jobs, {'preflight', 'unsigned-build', 'sign-' + channel})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--verify', action='store_true')
    parser.add_argument('--tools')
    args = parser.parse_args()
    channel = os.environ['CHANNEL']
    config = read_config('config/mintv-signing.json'); expected = anchor(config, channel)
    if args.verify:
        directory = Path('candidate')
        manifest = directory / f'MinTV-{channel}-update.json'
        data = verify(manifest, config, channel)
        if data['version_code'] != int(os.environ['EXPECTED_CODE']):
            raise ValueError('Candidate metadata differs from approved signing run')
        apk = directory / data['apk']; verify(manifest, config, channel, apk)
        check_identity(badging(command([str(Path(args.tools) / 'aapt2'), 'dump', 'badging', str(apk)])), channel, data['version_code'], data['version_name'])
        check_certificates(command([str(Path(args.tools) / 'apksigner'), 'verify', '--verbose', '--print-certs', '--Werr', str(apk)]), expected)
        no_key_material(apk)
        if channel == 'stable' and data['version_code'] <= config['stable']['installed_v01_version_code']:
            raise ValueError('Production candidate is not newer than installed v0.1')
        # The signed payload binds source/notes/hash as well as package and code.
        command(['git', 'merge-base', '--is-ancestor', data['source_commit'], 'origin/main'])
        published = api('releases?per_page=20')
        for item in published:
            tag = item.get('tag_name', '')
            if re.fullmatch(channel + '-[1-9][0-9]{6,9}', tag) and int(tag.split('-')[1]) >= data['version_code']:
                raise ValueError('A same or newer channel release already exists; never overwrite/downgrade')
        if api(f"git/matching-refs/tags/{data['tag']}"):
            raise ValueError('Release tag already exists; do not change or reuse it')
        public = Path('public'); public.mkdir()
        import shutil
        for source in (apk, manifest): shutil.copyfile(source, public / source.name)
        (public / 'SHA256SUMS').write_text(f"{data['apk_sha256']}  {data['apk']}\n")
        (public / 'RELEASE-NOTES.md').write_text(data['notes'])
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            output.write(f"tag={data['tag']}\nsource={data['source_commit']}\nname={data['version_name']}\n")
    else:
        if (os.environ.get('GITHUB_REPOSITORY') != 'jonasschroder/OwnTV' or os.environ.get('GITHUB_REF') != 'refs/heads/main'
                or os.environ.get('GITHUB_EVENT_NAME') != 'workflow_dispatch'
                or os.environ.get('CONFIRM') != 'PUBLISH-' + channel.upper()):
            raise ValueError('Explicit channel publication confirmation on reviewed main required')
        run_id = os.environ['CANDIDATE_RUN_ID']
        if not re.fullmatch('[1-9][0-9]{0,19}', run_id): raise ValueError('Invalid signing run ID')
        run = api(f'actions/runs/{run_id}')
        signing_run(run, api(f'actions/runs/{run_id}/jobs?per_page=100')['jobs'], channel)
        environment = 'mintv-' + ('qa' if channel == 'qa' else 'production') + '-distribution'
        protected_environment(api('environments/' + environment), api('environments/' + environment + '/deployment-branch-policies'))
        code = 1_000_000 + run['run_number']
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            output.write(f"artifact=MinTV-approved-{'QA' if channel == 'qa' else 'Stable'}-{code}\n")
            output.write(f'code={code}\n')


if __name__ == '__main__':
    try: main()
    except Exception:
        import sys
        sys.exit('BLOCKED: publication approval, signing run or authenticated APK verification failed. Nothing published.')

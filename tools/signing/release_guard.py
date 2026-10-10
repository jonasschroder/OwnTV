#!/usr/bin/env python3
"""Read-only, exact-commit CI/protection gate for the manual signing workflow."""
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request
from signing_policy import anchor, read_config, version_code, version_name

REPO = 'jonasschroder/OwnTV'
REQUIRED = {
    'android.yml': {'check-and-build', 'home-device-regressions', 'signing-upgrade-regressions'},
    'i18n.yml': {'baseline', 'validate', 'pseudo'},
}


def api(path):
    request = urllib.request.Request(f'https://api.github.com/repos/{REPO}/{path}', headers={
        'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
        'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28',
        'User-Agent': 'MinTV-signing-preflight'})
    with urllib.request.urlopen(request, timeout=20) as response:
        raw = response.read(1_000_001)
        if len(raw) > 1_000_000:
            raise ValueError('GitHub metadata exceeds the supported bound')
        return json.loads(raw)


def required_jobs(jobs, expected):
    seen = {item.get('name'): item.get('conclusion') for item in jobs}
    if not expected <= seen.keys() or any(seen[name] != 'success' for name in expected):
        raise ValueError('Required exact-commit CI jobs did not all pass')
    if any(value not in ('success', 'skipped') for value in seen.values()):
        raise ValueError('A CI job failed, was cancelled or is unfinished')


def protected_environment(info, policies):
    if info.get('can_admins_bypass', True):
        raise ValueError('Disable administrator bypass of required environment approval')
    rules = info.get('protection_rules', [])
    if not any(rule.get('type') == 'required_reviewers' and rule.get('reviewers') for rule in rules):
        raise ValueError('Configure required human reviewers before signing')
    if not info.get('deployment_branch_policy', {}).get('custom_branch_policies'):
        raise ValueError('Signing environment must allow only the trusted main branch')
    entries = policies.get('branch_policies', [])
    if len(entries) != 1 or entries[0].get('name') != 'main' or entries[0].get('type', 'branch') != 'branch':
        raise ValueError('Signing environment must allow exactly the main branch, no tags or PR refs')


def monotonic_history(runs, number, channel, read_jobs):
    # GitHub concurrency is not FIFO. An older queued dispatch must not sign after a newer
    # successful candidate, even though run_number was allocated in increasing order.
    if not any(run.get('run_number') == number for run in runs):
        raise ValueError('Run is outside the bounded signing history; start a new dispatch')
    for run in runs:
        if run.get('run_number', 0) > number:
            jobs = read_jobs(run['id'])
            if any(job.get('name') == 'sign-' + channel and job.get('conclusion') == 'success' for job in jobs):
                raise ValueError('A newer candidate of this channel was already signed; start a new dispatch')


def main():
    channel = os.environ['CHANNEL']
    source = os.environ['SOURCE_SHA']
    if os.environ.get('GITHUB_REPOSITORY') != REPO or os.environ.get('GITHUB_REF') != 'refs/heads/main':
        raise ValueError('Only a manual run of the reviewed workflow on main can sign')
    if os.environ.get('GITHUB_EVENT_NAME') != 'workflow_dispatch' or not re.fullmatch('[0-9a-f]{40}', source):
        raise ValueError('Manual dispatch and an exact lowercase 40-character commit SHA required')
    config = read_config('config/mintv-signing.json')
    certificate = anchor(config, channel)
    code = version_code(int(os.environ['GITHUB_RUN_NUMBER']), int(os.environ['GITHUB_RUN_ATTEMPT']))
    name = version_name(channel, os.environ['VERSION_NAME'])
    if channel == 'stable' and code <= config['stable']['installed_v01_version_code']:
        raise ValueError('Generated versionCode is not newer than installed production v0.1')
    # Workflow/tooling checkout is github.sha on main; source must be a reviewed main ancestor.
    result = subprocess.run(['git', 'merge-base', '--is-ancestor', source, 'origin/main'], capture_output=True)
    if result.returncode:
        raise ValueError('Source is not on reviewed main; do not pass an unmerged PR/fork commit')
    environment = 'mintv-qa-signing' if channel == 'qa' else 'mintv-production-signing'
    protected_environment(api('environments/' + environment), api('environments/' + environment + '/deployment-branch-policies'))
    history = api('actions/workflows/mintv-sign.yml/runs?per_page=100')['workflow_runs']
    monotonic_history(history, int(os.environ['GITHUB_RUN_NUMBER']), channel,
                      lambda run_id: api(f'actions/runs/{run_id}/jobs?per_page=100')['jobs'])
    runs_evidence = {}
    for workflow, expected_jobs in REQUIRED.items():
        runs = api(f'actions/workflows/{workflow}/runs?head_sha={source}&event=push&per_page=20')['workflow_runs']
        # A newer failed run invalidates an older green run. Require trusted push CI, not fork checks.
        matching = [run for run in runs if run.get('head_sha') == source and run.get('head_branch') == 'main']
        if not matching:
            raise ValueError('No exact-commit main CI run; run required checks first')
        run = max(matching, key=lambda item: item['id'])
        if run.get('status') != 'completed' or run.get('conclusion') != 'success':
            raise ValueError('Latest exact-commit CI run is not successful')
        jobs = api(f'actions/runs/{run["id"]}/jobs?per_page=100')['jobs']
        required_jobs(jobs, expected_jobs)
        runs_evidence[workflow] = run['id']
    notes = os.environ['RELEASE_NOTES']
    if not notes.strip() or len(notes.encode()) > 16_000:
        raise ValueError('Nonempty release notes (at most 16 KB) required')
    Path('release-notes.md').write_text(notes)
    Path('ci-evidence.json').write_text(json.dumps({'source': source, 'checks': runs_evidence}, indent=2) + '\n')
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write(f'code={code}\nname={name}\n')
    print(f'Approved preflight: {source}, {channel}, code {code}; protected reviewer approval still required.')


if __name__ == '__main__':
    try:
        main()
    except ValueError as error:
        import sys
        sys.exit('BLOCKED: ' + str(error) + '. No signing attempted.')
    except urllib.error.HTTPError as error:
        import sys
        sys.exit(f'BLOCKED: GitHub preflight HTTP {error.code}; check CI/environment read access. No signing attempted.')
    except (KeyError, OSError, urllib.error.URLError):
        import sys
        sys.exit('BLOCKED: exact-commit checks, certificate pins or protected environment are not ready. See the Mac setup guide. No signing attempted.')

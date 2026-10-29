#!/usr/bin/env python3
"""Bounded public-API research. No Android integration, scraping or secrets."""
from __future__ import annotations
import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
from pathlib import Path
import re
import time
import unicodedata
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler

ROOT = Path(__file__).resolve().parent
BASE = 'https://tvprogramidag.se/api/public/v1'
SPORT = BASE + '/sport?discipline=ishockey&limit=100'
TTL = timedelta(hours=1)  # Lab policy: conservative hourly cache, not a provider SLA.
MAX_BYTES = 2_000_000
MAX_RECORDS = 2000
UTC = timezone.utc


def stamp(value: datetime) -> str:
    return value.astimezone(UTC).isoformat().replace('+00:00', 'Z')


def instant(value: str) -> datetime:
    result = datetime.fromisoformat(value.replace('Z', '+00:00'))
    if result.tzinfo is None:
        raise ValueError('Timezone required')
    return result.astimezone(UTC)


def folded(value: str) -> str:
    text = ''.join(c for c in unicodedata.normalize('NFKD', value.casefold())
                   if not unicodedata.combining(c))
    return ' '.join(re.findall(r'[a-z0-9]+', text))


def contains(text: str, phrase: str) -> bool:
    phrase = folded(phrase)
    return bool(phrase) and (' ' + phrase + ' ') in (' ' + folded(text) + ' ')


def canonical_channel(name: str) -> str | None:
    """Identity normalization only. Legacy/OTT/unknown names never become TV4 linear."""
    s = folded(name).replace(' ', '')
    s = re.sub(r'(uhd|4k|fhd|hd|sd)$', '', s)
    m = re.fullmatch(r'tv4sportlive([1-9][0-9]*)', s)
    if m:
        return 'TV4 Sport Live ' + str(int(m[1]))
    m = re.fullmatch(r'tv4live([1-9][0-9]*)', s)
    if m:
        return 'TV4 Live ' + str(int(m[1]))  # Not equated with Sport Live.
    return {'tv4hockey': 'TV4 Hockey', 'tv4motor': 'TV4 Motor',
            'tv4': 'TV4', 'tv4sportkanalen': 'TV4 Sportkanalen'}.get(s)


def exact_fixture_match(program: dict, fixture: dict) -> tuple[bool, str]:
    text = ' '.join(str(program.get(k) or '') for k in ('title', 'description'))
    if not contains(text, fixture['league']):
        return False, 'missing_league'
    try:
        delta = (instant(program['start']) - instant(fixture['kickoff_local'])).total_seconds()
    except (KeyError, TypeError, ValueError, AttributeError):
        return False, 'invalid_start'
    if not -3600 <= delta <= 600:
        return False, 'wrong_time'
    if program.get('isLive') is not True:
        return False, 'not_explicitly_live'
    if not all(any(contains(text, alias) for alias in aliases) for aliases in fixture['team_tokens']):
        return False, 'missing_exact_teams'
    return True, 'teams_league_time'


def approved_url(url: str) -> bool:
    """No user-provided host, credentials, port or private/auth endpoint."""
    p = urlsplit(url)
    return (p.scheme == 'https' and p.netloc == 'tvprogramidag.se' and not p.fragment and
            (url in (BASE, BASE + '/channels', SPORT) or
             (not p.query and re.fullmatch(r'/api/public/v1/channel/[a-z0-9-]+', p.path) is not None)))


class NoRedirects(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError('Redirect refused; review documented endpoint before continuing')


def fetch_json(url: str, cache: Path, now: datetime, opener=None) -> dict:
    if not approved_url(url):
        raise ValueError('Unapproved public API URL')
    key = hashlib.sha256(url.encode()).hexdigest() + '.json'
    cache.mkdir(parents=True, exist_ok=True, mode=0o700)
    file = cache / key
    if file.exists():
        try:
            existing = json.loads(file.read_text())
            age = now - instant(existing['fetched_at'])
            if (existing['url'] == url and timedelta(0) <= age < TTL and
                    hashlib.sha256(json.dumps(existing['payload'], sort_keys=True).encode()).hexdigest()
                    == existing['payload_sha256']):
                return dict(existing, transport='cache')
        except (ValueError, KeyError, TypeError):
            pass
    req = Request(url, headers={'Accept': 'application/json',
                  'User-Agent': 'MinTV-Broadcast-Lab/0.2 (documented public API evaluation)'})
    with (opener or build_opener(NoRedirects())).open(req, timeout=15) as response:
        if 'json' not in response.headers.get('Content-Type', '').lower():
            raise ValueError('JSON content type required')
        data = response.read(MAX_BYTES + 1)
        if len(data) > MAX_BYTES:
            raise ValueError('Response size limit exceeded')
        payload = json.loads(data)
        if not isinstance(payload, dict):
            raise ValueError('JSON object required')
        result = {'url': url, 'fetched_at': stamp(now), 'status': 'reachable',
                  'http_status': response.status, 'body_sha256': hashlib.sha256(data).hexdigest(),
                  'payload_sha256': hashlib.sha256(json.dumps(payload, sort_keys=True).encode()).hexdigest(),
                  'server_date': response.headers.get('Date'),
                  'cache_control': response.headers.get('Cache-Control'), 'payload': payload}
    file.write_text(json.dumps(result, ensure_ascii=False) + '\n')
    file.chmod(0o600)
    return dict(result, transport='network')


def provider_programs(snapshot: dict) -> list[dict]:
    payload = snapshot['payload']
    rows = payload.get('programs')
    if not isinstance(rows, list) or len(rows) > MAX_RECORDS or not all(isinstance(p, dict) for p in rows):
        raise ValueError('Expected bounded programs[] objects')
    if any(not isinstance(p.get('title'), str) or not isinstance(p.get('start'), str) for p in rows):
        raise ValueError('Programme title/start required')
    channel = payload.get('channel')
    return [dict(p, channel=channel if isinstance(channel, dict) else p.get('channel')) for p in rows]


def fresh_until(snapshot: dict) -> datetime:
    fetched = instant(snapshot['fetched_at'])
    generated = instant(snapshot['payload']['generatedAt'])
    if generated > fetched + timedelta(minutes=5):
        raise ValueError('Future source timestamp')
    return min(fetched, generated) + TTL


def observations(snapshot: dict, fixture: dict) -> tuple[list[dict], list[dict]]:
    exact, candidates = [], []
    for p in provider_programs(snapshot):
        channel = p.get('channel')
        raw = channel.get('name') if isinstance(channel, dict) else None
        name = canonical_channel(raw or '')
        if name is None:
            continue
        matched, reason = exact_fixture_match(p, fixture)
        # Venue-only listings remain diagnostic candidates, NEVER verified assignments.
        text = ' '.join(str(p.get(k) or '') for k in ('title', 'description'))
        venue_only = (reason == 'missing_exact_teams' and contains(text, fixture.get('venue', '')))
        if not matched and not venue_only:
            continue
        item = {'provider': 'tvprogramidag', 'channel': name, 'raw_channel': raw,
                'programme_id': p.get('id'), 'programme_start': p['start'],
                'programme_title': p.get('title'), 'programme_description': p.get('description'),
                'source_url': snapshot['url'], 'programme_url': safe_evidence_url(p.get('url')),
                'fetched_at': snapshot['fetched_at'], 'generated_at': snapshot['payload'].get('generatedAt'),
                'expires_at': stamp(fresh_until(snapshot)),
                'match_basis': reason if matched else 'venue_league_time_only',
                'exact_teams_present': matched}
        (exact if matched else candidates).append(item)
    # Same broadcast returned by sport + channel endpoint is NOT independent corroboration.
    def unique(items):
        kept = {}
        for item in items:
            kept.setdefault((item['channel'], item['programme_start'], item['programme_id']), item)
        return list(kept.values())
    return unique(exact), unique(candidates)


def safe_evidence_url(url) -> str | None:
    if not isinstance(url, str):
        return None
    p = urlsplit(url)
    return url if (p.scheme == 'https' and p.netloc == 'tvprogramidag.se' and not p.query
                   and not p.fragment and p.path.startswith('/program/')) else None


def normalize(fixture: dict, evidence: list[dict], candidates: list[dict], now: datetime,
              expires: datetime, mode: str) -> dict:
    """Provider-independent contract: only explicit exact-fixture evidence can confirm."""
    valid = [e for e in evidence if e.get('exact_teams_present') is True and
             e.get('match_basis') == 'teams_league_time' and canonical_channel(e.get('channel', ''))
             and instant(e['fetched_at']) <= now < instant(e['expires_at'])]
    channels = sorted({canonical_channel(e['channel']) for e in valid})
    state = 'confirmed' if len(channels) == 1 else 'conflict' if channels else 'stale' if evidence else 'unverified'
    expiration = min((instant(e['expires_at']) for e in valid), default=expires)
    return {'fixture': {k: fixture.get(k) for k in ('id', 'league', 'home', 'away', 'display', 'kickoff_local', 'venue', 'reference_url')},
            'status': state, 'linear_channel': channels[0] if state == 'confirmed' else None,
            'checked_at': stamp(now), 'verified_at': stamp(max(instant(e['fetched_at']) for e in valid)) if valid else None,
            'expires_at': stamp(expiration), 'freshness': 'fresh' if now < expiration else 'expired',
            'source_mode': mode, 'app_ready': False,
            'evidence': evidence, 'unverified_candidates': candidates,
            'reason': 'Exact teams + league + live broadcast time required; venue alone is insufficient.'}


def endpoint_summary(snapshot: dict, now: datetime) -> dict:
    result = {k: snapshot.get(k) for k in ('url', 'status', 'http_status', 'fetched_at',
                                          'body_sha256', 'transport', 'server_date', 'cache_control', 'channel_count', 'selected_channels')}
    if snapshot['status'] != 'reachable':
        return result
    rows = provider_programs(snapshot)
    starts = [instant(p['start']) for p in rows if isinstance(p.get('start'), str)]
    shl = [p for p in rows if contains(str(p.get('title') or '') + ' ' + str(p.get('description') or ''), 'SHL')
           and p.get('isLive') is True and not contains(p.get('title') or '', 'studio')]
    result.update(status='reachable_validated', generated_at=snapshot['payload'].get('generatedAt'),
                  records=len(rows), first_start=stamp(min(starts)) if starts else None,
                  last_start=stamp(max(starts)) if starts else None,
                  latest_shl_start=stamp(max(instant(p['start']) for p in shl)) if shl else None,
                  live_shl_records=len(shl), expires_at=stamp(fresh_until(snapshot)))
    return result


def report(fixtures: list[dict], snapshots: list[dict], now: datetime, mode: str) -> dict:
    summaries, usable = [], []
    for snapshot in snapshots:
        try:
            summary = endpoint_summary(snapshot, now)
            if summary['status'] == 'reachable_validated':
                usable.append(snapshot)
        except (ValueError, KeyError, TypeError, AttributeError):
            summary = {'url': snapshot['url'], 'status': 'reachable_invalid_shape_or_time',
                       'fetched_at': snapshot.get('fetched_at')}
        summaries.append(summary)
    rows = []
    for fixture in fixtures:
        exact, candidates = [], []
        for snapshot in usable:
            a, b = observations(snapshot, fixture)
            exact.extend(a); candidates.extend(b)
        def unique(items):
            return list({(e['provider'], e['channel'], e['programme_start'], e['programme_id']): e for e in items}.values())
        expiry = min([fresh_until(s) for s in usable], default=now)
        row = normalize(fixture, unique(exact), unique(candidates), now, expiry, mode)
        if not usable:
            row['status'] = 'unavailable'
        rows.append(row)
    dates = sorted({f['kickoff_local'][:10] for f in fixtures})
    coverage = []
    for date in dates:
        group = [r for r in rows if r['fixture']['kickoff_local'].startswith(date)]
        coverage.append({'date': date, 'reference_fixtures': len(group),
                         'exact_confirmed': sum(r['status'] == 'confirmed' for r in group),
                         'venue_only_candidates': sum(bool(r['unverified_candidates']) for r in group)})
    return {'schema_version': 1, 'tool': 'MinTV Broadcast Lab 0.2', 'source_mode': mode,
            'checked_at': stamp(now), 'app_ready': False,
            'attribution': {'name': 'TV-Programidag', 'url': 'https://tvprogramidag.se'},
            'policy': {'max_requests_per_probe': 8, 'cache_seconds': 3600,
                       'venue_join_is_verified': False, 'no_android_integration': True},
            'endpoints': summaries, 'coverage': coverage, 'results': rows}


def live_probe(cache: Path) -> list[dict]:
    snapshots = []
    def get(url):
        now = datetime.now(UTC)
        try:
            value = fetch_json(url, cache, now)
        except HTTPError as error:
            value = {'url': url, 'fetched_at': stamp(now), 'status': 'access_denied' if error.code in (401, 403) else
                     'rate_limited' if error.code == 429 else 'http_error', 'http_status': error.code}
        except (URLError, OSError):
            value = {'url': url, 'fetched_at': stamp(now), 'status': 'network_unavailable'}
        except (ValueError, TypeError):
            value = {'url': url, 'fetched_at': stamp(now), 'status': 'reachable_invalid_json_or_redirect'}
        if value.get('transport') == 'network':
            time.sleep(1.1)  # Below 60/min even within this small probe; no automatic retry.
        return value
    sport = get(SPORT); snapshots.append(sport)
    if sport['status'] != 'reachable':
        return snapshots  # Respect transport/access/rate failures; don't try alternative routes.
    catalog = get(BASE + '/channels')
    if catalog['status'] != 'reachable':
        return snapshots + [catalog]
    channels = catalog['payload'].get('channels')
    if not isinstance(channels, list) or len(channels) > MAX_RECORDS:
        return snapshots + [dict(catalog, status='reachable_invalid_channels')]
    # These are channel identities, not fixture-to-channel assignments.
    wanted = {'TV4 Hockey', 'TV4 Motor', *(f'TV4 Sport Live {n}' for n in range(1, 5))}
    selected = {}
    for c in channels:
        if isinstance(c, dict) and c.get('name') in wanted and re.fullmatch(r'[a-z0-9-]+', str(c.get('slug', ''))):
            selected[c['name']] = c['slug']
    for name, slug in sorted(selected.items()):
        s = get(BASE + '/channel/' + slug); snapshots.append(s)
        if s['status'] != 'reachable':
            break
    # Catalog metadata is evidence of identity/coverage, not a programmes endpoint.
    snapshots.append({k: v for k, v in catalog.items() if k != 'payload'} | {
        'status': 'channel_catalog_validated', 'channel_count': len(channels),
        'selected_channels': sorted(selected)})
    return snapshots


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['probe', 'evaluate', 'demo'])
    parser.add_argument('--fixtures', type=Path, default=ROOT / 'fixtures/2026-10-29-31.json')
    parser.add_argument('--input', type=Path, help='Local snapshot bundle, never labelled live')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--cache-dir', type=Path, default=ROOT / '.cache')
    args = parser.parse_args()
    if args.command == 'demo':
        # Keep supplied research for audit only. It is not freshly verified or a live adapter.
        output = {'schema_version': 1, 'source_mode': 'archived_unverified_research', 'app_ready': False,
                  'note': 'Supplied ZIP research claims have not been revalidated. Never use as a production feed.',
                  'archive': 'sample/research-evidence-2026-10-28.json', 'results': []}
    else:
        fixtures = json.loads(args.fixtures.read_text())['fixtures']
        if args.command == 'evaluate' and not args.input:
            parser.error('evaluate requires --input snapshots.json')
        snapshots = live_probe(args.cache_dir) if args.command == 'probe' else json.loads(args.input.read_text())['snapshots']
        output = report(fixtures, snapshots, datetime.now(UTC), 'live_public_api' if args.command == 'probe' else 'file_evaluation')
    encoded = json.dumps(output, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.write_text(encoded)
        print(f"{args.output}: {len(output.get('results', []))} fixtures; "
              f"{sum(r['status'] == 'confirmed' for r in output.get('results', []))} exact confirmations")
    else:
        print(encoded, end='')
    return 2 if output.get('results') and all(r['status'] == 'unavailable' for r in output['results']) else 0


if __name__ == '__main__':
    raise SystemExit(main())

"""Synthetic data only. No test asserts a real match-to-channel assignment."""
from datetime import datetime, timedelta, timezone
from io import BytesIO
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch
from urllib.error import HTTPError
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import broadcast_lab as lab

NOW = datetime(2026, 10, 28, 12, tzinfo=timezone.utc)
F = {'id': 'synthetic-fixture', 'league': 'SHL', 'home': 'Brynäs IF', 'away': 'Färjestad BK',
     'display': 'Synthetic fixture, not real broadcast evidence', 'kickoff_local': '2026-10-29T19:00:00+01:00',
     'team_tokens': [['Brynäs IF', 'Brynäs'], ['Färjestad BK', 'Färjestad']], 'venue': 'Synthetic Arena'}
P = {'id': 'synthetic-programme', 'title': 'SHL: Brynäs IF – Färjestad BK', 'description': None,
     'isLive': True, 'start': '2026-10-29T18:45:00+01:00', 'channel': {'name': 'TV4 Sport Live 1 HD'}}


def snapshot(rows=None, generated=None, fetched=None, channel=None):
    payload = {'programs': [dict(P)] if rows is None else rows,
               'generatedAt': generated or lab.stamp(NOW)}
    if channel:
        payload['channel'] = {'name': channel}
    return {'url': lab.SPORT, 'fetched_at': fetched or lab.stamp(NOW), 'status': 'reachable',
            'payload': payload}


class MatchingTests(unittest.TestCase):
    def test_exact_teams_league_and_time(self):
        self.assertTrue(lab.exact_fixture_match(P, F)[0])

    def test_unicode_and_punctuation_aliases(self):
        self.assertTrue(lab.exact_fixture_match(dict(P, title='SHL: BRYNA\u0308S / FA\u0308RJESTAD'), F)[0])

    def test_broad_league_or_arena_is_not_exact(self):
        for text in ['SHL', 'SHL Från Synthetic Arena', 'SHL Brynäs vs Luleå']:
            self.assertFalse(lab.exact_fixture_match(dict(P, title=text), F)[0])

    def test_word_boundaries(self):
        self.assertFalse(lab.exact_fixture_match(dict(P, title='SHL Brynäslaget – Färjestad'), F)[0])
        self.assertFalse(lab.exact_fixture_match(dict(P, title='XSHL Brynäs – Färjestad'), F)[0])

    def test_replay_or_unknown_live_flag(self):
        for flag in [False, None, 1, 'true']:
            self.assertFalse(lab.exact_fixture_match(dict(P, isLive=flag), F)[0])

    def test_wrong_date_or_time(self):
        for start in ['2026-10-30T18:45:00+01:00', '2026-10-29T16:00:00+01:00', '2026-10-29T19:11:00+01:00']:
            self.assertFalse(lab.exact_fixture_match(dict(P, start=start), F)[0])

    def test_missing_or_invalid_timezone(self):
        for start in [None, '', '2026-10-29T18:45:00', 'not a date']:
            self.assertFalse(lab.exact_fixture_match(dict(P, start=start), F)[0])

    def test_utc_and_swedish_winter_time(self):
        self.assertTrue(lab.exact_fixture_match(dict(P, start='2026-10-29T17:45:00Z'), F)[0])

    def test_channel_identity_not_guessed(self):
        self.assertEqual(lab.canonical_channel('TV4 Sport Live 3 FHD'), 'TV4 Sport Live 3')
        self.assertNotEqual(lab.canonical_channel('TV4 Live 1'), lab.canonical_channel('TV4 Sport Live 1'))
        self.assertNotEqual(lab.canonical_channel('TV4 Hockey'), lab.canonical_channel('TV4 Sport Live 1'))
        for name in ['TV4 Play', 'TV4 Play Sport Hockey', 'C More Extreme', 'unknown', 'TV4SportLive0']:
            self.assertIsNone(lab.canonical_channel(name))


class NormalizationTests(unittest.TestCase):
    def result(self, snaps, now=NOW):
        return lab.report([F], snaps, now, 'file_evaluation')['results'][0]

    def test_exact_confirmation_has_provenance_and_expiry(self):
        r = self.result([snapshot()])
        self.assertEqual(r['status'], 'confirmed')
        self.assertEqual(r['linear_channel'], 'TV4 Sport Live 1')
        self.assertEqual(r['verified_at'], lab.stamp(NOW))
        self.assertEqual(r['expires_at'], lab.stamp(NOW + lab.TTL))
        self.assertFalse(r['app_ready'])
        self.assertEqual(r['source_mode'], 'file_evaluation')
        self.assertEqual(r['evidence'][0]['source_url'], lab.SPORT)

    def test_venue_candidate_is_never_confirmed(self):
        r = self.result([snapshot([dict(P, title='Live: Ishockey: SHL', description='Från Synthetic Arena.')])])
        self.assertEqual(r['status'], 'unverified')
        self.assertIsNone(r['linear_channel'])
        self.assertIsNone(r['verified_at'])
        self.assertEqual(r['unverified_candidates'][0]['match_basis'], 'venue_league_time_only')

    def test_replay_not_a_candidate(self):
        r = self.result([snapshot([dict(P, title='SHL', description='Synthetic Arena', isLive=False)])])
        self.assertEqual(r['unverified_candidates'], [])

    def test_ott_only_has_no_linear_confirmation(self):
        r = self.result([snapshot([dict(P, channel={'name': 'TV4 Play Sport Hockey'})])])
        self.assertEqual(r['status'], 'unverified')
        self.assertEqual(r['evidence'], [])

    def test_missing_epg_is_valid_empty_response(self):
        r = self.result([snapshot([])])
        self.assertEqual(r['status'], 'unverified')

    def test_conflicting_channels_are_not_auto_selected(self):
        r = self.result([snapshot([P, dict(P, id='other', channel={'name': 'TV4 Sport Live 2'})])])
        self.assertEqual(r['status'], 'conflict')
        self.assertIsNone(r['linear_channel'])

    def test_catalog_channel_applies_to_programme_rows(self):
        p = dict(P); p.pop('channel')
        r = self.result([snapshot([p], channel='TV4 Hockey')])
        self.assertEqual(r['linear_channel'], 'TV4 Hockey')

    def test_duplicate_endpoints_are_not_corroborating_sources(self):
        s = snapshot(); other = dict(s, url=lab.BASE + '/channel/tv4-sport-live-1')
        r = self.result([s, other])
        self.assertEqual(len(r['evidence']), 1)
        self.assertEqual(r['status'], 'confirmed')

    def test_expired_does_not_renew_from_evaluation_time(self):
        r = self.result([snapshot()], NOW + timedelta(hours=2))
        self.assertEqual(r['status'], 'stale')
        self.assertIsNone(r['linear_channel'])
        self.assertEqual(r['freshness'], 'expired')
        self.assertEqual(r['expires_at'], lab.stamp(NOW + lab.TTL))

    def test_old_source_generation_expires_before_fetch_time(self):
        r = self.result([snapshot(generated=lab.stamp(NOW - timedelta(hours=3)))])
        self.assertEqual(r['status'], 'stale')

    def test_future_or_missing_source_timestamp_is_unavailable(self):
        for generated in [lab.stamp(NOW + timedelta(hours=2)), 'bad', '2026-10-28T12:00:00']:
            self.assertEqual(self.result([snapshot(generated=generated)])['status'], 'unavailable')

    def test_access_failure_distinct_from_empty_data(self):
        r = self.result([{'url': lab.SPORT, 'status': 'access_denied', 'http_status': 403}])
        self.assertEqual(r['status'], 'unavailable')

    def test_partial_failure_retains_other_valid_evidence(self):
        r = self.result([snapshot(), {'url': lab.SPORT, 'status': 'http_error', 'http_status': 500}])
        self.assertEqual(r['status'], 'confirmed')

    def test_invalid_shape_is_not_live_coverage(self):
        for rows in [None, 'bad', [1], [{}]]:
            s = snapshot();s['payload']['programs'] = rows
            self.assertEqual(self.result([s])['status'], 'unavailable')

    def test_private_or_credential_urls_are_not_exported(self):
        for url in ['https://user:secret@tvprogramidag.se/program/test', 'https://private.example/secret',
                    'https://tvprogramidag.se/program/test?token=secret']:
            r = self.result([snapshot([dict(P, url=url)])])
            self.assertNotIn('secret', json.dumps(r))


class TransportTests(unittest.TestCase):
    def response(self, payload=None, content_type='application/json'):
        r = BytesIO(json.dumps(payload or {'programs': []}).encode())
        r.headers = {'Content-Type': content_type}; r.status = 200
        return r

    def test_only_reviewed_https_public_endpoints(self):
        for url in ['http://tvprogramidag.se/api/public/v1', 'https://evil.example/api/public/v1',
                    'https://token@tvprogramidag.se/api/public/v1', lab.SPORT + '&token=secret',
                    lab.BASE + '/channel/../private', lab.BASE + '/channels#secret']:
            self.assertFalse(lab.approved_url(url))
        self.assertTrue(lab.approved_url(lab.SPORT))
        self.assertTrue(lab.approved_url(lab.BASE + '/channel/tv4-hockey'))

    def test_cached_response_preserves_original_fetch_time(self):
        with tempfile.TemporaryDirectory() as directory:
            opener = Mock(); opener.open.return_value = self.response()
            a = lab.fetch_json(lab.SPORT, Path(directory), NOW, opener)
            b = lab.fetch_json(lab.SPORT, Path(directory), NOW + timedelta(minutes=5), opener)
            self.assertEqual(opener.open.call_count, 1)
            self.assertEqual(a['fetched_at'], b['fetched_at'])
            self.assertEqual(b['transport'], 'cache')

    def test_expired_cache_refetches(self):
        with tempfile.TemporaryDirectory() as directory:
            opener = Mock(); opener.open.side_effect = [self.response(), self.response()]
            lab.fetch_json(lab.SPORT, Path(directory), NOW, opener)
            lab.fetch_json(lab.SPORT, Path(directory), NOW + timedelta(hours=2), opener)
            self.assertEqual(opener.open.call_count, 2)

    def test_wrong_content_type_or_too_large(self):
        with tempfile.TemporaryDirectory() as directory:
            opener = Mock(); opener.open.return_value = self.response(content_type='text/html')
            with self.assertRaises(ValueError):lab.fetch_json(lab.SPORT, Path(directory), NOW, opener)
            r = BytesIO(b'x' * (lab.MAX_BYTES + 1));r.headers = {'Content-Type':'application/json'};r.status = 200
            opener.open.return_value = r
            with self.assertRaises(ValueError):lab.fetch_json(lab.SPORT, Path(directory), NOW, opener)

    def test_redirect_requires_manual_review(self):
        with self.assertRaises(ValueError):
            lab.NoRedirects().redirect_request(None, None, 302, '', {}, 'https://other.example')

    def test_access_denial_stops_probe_no_alternate_routes(self):
        for status in [401, 403, 429]:
            with patch.object(lab, 'fetch_json', side_effect=HTTPError(lab.SPORT, status, '', {}, None)) as fetch:
                result = lab.live_probe(Path('/unused'))
                self.assertEqual(fetch.call_count, 1)
                self.assertEqual(result[0]['http_status'], status)


if __name__ == '__main__':
    unittest.main()

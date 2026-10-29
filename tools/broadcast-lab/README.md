# Broadcast Lab — free, local SHL channel recommendations

Adapted from the owner's ZIP (SHA-256
`748d460a73e62b88bf6b5bf78ba311f3318225b2f2bbd6ec7cc65d711b3f9935`).
Python 3.10+, standard library only. No paid API, account, card or hosted backend.

```bash
cd tools/broadcast-lab
python3 -m unittest discover -s tests
python3 broadcast_lab.py serve --port 8765
# Open http://127.0.0.1:8765/ and press Hämta gratisdata.
python3 broadcast_lab.py probe --output live-report.json
```

The optional local server binds only 127.0.0.1, validates refresh Host/Origin,
accepts no credentials or arbitrary source URLs, and does not serve cache files.
The refresh button performs real public API requests in Python, with a one-hour
private local cache. No background network polling. At most eight spaced GETs per
probe; failures/access denial/rate limits stop the scan, redirects require review.
Do not run a separate `http.server`: the viewer's refresh API belongs to `serve`.

Use `--fixtures path/to/official-fixtures.json` with `serve` or `probe` for future
fixtures. The checked-in reference has 14 public fixture records for 29/31 October;
it is not a schedule scraper or hardcoded broadcaster table. Record reference URL,
exact teams, competition, kickoff with offset and official venue. No fuzzy venue
aliases are accepted. The Android integration reuses its existing official reader.

[Results and limitations](COMPARISON.md), [live evidence](evidence/2026-10-29-venue-policy.json).
The approved rule yields 12 probable matches in this sample, including both FBK
references. This is coverage, not proven accuracy. No exact-team confirmations.

## Normalized contract

`results[]` includes fixture identity/teams/date/time/venue/reference URL, status,
nullable exact `linear_channel`, display label, checked time, source evidence with
fetch/generation timestamps, expiry/freshness, conflict reasons and advance days.

- `probable_external_match`: unique fresh SHL + arena/time evidence; **Trolig kanal**.
- `confirmed`: explicit teams/fixture evidence; **Bekräftad kanal**.
- `conflict`, `stale`, `unverified`, `unavailable`: no automatic channel; manual choice.

`verified_at` is reserved for exact confirmation. Venue-only evidence remains in
`venue_candidates`. OTT, station numbers, replay exclusion, conflicting broadcasts
and reschedules fail closed. No confidence percentage is invented. Output remains
`app_ready: false` until the integration PR/physical QA is approved.

`evaluate --input snapshots.json` accepts local snapshot envelopes with original
`fetched_at`, `payload.generatedAt` and `payload.programs`; it retains expiry and
uses `file_evaluation`, never a newly fetched live label. `demo` only identifies the
ZIP's archived, unverified research. Archives and historical reports are not live feeds.

Only normalized observations go in Git. Raw cache files stay ignored, private and
local; never supply IPTV credentials or tokens. Data attribution and terms:
[TV-Programidag](https://tvprogramidag.se), [API documentation](https://tvprogramidag.se/utvecklare).

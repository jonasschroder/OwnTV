# Min TV – Broadcast Lab Phase 2

Standalone Python 3.10+ lab, adapted from the owner's `MinTV-Broadcast-Lab.zip`.
No external packages, Android changes, credentials, background jobs or production feed.
Input ZIP SHA-256: `748d460a73e62b88bf6b5bf78ba311f3318225b2f2bbd6ec7cc65d711b3f9935`.

```bash
cd tools/broadcast-lab
python3 -m unittest discover -s tests
python3 broadcast_lab.py probe --output live-report.json
python3 -m http.server 8765 --bind 127.0.0.1
# Open http://127.0.0.1:8765/ and load live-report.json.
```

`probe` makes at most **8** reviewed public API requests: hockey today, channel
catalog, and six TV4 channel schedules. It discovers slugs from the catalog rather
than guessing private endpoints. Requests are spaced; successful responses cache
for one hour in ignored `.cache/` (permissions 700/600). No automatic retries;
access denial, rate limit or transport failure stops that probe. Redirects require
manual review. A changed endpoint/terms contract needs review before rerunning.
The 1-hour TTL is our lab policy, not a provider availability guarantee.

Only normalized observations go in Git. Keep raw response/cache files local.
Do not supply IPTV URLs, credentials or provider tokens. No licensed adapter is
implemented until access, schemas, rights and exact SHL coverage are established.

## Current result

See [comparison](COMPARISON.md) and the [real network observation](evidence/2026-10-29-tvprogramidag.json).
The channel endpoint **does contain advance schedules**, despite the sport endpoint
being today-only. Both target matches have venue/time candidates, but neither has
both teams in the programme metadata. **No automatic fixture assignment is verified.**

The local viewer loads a saved observation; it never claims that viewing a file
made a new live request. It shows expiry and separates candidates from confirmations.
The ZIP's old manually entered channel claims are archived in `sample/`, excluded
from the adapter and never promoted to verified live data. `demo` only reports that
archive's unverified status. `sample/example-report.json` was removed to avoid a
second, misleading result contract.

## Output contract

Each `results[]` entry carries the exact fixture (home/away, kickoff with offset,
competition, venue and reference), nullable `linear_channel`, `status`, `checked_at`,
nullable `verified_at`, `expires_at`, freshness, source mode, exact evidence and
separate `unverified_candidates`. Each observation carries provider, source endpoint,
public programme ID, observed title/description/start, fetch/generation/expiry times.
No playback addresses or private account identifiers are accepted by this adapter.
All results have `app_ready: false`: this is a research tool, not an app integration.

Only explicit teams + SHL + live programme start within kickoff −60/+10 minutes
can confirm a channel. Arena/time alone cannot. Multiple exact linear channels
produce `conflict` and no automatic choice. Replays, OTT, missing language/timezone,
expired/future timestamps and unknown/legacy channel names fail closed. TV4 Live N
is not silently equated with TV4 Sport Live N. A broadcast repeated by two endpoints
of one provider is not independent corroboration.

Snapshots can be evaluated offline with `evaluate --input snapshots.json`. The
input envelope is `{ "snapshots": [{ "url": "…", "status": "reachable",
"fetched_at": "…", "payload": { "generatedAt": "…", "programs": [] } }] }`.
Evaluation retains the original timestamp/expiry and is labelled `file_evaluation`,
never newly verified live data. Synthetic tests use that envelope; no raw provider
database is committed. Cached probe responses retain their original fetch times.

Fixture input is a bounded manually checked reference for 29/31 October, with no
channel assignments. Refresh it from an authorized schedule owner before future
experiments; the lab is not a general fixture crawler.

Data attribution: [TV-Programidag](https://tvprogramidag.se).
Terms/docs: [public API](https://tvprogramidag.se/utvecklare).

# Free SHL venue matching — owner-approved policy, 29 October 2026

**Recommendation: integrate the free TV-Programidag channel API directly in Min TV.**
No paid provider, trial, account, card or hosted backend is required. The owner now
accepts a unique official-arena/time match as **probable**, without teams in EPG.
That changes the earlier Phase 2 decision; it does not make arena evidence confirmed.

## Live results

[New real API observation](evidence/2026-10-29-venue-policy.json) records public
endpoint URLs, HTTP status, body hashes, original fetch/generated times and expiry.
No raw programme database or private IPTV information is committed.

| Official fixture, local time | Output | Advance visibility |
|---|---|---|
| Brynäs–Färjestad, 29 Oct 19:00, Monitor ERP Arena | `probable_external_match`, **TV4 Sport Live 1**, programme 18:45–21:30 | Same-day observation; no claim of D−1 publication |
| Växjö–Färjestad, 31 Oct 15:15, Vida Arena | `probable_external_match`, **TV4 Sport Live 3**, programme 15:00–17:45 | Observed D−2, before matchday |

Across the 14 official references: **6/7 probable on each date; 12/14 total**.
No ambiguous matches were found in this bounded sample. Neither target has exact
teams in the programme, so neither is labelled confirmed. Incorrect-match count
is **unknown**: no independently authorized full broadcaster ground truth was
available, so 12/14 coverage must not be presented as a measured accuracy rate.

Both Djurgården fixtures remain manual. On 31 Oct, official Avicii Arena differs
from the programme's Hovet. No unsupported venue equivalence is introduced.
References: official [29 Oct](https://stats.swehockey.se/GamesByDate/2026-10-29/ByTime/90)
and [31 Oct](https://stats.swehockey.se/GamesByDate/2026-10-31/ByTime/90) schedules.
The input file contains fixture facts, never production broadcaster assignments.

Channel schedules contain programme starts through **2 Nov 05:00 CET**; latest
observed live SHL is 31 Oct 17:50 CET. Today-only `/sport` alone is insufficient.
This is an observed lower bound, not a promised publishing horizon or reliability
SLA. The original Phase 2 observations remain historical evidence, not live data.

## Rule and safeguards

- Require SHL, live flag, exact normalized official arena phrase, programme start
  within kickoff −60/+10 minutes, and end after kickoff. No fuzzy/renamed-arena aliases.
- Require one distinct recognized linear station, fresh source timestamps and no
  conflicting fixture identity, simultaneous fixture at that arena, contradictory
  teams, or overlapping different SHL broadcasts on the same channel.
- Return **Trolig kanal** / `probable_external_match` for unique venue evidence;
  **Bekräftad kanal** / `confirmed` when explicit team evidence confirms it.
  No numerical confidence score. Ambiguity/expired/missing data stays manual.
- Reject TV4 Play, legacy C More identity guesses, replays, studio and other leagues.
  Numbered TV4 Sport Live stations remain distinct. Never fetch media/stream URLs.

## Free source contract and network

[TV-Programidag documentation](https://tvprogramidag.se/utvecklare) permits free
commercial use with clickable attribution, max 60 requests/min/IP, hourly updates
and source caching 2–60 minutes; no availability guarantee. Host: `tvprogramidag.se`.
No credentials or remaining network permission blocker was observed after authorized
real network access. The lab uses at most eight spaced requests and a one-hour cache;
Android uses the six reviewed channel endpoints with the same conservative budget.
Access/rate failures stop the scan. No redirect/alternate-host workaround or paid fallback.

## Usability and integration

`python3 broadcast_lab.py serve` starts the optional loopback-only viewer. **Hämta
gratisdata** runs the actual bounded probe in Python; browser code has no secrets.
The page shows status, reason, source update/fetch/expiry and manual fallback. CLI
JSON export remains available. No hosted backend is needed.

The lab fixture reference is deliberately a bounded 14-match file; supply future
official fixture JSON through `--fixtures`. It is not a new schedule scraper.
Android reuses its existing opted-in official SHL reader, now preserving the Venue
column, its cache and lifecycle safeguards. The separate integration PR performs
the free join on-device, then reuses authorized IPTV matching/quality/preferences,
local EPG precedence, Watch validation and manual selection. No paid API or backend.

## Validation and remaining limitations

41 Python regression tests and the existing CI discovery hook cover exact/probable,
conflicts, reschedules, expiry, end times, identity, aliases, replay, transport and
cache. Browser refresh was exercised against real API/cache responses. The separate
Kotlin regression fixture contains selected real observations, not production mappings.

Long-term false-positive rate, schedule corrections and completeness beyond the
observed horizon still need repeated matchday checks. Missing official venues and
unsupported arena renames intentionally reduce coverage. Final Chromecast acceptance
requires a separately approved signed QA update; no installation is part of this work.

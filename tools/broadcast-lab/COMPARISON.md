# Broadcast Lab Phase 2 — 29 October 2026

**Decision:** do not yet integrate an external automatic fixture→linear-channel
feed into Min TV. Use TV-Programidag as the first free schedule candidate; request
richer team/event metadata. For a direct licensed mapping, prioritize a **Ronin
Sport trial**, conditional on Swedish SHL sample acceptance and written rights.
No provider has yet met all requirements. No Android code or releases changed.

## Actual network evidence

The reproducible `probe` ran from the Mac against documented public JSON endpoints;
its normalized [observation](evidence/2026-10-29-tvprogramidag.json) includes UTC fetch
and generation timestamps, source URLs, response hashes, coverage and expiry.
The eight requests were HTTP 200: catalog (226 channels), hockey today (33 rows),
and six channel schedules (21–50 rows each). No API key was used. Cache data/raw
bodies remain local and ignored; the repository contains selected observations only.

| Target fixture (Europe/Stockholm) | Real API observation | Verdict |
|---|---|---|
| Brynäs–Färjestad, 29 Oct 19:00 | TV4 Sport Live 1; programme 18:45; SHL, Monitor ERP Arena; **teams absent** | Venue/time candidate only; no verified assignment. Observation is on matchday, so D−1 publication is not proved. |
| Växjö–Färjestad, 31 Oct 15:15 | TV4 Sport Live 3; programme 15:00; SHL, Vida Arena; **teams absent** | D−2 venue/time candidate observed before matchday; no verified assignment. |

Both channel identities really occur in the free API; neither row proves both
teams. The lab leaves `linear_channel: null`, `verified_at: null`, with the observed
programme in `unverified_candidates`. It does not load the ZIP's old manual channel
claims as evidence. No broadcaster relationship is hardcoded into the adapter.

Against seven official fixture references on each date: **0/7 exact confirmations**
on 29 Oct and **0/7** on 31 Oct; venue/time candidates **6/7** on each date. This is
coverage within the six reviewed TV4 channels, not a claim about all broadcasters.
The Djurgården games remain unmatched. On 31 Oct, official fixture venue Avicii
Arena and the Hockey programme's Hovet description also disagree; do not alias
these venues silently.

The sport endpoint is today-only. The *documented channel endpoint* actually
returns starts through **2 Nov 05:00 CET**. Latest observed live SHL starts are
31 Oct 17:50 CET. Thus advance schedules exist, but the guaranteed publishing
horizon, completeness, and exact-team coverage are unknown. One snapshot cannot
establish long-term accuracy, SLA or the first publication time; response limits
also make this a lower-bound observation. Future date parameters were not guessed.

Fixture references were manually checked against the official [29 Oct](https://stats.swehockey.se/GamesByDate/2026-10-29/ByTime/90)
and [31 Oct](https://stats.swehockey.se/GamesByDate/2026-10-31/ByTime/90) schedules.
No schedule scraper or arena→team/channel lookup table is implemented.

## Provider comparison

| Provider / exact host | Availability and fields | Terms, cost and blocker |
|---|---|---|
| **TV-Programidag** — `tvprogramidag.se` | **Reachable/validated JSON; exact fixture fields unavailable in sampled SHL programmes.** IDs, title, description, start/end, live flag, channel name/slug/logo. Today sport + multi-day channel rows. No SHL coverage guarantee. | [Documented API](https://tvprogramidag.se/utvecklare): free including commercial use, clickable attribution; 60 requests/min/IP; provider caching 2–60 min, hourly updates, no uptime guarantee. Lab caches 60 min and makes at most 8 spaced requests. Ask for home/away/event IDs or richer programme descriptions and supported horizon. |
| **Ronin Sport** — `www.roninsport.io` (marketing; API hostname not disclosed) | **Authentication/contract required, API UNTESTED.** Documented product: fixtures, teams, leagues and TV/streaming channels, UTC, ID matching. SHL and exact Swedish numbered linear channels unverified. | [API product](https://www.roninsport.io/api-feeds/): two months free; contact `info@roninsport.io` with product/market. No public rate limit, future horizon, price after trial or redistribution/cache terms. Obtain base URL, credentials, schema, written rights and quote; no private endpoint guessing. |
| **Bombus** — `bombusinteractive.se`, published API host `api.bombusinteractive.se` | Public documentation **reachable**; partner API **authentication required/UNTESTED**. SHL/HA fixtures/results, tables and badges documented; exact broadcast assignments **not documented**. | [Data product](https://bombusinteractive.se/data): Bearer partner token, selected partners, hourly sync, five-minute cache guidance; no live results. Ask `info@bombusinteractive.se` specifically for linear sport-TV schedule product/schema, trial, rights, limits and quote. Price/trial not published. No tokenless authenticated API probing. |
| **TheSportsDB** — `www.thesportsdb.com` | **Reachable/validated development API; required current channel identity unavailable.** Five bounded GETs with published key `123`: target event IDs 2476585/2476588 both return **C More Extreme**. Date-TV endpoint has only one row; schedule endpoint only three under free limits. [Evidence](evidence/2026-10-29-sportsdb.json). | [Docs](https://www.thesportsdb.com/documentation): 30 req/min free, limited records. [Pricing](https://www.thesportsdb.com/pricing): developer $9/month, small business $20/month. [Terms](https://www.thesportsdb.com/docs_terms_of_use.php): development use free; app-store publication requires paid subscription; attribution and separate third-party artwork rights. Do not translate legacy C More identity to TV4 or infer paid-tier coverage. Timezone semantics also need confirmation. |
| TVmatchen / Sportstats / TV4-distributor guides | No newly authorized automatic JSON feed established. Not probed as scrapers. ZIP's manually researched claims are **archival, not live evidence**. | [TVmatchen terms](https://www.tvmatchen.nu/page/anvandarvillkor) limit use to private/non-commercial use; obtain a data/API licence for a distributed integration. Sportstats/TV4 machine reuse, API contract and exact channel horizon remain **UNTESTED**; public visibility does not establish redistribution rights. |

## Network versus access blockers

Sandbox DNS initially could not resolve `tvprogramidag.se`; authorized real network
execution succeeded. No remaining environment block was observed for the tested
hosts `tvprogramidag.se`, `www.thesportsdb.com`, `stats.swehockey.se` or
`bombusinteractive.se`. Ronin public documentation was read; its actual API host
must come from Ronin. `api.bombusinteractive.se` is documented but was not called
without its partner token. Credentials/contracts, not DNS workarounds, are needed.
Do not send tokens in chat, browser code, committed files or logs. No providers
were contacted, accounts created or subscriptions purchased.

## Acceptance gate before choosing a production provider

1. Obtain explicit SHL fixture identity plus exact linear station IDs/names; TV4
   Play alone is never sufficient. Verify both target fixtures and several other
   matches against authorized independent evidence, including a D−1/D−3 snapshot.
2. Establish supported horizon, correction handling, timezone, rate/cache limits,
   permitted in-app display/redistribution and the ongoing cost in writing.
3. Recheck repeated snapshots across matchdays: measure exact-team coverage,
   freshness and false positives. The current single observation is not reliability proof.
4. Keep the normalized contract independent of provider and join to the user's
   IPTV library locally in a separate reviewed Android change. Expired, ambiguous,
   unknown, OTT-only and venue-only records must preserve manual fallback.

**Recommendation:** pursue the free provider's richer metadata first. If it cannot
supply exact fixture identity, validate Ronin's trial before paying or integrating.
Bombus is a secondary enquiry because broadcast fields are not promised. Do not
ship a venue-only heuristic or a legacy-channel alias as verified broadcast intelligence.

## Validation and scope

30 synthetic regression tests pass: exact/ambiguous/late/expired data, timezone,
team boundaries, replay rejection, OTT/legacy identities, independent channel
numbers, catalog rows, conflicts, partial failures, deduplication, HTTP size/type,
redirect refusal, cache age and access-denial stop. No tests assert real broadcast
assignments. The actual network probe is recorded separately from synthetic tests.
No Android build/emulator run is needed locally for this tools-only branch; existing
GitHub workflows are unchanged. A small `tools/tests` discovery hook runs these
30 lab tests in the existing Python-tools CI step. PR CI status is reported in
the draft PR.

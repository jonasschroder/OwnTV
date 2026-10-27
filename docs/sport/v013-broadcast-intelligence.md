# v0.13 hockey broadcast intelligence

Base: `70157472fd1d7460b07ba87b446dad03f9055012` (latest main when work began).
Research checked **27 October 2026**, 10:59 UTC. No owner-device installation or private provider requests.

## Source decision

| Source | Exact assignment and advance coverage | Access, terms and cost | Verification / reliability |
|---|---|---|---|
| Existing authorized IPTV/XMLTV guide | Channel ID, programme teams/league and UTC interval. Depends on imported provider coverage; native lookup remains bounded to seven days. | Reuse existing authorized imports/mapping/offsets. No new provider, credential, request or fee. Provider rights and subscription still apply. | Production Room regression verifies a later fixture beyond the first 128 rows, guide changes and revoked access. Real owner's guide coverage is **not measured**. XMLTV is a format, not a licence to another site's data. |
| [TVmatchen](https://www.tvmatchen.nu/page/anvandarvillkor), `www.tvmatchen.nu` | Both exact examples below verified; linear information observed four days ahead, not a guaranteed publishing horizon. | Public viewing is free. Terms restrict use to private purposes and provide no general automated redistribution licence. Existing personal opt-in reader remains off by default, today…today+2, six requests/two hours, unchanged robots/terms/redirect guards. | Fresh bounded HTTP 200 match pages passed the **actual production fixture parser**. Website evidence can change; no SLA. No wider scraper or automated default enabled. |
| [Tvsporten](https://www.tvsporten.nu/lag/farjestad-bk), `www.tvsporten.nu` | Exact fixture time and numbered channel for both examples; four-day linear coverage observed. Later fixtures show Play only, which proves no linear assignment. | Free public viewing; no public reuse licence/API contract/rates established. [Owned by Ronin Sport](https://www.tvsporten.nu/om-oss). Seek licensed API access, not a product scraper. | Bounded HTTP 200 page rechecked; exact fixture rows agree with TVmatchen. Public-source research only. |
| [Ronin API](https://www.roninsport.io/api-feeds/), `www.roninsport.io` | Documented fixtures/teams/league/kickoff/channels, UTC, TV versus streaming. Swedish SHL/HA **numbered linear coverage and days ahead unverified**. | Commercial access agreement. Page offers two months free; recurring price is not published. Schema, API hostname, credentials, quotas, caching/display/retention rights supplied on agreement. | Official product page HTTP 200. **No authenticated API call or working external POC claimed.** Best first licensing candidate because its related public guide has the exact examples. |
| TV4/distributor guides, `www.tv4.se`; `www.tv.nu` | Potential schedule data, but required content not fetched. | Current robots deny ChatGPT-User; no bypass. TV.nu terms prohibit automated collection except explicit allowance/agreement. Need authorized distributor export/API and written rights. Price/horizon unknown. | Robots HTTP 200 only. Restricted, not a network outage. No private endpoints or impersonation. |
| [Gracenote OnConnect](https://developer.tmsapi.com/docs/data_v1_1/International_lineups), `developer.tmsapi.com` | Official docs list Sweden, lineup/station programme schedules. Exact TV4 Sport Live services, SHL fixture fields and horizon unverified. | API key and licensed package/quote required; verify current contract/rates/cache rights. | Documentation verified; no live API request or sample coverage proof. Secondary candidate, not a ready adapter. |
| [SVT Tablåtjänsten](https://api.svt.se/tablatjansten/docs), `api.svt.se` | Documented SVT schedules, typically up to four weeks subject to publication. Does not provide TV4 channel assignments. | Documented schedule service; no reason to add it for TV4 hockey. | Documentation inspected; no SHL/TV4 coverage claimed, no new integration. |

Network availability is established for the bounded public checks; there is no missing Codex domain
permission blocking the chosen local implementation. The missing external dependency is an **authorized
feed contract and credentials**, not an undiscovered public endpoint. Ronin's API hostname must come
from that agreement; do not guess URLs, ship a shared secret in the APK, or query a site's hidden API.

## Real upcoming fixture evidence

| Stockholm time | Exact fixture | Verified linear service | Streaming kept separate |
|---|---|---|---|
| 29 Oct 2026 19:00 (`18:00Z`) | [Brynäs IF–Färjestad BK](https://www.tvmatchen.nu/match/brynas-if-farjestad-bk-1896219) | TV4 Sport Live 1 | TV4 Play Hockey |
| 31 Oct 2026 15:15 (`14:15Z`) | [Växjö Lakers–Färjestad BK](https://www.tvmatchen.nu/match/vaxjo-lakers-farjestad-bk-1893256) | TV4 Sport Live 3 | TV4 Play Hockey |

Both also appear in Tvsporten's exact Färjestad rows. Retrieved canonical fixture JSON was passed to
`TvmatchenHtml.assignment`, verifying teams, competition, instant, canonical URL and linear flags.
A whole-page substring search is **not** evidence: unrelated channels also appear in site navigation.
Only compact public factual results and SHA-256 are committed in `v013-source-evidence.json`;
raw downloaded pages remain local outside Git. Research evidence and test fixtures are never a
production assignment table. On 27 October only the first example is inside the experimental +2-day
window; this PR intentionally does not extend it or enable the experiment for existing profiles.

## Working native implementation

- Existing current/upcoming local reader returns at most 128 programmes. With half-hour slots that
  covers about 64 hours, so a correctly imported later match was invisible. When that cap is reached,
  merge exact known fixture windows and read only uncovered tails via the existing mapped/shifted
  Room path. At most 64 channels, 12 merged windows/channel, 128 rows/window and the existing
  eight-second foreground deadline. Any truncated scan fails closed for automatic source uniqueness.
- Scan known fixtures in the loaded hockey competitions, including teams other than the favourites;
  preserve the original profile's team preferences and Home prioritization. No external requests,
  background service, dependency, decoder, schema or Core-pin changes.
- Keep evidence scoped to profile/source context and expire a local scan after three minutes;
  resolved direct-channel state expires after two minutes. A programme's future stop does not make an
  old failed scan indefinitely fresh. Refresh remains the existing two-minute **foreground-only** loop.
  Evidence time is scan time, not a claim about the provider's last successful XMLTV refresh.
- Sport cards display exact confirmed channel names. Local fixture EPG wins conflicting external
  metadata. Known external names remain visible even if absent from IPTV. Unknown and ambiguous
  states do not invent Watch actions. TV4Play aliases never become linear evidence.
- Short OK on a uniquely resolved card plays using the existing player. Long OK opens existing match
  details/manual choices; otherwise short OK opens details. Existing hero action uses the same verified
  card resolution. No new navigation row or persistent submenu.
- Immediately before playback, reread the exact stored fixture window and recheck the current
  profile/source authorization, preferences and fixture identity. This also works for non-favourite
  fixtures and later games; the old first-128 path cannot reject a valid late-game click.

## Validation and remaining limits

Local validation: **40 focused JVM regressions PASS**, plus the separate production-parser check of both newly fetched public fixture pages. QA app/instrumentation builds PASS; **3 native Room/D-pad tests PASS** (one real database case and the card case at 1080p/720p). i18n and executed-test inventory PASS. Mandatory CI results are linked in the PR.
The native cases are explicitly required in the existing executed-test inventory; no suite is removed.
Screenshots use the actual native Sport screen and global header with **synthetic** programme/channel
fixtures on a disposable API 34 TV emulator: [1080p](v013-images/sport-broadcast-1920x1080.png),
[720p](v013-images/sport-broadcast-1280x720.png). They are not real Chromecast playback evidence.

The app can resolve automatically only when the authorized guide contains sufficiently specific match
information or the existing permitted external reader supplies usable evidence. Broad/missing/stale
EPG, ambiguous providers, capped libraries or unpublished broadcasters remain explicit unknown states.
There is no new default external feed. To complete it: obtain a Ronin (or equivalent) sample/contract
proving the two fixtures, SHL/HA linear IDs, horizon, reschedule/cancellation updates and timezone;
then establish credential handling, request budget/TTL/negative caching and display/retention rights,
and implement contract tests against that verified schema behind `BroadcastMetadataSource`.
Physical provider coverage, actual remote long-hold behaviour and playback still require approved QA.
Both existing Chromecast installations remain untouched. No merge, release signing or publication.

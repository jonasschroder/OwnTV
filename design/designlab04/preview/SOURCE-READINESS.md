# Min TV Design Lab — source readiness (2026-10-24)

Read-only Android audit; changes confined to this local lab. No app, Core pin, branch, CI, device data or release changes. Runtime evidence: `probes.json`, plus browser LIVE result. Historical probes in repository docs are not treated as current validation.

## Inventory / evidence

| Dependency | Existing owner / contract | Current availability and reliability |
|---|---|---|
| IPTV channels | Core M3U/Xtream/Stalker source repositories; SwedishChannelStore logical grouping | UNTESTED in lab. Authorized profile-owned source required; no provider URL or device data read. Never infer access from channel name. |
| EPG current/next/grid | Core EpgRepository, XMLTV/Xtream short EPG/StalkerEpgLoader, EpgDao, EpgMatcher/EpgAutoMatcher; app SwedishRowEpgStore/HockeyEpgStore | UNTESTED private feeds. Existing matching/manual overrides/time offsets must be reused. Coverage depends on actual provider, IDs and guide freshness. No public universal Swedish guide established. |
| Logos / programme artwork | Provider logoUrl; EpgLogoStore opt-in feed `<channel><icon>` overrides; XMLTV details; TMDB metadata images | UNTESTED assets. Feed permission and image rights required. Prefer existing provider/guide assets; readable text fallback. No screenshot-derived images or invented real programme images. |
| SHL / HockeyAllsvenskan fixtures, results, table | ShlRepository/SportsRepository + ShlData parser, SportsFetch and diagnostics | stats.swehockey.se robots returned404 (host reachable, no data parser probe this iteration). HTML reuse rights UNKNOWN; existing reader opt-in/off by default. Prior schedule/table success is historical, not live-score coverage. |
| Verified match→linear TV | BroadcastMetadataSource, BroadcastFixture/Assignment validation; TvmatchenBroadcastSource and exact EPG confirmation | TVmatchen robots200, disallows `/api`; terms reviewed, private use only, no general automated reuse licence established. TERMS_RESTRICTED for new integration pending permission. No scraping probe. |
| TV Hockey variants / manual channel | ShlChannelOverrides FixtureChannelChoice bound to profile, fixture, source and channel; canonical channel matching | Existing library and current fixture evidence needed. Naming is identity assistance, not broadcast proof. UNTESTED provider library. Do not populate guessed channels in LIVE. |
| Twitch ohnePixel | TwitchStatus / TwitchActivation / device grant; encrypted Android token owner | api.twitch.tv Helix streams returned401: AUTH_REQUIRED. Requires registered Client-ID + app/user access token. No token extracted or minted. Streams returns title/category/viewers/start time when authorized, empty data can mean offline. |
| Movies / series / images | Core TmdbProvider: self-host > own key > shared Worker, MetadataBudget and cache | api.themoviedb.org configuration401: AUTH_REQUIRED. Existing default tmdb.owntv.me identity headers/budget must not be bypassed. Self-host and legacy worker not probed. Provider metadata remains fallback in Android. |
| TheSportsDB candidate | Separate lab adapter, not Android integration | REACHABLE_VALIDATED partial SHL JSON: HTTP200 `events`, one Malmö–Timrå fixture, league4419, teams/date/time/status/score fields. Free limit1, not full fixtures or live scores. No images downloaded. |
| TheSportsDB broadcasts | Public documented `lookuptv.php?id=2476548` | HTTP200 `tvevent` with Oct15 Brynäs–Djurgården / C More Extreme. REACHABLE BUT REQUIRED CURRENT DATA UNVERIFIED: historical event, stale channel branding, no current fixture linkage. Not promoted to verified assignment. |
| Sportradar candidate | https://developer.sportradar.com/ice-hockey/reference/overview | Docs reachable; Global Ice Hockey explicitly lists SHL. AUTH/CONTRACT REQUIRED, data UNTESTED. HA, exact Swedish linear broadcasts, quotas, caching and rights require written coverage/sample agreement. Live graphics product is not proof of broadcast assignments. |
| API-Sports candidate | https://api-sports.io/documentation/hockey/v1 | Docs yielded no readable content through research tool: ENVIRONMENT/RENDERING BLOCK, not proof source down. Data UNTESTED. Key, current SHL/HA coverage, rate plan, storage/redistribution rights unresolved. |
| Goalserve candidate | https://www.goalserve.com/en/sport-data-feeds/hockey-api/sample/29 | Research tool inaccessible: ENV_BLOCKED. No alternate-host workaround. Data/fields/competitions/quotas/rights UNTESTED; commercial agreement/sample required. |

## Documented usage / request policy

- TheSportsDB: https://www.thesportsdb.com/docs/api and https://www.thesportsdb.com/docs_terms_of_use.php. Public development key123; official API development use permitted, website scraping prohibited. Free30req/min, premium100/min (429 backoff). Paid subscription required for app-store publication; third-party image rights remain separate. Free league-next limited1; paid live-score coverage is not verified for SHL/HA. Lab caches900s, failures120s, no polling, 8s timeout, 512KiB cap, no redirects, one fixed endpoint. This is a *retrieval freshness* indicator, not proof of timely source scores. Timestamp timezone unresolved: display raw supplied time, no UTC assumption.
- Swehockey: https://stats.swehockey.se/ . No documented public API licence/rate contract established. Existing app budget12GET/2h shared; discovery/tables6h, fixtures1h matchday/6h otherwise; Retry-After/backoff and persistent access-stop must remain. Robots404 grants no reuse rights. Do not enable lab scraping until authorized.
- TVmatchen: https://www.tvmatchen.nu/page/anvandarvillkor . Website terms limit use to private use; obtain integration permission/API contract before automated lab use. No documented API quota/cache licence established. `/api` forbidden in robots; never probe it.
- Twitch: https://dev.twitch.tv/docs/api/reference/#get-streams and https://dev.twitch.tv/docs/api/guide/ . Helix authentication and token-bucket response rate headers/429 must be respected. Future lab policy: server-only token,60s status cache, visible-only refresh. Follow Twitch developer agreement/branding/embedding conditions; status permission does not authorize arbitrary rebroadcast. Authenticated data not tested.
- TMDB: https://developer.themoviedb.org/docs/faq and https://developer.themoviedb.org/docs/rate-limiting . Non-commercial API use requires attribution/logo/credit; commercial licence via TMDB. API key or read token required. Approximate upper40req/s is changeable; obey429. Reuse app bounded cache/budget; no bulk fetching. Programme photos/logos require actual rights, not merely reachable image URLs.

## Exact hosts / credentials

Ordinary sandbox probes failed name resolution for all six tested hosts; explicitly permitted execution succeeded. No endpoint access control was bypassed. Research web-tool access is separate from local runtime access.

- Local public fixture adapter needs **www.thesportsdb.com**. Works with approved network execution; no private credential for development key123. Image host **r2.thesportsdb.com** observed in JSON, UNTESTED and deliberately not enabled (rights unresolved).
- Twitch **api.twitch.tv**, auth **id.twitch.tv**, docs **dev.twitch.tv**. Need Client-ID and securely provisioned server OAuth token; never browser token. Auth host data not tested here.
- TMDB **api.themoviedb.org**, images **image.tmdb.org**, docs **developer.themoviedb.org**. Need authorized server key/read token and attribution. **tmdb.owntv.me** is existing authorized Worker contract, not a public bypass; legacy **owntv-tmdb-meta.xiannero.workers.dev** and custom self-host are untested, not recommended substitutions.
- Personal hockey experiment: **stats.swehockey.se**. TV assignments: **www.tvmatchen.nu**. Permission/licence blocker remains even if hosts allowed.
- Commercial review: **developer.sportradar.com**, **coverage-matrix.sportradar.com**, data **api.sportradar.com** (data untested); **api-sports.io**, **www.goalserve.com** (documentation availability blockers above). Do not guess/allow alternate API hosts until contractual docs identify them.
- IPTV/XMLTV/artwork provider domains are **unknown and profile-specific**. Configure later on trusted server after owner authorizes the source; no wildcard proxy/domain allowance. Do not paste credentials/provider URLs into chat, browser JS or diagnostics.

## Screen strategy

| Screen | Recommended data strategy |
|---|---|
| Hem | Existing profile/channel/resume readers + authorized current/next EPG; match hero only from fixtures, separate verified assignment; Twitch via server-only Helix. MOCK remains full approved visual scenario. LIVE currently limited public fixture preview, with explicit missing dependencies. |
| TV | Existing logical Swedish channels + provider/XMLTV guide/logo owners. Exact current programme and next, bounded reads, text fallback. No generic public website replacement for private channel library. |
| Sport | Existing SportsRepository fixtures/table + independent BroadcastMetadataSource/EPG validation; profile favourite teams/FixtureChannelChoice preserved in Android. Manual choice must never claim verification. Public SHL sample proves shape only; HA/full competition/live-score coverage remains unresolved. |
| Film & serier | Provider catalogue + existing TMDB contracts/cache/budget; authorized metadata and licensed artwork. Personal history stays profile-local. |
| Profile | Existing SportsPreferences bound to profile ID + creation time, fixture choices bound to profile/source/channel. Lab never reads/writes Android preferences. No synthetic profile presented as live account data. |

## Prototype / validation / limitations

Data selector adds MOCK / LIVE / UNAVAILABLE and Källstatus outside the TV design. MOCK uses original deterministic UI unchanged. LIVE fetches same-origin loopback adapter, includes source/fetch time/freshness and never silently uses mock content. Missing screen sources show explicit unavailable state. UNAVAILABLE tests missing-data behaviour. Live manual action reports absent private channel library rather than offering guessed demo channels. Full manual selector remains testable in MOCK. No credentials input, arbitrary URL proxy, external images, stream playback or periodic polling.

Only the public SHL fixture adapter is enabled. It does not implement HA, live scores, verified current broadcast assignments, IPTV, TMDB or Twitch authentication. Product decisions: timezone contract; licensed complete sports feed and broadcaster coverage; allowed image provenance; secure profile-scoped local bridge. These are blockers, not successes inferred from documentation.

Focused parser/failure/cache tests:3 passed. Browser modes and source status verified separately. No Android build/CI required or run for isolated lab files.

## Iteration 2 recheck
The existing public SHL adapter was exercised again through the browser: Frölunda HC–Rögle BK,2026-10-27,source time18:00,status NS; fetched2026-10-24T18:30:34Z. Partial fixture only; no verified broadcaster or timezone inference. No additional credentials or source permissions acquired. Developer panel now lists exact host/auth/permission blockers. Original source probes remain timestamped historical evidence, not continuously verified status.

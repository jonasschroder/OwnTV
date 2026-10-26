# v0.11.1 implementation evidence

Native Kotlin/Compose changes on `feat/v0111-navigation-hockey`, PR #23. No Core, schema, package, signing, release workflow or installed app changes.

## Implemented

- **TV:** no shell TV tabs or internal action rail. Three curated collections; news/children remain in Other and the original full library. Guide/Search/All/customization use one global utility surface. TV enters the grid; Guide retains its own state and existing preferences. Short/held OK and existing source picker/player are retained.
- **Channels:** exact TV Hockey aliases and TV4 Sport Live 1–999, numerical order before other TV4 sport/Eurosport. Quality duplicates resolve through existing profile preferences. Strict provider-labelled SVT1/2 Värmland services are preferred only for a single provider; saved national choices win. Regional source identities are never merged into national event evidence. No national channel is relabelled.
- **Other channels:** current/next/missing EPG buckets use only previously read visible-card summaries (bounded 32). Order freezes while grid owns focus. Unread channels remain unknown; this deliberately does not import the whole guide to sort.
- **Home:** approved composition retained; package-scoped SVT Play/Telenor shortcuts under Twitch, installed logos where available, Store fallback/error without automatic installation. Down from Twitch reaches shortcuts; Up returns. Version moves to global utilities.
- **EPG:** explicit manual refresh through existing EpgViewModel/EpgRepository, never on focus; process-wide VM click guard, 60-second elapsed-time throttle, 120-second bound, per-source ownership checks, result and session last-success time. Refuses concurrent known background EPG activity; no new importer, provider, polling, destructive reset or raw exception display. Standalone EPG sources retain their existing global ownership model.
- **Sport:** hero opens actual match details (or verified direct playback), no generic browse/settings CTA. Actual logical hockey/Sport Live channels only. Upcoming cards also show existing exact local-EPG/broadcast evidence; details distinguish absent EPG, disabled broadcast metadata, absent channels, manual assignment and checked evidence. Existing parser's team/competition/time/replay/ambiguity safeguards retained.
- **Profile:** top destination opens Settings directly. One category rail controls existing settings rows; original ordinals/deep links/preferences retained, sport category appended. Back from rows to category then header. Legacy favourites/history/backup/sync/error/About remain reachable through Library/history; downloads has a direct utility. Existing advanced groups retained rather than silently removed.
- **Release copy:** `qa-1000014` description corrected using prepared Swedish notes; tag/pre-release status/assets left in place. Future release notes are an explicit authoring responsibility in AGENTS.md. No new release created.

## Tests / evidence

Targeted JVM tests and QA instrumentation compilation are run locally, with logs outside the repository (`../v0111-tests-accepted.log`). Tests cover >10 numbered services, false aliases, regional absent/present/ambiguous/saved preference, current/next/stale sorting and frozen order, manual-refresh click guard, existing strict EPG fixture evidence, manual-vs-confirmed hero, profile route and sparse D-pad rows.

Production instrumentation now checks absence of deleted tabs, direct Guide access, remote access to global tools, both app shortcuts with real D-pad keys, direct Settings and retained legacy library pages. Existing native tests capture synthetic-data 1080p/720p Home/TV/Sport/VOD in CI. Screenshots are emulator evidence, never physical Chromecast evidence. Required CI status and artifact links are recorded in PR #23; pending jobs are not passes.

## Permitted source review — 26 October 2026

Bounded read-only checks of public pages (no undocumented API probing):

| Source | Observed | Integration decision |
|---|---|---|
| [TV4 SHL](https://www.tv4.se/shl), `www.tv4.se` | Page reachable; confirms streaming on TV4 Play. No per-fixture linear channel field in retrieved content. | Platform is not a linear channel. No new adapter. |
| [SHL live](https://www.shl.se/live), `www.shl.se` | Page reachable; links existing live game centres and TV4 Play. No exact linear broadcaster assignment in retrieved content. | Retain existing bounded schedule/result adapters. |
| [HA TV4 Play](https://hockeyallsvenskan.se/pages/tv4play), `hockeyallsvenskan.se` | Page reachable; streaming-platform rights/promotion, no linear fixture mapping. | No guessed channel mapping. |
| Owner-authorized IPTV EPG | Existing local importer and exact fixture parser reused; private feeds not accessed in this task. | Primary available per-profile channel evidence. |
| TVmatchen | Existing opt-in, robots/terms hash, failure budget and BroadcastResolver inspected; unchanged. | Do not bypass gates or treat past failure as proof no data exists. |

No public documented/licensed exact-linear assignment API was established by these checks. TV4/league/distributor feed permission, contract/schema and credentials (if required) remain prerequisites for a new integration. Rates/caching/redistribution rights are **not established** for such a feed. No blocked endpoint was bypassed and no IPTV URL/token entered browser code or reports.

## Remaining acceptance / limits

- Full existing CI must pass. New native screenshots must be inspected from final-head CI artifacts before visual sign-off.
- No owner-device installation, hardware latency/playback/external-app return test or physical remote long-press test performed here. Local APKs are development-only, not an approved QA update.
- Cached EPG sorting cannot rank unseen channels as though they were verified; last manual refresh time currently describes this process/session.
- Global tools: Right from the Profile top item enters the utility row; Up returns to the active top destination; Down restores content. Verify discoverability on Chromecast.
- Settings retains the full existing group taxonomy and legacy library/history hub; a further reduction to seven groups is a product follow-up, not a data migration.
- Rollback is a branch revert, with no DB migration or preference rewrite. Official QA requires owner-approved merge, green main CI, protected permanent QA signing and separate distribution/install approval.

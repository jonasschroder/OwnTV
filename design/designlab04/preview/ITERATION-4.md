# Design Lab 04 — owner review

Preview: http://127.0.0.1:57820/ . Existing workspace and visual design retained. Android source selection was read only as reference (ChannelSourcePicker/Selection: profile ownership, actual variants, remembered selection). No Android/Core/Git/release changes.

## Final interactions

- TV card short OK plays the fullscreen mock player immediately. Held OK (550ms in this web simulation) opens only available mock alternatives, preselecting the remembered variant; its opening release cannot start playback. A singleton source gives a clear notice instead of an invented picker. Back restores exact channel/focus/scroll. Programme details do not intercept TV playback.
- New movie/series OK opens details. Continue Watching OK plays immediately. Existing finite Back hierarchy remains.
- Default Jonas profile uses Färjestad. Home policy: live favourite match → favourite match later today → playable favourite channel with fresh/relevant programme metadata → eligible unfinished mock VOD item → recent available TV channel → setup. A match without verified broadcaster offers hockey browsing, never an invented Watch Now. An empty library offers no channel choice. Hero choice is latched during ordinary Home navigation and refreshed on re-entry or explicit scenario/profile change; recording a manual choice updates its label without replacing the hero.
- Sport uses configured mock provider names. Controlled matching accepts TV4 Hockey/TV Hockey, optional numbered Live and reviewed SE/HD/FHD/UHD/4K suffixes; rejects unrelated Highlights/prefix matches. Main sorts first, numbered Live numerically, including10. Full/partial/empty/extended libraries are shared with TV. Schedule, status/result, external broadcaster and playable channel remain independent.
- Manual match choices are profile/fixture scoped, persist, and remain labelled unverified. Fixture IDs are deterministic mock fixtures, not real-world schedule identities.

## Persistence / scenarios

Only `mintv-designlab04` localStorage is read/written: two fixed mock profiles, bounded favourite team/channel IDs, actual selected mock quality, unfinished mock progress records, last channel/focus/scroll and manual fixture choices. No provider URLs, IPTV credentials, tokens or arbitrary external strings are stored. Reset Demo Data resets this namespace only. Other scenario switches are session-local.

Scenarier covers live verified/unknown, later today, no favourite match, unavailable sports source, Twitch live/offline/auth/API failure, fresh/missing/stale EPG, missing branding/artwork, no IPTV channels, empty/populated progress and missing VOD artwork. All fake verification is explicitly MOCK; external source readiness is unchanged.

## Validation

Existing CUA test modules updated, not replaced with a new framework:
-30real-keyboard assertions passed at1920×1080 and1280×720 (direct playback, held OK, Back,20-channel traversal, Movies/Series, visible focus, screen history).
-23deterministic scenario assertions passed.
-23persistence/product assertions passed, including actual page reload, profile isolation, selected quality, progress/favourites, remembered manual choice, numeric Live10 ordering, stable Home hero and reset.
-3existing public-adapter unit tests passed. No Android build/emulator/CI run.

Evidence: iteration4-*-results.json and screenshots/iteration4-{home,tv,sport,film-series}.jpg. Browser screenshots are not Chromecast/native evidence.

## Remaining review and implementation readiness

Core interaction decisions are implemented for owner review. Remaining native tuning: use Android's existing long-press timing and source/variant owner rather than copying the web threshold; reuse the app's real resume eligibility, profile persistence and EPG validity contracts. Review source-picker dismissal/selection feel with the physical remote and asynchronous native list/profile changes before acceptance.

The prototype is ready for UX review and implementation planning, not a production data integration or release. Required real integrations remain: authorized profile-owned channel/EPG bridge, licensed programme images/logos; complete SHL/HA schedule/live status; current exact broadcaster assignments; authorized Twitch status; authorized TMDB/provider metadata. Public TheSportsDB remains partial fixtures only. No new external service was connected this iteration; previous dated evidence remains in SOURCE-READINESS.md. No guessing or restrictions bypassed.

Changed local files: index.html,iteration4.js,serve.py (one fixed local asset route),keyboard-tests.mjs,scenario-tests.mjs,README.md,this report and local evidence/screenshots. Server remains loopback-only on the same port.

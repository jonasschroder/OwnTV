# Min TV v0.11 — Native UX implementation

Status: **owner approved DESIGNLABB 04 on 2026-10-25**. This branch starts at reviewed v0.10 main commit `10f02c658640457de86c926ebd95064b7c8b673d`, without touching Stable/QA or release/signing.

## Current development checkpoint (2026-10-25)

- Native Home, TV grid, match-first Sport and combined Film/Series landing are implemented on existing draft PR #22, reusing the v0.10 data/player architecture.
- Local validation: 77 targeted JVM tests and 20 Android TV emulator tests passed; eight native 1080p/720p captures reviewed. Full CI status is authoritative in the PR checks.
- [Implementation comparison, captures, data gaps and physical acceptance](native-validation.md).
- Physical Chromecast acceptance and separately approved QA signing/install remain outstanding. No production data, package/signing identity or Core pin changed.

## Approved visual reference

- [FROZEN Design Lab 04](https://github.com/jonasschroder/OwnTV/tree/design/v011-designlab-04/design/designlab04/preview) (commit `a7f5f7c01d6d24c4d56f38854736355a973282b2`).
- Compare `screenshots/iteration4-home.jpg`, `iteration4-tv.jpg`, `iteration4-sport.jpg`, `iteration4-film-series.jpg` at **1920 × 1080** against real native captures. Use real Android tests at **1280 × 720** as well. `05-home-1280x720.jpg` is **stale Design Lab 01/Brynäs**; do not consider that an iteration 4 baseline.
- Design lab's simulated IPTV, EPG, SHL games, Twitch data, films and images are fixtures; **never ship them as production data**. Design approval does not certify their factual correctness.

## Implementation order / acceptance (do not build a new HTML demo)

### 1 — Native Home first
- [ ] Replace the v0.10 eight-channel/302-unit channel/series hero composition with the approved v0.11 layout **using native Compose**, not a WebView.
- [ ] On verified favorite-team matchday, prioritize the real favorite fixture (Färjestad is existing default via `SportsPreferences`); if live, live first; otherwise later today. Present exact known opponent/faceoff/status; omit invented broadcaster, live score or stream. CTA goes to verified match details or browses actual hockey channels, never an unverified auto-play.
- [ ] Right-side compact Twitch card with honest signed-out/offline/unavailable state (no oversized empty space). Reuse `TwitchStatus` and its existing auth/launcher.
- [ ] First row: **five** logical available Swedish TV channels with real cached EPG/logo, text fallback, natural horizontal scrolling, existing M3 short OK / long OK and preferred quality. Profile-specific channel order/hidden state is authoritative.
- [ ] Continue Watching: up to three exact eligible progress entries, single-tap direct resume, revalidation, no fuzzy match.
- [ ] No match today: context-sensitive recent-live/current EPG, then eligible VOD, then setup; no invented team/match and no empty unclickable hero. Freeze visible hero/order for each Home visit; refresh labels only, restore stable-key focus on Back and profile switching.
- [ ] Preserve `MinTvContentHome.kt`, `SmartHomeContent.kt`/policy/readers, `MinTvCompanions.kt`/SportsRepository, and existing source/player callbacks wherever possible. New adapters should be bounded and profile-owner checked, no extra decoder or stream on focus.

### 2 — Native TV
- [ ] Five-column browsable logical channel grid (Designlab04 screenshot). Real `SwedishChannelStore` and `SwedishRowEpgStore`; no repeated HD/SD/4K cards.
- [ ] Short OK plays immediately; long OK calls existing M3 source picker, not a second picker. Back returns to exact card and scroll. Preserve exact raw provider, EPG, catch-up, recording and parent/hidden-source semantics.
- [ ] Cases: 0, 1, 4, 5, 6, 20+ channels; missing/stale EPG; missing logos; profile/source change while focused; 720p legibility.

### 3 — Native Sport
- [ ] Favorite-match hero, Today, Upcoming and numeric TV4 Hockey/TV Hockey Live channel list; adapt `NativeSportScreen` and current sport repositories.
- [ ] Never infer a broadcaster from an approximate channel name. Keep manually chosen candidate marked **unverified**; only exact evidence supports Watch Now.
- [ ] Do not reintroduce the unreliable standings table or claim real-time scores without a proven source. Preserve hockey, Färjestad preferences, existing SHL/HA sources and network limits.

### 4 — Native Film & series
- [ ] Two clear Film/Series entry routes and horizontally browsable actual catalogue; new-title OK goes to details, Continue Watching OK resumes **exact** item.
- [ ] Use provider/TMDB metadata through authorized existing contracts with cache/budget and correct attribution; never synthetic programme posters. Retain profile canonical Library, Favorites, History, Downloads, existing DRM/player and parental visibility.

### 5 — Physical QA + controlled release preparation
- [ ] Run targeted tests during work and mandatory CI (Android, native D-pad/database, startup, upgrade/signing/distribution and i18n) before merge.
- [ ] Native screenshots and focus traces for 1920×1080 and 1280×720, compare with Designlab04 and explicitly mark deviations.
- [ ] Test actual Chromecast remote, playback, asynchronous metadata, source picker and Swedish subtitle episode handover before calling hardware accepted.
- [ ] Sign/publish/install **QA only** after a separate explicit user approval; never automatically merge or change Stable.

## Keep data integration as a separate workstream

Improved complete SHL/HA fixture/results, verified broadcast assignments, program art/logos, provider channel association, authenticated Twitch status and TMDB image access must not gate the v0.11 layout release. Reuse current authorized owners; visible unavailable/text states are valid. Never scrape forbidden TVmatchen endpoints, expose IPTV credentials or add speculative provider tokens.

## Integration PR

Continue draft PR #22 on `feat/v011-native-ux`. Native code, policy/focus tests and emulator captures are integrated; review the linked validation report and complete required CI before any readiness decision. Physical acceptance and owner-approved QA distribution remain separate gates.

## Safety

- Never change `se.jonasschroder.mintv` or `se.jonasschroder.mintv.qa` package IDs, signing keys, channel certificates, update workflows, Core pin, DB storage formats, or existing device data.
- Keep product copy in Swedish by default; maintain resource localization and accessibility.
- No silent merge, signing, publication or device installation.

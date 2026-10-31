# Min TV v0.16 — Major Experience Release
**Status:** planning specification / Codex execution brief, 29 October 2026. **Source:** `main` at `88a51db17493869805db7763a5e69c22a6299dd1`. **Scope:** one cohesive major QA release after owner review, not a string of micro-releases. **Do not merge, sign, publish, install, or touch Stable as part of preparation.**

## Product decision
Make Min TV feel like a complete, fast, calm, coherent Google TV app used every day. The owner wants a **substantial update covering navigation, performance, UI quality, everyday capabilities, and simpler in-app updates**. **Updated 30 October after physical Chromecast QA:** published QA v0.15.0-beta.1 (1000021) successfully launched TV4 Sport Live 3 from Sport's **Se matchen** action, with picture and sound confirmed by the owner. Sanitized diagnostics recorded SPORT → SOURCE_VALIDATED (+20 ms) → ACCEPTED (+84 ms) → OPENED_EXO (+1,398 ms), without player fallback. This establishes that *this exact channel route and local IPTV playback worked*, but does not confirm that the 31 October Växjö–Färjestad match will actually be shown on that station: the arena-based broadcaster recommendation remains **probable** until matchday/independent evidence. The confirmed remaining device defect is lost Sport focus after Back; draft PR #35 addresses this but is not installed. Do not describe all Färjestad playback as broken or all broadcast assignments as verified.

Keep the approved fundamental 5-destination navigation (Hem, TV, Sport, Film & serier, Profil/inställningar) and the core Home composition unless visual review proves a specific improvement; do not add tiers of menus or an alternative launcher. Preserve Film & serier's currently accepted core behavior. Swedish default; remote-first, readable at 2–3 metres, 1080p/720p, restrained dark aesthetic, stable focus.

This is a *product/UX release*. Avoid gratuitous rewrites, new external subscriptions, paywalls, unauthorized scraping, fake EPG matches or features that cannot be maintained.

## Workstream A — Navigation, interaction and visual consistency (P0)
Audit the current top navigation, utility bar, Home rows, TV five-column grid, Sport fixture screen, profile/settings, source picker and playback Back handling **as one journey**. Current navigation primitives include `NativeTopNavigation.kt`, `NativeNavigation.kt`, `NativeRowsNavigation.kt`, `HomeDpadNavigation.kt`, `OwnTVShell.kt` and contextual focus owners. This is an audit target, not an assumption every implementation is broken.

- One-level global navigation. Make programme guide, search, sources and update discoverable but do not add redundant nested top bars.
- Define explicit remote behaviour for D-pad, OK, long-OK, Back and channel number shortcuts. No double-OK needed for ordinary actions; no accidental screen activation on focus.
- Back should close search → picker → detail → destination → previous selected top item as appropriate. When leaving playback or another Android app, restore the exact logical item, row/scroll and profile (or nearest valid fallback if removed).
- Eliminate focus jumps, disappearing focus indicators, horizontal/vertical scroll leaks, double navigation and focus traps. Be robust when guide, channels, EPG and recommended programmes update while focus is elsewhere.
- Consolidate TV card styling, padding, focus halo, typography, empty/loading/error states and action labels across Home/TV/Sport/Profile. No gratuitous animation or huge blank hero cards; emphasize content and primary action. Respect reduced motion and TV safe areas.
- Review Profile & settings as one coherent screen with a clear left category list and right detail surface, not multiple stacked menu systems; preserve every existing setting, source, backup/restore and deep link.
- Keep existing easy access to SVT Play/Telenor Stream, OhnePixel, SmartTube, channel quality/source choice, EPG update, version, search and currently working player features. Do not hardcode unavailable app shortcuts.
- **Deliver** an explicit focus/Back/navigation contract, reviewable before/after actual native screenshots for 1920×1080 and 1280×720, and D-pad journey tests.

## Workstream B — Performance, reliability and load management (P0)
Use `docs/performance/v012/README.md` and `tools/performance/run-journey.sh` as methodology, NOT as fresh device evidence. Current heavy areas include stateful `MinTvCompanions.kt`, Home and Sport subscriptions, TV library/guide queries, and preview/player transitions.

- Benchmark the **same** seed/profile/dataset before and after at 1080p and 720p: cold launch→useful Home, warm resume, switching Home↔TV↔Sport↔VOD↔Settings, 20+ channel grid, D-pad key response, open/back from fullscreen playback, EPG refresh and Sport data loading.
- Record median/p95, frames/jank sample, sampled PSS, CPU, extra requests, actual channel starts and double decoder/session creation. Distinguish emulator/synthetic measurements from real Chromecast measurements.
- Fix identifiable repeated work, excessive recomposition, lazy-row key churn, redundant database/EPG reads, unnecessary external network requests, excess artwork work and lifecycle leaks. No background fetch when invisible and no parallel playback on focus.
- Propose measurable relative goals (e.g., 15–25% p95 improvement for genuinely slow journeys) rather than inventing absolute hardware claims. Do not weaken a green CI threshold, introduce flaky timing assertions, or sacrifice reliable playback for speed.
- Confirm no profile/playlist/favourites/history/EPG/subtitle loss after process recreation, navigation or compatible updates. Protect accessibility and focus during asynchronous data refresh.

## Workstream C — Sport / Färjestad: make recommendations actually useful (P0)
**Physical-device evidence is now mixed, not uniformly failing:** the owner confirmed that *Se matchen* successfully played TV4 Sport Live 3 with audio/video on existing QA 1000021 (30 October). The **broadcast assignment for the actual 31 October SHL fixture is still probable** and must be checked against the actual match. Remaining focus-after-Back defect is implemented but uninstalled in draft #35. Continue investigating other upcoming fixtures or repeatable channel-mapping/stream failures, not a blanket assumption that playback never works.

Instrument a bounded privacy-safe stage ladder, visible via optional diagnostics (without secrets or raw IPTV URLs):
  1. Official SHL fixture exists and has correct team/time/arena.
  2. TV-Programidag free permitted source yields probable linear channel, with timestamp/expiry.
  3. Suggested channel's **exact logical identity** matches the current profile's authorized IPTV library; show count of eligible variants and safe display labels.
  4. Saved manual/provider/HD-FHD preference and local EPG interaction are resolved; note if local EPG overrides a probable external station.
  5. Match card shows **Trolig kanal** or **Bekräftad kanal** plus a truthful action. Distinguish recommended-but-no-playable-source from playable.
  6. **Se matchen** actually dispatches the selected local channel through the existing entry gate, and playback opens with picture and sound.

- Preserve the real observed Brynäs–Färjestad (29 Oct) candidate as a past fixture, and Växjö–Färjestad (31 Oct) as an **upcoming** fixture as of 30 October. Venue-based TV4 Sport Live 3 metadata is a **probable** broadcast assignment, not a hardcoded production mapping; physical picture/sound on the service before kickoff is not proof it carries the match. Use fresh upcoming fixtures for extended QA.
- Investigate cases where a correct channel name appears but Watch is disabled/missing, the IPTV name uses a distinct numbering/prefix/quality variant, the profile owns a different provider, channel is unavailable, source picker selection is stale, or the actual player fails. Only improve aliases when supported by observed evidence; no fuzzy assignment to an unrelated station.
- If only the upstream IPTV stream fails, report this plainly; the TV schedule API supplies **metadata, never a playback URL**.
- Provide useful states: Trolig kanal, Bekräftad kanal, Kanal saknas i din IPTV-lista, Flera möjliga kanaler, Uppgift saknas / behöver uppdateras, plus **Välj kanal** manual fallback. Never claim an accuracy percentage without independent ground truth.
- Ensure next Färjestad game is clearly visible in Sport and contextually on Home, and never display stale matchday information as live. Keep existing EPG, in-play switch, fixture/team selection and existing no-standings policy.
- Free-only: SHL permitted fixture source + TV-Programidag documented free API, documented attribution/rate limits and caching. No paid service, subscription, card, scraping bypass or hosted server requirement.
- The regional SVT1 Värmland provider-stream failure remains a distinct diagnosis. Preserve PR #29 national/regional separation; don't silently play national SVT1 under the Värmland label.

## Workstream D — Easier, safer app updates (P0)
The app **already has** a verified in-app update manager and ordinary Android PackageInstaller: `features/update/MinTvUpdater.kt`, `UpdateDialog.kt`, `UpdateForegroundHost.kt`, `UpdatePolicy.kt`, `UpdateTransport.kt`. Protected manual signing / distribution exist and have produced QA releases. Improve usability rather than rebuild security from scratch.

Target user journey:
  1. Version and channel Test/Stable are easy to find from any destination and Settings → Om Min TV; an unobtrusive update-available indicator when authenticated release is newer.
  2. A single **Uppdatera** route shows current/new version, brief Swedish what's new, download size, network/space state, and **Uppdatera nu** / **Senare**.
  3. One clear download progress and verify state (no duplicate prompts, invisible stalls or lost focus). Handle interrupted download/re-entry, cached verified APK, storage/rate limit/offline errors and installer permission gracefully; avoid claiming auto-resume unless implemented and validated.
  4. Hand off to **Android's required installation confirmation** with a clear explanation; no silent background install, auto-granted unknown-source permission or certificate bypass.
  5. On successful restart, preserve source/profile/favourites/settings, show a modest version-updated confirmation and restore a sensible Home/destination. Do not promise perfect return across process destruction unless tested.
  6. Optional automatic **check** on startup/foreground, never automatic install. Manual check remains reachable and throttled. Dismiss/Senare remains respected.

Protect unchanged package IDs `se.jonasschroder.mintv` and `se.jonasschroder.mintv.qa`, signer pins, increasing `versionCode`, authenticated signed metadata, SHA256/ABI checks, exact QA/Stable separation, `PackageInstaller` user authorization, private install callback, update rate/cache protections and protected release approvals. Preserve working GitHub release distribution and its storage-retention safeguards; no new signing key or Core-pinning change.

Provide UI screenshots/tests for checking, available, downloading, interrupted, verifying, permissions, system confirmation, failure, installed, restart. Include long notes, 720p remote focus and history/profile preservation.

## Workstream E — Useful day-to-day polish (P1, within the same major release)
- Home: clearer last watched / På TV just nu / favourite match hierarchy, better missing/stale guide states, compact OhnePixel/Twitch/shortcut presentation, **Uppdatera EPG** prominent but unobtrusive. Keep approved content order unless owner signs off a documented improvement.
- TV: predictable five-column grid and logical sport ordering; preserve source selection and do not show SD/HD/FHD duplicates as separate channels without reason. Quality choices easy to inspect; avoid black-screen dead ends and status that falsely implies stream health.
- Sport: compact high-value fixture cards, opponent/time/channel hierarchy, no clutter of duplicated controls or obsolete standings.
- Profile: clear current profile, channels/sources, EPG, subtitles, update and diagnostics sections. Reduce cognitive overhead without hiding capabilities.
- Film & serier: maintain currently accepted behaviour; only targeted visual harmony, navigation and performance fixes.

## Delivery strategy: a *single reviewed major release*, safe implementation slices
**Phase 0 – Baseline and design decision (first, no release):** reuse existing draft #33's real native 1080p+720p captures, focus map, navigation measurement (including honestly reported rejected slow-path experiment) and new privacy-safe Chromecast playback evidence in draft #35; avoid duplicating work. Capture any still-missing baseline or physical regression evidence. Propose one cohesive v0.16 visual system with detailed native screen specifications (Home, TV, Sport, Profile/Settings and Update); owner reviews reference before major UI replacement.

**Phase 1 – Architecture/focus + updates:** review and integrate existing draft #33 (remote logical focus), #34 (in-app updates), and #35 (Sport Back focus) rather than recreating them. Verify final combined behavior with tests and before/after captures.

**Phase 2 – Playback/Sport resolution + Home/TV cohesion:** build on draft #35's actual Sport focus-after-Back fix and confirmed TV4 Sport Live 3 playback. Validate real fixture-to-broadcast accuracy on/near matchday, diagnose remaining failures by stage, and improve screens. Distinguish confirmed device findings from inferred hypotheses.

**Phase 3 – Performance + integration:** performance PR and integration branch / draft PR bringing all accepted pieces together, full CI on integrated candidate, conflict review, 1080p/720p screenshots and regression matrix, release notes. Avoid repeated full CI on throwaway drafts and do not fill GitHub Actions storage with redundant artifacts.

**Phase 4 – Owner-approved QA only:** after design/code review, green exact-commit main CI and user authorization, sign and publish **one** `0.16.0-beta.1` or suitably bumped QA candidate using existing protected workflows. Test on actual Chromecast with legal IPTV source; only then discuss Stable. No automatic merge, sign, publication or installation.

## Definition of done
- Real native Compose Android implementation, not a mock-only / HTML-only redesign.
- Unified remote/focus/Back rules tested on actual destinations, interruptions and two profiles.
- Significant, reproducible improvement for key slow journeys, with before/after evidence and no player regression.
- Färjestad channel candidates distinguish venue-based probability, local source resolution, and actual playback. The 30 Oct Chromecast TV4 Sport Live 3 playback is confirmed; the 31 Oct match-to-channel assignment and draft #35's focus recovery still require validation.
- Update discovery → review → download → verify → Android confirm → app restore works with QA signing and preserves app state; failures and Later are sensible.
- Native screenshots 1080p and 720p; long Swedish titles, empty/stale data and 10-foot text readability.
- Existing full Android/Core/i18n/performance/startup/upgrade/signing/distribution tests preserved. No secrets in logs, screenshots or Git; no new paid dependencies.
- Document every remaining external-provider limitation and author Swedish release notes from real changes.
- Main and Stable unaffected throughout preparation. Release only after explicit owner approval.

## Codex kickoff instruction
Treat this as the approved **planning direction**, not authorization to merge or ship. Inspect current app and compare with the cited baseline first. Start with a concise architecture/design+measurement deliverable and a realistic sequencing plan. Then implement in reviewable native slices on separate branches, with focused tests and proper PR descriptions. Act on the known reported defect; don't make theoretical diagrams or additional research the sole deliverable. Do not request payment, app secrets or indiscriminate log dumps. Never claim hardware playback success without direct evidence.

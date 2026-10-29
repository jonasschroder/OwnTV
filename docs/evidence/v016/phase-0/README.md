# v0.16 Phase 0 and shared-focus workstream

Planning authority: [PR #32](https://github.com/jonasschroder/OwnTV/pull/32), not a replacement specification.
Base: `88a51db17493869805db7763a5e69c22a6299dd1`. Its main prototype and i18n runs passed. Current published QA is `0.15.0-beta.1` / `1000021`; these fixtures deliberately use the same local `0.14.0-dev` / `2` stamp on both builds. They are not release artifacts.

## Reproducible native evidence

`baseline/<resolution>/native/` contains the actual MainActivity Home, TV, Sport, VOD and Profile screens at 1920×1080 and 1280×720. No HTML/prototype captures. Two rendered frames precede each capture: semantics readiness alone can show the previous surface. `provenance.json` records APK hashes, exact source/Core, environment and fixture ownership. Raw measurements are retained under `baseline/` and `focus-candidate/`; `comparison.json` is derived from them.

Disposable Android TV API 34 ARM64 emulator, software GPU, 4 cores/2 GiB. Same synthetic 12,000-channel profile/library, no credentials, real-provider playback, preview decoder or EPG network seed. This tests a large underlying library; the curated TV grid is smaller than 20 logical cards. Sport screenshots truthfully show hockey data disabled, VOD has no movie/episode history. They do not demonstrate populated live-provider startup or Färjestad playback.

Reproduce on a disposable emulator only:

1. Build QA debug + QA androidTest using the same test version stamp.
2. Run `tools/performance/run-journey.sh <output>` with explicit `ANDROID_SERIAL`; it requires qemu and seeds only its synthetic fixture profile.
3. Run `PerformanceJourneyTest#captureNativeProductionScreens` with `performanceProbe=true` after the seed. Pull `files/performance/native/`.
4. Summarize the two JSON files with `tools/performance/summarize.py`.

The host ADB installation and SDK are not runtime app dependencies. No owner-device installation or data change was performed. No new CI skips, thresholds, artifact-retention changes or release dispatches.

## Navigation audit and reusable ownership

| Surface | Existing behavior / owner to retain | Finding and workstream |
|---|---|---|
| Global | Native top destinations + compact existing header actions; Guide/Search/EPG actions | Keep five destinations and maximum one contextual selector. Current static version has no global update route; the separate navigation/update workstream makes it actionable in the same row. |
| Home | Smart Home data owners, profile-specific last channel/favorites/resume, existing Twitch/launchers | Preserve approved core composition and real bounded readers. Missing-data fixtures expose large decoration/spacing; review native references before a major visual replacement. |
| TV | SwedishLiveScreen, SwedishChannelStore, ChannelSourceResolver, five-column grid | One category selector already exists. Preserve short OK, long OK variants, source/quality ownership, saved choices and EPG. Empty/artwork fallbacks are honest. Further test a populated >20-card grid and delayed EPG. |
| Sport / VOD | Shared NativeRowsNavigation with logical keys, per-profile saved selection and lazy rails | Every movement previously scheduled two frames, including attached visible targets. Add immediate focus only for fully visible attached row/card; retain scrolling/deferred fallback, removed-item/header recovery and profile boundary. |
| Film & serier | Existing single Filmer/Serier submenu, content/detail/resume/player | Keep the existing content contracts. No new persistent navigation layer; both entries remain reachable. |
| Settings | Existing left category selector and right category content | Already two-pane. Reuse rather than replace; updater uses the same host adapter, not Core's disabled legacy updater. |
| Playback / data | Existing source resolver, LIVE_ENTRY diagnostics, EPG readers, Core player, settings/profile persistence | No playback/source schema/migration/Core pin change in this workstream. Owner-device Färjestad fault remains unverified. |

Native contract: Left/Right moves within a row without destination activation; OK activates; Down enters the focused destination through its existing entry callback; Up returns to the active global destination; Back unwinds the existing finite hierarchy and restores logical focus. Utilities remain in the existing top row. Scroll/async targets use the old safe deferred path. Available target removal must choose a valid same-screen fallback or the header. Profile changes must never restore another profile's selection.

## Matched before/after results

Two processes per build/resolution, two warmup cycles excluded, 20 measured cycles/process (40 destination samples, 40 remote pairs; 20 background/resume samples). Median / p95 below, milliseconds. These include instrumentation scheduling and Compose idling, **not physical remote-to-photon latency or release TTID**.

| Resolution / metric | Baseline | Focus candidate |
|---|---:|---:|
| 1080p TV entry | 1433.7 / 1732.8 | 1300.1 / 1484.8 |
| 1080p Home entry | 1515.9 / 1749.4 | 1364.7 / 1599.6 |
| 1080p top Right/Left | 133.6 / 150.0 | 133.4 / 135.0 |
| 1080p VOD Down/Up | 151.7 / 167.0 | **182.8 / 216.0** |
| 720p TV entry | 1416.9 / 1767.0 | 1283.1 / 1483.6 |
| 720p Home entry | 1499.6 / 1799.7 | 1366.4 / 1514.7 |
| 720p top Right/Left | 131.3 / 135.9 | 116.9 / 134.5 |
| 720p VOD Down/Up | 150.1 / 182.6 | 149.7 / 166.3 |

CPU process time for the complete scripted journey averaged 120,793 → 108,742 ms at 1080p (−10.0%) and 121,351 → 107,035 ms at 720p (−11.8%). Maximum **sampled** PSS was 286,846 → 291,836 KiB and 290,997 → 294,773 KiB respectively; this is not peak memory and no memory reduction is claimed. All FrameMetrics callback-loss counts were zero. Software-rendered frame durations and UID byte counts remain in raw evidence; do not interpret them as hardware FPS or provider request counts.

The visible-target optimization also passes the native rapid two-Right/one-Left focus regression plus existing removed-item/profile/row restoration checks. However, the VOD 1080p pair regressed in this small benchmark and does not isolate the fast path from scrolling/render/idling. The CPU/navigation changes are preliminary observations, not a proven app-wide causal improvement. The specification's 15–25% performance goal is **not met/claimed**. Retain this evidence and investigate the 1080p path before a release recommendation; no repeated broad suites merely to obtain a better number.

## Validation and remaining evidence

Targeted `NativeRowsNavigationTest` JVM tests, QA debug/test compilation and native `sportEntryRowNavigationAndRemovedFixtureRestoreSafeFocus` passed. The extended native method is already selected by mandatory CI; no workflow modification. Complete PR CI must pass independently.

Open: real release cold TTID/first frame, actual channel zapping, external-app return, provider request/decoder-start counters, process-death scroll restoration, long sessions/peak PSS and the owner's Färjestad stage ladder through picture+sound. ADB discovered no Chromecast during this baseline; emulator results cannot close those device checks. The separate update UI candidate is not included in these performance measurements.

Rollback: revert the single visible-target production hunk; the original asynchronous path remains intact. Evidence/tests can remain. Integrate only after owner review and required CI, then measure the combined candidate before any protected QA proposal.

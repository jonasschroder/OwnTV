# Native remote/navigation performance review

This extends draft PR #39; it preserves its TV/Sport/VOD visible-focus fast paths. No redesign,
playback-engine changes, source prefetch, database schema, dependency, package, signing or workflow changes.

## Bottlenecks and changes

- Home and TV each called `SwedishChannelStore.library` on re-entry. The bounded scanner still visited
  12,000 provider rows in 31 cursor pages each time. Reuse one bounded, URL-free projection across those
  readers. Every request still reads current linked/live-enabled sources, owner/kids policy, hidden items
  and category permissions. Await Room invalidation before cache lookup; imports/deletions revoke the
  projection. Group order, hidden channels and preferred quality remain current. No authorization cache.
- Room observer registration can synchronously install SQLite triggers (verified in bundled Room 2.8.5).
  Register it once inside the IO reader, not in the store's UI-created constructor.
- TV focused-channel lookup hydrated/authorized a complete entity on each D-pad movement even though
  only the collection editor used it. Keep it in the editor; actual Play still resolves/rechecks fresh data.
- TV rebuilt derived groups during focus/EPG recompositions, using repeated linear searches. Remember
  those projections by their real inputs and map order by identity. No retained hidden Compose screens.
- Home unconditionally waited two frames on entry/vertical movement. Visible targets now accept focus
  without that wait. Offscreen scrolling keeps layout settling and cancellation; repeated Down/Up
  advances from the pending logical section. PR #39's same rule remains in TV, Sport and VOD.

## Reproducible evidence

Baseline: exact `main` `2253ac4b6f404d0e360dbcf7b5b93f727421c9bb`.
Candidate includes the three original PR #39 commits through `a5db1a375f326a68a1007b6cfd6d0047806ce699` plus this change.
Both use the same final instrumentation APK. Only the approved disposable `mintv-v016` / `emulator-5588`
is installed. Android 14 ARM64, SwiftShader, four virtual CPUs, 2 GiB RAM, Swedish, weather/public sports
lookups disabled. Fresh separate userdata image; synthetic 12,000-channel fixture. No owner IPTV data. Local screenshots show the shared fixture stamp `0.14.0-dev`, not a new release version.
Two warmups and 20 cycles/process, both framebuffer sizes at benchmark density 320; native screenshots
use density 320/213 for 1080p/720p. Populated screenshots/loopback playback run after timings.

The inherited-image attempt is excluded: it retained earlier populated-scenario state, made public HTTP
calls, and failed its baseline visible-row assertion. Raw rejected results are preserved locally. The seed
now reapplies synthetic settings even when its fixture already exists; it never clears owner data.

The ordinary Compose probe can auto-advance awaited frames, hiding the two-frame delay. A separate
`NativeRemoteLatencyTest` deliberately has **no Compose test rule**: it injects real Android keys into
four visible synthetic cards using the actual Sport/VOD `NativeRowsNavigation` owner and real display
clock. It records key-injection-to-focus-callback latency (80 measured keys after warmup), not pixels
presented, full-shell latency or physical Chromecast timing. Existing CI retains every previous journey
and additionally runs this probe; no workflow is changed.

Raw JSON, summaries, APK hashes and native screenshot provenance are attached alongside this report.
Instrumentation/idling is included: these are not physical remote-to-photon latencies, TV FPS, cold-start
TTID, provider TTFF or real IPTV playback benchmarks. PSS is sampled, not a leak/peak-memory proof.
Read counts cover app-owned wrappers, not every Core/Room operation. Screenshots use synthetic local
programme/sports/progress data and neutral artwork; they are not verified real broadcast assignments.

## Physical Chromecast acceptance (owner-approved QA build only)

- Keep Stable and existing QA data. Verify approved QA signer/package/version before any later update.
- Repeatedly switch Home / TV / Sport / Film & serier / Profile; useful content, no repeated blank flashes.
- Hold each D-pad direction across long/offscreen rows; reverse direction; no skipped sections or traps.
- From scrolled TV, short OK plays once; long OK only opens actual source choices; Back restores exact
  channel/column/scroll. Verify picture/sound, channel changes, EPG arrivals and resume after sleep.
- Change profile, unlink/disable a source, hide a channel/category, switch kids policy, resync a playlist:
  no stale or cross-profile channels; favorites/order/preferred variants retained.
- Check empty/loading screens and Film/Series/details/resume, Sport return from player, Home favorites
  and update-dialog Back. No source credential/token/URL logging.

No physical-device latency or acceptance claim is made by this PR.

## Changed code and test ownership

Production: `features/home/NativeRowsNavigation.kt` (existing PR #39),
`features/home/SmartHomeContent.kt`, `features/live/SwedishLiveScreen.kt`,
`features/live/SwedishChannelStore.kt`, all beneath `app/src/main/java/tv/own/owntv/`.
Tests: `home/ChannelSourceStorageTest.kt`, `home/SmartHomeDeviceTest.kt`,
`performance/PerformanceJourneyTest.kt`, `performance/NativeRemoteLatencyTest.kt`, beneath
`app/src/androidTest/java/tv/own/owntv/`. CI's existing performance runner adds the real-clock case;
`tools/i18n/safe_literals.txt` declares only five technical Room table names.

Remaining work: physical Chromecast held-key/long-session acceptance, provider first-video-frame
measurement and profiling of full-shell rendering/cold startup with the owner's actual library. The
synthetic journey has no real IPTV/metadata artwork or large progress history. A faster navigation
journey is not proof of higher playback FPS or better decoder/provider startup. No animations were
removed, no always-composed hidden screens retained, and no unrelated services introduced.

## Final matched measurements

Milliseconds; `median / p95`, one process per variant/resolution. All raw samples are retained.

| Journey | 1080p before → after | 720p before → after |
|---|---|---|
| TV | 824.8 / 1128.4 → 249.7 / 325.1 | 828.0 / 1033.8 → 266.1 / 411.1 |
| Home | 898.8 / 1100.1 → 290.9 / 329.6 | 891.1 / 1051.3 → 322.9 / 458.4 |
| Sport | 217.0 / 286.5 → 249.7 / 283.4 | 243.5 / 282.6 → 265.0 / 351.3 |
| Film & serier | 157.1 / 182.6 → 150.0 / 172.2 | 150.9 / 183.8 → 165.9 / 183.6 |
| Profile | 210.8 / 283.0 → 215.1 / 250.7 | 199.7 / 238.9 → 214.6 / 281.5 |

| Separate real-clock visible-card focus (80 keys) | Before median / p95 | After median / p95 |
|---|---|---|
| 1920x1080 | 34.41 / 50.96 | 18.45 / 30.39 |
| 1280x720 | 36.06 / 50.0 | 5.81 / 19.26 |

| Whole-journey cost | 1080p before → after | 720p before → after |
|---|---|---|
| CPU process ms | 85869 → 42036 | 90707 → 46671 |
| Sampled maximum PSS KiB | 306889 → 269607 | 312392 → 273220 |
| Aggregate software-GPU frame p95 ms | 121.78 → 137.53 | 126.83 → 171.87 |

In each resolution, app catalogue reads fall **1395 → 31**, guide summaries stay **45 → 45**, and covered public HTTP stays **0 → 0**. Allocated ART bytes fall from about 2.4 GB to 0.55–0.56 GB per journey; GC counts fall 171 → 44 (1080p), 180 → 45 (720p).

**Tradeoffs:** Sport median does not improve; its 720p p95 worsens (282.6 → 351.3 ms), as does Profile 720p p95 (238.9 → 281.5 ms). Aggregate software-GPU frame p95 worsens in both resolutions. Every sampled frame exceeds 16.67 ms in this software-rendered setup; no claim of improved rendering FPS/jank is justified. Fewer frames and a different mixture of loading/useful-content frames also prevent interpreting the aggregate p95 as a controlled per-screen render comparison. Real-clock focus improves separately; physical full-shell frame tracing remains necessary. These are single-process samples, not confidence intervals. The earlier clean-image pilot also reduced Home/TV medians by roughly two thirds; it is kept locally as a pilot, not mixed into this final sample set.

## Validation and screenshots

- 25 targeted JVM regressions: pass. QA/debug and instrumentation APKs: build successfully.
- 44 targeted native regressions: pass (storage 1, Home 10, navigation 9, native Sport/VOD/settings presentation 16, channel grid 2, source selector 6).
- Matched production journeys, visible Sport probe and real-clock probe: pass on baseline/candidate in both resolutions. Populated **actual MainActivity** screenshots and remote/player-return/EPG/profile scenarios: pass in both resolutions and both variants. Loopback playback is synthetic Exo, not real IPTV/Chromecast.
- i18n merge-base ratchet: pass (45 baseline, 2454 safe literals); shell syntax and whitespace: pass. Full GitHub checks are required on the published PR head.

| Screen | Before 1080p / 720p | After 1080p / 720p |
|---|---|---|
| HOME | [1920x1080](before/POPULATED_HOME-1920x1080.png) / [1280x720](before/POPULATED_HOME-1280x720.png) | [1920x1080](after/POPULATED_HOME-1920x1080.png) / [1280x720](after/POPULATED_HOME-1280x720.png) |
| TV | [1920x1080](before/POPULATED_TV-1920x1080.png) / [1280x720](before/POPULATED_TV-1280x720.png) | [1920x1080](after/POPULATED_TV-1920x1080.png) / [1280x720](after/POPULATED_TV-1280x720.png) |
| SPORT | [1920x1080](before/POPULATED_SPORT-1920x1080.png) / [1280x720](before/POPULATED_SPORT-1280x720.png) | [1920x1080](after/POPULATED_SPORT-1920x1080.png) / [1280x720](after/POPULATED_SPORT-1280x720.png) |
| VOD | [1920x1080](before/POPULATED_VOD-1920x1080.png) / [1280x720](before/POPULATED_VOD-1280x720.png) | [1920x1080](after/POPULATED_VOD-1920x1080.png) / [1280x720](after/POPULATED_VOD-1280x720.png) |
| PROFILE | [1920x1080](before/POPULATED_PROFILE-1920x1080.png) / [1280x720](before/POPULATED_PROFILE-1280x720.png) | [1920x1080](after/POPULATED_PROFILE-1920x1080.png) / [1280x720](after/POPULATED_PROFILE-1280x720.png) |

The TV captures show the restored/scrolled grid, so a partial preceding row is intentional. The approved Home composition, top/context navigation, channel grid and Sport/VOD presentation remain intact. Missing EPG/artwork/broadcaster data remains explicit. Source picker, long list and update screenshots are also in `after/`.

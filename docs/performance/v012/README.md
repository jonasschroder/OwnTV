# v0.12 performance investigation

Production baseline: `08b2adb624a075d939af5f06d9e64f3a648d0ff0`.
Instrumentation-only baseline (identical production tree): `8afe2a0de5d9ffcc10b706ce6326c5dea72bfcaa`.

## Method and scope

API 34 Android TV arm64 emulator, read-only AVD overlay, software GPU, 2 GiB RAM,
12,000 deterministic synthetic channels, one favourite and last-live entry. QA package,
debug fixture signer, no real provider playback. Same dataset and test code before/after.
Two unreported warmups, 20 measured cycles per process (TV → Sport → VOD → Settings → Home),
20 D-pad Right/Left round trips, ten background/resume cycles. First-useful-Home waits for
actual production-shell channel and hero semantics. Instrumentation's launch sample includes
profile-gate interaction and test synchronization; it is **not release cold TTID**.
Navigation times include test synchronization; do not compare these absolute numbers to bare remote latency.

`FrameMetrics.TOTAL_DURATION` is software-rendered callback duration, not a precise missed-vsync count.
Callback losses are recorded separately. PSS samples are not a continuous peak or proof of a leak.
Process CPU covers the entire journey; UID traffic can include existing background services.
No network saving is claimed. Timing is observational: no flaky threshold or reduced CI coverage.
`baseline-r2` overlaps a host Gradle build; keep it for transparency, exclude it from the comparison.

## Before / after (final candidate)

Same host/emulator, two processes per revision, 40 navigation samples per destination.
Baseline/candidate are **debug instrumentation**, not release-speed claims.

| Operation | Baseline median / p95 ms | Candidate median / p95 ms |
|---|---:|---:|
| background_resume_ms | 729.0 / 882.4 | 766.4 / 867.0 |
| dpad_right_left_ms | 133.4 / 150.3 | 133.3 / 149.6 |
| enter_home_ms | 1615.7 / 2066.1 | 1432.9 / 1799.6 |
| enter_profile_ms | 216.5 / 278.7 | 215.1 / 283.4 |
| enter_sport_ms | 181.7 / 206.3 | 169.6 / 231.1 |
| enter_tv_ms | 1456.1 / 1924.1 | 1399.9 / 1762.0 |
| enter_vod_ms | 150.0 / 181.4 | 149.4 / 183.0 |
| instrumented_launch_to_useful_home_ms | 7663.3 / 7736.9 | 6987.2 / 7055.4 |

| Process metric | Baseline runs | Candidate runs |
|---|---|---|
| cpu_process_ms | 131730; 125281 | 112477; 112569 |
| pss_kib_samples | [254202, 281566, 277030, 282914, 278126, 293102, 263713]; [254598, 267779, 290719, 284347, 279019, 282434, 262280] | [260737, 271760, 278944, 282100, 275200, 282481, 257434]; [249057, 267654, 276978, 280750, 269894, 283074, 265368] |
| network_rx_bytes | 5521; 10563 | 10742; 10608 |
| network_tx_bytes | 1236; 2435 | 2459; 2395 |
| frame_callback_drops | 0; 0 | 0; 0 |

baseline: 920 FrameMetrics callbacks, TOTAL_DURATION median 33.8 ms / p95 134.1 ms.

candidate: 869 FrameMetrics callbacks, TOTAL_DURATION median 34.2 ms / p95 128.3 ms.

PSS maximum here means **maximum sampled PSS**, not continuous peak. All callbacks exceed
16.7 ms on this software-GPU setup; no hardware FPS improvement is claimed.
Earlier `candidate-r1` exploratory samples precede the final cheap profile-reset safeguard;
only `final-r1`/`final-r2` are included above. Raw samples are retained for audit.

## Confirmed avoidable work and changes

- Baseline process completed **23 `OwnTVPerf: home-data` loads** in one journey; candidate completed **0**.
  Native Home/Sport subscribed to `HomeViewModel.uiState` only for favourites, yet this started the
  full legacy Core feed. Every Home return requested it again. Native favourites now have a
  profile/source/category-scoped Room flow with the same legacy visibility/order policy.
  The legacy feed and all three observers start only with an actual subscriber and cancel on exit.
- Home separately authorized group choices, history, preferred history variants and favourites.
  One bounded operation now deduplicates channel ids/categories, captures access once and rechecks
  it before publication. Focused tests count two channel reads/one category read for `[1,1,2]`,
  and reject owner replacement, source revocation, profile switch, hidden-content changes and cancellation.
  Custom-category exceptions and kids restrictions remain; playback still revalidates.
- The initial source-preference emission rescanned the entire library immediately after the
  snapshot had applied those same choices. It is now skipped until choices actually differ.

## Reproduction

Build baseline and candidate **before** timed runs; never build concurrently with measurement.
Use only a disposable emulator. The instrumentation and host script independently refuse physical devices.
Install fixture-signed QA and instrumentation APKs only there, then:

```sh
ANDROID_SERIAL=emulator-5588 bash tools/performance/run-journey.sh /tmp/performance-evidence
python3 tools/performance/summarize.py /tmp/performance-evidence/journey-*.json
```

The additional `Synthetic performance journey` workflow repeats candidate journeys and uploads raw
samples. It does not replace any mandatory Android, startup, i18n, upgrade or distribution checks.
Normal CI also runs the real Room source-revocation regression and native production-shell
1080p/720p tests/screenshots. No owner data/credentials or physical screenshots are committed.

## Physical baseline (existing official QA only)

Chromecast with Google TV 4K, Android 14, QA `0.11.2-beta.1` / `1000016`:
Android first display, ten cold starts: median **2054 ms**, p95 **2328 ms**;
nine timed hot returns: median **172 ms**, p95 **240 ms** (one start supplied no TotalTime).
QA crash-buffer inspection found no QA FATAL. PSS snapshots 121629–129568 KiB belong to different
lifecycle phases, not a leak test. Startup/hot-return GFX cumulative p95 200 ms, 83/192 janky frames;
not steady-state remote-navigation data. Raw physical diagnostics remain private on the Mac.
Both owner installations/data were preserved. No candidate installed on Chromecast.

## Rejected/deferred changes and limits

- No shared persistent authorization/library cache: source revocation and reused profile ids must fail closed.
- No player/decoder/polling changes: no verified evidence that these caused this Home overhead.
- No Core pin, schema, image loader, design or signing changes.
- Real-provider TTFF, zapping, stalled/resolution-changing streams, SVT/Telenor/Twitch return,
  low-memory kill recovery and optimized hardware energy/CPU remain physical QA work after approval.
- No claim of improved network traffic, universal startup speed, leak elimination or hardware frame rate.
- Existing regressions cover EPG changes/midnight, async navigation, profile isolation, long/short OK,
  source restoration, persisted startup and signing upgrades; synthetic coverage cannot prove provider health.

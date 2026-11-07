# Native remote/navigation performance review

Extends draft PR #39 from main `2253ac4b6f404d0e360dbcf7b5b93f727421c9bb`.
No redesign, player/decoder, database schema, dependency, package, signing or workflow changes.

## Bottlenecks and implemented changes

- Home/TV rescanned 12,000 provider rows in 31 cursor pages on each re-entry. Reuse one bounded,
  profile-scoped projection (maximum 4096 variants), retaining no stream entities or URLs.
  Every lookup still reads current linked/live-enabled sources, owner/kids policy, hidden items
  and forbidden categories. Presentation order/hidden choices and preferred quality remain current.
- Cache coherency uses **public** Room `useWriterConnection` for one constant-size, read-only SQLite
  query: `total_changes()` detects that connection's writes; `data_version` detects other connections.
  Both are read on Room's single writer, never on interchangeable pooled readers. Core's pinned
  database is not auto-closing. The slot is released before metadata reads/catalogue scanning.
  All DB writes invalidate conservatively (including EPG/history and Room housekeeping); a cold
  metadata subscription can cause an additional miss. No schema, trigger or data is written.
- A writer may be busy importing EPG/playlists. Its optional version read has a **100 ms budget**;
  failure to obtain a version bypasses the cache and uses a fresh reader scan. Unknown versions
  never authorize reuse. Page cancellation propagates. Version SQL is counted separately as
  `CATALOG_VERSION`, so its cost is not hidden by the lower scan count.
- TV hydrated a full authorized channel on every focus movement even though only its collection
  editor used it. Hydrate in the editor; actual playback continues resolving/rechecking fresh data.
  Remember derived grid groups by real inputs and map order by identity instead of quadratic searches.
- Preserve PR #39's visible TV/Sport/VOD focus handling. Home also avoids unconditional two-frame
  waits for already-visible targets; offscreen scroll retains layout settling/cancellation. Repeated
  Up/Down advances from the pending logical section. No animations removed or hidden screens retained.

API basis: [Room connection contract](https://developer.android.com/reference/androidx/room/RoomDatabaseKt),
[SQLite total changes](https://www.sqlite.org/c3ref/total_changes.html),
[SQLite data version](https://www.sqlite.org/pragma.html#pragma_data_version).

## Verification method

Only approved disposable `mintv-v016` / `emulator-5588`: Android 14 ARM64, SwiftShader, four virtual CPUs,
2 GiB RAM, Swedish, weather/public sports lookups disabled. Separate fresh userdata, no owner IPTV.
12,000 synthetic channels; two warmups + 20 cycles/process at each resolution. Same final test APK
against both app APKs. Benchmark density 320; native screenshots use 320/213 for 1080p/720p.
Local header stamp `0.14.0-dev` is shared fixture configuration, not a changed release identity.

The ordinary Compose focus probe can auto-advance awaited frames. `NativeRemoteLatencyTest` instead
has **no Compose test rule**: real Android keys, display clock and production Sport/VOD focus owner,
four visible synthetic cards, 80 measured keys. It measures focus callback, not photons/full-shell.
Production screenshots/EPG/source-picker/scroll/loopback player/profile scenarios run after timings.
The baseline screenshot harness retains its legacy `reviewBaseline=true` flag (skips previous
candidate-only Home-action/geometry assertions); candidate assertions run fully. Timing/probes match.

Limitations: instrumentation/idling included, one process/variant/resolution, sampled PSS, software
GPU; no physical Chromecast, cold-start TTID, provider TTFF, playback FPS or leak-proof claim.
Counters cover app wrappers/version probes, not all Core/Room I/O. Programmes/sport/progress are
synthetic; unknown EPG/artwork/broadcaster states remain explicit. No real broadcast assignment proof.

## Validation history and safety

- Initial inherited-image pilot was excluded: previous scenarios/public HTTP and a failed focus
  assertion. Seed now reapplies synthetic settings. Earlier clean pilot figures remain historical.
- CI correctly rejected an unregistered real-clock test class; inventory now includes it and retains
  strict class/method/executed/no-skip assertions. No mandatory case or check was removed.
- CI lint rejected internal Room refresh/weak-observer calls in the earlier cache implementation.
  They have been removed, without suppressions. **Older private-API measurements/screenshots are
  superseded by the final public-API evidence below**, not claimed as its validation.
- Regression coverage includes warm reuse, immediate delete/reinsert, direct-connection stream
  revocation before async notification, source unlink/disable, hidden/kids policy, profile/owner reuse,
  saved preference retention, rapid input, offscreen focus, long/short OK and playback return.
- Three targeted version-budget tests cover immediate success, timeout/release and parent cancellation.
  Full required GitHub CI must pass on the final head before any merge/release decision.

## Changed code

Under `app/src/main/java/tv/own/owntv/`: `features/home/NativeRowsNavigation.kt` (existing #39),
`features/home/SmartHomeContent.kt`, `features/live/SwedishLiveScreen.kt`,
`features/live/SwedishChannelStore.kt`, `features/catalog/NativeReadWork.kt`,
`features/catalog/CatalogVersionBudget.kt`. Tests: existing source storage/Home/production performance
journeys, new native real-clock probe and JVM version-budget cases. Tools: performance runner,
strict test inventory and technical SQL-literal inventory. No Android manifest/workflow/Core-pin edit.

## Physical Chromecast checklist (owner-approved QA only)

- Verify approved QA package/signer/version compatibility; keep Stable and existing data.
- Revisit every top section; useful content, focus/scroll restored, no repeated blank/loading flashes.
- Hold/reverse all directions through long/offscreen rows; no skipped sections, freezes or traps.
- Scrolled TV: short OK plays once, long OK shows actual sources, Back restores channel/column/scroll.
- Verify picture/sound, channel changes, EPG arrivals, sleep/resume and player return with real IPTV.
- Switch profiles; unlink/disable source; hide item/category; change kids policy/resync: no stale or
  cross-profile channels, retained favorites/order/preferred variants.
- Check Film/Series/details/resume, Sport, Home favorites and update-dialog Back at 1080p/720p.
- Profile sustained frame timing/cold startup during a real EPG refresh. Synthetic soft-GPU aggregate
  p95 is not sufficient to establish physical rendering smoothness. No provider credentials in logs.

## Final public-API measurements

Milliseconds: **median / p95**. One process per variant/resolution; raw samples are retained.

| Journey | 1080p before → after | 720p before → after |
|---|---|---|
| TV | 806.3 / 966.4 → 250.0 / 300.6 | 783.2 / 1034.8 → 250.2 / 329.4 |
| Home | 883.7 / 1033.5 → 283.7 / 332.9 | 865.7 / 1116.0 → 281.6 / 332.0 |
| Sport | 233.1 / 299.6 → 233.7 / 266.0 | 216.7 / 265.5 → 233.5 / 283.2 |
| Film & serier | 150.1 / 181.9 → 149.8 / 166.8 | 149.6 / 180.2 → 150.7 / 178.6 |
| Profile | 207.4 / 233.2 → 206.8 / 234.1 | 199.4 / 216.2 → 198.9 / 233.3 |

| Real-clock visible-card focus (80 keys) | Before median / p95 | After median / p95 |
|---|---|---|
| 1920x1080 | 34.93 / 54.94 | 11.26 / 18.47 |
| 1280x720 | 33.6 / 46.65 | 7.01 / 17.95 |

| Whole-journey cost | 1080p before → after | 720p before → after |
|---|---|---|
| CPU process ms | 87193 → 42089 | 83224 → 42619 |
| Sampled maximum PSS KiB | 305094 → 271255 | 301569 → 268257 |
| Aggregate software-GPU frame p95 ms | 128.96 → 133.30 | 102.59 → 125.11 |

Each resolution: **catalogue scan queries 1395 → 62; additional version queries 0 → 92**. That is 1395 → 154 covered catalogue/version SQL operations, not zero-cost caching. Guide summaries stay **45 → 45**; covered public HTTP **0 → 0**. These counts exclude unchanged, uninstrumented Core/Room authorization DAO work. Two cold scans (62 pages) include conservative initialization invalidation; warm re-entry reuses the index.

**Tradeoffs:** Sport has no consistent section-entry gain; its 720p p95 increases 265.5 → 283.2 ms, and Profile p95 216.2 → 233.3 ms. Aggregate software-GPU frame p95 worsens in both sizes. Every sampled frame exceeds 16.67 ms in this software-rendered setup: no improved FPS/jank claim. Different loading/useful-content frame mixtures also limit aggregate comparisons. Real-clock focus improves separately; physical full-shell tracing remains necessary. One-process samples are not confidence intervals.

## Tests and screenshots

- Final public-API source: lintQaDebug, QA/debug + instrumentation builds pass; no RestrictedApi suppression. Three version-budget tests pass. All 44 targeted native regressions pass (storage 1, Home 10, navigation 9, native presentation 16, grid 2, source selector 6), including direct writer revocation.
- Matched production journeys, Compose probe and real-clock probe pass on both APK variants/resolutions. Populated **actual MainActivity** screen/EPG/source-picker/scroll/loopback player-return/profile scenarios pass on both.
- Earlier broad local QA JVM investigation passed 589 tests; current added budget cases pass separately. GitHub must run its complete JVM/Core/native/startup/upgrade/signing/i18n checks on the final source.
- i18n merge-base ratchet passes: 45 baseline + 2450 technical literals; shell syntax/whitespace pass.

| Screen | Before 1080p / 720p | After 1080p / 720p |
|---|---|---|
| HOME | [1920x1080](before/POPULATED_HOME-1920x1080.png) / [1280x720](before/POPULATED_HOME-1280x720.png) | [1920x1080](after/POPULATED_HOME-1920x1080.png) / [1280x720](after/POPULATED_HOME-1280x720.png) |
| TV | [1920x1080](before/POPULATED_TV-1920x1080.png) / [1280x720](before/POPULATED_TV-1280x720.png) | [1920x1080](after/POPULATED_TV-1920x1080.png) / [1280x720](after/POPULATED_TV-1280x720.png) |
| SPORT | [1920x1080](before/POPULATED_SPORT-1920x1080.png) / [1280x720](before/POPULATED_SPORT-1280x720.png) | [1920x1080](after/POPULATED_SPORT-1920x1080.png) / [1280x720](after/POPULATED_SPORT-1280x720.png) |
| VOD | [1920x1080](before/POPULATED_VOD-1920x1080.png) / [1280x720](before/POPULATED_VOD-1280x720.png) | [1920x1080](after/POPULATED_VOD-1920x1080.png) / [1280x720](after/POPULATED_VOD-1280x720.png) |
| PROFILE | [1920x1080](before/POPULATED_PROFILE-1920x1080.png) / [1280x720](before/POPULATED_PROFILE-1280x720.png) | [1920x1080](after/POPULATED_PROFILE-1920x1080.png) / [1280x720](after/POPULATED_PROFILE-1280x720.png) |

TV captures show restored/scrolled focus, with part of a preceding row intentionally clipped. No approved layout change. Extra long-list, source-picker and update-dialog screenshots are in `after/`.

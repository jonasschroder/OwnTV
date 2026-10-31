# v0.17 QA — daily TV, first implementation milestone

Branch `feat/v017-qa-experience`, existing draft #37. Baseline: frozen merged v0.16 `b94e98cbf28b61f75dcc804525cd128fd9683120`; brief-only starting head `79a07f4c6fb338b957dd29bd0b8e9d10e6b6beee`. Main and the separate Stable promotion are untouched.

## Audit and changes

The merged #36 already provides native five-column TV, source long-OK, profile-scoped source choices, finite header navigation, delayed Sport return, bounded EPG readers, authenticated updates and debounced CH tuning. These are reused. No new player, navigation tier, provider, permission, persistence format or Core revision.

1. **Live TV no-op stepping:** `LiveZapList.next` returned the current channel after wrapping a one-item list. The VM then scheduled an unnecessary tune. It now returns no target for unchanged positions, including the pending numeric-tune anchor. A regression sends 200 channel-key steps without producing a retune target, then verifies that national/regional SVT remain separate and wrap normally. Ordinary multi-channel stepping and debounce remain unchanged.
2. **Useful recent TV during matchday:** the Home match hero previously reduced last-watched TV to an anonymous button. `SmartHomeContent` now displays the actual channel name, fresh current programme and next programme under the existing match hero. The action and long-OK source route remain the same. Missing guide is explicit; no fabricated information.
3. **EPG display clock:** `rememberHomeDashboardGuide` previously advanced display time only when its reader completed. A slow reader could freeze progress/current-next evaluation. A lifecycle-scoped foreground clock now advances independently every five seconds, immediately on resume; it is cancelled when inactive. No new database/network reads. Tests cover midnight, stalled readers, cancellation and immediate resume.
4. **Sport defensive validation:** `hockeyCardBroadcast` independently rejects exhausted/invalid local intervals before preferring them over an unexpired exact external assignment. The normal foreground owner already expires local evidence upstream; this is additional boundary protection, **not a claim that the Chromecast's broadcaster issue was reproduced or fixed**. Valid exact local evidence still wins conflicting external data; streaming-only brands and ambiguous sources remain unplayable automatically.

## Validation and evidence

Native images are actual MainActivity in the explicitly approved disposable Android TV emulator `mintv-v016 / emulator-5588`, using synthetic profile 99000, 12,000 channels, long Swedish EPG, national/regional SVT, favourites, movie/episode progress and unknown-broadcaster fixtures. No owner data/provider URLs. Native screenshot densities are 320/213 dpi at 1080p/720p. The `0.14.0-dev/2` local fixture stamp is not a release version; no version files changed.

The baseline APK's production sources match frozen main; both APKs use the same final instrumentation harness. Timings run **before** populated screenshots at default 320 dpi, Swedish, weather off, same catalogue, one fresh app process per variant/resolution, two warmups and 20 measured cycles. CPU, sampled PSS, frame callbacks and bounded reader counts are retained. Instrumentation idling is included; not physical FPS, cold-start TTID, provider TTFF or measured network savings. No broad speedup claim is warranted by a small UI/identity change.

Full-shell regression checks the named recent-channel action and programme inside Home's hero, then exact match information/source picker/Back, delayed EPG geometry, TV source selection, actual loopback Exo playback, Sport return and profile isolation. No production stream is opened.

## Scope and remaining work

This is a focused first v0.17 implementation milestone, not completion of every proposed daily-viewing feature. Playback engines, multi-channel debounce, previous-channel shortcut, VOD, the accepted v0.16 layout, signing and updater stay unchanged. No new live score or verified broadcast service is added. Actual fixture/broadcaster ground truth remains unverified; probable and manual choices remain distinct.

The existing external-app store shortcuts were audited but retained for now: removing missing-app tiles safely also requires updating Home's registered focus entries and existing startup contracts. They must not simply disappear leaving invisible focus stops. This requirement remains outstanding.

## Owner Chromecast QA (later, no install authorized)

- Compare official QA signer/package/versionCode with installed QA before approving any in-place update; keep Stable independently installed.
- Home matchday: named recent channel, truthful now/next; short OK plays once, long OK source picker, Back same logical focus/scroll.
- Home without matchday, missing guide, midnight and long resume: no expired programme progress; manual refresh preserves focus.
- TV five columns, favourites, regional/national SVT, saved quality; source picker does not change source merely on focus.
- CH+/CH− through multiple logical channels; single-channel list does not restart; previous-channel shortcut and player Back remain predictable.
- Sport verified/probable/unknown/missing local source; manual selection stays profile-specific and player Back returns correctly. Independently verify the actual match broadcaster.
- Profile switch, VOD continuation/subtitles, Twitch, installed app shortcuts, Settings and update entry; no data loss.

Protected QA can only be proposed after Stable v0.16 publication, owner review, an explicitly approved later merge and green exact-main CI. No merge, protected signing, release publication or owner-device installation performed here.

## Native comparison

| Screen | 1080p before / after | 720p before / after |
|---|---|---|
| HOME | [before](before/POPULATED_HOME-1920x1080.png) / [after](after/POPULATED_HOME-1920x1080.png) | [before](before/POPULATED_HOME-1280x720.png) / [after](after/POPULATED_HOME-1280x720.png) |
| TV | [before](before/POPULATED_TV-1920x1080.png) / [after](after/POPULATED_TV-1920x1080.png) | [before](before/POPULATED_TV-1280x720.png) / [after](after/POPULATED_TV-1280x720.png) |
| SPORT | [before](before/POPULATED_SPORT-1920x1080.png) / [after](after/POPULATED_SPORT-1920x1080.png) | [before](before/POPULATED_SPORT-1280x720.png) / [after](after/POPULATED_SPORT-1280x720.png) |
| VOD | [before](before/POPULATED_VOD-1920x1080.png) / [after](after/POPULATED_VOD-1920x1080.png) | [before](before/POPULATED_VOD-1280x720.png) / [after](after/POPULATED_VOD-1280x720.png) |
| PROFILE | [before](before/POPULATED_PROFILE-1920x1080.png) / [after](after/POPULATED_PROFILE-1920x1080.png) | [before](before/POPULATED_PROFILE-1280x720.png) / [after](after/POPULATED_PROFILE-1280x720.png) |
| UPDATE | [before](before/POPULATED_UPDATE-1920x1080.png) / [after](after/POPULATED_UPDATE-1920x1080.png) | [before](before/POPULATED_UPDATE-1280x720.png) / [after](after/POPULATED_UPDATE-1280x720.png) |
| SPORT_LOOPBACK_PLAYBACK | [before](before/SPORT_LOOPBACK_PLAYBACK-1920x1080.png) / [after](after/SPORT_LOOPBACK_PLAYBACK-1920x1080.png) | [before](before/SPORT_LOOPBACK_PLAYBACK-1280x720.png) / [after](after/SPORT_LOOPBACK_PLAYBACK-1280x720.png) |

## Final local results

34 targeted JVM tests passed, 0 failures: LiveZapList, LiveEntryGate, HockeyBroadcastCards, HomeForegroundClock. QA/debug + instrumentation build passed. Both complete populated MainActivity journeys passed; real remote key events exercise source picker, loopback playback/return and profile switch. i18n inventory passed unchanged (45 debt / 2449 safe). Existing complete GitHub CI remains required on the exact final head.

| Metric | 1080p before -> after | 720p before -> after |
|---|---:|---:|
| enter_home_ms median/p95 ms | 890.25/1032.61 -> 870.69/1077.95 | 850.13/1012.83 -> 849.16/983.38 |
| enter_tv_ms median/p95 ms | 806.69/986.55 -> 783.96/1015.69 | 788.49/1051.42 -> 780.62/966.93 |
| enter_sport_ms median/p95 ms | 221.75/282.84 -> 232.77/265.95 | 233.29/266.23 -> 233.03/283.15 |
| enter_profile_ms median/p95 ms | 215.88/262.69 -> 214.24/249.26 | 200.19/231.96 -> 199.1/234.61 |
| background_resume_ms median/p95 ms | 745.97/917.81 -> 783.13/967.27 | 742.07/933.83 -> 735.44/900.26 |
| cpu_process_ms | 84616 -> 82680 | 81420 -> 81060 |
| sampled_max_pss_kib | 306260 -> 303312 | 302673 -> 307426 |
| app_reader_work_delta | {'CATALOG': 1395, 'GUIDE_SUMMARIES': 45, 'PUBLIC_HTTP': 0} -> {'CATALOG': 1395, 'GUIDE_SUMMARIES': 45, 'PUBLIC_HTTP': 0} | {'CATALOG': 1395, 'GUIDE_SUMMARIES': 45, 'PUBLIC_HTTP': 0} -> {'CATALOG': 1395, 'GUIDE_SUMMARIES': 45, 'PUBLIC_HTTP': 0} |
| frame_duration | {'n': 536, 'median_ms': 31.37, 'p95_ms': 118.85} -> {'n': 535, 'median_ms': 29.66, 'p95_ms': 117.82} | {'n': 531, 'median_ms': 23.68, 'p95_ms': 103.84} -> {'n': 534, 'median_ms': 23.22, 'p95_ms': 103.87} |

One process/variant/resolution, n=20, no runs dropped. Small mixed movements are not proof of speed gains: Home/TV 1080p p95 rise about 45/29 ms; CPU is slightly lower; max PSS is lower at 1080p but higher at 720p. Frame p95 and reader counts are broadly unchanged. Raw JSON preserves all slower actions, allocation and memory categories. Single-eligible-channel regression proves zero retune targets for 200 no-op key events; no actual provider stream-opening reduction was physically measured.

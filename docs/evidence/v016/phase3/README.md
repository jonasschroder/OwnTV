# v0.16 - final native UI review

Implements the [31 October owner review](https://github.com/jonasschroder/OwnTV/pull/36#issuecomment-6542698268) on integrated draft #36. Before is review head `7698b1a7d73fe2ec6582988e1a2f2db0f5da3019` (production-equivalent phase2 APK; its final follow-up changed tests only). Earlier phase2 evidence and measured main comparison remain intact.

## Material changes

| Screen | Implemented change |
|---|---|
| Update | The shared StagePopup now supports an explicitly opaque panel; only Update opts in. Normal dim backdrop remains. Title/current version/channel and action footer stay outside the D-pad-scrollable status/notes reader. Issue copy is smaller and regular weight, notes readable. No updater transport/policy/cryptography/installer changes. |
| Home | Unknown fixture now opens that match's information directly; holding OK opens its existing fixture-specific channel choice. Back restores the Home action, including after leaving the picker. League/time share one line; approved hierarchy, Twitch ownership and recent/continue data remain. Probable evidence stays labeled. |
| Settings | Compact heading and shorter category names, active fill plus explicit localized Aktiv marker and selected semantics. The focus outline is independent: focusing Profil while Snabbval remains selected is intentional. Normal Settings hides unrelated status information; mini/audio controls remain accessible. All 13 categories/routes retained. |
| Sport | Balanced hero, upcoming match and playable-channel card heights, larger channel text/action hint, optional existing provider logo. Separate Choose/Info retained; no fabricated fixture/image/broadcaster, source or API changes. |
| TV / Film & serier | Accepted five-column grid, stable guide geometry, scrolling/source selection and VOD experience retained. Comparison pictures included as regression evidence. |

## Native visual evidence

`before/` and `after/` contain actual MainActivity screenshots at 1920x1080 and 1280x720. Both variants use the same native density for each resolution (320/213 dpi). These are local synthetic fixtures: 12,000 catalogue entries, 32 Other Swedish channels, distinct national/regional SVT1, long Swedish now/next, missing guide, favourites, movie/episode continuation and two relative-time Färjestad fixtures with unknown broadcasting. No owner's IPTV data, credentials, provider URLs or real programme imagery. The TV grid is intentionally scrolled to its focused second row. Clock/fixture times differ; these are layout comparisons, not pixel-difference claims. Version `0.14.0-dev / 2` is the local test stamp, not a published release identity.

| Screen | Before / after 1080p | Before / after 720p |
|---|---|---|
| Hem | [before](before/POPULATED_HOME-1920x1080.png) / [after](after/POPULATED_HOME-1920x1080.png) | [before](before/POPULATED_HOME-1280x720.png) / [after](after/POPULATED_HOME-1280x720.png) |
| TV | [before](before/POPULATED_TV-1920x1080.png) / [after](after/POPULATED_TV-1920x1080.png) | [before](before/POPULATED_TV-1280x720.png) / [after](after/POPULATED_TV-1280x720.png) |
| Sport | [before](before/POPULATED_SPORT-1920x1080.png) / [after](after/POPULATED_SPORT-1920x1080.png) | [before](before/POPULATED_SPORT-1280x720.png) / [after](after/POPULATED_SPORT-1280x720.png) |
| Film & serier | [before](before/POPULATED_VOD-1920x1080.png) / [after](after/POPULATED_VOD-1920x1080.png) | [before](before/POPULATED_VOD-1280x720.png) / [after](after/POPULATED_VOD-1280x720.png) |
| Inställningar | [before](before/POPULATED_PROFILE-1920x1080.png) / [after](after/POPULATED_PROFILE-1920x1080.png) | [before](before/POPULATED_PROFILE-1280x720.png) / [after](after/POPULATED_PROFILE-1280x720.png) |
| Uppdatering | [before](before/POPULATED_UPDATE-1920x1080.png) / [after](after/POPULATED_UPDATE-1920x1080.png) | [before](before/POPULATED_UPDATE-1280x720.png) / [after](after/POPULATED_UPDATE-1280x720.png) |

`journeys/` has review/progress/permission/confirmation renderings using the real shared Compose presentation in ComponentActivity with explicitly synthetic post-authentication states. These do not prove a signed download, permission grant, system install or process restart. The MainActivity Update picture shows the actual debug-signing guard. `contact-sheets.py --output-dir <directory>` creates the two visual review PDFs using ReportLab/Pillow; no web prototype involved.

## Verification

- 47 targeted JVM update/navigation/broadcast/return tests passed; QA/debug and instrumentation builds passed.
- Native opacity regression compares four interior pixels against two different underlying backgrounds, also proving the exterior really changed. 14 updater states x 2 font scales (1.0/1.4) x 2 native resolutions = 56 cases. Header identity stays in the panel; D-pad scroll, Later, progress, verification, permission action, explicit install-consent action, cancellation, low space and Back restore are covered. No real APK/permission/installer invoked.
- Home short/long OK exactly-once tests at both resolutions; full MainActivity opens exact match, Back restores Home, long OK opens picker, two Backs return to same Home focus. Sport player return, TV source picker, late guide/focus geometry, manual Sport, profile isolation and actual Exo loopback remain in the populated full-shell journey.
- Settings regression proves focus-only does not activate another category, then OK activates and restores detail focus.
- The test setup now waits a bounded five seconds for actual display dimensions rather than waiting indefinitely for Google TV launcher's animations. A disposable-emulator window/launcher stall required a clean emulator restart; no Android production workaround was introduced.
- i18n merge-base ratchet and mandatory TV-suite inventory passed. Existing required CI jobs retained; two Home tests added, no test removed.

The complete required Android/i18n/performance CI must pass on the final exact PR head. Local tests remain focused per AGENTS.md.

The performance journey uses the same default 320 dpi at both framebuffer sizes, as in phase2. Screenshot review instead uses 320/213 dpi for native 1080p/720p. These are explicitly different purposes; screenshot density must not silently change the established benchmark conditions.

## Remaining gates

Physical Chromecast approval/testing is still required: remote long press/readability, real IPTV playback, real authenticated signed QA update including Android consent/cancel/restart, and independent actual fixture/broadcaster ground truth. Previous QA0.15 TV4 Sport Live3 picture/audio confirmation remains accepted; it does not verify a match assignment. No new candidate installed on Chromecast.

Proposed QA `0.16.0-beta.1`, package `se.jonasschroder.mintv.qa`, permanent QA signer and policy-generated versionCode. Existing protected workflow requires an approved immutable **main** commit with green exact-main CI; this unmerged draft head is not an eligible signing input. Prepared Swedish notes remain `docs/release-notes/v016-cohesion.sv.md`.

Rollback is a revert of this follow-up, independently of earlier #33-35/cohesion. No DB schema, Core pin, profile/source data, IPTV credentials, package, signing key, permissions or update safeguards changed. Stable/owner QA untouched. No merge, signing or release publication.

## Measured finishing-pass cost

One initial comparison showed small contradictory p95/PSS movements. A **single predeclared replication with reversed version order** was performed; no samples/runs were removed. Eight raw files are in `performance/`, initial comparison in `comparison.json`, pooled latency/per-process costs in `replication.json`. Reproduce with `compare.py` and `replicate.py`. Two processes/variant/resolution, two warmups and 20 measured cycles/process (40 pooled), same final harness and fixed numeric readers, SwiftShader ARM64 Android TV emulator.

| Metric | 1080p review -> final | 720p review -> final |
|---|---:|---:|
| Home median / p95 ms | 839 / 1001 -> 849 / 1050 | 835 / 1081 -> 866 / 1015 |
| TV median / p95 ms | 766 / 1000 -> 775 / 1050 | 783 / 1023 -> 791 / 1034 |
| Sport median / p95 ms | 217 / 300 -> 233 / 286 | 216 / 283 -> 233 / 271 |
| Settings median / p95 ms | 216 / 266 -> 200 / 266 | 200 / 250 -> 199 / 232 |
| Mean process CPU ms | 80810 -> 82973 (+2.7%) | 80705 -> 81577 (+1.1%) |
| Max sampled PSS range KiB (two processes) | 302626-310160 -> 305669-306326 | 307774-308820 -> 304787-305585 |
| Mean sampled PSS KiB (mean of processes) | 291209 -> 285927 | 289035 -> 288904 |
| Pooled frame median / p95 ms | 30.89 / 120.21 -> 29.55 / 114.21 | 22.99 / 106.27 -> 23.37 / 106.31 |
| Covered catalogue / guide / public HTTP calls | 1395-1396 / 45 / 0 -> 1395 / 45 / 0 | 1395 / 45 / 0 -> 1395 / 45 / 0 |

**Remaining performance limitation:** this is not zero latency regression. The final finishing pass has +49/+50 ms pooled Home/TV p95 at 1080p and +11 ms TV at 720p; Home 720p p95 improves 66 ms. Some medians/CPU rise slightly. Reversed 1080p runs reverse the TV difference (initial final slower; replication final faster), so one small software-GPU experiment cannot attribute the difference to a specific layout change. All slower actions remain in JSON. The practical Chromecast cost requires physical QA; no further speculative performance rewrite was added.

No additional PSS growth is demonstrated across the replicated processes; this does **not** resolve the earlier +4-6% phase2/main memory tradeoff or prove absence of a leak. Cumulative allocation remains about 2.4 GB per complete journey, with native libraries/decoder ownership unchanged. Covered guide/HTTP counts are unchanged. No new decoder, background polling or content cache was added. Instrumentation/Compose idling are included; frame callback timings are not physical FPS, a single useful-Home launch per process is not cold-start TTID, and PSS is sampled rather than peak/retained heap.

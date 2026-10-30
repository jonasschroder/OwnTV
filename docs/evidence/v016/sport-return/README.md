# v0.16 — Sport playback return, following physical Chromecast evidence

Planning reference: PR #32. Independent branch from main `88a51db17493869805db7763a5e69c22a6299dd1`; complements #33 (rapid D-pad intent) and #34 (updates). No merge or release proposal.

## What the real device established, 30 October 2026

Chromecast / Android 14, installed QA `0.15.0-beta.1` / `1000021`, read through PackageManager. The existing connection was already authorized; no new pairing was needed. No APK, setting, source preference or user-data reset was applied.

Sport loaded the next Växjö–Färjestad fixture (31 October, 15:15), labelled **probable** TV4 Sport Live 3 from the existing free TV-Programidag integration. The hero's Watch action opened the corresponding linear service; its actual HUD showed ExoPlayer/720p/stereo and current highlights. The owner confirmed moving picture and sound.

The app's existing sanitized export records SPORT REQUESTED → SOURCE_VALIDATED (+20 ms) → ACCEPTED (+84 ms) → OPENED_EXO (+1,398 ms), without fallback/exhaustion for this request. This is a request-to-engine event interval, **not measured first-video-frame latency**. The owner confirmation establishes A/V separately. Provider URLs, credentials, library IDs, full provider labels, screenshots and the device report are retained only locally, outside the repository.

This does **not** prove that tomorrow's actual fixture will air on that channel. The source remains probable; channel playback is separate from confirmed fixture/broadcaster accuracy. The previously reported Färjestad stream failure was not reproduced in this session and is not declared fixed.

A separate native navigation defect was reproduced: Back returned to Sport with transient empty content, then no visible focus after the data reappeared. Up from this state focused Home instead of the active Sport destination.

## Root cause and implementation

`OwnTVShell.exitPlayer` requested header focus while fullscreen still excluded the browse composition. The request could fail before the header remounted. Sport did not consume a post-remount return request, unlike pages with existing restore handling. Its saved hero/card also arrives after local schedule cache hydration.

- Record the ephemeral browse owner when fullscreen opens/expands. Only return to the same Sport page and profile; mini close or changed page/profile never inherits it.
- Pass a dedicated return request to NativeSportScreen. Reuse NativeContentEntry's existing post-frame, bounded entry mechanism and active-header fallback.
- Expose an entry hook after the existing initial local schedule read, so transient empty hydration does not masquerade as a removed saved card. Reuse current saveable row/scroll and detail/picker entry owners.
- Explicit remote input cancels the outstanding request, preventing a later focus jump over the user's browsing.
- Keep the existing player lifecycle, source validation, EPG, network/TTL gates, manual channel choices, profiles and data contracts. No extra decoder, polling loop, cache query, permission or dependency.

The existing 30-frame entry budget remains finite. If valid content is still unavailable, focus falls back to the active Sport header and does not jump into later content. Exact row restoration after an indefinitely slow/missing source is not promised.

## Validation

- 2 focused NativePlaybackReturn JVM tests pass (same owner; changed page/profile; missing owner; no profile; mini/browse exclusion).
- QA debug and androidTest compilation pass, unchanged Core pin/signing/package settings.
- All **9 NativeNavigationDeviceTest methods pass at 1920×1080 and 1280×720**. The existing mandatory fullscreen method now verifies saved row restoration after asynchronous remount, unavailable-content fallback without later stealing focus, profile isolation, and cancellation by explicit remote navigation. Existing TV timeline behavior remains covered.
- Literal ratchet, locale-number, overflow and executing-test inventory checks pass. No CI workflow or required-check reduction.
- Full existing CI runs on the draft PR. The new code is **not installed or verified on Chromecast**; physical evidence above concerns the published QA baseline. Fixtures test production navigation components with synthetic rows, not real provider playback.

Rollback: revert this workstream; only ephemeral focus ownership/readiness was added, with no preference/schema migration. Owner-approved signed QA testing is required before claiming the device return defect fixed. Keep tomorrow's fixture/broadcaster verification as a separate acceptance item.

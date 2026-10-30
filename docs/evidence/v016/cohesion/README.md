# v0.16 integrated native candidate

Reference: planning PR #32 and its 30 October owner correction. Base main: `88a51db17493869805db7763a5e69c22a6299dd1`. Reuses the exact reviewed file contents of draft #33 (`a7e5bf8`), #34 (`f0de00b`) and #35 (`0861101`) before the new changes. The integration is a development branch, not a merge or release. Original drafts stay open.

## Screen and navigation contract

| Destination | Reused functionality and current refinement | Focus / Back |
|---|---|---|
| Global | Five destinations, compact utilities, global authenticated update entry from #34 | Left/Right only moves focus; OK activates; Down activates and enters. No third persistent menu. |
| Home | Approved content order, live/resume/matchday identity, favourites and Twitch/shortcuts retained. Lower minimum hero height; actual channel remains recognizable without EPG; current/next programmes separate. | Existing logical/profile-scoped row owners; guide changes never replace focus keys. Source picker still uses long OK. |
| TV | Existing five-column grouped channels, source/quality ownership, logos and neutral artwork retained. Next start time, quality badge, concise remote hint, distinct missing-current/missing-next states. | Short OK plays; long OK source picker; Back restores logical channel/scroll via existing owner. |
| Sport | Match/time at left, truthful broadcaster/action surface at right; compact fixture hierarchy. Initial channel read no longer reports a completed empty result. No new source or invented assignment. | Reuses #33 rapid remote intent and #35 delayed post-player restoration; manual fallback stays. |
| Profile/settings | Existing category/detail architecture and every setting/deep link retained; concise category title. Left from details explicitly targets the selected category instead of geometry. | One category rail. Editors retain their input handling; ordinary directional exit targets the active rail item. |
| Film & series | Accepted content selection, resume and subtitles preserved. | Existing Movie/Series directional entry and logical row restoration. |
| Update | Reuses #34 review/download/verify/permission/Android-consent/error/re-entry states. No additional update owner. | Global entry is in the existing header; modal Back restores it. No install or permission bypass. |

This is the first integrated visual reference, not a claim that all v0.16 requirements or physical QA are complete. Larger replacements of the approved composition still require owner visual review. No new decoder, database schema, permission, Core pin, release version or signing change.

## Measured work reduction

`SwedishChannels` previously normalized every known broadcaster for every scanned catalog row and compiled three regexes repeatedly. It now builds immutable recognition tables once. No derived-library cache or invalidation heuristic was introduced. `SwedishChannelStore` reuses Core's exact category snapshot policy and reads the owner once instead of querying the same profile/category per category. Invalid/missing profiles fail closed.

`RecognitionProbe.java` compares separately loaded baseline/current compiled classes. The same 12,000-name synthetic dataset plus 7 edge cases produced identical complete values (12,007 comparisons). After 5 warmups per variant, 20 alternating measurements: host JVM median **54.87 → 25.74 ms**, p95 **61.23 → 27.25 ms**. See raw JSON and bytecode hashes. This isolates recognition only; it is not Android UI, physical latency, network or playback evidence.

Reproduction: Java 21 source-file mode `java RecognitionProbe.java BASELINE_CLASSES CANDIDATE_CLASSES KOTLIN_STDLIB_JAR`. Classes come from `app/build/intermediates/built_in_kotlinc/qaDebug/compileQaDebugKotlin/classes`; baseline source is unchanged from main. Source and binary provenance must be preserved when comparing.

## Validation status

- QA debug + androidTest compile locally, test-only stamp `0.14.0-dev / 2`.
- 34 focused JVM regressions pass (recognition, source ordering, rows, playback-return controller, update experience).
- i18n literal ratchet, number locale, text overflow and instrumentation suite inventory pass.
- Added native regressions: selected Settings category after detail scroll; Home guide arrival/missing-next/expiry while action remains focused; loading-versus-empty Sport; kids/custom-hidden categories/source isolation using real Room.
- Native 1080p/720p screenshots, actual MainActivity cold/recreate and identical-data Android journeys: pending local emulator run. Owner explicitly approved installations only in the disposable `mintv-v016` emulator; never the Chromecast.
- Existing complete GitHub checks remain mandatory. The only workflow addition schedules the two new native regressions; no check or coverage was removed.

## Physical evidence and remaining checks

Existing Chromecast QA `0.15.0-beta.1 / 1000021`, Android 14: owner confirmed picture and sound on TV4 Sport Live 3 through Se matchen. This is service playback, **not** verification of the 31 October Växjö–Färjestad assignment. No new candidate was installed on that device. #35's return fix and all combined changes still require approved physical QA.

Remaining: owner visual review, complete integrated CI, signed QA update/restart/permission/storage/network journey, physical remote/player/EPG checks and actual matchday broadcast ground truth. Android synthetic navigation cannot establish decoder/HDR/audio or real IPTV correctness. No release is ready yet.

Rollback: revert the new cohesion commit independently of the reused drafts; no persisted-data migration to undo. Keep main, Stable and both owner installations untouched.

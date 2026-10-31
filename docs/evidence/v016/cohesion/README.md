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

## Actual MainActivity Android journeys

Same emulator / 12,000 synthetic-channel dataset, 23 recognized logical channels across collections (10 in General); two processes at each resolution, two warmup cycles then 20 measured cycles each. No simultaneous Gradle build. Both fixtures have the same test stamp and pinned Core. Timing includes instrumentation and Compose idling; this is not remote-to-photon latency or playback startup. Raw per-process data and `android-comparison.json` are retained; `python3 compare.py` reproduces summaries.

| Journey (p95 ms; n=40 each) | 1080p baseline → candidate | 720p baseline → candidate |
|---|---:|---:|
| Home entry | 1,715 → 1,066 (−37.8%) | 1,999 → 1,032 (−48.4%) |
| TV entry | 1,699 → 1,016 (−40.2%) | 1,735 → 967 (−44.3%) |
| Sport entry | 236 → 250 (+5.9%) | 250 → 234 (−6.6%) |
| Settings entry | 250 → 251 (+0.3%) | 250 → 249 (−0.3%) |

Full-journey process CPU means: **119.4 → 81.9 s (1080p)**, **119.6 → 80.4 s (720p)**. Sampled max PSS **283.6 → 300.2 MiB** and **282.4 → 298.6 MiB**: memory did not improve. Software-GPU frame p95 barely changed: **122.2 → 121.3 ms**, **108.8 → 105.7 ms**. Some already-short VOD/header/foreground samples are slower; all raw results are reported instead of selecting only wins. Instrumented useful-Home launch has only n=2 per variant/resolution and is not TTID evidence.

The existing weather feature was enabled during measurements and can warm its own public-data cache; UID traffic is recorded, **not HTTP request counts**. In particular the first baseline process received about 2.9 MB, versus about 11 KB in subsequent processes. Do not attribute that cache difference to this implementation or claim zero network requests. IPTV/decoder/EPG-refresh latency and real peak memory remain unmeasured. Screenshot-only fixtures disable weather to avoid publishing an IP-derived location.

The n=40 comparison uses commit `9fbefd3` before the subsequent Settings Left correction and stable live-hero guide slots. The unchanged existing midnight regression caught the latter geometry issue; it now passes at both resolutions without relaxing the assertion. Final candidate verification (n=20 per resolution) is recorded separately after these fixes, in Swedish with weather disabled for screenshot privacy. The channel-index algorithm is unchanged between those candidates. Exact measured/final APK hashes are recorded separately. No broad speedup is claimed for every action.

Final-layout verification (n=20 at each resolution, Swedish, weather off): Home p95 **980 / 999 ms**, TV p95 **1,000 / 934 ms** (1080p / 720p). This supports the direction of the larger matched comparison without pretending that locale/weather conditions are identical. See `final-verification-summary.json` and raw runs.

## Native screenshot reference

`native/1920x1080/` and `native/1280x720/` contain **actual MainActivity**, all five destinations plus the real debug-guard update dialog, with the same synthetic library and no EPG/artwork provider. Version `0.14.0-dev` is the local fixture stamp, not a shipped v0.16 build. Weather is disabled only in the guarded emulator fixture; no IP-derived location is uploaded. Before images remain in `../phase-0/baseline/`.

`home-populated-component-synthetic.png` and `sport-populated-component-synthetic.png` in each folder show production Compose components with explicitly synthetic programmes/fixtures/progress in ComponentActivity. They demonstrate populated layout, **not the full production shell**, a real schedule or verified TV assignment. No programme photos or real broadcaster assets are invented. Full-shell no-source/missing-EPG states remain honest.

## Validation status

- QA debug + androidTest compile locally, test-only stamp `0.14.0-dev / 2`.
- 34 focused JVM regressions pass (recognition, source ordering, rows, playback-return controller, update experience).
- i18n literal ratchet, number locale, text overflow and instrumentation suite inventory pass.
- Added native regressions: selected Settings category after detail scroll; Home guide arrival/missing-next/expiry while action remains focused; loading-versus-empty Sport; kids/custom-hidden categories/source isolation using real Room.
- 14 targeted native executions plus 4 final guide/midnight executions pass across 1080p/720p, including font scaling, source persistence/isolation and the combined Sport return/rapid-key behavior. The new Settings regression first failed with a simple direction property; group `onExit` redirection fixed it. Native main-screen capture and repeated real MainActivity cold launches pass in the matched journeys; new candidate process-recreation/upgrade matrix remains covered by required CI. Owner explicitly approved installations only in the disposable `mintv-v016` emulator; never the Chromecast.
- Existing complete GitHub checks remain mandatory. The only workflow addition schedules the two new native regressions; no check or coverage was removed.

## Physical evidence and remaining checks

Existing Chromecast QA `0.15.0-beta.1 / 1000021`, Android 14: owner confirmed picture and sound on TV4 Sport Live 3 through Se matchen. This is service playback, **not** verification of the 31 October Växjö–Färjestad assignment. No new candidate was installed on that device. #35's return fix and all combined changes still require approved physical QA.

Remaining: owner visual review, complete integrated CI, signed QA update/restart/permission/storage/network journey, physical remote/player/EPG checks and actual matchday broadcast ground truth. Android synthetic navigation cannot establish decoder/HDR/audio or real IPTV correctness. No release is ready yet.

Rollback: revert the new cohesion commit independently of the reused drafts; no persisted-data migration to undo. Keep main, Stable and both owner installations untouched.

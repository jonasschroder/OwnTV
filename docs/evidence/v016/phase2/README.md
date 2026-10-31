# v0.16 - native review, phase 2

Continues the integrated draft #36 from `016e956271547f1cd1324122d5a2c2972f223464`. That exact head completed Android, i18n and synthetic-performance CI successfully before this phase. #33-35 remain reused, not separate merge candidates. Planning reference: #32 and the latest owner comment in #36.

## Visible changes and root causes

| Screen | Change in this phase | Scale |
|---|---|---|
| Hem | Approved layout/content ownership retained. Shared content cards retain a stable three-pixel outline without a scale transform; hero action outlines match its width. Real channel identity, matchday, continuation and Twitch remain. | Subtle visual consistency |
| TV | Two programme-title lines reserved using actual font metrics, plus persistent channel metadata and next slot. Missing/late EPG no longer changes card geometry (the full-shell regression caught a one-pixel change with estimated heights). Concise missing-guide state and readable quality. | Tangible readability/stability |
| Sport | Featured scheduled match removed from duplicate rails. Separate Watch/Choose channel and Info; unavailable/missing/ambiguous states explain manual fallback. Holding OK on a playable hero opens existing fixture-scoped choice. Loaded-empty state has a recovery action. No new broadcaster claim. | Tangible action/hierarchy improvement |
| Sport player return | The old fixed frame retry could expire before populated catalogue hydration. Return hook is now published only after schedule and channel owners finish. Existing pending owner/profile checks and user-input cancellation remain. A loaded empty result can still fall back. | Reliability fix, not just styling |
| Profil/inställningar | OK and Right enter the selected category once. Detail focus is saved before Left redirects, restored explicitly, scoped to category, with stable row/profile composition keys. The requester belongs inside the scroll container's focus target. Category change resets to a valid detail row. | Tangible remote simplification |
| Film & serier | Repeated empty-library explanation shown once, compact neutral entry cards; existing title-details/resume/player/subtitles unchanged. | Empty-state polish |
| Uppdatering | Existing authenticated updater retained. New remote regression follows review, Later, progress, verification, permission, confirmation, completion and recovery. It never triggers an installer or grants permission. | Acceptance evidence for integrated #34 |

### Remote focus map

`Topprad -> Down -> aktuell destination -> giltigt sparat innehåll` (TV/VOD keep their one contextual selector). Left/Right in header do not activate; OK activates. TV short OK plays, long OK opens sources; Back restores the logical card and scroll. Sport fullscreen Back first closes visible player controls, then exits and restores the channel after hydration. New input cancels pending automatic return. Settings Left returns to selected category; OK/Right restores its detail row. Modal Update Back/Later returns to the exact global version/update entry. Profile changes invalidate prior content ownership.

## Actual native pictures

All `before/` and `after/` pictures are **real MainActivity**, not isolated components or the HTML lab, at the filename's native resolution. They use explicitly synthetic local fixtures: 12,000 channels, 32 Other Swedish services, distinct national/regional SVT1, long now/next titles, missing guide, favourites, movie/episode progress and an unknown-broadcaster Färjestad fixture. No external artwork/credentials or owner's IPTV data. No fabricated production schedule.

The scenarios use relative wall-clock times; the final fixture caps the match to the same Stockholm date so an evening test cannot accidentally become tomorrow's non-matchday state. Before and after timestamps differ; this is a layout comparison, not a pixel-diff test. The TV comparison is deliberately scrolled to the focused second row. VOD has saved continuation but no qualified discovery artwork/metadata. Local stamp `0.14.0-dev / 2` is the test fixture identity, **not a published v0.16 version**. Weather is off.

| Screen | Before 1080p | After 1080p | Before 720p | After 720p |
|---|---|---|---|---|
| Hem | [before 1920x1080](./before/POPULATED_HOME-1920x1080.png) | [after 1920x1080](./after/POPULATED_HOME-1920x1080.png) | [before 1280x720](./before/POPULATED_HOME-1280x720.png) | [after 1280x720](./after/POPULATED_HOME-1280x720.png) |
| TV | [before 1920x1080](./before/POPULATED_TV-1920x1080.png) | [after 1920x1080](./after/POPULATED_TV-1920x1080.png) | [before 1280x720](./before/POPULATED_TV-1280x720.png) | [after 1280x720](./after/POPULATED_TV-1280x720.png) |
| Sport | [before 1920x1080](./before/POPULATED_SPORT-1920x1080.png) | [after 1920x1080](./after/POPULATED_SPORT-1920x1080.png) | [before 1280x720](./before/POPULATED_SPORT-1280x720.png) | [after 1280x720](./after/POPULATED_SPORT-1280x720.png) |
| Film & serier | [before 1920x1080](./before/POPULATED_VOD-1920x1080.png) | [after 1920x1080](./after/POPULATED_VOD-1920x1080.png) | [before 1280x720](./before/POPULATED_VOD-1280x720.png) | [after 1280x720](./after/POPULATED_VOD-1280x720.png) |
| Profil/inställningar | [before 1920x1080](./before/POPULATED_PROFILE-1920x1080.png) | [after 1920x1080](./after/POPULATED_PROFILE-1920x1080.png) | [before 1280x720](./before/POPULATED_PROFILE-1280x720.png) | [after 1280x720](./after/POPULATED_PROFILE-1280x720.png) |
| Uppdatering | [before 1920x1080](./before/POPULATED_UPDATE-1920x1080.png) | [after 1920x1080](./after/POPULATED_UPDATE-1920x1080.png) | [before 1280x720](./before/POPULATED_UPDATE-1280x720.png) | [after 1280x720](./after/POPULATED_UPDATE-1280x720.png) |

`journeys/` also contains late-guide, 32-channel scrolling, source choice, actual ExoPlayer loopback playback and manual Sport choice at both sizes. The update-flow pictures there are **ComponentActivity presentation tests with synthetic post-authentication states**; the full-shell Update picture shows the real debug-build guard. Neither is evidence of a production-signed download/Android installation.

Contact sheets: `python3 docs/evidence/v016/phase2/contact-sheets.py --output-dir output/pdf` (ReportLab, Pillow and an existing Arial/DejaVu TrueType font). They combine each real full-shell empty/bounded-library state and populated state, before/after, followed by the separately labelled updater presentation flow. PNGs are unedited.

## Validation

- 47 focused JVM tests: broadcast evidence/availability, native row targets, playback-return ownership, update experience/policy/transport; no failures or skips.
- Eight targeted presentation executions cover Settings restoration, unique playback/manual choice (1080p/720p), removed fixture/rapid intent, update remote journey (1080p/720p), 720p large text and guide updates.
- Actual MainActivity populated journey passes at **both 1920x1080 and 1280x720**: late EPG keeps exact bounds/focus; scrolling and long OK source-picker Back; global EPG refresh keeps focus; short OK starts a local synthetic MP4 in real ExoPlayer; finite HUD/fullscreen Back restores TV4 Sport Live 3; explicit fixture-scoped manual choice; profile switch confirms the empty profile has no prior source IDs/channel UI.
- Existing native navigation regression now delays content by 1.3 seconds (beyond the old fixed retry) and still tests genuine-empty fallback, later-data non-stealing, explicit input cancellation and profile invalidation.
- Actual bundled SQLite regression: 200,000 programmes, 16 requested projections, exactly **one** guide-summary query; unchanged authorization/expiry checks.
- Literal ratchet, number locale, overflow and suite inventory pass. Full required CI remains mandatory on the new PR head; only two additive updater cases were added to the existing Android inventory. No required check removed, skipped or weakened.

Successful native runner outputs are in `tests/`; `provenance.json` records fixture/source hashes. QA/debug fixture builds stay local. No test installs on physical devices.

## Matched performance and memory

Run `python3 docs/evidence/v016/phase2/compare.py` to rebuild `comparison.json` from all four raw files in `measurements/`. Same disposable AVD, 12,000-channel library, Swedish, weather off, software GPU; one fresh app process per variant/resolution, two warmups and 20 measured cycles. The original main has only `baseline-observation-only.patch` added, containing the exact same fixed numeric counters as the candidate. No baseline UI, source selection, player or policy changes. Never compare these counts to an uninstrumented zero.

`NativeReadWork` retains three atomic aggregate counts, no SQL/parameters, URLs, identifiers, credentials, owner objects or content lists. It covers app-owned catalogue reads, guide-summary reads and permitted public CompanionHttp calls. It does not instrument every Core/provider/updater request. The bounded guide test independently verifies one query.

All journey medians/p95 (including slower actions), sampled PSS, memory categories, ART GC/allocation deltas, process CPU, frame metrics, UID byte counts and reader counts are preserved. Instrumentation and Compose idling are included. No physical FPS, peak/retained heap, provider TTFF/zapping or credible cold-start TTID is claimed. Earlier untouched-main/016 raw comparisons and the historical n=40 comparison remain intact under the original evidence locations.

## Remaining gates and QA proposal

1. Review the integrated code and native pictures, then complete **all** required CI on the final exact head.
2. Independently check the actual 31 October Växjö-Färjestad broadcaster. The confirmed QA 0.15 picture/audio service test is already accepted; it does not establish fixture ground truth. No new external source or broadcast link is invented here. Unknown/probable states remain honest.
3. After owner approval, the protected QA process must use an approved **main** commit with green exact-main CI (not this unmerged draft head). Proposed inputs to existing `mintv-sign.yml`: `channel=qa`, `version_name=0.16.0-beta.1`, `source_sha=<approved immutable main SHA>`, release notes from `docs/release-notes/v016-cohesion.sv.md`. Existing policy generates the versionCode; package stays `se.jonasschroder.mintv.qa`.
4. Before any QA update, compare the installed QA signer/version with the official signed candidate. Then physically verify review/notes/size, authenticated transfer/verification, permission return, Android installer consent/cancel, process restart and failure recovery. The debug guard is preserved; presentation fixtures do not bypass production trust.
5. Physical Chromecast remote/player/subtitle/EPG/external-app checks and actual decoder/audio/memory remain outstanding. No new candidate has been installed there.

Rollback: revert this phase independently of reused #33-35/earlier cohesion. No database schema, data migration, package identity, production key, Core revision, new permission, paid provider or mandatory hosting change. Main, Stable and owner QA/data remain untouched. No merge, protected signing or release publication performed.

### Final matched results (emulator, not Chromecast)

| Metric | 1080p main -> candidate | 720p main -> candidate |
|---|---:|---:|
| Home median / p95 ms | 1546 / 1717 -> 881 / 1017 | 1516 / 1700 -> 843 / 1066 |
| TV median / p95 ms | 1450 / 1733 -> 807 / 1066 | 1442 / 1649 -> 784 / 950 |
| Sport p95 ms | 233 -> 260 (+27 ms) | 256 -> 267 (+11 ms) |
| Settings p95 ms | 265 -> 249 | 233 -> 250 (+17 ms) |
| VOD Down/Up p95 ms | 181 -> 183 | 168 -> 183 (+15 ms) |
| Full-journey CPU ms | 124594 -> 82779 (-33.6%) | 124323 -> 80962 (-34.9%) |
| Sampled max PSS KiB | 284234 -> 302421 (+6.4%) | 288638 -> 301272 (+4.4%) |
| Frame p95 ms | 123.66 -> 121.20 | 108.50 -> 108.52 |
| App catalogue / guide / public HTTP calls | 1396 / 45 / 0 -> 1395 / 45 / 0 | 1395 / 45 / 0 -> 1395 / 45 / 0 |

Memory investigation: mean native-heap summary grows about **6.7 / 9.0 MiB**, Java about **3.1 / 1.7 MiB** (1080p/720p); code memory slightly decreases. The native libraries are unchanged. No new decoder or catalogue-content cache was introduced. ART reports about **2.4 GB allocated per candidate journey versus 5.8 GB baseline**, with 169/172 versus 408/524 collections; these are cumulative churn, not retained heap. Lower churn/CPU does not prove a memory reduction or absence of a leak. The sampled memory cost remains unresolved pending retained-object/native allocation profiling on approved physical QA.

Sport's +27/+11 ms, Settings 720p's +17 ms and VOD movement's +15 ms are preserved regressions. Extra native recovery/action UI is present, but these one-process software-GPU samples do not establish its causal contribution. Header D-pad, resume and frame p95 are broadly unchanged; no blanket speedup claim. Reader counts show essentially **no query/request-count reduction** in this instrumented scope; the measured gain is reduced work/churn, not claimed EPG/network savings.

### CI follow-up

On `2e13080`, i18n, full build/unit/lint, Home D-pad/Room, signing/upgrade and the complete synthetic performance workflow passed. The persisted production-startup job caught an obsolete expectation: Down into disabled/empty Sport used to remain in the header. The new recovery action is real content, so the regression now requires Down to focus it, no extra persistent menu, and Up back to active Sport. This is a **test-only follow-up**, preserving the production sources/APK used for all measurements. Both complete populated persisted-startup/recreation journeys then pass locally at 1080p and 720p; the new exact head must still complete full CI.

The original before/720p Update frame had not finished drawing its modal. It was independently recaptured using the exact old `016e956` production APK and a bounded synthetic performance profile. The unedited replacement shows the debug guard; its later clock and harness hash are recorded in provenance. The remaining before pictures are unchanged.

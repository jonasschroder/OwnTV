# v0.14 live playback entry reliability

Base: `03525733340d47c7f4b13636dec1b70bc16bb7a8`. No Core pin, schema, package, signing or provider integration change.

## Reproducible failures and evidence

1. **TV grid teardown stops fullscreen.** The shell removes its browse composition when entering
   fullscreen. `SwedishLiveScreen.onDispose` still sees its last `previewEnabled=true` and calls
   `LiveViewModel.stopPreview()`. That calls **Core.stop**, cancelling the tune and stopping both
   engines, not merely a small preview. Home promotion used a different policy. The pinned Core
   regression reproduces launch → pending source lookup → stop → cancelled job/no dispatch, without
   any codec, SVT server or network. The regression matrix then proves browse cleanup cannot stop
   either an in-flight or accepted request for SVT1, SVT2 or TV4.
2. **False acceptance.** `ensurePlayingByIdAsync` returned true after finding a database row and
   enqueueing `ensurePlaying`. Authorization, source lookup and actual engine dispatch happened in
   another job. TV/Guide also opened fullscreen immediately. Missing authorization, an initialization
   exception or a silent resolver failure could therefore open an idle surface. External-launch failure
   was not propagated. Now callers await a validated, actual engine-command acceptance (or successful
   external dispatch). This is explicitly **not decoded-picture success**.
3. **Cancellation boundary.** A new test failed with `expected null, but was:<channel>` when the caller
   disappeared after its engine accepted but before its suspended caller resumed. The corrected gate
   cancels that undisplayed orphan. A completed transfer to fullscreen survives browse disposal;
   superseded/cancelled work cannot navigate, stop the winning request or show an old failure.

These are confirmed app-level races/false-positive paths. They do **not** prove that every reported
real SVT1/SVT2 failure has the same cause. Real provider traces and approved physical QA remain necessary.

## Shared sequence and pathways

`OK → existing source resolver/exact row → frozen profile/source owner → fresh authorized entity +
source → protected request ID → Core engine dispatch → validate acceptance → fullscreen → actual
Core Exo-open/MPV-outcome callbacks`.

| Path | Existing selection retained | Entry/navigation rule |
|---|---|---|
| Home hero, On Now, favourites | Existing logical resolver and saved variant | `playHomeChannel` → shared gate; fullscreen only for IN_APP |
| Swedish TV grid / source picker | Existing HD/FHD/UHD resolver, exact chosen source and logical zap replacement | One cancellable preparation job → `watchFullscreenAsync`; protected browse disposal |
| Full IPTV list and programme menus | Exact clicked row and browse zap context | Same checked async result, including DRM-aware external default |
| EPG/current programme | Exact selected live channel; past catch-up remains the existing archive path | `watchFromGuideAsync`; same source/engine acceptance, immediate intentional history |
| Sport verified/manual channel | Existing fixture, EPG, profile/manual-choice checks | Cancel superseded validation, then common SPORT entry; no hockey data changes |
| Continue/shortcuts/reminders | Re-read stored channel ID with current ownership | `ensurePlayingByIdAsync` is true only for accepted internal playback |
| In-player history/list, previous channel, CH zapping, numeric tune | Exact authorised row; existing bounded zap list and zap debounce | Same gate; numeric lookup also freezes profile, not only source IDs |
| Deep link / multiview promotion | Exact requested channel | Await checked result before fullscreen |
| Back / movie / catch-up / sleep | Existing player/archive behaviour | Release live ownership; explicit stop still stops; browse-only cleanup cannot |

Channel selection stays owned by the existing source resolver. The gate does not apply a second default
that could replace an explicitly picked source. Fresh remote-service tokens may be read for that same
service; source ID/remote identity and playback-relevant stream/header/DRM changes are guarded.
Logo/EPG/label changes and routine source-sync timestamps do not randomly reject a valid tune.
Profile/source changes cancel pending/active live ownership. Delayed history writes keep their original
profile and recheck access. No retry, sleep or new decoder was added to production playback.

## Diagnostics and errors

A bounded 64-event in-memory `LIVE_ENTRY` trace joins the existing diagnostics export. It contains
request sequence, monotonic timing and finite origin/logical/event enums only. SVT1/SVT2 are recognised
canonical families; other names are OTHER. No source/profile IDs, provider text, exception messages,
headers, credentials, tokens or stream URLs are retained. Existing ladder trace is preserved.

REQUESTED/SOURCE_VALIDATED/ACCEPTED are distinct from OPENED_EXO/OPENED_MPV. MPV `isPlaying` is not
used as evidence of a decoded stream. Fallback/exhaustion and cancellation/supersession remain visible.
The observer delegates all engine decisions and outcomes unchanged. Initialization/unavailability and
failed external dispatch use the existing unavailable-channel message and exit an idle live surface;
Core's existing buffering, fallback and final error UI continue to handle stream failures after dispatch.

## Validation

Local validation: **42 distinct focused JVM regressions PASS**, QA app/instrumentation builds PASS, i18n ratchet and two runner/inventory safeguard tests PASS. Native execution and full CI results are linked in the PR.
The matrix covers all 12 origin categories × SVT1/SVT2/TV4, delayed acceptance, competing OK, caller
cancellation, Back/re-entry, unavailable authorization, startup exception and external outcome.
Existing source resolver, preview policy and bounded Room authorization regressions remain exercised.

Added a **real QA Activity/Koin/Room/Exo** matrix using an original local H.264/AAC moving-bars fixture,
then a real native TV-grid OK → fullscreen disposal → Back journey. Existing startup CI requires it at
1080p and 720p **in addition to all eight previous executions**. It refuses non-CI, physical devices
and preinstalled QA before any mutation. The video is synthetic, not SVT/IPTV broadcast evidence.
The native capture must still show running media after browse disposal. No owner-device installation.

Required CI still includes Android builds/unit/lint, navigation/source/profile storage, Core MPV/Exo
regressions, upgrade/signing safeguards, i18n and the additional performance journey. No required
suite is removed or skipped. Native artifact screenshots are evidence of the actual production shell;
no Home/grid redesign or new menu is introduced.

Physical follow-up: reproduce the owner's SVT1/SVT2 URLs from Home, TV, EPG and favourites; compare
request/accept/open/fallback/cancel events, verify picture/sound and Back/replay; test slow provider
resolution, external-player handoff and hardware fallback. No claim of fixing provider/codec/network
issues without that evidence. QA and Stable installations and their private data remain untouched.

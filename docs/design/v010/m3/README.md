# M3 — shared logical channel source and quality

Base: merged M2, `57489af4406e0d3ba7680fd4fe6e27d76ac3ba54`. This implements the
remote contract in [iteration 2](../iteration-2.md), including its save-and-play OK rule.
M1 navigation, M2 geometry/membership, existing player engines, data and M4 subtitles remain intact.

## Ownership and resolution

`SwedishChannelStore` remains the only preference owner: the existing
`mintv-swedish-channels` file, profile ID + `createdAt`, `SwedishChoices.preferred`, and
`sourceId:remoteId` (raw name only when no remote identity exists). No schema or backup-format change.
An observer shares committed choices across Home/TV. A serialized merge writes just one preference,
preserving order, hidden groups, UHD, variant visibility and other channels. Failed commit restores
the previous value, reports failure and never requests playback. A second disk failure still means
durable storage is uncertain; it is never presented as success.

`ChannelSourceResolver` consumes the existing bounded, access-filtered Swedish groups. It does not
search names across the catalog. Saved available versions win, including explicit UHD; otherwise the
unchanged automatic order is HD, SD, FHD, unknown, UHD (UHD requires the existing opt-in).
The picker shows all actually available explicit choices; quality is provider-advertised, not measured.
The chooser sorts actual variants by advertised quality/source/stable key; refresh does not reorder
by ephemeral Room IDs. A missing saved key is retained, with an honest fallback status. Unknown decorated services stay
exact. Duplicate stable keys on different channel rows are excluded rather than arbitrarily selected.
Existing regional/EPG service partitions remain distinct; no prefix or fuzzy-name fallback rejoins them.

`rememberChannelSourceSelection` is a destination-scoped coordinator, not a repository or player.
It reloads local alternatives on channel/source/profile/category/access changes, resolves refreshed
row IDs by the stable key, and revalidates visibility, source, owner and active context before saving
and before calling existing playback. One in-flight latch prevents repeat commits/play requests. The chosen source is also retained in the
existing logical zap queue so its first channel change cannot revert to the previous quality.
There is no provider request, EPG scan, stream probe, extra decoder or automatic stream fallback here.

Home retains the logical focus key and module order when a preference updates; only the actual
channel/EPG entity changes. Home's hero uses its original group provenance, never a reconstructed
name guess. In Swedish TV, opening the chooser cancels the delayed preview action. Saving alone
does not start the newly selected preview; normal preview eligibility returns on browsing another
logical channel. Existing shared playback and ExoPlayer/MPV fallback remain unchanged.

## Remote contract

- Short OK: one existing play callback on release, with the current preferred available source.
- Held OK: approximately 500 ms opens the chooser. Repeats and release do not play. A busy main
  thread still recognizes a long duration at release instead of accidentally taking the short action.
- Focus/navigation/lifecycle/disposal cancels pending holds. The opening cycle is identified by
  Android monotonic `downTime`; old repeat/up events are swallowed even across dialog focus transfer.
  A lost release does not consume the next deliberate OK. Destination-owned cancellation also
  catches navigation that a Home ancestor consumes before the focused button sees it.
- Initial dialog focus: saved available variant, otherwise automatic choice, otherwise first real
  explicit choice. Up/Down browses, OK saves and plays. Right moves to Save without playing; Left
  returns to the marked variant. Back closes without a preference write and restores caller focus.
- Missing/unverified alternatives, removal while open and save failures have explicit recoverable
  states. Removal does not auto-select another source or silently overwrite the saved preference.
- Source ordinals identify real source IDs without displaying private account/source names or URLs.
  No new exported diagnostics contain provider data, credentials, channel names or stream addresses.

## Exact-source boundaries and existing capabilities

| Entry | M3 behavior |
|---|---|
| Home live hero, last-live companion, channel cards | Shared resolver and chooser; saved choices observed without reordering |
| TV / Svenska kanaler | Same resolver/chooser; saved quality reflected in row; existing collection controls retained |
| Raw TV provider rows and raw favorites/history | Existing exact row and menus; no universal redirection |
| EPG programmes, catch-up, recording/reminder actions | Existing exact channel/event identity and long-OK programme menus; no alternative is inferred from a similar name or guide title |
| Sport | Existing exact fixture/broadcaster evidence and explicit match choice win. Shared saved-choice resolution operates only within already verified candidates. Existing stricter unique-source/service automatic policy remains |
| Movies/series | Existing poster/episode long-OK menus retain favorites/download/details. Existing explicitly labelled provider-version chooser remains for the already identified groups; no new fuzzy cross-provider or episode merge |

EPG does not currently expose a separately proven logical-service intent, so its source-exact
behavior deliberately stays unchanged. Sport's resolver never discovers or proves a match. The
existing folder collector groups VOD by positive metadata ID + type + known year inside a provider
folder; it does not establish edition/language/episode equivalence across providers. M3 does not
broaden that contract or transfer progress. These are limits, not silently invented product rules.

Source deletion/reimport with different source IDs is not an ordinary stable-identity refresh; the
old choice remains missing. No promise of cross-device backup/reimport restoration is added.
Existing bounds remain: 100,000 scanned playable rows, 4,096 Swedish candidates, 48 Home channels,
16 visible/prefetched EPG keys. Partial indices are labelled; full TV remains available.

## Validation and visual evidence

Before coding: post-merge Android/i18n workflow checks passed on the exact base SHA. The first
117-case device run timed out in existing `SetupOnceHomeTest.realTvLauncherReturnsToHomeCardAfterBack`.
Logs showed external activity launch without a return to RESUMED, no crash. Unchanged-code rerun
passed all 117 cases. This establishes a non-reproduced activity/focus timing failure, not a proven
production root cause. No test was skipped or weakened. Local base JVM/debug lints also passed.

New JVM cases cover the OK state machine, saved/automatic/missing resolution, refreshed IDs,
ambiguous keys, exact EPG/recording/provider intents, service partitions and verified Sport subsets.
New native cases exercise real Android key down/repeat/up, lost release, Back/focus, save-only,
save-and-play callback count, removal, save error and profile/lifecycle cancellation. Real bundled
Room + isolated preferences test persistence/restart, observer propagation, ownership, hidden/access
filters, refreshed IDs, missing-source retention and in-memory publication followed by commit failure.

The mandatory real QA startup test retains its cold-start/recreation/profile cases and additionally
opens the production chooser on persisted synthetic Home data, saves without playing, reopens at
the saved variant, verifies TV row quality and TV chooser focus, and restores fixture preferences.
It captures `v010-source-picker-WIDTHxHEIGHT.png` and `v010-source-picker-tv-saved-WIDTHxHEIGHT.png`
at both 1920×1080 and 1280×720, alongside unchanged native Home/navigation screenshots.
These are actual native emulator pixels with explicitly synthetic data, not physical IPTV evidence.

Build, startup, device, upgrade, disposable-signing-safety, i18n and tooling results and artifact
links are recorded in the PR. The approved visual target is [quality-picker.jpg](../mockups/quality-picker.jpg). The chooser uses
the existing `OwnTVPopup` Stage host, retaining application/popup density and typography; its own
window hides system bars without changing device settings. It is centered with bounded lazy rows.

Hardware/firmware remote timing, sofa readability/overscan, DRM, real IPTV and engine fallback still
need separately approved physical QA. No owner Chromecast app, setting or data is touched. No merge,
production signing, distribution or installation is authorized by this PR.

The first M3 native run passed startup (including both resolutions, save-only and Home/TV persisted
consistency), upgrades and 122 of 123 device cases. Its new short-OK test used
`Instrumentation.sendKeyDownUpSync`, which constructs separate timestamped events. The source-cycle
guard correctly rejected the mismatched release. New test helpers explicitly inject down/up with
one shared monotonic `downTime`, like a physical remote; stale-release and repeat assertions remain.
[AOSP implementation](https://android.googlesource.com/platform/frameworks/base/+/2f431e264d886fd33d537802095e98e9c3425b7c/core/java/android/app/Instrumentation.java)
explains the helper behavior. Final CI evidence supersedes that development run.

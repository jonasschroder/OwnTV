> Final iteration: [iteration-2.md](iteration-2.md) supersedes conflicting Home layouts, library ownership and picker OK behavior below. The source audit and destination coverage remain valid.

# Source selection and Swedish subtitle assessment

## Current channel/source behavior — reuse, not rebuild

Reviewed source:

- [SwedishChannels](../../../app/src/main/java/tv/own/owntv/features/live/SwedishChannels.kt): recognized
  broadcaster identities, separate region/+1/audio/uncertain identities, advertised quality and order.
- [SwedishChannelStore](../../../app/src/main/java/tv/own/owntv/features/live/SwedishChannelStore.kt):
  `mintv-swedish-channels` SharedPreferences, per-profile owner timestamp, bounded current-source index.
- [SwedishLiveScreen](../../../app/src/main/java/tv/own/owntv/features/live/SwedishLiveScreen.kt):
  `choices.preferred[group.identity]`, explicit variant selection and profile/access rechecks.
- [MinTvContentHome](../../../app/src/main/java/tv/own/owntv/features/home/MinTvContentHome.kt) and
  [HomeDashboard](../../../app/src/main/java/tv/own/owntv/features/home/HomeDashboard.kt): Home's
  history hero, transient `heroChoice`, global choice write for recognized groups, Quick/Now projection.
- [HockeyEpgGuide](../../../app/src/main/java/tv/own/owntv/features/home/HockeyEpgGuide.kt): only picks
  a channel after fixture/broadcaster evidence, and refuses ambiguous sources/EPG service variants.
- [EpgScreen](../../../app/src/main/java/tv/own/owntv/features/epg/EpgScreen.kt) and
  [LiveViewModel](../../../app/src/main/java/tv/own/owntv/features/live/LiveViewModel.kt): exact guide
  channel tune goes through the existing shared controller/ladder. It does not globally replace every
  EPG channel with a Swedish preferred variant.

`SwedishGroup.preferred` already honors an existing explicit stable key first, including an explicitly
chosen UHD entry. Automatic order is **HD → SD → FHD → unknown → UHD**, with UHD excluded unless
allowed. Keep this order until evidence justifies a changed default; a provider's FHD label is not
measured quality or compatibility. Missing selections fall back to current eligible variants. Group
keys intentionally keep unproven regional/time-shift/audio distinctions separate.

Stored variant key is `sourceId:remoteId` (or sourceId plus raw name). That survives normal in-place
app updates and ordinary playlist refresh while IDs remain stable, but is **not a globally durable
provider identity across source deletion/reimport**. Profile `createdAt` prevents accidentally using
old choices when a database profile ID is reused. Do not overstate backup/reimport guarantees.

Current gaps are consistency and presentation: Home's hero can stay on the last-watched exact row
until its local override is chosen, the standard quick order prepends recent/favorite channels,
Guide rows are source-exact, Sports has stricter evidence rules, and choice observation is not a
single shared reactive resolver. A new picker alone will not make every route consistent.

## Proposed shared contract

Keep one per-profile store and add a TV-domain resolver around existing grouping/access logic:

```text
resolve(profileOwner, logicalChannelIdentity, allowedSourceGeneration, intent)
  -> chosen current variant + alternatives + status
status = saved | automatic | saved_missing | ambiguous | unavailable | partial_index
intent = logical_play | explicit_provider_row | verified_broadcast | guide_programme
```

No URL is stored in a new preference or diagnostic. Re-read the actual channel immediately before
tuning; check source/profile/parental/hidden/current identity again after pending IO. Current source
and group generation invalidate cached resolutions. Expose reactive preference updates through the
existing store or a SharedPreferences observer; don't create a second source-default database.

| Entry | Behavior |
| --- | --- |
| Home hero / Now and grouped TV | Short OK tunes saved valid equivalent, otherwise automatic eligible variant; same resolver and exact variant EPG |
| TV → Alla kanaler raw provider row | Respect explicitly named provider row. Context menu distinguishes Play this source from Save as logical default; never quietly redirect an exact row to a different provider |
| Favorites | Do not rewrite existing exact favorites. A logical grouped view may use preferred choice; raw favorite keeps its exact identity and label. This distinction must be approved and visible |
| Guide live logical row | Preferred choice only if actual broadcaster/region/time-shift/EPG service equivalence is established. Re-read current programme/offset for resolved variant; don't show one source's programme while tuning another |
| Guide programme/record/catch-up | Retain programme/channel/recording identity; don't substitute archive capability, schedule or URL based on a quality label. Picker may explain why a version is unavailable for that action |
| Sport | Require current exact fixture and verified broadcast service first. Resolve quality only within proven equivalent eligible choices; retain explicit match choice and ambiguity picker. Never use a global favorite to invent broadcaster evidence |

The strict provider-exact/guide exception is intentional. “Always use my preferred quality” cannot
safely mean swapping a +1/regional channel, different commentary, archive or simultaneous match.
Recommend consistent logical defaults with clearly labelled exact-source actions, rather than
unsafe universal replacement. This is an approval decision before M3, not a hidden implementation
choice in M1.

## Exact picker interaction

1. Short OK on a logical channel/Play action plays immediately using the resolver; no chooser first.
2. Held OK for 500 ms opens one modal. Cancel short Play on keyup and all repeat events.
3. Modal shows channel, existing current provider labels and advertised quality, saved/default state,
   and availability. Never show an unavailable variant as playable or invent missing HD/FHD/4K rows.
   Do not display endpoint URLs, account names or tokens. If provider label itself is private, keep it
   in the local app and omit it from exported diagnostics.
4. Initial focus is the saved valid option; otherwise the current automatic option. Up/Down moves;
   OK saves that actual current variant for this logical channel/profile and returns to caller.
   Saving **does not autoplay**. A later short OK plays it. Back cancels without writing.
5. Zero eligible options: focus a safe Close/Manage sources action. A partial bounded index is labelled
   partial, not “no other versions exist”. Refresh invalidation while open revalidates the chosen ID;
   removed options cannot commit. A missing previously saved choice is reported honestly, then an
   eligible automatic fallback is offered without overwriting the old explicit preference silently.
6. Explicit available UHD remains an owner choice; don't silently reset show-UHD or decoder settings.
7. Retain existing broad VOD context menus. Source/version selection is an additional labelled
   action only for genuine verified versions, not a replacement for Favorite/Info/Download/etc.

Persist via existing `SwedishChoices.preferred` with the same owner/profile/key encoding. No schema
migration is needed for M1–M3. If an additive versioned preference adapter is later required, dual-read
legacy choices, preserve unknown keys and test old serialized fixtures; do not clear old files.
Normal updates preserve app files. Existing derived Home/Swedish preferences must be audited against
Core's .own backup manifest before claiming cross-device restore; do not change the encrypted backup
format in this design. Source reimport with changed IDs fails closed unless an existing unambiguous
canonical identity resolver can prove a match. Do not derive persistence keys from credentials/URLs.

## Movie and episode equivalence

[DiscoveryModels](../../../app/src/main/java/tv/own/owntv/features/discovery/DiscoveryModels.kt) and
[DiscoveryRepository](../../../app/src/main/java/tv/own/owntv/features/discovery/DiscoveryRepository.kt)
use typed cached matches, confidence and identity; similar names/ratings/times alone are insufficient.
Use existing positive verified metadata ID + type and consistent known year before offering a
cross-provider movie-version group. Reject conflicting edition/cut/language, remake and unknown-year
cases; a fuzzy title match can be a suggestion, not an automatic merge.

For series, require verified series identity **and season/episode numbering or verified episode ID**,
with special/double/absolute-numbering ambiguity kept separate. Match series artwork does not prove
an episode equivalent. Preserve actual provider episode ID, queue/history/progress, subtitle recall
and accessibility policy. No cross-provider resume-position transfer in M1; an explicit version choice
may start the actual selected episode only after identity/access revalidation. This needs its own
small tested domain change, not a quick title-normalization function in the new shell.

## Swedish subtitles: what is established

The pinned Core already owns the preference and engines. Do **not** add a second “Swedish series
subtitles” toggle. Settings → Ljud & undertexter should link to the current preferred subtitle language
row; Swedish is stored as `swe` in TrackLanguages. Profile-specific settings include a legacy fallback.
Subtitle online-search language is intentionally a separate filter, not playback preference.

| Source path in pinned Core | Source-level finding |
| --- | --- |
| `core/.../settings/SettingsRepository.kt` near `preferredSubLang` | Per-profile Flow and setter; legacy global fallback; preferred-language backup helpers already exist |
| `core/.../player/TrackLanguages.kt` | Swedish `swe` is offered; `twoLetter("swe")` supplies `sv` |
| `player-core/.../SubtitleLanguage.kt` | Comparison trims case, `_`/`-` regions and ISO 639-1/2 through Locale; `sv`, `swe`, `sv-SE`, `SV_se` compare by Swedish identity. MPV's list builder expands configured `swe` to `swe,sv`; an arbitrary imported regional preference is not equivalently expanded by this builder and needs a test |
| `player-core/.../OwnTVPlayer.kt` preferredSubLang collector | Sets MPV `slang` and permits matching-language subtitles even when audio matches; passes preference to Exo VOD path |
| `player-core/.../ExoSubtitleEngine.kt` applyLanguagePrefs / applyPendingSubtitle | Media3 preferred text language plus explicit supported matching; no random text language when no preferred match. External subtitle selection waits for its labelled track |
| `player-core/.../LivePreviewEngine.kt` preferred language / track updates | Existing Exo live selection path also receives profile preference; don't infer VOD/series correctness from live alone |
| `player-core/.../TrackMemory.kt`, OwnTVPlayer recall | Stores selected language or explicit Off, not per-file track IDs. Series key is intended to carry language between episodes. Manual selection closes pending recall so a late stored answer cannot undo it; absent remembered language leaves global behavior |
| `features/series/SeriesViewModel.kt` / existing queues | Episode loading/progress and series playback identity already exist; reuse them rather than recreating episode loads in the new Film & serier page |

**Potential delayed-track gap to reproduce before fixing:** ExoSubtitleEngine's `applyPendingSubtitle`
sets `subtitleApplied=true` after the first non-empty text list, even if there is no preferred match.
Later `onTracksChanged` returns early on that flag. If an early non-Swedish list is followed by a
Swedish track, automatic selection can remain off. TrackMemory likewise finalizes one recall half
on the first non-empty list. This code path warrants explicit delayed-arrival tests; it is not a
proven failure of the installed app and no engine patch is included here.

Manual choice precedence proposed for verification: explicit current-load track/Off → existing
remembered series-language/Off where available → profile preference → existing engine policy.
Do not auto-enable another language when Swedish is missing, or reset a user-selected Off. Forced
tracks, original-language policy, bitmap subtitles and external files have distinct existing behavior;
keep it until fixtures demonstrate the intended result. Avoid a preference observer repeatedly
stealing a track from a manual selection.

External .srt/.ass/VobSub files are not fetched because the preferred language is Swedish. Reuse the
existing explicit OpenSubtitles/track-add flow. Reattached downloaded subs enter the picker without
unexpected selection; explicit external selection survives the current supported handover. Choosing
an image subtitle may require the existing engine support/handover path; a generic preference must
not invent unsupported image rendering or disable fallback.

## Verification status and proposed matrix

Executed the unchanged pinned **SubtitleLanguageTest (3)** and **TrackMemoryTest (5)** JVM cases:
**8 passed, zero failures**. Those mostly cover English/German and recall rules, not a full Swedish
series fixture. Swedish alias behavior above is static source assessment; automatic selection across
real series episodes, delayed tracks and MPV/Exo transitions is **NOT physically verified**. No device
operation or preference change was performed. Do not market this as a subtitle fix.

Before M4, add failing/passing tests in the owning Core repo and review a deliberate immutable Core
pin update, rather than editing this repository's pinned checkout. Required cases:

- Configured `swe` vs `sv`, `swe`, `sv-SE`, `SV_se`; imported regional preference; blank, unknown,
  `und`, missing lang and misleading display-label-only tracks. Never infer language from title alone.
- MPV and Exo, embedded text and supported image tracks; file preparation before/after preference
  emission; Swedish present initially, list initially empty, non-Swedish first then Swedish later.
- Episode A → B with changed track IDs/order and delayed track discovery; cancelled/superseded loads;
  per-series remembered language and profile isolation; no stale track index from previous episode.
- Manual non-Swedish selection and explicit Off before/after tracks/preference/store arrival; repeated
  track updates do not undo the user. Automatic-selection pending state remains pending only until a
  valid match, explicit override or load termination, not the first arbitrary non-empty list.
- Swedish missing: preserve existing no-random-language policy and visible track picker; supported
  forced track rules verified separately, no silent external download or new subtitle API call.
- Explicit externally loaded Swedish file, cached file reattach, unavailable file, external selection
  through Exo/MPV handover and manual override; test without real copyrighted/private media.

Use small synthetic licensed/local fixtures in disposable Android 14 emulators and then a separately
approved QA upgrade for real Chromecast audio/video/subtitle rendering. Unit tests of language tags
cannot prove actual subtitle cues render or verify series-wide persistence on this device.

[Media3's track-selection reference](https://developer.android.com/media/media3/exoplayer/track-selection)
confirms track availability can change with preparation/item transitions; application-specific
pending/override policy still needs these tests. [Preferred text language parameters](https://developer.android.com/reference/androidx/media3/common/TrackSelectionParameters.Builder)
are constraints, not a promise that every source contains Swedish subtitles.

# Swedish Live TV — Now/Next rows (PR #10)

The Swedish collection now shows logical channel names, existing channel/guide logos,
stored current programme, start/end times, elapsed progress, an optional next title and
the advertised quality of the chosen variant. Provider names remain in **Välj kvalitet/källa**.
Rows use one focus target and do not tune a stream themselves. The right-hand pane is
the existing `LiveStagePane`, with its existing single preview engine and full programme details.

## Data and memory bounds

- `SwedishRowEpgStore` uses the pinned Core's existing bundled Room database and
  `(epgChannelId, startMs)` index. No schema migration, guide download or provider request per row.
- Only visible logical groups and two following groups are considered, capped at **16 variants**.
  One SQL batch performs indexed, `LIMIT 1` probes for current and next programmes per effective
  guide key/time shift: at most **32 small programme summaries**, titles capped at 240 characters.
  Descriptions, complete guide entities and stream URLs are excluded from this projection.
- Queries consider starts within the previous/next 24 hours. Invalid/blank/expired records are
  excluded. An unusually long programme that began over 24 hours ago is conservatively unavailable
  in the compact row; the existing full guide remains available.
- Foreground cache: **32 channel identities**, 60-second maximum positive lifetime, 30-second
  negative lifetime, expiring earlier at programme boundaries. A 5-second foreground clock updates
  progress and wakes at the next known boundary. No background row polling.
- Remote scrolling coalesces for 100 ms; superseded work is cancelled. Local row reads have a
  2.5-second deadline. Cancellation is checked before cache mutation/publication even if an underlying
  read finishes late. Missing or failed EPG does not block selection or playback.
- Room invalidation after guide/channel/source/profile changes replaces the small cache. Profile,
  explicit per-profile customization, source scope, global offset and actual variant identity are
  included in the context. Results are authorized both before and after IO.
- Manual EPG matching uses Core's `epgMatchResolver`. `EpgShift` preserves the existing rule:
  a per-channel shift overrides the global offset, rather than adding to it. EPG IDs are normalized;
  independent XMLTV feeds deliberately need not share the IPTV playlist's source ID.
- Cache keys retain channel ID, source ID and stable provider identity. No sources/qualities are
  merged or deleted. Automatic quality remains **HD → SD → FHD → Unknown → UHD** (UHD opt-in);
  an explicitly chosen variant always wins.

The focused pane retains the existing Core reader, including its existing selected-channel
short-guide fallback. It is debounced separately, immediately clears superseded metadata and
checks full profile/source/customization/channel identity before displaying it. Compact rows
never invoke that fallback. Rows contain brief Now/Next information; the pane retains the selected
programme's full description. The compact row can show **Ingen programinformation** when only
the provider's transient short guide is available in the selected pane; no titles are fabricated.

## Interaction

- Up/Down moves through compact channel rows; focus still selects the existing preview.
- OK retains the existing fullscreen/zap action and original provider-authorized channel entity.
- Back from playback restores the selected logical row, including an offscreen row.
- Left from the selected pane's next-programme action restores its owning channel row.
- **Välj kvalitet/källa**, collection editing and Swedish/Hockey & Sport/News/Children/Other
  categories remain. Full-library FHD/UHD variants are retained.
- Header/category/variant actions use compact controls. Normal rows are designed for approximately
  7–9 fully visible channels at the default 1920×1080 TV scale; larger accessibility/font settings
  may show fewer. Inline variant lists intentionally consume extra space when expanded.

## Reproducing validation

JDK 21 and Android SDK 37, then prepare the unchanged pinned Core revision:

```sh
bash tools/prepare-core.sh
./gradlew :app:testStandardDebugUnitTest :app:testQaDebugUnitTest \
  :OwnTV_Core:core:testDebugUnitTest :OwnTV_Core:player-core:testDebugUnitTest \
  :app:lintStandardDebug :app:lintQaDebug :app:lintStandardRelease :app:lintQaRelease \
  :app:assembleStandardDebug :app:assembleQaDebug \
  :app:assembleStandardRelease :app:assembleQaRelease --max-workers=4
python3 -m unittest discover -s tools/tests -v
```

The ordinary PR workflow also executes `HomeRegressionSuite` on a disposable API 34
1080p x86_64 TV emulator. It requires every existing regression and the five new storage/TV
regressions to actually execute, without failures, errors or skips. New JVM tests cover cache
bounds/expiry, midnight transitions, stale context, cancellation and rapid focus changes. Real
bundled SQLite fixtures cover manual mapping, independent XMLTV source IDs, per-channel/global
offsets, authorization on both sides, resynchronization and **200,000 programmes / 500 channels**.
The query result is asserted to retain only 16 rows / 32 programme summaries. Logged query times
are emulator fixture measurements, not Chromecast performance claims.

The screenshot artifact contains `pr10-swedish-live-*-synthetic.png`: actual production row,
frame and selected-pane components, Swedish synthetic titles, missing EPG and quality selection.
The existing preview singleton remains idle: no stream, provider artwork, credentials or decoder
is exercised. A test verifies 7–9 completely visible normal rows. Remote tests exercise OK,
Up/Down, manual UHD selection and the production focus-restoration helper after a synthetic
fullscreen callback. They do **not** prove real playback promotion/return works on Chromecast.

## Physical Chromecast checks still required

- Owner's actual XMLTV/source/manual mappings, live resync and programme transition timings.
- Chromecast remote auto-repeat, category/quality editing, Guide/sidebar return and Back after
  actual fullscreen playback; switching profiles while fetching metadata.
- Single preview stream, provider session limits, audio/video and fullscreen promotion.
- Real 2 GB device RAM/GC and responsiveness during prolonged scrolling, sync and playback.
- Logo contrast, text readability, overscan, alternate zoom/font sizes and 4K display output.

No installed settings, profiles, IPTV data, package identities, HOME registration, player code,
permanent signing or update/distribution security are changed. Local/CI debug and unsigned release
APKs are validation outputs, not approved installers. This PR does not authorize signing,
distribution, installation or merging, and must not replace the populated installed QA app.

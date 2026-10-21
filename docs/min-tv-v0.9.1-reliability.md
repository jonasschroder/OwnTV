# Min TV v0.9.1: guide and playback reliability

Development starts from accepted Stable v0.9.0, main `09eb15ddbb532d345ca03b8d1ecabe59364c5d6b`.
This PR changes no Home layout, package identity, database schema, Core pin, production key,
version counter, signing or distribution safeguard. It does not operate either installed Chromecast app.

## Guide: cause and solution

`EpgScreen` updated its clock every 30 seconds, but loaded its database window only on screen entry
and data/settings events. A screen left open through the night could retain yesterday's scroll and
cursor. `selected` separately retained a channel/programme pair; only the focused `GuideRow` updated
it. Now changed scroll/cursor while the toolbar owned focus, leaving the header's programme behind.
An asynchronous full-description producer could also momentarily retain the previous programme.

Use one absolute-time cursor for cells and details. Derive details from that cursor and the selected
row even while a toolbar owns focus; key the row and full-description state by its actual identity
and cache revision, cancelling superseded reads. Empty rows/gaps clear details instead of keeping an
expired programme. Now and the day picker update the same cursor as keyboard browsing.

The foreground lifecycle loop refreshes immediately on RESUMED, then ticks every 30 seconds. Live
mode follows current time and refreshes the query window at most once per half-hour slot (plus
resume/explicit Now and existing data events). It stops on lifecycle pause. Manual historical/future
browsing freezes the cursor and viewport. Every guide keydown renews a monotonic activity timestamp;
wall-clock jumps and midnight alone never cancel an active manual selection. After **30 minutes
without guide input**, the next tick/resume returns to Now. That threshold is an explicit product
choice, not a measured device requirement. A short resume preserves manual browsing. Query-window
changes preserve the absolute viewport anchor while manually browsing instead of jumping to Now.

Tests cover exact programme-end/midnight, long and short resume, stale/updated programmes, empty
gaps, Now without row focus, historical and future browsing, and wall-clock changes. Compose tests
exercise the production selection reader with toolbar focus and delayed row/details reads. They do
not simulate real decoder behavior or claim a complete physical D-pad acceptance of this candidate.

## Playback: measured cause, no fallback change

Sanitized acceptance timeline (21 October 2026, device local time):

| Time | Observation |
| --- | --- |
| 11:08:41.533 | ExoPlayer-first tune requested |
| 11:08:42.892 | ExoPlayer opened |
| 11:08:43.195 | Fallback to MPV + TS: **no decodable audio track** |
| 11:08:43.199 | MPV start requested |
| 11:08:43.721 | MPV opened |

The physical observer confirmed picture and sound. Core's `LiveExoWatchdog` waits for tracks to settle,
then checks `audioUnsupported`. This is a supported audio-decoding fallback, not an opening timeout
or evidence that the later video timestamp warnings triggered the handover. The capture does not
establish the stream's exact audio codec or whether every alternative Exo audio track was unavailable.

Core already cancels old watcher/resolve jobs, stops Exo, waits for decoder release, rechecks current
tune ownership and opens MPV in a separate coroutine so the watcher does not cancel its own handover.
Keep that path, its bounded ladder and MPV intact. The later `VideoRenderQualityTracker` timestamp
jumps and missing presentation fences warrant follow-up for visible jitter, but are not a crash or
proof of failed playback. MPV's unavailable optional title/lang/bitrate/display properties and
OpenGL swap warnings are diagnostic noise in this capture, not the demonstrated fallback trigger.

Add a **32-event in-memory diagnostic trace** around the existing Core Host callbacks. It records
elapsed-realtime milliseconds, fallback/exhausted/handover/engine-start callback, engine when known,
and an allowlisted audio-undecodable cause (otherwise OTHER). Engine-start means the existing Core
callback fired, **not** proof that a first frame/audio arrived. It is included in the existing crash
report export from More → Error log; nothing is uploaded automatically. No URL, channel/source name,
credential, token or arbitrary exception text is stored in this additional trace. The Core callbacks
still receive their original arguments, preserving existing behavior. Existing Core diagnostics are
not rewritten or claimed to have a new privacy guarantee by this PR.

Next approved QA check: compare successful/failed tune timelines, rapid channel changes and Back,
real audio tracks, handover duration, and visible frame/audio continuity. Do not force failures on
the protected installation or disable the working fallback on the strength of system warnings.

## EPG freshness: verified bug and remaining uncertainty

Worker-based refresh already uses `markError` on failure, preserving `lastSyncAt` as the last success.
`runSemiAutoEpgSync` instead called `setSynced(now, error)` in its failure branch. That makes a failed
manual/onboarding attempt appear fresh and can postpone staleness-triggered retries. It also dated
success at request start. The app now records success at completion and records only an error on
failed download; cancellation writes neither. Focused coroutine tests cover these three paths.
Existing incorrect historical success timestamps cannot be reconstructed and are not rewritten.
This bug can misreport freshness; it does **not prove** the historical missing-programme root cause.

Existing matching/retention pipeline:

- Playlist channel `epgChannelId` and persisted manual/smart overrides feed Core's GuideReader and
  live EPG reader; comparisons normalize case/whitespace. Overrides are resolved across playlist
  reimports and current profile/source restrictions. Standalone feed IDs and playlist IDs both matter.
- XMLTV downloads retain channel definitions for matching but filter programme storage to needed
  playlist IDs plus persisted overrides across profiles. A later smart match may top up from cache.
- Guide retention, per-channel/global offsets, empty cached reads, actual time gaps and provider
  fallback can each produce no current programme even when the feed was downloaded successfully.
- The guide's `channelsWithGuide` is a stored-programme existence filter, not a current-programme
  coverage metric. Do not present that count as 'all channels have current EPG'.

### Proposed lightweight view (not implemented in this PR)

Place **EPG status** under More → Settings → App → Diagnostics, with a link from Sources & guide →
EPG sources. Keep the premium Home unchanged. Reuse the existing source store, sync scheduler,
profile restrictions and match resolver; no new service, network polling or automatic rematching.

| Field | Definition |
| --- | --- |
| Last successful refresh | Per-feed completion time from `lastSyncAt`; never an attempted refresh time. Show Never/Unknown explicitly. |
| Freshness | Per feed: refreshing, never, fresh, stale, or clock-inconsistent. Compare success age with that feed's configured refresh threshold; if automatic refresh is off, show age and Off rather than inventing a promised schedule. |
| Matched channels | Unique visible eligible Live channel identities in the active profile/sources with a resolved normalized ID present in the allowed stored guide data; include manual overrides, do not double-count multiple feeds. |
| Channels without a match | Eligible total minus matched; separate from matched channels whose data has expired or has no programme now. |
| Current coverage | Matched channels with a programme at current time, honoring offsets; show matched-but-no-current-programme separately so schedule gaps are not called unmatched. |
| Error state | Fixed localized category (offline, HTTP/auth, parse, cancelled, storage, unknown), last attempt status and retry/refresh activity, without raw source URLs or exception bodies. A last good refresh can coexist with a failed latest attempt. |

Compute a consistent snapshot on view entry/manual refresh, off the main thread, with cancellation
on profile/source changes. Use indexed batched queries; avoid one request/query per channel or
materializing all programmes. Before implementing, add Core aggregate queries/resolver parity tests
for duplicates across feeds, profile restrictions, reimports/manual overrides, offsets, no sources,
partial refresh failure, expiry, future clock values and large libraries. Do not infer counts from a
capped 20,000-row guide UI list. Existing schemas do not consistently retain last-attempt time across
all sync paths; show Unknown until a common typed outcome journal is added, rather than deriving it
from last success or arbitrary exception text. Keep translations in the established resource workflow.

## Validation and rollout limits

No candidate APK is installed on the Chromecast. Stable and QA data remain protected. CI's existing
Android 14 disposable TV emulator runs the new Compose cases through the strict no-skip inventory.
All existing startup, Home, signing-policy and upgrade-security gates remain enabled; CI fixture
signatures are disposable test keys, never production signing. A later QA upgrade, physical overnight
resume/EPG/audio check, permanent signing and distribution each remain separate owner-approved steps.

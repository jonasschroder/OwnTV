# Min TV v0.8 Home and update presentation

Base: merged PR #10, `09273507830bcb58ba6275e9c992d7bf23d45156`.
Development version: `0.8.0-beta.1` (environment override still supported). No version-code sequence,
package ID, permanent key, distribution workflow, launcher declaration or database schema changes.
Published QA at investigation: `qa-1000007`, version `0.7.0-beta.1`. This PR does not publish a successor.

## Composition and focus

1080p layout in existing mockup pixels (`mpx` scales with the existing TV theme):

```text
┌─ existing navigation ─┬──────── existing clock/source/profile controls ────────┐
│                      │ Min TV Test                 Customize · real version │
│                      │ ┌ LAST WATCHED ─────────────────────────────────────┐│
│                      │ │ Channel / current programme │                    ││
│                      │ │ times / progress / next     │  ONE 16:9 preview   ││
│                      │ │ Continue / Live / Guide     │  or logo fallback   ││
│                      │ └───────────────────────────────────────────────────┘│
│                      │ ┌ Personal hockey ─────────┐ ┌ ohnePixel ──────────┐│
│                      │ │ fixture / channel/action│ │ status / action     ││
│                      │ └──────────────────────────┘ └──────────────────────┘│
│                      │ Your channels →                                    │
│                      │ Continue Watching →                                │
│                      │ On TV now →                                        │
│                      │ Series / Movies →                                  │
│                      │ Installed supported apps                            │
└──────────────────────┴────────────────────────────────────────────────────┘
```

The second-iteration header shares the measured shell status band where width permits.
Content end inset is 56 mpx, row gap 18, hero horizontal/vertical padding 26/20,
and header minimum 60. The usual hero is 294 mpx high. Only an active preview
(including real loading, which needs a surface to prepare) allocates its 436 mpx
wide 16:9 slot; disabled, blocked or failed preview uses the whole tonal programme
surface. Continue is primary, TV Channels secondary; Guide/quality/customization
remain in the focusable More menu. Programme titles use 34 mpx with two bounded
lines, metadata 18, channel 23, eyebrow 14. Compact companions and visible quick
channel tiles complete the first viewport. See the second-iteration section below
for measured screenshot assertions and the first/second comparison provenance.

Up/Down follows **actual lazy item positions**, including reordered/hidden rails, not
enum order. Removal is compared with the last committed focused section before Android
can redirect native focus; the missing section restores the hero only while Home is active. Companions share a physical row; Left/Right moves between their actions.
Hero OK continues the accessible watched channel; it never chooses a merely highlighted
favorite. Header version opens the same About/Update dialog used by Settings. Back closes
modals and restores the originating section; fullscreen callbacks retain existing shell
restoration. Profile changes recreate presentation/cache/section state.

Hero and companion pair are fixed layout anchors; remaining rails can move up or be hidden.
Legacy visibility names and profile-createdAt ownership are retained. New `NOW` visibility
and order fields are additive; changing visibility preserves order and vice versa. Reset
is explicit and affects Home layout only.

## Reused domain owners

- `HomeViewModel` / Core `HomeFeedReader`: actual profile-scoped, timestamp-ordered Live
  history, favorites and real movie/episode continuation. The existing shell refreshes the
  feed on Home entry and fullscreen return; the foreground lifecycle also refreshes on return
  from an external player. No duplicate history-driven feed observer is added. No focus action writes watch history.
- The hero keeps the actually watched source/quality, independently of older automatic
  preferences. Its explicit source/quality picker overrides only that watch and still saves
  the shared preference for automatic rails. A new history item resets the local override.
- `SwedishChannelStore`: existing lightweight logical groups, ordering and explicit
  source/quality settings; HD → SD → FHD → Unknown → UHD policy is unchanged.
- `LiveViewModel.homeChannel`: revalidates access before display and again before playback.
- PR #10 `SwedishRowCache` + `swedishStoredRows`: max 16 requested keys and 32 tiny summaries,
  local indexed Now/Next, full source/stable-key/profile/mapping/offset context; no guide-wide
  load or per-card provider HTTP. These bounds cover the new row cache; the unchanged Core
  Home feed also retains its existing configured six-hour favorite-guide slice. A single visible hero synopsis reuses Core’s indexed
  `GuideReader.description` on programme changes, capped to 800 characters, with timeout,
  cancellation and fresh channel/profile authorization on both sides of the read. Current titles expire at programme boundaries. Empty data
  displays honest placeholders. Home requests visible cards + two nearby cards only.
- `HomeLivePreviewController` and existing singleton Live engine: hero only; deliberate
  hero interaction, debounced muted preview, no preview starts by scrolling rails. Paused
  when hidden, modal, backgrounded or another screen is open. Existing fullscreen promotion
  path remains.
- `MinSportCompanion`: current evidence, opt-in external readers, manual channels and
  diagnostics retained. Standings are no longer rendered/requested. Legacy saved TABLE
  enum state is read compatibly and redirected. Cached fixtures retain update labels.
- `TwitchCompanion`: same device grant, encrypted tokens, public Client ID and supported
  external-player picker; compact presentation only. Unknown is distinct from offline.
- `rememberDiscovery`: existing provider-first bounded shelves/metadata with visible-only
  network refresh. Existing detail, source-picker and playback callbacks remain.
- `MinTvUpdater`: same authenticated metadata and APK/certificate verification, durable
  installer session, foreground transport and cooldowns. Foreground action jobs are owned by
  that same manager, so closing a dialog does not cancel a download; background transport
  cancellation still applies. Presentation does not invent
  trust, progress, completion or release notes.

Retired: old video-on-left favorite-driven hero composition and tall stacked Home companion
panels. Unchanged: full Live TV/EPG/player, Movies/Series pages, sidebar/Settings structure,
OAuth, installed data and update infrastructure. `UpdateStatusToast` is the legacy Core UI;
Min TV continues to use its existing host adapter, not a second updater.

## States and performance

Missing history, removed/inaccessible channel: honest no-history hero with Live TV entry.
Missing EPG: channel remains playable. Missing artwork: logo/text fallback. Hockey source
failure never blocks Home; local EPG/manual browsing remains available. Twitch offline and
unknown are distinct; missing client uses the existing picker fallback. Empty rails hide.

No streams/credentials/tokens in fixtures. Measurements and final test evidence are recorded
in the PR; emulator timings are not Chromecast memory/decoder benchmarks.

## Validation commands and hardware checklist

Use JDK 21 and the repository's pinned Core source (`bash tools/prepare-core.sh`).

```sh
./gradlew :app:testStandardDebugUnitTest :app:testQaDebugUnitTest \
  :OwnTV_Core:core:testDebugUnitTest :OwnTV_Core:player-core:testDebugUnitTest --max-workers=4
./gradlew :app:lintStandardDebug :app:lintQaDebug :app:lintStandardRelease :app:lintQaRelease --max-workers=4
./gradlew :app:assembleStandardDebug :app:assembleQaDebug \
  :app:assembleStandardRelease :app:assembleQaRelease --max-workers=4
python3 -m unittest discover -s tools/tests -v
bash tools/run-tv-regressions.sh
```

Physical Chromecast checks (not validated by synthetic emulator fixtures): actual last-watched
channel after cold launch; provider single-session limit; muted preview; fullscreen promotion,
zapping and Back; device remote repeat behavior; readability at normal seating distance and
system scaling; real IPTV EPG sync and large-library RAM/GC; hockey/manual selection; connected
Twitch client return; authenticated update check/download/verify, Android confirmation,
cancel/permission/storage errors and post-upgrade data persistence. Only install approved
permanently signed updates after separate owner approval; never uninstall to test this PR.

## Screenshot provenance

`HomeDashboardPresentationTest` renders the real dashboard frame/hero/quick companion cards,
continuation and discovery rows, and `UpdatePresentation` with synthetic records. It does not
instantiate production `HomeViewModel` or resolve live provider sessions. The `before-hero`
screenshot substitutes only the exact hero composable copied from merged main `09273507`
inside the same test frame; it is a **hero component comparison**, not a screenshot of the
whole previous Home. Other state screenshots are the current production UI with fixtures.
The clock uses the emulator’s actual time; synthetic programmes are relative to that time.
Hockey faceoff rendering explicitly uses Europe/Stockholm, independently of emulator timezone.
Synthetic poster files are generated only in the disposable test app cache; Coil renders them
in the real cards, with missing-artwork examples retained. Both the runner and the fixture
require the actual emulator property (`ro.kernel.qemu=1`), in addition to the explicit
instrumentation argument. The 720p capture temporarily changes **only that emulator** display size and
restores it in `finally`; it never changes a physical Chromecast setting.

## Proposed release notes (not published)

- A new Live TV first Home with last-watched TV, compact hockey/Twitch and real Now/Next.
- Clearer version, authenticated update availability and download/install feedback.
- Profile Home visibility/order retained; no clean reinstall or data reset.
- Hockey standings removed; local EPG and manual channel lookup remain.

## About/Update modal

The existing TV popup is 1080 mockup pixels wide. Installed identity/version/build and update
channel appear first, followed by the verified candidate and last successful check time.
Only authenticated notes are shown, in a bounded 230-pixel focusable scroll area. Close is
the conservative default focus; Right reaches the applicable Check/Update/Permission/Confirm
action. Download percentage, actual APK verification and post-install version confirmation
have distinct truthful labels. Closing the window retains active/verified work; acknowledged
terminal failures/cancellations retain the original cleanup and retry behavior. Routine
checks never open another focus-stealing overlay. The dialog retains the caller's resource
locale, including a per-app language override, across its Android window boundary.

The presentation fixture includes the production clock/source status cluster and sidebar
with synthetic callbacks; it does not instantiate the complete OwnTVShell or HomeViewModel.
Sidebar/profile integration and provider playback remain on the physical-device checklist.

## Second design iteration — owner review on 15 October 2026

Review: https://github.com/jonasschroder/OwnTV/pull/11#issuecomment-6200839345.

The owner-reviewed first-iteration capture is retained unchanged under
`docs/images/v08/v08-iteration1-{1080p,720p}-synthetic.png` (source `5d1b5eb3`).
It is a real first-viewport capture, unlike the separate older copied-hero fixture.
The screenshot artifact includes `v08-review-compare.html`: extract the archive and
open the file for a side-by-side comparison of actual captures at both resolutions.
No comparison image is synthesized or redrawn.

The compact hero has a 254 mpx minimum content area and 20 mpx vertical padding:
294 mpx normally, instead of the roughly 510 mpx first-iteration panel. Natural
height may grow for large fonts; there is no hard clipping. Programme titles have
two lines; next programme uses up to three lines in the preview-off information area
and one bounded line alongside an active preview. Channel/source labels are bounded. A single
primary Continue action is followed by TV Channels and a focusable More button.
More opens the existing TV popup style with Guide, Source/Quality when available,
and Customize. The existing synopsis is available in that popup. Back restores the
More control; actions continue through their existing callbacks. A real active
preview alone allocates a 436 × 245.25 mpx, 16:9 video slot. Disabled, unavailable
or failed preview allocates no video slot and never composes its content. The full
programme surface then uses a restrained tonal gradient and the existing channel
logo, when available. The right-hand information area shows the actual stored next
programme instead of a disabled video frame. The progress line is capped at 460 mpx.
Action heights are 50/42 mpx and grow with user font scale rather than inheriting
Material’s larger minimum. No artwork or programme is invented.

The supporting strip is normally about 112–120 mpx high. Hockey uses a warmer teal
surface when a verified fixture is near, while unknown fixture/channel labels stay
explicit. Manual/EPG evidence and cached-update labels remain available. Offline or
unknown Twitch gets less width and a quiet status/settings tile. Live Twitch gets
more width for its title, category/viewer count and Watch action. All card actions
remain horizontal so the existing Up/Down section traversal cannot skip a nested
button. No reader, polling or authorization policy changes.

Header branding/profile, Customize and actual-version chip now occupy the same top
band as the existing source/clock cluster when at least 640 mpx is available. The
shell supplies its measured cluster width and height. With a large status cluster
or a wide sidebar the header uses a second band; the content inset becomes the
maximum of status and header bottoms plus spacing. The header stays accessible
while the list scrolls. Existing section indices retain a zero-height scroll anchor;
18 mpx row spacing replaces 26 mpx. Thus the normal 1080p first viewport includes
complete useful quick-channel tiles rather than only their heading. Exact bounds
are asserted and reported by the native fixtures; long-text/wide-status and 720p
captures check the constrained case, including long titles and overflow in 720p,
without claiming all physical font scales. Screenshot capture waits two real
animation frames after Compose idle to avoid presenting the previous UI state.

The new fixture renders the existing production `StageStatusCluster` and `StageRail`
with the same measured-safe-area contract and synthetic navigation callbacks. It
asserts sidebar return, overflow/Back/guide/quality, no disabled-video allocation,
quick-tile visibility, async companion focus and profile re-entry. This is still not
the complete database-backed `OwnTVShell`/HomeViewModel or a real decoder session.
Physical Chromecast playback, provider limits, actual font/display settings and
full application integration remain on the hardware checklist above. Installer
security, keys, identities, persisted preferences, data, launcher and updater logic
are unchanged by this second iteration.

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

Existing shell safe areas and status controls remain. Content end inset 56, row gap 26,
hero content padding 28, preview inset 16; header minimum 64. Hero splits approximately
49% text and 51% 16:9 preview; its height follows content, never a fixed full viewport.
Navy background, calm lighter surfaces, teal focus/accent; titles 31–36, section titles
25–27, body 18–21, secondary text 16–18. Ellipsis/line limits bound verbose data.
The initial viewport prioritizes the header, hero and companion summaries, with the
next rail indicating further content. The same layout scales at 720p.

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
  history, favorites and real movie/episode continuation. History observation refreshes
  the feed on returning from playback. No focus action writes watch history.
- `SwedishChannelStore`: existing lightweight logical groups, ordering and explicit
  source/quality settings; HD → SD → FHD → Unknown → UHD policy is unchanged.
- `LiveViewModel.homeChannel`: revalidates access before display and again before playback.
- PR #10 `SwedishRowCache` + `swedishStoredRows`: max 16 requested keys and 32 tiny summaries,
  local indexed Now/Next, full source/stable-key/profile/mapping/offset context; no guide-wide
  load or per-card provider HTTP. Current titles expire at programme boundaries. Empty data
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
The 720p capture temporarily changes **only the disposable CI emulator** display size and
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

The presentation fixture includes the production clock/source status cluster and reserves
the existing sidebar inset; it does not instantiate the full shell/sidebar or HomeViewModel.
Sidebar/profile integration and provider playback remain on the physical-device checklist.

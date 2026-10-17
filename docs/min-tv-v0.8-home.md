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
- `TwitchCompanion`: same device grant, encrypted tokens and public Client ID. Compact Home opens the
  official installed Twitch app; optional alternative players remain in Settings. Unknown is distinct from offline.
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
unknown are distinct; an unavailable official app offers concise guidance and an explicit Google Play action. Empty rails hide.

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
menu keeps the existing bounded local EPG reader active while the preview stays
paused; foreground and profile/source authorization still apply, with no network
fallback or background reads. A real active
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

## Owner feedback, 17 October: configure once

Home's compact hockey and Twitch cards now keep configuration off the daily screen. Hockey shows the
next relevant validated fixture, its time, and only a locally matched/manual channel or an unexpired
verified linear broadcast assignment. **Se matchen** appears only with an authorized playable local
choice (or a picker of verified candidates). The card surface always opens Matchcenter; the separate compact watch action plays or opens verified choices. Manual channel choice
remains there. Empty team selection leads to Settings. The permanent **Andra kanaler** Home button
is removed. No new schedule/broadcast requests or matching assumptions were introduced.

Twitch Home shows ohnePixel's reliable live/offline/unknown state and a single **Öppna Twitch**
action in every state. It opens the official app's normal interface, not a promised stream.
Authentication and live status never gate that launch; no first-use setup prompt or alternative-client
picker replaces it. The first viewport retains the hero, quick channels, real version/update entry
and profile layout. Existing app shortcuts retain their visibility/order and can be hidden/moved in Settings.

Persistent controls live in **More → Settings → Content & metadata**:

- **Min hockey**: the existing profile-bound followed-team picker, Home prominence/spoiler preferences,
  existing experimental source flags, access-block notices and local diagnostic/export panel.
- **Twitch & playback**: existing device authorization, disconnect and advanced public Client ID override,
  optional saved alternative TV client, explicit alternative launch, installed-client recheck and Google Play listings.
- **Customize Home**: the actual Min TV dashboard's per-profile visibility/order, optional app shortcuts,
  preview toggle and explicit layout reset. This is distinct from upstream Home's legacy layout page.
- Playlist/EPG source management stays in the existing **Sources** group.

The same Settings root/search and subpage focus restoration handle these destinations. Home's
Customize entry opens the canonical Settings subpage. Back through the Settings root returns to
Home when entered there; existing More/EPG/source entry points keep their own behavior. No sidebar
redesign or Settings group renumbering. Existing profile owner timestamps, stored keys, channel
identities/quality, last-watched history and EPG are preserved. The install-local, nonsecret
`twitch-player-package` preference is preserved for optional Settings alternatives. Any previously
stored `twitch-setup-seen` is left untouched but is no longer read or written. Account
storage and Client ID remain install-scoped as before; team/layout preferences remain profile-bound.
QA/Production storage stays separate by the existing application IDs.

### Final owner decision: open the installed official Twitch app

The [17 October final owner comment](https://github.com/jonasschroder/OwnTV/pull/11#issuecomment-6235025348)
supersedes the special-client default. Twitch's own
[Android package reference](https://github.com/twitchdev/deeplink-unity/blob/385e68f281db4a1867cfb850aeac12e503ff8dcd/Assets/Scripts/DeepLinkTest.cs#L49)
identifies `tv.twitch.android.app`. The [official Play listing](https://play.google.com/store/apps/details?id=tv.twitch.android.app)
and Twitch downloads/help pages were blocked by this cloud environment's HTTP CONNECT filter;
the first-party GitHub reference was retrieved successfully. No third-party APK was downloaded.

`TwitchPlayback.officialAppIntent` reuses `MinTvExternalApps.launch`, as SVT Play does: ask
PackageManager for that exact package's Leanback launcher, then its ordinary launcher if necessary.
No activity name is assumed. Re-resolve the actual installed activity and require the exact package,
enabled application/activity, exported entry, target SDK ≥23 and any required permission. Opening is
explicit, carries no URI/OAuth credentials/stream parameters, and catches removal/disable races.
A package ID identifies the intended app, not proof that the owner's installed version was inspected.
Only a physical-device check can confirm that version's behavior. There is no implicit browser or
arbitrary handler fallback, automatic store opening or installation.

If launching fails, the card opens a small explanation with Close focused and an optional explicit
**Hitta Twitch på Google Play** button. This uses only `com.android.vending` and the official package
listing, verifies the enabled/exported permitted handler and handles absent/blocked Play safely.
Back dismisses guidance or returns from the external app to the originating Home card. Home remains
independent of IPTV sources, saved alternative-client preference and Twitch authorization/status.

Optional S0undTV (`com.s0und.s0undtv`) and SmartTV Client (`com.fgl27.twitch`) controls remain in
Settings, with their existing preference and explicit Open action. S0undTV's documented
[public v1.5.10 contract](https://github.com/S0und/S0undTV/releases/tag/v1.5.10) remains verified at
runtime; otherwise clients use a disclosed manual TV launcher. No guessed URI is introduced.
Their existence never changes Home's official-app target. Account authorization, advanced public
Client ID, secure token storage and disconnect remain in Settings without stream-status polling.

The APK verifier's exact visibility list adds only `tv.twitch.android.app` to the previously reviewed
nine packages. It continues to reject broad `QUERY_ALL_PACKAGES`, unreviewed packages/providers and
missing required client queries. Package IDs, permanent keys, counters, update trust, EPG/player,
installed data and profile layout are unchanged. The translation debt baseline remains unchanged;
only the new technical package constant and removal of an unused preference reference are classified.

### Validation and physical limits

The mandatory emulator suite retains earlier auth, secure-storage, Home/focus, EPG and alternative
public-handler cases. Added/updated tests cover real PackageManager official-style TV entry launch
using the test APK, absent/disabled launchers, no URI/extras, actual external activity Back with
lifecycle-driven focus restoration, offline/unknown and unauthenticated launch, saved alternative
isolation, no Home client discovery, and explicit-store failure/guidance. Tests refuse physical devices.
These fixtures do not masquerade as the actual Twitch package or prove Twitch video playback.
Home 1080p/720p live/offline captures and Settings screenshots regenerate with synthetic data.
Final-head test results and current screenshot artifact links are recorded in PR #11.

Before this final simplification, head `3ae80808` passed all eight checks, including 60 TV tests,
standard/QA app JVM 435 + 435, Core 939, player 271 and tooling 48. Those results are historical,
not evidence for the new change. Current tests/CI must pass again before review.

Physical acceptance remains: confirm the official Twitch package on the owner's Chromecast,
press **Öppna Twitch** while live/offline/status unavailable, confirm its usual TV interface opens,
and press Back to return to the same Min TV card. Optional read-only Mac/ADB diagnostics, only if
already connected: `adb shell pm path tv.twitch.android.app` and
`adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER tv.twitch.android.app`.
No physical device was connected or modified by this task. Google Play availability, actual external
playback, remote behavior and existing IPTV/provider limits remain hardware acceptance items.
No merge, protected signing, owner installation or publication is performed.

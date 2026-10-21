> Final iteration: [iteration-2.md](iteration-2.md) supersedes conflicting Home layouts, library ownership and picker OK behavior below. The source audit and destination coverage remain valid.

# Architecture, interaction and staged delivery

## Decision summary

Four primary destinations: **Hem · TV · Sport · Film & serier**. A separate upper-right **Profil**
control opens profile/library/settings, always reachable regardless of content capability. Search
belongs to TV and Film & serier; Settings retains its own search. Sport is optional content, not a
new network integration. Use the complete [destination map](destination-map.md) as the removal gate
for the visible More section.

Recommend Home **Lugn start**. It preserves a clear first Play action while freeing the left rail's
width. Reject an automatically opening/dwell-switching nav: brushing past Sport must not trigger
fetching or a decoder handover. Choose explicit OK, or Down to commit-and-enter, as below. This is a
product choice to validate with the remote, not a claim that Google mandates this exact behavior.

## Current ownership and proposed boundaries

Current shell owns `ShellLayer { SIDEBAR, RAIL, CONTENT }`, `PlayerMode { NONE, FULLSCREEN, MINI,
AUDIO }`, `railSelection`, `searchReturn`, entry callbacks, Settings return flags and many legacy
`MainSection` routes. Simply moving its rail into a Row would retain ambiguous focus layers.

Use a TV presentation coordinator around the existing owners:

```text
App/profile/onboarding gate (unchanged)
  └ Native Compose TV ShellCoordinator
      ├ Header: primary tabs + Profile control
      ├ DestinationHost / SaveableStateHolder
      │   ├ Home: bounded history / Swedish-group / resume projections
      │   ├ TV: Swedish / full list / favorites / guide / recordings + local search
      │   ├ Sport: existing hockey / teams / matches / channel evidence / Twitch
      │   ├ Film & series: existing Movies / Series / Discovery / library + local search
      │   └ Profile hub: existing profiles, library, settings, data, diagnostics, updater
      └ Shared player / overlays (existing engines, focus caller and exact playback identity)
```

Do not instantiate a new LiveViewModel or decoder for each destination. The shell remains the
owner of one shared LiveTuneController and player-mode/zap-source routing. Separate visual focus,
committed destination and playback identity. Header focus does not imply a new playback session.

Proposed types (not added by this design PR): `PrimaryDestination`, `Route`, `OriginRoute`,
`FocusBookmark(profileOwner,route,subview,stableItemKey,rowKey,viewportAnchor)`, `OverlayCaller`,
`ResolvedLogicalChannel`. A bookmark stores identity and an absolute/lazy-list anchor, not a
FocusRequester or a bare index. Resolve after data loads; only then scroll the item into composition
and request focus. If missing: same logical channel → nearest surviving item in the same row → row
CTA → destination subnav/header. Reject bookmarks belonging to a different profile owner/source
context. Cancel superseded restoration jobs; held remote input targets the latest step.

Preserve existing deep links and shortcut semantics behind an adapter, including global Search
scope, Guide, Downloads/Recordings, profile gate, launcher continuation, external-player return and
installer confirmation. Do not adopt a new navigation dependency just for four tabs. Evaluate a
small typed coordinator against the current Compose/state-holder approach first.

## Compact header geometry

1920×1080 design canvas; primary safe area x=64…1856, y=40…1040. Header occupies a **constant
110-pixel design slot**, with the same content origin and target bounds in both modes. In content
focus, reduce inactive-label emphasis; do not slide or collapse targets under the D-pad. When header
has focus, show full labels and turquoise focus. Active selection has a small underline; focus has
an outline/fill/glow, so the two states are distinct. No permanent search tab or global weather tile.
Actual Compose sizing uses the existing `mpx`/density mechanism; revalidate native 720p, font scale,
long Swedish/English labels and RTL before release. The mockup is one fixed dark design direction,
not an implementation of every existing theme.

Recommended type hierarchy at 1080p: hero 52–58 px, page 40–44, section 30–32, channel title 22–26,
secondary metadata 18–22. A typographic channel label always survives missing logos. In production
prefer the existing allowed cached logos/artwork; never fetch arbitrary new artwork during focus.
Focus outline 3 design px plus restrained glow; selected state is not focus. Keep motion under
roughly 120–160 ms and honor reduced motion. No looping hero motion or animation on data arrival.
These are design targets, not measured Android performance guarantees.

## Exact remote model

| Context | Up | Down | Left / Right | Short OK | Long OK (≥500 ms) | Back |
| --- | --- | --- | --- | --- | --- | --- |
| Primary header | Stay, never leave app through Up | Commit focused destination; restore its subnav/content bookmark | Move header focus, including Profile at right; clamp edges, no wrap | Commit destination, keep header focus | No secondary action | Cancel an uncommitted tab candidate; otherwise non-Home → Home header; Home header → Android system |
| TV / VOD / Sport subnav | Current primary tab | Restore selected subview's item/bookmark; otherwise first valid CTA | Move focus among subviews, no wrap | Select subview, keep focus | No secondary action | Current primary tab |
| Home hero actions | Active primary tab | Same horizontal intent in Now row | Among visible hero actions; clamp row edge | Play actual resolved last channel, or first-run CTA | On logical Play/channel target, source/quality picker; no hidden action on generic CTA | Active primary tab |
| Horizontal content row | Previous row at nearest remembered x; first row → subnav/header | Next row preserving horizontal intent | Adjacent item, lazy-scroll as needed; no cross-destination navigation | Perform labelled action | Logical live card → source picker; VOD → existing context menu | Subnav/header, saving exact content bookmark |
| TV/VOD grid | Previous row/same column; top → subnav | Next row/same column; last clamps | Adjacent column; edges clamp | Play live or existing VOD details/resume action, per label | Live picker or existing VOD context menu | Subnav, then header on next Back |
| Guide | Preserve current guide row/time semantics | Existing channel row movement | Existing row/cell stage navigation; never let top-nav capture timeline keys | Existing browse/watch/programme semantics, not a generic VOD action | Existing programme actions; add labelled source entry only when equivalence is proven | Programme/cell stage → row → TV subnav → header; preserve v0.9.1 cursor/details synchrony |
| Profile hub | Header when at top | Next grid/group item | Spatially adjacent library/settings control | Open existing destination | Existing quick-pin/context action only where already meaningful | Pop subpage to caller; hub → previous primary destination/bookmark |
| Modal / source picker | Previous option | Next option | Existing modal option layout only | Commit labelled choice once | No second modal | Dismiss and restore exact caller; no preference write |
| Search + IME | Native IME behavior while open | Native IME behavior while open | Native IME behavior while open | Native text entry/selected result | Do not intercept IME selection | IME first, then search sheet to its scoped caller, then content hierarchy |
| Fullscreen/mini/audio player | Existing HUD semantics | Existing HUD semantics | Existing seek/HUD semantics | Existing HUD action | Existing player menu | Dismiss HUD/popup, then leave player to exact origin; never expose top nav over video as a new player owner |

At startup focus a usable content action, not a logo or decorative heading. Profile/settings remain
reachable from any destination via header. Back must terminate at the system in finitely many steps;
never alternate header/content indefinitely. System Home remains Android's; never claim HOME or
change launcher defaults. CH+/CH− and numeric tuning retain existing capabilities.

Short/long OK handling is a shared interaction contract: arm once on initial keydown, long press
fires once after threshold, consume repeats and suppress the subsequent keyup Play. Short action
fires on keyup if long has not fired. Cancel on focus loss, popup close, route/profile change and
lifecycle pause. Holding arrows may repeat navigation with current cancellation guards. Never
capture text/IME long presses or change the platform key mapping. Add a visible “Källa & kvalitet”
entry in the current context menu for accessibility/remotes where long OK is unreliable.

## Home content rules and alternatives

### Lugn start (recommended)

One 350-high design hero, full-width Now row with eight ~210-wide cards, then up to three compact
resume entries. Keep the principal Play target in the same place across loading, missing EPG and
no-history states. Use a static gradient/cached art, not a new autoplay stream. With no history:
explain what can be watched from current sources and focus Utforska TV; with no source: focus
existing source onboarding. Never pick a random channel and label it recently watched.

Now uses real available Swedish groups in default order SVT1, SVT2, TV3, TV4, Kanal 5, TV6, Sjuan,
TV8, then other recognized groups. Saved explicit user order/hidden/UHD choices take precedence.
Do not prepend favorites/history to this standard Now order as current Home quick projection does.
No favorites prerequisite and no duplicated quick-favorites row by default. If fewer than eight
exist, show fewer; do not pad with invented cards. A missing current programme says so but does not
disable a playable channel. Never substitute the next programme as “now”.

Hero is the most recent valid accessible live channel, resolves the saved preferred equivalent
variant through one shared resolver, and uses that exact variant's EPG/progress/next. Source choice
is visible in its detail/picker, not a second permanent main action. If that source is gone, use a
bounded safe fallback with an honest message; see [preferences](playback-and-preferences.md).

Personalized resume is based on current profile's real movie/episode positions, valid duration and
accessible item IDs. Missing data hides optional resume/discovery sections but never moves the
primary header/hero/Now controls while focus is in them. Freeze row ordering during active focus;
apply newly available sections at safe re-entry, or add below current focus. Keep hidden-section
preferences, not an automatic popularity feed. Optional favorites/recent shortcuts must add distinct
value and avoid repeating most of Now's identities. No automatic row scoring by invented metrics.

### Kanaler först

Tall 664-wide hero with 4×2 large channel matrix beside it; resume below. Stronger readibility and
same first action, but needs more vertical traversal and longer eye movement between hero and tiles.
It trades the requested horizontal Now rail for larger tiles. Validate sofa distance and remote
crossings with actual long titles; if A's eight-card row proves too dense, prefer six visible cards
with a partial next card before adopting B or shrinking text. Do not expose A/B as yet another
persisted setting in the first release.

### Optional external apps

Put Mina appar below Home content, opt-in/manageable in Utseende & Hem. Preserve existing explicit
enabled choices in a later additive adapter; don't remove installed shortcuts. Open only installed,
compatible existing handlers; no new package scans/token sharing/stream extraction. Twitch's
content/account experience belongs to Sport → Twitch even if an explicit app shortcut remains.

## Destination behavior

TV opens Svenska kanaler on a new profile's first entry; session return restores its last subview.
Respect an existing explicit guide-first preference via compatibility mapping pending approval.
All/Favorites/Guide use existing owners. Recordings and Search are secondary utilities, not primary
destinations. Provider category/folder organization stays reachable from Alla kanaler. Guide long
OK retains reminder/record/catch-up options rather than being replaced wholesale by source picking.

Sport brings together My teams, relevant verified matches, actual available broadcast channels and
optional Twitch. Reuse ShlRepository/SportPreferences/HockeyEpgStore/broadcaster evidence and source
opt-in/blocking. No data request on Home/header hover. Separate Empty (no teams/fixtures), Offline
(cached age), Error (typed stage), No verified broadcast and No compatible external app. A fixture
with a future end is not proof it is currently live. Revalidate profile/source/access and the exact
fixture before any Play. No new scraping/service/API key in this milestone.

Film & serier wraps existing Movies and Series without collapsing their IDs or queues. Provider
folders, matching, details, versions, episode selection, history, downloads and progress remain.
Show contextual search and one shared library owner. Cross-provider series identity is not inferred
from a Swedish title string. No new streaming-service entitlement logos or claims of bundled content.

Profile is a regular full-page hub in the prototype so the complete migration map is visible.
Production may use a small profile switcher overlay plus the same full-page hub; choose one before
implementation. Favor a single library route with contextual shortcuts, not three new stores.

## Loading and performance strategy

- Keep the shared player/controllers and existing pin/ladder. Default Home art is static. Existing
  explicit preview setting may allow one muted settled preview; never concurrently start Home,
  Guide and Sport decoders. Pause on header/profile/route/background and hand over using the current
  promotion/release logic. No new preview for poster focus.
- Derive channel index from current profile and allowed Live sources through SwedishChannelStore.
  Reuse its bounded cancellable catalog scan (400-row batches, 100,000 scanned / 4,096 candidates)
  and partial-state signal; do not pretend a bounded index is complete. Cache per profile/source/
  customization generation; invalidate on actual refresh/access changes, not each focus step.
- Query EPG only for visible logical channels plus a small look-ahead via existing cached/batched
  readers and offsets. Preserve the current SWEDISH_EPG_WINDOW cap (read constant when implementing),
  v0.9.1 foreground clock and cancellation. Do not load a week of programmes for every channel.
- Lazy lists/grids have stable profile/logical/item keys. Share compact immutable projections,
  not full entity graphs with URLs. Separate layout snapshots from fetching. No N+1 HTTP calls
  for source badges or focus; no decoding source variants to discover an advertised quality label.
- Sport uses existing cache/deadlines/backoff/opt-in. Foreground only, selected screen and current
  profile; don't fetch both Sports and Home instances of the same repository. Resume/discovery
  uses bounded local queries and existing metadata caches, never a library-wide online rescan.
- Maintain existing update signature validation, production/QA isolation, DB schema and backups.
  New derived UI preferences should reuse current preference stores. If an unavoidable schema
  change is proposed later, require separate justification, additive migration and old-data tests.
- Proposed acceptance targets, to measure before committing to claims: restored content focus in
  ≤2 frames after its data/target is composed; responsive key feedback; no new main-thread catalog
  scans; no extra concurrent decoder; no increased background traffic. Compare cold/warm startup,
  frame timings, Java/native/PSS/decoder memory and requests with v0.9.1 on the same fixture/device.
  Do not publish guessed millisecond/memory speedups from an HTML prototype.

## Implementation phases and gates

| Phase | Scope | Must pass before next phase |
| --- | --- | --- |
| Design approval (this PR) | Destination map, two Homes, all destination sketches, interaction/source/subtitle contracts | Owner chooses Home/header activation/legacy visibility defaults; no production UI code |
| M1 — native shell adapter | Typed TV-only route adapter, header/profile door, existing screens, ID-based focus bookmarks; keep More alias | Complete route/shortcut/back inventory, all Settings keys, no focus traps, startup/profile/rotation restoration tests, old data intact |
| M2 — chosen Home projection | Lugn start, grouped Now, honest empty states, move Sport content with same owner | No fake/duplicate channels, user prefs retained, EPG expiry/midnight/resume tests, no second decoder or fetch duplication |
| M3 — source consistency | Shared resolver and long-OK contract across logical Home/TV/Guide/Sport entries | Profile/access/reimport/removal/ambiguity/UHD tests, keyup suppression and actual tune identity/EPG checks |
| M4 — subtitle reliability | First add failing Core tests for delayed tracks/Swedish tags/manual overrides; only verified fixes with reviewed Core pin | Two-engine series fixture matrix, audio/subtitle continuity, off/manual choice preserved and no external download side effects |
| QA and release proposal | Separately approved same-key QA upgrade; Stable untouched | Real D-pad/IME, overnight/midnight, TV audio/decoders/subtitles, memory, backups and authenticated higher-version update checks; separate signing/distribution approval |

## Regression matrix

Native Compose tests: header pending vs committed state; all four primaries + Profile; Down returns
to channel 37 after offscreen lazy scroll; removed/reordered item fallback; rapid held arrows,
long OK once/no Play, focus loss and pause cancel; system Back termination; fullscreen/mini/audio
return; search query/result/IME dismissal; deep links and remote shortcuts; profile switch/PIN;
capability-empty versus user-hidden destinations; process recreation and locale/font/RTL.

Data tests: zero/one/many Swedish groups without favorites; default and saved ordering; regional
and +1 identities; changed/removed source and profile owner reuse; exact preferred variant; no
EPG/expired/next-only/offset/refresh/night transition; resume only valid positions; privacy-safe
diagnostics; source bounds/cancellation/no extra network; safe missing optional Home rows.

Migration tests: enumerate all entries in destination-map.md, every SettingsTab/SettingsGroup,
quick pins, hidden navigation values, existing HomeSections and legacy caller keys. Snapshot current
prefs and synthetic Room data before/after; logical regrouping cannot clear favorites/progress.
DB/package/provider/permission/update-security tests remain unchanged. No fixture may run on a
physical owner device without separate approval; retain disposable-emulator guards.

## Risks and decisions requiring review

1. Approve Lugn start versus Kanaler först; eight visible versus six if sofa readability requires it.
2. Approve explicit OK/Down activation; no dwell preview/fetch. Approve Back hierarchy and fixed-size
   header reservation before coding it.
3. Approve fixed primary tabs with honest emptiness versus current dynamic visibility, and treatment
   of legacy hidden tabs/guide-first settings. Never silently unhide a user's content category.
4. Global quality defaults apply only to proven equivalent logical channels. Provider-exact rows,
   regional/time-shift variants and ambiguous Sport evidence need explicit behavior; see preferences.
5. Source-pref backup/reimport scope and old derived Home keys need an additive compatibility plan;
   a normal update is not the same as deleting/reimporting sources or restoring to a new device.
6. Subtitle delayed-track behavior is not physically verified. No generic “Swedish subtitles fixed”
   claim or blanket MPV/Exo replacement is authorized by this design.
7. Combined VOD UI must preserve per-series episode memory; fuzzy cross-provider resume is too risky
   for M1. Images, fonts and branded logos must remain licensed/authorized existing sources.
8. The prototype demonstrates geometry and selected interactions, not Compose accessibility,
   Android IME, provider behavior, decoder performance or a complete player simulation.

Reference principles: [Android TV navigation](https://developer.android.com/training/tv/get-started/navigation)
for predictable remote traversal, explicit focus and finite Back; [Media3 track selection](https://developer.android.com/media/media3/exoplayer/track-selection)
for available-track updates and per-media selection. The exact four-tab/idle/long-OK product choices
above are this proposal's recommendations, not quotations or mandatory platform rules.

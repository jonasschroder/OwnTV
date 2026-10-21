# M1 — native top-navigation foundation

Design PR #16 was approved by the owner, restored its two truthful mandatory acknowledgements,
passed all eight current checks, and merged as `8533695f18240a7b0badcfbc5e5e6b985a336bc8`.
Implementation branch: `feat/v010-native-top-navigation`, based on that resulting main.
This implementation is separately reviewable and must not be merged, released or installed on an
owner device without approval. All design documents and original mockups remain intact.

## Architecture

`NativeTopNavigation` is a native Compose focus group, not a WebView. A fixed-height header separates
focus decoration from the active-page underline and selected semantics. Left/Right moves focus;
only OK selects. Down enters the **currently active** page, even if another tab is highlighted.
Primary tabs stay in predictable positions; unavailable/hidden content gets an honest settings CTA.
Profile/Settings is pinned on the right; the primary row scrolls if width/font/locale requires it.

`NativeNavigation` maps presentation-only HOME/TV/SPORT/VOD/PROFILE to existing `MainSection` owners.
No persisted Core enum, database schema, provider ID, signing identity or update contract changes.
Sport uses a presentation override around HOME's legacy ID; explicit old routes/shortcuts clear
that override. Native subnavigation uses existing sections and keeps contextual search available.

`OwnTVShell` still owns the one shared player, mini/audio mode, shortcuts, deep links, subtitle
controller, installer/modal Back behavior and existing view models. The visible StageRail is removed;
its class and internal More aliases remain for compatibility. Rail counts/auto-hide observers are
removed from the shell because this header needs neither. Existing status, playlist, weather,
Continue and audio controls remain; mini/audio Now playing is explicitly reachable.

`NativeSportScreen` composes the current `MinSportCompanion`/Matchcenter and `TwitchCompanion`, using
existing Koin singletons and profile/access readers. Only the active resumed page can fetch. No
extra sports API, duplicate repository, Home recommendation model or player engine is introduced.
The existing Home presentation is retained, including its current optional companions; moving its
content modules belongs to the separately reviewed Home milestone.

## Old → new map

| Existing route/function | M1 entry and owner |
| --- | --- |
| HOME | Hem → existing MinTvContentHome; constant left content inset, no rail dependency |
| LIVE_TV | TV → Svenska kanaler → existing LiveScreen/SwedishLiveScreen |
| LIVE_TV with saved GUIDE | Same owner and existing LiveView setting; guide-first is not rewritten |
| Full live/provider library, categories, favorites/history | Svenska kanaler → existing All channels/search entry and menus |
| EPG | TV → existing TV-guide/EpgScreen; timeline, programme selection and recording actions retained |
| MOVIES / SERIES | Film & serier → existing MoviesScreen / SeriesScreen; last available VOD subview restored |
| DOWNLOADS | Profile → Downloads → existing Movies/Series/Recordings tabs |
| MORE | Profile → Library & profile → unchanged MoreScreen and all its pages |
| SETTINGS | Profile → Settings; all existing groups/rows/search and profile management |
| Favorites / History | Profile → Library & profile → existing media filters and resume actions |
| Backup / Local Sync / errors / About / updates / developer | Same More pages, unchanged guards and owners |
| Hockey / Matchcenter / Twitch | Sport → existing companions, team/settings and external-player paths |
| SEARCH / OPEN_SEARCH | Contextual TV/VOD entry; existing query/result owners and old shortcut accepted |
| Fullscreen / mini / audio / external return | Existing shared shell/player layer, not a primary page |

A machine-checked map covers every existing MainSection. The complete settings and nested-page
inventory remains [the approved destination map](../destination-map.md); nothing there is removed.

## Focus, Back and saved state

- Up exiting a content focus group targets the selected primary header. Children get their key
  events first: EPG timeline Left/Right and local modal/category controls are not globally captured.
- Header Down invokes the current screen's existing entry hook, then its focus restorer, with a
  header fallback when no child can receive focus. Screen readers retain selected semantics.
- Saveable screen state is keyed by profile + unchanged MainSection + presentation override. The
  holder survives fullscreen and restores each screen's rememberSaveable list/scroll/selection state.
  Entry hooks are profile-scoped; a different profile never receives the previous profile's target.
- Child popups/details/settings stacks and player Back keep priority. Shell fallback is content →
  header → Home → existing exit confirmation. Settings root still returns to its More/Home caller.
- The header is composed only while the existing shell is browsing; fullscreen/multiview remain
  above it. No player SurfaceView call site or engine fallback is replaced.

## Preference adaptation

STATIC mode's old hidden flags filter the corresponding TV/VOD subroutes. Hiding MOVIES does not
unhide it through the combined Film & series entry. DYNAMIC mode keeps Core's existing capability
rules. Profile and Settings stay accessible with empty/offline data. Old rail style/size/length/hide
values remain stored; their visual controls are hidden only inside the native TV shell. The current
Navigation settings still expose the applicable mode/visibility toggles. No destructive migration
or one-time data rewrite occurs. Min TV's existing normal-entry Home behavior stays intact.

## Validation and limits

See [validation.md](validation.md) for executed checks and evidence. New tests exercise route
coverage, visibility adaptation, finite Back, actual native header input, content restoration,
scroll re-entry, profile isolation, empty data and synthetic fullscreen/timeline boundaries. Existing
Home/EPG/VOD/update/backup/startup/signing regression suites remain enabled. The real application
startup fixture additionally opens TV, Guide, Sport, VOD, Profile, Downloads and Settings and captures
native screenshots at the existing 1080p/720p settings.

An isolated UI fullscreen fixture proves navigation visibility and return behavior; it does not
claim a hardware decoder session. Physical remote/overscan, real DRM playback, external apps and
Chromecast acceptance still require a separately approved QA installation. Neither installed owner
app is used for these tests. No local emulator runtime is installed; CI uses disposable emulators.

## Proposed next milestone

M2: implement the approved Din kväll Home composition using existing data owners, first preserving
stable module keys and safe current/next EPG. Add seven-day/90%-completion policies only after the
owner confirms those proposed product thresholds. Compact Now data, shared source resolver and
save-and-play picker remain separate later changes; no subtitle or cross-provider merge changes
belong to this navigation PR.

> Final iteration: [iteration-2.md](iteration-2.md) supersedes conflicting Home layouts, library ownership and picker OK behavior below. The source audit and destination coverage remain valid.

# Destination inventory and migration contract

Source review: [OwnTVShell](../../../app/src/main/java/tv/own/owntv/features/shell/OwnTVShell.kt),
[MoreScreen](../../../app/src/main/java/tv/own/owntv/features/more/MoreScreen.kt),
[SettingsScreen](../../../app/src/main/java/tv/own/owntv/features/shell/components/SettingsScreen.kt),
[SettingsGroup](../../../app/src/main/java/tv/own/owntv/features/shell/components/SettingsGroup.kt),
[DownloadsScreen](../../../app/src/main/java/tv/own/owntv/features/downloads/DownloadsScreen.kt),
[MinTvSettings](../../../app/src/main/java/tv/own/owntv/features/home/MinTvSettings.kt).

## Primary and More destinations — no feature may disappear

Canonical path means one screen/data owner, even when contextual shortcuts lead to it.

| Current entry | Proposed canonical destination | Existing owner / preservation condition |
| --- | --- | --- |
| Home hero / latest live / Now / favorites quick row | Hem | MinTvContentHome, HomeDashboard and current bounded readers; merge duplicate channel rows visually, not stored favorites |
| Search / rail magnifier / OPEN_SEARCH shortcut | Contextual TV or Film & serier search | SearchScreen/SearchViewModel remain; legacy global search links open a scoped search sheet with explicit TV/Film/Serier scope switch, never silently discard query |
| Live TV → Swedish collection | TV → Svenska kanaler (default) | SwedishLiveScreen + SwedishChannelStore; current access, regional identities and saved order |
| Live TV → full provider library / categories / favorites / history | TV → Alla kanaler / Favoriter / history shortcut | LiveScreen and LiveViewModel remain; category/custom folders, ordering, renaming/hiding, numbers, zapping and source filter intact |
| EPG primary destination | TV → TV-guide | EpgScreen/EpgViewModel retain date, programme actions, offsets, matching, reminders, catch-up and recordings; v0.9.1 idle behavior preserved |
| Live TV Guide mode setting | TV → TV-guide alias | Preserve existing LiveView preference; proposed default subview is Svenska on first entry only. Restore last TV subview within session; seek approval before overriding an explicit saved guide-first choice |
| Movies | Film & serier → Filmer | MoviesScreen, provider folders, discovery, details, versions, favorites, progress and download actions |
| Series | Film & serier → Serier | SeriesScreen, seasons/episodes, resume/autoplay, tracks and downloads |
| Downloads → Movies / Series | Profil → Mitt bibliotek → Nedladdningar | DownloadsScreen retains download folder picker, active/queued/completed tasks, actions and file permissions; contextual alias from Film & serier → Mitt bibliotek |
| Downloads → Recordings and scheduled recordings card | Profil → Mitt bibliotek → Inspelningar | Same recording state; alias TV → Inspelningar and Guide programme actions, not a duplicate list implementation |
| More → Favorites (Live / Movies / Series / All) | Profil → Mitt bibliotek → Favoriter | FavouritesPage; TV → Favoriter and Film & serier saved filters call the same owner with media filter. No destructive favorite rewrite |
| More → History | Profil → Mitt bibliotek → Historik | HistoryPage, exact episode/movie resume and existing clear-history confirmation retained |
| More → Backup / Restore | Profil → Inställningar → Data & säkerhet → Säkerhetskopiering | Existing BackupScreen and encrypted .own format, preview/import safeguards, exclusions and local export |
| More → Local Sync | Profil → Inställningar → Data & säkerhet → Lokal synkronisering | Existing LocalSyncScreen, explicit listening/pairing and account/profile boundaries |
| More → Playback error log / crash export | Profil → Inställningar → App & diagnostik → Fellogg | Existing ErrorLogPage/export including sanitized v0.9.1 trace; never expose raw IPTV text in top-level Home |
| More → About / manual updater / automatic-check preference | Profil → Om Min TV | AboutPage / UpdateDialog / authenticated Stable/QA updater unchanged; Settings → App contains alias to same screen |
| More profile name / switch / avatar | Upper-right Profil → Byt / Hantera / Avatar | Existing profile gate, age/PIN controls, avatar picker/custom image permissions and profile owner identity |
| More → Settings | Upper-right Profil → Inställningar | Existing twelve groups reachable under six presentation collections below; no row renaming/deletion in M1 |
| More → Developer | Profil → App & diagnostik → Utvecklare | Only where BuildConfig.DEV_TOOLS already allows it; never enable it in production |
| Home → hockey summary / Matchcenter / My teams | Sport → För dig / Mina lag / Matcher | Existing SportPreferences, ShlRepository, hockey EPG evidence, broadcaster picker, team selection, source opt-in/blocked and diagnostics |
| Home → Twitch / account setup / open player | Sport → Twitch | Existing TwitchCompanion, token storage, authorization cancellation and compatible external-player intents; still optional |
| Home → installed-app shortcuts | Hem → optional Mina appar (bottom) | Existing installed-app allowlist/launcher. No new mandatory row; respect explicit existing user's enabled choice during adaptation; manage via Utseende & Hem |
| Home → Customize / section order / preview toggle | Profil → Inställningar → Utseende & Hem | Existing HomeSections and settings; preserve hidden/order/preview choices. Sport/Twitch keys become Sport visibility/preferences, not deleted data |
| Home/Live/VOD/Downloads → fullscreen / mini / audio player | Shared player layer above destination | Preserve playerMode, zapSource, guide callbacks, media session, external player return and subtitle search; not a primary tab |
| Standalone subtitle search/download/remove | Player → Undertexter → existing search / management | SubtitleSearchScreen/ViewModel, OpenSubtitles account/filter and local delete controls preserved |
| First run / profile gate / source onboarding | Existing gate before shell, then Hem discovery | Setup/profile access remains authoritative; empty-data CTA opens current source form, never a synthetic lineup |
| Update confirmation, remote shortcut intents, deep links, launcher continuation | Route adapter → canonical destination | Keep package schemes, permissions, installer callbacks and incoming IDs; tests must exercise every old entry |

## All twelve Settings groups and every nested SettingsTab

Six landing cards are presentation collections, not replacement settings stores. Retain internal
`SettingsGroup` ordinals, row keys, `SettingsStart(group,search)`, pinned quick keys, Search entries
and all existing values. Add stable named UI routes around them. Nested pages below are the complete
current `SettingsTab` inventory, excluding ROOT itself.

| Current group | Proposed collection | Included existing nested destinations / rows |
| --- | --- | --- |
| QUICK | Profil → Inställningar → Snabbval (always reachable above collections) | Existing pinned row references; no duplicated controls with different state |
| PROFILE | Profil / Hantera profiler | Profiles, children/PIN lock, avatar, preferred profile/languages; existing ProfileSettingsRows |
| SOURCES | Källor & TV-guide | SOURCES, EPG, per-source capability/options, EPG auto-refresh/logo rules, offsets, reminders, source selection and source backup tools |
| APPEARANCE | Utseende & Hem | GLASS_EFFECT, FONTS, WEATHER, themes, accent, text settings, accessibility; preview behavior preserved |
| LAYOUT | Utseende & Hem | PANEL_WIDTH, GUIDE_WIDTH, BROWSING, HOME, CONTENT_MENUS, navigation visibility, panel/list layouts and CH_NAV shortcut |
| CONTENT | Källor & TV-guide / Ljud & undertexter, explicit aliases | METADATA remains with catalogue/data; OPEN_SUBTITLES remains one account/search page under Ljud & undertexter; CUSTOMIZE stays existing content organizer |
| PLAYER | Spelare & inspelning | Engine choices, external playback, quality limits and player behavior, without new defaults |
| PICTURE | Spelare & inspelning → Bild | All current decoder, display/HDR/frame-rate/zoom rows; no automatic performance tuning |
| SOUND | Ljud & undertexter | Preferred audio/subtitle language, style SUBTITLE_STYLE, delay/audio options; no duplicate Swedish preference |
| LIVE | Källor & TV-guide → Live-TV / Spelare alias | Channel switching, preview, guide defaults, CH_NAV, source/quality and live behavior; single row owner |
| WATCHING | Spelare & inspelning | RecordingSettingsRows, catch-up/timeshift, playback/recording options and storage |
| APP | App & diagnostik / Data & säkerhet | LANGUAGE, NETWORK, DNS, BACKUP, LOCAL_SYNC; update/About/export/diagnostic rows. MINTV_SPORT → Sport settings; MINTV_TWITCH → Sport/Twitch settings; MINTV_HOME → Utseende & Hem |

Language, Network, DNS, Metadata, OpenSubtitles, Weather, channel-navigation, panel/guide width,
glass, content menus, fonts, browsing, subtitle style, Sport, Twitch and Home all retain their
existing entry. Keyboard Settings search must index these canonical targets and their old labels.
Do not remove a row simply because it is not drawn on the six-card mockup.

## Persisted-ID and visibility adapter

Core's `MainSection` constants are persisted in `navMenuHidden` and shared with mobile. Do not
rename SEARCH/HOME/LIVE_TV/MOVIES/SERIES/DOWNLOADS/EPG/SETTINGS/MORE. `MainSection.browseOrder` and
`NavVisibility` are not the new product hierarchy. A TV-only `PrimaryDestination`/`TvSubview`/
`LibrarySubview` adapter maps to current screen owners while translating legacy links in both
directions. New SPORT is presentation-only initially; its content/settings owners already exist.

Visibility needs a documented, non-destructive mapping: hidden MOVIES should hide movie content
within Film & serier, not unhide it because SERIES is visible; hidden EPG must remain respected via
explicit navigation customization, while Settings/Profile is never hideable. Empty capability is
not the same as a user-hidden destination. Decide whether fixed TV/VOD tabs show an honest empty
state (recommended for predictable geometry) or disappear; do not infer a new default from legacy
Core dynamic mode without approval. Preserve old values and add an adapter, with round-trip tests.

More may leave the visible chrome only after a machine-checked migration inventory covers this
map, legacy intent/shortcut/back-stack tests pass, and an approved physical QA review confirms all
features are reachable. Keep its internal alias until compatibility is established.

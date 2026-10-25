# v0.11 native implementation and review evidence

Implementation continues draft [PR #22](https://github.com/jonasschroder/OwnTV/pull/22), branch `feat/v011-native-ux`, from main `10f02c6`. Approved design: `a7f5f7c01d6d24c4d56f38854736355a973282b2`. This is Kotlin/Compose implementation; no WebView or production mock repository.

## Screen comparison

| Screen | Implemented native behavior | Difference / remaining verification |
|---|---|---|
| Home | Followed-team matchday hero (existing Färjestad default respected), exact fixture frozen during navigation, fresh existing schedule/specific EPG evidence, explicit browse/details fallback, verified-only direct hero playback. Recent accessible IPTV, five logical Now cards, source picker, three exact unfinished items, profile favourites below Continue, compact authenticated/offline/signed-out/unavailable Twitch, independent official-app entry and real version. | Provider programme artwork has no existing EPG contract; real channel logos/neutral gradients replace mock imagery. Native header is the approved M1 header, not the HTML header. No fabricated live period or score. Physical playback/auth return still requires Chromecast acceptance. |
| TV | Native five-column grid and column-aware D-pad movement, partial last row, stable logical IDs, bounded stored EPG, current/next/progress, actual branding/fallback, short OK fullscreen, held OK existing M3 selector, exact return focus/scroll. No preview decoder on focus; ordinary player exit silences the live backend. | Complete provider library, Guide/catch-up/recording and exact-source actions remain accessible through their existing routes. Category/Guide controls remain in the full destination; presentation fixtures isolate the grid. Programme backgrounds require authorized artwork. |
| Sport | Match-first dashboard with favourite hero, Today, Upcoming and numerically sorted actual hockey channels. Existing repositories, opt-ins, cache budgets, broadcast resolver, EPG evidence and profile/fixture manual choices retained. Manual choices never create verified hero autoplay; stale fixtures are omitted. Hiding Home prominence does not disable explicit Sport browsing. | There is no verified real-time period/score provider. Result snapshots remain labelled saved results; manual selection remains unverified in existing details. Partial/disabled sources show honest status and usable IPTV browsing. No standings table. |
| Film & serier | Two stacked entry rows and bounded actual catalogue previews. Down from Film reaches Series, stable column/focus on return; new titles open existing details/variant selector, exact progress resumes immediately. Existing full library, search, favourites, history and downloads retained. | Catalogues/posters depend on existing provider/TMDB access. Missing artwork uses a named fallback. Native catalogue/detail routes are preserved rather than rewritten. Actual external-player return and subtitle handover need physical QA. |

## Native screenshots

These are **real Android TV emulator renders of production Compose components with deterministic synthetic test datasets**, not browser screenshots, owner IPTV data, or Chromecast evidence. The TV fixture exercises the production grid/frame/cards without the destination's extra category/Guide controls. Synthetic names/results exist only in Android test source.

| Screen | 1920×1080 | 1280×720 | Approved reference |
|---|---|---|---|
| Home | [native](../../images/v011/native/v011-native-home-1080-synthetic.png) | [native](../../images/v011/native/v011-native-home-720-synthetic.png) | [Designlab 04](https://github.com/jonasschroder/OwnTV/blob/a7f5f7c01d6d24c4d56f38854736355a973282b2/design/designlab04/preview/screenshots/iteration4-home.jpg) |
| TV | [native](../../images/v011/native/v011-native-tv-1080-synthetic.png) | [native](../../images/v011/native/v011-native-tv-720-synthetic.png) | [Designlab 04](https://github.com/jonasschroder/OwnTV/blob/a7f5f7c01d6d24c4d56f38854736355a973282b2/design/designlab04/preview/screenshots/iteration4-tv.jpg) |
| Sport | [native](../../images/v011/native/v011-native-sport-1080-synthetic.png) | [native](../../images/v011/native/v011-native-sport-720-synthetic.png) | [Designlab 04](https://github.com/jonasschroder/OwnTV/blob/a7f5f7c01d6d24c4d56f38854736355a973282b2/design/designlab04/preview/screenshots/iteration4-sport.jpg) |
| Film & serier | [native](../../images/v011/native/v011-native-vod-1080-synthetic.png) | [native](../../images/v011/native/v011-native-vod-720-synthetic.png) | [Designlab 04](https://github.com/jonasschroder/OwnTV/blob/a7f5f7c01d6d24c4d56f38854736355a973282b2/design/designlab04/preview/screenshots/iteration4-film-series.jpg) |

Visual review corrected a missing match CTA, oversized supporting content, deadlocked Sport entry waiting for a nonexistent horizontal rail, and empty VOD artwork placeholders. Home/TV density and hierarchy follow the reference; colours/gradients, actual content and retained native controls differ. Final visual owner approval remains separate from automated acceptance.

## Validation

- **77 targeted JVM tests passed**, no failures/errors/skips: Home policy and fixture identity, shared row navigation, grid navigation, RemoteOkPress, source resolver, sports/EPG/match feed and discovery selection.
- **26 targeted Android TV emulator tests passed**: Home/source focus, real key cycles and long OK, Sport entry/row removal, Film → Series/details/exact resume, empty/profile switch, stable latest-state entry registration, signed-out Twitch, official-app/store/Back launcher regressions, isolated persisted progress and visibility, screenshots at both resolutions, and 720p with 140% text.
- Standard debug app/test APK and separately packaged QA debug APK compiled successfully. Local development version supplied as `MINTV_VERSION_NAME=0.11.0-dev`; no release versionCode policy changed.
- i18n merge-base ratchet, suite execution inventory and whitespace checks passed. Technical focus keys are classified; no hardcoded-string baseline growth. New product copy has English/Swedish resources.
- Full required GitHub CI remains authoritative on PR #22: Android build/unit/lint, native device suites, startup, signing/upgrade and i18n. No checks were removed or weakened. New native cases are registered as required executed/non-skipped results. The old eight-card selector mismatch was corrected. Existing unauthenticated Twitch launcher behavior is preserved; account status does not block opening the official app.
- No physical Chromecast playback, provider login, hardware decoding or user-data migration was performed. Emulator evidence does not certify these.

## Main ownership / changed components

- `features/home/MinTvContentHome`, `SmartHomeContent`/policy, `HomeQuickPanels`, `MinTvTwitch`: real-data Home composition and stable match identity. Existing bounded readers and source owner remain.
- `features/live/SwedishLiveScreen`, `SwedishChannelCard`, `ChannelGridNavigation`, `LiveScreen`: grid presentation/entry, existing fullscreen/source callbacks and bounded EPG. `SwedishChannelStore`/canonical grouping unchanged.
- `features/home/NativeSportDashboard`, `NativeSportScreen`, `MinTvCompanions`, `HockeyChannelOrder`: native dashboard; existing SHL/HA/broadcast/manual ownership unchanged.
- `features/home/NativeVodLanding`, `NativeVodHost`, `NativeRowsNavigation`: shared stable row focus and presentation router. Existing `DiscoveryRepository`, Movies/Series VMs, details and player reused. `DiscoveryScreen` filters expected profile immediately and clears hidden/customized stale shelves. VOD continuation clears immediately when profile/source/visibility ownership changes.
- `features/shell/NativeContentNavigation`, `OwnTVShell`, small optional close callbacks in `MoviesScreen`/`SeriesScreen`: shared VOD landing state, exact return and stable current-state entry registration. Legacy functions remain reachable; status/playlist controls remain on Profile and active mini/audio presentation.
- Focus/policy/native test files, English/Swedish v0.11 copy, technical i18n inventory and additive CI test selectors.

No Core repository/pin, player engine, subtitle engine, database schema, credential storage, package identity, app permission, signing certificate, release workflow or installed owner data changed. No new dependency, polling interval, source integration or player instance.

## Data gaps and release gates

- Programme artwork: no stored programme-image contract. Use real provider logos now; require an authorized/licensed image field before programme images can be shown.
- Sports: existing opt-in schedule/cache/EPG sources only. Exact broadcasts and real-time live scores are incomplete. Unknown stays unknown; manual channel choice is not proof.
- Twitch/TMDB: use existing authorized configuration; no embedded token, private provider URL or new authentication flow.
- Local QA debug APK is a development artifact, versionCode 2 with the existing local debug signer. It **cannot be assumed compatible with installed official QA**. Do not uninstall or clear data to install it. After review/CI and separate approval, use the existing permanent QA workflow, immutable source SHA and policy-generated higher code; verify installed/candidate package and signer, then transfer the approved QA APK via LocalSend. Stable remains untouched.

## Physical Chromecast acceptance (after separate QA install approval)

1. Confirm QA signer/code and Stable/QA separation; verify existing profiles, sources, favourites, order and progress survived.
2. Header Left/Right only focuses; OK activates; Down enters the selected page. Reach both VOD rows and all TV rows; finite Back restores exact card/scroll.
3. Check fresh/missing EPG, Home re-entry/midnight, match evidence/manual labels, numeric Live 1…10 and profile isolation.
4. Short/held/repeated OK: real picture/audio versus source picker; Back silences video and restores focus. Verify no hidden decoder or accidental long-press playback.
5. Movies/episodes: details, exact resume, source quality, Swedish subtitles, consecutive episodes and ExoPlayer/MPV fallback.
6. Twitch live/offline/signed-out/unavailable and external-app return; verify 720p/1080p, larger text, responsiveness and masked crash logs.

Next step: review the native captures and PR checks, then approve an official QA build/install for hardware acceptance. No merge, permanent signing, release publication or Chromecast installation is authorized by this implementation.

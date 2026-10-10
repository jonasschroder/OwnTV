# Min TV Test v0.2: TV interface and broadcast matching

This is an isolated QA build (`se.jonasschroder.mintv.qa`, **Min TV Test**). It does
not install over regular Min TV, clear either application's data, register as an
Android HOME app, or change Google TV/system packages. PR #3 remains unmerged.

## Short UI audit

The previous Home header had four competing buttons, including a duplicate Sport
route and a frequent-looking preview preference. Channel information competed with
the shell's clock/status area. An empty library still reserved a large black video
surface. Favorite names and generic pill buttons looked alike.

Matchcenter combined fixtures, settings, standings, parser/source notices and channel
search in one oversized dialog. Every game repeated full timestamps/disclaimers.
Standings were text rather than remote-navigable rows. Twitch setup led with API
instructions rather than the connection action. New Min TV text was largely English.

## Changes

- Home prioritises **Just nu**, actual selected-channel preview/EPG/progress, **Mina
  kanaler**, the compact Färjestad/SHL card, ohnePixel status, SmartTube and SVT Play.
- Navy surfaces, restrained teal focus borders, rectangular cards, channel logos,
  larger Swedish labels and bounded text. No new artwork download, team-logo licence,
  UI framework, player, native library or runtime dependency.
- Empty favorites use a compact setup card linking to the existing Sources settings.
  IPTV configuration/onboarding remain the existing OwnTV features. Live TV/guide and
  the shell navigation remain available. Preview preference/Android settings move
  into a small settings panel.
- Matchcenter is a full-screen content destination in the existing shell, not an
  Android Activity or large modal. **Matcher / Tabell / Färjestad / Inställningar**
  lead to scorecards, focusable standings rows and secondary source disclosures.
- Selecting a scorecard opens details; **Välj TV-kanal** opens logo/name cards,
  stored-EPG matches and explicit manual choices with channel search. Source numbers
  distinguish entries from different IPTV sources. Generic EPG titles do not confirm
  a game. Multiple matches stay separate; one confirmed match can use **Se matchen**.
- Home/list scroll state stays outside the conditional content. Selected game, tab,
  page and picker state are saved; Back returns picker → details → previous list →
  Home. Playback retains Matchcenter so player Back returns to the match/picker.
  Focus requesters restore the selected scorecard/Home entry. Table/source rows
  are focusable so the remote can scroll disclosures rather than trap focus at tabs.
- Opening Matchcenter stops the existing preview and pauses hidden Home EPG polling. Selecting a channel uses the
  existing playback/promotion callback. No preview on hockey/channel-picker focus,
  no autoplay, no second decoder or inferred stream URL.
- Dates/times use `Europe/Stockholm` and Swedish formatting. Result labels say
  **Ej liveuppdaterat** / **Sparat resultat** rather than implying a final/live score.
  One update indicator includes the date when the snapshot is from another day.
  Detailed fetch/source times remain in **Datakälla och uppdatering**. Live periods
  and lineup retrieval stay disabled.
- The existing experimental Swehockey reader stays off by default. Disabling it
  cancels the visible-content coroutine and performs no SHL requests/polling.
  Existing timeout/cache/robots/401/403/429 fail-closed safeguards are retained.
- Twitch Home is a small verified LIVE/Offline/status card. Setup prioritises
  **Anslut Twitch**, validated Twitch public-client activation URL/code and failure feedback. Encrypted device-local
  token storage and visible/foreground-only polling are unchanged.

## SHL broadcast discovery — current follow-up

The preceding redesigned QA APK was physically tested on Chromecast with Google TV
4K / Android 14. Schedule and standings worked; the external TVmatchen browser route
and search-first empty picker caused the reported UX problems.

The follow-up removes that browser route, adds an off-by-default personal HTML reader,
fixture-specific local channel choices, a channel-first/empty-source picker and clearer
standings. Current access/permission findings, actual live-source verification, limits
and physical test instructions are in [min-tv-broadcasts.md](min-tv-broadcasts.md).
TVmatchen is now reachable; that report supersedes the earlier proxy-denial finding.

## One-time public Twitch Client ID

Twitch documents public-client device-code authentication:
[Device code grant flow](https://dev.twitch.tv/docs/authentication/getting-tokens-oauth/#device-code-grant-flow)
and [application registration](https://dev.twitch.tv/docs/authentication/register-app/).
There is no Client Secret in that flow. **No ID is invented or borrowed.**

1. On your Mac, enable two-factor authentication for your Twitch account, then open
   [Twitch Developer Console](https://dev.twitch.tv/console/apps) and register a new
   personal application. Give it your own name, choose an appropriate category and
   **Public** client type. If registration requires a redirect URL, use
   `http://localhost` (the device grant does not use a redirect listener).
2. Copy its **Client ID**. Do not create/copy a Client Secret, access or refresh token
   into GitHub, source, build properties or this chat.
3. To include the public ID in future QA APKs: in your OwnTV GitHub repository open
   **Settings → Secrets and variables → Actions → Variables**, create the repository
   variable **MINTV_TWITCH_CLIENT_ID** with that legitimate public ID. The prototype
   workflow reads that public variable. An empty variable remains supported.
4. Local builds may set `MINTV_TWITCH_CLIENT_ID` or the user Gradle property
   `mintv.twitchClientId`; Gradle accepts only alphanumeric 8–128-character IDs.
5. On the TV select ohnePixel → **Anslut Twitch**. Open the displayed Twitch activation URL on the
   Mac (including `public=true` and its device code), enter the displayed code if requested and approve your own app. Back/Stäng cancels.
   There is no TV keyboard step when a registered build ID exists. Otherwise enter
   the legitimate ID once on the TV; the public ID is saved locally for later login.

The current cloud build has no supplied registered ID. Build-time support is ready;
real registration/login/refresh still needs your account and device test. The ID is
public (visible in the APK); private credentials remain encrypted on the TV. The
existing tokens are not migrated between package identities.

## Safe Mac → Chromecast QA installation

1. Leave **Min TV** and its data installed. Back up **Min TV Test** with the existing
   encrypted backup tool if its test configuration matters. Keep backup/password on
   your Mac securely; never upload credentials here or to public GitHub.
2. Sign into GitHub on your Mac, open PR #3's successful **Min TV prototype** Actions
   run and download the artifact **MinTV-v0.2-QA.apk**. GitHub supplies a ZIP; double
   click to extract. The actual APK inside has the same filename. Read
   `READ-BEFORE-INSTALL.txt`, `SIGNING-CERTIFICATE.txt` and the checksum file.
3. **Existing QA update:** compare the installed Test application's certificate with
   this artifact before an in-place update. Fresh GitHub runners use new debug keys.
   A differently signed APK cannot update Test while preserving its data. Do not
   uninstall/clear regular Min TV or accept instructions to disable Google TV.
   Follow [the certificate gate](min-tv-qa.md) / [signing plan](min-tv-signing.md).
   If certificates differ, stop: obtain an APK signed with the retained matching QA
   key or deliberately plan a QA-only encrypted-backup migration. No uninstall or
   data-clearing command is part of this task.
4. For a first install or verified compatible QA update, use
   [the documented Mac/Downloader transfer](min-tv-qa.md#install-from-a-mac-using-downloader).
   Transfer the extracted APK from your Mac on your trusted home network; an Actions
   artifact ZIP/sign-in URL is not an APK URL for Downloader. Alternatively use the
   existing USB ADB instructions with this QA package only.
5. Allow installs for Downloader only when prompted, choose **Min TV Test**, launch
   it, and verify the original **Min TV** still opens with its existing configuration.
   No launcher replacement or system modification is required.

Future stable regular signing remains the protected offline-key plan in
[min-tv-signing.md](min-tv-signing.md). No private signing keys were created/committed
for this redesign; PR workflows never receive a release key.

## Validation and remaining device checks

Baseline redesign evidence is recorded below. Current broadcast-follow-up results
are in [min-tv-broadcasts.md](min-tv-broadcasts.md) and PR #3. Cloud compilation is not a Chromecast render,
D-pad, decoder, memory or OAuth test. The user reports the preceding companion build
successfully fetched actual fixtures/standings on Android 14; that device evidence
applies to the preceding APK, not this redesigned one.

Physical checklist for the new QA APK:

- Readability/safe areas at 2–3 m; Swedish labels and long EPG/team/channel titles.
- Empty-library setup card; existing source configuration and encrypted backup import.
- D-pad focus/scroll in Home, full-screen tabs, 14-team table, source disclosure,
  details and channel picker; Back restores the selected game and Home scroll/focus.
- Background/browser return and process recreation retain sensible navigation without
  autoplay. No preview/extra connection on Matchcenter or picker focus.
- Real IPTV favorites, muted preview, programme progress, fullscreen audio/promotion,
  Stalker/DRM/provider limits, rapid navigation and long-running playback.
- Multiple IPTV sources/HD variants remain a picker; generic EPG remains manual.
- Experimental reader off: zero SHL traffic; foreground/visible cancellation, stale
  snapshots, malformed HTML and denied access stop/label safely.
- In-app automatic broadcaster/EPG/manual channel choice, saved fixture overrides,
  empty-source handling and keyboard Back; no SHL browser navigation remains.
- Legitimate Twitch public-client login, failure feedback, cancellation, token refresh,
  verified offline/live state and no background polling; SmartTube/SVT shortcuts.
- Measure actual RAM/frame drops/provider sessions against the previously installed
  QA build, then reopen original Min TV and verify its data is unchanged.

### Baseline redesign results — before the broadcast follow-up

| Check | Result |
| --- | --- |
| Regular + QA ARM debug | Both assembled, arm64-v8a + armeabi-v7a |
| Unit suites | 1,652 passed validations: app 221 regular + 221 QA, Core 939, player-core 271; zero failures/errors/skips. Unchanged Core/player local test outputs were reused; fresh CI runs those suites. |
| New regression tests | 12 broadcaster/fixture/expiry/DST/library tests, 5 presentation/Back-state tests, 3 Twitch activation validation tests, per app variant |
| Lint | Both pass: zero errors, 100 warnings, 22 hints each; existing warnings retained, no suppression/baseline added. Initial RememberInComposition error fixed with remembered focus requesters. |
| Device test APKs | Both compiled; 7 existing routing/identity/backup tests per identity not executed |
| APK checks | Both identities/providers/permissions/icon activities/link schemes disjoint; no HOME; ARM and signatures verified; QA debug pseudolocales verified |
| Source CI checks | Literal ratchet, localized numbers and text overflow pass; 45-entry existing literal debt unchanged |
| Core source | Exact pin `adca2bcd653f19e6e5d659c2722309e6aef4ec15` verified unchanged |

Clean local QA APK: **89,392,836 bytes** versus preceding clean local companion APK
89,324,668 bytes: **+68,168 bytes (+0.076%)**. Raw DEX +175,344 bytes; **all 26 native
libraries are byte-identical**. No new runtime dependencies. Warm incremental APK
padding was excluded by reassembling only generated APK outputs. This is an APK
comparison, not an Android RAM/decoder/frame-time measurement.

Local QA SHA-256: `edc05b25d01ef968c47c20ef32b7db2332829b83a275b80fd97a076ce5ec10f8`.
Local debug certificate SHA-256:
`2b8407729a5bf707603756248a9873a295718f39f1cb52061cb84979081c86b1`.
The fresh CI artifact has its own checksum/certificate files: do not substitute these
local values for the CI APK or assume either matches the physical installed QA app.

Build the documented pinned Core first, then run:

```bash
bash tools/prepare-core.sh
./gradlew :app:testStandardDebugUnitTest :app:testQaDebugUnitTest \
  :OwnTV_Core:core:testDebugUnitTest :OwnTV_Core:player-core:testDebugUnitTest
./gradlew :app:lintStandardDebug :app:lintQaDebug
./gradlew :app:assembleStandardDebug :app:assembleQaDebug \
  :app:assembleStandardDebugAndroidTest :app:assembleQaDebugAndroidTest
```

JDK 21, the repository Gradle wrapper and Android SDK 37/Build Tools 37.0.0 are used.
The prototype workflow performs these checks and uploads workflow artifacts under the repository’s GitHub access rules, not a Release. The separate i18n workflow also
assembles standard debug/release and verifies pseudolocale packaging. Current-head
CI results and the QA artifact link are recorded in PR #3 after those runs finish.

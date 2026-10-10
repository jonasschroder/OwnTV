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
  Home. Focus requesters restore the selected scorecard/Home entry. Table/source rows
  are focusable so the remote can scroll disclosures rather than trap focus at tabs.
- Opening Matchcenter stops the existing preview. Selecting a channel uses the
  existing playback/promotion callback. No preview on hockey/channel-picker focus,
  no autoplay, no second decoder or inferred stream URL.
- Dates/times use `Europe/Stockholm` and Swedish formatting. Result labels say
  **Ej liveuppdaterat** / **Resultatbild** rather than implying a final/live score.
  One update indicator includes the date when the snapshot is from another day.
  Detailed fetch/source times remain in **Datakälla och uppdatering**. Live periods
  and lineup retrieval stay disabled.
- The existing experimental Swehockey reader stays off by default. Disabling it
  cancels the visible-content coroutine and performs no SHL requests/polling.
  Existing timeout/cache/robots/401/403/429 fail-closed safeguards are retained.
- Twitch Home is a small verified LIVE/Offline/status card. Setup prioritises
  **Anslut Twitch**, validated Twitch public-client activation URL/code and failure feedback. Encrypted device-local
  token storage and visible/foreground-only polling are unchanged.

## TVmatchen feasibility and permission report — 10 October 2026

**Outcome: no automatic TVmatchen retrieval is implemented.** An authorized API or
licence has not been established. This is an access/permission uncertainty, not a
claim that TVmatchen offers no API or prohibits all reuse.

User-supplied examples:

- [Färjestad–Malmö match page](https://www.tvmatchen.nu/match/farjestad-bk-malmo-redhawks-1896181):
  10 October 2026, 15:15 Stockholm; user reports TV4 Sport Live 2 and TV4 Play Hockey.
- [3 October SHL matchday page](https://www.tvmatchen.nu/sport/ishockey/shl-idag-3-oktober-2026/).

The cloud environment's HTTPS proxy rejected CONNECT to `www.tvmatchen.nu` with
**403 Forbidden before reaching the origin** when attempting `robots.txt`. Thus
site availability, robots directives, linked terms, documented API and licensing
conditions could not be independently reviewed. Public HTML and the supplied
examples establish neither an extraction licence nor current origin availability.
Additive `tvmatchen.nu` / `www.tvmatchen.nu` entries were saved in the environment
configuration draft; review/save/publish is still needed for a fresh research check.
No alternate proxy, spoofed application, private/authenticated API, access bypass
or HTML scraper is used. Network allowance alone will not authorize extraction.

Before adding an adapter, contact TVmatchen through its official website and obtain
written permission/licence covering fixture-to-broadcaster reuse in this Android
app, authenticated API documentation, terms/attribution, retention, rate limits,
change/withdrawal handling, and the allowed deployment scope. Review origin robots
and terms again. Respect restrictions/403s; do not infer permission from robots
allowance. Until then the adapter remains network-free.

### Safe interface and matching

`BroadcastMetadataSource` is separate from `ShlRepository` and playback. The shipped
`NoBroadcastMetadata` returns no assignment and owns no HTTP client/timer/cache.
Nothing requests TVmatchen on Home, match selection, or in the background.

A future licensed assignment must include SHL, both canonical team identities,
Stockholm fixture date, an exact `Instant` faceoff, source, check time, expiry and
bounded broadcaster names. Unknown/ambiguous teams, swapped teams, different league,
rescheduled games, future checks, expired or over-24-hour records fail closed.
UTC source timestamps must be parsed as instants, never guessed local times; real
Stockholm zone rules distinguish summer/winter offsets and the repeated autumn hour.

Only conservative country/quality wrappers are removed for channel-name matching:
`TV4 Sport Live 2`, `TV4 SPORT LIVE 2 HD`, `SE | TV4 Sport Live 2` and
`SE: TV4 Sport Live 2 FHD` match. **1/2/3/4/12 are distinct**, and extra channel
suffixes/unknown country wrappers are not fuzzy-matched. Multiple entity IDs/quality
variants/sources remain choices rather than selecting the first stream.

**TV4 Play Hockey is never promoted to a linear channel.** Users can manually choose
an actual corresponding entry already provided by their configured service. This
code does not certify the service's rights, generate URLs, use pay-TV credentials,
or access a stream outside the configured IPTV library.

A reliable assignment is shown in match details with its update time. One reliable
library match permits **Se matchen**; multiple entries use the picker. A known
assignment without a match says **Kanalen finns inte i din lista**. The shipped
network-free source naturally falls back to stored EPG/manual **Välj TV-kanal**.
The external **Öppna TVmatchen** action opens their home page in an installed browser,
only on explicit OK. It does not guess a match slug from time/teams, embed a WebView,
fetch arbitrary pages, or establish a broadcast assignment. Users can locate the
fixture there, return and manually choose their channel. If Chromecast has no browser,
consult the supplied link or TVmatchen on the Mac instead. Browser launch failure
shows the existing app-unavailable message; playback remains independent.

A future permitted adapter must use cancellable visible-content requests, bounded
responses, conservative matchday caching and no application-wide background service.
Its assignment is revalidated when schedule/time/identity changes and before matching.
No broadcasting lookup blocks the player.

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

Actual automated results and size/certificate evidence are recorded below and in
PR #3 when the final build completes. Cloud compilation is not a Chromecast render,
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
- TVmatchen external browser (or Mac fallback) and manual channel choice; no automatic
  broadcaster assignment is expected in this build.
- Legitimate Twitch public-client login, failure feedback, cancellation, token refresh,
  verified offline/live state and no background polling; SmartTube/SVT shortcuts.
- Measure actual RAM/frame drops/provider sessions against the previously installed
  QA build, then reopen original Min TV and verify its data is unchanged.

### Actual local results — 10 October 2026

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

Clean local QA APK: **89,392,788 bytes** versus preceding clean local companion APK
89,324,668 bytes: **+68,120 bytes (+0.076%)**. Raw DEX +175,284 bytes; **all 26 native
libraries are byte-identical**. No new runtime dependencies. Warm incremental APK
padding was excluded by reassembling only generated APK outputs. This is an APK
comparison, not an Android RAM/decoder/frame-time measurement.

Local QA SHA-256: `6276a4c31654b1a92cc0491970613052606d9b01f89b6c2c1ae497b772250480`.
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
The prototype workflow performs these checks and publishes private-to-authorized-
GitHub-viewers workflow artifacts, not a Release. The separate i18n workflow also
assembles standard debug/release and verifies pseudolocale packaging. Current-head
CI results and the QA artifact link are recorded in PR #3 after those runs finish.

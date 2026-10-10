# Min TV v0.2 — lightweight hockey and Twitch status

For the latest full-screen TV redesign, broadcast-source feasibility, Twitch build
configuration and device checklist, see [the redesign guide](min-tv-redesign.md).

This continues PR #3. The user has installed and exercised the earlier v0.2 QA
on Chromecast with Google TV 4K / Android 14. The changes described here still
need physical testing. Regular Min TV v0.1 is not migrated, replaced or modified.
Use **Min TV Test**, package `se.jonasschroder.mintv.qa`.

## Use the prototype

IPTV favorites and the existing muted preview remain above companion content.
On Home, **Show all SHL games** opens Matchcenter. Select **Enable experimental
reader** only for permitted personal testing; **Disable reader** cancels its
requests and timers. It is off by default. No lineup or live-period requests are
made. This setting is stored separately in this installation, outside Core's
IPTV backup format; importing a backup does not enable the reader.

On ordinary days, hockey occupies one horizontal FBK row showing the next
scheduled game, when known, and the Matchcenter shortcut. Matchdays use Stockholm
dates and expand modestly below Favorites: FBK first, time/result snapshot,
Watch/Choose TV channel, up to two other pairings, FBK position/points and the
shortcut. **Collapse** persists compact-only presentation; **Expand matchdays**
reenables automatic expansion. At midnight the layout becomes compact if the
new day has no games. Full standings are displayed only in Matchcenter.

Results are **schedule snapshots, not confirmed live scores or final status**.
Matchday/Matchcenter shows both the fetch time and the source's reported update
time, when available. Refresh failures retain clearly labeled cached data; no
data means unavailable. No lineup, live period, provisional table, placeholder
score or generated match is supplied.

**Watch** is offered only for one locally verified EPG candidate. The programme
must name both teams (known aliases accepted), start within one hour of faceoff,
cover faceoff and last at most five hours. Replay/highlights/studio listings and
descriptions containing another concurrent pairing are rejected. Generic “SHL”
alone never confirms a game. Multiple candidates open a picker; manual choices
are explicitly unconfirmed. Channel-name search supports providers whose sports
channels do not use the default search terms. Hockey focus never starts a preview.
OK uses the existing Home-to-Live fullscreen promotion path with the current
profile/source/hidden/adult checks. It makes no hockey request to start IPTV.

Discovery scans at most 128 active-profile favorites/sports channels and 12 stored
programmes per channel. It respects manual EPG IDs and global/per-channel clock
offsets. It does not fetch short EPG from the provider or synchronize XMLTV.
Providers with only short EPG or missing/outdated stored guide data need manual
selection. At most 24 confirmed and 24 unconfirmed choices are rendered at once; type a more specific
channel name for others. Stale/failed schedule refresh disables automatic matching.

## Sources and access audit — 10 October 2026

| Source | Verified | Limitations / decision |
| --- | --- | --- |
| [Swehockey](https://stats.swehockey.se/) | HTTPS homepage, current SHL schedule and standings returned 200; current SHL ID can be discovered from the homepage | Public HTML, not a documented API. Copyright notice; no explicit automated-reuse grant found. Personal experimental reader only, off by default |
| Swehockey `/robots.txt` | Returned 404 during audit | Reader checks it before page requests; a later disallow rule or access denial stops requests |
| [SHL live](https://www.shl.se/live) | HTTP 403, Cloudflare error 1010, `cfOrigin;dur=0`; its robots URL also returned 403 | Access is blocked at SHL's Cloudflare edge. No browser impersonation, alternate endpoint or restriction bypass attempted |
| [troelskn/swehockey](https://github.com/troelskn/swehockey), [msjoelin/swehockey_scraper](https://github.com/msjoelin/swehockey_scraper), [GSHimself/hockey-api](https://github.com/GSHimself/hockey-api) | Reviewed discovery/schedule/standings extraction approaches | These references do not establish API guarantees or permission. No implementation copied, backend added or permanent season ID used |
| [Twitch OAuth](https://dev.twitch.tv/docs/authentication/getting-tokens-oauth/#device-code-grant-flow), [Get Streams](https://dev.twitch.tv/docs/api/reference/#get-streams), [validation](https://dev.twitch.tv/docs/authentication/validate-tokens/) | Official documentation reachable; public-client device flow explicitly needs no secret; Get Streams accepts user tokens without scopes | Real account authorization/API status on the Chromecast is not yet tested |
| [SmartTube configuration](https://github.com/yuliskov/SmartTube/blob/6f9b5f2c82f280027c690967a71afb7022effac7/smarttubetv/build.gradle) | Current Stable package `org.smarttube.stable` verified from source | Actual installed-app launch/deep link still requires device testing |

The environment allowlist initially blocked source access. After its update,
Swehockey/Twitch became reachable; SHL's remaining denial is distinct from that
initial proxy block. Public accessibility/robots absence is not a license to
redistribute data. No new data source is enabled automatically or bundled in APKs.

`ShlRepository` isolates transport/cache from `SwehockeyParser` and the compact
models/UI, so an authorized official API can replace extraction. The parser only
accepts the named tables and known column headings, excludes duplicate mobile
cells, recognizes known standings separators, and requires all 14 unique ranks.
Unknown layout, malformed rows/IDs, wrong-season dates, oversized responses and
ambiguous discovery fail closed rather than publish a partial or guessed table.
Current season IDs are discovered; published game IDs are used when present,
otherwise the schedule's fixture ID is used only for local identification.
Fixture IDs are **not** assumed to work in lineup/live endpoints.

The robots policy is intentionally conservative: **any nonempty Disallow rule**
suspends this reader, even if it might concern an unrelated path. HTTP 401/403/429 on
robots/pages also sets a persistent access-blocked flag. No further automatic
requests are made after that flag is set. Keep the reader disabled and investigate
permission/source changes before a future explicit reset implementation; do not
clear app data to work around it. Ordinary transient errors use backoff instead.

## Twitch: optional personal authentication

S0undTV's install shortcut, deep link, helper and package-visibility query are
removed. The ohnePixel row uses only Twitch Helix **Get Streams**, with LIVE,
title and localized viewer count, or Offline after a successful empty response.
Errors, missing login and rejected/expired tokens show **Status unavailable**.
No avatar is fetched in this iteration; no screenshots, thumbnails or video exist.

1. On your Mac, sign into [Twitch Developer Console](https://dev.twitch.tv/console).
   Follow [Register Your App](https://dev.twitch.tv/docs/authentication/register-app/),
   enable the required account 2FA and register your personal app as **Public**
   client type. If a redirect URL is required, use `http://localhost`; device-code
   login does not start a redirect listener. Do not generate/share a client secret.
2. In Min TV Test → **Twitch status setup**, enter the **public Client ID**, then
   **Connect with device code**. Use the Google TV keyboard/input facility to type
   it; entering a Client ID does not grant account access.
3. On your Mac visit `https://www.twitch.tv/activate`, enter the displayed code and
   approve only your own app. No account scopes are requested. Keep the TV dialog
   visible until login completes. Back/Close/background cancels pending login.
4. Return to the visible Twitch row. It queries approximately once per minute
   while foreground and pauses during favorite-row focus/preview loading.
   **Forget local login** removes local credentials. For server-side revocation,
   remove the connection in Twitch account settings; forgetting locally does not
   send a revocation request.

Tokens and rotated single-use refresh tokens are AES-GCM encrypted with an
Android Keystore key in `noBackupFilesDir`. They are not included in IPTV backups,
cloud build inputs, URLs, diagnostic logs or artifacts. Only the public Client ID
is entered. Validation runs on first use and hourly while active; expiration
refreshes without a client secret. Lost/rejected/interrupted refresh requires
login again; no secret/server fallback exists. Device polling honors Twitch's
interval and expiry. There is no background login or status service.

## Performance and resource boundaries

- No runtime dependencies, player, decoder, WebView, service, WorkManager job,
  wake lock, image asset or media engine was added. SmartTube remains an explicit,
  package-scoped shortcut; IPTV/preview/player implementations are retained.
- Reuses Core's ordinary OkHttp client and connection pool. Every call has an
  eight-second total timeout and cancels with its coroutine. Parsing/file work
  runs on IO/OkHttp workers. Companion clients share that pool/dispatcher and disable all redirects.
- Only actually visible LazyColumn items or their open dialog activate companion
  work. Leaving Home, fullscreen, background, favorite-row focus or preview
  loading cancels requests. Disabled SHL has no requests or timers. Initial Home
  rendering never waits for hockey/Twitch; watching IPTV never waits for them.
- Schedule JSON is app-private cached, at most 400 games / 200 KB cache file;
  schedule source reads are capped at 600 KB. No HTML document is retained after
  parsing. Standings contain only rank/name/points for 14 teams, with a six-hour
  in-process cache. No full logos or historical seasons are fetched.
- Schedule network TTL is six hours on ordinary days, ten minutes on matchdays;
  standings TTL is six hours, fetched only for expanded matchdays or explicit
  standings view. Robots TTL is six hours per reader instance. HTTP/parser failures
  back off from two to fifteen minutes while visible. Blocked access never retries.
- Twitch responses are bounded at 32 KB (16 KB for authentication); status polls
  every 60 seconds, only while visible/foreground. No status is persisted as proof
  of Offline. No new network traffic occurs for unconfigured Twitch.

These are design bounds, **not a measured Android RAM delta**. No physical device
is attached to the cloud. APK comparisons and actual test results are recorded
below and in PR #3; Chromecast PSS, native/graphics memory, frame time, decoder
counts and sustained playback cannot be inferred from compilation or APK size.

## Safe QA update and installation

Use the updated **MinTV-v0.2-QA.apk** artifact linked in PR #3, then follow the
[Mac → Downloader guide](min-tv-qa.md#install-from-a-mac-using-downloader).
The installer must say **Min TV Test**. Keep regular Min TV installed.

**An earlier QA app is already installed.** Fresh Actions runners normally create
different debug keys. Before updating QA, export a passphrase-protected `.own`
backup from **QA**, save it privately on the Mac and verify it through restore
preview. Compare the installed QA APK/certificate with the new candidate using
[the signing gate](min-tv-v0.2.md#safe-installationupdate--read-first), substituting
`se.jonasschroder.mintv.qa` and `MinTV-v0.2-QA.apk`. The downloaded prior PR #3 QA artifact (run `37993245937`, versionCode 8)
has public certificate SHA-256
`bd70fc1a73455ea41ca51449995cab60cb62e89de725f6cec24d2d76f90a3080`.
The local cloud key differs; the installed device certificate must still be
checked. If keys differ, Android cannot
update QA in place. Prefer rebuilding with QA's retained original key. Stop on a
mismatch; this PR does not uninstall, clear, migrate or replace either app.
Any later QA-only backup migration requires a separate deliberate manual decision.
Core backups copy IPTV configuration/favorites/settings, not the new SHL toggle
or Twitch credentials; reenable/relogin explicitly after any migration.

## Physical acceptance and memory comparison

Keep the installed QA baseline until its backup/signing gate is resolved. With
authorized USB ADB, obtain comparable measurements without clearing data:

```bash
adb shell dumpsys meminfo se.jonasschroder.mintv.qa
adb shell dumpsys gfxinfo se.jonasschroder.mintv.qa
```

Capture three samples after 60 seconds in each state: Home with SHL off and
Twitch unconfigured; enabled compact Home; expanded matchday; Matchcenter table;
muted real IPTV preview; fullscreen 4K playback. Repeat the same states/channel,
codec, profile and device settings after a **certificate-compatible** QA update.
Compare median TOTAL PSS, native/graphics heaps and frame statistics. Do not
force-stop the working regular application, clear caches/data, or alter Google
packages for these checks. No numeric Chromecast memory delta is claimed yet.

Required device checks: zero requests with SHL off; request cancellation on
Back/Home/background/fullscreen; cold Home responsiveness; matchday/midnight/DST
layout; cached timestamps/errors; D-pad dialog scrolling/focus restoration;
real EPG aliases/timing/multi-channel/manual choice; no hockey autoplay; unchanged
muted-preview/fullscreen/audio/Stalker/provider limits; Twitch public-client
approval/refresh/logout/offline/error; SmartTube Stable startup/video link; and
regular v0.1 profiles/favorites retained. Lineups/live periods remain disabled.

See [stable signing and encrypted migration](min-tv-signing.md). No key, credential,
release publication, merge, installation or Android HOME/system modification is
part of this change.

## Cloud verification and measured APK impact

Local results on 10 October 2026, compared with the downloaded PR #3 QA baseline
(run `37993245937`, commit `ab689d3a`, same Core/toolchain and ARM ABIs):

| Check | Actual result |
| --- | --- |
| Regular / QA ARM debug APKs | Both compiled and packaged; arm64-v8a + armeabi-v7a |
| Unit suites | 1,612 passed executions: app 201 regular + 201 QA, Core 939, player-core 271; zero failures/errors/skips |
| App lint | Completed local run: 0 errors, 99 warnings, 22 hints per variant; includes 5 new SharedPreferences KTX suggestions |
| Instrumentation | Both test APKs compiled; 7 tests per identity, not run on a Chromecast/emulator |
| Package/signature checks | Passed: distinct labels/IDs/providers/permissions/links, nine icon activities, no HOME, current SmartTube query and no S0undTV query |
| Source / debug packaging | i18n ratchet, number/overflow and debug pseudolocale checks passed; translation-debt baseline unchanged |
| Actual source parser smoke | Audited Swehockey pages accepted: current ID discovered, 364 schedule games and 14 standings entries |
| QA baseline APK | 89,205,928 bytes |
| New clean local QA APK | 89,324,668 bytes (85.19 MiB) |
| APK delta | **+118,740 bytes (+0.133%)**, using actual APK files, not ZIP download size |
| Code/native comparison | Raw DEX +228,796 bytes; all 26 native libraries byte-identical; no added runtime dependency/image |
| Physical Android RAM/frame time | **Not measured**; use the controlled device comparison above |
| Authenticated Twitch/device IPTV | Not executed in cloud; no account credentials supplied |

A warm incremental package initially contained about 6.8 MB of unused ZIP space.
Deleting only the generated regular/QA APK outputs and rerunning their assemble
tasks produced clean packages; no source/data/key was deleted. CI starts from a
fresh checkout and produces clean packages. The new Actions APK/ZIP byte count,
certificate, final CI run and download link are recorded in PR #3 after completion.
The source-page smoke ran on desktop JVM (cold schedule parse about 0.3 seconds,
standings 6 ms); that is not a Chromecast timing or RAM measurement.

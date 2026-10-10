# Min TV v0.2 — live IPTV Home inside a normal app

**Permanent signing continuation:** ordinary PR Actions no longer upload ephemeral
installer APKs. The owner chose one retained shared key for QA/Stable and accepts
planning an initial clean adoption of both apps; physical execution still needs
approval. Both protected signing environments are reported configured. Actual
owner-key signing is blocked by the still-unmerged main workflow. Use
[the current Mac/TV guide](min-tv-permanent-updates-mac.md) and
[acceptance report](min-tv-cloud-updates.md); the older debug-transfer examples
below are historical and do not identify a currently available permanent APK.

For first physical testing while retaining v0.1, use the separate
**[Min TV Test QA build and Mac installation guide](min-tv-qa.md)**
(`se.jonasschroder.mintv.qa`). The regular APK below remains an update candidate;
the QA APK installs alongside it without requiring its signing key.

Target: Chromecast with Google TV 4K, Android 14. Application ID remains
`se.jonasschroder.mintv`; Kotlin namespace remains `tv.own.owntv`. No database
migration, IPTV credentials, bundled channels or signing keys are introduced.
OwnTV and Min TV remain separate installed apps.

**Do not disable, override or replace Google's launcher or any Google system
package.** v0.2 removes the v0.1 HOME Activity/intent registration. Open Min TV
from its normal Google TV app icon; the physical Home button belongs to Google TV.
There are no system-launcher commands in this build or installation process.

## Architecture review and implemented behavior

The review was completed before implementation:

- `HomeViewModel` uses Core's `HomeFeedReader`. Its favorite list joins `FavoriteDao`
  through `ChannelDao.favoritesListAlpha()`, filters active/enabled profile sources,
  hidden content and adult policy. Room
  favorite changes refresh the feed; a profile identifier prevents stale rows being
  shown during a profile switch. `HistoryDao` remains the existing watch-history
  path; focusing a preview never records a watched channel.
- `LiveViewModel` owns one injected `LivePreviewEngine` and one pinned Core
  `LiveTuneController`, with the existing mpv/Exo engine pair. Home uses those same
  instances in `OwnTVShell`, rather than an independent playback Activity. Normal
  app entry opens this Home; explicit IPTV links still retain their existing routing.
  Setup, profile selection/PINs, sources, player controls and channel zapping remain
  in the existing shell. Legacy per-profile automatic startup playback/Favorites
  destinations are not executed in Min TV; their stored preferences remain intact.
- `LiveStagePane` renders the existing Live page. Home uses its same
  `ExoPreviewSurface` mechanism directly for a wider featured composition, keeping
  EPG beside the picture. The Home pane uses a TextureView so video clips within
  its rounded card; DRM channels use the existing Live pane's SurfaceView path.
  Fullscreen uses the existing hardware SurfaceView for quality,
  HDR and frame-rate handling. Core's identity-checked `detachSurface(oldSurface)`
  prevents a late destruction of the old view detaching the new fullscreen surface.
- `HomeLivePreviewController` is a focus/lifecycle policy, not another player. It
  waits 800 ms, cancels obsolete delayed work, and stops the prior Core request on
  a different channel before another starts. Refocusing an already playing channel
  reuses it. It starts nothing on initial/restored focus; deliberate D-pad movement
  within the favorite row arms preview after startup/resume. Leaving that row
  cancels/stops preview; EPG polling runs only while the Activity is resumed. The existing preference can disable
  preview entirely using **Förhandsvisning: på/av** in Home’s **Inställningar** panel (shared with Live TV).
- The channel name/EPG selection changes immediately on focus. Home calls the shared
  `LiveEpgReader` without Live's 350 ms metadata debounce. Old results are cleared and
  their coroutine is cancelled; bulk guide/cache is preferred, with existing provider
  short-EPG fallback. A network-dependent answer can take time: missing data is shown
  honestly. Progress updates every 30 seconds; expired cached programmes are refreshed.
- Preview is always muted even if the existing Live page audio preference is on.
  Core's mute implementation sets volume to zero and deselects audio tracks for video
  streams, including passthrough safety. Home never starts a movie/trending hero decoder.
  Core's low-spec budget still caps preview at 720p where applicable; the full-quality
  limit is restored by the existing `liveOnExo` observer on promotion.

### Fullscreen handoff and lifecycle

OK cancels Home's delayed request **without stopping the selected preview**. The
existing Home playback path re-reads and checks the channel against the active profile,
arms zapping from the favorite rail, and calls `ensurePlaying()`:
`expectPromotion()` selects the Exo fullscreen surface immediately if that channel's
preview is usable; `start()` unmutes/reuses the same URL or Stalker command. The
existing controller handles engine selection and Exo-to-mpv fallback, stopping the
old engine before the other claims the decoder/provider connection. Existing external
player settings are respected; they do not mount an unused fullscreen player.

This can avoid a new connection for a healthy Exo preview of the same source. It is
not a promise of zero black frames: surface replacement, track adaptation, provider
reconnects, timeshift/local-copy policy, explicit mpv pins and unsupported Exo formats
can require the existing safe playback path. mpv-only channels have no separate Home
mpv preview; OK still uses the canonical fullscreen path/fallback. HLS/TS/DRM and
Stalker behavior need real provider/device testing.

Home's synchronous ON_PAUSE observer stops/cancels preview before Activity onStop
can snapshot it for automatic restoration. Opening an external app stops preview
before launching the intent. Fullscreen promotion is exempt from Home disposal so
its stream survives surface handoff. Back from fullscreen stops the detached stream
before restoring Home focus; new remote interaction can preview again. This favors
silence/no hidden connections over keeping an unmuted stream alive after Back.
Fullscreen/mini/audio/Multiview task return retains the existing resume behavior.
A cold Activity or a new normal app-icon intent discards old engine snapshots;
restoration is decided at onResume, after onNewIntent, and allowed only when the
shell actually retains a player/grid. Normal entry closes a retained Multiview grid
as well as fullscreen/mini/audio playback before displaying Home. This prevents
an old stream reconnecting underneath Home. Process recreation starts Home
without autoplay after the normal profile/onboarding gate.

## Home and external content

The navy/teal Home contains actual live video, current/next EPG/progress and a real
favorite-channel row. **TV-kanaler** opens the existing browser and **TV-guide**
opens the existing guide. The redundant Sport button is removed; existing sports
categories remain available through the Live TV browser. See the
[full-screen SHL/TV redesign](min-tv-redesign.md) for the current Swedish UI.

- **ohnePixel status:** optional public-client Twitch device-code login and a
  compact Helix status row. No playback, S0undTV route/query or installation
  shortcut. Unconfigured/error status is unavailable. See
  [companion setup, source limitations and performance](min-tv-companions.md).
- **YouTube · SmartTube:** a working app shortcut. Recognized packages are current
  `org.smarttube.stable`, `org.smarttube.beta`, `app.smarttube.fdroid`, plus older
  `com.teamsmart.videomanager.tv` and `com.liskovsoft.smarttubetv.beta`. A verified
  helper can route a specific 11-character video ID through a package-scoped
  `https://www.youtube.com/watch?v=...` intent. No video-feed UI invokes it yet:
  configurable channels/metadata access are deferred. No ordinary YouTube fallback.
  If missing, installation instructions point to official `smarttubeapp.github.io`.
- **SVT Play:** resolve the official `se.svt.android.svtplay` TV/app launcher at
  runtime. If missing, offer its Google Play listing, scoped to `com.android.vending`.
  Installed package resolution and actual startup still need a Chromecast test.

Package visibility is declared explicitly; no QUERY_ALL_PACKAGES permission is added.
Failures are shown in a dialog, without installing apps or changing the system.

Verification references:
[SmartTube current package configuration](https://github.com/yuliskov/SmartTube/blob/6f9b5f2c82f280027c690967a71afb7022effac7/smarttubetv/build.gradle),
[VIEW manifest](https://github.com/yuliskov/SmartTube/blob/6f9b5f2c82f280027c690967a71afb7022effac7/smarttubetv/src/main/AndroidManifest.xml),
[video ID extraction](https://github.com/yuliskov/SmartTube/blob/6f9b5f2c82f280027c690967a71afb7022effac7/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/utils/IntentExtractor.java),
and [older package configuration](https://github.com/yuliskov/SmartTube/blob/bec4eb70cc2cdc1582ae64702e3621beca4a592d/smarttubetv/build.gradle).
These verify contracts, not installed-app behavior on the target hardware.

## Safe installation/update — read first

The historical Actions ZIP was named **MinTV-v0.2-arm-debug** and included an APK, SHA256SUMS,
READ-BEFORE-INSTALL.txt and its public signing certificate. Current prototype
runs upload reports only. Future approved permanent candidates come from
**Min TV approved signing**, with exact filenames in their signed metadata;
follow the current guide and do not install a historical debug ZIP as an update.
It expires after 14 days. It is a debuggable test build, not a public Release.

**A fresh CI runner normally signs with a new debug key. Do not assume this APK
can update the already installed v0.1, and do not uninstall to bypass a mismatch.**
There is no guaranteed in-place migration across unrelated signing certificates.
Android rejects an incompatible `install -r` without deleting existing app data.

1. With v0.1 still installed, use its **More → Backup & Restore** (also available
   from Settings). Select all needed profiles and backup sections, including sources,
   favorites and settings. Set a backup passphrase if you need credentials preserved;
   the existing exporter intentionally omits secret password fields without one.
2. Transfer the backup off the Chromecast. Inspect it through the existing restore
   preview and check profiles/source/favorite counts; keep the passphrase. Backups
   contain private configuration and potentially credentials: keep them encrypted
   and never attach them/logs with credentials to GitHub.
3. Compare the original installed APK with the candidate. With USB ADB:

   ```bash
   adb devices -l
   adb shell pm path se.jonasschroder.mintv
   # Copy the base.apk path printed above, without the "package:" prefix:
   adb pull /data/app/PATH_FROM_PREVIOUS_COMMAND/base.apk MinTV-installed.apk
   python3 tools/check-apk-update.py --previous MinTV-installed.apk --candidate MinTV-v0.2-arm-debug.apk --aapt2 "$ANDROID_HOME/build-tools/37.0.0/aapt2" --apksigner "$ANDROID_HOME/build-tools/37.0.0/apksigner"
   ```

4. Only when signers match and versionCode increases, install with
   `adb install -r MinTV-v0.2-arm-debug.apk`, or the Android installer through
   Downloader. Do not use downgrade/uninstall/clear-data workarounds. Open Min TV
   from Google TV and verify your profiles/favorites remain.

If signers differ, **stop and keep v0.1 installed**. Prefer rebuilding v0.2 using
the original signing key if it exists. An ephemeral v0.1 CI private key cannot be
recovered from its public certificate/APK. A manual uninstall/reinstall/restore is
an optional migration only after verifying the encrypted backup and deliberately
accepting deletion of local app data; it is not an update and is never automatic.
Uninstalling without that backup loses Min TV profiles, credentials and favorites.
This PR does not provide an automatic migration or claim a mismatch is harmless.

### Downloader transfer for a new install or a verified compatible update

Install Downloader by AFTVnews from Google Play on the Chromecast. Its browser
cannot install a GitHub artifact ZIP directly. Copy **only** the unzipped APK into
an empty folder named `MinTV-share` on a computer on your trusted home Wi-Fi:

```bash
python3 -m http.server 8765 --bind 0.0.0.0 --directory MinTV-share
```

Find the computer's local IPv4 address in its network settings. Enter
`http://COMPUTER_IP:8765/MinTV-v0.2-arm-debug.apk` in Downloader, download, and
permit installation from Downloader when Android asks. Do not forward the port
on your router or place backups/credentials in the shared folder. Stop the server
after the transfer. Send Files to TV is another local transfer option.
The system installer will refuse a certificate mismatch; keep the installed app
and follow the signing procedure above. No launcher activation is needed.

### Consistent signing for future versions

Local debug builds can reuse a private existing key with out-of-repository settings:
`MINTV_DEBUG_KEYSTORE_FILE`, `MINTV_DEBUG_KEYSTORE_PASSWORD`,
`MINTV_DEBUG_KEY_ALIAS`, `MINTV_DEBUG_KEY_PASSWORD` (or corresponding
`mintv.debugKeystoreFile`, `mintv.debugKeystorePassword`, `mintv.debugKeyAlias`,
`mintv.debugKeyPassword` in a private Gradle properties file). Set VERSION_CODE
higher than the installed APK's code. Never put passwords on a logged command line
or commit/upload keys. Regular PR CI uses its ordinary ephemeral debug key and
does not access signing secrets.

For permanent distribution, retain the owner-created key already configured in
both protected signing environments. Do not regenerate it. QA and Stable use the
same public certificate by explicit owner choice, but keep different packages,
private data and authenticated update channels. Secret-free unsigned builds and
native signing outside Gradle are implemented; real signing requires reviewed
main integration and manual environment approval. Release publication is separate
and is not authorized here. See [the current signing guide](min-tv-signing.md).

## Reproducible build and automated checks

Use JDK 21, SDK 37 / Build-Tools 37.0.0, Gradle 9.7.1. See
[the cloud baseline](build-baseline.md). Compatible Core remains exactly
`adca2bcd653f19e6e5d659c2722309e6aef4ec15`; no Core source modification is needed.

```bash
bash tools/prepare-core.sh
./gradlew :app:assembleStandardDebug :app:testStandardDebugUnitTest :app:lintStandardDebug :app:assembleStandardDebugAndroidTest :OwnTV_Core:core:testDebugUnitTest :OwnTV_Core:player-core:testDebugUnitTest --max-workers=4
python3 tools/verify-mintv-apk.py --apk app/build/outputs/apk/standard/debug/app-standard-debug.apk --aapt2 "$ANDROID_HOME/build-tools/37.0.0/aapt2"
python3 tools/i18n/check_hardcoded_strings.py verify-ci --base-sha "$(git merge-base HEAD origin/main)"
python3 tools/i18n/check_number_locale.py
python3 tools/i18n/check_text_overflow.py
```

The standard APK contains arm64-v8a and armeabi-v7a. `verify-mintv-apk.py` checks
identity, isolated authorities/permissions, nine preserved app-icon activities,
mintv link routing, ABI contents and **absence of Android HOME registration**.
Virtual-time controller tests cover debounce, cancellation, same-channel reuse,
no startup/resume autoplay, disabled preview, removal and promotion preservation.
Routing instrumentation tests are compiled; executing them requires Android.
CI also assembles debug/release for pseudolocale packaging checks, without publishing
a Release. APK/report uploads use read-only repository permissions.

Recorded cloud results on 9 October 2026:

| Check | Actual result |
| --- | --- |
| ARM standard debug APK | Passed; arm64-v8a + armeabi-v7a; versionName 0.2 |
| JVM tests | 1,400 passed: app 190 (including 13 Home/foreground policy tests), Core 939, player-core 271; no failures/errors/skips |
| App lint | Passed: 0 errors, 94 warnings, 22 hints |
| Instrumentation APK | Compiled; 4 routing tests **not executed** without Android hardware/emulator |
| Manifest/ABI and APK signature verification | Passed; Min TV identity and no Android HOME entry |
| Source i18n/locale/text-overflow checks | Passed; no hardcoded-literal baseline growth |
| Debug pseudolocale APK check | Passed |
| Local v0.1 → v0.2 signing check | Same verified certificate, versionCode 1 → 2; in-place compatible **with that local reference only** |
| Installed Chromecast APK certificate | Unknown; not compared to a physical device |

Local reference/candidate signer SHA-256:
`2b8407729a5bf707603756248a9873a295718f39f1cb52061cb84979081c86b1`.
CI generates its own key; do not infer compatibility from the local comparison.
CI results and downloadable APK/run links are recorded in the PR after the run.
**No physical Chromecast is connected in the cloud. Compilation/source tests are
not evidence of video output, seamless provider handoff or Chromecast performance.**

## Manual Chromecast acceptance checklist (not executed in the cloud)

Use your own permitted IPTV sources; none are bundled. Preserve the existing app
and its backup. Test on Chromecast with Google TV 4K **Android 14**, with Google's
launcher/packages left enabled and unchanged:

1. Open the normal app icon: custom Home follows the existing setup/profile/PIN gate.
   Repeat after backgrounding fullscreen/mini/audio/Multiview: no old stream should
   reconnect under Home. No preview starts until deliberate D-pad interaction, including with a saved legacy
   last/specific-channel startup preference. Original OwnTV still launches.
2. Browse actual favorites with D-pad. Names/logos and current/next programmes change
   with focus; unavailable EPG has no invented data. Test empty favorites/profile switch.
3. After focus settles for 800 ms, real muted video appears. Test AC3/E-AC3 passthrough:
   preview must remain silent even with Live's preview-audio preference enabled.
4. Hold left/right rapidly. Confirm only the final settled channel connects, no
   old provider request wins later, and no more than one preview/hero decoder is active.
5. OK gives fullscreen video with sound/normal quality. For a healthy same-channel Exo
   preview, check provider connection counts/logs to verify no stream restart. Repeat
   before the debounce expires and while loading; preview must not later remute fullscreen.
6. Try mpv-pinned/Exo-incompatible channels, provider one-connection accounts, Stalker,
   Xtream and M3U. Existing safe fullscreen playback, fallback/zapping/controls must work.
7. Back returns to the chosen favorite, stops fullscreen audio/connection, and requires
   fresh navigation to preview. Toggle Preview off; fullscreen OK remains available.
8. Test optional Twitch status login; launch SmartTube, SVT Play and Settings. Verify targets, missing-app
   installation dialogs and Back/resume; no IPTV audio/decoder/provider connection remains.
9. Test permitted HLS/TS, 4K HDR and 50/60 fps sports sources at full quality; compare
   decoder/frame drops with the unchanged Live page. Check EPG and favorites still update.
10. Test prolonged playback, Home/background/standby, memory pressure, profile/PIN changes,
    process recreation and repeated focus/fullscreen transitions. No stale audio/surface.
11. Verify source settings, refresh, backup/restore and all user profiles. Check a compatible
    in-place APK update keeps data. A certificate-mismatched attempt must leave v0.1 intact.

### USB ADB diagnostics

Use the recovered physical USB ADB connection and authorize only your trusted
computer. These diagnostics do not change system launchers or Google packages:

```bash
adb devices -l
adb shell dumpsys package se.jonasschroder.mintv > mintv-package.txt
adb shell dumpsys meminfo se.jonasschroder.mintv > mintv-memory-before.txt
adb logcat -v threadtime -s OwnTV-LivePreviewEngine LiveEngine OwnTVHome AndroidRuntime > mintv-playback.log
# Exercise focus, promotion, Back and external-app launch; then Ctrl-C.
adb shell dumpsys meminfo se.jonasschroder.mintv > mintv-memory-after.txt
adb shell dumpsys media.codec > mintv-codecs.txt
adb shell pidof se.jonasschroder.mintv
```

Compare app PSS/native/graphics memory before/after repeated transitions and after
several minutes idle, together with provider session counts. A stable snapshot
alone does not prove no leak; sustained repeated testing is needed. For process
recreation, background Min TV and run `adb shell am kill se.jonasschroder.mintv`,
then reopen its normal icon. Do not clear app data. Some firmware omits codec details
or process-kill support; record that rather than changing protected system settings.
Logs can contain URLs, source details and credentials despite redaction in parts
of Core; inspect/redact them locally before sharing. Never upload an unredacted
backup, token or raw credential-bearing log as a workflow artifact.

GPLv3 remains in force: keep [LICENSE](../LICENSE) and provide corresponding app
source at the APK workflow's exact head commit plus the pinned Core source and
existing dependency licenses with any redistribution. No GPL notice is removed.

# Min TV v0.1 — Chromecast prototype

Min TV is an independent debug app: **Min TV**, package **`se.jonasschroder.mintv`**,
version **0.1**. It installs beside OwnTV (`tv.own.owntv`) with separate storage,
profiles, credentials, providers and notification identities. It does not import
OwnTV's data automatically. Configure your own sources in Min TV's existing setup
wizard; Xtream, M3U, Stalker, EPG, favorites and both playback engines remain in
the pinned Core/app implementation. No channels or credentials are bundled.

The Kotlin namespace stays `tv.own.owntv`; it is a code namespace, not the installed
package identity. All nine existing IPTV launcher activities retain their names
for Core's icon switcher but use one temporary TV icon/banner. Their external
links use `mintv://play/...` and `mintv://open/live`. Internal Core links are parsed
inside this package. FileProvider, Startup, the live-logo provider and AndroidX's
signature permission use the new application ID after manifest merging.

Core's optional Watch Next/Recent Live card publication is gated off for this host:
its unscoped `owntv://` recommendation links could otherwise open the original
app. This does not disable IPTV or EPG. The updater now points at this fork, never
upstream OwnTV; v0.1 uses workflow artifacts, so in-app update checks cannot fetch
this prototype. Install prototype updates manually.

## What HOME does

`tv.own.owntv.home.MinTvHomeActivity` is an exported, stable `MAIN` + `HOME` +
`DEFAULT` activity in its own task (`se.jonasschroder.mintv.home`), with
`singleTask` launch mode. It is independent of icon switching. The app's existing
`LEANBACK_LAUNCHER` entry still opens the IPTV interface from Google TV's app row.

HOME displays Min TV and two TV Compose buttons: **Live TV** and **Android TV
settings**. Live TV explicitly targets the currently enabled IPTV icon activity,
opens the existing Live section, and retains onboarding and profile/PIN gates.
Settings uses the system `android.settings.SETTINGS` action. D-pad moves focus,
OK activates, and Back at the HOME root stays home. Settings and IPTV retain their
own Back behavior. Focus is saved across activity recreation and requested again
on resume; HOME does not store a selected channel or auto-start playback.

This is a two-button prototype, not an installed-app grid. Use the physical remote
to check focus, Settings return, IPTV exit, Home during playback and process recovery.

## Download and install with Downloader

1. On a computer, sign into GitHub and open this repository's **Actions** tab.
   Open the successful **Min TV prototype** run for this PR/commit. Under
   **Artifacts**, download **MinTV-v0.1-arm-debug**. The PR report links the exact
   artifact. GitHub supplies a ZIP; unzip it to obtain
   `MinTV-v0.1-arm-debug.apk` and `SHA256SUMS`. Artifacts expire after 14 days.
2. On Chromecast, install **Downloader by AFTVnews** from Google Play. Open it
   once. When Android asks, permit installation from Downloader; this may be under
   **Settings → Apps → Special app access → Install unknown apps**. Menu names
   differ by firmware. Do not use OwnTV's public Downloader code: that installs OwnTV.
3. A GitHub artifact page/ZIP is not a direct APK URL. To use Downloader without
   publishing the APK, put **only the APK** into an otherwise empty folder named
   `MinTV-share` on your computer. On a trusted Wi-Fi network shared with Chromecast,
   start a temporary local server in the folder's parent (Python 3 required):

   ```bash
   python3 -m http.server 8765 --bind 0.0.0.0 --directory MinTV-share
   ```

   Find the computer's local IPv4 address in its Wi-Fi/network details, for example
   `192.168.1.20`. In Downloader enter
   `http://192.168.1.20:8765/MinTV-v0.1-arm-debug.apk`, replacing the address.
   Allow the computer's firewall prompt for this trusted network if needed.
   Keep the computer awake; do not forward the port on your router.
4. Choose **Install**, then **Open**. The regular app entry opens IPTV setup;
   configure a profile and a source before testing Live TV. Press Ctrl+C on the
   computer to stop sharing. Downloader's install permission can then be revoked.

If you cannot run a local server, transfer the unzipped APK with a trusted local
file-transfer app such as Send Files to TV and open it with a file manager instead.
This alternative does not require Downloader. No public Release or public APK
hosting is created by this workflow.

Advanced installation from a computer with Android platform-tools:

```bash
adb install -r MinTV-v0.1-arm-debug.apk
adb shell am start -n se.jonasschroder.mintv/tv.own.owntv.home.MinTvHomeActivity
```

The second command previews HOME without changing the default launcher.

## Choosing HOME on Chromecast with Google TV 4K

**No physical Chromecast was attached during development.** Android supports HOME
resolution and a HOME role, but Google TV's settings/firmware determine whether a
chooser is exposed and whether the choice persists. A source-code declaration
alone cannot prove that this Chromecast will let you select Min TV directly.
First try pressing Home after installation: if a chooser offers Min TV, select it
and **Always**. If Settings offers **Default apps → Home app**, that is another
manual route. Do not assume that either UI exists on Google TV.

For firmware without a chooser, use ADB from a trusted computer. On Chromecast,
enable developer options by repeatedly selecting the Android TV OS build entry
under **Settings → System → About**, then enable the debugging option offered by
your firmware. Use network/wireless debugging only on a trusted LAN; authorize
the computer's prompt on the TV. If pairing is required, use the pairing endpoint
shown by the TV, then its separate connection endpoint:

```bash
adb pair TV_IP:PAIRING_PORT
adb connect TV_IP:CONNECTION_PORT
adb devices
```

On firmware exposing legacy network debugging, connection may instead be
`adb connect TV_IP:5555`; use the endpoint the device actually offers, not an
assumed port. Keep this connection available until rollback is tested.

Record the current HOME component and inspect packages **before changing anything**:

```bash
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell pm list packages com.google.android.apps.tv
adb shell pm list packages com.google.android.tungsten.setupwraith
```

Save the original component/package outside the TV. On Chromecast with Google TV,
the historically documented stock package is **`com.google.android.apps.tv.launcherx`**
(not an assumed `...launcher` package). Check your device's actual result.

Try the non-disabling method first, entering the commands yourself:

```bash
adb shell cmd package set-home-activity --user 0 se.jonasschroder.mintv/tv.own.owntv.home.MinTvHomeActivity
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

AOSP's Android 12 and current shell implementations assign the HOME role and report
`Success` or `Error: Failed to set default home.` This operation may be rejected or
reverted by Google TV firmware. Confirm Min TV opens with the physical Home button
and after a reboot. Package resolution is a useful check, not proof of button behavior.

### Optional disabling experiment — explicit manual action only

Disabling Google TV is **not a requirement imposed by Min TV**, and the app/workflow
never runs system-modification commands. Historical Chromecast instructions in
[FLauncher's documentation](https://gitlab.com/flauncher/flauncher/-/blob/master/README.md)
disable both `launcherx` and `setupwraith`; the latter was documented as re-enabling
the stock launcher. Those results are from another launcher on older firmware.
They do not prove this is necessary or safe on your current Chromecast.

Only if the non-disabling route fails, you explicitly choose to test this fallback,
both packages match the discovery output, and you have saved working rollback
commands/ADB access, you may manually enter:

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

Select Min TV if a chooser appears. Do not disable/uninstall other Google packages.
Do not uninstall the stock launcher or use an automated launcher-disabling script.
`setupwraith` is a setup/recovery component; leave it enabled unless you deliberately
choose this documented experiment. Firmware updates may change these behaviors.

### Effects that need physical testing

- The non-disabling role method leaves Google packages enabled. Replacing HOME
  changes which screen the Home button opens; Min TV does not replace Cast services,
  Google Home integration or the Chromecast remote service.
- Disabling the stock launcher removes its discovery/recommendations UI.
  FLauncher reports the **YouTube remote button stops working** in its disabling
  scenario. Netflix, Assistant, power/input/volume, voice search and Settings
  behavior on the current firmware must also be checked; do not promise they work.
- Casting normally involves other system components, but keeping those components
  installed does not prove casting/wake/return-to-home works. Test casting from a
  phone, stopping a cast, wake from standby and Google Home's device/remote controls.
- Pairing/setup, account features, screensaver, reboot and firmware updates may
  involve Google TV components. Test recovery before relying on this as daily HOME.

## Restore Google TV

These are manual recovery commands. For a device confirmed to have the Chromecast
packages above, re-enable **both** before restoring HOME:

```bash
adb shell pm enable --user 0 com.google.android.apps.tv.launcherx
adb shell pm enable --user 0 com.google.android.tungsten.setupwraith
adb shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

The shell accepts a package as well as a full component. If discovery showed a
different stock launcher, use the exact saved package/component instead. Do not
guess names. Verify the physical Home button opens Google TV; reboot if necessary.
After the original HOME works, uninstall Min TV via Settings or optionally:

```bash
adb uninstall se.jonasschroder.mintv
```

Uninstalling deletes **Min TV's** profiles and credentials; export a backup first
if you want them. Original OwnTV's storage is separate. Restore Google TV before
uninstalling a selected HOME app. Turn debugging off and revoke the computer's
authorization after testing. If ADB access is lost, try physical remote Settings/
the Min TV Settings button to recover; a factory reset is the last resort and
erases device data, so do not begin disabling experiments without recovery access.

## Build, checks and signing

```bash
bash tools/prepare-core.sh
./gradlew :app:assembleStandardDebug :app:testStandardDebugUnitTest :app:lintStandardDebug :OwnTV_Core:core:testDebugUnitTest :OwnTV_Core:player-core:testDebugUnitTest --max-workers=4
./gradlew :app:assembleStandardDebugAndroidTest --max-workers=4
python3 tools/verify-mintv-apk.py --apk app/build/outputs/apk/standard/debug/app-standard-debug.apk --aapt2 "$ANDROID_HOME/build-tools/37.0.0/aapt2"
```

Use JDK 21, SDK 37.0/Build-Tools 37.0.0 and Gradle 9.7.1. Core remains pinned at
`adca2bcd653f19e6e5d659c2722309e6aef4ec15`. The standard APK includes arm64-v8a
and armeabi-v7a, covering Chromecast's ARM runtime. The cloud helper can prepend
`python3 /workspace/cloud-setup/run.py` to Gradle commands; see
[the baseline guide](build-baseline.md). CI publishes only an expiring workflow
artifact plus its SHA-256 and test/lint reports, with read-only repository permissions.
It does not create Releases, merge PRs, upload keys or run ADB system changes.

Debug APKs are signed by a development key and are debuggable. Fresh CI runners
generate different debug keys, so an APK from another run/computer may fail an
in-place update with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. A higher version code
does not fix a certificate mismatch. Export Min TV data before uninstall/reinstall;
if HOME is active, restore Google TV first. CI sets versionCode from its run number;
local builds default to 1. Debug builds are for trusted testing, not production.

For stable updates, create a **unique Min TV signing key** offline, back it up
encrypted, and keep it in a secure signing service/vault. Use the same certificate
and increasing version codes for every update. A future trusted, manually approved
signing workflow should inject it only into temporary runner files via protected
environment secrets, keep passwords masked, verify the APK's certificate, then
delete those files. Never expose signing credentials to fork PR jobs, place keys
in Git/caches/artifacts, or reuse OwnTV's signing identity. Moving from these
ephemeral debug keys to that certificate usually needs one clean reinstall.

## Recorded cloud validation

On 9 October 2026, the standard ARM debug APK and its instrumentation test APK
built successfully with the pinned Core revision. APK signature verification and
`verify-mintv-apk.py` passed: package, Min TV label, ARM libraries, isolated provider
authorities/permissions, stable HOME activity and nine IPTV icon entries were checked.
All **1,387 JVM tests passed** (177 app, 939 Core, 271 player-core), with no failures,
errors or skips. The final local lint run passed with **94 warnings, 21 hints and
zero errors**. The i18n merge-base ratchet, number-locale and text-overflow checks
passed; the debug APK's pseudolocales were verified. CI performs the additional
existing debug/release pseudolocale check; its live result belongs in the PR checks.

Instrumentation tests were **compiled, not executed**. No Chromecast was attached,
no default HOME change was made, and no system package was disabled. Installation,
real remote navigation, process recovery, source import/playback, casting and
Google TV integration remain physical-device tests, not cloud-verified behavior.

## Device-test checklist (not executed in the cloud)

| Check | Required observation on Chromecast 4K |
|---|---|
| Coexistence | OwnTV and Min TV both install/open; data and favorites stay separate |
| HOME selection | Chooser/ADB result, resolved component, physical Home, persistence after reboot |
| Navigation | D-pad/OK, focused button contrast, Back at HOME, Settings return |
| Live entry | Fresh setup, Xtream/M3U/Stalker import, profiles/PIN, Live section opens |
| IPTV | EPG, favorites, tune/zap, mpv and ExoPlayer, audio/subtitles, HDR where supported |
| Lifecycle | Home during playback stops background audio; return, process death/recreation, icon switching |
| System | Casting, Google Home controls, voice/YouTube/Netflix keys, standby/screensaver |
| Recovery | Restore launcher with ADB; physical Home and reboot work before uninstall |
| Updates | Same-certificate update retains data; certificate mismatch recovery is understood |

`MinTvRoutingTest` provides device-side manifest/intent checks; it compiles in the
instrumentation APK but requires Android to execute. JVM tests and packaged
manifest checks cannot verify firmware policy, remote routing or real playback.

Sources checked during development: [AOSP Intent categories](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/content/Intent.java),
[Android 12 HOME shell implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-12.0.0_r1/services/core/java/com/android/server/pm/PackageManagerShellCommand.java),
[current HOME shell implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/services/core/java/com/android/server/pm/PackageManagerShellCommand.java),
and the FLauncher Chromecast report linked above. These establish Android's
contracts and historical device reports, not verification on your Chromecast.

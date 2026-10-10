# Min TV Test — side-by-side Chromecast QA

For the latest full-screen TV redesign, broadcast-source feasibility, Twitch build
configuration and device checklist, see [the redesign guide](min-tv-redesign.md).

Use this build for isolated physical Chromecast testing. It is a **separate app**,
not an update or migration of the working Min TV v0.1 installation.

| | Working/regular Min TV | QA prototype |
| --- | --- | --- |
| App name | Min TV | **Min TV Test** |
| Application ID | `se.jonasschroder.mintv` | `se.jonasschroder.mintv.qa` |
| ARM debug task | `:app:assembleStandardDebug` | `:app:assembleQaDebug` |
| External link scheme | `mintv://` | `mintv-qa://` |
| APK in Actions ZIP | `MinTV-v0.2-arm-debug.apk` | **`MinTV-v0.2-QA.apk`** |

Both use the existing v0.2 IPTV Home and player, namespace `tv.own.owntv`, and
Core commit `adca2bcd653f19e6e5d659c2722309e6aef4ec15`. QA adds a yellow **TEST**
badge to the TV banner and displays **Min TV Test** in Home. All nine alternate
icon activities inherit the QA application label/banner. The regular identity,
link scheme, banner, build tasks and signing configuration keep their existing
values. QA is an ARM debug-only flavor; no QA release variant is enabled.

**Leave Min TV v0.1 installed. Do not install the regular v0.2 APK for this test.
Do not clear data, uninstall Min TV, or disable/change Google TV or system apps.**
There is no Android HOME registration, launcher activation or device modification.
The remote's physical Home button still opens Google TV.

## Install from a Mac using Downloader

1. Sign into GitHub on your Mac. Open PR #3 → Checks → **Min TV prototype**, then
   download the artifact named **MinTV-v0.2-QA.apk** from the run's Artifacts area.
   GitHub downloads a ZIP even though the artifact name ends in `.apk`. Artifacts
   expire after 14 days; use the final run linked in the PR.
2. Double-click the ZIP in Finder. Inside are `MinTV-v0.2-QA.apk`, `SHA256SUMS`,
   a public signing certificate and a read-before-install notice. Read the notice.
3. In your Mac's Downloads folder, create an empty folder named **MinTV-QA-share**.
   Copy **only `MinTV-v0.2-QA.apk`** into it. Keep private backups and credentials
   elsewhere. Do not rename a ZIP to `.apk`.
4. Open Terminal (Applications → Utilities). If `python3 --version` does not work,
   install Python 3 from [python.org for macOS](https://www.python.org/downloads/macos/),
   then reopen Terminal. Run:

   ```bash
   python3 -m http.server 8765 --bind 0.0.0.0 --directory "$HOME/Downloads/MinTV-QA-share"
   ```

   Leave Terminal open. If macOS asks, allow Python's incoming connections on your
   trusted home network. Do not disable the firewall or forward a router port.
5. Find the Mac's local IP address under System Settings → Wi-Fi → Details → TCP/IP
   (or the connected Ethernet service). It often looks like `192.168.1.23`.
   Connect the Mac and Chromecast to the same trusted home network. A guest network
   or VPN may prevent local transfer; use the normal home connection.
6. Install **Downloader by AFTVnews** from Google Play on Chromecast. In Downloader,
   enter `http://YOUR_MAC_IP:8765/MinTV-v0.2-QA.apk`, replacing `YOUR_MAC_IP` with the
   address from step 5. Download the APK. If it cannot connect, check the address,
   Wi-Fi and the macOS permission for Python rather than changing Google packages.
7. When Android asks, allow **Downloader** to install unknown apps. The installer
   must say **Min TV Test**. If it says Min TV, cancel: that is the regular artifact.
   Select Install, then Open. This creates a separate QA app and private data.
8. In Terminal, press **Control-C** to stop the file server. You may revoke
   Downloader's install permission afterwards. Google TV should now contain both
   **Min TV** and **Min TV Test**; open the TEST banner for QA.

Optional integrity check on the Mac: open Terminal in the unzipped artifact folder
and run `shasum -a 256 -c SHA256SUMS`. It should report the APK as OK.

The first QA install does **not** need the working Min TV signing key, because the
packages differ. If a **previous QA** build is already installed, Android requires
its original certificate for a QA update; ephemeral CI keys may differ. Stop on a
signature error. It is never a reason to remove regular Min TV. Future QA builds
can reuse a private QA debug key through the existing out-of-repository signing
settings described in [the v0.2 guide](min-tv-v0.2.md#consistent-signing-for-future-versions).

## Configure IPTV or import a copy of a backup

QA has the normal source/profile onboarding and settings. You can enter your own
Xtream, M3U or Stalker configuration directly in **Min TV Test**, refresh the source,
and add real channels to Favorites. No channels, accounts or credentials are bundled.

To copy the working configuration instead:

1. In **Min TV v0.1**, open **More → Backup & Restore** (also in Settings). Choose
   the profiles and sections you want, including Sources, Favorites and Settings.
   Export a backup with a passphrase to preserve secret fields. Without a passphrase,
   source passwords are intentionally omitted. Keep the passphrase privately.
2. Save the encrypted `.own` backup on your Mac. The existing **Remote** backup
   option displays a local address and access code; open that exact address in your
   Mac browser on the same trusted network and download the backup. Verify the file
   exists and keep the original copy. Do not use the public APK-transfer folder.
3. Leave the original app's remote-backup screen with Back to stop its server.
   Stop any original IPTV playback/recording before testing the same provider in QA.
   Only one remote backup server should run at a time: both apps use the same device
   port. Separate apps still share provider account/device connection limits.
4. Open **Min TV Test**. Complete the normal introductory/profile screens; if source
   entry is optional, skip it. Open **More/Settings → Backup & Restore → Restore →
   Remote**. Open the address/code now displayed by **QA** on the Mac and upload a
   **copy** of the encrypted backup. The local file restore option also remains available.
5. Enter the backup passphrase in QA, review the available sections/counts, select
   Sources and Favorites (plus desired settings), and confirm **inside Min TV Test**.
   Profile/PIN and icon settings in the backup may also be restored into QA; they
   cannot switch the original app's launcher component. Use a separate shared folder
   for any new downloads/exports, e.g. `MinTV-Test`; shared external folders are not
   private app sandboxes.
6. Refresh imported sources in QA. Backups contain configuration/favorites, not the
   full channel catalogue; favorites appear once the source's channels are synced.
   Some provider/device-specific credentials or imported hardware preferences may
   need manual adjustment. Confirm the expected profiles and favorite names in QA.
7. Reopen **Min TV v0.1** and confirm its original profiles, sources and favorites are
   still present. Keep the backup on your Mac. Close playback before returning to QA.

The format is Core's existing package-independent `.own` container/legacy JSON,
not an Android whole-app backup tied to a signing key. `BackupViewModel` delegates
to the injecting app's `BackupManager`; Core DI supplies that app's Room DAOs,
DataStore/settings and `filesDir` for restored wallpaper/subtitles/avatars. Import
remaps profile/source IDs against the destination database, never the other app's
database. There is no shared UID, cross-app data access, silent restore or migration.
Read-only inspection/preview and confirmation remain the existing UI flow.

This isolation is verified from source and packaged identities. **An actual v0.1
backup import on the Chromecast has not been executed in the cloud**; step 7 is
part of the required device acceptance check. Device instrumentation checks include
reading a synthetic encrypted `.own` through the real inspection/preview path,
without importing or deleting user data; they are compiled, not hardware-executed.

## Application identity audit

| Component | Isolation/verification |
| --- | --- |
| FileProvider | `${applicationId}.fileprovider`; regular and QA authorities differ |
| AndroidX startup + LiveLogoArtProvider | `${applicationId}.androidx-startup` and `${applicationId}.owntv-live-logo-art`; separate authorities/caches |
| Custom permissions | Merged AndroidX dynamic-receiver permission is application-ID scoped; packaged definitions are checked for collisions |
| Launcher/icon/restart components | Same Kotlin class names, separate `(package, class)` components; AppIconSwitcher uses `context.packageName`; `:restart` process/task affinity is package-relative |
| External deep links | All nine icon activities register only their variant's scheme; QA does not claim `mintv://` or `owntv://`; explicit internal Core links still parse `owntv://` |
| Internal playback/notifications/alarms | Runtime package/component scopes in Core; recording/reminder alarm receivers are not exported; shared action strings do not route a pending intent to the other package |
| External package visibility | SmartTube, SVT Play, Play Store and stream-handler queries retained; S0undTV removed; no new query for regular Min TV or QUERY_ALL_PACKAGES |
| Database/settings/files/jobs | Application Context creates each package's private sandbox; no shared UID, data migration or schema change; WorkManager/notification identities are per package |
| In-app updater | Core `verifyApk()` rejects an archive whose package differs from `context.packageName`; QA cannot install a regular Min TV update through this path |
| TV rows/art | Core uses the owning package for TV rows, explicit launch components and logo-art URIs; existing row ownership is not renamed |
| Shared device resources | Remote companion port, shared external files, provider account limits, storage and decoders remain shared; use backup servers and IPTV playback sequentially |

App source has no hardcoded runtime reference to `se.jonasschroder.mintv` after the
variant change; that string remains the regular Gradle identity and verifier/docs
expectation. Kotlin package/import names are namespaces, not installed app IDs.
No modifications to pinned Core, IPTV/player behavior or Google packages are needed.

## Build and checks

Use [the existing JDK/SDK/Core setup](build-baseline.md):

```bash
./gradlew :app:assembleStandardDebug :app:assembleQaDebug :app:testStandardDebugUnitTest :app:testQaDebugUnitTest :app:lintStandardDebug :app:lintQaDebug :app:assembleStandardDebugAndroidTest :app:assembleQaDebugAndroidTest :OwnTV_Core:core:testDebugUnitTest :OwnTV_Core:player-core:testDebugUnitTest --max-workers=4
python3 tools/verify-mintv-apk.py --apk app/build/outputs/apk/standard/debug/app-standard-debug.apk --aapt2 "$ANDROID_HOME/build-tools/37.0.0/aapt2"
python3 tools/verify-mintv-apk.py --qa --apk app/build/outputs/apk/qa/debug/app-qa-debug.apk --aapt2 "$ANDROID_HOME/build-tools/37.0.0/aapt2" --peer-manifest app/build/intermediates/packaged_manifests/standardDebug/processStandardDebugManifestForPackage/AndroidManifest.xml
```

Actions runs tests/lint for both variants, compiles both instrumentation APKs,
checks ARM ABIs, labels, all icon activities, disjoint providers/permissions/link
schemes and absence of HOME in both manifests. It verifies APK signatures and QA
debug pseudolocale packaging. The regular artifact remains available separately;
the QA artifact is **MinTV-v0.2-QA.apk** and includes that APK, checksum, certificate
and QA installation notice. Neither artifact is a public GitHub Release.
Actual local/CI results for the final revision are recorded in PR #3.

Local cloud verification on 9 October 2026:

| Check | Result |
| --- | --- |
| Regular + QA ARM debug APKs | Both built; arm64-v8a and armeabi-v7a |
| Unit tests | 1,590 passed executions: 190 regular + 190 QA + 939 Core + 271 player-core; zero failures/errors/skips |
| Lint | Both passed: zero errors, 94 warnings and 22 hints per variant |
| Both instrumentation APKs | Compiled; 7 routing/identity/backup tests per variant, not executed on hardware |
| Packaged identity/isolation | Passed: labels in every packaged locale, separate providers/custom permission/components/link schemes; no HOME in either manifest |
| Signing + QA debug pseudolocales | Passed; no key or credential added to the repository |
| Source i18n checks | Passed; no literal-baseline growth |
| Regular local update reference | Same saved-local-v0.1 signer and increasing versionCode; unrelated to first QA install |
| Physical install, backup import and playback | Not executed; required checklist below |

The latest companion changes, test results, APK size comparison and remaining
physical measurements are documented in [the companion guide](min-tv-companions.md).
The earlier 9 October table above records the pre-companion QA baseline. The user
has since installed and exercised that baseline on Chromecast; cloud-only checks
do not extend that device result to the new companion APK.

## Stable signing for regular Min TV

See the [offline key, certificate gate and encrypted migration procedure](min-tv-signing.md).

For future distribution, use a QA debug key distinct from the regular release key.
Current local/CI debug variants may share a debug certificate; separate application
IDs and the absence of a shared UID still provide separate Android sandboxes.
For a stable regular distribution,
generate one dedicated Min TV release key offline, protect it with strong passwords,
and keep an encrypted backup in a secure vault with limited access. Record the public
certificate fingerprint, not the private key. Increase versionCode for every update.
Sign through a protected, manually approved workflow/signing service for trusted
commits; PR jobs must never receive the key or its passwords. Do not commit them or
put them in caches, logs, artifacts or this repository. Existing out-of-repository
release signing inputs can be used by that protected process.

A new certificate cannot transparently update a v0.1 debug installation. First
identify its actual certificate and whether its private key still exists. Reusing
that key is the route to an in-place update; a lost ephemeral CI key cannot be
recovered from an APK/public certificate. Choosing a new stable key may require an
explicitly planned, verified encrypted-backup migration later. **This QA task does
not migrate or remove regular v0.1.** See the regular guide's
[safe update procedure](min-tv-v0.2.md#safe-installationupdate--read-first).

## Physical QA checklist (not executed in this cloud)

1. Both app names are visible; v0.1 data remains present before and after QA install/import.
2. Normal TEST app entry opens custom Home without autoplay; configure/import real favorites.
3. D-pad updates selected EPG; focus settles for 800 ms before actual muted video starts.
4. Rapid navigation leaves one preview; AC3/E-AC3 passthrough stays silent.
5. OK produces full-quality fullscreen playback/audio; verify Exo same-stream promotion with logs/provider counts.
6. Back, background and process recreation leave no orphaned stream; mpv/HLS/TS/Stalker fallback still works.
7. SmartTube and official SVT Play open; optional Twitch status login works without playback.
8. Profile/PIN/icon changes in QA affect only QA. Reopen original and verify its data/icon.
9. Exercise 4K/50–60 fps, prolonged playback and memory pressure; record actual behavior.

Follow [v0.2 USB ADB diagnostics](min-tv-v0.2.md#usb-adb-diagnostics), replacing the
package in package/memory/process commands with **`se.jonasschroder.mintv.qa`**.
No launcher/system commands are needed. Redact URLs/credentials before sharing logs.
Keep GPLv3 notices/source with redistribution: this workflow's exact app commit plus
the pinned Core commit and existing dependency licenses.

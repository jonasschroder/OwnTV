# Safe update of the populated Min TV Test installation

**Current owner decision:** planning one clean adoption of BOTH applications is
accepted once permanent signing and acceptance succeed. The retained shared key
and backups and both protected signing environments are reported configured;
actual owner-key verification is still blocked by the unmerged main workflow.
See [the current setup guide](min-tv-permanent-updates-mac.md). This is not an
instruction to reinstall now. Keep both existing apps/data until execution is
separately approved. No ordinary PR job now uploads an ephemeral installer APK.

The checks below assess a verified compatible update; a new certificate cannot
update an unrelated old signer. Existing physical certificates and real backup
restore have not been verified in the cloud. Future same-key/package updates
must retain private stores. Nothing here installs/uninstalls/clears physical apps
or changes Android settings.

## 1. Make and verify the real IPTV backup first

On Chromecast, open the banner marked **Min TV Test**. In More/Settings → Backup
& Restore, select every needed profile plus Sources, Favorites and Settings.
Export with a strong private passphrase; without it source passwords are omitted.
Keep the passphrase in your password manager.

Use the existing Remote backup screen's exact private LAN address/access code in
your Mac browser, download the encrypted `.own` into a private folder outside
any APK-sharing directory, then leave the remote screen with Back. Only one
backup server should be open across both apps. Keep two private/offline copies.
Never upload the backup, IPTV passwords or passphrase to this PR or an artifact.

Verify the file is nonempty, make a checksum (`shasum -a 256 /PRIVATE/PATH/backup.own`),
and record the expected profile names, source count and favorite names/counts
privately. Use Backup & Restore's inspection/preview on a **copy**, enter the
passphrase and compare the sections/counts. Stop before confirming an import into
the working installation. A strong restore verification also requires importing a
copy into a disposable separate Android TV emulator/test sandbox, refreshing the
sources and checking favorites/playback within the provider's connection limits.
Do not use the populated QA installation as a destructive restore experiment.

The cloud verified Core's encryption/backup tests and compiled its synthetic
inspection test; it cannot access or verify your actual backup/passphrase. The
real backup and restore acceptance checks remain mandatory local steps. The new
My teams preferences, experimental opt-ins, Twitch credentials and short-lived
fixture channel choices are outside the IPTV `.own` backup. Note their choices
privately for a deliberate future migration. In-place same-signer updates retain them.

## 2. Read the installed QA identity, without changing it

Use an already authorized Mac ADB connection and Google's Platform/Build Tools.
If it is unavailable, keep the installation unchanged and postpone the update
check. No Android settings are changed by these commands:

```bash
adb devices -l
adb shell pm path se.jonasschroder.mintv.qa
```

Copy the returned `base.apk` path without `package:`:

```bash
adb pull /data/app/PATH_PRINTED_ABOVE/base.apk MinTV-Test-installed.apk
"$ANDROID_HOME/build-tools/37.0.0/apksigner" verify --print-certs MinTV-Test-installed.apk
"$ANDROID_HOME/build-tools/37.0.0/aapt2" dump badging MinTV-Test-installed.apk
```

This reads the public APK/certificate, not private IPTV data. If you retained the
exact APK you installed, it can be used instead after confirming its identity.
Record the public certificate SHA-256 and versionCode. Do not assume that the
last GitHub run is the build currently installed on the Chromecast.

After actual permanent signing, download the approved QA candidate ZIP on your Mac and extract it. Verify SHA256SUMS.
Run the repository's read-only checker, explicitly selecting QA:

```bash
python3 tools/check-apk-update.py \
  --application-id se.jonasschroder.mintv.qa \
  --previous MinTV-Test-installed.apk \
  --candidate MinTV-v0.2-QA.apk \
  --aapt2 "$ANDROID_HOME/build-tools/37.0.0/aapt2" \
  --apksigner "$ANDROID_HOME/build-tools/37.0.0/apksigner"
```

The checker is in this repository; use its reviewed PR revision on your Mac.
Replace the example candidate path with the exact signed QA APK filename from
the approved artifact. Artifacts contain only explicit public signing outputs.

It must report the same QA package, the same verified signer certificate(s), and
an increasing versionCode. It rejects the regular package, an unrelated signer,
unverified signatures and equal/lower versions. It never installs anything.
A PASS establishes these update prerequisites; it does not replace the real
backup, Android package-manager compatibility checks or device acceptance tests.

## 3. Permanent signing and the current blocker

Keep the working apps installed. Ordinary debug keys may differ from installed
certificates; the read-only checker must pass for any in-place update. An APK's
public certificate cannot recover its private key. Do not bypass
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` by clearing/removing an app.

The owner has retained and encrypted-backed-up one permanent key, explicitly
shared by QA/Stable. Both public pins are committed; package/data/update channels
and protected signing environments remain separate. Sharing the key provides no
cryptographic separation. Do not create another key or repeat completed setup.

The manual main-only signer builds unsigned release APKs WITHOUT secrets and
signs natively in a separate approved environment job, outside Gradle. It checks
actual key/manifest/APK certificate, exact package/code/name, ARM ABIs, v2/v3 and
checksum before uploading only public outputs. The workflow is still only in
PR #3, so actual signing is blocked until future approved main integration.
No permanently signed artifact or Release has been produced.

The planned first clean adoption may lose old local data. Before recommending it,
verify separate real encrypted backups and restoration of a copy, record settings
outside `.own`, pass owner-key acceptance, then obtain physical execution approval.
No automatic migration/uninstall/reset exists. [Current process and limitations](min-tv-cloud-updates.md).

## 4. Manual Mac/Downloader transfer, only after the gate passes

Once the backup is verified and the checker passes, follow the existing
[Mac/Downloader instructions](min-tv-qa.md#install-from-a-mac-using-downloader).
Share only the APK from a separate empty folder on the trusted LAN, confirm the
installer says **Min TV Test**, and stop the server with Control-C afterwards.
Installing a compatible update keeps the existing QA sandbox; do not open a fresh
install/recovery procedure or restore over the working database as a routine update.

Afterward verify the same QA profiles, IPTV sources and favorites, then the new
team choices, EPG, muted preview and fullscreen playback. Reopen regular Min TV
v0.1 and verify its original data too. If the gate cannot pass, keep both currently
installed apps as they are; this build can be tested in a disposable emulator instead.

# Safe update of the populated Min TV Test installation

**Keep both existing apps and their data. Do not reinstall yet.** The final PR #3
artifact is a validation build until its certificate and versionCode match your
installed QA app. A new persistent key cannot update an unrelated old certificate.
Nothing in this PR installs/uninstalls apps, clears data or changes system settings.

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

Download the final PR artifact ZIP on your Mac and extract it. Verify SHA256SUMS.
Run the repository's read-only checker, explicitly selecting QA:

```bash
python3 tools/check-apk-update.py \
  --application-id se.jonasschroder.mintv.qa \
  --previous MinTV-Test-installed.apk \
  --candidate MinTV-v0.2-QA.apk \
  --aapt2 "$ANDROID_HOME/build-tools/37.0.0/aapt2" \
  --apksigner "$ANDROID_HOME/build-tools/37.0.0/apksigner"
```

The artifact also includes `check-apk-update.py` directly; from the extracted
ZIP folder use `python3 check-apk-update.py` with the same arguments if you have
not cloned the repository.

It must report the same QA package, the same verified signer certificate(s), and
an increasing versionCode. It rejects the regular package, an unrelated signer,
unverified signatures and equal/lower versions. It never installs anything.
A PASS establishes these update prerequisites; it does not replace the real
backup, Android package-manager compatibility checks or device acceptance tests.

## 3. Persistent QA signing and the current blocker

Each ordinary PR Actions runner generates an ephemeral debug key. This artifact
may therefore **not** update your current Test installation. Never work around
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` by removing or clearing either app. An APK's
public certificate cannot recover its private key.

First locate the original QA private keystore on the trusted original build
machine or in its private backups. If its certificate matches the installed APK,
retain it and use the existing `MINTV_DEBUG_KEYSTORE_FILE`,
`MINTV_DEBUG_KEYSTORE_PASSWORD`, `MINTV_DEBUG_KEY_ALIAS`, `MINTV_DEBUG_KEY_PASSWORD`
inputs to build **only** `:app:assembleQaDebug`, with `VERSION_CODE` higher than
the installed version. Set credentials using a private protected configuration,
not password command-line flags/history or repository files. Repeat the update
checker against the signed result. No private key is available in this workspace.

If that original ephemeral key is lost, there is no ordinary in-place update
with a new key. Keep the working app installed. Do not begin a backup/reinstall/
restore migration without a separately authorized decision and a verified real
backup/restore path. This PR does not perform or recommend that migration now.

For future QA continuity, retain a dedicated QA key, separate from production.
[The signing plan](min-tv-signing.md) explains offline creation, public fingerprint
checks and two encrypted offline backups with a tested decrypt/certificate check.
A practical future protected CI setup is:

1. Create or recover that dedicated key on a trusted offline Mac; record its public
   fingerprint, alias and certificate expiry. Test both encrypted offline backups.
2. Create a GitHub **QA signing** environment with required reviewer approval and
   deployment restrictions to a trusted signing branch. Store the keystore as an
   encrypted environment secret and its passwords/alias as separate secrets.
3. Use a separate **manually dispatched** workflow that requires an exact immutable
   reviewed/tested app commit, checks out that commit and the pinned Core source,
   and receives secrets only inside the protected environment job. Never use
   `pull_request_target` or supply keys to PR/fork builds.
4. Decode the key to a runner temporary private file (permissions 600), set the
   existing signing inputs and an explicitly monotonic versionCode, build QA and
   verify package/signature/certificate against the pinned public fingerprint.
   Delete the key in an always-run cleanup. Do not cache/archive it or echo secrets.
5. Upload only the APK, checksum, public certificate and matching-source SHA; retain
   the offline backups independently of GitHub. Always compare with the installed
   APK before a manual device update. Production uses its own separately protected
   key/environment and retains `se.jonasschroder.mintv` unchanged.

This is a documented preparation plan; no signing secrets, protected environment
or signing workflow has been provisioned automatically. Ordinary PR checks remain
secret-free. Stable signing must be established before future distribution is
advertised as an update for a populated QA installation.

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

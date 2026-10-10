# Permanent Min TV signing — manual preparation only

**Current Phase A instructions:** use [the beginner Mac guide](min-tv-permanent-updates-mac.md)
and [update audit](min-tv-update-audit.md). The manual protected signing workflow
is now prepared, but public pins/secrets are not configured and no permanently
signed build or Release has been issued. Older preparation guidance below is
historical; use the new guide for environment names and the unsigned-build/native-signer separation.

Regular application ID remains `se.jonasschroder.mintv`; QA remains
`se.jonasschroder.mintv.qa`. This PR does not replace the installed regular v0.1.
The physical installed certificate and original private-key availability have
not been established in the cloud. A public certificate/APK cannot recover a key.

## Verify before choosing a key

On the Mac, install Google's SDK Platform Tools and Build Tools, authorize the
Chromecast's USB ADB connection, then read its installed package path:

```bash
adb devices -l
adb shell pm path se.jonasschroder.mintv
```

Copy the returned `base.apk` path without `package:` and download it read-only:

```bash
adb pull /data/app/PATH_PRINTED_ABOVE/base.apk MinTV-installed-v0.1.apk
"$ANDROID_HOME/build-tools/37.0.0/apksigner" verify --print-certs MinTV-installed-v0.1.apk
```

Keep the public SHA-256 certificate fingerprint. Search private offline key
backups and the original trusted build machine; do not upload their keys to this
PR. If a candidate key exists, use interactive `keytool -list -v -keystore
/PRIVATE/PATH/keystore` to compare its certificate. Matching certificates and an
increasing versionCode are required for an ordinary in-place update. Never use
uninstall/clear-data/downgrade to bypass the check. The repository's
`tools/check-apk-update.py` verifies package, signer and versionCode without installing.

## Create a future key offline, if needed

A new key **will not** automatically update an unrelated v0.1 certificate.
Prefer retaining a usable existing key for continuity. If deliberately starting
a new signing identity, disconnect a trusted computer from networks, use JDK 21
and create a private directory outside every repository/cloud-sync share:

```bash
mkdir -p "$HOME/MinTV-private-signing"
chmod 700 "$HOME/MinTV-private-signing"
keytool -genkeypair -keystore "$HOME/MinTV-private-signing/mintv-release.p12" -storetype PKCS12 -alias mintv-release -keyalg RSA -keysize 3072 -validity 10000
chmod 600 "$HOME/MinTV-private-signing/mintv-release.p12"
keytool -list -v -keystore "$HOME/MinTV-private-signing/mintv-release.p12" -alias mintv-release
```

Enter a strong unique password interactively; store it in a password manager.
Do not pass passwords in command-line flags/history or screenshots. Record the
public certificate fingerprint, alias and creation date. Make two encrypted
offline backups on separate media and keep one elsewhere. For example, if GnuPG
is already installed on that offline computer:

```bash
gpg --symmetric --cipher-algo AES256 --output /OFFLINE_MEDIA/mintv-release.p12.gpg "$HOME/MinTV-private-signing/mintv-release.p12"
```

Use a separate strong backup passphrase. Test decrypting a copy in a private
temporary folder and listing its certificate; verify the fingerprint matches,
then securely retire the temporary copy according to your storage policy.
Keep the encrypted backup/passphrases accessible to you years later. Do not
commit/upload the original key, backup or passwords to GitHub artifacts/caches.

Local release signing already accepts `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`, or the private user-wide Gradle properties
`owntv.keystoreFile`, `owntv.keystorePassword`, `owntv.keyAlias`,
`owntv.keyPassword`. Set those through a private protected configuration/secret
manager, not command-line password flags, and build `:app:assembleStandardRelease`.
Restrict the properties file to your account. Increase VERSION_CODE for every
distribution. Verify the APK certificate and run the update checker before any
manual install. Use a separate retained QA key via the existing MINTV_DEBUG_*
inputs; do not share a production key with debug testing.

A future signing workflow must require protected-environment manual approval
and trusted commits. PR/fork jobs must never receive signing keys or credentials.
Keep private keys away from build caches/logs; upload only APKs, checksums and
public certificates. This PR intentionally leaves Actions as unsigned-release /
ephemeral-debug validation, with no signing secrets or release publication.

## If the installed v0.1 key is lost

Keep v0.1 installed and working. Export the desired profiles, Sources, Favorites
and Settings through its existing **More/Settings → Backup & Restore**, using a
passphrase so credentials are preserved. Save the encrypted `.own` on the Mac,
outside the APK file-server folder. Verify backup inspection/preview and counts,
and test restoring a **copy** into the separate QA sandbox. Refresh imported
sources and confirm favorites and playback before considering a regular migration.
The [QA guide](min-tv-qa.md#configure-iptv-or-import-a-copy-of-a-backup) explains the
manual remote/local transfer and the shared backup-port/provider limits.

A different certificate requires a separately authorized, deliberate backup /
reinstall / restore migration with local-data loss understood. That decision is
outside this PR. Neither app is removed or cleared automatically; no installer
or workflow here performs migration. Twitch credentials and the experimental
SHL setting are intentionally outside the IPTV backup and require new setup.

For the already populated **Min TV Test** installation, use [the safe QA update
gate](min-tv-safe-qa-update.md), including the explicitly selected QA checker.
New sports preferences are local settings outside the IPTV backup and must be
reselected after an authorized restore.

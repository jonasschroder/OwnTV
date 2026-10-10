# Permanent Min TV signing

Current instructions: [Mac and TV guide](min-tv-permanent-updates-mac.md) and
[implementation/acceptance report](min-tv-cloud-updates.md).
The owner reports both protected signing environments configured and the retained
key/backups complete. Both channels pin the same owner-selected public certificate:

```text
D2:0B:2F:58:F4:E1:2B:95:6E:1E:1D:C4:0E:47:87:3D:9B:9E:E6:A6:66:A2:DE:1A:F2:A6:B8:AC:3D:40:54:7C
```

QA `se.jonasschroder.mintv.qa` and Stable `se.jonasschroder.mintv` retain distinct
sandboxes and authenticated update channels. Sharing the key does not provide
cryptographic separation. No shared UID/private-data permission is introduced.
The explicit owner choice supersedes the earlier separate-key preparation advice.

The protected manual workflow builds unsigned release APKs without secrets and
signs outside Gradle in a separate approved job. Actual owner-key verification
is still blocked: the workflow is not yet on main and PR #3 remains unmerged.
No Release or owner-signed artifact has been produced. Never replace this with
an ordinary ephemeral debug artifact or weaken protected-branch rules.

Both apps may need one clean adoption because their installed certificates are
not verified to match this key. The owner accepts that plan; physical execution
still requires approval. Export and verify encrypted backups first. No automatic
uninstall, clear-data, signing mismatch bypass or Android setting change exists.
Subsequent compatible same-key/package updates must preserve private data.
Do not create a new replacement key; retain the existing key and encrypted backups.

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

## Before an eventual clean adoption

Leave both installed apps working until actual permanent-key signing and upgrade
acceptance succeed. Export separate encrypted `.own` backups via each app's
Backup & Restore, inspect their contents/counts and verify restoring a copy into
a suitable separate test sandbox before recommending a reinstall. Follow the
[backup guide](min-tv-safe-qa-update.md). Cloud tests use synthetic data and do not
verify the owner's physical backups. Sports preferences, Twitch authentication
and some local settings are outside the IPTV backup; record/reconfigure those
separately. Neither app is removed or cleared by the workflows or updater.

A public APK/certificate cannot recover a private key. If the legacy key is
available, the read-only checker above can assess in-place compatibility instead.
An unrelated certificate cannot perform an ordinary Android in-place update.

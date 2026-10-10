# Min TV cloud updates: implementation and acceptance

PR #3 remains unmerged. No Release has been published, no owner-key APK has
been produced, and no physical app/system setting has been changed.

## Owner setup and the remaining signing blocker

The owner reports the permanent key and encrypted backups complete and all four
secrets configured in BOTH `mintv-qa-signing` and `mintv-production-signing`. Read-only API verification
of both environments confirms required reviewer `jonasschroder`, exactly `main` as a branch (no tags),
and `can_admins_bypass=false`. The integration returns HTTP 403 for secret-name
listing; their contents are neither accessible nor verified by Codex.
The owner explicitly chose the SAME permanent certificate for QA and Stable.
This supersedes the earlier separate-key plan. Both committed public pins are:

```text
D2:0B:2F:58:F4:E1:2B:95:6E:1E:1D:C4:0E:47:87:3D:9B:9E:E6:A6:66:A2:DE:1A:F2:A6:B8:AC:3D:40:54:7C
```

GitHub returns **404** for `mintv-sign.yml`: the workflow exists only in this
unmerged PR. The environment and workflow deliberately permit only reviewed
main commits. There is no signing run to approve yet. Do not weaken the branch
policy or push PR code to main as a workaround. A future owner-approved review
and integration is required before actual permanent-key verification can run.
Both channel policies now permit signing with that shared public pin, with
explicit reviewed `allow_shared_qa_production_certificate=true` and Stable
`initial_adoption=owner-approved-clean-install`. The owner accepts planning a
one-time clean installation of BOTH apps after acceptance, not its execution now.
Installed legacy certificates remain unknown: no in-place v0.1 compatibility is
claimed. Subsequent updates require the permanent certificate and a higher code.

Package IDs (`se.jonasschroder.mintv.qa` / `se.jonasschroder.mintv`), private
stores, metadata/channel checks, tags and environment approvals remain distinct.
A shared private key does NOT provide cryptographic separation; compromise of
either signing environment can affect both signing identities. The apps request
no shared UID or signature-based permission that grants each other private data.

## Authenticated distribution

The protected signer now also creates `MinTV-qa-update.json` or
`MinTV-stable-update.json`. Its envelope contains the public X.509 certificate,
Base64 exact UTF-8 payload bytes and a detached **SHA256withRSA** signature.
The app hashes the certificate against its compiled channel pin before
verifying that signature. It never trusts a certificate chosen by the server.
No private-key export or new crypto dependency is used: signing uses JDK JCA
inside the same private temporary directory as APK signing.

The signed payload binds channel, exact package, versionCode/name, reviewed
source SHA, APK name/SHA256/size, minSdk, ARM ABIs, release tag and bounded notes.
`release-candidate.json` remains build evidence, not an authenticated manifest.
Native apksigner verifies v2 AND v3 before the manifest is signed. The client
checks authenticated exact-byte SHA256 plus Android archive signing information;
Android verifies the APK again at normal installation. A public checksum alone
does not establish trust.

`mintv-distribute.yml` is a **separate manual-only** workflow. It requires a
successful approved main signing run, the matching channel/code artifact,
main ancestry, the committed certificate, authenticated manifest and actual
APK package/version/ABI/signatures/hash. It has no signing secrets. Only its
protected publication job has `contents: write`. It refuses existing tags or
same/newer channel versions; no clobber/overwrite operation exists. Publication
checks up to three pages of100 releases, so a busy QA channel cannot hide a newer
Stable behind the first page. An unexhausted300-release window blocks publication
rather than assuming no newer version exists. A separate regression test covers
that cross-channel history case (CLI suite now30; total unit/CLI1,858).

Before using it later, create `mintv-qa-distribution` with the same reviewer,
main-only branch and administrator-bypass protections as the signing environment.
No signing secrets belong there. Production uses `mintv-production-distribution`.
Starting a manual run requires typing `PUBLISH-QA` or `PUBLISH-STABLE`, followed
by environment approval. **This makes the APK publicly downloadable.** Merely
pushing/merging this workflow does not publish anything.
Tags are `qa-<versionCode>` (prerelease) or `stable-<versionCode>`; exact asset
names come from the authenticated manifest. Both use the signing workflow's
existing monotonically increasing code counter. Artifacts expire; publish an
approved candidate before its artifact expires, or sign a new higher-code build.

## Native TV behavior

Inställningar → **Om Min TV** shows version, build, fixed Test/Stable channel,
last successful check, automatic on/off and a manual check. Existing settings
storage/system-language behavior is reused. The More/About check opens the same
native dialog. The unsafe pinned Core selector stays disabled and untouched.

Automatic checks start five seconds after Home is usable and repeat at most
every six hours while Home is visible and the app is resumed. They do not run
in the background/fullscreen player. Manual checks have a 60-second minimum;
403/429 respect bounded Retry-After/reset backoff and are never bypassed.
Temporarily signed/debug builds, missing pins, mismatched installed certificates
and Android below API 28 perform **zero update requests**. Chromecast Android 14
is supported by source checks; device acceptance is still pending.

Discovery reads at most three pages of 20 GitHub releases, bounded to 1 MB each,
and fails rather than claiming no update when that window is exhausted without
finding the channel. Metadata is limited to 40 KB, notes to 16 KB and APKs to
200 MB. URLs are constructed from authenticated fields under the fixed repository;
remote arbitrary APK URLs are never used. Redirects are manual, HTTPS-only,
at most three hops, limited to GitHub's two named release CDN hosts. No token
is embedded. There is no new player, decoder, WebView or background service.

An available version gets a compact Home card with Uppdatera/Senare; it does
not take focus automatically. Fullscreen playback suppresses the card/dialog.
The dialog shows authenticated notes and download progress. APKs stream through
a 64 KB buffer to private cache; size, hash, exact package/version/certificate,
non-debuggable flag, minSdk, actual packaged ARM libraries, higher installed
code and persisted highest-seen code are checked before session creation.
Space must cover two APK copies plus 32 MB. Failed partial files are removed.
Foreground loss cancels the HTTP call and active foreground coroutine; no
background polling/restart service exists.

The app uses ordinary PackageInstaller MODE_FULL_INSTALL with user action
required on Android 12+. If needed, a button explains and opens this app's
unknown-source settings; the user must enable permission and return. The APK
is verified again before continuing. Nothing changes settings automatically.
A private explicit PendingIntent receiver saves session/approval/result state
without launching background UI. A live explicit update action hands off to
Android confirmation; after process recreation a Continue button resumes it.
Declining is shown as cancellation. Installer failures are reported without
resetting any IPTV/database/preferences. Uncommitted failed sessions are abandoned.

## Verified build/test snapshot

Signing/updater implementation `5a8ccf1831f1ac6e092af1d107da2bf43edb21a8`:

| Check | Actual result |
|---|---|
| App unit tests | 309 QA + 309 regular, no failure/error/skip |
| Pinned Core/player unit tests | 939 + 271, no failure/error/skip |
| Python policy/update tests | 29 passed; total unit/CLI executions 1,857 |
| ARM debug + instrumentation compile/package | Both identities passed |
| Unsigned optimized ARM release builds | QA and regular passed; minSdk26, ARM64 + ARMv7, not debuggable |
| Debug/release lint | Zero errors; 130 debug / 127 release warnings per identity |
| Actual native signing/metadata fixtures | All28 passed with one shared disposable certificate; wrong key/package/channel/corrupt/signed input rejected |
| Packaging/i18n/actionlint | Provider/permission/link/package isolation, no HOME, correct pseudolocale packaging and workflow syntax passed |

The native fixtures invoke signing independently twice per package and validate
actual v2/v3 signatures, manifests, hashes and the public-output whitelist. Their
private files/APKs are deleted; these are not owner-key acceptance artifacts.
Lint warnings include intentional synchronous installer-state persistence and
existing style warnings; no blanket suppression/baseline hides an error.
The current dialog focus follow-up must also pass the strict Android tests in
[PR #3's current Checks](https://github.com/jonasschroder/OwnTV/pull/3/checks).
The remote test explicitly establishes keyboard input mode without requesting
node focus; the app must select Close and handle actual D-pad/OK/Back events.

Reproduce unit/CLI/debug validation with the commands in `android.yml`. Build
unsigned releases with `VERSION_CODE=1000001`, `MINTV_VERSION_NAME=0.2.0` and
`:app:assembleStandardRelease :app:lintStandardRelease`, then
`MINTV_VERSION_NAME=0.2.0-beta.1` with the corresponding Qa tasks. These are TEST
version inputs, not the permanent production counter. `i18n.yml` runs
`tools/signing/test_signer.py` against both real unsigned releases. Ordinary
Gradle builds require JDK21, SDK/BuildTools37 and `bash tools/prepare-core.sh`.
No private signing secret is needed for these checks.

## Acceptance that remains mandatory

Local/CI fixture tests do not prove possession of the permanent owner key.
After future approved main integration, run signing twice with new dispatches
and verify the public pin, increasing codes, authentic manifest and APK integrity.
Then perform A→B on a fresh disposable emulator using those owner-signed builds
and check sample persistent data. API34 fixture upgrade tests separately verify
two sandbox identities with the owner-selected shared fixture certificate,
wrong-key/downgrade rejection and real persistent stores.

Physical Chromecast checks: real remote focus/Back, installer permission screen,
confirmation/decline/return, process death during confirmation, insufficient
storage/network interruption, actual app-signer/archive behavior, no fullscreen
interruption and real IPTV/profile/favorites/EPG/sports/Twitch preservation.
The current scripts do not claim to test live Twitch authentication data.
**Do not reinstall now.** The owner accepts planning one clean adoption of BOTH
apps after permanent signing verification; execution still needs explicit approval.
Keep both installed apps untouched until then. The built-in .own backup covers its supported IPTV
sections; it is not proof that sports/Twitch/local preferences are included.

## Simple future usage on Mac and TV

1. Keep your existing key/backups. Follow the [Mac guide](min-tv-permanent-updates-mac.md)
   for secure recovery; never regenerate a replacement key for normal updates.
2. After approved main integration and green exact-commit checks, open Actions →
   **Min TV approved signing**, choose `qa`, the reviewed 40-character commit and
   `0.2.0-beta.N` (or `stable` with `0.2.0` in a separate run); approve the signing environment when GitHub asks.
3. Inspect the successful artifact's certificate/checksum/manifest evidence.
   A failed signing run is retried with a **new dispatch**, never rerun.
4. After signing acceptance, approve distribution separately via **Min TV approved
   distribution**, supplying that signing run ID, its matching channel and `PUBLISH-QA` or `PUBLISH-STABLE`.
   Approve its environment only if public distribution is intended.
5. The first permanent adoption of each app is a separately approved manual migration.
   There is no ready APK or reinstall recommendation in this PR yet.
6. For subsequent compatible updates, open Min TV Test on the TV. Use the small
   Uppdatera prompt or Inställningar → Om Min TV → Sök efter uppdateringar.
   Read notes, choose Uppdatera, and approve Android's installation prompt.
   Senare/Back/decline leave the installed app's data intact.
7. Future updates reuse the same key and package, with a higher code. No manual
   transfer is needed after the first verified permanent adoption of each app. QA and Stable
   follow separate update channels and never install each other's APK.

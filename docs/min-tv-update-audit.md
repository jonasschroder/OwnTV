# Permanent updates: audit and phase boundary

Source baseline: PR #3, `da65a3bbb246e8c5107c944c1c6606b1525a7662`.
Pinned Core is unchanged at `adca2bcd653f19e6e5d659c2722309e6aef4ec15`.

## Existing implementation

Core `core/src/main/java/tv/own/owntv/core/update/UpdateManager.kt` already has
an OkHttp disk-streamed download (64 KB buffer), size/progress handling,
an approximate two-copy space check, own-package archive check, PackageInstaller
session write/fsync/commit, pending-user-action handoff, and cancellation/error
status mapping. Host `UpdateDialog.kt` and `UpdateStatusToast.kt` have native TV
controls, notes, D-pad focus, and settings/startup paths. These are useful patterns
to reuse; a second player, service, WebView or new dependency is unnecessary.

It is **not safe to expose Min TV's two future release channels as-is**:

| Current behavior | Required host adaptation in Phase C |
|---|---|
| `releases/latest`; first APK matching CPU | Exact channel/package and versionCode, QA prereleases separated |
| Numeric version-name comparison | Android versionCode is authoritative |
| Remote asset URL trusted; client redirects | Approved endpoints and narrowly validated GitHub CDN redirects |
| Archive parse + package name only | Verified APK signatures/cert pin/current installed signer, authenticated SHA256, ABI/minSdk |
| Unbounded metadata and download size | Bounded JSON/notes/APK size, streamed disk download and deadlines |
| App-lifetime IO scope; cancellation swallowed by runCatching | Lifecycle cancellation, cleanup, conservative caching/rate-limit backoff |
| Dynamic install receiver and fixed Core action | Durable package-specific callback/session state, pending-approval and process recreation |
| No unknown-source permission flow | Supported canRequestPackageInstalls/settings flow with explicit user choice |

We keep the pinned Core source untouched. The host's Phase A safety gate stops
the legacy startup check and manual updater before any call. Settings gives an
honest localized preparation message and Close/Back; no placeholder update is
offered. Re-enable only after the verified Min TV host adapter replaces the
incompatible selector. Other IPTV/player functionality is unchanged.

## Phase A preparation

`mintv-sign.yml` is manual-only and main-only. It checks a lowercase exact source
SHA on main, exact-commit successful main CI and all required jobs, required
environment reviewers/main-only branch policy, committed channel-specific public
certificates, and production v0.1 identity/version evidence. Source builds happen
in a separate job with no signing environment/secrets. The signing jobs check out
trusted workflow tooling, receive only this run's unsigned APK, and never run
Gradle. Separate QA/production environments inject secrets only into the native
signer step. Actions used here are pinned to public commit SHAs. No cache action
or broad upload is used in signing jobs. Keys are written with private permissions
under a TemporaryDirectory in RUNNER_TEMP and cleaned on both success and errors;
runner teardown is the fallback for abrupt VM termination. No private value is
logged. Explicit upload paths contain verified APK, checksum, public certificate,
notes and source/CI evidence only. No release/write permission/publication code.

The unsigned candidate must be a non-debuggable ARM release of the chosen exact
package/version. The signer validates the actual keystore cert BEFORE signing,
then the actual signed APK, exact one signer, v2 AND v3 verified schemes, manifest
identity, ABIs, minSdk and lack of keystore entries; a failure removes output.
Production is deliberately blocked without matching installed-v0.1 evidence.
Null anchors are intentional fail-closed configuration, never manufactured keys.

One signing workflow generates `1_000_000 + github.run_number` (both channels
have increasing per-package subsequences). Every rerun is rejected to prevent
reissuing different bytes with the same code. A new workflow run is required
after failure. The file/workflow identity and counter must be retained.

## External prerequisites — NOT completed by code

Read-only repository API returned zero protected environments during this audit.
Listing repository secret names returned GitHub HTTP 403 "Resource not accessible
by integration"; existing repository secrets are unknown, not presumed absent.
No relevant signing keystore inputs are bound locally. No key/secret/environment
was provisioned. The owner must follow [the Mac guide](min-tv-permanent-updates-mac.md),
verify offline backups, configure `mintv-qa-signing` and submit ONLY its public
certificate fingerprint for a reviewed pin. Production needs its own original
key/installed identity investigation. Workflow is not registered on main until
a user-reviewed merge; no merge is performed here.

The Android 14 disposable emulator test can prove the system's same-signer/data
semantics with test fixtures. It cannot claim owner's permanent keys are configured
or that the physical apps/backups are verified. Those acceptance checks remain
blocked. Ordinary PR APK uploads cease to avoid offering another ephemeral-signed
APK as the permanent-signing solution. Existing downloaded builds remain unchanged.

## Phase B/C follow-up

No public Release has approval yet. Prefer versioned GitHub Releases with explicit
QA prerelease/stable channel and exact asset names, a separately approved publication
workflow and immutable release metadata cryptographically authenticated under
each channel's pinned signing trust anchor. A checksum file next to an arbitrary
APK is not authentication; candidate JSON here is not an update manifest.
No GitHub token belongs in the app. Public repo Releases make APKs public, while
Actions artifacts expire and typically require login. Never distribute production
to QA, or QA to production. Define redirect/metadata validation before enabling
the host updater. Phase B distribution and Phase C full Settings/notification/
download/validation/installer flow remain pending the secure signing setup.

The owner has authorized PLANNING a one-time clean QA installation after a
verified retained QA key is ready, accepting QA configuration loss. This is not
authorization to execute uninstall or bypass signing mismatch. No app is
installed/uninstalled outside the disposable CI emulator. Production v0.1 is
untouched. Normal same-signer updates must preserve all app-private stores and
must never reset data; real key/installer/device acceptance remains required.

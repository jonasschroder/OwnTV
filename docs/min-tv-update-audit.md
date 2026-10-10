# Permanent updates: audit and phase boundary

Source baseline: PR #3, `da65a3bbb246e8c5107c944c1c6606b1525a7662`.
Pinned Core is unchanged at `adca2bcd653f19e6e5d659c2722309e6aef4ec15`.

Current continuation: [cloud-update implementation and acceptance](min-tv-cloud-updates.md).
The owner now reports all QA environment secrets configured, and the required
reviewer/main-only/no-admin-bypass protections were verified via read-only API.
Historical Phase A audit below is retained; Phase B/C source implementation now
exists in this PR, with actual permanent signing/publication still blocked.

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

We keep the pinned Core source untouched. The host no longer calls the legacy startup/manual selector. The MinTvUpdater
adapter uses authenticated channel metadata and exact-package validation instead.
Builds without a matching permanent installed identity fail closed before any
network call and explain that state in Settings. No placeholder update is offered. Other IPTV/player functionality is unchanged.

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
Missing production anchors are intentional fail-closed configuration. The QA
anchor is the public certificate fingerprint supplied by the owner; no private
key is manufactured or obtained by Codex.

One signing workflow generates `1_000_000 + github.run_number` (both channels
have increasing per-package subsequences). Every rerun is rejected to prevent
reissuing different bytes with the same code. A new workflow run is required
after failure. The file/workflow identity and counter must be retained.
An older queued dispatch also fails if a newer candidate for that channel was
already signed; GitHub does not guarantee FIFO concurrency ordering.

## External prerequisites — NOT completed by code

At the initial audit, no protected environments were present. The owner has since
reported the QA key and encrypted backups complete and all four environment
secrets configured. API verification confirms reviewer jonasschroder, main-only
branch and disabled administrator bypass. Secret-name listing returns HTTP403;
Codex cannot independently confirm their contents. The supplied public QA pin is
committed in config/mintv-signing.json. Private keys/backups have not been received.
GitHub returns404 for the signing workflow because it only exists in this PR.
Main-only signing therefore remains blocked until future user-approved reviewed
integration; no merge or branch-policy bypass is performed. Production remains
blocked pending its original key/installed identity investigation.

The Android 14 disposable emulator test can prove the system's same-signer/data
semantics with test fixtures. It cannot claim owner's permanent keys are configured
or that the physical apps/backups are verified. Those acceptance checks remain
blocked. Ordinary PR APK uploads cease to avoid offering another ephemeral-signed
APK as the permanent-signing solution. Existing downloaded builds remain unchanged.

## Phase B/C implementation

The reviewed-source implementation now includes a separately approved publication
workflow, exact QA/stable tags, a JCA-signed exact-byte update manifest, a bounded
foreground-only native host updater and durable normal PackageInstaller handoff.
See [the implementation and acceptance report](min-tv-cloud-updates.md) for its
precise limits and remaining tests. Candidate JSON is still build evidence only;
the separate signature envelope authenticates metadata. No GitHub token is in the
app. Public Releases make APKs public; no Release has been published/approved here.

The owner has authorized PLANNING a one-time clean QA installation after a
verified retained QA key is ready, accepting QA configuration loss. This is not
authorization to execute uninstall or bypass signing mismatch. No app is
installed/uninstalled outside the disposable CI emulator. Production v0.1 is
untouched. Normal same-signer updates must preserve all app-private stores and
must never reset data; real key/installer/device acceptance remains required.

# M1 validation ledger

Final CI results belong to the exact head shown on [implementation PR #17](https://github.com/jonasschroder/OwnTV/pull/17).
The PR description records that head's conclusions and native screenshot artifact links. A compiled
Android test APK is never reported as an executed device test. No physical Chromecast acceptance
is claimed.

## Local evidence

- Tooling: 52 Python tests passed.
- Standard and QA debug JVM suites: 460 tests each, zero failures/errors/skips; includes nine native
  route-policy cases covering old owners, hidden media, TV/VOD restoration and finite Back.
- Standard/QA debug APKs and x86_64 Android test APK packaged successfully using a task-owned
  Android user directory. Permanent signing configuration was not used or changed.
- Standard production and x86_64 Android-test Kotlin compiled after the focus/profile refinements.
- Both lint variants passed in the stable-source 3m 7s run and again after the focus/profile changes
  in the 2m 21s run. The final PR checks remain authoritative for its exact head.
- Literal inventory/merge-base ratchet, explicit number locales, bounded-text overflow and
  git diff --check passed. Only technical state/test IDs were added to the safe-literal inventory.

Local packaging initially lacked write access to the normal Android debug-keystore directory;
ANDROID_USER_HOME now points outside the repository into the task workspace. One concurrent lint
run encountered an internal IntelliJ PSI exception; a stable-source rerun passed without disabling
lint checks. No production key, owner installation or owner data was accessed.

## Disposable CI evidence and regressions

Initial head `8676d2c` passed all eight real-application startup executions, including persisted
profiles/sources/favorites/history, recreation, English/Swedish and 1080p/720p. Its native route
screenshots exposed clipped header labels; intrinsic maximum width and an actual TextLayoutResult
regression address this. These initial screenshots are diagnostic evidence, not final visual approval.

The first D-pad suite exposed two cases: an Up redirect cancelled by a subsequent cancellation call,
and one fixture key dispatched before a profile mutation reached Compose. The redirect is corrected;
the fixture awaits idle **before** key delivery. All assertions remain required. The final suite adds
full-label/no-resize coverage and reruns old Home/Guide/VOD/backup/update tests.

Production startup now also exercises:

- All four top destinations and the Profile entry on actual application screen owners.
- Remote Right/OK from the selected TV contextual tab into EPG.
- An explicit existing guide-first preference, retained across Home/TV navigation.
- Every retained release-visible More page (Favorites, History, Backup, Local Sync, errors, About),
  plus Downloads/Recordings and all Settings groups through the original screens.
- Native route screenshots at the existing 1080p and 720p emulator configurations.

`NativeNavigationDeviceTest` covers six cases: OK-only activation of five entries; full labels and
stable geometry; Up/Down and return focus; scrolling/page re-entry; profile isolation and empty
content; local timeline Left/Right and synthetic fullscreen hiding/return. The last case tests
navigation boundaries rather than hardware decoding. Existing CI asserts every required method
actually ran and did not skip.

## Limits and review gates

No local emulator runtime is installed. Emulator execution is in guarded CI runners that refuse
physical devices and existing owner installations. CI's generated screenshots are actual native
application/Compose output with synthetic data; any Android system bars belong to the emulator.

Native screen owners and Core settings/schema/signing IDs remain unchanged. Persisted data survival
is covered by startup and existing isolated upgrade tests; transient UI Bundle restoration across
arbitrary older app versions is not a data-backup guarantee. No historical Stable binary was
installed on an owner device for this change.

Real hardware decoding/DRM, provider authentication, external companion apps, TalkBack on hardware,
custom fonts/extreme UI zoom and overscan still need separately approved QA acceptance. Neither
Chromecast installation was opened, installed, uninstalled or cleared. Debug build artifacts remain
local/CI-only; no release was signed or distributed.

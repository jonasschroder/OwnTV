# v0.16 Phase 1 — global updates and native interaction

Planning reference: [PR #32](https://github.com/jonasschroder/OwnTV/pull/32). Base `88a51db17493869805db7763a5e69c22a6299dd1`; independent of focus/performance PR #33. This is an implementation workstream for one future v0.16 release, not a separate release proposal.

## Implemented

- The existing version label becomes the global update entry in the **same top row**, reachable from all five destinations. Availability/progress changes the label without stealing focus or opening a modal. Back restores that entry. No extra navigation level.
- Reuses MinTvUpdater and the shared About route. Shows current version/build and channel; review stage includes authenticated version notes, target version/code, download size and available space. Explicit Update, Later, cached-install and Android-consent actions.
- Distinct review → download/verification → Android approval states, percentage and bytes. Closing the dialog keeps current work; leaving the app still uses the existing transport cancellation policy. Interrupted downloads truthfully restart from the beginning after another explicit action; no range-resume claim.
- Reopening does not overwrite verified download/permission/confirmation state with a fresh check. Rapid repeated OK is coalesced even while the manager lock is occupied. Acknowledging an installed receipt restores the normal version label.
- Download progress publishes only on percentage changes rather than every 64 KiB buffer. The storage budget is unchanged. Existing foreground-only transport, rate limits, package/channel/signer/hash/ABI/minSdk/version checks and PackageInstaller verification/consent remain authoritative.

## Native evidence and tests

Folders `1920x1080/` and `1280x720/` contain all five actual MainActivity screens with the new global entry, plus `UPDATE` opened through that entry. Same deterministic synthetic profile/library as the phase-0 baseline. Current local debug stamp is **0.14.0-dev / 2**, not the current published QA or v0.16 identity. The debug signing guard correctly refuses update network/installation; its honest setup-error state is shown in the full-shell UPDATE image.

`*-synthetic.png` exercises the **same production UpdatePresentation** in ComponentActivity with clearly named `0.16.0-test-fixture` / `1000099` metadata and 30 test-note lines. This demonstrates typography, focus and state presentation, not an authenticated published v0.16 release or Android's system installer. Callback fixtures cannot download or install. Additional checking/verification/permission/confirmation/failure/receipt captures remain in the local evidence directory.

- 26 focused JVM tests pass: UpdateExperience 8, UpdatePolicy 13, UpdateTransport 5.
- QA debug + androidTest compile passes.
- Both existing mandatory UpdateDialogNavigationTest methods pass at **1080p and 720p**. Extended coverage verifies global-entry Back restoration; initial Update focus; real D-pad notes scrolling; eleven states; no automatic callback; Available → Downloading moves focus to Continue browsing and safely closes without a transfer. Logs retained here.
- Actual MainActivity capture probe passes at both sizes, opens/closes global updates and restores focus. Uses the separate PR #33 instrumentation harness against this workstream's production APK; performance production changes are not included.
- Literal inventory, locale-number and overflow checks pass. Complete existing GitHub CI must pass; no workflow/signer/Core/persistence changes.

Reproduce only on a disposable qemu Android TV: compile QA debug/androidTest; use explicit serial; run UpdateDialogNavigationTest with `disposableEmulator=true` at 1920×1080/dpi320 then 1280×720/dpi213. Pull `files/v016-update-screenshots/`. For full-shell captures run the PR #33 PerformanceJourneyTest screenshot probe with `performanceProbe=true` after its synthetic seed. No physical-device installation is authorized by this recipe.

## Review / remaining work

This is the native visual reference for update-flow review. Approved Home composition, TV grid, Sport and Film & serier remain intact. Larger layout/navigation replacements await reference review as required by #32. Settings already uses left categories/right content; no second settings architecture was introduced.

An owner-approved permanently signed QA is still required to test a real compatible upgrade, system permission/confirmation and process restart on Chromecast. No debug APK may replace the owner's existing QA. Network loss/process-death UI is covered by policy/transport tests and synthetic state fixtures, not a claimed real signed download/install. Partial files are not resumed by HTTP range. No picture/sound, hockey-source or data-migration claim is made here.

Rollback: revert this workstream; no schema migration, Core change or new permission. Signing configuration, protected workflows, release metadata and Stable/QA identities are unchanged. Neither owner's installed app was touched.

# M1 validation ledger

## Executed locally

- Initial Standard debug JVM suite: PASS (existing cached baseline).
- Tooling baseline: 52 Python tests PASS.
- Standard and QA debug JVM suites: 460 tests each, 0 failures/errors/skips; includes 9 NativeNavigationTest cases.
- Standard debug production Kotlin + Android test Kotlin compilation: PASS.
- Literal inventory, explicit number locales and text overflow: PASS.
- No ADB command, install, uninstall, clear, permanent signing or release dispatch performed.

Standard/QA debug APK packaging, x86_64 test APK packaging, both JVM suites and both lint variants
passed locally (3m 7s stable-source run). The later focus refinements also compile and pass both JVM
variants; final lint/CI evidence is pending. Initial CI production startup passed all eight executions
and produced real native 1080p/720p route captures. Initial D-pad suite exposed an Up redirect cancelled
by a subsequent cancelFocusChange call (fixed), and one fixture profile-change/input ordering race
(fixed by awaiting Compose idle before dispatch). The final CI run must execute every case again. Do not interpret compiled test sources as executed
Android tests. The documentation-only PR #16's checks are not results for this implementation.

Local packaging initially failed because the sandbox cannot create a debug keystore in the normal Android user directory. The rerun uses a task-owned ANDROID_USER_HOME outside the repository; permanent production keys/configuration remain unchanged.

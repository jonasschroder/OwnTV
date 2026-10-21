# M1 validation ledger

## Executed locally

- Initial Standard debug JVM suite: PASS (existing cached baseline).
- Tooling baseline: 52 Python tests PASS.
- Standard and QA debug JVM suites: 459 tests each, 0 failures/errors/skips; includes 8 NativeNavigationTest cases.
- Standard debug production Kotlin + Android test Kotlin compilation: PASS.
- Literal inventory, explicit number locales and text overflow: PASS.
- No ADB command, install, uninstall, clear, permanent signing or release dispatch performed.

Full Standard/QA debug builds, both JVM variants, x86_64 instrumentation APK compilation and lint
are being verified; final results will replace this provisional entry before review completion.
CI instrumentation/screenshot evidence is pending. Do not interpret compiled test sources as executed
Android tests. The documentation-only PR #16's checks are not results for this implementation.

Local packaging initially failed because the sandbox cannot create a debug keystore in the normal Android user directory. The rerun uses a task-owned ANDROID_USER_HOME outside the repository; permanent production keys/configuration remain unchanged.

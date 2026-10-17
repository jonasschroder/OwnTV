# Min TV v0.8 QA startup regression

Owner report: [merged PR #11](https://github.com/jonasschroder/OwnTV/pull/11#issuecomment-6239480436).
Known-working published QA v0.7 source: `ea50fa5ca2662d8c6dfca053fbabc4b948465c34`.
Affected published QA v0.8 source: `18e34861b0ff023ff4c5ea8e0184ae0f4f27a894` (`qa-1000008`).

## Reproduced exception and minimal fix

The full production startup test at `2b5fe33bdf779b1f4b54327e6920326ea92c409b`, whose
application code is unchanged from affected main, fails with:

```text
java.lang.IllegalArgumentException: Type of the key SwedishRowKey(id=101001,
sourceId=101, stableKey=101:synthetic-1) is not supported. On Android you can
only use types which can be stored inside the Bundle.
 at androidx.compose.runtime.saveable.SaveableStateHolderImpl.SaveableStateProvider(SaveableStateHolder.kt:74)
 at androidx.compose.foundation.lazy.layout.LazySaveableStateHolder.SaveableStateProvider(LazySaveableStateHolder.kt:76)
```

[Before-fix production test](https://github.com/jonasschroder/OwnTV/actions/runs/39069829130/job/117505058492).
The Android instrumentation output records one actual failing production-startup test (16.325s,
including the harness, profile gate and startup); this is not a measurement of the owner's one-second
interval. Compose's test environment captured the exception, so this evidence is a production-path
exception under instrumentation, not an owner-device `FATAL EXCEPTION` log.

v0.7 used a `Long` channel ID as its favorite `LazyRow` key. v0.8 changed the real Home's
quick-channel/Now rows to `ChannelEntity.homeEpgKey()`, a plain `SwedishRowKey` data class.
Compose wraps lazy items in a saveable-state provider; the Android parent registry rejects
this non-Serializable, non-Parcelable cache key. The failure happens when populated channel
summaries arrive, even if EPG is missing and preview is disabled. Empty-screen and prior isolated
Home fixtures did not execute this production key path: their synthetic rail used default index keys.

Only the UI key changes to a `String` containing the exact same source ID, row ID and stable channel
key. The EPG/cache key remains unchanged. No channels, source identities, quality choices, profile
values or stored preferences are modified, and no database migration is introduced. There is no
blanket catch, empty-list substitution or feature shutdown. Unit regressions protect stable identity,
source/variant separation, harmless display changes and exclusion of stream URLs from saved UI keys.

Confidence is high for this reproducible v0.8 defect. Without an owner-device stack trace, we cannot
prove it is the only fault on that particular Chromecast. No owner ADB setup or diagnostics are required.

## Production-path regression coverage

`ProductionStartupTest` uses the shipped `OwnTVApp`, production Koin definitions, real on-disk
Room database/DataStore/SharedPreferences and the installed QA Leanback entry/`MainActivity`.
It does not call `setContent`, replace the UI graph, mock a ViewModel or use an in-memory database.

A fresh disposable Android TV emulator seeds two profiles (IDs 7/8), two linked IPTV sources,
600 channels, per-profile favorites/history, missing EPG, a global EPG offset, personal sports choices
and v0.7-style Home preference JSON. URLs are loopback dummy inputs, not user streams. Preview and
automatic sync are disabled only in this synthetic dataset, not in application code.

Seeding and subsequent cold startups run in separate app processes without uninstalling or clearing
data. The required matrix runs English/1080p/profile 7, English/720p/profile 8 and Swedish/1080p/profile 7.
Each actual Home must appear, survive eight more real seconds, background/return and Activity recreation.
Profiles, sources, channel samples, favorites, history, sports choices, EPG offset and saved Home settings
must survive. Real production screenshots and failure/crash buffers are collected as CI evidence.

The runner refuses physical devices, non-CI execution and pre-existing QA installations before doing
anything. It targets only QA on its fresh emulator. It does not install or alter an owner's app, clear
storage, downgrade, bypass signers, use protected signing environments or publish an APK. Ordinary
CI debug builds/test infrastructure are used; no permanent signing keys or signing workflows change.

This is a persisted-v0.7-format-data cold-start regression, not a claim that the published ARM v0.7
APK was installed/upgraded in the emulator. Core version/pin and schema are unchanged across the
compared releases. `OwnTVApp`, `MainActivity` and `HomeViewModel` startup code are unchanged by the
v0.8 transition; the relevant change is the Home composition and its lazy channel identity. The shell,
profile gate, DI, optional hockey/Twitch initialization and background return are exercised in the real test.

## Validation and remaining limits

Final source/run identifiers, exact test counts and current evidence links are recorded in the fix PR.
Both standard/QA compilation, JVM tests, lint, existing TV regressions, startup matrix, i18n/release
packaging and read-only distribution checks must pass before review is complete. Debug/emulator
success is not physical Chromecast, ARM hardware-decoder or actual signed-release acceptance.

## Owner retest after a separately approved QA update — no ADB

1. Leave the currently installed apps and data intact. Do not uninstall, clear storage or install a debug APK.
2. After review and separate signing/distribution approval, install the approved QA update through the
   existing trusted update path with its permanent QA signing identity. Production is not part of this fix rollout.
3. Open Min TV Test, choose the usual profile if asked, and leave Home open for at least 30 seconds.
4. Check your existing IPTV sources, favorites, last-watched channel, EPG and sports/Home settings.
5. Exit and reopen, switch profiles, return from another TV app and verify the Home still works.
6. If it still closes, report only the visible behavior and installed version; no ADB/log collection is requested.

This PR does not merge, sign, distribute or change any installed application. No release is approved by these tests.

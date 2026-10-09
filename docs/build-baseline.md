# Reproducible debug build

The Min TV prototype keeps this Core baseline. Its independent app identity and
HOME/install workflow are documented in [Min TV v0.1](min-tv-v0.1.md).

The app uses APIs and resources introduced after the published OwnTV Core 1.0.64
artifacts. The default build therefore substitutes **both** `core` and `player-core`
with Core source at the exact commit recorded in `gradle/owntv-core.commit`:
`adca2bcd653f19e6e5d659c2722309e6aef4ec15`.

This revision contains `OwnTVPlayer.archiveStalled`,
`LiveTuneController.expectPromotion`, `CatchupContinue.liveAfterStall`,
`PlaybackEngine.stalledSinceMs`, category-restore resources and
`player_reconnecting`. These are absent in 1.0.64. No playback features or UI
are removed to accommodate older artifacts. The Core locale catalog matches the
app's copy. Core and the app use the same AGP, Kotlin and KSP versions.

## Build and test

Prerequisites: Git, Python 3 (the existing i18n checks), JDK **21** including
`javac`, and Android SDK Platform **37.0**, Build-Tools **37.0.0** and
platform-tools. Set `ANDROID_HOME` to your SDK location. Use the repository's
Gradle **9.7.1** wrapper. No signing credentials or Maven token are required for
debug builds. Network access is required for GitHub, Google/Maven/Gradle
dependencies and `ahxn00.github.io` (the native libmpv artifact).

From the app checkout:

```bash
bash tools/prepare-core.sh
./gradlew :app:assembleStandardDebug :app:testStandardDebugUnitTest :app:lintStandardDebug --max-workers=4
./gradlew :OwnTV_Core:core:testDebugUnitTest :OwnTV_Core:player-core:testDebugUnitTest --max-workers=4
```

The preparation script fetches the immutable commit into the existing ignored
`.gradle-cache/OwnTV_Core` directory, using a detached HEAD. Repeating it reuses
the same clean checkout; it refuses to overwrite a different revision or local
changes. Default Gradle configuration also checks the checkout's revision and
tracked changes. When changing the pin, preserve any local work and move the
old checkout aside before preparing again. The source pin, not the published
version in `libs.versions.toml`, determines Core for this baseline.

The ARM debug APK is `app/build/outputs/apk/standard/debug/app-standard-debug.apk`.
JVM results are under `app/build/reports/tests/`, with Core results under
`.gradle-cache/OwnTV_Core/{core,player-core}/build/reports/tests/`. Lint reports
are under `app/build/reports/`.

For an x86_64 Android TV emulator, use `:app:assembleX86_64Debug` instead.
Installing and exercising an APK requires a matching Android TV device or
emulator; JVM tests do not validate actual video playback.

## Local Core development

The existing override remains available:

```bash
./gradlew -Powntv.corePath=/absolute/path/to/OwnTV_Core :app:assembleStandardDebug
```

An explicit override intentionally bypasses the source pin so Core changes can
be tested locally. Do not use an unpinned override for reproducible CI or release
builds. CI prepares the same pinned checkout before tests and APK assembly.

## Codex cloud environment

This environment retains its JDK and SDK under `/workspace/cloud-setup` and
`/workspace/android-sdk`. Its helper configures the HTTPS proxy and system TLS
trust store without disabling verification. Run the same commands above with
`python3 /workspace/cloud-setup/run.py` prepended to `./gradlew`. Each task is
already isolated: use its existing checkout; no additional Git worktree is needed.

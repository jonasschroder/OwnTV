# Manual public-source Android diagnostic

`HubPublicSourceProbe.kt` verifies the real public NHL transport/parser/cache and writes only
sanitized source/timestamp/count/coverage state. This is an optional live diagnostic, not a
mandatory deterministic regression: external daily/monthly quotas must not be spent by CI.
All source fixture tests and native regression cases remain mandatory in their existing suites.

For an **explicitly owner-approved disposable emulator only**, with the existing local Android
SDK/JDK/Core setup, build the additional diagnostic into a temporary test source:

```sh
ANDROID_SERIAL=emulator-5588 bash tools/sporthub/build-public-probe.sh
```

The script refuses physical devices and existing temporary sources, cleans up its own source,
and does not install or request network data. The compatible local QA debug app must already
exist in that disposable emulator. After separate installation approval for that emulator:

```sh
adb -s emulator-5588 install -r app/build/outputs/apk/androidTest/qa/debug/app-qa-debug-androidTest.apk
adb -s emulator-5588 shell am instrument -w -r -e disposableEmulator true -e liveSportProbe true \
  -e class tv.own.owntv.home.HubPublicSourceProbe \
  se.jonasschroder.mintv.qa.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5588 pull /sdcard/Android/data/se.jonasschroder.mintv.qa/files/sport-hub-live-proof.json /tmp/mintv-public-probe/
```

Check the emulator's UTC clock first. Respect the documented provider quota and persistent
budget; do not clear budget files or repeat a request to evade denial/cooldown. A fresh response,
cached response, warning and failure are distinct. This does not verify broadcasts or physical
Chromecast playback. Never use this local debug tool over an owner's signed QA installation.

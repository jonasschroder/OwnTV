#!/usr/bin/env bash
# Only a fresh disposable CI emulator. No owner device, permanent signing, uninstall or data reset.
set -euo pipefail
[ "${GITHUB_ACTIONS:-}" = true ] || { echo 'CI emulator required'; exit 1; }
[[ "$(adb get-serialno)" == emulator-* ]] && [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ] || { echo 'Physical devices refused'; exit 1; }
qa=se.jonasschroder.mintv.qa
[ -z "$(adb shell pm path "$qa" 2>/dev/null | tr -d '\r')" ] || { echo 'Existing QA installation refused'; exit 1; }
mkdir -p startup-evidence
capture() {
  result=$?
  trap - EXIT
  adb logcat -d -b crash > startup-evidence/crash-logcat.txt || true
  adb shell dumpsys activity activities > startup-evidence/activities.txt || true
  python3 - <<'PYCRASH'
from pathlib import Path
s=Path('startup-evidence/crash-logcat.txt').read_text()[-16000:]
if 'FATAL EXCEPTION' in s:
    print('::error title=Production startup fatal exception::'+s.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
PYCRASH
  exit "$result"
}
trap capture EXIT
./gradlew :app:assembleQaDebug :app:assembleQaDebugAndroidTest -Pmintv.upgradeTestAbi=x86_64 --max-workers=4 --console=plain
adb install -r app/build/outputs/apk/qa/debug/app-qa-debug.apk
adb install -r app/build/outputs/apk/androidTest/qa/debug/app-qa-debug-androidTest.apk
run_case() {
  local name="$1"
  adb shell am instrument -w -r -e disposableEmulator true -e class "tv.own.owntv.home.ProductionStartupTest#$name" "$qa.test/androidx.test.runner.AndroidJUnitRunner" | tee "startup-evidence/$name.txt"
  python3 - "$name" <<'PY'
import pathlib,sys
s=pathlib.Path('startup-evidence',sys.argv[1]+'.txt').read_text()
assert 'OK (1 test)' in s and 'FAILURES' not in s and 'INSTRUMENTATION_FAILED' not in s, s[-12000:]
PY
}
run_case seedPersistedQaData
adb shell am force-stop "$qa"
run_case productionHomeSurvivesPersistedColdStartAndActivityRecreation

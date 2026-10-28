#!/usr/bin/env bash
# Only a fresh disposable CI emulator. No owner device, permanent signing, uninstall or data reset.
set -euo pipefail
[ "${GITHUB_ACTIONS:-}" = true ] || { echo 'CI emulator required'; exit 1; }
[[ "$(adb get-serialno)" == emulator-* ]] && [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ] || { echo 'Physical devices refused'; exit 1; }
qa=se.jonasschroder.mintv.qa
[ -z "$(adb shell pm path "$qa" 2>/dev/null | tr -d '\r')" ] || { echo 'Existing QA installation refused'; exit 1; }
mkdir -p startup-evidence
exec > >(tee startup-evidence/host.txt) 2>&1
capture() {
  result=$?
  trap - EXIT
  adb pull "/sdcard/Android/data/$qa/files/startup-screenshots" startup-evidence/screenshots >/dev/null 2>&1 || true
  adb shell wm size reset >/dev/null 2>&1 || true
  adb shell wm density reset >/dev/null 2>&1 || true
  adb logcat -d -b crash > startup-evidence/crash-logcat.txt || true
  if [ "$result" -ne 0 ]; then
    # Only synthetic data exists on this guarded disposable emulator. Keep a bounded decoder
    # excerpt for an actual playback failure, rather than gathering every successful device log.
    adb logcat -d -s 'LiveEngine:I' 'OwnTV-LivePreviewEngine:I' '*:S' | tail -n 180 > startup-evidence/decoder-failure-logcat.txt || true
  fi
  adb shell dumpsys activity activities > startup-evidence/activities.txt || true
  python3 - <<'PYCRASH'
from pathlib import Path
s=Path('startup-evidence/crash-logcat.txt').read_text()[-16000:]
if 'FATAL EXCEPTION' in s:
    print('::error title=Production startup fatal exception::'+s.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
PYCRASH
  if [ "$result" -ne 0 ]; then
    python3 - <<'PYFAIL'
from pathlib import Path
p=Path('startup-evidence/host.txt')
s=p.read_text()[-5000:] if p.exists() else 'No host log'
print('::error title=Startup reproduction host failure::'+s.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
PYFAIL
  fi
  exit "$result"
}
trap capture EXIT
./gradlew :app:assembleQaDebug :app:assembleQaDebugAndroidTest -Pmintv.upgradeTestAbi=x86_64 --max-workers=4 --console=plain
adb install -r app/build/outputs/apk/qa/debug/app-qa-debug.apk
adb install -r app/build/outputs/apk/androidTest/qa/debug/app-qa-debug-androidTest.apk
run_case() {
  local name="$1" label="${2:-$1}" profile="${3:-7}"
  adb shell am instrument -w -r -e disposableEmulator true -e startupProfile "$profile" -e premiumHome "${4:-false}" -e class "tv.own.owntv.home.ProductionStartupTest#$name" "$qa.test/androidx.test.runner.AndroidJUnitRunner" | tee "startup-evidence/$label.txt"
  python3 - "$label" <<'PY'
import pathlib,sys
s=pathlib.Path('startup-evidence',sys.argv[1]+'.txt').read_text()
if 'OK (1 test)' not in s or 'FAILURES' in s or 'INSTRUMENTATION_FAILED' in s:
    print('::error title=Production startup case failed::'+s[-16000:].replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
    raise SystemExit(1)
PY
}
run_case seedPersistedQaData
adb shell am force-stop "$qa"
run_case productionHomeSurvivesPersistedColdStartAndActivityRecreation cold-en-1080p-profile7 7
adb shell am force-stop "$qa"
adb shell wm size 1280x720
run_case productionHomeSurvivesPersistedColdStartAndActivityRecreation cold-en-720p-profile8 8
run_case selectSwedishLocale
adb shell am force-stop "$qa"
adb shell wm size reset
run_case productionHomeSurvivesPersistedColdStartAndActivityRecreation cold-sv-1080p-profile7 7
# Rich, persisted production-shell comparison. Never replaces the original five executions.
run_case populatePremiumHomeFixture
adb shell am force-stop "$qa"
run_case productionHomeSurvivesPersistedColdStartAndActivityRecreation premium-sv-1080p 7 true
adb shell am force-stop "$qa"
adb shell wm size 1280x720
# Native 720p TV scale (213 dpi). The original 720p/320 dpi stress cold start above stays mandatory.
adb shell wm density 213
run_case productionHomeSurvivesPersistedColdStartAndActivityRecreation premium-sv-720p 7 true
# Actual decoder fixture; both resolutions, same installed disposable QA identity.
adb shell am force-stop "$qa"
adb shell wm size reset
adb shell wm density reset
run_case liveEntryMatrixKeepsRealDecoderAliveAcrossFullscreenDisposal playback-entry-1080p
adb shell am force-stop "$qa"
adb shell wm size 1280x720
adb shell wm density 213
run_case liveEntryMatrixKeepsRealDecoderAliveAcrossFullscreenDisposal playback-entry-720p
python3 - <<'PYREQUIRED'
from pathlib import Path
required=['seedPersistedQaData','cold-en-1080p-profile7','cold-en-720p-profile8','selectSwedishLocale','cold-sv-1080p-profile7','populatePremiumHomeFixture','premium-sv-1080p','premium-sv-720p','playback-entry-1080p','playback-entry-720p']
for name in required:
    assert 'OK (1 test)' in Path('startup-evidence',name+'.txt').read_text(), name
print('PASS: 10 real QA instrumentation executions; actual playback entry matrix, five cold starts, two profiles, 1080p/720p, English/Swedish; persistent data verified; no owner device.')
PYREQUIRED

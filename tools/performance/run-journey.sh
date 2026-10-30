#!/usr/bin/env bash
# Opt-in synthetic QA data on one disposable emulator. Never auto-select a physical target.
set -euo pipefail
serial="${ANDROID_SERIAL:?Set ANDROID_SERIAL to a disposable emulator serial}"
[[ "$serial" == emulator-* ]] || { echo 'Physical device refused'; exit 1; }
[ "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ] || exit 1
out="${1:-performance-evidence}"
mkdir -p "$out"
qa=se.jonasschroder.mintv.qa
run() {
  local method="$1" label="$2"
  adb -s "$serial" shell am instrument -w -r -e performanceProbe true -e perfLabel "$label" -e class "tv.own.owntv.performance.PerformanceJourneyTest#$method" "$qa.test/androidx.test.runner.AndroidJUnitRunner" > "$out/$label.txt" 2>&1
  python3 - "$out/$label.txt" <<'PY'
import pathlib,sys
s=pathlib.Path(sys.argv[1]).read_text()
if 'OK (1 test)' not in s or 'FAILURES' in s or 'INSTRUMENTATION_FAILED' in s or 'INSTRUMENTATION_STATUS_CODE: -3' in s:
    print(s[-5000:]); raise SystemExit(1)
PY
}
# The caller installs a fixture-signed build only in its own disposable emulator. No installer here.
run seedControlledLibrary seed
for label in journey-1 journey-2; do
  adb -s "$serial" shell am force-stop "$qa"
  run repeatedProductionNavigation "$label"
  adb -s "$serial" pull "/sdcard/Android/data/$qa/files/performance/$label.json" "$out/$label.json" >/dev/null
  echo "PASS $label (synthetic production-shell navigation)"
done
# Every instrumentation case is executed by CI's guarded runner, including native evidence
# and the isolated visible-row probe. Keep these after the matched journey measurements.
run captureNativeProductionScreens native-production
adb -s "$serial" pull "/sdcard/Android/data/$qa/files/performance/native" "$out/native" >/dev/null
run visibleSportRowProbe visible-sport-row
adb -s "$serial" pull "/sdcard/Android/data/$qa/files/performance/visible-sport-row.json" "$out/visible-sport-row.json" >/dev/null
echo "PASS native screenshots and visible Sport row probe"

# Populated visual/remote scenarios are deliberately AFTER all identical-data timings.
run capturePopulatedNativeShell populated-native
adb -s "$serial" pull "/sdcard/Android/data/$qa/files/performance/native" "$out/populated-native" >/dev/null
echo "PASS populated actual MainActivity and remote return journeys"

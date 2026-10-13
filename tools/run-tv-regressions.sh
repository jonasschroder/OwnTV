#!/usr/bin/env bash
# Disposable CI emulator only. Preserve screenshots on failures without masking test status.
set -euo pipefail
capture_screenshots() {
  test_status=$?
  trap - EXIT
  screenshot_dir=/sdcard/Android/data/se.jonasschroder.mintv/files/v04-screenshots
  if adb shell test -d "$screenshot_dir"; then
    if ! adb pull "$screenshot_dir" v04-device-screenshots; then
      echo '::error::Could not collect synthetic emulator screenshots.'
      if [ "$test_status" -eq 0 ]; then test_status=1; fi
    fi
  elif [ "$test_status" -eq 0 ]; then
    echo '::error::Synthetic screenshots missing after successful tests.'
    test_status=1
  fi
  exit "$test_status"
}
trap capture_screenshots EXIT
./gradlew :app:connectedX86_64DebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=tv.own.owntv.home.HomeRegressionSuite \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --max-workers=4 --stacktrace

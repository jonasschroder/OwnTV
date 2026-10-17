#!/usr/bin/env bash
# Disposable CI emulator only. Preserve screenshots on failures without masking test status.
set -euo pipefail
# The 720p fixture changes display size temporarily. Refuse every physical device even if the
# instrumentation argument is supplied accidentally; no installed owner app may be touched.
if [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]; then
  echo '::error::TV regression runner requires a disposable Android emulator; physical devices are refused.'
  exit 1
fi
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
  if [ -d v04-device-screenshots ]; then
    cp docs/images/v08/v08-iteration1-*-synthetic.png v04-device-screenshots/
    cp docs/min-tv-v08-comparison.html v04-device-screenshots/v08-review-compare.html
  fi
  exit "$test_status"
}
trap capture_screenshots EXIT
./gradlew :app:connectedX86_64DebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.disposableEmulator=true \
  -Pandroid.testInstrumentationRunnerArguments.class=tv.own.owntv.home.HomeRegressionSuite \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --max-workers=4 --stacktrace

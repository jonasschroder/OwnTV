#!/usr/bin/env bash
# Manual live diagnostic. CI never consumes a public provider's quota. No installer here.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
serial="${ANDROID_SERIAL:?Choose the explicitly approved disposable emulator}"
[[ "$serial" == emulator-* ]] || { echo 'Physical device refused'; exit 1; }
[[ "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 1
out="${1:-/tmp/mintv-public-probe}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"
dest="$root/app/src/androidTest/java/tv/own/owntv/home/HubPublicSourceProbe.kt"
[[ ! -e "$dest" ]] || { echo 'Refusing to replace an existing source'; exit 1; }
cp "$root/tools/sporthub/HubPublicSourceProbe.kt" "$dest"
trap 'rm -f "$dest"' EXIT
cd "$root"
if ! ./gradlew :app:assembleQaDebugAndroidTest --console=plain > "$out/build.log" 2>&1; then
  tail -40 "$out/build.log"
  exit 1
fi
echo 'PASS: optional diagnostic test APK built; temporary source removed on exit.'
echo 'No installation, live request or app-data change was performed.'
echo 'APK: app/build/outputs/apk/androidTest/qa/debug/app-qa-debug-androidTest.apk'

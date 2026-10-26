#!/usr/bin/env bash
# One identity/task per process: keep Kotlin, R8 and lint within the hosted runner budget.
set -euo pipefail
case "${1:-}" in
  :app:assembleStandardDebug|:app:assembleStandardRelease|:app:lintStandardRelease|:app:assembleQaRelease|:app:lintQaRelease) ;;
  *) echo 'Expected one reviewed i18n build/lint task' >&2; exit 2 ;;
esac
[ "$#" -eq 1 ] || { echo 'Only one task per invocation is allowed' >&2; exit 2; }
mkdir -p i18n-build-evidence
label="${1//:/_}"
# Public process/resource metrics only; no environment dump, credentials or provider data.
{ free -m; df -h .; ps -eo pid,ppid,rss,etime,comm --sort=-rss | head -n 12; } > "i18n-build-evidence/${label}-resources.txt" || true
# QUIT produces JVM thread diagnostics on a stall; timeout/kill must fail the required check.
# Configuration-cache execution also parallelizes tasks within one project; --no-parallel
# alone does not serialize those tasks. Keep the ordinary task-output build cache enabled.
timeout --signal=QUIT --kill-after=30s 25m ./gradlew "$1" \
  --no-daemon --no-configuration-cache --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process \
  '-Dorg.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' \
  --console=plain --stacktrace 2>&1 | tee "i18n-build-evidence/${label}.log"

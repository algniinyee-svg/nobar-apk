#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: JDK 17 is required." >&2; exit 2
fi
if ! command -v gradle >/dev/null 2>&1; then
  echo "ERROR: Gradle 8.9 is required. Install it or run the GitHub Actions workflow android-debug-apk.yml." >&2; exit 2
fi
java -version 2>&1 | head -1
gradle --version | sed -n '1,12p'
python3 scripts/verify-source.py
bash scripts/test-js.sh
gradle --no-daemon clean testDebugUnitTest assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
sha256sum "$APK"
echo "APK ready: $APK"

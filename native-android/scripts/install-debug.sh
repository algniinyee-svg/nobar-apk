#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
APK="app/build/outputs/apk/debug/app-debug.apk"
if ! command -v adb >/dev/null 2>&1; then
  echo "ERROR: Android platform-tools (adb) are required." >&2; exit 2
fi
if [[ ! -s "$APK" ]]; then
  echo "ERROR: $APK not found. Run scripts/build-debug.sh first." >&2; exit 2
fi
adb devices
adb install -r "$APK"
echo "Installed com.algani.nobar. Launch Nobar from the device launcher."

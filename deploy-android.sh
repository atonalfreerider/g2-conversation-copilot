#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
cd "$project_dir"

if command -v gradle >/dev/null 2>&1; then
  gradle :app:assembleDebug
elif [[ -x ./gradlew ]]; then
  ./gradlew :app:assembleDebug
else
  echo "Gradle is required. Open this folder in Android Studio once, or install Gradle, then rerun." >&2
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "Android platform-tools (adb) are required." >&2
  exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "No authorized Android device detected. Unlock the Pixel and accept the USB debugging prompt." >&2
  exit 1
fi

apk="$project_dir/app/build/outputs/apk/debug/app-debug.apk"
adb install -r "$apk"
adb shell am start -n com.g2copilot.settings/.MainActivity
echo "G2 Copilot installed and opened."

#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
cd "$project_dir"

# Gradle 8.11 cannot run on Java 25. Prefer the Android baseline (17), then 21.
jdk_dir=""
for candidate in /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-21-openjdk-amd64 "$HOME/.sdkman/candidates/java/17.0.*/"; do
  if [[ -x "$candidate/bin/java" && -x "$candidate/bin/javac" ]]; then jdk_dir="${candidate%/}"; break; fi
done
if [[ -z "$jdk_dir" ]]; then
  echo "A full JDK 17 or 21 is required; the installed Java 21 runtime has no compiler." >&2
  echo "Install it with: sudo apt install openjdk-21-jdk-headless" >&2
  exit 1
fi
export JAVA_HOME="$jdk_dir"
export PATH="$JAVA_HOME/bin:$PATH"

sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_dir" ]]; then
  for candidate in "$HOME/Android/Sdk" /usr/lib/android-sdk /opt/android-sdk; do
    if [[ -f "$candidate/platforms/android-35/android.jar" ]]; then sdk_dir="$candidate"; break; fi
  done
fi

if [[ ! -x "$sdk_dir/build-tools/35.0.0/aapt2" ]]; then
  echo "Android SDK Build Tools 35.0.0 are missing." >&2
  echo "Install them with: sudo apt install google-android-build-tools-35.0.0-installer" >&2
  exit 1
fi

if [[ ! -f "$sdk_dir/platform-tools/source.properties" && ! -f "$sdk_dir/platform-tools/package.xml" ]]; then
  echo "Android SDK Platform-Tools are incomplete or not registered with the SDK." >&2
  echo "Install them with: sudo apt install google-android-platform-tools-installer" >&2
  exit 1
fi

if [[ -z "$sdk_dir" || ! -f "$sdk_dir/platforms/android-35/android.jar" ]]; then
  echo "Android SDK Platform 35 is not installed." >&2
  echo "On Ubuntu, install it with:" >&2
  echo "  sudo apt install google-android-platform-35-installer google-android-build-tools-35.0.0-installer" >&2
  echo "Then rerun this script; it will configure local.properties automatically." >&2
  exit 1
fi

escaped_sdk_dir="${sdk_dir//\\/\\\\}"
escaped_sdk_dir="${escaped_sdk_dir//:/\\:}"
printf 'sdk.dir=%s\n' "$escaped_sdk_dir" > local.properties

if [[ -x "$HOME/.sdkman/candidates/gradle/8.11.1/bin/gradle" ]]; then
  "$HOME/.sdkman/candidates/gradle/8.11.1/bin/gradle" --stop >/dev/null 2>&1 || true
  "$HOME/.sdkman/candidates/gradle/8.11.1/bin/gradle" --no-daemon -Dorg.gradle.java.home="$JAVA_HOME" :app:assembleDebug
elif command -v gradle >/dev/null 2>&1; then
  gradle_version="$(gradle --version | awk '/^Gradle / {print $2; exit}')"
  if [[ "$gradle_version" != 8.11.1 ]]; then
    echo "This project requires Gradle 8.11.1 (found $gradle_version)." >&2
    echo "Install it with: sdk install gradle 8.11.1" >&2
    exit 1
  fi
  gradle --stop >/dev/null 2>&1 || true
  gradle --no-daemon -Dorg.gradle.java.home="$JAVA_HOME" :app:assembleDebug
elif [[ -x ./gradlew ]]; then
  ./gradlew --stop >/dev/null 2>&1 || true
  ./gradlew --no-daemon -Dorg.gradle.java.home="$JAVA_HOME" :app:assembleDebug
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

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/android"
command -v gradle >/dev/null || { echo 'Gradle 8.9 required (not bundled). Install Android SDK 35 and Gradle first.' >&2; exit 2; }
: "${ANDROID_HOME:?ANDROID_HOME or SDK install required}"
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
printf 'APK: %s\n' "$PWD/app/build/outputs/apk/debug/app-debug.apk"

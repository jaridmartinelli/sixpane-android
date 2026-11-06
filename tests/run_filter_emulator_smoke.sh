#!/usr/bin/env bash
# This entire file runs in one shell, unlike the individual 'script:' lines
# executed by android-emulator-runner.
set +e
mkdir -p "$GITHUB_WORKSPACE/filter-evidence"
gradle --project-dir "$GITHUB_WORKSPACE/android" --no-daemon --stacktrace \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.sixpane.app.FilteredTabsInstrumentedTest
status=$?
timeout 12s adb pull /sdcard/Download/sixpane-filtered-14.png \
  "$GITHUB_WORKSPACE/filter-evidence/filtered-14.png" > \
  "$GITHUB_WORKSPACE/filter-evidence/screenshot-pull.log" 2>&1 || true
timeout 12s adb exec-out screencap -p > \
  "$GITHUB_WORKSPACE/filter-evidence/final-screen.png" 2>/dev/null || true
timeout 12s adb logcat -d -t 2200 > \
  "$GITHUB_WORKSPACE/filter-evidence/logcat.txt" 2>&1 || true
exit "$status"

#!/usr/bin/env bash
# A single shell is required: android-emulator-runner executes individual script
# lines in separate processes, so 'cd' and shell variables don't persist otherwise.
set +e
mkdir -p "$GITHUB_WORKSPACE/emulator-evidence"
timeout 12s adb shell dumpsys webviewupdate > "$GITHUB_WORKSPACE/emulator-evidence/webview.txt" 2>&1 || true
gradle --project-dir "$GITHUB_WORKSPACE/android" --no-daemon --stacktrace :app:connectedDebugAndroidTest
status=$?
# Screenshot and logs must be captured BEFORE the runner shuts down the emulator.
timeout 12s adb exec-out screencap -p > "$GITHUB_WORKSPACE/emulator-evidence/final-screen.png" 2>/dev/null || true
timeout 12s adb logcat -d -t 4000 > "$GITHUB_WORKSPACE/emulator-evidence/logcat.txt" 2>&1 || true
timeout 12s adb pull /sdcard/Android/data/com.sixpane.app.preview.filterview/files/sixpane-user-journey.png "$GITHUB_WORKSPACE/emulator-evidence/user-journey.png" > "$GITHUB_WORKSPACE/emulator-evidence/screenshot-pull.log" 2>&1 || true
exit "$status"

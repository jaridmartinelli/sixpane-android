#!/usr/bin/env bash
# One shell for the whole emulator session (android-emulator-runner runs each
# script line in a separate process). Evidence is collected before shutdown.
set +e
OUT="$GITHUB_WORKSPACE/tessera-evidence"; mkdir -p "$OUT"
timeout 15s adb shell dumpsys webviewupdate > "$OUT/webview.txt" 2>&1
gradle --project-dir "$GITHUB_WORKSPACE/tessera" --no-daemon --stacktrace :app:connectedDebugAndroidTest
status=$?
timeout 20s adb logcat -d -t 6000 > "$OUT/logcat.txt" 2>&1
timeout 60s adb pull /sdcard/Android/data/app.tessera.browser/files/shots "$OUT/shots" > "$OUT/pull.log" 2>&1
exit "$status"

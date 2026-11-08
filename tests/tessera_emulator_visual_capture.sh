#!/usr/bin/env bash
# Disposable Android emulator only. Do not connect to a user's phone or clear real device data.
set -uo pipefail
ROOT="${GITHUB_WORKSPACE:?}"
OUT="$ROOT/emulator-evidence"
APP="com.sixpane.app.preview.filterview"
mkdir -p "$OUT"
echo "Git revision: $(git -C "$ROOT" rev-parse HEAD)" | tee "$OUT/revision.txt"
adb devices -l > "$OUT/adb-devices.txt" 2>&1 || true
adb shell wm size > "$OUT/emulator-size.txt" 2>&1 || true
adb shell wm density > "$OUT/emulator-density.txt" 2>&1 || true
adb shell dumpsys webviewupdate > "$OUT/webview-provider.txt" 2>&1 || true
result=0
gradle --project-dir "$ROOT/android" --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest > "$OUT/gradle-build.log" 2>&1 || result=1
APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
if [[ -s "$APK" ]]; then
  adb install -r "$APK" > "$OUT/install.txt" 2>&1 || result=1
  # This wipes ONLY the ephemeral GitHub-hosted emulator, never the user's data.
  adb shell pm clear "$APP" > "$OUT/emulator-reset.txt" 2>&1 || true
  adb shell am start -W -n "$APP/com.sixpane.app.MainActivity" > "$OUT/launch.txt" 2>&1 || true
  sleep 5
  shot() {
    local name="$1"
    timeout 15s adb exec-out screencap -p > "$OUT/$name.png" 2>/dev/null || true
    timeout 20s adb shell uiautomator dump /sdcard/tessera-window.xml > "$OUT/$name-dump.log" 2>&1 || true
    timeout 15s adb exec-out cat /sdcard/tessera-window.xml > "$OUT/$name.xml" 2>/dev/null || true
  }
  SIZE="$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | tr -d '\r')"
  W="${SIZE%x*}"
  H="${SIZE#*x}"
  if ! [[ "$W" =~ ^[0-9]+$ && "$H" =~ ^[0-9]+$ ]]; then W=1080; H=2400; fi
  tap() { adb shell input tap "$(( W * $1 / 100 ))" "$(( H * $2 / 100 ))" || true; sleep 3; }
  # AOSP's disposable Pixel Launcher sometimes ANRs during startup, obscuring the app.
  # Dismiss that system-only dialog explicitly before capturing any Tessera layout.
  dismiss_system_anr() {
    for attempt in 1 2 3 4; do
      timeout 15s adb shell uiautomator dump /sdcard/tessera-window.xml >/dev/null 2>&1 || true
      if timeout 10s adb exec-out cat /sdcard/tessera-window.xml | grep -Eq "Pixel Launcher isn.t responding|Launcher isn.t responding"; then
        adb shell input tap "$(( W * 24 / 100 ))" "$(( H * 53 / 100 ))" || true
        sleep 3
      else
        break
      fi
    done
  }
  dismiss_system_anr
  shot "01-welcome"
  tap 50 94
  dismiss_system_anr
  shot "02-grid"
  tap 86 94
  dismiss_system_anr
  shot "03-workspaces"
  adb shell input keyevent 4 || true
  sleep 2
  tap 96 94
  dismiss_system_anr
  shot "04-pane-menu"
  adb shell input keyevent 4 || true
  sleep 2
  adb shell input tap "$(( W * 15 / 100 ))" "$(( H * 7 / 100 ))" || true
  adb shell input tap "$(( W * 15 / 100 ))" "$(( H * 7 / 100 ))" || true
  sleep 3
  dismiss_system_anr
  shot "05-expanded-attempt"
  adb shell input keyevent 4 || true
  sleep 2
  # Reopen the app to expose any fatal rendering/lifecycle errors.
  adb shell am force-stop "$APP" || true
  adb shell am start -W -n "$APP/com.sixpane.app.MainActivity" > "$OUT/relaunch.txt" 2>&1 || true
  sleep 4
  dismiss_system_anr
  shot "06-relaunch"
  timeout 20s adb logcat -d -t 6000 > "$OUT/logcat.txt" 2>&1 || true
else
  echo 'APK not produced, visual capture cannot run' > "$OUT/failure.txt"
fi
# Instrumentation runs against disposable emulator, independently of visual screenshots.
# Only tests valid for the preview package. Legacy toolbar and QA-only tests target
# different app contracts and must not be mislabeled as Tessera failures.
gradle --project-dir "$ROOT/android" --no-daemon --stacktrace :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sixpane.app.ProfileIsolationInstrumentedTest,com.sixpane.app.TesseraSelectionInstrumentedTest,com.sixpane.app.TesseraLayoutInstrumentedTest,com.sixpane.app.TesseraPopupInstrumentedTest > "$OUT/instrumentation.log" 2>&1 || result=1
find "$ROOT/android/app/build/outputs/androidTest-results" -type f -name '*.xml' -exec cp {} "$OUT"/ \; 2>/dev/null || true
exit "$result"

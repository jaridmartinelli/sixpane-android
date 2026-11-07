#!/usr/bin/env bash
# One shell for the whole emulator session (android-emulator-runner runs each
# script line in a separate process). Evidence is collected before shutdown.
set +e
OUT="$GITHUB_WORKSPACE/tessera-evidence"; mkdir -p "$OUT"
timeout 15s adb shell dumpsys webviewupdate > "$OUT/webview.txt" 2>&1
# The emulator's own setup app sometimes hangs and Android shows an "isn't responding"
# dialog over everything, which swallows the tests' taps. Disable it and hide system
# error dialogs; this affects only the throwaway emulator, never the app under test.
timeout 15s adb shell pm disable-user --user 0 com.google.android.googlesdksetup >/dev/null 2>&1
timeout 15s adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1
timeout 15s adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
# The emulator profile reports a hardware keyboard, which hides the on-screen keyboard.
# Show it anyway so typing journeys behave like a phone.
timeout 15s adb shell settings put secure show_ime_with_hard_keyboard 1 >/dev/null 2>&1
gradle --project-dir "$GITHUB_WORKSPACE/tessera" --no-daemon --stacktrace :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
status=$?
timeout 20s adb logcat -d > "$OUT/logcat.txt" 2>&1
timeout 60s adb pull /sdcard/Android/data/app.tessera.browser/files/shots "$OUT/shots" > "$OUT/pull.log" 2>&1
echo "=== TESSERA SUMMARY ==="
grep -h "TesseraTest" "$OUT/logcat.txt" | sed 's/.*TesseraTest/TesseraTest/'
grep -h -A3 "FAILED\|AssertionError" "$OUT/logcat.txt" | grep -i "tessera\|Assertion" | head -20
grep -m3 -i "Current WebView package\|versionName" "$OUT/webview.txt"
ls "$OUT/shots" 2>/dev/null
echo "--- test results ---"
python3 - "$GITHUB_WORKSPACE/tessera/app/build/outputs/androidTest-results" <<'PYEOF'
import glob, sys, xml.etree.ElementTree as ET
for f in glob.glob(sys.argv[1] + "/**/*.xml", recursive=True):
    for tc in ET.parse(f).getroot().iter("testcase"):
        fail = tc.find("failure") if tc.find("failure") is not None else tc.find("error")
        name = tc.get("classname", "").split(".")[-1] + "." + tc.get("name", "")
        if fail is None: print("PASS", name)
        else: print("FAIL", name, "::", (fail.get("message") or fail.text or "").strip().splitlines()[0][:300])
PYEOF
echo "--- journey evidence ---"
grep -h "JOURNEY\|TesseraTest: pane" "$OUT/logcat.txt" | sed 's/.*TesseraTest/TesseraTest/' | tail -12
echo "=== END SUMMARY ==="
exit "$status"

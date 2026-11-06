#!/usr/bin/env bash
# One shell for the whole emulator session (android-emulator-runner runs each
# script line in a separate process). Evidence is collected before shutdown.
set +e
OUT="$GITHUB_WORKSPACE/tessera-evidence"; mkdir -p "$OUT"
timeout 15s adb shell dumpsys webviewupdate > "$OUT/webview.txt" 2>&1
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
echo "=== END SUMMARY ==="
exit "$status"

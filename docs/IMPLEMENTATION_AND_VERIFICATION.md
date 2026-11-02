# SIXPANE — Local Android implementation report

Date: October 19, 2026 | Work scope: Replace remote-streaming implementation with local browser execution.

## What was actually authored

- A new Android Gradle source project under `android/`, without a server, remote streaming API, remote address, VPN, VPS, or proxy-rotation dependency.
- Separate AndroidX WebKit named profiles per pane (`WebViewCompat.setProfile`) with profile-backed cookies and WebStorage; runtime refuses to launch if the installed WebView provider does not support `MULTI_PROFILE`.
- Seven-workspace limit, six independent profiles per workspace, UUID profile IDs, persistent manifest, encrypted-in-transit browsing by normal HTTPS sites; no alternate IP/fingerprint spoofing.
- Closing a workspace explicitly retires and tombstones its six profiles and destroys their WebViews. New workspaces use fresh UUIDs, never reuse tombstoned profiles. Android may only permit physical profile deletion on next clean process start; failures stay queued.
- Browser views stay allocated and attached when offscreen, and code does not call `pauseTimers()` or intentionally stop hidden loads. This does NOT prove Android keeps hidden JS executing or that 42 fit in memory.
- Staggered WebView startup (130 ms per new pane) for CPU/memory admission only, not to circumvent WAF or queue controls.
- Random independent persistent viewport width/height deviations: 96%–104% (six distinct ratios per workspace) fit into aligned grid cells by differing the actual WebView bounds and applying visual transforms. Per-pane viewport sizes will still change with layout/focus resizing; test exact `innerWidth/innerHeight` on-device.
- Compact inline editable URL fields; WebView zoom +/- (10% requested increments, 50–200% allowed); per-pane desktop toggle disallowed while locked; red-glowing pane and tab locks; per-workspace Lock All; Chrome-style + after last tab; focus mode with right stack; 2x3 and 3x2 layouts; full-height cookie editor.
- Cookie editor lists cookie *names/values returned by CookieManager for the current page URL*, displays expandable rows, performs best-effort root-path edits/deletes with explicit delete confirmation, and reports when removal can't be verified. There is NO complete HttpOnly/path/expiry/domain enumerator in standard WebView. No add/import/export.
- Included controlled offline test page and Android instrumentation test source for named profile cookie isolation.

## Risks/gaps — do not claim operational readiness

1. **No APK compiled:** current tool environment has a JDK but no Android SDK/Gradle installation, and no usable network to obtain it. All Android code is source-level only, not Gradle-validated.
2. **No physical OnePlus Open test:** `MULTI_PROFILE` feature availability on the installed System WebView unknown; actual 42-WebView memory/performance unknown.
3. **No proved session continuity on Android background or hidden panes:** WebView scheduler may throttle hidden or offscreen content. Screen kept awake while Activity foreground is merely helpful, not a guarantee.
4. **Cookie editor limitation:** full DevTools-style editing by original path, domain, HttpOnly, SameSite and expiry remains unsupported; never misreport best-effort modification as full metadata access.
5. **No actual network/fingerprint independence:** Same physical device and IP; variations of 2–4% for responsive testing do not make contexts unrelated to third-party services. Canvas, GPU and user-agent fabrications are deliberately excluded.
6. **Renderer crash:** detected and reported; no silent replacement; explicit recovery UI after crash remains to be implemented.
7. **Session teardown:** profile deletion may remain pending until a clean process restart; app displays pending deletion status. No guarantee of a synchronous secure wipe.
8. **Zoom:** 10% represents requested WebView relative zoom, not a measured optical/page-scale guarantee; verify on-device and revise indicator if clamping occurs.
9. **Popup/new-window behavior, complete WebView browser parity, accessibility scaling on smallest panes and sustained 42-profile stress testing** require further implementation and real-device verification.
10. **No test for IndexedDB, service workers, simultaneous localStorage markers or time-sensitive remote queue continuity** was successfully run. Add device-side negative leakage tests before shipping.

## Android build procedure (once Android SDK + Gradle are installed)

1. Install JDK 17, Android SDK Platform 35, build-tools 35.0.0 and Gradle 8.9.
2. From `android/`: `gradle --no-daemon :app:assembleDebug`.
3. APK output expected **only after successful Gradle build**: `android/app/build/outputs/apk/debug/app-debug.apk`.
4. Install on an authorized test device: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
5. Compile/run unit tests: `gradle :app:testDebugUnitTest`.
6. Execute isolated instrumentation testing **only on a clean test install**, then clear the test package data: `gradle :app:connectedDebugAndroidTest`.

## Device verification acceptance sequence

A. Confirm `MULTI_PROFILE` supported before creating browsers; verify first two named profiles have isolated cookies, localStorage, IndexedDB, service workers and permission data, with deliberate conflicting marker values. Do not infer full isolation from cookies alone.
B. Test 1, 6, 12, 24, and 42 active local WebViews with controlled test HTML. Confirm every interval counter increases server-free during multiple workspace/layout switches and Right Stack focus, with frame/time measurements. Do not mistake created views for sustained execution.
C. Restart app without closing workspace: profile cookie markers and last URL retained. Runtime JavaScript state may restart; report accurately.
D. Lock pane and attempt desktop switch, cookie edit/delete and reset: lock shakes and action blocked. URL editing, navigation and zoom remain available while locked.
E. Lock workspace, tap ×: blocked with red lock animation; unlock, close and confirm; check retired IDs never used again. Verify cleanup status after process restart.
F. Open the cookie editor for each pane and inspect current-site results; test explicit deletion confirmation, a failure case with a non-root path cookie, and per-profile isolation.
G. Cover display, unfolded display, rotation, one-hand reach, URL keyboard and background/display-on memory stability.
H. Verify the 3x2 grid has accessible overflow and zoom controls when a pane is focused, and that 100% indicator is hidden.

## Source architecture

- `MainActivity.kt` — lifecycle, workspace UI, Chrome-style tabs, focus/right-stack, profile cleanup queue and multi-WebView allocation.
- `PaneCard.kt` — per-profile WebView, direct address bar, per-pane zoom and desktop, viewport variation, lock feedback.
- `CookieEditor.kt` — accessible current-site editor with confirmed deletion.
- `LocalStore.kt` — durable workspace and profile manifest, UUID identities, tombstones.
- `CornerIcon.kt` — white 70% opacity cookie/expand/unlocked icons; glowing red locked icon.
- `WorkspacePolicy.kt` — stable testable limits/lock/zoom policies.
- `results/policy_smoke.kt` — JDK-only policy test (passed).

**Status:** implementation source produced; not an installable or verified production app. Original remote-server source is intentionally not included in this replacement package.


## Window context and selection workflow (2026-11-02 source update)

- Browser-header long press or the browser's vertical \`⋮\` opens window actions: Lock/Unlock, Select Window, Bookmark Page, Share Page. Do **not** intercept long presses inside the WebView; website text/link menus remain intact.
- The **unlocked** browser lock icon is hidden. A **locked** browser has a small glowing red lock status in the top-left. Lock/unlock is in the context menu, not a dedicated icon tap. Workspace-tab locks remain separately visible.
- Workspace \`⋮ → Select windows\` enters selection with **zero** selected, matching disabled-action behavior. Long-press a browser-header menu and choose Select Window to begin with that pane selected. All six in-workspace panes show selection circles and stop receiving website taps while selection mode is active.
- Selection overflow has Select All, Deselect All, Lock Selected, Unlock Selected, Close Selected Windows, Bookmark Selected, Share Selected. Actions requiring an applicable selection are disabled when unavailable.
- Close Selected Windows is deliberately destructive: it refuses if **any** selected pane is locked, requires confirmation, retires old UUID profiles, and generates fresh blank profiles in the same grid slots. Physical WebKit profile deletion occurs on a later cold start; the UI must never claim synchronous disk wipe.
- Bookmarks save page URLs locally to the workspace manifest and can be reopened from the workspace menu. Share uses Android's OS sharing chooser. Neither action mixes cookies or sessions.
- Screenshot-inspired actions not copied: **Pin** overlaps with existing destructive-action lock, **Send to device** is redundant in a phone-only app, **Grouping** lacks defined behavior compatible with SIXPANE's always-six-visible requirement, and a reliable **per-WebView Mute Site** API has not been established. Do not expose nonfunctional controls.
- **Verification:** Compile and unit-test using GitHub Actions; subsequently test real long-press hit targets, zero/partial/all selection, lock blocking, close confirmation, and profile isolation on OnePlus Open. Screenshots/prototype do not count as real-device verification.

### Build and signing incident / prevention

The GitHub Actions debug APK workflow uses the runner's default debug signing key. Fresh CI runners may use different private keys, preventing an in-place update of a previously installed debug APK. **Never direct the user to uninstall an installed SIXPANE or wipe its data to resolve a signature mismatch without explicit consent and a verified backup/recovery plan.** Before a data-preserving update, provision secure persistent signing credentials and verify the signing certificate matches the installed app; otherwise disclose that in-place update is unverified.

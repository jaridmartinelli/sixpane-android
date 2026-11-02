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
- Workspace \`⋮ → Select windows\` enters selection with **zero** selected, matching disabled-action behavior. Long-press a browser-header menu and choose Select Window to begin with that pane selected. All six in-workspace panes show selection circles and stop receiving website taps while selection mode is active. Entering selection from Right Stack collapses to the full six-pane grid without destroying the underlying WebViews.
- Selection overflow has Select All, Deselect All, Lock Selected, Unlock Selected, Close Selected Windows, Bookmark Selected, Share Selected. Actions requiring an applicable selection are disabled when unavailable.
- Close Selected Windows is deliberately destructive: it refuses if **any** selected pane is locked, requires confirmation, retires old UUID profiles, and generates fresh blank profiles in the same grid slots. Physical WebKit profile deletion occurs on a later cold start; the UI must never claim synchronous disk wipe.
- Bookmarks save page URLs locally to the workspace manifest and can be reopened from the workspace menu. Share uses Android's OS sharing chooser. Neither action mixes cookies or sessions.
- Screenshot-inspired actions not copied: **Pin** overlaps with existing destructive-action lock, **Send to device** is redundant in a phone-only app, **Grouping** lacks defined behavior compatible with SIXPANE's always-six-visible requirement, and a reliable **per-WebView Mute Site** API has not been established. Do not expose nonfunctional controls.
- **Verification:** Compile and unit-test using GitHub Actions; subsequently test real long-press hit targets, zero/partial/all selection, lock blocking, close confirmation, and profile isolation on OnePlus Open. Screenshots/prototype do not count as real-device verification.

### Build and signing incident / prevention

The GitHub Actions debug APK workflow uses the runner's default debug signing key. Fresh CI runners may use different private keys, preventing an in-place update of a previously installed debug APK. **Never direct the user to uninstall an installed SIXPANE or wipe its data to resolve a signature mismatch without explicit consent and a verified backup/recovery plan.** Before a data-preserving update, provision secure persistent signing credentials and verify the signing certificate matches the installed app; otherwise disclose that in-place update is unverified.


### Verified side-by-side preview build workaround (2026-11-02)

The prior v0.2 debug APK and the first v0.3 debug APK have **different** APK Signing Block certificate SHA-256 hashes (v0.2: \`0527e14c23211786820c0835b90af6985af367120d83c2c1a7b2def077b53540\`; v0.3: \`a80f75720b09f4351846c4021b44dc6be5fddbb1321d9950bd1e64775cf44406\`). An in-place update is not possible with these signatures.

For device testing without endangering v0.2 profiles, the **debug-only** build now appends \`.preview\` to the package ID and labels the app **SIXPANE Preview**. Base/release ID remains \`com.sixpane.app\` unchanged. The preview installs as a separate, fresh app with no access to v0.2's cookie store. This **does not** establish stable signing for later preview updates, nor does it authorize uninstalling v0.2.


## 2026-11-02 — One-handed minimal browser chrome (v0.4 preview)

- Each local browser card uses a 20dp top strip with a short domain hint and a small vertical ⋮ glyph inside a 36dp transparent hit target. The always-visible URL edit field and zoom ± buttons have been removed.
- Long-press the browser strip or tap its ⋮ to open thumb-reachable bottom controls: direct URL field (with Go/IME Go), zoom −/value reset/+, pane Lock/Unlock and Select. Secondary browser actions remain under “More browser tools”.
- Website long-press gestures remain with the WebView itself; do not intercept those for app commands.
- Inter-window grid gaps are 2dp; card stroke stays 1dp with small radius. Six aligned browser surfaces and the Right Stack remain. The red lock is visible only when a browser is locked; tab locks remain unchanged.
- The Android debug preview changes its application ID to `com.sixpane.app.preview.onehand` to avoid an unsafe signature mismatch with existing debug apps. This **does not upgrade or reuse** cookies/data in v0.2 or v0.3. A stable production signing scheme must be established before shipping in-place updates.
- Verification: compile the Android debug APK + JVM tests; on device, test long press only on card chrome, direct URL submission and IME Go, zoom per pane with 100% indicator hidden, red lock blocks desktop and cookie edits, multi-select/close, and visible page area before/after. Physical touch and background WebView behavior remain unverified until device tests.

## 2026-11-02 — Session reliability update for the November 14 test

This section supersedes the earlier source-only / renderer-recovery status for this update. Baseline: main commit `6b5bf87430f11870124a09d162a928c5a2616324`. The existing Activity / PaneCard architecture is retained.

### Implemented behavior and interpretation

- A dead renderer's WebView is detached and destroyed. A pane-local handler recreates the WebView with the **same immutable `state.profile`**, assigning it before settings, clients, cookies or navigation. No replacement `PaneState`, UUID, profile migration or workspace creation occurs during recovery. The last main-frame URL, including redirects, is retained and loaded in that same profile.
- Retries wait 2, 4, 8, 16, then 30 seconds, capped at ten attempts **per recovery episode**. Each attempt has a 60-second load deadline. Main-frame network/HTTP/SSL failures during recovery retry; successful page completion clears interrupted state and resets the episode. Exhaustion remains visibly marked `Recovery stopped · 10/10`. Explicitly closed cards cancel all their callbacks. Recovery never navigates another card or reloads a healthy card.
- A renderer process can be shared by WebViews: terminating a shared process can generate callbacks for more than one card. All affected cards recover separately. The app cannot guarantee that an OS renderer kill affects exactly one WebView.
- A separate persisted `desktop-user-agent` preference defaults to ON. All panes use the same fixed desktop Chrome UA string in that mode; there are no per-pane random identities. The existing per-pane Desktop site flag still controls viewport overview fitting, still requires unlock and confirmation, and also selects desktop UA for that pane if the shared mode is OFF. This deliberately separates the shared request-UA mode from the existing per-pane viewport toggle. Changing shared UA is blocked while any pane is locked and warns that pages may reload.
- Two bulk actions target the active six panes or all **existing** panes across workspaces, up to 42. Neither creates workspaces. The address field is always blank: it never reads, clones, or suggests a live pane URL. The caller supplies the canonical entry portal. Pending allocations use their updated target URL; recovering cards retain it for their next attempt. Each WebView navigates independently in its own profile. There is no `shouldOverrideUrlLoading` filter.
- Pane labels 1–42 follow the persisted workspace order and slot order. They survive restart with that order, renumber after workspace removal, and do not add fields to the workspace manifest. The small badge sits below the header at the top-left of the browser body, away from the lock and overflow target, including narrow Right Stack cards.
- Session audit is a local, read-only, in-memory snapshot. For the entered host or URL it reads the bound profile's cookie manager, then independently evaluates `document.cookie` and Queue-it-prefixed localStorage on the current document. JavaScript reads require that document's exact origin to match the entered origin; a mismatch is reported, **never navigated**. Include a path in the entered URL to inspect cookies scoped to that path. Unavailable profiles, errors, mismatched origins and five-second JS timeouts are shown.
- Audit compares nonempty raw candidate values across **different pane numbers**, even when keys or sources differ. Reading one value from several sources within one pane is not a duplicate. Red `DUPLICATE` rows and the distinct-value total are provided. This includes all returned cookie candidates, so ordinary shared cookie values can also be flagged. It is not a complete HttpOnly enumerator or proof of an actual site's queue identity. A fresh harness exposes 84 distinct candidate values (42 qids + 42 local-only values), with zero duplicates expected.
- `PaneKeepAliveService` adds a low-importance ongoing notification and `dataSync` foreground priority while the Activity's panes exist. It does not own WebViews, does not restart after termination, and stops when the Activity is destroyed. API 33+ notification permission is requested once; denial still permits the service and is explained in-app. Battery exemption is requested with the system intent, with a workspace-menu action to request it again. Neither setting prevents all Android/OxygenOS kills.
- Android 15 imposes a six-hour background `dataSync` budget; `onTimeout(startId, fgsType)` stops the service instead of crashing or trying to bypass that limit. Reference: [Android foreground-service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout).
- `FLAG_KEEP_SCREEN_ON` remains in the Activity and is also used by the audit dialog. Additional keyboard, navigation, density, appearance and related configuration flags avoid common fold/display/keyboard recreations. Real Activity/process destruction still destroys live WebViews. No declaration can prevent every cause of recreation.
- Existing off-workspace placement stays translated off-screen and `VISIBLE`, allocated and attached. No timers are paused. Stale layout-animation callbacks are now discarded so fast workspace switches cannot pull an off-screen card back. No workspace swipe handler or WebView touch interceptor was introduced: page drags remain page gestures.
- The harness automatically writes a 128-bit random qid to cookie + localStorage, separate random local-only and IndexedDB values, a persistent heartbeat every 3 seconds, and reloads every 30 seconds. It shows visibility and reload count, asserts qid continuity, and retains detected failures rather than hiding them on a later successful reload.

### Files touched

| File | Change |
| --- | --- |
| `android/app/src/main/java/com/sixpane/app/PaneCard.kt` | Centralized first-operation profile binding; same-profile renderer recovery, bounded retry/deadline handling, recovery status, shared UA, numbering and safe navigation during backoff. |
| `android/app/src/main/java/com/sixpane/app/MainActivity.kt` | Bulk navigation, audit/menu entry points, shared-UA preference control, service/permission/battery lifecycle, numbering, stale-animation guard, safe callbacks on startup failure. |
| `android/app/src/main/java/com/sixpane/app/LocalStore.kt` | Separate persisted UA preference; existing manifest serializer and profile IDs unchanged. |
| `android/app/src/main/java/com/sixpane/app/CookieEditor.kt` | Retains the pane's bound profile cookie manager while a dialog is open, so callbacks can survive renderer replacement without accessing a destroyed WebView. |
| `android/app/src/main/java/com/sixpane/app/BrowserPolicy.kt` | Fixed UA, recovery limits, reusable address validation and cross-pane candidate comparison. |
| `android/app/src/main/java/com/sixpane/app/SessionAudit.kt` | Read-only multi-source audit with explicit availability, duplicate badges and distinct totals. |
| `android/app/src/main/java/com/sixpane/app/PaneKeepAliveService.kt` | Foreground notification, dataSync priority and Android 15 timeout handling. |
| `android/app/src/main/AndroidManifest.xml` | Foreground/notification/battery permissions, non-exported service, singleTop and expanded configuration handling. |
| `android/app/src/main/assets/isolation_test.html` | Persistent three-store endurance harness, heartbeat/reload/visibility status and sticky failure assertions. |
| `android/app/src/test/java/com/sixpane/app/BrowserPolicyTest.kt` | Recovery bounds, UA tokens, URL handling, multi-source versus cross-pane duplicate detection. |
| `results/harness_regression.cjs` | Harness logic regression with 42 simulated backing stores, simulated reload cycles and negative storage-loss cases; explicitly not Android verification. |
| `.github/workflows/sixpane-debug-apk.yml` | Runs the harness logic regression before the existing JVM-test/debug-APK build. |
| `.gitignore` | Excludes local Gradle/build outputs, machine SDK paths and Node test dependencies. |
| `results/VERIFICATION.txt` | Current build/test evidence and explicit acceptance status. |
| `docs/IMPLEMENTATION_AND_VERIFICATION.md` | This change summary, interpretations, incident record and device-verification limits. |

`WorkspacePolicy.kt`, SDK levels (min 29 / target 35 / compile 35), application IDs including `.preview.onehand`, version/signing configuration, lock/selection policies and the workspace manifest format are unchanged. No installed app was uninstalled, updated or cleared.

### Verification and remaining acceptance

Local Java 17 / Gradle 8.9 build passed `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`. Eight JVM tests passed. Lint had warnings but no errors; these include the explicitly requested battery-exemption intent, required JavaScript/database settings and feature-check analysis across helper methods. No SDK or library version was changed to obtain a pass. Hosted CI status belongs to the Actions run for the delivered commit.

The Node harness regression passed 42 simulated profiles, 60 reload cycles, 600 heartbeat ticks per profile, JS runtime replacement and negative tests for changed cookies, lost local-only storage and lost IndexedDB. Its clocks and backing stores are simulated using `fake-indexeddb`; it does not run Android or establish actual WebView isolation/timing. A separate Chromium test was unavailable because this environment returned an HTML error for the browser download. No connected Android device was found (`adb devices` was empty).

| Requested acceptance | Actual evidence in this delivery | On-device status |
| --- | --- | --- |
| 42 distinct qids, local-only values and IndexedDB values | Generator/storage/reload logic passes in 42 simulated stores; named profile binding is preserved in source. | **NOT RUN** in Android WebViews. |
| All 42 heartbeats continue while another workspace is selected | Source retains allocated, attached, VISIBLE off-screen views and no timer pause; simulated heartbeat logic passes. | **NOT RUN**; actual WebView/OxygenOS scheduling remains unknown. |
| No qid changes across 30 minutes of 30-second reloads | Sixty simulated reload cycles preserve all three values. This is not 30 minutes of real elapsed time. | **NOT RUN** for 30 real minutes. |
| Kill one renderer; same profile/qid recovers without disturbing healthy panes | Pane-local replacement factory and retry/deadline policy implemented; backoff bounds unit tested. Shared renderers may affect multiple cards. | **NOT RUN**; requires controlled renderer termination and before/after snapshots of all 42. |
| Audit reports zero duplicates | Duplicate-comparison unit tests pass, including two sources in one pane and the same value under different keys in panes 1 and 42. | **NOT RUN** against all 42 loaded WebViews; verify source availability as well as the count. |

Additional device verification needed: all 42 UAs identical with shared mode ON; canonical portal cross-domain 302 and autonomous 30-second reload; at least three hours of actual live waiting; retained values after renderer recovery; fold/unfold/rotation/keyboard without Activity replacement; page scrolling without workspace changes; numbering and header hit targets in Right Stack; per-pane desktop and lock/selection behavior; notification grant and denial; battery exemption grant and denial; foreground-service timeout and Activity teardown. Queue position restoration by the actual site is not established by this harness.

**Signing remains an existing limitation:** CI still uses its original default debug signing setup. Matching the currently installed preview's certificate has not been verified; an in-place update cannot be promised. No uninstall/data-clear workaround is authorized or used, and no signing credentials were replaced or added to the public repository.

### Incident / prevention record for this update

| Independent finding | Evidence / cause | Prevention and verification |
| --- | --- | --- |
| Local dependency resolution failed before source compilation. | Gradle could not resolve the existing `com.android.application:8.7.3` plugin; Java did not inherit this environment's HTTP proxy variables. | Passed proxy properties to the isolated build process, without changing repository dependencies or app networking; the full Android build then compiled successfully. |
| Installed SDK platform had an unexpected nested layout. | `Build properties not found` / `Failed to find target android-35`; `android.jar` was under `platforms/android-35/android-35/`. | Repaired only the scratch SDK package layout, confirmed root `android.jar` and `build.prop`, and built against unchanged API 35 successfully. |
| Initial pane-number placement could collide in narrow Right Stack headers. Caught before delivery. | Lock + number + overflow needed more width than the smallest stack header. | Moved the 20dp badge below the header. With a 60dp minimum card, its right edge is 22dp and the overflow target begins at 24dp; the lock ends above the badge. Source geometry reviewed; physical layout remains a device check. |
| Existing posted layout animations could outlive a workspace switch. | The posted runnable checked only its parent, so a later run could undo off-screen translation after a fast switch. | Cancel current animations before hiding and check a layout-generation token inside posted animations. Source path reviewed and Android compilation/lint passed; rapid physical switching remains a device check. |
| Existing cookie-editor callbacks could access the destroyed WebView during recovery. | Both its manager and URL getters dereferenced `card.webView`; a dialog already open at renderer death could run those getters during backoff. | Capture the bound profile's manager while opening on a live view and read the durable pane URL afterward. No singleton/default manager is used. Source reviewed and compiled; renderer termination while the editor is open remains an on-device check. |
| Recovery callbacks needed main-page scope checks. Caught in final review. | A stale completion from an abandoned navigation could clear recovery early, and an embedded-resource SSL error could unnecessarily restart the recovering pane. | Accept completion only for the current view URL, and keep default SSL cancellation while limiting SSL-triggered retries to the current main-page URL. No navigation or redirect is filtered. Android compilation/lint verifies integration; real callback ordering remains an on-device test. |
| The existing URL editor must retain its original parsing behavior. | Reusing the new strict `java.net.URI` input validator would reject inputs previously accepted by Android's URI parser, such as international hostnames or unescaped query spaces. | Restored the original per-pane normalizer verbatim. Strict validation is confined to the new bulk/audit inputs; ordinary navigation and redirect handling retain their prior behavior. Source comparison and Android compilation verify preservation. |

🚩G🔧 (Three independent callback findings shared a credible cause: work could continue after its view, navigation or layout state changed. The specific prevention now checks the current navigation/layout generation or retains the stable profile manager. Compilation and source review verify integration, but prevention effectiveness under actual renderer termination and rapid switching still needs device testing. No broader instruction changes are proposed.)

The unavailable Chromium download is recorded as an environment limitation, not a passed browser test. No repeated runtime failure after these preventions has been observed; actual device testing remains outstanding.

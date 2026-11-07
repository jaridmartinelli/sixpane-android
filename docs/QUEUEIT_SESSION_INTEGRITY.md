# SIXPANE — Queue-it compatibility / session-integrity verification

Last reviewed: 2026-11-06. Scope: diagnostic and testing changes only.
Canonical source: `main` (PR #5 merged in commit `48a3317d`). No change to
package ID, signing key, existing profile names, stored bookmarks or live navigation.

## Source versus externally verified facts

The supplied 2026 field-guide PDF describes JavaScript redirection, the four
integration locations (simple link, client-side, server connector, edge), response
headers, visitor-session cookies, signed `queueittoken`, potential challenge
scripts and five layers of HTTP client behavior. This document preserves those
topics while distinguishing assertions in the guide from proven configuration
of any particular Queue-it customer event.

The PDF is **not** a normative Queue-it specification. In particular, it does not
establish that every event uses all four challenges, that a token always requires
one specific cookie, that the `x-qit-visitorsessionid` *response* header is
automatically sent to another origin, or that a particular visitor-session cookie
equals a queue-position/Queue ID.

## Queue-it-related requirements and observable acceptance

| Area from guide | Required SIXPANE behavior | Evidence / limits |
| --- | --- | --- |
| JavaScript execution | Run ordinary site JavaScript in each pane. | `PaneCard` enables JS. Event behavior not tested. |
| Integrations / endpoint coverage | Navigate operator-provided canonical URLs; don't assume missing redirects prove a bypass. | WebView navigation. Site connector configuration belongs to operator, not SIXPANE. |
| Mobile/API parity | Use a normal browser path; don't probe alternate endpoints to avoid protection. | Operator responsibility, not an app feature. |
| Cookie lifecycle | Separate profile-backed cookie jars; never clear live cookies automatically. | 2-profile Android test previously passed; new 42-profile test introduced, CI result required. |
| `Queue-it-visitorsession` | Allow the browser to store/send cookies according to WebView's normal domain, path, Secure/HttpOnly/SameSite behavior. | No claim about an actual event cookie's value or scope without event evidence. |
| `queueittoken` | Follow normal redirects without stripping a site's issued query parameter. Don't forge or replay tokens. | Normal WebView navigation; authorized real-event test outstanding. |
| `QueueITAccepted-…-V3` | Keep any origin-scoped accepted-session cookie in that pane's profile. | Real origin/return continuity unverified. |
| Challenges / proof of work | Allow normal page JavaScript and user-driven challenge interactions. | WebView generally renders inline challenges; complete compatibility unverified. |
| Browser properties | Report measured, unmodified WebView properties rather than claiming manufactured fingerprints. | Existing per-pane page report; no unique-fingerprint guarantee. |
| Persistent storage | Check cookies + localStorage + IndexedDB and reconstruct WebView using same QA profile. | New emulator test uses three independent synthetic markers for 42 profiles, sequentially. |
| Background operation | Observe whether pages survive fold/background/renderer recovery. | 42 concurrent live/background sessions NOT tested by sequential CI test. |
| Server-side visitor identity | Identify Queue-it-issued values only in a permitted, authorized test; don't infer from local IDs. | Unverified. Server may associate multiple Queue IDs with one visitor. |

## New non-destructive diagnostics

- Settings > Developer tools > **Session integrity report (read-only)** provides
  declared pane/profile counts up to 42, duplicate manifest profile names, current
  WebView profile binding checks via `WebViewCompat.getProfile(...).name`,
  renderer-allocation status, and JavaScript/DOM-storage feature status.
- Report deliberately omits full URLs and query strings, cookies, session IDs,
  localStorage/IndexedDB values and tokens. It does not load, reload, navigate,
  clear, destroy, allocate or modify existing WebViews.
- An incomplete result is explicitly labelled **INCOMPLETE**, not PASS.
  A manifest/profile binding PASS does **not** mean different Queue-it visitors.
- Existing **Session audit** remains separate and read-only, for user-supplied
  URLs. It can display sensitive cookie values; do not share those raw values.

## New instrumented acceptance test

`SessionIntegrityInstrumentedTest` is emulator-only and is configured to execute
in a **disposable clean CI installation**. It allocates 42 unique *test-only*
profile names and synthetic marker values; for each, an offline asset page stores
markers in cookies, localStorage and IndexedDB. The test destroys each WebView,
recreates a WebView bound to the same test profile and requires all original
markers to be retained. A new profile must start without any prior marker.

These marker values are not Queue-it identifiers. Passing this test establishes
sequential profile isolation and same-profile WebView reconstruction on that
emulator/provider combination. It does **not** establish:
- 42 simultaneously active renderers, scheduling or battery endurance;
- persistence after OS process death / full app reinstall;
- separation of service worker and permission data;
- any cross-domain HTTP redirect or third-party cookie behavior;
- actual `Queue-it-visitorsession`, Queue ID, accepted-cookie issuance;
- Queue-it's internal server-side classification of visitors.

No test writes to the user's current app profiles or operates a live Queue-it
event. No profile names are reused during testing. CI creates synthetic profiles
only in its disposable emulator installation.

**Verified emulator result (2026-11-06):** GitHub Actions run
[42187351141](https://github.com/jaridmartinelli/sixpane-android/actions/runs/42187351141)
completed an Android instrumentation test with **1 test, 0 failures,
0 errors, 0 skipped**. XML reports 83.332 seconds for the test case.
The test code exercises 42 unique QA profiles, with first-load and
reconstruction checks. Neither the test nor this result proves 42 simultaneous
renderers or Queue-it server-issued identities.

## On-device acceptance still needed

1. Confirm all 42 existing pane WebViews can allocate on the target OnePlus Open,
   profiles bind to their declared names, and data remains separate using
   controlled local test pages. Use a **test-only installation** for writable QA.
2. Confirm cookies/localStorage/IndexedDB remain after normal reload, workspace
   switch and app process restart; record which types intentionally do not persist.
3. Confirm renderer kill/recovery restores each existing profile without rotating
   or deleting state or disturbing unrelated panes.
4. Test a normal waiting-room redirect/return/challenge sequence **only on an
   authorized Queue-it environment**. Record cookies and navigation stages,
   making no claims about unobservable server classification.
5. Inspect service-worker storage and permissions separately.
6. Record simultaneous 42-pane performance/background behavior on the OnePlus Open.

## Preservation and incident monitoring

No production profile, bookmark, cookie or queue token is migrated, cleared,
reset, exported or regenerated by this change. No spoofing, proxy rotation,
challenge automation, operator-trigger bypass or alternate API probing is added.

Source-audit discrepancy: an older implementation report claimed a desktop
Chrome user-agent override, but the current source deliberately retains the
genuine Android WebView UA. This new QA work does **not** silently reverse that
current behavior or interpret the prior report as current. Prevention: compare
current code and build checks with dated documentation before modifying UA.

CI completion and device verification must be recorded as actual observed
results; do not replace NOT VERIFIED with PASS based on simulated data.

## CI incident 1 — emulator runner working directory (2026-11-06)

Evidence: pull request run 42186716111 built the Android APK and instrumentation
sources successfully, booted the disposable emulator, then failed before tests
began with "Directory .../sixpane-android does not contain a Gradle build."
Cause: the emulator-runner treated each line of the multi-line `script:` as a
separate command. A standalone `cd` did not carry into the subsequent Gradle
command. Prevention: launch Gradle with an explicit `-p android` project
directory on the same command line; verify that a subsequent CI job reaches
actual Android test execution and publishes test-result artifacts. **Verified:**
run 42187351141 reached the Android test runner and passed the 42-profile test;
artifact `sixpane-42-profile-integrity-ci` (ID 13487734214) contains the XML.
This was a CI workflow defect, not evidence for or against cookie isolation.

## QA observation — public Queue-it demo redirect (2026-11-07)

User-reported observation: navigating to
`https://queueitcom.queue-it.net/?c=queueitcom&e=wrdemoproduct`
can immediately leave that host for `https://queue-it.com/product/`.
The observed landing URL carries `qitq`, `qitts`, and `qith` query keys,
not a `~queueittoken` parameter. These are **event-specific observations**, not
a general Queue-it token-format contract or an assertion of accepted-session
state. No parser, token handling, or challenge logic is derived from them.

For R6, `PhysicalStorageMatrixTest.kt` uses `https://example.com/` as the
stable HTTPS origin for synthetic WebView profile-storage checks. R18's future
challenge observation remains scoped to the demo and must tolerate a genuine
redirect to `queue-it.com` without treating it as a test failure. Neither
R18 nor live R13/R14 provider behavior is implemented or proven here.

## QA incident — R6 off-main-thread WebView URL reads (2026-11-07)

Physical-device evidence supplied by the user: the R6 test installs successfully,
but fails in `PhysicalStorageMatrixTest.probe()` with WebView's thread-check
exception before storage assertions, because `web.url` was read on the
instrumentation runner thread instead of the main Looper. The initial
`withLivePage()` check already wrapped URL access with `runOnMainSync`, but
`probe()` repeated the getter outside that wrapper, including its timeout
message. The initial build's success was compilation only, not runtime QA.

Prevention in the QA test: use `currentWebUrl(web)`, which obtains the URL via
`instrumentation.runOnMainSync`, at all `probe()` read sites, including
failure diagnostics. Confirm remaining `web.url` source occurrences are under
`runOnMainSync`. Physical instrumentation must be rerun; until it passes,
R6 storage isolation remains **unverified**, not failed or passed.

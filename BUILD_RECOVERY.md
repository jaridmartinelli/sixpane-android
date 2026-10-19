# SIXPANE — 3-way auto-heal and fourth-failure escalation

Source date: 2026-10-19. This is a **build/verification procedure**, not evidence that an APK has been produced.

## Policy
On a build/toolchain failure, do not quietly downgrade requirements, substitute mockups for working browsers, or present unchecked code as a finished APK. Preserve errors/logs, isolate root causes, and attempt three materially different fixes. A fourth verification failure requires a concise report showing attempts/results, the precise remaining blocker, and the **single best actionable resolution**. Avoid repeated retries against an unhealthy resource-constrained host. Do not use or configure a runtime VPS: SIXPANE's target is local Android.

## Independent recovery routes

1. **Existing local toolchain:** Check Java 17+, Android SDK 35, Gradle 8.9, free disk space and all Android dependencies. Run `cd android && gradle --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebug`. If failures are in Kotlin/Android source rather than setup, correct those issues and rerun.
2. **Repair isolated build environment:** Install the Android command-line tools, platform 35, build-tools 35.0.0 and Gradle 8.9 on a compatible x86_64 Linux machine or build container. Rerun with a clean Gradle cache if dependency resolution is corrupted. **Avoid x86-on-ARM emulation when it overloads the host.** Prefer native x86_64 runners.
3. **Independent hosted CI build:** Use the provided `.github/workflows/sixpane-debug-apk.yml` in a private repository (only after the owner authorizes that upload). GitHub Actions installs the SDK/Gradle and returns a compiled debug APK artifact or reproducible compiler errors.
4. **After another failed verification:** Report all prior outcomes, separate environmental failures from source defects, and recommend one next action. If no safe compatible compiler is available, the preferred resolution is a native x86_64 Android build runner, not a phone-installed server. Never fabricate successful build or 42-browser device performance.

## Acceptance criteria
- `:app:assembleDebug` passes and an actual `.apk` exists.
- Run `:app:testDebugUnitTest` successfully.
- Install on OnePlus Open test device and validate WebView `MULTI_PROFILE`; then run controlled cookie/localStorage/IndexedDB leakage tests.
- Test six panes per workspace up to seven workspaces, active timer progress, cookies on restart, popup/lifecycle behavior, protective locks, destructive confirmations, and URL/zoom/focus controls.
- Report separately **source created**, **compiled**, **unit tested**, **installed**, **device verified**.

## This turn's observed diagnostics
- ChatGPT working container: JDK and Kotlin compiler available; Gradle and Android SDK absent; its external DNS unavailable. No build attempted here beyond verifying missing prerequisites.
- Connected Linux `devixi-a2-vnic`: Android command-line tools downloaded; exact source Android tree transferred and SHA-256 verified (765f75863a6bbff4190c4f001fe5e1a6db16d757d52506efa05b950f2dc306eb). Docker image `gradle:8.10-jdk17` available only for x86_64 and requires emulation on ARM. The SDK package installation stalled during emulation and the host became unresponsive to subsequent operations; no successful compilation confirmed.
- Connected Termux Android: Java 21, ADB, network access; Gradle 9.8.1 and aapt2 packages available but uninstalled/unconfigured for this app. No build attempted on phone, to avoid unexpectedly downloading a large compiler toolchain to the personal device.
- No APK has been verified. Do not claim these checks constitute four completed build attempts.

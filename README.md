# SIXPANE — one canonical Android app

**Only `main` is the supported source/build.** It combines the v0.13 Filter View layout and all subsequent features.

- Debug install identity: `com.sixpane.app.preview.filterview` (unchanged from v0.13).
- App label: **SIXPANE**.
- Version: `0.13-consolidated-preview` / `versionCode 14`.
- The one installable build is named **SIXPANE-CANONICAL-APK-main** in the **SIXPANE Canonical Android APK (main)** workflow.
- Do not release APKs from feature branches, change the package suffix for convenience, or uninstall installed versions to work around signature errors.
- App updates require the **same signing key**. Distinct package IDs keep separate cookies, sessions, WebView profiles and bookmarks; no automatic migration exists.
- Local Android WebView app, no VPS or external browser runtime.

Detailed current features and evidence: `docs/IMPLEMENTATION_AND_VERIFICATION.md`.

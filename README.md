# SIXPANE — one canonical Android app

**Only `main` is the supported source/build.** It combines the v0.13 Filter View layout and all subsequent features.

- Debug install identity: `com.sixpane.app.preview.filterview` (unchanged from v0.13).
- App label: **SIXPANE**.
- Version: `0.14-consolidated-preview` / `versionCode 15`.
- The canonical signed build is **SIXPANE-CANONICAL-APK-main** in the **SIXPANE Canonical Android APK (main)** workflow; it publishes only after permanent signing secrets are configured and the signer matches the pinned certificate.
- Do not release APKs from feature branches or change the package suffix for convenience. A one-time clean install of the first permanently signed build replaces the earlier temporary-key debug installation, as approved.
- App updates require the **same signing key**. Distinct package IDs keep separate cookies, sessions, WebView profiles and bookmarks; no automatic migration exists.
- Local Android WebView app, no VPS or external browser runtime.

Detailed current features and evidence: `docs/IMPLEMENTATION_AND_VERIFICATION.md`.

**Mandatory artifact delivery policy:** See [`AGENTS.md`](AGENTS.md). No APK or other file link may be issued without actual delivery registration and retrieval checks. A successful build or local file checksum is not proof that ChatGPT can serve the link.

Permanent key fingerprint, backup locations, secure GitHub secret setup, and restore procedure: [`docs/SIGNING_AND_RECOVERY.md`](docs/SIGNING_AND_RECOVERY.md). Never regenerate the signing key for a routine update.

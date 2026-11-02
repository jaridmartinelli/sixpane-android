# SIXPANE — Phone-only Android source

This folder contains **new Android source** for the locally executing SIXPANE browser model. The source is compiled by GitHub Actions and its JVM unit tests; physical-device behavior has not yet been verified. The **debug** APK uses a separate `com.sixpane.app.preview` package ID and the **SIXPANE Preview** label to avoid overwriting previous debug installations and cookies. It does not use a VPS, browser relay, WebRTC, or external server for browser execution.

Start with `docs/IMPLEMENTATION_AND_VERIFICATION.md` for features, precise risks, build commands and test plan. Build requires a configured Android SDK/Gradle installation. The old remote-server package is superseded by this source for the agreed device-only architecture.

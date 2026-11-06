# SIXPANE permanent Android signing key: v14 and later

**Canonical debug package:** `com.sixpane.app.preview.filterview`. Do not change its suffix, version naming, or signing key merely to make Android accept an upgrade.

**Permanent signer alias:** `sixpane-canonical`.

**Pinned public certificate SHA-256:** `cced8806d97502e5a7cf950e57c2a82ece12da9c0a0b68cdaca1c03dc0238853`.

**Keystore:** PKCS#12, 4096-bit RSA, valid to March 24, 2054. The private key and password **must never be committed to source control, printed in workflow logs, placed in a pull request, or sent to another user**.

## Recoverable primary backups

- Private ChatGPT Library: `/SIXPANE/Signing/SIXPANE_CANONICAL_v14_AND_FUTURE.p12`.
- Copy-ready GitHub Actions keystore secret value, independently decoded and byte-compared to the original: `/SIXPANE/Signing/SIXPANE_CANONICAL_GITHUB_SECRET_BASE64.txt`. This encoded content is **sensitive encrypted key material**; keep it private.
- Password, separately: `/Private Recovery/SIXPANE_CANONICAL_KEY_PASSWORD.txt`.
- The November 6, 2026 backup was tested by materializing both files again, checking exact byte equivalence, and successfully loading alias `sixpane-canonical` with the expected certificate SHA-256.
- These backups are **within the same ChatGPT account**; keep an additional independent, private offline copy if continued account access cannot be guaranteed. Do not share a keystore together with its password.

## Configure repository GitHub Actions once

The connected GitHub connector does not grant access to GitHub Actions repository secrets. A repository administrator must add **two Actions secrets** at Settings → Secrets and variables → Actions → New repository secret:

1. `SIXPANE_CANONICAL_KEYSTORE_BASE64`: **the complete contents** of the prepared Library file `/SIXPANE/Signing/SIXPANE_CANONICAL_GITHUB_SECRET_BASE64.txt` (one line, no extra formatting). This has already been decoded and verified against the original `.p12`.
2. `SIXPANE_CANONICAL_KEYSTORE_PASSWORD`: the exact password from the separate private recovery file (same key and store password).

**Do not generate a second key.** The same saved keystore is required on every update. Do not paste either secret into a GitHub issue, PR, commit, or chat message.

Once configured, the main-branch workflow reconstructs the keystore in the ephemeral runner, checks that it unlocks under the expected alias, signs `:app:assembleDebug` using it, verifies the compiled package/version and pinned signer SHA-256, and publishes only if all checks pass. PR builds have no key access and are not distributable.

The version 14 APK previously created by a GitHub runner used an **unrelated temporary debug certificate**. It cannot update an installed app using that signer in place; a one-time clean installation of the first permanently signed APK is required. Future updates from that point should retain data when package ID and signature match.

## Release acceptance gate

- Build using the canonical `main` source, and preserve the package ID.
- Provision GitHub Actions secrets from the **backed-up** keystore, not a new key.
- Confirm `apksigner verify --print-certs` reports the pinned certificate fingerprint.
- Confirm v14 package version `versionCode=15` and APK is readable/integrity checked.
- Confirm in-place update of the *permanently signed* build on a real Android device before claiming it has been tested. Compilation alone does not verify that behavior.
- For ChatGPT download links, also satisfy `AGENTS.md`: a file must be registered and user-facing retrieval must be verified.

## Incident and prevention

On November 6, 2026, the canonical build failed publication because `SIXPANE_CANONICAL_CERT_SHA256` was not configured and GitHub-hosted debug keys rotated on every run. User authorized discarding existing installed data for a one-time transition to a permanent identity. Permanent key and password have now been generated and recovery tested; the workflow is changed to **require their secure provisioning** rather than silently signing with a new runner key. Actual GitHub secret configuration and a production-signed workflow run are separate pending verification checkpoints.

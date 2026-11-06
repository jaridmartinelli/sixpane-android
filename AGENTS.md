# Delivery policy — applies to every artifact produced for this project

**No unverified file links, ever.** Applies to APKs, ZIPs, PDFs, documents, images, and any other downloadable artifacts. This is a delivery gate, not a claim about the health of the build.

Before giving a user a downloadable artifact:
1. **Validate artifact:** Check the exact output file exists, is readable and nonempty; inspect format, file integrity, expected package/version/signature (APK), and required content. For transferred artifacts, compare SHA-256 to the source when possible.
2. **Validate registration:** Confirm the file is actually registered with the delivery surface visible to the user (ChatGPT conversation attachment, Library item, or externally hosted release). A path inside `/mnt/data`, a GitHub Actions build, a successful upload command, or a checksum alone is NOT delivery proof.
3. **Validate user-facing retrieval:** Independently retrieve the registered file using the same user-accessible delivery mechanism whenever that facility is available, then compare file size and checksum. Confirm the exact URL/reference resolves to the intended file. **If end-user access cannot be tested, label it unverified. Never claim client clickability was tested.**
4. **Fail closed:** Do not issue a download link when retrieval through the intended delivery mechanism is known to fail or there is no trustworthy way to establish the target exists and is retrievable. Explain what was verified versus blocked; choose another verified delivery channel when possible.
5. **On a reported broken link:** Treat it as a delivery incident. Inspect the generated-file record, attachment/Library registration, access controls, and retrieval path. Do not simply rename or reupload the same file and declare it fixed. Verify the new channel independently before attempting another link.
6. **Truthful status:** Distinguish *compiled*, *artifact verified*, *file registered*, *provider-side retrievable*, and *user-device download confirmed*. Never collapse these into “link works.” Only the last claim can rely on direct device/browser evidence.

## Incident / observed evidence (November 6, 2026)

A SIXPANE v14 APK existed in the build system and local workspace, yet the user repeatedly saw **“Library file not found”** on Android when opening ChatGPT download links. A matching APK hash and successful GitHub Actions build did not establish ChatGPT delivery. Cause within the user-facing file-resolution layer remains unverified; there is no evidence of an APK compilation failure. Prevent recurrence by applying the registration/retrieval gate above to ALL downloads, not just APKs.

This project policy is stored in the repository. It does not, by itself, configure global ChatGPT Custom Instructions or other projects.

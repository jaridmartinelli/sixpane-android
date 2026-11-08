# SIXPANE × Tessera — continuation of JM items 11–100
Date: 2026-11-08
Authority: `feature/tessera-ui-20261107`, starting at `ccf4d7f4d47424d6d7e0aae5b1414e99e38e0785`.
Source acceptance baseline: user-approved Tessera handoff, the existing `SIXPANE_TESSERA_JM_ACCEPTANCE_CHECKLIST.md` and `SIXPANE_TESSERA_JM_COMPLETE_AUDIT_2026-11-07.md` in the project Library, which contain sections 1–18 and journeys J01–J32.
**This is a progress and evidence record, NOT a claim that 90 device JM cases passed.** The earlier plain numbered 11–100 message was not present verbatim in this repository; do not recreate or renumber its items from memory.

## Authorized change boundary
UI/interaction only. No WebView engine, named-profile assignment, cookies/storage/session identity, Queue-it logic, UA/metadata, network, background service, package identity, signing, production promotion or irreversible data migration. In particular, the separately gated shared-bookmarks migration was not performed.

## Verified source edits on the same existing feature branch
- Current-workspace All/None toggles only the currently shown panes, preserving selected panes in other workspaces. A standalone policy routine has JVM regression cases.
- Selection dock refreshes when selected pane IDs or current workspace changes, even if the selected *count* does not change; JVM regression checks the signature.
- Selection overflow now opens the Workspaces screen, permitting cross-workspace selection without exiting selection mode.
- Bulk End targets all selected panes across their originating workspaces and refuses changed/missing targets; the confirmation and existing lock/profile-retirement path are retained. JVM regression checks multi-workspace detection.
- Bulk Lock/Unlock provides an Undo with a state-aware restore; no already-retired profile is resurrected by the undo closure.
- Closing a non-last workspace preserves the current workspace if it survives, otherwise selects the adjacent workspace rather than always jumping to the last. Existing retirement/tombstone logic is untouched; JVM regression checks first, middle, last, and final replacement choices.
- Closing a workspace removes its retired pane IDs from any outstanding cross-workspace selection.
- Selection entry closes the address editor/IME. Workspace creation refuses while selecting with the specified prompt. Filter clearing and successful explicit session ending use their requested feedback copy.
- The Workspaces full-screen page uses a 48dp × close button rather than a generic back chevron.

## Validation boundary
The GitHub Actions workflow for each source commit compiles/unit-tests and verifies preview package/signing gates; a successful run is source/build evidence **only**. A physical Android install was not available in this session. No pixel screenshots, tap traces, lifecycle stress tests, popup auth flows or signed in-place upgrades were executed. Device-only rows in the original 11–100 matrix remain **unchecked**.

## Remaining functional/visual gaps, not approved as complete
- Links/new-tab/sign-in popups on a locked pane: current WebView setting disables multiple windows. Requires an explicit safe same-profile popup solution and device sign-in tests (J23, section 10/13); beyond this UI-only edit slice when it changes browser engine handling.
- Background CPU/Wi-Fi, overlay/Recents, 30s JS heartbeat, all-42-liveness assurances: not implemented as handoff describes; Android may suspend or terminate live pages. No claim that the foreground service keeps all sessions continuously executing (sections 12 and 17).
- The handoff's single shared bookmarks migration is **gated**; current saved per-pane bookmarks intentionally preserved (section 15).
- Source/editor and runtime discrepancies remain in exact theme/icon geometry, workspace previews and transitions, snack/Undo coverage, popup/accessibility feedback, filter behavior, IndexedDB enumeration, before-unload and storage/status screens. Static source support is not screenshot parity (sections 2–18).
- Full acceptance requires executing J01–J32 and visual comparisons to the 27 reference screens, plus session/profile preservation, negative paths, TalkBack, rotation, IME, lifecycle and lock checks on an actual compatible Android device.

## Incident / regression prevention
1. Cross-workspace selection membership was previously treated as only an integer count in the dock, so switching workspaces or exchanging selected IDs could display stale controls. Prevention: workspace+sorted-ID presentation key with test.
2. The destructive selected-pane action previously inferred cross-workspace scope from filter mode, omitting selected panes in other workspaces. Prevention: infer from actual selected IDs and fail closed on missing targets, with test.
3. Workspace close always chose the last surviving workspace regardless of which workspace was removed. Prevention: nearest surviving selection policy with test.
These are code-inspection findings; physical-device reproductions or user incidents were not claimed.

Production `main` must not be merged from this document alone. Retain all original unverified rows as unchecked until evidence is recorded.

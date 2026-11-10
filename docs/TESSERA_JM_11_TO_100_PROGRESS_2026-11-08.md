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


---

## Full 100-item JM working index — November 8, 2026

The **canonical acceptance authority** remains the **18-section, 228-check**
`SIXPANE_TESSERA_JM_ACCEPTANCE_CHECKLIST.md` based on
`PROMPT_MATCH_TESSERA_DESIGN.md` and 27 reference JPGs in
`Tessera-screens.zip`. The 100 numbers below reproduce the **condensed working
list previously displayed in chat**, not a claim that this numbering occurs
verbatim in the original Claude handoff. Compound handoff requirements have
not been removed or waived by this working index.

Status meanings: **E** = a specific emulator view was previously observed,
not the complete journey; **T** = relevant policy/JVM test passed,
not the interaction; **I** = source implementation changed;
**U** = interaction/visual acceptance still unverified;
**P** = known incomplete requirement, mismatch or serious evidence gap.
**Zero full end-to-end JM journeys are accepted merely by compiling.**

### Core baseline (1–10)

| # | Working JM check | Evidence boundary |
|---:|---|---|
| 1 | Six panes appear without overlap | E · specific emulator screen observed; complete journey unaccepted |
| 2 | Bottom command bar accessible | E · specific emulator screen observed; complete journey unaccepted |
| 3 | Old top bar and seven-tab strip absent | E · specific emulator screen observed; complete journey unaccepted |
| 4 | Webpage touch activates correct pane | U · source support/behavior needs targeted JM evidence |
| 5 | Header tap activates pane | U · source support/behavior needs targeted JM evidence |
| 6 | Header double tap expands/collapses | U · source support/behavior needs targeted JM evidence |
| 7 | Header hold opens pane menu | U · source support/behavior needs targeted JM evidence |
| 8 | Pane chip tap expands/collapses | U · source support/behavior needs targeted JM evidence |
| 9 | Pane chip hold opens menu | U · source support/behavior needs targeted JM evidence |
| 10 | Dock actions target active pane | U · source support/behavior needs targeted JM evidence |

### Navigation, workspace selection, bulk actions (11–25)

| # | Working JM check | Evidence boundary |
|---:|---|---|
| 11 | Android Back follows priority order | U · source support/behavior needs targeted JM evidence |
| 12 | Workspace button opens workspace screen | E · specific emulator screen observed; complete journey unaccepted |
| 13 | Workspace card tap switches workspace | U · source support/behavior needs targeted JM evidence |
| 14 | Workspace card hold opens workspace menu | U · source support/behavior needs targeted JM evidence |
| 15 | Workspace button hold opens workspace menu | U · source support/behavior needs targeted JM evidence |
| 16 | Pane menu enters selection mode | U · source support/behavior needs targeted JM evidence |
| 17 | Pane tap toggles selection | U · source support/behavior needs targeted JM evidence |
| 18 | Workspaces reachable while selecting | I · source change implemented; interaction/photo QA pending |
| 19 | Cross-workspace selection retained | T · targeted JVM rule passed; UI test pending |
| 20 | All/None limited to visible page | T · targeted JVM rule passed; UI test pending |
| 21 | Selection controls fit narrow phones | U · source support/behavior needs targeted JM evidence |
| 22 | Bulk actions target selected IDs safely | T · targeted JVM rule passed; UI test pending |
| 23 | Locked panes skipped with feedback | I · source change implemented; interaction/photo QA pending |
| 24 | Controls work without filters | U · source support/behavior needs targeted JM evidence |
| 25 | Menu/dialog exits reach previous UI | I · source change implemented; interaction/photo QA pending |

### Pane geometry and gestures (26–50)

| # | Working JM check | Evidence boundary |
|---:|---|---|
| 26 | Auto 2x3 / 3x2 at 95% threshold | U · source support/behavior needs targeted JM evidence |
| 27 | Manual grid override persists | U · source support/behavior needs targeted JM evidence |
| 28 | Exact 4dp grid gap and padding | U · source support/behavior needs targeted JM evidence |
| 29 | 26dp pane header and 8dp corners | U · source support/behavior needs targeted JM evidence |
| 30 | Active pane 1.5dp white border | U · source support/behavior needs targeted JM evidence |
| 31 | Selected overlay/3dp ring/check size | P · known mismatch or missing evidence |
| 32 | Inactive pane 1dp outline | U · source support/behavior needs targeted JM evidence |
| 33 | Correct pane index, hostname and state | U · source support/behavior needs targeted JM evidence |
| 34 | Red lock badge when locked | U · source support/behavior needs targeted JM evidence |
| 35 | Desktop and non-100% zoom indicator | U · source support/behavior needs targeted JM evidence |
| 36 | Tag contrast readable | U · source support/behavior needs targeted JM evidence |
| 37 | Loading progress shown | U · source support/behavior needs targeted JM evidence |
| 38 | Empty pane Open a site action | I · source change implemented; interaction/photo QA pending |
| 39 | Page error Retry action | U · source support/behavior needs targeted JM evidence |
| 40 | Pull refresh only at top | U · source support/behavior needs targeted JM evidence |
| 41 | Nested scrolling prevents false refresh | P · known mismatch or missing evidence |
| 42 | Locked pane resists refresh | U · source support/behavior needs targeted JM evidence |
| 43 | 72dp refresh threshold | U · source support/behavior needs targeted JM evidence |
| 44 | Spinner stops after loading | U · source support/behavior needs targeted JM evidence |
| 45 | Pinch zoom, no buttons | U · source support/behavior needs targeted JM evidence |
| 46 | 320dp virtual width in small panes | U · source support/behavior needs targeted JM evidence |
| 47 | Portrait enlarged strip 78dp | U · source support/behavior needs targeted JM evidence |
| 48 | Landscape enlarged right strip | U · source support/behavior needs targeted JM evidence |
| 49 | Thumbnail switches focus | U · source support/behavior needs targeted JM evidence |
| 50 | Keyboard keeps site input and dock accessible | P · known mismatch or missing evidence |

### Bottom controls and menus (51–75)

| # | Working JM check | Evidence boundary |
|---:|---|---|
| 51 | Bottom command bar order | U · source support/behavior needs targeted JM evidence |
| 52 | Pane chip dimensions/feedback | U · source support/behavior needs targeted JM evidence |
| 53 | Address pill layout | U · source support/behavior needs targeted JM evidence |
| 54 | Host/full URL on focus | U · source support/behavior needs targeted JM evidence |
| 55 | Go/Enter submit | U · source support/behavior needs targeted JM evidence |
| 56 | URL/search resolution | U · source support/behavior needs targeted JM evidence |
| 57 | Paste and Go reads clipboard on tap | U · source support/behavior needs targeted JM evidence |
| 58 | Eight bookmark suggestions with metadata | P · known mismatch or missing evidence |
| 59 | Bookmark star turns into Stop | U · source support/behavior needs targeted JM evidence |
| 60 | Forward reflects history | U · source support/behavior needs targeted JM evidence |
| 61 | Workspace button geometry | P · known mismatch or missing evidence |
| 62 | More button includes All panes | E · specific emulator screen observed; complete journey unaccepted |
| 63 | Pane menu row order and inline controls | I · source change implemented; interaction/photo QA pending |
| 64 | All panes actions reach correct screens | U · source support/behavior needs targeted JM evidence |
| 65 | Full-screen menu page composition | I · source change implemented; interaction/photo QA pending |
| 66 | Disabled row reasons and feedback | U · source support/behavior needs targeted JM evidence |
| 67 | Blocked lock action haptic/shake/snack | P · known mismatch or missing evidence |
| 68 | Exactly six Tessera palette choices | P · known mismatch or missing evidence |
| 69 | Colour swatch ring/contrast | I · source change implemented; interaction/photo QA pending |
| 70 | Lock and unlock state | U · source support/behavior needs targeted JM evidence |
| 71 | Desktop site switch and lock restrictions | I · source change implemented; interaction/photo QA pending |
| 72 | Locked cookie editor view-only | U · source support/behavior needs targeted JM evidence |
| 73 | End session confirmation, target checks | I · source change implemented; interaction/photo QA pending |
| 74 | Selection controls refresh with membership | T · targeted JVM rule passed; UI test pending |
| 75 | Selection overflow actions navigable | I · source change implemented; interaction/photo QA pending |

### Workspaces, filters, acceptance (76–100)

| # | Working JM check | Evidence boundary |
|---:|---|---|
| 76 | Workspaces title/count/close | I · source change implemented; interaction/photo QA pending |
| 77 | Workspace cards responsive 2/3 columns | U · source support/behavior needs targeted JM evidence |
| 78 | Current workspace border | U · source support/behavior needs targeted JM evidence |
| 79 | Workspace card header/count/lock controls | I · source change implemented; interaction/photo QA pending |
| 80 | Six live preview tiles and fallback | P · known mismatch or missing evidence |
| 81 | Preview tiles dimensions | U · source support/behavior needs targeted JM evidence |
| 82 | Workspace lock/unlock and close | U · source support/behavior needs targeted JM evidence |
| 83 | Create right, adjacent close behavior | T · targeted JVM rule passed; UI test pending |
| 84 | Seven workspace maximum | U · source support/behavior needs targeted JM evidence |
| 85 | New workspace disabled correctly while filtering | P · known mismatch or missing evidence |
| 86 | Workspace menu actions | U · source support/behavior needs targeted JM evidence |
| 87 | Close workspace safely replaces state | I · source change implemented; interaction/photo QA pending |
| 88 | Multi-colour filtering | U · source support/behavior needs targeted JM evidence |
| 89 | URL substring filter live | I · source change implemented; interaction/photo QA pending |
| 90 | Filter chips, names and count | P · known mismatch or missing evidence |
| 91 | No matches offers Clear | U · source support/behavior needs targeted JM evidence |
| 92 | Theme colours parity | P · known mismatch or missing evidence |
| 93 | Icon sizes / tap targets | P · known mismatch or missing evidence |
| 94 | Snackbar above dock/keyboard | P · known mismatch or missing evidence |
| 95 | Undo for lock, colour, bookmark | P · known mismatch or missing evidence |
| 96 | Confirmation words/consequences | I · source change implemented; interaction/photo QA pending |
| 97 | Motion timings and reduced motion | P · known mismatch or missing evidence |
| 98 | Rotation/narrow/text-scale layouts | P · known mismatch or missing evidence |
| 99 | TalkBack actions and labels | P · known mismatch or missing evidence |
| 100 | Final full JM plus 27-image layout parity | P · known mismatch or missing evidence |

### High-priority findings from the current source and image audit

1. **Session safety is higher priority than styling.** Preserve unique
   WebView profiles and never reuse retired names. Screen filter pages must
   show the actual matching panes across source workspaces, not just the
   current source workspace; preserve their original memberships and cookies.
2. **Menus layout rather than art direction.** The pane menu now has inline
   none+six colour controls and a desktop toggle in the first seven actions,
   without deleting legacy saved tag values. Workspace-card lock and close
   actions now sit before their 2×3 preview, consistent with handoff images.
3. **Selection and destructive actions.** Cross-workspace membership persists
   while opening menus and changing filters. Bulk actions plan eligible,
   locked and missing IDs, and fail closed for stale selections. A fresh pane
   gets a fresh profile; UI selection/focus follows the corresponding slot.
4. **Photo parity not yet accepted.** Menus need actual clean emulator
   screenshots after each code change; unmatched layout states remain open.
   The existing reference set has images of the pane menu, All panes menu,
   workspace screen, selection menu, colour picker, filter, cookies and settings.
5. **Unapproved or infeasible promises remain open.** The handoff's shared
   bookmarks migration is explicitly approval-gated; current per-pane
   bookmarks are preserved. Background all-42 WebView continuous execution,
   fake visibility and Recents overlay behavior cannot be promised by the
   observed Android implementation. Locked sign-in popups/new windows and
   `beforeunload` remain unresolved and need separate safe WebView QA.
6. **Zero unsupported 'all 100 fixed' claim.** The valid acceptance
   requirement is source + automated regression + emulator journey + image
   comparison for every applicable interaction, with real-device performance
   and session upgrade checks reserved for later release acceptance.

### Incidents found and preventive checks

- Previously the selection state was cleared by a second menu entry and by
  filter changes. Fixed by preserving selected IDs, with JVM tests.
- Previously a 6-slot filter page rendered `store.active().panes` even when
  matched panes came from other source workspaces. Corrected the visible
  set for layout/interaction; a disposable-emulator test was added.
- Previously the pane menu required a second colour dialog and workspace
  close/lock controls appeared under the previews. Reorganized those shared
  menu structures and added layout instrumentation.
- A manual source edit temporarily failed CI because a TextView's own
  `text` property shadowed the chrome colour identifier. The ambiguity
  was corrected by using `this@TesseraBar.text`. Later source passes must
  be checked before calling the change tested.

No changes from this record promote the feature branch to `main`, install
on the user's phone, erase live sessions, or approve a bookmarks migration.


## Source-linked 18-section release gate (current feature branch)

The following is a **conservative source audit** tied to the canonical
18-section handoff. **PARTIAL** means both implemented and unverified/missing
atomic clauses remain. The working 100 checks above **do not replace** the
228 atomic acceptance checks or 32 JM journeys. File line ranges below
refer to the current feature-branch source after the menu/selection changes.

| Handoff section | Status | Current source anchors | Outstanding JM requirement |
|---|---|---|---|
| 1. Screen structure | PARTIAL | MainActivity.kt:108–160; 980–1075 | Grid/dock source present; exact insets and screenshot states remain unaccepted |
| 2. Theme tokens | PARTIAL | TesseraBar.kt:38–58; FramePalette.kt:6–26 | Legacy saved colours preserved; exact all-screen visual style not retested |
| 3. Command bar | PARTIAL | TesseraBar.kt:60–368; MainActivity.kt:1077–1086 | Core dock/back policy present; suggestion metadata and complete gesture checks open |
| 4. Pane and overlays | PARTIAL | PaneCard.kt:206–293; 638–770 | Open a site now distinct; touch/accessibility/lock and nested refresh need QA |
| 5. Enlarged pane | PARTIAL | PaneLayout.kt:1–58; MainActivity.kt:980–1075 | Rectangles and resize exist; screenshot and thumbnail interactions pending |
| 6. Full-screen menus | PARTIAL | TesseraBar.kt:371–632; 784–834 | Inline palette, desktop switch, row order changed; emulator/photo pass pending |
| 7. Workspaces | PARTIAL | TesseraBar.kt:633–783; LocalStore.kt:338–375 | Card controls now in header; preview live/fallback and layout need screenshot QA |
| 8. Multi-select | PARTIAL | MainActivity.kt:575–626; 701–756; TesseraBar.kt:183–229; 784–834 | Selected IDs persist, bulk skips locks; end-to-end emulator checks pending |
| 9. Filters | PARTIAL | MainActivity.kt:220–265; 887–899; 980–1068; TesseraBar.kt:835–868 | Cross-workspace result geometry corrected; virtual page behavior needs confirmed regression |
| 10. Links/popups/keyboard | NOT MET | PaneCard.kt:322–475; MainActivity.kt:980–999 | Locked new-tab/sign-in popup still disabled; IME and intent behavior need tests |
| 11. Feedback/motion | PARTIAL | TesseraMotion.kt:1–22; TesseraBar.kt:969–991 | Snack/undo exist; keyboard, modal and reduced-animation parity unverified |
| 12. Settings | NOT MET | TesseraBar.kt:869–920; PaneKeepAliveService.kt:1–41 | Specified wake/Wi-Fi/overlay switches and platform behaviors absent |
| 13. Lock behavior | PARTIAL | PaneCard.kt:445–486; 686–765; TesseraBar.kt:559–632 | Lock guards and disable explanations exist; new-tab popup missing |
| 14. Other screens | PARTIAL | CookieEditor.kt:1–328; SessionIntegrityReport.kt:1–130; TesseraBar.kt:920–968 | IndexDB names/health test/unsupported-screen parity incomplete |
| 15. Saved data rules | NOT MET | LocalStore.kt:133–375; MainActivity.kt:537–575; 701–756 | Fresh profiles and focus remap present; shared bookmarks migration explicitly gated |
| 16. Browser behavior | PARTIAL | PaneCard.kt:55–201; 304–475 | Many browser callbacks present; beforeunload and heartbeat incomplete |
| 17. Background/notifications | NOT MET | PaneKeepAliveService.kt:1–41; MainActivity.kt:1088–1136 | No credible all-42 live/visible guarantees; overlay/restore behavior absent |
| 18. Small interface rules | PARTIAL | TesseraBar.kt:183–368; 969–991; MainActivity.kt:575–626 | Selection, snacks and chips present; exact all-state layout/copy still unverified |

**Source changes pass the latest completed debug build and JVM unit suite;
individual UI actions are not automatically passed by compilation.**
Keep production promotion, session migration and user-device testing gated
until required evidence has been collected.


## Corrected JM filter authority — preserve source slots by default

**Detected contradiction (source vs handoff):** The prior code/display
implementation and progress note treated repacking matching panes from multiple
workspaces into virtual six-slot pages as the default "fixed" filter behavior.
That was incompatible with canonical handoff section 9, line 121, which says
non-matching panes fade **in their own slots** and other panes **never move or
grow**. Passing a test for repacked matches did not establish compliance with
the handoff. The earlier interpretation was an implementation error.

**Correction:** The default filter now keeps all six panes in the current
original workspace grid, fading non-matches to 16% opacity, without removing
or reassigning panes. The Workspaces page only lists workspaces containing
matches while a filter is active. The existing condensed cross-workspace
paging capability remains available as a separate reversible **Settings →
Condensed filter results** switch, default off. The original WebView profile
IDs, lock state and saved manifest format remain unchanged. The selection
All/None rule acts on matching panes when a filter is active. Changes are in
`MainActivity.kt`, `TesseraBar.kt` and a separate SharedPreferences key in
`LocalStore.kt`.

**Prevention:** Separate emulator tests check that default filter leaves
each source pane's grid coordinates unchanged and dims non-matches, and that
the opt-in condensed mode still displays matches from different source
workspaces while retaining profile IDs. The Filter chips test additionally
checks colour-dot/name/removal layout and touch size. Passing compilation
alone does not close either acceptance test.

### Current shared-layout and interaction corrections

- Workspace screen: full-screen ✕ is at top right; lock/close controls stay in
  the card header before the 2×3 preview; blank/still-loading previews show
  labelled placeholders instead of misleading white WebView screenshots.
- Workspace footer: when New workspace cannot be used (7 limit, active filter,
  or selection), the button is visibly dimmed and has an on-screen reason below
  it, while denied clicks give the same explanation and preserve state.
- Pane and selection: compact pane thumbnails are clickable even with loaded
  websites; they change expanded focus without forwarding clicks to the
  webpage. Pull-to-refresh refuses stale asynchronous JavaScript results from
  earlier gestures and cancels its permission on pinch/up/cancel.
- Accessibility: dock controls and selection actions have meaningful screen-
  reader labels; selection check circle is 26dp; filter chips have readable
  remove actions and minimum 44dp-high targets.

Any 100-point or 18-section "Done" designation still requires the relevant
runtime checks plus reference-layout comparisons. Do not equate new source
changes with all 27 image states passing.


---

## November 9, 2026 — emulator-only JM acceptance and repairs

**Updated acceptance authority:** The user expressly accepts Android emulator
validation for the 100-item JM development checklist. A physical-device run is
not a prerequisite for marking an item complete **for now**. To accept one item,
its actual specified UI interaction, outcome, applicable negative case, preserved
browser identity, and reference menu layout must pass in emulator evidence.
JVM-only tests, source review, compilation, and screenshot capture without
interaction checks are supporting evidence, not an automatic acceptance.

**Production remains protected.** The work targets
`feature/tessera-ui-20261107` and the isolated
`com.sixpane.app.preview.filterview` Android package on ephemeral CI emulators.
Do not merge to `main`, migrate real saved data, swap the canonical signing
identity, change the named profile setup, or claim all 100 completed.

### Executed regression runs and coverage boundary

- Baseline `58da194358451ddf175b92d473466aa25d5303ce`;
  emulator workflow [42508424835](https://github.com/jaridmartinelli/sixpane-android/actions/runs/42508424835)
  executed 14 instrumentation cases: **11 passed, 3 failed**. Failures:
  row-position expectation for filtered panes; missing Blue filter chip in
  accessibility hierarchy; script-opened popup did not navigate original pane
  to exact URL.
- Repair `17c8507fe46075f1e15c5ca3d9da73e55cb19dea`;
  emulator workflow [42588158073](https://github.com/jaridmartinelli/sixpane-android/actions/runs/42588158073)
  executed 14 cases: **12 passed, 2 failed**. Fixed the incorrect test
  assumption about absolute row Y when adding a filter bar; the two remaining
  failures were missing Blue filter chip and popup URL.
- Repair `b5a8801784cd8f5ee3d1857c7dde95b6e9a3ed56`;
  emulator workflow [42589160344](https://github.com/jaridmartinelli/sixpane-android/actions/runs/42589160344)
  executed 16 cases: **15 passed, 1 failed**. Exact popup navigation, outlined
  numbered workspace button and its size, distinct no-match Clear button,
  filters' original-pane slot ordering, and other covered flows passed. The
  Blue filter chip still was not discoverable.
- Fourth attempt `8dda29bc78ac496b971f961bb2b1ddfdc1fc8ed9`;
  emulator workflow [42590314997](https://github.com/jaridmartinelli/sixpane-android/actions/runs/42590314997)
  executed **16 cases: 16 passed, 0 failed, 0 skipped** (test report
  `TEST-emulator-5554 - 15-_app-.xml`). This validates the filter-chip
  accessibility and removal regression against the emulated revision.
  The source change avoids repeatedly recreating the same chip nodes during
  WebView-driven dock refreshes. New diagnostics remain in the test for
  future recurrence. This is not a 100/100 JM acceptance result.

**Repeated incident — filter chip:** Missing Blue filter-chip accessibility
node recurred in three independent CI attempts after incremental fixes. The
first two attempts addressed view refresh and geometry, but did not prevent
recurrence. The latest preventive theory is that unconditional removal and
recreation of chips during asynchronous WebView-driven dock renders destabilized
the hierarchy. The fourth test has now passed, providing prevention verification on
that emulated revision. Keep the repeatable test and its diagnostic path, rather
than relying only on a static code review; broad real-world durability remains
untested.

**Known acceptance gates / limitations:**

- **Resolved November 9, 2026 — user-approved decision:** Keep bookmarks
  strictly separate for each pane/session. Reject and skip the Tessera handoff's
  proposed single shared bookmark list and merge/migration. Do not change or
  erase existing per-pane bookmarks. This is a superseded requirement, not a
  failed implementation item; exclude the shared-bookmark migration from the
  remaining acceptance count. Verify that bookmark isolation persists across
  workspace changes, app restarts and session retirement as applicable.
- The Android 14+/15 lifecycle does not guarantee all 42 WebViews remain
  executing and visible after screen-off, Recents removal or process death;
  never spoof browser/page visibility or promise impossible guarantees.
  Any replacement for the handoff's absolute background-speed promise requires
  separately approved technical acceptance semantics.
- **Corrected November 9:** `Tessera-screens.zip` was located in the
  user's persistent ChatGPT Library; its archive contains **27 JPG reference
  images** (verified ZIP structure). The archive is not checked into GitHub,
  and manual side-by-side inspection has not established exact menu parity for
  all 27 states. Earlier wording that the references were unavailable was
  outdated.
- The condensed 100-item index remains the working list; the original
  228-check/18-section handoff is retained. Tests establish only their actual
  assertions, never every clause of an entire compound 100 item.

---

## November 9, 2026 continuation — tested UI journeys and bookmark isolation

The authoritative user decision is to **keep per-pane bookmarks**. No shared
bookmark list, metadata migration, or cross-pane merge is authorized. The
per-pane `PaneState.bookmarks` lists remain unchanged as the live source of
bookmark suggestions and changes; fresh sessions do not inherit old bookmarks.

### Emulator evidence

- Commit `7fa1e1e58f65626ca269e1f18c2b0fe8e412b9f6`: Android 15
  disposable emulator [run 42703274657](https://github.com/jaridmartinelli/sixpane-android/actions/runs/42703274657)
  executed **27 tests: 27 passed, 0 failed, 0 skipped**. Canonical preview
  CI also succeeded. These tests exercise UI gestures, selection, workspace
  controls, filter visibility, cross-workspace pane slots, popup navigation,
  exact address entry with Enter, snackbars above an open IME, per-pane bookmark
  storage/recreation, and state-aware Undo.
- Previously source-implemented but now emulator-verified behaviors include:
  distinct bookmark suggestions/removal without affecting another pane,
  cross-workspace selection persistence, All/None controls, locked-pane
  skip/end confirmation, workspace header controls, native dock and pane-header
  gestures, popup navigation, and labelled filter-chip removal.
- Scope: each case passes its explicit assertions **only**. This is not a
  blanket acceptance of 27 checklist rows, all 32 end-to-end journeys, or
  all 228 canonical atomic assertions. Exact 27-image layout parity remains
  open, despite the recovered references.

### Regressions, causes and prevention

1. **Address-field automation** failed repeatedly as UiAutomator's EditText
   accessibility node became stale or unavailable during IME/suggestion popup
   transitions. Production focus remained native; an emulator check now
   confirms focused EditText content, sends real Android text-input events and
   presses Enter. It passed in run 42703274657. Do not restore stale
   accessibility-node reuse in this journey.
2. **Cross-workspace filter fixture race**: navigating local asset pages
   during a newly allocated about:blank WebView could cause a late callback to
   remove a filter match from the fixture. The test now waits for both local
   URLs to settle before enabling condensed result mode; passed in run
   42703274657. No browser-profile or filter-policy change was needed.
3. **Undo safety**: Bookmark, pane-lock and multi-pane recolour callbacks now
   verify that the expected action is still current. Undo must never reverse
   an intervening user edit or write to a retired pane. Emulator lock and
   colour regressions passed. Bookmark Undo still needs its own dedicated
   user-level action/negative-case regression.
4. **Snackbar keyboard overlap**: the fixed gravity-bottom positioning was
   replaced by a dock-relative placement updated on layout changes. Emulator
   verified the Undo snackbar stays above the IME-lifted dock.

### Latest additional slice

Commit `e6dd2d18896215f038e4e9034d4b4ec269d9f2ed` adds an
unsupported-WebView recovery screen without falling back to shared cookie
storage, plus emulator checks for fresh-session bookmark separation, seven
workspaces and 42 distinct profile IDs, and the persistence/actual geometry
of manual grid settings. Emulator [run 42744305404](https://github.com/jaridmartinelli/sixpane-android/actions/runs/42744305404)
finished **31 passed, 0 failed, 0 skipped** (Android API 35; test suite
`com.sixpane.app.ProfileIsolationInstrumentedTest`). Canonical preview CI
[run 42744305401](https://github.com/jaridmartinelli/sixpane-android/actions/runs/42744305401)
also passed. These are the latest validated source and test artifacts.

New checks verified that retiring a pane replaces its profile without copying
bookmarks and does not affect its neighbor's bookmarks, all seven workspaces
hold 42 unique IDs and profile names without allowing an eighth, a manual
2×3/3×2 override changes actual pane positions and persists across activity
recreation, and unsupported named-profile WebView behavior refuses to start
with an accessible recovery action. These outcomes are evidence for the
individual asserted requirements, not automatic acceptance of full compound
JM items or all 27 reference layouts. No production promotion was performed.

### November 9 — cookie-editor layout and accurate background guidance

Two user-supplied reference states were revisited after the 31/31 emulator run:

- **Cookie editor** (`tour-14-cookie-editor.jpg`): Site actions belong
  immediately after the cookie list/empty state and before the storage details,
  not fixed at the foot of the screen. Edit/Delete controls belong only to
  an actual cookie selection. A locked pane must be view-only and must not
  expose cookie mutation controls. Implemented in the feature branch;
  `TesseraJourneysInstrumentedTest` now checks visible action availability
  and vertical ordering in the emulator.
- **Welcome screen** (`tour-14-welcome-page.jpg`): The prior caption
  `So sessions never drop` promised behavior Android cannot guarantee.
  Replaced with `Help keep sessions available` while retaining the
  notification/battery-permission choices. A first-launch emulator assertion
  guards this safety-critical copy.
- **Bookmark Undo**: The new emulator journey adds a bookmark in Pane 1,
  checks Pane 2 is unaffected, undoes the change, then exercises an
  intervening edit to ensure stale Undo does not overwrite the newer state.

The follow-up revision is `08933a05fdcbaea3499bb0d095c30892660c7023`.
Its CI preview run is [42746200242](https://github.com/jaridmartinelli/sixpane-android/actions/runs/42746200242).
Its emulator run is [42746200415](https://github.com/jaridmartinelli/sixpane-android/actions/runs/42746200415).
The emulator artifact's JUnit XML confirms **34 tests passed, 0 failed,
0 errors, 0 skipped**, on Android API 35. The canonical preview build passed
as well. Verified additions: a locked cookie editor hides mutation controls,
an unlocked editor shows site actions after the list and before storage info,
bookmark Undo is pane-scoped and ignores newer changes, and first-launch copy
does not promise continuous background execution.
This is still not a 100/100 end-to-end JM acceptance or complete 27-screen
layout sign-off.


---

## November 10, 2026 — approved three-menu cleanup; cookie editor excluded

User decisions for this revision:
- **Do not touch cookie editing.** The currently approved cookie editor remains
  untouched; no controls, data, or logic in `CookieEditor.kt` are modified.
- **Settings:** remove the redundant "Developer tools" entry opening a second
  Settings dialog. Keep canonical per-pane "Copy diagnostics" and All panes
  "Check isolation & health". When a retired profile has pending cleanup,
  show the required "Finish erasing ended sessions (N waiting)" status and
  explain that a clean restart retries deletion, without attempting to remove
  a live WebView's profile.
- **Workspace menu:** show "Lock all 6 panes" unless all six are locked;
  show "Unlock all panes" only when at least one pane is locked. Preserve
  New workspace and Close workspace with their existing validation/lock gates.
- **Open an address in selected:** replace the small input card with a
  full-screen Tessera page, 52dp-height Cancel/Open footer controls and a
  validated address/search input. Bulk action keeps the existing behavior of
  skipping locked panes and preserving profile IDs.

Emulator acceptance additions:
`TesseraApprovedMenuChangesInstrumentedTest` checks the absence of duplicate
Settings navigation and presence of canonical diagnostics, conditional
workspace menu row inventory across unlocked, locked and mixed states,
full-screen address entry/Cancel/empty validation/touch sizes, and that bulk
Open skips locked panes without replacing browser profiles.

This is a narrowly scoped feature-branch change, **not** permission to remove
any additional controls or edit cookie functionality. Full 100-item JM signoff
still requires separate evidence. New CI results must be recorded after
execution rather than assumed from source changes.


### Emulator regression: explicit address target must not be replaced by prefix

On the November 10 approved-menu revision
`c8cca1cbb51a151cb002ebb9fbe0a267e12afd0b`, emulator run
[42813905420](https://github.com/jaridmartinelli/sixpane-android/actions/runs/42813905420)
completed **43/44 passed; one failed**:
`bulkAddressSkipsLockedPanesAndPreservesProfiles` did not observe the full
explicit URL in the unlocked target pane. Build and JVM checks passed.
This is a user-visible end-to-end bulk-navigation failure, not an excuse to
remove or relax its acceptance test.

Confirmed source path: bulk Open called `PaneCard.loadAddress(resolved)`,
which defaults to `BrowserPolicy.entryCandidates` and can try shallow root
URLs before the exact requested destination. That behavior is inappropriate
for an explicit multi-pane Open action. The scoped prevention is to call
`loadAddress(resolved, exact=true)` **for bulk Open only**, preserving normal
single-pane navigation and browser/session policies. The emulator assertion
now includes the saved and current WebView URLs so any recurrence can be
diagnosed. The next run must verify both the exact target and skipping locked
panes before this item is accepted. No cookie-editor changes are authorized.

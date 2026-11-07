# Prompt: rebuild SIXPANE's UI to match the Tessera design

Paste everything below the line into ChatGPT.

---

You are working on my Android app SIXPANE (native Kotlin, WebView, 7 workspaces × 6 panes, each pane its own WebView profile). Redesign its controls, menus, flow and visual design to match the specification below **exactly**: same elements, placement, sizes (dp/sp), colours, behaviour and timings.

## Rules
- Keep everything under the hood unchanged: package ID, signing key, profile-per-pane isolation, saved workspaces, cookies, keep-alive service, crash recovery. This is a UI/flow change only. Existing sessions must survive the update.
- Where this spec removes a SIXPANE control, remove it. Where SIXPANE has a feature not in this spec, list it for me before deleting it.
- Do not add per-session fingerprint or identity spoofing of any kind.
- After building: list each numbered section (1–18) as done / partly done / not done, with the file and line numbers. Every row, snack message and data rule listed counts; nothing is optional unless I approve dropping it.

## 1. Screen structure
- Edge-to-edge dark UI. From top to bottom: the **pane grid**, then **filter chips** (shown only while a filter is on), then the **command bar**.
- **Remove** the top universal bar and the bottom row of 7 workspace tabs. There are no controls above the panes.
- Grid gaps: 4dp. Grid outer padding: 4dp plus the system-bar insets.
- Layout: automatic. 3 columns × 2 rows when the area is at least as wide as 95% of its height, otherwise 2 × 3. Settings can force either.

## 2. Theme tokens
- Background `#0B0E13` · surface `#141920` · surface2 `#1C222B` · surface3 `#252C37` · stroke `#2B3340`
- Text `#EEF2F7` · secondary text 70% white `#B3FFFFFF` · tertiary text `#73FFFFFF`
- Accent `#7EA6FF` (primary buttons only) · lock red `#FF4D57` · danger `#FF6B6B`
- **Tag colours: exactly six**, in this order: Blue `#204DDA`, Green `#59B435`, Red `#B5381C`, Gold `#C39533`, Olive `#9A9D32`, Teal `#7CC5B4`. Text on a tag colour is white or `#0B0E13`, whichever has the higher contrast ratio.
- **Web pages:** the WebView background is **white**, so unstyled pages read as black on white.
- **Icons:** 22dp glyphs inside touch targets of at least 44dp, with a rounded ripple. Secondary icons are 70% white.

## 3. Command bar (bottom; acts on the ACTIVE pane)
- Surface `#141920`, padding 8/6/4/6dp. A 2dp white progress line along its top edge while the active pane loads.

Left to right:
1. **Pane chip**, 44×44dp, radius 12dp, 1dp white border, filled with the pane's tag colour (surface3 if none).
   - Contents: the pane number (14sp bold) and an 11dp expand/collapse glyph in the bottom-right corner.
   - Tap: enlarge/shrink the active pane. Hold: open the pane menu.
2. **Address field**, height 44dp, takes the remaining width, radius 22dp, surface2 with a 1dp stroke.
   - Leading 16dp icon: a search glyph, or a red lock when the pane is locked.
   - The text field (15sp) shows the site host. Focusing it shows the full URL, all selected.
   - The keyboard's Go key and hardware Enter submit. Search words go to `https://www.google.com/search?q=`; anything address-like opens as a URL (https; http for localhost and IP addresses).
   - At the right end, inside the pill: a 40dp **☆** that bookmarks or unbookmarks the page. While loading it becomes **✕ (stop)**.
   - While the field is focused, a panel appears just above the bar with a "Paste & go" chip (it only checks the clipboard description; the text is read when tapped) and up to 8 bookmarks (title, host and a remove ✕).
3. **Forward →**, 44dp, 30% opacity when there's nothing to go forward to.
4. **Workspace button**, 44dp target, containing a 24×24dp square with a 2dp outline and the current workspace number (12.5sp bold).
   - Tap: open the workspace screen. Hold: menu for the current workspace.
5. **⋮**, 44dp: opens the pane menu for the active pane, plus the "All panes" section.

**No back, reload or zoom buttons:**
- **Back** is Android's back gesture, in this order: clear the address field focus → exit selection → page back → shrink the enlarged pane → `moveTaskToBack` (never finish the activity).
- **Reload** is pull-to-refresh. It starts mid-drag only when the page's own scroll is at the top **and** no scrolled-down element is under the finger (check with an `elementFromPoint` probe on touch-down). It never fires on a locked pane. Trigger distance: 72dp of drag at 0.5× resistance. Indicator: a 36dp white circle with a reload icon that spins until loading ends.
- **Zoom** is pinch only.

## 4. Pane
- Corner radius 8dp. A **26dp header strip** sits on top of the web content.
- **Header:**
  - Background: the tag colour; if none, surface3 when active and surface2 otherwise.
  - Contents: number chip (min 20×18dp, radius 5dp, 11sp bold) · red lock badge 18dp on a dark rounded backing (only when locked) · site host (11.5sp, single line) · small badge showing "Desktop" (and zoom %, if not 100).
  - A 2dp progress line along the header's bottom edge while loading.
- **Header gestures (the only app gestures on a pane):**
  - Tap: make the pane active (selecting mode: toggle selection).
  - Double-tap: enlarge or shrink.
  - Hold: pane menu, with haptic feedback.
  - Touching the page itself also makes the pane active, without consuming the touch. Never attach long-press handlers to the WebView.
- **Outlines:** active = white, 1.5dp. Selected = white, 3dp, a 16% white tint over the content, and a 26dp white circle with a check in the top-right. Default = 1dp stroke. Never use a tag colour for an outline.
- **Overlays:**
  - Empty pane: "New session" text plus an **Open a site** button, which focuses the address field.
  - Load error: a card with an icon, "Can't open this page", the reason and a **Retry** button.
  - Renderer crash: "Page crashed — restoring this session…", then automatic recovery on the same profile.
- **Small panes:** when a pane is narrower than 320dp, render the web page at 320dp width and scale it down to fit (Settings toggle "Full-size pages in small panes", default on).
- **Accessibility:** each pane exposes the actions "Make active", "Expand or shrink" and "Pane menu".

## 5. Enlarged pane
- The pane takes the main area.
- **Tall area** (height ≥ 1.1 × width): the other 5 panes sit in a **78dp** strip along the bottom. **Wide area:** a right-hand column ~101dp wide (78dp × 1.3), at most a third of the width.
- Tapping a small pane enlarges it instead. Animation: a 280ms resize with the curve (0.2, 0, 0, 1).

## 6. Menus: full-screen pages
Every menu is a **full-screen page**, except yes/no confirmations and quick pickers, which are compact centred cards.

**Page structure:**
- **Top:** a 48dp back arrow, a title (21sp bold) and an optional subtitle (13sp, 70% white).
- **Middle:** scrolling rows, each at least 52dp tall: a 22dp icon, a label (15sp) and an optional detail line (12sp).
- **Bottom:** action buttons docked at the bottom, sharing the width equally.

**Card structure:** width 92% of the screen, at most 440dp; radius 22dp; 55% dim behind.

**Disabled rows** stay visible at 45% opacity. Their detail line, in red, says why they're unavailable. Tapping one repeats the reason, and for lock-related reasons also shakes the pane's lock.

**Pane menu** (title "Pane N · Workspace W", subtitle = page title and host):
1. Colour tag row: a ✕ (none) swatch plus the 6 colours, each a 34dp circle; the current one has a 3dp white ring
2. Lock pane / Unlock pane
3. Desktop site (switch; disabled when locked, with the reason; reloads the page)
4. Cookies & site data (view-only when locked)
5. Copy diagnostics
6. Select multiple
7. **End session** (red; disabled when locked)

When opened from ⋮, add a section **"All panes"** with: Filter panes · Check isolation & health · Settings.

**Confirmations** (card):
- Plain words: "End Pane 3?" / "End 4 sessions?", one short line of consequence, and which locked panes will be skipped.
- Buttons: Cancel, and a red action button.

## 7. Workspace screen (full-screen overlay)
- **Header:** "Workspaces" (22sp bold), "N of 7", and a 48dp ✕.
- **Cards:** in a grid of 2 columns (3 when the screen is at least 600dp wide), radius 16dp; the current card has a 2dp white border. Each card has:
  - A top row: workspace number (17sp bold), a lock icon if any pane is locked, "N open", a **lock/unlock-all** button and a **close ✕** (asks to confirm; refused with a lock shake if any pane is locked).
  - A **live 2×3 preview** of its panes in grid order: each tile is a WebView snapshot (site name if the snapshot is blank), with a 4dp colour stripe and a lock badge.
  - Tap a card to switch. Hold it for the workspace menu.
- **Bottom:** a full-width 52dp **"+ New workspace"** button, inserted right after the current workspace (max 7). It's dimmed with the reason underneath when at 7 or while filtering.
- **Animations:** opens with fade + slide + scale from 98% over 220ms; closes in 160ms. Back closes it.

## 8. Multi-select
- **Selection bar** replaces the command bar, with a surface2 background:
  - ✕ (exit) · "N selected" (15sp bold) with "In this workspace / Across N workspaces" under it · **All / None** (text toggle) · Lock · Unlock · Tag colour · ⋮.
- **⋮ in selection** (page): Reload selected · Open an address in selected (skips locked panes) · Desktop site on · Desktop site off (both skip locked panes) · Clear selection · **End sessions…**
- **Across workspaces:** the workspace screen stays usable while selecting, so you can select panes in several workspaces.

## 9. Filters
- **Filter page:** colour swatches (multi-select; matches any selected colour) plus a "URL contains" field. Live update; Clear all / Done.
- **Chips row** above the command bar: a filter icon, one chip per colour (dot, name, ✕), a URL chip ("URL has "x"", ✕), "N matches" and **Clear all**.
- **Layout:** non-matching panes fade out **in their own slot**. Other panes never move or grow. Workspaces with no matches are hidden from the workspace screen. Clearing a filter never reloads anything.

## 10. Links, pop-ups, keyboard
- **Links that ask for a new tab:** in an unlocked pane, the link opens in the same pane.
- **Pop-ups:** in a locked pane, and for script-opened windows (sign-in pop-ups), use a full-screen pop-up on the same profile with a ✕, the title "Pane N · host" and **Open in pane**.
- **Keyboard while typing in a website:**
  - The dock rides on top of the keyboard, following its animation.
  - The pane area shrinks to the space above the dock, so the site can scroll its field and Send button into view.
  - The pane being typed in is enlarged until the keyboard closes, unless the user changed it manually.
- **Keyboard while typing in the address field:** only the dock lifts; the panes don't resize.

## 11. Feedback and motion
- **Snack bar** above the dock with an **Undo** (6s) for lock, colour and bookmark changes.
- **Every transition finishes within 600ms.** Put all timings in one constants file and add a unit test that fails if any exceeds 600ms:
  - Press: 90ms in, 150ms out, scale 0.88–0.97 on buttons, chips and cards.
  - Fades: 160ms.
  - Enter: 220ms; exit: 160ms.
  - Pane enlarge/shrink: 280ms.
  - Pops (bookmark, lock, select): 240ms.
  - Lock shake: 380ms.
  - Progress line: 120ms.
  - Pages: rise 6% + fade over 220ms in, 160ms out.
  - Cards: grow from 94% + fade over 180ms in, 120ms out.
- **Workspace switch:** the arriving panes slide 36dp from the side the workspace is on, plus fade, over 220ms.
- **Fades:** never restart a running fade, and a view that's fading out must stop catching touches immediately.
- Use only Android animators, so the system "Remove animations" setting disables them.

## 12. Settings (full-screen page)
- **Keep sessions alive:** Keep CPU awake (switch) · Keep Wi-Fi awake (switch; "Android 14+ may ignore this request") · Battery optimisation (✓ or Fix) · Keep-alive notification (✓ or Allow) · Full speed after closing from Recents (✓ or Allow, opens "Display over other apps") · App info (OnePlus background settings).
- **Layout:** Full-size pages in small panes (switch) · Automatic / Always 2×3 / Always 3×2 (✓ on the current choice).
- **About:** How sessions work.

## 13. What a lock does (all of it)
A locked pane:
- **Cannot:** be ended, switch desktop mode, or have its cookies edited (the cookie editor becomes view-only with a red banner).
- **Ignores:** pull-to-refresh, and is skipped by bulk "open address" and bulk desktop on/off.
- **Opens new-tab links in the pop-up**, so its own page is never replaced.
- **Can still:** browse normally (typing an address), go back, zoom and be recoloured.
- **Feedback:** every refused action shakes the pane's lock (380ms), plays a reject haptic, and shows the reason in the snack bar.

## 14. Remaining screens
- **Workspace menu** (page; hold a workspace card or the workspace button). Title "Workspace N", subtitle "X of 6 panes open · Y locked". Rows:
  1. Lock all 6 panes, "Only this workspace" (shown unless all are locked)
  2. Unlock all panes, "N locked in this workspace" (shown if any are locked)
  3. New workspace to the right (disabled with the reason when at 7, or while filtering)
  4. **Close workspace**, red, "Ends all 6 sessions. Reopening starts fresh." (disabled while any pane is locked; tapping it then shakes those locks)
- **Cookie editor** (full screen; the header shows a back arrow and "Pane N · Workspace W · host"):
  - One explanatory line: "These belong only to this pane's session (profile …xxxxxx). No other pane can see or change them."
  - When the pane is locked: a red view-only banner.
  - A search field, then cookie rows (name in bold, value in monospace, 2 lines max); tap a row to edit.
  - "Add cookie" and "Delete all for this site" buttons.
  - Edit/add is a compact card with Name and Value fields and Save / Delete / Cancel. Input is validated, and the result is checked by re-reading the cookie, with honest messages when a save or delete doesn't fully work.
  - A "Storage on this page" section: local storage key count, session storage key count, IndexedDB database names, and the origin.
  - A footer note that Android doesn't reveal cookie paths, expiry dates or HttpOnly flags.
  - With no site open: "Open a website in this pane to see its cookies."
- **Copy diagnostics** puts text on the clipboard: app and device versions, WebView version, workspace and pane, pane ID, profile name, which profile the WebView is actually bound to (flagged if it doesn't match), URL, title, lock/desktop/zoom/colour, loading/error state, crash count, last JS heartbeat and page visibility, session age, cookie **names only** (never values), storage counts, user agent, keep-alive status, and live/responding counts. Then a snack: "Diagnostics for pane N copied".
- **Check isolation & health** (page). Writes a different random cookie into every pane's cookie jar, reads them all back, then deletes them. The title is "N of N sessions isolated" or "Isolation problem found". Rows, each with ✓ or ⚠:
  - Cookie jars are separate
  - Unique profiles (none shared, none the default)
  - Each browser uses its own profile
  - Sessions awake: a live JS ping to every page right now, with how many report themselves as visible
  - Keep-alive service, CPU wake lock, Wi-Fi lock ("requested, Android 14+ may ignore it"), battery optimisation, and closing from Recents (full speed or SLOWED)
  
  Buttons: Copy report / Done.
- **Welcome page** on first launch, shown only once. Title "Welcome to …", subtitle "42 private browsers — 7 workspaces of 6 panes."
  - Two short how-to lines: the bar controls the active pane and back is the system gesture; the pane header is the app's (double-tap enlarges, hold opens the menu).
  - A section "So sessions never drop" with rows, each shown only if still needed: allow the keep-alive notification · allow running in the background · keep full speed after closing from Recents.
  - A "Got it" button.
- **How sessions work** (page), in plain sentences:
  - Each pane is its own private browser.
  - A login in one pane is not shared with any other pane.
  - Ending a session or closing a workspace erases it for good; profiles are never reused.
  - Bookmarks are just addresses.
  - How keep-alive works.
  - Point to the isolation & health check.
- **No filter matches:** centred filter icon, "No panes match these filters", "Your sessions are still running." and a **Clear filters** button.
- **Unsupported WebView:** if the WebView can't give each pane its own profile, the app refuses to start and shows a full screen with a shield icon, "Update Android System WebView", a plain explanation, and an **Open Play Store** button. Never fall back to a shared profile.

## 15. Data rules behind the buttons
- **End session:** the pane's WebView is destroyed and its profile retired. A fresh pane with a new, never-used profile takes the **same slot**, and selection, active and enlarged state carry over to it. Profile deletion is retried on the next launch if Android refuses; Settings shows "Finish erasing ended sessions (N waiting)" while any are pending.
- **Close workspace:** all 6 profiles are retired, and the neighbouring workspace becomes current. Closing the last workspace immediately creates a fresh one.
- **Bookmarks are ONE shared list** of addresses, not per pane (opening one uses the session of the pane you open it in). This is a deliberate change from SIXPANE's per-pane bookmarks: keep existing bookmarks by merging them into the shared list, and tell me before changing anything.
- **Survives a restart:** workspaces and their order, pane URLs and titles, tags, locks, desktop mode, the active and enlarged pane, the current workspace, filters, layout choice, bookmarks, the keep-awake switches, and whether the welcome page has been seen. Save atomically (write a temp file, then rename), debounced by 400ms, and immediately when the app goes to the background.
- **Desktop site:** a desktop user agent made from the WebView's own one with "Android …" replaced by "X11; Linux x86_64" and "Mobile" and "; wv" removed, plus overview mode. It reloads the page.

## 16. Browser behaviour
- **"Leave this page?" warnings** from websites are shown to the user, never auto-answered.
- **File-upload buttons** open the Android file picker. If no picker is available: "No app available to pick files".
- **Camera, microphone and location requests** are refused, with a snack saying which site asked.
- **Downloads** go through Android's DownloadManager, sending the pane's own cookies, saved to Downloads, with a "Downloading <file>" snack.
- **`intent:`, `mailto:` and other app links** open the matching app only after a tap; an `intent:` link falls back to its `browser_fallback_url`.
- **Certificate errors** block the page with "This site's security certificate isn't trusted, so it was blocked."
- **JS alerts/confirms** while the app has no screen are cancelled safely.
- **Renderer crash:** rebuild on the same profile after 1s, 2s, 4s … up to 30s.
- **Heartbeat:** a JS ping to every page every 30s, which also flushes cookies.

## 17. Background and notifications
- **Keep-alive foreground service:** low importance, ongoing. Title "Keeping N sessions running", text "Pages stay connected while the screen is off. Tap to open." Holds a CPU wake lock and a Wi-Fi lock while the matching switches are on.
- **If Android killed the app:** the notification becomes "Sessions were stopped by Android / Tap to restore them. Logins are kept; open pages reload."
- **Swiped away from Recents:**
  - With "Display over other apps" allowed: move every WebView into an invisible, untouchable overlay window (alpha 0, laid out at its last size, translated off screen) so pages stay attached and visible to the page. Move them back when the app reopens.
  - Without the permission: post "Sessions slowed down / Tessera was closed from Recents. Tap to resume full speed."
- **After a phone restart or app update:** post "Phone restarted" or "… was updated" with "Tap to restore your sessions. Logins are kept; pages reload."
- **WebViews:**
  - Override window/view visibility so pages always report "visible".
  - Use important renderer priority, not waived when hidden.
  - Never call `onPause` or `pauseTimers`.
  - Keep panes in other workspaces attached, translated off screen, and VISIBLE.

## 18. Small things that must match
- **Snack bar:** radius 14dp, minimum height 48dp, surface3 with a 1dp stroke, 8dp elevation, sits 10dp above the dock and rides up with the keyboard. Shows for 3.2s, or 6s when it has an action.
- **Filter chips:** 34dp tall, radius 16dp, surface2 with a 1dp stroke, each ✕ a 34dp target.
- **Suggestions panel:** the top edge has a 18dp radius, 6dp elevation, and it sits directly above the dock.
- **Workspace screen card previews:** 72dp tall tiles (64dp with 3 columns), radius 6dp.
- **Every snack message to copy word-for-word:**
  - "Nothing to go forward to"
  - "7 is the maximum. Close a workspace to add one."
  - "Clear filters to add a workspace" (with a Clear action)
  - "Finish selecting first (tap ✕)"
  - "Open a page first, then bookmark it"
  - "Bookmarked <host>" (with Undo)
  - "Bookmark removed" (with Undo)
  - "Locked N panes" / "Unlocked N panes" (with Undo)
  - "Coloured <name>: N panes" (with Undo)
  - "Ended N sessions — fresh panes are ready"
  - "Filters cleared — all panes shown"
- **Selection mode:** tapping anywhere on a pane (overlay or header) toggles it. Entering selection shrinks any enlarged pane and closes the address field. "All" turns into "None" when every visible pane in the workspace is selected.
- **Address field:** touching any page, entering selection or opening any menu closes the field and hides the keyboard.

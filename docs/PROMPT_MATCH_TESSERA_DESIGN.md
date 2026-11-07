# Prompt: rebuild SIXPANE's UI to match the Tessera design

Paste everything below the line into ChatGPT.

---

You are working on my Android app SIXPANE (native Kotlin, WebView, 7 workspaces × 6 panes, each pane its own WebView profile). Redesign its controls, menus, flow and visual design to match the specification below **exactly**: same elements, placement, sizes (dp/sp), colours, behaviour and timings.

## Rules
- Keep everything under the hood unchanged: package ID, signing key, profile-per-pane isolation, saved workspaces, cookies, keep-alive service, crash recovery. This is a UI/flow change only. Existing sessions must survive the update.
- Where this spec removes a SIXPANE control, remove it. Where SIXPANE has a feature not in this spec, list it for me before deleting it.
- Do not add per-session fingerprint or identity spoofing of any kind.
- After building: list each section below as done / partly done / not done, with the file and line numbers.

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

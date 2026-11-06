# Tessera

A separate Android app from SIXPANE (different folder, package `app.tessera.browser`,
signing key and CI). 42 private browser sessions: 7 workspaces × 6 panes, built for
one-handed use on the OnePlus Open (folded and unfolded).

## Screen layout (top → bottom)

| Zone | What's there |
|---|---|
| **Pane grid** | 2×3 when folded (tall), 3×2 when unfolded (wide) — automatic, or fixed in Settings. |
| **Filter chips** | Only while a filter is on. Each chip has its own ✕; "Clear all" at the end. |
| **Command bar** | Acts on the *active* pane: **[pane chip]** · **[address pill]** · Forward · Reload/Stop · ⋮ |
| **Workspace tabs** | Numbers 1–7 with six dots each (lit = page open, red = locked) · `+` |

Everything you touch often is in the bottom third, within thumb reach.

## Gesture map — app gestures never touch website content

| Where | Tap | Double-tap | Long-press | Swipe |
|---|---|---|---|---|
| Web page | Website + makes pane active | Website | **Website** (links, text) | Website |
| Pane header strip (26dp) | Make active (in selection: select) | Expand / collapse | Pane menu | — |
| Pane chip (command bar) | Expand / collapse | — | Pane menu | — |
| Workspace tab | Switch | — | Workspace menu | Tab strip: next / previous workspace |
| Small pane (expanded view) | Make it the large pane | — | — | — |
| Android back | Exit selection → page back → collapse → move app to background (never closes sessions) |

## Visibility tiers

**Tier 1 — always visible:** active pane chip (number + colour, expand glyph), address pill
(lock/zoom shown inline), Forward (dimmed when there's nothing to go forward to),
Reload ↔ Stop, ⋮, workspace tabs, `+`. Back is the Android gesture.

**Tier 2 — contextual:**
- *Pane menu* (hold header or chip; ⋮ for the active pane): colour, lock/unlock, desktop
  site, zoom − % + reset, cookies & site data, copy diagnostics, select multiple, end session.
- *⋮ adds an "All panes" section:* filter panes, check isolation & health, settings.
- *Selection bar* (replaces the command bar): ✕, count + scope, All, Lock, Unlock,
  Colour, ⋮ (reload, open address, desktop on/off, clear selection, end sessions…).
- *Address sheet* (tap the pill): input with live "Opens…/Searches Google for…" preview,
  Paste & go, bookmark toggle, bookmark list (each with remove).
- *Workspace menu* (hold a tab): lock all / unlock all, new workspace to the right,
  close workspace.

**Tier 3 — Settings:** keep CPU awake, keep Wi-Fi awake, battery optimisation, notification,
OnePlus app info, layout, "How sessions work", finish erasing ended sessions.

## State visuals

| State | Look |
|---|---|
| Active | Blue 2dp frame, brighter header |
| Locked | Red lock in header, red-tinted header, thin red frame; lock shakes + haptic when something is refused |
| Selected | Green 2dp frame, green tint, filled check |
| Filtered out | Slot left empty (other panes never grow); workspace tab hidden if nothing matches |
| Loading | Thin blue progress line in the header and on the command bar; Reload becomes Stop |
| Error | In-pane card with the reason and **Retry** |
| Crashed renderer | "Page crashed — restoring this session" then automatic reload, same profile |
| New / empty pane | "New session" + **Open a site** |
| Zoom ≠ 100% / Desktop | Small badge in the header and on the address pill |

## Safeguards

| Action | Rule |
|---|---|
| End session (1 or many) | Confirmation lists exactly which panes (e.g. `W1·P2, W3·P5`) and which locked panes are skipped. Not undoable — said up front. |
| Close workspace | Refused while any pane is locked (shakes the locks). Confirmation. Re-checked at the moment of closing. |
| Lock / unlock / recolour (single or bulk) | Instant, with **Undo** for 6 s. |
| Bookmark add / remove | Instant, with **Undo**. |
| Cookie delete / delete all | Confirmation; result verified by re-reading; failures are reported, not hidden. |
| Locked pane | Can't be ended, switch desktop mode, or have cookies edited (view-only). Browsing and zoom still work. |
| 7 workspaces | `+` dims, shows "7/7"; tapping explains why. |
| Filters on | `+` explains "Clear filters to add a workspace" with a Clear action. |
| Empty address | Inline message; nothing is opened. |
| Unsupported WebView | App refuses to start rather than mix sessions, with a Play Store link. |

## Sessions, isolation and keep-alive

- Every pane is bound to its own named WebView profile (`tp_<uuid>`) **before** any setting,
  cookie or navigation. Names are never reused; ended profiles are deleted from disk (retried
  on next start if Android is still holding them).
- **Check isolation & health** (⋮) writes a different secret cookie into all 42 jars and proves each
  jar sees only its own, confirms every WebView's bound profile, and counts pages that answered
  a JavaScript heartbeat in the last 90 s.
- Keep-alive: foreground service (low-priority notification), partial CPU wake lock, Wi-Fi lock,
  WebViews report "visible" to the page even off-screen/in background, important renderer
  priority, never `onPause()`/`pauseTimers()`, WebViews live in the Application (survive
  fold/rotate), Back moves the app to the background instead of closing.
- Limits that no app can remove: Android can still kill the process under extreme memory
  pressure or if the user force-stops it. Logins (persistent cookies) survive that; open pages reload.
  Session-only cookies and in-page state do not survive a process kill.

## Build

```
gradle -p tessera :app:testDebugUnitTest :app:assembleDebug
```
CI: `.github/workflows/tessera.yml` — unit tests, lint, signed APK (signer pinned), and an
emulator job that proves 42-session cookie/localStorage/IndexedDB isolation and full-rate
timers with the app in the background, plus UI screenshots.

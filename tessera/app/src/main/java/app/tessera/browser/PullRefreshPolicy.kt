package app.tessera.browser

/**
 * Pull-to-refresh only when the PAGE ITSELF is pulled past its top (not an inner
 * scrolling box that merely looks like it is at the top), and never on a locked pane.
 * Pure; unit tested.
 */
object PullRefreshPolicy {
    const val WINDOW_MS = 400L

    fun allow(locked: Boolean, pageScrollY: Int, msSinceTopOverscroll: Long): Boolean =
        !locked && pageScrollY <= 0 && msSinceTopOverscroll in 0..WINDOW_MS
}

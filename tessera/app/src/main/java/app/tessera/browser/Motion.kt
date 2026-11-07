package app.tessera.browser

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.view.View
import android.view.animation.PathInterpolator

/**
 * Every UI animation's timing lives here. All transitions finish within [MAX_MS]
 * (unit tested). Status indicators that run while work is in progress (the reload
 * spinner) are not transitions and repeat until the work ends.
 *
 * All animations use Android's animator system, so the system "Remove animations"
 * accessibility setting turns them off automatically (UX reference R22/R33).
 */
object Motion {
    const val MAX_MS = 600L

    const val PRESS_MS = 90L        // finger down
    const val RELEASE_MS = 150L     // finger up
    const val FADE_MS = 160L        // panels, overlays, bar swap, filter
    const val ENTER_MS = 220L       // workspace switch, pages, overview
    const val EXIT_MS = 160L
    const val EXPAND_MS = 280L      // pane maximise / minimise
    const val POP_MS = 240L         // bookmark, lock, select confirmation
    const val SHAKE_MS = 380L       // refused action on a locked pane
    const val PROGRESS_MS = 120L

    val all = listOf(PRESS_MS, RELEASE_MS, FADE_MS, ENTER_MS, EXIT_MS, EXPAND_MS, POP_MS, SHAKE_MS, PROGRESS_MS)

    /** Standard "emphasized" easing: fast start, gentle settle. */
    val standard get() = PathInterpolator(0.2f, 0f, 0f, 1f)
    val decelerate get() = PathInterpolator(0f, 0f, 0.2f, 1f)
    val accelerate get() = PathInterpolator(0.3f, 0f, 1f, 1f)

    /** Subtle press-in for buttons, chips and cards (works alongside the ripple). */
    fun pressable(v: View, scale: Float = 0.94f) {
        val down = AnimatorSet().apply {
            playTogether(ObjectAnimator.ofFloat(v, View.SCALE_X, scale), ObjectAnimator.ofFloat(v, View.SCALE_Y, scale))
            duration = PRESS_MS; interpolator = decelerate
        }
        val up = AnimatorSet().apply {
            playTogether(ObjectAnimator.ofFloat(v, View.SCALE_X, 1f), ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f))
            duration = RELEASE_MS; interpolator = standard
        }
        v.stateListAnimator = StateListAnimator().apply {
            addState(intArrayOf(android.R.attr.state_pressed), down)
            addState(intArrayOf(), up)
        }
    }

    /** Short confirmation "pop" when something becomes true (bookmarked, locked, selected). */
    fun pop(v: View, peak: Float = 1.25f) {
        v.animate().cancel()
        v.scaleX = 1f; v.scaleY = 1f
        v.animate().scaleX(peak).scaleY(peak).setDuration(POP_MS * 2 / 5).setInterpolator(decelerate).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(POP_MS * 3 / 5).setInterpolator(standard).start()
        }.start()
    }

    fun fadeIn(v: View, duration: Long = FADE_MS) {
        if (v.visibility == View.VISIBLE && v.alpha == 1f) return
        v.animate().cancel()
        if (v.visibility != View.VISIBLE) { v.alpha = 0f; v.visibility = View.VISIBLE }
        v.animate().alpha(1f).setDuration(duration).setInterpolator(decelerate).start()
    }

    fun fadeOut(v: View, duration: Long = FADE_MS) {
        if (v.visibility != View.VISIBLE) return
        v.animate().cancel()
        v.animate().alpha(0f).setDuration(duration).setInterpolator(accelerate).withEndAction {
            v.visibility = View.GONE; v.alpha = 1f
        }.start()
    }
}

package com.sixpane.app

/** All Tessera UI animation durations are bounded to 600 ms. */
object TesseraMotion {
    const val PRESS_IN_MS=90L
    const val PRESS_OUT_MS=150L
    const val FADE_MS=160L
    const val ENTER_MS=220L
    const val EXIT_MS=160L
    const val PANE_RESIZE_MS=280L
    const val POP_MS=240L
    const val LOCK_SHAKE_MS=380L
    const val PROGRESS_MS=120L
    const val CARD_ENTER_MS=180L
    const val CARD_EXIT_MS=120L
    const val SNACK_MS=3200L
    const val SNACK_WITH_ACTION_MS=6000L
    val transitions:List<Long> get()=listOf(PRESS_IN_MS,PRESS_OUT_MS,
        FADE_MS,ENTER_MS,EXIT_MS,PANE_RESIZE_MS,POP_MS,
        LOCK_SHAKE_MS,PROGRESS_MS,CARD_ENTER_MS,CARD_EXIT_MS)
}

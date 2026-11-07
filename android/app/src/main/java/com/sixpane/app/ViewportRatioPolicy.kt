package com.sixpane.app

import kotlin.math.abs

/** Persisted per-pane viewport percentages. Ratios are unique across all existing workspaces. */
object ViewportRatioPolicy {
    const val MIN_PERCENT = 96
    const val MAX_PERCENT = 104
    private const val NORMAL_PERCENT = 100
    const val MAX_WINDOWS = WorkspacePolicy.SLOT_CAPACITY

    private fun gcd(first: Int, second: Int): Int {
        var a = first
        var b = second
        while (b != 0) {
            val remainder = a % b
            a = b
            b = remainder
        }
        return a
    }

    /** Compare proportions, not just width/height pairs (96:96 == 104:104). */
    fun ratioKey(widthPercent: Int, heightPercent: Int): Pair<Int, Int> {
        require(widthPercent > 0 && heightPercent > 0)
        val divisor = gcd(widthPercent, heightPercent)
        return widthPercent / divisor to heightPercent / divisor
    }

    private val candidates: List<Pair<Int, Int>> =
        (MIN_PERCENT..MAX_PERCENT).flatMap { width ->
            (MIN_PERCENT..MAX_PERCENT).map { height -> width to height }
        }.distinctBy { ratioKey(it.first, it.second) }

    /** Keep already-unique pairs; change only duplicate or out-of-range incoming pairs. */
    fun allocate(
        reserved: List<Pair<Int, Int>>,
        incoming: List<Pair<Int, Int>>
    ): List<Pair<Int, Int>> {
        require(reserved.size + incoming.size <= MAX_WINDOWS)
        val used = reserved.map { ratioKey(it.first, it.second) }.toMutableSet()
        check(used.size == reserved.size) { "Reserved viewport ratios must already be unique" }
        return incoming.map { requested ->
            val legal = requested.first in MIN_PERCENT..MAX_PERCENT &&
                requested.second in MIN_PERCENT..MAX_PERCENT
            val selected = if (legal && ratioKey(requested.first, requested.second) !in used) {
                requested
            } else {
                candidates.asSequence()
                    .filter { ratioKey(it.first, it.second) !in used }
                    .minWithOrNull(compareBy<Pair<Int, Int>>(
                        { abs(it.first - requested.first) + abs(it.second - requested.second) },
                        { abs(it.first - NORMAL_PERCENT) + abs(it.second - NORMAL_PERCENT) },
                        { it.first }, { it.second }
                    )) ?: error("No distinct viewport ratio remains")
            }
            check(used.add(ratioKey(selected.first, selected.second)))
            selected
        }
    }

    fun allUnique(pairs: List<Pair<Int, Int>>): Boolean =
        pairs.size <= MAX_WINDOWS &&
            pairs.all { it.first in MIN_PERCENT..MAX_PERCENT && it.second in MIN_PERCENT..MAX_PERCENT } &&
            pairs.map { ratioKey(it.first, it.second) }.distinct().size == pairs.size
}

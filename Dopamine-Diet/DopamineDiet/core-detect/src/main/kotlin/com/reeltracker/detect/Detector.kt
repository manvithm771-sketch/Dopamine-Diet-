package com.reeltracker.detect

import com.reeltracker.model.Decision
import com.reeltracker.model.UiSignal

/**
 * OUTSIDE <-> SHORT-FORM FEED state machine. Pure: no Android, no I/O, no clocks of its own.
 *
 * Accuracy policy (design section 9): whenever we can't be sure we're in a tracked feed, exit.
 * A missed video makes the number low; a phantom one makes it wrong forever.
 */
class Detector(
    private val strategies: List<DetectionStrategy> = listOf(IndexStrategy()),
) {
    var inReels = false
        private set

    var activePackage: String? = null
        private set
    private var pinned: DetectionStrategy? = null
    private val recent = ArrayDeque<UiSignal>(RECENT)

    /** The last [RECENT] signals, for dumping on enter/exit. */
    fun recentSignals(): List<UiSignal> = recent.toList()

    fun reset() {
        inReels = false
        activePackage = null
        pinned = null
        recent.clear()
        strategies.forEach { it.reset() }
    }

    fun accept(signal: UiSignal): List<Decision> {
        if (recent.size == RECENT) recent.removeFirst()
        recent.addLast(signal)

        return when (signal) {
            is UiSignal.Foreground -> when {
                ReelsContext.isOverlay(signal.pkg, signal.cls) -> NONE
                ReelsContext.isTrackedPackage(signal.pkg) ->
                    if (inReels && activePackage != signal.pkg) exit() else NONE
                else -> exit()
            }

            is UiSignal.WindowState ->
                if (activePackage == ReelsContext.PACKAGE && !ReelsContext.isHostWindow(signal.cls)) exit()
                else NONE

            is UiSignal.Selected -> when {
                ReelsContext.isReelsTab(signal.sourceId) -> enter(ReelsContext.PACKAGE)
                ReelsContext.isOtherTab(signal.sourceId) && activePackage == ReelsContext.PACKAGE -> exit()
                else -> NONE
            }

            is UiSignal.Scrolled -> onScroll(signal)
        }
    }

    private fun onScroll(s: UiSignal.Scrolled): List<Decision> {
        val sourcePackage = ReelsContext.packageFromSource(s.sourceId)

        if (!ReelsContext.isPager(s.sourceId, s.sourceClass, s.deltaY)) {
            // A YouTube scroll on a non-Shorts view means the user left Shorts.
            return if (sourcePackage == ReelsContext.YOUTUBE_PACKAGE &&
                activePackage == ReelsContext.YOUTUBE_PACKAGE
            ) exit() else NONE
        }

        val app = sourcePackage ?: return NONE
        val out = ArrayList<Decision>(3)
        if (!inReels) {
            out += enter(app)
        } else if (activePackage != app) {
            out += exit()
            out += enter(app)
        }

        val strategy = pinned
            ?: strategies.firstOrNull { it.supports(SignalProbe(recent.toList())) }?.also { pinned = it }
            ?: return out + exit()

        when (val d = strategy.accept(s)) {
            Decision.ExitReels -> out += exit()
            Decision.Ignore -> Unit
            else -> out += d
        }
        return out
    }

    private fun enter(pkg: String): List<Decision> {
        if (inReels) return NONE
        inReels = true
        activePackage = pkg
        pinned = null
        return listOf(Decision.EnterReels)
    }

    private fun exit(): List<Decision> {
        if (!inReels) return NONE
        inReels = false
        activePackage = null
        pinned = null
        return listOf(Decision.ExitReels)
    }

    private companion object {
        const val RECENT = 40
        val NONE = emptyList<Decision>()
    }
}

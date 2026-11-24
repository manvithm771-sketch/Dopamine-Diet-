package com.reeltracker.detect

import com.reeltracker.model.Decision
import com.reeltracker.model.UiSignal

/**
 * Counts a settled pager index once per feed. Instagram and YouTube keep independent histories.
 */
class IndexStrategy : DetectionStrategy {
    override val id = "index-v1"

    private data class FeedState(
        val seen: HashSet<Int> = HashSet(),
        var epoch: Int = 0,
        var lastIndex: Int = -1,
        var lastItemCount: Int = -1,
        var lastYouTubeSwipeAt: Long = Long.MIN_VALUE,
        var youtubeSequence: Int = 0,
    )

    private val feeds = HashMap<String, FeedState>()

    override fun supports(probe: SignalProbe): Boolean {
        val s = probe.lastPagerScroll() ?: return false
        val youtube = ReelsContext.packageFromSource(s.sourceId) == ReelsContext.YOUTUBE_PACKAGE
        return if (youtube) s.deltaY != 0 else s.toIndex >= 0 && s.itemCount >= 0
    }

    override fun accept(signal: UiSignal.Scrolled): Decision {
        val app = ReelsContext.packageFromSource(signal.sourceId) ?: return Decision.ExitReels
        val state = feeds.getOrPut(app) { FeedState() }

        if (app == ReelsContext.YOUTUBE_PACKAGE) {
            // Shorts often recycle one item and report -1/0 indexes. Count a vertical
            // swipe once, suppressing repeated accessibility events from the same gesture.
            if (signal.deltaY == 0) return Decision.Ignore
            if (state.lastYouTubeSwipeAt != Long.MIN_VALUE &&
                signal.t - state.lastYouTubeSwipeAt < YOUTUBE_SWIPE_DEBOUNCE_MS
            ) return Decision.Ignore
            state.lastYouTubeSwipeAt = signal.t
            state.youtubeSequence++
            return Decision.ReelConfirmed(
                key = state.epoch * EPOCH_STRIDE + state.youtubeSequence,
                strategyId = "$id@$app",
            )
        }

        if (signal.toIndex < 0 || signal.itemCount < 0) return Decision.ExitReels
        if (signal.fromIndex >= 0 && signal.fromIndex != signal.toIndex) return Decision.Ignore

        if (signal.itemCount < state.lastItemCount && signal.toIndex < state.lastIndex) {
            state.epoch++
            state.seen.clear()
        }
        state.lastItemCount = signal.itemCount
        state.lastIndex = signal.toIndex

        if (!state.seen.add(signal.toIndex)) return Decision.Ignore
        return Decision.ReelConfirmed(
            key = state.epoch * EPOCH_STRIDE + signal.toIndex,
            strategyId = "$id@$app",
        )
    }

    override fun reset() {
        feeds.clear()
    }

    private companion object {
        const val EPOCH_STRIDE = 100_000
        const val YOUTUBE_SWIPE_DEBOUNCE_MS = 700L
    }
}

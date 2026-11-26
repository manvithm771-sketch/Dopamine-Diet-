package com.reeltracker.detect

import com.reeltracker.detect.ReelsContext.PAGER_ID
import com.reeltracker.detect.ReelsContext.YOUTUBE_PACKAGE
import com.reeltracker.detect.ReelsContext.YOUTUBE_PAGER_ID
import com.reeltracker.model.Decision
import com.reeltracker.model.Decision.EnterReels
import com.reeltracker.model.Decision.ExitReels
import com.reeltracker.model.UiSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorTest {

    private val d = Detector()
    private var t = 0L

    private fun page(index: Int, count: Int = 30, from: Int = index, id: String? = PAGER_ID) =
        d.accept(UiSignal.Scrolled(from, index, count, 1500, 0, id, "androidx.viewpager.widget.ViewPager", t++))

    private fun youtubePage(
        index: Int,
        count: Int = 30,
        from: Int = index,
        id: String? = YOUTUBE_PAGER_ID,
        at: Long = t++,
    ) = d.accept(UiSignal.Scrolled(from, index, count, 1500, 0, id, "androidx.recyclerview.widget.RecyclerView", at))

    private fun tab(name: String) = d.accept(UiSignal.Selected("com.instagram.android:id/$name", t++))

    private fun confirmed(ds: List<Decision>) = ds.filterIsInstance<Decision.ReelConfirmed>().map { it.key }

    @Test
    fun `reels tab enters, swipes count, other tab exits`() {
        assertEquals(listOf(EnterReels), tab("clips_tab"))
        assertEquals(listOf(1), confirmed(page(1)))
        assertEquals(listOf(2), confirmed(page(2)))
        assertEquals(listOf(ExitReels), tab("feed_tab"))
        assertFalse(d.inReels)
    }

    @Test
    fun `first pager scroll from elsewhere enters and counts in one step`() {
        val out = page(1)
        assertEquals(EnterReels, out[0])
        assertEquals(listOf(1), confirmed(out))
    }

    @Test
    fun `repeat events for one gesture count once`() {
        page(1)
        assertEquals(emptyList<Int>(), confirmed(page(1)))
        assertEquals(emptyList<Int>(), confirmed(page(1)))
    }

    @Test
    fun `swiping back and forward again does not recount`() {
        page(1); page(2); page(3)
        assertEquals(emptyList<Int>(), confirmed(page(2)))
        assertEquals(emptyList<Int>(), confirmed(page(3)))
        assertEquals(listOf(4), confirmed(page(4)))
    }

    @Test
    fun `range-reporting pager mid-swipe does not count until settled`() {
        page(1)
        assertEquals(emptyList<Int>(), confirmed(page(index = 2, from = 1)))
        assertEquals(emptyList<Int>(), confirmed(page(1))) // bounced back
        assertEquals(listOf(2), confirmed(page(2)))
    }

    @Test
    fun `session split by a dialog does not recount old reels`() {
        page(1); page(2)
        assertEquals(listOf(ExitReels), d.accept(UiSignal.WindowState("some.CommentsDialog", 0, t++)))
        val back = page(2)
        assertEquals(EnterReels, back[0])
        assertEquals(emptyList<Int>(), confirmed(back))
    }

    @Test
    fun `fresh feed starts a new epoch with distinct keys`() {
        page(5, count = 30); page(6, count = 30)
        val keys = confirmed(page(1, count = 8))
        assertEquals(1, keys.size)
        assertTrue("key must not collide with index 1 of the old feed", keys[0] != 1)
    }

    @Test
    fun `item removed while moving forward is not a fresh feed`() {
        page(5, count = 30)
        assertEquals(listOf(6), confirmed(page(6, count = 29)))
        assertEquals(emptyList<Int>(), confirmed(page(5, count = 29)))
    }

    @Test
    fun `missing indices mid-session stop counting`() {
        page(1)
        assertEquals(listOf(ExitReels), page(-1, count = -1, from = -1))
        assertFalse(d.inReels)
    }

    @Test
    fun `scrolls from other lists are ignored`() {
        tab("clips_tab")
        assertEquals(emptyList<Decision>(), page(5, id = "android:id/list"))
    }

    @Test
    fun `leaving instagram exits, system ui does not`() {
        page(1)
        assertEquals(emptyList<Decision>(), d.accept(UiSignal.Foreground("com.android.systemui", null, t++)))
        assertTrue(d.inReels)
        assertEquals(listOf(ExitReels), d.accept(UiSignal.Foreground("com.android.launcher3", null, t++)))
    }

    @Test
    fun `brainrot overlay keeps reels, brainrot app exits`() {
        page(1)
        assertEquals(emptyList<Decision>(), d.accept(UiSignal.Foreground("com.reeltracker", "android.widget.FrameLayout", t++)))
        assertTrue(d.inReels)
        assertEquals(listOf(ExitReels), d.accept(UiSignal.Foreground("com.reeltracker", "com.reeltracker.ui.MainActivity", t++)))
    }

    @Test
    fun `main activity window state keeps us in reels`() {
        page(1)
        assertEquals(
            emptyList<Decision>(),
            d.accept(UiSignal.WindowState("com.instagram.mainactivity.InstagramMainActivity", 0, t++)),
        )
        assertTrue(d.inReels)
    }

    @Test
    fun `youtube shorts uses the same index counter`() {
        val rawTime = t++
        val raw = RawEvent(
            type = SignalMapper.TYPE_VIEW_SCROLLED,
            pkg = YOUTUBE_PACKAGE,
            cls = "androidx.recyclerview.widget.RecyclerView",
            fromIndex = 1,
            toIndex = 1,
            itemCount = 30,
            scrollDeltaY = 1500,
            scrollY = 0,
            sourceId = YOUTUBE_PAGER_ID,
            sourceClass = "androidx.recyclerview.widget.RecyclerView",
            descHash = 0,
            t = rawTime,
        )
        assertTrue(SignalMapper.needsSource(SignalMapper.TYPE_VIEW_SCROLLED, YOUTUBE_PACKAGE))
        val signal = SignalMapper.map(raw) as UiSignal.Scrolled
        val out = d.accept(signal)
        assertEquals(EnterReels, out[0])
        assertEquals(listOf(1), confirmed(out))
        assertEquals(emptyList<Int>(), confirmed(youtubePage(1, at = rawTime + 100)))
        assertEquals(listOf(2), confirmed(youtubePage(2, at = rawTime + 900)))
    }

 
    @Test
    fun `youtube counts vertical swipes when recycler indexes are unavailable`() {
        val first = youtubePage(index = -1, count = -1, from = -1, at = 10_000)
        assertEquals(EnterReels, first[0])
        assertEquals(listOf(1), confirmed(first))
        assertEquals(emptyList<Int>(), confirmed(youtubePage(-1, -1, -1, at = 10_100)))
        assertEquals(listOf(2), confirmed(youtubePage(-1, -1, -1, at = 10_900)))
    }

    @Test
    fun `instagram and youtube keep separate seen index sets`() {
        page(1)
        assertEquals(listOf(ExitReels), d.accept(UiSignal.Foreground(YOUTUBE_PACKAGE, null, t++)))
        val youtube = youtubePage(1)
        assertEquals(EnterReels, youtube[0])
        assertEquals(listOf(1), confirmed(youtube))
    }

    @Test
    fun `scrolling youtube outside shorts exits shorts tracking`() {
        youtubePage(1)
        val out = d.accept(
            UiSignal.Scrolled(1, 2, 30, 1200, 0, "$YOUTUBE_PACKAGE:id/watch_player", "android.view.View", t++),
        )
        assertEquals(listOf(ExitReels), out)
        assertFalse(d.inReels)
    }

    @Test
    fun `recorder export round-trips structure`() {
        val r = SignalRecorder(capacity = 3)
        repeat(5) { i -> r.record(UiSignal.Selected("x\"$i", i.toLong()), listOf(EnterReels)) }
        val json = r.exportJson(mapOf("igVersion" to "447"))
        assertTrue(json.contains("\"igVersion\":\"447\""))
        assertFalse("oldest entries evicted", json.contains("x\\\"1"))
        assertTrue(json.contains("x\\\"4"))
    }
}

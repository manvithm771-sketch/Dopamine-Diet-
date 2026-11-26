package com.reeltracker.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.reeltracker.data.ReelGraph
import com.reeltracker.data.ReelRepository
import com.reeltracker.detect.Detector
import com.reeltracker.detect.RawEvent
import com.reeltracker.detect.SignalMapper
import com.reeltracker.model.Decision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Adapter. Android types live here and nowhere else: AccessibilityEvent -> UiSignal -> Detector,
 * then Decisions are acted on. No tree traversal and no screen text is read.
 */
class ReelAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val detector = Detector()
    private lateinit var repo: ReelRepository
    private lateinit var overlay: OverlayController

    // Preserve decision order so each confirmed reel is assigned to its feed visit.
    private val writes = Channel<Decision>(Channel.UNLIMITED)

    override fun onServiceConnected() {
        repo = ReelGraph.repository(this)
        overlay = OverlayController(this, scope)
        detector.reset()
        scope.launch(Dispatchers.IO) { applyWrites() }
        // Home-screen widget follows the count live, including the reset at midnight.
        scope.launch(Dispatchers.IO) {
            repo.todayCount.distinctUntilChanged().collect { ReelWidgetProvider.update(this@ReelAccessibilityService, it) }
        }
        ReelServiceState.connected = true
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val type = e.eventType
        val pkg = e.packageName?.toString()

        var sourceId: String? = null
        var sourceClass: String? = null
        if (SignalMapper.needsSource(type, pkg)) {
            val src = e.source ?: return // §9: no source node, no guess
            sourceId = src.viewIdResourceName
            sourceClass = src.className?.toString()
        }

        val signal = SignalMapper.map(
            RawEvent(
                type = type,
                pkg = pkg,
                cls = e.className?.toString(),
                fromIndex = e.fromIndex,
                toIndex = e.toIndex,
                itemCount = e.itemCount,
                scrollDeltaY = e.scrollDeltaY,
                scrollY = e.scrollY,
                sourceId = sourceId,
                sourceClass = sourceClass,
                descHash = e.contentDescription?.hashCode() ?: 0,
                t = SystemClock.elapsedRealtime(),
            )
        ) ?: return

        val decisions = detector.accept(signal)
        ReelServiceState.recorder.record(signal, decisions)
        for (d in decisions) {
            when (d) {
                Decision.EnterReels -> {
                    val countFlow = if (detector.activePackage == com.reeltracker.detect.ReelsContext.YOUTUBE_PACKAGE) {
                        repo.todayStats.map { it.shortsCount }
                    } else {
                        repo.todayStats.map { it.count }
                    }
                    overlay.show(countFlow)
                }
                Decision.ExitReels -> overlay.hide()
                else -> Unit
            }
            writes.trySend(d)
        }
    }

    private suspend fun applyWrites() {
        var feedVisitId = 0L
        for (d in writes) when (d) {
            Decision.EnterReels -> feedVisitId = UUID.randomUUID().mostSignificantBits
            Decision.ExitReels -> feedVisitId = 0L
            is Decision.ReelConfirmed -> {
                if (feedVisitId != 0L) repo.record(feedVisitId, d.key, d.strategyId)
            }
            Decision.Ignore -> Unit
        }
    }

    override fun onInterrupt() = Unit


    override fun onDestroy() {
        ReelServiceState.connected = false
        if (::overlay.isInitialized) overlay.hide()
        writes.close()
        scope.cancel()
        super.onDestroy()
    }
}

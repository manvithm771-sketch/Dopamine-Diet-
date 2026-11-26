package com.reeltracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.reeltracker.service.BrainRot
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeltracker.model.DayStats
import com.reeltracker.model.PeriodCounts
import com.reeltracker.model.UsageOverview

enum class Tracking { On, Stalled, Off }

private val tnum = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun TodayScreen(
    now: Long,
    stats: DayStats,
    usageOverview: UsageOverview,
    lastEventAt: Long?,
    tracking: Tracking,
    is24h: Boolean,
    exportMessage: String?,
    onOpenAccessibility: () -> Unit,
    onExport: () -> Unit,
    onAddWidget: () -> Unit,
) {
    val p = LocalPalette.current
    val clock = Clock(is24h)
    val bars = WindowInsets.systemBars.asPaddingValues()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(p.background),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = bars.calculateTopPadding() + 16.dp,
            bottom = bars.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header(now, clock, tracking, onOpenAccessibility) }
        if (tracking != Tracking.On) item { TrackingCard(tracking, onOpenAccessibility) }
        item { Hero(stats.count, stats.shortsCount, lastEventAt, now, clock) }
        item { PeriodPanel("This Week", usageOverview.thisWeek) }
        item { PeriodPanel("This Month", usageOverview.thisMonth) }
        item { PeriodPanel("Total", usageOverview.total) }
        item { BrainCard(stats.count, onAddWidget) }
        item { Footer(exportMessage, onExport) }
    }
}


@Composable
private fun PeriodPanel(title: String, counts: PeriodCounts) {
    Panel(title) {
        PeriodLine("Instagram Reels", counts.instagramReels)
        Spacer(Modifier.height(10.dp))
        PeriodLine("YouTube Shorts", counts.youtubeShorts)
        Spacer(Modifier.height(10.dp))
        PeriodLine("Combined total", counts.total, emphasized = true)
    }
}

@Composable
private fun PeriodLine(label: String, count: Int, emphasized: Boolean = false) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (emphasized) p.textPrimary else p.textSecondary,
        )
        Text(
            count.toString(),
            style = TextStyle(
                fontSize = if (emphasized) 22.sp else 18.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = p.textPrimary,
        )
    }
}

@Composable
private fun Header(now: Long, clock: Clock, tracking: Tracking, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(clock.date(now), style = MaterialTheme.typography.titleMedium, color = p.textSecondary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    clock.clock(now),
                    style = tnum.copy(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
                    color = p.textPrimary,
                )
                clock.amPm(now)?.let {
                    Text(
                        " $it",
                        style = MaterialTheme.typography.titleMedium,
                        color = p.textSecondary,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
            }
        }
        StatusPill(tracking, onClick, Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StatusPill(tracking: Tracking, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val (dot, label) = when (tracking) {
        Tracking.On -> p.good to "Tracking"
        Tracking.Stalled -> p.accent to "Not running"
        Tracking.Off -> p.accent to "Off"
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(p.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tracking == Tracking.On) PulsingDot(dot) else Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = p.textPrimary)
    }
}

@Composable
internal fun PulsingDot(color: Color) {
    val a = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "alpha",
    )
    Box(Modifier.size(8.dp).alpha(a.value).clip(CircleShape).background(color))
}

@Composable
private fun TrackingCard(tracking: Tracking, onClick: () -> Unit) {
    val p = LocalPalette.current
    Panel(if (tracking == Tracking.Off) "Tracking is off" else "Tracking stopped") {
        Text(
            if (tracking == Tracking.Off) {
                "Turn on Brainrot in Accessibility settings. It reads which list scrolled and its position, nothing else."
            } else {
                "The service is enabled but isn't running. The system probably stopped it to save battery. Turn it off and on again in Accessibility settings, and set battery use for Brainrot to Unrestricted."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = p.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = Color.White),
        ) { Text("Open Accessibility settings") }
    }
}

@Composable
private fun Hero(count: Int, shortsCount: Int, lastEventAt: Long?, now: Long, clock: Clock) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 18.dp, bottom = 6.dp)) {
        Text("Reels watched today", style = MaterialTheme.typography.titleMedium, color = p.textSecondary)
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
            },
            label = "count",
        ) { n ->
            Text(
                "$n",
                style = TextStyle(
                    fontSize = 112.sp,
                    lineHeight = 112.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-4).sp,
                ),
                color = p.textPrimary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Shorts watched today: $shortsCount",
            style = MaterialTheme.typography.titleMedium,
            color = p.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            lastEventAt?.let { "Last one counted ${clock.ago(it, now)}" } ?: "Nothing counted yet",
            style = MaterialTheme.typography.bodyMedium,
            color = p.textMuted,
        )
    }
}

/**
 * Today's brain, the same sprite the overlay and the widget show, with the way to put that
 * widget on the home screen.
 */
@Composable
private fun BrainCard(count: Int, onAddWidget: () -> Unit) {
    val p = LocalPalette.current
    val stage = BrainRot.stage(count)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(targetState = stage, label = "brain") { s ->
            Image(
                painter = painterResource(BrainRot.sprite(s)),
                contentDescription = "Brain: ${BrainRot.label(s)}",
                modifier = Modifier.size(width = 76.dp, height = 64.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                BrainRot.label(stage),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.textPrimary,
            )
            Text(
                BrainRot.untilNext(count)?.let { "$it more until it gets worse" } ?: "It can't get any worse",
                style = MaterialTheme.typography.bodySmall,
                color = p.textMuted,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onAddWidget,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
            ) {
                Text("Add to home screen", color = p.accent, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Footer(exportMessage: String?, onExport: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = onExport) {
            Text("Export debug capture", color = p.textSecondary)
        }
        exportMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = p.textMuted)
        }
        Spacer(Modifier.height(4.dp))
        Text("All activity counts are stored locally on this phone.", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
    }
}

@Composable
internal fun Panel(title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.card)
            .padding(18.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

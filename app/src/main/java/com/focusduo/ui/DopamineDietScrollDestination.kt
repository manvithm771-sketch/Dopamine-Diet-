package com.focusduo.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.activity.compose.BackHandler
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reeltracker.service.BrainRot
import com.reeltracker.service.ReelAccessibilityService
import com.reeltracker.service.ReelServiceState
import com.reeltracker.service.ReelWidgetProvider
import com.reeltracker.ui.TodayViewModel

private val ScrollBg = Color(0xFF08090E)
private val ScrollCard = Color(0xFF171923)
private val ScrollText = Color(0xFFF7F5FC)
private val ScrollMuted = Color(0xFFAAAABD)
private val ScrollViolet = Color(0xFF9B6DFF)
private val ScrollBlue = Color(0xFF278DFF)
private val ScrollMint = Color(0xFF36DFA0)

@Composable
fun DopamineDietScrollDestination() {
    val context = LocalContext.current
    val viewModel: TodayViewModel = viewModel()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val usageOverview by viewModel.usageOverview.collectAsStateWithLifecycle()
    val lastEventAt by viewModel.lastEventAt.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()

    var tracking by remember { mutableStateOf(readTrackingState(context)) }
    var is24h by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }
    LifecycleResumeEffect(Unit) {
        tracking = readTrackingState(context)
        is24h = DateFormat.is24HourFormat(context)
        ReelWidgetProvider.refresh(context)
        onPauseOrDispose { }
    }

    val totalToday = stats.count + stats.shortsCount
    var showInsights by remember { mutableStateOf(false) }
    if (showInsights) {
        BackHandler(enabled = true) { showInsights = false }
        ScrollInsightsScreen(today = totalToday, week = usageOverview.thisWeek, month = usageOverview.thisMonth, allTime = usageOverview.total, onBack = { showInsights = false })
        return
    }

    val date = remember(is24h) {
        java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date())
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().background(ScrollBg),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Scroll", color = ScrollText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatisticsIcon(Modifier.size(42.dp).clip(CircleShape).clickable { showInsights = true }.padding(10.dp))
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = BrainRot.sprite(BrainRot.stage(totalToday))),
                        contentDescription = "Brain status: ${BrainRot.label(BrainRot.stage(totalToday))}",
                        modifier = Modifier.size(width = 76.dp, height = 64.dp)
                    )
                    Text("$totalToday", color = ScrollText, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Reels + Shorts", color = ScrollMuted, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(13.dp))
                Text(date, color = ScrollMuted, style = MaterialTheme.typography.bodyMedium)
                Text("Your scrolling activity today", color = ScrollText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PlatformCountRow(
                    context = context,
                    packageName = "com.instagram.android",
                    label = "Instagram Reels",
                    count = stats.count
                )
                PlatformCountRow(
                    context = context,
                    packageName = "com.google.android.youtube",
                    label = "YouTube Shorts",
                    count = stats.shortsCount
                )
            }
        }
        if (tracking != com.reeltracker.ui.Tracking.On) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF211D2E))) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Turn on scroll tracking", color = ScrollText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Enable the accessibility service to count Reels and Shorts while you use supported apps.", color = ScrollMuted, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, colors = ButtonDefaults.buttonColors(containerColor = ScrollViolet)) {
                            Text("Open Accessibility settings")
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ScrollCard)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your activity", color = ScrollText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ScrollCountRow("Today", totalToday)
                    ScrollCountRow("This week", usageOverview.thisWeek.total)
                    ScrollCountRow("This month", usageOverview.thisMonth.total)
                    ScrollCountRow("Total", usageOverview.total.total)
                }
            }
        }
        item { lastEventAt?.let { Text("Last activity: ${java.text.DateFormat.getTimeInstance(if (is24h) java.text.DateFormat.SHORT else java.text.DateFormat.SHORT).format(java.util.Date(it))}", color = ScrollMuted, style = MaterialTheme.typography.bodySmall) } }
    }
}

@Composable
private fun PlatformCountRow(
    context: Context,
    packageName: String,
    label: String,
    count: Int
) {
    val iconAvailable = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
            true
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconAvailable) {
            AndroidView(
                factory = { viewContext ->
                    ImageView(viewContext).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(viewContext.packageManager.getApplicationIcon(packageName))
                    }
                },
                modifier = Modifier.size(34.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = if (iconAvailable) 13.dp else 0.dp)
        ) {
            Text(label, color = ScrollText, fontWeight = FontWeight.Medium)
        }
        Text(count.toString(), color = ScrollText, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ScrollCountRow(label: String, count: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ScrollMuted, modifier = Modifier.weight(1f))
        Text(count.toString(), color = ScrollText, fontWeight = FontWeight.SemiBold)
    }
}

private fun readTrackingState(context: Context): com.reeltracker.ui.Tracking {
    val service = ComponentName(context, ReelAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?.split(':')?.any { ComponentName.unflattenFromString(it) == service } == true
    return when {
        !enabled -> com.reeltracker.ui.Tracking.Off
        ReelServiceState.connected -> com.reeltracker.ui.Tracking.On
        else -> com.reeltracker.ui.Tracking.Stalled
    }
}

private fun requestBrainWidget(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, ReelWidgetProvider::class.java)
    val pinned = manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(provider, null, null)
    if (!pinned) Toast.makeText(context, "Long-press your home screen, open Widgets, and add Dopamine Diet.", Toast.LENGTH_LONG).show()
}

@Composable
private fun StatisticsIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val color = Color(0xFFE5E5EF)
        val stroke = 2.dp.toPx()
        listOf(0.35f, 0.65f, 0.95f).forEachIndexed { index, height ->
            val x = size.width * (0.18f + index * 0.32f)
            drawLine(color, Offset(x, size.height * (1f - height)), Offset(x, size.height), strokeWidth = stroke)
        }
    }
}

@Composable
private fun ScrollInsightsScreen(today: Int, week: com.reeltracker.model.PeriodCounts, month: com.reeltracker.model.PeriodCounts, allTime: com.reeltracker.model.PeriodCounts, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(ScrollBg).padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = ScrollText, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.clickable(onClick = onBack).padding(end = 16.dp))
            Column {
                Text("Scroll insights", color = ScrollText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Your recorded Reels and Shorts activity", color = ScrollMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        InsightMetricCard("Today", today, "Reels + Shorts")
        InsightMetricCard("This week", week.total, "Instagram " + week.instagramReels + " · YouTube " + week.youtubeShorts)
        InsightMetricCard("This month", month.total, "Instagram " + month.instagramReels + " · YouTube " + month.youtubeShorts)
        InsightMetricCard("All time", allTime.total, "Instagram " + allTime.instagramReels + " · YouTube " + allTime.youtubeShorts)
        Text("Platform breakdown", color = ScrollText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        PlatformInsightBar("Instagram Reels", allTime.instagramReels, allTime.total)
        PlatformInsightBar("YouTube Shorts", allTime.youtubeShorts, allTime.total)
        Text("Counts represent detected feed-item events. They do not measure watch duration.", color = ScrollMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InsightMetricCard(title: String, value: Int, detail: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = ScrollCard)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = ScrollMuted, style = MaterialTheme.typography.labelLarge)
            Text(value.toString(), color = ScrollText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(detail, color = ScrollMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlatformInsightBar(label: String, value: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row {
            Text(label, color = ScrollText, modifier = Modifier.weight(1f))
            Text(value.toString(), color = ScrollMuted)
        }
        androidx.compose.material3.LinearProgressIndicator(progress = { if (total > 0) value.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)), color = ScrollViolet, trackColor = Color(0xFF292A3B))
    }
}

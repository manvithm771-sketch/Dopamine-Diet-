package com.focusduo.ui

import android.content.pm.PackageManager
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mindful.android.nativeapp.data.local.NativeDatabaseProvider
import java.util.Calendar

private val TimeBg = Color(0xFF08090E)
private val TimeCard = Color(0xFF171923)
private val TimeText = Color(0xFFF7F5FC)
private val TimeMuted = Color(0xFFAAAABD)
private val TimeViolet = Color(0xFF9B6DFF)
private val TimeMint = Color(0xFF36DFA0)

@Composable
fun DopamineDietScreenTimeDestination() {
    val context = LocalContext.current
    val database = remember(context) { NativeDatabaseProvider.get(context) }

    val yearRange = remember {
        val currentWeek = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val sinceMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
            add(Calendar.DAY_OF_YEAR, -sinceMonday)
        }
        val firstWeek = (currentWeek.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, -51) }
        val end = (currentWeek.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, 1) }
        Triple(firstWeek.timeInMillis, end.timeInMillis, (0 until 52).map { offset ->
            (firstWeek.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, offset) }.timeInMillis
        })
    }
    val usage by database.nativeDashboardDao()
        .observeUsageForRange(yearRange.first, yearRange.second)
        .collectAsState(initial = emptyList())
    val daily by database.nativeDashboardDao()
        .observeDailyUsageRange(yearRange.first, yearRange.second)
        .collectAsState(initial = emptyList())

    val annualTotal = usage.sumOf { it.totalScreenTime }
    val weeklyTotals = remember(daily, yearRange.third) {
        val totals = MutableList(52) { 0L }
        daily.forEach { day ->
            val weekIndex = yearRange.third.indexOfLast { it <= day.dateEpochMillis }
            if (weekIndex in totals.indices) totals[weekIndex] += day.totalScreenTime
        }
        totals
    }
    val maxWeek = weeklyTotals.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val maxApp = usage.maxOfOrNull { it.totalScreenTime }?.coerceAtLeast(1L) ?: 1L
    val appRows = usage.sortedByDescending { it.totalScreenTime }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().background(TimeBg),
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Screen Time", color = TimeText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Your screen usage over the last 52 weeks.", color = TimeMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = TimeCard)
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF1C2030), Color(0xFF171923))))
                        .padding(19.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(formatScreenDuration(annualTotal), color = TimeText, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Total screen time · 52 weeks", color = TimeMuted, style = MaterialTheme.typography.bodyMedium)
                    Text("52-week history", color = TimeMint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(21.dp),
                colors = CardDefaults.cardColors(containerColor = TimeCard)
            ) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Weekly screen time", color = TimeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("52 WEEKS", color = TimeMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(126.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        weeklyTotals.forEachIndexed { index, seconds ->
                            Column(
                                Modifier.width(13.dp).height(122.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Box(
                                    Modifier.width(9.dp)
                                        .height((82f * (seconds.toFloat() / maxWeek).coerceAtLeast(0.035f)).dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Brush.verticalGradient(listOf(TimeViolet, Color(0xFF4B3A87))))
                                )
                                Spacer(Modifier.height(5.dp))
                                if (index % 4 == 0 || index == 51) {
                                    Text((index + 1).toString(), color = TimeMuted, style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("52 weeks ago", color = TimeMuted, style = MaterialTheme.typography.labelSmall)
                        Text("This week", color = TimeMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("All Apps", color = TimeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Last 52 weeks", color = TimeMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (appRows.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = TimeCard)) {
                    Text(
                        "No app usage history is available for this period. Check that Usage Access is enabled for Dopamine Diet.",
                        color = TimeMuted,
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(appRows.size) { index ->
                val app = appRows[index]
                val label = remember(app.packageName) { appDisplayName(context.packageManager, app.packageName) }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AndroidView(
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)),
                            factory = { viewContext ->
                                ImageView(viewContext).apply {
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    setPadding(3, 3, 3, 3)
                                }
                            },
                            update = { imageView ->
                                runCatching {
                                    imageView.setImageDrawable(context.packageManager.getApplicationIcon(app.packageName))
                                }.onFailure {
                                    imageView.setImageDrawable(null)
                                }
                            }
                        )
                        Column(Modifier.weight(1f).padding(start = 11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(label, color = TimeText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Last 52 weeks", color = TimeMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(formatScreenDuration(app.totalScreenTime), color = TimeMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(
                        progress = { (app.totalScreenTime.toFloat() / maxApp).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(start = 49.dp).height(6.dp).clip(CircleShape),
                        color = TimeViolet,
                        trackColor = Color(0xFF292A3B)
                    )
                }
            }
        }
    }
}

private fun appDisplayName(packageManager: PackageManager, packageName: String): String = runCatching {
    @Suppress("DEPRECATION")
    val info = packageManager.getApplicationInfo(packageName, 0)
    packageManager.getApplicationLabel(info).toString()
}.getOrDefault(packageName)

private fun formatScreenDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val days = hours / 24L
    val remainingHours = hours % 24L
    return when {
        days > 0L -> "${days}d ${remainingHours}h"
        hours > 0L -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

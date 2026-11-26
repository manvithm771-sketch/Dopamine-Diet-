package com.focusduo.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
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
import com.mindful.android.nativeapp.data.local.NativeDatabaseProvider
import com.mindful.android.nativeapp.data.local.NativeAppUsageSummary
import java.util.Calendar
import java.util.Locale

private val TimeBg = Color(0xFF08090E)
private val TimeCard = Color(0xFF171923)
private val TimeText = Color(0xFFF7F5FC)
private val TimeMuted = Color(0xFFAAAABD)
private val TimeViolet = Color(0xFF9B6DFF)
private val TimeBlue = Color(0xFF4D9DFF)
private val TimeMint = Color(0xFF36DFA0)

@Composable
fun DopamineDietScreenTimeDestination() {
    val context = LocalContext.current
    val database = remember(context) { NativeDatabaseProvider.get(context) }
    val todayRange = remember {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        start.timeInMillis to end.timeInMillis
    }
    val weekRange = remember {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            val sinceMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
            add(Calendar.DAY_OF_YEAR, -sinceMonday)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
        start.timeInMillis to end.timeInMillis
    }
    val usage by database.nativeDashboardDao().observeUsageForRange(todayRange.first, todayRange.second).collectAsState(initial = emptyList())
    val weekly by database.nativeDashboardDao().observeDailyUsageRange(weekRange.first, weekRange.second).collectAsState(initial = emptyList())
    val total = usage.sumOf { it.totalScreenTime }
    val weekTotal = weekly.sumOf { it.totalScreenTime }
    val maxApp = usage.maxOfOrNull { it.totalScreenTime }?.coerceAtLeast(1L) ?: 1L
    val maxDay = weekly.maxOfOrNull { it.totalScreenTime }?.coerceAtLeast(1L) ?: 1L
    val dayValues = remember(weekly, weekRange.first) { (0..6).map { offset -> val dayStart = weekRange.first + offset * 86_400_000L; weekly.firstOrNull { it.dateEpochMillis == dayStart }?.totalScreenTime ?: 0L } }
    val appRows = usage.sortedByDescending { it.totalScreenTime }.take(6)

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().background(TimeBg),
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(TimeViolet, Color(0xFF37257D)))), contentAlignment = Alignment.Center) {
                    Text("◷", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                }
                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                    Text("Screen Time", color = TimeText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Take control of your screen.", color = TimeMuted, style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF171D32)), contentAlignment = Alignment.Center) {
                    Text("▥", color = TimeBlue, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = TimeCard)) {
                Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1C2030), Color(0xFF171923)))).padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(formatScreenDuration(total), color = TimeText, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("Today's Screen Time", color = TimeMuted, style = MaterialTheme.typography.bodyMedium)
                        Text("This week: ${formatScreenDuration(weekTotal)}", color = TimeMint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Canvas(Modifier.size(112.dp)) {
                        val barCount = 5
                        val gap = size.width * 0.075f
                        val barWidth = (size.width - gap * (barCount - 1)) / barCount
                        val values = dayValues.takeLast(barCount).map { (it.toFloat() / maxDay).coerceAtLeast(0.04f) }
                        values.forEachIndexed { index, value ->
                            val h = size.height * value
                            drawRoundRect(
                                brush = Brush.verticalGradient(listOf(TimeViolet, Color(0xFF6843C8))),
                                topLeft = androidx.compose.ui.geometry.Offset(index * (barWidth + gap), size.height - h),
                                size = androidx.compose.ui.geometry.Size(barWidth, h),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = TimeCard)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Weekly activity", color = TimeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("This week", color = TimeMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(Modifier.fillMaxWidth().height(92.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        dayValues.forEachIndexed { index, seconds ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.width(22.dp).height((70f * (seconds.toFloat() / maxDay).coerceAtLeast(0.04f)).dp).clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(TimeViolet, Color(0xFF4B3A87)))))
                                Text(listOf("M","T","W","T","F","S","S")[index], color = TimeMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        item { Text("App Breakdown", color = TimeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (appRows.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = TimeCard)) {
                    Text("No app usage recorded today. Grant Usage Access to let Dopamine Diet read screen-time statistics.", color = TimeMuted, modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(appRows) { item ->
                val index = appRows.indexOf(item)
                val label = remember(item.packageName) { appDisplayName(context.packageManager, item.packageName) }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (index == 0) Color(0xFF472B39) else Color(0xFF25213A)), contentAlignment = Alignment.Center) {
                            Text(if (index == 0) "▶" else "●", color = if (index == 0) Color(0xFFFF7188) else TimeViolet, style = MaterialTheme.typography.titleSmall)
                        }
                        Text(label, color = TimeText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 11.dp))
                        Text(formatScreenDuration(item.totalScreenTime), color = TimeMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(
                        progress = { (item.totalScreenTime.toFloat() / maxApp).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(start = 45.dp).height(6.dp).clip(CircleShape),
                        color = TimeViolet,
                        trackColor = Color(0xFF292A3B)
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = TimeCard)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✦", color = Color(0xFFFFC65A), style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("A mindful break", color = TimeText, fontWeight = FontWeight.SemiBold)
                        Text("Take a short pause between screen sessions.", color = TimeMuted, style = MaterialTheme.typography.bodySmall)
                    }
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
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}

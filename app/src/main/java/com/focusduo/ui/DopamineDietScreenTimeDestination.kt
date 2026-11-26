package com.focusduo.ui

import android.content.pm.PackageManager
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mindful.android.nativeapp.data.local.NativeDatabaseProvider
import com.mindful.android.nativeapp.data.local.NativeAppUsageSummary
import com.mindful.android.nativeapp.data.local.entity.NativeAppRestrictionEntity
import com.mindful.android.nativeapp.data.local.entity.NativeRestrictionGroupEntity
import com.mindful.android.nativeapp.data.local.entity.NativeWellbeingEntity
import com.mindful.android.nativeapp.ui.NativeHomeScreen
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

private val TimeBg = Color(0xFF08090E)
private val TimeCard = Color(0xFF171923)
private val TimeText = Color(0xFFF7F5FC)
private val TimeMuted = Color(0xFFAAAABD)
private val TimeViolet = Color(0xFF9B6DFF)
private val TimeMint = Color(0xFF36DFA0)

@Composable
fun DopamineDietScreenTimeDestination(
    onSaveAppRestriction: (NativeAppRestrictionEntity) -> Unit,
    onDeleteAppRestriction: (String) -> Unit,
    onSaveRestrictionGroup: (NativeRestrictionGroupEntity) -> Unit,
    onDeleteRestrictionGroup: (Long) -> Unit,
    onSaveWellbeing: (NativeWellbeingEntity) -> Unit,
    onRefreshUsage: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { onRefreshUsage() }
    val database = remember(context) { NativeDatabaseProvider.get(context) }
    var openedBlockingTool by remember { mutableStateOf<String?>(null) }
    var showInsights by remember { mutableStateOf(false) }
    var showAllApps by remember { mutableStateOf(false) }

    if (openedBlockingTool != null) {
        NativeHomeScreen(
            dao = database.nativeDashboardDao(),
            focusDao = database.nativeFocusDao(),
            onSaveAppRestriction = onSaveAppRestriction,
            onDeleteAppRestriction = onDeleteAppRestriction,
            onSaveRestrictionGroup = onSaveRestrictionGroup,
            onDeleteRestrictionGroup = onDeleteRestrictionGroup,
            onSaveWellbeing = onSaveWellbeing,
            startOnApps = openedBlockingTool == "apps",
            startOnWebsiteBlock = openedBlockingTool == "websites",
            showInternalNavigation = false,
            forceDopamineDietTheme = true,
            onStandaloneBack = { openedBlockingTool = null }
        )
        return
    }

    val historyRange = remember {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -10) }
        val end = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        Triple(start.timeInMillis, end.timeInMillis, (0..10).map { daysAgo ->
            (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }.timeInMillis
        })
    }
    val usage by database.nativeDashboardDao()
        .observeUsageForRange(historyRange.first, historyRange.second)
        .collectAsState(initial = emptyList())
    val daily by database.nativeDashboardDao()
        .observeDailyUsageRange(historyRange.first, historyRange.second)
        .collectAsState(initial = emptyList())
    val dailyTotals = remember(daily, historyRange.third) {
        historyRange.third.map { day ->
            daily.firstOrNull { it.dateEpochMillis == day }?.totalScreenTime ?: 0L
        }
    }
    val todayRange = remember {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        start.timeInMillis to end.timeInMillis
    }
    val todayApps by database.nativeDashboardDao()
        .observeUsageForRange(todayRange.first, todayRange.second)
        .collectAsState(initial = emptyList())
    val todayTotal = todayApps.sumOf { it.totalScreenTime }
    val historyTotal = daily.sumOf { it.totalScreenTime }
    val appRows = usage.sortedByDescending { it.totalScreenTime }
    if (showInsights) {
        BackHandler(enabled = true) { showInsights = false }
        ScreenTimeInsightsScreen(
            historyTotal = historyTotal,
            dailyTotals = dailyTotals,
            dayStarts = historyRange.third,
            appRows = appRows,
            packageManager = context.packageManager,
            onBack = { showInsights = false }
        )
        return
    }

    if (showAllApps) {
        BackHandler(enabled = true) { showAllApps = false }
        AllScreenTimeAppsScreen(appRows, context.packageManager) { showAllApps = false }
        return
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().background(TimeBg),
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Screen Time", color = TimeText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Your screen usage over the last 11 days.", color = TimeMuted, style = MaterialTheme.typography.bodySmall)
                }
                ScreenStatisticsIcon(Modifier.size(42.dp).clip(CircleShape).clickable { showInsights = true }.padding(10.dp))
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = TimeCard)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Today", color = TimeMuted, style = MaterialTheme.typography.labelLarge)
                        Text(formatScreenDuration(todayTotal), color = TimeText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (todayApps.isEmpty()) "No app usage recorded today" else "${todayApps.size} apps with recorded usage",
                            color = TimeMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text("TODAY", color = TimeMint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Text("Today's app usage", color = TimeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (todayApps.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = TimeCard)) {
                    Text(
                        "No app usage has been recorded today yet. Usage Access must be enabled for Dopamine Diet.",
                        color = TimeMuted,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            items(todayApps.sortedByDescending { it.totalScreenTime }.take(5).size) { index ->
                val app = todayApps.sortedByDescending { it.totalScreenTime }[index]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AndroidView(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)),
                        factory = { viewContext -> ImageView(viewContext).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(3, 3, 3, 3) } },
                        update = { imageView ->
                            runCatching { imageView.setImageDrawable(context.packageManager.getApplicationIcon(app.packageName)) }
                                .onFailure { imageView.setImageDrawable(null) }
                        }
                    )
                    Text(
                        appDisplayName(context.packageManager, app.packageName),
                        color = TimeText,
                        modifier = Modifier.weight(1f).padding(start = 11.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(formatScreenDuration(app.totalScreenTime), color = TimeMuted, style = MaterialTheme.typography.bodySmall)
                }
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
                    Text(formatScreenDuration(historyTotal), color = TimeText, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Total screen time · last 11 days", color = TimeMuted, style = MaterialTheme.typography.bodyMedium)
                    Text("11-day history", color = TimeMint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            DailyScreenTimeChart(
                dailyTotals = dailyTotals,
                dayStarts = historyRange.third
            )
        }
        item {
            BlockingActionCard(
                title = "App Blocking",
                description = "Choose apps and manage their timers, launch limits and restriction groups.",
                onClick = { openedBlockingTool = "apps" }
            )
        }
        item {
            BlockingActionCard(
                title = "Website Blocking",
                description = "Manage blocked websites, adult-site rules and short-form content restrictions.",
                onClick = { openedBlockingTool = "websites" }
            )
        }
        item {
            Text("All Apps", color = TimeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            items(appRows.take(5).size) { index ->
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
                        }
                        Text(formatScreenDuration(app.totalScreenTime), color = TimeMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (appRows.size > 5) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { showAllApps = true }
                        .padding(vertical = 13.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("More", color = TimeText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("  ›", color = TimeMuted, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}



@Composable
private fun AllScreenTimeAppsScreen(
    appRows: List<NativeAppUsageSummary>,
    packageManager: PackageManager,
    onBack: () -> Unit
) {
    var selectedMetric by remember { mutableStateOf("time") }
    val sortedRows = remember(appRows, selectedMetric) {
        if (selectedMetric == "data") {
            appRows.sortedByDescending { it.totalMobileData + it.totalWifiData }
        } else {
            appRows.sortedByDescending { it.totalScreenTime }
        }
    }
    Column(Modifier.fillMaxSize().background(TimeBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = TimeText, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.clickable(onClick = onBack).padding(end = 16.dp))
            Column {
                Text("All Apps", color = TimeText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Last 11 days", color = TimeMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp)).background(TimeCard).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("time" to "Time", "data" to "Data").forEach { (metric, label) ->
                val selected = selectedMetric == metric
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                        .background(if (selected) Color(0xFF343640) else Color.Transparent)
                        .clickable { selectedMetric = metric }.padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (selected) TimeText else TimeMuted, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            if (sortedRows.isEmpty()) {
                item { Text("No app usage history is available for this period.", color = TimeMuted, style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(sortedRows.size) { index ->
                    val app = sortedRows[index]
                    val label = remember(app.packageName) { appDisplayName(packageManager, app.packageName) }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AndroidView(
                                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)),
                                factory = { viewContext -> ImageView(viewContext).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(3, 3, 3, 3) } },
                                update = { image -> runCatching { image.setImageDrawable(packageManager.getApplicationIcon(app.packageName)) }.onFailure { image.setImageDrawable(null) } }
                            )
                            Text(label, color = TimeText, modifier = Modifier.weight(1f).padding(start = 11.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            val value = if (selectedMetric == "data") {
                                formatDataUsage(app.totalMobileData + app.totalWifiData)
                            } else {
                                formatScreenDuration(app.totalScreenTime)
                            }
                            Text(value, color = TimeMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDataUsage(kilobytes: Long): String {
    val megabytes = kilobytes.coerceAtLeast(0L) / 1024.0
    return if (megabytes >= 1024.0) {
        String.format(Locale.getDefault(), "%.2f GB", megabytes / 1024.0)
    } else {
        String.format(Locale.getDefault(), "%.1f MB", megabytes)
    }
}

@Composable
private fun ScreenStatisticsIcon(modifier: Modifier = Modifier) {
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
private fun ScreenTimeInsightsScreen(
    historyTotal: Long,
    dailyTotals: List<Long>,
    dayStarts: List<Long>,
    appRows: List<NativeAppUsageSummary>,
    packageManager: PackageManager,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(TimeBg).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = TimeText, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.clickable(onClick = onBack).padding(end = 16.dp))
            Column {
                Text("Screen time insights", color = TimeText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Your last 11 days of recorded usage", color = TimeMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        InsightSummaryCard("Total screen time · last 11 days", formatScreenDuration(historyTotal))
        DailyScreenTimeChart(dailyTotals = dailyTotals, dayStarts = dayStarts)
        Text("Top apps · last 11 days", color = TimeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        appRows.take(8).forEach { app ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AndroidView(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)), factory = { viewContext -> ImageView(viewContext).apply { scaleType = ImageView.ScaleType.FIT_CENTER } }, update = { image -> runCatching { image.setImageDrawable(packageManager.getApplicationIcon(app.packageName)) }.onFailure { image.setImageDrawable(null) } })
                Text(appDisplayName(packageManager, app.packageName), color = TimeText, modifier = Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
                Text(formatScreenDuration(app.totalScreenTime), color = TimeMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Usage history is collected using the same recent-day UsageEvents approach as Mindful.", color = TimeMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DailyScreenTimeChart(
    dailyTotals: List<Long>,
    dayStarts: List<Long>
) {
    val maxDaily = (dailyTotals.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = TimeCard)
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Daily screen time", color = TimeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Last 11 days", color = TimeMuted, style = MaterialTheme.typography.labelMedium)
            }
            Text("Each bar represents one day", color = TimeMuted, style = MaterialTheme.typography.bodySmall)
            Canvas(Modifier.fillMaxWidth().height(172.dp)) {
                val chartHeight = size.height
                val slot = size.width / 11f
                listOf(0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
                    val y = chartHeight * (1f - fraction)
                    drawLine(
                        color = Color(0xFF343640),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
                    )
                }
                dailyTotals.take(11).forEachIndexed { index, total ->
                    val fraction = (total.toFloat() / maxDaily.toFloat()).coerceIn(0f, 1f)
                    val barHeight = chartHeight * fraction
                    val barWidth = slot * 0.58f
                    val left = index * slot + (slot - barWidth) / 2f
                    if (barHeight > 0f) {
                        drawRoundRect(
                            color = if (index == 10) Color(0xFFB6BFFF) else Color(0xFF777D9E),
                            topLeft = Offset(left, chartHeight - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                dayStarts.take(11).forEachIndexed { index, day ->
                    if (index == 0 || index == 5 || index == 10) {
                        Text(dateFormat.format(java.util.Date(day)), color = TimeMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightSummaryCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = TimeCard)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = TimeMuted, style = MaterialTheme.typography.labelLarge)
            Text(value, color = TimeText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BlockingActionCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TimeCard)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = TimeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, color = TimeMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", color = TimeMuted, style = MaterialTheme.typography.headlineMedium)
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

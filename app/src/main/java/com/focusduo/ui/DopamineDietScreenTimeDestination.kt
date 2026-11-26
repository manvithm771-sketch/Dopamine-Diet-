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
    var selectedWeekIndex by remember { mutableStateOf(51) }

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

    // Sum the daily aggregates so the annual figure uses the same 52-week dataset as the chart.
    val annualTotal = daily.sumOf { it.totalScreenTime }
    val currentWeekDailyTotals = remember(daily) {
        val monday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
        }
        val start = monday.timeInMillis
        val end = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis
        val totals = MutableList(7) { 0L }
        daily.filter { it.dateEpochMillis >= start && it.dateEpochMillis < end }.forEach { day ->
            val dayCalendar = Calendar.getInstance().apply { timeInMillis = day.dateEpochMillis }
            val index = (dayCalendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
            totals[index] += day.totalScreenTime
        }
        totals
    }
    val weeklyTotals = remember(daily, yearRange.third) {
        val totals = MutableList(52) { 0L }
        daily.forEach { day ->
            val weekIndex = yearRange.third.indexOfLast { it <= day.dateEpochMillis }
            if (weekIndex in totals.indices) totals[weekIndex] += day.totalScreenTime
        }
        totals
    }
    val selectedWeekStart = yearRange.third[selectedWeekIndex.coerceIn(0, 51)]
    val selectedWeekEnd = (Calendar.getInstance().apply { timeInMillis = selectedWeekStart }).apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis
    val selectedWeekDailyTotals = remember(daily, selectedWeekStart) {
        val totals = MutableList(7) { 0L }
        daily.filter { it.dateEpochMillis >= selectedWeekStart && it.dateEpochMillis < selectedWeekEnd }.forEach { day ->
            val dayCalendar = Calendar.getInstance().apply { timeInMillis = day.dateEpochMillis }
            val dayIndex = (dayCalendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
            totals[dayIndex] += day.totalScreenTime
        }
        totals
    }
    val appRows = usage.sortedByDescending { it.totalScreenTime }
    if (showInsights) {
        BackHandler(enabled = true) { showInsights = false }
        ScreenTimeInsightsScreen(
            annualTotal = annualTotal,
            weeklyTotals = weeklyTotals,
            appRows = appRows,
            packageManager = context.packageManager,
            selectedWeekDailyTotals = selectedWeekDailyTotals,
            selectedWeekIndex = selectedWeekIndex,
            selectedWeekStart = selectedWeekStart,
            canGoPrevious = selectedWeekIndex > 0,
            canGoNext = selectedWeekIndex < 51,
            onPreviousWeek = { if (selectedWeekIndex > 0) selectedWeekIndex-- },
            onNextWeek = { if (selectedWeekIndex < 51) selectedWeekIndex++ },
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
                    Text("Your screen usage over the last 52 weeks.", color = TimeMuted, style = MaterialTheme.typography.bodySmall)
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
                    Text(formatScreenDuration(annualTotal), color = TimeText, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Total screen time · 52 weeks", color = TimeMuted, style = MaterialTheme.typography.bodyMedium)
                    Text("52-week history", color = TimeMint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            WeeklyScreenTimeChart(
                weeklyTotals = weeklyTotals,
                selectedWeekIndex = selectedWeekIndex
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
                Text("Last 52 weeks", color = TimeMuted, style = MaterialTheme.typography.bodySmall)
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
    annualTotal: Long,
    weeklyTotals: List<Long>,
    appRows: List<NativeAppUsageSummary>,
    packageManager: PackageManager,
    selectedWeekDailyTotals: List<Long>,
    selectedWeekIndex: Int,
    selectedWeekStart: Long,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
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
                Text("A deeper look at your recorded usage", color = TimeMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        InsightSummaryCard("1-year total screen time · 52 weeks", formatScreenDuration(annualTotal))
        WeekdayScreenTimeChart(
            dayTotals = selectedWeekDailyTotals,
            weekNumber = selectedWeekIndex + 1,
            weekStartMillis = selectedWeekStart,
            canGoPrevious = canGoPrevious,
            canGoNext = canGoNext,
            onPrevious = onPreviousWeek,
            onNext = onNextWeek
        )
        Text("Top apps · last 52 weeks", color = TimeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        appRows.take(8).forEach { app ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AndroidView(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)), factory = { viewContext -> ImageView(viewContext).apply { scaleType = ImageView.ScaleType.FIT_CENTER } }, update = { image -> runCatching { image.setImageDrawable(packageManager.getApplicationIcon(app.packageName)) }.onFailure { image.setImageDrawable(null) } })
                Text(appDisplayName(packageManager, app.packageName), color = TimeText, modifier = Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
                Text(formatScreenDuration(app.totalScreenTime), color = TimeMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Insights use usage records available on this device. Missing historical records are not estimated.", color = TimeMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WeeklyScreenTimeChart(
    weeklyTotals: List<Long>,
    selectedWeekIndex: Int
) {
    val maxWeekly = (weeklyTotals.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val chartWidth = 52 * 14
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = TimeCard)
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Weekly screen time", color = TimeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Last 52 weeks", color = TimeMuted, style = MaterialTheme.typography.labelMedium)
            }
            Text("Each bar represents one week", color = TimeMuted, style = MaterialTheme.typography.bodySmall)
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Canvas(Modifier.width(chartWidth.dp).height(172.dp)) {
                    val chartHeight = size.height
                    val slot = size.width / 52f
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
                    weeklyTotals.take(52).forEachIndexed { index, total ->
                        val fraction = (total.toFloat() / maxWeekly.toFloat()).coerceIn(0f, 1f)
                        val barHeight = chartHeight * fraction
                        val barWidth = slot * 0.58f
                        val left = index * slot + (slot - barWidth) / 2f
                        if (barHeight > 0f) {
                            drawRoundRect(
                                color = if (index == selectedWeekIndex) Color(0xFFB6BFFF) else Color(0xFF777D9E),
                                topLeft = Offset(left, chartHeight - barHeight),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                            )
                        }
                    }
                }
                Row(Modifier.width(chartWidth.dp).padding(top = 7.dp)) {
                    (0 until 52).forEach { index ->
                        Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) {
                            if (index % 4 == 0 || index == 51) {
                                Text((index + 1).toString(), color = TimeMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Text("Weeks with no recorded usage appear empty. Scroll horizontally to view all 52 weeks.", color = TimeMuted, style = MaterialTheme.typography.labelSmall)
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
private fun WeekdayScreenTimeChart(
    dayTotals: List<Long>,
    weekNumber: Int,
    weekStartMillis: Long,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val axis = listOf("15h", "12h", "8h", "4h", "0s")
    val maxSeconds = 15L * 60L * 60L
    val weekEndMillis = (Calendar.getInstance().apply { timeInMillis = weekStartMillis }).apply { add(Calendar.DAY_OF_YEAR, 6) }.timeInMillis
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Daily breakdown", color = TimeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Week $weekNumber of 52", color = TimeMuted, style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                dateFormat.format(java.util.Date(weekStartMillis)) + " – " + dateFormat.format(java.util.Date(weekEndMillis)),
                color = TimeMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("‹", color = if (canGoPrevious) TimeText else TimeMuted.copy(alpha = 0.3f), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = canGoPrevious, onClick = onPrevious).padding(horizontal = 8.dp))
                Text("›", color = if (canGoNext) TimeText else TimeMuted.copy(alpha = 0.3f), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = canGoNext, onClick = onNext).padding(horizontal = 8.dp))
            }
        }

        // Reference-matched weekday chart: dark canvas, dashed hour guides,
        // lavender Monday bar, muted gradient bars, and a right-side hour axis.
        Row(
            modifier = Modifier.fillMaxWidth().height(244.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(
                modifier = Modifier.weight(1f).fillMaxSize().padding(end = 12.dp)
            ) {
                val chartHeight = size.height
                val chartWidth = size.width
                val gridHours = listOf(12f, 8f, 4f)
                gridHours.forEach { hours ->
                    val y = chartHeight - (hours / 15f) * chartHeight
                    drawLine(
                        color = Color(0xFF343640),
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 9.dp.toPx()))
                    )
                }
                val slot = chartWidth / 7f
                val barWidth = slot * 0.62f
                dayTotals.take(7).forEachIndexed { index, seconds ->
                    val fraction = (seconds.toFloat() / maxSeconds.toFloat()).coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        val barHeight = chartHeight * fraction
                        val left = index * slot + (slot - barWidth) / 2f
                        drawRoundRect(
                            brush = if (index == 0) {
                                Brush.verticalGradient(
                                    listOf(Color(0xFFB6BFFF), Color(0xFF777D9E))
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(Color(0xFF45495B), Color(0xFF242630))
                                )
                            },
                            topLeft = Offset(left, chartHeight - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.width(42.dp).fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                axis.forEach { value ->
                    Text(value, color = TimeMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            labels.forEach { label ->
                Text(label, color = TimeMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            }
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

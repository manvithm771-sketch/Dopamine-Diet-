package com.mindful.android.nativeapp.ui

import com.mindful.android.R

import android.content.Context
import android.content.Intent
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mindful.android.enums.PlatformFeatures
import com.mindful.android.nativeapp.restrictions.protectedSystemPackageNames
import com.mindful.android.nativeapp.data.local.NativeDashboardDao
import com.mindful.android.nativeapp.data.local.NativeAppUsageSummary
import com.mindful.android.nativeapp.data.local.NativeDailyUsageSummary
import com.mindful.android.nativeapp.data.local.NativeFocusDao
import com.mindful.android.nativeapp.data.local.entity.NativeRestrictionGroupEntity
import com.mindful.android.nativeapp.data.local.entity.NativeAppRestrictionEntity
import com.mindful.android.nativeapp.data.local.entity.NativeWellbeingEntity
import com.mindful.android.nativeapp.data.local.entity.NativeFocusProfileEntity
import com.mindful.android.nativeapp.data.local.entity.NativeFocusSessionEntity
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

private enum class NativeTab(val label: String, val symbol: String) {
    DASHBOARD("Home", "⌂"),
    STATISTICS("Stats", "▥"),
    FOCUS("Focus", "◎"),
    APPS("Apps", "▦"),
}

@Composable
fun NativeHomeScreen(
    dao: NativeDashboardDao,
    focusDao: NativeFocusDao,
    onStartFocus: (Long) -> Unit = {},
    onFinishFocus: (Boolean) -> Unit = {},
    onSaveFocusProfile: (NativeFocusProfileEntity) -> Unit = {},
    onSaveFocusReflection: (String) -> Unit = {},
    onRequestOverlayAccess: () -> Unit = {},
    onApplyRestrictions: () -> Unit = {},
    onSaveAppRestriction: (NativeAppRestrictionEntity) -> Unit = {},
    onDeleteAppRestriction: (String) -> Unit = {},
    onSaveRestrictionGroup: (NativeRestrictionGroupEntity) -> Unit = {},
    onDeleteRestrictionGroup: (Long) -> Unit = {},
    onSaveWellbeing: (NativeWellbeingEntity) -> Unit = {},
    startOnStatistics: Boolean = false,
    startOnFocus: Boolean = false,
    startOnApps: Boolean = false,
    startOnWebsiteBlock: Boolean = false,
    showInternalNavigation: Boolean = true,
    onStandaloneBack: (() -> Unit)? = null,
    forceDopamineDietTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    var weekOffset by remember { mutableStateOf(0) }
    // Flutter's DateTime.weekday uses Monday as the first day of the week.
    // Keep the native weekly range aligned with that exact Monday-to-Monday interval.
    val weekRange = remember(weekOffset) {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
            add(Calendar.DAY_OF_YEAR, -daysSinceMonday + (weekOffset * 7))
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
        start.timeInMillis to end.timeInMillis
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
    val restrictions by dao.observeAppRestrictions().collectAsState(initial = emptyList())
    val groups by dao.observeRestrictionGroups().collectAsState(initial = emptyList())
    val wellbeing by dao.observeWellbeing().collectAsState(initial = null)
    val usage by dao.observeUsageForRange(todayRange.first, todayRange.second)
        .collectAsState(initial = emptyList())
    var selectedStatsDateEpochMillis by remember { mutableStateOf(todayRange.first) }
    val selectedStatsDayRange = remember(selectedStatsDateEpochMillis) {
        val start = Calendar.getInstance().apply {
            timeInMillis = selectedStatsDateEpochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        start.timeInMillis to end.timeInMillis
    }
    val selectedStatsUsage by dao.observeUsageForRange(
        selectedStatsDayRange.first,
        selectedStatsDayRange.second
    ).collectAsState(initial = emptyList())
    val weeklyUsage by dao.observeDailyUsageRange(weekRange.first, weekRange.second)
        .collectAsState(initial = emptyList())
    val focusMode by dao.observeFocusMode().collectAsState(initial = null)
    val focusProfiles by dao.observeFocusProfiles().collectAsState(initial = emptyList())
    val focusSessions by dao.observeFocusSessions(todayRange.first, todayRange.second)
        .collectAsState(initial = emptyList())
    val activeFocusSession by focusDao.observeActiveSession().collectAsState(initial = null)
    val settings by dao.observeSettings().collectAsState(initial = null)

    val context = LocalContext.current
    val tabs = NativeTab.values().filterNot { it == NativeTab.FOCUS || it == NativeTab.APPS }
    var selectedTab by remember {
        mutableStateOf(
            when {
                startOnStatistics -> NativeTab.STATISTICS
                startOnFocus -> NativeTab.FOCUS
                startOnApps || startOnWebsiteBlock -> NativeTab.APPS
                else -> NativeTab.DASHBOARD
            }
        )
    }
    var selectedApp by remember { mutableStateOf<String?>(null) }
    val selectedAppUsage by dao.observeAppUsage(
        selectedApp ?: "",
        selectedStatsDayRange.first,
        selectedStatsDayRange.second
    ).collectAsState(initial = emptyList())

    NativeMindfulTheme(settings = settings, forceDopamineDietTheme = forceDopamineDietTheme) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                if (selectedTab != NativeTab.DASHBOARD) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        if (!showInternalNavigation && onStandaloneBack != null && !startOnWebsiteBlock) {
                            TextButton(onClick = onStandaloneBack) {
                                Text("‹", style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                        Text(
                            text = when (selectedTab) {
                                NativeTab.STATISTICS -> "Your statistics"
                                NativeTab.FOCUS -> "Focus"
                                NativeTab.APPS -> if (startOnWebsiteBlock) "Website blocking" else "App blocking"
                                NativeTab.DASHBOARD -> ""
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            bottomBar = {
                if (showInternalNavigation) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Text(tab.symbol, style = MaterialTheme.typography.titleLarge) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            when (selectedTab) {
                NativeTab.DASHBOARD -> DashboardContent(
                    modifier = Modifier.padding(padding),
                    totalScreenTime = usage.sumOf { it.totalScreenTime },
                    restrictedApps = restrictions.size,
                    focusSessions = focusSessions.size,
                    onOpenFocus = { selectedTab = NativeTab.FOCUS },
                    onOpenApps = {
                        selectedApp = null
                        selectedTab = NativeTab.APPS
                    }
                )
                NativeTab.STATISTICS -> StatisticsContent(
                    modifier = Modifier.padding(padding),
                    usage = selectedStatsUsage,
                    dailyUsage = weeklyUsage,
                    weekStartEpochMillis = weekRange.first,
                    selectedDateEpochMillis = selectedStatsDayRange.first,
                    weekOffset = weekOffset,
                    onWeekOffsetChange = { requestedOffset ->
                        val nextOffset = requestedOffset.coerceAtMost(0)
                        val weekDelta = nextOffset - weekOffset
                        weekOffset = nextOffset
                        selectedStatsDateEpochMillis = Calendar.getInstance().apply {
                            timeInMillis = selectedStatsDateEpochMillis
                            add(Calendar.DAY_OF_YEAR, weekDelta * 7)
                        }.timeInMillis
                    },
                    onSelectDay = { selectedStatsDateEpochMillis = it },
                    onSelectApp = {
                        selectedApp = it
                        selectedTab = NativeTab.APPS
                    }
                )
                NativeTab.FOCUS -> FocusContent(
                    modifier = Modifier.padding(padding),
                    currentStreak = focusMode?.currentStreak ?: 0L,
                    longestStreak = focusMode?.longestStreak ?: 0L,
                    sessionsToday = focusSessions.size,
                    profiles = focusProfiles,
                    sessions = focusSessions,
                    activeSession = activeFocusSession,
                    onStartFocus = onStartFocus,
                    onFinishFocus = onFinishFocus,
                    onSaveProfile = onSaveFocusProfile,
                    onSaveReflection = onSaveFocusReflection,
                    onBackToHome = { selectedTab = NativeTab.DASHBOARD }
                )
                NativeTab.APPS -> if (startOnWebsiteBlock) {
                    WebsiteBlockContent(
                        modifier = Modifier.padding(padding),
                        wellbeing = wellbeing,
                        onSaveWellbeing = onSaveWellbeing
                    )
                } else if (selectedApp == null) {
                    AppsContent(
                        modifier = Modifier.padding(padding),
                        restrictions = restrictions,
                        groups = groups,
                        wellbeing = wellbeing,
                        onSelectApp = { selectedApp = it },
                        onSaveRestrictionGroup = onSaveRestrictionGroup,
                        onDeleteRestrictionGroup = onDeleteRestrictionGroup,
                        onSaveWellbeing = onSaveWellbeing
                    )
                } else {
                    AppDetailContent(
                        modifier = Modifier.padding(padding),
                        packageName = selectedApp!!,
                        isImportantSystemApp = selectedApp in protectedSystemPackageNames(context),
                        restriction = restrictions.firstOrNull { it.appPackage == selectedApp },
                        groups = groups,
                        onBack = { selectedApp = null },
                        onSaveRestriction = onSaveAppRestriction,
                        onDeleteRestriction = onDeleteAppRestriction
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    modifier: Modifier,
    totalScreenTime: Long,
    restrictedApps: Int,
    focusSessions: Int,
    onOpenFocus: () -> Unit,
    onOpenApps: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            MetricCard(
                title = stringResource(R.string.dashboard_screen_time_today),
                value = formatDuration(totalScreenTime),
                supporting = stringResource(R.string.dashboard_recorded_usage)
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f).clickable(onClick = onOpenApps),
                    title = stringResource(R.string.dashboard_restricted_apps),
                    value = restrictedApps.toString(),
                    supporting = stringResource(R.string.dashboard_app_rules)
                )
                MetricCard(
                    modifier = Modifier.weight(1f).clickable(onClick = onOpenFocus),
                    title = stringResource(R.string.dashboard_focus_sessions),
                    value = focusSessions.toString(),
                    supporting = stringResource(R.string.dashboard_today)
                )
            }
        }
    }
}

@Composable
private fun StatisticsContent(
    modifier: Modifier,
    usage: List<NativeAppUsageSummary>,
    dailyUsage: List<NativeDailyUsageSummary>,
    weekStartEpochMillis: Long,
    selectedDateEpochMillis: Long,
    weekOffset: Int,
    onWeekOffsetChange: (Int) -> Unit,
    onSelectDay: (Long) -> Unit,
    onSelectApp: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedMetric by remember { mutableStateOf("screen") }
    var includeAllApps by remember { mutableStateOf(false) }
    val installedPackages = remember(context) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
    }
    val statsRows = remember(usage, installedPackages, selectedMetric, includeAllApps) {
        buildNativeStatisticsRows(usage, installedPackages, selectedMetric, includeAllApps)
    }
    fun appMetric(item: NativeAppUsageSummary): Long = when (selectedMetric) {
        "network" -> item.totalMobileData + item.totalWifiData
        else -> item.totalScreenTime
    }
    fun dayMetric(item: NativeDailyUsageSummary): Long = when (selectedMetric) {
        "network" -> item.totalMobileData + item.totalWifiData
        else -> item.totalScreenTime
    }
    fun displayMetric(value: Long): String =
        if (selectedMetric == "network") formatDataUsage(value) else formatDuration(value)
    val sortedUsage = if (selectedMetric == "alphabetic") {
        statsRows.sortedBy { nativeAppLabel(context, it.packageName).lowercase(Locale.ROOT) }
    } else {
        statsRows.sortedByDescending { appMetric(it) }
    }
    val weekDays = remember(dailyUsage, weekStartEpochMillis) {
        val recordedDays = dailyUsage.associateBy { it.dateEpochMillis }
        (0..6).map { dayIndex ->
            val dayStart = Calendar.getInstance().apply {
                timeInMillis = weekStartEpochMillis
                add(Calendar.DAY_OF_YEAR, dayIndex)
            }.timeInMillis
            recordedDays[dayStart] ?: NativeDailyUsageSummary(
                dateEpochMillis = dayStart,
                totalScreenTime = 0L,
                totalMobileData = 0L,
                totalWifiData = 0L
            )
        }
    }
    val maxDailyUsage = weekDays.maxOfOrNull { dayMetric(it) }?.coerceAtLeast(1L) ?: 1L

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Usage", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onWeekOffsetChange(weekOffset - 1) }) {
                    Text("Previous week")
                }
                Text(
                    when (weekOffset) {
                        0 -> "This week"
                        -1 -> "Last week"
                        else -> "${-weekOffset} weeks ago"
                    },
                    fontWeight = FontWeight.Medium
                )
                TextButton(
                    onClick = { onWeekOffsetChange(weekOffset + 1) },
                    enabled = weekOffset < 0
                ) {
                    Text("Next week")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "screen" to "Screen time",
                    "network" to "Network data",
                    "alphabetic" to "A–Z"
                ).forEach { (metric, label) ->
                    TextButton(onClick = { selectedMetric = metric }) {
                        Text(if (selectedMetric == metric) "✓ $label" else label)
                    }
                }
            }
        }
        if (dailyUsage.isEmpty()) {
            item { InfoCard("No weekly records", "Your daily usage chart will appear when usage records are available.") }
        }
        items(weekDays, key = { it.dateEpochMillis }) { day ->
            Card(
                modifier = Modifier.clickable { onSelectDay(day.dateEpochMillis) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (day.dateEpochMillis == selectedDateEpochMillis) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                                .format(java.util.Date(day.dateEpochMillis))
                        )
                        Text(displayMetric(dayMetric(day)), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = (dayMetric(day).toFloat() / maxDailyUsage.toFloat()).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
            Text(
                "Most used apps — " + java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                    .format(java.util.Date(selectedDateEpochMillis)),
                style = MaterialTheme.typography.titleLarge
            )
        }
        if (sortedUsage.isEmpty()) {
            item {
                InfoCard(
                    title = "No installed apps",
                    body = "No launchable apps were found on this device."
                )
            }
        } else {
            items(sortedUsage, key = { it.packageName }) { app ->
                Card(
                    modifier = Modifier.clickable { onSelectApp(app.packageName) },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(nativeAppLabel(context, app.packageName), fontWeight = FontWeight.Medium)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(displayMetric(appMetric(app)), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (!includeAllApps && selectedMetric != "alphabetic") {
            item {
                TextButton(
                    onClick = { includeAllApps = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Show all apps") }
            }
        }
    }
}

@Composable
private fun FocusContent(
    modifier: Modifier,
    currentStreak: Long,
    longestStreak: Long,
    sessionsToday: Int,
    profiles: List<NativeFocusProfileEntity>,
    sessions: List<NativeFocusSessionEntity>,
    activeSession: NativeFocusSessionEntity?,
    onStartFocus: (Long) -> Unit,
    onFinishFocus: (Boolean) -> Unit,
    onSaveProfile: (NativeFocusProfileEntity) -> Unit,
    onSaveReflection: (String) -> Unit,
    onBackToHome: () -> Unit
) {
    var selectedType by remember(profiles) {
        mutableStateOf(profiles.firstOrNull()?.sessionType ?: 0L)
    }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var editorVisible by remember { mutableStateOf(false) }
    var activeSessionViewVisible by remember(activeSession?.id) { mutableStateOf(activeSession != null) }
    var completedSession by remember { mutableStateOf<NativeFocusSessionEntity?>(null) }
    var reflectionDraft by remember(activeSession?.id, activeSession?.reflection) {
        mutableStateOf(activeSession?.reflection.orEmpty())
    }
    val context = LocalContext.current
    val launchableApps = remember(context) { loadRestrictableLaunchableApps(context) }

    LaunchedEffect(activeSession?.id) {
        if (activeSession != null) completedSession = null
        while (activeSession != null) {
            nowMillis = System.currentTimeMillis()
            val elapsed = ((nowMillis - activeSession.startDateTimeEpochMillis) / 1_000L)
                .coerceAtLeast(0L)
            if (activeSession.durationSecs > 0L && elapsed >= activeSession.durationSecs) {
                completedSession = completedFocusSessionSnapshot(activeSession, nowMillis)
                onFinishFocus(true)
                break
            }
            delay(1_000L)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { TextButton(onClick = onBackToHome) { Text("Back to Home") } }
        item { MetricCard(title = stringResource(R.string.focus_current_streak), value = currentStreak.toString(), supporting = stringResource(R.string.focus_days)) }
        item { MetricCard(title = stringResource(R.string.focus_longest_streak), value = longestStreak.toString(), supporting = stringResource(R.string.focus_days)) }
        item { MetricCard(title = stringResource(R.string.focus_sessions_today), value = sessionsToday.toString(), supporting = stringResource(R.string.focus_completed_recorded)) }
        item { Text(stringResource(R.string.focus_session), style = MaterialTheme.typography.titleLarge) }
        if (activeSession != null) {
            val elapsed = ((nowMillis - activeSession.startDateTimeEpochMillis) / 1_000L).coerceAtLeast(0L)
            val remaining = (activeSession.durationSecs - elapsed).coerceAtLeast(0L)
            val activeProfile = profiles.firstOrNull { it.sessionType == activeSession.type }
            item {
                InfoCard(
                    title = stringResource(R.string.focus_active),
                    body = if (activeSession.durationSecs > 0L) {
                        "Elapsed: ${formatDuration(elapsed)}\nRemaining: ${formatDuration(remaining)}"
                    } else {
                        "Elapsed: ${formatDuration(elapsed)}\nOpen-ended session"
                    }
                )
            }
            item {
                TextButton(
                    onClick = { activeSessionViewVisible = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.focus_open_fullscreen)) }
            }
            item {
                OutlinedTextField(
                    value = reflectionDraft,
                    onValueChange = { reflectionDraft = it.take(2_000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.focus_reflection)) },
                    supportingText = { Text(stringResource(R.string.focus_reflection_hint)) },
                    minLines = 2,
                    maxLines = 4
                )
                TextButton(
                    onClick = { onSaveReflection(reflectionDraft) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.focus_save_reflection))
                }
            }
            if (activeSession.durationSecs == 0L) {
                item {
                    TextButton(onClick = {
                        completedSession = completedFocusSessionSnapshot(activeSession, nowMillis)
                        onFinishFocus(true)
                    }) { Text(stringResource(R.string.focus_complete)) }
                }
            }
            if (
                activeSession.durationSecs > 0L &&
                (activeProfile?.enforceSession != true || elapsed >= activeSession.durationSecs)
            ) {
                item { TextButton(onClick = { onFinishFocus(false) }) { Text(stringResource(R.string.focus_end_early)) } }
            }
        } else {
            if (profiles.isEmpty()) {
                item { InfoCard(stringResource(R.string.focus_default_profile), stringResource(R.string.focus_default_profile_help)) }
            } else {
                item { Text(stringResource(R.string.focus_choose_profile), style = MaterialTheme.typography.titleMedium) }
                items(profiles, key = { it.sessionType }) { profile ->
                    Card(
                        modifier = Modifier.clickable { selectedType = profile.sessionType },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedType == profile.sessionType) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                nativeFocusTypeLabel(profile.sessionType) +
                                    if (selectedType == profile.sessionType) stringResource(R.string.focus_selected) else "",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (profile.sessionDuration > 0L) stringResource(R.string.focus_duration, formatDuration(profile.sessionDuration))
                                else stringResource(R.string.focus_duration_open)
                            )
                            Text(
                                stringResource(R.string.focus_enforcement, stringResource(if (profile.enforceSession) R.string.focus_on else R.string.focus_off)) + " · " +
                                    stringResource(R.string.focus_dnd, stringResource(if (profile.shouldStartDnd) R.string.focus_on else R.string.focus_off))
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { editorVisible = true }) {
                        Text(if (profiles.any { it.sessionType == selectedType }) stringResource(R.string.focus_edit_profile) else stringResource(R.string.focus_configure_profile))
                    }
                    TextButton(onClick = { onStartFocus(selectedType) }) {
                        Text(stringResource(R.string.focus_start))
                    }
                }
            }
        }
        item { Text(stringResource(R.string.focus_todays_sessions), style = MaterialTheme.typography.titleMedium) }
        if (sessions.isEmpty()) {
            item { InfoCard(stringResource(R.string.focus_no_sessions), stringResource(R.string.focus_no_sessions_help)) }
        } else {
            items(sessions, key = { it.id }) { session ->
                InfoCard(
                    title = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                        .format(java.util.Date(session.startDateTimeEpochMillis)),
                    body = "${nativeFocusTypeLabel(session.type)}\nDuration: ${formatDuration(session.durationSecs)}\n" +
                        "Status: ${when (session.state) { 1L -> "Completed"; 2L -> "Ended early"; else -> "Active" }}\n" +
                        "Reflection: ${session.reflection.ifBlank { "—" }}"
                )
            }
        }
    }
    if (editorVisible && activeSession == null) {
        FocusProfileEditorDialog(
            initial = profiles.firstOrNull { it.sessionType == selectedType },
            initialType = selectedType,
            profiles = profiles,
            installedApps = launchableApps,
            onDismiss = { editorVisible = false },
            onSave = { profile ->
                onSaveProfile(profile)
                selectedType = profile.sessionType
                editorVisible = false
            }
        )
    }
    if (activeSession != null && activeSessionViewVisible) {
        val elapsed = ((nowMillis - activeSession.startDateTimeEpochMillis) / 1_000L)
            .coerceAtLeast(0L)
        NativeActiveSessionDialog(
            session = activeSession,
            elapsedSeconds = elapsed,
            enforceSession = profiles.firstOrNull { it.sessionType == activeSession.type }?.enforceSession == true,
            reflection = reflectionDraft,
            onReflectionChange = { reflectionDraft = it.take(2_000) },
            onSaveReflection = { onSaveReflection(reflectionDraft) },
            onFinishFocus = { successful ->
                if (successful) completedSession = completedFocusSessionSnapshot(activeSession, nowMillis)
                onFinishFocus(successful)
            },
            onDismiss = { activeSessionViewVisible = false }
        )
    }
    completedSession?.let { session ->
        NativeFocusCompletionDialog(
            session = session,
            onDismiss = { completedSession = null }
        )
    }
}


@Composable
private fun NativeActiveSessionDialog(
    session: NativeFocusSessionEntity,
    elapsedSeconds: Long,
    enforceSession: Boolean,
    reflection: String,
    onReflectionChange: (String) -> Unit,
    onSaveReflection: () -> Unit,
    onFinishFocus: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val finite = session.durationSecs > 0L
    val remaining = (session.durationSecs - elapsedSeconds).coerceAtLeast(0L)
    val progress = if (finite) {
        (remaining.toFloat() / session.durationSecs.toFloat()).coerceIn(0f, 1f)
    } else {
        ((elapsedSeconds % 3_600L).toFloat() / 3_600f).coerceIn(0f, 1f)
    }
    val quotes = listOf(
        "Stay with the task in front of you.",
        "Small, consistent effort builds meaningful progress.",
        "Protect this time for the work that matters.",
        "Finish this interval with intention."
    )
    val quoteIndex = if (finite) {
        ((progress * quotes.size).toInt() - 1).coerceIn(0, quotes.lastIndex)
    } else {
        ((elapsedSeconds / 900L) % quotes.size).toInt()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.focus_session), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.focus_back)) }
                }
                Text(
                    nativeFocusTypeLabel(session.type),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                NativeTimerProgressClock(progress = progress)
                Text(
                    if (finite) stringResource(R.string.focus_time_remaining) else stringResource(R.string.focus_time_focused),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatDuration(if (finite) remaining else elapsedSeconds),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (finite) "Elapsed: ${formatDuration(elapsedSeconds)} of ${formatDuration(session.durationSecs)}"
                    else "Open-ended session · elapsed ${formatDuration(elapsedSeconds)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                InfoCard(
                    title = stringResource(R.string.focus_keep_going),
                    body = quotes[quoteIndex]
                )
                OutlinedTextField(
                    value = reflection,
                    onValueChange = onReflectionChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.focus_reflection)) },
                    supportingText = { Text(stringResource(R.string.focus_reflection_saved)) },
                    minLines = 3,
                    maxLines = 5
                )
                TextButton(
                    onClick = onSaveReflection,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.focus_save_reflection)) }
                if (session.durationSecs == 0L) {
                    TextButton(
                        onClick = { onFinishFocus(true) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.focus_finish)) }
                } else if (!enforceSession || elapsedSeconds >= session.durationSecs) {
                    TextButton(
                        onClick = { onFinishFocus(false) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.focus_end_early)) }
                }
            }
        }
    }
}


@Composable
private fun NativeTimerProgressClock(progress: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val error = MaterialTheme.colorScheme.error
    Canvas(modifier = Modifier.size(220.dp)) {
        val radius = size.minDimension / 2f - 4.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val innerRadius = radius * 0.75f

        for (index in 0 until 60) {
            val angle = Math.toRadians(index * 6.0 - 90.0)
            val isMajor = index % 5 == 0
            val notchLength = if (isMajor) radius * 0.10f else radius * 0.03f
            val start = Offset(
                center.x + cos(angle).toFloat() * (radius - notchLength),
                center.y + sin(angle).toFloat() * (radius - notchLength)
            )
            val end = Offset(
                center.x + cos(angle).toFloat() * radius,
                center.y + sin(angle).toFloat() * radius
            )
            drawLine(
                color = primary.copy(alpha = if (isMajor) 0.9f else 0.35f),
                start = start,
                end = end,
                strokeWidth = if (isMajor) 3.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        drawCircle(
            color = primary.copy(alpha = 0.06f),
            radius = innerRadius,
            center = center
        )
        drawArc(
            color = primary,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = true,
            topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
            size = Size(innerRadius * 2f, innerRadius * 2f)
        )
        val pointerAngle = Math.toRadians(-90.0 + 360.0 * progress.coerceIn(0f, 1f))
        val pointerEnd = Offset(
            center.x + cos(pointerAngle).toFloat() * radius,
            center.y + sin(pointerAngle).toFloat() * radius
        )
        drawLine(
            color = error,
            start = center,
            end = pointerEnd,
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(color = surface, radius = radius * 0.15f, center = center)
        drawCircle(color = primary, radius = radius * 0.09f, center = center)
    }
}

@Composable
private fun NativeFocusCompletionDialog(
    session: NativeFocusSessionEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.focus_completed_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(nativeFocusTypeLabel(session.type), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.focus_focused_for, formatDuration(session.durationSecs)))
                Text(stringResource(R.string.focus_completion_reflect))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.focus_done)) }
        }
    )
}


private fun loadRestrictableLaunchableApps(context: Context): List<NativeLaunchableApp> {
    val packageManager = context.packageManager
    val protectedPackages = protectedSystemPackageNames(context)
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(launcherIntent, 0)
        .mapNotNull { resolveInfo ->
            val info = resolveInfo.activityInfo ?: return@mapNotNull null
            NativeLaunchableApp(
                packageName = info.packageName,
                label = resolveInfo.loadLabel(packageManager).toString(),
                isImportantSystemApp = info.packageName in protectedPackages
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.ROOT) }
}

private data class NativeLaunchableApp(
    val packageName: String,
    val label: String,
    val isImportantSystemApp: Boolean = false
)

private val nativeFocusTypes = listOf(
    0L to "Study", 1L to "Work", 2L to "Exercise", 3L to "Meditation",
    4L to "Creative writing", 5L to "Reading", 6L to "Programming",
    7L to "Chores", 8L to "Project planning", 9L to "Art and design",
    10L to "Language learning", 11L to "Music practice", 12L to "Self care",
    13L to "Brainstorming", 14L to "Skill development", 15L to "Research",
    16L to "Networking", 17L to "Cooking", 18L to "Sports training",
    19L to "Rest and relaxation", 20L to "Other"
)

internal fun completedFocusSessionSnapshot(
    session: NativeFocusSessionEntity,
    nowMillis: Long
): NativeFocusSessionEntity {
    val elapsed = ((nowMillis - session.startDateTimeEpochMillis) / 1_000L).coerceAtLeast(0L)
    val actualDuration = if (session.durationSecs > 0L) {
        elapsed.coerceAtMost(session.durationSecs)
    } else {
        elapsed
    }
    return session.copy(durationSecs = actualDuration)
}

private fun nativeFocusTypeLabel(type: Long): String =
    nativeFocusTypes.firstOrNull { it.first == type }?.second ?: "Other"


@Composable
private fun FocusProfileEditorDialog(
    initial: NativeFocusProfileEntity?,
    initialType: Long,
    profiles: List<NativeFocusProfileEntity>,
    installedApps: List<NativeLaunchableApp>,
    onDismiss: () -> Unit,
    onSave: (NativeFocusProfileEntity) -> Unit
) {
    var sessionType by remember(initial?.sessionType, initialType) {
        mutableStateOf(initial?.sessionType ?: initialType)
    }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var durationSeconds by remember(initial?.sessionDuration) {
        mutableStateOf((initial?.sessionDuration ?: 0L).toString())
    }
    var enforceSession by remember(initial?.enforceSession) {
        mutableStateOf(initial?.enforceSession ?: false)
    }
    var startDnd by remember(initial?.shouldStartDnd) {
        mutableStateOf(initial?.shouldStartDnd ?: false)
    }
    val context = LocalContext.current
    val protectedPackages = remember(context) { protectedSystemPackageNames(context) }
    var selectedApps by remember(initial?.distractingAppsJson) {
        mutableStateOf(jsonItems(initial?.distractingAppsJson ?: "[]").filterNot { it in protectedPackages }.toSet())
    }
    val durationValue = durationSeconds.toLongOrNull()
    val validationMessage = when {
        durationValue == null -> "Enter a valid duration in seconds."
        durationValue < 0L -> "Duration cannot be negative."
        durationValue > 31536000L -> "Duration cannot exceed one year."
        enforceSession && durationValue == 0L -> "Enforced sessions need a duration greater than zero."
        selectedApps.isEmpty() -> "Choose at least one distracting app so focus enforcement has an app list."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Focus profile") },
        text = {
            LazyColumn(
                modifier = Modifier.height(440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("Session type", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { typeMenuExpanded = true }) {
                        Text(nativeFocusTypes.firstOrNull { it.first == sessionType }?.second ?: "Other")
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        nativeFocusTypes.forEach { (type, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    sessionType = type
                                    val selectedProfile = profiles.firstOrNull { it.sessionType == type }
                                    durationSeconds = (selectedProfile?.sessionDuration ?: 0L).toString()
                                    enforceSession = selectedProfile?.enforceSession ?: false
                                    startDnd = selectedProfile?.shouldStartDnd ?: false
                                    selectedApps = jsonItems(selectedProfile?.distractingAppsJson ?: "[]")
                                        .filterNot { it in protectedPackages }.toSet()
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = durationSeconds,
                        onValueChange = { durationSeconds = it.filter(Char::isDigit) },
                        label = { Text("Session duration (seconds)") },
                        supportingText = { Text("Use 0 for an open-ended session. The limit is one year.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enforce session")
                            Text("Prevent ending a timed session early.", style = MaterialTheme.typography.bodySmall)
                        }
                        Checkbox(
                            checked = enforceSession,
                            onCheckedChange = { enforceSession = it }
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Do Not Disturb")
                            Text("Request DND while the focus session runs.", style = MaterialTheme.typography.bodySmall)
                        }
                        Checkbox(
                            checked = startDnd,
                            onCheckedChange = { startDnd = it }
                        )
                    }
                }
                item {
                    Text("Distracting apps", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Select apps that the focus session should restrict.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (installedApps.isEmpty()) {
                    item { Text("No launchable apps were found on this device.") }
                } else {
                    items(installedApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !app.isImportantSystemApp) {
                                selectedApps = if (app.packageName in selectedApps) {
                                    selectedApps - app.packageName
                                } else {
                                    selectedApps + app.packageName
                                }
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = app.packageName in selectedApps,
                                enabled = !app.isImportantSystemApp,
                                onCheckedChange = { checked ->
                                    selectedApps = if (checked) {
                                        selectedApps + app.packageName
                                    } else {
                                        selectedApps - app.packageName
                                    }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                if (app.isImportantSystemApp) {
                                    Text(
                                        "Essential system app — cannot be selected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                if (validationMessage != null) {
                    item {
                        Text(
                            validationMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = validationMessage == null,
                onClick = {
                    val seconds = durationValue ?: return@TextButton
                    val packageJson = JSONArray().apply {
                        selectedApps.sorted().forEach { put(it) }
                    }.toString()
                    onSave(
                        NativeFocusProfileEntity(
                            sessionType = sessionType,
                            sessionDuration = seconds,
                            enforceSession = enforceSession,
                            shouldStartDnd = startDnd,
                            distractingAppsJson = packageJson
                        )
                    )
                }
            ) { Text("Save profile") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AppsContent(
    modifier: Modifier,
    restrictions: List<com.mindful.android.nativeapp.data.local.entity.NativeAppRestrictionEntity>,
    groups: List<NativeRestrictionGroupEntity>,
    wellbeing: NativeWellbeingEntity?,
    onSelectApp: (String) -> Unit,
    onSaveRestrictionGroup: (NativeRestrictionGroupEntity) -> Unit,
    onDeleteRestrictionGroup: (Long) -> Unit,
    onSaveWellbeing: (NativeWellbeingEntity) -> Unit
) {
    val context = LocalContext.current
    val installedApps = remember(context) { loadRestrictableLaunchableApps(context) }
    val restrictionsByPackage = remember(restrictions) {
        restrictions.associateBy { it.appPackage }
    }
    var appSearchQuery by remember { mutableStateOf("") }
    val filteredInstalledApps = installedApps.filter { app ->
        app.label.contains(appSearchQuery, ignoreCase = true) ||
            app.packageName.contains(appSearchQuery, ignoreCase = true)
    }
    var groupDialogVisible by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<NativeRestrictionGroupEntity?>(null) }
    var wellbeingDialogVisible by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (restrictions.isEmpty()) {
            item {
                InfoCard(
                    title = "Blocked apps",
                    body = "No apps are blocked yet. Select an installed app below to set its blocking limit."
                )
            }
        }
        item {
            OutlinedTextField(
                value = appSearchQuery,
                onValueChange = { appSearchQuery = it.take(100) },
                label = { Text("Search installed apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (filteredInstalledApps.isEmpty()) {
            item {
                InfoCard("No matching apps", "Try a different app name or package search.")
            }
        } else {
            items(filteredInstalledApps.chunked(5)) { appRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    appRow.forEach { app ->
                        Column(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectApp(app.packageName) }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            AndroidView(
                                modifier = Modifier.size(48.dp),
                                factory = { viewContext ->
                                    ImageView(viewContext).apply {
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                        setPadding(2, 2, 2, 2)
                                    }
                                },
                                update = { imageView ->
                                    runCatching {
                                        imageView.setImageDrawable(
                                            context.packageManager.getApplicationIcon(app.packageName)
                                        )
                                    }.onFailure {
                                        imageView.setImageDrawable(null)
                                    }
                                }
                            )
                            Text(
                                app.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                minLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            val rule = restrictionsByPackage[app.packageName]
                            val hasActiveRule = rule != null &&
                                (rule.timerSec > 0L || rule.launchLimit > 0L ||
                                    groups.any { it.id == rule.associatedGroupId && it.timerSec > 0L })
                            if (hasActiveRule) {
                                Text(
                                    "Limited",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    repeat(5 - appRow.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

    }
    if (groupDialogVisible) {
        RestrictionGroupEditorDialog(
            initialGroup = editingGroup,
            groups = groups,
            onDismiss = { groupDialogVisible = false },
            onSave = { group ->
                onSaveRestrictionGroup(group)
                groupDialogVisible = false
            }
        )
    }
    if (wellbeingDialogVisible) {
        WellbeingEditorDialog(
            initial = wellbeing,
            onDismiss = { wellbeingDialogVisible = false },
            onSave = { updated ->
                onSaveWellbeing(updated)
                wellbeingDialogVisible = false
            }
        )
    }
}

@Composable
private fun WebsiteBlockContent(
    modifier: Modifier,
    wellbeing: NativeWellbeingEntity?,
    onSaveWellbeing: (NativeWellbeingEntity) -> Unit
) {
    var websiteInput by remember { mutableStateOf("") }
    var websiteError by remember { mutableStateOf<String?>(null) }
    val blockedSites = jsonItems(wellbeing?.blockedWebsitesJson ?: "[]")
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Website name", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = websiteInput,
                onValueChange = {
                    websiteInput = it.take(253)
                    websiteError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Enter website name or domain") },
                placeholder = { Text("example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                isError = websiteError != null,
                supportingText = websiteError?.let { error -> ({ Text(error) }) }
            )
            TextButton(
                onClick = {
                    val host = normalizeBlockedWebsiteHost(websiteInput)
                    when {
                        host == null -> websiteError = "Enter a valid website domain, such as example.com."
                        blockedSites.any { normalizeBlockedWebsiteHost(it) == host } ->
                            websiteError = "This website is already blocked."
                        else -> {
                            val current = wellbeing ?: NativeWellbeingEntity()
                            val updatedSites = (blockedSites.mapNotNull(::normalizeBlockedWebsiteHost) + host).distinct()
                            onSaveWellbeing(
                                current.copy(
                                    blockedWebsitesJson = JSONArray(updatedSites).toString()
                                )
                            )
                            websiteInput = ""
                            websiteError = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = websiteInput.isNotBlank()
            ) {
                Text("Block website")
            }
            if (websiteError != null) {
                Text(websiteError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Text(
                if (blockedSites.isEmpty()) "Blocked websites" else "Blocked websites (${blockedSites.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (blockedSites.isEmpty()) {
            item { InfoCard("No blocked websites", "Add a website above to block it in supported browsers.") }
        } else {
            items(blockedSites) { site ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(site, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        TextButton(onClick = {
                            val current = wellbeing ?: NativeWellbeingEntity()
                            val updatedSites = blockedSites.filterNot { it == site }
                            onSaveWellbeing(current.copy(blockedWebsitesJson = JSONArray(updatedSites).toString()))
                        }) { Text("Unblock") }
                    }
                }
            }
        }
        item {
            InfoCard(
                title = if (wellbeing?.blockNsfwSites == true) "Adult-site blocking is on" else "Adult-site blocking is off",
                body = "Adult-site blocking uses the separately saved adult-site list."
            )
        }
    }
}

private fun normalizeBlockedWebsiteHost(input: String): String? {
    return runCatching {
        val raw = input.trim().trimEnd('.')
        if (raw.isBlank() || raw.any { it.isWhitespace() }) return@runCatching null
        val withScheme = if (raw.contains("://")) raw else "https://$raw"
        val uri = java.net.URI(withScheme)
        var host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return@runCatching null
        while (host.startsWith("www.") || host.startsWith("mobile.") || host.startsWith("m.")) {
            host = when {
                host.startsWith("www.") -> host.removePrefix("www.")
                host.startsWith("mobile.") -> host.removePrefix("mobile.")
                else -> host.removePrefix("m.")
            }
        }
        if (host.length !in 3..253 || !host.contains('.')) return@runCatching null
        val labels = host.split('.')
        if (labels.any { label ->
                label.isBlank() || label.length > 63 ||
                    !label.first().isLetterOrDigit() || !label.last().isLetterOrDigit() ||
                    label.any { !it.isLetterOrDigit() && it != '-' }
            }
        ) return@runCatching null
        host
    }.getOrNull()
}

private fun PlatformFeatures.displayLabel(): String = when (this) {
    PlatformFeatures.INSTAGRAM_REELS -> "Instagram Reels"
    PlatformFeatures.INSTAGRAM_EXPLORE -> "Instagram Explore"
    PlatformFeatures.SNAPCHAT_SPOTLIGHT -> "Snapchat Spotlight"
    PlatformFeatures.SNAPCHAT_DISCOVER -> "Snapchat Discover"
    PlatformFeatures.FACEBOOK_REELS -> "Facebook Reels"
    PlatformFeatures.REDDIT_SHORTS -> "Reddit Shorts"
    PlatformFeatures.YOUTUBE_SHORTS -> "YouTube Shorts"
}

@Composable
private fun WellbeingEditorDialog(
    initial: NativeWellbeingEntity?,
    onDismiss: () -> Unit,
    onSave: (NativeWellbeingEntity) -> Unit
) {
    var blockNsfw by remember(initial?.id) { mutableStateOf(initial?.blockNsfwSites ?: false) }
    var shortsSeconds by remember(initial?.id) {
        mutableStateOf((initial?.allowedShortsTimeSec ?: 1800L).toString())
    }
    var blockedSites by remember(initial?.id) {
        mutableStateOf(jsonItems(initial?.blockedWebsitesJson ?: "[]").joinToString(", "))
    }
    var adultSites by remember(initial?.id) {
        mutableStateOf(jsonItems(initial?.nsfwWebsitesJson ?: "[]").joinToString(", "))
    }
    var blockedFeatures by remember(initial?.id) {
        mutableStateOf(
            jsonItems(initial?.blockedFeaturesJson ?: "[]")
                .mapNotNull(PlatformFeatures::fromName)
                .toSet()
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit wellbeing rules") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    TextButton(onClick = { blockNsfw = !blockNsfw }) {
                        Text(if (blockNsfw) "Adult-site blocking: ON" else "Adult-site blocking: OFF")
                    }
                }
                item {
                    OutlinedTextField(
                        value = shortsSeconds,
                        onValueChange = { shortsSeconds = it.filter(Char::isDigit) },
                        label = { Text("Allowed short-video time (seconds)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = blockedSites,
                        onValueChange = { blockedSites = it },
                        label = { Text("Blocked websites (comma-separated)") }
                    )
                }
                item {
                    OutlinedTextField(
                        value = adultSites,
                        onValueChange = { adultSites = it },
                        label = { Text("Adult-site list (comma-separated)") }
                    )
                }
                item {
                    Text(
                        "Short-form content to restrict",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                items(PlatformFeatures.values().toList()) { feature ->
                    val checked = feature in blockedFeatures
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                blockedFeatures = if (checked) {
                                    blockedFeatures - feature
                                } else {
                                    blockedFeatures + feature
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { selected ->
                                blockedFeatures = if (selected) {
                                    blockedFeatures + feature
                                } else {
                                    blockedFeatures - feature
                                }
                            }
                        )
                        Text(feature.displayLabel())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val seconds = shortsSeconds.toLongOrNull() ?: return@TextButton
                fun toJson(value: String): String = JSONArray(
                    value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                ).toString()
                onSave(
                    NativeWellbeingEntity(
                        id = initial?.id ?: 0,
                        allowedShortsTimeSec = seconds,
                        blockedFeaturesJson = JSONArray(
                            blockedFeatures.map { it.jsonName }.sorted()
                        ).toString(),
                        blockNsfwSites = blockNsfw,
                        blockedWebsitesJson = toJson(blockedSites),
                        nsfwWebsitesJson = toJson(adultSites)
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RestrictionGroupEditorDialog(
    initialGroup: NativeRestrictionGroupEntity?,
    groups: List<NativeRestrictionGroupEntity>,
    onDismiss: () -> Unit,
    onSave: (NativeRestrictionGroupEntity) -> Unit
) {
    val context = LocalContext.current
    val protectedPackages = remember(context) { protectedSystemPackageNames(context) }
    val launchableApps = remember(context) { loadRestrictableLaunchableApps(context) }
    val assignedToOtherGroups = remember(groups, initialGroup?.id) {
        groups.filter { it.id != initialGroup?.id }
            .flatMap { jsonItems(it.distractingAppsJson) }
            .toSet()
    }
    var name by remember(initialGroup?.id) { mutableStateOf(initialGroup?.groupName.orEmpty()) }
    var timer by remember(initialGroup?.id) { mutableStateOf((initialGroup?.timerSec ?: 0L).toString()) }
    var selectedApps by remember(initialGroup?.id) {
        mutableStateOf(jsonItems(initialGroup?.distractingAppsJson ?: "[]")
            .filterNot { it in protectedPackages }.toSet())
    }
    var pendingApps by remember(initialGroup?.id) { mutableStateOf(selectedApps) }
    var appPickerVisible by remember { mutableStateOf(false) }

    val selectableApps = launchableApps.filter { app ->
        app.isImportantSystemApp ||
            app.packageName !in assignedToOtherGroups ||
            app.packageName in selectedApps
    } + selectedApps.filter { selected ->
        launchableApps.none { it.packageName == selected } && selected !in protectedPackages
    }.map { NativeLaunchableApp(it, it) }

    val selectedLabels = selectedApps.sorted().take(3).joinToString { packageName ->
        launchableApps.firstOrNull { it.packageName == packageName }?.label ?: packageName
    } + if (selectedApps.size > 3) " + ${selectedApps.size - 3} more" else ""
    val timerSeconds = timer.toLongOrNull()
    val canSave = name.isNotBlank() && timerSeconds != null &&
        timerSeconds > 0L && selectedApps.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialGroup == null) "Create restriction group" else "Edit restriction group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timer,
                    onValueChange = { timer = it.filter(Char::isDigit) },
                    label = { Text("Daily timer (seconds)") },
                    supportingText = { Text("A group needs a timer greater than zero.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        pendingApps = selectedApps
                        appPickerVisible = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Select distracting apps (${selectedApps.size})") }
                Text(
                    selectedLabels.ifBlank { "No apps selected" },
                    style = MaterialTheme.typography.bodySmall
                )
                if (selectedApps.isEmpty()) {
                    Text("Select at least one app to create this group.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val packageJson = JSONArray().apply {
                        selectedApps.sorted().forEach { put(it) }
                    }.toString()
                    onSave(
                        NativeRestrictionGroupEntity(
                            id = initialGroup?.id ?: 0L,
                            groupName = name.trim(),
                            timerSec = timerSeconds ?: 0L,
                            distractingAppsJson = packageJson
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (appPickerVisible) {
        AlertDialog(
            onDismissRequest = { appPickerVisible = false },
            title = { Text("Choose distracting apps") },
            text = {
                if (selectableApps.isEmpty()) {
                    Text("No launchable apps are available to add.")
                } else {
                    LazyColumn(
                        modifier = Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(selectableApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !app.isImportantSystemApp) {
                                        pendingApps = if (app.packageName in pendingApps) {
                                            pendingApps - app.packageName
                                        } else {
                                            pendingApps + app.packageName
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = app.packageName in pendingApps,
                                    enabled = !app.isImportantSystemApp,
                                    onCheckedChange = { checked ->
                                        pendingApps = if (checked) {
                                            pendingApps + app.packageName
                                        } else {
                                            pendingApps - app.packageName
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                    if (app.isImportantSystemApp) {
                                        Text(
                                            "Essential system app — cannot be selected",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedApps = pendingApps
                        appPickerVisible = false
                    }
                ) { Text("Apply selection") }
            },
            dismissButton = {
                TextButton(onClick = { appPickerVisible = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AppDetailContent(
    modifier: Modifier,
    packageName: String,
    isImportantSystemApp: Boolean,
    restriction: NativeAppRestrictionEntity?,
    groups: List<NativeRestrictionGroupEntity>,
    onBack: () -> Unit,
    onSaveRestriction: (NativeAppRestrictionEntity) -> Unit,
    onDeleteRestriction: (String) -> Unit
) {
    var timerText by remember(packageName, restriction?.timerSec) {
        mutableStateOf((restriction?.timerSec ?: 0L).toString())
    }
    var launchLimitText by remember(packageName, restriction?.launchLimit) {
        mutableStateOf((restriction?.launchLimit ?: 0L).toString())
    }
    var reminderType by remember(packageName, restriction?.reminderType) {
        mutableStateOf(
            restriction?.reminderType?.takeIf {
                it in setOf("none", "toast", "notification", "modalSheet")
            } ?: "toast"
        )
    }
    var reminderMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val groupName = groups.firstOrNull { it.id == restriction?.associatedGroupId }?.groupName
        ?: "Not assigned"

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "‹",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 4.dp)
                )
                AndroidView(
                    modifier = Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)),
                    factory = { viewContext ->
                        ImageView(viewContext).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setPadding(3, 3, 3, 3)
                        }
                    },
                    update = { image ->
                        runCatching {
                            image.setImageDrawable(context.packageManager.getApplicationIcon(packageName))
                        }.onFailure {
                            image.setImageDrawable(null)
                        }
                    }
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        nativeAppLabel(context, packageName),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(packageName, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (isImportantSystemApp) {
            item {
                InfoCard(
                    title = "Protected system app",
                    body = "Restriction controls are disabled for the default launcher, dialer, Settings, and Mindful to protect essential phone functions."
                )
            }
        } else {
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Restriction settings", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = timerText,
                        onValueChange = { timerText = it.filter(Char::isDigit) },
                        label = { Text("Daily limit (seconds)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = launchLimitText,
                        onValueChange = { launchLimitText = it.filter(Char::isDigit) },
                        label = { Text("Daily launch limit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if ((timerText.toLongOrNull() ?: 0L) > 0L) {
                        Box {
                            TextButton(onClick = { reminderMenuExpanded = true }) {
                                Text("Usage reminder: ${nativeReminderLabel(reminderType)}")
                            }
                            DropdownMenu(
                                expanded = reminderMenuExpanded,
                                onDismissRequest = { reminderMenuExpanded = false }
                            ) {
                                listOf(
                                    "none" to "None",
                                    "toast" to "Toast",
                                    "notification" to "Notification",
                                    "modalSheet" to "Overlay"
                                ).forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            reminderType = value
                                            reminderMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Text("Restriction group: $groupName")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = (timerText.toLongOrNull() ?: 0L) > 0L ||
                                (launchLimitText.toLongOrNull() ?: 0L) > 0L ||
                                groups.any { it.id == restriction?.associatedGroupId && it.timerSec > 0L },
                            onClick = {
                                onSaveRestriction(
                                    NativeAppRestrictionEntity(
                                        appPackage = packageName,
                                        timerSec = timerText.toLongOrNull() ?: 0L,
                                        launchLimit = launchLimitText.toLongOrNull() ?: 0L,
                                        associatedGroupId = restriction?.associatedGroupId,
                                        reminderType = reminderType
                                    )
                                )
                            }
                        ) { Text("Save rule") }
                        if (restriction != null) {
                            TextButton(onClick = { onDeleteRestriction(packageName) }) {
                                Text("Remove rule")
                            }
                        }
                    }
                }
            }
        }
        }

    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun nativeAppLabel(context: Context, packageName: String): String = runCatching {
    @Suppress("DEPRECATION")
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    context.packageManager.getApplicationLabel(info).toString()
}.getOrDefault(packageName)

internal fun nativeGroupAppLabels(
    packageNames: List<String>,
    appLabelsByPackage: Map<String, String>
): String = packageNames
    .distinct()
    .map { packageName -> appLabelsByPackage[packageName] ?: packageName }
    .joinToString()
    .ifBlank { "None" }

private fun jsonItems(raw: String): List<String> = try {
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        array.optString(index).takeIf { it.isNotBlank() }
    }
} catch (_: Exception) {
    emptyList()
}

internal fun buildNativeStatisticsRows(
    usage: List<NativeAppUsageSummary>,
    installedPackageNames: List<String>,
    selectedMetric: String,
    includeAllApps: Boolean
): List<NativeAppUsageSummary> {
    val installed = installedPackageNames.distinct()
    val installedSet = installed.toSet()
    val usageByPackage = usage.associateBy { it.packageName }
    val allApps = installed.map { packageName ->
        usageByPackage[packageName] ?: NativeAppUsageSummary(
            packageName = packageName,
            totalScreenTime = 0L,
            totalMobileData = 0L,
            totalWifiData = 0L
        )
    } + usage.filterNot { it.packageName in installedSet }

    if (includeAllApps || selectedMetric == "alphabetic") return allApps
    val filtered = when (selectedMetric) {
        "network" -> allApps.filter { it.totalMobileData + it.totalWifiData > 0L }
        else -> allApps.filter { it.totalScreenTime > 0L }
    }
    return filtered.ifEmpty { allApps }
}

private fun nativeReminderLabel(value: String): String = when (value) {
    "none" -> "None"
    "notification" -> "Notification"
    "modalSheet" -> "Overlay"
    else -> "Toast"
}

private fun formatDataUsage(kilobytes: Long): String = when {
    kilobytes < 1_024L -> "${kilobytes.coerceAtLeast(0L)} KB"
    kilobytes < 1_048_576L -> "${kilobytes / 1_024L} MB"
    else -> String.format(Locale.ROOT, "%.2f GB", kilobytes / 1_048_576.0)
}

private fun formatDuration(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3600L
    val minutes = (safeSeconds % 3600L) / 60L
    val remainingSeconds = safeSeconds % 60L
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${remainingSeconds}s"
        else -> "${remainingSeconds}s"
    }
}

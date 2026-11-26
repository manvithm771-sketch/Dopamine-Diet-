package com.mindful.android.nativeapp

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mindful.android.nativeapp.data.local.NativeDatabaseProvider
import com.mindful.android.nativeapp.data.migration.NativeStartupCoordinator
import com.mindful.android.nativeapp.data.local.entity.NativeAppRestrictionEntity
import com.mindful.android.nativeapp.data.local.entity.NativeMindfulSettingsEntity
import com.mindful.android.nativeapp.ui.NativeOnboardingScreen
import com.mindful.android.nativeapp.ui.NativePermissionStatus
import com.mindful.android.nativeapp.ui.isNativeOnboardingReady
import com.mindful.android.services.accessibility.MindfulAccessibilityService
import com.mindful.android.utils.Utils
import com.mindful.android.nativeapp.restrictions.NativeRestrictionController
import com.mindful.android.nativeapp.data.focus.NativeFocusRepository
import com.mindful.android.nativeapp.focus.NativeFocusServiceController
import com.mindful.android.nativeapp.focus.NativeFocusServiceStartResult
import com.mindful.android.nativeapp.data.usage.NativeUsageRepository
import com.mindful.android.nativeapp.data.usage.NativeUsageRefreshResult
import com.mindful.android.nativeapp.ui.NativeHomeScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Production native Android entry point. The application is hosted by Kotlin and Jetpack Compose.
 */
open class MainActivity : ComponentActivity() {
    protected val database by lazy { NativeDatabaseProvider.get(applicationContext) }
    private var restrictionController: NativeRestrictionController? = null
    // 0 = no pending flow, 1 = waiting for Usage Access, 2 = waiting for Overlay access.
    private var pendingAppBlockingPermissionStep = 0
    private val focusServiceController by lazy { NativeFocusServiceController(applicationContext) }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshNativePermissionStatus() }
    private var onboardingStateLoaded by mutableStateOf(false)
    private var onboardingDone by mutableStateOf(false)
    private var startupError by mutableStateOf<String?>(null)
    private var permissionStatus by mutableStateOf(NativePermissionStatus())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.mindful.android.helpers.device.NotificationHelper.registerNotificationChannels(this)
        refreshNativePermissionStatus()
        loadNativeStartupState()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFB69AFF),
                    secondary = Color(0xFFD3C2FF),
                    background = Color(0xFF09090D),
                    surface = Color(0xFF17171F),
                    onBackground = Color(0xFFF7F5FC),
                    onSurface = Color(0xFFF7F5FC)
                )
            ) {
            if (!onboardingStateLoaded) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (startupError != null) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Mindful could not finish startup") },
                    text = {
                        Text(
                            "The app stopped before opening your data because recovery or migration " +
                                "could not be completed. Do not uninstall the app. Details: " +
                                (startupError ?: "Unknown startup error.")
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { loadNativeStartupState() }) { Text("Retry") }
                    },
                    dismissButton = {
                        TextButton(onClick = { finish() }) { Text("Close") }
                    }
                )
            } else if (!onboardingDone) {
                NativeOnboardingScreen(
                    permissions = permissionStatus,
                    onRequestUsageAccess = { openUsageAccessSettings() },
                    onRequestOverlayAccess = { requestOverlayAccess() },
                    onRequestExactAlarm = { requestExactAlarmAccess() },
                    onRequestBatteryOptimization = { requestBatteryOptimizationExemption() },
                    onRequestAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onRequestNotifications = { requestNotificationPermission() },
                    onFinish = { finishNativeOnboarding() }
                )
            } else {
                MainContent()
            }
            }
        }
    }

    /**
     * Screen content hook for the unified product host. Startup, migrations,
     * permissions, services and persistence remain owned by this activity.
     */
    @Composable
    protected open fun MainContent() {
        NativeHomeScreen(
            dao = database.nativeDashboardDao(),
            focusDao = database.nativeFocusDao(),
            onStartFocus = { sessionType -> startFocusSession(sessionType) },
            onFinishFocus = { successful -> finishFocusSession(successful) },
            onSaveFocusProfile = { profile -> saveFocusProfile(profile) },
            onSaveFocusReflection = { reflection -> saveFocusReflection(reflection) },
            onRequestOverlayAccess = { requestOverlayAccess() },
            onApplyRestrictions = { applySavedRestrictions() },
            onSaveAppRestriction = { saveAppRestriction(it) },
            onDeleteAppRestriction = { deleteAppRestriction(it) },
            onSaveRestrictionGroup = { saveRestrictionGroup(it) },
            onDeleteRestrictionGroup = { deleteRestrictionGroup(it) },
            onSaveWellbeing = { saveWellbeing(it) }
        )
    }

    private fun loadNativeStartupState() {
        onboardingStateLoaded = false
        startupError = null
        lifecycleScope.launch {
            try {
                // Import legacy database/preferences before the launcher reads the native database.
                NativeStartupCoordinator.prepare(applicationContext)
                val savedSettings = database.nativeDashboardDao().observeSettings().first()
                onboardingDone = savedSettings?.isOnboardingDone == true
                runCatching {
                    com.mindful.android.nativeapp.work.NativeUsageRefreshScheduler.schedule(this@MainActivity)
                }.onFailure { error ->
                    android.util.Log.w("Mindful", "Usage refresh scheduling unavailable", error)
                }
                refreshNativePermissionStatus()
                refreshTodayUsage(showResult = false)
                syncSavedRestrictions()
                resumeActiveFocusSession()
            } catch (error: Exception) {
                android.util.Log.e("Mindful", "Native startup recovery or migration failed", error)
                startupError = error.message ?: "Unknown startup error."
            } finally {
                onboardingStateLoaded = true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!onboardingStateLoaded || startupError != null) return
        refreshNativePermissionStatus()
        // Resume any permission flow started when the user saved an app-blocking rule.
        resumePendingAppBlockingPermissionFlow()
        // Recheck on return from Android's special Usage Access settings screen.
        resumeActiveFocusSession()
        refreshTodayUsage(showResult = false)
        if (pendingAppBlockingPermissionStep == 0) syncSavedRestrictions()
    }


    private fun resumeActiveFocusSession() {
        lifecycleScope.launch {
            runCatching {
                val repository = NativeFocusRepository(database.nativeFocusDao())
                repository.completeIfExpired()
                val active = database.nativeFocusDao().getActiveSession()
                if (active != null) {
                    val profile = database.nativeFocusDao().getProfile(active.type)
                    scheduleFocusCompletion(active)
                    startFocusService(active, profile)
                }
            }.onFailure { error ->
                android.util.Log.e("Mindful", "Could not restore the active focus session.", error)
            }
        }
    }

    private fun refreshNativePermissionStatus() {
        val exactAlarmsAllowed = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            true
        } else {
            (getSystemService(ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        }
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        permissionStatus = NativePermissionStatus(
            usageAccess = hasUsageAccess(),
            overlayAccess = Settings.canDrawOverlays(this),
            exactAlarms = exactAlarmsAllowed,
            batteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(packageName),
            accessibilityEnabled = Utils.isServiceRunning(this, MindfulAccessibilityService::class.java),
            notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:$packageName")))
    }

    private fun requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:$packageName"))
                )
            }.onFailure {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }.onFailure {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun finishNativeOnboarding() {
        if (!isNativeOnboardingReady(permissionStatus)) return
        lifecycleScope.launch {
            try {
                val current = database.nativeDashboardDao().observeSettings().first()
                    ?: NativeMindfulSettingsEntity()
                database.nativeDashboardDao().saveSettings(current.copy(isOnboardingDone = true))
                onboardingDone = true
            } catch (error: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    error.message ?: "Could not save setup completion.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    protected fun saveFocusProfile(profile: com.mindful.android.nativeapp.data.local.entity.NativeFocusProfileEntity) {
        lifecycleScope.launch {
            try {
                check(database.nativeFocusDao().getActiveSession() == null) {
                    "Finish the active focus session before editing its profiles."
                }
                database.nativeFocusDao().saveProfile(profile)
                Toast.makeText(
                    this@MainActivity,
                    "Focus profile saved.",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (error: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    error.message ?: "Could not save focus profile.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    protected fun saveAppRestriction(rule: NativeAppRestrictionEntity) {
        lifecycleScope.launch {
            try {
                database.nativeRestrictionDao().saveRestriction(rule)
                when {
                    !hasUsageAccess() -> {
                        pendingAppBlockingPermissionStep = 1
                        Toast.makeText(
                            this@MainActivity,
                            "Rule saved. Grant Usage Access to activate app blocking.",
                            Toast.LENGTH_LONG
                        ).show()
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    !Settings.canDrawOverlays(this@MainActivity) -> {
                        pendingAppBlockingPermissionStep = 2
                        Toast.makeText(
                            this@MainActivity,
                            "Rule saved. Allow display over other apps to activate blocking.",
                            Toast.LENGTH_LONG
                        ).show()
                        requestOverlayAccess()
                    }
                    else -> {
                        pendingAppBlockingPermissionStep = 0
                        Toast.makeText(
                            this@MainActivity,
                            "Rule saved. Applying app-blocking rules.",
                            Toast.LENGTH_SHORT
                        ).show()
                        syncSavedRestrictions()
                    }
                }
            } catch (error: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    error.message ?: "Could not save restriction rule.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    protected fun deleteAppRestriction(packageName: String) {
        lifecycleScope.launch {
            try {
                database.nativeRestrictionDao().deleteRestriction(packageName)
                Toast.makeText(this@MainActivity, "Restriction rule removed.", Toast.LENGTH_SHORT).show()
                syncSavedRestrictions()
            } catch (error: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    error.message ?: "Could not remove restriction rule.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    protected fun saveRestrictionGroup(group: com.mindful.android.nativeapp.data.local.entity.NativeRestrictionGroupEntity) {
        lifecycleScope.launch {
            try {
                val dao = database.nativeRestrictionDao()
                val previousGroup = if (group.id > 0L) dao.getRestrictionGroup(group.id) else null
                val previousPackages = parseGroupPackageNames(previousGroup?.distractingAppsJson ?: "[]")
                val selectedPackages = parseGroupPackageNames(group.distractingAppsJson)
                dao.saveRestrictionGroupWithApps(group, selectedPackages, previousPackages)
                Toast.makeText(this@MainActivity, "Restriction group saved.", Toast.LENGTH_SHORT).show()
                syncSavedRestrictions()
            } catch (error: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    error.message ?: "Could not save restriction group.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    protected fun saveWellbeing(settings: com.mindful.android.nativeapp.data.local.entity.NativeWellbeingEntity) {
        lifecycleScope.launch {
            try {
                database.nativeRestrictionDao().saveWellbeing(settings)
                Toast.makeText(
                    this@MainActivity,
                    "Wellbeing rules saved. Enforcement still requires device verification.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (error: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    error.message ?: "Could not save wellbeing rules.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    protected fun deleteRestrictionGroup(groupId: Long) {
        lifecycleScope.launch {
            try {
                val restrictionDao = database.nativeRestrictionDao()
                restrictionDao.deleteRestrictionGroupAndDetachApps(groupId)
                Toast.makeText(this@MainActivity, "Restriction group deleted.", Toast.LENGTH_SHORT).show()
                syncSavedRestrictions()
            } catch (error: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    error.message ?: "Could not delete restriction group.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    protected fun requestOverlayAccess() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    protected fun applySavedRestrictions() {
        if (!hasUsageAccess()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayAccess()
            return
        }
        lifecycleScope.launch {
            val dashboard = database.nativeDashboardDao()
            val apps = dashboard.observeAppRestrictions().first()
            val groups = dashboard.observeRestrictionGroups().first()
            val applied = getRestrictionController().apply(apps, groups)
            Toast.makeText(
                this@MainActivity,
                if (applied) "Saved restriction rules applied." else "Required permissions are missing.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun resumePendingAppBlockingPermissionFlow() {
        when (pendingAppBlockingPermissionStep) {
            1 -> {
                pendingAppBlockingPermissionStep = 0
                if (!hasUsageAccess()) {
                    Toast.makeText(
                        this,
                        "Usage Access is still disabled. App blocking is not active.",
                        Toast.LENGTH_LONG
                    ).show()
                } else if (!Settings.canDrawOverlays(this)) {
                    pendingAppBlockingPermissionStep = 2
                    Toast.makeText(
                        this,
                        "Now allow display over other apps to finish app-blocking setup.",
                        Toast.LENGTH_LONG
                    ).show()
                    requestOverlayAccess()
                } else {
                    Toast.makeText(this, "Permissions granted. Applying app-blocking rules.", Toast.LENGTH_SHORT).show()
                }
            }
            2 -> {
                pendingAppBlockingPermissionStep = 0
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(
                        this,
                        "Overlay access is still disabled. App blocking cannot cover restricted apps.",
                        Toast.LENGTH_LONG
                    ).show()
                } else if (!hasUsageAccess()) {
                    pendingAppBlockingPermissionStep = 1
                    Toast.makeText(
                        this,
                        "Now grant Usage Access to finish app-blocking setup.",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } else {
                    Toast.makeText(this, "Permissions granted. Applying app-blocking rules.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun syncSavedRestrictions() {
        if (!hasUsageAccess() || !Settings.canDrawOverlays(this)) return
        lifecycleScope.launch {
            val dashboard = database.nativeDashboardDao()
            val apps = dashboard.observeAppRestrictions().first()
            val groups = dashboard.observeRestrictionGroups().first()
            // Apply empty lists too: a started tracker service can outlive this Activity
            // and otherwise retain stale restriction rules after the last rule is deleted.
            getRestrictionController().apply(apps, groups)
        }
    }

    private fun getRestrictionController(): NativeRestrictionController {
        return restrictionController ?: NativeRestrictionController(applicationContext)
            .also { restrictionController = it }
    }

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        return appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun scheduleFocusCompletion(
        active: com.mindful.android.nativeapp.data.local.entity.NativeFocusSessionEntity
    ) {
        runCatching {
            com.mindful.android.nativeapp.work.NativeFocusCompletionScheduler.schedule(
                applicationContext,
                active.id,
                active.durationSecs,
                active.startDateTimeEpochMillis
            )
        }.onFailure { error ->
            android.util.Log.w("Mindful", "Focus completion scheduling unavailable", error)
        }
    }

    private fun startFocusService(
        active: com.mindful.android.nativeapp.data.local.entity.NativeFocusSessionEntity,
        profile: com.mindful.android.nativeapp.data.local.entity.NativeFocusProfileEntity?
    ) {
        when (focusServiceController.start(active, profile)) {
            NativeFocusServiceStartResult.REQUIRED_PERMISSIONS_MISSING ->
                Toast.makeText(
                    this,
                    "Grant Usage Access and Overlay access to block distracting apps.",
                    Toast.LENGTH_LONG
                ).show()
            NativeFocusServiceStartResult.DND_PERMISSION_MISSING ->
                Toast.makeText(
                    this,
                    "Grant Do Not Disturb access to use this focus profile.",
                    Toast.LENGTH_LONG
                ).show()
            NativeFocusServiceStartResult.NO_DISTRACTING_APPS,
            NativeFocusServiceStartResult.READY,
            NativeFocusServiceStartResult.STARTED -> Unit
        }
    }

    protected fun startFocusSession(sessionType: Long) {
        lifecycleScope.launch {
            try {
                val profile = database.nativeFocusDao().getProfile(sessionType)
                when (focusServiceController.validate(profile)) {
                    NativeFocusServiceStartResult.NO_DISTRACTING_APPS -> {
                        Toast.makeText(
                            this@MainActivity,
                            "Choose at least one distracting app in this focus profile first.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    NativeFocusServiceStartResult.REQUIRED_PERMISSIONS_MISSING -> {
                        Toast.makeText(
                            this@MainActivity,
                            "Grant Usage Access and Overlay access before starting focus.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    NativeFocusServiceStartResult.DND_PERMISSION_MISSING -> {
                        Toast.makeText(
                            this@MainActivity,
                            "Allow Do Not Disturb access in the next Android settings screen, then start focus again.",
                            Toast.LENGTH_LONG
                        ).show()
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        return@launch
                    }
                    NativeFocusServiceStartResult.READY,
                    NativeFocusServiceStartResult.STARTED -> Unit
                }

                val repository = NativeFocusRepository(database.nativeFocusDao())
                repository.start(sessionType)
                val active = database.nativeFocusDao().getActiveSession()
                if (active != null) {
                    scheduleFocusCompletion(active)
                    startFocusService(active, profile)
                }
                Toast.makeText(this@MainActivity, "Focus session started.", Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity, error.message ?: "Could not start focus session.", Toast.LENGTH_LONG).show()
            }
        }
    }

    protected fun saveFocusReflection(reflection: String) {
        lifecycleScope.launch {
            try {
                val saved = NativeFocusRepository(database.nativeFocusDao())
                    .updateActiveReflection(reflection)
                if (saved) {
                    Toast.makeText(
                        this@MainActivity,
                        "Focus reflection saved.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (error: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    error.message ?: "Could not save focus reflection.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    protected fun finishFocusSession(successful: Boolean) {
        lifecycleScope.launch {
            try {
                val active = database.nativeFocusDao().getActiveSession()
                val finished = NativeFocusRepository(database.nativeFocusDao()).finish(successful)
                if (finished) {
                    active?.let { session ->
                        runCatching {
                            com.mindful.android.nativeapp.work.NativeFocusCompletionScheduler.cancel(
                                applicationContext,
                                session.id
                            )
                        }.onFailure { error ->
                            android.util.Log.w("Mindful", "Focus completion cancellation unavailable", error)
                        }
                    }
                    focusServiceController.finish(successful)
                    Toast.makeText(
                        this@MainActivity,
                        if (successful) "Focus session completed." else "Focus session ended.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity, error.message ?: "Could not finish focus session.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        restrictionController?.close()
        restrictionController = null
        focusServiceController.close()
        super.onDestroy()
    }

    private suspend fun refresh52WeekHistoryIfNeeded() {
        if (!hasUsageAccess()) return
        val preferences = getSharedPreferences("native_usage_history", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastRefresh = preferences.getLong("last_52_week_refresh_v2", 0L)
        if (now - lastRefresh < 24L * 60L * 60L * 1_000L) return

        try {
            when (val result = NativeUsageRepository(
                applicationContext,
                database.nativeUsageDao()
            ).refreshHistory(52)) {
                is NativeUsageRefreshResult.Updated -> {
                    preferences.edit().putLong("last_52_week_refresh_v2", now).apply()
                    android.util.Log.i(
                        "Mindful",
                        "52-week usage history refreshed: ${result.rowsWritten} records"
                    )
                }
                NativeUsageRefreshResult.UsageAccessRequired -> Unit
            }
        } catch (error: Exception) {
            android.util.Log.w("Mindful", "52-week usage history refresh failed", error)
        }
    }

    private fun refreshTodayUsage(showResult: Boolean) {
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        lifecycleScope.launch {
            try {
                when (val result = NativeUsageRepository(
                    applicationContext,
                    database.nativeUsageDao()
                ).refreshDay(dayStart)) {
                    is NativeUsageRefreshResult.Updated -> {
                        if (showResult) Toast.makeText(
                            this@MainActivity,
                            "Usage refreshed: ${result.rowsWritten} records",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    NativeUsageRefreshResult.UsageAccessRequired -> {
                        if (showResult) Toast.makeText(
                            this@MainActivity,
                            "Grant Usage Access first.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                refresh52WeekHistoryIfNeeded()
            } catch (error: Exception) {
                if (showResult) Toast.makeText(
                    this@MainActivity,
                    "Usage refresh failed: ${error.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}


private fun parseGroupPackageNames(raw: String): List<String> = runCatching {
    val array = org.json.JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        array.optString(index).trim().takeIf { it.isNotEmpty() }
    }
}.getOrDefault(emptyList())

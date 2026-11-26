/*
 *
 *  *
 *  *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *  *
 *  *  * This source code is licensed under the GPL-2.0 license license found in the
 *  *  * LICENSE file in the root directory of this source tree.
 *  *
 *
 */
package com.mindful.android.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.mindful.android.AppConstants.FACEBOOK_PACKAGE
import com.mindful.android.AppConstants.INSTAGRAM_PACKAGE
import com.mindful.android.AppConstants.REDDIT_PACKAGE
import com.mindful.android.AppConstants.SNAPCHAT_PACKAGE
import com.mindful.android.AppConstants.YOUTUBE_CLIENT_PACKAGE_SUFFIX
import com.mindful.android.AppConstants.YOUTUBE_PACKAGE
import com.mindful.android.R
import com.mindful.android.enums.PlatformFeatures
import com.mindful.android.helpers.storage.SharedPrefsHelper
import com.mindful.android.models.Wellbeing
import com.mindful.android.nativeapp.data.local.NativeDatabaseProvider
import com.mindful.android.nativeapp.data.migration.NativeStartupCoordinator
import com.mindful.android.nativeapp.data.wellbeing.toNativeWellbeingModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mindful.android.receivers.DeviceAppsChangedReceiver
import com.mindful.android.utils.ThreadUtils
import com.mindful.android.utils.executors.Throttler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * An AccessibilityService that monitors app usage and blocks access to specified content based on user settings.
 */
class MindfulAccessibilityService : AccessibilityService(), OnSharedPreferenceChangeListener {
    companion object {
        private const val TAG = "Mindful.MindfulAccessibilityService"

        const val ACTION_PERFORM_HOME_PRESS = "com.mindful.android.action.performHomePress"

        // Set of desired events which will be processed
        private val desiredEvents = setOf(
            TYPE_WINDOWS_CHANGED,
            TYPE_WINDOW_STATE_CHANGED,
            TYPE_VIEW_SCROLLED
        )

        private val browserPackages = mutableSetOf<String>()
        private val shortsPlatformPackages = mutableSetOf<String>()
    }


    // Fixed thread pool for parallel event processing
    private val executorService: ExecutorService = Executors.newFixedThreadPool(4)
    private val throttler: Throttler = Throttler(500L)
    private val deviceAppsChangedReceiver: DeviceAppsChangedReceiver =
        DeviceAppsChangedReceiver(onAppsChanged = { refreshServiceConfig() })

    // Managers
    private lateinit var browserManager: BrowserManager
    private lateinit var shortsPlatformManager: ShortsPlatformManager
    private lateinit var trackingManager: TrackingManager

    @Volatile private var wellbeing = Wellbeing()
    @Volatile private var usingNativeRoomWellbeing = false
    @Volatile private var sharedPrefsListenerRegistered = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var nativeWellbeingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        trackingManager = TrackingManager(context = this)
        shortsPlatformManager = ShortsPlatformManager(
            context = this,
            blockedContentGoBack = this::goBackWithToast
        )
        browserManager = BrowserManager(
            context = this,
            shortsPlatformManager = shortsPlatformManager,
            blockedContentGoBack = this::goBackWithToast
        )

        // Follow the migration marker so native Room becomes authoritative only after import.
        serviceScope.launch {
            val nativeDatabase = runCatching {
                NativeStartupCoordinator.prepare(applicationContext)
                NativeDatabaseProvider.get(applicationContext)
            }.onFailure { error ->
                Log.e(TAG, "Native startup preparation failed; using legacy wellbeing settings.", error)
            }.getOrNull()
            if (nativeDatabase == null) {
                loadLegacyWellbeingSettings()
                return@launch
            }
            nativeDatabase.nativeDataDao().observeMigrationState().collect { migrationState ->
                if (migrationState != null) {
                    usingNativeRoomWellbeing = true
                    withContext(Dispatchers.Main) {
                        if (sharedPrefsListenerRegistered) {
                            SharedPrefsHelper.registerUnregisterListenerToListenablePrefs(this@MindfulAccessibilityService, false, this@MindfulAccessibilityService)
                            sharedPrefsListenerRegistered = false
                        }
                    }
                    nativeWellbeingJob?.cancel()
                    nativeWellbeingJob = launch {
                        nativeDatabase.nativeDashboardDao().observeWellbeing().collect { entity ->
                            wellbeing = toNativeWellbeingModel(entity)
                            refreshServiceConfig()
                        }
                    }
                } else {
                    usingNativeRoomWellbeing = false
                    nativeWellbeingJob?.cancel()
                    loadLegacyWellbeingSettings()
                }
            }
        }

        // Register listener for install and uninstall events
        deviceAppsChangedReceiver.register(this)
    }


    private suspend fun loadLegacyWellbeingSettings() {
        withContext(Dispatchers.Main) {
            if (!sharedPrefsListenerRegistered) {
                SharedPrefsHelper.registerUnregisterListenerToListenablePrefs(this@MindfulAccessibilityService, true, this@MindfulAccessibilityService)
                sharedPrefsListenerRegistered = true
            }
            wellbeing = SharedPrefsHelper.getSetWellBeingSettings(this@MindfulAccessibilityService, null)
            refreshServiceConfig()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PERFORM_HOME_PRESS -> {
                Log.d(TAG, "onStartCommand: Pressing home button")
                goBackWithToast(GLOBAL_ACTION_HOME)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onServiceConnected() {
        refreshServiceConfig()
        trackingManager.stopManualTracking()
        Log.d(TAG, "onCreate: Accessibility service started successfully")
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            // If not desired event or executor is shutdown, then just return
            if (!desiredEvents.contains(event.eventType) || executorService.isShutdown) return

            executorService.submit {
                // Determine package and event source node
                val eventPackageName = event.packageName.toString()
                val node = if (eventPackageName == REDDIT_PACKAGE) event.source
                else rootInActiveWindow ?: event.source

                node?.let {
                    // Broadcast event
                    trackingManager.onNewEvent("${it.packageName}")

                    // Only process if any of the content is blocked
                    if (shouldBlockContent()) {
                        processEventInBackground(
                            packageName = eventPackageName,
                            node = it,
                            wellBeing = wellbeing.copy()
                        )
                    }
                }
            }

        } catch (ignored: Exception) {
        }
    }

    /**
     * Processes accessibility event in background thread instead of main thread.
     *
     * @param packageName The package name of the app generating the event.
     * @param node        The accessibility node representing the UI element currently in focus.
     */
    private fun processEventInBackground(
        packageName: String,
        node: AccessibilityNodeInfo,
        wellBeing: Wellbeing,
    ) {
        try {
            when (packageName) {
                in shortsPlatformPackages ->
                    shortsPlatformManager.blockDistraction(packageName, node, wellBeing)

                in browserPackages ->
                    browserManager.blockDistraction(packageName, node, wellBeing)
            }

        } catch (e: Exception) {
            Log.e(
                TAG,
                "processEventInBackground: Failed to process accessibility event in background",
                e
            )
            SharedPrefsHelper.insertCrashLogToPrefs(this, e)
        }
    }


    /**
     * Determines whether content should be blocked based on the current settings.
     *
     * @return `true` if content should be blocked based on the current settings,
     * `false` otherwise.
     */
    private fun shouldBlockContent(): Boolean {
        return wellbeing.blockedFeatures.isNotEmpty() ||
                wellbeing.blockedWebsites.isNotEmpty() ||
                wellbeing.nsfwWebsites.isNotEmpty() ||
                wellbeing.blockNsfwSites
    }


    /**
     * Performs the back action and shows a toast message indicating that the content is blocked.
     */
    private fun goBackWithToast(customAction: Int? = null) {
        throttler.submit {
            ThreadUtils.runOnMainThread {
                // Perform the back action (can be done on background thread)
                performGlobalAction(customAction ?: GLOBAL_ACTION_BACK)

                // Post Toast to main thread
                Toast.makeText(
                    this@MindfulAccessibilityService,
                    getString(R.string.toast_blocked_content),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Updates the service info with the latest settings and registered packages.
     */
    private fun refreshServiceConfig() {
        try {
            // Using hashset to avoid duplicates
            browserPackages.clear()
            shortsPlatformPackages.clear()
            val pm = packageManager

            // Fetch installed browser packages
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
            pm.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL).forEach {
                browserPackages.add(it.activityInfo.packageName)
            }

            wellbeing.blockedFeatures.forEach { feature ->
                when (feature) {
                    PlatformFeatures.INSTAGRAM_REELS,
                    PlatformFeatures.INSTAGRAM_EXPLORE ->
                        shortsPlatformPackages.add(INSTAGRAM_PACKAGE)

                    PlatformFeatures.SNAPCHAT_SPOTLIGHT,
                    PlatformFeatures.SNAPCHAT_DISCOVER ->
                        shortsPlatformPackages.add(SNAPCHAT_PACKAGE)

                    PlatformFeatures.FACEBOOK_REELS ->
                        shortsPlatformPackages.add(FACEBOOK_PACKAGE)

                    PlatformFeatures.REDDIT_SHORTS ->
                        shortsPlatformPackages.add(REDDIT_PACKAGE)

                    PlatformFeatures.YOUTUBE_SHORTS -> {
                        shortsPlatformPackages.add(YOUTUBE_PACKAGE)
                        val youtubeIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com")
                        )
                        pm.queryIntentActivities(youtubeIntent, PackageManager.MATCH_ALL)
                            .filterNot { browserPackages.contains(it.activityInfo.packageName) }
                            .forEach { shortsPlatformPackages.add(it.activityInfo.packageName) }
                    }
                }
            }

            // Load nsfw website domains if needed
            if (wellbeing.blockNsfwSites) BrowserManager.initializeNsfwDomains()
            else BrowserManager.clearNsfwDomains()

            Log.d(
                TAG, "refreshServiceConfig: Accessibility service config updated successfully: " +
                        "\n settings: $wellbeing" +
                        "\n browsers: $browserPackages"
            )
        } catch (e: Exception) {
            Log.e(TAG, "refreshServiceInfo: Failed to refresh service info", e)
            SharedPrefsHelper.insertCrashLogToPrefs(this, e)
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, changedKey: String?) {
        changedKey?.let { key ->
            if (!usingNativeRoomWellbeing && key == SharedPrefsHelper.PREF_KEY_WELLBEING_SETTINGS) {
                Log.d(TAG, "OnSharedPrefsChanged: Key changed = $changedKey")
                wellbeing = SharedPrefsHelper.getSetWellBeingSettings(this, null)
                refreshServiceConfig()
            }
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        try {
            // Stop accepting events before flushing so the final persisted value cannot be
            // overwritten by a late accessibility callback.
            executorService.shutdownNow()
            if (::shortsPlatformManager.isInitialized) {
                runCatching { shortsPlatformManager.flushShortsScreenTime() }
                    .onFailure { Log.w(TAG, "Failed to flush short-form usage during service shutdown", it) }
            }
            trackingManager.startManualTracking()

            // Unregister prefs listener and receiver
            deviceAppsChangedReceiver.unRegister(this)
            if (sharedPrefsListenerRegistered) {
                SharedPrefsHelper.registerUnregisterListenerToListenablePrefs(this, false, this)
                sharedPrefsListenerRegistered = false
            }
            nativeWellbeingJob?.cancel()
            serviceScope.cancel()
        } catch (e: Exception) {
            // ignored
        }

        Log.d(TAG, "onDestroy: Accessibility service destroyed")
        super.onDestroy()
    }
}

package com.mindful.android.nativeapp.data.usage

import android.app.usage.NetworkStats
import com.mindful.android.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NativeUsageRepositoryTest {
    @Test
    fun mapsScreenTimeInSecondsAndNetworkUsageInKb() {
        val rows = buildNativeUsageRows(
            dayStartEpochMillis = 86_400_000L,
            launchableApps = listOf(
                NativeLaunchableApp("com.example.social", 10_001)
            ),
            screenUsageSeconds = mapOf("com.example.social" to 1_245L),
            mobileUsageKb = mapOf(10_001 to 320L),
            wifiUsageKb = mapOf(10_001 to 640L)
        )

        val app = rows.single { it.packageName == "com.example.social" }
        assertEquals(86_400_000L, app.dateEpochMillis)
        assertEquals(1_245L, app.screenTime)
        assertEquals(320L, app.mobileData)
        assertEquals(640L, app.wifiData)
    }

    @Test
    fun skipsUnusedLaunchableAppsAndDuplicatePackageEntries() {
        val rows = buildNativeUsageRows(
            dayStartEpochMillis = 1_000L,
            launchableApps = listOf(
                NativeLaunchableApp("com.example.used", 10_001),
                NativeLaunchableApp("com.example.used", 10_001),
                NativeLaunchableApp("com.example.unused", 10_002)
            ),
            screenUsageSeconds = mapOf("com.example.used" to 20L),
            mobileUsageKb = emptyMap(),
            wifiUsageKb = emptyMap()
        )

        assertEquals(1, rows.count { it.packageName == "com.example.used" })
        assertFalse(rows.any { it.packageName == "com.example.unused" })
    }

    @Test
    fun keepsTetheringAndRemovedNetworkBucketsSeparate() {
        val rows = buildNativeUsageRows(
            dayStartEpochMillis = 1_000L,
            launchableApps = emptyList(),
            screenUsageSeconds = emptyMap(),
            mobileUsageKb = mapOf(
                NetworkStats.Bucket.UID_TETHERING to 11L,
                NetworkStats.Bucket.UID_REMOVED to 22L
            ),
            wifiUsageKb = mapOf(
                NetworkStats.Bucket.UID_TETHERING to 33L,
                NetworkStats.Bucket.UID_REMOVED to 44L
            )
        )

        val tethering = rows.single { it.packageName == AppConstants.TETHERING_PACKAGE }
        val removed = rows.single { it.packageName == AppConstants.REMOVED_PACKAGE }
        assertEquals(11L, tethering.mobileData)
        assertEquals(33L, tethering.wifiData)
        assertEquals(22L, removed.mobileData)
        assertEquals(44L, removed.wifiData)
    }

    @Test
    fun clampsNegativeNetworkValuesWhenAnAppHasScreenUsage() {
        val rows = buildNativeUsageRows(
            dayStartEpochMillis = 1_000L,
            launchableApps = listOf(NativeLaunchableApp("com.example.app", 10_001)),
            screenUsageSeconds = mapOf("com.example.app" to 10L),
            mobileUsageKb = mapOf(10_001 to -2L),
            wifiUsageKb = mapOf(10_001 to -3L)
        )

        val app = rows.single { it.packageName == "com.example.app" }
        assertEquals(10L, app.screenTime)
        assertEquals(0L, app.mobileData)
        assertEquals(0L, app.wifiData)
    }
}

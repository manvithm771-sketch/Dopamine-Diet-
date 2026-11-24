package com.mindful.android.nativeapp.ui

import com.mindful.android.nativeapp.data.local.NativeAppUsageSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeStatisticsRowsTest {
    private val usage = listOf(
        NativeAppUsageSummary("com.example.screen", 120L, 0L, 0L),
        NativeAppUsageSummary("com.example.network", 0L, 200L, 300L),
        NativeAppUsageSummary("com.example.both", 60L, 100L, 50L)
    )
    private val installed = listOf(
        "com.example.screen",
        "com.example.network",
        "com.example.both",
        "com.example.unused"
    )

    @Test fun screenMetricExcludesAppsWithOnlyNetworkUsage() {
        val rows = buildNativeStatisticsRows(usage, installed, "screen", false)
        assertEquals(
            setOf("com.example.screen", "com.example.both"),
            rows.map { it.packageName }.toSet()
        )
    }

    @Test fun networkMetricExcludesAppsWithOnlyScreenUsage() {
        val rows = buildNativeStatisticsRows(usage, installed, "network", false)
        assertEquals(
            setOf("com.example.network", "com.example.both"),
            rows.map { it.packageName }.toSet()
        )
    }

    @Test fun alphabeticModeIncludesInstalledAppsWithZeroUsage() {
        val rows = buildNativeStatisticsRows(usage, installed, "alphabetic", false)
        assertEquals(installed.toSet(), rows.map { it.packageName }.toSet())
        assertEquals(0L, rows.first { it.packageName == "com.example.unused" }.totalScreenTime)
    }

    @Test fun showAllAppsIncludesZeroUsageAppsForSelectedMetric() {
        val rows = buildNativeStatisticsRows(usage, installed, "screen", true)
        assertEquals(installed.toSet(), rows.map { it.packageName }.toSet())
    }

    @Test fun emptyFilteredResultFallsBackToAllInstalledApps() {
        val rows = buildNativeStatisticsRows(emptyList(), installed, "network", false)
        assertEquals(installed.toSet(), rows.map { it.packageName }.toSet())
    }
}

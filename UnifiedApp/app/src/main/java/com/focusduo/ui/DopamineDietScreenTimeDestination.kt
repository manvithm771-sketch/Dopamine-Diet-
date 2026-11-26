package com.focusduo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.mindful.android.nativeapp.data.local.NativeDatabaseProvider
import com.mindful.android.nativeapp.ui.NativeHomeScreen

/**
 * Reuses Mindful's real usage database and statistics UI as the unified
 * Screen time destination. The inner Mindful tab bar is suppressed so the
 * product shell remains the only top-level navigation.
 */
@Composable
fun DopamineDietScreenTimeDestination() {
    val context = LocalContext.current
    val database = NativeDatabaseProvider.get(context)
    NativeHomeScreen(
        dao = database.nativeDashboardDao(),
        focusDao = database.nativeFocusDao(),
        startOnStatistics = true,
        showInternalNavigation = false
    )
}

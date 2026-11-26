package com.focusduo.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reeltracker.service.ReelAccessibilityService
import com.reeltracker.service.ReelServiceState
import com.reeltracker.service.ReelWidgetProvider
import com.reeltracker.ui.LocalPalette
import com.reeltracker.ui.Palette
import com.reeltracker.ui.TodayScreen
import com.reeltracker.ui.TodayViewModel
import com.reeltracker.ui.Tracking
import kotlinx.coroutines.delay
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Hosts the existing Dopamine Diet Scroll experience inside the unified product.
 * The original ViewModel, repository, accessibility tracking and export flow remain
 * the source of truth; this wrapper does not create a parallel tracking implementation.
 */
@Composable
fun DopamineDietScrollDestination() {
    val context = LocalContext.current
    val viewModel: TodayViewModel = viewModel()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val usageOverview by viewModel.usageOverview.collectAsStateWithLifecycle()
    val lastEventAt by viewModel.lastEventAt.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()

    var tracking by remember { mutableStateOf(readTrackingState(context)) }
    var is24h by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }

    LifecycleResumeEffect(Unit) {
        tracking = readTrackingState(context)
        is24h = DateFormat.is24HourFormat(context)
        ReelWidgetProvider.refresh(context)
        onPauseOrDispose { }
    }

    val palette = Palette(
        background = Color(0xFF08090E),
        card = Color(0xFF171923),
        textPrimary = Color(0xFFF7F5FC),
        textSecondary = Color(0xFFB3B2C5),
        textMuted = Color(0xFF817B90),
        accent = Color(0xFFAA83FF),
        good = Color(0xFF75D6A0)
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.accent,
                background = palette.background,
                surface = palette.card,
                onSurface = palette.textPrimary
            )
        ) {
            TodayScreen(
                now = rememberNowMillis(),
                stats = stats,
                usageOverview = usageOverview,
                lastEventAt = lastEventAt,
                tracking = tracking,
                is24h = is24h,
                exportMessage = exportMessage,
                onOpenAccessibility = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onExport = viewModel::exportCapture,
                onAddWidget = { requestBrainWidget(context) }
            )
        }
    }
}

@Composable
private fun rememberNowMillis(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L - value % 1_000L)
        }
    }
    return now
}

private fun readTrackingState(context: Context): Tracking {
    val service = ComponentName(context, ReelAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )?.split(':')?.any { ComponentName.unflattenFromString(it) == service } == true
    return when {
        !enabled -> Tracking.Off
        ReelServiceState.connected -> Tracking.On
        else -> Tracking.Stalled
    }
}

private fun requestBrainWidget(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, ReelWidgetProvider::class.java)
    val pinned = manager.isRequestPinAppWidgetSupported &&
        manager.requestPinAppWidget(provider, null, null)
    if (!pinned) {
        Toast.makeText(
            context,
            "Long-press your home screen, open Widgets, and add Dopamine Diet.",
            Toast.LENGTH_LONG
        ).show()
    }
}

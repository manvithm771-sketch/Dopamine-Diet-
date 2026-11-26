package com.focusduo.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindful.android.nativeapp.data.local.MindfulNativeDatabase
import com.mindful.android.nativeapp.data.local.entity.NativeAppRestrictionEntity
import com.mindful.android.nativeapp.data.local.entity.NativeFocusProfileEntity
import com.mindful.android.nativeapp.data.local.entity.NativeRestrictionGroupEntity
import com.mindful.android.nativeapp.data.local.entity.NativeWellbeingEntity
import com.mindful.android.nativeapp.ui.NativeHomeScreen

@Composable
fun DopamineDietSettingsDestination(
    database: MindfulNativeDatabase,
    onStartFocus: (Long) -> Unit,
    onFinishFocus: (Boolean) -> Unit,
    onSaveFocusProfile: (NativeFocusProfileEntity) -> Unit,
    onSaveFocusReflection: (String) -> Unit,
    onRequestOverlayAccess: () -> Unit,
    onApplyRestrictions: () -> Unit,
    onSaveAppRestriction: (NativeAppRestrictionEntity) -> Unit,
    onDeleteAppRestriction: (String) -> Unit,
    onSaveRestrictionGroup: (NativeRestrictionGroupEntity) -> Unit,
    onDeleteRestrictionGroup: (Long) -> Unit,
    onSaveWellbeing: (NativeWellbeingEntity) -> Unit
) {
    var openedTool by remember { mutableStateOf<String?>(null) }
    if (openedTool == "focus" || openedTool == "apps") {
        NativeHomeScreen(
            dao = database.nativeDashboardDao(),
            focusDao = database.nativeFocusDao(),
            onStartFocus = onStartFocus,
            onFinishFocus = onFinishFocus,
            onSaveFocusProfile = onSaveFocusProfile,
            onSaveFocusReflection = onSaveFocusReflection,
            onRequestOverlayAccess = onRequestOverlayAccess,
            onApplyRestrictions = onApplyRestrictions,
            onSaveAppRestriction = onSaveAppRestriction,
            onDeleteAppRestriction = onDeleteAppRestriction,
            onSaveRestrictionGroup = onSaveRestrictionGroup,
            onDeleteRestrictionGroup = onDeleteRestrictionGroup,
            onSaveWellbeing = onSaveWellbeing,
            startOnFocus = openedTool == "focus",
            startOnApps = openedTool == "apps",
            showInternalNavigation = false,
            onStandaloneBack = { openedTool = null }
        )
        return
    }

    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("YOUR APP", color = Color(0xFFB69AFF), style = MaterialTheme.typography.labelMedium)
                Text(
                    "Settings",
                    color = Color(0xFFF7F5FC),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Manage focus tools, app controls and Android permissions.",
                    color = Color(0xFFAAA4B7),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        item {
            SettingsCard(
                title = "Focus sessions",
                subtitle = "Configure focus profiles, sessions and reflections.",
                action = "Open",
                onClick = { openedTool = "focus" }
            )
        }
        item {
            SettingsCard(
                title = "App restrictions",
                subtitle = "Manage restricted apps, groups and usage rules.",
                action = "Manage",
                onClick = { openedTool = "apps" }
            )
        }
        item {
            Text("Android permissions", color = Color(0xFFF7F5FC), style = MaterialTheme.typography.titleLarge)
        }
        item {
            SettingsCard(
                title = "Accessibility service",
                subtitle = "Required for Reels and Shorts tracking and configured app controls.",
                action = "Open settings",
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )
        }
        item {
            SettingsCard(
                title = "Usage access",
                subtitle = "Allows Screen time to read device usage statistics.",
                action = "Open settings",
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            )
        }
        item {
            SettingsCard(
                title = "Display over other apps",
                subtitle = "Manage permission for focus overlays.",
                action = "Open settings",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            .setData(Uri.parse("package:${context.packageName}"))
                    )
                }
            )
        }
        item {
            SettingsCard(
                title = "App notifications",
                subtitle = "Manage Dopamine Diet notification permissions and channels.",
                action = "Open settings",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = Color(0xFFF7F5FC), style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = Color(0xFFAAA4B7), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onClick) { Text(action, color = Color(0xFFB69AFF)) }
        }
    }
}

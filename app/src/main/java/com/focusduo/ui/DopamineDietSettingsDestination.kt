package com.focusduo.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

private val SettingsBg = Color(0xFF08090E)
private val SettingsSurface = Color(0xFF171923)
private val SettingsText = Color(0xFFF7F5FC)
private val SettingsMuted = Color(0xFFAAAABD)
private val SettingsViolet = Color(0xFF9B6DFF)

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
            forceDopamineDietTheme = true,
            onStandaloneBack = { openedTool = null }
        )
        return
    }

    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SettingsBg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF5C38D8), Color(0xFF2D76D8)))), contentAlignment = Alignment.Center) {
                    Text("⚙", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                    Text("Settings", color = SettingsText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Customize your experience.", color = SettingsMuted, style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF35266C)), contentAlignment = Alignment.Center) {
                    Text("●", color = Color(0xFFD8C5FF), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable { openedTool = "focus" }, shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = SettingsSurface)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF7562D9), Color(0xFF42348F)))), contentAlignment = Alignment.Center) {
                        Text("●", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    }
                    Column(Modifier.weight(1f).padding(start = 13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Mindful You", color = SettingsText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Live better. Feel better.", color = SettingsMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", color = SettingsMuted, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = SettingsSurface)) {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    SettingsRow("▣", "App Limits", "Manage restricted apps and usage rules", Color(0xFF39CDA7)) { openedTool = "apps" }
                    SettingsRow("◉", "Focus Mode", "Profiles, sessions and reflections", Color(0xFF9674F5)) { openedTool = "focus" }
                    SettingsRow("♧", "Notifications", "Android notification settings", Color(0xFFF06B85)) {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                    SettingsRow("◐", "Appearance", "Dark", Color(0xFFE6A34A)) { }
                    SettingsRow("ⓘ", "Accessibility", "Reels and Shorts tracking service", Color(0xFF38A8F5)) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    SettingsRow("◉", "Usage Access", "Screen time statistics permission", Color(0xFF4A9CF4)) {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    SettingsRow("▣", "Overlay Access", "Permission for focus overlays", Color(0xFF9A70F5)) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(Uri.parse("package:${context.packageName}")))
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = SettingsSurface)) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("♥", color = Color(0xFFF16F91), style = MaterialTheme.typography.titleLarge)
                    Text("  Thank you for using Dopamine Diet", color = SettingsText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(symbol: String, title: String, subtitle: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(symbol, color = tint, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = SettingsText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, color = SettingsMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text("›", color = SettingsMuted, style = MaterialTheme.typography.titleLarge)
    }
}

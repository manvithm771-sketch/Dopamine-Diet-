package com.focusduo.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindful.android.nativeapp.data.local.MindfulNativeDatabase
import com.mindful.android.nativeapp.data.local.entity.NativeAppRestrictionEntity
import com.mindful.android.nativeapp.data.local.entity.NativeFocusProfileEntity
import com.mindful.android.nativeapp.data.local.entity.NativeRestrictionGroupEntity
import com.mindful.android.nativeapp.data.local.entity.NativeWellbeingEntity
import com.mindful.android.nativeapp.ui.NativeHomeScreen

private val SettingsBg = Color(0xFF000000)
private val SettingsSurface = Color(0xFF202024)
private val SettingsText = Color(0xFFF4F4F5)
private val SettingsMuted = Color(0xFF929297)
private val SettingsDivider = Color(0xFF29292E)
private val SettingsOrange = Color(0xFFFF6900)
private val SettingsSwitchTrack = Color(0xFF35353B)

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
    var showGeneralSettings by remember { mutableStateOf(false) }

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
    if (showGeneralSettings) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(SettingsBg),
            contentPadding = PaddingValues(start = 10.dp, top = 18.dp, end = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showGeneralSettings = false }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("‹", color = SettingsMuted, fontSize = 32.sp)
                    Text("General settings", color = SettingsText, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
                }
            }
            item {
                SettingsGroup {
                    SettingsItem("▦", "App Limits", "Manage restricted apps and usage rules") { openedTool = "apps" }
                    SettingsItem("◉", "Focus Mode", "Profiles, sessions and reflections") { openedTool = "focus" }
                    SettingsItem("♧", "Notifications", "Android notification settings") {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                    SettingsItem("◐", "Appearance", "Dark") { }
                    SettingsItem("ⓘ", "Accessibility", "Reels and Shorts tracking service") {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    SettingsItem("◉", "Usage Access", "Screen time statistics permission") {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    SettingsItem("▣", "Overlay Access", "Permission for focus overlays") {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(Uri.parse("package:${context.packageName}")))
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SettingsBg),
        contentPadding = PaddingValues(start = 10.dp, top = 22.dp, end = 10.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 1.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(modifier = Modifier.padding(start = 20.dp)) {
                    Text("Hello, ", color = SettingsText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Manvith", color = Color(0xFF16B9D2), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(104.dp).clip(CircleShape).background(SettingsOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("M", color = Color(0xFFFFE9C9), fontSize = 58.sp, fontWeight = FontWeight.Normal)
                    }
                    Text("Manvith", color = SettingsText, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                    Text("17 years old", color = SettingsMuted, fontSize = 23.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp))
                }
            }
        }

        item {
            SettingsGroup {
                SettingsSwitchItem("♧", "Pause notifications", checked = true, onCheckedChange = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                })
                SettingsItem("☷", "General settings", null) { showGeneralSettings = true }
            }
        }

        item {
            SettingsGroup {
                SettingsSwitchItem("☾", "Dark mode", checked = false, onCheckedChange = { })
                SettingsItem("文", "Language", null) { }
                SettingsItem("♧", "My Contact", null) { }
            }
        }

        item {
            SettingsGroup {
                SettingsItem("ⓘ", "FAQ", null) { }
                SettingsItem("ⓘ", "Terms of service", null) { }
                SettingsItem("ⓘ", "User policy", null) { }
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsSurface)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsItem(
    symbol: String,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(if (subtitle == null) 88.dp else 76.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 37.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = SettingsMuted, fontSize = 24.sp, fontWeight = FontWeight.Normal)
        Column(modifier = Modifier.weight(1f).padding(start = 20.dp), verticalArrangement = Arrangement.Center) {
            Text(title, color = SettingsText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = SettingsMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Text("›", color = SettingsMuted, fontSize = 30.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun SettingsSwitchItem(
    symbol: String,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 37.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = SettingsMuted, fontSize = 24.sp, fontWeight = FontWeight.Normal)
        Text(title, color = SettingsText, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 20.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFB8F000),
                checkedBorderColor = Color(0xFFB8F000),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = SettingsSwitchTrack,
                uncheckedBorderColor = SettingsSwitchTrack
            )
        )
    }
}

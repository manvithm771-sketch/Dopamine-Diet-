package com.mindful.android.nativeapp.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Permission state refreshed whenever the Activity returns from Android Settings. */
data class NativePermissionStatus(
    val usageAccess: Boolean = false,
    val overlayAccess: Boolean = false,
    val exactAlarms: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
    val batteryOptimizationIgnored: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val notificationsAllowed: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
    val defaultHome: Boolean = false
)

/** Usage Access and overlays are always required; exact alarms are required on Android 12+. */
internal fun isNativeOnboardingReady(
    permissions: NativePermissionStatus,
    sdkInt: Int = Build.VERSION.SDK_INT
): Boolean = permissions.usageAccess && permissions.overlayAccess &&
    (sdkInt < Build.VERSION_CODES.S || permissions.exactAlarms) && permissions.defaultHome

@Composable
fun NativeOnboardingScreen(
    permissions: NativePermissionStatus,
    onRequestUsageAccess: () -> Unit,
    onRequestOverlayAccess: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestDefaultHome: () -> Unit,
    onFinish: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Set up Dopamine Diet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Grant the essential Android permissions and choose Dopamine Diet as your Home app so app limits can automatically hide and restore app icons. You can review these choices later in Android Settings.",
                style = MaterialTheme.typography.bodyMedium
            )
            PermissionCard(
                title = "Usage Access",
                description = "Required to read app usage and calculate your screen-time statistics.",
                granted = permissions.usageAccess,
                onGrant = onRequestUsageAccess
            )
            PermissionCard(
                title = "Display over other apps",
                description = "Required to display Mindful's restriction screen over a distracting app.",
                granted = permissions.overlayAccess,
                onGrant = onRequestOverlayAccess
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionCard(
                    title = "Alarms and reminders",
                    description = "Required for reliable scheduled focus-session and daily reset alarms.",
                    granted = permissions.exactAlarms,
                    onGrant = onRequestExactAlarm
                )
            }
            PermissionCard(
                title = "Default Home app",
                description = "Required for automatic app-icon hiding. Choose Dopamine Diet as your Home app; apps disappear from this Home screen when their limits are reached and return when you remove their rules. Other launchers are not changed.",
                granted = permissions.defaultHome,
                onGrant = onRequestDefaultHome,
                optional = false,
                grantedLabel = "Selected",
                actionLabel = "Choose Dopamine Diet Home"
            )
            Spacer(Modifier.height(2.dp))
            Text("Optional permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            PermissionCard(
                title = "Battery optimization exemption",
                description = "Can improve reliability of long-running tracking. Android may still manage background work.",
                granted = permissions.batteryOptimizationIgnored,
                onGrant = onRequestBatteryOptimization,
                optional = true
            )
            PermissionCard(
                title = "Accessibility service",
                description = "Needed for supported website and in-app content filtering. Enable it only if you want those features.",
                granted = permissions.accessibilityEnabled,
                onGrant = onRequestAccessibility,
                optional = true
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    title = "Notifications",
                    description = "Recommended so focus-session updates and usage reminders can appear in your notification shade.",
                    granted = permissions.notificationsAllowed,
                    onGrant = onRequestNotifications,
                    optional = true
                )
            }
            Button(
                onClick = onFinish,
                enabled = isNativeOnboardingReady(permissions),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isNativeOnboardingReady(permissions)) "Finish setup" else "Grant essential permissions first")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
    optional: Boolean = false,
    grantedLabel: String = "Allowed",
    actionLabel: String = "Open Android settings"
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(if (granted) grantedLabel else if (optional) "Optional" else "Required")
            }
            Text(description, style = MaterialTheme.typography.bodySmall)
            if (!granted) {
                OutlinedButton(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel)
                }
            }
        }
    }
}

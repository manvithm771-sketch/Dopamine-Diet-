package com.focusduo

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mindful.android.nativeapp.restrictions.BlockedAppVisibilityStore

private data class HomeLauncherApp(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

/**
 * Optional consumer-mode Home app. Blocked apps are omitted from this launcher's
 * app grid; the existing accessibility/overlay enforcement still protects
 * against launches through other system surfaces.
 */
class DopamineDietLauncherActivity : ComponentActivity() {
    private var refreshToken by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFAA83FF),
                    background = Color(0xFF08090E),
                    surface = Color(0xFF171923),
                    onBackground = Color(0xFFF7F5FC),
                    onSurface = Color(0xFFF7F5FC)
                )
            ) {
                val token = refreshToken
                val blocked = remember(token) {
                    BlockedAppVisibilityStore.blockedPackages(this)
                }
                val apps = remember(token, blocked) { loadVisibleApps(blocked) }
                val roleManager = getSystemService(RoleManager::class.java)
                val isDefaultHome = roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true

                Column(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF08090E))
                        .padding(horizontal = 18.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Dopamine Diet",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF7F5FC)
                    )
                    Text(
                        if (isDefaultHome) {
                            "Your Home screen. Apps disappear here when their limits are reached and return when their restriction rules are removed."
                        } else {
                            "Set Dopamine Diet as your default Home app to hide blocked apps from this launcher."
                        },
                        color = Color(0xFFAAAABD),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!isDefaultHome) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF171923))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Enable Dopamine Diet Home",
                                    color = Color(0xFFF7F5FC),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Android will ask you to choose your default Home app. You can switch back to your phone's original launcher in Android settings.",
                                    color = Color(0xFFAAAABD),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Button(
                                    onClick = {
                                        roleManager?.takeIf {
                                            it.isRoleAvailable(RoleManager.ROLE_HOME)
                                        }?.let {
                                            startActivity(it.createRequestRoleIntent(RoleManager.ROLE_HOME))
                                        }
                                    }
                                ) {
                                    Text("Set as default Home app")
                                }
                            }
                        }
                    }
                    Text(
                        "Apps",
                        color = Color(0xFFF7F5FC),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(apps, key = { it.packageName }) { app ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { launchApp(app.packageName) }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AndroidView(
                                    modifier = Modifier.size(48.dp),
                                    factory = { context ->
                                        ImageView(context).apply {
                                            scaleType = ImageView.ScaleType.FIT_CENTER
                                        }
                                    },
                                    update = { it.setImageDrawable(app.icon) }
                                )
                                Text(
                                    app.label,
                                    color = Color(0xFFF7F5FC),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshToken++
    }

    @Suppress("DEPRECATION")
    private fun loadVisibleApps(blockedPackages: Set<String>): List<HomeLauncherApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .asSequence()
            .map { resolve ->
                val packageName = resolve.activityInfo.packageName
                HomeLauncherApp(
                    packageName = packageName,
                    label = resolve.loadLabel(packageManager).toString(),
                    icon = resolve.loadIcon(packageManager)
                )
            }
            .filterNot { it.packageName in blockedPackages }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun launchApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
    }
}

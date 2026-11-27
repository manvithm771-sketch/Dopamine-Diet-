package com.focusduo.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.delay
import java.util.Locale

private val PhoneBlockBackground = Color(0xFF08090E)
private val PhoneBlockSurface = Color(0xFF171923)
private val PhoneBlockMuted = Color(0xFFAAAABD)
private val PhoneBlockAccent = Color(0xFFAA83FF)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PhoneBlockScreen(
    onBack: () -> Unit,
    onBlockFinished: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val durationOptions = listOf(
        15L * 60_000L to "15 min",
        30L * 60_000L to "30 min",
        60L * 60_000L to "1 hour",
        120L * 60_000L to "2 hours"
    )
    var selectedDuration by remember { mutableLongStateOf(30L * 60_000L) }
    var endElapsedRealtime by remember { mutableLongStateOf(0L) }
    var remainingMillis by remember { mutableLongStateOf(0L) }
    var pinRequestPending by remember { mutableStateOf(false) }
    var pinRequestMessage by remember { mutableStateOf<String?>(null) }
    val isActive = endElapsedRealtime > 0L

    DisposableEffect(activity, isActive) {
        val window = activity?.window
        if (isActive && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(pinRequestPending) {
        if (!pinRequestPending) return@LaunchedEffect
        val hostActivity = activity
        if (hostActivity == null) {
            pinRequestMessage = "Could not start screen pinning on this screen."
            pinRequestPending = false
            return@LaunchedEffect
        }

        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var enteredPinMode = false
        repeat(75) {
            val state = manager.lockTaskModeState
            if (state == ActivityManager.LOCK_TASK_MODE_PINNED ||
                state == ActivityManager.LOCK_TASK_MODE_LOCKED
            ) {
                enteredPinMode = true
                return@repeat
            }
            if (!enteredPinMode) delay(200L)
        }

        pinRequestPending = false
        if (enteredPinMode) {
            pinRequestMessage = null
            endElapsedRealtime = SystemClock.elapsedRealtime() + selectedDuration
            remainingMillis = selectedDuration
        } else {
            pinRequestMessage =
                "Screen pinning was not enabled. Approve the Android prompt and try again."
        }
    }

    LaunchedEffect(endElapsedRealtime) {
        if (endElapsedRealtime <= 0L) return@LaunchedEffect
        while (true) {
            val left = (endElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            remainingMillis = left
            if (left == 0L) {
                endElapsedRealtime = 0L
                activity?.stopLockTask()
                onBlockFinished()
                break
            }
            delay(250L)
        }
    }

    BackHandler(enabled = isActive) { }

    if (!isActive) {
        Column(
            modifier = Modifier.fillMaxSize().background(PhoneBlockBackground)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Phone Block", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Choose a duration. Android will ask you to approve screen pinning before the session begins.",
                color = PhoneBlockMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(28.dp))
            Surface(
                color = PhoneBlockSurface,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("BLOCK DURATION", color = PhoneBlockMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    durationOptions.chunked(2).forEach { rowOptions ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowOptions.forEach { (duration, label) ->
                                val selected = selectedDuration == duration
                                Button(
                                    onClick = { selectedDuration = duration },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) PhoneBlockAccent else Color(0xFF292B38),
                                        contentColor = if (selected) Color(0xFF17121F) else Color.White
                                    )
                                ) {
                                    Text(label, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            pinRequestMessage?.let { message ->
                Spacer(Modifier.height(14.dp))
                Text(message, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    pinRequestMessage = null
                    try {
                        activity?.startLockTask()
                        pinRequestPending = true
                    } catch (_: SecurityException) {
                        pinRequestMessage = "Android did not allow screen pinning. Check your device's screen-pinning setting."
                    } catch (_: IllegalStateException) {
                        pinRequestMessage = "Screen pinning could not start. Please try again."
                    }
                },
                enabled = !pinRequestPending,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PhoneBlockAccent, contentColor = Color(0xFF17121F))
            ) {
                Text(
                    if (pinRequestPending) "Waiting for Android approval…" else "Start Phone Block",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.TextButton(
                onClick = onBack,
                enabled = !pinRequestPending
            ) {
                Text("Back", color = PhoneBlockMuted)
            }
        }
    } else {
        val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val timerText = if (hours > 0L) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
        Box(
            modifier = Modifier.fillMaxSize().background(PhoneBlockBackground).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("PHONE BLOCK ACTIVE", color = PhoneBlockAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Take a break.", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(timerText, color = Color.White, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.SemiBold)
                Text("Android screen pinning is active for this session.", color = PhoneBlockMuted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        endElapsedRealtime = 0L
                        remainingMillis = 0L
                        activity?.stopLockTask()
                        onBlockFinished()
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF292B38), contentColor = Color.White)
                ) {
                    Text("Emergency · Exit Phone Block", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "You can also leave using Android's screen-pinning exit controls.",
                    color = PhoneBlockMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

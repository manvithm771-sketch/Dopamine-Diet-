package com.mindful.android.nativeapp.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.mindful.android.nativeapp.data.local.entity.NativeMindfulSettingsEntity

private val DopamineDarkColors = darkColorScheme(
    primary = Color(0xFFAA83FF),
    onPrimary = Color(0xFF160D27),
    secondary = Color(0xFF4D9DFF),
    onSecondary = Color(0xFF07182B),
    tertiary = Color(0xFF42DDA5),
    background = Color(0xFF08090E),
    surface = Color(0xFF171923),
    surfaceVariant = Color(0xFF222333),
    onBackground = Color(0xFFF7F5FC),
    onSurface = Color(0xFFF7F5FC),
    onSurfaceVariant = Color(0xFFB3B2C5),
    outline = Color(0xFF45465B)
)

@Composable
fun NativeMindfulTheme(
    settings: NativeMindfulSettingsEntity?,
    forceDopamineDietTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = if (forceDopamineDietTheme) true else when (settings?.themeMode ?: 0L) {
        1L -> false
        2L -> true
        else -> systemDark
    }
    val context = LocalContext.current
    val seed = if (forceDopamineDietTheme) Color(0xFFAA83FF) else nativeSeedColor(settings?.accentColor ?: "Indigo")
    val dynamicEnabled = !forceDopamineDietTheme && settings?.useDynamicColors == true &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseColors = when {
        forceDopamineDietTheme -> DopamineDarkColors
        dynamicEnabled && dark -> dynamicDarkColorScheme(context)
        dynamicEnabled -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = seed)
        else -> lightColorScheme(primary = seed)
    }
    val colors = if (!forceDopamineDietTheme && dark && settings?.useAmoledDark == true) {
        baseColors.copy(background = Color.Black, surface = Color.Black)
    } else {
        baseColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private fun nativeSeedColor(name: String): Color = when (name) {
    "Amber" -> Color(0xFFFFC107)
    "Blue" -> Color(0xFF2196F3)
    "Blue Grey" -> Color(0xFF607D8B)
    "Brown" -> Color(0xFF795548)
    "Cyan" -> Color(0xFF00BCD4)
    "Deep Orange" -> Color(0xFFFF5722)
    "Deep Purple" -> Color(0xFF673AB7)
    "Green" -> Color(0xFF4CAF50)
    "Grey" -> Color(0xFF9E9E9E)
    "Light Blue" -> Color(0xFF03A9F4)
    "Light Green" -> Color(0xFF8BC34A)
    "Lime" -> Color(0xFFCDDC39)
    "Orange" -> Color(0xFFFF9800)
    "Pink" -> Color(0xFFE91E63)
    "Purple" -> Color(0xFF9C27B0)
    "Red" -> Color(0xFFF44336)
    "Teal" -> Color(0xFF009688)
    "Yellow" -> Color(0xFFFFEB3B)
    else -> Color(0xFF3F51B5)
}

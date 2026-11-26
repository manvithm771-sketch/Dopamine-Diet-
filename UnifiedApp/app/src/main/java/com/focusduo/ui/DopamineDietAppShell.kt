package com.focusduo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Top-level destinations for the single Dopamine Diet application.
 * Keep destination ownership here; feature modules provide the actual screen content.
 */
enum class DopamineDestination(
    val title: String,
    val symbol: String,
    val description: String
) {
    HABITS("Habits", "◎", "Build routines that support your day"),
    SCROLL("Scroll", "◉", "Understand and manage short-form scrolling"),
    SCREEN_TIME("Screen time", "▥", "Review device usage and app activity"),
    SETTINGS("Settings", "⚙", "Manage your Dopamine Diet experience")
}

private val DopamineDietDarkColors = darkColorScheme(
    primary = Color(0xFFB7A2FF),
    onPrimary = Color(0xFF171320),
    secondary = Color(0xFFC4B5FD),
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFF5F5FA),
    surface = Color(0xFF15151D),
    onSurface = Color(0xFFF5F5FA),
    surfaceVariant = Color(0xFF20202A),
    onSurfaceVariant = Color(0xFFA7A7B5),
    outline = Color(0xFF30303A)
)

@Composable
fun DopamineDietTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DopamineDietDarkColors,
        content = content
    )
}

/**
 * Shared application shell. It deliberately accepts feature content as a slot so
 * Mindful and Dopamine Diet screens can be integrated without launching their
 * former activities or duplicating their data/business logic.
 */
@Composable
fun DopamineDietAppShell(
    selectedDestination: DopamineDestination,
    onDestinationSelected: (DopamineDestination) -> Unit,
    content: @Composable (DopamineDestination) -> Unit
) {
    DopamineDietTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Dopamine Diet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = selectedDestination.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        DopamineDestination.values().forEach { destination ->
                            NavigationBarItem(
                                selected = selectedDestination == destination,
                                onClick = { onDestinationSelected(destination) },
                                icon = {
                                    Text(
                                        text = destination.symbol,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                },
                                label = {
                                    Text(
                                        text = destination.title,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    content(selectedDestination)
                }
            }
        }
    }
}

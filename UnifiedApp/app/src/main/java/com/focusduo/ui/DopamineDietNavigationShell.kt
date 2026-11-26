package com.focusduo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The single navigation container for the Dopamine Diet product.
 *
 * Screen implementations are injected as slots so the existing Mindful and
 * Dopamine Diet features can be integrated without duplicating their data or
 * business logic inside the navigation layer.
 *
 * Habits is the initial destination, as agreed for the product.
 */
@Composable
fun DopamineDietNavigationShell(
    habits: @Composable () -> Unit,
    scroll: @Composable () -> Unit,
    screenTime: @Composable () -> Unit,
    settings: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDestination by remember { mutableIntStateOf(0) }

    val destinations = listOf(
        NavigationDestination("Habits", "◉"),
        NavigationDestination("Scroll", "⌁"),
        NavigationDestination("Screen time", "▥"),
        NavigationDestination("Settings", "⚙")
    )

    val content = when (selectedDestination) {
        0 -> habits
        1 -> scroll
        2 -> screenTime
        else -> settings
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF09090D),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF111117),
                contentColor = Color(0xFFF5F3FA),
                tonalElevation = 0.dp
            ) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedDestination == index,
                        onClick = { selectedDestination = index },
                        icon = {
                            Text(
                                text = destination.symbol,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        label = {
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFC4A7FF),
                            selectedTextColor = Color(0xFFC4A7FF),
                            indicatorColor = Color(0xFF292236),
                            unselectedIconColor = Color(0xFF85818F),
                            unselectedTextColor = Color(0xFF85818F)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF09090D))
                .padding(innerPadding)
        ) {
            content()
        }
    }
}

private data class NavigationDestination(
    val label: String,
    val symbol: String
)

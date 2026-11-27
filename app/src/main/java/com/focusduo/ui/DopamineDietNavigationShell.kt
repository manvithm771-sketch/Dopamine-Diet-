package com.focusduo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Unified four-destination shell. Visual styling is shared here while each
 * destination continues to own its existing data and feature logic.
 */
@Composable
fun DopamineDietNavigationShell(
    habits: @Composable () -> Unit,
    scroll: @Composable () -> Unit,
    screenTime: @Composable () -> Unit,
    settings: @Composable () -> Unit,
    isFocusSessionActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedDestination by rememberSaveable { mutableIntStateOf(0) }
    val destinations = listOf(
        NavigationDestination("Habits", "⌂"),
        NavigationDestination("Scroll", "⊘"),
        NavigationDestination("Screen Time", "◷"),
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
        containerColor = Color(0xFF08090E),
        bottomBar = {
            // Omit the complete outer navigation bar while any focus session is active.
            if (!isFocusSessionActive) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10111A))
                    .padding(start = 8.dp, end = 8.dp, top = 9.dp, bottom = 7.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEachIndexed { index, destination ->
                    val selected = selectedDestination == index
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedDestination = index }
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = destination.symbol,
                            color = if (selected) Color(0xFFC7A2FF) else Color(0xFF858797),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = destination.label,
                            color = if (selected) Color(0xFFD3B9FF) else Color(0xFF858797),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Spacer(Modifier.height(1.dp))
                        Box(
                            Modifier
                                .height(3.dp)
                                .fillMaxWidth(0.42f)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) Brush.horizontalGradient(
                                        listOf(Color(0xFF8B5CF6), Color(0xFF3D9BFF))
                                    ) else Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color.Transparent)
                                    )
                                )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF08090E))
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

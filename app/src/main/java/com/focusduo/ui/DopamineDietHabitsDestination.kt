package com.focusduo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private data class DietHabit(
    val id: Long,
    val title: String,
    val completedOn: String?
)

@Composable
fun DopamineDietHabitsDestination() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("dopamine_diet_habits", android.content.Context.MODE_PRIVATE)
    }
    var habits by remember {
        mutableStateOf(readHabits(preferences.getString("items", "[]").orEmpty()))
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var newHabitName by remember { mutableStateOf("") }
    val today = LocalDate.now().toString()
    val completed = habits.count { it.completedOn == today }

    LaunchedEffect(habits) {
        preferences.edit().putString("items", writeHabits(habits)).apply()
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Create a habit") },
            text = {
                OutlinedTextField(
                    value = newHabitName,
                    onValueChange = { newHabitName = it.take(48) },
                    label = { Text("Habit name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newHabitName.isNotBlank(),
                    onClick = {
                        val title = newHabitName.trim()
                        if (title.isNotEmpty()) {
                            habits = habits + DietHabit(System.currentTimeMillis(), title, null)
                            newHabitName = ""
                            showAddDialog = false
                        }
                    }
                ) { Text("Add habit") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF1A1921),
            titleContentColor = Color(0xFFF7F5FC),
            textContentColor = Color(0xFFB8B3C5)
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 22.dp, top = 28.dp, end = 22.dp, bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    "YOUR ROUTINE",
                    color = Color(0xFFB69AFF),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Build better habits.",
                    color = Color(0xFFF7F5FC),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Small actions, repeated consistently.",
                    color = Color(0xFFAAA4B7),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF211D2B))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("TODAY", color = Color(0xFFB69AFF), style = MaterialTheme.typography.labelMedium)
                        Text(
                            "$completed of ${habits.size} completed",
                            color = Color(0xFFF7F5FC),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        if (habits.isEmpty()) "—" else "${(completed * 100 / habits.size)}%",
                        color = Color(0xFFD3C2FF),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Daily habits", color = Color(0xFFF7F5FC), style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB69AFF),
                        contentColor = Color(0xFF17121F)
                    )
                ) { Text("Add") }
            }
        }
        if (habits.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Start with one small action", color = Color(0xFFF7F5FC), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Add a habit you want to practise every day. Your check-ins are saved on this device.",
                            color = Color(0xFFAAA4B7),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            items(habits, key = { it.id }) { habit ->
                val isDone = habit.completedOn == today
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17171F))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isDone,
                            onCheckedChange = { checked ->
                                habits = habits.map {
                                    if (it.id == habit.id) it.copy(completedOn = if (checked) today else null) else it
                                }
                            }
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(
                                habit.title,
                                color = Color(0xFFF7F5FC),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (isDone) "Completed today" else "Not completed yet",
                                color = Color(0xFFAAA4B7),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { habits = habits.filterNot { it.id == habit.id } }) {
                            Text("Remove", color = Color(0xFFAAA4B7))
                        }
                    }
                }
            }
        }
    }
}

private fun readHabits(raw: String): List<DietHabit> = runCatching {
    val array = JSONArray(raw)
    List(array.length()) { index ->
        val item = array.getJSONObject(index)
        DietHabit(
            id = item.getLong("id"),
            title = item.getString("title"),
            completedOn = if (item.isNull("completedOn")) null else item.getString("completedOn")
        )
    }
}.getOrDefault(emptyList())

private fun writeHabits(habits: List<DietHabit>): String {
    val array = JSONArray()
    habits.forEach { habit ->
        array.put(
            JSONObject().put("id", habit.id).put("title", habit.title)
                .put("completedOn", habit.completedOn ?: JSONObject.NULL)
        )
    }
    return array.toString()
}

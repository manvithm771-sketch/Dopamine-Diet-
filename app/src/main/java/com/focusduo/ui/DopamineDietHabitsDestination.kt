package com.focusduo.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private data class DietHabit(val id: Long, val title: String, val completedOn: String?)

private val Night = Color(0xFF08090E)
private val SurfaceTop = Color(0xFF1A1B28)
private val TextMain = Color(0xFFF7F5FC)
private val TextSoft = Color(0xFFAAAABD)
private val Violet = Color(0xFF9B6DFF)
private val Mint = Color(0xFF36DFA0)

@Composable
fun DopamineDietHabitsDestination() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("dopamine_diet_habits", Context.MODE_PRIVATE)
    }
    var habits by remember { mutableStateOf(readHabits(preferences.getString("items", "[]").orEmpty())) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newHabitName by remember { mutableStateOf("") }
    val today = LocalDate.now().toString()
    val completed = habits.count { it.completedOn == today }
    val progress = if (habits.isEmpty()) 0f else completed.toFloat() / habits.size

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
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } },
            containerColor = Color(0xFF1A1B28),
            titleContentColor = TextMain,
            textContentColor = TextSoft
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Night),
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8D5CF6), Color(0xFF287DDB)))),
                    contentAlignment = Alignment.Center
                ) { Text("✦", color = Color.White, style = MaterialTheme.typography.headlineMedium) }
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Dopamine Diet", color = TextMain, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Build good habits. Break bad ones.", color = TextSoft, style = MaterialTheme.typography.bodySmall)
                }
                Text("✧", color = Color(0xFFFFC857), style = MaterialTheme.typography.headlineMedium)
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(25.dp), colors = CardDefaults.cardColors(containerColor = SurfaceTop)) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF182B31), Color(0xFF1A1B2A), Color(0xFF202034))))
                        .padding(19.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(
                            progress = { progress }, modifier = Modifier.fillMaxSize(),
                            color = Mint, trackColor = Color(0xFF29423F), strokeWidth = 9.dp
                        )
                        Text("${(progress * 100).toInt()}%", color = TextMain, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Today's Habits", color = TextMain, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("$completed of ${habits.size} completed", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                        Text(if (habits.isEmpty()) "Add your first habit" else if (completed == habits.size) "Daily goal complete" else "One step at a time", color = Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF191A27))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF3A2B23)), contentAlignment = Alignment.Center) {
                        Text("✦", color = Color(0xFFFFB44A), style = MaterialTheme.typography.headlineSmall)
                    }
                    Column(Modifier.weight(1f).padding(start = 13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Build your rhythm", color = TextMain, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Small actions create lasting change.", color = TextSoft, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${habits.size}", color = Violet, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Today's Checklist", color = TextMain, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Keep your promises to yourself.", color = TextSoft, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { showAddDialog = true }, shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 17.dp, vertical = 9.dp)
                ) { Text("＋ Add") }
            }
        }
        if (habits.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF171823))) {
                    Column(
                        Modifier.fillMaxWidth().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Start with one small action", color = TextMain, style = MaterialTheme.typography.titleMedium)
                        Text("Add a habit you want to practise every day. Your check-ins are saved on this device.", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            items(habits, key = { it.id }) { habit ->
                val isDone = habit.completedOn == today
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF171923))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isDone,
                            onCheckedChange = { checked ->
                                habits = habits.map { if (it.id == habit.id) it.copy(completedOn = if (checked) today else null) else it }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Mint, uncheckedColor = Color(0xFF74778A), checkmarkColor = Night)
                        )
                        Column(Modifier.weight(1f).padding(start = 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(habit.title, color = TextMain, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            Text(if (isDone) "Completed today" else "Ready when you are", color = if (isDone) Mint else TextSoft, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { habits = habits.filterNot { it.id == habit.id } }) { Text("Remove", color = TextSoft) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

private fun readHabits(raw: String): List<DietHabit> = runCatching {
    val array = JSONArray(raw)
    List(array.length()) { index ->
        val item = array.getJSONObject(index)
        DietHabit(item.getLong("id"), item.getString("title"), if (item.isNull("completedOn")) null else item.getString("completedOn"))
    }
}.getOrDefault(emptyList())

private fun writeHabits(habits: List<DietHabit>): String {
    val array = JSONArray()
    habits.forEach { habit ->
        array.put(JSONObject().put("id", habit.id).put("title", habit.title).put("completedOn", habit.completedOn ?: JSONObject.NULL))
    }
    return array.toString()
}

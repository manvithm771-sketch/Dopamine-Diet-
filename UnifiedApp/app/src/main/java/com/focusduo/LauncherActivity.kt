package com.focusduo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LauncherScreen(
                        onDopamineDiet = {
                            startActivity(Intent().setClassName(this, "com.reeltracker.ui.MainActivity"))
                        },
                        onMindful = {
                            startActivity(Intent().setClassName(this, "com.mindful.android.nativeapp.MainActivity"))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LauncherScreen(
    onDopamineDiet: () -> Unit,
    onMindful: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Focus Duo", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text("Choose which app experience to open.")
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDopamineDiet, modifier = Modifier.fillMaxWidth()) {
            Text("Dopamine Diet")
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onMindful, modifier = Modifier.fillMaxWidth()) {
            Text("Mindful")
        }
    }
}

package com.vectencia.klarinet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vectencia.klarinet.demo.FilePlayerScreen
import com.vectencia.klarinet.demo.LatencyScreen
import com.vectencia.klarinet.demo.MicMeterScreen
import com.vectencia.klarinet.demo.Screen
import com.vectencia.klarinet.demo.EffectsScreen
import com.vectencia.klarinet.demo.ToneGeneratorScreen

@Composable
fun App() {
    MaterialTheme {
        var currentScreen by remember { mutableStateOf(Screen.TONE_GENERATOR) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            icon = { Text(screen.title.first().toString()) },
                            label = { Text(screen.title) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    Screen.TONE_GENERATOR -> ToneGeneratorScreen()
                    Screen.MIC_METER -> MicMeterScreen()
                    Screen.LATENCY -> LatencyScreen()
                    Screen.FILE_PLAYER -> FilePlayerScreen()
                    Screen.EFFECTS -> EffectsScreen()
                }
            }
        }
    }
}

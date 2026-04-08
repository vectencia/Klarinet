package com.vectencia.koboe

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
import com.vectencia.koboe.demo.FilePlayerScreen
import com.vectencia.koboe.demo.LatencyScreen
import com.vectencia.koboe.demo.MicMeterScreen
import com.vectencia.koboe.demo.Screen
import com.vectencia.koboe.demo.ToneGeneratorScreen

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
            val modifier = Modifier.padding(innerPadding)
            Box(modifier = modifier) {
                when (currentScreen) {
                    Screen.TONE_GENERATOR -> ToneGeneratorScreen()
                    Screen.MIC_METER -> MicMeterScreen()
                    Screen.LATENCY -> LatencyScreen()
                    Screen.FILE_PLAYER -> FilePlayerScreen()
                }
            }
        }
    }
}

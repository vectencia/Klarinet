package com.vectencia.klarinet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vectencia.klarinet.demo.DemoSession
import com.vectencia.klarinet.demo.EffectsScreen
import com.vectencia.klarinet.demo.FilePlayerScreen
import com.vectencia.klarinet.demo.LatencyScreen
import com.vectencia.klarinet.demo.MicMeterScreen
import com.vectencia.klarinet.demo.ScenesScreen
import com.vectencia.klarinet.demo.Screen
import com.vectencia.klarinet.demo.ToneGeneratorScreen
import com.vectencia.klarinet.demo.interruptionBanner

@Composable
fun App() {
    MaterialTheme {
        var currentScreen by remember { mutableStateOf(Screen.TONE_GENERATOR) }
        val interruption by DemoSession.interruption.collectAsState()
        val banner = interruptionBanner(interruption)

        Scaffold(
            topBar = {
                Column {
                    if (banner != null) {
                        Text(
                            text = banner,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        )
                    }
                    ScrollableTabRow(selectedTabIndex = currentScreen.ordinal) {
                        Screen.entries.forEach { screen ->
                            Tab(
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                text = { Text(screen.title) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentScreen) {
                    Screen.TONE_GENERATOR -> ToneGeneratorScreen()
                    Screen.MIC_METER -> MicMeterScreen()
                    Screen.LATENCY -> LatencyScreen()
                    Screen.FILE_PLAYER -> FilePlayerScreen()
                    Screen.EFFECTS -> EffectsScreen()
                    Screen.SCENES -> ScenesScreen()
                }
            }
        }
    }
}

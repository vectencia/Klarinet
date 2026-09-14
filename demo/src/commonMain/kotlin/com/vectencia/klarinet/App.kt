package com.vectencia.klarinet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.vectencia.klarinet.demo.routeBanner
import com.vectencia.klarinet.theme.DemoBackdrop
import com.vectencia.klarinet.theme.KlarinetTheme
import com.vectencia.klarinet.theme.SessionBanner
import com.vectencia.klarinet.theme.klarinetTween

@Composable
fun App() {
    KlarinetTheme {
        var currentScreen by remember { mutableStateOf(Screen.TONE_GENERATOR) }
        val interruption by DemoSession.interruption.collectAsState()
        val routeChange by DemoSession.routeChange.collectAsState()
        val banner = interruptionBanner(interruption)
        val route = routeBanner(routeChange)
        val scheme = MaterialTheme.colorScheme

        Scaffold(
            containerColor = scheme.background,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.background)
                        .padding(top = 12.dp, bottom = 8.dp),
                ) {
                    Text(
                        text = "Klarinet",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Text(
                        text = "Studio demo",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                    SessionBanner(banner)
                    SessionBanner(route)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Screen.entries.forEach { screen ->
                            val selected = currentScreen == screen
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) scheme.primary else scheme.surfaceVariant)
                                    .clickable { currentScreen = screen }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = screen.title,
                                    color = if (selected) scheme.onPrimary else scheme.onSurface,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            DemoBackdrop(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        val forward = targetState.ordinal >= initialState.ordinal
                        val enter = fadeIn(klarinetTween(380)) + slideInHorizontally(klarinetTween(380)) {
                            if (forward) it / 12 else -it / 12
                        }
                        val exit = fadeOut(klarinetTween(240)) + slideOutHorizontally(klarinetTween(240)) {
                            if (forward) -it / 16 else it / 16
                        }
                        enter togetherWith exit
                    },
                    label = "screen",
                ) { screen ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (screen) {
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
    }
}

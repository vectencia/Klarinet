package com.spiritbox

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spiritbox.di.stationModule
import com.spiritbox.ui.Appearance
import com.spiritbox.ui.RadioDestination
import com.spiritbox.ui.RadioFace
import com.spiritbox.ui.RadioViewModel
import com.spiritbox.ui.ReviewDestination
import com.spiritbox.ui.ReviewFace
import com.spiritbox.ui.ReviewViewModel
import com.spiritbox.ui.SpiritTheme
import kotlinx.coroutines.launch
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinConfiguration

@Composable
fun App() {
    KoinApplication(configuration = koinConfiguration { modules(stationModule) }) {
        val appearance = koinInject<Appearance>()
        val dark by appearance.dark.collectAsStateWithLifecycle()
        val navController = rememberNavController()
        SpiritTheme(dark = dark) {
            Surface(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = RadioDestination,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable<RadioDestination> {
                        RadioScreen(
                            dark = dark,
                            onTheme = appearance::toggle,
                            onOpenSession = { path ->
                                navController.navigate(ReviewDestination(path)) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                    composable<ReviewDestination> {
                        ReviewScreen(
                            dark = dark,
                            onTheme = appearance::toggle,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioScreen(
    dark: Boolean,
    onTheme: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: RadioViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.refreshTakes() }
    RadioFace(
        frequency = state.frequency,
        callsign = state.callsign,
        running = state.running,
        dwellMs = state.dwellMs,
        forward = state.forward,
        holding = state.holding,
        am = state.am,
        recording = state.recording,
        sessions = state.sessions,
        library = state.library,
        playing = null,
        mic = state.mic,
        sessionName = state.sessionName,
        recordGain = state.recordGain,
        live = state.live.toFloatArray(),
        elapsedLabel = state.elapsedLabel,
        dark = dark,
        onTheme = onTheme,
        onName = viewModel::onName,
        onRecordGain = viewModel::onRecordGain,
        onStartStop = viewModel::onStartStop,
        onRecord = viewModel::onRecord,
        onPlay = { path ->
            scope.launch {
                viewModel.stopForReview()
                onOpenSession(path)
            }
        },
        onDwell = viewModel::onDwell,
        onForward = viewModel::onForward,
        onReverse = viewModel::onReverse,
        onHold = viewModel::onHold,
        onAm = viewModel::onAm,
        onFm = viewModel::onFm,
        onMic = viewModel::onMic,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ReviewScreen(
    dark: Boolean,
    onTheme: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReviewViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.open() }
    ReviewFace(
        title = state.title,
        recorded = state.recorded,
        duration = state.duration,
        panel = state.panel,
        wave = state.wave.toFloatArray(),
        marks = state.marks,
        playing = state.playing,
        paused = state.paused,
        rate = state.rate,
        reverse = state.reverse,
        gain = state.gain,
        voiceBand = state.voiceBand,
        threshold = state.threshold,
        transcription = state.transcription,
        onBack = {
            viewModel.stopPlayback()
            onBack()
        },
        onPlay = viewModel::onPlay,
        onPause = viewModel::onPause,
        onRate = viewModel::onRate,
        onReverse = viewModel::onReverse,
        onGain = viewModel::onGain,
        onVoice = viewModel::onVoice,
        onThreshold = viewModel::onThreshold,
        onAddMark = viewModel::onAddMark,
        onRemoveMark = viewModel::onRemoveMark,
        onJump = viewModel::onJump,
        onTheme = onTheme,
        dark = dark,
        modifier = Modifier.fillMaxSize(),
    )
}

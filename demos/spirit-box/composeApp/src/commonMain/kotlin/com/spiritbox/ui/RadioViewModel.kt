package com.spiritbox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spiritbox.domain.Clock
import com.spiritbox.domain.MicrophoneAccess
import com.spiritbox.domain.RecordingRepository
import com.spiritbox.playback.elapsedMillis
import com.spiritbox.playback.formatElapsed
import com.spiritbox.playback.levelEnvelope
import com.spiritbox.session.RadioControl
import com.spiritbox.session.SessionTape
import com.spiritbox.session.TakeNote
import com.spiritbox.session.closeTake
import com.spiritbox.session.listedSession
import com.spiritbox.session.playSession
import com.spiritbox.station.HopDirection
import com.spiritbox.station.MAX_DWELL_MS
import com.spiritbox.station.MIN_DWELL_MS
import com.spiritbox.station.StationLabel
import com.spiritbox.station.amStations
import com.spiritbox.station.fmStations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class RadioViewModel(
    private val stations: List<StationLabel>,
    private val recordings: RecordingRepository,
    private val clock: Clock,
    private val microphone: MicrophoneAccess,
) : ViewModel() {
    private val fm = fmStations(stations)
    private val am = amStations(stations)
    private val control = RadioControl()
    private val tape = SessionTape()
    private val note = TakeNote()
    private var sessionJob: Job? = null
    private var meterJob: Job? = null
    private var recordStarted = 0L
    private var askingMic = false

    val uiState: StateFlow<RadioUiState>
        field = MutableStateFlow(
            RadioUiState(
                frequency = fm.first().frequency,
                callsign = fm.first().callsign,
            ),
        )

    fun refreshTakes() {
        viewModelScope.launch {
            val listed = runCatching {
                withContext(Dispatchers.Default) {
                    recordings.listPaths().map { path ->
                        listedSession(path, runCatching { recordings.load(path) }.getOrNull())
                    }
                }
            }
            if (listed.isFailure) {
                uiState.update { it.copy(sessions = emptyList(), library = panelState(loading = false, failed = true, itemCount = 0)) }
                return@launch
            }
            val sessions = listed.getOrDefault(emptyList())
            uiState.update {
                it.copy(sessions = sessions, library = panelState(loading = false, failed = false, itemCount = sessions.size))
            }
        }
    }

    fun onStartStop() {
        val job = sessionJob
        if (job != null) {
            job.cancel()
            sessionJob = null
            uiState.update { it.copy(running = false) }
            return
        }
        uiState.update { it.copy(running = true) }
        sessionJob = viewModelScope.launch {
            try {
                playSession(
                    limitMs = null,
                    onIndex = ::showIndex,
                    control = control,
                    stations = stations,
                    tape = tape,
                ) {
                    uiState.update { it.copy(mic = 0f) }
                }
            } finally {
                closeTake(tape, note, recordings::save)
                tape.disarm()
                tape.seal()
                stopMeter()
                uiState.update { it.copy(recording = false, running = false, live = emptyList()) }
                refreshTakes()
                if (sessionJob == this.coroutineContext[Job]) sessionJob = null
            }
        }
    }

    suspend fun stopForReview() {
        val previous = sessionJob
        sessionJob = null
        uiState.update { it.copy(running = false) }
        previous?.cancelAndJoin()
        closeTake(tape, note, recordings::save)
        stopMeter()
        uiState.update { it.copy(recording = false, live = emptyList()) }
    }

    fun onRecord() {
        if (uiState.value.recording) {
            closeTake(tape, note, recordings::save)
            stopMeter()
            uiState.update { it.copy(recording = false, live = emptyList()) }
            refreshTakes()
            return
        }
        val path = runCatching { recordings.newTakePath() }.getOrNull() ?: return
        recordStarted = clock.nowMillis()
        note.begin(path, recordStarted, uiState.value.sessionName)
        tape.recordGain = uiState.value.recordGain
        tape.arm(path)
        uiState.update { it.copy(recording = true, elapsedLabel = "0:00") }
        startMeter()
    }

    fun onDwell(value: Int) {
        val clamped = value.coerceIn(MIN_DWELL_MS, MAX_DWELL_MS)
        control.dwellMs = clamped
        uiState.update { it.copy(dwellMs = clamped) }
    }

    fun onForward() {
        control.direction = HopDirection.FORWARD
        uiState.update { it.copy(forward = true) }
    }

    fun onReverse() {
        control.direction = HopDirection.REVERSE
        uiState.update { it.copy(forward = false) }
    }

    fun onHold() {
        val next = !uiState.value.holding
        control.holding = next
        uiState.update { it.copy(holding = next) }
    }

    fun onAm() = selectBand(amBand = true)

    fun onFm() = selectBand(amBand = false)

    fun onName(value: String) {
        note.name = value
        uiState.update { it.copy(sessionName = value) }
    }

    fun onRecordGain(value: Float) {
        tape.recordGain = value
        uiState.update { it.copy(recordGain = value) }
    }

    fun onMic(ratio: Float) {
        val clamped = ratio.coerceIn(0f, 1f)
        uiState.update { it.copy(mic = clamped) }
        control.requestedMic = clamped
        if (clamped <= 0f) {
            control.micRatio = 0f
            control.micChanged.trySend(Unit)
            return
        }
        if (microphone.granted()) {
            control.micRatio = clamped
            control.micChanged.trySend(Unit)
            return
        }
        if (askingMic) return
        askingMic = true
        microphone.ask { granted ->
            viewModelScope.launch {
                askingMic = false
                val now = control.requestedMic
                if (granted && now > 0f) {
                    control.micRatio = now
                    control.micChanged.trySend(Unit)
                } else if (!granted) {
                    control.requestedMic = 0f
                    control.micRatio = 0f
                    uiState.update { it.copy(mic = 0f) }
                }
            }
        }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        meterJob?.cancel()
        tape.disarm()
        tape.seal()
    }

    private fun selectBand(amBand: Boolean) {
        control.chooseBand(amBand)
        uiState.update { state ->
            val label = bandLabel(state.index, amBand)
            state.copy(am = amBand, frequency = label.frequency, callsign = label.callsign)
        }
    }

    private fun showIndex(index: Int) {
        uiState.update { state ->
            val label = bandLabel(index, state.am)
            state.copy(index = index, frequency = label.frequency, callsign = label.callsign)
        }
    }

    private fun bandLabel(index: Int, amBand: Boolean): StationLabel {
        val shown = if (amBand) am else fm
        return shown[index.coerceIn(0, shown.lastIndex)]
    }

    private fun startMeter() {
        meterJob?.cancel()
        meterJob = viewModelScope.launch {
            while (isActive) {
                val live = levelEnvelope(tape.recent).toList()
                val elapsed = formatElapsed(elapsedMillis(recordStarted, clock.nowMillis()))
                uiState.update { it.copy(live = live, elapsedLabel = elapsed) }
                delay(80)
            }
        }
    }

    private fun stopMeter() {
        meterJob?.cancel()
        meterJob = null
    }
}

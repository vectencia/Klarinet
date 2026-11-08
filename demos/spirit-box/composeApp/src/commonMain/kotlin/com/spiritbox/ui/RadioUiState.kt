package com.spiritbox.ui

import com.spiritbox.session.ListedSession
import com.spiritbox.station.DEFAULT_DWELL_MS

internal data class RadioUiState(
    val frequency: String,
    val callsign: String,
    val index: Int = 0,
    val running: Boolean = false,
    val dwellMs: Int = DEFAULT_DWELL_MS,
    val forward: Boolean = true,
    val holding: Boolean = false,
    val am: Boolean = false,
    val recording: Boolean = false,
    val sessions: List<ListedSession> = emptyList(),
    val library: PanelState = PanelState.Loading,
    val mic: Float = 0f,
    val sessionName: String = "",
    val recordGain: Float = 1f,
    val live: List<Float> = emptyList(),
    val elapsedLabel: String = "0:00",
)

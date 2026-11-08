package com.spiritbox.ui

import com.spiritbox.session.SessionMeta
import com.spiritbox.station.defaultStations
import com.spiritbox.domain.Clock
import com.spiritbox.domain.MicrophoneAccess
import com.spiritbox.domain.RecordingRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadioViewModelTest {
    @Test
    fun startsOnFmAndKeepsDwellWhenTheBandChanges() {
        val viewModel = RadioViewModel(
            stations = defaultStations(),
            recordings = MemoryRecordings(),
            clock = Clock { 1_000L },
            microphone = object : MicrophoneAccess {
                override fun granted(): Boolean = true
                override fun ask(onResult: (Boolean) -> Unit) = Unit
            },
        )
        assertEquals("88.0 MHz", viewModel.uiState.value.frequency)
        assertFalse(viewModel.uiState.value.am)
        viewModel.onDwell(220)
        viewModel.onAm()
        assertTrue(viewModel.uiState.value.am)
        assertEquals(220, viewModel.uiState.value.dwellMs)
        assertEquals("530 kHz", viewModel.uiState.value.frequency)
        viewModel.onHold()
        assertTrue(viewModel.uiState.value.holding)
    }
}

private class MemoryRecordings : RecordingRepository {
    override fun listPaths(): List<String> = emptyList()

    override fun load(path: String): SessionMeta? = null

    override fun save(path: String, meta: SessionMeta) = Unit

    override fun newTakePath(): String = "session.wav"
}

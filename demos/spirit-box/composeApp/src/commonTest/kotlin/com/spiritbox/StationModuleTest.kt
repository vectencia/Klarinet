package com.spiritbox

import com.spiritbox.di.stationModule
import com.spiritbox.domain.Clock
import com.spiritbox.domain.MicrophoneAccess
import com.spiritbox.domain.RecordingRepository
import com.spiritbox.station.StationLabel
import com.spiritbox.transcription.TranscriptionPort
import com.spiritbox.ui.Appearance
import com.spiritbox.ui.RadioViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class StationModuleTest {
    @Test
    fun moduleProvidesSixFmAndSixAm() {
        val koin = startKoin { modules(stationModule) }.koin
        try {
            val stations = koin.get<List<StationLabel>>()
            assertEquals(6, stations.count { it.frequency.endsWith("MHz") })
            assertEquals(6, stations.count { it.frequency.endsWith("kHz") })
            val heard = koin.get<TranscriptionPort>().transcribe(FloatArray(8), 0, 8)
            assertEquals(emptyList(), heard)
            assertTrue(koin.get<Clock>().nowMillis() > 0L)
            koin.get<RecordingRepository>()
            koin.get<MicrophoneAccess>()
            koin.get<Appearance>()
            assertEquals("88.0 MHz", koin.get<RadioViewModel>().uiState.value.frequency)
        } finally {
            stopKoin()
        }
    }
}

package com.spiritbox.di

import com.spiritbox.data.FileRecordingRepository
import com.spiritbox.data.PlatformMicrophone
import com.spiritbox.domain.Clock
import com.spiritbox.domain.MicrophoneAccess
import com.spiritbox.domain.RecordingRepository
import com.spiritbox.domain.epochMillis
import com.spiritbox.station.defaultStations
import com.spiritbox.transcription.TranscriptionPort
import com.spiritbox.transcription.UnavailableTranscription
import com.spiritbox.ui.Appearance
import com.spiritbox.ui.RadioViewModel
import com.spiritbox.ui.ReviewViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal val stationModule = module {
    single { defaultStations() }
    single<TranscriptionPort> { UnavailableTranscription() }
    single<Clock> { Clock { epochMillis() } }
    single<RecordingRepository> { FileRecordingRepository(get()) }
    single<MicrophoneAccess> { PlatformMicrophone() }
    singleOf(::Appearance)
    viewModelOf(::RadioViewModel)
    viewModelOf(::ReviewViewModel)
}

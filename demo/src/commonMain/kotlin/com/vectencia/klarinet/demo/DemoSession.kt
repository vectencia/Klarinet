package com.vectencia.klarinet.demo

import com.vectencia.klarinet.AudioInterruptionInfo
import com.vectencia.klarinet.AudioInterruptionType
import com.vectencia.klarinet.AudioSessionCategory
import com.vectencia.klarinet.AudioSessionManager
import com.vectencia.klarinet.AudioSessionMode
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.coroutines.interruptionFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

fun interruptionBanner(info: AudioInterruptionInfo?): String? =
    if (info?.type == AudioInterruptionType.BEGAN) "Interrupted" else null

object DemoSession {
    val manager: AudioSessionManager = AudioSessionManager()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _interruption = MutableStateFlow<AudioInterruptionInfo?>(null)
    val interruption: StateFlow<AudioInterruptionInfo?> = _interruption.asStateFlow()

    init {
        try {
            manager.configure(AudioSessionCategory.PLAY_AND_RECORD, AudioSessionMode.DEFAULT)
            manager.setActive(true)
        } catch (_: Exception) {
        }
        scope.launch {
            manager.interruptionFlow().collect { info ->
                _interruption.value = if (info.type == AudioInterruptionType.BEGAN) info else null
            }
        }
    }

    fun attach(stream: AudioStream) {
        manager.attach(stream)
    }

    fun detach(stream: AudioStream) {
        manager.detach(stream)
    }
}

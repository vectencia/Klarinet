package com.vectencia.klarinet.demo.web

import com.vectencia.klarinet.AudioInterruptionType
import com.vectencia.klarinet.AudioSessionCategory
import com.vectencia.klarinet.AudioSessionManager
import com.vectencia.klarinet.AudioSessionMode
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.coroutines.interruptionFlow
import com.vectencia.klarinet.coroutines.routeChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object DemoSession {
    val manager = AudioSessionManager()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var banner: ((String?) -> Unit)? = null

    init {
        try {
            manager.configure(AudioSessionCategory.PLAY_AND_RECORD, AudioSessionMode.DEFAULT)
            manager.setActive(true)
        } catch (_: Throwable) {
        }
        scope.launch {
            manager.interruptionFlow().collect { info ->
                banner?.invoke(interruptionBanner(info))
            }
        }
        scope.launch {
            manager.routeChangeFlow().collect { info ->
                banner?.invoke(routeBanner(info))
            }
        }
    }

    fun onBanner(callback: (String?) -> Unit) {
        banner = callback
    }

    fun requestRecordPermission(onResult: (Boolean) -> Unit) {
        manager.requestRecordPermission(onResult)
    }

    fun attach(stream: AudioStream) {
        manager.attach(stream)
    }

    fun detach(stream: AudioStream) {
        manager.detach(stream)
    }
}

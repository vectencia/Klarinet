package com.vectencia.klarinet

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

actual class AudioSessionManager {
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private val interruptions = InterruptionController()

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        AudioFocusInterruptions.fromFocusChange(change)?.let { interruptions.dispatch(it) }
    }

    /**
     * Provide an Android [Context] so [setActive] can request audio focus.
     * Uses [Context.getApplicationContext]. No-op until this is called.
     */
    fun bind(context: Context) {
        audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    actual fun configure(category: AudioSessionCategory, mode: AudioSessionMode) { /* category is a host/session hint */ }

    actual fun setActive(active: Boolean) {
        val manager = audioManager ?: return
        if (active) {
            requestFocus(manager)
        } else {
            abandonFocus(manager)
        }
    }

    actual fun observeRouteChanges(listener: (AudioRouteChangeInfo) -> Unit) { /* no-op: use AudioDeviceCallback in the host */ }

    actual fun observeInterruptions(listener: (AudioInterruptionInfo) -> Unit) {
        interruptions.observe(listener)
    }

    actual fun attach(stream: AudioStream) = interruptions.attach(stream)

    actual fun detach(stream: AudioStream) = interruptions.detach(stream)

    private fun requestFocus(manager: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
    }

    private fun abandonFocus(manager: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = focusRequest
            if (request != null) {
                manager.abandonAudioFocusRequest(request)
                focusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(focusChangeListener)
        }
    }
}

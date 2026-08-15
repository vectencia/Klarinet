@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import klarinet_dsp.KlarinetUserAudioCallback
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction

internal class AppleAudioUser(
    val callback: AudioStreamCallback,
    val channelCount: Int,
)

internal val appleUserAudioCallback: KlarinetUserAudioCallback =
    staticCFunction { userData: COpaquePointer?, buffer: CPointer<FloatVar>?, numFrames: Int, channelCount: Int ->
        val user = userData?.asStableRef<AppleAudioUser>()?.get() ?: return@staticCFunction 0
        val total = numFrames * channelCount
        val arr = FloatArray(total)
        if (buffer != null) {
            for (i in 0 until total) {
                arr[i] = buffer[i]
            }
        }
        val n = user.callback.onAudioReady(arr, numFrames)
        if (buffer != null && n > 0) {
            val outSamples = n * channelCount
            for (i in 0 until outSamples) {
                buffer[i] = arr[i]
            }
        }
        n
    }

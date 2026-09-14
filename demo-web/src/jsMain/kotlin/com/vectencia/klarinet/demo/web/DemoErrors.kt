package com.vectencia.klarinet.demo.web

import com.vectencia.klarinet.AudioInterruptionInfo
import com.vectencia.klarinet.AudioInterruptionType
import com.vectencia.klarinet.AudioRouteChangeInfo
import com.vectencia.klarinet.PermissionException
import com.vectencia.klarinet.StreamOperationException

internal fun interruptionBanner(info: AudioInterruptionInfo?): String? =
    if (info?.type == AudioInterruptionType.BEGAN) "Interrupted" else null

internal fun routeBanner(info: AudioRouteChangeInfo): String = "Route: ${info.reason}"

internal fun demoErrorMessage(error: Throwable): String = when (error) {
    is PermissionException -> error.message ?: "Microphone permission denied"
    is StreamOperationException -> error.message ?: "Stream operation failed"
    else -> error.message ?: error.toString()
}

internal fun formatDb(value: Float): String =
    if (value.isFinite()) "${value.toInt()} dB" else "-inf dB"

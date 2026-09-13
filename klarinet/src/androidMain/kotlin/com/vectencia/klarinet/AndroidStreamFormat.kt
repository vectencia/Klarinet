package com.vectencia.klarinet

internal fun requirePcmFloat(format: AudioFormat) {
    if (format != AudioFormat.PCM_FLOAT) {
        throw UnsupportedFormatException(
            "Android streams only support PCM_FLOAT, got $format",
        )
    }
}

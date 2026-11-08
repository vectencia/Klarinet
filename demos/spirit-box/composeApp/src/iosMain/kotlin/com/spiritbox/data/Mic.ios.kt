package com.spiritbox.data

import com.vectencia.klarinet.AudioSessionManager

internal actual fun micGranted(): Boolean = AudioSessionManager().hasRecordPermission()

internal actual fun askMic(onResult: (Boolean) -> Unit) {
    AudioSessionManager().requestRecordPermission(onResult)
}

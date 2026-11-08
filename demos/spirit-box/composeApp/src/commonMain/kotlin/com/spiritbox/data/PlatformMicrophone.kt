package com.spiritbox.data

import com.spiritbox.domain.MicrophoneAccess

internal expect fun micGranted(): Boolean

internal expect fun askMic(onResult: (Boolean) -> Unit)

internal class PlatformMicrophone : MicrophoneAccess {
    override fun granted(): Boolean = micGranted()

    override fun ask(onResult: (Boolean) -> Unit) = askMic(onResult)
}

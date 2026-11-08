package com.spiritbox.domain

internal interface MicrophoneAccess {
    fun granted(): Boolean

    fun ask(onResult: (Boolean) -> Unit)
}

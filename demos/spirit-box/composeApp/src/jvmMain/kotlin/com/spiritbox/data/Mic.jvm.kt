package com.spiritbox.data

internal actual fun micGranted(): Boolean = true

internal actual fun askMic(onResult: (Boolean) -> Unit) = onResult(true)

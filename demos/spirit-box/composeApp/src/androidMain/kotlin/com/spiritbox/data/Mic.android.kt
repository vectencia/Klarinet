package com.spiritbox.data

private var micAllowed: () -> Boolean = { false }
private var micAsk: ((Boolean) -> Unit) -> Unit = { it(false) }

fun bindMicPermission(allowed: () -> Boolean, ask: ((Boolean) -> Unit) -> Unit) {
    micAllowed = allowed
    micAsk = ask
}

fun releaseMicPermission() {
    micAllowed = { false }
    micAsk = { it(false) }
}

internal actual fun micGranted(): Boolean = micAllowed()

internal actual fun askMic(onResult: (Boolean) -> Unit) = micAsk(onResult)

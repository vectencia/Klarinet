package com.spiritbox.session

import com.spiritbox.station.DEFAULT_DWELL_MS
import com.spiritbox.station.HopDirection
import kotlin.concurrent.Volatile
import kotlinx.coroutines.channels.Channel

internal class RadioControl {
    @Volatile var dwellMs: Int = DEFAULT_DWELL_MS
    @Volatile var direction: HopDirection = HopDirection.FORWARD
    @Volatile var holding: Boolean = false
    @Volatile var amBand: Boolean = false
    @Volatile var micRatio: Float = 0f
    @Volatile var requestedMic: Float = 0f
    val bandChanged = Channel<Unit>(Channel.CONFLATED)
    val micChanged = Channel<Unit>(Channel.CONFLATED)

    fun chooseBand(am: Boolean) {
        if (amBand == am) return
        amBand = am
        bandChanged.trySend(Unit)
    }
}

package com.spiritbox.station

internal const val MIN_DWELL_MS = 50
internal const val MAX_DWELL_MS = 350
internal const val DEFAULT_DWELL_MS = 150

private const val FM_BANDWIDTH_OCTAVES = 1f
private const val AM_BANDWIDTH_OCTAVES = 2.5f

internal enum class HopDirection { FORWARD, REVERSE }

internal data class StationLabel(val frequency: String, val callsign: String)

private val CALLS = arrayOf(
    "KQZV", "WNXB", "KLFQ", "WRBM", "KVHJ", "WQZX",
    "WDXQ", "KPNV", "WMJV", "KRVZ", "WVQL", "KBHX",
)

internal fun stationIndex(
    stationCount: Int,
    dwellMs: Int,
    direction: HopDirection,
    tMillis: Long,
    holding: Boolean,
): Int {
    if (holding || stationCount <= 0 || tMillis <= 0L) return 0
    // shortcut: dwell outside 50–350 ms is clamped, not rejected
    val dwell = dwellMs.coerceIn(MIN_DWELL_MS, MAX_DWELL_MS)
    val mod = ((tMillis / dwell) % stationCount).toInt()
    return if (direction == HopDirection.FORWARD) mod else (stationCount - mod) % stationCount
}

internal fun nextHop(current: Int, stepped: Int, holding: Boolean): Int =
    if (holding) current else stepped

internal fun stationLabel(index: Int): StationLabel {
    val i = index.mod(CALLS.size)
    val frequency = if (i < 6) {
        val mhz10 = 880 + i * 40
        "${mhz10 / 10}.${mhz10 % 10} MHz"
    } else {
        "${530 + (i - 6) * 234} kHz"
    }
    return StationLabel(frequency, CALLS[i])
}

internal fun fmStations(stations: List<StationLabel>): List<StationLabel> =
    stations.filter { it.frequency.endsWith("MHz") }

internal fun amStations(stations: List<StationLabel>): List<StationLabel> =
    stations.filter { it.frequency.endsWith("kHz") }

// AM is a wider pass so the bed is a different static. Same filter, no second effect.
internal fun bandBandwidth(am: Boolean): Float =
    if (am) AM_BANDWIDTH_OCTAVES else FM_BANDWIDTH_OCTAVES

internal fun defaultStations(): List<StationLabel> = List(CALLS.size) { stationLabel(it) }

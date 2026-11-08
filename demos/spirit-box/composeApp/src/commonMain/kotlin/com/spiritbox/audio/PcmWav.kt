package com.spiritbox.audio

import com.spiritbox.audio.SAMPLE_RATE


internal fun decodePcm16Wav(bytes: ByteArray): FloatArray {
    fun u16(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
    fun u32(at: Int) = u16(at) or (u16(at + 2) shl 16)
    fun tag(at: Int, a: Char, b: Char, c: Char, d: Char) =
        bytes[at] == a.code.toByte() && bytes[at + 1] == b.code.toByte() &&
            bytes[at + 2] == c.code.toByte() && bytes[at + 3] == d.code.toByte()
    require(bytes.size >= 12 && tag(0, 'R', 'I', 'F', 'F') && tag(8, 'W', 'A', 'V', 'E'))
    var at = 12
    var rate = 0
    var channels = 0
    var bits = 0
    var format = 0
    var dataAt = -1
    var dataBytes = 0
    while (at + 8 <= bytes.size) {
        val size = u32(at + 4)
        val body = at + 8
        if (tag(at, 'f', 'm', 't', ' ')) {
            format = u16(body)
            channels = u16(body + 2)
            rate = u32(body + 4)
            bits = u16(body + 14)
        } else if (tag(at, 'd', 'a', 't', 'a')) {
            dataAt = body
            dataBytes = size
            break
        }
        val next = body + size + (size and 1)
        if (next <= at) break
        at = next
    }
    require(format == 1 && rate == SAMPLE_RATE && channels == 1 && bits == 16 && dataAt >= 0) {
        "station wav must be $SAMPLE_RATE Hz mono pcm16"
    }
    val samples = minOf(dataBytes, bytes.size - dataAt) / 2
    require(samples > 0)
    val pcm = FloatArray(samples)
    var p = dataAt
    for (i in 0 until samples) {
        val lo = bytes[p].toInt() and 0xFF
        val hi = bytes[p + 1].toInt()
        pcm[i] = ((hi shl 8) or lo) / 32_768f
        p += 2
    }
    return pcm
}

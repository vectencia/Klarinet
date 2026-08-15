package com.vectencia.klarinet

internal data class DecodedWav(
    val samples: FloatArray,
    val sampleRate: Int,
    val channelCount: Int,
)

internal fun encodeWav16(samples: FloatArray, sampleRate: Int, channelCount: Int): ByteArray {
    val dataSize = samples.size * 2
    val bytes = ByteArray(44 + dataSize)
    writeAscii(bytes, 0, "RIFF")
    writeInt32(bytes, 4, 36 + dataSize)
    writeAscii(bytes, 8, "WAVE")
    writeAscii(bytes, 12, "fmt ")
    writeInt32(bytes, 16, 16)
    writeInt16(bytes, 20, 1)
    writeInt16(bytes, 22, channelCount)
    writeInt32(bytes, 24, sampleRate)
    writeInt32(bytes, 28, sampleRate * channelCount * 2)
    writeInt16(bytes, 32, channelCount * 2)
    writeInt16(bytes, 34, 16)
    writeAscii(bytes, 36, "data")
    writeInt32(bytes, 40, dataSize)
    var offset = 44
    for (sample in samples) {
        val pcm = (sample.coerceIn(-1f, 1f) * 32767f).toInt().coerceIn(-32768, 32767)
        writeInt16(bytes, offset, pcm)
        offset += 2
    }
    return bytes
}

internal fun decodeWav(bytes: ByteArray): DecodedWav {
    if (bytes.size < 12 || !asciiEquals(bytes, 0, "RIFF") || !asciiEquals(bytes, 8, "WAVE")) {
        throw AudioFileException("Not a WAV file")
    }
    var offset = 12
    var format = 1
    var channels = 1
    var sampleRate = 44100
    var bits = 16
    var dataOffset = -1
    var dataSize = 0
    while (offset + 8 <= bytes.size) {
        val size = readInt32(bytes, offset + 4)
        val start = offset + 8
        val end = (start + size).coerceAtMost(bytes.size)
        when {
            asciiEquals(bytes, offset, "fmt ") -> {
                if (end < start + 16) throw AudioFileException("Invalid WAV fmt chunk")
                format = readInt16(bytes, start)
                channels = readInt16(bytes, start + 2)
                sampleRate = readInt32(bytes, start + 4)
                bits = readInt16(bytes, start + 14)
            }
            asciiEquals(bytes, offset, "data") -> {
                dataOffset = start
                dataSize = size
            }
        }
        offset = start + size
        if (size % 2 == 1) offset++
    }
    if (dataOffset < 0 || channels <= 0 || sampleRate <= 0) {
        throw AudioFileException("WAV file is missing a data chunk")
    }
    val available = (bytes.size - dataOffset).coerceAtMost(dataSize)
    val samples = when {
        format == 1 && bits == 16 -> {
            val count = available / 2
            FloatArray(count) { index ->
                readInt16(bytes, dataOffset + index * 2) / 32768f
            }
        }
        format == 3 && bits == 32 -> {
            val count = available / 4
            FloatArray(count) { index ->
                Float.fromBits(readInt32(bytes, dataOffset + index * 4))
            }
        }
        else -> throw AudioFileException("Unsupported WAV format (format=$format bits=$bits)")
    }
    return DecodedWav(samples, sampleRate, channels)
}

private fun writeAscii(bytes: ByteArray, offset: Int, value: String) {
    for (i in value.indices) bytes[offset + i] = value[i].code.toByte()
}

private fun writeInt16(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = (value and 0xFF).toByte()
    bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
}

private fun writeInt32(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = (value and 0xFF).toByte()
    bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
    bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
}

private fun readInt16(bytes: ByteArray, offset: Int): Int {
    val value = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    return if (value >= 0x8000) value - 0x10000 else value
}

private fun readInt32(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 3].toInt() and 0xFF) shl 24)

private fun asciiEquals(bytes: ByteArray, offset: Int, value: String): Boolean {
    if (offset + value.length > bytes.size) return false
    for (i in value.indices) {
        if ((bytes[offset + i].toInt() and 0xFF).toChar() != value[i]) return false
    }
    return true
}

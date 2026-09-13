package com.vectencia.klarinet

import kotlin.math.min

/**
 * Converts interleaved PCM from [sourceRate] to a destination rate using
 * linear interpolation. Used by [AudioEngine.playFile] when the stream's
 * negotiated rate differs from the file (JS AudioContext is typically 48 kHz).
 */
internal class LinearResampler(
    private val sourceRate: Int,
    private val channels: Int,
) {
    init {
        require(sourceRate > 0) { "sourceRate must be > 0" }
        require(channels > 0) { "channels must be > 0" }
    }

    private val capacityFrames = 4096
    private val ring = FloatArray(capacityFrames * channels)
    private var head = 0
    private var size = 0
    private var phase = 0.0

    fun hasBufferedSource(): Boolean = size > 0

    fun render(
        destRate: Int,
        dest: FloatArray,
        destFrames: Int,
        readFrames: (maxFrames: Int) -> FloatArray,
    ): Int {
        require(destRate > 0) { "destRate must be > 0" }
        val maxOut = min(destFrames, dest.size / channels)
        if (maxOut <= 0) return 0

        if (destRate == sourceRate && size == 0 && phase == 0.0) {
            val decoded = readFrames(maxOut)
            if (decoded.isEmpty()) return 0
            val frames = min(decoded.size / channels, maxOut)
            decoded.copyInto(dest, endIndex = frames * channels)
            return frames
        }

        val step = sourceRate.toDouble() / destRate.toDouble()
        var out = 0
        while (out < maxOut) {
            if (!ensureFrames(2, readFrames)) {
                if (size <= 0) break
                copyFrame(0, dest, out)
                consume(step)
                out++
                continue
            }
            val t = phase
            val oneMinus = 1.0 - t
            val destOff = out * channels
            for (ch in 0 until channels) {
                val a = sample(0, ch)
                val b = sample(1, ch)
                dest[destOff + ch] = (a * oneMinus + b * t).toFloat()
            }
            consume(step)
            out++
        }
        return out
    }

    private fun sample(offset: Int, ch: Int): Float {
        val frame = (head + offset) % capacityFrames
        return ring[frame * channels + ch]
    }

    private fun copyFrame(offset: Int, dest: FloatArray, outFrame: Int) {
        val destOff = outFrame * channels
        for (ch in 0 until channels) {
            dest[destOff + ch] = sample(offset, ch)
        }
    }

    private fun consume(step: Double) {
        phase += step
        while (phase >= 1.0 && size > 0) {
            phase -= 1.0
            head = (head + 1) % capacityFrames
            size--
        }
        if (size == 0) phase = 0.0
    }

    private fun ensureFrames(needed: Int, readFrames: (Int) -> FloatArray): Boolean {
        while (size < needed) {
            val space = capacityFrames - size
            if (space <= 0) return size >= needed
            val got = readFrames(space)
            if (got.isEmpty()) return size >= needed
            val frames = got.size / channels
            if (frames <= 0) return size >= needed
            writeFrames(got, frames)
        }
        return true
    }

    private fun writeFrames(interleaved: FloatArray, frames: Int) {
        val n = min(frames, min(interleaved.size / channels, capacityFrames - size))
        var src = 0
        var tail = (head + size) % capacityFrames
        repeat(n) {
            interleaved.copyInto(ring, tail * channels, src, src + channels)
            src += channels
            tail++
            if (tail == capacityFrames) tail = 0
            size++
        }
    }
}

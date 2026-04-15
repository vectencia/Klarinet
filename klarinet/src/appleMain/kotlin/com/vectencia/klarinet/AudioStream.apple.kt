@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioSourceNode
import platform.CoreAudioTypes.AudioBufferList
import platform.posix.memset

actual class AudioStream internal constructor(
    actual val config: AudioStreamConfig,
    private val avEngine: AVAudioEngine,
    private val callback: AudioStreamCallback?,
) {

    /** Internal constructor used by the expect declaration (unused on Apple). */
    internal constructor(config: AudioStreamConfig) : this(config, AVAudioEngine(), null)

    private var _state: StreamState = StreamState.OPEN
    private var sourceNode: AVAudioSourceNode? = null

    actual val state: StreamState get() = _state

    actual val latencyInfo: LatencyInfo
        get() {
            if (_state == StreamState.CLOSED) return LatencyInfo(0.0, 0.0)
            val outputLatency = avEngine.outputNode.presentationLatency
            return LatencyInfo(
                inputLatencyMs = 0.0,
                outputLatencyMs = outputLatency * 1000.0,
            )
        }

    init {
        when (config.direction) {
            StreamDirection.OUTPUT -> setupOutputNode()
            StreamDirection.INPUT -> setupInputGraph()
        }
    }

    /**
     * For INPUT streams, connect inputNode → mainMixerNode → outputNode.
     * AVAudioEngine requires a complete graph before prepare() can be called.
     * The mainMixerNode volume is set to 0 so captured audio isn't played back.
     *
     * On iOS, AVAudioSession must be configured for recording before accessing
     * the inputNode, otherwise the format may be invalid (0 Hz on simulator).
     */
    private fun setupInputGraph() {
        // Configure audio session for recording first (iOS/tvOS).
        configurePlatformAudioSessionForInput()

        // Connect inputNode → mainMixerNode using null format (= use hardware native format).
        // Passing an explicit format can fail on simulator (0 Hz) or when the sample rate
        // doesn't match the hardware.
        avEngine.connect(avEngine.inputNode, to = avEngine.mainMixerNode, format = null)
        avEngine.mainMixerNode.outputVolume = 0f
    }

    private fun setupOutputNode() {
        val sampleRate = config.sampleRate.toDouble()
        val channelCount = config.channelCount.toUInt()
        val format = AVAudioFormat(standardFormatWithSampleRate = sampleRate, channels = channelCount)

        val cb = callback
        val cfg = config
        val stream = this

        // Create AVAudioSourceNode with explicit format so AVAudioEngine handles
        // sample rate conversion between the source (e.g., 22050 Hz) and the
        // hardware output (e.g., 48000 Hz). Without the format parameter, the
        // render block receives frames at the hardware rate, causing audio to
        // play at the wrong speed.
        val node = AVAudioSourceNode(format = format) { _, _, frameCount, outputData: CPointer<AudioBufferList>? ->
            val numFrames = frameCount.toInt()
            val totalSamples = numFrames * cfg.channelCount

            if (cb != null && stream._state == StreamState.STARTED) {
                val buffer = FloatArray(totalSamples)
                cb.onAudioReady(buffer, numFrames)

                var peak = 0f
                for (i in buffer.indices) {
                    val abs = if (buffer[i] >= 0f) buffer[i] else -buffer[i]
                    if (abs > peak) peak = abs
                }
                stream.peakLevelAtomic.set(peak)

                // Copy Kotlin FloatArray into Core Audio buffer.
                // AudioBufferList.mBuffers is the first AudioBuffer (flexible array member).
                // Access the mData pointer from it.
                if (outputData != null) {
                    val abl = outputData.pointed
                    val bufferData = abl.mBuffers.pointed.mData
                    if (bufferData != null) {
                        val floatPtr: CPointer<FloatVar> = bufferData.reinterpret()
                        for (i in 0 until totalSamples) {
                            floatPtr[i] = buffer[i]
                        }
                    }
                }
            } else {
                // Output silence when paused or no callback.
                if (outputData != null) {
                    val abl = outputData.pointed
                    val bufferData = abl.mBuffers.pointed.mData
                    if (bufferData != null) {
                        memset(bufferData, 0, (totalSamples * 4).convert())
                    }
                }
            }
            return@AVAudioSourceNode 0
        }

        sourceNode = node
        avEngine.attachNode(node)
        avEngine.connect(node, to = avEngine.mainMixerNode, format = format)
    }

    private fun installInputTap() {
        val inputNode = avEngine.inputNode
        val bufferSize: UInt = if (config.bufferCapacityInFrames > 0) {
            config.bufferCapacityInFrames.toUInt()
        } else {
            1024u
        }

        // Pass null format to use the input node's native hardware format.
        // Passing a custom format with a different sample rate causes a crash:
        // "format.sampleRate == inputHWFormat.sampleRate"
        inputNode.installTapOnBus(
            bus = 0u,
            bufferSize = bufferSize,
            format = null,
        ) { buffer, _ ->
            if (buffer == null || callback == null) return@installTapOnBus
            val pcmBuffer = buffer
            val floatChannelData = pcmBuffer.floatChannelData
                ?: return@installTapOnBus
            val frameLength = pcmBuffer.frameLength.toInt()
            val channels = config.channelCount
            val totalSamples = frameLength * channels
            val audioData = FloatArray(totalSamples)

            for (frame in 0 until frameLength) {
                for (ch in 0 until channels) {
                    val channelPtr = floatChannelData[ch]
                    if (channelPtr != null) {
                        audioData[frame * channels + ch] = channelPtr[frame]
                    }
                }
            }

            callback.onAudioReady(audioData, frameLength)

            var peakLevel = 0f
            for (i in audioData.indices) {
                val abs = if (audioData[i] >= 0f) audioData[i] else -audioData[i]
                if (abs > peakLevel) peakLevel = abs
            }
            peakLevelAtomic.set(peakLevel)
        }
    }

    actual fun start() {
        when (_state) {
            StreamState.OPEN, StreamState.PAUSED -> {
                _state = StreamState.STARTING
                try {
                    avEngine.prepare()
                    avEngine.startAndReturnError(null)

                    if (config.direction == StreamDirection.INPUT) {
                        installInputTap()
                    }

                    _state = StreamState.STARTED
                    callback?.onStreamStateChanged(this, _state)
                } catch (e: Exception) {
                    _state = StreamState.OPEN
                    throw StreamOperationException("Failed to start audio stream: ${e.message}", e)
                }
            }
            StreamState.STARTED -> { /* Already started, no-op */ }
            else -> throw StreamOperationException("Cannot start stream in state $_state")
        }
    }

    actual fun pause() {
        when (_state) {
            StreamState.STARTED -> {
                _state = StreamState.PAUSING
                avEngine.pause()
                _state = StreamState.PAUSED
                callback?.onStreamStateChanged(this, _state)
            }
            StreamState.PAUSED -> { /* Already paused, no-op */ }
            else -> throw StreamOperationException("Cannot pause stream in state $_state")
        }
    }

    actual fun stop() {
        when (_state) {
            StreamState.STARTED, StreamState.PAUSED -> {
                _state = StreamState.STOPPING

                if (config.direction == StreamDirection.INPUT) {
                    avEngine.inputNode.removeTapOnBus(0u)
                }
                avEngine.stop()

                _state = StreamState.STOPPED
                callback?.onStreamStateChanged(this, _state)
            }
            StreamState.STOPPED -> { /* Already stopped, no-op */ }
            else -> throw StreamOperationException("Cannot stop stream in state $_state")
        }
    }

    actual fun close() {
        if (_state == StreamState.CLOSED) return
        if (_state == StreamState.STARTED || _state == StreamState.PAUSED) {
            try { stop() } catch (_: Exception) { /* best-effort */ }
        }

        _state = StreamState.CLOSING

        sourceNode?.let { avEngine.detachNode(it) }
        sourceNode = null

        _state = StreamState.CLOSED
        callback?.onStreamStateChanged(this, _state)
    }

    /**
     * Push-model write is not supported on Apple platforms.
     *
     * Apple's AVAudioEngine uses a pull-model via render callbacks
     * (AVAudioSourceNode). Use [AudioStreamCallback.onAudioReady] instead.
     *
     * @throws StreamOperationException always.
     */
    actual fun write(data: FloatArray, numFrames: Int, timeoutNanos: Long): Int {
        throw StreamOperationException(
            "Push-model write is not supported on Apple platforms. " +
                "Use AudioStreamCallback.onAudioReady for output streams."
        )
    }

    /**
     * Push-model read is not supported on Apple platforms.
     *
     * Apple's AVAudioEngine uses a pull-model via render callbacks
     * (AVAudioSinkNode / installTap). Use [AudioStreamCallback.onAudioReady] instead.
     *
     * @throws StreamOperationException always.
     */
    actual fun read(data: FloatArray, numFrames: Int, timeoutNanos: Long): Int {
        throw StreamOperationException(
            "Push-model read is not supported on Apple platforms. " +
                "Use AudioStreamCallback.onAudioReady for input streams."
        )
    }

    actual var effectChain: AudioEffectChain? = null

    actual val peakLevel: Float get() = peakLevelAtomic.get()

    internal actual val peakLevelAtomic = AtomicFloat(0f)
}

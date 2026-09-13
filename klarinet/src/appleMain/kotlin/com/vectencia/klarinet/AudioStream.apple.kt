@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import klarinet_dsp.klarinet_chain_process
import klarinet_dsp.klarinet_offload_create
import klarinet_dsp.klarinet_offload_destroy
import klarinet_dsp.klarinet_offload_process
import klarinet_dsp.klarinet_offload_set_xrun_callback
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioSourceNode
import platform.CoreAudioTypes.AudioBufferList

actual class AudioStream internal constructor(
    actual val config: AudioStreamConfig,
    private val avEngine: AVAudioEngine,
    private val callback: AudioStreamCallback?,
) : AutoCloseable {

    /** Internal constructor used by the expect declaration (unused on Apple). */
    internal constructor(config: AudioStreamConfig) : this(config, AVAudioEngine(), null)

    private var _state: StreamState = StreamState.OPEN
    private var sourceNode: AVAudioSourceNode? = null
    private var offload: COpaquePointer? = null
    private var offloadUser: StableRef<AppleAudioUser>? = null
    private val inputScratch = FloatArray(8192)

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

        val cfg = config
        val stream = this
        startOffload(isCapture = false)

        // Create AVAudioSourceNode with explicit format so AVAudioEngine handles
        // sample rate conversion between the source (e.g., 22050 Hz) and the
        // hardware output (e.g., 48000 Hz). Without the format parameter, the
        // render block receives frames at the hardware rate, causing audio to
        // play at the wrong speed.
        val node = AVAudioSourceNode(format = format) { _, _, frameCount, outputData: CPointer<AudioBufferList>? ->
            val numFrames = frameCount.toInt()
            val floatPtr = outputFloatPointer(outputData)
            if (floatPtr != null && stream._state == StreamState.STARTED) {
                stream.offload?.let { klarinet_offload_process(it, floatPtr, numFrames) }
                    ?: run {
                        for (i in 0 until numFrames * cfg.channelCount) floatPtr[i] = 0f
                    }
                stream.processChainOnAudioThread(floatPtr, numFrames)
                stream.updatePeakFrom(floatPtr, numFrames * cfg.channelCount)
            } else if (floatPtr != null) {
                for (i in 0 until numFrames * cfg.channelCount) floatPtr[i] = 0f
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
        startOffload(isCapture = true)
        val channels = config.channelCount
        installPlatformInputTap(avEngine, bufferSize) { buffer ->
            if (buffer == null) return@installPlatformInputTap
            val floatChannelData = buffer.floatChannelData
                ?: return@installPlatformInputTap
            val frameLength = buffer.frameLength.toInt()
            val totalSamples = frameLength * channels
            if (totalSamples > inputScratch.size) return@installPlatformInputTap
            for (frame in 0 until frameLength) {
                for (ch in 0 until channels) {
                    val channelPtr = floatChannelData[ch]
                    if (channelPtr != null) {
                        inputScratch[frame * channels + ch] = channelPtr[frame]
                    }
                }
            }
            inputScratch.usePinned { pinned ->
                val ptr = pinned.addressOf(0)
                processChainOnAudioThread(ptr, frameLength)
                offload?.let { klarinet_offload_process(it, ptr, frameLength) }
                updatePeakFrom(ptr, totalSamples)
            }
        }
    }

    actual fun start() {
        requireActive(_state != StreamState.CLOSED, "AudioStream")
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
        requireActive(_state != StreamState.CLOSED, "AudioStream")
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
        requireActive(_state != StreamState.CLOSED, "AudioStream")
        when (_state) {
            StreamState.STARTED, StreamState.PAUSED -> {
                _state = StreamState.STOPPING

                if (config.direction == StreamDirection.INPUT) {
                    removePlatformInputTap(avEngine)
                }
                avEngine.stop()

                _state = StreamState.STOPPED
                callback?.onStreamStateChanged(this, _state)
            }
            StreamState.STOPPED -> { /* Already stopped, no-op */ }
            else -> throw StreamOperationException("Cannot stop stream in state $_state")
        }
    }

    actual override fun close() {
        if (_state == StreamState.CLOSED) return
        if (_state == StreamState.STARTED || _state == StreamState.PAUSED) {
            try { stop() } catch (_: Exception) { /* best-effort */ }
        }

        _state = StreamState.CLOSING

        sourceNode?.let { avEngine.detachNode(it) }
        sourceNode = null
        destroyOffload()

        _state = StreamState.CLOSED
        callback?.onStreamStateChanged(this, _state)
    }

    private fun burstFrames(): Int =
        if (config.bufferCapacityInFrames > 0) config.bufferCapacityInFrames else 256

    private fun startOffload(isCapture: Boolean) {
        val cb = callback ?: return
        destroyOffload()
        val user = StableRef.create(AppleAudioUser(cb, this, config.channelCount))
        offloadUser = user
        offload = klarinet_offload_create(
            burstFrames(),
            config.channelCount,
            if (isCapture) 1 else 0,
            appleUserAudioCallback,
            user.asCPointer(),
        )
        offload?.let { handle -> klarinet_offload_set_xrun_callback(handle, appleXrunCallback) }
    }

    private fun destroyOffload() {
        offload?.let { klarinet_offload_destroy(it) }
        offload = null
        offloadUser?.dispose()
        offloadUser = null
    }

    private fun outputFloatPointer(outputData: CPointer<AudioBufferList>?): CPointer<FloatVar>? {
        val bufferData = outputData?.pointed?.mBuffers?.pointed?.mData ?: return null
        return bufferData.reinterpret()
    }

    private fun processChainOnAudioThread(samples: CPointer<FloatVar>, numFrames: Int) {
        val handle = effectChain?.handle ?: return
        klarinet_chain_process(handle, samples, numFrames, config.channelCount)
    }

    private fun updatePeakFrom(samples: CPointer<FloatVar>, totalSamples: Int) {
        var peak = 0f
        for (i in 0 until totalSamples) {
            val value = samples[i]
            val abs = if (value >= 0f) value else -value
            if (abs > peak) peak = abs
        }
        peakLevelAtomic.set(peak)
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
        set(value) {
            requireActive(_state != StreamState.CLOSED, "AudioStream")
            value?.prepare(config.sampleRate, config.channelCount)
            field = value
        }

    actual val peakLevel: Float get() = peakLevelAtomic.get()

    internal actual val peakLevelAtomic = AtomicFloat(0f)
}

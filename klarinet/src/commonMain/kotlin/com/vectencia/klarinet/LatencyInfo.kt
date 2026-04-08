package com.vectencia.klarinet

/**
 * Latency measurements for an [AudioStream], reported in milliseconds.
 *
 * Latency is the delay between when audio data is produced and when it is
 * heard (output) or between when a sound occurs and when the application
 * receives the corresponding samples (input). These values are estimates
 * provided by the platform and may fluctuate over the lifetime of a stream.
 *
 * Access the current latency via [AudioStream.latencyInfo]. The values are
 * only meaningful while the stream is in the [StreamState.STARTED] state;
 * before starting, both values will typically be zero.
 *
 * @property inputLatencyMs Estimated input (recording) latency in milliseconds.
 *   This is the time between when a sound reaches the microphone and when the
 *   corresponding samples are delivered to [AudioStreamCallback.onAudioReady].
 *   For output-only streams, this value is typically 0.0.
 * @property outputLatencyMs Estimated output (playback) latency in milliseconds.
 *   This is the time between when the application writes audio samples and
 *   when the sound is emitted by the speaker or headphone. For input-only
 *   streams, this value is typically 0.0.
 *
 * @see AudioStream.latencyInfo
 */
data class LatencyInfo(
    val inputLatencyMs: Double,
    val outputLatencyMs: Double,
)

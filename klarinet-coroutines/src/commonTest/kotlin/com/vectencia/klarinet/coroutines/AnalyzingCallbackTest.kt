package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioAnalyzer
import com.vectencia.klarinet.AudioStreamCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyzingCallbackTest {

    @Test
    fun emitsAnalysisResultOnAudioReady() = runTest {
        val analyzer = AudioAnalyzer(fftSize = 256, sampleRate = 44100)
        val scope = CoroutineScope(Dispatchers.Default)
        val callback = AnalyzingCallback(analyzer, scope = scope)

        val buffer = FloatArray(256) { i ->
            sin(2.0 * PI * 300.0 * i / 44100).toFloat()
        }
        callback.onAudioReady(buffer, 256)

        val result = callback.results.first()
        assertTrue(result.rmsLevel > 0f)
        assertTrue(result.magnitudeSpectrum.isNotEmpty())
        scope.cancel()
    }

    @Test
    fun delegatesOnAudioReadyToInnerCallback() = runTest {
        val analyzer = AudioAnalyzer(fftSize = 256, sampleRate = 44100)
        var delegateCalled = false
        val delegate = object : AudioStreamCallback {
            override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                delegateCalled = true
                return numFrames
            }
        }
        val scope = CoroutineScope(Dispatchers.Default)
        val callback = AnalyzingCallback(analyzer, delegate = delegate, scope = scope)

        callback.onAudioReady(FloatArray(256), 256)
        assertTrue(delegateCalled)
        scope.cancel()
    }

    @Test
    fun returnsFrameCountFromDelegate() = runTest {
        val analyzer = AudioAnalyzer(fftSize = 256, sampleRate = 44100)
        val delegate = object : AudioStreamCallback {
            override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int = 42
        }
        val scope = CoroutineScope(Dispatchers.Default)
        val callback = AnalyzingCallback(analyzer, delegate = delegate, scope = scope)

        val result = callback.onAudioReady(FloatArray(256), 256)
        assertEquals(42, result)
        scope.cancel()
    }

    @Test
    fun returnsNumFramesWhenNoDelegate() = runTest {
        val analyzer = AudioAnalyzer(fftSize = 256, sampleRate = 44100)
        val scope = CoroutineScope(Dispatchers.Default)
        val callback = AnalyzingCallback(analyzer, scope = scope)

        val result = callback.onAudioReady(FloatArray(256), 256)
        assertEquals(256, result)
        scope.cancel()
    }

    @Test
    fun stereoBufferIsDownmixedNotTreatedAsMonoPrefix() = runTest {
        val analyzer = AudioAnalyzer(fftSize = 256, sampleRate = 44100)
        val scope = CoroutineScope(Dispatchers.Default)
        val callback = AnalyzingCallback(analyzer, scope = scope)

        val frames = 256
        val stereo = FloatArray(frames * 2)
        for (i in 0 until frames) {
            stereo[i * 2] = 0.8f
            stereo[i * 2 + 1] = 0f
        }
        callback.onAudioReady(stereo, frames)

        val result = callback.results.first()
        assertEquals(0.4f, result.peakLevel, 0.05f)
        callback.close()
        scope.cancel()
    }

    @Test
    fun closeStopsWorkerAndFurtherCallbacksAreIgnored() = runTest {
        val analyzer = AudioAnalyzer(fftSize = 256, sampleRate = 44100)
        val scope = CoroutineScope(Dispatchers.Default)
        val callback = AnalyzingCallback(analyzer, scope = scope)

        callback.onAudioReady(FloatArray(256) { 0.2f }, 256)
        callback.results.first()
        callback.close()
        callback.close()
        assertEquals(256, callback.onAudioReady(FloatArray(256) { 1f }, 256))
        scope.cancel()
    }
}

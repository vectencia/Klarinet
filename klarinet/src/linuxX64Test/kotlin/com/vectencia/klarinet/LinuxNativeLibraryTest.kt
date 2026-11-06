@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import klarinet_audio_test.klarinet_test_offload_user_stays_off_the_audio_thread
import klarinet_audio_test.klarinet_test_playback_chain
import klarinet_dsp.klarinet_chain_add
import klarinet_dsp.klarinet_chain_clear
import klarinet_dsp.klarinet_chain_create
import klarinet_dsp.klarinet_chain_destroy
import klarinet_dsp.klarinet_chain_get_effect_count
import klarinet_dsp.klarinet_chain_prepare
import klarinet_dsp.klarinet_chain_process
import klarinet_dsp.klarinet_create_effect
import klarinet_dsp.klarinet_effect_destroy
import klarinet_dsp.klarinet_effect_get_parameter
import klarinet_dsp.klarinet_effect_get_type
import klarinet_dsp.klarinet_effect_is_enabled
import klarinet_dsp.klarinet_effect_set_enabled
import klarinet_dsp.klarinet_effect_set_parameter
import klarinet_native.klarinet_context_init
import klarinet_native.klarinet_context_uninit
import klarinet_native.klarinet_decoder_init_file
import klarinet_native.klarinet_decoder_read_frames
import klarinet_native.klarinet_decoder_uninit
import klarinet_native.klarinet_encoder_init_file
import klarinet_native.klarinet_encoder_uninit
import klarinet_native.klarinet_encoder_write_frames
import klarinet_native.klarinet_get_capture_device_count
import klarinet_native.klarinet_get_playback_device_count
import klarinet_native.klarinet_get_playback_device_name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned

class LinuxNativeLibraryTest {
    @Test
    fun nullContextUsesTheRustNativeEntrypoints() {
        assertEquals(0, klarinet_get_playback_device_count(null))
        assertEquals(0, klarinet_get_capture_device_count(null))
        assertEquals("Unknown", klarinet_get_playback_device_name(null, 0)?.toKString())
        val context = klarinet_context_init()
        if (context != null) {
            klarinet_context_uninit(context)
        }
    }

    @Test
    fun gainOfMinusTwentyDecibelsSurvivesDestroyWhileTheChainHoldsIt() {
        val effect = klarinet_create_effect(0)
        assertNotNull(effect)
        assertEquals(0, klarinet_effect_get_type(effect))
        assertEquals(1, klarinet_effect_is_enabled(effect))
        klarinet_effect_set_parameter(effect, 0, -20f)
        assertEquals(-20f, klarinet_effect_get_parameter(effect, 0), 0.001f)

        val chain = klarinet_chain_create()
        assertNotNull(chain)
        klarinet_chain_add(chain, effect)
        assertEquals(1, klarinet_chain_get_effect_count(chain))
        klarinet_chain_prepare(chain, 48_000, 1)

        klarinet_effect_set_enabled(effect, 0)
        val bypass = floatArrayOf(1f)
        bypass.usePinned { pinned ->
            klarinet_chain_process(chain, pinned.addressOf(0), 1, 1)
        }
        assertEquals(1f, bypass[0], 0.001f)

        klarinet_effect_set_enabled(effect, 1)
        klarinet_effect_destroy(effect)
        val attenuated = floatArrayOf(1f)
        attenuated.usePinned { pinned ->
            klarinet_chain_process(chain, pinned.addressOf(0), 1, 1)
        }
        assertEquals(0.1f, attenuated[0], 0.001f)

        klarinet_chain_clear(chain)
        attenuated[0] = 1f
        attenuated.usePinned { pinned ->
            klarinet_chain_process(chain, pinned.addressOf(0), 1, 1)
        }
        assertEquals(1f, attenuated[0], 0.001f)
        klarinet_chain_destroy(chain)
    }

    @Test
    fun playbackCallbackAppliesGainOnTheAudioThread() {
        val effect = klarinet_create_effect(0)
        assertNotNull(effect)
        klarinet_effect_set_parameter(effect, 0, -20f)
        val chain = klarinet_chain_create()
        assertNotNull(chain)
        klarinet_chain_add(chain, effect)
        klarinet_chain_prepare(chain, 48_000, 1)
        val buffer = floatArrayOf(1f, 1f)
        buffer.usePinned { pinned ->
            klarinet_test_playback_chain(chain, pinned.addressOf(0), 2, 1)
        }
        assertEquals(0.1f, buffer[0], 0.001f)
        assertEquals(0.1f, buffer[1], 0.001f)
        klarinet_chain_destroy(chain)
        klarinet_effect_destroy(effect)
    }

    @Test
    fun offloadUserCallbackStaysOffTheAudioThread() {
        assertEquals(0, klarinet_test_offload_user_stays_off_the_audio_thread(8, 1))
    }

    @Test
    fun nullDecoderOutputDiscardsFrames() {
        memScoped {
            val path = "/tmp/klarinet-phase5-discard.wav".cstr.ptr
            val encoder = klarinet_encoder_init_file(path, 0, 1, 48_000)
            assertNotNull(encoder)
            val samples = FloatArray(8) { 0.25f }
            samples.usePinned { pinned ->
                assertEquals(0, klarinet_encoder_write_frames(encoder, pinned.addressOf(0), 8))
            }
            klarinet_encoder_uninit(encoder)
            val decoder = klarinet_decoder_init_file(path)
            assertNotNull(decoder)
            assertEquals(8L, klarinet_decoder_read_frames(decoder, null, 8))
            val tail = FloatArray(1)
            val remaining = tail.usePinned { pinned ->
                klarinet_decoder_read_frames(decoder, pinned.addressOf(0), 1)
            }
            assertEquals(0L, remaining)
            klarinet_decoder_uninit(decoder)
        }
    }
}

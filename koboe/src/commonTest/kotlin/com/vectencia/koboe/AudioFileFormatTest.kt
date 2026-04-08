package com.vectencia.koboe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioFileFormatTest {

    @Test
    fun allEnumValuesExist() {
        val entries = AudioFileFormat.entries
        assertEquals(4, entries.size)
        assertTrue(entries.contains(AudioFileFormat.WAV))
        assertTrue(entries.contains(AudioFileFormat.MP3))
        assertTrue(entries.contains(AudioFileFormat.AAC))
        assertTrue(entries.contains(AudioFileFormat.M4A))
    }

    @Test
    fun unsupportedFormatExceptionIsKoboeException() {
        val ex = UnsupportedFormatException("FLAC not supported")
        assertIs<KoboeException>(ex)
        assertIs<Exception>(ex)
        assertEquals("FLAC not supported", ex.message)
    }

    @Test
    fun audioFileExceptionIsKoboeExceptionWithoutCause() {
        val ex = AudioFileException("file not found")
        assertIs<KoboeException>(ex)
        assertEquals("file not found", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun audioFileExceptionIsKoboeExceptionWithCause() {
        val cause = RuntimeException("I/O error")
        val ex = AudioFileException("read failed", cause)
        assertIs<KoboeException>(ex)
        assertEquals("read failed", ex.message)
        assertNotNull(ex.cause)
        assertEquals(cause, ex.cause)
    }
}

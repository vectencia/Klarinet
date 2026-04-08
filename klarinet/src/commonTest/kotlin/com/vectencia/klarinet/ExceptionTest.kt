package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class ExceptionTest {

    @Test
    fun klarinetExceptionMessageAndCause() {
        val cause = RuntimeException("root cause")
        val ex = KlarinetException("test message", cause)
        assertEquals("test message", ex.message)
        assertEquals(cause, ex.cause)
    }

    @Test
    fun klarinetExceptionWithoutCause() {
        val ex = KlarinetException("no cause")
        assertEquals("no cause", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun streamCreationExceptionIsKlarinetException() {
        val ex = StreamCreationException("failed to create stream")
        assertIs<KlarinetException>(ex)
        assertIs<Exception>(ex)
        assertEquals("failed to create stream", ex.message)
    }

    @Test
    fun streamOperationExceptionIsKlarinetException() {
        val cause = IllegalStateException("bad state")
        val ex = StreamOperationException("operation failed", cause)
        assertIs<KlarinetException>(ex)
        assertEquals("operation failed", ex.message)
        assertNotNull(ex.cause)
    }

    @Test
    fun deviceNotFoundExceptionIsKlarinetException() {
        val ex = DeviceNotFoundException("device 42 not found")
        assertIs<KlarinetException>(ex)
        assertEquals("device 42 not found", ex.message)
    }

    @Test
    fun audioSessionExceptionIsKlarinetException() {
        val ex = AudioSessionException("session error")
        assertIs<KlarinetException>(ex)
        assertEquals("session error", ex.message)
    }

    @Test
    fun permissionExceptionIsKlarinetException() {
        val ex = PermissionException("microphone permission denied")
        assertIs<KlarinetException>(ex)
        assertEquals("microphone permission denied", ex.message)
    }
}

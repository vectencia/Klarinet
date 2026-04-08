package com.vectencia.koboe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class ExceptionTest {

    @Test
    fun koboeExceptionMessageAndCause() {
        val cause = RuntimeException("root cause")
        val ex = KoboeException("test message", cause)
        assertEquals("test message", ex.message)
        assertEquals(cause, ex.cause)
    }

    @Test
    fun koboeExceptionWithoutCause() {
        val ex = KoboeException("no cause")
        assertEquals("no cause", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun streamCreationExceptionIsKoboeException() {
        val ex = StreamCreationException("failed to create stream")
        assertIs<KoboeException>(ex)
        assertIs<Exception>(ex)
        assertEquals("failed to create stream", ex.message)
    }

    @Test
    fun streamOperationExceptionIsKoboeException() {
        val cause = IllegalStateException("bad state")
        val ex = StreamOperationException("operation failed", cause)
        assertIs<KoboeException>(ex)
        assertEquals("operation failed", ex.message)
        assertNotNull(ex.cause)
    }

    @Test
    fun deviceNotFoundExceptionIsKoboeException() {
        val ex = DeviceNotFoundException("device 42 not found")
        assertIs<KoboeException>(ex)
        assertEquals("device 42 not found", ex.message)
    }

    @Test
    fun audioSessionExceptionIsKoboeException() {
        val ex = AudioSessionException("session error")
        assertIs<KoboeException>(ex)
        assertEquals("session error", ex.message)
    }

    @Test
    fun permissionExceptionIsKoboeException() {
        val ex = PermissionException("microphone permission denied")
        assertIs<KoboeException>(ex)
        assertEquals("microphone permission denied", ex.message)
    }
}

package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertTrue

class RecordPermissionJvmTest {

    @Test
    fun jvmReportsGrantedWithoutAPrompt() {
        val session = AudioSessionManager()
        assertTrue(session.hasRecordPermission())
        var granted = false
        session.requestRecordPermission { granted = it }
        assertTrue(granted)
    }
}

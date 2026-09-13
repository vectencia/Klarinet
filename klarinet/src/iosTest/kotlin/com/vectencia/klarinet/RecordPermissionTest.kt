package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecordPermissionTest {

    @Test
    fun hasRecordPermissionDoesNotThrow() {
        AudioSessionManager().hasRecordPermission()
    }

    @Test
    fun requestReportsImmediatelyWhenAlreadyGranted() {
        val session = AudioSessionManager()
        if (!session.hasRecordPermission()) return
        var granted: Boolean? = null
        session.requestRecordPermission { granted = it }
        assertTrue(granted == true)
    }

    @Test
    fun inputOpenThrowsWhenRecordPermissionMissing() {
        if (AudioSessionManager().hasRecordPermission()) return
        AudioEngine.create().use { engine ->
            assertFailsWith<PermissionException> {
                engine.openStream(AudioStreamConfig(direction = StreamDirection.INPUT))
            }
        }
    }
}

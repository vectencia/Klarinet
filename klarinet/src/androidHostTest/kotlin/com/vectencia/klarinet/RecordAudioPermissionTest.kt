package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertFailsWith

class RecordAudioPermissionTest {

    @Test
    fun deniedRecordAudioThrowsPermissionException() {
        assertFailsWith<PermissionException> {
            throwIfRecordAudioDenied(false)
        }
    }

    @Test
    fun grantedRecordAudioDoesNotThrow() {
        throwIfRecordAudioDenied(true)
    }

    @Test
    fun unknownRecordAudioDoesNotThrow() {
        throwIfRecordAudioDenied(null)
    }
}

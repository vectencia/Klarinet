package com.spiritbox

import com.spiritbox.data.askMic
import com.spiritbox.data.bindMicPermission
import com.spiritbox.data.micGranted
import com.spiritbox.data.releaseMicPermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MicPermissionReleaseTest {
    @Test
    fun releaseDropsTheHooksTheActivityInstalled() {
        var grants = 0
        var asks = 0
        bindMicPermission(
            allowed = {
                grants += 1
                true
            },
            ask = { callback ->
                asks += 1
                callback(true)
            },
        )
        check(micGranted())
        var installed = false
        askMic { installed = it }
        check(installed)
        grants = 0
        asks = 0

        releaseMicPermission()

        assertFalse(micGranted())
        var result: Boolean? = null
        askMic { result = it }
        assertEquals(false, result)
        assertEquals(0, grants)
        assertEquals(0, asks)
    }
}

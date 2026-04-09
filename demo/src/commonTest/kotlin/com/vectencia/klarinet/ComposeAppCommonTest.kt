package com.vectencia.klarinet

import com.vectencia.klarinet.demo.Screen
import kotlin.test.Test
import kotlin.test.assertEquals

class DemoTest {
    @Test
    fun allScreensExist() {
        assertEquals(3, Screen.entries.size)
    }
}

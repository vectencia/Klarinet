package com.vectencia.koboe

import com.vectencia.koboe.demo.Screen
import kotlin.test.Test
import kotlin.test.assertEquals

class DemoTest {
    @Test
    fun allScreensExist() {
        assertEquals(3, Screen.entries.size)
    }
}

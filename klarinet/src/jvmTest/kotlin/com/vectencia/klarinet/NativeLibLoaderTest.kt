package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertTrue

class NativeLibLoaderTest {
    @Test fun detectOsReturnsKnownValue() {
        assertTrue(NativeLibLoader.detectOs() in listOf("macos", "linux", "windows"))
    }
    @Test fun detectArchReturnsKnownValue() {
        assertTrue(NativeLibLoader.detectArch() in listOf("arm64", "x64"))
    }
    @Test fun libNameFormattedCorrectly() {
        val os = NativeLibLoader.detectOs()
        val name = NativeLibLoader.mapLibName("klarinet_jvm", os)
        when (os) {
            "macos" -> assertTrue(name == "libklarinet_jvm.dylib")
            "linux" -> assertTrue(name == "libklarinet_jvm.so")
            "windows" -> assertTrue(name == "klarinet_jvm.dll")
        }
    }
}

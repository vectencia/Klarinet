package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeLibLoaderTest {
    @Test fun detectOsReturnsKnownValue() {
        assertTrue(NativeLibLoader.detectOs() in listOf("macos", "linux", "windows"))
    }
    @Test fun detectArchReturnsKnownValue() {
        assertTrue(NativeLibLoader.detectArch() in listOf("arm64", "x64"))
    }
    @Test fun libNameFormattedCorrectly() {
        assertEquals("libklarinet_jvm.dylib", NativeLibLoader.mapLibName("klarinet_jvm", "macos"))
        assertEquals("libklarinet_jvm.so", NativeLibLoader.mapLibName("klarinet_jvm", "linux"))
        assertEquals("klarinet_jvm.dll", NativeLibLoader.mapLibName("klarinet_jvm", "windows"))
    }

    @Test fun packagedNativesExistForDesktopHosts() {
        val loader = NativeLibLoaderTest::class.java.classLoader
        listOf(
            "natives/macos-arm64/libklarinet_jvm.dylib",
            "natives/macos-x64/libklarinet_jvm.dylib",
            "natives/linux-x64/libklarinet_jvm.so",
            "natives/linux-arm64/libklarinet_jvm.so",
            "natives/windows-x64/klarinet_jvm.dll",
        ).forEach { path ->
            assertNotNull(loader.getResource(path), path)
        }
    }
}

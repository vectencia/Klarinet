package com.vectencia.klarinet

import java.io.File
import java.io.FileOutputStream

internal object NativeLibLoader {
    private var loaded = false

    @Synchronized
    fun load(libName: String) {
        if (loaded) return
        val os = detectOs()
        val arch = detectArch()
        val fileName = mapLibName(libName, os)
        val resourcePath = "natives/$os-$arch/$fileName"

        val stream = NativeLibLoader::class.java.classLoader?.getResourceAsStream(resourcePath)
            ?: throw UnsatisfiedLinkError("Native library not found in JAR: $resourcePath")

        val tempDir = File(System.getProperty("java.io.tmpdir"), "klarinet-natives")
        tempDir.mkdirs()
        val tempFile = File(tempDir, fileName)

        stream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile.deleteOnExit()
        System.load(tempFile.absolutePath)
        loaded = true
    }

    fun detectOs(): String {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("mac") || osName.contains("darwin") -> "macos"
            osName.contains("linux") -> "linux"
            osName.contains("win") -> "windows"
            else -> throw UnsatisfiedLinkError("Unsupported OS: $osName")
        }
    }

    fun detectArch(): String {
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
            arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64") -> "x64"
            else -> throw UnsatisfiedLinkError("Unsupported architecture: $arch")
        }
    }

    fun mapLibName(libName: String, os: String): String {
        return when (os) {
            "macos" -> "lib$libName.dylib"
            "linux" -> "lib$libName.so"
            "windows" -> "$libName.dll"
            else -> throw UnsatisfiedLinkError("Unsupported OS: $os")
        }
    }
}

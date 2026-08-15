import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun dspCmakeArguments(targetName: String): List<String> = when (targetName) {
    "macosArm64" -> listOf("-DCMAKE_OSX_ARCHITECTURES=arm64")
    "iosSimulatorArm64" -> listOf(
        "-DCMAKE_SYSTEM_NAME=iOS",
        "-DCMAKE_OSX_SYSROOT=iphonesimulator",
        "-DCMAKE_OSX_ARCHITECTURES=arm64",
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0",
    )
    "iosArm64" -> listOf(
        "-DCMAKE_SYSTEM_NAME=iOS",
        "-DCMAKE_OSX_SYSROOT=iphoneos",
        "-DCMAKE_OSX_ARCHITECTURES=arm64",
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0",
    )
    "iosX64" -> listOf(
        "-DCMAKE_SYSTEM_NAME=iOS",
        "-DCMAKE_OSX_SYSROOT=iphonesimulator",
        "-DCMAKE_OSX_ARCHITECTURES=x86_64",
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0",
    )
    "tvosSimulatorArm64" -> listOf(
        "-DCMAKE_SYSTEM_NAME=tvOS",
        "-DCMAKE_OSX_SYSROOT=appletvsimulator",
        "-DCMAKE_OSX_ARCHITECTURES=arm64",
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0",
    )
    "tvosArm64" -> listOf(
        "-DCMAKE_SYSTEM_NAME=tvOS",
        "-DCMAKE_OSX_SYSROOT=appletvos",
        "-DCMAKE_OSX_ARCHITECTURES=arm64",
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0",
    )
    "watchosSimulatorArm64" -> listOf(
        "-DCMAKE_SYSTEM_NAME=watchOS",
        "-DCMAKE_OSX_SYSROOT=watchsimulator",
        "-DCMAKE_OSX_ARCHITECTURES=arm64",
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=8.0",
    )
    "watchosArm64" -> listOf(
        "-DCMAKE_SYSTEM_NAME=watchOS",
        "-DCMAKE_OSX_SYSROOT=watchos",
        "-DCMAKE_OSX_ARCHITECTURES=arm64",
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=8.0",
    )
    else -> emptyList()
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.vanniktechPublish)
    alias(libs.plugins.dokka)
}

val dspSourceDir = layout.projectDirectory.dir("src/cpp/dsp")

kotlin {
    android {
        namespace = "com.vectencia.klarinet"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        withHostTest {}
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    jvm()

    js {
        browser {
            testTask {
                enabled = false
            }
        }
    }

    val nativeDesktopTargets = listOf(
        linuxX64(),
        linuxArm64(),
        mingwX64(),
    )

    nativeDesktopTargets.forEach { target ->
        target.compilations.getByName("main") {
            cinterops {
                val klarinet_native by creating {
                    defFile(project.file("src/nativeInterop/cinterop/klarinet_native.def"))
                    includeDirs(project.file("src/nativeInterop/cinterop"))
                }
                val klarinet_dsp by creating {
                    defFile(project.file("src/nativeInterop/cinterop/klarinet_dsp.def"))
                    includeDirs(project.file("src/cpp/dsp"))
                }
            }
        }
    }

    val appleTargets = listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
        macosArm64(),
        tvosArm64(),
        tvosSimulatorArm64(),
        watchosArm64(),
        watchosSimulatorArm64(),
    )

    appleTargets.forEach { target ->
        target.binaries.framework {
            baseName = "Klarinet"
            isStatic = true
        }
        target.compilations.getByName("main") {
            cinterops {
                val klarinet_dsp by creating {
                    defFile(project.file("src/nativeInterop/cinterop/klarinet_dsp.def"))
                    includeDirs(project.file("src/cpp/dsp"))
                }
            }
        }
        val dspOut = layout.buildDirectory.dir("dsp-${target.name}")
        val configureDsp = tasks.register<Exec>("configureDsp${target.name.replaceFirstChar { it.uppercase() }}") {
            workingDir = dspSourceDir.asFile
            inputs.dir(dspSourceDir)
            outputs.dir(dspOut)
            commandLine(
                buildList {
                    add("cmake")
                    add("-S")
                    add(dspSourceDir.asFile.absolutePath)
                    add("-B")
                    add(dspOut.get().asFile.absolutePath)
                    add("-DKLARINET_DSP_BUILD_TESTS=OFF")
                    addAll(dspCmakeArguments(target.name))
                },
            )
        }
        val compileDsp = tasks.register<Exec>("compileDsp${target.name.replaceFirstChar { it.uppercase() }}") {
            dependsOn(configureDsp)
            inputs.dir(dspSourceDir)
            outputs.dir(dspOut)
            commandLine("cmake", "--build", dspOut.get().asFile.absolutePath)
        }
        target.binaries.all {
            linkerOpts(
                "-L${dspOut.get().asFile.absolutePath}",
                "-lklarinet-dsp",
                "-lc++",
                "-lpthread",
            )
        }
        tasks.matching { task ->
            val n = task.name.lowercase()
            n.contains("link") && n.contains(target.name.lowercase())
        }.configureEach {
            dependsOn(compileDsp)
        }
    }

    applyDefaultHierarchyTemplate()

    // Expect/actual classes are still opt-in (KT-61573).
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {}
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        androidMain.dependencies {
            api(project(":klarinet-android"))
        }

        // Intermediate source set for Apple platforms with ExtAudioFile support.
        // watchOS K/N bindings lack ExtAudioFile APIs, so it gets limited impls.
        val appleNonWatchMain by creating {
            dependsOn(appleMain.get())
        }
        iosMain.get().dependsOn(appleNonWatchMain)
        macosMain.get().dependsOn(appleNonWatchMain)
        tvosMain.get().dependsOn(appleNonWatchMain)

        // Shared miniaudio/cinterop actuals for Linux and Windows native.
        val nativeDesktopMain by creating {
            dependsOn(nativeMain.get())
        }
        linuxMain.get().dependsOn(nativeDesktopMain)
        mingwMain.get().dependsOn(nativeDesktopMain)

        getByName("androidDeviceTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
            implementation(libs.androidx.testExt.junit)
            implementation(libs.androidx.espresso.core)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
        }
    }
}

dokka {
    moduleName.set("Klarinet")
}

val dspBuildDir = layout.buildDirectory.dir("dsp-tests")

val dspSources = fileTree(dspSourceDir) {
    exclude("build/**")
}

val configureDspTests by tasks.registering(Exec::class) {
    workingDir = dspSourceDir.asFile
    inputs.files(dspSources)
    outputs.dir(dspBuildDir)
    commandLine(
        "cmake",
        "-S", dspSourceDir.asFile.absolutePath,
        "-B", dspBuildDir.get().asFile.absolutePath,
        "-DKLARINET_DSP_BUILD_TESTS=ON",
    )
}

val compileDspTests by tasks.registering(Exec::class) {
    dependsOn(configureDspTests)
    inputs.files(dspSources)
    outputs.dir(dspBuildDir)
    commandLine("cmake", "--build", dspBuildDir.get().asFile.absolutePath)
}

tasks.register<Exec>("dspTests") {
    group = "verification"
    description = "Build and run the C++ DSP unit tests"
    dependsOn(compileDspTests)
    workingDir = dspBuildDir.get().asFile
    commandLine("ctest", "--output-on-failure")
}

data class JvmNativeTarget(
    val id: String,
    val fileName: String,
    val cmakeSystemName: String?,
    val cmakeSystemProcessor: String?,
    val zigTarget: String?,
    val jniMd: String,
    val extraCmake: List<String> = emptyList(),
)

val jvmNativeTargets = listOf(
    JvmNativeTarget(
        id = "macos-arm64",
        fileName = "libklarinet_jvm.dylib",
        cmakeSystemName = null,
        cmakeSystemProcessor = null,
        zigTarget = null,
        jniMd = "darwin",
        extraCmake = listOf("-DCMAKE_OSX_ARCHITECTURES=arm64"),
    ),
    JvmNativeTarget(
        id = "macos-x64",
        fileName = "libklarinet_jvm.dylib",
        cmakeSystemName = null,
        cmakeSystemProcessor = null,
        zigTarget = null,
        jniMd = "darwin",
        extraCmake = listOf("-DCMAKE_OSX_ARCHITECTURES=x86_64"),
    ),
    JvmNativeTarget(
        id = "linux-x64",
        fileName = "libklarinet_jvm.so",
        cmakeSystemName = "Linux",
        cmakeSystemProcessor = "x86_64",
        zigTarget = "x86_64-linux-gnu",
        jniMd = "linux",
    ),
    JvmNativeTarget(
        id = "linux-arm64",
        fileName = "libklarinet_jvm.so",
        cmakeSystemName = "Linux",
        cmakeSystemProcessor = "aarch64",
        zigTarget = "aarch64-linux-gnu",
        jniMd = "linux",
    ),
    JvmNativeTarget(
        id = "windows-x64",
        fileName = "klarinet_jvm.dll",
        cmakeSystemName = "Windows",
        cmakeSystemProcessor = "x86_64",
        zigTarget = "x86_64-windows-gnu",
        jniMd = "win32",
    ),
)

fun jniIncludeDir(): File {
    val home = file(System.getProperty("java.home"))
    val candidates = listOf(
        home.resolve("include"),
        home.resolve("../include"),
    )
    return candidates.firstOrNull { it.resolve("jni.h").isFile }
        ?: error("jni.h not found under java.home=${home.absolutePath}")
}

fun runProcess(args: List<String>) {
    val result = ProcessBuilder(args).inheritIO().start().waitFor()
    check(result == 0) { "Command failed ($result): ${args.joinToString(" ")}" }
}

fun findZig(): File {
    val fromPath = System.getenv("PATH")
        ?.split(File.pathSeparator)
        ?.firstNotNullOfOrNull { dir -> File(dir, "zig").takeIf { it.canExecute() } }
    return fromPath ?: error("zig is required to cross-compile JVM natives. Install zig and retry.")
}

fun zigToolchainScript(zigTarget: String, tool: String): File {
    val dir = layout.buildDirectory.dir("zig-toolchains").get().asFile
    dir.mkdirs()
    val file = dir.resolve("zig-$zigTarget-$tool.sh")
    val body = when (tool) {
        "cc" -> "#!/bin/sh\nexec zig cc -target $zigTarget \"\$@\"\n"
        "c++" -> "#!/bin/sh\nexec zig c++ -target $zigTarget \"\$@\"\n"
        else -> "#!/bin/sh\nexec zig $tool \"\$@\"\n"
    }
    file.writeText(body)
    file.setExecutable(true)
    return file
}

val jvmNativeCppDir = layout.projectDirectory.dir("src/jvmMain/cpp")
val jvmNativeSources = fileTree(jvmNativeCppDir) {
    exclude("build/**")
}
val packagedNativeDir = layout.projectDirectory.dir("src/jvmMain/resources/natives")

val buildJvmNativeTasks = jvmNativeTargets.map { target ->
    tasks.register("buildJvmNative_${target.id.replace('-', '_')}") {
        group = "build"
        description = "Cross-compile libklarinet_jvm for ${target.id}"
        val buildDir = layout.buildDirectory.dir("jvm-native/${target.id}")
        val destFile = packagedNativeDir.dir(target.id).file(target.fileName)
        inputs.files(jvmNativeSources)
        inputs.files(dspSources)
        outputs.file(destFile)
        doLast {
            val cmakeArgs = mutableListOf(
                "cmake",
                "-S", jvmNativeCppDir.asFile.absolutePath,
                "-B", buildDir.get().asFile.absolutePath,
                "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY",
                "-DKLARINET_JNI_INCLUDE=${jniIncludeDir().absolutePath}",
                "-DKLARINET_JNI_MD_INCLUDE=${jvmNativeCppDir.dir("jni-md/${target.jniMd}").asFile.absolutePath}",
            )
            target.cmakeSystemName?.let { cmakeArgs += "-DCMAKE_SYSTEM_NAME=$it" }
            target.cmakeSystemProcessor?.let { cmakeArgs += "-DCMAKE_SYSTEM_PROCESSOR=$it" }
            target.extraCmake.forEach { cmakeArgs += it }
            val zigTarget = target.zigTarget
            if (zigTarget != null) {
                findZig()
                cmakeArgs += "-DCMAKE_C_COMPILER=${zigToolchainScript(zigTarget, "cc").absolutePath}"
                cmakeArgs += "-DCMAKE_CXX_COMPILER=${zigToolchainScript(zigTarget, "c++").absolutePath}"
                cmakeArgs += "-DCMAKE_AR=${zigToolchainScript(zigTarget, "ar").absolutePath}"
                cmakeArgs += "-DCMAKE_RANLIB=${zigToolchainScript(zigTarget, "ranlib").absolutePath}"
                cmakeArgs += "-DCMAKE_SHARED_LINKER_FLAGS=-s"
            }
            runProcess(cmakeArgs)
            runProcess(
                listOf(
                    "cmake",
                    "--build",
                    buildDir.get().asFile.absolutePath,
                    "--config",
                    "Release",
                    "-j",
                ),
            )
            val builtDir = buildDir.get().asFile
            val built = listOf(
                builtDir.resolve(target.fileName),
                builtDir.resolve("lib${target.fileName}"),
                builtDir.resolve("Release/${target.fileName}"),
                builtDir.resolve("Release/lib${target.fileName}"),
            ).firstOrNull { it.isFile }
                ?: error("Expected ${target.fileName} under ${builtDir.absolutePath} after compiling ${target.id}")
            destFile.asFile.parentFile.mkdirs()
            built.copyTo(destFile.asFile, overwrite = true)
        }
    }
}

tasks.register("buildJvmNatives") {
    group = "build"
    description = "Build packaged JVM natives for macOS, Linux, and Windows"
    dependsOn(buildJvmNativeTasks)
}

tasks.matching { it.name == "jvmProcessResources" || it.name == "processJvmMainResources" }.configureEach {
    mustRunAfter(buildJvmNativeTasks)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        name.set("Klarinet")
        description.set("Low-latency audio I/O SDK for Kotlin Multiplatform")
        url.set("https://github.com/vectencia/Klarinet")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        scm {
            url.set("https://github.com/vectencia/Klarinet")
            connection.set("scm:git:https://github.com/vectencia/Klarinet.git")
            developerConnection.set("scm:git:ssh://git@github.com/vectencia/Klarinet.git")
        }
    }
}

import java.io.File
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

private val hostIsMac: Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("mac")

fun dspCmakeArguments(targetName: String): List<String> = when (targetName) {
    "macosArm64" -> listOf(
        "-DCMAKE_OSX_ARCHITECTURES=arm64",
        "-DKLARINET_DSP_RUST_QUEUES=ON",
        "-DKLARINET_DSP_RUST_EFFECTS=ON",
    )
    "iosSimulatorArm64" -> listOf(
        "-DCMAKE_SYSTEM_NAME=iOS",
        "-DCMAKE_OSX_SYSROOT=iphonesimulator",
        "-DCMAKE_OSX_ARCHITECTURES=arm64",
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0",
        "-DKLARINET_DSP_RUST_QUEUES=ON",
        "-DKLARINET_DSP_RUST_EFFECTS=ON",
        "-DKLARINET_RUST_CARGO_TARGET=aarch64-apple-ios-sim",
    )
    "iosArm64" -> listOf(
        "-DCMAKE_SYSTEM_NAME=iOS",
        "-DCMAKE_OSX_SYSROOT=iphoneos",
        "-DCMAKE_OSX_ARCHITECTURES=arm64",
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0",
        "-DKLARINET_DSP_RUST_QUEUES=ON",
        "-DKLARINET_DSP_RUST_EFFECTS=ON",
        "-DKLARINET_RUST_CARGO_TARGET=aarch64-apple-ios",
    )
    "iosX64" -> listOf(
        "-DCMAKE_SYSTEM_NAME=iOS",
        "-DCMAKE_OSX_SYSROOT=iphonesimulator",
        "-DCMAKE_OSX_ARCHITECTURES=x86_64",
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0",
        "-DKLARINET_DSP_RUST_QUEUES=ON",
        "-DKLARINET_DSP_RUST_EFFECTS=ON",
        "-DKLARINET_RUST_CARGO_TARGET=x86_64-apple-ios",
    )
    "tvosSimulatorArm64" -> listOf(
        "-DCMAKE_SYSTEM_NAME=tvOS",
        "-DCMAKE_OSX_SYSROOT=appletvsimulator",
        "-DCMAKE_OSX_ARCHITECTURES=arm64",
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0",
        "-DKLARINET_DSP_RUST_QUEUES=ON",
        "-DKLARINET_DSP_RUST_EFFECTS=ON",
        "-DKLARINET_RUST_CARGO_TARGET=aarch64-apple-tvos-sim",
    )
    "tvosArm64" -> listOf(
        "-DCMAKE_SYSTEM_NAME=tvOS",
        "-DCMAKE_OSX_SYSROOT=appletvos",
        "-DCMAKE_OSX_ARCHITECTURES=arm64",
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0",
        "-DKLARINET_DSP_RUST_QUEUES=ON",
        "-DKLARINET_DSP_RUST_EFFECTS=ON",
        "-DKLARINET_RUST_CARGO_TARGET=aarch64-apple-tvos",
    )
    "watchosSimulatorArm64" -> listOf(
        "-DCMAKE_SYSTEM_NAME=watchOS",
        "-DCMAKE_OSX_SYSROOT=watchsimulator",
        "-DCMAKE_OSX_ARCHITECTURES=arm64",
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=8.0",
        "-DKLARINET_DSP_RUST_QUEUES=ON",
        "-DKLARINET_DSP_RUST_EFFECTS=ON",
        "-DKLARINET_RUST_CARGO_TARGET=aarch64-apple-watchos-sim",
    )
    "watchosArm64" -> listOf(
        "-DCMAKE_SYSTEM_NAME=watchOS",
        "-DCMAKE_OSX_SYSROOT=watchos",
        "-DCMAKE_OSX_ARCHITECTURES=arm64",
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=8.0",
        "-DKLARINET_DSP_RUST_QUEUES=ON",
        "-DKLARINET_DSP_RUST_EFFECTS=ON",
        "-DKLARINET_RUST_CARGO_TARGET=aarch64-apple-watchos",
    )
    else -> emptyList()
}.let { args ->
    if (args.isEmpty()) args else args + "-DKLARINET_DSP_RUST_ANALYSIS=ON"
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
        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
        }
    }

    jvm()

    js {
        browser {
            testTask {
                useKarma {
                    useChromeHeadlessNoSandbox()
                    useConfigDirectory(layout.projectDirectory.dir("src/jsTest/karma").asFile)
                }
            }
        }
    }

    val nativeDesktopTargets = listOf(
        linuxX64(),
        linuxArm64(),
        mingwX64(),
    )

    val linuxNativeArchiveDir = layout.buildDirectory.dir("native-linuxX64")
    nativeDesktopTargets.forEach { target ->
        target.compilations.getByName("main") {
            cinterops {
                create("klarinet_native") {
                    defFile(project.file("src/nativeInterop/cinterop/klarinet_native.def"))
                    includeDirs(project.file("src/nativeInterop/cinterop"))
                    // Phase 5 ships linuxX64 first. linuxArm64 and mingwX64 stay on
                    // klarinet_native.c until this archive has been linked.
                    if (target.name == "linuxX64") {
                        extraOpts(
                            "-libraryPath", linuxNativeArchiveDir.get().asFile.absolutePath,
                            "-staticLibrary", "libklarinet_native.a",
                        )
                    }
                }
                create("klarinet_dsp") {
                    defFile(project.file("src/nativeInterop/cinterop/klarinet_dsp.def"))
                    includeDirs(project.file("src/cpp/dsp"))
                }
            }
        }
    }

    // Headers only. The symbols live in libklarinet_native.a, which the
    // linuxX64 klarinet_native cinterop already passes as -staticLibrary.
    nativeDesktopTargets.single { it.name == "linuxX64" }.compilations.getByName("test") {
        cinterops {
            create("klarinet_audio_test") {
                defFile(project.file("src/linuxX64Test/cinterop/klarinet_audio_test.def"))
                includeDirs(project.file("src/linuxX64Test/cinterop"))
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
        val capitalized = target.name.replaceFirstChar { it.uppercase() }
        val dspOut = layout.buildDirectory.dir("dsp-${target.name}")
        val dspLib = dspOut.map { it.file("libklarinet-dsp.a") }
        val configureDsp = tasks.register<Exec>("configureDsp$capitalized") {
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
        val compileDsp = tasks.register<Exec>("compileDsp$capitalized") {
            dependsOn(configureDsp)
            inputs.dir(dspSourceDir)
            outputs.file(dspLib)
            commandLine("cmake", "--build", dspOut.get().asFile.absolutePath)
        }

        target.binaries.framework {
            baseName = "Klarinet"
            isStatic = true
        }
        target.compilations.getByName("main") {
            cinterops {
                create("klarinet_dsp") {
                    defFile(project.file("src/nativeInterop/cinterop/klarinet_dsp.def"))
                    includeDirs(project.file("src/cpp/dsp"))
                    // Pack libklarinet-dsp.a on macOS only. Linux/CI Ubuntu (Dokka,
                    // JS demo) still run Apple cinterop for metadata and cannot
                    // configure an iOS SDK.
                    if (hostIsMac) {
                        extraOpts(
                            "-libraryPath", dspOut.get().asFile.absolutePath,
                            "-staticLibrary", "libklarinet-dsp.a",
                        )
                    }
                }
            }
        }
        target.binaries.all {
            // macosArm64 still contains the C++ shims, so -lc++ stays.
            linkerOpts("-lc++")
        }
        val rustArchiveRelative = when (target.name) {
            "macosArm64" -> "rust-target/release/libklarinet_dsp_core.a"
            "iosSimulatorArm64" -> "rust-target/aarch64-apple-ios-sim/release/libklarinet_dsp_core.a"
            "iosArm64" -> "rust-target/aarch64-apple-ios/release/libklarinet_dsp_core.a"
            "iosX64" -> "rust-target/x86_64-apple-ios/release/libklarinet_dsp_core.a"
            "tvosSimulatorArm64" -> "rust-target/aarch64-apple-tvos-sim/release/libklarinet_dsp_core.a"
            "tvosArm64" -> "rust-target/aarch64-apple-tvos/release/libklarinet_dsp_core.a"
            "watchosSimulatorArm64" -> "rust-target/aarch64-apple-watchos-sim/release/libklarinet_dsp_core.a"
            "watchosArm64" -> "rust-target/aarch64-apple-watchos/release/libklarinet_dsp_core.a"
            else -> null
        }
        if (rustArchiveRelative != null) {
            val rustDspArchive = dspOut.map { it.file(rustArchiveRelative) }
            compileDsp.configure {
                inputs.files(
                    fileTree(layout.projectDirectory.dir("rust/klarinet-dsp")) { exclude("target/**") },
                )
                val dspArchivePath = dspLib.get().asFile.absolutePath
                val rustArchivePath = rustDspArchive.get().asFile.absolutePath
                // CMake's static-archive rule does not depend on the Rust
                // staticlib. Delete the published archive before cmake --build
                // so make recreates it from the C++ objects, then merge that
                // fresh archive. Merging the published file in place would
                // append a second copy of the Rust objects on the next run.
                doFirst {
                    File(dspArchivePath).delete()
                }
                doLast {
                    val merged = File("$dspArchivePath.merged")
                    val result = ProcessBuilder(
                        "libtool",
                        "-static",
                        "-o",
                        merged.absolutePath,
                        dspArchivePath,
                        rustArchivePath,
                    ).inheritIO().start().waitFor()
                    check(result == 0) { "libtool merge failed ($result)" }
                    check(File(dspArchivePath).delete()) { "could not replace $dspArchivePath" }
                    check(merged.renameTo(File(dspArchivePath))) { "could not move merged archive" }
                }
            }
        }
        if (hostIsMac) {
            tasks.matching { task ->
                val n = task.name.lowercase()
                val t = target.name.lowercase()
                n.contains(t) && (
                    n.contains("cinteropklarinet_dsp") ||
                        n.contains("link")
                    )
            }.configureEach {
                dependsOn(compileDsp)
                if (rustArchiveRelative != null) {
                    // The staticlib is copied into the klib. dependsOn does not
                    // make that copy an input.
                    inputs.file(dspLib)
                }
            }
        }
    }

    applyDefaultHierarchyTemplate()

    // Expect/actual classes are still opt-in (KT-61573).
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {}
        getByName("jsMain").kotlin.srcDir(layout.buildDirectory.dir("generated/jsWasm"))
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        androidMain.dependencies {
            api(project(":klarinet-android"))
        }

        // Intermediate source set for Apple platforms with ExtAudioFile support.
        // watchOS K/N bindings lack ExtAudioFile APIs, so it gets limited impls.
        val appleNonWatchMain = create("appleNonWatchMain") {
            dependsOn(getByName("appleMain"))
        }
        getByName("iosMain").dependsOn(appleNonWatchMain)
        getByName("macosMain").dependsOn(appleNonWatchMain)
        getByName("tvosMain").dependsOn(appleNonWatchMain)

        // Shared miniaudio/cinterop actuals for Linux and Windows native.
        val nativeDesktopMain = create("nativeDesktopMain") {
            dependsOn(getByName("nativeMain"))
        }
        getByName("linuxMain").dependsOn(nativeDesktopMain)
        getByName("mingwMain").dependsOn(nativeDesktopMain)

        // JVM, Android, JS, and Apple call the Rust analysis ABI. Native
        // desktop keeps this Kotlin body.
        val kotlinAnalysisMain = create("kotlinAnalysisMain") {
            dependsOn(getByName("commonMain"))
        }
        nativeDesktopMain.dependsOn(kotlinAnalysisMain)

        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
        }

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

// The host JVM cannot load libklarinet-jni.so. AndroidAnalysisPathTest
// covers these types on a device. The common tests run on JVM, JS, and Apple.
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    filter {
        excludeTestsMatching("com.vectencia.klarinet.FftTest")
        excludeTestsMatching("com.vectencia.klarinet.AudioAnalyzerTest")
        excludeTestsMatching("com.vectencia.klarinet.AudioMathTest")
        excludeTestsMatching("com.vectencia.klarinet.LinearResamplerTest")
    }
}

dokka {
    moduleName.set("Klarinet")
}

val appleDspTargets = listOf(
    "iosArm64",
    "iosSimulatorArm64",
    "iosX64",
    "macosArm64",
    "tvosArm64",
    "tvosSimulatorArm64",
    "watchosArm64",
    "watchosSimulatorArm64",
)

tasks.register("verifyDspEmbeddedInAppleKlibs") {
    group = "verification"
    description = "Assert Apple cinterop klibs embed libklarinet-dsp.a with DSP symbols"
    val klibDirs = appleDspTargets.associateWith { targetName ->
        layout.buildDirectory.dir(
            "classes/kotlin/$targetName/main/cinterop/klarinet-cinterop-klarinet_dsp",
        )
    }
    val symbols = listOf(
        "_klarinet_chain_create",
        "_klarinet_create_effect",
        "_klarinet_offload_create",
    )
    appleDspTargets.forEach { targetName ->
        dependsOn("cinteropKlarinet_dsp${targetName.replaceFirstChar { it.uppercase() }}")
    }
    doLast {
        klibDirs.forEach { (targetName, dirProvider) ->
            val klibDir = dirProvider.get().asFile
            check(klibDir.isDirectory) { "Missing cinterop klib for $targetName at $klibDir" }
            val archives = klibDir.walkTopDown()
                .filter { it.isFile && it.extension == "a" }
                .toList()
            check(archives.isNotEmpty()) {
                "cinterop klib for $targetName does not embed libklarinet-dsp.a. Contents:\n" +
                    klibDir.walkTopDown().joinToString("\n")
            }
            val archive = archives.first()
            val nm = ProcessBuilder("nm", archive.absolutePath)
                .redirectErrorStream(true)
                .start()
            val out = nm.inputStream.bufferedReader().readText()
            check(nm.waitFor() == 0) { "nm failed on $archive\n$out" }
            symbols.forEach { symbol ->
                val defined = out.lineSequence().any { line ->
                    line.contains(symbol) && (
                        line.contains(" T ") ||
                            line.startsWith("T ") ||
                            line.contains("\tT ")
                        )
                }
                check(defined) {
                    "Embedded DSP for $targetName is missing defined symbol $symbol in $archive"
                }
            }
        }
    }
}

tasks.matching { it.name == "check" }.configureEach {
    if (System.getProperty("os.name").orEmpty().contains("Mac", ignoreCase = true)) {
        dependsOn("verifyDspEmbeddedInAppleKlibs")
    }
}

val dspBuildDir = layout.buildDirectory.dir("dsp-tests")

val dspSources = fileTree(dspSourceDir) {
    exclude("build/**")
}

val rustDspSources = fileTree(layout.projectDirectory.dir("rust/klarinet-dsp")) {
    exclude("target/**")
}
val rustJvmSources = fileTree(layout.projectDirectory.dir("rust/klarinet-jvm")) {
    exclude("target/**")
}
val rustWavSources = fileTree(layout.projectDirectory.dir("rust/klarinet-wav")) {
    exclude("target/**")
}

val generateKlarinetWasm = tasks.register("generateKlarinetWasm") {
    group = "build"
    description = "Compile the effect and analysis crate to wasm."
    inputs.files(fileTree(layout.projectDirectory.dir("rust/klarinet-wasm")) { exclude("target/**") })
    inputs.files(rustDspSources)
    inputs.files(rustWavSources)
    val manifest = layout.projectDirectory.file("rust/klarinet-wasm/Cargo.toml").asFile.absolutePath
    val outPath = layout.buildDirectory.file("generated/jsWasm/KlarinetWasm.kt").get().asFile.absolutePath
    outputs.file(outPath)
    doLast {
        val cargo = listOf(
            File(System.getProperty("user.home"), ".cargo/bin/cargo"),
            File("/opt/homebrew/bin/cargo"),
        ).firstOrNull { it.canExecute() } ?: error("cargo was not found")
        val result = ProcessBuilder(
            cargo.absolutePath,
            "build",
            "--release",
            "--locked",
            "--manifest-path",
            manifest,
            "--target",
            "wasm32-unknown-unknown",
        ).inheritIO().start().waitFor()
        check(result == 0) { "wasm cargo build failed ($result)" }
        val wasm = File(manifest).parentFile.resolve(
            "target/wasm32-unknown-unknown/release/klarinet_wasm.wasm",
        )
        check(wasm.isFile) { "missing ${wasm.absolutePath}" }
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val data = wasm.readBytes()
        val encoded = buildString {
            var i = 0
            while (i < data.size) {
                val b0 = data[i].toInt() and 0xff
                val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xff else 0
                val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xff else 0
                append(alphabet[b0 shr 2])
                append(alphabet[((b0 and 3) shl 4) or (b1 shr 4)])
                if (i + 1 < data.size) append(alphabet[((b1 and 15) shl 2) or (b2 shr 6)]) else append('=')
                if (i + 2 < data.size) append(alphabet[b2 and 63]) else append('=')
                i += 3
            }
        }
        val out = File(outPath)
        out.parentFile.mkdirs()
        out.writeText(
            "package com.vectencia.klarinet\n\ninternal const val KLARINET_WASM_BASE64: String =\n    \"$encoded\"\n",
        )
    }
}

tasks.matching { it.name == "compileKotlinJs" }.configureEach {
    dependsOn(generateKlarinetWasm)
}

val configureDspTests = tasks.register<Exec>("configureDspTests") {
    workingDir = dspSourceDir.asFile
    inputs.files(dspSources, rustDspSources)
    outputs.dir(dspBuildDir)
    commandLine(
        "cmake",
        "-S", dspSourceDir.asFile.absolutePath,
        "-B", dspBuildDir.get().asFile.absolutePath,
        "-DKLARINET_DSP_BUILD_TESTS=ON",
        "-DKLARINET_DSP_RUST_QUEUES=ON",
        "-DKLARINET_DSP_RUST_EFFECTS=ON",
    )
}

val compileDspTests = tasks.register<Exec>("compileDspTests") {
    dependsOn(configureDspTests)
    inputs.files(dspSources, rustDspSources)
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

val linuxNativeOut = layout.buildDirectory.dir("native-linuxX64")
val buildKlarinetNativeLinuxX64 = tasks.register("buildKlarinetNativeLinuxX64") {
    group = "build"
    description = "Rust klarinet_native staticlib for linuxX64, with vendored miniaudio wrapped in C."
    val rustDir = layout.projectDirectory.dir("rust/klarinet-native").asFile
    val includeDir = layout.projectDirectory.dir("src/nativeInterop/cinterop").asFile
    val outDir = linuxNativeOut.get().asFile
    inputs.files(fileTree(rustDir) { exclude("target/**") })
    inputs.files(
        fileTree(layout.projectDirectory.dir("rust/klarinet-dsp")) { exclude("target/**") },
    )
    inputs.file(includeDir.resolve("miniaudio.h"))
    outputs.file(outDir.resolve("libklarinet_native.a"))
    // Paths only. The action must not call a script function, or the
    // configuration cache cannot store this task.
    val manifest = rustDir.resolve("Cargo.toml").absolutePath
    val miniaudioC = rustDir.resolve("c/miniaudio.c").absolutePath
    val wrapperC = rustDir.resolve("c/klarinet_ma.c").absolutePath
    val wrapperInclude = rustDir.resolve("c").absolutePath
    val miniaudioInclude = includeDir.absolutePath
    val outPath = outDir.absolutePath
    doLast {
        fun run(args: List<String>) {
            val result = ProcessBuilder(args).inheritIO().start().waitFor()
            check(result == 0) { "Command failed ($result): ${args.joinToString(" ")}" }
        }
        val out = File(outPath)
        out.mkdirs()
        val cargo = File(System.getProperty("user.home"), ".cargo/bin/cargo")
        check(cargo.canExecute()) { "cargo was not found at ${cargo.absolutePath}" }
        val targetDir = File(out, "target")
        run(
            listOf(
                cargo.absolutePath,
                "build",
                "--release",
                "--locked",
                "--manifest-path",
                manifest,
                "--target",
                "x86_64-unknown-linux-gnu",
                "--target-dir",
                targetDir.absolutePath,
            ),
        )
        val zig = System.getenv("PATH")
            ?.split(File.pathSeparator)
            ?.firstNotNullOfOrNull { dir -> File(dir, "zig").takeIf { it.canExecute() } }
            ?: error("zig is required to compile the linuxX64 miniaudio wrapper")
        val maObj = File(out, "miniaudio.o")
        val wrapObj = File(out, "klarinet_ma.o")
        run(
            listOf(
                zig.absolutePath,
                "cc",
                "-target",
                "x86_64-linux-gnu",
                "-c",
                "-O2",
                "-I",
                miniaudioInclude,
                miniaudioC,
                "-o",
                maObj.absolutePath,
            ),
        )
        run(
            listOf(
                zig.absolutePath,
                "cc",
                "-target",
                "x86_64-linux-gnu",
                "-c",
                "-O2",
                "-I",
                wrapperInclude,
                "-I",
                miniaudioInclude,
                wrapperC,
                "-o",
                wrapObj.absolutePath,
            ),
        )
        val archive = File(out, "libklarinet_native.a")
        val rustLib = File(targetDir, "x86_64-unknown-linux-gnu/release/libklarinet_native.a")
        if (archive.exists()) {
            check(archive.delete()) { "could not replace ${archive.absolutePath}" }
        }
        run(listOf("cp", rustLib.absolutePath, archive.absolutePath))
        run(
            listOf(
                zig.absolutePath,
                "ar",
                "r",
                archive.absolutePath,
                maObj.absolutePath,
                wrapObj.absolutePath,
            ),
        )
    }
}

val linuxNativeArchive = linuxNativeOut.map { it.file("libklarinet_native.a") }
tasks.matching { task ->
    val name = task.name
    name == "cinteropKlarinet_nativeLinuxX64" ||
        (name.startsWith("link") && name.contains("LinuxX64"))
}.configureEach {
    dependsOn(buildKlarinetNativeLinuxX64)
    // `-staticLibrary` is copied into the klib. dependsOn does not make that
    // copy an input, so a later Rust edit would leave cinterop UP-TO-DATE.
    inputs.file(linuxNativeArchive)
}

data class JvmNativeTarget(
    val id: String,
    val fileName: String,
    val cmakeSystemName: String?,
    val cmakeSystemProcessor: String?,
    val zigTarget: String?,
    val jniMd: String,
    val rustTriple: String? = null,
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
        rustTriple = "x86_64-apple-darwin",
        extraCmake = listOf("-DCMAKE_OSX_ARCHITECTURES=x86_64"),
    ),
    JvmNativeTarget(
        id = "linux-x64",
        fileName = "libklarinet_jvm.so",
        cmakeSystemName = "Linux",
        cmakeSystemProcessor = "x86_64",
        zigTarget = "x86_64-linux-gnu",
        jniMd = "linux",
        rustTriple = "x86_64-unknown-linux-gnu",
    ),
    JvmNativeTarget(
        id = "linux-arm64",
        fileName = "libklarinet_jvm.so",
        cmakeSystemName = "Linux",
        cmakeSystemProcessor = "aarch64",
        zigTarget = "aarch64-linux-gnu",
        jniMd = "linux",
        rustTriple = "aarch64-unknown-linux-gnu",
    ),
    JvmNativeTarget(
        id = "windows-x64",
        fileName = "klarinet_jvm.dll",
        cmakeSystemName = "Windows",
        cmakeSystemProcessor = "x86_64",
        zigTarget = "x86_64-windows-gnu",
        jniMd = "win32",
        rustTriple = "x86_64-pc-windows-gnu",
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
    // Copy into serializable values. The task action must not capture this script
    // or JvmNativeTarget, or the configuration cache cannot store the task.
    val targetId = target.id
    val fileName = target.fileName
    val jniMd = target.jniMd
    val cmakeSystemName = target.cmakeSystemName
    val cmakeSystemProcessor = target.cmakeSystemProcessor
    val extraCmake = target.extraCmake
    val zigTarget = target.zigTarget
    val enableRustEffects = targetId == "macos-arm64"
    val enableRustJvm = targetId == "macos-arm64" ||
        targetId == "macos-x64" ||
        targetId == "linux-x64" ||
        targetId == "linux-arm64" ||
        targetId == "windows-x64"
    val rustTriple = target.rustTriple
    val cmakeSource = jvmNativeCppDir.asFile.absolutePath
    val jniMdInclude = jvmNativeCppDir.dir("jni-md/$jniMd").asFile.absolutePath
    val zigToolDir = layout.buildDirectory.dir("zig-toolchains")
    tasks.register("buildJvmNative_${targetId.replace('-', '_')}") {
        group = "build"
        description = "Cross-compile libklarinet_jvm for $targetId"
        val buildDir = layout.buildDirectory.dir("jvm-native/$targetId")
        val destFile = packagedNativeDir.dir(targetId).file(fileName)
        inputs.files(jvmNativeSources)
        inputs.files(dspSources)
        if (enableRustJvm || enableRustEffects) {
            inputs.files(rustDspSources)
        }
        if (enableRustJvm) {
            inputs.files(rustJvmSources)
            inputs.files(rustWavSources)
        }
        outputs.file(destFile)
        doLast {
            val javaHome = File(System.getProperty("java.home"))
            val jniInclude = listOf(
                javaHome.resolve("include"),
                javaHome.resolve("../include"),
            ).firstOrNull { it.resolve("jni.h").isFile }?.absolutePath
                ?: error("jni.h not found under java.home=${javaHome.absolutePath}")
            val cmakeArgs = mutableListOf(
                "cmake",
                "-S", cmakeSource,
                "-B", buildDir.get().asFile.absolutePath,
                "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY",
                "-DKLARINET_JNI_INCLUDE=$jniInclude",
                "-DKLARINET_JNI_MD_INCLUDE=$jniMdInclude",
            )
            if (cmakeSystemName != null) {
                cmakeArgs += "-DCMAKE_SYSTEM_NAME=$cmakeSystemName"
            }
            if (cmakeSystemProcessor != null) {
                cmakeArgs += "-DCMAKE_SYSTEM_PROCESSOR=$cmakeSystemProcessor"
            }
            extraCmake.forEach { cmakeArgs += it }
            if (enableRustEffects) {
                cmakeArgs += "-DKLARINET_DSP_RUST_EFFECTS=ON"
            }
            if (enableRustJvm) {
                cmakeArgs += "-DKLARINET_JVM_RUST=ON"
                if (rustTriple != null) {
                    cmakeArgs += "-DKLARINET_JVM_RUST_TARGET=$rustTriple"
                }
                if (enableRustEffects) {
                    cmakeArgs += "-DKLARINET_JVM_RUST_EFFECTS=ON"
                }
            }
            if (zigTarget != null) {
                val zig = System.getenv("PATH")
                    ?.split(File.pathSeparator)
                    ?.firstNotNullOfOrNull { dir -> File(dir, "zig").takeIf { it.canExecute() } }
                    ?: error("zig is required to cross-compile JVM natives. Install zig and retry.")
                check(zig.canExecute())
                fun toolScript(tool: String): File {
                    val dir = zigToolDir.get().asFile
                    dir.mkdirs()
                    val script = dir.resolve("zig-$zigTarget-$tool.sh")
                    val body = when (tool) {
                        "cc" -> "#!/bin/sh\nexec zig cc -target $zigTarget \"\$@\"\n"
                        "c++" -> "#!/bin/sh\nexec zig c++ -target $zigTarget \"\$@\"\n"
                        else -> "#!/bin/sh\nexec zig $tool \"\$@\"\n"
                    }
                    script.writeText(body)
                    script.setExecutable(true)
                    return script
                }
                cmakeArgs += "-DCMAKE_C_COMPILER=${toolScript("cc").absolutePath}"
                cmakeArgs += "-DCMAKE_CXX_COMPILER=${toolScript("c++").absolutePath}"
                cmakeArgs += "-DCMAKE_AR=${toolScript("ar").absolutePath}"
                cmakeArgs += "-DCMAKE_RANLIB=${toolScript("ranlib").absolutePath}"
                cmakeArgs += "-DCMAKE_SHARED_LINKER_FLAGS=-s"
                if (enableRustJvm) {
                    cmakeArgs += "-DKLARINET_JVM_RUST_LINKER=${cmakeSource}/zig_rust_link.py"
                    cmakeArgs += "-DKLARINET_JVM_RUST_ZIG_TARGET=$zigTarget"
                }
            }
            fun run(args: List<String>) {
                val result = ProcessBuilder(args).inheritIO().start().waitFor()
                check(result == 0) { "Command failed ($result): ${args.joinToString(" ")}" }
            }
            run(cmakeArgs)
            run(
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
                builtDir.resolve(fileName),
                builtDir.resolve("lib$fileName"),
                builtDir.resolve("Release/$fileName"),
                builtDir.resolve("Release/lib$fileName"),
            ).firstOrNull { it.isFile }
                ?: error("Expected $fileName under ${builtDir.absolutePath} after compiling $targetId")
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
        scm {
            url.set("https://github.com/vectencia/Klarinet")
            connection.set("scm:git:https://github.com/vectencia/Klarinet.git")
            developerConnection.set("scm:git:ssh://git@github.com/vectencia/Klarinet.git")
        }
    }
}

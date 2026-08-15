import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun dspCmakeArguments(targetName: String): List<String> = when (targetName) {
    "macosArm64" -> listOf("-DCMAKE_OSX_ARCHITECTURES=arm64")
    "macosX64" -> listOf("-DCMAKE_OSX_ARCHITECTURES=x86_64")
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
        macosX64(),
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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktechPublish)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        publishLibraryVariants("release")
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
    }

    applyDefaultHierarchyTemplate()

    // Suppress beta warning for expect/actual classes (KT-61573).
    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {}
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        // Intermediate source set for Apple platforms with ExtAudioFile support.
        // watchOS K/N bindings lack ExtAudioFile APIs, so it gets limited impls.
        val appleNonWatchMain by creating {
            dependsOn(appleMain.get())
        }
        iosMain.get().dependsOn(appleNonWatchMain)
        macosMain.get().dependsOn(appleNonWatchMain)
        tvosMain.get().dependsOn(appleNonWatchMain)

        val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.testJunit)
                implementation(libs.androidx.testExt.junit)
                implementation(libs.androidx.espresso.core)
            }
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
        }
    }
}

android {
    namespace = "com.vectencia.klarinet"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                )
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
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

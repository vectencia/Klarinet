import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.vanniktechPublish)
    alias(libs.plugins.dokka)
}

kotlin {
    android {
        namespace = "com.vectencia.klarinet.coroutines"
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
                useKarma {
                    useChromeHeadlessNoSandbox()
                }
            }
        }
    }

    linuxX64()
    linuxArm64()
    mingwX64()

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
        target.binaries.all {
            linkerOpts("-lc++")
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(project(":klarinet"))
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
            implementation(libs.androidx.testExt.junit)
            implementation(libs.androidx.espresso.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// The host JVM cannot load libklarinet-jni.so. AnalyzingCallbackTest is the JVM check.
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    filter {
        excludeTestsMatching("com.vectencia.klarinet.coroutines.AnalyzingCallbackTest")
    }
}

dokka {
    moduleName.set("Klarinet Coroutines")
    dokkaSourceSets.configureEach {
        includes.from(layout.projectDirectory.file("dokka/module.md"))
    }
    dokkaPublications.html {
        includes.from(layout.projectDirectory.file("dokka/module.md"))
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        name.set("Klarinet Coroutines")
        description.set("Kotlin Coroutines extensions for Klarinet audio SDK")
    }
}

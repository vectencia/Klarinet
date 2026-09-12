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
    }

    jvm()

    js {
        browser {
            testTask {
                enabled = false
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
    )

    appleTargets.forEach { target ->
        val dspOut = project(":klarinet").layout.buildDirectory.dir("dsp-${target.name}")
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
            dependsOn(":klarinet:compileDsp${target.name.replaceFirstChar { it.uppercase() }}")
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
    }
}

dokka {
    moduleName.set("Klarinet Coroutines")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        name.set("Klarinet Coroutines")
        description.set("Kotlin Coroutines extensions for Klarinet audio SDK")
    }
}

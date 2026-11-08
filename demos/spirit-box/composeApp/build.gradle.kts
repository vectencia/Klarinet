import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.koin.compiler)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    jvm()
    android {
        namespace = "com.spiritbox.composeapp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
        androidResources {
            enable = true
        }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.android)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.klarinet)
            implementation(libs.klarinet.coroutines)
            implementation(libs.components.resources)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)
            implementation(libs.koin.annotations)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
        }
        iosMain.dependencies {
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            // collectAsStateWithLifecycle needs Dispatchers.Main on the desktop
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.spiritbox.MainKt"
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

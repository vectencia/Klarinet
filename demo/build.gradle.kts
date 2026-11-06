import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val useDebugDependencies = (findProperty("useDebugDependencies") as? String).toBoolean()

kotlin {
    android {
        namespace = "com.vectencia.klarinet.demo.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        androidResources {
            enable = true
        }
    }

    jvm("desktop")

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            if (useDebugDependencies) {
                export(project(":klarinet"))
            } else {
                export(libs.klarinet)
            }
        }
    }

    sourceSets {
        named("desktopMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            if (useDebugDependencies) {
                api(project(":klarinet"))
                implementation(project(":klarinet-coroutines"))
            } else {
                api(libs.klarinet)
                implementation(libs.klarinet.coroutines)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.vectencia.klarinet.MainKt"
    }
}

dependencies {
    "androidRuntimeClasspath"(libs.compose.uiTooling)
}

// Compose has no tvOS or watchOS artifacts, so those targets cannot share
// commonMain. The demo sine file is still compiled into ComposeApp, which
// exports Klarinet, and the view models typecheck against that framework.
val callbackKonanc: File = run {
    val arch = System.getProperty("os.arch").let { if (it == "arm64") "aarch64" else it }
    File(
        System.getProperty("user.home"),
        ".konan/kotlin-native-prebuilt-macos-$arch-${libs.versions.kotlin.get()}/bin/konanc",
    )
}
val klarinetProject = project(":klarinet")
val sineFill = layout.projectDirectory.file("src/iosMain/kotlin/com/vectencia/klarinet/SineFill.kt")

data class CallbackFramework(
    val kotlinTarget: String,
    val konanTarget: String,
    val sdk: String,
    val swiftTarget: String,
    val swiftDir: String,
)

listOf(
    CallbackFramework(
        "tvosSimulatorArm64",
        "tvos_simulator_arm64",
        "appletvsimulator",
        "arm64-apple-tvos15.0-simulator",
        "iosApp/tvosApp",
    ),
    CallbackFramework(
        "watchosSimulatorArm64",
        "watchos_simulator_arm64",
        "watchsimulator",
        "arm64-apple-watchos8.0-simulator",
        "iosApp/watchosApp",
    ),
).forEach { spec ->
    val capital = spec.kotlinTarget.replaceFirstChar { it.uppercase() }
    val outDir = layout.buildDirectory.dir("callback-framework/${spec.kotlinTarget}")
    val cinteropDir = klarinetProject.layout.buildDirectory.dir(
        "classes/kotlin/${spec.kotlinTarget}/main/cinterop/klarinet-cinterop-klarinet_dsp",
    )
    val mainKlib = klarinetProject.layout.buildDirectory.dir(
        "classes/kotlin/${spec.kotlinTarget}/main/klib/klarinet",
    )
    val dspLib = klarinetProject.layout.buildDirectory.file(
        "dsp-${spec.kotlinTarget}/libklarinet-dsp.a",
    )
    val link = tasks.register<Exec>("linkComposeApp$capital") {
        dependsOn(":klarinet:compileKotlin$capital")
        inputs.file(sineFill)
        inputs.dir(cinteropDir)
        inputs.dir(mainKlib)
        inputs.file(dspLib)
        outputs.dir(outDir)
        val outPath = outDir.get().asFile.absolutePath
        val dspDir = dspLib.get().asFile.parentFile.absolutePath
        doFirst { File(outPath).deleteRecursively() }
        commandLine(
            callbackKonanc.absolutePath,
            sineFill.asFile.absolutePath,
            "-target", spec.konanTarget,
            "-produce", "framework",
            "-module-name", "ComposeApp",
            "-output", "$outPath/ComposeApp",
            "-library", cinteropDir.get().asFile.absolutePath,
            "-library", mainKlib.get().asFile.absolutePath,
            "-Xexport-library=${mainKlib.get().asFile.absolutePath}",
            "-linker-options", "-L$dspDir -lklarinet-dsp",
        )
    }
    tasks.register<Exec>("compile${capital}ViewModels") {
        dependsOn(link)
        val sources = listOf(
            rootProject.file("${spec.swiftDir}/ToneGeneratorViewModel.swift"),
            rootProject.file("${spec.swiftDir}/ToneGeneratorView.swift"),
        )
        inputs.files(sources)
        inputs.dir(outDir)
        commandLine(
            listOf(
                "xcrun", "--sdk", spec.sdk, "swiftc", "-typecheck",
                "-target", spec.swiftTarget,
                "-F", outDir.get().asFile.absolutePath,
            ) + sources.map { it.absolutePath },
        )
    }
}

tasks.register("compileCallbackViewModels") {
    dependsOn("compileTvosSimulatorArm64ViewModels", "compileWatchosSimulatorArm64ViewModels")
}

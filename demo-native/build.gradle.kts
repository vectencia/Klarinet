plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    // Native demo executables require platform-specific static libraries
    // (libklarinet_native.a) compiled for the target OS. These are only
    // linkable on the matching platform — cross-linking is not supported.
    // Use: ./gradlew demo-native:linkReleaseExecutableLinuxX64  (on Linux)
    //      ./gradlew demo-native:linkReleaseExecutableMingwX64  (on Windows)

    linuxX64 {
        binaries {
            executable { entryPoint = "main" }
        }
    }
    mingwX64 {
        binaries {
            executable { entryPoint = "main" }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val linuxX64Main by getting {
            dependencies {
                implementation(project(":klarinet"))
            }
        }
        val mingwX64Main by getting {
            dependencies {
                implementation(project(":klarinet"))
            }
        }
    }
}

// Disable linking tasks by default — they require platform-native static libs.
// Compilation still verifies code correctness without linking.
tasks.matching { it.name.startsWith("link") && it.name.contains("Executable") }.configureEach {
    enabled = false
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
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

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    macosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(project(":klarinet"))
            implementation(libs.kotlin.test)
        }
    }
}

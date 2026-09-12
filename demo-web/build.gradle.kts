plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    js {
        outputModuleName.set("klarinet-demo")
        browser {
            commonWebpackConfig {
                outputFileName = "klarinet-demo.js"
            }
            binaries.executable()
        }
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":klarinet"))
            implementation(project(":klarinet-coroutines"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

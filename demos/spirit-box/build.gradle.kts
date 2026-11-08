// Kotlin 2.4.21, Gradle 9.5.1, AGP 9.1.1:
// https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.koin.compiler) apply false
}

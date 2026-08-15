plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

dependencies {
    implementation(project(":klarinet"))
}

application {
    mainClass.set("MainKt")
}

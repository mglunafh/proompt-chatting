plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    application
}

dependencies {
    implementation(platform(libs.bom.ktor))
    implementation(platform(libs.bom.kotlinx.coroutines))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)
}

application {
    mainClass.set("dev.burufi.chatting.simple.server.ServerKt")
}

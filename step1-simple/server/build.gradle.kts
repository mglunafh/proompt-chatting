plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(platform(libs.bom.ktor))
    implementation(platform(libs.bom.kotlinx.coroutines))

    implementation(project(":step1-simple:shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)

    runtimeOnly(libs.logback.classic)

    testImplementation(platform(libs.bom.junit))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("dev.burufi.chatting.simple.server.ServerKt")
}

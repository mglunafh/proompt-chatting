plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(platform(libs.bom.ktor))
    implementation(platform(libs.bom.kotlinx.coroutines))

    implementation(project(":step1-simple:shared"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.clikt)

    runtimeOnly(libs.logback.classic)

    testImplementation(testFixtures(project(":step1-simple:shared")))
    testImplementation(platform(libs.bom.junit))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(project(":step1-simple:server"))
    testImplementation(libs.ktor.server.cio)
}

application {
    mainClass.set("dev.burufi.chatting.simple.client.ClientMainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(platform(libs.bom.ktor))
    implementation(platform(libs.bom.kotlinx.coroutines))

    implementation(project(":step2-durable:shared"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.clikt)
    implementation(libs.mordant)

    runtimeOnly(libs.logback.classic)

    testImplementation(testFixtures(project(":step2-durable:shared")))
    testImplementation(platform(libs.bom.junit))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("dev.burufi.chatting.durable.client.ClientMainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

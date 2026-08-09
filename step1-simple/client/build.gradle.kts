plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    application
}

dependencies {
    implementation(platform(libs.bom.ktor))
    implementation(platform(libs.bom.kotlinx.coroutines))
    testImplementation(platform(libs.bom.junit))

    implementation(project(":step1-simple:shared"))
    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("dev.burufi.chatting.simple.client.ClientKt")
}

tasks.test {
    useJUnitPlatform()
}

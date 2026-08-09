plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    application
}

dependencies {
    implementation(platform(libs.bom.ktor))
    implementation(platform(libs.bom.kotlinx.coroutines))

    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("dev.burufi.chatting.simple.client.ClientKt")
}

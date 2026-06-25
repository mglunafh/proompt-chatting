plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(platform(libs.ktor.bom))
    implementation(platform(libs.kotlinx.coroutines.bom))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("AppKt")
}

repositories {
    mavenCentral()
}

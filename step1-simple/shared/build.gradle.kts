plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    `java-library`
}

dependencies {
    api(platform(libs.bom.kotlinx.serialization))
    api(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.bom.junit))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

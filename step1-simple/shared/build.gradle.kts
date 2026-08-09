plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
    alias(libs.plugins.ktlint)
}

dependencies {
    api(platform(libs.bom.kotlinx.serialization))
    api(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.bom.junit))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

plugins {
    alias(libs.plugins.ktlint) apply false
}

group = "dev.burufi.chatting.simple"
version = "0.1.0-SNAPSHOT"

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
